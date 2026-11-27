package com.rez.notif.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rez.notif.R

/*
 * Small motions for "شاخ گاو". Rules: each one is under half a second, none runs when [calmMotion] says so
 * (animations off in the app, reduce-motion, or battery saver), and the playful ones are skipped at serious
 * moments (Jokes.Mood.SERIOUS) by their callers.
 */

/** Fades and lifts an item in once, a little after the one before it ([index]). */
fun Modifier.appear(index: Int, calm: Boolean): Modifier = if (calm) this else composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(durationMillis = 320, delayMillis = (index * 45).coerceAtMost(270), easing = FastOutSlowInEasing)) }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * rise
    }
}

/** The daily joke turns over like a card, once a day (the first time the home screen shows it that day). */
@Composable
fun JokeFlip(calm: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val today = remember { java.time.LocalDate.now().toEpochDay() }
    val sp = remember { context.getSharedPreferences("motion", android.content.Context.MODE_PRIVATE) }
    val flip = remember { !calm && sp.getLong("joke_day", 0) != today }
    val rot = remember { Animatable(if (flip) 90f else 0f) }
    LaunchedEffect(Unit) {
        if (flip) {
            sp.edit().putLong("joke_day", today).apply()
            rot.animateTo(0f, tween(450, delayMillis = 250, easing = FastOutSlowInEasing))
        }
    }
    val density = LocalDensity.current.density
    androidx.compose.foundation.layout.Column(
        Modifier.graphicsLayer { rotationX = rot.value; cameraDistance = 14f * density; alpha = if (rot.value > 85f) 0f else 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** A coin flying from [from] to [to] (window coordinates) along an arc. */
data class CoinFlight(val from: Offset, val to: Offset, val id: Long)

/** Draws [flight] over the screen; [onDone] when it lands (about 0.45 s). */
@Composable
fun CoinFlightLayer(flight: CoinFlight?, onDone: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(flight?.id) {
        if (flight == null) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        onDone()
    }
    val lift = with(LocalDensity.current) { 110.dp.toPx() }
    val r = with(LocalDensity.current) { 11.dp.toPx() }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val f = flight ?: return@Canvas
        val t = p.value
        if (t <= 0f || t >= 1f) return@Canvas
        val a = f.from - origin
        val b = f.to - origin
        val c = Offset((a.x + b.x) / 2, minOf(a.y, b.y) - lift)
        // Quadratic curve: up and over, then down into the chip.
        val u = 1 - t
        val pos = Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
        val s = 1f - 0.35f * t
        // A coin seen turning: its width goes in and out.
        val turn = kotlin.math.abs(kotlin.math.cos(t * 3 * Math.PI)).toFloat().coerceAtLeast(0.25f)
        drawOval(Color(0x33000000), topLeft = Offset(pos.x - r * s * turn, pos.y - r * s + 3f), size = androidx.compose.ui.geometry.Size(2 * r * s * turn, 2 * r * s))
        drawOval(Color(0xFFE0A030), topLeft = Offset(pos.x - r * s * turn, pos.y - r * s), size = androidx.compose.ui.geometry.Size(2 * r * s * turn, 2 * r * s))
        drawOval(Color(0xFFFFD766), topLeft = Offset(pos.x - r * 0.7f * s * turn, pos.y - r * 0.7f * s), size = androidx.compose.ui.geometry.Size(1.4f * r * s * turn, 1.4f * r * s))
    }
}

/**
 * Pull-to-refresh indicator: the cow peeks down as you pull and chews while new SMS are read.
 * At serious moments ([playful] false) it does not chew.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CowRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, calm: Boolean, playful: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pull = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1.4f)
    if (pull <= 0f && !refreshing) return
    val chew = rememberInfiniteTransition(label = "chew")
    val jaw by chew.animateFloat(1f, 0.9f, infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse), label = "jaw")
    val wobble by chew.animateFloat(-6f, 6f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wobble")
    val moving = refreshing && !calm && playful
    Box(
        modifier.graphicsLayer {
            translationY = with(density) { (pull * 70.dp.toPx()) - 40.dp.toPx() }
            alpha = pull.coerceIn(0f, 1f)
            rotationZ = if (moving) wobble else (pull - 1f).coerceAtLeast(0f) * 20f
            scaleY = if (moving) jaw else 1f
        },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.cow_cut), null, Modifier.size(56.dp))
    }
}

/**
 * Empty screens: the cow chews slowly next to a short line. Static when motion is calm.
 * [size]: height of the cow.
 */
@Composable
fun EmptyCow(text: String, calm: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 72.dp) {
    val g = com.rez.notif.ui.theme.LocalGlass.current
    val t = rememberInfiniteTransition(label = "ruminate")
    // Slow chewing (jaw) and a small breath (bob); a ruminating cow is not in a hurry.
    val jaw by t.animateFloat(1f, 0.93f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "jaw")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val lift = with(LocalDensity.current) { 3.dp.toPx() }
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.cow_cut), null,
            Modifier.size(size).graphicsLayer {
                if (!calm) {
                    scaleY = jaw
                    translationY = -bob * lift
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                }
            },
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        androidx.compose.material3.Text(
            text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = g.text.copy(alpha = 0.85f),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}


/**
 * The pistachio jar ("قلک پسته‌ای"): fills with pistachios up to [progress] (0..1). When it grows, a pistachio
 * drops in and the level rises (under half a second).
 */
@Composable
fun PistachioJar(progress: Float, calm: Boolean, modifier: Modifier = Modifier) {
    val g = com.rez.notif.ui.theme.LocalGlass.current
    val target = progress.coerceIn(0f, 1f)
    val level = remember { Animatable(target) }
    val drop = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (calm || target <= level.value) { level.snapTo(target); return@LaunchedEffect }
        drop.snapTo(0f)
        drop.animateTo(1f, tween(280, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        level.animateTo(target, tween(220, easing = FastOutSlowInEasing))
    }
    val glass = if (g.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val line = g.text.copy(alpha = 0.35f)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val lidH = h * 0.10f
        val neckTop = h * 0.08f; val bodyTop = h * 0.22f
        val body = androidx.compose.ui.geometry.RoundRect(w * 0.08f, bodyTop, w * 0.92f, h, androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
        val bodyPath = androidx.compose.ui.graphics.Path().apply { addRoundRect(body) }
        // neck and glass
        drawRect(glass, Offset(w * 0.26f, lidH * 0.9f), androidx.compose.ui.geometry.Size(w * 0.48f, bodyTop - neckTop + 4f))
        drawPath(bodyPath, glass)
        // pistachios up to the level
        val fillTop = bodyTop + (h - bodyTop) * (1f - level.value)
        clipPath(bodyPath) {
            drawRect(Color(0xFFA8C97F).copy(alpha = 0.55f), Offset(0f, fillTop), androidx.compose.ui.geometry.Size(w, h - fillTop))
            val nut = w * 0.17f
            var row = 0
            var y = h - nut * 0.55f
            while (y > fillTop - nut * 0.2f) {
                var x = w * 0.12f + (if (row % 2 == 1) nut * 0.55f else 0f)
                while (x < w * 0.9f) {
                    pistachio(Offset(x + nut * 0.5f, y), nut, (row * 7 + (x / nut).toInt() * 3) % 5)
                    x += nut * 1.05f
                }
                y -= nut * 0.62f; row++
            }
        }
        // falling pistachio
        if (drop.value < 1f) {
            val nut = w * 0.17f
            val y = -nut + (fillTop + nut) * drop.value
            pistachio(Offset(w * 0.5f, y), nut, 2)
        }
        drawPath(bodyPath, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f * density))
        // neck outline, so the lid sits on the jar
        drawRect(line, Offset(w * 0.26f, lidH * 0.9f), androidx.compose.ui.geometry.Size(w * 0.48f, bodyTop - lidH * 0.9f + 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f * density))
        // shine and lid (saffron)
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.18f, bodyTop + h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.38f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        drawRoundRect(Color(0xFFF2A93B), Offset(w * 0.2f, 0f), androidx.compose.ui.geometry.Size(w * 0.6f, lidH), androidx.compose.ui.geometry.CornerRadius(lidH / 3))
    }
}

/** One pistachio: a beige shell open a little, the green nut showing. [tilt] 0..4 turns it a bit. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pistachio(c: Offset, size: Float, tilt: Int) {
    val deg = (tilt - 2) * 18f
    rotate(deg, c) {
        drawOval(Color(0xFFD9C29A), Offset(c.x - size * 0.5f, c.y - size * 0.33f), androidx.compose.ui.geometry.Size(size, size * 0.66f))
        drawOval(Color(0xFF8DB255), Offset(c.x - size * 0.28f, c.y - size * 0.2f), androidx.compose.ui.geometry.Size(size * 0.56f, size * 0.4f))
        drawOval(Color(0xFFB59A6A).copy(alpha = 0.6f), Offset(c.x - size * 0.5f, c.y - size * 0.33f), androidx.compose.ui.geometry.Size(size, size * 0.66f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size * 0.06f))
    }
}
