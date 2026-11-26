package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BanksTest {
    @Test fun sendersOnThePhone() {
        assertEquals("pasargad", Banks.of("B.Pasargad")?.id)
        assertEquals("pasargad", Banks.of("5000114")?.id)
        assertEquals("tejarat", Banks.of("TejaratBank")?.id)
        assertEquals("mellat", Banks.of("Bank Mellat")?.id)
        assertEquals("parsian", Banks.of("PARSIANBANK")?.id)
        assertEquals("saman", Banks.of("+989999920000")?.id)
        assertEquals("blu", Banks.of("Blu")?.id)
        assertEquals("sepah", Banks.of("بانک سپه")?.id)
    }

    @Test fun longerNamesWinAndShortCodesAreExact() {
        assertEquals("edbi", Banks.of("بانک توسعه صادرات")?.id)
        assertEquals("saderat", Banks.of("BSI")?.id)
        assertNull(Banks.of("Bimeh Iran"))
        assertNull(Banks.of("9830009417"))
        assertNull(Banks.of(""))
    }

    @Test fun userNameFindsTheBank() {
        val names = mapOf("9830009417" to "ملت حقوق", "1234567" to "صندوق خانوادگی")
        val a = Banks.shown("9830009417 4321", names)
        assertEquals("mellat", a.bank?.id)
        assertEquals("ملت حقوق", a.name)
        assertEquals("4321", a.last4)
        val b = Banks.shown("1234567", names)
        assertNull(b.bank)
        assertEquals("صندوق خانوادگی", b.name)
        assertEquals("پاسارگاد", Banks.shown("B.Pasargad 1111", names).name)
        assertEquals("9999", Banks.shown("9999", emptyMap()).name)
    }

    @Test fun splitsAccount() {
        assertEquals("B.Pasargad" to "1234", Banks.split("B.Pasargad 1234"))
        assertEquals("Blu" to null, Banks.split("Blu"))
    }
}
