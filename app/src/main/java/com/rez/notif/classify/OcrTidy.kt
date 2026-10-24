package com.rez.notif.classify

/**
 * Cleans what the OCR read, line by line: drops noise (status-bar icons, half letters) and joins lines
 * that were only wrapped by the picture back into one sentence. Pure Kotlin so it can be tested.
 */
object OcrTidy {
    /** One line Tesseract found: its text, confidence 0..100, and top/bottom in pixels. */
    data class Line(val text: String, val confidence: Float, val top: Int, val bottom: Int)

    fun isNoise(l: Line): Boolean {
        val t = l.text.trim()
        if (t.isEmpty()) return true
        val letters = t.count { it.isLetterOrDigit() }
        if (letters == 0) return true
        if (l.confidence < 45f) return true
        if (l.confidence < 75f && letters < 4) return true
        // Mostly symbols ("|| © ¥ 4G") is an icon row, not text.
        if (letters * 2 < t.count { !it.isWhitespace() } && l.confidence < 85f) return true
        return false
    }

    fun tidy(lines: List<Line>): String {
        val kept = lines.filterNot(::isNoise)
        val out = StringBuilder()
        var prev: Line? = null
        for (l in kept) {
            val text = l.text.trim()
            val p = prev
            if (p == null) {
                out.append(text)
            } else {
                val h = (p.bottom - p.top).coerceAtLeast(1)
                val hNow = (l.bottom - l.top).coerceAtLeast(1)
                val gap = l.top - p.bottom
                val sameSize = hNow * 10 in h * 7..h * 14
                val wrapped = gap in -h..(h * 6 / 10) && sameSize
                out.append(if (wrapped) " " else "\n").append(text)
            }
            prev = l
        }
        return out.toString().trim()
    }
}
