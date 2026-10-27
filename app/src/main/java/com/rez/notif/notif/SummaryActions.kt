package com.rez.notif.notif

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.rez.notif.NotifApp

/** The user swiped the summary away (or tapped Clear): what it showed counts as seen, so it doesn't come back. */
class SummarySeenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            runCatching { NotifApp.repo(context).prefs.update { it.copy(summarySeen = System.currentTimeMillis()) } }
            pending.finish()
        }.start()
    }
}

/**
 * An app icon in the summary was tapped. Invisible: opens that app at once, then marks its items
 * seen and refreshes the summary (so the icon leaves it).
 */
class OpenAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val sms = intent.getBooleanExtra(EXTRA_SMS, false)
        packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        val app = applicationContext
        Thread {
            runCatching {
                NotifApp.repo(app).seenApp(pkg, sms)
                SummaryNotifier.update(app)
            }
        }.start()
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_SMS = "sms"
    }
}
