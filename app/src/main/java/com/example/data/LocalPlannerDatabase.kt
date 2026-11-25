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

object SyncEntityType {
    const val PROFILE = "PROFILE"
    const val ITEM = "ITEM"
    const val STEP = "STEP"
    const val REMINDER = "REMINDER"
    const val CLASS_SCHEDULE = "CLASS_SCHEDULE"
}

object SyncAction {
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"
}

@Entity(tableName = "pending_sync_ops")
data class PendingSyncOperationEntity(
    @PrimaryKey val opKey: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val queuedAtMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
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
                lastError = null
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
                lastError = null
            )
        }
    }
}

@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: String,
    val dedicationText: String,
    val hasSeenDedication: Boolean,
    val defaultReminderHour: Int,
    val defaultReminderMinute: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "local_items")
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
    val updatedAtMillis: Long
)

@Entity(tableName = "local_steps")
data class LocalStepEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val title: String,
    val completed: Boolean,
    val dueDate: String?,
    val dueTimeMinutes: Int?,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "local_reminders")
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

    @Query("SELECT * FROM pending_sync_ops ORDER BY queuedAtMillis ASC")
    suspend fun getAllPendingSyncOps(): List<PendingSyncOperationEntity>

    // Pending sync queue mutations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingSyncOp(op: PendingSyncOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingSyncOps(ops: List<PendingSyncOperationEntity>)

    @Query("DELETE FROM pending_sync_ops WHERE opKey = :opKey")
    suspend fun deletePendingSyncOp(opKey: String)

    @Query("UPDATE pending_sync_ops SET retryCount = retryCount + 1, lastError = :error WHERE opKey = :opKey")
    suspend fun markPendingSyncFailed(opKey: String, error: String)

    @Query("DELETE FROM pending_sync_ops")
    suspend fun clearAllPendingSyncOps()

    // Profile writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("UPDATE local_profile SET dedicationText = :text, hasSeenDedication = :seen, updatedAtMillis = :now WHERE id = :profileId")
    suspend fun updateDedication(profileId: String, text: String, seen: Boolean, now: Long)

    @Query("UPDATE local_profile SET defaultReminderHour = :hour, defaultReminderMinute = :minute, updatedAtMillis = :now WHERE id = :profileId")
    suspend fun updateDefaultReminderTime(profileId: String, hour: Int, minute: Int, now: Long)

    // Item writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: LocalItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<LocalItemEntity>)

    @Query("UPDATE local_items SET completed = :completed, updatedAtMillis = :now WHERE id = :itemId")
    suspend fun updateItemCompleted(itemId: String, completed: Boolean, now: Long)

    @Query("DELETE FROM local_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    // Step writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStep(step: LocalStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<LocalStepEntity>)

    @Query("UPDATE local_steps SET completed = :completed, updatedAtMillis = :now WHERE id = :stepId")
    suspend fun updateStepCompleted(stepId: String, completed: Boolean, now: Long)

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
     */
    @Transaction
    suspend fun reconcileCloudItems(remoteItems: List<LocalItemEntity>) {
        val pendingIds = getAllPendingSyncOps()
            .filter { it.entityType == SyncEntityType.ITEM }
            .map { it.entityId }
            .toSet()
        val remoteIds = remoteItems.map { it.id }.toSet()
        val toUpsert = remoteItems.filter { it.id !in pendingIds }
        if (toUpsert.isNotEmpty()) upsertItems(toUpsert)
        val localItems = getAllItems()
        for (local in localItems) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteItemById(local.id)
            }
        }
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
        for (local in localSteps) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteStepById(local.id)
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
        for (local in localReminders) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteReminderById(local.id)
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
        for (local in localSchedules) {
            if (local.id !in remoteIds && local.id !in pendingIds) {
                deleteClassScheduleById(local.id)
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
        clearClassSchedules()
        clearReminders()
        clearSteps()
        clearItems()
        clearProfiles()
    }

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
        PendingSyncOperationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LocalPlannerDatabase : RoomDatabase() {
    abstract fun plannerDao(): LocalPlannerDao

    companion object {
        @Volatile
        private var INSTANCE: LocalPlannerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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

        fun getInstance(context: Context): LocalPlannerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalPlannerDatabase::class.java,
                    "bunny_memo_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
