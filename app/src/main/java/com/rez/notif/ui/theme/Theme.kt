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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.rez.notif.R
import com.rez.notif.data.Accent
import com.rez.notif.data.Settings
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
)

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
    val glass = glassColors(dark, settings.accent, settings.animations)
    val scheme = if (dark) {
        darkColorScheme(
            primary = glass.accent, secondary = glass.accent2, background = glass.bgTop,
            surface = Color(0xFF1B2133), onSurface = glass.text, onBackground = glass.text,
            surfaceContainerLow = Color(0xFF1B2133), surfaceContainer = Color(0xFF20273B),
        )
    } else {
        lightColorScheme(
            primary = glass.accent, secondary = glass.accent2, background = glass.bgTop,
            surface = Color(0xFFF8FAFC), onSurface = glass.text, onBackground = glass.text,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography()) {
        CompositionLocalProvider(
            LocalGlass provides glass,
            // The app is Persian: always right-to-left, whatever the phone language is.
            LocalLayoutDirection provides LayoutDirection.Rtl,
            content = content,
        )
    }
}
