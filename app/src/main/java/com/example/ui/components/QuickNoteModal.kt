package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CalendarDate
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

private enum class QuickNoteTarget {
    IDEA,
    TODAY
}

/**
 * Simple, fast forget-me-not text entry modal for capturing spontaneous thoughts or ideas.
 */
@Composable
fun QuickNoteModal(
    todayDate: CalendarDate,
    initialText: String = "",
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveNote: (rawText: String, dueDateIso: String?) -> Unit
) {
    var noteText by remember(initialText) { mutableStateOf(initialText) }
    var target by remember { mutableStateOf(QuickNoteTarget.IDEA) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BunnyMemoModalContainer(
        title = stringResource(R.string.quick_note_modal_title),
        subtitle = stringResource(R.string.quick_note_modal_subtitle),
        onDismiss = onDismiss,
        maxWidth = 440.dp,
        headerLeading = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PollenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = DeepBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) { requestDismiss ->
        OutlinedTextField(
            value = noteText,
            onValueChange = { updated ->
                if (updated.length <= 1000) noteText = updated
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.quick_note_input_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedNavy
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MidnightNavy),
            minLines = 4,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SoftIceSurface.copy(alpha = 0.65f),
                unfocusedContainerColor = SoftIceSurface.copy(alpha = 0.45f),
                focusedBorderColor = DeepBlue,
                unfocusedBorderColor = SubtleBorder,
                cursorColor = DeepBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .focusRequester(focusRequester)
                .testTag("quick_note_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "First line becomes the title; extra lines save as details.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedNavy,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${noteText.length}/1000",
                style = MaterialTheme.typography.labelSmall,
                color = MutedNavy
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        ModernSegmentedToggle(
            options = listOf(
                QuickNoteTarget.IDEA to stringResource(R.string.quick_note_mode_idea),
                QuickNoteTarget.TODAY to stringResource(R.string.quick_note_mode_today)
            ),
            selected = target,
            onSelect = { target = it },
            tagPrefix = "quick_note_target",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = requestDismiss,
                enabled = !isSaving,
                border = BorderStroke(1.dp, SubtleBorder),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("quick_note_cancel_button")
            ) {
                Text("Cancel", color = MidnightNavy)
            }
            Button(
                onClick = {
                    val trimmed = noteText.trim()
                    if (trimmed.isNotEmpty()) {
                        val dueIso = if (target == QuickNoteTarget.TODAY) {
                            todayDate.toIsoString()
                        } else {
                            null
                        }
                        onSaveNote(trimmed, dueIso)
                    }
                },
                enabled = noteText.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .heightIn(min = 48.dp)
                    .testTag("quick_note_save_button")
            ) {
                Text(
                    text = if (isSaving) {
                        stringResource(R.string.quick_note_saving)
                    } else {
                        stringResource(R.string.quick_note_save_button)
                    }
                )
            }
        }
    }
}
