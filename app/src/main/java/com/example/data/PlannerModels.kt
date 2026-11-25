package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

enum class ItemType(val label: String) {
    PLAN("Plan"),
    TASK("Task"),
    EVENT("Event");

    companion object {
        fun fromRaw(raw: String?): ItemType {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TASK
        }
    }
}

enum class ReminderKind(val label: String) {
    ORDINARY("Reminder"),
    PROGRESS_CHECKIN("Progress check-in");

    companion object {
        fun fromRaw(raw: String?): ReminderKind {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ORDINARY
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dedicationText: String = DEFAULT_DEDICATION,
    val hasSeenDedication: Boolean = false,
    val defaultReminderHour: Int = 9,
    val defaultReminderMinute: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val DEFAULT_DEDICATION =
            "For you, bb. I made this little planner so you have a place for all the things you look forward to. Love you."
    }
}

@Keep
@IgnoreExtraProperties
data class PlannerItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = ItemType.TASK.name,
    val title: String = "",
    val notes: String? = null,
    val completed: Boolean = false,
    val startDate: String? = null,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val startAtUtcMillis: Long? = null,
    val endAtUtcMillis: Long? = null,
    val parentPlanId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun itemType(): ItemType = ItemType.fromRaw(type)

    fun primaryCalendarDate(): CalendarDate? {
        return when (itemType()) {
            ItemType.EVENT -> startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                ?: CalendarDate.parse(dueDate)
            ItemType.PLAN -> CalendarDate.parse(dueDate) ?: CalendarDate.parse(startDate)
            ItemType.TASK -> CalendarDate.parse(dueDate)
        }
    }

    fun appearsOnDate(date: CalendarDate): Boolean {
        return when (itemType()) {
            ItemType.EVENT -> {
                val startDay = startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                val endDay = endAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) } ?: startDay
                if (startDay != null && endDay != null) {
                    date >= startDay && date <= endDay
                } else {
                    CalendarDate.parse(dueDate) == date
                }
            }
            ItemType.TASK -> CalendarDate.parse(dueDate) == date
            ItemType.PLAN -> {
                val start = CalendarDate.parse(startDate)
                val due = CalendarDate.parse(dueDate)
                when {
                    start != null && due != null -> date >= start && date <= due
                    due != null -> date == due
                    start != null -> date == start
                    else -> false
                }
            }
        }
    }
}

@Keep
@IgnoreExtraProperties
data class PlannerStep(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val sortOrder: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlannerReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val kind: String = ReminderKind.ORDINARY.name,
    val offsetMinutes: Int = 60,
    val localHour: Int = 9,
    val localMinute: Int = 0,
    val scheduledAtUtcMillis: Long = 0L,
    val snoozedUntilUtcMillis: Long? = null,
    val occurrenceKey: String = "",
    val dismissed: Boolean = false,
    val deliveredAtUtcMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun reminderKind(): ReminderKind = ReminderKind.fromRaw(kind)

    fun effectiveTriggerMillis(): Long = snoozedUntilUtcMillis ?: scheduledAtUtcMillis
}

enum class ClassRepeatMode(val label: String) {
    WEEKLY("Weekly days"),
    MONTHLY_DAY("Day of month");

    companion object {
        fun fromRaw(raw: String?): ClassRepeatMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WEEKLY
        }
    }
}

enum class ClassColorKey(val label: String) {
    SKY("Sky"),
    CORNFLOWER("Bloom"),
    SAGE("Sage"),
    POLLEN("Pollen");

    companion object {
        fun fromRaw(raw: String?): ClassColorKey {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SKY
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ClassScheduleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val room: String? = null,
    val repeatMode: String = ClassRepeatMode.WEEKLY.name,
    val daysOfWeek: List<Int> = emptyList(), // 0=Sun..6=Sat
    val dayOfMonth: Int = 1,                 // 1..31
    val startTimeMinutes: Int = 9 * 60,
    val endTimeMinutes: Int = 10 * 60,
    val semesterStartDate: String = "",      // YYYY-MM-DD
    val semesterMonths: Int = 4,             // 1..12
    val notifyEnabled: Boolean = true,
    val notifyOffsetMinutes: Int = 15,       // 0..10080
    val colorKey: String = ClassColorKey.SKY.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun repeatModeEnum(): ClassRepeatMode = ClassRepeatMode.fromRaw(repeatMode)

    fun colorKeyEnum(): ClassColorKey = ClassColorKey.fromRaw(colorKey)

    fun semesterStartCalendarDate(): CalendarDate? = CalendarDate.parse(semesterStartDate)

    fun semesterEndExclusiveDate(): CalendarDate? {
        val start = semesterStartCalendarDate() ?: return null
        return start.plusMonthsClamped(semesterMonths.coerceIn(1, 12))
    }

    fun semesterEndInclusiveDate(): CalendarDate? {
        return semesterEndExclusiveDate()?.plusDays(-1)
    }

    fun occursOnDate(date: CalendarDate): Boolean {
        val start = semesterStartCalendarDate() ?: return false
        val endExclusive = semesterEndExclusiveDate() ?: return false
        if (date < start || date >= endExclusive) return false
        return when (repeatModeEnum()) {
            ClassRepeatMode.WEEKLY -> date.dayOfWeekIndex() in daysOfWeek
            ClassRepeatMode.MONTHLY_DAY -> {
                val targetDay = dayOfMonth.coerceIn(1, date.daysInMonth())
                date.day == targetDay
            }
        }
    }

    fun isActiveOnOrAfter(today: CalendarDate): Boolean {
        val endInclusive = semesterEndInclusiveDate() ?: return true
        return endInclusive >= today
    }

    /**
     * Returns (triggerUtcMillis, classStartUtcMillis) for the soonest future notification
     * occurrence within the semester, scanning up to 62 days ahead.
     */
    fun nextNotificationOccurrence(nowMillis: Long): Pair<Long, Long>? {
        if (!notifyEnabled) return null
        val start = semesterStartCalendarDate() ?: return null
        val endExclusive = semesterEndExclusiveDate() ?: return null
        val today = CalendarDate.today(nowMillis)
        val scanStart = if (start > today) start else today
        if (scanStart >= endExclusive) return null

        val offsetMs = notifyOffsetMinutes.coerceIn(0, 10080).toLong() * 60_000L
        var cursor = scanStart
        var steps = 0
        while (cursor < endExclusive && steps < 62) {
            if (occursOnDate(cursor)) {
                val classStartMs = cursor.toLocalEpochMillis(
                    startTimeMinutes.coerceIn(0, 1439) / 60,
                    startTimeMinutes.coerceIn(0, 1439) % 60
                )
                val triggerMs = (classStartMs - offsetMs).coerceAtLeast(0L)
                if (triggerMs > nowMillis) {
                    return triggerMs to classStartMs
                }
            }
            cursor = cursor.plusDays(1)
            steps++
        }
        return null
    }

    fun formattedTimeRange(): String =
        PlannerDateUtils.formatMinutesRange(startTimeMinutes, endTimeMinutes)

    fun formattedSemesterSpan(): String {
        val start = semesterStartCalendarDate() ?: return "${semesterMonths} mo semester"
        val end = semesterEndInclusiveDate() ?: return "${semesterMonths} mo semester"
        val unit = if (semesterMonths == 1) "month" else "months"
        return "${PlannerDateUtils.formatCompactDate(start)} – ${PlannerDateUtils.formatCompactDate(end)} ($semesterMonths $unit)"
    }

    fun repeatSummaryLabel(): String {
        val moTag = if (semesterMonths == 1) "1 mo" else "$semesterMonths mo"
        return when (repeatModeEnum()) {
            ClassRepeatMode.WEEKLY -> {
                val ordered = listOf(1, 2, 3, 4, 5, 6, 0).filter { it in daysOfWeek }
                val daysStr = if (ordered.isEmpty()) "Weekly"
                else ordered.joinToString(", ") { PlannerDateUtils.weekdayShortLabel(it) }
                "$daysStr · $moTag"
            }
            ClassRepeatMode.MONTHLY_DAY -> {
                "Every ${PlannerDateUtils.formatOrdinalDay(dayOfMonth)} · $moTag"
            }
        }
    }
}

/**
 * Unified step entry representing either an embedded PlannerStep (milestone/prep step)
 * or a Task item linked to a Plan via parentPlanId.
 */
data class UnifiedStep(
    val id: String,
    val parentItemId: String,
    val title: String,
    val completed: Boolean,
    val dueDate: String?,
    val dueTimeMinutes: Int?,
    val isLinkedTask: Boolean,
    val sortOrder: Int
) {
    fun isOverdue(nowMillis: Long): Boolean {
        if (completed) return false
        val date = CalendarDate.parse(dueDate) ?: return false
        val mins = dueTimeMinutes ?: (23 * 60 + 59)
        val deadlineMillis = date.toLocalEpochMillis(mins / 60, mins % 60)
        return nowMillis > deadlineMillis
    }
}

data class ItemProgress(
    val completedCount: Int,
    val totalCount: Int,
    val percent: Int,
    val overdueCount: Int,
    val allStepsDone: Boolean
) {
    fun summaryLabel(itemType: ItemType): String {
        if (totalCount == 0) return ""
        val unit = when (itemType) {
            ItemType.PLAN -> if (totalCount == 1) "milestone" else "milestones"
            ItemType.EVENT -> if (totalCount == 1) "prep step" else "prep steps"
            ItemType.TASK -> if (totalCount == 1) "step" else "steps"
        }
        return "$completedCount of $totalCount $unit complete · $percent%"
    }

    fun completionLabel(itemType: ItemType): String {
        val unit = if (itemType == ItemType.EVENT) "preparation steps" else "milestones"
        return "$completedCount of $totalCount $unit complete."
    }
}

data class InboxNotificationEntry(
    val reminder: PlannerReminder,
    val item: PlannerItem,
    val triggerUtcMillis: Long,
    val title: String,
    val message: String,
    val remainingSteps: List<String>,
    val isSnoozed: Boolean
)

data class StepDraft(
    val title: String,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null
)

data class ReminderDraft(
    val kind: ReminderKind,
    val offsetMinutes: Int,
    val localHour: Int,
    val localMinute: Int
)

object PlannerLogic {

    fun stepsForItem(
        item: PlannerItem,
        allSteps: List<PlannerStep>,
        allItems: List<PlannerItem>
    ): List<UnifiedStep> {
        val direct = allSteps
            .filter { it.itemId == item.id }
            .sortedBy { it.sortOrder }
            .map { step ->
                UnifiedStep(
                    id = step.id,
                    parentItemId = item.id,
                    title = step.title,
                    completed = step.completed,
                    dueDate = step.dueDate,
                    dueTimeMinutes = step.dueTimeMinutes,
                    isLinkedTask = false,
                    sortOrder = step.sortOrder
                )
            }

        val linkedTasks = if (item.itemType() == ItemType.PLAN) {
            allItems
                .filter { it.itemType() == ItemType.TASK && it.parentPlanId == item.id }
                .mapIndexed { idx, task ->
                    UnifiedStep(
                        id = task.id,
                        parentItemId = item.id,
                        title = task.title,
                        completed = task.completed,
                        dueDate = task.dueDate,
                        dueTimeMinutes = task.dueTimeMinutes,
                        isLinkedTask = true,
                        sortOrder = 1000 + idx
                    )
                }
        } else {
            emptyList()
        }

        return direct + linkedTasks
    }

    fun computeProgress(
        item: PlannerItem,
        allSteps: List<PlannerStep>,
        allItems: List<PlannerItem>,
        nowMillis: Long
    ): ItemProgress {
        val steps = stepsForItem(item, allSteps, allItems)
        if (steps.isEmpty()) {
            return ItemProgress(
                completedCount = if (item.completed) 1 else 0,
                totalCount = 0,
                percent = if (item.completed) 100 else 0,
                overdueCount = 0,
                allStepsDone = item.completed
            )
        }
        val completed = steps.count { it.completed }
        val total = steps.size
        val percent = ((completed.toDouble() / total.toDouble()) * 100.0).roundToInt()
        val overdue = steps.count { it.isOverdue(nowMillis) }
        return ItemProgress(
            completedCount = completed,
            totalCount = total,
            percent = percent,
            overdueCount = overdue,
            allStepsDone = completed == total
        )
    }

    /**
     * Builds the dynamic reminder message rechecking live completion state.
     * Returns null when a PROGRESS_CHECKIN should be skipped because all steps are done.
     */
    fun buildReminderMessage(
        reminder: PlannerReminder,
        item: PlannerItem,
        allSteps: List<PlannerStep>,
        allItems: List<PlannerItem>,
        nowMillis: Long
    ): Pair<String, List<String>>? {
        val steps = stepsForItem(item, allSteps, allItems)
        val remaining = steps.filter { !it.completed }.map { it.title }
        val kind = reminder.reminderKind()

        if (kind == ReminderKind.PROGRESS_CHECKIN) {
            // Brief Page 4: A progress check-in runs only if work remains.
            if (steps.isNotEmpty() && remaining.isEmpty()) return null
            if (steps.isEmpty() && item.completed) return null
        }

        val timingPrefix = when (item.itemType()) {
            ItemType.EVENT -> {
                val start = item.startAtUtcMillis
                if (start != null) {
                    val today = CalendarDate.today(nowMillis)
                    val evDay = CalendarDate.fromUtcMillis(start)
                    val rel = PlannerDateUtils.formatRelativeDateLabel(evDay, today)
                    val time = PlannerDateUtils.formatUtcMillisTime(start)
                    "${item.title} is ${rel.lowercase(java.util.Locale.getDefault())} at $time."
                } else {
                    "${item.title} is coming up."
                }
            }
            ItemType.PLAN -> {
                val due = CalendarDate.parse(item.dueDate)
                if (due != null) {
                    val rel = PlannerDateUtils.formatRelativeDateLabel(due, CalendarDate.today(nowMillis))
                    "${item.title} deadline is ${rel.lowercase(java.util.Locale.getDefault())}."
                } else {
                    "Check-in for ${item.title}."
                }
            }
            ItemType.TASK -> {
                val due = CalendarDate.parse(item.dueDate)
                if (due != null) {
                    val rel = PlannerDateUtils.formatRelativeDateLabel(due, CalendarDate.today(nowMillis))
                    "${item.title} is due ${rel.lowercase(java.util.Locale.getDefault())}."
                } else {
                    "Reminder: ${item.title}."
                }
            }
        }

        val stepPhrase = when {
            remaining.isEmpty() -> {
                if (item.itemType() == ItemType.EVENT && steps.isNotEmpty()) {
                    "All preparation steps are complete."
                } else {
                    ""
                }
            }
            remaining.size == 1 -> {
                val stepNoun = if (item.itemType() == ItemType.EVENT) "preparation step" else "step"
                "One $stepNoun remains: ${remaining.first()}."
            }
            remaining.size == 2 -> {
                val stepNoun = if (item.itemType() == ItemType.EVENT) "preparation steps" else "steps"
                "Two $stepNoun remain: ${remaining[0]} and ${remaining[1]}."
            }
            else -> {
                val stepNoun = if (item.itemType() == ItemType.EVENT) "preparation steps" else "steps"
                "${remaining.size} $stepNoun remain: ${remaining.take(2).joinToString(", ")} and ${remaining.size - 2} more."
            }
        }

        val fullMessage = if (stepPhrase.isNotBlank()) "$timingPrefix $stepPhrase" else timingPrefix
        return fullMessage to remaining
    }

    fun buildInboxEntries(
        reminders: List<PlannerReminder>,
        items: List<PlannerItem>,
        steps: List<PlannerStep>,
        nowMillis: Long
    ): List<InboxNotificationEntry> {
        val itemMap = items.associateBy { it.id }
        return reminders
            .filter { !it.dismissed }
            .mapNotNull { reminder ->
                val item = itemMap[reminder.itemId] ?: return@mapNotNull null
                val trigger = reminder.effectiveTriggerMillis()
                // Show triggered reminders and reminders coming due within the next 24h in dated inbox
                // so the user can inspect, finish a step, snooze, or reschedule easily.
                val msgPair = buildReminderMessage(reminder, item, steps, items, nowMillis)
                    ?: return@mapNotNull null
                InboxNotificationEntry(
                    reminder = reminder,
                    item = item,
                    triggerUtcMillis = trigger,
                    title = if (reminder.reminderKind() == ReminderKind.PROGRESS_CHECKIN) {
                        "Progress check-in · ${item.title}"
                    } else {
                        item.title
                    },
                    message = msgPair.first,
                    remainingSteps = msgPair.second,
                    isSnoozed = reminder.snoozedUntilUtcMillis != null && reminder.snoozedUntilUtcMillis > nowMillis
                )
            }
            .sortedBy { it.triggerUtcMillis }
    }
}
