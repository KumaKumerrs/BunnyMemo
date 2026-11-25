package com.example.data

import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

data class LocalMigrationSnapshot(
    val profile: LocalProfileEntity?,
    val items: List<LocalItemEntity>,
    val steps: List<LocalStepEntity>,
    val reminders: List<LocalReminderEntity>,
    val classSchedules: List<LocalClassScheduleEntity> = emptyList()
) {
    val hasUserContent: Boolean
        get() = items.isNotEmpty() ||
            classSchedules.isNotEmpty() ||
            (profile != null && (profile.hasSeenDedication || profile.dedicationText != UserProfile.DEFAULT_DEDICATION))
}

/**
 * Offline-first Room implementation of [PlannerDataSource].
 * Every write persists immediately to Room and records a [PendingSyncOperationEntity] in the
 * local outbox (`pending_sync_ops`) so [RoomFirestoreSyncService] can push pending local changes
 * to Firestore automatically whenever signed in and a network connection is available/restored.
 */
class LocalPlannerRepository(
    private val dao: LocalPlannerDao,
    private val onMutationEnqueued: (() -> Unit)? = null
) : PlannerDataSource {

    companion object {
        const val LOCAL_USER_ID = "local_guest"
    }

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(20)}"

    private fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

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

    private suspend fun enqueueUpsert(entityType: String, entityId: String, now: Long) {
        dao.upsertPendingSyncOp(PendingSyncOperationEntity.upsert(entityType, entityId, now))
        onMutationEnqueued?.invoke()
    }

    private suspend fun enqueueDelete(entityType: String, entityId: String, now: Long) {
        dao.upsertPendingSyncOp(PendingSyncOperationEntity.delete(entityType, entityId, now))
        onMutationEnqueued?.invoke()
    }

    private suspend fun enqueueBatch(ops: List<PendingSyncOperationEntity>) {
        if (ops.isEmpty()) return
        dao.upsertPendingSyncOps(ops)
        onMutationEnqueued?.invoke()
    }

    // --- Migration & Backfill helpers ---

    suspend fun readMigrationSnapshot(): LocalMigrationSnapshot {
        return LocalMigrationSnapshot(
            profile = dao.getProfile(LOCAL_USER_ID),
            items = dao.getAllItems(),
            steps = dao.getAllSteps(),
            reminders = dao.getAllReminders(),
            classSchedules = dao.getAllClassSchedules()
        )
    }

    /**
     * Ensures any pre-existing local records (e.g., created in guest mode before sync outbox
     * existed) are queued as pending UPSERTs so [RoomFirestoreSyncService] pushes them cleanly.
     */
    suspend fun enqueueUnqueuedLocalDataForSync() {
        val now = System.currentTimeMillis()
        val existingKeys = dao.getAllPendingSyncOps().map { it.opKey }.toSet()
        val ops = mutableListOf<PendingSyncOperationEntity>()

        val profile = dao.getProfile(LOCAL_USER_ID)
        if (profile != null &&
            (profile.hasSeenDedication || profile.dedicationText != UserProfile.DEFAULT_DEDICATION ||
                profile.defaultReminderHour != 9 || profile.defaultReminderMinute != 0)
        ) {
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

        if (ops.isNotEmpty()) {
            dao.upsertPendingSyncOps(ops)
        }
    }

    suspend fun clearAllAfterMigration() {
        dao.clearAllLocalPlannerData()
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

    override suspend fun ensureProfileExists(userId: String): Result<Unit> = runCatching {
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
                    updatedAtMillis = now
                )
            )
        }
    }

    override suspend fun saveDedication(
        userId: String,
        dedicationText: String,
        markSeen: Boolean
    ): Result<Unit> = runCatching {
        val cleaned = dedicationText.trim().take(500).ifBlank { UserProfile.DEFAULT_DEDICATION }
        val now = System.currentTimeMillis()
        val existing = dao.getProfile(LOCAL_USER_ID)
        if (existing == null) {
            dao.upsertProfile(
                LocalProfileEntity(
                    id = LOCAL_USER_ID,
                    dedicationText = cleaned,
                    hasSeenDedication = markSeen,
                    defaultReminderHour = 9,
                    defaultReminderMinute = 0,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        } else {
            dao.updateDedication(LOCAL_USER_ID, cleaned, markSeen, now)
        }
        enqueueUpsert(SyncEntityType.PROFILE, LOCAL_USER_ID, now)
    }

    override suspend fun updateDefaultReminderTime(
        userId: String,
        hour: Int,
        minute: Int
    ): Result<Unit> = runCatching {
        ensureProfileExists(LOCAL_USER_ID)
        val now = System.currentTimeMillis()
        dao.updateDefaultReminderTime(
            profileId = LOCAL_USER_ID,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            now = now
        )
        enqueueUpsert(SyncEntityType.PROFILE, LOCAL_USER_ID, now)
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
        reminders: List<ReminderDraft>
    ): Result<String> {
        return try {
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotBlank()) { "Title is required." }

            val now = System.currentTimeMillis()
            val itemId = newId("item")
            val cleanNotes = notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val cleanParent = parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK }

            val itemEntity = LocalItemEntity(
                id = itemId,
                type = type.name,
                title = cleanTitle,
                notes = cleanNotes,
                completed = false,
                startDate = startDate?.takeIf { it.isNotBlank() },
                dueDate = dueDate?.takeIf { it.isNotBlank() },
                dueTimeMinutes = dueTimeMinutes?.coerceIn(0, 1439),
                startAtUtcMillis = startAtUtcMillis?.coerceAtLeast(0L),
                endAtUtcMillis = endAtUtcMillis?.coerceAtLeast(0L),
                parentPlanId = cleanParent,
                createdAtMillis = now,
                updatedAtMillis = now
            )

            val draftItem = itemEntity.toModel(userId.ifBlank { LOCAL_USER_ID })

            val stepEntities = steps.filter { it.title.isNotBlank() }.take(50).mapIndexed { idx, step ->
                LocalStepEntity(
                    id = newId("step"),
                    itemId = itemId,
                    title = step.title.trim().take(200),
                    completed = false,
                    dueDate = step.dueDate?.takeIf { it.isNotBlank() },
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

            dao.insertItemBundle(itemEntity, stepEntities, remEntities)

            val syncOps = buildList {
                add(PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now))
                stepEntities.forEach { add(PendingSyncOperationEntity.upsert(SyncEntityType.STEP, it.id, now)) }
                remEntities.forEach { add(PendingSyncOperationEntity.upsert(SyncEntityType.REMINDER, it.id, now)) }
            }
            enqueueBatch(syncOps)

            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        updatedReminderDrafts: List<ReminderDraft>?
    ): Result<Unit> {
        return try {
            require(itemId.isNotBlank()) { "Item ID must not be blank." }
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotBlank()) { "Title is required." }

            val now = System.currentTimeMillis()
            val cleanNotes = notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val cleanParent = parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK }
            val oldCreated = dao.getItemById(itemId)?.createdAtMillis ?: now

            val updatedEntity = LocalItemEntity(
                id = itemId,
                type = type.name,
                title = cleanTitle,
                notes = cleanNotes,
                completed = completed,
                startDate = startDate?.takeIf { it.isNotBlank() },
                dueDate = dueDate?.takeIf { it.isNotBlank() },
                dueTimeMinutes = dueTimeMinutes?.coerceIn(0, 1439),
                startAtUtcMillis = startAtUtcMillis?.coerceAtLeast(0L),
                endAtUtcMillis = endAtUtcMillis?.coerceAtLeast(0L),
                parentPlanId = cleanParent,
                createdAtMillis = oldCreated,
                updatedAtMillis = now
            )
            val updatedModel = updatedEntity.toModel(userId.ifBlank { LOCAL_USER_ID })

            dao.upsertItem(updatedEntity)
            val syncOps = mutableListOf(
                PendingSyncOperationEntity.upsert(SyncEntityType.ITEM, itemId, now)
            )

            if (updatedReminderDrafts != null) {
                val oldRemIds = (dao.getRemindersByItemId(itemId).map { it.id } +
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
                val rebuilt = existingReminders.filter { it.itemId == itemId }.map { oldRem ->
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

            enqueueBatch(syncOps)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleItemCompleted(
        userId: String,
        itemId: String,
        completed: Boolean
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.updateItemCompleted(itemId, completed, now)
        enqueueUpsert(SyncEntityType.ITEM, itemId, now)
    }

    override suspend fun deleteItemCascade(
        userId: String,
        itemId: String,
        itemSteps: List<PlannerStep>,
        itemReminders: List<PlannerReminder>
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val stepIds = (dao.getStepsByItemId(itemId).map { it.id } +
            itemSteps.filter { it.itemId == itemId }.map { it.id }).distinct()
        val remIds = (dao.getRemindersByItemId(itemId).map { it.id } +
            itemReminders.filter { it.itemId == itemId }.map { it.id }).distinct()

        dao.deleteItemCascade(itemId)

        val syncOps = buildList {
            remIds.forEach { add(PendingSyncOperationEntity.delete(SyncEntityType.REMINDER, it, now)) }
            stepIds.forEach { add(PendingSyncOperationEntity.delete(SyncEntityType.STEP, it, now)) }
            add(PendingSyncOperationEntity.delete(SyncEntityType.ITEM, itemId, now))
        }
        enqueueBatch(syncOps)
    }

    override suspend fun addStep(
        userId: String,
        itemId: String,
        title: String,
        dueDate: String?,
        dueTimeMinutes: Int?,
        sortOrder: Int
    ): Result<String> = runCatching {
        val cleanTitle = title.trim().take(200)
        require(cleanTitle.isNotBlank()) { "Step title cannot be empty." }
        val now = System.currentTimeMillis()
        val id = newId("step")
        dao.upsertStep(
            LocalStepEntity(
                id = id,
                itemId = itemId,
                title = cleanTitle,
                completed = false,
                dueDate = dueDate?.takeIf { it.isNotBlank() },
                dueTimeMinutes = dueTimeMinutes?.coerceIn(0, 1439),
                sortOrder = sortOrder.coerceIn(0, 1000),
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        enqueueUpsert(SyncEntityType.STEP, id, now)
        id
    }

    override suspend fun toggleStepCompleted(
        userId: String,
        stepId: String,
        completed: Boolean
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.updateStepCompleted(stepId, completed, now)
        enqueueUpsert(SyncEntityType.STEP, stepId, now)
    }

    override suspend fun deleteStep(userId: String, stepId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.deleteStepById(stepId)
        enqueueDelete(SyncEntityType.STEP, stepId, now)
    }

    override suspend fun addReminder(
        userId: String,
        item: PlannerItem,
        kind: ReminderKind,
        offsetMinutes: Int,
        localHour: Int,
        localMinute: Int
    ): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val safeOffset = offsetMinutes.coerceIn(0, 525600)
        val safeHour = localHour.coerceIn(0, 23)
        val safeMin = localMinute.coerceIn(0, 59)
        val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
            item, safeOffset, safeHour, safeMin
        )
        val occKey = "${item.id}_${kind.name}_${safeOffset}_${scheduledAt}".take(128)
        val id = newId("rem")
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
        enqueueUpsert(SyncEntityType.REMINDER, id, now)
        id
    }

    override suspend fun snoozeReminderOccurrence(
        userId: String,
        reminderId: String,
        snoozedUntilUtcMillis: Long
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.snoozeReminder(reminderId, snoozedUntilUtcMillis.coerceAtLeast(0L), now)
        enqueueUpsert(SyncEntityType.REMINDER, reminderId, now)
    }

    override suspend fun dismissReminder(userId: String, reminderId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.dismissReminder(reminderId, now)
        enqueueUpsert(SyncEntityType.REMINDER, reminderId, now)
    }

    override suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.deleteReminderById(reminderId)
        enqueueDelete(SyncEntityType.REMINDER, reminderId, now)
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
    ): Result<String> {
        return try {
            val cleanSubject = subject.trim().take(160)
            require(cleanSubject.isNotBlank()) { "Subject name is required." }
            val cleanDays = daysOfWeek.filter { it in 0..6 }.distinct().sorted()
            val cleanStart = CalendarDate.parse(semesterStartDate)?.toIsoString()
                ?: CalendarDate.today().toIsoString()
            val now = System.currentTimeMillis()
            val id = newId("cls")
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
            enqueueUpsert(SyncEntityType.CLASS_SCHEDULE, id, now)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    ): Result<Unit> {
        return try {
            require(scheduleId.isNotBlank()) { "Schedule ID must not be blank." }
            val cleanSubject = subject.trim().take(160)
            require(cleanSubject.isNotBlank()) { "Subject name is required." }
            val cleanDays = daysOfWeek.filter { it in 0..6 }.distinct().sorted()
            val cleanStart = CalendarDate.parse(semesterStartDate)?.toIsoString()
                ?: CalendarDate.today().toIsoString()
            val now = System.currentTimeMillis()
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
            enqueueUpsert(SyncEntityType.CLASS_SCHEDULE, scheduleId, now)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteClassSchedule(userId: String, scheduleId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.deleteClassScheduleById(scheduleId)
        enqueueDelete(SyncEntityType.CLASS_SCHEDULE, scheduleId, now)
    }

    override suspend fun seedWeekendScrapbookSample(
        userId: String,
        defaultHour: Int,
        defaultMinute: Int
    ): Result<String> {
        val today = CalendarDate.today()
        val dueDate = today.plusDays(3)
        return createItemWithStepsAndReminders(
            userId = userId.ifBlank { LOCAL_USER_ID },
            type = ItemType.PLAN,
            title = "Make a weekend scrapbook",
            notes = "Keep our favorite little snapshots and ticket stubs together.",
            startDate = today.toIsoString(),
            dueDate = dueDate.toIsoString(),
            dueTimeMinutes = 17 * 60,
            startAtUtcMillis = null,
            endAtUtcMillis = null,
            parentPlanId = null,
            steps = listOf(
                StepDraft(title = "Choose photos", dueDate = today.toIsoString()),
                StepDraft(title = "Print photos", dueDate = today.plusDays(1).toIsoString()),
                StepDraft(title = "Write a note", dueDate = today.plusDays(2).toIsoString())
            ),
            reminders = listOf(
                ReminderDraft(
                    kind = ReminderKind.PROGRESS_CHECKIN,
                    offsetMinutes = 1440,
                    localHour = defaultHour,
                    localMinute = defaultMinute
                ),
                ReminderDraft(
                    kind = ReminderKind.ORDINARY,
                    offsetMinutes = 60,
                    localHour = defaultHour,
                    localMinute = defaultMinute
                )
            )
        )
    }
}
