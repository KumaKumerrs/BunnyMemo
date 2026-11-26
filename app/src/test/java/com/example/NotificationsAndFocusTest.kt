package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.ItemType
import com.example.data.PlannerItem
import com.example.data.PlannerReminder
import com.example.focus.FocusPhase
import com.example.focus.FocusSessionManager
import com.example.notifications.ReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reminder alarm persistence/cancellation across restarts, disjoint PendingIntent request-code
 * ranges, and Pomodoro focus-state persistence + midnight rollover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationsAndFocusTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FocusSessionManager.resetInMemoryForReload()
        context.getSharedPreferences("bunny_memo_focus_session_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        FocusSessionManager.resetInMemoryForReload()
    }

    private val hour = 3_600_000L

    @Test
    fun syncReminders_persistsScheduledKeys_survivesRestart_andCancelAllClears() {
        val now = System.currentTimeMillis()
        val item = PlannerItem(id = "i1", type = ItemType.TASK.name, title = "Essay", dueDate = "2099-01-01")
        val reminders = listOf(
            PlannerReminder(id = "r1", itemId = "i1", scheduledAtUtcMillis = now + hour, occurrenceKey = "o1"),
            PlannerReminder(id = "r2", itemId = "i1", scheduledAtUtcMillis = now + 2 * hour, occurrenceKey = "o2"),
            PlannerReminder(id = "r3", itemId = "i1", scheduledAtUtcMillis = now - hour, occurrenceKey = "o3"),
            PlannerReminder(id = "r4", itemId = "i1", scheduledAtUtcMillis = now + hour, occurrenceKey = "o4", dismissed = true)
        )

        ReminderScheduler(context).syncReminders(reminders, listOf(item), emptyList(), now)

        // A fresh instance (process restart) still knows what is scheduled so it can cancel it.
        val afterRestart = ReminderScheduler(context)
        assertEquals(2, afterRestart.getPersistedScheduledItemCount())

        afterRestart.syncReminders(reminders.take(1), listOf(item), emptyList(), now)
        assertEquals(1, afterRestart.getPersistedScheduledItemCount())

        afterRestart.cancelAll()
        assertEquals(0, ReminderScheduler(context).getPersistedScheduledItemCount())
        assertEquals(0, ReminderScheduler(context).getPersistedScheduledClassCount())
    }

    @Test
    fun syncClassReminders_persistsEnabledClassesOnly() {
        val today = CalendarDate.today()
        val classes = listOf(
            ClassScheduleItem(
                id = "c1", subject = "Biology", daysOfWeek = (0..6).toList(),
                startTimeMinutes = 23 * 60 + 50, endTimeMinutes = 23 * 60 + 59,
                semesterStartDate = today.toIsoString(), semesterMonths = 4,
                notifyEnabled = true, notifyOffsetMinutes = 0
            ),
            ClassScheduleItem(
                id = "c2", subject = "Art", daysOfWeek = (0..6).toList(),
                semesterStartDate = today.toIsoString(), notifyEnabled = false
            )
        )
        val scheduler = ReminderScheduler(context)
        scheduler.syncClassReminders(classes)
        assertEquals(1, scheduler.getPersistedScheduledClassCount())

        scheduler.cancelAll()
        assertEquals(0, scheduler.getPersistedScheduledClassCount())
    }

    @Test
    fun requestCodes_forItemsAndClasses_areDisjoint() {
        val itemCodes = (0 until 2_000).map { ReminderScheduler.stableRequestCode(100_000, 400_000, "item_$it") }
        val classCodes = (0 until 2_000).map { ReminderScheduler.stableRequestCode(500_000, 400_000, "class_$it") }
        assertTrue(itemCodes.all { it in 100_000 until 500_000 })
        assertTrue(classCodes.all { it in 500_000 until 900_000 })
        assertTrue(itemCodes.toSet().intersect(classCodes.toSet()).isEmpty())
        // Must never collide with the fixed daily digest / focus IDs.
        val reserved = setOf(70241, 70242, 70243)
        assertTrue((itemCodes + classCodes).none { it in reserved })
    }

    @Test
    fun focusPreferences_persistAcrossProcessRestart() {
        FocusSessionManager.initialize(context)
        FocusSessionManager.setFocusDuration(context, 30)
        FocusSessionManager.setBreakDuration(context, 10)
        FocusSessionManager.setLinkedTaskTitle(context, "Write report")

        FocusSessionManager.resetInMemoryForReload()
        FocusSessionManager.initialize(context)

        val st = FocusSessionManager.state.value
        assertEquals(30, st.focusDurationMinutes)
        assertEquals(10, st.breakDurationMinutes)
        assertEquals(30 * 60, st.totalSeconds)
        assertEquals("Write report", st.linkedTaskTitle)
        assertEquals(FocusPhase.IDLE, st.phase)
    }

    @Test
    fun pausedFocusSession_restoresPhaseAndRemainingTimeAfterRestart() {
        FocusSessionManager.initialize(context)
        FocusSessionManager.setFocusDuration(context, 25)
        FocusSessionManager.startOrResume(context)
        FocusSessionManager.pause(context)
        val pausedRemaining = FocusSessionManager.state.value.remainingSeconds

        FocusSessionManager.resetInMemoryForReload()
        FocusSessionManager.initialize(context)

        val st = FocusSessionManager.state.value
        assertEquals(FocusPhase.FOCUS, st.phase)
        assertFalse(st.isRunning)
        assertEquals(pausedRemaining, st.remainingSeconds)
        assertTrue(st.remainingSeconds in 1..(25 * 60))
    }

    @Test
    fun dailyCounters_rollOverAtMidnight_keepingAllTimeTotals() {
        context.getSharedPreferences("bunny_memo_focus_session_v1", Context.MODE_PRIVATE).edit()
            .putString("stats_today_iso", "2000-01-01")
            .putInt("sessions_today", 3)
            .putInt("mins_today", 75)
            .putInt("sessions_all", 10)
            .putInt("mins_all", 250)
            .commit()

        FocusSessionManager.initialize(context)

        val st = FocusSessionManager.state.value
        assertEquals(0, st.completedSessionsToday)
        assertEquals(0, st.totalFocusMinutesToday)
        assertEquals(10, st.totalSessionsAllTime)
        assertEquals(250, st.totalFocusMinutesAllTime)
    }
}
