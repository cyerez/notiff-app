package com.rez.notif.notif

import android.Manifest
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
import com.rez.notif.classify.Ledger
import com.rez.notif.i18n.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "What was this 85,000 toman?" after a bank spending the ledger cannot sort. Two buttons with the most used
 * categories and "to myself"; tapping the notification opens the ledger to pick any other category.
 */
object LedgerAsk {
    private const val CHANNEL = "ledger_ask"
    const val ACTION = "com.rez.notif.LEDGER_CATEGORY"
    const val EXTRA_CATEGORY = "category"
    const val EXTRA_OPEN_LEDGER = "open_ledger"

    fun notifId(itemId: Long) = 80_000 + (itemId % 100_000).toInt()

    fun show(context: Context, itemId: Long, toman: Long, top: List<String>) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t("دسته خرج‌ها"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("بعد از هر خرج ناشناخته یه بار می‌پرسه چی بود")
                setSound(null, null)
            },
        )
        val id = notifId(itemId)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_OPEN_LEDGER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notif)
            .setContentTitle(t("این ") + com.rez.notif.ui.components.toman(toman * 10) + t(" چی بود؟"))
            .setContentText(t("یه دسته بزن؛ دفعه بعد از همین کارت یا فروشگاه خودم می‌فهمم."))
            .setContentIntent(open)
            .setAutoCancel(true)
        (top.take(2).map { it to t(it) } + (Ledger.OWN to t("به خودم"))).forEachIndexed { i, (cat, label) ->
            val pi = PendingIntent.getBroadcast(
                context, id * 4 + i,
                Intent(context, LedgerAskReceiver::class.java).setAction(ACTION).putExtra(Reminders.EXTRA_ITEM, itemId).putExtra(EXTRA_CATEGORY, cat),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(null, label, pi).build())
        }
        nm.notify(id, b.build())
    }
}

class LedgerAskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LedgerAsk.ACTION) return
        val itemId = intent.getLongExtra(Reminders.EXTRA_ITEM, 0L).takeIf { it > 0 } ?: return
        val category = intent.getStringExtra(LedgerAsk.EXTRA_CATEGORY) ?: return
        context.getSystemService(NotificationManager::class.java)?.cancel(LedgerAsk.notifId(itemId))
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { NotifApp.repo(context).categorizeItem(itemId, category) } finally { pending.finish() }
        }
    }
}
