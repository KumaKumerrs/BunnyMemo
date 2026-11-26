package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.TaskAudioMemoManager
import com.example.data.CalendarDate
import com.example.data.ClassColorKey
import com.example.data.ClassRepeatMode
import com.example.data.ClassScheduleItem
import com.example.data.InboxNotificationEntry
import com.example.data.ItemProgress
import com.example.data.ItemType
import com.example.data.PhotoAttachmentStore
import com.example.data.PlannerDataSource
import com.example.data.PlannerItem
import com.example.data.PlannerLogic
import com.example.data.PlannerReminder
import com.example.data.PlannerStep
import com.example.data.ReminderDraft
import com.example.data.ReminderKind
import com.example.data.StepDraft
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.example.data.UnifiedStep
import com.example.data.UserProfile
import com.example.notifications.DailyDigestSummary
import com.example.notifications.DailyReminderConfig
import com.example.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

sealed interface PlannerUiState {
    data object Loading : PlannerUiState
    data class Error(val message: String) : PlannerUiState
    data class Ready(
        val profile: UserProfile,
        val items: List<PlannerItem>,
        val steps: List<PlannerStep>,
        val reminders: List<PlannerReminder>,
        val classSchedules: List<ClassScheduleItem>,
        val nowMillis: Long,
        val inboxEntries: List<InboxNotificationEntry>,
        val dailyReminderConfig: DailyReminderConfig,
        val dailyDigestSummary: DailyDigestSummary
    ) : PlannerUiState {
        // Pre-indexed O(1) lookup tables avoid O(N) scans during Compose recompositions
        private val stepsByItemId: Map<String, List<PlannerStep>> by lazy(LazyThreadSafetyMode.NONE) {
            steps.groupBy { it.itemId }
        }
        private val linkedTasksByPlanId: Map<String, List<PlannerItem>> by lazy(LazyThreadSafetyMode.NONE) {
            items.filter { it.itemType() == ItemType.TASK && !it.parentPlanId.isNullOrBlank() }
                .groupBy { it.parentPlanId!! }
        }
        private val remindersByItemId: Map<String, List<PlannerReminder>> by lazy(LazyThreadSafetyMode.NONE) {
            reminders.groupBy { it.itemId }
                .mapValues { (_, list) -> list.sortedBy { it.effectiveTriggerMillis() } }
        }

        fun progressFor(item: PlannerItem): ItemProgress {
            val ownSteps = stepsByItemId[item.id].orEmpty()
            val linked = if (item.itemType() == ItemType.PLAN) {
                linkedTasksByPlanId[item.id].orEmpty()
            } else {
                emptyList()
            }
            return PlannerLogic.computeProgress(item, ownSteps, linked, nowMillis)
        }

        fun stepsFor(item: PlannerItem): List<UnifiedStep> {
            val ownSteps = stepsByItemId[item.id].orEmpty()
            val linked = if (item.itemType() == ItemType.PLAN) {
                linkedTasksByPlanId[item.id].orEmpty()
            } else {
                emptyList()
            }
            return PlannerLogic.stepsForItem(item, ownSteps, linked)
        }

        fun remindersFor(itemId: String): List<PlannerReminder> =
            remindersByItemId[itemId].orEmpty()

        fun classesOnDate(date: CalendarDate): List<ClassScheduleItem> =
            classSchedules.filter { it.occursOnDate(date) }.sortedBy { it.startTimeMinutes }

        fun todayDate(): CalendarDate = CalendarDate.today(nowMillis)
    }
}

private data class PlannerDataSnapshot(
    val profile: UserProfile?,
    val items: List<PlannerItem>,
    val steps: List<PlannerStep>,
    val reminders: List<PlannerReminder>,
    val classSchedules: List<ClassScheduleItem>
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(
    private val repository: PlannerDataSource,
    private val reminderScheduler: ReminderScheduler,
    private val currentUserId: String
) : ViewModel() {

    private val reloadNonce = MutableStateFlow(0)
    private val nowMillisFlow = MutableStateFlow(System.currentTimeMillis())
    private val dailyConfigFlow = MutableStateFlow(reminderScheduler.loadDailyConfig())

    private val _celebratingItemId = MutableStateFlow<String?>(null)
    val celebratingItemId: StateFlow<String?> = _celebratingItemId.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val feedbackEvents: SharedFlow<String> = _feedbackEvents.asSharedFlow()

    private val activeSaves = AtomicInteger(0)
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // Single shared upstream subscription avoids doubling Room/Firestore flow collectors
    private val sharedBaseDataFlow = reloadNonce.flatMapLatest {
        combine(
            repository.observeProfile(currentUserId),
            repository.observeItems(currentUserId),
            repository.observeSteps(currentUserId),
            repository.observeReminders(currentUserId),
            repository.observeClassSchedules(currentUserId)
        ) { profile, items, steps, reminders, classSchedules ->
            PlannerDataSnapshot(profile, items, steps, reminders, classSchedules)
        }
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        replay = 1
    )

    init {
        viewModelScope.launch {
            repository.ensureProfileExists(currentUserId)
                .onFailure { err ->
                    Log.w(TAG, "Profile initialization warning: ${err.message}")
                }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                nowMillisFlow.value = System.currentTimeMillis()
            }
        }
        // Sync OS alarms off the Main thread with retryWhen so transient stream errors never kill alarms
        viewModelScope.launch(Dispatchers.Default) {
            combine(sharedBaseDataFlow, dailyConfigFlow) { snapshot, dailyConfig ->
                snapshot to dailyConfig
            }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) return@retryWhen false
                    Log.w(TAG, "Alarm sync stream transient error (attempt $attempt): ${cause.message}")
                    delay((attempt.coerceAtMost(5) + 1L) * 2_000L)
                    true
                }
                .collectLatest { (snapshot, dailyConfig) ->
                    val now = System.currentTimeMillis()
                    reminderScheduler.syncReminders(snapshot.reminders, snapshot.items, snapshot.steps, now)
                    reminderScheduler.syncClassReminders(snapshot.classSchedules, now)
                    val digestSummary = reminderScheduler.buildDailyDigestSummary(snapshot.items, snapshot.steps, now)
                    reminderScheduler.syncDailyDigest(dailyConfig, digestSummary, now)
                }
        }
    }

    val uiState: StateFlow<PlannerUiState> = reloadNonce.flatMapLatest {
        combine(
            sharedBaseDataFlow,
            nowMillisFlow,
            dailyConfigFlow
        ) { snapshot, nowMillis, dailyConfig ->
            val effectiveProfile = snapshot.profile ?: UserProfile(
                id = currentUserId,
                userId = currentUserId,
                dedicationText = UserProfile.DEFAULT_DEDICATION,
                hasSeenDedication = false
            )
            val digestSummary = reminderScheduler.buildDailyDigestSummary(snapshot.items, snapshot.steps, nowMillis)
            val inbox = PlannerLogic.buildInboxEntries(snapshot.reminders, snapshot.items, snapshot.steps, nowMillis)
            PlannerUiState.Ready(
                profile = effectiveProfile,
                items = snapshot.items,
                steps = snapshot.steps,
                reminders = snapshot.reminders,
                classSchedules = snapshot.classSchedules,
                nowMillis = nowMillis,
                inboxEntries = inbox,
                dailyReminderConfig = dailyConfig,
                dailyDigestSummary = digestSummary
            )
        }
            .flowOn(Dispatchers.Default)
            .map<PlannerUiState.Ready, PlannerUiState> { it }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing planner streams", rootError)
                emit(PlannerUiState.Error(rootError.message ?: "Unable to sync your planner right now."))
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PlannerUiState.Loading
    )

    private fun emitFeedback(message: String) {
        _feedbackMessage.value = message
        _feedbackEvents.tryEmit(message)
    }

    private suspend inline fun <T> withSavingState(block: () -> T): T {
        if (activeSaves.incrementAndGet() > 0) {
            _isSaving.value = true
        }
        return try {
            block()
        } finally {
            if (activeSaves.decrementAndGet() <= 0) {
                activeSaves.set(0)
                _isSaving.value = false
            }
        }
    }

    fun retryLoad() {
        reloadNonce.value = reloadNonce.value + 1
        viewModelScope.launch {
            repository.ensureProfileExists(currentUserId)
        }
    }

    fun setDailyDigestEnabled(enabled: Boolean) {
        val updated = dailyConfigFlow.value.copy(enabled = enabled)
        dailyConfigFlow.value = updated
        reminderScheduler.saveDailyConfig(updated)
        emitFeedback(
            if (enabled) "Gentle daily forget-me-not reminders enabled."
            else "Daily forget-me-not reminders paused."
        )
    }

    fun updateDailyDigestTime(hour: Int, minute: Int) {
        val updated = dailyConfigFlow.value.copy(hour = hour, minute = minute, enabled = true)
        dailyConfigFlow.value = updated
        reminderScheduler.saveDailyConfig(updated)
        emitFeedback("Daily forget-me-not reminder time updated.")
    }

    fun sendDailyDigestPreview(): Boolean {
        val ready = uiState.value as? PlannerUiState.Ready ?: return false
        val sent = reminderScheduler.postDailyDigestPreview(ready.dailyDigestSummary)
        emitFeedback(
            if (sent) "Sent a gentle forget-me-not preview notification."
            else "Allow device notifications to preview your daily forget-me-not alert."
        )
        return sent
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun clearCelebration() {
        _celebratingItemId.value = null
    }

    fun saveDedication(text: String, markSeen: Boolean) {
        viewModelScope.launch {
            withSavingState {
                repository.saveDedication(currentUserId, text, markSeen)
                    .onSuccess {
                        if (!markSeen) emitFeedback("Dedication note saved.")
                    }
                    .onFailure { e ->
                        emitFeedback(e.localizedMessage ?: "Could not save dedication.")
                    }
            }
        }
    }

    fun updateDefaultReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateDefaultReminderTime(currentUserId, hour, minute)
                .onSuccess { emitFeedback("Default reminder time updated.") }
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Update failed.") }
        }
    }

    fun createItem(
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
        category: TaskCategory = TaskCategory.PERSONAL,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            withSavingState {
                repository.createItemWithStepsAndReminders(
                    userId = currentUserId,
                    type = type,
                    title = title,
                    notes = notes,
                    startDate = startDate,
                    dueDate = dueDate,
                    dueTimeMinutes = dueTimeMinutes,
                    startAtUtcMillis = startAtUtcMillis,
                    endAtUtcMillis = endAtUtcMillis,
                    parentPlanId = parentPlanId,
                    steps = steps,
                    reminders = reminders,
                    imagePaths = imagePaths,
                    priority = priority,
                    category = category
                ).onSuccess { newId ->
                    emitFeedback("${type.label} added.")
                    onCreated(newId)
                }.onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not save item.")
                }
            }
        }
    }

    fun createQuickNote(
        rawText: String,
        dueDateIso: String? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        category: TaskCategory = TaskCategory.PERSONAL,
        onCreated: (String) -> Unit = {}
    ) {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) return

        val lines = cleaned.lines().map { it.trimEnd() }
        val firstNonBlankIdx = lines.indexOfFirst { it.isNotBlank() }
        if (firstNonBlankIdx < 0) return

        val firstLine = lines[firstNonBlankIdx].trim()
        val remainingLines = lines.drop(firstNonBlankIdx + 1)
            .joinToString("\n")
            .trim()

        val title: String
        val notes: String?
        if (firstLine.length <= 200) {
            title = firstLine
            notes = remainingLines.take(1000).ifBlank { null }
        } else {
            title = firstLine.take(197).trimEnd() + "…"
            val overflow = buildString {
                append(firstLine)
                if (remainingLines.isNotBlank()) {
                    append("\n\n")
                    append(remainingLines)
                }
            }.take(1000)
            notes = overflow
        }

        viewModelScope.launch {
            withSavingState {
                repository.createItemWithStepsAndReminders(
                    userId = currentUserId,
                    type = ItemType.TASK,
                    title = title,
                    notes = notes,
                    startDate = null,
                    dueDate = dueDateIso?.takeIf { it.isNotBlank() },
                    dueTimeMinutes = null,
                    startAtUtcMillis = null,
                    endAtUtcMillis = null,
                    parentPlanId = null,
                    steps = emptyList(),
                    reminders = emptyList(),
                    priority = priority,
                    category = category
                ).onSuccess { newId ->
                    emitFeedback(
                        if (dueDateIso.isNullOrBlank()) "Quick note saved."
                        else "Quick note added for today."
                    )
                    onCreated(newId)
                }.onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not save quick note.")
                }
            }
        }
    }

    fun updateItem(
        item: PlannerItem,
        type: ItemType,
        title: String,
        notes: String?,
        startDate: String?,
        dueDate: String?,
        dueTimeMinutes: Int?,
        startAtUtcMillis: Long?,
        endAtUtcMillis: Long?,
        parentPlanId: String?,
        updatedReminders: List<ReminderDraft>? = null,
        imagePaths: List<String>? = null,
        priority: TaskPriority? = null,
        category: TaskCategory? = null,
        onUpdated: () -> Unit = {}
    ) {
        val currentState = uiState.value as? PlannerUiState.Ready
        val existingReminders = currentState?.remindersFor(item.id) ?: emptyList()

        viewModelScope.launch {
            withSavingState {
                repository.updateItemAndRebuildReminders(
                    userId = currentUserId,
                    itemId = item.id,
                    type = type,
                    title = title,
                    notes = notes,
                    completed = item.completed,
                    startDate = startDate,
                    dueDate = dueDate,
                    dueTimeMinutes = dueTimeMinutes,
                    startAtUtcMillis = startAtUtcMillis,
                    endAtUtcMillis = endAtUtcMillis,
                    parentPlanId = parentPlanId,
                    existingReminders = existingReminders,
                    updatedReminderDrafts = updatedReminders,
                    imagePaths = imagePaths,
                    priority = priority,
                    category = category
                ).onSuccess {
                    emitFeedback("Changes saved and reminders updated.")
                    onUpdated()
                }.onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not update item.")
                }
            }
        }
    }

    fun updateItemPriority(item: PlannerItem, priority: TaskPriority) {
        viewModelScope.launch {
            repository.updateItemPriority(currentUserId, item.id, priority)
                .onSuccess { emitFeedback("Priority set to ${priority.label}.") }
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Could not update priority.") }
        }
    }

    fun updateItemCategory(item: PlannerItem, category: TaskCategory) {
        viewModelScope.launch {
            repository.updateItemCategory(currentUserId, item.id, category)
                .onSuccess { emitFeedback("Category set to ${category.label}.") }
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Could not update category.") }
        }
    }

    fun updateItemPhotos(item: PlannerItem, newPaths: List<String>) {
        val removed = item.imagePaths.filter { it !in newPaths }
        viewModelScope.launch {
            repository.updateItemImages(currentUserId, item.id, newPaths)
                .onSuccess {
                    if (removed.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            PhotoAttachmentStore.deletePhotoFiles(removed)
                        }
                    }
                    emitFeedback(
                        if (newPaths.size > item.imagePaths.size) "Picture added to ${item.title}."
                        else "Pictures updated."
                    )
                }
                .onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not update pictures.")
                }
        }
    }

    fun updateItemAudioMemo(item: PlannerItem, audioMemoPath: String?, durationSeconds: Int) {
        val oldPath = item.audioMemoPath
        viewModelScope.launch {
            repository.updateItemAudioMemo(currentUserId, item.id, audioMemoPath, durationSeconds)
                .onSuccess {
                    if (!oldPath.isNullOrBlank() && oldPath != audioMemoPath) {
                        withContext(Dispatchers.IO) {
                            TaskAudioMemoManager.deleteMemoFile(oldPath)
                        }
                    }
                    emitFeedback(
                        if (!audioMemoPath.isNullOrBlank()) {
                            "Voice memo saved (${TaskAudioMemoManager.formatSeconds(durationSeconds)})."
                        } else {
                            "Voice memo removed."
                        }
                    )
                }
                .onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not update voice memo.")
                }
        }
    }

    fun toggleItemCompleted(item: PlannerItem, completed: Boolean) {
        val ready = uiState.value as? PlannerUiState.Ready
        val parentPlan = if (item.itemType() == ItemType.TASK && !item.parentPlanId.isNullOrBlank()) {
            ready?.items?.firstOrNull { it.id == item.parentPlanId }
        } else null

        val celebrateId = when {
            completed && parentPlan != null && ready != null -> {
                val before = ready.progressFor(parentPlan)
                if (before.totalCount > 0 && before.completedCount + 1 == before.totalCount) parentPlan.id else null
            }
            completed && item.itemType() == ItemType.PLAN && ready != null -> {
                val before = ready.progressFor(item)
                if (before.totalCount == 0) item.id else null
            }
            else -> null
        }

        viewModelScope.launch {
            repository.toggleItemCompleted(currentUserId, item.id, completed)
                .onSuccess {
                    if (celebrateId != null) {
                        _celebratingItemId.value = celebrateId
                    }
                }
                .onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not update status.")
                }
        }
    }

    fun deleteItem(item: PlannerItem, onDeleted: () -> Unit = {}) {
        val ready = uiState.value as? PlannerUiState.Ready
        val itemSteps = ready?.steps?.filter { it.itemId == item.id } ?: emptyList()
        val itemReminders = ready?.reminders?.filter { it.itemId == item.id } ?: emptyList()

        viewModelScope.launch {
            withSavingState {
                repository.deleteItemCascade(currentUserId, item.id, itemSteps, itemReminders)
                    .onSuccess {
                        withContext(Dispatchers.IO) {
                            TaskAudioMemoManager.deleteMemoFile(item.audioMemoPath)
                        }
                        emitFeedback("${item.itemType().label} deleted.")
                        onDeleted()
                    }
                    .onFailure { e ->
                        emitFeedback(e.localizedMessage ?: "Could not delete item.")
                    }
            }
        }
    }

    fun addStep(itemId: String, title: String, dueDate: String?, dueTimeMinutes: Int?) {
        val ready = uiState.value as? PlannerUiState.Ready
        val nextOrder = (ready?.steps?.filter { it.itemId == itemId }?.maxOfOrNull { it.sortOrder } ?: -1) + 1

        viewModelScope.launch {
            repository.addStep(currentUserId, itemId, title, dueDate, dueTimeMinutes, nextOrder)
                .onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not add step.")
                }
        }
    }

    fun toggleUnifiedStep(step: UnifiedStep, completed: Boolean) {
        val ready = uiState.value as? PlannerUiState.Ready
        val parentItem = ready?.items?.firstOrNull { it.id == step.parentItemId }

        val celebrateId = if (completed && parentItem != null && ready != null) {
            val before = ready.progressFor(parentItem)
            if (!step.completed && before.totalCount > 0 && before.completedCount + 1 == before.totalCount) {
                parentItem.id
            } else null
        } else null

        viewModelScope.launch {
            val result = if (step.isLinkedTask) {
                repository.toggleItemCompleted(currentUserId, step.id, completed)
            } else {
                repository.toggleStepCompleted(currentUserId, step.id, completed)
            }
            result.onSuccess {
                if (celebrateId != null) {
                    _celebratingItemId.value = celebrateId
                }
            }.onFailure { e ->
                emitFeedback(e.localizedMessage ?: "Could not update step.")
            }
        }
    }

    fun logStepTimeSpent(step: UnifiedStep, addedMinutes: Int) {
        if (step.isLinkedTask || addedMinutes <= 0) return
        viewModelScope.launch {
            repository.addStepTimeSpent(currentUserId, step.id, addedMinutes)
                .onSuccess { emitFeedback("Logged +${addedMinutes}m on “${step.title}”.") }
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Could not log milestone time.") }
        }
    }

    fun deleteStep(step: UnifiedStep) {
        viewModelScope.launch {
            val result = if (step.isLinkedTask) {
                val ready = uiState.value as? PlannerUiState.Ready
                val item = ready?.items?.firstOrNull { it.id == step.id }
                if (item != null) {
                    repository.deleteItemCascade(
                        currentUserId,
                        item.id,
                        ready.steps.filter { it.itemId == item.id },
                        ready.reminders.filter { it.itemId == item.id }
                    )
                } else Result.success(Unit)
            } else {
                repository.deleteStep(currentUserId, step.id)
            }
            result.onFailure { e ->
                emitFeedback(e.localizedMessage ?: "Could not remove step.")
            }
        }
    }

    fun addReminder(item: PlannerItem, kind: ReminderKind, offsetMinutes: Int, localHour: Int, localMinute: Int) {
        viewModelScope.launch {
            repository.addReminder(currentUserId, item, kind, offsetMinutes, localHour, localMinute)
                .onSuccess { emitFeedback("${kind.label} added.") }
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Could not add reminder.") }
        }
    }

    fun snoozeReminder(reminder: PlannerReminder, snoozeMinutes: Int) {
        val targetMillis = System.currentTimeMillis() + snoozeMinutes.toLong() * 60_000L
        viewModelScope.launch {
            repository.snoozeReminderOccurrence(currentUserId, reminder.id, targetMillis)
                .onSuccess {
                    val label = when (snoozeMinutes) {
                        15 -> "15 minutes"
                        60 -> "1 hour"
                        1440 -> "tomorrow"
                        else -> "$snoozeMinutes min"
                    }
                    emitFeedback("Snoozed this reminder for $label.")
                }
                .onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not snooze reminder.")
                }
        }
    }

    fun dismissReminder(reminder: PlannerReminder) {
        viewModelScope.launch {
            repository.dismissReminder(currentUserId, reminder.id)
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Could not dismiss reminder.") }
        }
    }

    fun deleteReminder(reminder: PlannerReminder) {
        viewModelScope.launch {
            repository.deleteReminder(currentUserId, reminder.id)
                .onFailure { e -> emitFeedback(e.localizedMessage ?: "Could not delete reminder.") }
        }
    }

    fun createClassSchedule(
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
        colorKey: ClassColorKey,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            withSavingState {
                repository.createClassSchedule(
                    userId = currentUserId,
                    subject = subject,
                    room = room,
                    repeatMode = repeatMode,
                    daysOfWeek = daysOfWeek,
                    dayOfMonth = dayOfMonth,
                    startTimeMinutes = startTimeMinutes,
                    endTimeMinutes = endTimeMinutes,
                    semesterStartDate = semesterStartDate,
                    semesterMonths = semesterMonths,
                    notifyEnabled = notifyEnabled,
                    notifyOffsetMinutes = notifyOffsetMinutes,
                    colorKey = colorKey
                ).onSuccess {
                    emitFeedback(
                        if (notifyEnabled) "$subject added to your class schedule with reminders."
                        else "$subject added to your class schedule."
                    )
                    onSaved()
                }.onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not save class.")
                }
            }
        }
    }

    fun updateClassSchedule(
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
        colorKey: ClassColorKey,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            withSavingState {
                repository.updateClassSchedule(
                    userId = currentUserId,
                    scheduleId = scheduleId,
                    subject = subject,
                    room = room,
                    repeatMode = repeatMode,
                    daysOfWeek = daysOfWeek,
                    dayOfMonth = dayOfMonth,
                    startTimeMinutes = startTimeMinutes,
                    endTimeMinutes = endTimeMinutes,
                    semesterStartDate = semesterStartDate,
                    semesterMonths = semesterMonths,
                    notifyEnabled = notifyEnabled,
                    notifyOffsetMinutes = notifyOffsetMinutes,
                    colorKey = colorKey
                ).onSuccess {
                    emitFeedback("Updated $subject.")
                    onSaved()
                }.onFailure { e ->
                    emitFeedback(e.localizedMessage ?: "Could not update class.")
                }
            }
        }
    }

    fun deleteClassSchedule(schedule: ClassScheduleItem, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            withSavingState {
                repository.deleteClassSchedule(currentUserId, schedule.id)
                    .onSuccess {
                        emitFeedback("Removed ${schedule.subject} from class schedule.")
                        onDeleted()
                    }
                    .onFailure { e ->
                        emitFeedback(e.localizedMessage ?: "Could not remove class.")
                    }
            }
        }
    }

    private companion object {
        const val TAG = "PlannerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
