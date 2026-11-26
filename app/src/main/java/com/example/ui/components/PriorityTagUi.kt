package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TaskPriority
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueContainer
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

enum class TaskPriorityFilter(val label: String, val priority: TaskPriority?) {
    ALL("All", null),
    HIGH("High", TaskPriority.HIGH),
    MEDIUM("Medium", TaskPriority.MEDIUM),
    LOW("Low", TaskPriority.LOW)
}

data class PriorityVisualPalette(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val dotColor: Color
)

fun priorityPalette(priority: TaskPriority): PriorityVisualPalette = when (priority) {
    TaskPriority.HIGH -> PriorityVisualPalette(
        containerColor = OverdueContainer,
        contentColor = OverdueTerracotta,
        borderColor = OverdueTerracotta.copy(alpha = 0.42f),
        dotColor = OverdueTerracotta
    )
    TaskPriority.MEDIUM -> PriorityVisualPalette(
        containerColor = PollenSoft,
        contentColor = MidnightNavy,
        borderColor = PollenYellow.copy(alpha = 0.72f),
        dotColor = DeepBlue
    )
    TaskPriority.LOW -> PriorityVisualPalette(
        containerColor = CalmSageContainer,
        contentColor = CalmSage,
        borderColor = CalmSage.copy(alpha = 0.38f),
        dotColor = CalmSage
    )
}

@Composable
fun TaskPriorityBadge(
    priority: TaskPriority,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val palette = priorityPalette(priority)
    val accessibilityText = stringResource(R.string.cd_task_priority_badge, priority.label)
    Surface(
        color = palette.containerColor,
        shape = CircleShape,
        border = BorderStroke(0.75.dp, palette.borderColor),
        modifier = modifier
            .semantics { contentDescription = accessibilityText }
            .testTag("priority_badge_${priority.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (priority == TaskPriority.HIGH) Icons.Filled.Flag else Icons.Outlined.Flag,
                contentDescription = null,
                tint = palette.dotColor,
                modifier = Modifier.size(if (compact) 11.dp else 13.dp)
            )
            Text(
                text = priority.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TaskPrioritySelector(
    selected: TaskPriority,
    onSelect: (TaskPriority) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = stringResource(R.string.priority_label),
    tagPrefix: String = "priority_selector"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskPriority.entries.forEach { priority ->
                val isSelected = priority == selected
                val palette = priorityPalette(priority)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) palette.containerColor else SoftIceSurface,
                    animationSpec = tween(160),
                    label = "priority_bg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) palette.borderColor else SubtleBorder.copy(alpha = 0.85f),
                    animationSpec = tween(160),
                    label = "priority_border"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) palette.contentColor else MutedNavy,
                    animationSpec = tween(160),
                    label = "priority_fg"
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bgColor,
                    border = BorderStroke(if (isSelected) 1.25.dp else 0.75.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(priority) }
                        .testTag("${tagPrefix}_${priority.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected && priority == TaskPriority.HIGH) {
                                Icons.Filled.Flag
                            } else {
                                Icons.Outlined.Flag
                            },
                            contentDescription = null,
                            tint = if (isSelected) palette.dotColor else MutedNavy,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = priority.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskPriorityFilterBar(
    selectedFilter: TaskPriorityFilter,
    counts: Map<TaskPriorityFilter, Int>,
    onSelectFilter: (TaskPriorityFilter) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "task_priority_filter"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskPriorityFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = counts[filter] ?: 0
            val palette = filter.priority?.let { priorityPalette(it) }
            val containerColor = when {
                isSelected && palette != null -> palette.containerColor
                isSelected -> SkyBlue.copy(alpha = 0.75f)
                else -> PureWhite
            }
            val labelColor = when {
                isSelected && palette != null -> palette.contentColor
                isSelected -> MidnightNavy
                else -> MutedNavy
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelectFilter(filter) },
                leadingIcon = palette?.let { p ->
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(p.dotColor)
                        )
                    }
                },
                label = {
                    Text(
                        text = "${filter.label} ($count)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = containerColor,
                    selectedLabelColor = labelColor,
                    containerColor = PureWhite,
                    labelColor = MutedNavy
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = SubtleBorder.copy(alpha = 0.9f),
                    selectedBorderColor = palette?.borderColor ?: DeepBlue.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("${tagPrefix}_${filter.name.lowercase()}")
            )
        }
    }
}
