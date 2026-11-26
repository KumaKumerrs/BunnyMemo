package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WorkOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TaskCategory
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

enum class TaskCategoryFilter(val label: String, val category: TaskCategory?) {
    ALL("All", null),
    PERSONAL("Personal", TaskCategory.PERSONAL),
    WORK("Work", TaskCategory.WORK),
    HEALTH("Health", TaskCategory.HEALTH),
    STUDY("Study", TaskCategory.STUDY),
    CREATIVE("Creative", TaskCategory.CREATIVE),
    ERRANDS("Errands", TaskCategory.ERRANDS)
}

data class CategoryVisualPalette(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val icon: ImageVector
)

@Composable
fun categoryPalette(category: TaskCategory): CategoryVisualPalette {
    // Use calm luminance-balanced colors derived from current canvas brightness so labels shine in light & dark mode
    val isDark = MidnightNavy.red > 0.7f || isSystemInDarkTheme() && PureWhite.red < 0.3f
    return when (category) {
        TaskCategory.PERSONAL -> CategoryVisualPalette(
            containerColor = if (isDark) Color(0xFF213654) else Color(0xFFE7F2FF),
            contentColor = if (isDark) Color(0xFF9FCAFF) else Color(0xFF255CA8),
            borderColor = if (isDark) Color(0xFF426A9E) else Color(0xFFB3D5FC),
            accentColor = DeepBlue,
            icon = Icons.Outlined.PersonOutline
        )
        TaskCategory.WORK -> CategoryVisualPalette(
            containerColor = if (isDark) Color(0xFF2D274D) else Color(0xFFEEEBFE),
            contentColor = if (isDark) Color(0xFFC5B9FF) else Color(0xFF5642A8),
            borderColor = if (isDark) Color(0xFF534887) else Color(0xFFCFC6FA),
            accentColor = if (isDark) Color(0xFFA794FF) else Color(0xFF6B52D1),
            icon = Icons.Outlined.WorkOutline
        )
        TaskCategory.HEALTH -> CategoryVisualPalette(
            containerColor = CalmSageContainer,
            contentColor = CalmSage,
            borderColor = CalmSage.copy(alpha = 0.42f),
            accentColor = CalmSage,
            icon = Icons.Outlined.FavoriteBorder
        )
        TaskCategory.STUDY -> CategoryVisualPalette(
            containerColor = if (isDark) Color(0xFF1F363B) else Color(0xFFE3F6F8),
            contentColor = if (isDark) Color(0xFF7AD6E0) else Color(0xFF196E78),
            borderColor = if (isDark) Color(0xFF36646D) else Color(0xFFAEE2E8),
            accentColor = if (isDark) Color(0xFF5FC6D2) else Color(0xFF1F8794),
            icon = Icons.Outlined.MenuBook
        )
        TaskCategory.CREATIVE -> CategoryVisualPalette(
            containerColor = if (isDark) Color(0xFF3C2336) else Color(0xFFFDEBF4),
            contentColor = if (isDark) Color(0xFFF6A3CE) else Color(0xFF9F356E),
            borderColor = if (isDark) Color(0xFF6E3B5F) else Color(0xFFF4BEDB),
            accentColor = if (isDark) Color(0xFFED82BA) else Color(0xFFC4498B),
            icon = Icons.Outlined.Brush
        )
        TaskCategory.ERRANDS -> CategoryVisualPalette(
            containerColor = PollenSoft,
            contentColor = if (isDark) Color(0xFFFAD976) else Color(0xFF8A5D0B),
            borderColor = if (isDark) Color(0xFF6E5B2A) else Color(0xFFF0CF75),
            accentColor = if (isDark) Color(0xFFFAD976) else Color(0xFFC78612),
            icon = Icons.Outlined.ShoppingBag
        )
    }
}

@Composable
fun TaskCategoryBadge(
    category: TaskCategory,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val palette = categoryPalette(category)
    val accessibilityText = stringResource(R.string.cd_task_category_badge, category.label)
    Surface(
        color = palette.containerColor,
        shape = CircleShape,
        border = BorderStroke(0.75.dp, palette.borderColor),
        modifier = modifier
            .semantics { contentDescription = accessibilityText }
            .testTag("category_badge_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 6.dp else 7.dp)
                    .clip(CircleShape)
                    .background(palette.accentColor)
            )
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = palette.contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TaskCategorySelector(
    selected: TaskCategory,
    onSelect: (TaskCategory) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = stringResource(R.string.category_label),
    tagPrefix: String = "category_selector"
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskCategory.entries.forEach { category ->
                val isSelected = category == selected
                val palette = categoryPalette(category)
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) palette.containerColor else SoftIceSurface,
                    animationSpec = tween(160),
                    label = "category_bg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) palette.borderColor else SubtleBorder.copy(alpha = 0.85f),
                    animationSpec = tween(160),
                    label = "category_border"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) palette.contentColor else MutedNavy,
                    animationSpec = tween(160),
                    label = "category_fg"
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bgColor,
                    border = BorderStroke(if (isSelected) 1.25.dp else 0.75.dp, borderColor),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(category) }
                        .testTag("${tagPrefix}_${category.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = palette.icon,
                            contentDescription = null,
                            tint = if (isSelected) palette.accentColor else MutedNavy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = category.label,
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
fun TaskCategoryFilterBar(
    selectedFilter: TaskCategoryFilter,
    counts: Map<TaskCategoryFilter, Int>,
    onSelectFilter: (TaskCategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "task_category_filter"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskCategoryFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = counts[filter] ?: 0
            val palette = filter.category?.let { categoryPalette(it) }
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
                                .background(p.accentColor)
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
