package com.rez.notif.security

import com.rez.notif.i18n.t
import android.app.Activity
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import com.rez.notif.data.RelockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fingerprint / face / phone PIN lock for the app. Uses Android's own prompt, so no extra library
 * and nothing is stored by us: the phone checks the finger and only tells us "ok".
 */
object AppLock {
    /** With [RelockMode.AFTER_30S]: after leaving the app for this long, it locks again. */
    private const val RELOCK_AFTER_MS = 30_000L

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var leftAt = 0L

    fun onLeave() { leftAt = System.currentTimeMillis() }

    fun onReturn(mode: RelockMode) {
        if (mode == RelockMode.AFTER_30S && _unlocked.value && System.currentTimeMillis() - leftAt > RELOCK_AFTER_MS) _unlocked.value = false
    }

    /** Screen turned off, or the app was closed from recent apps. */
    fun lockNow() { _unlocked.value = false }

    /** Shows the system prompt. Fingerprint, face, or the phone's own PIN/pattern all work. */
    fun authenticate(activity: Activity, title: String = t("باز کردن نوتیف"), onResult: (Boolean) -> Unit = {}) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(t("اثر انگشت یا رمز گوشی"))
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
