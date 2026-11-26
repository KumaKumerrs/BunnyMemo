package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickActionsAppWidgetProvider : AppWidgetProvider() {

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
                Log.w(TAG, "Quick Actions widget update failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuickWidgetProvider"

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val habitBitmap = WidgetBitmapRenderer.renderWeeklyHabitStripBitmap(
                today = snapshot.today,
                habitBloomDates = snapshot.habitBloomDates,
                isDark = WidgetBitmapRenderer.isDarkMode(context)
            )

            val streakText = when (snapshot.habitStreakDays) {
                0 -> "Start a bloom"
                1 -> "1d bloom streak"
                else -> "${snapshot.habitStreakDays}d bloom streak"
            }

            val latestNoteText = snapshot.latestQuickNote?.let { note ->
                "Latest note: ${note.title}"
            } ?: "Tap Quick Note to capture a spontaneous thought"

            val openTodayIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1401,
                tab = WidgetIntents.TAB_TODAY
            )
            val openQuickNoteIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1402,
                tab = WidgetIntents.TAB_TODAY,
                openAction = WidgetIntents.ACTION_OPEN_QUICK_NOTE
            )
            val openAddTaskIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1403,
                tab = WidgetIntents.TAB_TODAY,
                openAction = WidgetIntents.ACTION_OPEN_ADD_ITEM
            )
            val openCalendarIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1404,
                tab = WidgetIntents.TAB_CALENDAR
            )
            val openClassesIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1405,
                tab = WidgetIntents.TAB_CLASSES
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_quick_actions).apply {
                    setTextViewText(R.id.widget_quick_streak_badge, streakText)
                    setImageViewBitmap(R.id.widget_quick_habit_image, habitBitmap)
                    setTextViewText(R.id.widget_quick_latest_note, latestNoteText)

                    setOnClickPendingIntent(android.R.id.background, openTodayIntent)
                    setOnClickPendingIntent(R.id.widget_quick_header, openTodayIntent)
                    setOnClickPendingIntent(R.id.widget_quick_habit_card, openTodayIntent)
                    setOnClickPendingIntent(R.id.widget_quick_btn_note, openQuickNoteIntent)
                    setOnClickPendingIntent(R.id.widget_quick_btn_add, openAddTaskIntent)
                    setOnClickPendingIntent(R.id.widget_quick_btn_calendar, openCalendarIntent)
                    setOnClickPendingIntent(R.id.widget_quick_btn_classes, openClassesIntent)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
