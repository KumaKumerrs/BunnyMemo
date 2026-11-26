package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.audio.TaskAudioMemoManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID

internal suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Offline-first Room implementation of [PlannerDataSource].
 * Every write persists atomically with its [PendingSyncOperationEntity] outbox entry inside a
 * Room transaction so [RoomFirestoreSyncService] can push pending local changes to Firestore
 * without drift or lost mutations.
 */
class LocalPlannerRepository(
    private val dao: LocalPlannerDao,
    private val database: LocalPlannerDatabase? = null,
    private val onMutationEnqueued: (() -> Unit)? = null
) : PlannerDataSource {

    companion object {
        const val LOCAL_USER_ID = "local_guest"

        /**
         * Wipes all user-specific local state on sign-out (Room tables, local photo/audio files,
         * and user-scoped SharedPreferences) to guarantee zero cross-account data leakage.
         */
        suspend fun wipeAllUserLocalData(context: Context) {
            val appCtx = context.applicationContext
            withContext(Dispatchers.IO) {
                LocalPlannerDatabase.getInstance(appCtx).plannerDao().clearAllLocalPlannerData()
                runCatching { File(appCtx.filesDir, "planner_photos").deleteRecursively() }
                runCatching { File(appCtx.filesDir, "task_audio_memos").deleteRecursively() }
                runCatching { AppUsageTracker.getInstance(appCtx).resetAllUsage() }
                runCatching {
                    appCtx.getSharedPreferences("bunny_shared_calendar_seen_v1", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
                runCatching {
                    appCtx.getSharedPreferences("bunny_memo_focus_session_v1", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
            }
        }
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T {
        val db = database
        return if (db != null) db.withTransaction { block() } else block()
    }

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(20)}"

    private fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

    private fun sanitizeDateOrNull(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return CalendarDate.parse(trimmed)?.toIsoString()
    }

    private fun LocalProfileEntity.toModel(effectiveUserId: String): UserProfile = UserProfile(
        id = effectiveUserId,
        userId = effectiveUserId,
        dedicationText = dedicationText,
        hasSeenDedication = hasSeenDedication,
        defaultReminderHour = defaultReminderHour,
        defaultReminderMinute = defaultReminderMinute,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalItemEntity.toModel(effectiveUserId: String): PlannerItem = PlannerItem(
        id = id,
        userId = effectiveUserId,
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
        imagePaths = PhotoAttachmentStore.parsePaths(imagePathsCsv),
        audioMemoPath = audioMemoPath,
        audioMemoDurationSeconds = audioMemoDurationSeconds,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalStepEntity.toModel(effectiveUserId: String): PlannerStep = PlannerStep(
        id = id,
        userId = effectiveUserId,
        itemId = itemId,
        title = title,
        completed = completed,
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        sortOrder = sortOrder,
        timeSpentMinutes = timeSpentMinutes.coerceAtLeast(0),
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalReminderEntity.toModel(effectiveUserId: String): PlannerReminder = PlannerReminder(
        id = id,
        userId = effectiveUserId,
        itemId = itemId,
        kind = kind,
        offsetMinutes = offsetMinutes,
        localHour = localHour,
        localMinute = localMinute,
        scheduledAtUtcMillis = scheduledAtUtcMillis,
        snoozedUntilUtcMillis = snoozedUntilUtcMillis,
        occurrenceKey = occurrenceKey,
        dismissed = dismissed,
        deliveredAtUtcMillis = deliveredAtUtcMillis,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalClassScheduleEntity.toModel(effectiveUserId: String): ClassScheduleItem = ClassScheduleItem(
        id = id,
        userId = effectiveUserId,
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
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    /**
     * Ensures pre-existing local guest records (created before sign-in) are queued as pending
     * UPSERTs so [RoomFirestoreSyncService] pushes them cleanly. Only queues the profile when
     * the user explicitly customized their dedication or reminder defaults ([hasCustomLocalEdits]).
     */
    suspend fun enqueueUnqueuedLocalDataForSync() {
        val now = System.currentTimeMillis()
        inTransaction {
            val existingKeys = dao.getAllPendingSyncOps().map { it.opKey }.toSet()
            val ops = mutableListOf<PendingSyncOperationEntity>()

            val profile = dao.getProfile(LOCAL_USER_ID)
            if (profile != null && profile.hasCustomLocalEdits) {
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.PROFILE, LOCAL_USER_ID)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.PROFILE, LOCAL_USER_ID, now))
                }
            }

            dao.getAllItems().forEach { item ->
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, item.id)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, item.id, now))
                }
            }
            dao.getAllSteps().forEach { step ->
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.STEP, step.id)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.STEP, step.id, now))
                }
            }
            dao.getAllReminders().forEach { rem ->
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.REMINDER, rem.id)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, rem.id, now))
                }
            }
            dao.getAllClassSchedules().forEach { cls ->
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.CLASS_SCHEDULE, cls.id)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.CLASS_SCHEDULE, cls.id, now))
                }
            }
            dao.getAllDailyHabits().forEach { habit ->
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.DAILY_HABIT, habit.id)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.DAILY_HABIT, habit.id, now))
                }
            }
            dao.getAllHabitCheckIns().forEach { checkIn ->
                val key = PendingSyncOperationEntity.keyFor(SyncEntityType.HABIT_CHECKIN, checkIn.checkInKey)
                if (key !in existingKeys) {
                    ops.add(PendingSyncOperationEntity.upsert(SyncEntityType.HABIT_CHECKIN, checkIn.checkInKey, now))
                }
            }

            if (ops.isNotEmpty()) {
                dao.upsertPendingSyncOps(ops)
            }
        }
    }

    // --- Reactive Observations ---

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        dao.observeProfile(LOCAL_USER_ID).map { it?.toModel(userId.ifBlank { LOCAL_USER_ID }) }

    override fun observeItems(userId: String): Flow<List<PlannerItem>> =
        dao.observeItems().map { list ->
            val uid = userId.ifBlank { LOCAL_USER_ID }
            list.map { it.toModel(uid) }
        }

    override fun observeSteps(userId: String): Flow<List<PlannerStep>> =
        dao.observeSteps().map { list ->
            val uid = userId.ifBlank { LOCAL_USER_ID }
            list.map { it.toModel(uid) }
        }

    override fun observeReminders(userId: String): Flow<List<PlannerReminder>> =
        dao.observeReminders().map { list ->
            val uid = userId.ifBlank { LOCAL_USER_ID }
            list.map { it.toModel(uid) }
        }

    override fun observeClassSchedules(userId: String): Flow<List<ClassScheduleItem>> =
        dao.observeClassSchedules().map { list ->
            val uid = userId.ifBlank { LOCAL_USER_ID }
            list.map { it.toModel(uid) }
        }

    // --- Profile & Dedication ---

    override suspend fun ensureProfileExists(userId: String): Result<Unit> = suspendRunCatching {
        val existing = dao.getProfile(LOCAL_USER_ID)
        if (existing == null) {
            val now = System.currentTimeMillis()
            dao.upsertProfile(
                LocalProfileEntity(
                    id = LOCAL_USER_ID,
                    dedicationText = UserProfile.DEFAULT_DEDICATION,
                    hasSeenDedication = false,
                    defaultReminderHour = 9,
                    defaultReminderMinute = 0,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    hasCustomLocalEdits = false
                )
            )
        }
    }

    override suspend fun saveDedication(
        userId: String,
        dedicationText: String,
        markSeen: Boolean
    ): Result<Unit> = suspendRunCatching {
        val cleaned = dedicationText.trim().take(500).ifBlank { UserProfile.DEFAULT_DEDICATION }
        val now = System.currentTimeMillis()
        inTransaction {
            val existing = dao.getProfile(LOCAL_USER_ID)
            val isCustomized = cleaned != UserProfile.DEFAULT_DEDICATION ||
                (existing?.hasCustomLocalEdits == true)
            if (existing == null) {
                dao.upsertProfile(
                    LocalProfileEntity(
                        id = LOCAL_USER_ID,
                        dedicationText = cleaned,
                        hasSeenDedication = markSeen,
                        defaultReminderHour = 9,
                        defaultReminderMinute = 0,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        hasCustomLocalEdits = isCustomized
                    )
                )
            } else {
                dao.updateDedication(LOCAL_USER_ID, cleaned, markSeen, isCustomized, now)
            }
            if (isCustomized || userId != LOCAL_USER_ID) {
                dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.PROFILE, LOCAL_USER_ID, now))
            }
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun updateDefaultReminderTime(
        userId: String,
        hour: Int,
        minute: Int
    ): Result<Unit> = suspendRunCatching {
        ensureProfileExists(LOCAL_USER_ID).getOrThrow()
        val now = System.currentTimeMillis()
        inTransaction {
            dao.updateDefaultReminderTime(
                profileId = LOCAL_USER_ID,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                now = now
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.PROFILE, LOCAL_USER_ID, now))
        }
        onMutationEnqueued?.invoke()
    }

    // --- Items, Steps, and Reminders CRUD ---

    override suspend fun createItemWithStepsAndReminders(
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
        imagePaths: List<String>,
        priority: TaskPriority,
        category: TaskCategory
    ): Result<String> = suspendRunCatching {
        val cleanTitle = title.trim().take(200)
        require(cleanTitle.isNotBlank()) { "Title is required." }

        val now = System.currentTimeMillis()
        val itemId = newId("item")
        val cleanNotes = notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
        val cleanParent = parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK && it != itemId }
        val encodedPhotos = PhotoAttachmentStore.encodePaths(imagePaths)

        val itemEntity = LocalItemEntity(
            id = itemId,
            type = type.name,
            title = cleanTitle,
            notes = cleanNotes,
            completed = false,
            startDate = sanitizeDateOrNull(startDate),
            dueDate = sanitizeDateOrNull(dueDate),
            dueTimeMinutes = dueTimeMinutes?.coerceIn(0, 1439),
            startAtUtcMillis = startAtUtcMillis?.coerceAtLeast(0L),
            endAtUtcMillis = endAtUtcMillis?.coerceAtLeast(0L),
            parentPlanId = cleanParent,
            createdAtMillis = now,
            updatedAtMillis = now,
            imagePathsCsv = encodedPhotos,
            priority = priority.name,
            category = category.name
        )

        val draftItem = itemEntity.toModel(userId.ifBlank { LOCAL_USER_ID })

        val stepEntities = steps.filter { it.title.isNotBlank() }.take(50).mapIndexed { idx, step ->
            LocalStepEntity(
                id = newId("step"),
                itemId = itemId,
                title = step.title.trim().take(200),
                completed = false,
                dueDate = sanitizeDateOrNull(step.dueDate),
                dueTimeMinutes = step.dueTimeMinutes?.coerceIn(0, 1439),
                sortOrder = idx,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }

        val remEntities = reminders.take(10).map { rem ->
            val safeOffset = rem.offsetMinutes.coerceIn(0, 525600)
            val safeHour = rem.localHour.coerceIn(0, 23)
            val safeMin = rem.localMinute.coerceIn(0, 59)
            val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                draftItem, safeOffset, safeHour, safeMin
            )
            val occKey = "${itemId}_${rem.kind.name}_${safeOffset}_${scheduledAt}".take(128)
            LocalReminderEntity(
                id = newId("rem"),
                itemId = itemId,
                kind = rem.kind.name,
                offsetMinutes = safeOffset,
                localHour = safeHour,
                localMinute = safeMin,
                scheduledAtUtcMillis = scheduledAt,
                snoozedUntilUtcMillis = null,
                occurrenceKey = occKey,
                dismissed = false,
                deliveredAtUtcMillis = null,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }

        val syncOps = buildList {
            add(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now))
            stepEntities.forEach { add(PendingSyncOperationEntity.upsert(SyncEntityType.STEP, it.id, now)) }
            remEntities.forEach { add(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, it.id, now)) }
        }

        inTransaction {
            dao.insertItemBundle(itemEntity, stepEntities, remEntities)
            dao.upsertPendingSyncOps(syncOps)
        }
        onMutationEnqueued?.invoke()
        itemId
    }

    override suspend fun updateItemAndRebuildReminders(
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
        updatedReminderDrafts: List<ReminderDraft>?,
        imagePaths: List<String>?,
        priority: TaskPriority?,
        category: TaskCategory?
    ): Result<Unit> = suspendRunCatching {
        require(itemId.isNotBlank()) { "Item ID must not be blank." }
        val cleanTitle = title.trim().take(200)
        require(cleanTitle.isNotBlank()) { "Title is required." }

        val now = System.currentTimeMillis()
        val cleanNotes = notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
        val cleanParent = parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK && it != itemId }
        var removedPhotoPaths: List<String> = emptyList()

        inTransaction {
            val existingEntity = dao.getItemById(itemId)
            val oldCreated = existingEntity?.createdAtMillis ?: now
            val oldPhotoPaths = PhotoAttachmentStore.parsePaths(existingEntity?.imagePathsCsv)
            val resolvedPhotosCsv = if (imagePaths != null) {
                val keptSet = imagePaths.toSet()
                removedPhotoPaths = oldPhotoPaths.filter { it !in keptSet }
                PhotoAttachmentStore.encodePaths(imagePaths)
            } else {
                existingEntity?.imagePathsCsv
            }
            val resolvedPriority = (priority ?: TaskPriority.fromRaw(existingEntity?.priority)).name
            val resolvedCategory = (category ?: TaskCategory.fromRaw(existingEntity?.category)).name

            val updatedEntity = LocalItemEntity(
                id = itemId,
                type = type.name,
                title = cleanTitle,
                notes = cleanNotes,
                completed = completed,
                startDate = sanitizeDateOrNull(startDate),
                dueDate = sanitizeDateOrNull(dueDate),
                dueTimeMinutes = dueTimeMinutes?.coerceIn(0, 1439),
                startAtUtcMillis = startAtUtcMillis?.coerceAtLeast(0L),
                endAtUtcMillis = endAtUtcMillis?.coerceAtLeast(0L),
                parentPlanId = cleanParent,
                createdAtMillis = oldCreated,
                updatedAtMillis = now,
                imagePathsCsv = resolvedPhotosCsv,
                priority = resolvedPriority,
                category = resolvedCategory,
                audioMemoPath = existingEntity?.audioMemoPath,
                audioMemoDurationSeconds = existingEntity?.audioMemoDurationSeconds ?: 0
            )
            val updatedModel = updatedEntity.toModel(userId.ifBlank { LOCAL_USER_ID })

            dao.upsertItem(updatedEntity)
            val syncOps = mutableListOf(
                PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now)
            )

            val dbExistingRems = dao.getRemindersByItemId(itemId)
            if (updatedReminderDrafts != null) {
                val oldRemIds = (dbExistingRems.map { it.id } +
                    existingReminders.filter { it.itemId == itemId }.map { it.id }).distinct()
                dao.deleteRemindersByItemId(itemId)
                oldRemIds.forEach { oldId ->
                    syncOps.add(PendingSyncOperationEntity.delete(SyncEntityType.REMINDER, oldId, now))
                }

                val rebuilt = updatedReminderDrafts.take(10).map { rem ->
                    val safeOffset = rem.offsetMinutes.coerceIn(0, 525600)
                    val safeHour = rem.localHour.coerceIn(0, 23)
                    val safeMin = rem.localMinute.coerceIn(0, 59)
                    val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                        updatedModel, safeOffset, safeHour, safeMin
                    )
                    val occKey = "${itemId}_${rem.kind.name}_${safeOffset}_${scheduledAt}".take(128)
                    LocalReminderEntity(
                        id = newId("rem"),
                        itemId = itemId,
                        kind = rem.kind.name,
                        offsetMinutes = safeOffset,
                        localHour = safeHour,
                        localMinute = safeMin,
                        scheduledAtUtcMillis = scheduledAt,
                        snoozedUntilUtcMillis = null,
                        occurrenceKey = occKey,
                        dismissed = false,
                        deliveredAtUtcMillis = null,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
                if (rebuilt.isNotEmpty()) {
                    dao.upsertReminders(rebuilt)
                    rebuilt.forEach { rem ->
                        syncOps.add(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, rem.id, now))
                    }
                }
            } else {
                val sourceRems = if (dbExistingRems.isNotEmpty()) {
                    dbExistingRems.map { it.toModel(userId.ifBlank { LOCAL_USER_ID }) }
                } else {
                    existingReminders.filter { it.itemId == itemId }
                }
                val rebuilt = sourceRems.map { oldRem ->
                    val newScheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                        updatedModel,
                        oldRem.offsetMinutes,
                        oldRem.localHour,
                        oldRem.localMinute
                    )
                    val newOccKey =
                        "${itemId}_${oldRem.kind}_${oldRem.offsetMinutes}_${newScheduledAt}".take(128)
                    LocalReminderEntity(
                        id = oldRem.id,
                        itemId = itemId,
                        kind = oldRem.kind,
                        offsetMinutes = oldRem.offsetMinutes,
                        localHour = oldRem.localHour,
                        localMinute = oldRem.localMinute,
                        scheduledAtUtcMillis = newScheduledAt,
                        snoozedUntilUtcMillis = null,
                        occurrenceKey = newOccKey,
                        dismissed = false,
                        deliveredAtUtcMillis = oldRem.deliveredAtUtcMillis,
                        createdAtMillis = oldRem.createdAt?.toDate()?.time ?: now,
                        updatedAtMillis = now
                    )
                }
                if (rebuilt.isNotEmpty()) {
                    dao.upsertReminders(rebuilt)
                    rebuilt.forEach { rem ->
                        syncOps.add(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, rem.id, now))
                    }
                }
            }

            dao.upsertPendingSyncOps(syncOps)
        }

        if (removedPhotoPaths.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                PhotoAttachmentStore.deletePhotoFiles(removedPhotoPaths)
            }
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun updateItemImages(
        userId: String,
        itemId: String,
        imagePaths: List<String>
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        dao.updateItemImages(itemId, PhotoAttachmentStore.encodePaths(imagePaths), now)
    }

    override suspend fun updateItemAudioMemo(
        userId: String,
        itemId: String,
        audioMemoPath: String?,
        durationSeconds: Int
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        dao.updateItemAudioMemo(
            itemId = itemId,
            audioMemoPath = audioMemoPath?.takeIf { it.isNotBlank() },
            durationSeconds = durationSeconds.coerceAtLeast(0),
            now = now
        )
    }

    override suspend fun updateItemPriority(
        userId: String,
        itemId: String,
        priority: TaskPriority
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.updateItemPriority(itemId, priority.name, now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun updateItemCategory(
        userId: String,
        itemId: String,
        category: TaskCategory
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.updateItemCategory(itemId, category.name, now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun toggleItemCompleted(
        userId: String,
        itemId: String,
        completed: Boolean
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.updateItemCompleted(itemId, completed, now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun deleteItemCascade(
        userId: String,
        itemId: String,
        itemSteps: List<PlannerStep>,
        itemReminders: List<PlannerReminder>
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        var existingPhotos: List<String> = emptyList()
        var existingAudioMemo: String? = null

        inTransaction {
            val existingEntity = dao.getItemById(itemId)
            existingPhotos = PhotoAttachmentStore.parsePaths(existingEntity?.imagePathsCsv)
            existingAudioMemo = existingEntity?.audioMemoPath

            val stepIds = (dao.getStepsByItemId(itemId).map { it.id } +
                itemSteps.filter { it.itemId == itemId }.map { it.id }).distinct()
            val remIds = (dao.getRemindersByItemId(itemId).map { it.id } +
                itemReminders.filter { it.itemId == itemId }.map { it.id }).distinct()
            val linkedTasks = dao.getTasksLinkedToPlan(itemId)

            if (linkedTasks.isNotEmpty()) {
                dao.clearParentPlanId(itemId, now)
            }
            dao.deleteItemCascade(itemId)
            dao.upsertTombstone(
                DeletedTombstoneEntity(
                    opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, itemId),
                    entityType = SyncEntityType.ITEM,
                    entityId = itemId,
                    deletedAtMillis = now
                )
            )

            val syncOps = buildList {
                remIds.forEach { add(PendingSyncOperationEntity.delete(SyncEntityType.REMINDER, it, now)) }
                stepIds.forEach { add(PendingSyncOperationEntity.delete(SyncEntityType.STEP, it, now)) }
                linkedTasks.forEach { add(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, it.id, now)) }
                add(PendingSyncOperationEntity.delete(SyncEntityType.ITEM, itemId, now))
            }
            dao.upsertPendingSyncOps(syncOps)
        }

        // Delete disk media only after the DB transaction has committed cleanly
        withContext(Dispatchers.IO) {
            if (existingPhotos.isNotEmpty()) {
                PhotoAttachmentStore.deletePhotoFiles(existingPhotos)
            }
            TaskAudioMemoManager.deleteMemoFile(existingAudioMemo)
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun addStep(
        userId: String,
        itemId: String,
        title: String,
        dueDate: String?,
        dueTimeMinutes: Int?,
        sortOrder: Int
    ): Result<String> = suspendRunCatching {
        val cleanTitle = title.trim().take(200)
        require(cleanTitle.isNotBlank()) { "Step title cannot be empty." }
        val now = System.currentTimeMillis()
        val id = newId("step")
        inTransaction {
            val existingMaxOrder = dao.getStepsByItemId(itemId).maxOfOrNull { it.sortOrder }
            val resolvedOrder = maxOf(sortOrder, (existingMaxOrder ?: -1) + 1).coerceIn(0, 1000)
            dao.upsertStep(
                LocalStepEntity(
                    id = id,
                    itemId = itemId,
                    title = cleanTitle,
                    completed = false,
                    dueDate = sanitizeDateOrNull(dueDate),
                    dueTimeMinutes = dueTimeMinutes?.coerceIn(0, 1439),
                    sortOrder = resolvedOrder,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.STEP, id, now))
        }
        onMutationEnqueued?.invoke()
        id
    }

    override suspend fun toggleStepCompleted(
        userId: String,
        stepId: String,
        completed: Boolean
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.updateStepCompleted(stepId, completed, now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.STEP, stepId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun addStepTimeSpent(
        userId: String,
        stepId: String,
        addedMinutes: Int
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            val step = dao.getStepById(stepId) ?: error("Step not found.")
            val updatedTotal = (step.timeSpentMinutes + addedMinutes.coerceIn(1, 1440))
                .coerceIn(0, 100_000)
            dao.updateStepTimeSpent(stepId, updatedTotal, now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.STEP, stepId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun deleteStep(userId: String, stepId: String): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.deleteStepById(stepId)
            dao.upsertTombstone(
                DeletedTombstoneEntity(
                    opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.STEP, stepId),
                    entityType = SyncEntityType.STEP,
                    entityId = stepId,
                    deletedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.delete(SyncEntityType.STEP, stepId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun addReminder(
        userId: String,
        item: PlannerItem,
        kind: ReminderKind,
        offsetMinutes: Int,
        localHour: Int,
        localMinute: Int
    ): Result<String> = suspendRunCatching {
        val now = System.currentTimeMillis()
        val safeOffset = offsetMinutes.coerceIn(0, 525600)
        val safeHour = localHour.coerceIn(0, 23)
        val safeMin = localMinute.coerceIn(0, 59)
        val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
            item, safeOffset, safeHour, safeMin
        )
        val occKey = "${item.id}_${kind.name}_${safeOffset}_${scheduledAt}".take(128)
        val id = newId("rem")
        inTransaction {
            dao.upsertReminder(
                LocalReminderEntity(
                    id = id,
                    itemId = item.id,
                    kind = kind.name,
                    offsetMinutes = safeOffset,
                    localHour = safeHour,
                    localMinute = safeMin,
                    scheduledAtUtcMillis = scheduledAt,
                    snoozedUntilUtcMillis = null,
                    occurrenceKey = occKey,
                    dismissed = false,
                    deliveredAtUtcMillis = null,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, id, now))
        }
        onMutationEnqueued?.invoke()
        id
    }

    override suspend fun snoozeReminderOccurrence(
        userId: String,
        reminderId: String,
        snoozedUntilUtcMillis: Long
    ): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.snoozeReminder(reminderId, snoozedUntilUtcMillis.coerceAtLeast(0L), now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, reminderId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun dismissReminder(userId: String, reminderId: String): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.dismissReminder(reminderId, now)
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, reminderId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.deleteReminderById(reminderId)
            dao.upsertTombstone(
                DeletedTombstoneEntity(
                    opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.REMINDER, reminderId),
                    entityType = SyncEntityType.REMINDER,
                    entityId = reminderId,
                    deletedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.delete(SyncEntityType.REMINDER, reminderId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun createClassSchedule(
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
    ): Result<String> = suspendRunCatching {
        val cleanSubject = subject.trim().take(160)
        require(cleanSubject.isNotBlank()) { "Subject name is required." }
        val cleanDays = daysOfWeek.filter { it in 0..6 }.distinct().sorted()
        val cleanStart = CalendarDate.parse(semesterStartDate)?.toIsoString()
            ?: CalendarDate.today().toIsoString()
        val now = System.currentTimeMillis()
        val id = newId("cls")
        inTransaction {
            dao.upsertClassSchedule(
                LocalClassScheduleEntity(
                    id = id,
                    subject = cleanSubject,
                    room = room?.trim()?.take(160)?.takeIf { it.isNotEmpty() },
                    repeatMode = repeatMode.name,
                    daysOfWeekCsv = cleanDays.joinToString(","),
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                    startTimeMinutes = startTimeMinutes.coerceIn(0, 1439),
                    endTimeMinutes = endTimeMinutes.coerceIn(0, 1439),
                    semesterStartDate = cleanStart,
                    semesterMonths = semesterMonths.coerceIn(1, 12),
                    notifyEnabled = notifyEnabled,
                    notifyOffsetMinutes = notifyOffsetMinutes.coerceIn(0, 10080),
                    colorKey = colorKey.name,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.CLASS_SCHEDULE, id, now))
        }
        onMutationEnqueued?.invoke()
        id
    }

    override suspend fun updateClassSchedule(
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
    ): Result<Unit> = suspendRunCatching {
        require(scheduleId.isNotBlank()) { "Schedule ID must not be blank." }
        val cleanSubject = subject.trim().take(160)
        require(cleanSubject.isNotBlank()) { "Subject name is required." }
        val cleanDays = daysOfWeek.filter { it in 0..6 }.distinct().sorted()
        val cleanStart = CalendarDate.parse(semesterStartDate)?.toIsoString()
            ?: CalendarDate.today().toIsoString()
        val now = System.currentTimeMillis()
        inTransaction {
            val oldCreated = dao.getClassScheduleById(scheduleId)?.createdAtMillis ?: now
            dao.upsertClassSchedule(
                LocalClassScheduleEntity(
                    id = scheduleId,
                    subject = cleanSubject,
                    room = room?.trim()?.take(160)?.takeIf { it.isNotEmpty() },
                    repeatMode = repeatMode.name,
                    daysOfWeekCsv = cleanDays.joinToString(","),
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                    startTimeMinutes = startTimeMinutes.coerceIn(0, 1439),
                    endTimeMinutes = endTimeMinutes.coerceIn(0, 1439),
                    semesterStartDate = cleanStart,
                    semesterMonths = semesterMonths.coerceIn(1, 12),
                    notifyEnabled = notifyEnabled,
                    notifyOffsetMinutes = notifyOffsetMinutes.coerceIn(0, 10080),
                    colorKey = colorKey.name,
                    createdAtMillis = oldCreated,
                    updatedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(SyncEntityType.CLASS_SCHEDULE, scheduleId, now))
        }
        onMutationEnqueued?.invoke()
    }

    override suspend fun deleteClassSchedule(userId: String, scheduleId: String): Result<Unit> = suspendRunCatching {
        val now = System.currentTimeMillis()
        inTransaction {
            dao.deleteClassScheduleById(scheduleId)
            dao.upsertTombstone(
                DeletedTombstoneEntity(
                    opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.CLASS_SCHEDULE, scheduleId),
                    entityType = SyncEntityType.CLASS_SCHEDULE,
                    entityId = scheduleId,
                    deletedAtMillis = now
                )
            )
            dao.upsertPendingSyncOp(PendingSyncOperationEntity.delete(SyncEntityType.CLASS_SCHEDULE, scheduleId, now))
        }
        onMutationEnqueued?.invoke()
    }
}
