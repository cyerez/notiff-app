package com.rez.notif.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.ItemQuery
import com.rez.notif.data.Source
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.ItemList
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.ValuableColor
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

enum class SpecialList(val title: String, val hint: String) {
    VALUABLE("ارزشمند", "هدیه، تخفیف، ایمیل خوب و هر چی ستاره زدی. اینجا می‌مونن حتی اگه نوتیفشون پاک بشه."),
    ADS("تبلیغات مخفی‌شده", "اینجا رو گاهی نگاه کن. اگه چیزی اشتباهی اینجا اومده، بازش کن و دسته‌ش رو عوض کن."),
}

/** Source filter shared by list screens. */
private enum class SourceFilter(val label: String, val source: Source?) {
    ALL("همه", null), SMS("پیامک", Source.SMS), NOTIF("نوتیف", Source.NOTIFICATION)
}

@Composable
fun CategoryScreen(categoryId: String, nav: Navigator) {
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val c = Category.of(categoryId)
    var filter by remember { mutableStateOf(SourceFilter.ALL) }
    val items by rememberLoaded(repo, categoryId, filter) {
        repo.items(ItemQuery(category = categoryId, source = filter.source, limit = 500))
    }
    val list = items.orEmpty()
    var edit by remember { mutableStateOf(false) }

    Column {
        GlassTopBar(title = c.label, subtitle = fa(list.size) + " مورد", onBack = nav::back) {
            IconBubble(Icons.Rounded.Edit, { edit = !edit }, tint = if (edit) c.tint else null)
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.DoneAll, { scope.launch { repo.markRead(list) } })
        }
        ItemList(list, empty = "در این دسته چیزی نیست.", editMode = edit, onEditModeChange = { edit = it }) {
            item { Header(c.icon, c.tint, c.description) }
            item { SourceFilters(filter) { filter = it } }
        }
    }
}

@Composable
fun SpecialListScreen(kind: SpecialList, nav: Navigator) {
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    var filter by remember { mutableStateOf(SourceFilter.ALL) }
    val items by rememberLoaded(repo, kind, filter) {
        when (kind) {
            SpecialList.VALUABLE -> repo.items(ItemQuery(onlyValuable = true, includeAds = true, source = filter.source, limit = 500))
            SpecialList.ADS -> repo.items(ItemQuery(onlyAds = true, source = filter.source, limit = 500))
        }
    }
    val list = items.orEmpty()
    val (icon, color) = when (kind) {
        SpecialList.VALUABLE -> Icons.Rounded.Star to ValuableColor
        SpecialList.ADS -> Icons.Rounded.Campaign to Category.ADS.tint
    }
    var edit by remember { mutableStateOf(false) }
    Column {
        GlassTopBar(title = kind.title, subtitle = fa(list.size) + " مورد", onBack = nav::back) {
            IconBubble(Icons.Rounded.Edit, { edit = !edit }, tint = if (edit) color else null)
        }
        ItemList(list, empty = "فعلاً چیزی نیست.", editMode = edit, onEditModeChange = { edit = it }) {
            item { Header(icon, color, kind.hint) }
            item { SourceFilters(filter) { filter = it } }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, color: Color, text: String) {
    val g = LocalGlass.current
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), glow = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon, color, size = 46.dp)
            Spacer(Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = g.text)
        }
    }
}

@Composable
private fun SourceFilters(current: SourceFilter, onPick: (SourceFilter) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceFilter.entries.forEach { f -> GlassChip(f.label, selected = f == current, onClick = { onPick(f) }) }
    }
}
