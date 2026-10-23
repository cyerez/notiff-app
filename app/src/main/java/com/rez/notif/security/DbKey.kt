package com.rez.notif.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database password. A random 256-bit key, made once on this phone. It is stored only in
 * wrapped (encrypted) form; the wrapping key lives in the phone's hardware keystore and can never leave it.
 */
object DbKey {
    private const val ALIAS = "notif_db_wrap"
    private const val PREFS = "db_key"

    /** Returns the key as hex text, or null if a stored key exists but can no longer be unwrapped. */
    fun get(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = sp.getString("wrapped", null)
        if (stored != null) {
            return runCatching {
                val all = Base64.decode(stored, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, wrapKey(create = false), GCMParameterSpec(128, all.copyOfRange(0, 12)))
                String(cipher.doFinal(all, 12, all.size - 12), Charsets.US_ASCII)
            }.getOrNull()
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = raw.joinToString("") { "%02x".format(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey(create = true))
        val out = cipher.iv + cipher.doFinal(hex.toByteArray(Charsets.US_ASCII))
        // commit (not apply): the key must be on disk before any data is encrypted with it.
        sp.edit().putString("wrapped", Base64.encodeToString(out, Base64.NO_WRAP)).commit()
        return hex
    }

    private fun wrapKey(create: Boolean): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "wrapping key missing" }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}
