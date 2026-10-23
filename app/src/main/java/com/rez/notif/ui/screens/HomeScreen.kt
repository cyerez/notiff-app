package com.rez.notif.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Key
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
    PEOPLE("پیام‌ها", Icons.Rounded.Forum, listOf(Category.PERSONAL, Category.SOCIAL, Category.MISSED, Category.EMAIL)),
    MONEY("بانک و کد", Icons.Rounded.AccountBalance, listOf(Category.BANK, Category.OTP, Category.SHOPPING, Category.SERVICES)),
    OTHER("بقیه", Icons.Rounded.Apps, listOf(Category.OPERATOR, Category.APPS, Category.SYSTEM, Category.OTHER)),
}

/** Stays on the same tab when coming back from another screen. */
private val currentTab = mutableStateOf(HomeTab.HOME)

@Composable
fun HomeScreen(nav: Navigator) {
    var tab by currentTab
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val counts by rememberLoaded(repo) { repo.categoryCounts().associateBy { it.category } }

    // Back from a category tab goes to Home first.
    BackHandler(enabled = tab != HomeTab.HOME) { tab = HomeTab.HOME }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            HomeTab.HOME -> HomeTabContent(nav)
            else -> CategoryTab(tab, nav)
        }
        BottomTabs(
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
        Spacer(Modifier.width(8.dp))
        IconBubble(Icons.Rounded.Settings, { nav.go(Screen.Settings) })
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
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repo.prefs.update { it.copy(contactsAsked = true) }
        kotlin.concurrent.thread { ContactNames.refresh(context); repo.bump() }
    }

    Column {
        TopBar(t("نوتیف"), today(), nav)
        ItemList(items = feed?.recent.orEmpty(), empty = t("امروز هنوز چیزی نیومده.")) { openItem ->
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
            feed?.code?.let { c -> item(key = "code") { CodeCard(c.title, codeOf(c), c.timestamp) } }
            val needs = feed?.needsYou.orEmpty()
            item(key = "needs-title") { SectionTitle(t("منتظر تو")) }
            if (feed != null && needs.isEmpty()) {
                item(key = "needs-none") {
                    Text(
                        t("چیز مهمی منتظرت نیست."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = g.textDim,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                    )
                }
            }
            needs.forEach { i -> item(key = "need-" + i.id) { ItemRow(i, onClick = { openItem(i) }) } }
            item(key = "recent-title") { SectionTitle(t("بقیه امروز")) }
        }
    }
}

/** A fresh login code, big, with one tap to copy. */
@Composable
private fun CodeCard(from: String, code: String?, time: Long) {
    val g = LocalGlass.current
    val context = LocalContext.current
    code ?: return
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), glow = Category.OTP.tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("کد تازه") + " · " + senderLabel(from) + " · " + shortTime(time), style = MaterialTheme.typography.bodySmall, color = g.textDim)
                Text(fa(code), color = g.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
            if (tab == HomeTab.OTHER) {
                item(key = "tools") {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassChip(t("افراد منتخب"), false, { nav.go(Screen.Vip) }, color = VipColor, icon = Icons.Rounded.Favorite)
                        GlassChip(t("تبلیغات مخفی‌شده") + " " + fa(adCount), false, { nav.go(Screen.Special(SpecialList.ADS)) }, color = Category.ADS.tint, icon = Icons.Rounded.Campaign)
                        GlassChip(t("پاک‌سازی"), false, { nav.go(Screen.Cleanup) }, icon = Icons.Rounded.CleaningServices)
                        GlassChip(t("کیف پسورد"), false, { nav.go(Screen.Wallet) }, color = ValuableColor, icon = Icons.Rounded.Key)
                        GlassChip(t("متن از عکس"), false, { nav.go(Screen.Ocr()) }, icon = Icons.Rounded.DocumentScanner)
                        GlassChip(t("آمار"), false, { nav.go(Screen.Stats) }, icon = Icons.Rounded.BarChart)
                    }
                }
            }
            item(key = "filters") {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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

// ---------- bottom bar ----------

@Composable
private fun BottomTabs(current: HomeTab, unread: (HomeTab) -> Int, onPick: (HomeTab) -> Unit, modifier: Modifier) {
    val g = LocalGlass.current
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(28.dp), elevation = 12.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HomeTab.entries.forEach { tb ->
            val on = tb == current
            val n = unread(tb)
            Column(
                Modifier.weight(1f).pressable({ onPick(tb) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    if (on) NeonIcon(tb.icon, g.accent, size = 34.dp)
                    else Icon(tb.icon, null, tint = g.textDim, modifier = Modifier.padding(5.dp).size(24.dp))
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
                    color = if (on) g.text else g.textDim,
                )
            }
        }
    }
}
