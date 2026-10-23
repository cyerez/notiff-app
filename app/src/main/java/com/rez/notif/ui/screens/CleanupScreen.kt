package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.CleanupRule
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/**
 * Smart cleanup: finds clutter (old missed calls, login codes, ads...) and removes it from the app.
 * Chosen people, starred and valuable items are never touched.
 */
@Composable
fun CleanupScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val s by repo.prefs.state.collectAsState()
    val scope = rememberCoroutineScope()
    val preview by rememberLoaded(repo) { repo.cleanupPreview() }
    var result by remember { mutableStateOf<String?>(null) }
    val chosen = s.cleanupRules.mapNotNull { runCatching { CleanupRule.valueOf(it) }.getOrNull() }.toSet()
    val total = preview.orEmpty().filterKeys { it in chosen }.values.sum()

    Column {
        GlassTopBar(title = t("پاک‌سازی هوشمند"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), glow = g.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.CleaningServices, g.accent, size = 46.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            t("چیزهای قدیمی و بی‌مصرف رو پیدا می‌کنم. افراد منتخب، ستاره‌دارها و ارزشمندها هیچ‌وقت پاک نمیشن. پیامک‌های خود گوشی هم دست نمی‌خورن."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = g.text,
                        )
                    }
                }
            }
            items(CleanupRule.entries.toList(), key = { it.name }) { rule ->
                val on = rule in chosen
                val c = Category.of(rule.category)
                GlassCard(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    onClick = {
                        repo.prefs.update { st -> st.copy(cleanupRules = if (on) st.cleanupRules - rule.name else st.cleanupRules + rule.name) }
                    },
                    contentPadding = 12.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(c.icon, c.tint, size = 36.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t(rule.label), style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(fa(preview?.get(rule) ?: 0) + t(" مورد"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        }
                        Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.tint))
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                GlassButton(
                    text = if (total > 0) t("پاک کردن ") + fa(total) + t(" مورد") else t("چیزی برای پاک کردن نیست"),
                    enabled = total > 0,
                    onClick = {
                        scope.launch {
                            val n = repo.runCleanup(chosen)
                            result = fa(n) + t(" مورد پاک شد.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let { Text(it, color = g.accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("پاک‌سازی خودکار"), style = MaterialTheme.typography.titleSmall, color = g.text)
                            Text(t("روزی یک بار، موقع باز کردن اپ، با همین تیک‌ها"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        }
                        Switch(
                            checked = s.autoClean,
                            onCheckedChange = { v -> repo.prefs.update { it.copy(autoClean = v) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = g.accent, checkedThumbColor = Color.White),
                        )
                    }
                }
            }
        }
    }
}
