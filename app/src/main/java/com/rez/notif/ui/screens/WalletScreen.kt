package com.rez.notif.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.i18n.t
import com.rez.notif.security.WalletVault
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.theme.LocalGlass
import com.rez.notif.wallet.PasswordGen
import com.rez.notif.wallet.WalletEntry
import com.rez.notif.wallet.WalletKind
import kotlinx.coroutines.launch

private val WalletKind.icon: ImageVector
    get() = when (this) {
        WalletKind.LOGIN -> Icons.Rounded.Key
        WalletKind.BANK -> Icons.Rounded.CreditCard
        WalletKind.WIFI -> Icons.Rounded.Wifi
        WalletKind.NOTE -> Icons.Rounded.StickyNote2
    }

/**
 * Offline password wallet: only for the essentials, works without internet. It has its own lock
 * (fingerprint every time you open it), blocks screenshots, and locks again when you leave.
 */
@Composable
fun WalletScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val activity = context as? Activity
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val open by WalletVault.unlocked.collectAsState()
    var entries by remember { mutableStateOf<List<WalletEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<WalletEntry?>(null) }
    var editing by remember { mutableStateOf<WalletEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val secure = remember { WalletVault.deviceSecure(context) }

    // No screenshots or screen recording of the wallet; lock it again when leaving the page.
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            WalletVault.lock()
        }
    }

    fun reload() {
        scope.launch {
            entries = try { repo.walletEntries() } catch (e: WalletVault.Locked) { emptyList() }
        }
    }

    fun unlock() { activity?.let { a -> WalletVault.unlock(a) { ok -> if (ok) reload() } } }

    LaunchedEffect(open) { if (open) reload() else entries = emptyList() }
    LaunchedEffect(Unit) { if (secure && !WalletVault.isOpen()) unlock() }

    /** Runs a wallet action; if the 60-second key window has passed, asks for the finger and tries again. */
    fun guarded(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (e: WalletVault.Locked) {
                activity?.let { a -> WalletVault.unlock(a) { ok -> if (ok) scope.launch { runCatching { action() } } } }
            }
        }
    }

    Column {
        GlassTopBar(title = t("کیف پسورد"), subtitle = t("آفلاین، فقط روی همین گوشی"), onBack = nav::back) {
            if (open) {
                IconBubble(Icons.Rounded.Add, { editing = WalletEntry(title = "", kind = WalletKind.LOGIN) }, tint = g.accent)
                Spacer(Modifier.size(8.dp))
                IconBubble(Icons.Rounded.Lock, { WalletVault.lock() })
            }
        }
        when {
            !secure -> LockedState(
                t("برای کیف پسورد، گوشی باید قفل صفحه (پین، الگو یا رمز) داشته باشه. اول از تنظیمات گوشی قفل صفحه بذار."),
                null,
            )
            !open -> LockedState(t("کیف پسورد قفله"), ::unlock)
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)) {
                item {
                    OutlinedTextField(
                        query, { query = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = { Text(t("جستجو در کیف…")) },
                        singleLine = true,
                    )
                }
                val list = entries.filter { query.isBlank() || it.title.contains(query, true) || it.username.contains(query, true) }
                if (list.isEmpty()) {
                    item {
                        Text(
                            if (entries.isEmpty()) t("هنوز چیزی اینجا نیست. با دکمه + رمزهای ضروری رو اضافه کن: ایمیل اصلی، بانک، وای‌فای خونه…") else t("چیزی پیدا نشد."),
                            color = g.textDim, modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { e ->
                    GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = { shown = e }, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeonIcon(e.kind.icon, g.accent, size = 38.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.titleSmall, color = g.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    e.username.ifBlank { t(e.kind.label) },
                                    style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (e.secret.isNotEmpty()) IconBubble(Icons.Rounded.ContentCopy, { WalletVault.copy(context, e.secret); error = t("رمز کپی شد؛ ۴۵ ثانیه بعد از کلیپ‌بورد پاک میشه.") })
                        }
                    }
                }
                error?.let { msg -> item { Text(msg, color = g.accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) } }
            }
        }
    }

    shown?.let { e ->
        EntrySheet(
            e,
            onDismiss = { shown = null },
            onEdit = { shown = null; editing = e },
            onDelete = { shown = null; guarded { repo.walletDelete(e.id); reload() } },
        )
    }
    editing?.let { e ->
        EditSheet(e, onDismiss = { editing = null }, onSave = { updated ->
            editing = null
            guarded { repo.walletSave(updated); reload() }
        })
    }
}

@Composable
private fun LockedState(text: String, onUnlock: (() -> Unit)?) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        NeonIcon(Icons.Rounded.Lock, g.accent, size = 84.dp)
        Spacer(Modifier.height(18.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = g.text)
        if (onUnlock != null) {
            Spacer(Modifier.height(20.dp))
            GlassButton(t("باز کردن"), icon = Icons.Rounded.Fingerprint, onClick = onUnlock)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(e: WalletEntry, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    var reveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonIcon(e.kind.icon, g.accent, size = 46.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleLarge, color = g.text)
                    Text(t(e.kind.label), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                }
                IconBubble(Icons.Rounded.Edit, onEdit)
            }
            Spacer(Modifier.height(16.dp))
            if (e.username.isNotEmpty()) Field(t(e.kind.userLabel), e.username) { WalletVault.copy(context, e.username) }
            if (e.secret.isNotEmpty()) {
                Field(t(e.kind.secretLabel), if (reveal) e.secret else "••••••••••", mono = reveal, trailing = {
                    IconBubble(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, { reveal = !reveal })
                }) { WalletVault.copy(context, e.secret) }
            }
            if (e.url.isNotEmpty()) Field(t("سایت یا اپ"), e.url) { WalletVault.copy(context, e.url) }
            if (e.note.isNotEmpty()) Field(t("یادداشت"), e.note) { WalletVault.copy(context, e.note) }
            Text(t("کپی‌ها بعد از ۴۵ ثانیه از کلیپ‌بورد پاک میشن."), style = MaterialTheme.typography.bodySmall, color = g.textDim, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            GlassButton(t("حذف"), icon = Icons.Rounded.Delete, primary = false, onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("حذف از کیف؟")) },
            text = { Text(e.title) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(t("حذف کن"), color = Category.ADS.tint) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("نه")) } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false, trailing: @Composable () -> Unit = {}, onCopy: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = g.textDim)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = g.text, fontFamily = if (mono) FontFamily.Monospace else null)
            }
            trailing()
            Spacer(Modifier.size(6.dp))
            IconBubble(Icons.Rounded.ContentCopy, onCopy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(start: WalletEntry, onDismiss: () -> Unit, onSave: (WalletEntry) -> Unit) {
    val g = LocalGlass.current
    var e by remember { mutableStateOf(start) }
    var reveal by remember { mutableStateOf(start.id == 0L) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(if (start.id == 0L) t("افزودن به کیف") else t("ویرایش"), style = MaterialTheme.typography.titleLarge, color = g.text)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WalletKind.entries.forEach { k -> GlassChip(t(k.label), e.kind == k, { e = e.copy(kind = k) }, icon = k.icon) }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(e.title, { e = e.copy(title = it) }, label = { Text(t("اسم")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                e.username, { e = e.copy(username = it) }, label = { Text(t(e.kind.userLabel)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = if (e.kind == WalletKind.BANK) KeyboardType.Number else KeyboardType.Email),
            )
            OutlinedTextField(
                e.secret, { e = e.copy(secret = it) }, label = { Text(t(e.kind.secretLabel)) },
                singleLine = e.kind != WalletKind.NOTE, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (reveal || e.kind == WalletKind.NOTE) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = if (e.kind == WalletKind.NOTE) KeyboardType.Text else KeyboardType.Password),
                trailingIcon = { IconBubble(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, { reveal = !reveal }) },
            )
            if (e.kind == WalletKind.LOGIN || e.kind == WalletKind.WIFI) {
                Spacer(Modifier.height(6.dp))
                GlassButton(t("ساختن رمز قوی"), icon = Icons.Rounded.AutoFixHigh, primary = false, onClick = {
                    e = e.copy(secret = PasswordGen.generate(16)); reveal = true
                })
            }
            if (e.kind == WalletKind.LOGIN) {
                OutlinedTextField(e.url, { e = e.copy(url = it) }, label = { Text(t("سایت یا اپ")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(e.note, { e = e.copy(note = it) }, label = { Text(t("یادداشت")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(14.dp))
            GlassButton(t("ذخیره"), enabled = e.title.isNotBlank(), onClick = { onSave(e) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
