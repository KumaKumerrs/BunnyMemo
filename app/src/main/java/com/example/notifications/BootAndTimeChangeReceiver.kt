package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.focus.DoomscrollGuardManager
import com.example.focus.FocusSessionManager
import com.example.widgets.WidgetUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores all local ReminderScheduler alarms (item reminders, class alarms, daily digest),
 * FocusSessionManager state, DoomscrollGuardService (if enabled), and home screen widgets
 * after device reboot, app update, manual clock change, or timezone change.
 */
class BootAndTimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler(appContext).rescheduleAllFromDatabase()
                FocusSessionManager.initialize(appContext)
                DoomscrollGuardManager.initialize(appContext)
                DoomscrollGuardManager.syncServiceState(appContext)
                WidgetUpdateManager.refreshAllWidgetsSuspend(appContext)
            } catch (err: Exception) {
                Log.w(TAG, "Failed restoring alarms/services on $action: ${err.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootTimeReceiver"
    }
}
