package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.CalendarDate
import com.example.data.CalendarInvite
import com.example.data.DailyHabitRepository
import com.example.viewmodel.PlannerUiState
import com.example.data.RoomSyncStatus
import com.example.data.SyncPhase
import com.example.data.TaskCategory
import com.example.focus.FocusSessionManager
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyPose
import com.example.ui.components.LiveAppUsageSummaryCard
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.MonthlyBunnyAlmanacModal
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.TaskCategoryBadge
import com.example.ui.components.TonalInsetPanel
import com.example.ui.components.computeYearBouquetFlowerCount
import com.example.ui.components.formatCompactDuration
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    state: PlannerUiState.Ready,
    userDisplayName: String?,
    userEmail: String?,
    userPhotoUrl: String?,
    isLocalMode: Boolean,
    syncStatus: RoomSyncStatus,
    calendarInvites: List<CalendarInvite>,
    isExpanded: Boolean,
    bottomContentPadding: Dp = 96.dp,
    onEditDedication: () -> Unit,
    onOpenSettings: () -> Unit,
    onInviteFriendClick: () -> Unit,
    onTriggerSync: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val habitRepo = remember(context) { DailyHabitRepository.getInstance(context) }
    val habitSnapshot by habitRepo.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(context) { FocusSessionManager.initialize(context) }
    val focusState by FocusSessionManager.state.collectAsStateWithLifecycle()

    val today = state.todayDate()
    val currentBreed = remember(today.month) { BunnyBreed.forMonth(today.month) }
    var showBunnyAlmanac by remember { mutableStateOf(false) }

    val yearFlowers = remember(
        today.year,
        state.items,
        state.steps,
        habitSnapshot.checkInCountByDate,
        state.nowMillis
    ) {
        computeYearBouquetFlowerCount(
            year = today.year,
            items = state.items,
            steps = state.steps,
            habitCheckInCountByDate = habitSnapshot.checkInCountByDate,
            nowMillis = state.nowMillis
        )
    }

    val categoryCounts = remember(state.items) {
        TaskCategory.entries.map { cat ->
            cat to state.items.count { it.taskCategory() == cat }
        }
    }
    val completedItemsTotal = remember(state.items) { state.items.count { it.completed } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isExpanded) 1040.dp else 640.dp),
            contentPadding = PaddingValues(
                start = if (isExpanded) 28.dp else 18.dp,
                end = if (isExpanded) 28.dp else 18.dp,
                top = 12.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "profile_header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MidnightNavy
                    )
                    Text(
                        text = stringResource(R.string.profile_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedNavy
                    )
                }
            }

            item(key = "profile_identity_card") {
                ProfileIdentityCard(
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    userPhotoUrl = userPhotoUrl,
                    isLocalMode = isLocalMode,
                    syncStatus = syncStatus,
                    currentBreed = currentBreed,
                    onOpenAlmanac = {
                        feedback.onPopOpen()
                        showBunnyAlmanac = true
                    },
                    onOpenSettings = {
                        feedback.onPopOpen()
                        onOpenSettings()
                    },
                    onInviteFriendClick = {
                        feedback.onPopOpen()
                        onInviteFriendClick()
                    },
                    onTriggerSync = {
                        feedback.onLightTick()
                        onTriggerSync()
                    },
                    onGoogleSignIn = {
                        feedback.onPopOpen()
                        onGoogleSignIn()
                    },
                    onSignOut = {
                        feedback.onLightTick()
                        onSignOut()
                    }
                )
            }

            if (isExpanded) {
                item(key = "profile_wide_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LiveAppUsageSummaryCard()
                            DedicationQuickCard(
                                dedicationText = state.profile.dedicationText,
                                onEditDedication = {
                                    feedback.onPopOpen()
                                    onEditDedication()
                                }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FocusAndHabitStatsCard(
                                year = today.year,
                                yearFlowers = yearFlowers,
                                completedItemsTotal = completedItemsTotal,
                                streakDays = habitSnapshot.overallStreakDays,
                                bestStreakDays = habitSnapshot.bestStreakDays,
                                completedHabitsToday = habitSnapshot.completedTodayCount,
                                totalHabitsToday = habitSnapshot.totalHabitsCount,
                                weeklyConsistencyPercent = habitSnapshot.weeklyConsistencyPercent,
                                focusMinutesAllTime = focusState.totalFocusMinutesAllTime,
                                focusSessionsToday = focusState.completedSessionsToday,
                                sharedInvitesCount = calendarInvites.size,
                                categoryCounts = categoryCounts
                            )
                        }
                    }
                }
            } else {
                item(key = "profile_app_usage") {
                    LiveAppUsageSummaryCard()
                }
                item(key = "profile_focus_habit_stats") {
                    FocusAndHabitStatsCard(
                        year = today.year,
                        yearFlowers = yearFlowers,
                        completedItemsTotal = completedItemsTotal,
                        streakDays = habitSnapshot.overallStreakDays,
                        bestStreakDays = habitSnapshot.bestStreakDays,
                        completedHabitsToday = habitSnapshot.completedTodayCount,
                        totalHabitsToday = habitSnapshot.totalHabitsCount,
                        weeklyConsistencyPercent = habitSnapshot.weeklyConsistencyPercent,
                        focusMinutesAllTime = focusState.totalFocusMinutesAllTime,
                        focusSessionsToday = focusState.completedSessionsToday,
                        sharedInvitesCount = calendarInvites.size,
                        categoryCounts = categoryCounts
                    )
                }
                item(key = "profile_dedication_card") {
                    DedicationQuickCard(
                        dedicationText = state.profile.dedicationText,
                        onEditDedication = {
                            feedback.onPopOpen()
                            onEditDedication()
                        }
                    )
                }
            }
        }
    }

    if (showBunnyAlmanac) {
        MonthlyBunnyAlmanacModal(
            highlightedMonth = today.month,
            onDismiss = { showBunnyAlmanac = false }
        )
    }
}

@Composable
private fun ProfileIdentityCard(
    userDisplayName: String?,
    userEmail: String?,
    userPhotoUrl: String?,
    isLocalMode: Boolean,
    syncStatus: RoomSyncStatus,
    currentBreed: BunnyBreed,
    onOpenAlmanac: () -> Unit,
    onOpenSettings: () -> Unit,
    onInviteFriendClick: () -> Unit,
    onTriggerSync: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val displayTitle = userDisplayName?.takeIf { it.isNotBlank() }
        ?: userEmail?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.profile_local_guest_name)
    val initials = remember(displayTitle) {
        displayTitle.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "BM" }
    }

    val syncBadge = when (syncStatus.phase) {
        SyncPhase.SYNCED -> Triple("Cloud synced", CalmSageContainer, CalmSage)
        SyncPhase.SYNCING -> Triple("Syncing…", SkyBlue.copy(alpha = 0.7f), DeepBlue)
        SyncPhase.OFFLINE_QUEUED -> Triple("${syncStatus.pendingCount} queued", PollenSoft, MidnightNavy)
        SyncPhase.OFFLINE_IDLE -> Triple("Offline", SoftIceSurface, MutedNavy)
        SyncPhase.ERROR -> Triple("Sync retry", PollenSoft, MidnightNavy)
        SyncPhase.LOCAL_ONLY -> Triple("On this device", SoftIceSurface, MutedNavy)
    }

    ModernPanel(
        shape = RoundedCornerShape(26.dp),
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_identity_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SkyBlue.copy(alpha = 0.36f),
                            PureWhite
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PollenSoft,
                    border = BorderStroke(2.dp, DeepBlue.copy(alpha = 0.65f)),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!userPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userPhotoUrl,
                                contentDescription = displayTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = DeepBlue
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = userEmail ?: stringResource(R.string.profile_local_guest_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModernPillBadge(
                            text = syncBadge.first,
                            containerColor = syncBadge.second,
                            contentColor = syncBadge.third
                        )
                    }
                }

                // Current month's breed companion badge
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PureWhite,
                    border = BorderStroke(1.dp, SubtleBorder),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(width = 76.dp, height = 76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenAlmanac)
                        .testTag("profile_monthly_bunny_badge")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        SmallBunnyIllustration(
                            pose = BunnyPose.GREETING,
                            breed = currentBreed,
                            size = 42.dp,
                            contentDesc = stringResource(
                                R.string.cd_monthly_bunny_badge,
                                currentBreed.monthName,
                                currentBreed.breedName
                            )
                        )
                        Text(
                            text = currentBreed.shortMonthName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = DeepBlue,
                            maxLines = 1
                        )
                    }
                }
            }

            // Action strip: Invite friend + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onInviteFriendClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .heightIn(min = 48.dp)
                        .testTag("profile_invite_friend_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GroupAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.profile_invite_friend_cta),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    border = BorderStroke(1.dp, SubtleBorder),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("profile_open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.profile_open_settings_cta),
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Account & Cloud Sync controls
            if (isLocalMode) {
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("profile_sign_in_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Login,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_sign_in_cta),
                        color = DeepBlue
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onTriggerSync,
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("profile_sync_now_button")
                    ) {
                        Icon(
                            imageVector = when (syncStatus.phase) {
                                SyncPhase.SYNCED -> Icons.Outlined.CloudDone
                                SyncPhase.SYNCING -> Icons.Outlined.CloudSync
                                SyncPhase.OFFLINE_IDLE, SyncPhase.OFFLINE_QUEUED -> Icons.Outlined.CloudOff
                                else -> Icons.Outlined.Sync
                            },
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_sync_now_cta), color = MidnightNavy)
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("profile_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = MutedNavy,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_sign_out_cta), color = MidnightNavy)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusAndHabitStatsCard(
    year: Int,
    yearFlowers: Int,
    completedItemsTotal: Int,
    streakDays: Int,
    bestStreakDays: Int,
    completedHabitsToday: Int,
    totalHabitsToday: Int,
    weeklyConsistencyPercent: Int,
    focusMinutesAllTime: Int,
    focusSessionsToday: Int,
    sharedInvitesCount: Int,
    categoryCounts: List<Pair<TaskCategory, Int>>
) {
    ModernPanel(
        shape = RoundedCornerShape(22.dp),
        elevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_focus_habit_stats_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_habits_focus_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MidnightNavy
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    icon = Icons.Outlined.Whatshot,
                    label = "Habit streak",
                    value = "$streakDays d",
                    subtext = "Best $bestStreakDays d · $weeklyConsistencyPercent% wk",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Outlined.LocalFlorist,
                    label = "$year blooms",
                    value = "$yearFlowers",
                    subtext = "$completedItemsTotal done · $completedHabitsToday/$totalHabitsToday today",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    icon = Icons.Outlined.Timer,
                    label = "Deep focus",
                    value = formatCompactDuration(focusMinutesAllTime),
                    subtext = "$focusSessionsToday Pomodoro today",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Outlined.GroupAdd,
                    label = "Shared dates",
                    value = "$sharedInvitesCount",
                    subtext = if (sharedInvitesCount == 1) "1 friend schedule" else "$sharedInvitesCount friend schedules",
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Memo & task categories",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MutedNavy
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryCounts.forEach { (cat, count) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TaskCategoryBadge(category = cat, compact = true)
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    TonalInsetPanel(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DeepBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MidnightNavy
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MutedNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DedicationQuickCard(
    dedicationText: String,
    onEditDedication: () -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(22.dp),
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_dedication_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Personal Dedication",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                }
                OutlinedButton(
                    onClick = onEditDedication,
                    border = BorderStroke(1.dp, SubtleBorder),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("profile_edit_dedication_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.profile_edit_dedication_cta),
                        style = MaterialTheme.typography.labelMedium,
                        color = MidnightNavy
                    )
                }
            }
            TonalInsetPanel(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "“$dedicationText”",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MidnightNavy,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}
