package com.rez.notif.classify

/**
 * How important an item is right now (higher = more important). Pure Kotlin, unit-tested.
 * Chosen people and missed calls first, then personal messages and money; ads never.
 */
object Priority {
    data class Input(
        val category: Category,
        val isVip: Boolean,
        val isValuable: Boolean,
        val isStarred: Boolean,
        val isRead: Boolean,
        val ageMillis: Long,
        /** Someone asked something ("?" or "؟" in a personal message or chat). */
        val isQuestion: Boolean = false,
    )

    /** A message that is waiting for an answer. */
    fun isQuestion(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if ('?' in t || '؟' in t) return true
        return t.startsWith("آیا") || t.startsWith("کی ") || t.startsWith("کجایی")
    }

    /** Items scoring at least this show in "Important". */
    const val IMPORTANT = 50

    fun score(i: Input): Int {
        if (i.category == Category.ADS) return 0
        var s = when (i.category) {
            Category.MISSED -> 60
            Category.PERSONAL -> 50
            Category.BANK -> 40
            Category.OTP -> if (i.ageMillis < 10 * 60_000L) 55 else 0
            Category.SOCIAL -> 30
            Category.EMAIL -> 20
            Category.SHOPPING, Category.SERVICES -> 15
            else -> 5
        }
        if (i.isQuestion && (i.category == Category.PERSONAL || i.category == Category.SOCIAL || i.category == Category.EMAIL)) s += 25
        if (i.isVip) s += 60
        if (i.isValuable || i.isStarred) s += 20
        if (!i.isRead) s += 15
        // Older things matter less: -5 for every 6 hours, at most -40.
        s -= (i.ageMillis / (6 * 3_600_000L)).toInt().coerceAtMost(8) * 5
        return s.coerceAtLeast(0)
    }
}
