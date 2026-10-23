package com.rez.notif.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.rez.notif.NotifApp

/**
 * Gets every new SMS the moment it arrives (we are not the default SMS app, so we only read it;
 * the phone's own SMS app still receives it too).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val now = System.currentTimeMillis()

        // Long messages arrive in parts; join them per sender.
        val messages = parts.filterNotNull().groupBy { it.originatingAddress ?: "" }
            .map { (address, list) -> RawSms(-1, address, list.joinToString("") { it.messageBody ?: "" }, now, subId) }

        val pending = goAsync()
        Thread {
            try {
                val repo = NotifApp.repo(context)
                messages.forEach { repo.addSms(it) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
