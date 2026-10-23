package com.rez.notif.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rez.notif.classify.Analyzer
import com.rez.notif.classify.Category
import com.rez.notif.classify.Classifier
import com.rez.notif.classify.VipMatcher
import com.rez.notif.classify.VipPerson
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
)

/** Everything the screens and the background parts need, in one place. */
class Repository(private val context: Context, private val db: Db, val prefs: Prefs) {

    /** Goes up by one on every change, so screens know when to reload. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()
    private fun changed() = _version.update { it + 1 }

    /** Tap actions of live notifications, to open the exact chat/email. Lost when the app process dies. */
    private val contentIntents = ConcurrentHashMap<String, PendingIntent>()

    @Volatile private var overrides: Map<String, String>? = null
    private fun overrides(): Map<String, String> = overrides ?: db.overrides().also { overrides = it }

    @Volatile private var vipCache: List<VipPerson>? = null
    private fun vipPeople(): List<VipPerson> = vipCache ?: db.vips().also { vipCache = it }

    // ---------- incoming ----------

    private fun smsKey(address: String, body: String, date: Long): String =
        "sms:" + Classifier.normalize(address).replace(" ", "") + ":" + body.hashCode() + ":" + date / 600_000L

    private fun smsToItem(raw: RawSms): Item {
        val vip = VipMatcher.match(vipPeople(), true, raw.address, "", raw.body)
        val r = vipSafe(Classifier.classify(Classifier.Input(isSms = true, sender = raw.address, title = "", body = raw.body), overrides()), vip)
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

    /** A chosen person's message is never an ad. */
    private fun vipSafe(r: Classifier.Result, vip: VipPerson?): Classifier.Result =
        if (vip != null && r.category == Category.ADS) r.copy(category = Category.PERSONAL) else r

    fun addSms(raw: RawSms) {
        if (db.insert(smsToItem(raw))) changed()
    }

    /** Stores a notification. Returns true when the listener should remove it from the status bar (an ad). */
    fun addNotification(n: NotificationIn): Boolean {
        val hideAds = prefs.current.hideAds
        if (n.isSmsApp) {
            // The SMS itself is stored by SmsReceiver. Here we only decide whether to hide its notification.
            val vip = VipMatcher.match(vipPeople(), true, n.title, "", n.text)
            val r = vipSafe(Classifier.classify(Classifier.Input(true, n.title, "", n.text), overrides()), vip)
            return hideAds && r.category == Category.ADS
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
        return hideAds && r.category == Category.ADS
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

    suspend fun items(q: ItemQuery): List<Item> = withContext(Dispatchers.IO) { db.query(q) }
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
        changed()
    }

    suspend fun toggleStar(item: Item) = withContext(Dispatchers.IO) {
        db.setStarred(item.id, !item.isStarred)
        changed()
    }

    suspend fun markRead(items: List<Item>) = withContext(Dispatchers.IO) {
        val ids = items.filter { !it.isRead }.map { it.id }
        if (ids.isNotEmpty()) {
            db.markRead(ids)
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
        changed()
        ids
    }

    suspend fun undoDelete(ids: List<Long>) = withContext(Dispatchers.IO) {
        db.delete(ids, deleted = false)
        changed()
    }

    /** Everything from the same SMS sender, or the same app. */
    suspend fun deleteFromSender(item: Item): List<Long> = withContext(Dispatchers.IO) {
        val ids = if (item.source == Source.SMS) {
            db.ids(ItemQuery(source = Source.SMS, sender = item.sender, includeAds = true))
        } else {
            db.ids(ItemQuery(source = Source.NOTIFICATION, packageName = item.packageName, includeAds = true))
        }
        db.delete(ids)
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
        if (vip != null) return true
        val r = if (isSmsApp) {
            Classifier.classify(Classifier.Input(true, title, "", text), overrides())
        } else {
            Classifier.classify(Classifier.Input(false, title, title, text, packageName), overrides())
        }
        if (r.isValuable) return true
        if (r.category == Category.OTP) return System.currentTimeMillis() - postTime < 10 * 60_000L
        return r.category.id in prefs.current.mainCategories
    }

    // ---------- chosen people ----------

    data class VipSummary(val person: VipPerson, val count: Int, val last: Long)

    suspend fun vipSummaries(): List<VipSummary> = withContext(Dispatchers.IO) {
        val counts = db.vipCounts()
        db.vips().map { p -> VipSummary(p, counts[p.id]?.first ?: 0, counts[p.id]?.second ?: 0L) }
            .sortedByDescending { it.last }
    }

    suspend fun addVip(name: String, rawNumbers: List<String>): Long = withContext(Dispatchers.IO) {
        val numbers = rawNumbers.mapNotNull { VipMatcher.key(it) }.distinct()
        val id = db.addVip(name.trim().ifEmpty { rawNumbers.firstOrNull() ?: "?" }, numbers)
        vipCache = null
        retagVips()
        id
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
