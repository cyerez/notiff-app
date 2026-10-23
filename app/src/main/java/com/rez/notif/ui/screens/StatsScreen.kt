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
            item { SectionTitle(t("هفت روز اخیر")) }
            item { WeekChart(s) }
            item { SectionTitle(t("سهم دسته‌ها")) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
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
