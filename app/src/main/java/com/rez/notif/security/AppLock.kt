package com.rez.notif.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fingerprint / face / phone PIN lock for the app. Uses Android's own prompt, so no extra library
 * and nothing is stored by us: the phone checks the finger and only tells us "ok".
 */
object AppLock {
    /** After leaving the app for this long, it locks again. */
    private const val RELOCK_AFTER_MS = 30_000L

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var leftAt = 0L

    fun onLeave() { leftAt = System.currentTimeMillis() }

    fun onReturn() {
        if (_unlocked.value && System.currentTimeMillis() - leftAt > RELOCK_AFTER_MS) _unlocked.value = false
    }

    /** Shows the system prompt. Fingerprint, face, or the phone's own PIN/pattern all work. */
    fun authenticate(activity: Activity, title: String = "باز کردن نوتیف", onResult: (Boolean) -> Unit = {}) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("اثر انگشت یا رمز گوشی")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _unlocked.value = true
                    leftAt = System.currentTimeMillis()
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
    }
}
