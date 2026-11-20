package com.rez.notif.classify

/**
 * What the user owes and is owed outside the bank SMS: debts and credits with people, cheques, and savings goals
 * (the emergency piggy bank). Amounts in toman. Pure Kotlin, tested.
 */
data class Debt(
    val id: Long = 0,
    val person: String,
    val toman: Long,
    /** True: I owe them. False: they owe me. */
    val iOwe: Boolean,
    /** When it should be paid back (millis), 0 = no date. */
    val due: Long = 0,
    val note: String = "",
    val settled: Boolean = false,
    val created: Long = 0,
)

data class Cheque(
    val id: Long = 0,
    val person: String,
    val toman: Long,
    /** Due date (millis). */
    val due: Long,
    /** True: a cheque I receive. False: a cheque I wrote. */
    val incoming: Boolean,
    val number: String = "",
    val status: Status = Status.PENDING,
    val created: Long = 0,
) {
    enum class Status(val label: String) { PENDING("منتظر"), CLEARED("پاس شد"), BOUNCED("برگشت خورد") }
}

data class Goal(
    val id: Long = 0,
    val title: String,
    val target: Long,
    val saved: Long = 0,
    /** How much to put in every month (toman), 0 = none. Counted in "left to spend". */
    val monthly: Long = 0,
    val created: Long = 0,
) {
    val percent: Int get() = if (target <= 0) 0 else (saved * 100 / target).coerceIn(0, 100).toInt()
    val done: Boolean get() = target in 1..saved
    /** Months still needed at [monthly] (0 when there is no monthly amount or the goal is reached). */
    val monthsLeft: Int get() = if (monthly <= 0 || done) 0 else ((target - saved + monthly - 1) / monthly).toInt()
}

object Commitments {
    data class Totals(val iOwe: Long, val owedToMe: Long, val chequesOut: Long, val chequesIn: Long)

    fun totals(debts: List<Debt>, cheques: List<Cheque>): Totals = Totals(
        debts.filter { !it.settled && it.iOwe }.sumOf { it.toman },
        debts.filter { !it.settled && !it.iOwe }.sumOf { it.toman },
        cheques.filter { it.status == Cheque.Status.PENDING && !it.incoming }.sumOf { it.toman },
        cheques.filter { it.status == Cheque.Status.PENDING && it.incoming }.sumOf { it.toman },
    )

    /** Pending cheques due within [days] days of [now] (or already late), soonest first. */
    fun dueSoon(cheques: List<Cheque>, now: Long, days: Int = 7): List<Cheque> =
        cheques.filter { it.status == Cheque.Status.PENDING && it.due <= now + days * 86_400_000L }.sortedBy { it.due }

    /** Per person: positive = they owe me, negative = I owe them (open debts only). */
    fun byPerson(debts: List<Debt>): List<Pair<String, Long>> =
        debts.filter { !it.settled }.groupBy { it.person.trim() }
            .map { (p, l) -> p to l.sumOf { if (it.iOwe) -it.toman else it.toman } }
            .filter { it.second != 0L }
            .sortedByDescending { kotlin.math.abs(it.second) }

    // ---------- CSV for Excel ----------

    /** One CSV field: quoted when it has a comma, quote or line break. */
    fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** CSV text with a UTF-8 byte-order mark, so Excel shows Persian correctly. */
    fun csv(header: List<String>, rows: List<List<String>>): String = buildString {
        append('﻿')
        append(header.joinToString(",") { csvField(it) }).append("\r\n")
        rows.forEach { r -> append(r.joinToString(",") { csvField(it) }).append("\r\n") }
    }
}
