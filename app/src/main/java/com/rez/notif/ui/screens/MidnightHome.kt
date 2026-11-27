package com.rez.notif.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.R
import com.rez.notif.classify.Banks
import com.rez.notif.classify.Jokes
import com.rez.notif.classify.Ledger
import com.rez.notif.data.BankNames
import com.rez.notif.data.Repository
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.BankBadge
import com.rez.notif.ui.components.BankNameDialog
import com.rez.notif.ui.components.softShadow
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.theme.Lalezar
import com.rez.notif.ui.theme.LocalGlass

/*
 * The home of the "آبی شب (آزمایشی)" test style: a blue balance card at the top, bank cards that look like
 * bank cards, and round quick actions. The rest of the home is shared with the other styles.
 */

private val Blue = Color(0xFF2F6BFF)
private val BlueDeep = Color(0xFF1A3FB8)

@Composable
fun MidnightHeader(v: Repository.HomeView?, jokes: Boolean) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            Image(painterResource(R.drawable.cow_cut), null, Modifier.size(38.dp))
            Spacer(Modifier.width(10.dp))
            Text(t("شاخ گاو"), color = g.text, fontFamily = Lalezar, fontSize = 22.sp)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth()
                .softShadow(RoundedCornerShape(30.dp), Blue.copy(alpha = 0.6f), blur = 30.dp, offsetY = 12.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4C8DFF), Blue, BlueDeep)))
                // glossy light on the upper half, like the reference
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.55f to Color.Transparent))
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))), RoundedCornerShape(30.dp))
                .padding(vertical = 22.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t("موجودی همه‌ی حساب‌ها"), style = MaterialTheme.typography.labelMedium, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (v == null) { Spacer(Modifier.height(60.dp)); return@Column }
            Row(verticalAlignment = Alignment.Bottom) {
                RollingToman(v.total, Color.White)
                Spacer(Modifier.width(6.dp))
                Text(t("تومان"), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 10.dp))
            }
            v.left?.let { l ->
                Text(
                    if (l.perDay > 0) t("تا آخر ماه روزی ") + shortToman(l.perDay * 10) + t(" می‌تونی خرج کنی")
                    else t("این ماه از برنامه‌ت بیشتر خرج شده"),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f),
                )
            }
            JokePill(v, jokes, onBlue = true)
        }
    }
}

/** A bank card: the bank's color as a glossy gradient, its logo, the balance and the last 4 digits. */
@Composable
fun MidnightBankCard(b: Ledger.Balance) {
    var naming by remember { mutableStateOf(false) }
    if (naming) BankNameDialog(b.account) { naming = false }
    val shown = Banks.shown(b.account, BankNames.map)
    val base = shown.bank?.let { Color(it.color) } ?: Color(0xFF3A4A7A)
    val fresh = Ledger.isFresh(b, System.currentTimeMillis())
    Column(
        Modifier.padding(vertical = 10.dp).width(210.dp).height(128.dp).alpha(if (fresh) 1f else 0.5f)
            .softShadow(RoundedCornerShape(22.dp), Color(0xFF02061A).copy(alpha = 0.7f), blur = 18.dp, offsetY = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.95f), lerpTo(base, Color(0xFF0B1230), 0.55f))))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.Transparent)))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.2f))), RoundedCornerShape(22.dp))
            .clickable { naming = true }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankBadge(b.account, 30.dp)
            Spacer(Modifier.weight(1f))
            Text(shortTime(b.time), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        }
        Spacer(Modifier.weight(1f))
        Text(shortToman(b.rial), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            if (!fresh) t("قدیمی؛ تو جمع نیست") else shown.name + (shown.last4?.let { "   •••• " + fa(it) } ?: ""),
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1,
        )
    }
}

private fun lerpTo(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f, green = a.green + (b.green - a.green) * f, blue = a.blue + (b.blue - a.blue) * f, alpha = 1f,
)

/** Round quick buttons, like a bank app's "card control". */
@Composable
fun MidnightActions(nav: Navigator) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickAction(Icons.Rounded.CalendarMonth, t("تقویم")) { nav.go(Screen.Calendar()) }
        QuickAction(Icons.Rounded.AccountBalance, t("قسط و قبض")) { nav.go(Screen.Money) }
        QuickAction(Icons.Rounded.Groups, t("دنگ")) { nav.go(Screen.Dong) }
        QuickAction(Icons.Rounded.DocumentScanner, t("متن از عکس")) { nav.go(Screen.Ocr()) }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Box(
            Modifier.size(54.dp)
                .softShadow(CircleShape, Blue.copy(alpha = 0.55f), blur = 16.dp, offsetY = 6.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF4C8DFF), BlueDeep)))
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.5f to Color.Transparent))
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = g.text, maxLines = 1)
    }
}
