package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.PlannerItem
import com.example.focus.DoomscrollGuardManager
import com.example.focus.FocusPhase
import com.example.focus.FocusSessionManager
import com.example.ui.theme.BunnyFlowerCrown
import com.example.ui.theme.BunnyFurWhite
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Isolated leaf Pomodoro banner card on TodayScreen so 1-second timer ticks never recompose TodayScreen.
 */
@Composable
fun LiveFocusSessionBannerCard(
    onOpenModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    LaunchedEffect(context) { FocusSessionManager.initialize(context) }

    val focusState by FocusSessionManager.state.collectAsStateWithLifecycle()
    val guardConfig by DoomscrollGuardManager.config.collectAsStateWithLifecycle()

    val isFocusActive = focusState.phase == FocusPhase.FOCUS && focusState.isRunning
    val isBreakActive = focusState.phase == FocusPhase.BREAK && focusState.isRunning

    Surface(
        onClick = {
            feedback.onPopOpen()
            onOpenModal()
        },
        shape = MaterialTheme.shapes.medium,
        color = PureWhite,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isFocusActive -> DeepBlue.copy(alpha = 0.55f)
                isBreakActive -> CalmSage.copy(alpha = 0.55f)
                else -> SubtleBorder
            }
        ),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("today_focus_session_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isFocusActive -> SkyBlue.copy(alpha = 0.75f)
                    isBreakActive -> CalmSageContainer
                    else -> PollenSoft
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isBreakActive) Icons.Rounded.Coffee else Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = if (isBreakActive) CalmSage else DeepBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when {
                            isFocusActive -> "Focus · ${focusState.formattedCountdown()}"
                            isBreakActive -> "Break · ${focusState.formattedCountdown()}"
                            focusState.phase == FocusPhase.COMPLETED -> "Focus session complete!"
                            else -> stringResource(R.string.focus_session_title)
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isFocusActive && focusState.blockAppsDuringFocus) {
                        ModernPillBadge(
                            text = "${guardConfig.watchedPackages.size} blocked",
                            containerColor = CalmSageContainer,
                            contentColor = CalmSage
                        )
                    }
                }
                Text(
                    text = when {
                        isFocusActive && !focusState.linkedTaskTitle.isNullOrBlank() ->
                            "Committed to: ${focusState.linkedTaskTitle}"
                        isFocusActive ->
                            "Blocking distracting apps while your timer runs"
                        focusState.completedSessionsToday > 0 ->
                            "${focusState.completedSessionsToday} sessions · ${focusState.totalFocusMinutesToday}m focused today"
                        else ->
                            "Pomodoro timer that blocks distracting apps while you work"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                onClick = {
                    if (focusState.isRunning) {
                        feedback.onLightTick()
                        FocusSessionManager.pause(context)
                    } else {
                        feedback.onPopOpen()
                        FocusSessionManager.startOrResume(context)
                    }
                },
                shape = CircleShape,
                color = DeepBlue,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("today_focus_quick_toggle")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (focusState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (focusState.isRunning) "Pause focus timer" else "Start focus timer",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full Pomodoro Focus Session modal with circular progress timer, task commitment picker,
 * duration presets, break mode, and live distracting-app blocking controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusSessionModal(
    activeTasks: List<PlannerItem>,
    onOpenBlockedAppsSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }

    LaunchedEffect(context) {
        FocusSessionManager.initialize(context)
        DoomscrollGuardManager.initialize(context)
    }

    val focusState by FocusSessionManager.state.collectAsStateWithLifecycle()
    val guardConfig by DoomscrollGuardManager.config.collectAsStateWithLifecycle()
    var permsReady by remember { mutableStateOf(DoomscrollGuardManager.hasAllRequiredPermissions(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permsReady = DoomscrollGuardManager.hasAllRequiredPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = focusState.progressFraction,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "pomodoro_ring_progress"
    )

    BunnyMemoModalContainer(
        title = stringResource(R.string.focus_session_title),
        subtitle = stringResource(R.string.focus_session_subtitle),
        onDismiss = onDismiss,
        maxWidth = 520.dp
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 570.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular Pomodoro Ring Canvas + Bunny centerpiece
            Box(
                modifier = Modifier
                    .size(224.dp)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                PomodoroRingCanvas(
                    progress = animatedProgress,
                    isBreak = focusState.phase == FocusPhase.BREAK,
                    isCompleted = focusState.phase == FocusPhase.COMPLETED,
                    modifier = Modifier.size(212.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SmallBunnyIllustration(
                        pose = when (focusState.phase) {
                            FocusPhase.COMPLETED -> BunnyPose.CELEBRATING
                            FocusPhase.BREAK -> BunnyPose.RESTING
                            FocusPhase.FOCUS -> BunnyPose.STUDYING
                            else -> BunnyPose.STUDYING
                        },
                        size = 64.dp,
                        contentDesc = "Focus session bunny"
                    )
                    Text(
                        text = if (focusState.phase == FocusPhase.COMPLETED) "Done!" else focusState.formattedCountdown(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MidnightNavy,
                        modifier = Modifier.testTag("focus_timer_countdown_text")
                    )
                    Text(
                        text = when (focusState.phase) {
                            FocusPhase.FOCUS -> if (focusState.isRunning) "Deep Focus" else "Paused"
                            FocusPhase.BREAK -> if (focusState.isRunning) "Gentle Break" else "Break Paused"
                            FocusPhase.COMPLETED -> "Bloom earned!"
                            FocusPhase.IDLE -> "Ready to focus"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (focusState.phase == FocusPhase.BREAK) CalmSage else DeepBlue
                    )
                }
            }

            // Primary playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        feedback.onLightTick()
                        FocusSessionManager.reset(context)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SoftIceSurface)
                        .testTag("focus_timer_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset timer",
                        tint = MidnightNavy
                    )
                }

                Button(
                    onClick = {
                        if (focusState.isRunning) {
                            feedback.onLightTick()
                            FocusSessionManager.pause(context)
                        } else {
                            feedback.onPopOpen()
                            FocusSessionManager.startOrResume(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (focusState.phase == FocusPhase.BREAK) CalmSage else DeepBlue,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("focus_timer_start_pause_button")
                ) {
                    Icon(
                        imageVector = if (focusState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            focusState.isRunning -> "Pause"
                            focusState.phase == FocusPhase.FOCUS -> "Resume Focus"
                            focusState.phase == FocusPhase.BREAK -> "Resume Break"
                            focusState.phase == FocusPhase.COMPLETED -> "Start Another"
                            else -> "Start Focus"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                IconButton(
                    onClick = {
                        feedback.onPopOpen()
                        FocusSessionManager.startBreakNow(context)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CalmSageContainer)
                        .testTag("focus_timer_break_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Coffee,
                        contentDescription = "Start break timer",
                        tint = CalmSage
                    )
                }
            }

            if (focusState.phase == FocusPhase.FOCUS && focusState.remainingSeconds < focusState.totalSeconds) {
                TextButton(
                    onClick = { FocusSessionManager.finishEarlyAndCount(context) },
                    modifier = Modifier.testTag("focus_timer_finish_early_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = CalmSage,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Complete session early", color = CalmSage)
                }
            }

            // Today's Pomodoro stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TonalInsetPanel(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${focusState.completedSessionsToday}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MidnightNavy
                        )
                        Text(
                            text = "Sessions today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }
                TonalInsetPanel(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${focusState.totalFocusMinutesToday}m",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = DeepBlue
                        )
                        Text(
                            text = "Focused today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }
            }

            // Distracting App Blocker Panel
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (focusState.blockAppsDuringFocus && permsReady) CalmSageContainer.copy(alpha = 0.55f) else SoftIceSurface,
                border = BorderStroke(
                    1.dp,
                    if (focusState.blockAppsDuringFocus && !permsReady) OverdueTerracotta.copy(alpha = 0.45f)
                    else SubtleBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = if (focusState.blockAppsDuringFocus && permsReady) CalmSage else DeepBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Block distracting apps",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MidnightNavy
                                )
                                Text(
                                    text = if (permsReady) {
                                        "Blocks ${guardConfig.watchedPackages.size} chosen apps while timer runs"
                                    } else {
                                        "Grant Usage Access & Overlay to block ${guardConfig.watchedPackages.size} apps"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!permsReady && focusState.blockAppsDuringFocus) OverdueTerracotta else MutedNavy
                                )
                            }
                        }
                        Switch(
                            checked = focusState.blockAppsDuringFocus,
                            onCheckedChange = { enabled ->
                                feedback.onTaskToggled(enabled)
                                FocusSessionManager.setBlockAppsDuringFocus(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = CalmSage
                            ),
                            modifier = Modifier.testTag("focus_session_block_apps_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            feedback.onPopOpen()
                            requestDismiss()
                            onOpenBlockedAppsSetup()
                        },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("focus_session_manage_apps_button")
                    ) {
                        Text(
                            text = if (permsReady) "Customize blocked apps (${guardConfig.watchedPackages.size})" else "Set up app blocking permissions",
                            style = MaterialTheme.typography.labelLarge,
                            color = DeepBlue
                        )
                    }
                }
            }

            HorizontalDivider(color = SubtleBorder)

            // Focus & Break Duration Presets
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Focus duration",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15 to "15m Sprint", 25 to "25m Classic", 45 to "45m Deep", 60 to "60m Study").forEach { (mins, label) ->
                        FilterChip(
                            selected = focusState.focusDurationMinutes == mins,
                            onClick = {
                                feedback.onLightTick()
                                FocusSessionManager.setFocusDuration(context, mins)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SkyBlue,
                                selectedLabelColor = MidnightNavy
                            )
                        )
                    }
                }

                Text(
                    text = "Break length",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { mins ->
                        FilterChip(
                            selected = focusState.breakDurationMinutes == mins,
                            onClick = {
                                feedback.onLightTick()
                                FocusSessionManager.setBreakDuration(context, mins)
                            },
                            label = { Text("${mins}m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CalmSageContainer,
                                selectedLabelColor = CalmSage
                            )
                        )
                    }
                }
            }

            // Optional Task Commitment Selector
            if (activeTasks.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Commit to a task (optional)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = focusState.linkedTaskTitle == null,
                            onClick = {
                                feedback.onLightTick()
                                FocusSessionManager.setLinkedTaskTitle(null)
                            },
                            label = { Text("Free focus") }
                        )
                        activeTasks.take(8).forEach { task ->
                            FilterChip(
                                selected = focusState.linkedTaskTitle == task.title,
                                onClick = {
                                    feedback.onLightTick()
                                    FocusSessionManager.setLinkedTaskTitle(task.title)
                                },
                                label = {
                                    Text(
                                        text = task.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PollenSoft,
                                    selectedLabelColor = MidnightNavy
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PomodoroRingCanvas(
    progress: Float,
    isBreak: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = when {
        isCompleted -> PollenYellow
        isBreak -> CalmSage
        else -> DeepBlue
    }
    val trackColor = SkyBlue.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val strokePx = 12.dp.toPx()
        val inset = strokePx / 2f + 10.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val topLeft = Offset(inset, inset)

        // Track ring
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Active sweep arc
        val sweep = (progress * 360f).coerceIn(0f, 360f)
        if (sweep > 0.5f) {
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Orbiting forget-me-not bloom marker at the tip of the arc
        val tipRad = ((-90f + sweep) * PI / 180.0)
        val radius = arcSize.width / 2f
        val cx = size.width / 2f + (radius * cos(tipRad)).toFloat()
        val cy = size.height / 2f + (radius * sin(tipRad)).toFloat()
        val flowerR = 9.dp.toPx()
        val petalDist = flowerR * 0.48f
        val petalR = flowerR * 0.38f
        for (i in 0 until 5) {
            val pRad = (-90.0 + i * 72.0) * PI / 180.0
            drawCircle(
                color = if (isBreak) CalmSage else ForgetMeNotBlue,
                radius = petalR,
                center = Offset(cx + (petalDist * cos(pRad)).toFloat(), cy + (petalDist * sin(pRad)).toFloat())
            )
        }
        drawCircle(BunnyFurWhite, radius = flowerR * 0.28f, center = Offset(cx, cy))
        drawCircle(PollenYellow, radius = flowerR * 0.19f, center = Offset(cx, cy))
    }
}
