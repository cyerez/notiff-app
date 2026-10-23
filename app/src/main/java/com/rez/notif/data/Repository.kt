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

    // ---------- incoming ----------

    private fun smsKey(address: String, body: String, date: Long): String =
        "sms:" + Classifier.normalize(address).replace(" ", "") + ":" + body.hashCode() + ":" + date / 600_000L

    private fun smsToItem(raw: RawSms): Item {
        val r = Classifier.classify(Classifier.Input(isSms = true, sender = raw.address, title = "", body = raw.body), overrides())
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
        )
    }

    fun addSms(raw: RawSms) {
        if (db.insert(smsToItem(raw))) changed()
    }

    /** Stores a notification. Returns true when the listener should remove it from the status bar (an ad). */
    fun addNotification(n: NotificationIn): Boolean {
        val hideAds = prefs.current.hideAds
        if (n.isSmsApp) {
            // The SMS itself is stored by SmsReceiver. Here we only decide whether to hide its notification.
            val r = Classifier.classify(Classifier.Input(true, n.title, "", n.text), overrides())
            return hideAds && r.category == Category.ADS
        }
        val r = Classifier.classify(
            Classifier.Input(isSms = false, sender = n.title, title = n.title, body = n.text, packageName = n.packageName),
            overrides(),
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
            val smoothed = items.zip(msgs) { item, m ->
                item.copy(category = m.category.id, isAd = m.category == Category.ADS, isValuable = item.isValuable && m.category != Category.ADS)
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
