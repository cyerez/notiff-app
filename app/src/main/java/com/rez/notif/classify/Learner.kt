package com.rez.notif.classify

import kotlin.math.exp
import kotlin.math.ln

/**
 * A small offline model (multinomial naive Bayes) that learns word patterns from messages that
 * already have a category, including every change the user made by hand. It is used only for
 * messages the rules could not place ("Other"), and only when it is quite sure.
 * Pure Kotlin, no internet, trains in well under a second on a few thousand messages.
 */
class Learner private constructor(
    private val classes: Map<Category, ClassStats>,
    private val vocabSize: Int,
    private val totalDocs: Int,
) {
    private class ClassStats(val docs: Int, val words: Map<String, Int>, val wordTotal: Int)

    data class Guess(val category: Category, val confidence: Double)

    /** Best category for [text], or null when the model is not sure enough. */
    fun guess(text: String, minConfidence: Double = 0.85): Guess? {
        val tokens = tokens(text)
        if (tokens.size < 2 || classes.isEmpty()) return null
        val logs = classes.mapValues { (_, c) ->
            var lp = ln(c.docs.toDouble() / totalDocs)
            for (t in tokens) lp += ln((c.words[t] ?: 0) + 1.0) - ln(c.wordTotal + vocabSize.toDouble())
            lp
        }
        val best = logs.maxBy { it.value }
        // Softmax to a probability for the best class.
        val sum = logs.values.sumOf { exp(it - best.value) }
        val p = 1.0 / sum
        return if (p >= minConfidence) Guess(best.key, p) else null
    }

    companion object {
        /** Categories the model may suggest; "Other" is what it tries to fix, never an answer. */
        private val LEARNABLE = Category.entries.filter { it != Category.OTHER }
        private const val MIN_DOCS = 8

        fun train(samples: List<Pair<String, Category>>): Learner {
            val byClass = HashMap<Category, MutableList<List<String>>>()
            for ((text, cat) in samples) {
                if (cat !in LEARNABLE) continue
                val t = tokens(text)
                if (t.isNotEmpty()) byClass.getOrPut(cat) { ArrayList() } += t
            }
            val kept = byClass.filterValues { it.size >= MIN_DOCS }
            val vocab = HashSet<String>()
            val stats = kept.mapValues { (_, docs) ->
                val words = HashMap<String, Int>()
                var total = 0
                for (d in docs) for (w in d) { words[w] = (words[w] ?: 0) + 1; total++; vocab += w }
                ClassStats(docs.size, words, total)
            }
            return Learner(stats, vocab.size.coerceAtLeast(1), stats.values.sumOf { it.docs }.coerceAtLeast(1))
        }

        private val SPLIT = Regex("[^\\p{L}]+")

        /** Words of 2+ letters, Persian letters unified, numbers dropped (they differ every time). */
        fun tokens(text: String): List<String> =
            Classifier.normalize(text).lowercase().split(SPLIT).filter { it.length >= 2 }.distinct()
    }
}
