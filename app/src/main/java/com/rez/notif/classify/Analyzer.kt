package com.rez.notif.classify

/**
 * The "first brain" pass that runs after the first full SMS scan.
 *
 * Step 1: every message gets a category from [Classifier] (message by message).
 * Step 2: messages are grouped by sender. When most messages of one sender share a category,
 *         that sender's leftover "other" messages take that category too
 *         (an ad line sends some messages without ad words, a bank sends some short notes).
 * Step 3: categories are summarized into suggestions (count, top senders, samples) for the user to pick.
 */
object Analyzer {

    data class Msg(val key: String, val sender: String, val text: String, var category: Category)

    data class Suggestion(
        val category: Category,
        val count: Int,
        val topSenders: List<String>,
        val samples: List<String>,
        /** Pre-ticked in the picker: big enough to deserve its own tile. */
        val recommended: Boolean,
    )

    /** Fills the gaps using sender majority. Returns how many messages changed. */
    fun smoothBySender(messages: List<Msg>, minMessages: Int = 3, minShare: Double = 0.6): Int {
        var changed = 0
        messages.groupBy { it.sender }.forEach { (_, group) ->
            if (group.size < minMessages) return@forEach
            val counted = group.filter { it.category != Category.OTHER }.groupingBy { it.category }.eachCount()
            val (top, topCount) = counted.maxByOrNull { it.value }?.toPair() ?: return@forEach
            if (topCount.toDouble() / group.size < minShare) return@forEach
            if (top == Category.OTP) return@forEach // an OTP sender's other texts are not OTP codes
            group.filter { it.category == Category.OTHER }.forEach {
                it.category = top
                changed++
            }
        }
        return changed
    }

    fun suggest(messages: List<Msg>): List<Suggestion> {
        val total = messages.size.coerceAtLeast(1)
        return messages.groupBy { it.category }
            .map { (cat, group) ->
                Suggestion(
                    category = cat,
                    count = group.size,
                    topSenders = group.groupingBy { it.sender }.eachCount().entries
                        .sortedByDescending { it.value }.take(3).map { it.key },
                    samples = group.asSequence().map { it.text.take(90) }.distinct().take(3).toList(),
                    recommended = cat != Category.ADS && cat != Category.OTHER &&
                        group.size >= maxOf(3, total / 100),
                )
            }
            .sortedByDescending { it.count }
    }
}
