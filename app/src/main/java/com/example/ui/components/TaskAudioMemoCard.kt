package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.audio.TaskAudioMemoManager
import com.example.data.PlannerItem
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueContainer
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

@Composable
fun TaskAudioMemoCard(
    item: PlannerItem,
    onUpdateAudioMemo: (path: String?, durationSeconds: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember(context) { TaskAudioMemoManager.getInstance(context) }
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val playbackState by audioManager.playbackState.collectAsStateWithLifecycle()

    var permissionDeniedMessage by remember { mutableStateOf(false) }
    var confirmDeleteMemo by remember { mutableStateOf(false) }
    var confirmOverwriteMemo by remember { mutableStateOf(false) }

    val beginRecording = remember(audioManager, item.id, item.title, item.completed, item.audioMemoPath) {
        {
            audioManager.clearError()
            audioManager.startRecording(
                itemId = item.id,
                itemTitle = item.title,
                isTaskCompleted = item.completed,
                previousMemoPath = item.audioMemoPath,
                onSaved = { path, durationSec ->
                    onUpdateAudioMemo(path, durationSec)
                }
            )
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedMessage = false
            beginRecording()
        } else {
            permissionDeniedMessage = true
        }
    }

    val isRecordingThis = playbackState.recordingItemId == item.id
    val isPlayingThis = playbackState.playingItemId == item.id
    val hasSavedMemo = item.hasAudioMemo()

    fun onRecordClicked() {
        permissionDeniedMessage = false
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            beginRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModernPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_audio_memo_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.audio_memo_section_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MidnightNavy
                        )
                        ModernPillBadge(
                            text = stringResource(R.string.saved_on_device_only),
                            containerColor = SoftIceSurface,
                            contentColor = MutedNavy
                        )
                    }
                }

                when {
                    isRecordingThis -> {
                        ModernPillBadge(
                            text = "REC · ${TaskAudioMemoManager.formatSeconds(playbackState.recordingElapsedSeconds)}",
                            containerColor = OverdueContainer,
                            contentColor = OverdueTerracotta
                        )
                    }
                    hasSavedMemo -> {
                        ModernPillBadge(
                            text = TaskAudioMemoManager.formatSeconds(item.audioMemoDurationSeconds),
                            containerColor = if (isPlayingThis) CalmSageContainer else SkyBlue.copy(alpha = 0.65f),
                            contentColor = if (isPlayingThis) CalmSage else DeepBlue
                        )
                    }
                }
            }

            if (isRecordingThis) {
                TonalInsetPanel(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = OverdueTerracotta,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.audio_memo_bunny_listening),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MidnightNavy
                            )
                        }
                        LinearProgressIndicator(
                            progress = {
                                (playbackState.recordingElapsedSeconds.toFloat() /
                                    TaskAudioMemoManager.MAX_DURATION_SEC.toFloat()).coerceIn(0f, 1f)
                            },
                            color = OverdueTerracotta,
                            trackColor = SubtleBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Button(
                            onClick = { audioManager.stopRecording() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OverdueTerracotta,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audio_memo_stop_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${stringResource(R.string.audio_memo_stop_button)} (${TaskAudioMemoManager.formatSeconds(playbackState.recordingElapsedSeconds)} / 1:00)")
                        }
                    }
                }
            } else if (hasSavedMemo) {
                TonalInsetPanel(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = {
                                    audioManager.togglePlayback(
                                        itemId = item.id,
                                        itemTitle = item.title,
                                        isTaskCompleted = item.completed,
                                        filePath = item.audioMemoPath ?: "",
                                        knownDurationSeconds = item.audioMemoDurationSeconds
                                    )
                                },
                                shape = CircleShape,
                                color = if (isPlayingThis) CalmSage else DeepBlue,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("audio_memo_play_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlayingThis) "Stop voice memo" else "Play voice memo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isPlayingThis) {
                                        "Bunny is listening… ${TaskAudioMemoManager.formatSeconds(playbackState.playbackPositionSeconds)} / ${TaskAudioMemoManager.formatSeconds(playbackState.playbackDurationSeconds)}"
                                    } else {
                                        "Voice note saved · ${TaskAudioMemoManager.formatSeconds(item.audioMemoDurationSeconds)}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MidnightNavy
                                )
                                val totalDur = playbackState.playbackDurationSeconds
                                    .takeIf { isPlayingThis && it > 0 }
                                    ?: item.audioMemoDurationSeconds.coerceAtLeast(1)
                                val pos = if (isPlayingThis) playbackState.playbackPositionSeconds else 0
                                LinearProgressIndicator(
                                    progress = { (pos.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f) },
                                    color = if (isPlayingThis) CalmSage else DeepBlue,
                                    trackColor = SubtleBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape)
                                )
                            }

                            IconButton(
                                onClick = {
                                    feedback.onLightTick()
                                    confirmDeleteMemo = true
                                },
                                modifier = Modifier.testTag("audio_memo_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.audio_memo_delete_cd),
                                    tint = MutedNavy
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                feedback.onLightTick()
                                confirmOverwriteMemo = true
                            },
                            border = BorderStroke(1.dp, SubtleBorder),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("audio_memo_rerecord_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.audio_memo_re_record_button),
                                style = MaterialTheme.typography.labelLarge,
                                color = MidnightNavy
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.audio_memo_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
                OutlinedButton(
                    onClick = { onRecordClicked() },
                    border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.4f)),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("audio_memo_record_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.audio_memo_record_button),
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepBlue
                    )
                }
            }

            if (permissionDeniedMessage) {
                Surface(
                    color = OverdueContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.audio_memo_permission_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = OverdueTerracotta,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }
                        ) {
                            Text("Open settings", color = OverdueTerracotta)
                        }
                    }
                }
            } else if (!playbackState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = SoftIceSurface,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = playbackState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }

    if (confirmDeleteMemo) {
        BunnyMemoConfirmModal(
            title = "Delete voice memo?",
            message = "This permanently removes the saved voice memo from this device.",
            confirmLabel = "Delete",
            cancelLabel = "Keep it",
            isDestructive = true,
            onConfirm = {
                confirmDeleteMemo = false
                feedback.onDeleteAction()
                if (isPlayingThis) {
                    audioManager.stopPlayback(emitFeedback = false)
                }
                onUpdateAudioMemo(null, 0)
            },
            onDismiss = { confirmDeleteMemo = false }
        )
    }

    if (confirmOverwriteMemo) {
        BunnyMemoConfirmModal(
            title = "Replace voice memo?",
            message = "Recording a new voice note will replace the current saved memo once finished.",
            confirmLabel = "Record new",
            cancelLabel = "Keep current",
            isDestructive = false,
            onConfirm = {
                confirmOverwriteMemo = false
                onRecordClicked()
            },
            onDismiss = { confirmOverwriteMemo = false }
        )
    }
}
