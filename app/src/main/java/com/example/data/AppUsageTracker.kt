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
 * Loads SharedPreferences asynchronously off the Main thread, prunes stale daily keys older
 * than 365 days, and supports clean reset on sign-out.
 */
class AppUsageTracker private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private var isForeground = false
    private var sessionStartElapsedMs: Long = 0L
    private var lastFlushedElapsedMs: Long = 0L
    private var activeDateIso: String = CalendarDate.today().toIsoString()
    private var cachedHistoryDateIso: String = ""
    private var cachedPastSixDays: List<DailyUsageEntry> = emptyList()
    private var cachedPastSixTotalSeconds: Long = 0L
    private var cachedPastSixActiveDays: Int = 0
    private var cachedTodayStoredSeconds: Long = 0L
    private var cachedAllTimeStoredSeconds: Long = 0L
    private var tickerJob: Job? = null

    private val _usageSummary = MutableStateFlow(AppUsageSummary())
    val usageSummary: StateFlow<AppUsageSummary> = _usageSummary.asStateFlow()

    init {
        scope.launch {
            val today = CalendarDate.today()
            synchronized(lock) {
                pruneOldKeysLocked(today)
                refreshCacheFromPrefsLocked(today)
                _usageSummary.value = buildSnapshotLocked(unflushedSeconds = 0L, sessionSeconds = 0L)
            }
        }
    }

    fun onForegroundResume() {
        val nowElapsed = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (isForeground) return
            isForeground = true
            sessionStartElapsedMs = nowElapsed
            lastFlushedElapsedMs = nowElapsed
            activeDateIso = CalendarDate.today().toIsoString()
        }
        scope.launch {
            val today = CalendarDate.today()
            synchronized(lock) {
                refreshCacheFromPrefsLocked(today)
                val sessionSec = if (isForeground) {
                    ((SystemClock.elapsedRealtime() - sessionStartElapsedMs) / 1_000L).coerceAtLeast(0L)
                } else 0L
                _usageSummary.value = buildSnapshotLocked(unflushedSeconds = 0L, sessionSeconds = sessionSec)
            }
        }
        startTicker()
    }

    fun onForegroundPause() {
        val nowElapsed = SystemClock.elapsedRealtime()
        tickerJob?.cancel()
        tickerJob = null
        synchronized(lock) {
            if (!isForeground) return
            isForeground = false
            val flushAnchor = lastFlushedElapsedMs
            val dateToFlush = activeDateIso
            sessionStartElapsedMs = 0L
            lastFlushedElapsedMs = 0L
            scope.launch {
                synchronized(lock) {
                    val deltaSec = ((nowElapsed - flushAnchor) / 1_000L).coerceAtLeast(0L)
                    if (deltaSec > 0L) {
                        persistDeltaLocked(dateToFlush, deltaSec)
                    }
                    _usageSummary.value = buildSnapshotLocked(unflushedSeconds = 0L, sessionSeconds = 0L)
                }
            }
        }
    }

    fun resetAllUsage() {
        synchronized(lock) {
            prefs.edit().clear().apply()
            cachedHistoryDateIso = ""
            cachedPastSixDays = emptyList()
            cachedPastSixTotalSeconds = 0L
            cachedPastSixActiveDays = 0
            cachedTodayStoredSeconds = 0L
            cachedAllTimeStoredSeconds = 0L
            _usageSummary.value = AppUsageSummary()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                delay(5_000L)
                val nowElapsed = SystemClock.elapsedRealtime()
                val today = CalendarDate.today()
                val todayIso = today.toIsoString()
                synchronized(lock) {
                    if (!isForeground) return@launch
                    tickCount++
                    if (todayIso != activeDateIso || tickCount % 2 == 0) {
                        flushLocked(nowElapsed)
                        if (todayIso != activeDateIso) {
                            activeDateIso = todayIso
                            pruneOldKeysLocked(today)
                            refreshCacheFromPrefsLocked(today)
                        }
                    }
                    val unflushedSec = ((nowElapsed - lastFlushedElapsedMs) / 1_000L).coerceAtLeast(0L)
                    val sessionSec = ((nowElapsed - sessionStartElapsedMs) / 1_000L).coerceAtLeast(0L)
                    _usageSummary.value = buildSnapshotLocked(
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
        persistDeltaLocked(activeDateIso, deltaSec)
        lastFlushedElapsedMs += deltaSec * 1_000L
    }

    private fun persistDeltaLocked(dateIso: String, deltaSec: Long) {
        val dayKey = dayPrefKey(dateIso)
        val prevDay = prefs.getLong(dayKey, 0L)
        val prevTotal = prefs.getLong(KEY_ALL_TIME_SECONDS, 0L)
        val updatedDay = prevDay + deltaSec
        val updatedAllTime = prevTotal + deltaSec
        prefs.edit()
            .putLong(dayKey, updatedDay)
            .putLong(KEY_ALL_TIME_SECONDS, updatedAllTime)
            .apply()
        if (dateIso == cachedHistoryDateIso) {
            cachedTodayStoredSeconds = updatedDay
        }
        cachedAllTimeStoredSeconds = updatedAllTime
    }

    private fun pruneOldKeysLocked(today: CalendarDate) {
        val cutoff = today.plusDays(-RETENTION_DAYS)
        val staleKeys = prefs.all.keys.filter { key ->
            if (!key.startsWith("day_")) return@filter false
            val parsed = CalendarDate.parse(key.removePrefix("day_")) ?: return@filter true
            parsed < cutoff
        }
        if (staleKeys.isNotEmpty()) {
            val editor = prefs.edit()
            staleKeys.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    private fun refreshCacheFromPrefsLocked(today: CalendarDate) {
        val todayIso = today.toIsoString()
        val pastList = ArrayList<DailyUsageEntry>(6)
        var pastTotal = 0L
        var pastActive = 0
        for (offset in -6..-1) {
            val d = today.plusDays(offset)
            val iso = d.toIsoString()
            val stored = prefs.getLong(dayPrefKey(iso), 0L)
            pastTotal += stored
            if (stored > 0L) pastActive++
            pastList.add(
                DailyUsageEntry(
                    date = d,
                    shortDayLabel = PlannerDateUtils.weekdayShortLabel(d.dayOfWeekIndex()),
                    seconds = stored,
                    isToday = false
                )
            )
        }
        cachedHistoryDateIso = todayIso
        cachedPastSixDays = pastList
        cachedPastSixTotalSeconds = pastTotal
        cachedPastSixActiveDays = pastActive
        cachedTodayStoredSeconds = prefs.getLong(dayPrefKey(todayIso), 0L)
        cachedAllTimeStoredSeconds = prefs.getLong(KEY_ALL_TIME_SECONDS, 0L)
    }

    private fun buildSnapshotLocked(unflushedSeconds: Long, sessionSeconds: Long): AppUsageSummary {
        val today = CalendarDate.today()
        val todayIso = today.toIsoString()
        if (cachedHistoryDateIso != todayIso) {
            refreshCacheFromPrefsLocked(today)
        }

        val todayTotal = cachedTodayStoredSeconds + unflushedSeconds
        val weekTotal = cachedPastSixTotalSeconds + todayTotal
        val activeDays = cachedPastSixActiveDays + if (todayTotal > 0L) 1 else 0
        val allTime = cachedAllTimeStoredSeconds + unflushedSeconds
        val dailyAvg = if (activeDays > 0) weekTotal / activeDays else 0L

        val history = ArrayList<DailyUsageEntry>(7).apply {
            addAll(cachedPastSixDays)
            add(
                DailyUsageEntry(
                    date = today,
                    shortDayLabel = "Today",
                    seconds = todayTotal,
                    isToday = true
                )
            )
        }

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
        private const val RETENTION_DAYS = 365

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
