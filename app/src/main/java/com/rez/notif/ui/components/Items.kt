package com.rez.notif.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.Item
import com.rez.notif.data.Source
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/** One SMS or notification in a list. */
@Composable
fun ItemRow(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val cat = Category.of(item.category)
    GlassCard(
        modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NeonIcon(cat.icon, cat.tint, size = 38.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.source == Source.SMS) item.sender else item.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = g.text,
                        fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.isValuable || item.isStarred) {
                        Icon(Icons.Rounded.Star, null, tint = ValuableColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                    }
                    if (item.simSlot > 0) {
                        Text("سیم " + fa(item.simSlot), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(shortTime(item.timestamp), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                    if (!item.isRead) {
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.size(8.dp).background(g.accent2, CircleShape))
                    }
                }
                if (item.source == Source.NOTIFICATION && item.title.isNotBlank()) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, color = g.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Adds rows to a LazyColumn. */
fun LazyListScope.itemRows(items: List<Item>, onClick: (Item) -> Unit) {
    items(items, key = { it.id }) { item -> ItemRow(item, onClick = { onClick(item) }) }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier.padding(16.dp).fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = g.textDim)
    }
}

/** A full list with the detail sheet built in. */
@Composable
fun ItemList(
    items: List<Item>,
    modifier: Modifier = Modifier,
    empty: String = "چیزی اینجا نیست.",
    header: LazyListScope.() -> Unit = {},
) {
    var open by remember { mutableStateOf<Item?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        header()
        if (items.isEmpty()) item { EmptyState(empty) }
        itemRows(items) { open = it }
    }
    open?.let { ItemSheet(it, onDismiss = { open = null }) }
}

/**
 * Detail of one item: full text, open/reply in the source app, star, and change category.
 * Changing category with "all from this sender" teaches the app for the future.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(item: Item, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var starred by remember(item.id) { mutableStateOf(item.isStarred) }
    var current by remember(item.id) { mutableStateOf(Category.of(item.category)) }
    var wholeSender by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(item.id) { repo.markRead(listOf(item)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = if (g.isDark) Color(0xFF161B2B) else Color(0xFFF8FAFC),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonIcon(current.icon, current.tint, size = 48.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (item.source == Source.SMS) item.sender else item.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = g.text,
                    )
                    Text(
                        fullTime(item.timestamp) + (if (item.simSlot > 0) "  ·  سیم " + fa(item.simSlot) else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = g.textDim,
                    )
                }
                IconBubble(
                    if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    onClick = {
                        scope.launch { repo.toggleStar(item.copy(isStarred = starred)) }
                        starred = !starred
                    },
                    tint = if (starred) ValuableColor else null,
                )
            }
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                if (item.source == Source.NOTIFICATION && item.title.isNotBlank()) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = g.text)
                    Spacer(Modifier.height(6.dp))
                }
                SelectionContainer {
                    Text(item.body, style = MaterialTheme.typography.bodyLarge, color = g.text)
                }
            }
            Spacer(Modifier.height(16.dp))
            GlassButton(
                text = if (item.source == Source.SMS) "باز کردن گفتگو و جواب" else "باز کردن در " + item.appName,
                icon = Icons.AutoMirrored.Rounded.Reply,
                onClick = { openFailed = !repo.openSource(context, item) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (openFailed) {
                Text("این اپ باز نشد.", color = Category.ADS.tint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text("دسته", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    GlassChip(c.label, selected = c == current, color = c.tint, icon = c.icon, onClick = {
                        current = c
                        scope.launch { repo.setCategory(item, c, wholeSender) }
                    })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(
                    checked = wholeSender,
                    onCheckedChange = {
                        wholeSender = it
                        if (it) scope.launch { repo.setCategory(item, current, true) }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = g.accent),
                )
                Text(
                    if (item.source == Source.SMS) "همه پیام‌های این فرستنده (اپ یاد می‌گیره)" else "همه نوتیف‌های این اپ (اپ یاد می‌گیره)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.text,
                )
            }
        }
    }
}
