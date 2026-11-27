package com.rez.notif.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Jalali
import com.rez.notif.classify.Planner
import com.rez.notif.data.Db
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.GlassColors
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Color of a mark on the calendar, the same everywhere (screen, home, widget legend). */
fun markColor(k: Planner.MarkKind, g: GlassColors): Color = when (k) {
    Planner.MarkKind.EVENT -> g.accent
    Planner.MarkKind.EXPENSE -> g.moneyOut
    Planner.MarkKind.TRIP -> Color(0xFF3FA7D6)
    Planner.MarkKind.INSTALLMENT -> g.accent2
    Planner.MarkKind.CHEQUE_IN -> g.moneyIn
    Planner.MarkKind.CHEQUE_OUT -> Color(0xFFB0558C)
}

fun markLabel(k: Planner.MarkKind): String = when (k) {
    Planner.MarkKind.EVENT -> t("رویداد")
    Planner.MarkKind.EXPENSE -> t("خرج")
    Planner.MarkKind.TRIP -> t("سفر و دنگ")
    Planner.MarkKind.INSTALLMENT -> t("قسط")
    Planner.MarkKind.CHEQUE_IN -> t("چک دریافتی")
    Planner.MarkKind.CHEQUE_OUT -> t("چک پرداختی")
}

/** "جمعه ۶ آذر ۱۴۰۵". */
fun longDay(day: Long): String {
    val d = Jalali.ofEpochDay(day)
    return t(Jalali.WEEK[Jalali.weekday(day)]) + " " + fa("${d.d} ") + t(Jalali.MONTHS[d.m - 1]) + " " + fa(d.y.toString())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(nav: Navigator, embedded: Boolean = false, startAdding: Boolean = false) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val today = remember { LocalDate.now().toEpochDay() }
    val todayJ = remember { Jalali.ofEpochDay(today) }
    var y by rememberSaveable { mutableStateOf(todayJ.y) }
    var m by rememberSaveable { mutableStateOf(todayJ.m) }
    var selected by rememberSaveable { mutableLongStateOf(today) }
    var editing by remember { mutableStateOf<Planner.PlanEvent?>(null) }
    var adding by remember { mutableStateOf(startAdding) }
    val view by rememberLoaded(repo, y, m) { repo.calendar(y, m) }
    val scope = rememberCoroutineScope()

    fun move(n: Int) {
        val (ny, nm) = Jalali.plusMonths(y, m, n)
        y = ny; m = nm
        selected = Jalali.toEpochDay(ny, nm, 1).let { if (ny == todayJ.y && nm == todayJ.m) today else it }
    }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = t("تقویم"), subtitle = t("قسط، چک، سفر و یادآور"), onBack = if (embedded) null else nav::back) {
            IconBubble(Icons.Rounded.Add, { adding = true })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.ChevronRight, { move(-1) })
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t(Jalali.MONTHS[m - 1]) + " " + fa(y.toString()), style = MaterialTheme.typography.titleLarge, color = g.text, fontWeight = FontWeight.Bold)
                            if (y != todayJ.y || m != todayJ.m) Text(
                                t("برگرد به امروز"), style = MaterialTheme.typography.labelMedium, color = g.accent,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { y = todayJ.y; m = todayJ.m; selected = today }.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        IconBubble(Icons.Rounded.ChevronLeft, { move(1) })
                    }
                    Spacer(Modifier.height(10.dp))
                    MonthGrid(view, selected, today) { selected = it }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(longDay(selected), style = MaterialTheme.typography.titleMedium, color = g.text, modifier = Modifier.weight(1f))
                    GlassChip(t("+ افزودن"), false, { adding = true })
                }
            }
            val dayMarks = view?.marks?.filter { it.day == selected }.orEmpty()
            if (view != null && dayMarks.isEmpty()) item {
                EmptyCow(t("برای این روز چیزی نیست."), calmMotion(), Modifier.padding(horizontal = 22.dp, vertical = 4.dp), size = 56.dp)
            }
            items(dayMarks, key = { "${it.kind}-${it.refId}" }) { mk ->
                GlassCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp,
                    onClick = {
                        when (mk.kind) {
                            Planner.MarkKind.EVENT, Planner.MarkKind.EXPENSE, Planner.MarkKind.TRIP -> scope.launch { editing = repo.event(mk.refId) }
                            Planner.MarkKind.INSTALLMENT -> nav.go(Screen.Money)
                            Planner.MarkKind.CHEQUE_IN, Planner.MarkKind.CHEQUE_OUT -> nav.go(Screen.Commitments)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(markColor(mk.kind, g), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mk.title, style = MaterialTheme.typography.bodyLarge, color = g.text, maxLines = 1)
                            Text(markLabel(mk.kind), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                        }
                        if (mk.toman > 0) Text(shortToman(mk.toman * 10), style = MaterialTheme.typography.labelLarge, color = markColor(mk.kind, g))
                    }
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Planner.MarkKind.entries.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(markColor(k, g), CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(markLabel(k), style = MaterialTheme.typography.labelSmall, color = g.textDim)
                        }
                    }
                }
            }
        }
    }

    if (adding) EventDialog(
        start = Planner.PlanEvent(title = "", start = selected),
        onDismiss = { adding = false },
        onSave = { e -> adding = false; scope.launch { repo.saveEvent(e) } },
        onSaveInstallment = { b -> adding = false; scope.launch { repo.saveMyBill(b) } },
        onDelete = null, onOpenTrip = null,
    )
    editing?.let { e ->
        EventDialog(
            start = e,
            onDismiss = { editing = null },
            onSave = { ne -> editing = null; scope.launch { repo.saveEvent(ne) } },
            onSaveInstallment = null,
            onDelete = { editing = null; scope.launch { repo.deleteEvent(e.id) } },
            onOpenTrip = if (e.tripId > 0) ({ editing = null; nav.go(Screen.Trip(e.tripId)) }) else null,
        )
    }
}

@Composable
private fun MonthGrid(v: com.rez.notif.data.Repository.CalendarMonth?, selected: Long, today: Long, onPick: (Long) -> Unit) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth()) {
        Jalali.WEEK_SHORT.forEachIndexed { i, w ->
            Text(t(w), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (i == 6) g.moneyOut else g.textDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    Spacer(Modifier.height(4.dp))
    if (v == null) { Spacer(Modifier.height(240.dp)); return }
    val lead = Jalali.weekday(v.firstDay)
    val cells = lead + v.days
    val byDay = v.marks.groupBy { it.day }
    for (row in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
                val i = row * 7 + col - lead
                Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (i in 0 until v.days) {
                        val day = v.firstDay + i
                        val marks = byDay[day].orEmpty()
                        val trip = marks.firstOrNull { it.kind == Planner.MarkKind.TRIP }
                        val isSel = day == selected
                        val bg = when {
                            isSel -> g.accent
                            trip != null -> markColor(Planner.MarkKind.TRIP, g).copy(alpha = 0.18f)
                            else -> Color.Transparent
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                                .then(if (day == today && !isSel) Modifier.border(1.5.dp, g.accent, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { onPick(day) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                fa((i + 1).toString()), style = MaterialTheme.typography.bodyLarge,
                                color = when { isSel -> Color.White; col == 6 -> g.moneyOut; else -> g.text },
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                            )
                            Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                marks.map { it.kind }.distinct().take(3).forEach { k ->
                                    Box(Modifier.size(5.dp).background(if (isSel) Color.White else markColor(k, g), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayStartMillis(day: Long, hour: Int): Long =
    LocalDate.ofEpochDay(day).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Add or edit something on the calendar. "قسط" saves a monthly installment (same as in "پول") that starts in the
 * chosen month; the rest are calendar events. Trips span days and get their own dong.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDialog(
    start: Planner.PlanEvent,
    onDismiss: () -> Unit,
    onSave: (Planner.PlanEvent) -> Unit,
    onSaveInstallment: ((Db.MyBill) -> Unit)?,
    onDelete: (() -> Unit)?,
    onOpenTrip: (() -> Unit)?,
) {
    val g = LocalGlass.current
    val editing = start.id > 0
    // "INSTALLMENT" is only offered when adding.
    var kind by remember { mutableStateOf(start.kind.name) }
    var title by remember { mutableStateOf(start.title) }
    var amount by remember { mutableStateOf(if (start.toman > 0) start.toman.toString() else "") }
    var days by remember { mutableStateOf(start.days) }
    var count by remember { mutableStateOf("") }
    val remindZone = ZoneId.systemDefault()
    val oldRemind = start.remindAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(remindZone) }
    // -1: no reminder; else days before the start.
    var before by remember { mutableStateOf(oldRemind?.let { (start.start - it.toLocalDate().toEpochDay()).toInt().coerceAtLeast(0) } ?: -1) }
    var hour by remember { mutableStateOf(oldRemind?.hour ?: 9) }
    val a = amount.toLongOrNull() ?: 0
    val isInstallment = kind == "INSTALLMENT"
    val spans = kind == Planner.Kind.TRIP.name || kind == Planner.Kind.EVENT.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) t("ویرایش") else longDay(start.start)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip(t("رویداد"), kind == Planner.Kind.EVENT.name, { kind = Planner.Kind.EVENT.name })
                    GlassChip(t("خرج"), kind == Planner.Kind.EXPENSE.name, { kind = Planner.Kind.EXPENSE.name })
                    if (onSaveInstallment != null) GlassChip(t("قسط"), isInstallment, { kind = "INSTALLMENT" })
                    GlassChip(t("سفر و دنگ"), kind == Planner.Kind.TRIP.name, { kind = Planner.Kind.TRIP.name })
                }
                OutlinedTextField(
                    title, { title = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(when (kind) { "INSTALLMENT" -> t("مثلاً قسط ماشین"); Planner.Kind.TRIP.name -> t("مثلاً سفر شمال"); else -> t("عنوان") }) },
                )
                OutlinedTextField(
                    amount, { v -> amount = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("").take(12) },
                    label = { Text(if (isInstallment) t("مبلغ هر قسط (تومان)") else t("مبلغ به تومان (اختیاری)")) }, singleLine = true,
                    supportingText = { if (a > 0) Text(shortToman(a * 10)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                if (spans) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("چند روز؟"), color = g.text, modifier = Modifier.weight(1f))
                    GlassChip("−", false, { if (days > 1) days-- })
                    Text(fa(days.toString()), color = g.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                    GlassChip("+", false, { if (days < 60) days++ })
                }
                if (spans && days > 1) Text(
                    t("از ") + fa(Jalali.dayMonth(start.start)) + t(" تا ") + fa(Jalali.dayMonth(start.start + days - 1)),
                    style = MaterialTheme.typography.bodySmall, color = g.textDim,
                )
                if (isInstallment) {
                    OutlinedTextField(
                        count, { v -> count = v.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter { it.isDigit() }.joinToString("").take(3) },
                        label = { Text(t("چند قسط؟ (خالی = هر ماه)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        t("هر ماه روز ") + fa(Jalali.ofEpochDay(start.start).d.toString()) + t(" سررسید میشه. یادآوری قسط‌ها طبق تنظیمات چند روز قبل میاد."),
                        style = MaterialTheme.typography.bodySmall, color = g.textDim,
                    )
                } else {
                    Text(t("یادآوری"), style = MaterialTheme.typography.labelLarge, color = g.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassChip(t("بدون یادآوری"), before == -1, { before = -1 })
                        GlassChip(t("همون روز"), before == 0, { before = 0 })
                        GlassChip(t("یه روز قبل"), before == 1, { before = 1 })
                        GlassChip(t("سه روز قبل"), before == 3, { before = 3 })
                        GlassChip(t("یه هفته قبل"), before == 7, { before = 7 })
                    }
                    if (before >= 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(8, 12, 18, 21).forEach { h -> GlassChip(t("ساعت ") + fa(h.toString()), hour == h, { hour = h }) }
                    }
                }
                if (onOpenTrip != null) TextButton(onClick = onOpenTrip) { Text(t("حساب دنگ این سفر")) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && (!isInstallment || a > 0), onClick = {
                if (isInstallment) {
                    val d = Jalali.ofEpochDay(start.start)
                    onSaveInstallment?.invoke(Db.MyBill(title = title.trim(), amount = a, day = d.d, remaining = count.toIntOrNull() ?: 0, startYm = d.ym))
                } else {
                    val k = Planner.Kind.valueOf(kind)
                    val end = if (spans) start.start + days - 1 else start.start
                    onSave(
                        start.copy(
                            title = title.trim(), kind = k, end = end, toman = a,
                            remindAt = if (before < 0) 0 else dayStartMillis(start.start - before, hour),
                        ),
                    )
                }
            }) { Text(t("ذخیره")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(t("حذف"), color = g.moneyOut) }
                TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) }
            }
        },
    )
}
