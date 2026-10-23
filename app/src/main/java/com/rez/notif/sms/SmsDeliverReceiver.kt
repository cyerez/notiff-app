package com.rez.notif.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.rez.notif.NotifApp
import com.rez.notif.notif.CodeNotifier
import com.rez.notif.notif.SummaryNotifier

/**
 * Phase 5: only the default SMS app gets SMS_DELIVER. It must save the message in the phone's inbox
 * itself (nobody else will), then Notif sorts it and decides how loud to be.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull() ?: return
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val now = System.currentTimeMillis()
        val messages = parts.groupBy { it.originatingAddress ?: "" }.map { (address, list) ->
            RawSms(-1, address, list.joinToString("") { it.messageBody ?: "" }, now, subId) to (list.first().timestampMillis)
        }

        val pending = goAsync()
        Thread {
            try {
                val repo = NotifApp.repo(context)
                messages.forEach { (raw, sentAt) ->
                    val d = runCatching { repo.receiveSms(raw) }.getOrNull()
                    // Never lose a message: if sorting failed, it still goes to the phone's inbox.
                    if (d?.block != true) DefaultSms.writeInbox(context, raw, sentAt, read = d?.isAd == true)
                    when {
                        d == null -> SmsNotifier.show(context, raw.address, raw.body, stay = true)
                        d.code != null -> CodeNotifier.show(context, d.code, raw.address)
                        d.notify -> SmsNotifier.show(context, raw.address, raw.body, stay = true)
                        !d.isAd -> SmsNotifier.show(context, raw.address, raw.body, stay = false)
                    }
                }
                runCatching { SummaryNotifier.update(context) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
