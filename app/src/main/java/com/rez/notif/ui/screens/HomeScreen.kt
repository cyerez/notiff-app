package com.rez.notif.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.CategoryCount
import com.rez.notif.data.ItemQuery
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.Badge
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.ItemList
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.NeonNumber
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.ValuableColor
import com.rez.notif.ui.components.VipColor
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.today
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass

private data class HomeData(
    val counts: List<CategoryCount>,
    val todayCount: Int,
    val valuable: Int,
    val vips: Int,
    val vipItems: Int,
    val clutter: Int,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val settings by repo.prefs.state.collectAsState()

    val data by rememberLoaded(repo) {
        val counts = repo.categoryCounts()
        val dayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        HomeData(
            counts = counts,
            todayCount = repo.count(ItemQuery(sinceMillis = dayStart)),
            valuable = repo.count(ItemQuery(onlyValuable = true, includeAds = true)),
            vips = repo.vipSummaries().size,
            vipItems = repo.count(ItemQuery(onlyVip = true, includeAds = true)),
            clutter = repo.prefs.current.cleanupRules.let { on -> repo.cleanupPreview().filterKeys { it.name in on }.values.sum() },
        )
    }
    val recent by rememberLoaded(repo) { repo.items(ItemQuery(limit = 40)) }

    val counts = data?.counts.orEmpty().associateBy { it.category }
    val unreadAll = data?.counts.orEmpty().filter { it.category != Category.ADS.id }.sumOf { it.unread }
    val main = Category.entries.filter { it.id in settings.mainCategories && it != Category.ADS }
    val others = Category.entries.filter { it.id !in settings.mainCategories && it != Category.ADS && (counts[it.id]?.total ?: 0) > 0 }
    val adCount = counts[Category.ADS.id]?.total ?: 0
    var edit by remember { mutableStateOf(false) }

    Column {
        GlassTopBar(title = "نوتیف", subtitle = today()) {
            IconBubble(Icons.Rounded.Edit, { edit = !edit }, tint = if (edit) g.accent else null)
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.Search, { nav.go(Screen.Search) })
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.BarChart, { nav.go(Screen.Stats) })
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.Settings, { nav.go(Screen.Settings) })
        }

        ItemList(
            items = recent.orEmpty(),
            empty = "هنوز چیزی نیومده.",
            editMode = edit,
            onEditModeChange = { edit = it },
        ) {
            item {
                // Three big tiles, like the "Inbox / To-Do / Notifications" sample.
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile("امروز", fa(data?.todayCount ?: 0), g.accent, Modifier.weight(1f)) {}
                    SummaryTile("خونده‌نشده", fa(unreadAll), Color(0xFF34D399), Modifier.weight(1f)) {}
                    SummaryTile("ارزشمند", fa(data?.valuable ?: 0), ValuableColor, Modifier.weight(1f)) {
                        nav.go(Screen.Special(SpecialList.VALUABLE))
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        Icons.Rounded.Favorite, VipColor, "افراد منتخب",
                        if ((data?.vips ?: 0) == 0) "هنوز کسی نیست" else fa(data?.vips ?: 0) + " نفر · " + fa(data?.vipItems ?: 0) + " مورد",
                        Modifier.weight(1f),
                    ) { nav.go(Screen.Vip) }
                    ActionTile(
                        Icons.Rounded.CleaningServices, g.accent, "پاک‌سازی",
                        if ((data?.clutter ?: 0) == 0) "همه چی مرتبه" else fa(data?.clutter ?: 0) + " مورد اضافه",
                        Modifier.weight(1f),
                    ) { nav.go(Screen.Cleanup) }
                }
            }
            if (main.isNotEmpty()) {
                item { SectionTitle("دسته‌های اصلی") }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        main.forEach { c ->
                            CategoryTile(c, counts[c.id], Modifier.weight(1f)) { nav.go(Screen.CategoryList(c.id)) }
                        }
                        if (main.size % 2 == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (others.isNotEmpty()) {
                item { SectionTitle("بقیه دسته‌ها") }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        others.forEach { c ->
                            GlassChip(
                                c.label + " " + fa(counts[c.id]?.total ?: 0),
                                selected = false,
                                color = c.tint,
                                icon = c.icon,
                                onClick = { nav.go(Screen.CategoryList(c.id)) },
                            )
                        }
                    }
                }
            }
            item {
                GlassCard(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    onClick = { nav.go(Screen.Special(SpecialList.ADS)) },
                    contentPadding = 14.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.Campaign, Category.ADS.tint, size = 36.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("تبلیغات مخفی‌شده", style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(
                                if (settings.hideAds) "نوتیف تبلیغ‌ها خودکار پاک میشه" else "مخفی کردن تبلیغ خاموشه",
                                style = MaterialTheme.typography.bodySmall,
                                color = g.textDim,
                            )
                        }
                        Text(fa(adCount), style = MaterialTheme.typography.titleMedium, color = Category.ADS.tint)
                    }
                }
            }
            item { SectionTitle("تازه‌ها") }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick, contentPadding = 12.dp) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = g.text, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        NeonNumber(value, color, size = 58.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, color: Color, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(22.dp), onClick = onClick, contentPadding = 12.dp, glow = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon, color, size = 36.dp)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = g.text, maxLines = 1)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Category, count: CategoryCount?, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(c.icon, c.tint, size = 40.dp)
            Spacer(Modifier.weight(1f))
            Badge(count?.unread ?: 0, c.tint)
        }
        Spacer(Modifier.height(12.dp))
        Text(c.label, style = MaterialTheme.typography.titleMedium, color = g.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(fa(count?.total ?: 0) + " مورد", style = MaterialTheme.typography.bodySmall, color = g.textDim)
    }
}
