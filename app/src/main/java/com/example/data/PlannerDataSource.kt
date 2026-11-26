package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Common data contract implemented by both [LocalPlannerRepository] (device-local Room storage
 * when using Bunny Memo without an account) and [PlannerRepository] (private Firebase Firestore
 * storage when signed in with Google).
 */
interface PlannerDataSource {

    fun observeProfile(userId: String): Flow<UserProfile?>
    fun observeItems(userId: String): Flow<List<PlannerItem>>
    fun observeSteps(userId: String): Flow<List<PlannerStep>>
    fun observeReminders(userId: String): Flow<List<PlannerReminder>>
    fun observeClassSchedules(userId: String): Flow<List<ClassScheduleItem>>

    suspend fun ensureProfileExists(userId: String): Result<Unit>
    suspend fun saveDedication(userId: String, dedicationText: String, markSeen: Boolean): Result<Unit>
    suspend fun updateDefaultReminderTime(userId: String, hour: Int, minute: Int): Result<Unit>

    suspend fun createItemWithStepsAndReminders(
        userId: String,
        type: ItemType,
        title: String,
        notes: String?,
        startDate: String?,
        dueDate: String?,
        dueTimeMinutes: Int?,
        startAtUtcMillis: Long?,
        endAtUtcMillis: Long?,
        parentPlanId: String?,
        steps: List<StepDraft>,
        reminders: List<ReminderDraft>,
        imagePaths: List<String> = emptyList(),
        priority: TaskPriority = TaskPriority.MEDIUM,
        category: TaskCategory = TaskCategory.PERSONAL
    ): Result<String>

    suspend fun updateItemAndRebuildReminders(
        userId: String,
        itemId: String,
        type: ItemType,
        title: String,
        notes: String?,
        completed: Boolean,
        startDate: String?,
        dueDate: String?,
        dueTimeMinutes: Int?,
        startAtUtcMillis: Long?,
        endAtUtcMillis: Long?,
        parentPlanId: String?,
        existingReminders: List<PlannerReminder>,
        updatedReminderDrafts: List<ReminderDraft>? = null,
        imagePaths: List<String>? = null,
        priority: TaskPriority? = null,
        category: TaskCategory? = null
    ): Result<Unit>

    suspend fun updateItemImages(
        userId: String,
        itemId: String,
        imagePaths: List<String>
    ): Result<Unit>

    suspend fun updateItemAudioMemo(
        userId: String,
        itemId: String,
        audioMemoPath: String?,
        durationSeconds: Int
    ): Result<Unit>

    suspend fun updateItemPriority(
        userId: String,
        itemId: String,
        priority: TaskPriority
    ): Result<Unit>

    suspend fun updateItemCategory(
        userId: String,
        itemId: String,
        category: TaskCategory
    ): Result<Unit>

    suspend fun toggleItemCompleted(userId: String, itemId: String, completed: Boolean): Result<Unit>

    suspend fun deleteItemCascade(
        userId: String,
        itemId: String,
        itemSteps: List<PlannerStep>,
        itemReminders: List<PlannerReminder>
    ): Result<Unit>

    suspend fun addStep(
        userId: String,
        itemId: String,
        title: String,
        dueDate: String?,
        dueTimeMinutes: Int?,
        sortOrder: Int
    ): Result<String>

    suspend fun toggleStepCompleted(userId: String, stepId: String, completed: Boolean): Result<Unit>
    suspend fun addStepTimeSpent(userId: String, stepId: String, addedMinutes: Int): Result<Unit>
    suspend fun deleteStep(userId: String, stepId: String): Result<Unit>

    suspend fun addReminder(
        userId: String,
        item: PlannerItem,
        kind: ReminderKind,
        offsetMinutes: Int,
        localHour: Int,
        localMinute: Int
    ): Result<String>

    suspend fun snoozeReminderOccurrence(
        userId: String,
        reminderId: String,
        snoozedUntilUtcMillis: Long
    ): Result<Unit>

    suspend fun dismissReminder(userId: String, reminderId: String): Result<Unit>
    suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit>

    suspend fun createClassSchedule(
        userId: String,
        subject: String,
        room: String?,
        repeatMode: ClassRepeatMode,
        daysOfWeek: List<Int>,
        dayOfMonth: Int,
        startTimeMinutes: Int,
        endTimeMinutes: Int,
        semesterStartDate: String,
        semesterMonths: Int,
        notifyEnabled: Boolean,
        notifyOffsetMinutes: Int,
        colorKey: ClassColorKey
    ): Result<String>

    suspend fun updateClassSchedule(
        userId: String,
        scheduleId: String,
        subject: String,
        room: String?,
        repeatMode: ClassRepeatMode,
        daysOfWeek: List<Int>,
        dayOfMonth: Int,
        startTimeMinutes: Int,
        endTimeMinutes: Int,
        semesterStartDate: String,
        semesterMonths: Int,
        notifyEnabled: Boolean,
        notifyOffsetMinutes: Int,
        colorKey: ClassColorKey
    ): Result<Unit>

    suspend fun deleteClassSchedule(userId: String, scheduleId: String): Result<Unit>
}
