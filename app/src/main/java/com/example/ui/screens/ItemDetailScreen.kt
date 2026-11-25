package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CalendarDate
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerReminder
import com.example.data.ReminderKind
import com.example.data.UnifiedStep
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.components.BunnyMemoConfirmModal
import com.example.ui.components.BunnyMemoDatePickerModal
import com.example.ui.components.BunnyPose
import com.example.ui.components.FlowerProgressRow
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.TonalInsetPanel
import com.example.ui.components.ruledMemoLines
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueContainer
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(
    item: PlannerItem,
    state: PlannerUiState.Ready,
    isCelebrating: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onEditItem: (PlannerItem) -> Unit,
    onToggleItemCompleted: (PlannerItem, Boolean) -> Unit,
    onDeleteItem: (PlannerItem) -> Unit,
    onAddStep: (itemId: String, title: String, dueDate: String?) -> Unit,
    onToggleStep: (UnifiedStep, Boolean) -> Unit,
    onDeleteStep: (UnifiedStep) -> Unit,
    onAddReminder: (PlannerItem, ReminderKind, Int) -> Unit,
    onDeleteReminder: (PlannerReminder) -> Unit,
    onDismissCelebration: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val progress = state.progressFor(item)
    val steps = state.stepsFor(item)
    val reminders = state.remindersFor(item.id)
    val nowMillis = state.nowMillis

    var showAddStepField by remember(item.id) { mutableStateOf(false) }
    var newStepTitle by remember(item.id) { mutableStateOf("") }
    var newStepDueDate by remember(item.id) { mutableStateOf<CalendarDate?>(null) }
    var showStepDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val supportsChecklist = item.itemType() == ItemType.PLAN || item.itemType() == ItemType.EVENT

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("item_detail_screen"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header action bar
            item(key = "detail_top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("detail_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MidnightNavy
                                )
                            }
                        }
                        ModernPillBadge(
                            text = item.itemType().label,
                            containerColor = SoftIceSurface,
                            contentColor = MidnightNavy
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onEditItem(item) },
                            modifier = Modifier.testTag("detail_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit or reschedule",
                                tint = DeepBlue
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete item",
                                tint = OverdueTerracotta
                            )
                        }
                        if (!showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("detail_close_pane_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close detail pane",
                                    tint = MutedNavy
                                )
                            }
                        }
                    }
                }
            }

        // Completion celebration moment (Brief Page 3: last bud opens beside the bunny once!)
        item(key = "celebration_banner") {
            AnimatedVisibility(visible = isCelebrating && progress.allStepsDone) {
                Surface(
                    color = PollenSoft,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ForgetMeNotBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("completion_celebration_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallBunnyIllustration(
                            pose = BunnyPose.CELEBRATING,
                            size = 64.dp,
                            contentDesc = stringResource(R.string.cd_bunny_celebrating)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (progress.totalCount > 0) {
                                    progress.completionLabel(item.itemType())
                                } else {
                                    "All done!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MidnightNavy
                            )
                            Text(
                                text = "Every forget-me-not has bloomed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onDismissCelebration) {
                            Text("Lovely")
                        }
                    }
                }
            }
        }

        // Title, Timing, Event Countdown & Notes Card
        item(key = "detail_hero") {
            ModernPanel(
                shape = RoundedCornerShape(24.dp),
                elevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MidnightNavy
                    )

                    // Timing details
                    when (item.itemType()) {
                        ItemType.EVENT -> {
                            val eventDay = item.startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                                ?: CalendarDate.parse(item.dueDate)
                            val countdown = PlannerDateUtils.formatEventCountdown(
                                item.startAtUtcMillis,
                                item.endAtUtcMillis,
                                nowMillis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = DeepBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = buildString {
                                        if (eventDay != null) {
                                            append(PlannerDateUtils.formatShortDate(eventDay))
                                            append(" · ")
                                        }
                                        append(
                                            PlannerDateUtils.formatEventTimeRange(
                                                item.startAtUtcMillis,
                                                item.endAtUtcMillis
                                            )
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MidnightNavy
                                )
                            }
                            // Event countdown shown separately from preparation progress (Brief Page 2)
                            Surface(
                                color = SkyBlue.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Countdown: $countdown",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MidnightNavy,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        ItemType.PLAN -> {
                            val start = CalendarDate.parse(item.startDate)
                            val due = CalendarDate.parse(item.dueDate)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = DeepBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildString {
                                        if (start != null) {
                                            append(PlannerDateUtils.formatShortDate(start))
                                            append(" → ")
                                        }
                                        if (due != null) {
                                            append(PlannerDateUtils.formatShortDate(due))
                                        } else {
                                            append("No deadline set")
                                        }
                                        item.dueTimeMinutes?.let {
                                            append(" · ${PlannerDateUtils.formatTimeOfDay(it)}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MidnightNavy
                                )
                            }
                        }

                        ItemType.TASK -> {
                            val due = CalendarDate.parse(item.dueDate)
                            val parentTitle = item.parentPlanId?.let { pid ->
                                state.items.firstOrNull { it.id == pid }?.title
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = DeepBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildString {
                                        if (due != null) append("Due ${PlannerDateUtils.formatShortDate(due)}")
                                        else append("No due date")
                                        item.dueTimeMinutes?.let {
                                            append(" · ${PlannerDateUtils.formatTimeOfDay(it)}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (parentTitle != null) {
                                Surface(
                                    color = SoftIceSurface,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Counts as a step in: $parentTitle",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DeepBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!item.notes.isNullOrBlank()) {
                        TonalInsetPanel(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MidnightNavy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .ruledMemoLines(lineSpacing = 22.dp)
                                    .padding(14.dp)
                            )
                        }
                    }

                    // Easy-to-reach primary completion / reschedule controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (item.itemType() == ItemType.TASK || (item.itemType() == ItemType.PLAN && progress.totalCount == 0)) {
                            Button(
                                onClick = { onToggleItemCompleted(item, !item.completed) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (item.completed) CalmSage else DeepBlue,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_complete_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (item.completed) "Done · Reopen" else "Mark complete")
                            }
                        }
                        OutlinedButton(
                            onClick = { onEditItem(item) },
                            border = BorderStroke(1.dp, SubtleBorder),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("detail_reschedule_button")
                        ) {
                            Text("Edit / Reschedule", color = MidnightNavy)
                        }
                    }
                }
            }
        }

        // Progress & Checklist section for Plans and Events
        if (supportsChecklist) {
            item(key = "detail_progress_card") {
                ModernPanel(
                    shape = RoundedCornerShape(24.dp),
                    elevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
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
                            Text(
                                text = if (item.itemType() == ItemType.PLAN) "Milestones" else "Preparation checklist",
                                style = MaterialTheme.typography.titleLarge,
                                color = MidnightNavy
                            )
                            if (progress.overdueCount > 0) {
                                Surface(
                                    color = OverdueContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.WarningAmber,
                                            contentDescription = null,
                                            tint = OverdueTerracotta,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${progress.overdueCount} overdue",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OverdueTerracotta
                                        )
                                    }
                                }
                            } else if (progress.totalCount > 0 && progress.allStepsDone) {
                                Surface(
                                    color = CalmSageContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (item.itemType() == ItemType.EVENT) "Fully prepared" else "Complete",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CalmSage,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (progress.totalCount > 0) {
                            FlowerProgressRow(
                                completed = progress.completedCount,
                                total = progress.totalCount,
                                summaryText = progress.summaryLabel(item.itemType()),
                                celebrateFinal = isCelebrating,
                                flowerSize = 26.dp
                            )
                        } else {
                            // Brief Page 2: When no steps exist, offer "Add first step" and allow manual plan completion.
                            Text(
                                text = if (item.itemType() == ItemType.PLAN) {
                                    "Each completed milestone opens a forget-me-not. Add your first step or complete this plan manually."
                                } else {
                                    "Add optional preparation steps to track readiness before the event begins."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showAddStepField = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeepBlue,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("add_first_step_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add first step")
                            }
                        }

                        // Inline step composer
                        if (showAddStepField || progress.totalCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newStepTitle,
                                    onValueChange = { newStepTitle = it.take(200) },
                                    placeholder = { Text("Add a step...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detail_new_step_input")
                                )
                                IconButton(onClick = { showStepDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = "Set step due date",
                                        tint = if (newStepDueDate != null) DeepBlue else MidnightNavy
                                    )
                                }
                                Button(
                                    onClick = {
                                        val clean = newStepTitle.trim()
                                        if (clean.isNotEmpty()) {
                                            onAddStep(item.id, clean, newStepDueDate?.toIsoString())
                                            newStepTitle = ""
                                            newStepDueDate = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeepBlue,
                                        contentColor = PureWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    modifier = Modifier.testTag("detail_add_step_submit")
                                ) {
                                    Text("Add")
                                }
                            }
                            if (newStepDueDate != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Step due ${PlannerDateUtils.formatShortDate(newStepDueDate!!)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DeepBlue
                                    )
                                    TextButton(onClick = { newStepDueDate = null }) {
                                        Text("Clear")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(steps, key = { "detail_step_${it.id}" }) { step ->
                val overdue = step.isOverdue(nowMillis)
                ModernPanel(
                    shape = RoundedCornerShape(18.dp),
                    borderColor = if (overdue) OverdueTerracotta.copy(alpha = 0.4f) else SubtleBorder.copy(alpha = 0.85f),
                    elevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = step.completed,
                            onCheckedChange = { checked -> onToggleStep(step, checked) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DeepBlue,
                                uncheckedColor = ForgetMeNotBlue,
                                checkmarkColor = PureWhite
                            ),
                            modifier = Modifier.testTag("detail_step_checkbox_${step.id}")
                        )
                        ForgetMeNotFlower(
                            bloomed = step.completed,
                            size = 20.dp,
                            animatePop = step.completed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (step.completed) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (step.completed) MaterialTheme.colorScheme.onSurfaceVariant else MidnightNavy
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                step.dueDate?.let { raw ->
                                    CalendarDate.parse(raw)?.let { parsed ->
                                        Text(
                                            text = "Due ${PlannerDateUtils.formatCompactDate(parsed)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (overdue) OverdueTerracotta else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (step.isLinkedTask) {
                                    Text(
                                        text = "Linked task",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DeepBlue
                                    )
                                }
                                if (overdue) {
                                    Text(
                                        text = "Overdue",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OverdueTerracotta
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onDeleteStep(step) }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove step",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Reminders section
        item(key = "detail_reminders_card") {
            ModernPanel(
                shape = RoundedCornerShape(24.dp),
                elevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reminders",
                            style = MaterialTheme.typography.titleLarge,
                            color = MidnightNavy
                        )
                    }

                    if (reminders.isEmpty()) {
                        Text(
                            text = "No reminders scheduled yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    } else {
                        reminders.forEach { rem ->
                            TonalInsetPanel(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${rem.reminderKind().label} · ${PlannerDateUtils.formatOffsetLabel(rem.offsetMinutes)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MidnightNavy
                                        )
                                        Text(
                                            text = PlannerDateUtils.formatUtcMillisDateTime(rem.effectiveTriggerMillis()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteReminder(rem) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove reminder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Quick add reminder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { onAddReminder(item, ReminderKind.ORDINARY, 60) },
                            label = { Text("+ 1h before") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { onAddReminder(item, ReminderKind.ORDINARY, 1440) },
                            label = { Text("+ 1 day before") }
                        )
                        if (supportsChecklist) {
                            FilterChip(
                                selected = false,
                                onClick = { onAddReminder(item, ReminderKind.PROGRESS_CHECKIN, 1440) },
                                label = { Text("+ Progress check-in (1d)") }
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (showStepDatePicker) {
        val initial = newStepDueDate ?: CalendarDate.parse(item.dueDate) ?: state.todayDate()
        BunnyMemoDatePickerModal(
            title = "Milestone due date",
            initialDate = initial,
            onDateSelected = { picked ->
                newStepDueDate = picked
                showStepDatePicker = false
            },
            onDismiss = { showStepDatePicker = false }
        )
    }

    if (showDeleteConfirm) {
        BunnyMemoConfirmModal(
            title = "Delete ${item.itemType().label.lowercase()}?",
            message = "This removes “${item.title}” along with its steps and pending reminders.",
            confirmLabel = "Delete",
            cancelLabel = "Keep it",
            isDestructive = true,
            confirmTestTag = "confirm_delete_button",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteItem(item)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
