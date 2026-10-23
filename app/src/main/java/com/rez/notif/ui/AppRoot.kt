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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rez.notif.NotifApp
import com.rez.notif.data.Repository
import com.rez.notif.ui.components.GlassBackground
import com.rez.notif.ui.screens.CategoryScreen
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

    NotifTheme(settings) {
        GlassBackground {
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
                    }
                }
            }
        }
    }
}

/** Loads data with [loader] and reloads it whenever anything in the database changes. */
@Composable
fun <T> rememberLoaded(repo: Repository, vararg keys: Any?, loader: suspend () -> T): State<T?> {
    val version by repo.version.collectAsState()
    return produceState<T?>(initialValue = null, version, *keys) { value = loader() }
}
