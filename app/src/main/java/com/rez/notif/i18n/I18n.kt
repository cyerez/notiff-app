package com.rez.notif.i18n

/** App languages. Persian is the main one; English is a translation of it. */
enum class Lang(val label: String) { FA("فارسی"), EN("English") }

/**
 * Tiny translation layer: the Persian text is the key, so code stays readable and any string can be
 * translated anywhere (also outside Compose). Missing English entries fall back to Persian.
 * Pure Kotlin, unit-tested: every wrapped string must have an English entry (see I18nTest).
 */
object I18n {
    @Volatile var lang: Lang = Lang.FA

    val isFa: Boolean get() = lang == Lang.FA

    fun t(fa: String): String = if (lang == Lang.FA) fa else En.map[fa] ?: fa
}

fun t(fa: String): String = I18n.t(fa)
