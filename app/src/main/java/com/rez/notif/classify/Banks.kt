package com.rez.notif.classify

/**
 * Which bank an SMS sender (or a ledger account "<sender> <last 4>") belongs to: its name, brand color and
 * logo id (the logo is res/drawable-nodpi/bank_<id>.png, from github.com/amastaneh/IranianBankLogos).
 * Pure Kotlin, tested.
 */
object Banks {
    /** [hasLogo] false: the UI draws a circle in [color] with the first letter instead. */
    data class Bank(val id: String, val fa: String, val en: String, val color: Long, val hasLogo: Boolean = true)

    private class Rule(val bank: Bank, val words: List<String>)

    private fun b(id: String, fa: String, en: String, color: Long, vararg words: String, logo: Boolean = true) =
        Rule(Bank(id, fa, en, color, logo), words.toList())

    /** Order matters: longer names first ("توسعه صادرات" before "صادرات"). Words are compared without spaces/dots, lower case. */
    private val RULES = listOf(
        b("blu", "بلو", "Blu", 0xFF2F6BFF, "blu", "بلو", logo = false),
        b("edbi", "توسعه صادرات", "EDBI", 0xFF0D4C8B, "edbi", "toseesaderat", "توسعهصادرات"),
        b("taavon", "توسعه تعاون", "Tosee Taavon", 0xFF00753A, "ttbank", "taavon", "توسعهتعاون"),
        b("mehriran", "مهر ایران", "Mehr Iran", 0xFF00A859, "mehriran", "qmb", "مهرایران"),
        b("middleeast", "خاورمیانه", "Middle East", 0xFFF39200, "middleeast", "khavarmianeh", "خاورمیانه"),
        b("iranzamin", "ایران زمین", "Iran Zamin", 0xFF6A2C91, "iranzamin", "ایرانزمین"),
        b("novin", "اقتصاد نوین", "EN Bank", 0xFF7A1F5C, "eghtesad", "novin", "enbank", "اقتصادنوین"),
        b("pasargad", "پاسارگاد", "Pasargad", 0xFFE8A900, "pasargad", "bpi", "پاسارگاد", "5000114"),
        b("tejarat", "تجارت", "Tejarat", 0xFF1F3B8F, "tejarat", "تجارت"),
        b("mellat", "ملت", "Mellat", 0xFFE2262A, "mellat", "ملت"),
        b("melli", "ملی", "Melli", 0xFF0B4EA2, "melli", "bmi", "ملی"),
        b("sepah", "سپه", "Sepah", 0xFF0E4D92, "sepah", "سپه"),
        b("saderat", "صادرات", "Saderat", 0xFF283C8E, "saderat", "bsi", "صادرات"),
        b("saman", "سامان", "Saman", 0xFF1B9BD7, "saman", "سامان", "989999920000"),
        b("parsian", "پارسیان", "Parsian", 0xFF8B1E3F, "parsian", "پارسیان"),
        b("keshavarzi", "کشاورزی", "Keshavarzi", 0xFF2E8B57, "keshavarzi", "agribank", "bki", "کشاورزی"),
        b("maskan", "مسکن", "Maskan", 0xFFF58220, "maskan", "مسکن"),
        b("refah", "رفاه", "Refah", 0xFF3949AB, "refah", "رفاه"),
        b("ayandeh", "آینده", "Ayandeh", 0xFF6D2077, "ayandeh", "آینده"),
        b("karafarin", "کارآفرین", "Karafarin", 0xFF00875A, "karafarin", "کارآفرین"),
        b("sina", "سینا", "Sina", 0xFF0072BC, "sinabank", "banksina", "سینا"),
        b("shahr", "شهر", "Shahr", 0xFFD71920, "shahr", "بانکشهر"),
        b("dey", "دی", "Dey", 0xFF00838F, "daybank", "deybank", "bankdey", "bankday", "بانکدی"),
        b("resalat", "رسالت", "Resalat", 0xFF00A651, "resalat", "رسالت"),
        b("sarmayeh", "سرمایه", "Sarmayeh", 0xFF5B2C83, "sarmayeh", "سرمایه"),
        b("post", "پست بانک", "Post Bank", 0xFF00599C, "postbank", "پستبانک"),
        b("ansar", "انصار", "Ansar", 0xFF00796B, "ansar", "انصار"),
        b("ghavamin", "قوامین", "Ghavamin", 0xFF2E7D32, "ghavamin", "قوامین"),
        b("hekmat", "حکمت", "Hekmat", 0xFF1565C0, "hekmat", "حکمت"),
        b("tourism", "گردشگری", "Tourism", 0xFF00897B, "tourism", "gardeshgari", "گردشگری"),
        b("sanat", "صنعت و معدن", "Sanat Madan", 0xFF37474F, "sanatmadan", "bim", "صنعتومعدن"),
        b("melal", "ملل", "Melal", 0xFF1A237E, "melal", "ملل"),
        b("ivbb", "ایران و ونزوئلا", "Iran-Venezuela", 0xFFC62828, "ivbb", "ونزوئلا"),
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** The bank of a sender name or number, or null when unknown. */
    fun of(sender: String): Bank? {
        val n = norm(sender)
        if (n.isEmpty()) return null
        // Short Latin codes ("bmi", "bsi") must be the whole name, or "Bimeh" would look like "bim".
        return RULES.firstOrNull { r -> r.words.any { w -> if (w.length <= 3 && w.all { it in 'a'..'z' }) n == w || n == w + "bank" else n.contains(w) } }?.bank
    }

    /**
     * What to show for a ledger account. [names]: names the user typed for senders the app did not know
     * (sender -> name, e.g. "9830009417" -> "ملت حقوق"); the bank is then found from that name.
     * [name] is the user name, else the bank name ([fa] tells which language), else the sender.
     */
    data class Shown(val bank: Bank?, val name: String, val last4: String?)

    fun shown(account: String, names: Map<String, String>, fa: Boolean = true): Shown {
        val (sender, last4) = split(account)
        val custom = names[sender]?.trim()?.takeIf { it.isNotEmpty() }
        val bank = custom?.let { of(it) } ?: of(sender)
        val name = custom ?: bank?.let { if (fa) it.fa else it.en } ?: sender
        return Shown(bank, name, last4)
    }

    /** Splits a ledger account "<sender> <last 4>" into (sender, last 4 or null). */
    fun split(account: String): Pair<String, String?> {
        val last = account.substringAfterLast(' ', "")
        return if (last.length == 4 && last.all(Char::isDigit)) account.dropLast(5) to last else account to null
    }
}
