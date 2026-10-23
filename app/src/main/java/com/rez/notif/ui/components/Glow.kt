package com.rez.notif.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft shadow or neon glow drawn by hand with a blur filter, only OUTSIDE the shape.
 *
 * We do not use Modifier.shadow here: elevation shadows show through translucent glass
 * as hard white/grey rectangles (the bug seen in v0.1).
 */
fun Modifier.softShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp = 0.dp): Modifier = drawBehind {
    val radius = blur.toPx()
    if (radius <= 0f || color.alpha <= 0f) return@drawBehind
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }.asAndroidPath()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        c.save()
        c.clipOutPath(path)
        c.translate(0f, offsetY.toPx())
        c.drawPath(path, paint)
        c.restore()
    }
}
