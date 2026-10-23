package com.rez.notif.ui.screens

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.Accent
import com.rez.notif.data.ThemeMode
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
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.theme.LocalGlass
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
        GlassTopBar(title = "تنظیمات", onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp)) {
            item { SectionTitle("ظاهر") }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text("حالت", style = MaterialTheme.typography.titleSmall, color = g.text)
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("خودکار", s.themeMode == ThemeMode.SYSTEM, { repo.prefs.update { it.copy(themeMode = ThemeMode.SYSTEM) } })
                        GlassChip("روشن", s.themeMode == ThemeMode.LIGHT, { repo.prefs.update { it.copy(themeMode = ThemeMode.LIGHT) } })
                        GlassChip("تیره", s.themeMode == ThemeMode.DARK, { repo.prefs.update { it.copy(themeMode = ThemeMode.DARK) } })
                    }
                    Text("رنگ نئون", style = MaterialTheme.typography.titleSmall, color = g.text)
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accent.entries.forEach { a -> AccentDot(a, a == s.accent) { repo.prefs.update { it.copy(accent = a) } } }
                    }
                    Text(s.accent.label, style = MaterialTheme.typography.bodySmall, color = g.textDim)
                    ToggleRow("انیمیشن‌ها", "پس‌زمینه متحرک و حرکت‌های کوچک", s.animations) { v ->
                        repo.prefs.update { it.copy(animations = v) }
                    }
                }
            }

            item { SectionTitle("دسته‌ها و تبلیغ") }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    ToggleRow("مخفی کردن تبلیغ‌ها", "نوتیف تبلیغی (و نوتیف پیامک تبلیغی) فوراً پاک میشه و فقط در «تبلیغات مخفی‌شده» می‌مونه.", s.hideAds) { v ->
                        repo.prefs.update { it.copy(hideAds = v) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("دسته‌های اصلی صفحه خانه", style = MaterialTheme.typography.titleSmall, color = g.text)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.filter { it != Category.ADS }.forEach { c ->
                            val on = c.id in s.mainCategories
                            GlassChip(c.label, on, color = c.tint, icon = c.icon, onClick = {
                                repo.prefs.update { st ->
                                    st.copy(mainCategories = if (on) st.mainCategories - c.id else st.mainCategories + c.id)
                                }
                            })
                        }
                    }
                }
            }

            item { SectionTitle("داده‌ها") }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text(
                        "همه چیز فقط روی همین گوشی ذخیره میشه. این اپ اصلاً اجازه اینترنت نداره.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = g.textDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("همگام‌سازی پیامک‌ها", onClick = {
                            scope.launch {
                                val n = runCatching { repo.syncSms() }.getOrNull()
                                syncMsg = if (n == null) "خطا در خوندن پیامک‌ها" else fa(n) + " پیامک جدید اضافه شد"
                            }
                        }, primary = false)
                        GlassButton("اسکن و دسته‌بندی دوباره", onClick = {
                            repo.prefs.update { it.copy(firstScanDone = false) }
                            nav.reset(Screen.Scan)
                        }, primary = false)
                        GlassButton("دسترسی‌ها", onClick = { nav.go(Screen.Onboarding) }, primary = false)
                        GlassButton("پاک کردن همه", onClick = { confirmWipe = true }, primary = false)
                    }
                    syncMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = g.accent, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            item {
                Text(
                    "نوتیف · نسخه ۰.۱ (آزمایشی)",
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
            title = { Text("پاک کردن همه داده‌ها؟") },
            text = { Text("همه پیامک‌ها و نوتیف‌های ذخیره‌شده در این اپ پاک میشن. پیامک‌های خود گوشی دست نمی‌خورن.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch { repo.deleteAll() }
                }) { Text("پاک کن", color = Category.ADS.tint) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("نه") } },
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
            .shadow(if (selected) 14.dp else 4.dp, CircleShape, ambientColor = c, spotColor = c)
            .background(Brush.linearGradient(listOf(c, Color(a.second))), CircleShape)
            .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.9f else 0.4f), CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
