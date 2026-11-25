package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private fun raw(id: Long, time: Long, text: String, sender: String = "Mellat"): Ledger.Raw {
        val m = Money.parse(text)
        return Ledger.Raw(id, time, sender, text, m?.out ?: 0, m?.inn ?: 0, m?.balance)
    }

    @Test fun readsMyCardAndTheOtherSide() {
        val text = "انتقال از کارت 6037-99**-1234\nبه کارت 6219-86**-4521\nمبلغ: 500,000\nمانده: 1,000,000"
        assertEquals("Mellat 1234", Ledger.accountOf("Mellat", text))
        assertEquals("card:4521", Ledger.partyOf(text))
    }

    @Test fun amountsAndDatesAreNotCards() {
        val text = "برداشت: ۱,۲۵۰,۰۰۰\nحساب ۱۲۳۴۵۶۷۸۹۰\nمانده: ۱۲,۳۴۵,۶۷۸\n۰۸۰۱-۱۲:۳۰"
        val nums = Ledger.numbers(text)
        assertEquals(listOf("7890"), nums.map { it.last4 })
        assertEquals("", Ledger.partyOf(text))
    }

    @Test fun shopIsTheParty() {
        val text = "خرید\nمبلغ: 85,000\nپذیرنده: سوپر مارکت گلها\nمانده: 2,000,000"
        assertEquals("shop:سوپر مارکت گلها", Ledger.partyOf(text))
    }

    @Test fun transferToMyOtherCardIsOwn() {
        val a = raw(1, 1000, "انتقال از کارت 6037-99**-1234\nبه کارت 6219-86**-4521\nمبلغ: 500,000\nمانده: 1,000,000")
        val b = raw(2, 5_000, "واریز به کارت 6219-86**-4521\nمبلغ: 300,000\nمانده: 900,000", sender = "Saman")
        // 4521 shows up as an account in Saman's SMS, so it is mine even though the amounts differ.
        val e = Ledger.build(listOf(a, b), emptyMap(), emptyMap(), emptySet())
        assertEquals(Ledger.OWN, e.first { it.id == 1L }.category)
    }

    @Test fun learnsFromTheSameCard() {
        val a = raw(1, 1000, "انتقال از کارت 6037-99**-1234\nبه کارت 5022-29**-7777\nمبلغ: 120,000\nمانده: 880,000")
        val unknown = Ledger.build(listOf(a), emptyMap(), emptyMap(), emptySet()).single()
        assertNull(unknown.category)
        assertTrue(unknown.needsCategory)
        val known = Ledger.build(listOf(a), emptyMap(), mapOf("card:7777" to "سیگار"), emptySet()).single()
        assertEquals("سیگار", known.category)
        val tagged = Ledger.build(listOf(a), mapOf(1L to "خوراک"), mapOf("card:7777" to "سیگار"), emptySet()).single()
        assertEquals("خوراک", tagged.category)
    }

    @Test fun ownCardListWorks() {
        val a = raw(1, 1000, "انتقال از کارت 6037-99**-1234\nبه کارت 5022-29**-7777\nمبلغ: 120,000\nمانده: 880,000")
        assertEquals(Ledger.OWN, Ledger.build(listOf(a), emptyMap(), emptyMap(), setOf("7777")).single().category)
    }

    @Test fun findsMissingSms() {
        val e = Ledger.build(
            listOf(
                raw(1, 1000, "کارت 6037-99**-1234\n-100,000\nمانده:1,000,000"),
                // 380,000 left the card with no SMS in between.
                raw(2, 2000, "کارت 6037-99**-1234\n-120,000\nمانده:500,000"),
                raw(3, 3000, "کارت 6037-99**-1234\n-100,000\nمانده:400,000"),
            ),
            emptyMap(), emptyMap(), emptySet(),
        )
        val gaps = Ledger.gaps(e)
        assertEquals(1, gaps.size)
        assertEquals(380_000L, gaps[0].amount)
        assertEquals("Mellat 1234", gaps[0].account)

        // Naming the gap (a hand-entered entry on that account) closes it.
        val named = Ledger.Entry(-1, 1500, "Mellat 1234", 380_000, 0, null, Spending.Kind.OTHER_OUT, "", "سیگار", manual = true)
        assertTrue(Ledger.gaps(e + named).isEmpty())
    }

    @Test fun smallDifferencesAreNotGaps() {
        val e = Ledger.build(
            listOf(
                raw(1, 1000, "کارت 6037-99**-1234\n-100,000\nمانده:1,000,000"),
                raw(2, 2000, "کارت 6037-99**-1234\n-100,000\nمانده:890,000"),
            ),
            emptyMap(), emptyMap(), emptySet(),
        )
        assertTrue(Ledger.gaps(e).isEmpty())
    }

    @Test fun summaryLeavesOwnAndIgnoredOut() {
        val k = Spending.Kind.OTHER_OUT
        val s = Ledger.summarize(
            listOf(
                Ledger.Entry(1, 1, "a", 100, 0, null, k, "", "سیگار"),
                Ledger.Entry(2, 2, "a", 50, 0, null, k, "", "سیگار"),
                Ledger.Entry(3, 3, "a", 70, 0, null, k, "", null),
                Ledger.Entry(4, 4, "a", 500, 0, null, k, "", Ledger.OWN),
                Ledger.Entry(5, 5, "a", 900, 0, null, k, "", Ledger.IGNORE),
                Ledger.Entry(6, 6, "a", 0, 1000, null, Spending.Kind.SALARY, "", "حقوق"),
            ),
        )
        assertEquals(220L, s.out)
        assertEquals(1000L, s.inn)
        assertEquals(500L, s.own)
        assertEquals("سیگار" to 150L, s.byCategory.first())
        assertEquals(1, s.unsorted)
    }

    @Test fun maskedCardDigitsAreNotTheAmount() {
        val m = Money.parse("واریز به کارت 6219-86**-4521\nمبلغ: 300,000\nمانده: 900,000")!!
        assertEquals(300_000L, m.inn)
        assertEquals(0L, m.out)
        val o = Money.parse("انتقال از کارت 6037-99**-1234\nبه کارت 6219-86**-4521\nمبلغ: 500,000\nمانده: 1,000,000")!!
        assertEquals(500_000L, o.out)
    }

    @Test fun balancePerAccountFromNewestSms() {
        fun e(id: Long, time: Long, acc: String, bal: Long?, manual: Boolean = false) =
            Ledger.Entry(id, time, acc, 1, 0, bal, Spending.Kind.OTHER_OUT, "", null, manual = manual)
        val now = 1_000_000_000L
        val list = listOf(
            e(1, now - 5000, "Sepah 1234", 40_000_000),
            e(2, now - 1000, "Sepah 1234", 42_000_000),
            e(3, now - 500, "Sepah 1234", null),
            e(4, now - 2000, "Blu", 8_500_000),
            e(5, now - 100, "Blu", null, manual = true),
            e(6, now - 200L * 86_400_000L, "Old 9999", 1_000),
        )
        val b = Ledger.balances(list, now)
        assertEquals(listOf("Sepah 1234" to 42_000_000L, "Blu" to 8_500_000L), b.map { it.account to it.rial })
    }
}
