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
import com.rez.notif.classify.Ledger
import com.rez.notif.classify.Money
import com.rez.notif.classify.Bills
import com.rez.notif.classify.Rule
import com.rez.notif.classify.RuleAction
import com.rez.notif.classify.Rules
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
    companion object {
        /** Blu bank sends app notifications instead of SMS. */
        fun isBluApp(pkg: String, appName: String): Boolean {
            val n = appName.lowercase().trim()
            return pkg.startsWith("com.samanpr") || n == "blu" || n == "بلو" || "blu bank" in n || "بلو بانک" in n
        }
    }


    /** For the background helpers (reminders) that talk to the database directly. */
    val database: Db get() = db

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

    private fun smsToItem(raw: RawSms): Item = applyRule(smsToItemPlain(raw)).first

    private fun smsToItemPlain(raw: RawSms): Item {
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
        val week = db.query(ItemQuery(sinceMillis = now - 7 * 86_400_000L, limit = 2000))
        val items = week.filter { now - it.timestamp < 48 * 3_600_000L }
        val code = items.firstOrNull { it.category == Category.OTP.id && now - it.timestamp < 10 * 60_000L }
        // Missed calls and chosen people stay until read (a whole week); other important things for 48 hours.
        val needsYou = week
            .filter { !it.isRead && it.category != Category.OTP.id }
            .filter { neverMiss(it) || (now - it.timestamp < 48 * 3_600_000L && scoreOf(it, now) >= Priority.IMPORTANT) }
            .sortedByDescending { it.timestamp }
            .take(40)
        val shown = needsYou.map { it.id }.toSet() + listOfNotNull(code?.id)
        val since = dayStart().coerceAtMost(now - 12 * 3_600_000L)
        val recent = items.filter { it.timestamp >= since && it.id !in shown && !isClutter(it, now) }.take(30)
        HomeFeed(code, needsYou, recent)
    }

    /** Missed calls and messages from chosen people: never hidden from the status bar, kept on Home until read. */
    fun neverMiss(i: Item) = i.category == Category.MISSED.id || i.vipId > 0

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
        val (item, rule) = applyRule(smsToItemPlain(raw))
        if (db.insert(item)) {
            changed()
            if (rule?.action == RuleAction.REMIND) remindNow(item.sourceKey)
            afterNew(item)
        }
    }

    /** What to do with a new SMS while Notif is the default SMS app. */
    data class SmsDecision(val notify: Boolean, val code: String?, val isAd: Boolean, val block: Boolean)

    /** Stores a new SMS (default SMS app mode) and decides: notify, show only a code, or stay quiet. */
    fun receiveSms(raw: RawSms): SmsDecision {
        val (item, rule) = applyRule(smsToItemPlain(raw))
        if (db.insert(item)) {
            changed()
            if (rule?.action == RuleAction.REMIND) remindNow(item.sourceKey)
            afterNew(item)
        }
        val category = Category.of(item.category)
        val vip = vipPeople().firstOrNull { it.id == item.vipId }
        val code = codeFor(category, raw.body)
        val hide = hideNow(category, vip, raw.date, code != null, rule)
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
    /** [holdMs]: how long a hidden notification may stay in the status bar first (counted from when it was posted). */
    data class Decision(val hide: Boolean, val code: String?, val holdMs: Long = 0, val sourceKey: String = "")

    fun addNotification(n: NotificationIn): Decision {
        if (n.isSmsApp) {
            // The SMS itself is stored by SmsReceiver. Here we only decide whether to hide its notification.
            val vip = VipMatcher.match(vipPeople(), true, n.title, "", n.text)
            val r = vipSafe(Classifier.classify(Classifier.Input(true, n.title, "", n.text), overrides()), vip)
            val rule = Rules.first(rules(), Rules.Input(true, "", n.title, n.text, r.category.id, hourOf(n.postTime)))
            val cat = if (rule?.action == RuleAction.MOVE) Category.of(rule.target) else r.category
            val code = codeFor(cat, n.text)
            return Decision(hideNow(cat, vip, n.postTime, code != null, rule), code, holdFor(cat, code, rule, n.postTime))
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
        val (stored, rule) = applyRule(
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
        if (db.insert(stored)) {
            changed()
            if (rule?.action == RuleAction.REMIND) remindNow(stored.sourceKey)
            afterNew(stored)
        }
        val cat = Category.of(stored.category)
        val code = codeFor(cat, n.title + "\n" + n.text)
        return Decision(hideNow(cat, vip, n.postTime, code != null, rule), code, holdFor(cat, code, rule, n.postTime), key)
    }

    /** A code to show with a copy button, only when we are hiding notifications and replacing codes. */
    private fun codeFor(category: Category, text: String): String? {
        val s = prefs.current
        if (category != Category.OTP || !s.hideAll || !s.keepOtp) return null
        return Classifier.extractCode(text)
    }

    /** Normal messages stay a little while so they can be read; ads, codes we replaced and quiet hours go at once. */
    private fun holdFor(category: Category, code: String?, rule: Rule?, postTime: Long): Long {
        if (code != null || category == Category.ADS || rule?.action == RuleAction.DELETE || inQuietHours(postTime)) return 0
        return prefs.current.holdSeconds * 1000L
    }

    /** Should this notification leave the status bar right away? */
    private fun hideNow(category: Category, vip: VipPerson?, postTime: Long, replacedByOurCode: Boolean = false, rule: Rule? = null): Boolean {
        val s = prefs.current
        val freshCode = s.keepOtp && category == Category.OTP && System.currentTimeMillis() - postTime < 10 * 60_000L
        // The user's own rules come first.
        when (rule?.action) {
            RuleAction.ALERT -> return freshCode && replacedByOurCode
            RuleAction.QUIET, RuleAction.DELETE, RuleAction.REMIND -> return !(freshCode && !replacedByOurCode)
            else -> {}
        }
        // Missed calls and chosen people are never taken off the status bar (unless the user's own rule says so).
        if (category == Category.MISSED || vip != null) return false
        if (!s.hideAll) return (s.hideAds && category == Category.ADS) || (inQuietHours(postTime) && !freshCode)
        // A fresh code stays visible: either as our own copy-button notification, or the original.
        if (freshCode) return replacedByOurCode
        if (s.keepVip && vip != null && !inQuietHours(postTime)) return false
        return true
    }

    /** Inside the user's quiet hours (only rules marked "always show" and fresh codes get through). */
    fun inQuietHours(time: Long = System.currentTimeMillis()): Boolean {
        val s = prefs.current
        return s.quietFrom >= 0 && s.quietTo >= 0 && s.quietFrom != s.quietTo && Rules.inHours(s.quietFrom, s.quietTo, hourOf(time))
    }

    private fun hourOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)

    // ---------- the user's rules ----------

    @Volatile private var rulesCache: List<Rule>? = null
    private fun rules(): List<Rule> = rulesCache ?: db.rules().also { rulesCache = it }

    private fun ruleInput(i: Item) = Rules.Input(
        isSms = i.source == Source.SMS,
        packageName = i.packageName,
        sender = i.sender,
        text = i.title + "\n" + i.body,
        category = i.category,
        hour = hourOf(i.timestamp),
    )

    /** Applies "move" and "delete" rules before an item is stored; also returns the rule that matched. */
    private fun applyRule(item: Item, list: List<Rule> = rules()): Pair<Item, Rule?> {
        val r = Rules.first(list, ruleInput(item)) ?: return item to null
        val out = when (r.action) {
            RuleAction.MOVE -> Category.of(r.target).let { item.copy(category = it.id, isAd = it == Category.ADS) }
            RuleAction.DELETE -> item.copy(category = Category.ADS.id, isAd = true)
            else -> item
        }
        return out to r
    }

    private fun remindNow(sourceKey: String) {
        val id = db.idOf(sourceKey) ?: return
        com.rez.notif.notif.Reminders.schedule(context, id, System.currentTimeMillis(), repeats = 3)
    }

    suspend fun userRules(): List<Rule> = withContext(Dispatchers.IO) { rules() }

    suspend fun saveRule(r: Rule): Long = withContext(Dispatchers.IO) {
        db.saveRule(r).also { rulesCache = null; changed() }
    }

    suspend fun deleteRule(id: Long) = withContext(Dispatchers.IO) {
        db.deleteRule(id); rulesCache = null; changed()
    }

    suspend fun moveRule(id: Long, up: Boolean) = withContext(Dispatchers.IO) {
        val ids = rules().map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in ids.indices) return@withContext
        ids[i] = ids[j].also { ids[j] = ids[i] }
        db.reorderRules(ids); rulesCache = null; changed()
    }

    /** What a rule would have caught in the last 7 days: the count and a few examples. Nothing is changed. */
    data class RulePreview(val count: Int, val examples: List<Item>)

    suspend fun previewRule(r: Rule): RulePreview = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - 7 * 86_400_000L
        val hits = db.query(ItemQuery(sinceMillis = since, includeAds = true, limit = 5000))
            .filter { Rules.matches(r.copy(enabled = true), ruleInput(it)) }
        RulePreview(hits.size, hits.take(4))
    }

    /** Runs "move" and "delete" rules over what is already stored (after the user saves a rule). */
    suspend fun applyRulesToPast(r: Rule): Int = withContext(Dispatchers.IO) {
        if (r.action != RuleAction.MOVE && r.action != RuleAction.DELETE) return@withContext 0
        val since = System.currentTimeMillis() - 30 * 86_400_000L
        var n = 0
        db.query(ItemQuery(sinceMillis = since, includeAds = true, limit = 20000)).forEach { i ->
            if (i.vipId > 0 || i.isStarred) return@forEach
            val (out, hit) = applyRule(i, listOf(r.copy(enabled = true)))
            if (hit != null && out.category != i.category) { db.updateCategory(i.id, out.category, out.isAd); n++ }
        }
        if (n > 0) changed()
        n
    }

    /** Apps that notified in the last 30 days, busiest first (for the rule editor). */
    suspend fun knownApps(): List<AppCount> = withContext(Dispatchers.IO) {
        db.query(ItemQuery(source = Source.NOTIFICATION, sinceMillis = System.currentTimeMillis() - 30 * 86_400_000L, includeAds = true, limit = 8000))
            .groupBy { it.packageName }
            .map { (pkg, l) -> AppCount(l.first().appName, pkg, l.size) }
            .sortedByDescending { it.total }
            .take(16)
    }

    // ---------- remind me later ----------

    suspend fun remind(item: Item, at: Long) = withContext(Dispatchers.IO) {
        com.rez.notif.notif.Reminders.schedule(context, item.id, at, repeats = 0)
        changed()
    }

    suspend fun cancelReminder(item: Item) = withContext(Dispatchers.IO) {
        com.rez.notif.notif.Reminders.cancel(context, item.id)
        changed()
    }

    suspend fun reminderAt(itemId: Long): Long? = withContext(Dispatchers.IO) {
        db.reminders().firstOrNull { it.first == itemId }?.second
    }

    // ---------- bank alerts and bill reminders ----------

    /** A new SMS or notification just arrived: bank alerts and bill reminders. */
    private fun afterNew(item: Item) {
        if (item.isAd) return
        val s = prefs.current
        try {
            if (item.category == Category.BANK.id && (s.lowBalanceOn || s.bigSpendOn)) {
                Money.parse(item.body)?.let { tx ->
                    val id = db.idOf(item.sourceKey)
                    val who = if (item.source == Source.SMS) ContactNames.label(item.sender) else item.appName
                    val bal = tx.balance
                    when {
                        s.bigSpendOn && tx.out / 10 >= s.bigSpend ->
                            com.rez.notif.notif.BankAlerts.show(context, id, com.rez.notif.i18n.t("خرج بزرگ: ") + com.rez.notif.ui.components.toman(tx.out), who + (if (bal != null) com.rez.notif.i18n.t("  ·  مانده ") + com.rez.notif.ui.components.toman(bal) else ""))
                        s.lowBalanceOn && bal != null && bal / 10 < s.lowBalance ->
                            com.rez.notif.notif.BankAlerts.show(context, id, com.rez.notif.i18n.t("مانده کم: ") + com.rez.notif.ui.components.toman(bal), who)
                    }
                }
            }
            if (s.billReminders) scheduleBill(item)
            if (s.ledgerAsk) askLedger(item)
        } catch (_: Exception) { }
    }

    /** Sets a reminder before a bill's due date. Returns true when one was set. */
    private fun mayBeBill(i: Item) =
        !i.isAd && i.category != Category.ADS.id && i.category != Category.OPERATOR.id && i.id.toString() !in prefs.current.ignoredBills

    private fun scheduleBill(item: Item, existing: Set<Long> = emptySet()): Boolean {
        if (!mayBeBill(item)) return false
        val b = Bills.parse(item.title + "\n" + item.body) ?: return false
        val now = System.currentTimeMillis()
        if (b.due < now - 3_600_000L) return false
        val id = (if (item.id > 0) item.id else db.idOf(item.sourceKey)) ?: return false
        if (id in existing) return false
        val at = b.due - prefs.current.billDaysBefore * 86_400_000L
        com.rez.notif.notif.Reminders.schedule(context, id, maxOf(at, now + 60_000L), repeats = 0)
        return true
    }

    /** Looks for bills in the last 60 days and sets reminders for the ones still ahead. */
    suspend fun scanBills(): Int = withContext(Dispatchers.IO) {
        val existing = db.reminders().map { it.first }.toSet()
        db.query(ItemQuery(sinceMillis = System.currentTimeMillis() - 60 * 86_400_000L, limit = 5000))
            .count { scheduleBill(it, existing) }
    }

    /** Bills and installments that are not yet due (from the last 90 days of messages), soonest first. */
    suspend fun upcomingBills(): List<Pair<Item, Bills.Bill>> = withContext(Dispatchers.IO) {
        val from = dayStart()
        db.query(ItemQuery(sinceMillis = System.currentTimeMillis() - 90 * 86_400_000L, limit = 5000))
            .filter { mayBeBill(it) }
            .mapNotNull { i -> Bills.parse(i.title + "\n" + i.body)?.takeIf { it.due >= from }?.let { i to it } }
            .distinctBy { it.second.due to it.first.sender }
            .sortedBy { it.second.due }
    }

    /** "This is not a bill": hide it from the list and drop its reminder. */
    suspend fun ignoreBill(item: Item) = withContext(Dispatchers.IO) {
        prefs.update { it.copy(ignoredBills = it.ignoredBills + item.id.toString()) }
        com.rez.notif.notif.Reminders.cancel(context, item.id)
        changed()
    }

    suspend fun myBills(): List<Db.MyBill> = withContext(Dispatchers.IO) { db.myBills() }

    suspend fun saveMyBill(b: Db.MyBill) = withContext(Dispatchers.IO) {
        val id = db.saveMyBill(b)
        com.rez.notif.notif.Reminders.scheduleBill(context, b.copy(id = id))
        changed()
    }

    suspend fun deleteMyBill(id: Long) = withContext(Dispatchers.IO) {
        db.deleteMyBill(id)
        com.rez.notif.notif.Reminders.cancelBill(context, id)
        changed()
    }

    // ---------- monthly budget ----------

    /** This Persian month as yyyymm. */
    fun thisMonth(): Int {
        val c = Calendar.getInstance()
        val (jy, jm, _) = Bills.gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return com.rez.notif.classify.Budget.of(jy, jm)
    }

    /** One month: the user's lines, their installments (Money screen), and what bank SMS say really went out/in (toman). */
    data class BudgetView(
        val month: com.rez.notif.classify.Budget.Month,
        /** From bank SMS this month, own transfers left out (toman). */
        val bankOut: Long,
        val bankIn: Long,
        val bankInternal: Long,
        val byKind: List<Pair<com.rez.notif.classify.Spending.Kind, Long>>,
        val bills: List<Db.MyBill>,
        /** Bills ticked as paid this month. */
        val paid: Set<Long>,
    )

    suspend fun budget(month: Int): BudgetView = withContext(Dispatchers.IO) {
        val bills = db.myBills().filter { it.amount > 0 }
        val m = com.rez.notif.classify.Budget.month(db.budgetLines(), month, bills.sumOf { it.amount })
        val start = (Bills.toMillis(month / 100, month % 100, 1) ?: 0L) - 9 * 3_600_000L
        val next = com.rez.notif.classify.Budget.next(month)
        val end = (Bills.toMillis(next / 100, next % 100, 1) ?: Long.MAX_VALUE) - 9 * 3_600_000L
        val s = if (start > 0) com.rez.notif.classify.Spending.summarize(bankTagged(start, end).second) else null
        val paid = bills.filter { isPaid(it.id, month) }.map { it.id }.toSet()
        BudgetView(
            m, (s?.out ?: 0) / 10, (s?.inn ?: 0) / 10, (s?.internal ?: 0) / 10,
            s?.byKind.orEmpty().map { it.first to it.second / 10 }, bills, paid,
        )
    }

    fun isPaid(billId: Long, month: Int) = "$billId:$month" in prefs.current.paidBills

    /** Ticks (or unticks) a hand-entered installment as paid for [month]; its reminders for that month stay quiet. */
    suspend fun setPaid(billId: Long, month: Int, paid: Boolean) = withContext(Dispatchers.IO) {
        val key = "$billId:$month"
        prefs.update { it.copy(paidBills = if (paid) it.paidBills + key else it.paidBills - key) }
        changed()
    }

    suspend fun saveBudgetLine(b: com.rez.notif.classify.BudgetLine) = withContext(Dispatchers.IO) { db.saveBudgetLine(b); changed() }

    /** A one-off line is deleted. A monthly line stops from [month] on, so earlier months keep their numbers. */
    suspend fun removeBudgetLine(b: com.rez.notif.classify.BudgetLine, month: Int) = withContext(Dispatchers.IO) {
        if (!b.monthly || month <= b.month) db.deleteBudgetLine(b.id)
        else db.saveBudgetLine(b.copy(until = com.rez.notif.classify.Budget.prev(month)))
        changed()
    }

    // ---------- ledger (دفتر حساب) ----------

    /** Persian month [month] (yyyymm) as [start, end) in millis, the same way as the budget screen. */
    fun monthRange(month: Int): Pair<Long, Long> {
        val start = (Bills.toMillis(month / 100, month % 100, 1) ?: 0L) - 9 * 3_600_000L
        val next = com.rez.notif.classify.Budget.next(month)
        val end = (Bills.toMillis(next / 100, next % 100, 1) ?: Long.MAX_VALUE) - 9 * 3_600_000L
        return start to end
    }

    /** Blu bank sends app notifications instead of SMS. */
    private fun isBlu(i: Item): Boolean = isBluApp(i.packageName, i.appName)

    private fun ledgerText(i: Item) = if (i.source == Source.SMS) i.body else i.title + "\n" + i.body

    private fun ledgerRaw(i: Item): Ledger.Raw? {
        val text = ledgerText(i)
        val m = Money.parse(text) ?: return null
        return Ledger.Raw(i.id, i.timestamp, if (i.source == Source.SMS) i.sender else "Blu", text, m.out, m.inn, m.balance)
    }

    /** Every ledger entry between [since] and [until]: bank SMS, Blu notifications and hand-entered ones. */
    private fun ledgerEntries(since: Long, until: Long): List<Ledger.Entry> {
        val sms = db.query(ItemQuery(category = Category.BANK.id, source = Source.SMS, sinceMillis = since, limit = 10000)).filter { it.timestamp < until }
        val blu = db.query(ItemQuery(source = Source.NOTIFICATION, sinceMillis = since, includeAds = true, limit = 10000))
            .filter { it.timestamp < until && isBlu(it) }
            .distinctBy { ledgerText(it) + "|" + it.timestamp / 120_000L }
        val raws = (sms + blu).mapNotNull(::ledgerRaw)
        val built = Ledger.build(raws, db.ledgerTags(), db.ledgerLearned(), emptySet())
        val manual = db.manualTxs(since, until).map {
            Ledger.Entry(-it.id, it.time, it.account, it.outRial, it.inRial, null, com.rez.notif.classify.Spending.Kind.OTHER_OUT, "", it.category, it.note, manual = true)
        }
        return built + manual
    }

    data class LedgerView(
        val month: Int,
        /** This month's entries, newest first. */
        val entries: List<Ledger.Entry>,
        /** Missing SMS this month (amount in rial). */
        val gaps: List<Ledger.Gap>,
        val summary: Ledger.Summary,
        /** Last-4 digits of cards the user marked as theirs. */
        val myCards: List<String>,
        /** Accounts seen (for hand entries), most used first. */
        val accounts: List<String>,
        /** Categories in the order to offer them: most used first. */
        val categories: List<String>,
        /** Bank SMS about money whose amount could not be read (not yet handled). */
        val unreadable: List<Item> = emptyList(),
        /** Monthly limit per category (toman). */
        val limits: Map<String, Long> = emptyMap(),
        /** Free to spend until the end of the month (toman). */
        val left: Ledger.Left? = null,
        val daysLeft: Int = 0,
        /** True when the income figure comes from "دخل و خرج" lines, false when from this month's deposits. */
        val plannedIncome: Boolean = false,
        val chequesSoon: List<com.rez.notif.classify.Cheque> = emptyList(),
        val goals: List<com.rez.notif.classify.Goal> = emptyList(),
        val commitments: com.rez.notif.classify.Commitments.Totals? = null,
    )

    suspend fun ledger(month: Int): LedgerView = withContext(Dispatchers.IO) {
        val (start, end) = monthRange(month)
        // Read 40 days more, so the first SMS of the month has a balance before it.
        val all = ledgerEntries(start - 40 * 86_400_000L, end)
        val inMonth = all.filter { it.time in start until end }.sortedByDescending { it.time }
        val gaps = Ledger.gaps(all).filter { it.to in start until end }
        val learned = db.ledgerLearned()
        val myCards = learned.filter { it.key.startsWith("card:") && it.value == Ledger.OWN }.keys.map { it.removePrefix("card:") }.sorted()
        val summary = Ledger.summarize(inMonth)
        val tags = db.ledgerTags()
        val unreadable = db.query(ItemQuery(category = Category.BANK.id, source = Source.SMS, sinceMillis = start, limit = 3000))
            .filter { it.timestamp < end && tags[it.id] == null && Ledger.unreadable(it.body, Money.parse(it.body)) }
        // What is left: planned income (or this month's deposits) minus fixed costs, savings and the rest of the spending.
        val bills = db.myBills().filter { it.amount > 0 }
        val plan = com.rez.notif.classify.Budget.month(db.budgetLines(), month, bills.sumOf { it.amount })
        val goals = db.goals()
        val planned = plan.income > 0
        val income = if (planned) plan.income else summary.inn / 10
        val fixedPlanned = plan.spending + plan.installments
        val fixedActual = summary.byCategory.filter { it.first in Ledger.FIXED_CATS }.sumOf { it.second } / 10
        val daysLeft = when {
            month < thisMonth() -> 0
            month > thisMonth() -> Ledger.monthDays(month)
            else -> {
                val c = Calendar.getInstance()
                val (_, _, jd) = Bills.gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                (Ledger.monthDays(month) - jd + 1).coerceAtLeast(1)
            }
        }
        val left = Ledger.leftToSpend(
            income, maxOf(fixedPlanned, fixedActual), Ledger.variableSpent(summary) / 10,
            goals.filter { !it.done }.sumOf { it.monthly }, daysLeft,
        )
        val cheques = db.cheques()
        LedgerView(
            month, inMonth, gaps, summary, myCards,
            all.groupingBy { it.account }.eachCount().entries.sortedByDescending { it.value }.map { it.key },
            categoryOrder(),
            unreadable, db.limits(), left.takeIf { income > 0 }, daysLeft, planned,
            com.rez.notif.classify.Commitments.dueSoon(cheques, System.currentTimeMillis()), goals,
            com.rez.notif.classify.Commitments.totals(db.debts(), cheques),
        )
    }

    // ---------- home of "شاخ گاو" ----------

    /** Something due in the next days: a cheque, an installment or a bill. [toman] 0 = amount not known. */
    data class Upcoming(val title: String, val toman: Long, val due: Long, val kind: String)

    data class HomeView(
        val balances: List<Ledger.Balance>,
        /** Sum of the balances at most a week old (rial); older cards are shown dimmed. */
        val total: Long,
        val left: Ledger.Left?,
        val daysLeft: Int,
        /** Spending that still needs a category, newest first. */
        val unsorted: List<Ledger.Entry>,
        val categories: List<String>,
        val next7: List<Upcoming>,
        /** Today's entries, newest first. */
        val today: List<Ledger.Entry>,
        /** Today's biggest spending (rial), for the "big spend" joke. */
        val bigToday: Long,
    )

    suspend fun home(): HomeView {
        val month = thisMonth()
        val v = ledger(month)
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val all = ledgerEntries(now - 120L * 86_400_000L, Long.MAX_VALUE)
            val balances = Ledger.balances(all, now)
            val weekEnd = now + 7 * 86_400_000L
            val next = ArrayList<Upcoming>()
            com.rez.notif.classify.Commitments.dueSoon(db.cheques(), now).forEach { q ->
                next += Upcoming(q.person, q.toman, q.due, if (q.incoming) "cheque_in" else "cheque_out")
            }
            // Hand-entered installments/bills: day of the Persian month.
            val c = Calendar.getInstance()
            val (_, _, jd) = Bills.gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            val days = Ledger.monthDays(month)
            db.myBills().filter { !isPaid(it.id, month) }.forEach { b ->
                val ahead = if (b.day >= jd) b.day - jd else b.day + days - jd
                if (ahead in 0..7) next += Upcoming(b.title, b.amount, dayStart() + ahead * 86_400_000L, "bill")
            }
            next += db.query(ItemQuery(sinceMillis = now - 90 * 86_400_000L, limit = 5000))
                .filter { mayBeBill(it) }
                .mapNotNull { i -> Bills.parse(i.title + "\n" + i.body)?.takeIf { it.due in dayStart()..weekEnd }?.let { i to it } }
                .distinctBy { it.second.due to it.first.sender }
                .map { (i, b) -> Upcoming(i.sender.ifBlank { i.title }, (b.amount ?: 0) / 10, b.due, "sms") }
            val today = v.entries.filter { it.time >= dayStart() }
            HomeView(
                balances.sortedByDescending { Ledger.isFresh(it, now) }, Ledger.freshTotal(balances, now), v.left, v.daysLeft,
                v.entries.filter { it.needsCategory }, v.categories,
                next.sortedBy { it.due }, today,
                today.filter { it.counted }.maxOfOrNull { it.out } ?: 0,
            )
        }
    }

    /** A bank SMS the app could not read: the user types the amount (or skips it). Either way it leaves the queue. */
    suspend fun handleUnreadable(item: Item, toman: Long, income: Boolean, category: String) = withContext(Dispatchers.IO) {
        if (toman > 0) {
            val account = Ledger.accountOf(item.sender, item.body, income)
            db.saveManualTx(Db.ManualTx(0, item.timestamp, account, if (income) 0 else toman * 10, if (income) toman * 10 else 0, category, "پیامک ناخوانا"))
        }
        db.setLedgerTag(item.id, Ledger.IGNORE)
        changed()
    }

    suspend fun setLimit(category: String, toman: Long) = withContext(Dispatchers.IO) { db.setLimit(category, toman); changed() }

    // ---------- commitments: debts, cheques, goals ----------

    data class CommitmentsView(
        val debts: List<com.rez.notif.classify.Debt>,
        val cheques: List<com.rez.notif.classify.Cheque>,
        val goals: List<com.rez.notif.classify.Goal>,
        val bills: List<Db.MyBill>,
        val totals: com.rez.notif.classify.Commitments.Totals,
        val people: List<Pair<String, Long>>,
    )

    suspend fun commitments(): CommitmentsView = withContext(Dispatchers.IO) {
        val d = db.debts(); val c = db.cheques()
        CommitmentsView(d, c, db.goals(), db.myBills(), com.rez.notif.classify.Commitments.totals(d, c), com.rez.notif.classify.Commitments.byPerson(d))
    }

    suspend fun saveDebt(d: com.rez.notif.classify.Debt) = withContext(Dispatchers.IO) { db.saveDebt(d); changed() }
    suspend fun deleteDebt(id: Long) = withContext(Dispatchers.IO) { db.deleteDebt(id); changed() }

    suspend fun saveCheque(q: com.rez.notif.classify.Cheque) = withContext(Dispatchers.IO) {
        val id = db.saveCheque(q)
        com.rez.notif.notif.Reminders.scheduleCheque(context, q.copy(id = id))
        changed()
    }
    suspend fun deleteCheque(id: Long) = withContext(Dispatchers.IO) {
        db.deleteCheque(id)
        com.rez.notif.notif.Reminders.cancelCheque(context, id)
        changed()
    }

    suspend fun saveGoal(g: com.rez.notif.classify.Goal) = withContext(Dispatchers.IO) { db.saveGoal(g); changed() }
    suspend fun deleteGoal(id: Long) = withContext(Dispatchers.IO) { db.deleteGoal(id); changed() }

    /** For the cheque reminder: the cheque itself. */
    fun cheque(id: Long): com.rez.notif.classify.Cheque? = db.cheques().firstOrNull { it.id == id }

    // ---------- export ----------

    private fun jalaliDate(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val (y, m, d) = Bills.gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return String.format(java.util.Locale.US, "%04d/%02d/%02d", y, m, d)
    }

    /** The ledger of [month] (0 = everything) as a CSV file Excel opens, written to [uri]. Returns the row count. */
    suspend fun exportLedgerCsv(uri: Uri, month: Int): Int = withContext(Dispatchers.IO) {
        val (start, end) = if (month == 0) 0L to Long.MAX_VALUE else monthRange(month)
        val entries = ledgerEntries(start, end).filter { it.time in start until end && (it.out > 0 || it.inn > 0) }.sortedBy { it.time }
        val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val rows = entries.map { e ->
            val kind = when {
                e.isOwn -> "به خودم"
                e.category == Ledger.IGNORE -> "نادیده"
                e.inn > 0 -> "واریز"
                else -> "خرج"
            }
            listOf(
                jalaliDate(e.time), hhmm.format(java.util.Date(e.time)), e.account, kind, ((e.out + e.inn) / 10).toString(),
                e.category?.takeIf { it != Ledger.OWN && it != Ledger.IGNORE } ?: "", e.party.substringAfter(':'), e.text.replace('\n', ' '),
            )
        }
        val text = com.rez.notif.classify.Commitments.csv(listOf("تاریخ", "ساعت", "حساب", "نوع", "مبلغ (تومان)", "دسته", "طرف", "متن"), rows)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("cannot write file")
        rows.size
    }

    /** Spending categories, the ones the user picks most first. */
    private fun categoryOrder(): List<String> {
        val used = (db.ledgerTags().values + db.ledgerLearned().values + db.manualTxs().map { it.category })
            .filter { it in Ledger.SPEND_CATS }.groupingBy { it }.eachCount()
        return Ledger.SPEND_CATS.sortedByDescending { used[it] ?: 0 }
    }

    /** The user names an entry's category. With [learn], the same card/shop gets it from now on. */
    suspend fun setLedgerCategory(e: Ledger.Entry, category: String, learn: Boolean = true) = withContext(Dispatchers.IO) {
        if (e.manual) {
            db.manualTxs().firstOrNull { it.id == -e.id }?.let { db.saveManualTx(it.copy(category = category)) }
        } else {
            db.setLedgerTag(e.id, category)
            if (learn && e.party.isNotEmpty()) db.setLearned(e.party, category)
        }
        changed()
    }

    /** From the "what was this?" notification buttons. */
    suspend fun categorizeItem(itemId: Long, category: String) = withContext(Dispatchers.IO) {
        val item = db.get(itemId) ?: return@withContext
        db.setLedgerTag(itemId, category)
        val party = Ledger.partyOf(ledgerText(item), ledgerRaw(item)?.let { it.inn > 0 && it.out == 0L } ?: false)
        if (party.isNotEmpty()) db.setLearned(party, category)
        changed()
    }

    /** Names a missing SMS: a hand entry on that account, just before the SMS that showed the difference. */
    suspend fun nameGap(g: Ledger.Gap, category: String) = withContext(Dispatchers.IO) {
        db.saveManualTx(Db.ManualTx(0, g.to - 1, g.account, maxOf(g.amount, 0), maxOf(-g.amount, 0), category, "پیامک نیومده"))
        changed()
    }

    /** Hand-entered spending or income (toman). Cash unless an account is given. */
    suspend fun addManual(toman: Long, income: Boolean, category: String, note: String, account: String = "نقد", time: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            db.saveManualTx(Db.ManualTx(0, time, account, if (income) 0 else toman * 10, if (income) toman * 10 else 0, category, note))
            changed()
        }

    suspend fun deleteManual(e: Ledger.Entry) = withContext(Dispatchers.IO) {
        if (e.manual) db.deleteManualTx(-e.id)
        changed()
    }

    suspend fun setMyCard(last4: String, mine: Boolean) = withContext(Dispatchers.IO) {
        db.setLearned("card:$last4", if (mine) Ledger.OWN else null)
        changed()
    }

    /** After a new bank message: if it is spending the app cannot sort, ask with one notification. */
    private fun askLedger(item: Item) {
        val isBank = (item.source == Source.SMS && item.category == Category.BANK.id) || (item.source == Source.NOTIFICATION && isBlu(item))
        if (!isBank) return
        val id = (if (item.id > 0) item.id else db.idOf(item.sourceKey)) ?: return
        val now = System.currentTimeMillis()
        // The last hour too, so a transfer to my own other card (its deposit SMS) is recognized.
        val recent = ledgerEntries(now - 3_600_000L, now + 60_000L)
        val e = recent.firstOrNull { it.id == id } ?: return
        if (!e.needsCategory) return
        com.rez.notif.notif.LedgerAsk.show(context, id, e.out / 10, categoryOrder().take(2))
    }

    // ---------- dong ----------

    suspend fun trips(): List<com.rez.notif.classify.DongTrip> = withContext(Dispatchers.IO) { db.trips() }

    suspend fun trip(id: Long): com.rez.notif.classify.DongTrip? = withContext(Dispatchers.IO) { db.trips().firstOrNull { it.id == id } }

    suspend fun saveTrip(t: com.rez.notif.classify.DongTrip): Long = withContext(Dispatchers.IO) { db.saveTrip(t).also { changed() } }

    suspend fun deleteTrip(id: Long) = withContext(Dispatchers.IO) { db.deleteTrip(id); changed() }

    /** After "days before" changes: move every hand-entered bill's reminder. */
    suspend fun rescheduleMyBills() = withContext(Dispatchers.IO) {
        db.myBills().forEach { com.rez.notif.notif.Reminders.scheduleBill(context, it) }
    }

    /** The newest login code from the last 30 minutes: (code, from). For the quick-settings tile. */
    fun latestCode(): Pair<String, String>? {
        val now = System.currentTimeMillis()
        return db.query(ItemQuery(category = Category.OTP.id, sinceMillis = now - 30 * 60_000L, limit = 5)).firstNotNullOfOrNull { i ->
            Classifier.extractCode(i.title + "\n" + i.body)?.let { it to (if (i.source == Source.SMS) ContactNames.label(i.sender) else i.appName) }
        }
    }

    // ---------- bank summary ----------

    data class BankAccount(val sender: String, val balance: Long, val time: Long)
    data class MoneySummary(
        val out: Long, val inn: Long, val count: Int, val accounts: List<BankAccount>, val since: Long,
        /** Moved between the user's own accounts (rial); not in [out] or [inn]. */
        val internal: Long = 0,
        /** Money per kind of transaction (rial), biggest first. */
        val byKind: List<Pair<com.rez.notif.classify.Spending.Kind, Long>> = emptyList(),
    )

    /** Bank SMS between [since] and [until], read and tagged (own transfers, card transfer, online, store...). */
    private fun bankTagged(since: Long, until: Long = Long.MAX_VALUE): Pair<List<Item>, List<com.rez.notif.classify.Spending.Tagged>> {
        val items = db.query(ItemQuery(category = Category.BANK.id, source = Source.SMS, sinceMillis = since, limit = 5000)).filter { it.timestamp < until }
        val txs = items.mapNotNull { i ->
            val tx = Money.parse(i.body) ?: return@mapNotNull null
            if (tx.out == 0L && tx.inn == 0L) null else com.rez.notif.classify.Spending.Tx(i.id, i.timestamp, i.sender, i.body, tx.out, tx.inn)
        }
        return items to com.rez.notif.classify.Spending.tag(txs)
    }

    /** Spending and income of the last 30 days from bank SMS, and the latest balance of each bank. Rial. */
    suspend fun moneySummary(): MoneySummary? = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - 30 * 86_400_000L
        val (items, tagged) = bankTagged(since)
        val accounts = LinkedHashMap<String, BankAccount>()
        for (i in items) { // newest first
            val b = Money.parse(i.body)?.balance ?: continue
            if (i.sender !in accounts) accounts[i.sender] = BankAccount(i.sender, b, i.timestamp)
        }
        val s = com.rez.notif.classify.Spending.summarize(tagged)
        if (tagged.isEmpty() && accounts.isEmpty()) null
        else MoneySummary(s.out, s.inn, s.count, accounts.values.toList(), since, s.internal, s.byKind)
    }

    // ---------- weekly report ----------

    data class Weekly(val total: Int, val hidden: Int, val ads: Int, val noisy: List<AppCount>)

    /** This week in numbers, and the noisiest apps (with a one-tap rule to quiet them). */
    suspend fun weekly(): Weekly = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - 7 * 86_400_000L
        val items = db.query(ItemQuery(sinceMillis = since, includeAds = true, limit = 20000))
        val notifs = items.filter { it.source == Source.NOTIFICATION }
        val noisy = notifs.filter { it.vipId == 0L && it.category !in setOf(Category.OTP.id, Category.BANK.id, Category.PERSONAL.id) }
            .groupBy { it.packageName }
            .map { (pkg, l) -> AppCount(l.first().appName, pkg, l.size) }
            .sortedByDescending { it.total }
            .take(3)
        Weekly(items.size, if (prefs.current.hideAll) notifs.size else notifs.count { it.isAd }, items.count { it.isAd }, noisy)
    }

    /**
     * What arrived after the app was last opened and is still unread (for the summary notification). Ads are left out.
     * SMS the user already read in the phone's messages app count as read here too.
     */
    fun newSince(since: Long): List<Item> {
        val items = db.query(ItemQuery(sinceMillis = maxOf(since + 1, System.currentTimeMillis() - 24 * 3_600_000L), limit = 300))
            .filter { !it.isRead }
        val sms = items.filter { it.source == Source.SMS }
        if (sms.isEmpty()) return items
        val read = com.rez.notif.sms.SmsReader.readBodies(context, sms.minOf { it.timestamp } - 120_000L)
        val readElsewhere = sms.filter { it.body.trim() in read }
        if (readElsewhere.isEmpty()) return items
        db.markRead(readElsewhere.map { it.id })
        changed()
        val gone = readElsewhere.map { it.id }.toSet()
        return items.filter { it.id !in gone }
    }

    /** The app's own notification for this item was opened or swiped away, so the user has seen it. */
    fun seenByKey(sourceKey: String): Boolean {
        val id = db.idOf(sourceKey) ?: return false
        db.markRead(listOf(id))
        changed()
        return true
    }

    /** The user tapped an app's icon in the summary: that app's new items are seen. */
    fun seenApp(pkg: String, sms: Boolean) {
        val since = maxOf(prefs.current.lastSeen, prefs.current.summarySeen)
        val ids = newSince(since).filter { if (sms) it.source == Source.SMS || it.packageName.isBlank() else it.packageName == pkg && it.source != Source.SMS }.map { it.id }
        if (ids.isEmpty()) return
        db.markRead(ids)
        changed()
    }

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
        val rule = Rules.first(rules(), Rules.Input(isSmsApp, packageName, title, text, r.category.id, hourOf(postTime)))
        when (rule?.action) {
            RuleAction.ALERT -> return true
            RuleAction.QUIET, RuleAction.DELETE, RuleAction.REMIND -> return false
            else -> {}
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

    /**
     * Imports a password export (CSV from Google, Edge, Firefox, Bitwarden...). Skips what is already in the
     * wallet. Returns (added, found). Needs the wallet open; throws WalletVault.Locked otherwise.
     */
    suspend fun walletImport(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("cannot read file")
        val found = com.rez.notif.wallet.PasswordCsv.parse(text)
        val have = walletEntries().map { Triple(it.title, it.username, it.secret) }.toMutableSet()
        var added = 0
        for (e in found) {
            val k = Triple(e.title, e.username, e.secret)
            if (k in have) continue
            db.walletPut(0, e.title.trim(), e.kind.name, WalletVault.encrypt(WalletCodec.encode(e)), System.currentTimeMillis())
            have += k
            added++
        }
        added to found.size
    }

    // ---------- move messages to another phone ----------

    /** What to take: these categories, plus (optionally) chosen people and starred/valuable ones, since [sinceMillis]. Ads never. */
    data class TransferChoice(val categories: Set<String>, val vip: Boolean, val kept: Boolean, val sinceMillis: Long?)

    private suspend fun transferItems(c: TransferChoice): List<Item> = withContext(Dispatchers.IO) {
        db.query(ItemQuery(sinceMillis = c.sinceMillis, limit = 500_000)).filter { i ->
            !i.isAd && (i.category in c.categories || (c.vip && i.vipId > 0) || (c.kept && (i.isStarred || i.isValuable)))
        }
    }

    suspend fun transferCount(c: TransferChoice): Int = transferItems(c).size

    /** Writes the chosen messages (and the chosen people) to [uri], locked with [password]. Returns how many messages. */
    suspend fun exportTransfer(uri: Uri, password: CharArray, c: TransferChoice): Int = withContext(Dispatchers.IO) {
        val items = transferItems(c)
        val vips = db.vips()
        val vipName = vips.associate { it.id to it.name }
        val rows = ArrayList<List<String>>()
        vips.forEach { v -> rows += listOf("V", v.name, v.numbers.joinToString(",")) }
        items.forEach { i ->
            rows += listOf(
                "I", i.source.name, i.sourceKey, i.packageName, i.appName, i.sender, i.title, i.body, i.timestamp.toString(),
                i.simSlot.toString(), i.category, b(i.isValuable), b(i.isStarred), b(i.isRead), vipName[i.vipId].orEmpty(),
            )
        }
        val locked = BackupCrypto.encrypt(com.rez.notif.classify.Records.encode(rows).toByteArray(Charsets.UTF_8), password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(locked) } ?: error("cannot write file")
        items.size
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    data class TransferResult(val added: Int, val total: Int, val inbox: Int)

    /**
     * Adds the messages in a transfer file to this phone; what is already here is skipped. With [toInbox] (and Notif as
     * the phone's SMS app), new SMS are also written into the phone's own messages. Throws BackupCrypto / Records errors.
     */
    suspend fun importTransfer(uri: Uri, password: CharArray, toInbox: Boolean): TransferResult = withContext(Dispatchers.IO) {
        val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("cannot read file")
        val rows = com.rez.notif.classify.Records.decode(BackupCrypto.decrypt(data, password).toString(Charsets.UTF_8))
        val vipIds = db.vips().associate { it.name to it.id }.toMutableMap()
        rows.filter { it.firstOrNull() == "V" && it.size >= 3 }.forEach { r ->
            if (r[1] !in vipIds) vipIds[r[1]] = db.addVip(r[1], r[2].split(',').filter { it.isNotBlank() })
        }
        vipCache = null
        val inbox = toInbox && com.rez.notif.sms.DefaultSms.isDefault(context)
        var added = 0; var total = 0; var wrote = 0
        rows.filter { it.firstOrNull() == "I" && it.size >= 15 }.forEach { r ->
            total++
            val item = runCatching {
                Item(
                    source = Source.valueOf(r[1]), sourceKey = r[2], packageName = r[3], appName = r[4], sender = r[5], title = r[6],
                    body = r[7], timestamp = r[8].toLong(), simSlot = r[9].toIntOrNull() ?: 0, category = r[10],
                    isValuable = r[11] == "1", isStarred = r[12] == "1", isRead = r[13] == "1", vipId = vipIds[r[14]] ?: 0,
                )
            }.getOrNull() ?: return@forEach
            if (db.insert(item)) {
                added++
                if (inbox && item.source == Source.SMS) {
                    com.rez.notif.sms.DefaultSms.writeInbox(context, com.rez.notif.sms.RawSms(0, item.sender, item.body, item.timestamp, -1), item.timestamp, read = true)
                    wrote++
                }
            }
        }
        changed()
        TransferResult(added, total, wrote)
    }

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
        rulesCache = null
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
