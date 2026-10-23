package com.rez.notif.sms

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

/** Phase 5: Notif is the SMS app now, so it posts the "new SMS" notification itself. */
object SmsNotifier {
    private const val CHANNEL = "sms"
    const val EXTRA_ADDRESS = "open_sms_address"

    /**
     * [stay] false: the notification rings and then leaves the status bar after a few seconds, the same
     * way hidden notifications behaved before (they live on inside the app and in the summary).
     */
    fun show(context: Context, address: String, body: String, stay: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("پیامک‌های تازه"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = t("وقتی نوتیف اپ پیامک گوشیه")
            },
        )
        val id = 5000 + (address.hashCode() and 0xfff)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notif)
            .setContentTitle(ContactNames.ensure(context).let { ContactNames.label(address) })
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setShowWhen(true)
        if (!stay) b.setTimeoutAfter(4_000)
        nm.notify(id, b.build())
    }

    fun clear(context: Context, address: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(5000 + (address.hashCode() and 0xfff))
    }
}
