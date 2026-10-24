package com.rez.notif.notif

import com.rez.notif.i18n.t
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.rez.notif.R
import com.rez.notif.ui.components.fa

/**
 * Login codes and bank one-time passwords: the original notification is hidden and replaced by ours,
 * which shows the code big with a "copy" button and disappears by itself after 10 minutes.
 */
object CodeNotifier {
    private const val CHANNEL = "codes"
    private const val CHANNEL_QUIET = "codes_quiet"
    private const val EXTRA_CODE = "code"
    const val ACTION_COPY = "com.rez.notif.COPY_CODE"

    fun show(context: Context, code: String, from: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val s = com.rez.notif.NotifApp.repo(context).prefs.current
        // With "appear on top", our own box stays up for the chosen seconds; the notification then waits quietly in the shade.
        val boxed = s.codeOverlay && CodeOverlay.show(context, code, from, s.codeSeconds)
        if (boxed) nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, t("کدهای تأیید (بی‌صدا)"), NotificationManager.IMPORTANCE_LOW).apply {
                description = t("وقتی باکس کد روی صفحه نشون داده میشه")
            },
        )
        // Pops up on screen but stays silent: the original notification already made its sound.
        else nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("کدهای تأیید"), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                description = t("کد ورود و رمز پویا با دکمه کپی")
            },
        )
        val id = 1000 + (code.hashCode() and 0xFFFF)
        val copy = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, CopyCodeReceiver::class.java).setAction(ACTION_COPY).putExtra(EXTRA_CODE, code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, if (boxed) CHANNEL_QUIET else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notif)
            .setContentTitle(fa(code))
            .setContentText(from)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setTimeoutAfter(10 * 60_000L)
            .setAutoCancel(true)
            .setContentIntent(copy)
            .addAction(Notification.Action.Builder(null, t("کپی کد"), copy).build())
            .build()
        nm.notify(id, n)
    }

    /** Puts the code on the clipboard, marked sensitive so the phone does not show it in previews. */
    fun copy(context: Context, code: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("code", code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        cm.setPrimaryClip(clip)
        // Android 13+ shows its own "copied" message.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, t("کد کپی شد"), Toast.LENGTH_SHORT).show()
    }

    internal fun codeOf(intent: Intent): String? = intent.getStringExtra(EXTRA_CODE)
}

class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CodeNotifier.ACTION_COPY) return
        CodeNotifier.codeOf(intent)?.let { CodeNotifier.copy(context, it) }
    }
}
