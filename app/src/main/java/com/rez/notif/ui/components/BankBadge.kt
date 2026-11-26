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
import com.rez.notif.classify.Banks
import com.rez.notif.i18n.I18n
import com.rez.notif.ui.theme.LocalGlass

/** "B.Pasargad 1234" -> "پاسارگاد ۱۲۳۴"; unknown senders stay as they are. */
fun bankLabel(account: String): String {
    val (sender, last4) = Banks.split(account)
    val bank = Banks.of(sender) ?: return fa(account)
    val name = if (I18n.isFa) bank.fa else bank.en
    return if (last4 != null) name + " " + fa(last4) else name
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
    val sender = Banks.split(account).first
    val bank = Banks.of(sender)
    val logo = bank?.let { logoOf(it.id) }
    if (logo != null) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.92f))
        }
    } else {
        val color = bank?.let { Color(it.color) } ?: LocalGlass.current.textDim
        val letter = (bank?.let { if (I18n.isFa) it.fa else it.en } ?: sender).trim().take(1).ifEmpty { "؟" }
        Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}
