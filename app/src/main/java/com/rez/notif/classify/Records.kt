package com.rez.notif.classify

/**
 * A tiny text format for moving messages between phones: one record per line, fields split by tabs,
 * with \\, \t, \n and \r escaped. The first line names the format so a wrong file is refused. Pure Kotlin, tested.
 */
object Records {
    const val HEADER = "NOTIF-TRANSFER\t1"

    class NotATransfer : Exception("not a Notif transfer file")

    fun encode(rows: List<List<String>>): String = buildString {
        append(HEADER).append('\n')
        rows.forEach { r -> append(r.joinToString("\t", transform = ::esc)).append('\n') }
    }

    fun decode(text: String): List<List<String>> {
        val lines = text.split('\n')
        if (lines.firstOrNull()?.trimEnd('\r') != HEADER) throw NotATransfer()
        return lines.drop(1).filter { it.isNotEmpty() }.map { line -> line.split('\t').map(::unesc) }
    }

    private fun esc(s: String) = buildString(s.length) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun unesc(s: String) = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> append('\t'); 'n' -> append('\n'); 'r' -> append('\r'); else -> append(s[i + 1])
                }
                i += 2
            } else { append(c); i++ }
        }
    }
}
