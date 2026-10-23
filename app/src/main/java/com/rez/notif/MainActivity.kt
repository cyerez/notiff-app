package com.rez.notif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.rez.notif.notif.NotifListener
import com.rez.notif.notif.SummaryNotifier
import com.rez.notif.security.AppLock
import com.rez.notif.ui.AppRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onReturn(NotifApp.repo(this).prefs.current.relock)
        // While the lock is on, the recent-apps preview stays blank.
        setRecentsScreenshotEnabled(!NotifApp.repo(this).prefs.current.appLock)
    }

    override fun onDestroy() {
        // Closed for real (swiped away from recent apps), not just rotated: lock again.
        if (isFinishing && !isChangingConfigurations) AppLock.lockNow()
        super.onDestroy()
    }

    override fun onStop() {
        AppLock.onLeave()
        // Time away (and the summary notification) counts from when the user left the app.
        NotifApp.repo(this).let { r -> if (r.prefs.current.firstScanDone) r.prefs.update { it.copy(lastSeen = System.currentTimeMillis()) } }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Catch SMS that arrived while the app was not running (for example right after a reboot).
        val repo = NotifApp.repo(this)
        if (!repo.prefs.current.firstScanDone) return
        // Everything new is now in front of the user: reset the summary notification.
        val now = System.currentTimeMillis()
        repo.prefs.update {
            val away = if (it.lastSeen > 0 && now - it.lastSeen > 2 * 3_600_000L) it.lastSeen else it.awaySince
            it.copy(lastSeen = now, awaySince = away)
        }
        SummaryNotifier.clear(this)
        lifecycleScope.launch {
            if (hasSmsPermission()) runCatching { repo.syncSms() }
            runCatching { repo.autoCleanIfDue() }
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.ensureLearner() } }
        }
        // Opening the app tidies the status bar (see Repository.keepInShade).
        if (repo.prefs.current.clearOnOpen) NotifListener.instance?.clearNonEssential()
    }

    private fun hasSmsPermission() =
        checkSelfPermission(android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
