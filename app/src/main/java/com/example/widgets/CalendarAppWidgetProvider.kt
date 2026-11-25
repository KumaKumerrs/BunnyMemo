package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.data.PlannerDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CalendarAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataLoader.load(context)
                updateWidgets(context, appWidgetManager, appWidgetIds, snapshot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val today = snapshot.today
            val monthTitle = PlannerDateUtils.formatMonthYear(today)
            val scheduledCount = snapshot.scheduledDatesInMonth.size
            val bloomCount = snapshot.habitBloomDates.count {
                it.year == today.year && it.month == today.month
            }
            val subtitle = when {
                scheduledCount > 0 && bloomCount > 0 -> "$scheduledCount planned days · $bloomCount blooms"
                scheduledCount > 0 -> "$scheduledCount planned ${if (scheduledCount == 1) "day" else "days"} this month"
                bloomCount > 0 -> "$bloomCount forget-me-not ${if (bloomCount == 1) "bloom" else "blooms"} this month"
                else -> PlannerDateUtils.formatFullDateHeader(today)
            }

            val calendarBitmap = WidgetBitmapRenderer.renderMonthCalendarBitmap(
                today = today,
                scheduledDates = snapshot.scheduledDatesInMonth,
                habitBloomDates = snapshot.habitBloomDates
            )

            // Build concise Today highlight lines
            val highlights = buildList {
                snapshot.todayEvents.forEach { ev ->
                    val time = PlannerDateUtils.formatEventTimeRange(ev.startAtUtcMillis, ev.endAtUtcMillis)
                    add(if (time.isNotBlank()) "Event · ${ev.title} ($time)" else "Event · ${ev.title}")
                }
                snapshot.todayClasses.forEach { cls ->
                    add("Class · ${cls.subject} (${PlannerDateUtils.formatTimeOfDay(cls.startTimeMinutes)})")
                }
                snapshot.todayActionableTasks.filter { !it.completed }.forEach { task ->
                    add("Task · ${task.title}")
                }
            }

            val line1: String
            val line2: String
            when {
                highlights.isEmpty() -> {
                    line1 = "Today (${PlannerDateUtils.formatCompactDate(today)}) · Quiet day"
                    line2 = "Tap + to add an event, task, or plan."
                }
                highlights.size == 1 -> {
                    line1 = highlights[0]
                    line2 = "Tap calendar to explore full month"
                }
                else -> {
                    line1 = highlights[0]
                    line2 = if (highlights.size > 2) {
                        "${highlights[1]} (+${highlights.size - 2} more)"
                    } else {
                        highlights[1]
                    }
                }
            }

            val openCalendarIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1101,
                tab = WidgetIntents.TAB_CALENDAR
            )
            val addEventIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1102,
                tab = WidgetIntents.TAB_CALENDAR,
                openAction = WidgetIntents.ACTION_OPEN_ADD_ITEM
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_calendar).apply {
                    setTextViewText(R.id.widget_cal_month_title, monthTitle)
                    setTextViewText(R.id.widget_cal_subtitle, subtitle)
                    setImageViewBitmap(R.id.widget_cal_grid_image, calendarBitmap)
                    setTextViewText(R.id.widget_cal_today_line1, line1)
                    setTextViewText(R.id.widget_cal_today_line2, line2)

                    setOnClickPendingIntent(android.R.id.background, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_cal_header, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_cal_grid_container, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_cal_footer, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_cal_add_btn, addEventIntent)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
