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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.classify.Rule
import com.rez.notif.classify.RuleAction
import com.rez.notif.classify.Rules
import com.rez.notif.data.Repository
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.senderLabel
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The user's own rules: "when this comes, do that". First match wins, top to bottom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val rules by rememberLoaded(repo) { repo.userRules() }
    val list = rules.orEmpty()

    Column {
        GlassTopBar(title = t("قانون‌ها"), subtitle = fa(list.size) + t(" قانون"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth(), glow = g.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.Tune, g.accent, size = 46.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            t("خودت بگو با هر نوتیف یا پیامک چی کار کنم: همیشه نشون بده، بی‌صدا نگه دار، یادت بندازم یا پاکش کنم. قانون بالاتر اول حساب میشه."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = g.text,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassButton(t("قانون تازه"), icon = Icons.Rounded.Add, onClick = { nav.go(Screen.RuleEdit(Rule())) }, modifier = Modifier.fillMaxWidth())
                }
            }
            items(list, key = { it.id }) { r ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Screen.RuleEdit(r)) }, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name.ifBlank { t("بی‌نام") }, style = MaterialTheme.typography.titleSmall, color = if (r.enabled) g.text else g.textDim)
                            Text(describe(r), style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(
                            checked = r.enabled,
                            onCheckedChange = { on -> scope.launch { repo.saveRule(r.copy(enabled = on)) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = g.accent, checkedThumbColor = Color.White),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconBubble(Icons.Rounded.KeyboardArrowUp, { scope.launch { repo.moveRule(r.id, up = true) } })
                        IconBubble(Icons.Rounded.KeyboardArrowDown, { scope.launch { repo.moveRule(r.id, up = false) } })
                        Spacer(Modifier.weight(1f))
                        IconBubble(Icons.Rounded.Delete, { scope.launch { repo.deleteRule(r.id) } })
                    }
                }
            }
            item {
                SectionTitle(t("شروع از نمونه‌ها"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rules.templates.forEach { tpl ->
                        GlassChip(t(tpl.name), selected = false, icon = Icons.Rounded.Add, onClick = { nav.go(Screen.RuleEdit(tpl)) })
                    }
                }
            }
        }
    }
}

/** One line in plain words: "Telegram · words: urgent · 23–7 → always show". */
private fun describe(r: Rule): String {
    val parts = mutableListOf<String>()
    if (r.apps.isNotEmpty()) parts += r.apps.joinToString(t("، ")) { if (it == "sms") t("پیامک") else it.substringAfterLast('.') }
    if (r.sender.isNotBlank()) parts += t("فرستنده: ") + r.sender
    if (r.words.isNotEmpty()) parts += (if (r.regex) t("الگو: ") else t("کلمه: ")) + r.words.joinToString(t("، "))
    if (r.categories.isNotEmpty()) parts += r.categories.joinToString(t("، ")) { t(Category.of(it).label) }
    if (r.fromHour >= 0 && r.toHour >= 0 && r.fromHour != r.toHour) parts += fa(r.fromHour) + t(" تا ") + fa(r.toHour)
    val cond = if (parts.isEmpty()) t("همه") else parts.joinToString("  ·  ")
    val act = if (r.action == RuleAction.MOVE) t("ببر به ") + t(Category.of(r.target).label) else t(r.action.label)
    return "$cond  ←  $act"
}

/** Make or change a rule, with a live preview on the last 7 days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(start: Rule, nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    var rule by remember(start) { mutableStateOf(start.copy(name = if (start.id == 0L) t(start.name) else start.name)) }
    var wordsText by remember(start) { mutableStateOf(start.words.joinToString(if (start.regex) "" else "، ")) }
    var preview by remember { mutableStateOf<Repository.RulePreview?>(null) }
    val apps by rememberLoaded(repo) { repo.knownApps() }
    val timed = rule.fromHour >= 0 && rule.toHour >= 0

    fun words(text: String, regex: Boolean) =
        if (regex) listOf(text.trim()).filter { it.isNotEmpty() } else text.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    LaunchedEffect(rule) {
        delay(350)
        preview = repo.previewRule(rule)
    }

    Column(Modifier.imePadding()) {
        GlassTopBar(title = if (start.id == 0L) t("قانون تازه") else t("ویرایش قانون"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(rule.name, { rule = rule.copy(name = it) }, label = { Text(t("اسم قانون")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                SectionTitle(t("از کجا"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("همه"), rule.apps.isEmpty(), { rule = rule.copy(apps = emptySet()) })
                    GlassChip(t("پیامک"), "sms" in rule.apps, { rule = rule.copy(apps = rule.apps.toggle("sms")) })
                    val shown = (apps.orEmpty().map { it.packageName to it.appName } +
                        rule.apps.filter { it != "sms" && apps.orEmpty().none { a -> a.packageName == it } }.map { it to it.substringAfterLast('.') })
                    shown.forEach { (pkg, name) ->
                        GlassChip(name, pkg in rule.apps, { rule = rule.copy(apps = rule.apps.toggle(pkg)) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    rule.sender,
                    { rule = rule.copy(sender = it) },
                    label = { Text(t("فرستنده یا اسم چت (اختیاری)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    wordsText,
                    { wordsText = it; rule = rule.copy(words = words(it, rule.regex)) },
                    label = { Text(if (rule.regex) t("الگوی regex") else t("کلمه‌ها، با کاما جدا (هر کدوم بیاد کافیه)")) },
                    isError = rule.regex && wordsText.isNotBlank() && !Rules.validRegex(wordsText.trim()),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rule.regex,
                        onCheckedChange = { rule = rule.copy(regex = it, words = words(wordsText, it)) },
                        colors = CheckboxDefaults.colors(checkedColor = g.accent),
                    )
                    Text(t("الگوی پیشرفته (regex)"), style = MaterialTheme.typography.bodyMedium, color = g.text)
                }
            }
            item {
                SectionTitle(t("فقط این دسته‌ها (اختیاری)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        GlassChip(t(c.label), c.id in rule.categories, { rule = rule.copy(categories = rule.categories.toggle(c.id)) }, color = c.tint, icon = c.icon)
                    }
                }
            }
            item {
                SectionTitle(t("ساعت"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("همه روز"), !timed, { rule = rule.copy(fromHour = -1, toHour = -1) })
                    GlassChip(t("شب (۱۱ تا ۷)"), rule.fromHour == 23 && rule.toHour == 7, { rule = rule.copy(fromHour = 23, toHour = 7) })
                    GlassChip(t("ساعت کاری (۹ تا ۱۷)"), rule.fromHour == 9 && rule.toHour == 17, { rule = rule.copy(fromHour = 9, toHour = 17) })
                    GlassChip(t("دلخواه"), timed && !(rule.fromHour == 23 && rule.toHour == 7) && !(rule.fromHour == 9 && rule.toHour == 17), {
                        if (!timed) rule = rule.copy(fromHour = 8, toHour = 12)
                    })
                }
                if (timed) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        HourStepper(t("از"), rule.fromHour) { rule = rule.copy(fromHour = it) }
                        Spacer(Modifier.width(16.dp))
                        HourStepper(t("تا"), rule.toHour) { rule = rule.copy(toHour = it) }
                    }
                }
            }
            item {
                SectionTitle(t("چی کار کنم"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleAction.entries.forEach { a ->
                        GlassChip(t(a.label), rule.action == a, { rule = rule.copy(action = a, target = if (a == RuleAction.MOVE && rule.target.isBlank()) Category.OTHER.id else rule.target) })
                    }
                }
                if (rule.action == RuleAction.MOVE) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.forEach { c ->
                            GlassChip(t(c.label), rule.target == c.id, { rule = rule.copy(target = c.id) }, color = c.tint, icon = c.icon)
                        }
                    }
                }
            }
            item {
                val p = preview
                GlassCard(Modifier.fillMaxWidth(), glow = g.accent, contentPadding = 14.dp) {
                    Text(t("پیش‌نمایش روی ۷ روز گذشته"), style = MaterialTheme.typography.titleSmall, color = g.text)
                    Text(
                        if (p == null) t("دارم حساب می‌کنم…") else t("این قانون ") + fa(p.count) + t(" مورد رو می‌گرفت."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = g.textDim,
                    )
                    p?.examples?.forEach { i ->
                        Text(
                            "• " + (if (i.source == com.rez.notif.data.Source.SMS) senderLabel(i.sender) else i.appName) + ": " +
                                i.body.replace('\n', ' ').take(60) + "  (" + shortTime(i.timestamp) + ")",
                            style = MaterialTheme.typography.bodySmall,
                            color = g.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                val badRegex = rule.regex && rule.words.isNotEmpty() && !Rules.validRegex(rule.words.first())
                GlassButton(
                    t("ذخیره"),
                    enabled = !badRegex,
                    onClick = {
                        scope.launch {
                            val id = repo.saveRule(rule)
                            repo.applyRulesToPast(rule.copy(id = id))
                            nav.back()
                            if (nav.current !is Screen.Rules) nav.go(Screen.Rules)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rule.action == RuleAction.MOVE || rule.action == RuleAction.DELETE) {
                    Text(
                        t("موقع ذخیره، روی پیام‌های ۳۰ روز گذشته هم اجرا میشه (به جز افراد منتخب و ستاره‌دارها)."),
                        style = MaterialTheme.typography.bodySmall,
                        color = g.textDim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = g.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        IconBubble(Icons.Rounded.Remove, { onChange((value + 23) % 24) })
        Text(fa(value), color = g.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        IconBubble(Icons.Rounded.Add, { onChange((value + 1) % 24) })
    }
}

private fun Set<String>.toggle(x: String): Set<String> = if (x in this) this - x else this + x
