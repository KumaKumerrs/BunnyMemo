package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

import androidx.room.Index
import java.util.concurrent.atomic.AtomicLong

object SyncEntityType {
    const val PROFILE = "PROFILE"
    const val ITEM = "ITEM"
    const val STEP = "STEP"
    const val REMINDER = "REMINDER"
    const val CLASS_SCHEDULE = "CLASS_SCHEDULE"
    const val DAILY_HABIT = "DAILY_HABIT"
    const val HABIT_CHECKIN = "HABIT_CHECKIN"
}

object SyncAction {
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"
}

private object PendingSyncRevisionCounter {
    private val lastRevision = AtomicLong(System.currentTimeMillis() * 1000L)

    fun next(): Long {
        while (true) {
            val prev = lastRevision.get()
            val candidate = maxOf(prev + 1L, System.currentTimeMillis() * 1000L)
            if (lastRevision.compareAndSet(prev, candidate)) return candidate
        }
    }
}

@Entity(tableName = "pending_sync_ops")
data class PendingSyncOperationEntity(
    @PrimaryKey val opKey: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val queuedAtMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val revision: Long = PendingSyncRevisionCounter.next()
) {
    companion object {
        fun keyFor(entityType: String, entityId: String): String = "$entityType:$entityId"

        fun upsert(entityType: String, entityId: String, nowMillis: Long = System.currentTimeMillis()): PendingSyncOperationEntity {
            return PendingSyncOperationEntity(
                opKey = keyFor(entityType, entityId),
                entityType = entityType,
                entityId = entityId,
                action = SyncAction.UPSERT,
                queuedAtMillis = nowMillis,
                retryCount = 0,
                lastError = null,
                revision = PendingSyncRevisionCounter.next()
            )
        }

        fun delete(entityType: String, entityId: String, nowMillis: Long = System.currentTimeMillis()): PendingSyncOperationEntity {
            return PendingSyncOperationEntity(
                opKey = keyFor(entityType, entityId),
                entityType = entityType,
                entityId = entityId,
                action = SyncAction.DELETE,
                queuedAtMillis = nowMillis,
                retryCount = 0,
                lastError = null,
                revision = PendingSyncRevisionCounter.next()
            )
        }
    }
}

@Entity(tableName = "deleted_tombstones")
data class DeletedTombstoneEntity(
    @PrimaryKey val opKey: String,
    val entityType: String,
    val entityId: String,
    val deletedAtMillis: Long
)

@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: String,
    val dedicationText: String,
    val hasSeenDedication: Boolean,
    val defaultReminderHour: Int,
    val defaultReminderMinute: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hasCustomLocalEdits: Boolean = false
)

@Entity(
    tableName = "local_items",
    indices = [Index(value = ["parentPlanId"])]
)
data class LocalItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val notes: String?,
    val completed: Boolean,
    val startDate: String?,
    val dueDate: String?,
    val dueTimeMinutes: Int?,
    val startAtUtcMillis: Long?,
    val endAtUtcMillis: Long?,
    val parentPlanId: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val imagePathsCsv: String? = null,
    val priority: String = TaskPriority.MEDIUM.name,
    val category: String = TaskCategory.PERSONAL.name,
    val audioMemoPath: String? = null,
    val audioMemoDurationSeconds: Int = 0
)

@Entity(
    tableName = "local_steps",
    indices = [Index(value = ["itemId"])]
)
data class LocalStepEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val title: String,
    val completed: Boolean,
    val dueDate: String?,
    val dueTimeMinutes: Int?,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val timeSpentMinutes: Int = 0
)

@Entity(
    tableName = "local_reminders",
    indices = [Index(value = ["itemId"])]
)
data class LocalReminderEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val kind: String,
    val offsetMinutes: Int,
    val localHour: Int,
    val localMinute: Int,
    val scheduledAtUtcMillis: Long,
    val snoozedUntilUtcMillis: Long?,
    val occurrenceKey: String,
    val dismissed: Boolean,
    val deliveredAtUtcMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "local_class_schedules")
data class LocalClassScheduleEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val room: String?,
    val repeatMode: String,
    val daysOfWeekCsv: String,
    val dayOfMonth: Int,
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
    val semesterStartDate: String,
    val semesterMonths: Int,
    val notifyEnabled: Boolean,
    val notifyOffsetMinutes: Int,
    val colorKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "local_daily_habits")
data class LocalDailyHabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "local_habit_checkins",
    indices = [Index(value = ["habitId"])]
)
data class LocalHabitCheckInEntity(
    @PrimaryKey val checkInKey: String, // "$habitId|$dateIso"
    val habitId: String,
    val dateIso: String,
    val completedAtMillis: Long
)

@Dao
interface LocalPlannerDao {

    // Reactive Flows
    @Query("SELECT * FROM local_profile WHERE id = :profileId LIMIT 1")
    fun observeProfile(profileId: String): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_items ORDER BY createdAtMillis ASC")
    fun observeItems(): Flow<List<LocalItemEntity>>

    @Query("SELECT * FROM local_steps ORDER BY sortOrder ASC, createdAtMillis ASC")
    fun observeSteps(): Flow<List<LocalStepEntity>>

    @Query("SELECT * FROM local_reminders ORDER BY scheduledAtUtcMillis ASC")
    fun observeReminders(): Flow<List<LocalReminderEntity>>

    @Query("SELECT * FROM local_class_schedules ORDER BY startTimeMinutes ASC, createdAtMillis ASC")
    fun observeClassSchedules(): Flow<List<LocalClassScheduleEntity>>

    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM pending_sync_ops ORDER BY queuedAtMillis ASC")
    fun observePendingSyncOps(): Flow<List<PendingSyncOperationEntity>>

    // Daily habits & check-ins reactive flows
    @Query("SELECT * FROM local_daily_habits ORDER BY sortOrder ASC, createdAtMillis ASC")
    fun observeDailyHabits(): Flow<List<LocalDailyHabitEntity>>

    @Query("SELECT * FROM local_habit_checkins ORDER BY dateIso DESC, completedAtMillis DESC")
    fun observeHabitCheckIns(): Flow<List<LocalHabitCheckInEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyHabit(habit: LocalDailyHabitEntity)

    @Query("DELETE FROM local_daily_habits WHERE id = :habitId")
    suspend fun deleteDailyHabitById(habitId: String)

    @Query("DELETE FROM local_habit_checkins WHERE habitId = :habitId")
    suspend fun deleteCheckInsByHabitId(habitId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitCheckIn(checkIn: LocalHabitCheckInEntity)

    @Query("DELETE FROM local_habit_checkins WHERE checkInKey = :checkInKey")
    suspend fun deleteHabitCheckInByKey(checkInKey: String)

    @Query("SELECT * FROM local_habit_checkins WHERE checkInKey = :checkInKey LIMIT 1")
    suspend fun getHabitCheckIn(checkInKey: String): LocalHabitCheckInEntity?

    @Transaction
    suspend fun deleteDailyHabitCascade(habitId: String) {
        deleteCheckInsByHabitId(habitId)
        deleteDailyHabitById(habitId)
    }

    // One-shot reads for sync engine, migration & checks
    @Query("SELECT * FROM local_profile WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: String): LocalProfileEntity?

    @Query("SELECT * FROM local_items")
    suspend fun getAllItems(): List<LocalItemEntity>

    @Query("SELECT * FROM local_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): LocalItemEntity?

    @Query("SELECT * FROM local_steps")
    suspend fun getAllSteps(): List<LocalStepEntity>

    @Query("SELECT * FROM local_steps WHERE id = :stepId LIMIT 1")
    suspend fun getStepById(stepId: String): LocalStepEntity?

    @Query("SELECT * FROM local_steps WHERE itemId = :itemId")
    suspend fun getStepsByItemId(itemId: String): List<LocalStepEntity>

    @Query("SELECT * FROM local_reminders")
    suspend fun getAllReminders(): List<LocalReminderEntity>

    @Query("SELECT * FROM local_reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: String): LocalReminderEntity?

    @Query("SELECT * FROM local_reminders WHERE itemId = :itemId")
    suspend fun getRemindersByItemId(itemId: String): List<LocalReminderEntity>

    @Query("SELECT * FROM local_class_schedules")
    suspend fun getAllClassSchedules(): List<LocalClassScheduleEntity>

    @Query("SELECT * FROM local_class_schedules WHERE id = :scheduleId LIMIT 1")
    suspend fun getClassScheduleById(scheduleId: String): LocalClassScheduleEntity?

    @Query("SELECT * FROM local_daily_habits ORDER BY sortOrder ASC, createdAtMillis ASC")
    suspend fun getAllDailyHabits(): List<LocalDailyHabitEntity>

    @Query("SELECT * FROM local_daily_habits WHERE id = :habitId LIMIT 1")
    suspend fun getDailyHabitById(habitId: String): LocalDailyHabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyHabits(habits: List<LocalDailyHabitEntity>)

    @Query("SELECT * FROM local_habit_checkins ORDER BY dateIso DESC, completedAtMillis DESC")
    suspend fun getAllHabitCheckIns(): List<LocalHabitCheckInEntity>

    @Query("SELECT * FROM local_habit_checkins WHERE habitId = :habitId")
    suspend fun getHabitCheckInsByHabitId(habitId: String): List<LocalHabitCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitCheckIns(checkIns: List<LocalHabitCheckInEntity>)

    @Query("SELECT MAX(sortOrder) FROM local_daily_habits")
    suspend fun getMaxHabitSortOrder(): Int?

    @Query("SELECT * FROM pending_sync_ops ORDER BY queuedAtMillis ASC")
    suspend fun getAllPendingSyncOps(): List<PendingSyncOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    suspend fun getPendingSyncCount(): Int

    // Pending sync queue mutations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingSyncOp(op: PendingSyncOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingSyncOps(ops: List<PendingSyncOperationEntity>)

    @Query("DELETE FROM pending_sync_ops WHERE opKey = :opKey")
    suspend fun deletePendingSyncOp(opKey: String)

    @Query("DELETE FROM pending_sync_ops WHERE opKey = :opKey AND revision <= :pushedRevision")
    suspend fun deletePendingSyncOpIfUnchanged(opKey: String, pushedRevision: Long)

    @Query("UPDATE pending_sync_ops SET retryCount = retryCount + 1, lastError = :error WHERE opKey = :opKey")
    suspend fun markPendingSyncFailed(opKey: String, error: String)

    @Query("DELETE FROM pending_sync_ops")
    suspend fun clearAllPendingSyncOps()

    // Tombstone tracking (prevents offline edits from resurrecting remotely deleted docs)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: DeletedTombstoneEntity)

    @Query("SELECT * FROM deleted_tombstones WHERE opKey = :opKey LIMIT 1")
    suspend fun getTombstone(opKey: String): DeletedTombstoneEntity?

    @Query("DELETE FROM deleted_tombstones WHERE opKey = :opKey")
    suspend fun deleteTombstone(opKey: String)

    @Query("DELETE FROM deleted_tombstones")
    suspend fun clearTombstones()

    // Profile writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("UPDATE local_profile SET dedicationText = :text, hasSeenDedication = :seen, hasCustomLocalEdits = :customEdits, updatedAtMillis = :now WHERE id = :profileId")
    suspend fun updateDedication(profileId: String, text: String, seen: Boolean, customEdits: Boolean, now: Long)

    @Query("UPDATE local_profile SET defaultReminderHour = :hour, defaultReminderMinute = :minute, hasCustomLocalEdits = 1, updatedAtMillis = :now WHERE id = :profileId")
    suspend fun updateDefaultReminderTime(profileId: String, hour: Int, minute: Int, now: Long)

    // Item writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: LocalItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<LocalItemEntity>)

    @Query("UPDATE local_items SET completed = :completed, updatedAtMillis = :now WHERE id = :itemId")
    suspend fun updateItemCompleted(itemId: String, completed: Boolean, now: Long)

    @Query("UPDATE local_items SET priority = :priority, updatedAtMillis = :now WHERE id = :itemId")
    suspend fun updateItemPriority(itemId: String, priority: String, now: Long)

    @Query("UPDATE local_items SET category = :category, updatedAtMillis = :now WHERE id = :itemId")
    suspend fun updateItemCategory(itemId: String, category: String, now: Long)

    @Query("UPDATE local_items SET imagePathsCsv = :imagePathsCsv, updatedAtMillis = :now WHERE id = :itemId")
    suspend fun updateItemImages(itemId: String, imagePathsCsv: String?, now: Long)

    @Query("UPDATE local_items SET audioMemoPath = :audioMemoPath, audioMemoDurationSeconds = :durationSeconds, updatedAtMillis = :now WHERE id = :itemId")
    suspend fun updateItemAudioMemo(itemId: String, audioMemoPath: String?, durationSeconds: Int, now: Long)

    @Query("SELECT * FROM local_items WHERE parentPlanId = :planId")
    suspend fun getTasksLinkedToPlan(planId: String): List<LocalItemEntity>

    @Query("UPDATE local_items SET parentPlanId = NULL, updatedAtMillis = :now WHERE parentPlanId = :planId")
    suspend fun clearParentPlanId(planId: String, now: Long)

    @Query("DELETE FROM local_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    // Step writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStep(step: LocalStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<LocalStepEntity>)

    @Query("UPDATE local_steps SET completed = :completed, updatedAtMillis = :now WHERE id = :stepId")
    suspend fun updateStepCompleted(stepId: String, completed: Boolean, now: Long)

    @Query("UPDATE local_steps SET timeSpentMinutes = :minutes, updatedAtMillis = :now WHERE id = :stepId")
    suspend fun updateStepTimeSpent(stepId: String, minutes: Int, now: Long)

    @Query("DELETE FROM local_steps WHERE id = :stepId")
    suspend fun deleteStepById(stepId: String)

    @Query("DELETE FROM local_steps WHERE itemId = :itemId")
    suspend fun deleteStepsByItemId(itemId: String)

    // Reminder writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: LocalReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminders(reminders: List<LocalReminderEntity>)

    @Query("UPDATE local_reminders SET snoozedUntilUtcMillis = :snoozeUntil, dismissed = 0, updatedAtMillis = :now WHERE id = :reminderId")
    suspend fun snoozeReminder(reminderId: String, snoozeUntil: Long, now: Long)

    @Query("UPDATE local_reminders SET dismissed = 1, updatedAtMillis = :now WHERE id = :reminderId")
    suspend fun dismissReminder(reminderId: String, now: Long)

    @Query("DELETE FROM local_reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    @Query("DELETE FROM local_reminders WHERE itemId = :itemId")
    suspend fun deleteRemindersByItemId(itemId: String)

    // Class schedule writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassSchedule(schedule: LocalClassScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassSchedules(schedules: List<LocalClassScheduleEntity>)

    @Query("DELETE FROM local_class_schedules WHERE id = :scheduleId")
    suspend fun deleteClassScheduleById(scheduleId: String)

    // Transactional helpers
    @Transaction
    suspend fun insertItemBundle(
        item: LocalItemEntity,
        steps: List<LocalStepEntity>,
        reminders: List<LocalReminderEntity>
    ) {
        upsertItem(item)
        if (steps.isNotEmpty()) upsertSteps(steps)
        if (reminders.isNotEmpty()) upsertReminders(reminders)
    }

    @Transaction
    suspend fun deleteItemCascade(itemId: String) {
        deleteStepsByItemId(itemId)
        deleteRemindersByItemId(itemId)
        deleteItemById(itemId)
    }

    /**
     * Reconciles a cloud snapshot of items into Room without clobbering any item
     * that currently has an unsynced pending local change in [pending_sync_ops].
     * Returns the list of locally removed items so the caller can clean up orphaned media files.
     */
    @Transaction
    suspend fun reconcileCloudItems(remoteItems: List<LocalItemEntity>): List<LocalItemEntity> {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.ITEM }
            .map { it.entityId }
            .toSet()
        val existingById = getAllItems().associateBy { it.id }
        val remoteIds = remoteItems.map { it.id }.toSet()
        val toUpsert = remoteItems
            .filter { it.id !in pendingIds }
            .map { remote ->
                val existing = existingById[remote.id]
                val preservedPhotos = existing?.imagePathsCsv
                val preservedAudioPath = existing?.audioMemoPath
                val preservedAudioDuration = existing?.audioMemoDurationSeconds ?: 0
                remote.copy(
                    imagePathsCsv = if (!preservedPhotos.isNullOrBlank() && remote.imagePathsCsv.isNullOrBlank()) preservedPhotos else remote.imagePathsCsv,
                    audioMemoPath = if (!preservedAudioPath.isNullOrBlank() && remote.audioMemoPath.isNullOrBlank()) preservedAudioPath else remote.audioMemoPath,
                    audioMemoDurationSeconds = if (preservedAudioDuration > 0 && remote.audioMemoDurationSeconds == 0) preservedAudioDuration else remote.audioMemoDurationSeconds
                )
            }
        if (toUpsert.isNotEmpty()) upsertItems(toUpsert)
        val removed = mutableListOf<LocalItemEntity>()
        val now = System.currentTimeMillis()
        for (local in existingById.values) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteItemById(local.id)
                upsertTombstone(
                    DeletedTombstoneEntity(
                        opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.ITEM, local.id),
                        entityType = SyncEntityType.ITEM,
                        entityId = local.id,
                        deletedAtMillis = now
                    )
                )
                removed.add(local)
            }
        }
        return removed
    }

    @Transaction
    suspend fun reconcileCloudSteps(remoteSteps: List<LocalStepEntity>) {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.STEP }
            .map { it.entityId }
            .toSet()
        val remoteIds = remoteSteps.map { it.id }.toSet()
        val toUpsert = remoteSteps.filter { it.id !in pendingIds }
        if (toUpsert.isNotEmpty()) upsertSteps(toUpsert)
        val localSteps = getAllSteps()
        val now = System.currentTimeMillis()
        for (local in localSteps) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteStepById(local.id)
                upsertTombstone(
                    DeletedTombstoneEntity(
                        opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.STEP, local.id),
                        entityType = SyncEntityType.STEP,
                        entityId = local.id,
                        deletedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun reconcileCloudReminders(remoteReminders: List<LocalReminderEntity>) {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.REMINDER }
            .map { it.entityId }
            .toSet()
        val remoteIds = remoteReminders.map { it.id }.toSet()
        val toUpsert = remoteReminders.filter { it.id !in pendingIds }
        if (toUpsert.isNotEmpty()) upsertReminders(toUpsert)
        val localReminders = getAllReminders()
        val now = System.currentTimeMillis()
        for (local in localReminders) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteReminderById(local.id)
                upsertTombstone(
                    DeletedTombstoneEntity(
                        opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.REMINDER, local.id),
                        entityType = SyncEntityType.REMINDER,
                        entityId = local.id,
                        deletedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun reconcileCloudClassSchedules(remoteSchedules: List<LocalClassScheduleEntity>) {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.CLASS_SCHEDULE }
            .map { it.entityId }
            .toSet()
        val remoteIds = remoteSchedules.map { it.id }.toSet()
        val toUpsert = remoteSchedules.filter { it.id !in pendingIds }
        if (toUpsert.isNotEmpty()) upsertClassSchedules(toUpsert)
        val localSchedules = getAllClassSchedules()
        val now = System.currentTimeMillis()
        for (local in localSchedules) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteClassScheduleById(local.id)
                upsertTombstone(
                    DeletedTombstoneEntity(
                        opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.CLASS_SCHEDULE, local.id),
                        entityType = SyncEntityType.CLASS_SCHEDULE,
                        entityId = local.id,
                        deletedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun reconcileCloudDailyHabits(remoteHabits: List<LocalDailyHabitEntity>) {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.DAILY_HABIT }
            .map { it.entityId }
            .toSet()
        val remoteIds = remoteHabits.map { it.id }.toSet()
        val toUpsert = remoteHabits.filter { it.id !in pendingIds }
        if (toUpsert.isNotEmpty()) upsertDailyHabits(toUpsert)
        val localHabits = getAllDailyHabits()
        val now = System.currentTimeMillis()
        for (local in localHabits) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteDailyHabitById(local.id)
                upsertTombstone(
                    DeletedTombstoneEntity(
                        opKey = PendingSyncOperationEntity.keyFor(SyncEntityType.DAILY_HABIT, local.id),
                        entityType = SyncEntityType.DAILY_HABIT,
                        entityId = local.id,
                        deletedAtMillis = now
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun reconcileCloudHabitCheckIns(remoteCheckIns: List<LocalHabitCheckInEntity>) {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.HABIT_CHECKIN }
            .map { it.entityId }
            .toSet()
        val remoteKeys = remoteCheckIns.map { it.checkInKey }.toSet()
        val toUpsert = remoteCheckIns.filter { it.checkInKey !in pendingIds }
        if (toUpsert.isNotEmpty()) upsertHabitCheckIns(toUpsert)
        val localCheckIns = getAllHabitCheckIns()
        for (local in localCheckIns) {
            if (local.checkInKey !in remoteKeys && local.checkInKey !in pendingIds) {
                deleteHabitCheckInByKey(local.checkInKey)
            }
        }
    }

    @Transaction
    suspend fun reconcileCloudProfile(remoteProfile: LocalProfileEntity) {
        val hasPendingProfile = getAllPendingSyncOps()
            .any { it.entityType == SyncEntityType.PROFILE }
        if (!hasPendingProfile) {
            upsertProfile(remoteProfile)
        }
    }

    @Transaction
    suspend fun clearAllLocalPlannerData() {
        clearAllPendingSyncOps()
        clearTombstones()
        clearHabitCheckIns()
        clearDailyHabits()
        clearClassSchedules()
        clearReminders()
        clearSteps()
        clearItems()
        clearProfiles()
    }

    @Query("DELETE FROM local_habit_checkins")
    suspend fun clearHabitCheckIns()

    @Query("DELETE FROM local_daily_habits")
    suspend fun clearDailyHabits()

    @Query("DELETE FROM local_class_schedules")
    suspend fun clearClassSchedules()

    @Query("DELETE FROM local_reminders")
    suspend fun clearReminders()

    @Query("DELETE FROM local_steps")
    suspend fun clearSteps()

    @Query("DELETE FROM local_items")
    suspend fun clearItems()

    @Query("DELETE FROM local_profile")
    suspend fun clearProfiles()
}

@Database(
    entities = [
        LocalProfileEntity::class,
        LocalItemEntity::class,
        LocalStepEntity::class,
        LocalReminderEntity::class,
        LocalClassScheduleEntity::class,
        PendingSyncOperationEntity::class,
        DeletedTombstoneEntity::class,
        LocalDailyHabitEntity::class,
        LocalHabitCheckInEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class LocalPlannerDatabase : RoomDatabase() {
    abstract fun plannerDao(): LocalPlannerDao

    companion object {
        @Volatile
        private var INSTANCE: LocalPlannerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_class_schedules (
                        id TEXT NOT NULL PRIMARY KEY,
                        subject TEXT NOT NULL,
                        room TEXT,
                        repeatMode TEXT NOT NULL,
                        daysOfWeekCsv TEXT NOT NULL,
                        dayOfMonth INTEGER NOT NULL,
                        startTimeMinutes INTEGER NOT NULL,
                        endTimeMinutes INTEGER NOT NULL,
                        semesterStartDate TEXT NOT NULL,
                        semesterMonths INTEGER NOT NULL,
                        notifyEnabled INTEGER NOT NULL,
                        notifyOffsetMinutes INTEGER NOT NULL,
                        colorKey TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_sync_ops (
                        opKey TEXT NOT NULL PRIMARY KEY,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        queuedAtMillis INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String
        ) {
            if (!hasColumn(db, tableName, columnName)) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinition")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "local_items", "imagePathsCsv", "TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_daily_habits (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        colorKey TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_habit_checkins (
                        checkInKey TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        dateIso TEXT NOT NULL,
                        completedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "local_items", "priority", "TEXT NOT NULL DEFAULT 'MEDIUM'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "local_items", "category", "TEXT NOT NULL DEFAULT 'PERSONAL'")
                addColumnIfMissing(db, "local_steps", "timeSpentMinutes", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "local_items", "audioMemoPath", "TEXT")
                addColumnIfMissing(db, "local_items", "audioMemoDurationSeconds", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "pending_sync_ops", "revision", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "local_profile", "hasCustomLocalEdits", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deleted_tombstones (
                        opKey TEXT NOT NULL PRIMARY KEY,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        deletedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_items_parentPlanId ON local_items(parentPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_steps_itemId ON local_steps(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_reminders_itemId ON local_reminders(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_habit_checkins_habitId ON local_habit_checkins(habitId)")
            }
        }

        fun allMigrations(): Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )

        fun getInstance(context: Context): LocalPlannerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalPlannerDatabase::class.java,
                    "bunny_memo_local.db"
                )
                    .addMigrations(*allMigrations())
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
