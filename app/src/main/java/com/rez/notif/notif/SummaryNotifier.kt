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
 * (an icon with a count for each app). Tapping it opens the app. It goes away when the app opens.
 */
object SummaryNotifier {
    private const val CHANNEL = "summary"
    private const val DIGEST_CHANNEL = "digest"
    private const val ID = 1

    /**
     * Live mode: called after every hidden notification. Digest mode (the user picked summary hours):
     * live calls do nothing, and the alarm calls this with [digest] at each chosen hour.
     */
    fun update(context: Context, digest: Boolean = false) {
        val repo = NotifApp.repo(context)
        val s = repo.prefs.current
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!s.hideAll || !s.summary) { nm.cancel(ID); return }
        if (s.digestHours.isNotEmpty() && !digest) return
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
        if (digest) {
            nm.createNotificationChannel(
                NotificationChannel(DIGEST_CHANNEL, t("خلاصه در ساعت‌های مشخص"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = t("خلاصه نوتیف‌ها فقط در ساعت‌هایی که انتخاب کردی")
                },
            )
        }
        val byCategory = items.groupBy { it.category }.entries.sortedByDescending { it.value.size }
        val summary = byCategory.joinToString(t("، ")) { t(Category.of(it.key).label) + " " + fa(it.value.size) }
        // One icon per app with its count, instead of lines of text.
        val byApp = items.groupBy { appOf(context, it) }.entries.sortedByDescending { it.value.size }.map { it.key to it.value.size }
        val small = iconsView(context, byApp, null, rows = 1)
        val big = iconsView(context, byApp, fa(items.size) + t(" مورد تازه"), rows = 2)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, if (digest) DIGEST_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notif)
            .setContentTitle(fa(items.size) + t(" مورد تازه"))
            .setContentText(summary)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setNumber(items.size)
            .setContentIntent(open)
            .setOnlyAlertOnce(!digest)
            .setShowWhen(true)
            .setWhen(items.first().timestamp)
            .build()
        nm.notify(ID, n)
    }

    private const val PER_ROW = 7

    private fun iconsView(context: Context, apps: List<Pair<String, Int>>, title: String?, rows: Int): android.widget.RemoteViews {
        val v = android.widget.RemoteViews(context.packageName, R.layout.summary_notif)
        if (title == null) v.setViewVisibility(R.id.summary_title, android.view.View.GONE) else v.setTextViewText(R.id.summary_title, title)
        v.removeAllViews(R.id.summary_icons)
        v.removeAllViews(R.id.summary_icons2)
        apps.take(PER_ROW * rows).forEachIndexed { i, (pkg, count) ->
            val cell = android.widget.RemoteViews(context.packageName, R.layout.summary_icon)
            val icon = iconOf(context, pkg)
            if (icon != null) cell.setImageViewBitmap(R.id.summary_app, icon) else cell.setImageViewResource(R.id.summary_app, R.mipmap.ic_launcher)
            cell.setTextViewText(R.id.summary_count, fa(count))
            // Tapping an app's icon opens that app itself, so the whole conversation is in front of you.
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = android.app.PendingIntent.getActivity(context, 7000 + (pkg.hashCode() and 0xfff), launch, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                cell.setOnClickPendingIntent(R.id.summary_app, pi)
                cell.setOnClickPendingIntent(R.id.summary_count, pi)
            }
            v.addView(if (i < PER_ROW) R.id.summary_icons else R.id.summary_icons2, cell)
        }
        return v
    }

    /** SMS are shown with the phone's messages app icon. */
    private fun appOf(context: Context, i: com.rez.notif.data.Item): String =
        if (i.source == com.rez.notif.data.Source.SMS || i.packageName.isBlank()) {
            android.provider.Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { it != context.packageName } ?: "com.samsung.android.messaging"
        } else i.packageName

    private val icons = HashMap<String, android.graphics.Bitmap?>()

    private fun iconOf(context: Context, pkg: String): android.graphics.Bitmap? = icons.getOrPut(pkg) {
        runCatching {
            val d = context.packageManager.getApplicationIcon(pkg)
            val size = 96
            val b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(android.graphics.Canvas(b))
            b
        }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}
