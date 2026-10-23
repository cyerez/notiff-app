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
)

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
            .apply()
        _state.value = s
    }
}
