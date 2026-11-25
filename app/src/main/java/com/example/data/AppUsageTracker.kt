package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
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

data class DailyUsageEntry(
    val date: CalendarDate,
    val shortDayLabel: String,
    val seconds: Long,
    val isToday: Boolean
)

data class AppUsageSummary(
    val todaySeconds: Long = 0L,
    val currentSessionSeconds: Long = 0L,
    val last7DaysTotalSeconds: Long = 0L,
    val dailyAverageSeconds: Long = 0L,
    val allTimeSeconds: Long = 0L,
    val dailyHistory: List<DailyUsageEntry> = emptyList()
)

/**
 * Lifecycle-aware foreground usage tracker for Bunny Memo.
 * Records real active time spent inside the app per calendar day into SharedPreferences
 * with zero special permissions required.
 */
class AppUsageTracker private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val lock = Any()
    private var isForeground = false
    private var sessionStartElapsedMs: Long = 0L
    private var lastFlushedElapsedMs: Long = 0L
    private var activeDateIso: String = CalendarDate.today().toIsoString()
    private var tickerJob: Job? = null

    private val _usageSummary = MutableStateFlow(buildSnapshot(unflushedSeconds = 0L, sessionSeconds = 0L))
    val usageSummary: StateFlow<AppUsageSummary> = _usageSummary.asStateFlow()

    fun onForegroundResume() {
        val nowElapsed = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (isForeground) return
            isForeground = true
            sessionStartElapsedMs = nowElapsed
            lastFlushedElapsedMs = nowElapsed
            activeDateIso = CalendarDate.today().toIsoString()
            _usageSummary.value = buildSnapshot(unflushedSeconds = 0L, sessionSeconds = 0L)
        }
        startTicker()
    }

    fun onForegroundPause() {
        val nowElapsed = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (!isForeground) return
            flushLocked(nowElapsed)
            isForeground = false
            sessionStartElapsedMs = 0L
            lastFlushedElapsedMs = 0L
            _usageSummary.value = buildSnapshot(unflushedSeconds = 0L, sessionSeconds = 0L)
        }
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                delay(1_000L)
                val nowElapsed = SystemClock.elapsedRealtime()
                val todayIso = CalendarDate.today().toIsoString()
                synchronized(lock) {
                    if (!isForeground) return@launch
                    tickCount++
                    // Flush on midnight boundary or every 10 seconds to stay crash-safe
                    if (todayIso != activeDateIso || tickCount % 10 == 0) {
                        flushLocked(nowElapsed)
                        activeDateIso = todayIso
                    }
                    val unflushedSec = ((nowElapsed - lastFlushedElapsedMs) / 1_000L).coerceAtLeast(0L)
                    val sessionSec = ((nowElapsed - sessionStartElapsedMs) / 1_000L).coerceAtLeast(0L)
                    _usageSummary.value = buildSnapshot(
                        unflushedSeconds = unflushedSec,
                        sessionSeconds = sessionSec
                    )
                }
            }
        }
    }

    private fun flushLocked(nowElapsed: Long) {
        val deltaSec = ((nowElapsed - lastFlushedElapsedMs) / 1_000L).coerceAtLeast(0L)
        if (deltaSec <= 0L) return
        val dayKey = dayPrefKey(activeDateIso)
        val prevDay = prefs.getLong(dayKey, 0L)
        val prevTotal = prefs.getLong(KEY_ALL_TIME_SECONDS, 0L)
        prefs.edit()
            .putLong(dayKey, prevDay + deltaSec)
            .putLong(KEY_ALL_TIME_SECONDS, prevTotal + deltaSec)
            .apply()
        lastFlushedElapsedMs += deltaSec * 1_000L
    }

    private fun buildSnapshot(unflushedSeconds: Long, sessionSeconds: Long): AppUsageSummary {
        val today = CalendarDate.today()
        val todayIso = today.toIsoString()
        val history = ArrayList<DailyUsageEntry>(7)
        var weekTotal = 0L
        var activeDays = 0

        for (offset in -6..0) {
            val d = today.plusDays(offset)
            val iso = d.toIsoString()
            val stored = prefs.getLong(dayPrefKey(iso), 0L)
            val effective = if (iso == todayIso) stored + unflushedSeconds else stored
            weekTotal += effective
            if (effective > 0L) activeDays++
            history.add(
                DailyUsageEntry(
                    date = d,
                    shortDayLabel = if (offset == 0) "Today" else PlannerDateUtils.weekdayShortLabel(d.dayOfWeekIndex()),
                    seconds = effective,
                    isToday = offset == 0
                )
            )
        }

        val todayTotal = history.lastOrNull()?.seconds ?: unflushedSeconds
        val allTime = prefs.getLong(KEY_ALL_TIME_SECONDS, 0L) + unflushedSeconds
        val dailyAvg = if (activeDays > 0) weekTotal / activeDays else 0L

        return AppUsageSummary(
            todaySeconds = todayTotal,
            currentSessionSeconds = sessionSeconds,
            last7DaysTotalSeconds = weekTotal,
            dailyAverageSeconds = dailyAvg,
            allTimeSeconds = allTime,
            dailyHistory = history
        )
    }

    private fun dayPrefKey(isoDate: String): String = "day_$isoDate"

    companion object {
        private const val PREFS_NAME = "bunny_memo_app_usage_v1"
        private const val KEY_ALL_TIME_SECONDS = "all_time_seconds"

        @Volatile
        private var instance: AppUsageTracker? = null

        fun getInstance(context: Context): AppUsageTracker {
            return instance ?: synchronized(this) {
                instance ?: AppUsageTracker(context.applicationContext).also { instance = it }
            }
        }

        fun formatDuration(totalSeconds: Long, includeSeconds: Boolean = true): String {
            val safe = totalSeconds.coerceAtLeast(0L)
            val hours = safe / 3600L
            val minutes = (safe % 3600L) / 60L
            val seconds = safe % 60L
            return when {
                hours > 0L -> "${hours}h ${minutes}m"
                minutes > 0L && includeSeconds -> "${minutes}m ${seconds}s"
                minutes > 0L -> "${minutes}m"
                else -> "${seconds}s"
            }
        }
    }
}
