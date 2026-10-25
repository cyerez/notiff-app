package com.rez.notif.data

import android.content.Context
import com.rez.notif.i18n.I18n
import com.rez.notif.i18n.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The look of the whole app. Each style has light and dark versions. More styles come in phase 3. */
enum class AppStyle(val label: String) {
    GLASS("شیشه‌ای نئونی"),
    PEACH("کوهستان هلویی"),
    SEASONS("چهار فصل"),
    CYBER("سایبرپانک"),
    STEAM("استیم‌پانک"),
    RETRO("رترو پیکسلی"),
    MEADOW("دشت جیبلی"),
}

/** Which season the "four seasons" style shows. AUTO follows the Persian calendar (Farvardin = spring). */
enum class Season(val label: String) {
    AUTO("خودکار از تقویم"),
    SPRING("بهار"),
    SUMMER("تابستون"),
    AUTUMN("پاییز"),
    WINTER("زمستون"),
}

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
    val style: AppStyle = AppStyle.GLASS,
    val season: Season = Season.AUTO,
    /** Asked once for contacts access (to show names instead of numbers). */
    val contactsAsked: Boolean = false,
    /** Home sections, in the user's order, and the ones they hid. */
    val homeOrder: List<String> = emptyList(),
    val homeHidden: Set<String> = emptySet(),
    /** Phase 5 (Notif is the default SMS app): ad SMS never reach the phone's inbox; they stay only in "hidden ads". */
    val blockAdSms: Boolean = false,
    /** Phase 5: deleting in Notif also deletes the SMS from the phone (undo puts it back). */
    val phoneDelete: Boolean = true,
    val language: Lang = Lang.FA,
    val animations: Boolean = true,
    /** How strong the background picture is: 0 full, 1 soft, 2 very soft. */
    val backdropDim: Int = 1,
    /** Hide every new notification from the status bar right away; it lives on inside the app. */
    val hideAll: Boolean = true,
    /** Exceptions while hideAll is on. */
    val keepOtp: Boolean = true,
    val keepVip: Boolean = false,
    /** One quiet summary notification from this app instead of the hidden ones. */
    val summary: Boolean = true,
    /** When the app was last opened; the summary counts what came after it. */
    val lastSeen: Long = 0,
    /** Start of the last long absence (2+ hours), for the "while you were away" card; 0 = none. */
    val awaySince: Long = 0,
    /** Ask for fingerprint / phone PIN when opening the app. */
    val appLock: Boolean = false,
    val relock: RelockMode = RelockMode.APP_CLOSED,
    /** On opening the app, clear status-bar notifications except main categories and chosen people. */
    val clearOnOpen: Boolean = true,
    /** Run the smart cleanup once a day on opening the app. */
    val autoClean: Boolean = false,
    val cleanupRules: Set<String> = CleanupRule.entries.filter { it.defaultOn }.map { it.name }.toSet(),
    val lastAutoClean: Long = 0,
    /** Quiet hours (-1 = off): only "always show" rules and fresh codes reach the status bar. */
    val quietFrom: Int = -1,
    val quietTo: Int = -1,
    /** Hours of day for the summary notification; empty = live summary after every hidden notification. */
    val digestHours: Set<Int> = emptySet(),
    /** Bank alerts (toman): balance below [lowBalance], or one spend at least [bigSpend]. */
    val lowBalanceOn: Boolean = false,
    val lowBalance: Long = 1_000_000,
    val bigSpendOn: Boolean = false,
    val bigSpend: Long = 5_000_000,
    /** Remind bills and installments found in SMS, [billDaysBefore] days before the due date (0 = that morning). */
    val billReminders: Boolean = true,
    val billDaysBefore: Int = 3,
    /** Seconds a normal notification stays in the status bar before Notif tidies it away (0 = right away). Ads always go at once. */
    val holdSeconds: Int = 30,
    /** Smartwatch: while the phone is locked, notifications stay (so the watch shows them) until it is unlocked. */
    val watchMode: Boolean = true,
    /** Seconds the "copy code" box stays on top of the screen. */
    val codeSeconds: Int = 20,
    /** Show the code box over other apps (needs "appear on top"); otherwise only the notification. */
    val codeOverlay: Boolean = true,
    /** Messages the user said are not bills ("این قسط نیست"), by item id. */
    val ignoredBills: Set<String> = emptySet(),
    /** Hand-entered installments ticked as paid: "billId:yyyymm" (Persian month). */
    val paidBills: Set<String> = emptySet(),
)

/** When the fingerprint lock comes back after unlocking once. */
enum class RelockMode(val label: String) {
    APP_CLOSED("فقط وقتی اپ رو کامل ببندی"),
    SCREEN_OFF("وقتی صفحه گوشی خاموش بشه"),
    AFTER_30S("۳۰ ثانیه بعد از بیرون رفتن"),
}

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
    private val _state = MutableStateFlow(load().also { I18n.lang = it.language })
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    private fun load() = Settings(
        onboardingDone = sp.getBoolean("onboardingDone", false),
        firstScanDone = sp.getBoolean("firstScanDone", false),
        mainCategories = sp.getStringSet("mainCategories", emptySet())?.toSet() ?: emptySet(),
        hideAds = sp.getBoolean("hideAds", true),
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("themeMode", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        accent = runCatching { Accent.valueOf(sp.getString("accent", null)!!) }.getOrDefault(Accent.NEON_BLUE),
        style = runCatching { AppStyle.valueOf(sp.getString("style", null)!!) }.getOrDefault(AppStyle.GLASS),
        blockAdSms = sp.getBoolean("blockAdSms", false),
        phoneDelete = sp.getBoolean("phoneDelete", true),
        contactsAsked = sp.getBoolean("contactsAsked", false),
        homeOrder = sp.getString("homeOrder", "").orEmpty().split(",").filter { it.isNotBlank() },
        homeHidden = sp.getStringSet("homeHidden", emptySet())?.toSet() ?: emptySet(),
        season = runCatching { Season.valueOf(sp.getString("season", null)!!) }.getOrDefault(Season.AUTO),
        language = runCatching { Lang.valueOf(sp.getString("language", null)!!) }.getOrDefault(Lang.FA),
        animations = sp.getBoolean("animations", true),
        backdropDim = sp.getInt("backdropDim", 1),
        hideAll = sp.getBoolean("hideAll", true),
        keepOtp = sp.getBoolean("keepOtp", true),
        keepVip = sp.getBoolean("keepVip", false),
        summary = sp.getBoolean("summary", true),
        lastSeen = sp.getLong("lastSeen", 0),
        awaySince = sp.getLong("awaySince", 0),
        appLock = sp.getBoolean("appLock", false),
        relock = runCatching { RelockMode.valueOf(sp.getString("relock", null)!!) }.getOrDefault(RelockMode.APP_CLOSED),
        clearOnOpen = sp.getBoolean("clearOnOpen", true),
        autoClean = sp.getBoolean("autoClean", false),
        cleanupRules = sp.getStringSet("cleanupRules", null)?.toSet() ?: Settings().cleanupRules,
        lastAutoClean = sp.getLong("lastAutoClean", 0),
        quietFrom = sp.getInt("quietFrom", -1),
        quietTo = sp.getInt("quietTo", -1),
        lowBalanceOn = sp.getBoolean("lowBalanceOn", false),
        lowBalance = sp.getLong("lowBalance", 1_000_000),
        bigSpendOn = sp.getBoolean("bigSpendOn", false),
        bigSpend = sp.getLong("bigSpend", 5_000_000),
        billReminders = sp.getBoolean("billReminders", true),
        billDaysBefore = sp.getInt("billDaysBefore2", 3),
        holdSeconds = sp.getInt("holdSeconds", 30),
        watchMode = sp.getBoolean("watchMode", true),
        codeSeconds = sp.getInt("codeSeconds", 20),
        codeOverlay = sp.getBoolean("codeOverlay", true),
        ignoredBills = sp.getStringSet("ignoredBills", emptySet())?.toSet() ?: emptySet(),
        paidBills = sp.getStringSet("paidBills", emptySet())?.toSet() ?: emptySet(),
        digestHours = sp.getStringSet("digestHours", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
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
            .putString("style", s.style.name)
            .putString("season", s.season.name)
            .putBoolean("contactsAsked", s.contactsAsked)
            .putString("homeOrder", s.homeOrder.joinToString(","))
            .putStringSet("homeHidden", s.homeHidden)
            .putBoolean("blockAdSms", s.blockAdSms)
            .putBoolean("phoneDelete", s.phoneDelete)
            .putString("language", s.language.name)
            .putBoolean("animations", s.animations)
            .putInt("backdropDim", s.backdropDim)
            .putBoolean("hideAll", s.hideAll)
            .putBoolean("keepOtp", s.keepOtp)
            .putBoolean("keepVip", s.keepVip)
            .putBoolean("summary", s.summary)
            .putLong("lastSeen", s.lastSeen)
            .putLong("awaySince", s.awaySince)
            .putBoolean("appLock", s.appLock)
            .putString("relock", s.relock.name)
            .putBoolean("clearOnOpen", s.clearOnOpen)
            .putBoolean("autoClean", s.autoClean)
            .putStringSet("cleanupRules", s.cleanupRules)
            .putLong("lastAutoClean", s.lastAutoClean)
            .putInt("quietFrom", s.quietFrom)
            .putInt("quietTo", s.quietTo)
            .putBoolean("lowBalanceOn", s.lowBalanceOn)
            .putLong("lowBalance", s.lowBalance)
            .putBoolean("bigSpendOn", s.bigSpendOn)
            .putLong("bigSpend", s.bigSpend)
            .putBoolean("billReminders", s.billReminders)
            .putInt("billDaysBefore2", s.billDaysBefore)
            .putInt("holdSeconds", s.holdSeconds)
            .putBoolean("watchMode", s.watchMode)
            .putInt("codeSeconds", s.codeSeconds)
            .putBoolean("codeOverlay", s.codeOverlay)
            .putStringSet("ignoredBills", s.ignoredBills)
            .putStringSet("paidBills", s.paidBills)
            .putStringSet("digestHours", s.digestHours.map { it.toString() }.toSet())
            .apply()
        I18n.lang = s.language
        _state.value = s
    }
}
