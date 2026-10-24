package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DongTest {
    private val five = listOf("علی", "رضا", "سارا", "مینا", "نیما").map { DongPerson(it) }

    @Test fun differentPeopleEachDay() {
        // Day 1: all five share a 500 dinner paid by Ali. Day 2: only Reza and Sara share 300, Sara pays.
        val trip = DongTrip(
            name = "شمال", people = five, days = 2,
            expenses = listOf(
                DongExpense("شام", 500, payer = 0, day = 1, people = setOf(0, 1, 2, 3, 4)),
                DongExpense("ناهار", 300, payer = 2, day = 2, people = setOf(1, 2)),
            ),
        )
        val b = Dong.balances(trip)
        assertEquals(listOf(100L, 250L, 250L, 100L, 100L), b.map { it.share })
        assertEquals(400L, b[0].net)
        assertEquals(-250L, b[1].net)
        assertEquals(50L, b[2].net)
        assertEquals(0L, b.sumOf { it.net })
        val tr = Dong.settle(b)
        // Every debt is paid and every credit received.
        val after = LongArray(5) { b[it].net }
        tr.forEach { after[it.from] += it.amount; after[it.to] -= it.amount }
        assertTrue(after.all { it == 0L })
        assertTrue(tr.size <= 4)
        // Only day 2.
        assertEquals(listOf(0L, -150L, 150L, 0L, 0L), Dong.balances(trip, day = 2).map { it.net })
    }

    @Test fun weightsAndRounding() {
        val people = listOf(DongPerson("زوج", 2), DongPerson("تک"))
        val parts = Dong.split(DongExpense("x", 100, 1, 1, setOf(0, 1)), people)
        assertEquals(100L, parts.values.sum())
        assertEquals(67L, parts[0]) // 66 + 1 leftover
        assertEquals(33L, parts[1])
        val three = Dong.split(DongExpense("x", 100, 0, 1, setOf(0, 1, 2)), five)
        assertEquals(listOf(34L, 33L, 33L), listOf(three[0], three[1], three[2]))
    }

    @Test fun codecRoundTrip() {
        val trip = DongTrip(
            id = 7, name = "کیش ۱۴۰۵", people = five + DongPerson("خانواده", 3), days = 5,
            present = mapOf(2 to setOf(0, 1), 3 to emptySet()),
            expenses = listOf(DongExpense("بنزین", 1_200_000, 4, 3, setOf(0, 4, 5)), DongExpense("هتل", 9_000_000, 0, 1, setOf(0, 1, 2, 3, 4, 5))),
        )
        assertEquals(trip, Dong.decode(7, Dong.encode(trip)))
        assertEquals(DongTrip(id = 1, name = "خالی"), Dong.decode(1, Dong.encode(DongTrip(name = "خالی"))))
        assertNull(Dong.decode(1, "garbage"))
    }

    @Test fun addAndRemovePeople() {
        val trip = DongTrip(name = "t", people = five, days = 2, present = mapOf(1 to setOf(0, 3, 4)), expenses = listOf(DongExpense("x", 10, 4, 1, setOf(3, 4))))
        assertNull(Dong.removePerson(trip, 4))
        val r = Dong.removePerson(trip, 1)!!
        assertEquals(4, r.people.size)
        assertEquals(setOf(0, 2, 3), r.present[1])
        assertEquals(3, r.expenses[0].payer)
        assertEquals(setOf(2, 3), r.expenses[0].people)
        val a = Dong.addPerson(trip, "تازه")
        assertEquals(setOf(0, 3, 4, 5), a.present[1])
        assertEquals(setOf(0, 1, 2, 3, 4, 5), a.presentOn(2))
    }
}
