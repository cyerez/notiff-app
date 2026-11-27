package com.rez.notif.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Names the user gave to SMS senders the app did not know ("9830009417" -> "ملت").
 * Kept in its own SharedPreferences file; Compose state, so cards update right after saving.
 */
object BankNames {
    private const val FILE = "bank_names"

    var map: Map<String, String> by mutableStateOf(emptyMap())
        private set

    fun load(context: Context) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        map = sp.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    }

    /** Blank [name] removes it. */
    fun set(context: Context, sender: String, name: String) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val n = name.trim()
        if (n.isEmpty()) sp.edit().remove(sender).apply() else sp.edit().putString(sender, n).apply()
        map = if (n.isEmpty()) map - sender else map + (sender to n)
    }
}
