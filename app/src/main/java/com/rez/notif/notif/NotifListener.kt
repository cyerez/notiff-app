package com.rez.notif.notif

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rez.notif.NotifApp
import com.rez.notif.data.NotificationIn
import java.util.concurrent.Executors

/**
 * Android calls this service for every notification once the user turns on
 * "Notification access" for the app in phone settings.
 */
class NotifListener : NotificationListenerService() {

    /** One background thread, so notifications are stored in order and never block the system. */
    private val worker = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        instance = this
        // Pick up what is already on screen right now (the "first scan" for notifications).
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        worker.execute { active.forEach { safeHandle(it) } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        worker.execute { safeHandle(sbn) }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    /**
     * Clears the status bar except what the user cares about (see Repository.keepInShade).
     * Everything cleared is already saved in the app, so nothing is lost.
     */
    fun clearNonEssential() {
        worker.execute {
            try {
                val repo = NotifApp.repo(this)
                val keys = activeNotifications.orEmpty().filter { sbn ->
                    val n = sbn.notification
                    sbn.packageName != packageName && sbn.isClearable && n != null && !mustKeep(sbn) &&
                        n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                        !repo.keepInShade(
                            sbn.packageName,
                            sbn.packageName in SMS_APPS,
                            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                            (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
                            sbn.postTime,
                        )
                }.map { it.key }
                if (keys.isNotEmpty()) cancelNotifications(keys.toTypedArray())
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        main.removeCallbacksAndMessages(null)
        worker.shutdown()
        super.onDestroy()
    }

    private fun safeHandle(sbn: StatusBarNotification) {
        try { handle(sbn) } catch (_: Exception) { }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        // Music, calls, navigation, downloads, VPN, recorders...: never stored, never hidden.
        if (mustKeep(sbn)) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: lines
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val input = NotificationIn(
            key = sbn.key,
            packageName = pkg,
            appName = appLabel(pkg),
            title = title,
            text = text,
            postTime = sbn.postTime,
            contentIntent = n.contentIntent,
            isSmsApp = pkg in SMS_APPS,
            // The app's own "reply" button (Telegram, WhatsApp...), so we can answer from inside Notif.
            reply = n.actions?.firstOrNull { a ->
                !a.remoteInputs.isNullOrEmpty() &&
                    (a.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY || a.remoteInputs.any { it.allowFreeFormInput })
            },
        )
        val decision = NotifApp.repo(this).addNotification(input)
        decision.code?.let { CodeNotifier.show(this, it, if (input.isSmsApp) title else input.appName) }
        if (decision.hide) {
            val wait = sbn.postTime + decision.holdMs - System.currentTimeMillis()
            if (wait <= 500) hide(sbn.key) else later(sbn.key, wait)
        }
    }

    private fun hide(key: String) {
        try { cancelNotification(key) } catch (_: Exception) { }
        SummaryNotifier.update(this)
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val pending = HashMap<String, Runnable>()

    /** Leaves the notification up for [ms] so it can be read, then tidies it away. A newer post of the same notification restarts the wait. */
    private fun later(key: String, ms: Long) {
        main.post {
            pending.remove(key)?.let(main::removeCallbacks)
            val r = Runnable { pending.remove(key); worker.execute { hide(key) } }
            pending[key] = r
            main.postDelayed(r, ms)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        main.post { pending.remove(sbn.key)?.let(main::removeCallbacks) }
    }

    private val labels = HashMap<String, String>()

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    companion object {
        private val KEEP_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT, // music and video players
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_LOCATION_SHARING,
            Notification.CATEGORY_WORKOUT,
        )

        /**
         * Notifications that belong to something running (a player, a call, a foreground service).
         * Since Android 14 these can be "clearable", but removing them breaks the app (music stops,
         * the controls vanish), so we leave them completely alone.
         */
        fun mustKeep(sbn: StatusBarNotification): Boolean {
            val n = sbn.notification ?: return true
            if (sbn.isOngoing) return true
            val keepFlags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR
            if (n.flags and keepFlags != 0) return true
            if (n.category in KEEP_CATEGORIES) return true
            val e = n.extras ?: return false
            if (e.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
            val template = e.getString(Notification.EXTRA_TEMPLATE).orEmpty()
            if ("MediaStyle" in template || "CallStyle" in template) return true
            if (e.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) return true
            return false
        }

        /** The running listener, when notification access is on. */
        @Volatile var instance: NotifListener? = null

        /** Notifications from these apps are SMS we already store through SmsReceiver. */
        val SMS_APPS = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
        )
    }
}
