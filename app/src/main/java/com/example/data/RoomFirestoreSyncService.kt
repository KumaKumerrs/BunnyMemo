package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.R
import com.example.audio.TaskAudioMemoManager
import com.example.widgets.WidgetUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlin.math.min
import kotlin.random.Random

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
 * - Lazily initializes Firebase so local-only mode never crashes when `google-services.json` is absent.
 * - Uses revision-checked outbox deletes (`deletePendingSyncOpIfUnchanged`) to eliminate concurrent edit races.
 * - Checks tombstones & remote `updatedAt` timestamps (LWW) before overwriting documents.
 * - Classifies permanent vs. transient Firestore errors with jittered exponential backoff and dead-letter eviction.
 * - Syncs Profiles, Items, Steps, Reminders, Class Schedules, Daily Habits, and Habit Check-Ins.
 * - Cleans up orphaned local photos/audio when remote items are deleted and debounces widget refreshes.
 */
class RoomFirestoreSyncService private constructor(
    private val appContext: Context
) {
    private val database: LocalPlannerDatabase = LocalPlannerDatabase.getInstance(appContext)
    private val dao: LocalPlannerDao = database.plannerDao()

    private val firestoreOrNull: FirebaseFirestore? by lazy {
        runCatching {
            FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
        }.getOrNull()
    }
    private val authOrNull: FirebaseAuth? by lazy {
        runCatching { Firebase.auth }.getOrNull()
    }
    private val cloudRepositoryOrNull: PlannerRepository? by lazy {
        val db = firestoreOrNull ?: return@lazy null
        val auth = authOrNull ?: return@lazy null
        PlannerRepository(db, auth)
    }

    private val connectivityManager: ConnectivityManager? = runCatching {
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.w(TAG, "Sync service background warning: ${throwable.message}")
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val syncMutex = Mutex()
    private val mirrorLock = Any()

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var wasOffline: Boolean = false

    @Volatile
    private var isSyncingNow: Boolean = false

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var retryAttempt: Int = 0

    @Volatile
    private var networkRegistered: Boolean = false

    private val cloudMirrorJobs: MutableList<Job> = mutableListOf()
    private var startupSyncJob: Job? = null
    private var retryJob: Job? = null
    private var widgetRefreshDebounceJob: Job? = null

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

    fun createOfflineFirstDataSource(): LocalPlannerRepository {
        return LocalPlannerRepository(
            dao = dao,
            database = database,
            onMutationEnqueued = {
                scheduleDebouncedWidgetRefresh()
                requestSync(restoredFromOffline = false)
            }
        )
    }

    suspend fun getPendingOpCount(): Int = runCatching { dao.getPendingSyncCount() }.getOrDefault(0)

    fun startCloudSyncForUser(userId: String) {
        val auth = authOrNull ?: return
        if (firestoreOrNull == null) return
        val currentAuthUid = auth.currentUser?.uid
        if (userId.isBlank() || currentAuthUid != userId) {
            Log.w(TAG, "Ignoring startCloudSyncForUser: auth UID mismatch or not signed in.")
            return
        }
        val changedUser = activeUserId != userId
        activeUserId = userId
        lastError = null
        retryAttempt = 0
        refreshStatus()

        if (changedUser) {
            synchronized(mirrorLock) {
                startupSyncJob?.cancel()
                stopCloudMirrorJobsLocked()
                startupSyncJob = scope.launch {
                    try {
                        // First pull cloud profile if present so default local guest settings never clobber it
                        pullInitialCloudProfileIfAbsentLocally(userId)
                        LocalPlannerRepository(dao, database).enqueueUnqueuedLocalDataForSync()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed backfilling local records into sync outbox: ${e.message}")
                    }
                    if (activeUserId != userId) return@launch
                    if (checkCurrentConnectivity()) {
                        syncPendingChangesInternal(restoredFromOffline = false)
                    }
                    if (activeUserId == userId) {
                        startCloudMirrorJobs(userId)
                    }
                }
            }
        } else {
            requestSync(restoredFromOffline = false)
        }
    }

    fun stopCloudSync() {
        activeUserId = null
        lastError = null
        retryAttempt = 0
        synchronized(mirrorLock) {
            startupSyncJob?.cancel()
            startupSyncJob = null
            retryJob?.cancel()
            retryJob = null
            stopCloudMirrorJobsLocked()
        }
        refreshStatus()
    }

    fun unregisterNetworkMonitor() {
        if (!networkRegistered) return
        val cm = connectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        networkRegistered = false
    }

    fun requestSync(restoredFromOffline: Boolean = false) {
        scope.launch {
            syncPendingChangesInternal(restoredFromOffline = restoredFromOffline)
        }
    }

    private fun scheduleDebouncedWidgetRefresh() {
        synchronized(mirrorLock) {
            widgetRefreshDebounceJob?.cancel()
            widgetRefreshDebounceJob = scope.launch {
                delay(280L)
                WidgetUpdateManager.refreshAllWidgets(appContext)
            }
        }
    }

    private suspend fun pullInitialCloudProfileIfAbsentLocally(userId: String) {
        val db = firestoreOrNull ?: return
        val localProfile = dao.getProfile(LocalPlannerRepository.LOCAL_USER_ID)
        if (localProfile != null && localProfile.hasCustomLocalEdits) return
        val snap = runCatching { db.collection("users").document(userId).get().await() }.getOrNull() ?: return
        if (!snap.exists()) return
        val now = System.currentTimeMillis()
        val dedication = snap.getString("dedicationText")?.takeIf { it.isNotBlank() }
            ?: UserProfile.DEFAULT_DEDICATION
        val seen = snap.getBoolean("hasSeenDedication") ?: true
        val hour = (snap.getLong("defaultReminderHour") ?: 9L).toInt().coerceIn(0, 23)
        val minute = (snap.getLong("defaultReminderMinute") ?: 0L).toInt().coerceIn(0, 59)
        dao.upsertProfile(
            LocalProfileEntity(
                id = LocalPlannerRepository.LOCAL_USER_ID,
                dedicationText = dedication,
                hasSeenDedication = seen,
                defaultReminderHour = hour,
                defaultReminderMinute = minute,
                createdAtMillis = snap.getTimestamp("createdAt")?.toDate()?.time ?: now,
                updatedAtMillis = snap.getTimestamp("updatedAt")?.toDate()?.time ?: now,
                hasCustomLocalEdits = false
            )
        )
    }

    private fun registerNetworkMonitor() {
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            networkRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register NetworkCallback: ${e.message}")
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
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
            retryAttempt = 0
            requestSync(restoredFromOffline = previouslyOffline)
        }
    }

    private fun observePendingQueueCount() {
        scope.launch {
            dao.observePendingSyncCount()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "observePendingSyncCount failed: ${e.message}")
                    emit(0)
                }
                .collectLatest { count ->
                    refreshStatus(overridePendingCount = count)
                }
        }
    }

    private fun stopCloudMirrorJobsLocked() {
        cloudMirrorJobs.forEach { it.cancel() }
        cloudMirrorJobs.clear()
    }

    private fun startCloudMirrorJobs(userId: String) {
        val cloudRepo = cloudRepositoryOrNull ?: return
        val db = firestoreOrNull ?: return
        synchronized(mirrorLock) {
            if (activeUserId != userId) return
            stopCloudMirrorJobsLocked()

            cloudMirrorJobs += scope.launch {
                cloudRepo.observeProfile(userId)
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
                                    updatedAtMillis = remote.updatedAt?.toDate()?.time ?: now,
                                    hasCustomLocalEdits = false
                                )
                            )
                        }
                    }
            }

            cloudMirrorJobs += scope.launch {
                cloudRepo.observeItems(userId)
                    .catch { e ->
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Cloud items mirror paused: ${e.message}")
                    }
                    .collect { remoteList ->
                        if (activeUserId == userId) {
                            val now = System.currentTimeMillis()
                            val removedItems = dao.reconcileCloudItems(
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
                                        updatedAtMillis = item.updatedAt?.toDate()?.time ?: now,
                                        priority = TaskPriority.fromRaw(item.priority).name,
                                        category = TaskCategory.fromRaw(item.category).name
                                    )
                                }
                            )
                            if (removedItems.isNotEmpty()) {
                                removedItems.forEach { removed ->
                                    val photos = PhotoAttachmentStore.parsePaths(removed.imagePathsCsv)
                                    if (photos.isNotEmpty()) {
                                        PhotoAttachmentStore.deletePhotoFiles(photos)
                                    }
                                    TaskAudioMemoManager.deleteMemoFile(removed.audioMemoPath)
                                }
                            }
                            scheduleDebouncedWidgetRefresh()
                        }
                    }
            }

            cloudMirrorJobs += scope.launch {
                cloudRepo.observeSteps(userId)
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
                                        updatedAtMillis = step.updatedAt?.toDate()?.time ?: now,
                                        timeSpentMinutes = step.timeSpentMinutes.coerceIn(0, 100_000)
                                    )
                                }
                            )
                            scheduleDebouncedWidgetRefresh()
                        }
                    }
            }

            cloudMirrorJobs += scope.launch {
                cloudRepo.observeReminders(userId)
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
                cloudRepo.observeClassSchedules(userId)
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
                            scheduleDebouncedWidgetRefresh()
                        }
                    }
            }

            cloudMirrorJobs += scope.launch {
                db.collection("users").document(userId).collection("dailyHabits")
                    .snapshots()
                    .map { snap ->
                        val now = System.currentTimeMillis()
                        snap.documents.mapNotNull { doc ->
                            val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            LocalDailyHabitEntity(
                                id = doc.id,
                                title = title.take(80),
                                iconKey = HabitIconKey.fromRaw(doc.getString("iconKey")).name,
                                colorKey = ClassColorKey.fromRaw(doc.getString("colorKey")).name,
                                sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt().coerceIn(0, 500),
                                createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: now,
                                updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time ?: now
                            )
                        }
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Cloud daily habits mirror paused: ${e.message}")
                    }
                    .collect { remoteHabits ->
                        if (activeUserId == userId) {
                            dao.reconcileCloudDailyHabits(remoteHabits)
                            scheduleDebouncedWidgetRefresh()
                        }
                    }
            }

            cloudMirrorJobs += scope.launch {
                db.collection("users").document(userId).collection("habitCheckIns")
                    .snapshots()
                    .map { snap ->
                        snap.documents.mapNotNull { doc ->
                            val habitId = doc.getString("habitId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val dateIso = doc.getString("dateIso")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            LocalHabitCheckInEntity(
                                checkInKey = "$habitId|$dateIso",
                                habitId = habitId,
                                dateIso = dateIso,
                                completedAtMillis = doc.getLong("completedAtMillis") ?: System.currentTimeMillis()
                            )
                        }
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Cloud habit check-ins mirror paused: ${e.message}")
                    }
                    .collect { remoteCheckIns ->
                        if (activeUserId == userId) {
                            dao.reconcileCloudHabitCheckIns(remoteCheckIns)
                            scheduleDebouncedWidgetRefresh()
                        }
                    }
            }
        }
    }

    private suspend fun syncPendingChangesInternal(restoredFromOffline: Boolean) {
        val uid = activeUserId ?: return
        val auth = authOrNull ?: return
        val cloudRepo = cloudRepositoryOrNull ?: return
        if (auth.currentUser?.uid != uid) return
        if (!checkCurrentConnectivity()) {
            refreshStatus(overrideOnline = false)
            return
        }

        syncMutex.withLock {
            if (auth.currentUser?.uid != uid || activeUserId != uid) return
            val rawOps = dao.getAllPendingSyncOps()
            if (rawOps.isEmpty()) {
                val ensureResult = cloudRepo.ensureProfileExists(uid)
                lastError = ensureResult.exceptionOrNull()?.message
                refreshStatus(overrideOnline = true, overridePendingCount = 0)
                return
            }

            isSyncingNow = true
            lastError = null
            refreshStatus(overrideOnline = true, overridePendingCount = rawOps.size)

            val orderedOps = rawOps.sortedWith(
                compareBy<PendingSyncOperationEntity> { opOrderPriority(it) }
                    .thenBy { it.queuedAtMillis }
            )

            var pushedCount = 0
            var encounteredTransientError = false

            for (op in orderedOps) {
                if (auth.currentUser?.uid != uid || activeUserId != uid) break
                if (!checkCurrentConnectivity()) {
                    encounteredTransientError = true
                    break
                }
                val result = pushSingleOperation(uid, op)
                if (result.isSuccess) {
                    // Only remove the outbox row if no newer local edit replaced it while network I/O was in flight
                    dao.deletePendingSyncOpIfUnchanged(op.opKey, op.revision)
                    pushedCount++
                } else {
                    val err = result.exceptionOrNull()
                    if (err is CancellationException) throw err
                    val msg = err?.message ?: "Unable to sync change"
                    if (isPermanentSyncError(err) || op.retryCount + 1 >= MAX_SYNC_RETRIES) {
                        Log.w(TAG, "Dropping permanently unrecoverable sync op ${op.opKey}: $msg")
                        dao.deletePendingSyncOpIfUnchanged(op.opKey, op.revision)
                        lastError = null
                    } else {
                        dao.markPendingSyncFailed(op.opKey, msg.take(200))
                        lastError = msg
                        encounteredTransientError = true
                        if (isNetworkLikeError(err)) {
                            break
                        }
                    }
                }
            }

            isSyncingNow = false
            val remaining = dao.getPendingSyncCount()
            val nowOnline = checkCurrentConnectivity()

            if (pushedCount > 0) {
                retryAttempt = 0
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
                synchronized(mirrorLock) {
                    retryJob?.cancel()
                    retryJob = null
                }
            }
        }
    }

    private fun scheduleRetryBackoff() {
        synchronized(mirrorLock) {
            if (retryJob?.isActive == true) return
            val attempt = retryAttempt.coerceAtMost(4)
            retryAttempt = attempt + 1
            val baseMs = min(120_000L, 5_000L * (1L shl attempt))
            val jitterMs = Random.nextLong(500L, 2_500L)
            retryJob = scope.launch {
                delay(baseMs + jitterMs)
                if (activeUserId != null && checkCurrentConnectivity()) {
                    syncPendingChangesInternal(restoredFromOffline = false)
                }
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
                SyncEntityType.DAILY_HABIT -> 5
                SyncEntityType.HABIT_CHECKIN -> 6
                else -> 7
            }
        } else {
            when (op.entityType) {
                SyncEntityType.HABIT_CHECKIN -> 10
                SyncEntityType.DAILY_HABIT -> 11
                SyncEntityType.REMINDER -> 12
                SyncEntityType.STEP -> 13
                SyncEntityType.ITEM -> 14
                SyncEntityType.CLASS_SCHEDULE -> 15
                SyncEntityType.PROFILE -> 16
                else -> 17
            }
        }
    }

    private fun isRemoteNewer(snap: DocumentSnapshot, localUpdatedAtMillis: Long): Boolean {
        val remoteMillis = snap.getTimestamp("updatedAt")?.toDate()?.time ?: return false
        return remoteMillis > localUpdatedAtMillis + 2_000L
    }

    private suspend fun isBlockedByRemoteDeleteTombstone(op: PendingSyncOperationEntity, localUpdatedAtMillis: Long): Boolean {
        val tombstone = dao.getTombstone(op.opKey) ?: return false
        return tombstone.deletedAtMillis >= localUpdatedAtMillis
    }

    private suspend fun pushSingleOperation(
        uid: String,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val db = firestoreOrNull ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val userDoc = db.collection("users").document(uid)
        return when (op.entityType) {
            SyncEntityType.PROFILE -> pushProfileOp(uid, userDoc)
            SyncEntityType.ITEM -> pushItemOp(uid, userDoc, op)
            SyncEntityType.STEP -> pushStepOp(uid, userDoc, op)
            SyncEntityType.REMINDER -> pushReminderOp(uid, userDoc, op)
            SyncEntityType.CLASS_SCHEDULE -> pushClassScheduleOp(uid, userDoc, op)
            SyncEntityType.DAILY_HABIT -> pushDailyHabitOp(uid, userDoc, op)
            SyncEntityType.HABIT_CHECKIN -> pushHabitCheckInOp(uid, userDoc, op)
            else -> Result.success(Unit)
        }
    }

    private suspend fun pushProfileOp(
        uid: String,
        userDoc: DocumentReference
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
            } else if (!isRemoteNewer(snap, local.updatedAtMillis)) {
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
        userDoc: DocumentReference,
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
            if (isBlockedByRemoteDeleteTombstone(op, local.updatedAtMillis)) return Result.success(Unit)
            val cleanTitle = local.title.trim().take(200)
            if (cleanTitle.isBlank()) return Result.success(Unit)
            val type = ItemType.fromRaw(local.type)
            val cleanNotes = local.notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }
            val cleanParent = local.parentPlanId?.trim()?.takeIf { it.isNotEmpty() && type == ItemType.TASK && it != local.id }
            val cleanStart = local.startDate?.let { CalendarDate.parse(it)?.toIsoString() }
            val cleanDue = local.dueDate?.let { CalendarDate.parse(it)?.toIsoString() }

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
                    put("priority", TaskPriority.fromRaw(local.priority).name)
                    put("category", TaskCategory.fromRaw(local.category).name)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else if (!isRemoteNewer(snap, local.updatedAtMillis)) {
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
                    "priority" to TaskPriority.fromRaw(local.priority).name,
                    "category" to TaskCategory.fromRaw(local.category).name,
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
        userDoc: DocumentReference,
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
            if (isBlockedByRemoteDeleteTombstone(op, local.updatedAtMillis)) return Result.success(Unit)
            val cleanTitle = local.title.trim().take(200)
            if (cleanTitle.isBlank()) return Result.success(Unit)
            val cleanDue = local.dueDate?.let { CalendarDate.parse(it)?.toIsoString() }

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
                    put("timeSpentMinutes", local.timeSpentMinutes.coerceIn(0, 100_000))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else if (!isRemoteNewer(snap, local.updatedAtMillis)) {
                val updateMap = mapOf<String, Any>(
                    "title" to cleanTitle,
                    "completed" to local.completed,
                    "dueDate" to (cleanDue ?: FieldValue.delete()),
                    "dueTimeMinutes" to (local.dueTimeMinutes?.coerceIn(0, 1439) ?: FieldValue.delete()),
                    "sortOrder" to local.sortOrder.coerceIn(0, 1000),
                    "timeSpentMinutes" to local.timeSpentMinutes.coerceIn(0, 100_000),
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
        userDoc: DocumentReference,
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
            if (isBlockedByRemoteDeleteTombstone(op, local.updatedAtMillis)) return Result.success(Unit)
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
            } else if (!isRemoteNewer(snap, local.updatedAtMillis)) {
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
        userDoc: DocumentReference,
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
            if (isBlockedByRemoteDeleteTombstone(op, local.updatedAtMillis)) return Result.success(Unit)
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
            } else if (!isRemoteNewer(snap, local.updatedAtMillis)) {
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

    private suspend fun pushDailyHabitOp(
        uid: String,
        userDoc: DocumentReference,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val docRef = userDoc.collection("dailyHabits").document(op.entityId)
        val path = docRef.path
        return try {
            if (op.action == SyncAction.DELETE) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val local = dao.getDailyHabitById(op.entityId) ?: return Result.success(Unit)
            if (isBlockedByRemoteDeleteTombstone(op, local.updatedAtMillis)) return Result.success(Unit)
            val cleanTitle = local.title.trim().take(80)
            if (cleanTitle.isBlank()) return Result.success(Unit)

            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "iconKey" to HabitIconKey.fromRaw(local.iconKey).name,
                        "colorKey" to ClassColorKey.fromRaw(local.colorKey).name,
                        "sortOrder" to local.sortOrder.coerceIn(0, 500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (!isRemoteNewer(snap, local.updatedAtMillis)) {
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "iconKey" to HabitIconKey.fromRaw(local.iconKey).name,
                        "colorKey" to ClassColorKey.fromRaw(local.colorKey).name,
                        "sortOrder" to local.sortOrder.coerceIn(0, 500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (op.action == SyncAction.DELETE) OperationType.DELETE else OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun pushHabitCheckInOp(
        uid: String,
        userDoc: DocumentReference,
        op: PendingSyncOperationEntity
    ): Result<Unit> {
        val firestoreDocId = firestoreCheckInDocId(op.entityId)
        val docRef = userDoc.collection("habitCheckIns").document(firestoreDocId)
        val path = docRef.path
        return try {
            if (op.action == SyncAction.DELETE) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val local = dao.getHabitCheckIn(op.entityId) ?: return Result.success(Unit)
            val cleanDate = CalendarDate.parse(local.dateIso)?.toIsoString() ?: return Result.success(Unit)

            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "habitId" to local.habitId,
                        "dateIso" to cleanDate,
                        "completedAtMillis" to local.completedAtMillis.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "completedAtMillis" to local.completedAtMillis.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
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

    private fun isPermanentSyncError(error: Throwable?): Boolean {
        val code = (error as? FirebaseFirestoreException)?.code ?: return false
        return code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == FirebaseFirestoreException.Code.INVALID_ARGUMENT ||
            code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
    }

    private fun refreshStatus(
        overrideOnline: Boolean? = null,
        overridePendingCount: Int? = null
    ) {
        val prev = _syncStatus.value
        val online = overrideOnline ?: checkCurrentConnectivity()
        val signedIn = activeUserId != null && authOrNull?.currentUser?.uid == activeUserId
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
        private const val MAX_SYNC_RETRIES = 5

        fun firestoreCheckInDocId(roomCheckInKey: String): String =
            roomCheckInKey.replace("|", "_").replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)

        @Volatile
        private var instance: RoomFirestoreSyncService? = null

        fun getInstance(context: Context): RoomFirestoreSyncService {
            return instance ?: synchronized(this) {
                instance ?: RoomFirestoreSyncService(context.applicationContext).also { instance = it }
            }
        }
    }
}
