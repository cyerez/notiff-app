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
import com.rez.notif.ui.components.softShadow
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.fillMaxSize
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
    // Items fade in only when the screen opens, not every time they scroll back into view.
    val shownAt = remember { System.currentTimeMillis() }
    fun quiet() = calm || System.currentTimeMillis() - shownAt > 1500
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

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoneyHomeScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view by rememberLoaded(repo) { repo.home() }
    val settings by repo.prefs.state.collectAsState()
    val calm = calmMotion()
    // Items fade in only when the screen opens, not every time they scroll back into view.
    val shownAt = remember { System.currentTimeMillis() }
    fun quiet() = calm || System.currentTimeMillis() - shownAt > 1500
    // Playful motions (coin, chewing cow) are left out when money is very low.
    val playful = view?.let { Jokes.mood(it.total / 10, com.rez.notif.classify.Occasions.isMourning(java.time.LocalDate.now().toEpochDay())) == Jokes.Mood.JOKE } ?: true
    var flight by remember { mutableStateOf<CoinFlight?>(null) }
    val amountAt = remember { mutableMapOf<Long, androidx.compose.ui.geometry.Offset>() }
    val chipAt = remember { mutableMapOf<String, androidx.compose.ui.geometry.Offset>() }
    var refreshing by remember { mutableStateOf(false) }
    var pricesVersion by remember { mutableStateOf(0) }
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    /** Sorts a spending; with motion a coin flies into the chip first and the card glides out. */
    fun sort(e: Ledger.Entry, category: String, chipKey: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val from = amountAt[e.id]
        val to = chipAt[chipKey]
        if (calm || !playful || from == null || to == null) {
            scope.launch { repo.setLedgerCategory(e, category) }
        } else {
            flight = CoinFlight(from, to, System.nanoTime())
            scope.launch {
                kotlinx.coroutines.delay(430)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                repo.setLedgerCategory(e, category)
            }
        }
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                val started = System.currentTimeMillis()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { repo.syncSms() }
                    runCatching { com.rez.notif.data.PriceStore.refreshIfOld(context, force = true) }
                }
                pricesVersion++
                // Long enough to see the cow chew.
                kotlinx.coroutines.delay((900 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                refreshing = false
            }
        },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            CowRefreshIndicator(pullState, refreshing, calm, playful, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        },
    ) {
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val v = view
        val midnight = g.style == com.rez.notif.data.AppStyle.MIDNIGHT
        var n = 0
        val i0 = n++
        item { Box(Modifier.appear(i0, quiet())) { if (midnight) MidnightHeader(v, jokes = I18n.isFa) else Header(v, jokes = I18n.isFa) } }
        if (v == null) return@LazyColumn

        // Bank cards
        val i1 = n++
        if (v.balances.isNotEmpty()) item {
            LazyRow(Modifier.appear(i1, calm), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(v.balances, key = { it.account }) { b -> if (midnight) MidnightBankCard(b) else BankCard(b) }
            }
        }
        val i2 = n++
        if (midnight) item { Box(Modifier.appear(i2, quiet())) { MidnightActions(nav) } }
        // Gold and dollar
        val i3 = n++
        item { Box(Modifier.appear(i3, quiet())) { PricesRow(pricesVersion) } }

        // What were these?
        if (v.unsorted.isNotEmpty()) {
            val i4 = n++
            item { Box(Modifier.appear(i4, quiet())) { SectionTitle(t("این‌ها چی بودن؟") + "  ·  " + fa(v.unsorted.size)) } }
            v.unsorted.take(3).forEach { e ->
                val ie = n++
                item(key = "ask" + e.id) {
                    GlassCard(Modifier.animateItem().appear(ie, quiet()).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp) {
                        Text(
                            t("این ") + shortToman(e.out) + t(" چی بود؟"),
                            style = MaterialTheme.typography.titleSmall, color = g.text,
                            modifier = Modifier.onGloballyPositioned { amountAt[e.id] = it.boundsInWindow().center },
                        )
                        Text(bankLabel(e.account) + "  ·  " + shortTime(e.time), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            v.categories.take(4).forEach { c ->
                                val k = "${e.id}|$c"
                                Box(Modifier.onGloballyPositioned { chipAt[k] = it.boundsInWindow().center }) {
                                    GlassChip(t(c), false, { sort(e, c, k) })
                                }
                            }
                            val k = "${e.id}|own"
                            Box(Modifier.onGloballyPositioned { chipAt[k] = it.boundsInWindow().center }) {
                                GlassChip(t("به خودم"), false, { sort(e, Ledger.OWN, k) })
                            }
                        }
                    }
                }
            }
            if (v.unsorted.size > 3) item(key = "more") {
                Box(Modifier.animateItem().padding(horizontal = 16.dp)) { GlassChip(t("بقیه در تراکنش‌ها"), false, { nav.go(Screen.Ledger) }) }
            }
        }

        // Next 7 days
        if (v.next7.isNotEmpty()) {
            val i5 = n++
            item(key = "next7t") { Box(Modifier.animateItem().appear(i5, quiet())) { SectionTitle(t("۷ روز آینده")) } }
            item(key = "next7") {
                GlassCard(Modifier.animateItem().appear(i5 + 1, quiet()).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp, onClick = { nav.go(Screen.Calendar()) }) {
                    v.next7.take(6).forEach { u -> UpcomingRow(u) }
                }
            }
        }

        // Today
        val i6 = n + 1
        item(key = "todayt") { Box(Modifier.animateItem().appear(i6, quiet())) { SectionTitle(t("امروز")) } }
        if (v.today.isEmpty()) item(key = "today0") {
            EmptyCow(
                if (I18n.isFa && playful) Jokes.pick(Jokes.EMPTY_SPENDING, System.currentTimeMillis() / 86_400_000L) else t("هنوز تراکنشی نیست."),
                calm || !playful,
                Modifier.animateItem().appear(i6 + 1, quiet()).padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else item(key = "today") {
            GlassCard(Modifier.animateItem().appear(i6 + 1, quiet()).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 12.dp, onClick = { nav.go(Screen.Ledger) }) {
                v.today.take(8).forEach { e -> TxRow(e) }
            }
        }
    }
    CoinFlightLayer(flight) { flight = null }
    }
}

@Composable
private fun Header(v: Repository.HomeView?, jokes: Boolean) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.cow_cut), contentDescription = null,
                modifier = Modifier.size(44.dp),
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
        JokePill(v, jokes, onBlue = false)
    }
}

/**
 * Today's occasion (if any) and the line under the total: the occasion's quip, or a joke that fits the month.
 * Big and clear on a saffron (or dark glass on blue) pill. Nothing at serious moments.
 */
@Composable
internal fun JokePill(v: Repository.HomeView, jokes: Boolean, onBlue: Boolean) {
    val g = LocalGlass.current
    val today = remember { java.time.LocalDate.now().toEpochDay() }
    val occasions = remember(today) { com.rez.notif.classify.Occasions.on(today) }
    val mourning = occasions.any { it.mourning }
    if (occasions.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "🗓 " + occasions.joinToString("  ·  ") { if (I18n.isFa) it.name else it.en },
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = if (onBlue) Color.White else if (occasions.any { it.holiday }) g.moneyOut else g.accent,
        )
    }
    if (!jokes) return
    val line = Jokes.forHome(
        totalToman = v.total / 10, perDayToman = v.left?.perDay, leftToman = v.left?.left, bigTodayRial = v.bigToday,
        epochDay = today, occasionQuip = com.rez.notif.classify.Occasions.quip(today), mourning = mourning,
    ) ?: return
    JokeFlip(calmMotion()) {
        Spacer(Modifier.height(10.dp))
        Text(
            line,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = if (onBlue) Color.White else Color(0xFF2D2236),
            modifier = Modifier
                .softShadow(RoundedCornerShape(50), (if (onBlue) Color.Black else g.accent2).copy(alpha = 0.35f), blur = 10.dp, offsetY = 3.dp)
                .background(if (onBlue) Color(0xFF0B1A4A).copy(alpha = 0.6f) else g.accent2, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
