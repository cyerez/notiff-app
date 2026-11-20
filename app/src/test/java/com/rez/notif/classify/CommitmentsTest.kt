package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentsTest {
    @Test fun totalsAndPeople() {
        val debts = listOf(
            Debt(1, "علی", 500, iOwe = true),
            Debt(2, "علی", 200, iOwe = false),
            Debt(3, "سارا", 300, iOwe = false),
            Debt(4, "رضا", 900, iOwe = true, settled = true),
        )
        val cheques = listOf(
            Cheque(1, "صاحبخونه", 1000, due = 10, incoming = false),
            Cheque(2, "مشتری", 400, due = 20, incoming = true, status = Cheque.Status.CLEARED),
        )
        val t = Commitments.totals(debts, cheques)
        assertEquals(500L, t.iOwe)
        assertEquals(500L, t.owedToMe)
        assertEquals(1000L, t.chequesOut)
        assertEquals(0L, t.chequesIn)
        assertEquals(setOf("سارا" to 300L, "علی" to -300L), Commitments.byPerson(debts).toSet())
    }

    @Test fun chequesDueSoon() {
        val day = 86_400_000L
        val c = listOf(
            Cheque(1, "a", 1, due = 10 * day, incoming = false),
            Cheque(2, "b", 1, due = 3 * day, incoming = false),
            Cheque(3, "c", 1, due = 1 * day, incoming = false, status = Cheque.Status.CLEARED),
        )
        assertEquals(listOf(2L), Commitments.dueSoon(c, now = 0).map { it.id })
    }

    @Test fun goals() {
        val g = Goal(title = "اضطراری", target = 10_000, saved = 2_500, monthly = 3_000)
        assertEquals(25, g.percent)
        assertEquals(3, g.monthsLeft)
        assertFalse(g.done)
        assertTrue(g.copy(saved = 10_000).done)
    }

    @Test fun csvQuotesAndBom() {
        val s = Commitments.csv(listOf("a", "b"), listOf(listOf("x,y", "say \"hi\"")))
        assertTrue(s.startsWith("﻿"))
        assertTrue(s.contains("\"x,y\",\"say \"\"hi\"\"\""))
    }

    @Test fun leftToSpend() {
        val l = Ledger.leftToSpend(income = 30_000, fixed = 12_000, spentVariable = 6_000, saving = 2_000, daysLeft = 10)
        assertEquals(10_000L, l.left)
        assertEquals(1_000L, l.perDay)
        assertEquals(0L, Ledger.leftToSpend(1, 5, 0, 0, 3).perDay)
    }

    @Test fun unreadableQueue() {
        assertTrue(Ledger.unreadable("بانک ملت\nخرید از پایانه\n1,250,000", null))
        assertFalse(Ledger.unreadable("رمز پویا خرید: 123456 مبلغ 50,000", null))
        assertFalse(Ledger.unreadable("سلام، وقت بخیر", null))
        assertFalse(Ledger.unreadable("برداشت: 1,250,000", Money.Tx(1_250_000, 0, null)))
    }
}
