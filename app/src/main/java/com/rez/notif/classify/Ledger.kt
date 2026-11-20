package com.rez.notif.classify

/**
 * The personal ledger ("دفتر حساب"): every bank SMS, Blu notification and hand-entered spending in one list.
 *
 * - Each entry belongs to an account (bank + last 4 digits of the card/account in the text).
 * - A transfer to one of the user's own cards is OWN: not spending, not income.
 * - Each spending gets a category (food, cigarettes, motorbike...): the user's choice, else what the app learned
 *   from the same destination card or shop, else none (the app asks once).
 * - Missing SMS: the balance after each SMS must equal the balance before it, minus what went out, plus what came in.
 *   When it does not, money moved without an SMS: that is a [Gap] the user can name.
 *
 * Amounts are rial (as in bank SMS). Pure Kotlin, tested.
 */
object Ledger {
    /** Category marker: money moved between the user's own accounts. */
    const val OWN = "__own"
    /** Category marker: a gap the user said to ignore (still closes the gap, never counted). */
    const val IGNORE = "__ignore"

    val SPEND_CATS = listOf(
        "خوراک", "سیگار", "موتور و بنزین", "رفت‌وآمد", "اجاره", "اینترنت و موبایل", "قبض آب و برق و گاز",
        "قسط و وام", "خرید", "تفریح", "سلامت", "هدیه", "برداشت نقدی", "کارمزد بانک", "سایر",
    )
    val INCOME_CATS = listOf("حقوق", "واریزی", "سایر درآمد")

    /** One money movement. [id] is the item id for SMS/notifications, minus the row id for hand-entered ones. */
    data class Entry(
        val id: Long,
        val time: Long,
        val account: String,
        val out: Long,
        val inn: Long,
        val balance: Long?,
        val kind: Spending.Kind,
        /** Who the money went to or came from: "card:4521", "shop:..." or "" when the text does not say. */
        val party: String,
        /** null: not known yet (ask). [OWN] or [IGNORE] are markers. */
        val category: String?,
        val text: String = "",
        val manual: Boolean = false,
    ) {
        val isOwn: Boolean get() = category == OWN
        val counted: Boolean get() = category != OWN && category != IGNORE
        val needsCategory: Boolean get() = category == null && out > 0
    }

    /** Money moved on [account] between two SMS without its own SMS. [amount] > 0: went out; < 0: came in. */
    data class Gap(val key: String, val account: String, val from: Long, val to: Long, val amount: Long)

    data class Summary(
        val out: Long,
        val inn: Long,
        val own: Long,
        /** Spending per category, biggest first ("" = not sorted yet). */
        val byCategory: List<Pair<String, Long>>,
        val incomeByCategory: List<Pair<String, Long>>,
        val unsorted: Int,
    )

    // ---------- reading the text ----------

    private fun digits(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                'ي' -> 'ی'
                'ك' -> 'ک'
                else -> c
            },
        )
    }

    /** Card or account numbers: "6037-99**-1234", "...1234", "0101234567". Plain amounts and dates are left out. */
    private val NUMBER = Regex("(?<![0-9,٬/:])([0-9*.]{2,}(?:[- ][0-9*]{2,}){0,4})(?![0-9,٬/:])")
    private val ACCOUNT_WORDS = listOf("کارت", "حساب", "سپرده", "مقصد", "ش.ح", "شماره", "از", "به")
    private val TO_WORDS = listOf("به کارت", "به حساب", "مقصد", "به شماره", "گیرنده", "واریز به")
    private val MONEY_WORDS = listOf("مبلغ", "مانده", "موجودی", "ریال", "تومان", "بالانس")

    data class Num(val last4: String, val isTarget: Boolean)

    /** Every card/account number in the text, with whether the words before it say "to" (the other side). */
    fun numbers(text: String): List<Num> {
        val t = digits(text)
        val out = ArrayList<Num>()
        for (m in NUMBER.findAll(t)) {
            val raw = m.groupValues[1]
            val ds = raw.filter { it.isDigit() }
            val tail = raw.takeLastWhile { it.isDigit() }
            val masked = '*' in raw || raw.startsWith("..")
            if (tail.length < 4) continue
            if (!masked && ds.length < 8) continue
            val before = t.substring(maxOf(0, m.range.first - 22), m.range.first)
            val after = t.substring(m.range.last + 1, minOf(t.length, m.range.last + 8))
            if (MONEY_WORDS.any { it in before.takeLast(10) } || "ریال" in after || "تومان" in after) continue
            if (!masked && ACCOUNT_WORDS.none { it in before }) continue
            // The nearest word before the number decides: "از کارت X به کارت Y".
            val near = before.takeLast(14)
            val target = TO_WORDS.any { it in near } || (" به " in " $near" && " از " !in " $near")
            out += Num(tail.takeLast(4), target)
        }
        return out
    }

    private val SHOP_LINE = Regex("(?:پذیرنده|فروشگاه|ترمینال|پایانه|خرید از|نام)\\s*[:：]?\\s*(.+)")

    /** Where a purchase was made, when the text names it ("پذیرنده: سوپر مارکت ..."), normalized. */
    fun shop(text: String): String? {
        for (line in digits(text).split('\n')) {
            val m = SHOP_LINE.find(line) ?: continue
            val name = m.groupValues[1].replace(Regex("[\\s\\u200c]+"), " ").replace(Regex("[:：،,.\\-]+$"), "").trim().lowercase()
            if (name.length >= 2 && !name.all { it.isDigit() || it == ' ' } || name.length >= 4) return name.take(40)
        }
        return null
    }

    /**
     * (my card, the other side's card) as last-4 digits. Money out: "از کارت X به کارت Y" = mine X, other Y.
     * Money in ([deposit]): "واریز به کارت X از کارت Y" = mine X, other Y.
     */
    fun sides(text: String, deposit: Boolean): Pair<String?, String?> {
        val n = numbers(text)
        if (n.isEmpty()) return null to null
        val target = n.firstOrNull { it.isTarget }?.last4
        val source = n.firstOrNull { !it.isTarget }?.last4
        return if (deposit) (target ?: source) to (if (target != null) source else null)
        else (source ?: if (n.size > 1) n[0].last4 else null) to target
    }

    /** The account an SMS is about: "<sender> <last 4>" or just the sender. */
    fun accountOf(sender: String, text: String, deposit: Boolean = false): String {
        val mine = sides(text, deposit).first
        return if (mine != null) "$sender $mine" else sender
    }

    /** Who the money went to/came from. */
    fun partyOf(text: String, deposit: Boolean = false): String {
        sides(text, deposit).second?.let { return "card:$it" }
        shop(text)?.let { return "shop:$it" }
        return ""
    }

    // ---------- building entries ----------

    /** A bank message already read by [Money]. */
    data class Raw(val id: Long, val time: Long, val sender: String, val text: String, val out: Long, val inn: Long, val balance: Long?)

    /**
     * Turns bank messages into entries. [tags]: categories the user picked per item id. [learned]: party -> category.
     * [ownCards]: last-4 digits of cards the user said are theirs (cards seen as an account in their SMS count too).
     */
    fun build(raws: List<Raw>, tags: Map<Long, String>, learned: Map<String, String>, ownCards: Set<String>): List<Entry> {
        val accounts = raws.associate { it.id to accountOf(it.sender, it.text, it.inn > 0 && it.out == 0L) }
        val mineSeen = accounts.values.mapNotNull { a -> a.substringAfterLast(' ', "").takeIf { it.length == 4 && it.all(Char::isDigit) } }.toSet()
        val own = ownCards + mineSeen
        val withMoney = raws.filter { it.out > 0 || it.inn > 0 }
        val internal = Spending.tag(withMoney.map { Spending.Tx(it.id, it.time, it.sender, it.text, it.out, it.inn) })
            .associate { it.tx.id to it.kind }
        return raws.map { r ->
            val kind = internal[r.id] ?: Spending.kindOf(Spending.Tx(r.id, r.time, r.sender, r.text, r.out, r.inn))
            val party = partyOf(r.text, r.inn > 0 && r.out == 0L)
            val partyCard = party.removePrefix("card:").takeIf { party.startsWith("card:") }
            val category = when {
                r.out == 0L && r.inn == 0L -> null
                tags[r.id] != null -> tags[r.id]
                kind == Spending.Kind.INTERNAL -> OWN
                partyCard != null && partyCard in own -> OWN
                party.isNotEmpty() && learned[party] != null -> learned[party]
                else -> defaultCategory(kind)
            }
            Entry(r.id, r.time, accounts.getValue(r.id), r.out, r.inn, r.balance, kind, party, category, r.text)
        }
    }

    /** Categories that need no question. */
    fun defaultCategory(kind: Spending.Kind): String? = when (kind) {
        Spending.Kind.SALARY -> "حقوق"
        Spending.Kind.INTEREST, Spending.Kind.REFUND -> "سایر درآمد"
        Spending.Kind.TRANSFER_IN -> "واریزی"
        Spending.Kind.FEE -> "کارمزد بانک"
        Spending.Kind.ATM -> "برداشت نقدی"
        Spending.Kind.INTERNAL -> OWN
        else -> null
    }

    // ---------- missing SMS ----------

    /**
     * Walks each account in time order. Entries without a balance (and hand-entered ones on that account, such as a
     * named gap) still count between two balances. A difference of at least [minRial] is a gap.
     */
    fun gaps(entries: List<Entry>, minRial: Long = 100_000): List<Gap> {
        val out = ArrayList<Gap>()
        for ((account, list) in entries.groupBy { it.account }) {
            var lastBal: Long? = null
            var lastId = 0L
            var lastTime = 0L
            var moved = 0L
            for (e in list.sortedWith(compareBy({ it.time }, { it.id }))) {
                if (e.balance == null) {
                    moved += e.inn - e.out
                    continue
                }
                val prev = lastBal
                if (prev != null) {
                    val expected = prev + moved + e.inn - e.out
                    val diff = expected - e.balance
                    if (kotlin.math.abs(diff) >= minRial) out += Gap("$account|$lastId|${e.id}", account, lastTime, e.time, diff)
                }
                lastBal = e.balance
                lastId = e.id
                lastTime = e.time
                moved = 0L
            }
        }
        return out.sortedByDescending { it.to }
    }

    // ---------- totals ----------

    fun summarize(entries: List<Entry>): Summary {
        var out = 0L; var inn = 0L; var own = 0L
        val by = HashMap<String, Long>()
        val inBy = HashMap<String, Long>()
        for (e in entries) {
            if (e.isOwn) { own += e.out; continue }
            if (!e.counted) continue
            out += e.out; inn += e.inn
            if (e.out > 0) by[e.category ?: ""] = (by[e.category ?: ""] ?: 0L) + e.out
            if (e.inn > 0) inBy[e.category ?: "واریزی"] = (inBy[e.category ?: "واریزی"] ?: 0L) + e.inn
        }
        return Summary(
            out, inn, own,
            by.entries.sortedByDescending { it.value }.map { it.key to it.value },
            inBy.entries.sortedByDescending { it.value }.map { it.key to it.value },
            entries.count { it.needsCategory },
        )
    }
}
