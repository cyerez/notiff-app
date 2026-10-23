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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.ui.theme.LocalGlass

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
        Canvas(Modifier.fillMaxSize().blur(90.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(g.blobs[0], radius = w * 0.55f, center = Offset(w * (0.15f + 0.2f * drift), h * (0.12f + 0.06f * drift)))
            drawCircle(g.blobs[1], radius = w * 0.5f, center = Offset(w * (0.95f - 0.25f * drift), h * (0.45f + 0.1f * drift)))
            drawCircle(g.blobs[2], radius = w * 0.45f, center = Offset(w * (0.3f + 0.3f * drift), h * (0.9f - 0.08f * drift)))
        }
        content()
    }
}

/** Frosted glass panel: translucent fill, bright thin edge, soft shadow. */
fun Modifier.glass(
    shape: Shape = GlassShape,
    glow: Color? = null,
    elevation: Dp = 14.dp,
): Modifier = this.then(
    Modifier
        // Neon glow when a color is given, otherwise a soft neutral shadow.
        .shadow(elevation, shape, clip = false, ambientColor = glow ?: Color.Black, spotColor = glow ?: Color.Black),
)

@Composable
fun Modifier.glassSurface(shape: Shape = GlassShape, glow: Color? = null, elevation: Dp = 14.dp): Modifier {
    val g = LocalGlass.current
    return this
        .glass(shape, glow, elevation)
        .background(Brush.verticalGradient(listOf(g.glassTop, g.glassBottom)), shape)
        .border(
            1.dp,
            Brush.linearGradient(listOf(g.edge, g.edge.copy(alpha = g.edge.alpha * 0.25f), (glow ?: g.edge).copy(alpha = 0.5f))),
            shape,
        )
}

/** Squeezes a little when pressed (the "tactile" feel of the sample designs). */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
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
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    glow: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.glassSurface(shape, glow)
    Column(
        (if (onClick != null) m.pressable(onClick) else m).padding(contentPadding),
        content = content,
    )
}

/** Raised colored square with a number, like the "Inbox / To-Do / Notifications" tiles. */
@Composable
fun NeonNumber(value: String, color: Color, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(18.dp, RoundedCornerShape(size / 3.2f), ambientColor = color, spotColor = color)
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
fun NeonIcon(icon: ImageVector, color: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(12.dp, CircleShape, ambientColor = color, spotColor = color)
            .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.75f))), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

/** Small glowing count badge. */
@Composable
fun Badge(count: Int, color: Color, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .shadow(8.dp, PillShape, ambientColor = color, spotColor = color)
            .background(color, PillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(fa(if (count > 999) "999+" else count.toString()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    val base = if (primary) {
        Modifier
            .shadow(16.dp, PillShape, ambientColor = g.accent, spotColor = g.accent)
            .background(Brush.horizontalGradient(listOf(g.accent, g.accent2.copy(alpha = 0.9f))), PillShape)
            .border(1.dp, Color.White.copy(alpha = 0.4f), PillShape)
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
        val color = if (primary) Color.White else g.text
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
    val c = color ?: g.accent
    val m = if (selected) {
        Modifier
            .shadow(10.dp, PillShape, ambientColor = c, spotColor = c)
            .background(c.copy(alpha = 0.9f), PillShape)
            .border(1.dp, Color.White.copy(alpha = 0.45f), PillShape)
    } else {
        Modifier.glassSurface(PillShape, elevation = 2.dp)
    }
    Row(
        m.pressable(onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else c, modifier = Modifier.size(16.dp))
            Box(Modifier.size(6.dp))
        }
        Text(text, color = if (selected) Color.White else g.text, style = MaterialTheme.typography.labelMedium)
    }
}
