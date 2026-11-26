package com.example.focus

import android.content.Context
import com.example.audio.BunnyFeedbackManager
import com.example.data.CalendarDate
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(FocusSessionState())
    val state: StateFlow<FocusSessionState> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val focusMins = prefs.getInt(KEY_FOCUS_MINS, 25).coerceIn(5, 90)
            val breakMins = prefs.getInt(KEY_BREAK_MINS, 5).coerceIn(1, 30)
            val blockApps = prefs.getBoolean(KEY_BLOCK_APPS, true)
            val todayIso = CalendarDate.today().toIsoString()
            val storedIso = prefs.getString(KEY_TODAY_ISO, null)
            val sameDay = storedIso == todayIso
            val sessionsToday = if (sameDay) prefs.getInt(KEY_SESSIONS_TODAY, 0) else 0
            val minsToday = if (sameDay) prefs.getInt(KEY_MINS_TODAY, 0) else 0
            val sessionsAll = prefs.getInt(KEY_SESSIONS_ALL, 0)
            val minsAll = prefs.getInt(KEY_MINS_ALL, 0)

            _state.value = FocusSessionState(
                phase = FocusPhase.IDLE,
                isRunning = false,
                focusDurationMinutes = focusMins,
                breakDurationMinutes = breakMins,
                totalSeconds = focusMins * 60,
                remainingSeconds = focusMins * 60,
                blockAppsDuringFocus = blockApps,
                completedSessionsToday = sessionsToday,
                totalFocusMinutesToday = minsToday,
                totalSessionsAllTime = sessionsAll,
                totalFocusMinutesAllTime = minsAll
            )
            initialized = true
        }
    }

    fun isBlockingAppsNow(): Boolean = _state.value.isBlockingDistractionsNow

    fun setFocusDuration(context: Context, minutes: Int) {
        initialize(context)
        val safe = minutes.coerceIn(5, 90)
        val current = _state.value
        val updated = if (current.phase == FocusPhase.IDLE || current.phase == FocusPhase.COMPLETED) {
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
        _state.value = _state.value.copy(linkedTaskTitle = title?.takeIf { it.isNotBlank() })
    }

    fun startOrResume(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
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
        _state.value = updated
        startTicker(appContext)
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    fun pause(context: Context) {
        initialize(context)
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false)
        DoomscrollGuardManager.syncServiceState(context.applicationContext)
    }

    fun reset(context: Context) {
        initialize(context)
        tickerJob?.cancel()
        tickerJob = null
        val cur = _state.value
        _state.value = cur.copy(
            phase = FocusPhase.IDLE,
            isRunning = false,
            totalSeconds = cur.focusDurationMinutes * 60,
            remainingSeconds = cur.focusDurationMinutes * 60
        )
        DoomscrollGuardManager.syncServiceState(context.applicationContext)
    }

    fun startBreakNow(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        val cur = _state.value
        val secs = cur.breakDurationMinutes * 60
        _state.value = cur.copy(
            phase = FocusPhase.BREAK,
            isRunning = true,
            totalSeconds = secs,
            remainingSeconds = secs
        )
        startTicker(appContext)
        DoomscrollGuardManager.syncServiceState(appContext)
    }

    fun finishEarlyAndCount(context: Context) {
        initialize(context)
        val appContext = context.applicationContext
        val cur = _state.value
        if (cur.phase == FocusPhase.FOCUS) {
            val elapsedMins = ((cur.totalSeconds - cur.remainingSeconds) / 60).coerceAtLeast(1)
            completeFocusPhase(appContext, elapsedMins)
        } else {
            reset(appContext)
        }
    }

    private fun startTicker(appContext: Context) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val cur = _state.value
                if (!cur.isRunning) break
                val nextRem = (cur.remainingSeconds - 1).coerceAtLeast(0)
                if (nextRem > 0) {
                    _state.value = cur.copy(remainingSeconds = nextRem)
                } else {
                    if (cur.phase == FocusPhase.FOCUS) {
                        completeFocusPhase(appContext, cur.focusDurationMinutes)
                    } else {
                        // Break finished -> ready for next focus session
                        BunnyFeedbackManager.getInstance(appContext).onSaveSuccess()
                        _state.value = cur.copy(
                            phase = FocusPhase.IDLE,
                            isRunning = false,
                            totalSeconds = cur.focusDurationMinutes * 60,
                            remainingSeconds = cur.focusDurationMinutes * 60
                        )
                        DoomscrollGuardManager.syncServiceState(appContext)
                    }
                    break
                }
            }
        }
    }

    private fun completeFocusPhase(appContext: Context, earnedMinutes: Int) {
        tickerJob?.cancel()
        tickerJob = null
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
        DoomscrollGuardManager.syncServiceState(appContext)
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
            .apply()
    }
}
