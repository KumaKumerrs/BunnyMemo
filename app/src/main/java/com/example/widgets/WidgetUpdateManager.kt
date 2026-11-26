package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlannerWidgetKind(val providerClass: Class<*>) {
    INTERACTIVE_BUNNY(InteractiveBunnyAppWidgetProvider::class.java),
    WEEKLY_CALENDAR(WeeklyCalendarAppWidgetProvider::class.java),
    CALENDAR(CalendarAppWidgetProvider::class.java),
    CLASS_SCHEDULE(ClassScheduleAppWidgetProvider::class.java),
    TASKS(TasksAppWidgetProvider::class.java),
    QUICK_ACTIONS(QuickActionsAppWidgetProvider::class.java)
}

/**
 * Coordinates automatic refresh and one-tap launcher pinning for all Bunny Memo home screen widgets.
 */
object WidgetUpdateManager {
    private const val TAG = "WidgetUpdateMgr"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAllWidgets(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            refreshAllWidgetsSuspend(appContext)
        }
    }

    /**
     * Awaitable refresh used inside BroadcastReceiver `goAsync()` blocks so Android never
     * terminates the receiver before widget updates finish pushing to the launcher.
     */
    suspend fun refreshAllWidgetsSuspend(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            val manager = AppWidgetManager.getInstance(appContext)
            val bunnyIds = manager.getAppWidgetIds(ComponentName(appContext, InteractiveBunnyAppWidgetProvider::class.java))
            val weekIds = manager.getAppWidgetIds(ComponentName(appContext, WeeklyCalendarAppWidgetProvider::class.java))
            val calIds = manager.getAppWidgetIds(ComponentName(appContext, CalendarAppWidgetProvider::class.java))
            val clsIds = manager.getAppWidgetIds(ComponentName(appContext, ClassScheduleAppWidgetProvider::class.java))
            val taskIds = manager.getAppWidgetIds(ComponentName(appContext, TasksAppWidgetProvider::class.java))
            val quickIds = manager.getAppWidgetIds(ComponentName(appContext, QuickActionsAppWidgetProvider::class.java))

            if (bunnyIds.isEmpty() && weekIds.isEmpty() && calIds.isEmpty() && clsIds.isEmpty() && taskIds.isEmpty() && quickIds.isEmpty()) {
                return@withContext
            }

            val snapshot = WidgetDataLoader.load(appContext)
            if (bunnyIds.isNotEmpty()) {
                InteractiveBunnyAppWidgetProvider.updateWidgets(appContext, manager, bunnyIds, snapshot)
            }
            if (weekIds.isNotEmpty()) {
                WeeklyCalendarAppWidgetProvider.updateWidgets(appContext, manager, weekIds, snapshot)
            }
            if (calIds.isNotEmpty()) {
                CalendarAppWidgetProvider.updateWidgets(appContext, manager, calIds, snapshot)
            }
            if (clsIds.isNotEmpty()) {
                ClassScheduleAppWidgetProvider.updateWidgets(appContext, manager, clsIds, snapshot)
            }
            if (taskIds.isNotEmpty()) {
                TasksAppWidgetProvider.updateWidgets(appContext, manager, taskIds, snapshot)
            }
            if (quickIds.isNotEmpty()) {
                QuickActionsAppWidgetProvider.updateWidgets(appContext, manager, quickIds, snapshot)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh skipped: ${e.message}")
        }
    }

    /**
     * Requests the launcher to pin the selected widget directly to the user's home screen (API 26+).
     * Returns true if the pin prompt was dispatched to the launcher.
     */
    fun requestPinWidget(context: Context, kind: PlannerWidgetKind): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, kind.providerClass)
            manager.requestPinAppWidget(provider, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to request pin widget ${kind.name}: ${e.message}")
            false
        }
    }
}
