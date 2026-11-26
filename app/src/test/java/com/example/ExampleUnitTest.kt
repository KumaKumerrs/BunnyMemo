package com.example

import com.example.audio.BunnySfx
import com.example.audio.BunnyVoiceMood
import com.example.audio.ChiptuneSynthesizer
import com.example.data.CalendarDate
import com.example.data.ItemType
import com.example.data.PlannerItem
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.example.ui.components.BunnyAutoEvent
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyBubbleTopic
import com.example.ui.components.BunnyCompanionExpression
import com.example.ui.components.BunnyCompanionStateLogic
import com.example.ui.components.BunnyTransientReaction
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM unit tests for Bunny Memo audio synthesis, companion logic, and planner domain models.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun meadow8BitBgm_synthesizesNonEmptyLoop() {
    val samples = ChiptuneSynthesizer.synthesizeMeadow8BitBgmLoop()
    assertTrue(samples.size > ChiptuneSynthesizer.SAMPLE_RATE * 10)
    assertTrue(samples.any { it.toInt() != 0 })
  }

  @Test
  fun allBunnySfx_synthesizeValidPcm() {
    BunnySfx.entries.forEach { sfx ->
      val pcm = ChiptuneSynthesizer.synthesizeSfx(sfx)
      assertTrue("Expected non-empty PCM for $sfx", pcm.isNotEmpty())
      assertTrue("Expected non-silent PCM for $sfx", pcm.any { it.toInt() != 0 })
    }
  }

  @Test
  fun allBunnyVoices_synthesizeValidPcm() {
    BunnyVoiceMood.entries.forEach { mood ->
      val pcm = ChiptuneSynthesizer.synthesizeBunnyVoice(mood)
      assertTrue("Expected non-empty voice PCM for $mood", pcm.isNotEmpty())
      assertTrue("Expected non-silent voice PCM for $mood", pcm.any { it.toInt() != 0 })
    }
  }

  @Test
  fun monthlyBunnyBreeds_coverAllTwelveMonths() {
    val breeds = (1..12).map { BunnyBreed.forMonth(it) }.toSet()
    assertEquals(12, breeds.size)
  }

  @Test
  fun bunnyLogic_transitionsHappyToSadOnMissedDeadlineAndBackToHappyOnComplete() {
    val today = CalendarDate(2026, 11, 25)
    val beforeDeadlineMs = today.toLocalEpochMillis(14, 0)
    val afterDeadlineMs = today.toLocalEpochMillis(16, 0)

    val pendingTask = PlannerItem(
      id = "t1",
      userId = "u1",
      type = ItemType.TASK.name,
      title = "Submit biology lab",
      completed = false,
      dueDate = today.toIsoString(),
      dueTimeMinutes = 15 * 60 // 3:00 PM
    )

    val beforeSnap = BunnyCompanionStateLogic.analyzeTasks(
      items = listOf(pendingTask),
      steps = emptyList(),
      classSchedules = emptyList(),
      nowMillis = beforeDeadlineMs
    )
    assertEquals(0, beforeSnap.missedCount)
    assertEquals(
      BunnyCompanionExpression.NEUTRAL,
      BunnyCompanionStateLogic.resolveVisualExpression(beforeSnap, BunnyTransientReaction.NONE, isTalking = false)
    )

    // Clock advances past 3:00 PM -> deadline missed!
    val missedSnap = BunnyCompanionStateLogic.analyzeTasks(
      items = listOf(pendingTask),
      steps = emptyList(),
      classSchedules = emptyList(),
      nowMillis = afterDeadlineMs
    )
    assertEquals(1, missedSnap.missedCount)
    val sadOutcome = BunnyCompanionStateLogic.resolveAutomaticTransition(
      previous = beforeSnap,
      current = missedSnap,
      currentTopic = BunnyBubbleTopic.GREETING,
      currentReaction = BunnyTransientReaction.NONE
    )
    assertEquals(BunnyAutoEvent.DEADLINE_MISSED_SAD, sadOutcome.triggeredEvent)
    assertEquals(BunnyVoiceMood.SAD_MISSED, sadOutcome.soundCue)
    assertEquals(
      BunnyCompanionExpression.SAD,
      BunnyCompanionStateLogic.resolveVisualExpression(missedSnap, sadOutcome.nextTransientReaction, isTalking = false)
    )

    // User completes the missed task -> bunny becomes HAPPY with happy 8-bit cue!
    val completedSnap = BunnyCompanionStateLogic.analyzeTasks(
      items = listOf(pendingTask.copy(completed = true)),
      steps = emptyList(),
      classSchedules = emptyList(),
      nowMillis = afterDeadlineMs
    )
    assertEquals(0, completedSnap.missedCount)
    val happyOutcome = BunnyCompanionStateLogic.resolveAutomaticTransition(
      previous = missedSnap,
      current = completedSnap,
      currentTopic = sadOutcome.nextTopic,
      currentReaction = sadOutcome.nextTransientReaction
    )
    assertEquals(BunnyAutoEvent.TASK_COMPLETED_HAPPY, happyOutcome.triggeredEvent)
    assertEquals(BunnyVoiceMood.HAPPY_PAT, happyOutcome.soundCue)
    assertEquals(
      BunnyCompanionExpression.HAPPY,
      BunnyCompanionStateLogic.resolveVisualExpression(completedSnap, happyOutcome.nextTransientReaction, isTalking = false)
    )
  }

  @Test
  fun audioMemoFeedback_adaptsToDurationAndTaskCompletion() {
    val shortIncomplete = BunnyCompanionStateLogic.buildAudioMemoFeedback(
      itemTitle = "Pack suitcase",
      durationSeconds = 4,
      isTaskCompleted = false,
      isNewRecording = true
    )
    assertTrue(shortIncomplete.first.contains("4s"))
    assertTrue(shortIncomplete.first.contains("Pack suitcase"))
    assertEquals(BunnyVoiceMood.AUDIO_MEMO_FEEDBACK, shortIncomplete.second)

    val longCompleted = BunnyCompanionStateLogic.buildAudioMemoFeedback(
      itemTitle = "Study notes",
      durationSeconds = 28,
      isTaskCompleted = true,
      isNewRecording = false
    )
    assertTrue(longCompleted.first.contains("28s"))
    assertEquals(BunnyVoiceMood.HAPPY_PAT, longCompleted.second)
  }

  @Test
  fun habitRewards_detectDailyCompletionAllDoneAndStreakMilestones() {
    val start = com.example.data.DailyHabitSnapshot(
      completedTodayCount = 0,
      totalHabitsCount = 2,
      overallStreakDays = 1,
      bunnyStage = com.example.data.BunnyGrowthStage.SPROUT_KIT
    )
    val oneChecked = com.example.data.DailyHabitSnapshot(
      completedTodayCount = 1,
      totalHabitsCount = 2,
      overallStreakDays = 1,
      bunnyStage = com.example.data.BunnyGrowthStage.SPROUT_KIT
    )
    val firstOutcome = BunnyCompanionStateLogic.analyzeHabitsAndRewards(start, oneChecked)
    assertTrue(firstOutcome.rewarded)
    assertFalse(firstOutcome.allDoneToday)
    assertFalse(firstOutcome.isStreakMilestone)
    assertEquals(BunnyVoiceMood.HABIT_REWARD, firstOutcome.soundMood)

    val allDoneMilestone = com.example.data.DailyHabitSnapshot(
      completedTodayCount = 2,
      totalHabitsCount = 2,
      overallStreakDays = 2,
      bunnyStage = com.example.data.BunnyGrowthStage.HOPPING_KIT
    )
    val milestoneOutcome = BunnyCompanionStateLogic.analyzeHabitsAndRewards(oneChecked, allDoneMilestone)
    assertTrue(milestoneOutcome.rewarded)
    assertTrue(milestoneOutcome.allDoneToday)
    assertTrue(milestoneOutcome.isStreakMilestone)
    assertTrue(milestoneOutcome.dialogue.contains("2"))
    assertEquals(BunnyVoiceMood.HABIT_REWARD, milestoneOutcome.soundMood)

    // Happiness percentage reaches 100% when all daily habits are completed with no missed tasks
    val startHappiness = BunnyCompanionStateLogic.computeHabitHappinessPercent(start, missedTasksCount = 0)
    val partialHappiness = BunnyCompanionStateLogic.computeHabitHappinessPercent(oneChecked, missedTasksCount = 0)
    val fullHappiness = BunnyCompanionStateLogic.computeHabitHappinessPercent(allDoneMilestone, missedTasksCount = 0)
    assertTrue(partialHappiness > startHappiness)
    assertEquals(100, fullHappiness)
  }

  @Test
  fun resolveVisualExpression_supportsListeningAndHabitHappiness() {
    val emptyTasks = BunnyCompanionStateLogic.analyzeTasks(
      items = emptyList(),
      steps = emptyList(),
      classSchedules = emptyList(),
      nowMillis = CalendarDate(2026, 11, 25).toLocalEpochMillis(10, 0)
    )
    assertEquals(
      BunnyCompanionExpression.LISTENING,
      BunnyCompanionStateLogic.resolveVisualExpression(
        snapshot = emptyTasks,
        transientReaction = BunnyTransientReaction.NONE,
        isTalking = false,
        isListening = true
      )
    )

    val habitDoneToday = com.example.data.DailyHabitSnapshot(completedTodayCount = 1, totalHabitsCount = 3)
    assertEquals(
      BunnyCompanionExpression.HAPPY,
      BunnyCompanionStateLogic.resolveVisualExpression(
        snapshot = emptyTasks,
        transientReaction = BunnyTransientReaction.NONE,
        isTalking = false,
        isListening = false,
        habitSnapshot = habitDoneToday
      )
    )
  }

  @Test
  fun calendarDateAndCategoryParsing_areSafe() {
    val parsed = CalendarDate.parse("2026-11-25")
    assertEquals(CalendarDate(2026, 11, 25), parsed)
    assertEquals(TaskPriority.MEDIUM, TaskPriority.fromRaw(null))
    assertEquals(TaskCategory.PERSONAL, TaskCategory.fromRaw("unknown"))
  }

  @Test
  fun interactiveBunnyWidget_isRegisteredWithExpectedActions() {
    assertEquals(
      com.example.widgets.InteractiveBunnyAppWidgetProvider::class.java,
      com.example.widgets.PlannerWidgetKind.INTERACTIVE_BUNNY.providerClass
    )
    assertTrue(com.example.widgets.InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PAT.isNotBlank())
    assertTrue(com.example.widgets.InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_LEFT.isNotBlank())
    assertTrue(com.example.widgets.InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_RIGHT.isNotBlank())
    assertTrue(com.example.widgets.InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_TALK.isNotBlank())
  }
}
