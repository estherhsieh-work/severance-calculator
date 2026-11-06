package tw.personal.severance

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class SeveranceWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val PREFS = "personal_widget"
        private const val DELTA = "meeting_delta"
        private val start = LocalDate.of(2026, 6, 23)
        private val baseline = LocalDate.of(2026, 11, 3)
        private const val salary = 39300.0
        private const val baseMeetings = 15

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SeveranceWidgetProvider::class.java)
            )
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val today = LocalDate.now()
            val elapsed = ChronoUnit.DAYS.between(start, today).coerceAtLeast(0)
            val inclusive = elapsed + 1
            val noticeDays = when {
                elapsed < 90 -> 0
                elapsed < 365 -> 10
                elapsed < 3 * 365 -> 20
                else -> 30
            }
            val weeks = ChronoUnit.WEEKS.between(
                baseline.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
                today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            ).coerceAtLeast(0)
            val delta = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(DELTA, 0)
            val meetings = (baseMeetings + weeks.toInt() + delta).coerceAtLeast(0)
            val severance = salary * 0.5 * elapsed / 365.0
            val notice = salary / 30.0 * noticeDays
            val overtime = salary / 240.0 * 1.34 * 1.5 * meetings
            val total = severance + notice + overtime
            val fmt = NumberFormat.getNumberInstance(Locale.TAIWAN)
            fun money(n: Double) = "NT$" + fmt.format(n.toLong())

            val v = RemoteViews(context.packageName, R.layout.widget_severance)
            v.setTextViewText(R.id.today, today.toString())
            v.setTextViewText(R.id.days, "在職第 ${fmt.format(inclusive)} 天")
            v.setTextViewText(R.id.total, money(total))
            v.setTextViewText(R.id.details,
                "資遣 ${money(severance)}  ·  預告 ${money(notice)}\n週會 ${meetings} 次：${money(overtime)}")
            v.setTextViewText(R.id.meetings, "週會次數：$meetings")
            v.setOnClickPendingIntent(R.id.minus, action(context, "minus"))
            v.setOnClickPendingIntent(R.id.plus, action(context, "plus"))
            manager.updateAppWidget(id, v)
        }

        private fun action(context: Context, action: String): PendingIntent {
            val i = Intent(context, SeveranceWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, if (action == "plus") 2 else 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "plus" || intent.action == "minus") {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = p.getInt(DELTA, 0)
            p.edit().putInt(DELTA, old + if (intent.action == "plus") 1 else -1).apply()
            refresh(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }
}
