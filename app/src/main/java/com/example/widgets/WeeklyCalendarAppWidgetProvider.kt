package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.example.R
import com.example.data.PlannerDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen widget displaying ONLY the current 7-day week (Sunday–Saturday)
 * with today's highlight, scheduled day indicators, and forget-me-not blooms.
 */
class WeeklyCalendarAppWidgetProvider : AppWidgetProvider() {

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
            } catch (e: Exception) {
                Log.w(TAG, "Weekly calendar widget update failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WeekWidgetProvider"

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val today = snapshot.today
            val weekDates = snapshot.currentWeekDates
            val weekRangeTitle = if (weekDates.size >= 7) {
                "This Week · ${PlannerDateUtils.formatCompactDate(weekDates.first())} – ${PlannerDateUtils.formatCompactDate(weekDates.last())}"
            } else {
                "This Week"
            }

            val plannedDays = snapshot.scheduledDatesInCurrentWeek.size
            val bloomDays = weekDates.count { it in snapshot.habitBloomDates }
            val subtitle = when {
                plannedDays > 0 && bloomDays > 0 -> "$plannedDays planned days · $bloomDays blooms this week"
                plannedDays > 0 -> "$plannedDays planned ${if (plannedDays == 1) "day" else "days"} this week"
                bloomDays > 0 -> "$bloomDays forget-me-not ${if (bloomDays == 1) "bloom" else "blooms"} this week"
                else -> PlannerDateUtils.formatFullDateHeader(today)
            }

            val weekStripBitmap = WidgetBitmapRenderer.renderCurrentWeekStripBitmap(
                today = today,
                weekDates = weekDates,
                scheduledDates = snapshot.scheduledDatesInCurrentWeek,
                habitBloomDates = snapshot.habitBloomDates,
                itemCountByDate = snapshot.weekItemCountByDate,
                isDark = WidgetBitmapRenderer.isDarkMode(context)
            )

            val footerText = buildString {
                val firstEvent = snapshot.todayEvents.firstOrNull()
                val firstClass = snapshot.todayClasses.firstOrNull()
                val firstOpenTask = snapshot.todayActionableTasks.firstOrNull { !it.completed }
                when {
                    firstEvent != null -> {
                        val time = PlannerDateUtils.formatEventTimeRange(firstEvent.startAtUtcMillis, firstEvent.endAtUtcMillis)
                        append("Today · ${firstEvent.title}")
                        if (time.isNotBlank()) append(" ($time)")
                    }
                    firstClass != null -> {
                        append("Today · ${firstClass.subject} (${PlannerDateUtils.formatTimeOfDay(firstClass.startTimeMinutes)})")
                    }
                    firstOpenTask != null -> {
                        val openCount = snapshot.todayActionableTasks.count { !it.completed }
                        append("Today · ${firstOpenTask.title}")
                        if (openCount > 1) append(" (+${openCount - 1} more)")
                    }
                    else -> {
                        append("Today (${PlannerDateUtils.formatCompactDate(today)}) · Clear & calm")
                    }
                }
            }

            val openCalendarIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1501,
                tab = WidgetIntents.TAB_CALENDAR
            )
            val addItemIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1502,
                tab = WidgetIntents.TAB_CALENDAR,
                openAction = WidgetIntents.ACTION_OPEN_ADD_ITEM
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_weekly_calendar).apply {
                    setTextViewText(R.id.widget_week_title, weekRangeTitle)
                    setTextViewText(R.id.widget_week_subtitle, subtitle)
                    setImageViewBitmap(R.id.widget_week_strip_image, weekStripBitmap)
                    setTextViewText(R.id.widget_week_footer_text, footerText)

                    setOnClickPendingIntent(android.R.id.background, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_week_header, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_week_strip_container, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_week_footer_text, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_week_add_btn, addItemIntent)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
