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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Bills
import com.rez.notif.classify.Category
import com.rez.notif.classify.Cheque
import com.rez.notif.classify.Debt
import com.rez.notif.classify.Goal
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.fullTime
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.toman
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

private val InColor = Color(0xFF2E9E6A)
private val OutColor = Category.ADS.tint

private fun numOnly(v: String) = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("")

/** Toman with Persian digits (amounts on this screen are stored in toman). */
private fun tmn(amount: Long) = toman(amount * 10)

/**
 * Commitments ("طلب، بدهی، چک و قلک"): money owed to and by people, cheques with reminders,
 * installments (from the Money screen) and savings goals such as the emergency piggy bank.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommitmentsScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val view by rememberLoaded(repo) { repo.commitments() }
    var debt by remember { mutableStateOf<Debt?>(null) }
    var cheque by remember { mutableStateOf<Cheque?>(null) }
    var goal by remember { mutableStateOf<Goal?>(null) }
    var deposit by remember { mutableStateOf<Goal?>(null) }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = t("طلب، بدهی، چک و قلک"), subtitle = t("تعهدهای مالی و پس‌انداز"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val v = view ?: return@LazyColumn
            val tot = v.totals
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentPadding = 14.dp) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(t("طلب دارم"), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                            Text(tmn(tot.owedToMe), color = InColor, style = MaterialTheme.typography.titleSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t("بدهکارم"), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                            Text(tmn(tot.iOwe), color = OutColor, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (tot.chequesOut > 0 || tot.chequesIn > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t("چک‌های پاس‌نشده: ") + t("پرداختی ") + tmn(tot.chequesOut) + t("، دریافتی ") + tmn(tot.chequesIn),
                            style = MaterialTheme.typography.bodySmall, color = g.textDim,
                        )
                    }
                }
            }

            // ---------- savings goals ----------
            item {
                SectionTitle(t("قلک‌ها")) {
                    GlassChip(t("+ قلک"), false, { goal = Goal(title = "", target = 0) })
                }
            }
            if (v.goals.isEmpty()) item {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PistachioJar(0.4f, true, Modifier.size(width = 46.dp, height = 58.dp))
                    Box(Modifier.weight(1f)) {
                Hint2(t("مثلاً «قلک اضطراری»: هر ماه یه مبلغ کنار بذار تا خرج‌های یهویی از بودجه ماه نخوره. مبلغ ماهانه از «تا آخر ماه چقدر می‌تونی خرج کنی» کم میشه."))
                    }
                }
            }
            v.goals.forEach { gl ->
                item(key = "goal" + gl.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { goal = gl }, contentPadding = 12.dp) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        PistachioJar(gl.percent / 100f, calmMotion(), Modifier.size(width = 58.dp, height = 74.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(gl.title, style = MaterialTheme.typography.titleSmall, color = g.text, modifier = Modifier.weight(1f))
                            Text(fa(gl.percent) + "٪", style = MaterialTheme.typography.labelMedium, color = if (gl.done) InColor else g.accent)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(g.textDim.copy(alpha = 0.15f))) {
                            Box(Modifier.fillMaxWidth(gl.percent / 100f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (gl.done) InColor else g.accent))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tmn(gl.saved) + t(" از ") + tmn(gl.target) +
                                (if (gl.monthly > 0) t("  ·  ماهی ") + tmn(gl.monthly) else "") +
                                (if (gl.monthsLeft > 0) t("  ·  ") + fa(gl.monthsLeft) + t(" ماه دیگه") else ""),
                            style = MaterialTheme.typography.bodySmall, color = g.textDim,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassChip(t("+ گذاشتم توش"), false, { deposit = gl })
                            if (gl.monthly > 0 && !gl.done) GlassChip(t("+ ماهانه"), false, { scope.launch { repo.saveGoal(gl.copy(saved = gl.saved + gl.monthly)) } })
                        }
                        }
                      }
                    }
                }
            }

            // ---------- debts ----------
            item {
                SectionTitle(t("طلب و بدهی")) {
                    GlassChip(t("+ ثبت"), false, { debt = Debt(person = "", toman = 0, iOwe = true) })
                }
            }
            if (v.people.isNotEmpty()) item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    v.people.forEach { (p, n) ->
                        GlassChip(p + "  " + (if (n > 0) t("طلب ") else t("بدهی ")) + tmn(kotlin.math.abs(n)), false, {}, color = if (n > 0) InColor else OutColor)
                    }
                }
            }
            if (v.debts.isEmpty()) item { Hint2(t("پولی که به کسی قرض دادی یا ازش گرفتی رو اینجا بنویس تا یادت نره.")) }
            v.debts.forEach { d ->
                item(key = "debt" + d.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { debt = d }, contentPadding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (d.iOwe) t("بدهی به ") else t("طلب از ")) + d.person,
                                    style = MaterialTheme.typography.titleSmall, color = if (d.settled) g.textDim else g.text,
                                )
                                val sub = listOfNotNull(
                                    d.note.takeIf { it.isNotBlank() },
                                    if (d.due > 0) t("موعد ") + fullTime(d.due) else null,
                                    if (d.settled) t("تسویه شد ✓") else null,
                                ).joinToString("  ·  ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 1)
                            }
                            Text(tmn(d.toman), style = MaterialTheme.typography.labelMedium, color = if (d.settled) g.textDim else if (d.iOwe) OutColor else InColor)
                        }
                    }
                }
            }

            // ---------- cheques ----------
            item {
                SectionTitle(t("چک‌ها")) {
                    GlassChip(t("+ چک"), false, { cheque = Cheque(person = "", toman = 0, due = System.currentTimeMillis() + 7 * 86_400_000L, incoming = false) })
                }
            }
            if (v.cheques.isEmpty()) item { Hint2(t("چک‌هایی که دادی یا گرفتی رو با تاریخش بنویس؛ از چند روز قبل هر روز صبح یادآوری میاد.")) }
            v.cheques.forEach { q ->
                item(key = "cheque" + q.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { cheque = q }, contentPadding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (q.incoming) t("دریافتی از ") else t("پرداختی به ")) + q.person, style = MaterialTheme.typography.titleSmall, color = g.text)
                                Text(
                                    fullTime(q.due) + "  ·  " + t(q.status.label) + (if (q.number.isNotBlank()) "  ·  " + fa(q.number) else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (q.status == Cheque.Status.BOUNCED) OutColor else g.textDim,
                                )
                            }
                            Text(tmn(q.toman), style = MaterialTheme.typography.labelMedium, color = if (q.incoming) InColor else OutColor)
                        }
                    }
                }
            }

            // ---------- installments ----------
            item {
                SectionTitle(t("قسط‌ها")) { GlassChip(t("مدیریت"), false, { nav.go(Screen.Money) }) }
            }
            if (v.bills.isEmpty()) item { Hint2(t("قسط وام و قبض‌های ماهانه رو از «قسط و قبض» اضافه کن.")) }
            v.bills.forEach { b ->
                item(key = "bill" + b.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { nav.go(Screen.Money) }, contentPadding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleSmall, color = g.text)
                                Text(
                                    t("روز ") + fa(b.day) + t(" هر ماه") + (if (b.remaining > 0) t("  ·  ") + fa(b.remaining) + t(" قسط مونده") else ""),
                                    style = MaterialTheme.typography.bodySmall, color = g.textDim,
                                )
                            }
                            if (b.amount > 0) Text(tmn(b.amount), style = MaterialTheme.typography.labelMedium, color = OutColor)
                        }
                    }
                }
            }
        }
    }

    debt?.let { d ->
        DebtDialog(d, onDismiss = { debt = null }, onSave = { nd -> debt = null; scope.launch { repo.saveDebt(nd) } },
            onDelete = if (d.id > 0) ({ debt = null; scope.launch { repo.deleteDebt(d.id) } }) else null)
    }
    cheque?.let { q ->
        ChequeDialog(q, onDismiss = { cheque = null }, onSave = { nq -> cheque = null; scope.launch { repo.saveCheque(nq) } },
            onDelete = if (q.id > 0) ({ cheque = null; scope.launch { repo.deleteCheque(q.id) } }) else null)
    }
    goal?.let { gl ->
        GoalDialog(gl, onDismiss = { goal = null }, onSave = { ng -> goal = null; scope.launch { repo.saveGoal(ng) } },
            onDelete = if (gl.id > 0) ({ goal = null; scope.launch { repo.deleteGoal(gl.id) } }) else null)
    }
    deposit?.let { gl ->
        var amount by remember { mutableStateOf("") }
        val a = amount.toLongOrNull() ?: 0
        AlertDialog(
            onDismissRequest = { deposit = null },
            title = { Text(gl.title) },
            text = {
                MoneyField(amount, { amount = it }, t("چقدر گذاشتی؟ (تومان)"))
            },
            confirmButton = {
                TextButton(enabled = a > 0, onClick = { deposit = null; scope.launch { repo.saveGoal(gl.copy(saved = gl.saved + a)) } }) { Text(t("اضافه کن")) }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = a > 0, onClick = { deposit = null; scope.launch { repo.saveGoal(gl.copy(saved = (gl.saved - a).coerceAtLeast(0))) } }) { Text(t("برداشتم")) }
                    TextButton(onClick = { deposit = null }) { Text(t("بی‌خیال")) }
                }
            },
        )
    }
}

@Composable
private fun Hint2(text: String) {
    Text(text, color = LocalGlass.current.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 22.dp))
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String) {
    val a = value.toLongOrNull() ?: 0
    OutlinedTextField(
        value, { onChange(numOnly(it).take(12)) }, label = { Text(label) }, singleLine = true,
        supportingText = { if (a > 0) Text(tmn(a)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
}

/** Persian date fields (year / month / day) -> millis at noon. */
@Composable
private fun JalaliDateField(millis: Long, onChange: (Long) -> Unit) {
    val start = remember(millis) {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = if (millis > 0) millis else System.currentTimeMillis() }
        Bills.gregorianToJalali(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
    var y by remember { mutableStateOf(start.first.toString()) }
    var m by remember { mutableStateOf(start.second.toString()) }
    var d by remember { mutableStateOf(start.third.toString()) }
    fun push() {
        val yy = y.toIntOrNull() ?: return; val mm = m.toIntOrNull() ?: return; val dd = d.toIntOrNull() ?: return
        if (mm !in 1..12 || dd !in 1..31) return
        Bills.toMillis(yy, mm, dd)?.let { onChange(it + 12 * 3_600_000L) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(d, { d = numOnly(it).take(2); push() }, label = { Text(t("روز")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        OutlinedTextField(m, { m = numOnly(it).take(2); push() }, label = { Text(t("ماه")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        OutlinedTextField(y, { y = numOnly(it).take(4); push() }, label = { Text(t("سال")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.3f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtDialog(start: Debt, onDismiss: () -> Unit, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?) {
    var person by remember { mutableStateOf(start.person) }
    var amount by remember { mutableStateOf(if (start.toman > 0) start.toman.toString() else "") }
    var iOwe by remember { mutableStateOf(start.iOwe) }
    var note by remember { mutableStateOf(start.note) }
    var hasDue by remember { mutableStateOf(start.due > 0) }
    var due by remember { mutableStateOf(start.due) }
    var settled by remember { mutableStateOf(start.settled) }
    val a = amount.toLongOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id > 0) t("ویرایش") else t("طلب یا بدهی تازه")) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("من بدهکارم"), iOwe, { iOwe = true })
                    GlassChip(t("من طلب دارم"), !iOwe, { iOwe = false })
                }
                OutlinedTextField(person, { person = it.take(40) }, label = { Text(t("اسم طرف")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(amount, { amount = it }, t("مبلغ به تومان"))
                OutlinedTextField(note, { note = it.take(60) }, label = { Text(t("یادداشت (اختیاری)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("موعد داره"), hasDue, { hasDue = !hasDue; if (hasDue && due == 0L) due = System.currentTimeMillis() + 30 * 86_400_000L })
                    if (start.id > 0) GlassChip(t("تسویه شد"), settled, { settled = !settled })
                }
                if (hasDue) JalaliDateField(due) { due = it }
            }
        },
        confirmButton = {
            TextButton(enabled = person.isNotBlank() && a > 0, onClick = {
                onSave(start.copy(person = person.trim(), toman = a, iOwe = iOwe, note = note.trim(), due = if (hasDue) due else 0, settled = settled))
            }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف"), color = OutColor) }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChequeDialog(start: Cheque, onDismiss: () -> Unit, onSave: (Cheque) -> Unit, onDelete: (() -> Unit)?) {
    var person by remember { mutableStateOf(start.person) }
    var amount by remember { mutableStateOf(if (start.toman > 0) start.toman.toString() else "") }
    var incoming by remember { mutableStateOf(start.incoming) }
    var number by remember { mutableStateOf(start.number) }
    var due by remember { mutableStateOf(start.due) }
    var status by remember { mutableStateOf(start.status) }
    val a = amount.toLongOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id > 0) t("ویرایش چک") else t("چک تازه")) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("چک دادم"), !incoming, { incoming = false })
                    GlassChip(t("چک گرفتم"), incoming, { incoming = true })
                }
                OutlinedTextField(person, { person = it.take(40) }, label = { Text(if (incoming) t("از کی") else t("به کی")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(amount, { amount = it }, t("مبلغ به تومان"))
                OutlinedTextField(number, { number = numOnly(it).take(20) }, label = { Text(t("شماره چک (اختیاری)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(t("تاریخ چک"), style = MaterialTheme.typography.labelMedium)
                JalaliDateField(due) { due = it }
                if (start.id > 0) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Cheque.Status.entries.forEach { st -> GlassChip(t(st.label), status == st, { status = st }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = person.isNotBlank() && a > 0 && due > 0, onClick = {
                onSave(start.copy(person = person.trim(), toman = a, incoming = incoming, number = number, due = due, status = status))
            }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف"), color = OutColor) }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(start: Goal, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(start.title) }
    var target by remember { mutableStateOf(if (start.target > 0) start.target.toString() else "") }
    var monthly by remember { mutableStateOf(if (start.monthly > 0) start.monthly.toString() else "") }
    var saved by remember { mutableStateOf(if (start.saved > 0) start.saved.toString() else "") }
    val tg = target.toLongOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id > 0) t("ویرایش قلک") else t("قلک تازه")) },
        text = {
            Column {
                OutlinedTextField(title, { title = it.take(40) }, label = { Text(t("اسم")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (start.id == 0L) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("قلک اضطراری", "تعمیر موتور", "سفر", "گوشی تازه").forEach { idea -> GlassChip(t(idea), title == t(idea), { title = t(idea) }) }
                    }
                }
                MoneyField(target, { target = it }, t("هدف به تومان"))
                MoneyField(monthly, { monthly = it }, t("هر ماه چقدر کنار بذارم؟ (اختیاری)"))
                MoneyField(saved, { saved = it }, t("الان چقدر توشه؟"))
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && tg > 0, onClick = {
                onSave(start.copy(title = title.trim(), target = tg, monthly = monthly.toLongOrNull() ?: 0, saved = saved.toLongOrNull() ?: 0))
            }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف"), color = OutColor) }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}
