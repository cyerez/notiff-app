package com.rez.notif.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.classify.Dong
import com.rez.notif.classify.DongExpense
import com.rez.notif.classify.DongPerson
import com.rez.notif.classify.DongTrip
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

private val PlusColor = Color(0xFF2E9E6A)
private val MinusColor = Category.ADS.tint

/** Amounts here are toman already. */
private fun tm(toman: Long): String = fa(String.format(java.util.Locale.US, "%,d", toman)) + t(" تومان")

private fun digitsOnly(v: String) = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("")

/** Trips and nights out, each with its own people and costs. */
@Composable
fun DongScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val trips by rememberLoaded(repo) { repo.trips() }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = t("حساب دنگ"), subtitle = t("سفر و دورهمی با دوستان"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                GlassButton(t("+ سفر یا دورهمی تازه"), onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            val list = trips.orEmpty()
            if (trips != null && list.isEmpty()) {
                item {
                    Text(
                        t("یه سفر بساز، اسم دوستات رو بنویس و هر خرجی شد ثبت کن: کی داد و کی‌ها توش بودن. آخرش می‌گم کی به کی چقدر بده."),
                        color = g.textDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                }
            }
            list.forEach { trip ->
                item(key = trip.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { nav.go(Screen.Trip(trip.id)) }, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(trip.name, style = MaterialTheme.typography.titleSmall, color = g.text)
                                Text(
                                    fa(trip.people.size) + t(" نفر") + "  ·  " + fa(trip.days) + t(" روز") + "  ·  " + fa(trip.expenses.size) + t(" خرج"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = g.textDim,
                                )
                            }
                            Text(tm(Dong.total(trip)), style = MaterialTheme.typography.labelMedium, color = g.text)
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        NewTripDialog(onDismiss = { adding = false }, onSave = { trip ->
            adding = false
            scope.launch { val id = repo.saveTrip(trip); nav.go(Screen.Trip(id)) }
        })
    }
}

@Composable
private fun NewTripDialog(onDismiss: () -> Unit, onSave: (DongTrip) -> Unit) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("1") }
    var people by remember { mutableStateOf("") }
    val names = people.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("سفر یا دورهمی تازه")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(t("اسم (مثلاً سفر شمال)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    days, { days = digitsOnly(it).take(2) }, label = { Text(t("چند روز؟")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    people, { people = it }, label = { Text(t("اسم آدم‌ها، با ویرگول جدا")) },
                    supportingText = { Text(fa(names.size) + t(" نفر")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(DongTrip(name = name.trim(), days = (days.toIntOrNull() ?: 1).coerceIn(1, 60), people = names.map { DongPerson(it) }))
            }) { Text(t("بساز")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) } },
    )
}

/** One trip: people, days (who was there), costs, and who pays whom at the end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripScreen(id: Long, nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    var trip by remember { mutableStateOf<DongTrip?>(null) }
    LaunchedEffect(id) { trip = repo.trip(id) }
    var day by remember { mutableStateOf(0) } // 0 = all days
    var editingExpense by remember { mutableStateOf<Pair<Int, DongExpense>?>(null) }
    var editingPerson by remember { mutableStateOf<Int?>(null) }
    var editingTrip by remember { mutableStateOf(false) }

    fun save(n: DongTrip) {
        trip = n
        scope.launch { repo.saveTrip(n) }
    }

    val tr = trip
    Column(Modifier.imePadding()) {
        GlassTopBar(
            title = tr?.name ?: t("حساب دنگ"),
            subtitle = tr?.let { fa(it.people.size) + t(" نفر") + "  ·  " + t("جمع ") + tm(Dong.total(it)) },
            onBack = nav::back,
        ) {
            if (tr != null) {
                IconBubble(Icons.Rounded.Share, {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(tr))
                    context.startActivity(Intent.createChooser(send, null))
                })
                Spacer(Modifier.width(6.dp))
                IconBubble(Icons.Rounded.Edit, { editingTrip = true })
                Spacer(Modifier.width(6.dp))
            }
        }
        if (tr == null) return@Column
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                SectionTitle(t("آدم‌ها")) {
                    GlassChip(t("+ نفر"), false, { editingPerson = -1 })
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tr.people.forEachIndexed { i, p ->
                        GlassChip(p.name + if (p.weight > 1) " ×" + fa(p.weight) else "", false, { editingPerson = i })
                    }
                }
            }
            item { SectionTitle(t("روزها")) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tr.days > 1) GlassChip(t("همه روزها"), day == 0, { day = 0 })
                    for (d in 1..tr.days) GlassChip(t("روز ") + fa(d), day == d || (tr.days == 1 && day == 0), { day = d })
                    GlassChip(t("+ روز"), false, { save(tr.copy(days = tr.days + 1)); day = tr.days + 1 })
                }
            }
            val shownDay = if (tr.days == 1) 1 else day
            if (shownDay > 0 && tr.people.isNotEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentPadding = 12.dp) {
                        Text(t("کی‌ها روز ") + fa(shownDay) + t(" بودن؟"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        Text(t("خرج‌های تازه‌ی این روز بین همین‌ها تقسیم میشه (برای هر خرج هم میشه عوضش کرد)."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        Spacer(Modifier.height(8.dp))
                        val here = tr.presentOn(shownDay)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tr.people.forEachIndexed { i, p ->
                                GlassChip(p.name, i in here, {
                                    val n = if (i in here) here - i else here + i
                                    save(tr.copy(present = tr.present + (shownDay to n)))
                                })
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle(if (shownDay > 0) t("خرج‌های روز ") + fa(shownDay) + "  ·  " + tm(Dong.total(tr, shownDay)) else t("همه‌ی خرج‌ها")) {
                    if (tr.people.isNotEmpty()) GlassChip(t("+ خرج"), false, {
                        val d = if (shownDay > 0) shownDay else tr.days
                        editingExpense = -1 to DongExpense("", 0, payer = 0, day = d, people = tr.presentOn(d))
                    })
                }
            }
            if (tr.people.isEmpty()) {
                item { Hint(t("اول آدم‌ها رو اضافه کن.")) }
            }
            val shown = tr.expenses.withIndex().filter { shownDay == 0 || it.value.day == shownDay }
            if (tr.people.isNotEmpty() && shown.isEmpty()) item { Hint(t("هنوز خرجی ثبت نشده.")) }
            shown.forEach { (i, e) ->
                item(key = "e$i") {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { editingExpense = i to e }, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title.ifBlank { t("خرج") }, style = MaterialTheme.typography.titleSmall, color = g.text)
                                val who = if (e.people.size == tr.people.size) t("همه") else fa(e.people.size) + t(" نفر")
                                Text(
                                    (if (shownDay == 0 && tr.days > 1) t("روز ") + fa(e.day) + "  ·  " else "") +
                                        tr.people.getOrNull(e.payer)?.name.orEmpty() + t(" داد") + "  ·  " + who,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = g.textDim,
                                )
                            }
                            Text(tm(e.amount), style = MaterialTheme.typography.labelMedium, color = g.text)
                        }
                    }
                }
            }
            if (tr.expenses.isNotEmpty()) {
                val balances = Dong.balances(tr)
                item { SectionTitle(t("تسویه حساب کل سفر")) }
                item {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                        balances.forEach { b ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr.people[b.person].name, style = MaterialTheme.typography.bodyMedium, color = g.text)
                                    Text(t("داده ") + tm(b.paid) + "  ·  " + t("سهم ") + tm(b.share), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                                }
                                val n = b.net
                                Text(
                                    when {
                                        n > 0 -> t("می‌گیره ") + tm(n)
                                        n < 0 -> t("بده ") + tm(-n)
                                        else -> t("بی‌حساب")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (n > 0) PlusColor else if (n < 0) MinusColor else g.textDim,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                val transfers = Dong.settle(balances)
                if (transfers.isNotEmpty()) {
                    item { SectionTitle(t("کی به کی بده")) }
                    item {
                        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), glow = PlusColor, contentPadding = 12.dp) {
                            transfers.forEach { x ->
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        tr.people[x.from].name + t(" به ") + tr.people[x.to].name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = g.text,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(tm(x.amount), style = MaterialTheme.typography.labelMedium, color = g.text, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingExpense?.let { (i, e) ->
        ExpenseDialog(
            tr ?: return@let, e,
            onDismiss = { editingExpense = null },
            onSave = { ne ->
                editingExpense = null
                val t0 = trip ?: return@ExpenseDialog
                save(t0.copy(expenses = if (i < 0) t0.expenses + ne else t0.expenses.mapIndexed { j, old -> if (j == i) ne else old }))
            },
            onDelete = if (i >= 0) ({
                editingExpense = null
                val t0 = trip
                if (t0 != null) save(t0.copy(expenses = t0.expenses.filterIndexed { j, _ -> j != i }))
            }) else null,
        )
    }
    editingPerson?.let { i ->
        val t0 = tr ?: return@let
        PersonDialog(
            start = t0.people.getOrNull(i),
            canDelete = i >= 0 && Dong.removePerson(t0, i) != null,
            onDismiss = { editingPerson = null },
            onSave = { name, w ->
                editingPerson = null
                save(if (i < 0) Dong.addPerson(t0, name, w) else t0.copy(people = t0.people.mapIndexed { j, p -> if (j == i) DongPerson(name, w) else p }))
            },
            onDelete = { editingPerson = null; Dong.removePerson(t0, i)?.let { save(it) } },
        )
    }
    if (editingTrip && tr != null) {
        TripDialog(
            tr,
            onDismiss = { editingTrip = false },
            onSave = { name, days ->
                editingTrip = false
                if (day > days) day = 0
                save(tr.copy(name = name, days = days, present = tr.present.filterKeys { it <= days }))
            },
            onDelete = { editingTrip = false; scope.launch { repo.deleteTrip(tr.id); nav.back() } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalGlass.current.textDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 22.dp))
}

private fun shareText(trip: DongTrip): String = buildString {
    appendLine(trip.name + " · " + t("جمع ") + tm(Dong.total(trip)))
    val b = Dong.balances(trip)
    b.forEach { appendLine(trip.people[it.person].name + ": " + t("داده ") + tm(it.paid) + "، " + t("سهم ") + tm(it.share)) }
    val tr = Dong.settle(b)
    if (tr.isNotEmpty()) {
        appendLine()
        tr.forEach { appendLine(trip.people[it.from].name + t(" به ") + trip.people[it.to].name + ": " + tm(it.amount)) }
    }
}.trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(trip: DongTrip, start: DongExpense, onDismiss: () -> Unit, onSave: (DongExpense) -> Unit, onDelete: (() -> Unit)?) {
    val g = LocalGlass.current
    var title by remember { mutableStateOf(start.title) }
    var amount by remember { mutableStateOf(if (start.amount > 0) start.amount.toString() else "") }
    var payer by remember { mutableStateOf(start.payer.coerceIn(0, (trip.people.size - 1).coerceAtLeast(0))) }
    var day by remember { mutableStateOf(start.day.coerceIn(1, trip.days)) }
    var people by remember { mutableStateOf(start.people) }
    val a = amount.toLongOrNull() ?: 0
    val ok = a > 0 && people.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete != null) t("ویرایش خرج") else t("خرج تازه")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text(t("برای چی؟ (مثلاً شام)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = digitsOnly(it).take(12) }, label = { Text(t("مبلغ به تومان")) }, singleLine = true,
                    supportingText = { if (a > 0) Text(tm(a)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Label(t("کی پول داد؟"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trip.people.forEachIndexed { i, p -> GlassChip(p.name, payer == i, { payer = i }) }
                }
                Label(t("بین کی‌ها تقسیم بشه؟") + "  " + fa(people.size) + t(" نفر"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip(t("همه"), people.size == trip.people.size, { people = trip.people.indices.toSet() })
                    if (trip.present.containsKey(day)) GlassChip(t("حاضرهای روز ") + fa(day), people == trip.presentOn(day), { people = trip.presentOn(day) })
                    trip.people.forEachIndexed { i, p -> GlassChip(p.name, i in people, { people = if (i in people) people - i else people + i }) }
                }
                if (a > 0 && people.isNotEmpty()) {
                    val each = Dong.split(DongExpense("", a, payer, day, people), trip.people)
                    val one = each.values.minOrNull() ?: 0
                    Text(t("نفری حدود ") + tm(one), style = MaterialTheme.typography.bodySmall, color = g.textDim, modifier = Modifier.padding(top = 6.dp))
                }
                if (trip.days > 1) {
                    Label(t("کدوم روز؟"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in 1..trip.days) GlassChip(fa(d), day == d, { day = d })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onSave(DongExpense(title.trim(), a, payer, day, people)) }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف"), color = MinusColor) }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalGlass.current.text, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonDialog(start: DongPerson?, canDelete: Boolean, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: () -> Unit) {
    val g = LocalGlass.current
    var name by remember { mutableStateOf(start?.name.orEmpty()) }
    var weight by remember { mutableStateOf(start?.weight ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start == null) t("نفر تازه") else t("ویرایش")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(t("اسم")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Label(t("چند سهم حساب بشه؟"))
                Text(t("مثلاً یه زوج یا خانواده‌ی سه نفره رو یه نفر با ۲ یا ۳ سهم بنویس."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (w in 1..5) GlassChip(fa(w), weight == w, { weight = w })
                }
                if (start != null && !canDelete) {
                    Text(t("چون توی خرج‌ها هست، پاک نمیشه."), style = MaterialTheme.typography.bodySmall, color = g.textDim, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), weight) }) { Text(t("ذخیره")) } },
        dismissButton = {
            Row {
                if (start != null && canDelete) TextButton(onClick = onDelete) { Text(t("حذف"), color = MinusColor) }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}

@Composable
private fun TripDialog(trip: DongTrip, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: () -> Unit) {
    val g = LocalGlass.current
    var name by remember { mutableStateOf(trip.name) }
    var days by remember { mutableStateOf(trip.days.toString()) }
    var sure by remember { mutableStateOf(false) }
    val usedDays = trip.expenses.maxOfOrNull { it.day } ?: 1
    val d = days.toIntOrNull() ?: 0
    val ok = name.isNotBlank() && d in usedDays..60
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("ویرایش سفر")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(t("اسم")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    days, { days = digitsOnly(it).take(2) }, label = { Text(t("چند روز؟")) }, singleLine = true,
                    isError = !ok,
                    supportingText = { if (usedDays > 1) Text(t("کمتر از ") + fa(usedDays) + t(" نمیشه، چون خرج داره.")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { if (sure) onDelete() else sure = true }) {
                    Text(if (sure) t("مطمئنی؟ دوباره بزن تا پاک بشه") else t("پاک کردن این سفر"), color = MinusColor)
                }
                if (sure) Text(t("همه‌ی خرج‌هاش هم پاک میشه."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(name.trim(), d) }) { Text(t("ذخیره")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) } },
    )
}
