package com.rez.notif.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

/** Backgrounds of the extra styles, drawn in code like the landscapes. [t] (0..1) moves things a little. */
fun DrawScope.drawBackdrop(b: Backdrop, g: GlassColors, t: Float) {
    when (b) {
        Backdrop.CYBER -> cyberHud(g, t)
        Backdrop.STEAM -> steamWorks(g, t)
        Backdrop.PIXEL -> pixelScreen(g, t)
        Backdrop.MEADOW -> meadow(g.isDark, t)
        Backdrop.BLOBS -> Unit
    }
}

private class R(var s: Int) {
    fun n(): Float { s = (s * 1103515245 + 12345) and 0x7fffffff; return (s % 10_000) / 10_000f }
}

/** Cyberpunk: faint grid, ruler ticks on the side, binary columns, big arcs and a moving scan line. */
private fun DrawScope.cyberHud(g: GlassColors, t: Float) {
    val w = size.width; val h = size.height
    val red = g.accent; val cyan = g.accent2
    val step = w / 12f
    var x = 0f
    while (x < w) { drawLine(red.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, h)); x += step }
    var y = 0f
    while (y < h) { drawLine(red.copy(alpha = 0.05f), Offset(0f, y), Offset(w, y)); y += step }
    // Ruler on the left edge.
    var k = 0
    y = h * 0.15f
    while (y < h * 0.85f) {
        drawLine(red.copy(alpha = 0.45f), Offset(0f, y), Offset(if (k % 5 == 0) w * 0.05f else w * 0.025f, y), strokeWidth = 2f)
        y += h * 0.012f; k++
    }
    // Diagonal stripes on the right edge.
    for (i in 0 until 40) {
        val sy = h * 0.2f + i * h * 0.012f
        drawLine(red.copy(alpha = 0.25f), Offset(w * 0.985f, sy), Offset(w, sy - h * 0.008f), strokeWidth = 3f)
    }
    // Big arcs, bottom corner.
    for (i in 0 until 3) {
        val r = w * (0.35f + i * 0.14f)
        drawArc(cyan.copy(alpha = 0.10f), 180f + 20f * t, 120f, false, Offset(w * 0.85f - r, h * 0.92f - r), Size(r * 2, r * 2), style = Stroke(w * 0.012f))
    }
    // Binary columns.
    val rnd = R(5)
    for (c in 0 until 6) {
        val cx = w * (0.08f + c * 0.16f)
        for (r in 0 until 8) {
            if (rnd.n() > 0.5f) drawRect(red.copy(alpha = 0.06f), Offset(cx + rnd.n() * 20f, h * (0.05f + r * 0.11f)), Size(w * 0.06f, 3f))
        }
    }
    // Scan line sliding down.
    val sy = h * t
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, cyan.copy(alpha = 0.10f), Color.Transparent), sy - 60f, sy + 60f), Offset(0f, sy - 60f), Size(w, 120f))
}

/** Steampunk: brass gears turning slowly, pipes and rivets on warm dark leather. */
private fun DrawScope.steamWorks(g: GlassColors, t: Float) {
    val w = size.width; val h = size.height
    val brass = Color(0xFFC9963F)
    val dark = Color(0xFF1A110B)
    // Warm light from the top, like a lamp.
    drawRect(Brush.radialGradient(listOf(Color(0xFFE0A050).copy(alpha = 0.25f), Color.Transparent), Offset(w * 0.5f, 0f), h * 0.6f))
    gear(Offset(w * 0.9f, h * 0.12f), w * 0.28f, 14, brass.copy(alpha = 0.22f), dark, 40f * t)
    gear(Offset(w * 0.62f, h * 0.2f), w * 0.15f, 10, brass.copy(alpha = 0.18f), dark, -70f * t)
    gear(Offset(w * 0.05f, h * 0.62f), w * 0.3f, 16, brass.copy(alpha = 0.16f), dark, -30f * t)
    gear(Offset(w * 0.95f, h * 0.9f), w * 0.22f, 12, brass.copy(alpha = 0.2f), dark, 50f * t)
    gear(Offset(w * 0.3f, h * 0.97f), w * 0.12f, 9, brass.copy(alpha = 0.16f), dark, -80f * t)
    // Copper pipes along the edges with joints.
    val copper = Color(0xFFB0643A).copy(alpha = 0.35f)
    drawLine(copper, Offset(w * 0.02f, 0f), Offset(w * 0.02f, h * 0.45f), strokeWidth = w * 0.018f)
    drawLine(copper, Offset(w * 0.02f, h * 0.45f), Offset(w * 0.2f, h * 0.45f), strokeWidth = w * 0.018f)
    drawCircle(brass.copy(alpha = 0.4f), w * 0.016f, Offset(w * 0.02f, h * 0.45f))
    drawLine(copper, Offset(w * 0.98f, h * 0.35f), Offset(w * 0.98f, h * 0.75f), strokeWidth = w * 0.018f)
    // Rivets.
    for (i in 0 until 14) {
        drawCircle(brass.copy(alpha = 0.35f), 4f, Offset(w * (0.04f + i * 0.07f), h * 0.995f))
    }
}

private fun DrawScope.gear(c: Offset, r: Float, teeth: Int, color: Color, hole: Color, angle: Float) {
    rotate(angle, c) {
        val path = Path()
        val inner = r * 0.82f
        val steps = teeth * 4
        for (i in 0..steps) {
            val a = i * 2 * Math.PI / steps
            val rr = if ((i / 2) % 2 == 0) r else inner
            val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, color)
        drawCircle(hole.copy(alpha = 0.5f), r * 0.42f, c)
        drawCircle(color, r * 0.3f, c, style = Stroke(r * 0.06f))
        for (k in 0 until 5) {
            val a = k * 2 * Math.PI / 5
            drawLine(color, c, Offset(c.x + (r * 0.42f * cos(a)).toFloat(), c.y + (r * 0.42f * sin(a)).toFloat()), strokeWidth = r * 0.07f)
        }
    }
}

/** Retro pixel: a dotted grid, scan lines and a few blinking pixels. */
private fun DrawScope.pixelScreen(g: GlassColors, t: Float) {
    val w = size.width; val h = size.height
    val cell = w / 36f
    var y = 0f
    var row = 0
    while (y < h) {
        var x = 0f
        while (x < w) {
            drawRect(Color.White.copy(alpha = 0.05f), Offset(x, y), Size(3f, 3f))
            x += cell
        }
        y += cell; row++
    }
    y = 0f
    while (y < h) { drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Size(w, 2f)); y += 6f }
    val rnd = R(9)
    for (i in 0 until 18) {
        val on = (rnd.n() + t) % 1f > 0.5f
        val px = (rnd.n() * 36).toInt() * cell
        val py = (rnd.n() * (h / cell)).toInt() * cell
        if (on) drawRect(g.accent.copy(alpha = 0.18f), Offset(px, py), Size(cell * 0.6f, cell * 0.6f))
    }
}

/** Meadow (original art): big soft clouds, rolling green hills, a large round tree and a small red-roof house. */
private fun DrawScope.meadow(night: Boolean, t: Float) {
    val w = size.width; val h = size.height
    val sky1 = if (night) Color(0xFF14203F) else Color(0xFF8FCBEA)
    val sky2 = if (night) Color(0xFF2B3F6B) else Color(0xFFD6EEF8)
    drawRect(Brush.verticalGradient(listOf(sky1, sky2), 0f, h * 0.6f))
    if (night) {
        val rnd = R(3)
        repeat(60) { drawCircle(Color.White.copy(alpha = 0.5f + 0.4f * ((rnd.n() + t) % 1f)), 1.2f + 1.8f * rnd.n(), Offset(w * rnd.n(), h * 0.5f * rnd.n())) }
        drawCircle(Color(0xFFFFF4D2), w * 0.07f, Offset(w * 0.78f, h * 0.12f))
    }
    // Tall summer clouds: stacked circles with a shaded underside.
    fun bigCloud(cx: Float, cy: Float, s: Float) {
        val top = if (night) Color(0xFF3A4C78) else Color.White
        val shade = if (night) Color(0xFF2E3D63) else Color(0xFFDDEBF4)
        drawCircle(shade, s * 0.55f, Offset(cx - s * 0.5f, cy + s * 0.15f))
        drawCircle(shade, s * 0.6f, Offset(cx + s * 0.5f, cy + s * 0.12f))
        drawCircle(top, s * 0.7f, Offset(cx, cy - s * 0.2f))
        drawCircle(top, s * 0.5f, Offset(cx - s * 0.45f, cy - s * 0.05f))
        drawCircle(top, s * 0.55f, Offset(cx + s * 0.45f, cy - s * 0.02f))
        drawCircle(top, s * 0.45f, Offset(cx + s * 0.1f, cy - s * 0.7f))
    }
    bigCloud(w * (0.25f + 0.05f * t), h * 0.16f, w * 0.2f)
    bigCloud(w * (0.82f - 0.04f * t), h * 0.3f, w * 0.14f)
    // Hills, back to front.
    val far = if (night) Color(0xFF22413F) else Color(0xFF9ACF8A)
    val mid = if (night) Color(0xFF1D3A33) else Color(0xFF7DBE6E)
    val near = if (night) Color(0xFF17302A) else Color(0xFF63A85A)
    fun hill(y0: Float, amp: Float, c: Color, phase: Float) {
        val p = Path().apply {
            moveTo(0f, h)
            lineTo(0f, y0)
            var x = 0f
            while (x <= w) {
                lineTo(x, y0 - amp * sin((x / w) * 3.2f + phase))
                x += w / 40f
            }
            lineTo(w, h); close()
        }
        drawPath(p, c)
    }
    hill(h * 0.58f, h * 0.04f, far, 0.5f)
    // A small house with a red roof on the far hill.
    val hx = w * 0.22f; val hy = h * 0.565f
    drawRect(if (night) Color(0xFF6B6250) else Color(0xFFF4EBD6), Offset(hx, hy), Size(w * 0.06f, h * 0.025f))
    drawPath(Path().apply { moveTo(hx - w * 0.008f, hy); lineTo(hx + w * 0.03f, hy - h * 0.018f); lineTo(hx + w * 0.068f, hy); close() }, if (night) Color(0xFF7A3A33) else Color(0xFFD9573F))
    if (night) drawRect(Color(0xFFFFD27A), Offset(hx + w * 0.022f, hy + h * 0.008f), Size(w * 0.012f, h * 0.01f))
    hill(h * 0.68f, h * 0.05f, mid, 2.2f)
    // The big tree on the middle hill.
    val tx = w * 0.72f; val ty = h * 0.64f
    val trunk = if (night) Color(0xFF3B2E25) else Color(0xFF6E5236)
    drawRect(trunk, Offset(tx - w * 0.025f, ty - h * 0.08f), Size(w * 0.05f, h * 0.1f))
    val leaf = if (night) Color(0xFF1F4A3B) else Color(0xFF3E8E5A)
    val leafLight = lerp(leaf, Color.White, 0.18f)
    drawCircle(leaf, w * 0.16f, Offset(tx, ty - h * 0.15f))
    drawCircle(leaf, w * 0.12f, Offset(tx - w * 0.14f, ty - h * 0.1f))
    drawCircle(leaf, w * 0.12f, Offset(tx + w * 0.14f, ty - h * 0.1f))
    drawCircle(leafLight, w * 0.08f, Offset(tx - w * 0.06f, ty - h * 0.17f))
    hill(h * 0.8f, h * 0.035f, near, 4.0f)
    // Flowers or fireflies.
    val rnd = R(17)
    repeat(50) {
        val fx = w * rnd.n(); val fy = h * (0.82f + 0.17f * rnd.n())
        if (night) {
            val glow = 0.3f + 0.7f * ((rnd.n() + t) % 1f)
            drawCircle(Color(0xFFF6E27A).copy(alpha = 0.25f * glow), 9f, Offset(fx, fy - h * 0.2f * rnd.n()))
            drawCircle(Color(0xFFF6E27A).copy(alpha = glow), 2.5f, Offset(fx, fy - h * 0.2f * rnd.n()))
        } else {
            val c = listOf(Color.White, Color(0xFFFFD66B), Color(0xFFF7A1C0))[it % 3]
            drawCircle(c, 3.5f, Offset(fx, fy))
        }
    }
    // A gentle breeze line.
    if (!night) drawLine(Color.White.copy(alpha = 0.5f), Offset(w * (0.1f + 0.2f * t), h * 0.45f), Offset(w * (0.3f + 0.2f * t), h * 0.44f), strokeWidth = 3f, cap = StrokeCap.Round)
}
