package com.rez.notif.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Locks a backup file with a password. Pure Kotlin (no Android), unit-tested.
 *
 * File layout: "NOTIFBK1" | salt (16) | iv (12) | AES-256-GCM ciphertext.
 * The key comes from the password with PBKDF2-HMAC-SHA256. Without the password the file is unreadable,
 * and GCM makes any change to the file (or a wrong password) fail loudly.
 */
object BackupCrypto {
    private val MAGIC = "NOTIFBK1".toByteArray()
    private const val SALT = 16
    private const val IV = 12
    private const val ITERATIONS = 120_000

    class WrongPassword : Exception("wrong password or damaged file")
    class NotABackup : Exception("not a Notif backup file")

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT).also(rnd::nextBytes)
        val iv = ByteArray(IV).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < MAGIC.size + SALT + IV + 16 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackup()
        var p = MAGIC.size
        val salt = data.copyOfRange(p, p + SALT).also { p += SALT }
        val iv = data.copyOfRange(p, p + IV).also { p += IV }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(data, p, data.size - p)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
