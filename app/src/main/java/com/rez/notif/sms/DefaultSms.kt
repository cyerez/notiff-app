package com.rez.notif.sms

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/** One message of a conversation, read from the phone's SMS store. */
data class ThreadMsg(val id: Long, val incoming: Boolean, val body: String, val date: Long, val simSlot: Int, val failed: Boolean)

/**
 * Phase 5: Notif as the phone's default SMS app. Only the default app may write to (and delete from)
 * the phone's SMS store, and it must save every incoming and sent message there itself.
 */
object DefaultSms {

    fun isDefault(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /** The system dialog that asks "make Notif your SMS app?". */
    fun requestIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }

    /** Where the user picks another SMS app (to go back to Samsung Messages). */
    fun chooseOtherIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    /** Saves an incoming SMS in the phone's inbox. */
    fun writeInbox(context: Context, raw: RawSms, sentAt: Long, read: Boolean) {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, raw.address)
            put(Telephony.Sms.BODY, raw.body)
            put(Telephony.Sms.DATE, raw.date)
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (raw.subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, raw.subId)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v) }
    }

    /** Saves a message we sent in the phone's "sent" box (or "failed" if sending did not start). */
    private fun writeSent(context: Context, address: String, body: String, subId: Int, failed: Boolean) {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, if (failed) Telephony.Sms.MESSAGE_TYPE_FAILED else Telephony.Sms.MESSAGE_TYPE_SENT)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.CONTENT_URI, v) }
    }

    /**
     * Sends a text from SIM [simSlot] (1 or 2, 0 = phone default). When Notif is the default app it also
     * saves the message in the phone's sent box (the system only does that for other apps).
     */
    fun send(context: Context, address: String, text: String, simSlot: Int): Boolean {
        val subId = subIdForSlot(context, simSlot)
        val ok = try {
            val base = checkNotNull(context.getSystemService(SmsManager::class.java)) { "no SMS on this device" }
            val sm = if (subId >= 0) base.createForSubscriptionId(subId) else base
            sm.sendMultipartTextMessage(address, null, sm.divideMessage(text), null, null)
            true
        } catch (e: Exception) {
            false
        }
        if (isDefault(context)) writeSent(context, address, text, subId, failed = !ok)
        return ok
    }

    @SuppressLint("MissingPermission")
    fun subIdForSlot(context: Context, simSlot: Int): Int {
        if (simSlot <= 0) return -1
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == simSlot - 1 }?.subscriptionId ?: -1
        } catch (e: SecurityException) {
            -1
        }
    }

    /** How many SIMs are active (to show the SIM picker only when there are two). */
    @SuppressLint("MissingPermission")
    fun simCount(context: Context): Int = try {
        context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoCount ?: 1
    } catch (e: SecurityException) {
        1
    }

    /** Deletes one incoming SMS from the phone. Only works while Notif is the default app. */
    fun deleteFromPhone(context: Context, address: String, body: String, date: Long): Int {
        if (!isDefault(context)) return 0
        return runCatching {
            context.contentResolver.delete(
                Telephony.Sms.Inbox.CONTENT_URI,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(address, body, (date - 120_000L).toString(), (date + 120_000L).toString()),
            )
        }.getOrDefault(0)
    }

    /** Marks every SMS from [address] as read on the phone (clears the unread badge). */
    fun markRead(context: Context, address: String) {
        if (!isDefault(context)) return
        val v = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI, v,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(address),
            )
        }
    }

    /** The whole conversation with [address] (received and sent), oldest first. */
    fun thread(context: Context, address: String, limit: Int = 500): List<ThreadMsg> {
        val out = ArrayList<ThreadMsg>()
        val tail = address.filter { it.isDigit() }.takeLast(10)
        // Match "+98912…" and "0912…" as the same person; names like "Hamrah-e Aval" match exactly.
        val (where, args) = if (tail.length >= 7) {
            "${Telephony.Sms.ADDRESS} LIKE ?" to arrayOf("%$tail")
        } else {
            "${Telephony.Sms.ADDRESS} = ?" to arrayOf(address)
        }
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.TYPE, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.SUBSCRIPTION_ID),
                where, args, "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(1)
                    if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                    out += ThreadMsg(
                        id = c.getLong(0),
                        incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        body = c.getString(2) ?: "",
                        date = c.getLong(3),
                        simSlot = SmsReader.slotOf(context, c.getInt(4)),
                        failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                    )
                }
            }
        }
        return out.reversed()
    }

    /**
     * Opens the phone's SMS app with the text already written, so the user only taps send.
     * Used when Android blocks our SEND_SMS permission (sideloaded apps hit "restricted settings").
     */
    fun openComposer(context: android.content.Context, address: String, text: String): Boolean = try {
        val i = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + android.net.Uri.encode(address)))
            .putExtra("sms_body", text)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}
