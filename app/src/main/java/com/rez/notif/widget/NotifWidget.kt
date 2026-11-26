package com.rez.notif.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.rez.notif.MainActivity
import com.rez.notif.NotifApp
import com.rez.notif.R
import com.rez.notif.classify.Category
import com.rez.notif.i18n.t
import com.rez.notif.ui.components.fa
import java.util.concurrent.Executors

/** Home-screen widget: unread count, today's count and the three busiest main categories. Tap opens the app. */
class NotifWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        worker.execute {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var scheduled = false

        /** Called when data changes; waits a moment so a burst of notifications updates the widget once. */
        fun refreshSoon(context: Context) {
            if (scheduled) return
            scheduled = true
            val app = context.applicationContext
            main.postDelayed({
                scheduled = false
                worker.execute { runCatching { refreshNow(app) } }
            }, 2_000)
        }

        private fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NotifWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = NotifApp.repo(context)
            val s = repo.prefs.current
            val w = repo.widgetData()
            val views = RemoteViews(context.packageName, R.layout.widget_notif)
            views.setTextViewText(R.id.widget_title, t("شاخ گاو"))
            views.setTextViewText(R.id.widget_today, t("امروز") + " " + fa(w.today))
            views.setTextViewText(R.id.widget_unread, fa(w.unread) + t(" خونده‌نشده"))
            val chips = listOf(R.id.widget_c1, R.id.widget_c2, R.id.widget_c3)
            val main = w.counts.filter { it.category in s.mainCategories && it.category != Category.ADS.id }
                .sortedByDescending { it.unread }.take(3)
            chips.forEachIndexed { i, id ->
                val c = main.getOrNull(i)
                views.setTextViewText(id, if (c == null) "" else t(Category.of(c.category).label) + " " + fa(c.unread))
                views.setInt(id, "setVisibility", if (c == null) android.view.View.INVISIBLE else android.view.View.VISIBLE)
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
