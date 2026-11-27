package com.rez.notif.ui.screens

import android.provider.Settings as SysSettings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Jokes
import com.rez.notif.classify.Ledger
import com.rez.notif.data.Repository
import com.rez.notif.i18n.I18n
import com.rez.notif.i18n.t
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.BankBadge
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.bankLabel
import com.rez.notif.ui.components.BankNameDialog
import com.rez.notif.ui.components.bankUnknown
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.components.toman
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.Lalezar
import com.rez.notif.ui.theme.LocalGlass
import com.rez.notif.R
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch

/** Rial -> "۴٫۲ میلیون" / "۸۵۰ هزار" (toman), for the small bank cards. */
fun shortToman(rial: Long): String {
    val tm = rial / 10
    val a = kotlin.math.abs(tm)
    val sign = if (tm < 0) "−" else ""
    fun one(x: Double) = fa(String.format(java.util.Locale.US, "%.1f", x).removeSuffix(".0")).let { if (I18n.isFa) it.replace('.', '٫') else it }
    return when {
        a >= 1_000_000_000 -> sign + one(a / 1e9) + t(" میلیارد")
        a >= 1_000_000 -> sign + one(a / 1e6) + t(" میلیون")
        a >= 1_000 -> sign + fa((a / 1_000).toString()) + t(" هزار")
        else -> sign + fa(a.toString())
    }
}


/** Animations stay small: off when the user turned them off, or the phone asks for less motion / saves battery. */
@Composable
fun calmMotion(): Boolean {
    val ctx = LocalContext.current
    val g = LocalGlass.current
    return remember(g.animations) {
        val scale = runCatching { SysSettings.Global.getFloat(ctx.contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        val saver = runCatching { (ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode }.getOrDefault(false)
        !g.animations || scale == 0f || saver
    }
}

/** The total turns like an odometer to its new value (under half a second). */
@Composable
internal fun RollingToman(rial: Long, color: Color) {
    val calm = calmMotion()
    val anim = remember { Animatable(0f) }
    var from by remember { androidx.compose.runtime.mutableLongStateOf(rial) }
    var to by remember { androidx.compose.runtime.mutableLongStateOf(rial) }
    LaunchedEffect(rial) {
        from = if (calm) rial else from + ((to - from) * anim.value).toLong()
        to = rial
        anim.snapTo(0f)
        if (!calm) anim.animateTo(1f, tween(450)) else anim.snapTo(1f)
    }
    val shown = from + ((to - from) * anim.value).toLong()
    Text(
        fa(String.format(java.util.Locale.US, "%,d", shown / 10)),
        fontSize = 44.sp, fontWeight = FontWeight.Black, color = color,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyHomeScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view by rememberLoaded(repo) { repo.home() }
    val settings by repo.prefs.state.collectAsState()

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val v = view
        val midnight = g.style == com.rez.notif.data.AppStyle.MIDNIGHT
        item { if (midnight) MidnightHeader(v, jokes = I18n.isFa) else Header(v, jokes = I18n.isFa) }
        if (v == null) return@LazyColumn

        // Bank cards
        if (v.balances.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(v.balances, key = { it.account }) { b -> if (midnight) MidnightBankCard(b) else BankCard(b) }
            }
        }
        if (midnight) item { MidnightActions(nav) }
        // Gold and dollar
        item { PricesRow() }

        // What were these?
        if (v.unsorted.isNotEmpty()) {
            item { SectionTitle(t("این‌ها چی بودن؟") + "  ·  " + fa(v.unsorted.size)) }
            v.unsorted.take(3).forEach { e ->
                item(key = "ask" + e.id) {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                        Text(
                            t("این ") + shortToman(e.out) + t(" چی بود؟"),
                            style = MaterialTheme.typography.titleSmall, color = g.text,
                        )
                        Text(bankLabel(e.account) + "  ·  " + shortTime(e.time), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            v.categories.take(4).forEach { c ->
                                GlassChip(t(c), false, {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { repo.setLedgerCategory(e, c) }
                                })
                            }
                            GlassChip(t("به خودم"), false, { scope.launch { repo.setLedgerCategory(e, Ledger.OWN) } })
                        }
                    }
                }
            }
            if (v.unsorted.size > 3) item {
                Box(Modifier.padding(horizontal = 16.dp)) { GlassChip(t("بقیه در تراکنش‌ها"), false, { nav.go(Screen.Ledger) }) }
            }
        }

        // Next 7 days
        if (v.next7.isNotEmpty()) {
            item { SectionTitle(t("۷ روز آینده")) }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp, onClick = { nav.go(Screen.Calendar()) }) {
                    v.next7.take(6).forEach { u -> UpcomingRow(u) }
                }
            }
        }

        // Today
        item { SectionTitle(t("امروز")) }
        if (v.today.isEmpty()) item {
            Text(
                if (I18n.isFa) Jokes.pick(Jokes.EMPTY_SPENDING, System.currentTimeMillis() / 86_400_000L) else t("هنوز تراکنشی نیست."),
                style = MaterialTheme.typography.bodyMedium, color = g.textDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else item {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp, onClick = { nav.go(Screen.Ledger) }) {
                v.today.take(8).forEach { e -> TxRow(e) }
            }
        }
    }
}

@Composable
private fun Header(v: Repository.HomeView?, jokes: Boolean) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.cow_logo), contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(t("شاخ گاو"), color = g.accent, fontFamily = Lalezar, fontSize = 28.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(t("موجودی همه‌ی حساب‌ها"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
        if (v == null) return@Column
        Row(verticalAlignment = Alignment.Bottom) {
            RollingToman(v.total, g.text)
            Spacer(Modifier.width(6.dp))
            Text(t("تومان"), style = MaterialTheme.typography.bodyMedium, color = g.textDim, modifier = Modifier.padding(bottom = 10.dp))
        }
        v.left?.let { l ->
            Text(
                if (l.perDay > 0) t("تا آخر ماه روزی ") + shortToman(l.perDay * 10) + t(" می‌تونی خرج کنی")
                else t("این ماه از برنامه‌ت بیشتر خرج شده"),
                style = MaterialTheme.typography.bodyMedium, color = if (l.perDay > 0) g.text else g.moneyOut,
            )
        }
        val mood = Jokes.mood(v.left?.perDay, v.left?.left)
        if (jokes && mood == Jokes.Mood.JOKE) {
            val day = System.currentTimeMillis() / 86_400_000L
            val joke = when {
                v.bigToday >= 10_000_000 -> Jokes.pick(Jokes.BIG_SPEND, day)
                Jokes.isLow(v.left?.perDay) -> Jokes.pick(Jokes.LOW, day)
                else -> Jokes.ofDay(day)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                joke,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF2D2236),
                modifier = Modifier.background(g.accent2, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun BankCard(b: Ledger.Balance) {
    val g = LocalGlass.current
    var naming by remember { mutableStateOf(false) }
    if (naming) BankNameDialog(b.account) { naming = false }
    val fresh = Ledger.isFresh(b, System.currentTimeMillis())
    GlassCard(Modifier.width(150.dp).alpha(if (fresh) 1f else 0.55f), contentPadding = 12.dp, onClick = { naming = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankBadge(b.account, 32.dp)
            Spacer(Modifier.width(8.dp))
            Text(bankLabel(b.account), style = MaterialTheme.typography.labelMedium, color = g.textDim, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(shortToman(b.rial), style = MaterialTheme.typography.titleMedium, color = g.text, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(shortTime(b.time), style = MaterialTheme.typography.labelSmall, color = g.textDim, maxLines = 1)
        if (!fresh) Text(t("قدیمی؛ تو جمع نیست"), style = MaterialTheme.typography.labelSmall, color = g.moneyOut, maxLines = 1)
        else if (bankUnknown(b.account)) Text(t("بزن، اسم بذار"), style = MaterialTheme.typography.labelSmall, color = g.accent, maxLines = 1)
    }
}

@Composable
private fun UpcomingRow(u: Repository.Upcoming) {
    val g = LocalGlass.current
    val incoming = u.kind == "cheque_in"
    val title = when (u.kind) {
        "cheque_in" -> t("چک از ") + u.title
        "cheque_out" -> t("چک به ") + u.title
        else -> u.title
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (incoming) g.moneyIn else g.accent2, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = g.text, maxLines = 1)
            Text(shortTime(u.due), style = MaterialTheme.typography.labelSmall, color = g.textDim)
        }
        if (u.toman > 0) Text(shortToman(u.toman * 10), style = MaterialTheme.typography.labelLarge, color = if (incoming) g.moneyIn else g.moneyOut)
    }
}

@Composable
private fun TxRow(e: Ledger.Entry) {
    val g = LocalGlass.current
    val color = when {
        e.isOwn -> g.moneyOwn
        e.inn > 0 && e.out == 0L -> g.moneyIn
        else -> g.moneyOut
    }
    val amount = if (e.inn > 0 && e.out == 0L) "+" + shortToman(e.inn) else "−" + shortToman(e.out)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        BankBadge(e.account, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (e.category) { null, "" -> t("بی‌دسته"); Ledger.OWN -> t("به خودم"); Ledger.IGNORE -> t("نادیده"); else -> t(e.category) },
                style = MaterialTheme.typography.bodyMedium, color = g.text, maxLines = 1,
            )
            Text(bankLabel(e.account) + "  ·  " + shortTime(e.time), style = MaterialTheme.typography.labelSmall, color = g.textDim)
        }
        Text(amount, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
