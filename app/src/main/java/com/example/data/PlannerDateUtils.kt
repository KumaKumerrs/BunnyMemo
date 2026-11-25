package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * API-24-compatible date-only calendar value (YYYY-MM-DD) stored without timezone shift.
 * Timed instants (Events, Reminders) are always stored in UTC epoch milliseconds and
 * rendered in the user's local TimeZone.
 */
data class CalendarDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) : Comparable<CalendarDate> {

    override fun compareTo(other: CalendarDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    fun toIsoString(): String = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun plusDays(days: Int): CalendarDate {
        val cal = toLocalCalendar(12, 0)
        cal.add(Calendar.DAY_OF_MONTH, days)
        return CalendarDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun plusMonths(months: Int): CalendarDate {
        val cal = toLocalCalendar(12, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, months)
        return CalendarDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            1
        )
    }

    /** Adds [months] while preserving the day of month, clamped to the target month's length. */
    fun plusMonthsClamped(months: Int): CalendarDate {
        val cal = toLocalCalendar(12, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, months)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val clampedDay = day.coerceIn(1, maxDay)
        return CalendarDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            clampedDay
        )
    }

    fun toLocalCalendar(hour: Int = 0, minute: Int = 0): Calendar {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun toLocalEpochMillis(hour: Int = 0, minute: Int = 0): Long {
        return toLocalCalendar(hour, minute).timeInMillis
    }

    /** 0 = Sunday .. 6 = Saturday */
    fun dayOfWeekIndex(): Int {
        return toLocalCalendar(12, 0).get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    fun daysInMonth(): Int {
        return toLocalCalendar(12, 0).getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun daysUntil(other: CalendarDate): Int {
        val diff = other.toLocalEpochMillis(12, 0) - toLocalEpochMillis(12, 0)
        return Math.round(diff.toDouble() / 86_400_000.0).toInt()
    }

    companion object {
        fun today(nowMillis: Long = System.currentTimeMillis()): CalendarDate {
            return fromUtcMillis(nowMillis)
        }

        fun fromUtcMillis(epochMillis: Long): CalendarDate {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = epochMillis
            }
            return CalendarDate(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun parse(iso: String?): CalendarDate? {
            if (iso.isNullOrBlank()) return null
            val parts = iso.trim().split("-")
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..31) return null
            return CalendarDate(y, m, d)
        }
    }
}

object PlannerDateUtils {

    fun formatFullDateHeader(date: CalendarDate): String {
        val fmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        return fmt.format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatMonthYear(date: CalendarDate): String {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return fmt.format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatShortDate(date: CalendarDate): String {
        val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        return fmt.format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatCompactDate(date: CalendarDate): String {
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        return fmt.format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatTimeOfDay(minutesSinceMidnight: Int): String {
        val clamped = minutesSinceMidnight.coerceIn(0, 1439)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, clamped / 60)
            set(Calendar.MINUTE, clamped % 60)
        }
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun formatUtcMillisTime(utcMillis: Long): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date(utcMillis))
    }

    fun formatUtcMillisDateTime(utcMillis: Long): String {
        val fmt = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date(utcMillis))
    }

    fun formatEventTimeRange(startUtcMillis: Long?, endUtcMillis: Long?): String {
        if (startUtcMillis == null) return "Time not set"
        val startStr = formatUtcMillisTime(startUtcMillis)
        if (endUtcMillis == null || endUtcMillis <= startUtcMillis) return startStr
        val endStr = formatUtcMillisTime(endUtcMillis)
        return "$startStr – $endStr"
    }

    fun formatMinutesRange(startMinutes: Int, endMinutes: Int): String {
        val startStr = formatTimeOfDay(startMinutes)
        if (endMinutes <= startMinutes) return startStr
        val endStr = formatTimeOfDay(endMinutes)
        return "$startStr – $endStr"
    }

    fun formatOrdinalDay(day: Int): String {
        val d = day.coerceIn(1, 31)
        val suffix = when {
            d in 11..13 -> "th"
            d % 10 == 1 -> "st"
            d % 10 == 2 -> "nd"
            d % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$d$suffix"
    }

    fun weekdayShortLabel(dayOfWeekIndex: Int): String = when (dayOfWeekIndex) {
        0 -> "Sun"
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        else -> "Day"
    }

    fun formatRelativeDateLabel(date: CalendarDate, today: CalendarDate): String {
        val diff = today.daysUntil(date)
        return when (diff) {
            0 -> "Today"
            1 -> "Tomorrow"
            -1 -> "Yesterday"
            in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(date.toLocalEpochMillis(12, 0)))
            else -> formatShortDate(date)
        }
    }

    /**
     * Separate countdown for timed events (independent of preparation checklist readiness).
     */
    fun formatEventCountdown(startUtcMillis: Long?, endUtcMillis: Long?, nowMillis: Long): String {
        if (startUtcMillis == null) return "Unscheduled"
        val effectiveEnd = endUtcMillis ?: (startUtcMillis + 3_600_000L)
        if (nowMillis in startUtcMillis..effectiveEnd) return "Happening now"
        if (nowMillis > effectiveEnd) return "Completed"

        val diffMillis = startUtcMillis - nowMillis
        val diffMinutes = (diffMillis / 60_000L).toInt()
        val diffHours = diffMinutes / 60
        val today = CalendarDate.today(nowMillis)
        val eventDay = CalendarDate.fromUtcMillis(startUtcMillis)
        val dayDiff = today.daysUntil(eventDay)

        return when {
            diffMinutes < 60 -> "In ${diffMinutes.coerceAtLeast(1)} min"
            dayDiff == 0 -> "Today in ${diffHours}h ${diffMinutes % 60}m"
            dayDiff == 1 -> "Tomorrow at ${formatUtcMillisTime(startUtcMillis)}"
            dayDiff in 2..14 -> "In $dayDiff days · ${formatUtcMillisTime(startUtcMillis)}"
            else -> "In $dayDiff days"
        }
    }

    /**
     * Computes a trigger timestamp in UTC millis for a reminder rule on an item.
     */
    fun computeScheduledAtUtcMillis(
        item: PlannerItem,
        offsetMinutes: Int,
        localHour: Int,
        localMinute: Int
    ): Long {
        val baseMillis = when (item.itemType()) {
            ItemType.EVENT -> {
                item.startAtUtcMillis
                    ?: CalendarDate.parse(item.dueDate)?.toLocalEpochMillis(localHour, localMinute)
                    ?: (System.currentTimeMillis() + 3_600_000L)
            }
            ItemType.PLAN, ItemType.TASK -> {
                val targetDate = CalendarDate.parse(item.dueDate)
                    ?: CalendarDate.parse(item.startDate)
                    ?: CalendarDate.today()
                val dueMins = item.dueTimeMinutes
                if (dueMins != null) {
                    targetDate.toLocalEpochMillis(dueMins / 60, dueMins % 60)
                } else {
                    targetDate.toLocalEpochMillis(localHour, localMinute)
                }
            }
        }
        return (baseMillis - offsetMinutes.toLong() * 60_000L).coerceAtLeast(0L)
    }

    fun formatOffsetLabel(offsetMinutes: Int): String {
        return when (offsetMinutes) {
            0 -> "At scheduled time"
            15 -> "15 minutes before"
            30 -> "30 minutes before"
            60 -> "1 hour before"
            120 -> "2 hours before"
            1440 -> "1 day before"
            2880 -> "2 days before"
            4320 -> "3 days before"
            10080 -> "1 week before"
            else -> {
                val absMin = abs(offsetMinutes)
                when {
                    absMin % 1440 == 0 -> "${absMin / 1440} days before"
                    absMin % 60 == 0 -> "${absMin / 60} hours before"
                    else -> "$absMin min before"
                }
            }
        }
    }

    fun buildMonthGrid(monthStart: CalendarDate): List<CalendarDate?> {
        val firstDay = CalendarDate(monthStart.year, monthStart.month, 1)
        val leadingEmpty = firstDay.dayOfWeekIndex()
        val totalDays = firstDay.daysInMonth()
        val cells = mutableListOf<CalendarDate?>()
        repeat(leadingEmpty) { cells.add(null) }
        for (d in 1..totalDays) {
            cells.add(CalendarDate(monthStart.year, monthStart.month, d))
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }
        return cells
    }

    fun buildWeekDates(anchor: CalendarDate): List<CalendarDate> {
        val startOfWeek = anchor.plusDays(-anchor.dayOfWeekIndex())
        return (0..6).map { startOfWeek.plusDays(it) }
    }
}
