package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.MarkEmailUnread
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CalendarDate
import com.example.data.CalendarInvite
import com.example.data.CalendarScheduleReply
import com.example.data.InviteRsvpStatus
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.TaskCategory
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueContainer
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

/**
 * Clickable friend profile avatar shown next to shared calendar schedules so the user can
 * immediately tell whose schedule it is and tap to inspect details / reply on planned dates.
 */
@Composable
fun FriendProfileIconButton(
    name: String,
    email: String,
    photoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tagSuffix: String = "friend"
) {
    val initials = remember(name, email) {
        val src = name.ifBlank { email }.trim()
        val parts = src.split(" ", ".", "@").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            src.isNotEmpty() -> src.take(2).uppercase()
            else -> "?"
        }
    }
    val cd = stringResource(R.string.cd_friend_profile_icon, name.ifBlank { email })

    Surface(
        shape = CircleShape,
        color = PollenSoft,
        border = BorderStroke(1.5.dp, DeepBlue.copy(alpha = 0.65f)),
        shadowElevation = 2.dp,
        modifier = modifier
            .size(maxOf(size, 48.dp))
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
            .testTag("friend_profile_icon_$tagSuffix")
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepBlue
                )
            }
        }
    }
}

@Composable
fun rsvpStatusColors(status: InviteRsvpStatus): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (status) {
        InviteRsvpStatus.GOING -> CalmSageContainer to CalmSage
        InviteRsvpStatus.MAYBE -> PollenSoft to MidnightNavy
        InviteRsvpStatus.DECLINED -> OverdueContainer to OverdueTerracotta
        InviteRsvpStatus.PENDING -> SkyBlue.copy(alpha = 0.65f) to DeepBlue
    }
}

/**
 * Shared schedule card placed on Today / Calendar views with a prominent clickable friend profile
 * icon next to the title so the user never confuses shared dates with personal memos.
 */
@Composable
fun SharedScheduleCard(
    invite: CalendarInvite,
    currentUserId: String?,
    onProfileIconClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMine = invite.ownerUid == currentUserId
    val badgeName = if (isMine) "To ${invite.inviteeEmail}" else "From ${invite.ownerName}"
    val (rsvpBg, rsvpFg) = rsvpStatusColors(invite.rsvpStatusEnum())
    val category = invite.categoryEnum()
    val parsedDate = remember(invite.plannedDate) { CalendarDate.parse(invite.plannedDate) }

    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        containerColor = SoftIceSurface,
        borderColor = DeepBlue.copy(alpha = 0.45f),
        borderWidth = 1.dp,
        elevation = 2.dp,
        onClick = onCardClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("shared_schedule_card_${invite.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tappable profile icon right next to their schedule to avoid confusion
            FriendProfileIconButton(
                name = if (isMine) invite.inviteeEmail else invite.ownerName,
                email = if (isMine) invite.inviteeEmail else invite.ownerEmail,
                photoUrl = if (isMine) null else invite.ownerPhotoUrl,
                onClick = onProfileIconClick,
                size = 40.dp,
                tagSuffix = invite.id
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = invite.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ModernPillBadge(
                        text = invite.rsvpStatusEnum().label,
                        containerColor = rsvpBg,
                        contentColor = rsvpFg
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernPillBadge(
                        text = badgeName,
                        containerColor = PureWhite,
                        contentColor = DeepBlue
                    )
                    TaskCategoryBadge(category = category, compact = true)
                }

                Text(
                    text = buildString {
                        parsedDate?.let { append(PlannerDateUtils.formatCompactDate(it)) } ?: append(invite.plannedDate)
                        if (!invite.scheduleTimeLabel.isNullOrBlank()) {
                            append(" · ")
                            append(invite.scheduleTimeLabel)
                        }
                        if (invite.replyCount > 0) {
                            append(" · ${invite.replyCount} ")
                            append(if (invite.replyCount == 1) "reply" else "replies")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!invite.lastReplyText.isNullOrBlank()) {
                    Text(
                        text = "${invite.lastReplyAuthorName ?: "Reply"}: “${invite.lastReplyText}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Dialog for inviting a friend to a scheduled date via email + Firestore sync.
 */
@Composable
fun InviteFriendToCalendarDialog(
    initialTitle: String,
    initialDateIso: String,
    initialTimeLabel: String?,
    initialNotes: String?,
    initialItemType: ItemType,
    initialCategory: TaskCategory,
    isSignedIn: Boolean,
    onDismiss: () -> Unit,
    onSendInvite: (
        email: String,
        title: String,
        plannedDate: String,
        timeLabel: String?,
        notes: String?,
        sendViaEmailApp: Boolean
    ) -> Unit
) {
    var friendEmail by rememberSaveable { mutableStateOf("") }
    var scheduleTitle by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var plannedDateIso by rememberSaveable(initialDateIso) { mutableStateOf(initialDateIso) }
    var timeLabel by rememberSaveable(initialTimeLabel) { mutableStateOf(initialTimeLabel.orEmpty()) }
    var noteText by rememberSaveable(initialNotes) { mutableStateOf(initialNotes.orEmpty()) }
    var openEmailApp by rememberSaveable { mutableStateOf(true) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val parsedDate = CalendarDate.parse(plannedDateIso) ?: CalendarDate.today()
    val emailValid = friendEmail.trim().contains("@") && friendEmail.trim().contains(".")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ModernPanel(
            shape = RoundedCornerShape(28.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .testTag("invite_friend_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SkyBlue.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GroupAdd,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.invite_friend_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy
                            )
                            Text(
                                text = stringResource(R.string.invite_friend_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_button),
                            tint = MutedNavy
                        )
                    }
                }

                if (!isSignedIn) {
                    TonalInsetPanel(
                        shape = RoundedCornerShape(16.dp),
                        containerColor = OverdueContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.invite_sign_in_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = OverdueTerracotta,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = friendEmail,
                    onValueChange = { friendEmail = it },
                    label = { Text(stringResource(R.string.invite_email_label)) },
                    placeholder = { Text("friend@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = {
                        Icon(Icons.Outlined.Email, contentDescription = null, tint = DeepBlue)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_friend_email_input")
                )

                OutlinedTextField(
                    value = scheduleTitle,
                    onValueChange = { scheduleTitle = it },
                    label = { Text(stringResource(R.string.invite_schedule_title_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_friend_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("invite_friend_date_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = PlannerDateUtils.formatCompactDate(parsedDate),
                            color = MidnightNavy,
                            maxLines = 1
                        )
                    }

                    OutlinedTextField(
                        value = timeLabel,
                        onValueChange = { timeLabel = it },
                        label = { Text("Time (optional)") },
                        placeholder = { Text("3:00 PM") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Message or location note") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                FilterChip(
                    selected = openEmailApp,
                    onClick = { openEmailApp = !openEmailApp },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailUnread,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Also open email app to send invite") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SkyBlue.copy(alpha = 0.7f),
                        selectedLabelColor = MidnightNavy
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = MidnightNavy)
                    }
                    Button(
                        onClick = {
                            onSendInvite(
                                friendEmail.trim(),
                                scheduleTitle.trim(),
                                plannedDateIso,
                                timeLabel.trim().takeIf { it.isNotEmpty() },
                                noteText.trim().takeIf { it.isNotEmpty() },
                                openEmailApp
                            )
                        },
                        enabled = isSignedIn && emailValid && scheduleTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepBlue,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("invite_friend_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.invite_send_button))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        BunnyMemoDatePickerModal(
            title = "Select shared date",
            initialDate = parsedDate,
            onDateSelected = { chosen ->
                plannedDateIso = chosen.toIsoString()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

/**
 * Friend Schedule & Reply detail dialog triggered when tapping the friend profile icon or card
 * on Calendar, Today, or Inbox notifications.
 */
@Composable
fun FriendScheduleDetailModal(
    invite: CalendarInvite,
    replies: List<CalendarScheduleReply>,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onSendReply: (InviteRsvpStatus, String) -> Unit,
    onResendEmail: () -> Unit,
    onDeleteInvite: () -> Unit
) {
    val isOwner = invite.ownerUid == currentUserId
    var selectedStatus by rememberSaveable(invite.id) {
        mutableStateOf(
            if (invite.rsvpStatusEnum() == InviteRsvpStatus.PENDING) InviteRsvpStatus.GOING
            else invite.rsvpStatusEnum()
        )
    }
    var replyText by rememberSaveable(invite.id) { mutableStateOf("") }
    val plannedDate = CalendarDate.parse(invite.plannedDate)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ModernPanel(
            shape = RoundedCornerShape(28.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .testTag("friend_schedule_detail_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header showing sender's profile info prominently so ownership is crystal clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FriendProfileIconButton(
                        name = invite.ownerName,
                        email = invite.ownerEmail,
                        photoUrl = invite.ownerPhotoUrl,
                        onClick = {},
                        size = 46.dp,
                        tagSuffix = "modal_${invite.id}"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOwner) "Shared by you with ${invite.inviteeEmail}" else "${invite.ownerName}’s schedule",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MidnightNavy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isOwner) invite.ownerEmail else "${invite.ownerEmail} · To ${invite.inviteeEmail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_button), tint = MutedNavy)
                    }
                }

                TonalInsetPanel(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = invite.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy,
                                modifier = Modifier.weight(1f)
                            )
                            TaskCategoryBadge(category = invite.categoryEnum(), compact = true)
                        }
                        Text(
                            text = buildString {
                                append(plannedDate?.let { PlannerDateUtils.formatFullDateHeader(it) } ?: invite.plannedDate)
                                invite.scheduleTimeLabel?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = DeepBlue
                        )
                        if (!invite.notes.isNullOrBlank()) {
                            Text(
                                text = invite.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MidnightNavy
                            )
                        }
                    }
                }

                // Replies timeline on this planned date
                Text(
                    text = stringResource(R.string.invite_replies_section_title, replies.size),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )

                if (replies.isEmpty()) {
                    Text(
                        text = stringResource(R.string.invite_no_replies_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        replies.forEach { reply ->
                            val (bg, fg) = rsvpStatusColors(reply.rsvpStatusEnum())
                            TonalInsetPanel(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("invite_reply_${reply.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    FriendProfileIconButton(
                                        name = reply.authorName,
                                        email = reply.authorEmail,
                                        photoUrl = reply.authorPhotoUrl,
                                        onClick = {},
                                        size = 32.dp,
                                        tagSuffix = "reply_${reply.id}"
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = reply.authorName,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MidnightNavy
                                            )
                                            ModernPillBadge(
                                                text = reply.rsvpStatusEnum().label,
                                                containerColor = bg,
                                                contentColor = fg
                                            )
                                        }
                                        Text(
                                            text = reply.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MidnightNavy
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = SubtleBorder.copy(alpha = 0.7f))

                // RSVP + Reply composer
                Text(
                    text = stringResource(R.string.invite_reply_composer_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        InviteRsvpStatus.GOING,
                        InviteRsvpStatus.MAYBE,
                        InviteRsvpStatus.DECLINED
                    ).forEach { st ->
                        val selected = selectedStatus == st
                        val (bg, fg) = rsvpStatusColors(st)
                        FilterChip(
                            selected = selected,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = bg,
                                selectedLabelColor = fg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("rsvp_chip_${st.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text(stringResource(R.string.invite_reply_placeholder)) },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_reply_input")
                )

                Button(
                    onClick = {
                        onSendReply(selectedStatus, replyText)
                        replyText = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("invite_send_reply_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.invite_send_reply_button))
                }

                if (isOwner) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResendEmail,
                            border = BorderStroke(1.dp, SubtleBorder),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Email invite", color = MidnightNavy)
                        }
                        OutlinedButton(
                            onClick = onDeleteInvite,
                            border = BorderStroke(1.dp, OverdueTerracotta.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("invite_delete_button")
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = OverdueTerracotta, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", color = OverdueTerracotta)
                        }
                    }
                }
            }
        }
    }
}
