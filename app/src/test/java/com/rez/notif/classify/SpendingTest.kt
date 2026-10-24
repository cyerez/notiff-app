package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingTest {
    private fun tx(id: Long, min: Int, text: String, out: Long = 0, inn: Long = 0, bank: String = "Mellat") =
        Spending.Tx(id, min * 60_000L, bank, text, out, inn)

    @Test fun kinds() {
        assertEquals(Spending.Kind.ONLINE, Spending.kindOf(tx(1, 0, "خرید اینترنتی مبلغ 1,200,000", out = 1)))
        assertEquals(Spending.Kind.STORE, Spending.kindOf(tx(1, 0, "خرید با کارت از کارتخوان", out = 1)))
        assertEquals(Spending.Kind.STORE, Spending.kindOf(tx(1, 0, "خرید 450,000", out = 1)))
        assertEquals(Spending.Kind.TRANSFER_OUT, Spending.kindOf(tx(1, 0, "انتقال کارت به کارت", out = 1)))
        assertEquals(Spending.Kind.ATM, Spending.kindOf(tx(1, 0, "برداشت از خودپرداز", out = 1)))
        assertEquals(Spending.Kind.BILL, Spending.kindOf(tx(1, 0, "پرداخت قسط تسهیلات", out = 1)))
        assertEquals(Spending.Kind.SALARY, Spending.kindOf(tx(1, 0, "واریز حقوق", inn = 1)))
        assertEquals(Spending.Kind.TRANSFER_IN, Spending.kindOf(tx(1, 0, "واریز 2,000,000", inn = 1)))
    }

    @Test fun ownTransfersAreLeftOut() {
        val txs = listOf(
            tx(1, 0, "انتقال وجه -5,000,000", out = 5_000_000, bank = "Mellat"),
            tx(2, 1, "واریز +5,000,000", inn = 5_000_000, bank = "Saman"),
            tx(3, 10, "خرید اینترنتی", out = 300_000),
            tx(4, 200, "واریز", inn = 5_000_000), // same amount but hours later: real income
            tx(5, 300, "واریز حقوق", inn = 40_000_000),
        )
        val tagged = Spending.tag(txs)
        assertEquals(Spending.Kind.INTERNAL, tagged[0].kind)
        assertEquals(Spending.Kind.INTERNAL, tagged[1].kind)
        assertEquals(Spending.Kind.TRANSFER_IN, tagged[3].kind)
        val s = Spending.summarize(tagged)
        assertEquals(300_000L, s.out)
        assertEquals(45_000_000L, s.inn)
        assertEquals(5_000_000L, s.internal)
        assertEquals(Spending.Kind.SALARY, s.byKind.first().first)
    }

    @Test fun oneDepositPairsOnce() {
        val txs = listOf(
            tx(1, 0, "انتقال", out = 100_000), tx(2, 1, "انتقال", out = 100_000), tx(3, 2, "واریز", inn = 100_000),
        )
        assertEquals(2, Spending.tag(txs).count { it.kind == Spending.Kind.INTERNAL })
    }
}
