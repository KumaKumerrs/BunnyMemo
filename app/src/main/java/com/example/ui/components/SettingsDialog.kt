package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlannerDateUtils
import com.example.data.UserProfile
import com.example.notifications.DailyReminderConfig
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SubtleBorder

@Composable
fun SettingsDialog(
    profile: UserProfile,
    dailyReminderConfig: DailyReminderConfig,
    isLocalMode: Boolean,
    userEmail: String?,
    hasNotificationPermission: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveDedication: (String) -> Unit,
    onUpdateDefaultReminderTime: (Int, Int) -> Unit,
    onToggleDailyDigest: (Boolean) -> Unit,
    onUpdateDailyDigestTime: (Int, Int) -> Unit,
    onAddSamplePlan: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit
) {
    var dedicationDraft by remember(profile.dedicationText) { mutableStateOf(profile.dedicationText) }
    var activeTimePicker by remember { mutableStateOf<String?>(null) } // "default" or "daily"

    BunnyMemoModalContainer(
        title = "Planner settings",
        subtitle = "Personalize your Bunny Memo experience",
        onDismiss = onDismiss,
        maxWidth = 480.dp
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Editable Dedication Note
            TonalInsetPanel(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dedication note",
                            style = MaterialTheme.typography.titleSmall,
                            color = MidnightNavy
                        )
                    }
                    OutlinedTextField(
                        value = dedicationDraft,
                        onValueChange = { dedicationDraft = it.take(500) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSaveDedication(dedicationDraft) },
                        enabled = !isSaving && dedicationDraft.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepBlue,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save note")
                    }
                }
            }

            // Gentle Daily Forget-Me-Not Digest schedule
            TonalInsetPanel(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ForgetMeNotFlower(
                                bloomed = dailyReminderConfig.enabled,
                                size = 22.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.daily_reminder_card_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MidnightNavy
                                )
                                Text(
                                    text = if (dailyReminderConfig.enabled) {
                                        stringResource(
                                            R.string.daily_reminder_time_label,
                                            PlannerDateUtils.formatTimeOfDay(
                                                dailyReminderConfig.hour * 60 + dailyReminderConfig.minute
                                            )
                                        )
                                    } else {
                                        stringResource(R.string.daily_reminder_paused)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = dailyReminderConfig.enabled,
                            onCheckedChange = onToggleDailyDigest,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = DeepBlue,
                                uncheckedBorderColor = SubtleBorder
                            ),
                            modifier = Modifier.testTag("settings_daily_digest_switch")
                        )
                    }
                    if (dailyReminderConfig.enabled) {
                        TextButton(
                            onClick = { activeTimePicker = "daily" },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("settings_daily_digest_time_button")
                        ) {
                            Text(stringResource(R.string.daily_reminder_change_time))
                        }
                    }
                }
            }

            // Default reminder time for date-only items
            TonalInsetPanel(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Default item reminder time",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = PlannerDateUtils.formatTimeOfDay(
                                    profile.defaultReminderHour * 60 + profile.defaultReminderMinute
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { activeTimePicker = "default" }) {
                        Text("Change")
                    }
                }
            }

            // Notifications status
            if (!hasNotificationPermission) {
                TonalInsetPanel(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Device notifications",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "In-app inbox is active; allow alerts for closed-app reminders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = onRequestNotificationPermission) {
                            Text("Enable")
                        }
                    }
                }
            }

            // Optional Page 5 walkthrough sample plan
            TonalInsetPanel(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Optional walkthrough sample",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "Adds “Make a weekend scrapbook” with 3 milestones and a progress check-in so you can try blooming progress right away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            onAddSamplePlan()
                            requestDismiss()
                        },
                        enabled = !isSaving,
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.testTag("settings_add_sample_button")
                    ) {
                        Text("Add sample plan", color = MidnightNavy)
                    }
                }
            }

            HorizontalDivider(color = SubtleBorder)

            // Account & Sync Section (Local Mode vs Private Cloud Sync)
            if (isLocalMode) {
                TonalInsetPanel(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.local_mode_backup_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MidnightNavy
                            )
                        }
                        Text(
                            text = stringResource(R.string.local_mode_backup_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                requestDismiss()
                                onSignInWithGoogle()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepBlue,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_sign_in_google_button")
                        ) {
                            Text(stringResource(R.string.local_mode_sign_in_cta))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Private cloud sync",
                            style = MaterialTheme.typography.labelMedium,
                            color = MidnightNavy
                        )
                        Text(
                            text = userEmail ?: "Signed in with Google",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            requestDismiss()
                            onSignOut()
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = OverdueTerracotta,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign out", color = OverdueTerracotta)
                    }
                }
            }
        }
    }

    if (activeTimePicker != null) {
        val isDaily = activeTimePicker == "daily"
        BunnyMemoTimePickerModal(
            title = if (isDaily) "Daily forget-me-not time" else "Default reminder time",
            subtitle = if (isDaily) {
                "When your gentle daily task & milestone note arrives"
            } else {
                "Default time for date-only tasks and milestones"
            },
            initialHour = if (isDaily) dailyReminderConfig.hour else profile.defaultReminderHour,
            initialMinute = if (isDaily) dailyReminderConfig.minute else profile.defaultReminderMinute,
            onTimeSelected = { h, m ->
                if (isDaily) {
                    onUpdateDailyDigestTime(h, m)
                } else {
                    onUpdateDefaultReminderTime(h, m)
                }
                activeTimePicker = null
            },
            onDismiss = { activeTimePicker = null }
        )
    }
}
