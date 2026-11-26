package com.example.ui.components

import com.example.audio.BunnyVoiceMood
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.DailyHabitSnapshot
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerStep
import com.example.data.UnifiedStep
import java.util.Calendar

enum class BunnyTransientReaction {
    NONE,
    PATTED,
    CHEEK_PINCHED,
    JUST_COMPLETED,
    CHECK_IN_REPLY,
    LISTENING,
    BUNNY_AUDIO_FEEDBACK,
    HABIT_REWARD
}

data class BunnyHabitRewardOutcome(
    val rewarded: Boolean,
    val allDoneToday: Boolean,
    val isStreakMilestone: Boolean,
    val dialogue: String,
    val soundMood: BunnyVoiceMood
)

enum class BunnyBubbleTopic {
    GREETING,
    REMINDER,
    MISSED
}

enum class BunnyAutoEvent {
    NONE,
    TASK_COMPLETED_HAPPY,
    DEADLINE_MISSED_SAD
}

data class BunnyTaskStatusSnapshot(
    val missedCount: Int,
    val firstMissedTitle: String?,
    val firstMissedWhen: String?,
    val firstMissedItemId: String?,
    val nextTaskTitle: String?,
    val nextTaskWhen: String?,
    val nextTaskItemId: String?,
    val upcomingClassLabel: String?,
    val totalCompletedCount: Int,
    val totalTodayRemaining: Int
)

data class BunnyStateChangeOutcome(
    val triggeredEvent: BunnyAutoEvent,
    val nextTransientReaction: BunnyTransientReaction,
    val nextTopic: BunnyBubbleTopic,
    val soundCue: BunnyVoiceMood?,
    val dialogueOverride: String?,
    val actionItemId: String?,
    val showMoodReplyChips: Boolean
)

/**
 * Pure domain logic layer tracking task/step completion and deadline state
 * to drive the Month Bunny companion's visual expressions and 8-bit sound cues.
 */
object BunnyCompanionStateLogic {

    fun analyzeTasks(
        items: List<PlannerItem>,
        steps: List<PlannerStep>,
        classSchedules: List<ClassScheduleItem>,
        nowMillis: Long
    ): BunnyTaskStatusSnapshot {
        val today = CalendarDate.today(nowMillis)
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val itemTitleById = items.associate { it.id to it.title }

        data class TaskEntry(
            val itemId: String,
            val title: String,
            val whenLabel: String,
            val sortMillis: Long
        )

        val missedEntries = mutableListOf<TaskEntry>()
        val upcomingEntries = mutableListOf<TaskEntry>()

        items.filter { !it.completed }.forEach { item ->
            when (item.itemType()) {
                ItemType.EVENT -> {
                    val startMs = item.startAtUtcMillis
                    val endMs = item.endAtUtcMillis ?: startMs?.plus(3_600_000L)
                    val calDate = startMs?.let { CalendarDate.fromUtcMillis(it) } ?: CalendarDate.parse(item.dueDate)
                    if (endMs != null && nowMillis > endMs) {
                        val rel = calDate?.let { PlannerDateUtils.formatRelativeDateLabel(it, today) } ?: "earlier"
                        val time = startMs?.let { PlannerDateUtils.formatUtcMillisTime(it) }
                        val label = if (time != null) "$rel at $time" else rel
                        missedEntries += TaskEntry(item.id, item.title, label, endMs)
                    } else if (startMs != null && calDate != null) {
                        val daysAhead = (calDate.toLocalEpochMillis(0, 0) - today.toLocalEpochMillis(0, 0)) / 86_400_000L
                        if (daysAhead in 0..7) {
                            val rel = PlannerDateUtils.formatRelativeDateLabel(calDate, today)
                            val time = PlannerDateUtils.formatUtcMillisTime(startMs)
                            upcomingEntries += TaskEntry(item.id, item.title, "$rel at $time", startMs)
                        }
                    }
                }
                ItemType.TASK, ItemType.PLAN -> {
                    val due = CalendarDate.parse(item.dueDate)
                    if (due != null) {
                        val dueMins = item.dueTimeMinutes
                        val deadlineMs = due.toLocalEpochMillis(
                            (dueMins ?: (23 * 60 + 59)) / 60,
                            (dueMins ?: (23 * 60 + 59)) % 60
                        )
                        val isMissed = due < today || (due == today && dueMins != null && nowMinutes > dueMins)
                        val rel = PlannerDateUtils.formatRelativeDateLabel(due, today)
                        val whenText = if (dueMins != null) "$rel at ${PlannerDateUtils.formatTimeOfDay(dueMins)}" else rel
                        if (isMissed) {
                            missedEntries += TaskEntry(item.id, item.title, whenText, deadlineMs)
                        } else {
                            val daysAhead = (due.toLocalEpochMillis(0, 0) - today.toLocalEpochMillis(0, 0)) / 86_400_000L
                            if (daysAhead in 0..7) {
                                upcomingEntries += TaskEntry(item.id, item.title, whenText, deadlineMs)
                            }
                        }
                    }
                }
            }
        }

        // Overdue / upcoming milestones & steps
        steps.filter { !it.completed && !it.dueDate.isNullOrBlank() }.forEach { step ->
            val due = CalendarDate.parse(step.dueDate) ?: return@forEach
            val unified = UnifiedStep(
                id = step.id,
                parentItemId = step.itemId,
                title = step.title,
                completed = step.completed,
                dueDate = step.dueDate,
                dueTimeMinutes = step.dueTimeMinutes,
                isLinkedTask = false,
                sortOrder = step.sortOrder
            )
            val parentTitle = itemTitleById[step.itemId]
            val fullTitle = if (!parentTitle.isNullOrBlank()) "${step.title} ($parentTitle)" else step.title
            val mins = step.dueTimeMinutes ?: (23 * 60 + 59)
            val deadlineMs = due.toLocalEpochMillis(mins / 60, mins % 60)
            val rel = PlannerDateUtils.formatRelativeDateLabel(due, today)
            val whenText = if (step.dueTimeMinutes != null) "$rel at ${PlannerDateUtils.formatTimeOfDay(step.dueTimeMinutes)}" else rel

            if (unified.isOverdue(nowMillis)) {
                missedEntries += TaskEntry(step.itemId, fullTitle, whenText, deadlineMs)
            } else if (due == today) {
                upcomingEntries += TaskEntry(step.itemId, fullTitle, whenText, deadlineMs)
            }
        }

        val sortedMissed = missedEntries.sortedByDescending { it.sortMillis }
        val sortedUpcoming = upcomingEntries.sortedBy { it.sortMillis }

        val nextClass = classSchedules
            .filter { it.occursOnDate(today) }
            .sortedBy { it.startTimeMinutes }
            .firstOrNull { it.endTimeMinutes > nowMinutes }
        val classLabel = nextClass?.let { cls ->
            val time = PlannerDateUtils.formatTimeOfDay(cls.startTimeMinutes)
            if (!cls.room.isNullOrBlank()) "${cls.subject} at $time (${cls.room})" else "${cls.subject} at $time"
        }

        val totalCompleted = items.count { it.completed } + steps.count { it.completed }
        val todayRemaining = items.count { !it.completed && it.appearsOnDate(today) }

        return BunnyTaskStatusSnapshot(
            missedCount = sortedMissed.size,
            firstMissedTitle = sortedMissed.firstOrNull()?.title,
            firstMissedWhen = sortedMissed.firstOrNull()?.whenLabel,
            firstMissedItemId = sortedMissed.firstOrNull()?.itemId,
            nextTaskTitle = sortedUpcoming.firstOrNull()?.title,
            nextTaskWhen = sortedUpcoming.firstOrNull()?.whenLabel,
            nextTaskItemId = sortedUpcoming.firstOrNull()?.itemId,
            upcomingClassLabel = classLabel,
            totalCompletedCount = totalCompleted,
            totalTodayRemaining = todayRemaining
        )
    }

    /**
     * Resolves automatic visual & 8-bit audio transitions whenever tasks are completed
     * or a deadline transitions from upcoming to missed.
     */
    fun resolveAutomaticTransition(
        previous: BunnyTaskStatusSnapshot,
        current: BunnyTaskStatusSnapshot,
        currentTopic: BunnyBubbleTopic,
        currentReaction: BunnyTransientReaction
    ): BunnyStateChangeOutcome {
        val completedIncreased = current.totalCompletedCount > previous.totalCompletedCount
        val missedCleared = previous.missedCount > 0 && current.missedCount == 0
        val deadlineNewlyMissed = current.missedCount > previous.missedCount

        return when {
            completedIncreased || missedCleared -> {
                val msg = when {
                    missedCleared -> "Yay!! You finished your missed task! Now I can smile brightly again!"
                    current.missedCount > 0 -> "Woohoo! One task bloomed! Just ${current.missedCount} overdue left — we've got this!"
                    current.totalTodayRemaining == 0 -> "Hooray! Every plan for today is done! I'm so proud of you!"
                    else -> "Yay! Awesome job completing that task! Keep blooming!"
                }
                BunnyStateChangeOutcome(
                    triggeredEvent = BunnyAutoEvent.TASK_COMPLETED_HAPPY,
                    nextTransientReaction = BunnyTransientReaction.JUST_COMPLETED,
                    nextTopic = if (current.missedCount > 0) BunnyBubbleTopic.MISSED else BunnyBubbleTopic.REMINDER,
                    soundCue = BunnyVoiceMood.HAPPY_PAT,
                    dialogueOverride = msg,
                    actionItemId = null,
                    showMoodReplyChips = false
                )
            }
            deadlineNewlyMissed -> {
                val missedTitle = current.firstMissedTitle ?: "a scheduled task"
                val whenStr = current.firstMissedWhen?.let { " ($it)" }.orEmpty()
                BunnyStateChangeOutcome(
                    triggeredEvent = BunnyAutoEvent.DEADLINE_MISSED_SAD,
                    nextTransientReaction = BunnyTransientReaction.NONE,
                    nextTopic = BunnyBubbleTopic.MISSED,
                    soundCue = BunnyVoiceMood.SAD_MISSED,
                    dialogueOverride = "Oh no… \"$missedTitle\"$whenStr just passed its deadline. Finish it so I can smile again!",
                    actionItemId = current.firstMissedItemId,
                    showMoodReplyChips = false
                )
            }
            else -> {
                val nextTopic = if (current.missedCount > 0 && currentReaction == BunnyTransientReaction.NONE) {
                    BunnyBubbleTopic.MISSED
                } else {
                    currentTopic
                }
                BunnyStateChangeOutcome(
                    triggeredEvent = BunnyAutoEvent.NONE,
                    nextTransientReaction = currentReaction,
                    nextTopic = nextTopic,
                    soundCue = null,
                    dialogueOverride = null,
                    actionItemId = null,
                    showMoodReplyChips = currentReaction == BunnyTransientReaction.NONE && nextTopic == BunnyBubbleTopic.GREETING
                )
            }
        }
    }

    fun analyzeHabitsAndRewards(
        previous: DailyHabitSnapshot?,
        current: DailyHabitSnapshot
    ): BunnyHabitRewardOutcome {
        if (previous == null) {
            return BunnyHabitRewardOutcome(
                rewarded = false,
                allDoneToday = false,
                isStreakMilestone = false,
                dialogue = "",
                soundMood = BunnyVoiceMood.HAPPY_PAT
            )
        }

        val completedHabit = current.completedTodayCount > previous.completedTodayCount &&
            current.completedTodayCount > 0
        if (!completedHabit) {
            return BunnyHabitRewardOutcome(
                rewarded = false,
                allDoneToday = false,
                isStreakMilestone = false,
                dialogue = "",
                soundMood = BunnyVoiceMood.HAPPY_PAT
            )
        }

        val allDoneToday = current.totalHabitsCount > 0 &&
            current.completedTodayCount >= current.totalHabitsCount
        val milestoneDays = setOf(2, 3, 5, 7, 12, 14, 21, 30)
        val streakIncreased = current.overallStreakDays > previous.overallStreakDays
        val isStreakMilestone = current.bunnyStage.level > previous.bunnyStage.level ||
            (streakIncreased && (current.overallStreakDays in milestoneDays ||
                (current.overallStreakDays > 0 && current.overallStreakDays % 7 == 0)))

        val dialogue = when {
            allDoneToday && current.bunnyStage.level > previous.bunnyStage.level ->
                "All ${current.totalHabitsCount} daily habits complete AND Level Up to ${current.bunnyStage.title}!! (${current.overallStreakDays}d streak!) Enjoy my special celebratory 8-bit fanfare!"
            allDoneToday && isStreakMilestone ->
                "Woohoo!! All ${current.totalHabitsCount} daily habits done today — and a ${current.overallStreakDays}-day streak milestone! Playing my celebratory 8-bit victory melody!"
            allDoneToday ->
                "Woohoo!! All ${current.totalHabitsCount} daily habits completed today! (${current.overallStreakDays.coerceAtLeast(1)}d streak · 100% Bunny Happiness!) Hear my celebratory 8-bit fanfare!"
            current.bunnyStage.level > previous.bunnyStage.level ->
                "Level up!! Your consistent habits evolved me into ${current.bunnyStage.title}! Enjoy this 8-bit fanfare!"
            isStreakMilestone ->
                "Amazing ${current.overallStreakDays}-day habit streak!! Here's a special 8-bit reward melody just for you!"
            else ->
                "Yay! Habit ${current.completedTodayCount}/${current.totalHabitsCount} done today (${current.overallStreakDays.coerceAtLeast(1)}d streak)! My happiness is rising!"
        }

        return BunnyHabitRewardOutcome(
            rewarded = true,
            allDoneToday = allDoneToday,
            isStreakMilestone = isStreakMilestone,
            dialogue = dialogue,
            soundMood = BunnyVoiceMood.HABIT_REWARD
        )
    }

    /**
     * Computes a 0..100 mascot happiness percentage that integrates daily habit completion,
     * active habit streak days, and missed-task penalties.
     */
    fun computeHabitHappinessPercent(
        habitSnapshot: DailyHabitSnapshot,
        missedTasksCount: Int = 0
    ): Int {
        if (missedTasksCount > 0 && habitSnapshot.completedTodayCount == 0) {
            return (35 - (missedTasksCount - 1) * 10).coerceIn(15, 35)
        }
        val habitRatio = if (habitSnapshot.totalHabitsCount > 0) {
            habitSnapshot.completedTodayCount.toFloat() / habitSnapshot.totalHabitsCount.toFloat()
        } else {
            0f
        }
        val base = 50
        val dailyGain = (habitRatio * 38f).toInt()
        val streakBonus = (habitSnapshot.overallStreakDays.coerceAtMost(7) * 2)
        val allDoneBonus = if (habitSnapshot.totalHabitsCount > 0 &&
            habitSnapshot.completedTodayCount >= habitSnapshot.totalHabitsCount
        ) 12 else 0
        val missedPenalty = missedTasksCount * 22
        return (base + dailyGain + streakBonus + allDoneBonus - missedPenalty).coerceIn(15, 100)
    }

    fun buildAudioMemoFeedback(
        itemTitle: String,
        durationSeconds: Int,
        isTaskCompleted: Boolean,
        isNewRecording: Boolean
    ): Pair<String, BunnyVoiceMood> {
        val safeSec = durationSeconds.coerceAtLeast(1)
        val cleanTitle = itemTitle.ifBlank { "this task" }
        val durationLabel = if (safeSec >= 60) {
            "1m ${safeSec % 60}s"
        } else {
            "${safeSec}s"
        }

        return if (isTaskCompleted) {
            val msg = when {
                safeSec <= 5 ->
                    "Ooh, a quick $durationLabel voice note on completed \"$cleanTitle\"! Short, sweet, and done!"
                safeSec <= 20 ->
                    "I loved listening to your $durationLabel memo for \"$cleanTitle\" — and it's already checked off! Proud bunny!"
                else ->
                    "Wow, what a thoughtful $durationLabel reflection on \"$cleanTitle\"! Finishing it makes me super happy!"
            }
            msg to BunnyVoiceMood.HAPPY_PAT
        } else {
            val msg = when {
                safeSec <= 5 ->
                    if (isNewRecording) {
                        "Got your snappy $durationLabel voice reminder for \"$cleanTitle\"! I'll help nudge you until it's done!"
                    } else {
                        "Short $durationLabel note on \"$cleanTitle\"! Ready to knock it out now?"
                    }
                safeSec <= 20 ->
                    if (isNewRecording) {
                        "Saved your $durationLabel memo for \"$cleanTitle\"! Everything sounds super clear — you've got this!"
                    } else {
                        "Listening back to your $durationLabel plan for \"$cleanTitle\" — one small step at a time!"
                    }
                else ->
                    if (isNewRecording) {
                        "Whoa, a detailed $durationLabel voice plan for \"$cleanTitle\"! With that prep, you're going to crush it!"
                    } else {
                        "I heard all $durationLabel of \"$cleanTitle\"! Let's start the first piece together!"
                    }
            }
            msg to BunnyVoiceMood.AUDIO_MEMO_FEEDBACK
        }
    }

    fun resolveVisualExpression(
        snapshot: BunnyTaskStatusSnapshot,
        transientReaction: BunnyTransientReaction,
        isTalking: Boolean,
        isListening: Boolean = false,
        habitSnapshot: DailyHabitSnapshot? = null
    ): BunnyCompanionExpression {
        if (isListening) return BunnyCompanionExpression.LISTENING
        return when (transientReaction) {
            BunnyTransientReaction.PATTED -> BunnyCompanionExpression.PATTED
            BunnyTransientReaction.CHEEK_PINCHED -> BunnyCompanionExpression.CHEEK_PINCHED
            BunnyTransientReaction.LISTENING -> BunnyCompanionExpression.LISTENING
            BunnyTransientReaction.JUST_COMPLETED,
            BunnyTransientReaction.CHECK_IN_REPLY,
            BunnyTransientReaction.BUNNY_AUDIO_FEEDBACK,
            BunnyTransientReaction.HABIT_REWARD -> BunnyCompanionExpression.HAPPY
            BunnyTransientReaction.NONE -> when {
                snapshot.missedCount > 0 -> BunnyCompanionExpression.SAD
                snapshot.totalTodayRemaining == 0 && snapshot.totalCompletedCount > 0 -> BunnyCompanionExpression.HAPPY
                habitSnapshot != null && habitSnapshot.completedTodayCount > 0 -> BunnyCompanionExpression.HAPPY
                isTalking -> BunnyCompanionExpression.TALKING
                else -> BunnyCompanionExpression.NEUTRAL
            }
        }
    }

    fun greetingForTimeOfDay(nowMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning! How are you feeling today?"
            hour < 17 -> "Good afternoon! How are you doing today?"
            else -> "Good evening! How was your day so far?"
        }
    }
}
