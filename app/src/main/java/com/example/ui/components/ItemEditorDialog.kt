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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.BunnyFeedbackManager
import com.example.data.CalendarDate
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.PlannerReminder
import com.example.data.ReminderDraft
import com.example.data.ReminderKind
import com.example.data.StepDraft
import com.example.data.TaskCategory
import com.example.data.TaskPriority
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
        reminders: List<ReminderDraft>,
        imagePaths: List<String>,
        priority: TaskPriority,
        category: TaskCategory
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
        reminders: List<ReminderDraft>,
        imagePaths: List<String>,
        priority: TaskPriority,
        category: TaskCategory
    ) -> Unit
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val isEditing = existingItem != null
    val formKey = existingItem?.id
    var selectedType by remember(formKey) { mutableStateOf(existingItem?.itemType() ?: ItemType.TASK) }
    var selectedPriority by remember(formKey) {
        mutableStateOf(existingItem?.taskPriority() ?: TaskPriority.MEDIUM)
    }
    var selectedCategory by remember(formKey) {
        mutableStateOf(existingItem?.taskCategory() ?: TaskCategory.PERSONAL)
    }
    var title by remember(formKey) { mutableStateOf(existingItem?.title ?: "") }
    var notes by remember(formKey) { mutableStateOf(existingItem?.notes ?: "") }
    var titleError by remember(formKey) { mutableStateOf<String?>(null) }
    var dateTimeError by remember(formKey) { mutableStateOf<String?>(null) }

    // Date states
    var startDate by remember(formKey, prefillDate) {
        mutableStateOf(
            CalendarDate.parse(existingItem?.startDate)
                ?: existingItem?.startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                ?: prefillDate
        )
    }
    var dueDate by remember(formKey, prefillDate) {
        mutableStateOf(
            CalendarDate.parse(existingItem?.dueDate)
                ?: existingItem?.startAtUtcMillis?.let { CalendarDate.fromUtcMillis(it) }
                ?: if (selectedType == ItemType.PLAN) prefillDate.plusDays(3) else prefillDate
        )
    }

    // Direct typed time states
    var dueTimeText by remember(formKey) {
        mutableStateOf(
            existingItem?.dueTimeMinutes?.let { PlannerDateUtils.formatTimeOfDay(it) } ?: ""
        )
    }

    val initialEventStartCal = remember(formKey) {
        Calendar.getInstance(TimeZone.getDefault()).apply {
            if (existingItem?.startAtUtcMillis != null) {
                timeInMillis = existingItem.startAtUtcMillis
            } else {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
            }
        }
    }
    val initialEventEndCal = remember(formKey) {
        Calendar.getInstance(TimeZone.getDefault()).apply {
            if (existingItem?.endAtUtcMillis != null) {
                timeInMillis = existingItem.endAtUtcMillis
            } else {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 30)
            }
        }
    }
    var eventStartTimeText by remember(formKey) {
        mutableStateOf(
            PlannerDateUtils.formatTimeOfDay(
                initialEventStartCal.get(Calendar.HOUR_OF_DAY) * 60 + initialEventStartCal.get(Calendar.MINUTE)
            )
        )
    }
    var eventEndTimeText by remember(formKey) {
        mutableStateOf(
            PlannerDateUtils.formatTimeOfDay(
                initialEventEndCal.get(Calendar.HOUR_OF_DAY) * 60 + initialEventEndCal.get(Calendar.MINUTE)
            )
        )
    }

    // Linked parent plan for Task
    var parentPlanId by remember(formKey) { mutableStateOf(existingItem?.parentPlanId) }

    // Inline steps when creating a new Plan or Event
    val draftSteps = remember(formKey) { mutableStateListOf<StepDraft>() }
    var newStepTitle by remember(formKey) { mutableStateOf("") }
    var newStepDueDate by remember(formKey) { mutableStateOf<CalendarDate?>(null) }

    // Attached pictures for Tasks, Plans, and Events
    val draftImagePaths = remember(formKey) {
        mutableStateListOf<String>().apply {
            existingItem?.imagePaths?.let { addAll(it) }
        }
    }

    // Reminder drafts
    val draftReminders = remember(formKey) {
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
    val initialReminderMinutes = remember(formKey) {
        val h = existingReminders.firstOrNull()?.localHour ?: defaultReminderHour
        val m = existingReminders.firstOrNull()?.localMinute ?: defaultReminderMinute
        h * 60 + m
    }
    var reminderTimeText by remember(formKey) {
        mutableStateOf(PlannerDateUtils.formatTimeOfDay(initialReminderMinutes))
    }

    val parsedDueTimeMinutes = remember(dueTimeText) {
        PlannerDateUtils.parseFlexibleTimeMinutes(dueTimeText)
    }
    val parsedReminderMinutes = remember(reminderTimeText) {
        PlannerDateUtils.parseFlexibleTimeMinutes(reminderTimeText) ?: initialReminderMinutes
    }

    // Active date picker modal
    var activeDateTarget by remember { mutableStateOf<String?>(null) } // "start", "due", "step"

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
                            dateTimeError = null
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
                            DirectTimeTextField(
                                label = "Deadline time (optional)",
                                value = dueTimeText,
                                onValueChange = {
                                    dueTimeText = it
                                    dateTimeError = null
                                },
                                optional = true,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "editor_due_time_input"
                            )
                        }

                        ItemType.TASK -> {
                            PickerChipButton(
                                label = "Due date",
                                value = PlannerDateUtils.formatShortDate(dueDate),
                                icon = Icons.Outlined.CalendarToday,
                                onClick = { activeDateTarget = "due" },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DirectTimeTextField(
                                label = "Due time (optional)",
                                value = dueTimeText,
                                onValueChange = {
                                    dueTimeText = it
                                    dateTimeError = null
                                },
                                optional = true,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "editor_due_time_input"
                            )

                            TaskPrioritySelector(
                                selected = selectedPriority,
                                onSelect = {
                                    feedback.onLightTick()
                                    selectedPriority = it
                                },
                                tagPrefix = "editor_priority"
                            )

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
                                DirectTimeTextField(
                                    label = "Starts",
                                    value = eventStartTimeText,
                                    onValueChange = {
                                        eventStartTimeText = it
                                        dateTimeError = null
                                    },
                                    optional = false,
                                    placeholder = "7:00 PM",
                                    modifier = Modifier.weight(1f),
                                    testTag = "editor_event_start_time_input"
                                )
                                DirectTimeTextField(
                                    label = "Ends",
                                    value = eventEndTimeText,
                                    onValueChange = {
                                        eventEndTimeText = it
                                        dateTimeError = null
                                    },
                                    optional = false,
                                    placeholder = "8:30 PM",
                                    modifier = Modifier.weight(1f),
                                    testTag = "editor_event_end_time_input"
                                )
                            }
                        }
                    }

                    if (!dateTimeError.isNullOrBlank()) {
                        Surface(
                            color = com.example.ui.theme.OverdueContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_datetime_error")
                        ) {
                            Text(
                                text = dateTimeError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = com.example.ui.theme.OverdueTerracotta,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Color-coded category tag for Tasks, Plans, and Events
                    TaskCategorySelector(
                        selected = selectedCategory,
                        onSelect = {
                            feedback.onLightTick()
                            selectedCategory = it
                        },
                        tagPrefix = "editor_category"
                    )

                    // Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(1000) },
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Picture attachments (Tasks, Plans, Events)
                    PhotoAttachmentPickerSection(
                        imagePaths = draftImagePaths,
                        onAddPaths = { added ->
                            added.forEach { path ->
                                if (path !in draftImagePaths && draftImagePaths.size < com.example.data.PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM) {
                                    draftImagePaths.add(path)
                                }
                            }
                        },
                        onRemovePath = { path ->
                            draftImagePaths.remove(path)
                        }
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
                                    imageVector = Icons.Rounded.Add,
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
                                shape = MaterialTheme.shapes.small,
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
                                            imageVector = Icons.Rounded.Close,
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
                    }

                    if (selectedType != ItemType.EVENT && dueTimeText.isBlank()) {
                        DirectTimeTextField(
                            label = "Reminder time",
                            value = reminderTimeText,
                            onValueChange = {
                                reminderTimeText = it
                                dateTimeError = null
                            },
                            optional = false,
                            placeholder = "9:00 AM",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "editor_reminder_time_input"
                        )
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
                                                localHour = parsedReminderMinutes / 60,
                                                localMinute = parsedReminderMinutes % 60
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
                        shape = MaterialTheme.shapes.medium,
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
                            if (selectedType == ItemType.PLAN && dueDate < startDate) {
                                dateTimeError = "Deadline must be on or after the start date."
                                return@Button
                            }
                            if (selectedType != ItemType.EVENT && dueTimeText.isNotBlank() && parsedDueTimeMinutes == null) {
                                dateTimeError = "Enter a valid time (e.g. 2:30 PM or 14:30), or clear it for Anytime."
                                return@Button
                            }
                            val parsedEventStart = if (selectedType == ItemType.EVENT) {
                                PlannerDateUtils.parseFlexibleTimeMinutes(eventStartTimeText)
                            } else null
                            val parsedEventEnd = if (selectedType == ItemType.EVENT) {
                                PlannerDateUtils.parseFlexibleTimeMinutes(eventEndTimeText)
                            } else null
                            if (selectedType == ItemType.EVENT) {
                                if (parsedEventStart == null || parsedEventEnd == null) {
                                    dateTimeError = "Enter valid start and end times (e.g. 7:00 PM or 19:00)."
                                    return@Button
                                }
                                if (parsedEventEnd <= parsedEventStart) {
                                    dateTimeError = "Event end time must be after start time."
                                    return@Button
                                }
                            }
                            if (selectedType != ItemType.EVENT && dueTimeText.isBlank() &&
                                PlannerDateUtils.parseFlexibleTimeMinutes(reminderTimeText) == null
                            ) {
                                dateTimeError = "Enter a valid reminder time (e.g. 9:00 AM or 09:00)."
                                return@Button
                            }
                            dateTimeError = null

                            val remHour = parsedReminderMinutes / 60
                            val remMinute = parsedReminderMinutes % 60
                            val syncedReminders = draftReminders.map {
                                it.copy(localHour = remHour, localMinute = remMinute)
                            }
                            val finalStartDate = if (selectedType == ItemType.PLAN) startDate.toIsoString() else null
                            val finalDueDate = when (selectedType) {
                                ItemType.PLAN -> dueDate.toIsoString()
                                ItemType.TASK -> dueDate.toIsoString()
                                ItemType.EVENT -> startDate.toIsoString()
                            }
                            val finalDueTime = if (selectedType != ItemType.EVENT) {
                                parsedDueTimeMinutes
                            } else null

                            val finalEventStart = if (selectedType == ItemType.EVENT && parsedEventStart != null) {
                                startDate.toLocalEpochMillis(parsedEventStart / 60, parsedEventStart % 60)
                            } else null
                            val finalEventEnd = if (selectedType == ItemType.EVENT && parsedEventEnd != null) {
                                startDate.toLocalEpochMillis(parsedEventEnd / 60, parsedEventEnd % 60)
                            } else null

                            val pendingStepTitle = newStepTitle.trim()
                            val finalSteps = buildList {
                                addAll(draftSteps)
                                if (pendingStepTitle.isNotEmpty()) {
                                    add(
                                        StepDraft(
                                            title = pendingStepTitle,
                                            dueDate = newStepDueDate?.toIsoString()
                                        )
                                    )
                                }
                            }

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
                                    syncedReminders,
                                    draftImagePaths.toList(),
                                    selectedPriority,
                                    selectedCategory
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
                                    finalSteps,
                                    syncedReminders,
                                    draftImagePaths.toList(),
                                    selectedPriority,
                                    selectedCategory
                                )
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepBlue,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium,
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
                dateTimeError = null
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
}

@Composable
internal fun DirectTimeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    optional: Boolean = false,
    placeholder: String = "e.g. 2:30 PM or 14:30",
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val parsedMinutes = remember(value) { PlannerDateUtils.parseFlexibleTimeMinutes(value) }
    val showError = if (optional) {
        value.isNotBlank() && parsedMinutes == null
    } else {
        parsedMinutes == null
    }
    val helperText = when {
        value.isBlank() && optional -> "Leave blank for Anytime"
        showError -> "Use e.g. 2:30 PM or 14:30"
        parsedMinutes != null -> PlannerDateUtils.formatTimeOfDay(parsedMinutes)
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(16)) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (optional && value.isNotBlank()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear time",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        singleLine = true,
        isError = showError,
        supportingText = helperText?.let { msg -> { Text(msg) } },
        shape = MaterialTheme.shapes.medium,
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    )
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
        shape = MaterialTheme.shapes.medium,
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
