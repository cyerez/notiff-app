package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.Accent
import com.rez.notif.data.AppStyle
import com.rez.notif.data.Season
import com.rez.notif.data.RelockMode
import com.rez.notif.data.ThemeMode
import com.rez.notif.i18n.Lang
import com.rez.notif.security.AppLock
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.pressable
import com.rez.notif.ui.components.softShadow
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.theme.LocalGlass
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.rez.notif.sms.DefaultSms
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val s by repo.prefs.state.collectAsState()
    val scope = rememberCoroutineScope()
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column {
        GlassTopBar(title = t("تنظیمات"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp)) {
            item { SectionTitle(t("ظاهر")) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text("زبان / Language", style = MaterialTheme.typography.titleSmall, color = g.text)
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Lang.entries.forEach { l -> GlassChip(l.label, s.language == l, { repo.prefs.update { it.copy(language = l) } }) }
                    }
                    Text(t("سبک"), style = MaterialTheme.typography.titleSmall, color = g.text)
                    FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppStyle.entries.forEach { st ->
                            GlassChip(t(st.label), s.style == st, { repo.prefs.update { it.copy(style = st) } })
                        }
                    }
                    if (s.style == AppStyle.SEASONS) {
                        Text(t("فصل"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Season.entries.forEach { se ->
                                GlassChip(t(se.label), s.season == se, { repo.prefs.update { it.copy(season = se) } })
                            }
                        }
                    }
                    Text(t("حالت"), style = MaterialTheme.typography.titleSmall, color = g.text)
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip(t("خودکار"), s.themeMode == ThemeMode.SYSTEM, { repo.prefs.update { it.copy(themeMode = ThemeMode.SYSTEM) } })
                        GlassChip(t("روشن"), s.themeMode == ThemeMode.LIGHT, { repo.prefs.update { it.copy(themeMode = ThemeMode.LIGHT) } })
                        GlassChip(t("تیره"), s.themeMode == ThemeMode.DARK, { repo.prefs.update { it.copy(themeMode = ThemeMode.DARK) } })
                    }
                    if (s.style == AppStyle.GLASS) {
                        Text(t("رنگ نئون"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Accent.entries.forEach { a -> AccentDot(a, a == s.accent) { repo.prefs.update { it.copy(accent = a) } } }
                        }
                        Text(t(s.accent.label), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                    }
                    ToggleRow(t("انیمیشن‌ها"), t("پس‌زمینه متحرک و حرکت‌های کوچک"), s.animations) { v ->
                        repo.prefs.update { it.copy(animations = v) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(t("تصویر پس‌زمینه"), style = MaterialTheme.typography.titleSmall, color = g.text)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to t("کامل"), 1 to t("کم‌رنگ"), 2 to t("خیلی کم‌رنگ")).forEach { (d, label) ->
                            GlassChip(label, s.backdropDim == d, { repo.prefs.update { it.copy(backdropDim = d) } })
                        }
                    }
                }
            }

            item { SectionTitle(t("دسته‌ها و تبلیغ")) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    ToggleRow(t("مخفی کردن تبلیغ‌ها"), t("نوتیف تبلیغی (و نوتیف پیامک تبلیغی) فوراً پاک میشه و فقط در «تبلیغات مخفی‌شده» می‌مونه."), s.hideAds) { v ->
                        repo.prefs.update { it.copy(hideAds = v) }
                    }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(t("مخفی کردن فوری همه نوتیف‌ها"), t("هر نوتیف تازه همون لحظه از نوار بالای گوشی برداشته میشه و فقط توی دسته خودش در این اپ می‌مونه."), s.hideAll) { v ->
                        repo.prefs.update { it.copy(hideAll = v) }
                    }
                    if (s.hideAll) {
                        Spacer(Modifier.height(10.dp))
                        Text(t("نوتیف چقدر روی نوار بمونه؟"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        Text(t("پیام‌های معمولی اول چند ثانیه می‌مونن که بخونیشون، بعد میرن توی اپ. تبلیغ همیشه فوری میره."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 15, 30, 60, 120).forEach { sec ->
                                GlassChip(if (sec == 0) t("فوری") else if (sec < 60) fa(sec) + t(" ثانیه") else fa(sec / 60) + t(" دقیقه"), s.holdSeconds == sec, {
                                    repo.prefs.update { it.copy(holdSeconds = sec) }
                                })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(t("کد تأیید روی گوشی بمونه"), t("کد ورود و رمز پویا با دکمه «کپی کد» تا ۱۰ دقیقه روی نوار گوشی می‌مونه."), s.keepOtp) { v ->
                            repo.prefs.update { it.copy(keepOtp = v) }
                        }
                        if (s.keepOtp) {
                            Spacer(Modifier.height(10.dp))
                            CodeBoxSettings(s.codeOverlay, s.codeSeconds, onOverlay = { v -> repo.prefs.update { it.copy(codeOverlay = v) } }, onSeconds = { sec -> repo.prefs.update { it.copy(codeSeconds = sec) } })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(t("میس‌کال‌ها و پیام افراد منتخب هیچ‌وقت از نوار برداشته نمیشن و تا نخونی توی خانه می‌مونن."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(t("یک نوتیف خلاصه"), t("به جای همه، یک نوتیف آروم از «نوتیف» میاد که میگه چی رسیده. با باز کردن اپ پاک میشه."), s.summary) { v ->
                            repo.prefs.update { it.copy(summary = v) }
                        }
                        if (s.summary) {
                            Spacer(Modifier.height(8.dp))
                            Text(t("خلاصه کی بیاد؟"), style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(t("اگه ساعت انتخاب کنی، خلاصه فقط همون ساعت‌ها با صدا میاد، نه بعد از هر نوتیف."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassChip(t("همون لحظه"), s.digestHours.isEmpty(), {
                                    repo.prefs.update { it.copy(digestHours = emptySet()) }
                                    com.rez.notif.notif.Digest.arm(context)
                                })
                                listOf(8, 13, 17, 21).forEach { h ->
                                    GlassChip(t("ساعت ") + fa(h), h in s.digestHours, {
                                        repo.prefs.update { st -> st.copy(digestHours = if (h in st.digestHours) st.digestHours - h else st.digestHours + h) }
                                        com.rez.notif.notif.Digest.arm(context)
                                    })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(t("مرتب کردن نوتیف‌ها موقع ورود"), t("با باز کردن اپ، نوتیف‌های قبلی هم با همین قانون‌ها از نوار گوشی پاک میشن."), s.clearOnOpen) { v ->
                        repo.prefs.update { it.copy(clearOnOpen = v) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(t("ساعت سکوت"), style = MaterialTheme.typography.titleSmall, color = g.text)
                    Text(t("توی این ساعت‌ها فقط کد تأیید و قانون‌های «همیشه نشون بده» روی گوشی میان؛ بقیه بی‌صدا توی اپ جمع میشن."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1 to -1, 23 to 7, 0 to 8, 22 to 6).forEach { (from, to) ->
                            val label = if (from < 0) t("خاموش") else fa(from) + t(" تا ") + fa(to)
                            GlassChip(label, s.quietFrom == from && s.quietTo == to, { repo.prefs.update { it.copy(quietFrom = from, quietTo = to) } })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(t("قانون‌ها"), onClick = { nav.go(Screen.Rules) }, primary = false)
                        GlassButton(t("افراد منتخب"), onClick = { nav.go(Screen.Vip) }, primary = false)
                        GlassButton(t("پاک‌سازی هوشمند"), onClick = { nav.go(Screen.Cleanup) }, primary = false)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(t("دسته‌های اصلی صفحه خانه"), style = MaterialTheme.typography.titleSmall, color = g.text)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.filter { it != Category.ADS }.forEach { c ->
                            val on = c.id in s.mainCategories
                            GlassChip(t(c.label), on, color = c.tint, icon = c.icon, onClick = {
                                repo.prefs.update { st ->
                                    st.copy(mainCategories = if (on) st.mainCategories - c.id else st.mainCategories + c.id)
                                }
                            })
                        }
                    }
                }
            }

            item { BatteryCard() }
            item { SectionTitle(t("اپ پیامک گوشی")) }
            item { SmsAppCard() }

            item { SectionTitle(t("امنیت و پشتیبان")) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    ToggleRow(t("قفل با اثر انگشت"), t("موقع باز کردن اپ یه بار اثر انگشت یا رمز گوشی می‌خواد."), s.appLock) { v ->
                        // Ask for the finger first, so nobody can turn the lock on or off without it.
                        (context as? Activity)?.let { a ->
                            AppLock.authenticate(a, if (v) t("روشن کردن قفل") else t("خاموش کردن قفل")) { ok ->
                                if (ok) repo.prefs.update { it.copy(appLock = v) }
                            }
                        }
                    }
                    if (s.appLock) {
                        Spacer(Modifier.height(10.dp))
                        Text(t("دوباره قفل بشه"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RelockMode.entries.forEach { m ->
                                GlassChip(t(m.label), s.relock == m, { repo.prefs.update { it.copy(relock = m) } })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(t("پشتیبان رمزدار"), onClick = { nav.go(Screen.Backup) }, primary = false)
                        GlassButton(t("انتقال به گوشی دیگه"), onClick = { nav.go(Screen.Transfer) }, primary = false)
                        GlassButton(t("یادگرفته‌ها"), onClick = { nav.go(Screen.Learned) }, primary = false)
                        GlassButton(t("کیف پسورد"), onClick = { nav.go(Screen.Wallet) }, primary = false)
                    }
                }
            }

            item { SectionTitle(t("داده‌ها")) }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text(
                        t("همه چیز فقط روی همین گوشی ذخیره میشه. این اپ اصلاً اجازه اینترنت نداره."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = g.textDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(t("همگام‌سازی پیامک‌ها"), onClick = {
                            scope.launch {
                                val n = runCatching { repo.syncSms() }.getOrNull()
                                syncMsg = if (n == null) t("خطا در خوندن پیامک‌ها") else fa(n) + t(" پیامک جدید اضافه شد")
                            }
                        }, primary = false)
                        GlassButton(t("اسکن و دسته‌بندی دوباره"), onClick = {
                            repo.prefs.update { it.copy(firstScanDone = false) }
                            nav.reset(Screen.Scan)
                        }, primary = false)
                        GlassButton(t("مرتب کردن «سایر» با مدل هوشمند"), onClick = {
                            scope.launch {
                                val n = repo.relabelOthersNow()
                                syncMsg = fa(n) + t(" پیامک از «سایر» به دسته درست رفت")
                            }
                        }, primary = false)
                        GlassButton(t("دسترسی‌ها"), onClick = { nav.go(Screen.Onboarding) }, primary = false)
                        GlassButton(t("پاک کردن همه"), onClick = { confirmWipe = true }, primary = false)
                    }
                    syncMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = g.accent, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            item {
                Text(
                    t("نوتیف · نسخه ") + fa(runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "") + t(" (آزمایشی)"),
                    style = MaterialTheme.typography.labelMedium,
                    color = g.textDim,
                    modifier = Modifier.padding(22.dp),
                )
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(t("پاک کردن همه داده‌ها؟")) },
            text = { Text(t("همه پیامک‌ها و نوتیف‌های ذخیره‌شده در این اپ پاک میشن. پیامک‌های خود گوشی دست نمی‌خورن.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch { repo.deleteAll() }
                }) { Text(t("پاک کن"), color = Category.ADS.tint) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(t("نه")) } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val g = LocalGlass.current
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = g.text)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = g.textDim)
        }
        Spacer(Modifier.size(8.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = g.accent, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun AccentDot(a: Accent, selected: Boolean, onClick: () -> Unit) {
    val c = Color(a.color)
    Box(
        Modifier
            .size(36.dp)
            .softShadow(CircleShape, c.copy(alpha = if (selected) 0.6f else 0.25f), if (selected) 12.dp else 4.dp)
            .background(Brush.linearGradient(listOf(c, Color(a.second))), CircleShape)
            .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.9f else 0.4f), CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * Samsung puts apps to sleep, and then notifications stop being saved.
 * Shows a warning with a one-tap fix only while the app is not exempt.
 */
@Composable
private fun BatteryCard() {
    val g = LocalGlass.current
    val context = LocalContext.current
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    var exempt by remember { mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    if (exempt) return
    SectionTitle(t("کار در پس‌زمینه"))
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), glow = Category.ADS.tint) {
        Text(t("گوشی ممکنه نوتیف رو بخوابونه و بعضی نوتیف‌ها ذخیره نشن."), style = MaterialTheme.typography.titleSmall, color = g.text)
        Text(
            t("دکمه زیر رو بزن و «اجازه» بده. توی سامسونگ اینم بزن: تنظیمات > مراقبت از دستگاه > باتری > محدودیت‌های پس‌زمینه > «برنامه‌های هرگز در حالت خواب» و نوتیف رو اضافه کن."),
            style = MaterialTheme.typography.bodySmall,
            color = g.textDim,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        GlassButton(t("بیدار نگه داشتن نوتیف"), onClick = {
            runCatching {
                ask.launch(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:" + context.packageName)),
                )
            }
        }, modifier = Modifier.fillMaxWidth())
    }
}

/** Phase 5: make Notif the phone's SMS app (or go back), and what that turns on. */
@Composable
private fun SmsAppCard() {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val s by repo.prefs.state.collectAsState()
    var isDefault by remember { mutableStateOf(DefaultSms.isDefault(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultSms.isDefault(context)
    }
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        if (!isDefault) {
            Text(t("نوتیف به جای پیام‌رسان سامسونگ"), style = MaterialTheme.typography.titleSmall, color = g.text)
            Spacer(Modifier.height(4.dp))
            Text(
                t("اگه نوتیف اپ پیامک گوشی بشه: پیامک‌ها مستقیم اینجا میان و از همینجا جواب میدی، حذف واقعاً از گوشی پاک می‌کنه و تبلیغ‌ها رو میشه کامل بلاک کرد. پیام‌های MMS (عکس و صدا) باز نمیشن. هر وقت خواستی برمی‌گردی به پیام‌رسان قبلی."),
                style = MaterialTheme.typography.bodySmall,
                color = g.textDim,
            )
            Spacer(Modifier.height(10.dp))
            GlassButton(t("نوتیف رو اپ پیامک گوشی کن"), onClick = {
                DefaultSms.requestIntent(context)?.let { roleLauncher.launch(it) }
            })
        } else {
            Text(t("نوتیف الان اپ پیامک گوشیه."), style = MaterialTheme.typography.titleSmall, color = g.text)
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                t("حذف از گوشی هم"),
                t("حذف و پاک‌سازی در نوتیف، پیامک رو از خود گوشی هم پاک می‌کنه. تا چند ثانیه با «برگردون» برمی‌گرده."),
                s.phoneDelete,
            ) { v -> repo.prefs.update { it.copy(phoneDelete = v) } }
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                t("بلاک کامل پیامک تبلیغاتی"),
                t("پیامک تبلیغاتی اصلاً وارد صندوق پیامک گوشی نمیشه؛ فقط در «تبلیغات مخفی‌شده» همین اپ می‌مونه."),
                s.blockAdSms,
            ) { v -> repo.prefs.update { it.copy(blockAdSms = v) } }
            Spacer(Modifier.height(10.dp))
            GlassButton(t("برگشت به پیام‌رسان قبلی"), primary = false, onClick = {
                runCatching { roleLauncher.launch(DefaultSms.chooseOtherIntent()) }
            })
        }
    }
}

/** The "copy code" box on top of the screen: on/off, the "appear on top" permission, and how long it stays. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CodeBoxSettings(on: Boolean, seconds: Int, onOverlay: (Boolean) -> Unit, onSeconds: (Int) -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(com.rez.notif.notif.CodeOverlay.allowed(context)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        allowed = com.rez.notif.notif.CodeOverlay.allowed(context)
    }
    ToggleRow(t("باکس کپی کد بالای صفحه"), t("وقتی کد میاد یه باکس با دکمه «کپی کد» بالای صفحه می‌مونه، هر اپی باز باشه. با کشیدن به بالا یا × بسته میشه."), on, onOverlay)
    if (!on) return
    if (!allowed) {
        Spacer(Modifier.height(6.dp))
        Text(t("برای این کار اجازه «نمایش روی برنامه‌های دیگر» لازمه. تا وقتی ندی، کد مثل قبل فقط با نوتیف میاد."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
        Spacer(Modifier.height(6.dp))
        GlassButton(t("دادن اجازه"), primary = false, onClick = {
            ask.launch(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName)))
        })
    }
    Spacer(Modifier.height(8.dp))
    Text(t("باکس چند ثانیه بمونه؟"), style = MaterialTheme.typography.titleSmall, color = g.text)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 20, 30, 45, 60).forEach { sec ->
            GlassChip(fa(sec) + t(" ثانیه"), seconds == sec, { onSeconds(sec) })
        }
    }
}
