package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 adaptive width tiers derived from container constraints (BoxWithConstraints),
 * ensuring seamless scaling across phones, foldables, portrait tablets, landscape tablets,
 * and split-screen multi-window modes.
 */
enum class WindowWidthTier {
    COMPACT,   // < 600.dp (phones, narrow split-screen)
    MEDIUM,    // 600.dp .. 839.dp (foldables, 7-8" portrait tablets)
    EXPANDED   // >= 840.dp (tablets landscape, 10-13" tablets, desktop/ChromeOS)
}

fun resolveWidthTier(availableWidth: Dp): WindowWidthTier = when {
    availableWidth >= 840.dp -> WindowWidthTier.EXPANDED
    availableWidth >= 600.dp -> WindowWidthTier.MEDIUM
    else -> WindowWidthTier.COMPACT
}

/**
 * Responsive horizontal screen padding that breathes more on wide tablet viewports
 * while staying crisp and space-efficient on compact phones.
 */
fun adaptiveHorizontalPadding(availableWidth: Dp): Dp = when {
    availableWidth >= 1000.dp -> 28.dp
    availableWidth >= 600.dp -> 24.dp
    else -> 18.dp
}

/**
 * Adaptive detail-pane width for list-detail split layouts on tablets & large screens.
 */
fun adaptiveDetailPaneWidth(totalWidth: Dp): Dp = when {
    totalWidth >= 1280.dp -> 460.dp
    totalWidth >= 1000.dp -> 420.dp
    totalWidth >= 840.dp -> 380.dp
    else -> 348.dp
}

/**
 * Lightweight 2-column responsive row helper for placing cards in balanced pairs
 * on tablets while keeping a clean 1-column stack on phones.
 */
@Composable
fun <T> ResponsiveCardGrid(
    items: List<T>,
    columns: Int,
    horizontalSpacing: Dp = 14.dp,
    verticalSpacing: Dp = 12.dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    if (columns <= 1) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            items.forEach { item ->
                itemContent(item)
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            itemContent(item)
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
