package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.R
import com.example.widgets.WidgetUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

enum class SyncPhase {
    LOCAL_ONLY,
    OFFLINE_QUEUED,
    OFFLINE_IDLE,
    SYNCING,
    SYNCED,
    ERROR
}

data class RoomSyncStatus(
    val phase: SyncPhase = SyncPhase.LOCAL_ONLY,
    val isOnline: Boolean = true,
    val isSignedIn: Boolean = false,
    val pendingCount: Int = 0,
    val lastSyncedAtMillis: Long? = null,
    val lastSyncedCount: Int = 0,
    val lastErrorMessage: String? = null
)

/**
 * Local Room-to-Firestore synchronization service.
 *
 * - Observes live Android connectivity via [ConnectivityManager.NetworkCallback].
 * - Automatically detects when network connectivity is restored and immediately pushes all
 *   queued local Room mutations (`pending_sync_ops`) to the signed-in user's private Firestore
 *   database in dependency-safe order (matching `firestore.rules` 1:1).
 * - Mirrors remote Firestore snapshots back into Room when signed in without overwriting any
 *   entity that has an unsynced local edit waiting in the outbox.
 */
class RoomFirestoreSyncService private constructor(
    private val appContext: Context
) {
    private val dao: LocalPlannerDao = LocalPlannerDatabase.getInstance(appContext).plannerDao()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    )
    private val auth: FirebaseAuth = Firebase.auth
    private val cloudRepository: PlannerRepository = PlannerRepository(firestore, auth)
    private val connectivityManager: ConnectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var wasOffline: Boolean = false

    @Volatile
    private var isSyncingNow: Boolean = false

    @Volatile
    private var lastError: String? = null

    private var cloudMirrorJobs: MutableList<Job> = mutableListOf()
    private var retryJob: Job? = null

    private val _syncStatus = MutableStateFlow(
        RoomSyncStatus(
            phase = SyncPhase.LOCAL_ONLY,
            isOnline = checkCurrentConnectivity(),
            isSignedIn = false,
            pendingCount = 0
        )
    )
    val syncStatus: StateFlow<RoomSyncStatus> = _syncStatus.asStateFlow()

    private val _syncEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val syncEvents: SharedFlow<String> = _syncEvents.asSharedFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handleConnectivityChanged(isNowOnline = checkCurrentConnectivity())
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            handleConnectivityChanged(isNowOnline = hasInternet || checkCurrentConnectivity())
        }

        override fun onLost(network: Network) {
            handleConnectivityChanged(isNowOnline = checkCurrentConnectivity())
        }

        override fun onUnavailable() {
            handleConnectivityChanged(isNowOnline = false)
        }
    }

    init {
        val initiallyOnline = checkCurrentConnectivity()
        wasOffline = !initiallyOnline
        registerNetworkMonitor()
        observePendingQueueCount()
    }

    /**
     * Returns an offline-first [PlannerDataSource] backed by Room that notifies this sync service
     * whenever a local write is persisted.
     */
    fun createOfflineFirstDataSource(): LocalPlannerRepository {
        return LocalPlannerRepository(
            dao = dao,
            onMutationEnqueued = {
                WidgetUpdateManager.refreshAllWidgets(appContext)
                requestSync(restoredFromOffline = false)
            }
        )
    }

    /**
     * Activates automatic cloud synchronization for an authenticated Google user.
     * Queues any pre-existing guest records, immediately pushes pending Room changes if online,
     * and mirrors remote Firestore changes into Room safely.
     */
    fun startCloudSyncForUser(userId: String) {
        val currentAuthUid = auth.currentUser?.uid
        if (userId.isBlank() || currentAuthUid != userId) {
            Log.w(TAG, "Ignoring startCloudSyncForUser: auth UID mismatch or not signed in.")
            return
        }
        val changedUser = activeUserId != userId
        activeUserId = userId
        lastError = null
        refreshStatus()

        if (changedUser) {
            stopCloudMirrorJobs()
            scope.launch {
                try {
                    // Ensure any pre-existing guest items/schedules are queued before cloud mirror reconciles
                    LocalPlannerRepository(dao).enqueueUnqueuedLocalDataForSync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed backfilling local records into sync outbox: ${e.message}")
                }
                if (checkCurrentConnectivity()) {
                    syncPendingChangesInternal(restoredFromOffline = false)
                }
                startCloudMirrorJobs(userId)
            }
        } else {
            requestSync(restoredFromOffline = false)
        }
    }

    /**
     * Stops cloud listeners when the user signs out while keeping Room usable offline/locally.
     */
    fun stopCloudSync() {
        activeUserId = null
        lastError = null
        retryJob?.cancel()
        retryJob = null
        stopCloudMirrorJobs()
        refreshStatus()
    }

    /**
     * Triggers an immediate synchronization attempt if signed in and online.
     */
    fun requestSync(restoredFromOffline: Boolean = false) {
        scope.launch {
            syncPendingChangesInternal(restoredFromOffline = restoredFromOffline)
        }
    }

    private fun registerNetworkMonitor() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register NetworkCallback: ${e.message}")
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        return try {
            val active = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    private fun handleConnectivityChanged(isNowOnline: Boolean) {
        val previouslyOffline = wasOffline
        wasOffline = !isNowOnline
        refreshStatus(overrideOnline = isNowOnline)

        if (isNowOnline && (previouslyOffline || _syncStatus.value.pendingCount > 0)) {
            Log.i(TAG, "Network connection restored — triggering automatic Room-to-Firestore sync.")
            requestSync(restoredFromOffline = previouslyOffline)
        }
    }

    private fun observePendingQueueCount() {
        scope.launch {
            dao.observePendingSyncCount().collectLatest { count ->
                refreshStatus(overridePendingCount = count)
            }
        }
    }

    private fun stopCloudMirrorJobs() {
        cloudMirrorJobs.forEach { it.cancel() }
        cloudMirrorJobs.clear()
    }

    private fun startCloudMirrorJobs(userId: String) {
        stopCloudMirrorJobs()

        cloudMirrorJobs += scope.launch {
            cloudRepository.observeProfile(userId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud profile mirror paused: ${e.message}")
                }
                .collect { remote ->
                    if (remote != null && activeUserId == userId) {
                        val now = System.currentTimeMillis()
                        dao.reconcileCloudProfile(
                            LocalProfileEntity(
                                id = LocalPlannerRepository.LOCAL_USER_ID,
                                dedicationText = remote.dedicationText,
                                hasSeenDedication = remote.hasSeenDedication,
                                defaultReminderHour = remote.defaultReminderHour,
                                defaultReminderMinute = remote.defaultReminderMinute,
                                createdAtMillis = remote.createdAt?.toDate()?.time ?: now,
                                updatedAtMillis = remote.updatedAt?.toDate()?.time ?: now
                            )
                        )
                    }
                }
        }

        cloudMirrorJobs += scope.launch {
            cloudRepository.observeItems(userId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud items mirror paused: ${e.message}")
                }
                .collect { remoteList ->
                    if (activeUserId == userId) {
                        val now = System.currentTimeMillis()
                        dao.reconcileCloudItems(
                            remoteList.map { item ->
                                LocalItemEntity(
                                    id = item.id,
                                    type = item.type,
                                    title = item.title,
                                    notes = item.notes,
                                    completed = item.completed,
                                    startDate = item.startDate,
                                    dueDate = item.dueDate,
                                    dueTimeMinutes = item.dueTimeMinutes,
                                    startAtUtcMillis = item.startAtUtcMillis,
                                    endAtUtcMillis = item.endAtUtcMillis,
                                    parentPlanId = item.parentPlanId,
                                    createdAtMillis = item.createdAt?.toDate()?.time ?: now,
                                    updatedAtMillis = item.updatedAt?.toDate()?.time ?: now
                                )
                            }
                        )
                        WidgetUpdateManager.refreshAllWidgets(appContext)
                    }
                }
        }

        cloudMirrorJobs += scope.launch {
            cloudRepository.observeSteps(userId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud steps mirror paused: ${e.message}")
                }
                .collect { remoteList ->
                    if (activeUserId == userId) {
                        val now = System.currentTimeMillis()
                        dao.reconcileCloudSteps(
                            remoteList.map { step ->
                                LocalStepEntity(
                                    id = step.id,
                                    itemId = step.itemId,
                                    title = step.title,
                                    completed = step.completed,
                                    dueDate = step.dueDate,
                                    dueTimeMinutes = step.dueTimeMinutes,
                                    sortOrder = step.sortOrder,
                                    createdAtMillis = step.createdAt?.toDate()?.time ?: now,
                                    updatedAtMillis = step.updatedAt?.toDate()?.time ?: now
                                )
                            }
                        )
                        WidgetUpdateManager.refreshAllWidgets(appContext)
                    }
                }
        }

        cloudMirrorJobs += scope.launch {
            cloudRepository.observeReminders(userId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud reminders mirror paused: ${e.message}")
                }
                .collect { remoteList ->
                    if (activeUserId == userId) {
                        val now = System.currentTimeMillis()
                        dao.reconcileCloudReminders(
                            remoteList.map { rem ->
                                LocalReminderEntity(
                                    id = rem.id,
                                    itemId = rem.itemId,
                                    kind = rem.kind,
                                    offsetMinutes = rem.offsetMinutes,
                                    localHour = rem.localHour,
                                    localMinute = rem.localMinute,
                                    scheduledAtUtcMillis = rem.scheduledAtUtcMillis,
                                    snoozedUntilUtcMillis = rem.snoozedUntilUtcMillis,
                                    occurrenceKey = rem.occurrenceKey,
                                    dismissed = rem.dismissed,
                                    deliveredAtUtcMillis = rem.deliveredAtUtcMillis,
                                    createdAtMillis = rem.createdAt?.toDate()?.time ?: now,
                                    updatedAtMillis = rem.updatedAt?.toDate()?.time ?: now
                                )
                            }
                        )
                    }
                }
        }

        cloudMirrorJobs += scope.launch {
            cloudRepository.observeClassSchedules(userId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud class schedules mirror paused: ${e.message}")
                }
                .collect { remoteList ->
                    if (activeUserId == userId) {
                        val now = System.currentTimeMillis()
                        dao.reconcileCloudClassSchedules(
                            remoteList.map { cls ->
                                LocalClassScheduleEntity(
                                    id = cls.id,
                                    subject = cls.subject,
                                    room = cls.room,
                                    repeatMode = cls.repeatMode,
                                    daysOfWeekCsv = cls.daysOfWeek.joinToString(","),
                                    dayOfMonth = cls.dayOfMonth,
                                    startTimeMinutes = cls.startTimeMinutes,
                                    endTimeMinutes = cls.endTimeMinutes,
                                    semesterStartDate = cls.semesterStartDate,
                                    semesterMonths = cls.semesterMonths,
                                    notifyEnabled = cls.notifyEnabled,
                                    notifyOffsetMinutes = cls.notifyOffsetMinutes,
                                    colorKey = cls.colorKey,
                                    createdAtMillis = cls.createdAt?.toDate()?.time ?: now,
                                    updatedAtMillis = cls.updatedAt?.toDate()?.time ?: now
                                )
                            }
                        )
                        WidgetUpdateManager.refreshAllWidgets(appContext)
                    }
                }
        }
    }

    private suspend fun syncPendingChangesInternal(restoredFromOffline: Boolean) {
        val uid = activeUserId ?: return
        if (auth.currentUser?.uid != uid) return
        if (!checkCurrentConnectivity()) {
            refreshStatus(overrideOnline = false)
            return
        }

        syncMutex.withLock {
            if (auth.currentUser?.uid != uid) return
            val rawOps = dao.getAllPendingSyncOps()
            if (rawOps.isEmpty()) {
                // Ensure remote profile exists on first sign-in even if no local edits are pending
                cloudRepository.ensureProfileExists(uid)
                lastError = null
                refreshStatus(overrideOnline = true, overridePendingCount = 0)
                return
            }

            isSyncingNow = true
            lastError = null
            refreshStatus(overrideOnline = true, overridePendingCount = rawOps.size)

            // Dependency-safe ordering:
            // Upserts: PROFILE -> ITEM -> STEP -> REMINDER -> CLASS_SCHEDULE
            // Deletes: REMINDER -> STEP -> ITEM -> CLASS_SCHEDULE -> PROFILE
            val orderedOps = rawOps.sortedWith(
                compareBy<PendingSyncOperationEntity> { opOrderPriority(it) }
                    .thenBy { it.queuedAtMillis }
            )

            var pushedCount = 0
            var encounteredTransientError = false

            for (op in orderedOps) {
                if (auth.currentUser?.uid != uid) break
                if (!checkCurrentConnectivity()) {
                    encounteredTransientError = true
                    break
                }
                val result = pushSingleOperation(uid, op)
                if (result.isSuccess) {
                    dao.deletePendingSyncOp(op.opKey)
                    pushedCount++
                } else {
                    val err = result.exceptionOrNull()
                    if (err is CancellationException) throw err
                    val msg = err?.message ?: "Unable to sync change"
                    dao.markPendingSyncFailed(op.opKey, msg.take(200))
                    lastError = msg
                    encounteredTransientError = true
                    if (isNetworkLikeError(err)) {
                        break
                    }
                }
            }

            isSyncingNow = false
            val remaining = dao.getAllPendingSyncOps().size
            val nowOnline = checkCurrentConnectivity()

            if (pushedCount > 0) {
                _syncStatus.value = _syncStatus.value.copy(
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    lastSyncedCount = pushedCount
                )
                if (restoredFromOffline && remaining == 0) {
                    _syncEvents.tryEmit(
                        appContext.getString(R.string.sync_restored_banner, pushedCount)
                    )
                }
            }

            refreshStatus(overrideOnline = nowOnline, overridePendingCount = remaining)

            if (remaining > 0 && nowOnline && encounteredTransientError) {
                scheduleRetryBackoff()
            } else {
                retryJob?.cancel()
                retryJob = null
            }
        }
    }

    private fun scheduleRetryBackoff() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(15_000L)
            if (activeUserId != null && checkCurrentConnectivity()) {
                syncPendingChangesInternal(restoredFromOffline = false)
            }
        }
    }

    private fun opOrderPriority(op: PendingSyncOperationEntity): Int {
        return if (op.action == SyncAction.UPSERT) {
            when (op.entityType) {
                SyncEntityType.PROFILE -> 0
                SyncEntityType.ITEM -> 1
                SyncEntityType.STEP -> 2
                SyncEntityType.REMINDER -> 3
                SyncEntityType.CLASS_SCHEDULE -> 4
                else -> 5
            }
        } else {
            when (op.entityType) {
                SyncEntityType.REMINDER -> 10
                SyncEntityType.STEP -> 11
                SyncEntityType.ITEM -> 12
                SyncEntityType.CLASS_SCHEDULE -> 13
                SyncEntityType.PROFILE -> 14
                else -> 15
            }
        }
    }

    private suspend fun pushSingleOperation(
        uid: String,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val userDoc = firestore.collection("users").document(uid)
        return when (op.entityType) {
            SyncEntityType.PROFILE -> pushProfileOp(uid, userDoc)
            SyncEntityType.ITEM -> pushItemOp(uid, userDoc, op)
            SyncEntityType.STEP -> pushStepOp(uid, userDoc, op)
            SyncEntityType.REMINDER -> pushReminderOp(uid, userDoc, op)
            SyncEntityType.CLASS_SCHEDULE -> pushClassScheduleOp(uid, userDoc, op)
            else -> Result.success(Unit)
        }
    }

    private suspend fun pushProfileOp(
        uid: String,
        userDoc: com.google.firebase.firestore.DocumentReference
    ): Result<Unit> {
        val path = userDoc.path
        return try {
            val local = dao.getProfile(LocalPlannerRepository.LOCAL_USER_ID) ?: return Result.success(Unit)
            val cleanedDedication = local.dedicationText.trim().take(500)
                .ifBlank { UserProfile.DEFAULT_DEDICATION }
            val safeHour = local.defaultReminderHour.coerceIn(0, 23)
            val safeMinute = local.defaultReminderMinute.coerceIn(0, 59)

            val snap = userDoc.get().await()
            if (!snap.exists()) {
                userDoc.set(
                    mapOf(
                        "userId" to uid,
                        "dedicationText" to cleanedDedication,
                        "hasSeenDedication" to local.hasSeenDedication,
                        "defaultReminderHour" to safeHour,
                        "defaultReminderMinute" to safeMinute,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                userDoc.update(
                    mapOf(
                        "dedicationText" to cleanedDedication,
                        "hasSeenDedication" to local.hasSeenDedication,
                        "defaultReminderHour" to safeHour,
                        "defaultReminderMinute" to safeMinute,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun pushItemOp(
        uid: String,
        userDoc: com.google.firebase.firestore.DocumentReference,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val docRef = userDoc.collection("items").document(op.entityId)
        val path = docRef.path
        return try {
            if (op.action == SyncAction.DELETE) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val local = dao.getItemById(op.entityId) ?: return Result.success(Unit)
            val cleanTitle = local.title.trim().take(200)
            if (cleanTitle.isBlank()) return Result.success(Unit)
            val type = ItemType.fromRaw(local.type)
            val cleanNotes = local.notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val cleanParent = local.parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK }
            val cleanStart = local.startDate?.takeIf { it.isNotBlank() }
            val cleanDue = local.dueDate?.takeIf { it.isNotBlank() }

            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("type", type.name)
                    put("title", cleanTitle)
                    if (cleanNotes != null) put("notes", cleanNotes)
                    put("completed", local.completed)
                    if (cleanStart != null) put("startDate", cleanStart)
                    if (cleanDue != null) put("dueDate", cleanDue)
                    local.dueTimeMinutes?.let { put("dueTimeMinutes", it.coerceIn(0, 1439)) }
                    local.startAtUtcMillis?.let { put("startAtUtcMillis", it.coerceAtLeast(0L)) }
                    local.endAtUtcMillis?.let { put("endAtUtcMillis", it.coerceAtLeast(0L)) }
                    if (cleanParent != null) put("parentPlanId", cleanParent)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf<String, Any>(
                    "type" to type.name,
                    "title" to cleanTitle,
                    "completed" to local.completed,
                    "notes" to (cleanNotes ?: FieldValue.delete()),
                    "startDate" to (cleanStart ?: FieldValue.delete()),
                    "dueDate" to (cleanDue ?: FieldValue.delete()),
                    "dueTimeMinutes" to (local.dueTimeMinutes?.coerceIn(0, 1439) ?: FieldValue.delete()),
                    "startAtUtcMillis" to (local.startAtUtcMillis?.coerceAtLeast(0L) ?: FieldValue.delete()),
                    "endAtUtcMillis" to (local.endAtUtcMillis?.coerceAtLeast(0L) ?: FieldValue.delete()),
                    "parentPlanId" to (cleanParent ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (op.action == SyncAction.DELETE) OperationType.DELETE else OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun pushStepOp(
        uid: String,
        userDoc: com.google.firebase.firestore.DocumentReference,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val docRef = userDoc.collection("steps").document(op.entityId)
        val path = docRef.path
        return try {
            if (op.action == SyncAction.DELETE) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val local = dao.getStepById(op.entityId) ?: return Result.success(Unit)
            val cleanTitle = local.title.trim().take(200)
            if (cleanTitle.isBlank()) return Result.success(Unit)
            val cleanDue = local.dueDate?.takeIf { it.isNotBlank() }

            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("itemId", local.itemId)
                    put("title", cleanTitle)
                    put("completed", local.completed)
                    if (cleanDue != null) put("dueDate", cleanDue)
                    local.dueTimeMinutes?.let { put("dueTimeMinutes", it.coerceIn(0, 1439)) }
                    put("sortOrder", local.sortOrder.coerceIn(0, 1000))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf<String, Any>(
                    "title" to cleanTitle,
                    "completed" to local.completed,
                    "dueDate" to (cleanDue ?: FieldValue.delete()),
                    "dueTimeMinutes" to (local.dueTimeMinutes?.coerceIn(0, 1439) ?: FieldValue.delete()),
                    "sortOrder" to local.sortOrder.coerceIn(0, 1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (op.action == SyncAction.DELETE) OperationType.DELETE else OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun pushReminderOp(
        uid: String,
        userDoc: com.google.firebase.firestore.DocumentReference,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val docRef = userDoc.collection("reminders").document(op.entityId)
        val path = docRef.path
        return try {
            if (op.action == SyncAction.DELETE) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val local = dao.getReminderById(op.entityId) ?: return Result.success(Unit)
            val safeKind = ReminderKind.fromRaw(local.kind).name
            val safeOffset = local.offsetMinutes.coerceIn(0, 525600)
            val safeHour = local.localHour.coerceIn(0, 23)
            val safeMinute = local.localMinute.coerceIn(0, 59)
            val safeScheduled = local.scheduledAtUtcMillis.coerceAtLeast(0L)
            val occKey = local.occurrenceKey.ifBlank {
                "${local.itemId}_${safeKind}_${safeOffset}_${safeScheduled}"
            }.take(128)

            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("itemId", local.itemId)
                    put("kind", safeKind)
                    put("offsetMinutes", safeOffset)
                    put("localHour", safeHour)
                    put("localMinute", safeMinute)
                    put("scheduledAtUtcMillis", safeScheduled)
                    local.snoozedUntilUtcMillis?.let { put("snoozedUntilUtcMillis", it.coerceAtLeast(0L)) }
                    put("occurrenceKey", occKey)
                    put("dismissed", local.dismissed)
                    local.deliveredAtUtcMillis?.let { put("deliveredAtUtcMillis", it.coerceAtLeast(0L)) }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf<String, Any>(
                    "kind" to safeKind,
                    "offsetMinutes" to safeOffset,
                    "localHour" to safeHour,
                    "localMinute" to safeMinute,
                    "scheduledAtUtcMillis" to safeScheduled,
                    "snoozedUntilUtcMillis" to (local.snoozedUntilUtcMillis?.coerceAtLeast(0L) ?: FieldValue.delete()),
                    "occurrenceKey" to occKey,
                    "dismissed" to local.dismissed,
                    "deliveredAtUtcMillis" to (local.deliveredAtUtcMillis?.coerceAtLeast(0L) ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (op.action == SyncAction.DELETE) OperationType.DELETE else OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun pushClassScheduleOp(
        uid: String,
        userDoc: com.google.firebase.firestore.DocumentReference,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val docRef = userDoc.collection("classSchedules").document(op.entityId)
        val path = docRef.path
        return try {
            if (op.action == SyncAction.DELETE) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val local = dao.getClassScheduleById(op.entityId) ?: return Result.success(Unit)
            val cleanSubject = local.subject.trim().take(160)
            if (cleanSubject.isBlank()) return Result.success(Unit)
            val cleanRoom = local.room?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val cleanDays = local.daysOfWeekCsv
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0..6 }
                .distinct()
                .sorted()
            val cleanStart = CalendarDate.parse(local.semesterStartDate)?.toIsoString()
                ?: CalendarDate.today().toIsoString()

            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("subject", cleanSubject)
                    if (cleanRoom != null) put("room", cleanRoom)
                    put("repeatMode", ClassRepeatMode.fromRaw(local.repeatMode).name)
                    put("daysOfWeek", cleanDays)
                    put("dayOfMonth", local.dayOfMonth.coerceIn(1, 31))
                    put("startTimeMinutes", local.startTimeMinutes.coerceIn(0, 1439))
                    put("endTimeMinutes", local.endTimeMinutes.coerceIn(0, 1439))
                    put("semesterStartDate", cleanStart)
                    put("semesterMonths", local.semesterMonths.coerceIn(1, 12))
                    put("notifyEnabled", local.notifyEnabled)
                    put("notifyOffsetMinutes", local.notifyOffsetMinutes.coerceIn(0, 10080))
                    put("colorKey", ClassColorKey.fromRaw(local.colorKey).name)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updateMap = mapOf<String, Any>(
                    "subject" to cleanSubject,
                    "room" to (cleanRoom ?: FieldValue.delete()),
                    "repeatMode" to ClassRepeatMode.fromRaw(local.repeatMode).name,
                    "daysOfWeek" to cleanDays,
                    "dayOfMonth" to local.dayOfMonth.coerceIn(1, 31),
                    "startTimeMinutes" to local.startTimeMinutes.coerceIn(0, 1439),
                    "endTimeMinutes" to local.endTimeMinutes.coerceIn(0, 1439),
                    "semesterStartDate" to cleanStart,
                    "semesterMonths" to local.semesterMonths.coerceIn(1, 12),
                    "notifyEnabled" to local.notifyEnabled,
                    "notifyOffsetMinutes" to local.notifyOffsetMinutes.coerceIn(0, 10080),
                    "colorKey" to ClassColorKey.fromRaw(local.colorKey).name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (op.action == SyncAction.DELETE) OperationType.DELETE else OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun isNetworkLikeError(error: Throwable?): Boolean {
        val code = (error as? FirebaseFirestoreException)?.code
        return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }

    private fun refreshStatus(
        overrideOnline: Boolean? = null,
        overridePendingCount: Int? = null
    ) {
        val prev = _syncStatus.value
        val online = overrideOnline ?: checkCurrentConnectivity()
        val signedIn = activeUserId != null && auth.currentUser?.uid == activeUserId
        val pending = overridePendingCount ?: prev.pendingCount

        val phase = when {
            !signedIn -> SyncPhase.LOCAL_ONLY
            isSyncingNow -> SyncPhase.SYNCING
            !online && pending > 0 -> SyncPhase.OFFLINE_QUEUED
            !online -> SyncPhase.OFFLINE_IDLE
            pending > 0 && lastError != null -> SyncPhase.ERROR
            pending > 0 -> SyncPhase.SYNCING
            else -> SyncPhase.SYNCED
        }

        _syncStatus.value = prev.copy(
            phase = phase,
            isOnline = online,
            isSignedIn = signedIn,
            pendingCount = pending,
            lastErrorMessage = if (phase == SyncPhase.ERROR) lastError else null
        )
    }

    companion object {
        private const val TAG = "RoomFirestoreSync"

        @Volatile
        private var instance: RoomFirestoreSyncService? = null

        fun getInstance(context: Context): RoomFirestoreSyncService {
            return instance ?: synchronized(this) {
                instance ?: RoomFirestoreSyncService(context.applicationContext).also { instance = it }
            }
        }
    }
}
