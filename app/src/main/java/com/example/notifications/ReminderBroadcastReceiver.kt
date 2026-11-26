package com.example.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.widgets.WidgetIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action
        val isDailyDigest = action == ReminderScheduler.ACTION_DAILY_DIGEST
        val isClassReminder = action == ReminderScheduler.ACTION_CLASS_REMINDER

        val scheduler = ReminderScheduler(appContext)
        // Always ensure channels exist even on cold-start process wakeups
        scheduler.ensureNotificationChannel()

        if (isDailyDigest || isClassReminder) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    if (isDailyDigest) {
                        val freshSummary = runCatching {
                            scheduler.buildFreshDailyDigestFromDatabase()
                        }.getOrNull()
                        scheduler.rescheduleTomorrowFromStoredPrefs(freshSummary)
                        if (hasNotificationPermission(appContext)) {
                            val title = freshSummary?.title
                                ?: intent.getStringExtra(ReminderScheduler.EXTRA_TITLE)
                                ?: appContext.getString(R.string.app_name)
                            val message = freshSummary?.body
                                ?: intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE)
                                ?: appContext.getString(R.string.notif_default_body)
                            val itemId = freshSummary?.highlightItemId
                                ?: intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID)
                            val notificationId = intent.getIntExtra(
                                ReminderScheduler.EXTRA_NOTIFICATION_ID,
                                70242
                            )
                            postNotification(
                                context = appContext,
                                channelId = ReminderScheduler.DAILY_DIGEST_CHANNEL_ID,
                                title = title,
                                message = message,
                                notificationId = notificationId,
                                itemId = itemId,
                                openTab = null
                            )
                        }
                    } else {
                        // Class reminder: post notification AND immediately schedule the next recurring session
                        if (hasNotificationPermission(appContext)) {
                            val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE)
                                ?: appContext.getString(R.string.app_name)
                            val message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE)
                                ?: appContext.getString(R.string.notif_default_body)
                            val notificationId = intent.getIntExtra(
                                ReminderScheduler.EXTRA_NOTIFICATION_ID,
                                ReminderScheduler.stableRequestCode(500_000, 400_000, title)
                            )
                            postNotification(
                                context = appContext,
                                channelId = ReminderScheduler.CHANNEL_ID,
                                title = title,
                                message = message,
                                notificationId = notificationId,
                                itemId = null,
                                openTab = WidgetIntents.TAB_CLASSES
                            )
                        }
                        runCatching { scheduler.rescheduleNextClassRemindersFromDatabase() }
                    }
                } catch (err: Exception) {
                    Log.w(TAG, "Error handling broadcast $action: ${err.message}")
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (!hasNotificationPermission(appContext)) return

        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID)
        if (itemId.isNullOrBlank()) return

        val channelId = intent.getStringExtra(ReminderScheduler.EXTRA_CHANNEL_ID)
            ?: ReminderScheduler.CHANNEL_ID
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE)
            ?: appContext.getString(R.string.app_name)
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE)
            ?: appContext.getString(R.string.notif_default_body)
        val notificationId = intent.getIntExtra(
            ReminderScheduler.EXTRA_NOTIFICATION_ID,
            ReminderScheduler.stableRequestCode(100_000, 400_000, itemId)
        )

        postNotification(
            context = appContext,
            channelId = channelId,
            title = title,
            message = message,
            notificationId = notificationId,
            itemId = itemId,
            openTab = null
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int,
        itemId: String?,
        openTab: String?
    ) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!itemId.isNullOrBlank()) {
                putExtra(ReminderScheduler.EXTRA_ITEM_ID, itemId)
            }
            if (!openTab.isNullOrBlank()) {
                putExtra(WidgetIntents.EXTRA_OPEN_TAB, openTab)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_flower_mono)
            .setColor(ReminderScheduler.FORGET_ME_NOT_ACCENT_COLOR)
            .setSubText(context.getString(R.string.notif_subtext_theme))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "ReminderReceiver"
    }
}
