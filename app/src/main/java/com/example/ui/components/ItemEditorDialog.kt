package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CalendarDate
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerReminder
import com.example.data.ReminderDraft
import com.example.data.ReminderKind
import com.example.data.StepDraft
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SubtleBorder
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditorDialog(
    prefillDate: CalendarDate,
    existingItem: PlannerItem?,
    existingReminders: List<PlannerReminder>,
    availablePlans: List<PlannerItem>,
    defaultReminderHour: Int,
    defaultReminderMinute: Int,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveNew: (
        type: ItemType,
        title: String,
        notes: String?,
        startDate: String?,
        dueDate: String?,
        dueTimeMinutes: Int?,
        startAtUtcMillis: Long?,
        endAtUtcMillis: Long?,
        parentPlanId: String?,
        steps: List<StepDraft>,
        reminders: List<ReminderDraft>
    ) -> Unit,
    onSaveEdit: (
        item: PlannerItem,
        type: ItemType,
        title: String,
        notes: String?,
        startDate: String?,
        dueDate: String?,
        dueTimeMinutes: Int?,
        startAtUtcMillis: Long?,
        endAtUtcMillis: Long?,
        parentPlanId: String?,
        reminders: List<ReminderDraft>
    ) -> Unit
) {
    val isEditing = existingItem != null
    var selectedType by remember { mutableStateOf(existingItem?.itemType() ?: ItemType.TASK) }
    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var notes by remember { mutableStateOf(existingItem?.notes ?: "") }
    var titleError by remember { mutableStateOf<String?>(null) }

    // Date states
    var startDate by remember {
        mutableStateOf(
            CalendarDate.parse(existingItem?.startDate)
                ?: existingItem?.startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                ?: prefillDate
        )
    }
    var dueDate by remember {
        mutableStateOf(
            CalendarDate.parse(existingItem?.dueDate)
                ?: existingItem?.startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                ?: if (selectedType == ItemType.PLAN) prefillDate.plusDays(3) else prefillDate
        )
    }

    // Time states
    var hasDueTime by remember { mutableStateOf(existingItem?.dueTimeMinutes != null) }
    var dueHour by remember { mutableIntStateOf((existingItem?.dueTimeMinutes ?: (17 * 60)) / 60) }
    var dueMinute by remember { mutableIntStateOf((existingItem?.dueTimeMinutes ?: (17 * 60)) % 60) }

    val initialEventStartCal = remember(existingItem) {
        Calendar.getInstance(TimeZone.getDefault()).apply {
            if (existingItem?.startAtUtcMillis != null) {
                timeInMillis = existingItem.startAtUtcMillis
            } else {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
            }
        }
    }
    val initialEventEndCal = remember(existingItem) {
        Calendar.getInstance(TimeZone.getDefault()).apply {
            if (existingItem?.endAtUtcMillis != null) {
                timeInMillis = existingItem.endAtUtcMillis
            } else {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 30)
            }
        }
    }
    var eventStartHour by remember { mutableIntStateOf(initialEventStartCal.get(Calendar.HOUR_OF_DAY)) }
    var eventStartMinute by remember { mutableIntStateOf(initialEventStartCal.get(Calendar.MINUTE)) }
    var eventEndHour by remember { mutableIntStateOf(initialEventEndCal.get(Calendar.HOUR_OF_DAY)) }
    var eventEndMinute by remember { mutableIntStateOf(initialEventEndCal.get(Calendar.MINUTE)) }

    // Linked parent plan for Task
    var parentPlanId by remember { mutableStateOf(existingItem?.parentPlanId) }

    // Inline steps when creating a new Plan or Event
    val draftSteps = remember { mutableStateListOf<StepDraft>() }
    var newStepTitle by remember { mutableStateOf("") }
    var newStepDueDate by remember { mutableStateOf<CalendarDate?>(null) }

    // Reminder drafts
    val draftReminders = remember {
        mutableStateListOf<ReminderDraft>().apply {
            if (existingItem != null) {
                existingReminders.forEach {
                    add(
                        ReminderDraft(
                            kind = it.reminderKind(),
                            offsetMinutes = it.offsetMinutes,
                            localHour = it.localHour,
                            localMinute = it.localMinute
                        )
                    )
                }
            } else {
                add(
                    ReminderDraft(
                        kind = ReminderKind.ORDINARY,
                        offsetMinutes = 60,
                        localHour = defaultReminderHour,
                        localMinute = defaultReminderMinute
                    )
                )
            }
        }
    }
    var reminderLocalHour by remember {
        mutableIntStateOf(
            existingReminders.firstOrNull()?.localHour ?: defaultReminderHour
        )
    }
    var reminderLocalMinute by remember {
        mutableIntStateOf(
            existingReminders.firstOrNull()?.localMinute ?: defaultReminderMinute
        )
    }

    // Active picker modals
    var activeDateTarget by remember { mutableStateOf<String?>(null) } // "start", "due", "step"
    var activeTimeTarget by remember { mutableStateOf<String?>(null) } // "dueTime", "eventStart", "eventEnd", "reminderTime"

    BunnyMemoModalContainer(
        title = if (isEditing) "Edit ${selectedType.label}" else "Add to planner",
        subtitle = "Plant a plan, task, or gentle milestone",
        onDismiss = onDismiss,
        maxWidth = 520.dp
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
                    // Item Type Picker (Plan / Task / Event)
                    ModernSegmentedToggle(
                        options = ItemType.entries.map { it to it.label },
                        selected = selectedType,
                        onSelect = { itemType ->
                            selectedType = itemType
                            if (!isEditing && itemType == ItemType.PLAN && dueDate == startDate) {
                                dueDate = startDate.plusDays(3)
                            }
                        },
                        tagPrefix = "type_chip",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(200)
                            titleError = null
                        },
                        label = {
                            Text(
                                when (selectedType) {
                                    ItemType.PLAN -> "Plan title (e.g. Make a weekend scrapbook)"
                                    ItemType.TASK -> "Task title"
                                    ItemType.EVENT -> "Event title (e.g. Birthday dinner)"
                                }
                            )
                        },
                        singleLine = true,
                        isError = titleError != null,
                        supportingText = titleError?.let { msg -> { Text(msg) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )

                    // Timing section
                    when (selectedType) {
                        ItemType.PLAN -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PickerChipButton(
                                    label = "Start",
                                    value = PlannerDateUtils.formatShortDate(startDate),
                                    icon = Icons.Outlined.CalendarToday,
                                    onClick = { activeDateTarget = "start" },
                                    modifier = Modifier.weight(1f)
                                )
                                PickerChipButton(
                                    label = "Deadline",
                                    value = PlannerDateUtils.formatShortDate(dueDate),
                                    icon = Icons.Outlined.CalendarToday,
                                    onClick = { activeDateTarget = "due" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = hasDueTime,
                                    onClick = { hasDueTime = !hasDueTime },
                                    label = { Text(if (hasDueTime) "Deadline time: ${PlannerDateUtils.formatTimeOfDay(dueHour * 60 + dueMinute)}" else "Add deadline time") }
                                )
                                if (hasDueTime) {
                                    TextButton(onClick = { activeTimeTarget = "dueTime" }) {
                                        Text("Change")
                                    }
                                }
                            }
                        }

                        ItemType.TASK -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PickerChipButton(
                                    label = "Due date",
                                    value = PlannerDateUtils.formatShortDate(dueDate),
                                    icon = Icons.Outlined.CalendarToday,
                                    onClick = { activeDateTarget = "due" },
                                    modifier = Modifier.weight(1f)
                                )
                                PickerChipButton(
                                    label = "Due time",
                                    value = if (hasDueTime) PlannerDateUtils.formatTimeOfDay(dueHour * 60 + dueMinute) else "Anytime",
                                    icon = Icons.Outlined.Schedule,
                                    onClick = {
                                        hasDueTime = true
                                        activeTimeTarget = "dueTime"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (availablePlans.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Link as a step in a plan (optional)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = parentPlanId == null,
                                            onClick = { parentPlanId = null },
                                            label = { Text("Standalone") }
                                        )
                                        availablePlans.forEach { plan ->
                                            FilterChip(
                                                selected = parentPlanId == plan.id,
                                                onClick = { parentPlanId = plan.id },
                                                label = { Text(plan.title.take(24)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ItemType.EVENT -> {
                            PickerChipButton(
                                label = "Event date",
                                value = PlannerDateUtils.formatShortDate(startDate),
                                icon = Icons.Outlined.CalendarToday,
                                onClick = { activeDateTarget = "start" },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PickerChipButton(
                                    label = "Starts",
                                    value = PlannerDateUtils.formatTimeOfDay(eventStartHour * 60 + eventStartMinute),
                                    icon = Icons.Outlined.Schedule,
                                    onClick = { activeTimeTarget = "eventStart" },
                                    modifier = Modifier.weight(1f)
                                )
                                PickerChipButton(
                                    label = "Ends",
                                    value = PlannerDateUtils.formatTimeOfDay(eventEndHour * 60 + eventEndMinute),
                                    icon = Icons.Outlined.Schedule,
                                    onClick = { activeTimeTarget = "eventEnd" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(1000) },
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Optional initial steps for new Plan or Event
                    if (!isEditing && (selectedType == ItemType.PLAN || selectedType == ItemType.EVENT)) {
                        HorizontalDivider(color = SubtleBorder)
                        Text(
                            text = if (selectedType == ItemType.PLAN) "Milestones (optional)" else "Preparation checklist (optional)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MidnightNavy
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newStepTitle,
                                onValueChange = { newStepTitle = it.take(200) },
                                placeholder = {
                                    Text(
                                        if (selectedType == ItemType.PLAN) "Add a milestone..."
                                        else "Add a prep step..."
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_step_input")
                            )
                            IconButton(
                                onClick = { activeDateTarget = "step" }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = "Optional step due date",
                                    tint = if (newStepDueDate != null) DeepBlue else MidnightNavy
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clean = newStepTitle.trim()
                                    if (clean.isNotEmpty()) {
                                        draftSteps.add(
                                            StepDraft(
                                                title = clean,
                                                dueDate = newStepDueDate?.toIsoString()
                                            )
                                        )
                                        newStepTitle = ""
                                        newStepDueDate = null
                                    }
                                },
                                modifier = Modifier.testTag("editor_add_step_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add step",
                                    tint = DeepBlue
                                )
                            }
                        }
                        if (newStepDueDate != null) {
                            Text(
                                text = "Step due: ${PlannerDateUtils.formatShortDate(newStepDueDate!!)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepBlue
                            )
                        }
                        draftSteps.forEachIndexed { index, step ->
                            Surface(
                                color = PureWhite,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SubtleBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ForgetMeNotFlower(bloomed = false, size = 18.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(step.title, style = MaterialTheme.typography.bodyMedium)
                                        step.dueDate?.let { d ->
                                            CalendarDate.parse(d)?.let { parsed ->
                                                Text(
                                                    "Due ${PlannerDateUtils.formatShortDate(parsed)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = { draftSteps.removeAt(index) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove step",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Reminder rules section
                    HorizontalDivider(color = SubtleBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reminders",
                                style = MaterialTheme.typography.titleSmall,
                                color = MidnightNavy
                            )
                        }
                        if (selectedType != ItemType.EVENT && !hasDueTime) {
                            TextButton(onClick = { activeTimeTarget = "reminderTime" }) {
                                Text("Local time ${PlannerDateUtils.formatTimeOfDay(reminderLocalHour * 60 + reminderLocalMinute)}")
                            }
                        }
                    }

                    // Quick reminder preset chips
                    val presets = listOf(
                        Triple(ReminderKind.ORDINARY, 60, "1h before"),
                        Triple(ReminderKind.ORDINARY, 1440, "1 day before"),
                        Triple(ReminderKind.PROGRESS_CHECKIN, 1440, "Check-in · 1d before"),
                        Triple(ReminderKind.PROGRESS_CHECKIN, 2880, "Check-in · 2d before")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (kind, offset, label) ->
                            val isSelected = draftReminders.any {
                                it.kind == kind && it.offsetMinutes == offset
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        draftReminders.removeAll {
                                            it.kind == kind && it.offsetMinutes == offset
                                        }
                                    } else {
                                        draftReminders.add(
                                            ReminderDraft(
                                                kind = kind,
                                                offsetMinutes = offset,
                                                localHour = reminderLocalHour,
                                                localMinute = reminderLocalMinute
                                            )
                                        )
                                    }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SkyBlue,
                                    selectedLabelColor = MidnightNavy
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = requestDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val trimmed = title.trim()
                            if (trimmed.isEmpty()) {
                                titleError = "Please enter a title."
                                return@Button
                            }
                            val syncedReminders = draftReminders.map {
                                it.copy(localHour = reminderLocalHour, localMinute = reminderLocalMinute)
                            }
                            val finalStartDate = if (selectedType == ItemType.PLAN) startDate.toIsoString() else null
                            val finalDueDate = when (selectedType) {
                                ItemType.PLAN -> {
                                    val safeDue = if (dueDate < startDate) startDate else dueDate
                                    safeDue.toIsoString()
                                }
                                ItemType.TASK -> dueDate.toIsoString()
                                ItemType.EVENT -> startDate.toIsoString()
                            }
                            val finalDueTime = if (selectedType != ItemType.EVENT && hasDueTime) {
                                dueHour * 60 + dueMinute
                            } else null

                            val finalEventStart = if (selectedType == ItemType.EVENT) {
                                startDate.toLocalEpochMillis(eventStartHour, eventStartMinute)
                            } else null
                            val finalEventEnd = if (selectedType == ItemType.EVENT) {
                                val rawEnd = startDate.toLocalEpochMillis(eventEndHour, eventEndMinute)
                                if (finalEventStart != null && rawEnd <= finalEventStart) {
                                    finalEventStart + 3_600_000L
                                } else rawEnd
                            } else null

                            if (existingItem != null) {
                                onSaveEdit(
                                    existingItem,
                                    selectedType,
                                    trimmed,
                                    notes.trim().takeIf { it.isNotEmpty() },
                                    finalStartDate,
                                    finalDueDate,
                                    finalDueTime,
                                    finalEventStart,
                                    finalEventEnd,
                                    if (selectedType == ItemType.TASK) parentPlanId else null,
                                    syncedReminders
                                )
                            } else {
                                onSaveNew(
                                    selectedType,
                                    trimmed,
                                    notes.trim().takeIf { it.isNotEmpty() },
                                    finalStartDate,
                                    finalDueDate,
                                    finalDueTime,
                                    finalEventStart,
                                    finalEventEnd,
                                    if (selectedType == ItemType.TASK) parentPlanId else null,
                                    draftSteps.toList(),
                                    syncedReminders
                                )
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepBlue,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("editor_save_button")
                    ) {
                        Text(if (isEditing) "Save changes" else "Save")
                    }
                }
    }

    // Custom Forget-Me-Not Stationery Date Picker modal
    if (activeDateTarget != null) {
        val currentTarget = activeDateTarget!!
        val initialDate = when (currentTarget) {
            "start" -> startDate
            "step" -> newStepDueDate ?: dueDate
            else -> dueDate
        }
        val dateTitle = when (currentTarget) {
            "start" -> if (selectedType == ItemType.EVENT) "Event date" else "Start date"
            "step" -> "Milestone due date"
            else -> if (selectedType == ItemType.PLAN) "Plan deadline" else "Due date"
        }
        BunnyMemoDatePickerModal(
            title = dateTitle,
            initialDate = initialDate,
            onDateSelected = { picked ->
                when (currentTarget) {
                    "start" -> {
                        startDate = picked
                        if (dueDate < picked) dueDate = picked
                    }
                    "due" -> dueDate = picked
                    "step" -> newStepDueDate = picked
                }
                activeDateTarget = null
            },
            onDismiss = { activeDateTarget = null }
        )
    }

    // Custom Forget-Me-Not Stationery Time Picker modal
    if (activeTimeTarget != null) {
        val currentTarget = activeTimeTarget!!
        val (initH, initM) = when (currentTarget) {
            "eventStart" -> eventStartHour to eventStartMinute
            "eventEnd" -> eventEndHour to eventEndMinute
            "reminderTime" -> reminderLocalHour to reminderLocalMinute
            else -> dueHour to dueMinute
        }
        val timeTitle = when (currentTarget) {
            "eventStart" -> "Event start time"
            "eventEnd" -> "Event end time"
            "reminderTime" -> "Reminder local time"
            else -> "Due time"
        }
        BunnyMemoTimePickerModal(
            title = timeTitle,
            initialHour = initH,
            initialMinute = initM,
            onTimeSelected = { h, m ->
                when (currentTarget) {
                    "eventStart" -> {
                        eventStartHour = h
                        eventStartMinute = m
                    }
                    "eventEnd" -> {
                        eventEndHour = h
                        eventEndMinute = m
                    }
                    "reminderTime" -> {
                        reminderLocalHour = h
                        reminderLocalMinute = m
                    }
                    else -> {
                        dueHour = h
                        dueMinute = m
                    }
                }
                activeTimeTarget = null
            },
            onDismiss = { activeTimeTarget = null }
        )
    }
}

@Composable
private fun PickerChipButton(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MidnightNavy
                )
            }
        }
    }
}
