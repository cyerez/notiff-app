package com.rez.notif.classify

/**
 * Splitting costs with friends ("دنگ") for a trip or a night out.
 * Every expense has its own payer, day and the people who share it, so the group can change from day to day.
 * A person can count as more than one share (e.g. a couple = 2). All amounts are in toman.
 */
data class DongPerson(val name: String, val weight: Int = 1)

data class DongExpense(
    val title: String,
    val amount: Long,
    /** Index into [DongTrip.people]. */
    val payer: Int,
    /** Day of the trip, from 1. */
    val day: Int,
    /** Indexes of the people who share this cost. */
    val people: Set<Int>,
)

data class DongTrip(
    val id: Long = 0,
    val name: String,
    val people: List<DongPerson> = emptyList(),
    val days: Int = 1,
    /** Who was there on each day; a day not listed means everyone. New expenses start with these people. */
    val present: Map<Int, Set<Int>> = emptyMap(),
    val expenses: List<DongExpense> = emptyList(),
    val updated: Long = 0,
) {
    fun presentOn(day: Int): Set<Int> = present[day] ?: people.indices.toSet()
}

object Dong {
    data class Balance(val person: Int, val paid: Long, val share: Long) {
        /** Positive: the group owes this person. Negative: this person owes. */
        val net: Long get() = paid - share
    }

    data class Transfer(val from: Int, val to: Int, val amount: Long)

    /**
     * Each person's part of one expense, by weight. The parts always add up to the exact amount:
     * the leftover from rounding goes to the first people.
     */
    fun split(e: DongExpense, people: List<DongPerson>): Map<Int, Long> {
        val who = e.people.filter { it in people.indices }.sorted()
        if (who.isEmpty() || e.amount <= 0) return emptyMap()
        val weights = who.associateWith { people[it].weight.coerceAtLeast(1).toLong() }
        val total = weights.values.sum()
        val parts = who.associateWith { e.amount * weights.getValue(it) / total }.toMutableMap()
        var left = e.amount - parts.values.sum()
        var i = 0
        while (left > 0) {
            val p = who[i % who.size]
            parts[p] = parts.getValue(p) + 1
            left--
            i++
        }
        return parts
    }

    fun balances(trip: DongTrip, day: Int? = null): List<Balance> {
        val paid = LongArray(trip.people.size)
        val share = LongArray(trip.people.size)
        for (e in trip.expenses) {
            if (day != null && e.day != day) continue
            val parts = split(e, trip.people)
            if (parts.isEmpty()) continue
            if (e.payer in trip.people.indices) paid[e.payer] += e.amount
            parts.forEach { (p, a) -> share[p] += a }
        }
        return trip.people.indices.map { Balance(it, paid[it], share[it]) }
    }

    /** The fewest simple payments that settle everyone: the biggest debtor pays the biggest creditor, again and again. */
    fun settle(balances: List<Balance>): List<Transfer> {
        val owe = balances.filter { it.net < 0 }.map { it.person to -it.net }.toMutableList()
        val get = balances.filter { it.net > 0 }.map { it.person to it.net }.toMutableList()
        val out = ArrayList<Transfer>()
        while (owe.isNotEmpty() && get.isNotEmpty()) {
            owe.sortByDescending { it.second }
            get.sortByDescending { it.second }
            val (d, da) = owe[0]
            val (c, ca) = get[0]
            val a = minOf(da, ca)
            out += Transfer(d, c, a)
            if (da == a) owe.removeAt(0) else owe[0] = d to da - a
            if (ca == a) get.removeAt(0) else get[0] = c to ca - a
        }
        return out
    }

    fun total(trip: DongTrip, day: Int? = null): Long = trip.expenses.filter { day == null || it.day == day }.sumOf { it.amount }

    /** Removes a person who has no expenses; returns null when they still pay or share something. */
    fun removePerson(trip: DongTrip, p: Int): DongTrip? {
        if (trip.expenses.any { it.payer == p || p in it.people }) return null
        fun shift(s: Set<Int>) = s.filter { it != p }.map { if (it > p) it - 1 else it }.toSet()
        return trip.copy(
            people = trip.people.filterIndexed { i, _ -> i != p },
            present = trip.present.mapValues { shift(it.value) },
            expenses = trip.expenses.map { it.copy(payer = if (it.payer > p) it.payer - 1 else it.payer, people = shift(it.people)) },
        )
    }

    /** Adds a person; they are present on every day that already has its own list. */
    fun addPerson(trip: DongTrip, name: String, weight: Int = 1): DongTrip {
        val i = trip.people.size
        return trip.copy(people = trip.people + DongPerson(clean(name), weight), present = trip.present.mapValues { it.value + i })
    }

    // ---- storage: one text field, no JSON library needed ----

    private const val F = '\u001F'
    private const val L = '\u001E'
    private const val P = '\u001D'

    private fun clean(s: String) = s.filter { it != F && it != L && it != P }.trim()
    private fun ints(s: String) = s.split(',').mapNotNull { it.toIntOrNull() }.toSet()

    fun encode(t: DongTrip): String = listOf(
        "1",
        clean(t.name),
        t.days.toString(),
        t.people.joinToString(L.toString()) { clean(it.name) + P + it.weight },
        t.present.entries.joinToString(L.toString()) { it.key.toString() + P + it.value.sorted().joinToString(",") },
        t.expenses.joinToString(L.toString()) { listOf(clean(it.title), it.amount, it.payer, it.day, it.people.sorted().joinToString(",")).joinToString(P.toString()) },
    ).joinToString(F.toString())

    fun decode(id: Long, s: String, updated: Long = 0): DongTrip? = runCatching {
        val f = s.split(F)
        fun list(x: String) = if (x.isEmpty()) emptyList() else x.split(L)
        DongTrip(
            id = id,
            name = f[1],
            days = f[2].toInt(),
            people = list(f[3]).map { val p = it.split(P); DongPerson(p[0], p.getOrNull(1)?.toIntOrNull() ?: 1) },
            present = list(f[4]).associate { val p = it.split(P); p[0].toInt() to ints(p.getOrElse(1) { "" }) },
            expenses = list(f[5]).map { val p = it.split(P); DongExpense(p[0], p[1].toLong(), p[2].toInt(), p[3].toInt(), ints(p[4])) },
            updated = updated,
        )
    }.getOrNull()
}
