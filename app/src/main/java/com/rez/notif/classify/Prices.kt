package com.rez.notif.classify

/**
 * Gold and dollar prices for the widget, read from tgju.org's public price file (call1.tgju.org/ajax.json).
 * This is the only thing the app ever fetches from the internet, and nothing about the user is sent.
 * Parsing is pure Kotlin (no JSON library) so it can be tested.
 */
object Prices {
    enum class Item(val key: String, val label: String) {
        DOLLAR("price_dollar_rl", "دلار"),
        GOLD18("geram18", "طلا ۱۸"),
        COIN("sekee", "سکه امامی"),
    }

    /** [rial]: price in rial; [changePct]: change since yesterday in percent (negative = down). */
    data class Quote(val item: Item, val rial: Long, val changePct: Double)

    /** Reads the quotes we show from the price file; missing or broken ones are left out. */
    fun parse(json: String): List<Quote> = Item.entries.mapNotNull { item ->
        val start = json.indexOf("\"" + item.key + "\":{")
        if (start < 0) return@mapNotNull null
        val end = json.indexOf('}', start)
        if (end < 0) return@mapNotNull null
        val obj = json.substring(start, end)
        val p = Regex("\"p\":\"([0-9,.]+)\"").find(obj)?.groupValues?.get(1)?.replace(",", "")?.substringBefore('.')?.toLongOrNull()
            ?: return@mapNotNull null
        val dp = Regex("\"dp\":\"?(-?[0-9.]+)\"?").find(obj)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val down = Regex("\"dt\":\"(\\w*)\"").find(obj)?.groupValues?.get(1) == "low"
        Quote(item, p, if (down) -kotlin.math.abs(dp) else kotlin.math.abs(dp))
    }

    /** One quote stored as text: "rial|changePct". */
    fun encode(q: Quote) = "${q.rial}|${q.changePct}"

    fun decode(item: Item, s: String): Quote? {
        val parts = s.split('|')
        val rial = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return Quote(item, rial, parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
    }

    /** "۲۹۲٫۹ هزار" / "۲۵٫۸ م" style short toman, with Latin digits (the UI makes them Persian). */
    fun shortToman(rial: Long): Pair<String, Boolean> {
        val toman = rial / 10
        return if (toman >= 1_000_000) String.format(java.util.Locale.US, "%.1f", toman / 1e6).removeSuffix(".0") to true
        else String.format(java.util.Locale.US, "%.1f", toman / 1e3).removeSuffix(".0") to false
    }
}
