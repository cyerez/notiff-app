package com.rez.notif.ui.screens

import com.rez.notif.i18n.t
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.backup.BackupCrypto
import com.rez.notif.classify.Category
import com.rez.notif.security.WalletVault
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackupStep { EXPORT, IMPORT }

/** Save everything to one password-locked file (e.g. on Google Drive or a USB drive), or bring it back. */
@Composable
fun BackupScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<BackupStep, Uri>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) pending = BackupStep.EXPORT to uri
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) pending = BackupStep.IMPORT to uri
    }

    Column {
        GlassTopBar(title = t("پشتیبان"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), glow = g.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonIcon(Icons.Rounded.Lock, g.accent, size = 46.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            t("همه پیامک‌ها، نوتیف‌ها، افراد منتخب و یادگرفته‌ها توی یک فایل رمزدار ذخیره میشن. بدون رمز هیچ‌کس نمی‌تونه بازش کنه، پس رمز رو فراموش نکن."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = g.text,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                GlassButton(t("ساختن فایل پشتیبان"), icon = Icons.Rounded.CloudUpload, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    val name = "notif-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".notifbak"
                    createFile.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/octet-stream")
                            .putExtra(Intent.EXTRA_TITLE, name),
                    )
                })
                Spacer(Modifier.height(10.dp))
                GlassButton(t("بازگردانی از فایل"), icon = Icons.Rounded.CloudDownload, primary = false, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    openFile.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
                })
                message?.let { Text(it, color = g.accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }

    pending?.let { (step, uri) ->
        var pass by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        val ok = if (step == BackupStep.EXPORT) pass.length >= 4 && pass == again else pass.isNotEmpty()
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (step == BackupStep.EXPORT) t("رمز فایل پشتیبان") else t("بازگردانی")) },
            text = {
                Column {
                    if (step == BackupStep.IMPORT) {
                        Text(t("همه داده‌های فعلی اپ با این فایل جایگزین میشن."), color = Category.ADS.tint, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    PasswordField(pass, { pass = it }, t("رمز"))
                    if (step == BackupStep.EXPORT) PasswordField(again, { again = it }, t("تکرار رمز"))
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    pending = null
                    busy = true
                    message = t("صبر کن…")
                    val chars = pass.toCharArray()
                    fun run(askedWallet: Boolean) {
                        scope.launch {
                            message = try {
                                if (step == BackupStep.EXPORT) {
                                    repo.exportBackup(uri, chars); t("فایل پشتیبان ساخته شد.")
                                } else {
                                    repo.importBackup(uri, chars); t("بازگردانی انجام شد.")
                                }
                            } catch (e: WalletVault.Locked) {
                                // The password wallet is inside the backup, so it needs its own fingerprint.
                                val activity = context as? Activity
                                if (!askedWallet && activity != null) {
                                    WalletVault.unlock(activity) { ok -> if (ok) run(true) else { message = t("کیف پسورد قفله"); busy = false } }
                                    return@launch
                                }
                                t("کیف پسورد قفله")
                            } catch (e: BackupCrypto.WrongPassword) {
                                t("رمز اشتباهه (یا فایل خراب شده).")
                            } catch (e: BackupCrypto.NotABackup) {
                                t("این فایل پشتیبان نوتیف نیست.")
                            } catch (e: Exception) {
                                t("خطا: ") + (e.message ?: t("نامشخص"))
                            }
                            busy = false
                        }
                    }
                    run(false)
                }) { Text(if (step == BackupStep.EXPORT) t("ذخیره") else t("بازگردانی")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(t("لغو")) } },
        )
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}
