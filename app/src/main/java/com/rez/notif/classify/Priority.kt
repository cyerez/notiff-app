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
    )

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
        if (i.isVip) s += 60
        if (i.isValuable || i.isStarred) s += 20
        if (!i.isRead) s += 15
        // Older things matter less: -5 for every 6 hours, at most -40.
        s -= (i.ageMillis / (6 * 3_600_000L)).toInt().coerceAtMost(8) * 5
        return s.coerceAtLeast(0)
    }
}
