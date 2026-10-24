package com.rez.notif.classify

/**
 * Monthly income and spending the user writes down ("دخل و خرج"): salary, rent, gym, allowance...
 * A line is either every month or only in one Persian month. Amounts are in toman.
 */
data class BudgetLine(
    val id: Long = 0,
    val title: String,
    val amount: Long,
    val income: Boolean,
    /** True: counts in every month from [month] on. False: only in [month]. */
    val monthly: Boolean,
    /** Persian month as yyyymm, e.g. 140508. */
    val month: Int,
    /** For monthly lines: the last month it counts in (0 = no end). */
    val until: Int = 0,
)

object Budget {
    data class Month(
        val income: Long,
        val spending: Long,
        /** Installments the user entered in Money (Db.bills), counted as spending too. */
        val installments: Long,
        val incomeLines: List<BudgetLine>,
        val spendLines: List<BudgetLine>,
    ) {
        val totalOut: Long get() = spending + installments
        val left: Long get() = income - totalOut

        /** Share of income left over, 0..100 (negative when spending is more than income). */
        val savedPercent: Int get() = if (income <= 0) 0 else (left * 100 / income).toInt()
    }

    fun counts(l: BudgetLine, month: Int): Boolean =
        if (l.monthly) month >= l.month && (l.until == 0 || month <= l.until) else l.month == month

    fun month(lines: List<BudgetLine>, month: Int, installments: Long = 0): Month {
        val active = lines.filter { counts(it, month) }
        val (inn, out) = active.partition { it.income }
        return Month(inn.sumOf { it.amount }, out.sumOf { it.amount }, installments, inn, out)
    }

    fun next(month: Int): Int = if (month % 100 >= 12) (month / 100 + 1) * 100 + 1 else month + 1
    fun prev(month: Int): Int = if (month % 100 <= 1) (month / 100 - 1) * 100 + 12 else month - 1

    val MONTH_NAMES = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    fun of(jy: Int, jm: Int) = jy * 100 + jm
}
