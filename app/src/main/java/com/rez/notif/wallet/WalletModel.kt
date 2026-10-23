package com.rez.notif.wallet

import java.security.SecureRandom

/** Kinds of things kept in the offline password wallet ("کیف پسورد"). Labels per kind for the two main fields. */
enum class WalletKind(val label: String, val userLabel: String, val secretLabel: String) {
    LOGIN("ورود سایت یا اپ", "نام کاربری یا ایمیل", "رمز"),
    BANK("کارت بانکی", "شماره کارت", "رمز / CVV2"),
    WIFI("وای‌فای", "اسم شبکه", "رمز وای‌فای"),
    NOTE("یادداشت امن", "عنوان کوتاه", "متن محرمانه"),
}

/** One saved secret. Only [title] and [kind] are stored readable (inside the encrypted DB); the rest is locked twice. */
data class WalletEntry(
    val id: Long = 0,
    val title: String,
    val kind: WalletKind,
    val username: String = "",
    val secret: String = "",
    val url: String = "",
    val note: String = "",
    val updated: Long = System.currentTimeMillis(),
)

/** Turns the secret fields into one string and back (length-prefixed, so any text is safe). Pure Kotlin, tested. */
object WalletCodec {
    fun encode(e: WalletEntry): String =
        listOf(e.username, e.secret, e.url, e.note).joinToString("") { "${it.length}:$it" }

    /** Fills the secret fields of [base] from [data]. */
    fun decode(base: WalletEntry, data: String): WalletEntry {
        val parts = ArrayList<String>(4)
        var i = 0
        while (i < data.length && parts.size < 4) {
            val colon = data.indexOf(':', i)
            require(colon > i) { "bad wallet data" }
            val len = data.substring(i, colon).toInt()
            parts += data.substring(colon + 1, colon + 1 + len)
            i = colon + 1 + len
        }
        while (parts.size < 4) parts += ""
        return base.copy(username = parts[0], secret = parts[1], url = parts[2], note = parts[3])
    }
}

/** Strong random passwords. */
object PasswordGen {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%&*-_=+?"

    fun generate(length: Int = 16, digits: Boolean = true, symbols: Boolean = true, rnd: SecureRandom = SecureRandom()): String {
        val sets = buildList {
            add(LOWER); add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        val n = length.coerceIn(sets.size, 64)
        val all = sets.joinToString("")
        // One from each chosen group, the rest from everything, then shuffled.
        val chars = sets.map { it[rnd.nextInt(it.length)] }.toMutableList()
        while (chars.size < n) chars += all[rnd.nextInt(all.length)]
        for (i in chars.indices.reversed()) {
            val j = rnd.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return chars.joinToString("")
    }
}
