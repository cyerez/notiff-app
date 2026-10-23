package com.rez.notif.sms

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager

/**
 * Phase 5: "reply with a message" from the phone app's incoming-call screen. Required for a default SMS app.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val to = recipients(intent.data)
            if (text.isNotBlank()) to.forEach { DefaultSms.send(this, it, text, 0) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        /** "smsto:0912…;0935…" → the numbers. */
        fun recipients(uri: Uri?): List<String> {
            val part = uri?.schemeSpecificPart ?: return emptyList()
            return part.substringBefore('?').split(',', ';').map { Uri.decode(it).trim() }.filter { it.isNotEmpty() }
        }
    }
}
