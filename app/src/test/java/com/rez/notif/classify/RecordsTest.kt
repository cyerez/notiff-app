package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsTest {
    @Test fun roundTrip() {
        val rows = listOf(
            listOf("I", "SMS", "+98912", "سلام\nخوبی؟\tتب", "back\\slash", ""),
            listOf("V", "مامان", "09121234567,09351234567"),
        )
        assertEquals(rows, Records.decode(Records.encode(rows)))
        assertEquals(emptyList<List<String>>(), Records.decode(Records.encode(emptyList())))
    }

    @Test(expected = Records.NotATransfer::class)
    fun refusesOtherFiles() {
        Records.decode("SQLite format 3")
    }
}
