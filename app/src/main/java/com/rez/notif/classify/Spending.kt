package com.rez.notif.classify

/**
 * Makes bank SMS totals honest: money moved between the user's own accounts is not income or spending,
 * and every transaction gets a kind (card transfer, online, store, ATM, bills...). Pure Kotlin, tested.
 */
object Spending {
    enum class Kind(val label: String, val isIncome: Boolean) {
        TRANSFER_OUT("کارت به کارت و انتقال", false),
        ONLINE("خرید اینترنتی", false),
        STORE("خرید فروشگاهی", false),
        ATM("برداشت نقدی", false),
        BILL("قبض و قسط", false),
        FEE("کارمزد", false),
        OTHER_OUT("سایر برداشت‌ها", false),
        SALARY("حقوق", true),
        INTEREST("سود", true),
        REFUND("برگشت پول", true),
        TRANSFER_IN("واریز و انتقال", true),
        /** Between the user's own accounts: left out of the totals. */
        INTERNAL("بین حساب‌های خودم", false),
    }

    /** One bank SMS already read by [Money]: amount out or in (rial). */
    data class Tx(val id: Long, val time: Long, val bank: String, val text: String, val out: Long, val inn: Long)

    data class Tagged(val tx: Tx, val kind: Kind)

    data class Summary(val out: Long, val inn: Long, val internal: Long, val byKind: List<Pair<Kind, Long>>, val count: Int)

    private val ONLINE = listOf("اینترنتی", "درگاه", "پرداخت الکترونیک", "ipg", "شاپرک", "اینترنت", "آنلاین", "سایت", "اپلیکیشن", "موبایل بانک", "همراه بانک", "خرید شارژ", "شارژ سیم")
    private val STORE = listOf("کارتخوان", "پوز", "pos", "فروشگاه", "پایانه")
    private val ATM = listOf("خودپرداز", "atm", "برداشت نقدی", "عابربانک", "عابر بانک")
    private val BILL = listOf("قبض", "قسط", "اقساط", "تسهیلات", "وام", "بیمه", "عوارض")
    private val FEE = listOf("کارمزد", "آبونمان", "هزینه پیامک", "حق اشتراک")
    private val TRANSFER = listOf("کارت به کارت", "کارتبکارت", "انتقال", "پایا", "ساتنا", "شبا", "حواله")
    private val SALARY = listOf("حقوق", "دستمزد", "مزد")
    private val INTEREST = listOf("سود")
    private val REFUND = listOf("برگشت", "عودت", "استرداد", "بازگشت")

    private fun has(t: String, words: List<String>) = words.any { it in t }

    fun kindOf(tx: Tx): Kind {
        val t = tx.text.lowercase()
        if (tx.inn > 0 && tx.out == 0L) return when {
            has(t, SALARY) -> Kind.SALARY
            has(t, REFUND) -> Kind.REFUND
            has(t, INTEREST) -> Kind.INTEREST
            else -> Kind.TRANSFER_IN
        }
        return when {
            has(t, FEE) && !has(t, TRANSFER) && !t.contains("خرید") -> Kind.FEE
            has(t, BILL) -> Kind.BILL
            has(t, ATM) -> Kind.ATM
            has(t, TRANSFER) -> Kind.TRANSFER_OUT
            has(t, ONLINE) -> Kind.ONLINE
            has(t, STORE) || t.contains("خرید") -> Kind.STORE
            else -> Kind.OTHER_OUT
        }
    }

    /**
     * A transfer out and a deposit of the same amount within [window] (usually seconds apart, in two banks' SMS)
     * is the user moving their own money. Both sides become INTERNAL. Each deposit pairs with at most one transfer.
     */
    fun tag(txs: List<Tx>, window: Long = 30 * 60_000L): List<Tagged> {
        val kinds = txs.associate { it.id to kindOf(it) }.toMutableMap()
        val outs = txs.filter { it.out > 0 && kinds[it.id] in setOf(Kind.TRANSFER_OUT, Kind.OTHER_OUT) }.sortedBy { it.time }
        val ins = txs.filter { it.inn > 0 && kinds[it.id] == Kind.TRANSFER_IN }.sortedBy { it.time }.toMutableList()
        for (o in outs) {
            val match = ins.firstOrNull { i -> i.inn == o.out && kotlin.math.abs(i.time - o.time) <= window && i.id != o.id } ?: continue
            kinds[o.id] = Kind.INTERNAL
            kinds[match.id] = Kind.INTERNAL
            ins.remove(match)
        }
        return txs.map { Tagged(it, kinds.getValue(it.id)) }
    }

    fun summarize(tagged: List<Tagged>): Summary {
        var out = 0L; var inn = 0L; var internal = 0L
        val by = LinkedHashMap<Kind, Long>()
        for ((tx, k) in tagged) {
            if (k == Kind.INTERNAL) { internal += tx.out; continue }
            out += tx.out; inn += tx.inn
            by[k] = (by[k] ?: 0L) + tx.out + tx.inn
        }
        return Summary(out, inn, internal, by.entries.sortedByDescending { it.value }.map { it.key to it.value }, tagged.count { it.kind != Kind.INTERNAL })
    }
}
