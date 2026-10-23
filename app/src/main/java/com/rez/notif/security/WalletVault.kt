package com.rez.notif.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.rez.notif.i18n.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The password wallet's own lock and key. On top of the encrypted database, each secret is encrypted
 * with a hardware key that only works for 60 seconds after a fingerprint (or phone PIN). So even
 * with the app open, nobody can read the wallet without the finger.
 */
object WalletVault {
    private const val ALIAS = "notif_wallet_key"
    private const val AUTH_SECONDS = 60
    private const val CLIP_LABEL = "notif-wallet"

    class Locked : Exception("wallet locked")

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    @Volatile private var unlockedAt = 0L

    /** True while the key can still be used without asking again. */
    fun isOpen(): Boolean = _unlocked.value && System.currentTimeMillis() - unlockedAt < (AUTH_SECONDS - 3) * 1000L

    fun lock() { _unlocked.value = false }

    /** The hardware key needs a screen lock (PIN, pattern or password) on the phone. */
    fun deviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Asks for a strong fingerprint or the phone PIN. Face unlock is not enough for the wallet. */
    fun unlock(activity: Activity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(t("باز کردن کیف پسورد"))
            .setSubtitle(t("اثر انگشت یا رمز گوشی"))
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val ok = runCatching { key() }.isSuccess
                    if (ok) {
                        unlockedAt = System.currentTimeMillis()
                        _unlocked.value = true
                    }
                    onResult(ok)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }

    fun encrypt(plain: String): ByteArray = withAuth {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(data: ByteArray): String = withAuth {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }

    private inline fun <T> withAuth(block: () -> T): T {
        if (!isOpen()) { lock(); throw Locked() }
        return try {
            block()
        } catch (e: UserNotAuthenticatedException) {
            lock()
            throw Locked()
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(AUTH_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                // Adding a new fingerprint must not destroy the wallet.
                .setInvalidatedByBiometricEnrollment(false)
                .build(),
        )
        return gen.generateKey()
    }

    private val main = Handler(Looper.getMainLooper())

    /** Copies a secret (hidden from clipboard previews) and wipes it from the clipboard after 45 seconds. */
    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        main.postDelayed({
            val stillOurs = runCatching { cm.primaryClipDescription?.label == CLIP_LABEL }.getOrDefault(true)
            if (stillOurs) runCatching { cm.clearPrimaryClip() }
        }, 45_000)
    }
}
