package com.rez.notif.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp

/**
 * Flat vector landscapes drawn in code (no image files): every shape is a path, so they stay sharp
 * on any screen, take no space, and recolor with a palette. A new season or mood is just a new palette.
 */
@Immutable
data class ScenePalette(
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val cloud: Color,
    val far: Color,
    val mid: Color,
    val forest: Color,
    val forestBack: Color,
    val lake: Color,
    val lakeShine: Color,
    val ground: Color,
    val groundFront: Color,
    val night: Boolean,
    val snow: Boolean = false,
)

val PeachDay = ScenePalette(
    skyTop = Color(0xFFFDEBE1), skyBottom = Color(0xFFF8CBB6), sun = Color(0xFFF6A07F), cloud = Color(0xFFFFF7F2),
    far = Color(0xFFF2BDA8), mid = Color(0xFFE59B87), forest = Color(0xFF4E6B66), forestBack = Color(0xFF7E8F86),
    lake = Color(0xFFA9D6D1), lakeShine = Color(0xFFEAF6F3), ground = Color(0xFFF0B49E), groundFront = Color(0xFFE39D89),
    night = false,
)

/** Seasons, picked by the Persian calendar (Farvardin = spring). Same landscape, new colors. */
val Spring = ScenePalette(
    skyTop = Color(0xFFE6F4FA), skyBottom = Color(0xFFCDE9F2), sun = Color(0xFFFFD27A), cloud = Color(0xFFFFFFFF),
    far = Color(0xFFB9DDB6), mid = Color(0xFF8CC79A), forest = Color(0xFF3F7F5F), forestBack = Color(0xFF77A88A),
    lake = Color(0xFF9ED8E6), lakeShine = Color(0xFFF2FBFD), ground = Color(0xFFF4C6D7), groundFront = Color(0xFFA9D8A0),
    night = false,
)
val Summer = ScenePalette(
    skyTop = Color(0xFFFFF1D6), skyBottom = Color(0xFFFFD9A0), sun = Color(0xFFFF9F43), cloud = Color(0xFFFFFBF2),
    far = Color(0xFFE9C38B), mid = Color(0xFFD9A066), forest = Color(0xFF4F7A52), forestBack = Color(0xFF86A776),
    lake = Color(0xFF6FC7D9), lakeShine = Color(0xFFE8F8FB), ground = Color(0xFFF2CF8C), groundFront = Color(0xFFE4B66F),
    night = false,
)
val Autumn = ScenePalette(
    skyTop = Color(0xFFFBE2CC), skyBottom = Color(0xFFF3B98E), sun = Color(0xFFE9774A), cloud = Color(0xFFFFF4EA),
    far = Color(0xFFD9967A), mid = Color(0xFFB86B52), forest = Color(0xFFA6492E), forestBack = Color(0xFFD08A3E),
    lake = Color(0xFF9CB8C0), lakeShine = Color(0xFFEAF1F2), ground = Color(0xFFE8A367), groundFront = Color(0xFFC9784B),
    night = false,
)
val Winter = ScenePalette(
    skyTop = Color(0xFFE8EEF7), skyBottom = Color(0xFFC9D6EA), sun = Color(0xFFF6F2E9), cloud = Color(0xFFFFFFFF),
    far = Color(0xFFDDE6F2), mid = Color(0xFFB7C6DD), forest = Color(0xFF3E5A6E), forestBack = Color(0xFF7D92A8),
    lake = Color(0xFFB9CFE3), lakeShine = Color(0xFFFFFFFF), ground = Color(0xFFF4F7FB), groundFront = Color(0xFFE1E8F2),
    night = false, snow = true,
)

val PeachNight = ScenePalette(
    skyTop = Color(0xFF241F3A), skyBottom = Color(0xFF6E4A6A), sun = Color(0xFFFBE8DA), cloud = Color(0xFF8A6A88),
    far = Color(0xFF7B5A7A), mid = Color(0xFF5C4462), forest = Color(0xFF26283A), forestBack = Color(0xFF3C3550),
    lake = Color(0xFF4A5878), lakeShine = Color(0xFF93A6C8), ground = Color(0xFF3E2F49), groundFront = Color(0xFF33283E),
    night = true,
)

/** Peach mountains: sun or moon, clouds, two mountain ranges, pine forest, a lake and hills. [t] (0..1) moves things a little. */
fun DrawScope.peachMountains(p: ScenePalette, t: Float) {
    val w = size.width
    val h = size.height

    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom), 0f, h * 0.55f))

    if (p.night) {
        var seed = 7
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 10_000) / 10_000f }
        repeat(46) { i ->
            val twinkle = 0.45f + 0.55f * (((i * 0.37f) + t) % 1f)
            drawCircle(Color.White.copy(alpha = 0.7f * twinkle), radius = 1.2f + 2f * rnd(), center = Offset(w * rnd(), h * 0.42f * rnd()))
        }
    }

    // Sun (or moon) with soft halos.
    val sun = Offset(w * 0.72f, h * 0.2f)
    val r = w * 0.1f
    drawCircle(p.sun.copy(alpha = 0.12f), r * 2.4f, sun)
    drawCircle(p.sun.copy(alpha = 0.22f), r * 1.6f, sun)
    drawCircle(p.sun, r, sun)
    if (p.night) drawCircle(p.skyTop.copy(alpha = 0.9f), r * 0.85f, sun + Offset(r * 0.45f, -r * 0.2f))

    // Clouds drift slowly.
    cloud(Offset(w * (0.16f + 0.08f * t), h * 0.13f), w * 0.2f, p.cloud.copy(alpha = if (p.night) 0.5f else 0.95f))
    cloud(Offset(w * (0.62f - 0.06f * t), h * 0.29f), w * 0.15f, p.cloud.copy(alpha = if (p.night) 0.4f else 0.85f))

    // Far range, then near range (moves a bit more: parallax).
    val farBase = h * 0.5f
    listOf(Triple(0.08f, 0.31f, 0.24f), Triple(0.38f, 0.26f, 0.26f), Triple(0.64f, 0.32f, 0.2f), Triple(0.92f, 0.28f, 0.25f))
        .forEach { (x, y, hw) -> peak(w * x + w * 0.01f * t, h * y, w * hw, farBase, p.far) }
    val midBase = h * 0.56f
    listOf(Triple(-0.02f, 0.38f, 0.22f), Triple(0.27f, 0.36f, 0.22f), Triple(0.56f, 0.41f, 0.2f), Triple(0.84f, 0.37f, 0.26f))
        .forEach { (x, y, hw) -> peak(w * x - w * 0.02f * t, h * y, w * hw, midBase, p.mid) }

    // Forest line at the lake shore: a back row and a front row of pines.
    drawRect(p.ground, Offset(0f, h * 0.53f), Size(w, h * 0.05f))
    var x = -w * 0.02f
    var i = 0
    while (x < w * 1.04f) {
        val hh = h * (0.035f + 0.02f * (((i * 7) % 5) / 4f))
        pine(x, h * 0.565f, hh, p.forestBack)
        x += w * 0.045f; i++
    }
    x = w * 0.01f; i = 0
    while (x < w * 1.04f) {
        val hh = h * (0.03f + 0.025f * (((i * 3) % 4) / 3f))
        pine(x, h * 0.58f, hh, p.forest)
        x += w * 0.06f; i++
    }

    // Lake with shine lines and the sun's reflection.
    drawRect(Brush.verticalGradient(listOf(p.lake, lerp(p.lake, p.skyBottom, 0.35f)), h * 0.58f, h * 0.76f), Offset(0f, h * 0.58f), Size(w, h * 0.18f))
    for (k in 0 until 6) {
        val y = h * (0.6f + k * 0.027f)
        val len = w * (0.08f + 0.05f * (k % 3))
        val cx = sun.x + (if (k % 2 == 0) -1 else 1) * w * 0.02f * (1 + k % 2) + w * 0.015f * t
        drawRoundRect(p.sun.copy(alpha = 0.55f - k * 0.07f), Offset(cx - len / 2, y), Size(len, h * 0.005f), androidx.compose.ui.geometry.CornerRadius(h * 0.003f))
        val sx = w * ((0.12f + k * 0.17f + 0.03f * t) % 1f)
        drawRoundRect(p.lakeShine.copy(alpha = 0.5f), Offset(sx, y + h * 0.01f), Size(w * 0.07f, h * 0.004f), androidx.compose.ui.geometry.CornerRadius(h * 0.002f))
    }

    // Rolling hills in front, with big pines on the sides.
    val hill = Path().apply {
        moveTo(0f, h * 0.76f)
        quadraticTo(w * 0.3f, h * 0.7f, w * 0.6f, h * 0.77f)
        quadraticTo(w * 0.85f, h * 0.82f, w, h * 0.74f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(hill, p.ground)
    val front = Path().apply {
        moveTo(0f, h * 0.86f)
        quadraticTo(w * 0.45f, h * 0.8f, w, h * 0.88f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(front, p.groundFront)
    pine(w * 0.07f, h * 0.8f, h * 0.12f, p.forest)
    pine(w * 0.16f, h * 0.79f, h * 0.085f, p.forestBack)
    pine(w * 0.9f, h * 0.78f, h * 0.13f, p.forest)
    pine(w * 0.81f, h * 0.79f, h * 0.08f, p.forestBack)

    // Gentle snowfall: flakes slide down as [t] moves.
    if (p.snow) {
        var seed = 11
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 10_000) / 10_000f }
        repeat(70) {
            val x = w * rnd()
            val y = (h * rnd() + h * 0.25f * t) % h
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 1.5f + 2.5f * rnd(), center = Offset(x, y))
        }
    }
}

/** A flat mountain: a lit left face and a shaded right face, like folded paper. */
private fun DrawScope.peak(px: Float, py: Float, halfWidth: Float, baseY: Float, c: Color) {
    val bottom = size.height
    drawPath(Path().apply { moveTo(px, py); lineTo(px - halfWidth, baseY); lineTo(px - halfWidth, bottom); lineTo(px, bottom); close() }, c)
    drawPath(Path().apply { moveTo(px, py); lineTo(px + halfWidth, baseY); lineTo(px + halfWidth, bottom); lineTo(px, bottom); close() }, lerp(c, Color.Black, 0.1f))
    // Small light cap at the top.
    val cap = 0.18f
    drawPath(
        Path().apply {
            moveTo(px, py)
            lineTo(px - halfWidth * cap, py + (baseY - py) * cap)
            lineTo(px - halfWidth * cap * 0.3f, py + (baseY - py) * cap * 0.8f)
            lineTo(px, py + (baseY - py) * cap * 1.05f)
            close()
        },
        lerp(c, Color.White, 0.45f),
    )
}

/** Three stacked triangles and a trunk. */
private fun DrawScope.pine(x: Float, baseY: Float, height: Float, c: Color) {
    val w = height * 0.5f
    drawRect(lerp(c, Color.Black, 0.25f), Offset(x - w * 0.07f, baseY - height * 0.12f), Size(w * 0.14f, height * 0.12f))
    for (k in 0 until 3) {
        val top = baseY - height * (1f - k * 0.22f)
        val bottom = baseY - height * (0.32f - k * 0.12f)
        val half = w * (0.55f + k * 0.2f) / 2f * 1.2f
        drawPath(Path().apply { moveTo(x, top); lineTo(x - half, bottom); lineTo(x + half, bottom); close() }, c)
    }
}

private fun DrawScope.cloud(center: Offset, width: Float, c: Color) {
    val r = width / 4f
    drawRoundRect(c, Offset(center.x - width / 2, center.y), Size(width, r * 1.1f), androidx.compose.ui.geometry.CornerRadius(r * 0.55f))
    drawCircle(c, r, center + Offset(-width * 0.18f, r * 0.3f))
    drawCircle(c, r * 1.35f, center + Offset(width * 0.05f, 0f))
    drawCircle(c, r * 0.9f, center + Offset(width * 0.28f, r * 0.35f))
}
