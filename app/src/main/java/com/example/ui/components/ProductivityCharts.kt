package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CalendarDate
import com.example.data.MonthlyAccomplishmentReport
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.SharedInviteAccent
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

/**
 * Native Jetpack Compose Canvas chart visualizing completed tasks across weeks of the month
 * plus a color-coded category breakdown bar card.
 */
@Composable
fun MonthlyCompletedTasksChartCard(
    report: MonthlyAccomplishmentReport,
    modifier: Modifier = Modifier
) {
    var selectedWeekIndex by remember(report.year, report.month) { mutableIntStateOf(-1) }
    val weeklyData = remember(report.weeklyCompletedTasks) {
        if (report.weeklyCompletedTasks.size == 5) report.weeklyCompletedTasks else List(5) { 0 }
    }
    val maxCount = (weeklyData.maxOrNull() ?: 0).coerceAtLeast(1)
    val weekLabels = remember { listOf("W1", "W2", "W3", "W4", "W5") }

    ModernPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_completed_tasks_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(CalmSageContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = null,
                            tint = CalmSage,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.report_completed_tasks_chart_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MidnightNavy
                        )
                        Text(
                            text = "${report.tasksCompleted} of ${report.tasksTotal} tasks done · ${report.totalBlooms} total blooms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }
                ModernPillBadge(
                    text = "${report.completionPercent}%",
                    containerColor = CalmSageContainer,
                    contentColor = CalmSage
                )
            }

            // Native Compose Canvas grouped bar + trend curve chart
            val gridColor = SubtleBorder.copy(alpha = 0.7f)
            val barFillTop = DeepBlue
            val barFillBottom = ForgetMeNotBlue.copy(alpha = 0.75f)
            val selectedBarColor = CalmSage
            val emptyBarColor = SoftIceSurface

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(SoftIceSurface.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    val tasksChartDescription = remember(weeklyData) {
                        "Completed tasks per week: " + weeklyData.mapIndexed { idx, value ->
                            "${weekLabels[idx]} $value"
                        }.joinToString(", ")
                    }
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .semantics { contentDescription = tasksChartDescription }
                    ) {
                        val chartW = size.width
                        val chartH = size.height
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        // 3 horizontal reference grid lines
                        listOf(0.2f, 0.55f, 0.9f).forEach { frac ->
                            val y = chartH * frac
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(chartW, y),
                                strokeWidth = 1.5f,
                                pathEffect = dashEffect
                            )
                        }

                        val slotW = chartW / 5f
                        val barW = (slotW * 0.44f).coerceIn(18.dp.toPx(), 40.dp.toPx())
                        val maxBarH = chartH * 0.84f
                        val baselineY = chartH * 0.92f
                        val centers = mutableListOf<Offset>()

                        weeklyData.forEachIndexed { idx, value ->
                            val centerX = slotW * idx + slotW / 2f
                            val barLeft = centerX - barW / 2f
                            val barH = if (value == 0) {
                                6.dp.toPx()
                            } else {
                                ((value.toFloat() / maxCount.toFloat()) * maxBarH).coerceAtLeast(14.dp.toPx())
                            }
                            val topY = baselineY - barH
                            centers.add(Offset(centerX, topY))

                            val isSelected = selectedWeekIndex == idx
                            if (value == 0) {
                                drawRoundRect(
                                    color = emptyBarColor,
                                    topLeft = Offset(barLeft, topY),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                            } else {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = if (isSelected) {
                                            listOf(selectedBarColor, selectedBarColor.copy(alpha = 0.72f))
                                        } else {
                                            listOf(barFillTop, barFillBottom)
                                        },
                                        startY = topY,
                                        endY = baselineY
                                    ),
                                    topLeft = Offset(barLeft, topY),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                )
                            }
                        }

                        // Subtle connecting line across bar peaks when there is data
                        if (weeklyData.any { it > 0 } && centers.size == 5) {
                            val linePath = Path().apply {
                                moveTo(centers[0].x, centers[0].y)
                                for (i in 1 until centers.size) {
                                    val prev = centers[i - 1]
                                    val cur = centers[i]
                                    val midX = (prev.x + cur.x) / 2f
                                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                                }
                            }
                            drawPath(
                                path = linePath,
                                color = PollenYellow.copy(alpha = 0.85f),
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            centers.forEachIndexed { idx, pt ->
                                drawCircle(
                                    color = if (weeklyData[idx] > 0) PollenYellow else SubtleBorder,
                                    radius = if (selectedWeekIndex == idx) 5.5.dp.toPx() else 3.5.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // Tappable week labels + numeric counts aligned to the 5 bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weeklyData.forEachIndexed { idx, count ->
                        val isSelected = selectedWeekIndex == idx
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    selectedWeekIndex = if (selectedWeekIndex == idx) -1 else idx
                                }
                                .padding(vertical = 4.dp)
                                .testTag("tasks_chart_week_${idx + 1}")
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected || count > 0) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) CalmSage else if (count > 0) DeepBlue else MutedNavy
                            )
                            Text(
                                text = weekLabels[idx],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MidnightNavy else MutedNavy,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (selectedWeekIndex in 0..4) {
                    val daysInMonth = CalendarDate.daysInMonth(report.year, report.month)
                    val dayRange = when (selectedWeekIndex) {
                        0 -> "Days 1–7"
                        1 -> "Days 8–14"
                        2 -> "Days 15–21"
                        3 -> "Days 22–28"
                        else -> when {
                            daysInMonth <= 28 -> "No Week 5"
                            daysInMonth == 29 -> "Day 29"
                            else -> "Days 29–$daysInMonth"
                        }
                    }
                    Text(
                        text = "${weekLabels[selectedWeekIndex]} ($dayRange): ${weeklyData[selectedWeekIndex]} tasks completed",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepBlue,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Color-coded Category breakdown
            if (report.categoryBreakdown.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.report_category_breakdown_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                    report.categoryBreakdown.forEach { catSummary ->
                        val palette = categoryPalette(catSummary.category)
                        val ratio = if (catSummary.totalCount == 0) 0f
                        else (catSummary.completedCount.toFloat() / catSummary.totalCount.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentColor)
                                    )
                                    Text(
                                        text = catSummary.category.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MidnightNavy
                                    )
                                }
                                Text(
                                    text = "${catSummary.completedCount}/${catSummary.totalCount} done",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedNavy
                                )
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                color = palette.accentColor,
                                trackColor = palette.containerColor,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Native Jetpack Compose Canvas chart visualizing time spent on milestones & prep steps
 * across the 5 weeks of the selected month, plus top timed milestones.
 */
@Composable
fun MonthlyMilestoneTimeChartCard(
    report: MonthlyAccomplishmentReport,
    modifier: Modifier = Modifier
) {
    var selectedWeekIndex by remember(report.year, report.month) { mutableIntStateOf(-1) }
    val weeklyMinutes = remember(report.weeklyMilestoneMinutes) {
        if (report.weeklyMilestoneMinutes.size == 5) report.weeklyMilestoneMinutes else List(5) { 0 }
    }
    val maxMins = (weeklyMinutes.maxOrNull() ?: 0).coerceAtLeast(15)
    val weekLabels = remember { listOf("W1", "W2", "W3", "W4", "W5") }

    ModernPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_milestone_time_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(PollenSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.report_milestone_time_chart_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MidnightNavy
                        )
                        Text(
                            text = "${report.milestonesCompleted}/${report.milestonesTotal} milestones completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }
                ModernPillBadge(
                    text = formatCompactDuration(report.totalMilestoneMinutes),
                    containerColor = SkyBlue.copy(alpha = 0.65f),
                    contentColor = DeepBlue
                )
            }

            // Native Compose Canvas Bar + Smooth Area visualization
            val gridColor = SubtleBorder.copy(alpha = 0.7f)
            val areaTop = ForgetMeNotBlue.copy(alpha = 0.34f)
            val barPrimary = SharedInviteAccent
            val barSecondary = ForgetMeNotBlue

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(138.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(SoftIceSurface.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    val milestoneChartDescription = remember(weeklyMinutes) {
                        "Milestone time per week: " + weeklyMinutes.mapIndexed { idx, mins ->
                            "${weekLabels[idx]} ${formatCompactDuration(mins)}"
                        }.joinToString(", ")
                    }
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .semantics { contentDescription = milestoneChartDescription }
                    ) {
                        val chartW = size.width
                        val chartH = size.height
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        listOf(0.2f, 0.55f, 0.9f).forEach { frac ->
                            val y = chartH * frac
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(chartW, y),
                                strokeWidth = 1.5f,
                                pathEffect = dash
                            )
                        }

                        val slotW = chartW / 5f
                        val barW = (slotW * 0.42f).coerceIn(18.dp.toPx(), 38.dp.toPx())
                        val maxBarH = chartH * 0.84f
                        val baselineY = chartH * 0.92f
                        val topPoints = mutableListOf<Offset>()

                        weeklyMinutes.forEachIndexed { idx, mins ->
                            val cx = slotW * idx + slotW / 2f
                            val h = if (mins == 0) 6.dp.toPx()
                            else ((mins.toFloat() / maxMins.toFloat()) * maxBarH).coerceAtLeast(14.dp.toPx())
                            topPoints.add(Offset(cx, baselineY - h))
                        }

                        // Soft gradient area beneath curve
                        if (weeklyMinutes.any { it > 0 } && topPoints.size == 5) {
                            val areaPath = Path().apply {
                                moveTo(topPoints.first().x, baselineY)
                                lineTo(topPoints.first().x, topPoints.first().y)
                                for (i in 1 until topPoints.size) {
                                    val prev = topPoints[i - 1]
                                    val cur = topPoints[i]
                                    val midX = (prev.x + cur.x) / 2f
                                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                                }
                                lineTo(topPoints.last().x, baselineY)
                                close()
                            }
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(areaTop, Color.Transparent),
                                    startY = 0f,
                                    endY = baselineY
                                )
                            )
                        }

                        weeklyMinutes.forEachIndexed { idx, mins ->
                            val cx = slotW * idx + slotW / 2f
                            val barLeft = cx - barW / 2f
                            val h = if (mins == 0) 6.dp.toPx()
                            else ((mins.toFloat() / maxMins.toFloat()) * maxBarH).coerceAtLeast(14.dp.toPx())
                            val topY = baselineY - h
                            val isSelected = selectedWeekIndex == idx

                            drawRoundRect(
                                brush = if (mins == 0) {
                                    Brush.verticalGradient(listOf(SubtleBorder.copy(alpha = 0.6f), SubtleBorder.copy(alpha = 0.6f)))
                                } else if (isSelected) {
                                    Brush.verticalGradient(listOf(CalmSage, CalmSage.copy(alpha = 0.72f)))
                                } else {
                                    Brush.verticalGradient(listOf(barPrimary, barSecondary))
                                },
                                topLeft = Offset(barLeft, topY),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weeklyMinutes.forEachIndexed { idx, mins ->
                        val isSelected = selectedWeekIndex == idx
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    selectedWeekIndex = if (selectedWeekIndex == idx) -1 else idx
                                }
                                .padding(vertical = 4.dp)
                                .testTag("milestone_time_week_${idx + 1}")
                        ) {
                            Text(
                                text = if (mins > 0) formatCompactDuration(mins) else "0m",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected || mins > 0) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) CalmSage else if (mins > 0) DeepBlue else MutedNavy
                            )
                            Text(
                                text = weekLabels[idx],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MidnightNavy else MutedNavy
                            )
                        }
                    }
                }

                if (selectedWeekIndex in 0..4) {
                    Text(
                        text = "${weekLabels[selectedWeekIndex]}: ${formatCompactDuration(weeklyMinutes[selectedWeekIndex])} invested in milestones",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepBlue
                    )
                }
            }

            if (report.topTimedMilestones.isEmpty()) {
                Text(
                    text = stringResource(R.string.report_milestone_time_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            } else {
                val maxItemMins = report.topTimedMilestones.maxOf { it.minutesSpent }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.report_top_timed_milestones_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                    report.topTimedMilestones.forEach { entry ->
                        val frac = (entry.minutesSpent.toFloat() / maxItemMins.toFloat()).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (entry.completed) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = CalmSage,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = "${entry.stepTitle} · ${entry.parentTitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MidnightNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatCompactDuration(entry.minutesSpent),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = DeepBlue
                                )
                            }
                            LinearProgressIndicator(
                                progress = { frac },
                                color = DeepBlue,
                                trackColor = SkyBlue.copy(alpha = 0.45f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatCompactDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hrs = safe / 60
    val mins = safe % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        else -> "${mins}m"
    }
}
