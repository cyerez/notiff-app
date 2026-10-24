package com.rez.notif.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.backup.BackupCrypto
import com.rez.notif.classify.Category
import com.rez.notif.classify.Records
import com.rez.notif.data.Repository
import com.rez.notif.i18n.t
import com.rez.notif.sms.DefaultSms
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TransferStep { EXPORT, IMPORT }

/** Take chosen messages from this phone to another one that also has Notif: one password-locked file. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransferScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf(setOf(Category.PERSONAL.id, Category.BANK.id, Category.MISSED.id, Category.SERVICES.id)) }
    var vip by remember { mutableStateOf(true) }
    var kept by remember { mutableStateOf(true) }
    var since by remember { mutableStateOf(0) } // 0 = all, else months back
    val choice = Repository.TransferChoice(cats, vip, kept, if (since == 0) null else System.currentTimeMillis() - since * 30L * 86_400_000L)
    val count by rememberLoaded(repo, choice.copy(sinceMillis = since.toLong())) { repo.transferCount(choice) }
    var pending by remember { mutableStateOf<Pair<TransferStep, Uri>?>(null) }
    var made by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) pending = TransferStep.EXPORT to uri
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) pending = TransferStep.IMPORT to uri
    }

    Column {
        GlassTopBar(title = t("انتقال به گوشی دیگه"), subtitle = t("پیام‌های مهم رو با خودت ببر"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), glow = g.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.PhoneAndroid, g.accent, size = 42.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            listOf(t("۱. روی گوشی قدیمی انتخاب کن چی ببری و فایل رو بساز."), t("۲. فایل رو با Quick Share، بلوتوث یا تلگرام به گوشی جدید بفرست."), t("۳. روی گوشی جدید نوتیف رو نصب کن و «خوندن فایل انتقال» رو بزن.")).joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = g.text,
                        )
                    }
                }
            }
            item { SectionTitle(t("چی ببرم؟"), Modifier.padding(start = 0.dp)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.filter { it != Category.ADS }.forEach { c ->
                        GlassChip(t(c.label), c.id in cats, { cats = if (c.id in cats) cats - c.id else cats + c.id }, color = c.tint)
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(t("همه پیام‌های افراد منتخب"), vip, { vip = !vip })
                    GlassChip(t("ستاره‌دار و ارزشمندها"), kept, { kept = !kept })
                }
                Spacer(Modifier.height(10.dp))
                Text(t("از کی؟"), style = MaterialTheme.typography.titleSmall, color = g.text)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to t("همه"), 12 to t("یک سال اخیر"), 3 to t("سه ماه اخیر")).forEach { (m, label) ->
                        GlassChip(label, since == m, { since = m })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    (count?.let { fa(it) + t(" پیام انتخاب شده") } ?: "…") + t(". تبلیغ‌ها هیچ‌وقت منتقل نمیشن."),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.textDim,
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(t("ساختن فایل انتقال"), icon = Icons.Rounded.Upload, enabled = !busy && (count ?: 0) > 0, modifier = Modifier.fillMaxWidth(), onClick = {
                    val name = "notif-transfer-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".notifmsg"
                    createFile.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, name),
                    )
                })
                made?.let { uri ->
                    Spacer(Modifier.height(8.dp))
                    GlassButton(t("فرستادن فایل"), icon = Icons.Rounded.Share, primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, null))
                    })
                }
            }
            item { SectionTitle(t("روی گوشی جدید"), Modifier.padding(start = 0.dp)) }
            item {
                Text(
                    t("پیام‌های فایل به پیام‌های این گوشی اضافه میشن و چیزی پاک نمیشه. پیامی که از قبل هست دوباره اضافه نمیشه."),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.textDim,
                )
                Spacer(Modifier.height(8.dp))
                GlassButton(t("خوندن فایل انتقال"), icon = Icons.Rounded.Download, primary = false, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    openFile.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
                })
                message?.let { Text(it, color = g.accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }

    pending?.let { (step, uri) ->
        var pass by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        val canInbox = DefaultSms.isDefault(context)
        var toInbox by remember { mutableStateOf(canInbox) }
        val ok = if (step == TransferStep.EXPORT) pass.length >= 4 && pass == again else pass.isNotEmpty()
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (step == TransferStep.EXPORT) t("رمز فایل انتقال") else t("خوندن فایل انتقال")) },
            text = {
                Column {
                    if (step == TransferStep.EXPORT) {
                        Text(t("همین رمز رو روی گوشی جدید لازم داری."), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        Spacer(Modifier.height(6.dp))
                    }
                    PassField(pass, { pass = it }, t("رمز"))
                    if (step == TransferStep.EXPORT) PassField(again, { again = it }, t("تکرار رمز"))
                    if (step == TransferStep.IMPORT) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Checkbox(checked = toInbox, onCheckedChange = { toInbox = it }, enabled = canInbox)
                            Text(t("پیامک‌ها به پیام‌های خود گوشی هم اضافه بشن"), style = MaterialTheme.typography.bodySmall, color = g.text)
                        }
                        if (!canInbox) {
                            Text(
                                t("برای این کار نوتیف باید اپ پیامک گوشی باشه (تنظیمات > اپ پیامک گوشی). بدون اون، پیام‌ها فقط توی نوتیف میان."),
                                style = MaterialTheme.typography.bodySmall,
                                color = g.textDim,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    pending = null
                    busy = true
                    message = t("صبر کن…")
                    val chars = pass.toCharArray()
                    scope.launch {
                        message = try {
                            if (step == TransferStep.EXPORT) {
                                val n = repo.exportTransfer(uri, chars, choice)
                                made = uri
                                fa(n) + t(" پیام توی فایل رفت. حالا «فرستادن فایل» رو بزن.")
                            } else {
                                val r = repo.importTransfer(uri, chars, toInbox && canInbox)
                                fa(r.added) + t(" پیام تازه اضافه شد") +
                                    (if (r.total > r.added) t(" (") + fa(r.total - r.added) + t(" تا از قبل بودن)") else "") +
                                    (if (r.inbox > 0) t("، و ") + fa(r.inbox) + t(" پیامک توی پیام‌های گوشی.") else ".")
                            }
                        } catch (e: BackupCrypto.WrongPassword) {
                            t("رمز اشتباهه (یا فایل خراب شده).")
                        } catch (e: Records.NotATransfer) {
                            t("این فایل انتقال نوتیف نیست.")
                        } catch (e: Exception) {
                            t("خطا: ") + (e.message ?: t("نامشخص"))
                        }
                        busy = false
                    }
                }) { Text(if (step == TransferStep.EXPORT) t("ذخیره") else t("خوندن")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(t("لغو")) } },
        )
    }
}

@Composable
private fun PassField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
