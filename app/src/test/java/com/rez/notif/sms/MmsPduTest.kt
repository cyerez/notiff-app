package com.rez.notif.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPduTest {
    private fun pdu(vararg parts: Any): ByteArray = parts.flatMap { p ->
        when (p) {
            is Int -> listOf(p.toByte())
            is String -> p.toByteArray().toList()
            else -> error("bad")
        }
    }.toByteArray()

    @Test fun readsSender() {
        val addr = "+989121234567/TYPE=PLMN"
        val p = pdu(0x8C, 0x82, 0x98, "tx1", 0, 0x8D, 0x92, 0x89, addr.length + 2, 0x80, addr, 0)
        assertTrue(MmsPdu.isNotification(p))
        assertEquals("+989121234567", MmsPdu.from(p))
    }

    @Test fun ignoresOtherPushes() {
        assertFalse(MmsPdu.isNotification(pdu(0x8C, 0x86, 0x00)))
        assertEquals(null, MmsPdu.from(pdu(0x8C, 0x82, 0x98, "x", 0)))
    }
}
