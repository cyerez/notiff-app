package com.rez.notif.classify

/** Reads amounts out of Iranian bank SMS: money out, money in, and the balance after. Amounts are in rial. */
object Money {
    data class Tx(val out: Long, val inn: Long, val balance: Long?)

    private val NUM = "([0-9][0-9,٬،.]*)"
    private val BALANCE = Regex("(?:مانده|موجودی|مانده حساب|بالانس)\\s*(?:حساب)?\\s*[:：]?\\s*([-+]?)\\s*$NUM")
    private val OUT_WORDS = listOf("برداشت", "خرید", "انتقال", "کسر", "پرداخت", "برداشت از", "انتقال از")
    private val IN_WORDS = listOf("واریز", "انتقال به حساب شما", "سود", "برگشت", "دریافت")
    private val SIGNED = Regex("(?<![0-9,])([-+])\\s?([0-9][0-9,٬]{2,})")

    fun parse(text: String): Tx? {
        val t = digits(text)
        val balance = BALANCE.find(t)?.let { m -> amount(m.groupValues[2])?.let { if (m.groupValues[1] == "-") -it else it } }
        val withoutBalance = BALANCE.find(t)?.let { t.removeRange(it.range) } ?: t

        var out = 0L
        var inn = 0L
        // "برداشت: 1,200,000" / "واریز 500,000"
        for (line in withoutBalance.split('\n', '؛', ';')) {
            val a = lineAmount(line) ?: continue
            when {
                IN_WORDS.any { it in line } -> inn = maxOf(inn, a)
                OUT_WORDS.any { it in line } -> out = maxOf(out, a)
            }
        }
        // "-1,200,000" / "+500,000" (Saman, Pasargad, Mellat...)
        if (out == 0L && inn == 0L) {
            SIGNED.findAll(withoutBalance).forEach { m ->
                val a = amount(m.groupValues[2]) ?: return@forEach
                if (a < 1000) return@forEach
                if (m.groupValues[1] == "-") out = maxOf(out, a) else inn = maxOf(inn, a)
            }
        }
        if (out == 0L && inn == 0L && balance == null) return null
        return Tx(out, inn, balance)
    }

    /** The money amount on one line: after "مبلغ", else a number written with commas, else the first number. */
    private fun lineAmount(line: String): Long? {
        Regex("مبلغ\\s*[:：]?\\s*$NUM").find(line)?.let { m -> amount(m.groupValues[1])?.let { return it } }
        val nums = Regex(NUM).findAll(line).map { it.value }.filter { (amount(it) ?: 0) >= 1000 }.toList()
        val pick = nums.firstOrNull { ',' in it || '٬' in it } ?: nums.firstOrNull()?.takeIf { it.length <= 12 } ?: return null
        return amount(pick)
    }

    private fun amount(s: String): Long? {
        val clean = s.replace(",", "").replace("٬", "").replace("،", "").replace(".", "")
        if (clean.length > 15 || clean.isEmpty()) return null
        return clean.toLongOrNull()
    }

    private fun digits(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                'ي' -> 'ی'
                else -> c
            },
        )
    }
}
