package com.example.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.notifications.ReminderScheduler

object WidgetIntents {
    const val EXTRA_OPEN_TAB = "com.example.widgets.EXTRA_OPEN_TAB"
    const val EXTRA_OPEN_ACTION = "com.example.widgets.EXTRA_OPEN_ACTION"
    const val EXTRA_TASK_ID = "com.example.widgets.EXTRA_TASK_ID"
    const val EXTRA_TASK_COMPLETED = "com.example.widgets.EXTRA_TASK_COMPLETED"

    const val TAB_TODAY = "TODAY"
    const val TAB_CALENDAR = "CALENDAR"
    const val TAB_CLASSES = "CLASSES"
    const val TAB_NOTIFICATIONS = "NOTIFICATIONS"
    const val TAB_REPORTS = "REPORTS"

    const val ACTION_OPEN_QUICK_NOTE = "QUICK_NOTE"
    const val ACTION_OPEN_ADD_ITEM = "ADD_ITEM"
    const val ACTION_OPEN_ADD_CLASS = "ADD_CLASS"

    const val ACTION_TOGGLE_TASK = "com.example.widgets.ACTION_TOGGLE_TASK"

    fun openMainActivityPendingIntent(
        context: Context,
        requestCode: Int,
        tab: String? = null,
        openAction: String? = null,
        itemId: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!tab.isNullOrBlank()) putExtra(EXTRA_OPEN_TAB, tab)
            if (!openAction.isNullOrBlank()) putExtra(EXTRA_OPEN_ACTION, openAction)
            if (!itemId.isNullOrBlank()) putExtra(ReminderScheduler.EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun toggleTaskBroadcastPendingIntent(
        context: Context,
        requestCode: Int,
        taskId: String,
        newCompleted: Boolean
    ): PendingIntent {
        // Explicit intent to internal non-exported WidgetActionReceiver
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_COMPLETED, newCompleted)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun bunnyActionBroadcastPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
