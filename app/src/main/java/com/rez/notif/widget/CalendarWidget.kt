package com.rez.notif.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.rez.notif.MainActivity
import com.rez.notif.NotifApp
import com.rez.notif.R
import com.rez.notif.classify.Jalali
import com.rez.notif.classify.Planner
import com.rez.notif.i18n.t
import com.rez.notif.ui.components.fa
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Calendar widget: today's Persian date and the next things on the calendar (installments, cheques, trips,
 * events). "+ یادآور" opens the add dialog on today; a tap anywhere else opens the calendar.
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        worker.execute {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        /** Boolean extra for MainActivity: open the calendar (true: with the add dialog). */
        const val EXTRA_CALENDAR = "open_calendar"

        private val worker = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var scheduled = false

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
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun color(k: Planner.MarkKind): Int = when (k) {
            Planner.MarkKind.EVENT -> 0xFF5B8CFF.toInt()
            Planner.MarkKind.EXPENSE -> 0xFFFF6B5E.toInt()
            Planner.MarkKind.TRIP -> 0xFF3FD0E0.toInt()
            Planner.MarkKind.INSTALLMENT -> 0xFFF2A93B.toInt()
            Planner.MarkKind.CHEQUE_IN -> 0xFF6BD48F.toInt()
            Planner.MarkKind.CHEQUE_OUT -> 0xFFE57BB8.toInt()
        }

        private fun shortAmount(toman: Long): String = when {
            toman >= 1_000_000 -> fa(String.format(java.util.Locale.US, "%.1f", toman / 1e6).removeSuffix(".0").replace('.', '٫')) + t(" م")
            toman >= 1_000 -> fa((toman / 1_000).toString()) + t(" هزار")
            else -> fa(toman.toString())
        }

        private fun line(mk: Planner.Mark, today: Long): CharSequence {
            val whenText = when (mk.day - today) {
                0L -> t("امروز")
                1L -> t("فردا")
                else -> fa(Jalali.ofEpochDay(mk.day).d.toString()) + " " + t(Jalali.MONTHS[Jalali.ofEpochDay(mk.day).m - 1])
            }
            val sb = SpannableStringBuilder()
            sb.append("● ", ForegroundColorSpan(color(mk.kind)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(whenText, ForegroundColorSpan(0xFF9FB0D9.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ").append(mk.title)
            if (mk.toman > 0) sb.append("  ").append(shortAmount(mk.toman), ForegroundColorSpan(color(mk.kind)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = NotifApp.repo(context)
            val today = LocalDate.now().toEpochDay()
            val d = Jalali.ofEpochDay(today)
            val next = runCatching { repo.upcomingPlanNow(31) }.getOrDefault(emptyList()).take(4)
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            views.setTextViewText(R.id.cal_weekday, t(Jalali.WEEK[Jalali.weekday(today)]))
            views.setTextViewText(R.id.cal_day, fa(d.d.toString()))
            views.setTextViewText(R.id.cal_month, t(Jalali.MONTHS[d.m - 1]) + " " + fa(d.y.toString()))
            views.setTextViewText(R.id.cal_head, t("پیش رو"))
            val lines = listOf(R.id.cal_l1, R.id.cal_l2, R.id.cal_l3, R.id.cal_l4)
            lines.forEachIndexed { i, id ->
                val mk = next.getOrNull(i)
                views.setTextViewText(id, if (mk == null) "" else line(mk, today))
                views.setViewVisibility(id, if (mk == null) View.GONE else View.VISIBLE)
            }
            views.setTextViewText(R.id.cal_empty, t("تا یه ماه دیگه چیزی تو تقویم نیست."))
            views.setViewVisibility(R.id.cal_empty, if (next.isEmpty()) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.cal_add, t("+ یادآور"))

            fun open(add: Boolean, code: Int) = PendingIntent.getActivity(
                context, code,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CALENDAR, add),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.cal_root, open(false, 7101))
            views.setOnClickPendingIntent(R.id.cal_add, open(true, 7102))
            manager.updateAppWidget(ids, views)
        }
    }
}
