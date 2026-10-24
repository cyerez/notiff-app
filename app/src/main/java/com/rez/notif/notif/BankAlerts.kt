package com.rez.notif.notif

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rez.notif.MainActivity
import com.rez.notif.R
import com.rez.notif.i18n.t

/** "Balance is low" and "big spend" alerts from bank SMS. Tapping opens that SMS in Notif. */
object BankAlerts {
    private const val CHANNEL = "bank_alerts"

    fun show(context: Context, itemId: Long?, title: String, text: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("هشدار بانکی"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = t("مانده کم و خرج‌های بزرگ")
            },
        )
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (itemId != null) intent.putExtra(Reminders.EXTRA_ITEM, itemId)
        val id = 70_000 + ((itemId ?: 0L) % 100_000).toInt()
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
}
