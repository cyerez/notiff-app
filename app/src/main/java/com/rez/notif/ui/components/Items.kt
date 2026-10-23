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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.rez.notif.classify.VipMatcher
import com.rez.notif.data.Item
import com.rez.notif.data.Source
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One SMS or notification in a list. */
@Composable
fun ItemRow(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** null when not selecting; true/false shows a tick circle. */
    selected: Boolean? = null,
) {
    val g = LocalGlass.current
    val cat = Category.of(item.category)
    GlassCard(
        modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        glow = if (selected == true) g.accent else null,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (selected != null) {
                Icon(
                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    null,
                    tint = if (selected) g.accent else g.textDim,
                    modifier = Modifier.padding(top = 8.dp, end = 10.dp).size(24.dp),
                )
            }
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
                    if (item.vipId > 0) {
                        Icon(Icons.Rounded.Favorite, null, tint = VipColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                    }
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

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier.padding(16.dp).fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = g.textDim)
    }
}

/**
 * A full list with the detail sheet built in.
 * Long-press an item (or turn on [editMode]) to select several and delete them; a short undo bar follows.
 */
@Composable
fun ItemList(
    items: List<Item>,
    modifier: Modifier = Modifier,
    empty: String = "چیزی اینجا نیست.",
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
    header: LazyListScope.() -> Unit = {},
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Item?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var undo by remember { mutableStateOf<List<Long>?>(null) }
    val selecting = editMode || selected.isNotEmpty()

    fun stopSelecting() {
        selected = emptySet()
        onEditModeChange(false)
    }

    LaunchedEffect(undo) { if (undo != null) { delay(6000); undo = null } }

    Box(modifier) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            header()
            if (items.isEmpty()) item { EmptyState(empty) }
            items(items, key = { it.id }) { item ->
                ItemRow(
                    item,
                    selected = if (selecting) item.id in selected else null,
                    onClick = {
                        if (selecting) {
                            selected = if (item.id in selected) selected - item.id else selected + item.id
                        } else {
                            open = item
                        }
                    },
                    onLongClick = { selected = selected + item.id },
                )
            }
        }

        if (selecting) {
            BottomBar(Modifier.align(Alignment.BottomCenter)) {
                Text(fa(selected.size) + " انتخاب", color = g.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GlassButton("همه", onClick = { selected = items.map { it.id }.toSet() }, primary = false)
                Spacer(Modifier.size(6.dp))
                GlassButton("حذف", icon = Icons.Rounded.Delete, enabled = selected.isNotEmpty(), onClick = {
                    val chosen = items.filter { it.id in selected }
                    scope.launch { undo = repo.delete(chosen) }
                    stopSelecting()
                })
                Spacer(Modifier.size(6.dp))
                IconBubble(Icons.Rounded.Close, { stopSelecting() })
            }
        } else if (undo != null) {
            BottomBar(Modifier.align(Alignment.BottomCenter)) {
                Text(fa(undo!!.size) + " مورد حذف شد", color = g.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GlassButton("برگردون", onClick = {
                    val ids = undo!!
                    undo = null
                    scope.launch { repo.undoDelete(ids) }
                }, primary = false)
            }
        }
    }
    open?.let { ItemSheet(it, onDismiss = { open = null }, onDeleted = { ids -> undo = ids }) }
}

/** Floating glass bar at the bottom of a list. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(26.dp), elevation = 18.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Detail of one item: full text, open/reply in the source app, star, and change category.
 * Changing category with "all from this sender" teaches the app for the future.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(item: Item, onDismiss: () -> Unit, onDeleted: (List<Long>) -> Unit = {}) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var starred by remember(item.id) { mutableStateOf(item.isStarred) }
    var current by remember(item.id) { mutableStateOf(Category.of(item.category)) }
    var wholeSender by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    var vipAdded by remember { mutableStateOf(item.vipId > 0) }
    var confirmSenderDelete by remember { mutableStateOf(false) }
    val who = if (item.source == Source.SMS) item.sender else item.appName

    LaunchedEffect(item.id) { repo.markRead(listOf(item)) }

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

            Spacer(Modifier.height(18.dp))
            Text("کارها", style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vipAdded) {
                    GlassButton("افزودن به افراد منتخب", icon = Icons.Rounded.Favorite, primary = false, onClick = {
                        vipAdded = true
                        val name = if (item.source == Source.SMS) item.sender else item.title
                        val numbers = listOfNotNull(VipMatcher.key(item.sender))
                        scope.launch { repo.addVip(name, numbers) }
                    })
                }
                GlassButton("حذف", icon = Icons.Rounded.Delete, primary = false, onClick = {
                    scope.launch {
                        onDeleted(repo.delete(listOf(item)))
                        onDismiss()
                    }
                })
                GlassButton("حذف همه از «" + who.take(18) + "»", icon = Icons.Rounded.DeleteSweep, primary = false, onClick = {
                    confirmSenderDelete = true
                })
            }
            Text(
                "حذف فقط از این اپه؛ پیامک‌های خود گوشی دست نمی‌خورن.",
                style = MaterialTheme.typography.bodySmall,
                color = g.textDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (confirmSenderDelete) {
        AlertDialog(
            onDismissRequest = { confirmSenderDelete = false },
            title = { Text("حذف همه از «$who»؟") },
            text = { Text("همه پیامک‌ها یا نوتیف‌های این فرستنده از اپ حذف میشن. تا چند ثانیه بعد می‌تونی برگردونی.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSenderDelete = false
                    scope.launch {
                        onDeleted(repo.deleteFromSender(item))
                        onDismiss()
                    }
                }) { Text("حذف کن", color = Category.ADS.tint) }
            },
            dismissButton = { TextButton(onClick = { confirmSenderDelete = false }) { Text("نه") } },
        )
    }
}
