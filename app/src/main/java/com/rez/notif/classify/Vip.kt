package com.rez.notif.classify

/** A chosen person ("افراد منتخب"). Numbers are stored as their last 10 digits (9121234567). */
data class VipPerson(val id: Long, val name: String, val numbers: List<String>)

/** Finds which chosen person an SMS or notification belongs to. Pure Kotlin, unit-tested. */
object VipMatcher {

    private val PHONE_IN_TEXT = Regex("(\\+?98|0098|0)?9[0-9]{9}")

    /** "+98 912 123 4567", "09121234567", "۰۹۱۲..." -> "9121234567"; null when it is not a phone number. */
    fun key(raw: String): String? {
        val d = Classifier.normalize(raw).filter { it in '0'..'9' }
        return if (d.length >= 10) d.takeLast(10) else null
    }

    fun match(people: List<VipPerson>, isSms: Boolean, sender: String, title: String, body: String): VipPerson? {
        if (people.isEmpty()) return null
        val numbers = HashSet<String>()
        key(sender)?.let { numbers += it }
        // Missed-call SMS from the operator carry the caller's number inside the text.
        PHONE_IN_TEXT.findAll(Classifier.normalize("$title $body").replace(" ", "")).forEach { m -> key(m.value)?.let { numbers += it } }
        people.firstOrNull { p -> p.numbers.any { it in numbers } }?.let { return it }
        if (isSms) return null

        // Notifications (Telegram, WhatsApp, the phone app) usually show the contact name.
        val t = Classifier.normalize(title)
        val b = Classifier.normalize(body)
        return people.firstOrNull { p ->
            val n = Classifier.normalize(p.name)
            n.length >= 3 && (t.contains(n) || (t.length >= 3 && n.contains(t)) || b.lineSequence().any { it.trim() == n })
        }
    }
}
