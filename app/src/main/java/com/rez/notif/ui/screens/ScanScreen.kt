package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Analyzer
import com.rez.notif.classify.Category
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.NeonRing
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.theme.LocalGlass

/**
 * First scan: reads every SMS, runs the first brain, then shows the suggested categories.
 * The user ticks the main ones; those become the big tiles on the home screen.
 */
@Composable
fun ScanScreen(onDone: () -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf<List<Analyzer.Suggestion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        try {
            val s = repo.firstScan { d, t -> done = d; total = t }
            picked.clear()
            picked.addAll(s.filter { it.recommended }.map { it.category.id })
            suggestions = s
        } catch (e: SecurityException) {
            error = t("دسترسی پیامک داده نشده.")
        } catch (e: Exception) {
            error = t("اسکن با خطا روبه‌رو شد: ") + (e.message ?: "")
        }
    }

    val list = suggestions
    if (list == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            NeonRing(progress = if (total > 0) done.toFloat() / total else null, size = 200.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fa(done), style = MaterialTheme.typography.headlineLarge, color = g.text)
                    if (total > 0) Text(t("از ") + fa(total), style = MaterialTheme.typography.bodyMedium, color = g.textDim)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                error ?: t("دارم پیامک‌ها رو می‌خونم و دسته‌ها رو حدس می‌زنم…"),
                style = MaterialTheme.typography.titleMedium,
                color = g.text,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                GlassButton(t("رد شدن"), onClick = {
                    repo.prefs.update { it.copy(firstScanDone = true) }
                    onDone()
                }, primary = false)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)) {
            item {
                Text(t("دسته‌های پیشنهادی"), style = MaterialTheme.typography.headlineMedium, color = g.text)
                Text(
                    fa(list.sumOf { it.count }) + t(" مورد خونده شد. دسته‌هایی که برات مهمن رو تیک بزن تا در صفحه اصلی بزرگ نشون داده بشن. بقیه هم هستن، فقط کوچیک‌تر."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.textDim,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            }
            items(list, key = { it.category.id }) { s ->
                val checked = s.category.id in picked
                SuggestionCard(s, checked) {
                    if (checked) picked.remove(s.category.id) else picked.add(s.category.id)
                }
            }
        }
        GlassButton(
            text = t("تأیید و شروع"),
            onClick = {
                repo.prefs.update { it.copy(firstScanDone = true, mainCategories = picked.toSet()) }
                onDone()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun SuggestionCard(s: Analyzer.Suggestion, checked: Boolean, onToggle: () -> Unit) {
    val g = LocalGlass.current
    val c: Category = s.category
    GlassCard(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        glow = if (checked) c.tint else null,
        onClick = onToggle,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(c.icon, c.tint, size = 42.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t(c.label) + "  ·  " + fa(s.count), style = MaterialTheme.typography.titleMedium, color = g.text)
                Text(t(c.description), style = MaterialTheme.typography.bodySmall, color = g.textDim)
            }
            Checkbox(checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = c.tint))
        }
        if (s.topSenders.isNotEmpty()) {
            Text(
                t("فرستنده‌ها: ") + s.topSenders.joinToString(t("، ")),
                style = MaterialTheme.typography.labelMedium,
                color = g.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        s.samples.take(2).forEach {
            Text(
                "«$it»",
                style = MaterialTheme.typography.bodySmall,
                color = g.text.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
