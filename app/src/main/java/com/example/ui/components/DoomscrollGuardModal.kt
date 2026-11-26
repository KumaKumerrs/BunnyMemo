package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.focus.DoomscrollGuardManager
import com.example.focus.WatchedAppCandidate
import com.example.ui.theme.BunnyEarBlush
import com.example.ui.theme.BunnyFlowerCrown
import com.example.ui.theme.BunnyFurWhite
import com.example.ui.theme.BunnyOutlineInk
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Isolated leaf banner card for TodayScreen showing Bunny Focus Guard status without recomposing the whole screen.
 */
@Composable
fun LiveFocusGuardBannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    LaunchedEffect(context) { DoomscrollGuardManager.initialize(context) }

    val config by DoomscrollGuardManager.config.collectAsStateWithLifecycle()
    val liveStatus by DoomscrollGuardManager.liveStatus.collectAsStateWithLifecycle()
    val isActive = config.isEnabled && liveStatus.isServiceRunning
    val watchedCount = config.watchedPackages.size

    Surface(
        onClick = {
            feedback.onPopOpen()
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = PureWhite,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) CalmSage.copy(alpha = 0.55f) else SubtleBorder
        ),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("today_focus_guard_banner")
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
                color = if (isActive) CalmSageContainer else SkyBlue.copy(alpha = 0.65f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (isActive) CalmSage else DeepBlue,
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
                        text = stringResource(R.string.focus_guard_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MidnightNavy,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isActive) CalmSageContainer else SoftIceSurface
                    ) {
                        Text(
                            text = if (isActive) stringResource(R.string.focus_guard_status_active)
                            else if (config.isEnabled) stringResource(R.string.focus_guard_status_needs_setup)
                            else stringResource(R.string.focus_guard_status_off),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) CalmSage else DeepBlue,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = if (isActive) {
                        "Watching $watchedCount social apps · Full bunny at ${DoomscrollGuardManager.formatDurationShort(config.maxScrollSeconds)}"
                    } else {
                        "Grow a giant bunny over social apps when you doomscroll too long"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Configure",
                style = MaterialTheme.typography.labelLarge,
                color = DeepBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Full Anti-Doomscroll Bunny Guard configuration modal with:
 * - Enable / Disable master switch
 * - Interactive Live Growth Simulator (auto-play + slider + Full-Screen Takeover Preview)
 * - Usage Access & Display Over Other Apps permission checklist (auto-refreshes on resume)
 * - Social media app picker (Facebook, Instagram, TikTok, YouTube, X, Reddit, etc. + custom package)
 * - Takeover duration selector (30s, 1m, 3m, 5m, 10m, 15m)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoomscrollGuardModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }

    LaunchedEffect(context) { DoomscrollGuardManager.initialize(context) }

    val config by DoomscrollGuardManager.config.collectAsStateWithLifecycle()
    val liveStatus by DoomscrollGuardManager.liveStatus.collectAsStateWithLifecycle()

    var hasUsageAccess by remember { mutableStateOf(DoomscrollGuardManager.hasUsageStatsPermission(context)) }
    var hasOverlayAccess by remember { mutableStateOf(DoomscrollGuardManager.hasOverlayPermission(context)) }
    var candidates by remember { mutableStateOf(DoomscrollGuardManager.getAvailableCandidates(context)) }

    // Refresh permissions & installed status whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = DoomscrollGuardManager.hasUsageStatsPermission(context)
                hasOverlayAccess = DoomscrollGuardManager.hasOverlayPermission(context)
                candidates = DoomscrollGuardManager.getAvailableCandidates(context)
                if (DoomscrollGuardManager.config.value.isEnabled) {
                    DoomscrollGuardManager.syncServiceState(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recompute candidates if custom packages change
    LaunchedEffect(config.customPackages) {
        candidates = DoomscrollGuardManager.getAvailableCandidates(context)
    }

    // Interactive simulator state so users can immediately see the growing bunny effect
    var simProgress by remember { mutableFloatStateOf(0.28f) }
    var isSimPlaying by remember { mutableStateOf(false) }
    var showFullTakeoverPreview by remember { mutableStateOf(false) }

    LaunchedEffect(isSimPlaying) {
        if (isSimPlaying) {
            if (simProgress >= 0.99f) simProgress = 0.05f
            while (isSimPlaying && simProgress < 1f) {
                delay(60L)
                simProgress = (simProgress + 0.015f).coerceAtMost(1f)
            }
            if (simProgress >= 1f) {
                isSimPlaying = false
                feedback.onCelebrateBloom()
                showFullTakeoverPreview = true
            }
        }
    }

    var customPkgInput by remember { mutableStateOf("") }
    var customPkgError by remember { mutableStateOf<String?>(null) }

    val allPermsReady = hasUsageAccess && hasOverlayAccess

    BunnyMemoModalContainer(
        title = stringResource(R.string.focus_guard_title),
        subtitle = stringResource(R.string.focus_guard_subtitle),
        onDismiss = onDismiss,
        maxWidth = 540.dp
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Master Enable Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (config.isEnabled && allPermsReady) CalmSageContainer else SoftIceSurface,
                border = BorderStroke(
                    1.dp,
                    if (config.isEnabled && allPermsReady) CalmSage.copy(alpha = 0.45f) else SubtleBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (config.isEnabled && allPermsReady) "Bunny Guard is active"
                            else if (config.isEnabled) "Permissions needed to activate"
                            else "Enable Anti-Doomscroll Guard",
                            style = MaterialTheme.typography.titleSmall,
                            color = MidnightNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (config.takeoversCount > 0) {
                                "Saved you from doomscrolling ${config.takeoversCount} ${if (config.takeoversCount == 1) "time" else "times"}"
                            } else if (liveStatus.isServiceRunning) {
                                "Watching ${config.watchedPackages.size} apps in the background"
                            } else {
                                "The longer you scroll a chosen app, the bigger the bunny gets"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = { enable ->
                            feedback.onTaskToggled(enable)
                            DoomscrollGuardManager.setGuardEnabled(context, enable)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = CalmSage
                        ),
                        modifier = Modifier.testTag("focus_guard_master_switch")
                    )
                }
            }

            // 2. Interactive Live Bunny Growth Simulator
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PureWhite,
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Live Bunny Growth Preview",
                                style = MaterialTheme.typography.titleSmall,
                                color = MidnightNavy,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    simProgress < 0.35f -> "Stage 1 · Peeking in the corner"
                                    simProgress < 0.70f -> "Stage 2 · Growing bigger & watching"
                                    simProgress < 0.99f -> "Stage 3 · Blocking your feed!"
                                    else -> "Stage 4 · Full-screen productivity shield!"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepBlue
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    feedback.onLightTick()
                                    isSimPlaying = !isSimPlaying
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SkyBlue.copy(alpha = 0.7f))
                                    .testTag("focus_guard_sim_play_button")
                            ) {
                                Icon(
                                    imageVector = if (isSimPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isSimPlaying) "Pause preview" else "Play preview",
                                    tint = MidnightNavy
                                )
                            }
                            IconButton(
                                onClick = {
                                    feedback.onPopOpen()
                                    isSimPlaying = false
                                    simProgress = 1f
                                    showFullTakeoverPreview = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PollenSoft)
                                    .testTag("focus_guard_preview_takeover_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = "Preview full-screen takeover",
                                    tint = MidnightNavy
                                )
                            }
                        }
                    }

                    // Simulated mini phone feed with growing bunny on top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SoftIceSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        // Mock social media feed skeleton behind the bunny
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(SubtleBorder)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(SubtleBorder)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(74.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SubtleBorder.copy(alpha = 0.65f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(SubtleBorder.copy(alpha = 0.8f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(SubtleBorder.copy(alpha = 0.6f))
                            )
                        }

                        // Darkening scrim as bunny approaches full takeover
                        if (simProgress > 0.55f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0B1324).copy(alpha = ((simProgress - 0.55f) * 1.4f).coerceIn(0f, 0.78f)))
                            )
                        }

                        // Progressively growing bunny illustration
                        val bunnySizeDp = (46f + simProgress * 124f).dp
                        GrowingGuardBunnyIllustration(
                            progress = simProgress,
                            modifier = Modifier
                                .align(if (simProgress < 0.45f) Alignment.BottomEnd else Alignment.Center)
                                .padding(if (simProgress < 0.45f) 10.dp else 4.dp)
                                .size(bunnySizeDp)
                        )

                        // Live percentage pill
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MidnightNavy.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            val simSec = (simProgress * config.maxScrollSeconds).toInt()
                            Text(
                                text = "${DoomscrollGuardManager.formatDurationShort(simSec)} / ${DoomscrollGuardManager.formatDurationShort(config.maxScrollSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Slider(
                        value = simProgress,
                        onValueChange = {
                            isSimPlaying = false
                            simProgress = it
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = DeepBlue,
                            activeTrackColor = DeepBlue,
                            inactiveTrackColor = SubtleBorder
                        ),
                        modifier = Modifier.testTag("focus_guard_sim_slider")
                    )
                }
            }

            // 3. Required Android Permissions Checklist
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Required Android Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy,
                    fontWeight = FontWeight.SemiBold
                )
                PermissionStatusRow(
                    icon = Icons.Rounded.QueryStats,
                    title = "Usage Access",
                    subtitle = "Detects when you open a selected social media app",
                    granted = hasUsageAccess,
                    onGrantClick = {
                        feedback.onLightTick()
                        DoomscrollGuardManager.openUsageAccessSettings(context)
                    },
                    testTag = "focus_guard_usage_perm_button"
                )
                PermissionStatusRow(
                    icon = Icons.Rounded.Layers,
                    title = "Display Over Other Apps",
                    subtitle = "Lets the bunny appear and grow over distracting feeds",
                    granted = hasOverlayAccess,
                    onGrantClick = {
                        feedback.onLightTick()
                        DoomscrollGuardManager.openOverlayPermissionSettings(context)
                    },
                    testTag = "focus_guard_overlay_perm_button"
                )
            }

            HorizontalDivider(color = SubtleBorder)

            // 4. Time Until Full-Screen Takeover
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Time until bunny fills the screen",
                        style = MaterialTheme.typography.titleSmall,
                        color = MidnightNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val presets = listOf(
                    30 to "30 sec",
                    60 to "1 min",
                    180 to "3 min",
                    300 to "5 min",
                    600 to "10 min",
                    900 to "15 min"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (sec, label) ->
                        val selected = config.maxScrollSeconds == sec
                        FilterChip(
                            selected = selected,
                            onClick = {
                                feedback.onLightTick()
                                DoomscrollGuardManager.setMaxScrollSeconds(context, sec)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SkyBlue,
                                selectedLabelColor = MidnightNavy
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = SubtleBorder)

            // 5. Choose Social Media / Distracting Apps
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Apps to guard (${config.watchedPackages.size} selected)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MidnightNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Pick Facebook, Instagram, TikTok, YouTube, or any distracting app you want Bunny Guard to watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidates.forEach { app ->
                        val isWatched = app.packageName in config.watchedPackages
                        WatchedAppChip(
                            app = app,
                            selected = isWatched,
                            onToggle = {
                                feedback.onLightTick()
                                DoomscrollGuardManager.toggleWatchedPackage(context, app.packageName)
                            },
                            onRemoveCustom = if (app.isCustom) {
                                {
                                    feedback.onLightTick()
                                    DoomscrollGuardManager.removeCustomPackage(context, app.packageName)
                                }
                            } else null
                        )
                    }
                }

                // Add custom app package
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customPkgInput,
                        onValueChange = {
                            customPkgInput = it.take(100)
                            customPkgError = null
                        },
                        label = { Text("Add another app package (e.g. com.example.app)") },
                        singleLine = true,
                        isError = customPkgError != null,
                        supportingText = customPkgError?.let { msg -> { Text(msg) } },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val ok = DoomscrollGuardManager.addCustomPackage(context, customPkgInput)
                            if (ok) {
                                feedback.onPopOpen()
                                customPkgInput = ""
                                customPkgError = null
                            } else {
                                customPkgError = "Enter a valid package ID"
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DeepBlue)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add custom app",
                            tint = PureWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    feedback.onPopOpen()
                    simProgress = 1f
                    showFullTakeoverPreview = true
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text("Test Takeover", color = MidnightNavy)
            }
            Button(
                onClick = {
                    DoomscrollGuardManager.syncServiceState(context)
                    requestDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("focus_guard_done_button")
            ) {
                Text("Done")
            }
        }
    }

    if (showFullTakeoverPreview) {
        FullScreenTakeoverPreviewDialog(
            durationLabel = DoomscrollGuardManager.formatDurationShort(config.maxScrollSeconds),
            onDismiss = { showFullTakeoverPreview = false }
        )
    }
}

@Composable
private fun PermissionStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrantClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (granted) CalmSageContainer.copy(alpha = 0.65f) else OverdueContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (granted) CalmSage.copy(alpha = 0.4f) else OverdueTerracotta.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) CalmSage else OverdueTerracotta,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy
                )
            }
            if (granted) {
                Text(
                    text = "Allowed",
                    style = MaterialTheme.typography.labelMedium,
                    color = CalmSage,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                OutlinedButton(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag(testTag)
                ) {
                    Text("Allow", style = MaterialTheme.typography.labelMedium, color = DeepBlue)
                }
            }
        }
    }
}

@Composable
private fun WatchedAppChip(
    app: WatchedAppCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
    onRemoveCustom: (() -> Unit)?
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(app.displayName)
                if (app.isInstalled) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CalmSage)
                    )
                }
            }
        },
        trailingIcon = if (onRemoveCustom != null) {
            {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove custom app",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(role = Role.Button, onClick = onRemoveCustom)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SkyBlue,
            selectedLabelColor = MidnightNavy
        )
    )
}

@Composable
private fun FullScreenTakeoverPreviewDialog(
    durationLabel: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B1324))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = PollenYellow
                ) {
                    Text(
                        text = "$durationLabel of doomscrolling reached",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF2B4360),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }

                GrowingGuardBunnyIllustration(
                    progress = 1f,
                    modifier = Modifier.size(260.dp)
                )

                Text(
                    text = "Time to bloom, not doomscroll!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFFCFEFF),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "When your timer hits 100%, the giant Bunny Memo guardian fills your screen to bring you right back to your tasks and goals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFDCECFD),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82DC),
                        contentColor = Color(0xFFFCFEFF)
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("takeover_preview_back_button")
                ) {
                    Text("Back to Bunny Memo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onDismiss) {
                    Text("Close preview", color = Color(0xFFC7DFF8))
                }
            }
        }
    }
}

/**
 * Compose Canvas version of the Anti-Doomscroll Bunny whose expression gets progressively
 * more determined as [progress] grows from 0f (peeking) to 1f (full takeover).
 */
@Composable
fun GrowingGuardBunnyIllustration(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        if (sizePx <= 4f) return@Canvas
        val ox = (size.width - sizePx) / 2f
        val oy = (size.height - sizePx) / 2f
        val strokeW = (sizePx * 0.03f).coerceIn(2.2f, 7f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        if (p > 0.25f) {
            drawCircle(
                color = ForgetMeNotBlue.copy(alpha = (p * 0.42f).coerceIn(0.1f, 0.42f)),
                radius = sizePx * 0.44f,
                center = Offset(ox + sizePx * 0.50f, oy + sizePx * 0.56f)
            )
        }

        val earTilt = 10f + p * 8f
        drawGuardEar(ox + sizePx * 0.39f, oy + sizePx * 0.35f, sizePx, -earTilt, stroke)
        drawGuardEar(ox + sizePx * 0.61f, oy + sizePx * 0.35f, sizePx, earTilt, stroke)

        // Body
        drawOval(BunnyFurWhite, Offset(ox + sizePx * 0.24f, oy + sizePx * 0.53f), Size(sizePx * 0.52f, sizePx * 0.37f))
        drawOval(BunnyOutlineInk, Offset(ox + sizePx * 0.24f, oy + sizePx * 0.53f), Size(sizePx * 0.52f, sizePx * 0.37f), style = stroke)

        // Head
        drawOval(BunnyFurWhite, Offset(ox + sizePx * 0.23f, oy + sizePx * 0.28f), Size(sizePx * 0.54f, sizePx * 0.39f))
        drawOval(BunnyOutlineInk, Offset(ox + sizePx * 0.23f, oy + sizePx * 0.28f), Size(sizePx * 0.54f, sizePx * 0.39f), style = stroke)

        // Cheeks
        val cheekColor = if (p >= 0.70f) Color(0xFFF5B0B8) else BunnyEarBlush
        drawCircle(cheekColor, radius = sizePx * 0.048f, center = Offset(ox + sizePx * 0.33f, oy + sizePx * 0.52f))
        drawCircle(cheekColor, radius = sizePx * 0.048f, center = Offset(ox + sizePx * 0.67f, oy + sizePx * 0.52f))

        // Eyes
        drawCircle(BunnyOutlineInk, radius = sizePx * 0.030f, center = Offset(ox + sizePx * 0.41f, oy + sizePx * 0.45f))
        drawCircle(BunnyOutlineInk, radius = sizePx * 0.030f, center = Offset(ox + sizePx * 0.59f, oy + sizePx * 0.45f))

        // Determined eyebrows
        if (p >= 0.35f) {
            val intensity = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val dip = sizePx * 0.028f * intensity
            drawLine(
                color = BunnyOutlineInk,
                start = Offset(ox + sizePx * 0.35f, oy + sizePx * 0.39f - dip * 0.3f),
                end = Offset(ox + sizePx * 0.45f, oy + sizePx * 0.39f + dip),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = BunnyOutlineInk,
                start = Offset(ox + sizePx * 0.65f, oy + sizePx * 0.39f - dip * 0.3f),
                end = Offset(ox + sizePx * 0.55f, oy + sizePx * 0.39f + dip),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }

        // Nose & progressive mouth
        drawCircle(BunnyOutlineInk, radius = sizePx * 0.019f, center = Offset(ox + sizePx * 0.50f, oy + sizePx * 0.50f))
        when {
            p < 0.45f -> {
                val smile = Path().apply {
                    moveTo(ox + sizePx * 0.45f, oy + sizePx * 0.545f)
                    quadraticTo(ox + sizePx * 0.50f, oy + sizePx * 0.58f, ox + sizePx * 0.55f, oy + sizePx * 0.545f)
                }
                drawPath(smile, BunnyOutlineInk, style = stroke)
            }
            p < 0.80f -> {
                drawCircle(BunnyOutlineInk, radius = sizePx * 0.022f, center = Offset(ox + sizePx * 0.50f, oy + sizePx * 0.555f), style = stroke)
            }
            else -> {
                val frown = Path().apply {
                    moveTo(ox + sizePx * 0.44f, oy + sizePx * 0.575f)
                    quadraticTo(ox + sizePx * 0.50f, oy + sizePx * 0.535f, ox + sizePx * 0.56f, oy + sizePx * 0.575f)
                }
                drawPath(frown, BunnyOutlineInk, style = stroke)
            }
        }

        // Paws
        drawOval(BunnyFurWhite, Offset(ox + sizePx * 0.31f, oy + sizePx * 0.78f), Size(sizePx * 0.14f, sizePx * 0.10f))
        drawOval(BunnyOutlineInk, Offset(ox + sizePx * 0.31f, oy + sizePx * 0.78f), Size(sizePx * 0.14f, sizePx * 0.10f), style = stroke)
        drawOval(BunnyFurWhite, Offset(ox + sizePx * 0.55f, oy + sizePx * 0.78f), Size(sizePx * 0.14f, sizePx * 0.10f))
        drawOval(BunnyOutlineInk, Offset(ox + sizePx * 0.55f, oy + sizePx * 0.78f), Size(sizePx * 0.14f, sizePx * 0.10f), style = stroke)

        // Forget-me-not bloom on bunny's crown
        val fx = ox + sizePx * 0.64f
        val fy = oy + sizePx * 0.29f
        val fr = sizePx * 0.082f
        for (i in 0 until 5) {
            val rad = ((-90.0 + i * 72.0) * PI / 180.0)
            drawCircle(
                color = ForgetMeNotBlue,
                radius = fr * 0.38f,
                center = Offset(fx + (fr * 0.48f * cos(rad)).toFloat(), fy + (fr * 0.48f * sin(rad)).toFloat())
            )
        }
        drawCircle(BunnyFlowerCrown, radius = fr * 0.30f, center = Offset(fx, fy))
        drawCircle(PollenYellow, radius = fr * 0.21f, center = Offset(fx, fy))
    }
}

private fun DrawScope.drawGuardEar(
    pivotX: Float,
    pivotY: Float,
    sizePx: Float,
    angleDeg: Float,
    stroke: Stroke
) {
    rotate(angleDeg, pivot = Offset(pivotX, pivotY)) {
        drawOval(
            color = BunnyFurWhite,
            topLeft = Offset(pivotX - sizePx * 0.072f, pivotY - sizePx * 0.29f),
            size = Size(sizePx * 0.144f, sizePx * 0.32f)
        )
        drawOval(
            color = BunnyOutlineInk,
            topLeft = Offset(pivotX - sizePx * 0.072f, pivotY - sizePx * 0.29f),
            size = Size(sizePx * 0.144f, sizePx * 0.32f),
            style = stroke
        )
        drawOval(
            color = BunnyEarBlush,
            topLeft = Offset(pivotX - sizePx * 0.034f, pivotY - sizePx * 0.23f),
            size = Size(sizePx * 0.068f, sizePx * 0.20f)
        )
    }
}
