package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.BunnyPose
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ForgetMeNotStationeryBackground
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.ruledMemoLines
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SubtleBorder

/**
 * First-minute editable dedication screen from Page 1 of the brief.
 * Shown on first visit; later visits open Today directly.
 */
@Composable
fun DedicationScreen(
    initialDedication: String,
    isSaving: Boolean,
    onOpenPlanner: (editedText: String) -> Unit
) {
    var dedicationText by remember(initialDedication) { mutableStateOf(initialDedication) }
    var isEditing by remember { mutableStateOf(false) }

    ForgetMeNotStationeryBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
        com.example.ui.components.ModernPanel(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(28.dp),
            elevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ForgetMeNotFlower(bloomed = true, size = 26.dp)
                    SmallBunnyIllustration(
                        pose = BunnyPose.GREETING,
                        size = 80.dp,
                        contentDesc = stringResource(R.string.cd_bunny_greeting)
                    )
                    ForgetMeNotFlower(bloomed = true, size = 26.dp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "PERSONAL GIFT EDITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditing) {
                    OutlinedTextField(
                        value = dedicationText,
                        onValueChange = { dedicationText = it.take(500) },
                        label = { Text(stringResource(R.string.dedication_edit_hint)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dedication_text_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done editing")
                    }
                } else {
                    com.example.ui.components.TonalInsetPanel(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = dedicationText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            color = MidnightNavy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .ruledMemoLines(lineSpacing = 28.dp)
                                .padding(horizontal = 22.dp, vertical = 24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.testTag("dedication_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit dedication")
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = { onOpenPlanner(dedicationText) },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_my_planner_button")
                ) {
                    Text(
                        text = stringResource(R.string.dedication_open_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        }
    }
}
