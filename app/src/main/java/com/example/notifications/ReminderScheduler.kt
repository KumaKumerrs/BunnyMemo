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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.ItemType
import com.example.data.LocalPlannerDatabase
import com.example.data.LocalPlannerRepository
import com.example.data.PhotoAttachmentStore
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerLogic
import com.example.data.PlannerReminder
import com.example.data.PlannerStep
import com.example.data.ReminderKind
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
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
 * persisted across process restarts so stale alarms and sign-out cancellations are reliable.
 * Also manages the daily localized "forget-me-not" digest and Focus Session completion alerts.
 */
class ReminderScheduler(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

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

            val focusChannel = NotificationChannel(
                FOCUS_CHANNEL_ID,
                appContext.getString(R.string.focus_session_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.focus_session_subtitle)
            }

            manager.createNotificationChannel(remindersChannel)
            manager.createNotificationChannel(dailyChannel)
            manager.createNotificationChannel(focusChannel)
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
     * the next daily alarm.
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

        scheduleAlarmCompat(alarmManager, triggerCal.timeInMillis, pendingIntent)
    }

    fun rescheduleTomorrowFromStoredPrefs(freshSummary: DailyDigestSummary? = null) {
        val config = loadDailyConfig()
        if (!config.enabled) return
        val title = freshSummary?.title
            ?: prefs.getString(KEY_CACHED_TITLE, null)
            ?: appContext.getString(R.string.notif_daily_title_quiet)
        val body = freshSummary?.body
            ?: prefs.getString(KEY_CACHED_BODY, null)
            ?: appContext.getString(R.string.notif_daily_body_quiet)
        val itemId = freshSummary?.highlightItemId ?: prefs.getString(KEY_CACHED_ITEM_ID, null)
        if (freshSummary != null) {
            prefs.edit()
                .putString(KEY_CACHED_TITLE, title)
                .putString(KEY_CACHED_BODY, body)
                .putString(KEY_CACHED_ITEM_ID, itemId)
                .apply()
        }
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
        if (!hasNotificationPermission()) return false

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
            .setSmallIcon(R.drawable.ic_notif_flower_mono)
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
     * Posts a completion notification when a Pomodoro focus or break timer finishes in the background.
     */
    fun postFocusCompletedNotification(isFocusCompleted: Boolean, linkedTaskTitle: String?, earnedMinutes: Int) {
        if (!hasNotificationPermission()) return
        ensureNotificationChannel()

        val title = if (isFocusCompleted) {
            "Focus session complete!"
        } else {
            "Break time finished"
        }
        val body = if (isFocusCompleted) {
            if (!linkedTaskTitle.isNullOrBlank()) {
                "You focused for ${earnedMinutes}m on \"$linkedTaskTitle\". Time for a gentle break!"
            } else {
                "Great focus! You earned ${earnedMinutes}m of deep focus. Take a soft stretch."
            }
        } else {
            "Your break is over — your bunny companion is ready for the next focus round."
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            FOCUS_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, FOCUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_flower_mono)
            .setColor(FORGET_ME_NOT_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(FOCUS_NOTIFICATION_ID, notification)
    }

    /**
     * Reconciles local AlarmManager alerts with the current local Room snapshot.
     * Persists scheduled request codes to SharedPreferences so cancellations survive process restarts.
     */
    fun syncReminders(
        reminders: List<PlannerReminder>,
        items: List<PlannerItem>,
        steps: List<PlannerStep>,
        nowMillis: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val itemMap = items.associateBy { it.id }
        val previouslyScheduled = loadPersistedCodeMap(KEY_SCHEDULED_ITEM_CODES)
        val nextScheduled = mutableMapOf<String, Int>()

        reminders.filter { !it.dismissed }.forEach { reminder ->
            val item = itemMap[reminder.itemId] ?: return@forEach
            val triggerAt = reminder.effectiveTriggerMillis()
            if (triggerAt <= nowMillis) return@forEach

            val messagePair = PlannerLogic.buildReminderMessage(
                reminder = reminder,
                item = item,
                allSteps = steps,
                allItems = items,
                nowMillis = triggerAt
            ) ?: return@forEach

            val stableKey = "${reminder.occurrenceKey}_${triggerAt}"
            val requestCode = stableRequestCode(ITEM_CODE_BASE, ITEM_CODE_SPAN, stableKey)
            nextScheduled[stableKey] = requestCode

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

            scheduleAlarmCompat(alarmManager, triggerAt, pendingIntent)
        }

        val activeCodes = nextScheduled.values.toSet()
        previouslyScheduled.forEach { (key, requestCode) ->
            if (key !in nextScheduled && requestCode !in activeCodes) {
                cancelBroadcastAlarm(alarmManager, ACTION_ITEM_REMINDER, requestCode)
            }
        }
        savePersistedCodeMap(KEY_SCHEDULED_ITEM_CODES, nextScheduled)
    }

    /**
     * Reconciles upcoming local notifications for repeating semester classes that have
     * notifications enabled, honoring each class's custom lead time (notifyOffsetMinutes).
     */
    fun syncClassReminders(
        classSchedules: List<ClassScheduleItem>,
        nowMillis: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val previouslyScheduled = loadPersistedCodeMap(KEY_SCHEDULED_CLASS_CODES)
        val nextScheduled = mutableMapOf<String, Int>()

        classSchedules.filter { it.notifyEnabled }.forEach { cls ->
            val nextPair = cls.nextNotificationOccurrence(nowMillis) ?: return@forEach
            val (triggerAt, _) = nextPair
            val stableKey = "class_${cls.id}_${triggerAt}_${cls.notifyOffsetMinutes}"
            val requestCode = stableRequestCode(CLASS_CODE_BASE, CLASS_CODE_SPAN, stableKey)
            nextScheduled[stableKey] = requestCode

            val startText = PlannerDateUtils.formatTimeOfDay(cls.startTimeMinutes)
            val title = appContext.getString(R.string.notif_class_title_prefix, cls.subject)
            val body = if (!cls.room.isNullOrBlank()) {
                appContext.getString(R.string.notif_class_body_with_room, cls.subject, startText, cls.room)
            } else {
                appContext.getString(R.string.notif_class_body_plain, cls.subject, startText)
            }

            val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_CLASS_REMINDER
                putExtra(EXTRA_CLASS_ID, cls.id)
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

            scheduleAlarmCompat(alarmManager, triggerAt, pendingIntent)
        }

        val activeCodes = nextScheduled.values.toSet()
        previouslyScheduled.forEach { (key, requestCode) ->
            if (key !in nextScheduled && requestCode !in activeCodes) {
                cancelBroadcastAlarm(alarmManager, ACTION_CLASS_REMINDER, requestCode)
            }
        }
        savePersistedCodeMap(KEY_SCHEDULED_CLASS_CODES, nextScheduled)
    }

    /**
     * Queries the local Room database directly to recompute & reschedule all item reminders,
     * class alarms, and the daily digest (used on boot, time/timezone change, and app update).
     */
    suspend fun rescheduleAllFromDatabase(nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val dao = LocalPlannerDatabase.getInstance(appContext).plannerDao()
        val items = dao.getAllItems().map { e ->
            PlannerItem(
                id = e.id,
                userId = LocalPlannerRepository.LOCAL_USER_ID,
                type = e.type,
                title = e.title,
                notes = e.notes,
                completed = e.completed,
                startDate = e.startDate,
                dueDate = e.dueDate,
                dueTimeMinutes = e.dueTimeMinutes,
                startAtUtcMillis = e.startAtUtcMillis,
                endAtUtcMillis = e.endAtUtcMillis,
                parentPlanId = e.parentPlanId,
                priority = TaskPriority.fromRaw(e.priority).name,
                category = TaskCategory.fromRaw(e.category).name,
                imagePaths = PhotoAttachmentStore.parsePaths(e.imagePathsCsv),
                audioMemoPath = e.audioMemoPath,
                audioMemoDurationSeconds = e.audioMemoDurationSeconds,
                createdAt = Timestamp(Date(e.createdAtMillis)),
                updatedAt = Timestamp(Date(e.updatedAtMillis))
            )
        }
        val steps = dao.getAllSteps().map { e ->
            PlannerStep(
                id = e.id,
                userId = LocalPlannerRepository.LOCAL_USER_ID,
                itemId = e.itemId,
                title = e.title,
                completed = e.completed,
                dueDate = e.dueDate,
                dueTimeMinutes = e.dueTimeMinutes,
                sortOrder = e.sortOrder,
                timeSpentMinutes = e.timeSpentMinutes.coerceAtLeast(0),
                createdAt = Timestamp(Date(e.createdAtMillis)),
                updatedAt = Timestamp(Date(e.updatedAtMillis))
            )
        }
        val reminders = dao.getAllReminders().map { e ->
            PlannerReminder(
                id = e.id,
                userId = LocalPlannerRepository.LOCAL_USER_ID,
                itemId = e.itemId,
                kind = e.kind,
                offsetMinutes = e.offsetMinutes,
                localHour = e.localHour,
                localMinute = e.localMinute,
                scheduledAtUtcMillis = e.scheduledAtUtcMillis,
                snoozedUntilUtcMillis = e.snoozedUntilUtcMillis,
                dismissed = e.dismissed,
                occurrenceKey = e.occurrenceKey,
                createdAt = Timestamp(Date(e.createdAtMillis))
            )
        }
        val classes = dao.getAllClassSchedules().map { e ->
            ClassScheduleItem(
                id = e.id,
                userId = LocalPlannerRepository.LOCAL_USER_ID,
                subject = e.subject,
                room = e.room,
                repeatMode = e.repeatMode,
                daysOfWeek = e.daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..6 }.distinct().sorted(),
                dayOfMonth = e.dayOfMonth.coerceIn(1, 31),
                startTimeMinutes = e.startTimeMinutes.coerceIn(0, 1439),
                endTimeMinutes = e.endTimeMinutes.coerceIn(0, 1439),
                semesterStartDate = e.semesterStartDate,
                semesterMonths = e.semesterMonths.coerceIn(1, 12),
                notifyEnabled = e.notifyEnabled,
                notifyOffsetMinutes = e.notifyOffsetMinutes.coerceIn(0, 10080),
                colorKey = e.colorKey,
                createdAt = Timestamp(Date(e.createdAtMillis)),
                updatedAt = Timestamp(Date(e.updatedAtMillis))
            )
        }

        syncReminders(reminders, items, steps, nowMillis)
        syncClassReminders(classes, nowMillis)
        val config = loadDailyConfig()
        val summary = buildDailyDigestSummary(items, steps, nowMillis)
        syncDailyDigest(config, summary, nowMillis)
    }

    /**
     * Loads a fresh daily digest summary from Room (used when the daily alarm fires in the background).
     */
    suspend fun buildFreshDailyDigestFromDatabase(nowMillis: Long = System.currentTimeMillis()): DailyDigestSummary =
        withContext(Dispatchers.IO) {
            val dao = LocalPlannerDatabase.getInstance(appContext).plannerDao()
            val items = dao.getAllItems().map { e ->
                PlannerItem(
                    id = e.id,
                    userId = LocalPlannerRepository.LOCAL_USER_ID,
                    type = e.type,
                    title = e.title,
                    notes = e.notes,
                    completed = e.completed,
                    startDate = e.startDate,
                    dueDate = e.dueDate,
                    dueTimeMinutes = e.dueTimeMinutes,
                    startAtUtcMillis = e.startAtUtcMillis,
                    endAtUtcMillis = e.endAtUtcMillis,
                    parentPlanId = e.parentPlanId,
                    priority = TaskPriority.fromRaw(e.priority).name,
                    category = TaskCategory.fromRaw(e.category).name,
                    createdAt = Timestamp(Date(e.createdAtMillis)),
                    updatedAt = Timestamp(Date(e.updatedAtMillis))
                )
            }
            val steps = dao.getAllSteps().map { e ->
                PlannerStep(
                    id = e.id,
                    userId = LocalPlannerRepository.LOCAL_USER_ID,
                    itemId = e.itemId,
                    title = e.title,
                    completed = e.completed,
                    dueDate = e.dueDate,
                    dueTimeMinutes = e.dueTimeMinutes,
                    sortOrder = e.sortOrder,
                    timeSpentMinutes = e.timeSpentMinutes.coerceAtLeast(0),
                    createdAt = Timestamp(Date(e.createdAtMillis)),
                    updatedAt = Timestamp(Date(e.updatedAtMillis))
                )
            }
            buildDailyDigestSummary(items, steps, nowMillis)
        }

    /**
     * Re-schedules the next occurrence of every enabled class alarm from Room.
     */
    suspend fun rescheduleNextClassRemindersFromDatabase(nowMillis: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            val dao = LocalPlannerDatabase.getInstance(appContext).plannerDao()
            val classes = dao.getAllClassSchedules().map { e ->
                ClassScheduleItem(
                    id = e.id,
                    userId = LocalPlannerRepository.LOCAL_USER_ID,
                    subject = e.subject,
                    room = e.room,
                    repeatMode = e.repeatMode,
                    daysOfWeek = e.daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..6 }.distinct().sorted(),
                    dayOfMonth = e.dayOfMonth.coerceIn(1, 31),
                    startTimeMinutes = e.startTimeMinutes.coerceIn(0, 1439),
                    endTimeMinutes = e.endTimeMinutes.coerceIn(0, 1439),
                    semesterStartDate = e.semesterStartDate,
                    semesterMonths = e.semesterMonths.coerceIn(1, 12),
                    notifyEnabled = e.notifyEnabled,
                    notifyOffsetMinutes = e.notifyOffsetMinutes.coerceIn(0, 10080),
                    colorKey = e.colorKey,
                    createdAt = Timestamp(Date(e.createdAtMillis)),
                    updatedAt = Timestamp(Date(e.updatedAtMillis))
                )
            }
            syncClassReminders(classes, nowMillis + 60_000L)
        }

    fun cancelAll() = synchronized(lock) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        loadPersistedCodeMap(KEY_SCHEDULED_ITEM_CODES).values.forEach { requestCode ->
            cancelBroadcastAlarm(alarmManager, ACTION_ITEM_REMINDER, requestCode)
        }
        loadPersistedCodeMap(KEY_SCHEDULED_CLASS_CODES).values.forEach { requestCode ->
            cancelBroadcastAlarm(alarmManager, ACTION_CLASS_REMINDER, requestCode)
        }
        savePersistedCodeMap(KEY_SCHEDULED_ITEM_CODES, emptyMap())
        savePersistedCodeMap(KEY_SCHEDULED_CLASS_CODES, emptyMap())
        cancelDailyDigest()
    }

    internal fun getPersistedScheduledItemCount(): Int =
        synchronized(lock) { loadPersistedCodeMap(KEY_SCHEDULED_ITEM_CODES).size }

    internal fun getPersistedScheduledClassCount(): Int =
        synchronized(lock) { loadPersistedCodeMap(KEY_SCHEDULED_CLASS_CODES).size }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun scheduleAlarmCompat(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm unavailable; falling back to inexact idle alarm: ${se.message}")
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }.onFailure {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    private fun cancelBroadcastAlarm(alarmManager: AlarmManager, action: String, requestCode: Int) {
        val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            this.action = action
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

    private fun loadPersistedCodeMap(prefKey: String): MutableMap<String, Int> {
        val raw = prefs.getStringSet(prefKey, emptySet()).orEmpty()
        val map = mutableMapOf<String, Int>()
        raw.forEach { entry ->
            val sep = entry.lastIndexOf('|')
            if (sep > 0 && sep < entry.length - 1) {
                val k = entry.substring(0, sep)
                val code = entry.substring(sep + 1).toIntOrNull()
                if (code != null) map[k] = code
            }
        }
        return map
    }

    private fun savePersistedCodeMap(prefKey: String, map: Map<String, Int>) {
        val encoded = map.entries.map { "${it.key}|${it.value}" }.toSet()
        prefs.edit().putStringSet(prefKey, encoded).apply()
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        const val CHANNEL_ID = "forget_me_not_reminders"
        const val DAILY_DIGEST_CHANNEL_ID = "forget_me_not_daily_digest"
        const val FOCUS_CHANNEL_ID = "bunny_memo_focus_timer"

        const val ACTION_ITEM_REMINDER = "com.example.notifications.ACTION_ITEM_REMINDER"
        const val ACTION_DAILY_DIGEST = "com.example.notifications.ACTION_DAILY_DIGEST"
        const val ACTION_CLASS_REMINDER = "com.example.notifications.ACTION_CLASS_REMINDER"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_CLASS_ID = "extra_class_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val FORGET_ME_NOT_ACCENT_COLOR = 0xFF27578A.toInt()

        private const val DAILY_DIGEST_REQUEST_CODE = 70241
        private const val DAILY_DIGEST_NOTIFICATION_ID = 70242
        private const val FOCUS_NOTIFICATION_ID = 70243

        // Disjoint request-code & notification-ID ranges prevent cross-type PendingIntent collisions
        private const val ITEM_CODE_BASE = 100_000
        private const val ITEM_CODE_SPAN = 400_000
        private const val CLASS_CODE_BASE = 500_000
        private const val CLASS_CODE_SPAN = 400_000

        private const val PREFS_NAME = "bunny_memo_daily_reminders"
        private const val KEY_DAILY_ENABLED = "daily_digest_enabled"
        private const val KEY_DAILY_HOUR = "daily_digest_hour"
        private const val KEY_DAILY_MINUTE = "daily_digest_minute"
        private const val KEY_CACHED_TITLE = "daily_cached_title"
        private const val KEY_CACHED_BODY = "daily_cached_body"
        private const val KEY_CACHED_ITEM_ID = "daily_cached_item_id"
        private const val KEY_SCHEDULED_ITEM_CODES = "scheduled_item_codes_v2"
        private const val KEY_SCHEDULED_CLASS_CODES = "scheduled_class_codes_v2"

        internal fun stableRequestCode(base: Int, span: Int, key: String): Int {
            val nonNegative = key.hashCode() and 0x7FFFFFFF
            return base + (nonNegative % span)
        }
    }
}
