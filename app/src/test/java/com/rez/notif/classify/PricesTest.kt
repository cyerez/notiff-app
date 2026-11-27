package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class PricesTest {
    private val sample = """{"current":{"price_dollar_rl":{"p":"2,928,900","h":"2,958,900","l":"2,925,500","d":"12,000","dp":0.41,"dt":"high","t":"x"},""" +
        """"geram18":{"p":"257,978,000","h":"1","l":"1","d":"2,000","dp":1.2,"dt":"low","t":"x"},"ons":{"p":"3,643.79","dp":0,"dt":""}}}"""

    @Test fun readsDollarAndGold() {
        val q = Prices.parse(sample)
        assertEquals(2, q.size) // the coin is missing in this sample
        assertEquals(2_928_900L, q[0].rial)
        assertEquals(0.41, q[0].changePct, 0.001)
        assertEquals(Prices.Item.GOLD18, q[1].item)
        assertEquals(-1.2, q[1].changePct, 0.001)
    }

    @Test fun storesAndShortens() {
        val q = Prices.Quote(Prices.Item.DOLLAR, 2_928_900, -0.5)
        assertEquals(q, Prices.decode(Prices.Item.DOLLAR, Prices.encode(q)))
        assertEquals("292.9" to false, Prices.shortToman(2_928_900))
        assertEquals("25.8" to true, Prices.shortToman(257_978_000))
    }

    @Test fun brokenFileGivesNothing() {
        assertEquals(0, Prices.parse("<html>blocked</html>").size)
    }
}
