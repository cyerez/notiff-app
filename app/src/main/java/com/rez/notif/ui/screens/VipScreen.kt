package com.rez.notif.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.ItemQuery
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.ItemList
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.VipColor
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/**
 * Chosen people: every SMS, notification and missed call from them is kept here forever,
 * is never hidden as an ad, never auto-cleaned, and stays in the status bar.
 */
@Composable
fun VipScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val people by rememberLoaded(repo) { repo.vipSummaries() }
    var manual by remember { mutableStateOf(false) }

    // The system contact picker hands back only the chosen number, so no contacts permission is needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        val cols = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0) ?: ""
                val number = c.getString(1) ?: ""
                scope.launch { repo.addVip(name, listOf(number)) }
            }
        }
    }

    Column {
        GlassTopBar(title = "افراد منتخب", subtitle = fa(people?.size ?: 0) + " نفر", onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), glow = VipColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.Favorite, VipColor, size = 46.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "هر پیامک، نوتیف یا میس‌کالی از این افراد برای همیشه اینجا می‌مونه. هیچ‌وقت تبلیغ حساب نمیشه، خودکار پاک نمیشه و نوتیفش روی گوشی می‌مونه.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = g.text,
                        )
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("از مخاطبین", icon = Icons.Rounded.ContactPhone, onClick = {
                            picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        })
                        GlassButton("دستی", icon = Icons.Rounded.EditNote, primary = false, onClick = { manual = true })
                    }
                }
            }
            items(people.orEmpty(), key = { it.person.id }) { v ->
                GlassCard(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    onClick = { nav.go(Screen.VipPerson(v.person.id, v.person.name)) },
                    contentPadding = 12.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.Favorite, VipColor, size = 40.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.person.name, style = MaterialTheme.typography.titleMedium, color = g.text)
                            Text(
                                fa(v.count) + " مورد" + (if (v.last > 0) "  ·  آخرین: " + shortTime(v.last) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = g.textDim,
                            )
                        }
                        IconBubble(Icons.Rounded.Delete, { scope.launch { repo.removeVip(v.person.id) } })
                    }
                }
            }
        }
    }

    if (manual) {
        var name by remember { mutableStateOf("") }
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("افزودن دستی") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم (همونی که در تلگرام و … دیده میشه)") }, singleLine = true)
                    OutlinedTextField(
                        number,
                        { number = it },
                        label = { Text("شماره (اختیاری)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() || number.isNotBlank(), onClick = {
                    manual = false
                    scope.launch { repo.addVip(name, listOf(number)) }
                }) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("لغو") } },
        )
    }
}

/** All messages, notifications and missed calls of one chosen person. */
@Composable
fun VipPersonScreen(id: Long, name: String, nav: Navigator) {
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    var edit by remember { mutableStateOf(false) }
    val items by rememberLoaded(repo, id) { repo.items(ItemQuery(vipId = id, includeAds = true, limit = 2000)) }
    val list = items.orEmpty()
    val missed = list.count { it.category == Category.MISSED.id }
    Column {
        GlassTopBar(
            title = name,
            subtitle = fa(list.size) + " مورد" + (if (missed > 0) "  ·  " + fa(missed) + " میس‌کال" else ""),
            onBack = nav::back,
        ) {
            IconBubble(Icons.Rounded.Edit, { edit = !edit }, tint = if (edit) Category.MISSED.tint else null)
        }
        ItemList(list, empty = "هنوز چیزی از این شخص نیومده.", editMode = edit, onEditModeChange = { edit = it })
    }
}
