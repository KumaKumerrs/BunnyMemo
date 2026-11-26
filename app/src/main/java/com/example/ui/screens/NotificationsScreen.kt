package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.CalendarDate
import com.example.data.CalendarInvite
import com.example.data.InboxNotificationEntry
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerReminder
import com.example.data.ReminderKind
import com.example.notifications.DailyDigestSummary
import com.example.notifications.DailyReminderConfig
import com.example.ui.components.BunnyMemoTimePickerModal
import com.example.ui.components.BunnyPose
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.SharedScheduleCard
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.TonalInsetPanel
import com.example.ui.components.adaptiveHorizontalPadding
import com.example.ui.components.ruledMemoLines
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState

@Composable
fun NotificationsScreen(
    state: PlannerUiState.Ready,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleDailyDigest: (Boolean) -> Unit,
    onUpdateDailyDigestTime: (Int, Int) -> Unit,
    onSendDailyDigestPreview: () -> Unit,
    onOpenItem: (String) -> Unit,
    onRescheduleItem: (PlannerItem) -> Unit,
    onSnoozeReminder: (PlannerReminder, Int) -> Unit,
    onDismissReminder: (PlannerReminder) -> Unit,
    calendarInvites: List<CalendarInvite> = emptyList(),
    currentUserId: String? = null,
    onOpenSharedInvite: (CalendarInvite) -> Unit = {},
    onInviteFriendClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val today = state.todayDate()
    val entries = state.inboxEntries
    val groupedByDate = remember(entries) {
        entries.groupBy { CalendarDate.fromUtcMillis(it.triggerUtcMillis) }
    }
    var showDailyTimeModal by remember { mutableStateOf(false) }

    val onPreviewAction: () -> Unit = {
        if (!hasNotificationPermission) {
            onRequestNotificationPermission()
        } else {
            onSendDailyDigestPreview()
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPad = adaptiveHorizontalPadding(maxWidth)
        val useSideBySide = maxWidth >= 780.dp

        if (useSideBySide) {
            // Wide Tablet Side-by-Side Layout: Daily Scheduler (Left) + Dated Reminder Inbox (Right)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InboxEditorialHeader()
                    DailyForgetMeNotCard(
                        config = state.dailyReminderConfig,
                        summary = state.dailyDigestSummary,
                        hasNotificationPermission = hasNotificationPermission,
                        onToggleEnabled = onToggleDailyDigest,
                        onChangeTimeClick = { showDailyTimeModal = true },
                        onSendPreviewClick = onPreviewAction
                    )
                    if (!hasNotificationPermission) {
                        NotificationsPermissionBanner(onRequestPermission = onRequestNotificationPermission)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .testTag("notifications_screen_list"),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "inbox_shared_invites_header_wide") {
                        SharedInvitesInboxHeader(
                            count = calendarInvites.size,
                            onInviteFriend = {
                                feedback.onPopOpen()
                                onInviteFriendClick()
                            }
                        )
                    }
                    if (calendarInvites.isNotEmpty()) {
                        items(calendarInvites, key = { "inbox_invite_${it.id}" }) { invite ->
                            SharedScheduleCard(
                                invite = invite,
                                currentUserId = currentUserId,
                                onProfileIconClick = {
                                    feedback.onPopOpen()
                                    onOpenSharedInvite(invite)
                                },
                                onCardClick = {
                                    feedback.onPopOpen()
                                    onOpenSharedInvite(invite)
                                }
                            )
                        }
                    }

                    if (entries.isEmpty() && calendarInvites.isEmpty()) {
                        item(key = "inbox_empty") {
                            EmptyInboxPanel()
                        }
                    } else {
                        groupedByDate.forEach { (date, dayEntries) ->
                            item(key = "date_hdr_${date.toIsoString()}") {
                                Text(
                                    text = "${PlannerDateUtils.formatRelativeDateLabel(date, today)} · ${PlannerDateUtils.formatCompactDate(date)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MutedNavy,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                            items(dayEntries, key = { "inbox_${it.reminder.id}" }) { entry ->
                                InboxEntryCard(
                                    entry = entry,
                                    nowMillis = state.nowMillis,
                                    onOpen = { onOpenItem(entry.item.id) },
                                    onReschedule = {
                                        feedback.onPopOpen()
                                        onRescheduleItem(entry.item)
                                    },
                                    onSnooze = { mins ->
                                        feedback.onReminderSnoozed()
                                        onSnoozeReminder(entry.reminder, mins)
                                    },
                                    onDismiss = {
                                        feedback.onReminderDismissed()
                                        onDismissReminder(entry.reminder)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("notifications_screen_list"),
                contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 16.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "inbox_header") {
                    InboxEditorialHeader()
                }

                item(key = "daily_forget_me_not_card") {
                    DailyForgetMeNotCard(
                        config = state.dailyReminderConfig,
                        summary = state.dailyDigestSummary,
                        hasNotificationPermission = hasNotificationPermission,
                        onToggleEnabled = onToggleDailyDigest,
                        onChangeTimeClick = {
                            feedback.onPopOpen()
                            showDailyTimeModal = true
                        },
                        onSendPreviewClick = onPreviewAction
                    )
                }

                if (!hasNotificationPermission) {
                    item(key = "permission_banner") {
                        NotificationsPermissionBanner(onRequestPermission = onRequestNotificationPermission)
                    }
                }

                item(key = "inbox_shared_invites_header_compact") {
                    SharedInvitesInboxHeader(
                        count = calendarInvites.size,
                        onInviteFriend = {
                            feedback.onPopOpen()
                            onInviteFriendClick()
                        }
                    )
                }
                if (calendarInvites.isNotEmpty()) {
                    items(calendarInvites, key = { "inbox_invite_${it.id}" }) { invite ->
                        SharedScheduleCard(
                            invite = invite,
                            currentUserId = currentUserId,
                            onProfileIconClick = {
                                feedback.onPopOpen()
                                onOpenSharedInvite(invite)
                            },
                            onCardClick = {
                                feedback.onPopOpen()
                                onOpenSharedInvite(invite)
                            }
                        )
                    }
                }

                if (entries.isEmpty() && calendarInvites.isEmpty()) {
                    item(key = "inbox_empty") {
                        EmptyInboxPanel()
                    }
                } else {
                    groupedByDate.forEach { (date, dayEntries) ->
                        item(key = "date_hdr_${date.toIsoString()}") {
                            Text(
                                text = "${PlannerDateUtils.formatRelativeDateLabel(date, today)} · ${PlannerDateUtils.formatCompactDate(date)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MutedNavy,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                        items(dayEntries, key = { "inbox_${it.reminder.id}" }) { entry ->
                            InboxEntryCard(
                                entry = entry,
                                nowMillis = state.nowMillis,
                                onOpen = { onOpenItem(entry.item.id) },
                                onReschedule = {
                                    feedback.onPopOpen()
                                    onRescheduleItem(entry.item)
                                },
                                onSnooze = { mins ->
                                    feedback.onReminderSnoozed()
                                    onSnoozeReminder(entry.reminder, mins)
                                },
                                onDismiss = {
                                    feedback.onReminderDismissed()
                                    onDismissReminder(entry.reminder)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDailyTimeModal) {
        BunnyMemoTimePickerModal(
            title = "Daily forget-me-not time",
            subtitle = "Choose when your gentle daily note arrives",
            initialHour = state.dailyReminderConfig.hour,
            initialMinute = state.dailyReminderConfig.minute,
            onTimeSelected = { h, m ->
                onUpdateDailyDigestTime(h, m)
                showDailyTimeModal = false
            },
            onDismiss = { showDailyTimeModal = false }
        )
    }
}

@Composable
private fun InboxEditorialHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "INBOX",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                color = MutedNavy
            )
            Text(
                text = "Reminders & Check-ins",
                style = MaterialTheme.typography.headlineMedium,
                color = MidnightNavy
            )
            Text(
                text = "Check-ins quietly skip once all steps are done.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedNavy
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            color = PureWhite,
            shape = CircleShape,
            shadowElevation = 2.dp,
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                SmallBunnyIllustration(
                    pose = BunnyPose.ENVELOPE,
                    size = 48.dp,
                    contentDesc = stringResource(R.string.cd_bunny_envelope)
                )
            }
        }
    }
}

@Composable
private fun NotificationsPermissionBanner(onRequestPermission: () -> Unit) {
    TonalInsetPanel(
        shape = RoundedCornerShape(20.dp),
        containerColor = SoftIceSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notifications_permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
                Text(
                    text = stringResource(R.string.notifications_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("inbox_allow_notifications_button")
            ) {
                Text(
                    text = stringResource(R.string.notifications_permission_allow),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyInboxPanel() {
    ModernPanel(
        shape = RoundedCornerShape(24.dp),
        elevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmallBunnyIllustration(
                pose = BunnyPose.ENVELOPE,
                size = 92.dp,
                contentDesc = stringResource(R.string.cd_bunny_envelope)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.notifications_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MidnightNavy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add a reminder or progress check-in to any plan, task, or event.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedNavy
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyForgetMeNotCard(
    config: DailyReminderConfig,
    summary: DailyDigestSummary,
    hasNotificationPermission: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onChangeTimeClick: () -> Unit,
    onSendPreviewClick: () -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(24.dp),
        elevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_forget_me_not_card")
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = SoftIceSurface,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ForgetMeNotFlower(
                                bloomed = config.enabled,
                                size = 24.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.daily_reminder_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy
                        )
                        Text(
                            text = if (config.enabled) {
                                stringResource(
                                    R.string.daily_reminder_time_label,
                                    PlannerDateUtils.formatTimeOfDay(config.hour * 60 + config.minute)
                                )
                            } else {
                                stringResource(R.string.daily_reminder_paused)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    }
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = DeepBlue,
                        uncheckedTrackColor = SoftIceSurface,
                        uncheckedBorderColor = SubtleBorder
                    ),
                    modifier = Modifier.testTag("daily_digest_toggle")
                )
            }

            AnimatedVisibility(
                visible = config.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Borderless tonal inset memo preview
                    TonalInsetPanel(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = SoftIceSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .ruledMemoLines(lineSpacing = 22.dp)
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = summary.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MidnightNavy
                            )
                            Text(
                                text = summary.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onChangeTimeClick,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftIceSurface,
                                contentColor = MidnightNavy
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("daily_digest_time_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.daily_reminder_change_time),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Button(
                            onClick = onSendPreviewClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepBlue,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("daily_digest_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasNotificationPermission) {
                                    stringResource(R.string.daily_reminder_preview_button)
                                } else {
                                    stringResource(R.string.notifications_permission_allow)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxEntryCard(
    entry: InboxNotificationEntry,
    nowMillis: Long,
    onOpen: () -> Unit,
    onReschedule: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isDueNow = entry.triggerUtcMillis <= nowMillis
    val isCheckIn = entry.reminder.reminderKind() == ReminderKind.PROGRESS_CHECKIN

    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        borderColor = if (isDueNow) DeepBlue.copy(alpha = 0.5f) else SubtleBorder.copy(alpha = 0.85f),
        borderWidth = if (isDueNow) 1.2.dp else 0.75.dp,
        elevation = 3.dp,
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inbox_card_${entry.reminder.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallBunnyIllustration(
                    pose = BunnyPose.ENVELOPE,
                    size = 42.dp,
                    contentDesc = null
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModernPillBadge(
                            text = if (isCheckIn) "Progress check-in" else "Reminder",
                            containerColor = if (isCheckIn) PollenSoft else SoftIceSurface,
                            contentColor = MidnightNavy
                        )
                        if (entry.isSnoozed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Snooze,
                                    contentDescription = null,
                                    tint = MutedNavy,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Snoozed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedNavy
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MidnightNavy
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = PlannerDateUtils.formatUtcMillisDateTime(entry.triggerUtcMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSnooze(60) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftIceSurface,
                        contentColor = MidnightNavy
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.testTag("inbox_snooze_1h_${entry.reminder.id}")
                ) {
                    Text("Snooze 1h", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = { onSnooze(1440) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftIceSurface,
                        contentColor = MidnightNavy
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Tomorrow", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onReschedule,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftIceSurface,
                        contentColor = MidnightNavy
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Reschedule", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("inbox_dismiss_${entry.reminder.id}")
                ) {
                    Text("Dismiss", color = MutedNavy)
                }
            }
        }
    }
}

@Composable
private fun SharedInvitesInboxHeader(
    count: Int,
    onInviteFriend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count > 0) "Shared calendar invites ($count)" else "Shared calendar invites",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MidnightNavy
            )
            Text(
                text = "Tap a friend's profile icon to view or reply on planned dates",
                style = MaterialTheme.typography.labelSmall,
                color = MutedNavy
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onInviteFriend,
            border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(100.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("inbox_invite_friend_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.GroupAdd,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Invite", style = MaterialTheme.typography.labelMedium, color = DeepBlue)
        }
    }
}
