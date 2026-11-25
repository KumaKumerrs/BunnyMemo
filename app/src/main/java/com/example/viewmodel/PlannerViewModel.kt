package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CalendarDate
import com.example.data.ClassColorKey
import com.example.data.ClassRepeatMode
import com.example.data.ClassScheduleItem
import com.example.data.InboxNotificationEntry
import com.example.data.ItemProgress
import com.example.data.ItemType
import com.example.data.PlannerDataSource
import com.example.data.PlannerItem
import com.example.data.PlannerLogic
import com.example.data.PlannerReminder
import com.example.data.PlannerStep
import com.example.data.ReminderDraft
import com.example.data.ReminderKind
import com.example.data.StepDraft
import com.example.data.UnifiedStep
import com.example.data.UserProfile
import com.example.notifications.DailyDigestSummary
import com.example.notifications.DailyReminderConfig
import com.example.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        fun progressFor(item: PlannerItem): ItemProgress =
            PlannerLogic.computeProgress(item, steps, items, nowMillis)

        fun stepsFor(item: PlannerItem): List<UnifiedStep> =
            PlannerLogic.stepsForItem(item, steps, items)

        fun remindersFor(itemId: String): List<PlannerReminder> =
            reminders.filter { it.itemId == itemId }.sortedBy { it.effectiveTriggerMillis() }

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

class PlannerViewModel(
    private val repository: PlannerDataSource,
    private val reminderScheduler: ReminderScheduler,
    private val currentUserId: String
) : ViewModel() {

    private val nowMillisFlow = MutableStateFlow(System.currentTimeMillis())
    private val dailyConfigFlow = MutableStateFlow(reminderScheduler.loadDailyConfig())

    private val _celebratingItemId = MutableStateFlow<String?>(null)
    val celebratingItemId: StateFlow<String?> = _celebratingItemId.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val baseDataFlow = combine(
        repository.observeProfile(currentUserId),
        repository.observeItems(currentUserId),
        repository.observeSteps(currentUserId),
        repository.observeReminders(currentUserId),
        repository.observeClassSchedules(currentUserId)
    ) { profile, items, steps, reminders, classSchedules ->
        PlannerDataSnapshot(profile, items, steps, reminders, classSchedules)
    }

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
        // Sync OS alarms strictly off the main UI thread whenever schedules/reminders/config change
        viewModelScope.launch(Dispatchers.Default) {
            combine(baseDataFlow, dailyConfigFlow) { snapshot, dailyConfig ->
                snapshot to dailyConfig
            }
                .catch { /* Handled by uiState stream */ }
                .collectLatest { (snapshot, dailyConfig) ->
                    val now = System.currentTimeMillis()
                    reminderScheduler.syncReminders(snapshot.reminders, snapshot.items, snapshot.steps, now)
                    reminderScheduler.syncClassReminders(snapshot.classSchedules, now)
                    val digestSummary = reminderScheduler.buildDailyDigestSummary(snapshot.items, snapshot.steps, now)
                    reminderScheduler.syncDailyDigest(dailyConfig, digestSummary, now)
                }
        }
    }

    val uiState: StateFlow<PlannerUiState> = combine(
        baseDataFlow,
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlannerUiState.Loading
        )

    fun setDailyDigestEnabled(enabled: Boolean) {
        val updated = dailyConfigFlow.value.copy(enabled = enabled)
        dailyConfigFlow.value = updated
        reminderScheduler.saveDailyConfig(updated)
        _feedbackMessage.value = if (enabled) {
            "Gentle daily forget-me-not reminders enabled."
        } else {
            "Daily forget-me-not reminders paused."
        }
    }

    fun updateDailyDigestTime(hour: Int, minute: Int) {
        val updated = dailyConfigFlow.value.copy(hour = hour, minute = minute, enabled = true)
        dailyConfigFlow.value = updated
        reminderScheduler.saveDailyConfig(updated)
        _feedbackMessage.value = "Daily forget-me-not reminder time updated."
    }

    fun sendDailyDigestPreview(): Boolean {
        val ready = uiState.value as? PlannerUiState.Ready ?: return false
        val sent = reminderScheduler.postDailyDigestPreview(ready.dailyDigestSummary)
        _feedbackMessage.value = if (sent) {
            "Sent a gentle forget-me-not preview notification."
        } else {
            "Allow device notifications to preview your daily forget-me-not alert."
        }
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
            _isSaving.value = true
            repository.saveDedication(currentUserId, text, markSeen)
                .onSuccess {
                    if (!markSeen) _feedbackMessage.value = "Dedication note saved."
                }
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not save dedication."
                }
            _isSaving.value = false
        }
    }

    fun updateDefaultReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateDefaultReminderTime(currentUserId, hour, minute)
                .onSuccess { _feedbackMessage.value = "Default reminder time updated." }
                .onFailure { e -> _feedbackMessage.value = e.localizedMessage ?: "Update failed." }
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
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
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
                reminders = reminders
            ).onSuccess { newId ->
                _feedbackMessage.value = "${type.label} added."
                onCreated(newId)
            }.onFailure { e ->
                _feedbackMessage.value = e.localizedMessage ?: "Could not save item."
            }
            _isSaving.value = false
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
        onUpdated: () -> Unit = {}
    ) {
        val currentState = uiState.value as? PlannerUiState.Ready
        val existingReminders = currentState?.remindersFor(item.id) ?: emptyList()

        viewModelScope.launch {
            _isSaving.value = true
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
                updatedReminderDrafts = updatedReminders
            ).onSuccess {
                _feedbackMessage.value = "Changes saved and reminders updated."
                onUpdated()
            }.onFailure { e ->
                _feedbackMessage.value = e.localizedMessage ?: "Could not update item."
            }
            _isSaving.value = false
        }
    }

    fun toggleItemCompleted(item: PlannerItem, completed: Boolean) {
        val ready = uiState.value as? PlannerUiState.Ready
        // Check if toggling a linked task completes its parent plan
        val parentPlan = if (item.itemType() == ItemType.TASK && !item.parentPlanId.isNullOrBlank()) {
            ready?.items?.firstOrNull { it.id == item.parentPlanId }
        } else null

        if (completed && parentPlan != null && ready != null) {
            val before = ready.progressFor(parentPlan)
            if (before.totalCount > 0 && before.completedCount + 1 == before.totalCount) {
                _celebratingItemId.value = parentPlan.id
            }
        } else if (completed && item.itemType() == ItemType.PLAN && ready != null) {
            val before = ready.progressFor(item)
            if (before.totalCount == 0) {
                _celebratingItemId.value = item.id
            }
        }

        viewModelScope.launch {
            repository.toggleItemCompleted(currentUserId, item.id, completed)
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not update status."
                }
        }
    }

    fun deleteItem(item: PlannerItem, onDeleted: () -> Unit = {}) {
        val ready = uiState.value as? PlannerUiState.Ready
        val itemSteps = ready?.steps?.filter { it.itemId == item.id } ?: emptyList()
        val itemReminders = ready?.reminders?.filter { it.itemId == item.id } ?: emptyList()

        viewModelScope.launch {
            _isSaving.value = true
            repository.deleteItemCascade(currentUserId, item.id, itemSteps, itemReminders)
                .onSuccess {
                    _feedbackMessage.value = "${item.itemType().label} deleted."
                    onDeleted()
                }
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not delete item."
                }
            _isSaving.value = false
        }
    }

    fun addStep(itemId: String, title: String, dueDate: String?, dueTimeMinutes: Int?) {
        val ready = uiState.value as? PlannerUiState.Ready
        val nextOrder = (ready?.steps?.filter { it.itemId == itemId }?.maxOfOrNull { it.sortOrder } ?: -1) + 1

        viewModelScope.launch {
            repository.addStep(currentUserId, itemId, title, dueDate, dueTimeMinutes, nextOrder)
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not add step."
                }
        }
    }

    fun toggleUnifiedStep(step: UnifiedStep, completed: Boolean) {
        val ready = uiState.value as? PlannerUiState.Ready
        val parentItem = ready?.items?.firstOrNull { it.id == step.parentItemId }

        if (completed && parentItem != null && ready != null) {
            val before = ready.progressFor(parentItem)
            if (!step.completed && before.totalCount > 0 && before.completedCount + 1 == before.totalCount) {
                _celebratingItemId.value = parentItem.id
            }
        }

        viewModelScope.launch {
            val result = if (step.isLinkedTask) {
                repository.toggleItemCompleted(currentUserId, step.id, completed)
            } else {
                repository.toggleStepCompleted(currentUserId, step.id, completed)
            }
            result.onFailure { e ->
                _feedbackMessage.value = e.localizedMessage ?: "Could not update step."
            }
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
                _feedbackMessage.value = e.localizedMessage ?: "Could not remove step."
            }
        }
    }

    fun addReminder(item: PlannerItem, kind: ReminderKind, offsetMinutes: Int, localHour: Int, localMinute: Int) {
        viewModelScope.launch {
            repository.addReminder(currentUserId, item, kind, offsetMinutes, localHour, localMinute)
                .onSuccess { _feedbackMessage.value = "${kind.label} added." }
                .onFailure { e -> _feedbackMessage.value = e.localizedMessage ?: "Could not add reminder." }
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
                    _feedbackMessage.value = "Snoozed this reminder for $label."
                }
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not snooze reminder."
                }
        }
    }

    fun dismissReminder(reminder: PlannerReminder) {
        viewModelScope.launch {
            repository.dismissReminder(currentUserId, reminder.id)
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not dismiss reminder."
                }
        }
    }

    fun deleteReminder(reminder: PlannerReminder) {
        viewModelScope.launch {
            repository.deleteReminder(currentUserId, reminder.id)
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not delete reminder."
                }
        }
    }

    fun seedSampleScrapbookPlan(onCreated: (String) -> Unit = {}) {
        val ready = uiState.value as? PlannerUiState.Ready
        val hour = ready?.profile?.defaultReminderHour ?: 9
        val minute = ready?.profile?.defaultReminderMinute ?: 0
        viewModelScope.launch {
            _isSaving.value = true
            repository.seedWeekendScrapbookSample(currentUserId, hour, minute)
                .onSuccess { id ->
                    _feedbackMessage.value = "Added sample plan: Make a weekend scrapbook."
                    onCreated(id)
                }
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not add sample plan."
                }
            _isSaving.value = false
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
            _isSaving.value = true
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
                _feedbackMessage.value = if (notifyEnabled) {
                    "$subject added to your class schedule with reminders."
                } else {
                    "$subject added to your class schedule."
                }
                onSaved()
            }.onFailure { e ->
                _feedbackMessage.value = e.localizedMessage ?: "Could not save class."
            }
            _isSaving.value = false
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
            _isSaving.value = true
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
                _feedbackMessage.value = "Updated $subject."
                onSaved()
            }.onFailure { e ->
                _feedbackMessage.value = e.localizedMessage ?: "Could not update class."
            }
            _isSaving.value = false
        }
    }

    fun deleteClassSchedule(schedule: ClassScheduleItem, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            repository.deleteClassSchedule(currentUserId, schedule.id)
                .onSuccess {
                    _feedbackMessage.value = "Removed ${schedule.subject} from class schedule."
                    onDeleted()
                }
                .onFailure { e ->
                    _feedbackMessage.value = e.localizedMessage ?: "Could not remove class."
                }
            _isSaving.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    private companion object {
        const val TAG = "PlannerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
