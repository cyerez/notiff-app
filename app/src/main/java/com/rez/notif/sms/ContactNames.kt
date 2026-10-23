package com.rez.notif.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.rez.notif.classify.VipMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Names from the phone's contacts, so "+98912…" shows as "Ali". Matched by the last 10 digits,
 * so "+98912…", "0912…" and "912…" are the same person. Read on device only.
 */
object ContactNames {
    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Number key (last 10 digits) → contact name. Changes when contacts are reloaded. */
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()
    @Volatile private var loadedAt = 0L

    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Reloads all contacts (a few ms for a normal phone book). Call off the main thread. */
    fun refresh(context: Context) {
        if (!allowed(context)) return
        val map = HashMap<String, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = VipMatcher.key(c.getString(0) ?: continue) ?: continue
                    val name = c.getString(1)?.trim().orEmpty()
                    if (name.isNotEmpty()) map.putIfAbsent(key, name)
                }
            }
        }
        _names.value = map
        loadedAt = System.currentTimeMillis()
    }

    /** Loads once if nothing is loaded yet (for receivers that run without the app open). */
    fun ensure(context: Context) {
        if (loadedAt == 0L || System.currentTimeMillis() - loadedAt > 10 * 60_000L) refresh(context)
    }

    /** The contact's name for a sender, or null when it is not a saved number. */
    fun nameOf(sender: String): String? = VipMatcher.key(sender)?.let { _names.value[it] }

    /** Name if known, otherwise the sender as it came. */
    fun label(sender: String): String = nameOf(sender) ?: sender

    /** Name and every number of one picked contact (from the contact picker's result). */
    fun contactOf(context: Context, contactUri: Uri): Pair<String, List<String>>? {
        val cr = context.contentResolver
        val (id, name) = cr.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) to (c.getString(1) ?: "") else null } ?: return null
        val numbers = ArrayList<String>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id.toString()), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { numbers += it } }
        return name to numbers
    }
}
