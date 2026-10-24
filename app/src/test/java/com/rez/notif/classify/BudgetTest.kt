package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTest {
    private val lines = listOf(
        BudgetLine(1, "حقوق", 40_000_000, income = true, monthly = true, month = 140501),
        BudgetLine(2, "اجاره", 15_000_000, income = false, monthly = true, month = 140501),
        BudgetLine(3, "باشگاه", 1_500_000, income = false, monthly = true, month = 140507, until = 140508),
        BudgetLine(4, "پاداش", 5_000_000, income = true, monthly = false, month = 140508),
        BudgetLine(5, "تعمیر ماشین", 8_000_000, income = false, monthly = false, month = 140509),
    )

    @Test fun monthTotals() {
        val m = Budget.month(lines, 140508, installments = 3_000_000)
        assertEquals(45_000_000L, m.income)
        assertEquals(16_500_000L, m.spending)
        assertEquals(19_500_000L, m.totalOut)
        assertEquals(25_500_000L, m.left)
        assertEquals(56, m.savedPercent)
        val later = Budget.month(lines, 140509)
        assertEquals(40_000_000L, later.income)
        assertEquals(23_000_000L, later.spending) // gym ended, car repair once
        assertEquals(0L, Budget.month(lines, 140412).income)
    }

    @Test fun monthSteps() {
        assertEquals(140601, Budget.next(140512))
        assertEquals(140512, Budget.prev(140601))
        assertEquals(140509, Budget.next(140508))
    }

    @Test fun overspending() {
        val m = Budget.month(listOf(BudgetLine(1, "x", 100, true, true, 1), BudgetLine(2, "y", 150, false, true, 1)), 5)
        assertEquals(-50L, m.left)
        assertEquals(-50, m.savedPercent)
    }
}
