package com.example.focus

import android.content.Context
import android.os.SystemClock
import com.example.audio.BunnyFeedbackManager
import com.example.data.CalendarDate
import com.example.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class FocusPhase {
    IDLE,
    FOCUS,
    BREAK,
    COMPLETED
}

data class FocusSessionState(
    val phase: FocusPhase = FocusPhase.IDLE,
    val isRunning: Boolean = false,
    val focusDurationMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val blockAppsDuringFocus: Boolean = true,
    val linkedTaskTitle: String? = null,
    val completedSessionsToday: Int = 0,
    val totalFocusMinutesToday: Int = 0,
    val totalSessionsAllTime: Int = 0,
    val totalFocusMinutesAllTime: Int = 0
) {
    val isBlockingDistractionsNow: Boolean
        get() = phase == FocusPhase.FOCUS && isRunning && blockAppsDuringFocus

    val progressFraction: Float
        get() = if (totalSeconds <= 0) 0f
        else ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    fun formattedCountdown(): String {
        val safe = remainingSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return String.format("%02d:%02d", mins, secs)
    }
}

/**
 * Pomodoro-style Focus Session timer manager that coordinates with [DoomscrollGuardService]
 * to block user-selected distracting apps while a focus timer is actively running.
 *
 * Persists target end timestamps (wall-clock epoch + monotonic elapsedRealtime) so timers
 * survive Doze throttling and process restarts without losing time, and rolls over daily
 * session counters automatically at midnight.
 */
object FocusSessionManager {
    private const val PREFS_NAME = "bunny_memo_focus_session_v1"
    private const val KEY_FOCUS_MINS = "focus_minutes"
    private const val KEY_BREAK_MINS = "break_minutes"
    private const val KEY_BLOCK_APPS = "block_apps"
    private const val KEY_TODAY_ISO = "stats_today_iso"
    private const val KEY_SESSIONS_TODAY = "sessions_today"
    private const val KEY_MINS_TODAY = "mins_today"
    private const val KEY_SESSIONS_ALL = "sessions_all"
    private const val KEY_MINS_ALL = "mins_all"

    // Persisted active timer state (BE-21)
    private const val KEY_ACTIVE_PHASE = "active_phase"
    private const val KEY_ACTIVE_RUNNING = "active_running"
    private const val KEY_ACTIVE_TOTAL_SEC = "active_total_sec"
    private const val KEY_ACTIVE_REMAINING_SEC = "active_remaining_sec"
    private const val KEY_TARGET_END_EPOCH_MS = "target_end_epoch_ms"
    private const val KEY_TARGET_END_ELAPSED_MS = "target_end_elapsed_ms"
    private const val KEY_LINKED_TASK_TITLE = "linked_task_title"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    @Volatile
    private var activeTargetEndElapsedMs: Long = 0L

    @Volatile
    private var activeTargetEndEpochMs: Long = 0L

    @Volatile
    private var lastStatsDateIso: String = ""

    private val _state = MutableStateFlow(FocusSessionState())
    val state: StateFlow<FocusSessionState> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (initialized) {
            rolloverDayIfNeeded(appContext)
            reconcileActiveClock(appContext)
            return
        }
        synchronized(this) {
            if (initialized) return
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val focusMins = prefs.getInt(KEY_FOCUS_MINS, 25).coerceIn(5, 90)
            val breakMins = prefs.getInt(KEY_BREAK_MINS, 5).coerceIn(1, 30)
            val blockApps = prefs.getBoolean(KEY_BLOCK_APPS, true)
            val todayIso = CalendarDate.today().toIsoString()
            val storedIso = prefs.getString(KEY_TODAY_ISO, null)
            val sameDay = storedIso == todayIso
            lastStatsDateIso = todayIso

            val sessionsToday = if (sameDay) prefs.getInt(KEY_SESSIONS_TODAY, 0) else 0
            val minsToday = if (sameDay) prefs.getInt(KEY_MINS_TODAY, 0) else 0
            val sessionsAll = prefs.getInt(KEY_SESSIONS_ALL, 0)
            val minsAll = prefs.getInt(KEY_MINS_ALL, 0)

            val savedPhase = prefs.getString(KEY_ACTIVE_PHASE, null)
                ?.let { raw -> runCatching { FocusPhase.valueOf(raw) }.getOrNull() }
                ?: FocusPhase.IDLE
            val savedRunning = prefs.getBoolean(KEY_ACTIVE_RUNNING, false)
            val savedTotalSec = prefs.getInt(KEY_ACTIVE_TOTAL_SEC, focusMins * 60).coerceAtLeast(60)
            val savedRemSec = prefs.getInt(KEY_ACTIVE_REMAINING_SEC, savedTotalSec).coerceIn(0, savedTotalSec)
            val savedEndEpoch = prefs.getLong(KEY_TARGET_END_EPOCH_MS, 0L)
            val savedEndElapsed = prefs.getLong(KEY_TARGET_END_ELAPSED_MS, 0L)
            val savedTaskTitle = prefs.getString(KEY_LINKED_TASK_TITLE, null)?.takeIf { it.isNotBlank() }

            var restoredPhase = savedPhase
            var restoredRunning = savedRunning
            var restoredTotal = if (savedPhase == FocusPhase.IDLE) focusMins * 60 else savedTotalSec
            var restoredRem = if (savedPhase == FocusPhase.IDLE) focusMins * 60 else savedRemSec
            var pendingFocusCompletionMinutes: Int? = null
            var pendingBreakCompletion = false

            if (savedRunning && (savedPhase == FocusPhase.FOCUS || savedPhase == FocusPhase.BREAK)) {
                val remMs = computeRemainingMillisFromSaved(
                    savedEndElapsed = savedEndElapsed,
                    savedEndEpoch = savedEndEpoch,
                    fallbackRemSec = savedRemSec
                )
                val computedRemSec = ceil(remMs / 1000.0).toInt().coerceAtLeast(0)
                if (computedRemSec <= 0) {
                    restoredRunning = false
                    restoredRem = 0
                    if (savedPhase == FocusPhase.FOCUS) {
                        pendingFocusCompletionMinutes = (savedTotalSec / 60).coerceAtLeast(1)
                    } else {
                        pendingBreakCompletion = true
                        restoredPhase = FocusPhase.IDLE
                        restoredTotal = focusMins * 60
                        restoredRem = focusMins * 60
                    }
                } else {
                    restoredRem = computedRemSec.coerceAtMost(savedTotalSec)
                    activeTargetEndElapsedMs = SystemClock.elapsedRealtime() + remMs
                    activeTargetEndEpochMs = System.currentTimeMillis() + remMs
                }
            }

            _state.value = FocusSessionState(
                phase = restoredPhase,
                isRunning = restoredRunning,
                focusDurationMinutes = focusMins,
                breakDurationMinutes = breakMins,
                totalSeconds = restoredTotal,
                remainingSeconds = restoredRem,
                blockAppsDuringFocus = blockApps,
                linkedTaskTitle = savedTaskTitle,
                completedSessionsToday = sessionsToday,
                totalFocusMinutesToday = minsToday,
                totalSessionsAllTime = sessionsAll,
                totalFocusMinutesAllTime = minsAll
            )
            initialized = true

            when {
                pendingFocusCompletionMinutes != null -> {
                    completeFocusPhase(appContext, pendingFocusCompletionMinutes, notifyUser = true)
                }
                pendingBreakCompletion -> {
                    persistPrefs(appContext, _state.value)
                    ReminderScheduler(appContext).postFocusCompletedNotification(
                        isFocusCompleted = false,
                        linkedTaskTitle = savedTaskTitle,
                        earnedMinutes = 0
                    )
                }
                restoredRunning -> {
                    startTicker(appContext)
                }
            }
        }
    }

    fun isBlockingAppsNow(): Boolean = _state.value.isBlockingDistractionsNow

    fun setFocusDuration(context: Context, minutes: Int) {
        initialize(context)
        val safe = minutes.coerceIn(5, 90)
        val current = _state.value
        val updated = if (current.phase == FocusPhase.IDLE || current.phase == FocusPhase.COMPLETED) {
            activeTargetEndElapsedMs = 0L
            activeTargetEndEpochMs = 0L
            current.copy(
                phase = FocusPhase.IDLE,
                isRunning = false,
                focusDurationMinutes = safe,
                totalSeconds = safe * 60,
                remainingSeconds = safe * 60
            )
        } else {
            current.copy(focusDurationMinutes = safe)
        }
        _state.value = updated
        persistPrefs(context, updated)
    }

    fun setBreakDuration(context: Context, minutes: Int) {
        initialize(context)
        val safe = minutes.coerceIn(1, 30)
        val updated = _state.value.copy(breakDurationMinutes = safe)
        _state.value = updated
        persistPrefs(context, updated)
    }

    fun setBlockAppsDuringFocus(context: Context, enabled: Boolean) {
        initialize(context)
        val updated = _state.value.copy(blockAppsDuringFocus = enabled)
        _state.value = updated
        persistPrefs(context, updated)
        DoomscrollGuardManager.syncServiceState(context)
    }

    fun setLinkedTaskTitle(title: String?) {
        val clean = title?.takeIf { it.isNotBlank() }
        _state.value = _state.value.copy(linkedTaskTitle = clean)
    }

    fun setLinkedTaskTitle(context: Context, title: String?) {
        initialize(context)
        val clean = title?.takeIf { it.isNotBlank() }
        val updated = _state.value.copy(linkedTaskTitle = clean)
        _state.value = updated
        persistPrefs(context, updated)
    }

    fun startOrResume(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        rolloverDayIfNeeded(appContext)
        val current = _state.value
        val updated = when (current.phase) {
            FocusPhase.IDLE, FocusPhase.COMPLETED -> {
                val secs = current.focusDurationMinutes * 60
                current.copy(
                    phase = FocusPhase.FOCUS,
                    isRunning = true,
                    totalSeconds = secs,
                    remainingSeconds = secs
                )
            }
            FocusPhase.FOCUS, FocusPhase.BREAK -> {
                current.copy(isRunning = true)
            }
        }
        val remMs = updated.remainingSeconds.coerceAtLeast(1) * 1000L
        activeTargetEndElapsedMs = SystemClock.elapsedRealtime() + remMs
        activeTargetEndEpochMs = System.currentTimeMillis() + remMs
        _state.value = updated
        persistPrefs(appContext, updated)
        startTicker(appContext)
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    fun pause(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        tickerJob?.cancel()
        tickerJob = null
        val cur = _state.value
        val accurateRem = if (cur.isRunning && activeTargetEndElapsedMs > 0L) {
            ceil((activeTargetEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000.0)
                .toInt()
                .coerceIn(0, cur.totalSeconds)
        } else {
            cur.remainingSeconds
        }
        activeTargetEndElapsedMs = 0L
        activeTargetEndEpochMs = 0L
        val updated = cur.copy(isRunning = false, remainingSeconds = accurateRem)
        _state.value = updated
        persistPrefs(appContext, updated)
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    fun reset(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        tickerJob?.cancel()
        tickerJob = null
        activeTargetEndElapsedMs = 0L
        activeTargetEndEpochMs = 0L
        val cur = _state.value
        val updated = cur.copy(
            phase = FocusPhase.IDLE,
            isRunning = false,
            totalSeconds = cur.focusDurationMinutes * 60,
            remainingSeconds = cur.focusDurationMinutes * 60
        )
        _state.value = updated
        persistPrefs(appContext, updated)
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    fun startBreakNow(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        rolloverDayIfNeeded(appContext)
        val cur = _state.value
        val secs = cur.breakDurationMinutes * 60
        val remMs = secs * 1000L
        activeTargetEndElapsedMs = SystemClock.elapsedRealtime() + remMs
        activeTargetEndEpochMs = System.currentTimeMillis() + remMs
        val updated = cur.copy(
            phase = FocusPhase.BREAK,
            isRunning = true,
            totalSeconds = secs,
            remainingSeconds = secs
        )
        _state.value = updated
        persistPrefs(appContext, updated)
        startTicker(appContext)
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    fun finishEarlyAndCount(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        rolloverDayIfNeeded(appContext)
        val cur = _state.value
        if (cur.phase == FocusPhase.FOCUS) {
            val elapsedMins = ((cur.totalSeconds - cur.remainingSeconds) / 60).coerceAtLeast(1)
            completeFocusPhase(appContext, elapsedMins, notifyUser = false)
        } else {
            reset(appContext)
        }
    }

    /**
     * Clears runtime state so unit tests or sign-out isolation can re-initialize cleanly from disk.
     */
    fun resetInMemoryForReload() {
        tickerJob?.cancel()
        tickerJob = null
        activeTargetEndElapsedMs = 0L
        activeTargetEndEpochMs = 0L
        lastStatsDateIso = ""
        _state.value = FocusSessionState()
        initialized = false
    }

    private fun rolloverDayIfNeeded(appContext: Context) {
        val todayIso = CalendarDate.today().toIsoString()
        if (lastStatsDateIso == todayIso) return
        lastStatsDateIso = todayIso
        val cur = _state.value
        if (cur.completedSessionsToday != 0 || cur.totalFocusMinutesToday != 0) {
            val updated = cur.copy(
                completedSessionsToday = 0,
                totalFocusMinutesToday = 0
            )
            _state.value = updated
            persistPrefs(appContext, updated)
        }
    }

    private fun reconcileActiveClock(appContext: Context) {
        val cur = _state.value
        if (!cur.isRunning || activeTargetEndElapsedMs <= 0L) return
        val remMs = (activeTargetEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val nextRem = ceil(remMs / 1000.0).toInt().coerceIn(0, cur.totalSeconds)
        if (nextRem <= 0) {
            if (cur.phase == FocusPhase.FOCUS) {
                completeFocusPhase(appContext, cur.focusDurationMinutes, notifyUser = true)
            } else if (cur.phase == FocusPhase.BREAK) {
                completeBreakPhase(appContext, notifyUser = true)
            }
        } else if (nextRem != cur.remainingSeconds) {
            _state.value = cur.copy(remainingSeconds = nextRem)
        }
    }

    private fun startTicker(appContext: Context) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(500L)
                rolloverDayIfNeeded(appContext)
                val cur = _state.value
                if (!cur.isRunning) break
                val remMs = if (activeTargetEndElapsedMs > 0L) {
                    (activeTargetEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                } else {
                    ((cur.remainingSeconds - 1).coerceAtLeast(0)) * 1000L
                }
                val nextRem = ceil(remMs / 1000.0).toInt().coerceIn(0, cur.totalSeconds)
                if (nextRem > 0) {
                    if (nextRem != cur.remainingSeconds) {
                        _state.value = cur.copy(remainingSeconds = nextRem)
                    }
                } else {
                    if (cur.phase == FocusPhase.FOCUS) {
                        completeFocusPhase(appContext, cur.focusDurationMinutes, notifyUser = true)
                    } else {
                        completeBreakPhase(appContext, notifyUser = true)
                    }
                    break
                }
            }
        }
    }

    private fun completeFocusPhase(appContext: Context, earnedMinutes: Int, notifyUser: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        activeTargetEndElapsedMs = 0L
        activeTargetEndEpochMs = 0L
        rolloverDayIfNeeded(appContext)
        val cur = _state.value
        val updated = cur.copy(
            phase = FocusPhase.COMPLETED,
            isRunning = false,
            remainingSeconds = 0,
            completedSessionsToday = cur.completedSessionsToday + 1,
            totalFocusMinutesToday = cur.totalFocusMinutesToday + earnedMinutes,
            totalSessionsAllTime = cur.totalSessionsAllTime + 1,
            totalFocusMinutesAllTime = cur.totalFocusMinutesAllTime + earnedMinutes
        )
        _state.value = updated
        persistPrefs(appContext, updated)
        BunnyFeedbackManager.getInstance(appContext).onCelebrateBloom()
        if (notifyUser) {
            ReminderScheduler(appContext).postFocusCompletedNotification(
                isFocusCompleted = true,
                linkedTaskTitle = cur.linkedTaskTitle,
                earnedMinutes = earnedMinutes
            )
        }
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    private fun completeBreakPhase(appContext: Context, notifyUser: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        activeTargetEndElapsedMs = 0L
        activeTargetEndEpochMs = 0L
        val cur = _state.value
        val updated = cur.copy(
            phase = FocusPhase.IDLE,
            isRunning = false,
            totalSeconds = cur.focusDurationMinutes * 60,
            remainingSeconds = cur.focusDurationMinutes * 60
        )
        _state.value = updated
        persistPrefs(appContext, updated)
        BunnyFeedbackManager.getInstance(appContext).onSaveSuccess()
        if (notifyUser) {
            ReminderScheduler(appContext).postFocusCompletedNotification(
                isFocusCompleted = false,
                linkedTaskTitle = cur.linkedTaskTitle,
                earnedMinutes = 0
            )
        }
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    private fun computeRemainingMillisFromSaved(
        savedEndElapsed: Long,
        savedEndEpoch: Long,
        fallbackRemSec: Int
    ): Long {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        // If elapsedRealtime is plausible (same boot session), prefer monotonic clock delta
        if (savedEndElapsed > 0L && savedEndEpoch > 0L) {
            val epochDelta = savedEndEpoch - nowEpoch
            val elapsedDelta = savedEndElapsed - nowElapsed
            // If device rebooted, elapsedDelta diverges wildly from epochDelta (> 60s)
            val diff = kotlin.math.abs(epochDelta - elapsedDelta)
            return if (diff < 60_000L) {
                elapsedDelta.coerceAtLeast(0L)
            } else {
                epochDelta.coerceAtLeast(0L)
            }
        }
        if (savedEndEpoch > 0L) {
            return (savedEndEpoch - nowEpoch).coerceAtLeast(0L)
        }
        return (fallbackRemSec.coerceAtLeast(0) * 1000L)
    }

    private fun persistPrefs(context: Context, st: FocusSessionState) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FOCUS_MINS, st.focusDurationMinutes)
            .putInt(KEY_BREAK_MINS, st.breakDurationMinutes)
            .putBoolean(KEY_BLOCK_APPS, st.blockAppsDuringFocus)
            .putString(KEY_TODAY_ISO, CalendarDate.today().toIsoString())
            .putInt(KEY_SESSIONS_TODAY, st.completedSessionsToday)
            .putInt(KEY_MINS_TODAY, st.totalFocusMinutesToday)
            .putInt(KEY_SESSIONS_ALL, st.totalSessionsAllTime)
            .putInt(KEY_MINS_ALL, st.totalFocusMinutesAllTime)
            .putString(KEY_ACTIVE_PHASE, st.phase.name)
            .putBoolean(KEY_ACTIVE_RUNNING, st.isRunning)
            .putInt(KEY_ACTIVE_TOTAL_SEC, st.totalSeconds)
            .putInt(KEY_ACTIVE_REMAINING_SEC, st.remainingSeconds)
            .putLong(KEY_TARGET_END_EPOCH_MS, if (st.isRunning) activeTargetEndEpochMs else 0L)
            .putLong(KEY_TARGET_END_ELAPSED_MS, if (st.isRunning) activeTargetEndElapsedMs else 0L)
            .putString(KEY_LINKED_TASK_TITLE, st.linkedTaskTitle)
            .apply()
    }
}
