package com.rez.notif.classify

/**
 * The calendar ("تقویم"): the user's own events, planned expenses and trips, plus installments and cheques
 * that live elsewhere in the app, all turned into marks on days. Pure Kotlin, tested.
 */
object Planner {
    enum class Kind(val label: String) {
        EVENT("رویداد"),
        EXPENSE("خرج"),
        /** A trip or gathering over some days; it has its own dong ("حساب دنگ") through [PlanEvent.tripId]. */
        TRIP("سفر و دنگ"),
    }

    /**
     * One thing the user put on the calendar. [start] and [end] are epoch days (end inclusive, same as start for one day).
     * [remindAt]: when to remind (epoch millis), 0 for none.
     */
    data class PlanEvent(
        val id: Long = 0,
        val title: String,
        val start: Long,
        val end: Long = start,
        val kind: Kind = Kind.EVENT,
        val toman: Long = 0,
        val remindAt: Long = 0,
        val note: String = "",
        val tripId: Long = 0,
    ) {
        val days: Int get() = (end - start + 1).toInt().coerceAtLeast(1)
    }

    /** A monthly installment: due on Persian day [day]; [remaining] months left (0 = every month); starts in [startYm]. */
    data class Installment(val id: Long, val title: String, val toman: Long, val day: Int, val remaining: Int, val startYm: Int = 0)

    data class ChequeDue(val id: Long, val person: String, val toman: Long, val day: Long, val incoming: Boolean)

    enum class MarkKind { EVENT, EXPENSE, TRIP, INSTALLMENT, CHEQUE_IN, CHEQUE_OUT }

    /** Something to show on [day]. [refId]: the event, installment or cheque id. [first]/[last]: ends of a trip span. */
    data class Mark(
        val day: Long,
        val title: String,
        val toman: Long,
        val kind: MarkKind,
        val refId: Long,
        val first: Boolean = true,
        val last: Boolean = true,
    )

    /**
     * Every mark from [fromDay] to [toDay] (both inclusive), ordered by day.
     * Installments count from [todayYm] (or their start month if later): only [Installment.remaining] months are shown.
     */
    fun marks(
        fromDay: Long,
        toDay: Long,
        todayYm: Int,
        events: List<PlanEvent>,
        installments: List<Installment>,
        cheques: List<ChequeDue>,
    ): List<Mark> {
        val out = ArrayList<Mark>()
        for (e in events) {
            if (e.end < fromDay || e.start > toDay) continue
            val kind = when (e.kind) { Kind.EVENT -> MarkKind.EVENT; Kind.EXPENSE -> MarkKind.EXPENSE; Kind.TRIP -> MarkKind.TRIP }
            for (d in maxOf(e.start, fromDay)..minOf(e.end, toDay)) {
                out += Mark(d, e.title, if (d == e.start) e.toman else 0, kind, e.id, first = d == e.start, last = d == e.end)
            }
        }
        val fromYm = Jalali.ofEpochDay(fromDay).ym
        val toYm = Jalali.ofEpochDay(toDay).ym
        for (b in installments) {
            val firstYm = maxOf(todayYm, b.startYm)
            var ym = maxOf(fromYm, firstYm)
            while (ym <= toYm) {
                val n = Jalali.monthsBetween(firstYm, ym)
                if (b.remaining > 0 && n >= b.remaining) break
                val y = ym / 100; val m = ym % 100
                val day = Jalali.toEpochDay(y, m, b.day.coerceIn(1, Jalali.daysInMonth(y, m)))
                if (day in fromDay..toDay) out += Mark(day, b.title, b.toman, MarkKind.INSTALLMENT, b.id)
                ym = Jalali.plusMonths(ym, 1)
            }
        }
        for (c in cheques) {
            if (c.day in fromDay..toDay) out += Mark(c.day, c.person, c.toman, if (c.incoming) MarkKind.CHEQUE_IN else MarkKind.CHEQUE_OUT, c.id)
        }
        return out.sortedWith(compareBy({ it.day }, { it.kind.ordinal }))
    }

    /** The next things to do, for the home screen and the widget: one line per event (not per trip day). */
    fun upcoming(today: Long, days: Int, todayYm: Int, events: List<PlanEvent>, installments: List<Installment>, cheques: List<ChequeDue>): List<Mark> =
        marks(today, today + days - 1, todayYm, events, installments, cheques).filter { it.first || it.day == today }
}
