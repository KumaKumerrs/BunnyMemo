package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PlannerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : PlannerDataSource {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Signed-in user changed; refusing cross-user access.")
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun itemsCol(uid: String) = userDoc(uid).collection("items")
    private fun stepsCol(uid: String) = userDoc(uid).collection("steps")
    private fun remindersCol(uid: String) = userDoc(uid).collection("reminders")
    private fun classSchedulesCol(uid: String) = userDoc(uid).collection("classSchedules")

    // --- Realtime Observations ---

    override fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    override fun observeItems(userId: String): Flow<List<PlannerItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/items"
        emitAll(
            itemsCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlannerItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    override fun observeSteps(userId: String): Flow<List<PlannerStep>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/steps"
        emitAll(
            stepsCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlannerStep::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    override fun observeReminders(userId: String): Flow<List<PlannerReminder>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/reminders"
        emitAll(
            remindersCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlannerReminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    override fun observeClassSchedules(userId: String): Flow<List<ClassScheduleItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/classSchedules"
        emitAll(
            classSchedulesCol(uid).snapshots()
                .map { snap ->
                    snap.toObjects(
                        ClassScheduleItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.startTimeMinutes }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    // --- Profile & Dedication ---

    override suspend fun ensureProfileExists(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = userDoc(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "dedicationText" to UserProfile.DEFAULT_DEDICATION,
                    "hasSeenDedication" to false,
                    "defaultReminderHour" to 9,
                    "defaultReminderMinute" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveDedication(
        userId: String,
        dedicationText: String,
        markSeen: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleaned = dedicationText.trim().take(500).ifBlank { UserProfile.DEFAULT_DEDICATION }
            val docRef = userDoc(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dedicationText" to cleaned,
                        "hasSeenDedication" to markSeen,
                        "defaultReminderHour" to 9,
                        "defaultReminderMinute" to 0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "dedicationText" to cleaned,
                        "hasSeenDedication" to markSeen,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateDefaultReminderTime(
        userId: String,
        hour: Int,
        minute: Int
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeHour = hour.coerceIn(0, 23)
            val safeMinute = minute.coerceIn(0, 59)
            userDoc(uid).update(
                mapOf(
                    "defaultReminderHour" to safeHour,
                    "defaultReminderMinute" to safeMinute,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
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
    ): Result<String> {
        var path = "users/$userId/items"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotBlank()) { "Title is required." }

            val itemRef = itemsCol(uid).document()
            val itemId = itemRef.id
            path = itemRef.path

            val cleanNotes = notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val cleanParent = parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK }

            val itemPayload = buildMap<String, Any> {
                put("userId", uid)
                put("type", type.name)
                put("title", cleanTitle)
                if (cleanNotes != null) put("notes", cleanNotes)
                put("completed", false)
                if (!startDate.isNullOrBlank()) put("startDate", startDate)
                if (!dueDate.isNullOrBlank()) put("dueDate", dueDate)
                if (dueTimeMinutes != null) put("dueTimeMinutes", dueTimeMinutes.coerceIn(0, 1439))
                if (startAtUtcMillis != null) put("startAtUtcMillis", startAtUtcMillis.coerceAtLeast(0L))
                if (endAtUtcMillis != null) put("endAtUtcMillis", endAtUtcMillis.coerceAtLeast(0L))
                if (cleanParent != null) put("parentPlanId", cleanParent)
                put("priority", priority.name)
                put("category", category.name)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val draftItem = PlannerItem(
                id = itemId,
                userId = uid,
                type = type.name,
                title = cleanTitle,
                notes = cleanNotes,
                completed = false,
                startDate = startDate,
                dueDate = dueDate,
                dueTimeMinutes = dueTimeMinutes,
                startAtUtcMillis = startAtUtcMillis,
                endAtUtcMillis = endAtUtcMillis,
                parentPlanId = cleanParent,
                priority = priority.name,
                category = category.name
            )

            val batch = db.batch()
            batch.set(itemRef, itemPayload)

            steps.filter { it.title.isNotBlank() }.take(50).forEachIndexed { idx, step ->
                val stepRef = stepsCol(uid).document()
                val stepPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("itemId", itemId)
                    put("title", step.title.trim().take(200))
                    put("completed", false)
                    if (!step.dueDate.isNullOrBlank()) put("dueDate", step.dueDate)
                    if (step.dueTimeMinutes != null) {
                        put("dueTimeMinutes", step.dueTimeMinutes.coerceIn(0, 1439))
                    }
                    put("sortOrder", idx)
                    put("timeSpentMinutes", 0)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.set(stepRef, stepPayload)
            }

            reminders.take(10).forEach { rem ->
                val remRef = remindersCol(uid).document()
                val safeOffset = rem.offsetMinutes.coerceIn(0, 525600)
                val safeHour = rem.localHour.coerceIn(0, 23)
                val safeMin = rem.localMinute.coerceIn(0, 59)
                val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                    draftItem, safeOffset, safeHour, safeMin
                )
                val occKey = "${itemId}_${rem.kind.name}_${safeOffset}_${scheduledAt}".take(128)
                val remPayload = mapOf(
                    "userId" to uid,
                    "itemId" to itemId,
                    "kind" to rem.kind.name,
                    "offsetMinutes" to safeOffset,
                    "localHour" to safeHour,
                    "localMinute" to safeMin,
                    "scheduledAtUtcMillis" to scheduledAt,
                    "occurrenceKey" to occKey,
                    "dismissed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(remRef, remPayload)
            }

            batch.commit().await()
            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Edits/reschedules an item and automatically rebuilds pending reminders for the new schedule.
     */
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
    ): Result<Unit> {
        val path = "users/$userId/items/$itemId"
        return try {
            val uid = requireUserId(userId)
            require(itemId.isNotBlank()) { "Item ID must not be blank." }
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotBlank()) { "Title is required." }

            val cleanNotes = notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val cleanParent = parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK }

            val updateMap = mutableMapOf<String, Any>(
                "type" to type.name,
                "title" to cleanTitle,
                "completed" to completed,
                "notes" to (cleanNotes ?: FieldValue.delete()),
                "startDate" to (startDate?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                "dueDate" to (dueDate?.takeIf { it.isNotBlank() } ?: FieldValue.delete()),
                "dueTimeMinutes" to (dueTimeMinutes?.coerceIn(0, 1439) ?: FieldValue.delete()),
                "startAtUtcMillis" to (startAtUtcMillis?.coerceAtLeast(0L) ?: FieldValue.delete()),
                "endAtUtcMillis" to (endAtUtcMillis?.coerceAtLeast(0L) ?: FieldValue.delete()),
                "parentPlanId" to (cleanParent ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (priority != null) {
                updateMap["priority"] = priority.name
            }
            if (category != null) {
                updateMap["category"] = category.name
            }

            val updatedItem = PlannerItem(
                id = itemId,
                userId = uid,
                type = type.name,
                title = cleanTitle,
                notes = cleanNotes,
                completed = completed,
                startDate = startDate,
                dueDate = dueDate,
                dueTimeMinutes = dueTimeMinutes,
                startAtUtcMillis = startAtUtcMillis,
                endAtUtcMillis = endAtUtcMillis,
                parentPlanId = cleanParent,
                priority = (priority ?: TaskPriority.MEDIUM).name,
                category = (category ?: TaskCategory.PERSONAL).name
            )

            val batch = db.batch()
            batch.update(itemsCol(uid).document(itemId), updateMap)

            if (updatedReminderDrafts != null) {
                // Replace reminders cleanly when edited from the full editor
                existingReminders.filter { it.itemId == itemId }.forEach { oldRem ->
                    batch.delete(remindersCol(uid).document(oldRem.id))
                }
                updatedReminderDrafts.take(10).forEach { rem ->
                    val remRef = remindersCol(uid).document()
                    val safeOffset = rem.offsetMinutes.coerceIn(0, 525600)
                    val safeHour = rem.localHour.coerceIn(0, 23)
                    val safeMin = rem.localMinute.coerceIn(0, 59)
                    val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                        updatedItem, safeOffset, safeHour, safeMin
                    )
                    val occKey = "${itemId}_${rem.kind.name}_${safeOffset}_${scheduledAt}".take(128)
                    batch.set(
                        remRef,
                        mapOf(
                            "userId" to uid,
                            "itemId" to itemId,
                            "kind" to rem.kind.name,
                            "offsetMinutes" to safeOffset,
                            "localHour" to safeHour,
                            "localMinute" to safeMin,
                            "scheduledAtUtcMillis" to scheduledAt,
                            "occurrenceKey" to occKey,
                            "dismissed" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else {
                // Rebuild existing reminders in place on reschedule, clearing stale snoozes
                existingReminders.filter { it.itemId == itemId }.forEach { oldRem ->
                    val newScheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                        updatedItem,
                        oldRem.offsetMinutes,
                        oldRem.localHour,
                        oldRem.localMinute
                    )
                    val newOccKey =
                        "${itemId}_${oldRem.kind}_${oldRem.offsetMinutes}_${newScheduledAt}".take(128)
                    batch.update(
                        remindersCol(uid).document(oldRem.id),
                        mapOf(
                            "scheduledAtUtcMillis" to newScheduledAt,
                            "snoozedUntilUtcMillis" to FieldValue.delete(),
                            "occurrenceKey" to newOccKey,
                            "dismissed" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateItemImages(
        userId: String,
        itemId: String,
        imagePaths: List<String>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateItemAudioMemo(
        userId: String,
        itemId: String,
        audioMemoPath: String?,
        durationSeconds: Int
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateItemPriority(
        userId: String,
        itemId: String,
        priority: TaskPriority
    ): Result<Unit> {
        val path = "users/$userId/items/$itemId"
        return try {
            val uid = requireUserId(userId)
            itemsCol(uid).document(itemId).update(
                mapOf(
                    "priority" to priority.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateItemCategory(
        userId: String,
        itemId: String,
        category: TaskCategory
    ): Result<Unit> {
        val path = "users/$userId/items/$itemId"
        return try {
            val uid = requireUserId(userId)
            itemsCol(uid).document(itemId).update(
                mapOf(
                    "category" to category.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleItemCompleted(
        userId: String,
        itemId: String,
        completed: Boolean
    ): Result<Unit> {
        val path = "users/$userId/items/$itemId"
        return try {
            val uid = requireUserId(userId)
            itemsCol(uid).document(itemId).update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteItemCascade(
        userId: String,
        itemId: String,
        itemSteps: List<PlannerStep>,
        itemReminders: List<PlannerReminder>
    ): Result<Unit> {
        val path = "users/$userId/items/$itemId"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            itemSteps.filter { it.itemId == itemId }.forEach { step ->
                batch.delete(stepsCol(uid).document(step.id))
            }
            itemReminders.filter { it.itemId == itemId }.forEach { rem ->
                batch.delete(remindersCol(uid).document(rem.id))
            }
            batch.delete(itemsCol(uid).document(itemId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Steps (Milestones & Preparation Checklist) ---

    override suspend fun addStep(
        userId: String,
        itemId: String,
        title: String,
        dueDate: String?,
        dueTimeMinutes: Int?,
        sortOrder: Int
    ): Result<String> {
        var path = "users/$userId/steps"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotBlank()) { "Step title cannot be empty." }
            val stepRef = stepsCol(uid).document()
            path = stepRef.path
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("itemId", itemId)
                put("title", cleanTitle)
                put("completed", false)
                if (!dueDate.isNullOrBlank()) put("dueDate", dueDate)
                if (dueTimeMinutes != null) put("dueTimeMinutes", dueTimeMinutes.coerceIn(0, 1439))
                put("sortOrder", sortOrder.coerceIn(0, 1000))
                put("timeSpentMinutes", 0)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            stepRef.set(payload).await()
            Result.success(stepRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleStepCompleted(
        userId: String,
        stepId: String,
        completed: Boolean
    ): Result<Unit> {
        val path = "users/$userId/steps/$stepId"
        return try {
            val uid = requireUserId(userId)
            stepsCol(uid).document(stepId).update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun addStepTimeSpent(
        userId: String,
        stepId: String,
        addedMinutes: Int
    ): Result<Unit> {
        val path = "users/$userId/steps/$stepId"
        return try {
            val uid = requireUserId(userId)
            val docRef = stepsCol(uid).document(stepId)
            val snap = docRef.get().await()
            val current = snap.getLong("timeSpentMinutes")?.toInt() ?: 0
            val updated = (current + addedMinutes.coerceIn(1, 1440)).coerceIn(0, 100_000)
            docRef.update(
                mapOf(
                    "timeSpentMinutes" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteStep(userId: String, stepId: String): Result<Unit> {
        val path = "users/$userId/steps/$stepId"
        return try {
            val uid = requireUserId(userId)
            stepsCol(uid).document(stepId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Reminders ---

    override suspend fun addReminder(
        userId: String,
        item: PlannerItem,
        kind: ReminderKind,
        offsetMinutes: Int,
        localHour: Int,
        localMinute: Int
    ): Result<String> {
        var path = "users/$userId/reminders"
        return try {
            val uid = requireUserId(userId)
            val safeOffset = offsetMinutes.coerceIn(0, 525600)
            val safeHour = localHour.coerceIn(0, 23)
            val safeMin = localMinute.coerceIn(0, 59)
            val scheduledAt = PlannerDateUtils.computeScheduledAtUtcMillis(
                item, safeOffset, safeHour, safeMin
            )
            val occKey = "${item.id}_${kind.name}_${safeOffset}_${scheduledAt}".take(128)
            val remRef = remindersCol(uid).document()
            path = remRef.path
            remRef.set(
                mapOf(
                    "userId" to uid,
                    "itemId" to item.id,
                    "kind" to kind.name,
                    "offsetMinutes" to safeOffset,
                    "localHour" to safeHour,
                    "localMinute" to safeMin,
                    "scheduledAtUtcMillis" to scheduledAt,
                    "occurrenceKey" to occKey,
                    "dismissed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(remRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun snoozeReminderOccurrence(
        userId: String,
        reminderId: String,
        snoozedUntilUtcMillis: Long
    ): Result<Unit> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId(userId)
            remindersCol(uid).document(reminderId).update(
                mapOf(
                    "snoozedUntilUtcMillis" to snoozedUntilUtcMillis.coerceAtLeast(0L),
                    "dismissed" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun dismissReminder(userId: String, reminderId: String): Result<Unit> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId(userId)
            remindersCol(uid).document(reminderId).update(
                mapOf(
                    "dismissed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId(userId)
            remindersCol(uid).document(reminderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Class Schedules ---

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
        var path = "users/$userId/classSchedules"
        return try {
            val uid = requireUserId(userId)
            val cleanSubject = subject.trim().take(160)
            require(cleanSubject.isNotBlank()) { "Subject name is required." }
            val cleanRoom = room?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val cleanDays = daysOfWeek.filter { it in 0..6 }.distinct().sorted()
            val cleanStart = CalendarDate.parse(semesterStartDate)?.toIsoString()
                ?: CalendarDate.today().toIsoString()

            val docRef = classSchedulesCol(uid).document()
            path = docRef.path
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("subject", cleanSubject)
                if (cleanRoom != null) put("room", cleanRoom)
                put("repeatMode", repeatMode.name)
                put("daysOfWeek", cleanDays)
                put("dayOfMonth", dayOfMonth.coerceIn(1, 31))
                put("startTimeMinutes", startTimeMinutes.coerceIn(0, 1439))
                put("endTimeMinutes", endTimeMinutes.coerceIn(0, 1439))
                put("semesterStartDate", cleanStart)
                put("semesterMonths", semesterMonths.coerceIn(1, 12))
                put("notifyEnabled", notifyEnabled)
                put("notifyOffsetMinutes", notifyOffsetMinutes.coerceIn(0, 10080))
                put("colorKey", colorKey.name)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
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
        val path = "users/$userId/classSchedules/$scheduleId"
        return try {
            val uid = requireUserId(userId)
            require(scheduleId.isNotBlank()) { "Schedule ID must not be blank." }
            val cleanSubject = subject.trim().take(160)
            require(cleanSubject.isNotBlank()) { "Subject name is required." }
            val cleanRoom = room?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val cleanDays = daysOfWeek.filter { it in 0..6 }.distinct().sorted()
            val cleanStart = CalendarDate.parse(semesterStartDate)?.toIsoString()
                ?: CalendarDate.today().toIsoString()

            val updateMap = mapOf<String, Any>(
                "subject" to cleanSubject,
                "room" to (cleanRoom ?: FieldValue.delete()),
                "repeatMode" to repeatMode.name,
                "daysOfWeek" to cleanDays,
                "dayOfMonth" to dayOfMonth.coerceIn(1, 31),
                "startTimeMinutes" to startTimeMinutes.coerceIn(0, 1439),
                "endTimeMinutes" to endTimeMinutes.coerceIn(0, 1439),
                "semesterStartDate" to cleanStart,
                "semesterMonths" to semesterMonths.coerceIn(1, 12),
                "notifyEnabled" to notifyEnabled,
                "notifyOffsetMinutes" to notifyOffsetMinutes.coerceIn(0, 10080),
                "colorKey" to colorKey.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            classSchedulesCol(uid).document(scheduleId).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteClassSchedule(userId: String, scheduleId: String): Result<Unit> {
        val path = "users/$userId/classSchedules/$scheduleId"
        return try {
            val uid = requireUserId(userId)
            classSchedulesCol(uid).document(scheduleId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Imports any memos created in local guest mode into the newly signed-in user's private
     * Firestore account, preserving parent-plan links, milestones, reminders, and dedication note.
     */
    suspend fun importLocalDataToCloud(
        userId: String,
        snapshot: LocalMigrationSnapshot
    ): Result<Int> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            if (!snapshot.hasUserContent) return Result.success(0)

            // 1. Ensure cloud profile exists, carrying over custom dedication / reminder time if brand new
            val docRef = userDoc(uid)
            val existingProfile = docRef.get().await()
            val localProfile = snapshot.profile
            if (!existingProfile.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dedicationText" to (localProfile?.dedicationText?.trim()?.take(500)
                            ?.ifBlank { UserProfile.DEFAULT_DEDICATION } ?: UserProfile.DEFAULT_DEDICATION),
                        "hasSeenDedication" to (localProfile?.hasSeenDedication ?: true),
                        "defaultReminderHour" to (localProfile?.defaultReminderHour ?: 9).coerceIn(0, 23),
                        "defaultReminderMinute" to (localProfile?.defaultReminderMinute ?: 0).coerceIn(0, 59),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // 2. Upload items first (plans before linked tasks) so steps/reminders pass existsAfter checks
            val validItemIds = mutableSetOf<String>()
            snapshot.items.chunked(200).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { item ->
                    val cleanTitle = item.title.trim().take(200).ifBlank { return@forEach }
                    val itemRef = itemsCol(uid).document(item.id)
                    validItemIds.add(item.id)
                    val payload = buildMap<String, Any> {
                        put("userId", uid)
                        put("type", ItemType.fromRaw(item.type).name)
                        put("title", cleanTitle)
                        item.notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }?.let { put("notes", it) }
                        put("completed", item.completed)
                        item.startDate?.takeIf { it.isNotBlank() }?.let { put("startDate", it) }
                        item.dueDate?.takeIf { it.isNotBlank() }?.let { put("dueDate", it) }
                        item.dueTimeMinutes?.let { put("dueTimeMinutes", it.coerceIn(0, 1439)) }
                        item.startAtUtcMillis?.let { put("startAtUtcMillis", it.coerceAtLeast(0L)) }
                        item.endAtUtcMillis?.let { put("endAtUtcMillis", it.coerceAtLeast(0L)) }
                        item.parentPlanId?.takeIf { it.isNotBlank() && item.type == ItemType.TASK.name }
                            ?.let { put("parentPlanId", it) }
                        put("priority", TaskPriority.fromRaw(item.priority).name)
                        put("category", TaskCategory.fromRaw(item.category).name)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.set(itemRef, payload)
                }
                batch.commit().await()
            }

            // 3. Upload steps referencing migrated items
            snapshot.steps
                .filter { it.itemId in validItemIds && it.title.isNotBlank() }
                .chunked(200)
                .forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { step ->
                        val stepRef = stepsCol(uid).document(step.id)
                        val payload = buildMap<String, Any> {
                            put("userId", uid)
                            put("itemId", step.itemId)
                            put("title", step.title.trim().take(200))
                            put("completed", step.completed)
                            step.dueDate?.takeIf { it.isNotBlank() }?.let { put("dueDate", it) }
                            step.dueTimeMinutes?.let { put("dueTimeMinutes", it.coerceIn(0, 1439)) }
                            put("sortOrder", step.sortOrder.coerceIn(0, 1000))
                            put("timeSpentMinutes", step.timeSpentMinutes.coerceIn(0, 100_000))
                            put("createdAt", FieldValue.serverTimestamp())
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                        batch.set(stepRef, payload)
                    }
                    batch.commit().await()
                }

            // 4. Upload reminders referencing migrated items
            snapshot.reminders
                .filter { it.itemId in validItemIds }
                .chunked(200)
                .forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { rem ->
                        val remRef = remindersCol(uid).document(rem.id)
                        val occKey = rem.occurrenceKey.ifBlank {
                            "${rem.itemId}_${rem.kind}_${rem.offsetMinutes}_${rem.scheduledAtUtcMillis}"
                        }.take(128)
                        val payload = buildMap<String, Any> {
                            put("userId", uid)
                            put("itemId", rem.itemId)
                            put("kind", ReminderKind.fromRaw(rem.kind).name)
                            put("offsetMinutes", rem.offsetMinutes.coerceIn(0, 525600))
                            put("localHour", rem.localHour.coerceIn(0, 23))
                            put("localMinute", rem.localMinute.coerceIn(0, 59))
                            put("scheduledAtUtcMillis", rem.scheduledAtUtcMillis.coerceAtLeast(0L))
                            rem.snoozedUntilUtcMillis?.let { put("snoozedUntilUtcMillis", it.coerceAtLeast(0L)) }
                            put("occurrenceKey", occKey)
                            put("dismissed", rem.dismissed)
                            rem.deliveredAtUtcMillis?.let { put("deliveredAtUtcMillis", it.coerceAtLeast(0L)) }
                            put("createdAt", FieldValue.serverTimestamp())
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                        batch.set(remRef, payload)
                    }
                    batch.commit().await()
                }

            // 5. Upload local semester class schedules
            var migratedClasses = 0
            snapshot.classSchedules
                .filter { it.subject.isNotBlank() }
                .chunked(200)
                .forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { cls ->
                        val clsRef = classSchedulesCol(uid).document(cls.id)
                        val cleanSubject = cls.subject.trim().take(160).ifBlank { return@forEach }
                        val cleanDays = cls.daysOfWeekCsv
                            .split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 0..6 }
                            .distinct()
                            .sorted()
                        val cleanStart = CalendarDate.parse(cls.semesterStartDate)?.toIsoString()
                            ?: CalendarDate.today().toIsoString()
                        val payload = buildMap<String, Any> {
                            put("userId", uid)
                            put("subject", cleanSubject)
                            cls.room?.trim()?.take(160)?.takeIf { it.isNotEmpty() }?.let { put("room", it) }
                            put("repeatMode", ClassRepeatMode.fromRaw(cls.repeatMode).name)
                            put("daysOfWeek", cleanDays)
                            put("dayOfMonth", cls.dayOfMonth.coerceIn(1, 31))
                            put("startTimeMinutes", cls.startTimeMinutes.coerceIn(0, 1439))
                            put("endTimeMinutes", cls.endTimeMinutes.coerceIn(0, 1439))
                            put("semesterStartDate", cleanStart)
                            put("semesterMonths", cls.semesterMonths.coerceIn(1, 12))
                            put("notifyEnabled", cls.notifyEnabled)
                            put("notifyOffsetMinutes", cls.notifyOffsetMinutes.coerceIn(0, 10080))
                            put("colorKey", ClassColorKey.fromRaw(cls.colorKey).name)
                            put("createdAt", FieldValue.serverTimestamp())
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                        batch.set(clsRef, payload)
                        migratedClasses++
                    }
                    batch.commit().await()
                }

            Result.success(validItemIds.size + migratedClasses)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
