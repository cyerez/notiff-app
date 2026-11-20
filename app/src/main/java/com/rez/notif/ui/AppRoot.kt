package com.rez.notif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rez.notif.NotifApp
import com.rez.notif.data.Repository
import com.rez.notif.security.AppLock
import com.rez.notif.ui.components.GlassBackground
import com.rez.notif.ui.screens.CategoryScreen
import com.rez.notif.ui.screens.ConversationScreen
import com.rez.notif.ui.screens.OcrScreen
import com.rez.notif.ui.screens.BackupScreen
import com.rez.notif.ui.screens.CleanupScreen
import com.rez.notif.ui.screens.LearnedScreen
import com.rez.notif.ui.screens.LockScreen
import com.rez.notif.ui.screens.VipPersonScreen
import com.rez.notif.ui.screens.VipScreen
import com.rez.notif.ui.screens.WalletScreen
import com.rez.notif.ui.screens.HomeScreen
import com.rez.notif.ui.screens.OnboardingScreen
import com.rez.notif.ui.screens.ScanScreen
import com.rez.notif.ui.screens.SearchScreen
import com.rez.notif.ui.screens.SettingsScreen
import com.rez.notif.ui.screens.SpecialList
import com.rez.notif.ui.screens.SpecialListScreen
import com.rez.notif.ui.screens.StatsScreen
import com.rez.notif.ui.theme.NotifTheme

/** The screens of the app. */
sealed interface Screen {
    data object Onboarding : Screen
    data object Scan : Screen
    data object Home : Screen
    data class CategoryList(val categoryId: String) : Screen
    data class Special(val kind: SpecialList) : Screen
    data object Search : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Cleanup : Screen
    data object Learned : Screen
    data object Backup : Screen
    data object Wallet : Screen
    data object Vip : Screen
    data class VipPerson(val id: Long, val name: String) : Screen
    data class Conversation(val address: String, val draft: String = "") : Screen
    data class Ocr(val uri: String? = null) : Screen
    data object Rules : Screen
    data object Money : Screen
    data object Dong : Screen
    data object Budget : Screen
    data object Ledger : Screen
    data object Transfer : Screen
    data class Trip(val id: Long) : Screen
    data class RuleEdit(val rule: com.rez.notif.classify.Rule) : Screen
}

/** Screens asked for from outside the UI tree: a tapped SMS notification, "send message" from another app. */
object OpenRequests {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<Screen?>(null)
    fun open(s: Screen) { pending.value = s }
    /** An item to show in its detail sheet over whatever screen is open (a tapped reminder). */
    val item = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    fun openItem(id: Long) { item.value = id }
}

/** Simple back stack: the last screen is shown. */
class Navigator(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun reset(s: Screen) { stack.clear(); stack.add(s) }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { NotifApp.repo(context) }
    val settings by repo.prefs.state.collectAsState()
    val nav = remember {
        Navigator(
            when {
                !repo.prefs.current.onboardingDone -> Screen.Onboarding
                !repo.prefs.current.firstScanDone -> Screen.Scan
                else -> Screen.Home
            },
        )
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    val request by OpenRequests.pending.collectAsState()
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        OpenRequests.pending.value = null
        if (!repo.prefs.current.firstScanDone) return@LaunchedEffect
        // The same conversation twice in a row is just one screen.
        if (nav.current != r) nav.go(r)
    }

    val unlocked by AppLock.unlocked.collectAsState()
    // Changing the language rebuilds every screen so all texts switch at once.
    key(settings.language) { NotifTheme(settings) {
        GlassBackground {
            if (settings.appLock && !unlocked) {
                Box(Modifier.fillMaxSize().statusBarsPadding()) { LockScreen() }
                return@GlassBackground
            }
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
                },
                label = "screens",
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    when (screen) {
                        Screen.Onboarding -> OnboardingScreen(onDone = {
                            repo.prefs.update { it.copy(onboardingDone = true) }
                            nav.reset(if (repo.prefs.current.firstScanDone) Screen.Home else Screen.Scan)
                        })
                        Screen.Scan -> ScanScreen(onDone = { nav.reset(Screen.Home) })
                        Screen.Home -> HomeScreen(nav)
                        is Screen.CategoryList -> CategoryScreen(screen.categoryId, nav)
                        is Screen.Special -> SpecialListScreen(screen.kind, nav)
                        Screen.Search -> SearchScreen(nav)
                        Screen.Stats -> StatsScreen(nav)
                        Screen.Settings -> SettingsScreen(nav)
                        Screen.Cleanup -> CleanupScreen(nav)
                        Screen.Learned -> LearnedScreen(nav)
                        Screen.Backup -> BackupScreen(nav)
                        Screen.Wallet -> WalletScreen(nav)
                        Screen.Vip -> VipScreen(nav)
                        is Screen.VipPerson -> VipPersonScreen(screen.id, screen.name, nav)
                        is Screen.Conversation -> ConversationScreen(screen.address, screen.draft, nav)
                        is Screen.Ocr -> OcrScreen(screen.uri, nav)
                        Screen.Rules -> com.rez.notif.ui.screens.RulesScreen(nav)
                        Screen.Money -> com.rez.notif.ui.screens.MoneyScreen(nav)
                        Screen.Dong -> com.rez.notif.ui.screens.DongScreen(nav)
                        Screen.Budget -> com.rez.notif.ui.screens.BudgetScreen(nav)
                        Screen.Ledger -> com.rez.notif.ui.screens.LedgerScreen(nav)
                        Screen.Transfer -> com.rez.notif.ui.screens.TransferScreen(nav)
                        is Screen.Trip -> com.rez.notif.ui.screens.TripScreen(screen.id, nav)
                        is Screen.RuleEdit -> com.rez.notif.ui.screens.RuleEditScreen(screen.rule, nav)
                    }
                }
            }
            val openItem by OpenRequests.item.collectAsState()
            var shown by remember { mutableStateOf<com.rez.notif.data.Item?>(null) }
            LaunchedEffect(openItem) {
                val id = openItem ?: return@LaunchedEffect
                OpenRequests.item.value = null
                if (repo.prefs.current.firstScanDone) shown = repo.item(id)
            }
            shown?.let { com.rez.notif.ui.components.ItemSheet(it, onDismiss = { shown = null }) }
        }
    } }
}

/** Loads data with [loader] and reloads it whenever anything in the database changes. */
@Composable
fun <T> rememberLoaded(repo: Repository, vararg keys: Any?, loader: suspend () -> T): State<T?> {
    val version by repo.version.collectAsState()
    return produceState<T?>(initialValue = null, version, *keys) { value = loader() }
}
