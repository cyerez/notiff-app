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
import com.rez.notif.data.Season
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
    /** How panels are cut and outlined (rounded glass, cyber cut corners, brass, pixel, cartoon). */
    val frame: Frame = Frame.ROUND,
    /** What is drawn behind everything when there is no [scene]. */
    val backdrop: Backdrop = Backdrop.BLOBS,
    /** Solid color for dialogs and sheets. */
    val surface: Color? = null,
    /** Money in, money out (soft, not alarming), and moves between the user's own accounts. */
    val moneyIn: Color = Color(0xFF3FA56B),
    val moneyOut: Color = Color(0xFFE0604E),
    val moneyOwn: Color = Color(0xFF9AA0A6),
)

enum class Frame { ROUND, CUT, BRASS, PIXEL, CARTOON, MORPH }
enum class Backdrop { BLOBS, CYBER, STEAM, PIXEL, MEADOW }

/** Softens a bright color for calm styles. */
fun GlassColors.soft(c: Color): Color = if (pastel > 0f) lerp(c, paper, pastel) else c

fun themeColors(dark: Boolean, settings: Settings): GlassColors = when (settings.style) {
    AppStyle.PISTACHIO -> pistachioColors(dark, settings.animations)
    AppStyle.GLASS -> glassColors(dark, settings.accent, settings.animations)
    AppStyle.PEACH -> peachColors(dark, settings.animations)
    AppStyle.SEASONS -> seasonColors(dark, settings.season, settings.animations)
    AppStyle.CYBER -> cyberColors(dark, settings.animations)
    AppStyle.STEAM -> steamColors(dark, settings.animations)
    AppStyle.RETRO -> retroColors(dark, settings.animations)
    AppStyle.MEADOW -> meadowColors(dark, settings.animations)
    // Always the night look of the reference design, also when the phone is in light mode.
    AppStyle.MIDNIGHT -> midnightColors(true, settings.animations)
}

/** Test style: deep blue night with soft blue light and see-through glass (dark), or pale blue day (light). */
private fun midnightColors(dark: Boolean, animations: Boolean) = if (dark) GlassColors(
    isDark = true, accent = Color(0xFF4C8DFF), accent2 = Color(0xFF6FD8FF),
    bgTop = Color(0xFF0E1A4A), bgBottom = Color(0xFF050914),
    blobs = listOf(Color(0xFF2F6BFF).copy(alpha = 0.6f), Color(0xFF1E3A8A).copy(alpha = 0.65f), Color(0xFF4C6FFF).copy(alpha = 0.4f)),
    glassTop = Color(0xFF22336E).copy(alpha = 0.55f), glassBottom = Color(0xFF0E1636).copy(alpha = 0.7f),
    edge = Color.White.copy(alpha = 0.38f),
    text = Color(0xFFF2F5FF), textDim = Color(0xFF9FB0D9),
    shadow = Color.Black.copy(alpha = 0.35f),
    animations = animations, style = AppStyle.MIDNIGHT,
    glow = 0.25f, surface = Color(0xFF16224D), frame = Frame.MORPH,
    moneyIn = Color(0xFF5BE49B), moneyOut = Color(0xFFFF7A6B), moneyOwn = Color(0xFF9FB0D9),
) else GlassColors(
    isDark = false, accent = Color(0xFF2F6BFF), accent2 = Color(0xFF1FA2D6),
    bgTop = Color(0xFFE6ECFF), bgBottom = Color(0xFFD3DDFB),
    blobs = listOf(Color(0xFF8FB0FF).copy(alpha = 0.5f), Color(0xFFB9CBFF).copy(alpha = 0.5f), Color(0xFF6E95FF).copy(alpha = 0.3f)),
    glassTop = Color.White.copy(alpha = 0.82f), glassBottom = Color.White.copy(alpha = 0.7f),
    edge = Color.White.copy(alpha = 0.9f),
    text = Color(0xFF0E1A4A), textDim = Color(0xFF5B6A93),
    shadow = Color(0xFF1E3A8A).copy(alpha = 0.12f),
    animations = animations, style = AppStyle.MIDNIGHT,
    glow = 0.15f, surface = Color(0xFFF4F7FF),
    moneyIn = Color(0xFF1E9E5A), moneyOut = Color(0xFFE0504A), moneyOwn = Color(0xFF7A88AD),
)

/** "شاخ گاو" default: calm and flat. Pistachio main color, saffron for buttons and the daily joke, pomegranate for spending. */
private fun pistachioColors(dark: Boolean, animations: Boolean) = if (!dark) GlassColors(
    isDark = false, accent = Color(0xFF7FAE5A), accent2 = Color(0xFFF2A93B),
    bgTop = Color(0xFFFFF8EE), bgBottom = Color(0xFFFFF8EE), blobs = emptyList(),
    glassTop = Color(0xFFFFFFFF), glassBottom = Color(0xFFFFFFFF),
    edge = Color(0xFF2D2236).copy(alpha = 0.07f),
    text = Color(0xFF2D2236), textDim = Color(0xFF7D7186),
    shadow = Color(0xFF2D2236).copy(alpha = 0.08f),
    animations = animations, style = AppStyle.PISTACHIO,
    glow = 0f, surface = Color(0xFFFFFFFF),
    moneyIn = Color(0xFF4E9A3A), moneyOut = Color(0xFFE0604E), moneyOwn = Color(0xFF9AA0A6),
) else GlassColors(
    isDark = true, accent = Color(0xFF94C470), accent2 = Color(0xFFF2A93B),
    bgTop = Color(0xFF1E1A24), bgBottom = Color(0xFF1E1A24), blobs = emptyList(),
    glassTop = Color(0xFF2B2533), glassBottom = Color(0xFF2B2533),
    edge = Color.White.copy(alpha = 0.06f),
    text = Color(0xFFF3EDF5), textDim = Color(0xFFA99FB2),
    shadow = Color.Black.copy(alpha = 0.25f),
    animations = animations, style = AppStyle.PISTACHIO,
    glow = 0f, surface = Color(0xFF2B2533),
    moneyIn = Color(0xFF8BC77A), moneyOut = Color(0xFFEE7E6E), moneyOwn = Color(0xFF9AA0A6),
)

/** Cyberpunk HUD: red and cyan lines, cut corners. Dark = red-black night screen, light = pale daylight HUD. */
private fun cyberColors(dark: Boolean, animations: Boolean) = if (!dark) GlassColors(
    isDark = false, accent = Color(0xFFD7263D), accent2 = Color(0xFF00838F),
    bgTop = Color(0xFFF3ECEC), bgBottom = Color(0xFFDCE6E8), blobs = emptyList(),
    glassTop = Color(0xFFFFFFFF).copy(alpha = 0.94f), glassBottom = Color(0xFFF4F7F8).copy(alpha = 0.94f),
    edge = Color(0xFFD7263D).copy(alpha = 0.7f),
    text = Color(0xFF1B1F22), textDim = Color(0xFF7A4B50),
    shadow = Color.Transparent,
    animations = animations, style = AppStyle.CYBER,
    glow = 0.5f, frame = Frame.CUT, backdrop = Backdrop.CYBER, surface = Color(0xFFFFFFFF),
) else GlassColors(
    isDark = true, accent = Color(0xFFFF5C5C), accent2 = Color(0xFF5CE1E6),
    bgTop = Color(0xFF2A0F14), bgBottom = Color(0xFF0B1418), blobs = emptyList(),
    glassTop = Color(0xFF1A2226).copy(alpha = 0.92f), glassBottom = Color(0xFF141B1F).copy(alpha = 0.92f),
    edge = Color(0xFFFF5C5C).copy(alpha = 0.75f),
    text = Color(0xFFF2E6E6), textDim = Color(0xFFB07A7A),
    shadow = Color.Transparent,
    animations = animations, style = AppStyle.CYBER,
    glow = 0.8f, frame = Frame.CUT, backdrop = Backdrop.CYBER, surface = Color(0xFF1A2226),
)

/** Steampunk: dark leather and brass outside, parchment cards with brown ink. */
private fun steamColors(dark: Boolean, animations: Boolean) = if (dark) {
    GlassColors(
        isDark = true, accent = Color(0xFFD4A24C), accent2 = Color(0xFFB8452F),
        bgTop = Color(0xFF2B1D14), bgBottom = Color(0xFF120C08), blobs = emptyList(),
        glassTop = Color(0xFF3A2A1E).copy(alpha = 0.95f), glassBottom = Color(0xFF2E2118).copy(alpha = 0.95f),
        edge = Color(0xFFC9963F),
        text = Color(0xFFF3E3C3), textDim = Color(0xFFC2A57A),
        shadow = Color.Black.copy(alpha = 0.5f),
        animations = animations, style = AppStyle.STEAM,
        glow = 0.35f, pastel = 0.2f, paper = Color(0xFFE9D5AE), frame = Frame.BRASS, backdrop = Backdrop.STEAM,
        surface = Color(0xFF3A2A1E),
    )
} else {
    GlassColors(
        isDark = false, accent = Color(0xFFA0522D), accent2 = Color(0xFFB8862B),
        bgTop = Color(0xFFF0DFBC), bgBottom = Color(0xFFCFAE7A), blobs = emptyList(),
        glassTop = Color(0xFFF6EBD3).copy(alpha = 0.97f), glassBottom = Color(0xFFEBDCBC).copy(alpha = 0.97f),
        edge = Color(0xFFB8862B),
        text = Color(0xFF3B2416), textDim = Color(0xFF7A5A3E),
        shadow = Color.Black.copy(alpha = 0.45f),
        animations = animations, style = AppStyle.STEAM,
        glow = 0.25f, pastel = 0.35f, paper = Color(0xFFF6EBD3), frame = Frame.BRASS, backdrop = Backdrop.STEAM,
        surface = Color(0xFFF6EBD3),
    )
}

/** Retro pixel: square panels with thick outlines. Dark = black screen and lime; light = old handheld green-grey. */
private fun retroColors(dark: Boolean, animations: Boolean) = if (!dark) GlassColors(
    isDark = false, accent = Color(0xFF3F6E12), accent2 = Color(0xFF1F6F8B),
    bgTop = Color(0xFFDCE4C4), bgBottom = Color(0xFFC3CEA2), blobs = emptyList(),
    glassTop = Color(0xFFF3F6E6), glassBottom = Color(0xFFF3F6E6),
    edge = Color(0xFF26301A),
    text = Color(0xFF1C2414), textDim = Color(0xFF5B6748),
    shadow = Color(0xFF26301A).copy(alpha = 0.85f),
    animations = animations, style = AppStyle.RETRO,
    glow = 0.3f, frame = Frame.PIXEL, backdrop = Backdrop.PIXEL, surface = Color(0xFFF3F6E6),
) else GlassColors(
    isDark = true, accent = Color(0xFFB6F23C), accent2 = Color(0xFF7DD3FC),
    bgTop = Color(0xFF0C0D0E), bgBottom = Color(0xFF151719), blobs = emptyList(),
    glassTop = Color(0xFF1C1F21), glassBottom = Color(0xFF1C1F21),
    edge = Color(0xFF4A5056),
    text = Color(0xFFE8ECEF), textDim = Color(0xFF8A939A),
    shadow = Color.Black.copy(alpha = 0.9f),
    animations = animations, style = AppStyle.RETRO,
    glow = 0.6f, frame = Frame.PIXEL, backdrop = Backdrop.PIXEL, surface = Color(0xFF1C1F21),
)

/** A calm meadow in the style of hand-drawn animated films (original art): sky, clouds, hills, a big tree. */
private fun meadowColors(dark: Boolean, animations: Boolean) = if (dark) {
    GlassColors(
        isDark = true, accent = Color(0xFF9BD4A0), accent2 = Color(0xFFF6D56B),
        bgTop = Color(0xFF14203F), bgBottom = Color(0xFF1C3A2E), blobs = emptyList(),
        glassTop = Color(0xFF1F2B45).copy(alpha = 0.9f), glassBottom = Color(0xFF1B2A3A).copy(alpha = 0.9f),
        edge = Color(0xFF0B1120),
        text = Color(0xFFEAF2F8), textDim = Color(0xFFA7B8C9),
        shadow = Color.Black.copy(alpha = 0.35f),
        animations = animations, style = AppStyle.MEADOW,
        glow = 0.3f, pastel = 0.2f, paper = Color(0xFFDCE7F0), frame = Frame.CARTOON, backdrop = Backdrop.MEADOW,
        surface = Color(0xFF1F2B45),
    )
} else {
    GlassColors(
        isDark = false, accent = Color(0xFF3E8E5A), accent2 = Color(0xFFE77B5C),
        bgTop = Color(0xFF9FD3EE), bgBottom = Color(0xFF8CCB7E), blobs = emptyList(),
        glassTop = Color(0xFFFFFEF8).copy(alpha = 0.95f), glassBottom = Color(0xFFF8F6EC).copy(alpha = 0.95f),
        edge = Color(0xFF3D4B52),
        text = Color(0xFF2C3A40), textDim = Color(0xFF6A7B82),
        shadow = Color(0xFF2C3A40).copy(alpha = 0.18f),
        animations = animations, style = AppStyle.MEADOW,
        glow = 0.25f, pastel = 0.3f, paper = Color(0xFFFFFEF8), frame = Frame.CARTOON, backdrop = Backdrop.MEADOW,
        surface = Color(0xFFFFFEF8),
    )
}

/** 0 spring, 1 summer, 2 autumn, 3 winter, by the Persian calendar (Farvardin = spring). */
fun persianSeason(): Int {
    val cal = android.icu.util.Calendar.getInstance(android.icu.util.ULocale("fa_IR@calendar=persian"))
    return cal.get(android.icu.util.Calendar.MONTH) / 3
}

private fun seasonColors(dark: Boolean, choice: Season, animations: Boolean): GlassColors {
    val index = if (choice == Season.AUTO) persianSeason() else choice.ordinal - 1
    val (scene, accent, accent2) = when (index) {
        0 -> Triple(Spring, Color(0xFF3FA06D), Color(0xFFF08DB0))
        1 -> Triple(Summer, Color(0xFFF08A24), Color(0xFF2FB3C9))
        2 -> Triple(Autumn, Color(0xFFD2603A), Color(0xFFE3A23C))
        else -> Triple(Winter, Color(0xFF5B84C4), Color(0xFF8FB3E0))
    }
    if (dark) {
        val night = scene.atNight()
        return peachColors(true, animations).copy(
            accent = lerp(accent, Color.White, 0.2f), accent2 = lerp(accent2, Color.White, 0.2f),
            scene = night, style = AppStyle.SEASONS, bgTop = night.skyTop, bgBottom = night.groundFront,
        )
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

/** Playful display font for the app name (Lalezar, SIL Open Font License). */
val Lalezar = FontFamily(Font(R.font.lalezar, FontWeight.Normal))

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
    val wantDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val glass = themeColors(wantDark, settings)
    val dark = glass.isDark
    val scheme = if (dark) {
        darkColorScheme(
            primary = glass.accent, secondary = glass.accent2, background = glass.bgTop,
            surface = glass.surface ?: if (glass.scene != null) Color(0xFF2E2640) else Color(0xFF1B2133), onSurface = glass.text, onBackground = glass.text,
            surfaceContainerLow = glass.surface ?: if (glass.scene != null) Color(0xFF2E2640) else Color(0xFF1B2133),
            surfaceContainer = glass.surface ?: if (glass.scene != null) Color(0xFF362D4A) else Color(0xFF20273B),
            surfaceContainerHigh = glass.surface ?: if (glass.scene != null) Color(0xFF3B3150) else Color(0xFF262E45),
        )
    } else {
        lightColorScheme(
            primary = glass.accent, secondary = glass.accent2, background = glass.bgTop,
            surface = glass.surface ?: if (glass.scene != null) Color(0xFFFFF6F0) else Color(0xFFF8FAFC), onSurface = glass.text, onBackground = glass.text,
            surfaceContainerHigh = glass.surface ?: if (glass.scene != null) Color(0xFFFFEFE6) else Color(0xFFF1F5F9),
            surfaceContainerLow = glass.surface ?: Color(0xFFF8FAFC),
            surfaceContainer = glass.surface ?: Color(0xFFF1F5F9),
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
