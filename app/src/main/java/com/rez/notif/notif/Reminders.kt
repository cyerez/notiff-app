package com.rez.notif.notif

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rez.notif.MainActivity
import com.rez.notif.NotifApp
import com.rez.notif.R
import com.rez.notif.data.Db
import com.rez.notif.i18n.t
import com.rez.notif.sms.ContactNames
import java.util.Calendar

/**
 * "Remind me later" and "remind me until I see it".
 * Reminders live in the database; one alarm wakes us for the soonest one.
 * No exact-alarm permission: Android may deliver a few minutes late, which is fine here.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_ITEM = "open_item_id"
    /** Gap between repeats of a "until I see it" reminder. */
    private const val REPEAT_GAP = 30 * 60_000L

    private fun db(context: Context): Db = NotifApp.repo(context).database

    fun schedule(context: Context, itemId: Long, at: Long, repeats: Int) {
        db(context).putReminder(itemId, at, repeats)
        if (at <= System.currentTimeMillis()) fire(context) else arm(context)
    }

    fun cancel(context: Context, itemId: Long) {
        db(context).removeReminder(itemId)
        context.getSystemService(NotificationManager::class.java)?.cancel(notifId(itemId))
        arm(context)
    }

    /** Opening an item counts as seeing it: its "until I see it" reminder stops. */
    fun seen(context: Context, itemId: Long) {
        val r = db(context).reminders().firstOrNull { it.first == itemId } ?: return
        context.getSystemService(NotificationManager::class.java)?.cancel(notifId(itemId))
        if (r.third > 0) { db(context).removeReminder(itemId); arm(context) }
    }

    /** Sets the alarm for the soonest reminder (or clears it). */
    fun arm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context, AlarmReceiver.ACTION_REMIND)
        val next = db(context).reminders().firstOrNull()
        if (next == null) { am.cancel(pi); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(next.second, System.currentTimeMillis() + 1000), pi)
    }

    /** Shows every reminder that is due, then sets the next alarm. */
    fun fire(context: Context) {
        val db = db(context)
        val now = System.currentTimeMillis()
        for ((id, at, repeats) in db.reminders()) {
            if (at > now + 30_000) break
            // Below CHEQUE_BASE: cheques. Other negative ids are the user's own bills (Db.bills), not items.
            if (id <= -CHEQUE_BASE) { fireCheque(context, -id - CHEQUE_BASE, at); continue }
            if (id < 0) { fireBill(context, -id, at); continue }
            val item = db.get(id)
            if (item == null || (repeats > 0 && item.isRead)) { db.removeReminder(id); continue }
            show(context, item)
            if (repeats > 0) db.putReminder(id, now + REPEAT_GAP, repeats - 1) else db.removeReminder(id)
        }
        arm(context)
    }

    /**
     * Shows a hand-entered bill's reminder. It comes back every morning until the due day,
     * then the next month's one is set.
     */
    private fun fireBill(context: Context, billId: Long, at: Long) {
        val db = db(context)
        db.removeReminder(-billId)
        val b = db.myBill(billId) ?: return
        val due = com.rez.notif.classify.Bills.nextMonthly(b.day, 0, at - 60_000L)
        val daysLeft = Math.floorDiv(due - at - 3_600_000L + 86_399_999L, 86_400_000L).toInt().coerceAtLeast(0)
        // Ticked as paid for this due month: no more reminders, just move on to the due day / next month.
        val dc = java.util.Calendar.getInstance().apply { timeInMillis = due }
        val (jy, jm, _) = com.rez.notif.classify.Bills.gregorianToJalali(dc.get(java.util.Calendar.YEAR), dc.get(java.util.Calendar.MONTH) + 1, dc.get(java.util.Calendar.DAY_OF_MONTH))
        if (NotifApp.repo(context).isPaid(billId, jy * 100 + jm)) {
            if (daysLeft > 0) { db.putReminder(-billId, due, 0); arm(context); return }
            if (b.remaining == 1) { db.deleteMyBill(billId); return }
            if (b.remaining > 1) db.saveMyBill(b.copy(remaining = b.remaining - 1))
            scheduleBill(context, b)
            return
        }
        val whenText = if (daysLeft <= 0) t("امروز سررسیده") else com.rez.notif.ui.components.fa(daysLeft) + t(" روز دیگه سررسیده")
        post(
            context, notifId(-billId), t("قسط و قبض: ") + b.title,
            (if (b.amount > 0) com.rez.notif.ui.components.toman(b.amount * 10) + "  ·  " else "") + whenText +
                "  ·  " + com.rez.notif.ui.components.fullTime(due),
            null,
        )
        if (daysLeft > 0) {
            db.putReminder(-billId, minOf(at + 86_400_000L, due), 0)
            arm(context)
            return
        }
        // Count down installments; the last one removes the bill.
        if (b.remaining == 1) { db.deleteMyBill(billId); return }
        if (b.remaining > 1) db.saveMyBill(b.copy(remaining = b.remaining - 1))
        scheduleBill(context, b)
    }

    /** Sets the reminder for the next due date of a hand-entered bill. */
    fun scheduleBill(context: Context, b: Db.MyBill) {
        val days = NotifApp.repo(context).prefs.current.billDaysBefore
        val now = System.currentTimeMillis()
        // This month's due date may already be inside the "days before" window: start reminding now.
        val due = com.rez.notif.classify.Bills.nextMonthly(b.day, 0, now)
        val at = due - days * 86_400_000L
        db(context).putReminder(-b.id, if (at > now) at else now + 60_000L, 0)
        arm(context)
    }

    /** Reminder ids for cheques: -(CHEQUE_BASE + cheque id), far from bill ids. */
    private const val CHEQUE_BASE = 1_000_000_000L

    /** A pending cheque: reminders every morning from "days before" until its due day. */
    fun scheduleCheque(context: Context, q: com.rez.notif.classify.Cheque) {
        val rid = -(CHEQUE_BASE + q.id)
        val now = System.currentTimeMillis()
        if (q.status != com.rez.notif.classify.Cheque.Status.PENDING || q.due < now - 86_400_000L) {
            db(context).removeReminder(rid); arm(context); return
        }
        val days = NotifApp.repo(context).prefs.current.billDaysBefore
        val at = q.due - days * 86_400_000L
        db(context).putReminder(rid, if (at > now) at else now + 60_000L, 0)
        arm(context)
    }

    fun cancelCheque(context: Context, chequeId: Long) {
        db(context).removeReminder(-(CHEQUE_BASE + chequeId))
        arm(context)
    }

    private fun fireCheque(context: Context, chequeId: Long, at: Long) {
        val db = db(context)
        db.removeReminder(-(CHEQUE_BASE + chequeId))
        val q = NotifApp.repo(context).cheque(chequeId) ?: return
        if (q.status != com.rez.notif.classify.Cheque.Status.PENDING) return
        val daysLeft = Math.floorDiv(q.due - at + 86_399_999L, 86_400_000L).toInt().coerceAtLeast(0)
        val whenText = if (daysLeft <= 0) t("امروز سررسیده") else com.rez.notif.ui.components.fa(daysLeft) + t(" روز دیگه سررسیده")
        post(
            context, 65_000 + (chequeId % 10_000).toInt(),
            (if (q.incoming) t("چک دریافتی: ") else t("چک پرداختی: ")) + q.person,
            com.rez.notif.ui.components.toman(q.toman * 10) + "  ·  " + whenText,
            null,
        )
        if (daysLeft > 0) {
            db.putReminder(-(CHEQUE_BASE + chequeId), minOf(at + 86_400_000L, q.due), 0)
            arm(context)
        }
    }

    fun cancelBill(context: Context, billId: Long) {
        db(context).removeReminder(-billId)
        arm(context)
    }

    private fun post(context: Context, id: Int, title: String, text: String, itemId: Long?) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("یادآوری‌ها"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = t("پیام‌هایی که گفتی بعداً یادت بندازم")
            },
        )
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (itemId != null) intent.putExtra(EXTRA_ITEM, itemId)
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(
            id,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun show(context: Context, item: com.rez.notif.data.Item) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("یادآوری‌ها"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = t("پیام‌هایی که گفتی بعداً یادت بندازم")
            },
        )
        ContactNames.ensure(context)
        val who = if (item.source == com.rez.notif.data.Source.SMS) ContactNames.label(item.sender) else item.appName
        val open = PendingIntent.getActivity(
            context,
            notifId(item.id),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ITEM, item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notif)
            .setContentTitle(t("یادآوری: ") + who)
            .setContentText(item.body.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText((if (item.title.isNotBlank() && item.title != item.sender) item.title + "\n" else "") + item.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(item.timestamp)
            .setShowWhen(true)
            .build()
        nm.notify(notifId(item.id), n)
    }

    private fun notifId(itemId: Long) = if (itemId < 0) 60_000 + (-itemId % 10_000).toInt() else 50_000 + (itemId % 1_000_000).toInt()

    fun alarmIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Handy times for the "remind me" menu. */
    fun choices(now: Long = System.currentTimeMillis()): List<Pair<String, Long>> {
        fun at(daysAhead: Int, hour: Int) = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val l = mutableListOf(t("۱ ساعت دیگه") to now + 3_600_000L, t("۳ ساعت دیگه") to now + 3 * 3_600_000L)
        val tonight = at(0, 20)
        if (tonight - now > 3_600_000L) l += t("امشب ساعت ۸") to tonight
        l += t("فردا ساعت ۹ صبح") to at(1, 9)
        return l
    }
}

/** Summary times ("digest"): the quiet summary appears only at the hours the user picked. */
object Digest {
    fun arm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = Reminders.alarmIntent(context, AlarmReceiver.ACTION_DIGEST)
        val hours = NotifApp.repo(context).prefs.current.digestHours
        if (hours.isEmpty()) { am.cancel(pi); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(hours, System.currentTimeMillis()), pi)
    }

    fun next(hours: Set<Int>, now: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        repeat(48) {
            c.add(Calendar.HOUR_OF_DAY, 1)
            if (c.get(Calendar.HOUR_OF_DAY) in hours) return c.timeInMillis
        }
        return now + 86_400_000L
    }
}

/** Wakes up for reminders and digest times, and sets the alarms again after a reboot. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ACTION_REMIND -> Reminders.fire(context)
                    ACTION_DIGEST -> { SummaryNotifier.update(context, digest = true); Digest.arm(context) }
                    else -> { Reminders.arm(context); Digest.arm(context) }
                }
            } catch (_: Exception) {
            } finally {
                done.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_REMIND = "com.rez.notif.REMIND"
        const val ACTION_DIGEST = "com.rez.notif.DIGEST"
    }
}
