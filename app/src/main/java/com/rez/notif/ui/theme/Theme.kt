package com.rez.notif.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.rez.notif.R
import com.rez.notif.data.Accent
import com.rez.notif.data.AppStyle
import com.rez.notif.data.Settings
import com.rez.notif.i18n.Lang
import com.rez.notif.data.ThemeMode

/** All colors of the glass look. One instance for light, one for dark, tinted by the chosen accent. */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    val accent: Color,
    val accent2: Color,
    /** Page background gradient, top to bottom. */
    val bgTop: Color,
    val bgBottom: Color,
    /** Soft colored light spots behind the glass. */
    val blobs: List<Color>,
    /** Glass panel fill, top and bottom of the panel. */
    val glassTop: Color,
    val glassBottom: Color,
    /** Thin bright edge of glass panels. */
    val edge: Color,
    val text: Color,
    val textDim: Color,
    val shadow: Color,
    val animations: Boolean,
    val style: AppStyle = AppStyle.GLASS,
    /** How strong neon glows are (1 = glass, lower = calm flat styles). */
    val glow: Float = 1f,
    /** How much bright category colors are softened toward [paper] (0 = not at all). */
    val pastel: Float = 0f,
    val paper: Color = Color.White,
    /** Vector landscape drawn behind everything, or null for the glowing color spots. */
    val scene: ScenePalette? = null,
)

/** Softens a bright color for calm styles. */
fun GlassColors.soft(c: Color): Color = if (pastel > 0f) lerp(c, paper, pastel) else c

fun themeColors(dark: Boolean, settings: Settings): GlassColors = when (settings.style) {
    AppStyle.GLASS -> glassColors(dark, settings.accent, settings.animations)
    AppStyle.PEACH -> peachColors(dark, settings.animations)
    AppStyle.SEASONS -> if (dark) peachColors(true, settings.animations).copy(style = AppStyle.SEASONS) else seasonColors(settings.animations)
}

/** 0 spring, 1 summer, 2 autumn, 3 winter, by the Persian calendar. */
fun persianSeason(): Int {
    val cal = android.icu.util.Calendar.getInstance(android.icu.util.ULocale("fa_IR@calendar=persian"))
    return cal.get(android.icu.util.Calendar.MONTH) / 3
}

private fun seasonColors(animations: Boolean): GlassColors {
    val (scene, accent, accent2) = when (persianSeason()) {
        0 -> Triple(Spring, Color(0xFF3FA06D), Color(0xFFF08DB0))
        1 -> Triple(Summer, Color(0xFFF08A24), Color(0xFF2FB3C9))
        2 -> Triple(Autumn, Color(0xFFD2603A), Color(0xFFE3A23C))
        else -> Triple(Winter, Color(0xFF5B84C4), Color(0xFF8FB3E0))
    }
    return peachColors(false, animations).copy(
        accent = accent, accent2 = accent2, scene = scene, style = AppStyle.SEASONS,
        bgTop = scene.skyTop, bgBottom = scene.groundFront,
        text = Color(0xFF3B3940), textDim = Color(0xFF7A7682),
        glassTop = Color(0xFFFFFDFB).copy(alpha = 0.88f), glassBottom = Color(0xFFFBF8F5).copy(alpha = 0.8f),
    )
}

private fun peachColors(dark: Boolean, animations: Boolean): GlassColors = if (dark) {
    GlassColors(
        isDark = true, accent = Color(0xFFF2A07E), accent2 = Color(0xFFC98BB0),
        bgTop = PeachNight.skyTop, bgBottom = PeachNight.groundFront, blobs = emptyList(),
        glassTop = Color(0xFF2E2640).copy(alpha = 0.84f), glassBottom = Color(0xFF2A2236).copy(alpha = 0.76f),
        edge = Color.White.copy(alpha = 0.16f),
        text = Color(0xFFF8ECE4), textDim = Color(0xFFC2AAB5),
        shadow = Color.Black.copy(alpha = 0.4f),
        animations = animations, style = AppStyle.PEACH,
        glow = 0.45f, pastel = 0.15f, paper = Color(0xFFF3D9CF), scene = PeachNight,
    )
} else {
    GlassColors(
        isDark = false, accent = Color(0xFFEE8466), accent2 = Color(0xFFF5B38C),
        bgTop = PeachDay.skyTop, bgBottom = PeachDay.groundFront, blobs = emptyList(),
        glassTop = Color(0xFFFFF8F3).copy(alpha = 0.88f), glassBottom = Color(0xFFFFF1E8).copy(alpha = 0.8f),
        edge = Color.White.copy(alpha = 0.9f),
        text = Color(0xFF4A3634), textDim = Color(0xFF8E716B),
        shadow = Color(0xFF7A4B3E).copy(alpha = 0.16f),
        animations = animations, style = AppStyle.PEACH,
        glow = 0.35f, pastel = 0.3f, paper = Color(0xFFFFF4EC), scene = PeachDay,
    )
}

val LocalGlass = staticCompositionLocalOf { glassColors(true, Accent.NEON_BLUE, true) }

fun glassColors(dark: Boolean, accent: Accent, animations: Boolean): GlassColors {
    val a = Color(accent.color)
    val b = Color(accent.second)
    return if (dark) {
        GlassColors(
            isDark = true, accent = a, accent2 = b,
            bgTop = Color(0xFF0E1220), bgBottom = Color(0xFF1A2033),
            blobs = listOf(a.copy(alpha = 0.55f), b.copy(alpha = 0.45f), Color(0xFFFF8A3D).copy(alpha = 0.25f)),
            glassTop = Color.White.copy(alpha = 0.14f), glassBottom = Color.White.copy(alpha = 0.05f),
            edge = Color.White.copy(alpha = 0.22f),
            text = Color(0xFFF1F5F9), textDim = Color(0xFF94A3B8),
            shadow = Color.Black.copy(alpha = 0.5f),
            animations = animations,
        )
    } else {
        GlassColors(
            isDark = false, accent = a, accent2 = b,
            bgTop = Color(0xFFF3EEE9), bgBottom = Color(0xFFDDE6F3),
            blobs = listOf(a.copy(alpha = 0.35f), b.copy(alpha = 0.30f), Color(0xFFF9C6D3).copy(alpha = 0.6f)),
            glassTop = Color.White.copy(alpha = 0.72f), glassBottom = Color.White.copy(alpha = 0.42f),
            edge = Color.White.copy(alpha = 0.95f),
            text = Color(0xFF0F172A), textDim = Color(0xFF64748B),
            shadow = Color(0xFF1E293B).copy(alpha = 0.18f),
            animations = animations,
        )
    }
}

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_black, FontWeight.Black),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazirmatn)
    return Typography(
        displayLarge = base.displayLarge.v(),
        displayMedium = base.displayMedium.v(),
        displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v().copy(fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.v().copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.v().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.v().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.v().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.v().copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.v().copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.v().copy(lineHeight = 22.sp),
        bodySmall = base.bodySmall.v(),
        labelLarge = base.labelLarge.v().copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.v(),
        labelSmall = base.labelSmall.v(),
    )
}

@Composable
fun NotifTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val glass = themeColors(dark, settings)
    val scheme = if (dark) {
        darkColorScheme(
            primary = glass.accent, secondary = glass.accent2, background = glass.bgTop,
            surface = if (glass.scene != null) Color(0xFF2E2640) else Color(0xFF1B2133), onSurface = glass.text, onBackground = glass.text,
            surfaceContainerLow = if (glass.scene != null) Color(0xFF2E2640) else Color(0xFF1B2133),
            surfaceContainer = if (glass.scene != null) Color(0xFF362D4A) else Color(0xFF20273B),
            surfaceContainerHigh = if (glass.scene != null) Color(0xFF3B3150) else Color(0xFF262E45),
        )
    } else {
        lightColorScheme(
            primary = glass.accent, secondary = glass.accent2, background = glass.bgTop,
            surface = if (glass.scene != null) Color(0xFFFFF6F0) else Color(0xFFF8FAFC), onSurface = glass.text, onBackground = glass.text,
            surfaceContainerHigh = if (glass.scene != null) Color(0xFFFFEFE6) else Color(0xFFF1F5F9),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography()) {
        CompositionLocalProvider(
            LocalGlass provides glass,
            // Follows the app language (not the phone's): Persian right-to-left, English left-to-right.
            LocalLayoutDirection provides if (settings.language == Lang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content,
        )
    }
}
