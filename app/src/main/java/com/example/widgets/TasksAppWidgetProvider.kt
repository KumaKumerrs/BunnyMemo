package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.CalendarDate
import com.example.data.LocalPlannerRepository
import com.example.data.PlannerDateUtils
import com.example.data.RoomFirestoreSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TasksAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetIntents.ACTION_TOGGLE_TASK) {
            val taskId = intent.getStringExtra(WidgetIntents.EXTRA_TASK_ID) ?: return
            val newCompleted = intent.getBooleanExtra(WidgetIntents.EXTRA_TASK_COMPLETED, true)
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val dataSource = RoomFirestoreSyncService
                        .getInstance(context.applicationContext)
                        .createOfflineFirstDataSource()
                    dataSource.toggleItemCompleted(
                        userId = LocalPlannerRepository.LOCAL_USER_ID,
                        itemId = taskId,
                        completed = newCompleted
                    )
                    WidgetUpdateManager.refreshAllWidgets(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

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
        private val ROW_IDS = intArrayOf(
            R.id.widget_task_row_0,
            R.id.widget_task_row_1,
            R.id.widget_task_row_2,
            R.id.widget_task_row_3,
            R.id.widget_task_row_4
        )
        private val CHECK_IDS = intArrayOf(
            R.id.widget_task_check_0,
            R.id.widget_task_check_1,
            R.id.widget_task_check_2,
            R.id.widget_task_check_3,
            R.id.widget_task_check_4
        )
        private val BODY_IDS = intArrayOf(
            R.id.widget_task_body_0,
            R.id.widget_task_body_1,
            R.id.widget_task_body_2,
            R.id.widget_task_body_3,
            R.id.widget_task_body_4
        )
        private val TITLE_IDS = intArrayOf(
            R.id.widget_task_title_0,
            R.id.widget_task_title_1,
            R.id.widget_task_title_2,
            R.id.widget_task_title_3,
            R.id.widget_task_title_4
        )
        private val META_IDS = intArrayOf(
            R.id.widget_task_meta_0,
            R.id.widget_task_meta_1,
            R.id.widget_task_meta_2,
            R.id.widget_task_meta_3,
            R.id.widget_task_meta_4
        )

        private val COLOR_ACTIVE_TEXT = Color.parseColor("#2B4360")
        private val COLOR_DONE_TEXT = Color.parseColor("#6B85A3")
        private val COLOR_OVERDUE_TEXT = Color.parseColor("#D65A6A")

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val today = snapshot.today
            val tasks = snapshot.todayActionableTasks

            val openTodayIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1301,
                tab = WidgetIntents.TAB_TODAY
            )
            val addTaskIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1302,
                tab = WidgetIntents.TAB_TODAY,
                openAction = WidgetIntents.ACTION_OPEN_ADD_ITEM
            )
            val quickNoteIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1303,
                tab = WidgetIntents.TAB_TODAY,
                openAction = WidgetIntents.ACTION_OPEN_QUICK_NOTE
            )

            val doneCount = tasks.count { it.completed }
            val totalCount = tasks.size
            val badgeText = if (totalCount == 0) "Clear" else "$doneCount/$totalCount"
            val subtitleText = PlannerDateUtils.formatFullDateHeader(today)

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_tasks).apply {
                    setTextViewText(R.id.widget_tasks_title, "Today's Tasks")
                    setTextViewText(R.id.widget_tasks_subtitle, subtitleText)
                    setTextViewText(R.id.widget_tasks_badge, badgeText)

                    setOnClickPendingIntent(android.R.id.background, openTodayIntent)
                    setOnClickPendingIntent(R.id.widget_tasks_header, openTodayIntent)
                    setOnClickPendingIntent(R.id.widget_tasks_add_btn, addTaskIntent)
                    setOnClickPendingIntent(R.id.widget_tasks_quick_note_btn, quickNoteIntent)

                    if (tasks.isEmpty()) {
                        setViewVisibility(R.id.widget_tasks_list_container, View.GONE)
                        setViewVisibility(R.id.widget_tasks_more, View.GONE)
                        setViewVisibility(R.id.widget_tasks_empty_card, View.VISIBLE)
                        setOnClickPendingIntent(R.id.widget_tasks_empty_card, addTaskIntent)
                    } else {
                        setViewVisibility(R.id.widget_tasks_empty_card, View.GONE)
                        setViewVisibility(R.id.widget_tasks_list_container, View.VISIBLE)

                        val visible = tasks.take(ROW_IDS.size)
                        for (i in ROW_IDS.indices) {
                            if (i < visible.size) {
                                val task = visible[i]
                                val due = CalendarDate.parse(task.dueDate)
                                val isOverdue = !task.completed && due != null && due < today
                                val isQuickNote = task.dueDate.isNullOrBlank() && task.parentPlanId.isNullOrBlank()

                                setViewVisibility(ROW_IDS[i], View.VISIBLE)
                                setImageViewResource(
                                    CHECK_IDS[i],
                                    if (task.completed) R.drawable.ic_widget_check_checked
                                    else R.drawable.ic_widget_check_unchecked
                                )
                                setTextViewText(TITLE_IDS[i], task.title)
                                setTextColor(
                                    TITLE_IDS[i],
                                    if (task.completed) COLOR_DONE_TEXT else COLOR_ACTIVE_TEXT
                                )
                                setInt(
                                    TITLE_IDS[i],
                                    "setPaintFlags",
                                    if (task.completed) {
                                        Paint.ANTI_ALIAS_FLAG or Paint.STRIKE_THRU_TEXT_FLAG
                                    } else {
                                        Paint.ANTI_ALIAS_FLAG
                                    }
                                )

                                val metaText = when {
                                    isQuickNote -> "Quick Note"
                                    isOverdue -> "Overdue · ${PlannerDateUtils.formatCompactDate(due!!)}"
                                    task.dueTimeMinutes != null -> "Today · ${PlannerDateUtils.formatTimeOfDay(task.dueTimeMinutes)}"
                                    else -> "Due today"
                                }
                                setTextViewText(META_IDS[i], metaText)
                                setTextColor(
                                    META_IDS[i],
                                    if (isOverdue) COLOR_OVERDUE_TEXT else COLOR_DONE_TEXT
                                )

                                val toggleIntent = WidgetIntents.toggleTaskBroadcastPendingIntent(
                                    context = context,
                                    requestCode = 2000 + widgetId * 10 + i,
                                    taskId = task.id,
                                    newCompleted = !task.completed
                                )
                                val openItemIntent = WidgetIntents.openMainActivityPendingIntent(
                                    context = context,
                                    requestCode = 3000 + widgetId * 10 + i,
                                    tab = WidgetIntents.TAB_TODAY,
                                    itemId = task.id
                                )
                                setOnClickPendingIntent(CHECK_IDS[i], toggleIntent)
                                setOnClickPendingIntent(BODY_IDS[i], openItemIntent)
                            } else {
                                setViewVisibility(ROW_IDS[i], View.GONE)
                            }
                        }

                        val overflow = tasks.size - visible.size
                        if (overflow > 0) {
                            setViewVisibility(R.id.widget_tasks_more, View.VISIBLE)
                            setTextViewText(R.id.widget_tasks_more, "+$overflow more · Open planner")
                            setOnClickPendingIntent(R.id.widget_tasks_more, openTodayIntent)
                        } else {
                            setViewVisibility(R.id.widget_tasks_more, View.GONE)
                        }
                    }
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
