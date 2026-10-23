package com.rez.notif.sms

/** Just enough of the MMS header format to read the message type and the sender. */
object MmsPdu {
    fun isNotification(pdu: ByteArray): Boolean =
        pdu.size > 2 && (pdu[0].toInt() and 0xff) == 0x8C && (pdu[1].toInt() and 0xff) == 0x82

    /** The "From" header (0x89): value-length, 0x80 (address present), then the address text. */
    fun from(pdu: ByteArray): String? {
        var i = 0
        while (i < pdu.size - 2) {
            if ((pdu[i].toInt() and 0xff) == 0x89) {
                var j = i + 1
                var len = pdu[j].toInt() and 0xff
                j++
                if (len == 31) { // uintvar length
                    len = 0
                    while (j < pdu.size) {
                        val b = pdu[j].toInt() and 0xff; j++
                        len = (len shl 7) or (b and 0x7f)
                        if (b and 0x80 == 0) break
                    }
                } else if (len > 30) return null
                if (j >= pdu.size || (pdu[j].toInt() and 0xff) != 0x80) return null
                val end = minOf(pdu.size, j + len)
                val text = String(pdu.copyOfRange(j + 1, end), Charsets.UTF_8)
                    .filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' }
                return text.substringBefore("/TYPE").ifBlank { null }
            }
            i++
        }
        return null
    }
}
