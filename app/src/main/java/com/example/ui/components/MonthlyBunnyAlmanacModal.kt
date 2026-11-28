package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.CalendarDate
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

@Composable
fun MonthlyBunnyAlmanacModal(
    highlightedMonth: Int,
    onDismiss: () -> Unit,
    onSelectMonth: ((Int) -> Unit)? = null
) {
    val currentCalendarMonth = CalendarDate.today().month
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = PureWhite,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .testTag("monthly_bunny_almanac_modal")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bunny_almanac_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MidnightNavy
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.bunny_almanac_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_bunny_almanac_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close_button),
                            tint = MidnightNavy
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    items(BunnyBreed.entries, key = { it.month }) { breed ->
                        val isHighlighted = breed.month == highlightedMonth
                        val isCurrentMonth = breed.month == currentCalendarMonth
                        val cardDesc = stringResource(
                            R.string.cd_bunny_almanac_card,
                            breed.monthName,
                            breed.breedName,
                            breed.coatDescription
                        )
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isHighlighted) SoftIceSurface else PureWhite,
                            border = BorderStroke(
                                width = if (isHighlighted) 1.5.dp else 1.dp,
                                color = if (isHighlighted) ForgetMeNotBlue else SubtleBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .then(
                                    if (onSelectMonth != null) {
                                        Modifier.clickable {
                                            onSelectMonth(breed.month)
                                            onDismiss()
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .clearAndSetSemantics { contentDescription = cardDesc }
                                .testTag("almanac_breed_month_${breed.month}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = breed.shortMonthName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isHighlighted) DeepBlue else MutedNavy
                                    )
                                    if (isCurrentMonth) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(PollenSoft)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.bunny_almanac_current_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MidnightNavy
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = SkyBlue.copy(alpha = 0.28f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SmallBunnyIllustration(
                                            pose = BunnyPose.GREETING,
                                            breed = breed,
                                            size = 56.dp
                                        )
                                    }
                                }

                                Text(
                                    text = breed.breedName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MidnightNavy,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = breed.traitTagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepBlue,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = breed.coatDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedNavy,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("almanac_done_button")
                ) {
                    Text(stringResource(R.string.bunny_almanac_close))
                }
            }
        }
    }
}
