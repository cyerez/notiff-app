package com.rez.notif.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.i18n.t
import com.rez.notif.ocr.Ocr
import com.rez.notif.ocr.OcrLang
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picture to text, offline. Opened from the share sheet (S Pen Smart select → Share → Notif) or from Home. */
@Composable
fun OcrScreen(startUri: String?, nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    var uri by remember { mutableStateOf(startUri) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lang by remember { mutableStateOf(OcrLang.BOTH) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri = it.toString() }
    }
    fun openPicker() {
        pick.launch(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE))
    }

    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        note = null
        bitmap = withContext(Dispatchers.IO) { runCatching { Ocr.load(context, Uri.parse(u)) }.getOrNull() }
        if (bitmap == null) note = t("این عکس باز نشد.")
    }
    LaunchedEffect(bitmap, lang) {
        val b = bitmap ?: return@LaunchedEffect
        busy = true
        text = withContext(Dispatchers.Default) {
            runCatching { Ocr.read(context, b, lang) }.getOrElse { t("خطا: ") + (it.message ?: t("نامشخص")) }
        }
        busy = false
        if (text.isBlank()) note = t("متنی پیدا نشد. یه تیکه بزرگ‌تر و واضح‌تر رو انتخاب کن.")
    }

    androidx.compose.foundation.layout.Column(Modifier.imePadding()) {
        GlassTopBar(title = t("متن از عکس"), subtitle = t("آفلاین، روی همین گوشی"), onBack = nav::back)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bitmap == null) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), glow = g.accent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeonIcon(Icons.Rounded.DocumentScanner, g.accent, size = 46.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                t("با قلم: دکمه قلم رو بزن، «Smart select» رو انتخاب کن، دور متن بکش، بعد «اشتراک» و «متن از عکس» نوتیف رو بزن."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = g.text,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassButton(t("انتخاب عکس"), icon = Icons.Rounded.Image, onClick = { openPicker() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            bitmap?.let { b ->
                item {
                    Image(
                        b.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(18.dp)),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OcrLang.entries.forEach { l -> GlassChip(t(l.label), lang == l, { lang = l }) }
                    }
                }
                item {
                    OutlinedTextField(
                        if (busy) t("دارم می‌خونم…") else text,
                        { if (!busy) text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        enabled = !busy,
                        // Each line picks its own direction, so English sentences keep their dot at the end.
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(t("کپی"), icon = Icons.Rounded.ContentCopy, onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("text", text))
                            note = t("کپی شد.")
                        })
                        GlassButton(t("اشتراک"), icon = Icons.Rounded.Share, primary = false, onClick = {
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
                            )
                        })
                        GlassButton(t("عکس دیگه"), icon = Icons.Rounded.Image, primary = false, onClick = { openPicker() })
                    }
                }
            }
            note?.let { n -> item { Text(n, color = g.textDim, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
