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
 * Calendar widget in a "bank app" look: a blue panel with today's Persian date and the next five days
 * (saffron = something on that day), a glass card with what's next, and buttons
 * (calendar, ledger, open the app, "+ یادآور" = add on today).
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        worker.execute {
            try {
                manager.updateAppWidget(ids, views(context))
                // Prices are fetched at most every 3 hours; draw again when new ones came.
                if (com.rez.notif.data.PriceStore.refreshIfOld(context)) {
                    manager.updateAppWidget(ids, views(context))
                    NotifWidget.refreshSoon(context)
                }
            } finally { pending.finish() }
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
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
            if (com.rez.notif.data.PriceStore.refreshIfOld(context)) {
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
                NotifWidget.refreshSoon(context)
            }
        }

        private fun color(k: Planner.MarkKind): Int = when (k) {
            Planner.MarkKind.EVENT -> 0xFF8FB4FF.toInt()
            Planner.MarkKind.EXPENSE -> 0xFFFF8A7E.toInt()
            Planner.MarkKind.TRIP -> 0xFF6FD8FF.toInt()
            Planner.MarkKind.INSTALLMENT -> 0xFFF2A93B.toInt()
            Planner.MarkKind.CHEQUE_IN -> 0xFF6BD48F.toInt()
            Planner.MarkKind.CHEQUE_OUT -> 0xFFE57BB8.toInt()
        }

        private fun kindLabel(k: Planner.MarkKind): String = when (k) {
            Planner.MarkKind.EVENT -> t("رویداد")
            Planner.MarkKind.EXPENSE -> t("خرج")
            Planner.MarkKind.TRIP -> t("سفر و دنگ")
            Planner.MarkKind.INSTALLMENT -> t("قسط")
            Planner.MarkKind.CHEQUE_IN -> t("چک دریافتی")
            Planner.MarkKind.CHEQUE_OUT -> t("چک پرداختی")
        }

        private fun shortAmount(toman: Long): String = when {
            toman >= 1_000_000 -> fa(String.format(java.util.Locale.US, "%.1f", toman / 1e6).removeSuffix(".0").replace('.', '٫')) + t(" م")
            toman >= 1_000 -> fa((toman / 1_000).toString()) + t(" هزار")
            else -> fa(toman.toString())
        }

        private fun whenText(day: Long, today: Long): String = when (day - today) {
            0L -> t("امروز")
            1L -> t("فردا")
            else -> Jalali.ofEpochDay(day).let { fa(it.d.toString()) + " " + t(Jalali.MONTHS[it.m - 1]) }
        }

        private fun line(mk: Planner.Mark, today: Long): CharSequence {
            val sb = SpannableStringBuilder()
            sb.append("● ", ForegroundColorSpan(color(mk.kind)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(whenText(mk.day, today), ForegroundColorSpan(0xFF9FB0D9.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ").append(mk.title)
            if (mk.toman > 0) sb.append("  ").append(shortAmount(mk.toman), ForegroundColorSpan(color(mk.kind)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        /** "دلار" on a small line, then "۲۹۲٫۹ هزار ▲". */
        private fun priceText(item: com.rez.notif.classify.Prices.Item, q: com.rez.notif.classify.Prices.Quote?): CharSequence {
            val sb = SpannableStringBuilder()
            sb.append(t(item.label), ForegroundColorSpan(0xFF9FB0D9.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.RelativeSizeSpan(0.82f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append('\n')
            if (q == null) { sb.append("—"); return sb }
            val (num, million) = com.rez.notif.classify.Prices.shortToman(q.rial)
            sb.append(fa(num.replace('.', '٫')) + (if (million) t(" م") else t(" هزار")))
            when {
                q.changePct > 0.05 -> sb.append(" ▲", ForegroundColorSpan(0xFF6BD48F.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                q.changePct < -0.05 -> sb.append(" ▼", ForegroundColorSpan(0xFFFF8A7E.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }

        private fun open(context: Context, code: Int, extras: Intent.() -> Unit) = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** The filled widget; also used by the older "شاخ گاو" widget when the notification module is off. */
        fun views(context: Context): RemoteViews {
            val repo = NotifApp.repo(context)
            val today = LocalDate.now().toEpochDay()
            val d = Jalali.ofEpochDay(today)
            val next = runCatching { repo.upcomingPlanNow(31) }.getOrDefault(emptyList())
            val busy = runCatching { repo.marksNow(5) }.getOrDefault(emptyList()).map { it.day }.toSet()
            val v = RemoteViews(context.packageName, R.layout.widget_calendar)

            v.setTextViewText(R.id.cal_app, t("شاخ گاو"))
            v.setTextViewText(R.id.cal_year, fa(d.y.toString()))
            v.setTextViewText(R.id.cal_weekday, t(Jalali.WEEK[Jalali.weekday(today)]))
            v.setTextViewText(R.id.cal_date, fa(d.d.toString()) + " " + t(Jalali.MONTHS[d.m - 1]))
            listOf(R.id.cal_d1, R.id.cal_d2, R.id.cal_d3, R.id.cal_d4, R.id.cal_d5).forEachIndexed { i, id ->
                val day = today + i
                v.setTextViewText(id, fa(Jalali.ofEpochDay(day).d.toString()))
                v.setInt(id, "setBackgroundResource", when { i == 0 -> R.drawable.wr_day_today; day in busy -> R.drawable.wr_day_mark; else -> R.drawable.wr_day })
                v.setTextColor(id, if (i == 0) 0xFF2F6BFF.toInt() else 0xFFFFFFFF.toInt())
            }

            val prices = com.rez.notif.data.PriceStore.saved(context)
            listOf(R.id.cal_p1, R.id.cal_p2, R.id.cal_p3).forEachIndexed { i, id ->
                val item = com.rez.notif.classify.Prices.Item.entries[i]
                val q = prices.quotes.firstOrNull { it.item == item }
                v.setTextViewText(id, priceText(item, q))
                v.setTextViewCompoundDrawablesRelative(id, com.rez.notif.ui.screens.priceIcon(item), 0, 0, 0)
            }
            v.setViewVisibility(R.id.cal_prices, if (prices.quotes.isEmpty()) View.GONE else View.VISIBLE)

            val first = next.firstOrNull()
            if (first == null) {
                v.setTextViewText(R.id.cal_dot, "✓")
                v.setTextViewText(R.id.cal_title, t("تا یه ماه دیگه چیزی تو تقویم نیست."))
                v.setTextViewText(R.id.cal_when, t("با «+ یادآور» یکی اضافه کن"))
                v.setTextViewText(R.id.cal_amount, "")
            } else {
                v.setTextViewText(R.id.cal_dot, fa(Jalali.ofEpochDay(first.day).d.toString()))
                v.setInt(R.id.cal_dot, "setBackgroundResource", R.drawable.wr_day_mark)
                v.setTextViewText(R.id.cal_title, first.title)
                v.setTextViewText(R.id.cal_when, t("پیش رو") + "  ·  " + whenText(first.day, today) + "  ·  " + kindLabel(first.kind))
                v.setTextViewText(R.id.cal_amount, if (first.toman > 0) shortAmount(first.toman) else "")
            }
            listOf(R.id.cal_l2, R.id.cal_l3).forEachIndexed { i, id ->
                val mk = next.getOrNull(i + 1)
                v.setTextViewText(id, if (mk == null) "" else line(mk, today))
                v.setViewVisibility(id, if (mk == null) View.GONE else View.VISIBLE)
            }

            v.setTextViewText(R.id.cal_home, t("باز کردن"))
            v.setTextViewText(R.id.cal_add, t("+ یادآور"))
            v.setOnClickPendingIntent(R.id.cal_root, open(context, 7101) { putExtra(EXTRA_CALENDAR, false) })
            v.setOnClickPendingIntent(R.id.cal_card, open(context, 7103) { putExtra(EXTRA_CALENDAR, false) })
            v.setOnClickPendingIntent(R.id.cal_btn_cal, open(context, 7104) { putExtra(EXTRA_CALENDAR, false) })
            v.setOnClickPendingIntent(R.id.cal_btn_bank, open(context, 7105) { putExtra(com.rez.notif.notif.LedgerAsk.EXTRA_OPEN_LEDGER, true) })
            v.setOnClickPendingIntent(R.id.cal_home, open(context, 7106) { })
            v.setOnClickPendingIntent(R.id.cal_add, open(context, 7102) { putExtra(EXTRA_CALENDAR, true) })
            return v
        }
    }
}
