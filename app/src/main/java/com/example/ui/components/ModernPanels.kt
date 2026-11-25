package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

/**
 * Modern elevated card panel following a 60-30-10 aesthetic:
 * Crisp pure-white surface, whisper-soft ambient shadow, and hairline neutral border
 * instead of heavy saturated outlines.
 */
@Composable
fun ModernPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = PureWhite,
    borderColor: Color = SubtleBorder.copy(alpha = 0.85f),
    borderWidth: Dp = 0.75.dp,
    elevation: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shadowModifier = modifier.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = MidnightNavy.copy(alpha = 0.08f),
        spotColor = MidnightNavy.copy(alpha = 0.08f)
    )
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = containerColor,
            border = BorderStroke(borderWidth, borderColor),
            modifier = shadowModifier
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = BorderStroke(borderWidth, borderColor),
            modifier = shadowModifier
        ) {
            Column(content = content)
        }
    }
}

/**
 * Quiet borderless secondary tonal panel (part of the 30% secondary layer) used inside
 * cards/modals to avoid nested bordered-box clutter.
 */
@Composable
fun TonalInsetPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = SoftIceSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
    ) {
        Column(content = content)
    }
}

/**
 * Sleek modern pill-track segmented control replacing clunky bordered FilterChip rows.
 */
@Composable
fun <T> ModernSegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "segment"
) {
    Surface(
        color = SoftIceSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) PureWhite else Color.Transparent,
                    animationSpec = tween(180),
                    label = "segment_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MidnightNavy else MutedNavy,
                    animationSpec = tween(180),
                    label = "segment_fg"
                )
                val pillShape = RoundedCornerShape(11.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = pillShape,
                                    ambientColor = MidnightNavy.copy(alpha = 0.10f),
                                    spotColor = MidnightNavy.copy(alpha = 0.10f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(pillShape)
                        .background(bgColor)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("${tagPrefix}_${value.toString().lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
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

/**
 * Clean editorial section header with understated neutral count badge.
 */
@Composable
fun ModernSectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MidnightNavy
        )
        if (count != null) {
            Surface(
                color = SoftIceSurface,
                shape = CircleShape
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MutedNavy,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Understated pill badge for status or metadata tags.
 */
@Composable
fun ModernPillBadge(
    text: String,
    containerColor: Color = SoftIceSurface,
    contentColor: Color = MutedNavy,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Selected/accent card highlight color kept calm and restrained.
 */
fun selectedPanelContainer(isSelected: Boolean): Color =
    if (isSelected) SkyBlue.copy(alpha = 0.28f) else PureWhite

fun selectedPanelBorder(isSelected: Boolean, isOverdue: Boolean = false, overdueColor: Color = Color.Unspecified): Color =
    when {
        isSelected -> DeepBlue.copy(alpha = 0.65f)
        isOverdue && overdueColor != Color.Unspecified -> overdueColor.copy(alpha = 0.35f)
        else -> SubtleBorder.copy(alpha = 0.85f)
    }
