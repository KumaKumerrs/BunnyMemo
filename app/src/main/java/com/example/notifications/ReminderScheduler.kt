package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerLogic
import com.example.data.PlannerReminder
import com.example.data.PlannerStep
import com.example.data.ReminderKind
import java.util.Calendar
import java.util.TimeZone

data class DailyReminderConfig(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0
)

data class DailyDigestSummary(
    val title: String,
    val body: String,
    val todayTaskCount: Int,
    val upcomingMilestoneCount: Int,
    val highlightItemId: String?
)

/**
 * Schedules and cancels local Android reminder alarms deduplicated by stable occurrenceKey,
 * plus a gentle daily localized "forget-me-not" digest highlighting today's tasks and
 * upcoming milestones. Works alongside the in-app Notifications inbox.
 */
class ReminderScheduler(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scheduledKeys = mutableMapOf<String, Int>()
    private val scheduledClassKeys = mutableMapOf<String, Int>()

    init {
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val remindersChannel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notif_channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.notif_channel_reminders_desc)
            }

            val dailyChannel = NotificationChannel(
                DAILY_DIGEST_CHANNEL_ID,
                appContext.getString(R.string.notif_channel_daily_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.notif_channel_daily_desc)
            }

            manager.createNotificationChannel(remindersChannel)
            manager.createNotificationChannel(dailyChannel)
        }
    }

    fun loadDailyConfig(): DailyReminderConfig {
        return DailyReminderConfig(
            enabled = prefs.getBoolean(KEY_DAILY_ENABLED, true),
            hour = prefs.getInt(KEY_DAILY_HOUR, 9).coerceIn(0, 23),
            minute = prefs.getInt(KEY_DAILY_MINUTE, 0).coerceIn(0, 59)
        )
    }

    fun saveDailyConfig(config: DailyReminderConfig) {
        prefs.edit()
            .putBoolean(KEY_DAILY_ENABLED, config.enabled)
            .putInt(KEY_DAILY_HOUR, config.hour.coerceIn(0, 23))
            .putInt(KEY_DAILY_MINUTE, config.minute.coerceIn(0, 59))
            .apply()
    }

    /**
     * Builds a localized, gentle forget-me-not summary of today's open tasks/events
     * and upcoming incomplete milestones (within the next 3 days).
     */
    fun buildDailyDigestSummary(
        items: List<PlannerItem>,
        steps: List<PlannerStep>,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyDigestSummary {
        val today = CalendarDate.today(nowMillis)
        val horizon = today.plusDays(3)

        val todayOpenItems = items.filter { item ->
            val isDone = when (item.itemType()) {
                ItemType.PLAN -> PlannerLogic.computeProgress(item, steps, items, nowMillis).allStepsDone
                ItemType.TASK, ItemType.EVENT -> item.completed
            }
            !isDone && item.appearsOnDate(today) && item.itemType() != ItemType.PLAN
        }

        val itemMap = items.associateBy { it.id }

        // Upcoming incomplete milestones (steps due today..+3d OR active plans due today..+3d)
        val upcomingStepMilestones = steps.filter { step ->
            if (step.completed) return@filter false
            val parent = itemMap[step.itemId] ?: return@filter false
            val due = CalendarDate.parse(step.dueDate) ?: parent.primaryCalendarDate() ?: return@filter false
            due >= today && due <= horizon
        }
        val upcomingPlansWithoutStepDates = items.filter { item ->
            if (item.itemType() != ItemType.PLAN) return@filter false
            val prog = PlannerLogic.computeProgress(item, steps, items, nowMillis)
            if (prog.allStepsDone) return@filter false
            val due = CalendarDate.parse(item.dueDate) ?: return@filter false
            due >= today && due <= horizon
        }

        val taskCount = todayOpenItems.size
        val milestoneCount = upcomingStepMilestones.size +
            upcomingPlansWithoutStepDates.count { plan ->
                upcomingStepMilestones.none { it.itemId == plan.id }
            }

        val firstTaskTitle = todayOpenItems.firstOrNull()?.title
        val firstMilestoneTitle = upcomingStepMilestones.firstOrNull()?.title
            ?: upcomingPlansWithoutStepDates.firstOrNull()?.title
        val highlightItemId = todayOpenItems.firstOrNull()?.id
            ?: upcomingStepMilestones.firstOrNull()?.itemId
            ?: upcomingPlansWithoutStepDates.firstOrNull()?.id

        val (title, body) = when {
            taskCount > 0 && milestoneCount > 0 -> {
                val lead = firstTaskTitle ?: firstMilestoneTitle ?: appContext.getString(R.string.app_name)
                appContext.getString(R.string.notif_daily_title_both) to
                    appContext.getString(R.string.notif_daily_body_both, taskCount, milestoneCount, lead)
            }
            taskCount > 0 -> {
                val lead = firstTaskTitle ?: appContext.getString(R.string.app_name)
                appContext.getString(R.string.notif_daily_title_tasks) to
                    appContext.getString(R.string.notif_daily_body_tasks, taskCount, lead)
            }
            milestoneCount > 0 -> {
                val lead = firstMilestoneTitle ?: appContext.getString(R.string.app_name)
                appContext.getString(R.string.notif_daily_title_milestones) to
                    appContext.getString(R.string.notif_daily_body_milestones, milestoneCount, lead)
            }
            else -> {
                appContext.getString(R.string.notif_daily_title_quiet) to
                    appContext.getString(R.string.notif_daily_body_quiet)
            }
        }

        return DailyDigestSummary(
            title = title,
            body = body,
            todayTaskCount = taskCount,
            upcomingMilestoneCount = milestoneCount,
            highlightItemId = highlightItemId
        )
    }

    /**
     * Caches the freshest localized forget-me-not digest summary and schedules or cancels
     * the next daily alarm using Play-compliant inexact-idle delivery.
     */
    fun syncDailyDigest(
        config: DailyReminderConfig,
        summary: DailyDigestSummary,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        saveDailyConfig(config)
        prefs.edit()
            .putString(KEY_CACHED_TITLE, summary.title)
            .putString(KEY_CACHED_BODY, summary.body)
            .putString(KEY_CACHED_ITEM_ID, summary.highlightItemId)
            .apply()

        if (config.enabled) {
            scheduleNextDailyAlarm(
                hour = config.hour,
                minute = config.minute,
                title = summary.title,
                body = summary.body,
                itemId = summary.highlightItemId,
                nowMillis = nowMillis,
                forceTomorrow = false
            )
        } else {
            cancelDailyDigest()
        }
    }

    fun scheduleNextDailyAlarm(
        hour: Int,
        minute: Int,
        title: String,
        body: String,
        itemId: String?,
        nowMillis: Long = System.currentTimeMillis(),
        forceTomorrow: Boolean = false
    ) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerCal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceTomorrow || timeInMillis <= nowMillis + 5_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_DAILY_DIGEST
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, body)
            putExtra(EXTRA_CHANNEL_ID, DAILY_DIGEST_CHANNEL_ID)
            putExtra(EXTRA_NOTIFICATION_ID, DAILY_DIGEST_NOTIFICATION_ID)
            if (!itemId.isNullOrBlank()) {
                putExtra(EXTRA_ITEM_ID, itemId)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            DAILY_DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerCal.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pendingIntent)
        }
    }

    fun rescheduleTomorrowFromStoredPrefs() {
        val config = loadDailyConfig()
        if (!config.enabled) return
        val title = prefs.getString(KEY_CACHED_TITLE, null)
            ?: appContext.getString(R.string.notif_daily_title_quiet)
        val body = prefs.getString(KEY_CACHED_BODY, null)
            ?: appContext.getString(R.string.notif_daily_body_quiet)
        val itemId = prefs.getString(KEY_CACHED_ITEM_ID, null)
        scheduleNextDailyAlarm(
            hour = config.hour,
            minute = config.minute,
            title = title,
            body = body,
            itemId = itemId,
            forceTomorrow = true
        )
    }

    fun cancelDailyDigest() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_DAILY_DIGEST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            DAILY_DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Immediately sends a localized forget-me-not themed preview notification so the user
     * can test their daily reminder tone right away. Returns true if posted.
     */
    fun postDailyDigestPreview(summary: DailyDigestSummary): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        ensureNotificationChannel()

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            summary.highlightItemId?.let { putExtra(EXTRA_ITEM_ID, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            DAILY_DIGEST_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, DAILY_DIGEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(FORGET_ME_NOT_ACCENT_COLOR)
            .setSubText(appContext.getString(R.string.notif_subtext_theme))
            .setContentTitle(summary.title)
            .setContentText(summary.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        manager.notify(DAILY_DIGEST_NOTIFICATION_ID, notification)
        return true
    }

    /**
     * Reconciles local AlarmManager alerts with the current Firestore snapshot.
     * - Cancels alerts for deleted, dismissed, or completed progress check-ins.
     * - Schedules/updates future occurrences using stable occurrenceKey deduplication.
     */
    fun syncReminders(
        reminders: List<PlannerReminder>,
        items: List<PlannerItem>,
        steps: List<PlannerStep>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val itemMap = items.associateBy { it.id }
        val activeKeys = mutableSetOf<String>()

        reminders.filter { !it.dismissed }.forEach { reminder ->
            val item = itemMap[reminder.itemId] ?: return@forEach
            val triggerAt = reminder.effectiveTriggerMillis()
            // Skip past reminders (already visible in the in-app inbox)
            if (triggerAt <= nowMillis) return@forEach

            // Recheck completion state; skip progress check-ins if all steps are already done
            val messagePair = PlannerLogic.buildReminderMessage(
                reminder = reminder,
                item = item,
                allSteps = steps,
                allItems = items,
                nowMillis = triggerAt
            ) ?: return@forEach

            val stableKey = "${reminder.occurrenceKey}_${triggerAt}"
            val requestCode = stableKey.hashCode()
            activeKeys.add(stableKey)
            scheduledKeys[stableKey] = requestCode

            val title = if (reminder.reminderKind() == ReminderKind.PROGRESS_CHECKIN) {
                appContext.getString(R.string.notif_checkin_title_prefix, item.title)
            } else {
                appContext.getString(R.string.notif_item_title_prefix, item.title)
            }

            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_ITEM_REMINDER
                putExtra(EXTRA_ITEM_ID, item.id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, messagePair.first)
                putExtra(EXTRA_CHANNEL_ID, CHANNEL_ID)
                putExtra(EXTRA_NOTIFICATION_ID, requestCode)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        // Cancel any previously scheduled alerts that were deleted, rescheduled, or completed
        val staleKeys = scheduledKeys.keys - activeKeys
        staleKeys.forEach { key ->
            val requestCode = scheduledKeys.remove(key) ?: return@forEach
            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_ITEM_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /**
     * Reconciles upcoming local notifications for repeating semester classes that have
     * notifications enabled, honoring each class's custom lead time (notifyOffsetMinutes).
     */
    fun syncClassReminders(
        classSchedules: List<ClassScheduleItem>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val activeKeys = mutableSetOf<String>()

        classSchedules.filter { it.notifyEnabled }.forEach { cls ->
            val nextPair = cls.nextNotificationOccurrence(nowMillis) ?: return@forEach
            val (triggerAt, _) = nextPair
            val stableKey = "class_${cls.id}_${triggerAt}_${cls.notifyOffsetMinutes}"
            val requestCode = stableKey.hashCode()
            activeKeys.add(stableKey)
            scheduledClassKeys[stableKey] = requestCode

            val startText = PlannerDateUtils.formatTimeOfDay(cls.startTimeMinutes)
            val title = appContext.getString(R.string.notif_class_title_prefix, cls.subject)
            val body = if (!cls.room.isNullOrBlank()) {
                appContext.getString(R.string.notif_class_body_with_room, cls.subject, startText, cls.room)
            } else {
                appContext.getString(R.string.notif_class_body_plain, cls.subject, startText)
            }

            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_CLASS_REMINDER
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, body)
                putExtra(EXTRA_CHANNEL_ID, CHANNEL_ID)
                putExtra(EXTRA_NOTIFICATION_ID, requestCode)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        val staleKeys = scheduledClassKeys.keys - activeKeys
        staleKeys.forEach { key ->
            val requestCode = scheduledClassKeys.remove(key) ?: return@forEach
            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_CLASS_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun cancelAll() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        scheduledKeys.values.forEach { requestCode ->
            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_ITEM_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        scheduledKeys.clear()
        scheduledClassKeys.values.forEach { requestCode ->
            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_CLASS_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        scheduledClassKeys.clear()
        cancelDailyDigest()
    }

    companion object {
        const val CHANNEL_ID = "forget_me_not_reminders"
        const val DAILY_DIGEST_CHANNEL_ID = "forget_me_not_daily_digest"
        const val ACTION_ITEM_REMINDER = "com.example.notifications.ACTION_ITEM_REMINDER"
        const val ACTION_DAILY_DIGEST = "com.example.notifications.ACTION_DAILY_DIGEST"
        const val ACTION_CLASS_REMINDER = "com.example.notifications.ACTION_CLASS_REMINDER"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val FORGET_ME_NOT_ACCENT_COLOR = 0xFF86B5F4.toInt()

        private const val DAILY_DIGEST_REQUEST_CODE = 70241
        private const val DAILY_DIGEST_NOTIFICATION_ID = 70242
        private const val PREFS_NAME = "bunny_memo_daily_reminders"
        private const val KEY_DAILY_ENABLED = "daily_digest_enabled"
        private const val KEY_DAILY_HOUR = "daily_digest_hour"
        private const val KEY_DAILY_MINUTE = "daily_digest_minute"
        private const val KEY_CACHED_TITLE = "daily_cached_title"
        private const val KEY_CACHED_BODY = "daily_cached_body"
        private const val KEY_CACHED_ITEM_ID = "daily_cached_item_id"
    }
}
