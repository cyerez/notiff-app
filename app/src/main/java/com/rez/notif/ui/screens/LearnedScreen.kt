package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.Repository
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/** What the app learned from "move all from this sender": see, change or forget each rule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnedScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val rules by rememberLoaded(repo) { repo.learned() }
    var editing by remember { mutableStateOf<Repository.Learned?>(null) }

    Column {
        GlassTopBar(title = t("یادگرفته‌ها"), subtitle = fa(rules?.size ?: 0) + t(" قانون"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), glow = g.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.School, g.accent, size = 46.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            t("هر بار که «همه پیام‌های این فرستنده» رو به یه دسته بردی، اپ یه قانون یاد گرفته. اینجا می‌تونی عوضش کنی یا فراموشش کنی."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = g.text,
                        )
                    }
                }
            }
            if (rules?.isEmpty() == true) {
                item { Text(t("هنوز چیزی یاد نگرفته."), color = g.textDim, modifier = Modifier.padding(16.dp)) }
            }
            items(rules.orEmpty(), key = { it.key }) { r ->
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = { editing = r }, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(if (r.isSms) Icons.Rounded.Sms else Icons.Rounded.Apps, r.category.tint, size = 36.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.who, style = MaterialTheme.typography.titleSmall, color = g.text, maxLines = 1)
                            Text((if (r.isSms) t("پیامک") else t("اپ")) + " ← " + t(r.category.label), style = MaterialTheme.typography.bodySmall, color = r.category.tint)
                        }
                        IconBubble(Icons.Rounded.Delete, { scope.launch { repo.forgetLearned(r.key) } })
                    }
                }
            }
        }
    }

    editing?.let { r ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(r.who) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        GlassChip(t(c.label), c == r.category, color = c.tint, icon = c.icon, onClick = {
                            editing = null
                            scope.launch { repo.changeLearned(r.key, c) }
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text(t("بستن")) } },
        )
    }
}
