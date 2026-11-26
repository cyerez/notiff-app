package com.rez.notif.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.data.ItemQuery
import com.rez.notif.data.Source
import com.rez.notif.i18n.t
import com.rez.notif.sms.ContactNames
import com.rez.notif.sms.DefaultSms
import com.rez.notif.sms.SmsNotifier
import com.rez.notif.sms.ThreadMsg
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Phase 5: one SMS conversation (received and sent), with a box to write back. */
@Composable
fun ConversationScreen(address: String, draft: String, nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val version by repo.version.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf<List<ThreadMsg>?>(null) }
    var text by remember(address) { mutableStateOf(draft) }
    var status by remember { mutableStateOf<String?>(null) }
    val sims = remember { DefaultSms.simCount(context) }
    val canRead = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(address, version, refresh) {
        messages = if (canRead) withContext(Dispatchers.IO) { DefaultSms.thread(context, address) } else emptyList()
    }
    // Opening the conversation reads it: here, on the phone, and in the status bar.
    LaunchedEffect(address) {
        SmsNotifier.clear(context, address)
        runCatching { repo.markRead(repo.items(ItemQuery(source = Source.SMS, sender = address, includeAds = true, limit = 500))) }
        withContext(Dispatchers.IO) { DefaultSms.markRead(context, address) }
    }
    var sim by remember(messages) { mutableIntStateOf(messages?.lastOrNull { it.incoming }?.simSlot ?: 0) }

    fun send() {
        val body = text.trim()
        if (body.isEmpty()) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { DefaultSms.send(context, address, body, sim) }
            status = if (ok) null else t("فرستادن نشد.")
            if (ok) text = ""
            refresh++
        }
    }
    val askSend = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) {
            send()
        } else if (DefaultSms.openComposer(context, address, text.trim())) {
            status = t("اپ پیامک گوشی با همین متن باز شد؛ فقط دکمه ارسال رو بزن. برای فرستادن از خود شاخ گاو: تنظیمات گوشی > برنامه‌ها > شاخ گاو > ⋮ > Allow restricted settings، بعد مجوزها > پیامک.")
            text = ""
        } else {
            status = t("اجازه فرستادن پیامک داده نشد.")
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        GlassTopBar(title = com.rez.notif.ui.components.senderLabel(address), subtitle = if (ContactNames.nameOf(address) != null) address else t("گفتگو"), onBack = { nav.back() })

        val list = messages.orEmpty()
        val state = rememberLazyListState()
        LaunchedEffect(list.size) { if (list.isNotEmpty()) state.scrollToItem(list.lastIndex) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages != null && list.isEmpty()) {
                Text(
                    if (canRead) t("هنوز پیامی نیست.") else t("اول دسترسی پیامک لازمه"),
                    color = g.textDim,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { m -> Bubble(m, showSim = sims > 1) }
            }
        }

        status?.let { Text(it, color = g.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp)) }
        if (sims > 1) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(t("سیم ۱"), sim == 1, { sim = 1 })
                GlassChip(t("سیم ۲"), sim == 2, { sim = 2 })
            }
        }
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                text,
                { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(t("پیام…")) },
                maxLines = 5,
            )
            Spacer(Modifier.size(8.dp))
            IconBubble(Icons.AutoMirrored.Rounded.Send, {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    askSend.launch(arrayOf(Manifest.permission.SEND_SMS))
                } else {
                    send()
                }
            }, tint = g.accent)
        }
    }
}

@Composable
private fun Bubble(m: ThreadMsg, showSim: Boolean) {
    val g = LocalGlass.current
    val mine = !m.incoming
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (mine) g.accent.copy(alpha = 0.22f) else g.glassTop,
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SelectionContainer { Text(m.body, color = g.text, style = MaterialTheme.typography.bodyMedium) }
            Text(
                shortTime(m.date) +
                    (if (showSim && m.simSlot > 0) t("  ·  سیم ") + fa(m.simSlot) else "") +
                    (if (m.failed) "  ·  " + t("فرستاده نشد") else ""),
                color = g.textDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
