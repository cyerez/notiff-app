package com.rez.notif.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Budget
import com.rez.notif.classify.Category
import com.rez.notif.classify.Ledger
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.pressable
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.toman
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

private val GoodColor = Color(0xFF2E9E6A)
private val SpendColor = Category.ADS.tint

private fun catLabel(c: String?): String = when (c) {
    null, "" -> t("بی‌دسته")
    Ledger.OWN -> t("به خودم")
    Ledger.IGNORE -> t("نادیده")
    else -> t(c)
}

private fun partyLabel(p: String): String = when {
    p.startsWith("card:") -> t("کارت ") + fa(p.removePrefix("card:"))
    p.startsWith("shop:") -> p.removePrefix("shop:")
    else -> ""
}

private fun accountLabel(a: String): String = fa(a)

private fun digitsOnly(v: String) = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("")

/**
 * The ledger ("دفتر حساب"): every money movement of a month in one place, spending per category,
 * missing SMS found from the balances, and the questions "what was this?".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(nav: Navigator, embedded: Boolean = false) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val today = remember { repo.thisMonth() }
    var month by remember { mutableStateOf(today) }
    val view by rememberLoaded(repo, month) { repo.ledger(month) }
    var picking by remember { mutableStateOf<Ledger.Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingCard by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<com.rez.notif.data.Item?>(null) }
    var limitFor by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) scope.launch {
            exportMsg = runCatching { repo.exportLedgerCsv(uri, month) }.fold(
                { n -> fa(n) + t(" ردیف ذخیره شد. با Excel یا Google Sheets بازش کن.") },
                { t("ذخیره نشد.") },
            )
        }
    }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = t("دفتر حساب"), subtitle = t("همه خرج و درآمدت یه جا"), onBack = if (embedded) null else nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = if (embedded) 130.dp else 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassChip("‹", false, { month = Budget.prev(month) })
                    Text(
                        t(Budget.MONTH_NAMES[(month % 100 - 1).coerceIn(0, 11)]) + " " + fa(month / 100) + if (month == today) t(" (این ماه)") else "",
                        style = MaterialTheme.typography.titleMedium, color = g.text,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Center,
                    )
                    GlassChip("›", false, { month = Budget.next(month) })
                }
            }
            val v = view ?: return@LazyColumn
            val s = v.summary
            item { LedgerSummary(s) }
            item { LeftCard(v, onPlan = { nav.go(Screen.Budget) }) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip(t("طلب، بدهی، چک و قلک"), false, { nav.go(Screen.Commitments) })
                    GlassChip(t("دخل و خرج"), false, { nav.go(Screen.Budget) })
                    GlassChip(t("قسط و قبض"), false, { nav.go(Screen.Money) })
                    GlassChip(t("دنگ"), false, { nav.go(Screen.Dong) })
                    GlassChip(t("خروجی Excel"), false, {
                        exporter.launch("ledger-" + month + ".csv")
                    })
                }
            }
            exportMsg?.let { m -> item { Hint(m) } }
            if (v.chequesSoon.isNotEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), glow = SpendColor, onClick = { nav.go(Screen.Commitments) }, contentPadding = 12.dp) {
                        Text(t("چک‌های این هفته"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        v.chequesSoon.forEach { q ->
                            Text(
                                (if (q.incoming) t("دریافتی از ") else t("پرداختی به ")) + q.person + "  ·  " + toman(q.toman * 10) + "  ·  " + shortTime(q.due),
                                style = MaterialTheme.typography.bodySmall, color = g.textDim,
                            )
                        }
                    }
                }
            }

            // Bank SMS that could not be read
            if (v.unreadable.isNotEmpty()) {
                item { SectionTitle(t("پیامک‌هایی که نتونستم بخونم") + "  ·  " + fa(v.unreadable.size)) }
                v.unreadable.take(10).forEach { it0 ->
                    item(key = "unread" + it0.id) {
                        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                            Text(it0.sender + "  ·  " + shortTime(it0.timestamp), style = MaterialTheme.typography.labelMedium, color = g.textDim)
                            Text(it0.body, style = MaterialTheme.typography.bodySmall, color = g.text, maxLines = 5)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassChip(t("مبلغش رو بنویس"), false, { reading = it0 })
                                GlassChip(t("پول نبود"), false, { scope.launch { repo.handleUnreadable(it0, 0, false, "") } })
                            }
                        }
                    }
                }
            }

            // Missing SMS
            if (v.gaps.isNotEmpty()) {
                item { SectionTitle(t("پیامک‌هایی که نیومده") + "  ·  " + fa(v.gaps.size)) }
                v.gaps.forEach { gap ->
                    item(key = "gap" + gap.key) {
                        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), glow = SpendColor, contentPadding = 12.dp) {
                            val out = gap.amount > 0
                            Text(
                                (if (out) t("از ") else t("به ")) + accountLabel(gap.account) + "  " + toman(kotlin.math.abs(gap.amount)) +
                                    (if (out) t(" کم شده") else t(" اضافه شده")) + t(" ولی پیامکش نیومده."),
                                style = MaterialTheme.typography.titleSmall, color = g.text,
                            )
                            Text(t("بین ") + shortTime(gap.from) + t(" و ") + shortTime(gap.to) + t(". چی بود؟"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                            Spacer(Modifier.height(6.dp))
                            val cats = if (out) v.categories.take(6) else Ledger.INCOME_CATS
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                cats.forEach { c -> GlassChip(t(c), false, { scope.launch { repo.nameGap(gap, c) } }) }
                                GlassChip(t("به خودم"), false, { scope.launch { repo.nameGap(gap, Ledger.OWN) } })
                                GlassChip(t("نادیده بگیر"), false, { scope.launch { repo.nameGap(gap, Ledger.IGNORE) } })
                            }
                        }
                    }
                }
            }

            // What was this?
            val unsorted = v.entries.filter { it.needsCategory }
            if (unsorted.isNotEmpty()) {
                item { SectionTitle(t("این‌ها چی بودن؟") + "  ·  " + fa(unsorted.size)) }
                item { Hint(t("یه بار بگو؛ دفعه بعد از همون کارت یا فروشگاه خودش می‌فهمه.")) }
                unsorted.take(if (showAll) 200 else 8).forEach { e ->
                    item(key = "ask" + e.id) {
                        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(partyLabel(e.party).ifEmpty { t(e.kind.label) }, style = MaterialTheme.typography.titleSmall, color = g.text, maxLines = 1)
                                    Text(accountLabel(e.account) + "  ·  " + shortTime(e.time), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                                }
                                Text(toman(e.out), style = MaterialTheme.typography.labelMedium, color = SpendColor)
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                v.categories.take(5).forEach { c -> GlassChip(t(c), false, { scope.launch { repo.setLedgerCategory(e, c) } }) }
                                GlassChip(t("به خودم"), false, { scope.launch { repo.setLedgerCategory(e, Ledger.OWN) } })
                                GlassChip(t("بقیه…"), false, { picking = e })
                            }
                        }
                    }
                }
                if (unsorted.size > 8 && !showAll) item {
                    Box(Modifier.padding(horizontal = 16.dp)) { GlassChip(t("همه رو نشون بده"), false, { showAll = true }) }
                }
            }

            // Per category, with the monthly limits
            val catRows = (s.byCategory.map { it.first } + v.limits.keys).distinct()
            item {
                SectionTitle(t("خرج هر دسته")) { GlassChip(t("+ سقف ماهانه"), false, { limitFor = "" }) }
            }
            if (catRows.isEmpty()) item { Hint(t("برای هر دسته (مثلاً سیگار یا خوراک) یه سقف ماهانه بذار تا ببینی چقدر جا داری.")) }
            else item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                    val spentOf = s.byCategory.toMap()
                    val max = (s.byCategory.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1)
                    catRows.forEach { c ->
                        val a = spentOf[c] ?: 0L
                        val limit = v.limits[c]?.let { it * 10 }
                        val over = limit != null && a > limit
                        val near = limit != null && !over && a * 10 >= limit * 8
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).pressable({ if (c.isNotEmpty()) limitFor = c }).padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(catLabel(c), style = MaterialTheme.typography.bodyMedium, color = g.text, modifier = Modifier.weight(0.35f), maxLines = 1)
                                val part = if (limit != null && limit > 0) (a.toFloat() / limit).coerceIn(0f, 1f) else a.toFloat() / max
                                Box(Modifier.weight(0.4f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(g.textDim.copy(alpha = 0.15f))) {
                                    Box(
                                        Modifier.fillMaxWidth(part).height(8.dp).clip(RoundedCornerShape(4.dp))
                                            .background(if (over) SpendColor else if (near) Color(0xFFFF8A3D) else if (c.isEmpty()) g.textDim else g.accent),
                                    )
                                }
                                Text(toman(a), style = MaterialTheme.typography.labelSmall, color = if (over) SpendColor else g.textDim, modifier = Modifier.weight(0.3f).padding(start = 6.dp), maxLines = 1)
                            }
                            if (limit != null) {
                                Text(
                                    if (over) t("از سقف ") + toman(limit) + t(" رد شده: ") + toman(a - limit) + t(" بیشتر")
                                    else t("سقف ") + toman(limit) + t("، مونده ") + toman(limit - a),
                                    style = MaterialTheme.typography.labelSmall, color = if (over) SpendColor else g.textDim,
                                )
                            }
                        }
                    }
                }
            }

            // All entries
            item {
                SectionTitle(t("همه تراکنش‌ها") + "  ·  " + fa(v.entries.size)) {
                    GlassChip(t("+ خرج دستی"), false, { adding = true })
                }
            }
            if (v.entries.isEmpty()) item { Hint(t("این ماه هنوز پیامک بانکی یا خرج دستی نداری.")) }
            v.entries.forEach { e ->
                item(key = "e" + e.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { picking = e }, contentPadding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    catLabel(e.category) + (partyLabel(e.party).takeIf { it.isNotEmpty() }?.let { "  ·  $it" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall, color = if (e.category == null && e.out > 0) SpendColor else g.text, maxLines = 1,
                                )
                                Text(
                                    accountLabel(e.account) + "  ·  " + shortTime(e.time) + (if (e.manual) t("  ·  دستی") else ""),
                                    style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 1,
                                )
                            }
                            val inn = e.inn > 0
                            Text(
                                (if (inn) "+" else "−") + toman(if (inn) e.inn else e.out),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!e.counted) g.textDim else if (inn) GoodColor else SpendColor,
                            )
                        }
                    }
                }
            }

            // My cards
            item {
                SectionTitle(t("کارت‌های خودم")) { GlassChip(t("+ کارت"), false, { addingCard = true }) }
            }
            item {
                Hint(
                    t("کارت‌هایی که پیامکشون برات میاد خودکار شناخته میشن. کارتی که پیامک نداره (مثلاً بانک دیگه) رو اینجا با ۴ رقم آخرش اضافه کن تا کارت به کارت به اون «به خودم» حساب بشه."),
                )
            }
            if (v.myCards.isNotEmpty()) item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    v.myCards.forEach { c -> GlassChip(t("کارت ") + fa(c) + "  ✕", true, { scope.launch { repo.setMyCard(c, false) } }) }
                }
            }
        }
    }

    picking?.let { e ->
        val v = view
        CategoryDialog(
            e, if (e.inn > 0 && e.out == 0L) Ledger.INCOME_CATS else (v?.categories ?: Ledger.SPEND_CATS),
            onDismiss = { picking = null },
            onPick = { c, learn -> picking = null; scope.launch { repo.setLedgerCategory(e, c, learn) } },
            onDelete = if (e.manual) ({ picking = null; scope.launch { repo.deleteManual(e) } }) else null,
        )
    }
    reading?.let { item ->
        AddDialog(
            view?.categories ?: Ledger.SPEND_CATS,
            onDismiss = { reading = null },
            onSave = { toman, income, c, _ -> reading = null; scope.launch { repo.handleUnreadable(item, toman, income, c) } },
            title = t("این پیامک چقدر بود؟"),
            text = item.body,
        )
    }
    limitFor?.let { start ->
        LimitDialog(
            start, view?.limits.orEmpty(), view?.categories ?: Ledger.SPEND_CATS,
            onDismiss = { limitFor = null },
            onSave = { c, toman -> limitFor = null; scope.launch { repo.setLimit(c, toman) } },
        )
    }
    if (adding) {
        AddDialog(
            view?.categories ?: Ledger.SPEND_CATS,
            onDismiss = { adding = false },
            onSave = { toman, income, c, note -> adding = false; scope.launch { repo.addManual(toman, income, c, note) } },
        )
    }
    if (addingCard) {
        var digits by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingCard = false },
            title = { Text(t("کارت خودم")) },
            text = {
                OutlinedTextField(
                    digits, { digits = digitsOnly(it).take(4) }, label = { Text(t("۴ رقم آخر کارت")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = digits.length == 4, onClick = { addingCard = false; scope.launch { repo.setMyCard(digits, true) } }) { Text(t("ذخیره")) }
            },
            dismissButton = { TextButton(onClick = { addingCard = false }) { Text(t("بی‌خیال")) } },
        )
    }
}

@Composable
private fun LedgerSummary(s: Ledger.Summary) {
    val g = LocalGlass.current
    val left = s.inn - s.out
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), glow = if (left >= 0) GoodColor else SpendColor, contentPadding = 14.dp) {
        Row {
            Figure(t("خرج"), s.out, SpendColor, Modifier.weight(1f))
            Figure(t("واریز"), s.inn, GoodColor, Modifier.weight(1f))
            Figure(if (left >= 0) t("اضافه") else t("کسری"), kotlin.math.abs(left), if (left >= 0) GoodColor else SpendColor, Modifier.weight(1f))
        }
        if (s.own > 0) {
            Spacer(Modifier.height(6.dp))
            Text(toman(s.own) + t(" بین کارت‌های خودت جابه‌جا شده و حساب نشده."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
        }
    }
}

@Composable
private fun Figure(label: String, rial: Long, color: Color, modifier: Modifier) {
    val g = LocalGlass.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = g.textDim)
        Text(fa(String.format(java.util.Locale.US, "%,d", rial / 10)), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(t("تومان"), style = MaterialTheme.typography.labelSmall, color = g.textDim)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalGlass.current.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 22.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(e: Ledger.Entry, cats: List<String>, onDismiss: () -> Unit, onPick: (String, Boolean) -> Unit, onDelete: (() -> Unit)?) {
    val g = LocalGlass.current
    var learn by remember { mutableStateOf(e.party.isNotEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(toman(if (e.inn > 0) e.inn else e.out) + "  ·  " + shortTime(e.time)) },
        text = {
            Column {
                if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 6)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c -> GlassChip(t(c), e.category == c, { onPick(c, learn) }) }
                    GlassChip(t("به خودم"), e.category == Ledger.OWN, { onPick(Ledger.OWN, learn) })
                    GlassChip(t("نادیده"), e.category == Ledger.IGNORE, { onPick(Ledger.IGNORE, false) })
                }
                if (e.party.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(learn, { learn = it })
                        Text(t("دفعه بعد هم برای ") + partyLabel(e.party) + t(" همین باشه"), style = MaterialTheme.typography.bodySmall, color = g.text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("بستن")) } },
        dismissButton = { if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف"), color = SpendColor) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDialog(
    cats: List<String>,
    onDismiss: () -> Unit,
    onSave: (Long, Boolean, String, String) -> Unit,
    title: String = t("خرج یا درآمد دستی"),
    text: String? = null,
) {
    var amount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val a = amount.toLongOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textDim, maxLines = 6)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("خرج"), !income, { income = false; cat = null })
                    GlassChip(t("درآمد"), income, { income = true; cat = null })
                }
                OutlinedTextField(
                    amount, { amount = digitsOnly(it).take(12) }, label = { Text(t("مبلغ به تومان")) }, singleLine = true,
                    supportingText = { if (a > 0) Text(toman(a * 10)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (income) Ledger.INCOME_CATS else cats).forEach { c -> GlassChip(t(c), cat == c, { cat = c }) }
                }
                OutlinedTextField(note, { note = it.take(60) }, label = { Text(t("یادداشت (اختیاری)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = a > 0 && cat != null, onClick = { onSave(a, income, cat!!, note.trim()) }) { Text(t("ذخیره")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) } },
    )
}

/** "Until the end of the month you can still spend X, that is Y a day." */
@Composable
private fun LeftCard(v: com.rez.notif.data.Repository.LedgerView, onPlan: () -> Unit) {
    val g = LocalGlass.current
    val l = v.left
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onPlan, contentPadding = 12.dp) {
        if (l == null) {
            Text(t("تا آخر ماه چقدر می‌تونی خرج کنی؟"), style = MaterialTheme.typography.titleSmall, color = g.text)
            Text(t("درآمد ماهانه و خرج‌های ثابتت (اجاره، قسط...) رو توی «دخل و خرج» بنویس تا اینجا حساب کنم."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
            return@GlassCard
        }
        val ok = l.left >= 0
        Text(
            if (v.daysLeft == 0) (if (ok) t("این ماه ") + toman(l.left * 10) + t(" اضافه موند.") else t("این ماه ") + toman(-l.left * 10) + t(" کم آوردی."))
            else if (ok) t("تا آخر ماه ") + toman(l.left * 10) + t(" می‌تونی خرج کنی")
            else t("این ماه ") + toman(-l.left * 10) + t(" بیشتر از درآمدت خرج شده"),
            style = MaterialTheme.typography.titleSmall, color = if (ok) GoodColor else SpendColor,
        )
        if (ok && v.daysLeft > 0) {
            Text(t("یعنی روزی ") + toman(l.perDay * 10) + t(" برای ") + fa(v.daysLeft) + t(" روز باقی‌مونده"), style = MaterialTheme.typography.bodySmall, color = g.text)
        }
        Text(
            (if (v.plannedIncome) t("درآمد ") else t("واریزی‌های این ماه ")) + toman(l.income * 10) + t(" − ثابت‌ها ") + toman(l.fixed * 10) +
                (if (l.saving > 0) t(" − قلک ") + toman(l.saving * 10) else "") + t(" − بقیه خرج‌ها ") + toman(l.spent * 10),
            style = MaterialTheme.typography.labelSmall, color = g.textDim,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitDialog(start: String, limits: Map<String, Long>, cats: List<String>, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var cat by remember { mutableStateOf(start.ifEmpty { null }) }
    var amount by remember { mutableStateOf(limits[start]?.toString() ?: "") }
    val a = amount.toLongOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("سقف ماهانه")) },
        text = {
            Column {
                if (start.isEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEach { c -> GlassChip(t(c), cat == c, { cat = c; amount = limits[c]?.toString() ?: "" }) }
                    }
                } else {
                    Text(t(start), style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    amount, { amount = digitsOnly(it).take(12) }, label = { Text(t("سقف به تومان (خالی = بدون سقف)")) }, singleLine = true,
                    supportingText = { if (a > 0) Text(toman(a * 10)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = cat != null, onClick = { onSave(cat!!, a) }) { Text(t("ذخیره")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) } },
    )
}
