package com.rez.notif.ui.screens

import com.rez.notif.ui.components.styledShape
import com.rez.notif.ui.components.softShadow
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Savings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.ItemQuery
import com.rez.notif.i18n.t
import com.rez.notif.notif.CodeNotifier
import com.rez.notif.sms.ContactNames
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.ItemList
import com.rez.notif.ui.components.ItemRow
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.ValuableColor
import com.rez.notif.ui.components.VipColor
import com.rez.notif.ui.components.codeOf
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.glassSurface
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.pressable
import com.rez.notif.ui.components.senderLabel
import com.rez.notif.ui.components.shortTime
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.today
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/** The four tabs at the bottom. Home is what needs you; the other three hold the categories. */
enum class HomeTab(val label: String, val icon: ImageVector, val categories: List<Category>) {
    HOME("خانه", Icons.Rounded.Home, emptyList()),
    /** Every message and notification (ads have their own list). */
    MESSAGES("پیام‌ها", Icons.Rounded.Forum, Category.entries.filter { it != Category.ADS }),
    /** The ledger: the main screen when the notification module is off. */
    LEDGER("حساب", Icons.Rounded.AccountBalance, emptyList()),
    /** All the tools: money, budget, dong, wallet, stats, OCR... */
    APPS("برنامه‌ها", Icons.Rounded.Apps, emptyList()),
    SETTINGS("تنظیمات", Icons.Rounded.Settings, emptyList()),
}

/** Stays on the same tab when coming back from another screen. */
private val currentTab = mutableStateOf(HomeTab.LEDGER)

/** Tabs shown: without the notification module, the app is the ledger, the tools and settings. */
fun visibleTabs(notifModule: Boolean): List<HomeTab> =
    if (notifModule) HomeTab.entries.toList() else listOf(HomeTab.LEDGER, HomeTab.APPS, HomeTab.SETTINGS)

@Composable
fun HomeScreen(nav: Navigator) {
    var tab by currentTab
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val counts by rememberLoaded(repo) { repo.categoryCounts().associateBy { it.category } }
    val settings by repo.prefs.state.collectAsState()
    val tabs = visibleTabs(settings.notifModule)
    val first = tabs.first()
    if (tab !in tabs) tab = first

    // Back from another tab goes to the first tab.
    BackHandler(enabled = tab != first) { tab = first }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            HomeTab.HOME -> HomeTabContent(nav)
            HomeTab.MESSAGES -> CategoryTab(tab, nav)
            HomeTab.LEDGER -> LedgerScreen(nav, embedded = true)
            HomeTab.APPS -> AppsTab(nav)
            HomeTab.SETTINGS -> SettingsScreen(nav, embedded = true)
        }
        BottomTabs(
            tabs = tabs,
            current = tab,
            unread = { t -> t.categories.sumOf { c -> counts?.get(c.id)?.unread ?: 0 } },
            onPick = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopBar(title: String, subtitle: String?, nav: Navigator, extra: @Composable () -> Unit = {}) {
    GlassTopBar(title = title, subtitle = subtitle) {
        extra()
        IconBubble(Icons.Rounded.Search, { nav.go(Screen.Search) })
    }
}

// ---------- Home: what needs you ----------

@Composable
private fun HomeTabContent(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val settings by repo.prefs.state.collectAsState()
    val feed by rememberLoaded(repo) { repo.homeFeed() }
    val scope = rememberCoroutineScope()
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repo.prefs.update { it.copy(contactsAsked = true) }
        kotlin.concurrent.thread { ContactNames.refresh(context); repo.bump() }
    }

    Column {
        TopBar("NotificatioN", today(), nav) {
            IconBubble(Icons.Rounded.Savings, { nav.go(Screen.Money) }, tint = Category.BANK.tint)
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.Key, { nav.go(Screen.Wallet) }, tint = ValuableColor)
            Spacer(Modifier.width(8.dp))
        }
        // Home shows only what needs you; the rest of today is one tap away.
        ItemList(items = feed?.needsYou.orEmpty(), empty = t("چیز مهمی منتظرت نیست.")) { openItem ->
            if (settings.firstScanDone && !settings.contactsAsked && !ContactNames.allowed(context)) {
                item {
                    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), glow = VipColor) {
                        Text(t("اسم مخاطب‌ها به جای شماره"), style = MaterialTheme.typography.titleSmall, color = g.text)
                        Text(
                            t("با اجازه دسترسی به مخاطبین، همه جا اسم آدم‌ها نشون داده میشه و چند شماره یه نفر یکی حساب میشن. مخاطبین از گوشی بیرون نمیرن."),
                            style = MaterialTheme.typography.bodySmall,
                            color = g.textDim,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(t("اجازه بده"), onClick = { askContacts.launch(arrayOf(android.Manifest.permission.READ_CONTACTS)) })
                            GlassButton(t("نه"), primary = false, onClick = { repo.prefs.update { it.copy(contactsAsked = true) } })
                        }
                    }
                }
            }
            item(key = "stats") { StatsStrip(nav) }
            feed?.code?.let { c -> item(key = "code") { CodeCard(c.title, codeOf(c), c.timestamp) } }
            item(key = "needs-title") {
                SectionTitle(t("منتظر تو")) {
                    val rest = feed?.recent?.size ?: 0
                    if (rest > 0) GlassChip(t("بقیه امروز") + " " + fa(rest), false, { nav.go(Screen.Special(SpecialList.TODAY)) })
                    val all = feed?.needsYou.orEmpty() + feed?.recent.orEmpty()
                    if (all.any { !it.isRead }) {
                        Spacer(Modifier.width(6.dp))
                        // Everything that came in is marked read and taken off the status bar (it stays in the app).
                        GlassChip(t("همه برن"), false, {
                            scope.launch {
                                repo.markRead(all)
                                com.rez.notif.notif.NotifListener.instance?.clearNonEssential()
                                android.widget.Toast.makeText(context, t("همه خونده شد و از نوار رفت"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }, icon = Icons.Rounded.DoneAll)
                    }
                }
            }
        }
    }
}

/** Today / unread / tidied this week: three small tiles; each opens its list, the last opens the stats. */
@Composable
private fun StatsStrip(nav: Navigator) {
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val today by rememberLoaded(repo) { repo.count(ItemQuery(sinceMillis = repo.dayStart())) }
    val unread by rememberLoaded(repo) { repo.count(ItemQuery(onlyUnread = true)) }
    val week by rememberLoaded(repo) { repo.weekly() }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(t("امروز"), today, LocalGlass.current.accent, Modifier.weight(1f)) { nav.go(Screen.Special(SpecialList.TODAY)) }
        StatTile(t("خونده‌نشده"), unread, Category.PERSONAL.tint, Modifier.weight(1f)) { nav.go(Screen.Special(SpecialList.UNREAD)) }
        StatTile(t("جمع‌شده این هفته"), week?.hidden, Category.SOCIAL.tint, Modifier.weight(1f)) { nav.go(Screen.Stats) }
    }
}

@Composable
private fun StatTile(label: String, value: Int?, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, onClick = onClick, contentPadding = 10.dp) {
        Text(if (value == null) "…" else fa(value), color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = g.textDim, maxLines = 1)
    }
}

/** A fresh login code, big, with one tap to copy. */
@Composable
private fun CodeCard(from: String, code: String?, time: Long) {
    val g = LocalGlass.current
    val context = LocalContext.current
    code ?: return
    GlassCard(Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth(), glow = Category.OTP.tint, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("کد تازه") + " · " + senderLabel(from) + " · " + shortTime(time), style = MaterialTheme.typography.labelSmall, color = g.textDim, maxLines = 1)
                Text(fa(code), color = g.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            GlassButton(t("کپی"), icon = Icons.Rounded.ContentCopy, onClick = { CodeNotifier.copy(context, code) })
        }
    }
}

// ---------- the three category tabs ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryTab(tab: HomeTab, nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val scope = rememberCoroutineScope()
    var pick by remember(tab) { mutableStateOf<Category?>(null) }
    val counts by rememberLoaded(repo) { repo.categoryCounts().associateBy { it.category } }
    val items by rememberLoaded(repo, tab, pick) {
        val cats = pick?.let { setOf(it.id) } ?: tab.categories.map { it.id }.toSet()
        repo.items(ItemQuery(categories = cats, limit = 400))
    }
    val adCount = counts?.get(Category.ADS.id)?.total ?: 0

    Column {
        TopBar(t(tab.label), null, nav) {
            IconBubble(Icons.Rounded.DoneAll, { scope.launch { repo.markRead(items.orEmpty()) } })
            Spacer(Modifier.width(8.dp))
        }
        ItemList(items = items.orEmpty(), empty = t("اینجا چیزی نیست.")) {
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassChip(t("همه"), pick == null, { pick = null })
                    tab.categories.forEach { c ->
                        val unread = counts?.get(c.id)?.unread ?: 0
                        GlassChip(
                            t(c.label) + if (unread > 0) " " + fa(unread) else "",
                            pick == c,
                            { pick = if (pick == c) null else c },
                            color = c.tint,
                            icon = c.icon,
                        )
                    }
                }
            }
        }
    }
}

// ---------- all tools ----------

private data class Tool(val label: String, val hint: String, val icon: ImageVector, val color: androidx.compose.ui.graphics.Color?, val go: Screen)

@Composable
private fun AppsTab(nav: Navigator) {
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val counts by rememberLoaded(repo) { repo.categoryCounts().associateBy { it.category } }
    val adCount = counts?.get(Category.ADS.id)?.total ?: 0
    val tools = listOf(
        Tool("دفتر حساب", "همه خرج‌ها با دسته", Icons.Rounded.AccountBalance, Category.BANK.tint, Screen.Ledger),
        Tool("طلب، بدهی، چک و قلک", "تعهدها و پس‌انداز", Icons.Rounded.Savings, VipColor, Screen.Commitments),
        Tool("قسط و قبض", "خلاصه بانک، قسط و قبض", Icons.Rounded.AccountBalance, Category.BANK.tint, Screen.Money),
        Tool("دخل و خرج", "درآمد و خرج ماه", Icons.Rounded.AccountBalanceWallet, Category.PERSONAL.tint, Screen.Budget),
        Tool("حساب دنگ", "سفر و دورهمی", Icons.Rounded.Groups, Category.SOCIAL.tint, Screen.Dong),
        Tool("کیف پسورد", "رمزها با اثر انگشت", Icons.Rounded.Key, ValuableColor, Screen.Wallet),
        Tool("متن از عکس", "بدون اینترنت", Icons.Rounded.DocumentScanner, Category.OTP.tint, Screen.Ocr()),
        Tool("آمار", "گزارش هفتگی و نمودارها", Icons.Rounded.BarChart, Category.SERVICES.tint, Screen.Stats),
        Tool("قانون‌ها", "هر پیام چی بشه", Icons.Rounded.Tune, null, Screen.Rules),
        Tool("افراد منتخب", "هیچ‌وقت مخفی نمیشن", Icons.Rounded.Favorite, VipColor, Screen.Vip),
        Tool("تبلیغات مخفی‌شده", fa(adCount) + " " + "پیام", Icons.Rounded.Campaign, Category.ADS.tint, Screen.Special(SpecialList.ADS)),
        Tool("پاک‌سازی", "پاک کردن پیام‌های قدیمی", Icons.Rounded.CleaningServices, null, Screen.Cleanup),
        Tool("انتقال به گوشی دیگه", "پیام‌های مهم با خودت", Icons.Rounded.PhoneAndroid, null, Screen.Transfer),
        Tool("پشتیبان رمزدار", "ذخیره و بازگردانی", Icons.Rounded.Lock, null, Screen.Backup),
    ).filter { tool ->
        // Without the notification module, only money tools (and wallet, OCR, backup) are shown.
        val notifOnly = tool.go is Screen.Special || tool.go in setOf(Screen.Rules, Screen.Vip, Screen.Cleanup, Screen.Transfer, Screen.Stats)
        val module = repo.prefs.current.notifModule
        (module || !notifOnly) && tool.go != Screen.Ledger
    }
    val g = LocalGlass.current
    Column {
        TopBar(t("برنامه‌ها"), null, nav)
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tools.size) { i ->
                val tool = tools[i]
                GlassCard(Modifier.fillMaxWidth().height(112.dp), onClick = { nav.go(tool.go) }, contentPadding = 12.dp) {
                    NeonIcon(tool.icon, tool.color ?: g.accent, size = 38.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(t(tool.label), style = MaterialTheme.typography.titleSmall, color = g.text, maxLines = 1)
                    Text(if (tool.go is Screen.Special) tool.hint.replace("پیام", t("پیام")) else t(tool.hint), style = MaterialTheme.typography.labelSmall, color = g.textDim, maxLines = 1)
                }
            }
        }
    }
}

// ---------- bottom bar ----------

@Composable
private fun BottomTabs(tabs: List<HomeTab>, current: HomeTab, unread: (HomeTab) -> Int, onPick: (HomeTab) -> Unit, modifier: Modifier) {
    val g = LocalGlass.current
    // Solid, not glass: list rows scroll behind it and must not show through.
    val barShape = styledShape(RoundedCornerShape(28.dp))
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .softShadow(barShape, g.shadow.copy(alpha = 0.35f), 14.dp)
            .background(MaterialTheme.colorScheme.surface, barShape)
            .border(1.dp, g.edge.copy(alpha = if (g.isDark) 0.5f else 0.9f), barShape)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tb ->
            val on = tb == current
            val n = unread(tb)
            Column(
                Modifier.weight(1f).pressable({ onPick(tb) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    if (on) NeonIcon(tb.icon, g.accent, size = 34.dp)
                    else Icon(tb.icon, null, tint = g.text.copy(alpha = 0.8f), modifier = Modifier.padding(5.dp).size(24.dp))
                    if (n > 0 && !on) {
                        Text(
                            fa(if (n > 99) 99 else n),
                            color = g.accent,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
                Text(
                    t(tb.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) g.accent else g.text,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
