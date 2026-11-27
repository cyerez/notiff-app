package com.rez.notif.data

import android.content.Context
import com.rez.notif.classify.Prices
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gold and dollar prices: the ONLY internet use in the app. A plain GET of a public price file,
 * no cookies, no user data, no analytics. At most once every [MAX_AGE_MS]; the last prices stay
 * saved so the widget still shows something offline.
 */
object PriceStore {
    private const val FILE = "prices"
    private const val URL_PRICES = "https://call1.tgju.org/ajax.json"
    private const val MAX_AGE_MS = 3 * 3_600_000L

    data class Saved(val quotes: List<Prices.Quote>, val fetchedAt: Long)

    fun saved(context: Context): Saved {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val quotes = Prices.Item.entries.mapNotNull { item -> sp.getString(item.name, null)?.let { Prices.decode(item, it) } }
        return Saved(quotes, sp.getLong("at", 0))
    }

    /** Fetches new prices when the saved ones are old. Blocking: call it off the main thread. Returns true when it got new ones. */
    fun refreshIfOld(context: Context, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!force && now - sp.getLong("tried", 0) < MAX_AGE_MS) return false
        sp.edit().putLong("tried", now).apply()
        val text = runCatching { download() }.getOrNull() ?: return false
        val quotes = Prices.parse(text)
        if (quotes.isEmpty()) return false
        sp.edit().apply {
            quotes.forEach { putString(it.item.name, Prices.encode(it)) }
            putLong("at", now)
        }.apply()
        return true
    }

    private fun download(): String {
        val c = URL(URL_PRICES).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.useCaches = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
