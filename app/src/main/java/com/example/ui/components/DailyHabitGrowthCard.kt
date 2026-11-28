package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.BunnyGrowthStage
import com.example.data.ClassColorKey
import com.example.data.DailyHabit
import com.example.data.DailyHabitRepository
import com.example.data.HabitIconKey
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
 * Self-contained Daily Habit Tracker + Animated Bunny Growth Evolution card.
 * Isolated leaf collector so habit check-offs never recompose the entire TodayScreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyHabitGrowthCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(context) { DailyHabitRepository.getInstance(context) }
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val snapshot by repo.snapshot.collectAsStateWithLifecycle()

    var showAddHabitModal by remember { mutableStateOf(false) }
    var previewStageOverride by remember { mutableStateOf<BunnyGrowthStage?>(null) }
    var hopTriggerCount by remember { mutableIntStateOf(0) }

    val activeStage = previewStageOverride ?: snapshot.bunnyStage
    val allHabitsDoneToday = snapshot.totalHabitsCount > 0 &&
        snapshot.completedTodayCount >= snapshot.totalHabitsCount
    val bunnyHappinessPct = remember(snapshot) {
        BunnyCompanionStateLogic.computeHabitHappinessPercent(snapshot, missedTasksCount = 0)
    }

    ModernPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_habit_growth_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.daily_habits_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                    Text(
                        text = if (snapshot.totalHabitsCount == 0) {
                            stringResource(R.string.daily_habits_subtitle_empty)
                        } else {
                            "${snapshot.completedTodayCount} of ${snapshot.totalHabitsCount} checked today · ${snapshot.weeklyConsistencyPercent}% 7-day consistency"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy
                    )
                }
                OutlinedButton(
                    onClick = {
                        feedback.onPopOpen()
                        showAddHabitModal = true
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_daily_habit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.daily_habits_add_button),
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepBlue
                    )
                }
            }

            // Animated Bunny Growth Habitat
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = SoftIceSurface,
                border = BorderStroke(1.dp, SubtleBorder.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    SkyBlue.copy(alpha = 0.24f),
                                    SoftIceSurface
                                )
                            )
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(PureWhite.copy(alpha = 0.85f))
                                .border(1.dp, SubtleBorder.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                                .clickable {
                                    feedback.onLightTick()
                                    hopTriggerCount++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedGrowthBunnyCanvas(
                                stage = activeStage,
                                hopTrigger = hopTriggerCount,
                                modifier = Modifier.size(94.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ModernPillBadge(
                                    text = "Stage ${activeStage.level}/5",
                                    containerColor = if (activeStage == BunnyGrowthStage.GOLDEN_GUARDIAN) PollenSoft else SkyBlue.copy(alpha = 0.7f),
                                    contentColor = MidnightNavy
                                )
                                if (previewStageOverride != null) {
                                    ModernPillBadge(
                                        text = "Preview",
                                        containerColor = PollenSoft,
                                        contentColor = DeepBlue
                                    )
                                } else {
                                    ModernPillBadge(
                                        text = "${snapshot.overallStreakDays}d streak",
                                        containerColor = CalmSageContainer,
                                        contentColor = CalmSage
                                    )
                                }
                                ModernPillBadge(
                                    text = "$bunnyHappinessPct% Happy",
                                    containerColor = if (allHabitsDoneToday) PollenSoft else SkyBlue.copy(alpha = 0.55f),
                                    contentColor = if (allHabitsDoneToday) MidnightNavy else DeepBlue
                                )
                            }
                            Text(
                                text = activeStage.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MidnightNavy
                            )
                            Text(
                                text = activeStage.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy,
                                maxLines = 2
                            )

                            if (previewStageOverride == null) {
                                val nextTarget = snapshot.bunnyStage.nextStreakTarget
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    LinearProgressIndicator(
                                        progress = { snapshot.progressToNextStage },
                                        color = DeepBlue,
                                        trackColor = SubtleBorder,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = if (nextTarget != null) {
                                            val needed = (nextTarget - snapshot.overallStreakDays).coerceAtLeast(1)
                                            "$needed more consistent ${if (needed == 1) "day" else "days"} to grow"
                                        } else {
                                            "Max bunny growth reached · Radiant!"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DeepBlue
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Growth Evolution Strip (tap any dot to preview growth animation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BunnyGrowthStage.entries.forEach { stage ->
                            val reached = snapshot.overallStreakDays >= stage.minStreakDays
                            val isShowing = activeStage == stage
                            Surface(
                                onClick = {
                                    feedback.onLightTick()
                                    hopTriggerCount++
                                    previewStageOverride = if (stage == snapshot.bunnyStage || previewStageOverride == stage) {
                                        null
                                    } else {
                                        stage
                                    }
                                },
                                shape = CircleShape,
                                color = when {
                                    isShowing -> DeepBlue
                                    reached -> SkyBlue.copy(alpha = 0.6f)
                                    else -> PureWhite.copy(alpha = 0.7f)
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isShowing) DeepBlue else SubtleBorder
                                ),
                                modifier = Modifier.testTag("bunny_stage_pill_${stage.level}")
                            ) {
                                Text(
                                    text = "${stage.minStreakDays}d",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isShowing) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isShowing) PureWhite else MidnightNavy,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Daily Habit Streak Counter & Mascot Happiness State Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(PureWhite.copy(alpha = 0.78f))
                            .border(0.75.dp, SubtleBorder.copy(alpha = 0.8f), MaterialTheme.shapes.medium)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("habit_streak_counter_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Current Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedNavy
                            )
                            Text(
                                text = "${snapshot.overallStreakDays} ${if (snapshot.overallStreakDays == 1) "day" else "days"} (Best ${snapshot.bestStreakDays}d)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = DeepBlue
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "Today's Habits",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedNavy
                            )
                            Text(
                                text = "${snapshot.completedTodayCount} / ${snapshot.totalHabitsCount}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (allHabitsDoneToday) CalmSage else MidnightNavy
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "Bunny Mood",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedNavy
                            )
                            Text(
                                text = when {
                                    allHabitsDoneToday -> "Overjoyed · 100%"
                                    snapshot.completedTodayCount > 0 -> "Happy · $bunnyHappinessPct%"
                                    else -> "Cozy · $bunnyHappinessPct%"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (snapshot.completedTodayCount > 0) CalmSage else DeepBlue
                            )
                        }
                    }

                    // Celebratory 8-bit mascot reward banner when all marked habits for the day are complete
                    AnimatedVisibility(visible = allHabitsDoneToday) {
                        Surface(
                            onClick = {
                                hopTriggerCount++
                                feedback.onHabitRewardEarned(
                                    allDoneToday = true,
                                    isStreakMilestone = snapshot.overallStreakDays >= 2
                                )
                                feedback.playBunnyVoice( com.example.audio.BunnyVoiceMood.HABIT_REWARD)
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = PollenSoft.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, CalmSage.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("all_habits_completed_reward_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = DeepBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = "All daily habits complete! (${snapshot.overallStreakDays.coerceAtLeast(1)}d streak)",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MidnightNavy
                                        )
                                        Text(
                                            text = "Bunny is beaming! Tap to replay 8-bit victory fanfare",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DeepBlue
                                        )
                                    }
                                }
                                ModernPillBadge(
                                    text = "8-Bit Fanfare",
                                    containerColor = PureWhite,
                                    contentColor = DeepBlue
                                )
                            }
                        }
                    }
                }
            }

            // Daily Goals Checklist OR Empty State
            if (snapshot.habits.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No daily habits yet. Add your own daily habits to build your streak and cheer up your bunny!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy
                    )
                    OutlinedButton(
                        onClick = {
                            feedback.onPopOpen()
                            showAddHabitModal = true
                        },
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = CircleShape,
                        modifier = Modifier.testTag("create_first_habit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add your first daily habit",
                            color = MidnightNavy
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    snapshot.habits.forEachIndexed { idx, habit ->
                        DailyHabitRow(
                            habit = habit,
                            onToggleToday = {
                                val nextChecked = !habit.completedToday
                                if (nextChecked) {
                                    feedback.onLightTick()
                                    hopTriggerCount++
                                } else {
                                    feedback.onHabitToggled(false)
                                }
                                repo.toggleHabitForDate(habit.id)
                            },
                            onDelete = {
                                feedback.onDeleteAction()
                                repo.deleteHabit(habit.id)
                            }
                        )
                        if (idx < snapshot.habits.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.75.dp,
                                color = SubtleBorder.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddHabitModal) {
        AddDailyHabitModal(
            onDismiss = { showAddHabitModal = false },
            onSave = { title, iconKey, colorKey ->
                feedback.onSaveSuccess()
                repo.addHabit(title, iconKey, colorKey)
                hopTriggerCount++
                showAddHabitModal = false
            }
        )
    }
}

@Composable
private fun DailyHabitRow(
    habit: DailyHabit,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit
) {
    val (fill, accent) = classColorPalette(habit.colorKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleToday)
            .padding(vertical = 10.dp)
            .testTag("daily_habit_row_${habit.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Checkable flower/circle target (48dp touch friendly)
        Surface(
            onClick = onToggleToday,
            shape = CircleShape,
            color = if (habit.completedToday) CalmSage else fill,
            border = BorderStroke(
                width = 1.5.dp,
                color = if (habit.completedToday) CalmSage else accent.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("daily_habit_check_${habit.id}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (habit.completedToday) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Completed today",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = habitIconVector(habit.iconKey),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (habit.completedToday) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (habit.completedToday) MutedNavy else MidnightNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (habit.currentStreakDays > 0) {
                    Surface(
                        shape = CircleShape,
                        color = SkyBlue.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "${habit.currentStreakDays}d",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DeepBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 7-day rolling consistency dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                habit.last7DaysCompleted.forEachIndexed { idx, done ->
                    val isToday = idx == 6
                    Box(
                        modifier = Modifier
                            .size(if (isToday) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> ForgetMeNotBlue
                                    isToday -> SubtleBorder
                                    else -> SubtleBorder.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "7d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy
                )
            }
        }

        if (habit.completedToday) {
            ForgetMeNotFlower(bloomed = true, size = 20.dp, animatePop = true)
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete habit",
                tint = MutedNavy.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDailyHabitModal(
    onDismiss: () -> Unit,
    onSave: (String, HabitIconKey, ClassColorKey) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(HabitIconKey.SPARKLE) }
    var selectedColor by remember { mutableStateOf(ClassColorKey.SKY) }

    BunnyMemoModalContainer(
        title = stringResource(R.string.daily_habits_modal_title),
        subtitle = stringResource(R.string.daily_habits_modal_subtitle),
        onDismiss = onDismiss,
        maxWidth = 440.dp
    ) { requestDismiss ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                label = { Text("Small daily goal (e.g., Read 10 pages)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_habit_title_input")
            )

            Text(
                text = "Choose an icon",
                style = MaterialTheme.typography.labelMedium,
                color = MidnightNavy
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HabitIconKey.entries.forEach { key ->
                    FilterChip(
                        selected = selectedIcon == key,
                        onClick = { selectedIcon = key },
                        leadingIcon = {
                            Icon(
                                imageVector = habitIconVector(key),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(key.label) }
                    )
                }
            }

            Text(
                text = "Accent color",
                style = MaterialTheme.typography.labelMedium,
                color = MidnightNavy
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClassColorKey.entries.forEach { cKey ->
                    val (fill, accent) = classColorPalette(cKey)
                    val isSel = selectedColor == cKey
                    Surface(
                        onClick = { selectedColor = cKey },
                        shape = CircleShape,
                        color = fill,
                        border = BorderStroke(if (isSel) 2.5.dp else 1.dp, if (isSel) DeepBlue else accent),
                        modifier = Modifier.size(34.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = requestDismiss,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(title, selectedIcon, selectedColor)
                        }
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_daily_habit_button")
                ) {
                    Text("Add goal")
                }
            }
        }
    }
}

private fun habitIconVector(key: HabitIconKey): ImageVector = when (key) {
    HabitIconKey.WATER -> Icons.Outlined.WaterDrop
    HabitIconKey.BOOK -> Icons.AutoMirrored.Outlined.MenuBook
    HabitIconKey.STRETCH -> Icons.Outlined.FitnessCenter
    HabitIconKey.SPARKLE -> Icons.Outlined.AutoAwesome
    HabitIconKey.MEDITATE -> Icons.Outlined.SelfImprovement
    HabitIconKey.LEAF -> Icons.Outlined.LocalFlorist
    HabitIconKey.JOURNAL -> Icons.Outlined.EditNote
    HabitIconKey.HEART -> Icons.Outlined.FavoriteBorder
}

/**
 * Animated 5-stage Bunny Growth illustration that visibly evolves in scale, ear length,
 * forget-me-not blossoms, scarf ribbon, and golden halo based on daily streak consistency.
 */
@Composable
fun AnimatedGrowthBunnyCanvas(
    stage: BunnyGrowthStage,
    hopTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()

    // Smooth spring growth when advancing stages
    val animatedScale by animateFloatAsState(
        targetValue = stage.targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bunny_growth_scale"
    )

    // Celebratory hop bounce whenever a habit is checked off or tapped
    var isHopping by remember { mutableStateOf(false) }
    LaunchedEffect(hopTrigger) {
        if (hopTrigger > 0 && !reducedMotion) {
            isHopping = true
            delay(260L)
            isHopping = false
        }
    }
    val hopOffsetFraction by animateFloatAsState(
        targetValue = if (isHopping) -0.10f else 0f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = 380f
        ),
        label = "bunny_hop_offset"
    )

    // Gentle idle breathing bob
    val infinite = rememberInfiniteTransition(label = "bunny_idle_breath")
    val idleBob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bunny_bob_phase"
    )

    Canvas(modifier = modifier) {
        val s = size.minDimension
        if (s <= 4f) return@Canvas

        val cx = size.width / 2f
        val baseY = size.height * (0.56f + hopOffsetFraction + (idleBob - 0.5f) * 0.025f)
        val bunnyUnit = s * 0.78f * animatedScale
        val strokeW = (s * 0.026f).coerceIn(1.8f, 4.2f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Stage 5 Golden Guardian Halo + drifting petals
        if (stage.level >= 5) {
            drawCircle(
                color = PollenYellow.copy(alpha = 0.32f + idleBob * 0.14f),
                radius = bunnyUnit * 0.54f,
                center = Offset(cx, baseY - bunnyUnit * 0.04f)
            )
            for (i in 0 until 6) {
                val angle = (i * 60f + idleBob * 18f) * (PI / 180.0)
                val rx = cx + (bunnyUnit * 0.48f * cos(angle)).toFloat()
                val ry = baseY - bunnyUnit * 0.04f + (bunnyUnit * 0.44f * sin(angle)).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) PollenYellow else BunnyFlowerCrown,
                    radius = s * 0.026f,
                    center = Offset(rx, ry)
                )
            }
        } else if (stage.level >= 3) {
            drawCircle(
                color = SkyBlue.copy(alpha = 0.25f),
                radius = bunnyUnit * 0.46f,
                center = Offset(cx, baseY)
            )
        }

        // Ground shadow shrinks during hop
        val shadowScale = (1f + hopOffsetFraction * 2f).coerceIn(0.65f, 1f)
        drawOval(
            color = BunnyOutlineInk.copy(alpha = 0.10f),
            topLeft = Offset(cx - bunnyUnit * 0.30f * shadowScale, size.height * 0.86f),
            size = Size(bunnyUnit * 0.60f * shadowScale, s * 0.06f)
        )

        // Stage 1 Sprout Kit: cozy nest leaf beneath tiny bunny
        if (stage.level == 1) {
            drawOval(
                color = CalmSage.copy(alpha = 0.38f),
                topLeft = Offset(cx - bunnyUnit * 0.44f, baseY + bunnyUnit * 0.16f),
                size = Size(bunnyUnit * 0.88f, bunnyUnit * 0.26f)
            )
        }

        // Ears grow taller & perkier as streak grows
        val earLenFactor = 0.23f + (stage.level - 1) * 0.026f
        val earTilt = when (stage.level) {
            1 -> 18f
            2 -> 13f
            else -> 9f
        }
        val headTopY = baseY - bunnyUnit * 0.18f
        drawGrowthEar(cx - bunnyUnit * 0.11f, headTopY, bunnyUnit, earLenFactor, -earTilt, stroke)
        drawGrowthEar(cx + bunnyUnit * 0.11f, headTopY, bunnyUnit, earLenFactor, earTilt, stroke)

        // Body
        val bodyW = bunnyUnit * 0.50f
        val bodyH = bunnyUnit * 0.38f
        drawOval(
            color = BunnyFurWhite,
            topLeft = Offset(cx - bodyW / 2f, baseY + bunnyUnit * 0.02f),
            size = Size(bodyW, bodyH)
        )
        drawOval(
            color = BunnyOutlineInk,
            topLeft = Offset(cx - bodyW / 2f, baseY + bunnyUnit * 0.02f),
            size = Size(bodyW, bodyH),
            style = stroke
        )

        // Stage 4+ pastel sky ribbon scarf
        if (stage.level >= 4) {
            drawRoundRect(
                color = if (stage.level >= 5) PollenYellow else BunnyFlowerCrown,
                topLeft = Offset(cx - bodyW * 0.38f, baseY + bunnyUnit * 0.03f),
                size = Size(bodyW * 0.76f, bunnyUnit * 0.09f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bunnyUnit * 0.05f)
            )
        }

        // Head
        val headW = bunnyUnit * 0.54f
        val headH = bunnyUnit * 0.40f
        val headTop = baseY - bunnyUnit * 0.24f
        drawOval(
            color = BunnyFurWhite,
            topLeft = Offset(cx - headW / 2f, headTop),
            size = Size(headW, headH)
        )
        drawOval(
            color = BunnyOutlineInk,
            topLeft = Offset(cx - headW / 2f, headTop),
            size = Size(headW, headH),
            style = stroke
        )

        // Blush cheeks
        drawCircle(BunnyEarBlush, radius = bunnyUnit * 0.048f, center = Offset(cx - headW * 0.30f, headTop + headH * 0.62f))
        drawCircle(BunnyEarBlush, radius = bunnyUnit * 0.048f, center = Offset(cx + headW * 0.30f, headTop + headH * 0.62f))

        // Eyes (Stage 1 sleepy arcs -> Stage 2+ bright cheerful eyes)
        if (stage.level == 1) {
            val leftEye = Path().apply {
                moveTo(cx - headW * 0.22f, headTop + headH * 0.46f)
                quadraticTo(cx - headW * 0.16f, headTop + headH * 0.52f, cx - headW * 0.10f, headTop + headH * 0.46f)
            }
            val rightEye = Path().apply {
                moveTo(cx + headW * 0.10f, headTop + headH * 0.46f)
                quadraticTo(cx + headW * 0.16f, headTop + headH * 0.52f, cx + headW * 0.22f, headTop + headH * 0.46f)
            }
            drawPath(leftEye, BunnyOutlineInk, style = stroke)
            drawPath(rightEye, BunnyOutlineInk, style = stroke)
        } else {
            drawCircle(BunnyOutlineInk, radius = bunnyUnit * 0.028f, center = Offset(cx - headW * 0.17f, headTop + headH * 0.46f))
            drawCircle(BunnyOutlineInk, radius = bunnyUnit * 0.028f, center = Offset(cx + headW * 0.17f, headTop + headH * 0.46f))
            // Eye sparkle
            drawCircle(Color.White, radius = bunnyUnit * 0.010f, center = Offset(cx - headW * 0.18f, headTop + headH * 0.44f))
            drawCircle(Color.White, radius = bunnyUnit * 0.010f, center = Offset(cx + headW * 0.16f, headTop + headH * 0.44f))
        }

        // Cute nose & happy smile
        drawCircle(BunnyOutlineInk, radius = bunnyUnit * 0.017f, center = Offset(cx, headTop + headH * 0.57f))
        val smile = Path().apply {
            moveTo(cx - bunnyUnit * 0.045f, headTop + headH * 0.66f)
            quadraticTo(cx, headTop + headH * 0.74f, cx + bunnyUnit * 0.045f, headTop + headH * 0.66f)
        }
        drawPath(smile, BunnyOutlineInk, style = stroke)

        // Stage 2+: Tiny bud or forget-me-not blossoms on crown
        when {
            stage.level == 2 -> {
                // Single cute sprout bud on crown
                drawCircle(CalmSage, radius = bunnyUnit * 0.045f, center = Offset(cx + headW * 0.22f, headTop + headH * 0.06f))
                drawCircle(BunnyFlowerCrown, radius = bunnyUnit * 0.032f, center = Offset(cx + headW * 0.27f, headTop))
            }
            stage.level == 3 -> {
                drawTinyForgetMeNot(cx + headW * 0.26f, headTop + headH * 0.06f, bunnyUnit * 0.088f)
            }
            stage.level == 4 -> {
                drawTinyForgetMeNot(cx - headW * 0.24f, headTop + headH * 0.06f, bunnyUnit * 0.08f)
                drawTinyForgetMeNot(cx, headTop - bunnyUnit * 0.01f, bunnyUnit * 0.095f)
                drawTinyForgetMeNot(cx + headW * 0.24f, headTop + headH * 0.06f, bunnyUnit * 0.08f)
            }
            stage.level >= 5 -> {
                drawTinyForgetMeNot(cx - headW * 0.26f, headTop + headH * 0.06f, bunnyUnit * 0.085f)
                drawTinyForgetMeNot(cx + headW * 0.26f, headTop + headH * 0.06f, bunnyUnit * 0.085f)
                // Golden crown blossom center
                drawTinyForgetMeNot(cx, headTop - bunnyUnit * 0.02f, bunnyUnit * 0.11f, petalColor = PollenYellow)
            }
        }
    }
}

private fun DrawScope.drawGrowthEar(
    pivotX: Float,
    pivotY: Float,
    unit: Float,
    lenFactor: Float,
    angleDeg: Float,
    stroke: Stroke
) {
    rotate(degrees = angleDeg, pivot = Offset(pivotX, pivotY)) {
        val earW = unit * 0.14f
        val earH = unit * lenFactor
        drawOval(
            color = BunnyFurWhite,
            topLeft = Offset(pivotX - earW / 2f, pivotY - earH),
            size = Size(earW, earH + unit * 0.04f)
        )
        drawOval(
            color = BunnyOutlineInk,
            topLeft = Offset(pivotX - earW / 2f, pivotY - earH),
            size = Size(earW, earH + unit * 0.04f),
            style = stroke
        )
        drawOval(
            color = BunnyEarBlush,
            topLeft = Offset(pivotX - earW * 0.24f, pivotY - earH * 0.80f),
            size = Size(earW * 0.48f, earH * 0.66f)
        )
    }
}

private fun DrawScope.drawTinyForgetMeNot(
    cx: Float,
    cy: Float,
    radius: Float,
    petalColor: Color = ForgetMeNotBlue
) {
    val petalDist = radius * 0.46f
    val petalR = radius * 0.38f
    for (i in 0 until 5) {
        val rad = (-90.0 + i * 72.0) * PI / 180.0
        val px = cx + (petalDist * cos(rad)).toFloat()
        val py = cy + (petalDist * sin(rad)).toFloat()
        drawCircle(petalColor, radius = petalR, center = Offset(px, py))
    }
    drawCircle(BunnyFurWhite, radius = radius * 0.28f, center = Offset(cx, cy))
    drawCircle(PollenYellow, radius = radius * 0.19f, center = Offset(cx, cy))
}
