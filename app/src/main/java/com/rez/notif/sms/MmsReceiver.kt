package com.rez.notif.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rez.notif.NotifApp
import com.rez.notif.i18n.t

/**
 * Phase 5: the default SMS app must accept MMS pushes. Notif does not download MMS (picture/voice messages);
 * it records that one arrived and from whom, so nothing goes by unseen.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data") ?: return
        // 0x82 = m-notification-ind ("a new MMS is waiting"); other pushes (delivery reports) are ignored.
        if (!MmsPdu.isNotification(pdu)) return
        val from = MmsPdu.from(pdu) ?: "MMS"
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val raw = RawSms(-1, from, t("یک پیام MMS (عکس، صدا یا پیام گروهی) رسید. شاخ گاو MMS رو باز نمی‌کنه."), System.currentTimeMillis(), subId)
        val pending = goAsync()
        Thread {
            try {
                NotifApp.repo(context).addSms(raw)
                SmsNotifier.show(context, raw.address, raw.body, stay = true)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
