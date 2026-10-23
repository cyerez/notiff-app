package com.rez.notif.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rez.notif.backup.BackupCrypto
import com.rez.notif.classify.Analyzer
import com.rez.notif.security.WalletVault
import com.rez.notif.wallet.WalletCodec
import com.rez.notif.wallet.WalletEntry
import com.rez.notif.wallet.WalletKind
import com.rez.notif.classify.Category
import com.rez.notif.classify.Classifier
import com.rez.notif.classify.Learner
import com.rez.notif.classify.Priority
import com.rez.notif.classify.VipMatcher
import com.rez.notif.classify.VipPerson
import com.rez.notif.sms.ContactNames
import com.rez.notif.sms.DefaultSms
import com.rez.notif.sms.RawSms
import com.rez.notif.sms.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** A notification as the listener hands it over. */
data class NotificationIn(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val isSmsApp: Boolean,
    val reply: android.app.Notification.Action? = null,
)

/** Everything the screens and the background parts need, in one place. */
class Repository(private val context: Context, private val db: Db, val prefs: Prefs) {

    /** Goes up by one on every change, so screens know when to reload. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()
    /** Asks every screen to reload (e.g. after contacts were loaded). */
    fun bump() = changed()

    private fun changed() {
        _version.update { it + 1 }
        com.rez.notif.widget.NotifWidget.refreshSoon(context)
    }

    data class WidgetData(val unread: Int, val today: Int, val counts: List<CategoryCount>)

    /** Numbers for the home-screen widget (called on a background thread). */
    fun widgetData(): WidgetData {
        val counts = db.categoryCounts()
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return WidgetData(
            unread = counts.filter { it.category != Category.ADS.id }.sumOf { it.unread },
            today = db.count(ItemQuery(sinceMillis = dayStart)),
            counts = counts,
        )
    }

    /** Tap actions of live notifications, to open the exact chat/email. Lost when the app process dies. */
    private val contentIntents = ConcurrentHashMap<String, PendingIntent>()
    /** Reply buttons of live notifications (kept in memory only, like the content intents). */
    private val replyActions = ConcurrentHashMap<String, android.app.Notification.Action>()

    @Volatile private var overrides: Map<String, String>? = null
    private fun overrides(): Map<String, String> = overrides ?: db.overrides().also { overrides = it }

    @Volatile private var vipCache: List<VipPerson>? = null
    private fun vipPeople(): List<VipPerson> = vipCache ?: db.vips().also { vipCache = it }

    // ---------- incoming ----------

    private fun smsKey(address: String, body: String, date: Long): String =
        "sms:" + Classifier.normalize(address).replace(" ", "") + ":" + body.hashCode() + ":" + date / 600_000L

    private fun smsToItem(raw: RawSms): Item {
        ContactNames.ensure(context)
        val vip = VipMatcher.match(vipPeople(), true, raw.address, "", raw.body)
        var r = vipSafe(Classifier.classify(Classifier.Input(isSms = true, sender = raw.address, title = "", body = raw.body), overrides()), vip)
        if (r.category == Category.OTHER && vip == null) learner?.guess(raw.body)?.let { r = r.copy(category = it.category) }
        // A saved contact is a person: never an ad, and "personal" when nothing better fits.
        if (ContactNames.nameOf(raw.address) != null && (r.category == Category.ADS || r.category == Category.OTHER)) {
            r = r.copy(category = Category.PERSONAL)
        }
        return Item(
            source = Source.SMS,
            sourceKey = smsKey(raw.address, raw.body, raw.date),
            packageName = "",
            appName = "پیامک",
            sender = raw.address,
            title = raw.address,
            body = raw.body,
            timestamp = raw.date,
            simSlot = SmsReader.slotOf(context, raw.subId),
            category = r.category.id,
            isAd = r.category == Category.ADS,
            isValuable = r.isValuable,
            vipId = vip?.id ?: 0,
        )
    }

    // ---------- small offline model ----------

    @Volatile private var learner: Learner? = null
    @Volatile private var learnerStale = true

    /** (Re)trains the model from what is already sorted, including the user's own changes. Cheap; runs on resume. */
    fun ensureLearner() {
        if (!learnerStale && learner != null) return
        learner = Learner.train(db.trainingSamples().map { (text, cat) -> text to Category.of(cat) })
        learnerStale = false
    }

    /** Lets the model sort what the rules left in "Other". Returns how many moved. */
    fun relabelOthers(): Int {
        ensureLearner()
        val m = learner ?: return 0
        var moved = 0
        for ((id, text) in db.uncategorizedSms()) {
            val g = m.guess(text) ?: continue
            db.updateCategory(id, g.category.id, g.category == Category.ADS)
            moved++
        }
        if (moved > 0) changed()
        return moved
    }

    suspend fun relabelOthersNow(): Int = withContext(Dispatchers.IO) { relabelOthers() }

    // ---------- important & while you were away ----------

    /** Unread things of the last two days that matter most (chosen people, missed calls, personal, money). */
    private fun scoreOf(it: Item, now: Long) = Priority.score(
        Priority.Input(
            Category.of(it.category), it.vipId > 0, it.isValuable, it.isStarred, it.isRead, now - it.timestamp,
            isQuestion = Priority.isQuestion(it.body),
        ),
    )

    suspend fun important(): List<Item> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.query(ItemQuery(sinceMillis = now - 48 * 3_600_000L, limit = 400))
            .filter { !it.isRead }
            .map { it to scoreOf(it, now) }
            .filter { it.second >= Priority.IMPORTANT }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    /** The home tab: a fresh login code, what needs you (chosen people, questions, missed calls…), and the rest of today. */
    data class HomeFeed(val code: Item?, val needsYou: List<Item>, val recent: List<Item>)

    suspend fun homeFeed(): HomeFeed = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val items = db.query(ItemQuery(sinceMillis = now - 48 * 3_600_000L, limit = 800))
        val code = items.firstOrNull { it.category == Category.OTP.id && now - it.timestamp < 10 * 60_000L }
        val needsYou = items
            .filter { !it.isRead && it.category != Category.OTP.id }
            .map { it to scoreOf(it, now) }
            .filter { it.second >= Priority.IMPORTANT }
            .sortedByDescending { it.first.timestamp }
            .take(15)
            .map { it.first }
        val shown = needsYou.map { it.id }.toSet() + listOfNotNull(code?.id)
        val since = dayStart().coerceAtMost(now - 12 * 3_600_000L)
        val recent = items.filter { it.timestamp >= since && it.id !in shown && !isClutter(it, now) }.take(30)
        HomeFeed(code, needsYou, recent)
    }

    /** What counts as clutter in "today": ads, operator and system messages, and login codes older than an hour. */
    val clutterCategories = setOf(Category.OPERATOR.id, Category.SYSTEM.id)

    private fun isClutter(i: Item, now: Long) =
        i.isAd || i.category in clutterCategories || (i.category == Category.OTP.id && now - i.timestamp > 3_600_000L)

    fun dayStart(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Today's items; without [all], the clutter is left out. Returns (shown, clutter). */
    suspend fun today(all: Boolean): Pair<List<Item>, List<Item>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val items = db.query(ItemQuery(sinceMillis = dayStart(), includeAds = true, limit = 2000))
        val (junk, good) = items.partition { isClutter(it, now) && it.vipId == 0L && !it.isStarred && !it.isValuable }
        (if (all) items else good) to junk
    }

    data class Away(val since: Long, val total: Int, val byCategory: List<Pair<Category, Int>>, val vip: Int)

    /** What came in while the user was away (set when the app opens after 2+ hours). */
    suspend fun awaySummary(): Away? = withContext(Dispatchers.IO) {
        val since = prefs.current.awaySince
        if (since <= 0) return@withContext null
        val items = db.query(ItemQuery(sinceMillis = since, limit = 2000))
        if (items.isEmpty()) return@withContext null
        Away(
            since = since,
            total = items.size,
            byCategory = items.groupBy { Category.of(it.category) }.map { it.key to it.value.size }.sortedByDescending { it.second },
            vip = items.count { it.vipId > 0 },
        )
    }

    /** A chosen person's message is never an ad. */
    private fun vipSafe(r: Classifier.Result, vip: VipPerson?): Classifier.Result =
        if (vip != null && r.category == Category.ADS) r.copy(category = Category.PERSONAL) else r

    fun addSms(raw: RawSms) {
        if (db.insert(smsToItem(raw))) changed()
    }

    /** What to do with a new SMS while Notif is the default SMS app. */
    data class SmsDecision(val notify: Boolean, val code: String?, val isAd: Boolean, val block: Boolean)

    /** Stores a new SMS (default SMS app mode) and decides: notify, show only a code, or stay quiet. */
    fun receiveSms(raw: RawSms): SmsDecision {
        val item = smsToItem(raw)
        if (db.insert(item)) changed()
        val category = Category.of(item.category)
        val vip = vipPeople().firstOrNull { it.id == item.vipId }
        val code = codeFor(category, raw.body)
        val hide = hideNow(category, vip, raw.date, code != null)
        val s = prefs.current
        return SmsDecision(
            notify = !hide,
            code = code,
            isAd = item.isAd,
            block = item.isAd && s.hideAds && s.blockAdSms,
        )
    }

    /** Stores a notification. Returns true when the listener should remove it from the status bar (an ad). */
    /** What the listener should do with a notification: hide it, and maybe show the code with a copy button. */
    data class Decision(val hide: Boolean, val code: String?)

    fun addNotification(n: NotificationIn): Decision {
        if (n.isSmsApp) {
            // The SMS itself is stored by SmsReceiver. Here we only decide whether to hide its notification.
            val vip = VipMatcher.match(vipPeople(), true, n.title, "", n.text)
            val r = vipSafe(Classifier.classify(Classifier.Input(true, n.title, "", n.text), overrides()), vip)
            val code = codeFor(r.category, n.text)
            return Decision(hideNow(r.category, vip, n.postTime, code != null), code)
        }
        val vip = VipMatcher.match(vipPeople(), false, n.title, n.title, n.text)
        val r = vipSafe(
            Classifier.classify(
                Classifier.Input(isSms = false, sender = n.title, title = n.title, body = n.text, packageName = n.packageName),
                overrides(),
            ),
            vip,
        )
        val key = "n:" + n.packageName + ":" + (n.title + "|" + n.text).hashCode() + ":" + n.postTime / 600_000L
        n.contentIntent?.let { contentIntents[key] = it }
        n.reply?.let { replyActions[key] = it }
        val inserted = db.insert(
            Item(
                source = Source.NOTIFICATION,
                sourceKey = key,
                packageName = n.packageName,
                appName = n.appName,
                sender = n.title,
                title = n.title,
                body = n.text,
                timestamp = n.postTime,
                category = r.category.id,
                isAd = r.category == Category.ADS,
                isValuable = r.isValuable,
                vipId = vip?.id ?: 0,
            ),
        )
        if (inserted) changed()
        val code = codeFor(r.category, n.title + "\n" + n.text)
        return Decision(hideNow(r.category, vip, n.postTime, code != null), code)
    }

    /** A code to show with a copy button, only when we are hiding notifications and replacing codes. */
    private fun codeFor(category: Category, text: String): String? {
        val s = prefs.current
        if (category != Category.OTP || !s.hideAll || !s.keepOtp) return null
        return Classifier.extractCode(text)
    }

    /** Should this notification leave the status bar right away? */
    private fun hideNow(category: Category, vip: VipPerson?, postTime: Long, replacedByOurCode: Boolean = false): Boolean {
        val s = prefs.current
        if (!s.hideAll) return s.hideAds && category == Category.ADS
        if (s.keepVip && vip != null) return false
        // A fresh code stays visible: either as our own copy-button notification, or the original.
        if (s.keepOtp && category == Category.OTP && System.currentTimeMillis() - postTime < 10 * 60_000L) return replacedByOurCode
        return true
    }

    /** What arrived after the app was last opened (for the summary notification). Ads are left out. */
    fun newSince(since: Long): List<Item> =
        db.query(ItemQuery(sinceMillis = maxOf(since + 1, System.currentTimeMillis() - 24 * 3_600_000L), limit = 300))

    // ---------- first scan (the "first brain") ----------

    /** Reads every SMS, classifies, fixes gaps by sender, stores, and returns category suggestions. */
    suspend fun firstScan(onProgress: (done: Int, total: Int) -> Unit): List<Analyzer.Suggestion> =
        withContext(Dispatchers.IO) {
            val raws = SmsReader.readInbox(context, 0L, onProgress)
            val items = raws.map { smsToItem(it) }
            val msgs = items.map { Analyzer.Msg(it.sourceKey, it.sender, it.body, Category.of(it.category)) }
            Analyzer.smoothBySender(msgs)
            val dayAgo = System.currentTimeMillis() - 86_400_000L
            val smoothed = items.zip(msgs) { item, m ->
                val cat = if (item.vipId > 0 && m.category == Category.ADS) Category.PERSONAL else m.category
                item.copy(
                    category = cat.id,
                    isAd = cat == Category.ADS,
                    isValuable = item.isValuable && cat != Category.ADS,
                    // Old messages count as already read; only the last day shows as new.
                    isRead = item.timestamp < dayAgo,
                )
            }
            db.insertAll(smoothed)
            // The small model learns from what the rules sorted and tries the leftovers in "Other".
            runCatching { relabelOthers() }
            // Notifications caught so far also count toward the suggestions.
            val notifMsgs = db.query(ItemQuery(source = Source.NOTIFICATION, includeAds = true, limit = 5000))
                .map { Analyzer.Msg(it.sourceKey, it.appName, it.title + " " + it.body, Category.of(it.category)) }
            changed()
            Analyzer.suggest(msgs + notifMsgs)
        }

    /** Import SMS that arrived while the app could not listen (e.g. after a reboot). */
    suspend fun syncSms(): Int = withContext(Dispatchers.IO) {
        // Look one day back from the newest stored SMS, so nothing at the edge is missed.
        val since = (db.latestSmsTime() - 86_400_000L).coerceAtLeast(0L)
        val n = db.insertAll(SmsReader.readInbox(context, since).map { smsToItem(it) })
        if (n > 0) changed()
        n
    }

    // ---------- reads ----------

    suspend fun items(q: ItemQuery): List<Item> = withContext(Dispatchers.IO) { db.query(withContactSearch(q)) }

    /** Searching a contact's name also finds their SMS (which are stored under the number). */
    private fun withContactSearch(q: ItemQuery): ItemQuery {
        val text = q.text?.trim().orEmpty()
        if (text.length < 2) return q
        val tails = ContactNames.names.value.filterValues { it.contains(text, ignoreCase = true) }.keys.toList()
        return if (tails.isEmpty()) q else q.copy(textSenderTails = tails)
    }
    suspend fun item(id: Long): Item? = withContext(Dispatchers.IO) { db.get(id) }
    suspend fun count(q: ItemQuery): Int = withContext(Dispatchers.IO) { db.count(q) }
    suspend fun categoryCounts(): List<CategoryCount> = withContext(Dispatchers.IO) { db.categoryCounts() }

    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val starts = (0 until 7).map { cal.timeInMillis.also { cal.add(Calendar.DAY_OF_YEAR, 1) } }
        db.stats(starts)
    }

    // ---------- user actions ----------

    /**
     * Moves an item to another category. With [wholeSender] the app also learns:
     * every past and future message from that sender/app goes to the new category.
     */
    suspend fun setCategory(item: Item, category: Category, wholeSender: Boolean) = withContext(Dispatchers.IO) {
        val isAd = category == Category.ADS
        if (wholeSender) {
            val key = Classifier.senderKey(
                Classifier.Input(item.source == Source.SMS, item.sender, item.title, item.body, item.packageName),
            )
            db.putOverride(key, category.id)
            overrides = null
            val who = if (item.source == Source.SMS) item.sender else item.packageName
            db.updateCategoryForSender(who, item.source, category.id, isAd)
        } else {
            db.updateCategory(item.id, category.id, isAd)
        }
        learnerStale = true
        changed()
    }

    suspend fun toggleStar(item: Item) = withContext(Dispatchers.IO) {
        db.setStarred(item.id, !item.isStarred)
        changed()
    }

    suspend fun markRead(items: List<Item>) = withContext(Dispatchers.IO) {
        val unread = items.filter { !it.isRead }
        val ids = unread.map { it.id }
        if (ids.isNotEmpty()) {
            db.markRead(ids)
            unread.filter { it.source == Source.SMS }.map { it.sender }.distinct().forEach { DefaultSms.markRead(context, it) }
            changed()
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.deleteAll()
        changed()
    }

    // ---------- delete and cleanup ----------

    /** Hides items from the app (the phone's own SMS inbox is not touched). Returns their ids for undo. */
    suspend fun delete(items: List<Item>): List<Long> = withContext(Dispatchers.IO) {
        val ids = items.map { it.id }
        db.delete(ids)
        removeFromPhone(ids)
        changed()
        ids
    }

    suspend fun undoDelete(ids: List<Long>) = withContext(Dispatchers.IO) {
        db.delete(ids, deleted = false)
        putBackOnPhone(ids)
        changed()
    }

    /** SMS we removed from the phone's inbox, kept for a while so "undo" can put them back. */
    private val removedFromPhone = java.util.concurrent.ConcurrentHashMap<Long, Item>()

    /** Phase 5: when Notif is the default SMS app and the user wants it, deleting also deletes on the phone. */
    private fun removeFromPhone(ids: List<Long>) {
        if (!prefs.current.phoneDelete || ids.isEmpty() || !DefaultSms.isDefault(context)) return
        if (removedFromPhone.size > 5000) removedFromPhone.clear()
        db.smsByIds(ids).forEach { item ->
            if (DefaultSms.deleteFromPhone(context, item.sender, item.body, item.timestamp) > 0) removedFromPhone[item.id] = item
        }
    }

    private fun putBackOnPhone(ids: List<Long>) {
        ids.forEach { id ->
            val item = removedFromPhone.remove(id) ?: return@forEach
            DefaultSms.writeInbox(
                context,
                RawSms(-1, item.sender, item.body, item.timestamp, DefaultSms.subIdForSlot(context, item.simSlot)),
                sentAt = item.timestamp,
                read = true,
            )
        }
    }

    /** Everything from the same SMS sender, or the same app. */
    suspend fun deleteFromSender(item: Item): List<Long> = withContext(Dispatchers.IO) {
        val ids = if (item.source == Source.SMS) {
            db.ids(ItemQuery(source = Source.SMS, sender = item.sender, includeAds = true))
        } else {
            db.ids(ItemQuery(source = Source.NOTIFICATION, packageName = item.packageName, includeAds = true))
        }
        db.delete(ids)
        removeFromPhone(ids)
        changed()
        ids
    }

    private fun cleanupQuery(rule: CleanupRule) = ItemQuery(
        category = rule.category,
        beforeMillis = System.currentTimeMillis() - rule.days * 86_400_000L,
        excludeKept = true,
        includeAds = true,
        limit = Int.MAX_VALUE,
    )

    suspend fun cleanupPreview(): Map<CleanupRule, Int> = withContext(Dispatchers.IO) {
        CleanupRule.entries.associateWith { db.count(cleanupQuery(it)) }
    }

    suspend fun runCleanup(rules: Set<CleanupRule>): Int = withContext(Dispatchers.IO) {
        var n = 0
        rules.forEach { rule ->
            val ids = db.ids(cleanupQuery(rule))
            db.delete(ids)
            removeFromPhone(ids)
            n += ids.size
        }
        if (n > 0) changed()
        n
    }

    /** Runs the chosen cleanup rules at most once a day, when the user turned auto clean on. */
    suspend fun autoCleanIfDue() {
        val s = prefs.current
        if (!s.autoClean || System.currentTimeMillis() - s.lastAutoClean < 86_400_000L) return
        runCleanup(s.cleanupRules.mapNotNull { runCatching { CleanupRule.valueOf(it) }.getOrNull() }.toSet())
        prefs.update { it.copy(lastAutoClean = System.currentTimeMillis()) }
    }

    // ---------- status bar ----------

    /**
     * Decides what stays in the phone's notification shade when the app is opened:
     * chosen people, main categories, valuable items, and fresh login codes (under 10 minutes).
     */
    fun keepInShade(packageName: String, isSmsApp: Boolean, title: String, text: String, postTime: Long): Boolean {
        val vip = VipMatcher.match(vipPeople(), isSmsApp, title, if (isSmsApp) "" else title, text)
        val r = if (isSmsApp) {
            Classifier.classify(Classifier.Input(true, title, "", text), overrides())
        } else {
            Classifier.classify(Classifier.Input(false, title, title, text, packageName), overrides())
        }
        if (prefs.current.hideAll) return !hideNow(r.category, vip, postTime)
        if (vip != null) return true
        if (r.isValuable) return true
        if (r.category == Category.OTP) return System.currentTimeMillis() - postTime < 10 * 60_000L
        return r.category.id in prefs.current.mainCategories
    }

    // ---------- learned rules ----------

    /** A rule the app learned when the user moved "all from this sender" to a category. */
    data class Learned(val key: String, val who: String, val isSms: Boolean, val category: Category)

    suspend fun learned(): List<Learned> = withContext(Dispatchers.IO) {
        db.overrides().map { (key, cat) ->
            val isSms = key.startsWith("sms:")
            val raw = key.substringAfter(':')
            val who = if (isSms) raw else runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(raw, 0)).toString()
            }.getOrDefault(raw)
            Learned(key, who, isSms, Category.of(cat))
        }.sortedBy { it.who }
    }

    suspend fun changeLearned(key: String, category: Category) = withContext(Dispatchers.IO) {
        db.putOverride(key, category.id)
        overrides = null
        if (key.startsWith("app:")) {
            db.updateCategoryForSender(key.removePrefix("app:"), Source.NOTIFICATION, category.id, category == Category.ADS)
        }
        changed()
    }

    /** Forgets a rule. Items keep their category; new ones go back to the built-in rules. */
    suspend fun forgetLearned(key: String) = withContext(Dispatchers.IO) {
        db.removeOverride(key)
        overrides = null
        changed()
    }

    // ---------- password wallet ----------

    fun walletCount(): Int = db.walletCount()

    /** All wallet entries, unlocked. Needs [WalletVault] to be open (throws WalletVault.Locked otherwise). */
    suspend fun walletEntries(): List<WalletEntry> = withContext(Dispatchers.IO) {
        db.walletRows().map { (base, data) -> WalletCodec.decode(base, WalletVault.decrypt(data)) }
    }

    suspend fun walletSave(e: WalletEntry): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.walletPut(e.id, e.title.trim(), e.kind.name, WalletVault.encrypt(WalletCodec.encode(e)), now)
    }

    suspend fun walletDelete(id: Long) = withContext(Dispatchers.IO) { db.walletDelete(id) }

    // ---------- backup ----------

    /** Writes a password-locked copy of everything in the app to [uri]. */
    suspend fun exportBackup(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val tmp = java.io.File(context.cacheDir, "backup.db")
        try {
            // Wallet secrets only open on this phone, so they go into the backup unlocked
            // (the whole backup file is locked with the backup password right after).
            val wallet = if (db.walletCount() > 0) {
                db.walletRows().map { (base, data) -> base to WalletVault.decrypt(data) }
            } else emptyList()
            db.snapshotTo(tmp)
            android.database.sqlite.SQLiteDatabase.openDatabase(tmp.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { d ->
                runCatching { d.execSQL("DELETE FROM wallet") }
                d.execSQL("CREATE TABLE wallet_export (title TEXT, kind TEXT, data TEXT, updated INTEGER)")
                for ((base, plainData) in wallet) {
                    d.execSQL(
                        "INSERT INTO wallet_export VALUES (?, ?, ?, ?)",
                        arrayOf<Any>(base.title, base.kind.name, plainData, base.updated),
                    )
                }
            }
            val locked = BackupCrypto.encrypt(tmp.readBytes(), password)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(locked) } ?: error("cannot write file")
        } finally {
            tmp.delete()
        }
    }

    /** Replaces everything in the app with the backup in [uri]. Throws BackupCrypto errors on a bad file/password. */
    suspend fun importBackup(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("cannot read file")
        val plain = BackupCrypto.decrypt(data, password)
        if (!plain.copyOfRange(0, 15).contentEquals("SQLite format 3".toByteArray())) throw BackupCrypto.NotABackup()
        val tmp = java.io.File(context.cacheDir, "restore.db")
        tmp.writeBytes(plain)
        try {
            // Make sure it really is our database before touching the current one, and take the wallet out.
            val walletPlain = ArrayList<Pair<WalletEntry, String>>()
            android.database.sqlite.SQLiteDatabase.openDatabase(tmp.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { d ->
                d.rawQuery("SELECT COUNT(*) FROM items", null).use { it.moveToFirst() }
                runCatching {
                    d.rawQuery("SELECT title, kind, data, updated FROM wallet_export", null).use { c ->
                        while (c.moveToNext()) {
                            val kind = runCatching { WalletKind.valueOf(c.getString(1)) }.getOrDefault(WalletKind.LOGIN)
                            walletPlain += WalletEntry(title = c.getString(0), kind = kind, updated = c.getLong(3)) to c.getString(2)
                        }
                    }
                }
                runCatching { d.execSQL("DROP TABLE IF EXISTS wallet_export") }
                runCatching { d.execSQL("DELETE FROM wallet") }
            }
            // Lock the wallet entries with this phone's key before replacing anything (needs the finger).
            val walletLocked = walletPlain.map { (base, plainData) -> base to WalletVault.encrypt(plainData) }
            db.restoreFrom(tmp)
            for ((base, data) in walletLocked) db.walletPut(0, base.title, base.kind.name, data, base.updated)
        } finally {
            tmp.delete()
        }
        overrides = null
        vipCache = null
        changed()
    }

    // ---------- chosen people ----------

    data class VipSummary(val person: VipPerson, val count: Int, val last: Long)

    suspend fun vipSummaries(): List<VipSummary> = withContext(Dispatchers.IO) {
        tidyVips()
        val counts = db.vipCounts()
        db.vips().map { p -> VipSummary(p, counts[p.id]?.first ?: 0, counts[p.id]?.second ?: 0L) }
            .sortedByDescending { it.last }
    }

    suspend fun addVip(name: String, rawNumbers: List<String>): Long = withContext(Dispatchers.IO) {
        val numbers = rawNumbers.mapNotNull { VipMatcher.key(it) }.distinct()
        val id = db.addVip(name.trim().ifEmpty { rawNumbers.firstOrNull() ?: "?" }, numbers)
        vipCache = null
        tidyVips(force = true)
        id
    }

    /**
     * One person = one entry: a chosen person whose number is in the contacts gets the contact's name and
     * all of the contact's numbers, and entries with the same name are folded together.
     */
    private fun tidyVips(force: Boolean = false) {
        ContactNames.ensure(context)
        val names = ContactNames.names.value
        var changed = false
        if (names.isNotEmpty()) {
            for (v in db.vips()) {
                val contact = v.numbers.firstNotNullOfOrNull { names[it] } ?: continue
                val all = names.filterValues { it == contact }.keys
                val newName = if (v.name.none { it.isLetter() }) contact else v.name
                val newNumbers = (v.numbers + all).distinct()
                if (newName != v.name || newNumbers.size != v.numbers.size) {
                    db.updateVip(v.id, newName, newNumbers)
                    changed = true
                }
            }
        }
        if (db.mergeVipsByName()) changed = true
        if (changed || force) {
            vipCache = null
            retagVips()
        }
    }

    suspend fun removeVip(id: Long) = withContext(Dispatchers.IO) {
        db.removeVip(id)
        vipCache = null
        changed()
    }

    /** Finds old messages of chosen people, also ones deleted before, and files them under that person. */
    private fun retagVips() {
        val people = vipPeople()
        val byVip = HashMap<Long, MutableList<Long>>()
        db.allForVipScan().forEach { row ->
            val isSms = row[1] == Source.SMS.name
            VipMatcher.match(people, isSms, row[2], if (isSms) "" else row[3], row[4])?.let { p ->
                byVip.getOrPut(p.id) { ArrayList() } += row[0].toLong()
            }
        }
        byVip.forEach { (vipId, ids) ->
            db.setVip(ids, vipId)
            db.delete(ids, deleted = false) // a chosen person's messages are never lost
        }
        changed()
    }

    /**
     * Opens where the user can reply: the SMS conversation, or the exact chat/email of a notification
     * (when it is still live), otherwise the app itself.
     */
    fun openSource(activityContext: Context, item: Item): Boolean {
        return try {
            if (item.source == Source.SMS) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(item.sender)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activityContext.startActivity(intent)
                true
            } else {
                val pi = contentIntents[item.sourceKey]
                if (pi != null && sendPending(pi)) return true
                val launch = activityContext.packageManager.getLaunchIntentForPackage(item.packageName) ?: return false
                activityContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ---------- quick reply ----------

    /** SMS can always be answered (sent from the same SIM); a notification only while its reply button is known. */
    fun canReply(item: Item): Boolean =
        item.source == Source.SMS && item.sender.any { it.isDigit() } || replyActions.containsKey(item.sourceKey)

    /** Sends [text] as an answer. Returns false if it could not be sent. */
    fun reply(item: Item, text: String): Boolean = try {
        if (item.source == Source.SMS) {
            DefaultSms.send(context, item.sender, text, item.simSlot)
        } else {
            val a = replyActions[item.sourceKey] ?: return false
            val inputs = a.remoteInputs ?: return false
            val results = android.os.Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            val fill = Intent()
            android.app.RemoteInput.addResultsToIntent(inputs, fill, results)
            a.actionIntent.send(context, 0, fill)
            true
        }
    } catch (e: Exception) {
        false
    }

    private fun sendPending(pi: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(context, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    }
}
