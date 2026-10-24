package com.rez.notif.classify

import java.util.Calendar

/** Finds a due date (and amount) in bill / installment SMS, e.g. "مهلت پرداخت قسط: ۱۴۰۵/۰۸/۱۰". */
object Bills {
    data class Bill(val due: Long, val amount: Long?)

    private val KEYWORDS = listOf("مهلت پرداخت", "سررسید", "قسط", "اقساط", "بدهی", "قبض", "آخرین مهلت", "معوق", "تا تاریخ", "پرداخت نشده")
    private val PAID = listOf("پرداخت شد", "با موفقیت", "تسویه شد", "دریافت شد")
    /** Offers and packages that mention dates but are not bills. */
    private val NOT_BILL = listOf("جایزه", "هدیه", "تخفیف", "بسته", "اینترنت", "گیگ", "قرعه", "برنده", "اعتبار بیشتر", "لغو11", "شارژ رایگان", "جشنواره", "فعالسازی")
    private val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    private val YMD = Regex("(?<![0-9])(1[34][0-9]{2}|[0-9]{2})[/\\-.]([0-9]{1,2})[/\\-.]([0-9]{1,2})(?![0-9])")
    private val DMY = Regex("(?<![0-9])([0-9]{1,2})[/\\-.]([0-9]{1,2})[/\\-.](1[34][0-9]{2})(?![0-9])")
    private val NAMED = Regex("(?<![0-9])([0-9]{1,2})\\s*(" + MONTHS.joinToString("|") + ")(?![\\u0600-\\u06FF])\\s*(?:ماه)?\\s*(1[34][0-9]{2})?")

    fun isBill(text: String): Boolean {
        val t = Classifier.normalize(text)
        return KEYWORDS.any { t.contains(Classifier.normalize(it)) } &&
            PAID.none { t.contains(Classifier.normalize(it)) } &&
            NOT_BILL.none { t.contains(Classifier.normalize(it)) }
    }

    /** The due date (9 am that day) if this looks like an unpaid bill or installment; null otherwise. */
    fun parse(text: String, now: Long = System.currentTimeMillis()): Bill? {
        if (!isBill(text)) return null
        val t = Classifier.normalize(text)
        val due = YMD.find(t)?.let { m ->
            val y = m.groupValues[1].toInt().let { if (it < 100) 1400 + it else it }
            toMillis(y, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } ?: DMY.find(t)?.let { m ->
            toMillis(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        } ?: NAMED.find(t)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = MONTHS.indexOf(m.groupValues[2]) + 1
            val y = m.groupValues[3].toIntOrNull()
            if (y != null) toMillis(y, mo, d) else guessYear(mo, d, now)
        } ?: return null
        val amount = (Money.parse(text)?.let { maxOf(it.out, it.inn).takeIf { a -> a > 0 } } ?: amountAfterMablagh(t))
            ?.takeIf { it <= 100_000_000_000L }
        return Bill(due, amount)
    }

    private fun amountAfterMablagh(t: String): Long? =
        Regex("مبلغ\\s*[:：]?\\s*([0-9][0-9,٬]{3,})").find(t)?.groupValues?.get(1)?.replace(",", "")?.replace("٬", "")?.toLongOrNull()

    /** With no year: the nearest such date from a month ago to eleven months ahead. */
    private fun guessYear(m: Int, d: Int, now: Long): Long? {
        val gy = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        return listOf(gy - 622, gy - 621, gy - 620).mapNotNull { toMillis(it, m, d) }
            .firstOrNull { it > now - 30 * 86_400_000L && it < now + 330 * 86_400_000L }
    }

    fun toMillis(jy: Int, jm: Int, jd: Int): Long? {
        if (jm !in 1..12 || jd !in 1..31 || jy !in 1300..1500) return null
        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
        return Calendar.getInstance().apply {
            clear()
            set(gy, gm - 1, gd, 9, 0, 0)
        }.timeInMillis
    }

    /** The well-known jalali → gregorian conversion (as in jdf / jalaali). */
    fun jalaliToGregorian(jyIn: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy = jyIn + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + jd + if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val months = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && gd > months[gm]) { gd -= months[gm]; gm++ }
        return Triple(gy, gm + 1, gd)
    }

    /** gregorian → jalali (year, month, day). */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return Triple(jy, jm, jd)
    }

    /**
     * Next due date (9 am) of a monthly payment on Persian day [day], whose reminder ([daysBefore] days earlier)
     * is still ahead of [now]. Day 31 in a 30-day month falls on its last day.
     */
    fun nextMonthly(day: Int, daysBefore: Int, now: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        var (jy, jm, _) = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        repeat(14) {
            val last = if (jm <= 6) 31 else if (jm <= 11) 30 else 29
            val due = toMillis(jy, jm, day.coerceIn(1, last))!!
            if (due - daysBefore * 86_400_000L > now) return due
            jm++
            if (jm > 12) { jm = 1; jy++ }
        }
        return now + 30 * 86_400_000L
    }
}
