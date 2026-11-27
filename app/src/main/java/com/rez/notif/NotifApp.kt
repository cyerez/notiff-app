package com.rez.notif

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.rez.notif.data.RelockMode
import com.rez.notif.security.AppLock
import com.rez.notif.data.Db
import com.rez.notif.data.Prefs
import com.rez.notif.data.Repository

/** App entry point. Holds the single Repository shared by screens, the SMS receiver and the listener. */
class NotifApp : Application() {

    val repository: Repository by lazy { Repository(this, Db.open(this), Prefs(this)) }

    override fun onCreate() {
        super.onCreate()
        com.rez.notif.data.BankNames.load(this)
        // After an app update the launcher may keep showing an old or empty widget: draw them again.
        com.rez.notif.widget.NotifWidget.refreshSoon(this)
        com.rez.notif.widget.CalendarWidget.refreshSoon(this)
        // "Lock when the screen turns off" needs to hear the screen going off while the app is in the background.
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val s = repository.prefs.current
                    if (s.appLock && s.relock == RelockMode.SCREEN_OFF) AppLock.lockNow()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
        )
        // Reminders and summary hours survive app updates this way (and reboots through AlarmReceiver).
        Thread {
            runCatching {
                com.rez.notif.notif.Reminders.arm(this)
                com.rez.notif.notif.Digest.arm(this)
            }
        }.start()
    }

    companion object {
        fun repo(context: Context): Repository = (context.applicationContext as NotifApp).repository
    }
}
