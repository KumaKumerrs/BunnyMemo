package com.example.widgets

import android.content.Context
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.DailyHabitRepository
import com.example.data.DailyHabitSnapshot
import com.example.data.ItemType
import com.example.data.LocalClassScheduleEntity
import com.example.data.LocalItemEntity
import com.example.data.LocalPlannerDatabase
import com.example.data.LocalPlannerRepository
import com.example.data.LocalStepEntity
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerStep
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

data class NextUpcomingClass(
    val schedule: ClassScheduleItem,
    val date: CalendarDate
)

data class WidgetSnapshot(
    val nowMillis: Long,
    val today: CalendarDate,
    val currentMinutesOfDay: Int,
    val allItems: List<PlannerItem>,
    val allSteps: List<PlannerStep>,
    val allClasses: List<ClassScheduleItem>,
    val todayClasses: List<ClassScheduleItem>,
    val nextUpcomingClass: NextUpcomingClass?,
    val todayEvents: List<PlannerItem>,
    val todayActionableTasks: List<PlannerItem>,
    val completedTodayCount: Int,
    val totalTodayTasksCount: Int,
    val latestQuickNote: PlannerItem?,
    val scheduledDatesInMonth: Set<CalendarDate>,
    val currentWeekDates: List<CalendarDate>,
    val scheduledDatesInCurrentWeek: Set<CalendarDate>,
    val weekItemCountByDate: Map<CalendarDate, Int>,
    val habitBloomDates: Set<CalendarDate>,
    val habitStreakDays: Int,
    val dailyHabitSnapshot: DailyHabitSnapshot = DailyHabitSnapshot()
)

object WidgetDataLoader {

    suspend fun load(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        val nowMillis = System.currentTimeMillis()
        val today = CalendarDate.today(nowMillis)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMinutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val db = LocalPlannerDatabase.getInstance(context.applicationContext)
        val dao = db.plannerDao()
        val items = dao.getAllItems().map { it.toModel() }
        val steps = dao.getAllSteps().map { it.toModel() }
        val classes = dao.getAllClassSchedules().map { it.toModel() }
        val habitEntities = dao.getAllDailyHabits()
        val checkInEntities = dao.getAllHabitCheckIns()
        val dailyHabitSnap = DailyHabitRepository.buildSnapshot(
            habitEntities = habitEntities,
            checkInEntities = checkInEntities,
            today = today
        )

        val todayClasses = classes
            .filter { it.occursOnDate(today) }
            .sortedBy { it.startTimeMinutes }

        val nextUpcomingClass = if (todayClasses.isEmpty()) {
            findNextUpcomingClass(classes, today)
        } else {
            null
        }

        val todayEvents = items
            .filter { it.itemType() == ItemType.EVENT && it.appearsOnDate(today) }
            .sortedBy { it.startAtUtcMillis ?: Long.MAX_VALUE }

        // Today's due tasks + overdue unfinished tasks
        val dueTodayOrOverdueTasks = items
            .filter { item ->
                if (item.itemType() != ItemType.TASK) return@filter false
                val due = CalendarDate.parse(item.dueDate)
                due == today || (!item.completed && due != null && due < today)
            }
            .sortedWith(
                compareBy<PlannerItem> { it.completed }
                    .thenBy { it.taskPriority().sortRank }
                    .thenBy { it.dueDate ?: "" }
                    .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
            )

        // Spontaneous Quick Notes (unscheduled tasks)
        val quickNotes = items
            .filter { item ->
                item.itemType() == ItemType.TASK &&
                    item.dueDate.isNullOrBlank() &&
                    item.parentPlanId.isNullOrBlank()
            }
            .sortedWith(
                compareBy<PlannerItem> { it.completed }
                    .thenByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            )

        val actionableTasks = (dueTodayOrOverdueTasks + quickNotes.filter { !it.completed })
            .distinctBy { it.id }

        val completedTodayCount = dueTodayOrOverdueTasks.count { it.completed }
        val totalTodayTasksCount = dueTodayOrOverdueTasks.size
        val latestQuickNote = quickNotes.firstOrNull { !it.completed } ?: quickNotes.firstOrNull()

        // Dates in current month with any event, task, plan, or class
        val monthStart = CalendarDate(today.year, today.month, 1)
        val daysInMonth = monthStart.daysInMonth()
        val scheduledDates = mutableSetOf<CalendarDate>()
        for (dayNum in 1..daysInMonth) {
            val d = CalendarDate(today.year, today.month, dayNum)
            val hasItem = items.any { it.appearsOnDate(d) }
            val hasClass = classes.any { it.occursOnDate(d) }
            if (hasItem || hasClass) {
                scheduledDates.add(d)
            }
        }

        // Current 7-day week (Sunday..Saturday containing today) for the "This Week" widget
        val sundayOffset = today.dayOfWeekIndex()
        val weekStart = today.plusDays(-sundayOffset)
        val currentWeekDates = (0 until 7).map { weekStart.plusDays(it) }
        val scheduledDatesInWeek = mutableSetOf<CalendarDate>()
        val weekItemCounts = mutableMapOf<CalendarDate, Int>()
        currentWeekDates.forEach { d ->
            val itemCount = items.count { it.appearsOnDate(d) }
            val classCount = classes.count { it.occursOnDate(d) }
            val totalForDay = itemCount + classCount
            if (totalForDay > 0) {
                scheduledDatesInWeek.add(d)
                weekItemCounts[d] = totalForDay
            }
        }

        // Unified habit bloom dates: completed tasks/steps + Daily Habit check-ins
        val habitCheckInDates = dailyHabitSnap.checkInCountByDate
            .filterValues { it > 0 }
            .keys
        val bloomDates = computeHabitBloomDates(items, steps) + habitCheckInDates
        val streak = computeHabitStreak(bloomDates, today)

        WidgetSnapshot(
            nowMillis = nowMillis,
            today = today,
            currentMinutesOfDay = currentMinutesOfDay,
            allItems = items,
            allSteps = steps,
            allClasses = classes,
            todayClasses = todayClasses,
            nextUpcomingClass = nextUpcomingClass,
            todayEvents = todayEvents,
            todayActionableTasks = actionableTasks,
            completedTodayCount = completedTodayCount,
            totalTodayTasksCount = totalTodayTasksCount,
            latestQuickNote = latestQuickNote,
            scheduledDatesInMonth = scheduledDates,
            currentWeekDates = currentWeekDates,
            scheduledDatesInCurrentWeek = scheduledDatesInWeek,
            weekItemCountByDate = weekItemCounts,
            habitBloomDates = bloomDates,
            habitStreakDays = maxOf(streak, dailyHabitSnap.overallStreakDays),
            dailyHabitSnapshot = dailyHabitSnap
        )
    }

    private fun findNextUpcomingClass(
        classes: List<ClassScheduleItem>,
        today: CalendarDate
    ): NextUpcomingClass? {
        if (classes.isEmpty()) return null
        for (offset in 1..14) {
            val candidate = today.plusDays(offset)
            val first = classes
                .filter { it.occursOnDate(candidate) }
                .minByOrNull { it.startTimeMinutes }
            if (first != null) {
                return NextUpcomingClass(schedule = first, date = candidate)
            }
        }
        return null
    }

    private fun computeHabitBloomDates(
        items: List<PlannerItem>,
        steps: List<PlannerStep>
    ): Set<CalendarDate> {
        val result = mutableSetOf<CalendarDate>()
        items.forEach { item ->
            if (item.completed) {
                val d = CalendarDate.parse(item.dueDate)
                    ?: item.primaryCalendarDate()
                    ?: item.updatedAt?.toDate()?.time?.let { CalendarDate.fromUtcMillis(it) }
                if (d != null) result.add(d)
            }
        }
        steps.forEach { step ->
            if (step.completed) {
                val d = CalendarDate.parse(step.dueDate)
                    ?: step.updatedAt?.toDate()?.time?.let { CalendarDate.fromUtcMillis(it) }
                if (d != null) result.add(d)
            }
        }
        return result
    }

    private fun computeHabitStreak(bloomDates: Set<CalendarDate>, today: CalendarDate): Int {
        if (bloomDates.isEmpty()) return 0
        var cursor = if (today in bloomDates) today else today.plusDays(-1)
        var streak = 0
        while (cursor in bloomDates && streak < 365) {
            streak++
            cursor = cursor.plusDays(-1)
        }
        return streak
    }

    private fun Long.toTs(): Timestamp = Timestamp(Date(this))

    private fun LocalItemEntity.toModel(): PlannerItem = PlannerItem(
        id = id,
        userId = LocalPlannerRepository.LOCAL_USER_ID,
        type = type,
        title = title,
        notes = notes,
        completed = completed,
        startDate = startDate,
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        startAtUtcMillis = startAtUtcMillis,
        endAtUtcMillis = endAtUtcMillis,
        parentPlanId = parentPlanId,
        priority = TaskPriority.fromRaw(priority).name,
        category = TaskCategory.fromRaw(category).name,
        imagePaths = com.example.data.PhotoAttachmentStore.parsePaths(imagePathsCsv),
        createdAt = createdAtMillis.toTs(),
        updatedAt = updatedAtMillis.toTs()
    )

    private fun LocalStepEntity.toModel(): PlannerStep = PlannerStep(
        id = id,
        userId = LocalPlannerRepository.LOCAL_USER_ID,
        itemId = itemId,
        title = title,
        completed = completed,
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        sortOrder = sortOrder,
        timeSpentMinutes = timeSpentMinutes.coerceAtLeast(0),
        createdAt = createdAtMillis.toTs(),
        updatedAt = updatedAtMillis.toTs()
    )

    private fun LocalClassScheduleEntity.toModel(): ClassScheduleItem = ClassScheduleItem(
        id = id,
        userId = LocalPlannerRepository.LOCAL_USER_ID,
        subject = subject,
        room = room,
        repeatMode = repeatMode,
        daysOfWeek = daysOfWeekCsv
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..6 }
            .distinct()
            .sorted(),
        dayOfMonth = dayOfMonth.coerceIn(1, 31),
        startTimeMinutes = startTimeMinutes.coerceIn(0, 1439),
        endTimeMinutes = endTimeMinutes.coerceIn(0, 1439),
        semesterStartDate = semesterStartDate,
        semesterMonths = semesterMonths.coerceIn(1, 12),
        notifyEnabled = notifyEnabled,
        notifyOffsetMinutes = notifyOffsetMinutes.coerceIn(0, 10080),
        colorKey = colorKey,
        createdAt = createdAtMillis.toTs(),
        updatedAt = updatedAtMillis.toTs()
    )

    fun formatCompactDateOrRelative(date: CalendarDate, today: CalendarDate): String {
        val rel = PlannerDateUtils.formatRelativeDateLabel(date, today)
        return if (rel == "Today" || rel == "Tomorrow" || rel == "Yesterday") {
            rel
        } else {
            PlannerDateUtils.formatCompactDate(date)
        }
    }
}
