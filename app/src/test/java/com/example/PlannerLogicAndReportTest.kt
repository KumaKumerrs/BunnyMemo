package com.example

import com.example.data.AccomplishmentReportEngine
import com.example.data.CalendarDate
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerLogic
import com.example.data.PlannerReminder
import com.example.data.PlannerStep
import com.google.firebase.Timestamp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure planner logic: accomplishment report math, strict date parsing, DST-safe time math,
 * and the 24h Inbox horizon. Runs under Robolectric because Firebase [Timestamp] is Parcelable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlannerLogicAndReportTest {

    private lateinit var originalTz: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
        Locale.setDefault(originalLocale)
    }

    private val now = CalendarDate(2026, 6, 15).toLocalEpochMillis(12, 0)

    private fun task(id: String, due: String?, done: Boolean, planId: String? = null, createdAt: Timestamp? = null) =
        PlannerItem(
            id = id,
            type = ItemType.TASK.name,
            title = "Task $id",
            completed = done,
            dueDate = due,
            parentPlanId = planId,
            createdAt = createdAt
        )

    // --- Accomplishment report ---------------------------------------------------------------

    @Test
    fun monthlyAndYearEndCompletion_useExactTrackableUnits() {
        val plan = PlannerItem(id = "p1", type = ItemType.PLAN.name, title = "Plan", dueDate = "2026-01-20")
        val steps = listOf(
            PlannerStep(id = "s1", itemId = "p1", title = "Step 1", completed = true),
            PlannerStep(id = "s2", itemId = "p1", title = "Step 2", completed = false)
        )
        val items = listOf(plan, task("t1", "2026-01-05", true), task("t2", "2026-01-06", false))

        val jan = AccomplishmentReportEngine.buildMonthlyReport(2026, 1, items, steps, now)
        // Plan with child steps is NOT double-counted: 2 steps + 2 tasks = 4 units, 2 bloomed.
        assertEquals(4, jan.totalTrackableUnits)
        assertEquals(2, jan.totalBlooms)
        assertEquals(50, jan.completionPercent)

        val year = AccomplishmentReportEngine.buildYearEndReport(2026, items, steps, now)
        assertEquals(50, year.overallCompletionPercent)
        assertEquals(2, year.totalBlooms)
    }

    @Test
    fun undatedTask_isAttributedToCreatedAtMonth() {
        val createdMillis = CalendarDate(2026, 3, 10).toLocalEpochMillis(12, 0)
        val undated = task("u1", null, true, createdAt = Timestamp(Date(createdMillis)))

        val march = AccomplishmentReportEngine.buildMonthlyReport(2026, 3, listOf(undated), emptyList(), now)
        val april = AccomplishmentReportEngine.buildMonthlyReport(2026, 4, listOf(undated), emptyList(), now)

        assertEquals(1, march.tasksTotal)
        assertEquals(1, march.tasksCompleted)
        assertEquals(0, april.tasksTotal)
    }

    @Test
    fun emptyMonth_reportsZeroPercentWithoutDivisionErrors() {
        val report = AccomplishmentReportEngine.buildMonthlyReport(2026, 2, emptyList(), emptyList(), now)
        assertEquals(0, report.completionPercent)
        assertEquals(0, report.totalTrackableUnits)
        assertEquals(5, report.weeklyCompletedTasks.size)
        assertFalse(report.hasAnyActivity)
    }

    // --- Strict date parsing -----------------------------------------------------------------

    @Test
    fun calendarDateParse_rejectsImpossibleDates() {
        assertNull(CalendarDate.parse("2026-02-31"))
        assertNull(CalendarDate.parse("2026-04-31"))
        assertNull(CalendarDate.parse("2026-02-29"))
        assertNull(CalendarDate.parse("2026-13-01"))
        assertNull(CalendarDate.parse("2026-00-10"))
        assertNull(CalendarDate.parse("not-a-date"))
        assertNull(CalendarDate.parse(""))
        assertNull(CalendarDate.parse(null))
    }

    @Test
    fun calendarDateParse_acceptsValidAndLeapDates() {
        assertEquals(CalendarDate(2028, 2, 29), CalendarDate.parse("2028-02-29"))
        assertEquals(CalendarDate(2026, 4, 30), CalendarDate.parse("2026-04-30"))
        assertEquals(CalendarDate(2026, 12, 31), CalendarDate.parse(" 2026-12-31 "))
    }

    @Test
    fun daysInMonth_handlesFebruaryAnd30DayMonths() {
        assertEquals(28, CalendarDate.daysInMonth(2026, 2))
        assertEquals(29, CalendarDate.daysInMonth(2028, 2))
        assertEquals(30, CalendarDate.daysInMonth(2026, 9))
        assertEquals(31, CalendarDate.daysInMonth(2026, 12))
    }

    // --- DST-safe time math ------------------------------------------------------------------

    @Test
    fun formatTimeOfDay_neverShiftsSpringForwardHour() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val label = PlannerDateUtils.formatTimeOfDay(2 * 60 + 30)
        assertTrue("Expected 2:30 AM but was $label", label.startsWith("2:30"))
        assertEquals("12:00 AM", PlannerDateUtils.formatTimeOfDay(-5))
        assertEquals("11:59 PM", PlannerDateUtils.formatTimeOfDay(5000))
    }

    @Test
    fun computeScheduledAt_oneDayBeforeAcrossDst_keepsLocalHour() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        // DST starts 2026-03-08 in the US; a task due 03-09 09:00 reminded 1 day before -> 03-08 09:00 local.
        val item = task("dst", "2026-03-09", false).copy(dueTimeMinutes = 9 * 60)
        val trigger = PlannerDateUtils.computeScheduledAtUtcMillis(item, offsetMinutes = 1440, localHour = 8, localMinute = 0)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(8, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(CalendarDate(2026, 3, 8).toLocalEpochMillis(9, 0), trigger)
    }

    // --- Inbox horizon -----------------------------------------------------------------------

    @Test
    fun buildInboxEntries_includesOnlyDueAndNext24h() {
        val item = task("i1", "2026-07-01", false)
        val hour = 3_600_000L
        val reminders = listOf(
            PlannerReminder(id = "past", itemId = "i1", scheduledAtUtcMillis = now - hour, occurrenceKey = "a"),
            PlannerReminder(id = "soon", itemId = "i1", scheduledAtUtcMillis = now + 23 * hour, occurrenceKey = "b"),
            PlannerReminder(id = "far", itemId = "i1", scheduledAtUtcMillis = now + 25 * hour, occurrenceKey = "c"),
            PlannerReminder(id = "dismissed", itemId = "i1", scheduledAtUtcMillis = now - hour, occurrenceKey = "d", dismissed = true),
            PlannerReminder(id = "orphan", itemId = "missing", scheduledAtUtcMillis = now - hour, occurrenceKey = "e")
        )

        val entries = PlannerLogic.buildInboxEntries(reminders, listOf(item), emptyList(), now)

        assertEquals(listOf("past", "soon"), entries.map { it.reminder.id })
        assertTrue(entries.zipWithNext().all { (a, b) -> a.triggerUtcMillis <= b.triggerUtcMillis })
    }

    @Test
    fun buildInboxEntries_keepsSnoozedOccurrenceEvenBeyondHorizon() {
        val item = task("i2", "2026-07-01", false)
        val snoozedUntil = now + 30 * 3_600_000L
        val reminder = PlannerReminder(
            id = "snoozed",
            itemId = "i2",
            scheduledAtUtcMillis = now - 1_000L,
            snoozedUntilUtcMillis = snoozedUntil,
            occurrenceKey = "s"
        )
        val entry = PlannerLogic.buildInboxEntries(listOf(reminder), listOf(item), emptyList(), now).singleOrNull()
        assertNotNull(entry)
        assertTrue(entry!!.isSnoozed)
        assertEquals(snoozedUntil, entry.triggerUtcMillis)
    }

    @Test
    fun parseFlexibleTimeMinutes_accepts12h24hCompactAndRejectsInvalid() {
        assertEquals(14 * 60 + 30, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("2:30 PM"))
        assertEquals(14 * 60 + 30, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("2:30pm"))
        assertEquals(14 * 60 + 30, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("14:30"))
        assertEquals(14 * 60, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("2pm"))
        assertEquals(0, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("12:00 AM"))
        assertEquals(12 * 60, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("12:00 PM"))
        assertEquals(9 * 60 + 5, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("09:05"))
        assertEquals(9 * 60 + 30, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("930am"))
        assertEquals(23 * 60 + 59, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("2359"))

        assertEquals(null, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes(""))
        assertEquals(null, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("   "))
        assertEquals(null, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("25:00"))
        assertEquals(null, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("13:00 PM"))
        assertEquals(null, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("9:65 AM"))
        assertEquals(null, com.example.data.PlannerDateUtils.parseFlexibleTimeMinutes("hello"))
    }
}
