package com.rez.notif.ui.components

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.DoneAll
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.automirrored.rounded.Send
import com.rez.notif.i18n.t
import com.rez.notif.sms.ContactNames
import com.rez.notif.notif.CodeNotifier
import com.rez.notif.classify.Classifier
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.collectAsState
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

/**
 * Swipe a row to the right = "read it", to the left = "remind me in an hour". The row springs back.
 * Directions are physical (screen left/right), so they behave the same in Persian and English.
 */
@Composable
fun SwipeActions(item: Item, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 110.dp.toPx() }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { d -> scope.launch { offset.snapTo(offset.value + d) } }
    Box {
        val x = offset.value
        if (kotlin.math.abs(x) > 1f) {
            val right = x > 0
            val color = if (right) Category.PERSONAL.tint else Category.OTP.tint
            val armed = kotlin.math.abs(x) > threshold
            Box(
                Modifier.matchParentSize().padding(horizontal = 14.dp, vertical = 3.dp)
                    .background(color.copy(alpha = if (armed) 0.45f else 0.2f), RoundedCornerShape(18.dp)).padding(horizontal = 22.dp),
                contentAlignment = if (right) androidx.compose.ui.AbsoluteAlignment.CenterLeft else androidx.compose.ui.AbsoluteAlignment.CenterRight,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (right) Icons.Rounded.DoneAll else Icons.Rounded.Alarm, null, tint = color)
                    Spacer(Modifier.size(8.dp))
                    Text(if (right) t("خوندم") else t("۱ ساعت دیگه"), color = g.text, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Box(
            Modifier
                .absoluteOffset { androidx.compose.ui.unit.IntOffset(offset.value.toInt(), 0) }
                .draggable(
                    drag,
                    androidx.compose.foundation.gestures.Orientation.Horizontal,
                    // draggable gives raw screen deltas (unlike scrollable, it does not flip for right-to-left).
                    onDragStopped = {
                        val v = offset.value
                        when {
                            v > threshold -> scope.launch {
                                repo.markRead(listOf(item))
                                android.widget.Toast.makeText(context, t("خونده شد"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            v < -threshold -> scope.launch {
                                repo.markRead(listOf(item))
                                repo.remind(item, System.currentTimeMillis() + 3_600_000L)
                                android.widget.Toast.makeText(context, t("یک ساعت دیگه یادت میندازم"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        offset.animateTo(0f)
                    },
                ),
        ) { content() }
    }
}

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
        modifier.padding(horizontal = 14.dp, vertical = 3.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        glow = if (selected == true) g.accent else null,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = 11.dp,
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
            NeonIcon(cat.icon, cat.tint, size = 32.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Who it is from: the contact for SMS; for apps the chat/person title, with the app's name small.
                    val isNotif = item.source == Source.NOTIFICATION
                    Text(
                        if (!isNotif) senderLabel(item.sender) else item.title.ifBlank { item.appName },
                        style = MaterialTheme.typography.titleSmall,
                        color = g.text,
                        fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isNotif && item.title.isNotBlank()) {
                        Text(
                            item.appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = g.textDim,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp).widthIn(max = 90.dp),
                        )
                    }
                    if (item.vipId > 0) {
                        Icon(Icons.Rounded.Favorite, null, tint = VipColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                    }
                    if (item.isValuable || item.isStarred) {
                        Icon(Icons.Rounded.Star, null, tint = ValuableColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                    }
                    if (item.simSlot > 0) {
                        Text(t("سیم ") + fa(item.simSlot), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(shortTime(item.timestamp), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                    if (!item.isRead) {
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.size(8.dp).background(g.accent2, CircleShape))
                    }
                }
                Text(
                    item.body.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected == null) CopyCodeChip(item, Modifier.padding(top = 6.dp))
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
    empty: String = t("چیزی اینجا نیست."),
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
    /** Extra rows above the list; call the given function to open an item's detail sheet. */
    header: LazyListScope.(openItem: (Item) -> Unit) -> Unit = {},
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
            header { open = it }
            if (items.isEmpty()) item { EmptyState(empty) }
            items(items, key = { it.id }) { item ->
                SwipeActions(item, enabled = !selecting) { ItemRow(
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
                ) }
            }
        }

        if (selecting) {
            BottomBar(Modifier.align(Alignment.BottomCenter)) {
                Text(fa(selected.size) + t(" انتخاب"), color = g.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GlassButton(t("همه"), onClick = { selected = items.map { it.id }.toSet() }, primary = false)
                Spacer(Modifier.size(6.dp))
                GlassButton(t("حذف"), icon = Icons.Rounded.Delete, enabled = selected.isNotEmpty(), onClick = {
                    val chosen = items.filter { it.id in selected }
                    scope.launch { undo = repo.delete(chosen) }
                    stopSelecting()
                })
                Spacer(Modifier.size(6.dp))
                IconBubble(Icons.Rounded.Close, { stopSelecting() })
            }
        } else if (undo != null) {
            BottomBar(Modifier.align(Alignment.BottomCenter)) {
                Text(fa(undo!!.size) + t(" مورد حذف شد"), color = g.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GlassButton(t("برگردون"), onClick = {
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
    val who = if (item.source == Source.SMS) senderLabel(item.sender) else item.appName

    LaunchedEffect(item.id) {
        repo.markRead(listOf(item))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.rez.notif.notif.Reminders.seen(context, item.id) }
    }
    var remindAt by remember(item.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(item.id) { remindAt = repo.reminderAt(item.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
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
                        if (item.source == Source.SMS) senderLabel(item.sender) else item.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = g.text,
                    )
                    Text(
                        fullTime(item.timestamp) + (if (item.simSlot > 0) t("  ·  سیم ") + fa(item.simSlot) else ""),
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
            codeOf(item)?.let { code ->
                GlassButton(
                    text = t("کپی کد ") + fa(code),
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { CodeNotifier.copy(context, code) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            GlassButton(
                text = if (item.source == Source.SMS) t("باز کردن گفتگو و جواب") else t("باز کردن در ") + item.appName,
                primary = codeOf(item) == null,
                icon = Icons.AutoMirrored.Rounded.Reply,
                onClick = {
                    // As the phone's SMS app, Notif opens its own conversation screen.
                    if (item.source == Source.SMS && com.rez.notif.sms.DefaultSms.isDefault(context)) {
                        com.rez.notif.ui.OpenRequests.open(com.rez.notif.ui.Screen.Conversation(item.sender))
                    } else {
                        openFailed = !repo.openSource(context, item)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (repo.canReply(item)) {
                Spacer(Modifier.height(12.dp))
                QuickReply(item)
            }
            if (openFailed) {
                Text(t("این اپ باز نشد."), color = Category.ADS.tint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(t("بعداً یادم بنداز"), style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            val at = remindAt
            if (at != null) {
                GlassChip(t("یادآوری: ") + fullTime(at) + "  ✕", selected = true, onClick = {
                    remindAt = null
                    scope.launch { repo.cancelReminder(item) }
                })
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.rez.notif.notif.Reminders.choices().forEach { (label, time) ->
                        GlassChip(label, selected = false, icon = Icons.Rounded.Alarm, onClick = {
                            remindAt = time
                            scope.launch { repo.remind(item, time) }
                        })
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(t("دسته"), style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    GlassChip(t(c.label), selected = c == current, color = c.tint, icon = c.icon, onClick = {
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
                    if (item.source == Source.SMS) t("همه پیام‌های این فرستنده (اپ یاد می‌گیره)") else t("همه نوتیف‌های این اپ (اپ یاد می‌گیره)"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.text,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(t("کارها"), style = MaterialTheme.typography.titleMedium, color = g.text)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vipAdded) {
                    GlassButton(t("افزودن به افراد منتخب"), icon = Icons.Rounded.Favorite, primary = false, onClick = {
                        vipAdded = true
                        // A saved contact is added once, with the contact's name and all of their numbers.
                        val contact = if (item.source == Source.SMS) ContactNames.nameOf(item.sender) else null
                        val name = contact ?: if (item.source == Source.SMS) item.sender else item.title
                        val numbers = (listOfNotNull(VipMatcher.key(item.sender)) +
                            (contact?.let { c -> ContactNames.names.value.filterValues { it == c }.keys } ?: emptySet())).distinct()
                        scope.launch { repo.addVip(name, numbers) }
                    })
                }
                GlassButton(t("ساختن قانون"), icon = Icons.Rounded.Tune, primary = false, onClick = {
                    val rule = com.rez.notif.classify.Rule(
                        name = who,
                        apps = setOf(if (item.source == Source.SMS) "sms" else item.packageName),
                        sender = if (item.source == Source.SMS) item.sender else "",
                    )
                    onDismiss()
                    com.rez.notif.ui.OpenRequests.open(com.rez.notif.ui.Screen.RuleEdit(rule))
                })
                GlassButton(t("حذف"), icon = Icons.Rounded.Delete, primary = false, onClick = {
                    scope.launch {
                        onDeleted(repo.delete(listOf(item)))
                        onDismiss()
                    }
                })
                GlassButton(t("حذف همه از «") + who.take(18) + "»", icon = Icons.Rounded.DeleteSweep, primary = false, onClick = {
                    confirmSenderDelete = true
                })
            }
            Text(
                if (repo.prefs.current.phoneDelete && com.rez.notif.sms.DefaultSms.isDefault(context)) {
                    t("پیامک از خود گوشی هم پاک میشه (تا چند ثانیه می‌تونی برگردونی).")
                } else {
                    t("حذف فقط از این اپه؛ پیامک‌های خود گوشی دست نمی‌خورن.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = g.textDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (confirmSenderDelete) {
        AlertDialog(
            onDismissRequest = { confirmSenderDelete = false },
            title = { Text(t("حذف همه از «") + who + t("»؟")) },
            text = { Text(t("همه پیامک‌ها یا نوتیف‌های این فرستنده از اپ حذف میشن. تا چند ثانیه بعد می‌تونی برگردونی.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSenderDelete = false
                    scope.launch {
                        onDeleted(repo.deleteFromSender(item))
                        onDismiss()
                    }
                }) { Text(t("حذف کن"), color = Category.ADS.tint) }
            },
            dismissButton = { TextButton(onClick = { confirmSenderDelete = false }) { Text(t("نه")) } },
        )
    }
}

/** The login code / one-time password in an item, if it is one. */
fun codeOf(item: Item): String? =
    if (item.category == Category.OTP.id) Classifier.extractCode(item.title + "\n" + item.body) else null

/** Small "copy code" pill shown right on the list row of a code message. */
@Composable
private fun CopyCodeChip(item: Item, modifier: Modifier = Modifier) {
    val code = remember(item.id) { codeOf(item) } ?: return
    val context = LocalContext.current
    Box(modifier) {
        GlassChip(t("کپی ") + fa(code), selected = true, color = Category.OTP.tint, icon = Icons.Rounded.ContentCopy, onClick = { CodeNotifier.copy(context, code) })
    }
}

/** Answer right here: SMS goes out from the same SIM, chats through the app's own reply button. */
@Composable
private fun QuickReply(item: Item) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    var text by remember(item.id) { mutableStateOf("") }
    var status by remember(item.id) { mutableStateOf<String?>(null) }
    fun send() {
        val ok = repo.reply(item, text.trim())
        status = if (ok) t("فرستاده شد.") else t("فرستادن نشد. از «باز کردن» جواب بده.")
        if (ok) text = ""
    }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) {
            send()
        } else if (com.rez.notif.sms.DefaultSms.openComposer(context, item.sender, text.trim())) {
            // Android blocked the permission: hand the written text to the phone's SMS app instead.
            status = t("اپ پیامک گوشی با همین متن باز شد؛ فقط دکمه ارسال رو بزن. برای فرستادن از خود شاخ گاو: تنظیمات گوشی > برنامه‌ها > شاخ گاو > ⋮ > Allow restricted settings، بعد مجوزها > پیامک.")
            text = ""
        } else {
            status = t("اجازه فرستادن پیامک داده نشد.")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text,
            { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(t("جواب سریع…")) },
            maxLines = 4,
        )
        Spacer(Modifier.size(8.dp))
        IconBubble(Icons.AutoMirrored.Rounded.Send, {
            if (text.isBlank()) return@IconBubble
            val needsSms = item.source == Source.SMS &&
                context.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsSms) askSms.launch(arrayOf(android.Manifest.permission.SEND_SMS)) else send()
        }, tint = g.accent)
    }
    status?.let { Text(it, color = g.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
}

/** A contact's name for an SMS sender (recomposes when contacts load), or the sender as it came. */
@Composable
fun senderLabel(sender: String): String {
    val names by ContactNames.names.collectAsState()
    return VipMatcher.key(sender)?.let { names[it] } ?: sender
}
