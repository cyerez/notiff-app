package com.rez.notif.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTest {
    @Test fun codecRoundTrip() {
        val e = WalletEntry(title = "بانک", kind = WalletKind.BANK, username = "6037:99", secret = "p:a:ss\n12", url = "", note = "یادداشت ۱۲:۳۰")
        val back = WalletCodec.decode(e.copy(username = "", secret = "", note = ""), WalletCodec.encode(e))
        assertEquals(e, back)
    }

    @Test fun generatorHasEveryGroup() {
        repeat(50) {
            val p = PasswordGen.generate(12)
            assertEquals(12, p.length)
            assertTrue(p.any { it.isLowerCase() } && p.any { it.isUpperCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
        }
    }

    @Test fun generatorWithoutSymbols() {
        val p = PasswordGen.generate(20, symbols = false)
        assertTrue(p.all { it.isLetterOrDigit() })
    }
}
