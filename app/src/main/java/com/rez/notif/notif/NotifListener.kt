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
                    sbn.packageName != packageName && sbn.isClearable && !sbn.isOngoing && n != null &&
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
        if (sbn.isOngoing) return // music, downloads, navigation...
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
        )
        val decision = NotifApp.repo(this).addNotification(input)
        decision.code?.let { CodeNotifier.show(this, it, if (input.isSmsApp) title else input.appName) }
        if (decision.hide) {
            try { cancelNotification(sbn.key) } catch (_: Exception) { }
            SummaryNotifier.update(this)
        }
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
