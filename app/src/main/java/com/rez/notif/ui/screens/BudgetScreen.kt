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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Budget
import com.rez.notif.classify.BudgetLine
import com.rez.notif.classify.Category
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

private val InColor = Color(0xFF2E9E6A)
private val OutColor = Category.ADS.tint

private fun tm(toman: Long): String = fa(String.format(java.util.Locale.US, "%,d", toman)) + t(" تومان")

private fun monthName(m: Int): String = t(Budget.MONTH_NAMES[(m % 100 - 1).coerceIn(0, 11)]) + " " + fa(m / 100)

/** Monthly income and spending: what comes in, what goes out, and what is left. */
@Composable
fun BudgetScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val today = remember { repo.thisMonth() }
    var month by remember { mutableStateOf(today) }
    val view by rememberLoaded(repo, month) { repo.budget(month) }
    var editing by remember { mutableStateOf<BudgetLine?>(null) }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = t("دخل و خرج"), subtitle = t("ورودی و خروجی هر ماه"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassChip("‹", false, { month = Budget.prev(month) })
                    Text(
                        monthName(month) + if (month == today) t(" (این ماه)") else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = g.text,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    GlassChip("›", false, { month = Budget.next(month) })
                }
            }
            val v = view ?: return@LazyColumn
            val m = v.month
            item { SummaryCard(m) }
            if (v.bills.isNotEmpty()) {
                item {
                    val paidSum = v.bills.filter { it.id in v.paid }.sumOf { it.amount }
                    Text(
                        t("قسط‌های این ماه: ") + tm(paidSum) + t(" پرداخت شده، ") + tm(m.installments - paidSum) + t(" مونده"),
                        style = MaterialTheme.typography.bodySmall,
                        color = g.textDim,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                    )
                }
            }
            if (v.bankOut > 0 || v.bankIn > 0) {
                item {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentPadding = 12.dp) {
                        Text(t("از پیامک‌های بانک در این ماه"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        Text(
                            t("خرج ") + tm(v.bankOut) + t("، واریز ") + tm(v.bankIn),
                            style = MaterialTheme.typography.bodySmall,
                            color = g.textDim,
                        )
                        if (v.bankInternal > 0) {
                            Text(tm(v.bankInternal) + t(" جابه‌جایی بین حساب‌های خودت حساب نشده."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        }
                        Spacer(Modifier.height(8.dp))
                        KindBreakdown(v.byKind)
                    }
                }
            }

            item {
                SectionTitle(t("ورودی‌ها") + "  ·  " + tm(m.income)) {
                    GlassChip(t("+ ورودی"), false, { editing = BudgetLine(title = "", amount = 0, income = true, monthly = true, month = month) })
                }
            }
            if (m.incomeLines.isEmpty()) item { Hint(t("حقوق، اجاره‌ای که می‌گیری یا هر پولی که هر ماه میاد رو اضافه کن.")) }
            m.incomeLines.forEach { l -> item(key = "in" + l.id) { LineRow(l, InColor) { editing = l } } }

            item {
                SectionTitle(t("خروجی‌ها") + "  ·  " + tm(m.totalOut)) {
                    GlassChip(t("+ خرج"), false, { editing = BudgetLine(title = "", amount = 0, income = false, monthly = true, month = month) })
                }
            }
            if (m.spendLines.isEmpty() && v.bills.isEmpty()) item { Hint(t("اجاره، خرجی خونه، باشگاه، بنزین و هر خرج ثابت یا موردی رو اضافه کن.")) }
            m.spendLines.forEach { l -> item(key = "out" + l.id) { LineRow(l, OutColor) { editing = l } } }
            v.bills.forEach { b ->
                item(key = "bill" + b.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { nav.go(Screen.Money) }, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleSmall, color = g.text)
                                Text(t("قسط (از حساب و کتاب)") + "  ·  " + t("روز ") + fa(b.day), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                            }
                            Text(tm(b.amount), style = MaterialTheme.typography.labelMedium, color = OutColor)
                        }
                        val paid = b.id in v.paid
                        Spacer(Modifier.height(6.dp))
                        GlassChip(if (paid) t("پرداخت شد ✓") else t("پرداختش کردم"), paid, {
                            scope.launch { repo.setPaid(b.id, month, !paid) }
                        }, color = InColor)
                    }
                }
            }
        }
    }

    editing?.let { l ->
        LineDialog(
            l, month,
            onDismiss = { editing = null },
            onSave = { nl -> editing = null; scope.launch { repo.saveBudgetLine(nl) } },
            onDelete = if (l.id > 0) ({ editing = null; scope.launch { repo.removeBudgetLine(l, month) } }) else null,
        )
    }
}

@Composable
private fun SummaryCard(m: Budget.Month) {
    val g = LocalGlass.current
    val good = m.left >= 0
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), glow = if (good) InColor else OutColor, contentPadding = 14.dp) {
        Row {
            Figure(t("ورودی"), m.income, InColor, Modifier.weight(1f))
            Figure(t("خروجی"), m.totalOut, OutColor, Modifier.weight(1f))
            Figure(if (good) t("اضافه میاری") else t("کم میاری"), kotlin.math.abs(m.left), if (good) InColor else OutColor, Modifier.weight(1f))
        }
        if (m.income > 0) {
            Spacer(Modifier.height(10.dp))
            val part = (m.totalOut.toFloat() / m.income).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(g.textDim.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(part).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (good) g.accent else OutColor))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (good) fa(m.savedPercent) + t("٪ از درآمدت می‌مونه.") else t("این ماه خرجت ") + tm(-m.left) + t(" از درآمدت بیشتره."),
                style = MaterialTheme.typography.bodySmall,
                color = g.textDim,
            )
        }
    }
}

@Composable
private fun Figure(label: String, toman: Long, color: Color, modifier: Modifier) {
    val g = LocalGlass.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = g.textDim)
        Text(fa(String.format(java.util.Locale.US, "%,d", toman)), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(t("تومان"), style = MaterialTheme.typography.labelSmall, color = g.textDim)
    }
}

@Composable
private fun LineRow(l: BudgetLine, color: Color, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleSmall, color = g.text)
                Text(if (l.monthly) t("هر ماه") else t("فقط همین ماه"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
            }
            Text(tm(l.amount), style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalGlass.current.textDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 22.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineDialog(start: BudgetLine, month: Int, onDismiss: () -> Unit, onSave: (BudgetLine) -> Unit, onDelete: (() -> Unit)?) {
    val g = LocalGlass.current
    var title by remember { mutableStateOf(start.title) }
    var amount by remember { mutableStateOf(if (start.amount > 0) start.amount.toString() else "") }
    var monthly by remember { mutableStateOf(start.monthly) }
    val a = amount.toLongOrNull() ?: 0
    val ideas = if (start.income) listOf("حقوق", "اجاره دریافتی", "سود بانکی", "کار پاره‌وقت")
    else listOf("اجاره", "خرجی خونه", "باشگاه", "بنزین", "قبض‌ها", "اینترنت و موبایل")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id > 0) t("ویرایش") else if (start.income) t("ورودی تازه") else t("خرج تازه")) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(t("اسم")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (start.id == 0L) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ideas.forEach { idea -> GlassChip(t(idea), title == t(idea), { title = t(idea) }) }
                    }
                }
                OutlinedTextField(
                    amount, { v -> amount = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("").take(12) },
                    label = { Text(t("مبلغ به تومان")) }, singleLine = true,
                    supportingText = { if (a > 0) Text(tm(a)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("هر ماه"), monthly, { monthly = true })
                    GlassChip(t("فقط همین ماه"), !monthly, { monthly = false })
                }
                if (monthly && start.id > 0 && month > start.month) {
                    Text(t("تغییر مبلغ روی همه ماه‌ها اثر می‌ذاره. اگه از این ماه عوض شده، این رو حذف کن و یکی تازه بساز."), style = MaterialTheme.typography.bodySmall, color = g.textDim, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && a > 0, onClick = {
                onSave(start.copy(title = title.trim(), amount = a, monthly = monthly, month = if (start.id == 0L || !monthly) month else start.month))
            }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) {
                    Text(if (start.monthly && month > start.month) t("حذف از این ماه به بعد") else t("حذف"), color = OutColor)
                }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}
