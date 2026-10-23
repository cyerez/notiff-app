package com.rez.notif.notif

import com.rez.notif.i18n.t
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rez.notif.MainActivity
import com.rez.notif.NotifApp
import com.rez.notif.R
import com.rez.notif.classify.Category
import com.rez.notif.ui.components.fa

/**
 * When every notification is hidden, this one quiet notification says what arrived
 * ("۵ مورد تازه: بانک ۲، تلگرام ۳"). Tapping it opens the app. It goes away when the app opens.
 */
object SummaryNotifier {
    private const val CHANNEL = "summary"
    private const val ID = 1

    fun update(context: Context) {
        val repo = NotifApp.repo(context)
        val s = repo.prefs.current
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!s.hideAll || !s.summary) { nm.cancel(ID); return }
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        com.rez.notif.sms.ContactNames.ensure(context)
        val items = repo.newSince(s.lastSeen)
        if (items.isEmpty()) { nm.cancel(ID); return }

        // Low importance: the original notification already made its sound, so this one stays silent.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("خلاصه نوتیف‌ها"), NotificationManager.IMPORTANCE_LOW).apply {
                description = t("یک نوتیف آروم به جای همه نوتیف‌های مخفی‌شده")
            },
        )
        val byCategory = items.groupBy { it.category }.entries.sortedByDescending { it.value.size }
        val summary = byCategory.joinToString(t("، ")) { t(Category.of(it.key).label) + " " + fa(it.value.size) }
        val style = Notification.InboxStyle().setSummaryText(summary)
        items.take(6).forEach { i ->
            val who = if (i.sender.isBlank()) i.appName else com.rez.notif.sms.ContactNames.label(i.sender)
            style.addLine("$who: " + i.body.replace('\n', ' ').take(70))
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notif)
            .setContentTitle(fa(items.size) + t(" مورد تازه"))
            .setContentText(summary)
            .setStyle(style)
            .setNumber(items.size)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(items.first().timestamp)
            .build()
        nm.notify(ID, n)
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}
