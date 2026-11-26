package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class HabitIconKey(val label: String) {
    WATER("Hydrate"),
    BOOK("Read"),
    STRETCH("Move"),
    SPARKLE("Tidy"),
    MEDITATE("Breathe"),
    LEAF("Nature"),
    JOURNAL("Write"),
    HEART("Self-care");

    companion object {
        fun fromRaw(raw: String?): HabitIconKey =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SPARKLE
    }
}

enum class BunnyGrowthStage(
    val level: Int,
    val title: String,
    val subtitle: String,
    val minStreakDays: Int,
    val nextStreakTarget: Int?,
    val targetScale: Float
) {
    SPROUT_KIT(
        level = 1,
        title = "Sprout Kit",
        subtitle = "Check off a daily goal to wake your little bunny",
        minStreakDays = 0,
        nextStreakTarget = 2,
        targetScale = 0.74f
    ),
    HOPPING_KIT(
        level = 2,
        title = "Hopping Kit",
        subtitle = "2+ day streak · Ears perked and ready to hop",
        minStreakDays = 2,
        nextStreakTarget = 5,
        targetScale = 0.85f
    ),
    MEADOW_BUNNY(
        level = 3,
        title = "Meadow Bunny",
        subtitle = "5+ day streak · Wearing its first forget-me-not bloom",
        minStreakDays = 5,
        nextStreakTarget = 12,
        targetScale = 0.95f
    ),
    BLOOMING_BUNNY(
        level = 4,
        title = "Blooming Bunny",
        subtitle = "12+ day streak · Crowned in forget-me-not blossoms",
        minStreakDays = 12,
        nextStreakTarget = 30,
        targetScale = 1.03f
    ),
    GOLDEN_GUARDIAN(
        level = 5,
        title = "Golden Guardian",
        subtitle = "30+ day streak · Radiant guardian of your garden",
        minStreakDays = 30,
        nextStreakTarget = null,
        targetScale = 1.10f
    );

    companion object {
        fun forStreak(streakDays: Int): BunnyGrowthStage {
            return when {
                streakDays >= 30 -> GOLDEN_GUARDIAN
                streakDays >= 12 -> BLOOMING_BUNNY
                streakDays >= 5 -> MEADOW_BUNNY
                streakDays >= 2 -> HOPPING_KIT
                else -> SPROUT_KIT
            }
        }
    }
}

data class DailyHabit(
    val id: String,
    val title: String,
    val iconKey: HabitIconKey,
    val colorKey: ClassColorKey,
    val sortOrder: Int,
    val completedToday: Boolean,
    val currentStreakDays: Int,
    val last7DaysCompleted: List<Boolean> // oldest -> today (size 7)
)

data class DailyHabitSnapshot(
    val habits: List<DailyHabit> = emptyList(),
    val completedTodayCount: Int = 0,
    val totalHabitsCount: Int = 0,
    val overallStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val weeklyConsistencyPercent: Int = 0,
    val totalCheckInsAllTime: Int = 0,
    val checkInCountByDate: Map<CalendarDate, Int> = emptyMap(),
    val bunnyStage: BunnyGrowthStage = BunnyGrowthStage.SPROUT_KIT,
    val progressToNextStage: Float = 0f
)

/**
 * Room-backed repository for small, consistent daily habits and streak-driven Bunny Growth.
 */
class DailyHabitRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = LocalPlannerDatabase.getInstance(appContext).plannerDao()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.w(TAG, "DailyHabitRepository background warning: ${throwable.message}")
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    val snapshot: StateFlow<DailyHabitSnapshot> = combine(
        dao.observeDailyHabits().catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "observeDailyHabits failed: ${err.message}")
            emit(emptyList())
        },
        dao.observeHabitCheckIns().catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "observeHabitCheckIns failed: ${err.message}")
            emit(emptyList())
        }
    ) { habitEntities, checkInEntities ->
        buildSnapshot(habitEntities, checkInEntities, CalendarDate.today())
    }
        .catch { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "DailyHabit snapshot error: ${err.message}")
            emit(DailyHabitSnapshot())
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DailyHabitSnapshot()
        )

    fun addHabit(
        title: String,
        iconKey: HabitIconKey = HabitIconKey.SPARKLE,
        colorKey: ClassColorKey = ClassColorKey.SKY
    ) {
        val clean = title.trim().take(60)
        if (clean.isEmpty()) return
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val nextOrder = snapshot.value.habits.size
                dao.upsertDailyHabit(
                    LocalDailyHabitEntity(
                        id = "hab_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                        title = clean,
                        iconKey = iconKey.name,
                        colorKey = colorKey.name,
                        sortOrder = nextOrder,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                com.example.widgets.WidgetUpdateManager.refreshAllWidgets(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed adding daily habit: ${e.message}")
            }
        }
    }

    fun toggleHabitForDate(habitId: String, date: CalendarDate = CalendarDate.today()) {
        scope.launch {
            try {
                val dateIso = date.toIsoString()
                val key = "$habitId|$dateIso"
                val existing = dao.getHabitCheckIn(key)
                if (existing != null) {
                    dao.deleteHabitCheckInByKey(key)
                } else {
                    dao.upsertHabitCheckIn(
                        LocalHabitCheckInEntity(
                            checkInKey = key,
                            habitId = habitId,
                            dateIso = dateIso,
                            completedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
                com.example.widgets.WidgetUpdateManager.refreshAllWidgets(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed toggling habit check-in: ${e.message}")
            }
        }
    }

    fun deleteHabit(habitId: String) {
        scope.launch {
            try {
                dao.deleteDailyHabitCascade(habitId)
                com.example.widgets.WidgetUpdateManager.refreshAllWidgets(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting daily habit: ${e.message}")
            }
        }
    }

    fun checkInsInYear(year: Int): Int {
        return snapshot.value.checkInCountByDate.entries
            .filter { it.key.year == year }
            .sumOf { it.value }
    }

    companion object {
        private const val TAG = "DailyHabitRepository"

        @Volatile
        private var INSTANCE: DailyHabitRepository? = null

        fun getInstance(context: Context): DailyHabitRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyHabitRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildSnapshot(
            habitEntities: List<LocalDailyHabitEntity>,
            checkInEntities: List<LocalHabitCheckInEntity>,
            today: CalendarDate
        ): DailyHabitSnapshot {
            val todayIso = today.toIsoString()
            val checkInsByHabit = HashMap<String, MutableSet<String>>()
            val checkInCountByDate = HashMap<CalendarDate, Int>()
            val activeHabitIds = habitEntities.map { it.id }.toHashSet()

            for (c in checkInEntities) {
                if (c.habitId !in activeHabitIds) continue
                checkInsByHabit.getOrPut(c.habitId) { HashSet() }.add(c.dateIso)
                val parsed = CalendarDate.parse(c.dateIso)
                if (parsed != null) {
                    checkInCountByDate[parsed] = (checkInCountByDate[parsed] ?: 0) + 1
                }
            }

            val last7Dates = (-6..0).map { today.plusDays(it) }
            val last7Isos = last7Dates.map { it.toIsoString() }

            val habits = habitEntities.map { entity ->
                val doneIsos = checkInsByHabit[entity.id].orEmpty()
                val streak = computeDateStreak(doneIsos, today)
                DailyHabit(
                    id = entity.id,
                    title = entity.title,
                    iconKey = HabitIconKey.fromRaw(entity.iconKey),
                    colorKey = ClassColorKey.fromRaw(entity.colorKey),
                    sortOrder = entity.sortOrder,
                    completedToday = todayIso in doneIsos,
                    currentStreakDays = streak,
                    last7DaysCompleted = last7Isos.map { it in doneIsos }
                )
            }

            val activeDateIsos = checkInCountByDate.keys.map { it.toIsoString() }.toSet()
            val overallStreak = computeDateStreak(activeDateIsos, today)
            val bestStreak = computeBestStreak(checkInCountByDate.keys)

            val weeklyPossible = habits.size * 7
            val weeklyDone = habits.sumOf { h -> h.last7DaysCompleted.count { it } }
            val weeklyPct = if (weeklyPossible > 0) {
                ((weeklyDone * 100f) / weeklyPossible.toFloat()).toInt().coerceIn(0, 100)
            } else {
                0
            }

            val stage = BunnyGrowthStage.forStreak(overallStreak)
            val progressToNext = if (stage.nextStreakTarget == null) {
                1f
            } else {
                val span = (stage.nextStreakTarget - stage.minStreakDays).coerceAtLeast(1)
                val gained = (overallStreak - stage.minStreakDays).coerceAtLeast(0)
                // If user has 0-day streak yet completed something today, show gentle initial growth
                val bonus = if (overallStreak == 0 && habits.any { it.completedToday }) 0.35f else 0f
                ((gained.toFloat() / span.toFloat()) + bonus).coerceIn(0f, 1f)
            }

            return DailyHabitSnapshot(
                habits = habits,
                completedTodayCount = habits.count { it.completedToday },
                totalHabitsCount = habits.size,
                overallStreakDays = overallStreak,
                bestStreakDays = bestStreak,
                weeklyConsistencyPercent = weeklyPct,
                totalCheckInsAllTime = checkInCountByDate.values.sum(),
                checkInCountByDate = checkInCountByDate,
                bunnyStage = stage,
                progressToNextStage = progressToNext
            )
        }

        private fun computeDateStreak(doneIsos: Set<String>, today: CalendarDate): Int {
            if (doneIsos.isEmpty()) return 0
            var cursor = if (today.toIsoString() in doneIsos) today else today.plusDays(-1)
            var count = 0
            while (cursor.toIsoString() in doneIsos) {
                count++
                cursor = cursor.plusDays(-1)
            }
            return count
        }

        private fun computeBestStreak(dates: Set<CalendarDate>): Int {
            if (dates.isEmpty()) return 0
            val sorted = dates.sorted()
            var best = 0
            var running = 0
            var prev: CalendarDate? = null
            for (d in sorted) {
                running = if (prev != null && prev.daysUntil(d) == 1) running + 1 else 1
                if (running > best) best = running
                prev = d
            }
            return best
        }
    }
}
