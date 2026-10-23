package com.rez.notif.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.ui.theme.Backdrop
import com.rez.notif.ui.theme.Frame
import com.rez.notif.ui.theme.LocalGlass
import com.rez.notif.ui.theme.drawBackdrop
import com.rez.notif.ui.theme.peachMountains
import com.rez.notif.ui.theme.soft

val GlassShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)

/**
 * The page background: a soft gradient with slowly drifting, blurred color spots.
 * The glass panels on top let these colors shine through.
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    val t = rememberInfiniteTransition(label = "bg")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = if (g.animations) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom))),
    ) {
        val scene = g.scene
        if (scene != null) {
            Canvas(Modifier.fillMaxSize()) { peachMountains(scene, drift) }
        } else if (g.backdrop != Backdrop.BLOBS) {
            Canvas(Modifier.fillMaxSize()) { drawBackdrop(g.backdrop, g, drift) }
        } else Canvas(Modifier.fillMaxSize().blur(90.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(g.blobs[0], radius = w * 0.55f, center = Offset(w * (0.15f + 0.2f * drift), h * (0.12f + 0.06f * drift)))
            drawCircle(g.blobs[1], radius = w * 0.5f, center = Offset(w * (0.95f - 0.25f * drift), h * (0.45f + 0.1f * drift)))
            drawCircle(g.blobs[2], radius = w * 0.45f, center = Offset(w * (0.3f + 0.3f * drift), h * (0.9f - 0.08f * drift)))
        }
        content()
    }
}

/** The shape a panel really gets in the current style (cut corners for cyber, square-ish for pixel…). */
@Composable
fun styledShape(requested: Shape): Shape {
    val g = LocalGlass.current
    val round = requested == CircleShape || requested == PillShape
    return when (g.frame) {
        Frame.ROUND, Frame.CARTOON -> requested
        Frame.CUT -> if (round) CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
        else CutCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 0.dp, bottomStart = 18.dp)
        Frame.BRASS -> if (requested == CircleShape) CircleShape else RoundedCornerShape(if (round) 10.dp else 12.dp)
        Frame.PIXEL -> CutCornerShape(if (round) 5.dp else 7.dp)
    }
}

/** Frosted glass panel: translucent fill, bright thin edge, soft shadow (or neon glow when [glow] is set). */
@Composable
fun Modifier.glassSurface(shape: Shape = GlassShape, glow: Color? = null, elevation: Dp = 14.dp): Modifier {
    val g = LocalGlass.current
    val sh = styledShape(shape)
    val fill = Brush.verticalGradient(listOf(g.glassTop, g.glassBottom))
    return when (g.frame) {
        Frame.ROUND -> this
            .softShadow(
                sh,
                color = glow?.let { g.soft(it).copy(alpha = 0.55f * g.glow) } ?: g.shadow,
                blur = elevation,
                offsetY = if (glow == null) elevation / 3 else 0.dp,
            )
            .background(fill, sh)
            .border(
                1.dp,
                Brush.linearGradient(listOf(g.edge, g.edge.copy(alpha = g.edge.alpha * 0.25f), (glow ?: g.edge).copy(alpha = 0.5f))),
                sh,
            )
        // Thin lit outline, glow only when asked.
        Frame.CUT -> this
            .softShadow(sh, color = (glow ?: Color.Transparent).copy(alpha = if (glow != null) 0.4f else 0f), blur = elevation)
            .background(fill, sh)
            .border(1.2.dp, glow ?: g.edge, sh)
        // Brass frame: a thick gold-to-bronze rim with a thin inner line.
        Frame.BRASS -> this
            .softShadow(sh, color = g.shadow, blur = elevation, offsetY = elevation / 3)
            .background(fill, sh)
            .border(2.5.dp, Brush.linearGradient(listOf(Color(0xFFE8C77A), Color(0xFF9A6A2A), Color(0xFFD9AE5C), Color(0xFF7A5222))), sh)
            .padding(3.5.dp)
            .border(0.8.dp, (glow ?: g.edge).copy(alpha = 0.55f), sh)
        // Pixel: hard, unblurred drop shadow and a thick flat outline.
        Frame.PIXEL -> this
            .softShadow(sh, color = Color.Black, blur = 1.dp, offsetY = 4.dp)
            .background(fill, sh)
            .border(2.5.dp, glow ?: g.edge, sh)
        // Cartoon: clean paper, dark ink outline, a small offset shadow.
        Frame.CARTOON -> this
            .softShadow(sh, color = g.shadow, blur = 2.dp, offsetY = 3.dp)
            .background(fill, sh)
            .border(1.8.dp, if (glow != null) lerp(glow, g.edge, 0.4f) else g.edge, sh)
    }
}

/** Squeezes a little when pressed (the "tactile" feel of the sample designs). */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val g = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && g.animations) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    glow: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Press effect first, so the whole glass panel squeezes together.
    val base = if (onClick != null) modifier.pressable(onClick, onLongClick) else modifier
    Column(base.glassSurface(shape, glow).padding(contentPadding), content = content)
}

/** Raised colored square with a number, like the "Inbox / To-Do / Notifications" tiles. */
@Composable
fun NeonNumber(value: String, neon: Color, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val color = g.soft(neon)
    Box(
        modifier
            .size(size)
            .softShadow(RoundedCornerShape(size / 3.2f), color.copy(alpha = 0.6f * g.glow), 14.dp, 4.dp)
            .background(
                Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color)),
                RoundedCornerShape(size / 3.2f),
            )
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(size / 3.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** Round glowing icon bubble used for categories. */
@Composable
fun NeonIcon(icon: ImageVector, neon: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val color = g.soft(neon)
    val sh = styledShape(CircleShape)
    Box(
        modifier
            .size(size)
            .softShadow(sh, color.copy(alpha = 0.55f * g.glow), 10.dp, 2.dp)
            .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.75f))), sh)
            .border(1.dp, Color.White.copy(alpha = 0.5f), sh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = onColor(color), modifier = Modifier.size(size * 0.52f))
    }
}

/** Small glowing count badge. */
@Composable
fun Badge(count: Int, neon: Color, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val g = LocalGlass.current
    val color = g.soft(neon)
    val sh = styledShape(PillShape)
    Box(
        modifier
            .softShadow(sh, color.copy(alpha = 0.5f * g.glow), 6.dp)
            .background(color, sh)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(fa(if (count > 999) "999+" else count.toString()), color = onColor(color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Pill button with glow; filled when primary. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val g = LocalGlass.current
    val sh = styledShape(PillShape)
    val base = if (primary) {
        Modifier
            .softShadow(sh, g.accent.copy(alpha = 0.55f * g.glow), 14.dp, 3.dp)
            .background(
                if (g.frame == Frame.ROUND) Brush.horizontalGradient(listOf(g.accent, g.accent2.copy(alpha = 0.9f)))
                else Brush.horizontalGradient(listOf(g.accent, g.accent)),
                sh,
            )
            .border(1.dp, Color.White.copy(alpha = 0.4f), sh)
    } else {
        Modifier.glassSurface(PillShape, elevation = 8.dp)
    }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(base)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (primary) onColor(g.accent) else g.text
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Glowing ring that spins while scanning and fills with progress (0..1). */
@Composable
fun NeonRing(progress: Float?, modifier: Modifier = Modifier, size: Dp = 180.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val g = LocalGlass.current
    val t = rememberInfiniteTransition(label = "ring")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().blur(14.dp)) {
            drawArc(g.accent2, -90f, 360f * (progress ?: 0.3f), false, style = Stroke(18f, cap = StrokeCap.Round))
        }
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            drawArc(g.edge.copy(alpha = 0.3f), 0f, 360f, false, style = Stroke(10f))
            val start = if (progress == null) spin else -90f
            drawArc(
                Brush.sweepGradient(listOf(g.accent, g.accent2, g.accent)),
                start,
                360f * (progress ?: 0.3f),
                false,
                style = Stroke(12f, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Glass top bar with a title and action icons. */
@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val g = LocalGlass.current
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassSurface(RoundedCornerShape(24.dp), elevation = 10.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
            Box(Modifier.size(6.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = g.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = g.textDim)
        }
        actions()
    }
}

@Composable
fun IconBubble(icon: ImageVector, onClick: () -> Unit, tint: Color? = null) {
    val g = LocalGlass.current
    Box(
        Modifier
            .size(42.dp)
            .glassSurface(CircleShape, elevation = 4.dp)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint ?: g.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val g = LocalGlass.current
    Row(modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = g.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Glass chip that glows when selected. */
@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, color: Color? = null, icon: ImageVector? = null) {
    val g = LocalGlass.current
    val c = g.soft(color ?: g.accent)
    val sh = styledShape(PillShape)
    val m = if (selected) {
        Modifier
            .softShadow(sh, c.copy(alpha = 0.5f * g.glow), 8.dp)
            .background(c.copy(alpha = 0.9f), sh)
            .border(1.dp, Color.White.copy(alpha = 0.45f), sh)
    } else {
        Modifier.glassSurface(PillShape, elevation = 2.dp)
    }
    Row(
        m.pressable(onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) onColor(c) else c, modifier = Modifier.size(16.dp))
            Box(Modifier.size(6.dp))
        }
        Text(text, color = if (selected) onColor(c) else g.text, style = MaterialTheme.typography.labelMedium)
    }
}

/** White on dark colors, near-black on bright ones (lime, gold). */
fun onColor(c: Color): Color = if (c.luminance() > 0.62f) Color(0xFF111315) else Color.White
