package com.rez.notif.classify

import java.time.LocalDate

/**
 * The Persian (Jalali) calendar for the calendar screen and widget.
 * Days are "epoch days" (days since 1970-01-01, local date), so no time zone surprises. Pure Kotlin, tested.
 */
object Jalali {
    val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    /** Week starts on Saturday. */
    val WEEK_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val WEEK = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    data class Date(val y: Int, val m: Int, val d: Int) {
        /** Persian yyyymm, e.g. 140509. */
        val ym: Int get() = y * 100 + m
    }

    fun toEpochDay(y: Int, m: Int, d: Int): Long {
        val (gy, gm, gd) = Bills.jalaliToGregorian(y, m, d)
        return LocalDate.of(gy, gm, gd).toEpochDay()
    }

    fun ofEpochDay(day: Long): Date {
        val g = LocalDate.ofEpochDay(day)
        val (y, m, d) = Bills.gregorianToJalali(g.year, g.monthValue, g.dayOfMonth)
        return Date(y, m, d)
    }

    fun isLeap(y: Int): Boolean = ofEpochDay(toEpochDay(y, 12, 30)) == Date(y, 12, 30)

    fun daysInMonth(y: Int, m: Int): Int = when {
        m <= 6 -> 31
        m <= 11 -> 30
        isLeap(y) -> 30
        else -> 29
    }

    /** 0 = Saturday ... 6 = Friday. */
    fun weekday(day: Long): Int = (LocalDate.ofEpochDay(day).dayOfWeek.value + 1) % 7

    /** Month [n] months after (y, m); n may be negative. */
    fun plusMonths(y: Int, m: Int, n: Int): Pair<Int, Int> {
        val i = y * 12 + (m - 1) + n
        return Math.floorDiv(i, 12) to Math.floorMod(i, 12) + 1
    }

    /** Persian yyyymm of a month [n] months after [ym]. */
    fun plusMonths(ym: Int, n: Int): Int = plusMonths(ym / 100, ym % 100, n).let { it.first * 100 + it.second }

    /** Months from [fromYm] to [toYm] (0 when the same month). */
    fun monthsBetween(fromYm: Int, toYm: Int): Int = (toYm / 100 * 12 + toYm % 100) - (fromYm / 100 * 12 + fromYm % 100)

    /** "۱۸ آذر" style text, with Latin digits (the UI turns digits Persian). */
    fun dayMonth(day: Long): String = ofEpochDay(day).let { "${it.d} ${MONTHS[it.m - 1]}" }
}
