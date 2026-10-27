package com.rez.notif.sms

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Telephony
import android.telephony.SubscriptionManager

/** One raw SMS from the phone's inbox. */
data class RawSms(val id: Long, val address: String, val body: String, val date: Long, val subId: Int)

/** Reads the SMS inbox (all old messages) through the system content provider. */
object SmsReader {

    fun readInbox(
        context: Context,
        sinceMillis: Long = 0L,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<RawSms> {
        val out = ArrayList<RawSms>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.SUBSCRIPTION_ID,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            if (sinceMillis > 0) "${Telephony.Sms.DATE} > ?" else null,
            if (sinceMillis > 0) arrayOf(sinceMillis.toString()) else null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val total = c.count
            var done = 0
            val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val iSub = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            while (c.moveToNext()) {
                out += RawSms(
                    id = c.getLong(iId),
                    address = c.getString(iAddr) ?: "",
                    body = c.getString(iBody) ?: "",
                    date = c.getLong(iDate),
                    subId = if (iSub >= 0) c.getInt(iSub) else -1,
                )
                done++
                if (done % 200 == 0) onProgress(done, total)
            }
            onProgress(total, total)
        }
        return out
    }

    /** Bodies of inbox SMS since [sinceMillis] that are already marked read in the phone (empty without permission). */
    fun readBodies(context: Context, sinceMillis: Long): Set<String> = runCatching {
        val out = HashSet<String>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY),
            "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.READ} = 1",
            arrayOf(sinceMillis.toString()),
            null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out += it.trim() } }
        out as Set<String>
    }.getOrDefault(emptySet())

    private val slotCache = HashMap<Int, Int>()

    /** SIM slot (1 or 2) for a subscription id, or 0 when unknown / permission missing. */
    @SuppressLint("MissingPermission")
    fun slotOf(context: Context, subId: Int): Int {
        if (subId < 0) return 0
        slotCache[subId]?.let { return it }
        val slot = try {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            (sm?.getActiveSubscriptionInfo(subId)?.simSlotIndex ?: -1) + 1
        } catch (e: SecurityException) {
            0
        }
        slotCache[subId] = slot
        return slot
    }
}
