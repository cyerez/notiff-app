package com.rez.notif

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.rez.notif.notif.NotifListener
import com.rez.notif.notif.SummaryNotifier
import com.rez.notif.security.AppLock
import com.rez.notif.security.WalletVault
import com.rez.notif.sms.HeadlessSmsSendService
import com.rez.notif.sms.SmsNotifier
import com.rez.notif.ui.AppRoot
import com.rez.notif.ui.OpenRequests
import com.rez.notif.ui.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Opens a conversation for a tapped SMS notification, or for "send message" from another app. */
    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getLongExtra(com.rez.notif.notif.Reminders.EXTRA_ITEM, 0L).takeIf { it > 0 }?.let {
            intent.removeExtra(com.rez.notif.notif.Reminders.EXTRA_ITEM)
            OpenRequests.openItem(it)
            return
        }
        if (intent.getBooleanExtra(com.rez.notif.notif.LedgerAsk.EXTRA_OPEN_LEDGER, false)) {
            intent.removeExtra(com.rez.notif.notif.LedgerAsk.EXTRA_OPEN_LEDGER)
            OpenRequests.open(Screen.Ledger)
            return
        }
        intent.getStringExtra(SmsNotifier.EXTRA_ADDRESS)?.let {
            intent.removeExtra(SmsNotifier.EXTRA_ADDRESS)
            OpenRequests.open(Screen.Conversation(it))
            return
        }
        // A shared picture (S Pen Smart select → Share → "Picture to text").
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: intent.clipData?.getItemAt(0)?.uri ?: return
            OpenRequests.open(Screen.Ocr(uri.toString()))
            return
        }
        if (intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            val to = HeadlessSmsSendService.recipients(intent.data).firstOrNull() ?: return
            val draft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            OpenRequests.open(Screen.Conversation(to, draft))
        }
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
        WalletVault.lock()
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
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Contacts may have changed while the app was closed.
                com.rez.notif.sms.ContactNames.refresh(this@MainActivity)
            }
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
