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
        return daysInMonth(year, month)
    }

    fun daysUntil(other: CalendarDate): Int {
        val diff = other.toLocalEpochMillis(12, 0) - toLocalEpochMillis(12, 0)
        return Math.round(diff.toDouble() / 86_400_000.0).toInt()
    }

    companion object {
        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }

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
            if (y !in 1900..2199 || m !in 1..12) return null
            val maxDay = daysInMonth(y, m)
            if (d !in 1..maxDay) return null
            return CalendarDate(y, m, d)
        }
    }
}

object PlannerDateUtils {

    private val formatCache = object : ThreadLocal<MutableMap<String, SimpleDateFormat>>() {
        override fun initialValue(): MutableMap<String, SimpleDateFormat> = HashMap()
    }

    private fun cachedFormat(pattern: String, tz: TimeZone = TimeZone.getDefault()): SimpleDateFormat {
        val locale = Locale.getDefault()
        val key = "$pattern|${locale.toLanguageTag()}|${tz.id}"
        val map = formatCache.get()!!
        return map.getOrPut(key) {
            SimpleDateFormat(pattern, locale).apply { timeZone = tz }
        }
    }

    fun formatFullDateHeader(date: CalendarDate): String {
        return cachedFormat("EEEE, MMMM d").format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatMonthYear(date: CalendarDate): String {
        return cachedFormat("MMMM yyyy").format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatShortDate(date: CalendarDate): String {
        return cachedFormat("EEE, MMM d").format(Date(date.toLocalEpochMillis(12, 0)))
    }

    fun formatCompactDate(date: CalendarDate): String {
        return cachedFormat("MMM d").format(Date(date.toLocalEpochMillis(12, 0)))
    }

    /**
     * Formats minutes-since-midnight using a fixed UTC reference date so DST spring-forward
     * skipped hours (e.g. 2:30 AM) never roll forward to 3:30 AM.
     */
    fun formatTimeOfDay(minutesSinceMidnight: Int): String {
        val clamped = minutesSinceMidnight.coerceIn(0, 1439)
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JANUARY, 1, clamped / 60, clamped % 60, 0)
        }
        return cachedFormat("h:mm a", utc).format(cal.time)
    }

    /**
     * Parses a user-typed time string in 12-hour or 24-hour format into minutes since midnight (0..1439).
     * Accepts flexible inputs such as "2:30 PM", "2:30pm", "2pm", "14:30", "9:05", "0905", or "930am".
     * Returns null when blank or invalid.
     */
    fun parseFlexibleTimeMinutes(raw: String): Int? {
        val cleaned = raw
            .trim()
            .uppercase(Locale.US)
            .replace(".", "")
            .replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return null

        val isAm = cleaned.endsWith("AM")
        val isPm = cleaned.endsWith("PM")
        val hasMeridiem = isAm || isPm
        val core = if (hasMeridiem) cleaned.dropLast(2).trim() else cleaned
        if (core.isEmpty()) return null

        val hour: Int
        val minute: Int
        if (core.contains(":")) {
            val parts = core.split(":")
            if (parts.size != 2) return null
            hour = parts[0].trim().toIntOrNull() ?: return null
            val minStr = parts[1].trim()
            if (minStr.length !in 1..2) return null
            minute = minStr.toIntOrNull() ?: return null
        } else if (core.all { it.isDigit() }) {
            when (core.length) {
                1, 2 -> {
                    if (!hasMeridiem) return null
                    hour = core.toIntOrNull() ?: return null
                    minute = 0
                }
                3 -> {
                    hour = core.substring(0, 1).toIntOrNull() ?: return null
                    minute = core.substring(1, 3).toIntOrNull() ?: return null
                }
                4 -> {
                    hour = core.substring(0, 2).toIntOrNull() ?: return null
                    minute = core.substring(2, 4).toIntOrNull() ?: return null
                }
                else -> return null
            }
        } else {
            return null
        }

        if (minute !in 0..59) return null

        val hour24 = if (hasMeridiem) {
            if (hour !in 1..12) return null
            when {
                isAm && hour == 12 -> 0
                isPm && hour == 12 -> 12
                isPm -> hour + 12
                else -> hour
            }
        } else {
            if (hour !in 0..23) return null
            hour
        }
        return hour24 * 60 + minute
    }

    fun formatUtcMillisTime(utcMillis: Long): String {
        return cachedFormat("h:mm a").format(Date(utcMillis))
    }

    fun formatUtcMillisDateTime(utcMillis: Long): String {
        return cachedFormat("EEE, MMM d · h:mm a").format(Date(utcMillis))
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
            in 2..6 -> cachedFormat("EEEE").format(Date(date.toLocalEpochMillis(12, 0)))
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
     * Day-multiple offsets (e.g., 1 day / 1 week before) are computed in local calendar space so
     * DST transitions do not shift local reminder hours by +/- 1 hour.
     */
    fun computeScheduledAtUtcMillis(
        item: PlannerItem,
        offsetMinutes: Int,
        localHour: Int,
        localMinute: Int
    ): Long {
        val safeOffset = offsetMinutes.coerceAtLeast(0)
        val isWholeDays = safeOffset > 0 && safeOffset % 1440 == 0
        val offsetDays = safeOffset / 1440

        if (item.itemType() != ItemType.EVENT || item.startAtUtcMillis == null) {
            val targetDate = CalendarDate.parse(item.dueDate)
                ?: CalendarDate.parse(item.startDate)
                ?: CalendarDate.today()
            val dueMins = if (item.itemType() != ItemType.EVENT) item.dueTimeMinutes else null
            val targetHour = dueMins?.div(60) ?: localHour
            val targetMinute = dueMins?.rem(60) ?: localMinute
            if (isWholeDays) {
                return targetDate.plusDays(-offsetDays)
                    .toLocalEpochMillis(targetHour, targetMinute)
                    .coerceAtLeast(0L)
            }
            val baseMillis = targetDate.toLocalEpochMillis(targetHour, targetMinute)
            return (baseMillis - safeOffset.toLong() * 60_000L).coerceAtLeast(0L)
        }

        val startMillis = item.startAtUtcMillis
        if (isWholeDays) {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = startMillis
                add(Calendar.DAY_OF_MONTH, -offsetDays)
            }
            return cal.timeInMillis.coerceAtLeast(0L)
        }
        return (startMillis - safeOffset.toLong() * 60_000L).coerceAtLeast(0L)
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
