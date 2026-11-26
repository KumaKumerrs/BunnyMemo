package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CalendarDate
import com.example.data.DailyHabitRepository
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerStep
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

data class DailyHabitStatus(
    val date: CalendarDate,
    val completedTasks: Int,
    val totalTasks: Int
) {
    val hasCompletedDailyTask: Boolean get() = completedTasks > 0
    val hasPendingTasks: Boolean get() = totalTasks > completedTasks
}

data class HabitGardenSummary(
    val statusByDate: Map<CalendarDate, DailyHabitStatus>,
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val totalBloomedDays: Int
)

/**
 * Computes per-day task completion map & streak statistics from real PlannerItems and dated PlannerSteps.
 * Any day with at least one completed daily task (or completed dated step) blooms a forget-me-not icon.
 */
fun computeHabitGardenSummary(
    items: List<PlannerItem>,
    steps: List<PlannerStep>,
    today: CalendarDate,
    habitCheckInCountByDate: Map<CalendarDate, Int> = emptyMap()
): HabitGardenSummary {
    val completedByDate = HashMap<CalendarDate, Int>()
    val totalByDate = HashMap<CalendarDate, Int>()

    for (item in items) {
        if (item.itemType() != ItemType.TASK) continue
        val date = CalendarDate.parse(item.dueDate)
            ?: item.updatedAt?.toDate()?.time?.let { CalendarDate.fromUtcMillis(it) }
            ?: item.createdAt?.toDate()?.time?.let { CalendarDate.fromUtcMillis(it) }
            ?: continue
        totalByDate[date] = (totalByDate[date] ?: 0) + 1
        if (item.completed) {
            completedByDate[date] = (completedByDate[date] ?: 0) + 1
        }
    }

    for (step in steps) {
        val date = CalendarDate.parse(step.dueDate) ?: continue
        totalByDate[date] = (totalByDate[date] ?: 0) + 1
        if (step.completed) {
            completedByDate[date] = (completedByDate[date] ?: 0) + 1
        }
    }

    for ((date, count) in habitCheckInCountByDate) {
        if (count > 0) {
            totalByDate[date] = (totalByDate[date] ?: 0) + count
            completedByDate[date] = (completedByDate[date] ?: 0) + count
        }
    }

    val allDates = totalByDate.keys
    val statusByDate = HashMap<CalendarDate, DailyHabitStatus>(allDates.size)
    for (d in allDates) {
        statusByDate[d] = DailyHabitStatus(
            date = d,
            completedTasks = completedByDate[d] ?: 0,
            totalTasks = totalByDate[d] ?: 0
        )
    }

    val bloomedDates = statusByDate.values
        .filter { it.hasCompletedDailyTask }
        .map { it.date }
        .sorted()

    // Current streak: consecutive bloomed days ending today (or yesterday if today hasn't bloomed yet)
    var currentStreak = 0
    val bloomSet = bloomedDates.toHashSet()
    var cursor = if (today in bloomSet) today else today.plusDays(-1)
    while (cursor in bloomSet) {
        currentStreak++
        cursor = cursor.plusDays(-1)
    }

    // Best streak across all recorded dates
    var bestStreak = 0
    var running = 0
    var prev: CalendarDate? = null
    for (d in bloomedDates) {
        if (prev != null && prev.daysUntil(d) == 1) {
            running++
        } else {
            running = 1
        }
        if (running > bestStreak) bestStreak = running
        prev = d
    }

    return HabitGardenSummary(
        statusByDate = statusByDate,
        currentStreakDays = currentStreak,
        bestStreakDays = bestStreak,
        totalBloomedDays = bloomedDates.size
    )
}

/**
 * Visual habit-tracking calendar card that marks off every day a daily task is completed
 * with a small bloomed forget-me-not flower icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForgetMeNotHabitCalendarCard(
    items: List<PlannerItem>,
    steps: List<PlannerStep>,
    today: CalendarDate,
    onAddTaskForDate: ((CalendarDate) -> Unit)? = null,
    initialMonth: CalendarDate = CalendarDate(today.year, today.month, 1),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val habitRepo = remember(context) { DailyHabitRepository.getInstance(context) }
    val habitSnapshot by habitRepo.snapshot.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotion()
    var visibleMonth by remember(initialMonth.year, initialMonth.month) {
        mutableStateOf(CalendarDate(initialMonth.year, initialMonth.month, 1))
    }
    var inspectedDate by remember(today) { mutableStateOf(today) }

    val summary = remember(items, steps, today, habitSnapshot.checkInCountByDate) {
        computeHabitGardenSummary(items, steps, today, habitSnapshot.checkInCountByDate)
    }

    val gridCells = remember(visibleMonth) {
        PlannerDateUtils.buildMonthGrid(visibleMonth)
    }
    val monthBloomedCount = remember(gridCells, summary) {
        gridCells.filterNotNull().count { summary.statusByDate[it]?.hasCompletedDailyTask == true }
    }
    val inspectedStatus = summary.statusByDate[inspectedDate]

    ModernPanel(
        shape = RoundedCornerShape(24.dp),
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_tracker_calendar_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with flower badge & month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = SkyBlue.copy(alpha = 0.55f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ForgetMeNotFlower(bloomed = true, size = 22.dp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.habit_tracker_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy
                        )
                        Text(
                            text = PlannerDateUtils.formatMonthYear(visibleMonth),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { visibleMonth = visibleMonth.plusMonths(-1) },
                        modifier = Modifier.testTag("habit_calendar_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous habit month",
                            tint = MidnightNavy
                        )
                    }
                    IconButton(
                        onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                        modifier = Modifier.testTag("habit_calendar_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next habit month",
                            tint = MidnightNavy
                        )
                    }
                }
            }

            // Streak & monthly bloom badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModernPillBadge(
                    text = "${summary.currentStreakDays}-day streak",
                    containerColor = if (summary.currentStreakDays > 0) SkyBlue.copy(alpha = 0.65f) else SoftIceSurface,
                    contentColor = if (summary.currentStreakDays > 0) DeepBlue else MutedNavy
                )
                ModernPillBadge(
                    text = "$monthBloomedCount ${if (monthBloomedCount == 1) "day" else "days"} bloomed",
                    containerColor = CalmSageContainer,
                    contentColor = CalmSage
                )
                if (summary.bestStreakDays > 0) {
                    ModernPillBadge(
                        text = "Best: ${summary.bestStreakDays}d",
                        containerColor = PollenSoft,
                        contentColor = MidnightNavy
                    )
                }
            }

            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayName ->
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MutedNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Animated calendar grid where completed daily tasks bloom a forget-me-not
            AnimatedContent(
                targetState = visibleMonth,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(100))
                    } else {
                        val dir = if (targetState >= initialState) 1 else -1
                        (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { w -> dir * w / 10 } +
                            fadeIn(tween(170))) togetherWith
                            (slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { w -> -dir * w / 12 } +
                                fadeOut(tween(130)))
                    }
                },
                label = "habit_calendar_month_transition"
            ) { targetMonth ->
                val monthCells = remember(targetMonth) { PlannerDateUtils.buildMonthGrid(targetMonth) }
                val rows = remember(monthCells) { monthCells.chunked(7) }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    rows.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    )
                                } else {
                                    val status = summary.statusByDate[date]
                                    HabitCalendarDayCell(
                                        date = date,
                                        status = status,
                                        isToday = date == today,
                                        isSelected = date == inspectedDate,
                                        onClick = { inspectedDate = date },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Inspected day status footer & legend
            TonalInsetPanel(
                containerColor = SoftIceSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ForgetMeNotFlower(
                            bloomed = inspectedStatus?.hasCompletedDailyTask == true,
                            size = 20.dp,
                            animatePop = true
                        )
                        Column {
                            Text(
                                text = PlannerDateUtils.formatShortDate(inspectedDate),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy
                            )
                            Text(
                                text = when {
                                    inspectedStatus == null -> "No daily tasks yet"
                                    inspectedStatus.hasCompletedDailyTask -> {
                                        val done = inspectedStatus.completedTasks
                                        val total = inspectedStatus.totalTasks
                                        "$done of $total daily ${if (total == 1) "task" else "tasks"} bloomed"
                                    }
                                    else -> {
                                        val total = inspectedStatus.totalTasks
                                        "$total daily ${if (total == 1) "task" else "tasks"} waiting to bloom"
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedNavy
                            )
                        }
                    }
                    if (onAddTaskForDate != null) {
                        TextButton(
                            onClick = { onAddTaskForDate(inspectedDate) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("habit_calendar_add_task_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Task", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Compact flower legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ForgetMeNotFlower(bloomed = true, size = 14.dp)
                    Text(
                        text = "Task completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ForgetMeNotFlower(bloomed = false, size = 14.dp)
                    Text(
                        text = "Task scheduled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitCalendarDayCell(
    date: CalendarDate,
    status: DailyHabitStatus?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bloomed = status?.hasCompletedDailyTask == true
    val pendingOnly = status != null && !bloomed && status.hasPendingTasks
    val cellShape = RoundedCornerShape(12.dp)

    val bgColor = when {
        bloomed -> SkyBlue.copy(alpha = 0.52f)
        pendingOnly -> SoftIceSurface
        isSelected -> SoftIceSurface
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> DeepBlue
        isToday -> DeepBlue.copy(alpha = 0.72f)
        bloomed -> ForgetMeNotBlue.copy(alpha = 0.65f)
        pendingOnly -> SubtleBorder
        else -> Color.Transparent
    }

    val cellDescription = buildString {
        append(PlannerDateUtils.formatShortDate(date))
        when {
            bloomed -> append(", ${status?.completedTasks ?: 1} daily task completed, marked with forget-me-not")
            pendingOnly -> append(", ${status?.totalTasks ?: 1} daily task scheduled")
            else -> append(", no daily tasks")
        }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(cellShape)
            .background(bgColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = if (isSelected || isToday) 1.5.dp else 0.9.dp,
                        color = borderColor,
                        shape = cellShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = cellDescription }
            .testTag("habit_day_cell_${date.toIsoString()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isToday || bloomed || isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isToday) DeepBlue else MidnightNavy
            )
            Spacer(modifier = Modifier.height(2.dp))
            when {
                bloomed -> {
                    ForgetMeNotFlower(
                        bloomed = true,
                        size = 18.dp,
                        animatePop = true
                    )
                }
                pendingOnly -> {
                    ForgetMeNotFlower(
                        bloomed = false,
                        size = 14.dp
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}
