package com.rez.notif.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.Item
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.ItemSheet
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.fullTime
import com.rez.notif.ui.components.senderLabel
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.toman
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/** Money in one place: spending summary, balances, upcoming bills, and the bank alerts' own settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val s by repo.prefs.state.collectAsState()
    val bills by rememberLoaded(repo) { repo.upcomingBills() }
    val mine by rememberLoaded(repo) { repo.myBills() }
    var editingBill by remember { mutableStateOf<com.rez.notif.data.Db.MyBill?>(null) }
    var open by remember { mutableStateOf<Item?>(null) }
    var scanNote by remember { mutableStateOf<String?>(null) }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = t("حساب و کتاب"), subtitle = t("از روی پیامک‌های بانک، روی همین گوشی"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { MoneySummaryCard(onOpen = null) }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { nav.go(com.rez.notif.ui.Screen.Budget) }, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.AccountBalanceWallet, Category.BANK.tint, size = 36.dp)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("دخل و خرج"), style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(t("درآمد و خرج‌های ماهت و اینکه چقدر اضافه میاری"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { nav.go(com.rez.notif.ui.Screen.Dong) }, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.Groups, g.accent, size = 36.dp)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("حساب دنگ"), style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(t("خرج سفر و دورهمی با دوستان: کی به کی چقدر بده"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        }
                    }
                }
            }

            item {
                SectionTitle(t("قسط و قبض‌های من")) {
                    GlassChip(t("+ تازه"), false, { editingBill = com.rez.notif.data.Db.MyBill(title = "", amount = 0, day = 1) })
                }
            }
            val myList = mine.orEmpty()
            if (mine != null && myList.isEmpty()) {
                item {
                    Text(
                        t("قسط وام، اجاره، قبض یا هر پرداخت ماهانه رو خودت اضافه کن تا هر ماه سر موقع یادت بندازم."),
                        color = g.textDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                }
            }
            myList.forEach { b ->
                item(key = "my-" + b.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { editingBill = b }, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleSmall, color = g.text)
                                Text(
                                    t("روز ") + fa(b.day) + t(" هر ماه") + (if (b.remaining > 0) "  ·  " + fa(b.remaining) + t(" قسط مونده") else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = g.textDim,
                                )
                            }
                            if (b.amount > 0) Text(toman(b.amount * 10), style = MaterialTheme.typography.labelMedium, color = g.text)
                        }
                    }
                }
            }

            item { SectionTitle(t("از پیامک‌ها پیدا کردم")) }
            val list = bills.orEmpty()
            if (bills != null && list.isEmpty()) {
                item {
                    Text(
                        t("پیامکی با تاریخ سررسید پیدا نکردم."),
                        color = g.textDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                }
            }
            list.forEach { (item, bill) ->
                item(key = "bill-" + item.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { open = item }, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(senderLabel(item.sender).ifBlank { item.appName }, style = MaterialTheme.typography.titleSmall, color = g.text)
                                Text(item.body.replace('\n', ' '), style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fullTime(bill.due), style = MaterialTheme.typography.labelMedium, color = Category.ADS.tint)
                                bill.amount?.let { Text(toman(it), style = MaterialTheme.typography.labelSmall, color = g.text) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        GlassChip(t("این قسط نیست"), false, { scope.launch { repo.ignoreBill(item) } })
                    }
                }
            }

            item { SectionTitle(t("هشدارها و یادآوری")) }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    AlertToggle(t("مانده کم"), t("وقتی مانده حساب از این عدد کمتر شد خبرم کن."), s.lowBalanceOn, s.lowBalance,
                        onToggle = { v -> repo.prefs.update { it.copy(lowBalanceOn = v) } },
                        onAmount = { a -> repo.prefs.update { it.copy(lowBalance = a) } })
                    Spacer(Modifier.height(14.dp))
                    AlertToggle(t("خرج بزرگ"), t("وقتی یه برداشت یا خرید از این عدد بیشتر بود خبرم کن."), s.bigSpendOn, s.bigSpend,
                        onToggle = { v -> repo.prefs.update { it.copy(bigSpendOn = v) } },
                        onAmount = { a -> repo.prefs.update { it.copy(bigSpend = a) } })
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("یادآوری قسط و قبض"), style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(t("قسط‌های خودت رو از چند روز قبل هر روز صبح یادت میندازم؛ سررسیدهای توی پیامک رو هم."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        }
                        Switch(
                            checked = s.billReminders,
                            onCheckedChange = { v ->
                                repo.prefs.update { it.copy(billReminders = v) }
                                if (v) scope.launch { repo.scanBills() }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = g.accent, checkedThumbColor = Color.White),
                        )
                    }
                    if (s.billReminders) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to t("۱ روز قبل"), 2 to t("۲ روز قبل"), 3 to t("۳ روز قبل"), 5 to t("۵ روز قبل")).forEach { (d, label) ->
                                GlassChip(label, s.billDaysBefore == d, {
                                    repo.prefs.update { it.copy(billDaysBefore = d) }
                                    scope.launch { repo.rescheduleMyBills() }
                                })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassButton(t("گشتن پیامک‌های قبلی"), primary = false, onClick = {
                            scope.launch {
                                val n = repo.scanBills()
                                scanNote = if (n == 0) t("یادآوری تازه‌ای لازم نبود.") else fa(n) + t(" یادآوری گذاشتم.")
                            }
                        })
                        scanNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = g.textDim, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
            item {
                Text(
                    t("عددها از متن پیامک خونده میشن و ممکنه کامل نباشن. هیچ چیزی از گوشی بیرون نمیره."),
                    style = MaterialTheme.typography.labelSmall,
                    color = g.textDim,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        }
    }
    open?.let { ItemSheet(it, onDismiss = { open = null }) }
    editingBill?.let { b ->
        BillDialog(
            b,
            onDismiss = { editingBill = null },
            onSave = { nb -> editingBill = null; scope.launch { repo.saveMyBill(nb) } },
            onDelete = if (b.id > 0) ({ editingBill = null; scope.launch { repo.deleteMyBill(b.id) } }) else null,
        )
    }
}

/** Add or change a monthly payment: name, amount (toman), Persian day of month, installments left. */
@Composable
private fun BillDialog(start: com.rez.notif.data.Db.MyBill, onDismiss: () -> Unit, onSave: (com.rez.notif.data.Db.MyBill) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(start.title) }
    var amount by remember { mutableStateOf(if (start.amount > 0) start.amount.toString() else "") }
    var day by remember { mutableStateOf(start.day.toString()) }
    var left by remember { mutableStateOf(if (start.remaining > 0) start.remaining.toString() else "") }
    fun num(v: String) = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("")
    val dayN = day.toIntOrNull()
    val ok = title.isNotBlank() && dayN != null && dayN in 1..31
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id > 0) t("ویرایش قسط") else t("قسط یا قبض تازه")) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(t("اسم (مثلاً وام مسکن)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = num(it).take(13) }, label = { Text(t("مبلغ به تومان (اختیاری)")) }, singleLine = true,
                    supportingText = { if (amount.isNotEmpty()) Text(toman((amount.toLongOrNull() ?: 0) * 10)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    day, { day = num(it).take(2) }, label = { Text(t("روز سررسید در هر ماه (۱ تا ۳۱)")) }, singleLine = true,
                    isError = !(dayN != null && dayN in 1..31),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    left, { left = num(it).take(3) }, label = { Text(t("چند قسط مونده؟ (خالی = همیشگی)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = ok, onClick = {
                onSave(start.copy(title = title.trim(), amount = amount.toLongOrNull() ?: 0, day = dayN ?: 1, remaining = left.toIntOrNull() ?: 0))
            }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) androidx.compose.material3.TextButton(onClick = onDelete) { Text(t("حذف"), color = Category.ADS.tint) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}

@Composable
private fun AlertToggle(title: String, hint: String, on: Boolean, amount: Long, onToggle: (Boolean) -> Unit, onAmount: (Long) -> Unit) {
    val g = LocalGlass.current
    var text by remember(amount) { mutableStateOf(amount.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = g.text)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = g.textDim)
        }
        Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = g.accent, checkedThumbColor = Color.White))
    }
    if (on) {
        OutlinedTextField(
            text,
            { v ->
                val digits = v.filter { it.isDigit() }.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.joinToString("").take(13)
                text = digits
                digits.toLongOrNull()?.let(onAmount)
            },
            label = { Text(t("مبلغ به تومان")) },
            supportingText = { Text(toman((text.toLongOrNull() ?: 0) * 10)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/** Last 30 days from bank SMS: money out, money in, and each bank's latest balance. Shown in toman. */
@Composable
fun MoneySummaryCard(onOpen: (() -> Unit)?) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val m by rememberLoaded(repo) { repo.moneySummary() }
    val sum = m ?: return
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), glow = Category.BANK.tint, onClick = onOpen, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(Icons.Rounded.Savings, Category.BANK.tint, size = 34.dp)
            Spacer(Modifier.size(10.dp))
            Text(t("۳۰ روز اخیر از پیامک‌های بانک"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("خرج و برداشت"), style = MaterialTheme.typography.labelMedium, color = g.textDim)
                Text(toman(sum.out), style = MaterialTheme.typography.titleMedium, color = Category.ADS.tint, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(t("واریز"), style = MaterialTheme.typography.labelMedium, color = g.textDim)
                Text(toman(sum.inn), style = MaterialTheme.typography.titleMedium, color = Category.PERSONAL.tint, fontWeight = FontWeight.Bold)
            }
        }
        sum.accounts.take(4).forEach { a ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("مانده ") + senderLabel(a.sender), style = MaterialTheme.typography.bodySmall, color = g.text, modifier = Modifier.weight(1f))
                Text(toman(a.balance) + "  ·  " + shortTime(a.time), style = MaterialTheme.typography.bodySmall, color = g.textDim)
            }
        }
        Text(
            fa(sum.count) + t(" تراکنش. عددها از متن پیامک خونده شده و ممکنه کامل نباشه."),
            style = MaterialTheme.typography.labelSmall,
            color = g.textDim,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
