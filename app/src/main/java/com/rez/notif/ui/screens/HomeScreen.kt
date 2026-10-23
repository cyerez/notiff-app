package com.rez.notif.ui.screens

import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Key
import com.rez.notif.ui.components.ItemRow
import com.rez.notif.data.Repository
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import com.rez.notif.i18n.t
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import com.rez.notif.sms.ContactNames
import com.rez.notif.ui.components.GlassButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rez.notif.NotifApp
import com.rez.notif.classify.Category
import com.rez.notif.data.CategoryCount
import com.rez.notif.data.ItemQuery
import com.rez.notif.ui.Navigator
import com.rez.notif.ui.Screen
import com.rez.notif.ui.components.Badge
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.GlassChip
import com.rez.notif.ui.components.GlassTopBar
import com.rez.notif.ui.components.IconBubble
import com.rez.notif.ui.components.ItemList
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.components.NeonNumber
import com.rez.notif.ui.components.SectionTitle
import com.rez.notif.ui.components.ValuableColor
import com.rez.notif.ui.components.VipColor
import com.rez.notif.ui.components.fa
import com.rez.notif.ui.components.icon
import com.rez.notif.ui.components.tint
import com.rez.notif.ui.components.today
import com.rez.notif.ui.rememberLoaded
import com.rez.notif.ui.theme.LocalGlass

private data class HomeData(
    val counts: List<CategoryCount>,
    val todayCount: Int,
    val valuable: Int,
    val vips: Int,
    val vipItems: Int,
    val clutter: Int,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Navigator) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val settings by repo.prefs.state.collectAsState()

    val data by rememberLoaded(repo) {
        val counts = repo.categoryCounts()
        HomeData(
            counts = counts,
            todayCount = repo.today(false).first.size,
            valuable = repo.count(ItemQuery(onlyValuable = true, includeAds = true)),
            vips = repo.vipSummaries().size,
            vipItems = repo.count(ItemQuery(onlyVip = true, includeAds = true)),
            clutter = repo.prefs.current.cleanupRules.let { on -> repo.cleanupPreview().filterKeys { it.name in on }.values.sum() },
        )
    }
    val recent by rememberLoaded(repo) { repo.items(ItemQuery(limit = 40)) }
    val important by rememberLoaded(repo) { repo.important() }
    val away by rememberLoaded(repo, settings.awaySince) { repo.awaySummary() }

    val counts = data?.counts.orEmpty().associateBy { it.category }
    val unreadAll = data?.counts.orEmpty().filter { it.category != Category.ADS.id }.sumOf { it.unread }
    val main = Category.entries.filter { it.id in settings.mainCategories && it != Category.ADS }
    val others = Category.entries.filter { it.id !in settings.mainCategories && it != Category.ADS && (counts[it.id]?.total ?: 0) > 0 }
    val adCount = counts[Category.ADS.id]?.total ?: 0
    var edit by remember { mutableStateOf(false) }
    var showAllImportant by remember { mutableStateOf(false) }

    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repo.prefs.update { it.copy(contactsAsked = true) }
        kotlin.concurrent.thread { ContactNames.refresh(context); repo.bump() }
    }
    val order = HomeBlock.ordered(settings.homeOrder)
    fun move(b: HomeBlock, by: Int) {
        val l = order.toMutableList()
        val i = l.indexOf(b)
        val j = (i + by).coerceIn(0, l.lastIndex)
        if (i == j) return
        l.removeAt(i); l.add(j, b)
        repo.prefs.update { it.copy(homeOrder = l.map { x -> x.name }) }
    }
    fun toggleHidden(b: HomeBlock) = repo.prefs.update {
        it.copy(homeHidden = if (b.name in it.homeHidden) it.homeHidden - b.name else it.homeHidden + b.name)
    }

    Column {
        GlassTopBar(title = t("نوتیف"), subtitle = today()) {
            IconBubble(if (edit) Icons.Rounded.Check else Icons.Rounded.Edit, { edit = !edit }, tint = if (edit) g.accent else null)
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.Search, { nav.go(Screen.Search) })
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.BarChart, { nav.go(Screen.Stats) })
            Spacer(Modifier.width(8.dp))
            IconBubble(Icons.Rounded.Settings, { nav.go(Screen.Settings) })
        }

        ItemList(
            items = recent.orEmpty(),
            empty = t("هنوز چیزی نیومده."),
        ) { openItem ->
            if (edit) {
                item {
                    Text(
                        t("ترتیب بخش‌ها رو با فلش‌ها عوض کن و با چشم پنهان یا پیداشون کن. دسته‌های اصلی رو هم همینجا انتخاب کن. آخرش ✓ رو بزن."),
                        style = MaterialTheme.typography.bodySmall,
                        color = g.textDim,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                    )
                }
            }
            if (!edit && settings.firstScanDone && !settings.contactsAsked && !ContactNames.allowed(context)) {
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
            if (!edit) away?.let { a ->
                item { AwayCard(a) { repo.prefs.update { it.copy(awaySince = 0) } } }
            }
            order.forEachIndexed { index, block ->
                val hidden = block.name in settings.homeHidden
                if (edit) {
                    item(key = "edit-" + block.name) {
                        BlockEditBar(
                            t(block.label), hidden,
                            canUp = index > 0, canDown = index < order.lastIndex,
                            onUp = { move(block, -1) }, onDown = { move(block, 1) }, onEye = { toggleHidden(block) },
                        )
                    }
                }
                if (hidden) return@forEachIndexed
                when (block) {
                    HomeBlock.SUMMARY -> item(key = "b-summary") {
                        // Three big tiles, like the "Inbox / To-Do / Notifications" sample.
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryTile(t("امروز"), fa(data?.todayCount ?: 0), g.accent, Modifier.weight(1f)) {
                                nav.go(Screen.Special(SpecialList.TODAY))
                            }
                            SummaryTile(t("خونده‌نشده"), fa(unreadAll), Color(0xFF34D399), Modifier.weight(1f)) {
                                nav.go(Screen.Special(SpecialList.UNREAD))
                            }
                            SummaryTile(t("ارزشمند"), fa(data?.valuable ?: 0), ValuableColor, Modifier.weight(1f)) {
                                nav.go(Screen.Special(SpecialList.VALUABLE))
                            }
                        }
                    }
                    HomeBlock.ACTIONS -> item(key = "b-actions") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile(
                                Icons.Rounded.Favorite, VipColor, t("افراد منتخب"),
                                if ((data?.vips ?: 0) == 0) t("هنوز کسی نیست") else fa(data?.vips ?: 0) + t(" نفر · ") + fa(data?.vipItems ?: 0) + t(" مورد"),
                                Modifier.weight(1f),
                            ) { nav.go(Screen.Vip) }
                            ActionTile(
                                Icons.Rounded.CleaningServices, g.accent, t("پاک‌سازی"),
                                if ((data?.clutter ?: 0) == 0) t("همه چی مرتبه") else fa(data?.clutter ?: 0) + t(" مورد اضافه"),
                                Modifier.weight(1f),
                            ) { nav.go(Screen.Cleanup) }
                        }
                    }
                    HomeBlock.TOOLS -> item(key = "b-tools") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile(
                                Icons.Rounded.Key, ValuableColor, t("کیف پسورد"), t("رمزهای ضروری، آفلاین"),
                                Modifier.weight(1f),
                            ) { nav.go(Screen.Wallet) }
                            ActionTile(
                                Icons.Rounded.DocumentScanner, g.accent2, t("متن از عکس"), t("با قلم یا هر عکسی"),
                                Modifier.weight(1f),
                            ) { nav.go(Screen.Ocr()) }
                        }
                    }
                    HomeBlock.MAIN -> {
                        if (edit) {
                            item(key = "b-main-pick") {
                                FlowRow(
                                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Category.entries.filter { it != Category.ADS }.forEach { c ->
                                        val on = c.id in settings.mainCategories
                                        GlassChip(t(c.label), on, {
                                            repo.prefs.update { st ->
                                                st.copy(mainCategories = if (on) st.mainCategories - c.id else st.mainCategories + c.id)
                                            }
                                        }, color = c.tint, icon = c.icon)
                                    }
                                }
                            }
                        } else if (main.isNotEmpty()) {
                            item(key = "b-main-title") { SectionTitle(t("دسته‌های اصلی")) }
                            item(key = "b-main") {
                                FlowRow(
                                    Modifier.padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    maxItemsInEachRow = 2,
                                ) {
                                    main.forEach { c ->
                                        CategoryTile(c, counts[c.id], Modifier.weight(1f)) { nav.go(Screen.CategoryList(c.id)) }
                                    }
                                    if (main.size % 2 == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    HomeBlock.OTHERS -> if (others.isNotEmpty() && !edit) {
                        item(key = "b-others-title") { SectionTitle(t("بقیه دسته‌ها")) }
                        item(key = "b-others") {
                            FlowRow(
                                Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                others.forEach { c ->
                                    GlassChip(
                                        t(c.label) + " " + fa(counts[c.id]?.total ?: 0),
                                        selected = false,
                                        color = c.tint,
                                        icon = c.icon,
                                        onClick = { nav.go(Screen.CategoryList(c.id)) },
                                    )
                                }
                            }
                        }
                    }
                    HomeBlock.ADS -> item(key = "b-ads") {
                        GlassCard(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            onClick = { nav.go(Screen.Special(SpecialList.ADS)) },
                            contentPadding = 14.dp,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NeonIcon(Icons.Rounded.Campaign, Category.ADS.tint, size = 36.dp)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t("تبلیغات مخفی‌شده"), style = MaterialTheme.typography.titleSmall, color = g.text)
                                    Text(
                                        if (settings.hideAds) t("نوتیف تبلیغ‌ها خودکار پاک میشه") else t("مخفی کردن تبلیغ خاموشه"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = g.textDim,
                                    )
                                }
                                Text(fa(adCount), style = MaterialTheme.typography.titleMedium, color = Category.ADS.tint)
                            }
                        }
                    }
                    HomeBlock.IMPORTANT -> if (!edit) {
                        // Two are shown; the rest on tap.
                        val top = important.orEmpty()
                        if (top.isNotEmpty()) {
                            item(key = "b-important-title") {
                                SectionTitle(t("مهم‌ها")) {
                                    if (top.size > 2) {
                                        GlassChip(
                                            if (showAllImportant) t("کمتر") else t("همه") + " " + fa(top.size),
                                            selected = showAllImportant,
                                            onClick = { showAllImportant = !showAllImportant },
                                        )
                                    }
                                }
                            }
                            (if (showAllImportant) top else top.take(2)).forEach { i -> item(key = "imp-" + i.id) { ItemRow(i, onClick = { openItem(i) }) } }
                        }
                    }
                }
            }
            item { SectionTitle(t("تازه‌ها")) }
        }
    }
}

/** Up / down / show-hide controls above a home section while editing. */
@Composable
private fun BlockEditBar(title: String, hidden: Boolean, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onEye: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (hidden) g.textDim else g.accent,
            modifier = Modifier.weight(1f),
        )
        if (canUp) { IconBubble(Icons.Rounded.KeyboardArrowUp, onUp); Spacer(Modifier.width(6.dp)) }
        if (canDown) { IconBubble(Icons.Rounded.KeyboardArrowDown, onDown); Spacer(Modifier.width(6.dp)) }
        IconBubble(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, onEye, tint = if (hidden) g.textDim else g.accent)
    }
}

/** The sections of the home screen the user can move and hide. */
enum class HomeBlock(val label: String) {
    SUMMARY("کاشی‌های امروز، خونده‌نشده، ارزشمند"),
    ACTIONS("افراد منتخب و پاک‌سازی"),
    TOOLS("کیف پسورد و متن از عکس"),
    MAIN("دسته‌های اصلی"),
    OTHERS("بقیه دسته‌ها"),
    ADS("تبلیغات مخفی‌شده"),
    IMPORTANT("مهم‌ها");

    companion object {
        /** Saved order, with any new section added at its default place. */
        fun ordered(saved: List<String>): List<HomeBlock> {
            val known = saved.mapNotNull { n -> entries.firstOrNull { it.name == n } }
            return known + entries.filter { it !in known }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick, contentPadding = 12.dp) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = g.text, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        NeonNumber(value, color, size = 58.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
    }
}

/** "While you were away": shown after 2+ hours away, until closed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AwayCard(a: Repository.Away, onClose: () -> Unit) {
    val g = LocalGlass.current
    val hours = ((System.currentTimeMillis() - a.since) / 3_600_000L).toInt().coerceAtLeast(2)
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), glow = g.accent2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(Icons.Rounded.History, g.accent2, size = 40.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("وقتی نبودی"), style = MaterialTheme.typography.titleMedium, color = g.text)
                Text(
                    fa(hours) + t(" ساعت") + "  ·  " + fa(a.total) + t(" مورد") +
                        (if (a.vip > 0) "  ·  " + fa(a.vip) + t(" از افراد منتخب") else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.textDim,
                )
            }
            IconBubble(Icons.Rounded.Close, onClose)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            a.byCategory.filter { it.first != Category.ADS }.take(6).forEach { (c, n) ->
                Badge(n, c.tint)
                Text(t(c.label), style = MaterialTheme.typography.labelMedium, color = g.text, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, color: Color, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(22.dp), onClick = onClick, contentPadding = 12.dp, glow = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon, color, size = 36.dp)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = g.text, maxLines = 1)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = g.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Category, count: CategoryCount?, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(c.icon, c.tint, size = 40.dp)
            Spacer(Modifier.weight(1f))
            Badge(count?.unread ?: 0, c.tint)
        }
        Spacer(Modifier.height(12.dp))
        Text(t(c.label), style = MaterialTheme.typography.titleMedium, color = g.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(fa(count?.total ?: 0) + t(" مورد"), style = MaterialTheme.typography.bodySmall, color = g.textDim)
    }
}
