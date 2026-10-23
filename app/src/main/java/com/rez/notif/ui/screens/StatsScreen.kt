package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.AppCount
import com.rez.notif.data.Stats
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.NeonNumber
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.ValuableColor
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.weekday
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass

@Composable
fun StatsScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val stats by rememberLoaded(repo) { repo.stats() }

    Column {
        GlassTopBar(title = t("آمار"), onBack = nav::back)
        val s = stats ?: return@Column
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(t("پیامک"), s.sms, Color(0xFF34D399), Modifier.weight(1f))
                    Tile(t("نوتیف"), s.notifications, g.accent, Modifier.weight(1f))
                    Tile(t("تبلیغ"), s.ads, Category.ADS.tint, Modifier.weight(1f))
                    Tile(t("ارزشمند"), s.valuable, ValuableColor, Modifier.weight(1f))
                }
            }
            item { WeeklyCard(nav) }
            item { SectionTitle(t("هفت روز اخیر")) }
            item { WeekChart(s) }
            item { SectionTitle(t("۳۰ روز اخیر")) }
            item { MonthChart(s.last30Days) }
            item { SectionTitle(t("ساعت‌های شلوغ")) }
            item { HourChart(s.byHour) }
            if (s.sim1 > 0 && s.sim2 > 0) {
                item { SectionTitle(t("پیامک هر سیم‌کارت")) }
                item {
                    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        val max = maxOf(s.sim1, s.sim2)
                        BarRow(t("سیم ۱"), s.sim1, max, g.accent)
                        BarRow(t("سیم ۲"), s.sim2, max, g.accent2)
                    }
                }
            }
            item { SectionTitle(t("سهم دسته‌ها")) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Donut(s)
                    Spacer(Modifier.height(12.dp))
                    val max = s.byCategory.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
                    s.byCategory.forEach { cc ->
                        val c = Category.of(cc.category)
                        BarRow(t(c.label), cc.total, max, c.tint)
                    }
                }
            }
            if (s.topApps.isNotEmpty()) {
                item { SectionTitle(t("پرنوتیف‌ترین اپ‌ها")) }
                item { RankCard(s.topApps) }
            }
            if (s.topSenders.isNotEmpty()) {
                item { SectionTitle(t("پرپیامک‌ترین فرستنده‌ها")) }
                item { RankCard(s.topSenders) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: Int, color: Color, modifier: Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(22.dp), contentPadding = 10.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = g.text, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        NeonNumber(compact(value), color, size = 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun compact(n: Int): String = when {
    n >= 10_000 -> fa(n / 1000) + "k"
    else -> fa(n)
}

/** Bars per day: full height = all items, red part = ads. Grows in with a short animation. */
@Composable
private fun WeekChart(s: Stats) {
    val g = LocalGlass.current
    val grow = remember { Animatable(if (g.animations) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }
    val max = s.last7Days.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            s.last7Days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(fa(d.total), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                    Canvas(Modifier.width(18.dp).height(110.dp)) {
                        val h = size.height * d.total / max * grow.value
                        val adH = size.height * d.ads / max * grow.value
                        drawRoundRect(
                            Brush.verticalGradient(listOf(g.accent2, g.accent)),
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h),
                            cornerRadius = CornerRadius(size.width / 2),
                        )
                        if (adH > 0f) {
                            drawRoundRect(
                                Category.ADS.tint.copy(alpha = 0.9f),
                                topLeft = Offset(0f, size.height - adH),
                                size = Size(size.width, adH),
                                cornerRadius = CornerRadius(size.width / 2),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            s.last7Days.forEach { d ->
                Text(
                    weekday(d.dayStart).take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = g.textDim,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(g.accent, RoundedCornerShape(5.dp)))
            Text(t("  همه   "), style = MaterialTheme.typography.labelSmall, color = g.textDim)
            Box(Modifier.size(10.dp).background(Category.ADS.tint, RoundedCornerShape(5.dp)))
            Text(t("  تبلیغ"), style = MaterialTheme.typography.labelSmall, color = g.textDim)
        }
    }
}

@Composable
private fun BarRow(label: String, value: Int, max: Int, color: Color) {
    val g = LocalGlass.current
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = g.text, modifier = Modifier.width(96.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(g.edge.copy(alpha = 0.25f))) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.toFloat() / max)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
            )
        }
        Text(fa(value), style = MaterialTheme.typography.labelMedium, color = g.textDim, modifier = Modifier.padding(start = 8.dp).width(48.dp))
    }
}

@Composable
private fun RankCard(list: List<AppCount>) {
    val g = LocalGlass.current
    val max = list.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        list.forEach { a ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(fa(a.total), style = MaterialTheme.typography.labelLarge, color = g.accent)
            }
            Box(
                Modifier
                    .fillMaxWidth(a.total.toFloat() / max)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(g.accent, g.accent2))),
            )
        }
    }
}

/** Smooth filled line of the last 30 days. */
@Composable
private fun MonthChart(days: List<Int>) {
    val g = LocalGlass.current
    if (days.isEmpty()) return
    val grow = remember { Animatable(if (g.animations) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800)) }
    val max = days.max().coerceAtLeast(1)
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val step = size.width / (days.size - 1).coerceAtLeast(1)
            fun y(v: Int) = size.height - size.height * 0.9f * v / max * grow.value
            val line = androidx.compose.ui.graphics.Path()
            days.forEachIndexed { i, v ->
                val x = i * step
                if (i == 0) line.moveTo(x, y(v)) else {
                    val px = (i - 1) * step
                    line.cubicTo(px + step / 2, y(days[i - 1]), x - step / 2, y(v), x, y(v))
                }
            }
            val area = androidx.compose.ui.graphics.Path().apply {
                addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(area, Brush.verticalGradient(listOf(g.accent.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, Brush.horizontalGradient(listOf(g.accent2, g.accent)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(t("۳۰ روز پیش"), style = MaterialTheme.typography.labelSmall, color = g.textDim, modifier = Modifier.weight(1f))
            Text(t("امروز"), style = MaterialTheme.typography.labelSmall, color = g.textDim)
        }
    }
}

/** 24 thin bars: when in the day messages come. */
@Composable
private fun HourChart(hours: List<Int>) {
    val g = LocalGlass.current
    val max = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        // Always morning on the left, like a clock line, in both languages.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column {
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val w = size.width / 24f
                    hours.forEachIndexed { h, v ->
                        val bh = size.height * v / max
                        drawRoundRect(
                            if (h in 0..5 || h >= 23) g.accent2.copy(alpha = 0.6f) else g.accent,
                            topLeft = Offset(h * w + w * 0.2f, size.height - bh),
                            size = Size(w * 0.6f, bh),
                            cornerRadius = CornerRadius(w * 0.3f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf(0, 6, 12, 18).forEach { h ->
                        Text(fa(h) + ":۰۰".let { if (com.rez.notif.i18n.I18n.isFa) it else ":00" }, style = MaterialTheme.typography.labelSmall, color = g.textDim, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Ring of category shares, biggest first; the total in the middle. */
@Composable
private fun Donut(s: Stats) {
    val g = LocalGlass.current
    val total = s.byCategory.sumOf { it.total }.coerceAtLeast(1)
    val grow = remember { Animatable(if (g.animations) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900)) }
    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            var start = -90f
            val stroke = 26f
            s.byCategory.forEach { cc ->
                val sweep = 360f * cc.total / total * grow.value
                drawArc(
                    Category.of(cc.category).tint,
                    start, (sweep - 1.5f).coerceAtLeast(0f), false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(size.width - 2 * stroke, size.height - 2 * stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(compact(total), style = MaterialTheme.typography.headlineSmall, color = g.text)
            Text(t(" مورد").trim(), style = MaterialTheme.typography.labelSmall, color = g.textDim)
        }
    }
}

/** This week in numbers, and the noisiest apps with a one-tap "quiet it" rule. */
@Composable
private fun WeeklyCard(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val w by rememberLoaded(repo) { repo.weekly() }
    val week = w ?: return
    com.rez.notif.ui.components.GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), glow = g.accent, contentPadding = 14.dp) {
        Text(t("این هفته"), style = MaterialTheme.typography.titleSmall, color = g.text)
        Text(
            fa(week.total) + t(" مورد رسید؛ ") + fa(week.hidden) + t(" نوتیف رو از نوار گوشی جمع کردم و ") + fa(week.ads) + t(" تبلیغ رو مخفی کردم."),
            style = MaterialTheme.typography.bodyMedium,
            color = g.textDim,
        )
        if (week.noisy.isNotEmpty()) {
            Text(t("پرسروصداترین‌ها:"), style = MaterialTheme.typography.labelMedium, color = g.text, modifier = Modifier.padding(top = 8.dp))
            week.noisy.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.appName + "  ·  " + fa(a.total), style = MaterialTheme.typography.bodyMedium, color = g.text, modifier = Modifier.weight(1f))
                    com.rez.notif.ui.components.GlassChip(t("بی‌صدا کن"), false, {
                        nav.go(
                            com.rez.notif.ui.Screen.RuleEdit(
                                com.rez.notif.classify.Rule(name = a.appName, apps = setOf(a.packageName), action = com.rez.notif.classify.RuleAction.QUIET),
                            ),
                        )
                    })
                }
            }
        }
    }
}
