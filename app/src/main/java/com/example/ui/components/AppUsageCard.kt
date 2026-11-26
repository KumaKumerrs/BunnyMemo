package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppUsageSummary
import com.example.data.AppUsageTracker
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface

/**
 * Isolated leaf wrapper for [CompactAppUsageStrip] so periodic timer updates only
 * recompose this strip and never trigger root or list recompositions.
 */
@Composable
fun LiveCompactAppUsageStrip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracker = remember(context) { AppUsageTracker.getInstance(context) }
    val usage by tracker.usageSummary.collectAsStateWithLifecycle()
    CompactAppUsageStrip(
        usage = usage,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Isolated leaf wrapper for [AppUsageSummaryCard] so periodic timer updates only
 * recompose the usage analytics card.
 */
@Composable
fun LiveAppUsageSummaryCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracker = remember(context) { AppUsageTracker.getInstance(context) }
    val usage by tracker.usageSummary.collectAsStateWithLifecycle()
    AppUsageSummaryCard(
        usage = usage,
        modifier = modifier
    )
}

/**
 * Compact live usage strip shown on TodayScreen so the user can always see
 * how long they've spent in Bunny Memo today and tap to open the full 7-day chart.
 */
@Composable
fun CompactAppUsageStrip(
    usage: AppUsageSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModernPanel(
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_app_usage_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SkyBlue.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = "App usage timer",
                    tint = DeepBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_usage_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )
                Text(
                    text = "Today ${AppUsageTracker.formatDuration(usage.todaySeconds)} · 7-day ${AppUsageTracker.formatDuration(usage.last7DaysTotalSeconds, includeSeconds = false)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            }

            ModernPillBadge(
                text = "Live ${AppUsageTracker.formatDuration(usage.currentSessionSeconds)}",
                containerColor = CalmSageContainer,
                contentColor = CalmSage
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View full app usage breakdown",
                tint = MutedNavy,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Detailed app usage analytics card with live today timer, 7-day total, daily average,
 * all-time duration, and a 7-day bar chart built in Compose Canvas.
 */
@Composable
fun AppUsageSummaryCard(
    usage: AppUsageSummary,
    modifier: Modifier = Modifier
) {
    val history = usage.dailyHistory
    val maxSec = (history.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(60L)

    ModernPanel(
        shape = RoundedCornerShape(24.dp),
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_usage_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.app_usage_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy
                        )
                        Text(
                            text = stringResource(R.string.app_usage_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                ModernPillBadge(
                    text = "Session ${AppUsageTracker.formatDuration(usage.currentSessionSeconds)}",
                    containerColor = CalmSageContainer,
                    contentColor = CalmSage
                )
            }

            // Key duration metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageMetricPill(
                    label = "Today",
                    value = AppUsageTracker.formatDuration(usage.todaySeconds),
                    highlighted = true,
                    modifier = Modifier.weight(1f)
                )
                UsageMetricPill(
                    label = "Last 7 days",
                    value = AppUsageTracker.formatDuration(usage.last7DaysTotalSeconds, includeSeconds = false),
                    highlighted = false,
                    modifier = Modifier.weight(1f)
                )
                UsageMetricPill(
                    label = "All time",
                    value = AppUsageTracker.formatDuration(usage.allTimeSeconds, includeSeconds = false),
                    highlighted = false,
                    modifier = Modifier.weight(1f)
                )
            }

            // 7-day usage bar chart
            if (history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last 7 days activity",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MidnightNavy
                        )
                        Text(
                            text = "Avg ${AppUsageTracker.formatDuration(usage.dailyAverageSeconds, includeSeconds = false)}/day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    ) {
                        val count = history.size
                        val slotW = size.width / count
                        val barW = (slotW * 0.48f).coerceAtMost(32.dp.toPx())
                        val chartH = size.height - 12.dp.toPx()

                        history.forEachIndexed { idx, entry ->
                            val ratio = (entry.seconds.toFloat() / maxSec.toFloat()).coerceIn(0f, 1f)
                            val barH = if (entry.seconds <= 0L) {
                                5.dp.toPx()
                            } else {
                                (chartH * ratio).coerceAtLeast(12.dp.toPx())
                            }
                            val left = idx * slotW + (slotW - barW) / 2f
                            val top = size.height - barH

                            drawRoundRect(
                                color = when {
                                    entry.seconds <= 0L -> SkyBlue.copy(alpha = 0.40f)
                                    entry.isToday -> DeepBlue
                                    else -> ForgetMeNotBlue
                                },
                                topLeft = Offset(left, top),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )

                            if (entry.isToday && entry.seconds > 0L) {
                                drawCircle(
                                    color = PollenYellow,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(
                                        left + barW / 2f,
                                        (top - 6.dp.toPx()).coerceAtLeast(4.dp.toPx())
                                    )
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        history.forEach { entry ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatShortBarDuration(entry.seconds),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (entry.isToday) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (entry.isToday) DeepBlue else MidnightNavy,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = entry.shortDayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedNavy,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageMetricPill(
    label: String,
    value: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    TonalInsetPanel(
        containerColor = if (highlighted) SkyBlue.copy(alpha = 0.48f) else SoftIceSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) DeepBlue else MutedNavy
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MidnightNavy
            )
        }
    }
}

private fun formatShortBarDuration(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val hours = seconds / 3600L
    val mins = (seconds % 3600L) / 60L
    return when {
        hours > 0L -> "${hours}h"
        mins > 0L -> "${mins}m"
        else -> "${seconds}s"
    }
}
