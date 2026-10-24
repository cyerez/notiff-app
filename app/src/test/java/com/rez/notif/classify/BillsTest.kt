package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class BillsTest {
    private fun ymd(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let {
        Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }
    private val now = Calendar.getInstance().apply { clear(); set(2026, 9, 24, 7, 0) }.timeInMillis

    @Test fun conversion() {
        assertEquals(Triple(2026, 10, 24), Bills.jalaliToGregorian(1405, 8, 2))
        assertEquals(Triple(2026, 3, 21), Bills.jalaliToGregorian(1405, 1, 1))
        assertEquals(Triple(2025, 3, 21), Bills.jalaliToGregorian(1404, 1, 1))
    }

    @Test fun installmentWithFullDate() {
        val b = Bills.parse("مشتری گرامی، مهلت پرداخت قسط وام شما به مبلغ ۵,۰۰۰,۰۰۰ ریال تا تاریخ ۱۴۰۵/۰۸/۱۰ است.", now)!!
        assertEquals(Triple(2026, 11, 1), ymd(b.due))
        assertEquals(5_000_000L, b.amount)
    }

    @Test fun billWithMonthName() {
        val b = Bills.parse("قبض برق شما صادر شد. آخرین مهلت پرداخت 15 آبان", now)
        assertNotNull(b)
        assertEquals(Triple(2026, 11, 6), ymd(b!!.due))
    }

    @Test fun shortYear() {
        val b = Bills.parse("سررسید قسط: 05/09/01", now)!!
        assertEquals(Triple(2026, 11, 22), ymd(b.due))
    }

    @Test fun paidIsNotABill() = assertNull(Bills.parse("قسط شما با موفقیت پرداخت شد 1405/08/10", now))

    @Test fun missedCallIsNotABill() = assertNull(Bills.parse("شما در تاریخ 01/08/1405 12:05 تعداد 1 تماس داشته اید", now))
}

class BillsMoreTest {
    private fun ymd(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let {
        Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }
    private val now = Calendar.getInstance().apply { clear(); set(2026, 9, 24, 7, 0) }.timeInMillis // 2 Aban 1405

    @Test fun toJalali() = assertEquals(Triple(1405, 8, 2), Bills.gregorianToJalali(2026, 10, 24))

    @Test fun monthlyThisMonth() = assertEquals(Triple(2026, 11, 1), ymd(Bills.nextMonthly(10, 1, now))) // 10 Aban

    @Test fun monthlyNextMonth() = assertEquals(Triple(2026, 11, 22), ymd(Bills.nextMonthly(1, 0, now))) // 1 Azar

    @Test fun reminderAlreadyPassedGoesToNextMonth() = assertEquals(Triple(2026, 11, 23), ymd(Bills.nextMonthly(2, 1, now))) // 2 Azar

    @Test fun internetPackIsNotABill() = assertNull(Bills.parse("مشترک گرامی بسته 5گیگ اینترنت شما تا تاریخ 1405/08/03 فعال است", now))
}
