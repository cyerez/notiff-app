package com.rez.notif.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent colors of the glass theme (taken from the sample designs). */
enum class Accent(val label: String, val color: Long, val second: Long) {
    NEON_BLUE("آبی نئونی", 0xFF3D7BFF, 0xFF5CF2FF),
    AQUA("فیروزه‌ای", 0xFF14B8A6, 0xFF5CF2FF),
    PURPLE("بنفش", 0xFF8B5CF6, 0xFFF472B6),
    SUNSET("نارنجی", 0xFFFF8A3D, 0xFFFACC15),
    ROSE("صورتی", 0xFFF472B6, 0xFF8B5CF6),
}

data class Settings(
    val onboardingDone: Boolean = false,
    val firstScanDone: Boolean = false,
    val mainCategories: Set<String> = emptySet(),
    val hideAds: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.NEON_BLUE,
    val animations: Boolean = true,
    /** Hide every new notification from the status bar right away; it lives on inside the app. */
    val hideAll: Boolean = true,
    /** Exceptions while hideAll is on. */
    val keepOtp: Boolean = true,
    val keepVip: Boolean = false,
    /** One quiet summary notification from this app instead of the hidden ones. */
    val summary: Boolean = true,
    /** When the app was last opened; the summary counts what came after it. */
    val lastSeen: Long = 0,
    /** Ask for fingerprint / phone PIN when opening the app. */
    val appLock: Boolean = false,
    /** On opening the app, clear status-bar notifications except main categories and chosen people. */
    val clearOnOpen: Boolean = true,
    /** Run the smart cleanup once a day on opening the app. */
    val autoClean: Boolean = false,
    val cleanupRules: Set<String> = CleanupRule.entries.filter { it.defaultOn }.map { it.name }.toSet(),
    val lastAutoClean: Long = 0,
)

/** Smart cleanup rules: what counts as clutter, and from what age. Chosen people, starred and valuable are never touched. */
enum class CleanupRule(val label: String, val category: String, val days: Int, val defaultOn: Boolean) {
    MISSED("میس‌کال‌های قدیمی‌تر از ۳ روز", "missed", 3, true),
    OTP("کدهای تأیید قدیمی‌تر از ۱ روز", "otp", 1, true),
    ADS("تبلیغ‌های قدیمی‌تر از ۷ روز", "ads", 7, true),
    OPERATOR("پیام‌های اپراتور قدیمی‌تر از ۳۰ روز", "operator", 30, false),
    SYSTEM("نوتیف‌های سیستم قدیمی‌تر از ۷ روز", "system", 7, true),
    APPS("نوتیف بقیه اپ‌ها قدیمی‌تر از ۳۰ روز", "apps", 30, false),
}

/** Small settings store on top of SharedPreferences, exposed as a flow for the UI. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    private fun load() = Settings(
        onboardingDone = sp.getBoolean("onboardingDone", false),
        firstScanDone = sp.getBoolean("firstScanDone", false),
        mainCategories = sp.getStringSet("mainCategories", emptySet())?.toSet() ?: emptySet(),
        hideAds = sp.getBoolean("hideAds", true),
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("themeMode", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        accent = runCatching { Accent.valueOf(sp.getString("accent", null)!!) }.getOrDefault(Accent.NEON_BLUE),
        animations = sp.getBoolean("animations", true),
        hideAll = sp.getBoolean("hideAll", true),
        keepOtp = sp.getBoolean("keepOtp", true),
        keepVip = sp.getBoolean("keepVip", false),
        summary = sp.getBoolean("summary", true),
        lastSeen = sp.getLong("lastSeen", 0),
        appLock = sp.getBoolean("appLock", false),
        clearOnOpen = sp.getBoolean("clearOnOpen", true),
        autoClean = sp.getBoolean("autoClean", false),
        cleanupRules = sp.getStringSet("cleanupRules", null)?.toSet() ?: Settings().cleanupRules,
        lastAutoClean = sp.getLong("lastAutoClean", 0),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(_state.value)
        sp.edit()
            .putBoolean("onboardingDone", s.onboardingDone)
            .putBoolean("firstScanDone", s.firstScanDone)
            .putStringSet("mainCategories", s.mainCategories)
            .putBoolean("hideAds", s.hideAds)
            .putString("themeMode", s.themeMode.name)
            .putString("accent", s.accent.name)
            .putBoolean("animations", s.animations)
            .putBoolean("hideAll", s.hideAll)
            .putBoolean("keepOtp", s.keepOtp)
            .putBoolean("keepVip", s.keepVip)
            .putBoolean("summary", s.summary)
            .putLong("lastSeen", s.lastSeen)
            .putBoolean("appLock", s.appLock)
            .putBoolean("clearOnOpen", s.clearOnOpen)
            .putBoolean("autoClean", s.autoClean)
            .putStringSet("cleanupRules", s.cleanupRules)
            .putLong("lastAutoClean", s.lastAutoClean)
            .apply()
        _state.value = s
    }
}
