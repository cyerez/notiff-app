package com.rez.notif.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rez.notif.classify.Banks
import com.rez.notif.data.BankNames
import com.rez.notif.i18n.I18n
import com.rez.notif.i18n.t
import com.rez.notif.ui.theme.LocalGlass

/** "B.Pasargad 1234" -> "پاسارگاد ۱۲۳۴"; a name the user gave wins; unknown senders stay as they are. */
fun bankLabel(account: String): String {
    val s = Banks.shown(account, BankNames.map, I18n.isFa)
    return fa(if (s.last4 != null) s.name + " " + s.last4 else s.name)
}

/** True when the app could not tell the bank of this account (the user may name it). */
fun bankUnknown(account: String): Boolean = Banks.shown(account, BankNames.map).bank == null

/** Lets the user name the sender of an account; the bank (and logo) is found from the name while typing. */
@Composable
fun BankNameDialog(account: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sender = Banks.split(account).first
    var name by rememberSaveable { mutableStateOf(BankNames.map[sender] ?: "") }
    val found = Banks.of(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("اسم این حساب")) },
        text = {
            Column {
                Text(t("فرستنده: ") + fa(sender), style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textDim)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    name, { name = it.take(30) }, singleLine = true,
                    label = { Text(t("مثلاً «ملت» یا «سامان حقوق»")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankBadge(if (name.isBlank()) account else name, 32.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        when {
                            name.isBlank() -> t("یه اسم بنویس")
                            found != null -> t("بانک ") + (if (I18n.isFa) found.fa else found.en)
                            else -> t("بانکش رو نشناختم؛ فقط اسم نشون داده میشه")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { BankNames.set(context, sender, name); onDismiss() }) { Text(t("ذخیره")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("بی‌خیال")) } },
    )
}

private fun logoOf(id: String): Int? = when (id) {
    "ansar" -> R.drawable.bank_ansar
    "ayandeh" -> R.drawable.bank_ayandeh
    "dey" -> R.drawable.bank_dey
    "edbi" -> R.drawable.bank_edbi
    "ghavamin" -> R.drawable.bank_ghavamin
    "hekmat" -> R.drawable.bank_hekmat
    "iranzamin" -> R.drawable.bank_iranzamin
    "ivbb" -> R.drawable.bank_ivbb
    "karafarin" -> R.drawable.bank_karafarin
    "keshavarzi" -> R.drawable.bank_keshavarzi
    "maskan" -> R.drawable.bank_maskan
    "mehriran" -> R.drawable.bank_mehriran
    "melal" -> R.drawable.bank_melal
    "mellat" -> R.drawable.bank_mellat
    "melli" -> R.drawable.bank_melli
    "middleeast" -> R.drawable.bank_middleeast
    "novin" -> R.drawable.bank_novin
    "parsian" -> R.drawable.bank_parsian
    "pasargad" -> R.drawable.bank_pasargad
    "post" -> R.drawable.bank_post
    "refah" -> R.drawable.bank_refah
    "resalat" -> R.drawable.bank_resalat
    "saderat" -> R.drawable.bank_saderat
    "saman" -> R.drawable.bank_saman
    "sanat" -> R.drawable.bank_sanat
    "sarmayeh" -> R.drawable.bank_sarmayeh
    "sepah" -> R.drawable.bank_sepah
    "shahr" -> R.drawable.bank_shahr
    "sina" -> R.drawable.bank_sina
    "taavon" -> R.drawable.bank_taavon
    "tejarat" -> R.drawable.bank_tejarat
    "tourism" -> R.drawable.bank_tourism
    else -> null
}

/** The bank's logo on a small white tile; a colored circle with the first letter when there is no logo. */
@Composable
fun BankBadge(account: String, size: Dp = 36.dp) {
    val shown = Banks.shown(account, BankNames.map, I18n.isFa)
    val bank = shown.bank
    val logo = bank?.let { logoOf(it.id) }
    if (logo != null) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.92f))
        }
    } else {
        val color = bank?.let { Color(it.color) } ?: LocalGlass.current.textDim
        val letter = fa(shown.name.trim().take(1)).ifEmpty { "؟" }
        Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}
