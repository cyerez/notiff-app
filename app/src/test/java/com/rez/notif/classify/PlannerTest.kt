package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val nov27 = LocalDate.of(2026, 11, 27).toEpochDay()

    @Test fun jalaliDates() {
        assertEquals(Jalali.Date(1405, 9, 6), Jalali.ofEpochDay(nov27))
        assertEquals(nov27, Jalali.toEpochDay(1405, 9, 6))
        assertEquals(6, Jalali.weekday(nov27)) // Friday
        assertEquals(0, Jalali.weekday(nov27 + 1)) // Saturday
        assertTrue(Jalali.isLeap(1403))
        assertFalse(Jalali.isLeap(1404))
        assertEquals(30, Jalali.daysInMonth(1403, 12))
        assertEquals(29, Jalali.daysInMonth(1404, 12))
        assertEquals(1406 to 2, Jalali.plusMonths(1405, 12, 2))
        assertEquals(140411, Jalali.plusMonths(140501, -2))
        assertEquals(3, Jalali.monthsBetween(140511, 140602))
    }

    @Test fun tripSpansItsDays() {
        val start = Jalali.toEpochDay(1405, 9, 18)
        val trip = Planner.PlanEvent(1, "شمال", start, Jalali.toEpochDay(1405, 9, 23), Planner.Kind.TRIP, toman = 5_000_000)
        val m = Planner.marks(Jalali.toEpochDay(1405, 9, 1), Jalali.toEpochDay(1405, 9, 30), 140509, listOf(trip), emptyList(), emptyList())
        assertEquals(6, m.size)
        assertTrue(m.first().first && !m.first().last)
        assertTrue(m.last().last)
        assertEquals(5_000_000L, m.sumOf { it.toman }) // the amount shows once, on the first day
        val up = Planner.upcoming(start - 2, 30, 140509, listOf(trip), emptyList(), emptyList())
        assertEquals(1, up.size)
    }

    @Test fun installmentsRepeatMonthlyForTheirCount() {
        val car = Planner.Installment(7, "قسط ماشین", 3_000_000, day = 31, remaining = 3)
        val from = Jalali.toEpochDay(1405, 6, 1)
        val to = Jalali.toEpochDay(1405, 12, 29)
        val m = Planner.marks(from, to, 140506, emptyList(), listOf(car), emptyList())
        // Shahrivar 31, then Mehr and Aban have 30 days: moved to their last day.
        assertEquals(listOf(Jalali.toEpochDay(1405, 6, 31), Jalali.toEpochDay(1405, 7, 30), Jalali.toEpochDay(1405, 8, 30)), m.map { it.day })
        val later = Planner.Installment(8, "وام", 1_000_000, day = 5, remaining = 2, startYm = 140510)
        val m2 = Planner.marks(from, to, 140506, emptyList(), listOf(later), emptyList())
        assertEquals(listOf(Jalali.toEpochDay(1405, 10, 5), Jalali.toEpochDay(1405, 11, 5)), m2.map { it.day })
        val forever = Planner.Installment(9, "اجاره", 10_000_000, day = 1, remaining = 0)
        assertEquals(7, Planner.marks(from, to, 140506, emptyList(), listOf(forever), emptyList()).size)
    }

    @Test fun chequesOnTheirDay() {
        val c = Planner.ChequeDue(3, "علی", 2_000_000, nov27 + 3, incoming = true)
        val m = Planner.marks(nov27, nov27 + 6, 140509, emptyList(), emptyList(), listOf(c))
        assertEquals(Planner.MarkKind.CHEQUE_IN, m.single().kind)
    }
}
