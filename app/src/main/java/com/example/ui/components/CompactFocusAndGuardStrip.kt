package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus.DoomscrollGuardManager
import com.example.focus.FocusPhase
import com.example.focus.FocusSessionManager
import com.example.ui.theme.ForgetMeNotTheme
import com.example.ui.theme.LocalForgetMeNotColors

/**
 * Compact Focus & Guard status strip for TodayScreen.
 * Shows a single sleek 2-pill row when both Pomodoro and Focus Guard are idle to prevent
 * dashboard clutter, and expands automatically into live cards whenever either tool is active.
 */
@Composable
fun CompactFocusAndGuardStrip(
    onOpenFocusSession: () -> Unit,
    onOpenFocusGuard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalForgetMeNotColors.current
    LaunchedEffect(context) {
        FocusSessionManager.initialize(context)
        DoomscrollGuardManager.initialize(context)
    }

    val focusState by FocusSessionManager.state.collectAsStateWithLifecycle()
    val guardConfig by DoomscrollGuardManager.config.collectAsStateWithLifecycle()
    val guardStatus by DoomscrollGuardManager.liveStatus.collectAsStateWithLifecycle()

    val focusActive = focusState.phase != FocusPhase.IDLE
    val guardActive = guardConfig.isEnabled && guardStatus.isServiceRunning

    Column(
        modifier = modifier.testTag("compact_focus_guard_strip"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (focusActive) {
            LiveFocusSessionBannerCard(
                onOpenModal = onOpenFocusSession,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (guardActive) {
            LiveFocusGuardBannerCard(
                onClick = onOpenFocusGuard,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!focusActive || !guardActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!focusActive) {
                    Surface(
                        onClick = onOpenFocusSession,
                        shape = MaterialTheme.shapes.medium,
                        color = palette.surfaceCard,
                        border = BorderStroke(0.75.dp, palette.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("today_compact_focus_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Focus · ${focusState.focusDurationMinutes}m",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to start timer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (!guardActive) {
                    Surface(
                        onClick = onOpenFocusGuard,
                        shape = MaterialTheme.shapes.medium,
                        color = palette.surfaceCard,
                        border = BorderStroke(0.75.dp, palette.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("today_compact_guard_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = palette.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Focus Guard",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (guardConfig.isEnabled) "Ready" else "Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@BunnyPreview
@Composable
private fun CompactFocusAndGuardStripPreview() {
    ForgetMeNotTheme {
        Surface(color = LocalForgetMeNotColors.current.surfaceCanvas) {
            CompactFocusAndGuardStrip(
                onOpenFocusSession = {},
                onOpenFocusGuard = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
