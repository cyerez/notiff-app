package com.rez.notif.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCryptoTest {
    private val data = "SQLite format 3 سلام".toByteArray() + ByteArray(5000) { it.toByte() }

    @Test fun roundTrip() {
        val locked = BackupCrypto.encrypt(data, "رمز۱۲۳".toCharArray())
        assertFalse(String(locked).contains("SQLite"))
        assertArrayEquals(data, BackupCrypto.decrypt(locked, "رمز۱۲۳".toCharArray()))
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun wrongPasswordFails() {
        BackupCrypto.decrypt(BackupCrypto.encrypt(data, "a".toCharArray()), "b".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun tamperedFileFails() {
        val locked = BackupCrypto.encrypt(data, "a".toCharArray())
        locked[locked.size - 20] = (locked[locked.size - 20] + 1).toByte()
        BackupCrypto.decrypt(locked, "a".toCharArray())
    }

    @Test(expected = BackupCrypto.NotABackup::class)
    fun randomFileRejected() {
        BackupCrypto.decrypt(ByteArray(100), "a".toCharArray())
    }
}
