package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.ItemQuery
import com.rez.notif.data.Source
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.ItemList
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.glassSurface
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    var text by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<Source?>(null) }
    var sim by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var withAds by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Wait until typing pauses before searching.
    LaunchedEffect(text) { delay(250); query = text }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val active = query.isNotBlank() || category != null || source != null || sim != null
    val results by rememberLoaded(repo, query, source, sim, category, withAds) {
        if (!active) emptyList() else repo.items(
            ItemQuery(text = query, source = source, simSlot = sim, category = category, includeAds = withAds, limit = 300),
        )
    }

    Column {
        GlassTopBar(title = t("جستجو"), onBack = nav::back)
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(t("متن، فرستنده یا اسم اپ…")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = g.text,
                unfocusedTextColor = g.text,
                cursorColor = g.accent,
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .glassSurface(RoundedCornerShape(22.dp), glow = g.accent, elevation = 8.dp)
                .focusRequester(focus),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(t("همه"), source == null, { source = null })
            GlassChip(t("پیامک"), source == Source.SMS, { source = Source.SMS })
            GlassChip(t("نوتیف"), source == Source.NOTIFICATION, { source = Source.NOTIFICATION })
            GlassChip(t("سیم ۱"), sim == 1, { sim = if (sim == 1) null else 1 })
            GlassChip(t("سیم ۲"), sim == 2, { sim = if (sim == 2) null else 2 })
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { GlassChip(t("با تبلیغ‌ها"), withAds, { withAds = !withAds }, color = Category.ADS.tint) }
            Category.entries.filter { it != Category.ADS }.forEach { c ->
                item {
                    GlassChip(t(c.label), category == c.id, { category = if (category == c.id) null else c.id }, color = c.tint, icon = c.icon)
                }
            }
        }
        val list = results.orEmpty()
        if (active) {
            Text(
                fa(list.size) + t(" نتیجه"),
                style = MaterialTheme.typography.labelMedium,
                color = g.textDim,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
        ItemList(list, empty = if (active) t("چیزی پیدا نشد.") else t("یک کلمه بنویس یا یک فیلتر انتخاب کن."))
    }
}
