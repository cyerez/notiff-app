package com.rez.notif.classify

/** What a user rule does with a matching notification or SMS. */
enum class RuleAction(val label: String) {
    ALERT("همیشه نشون بده (حتی وقتی همه مخفی میشن)"),
    QUIET("فقط توی اپ، بی‌صدا"),
    REMIND("یادم بنداز تا وقتی ببینمش"),
    DELETE("بفرست به تبلیغ‌های مخفی"),
    MOVE("ببر به یه دسته دیگه"),
}

/**
 * A rule the user wrote: "when [these conditions], do [action]".
 * Empty conditions match everything; all filled conditions must match (AND).
 */
data class Rule(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    /** App packages; "sms" means SMS. Empty = any app. */
    val apps: Set<String> = emptySet(),
    /** Part of the sender / chat name. */
    val sender: String = "",
    /** Any of these words in the title or text (or one regex when [regex]). */
    val words: List<String> = emptyList(),
    val regex: Boolean = false,
    /** Category ids the item must be in. Empty = any. */
    val categories: Set<String> = emptySet(),
    /** Hours of day, -1 = all day. A range may wrap midnight (23 -> 7). */
    val fromHour: Int = -1,
    val toHour: Int = -1,
    val action: RuleAction = RuleAction.QUIET,
    /** For MOVE: the target category id. */
    val target: String = "",
)

object Rules {
    data class Input(
        val isSms: Boolean,
        val packageName: String,
        val sender: String,
        val text: String,
        val category: String,
        val hour: Int,
    )

    fun matches(r: Rule, i: Input): Boolean {
        if (!r.enabled) return false
        if (r.apps.isNotEmpty()) {
            val app = if (i.isSms) "sms" else i.packageName
            if (app !in r.apps) return false
        }
        if (r.sender.isNotBlank() && !Classifier.normalize(i.sender).contains(Classifier.normalize(r.sender))) return false
        if (r.categories.isNotEmpty() && i.category !in r.categories) return false
        if (!inHours(r.fromHour, r.toHour, i.hour)) return false
        val words = r.words.filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val hit = if (r.regex) {
                runCatching { Regex(words.first(), RegexOption.IGNORE_CASE).containsMatchIn(i.sender + "\n" + i.text) }.getOrDefault(false)
            } else {
                val t = Classifier.normalize(i.sender + " " + i.text)
                words.any { t.contains(Classifier.normalize(it)) }
            }
            if (!hit) return false
        }
        return true
    }

    /** The first enabled rule that matches, in the user's order. */
    fun first(rules: List<Rule>, i: Input): Rule? = rules.firstOrNull { matches(it, i) }

    fun inHours(from: Int, to: Int, hour: Int): Boolean {
        if (from < 0 || to < 0 || from == to) return true
        return if (from < to) hour in from until to else hour >= from || hour < to
    }

    fun validRegex(p: String): Boolean = runCatching { Regex(p) }.isSuccess

    // ---- storage: one line of text per rule, no JSON library needed ----

    private const val F = '\u001F'
    private const val L = '\u001E'

    fun encode(r: Rule): String = listOf(
        "1", r.name, if (r.enabled) "1" else "0", r.apps.joinToString(L.toString()), r.sender,
        r.words.joinToString(L.toString()), if (r.regex) "1" else "0", r.categories.joinToString(L.toString()),
        r.fromHour.toString(), r.toHour.toString(), r.action.name, r.target,
    ).joinToString(F.toString())

    fun decode(id: Long, s: String): Rule? = runCatching {
        val f = s.split(F)
        fun set(x: String) = x.split(L).filter { it.isNotEmpty() }
        Rule(
            id = id,
            name = f[1],
            enabled = f[2] == "1",
            apps = set(f[3]).toSet(),
            sender = f[4],
            words = set(f[5]),
            regex = f[6] == "1",
            categories = set(f[7]).toSet(),
            fromHour = f[8].toInt(),
            toHour = f[9].toInt(),
            action = RuleAction.valueOf(f[10]),
            target = f[11],
        )
    }.getOrNull()

    /** Ready-made rules to start from. */
    val templates: List<Rule> = listOf(
        Rule(name = "کلمه‌های فوری همیشه نشون داده بشن", words = listOf("فوری", "اورژانس", "urgent", "زنگ بزن"), action = RuleAction.ALERT),
        Rule(
            name = "پیام‌رسان‌ها از ۱۱ شب تا ۷ صبح ساکت",
            categories = setOf(Category.SOCIAL.id), fromHour = 23, toHour = 7, action = RuleAction.QUIET,
        ),
        Rule(name = "تخفیف و جشنواره پاک بشه", words = listOf("تخفیف", "جشنواره", "کد هدیه", "حراج", "٪"), categories = setOf(Category.APPS.id, Category.SHOPPING.id, Category.OTHER.id), action = RuleAction.DELETE),
        Rule(name = "قبض و اقساط یادم بمونه", words = listOf("مهلت پرداخت", "سررسید", "قسط", "بدهی", "قبض"), action = RuleAction.REMIND),
        Rule(name = "تراکنش بانکی همیشه نشون داده بشه", categories = setOf(Category.BANK.id), action = RuleAction.ALERT),
    )
}
