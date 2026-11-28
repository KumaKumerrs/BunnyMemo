package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CalendarDate
import com.example.data.ClassColorKey
import com.example.data.ClassRepeatMode
import com.example.data.ClassScheduleItem
import com.example.data.PlannerDateUtils
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder

private val WEEKDAY_ORDER = listOf(1, 2, 3, 4, 5, 6, 0) // Mon..Sun

private val NOTIFY_PRESETS = listOf(
    0 to "At start",
    5 to "5 min before",
    10 to "10 min before",
    15 to "15 min before",
    30 to "30 min before",
    60 to "1 hour before",
    120 to "2 hours before",
    1440 to "1 day before"
)

private val MONTH_PRESETS = listOf(1, 3, 4, 5, 6)

/**
 * Custom forget-me-not animated modal for adding or editing a customizable semester class.
 * Supports weekly day columns, specific day-of-month repetition, customizable semester
 * duration (1-12 months), custom start/end times, and per-class notification timing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassScheduleEditorModal(
    existing: ClassScheduleItem?,
    prefillDayOfWeek: Int?,
    prefillDate: CalendarDate,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        subject: String,
        room: String?,
        repeatMode: ClassRepeatMode,
        daysOfWeek: List<Int>,
        dayOfMonth: Int,
        startTimeMinutes: Int,
        endTimeMinutes: Int,
        semesterStartDate: String,
        semesterMonths: Int,
        notifyEnabled: Boolean,
        notifyOffsetMinutes: Int,
        colorKey: ClassColorKey
    ) -> Unit,
    onDelete: ((ClassScheduleItem) -> Unit)? = null
) {
    val isEditing = existing != null
    var subject by remember(existing?.id) { mutableStateOf(existing?.subject ?: "") }
    var room by remember(existing?.id) { mutableStateOf(existing?.room ?: "") }
    var repeatMode by remember(existing?.id) {
        mutableStateOf(existing?.repeatModeEnum() ?: ClassRepeatMode.WEEKLY)
    }
    var selectedDays by remember(existing?.id, prefillDayOfWeek) {
        val initial = existing?.daysOfWeek?.toSet()
            ?: setOf(prefillDayOfWeek ?: prefillDate.dayOfWeekIndex())
        mutableStateOf(initial.ifEmpty { setOf(1) })
    }
    var semesterStartDate by remember(existing?.id, prefillDate) {
        mutableStateOf(existing?.semesterStartCalendarDate() ?: prefillDate)
    }
    var dayOfMonth by remember(existing?.id, prefillDate) {
        mutableIntStateOf(existing?.dayOfMonth ?: prefillDate.day)
    }
    var semesterMonths by remember(existing?.id) {
        mutableIntStateOf((existing?.semesterMonths ?: 4).coerceIn(1, 12))
    }
    var startTimeMinutes by remember(existing?.id) {
        mutableIntStateOf(existing?.startTimeMinutes ?: (9 * 60))
    }
    var endTimeMinutes by remember(existing?.id) {
        mutableIntStateOf(existing?.endTimeMinutes ?: (10 * 60 + 30))
    }
    var notifyEnabled by remember(existing?.id) {
        mutableStateOf(existing?.notifyEnabled ?: true)
    }
    var notifyOffsetMinutes by remember(existing?.id) {
        mutableIntStateOf(existing?.notifyOffsetMinutes ?: 15)
    }
    var colorKey by remember(existing?.id) {
        mutableStateOf(existing?.colorKeyEnum() ?: ClassColorKey.SKY)
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var activeTimePicker by remember { mutableStateOf<String?>(null) } // "start" or "end"
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val endInclusiveDate = remember(semesterStartDate, semesterMonths) {
        semesterStartDate.plusMonthsClamped(semesterMonths).plusDays(-1)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DeepBlue,
        unfocusedBorderColor = SubtleBorder,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = PureWhite,
        focusedTextColor = MidnightNavy,
        unfocusedTextColor = MidnightNavy
    )

    BunnyMemoModalContainer(
        title = if (isEditing) "Edit Class" else "Add Class to Timetable",
        subtitle = "Customize subject, time, day & semester repeat",
        onDismiss = onDismiss,
        maxWidth = 520.dp,
        headerLeading = {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Subject & Room
            OutlinedTextField(
                value = subject,
                onValueChange = {
                    subject = it
                    validationError = null
                },
                label = { Text("Subject name") },
                placeholder = { Text("e.g., Calculus II, Art History, Biology Lab") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("class_subject_input")
            )

            OutlinedTextField(
                value = room,
                onValueChange = { room = it },
                label = { Text("Room or instructor (optional)") },
                placeholder = { Text("e.g., Room 204 · Prof. Cruz") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("class_room_input")
            )

            // Repeat Pattern: Weekly Days vs Day of Month
            TonalInsetPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Schedule day",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                    ModernSegmentedToggle(
                        options = listOf(
                            ClassRepeatMode.WEEKLY to "Weekly days",
                            ClassRepeatMode.MONTHLY_DAY to "Day of month"
                        ),
                        selected = repeatMode,
                        onSelect = {
                            repeatMode = it
                            validationError = null
                        },
                        tagPrefix = "class_repeat_mode"
                    )

                    if (repeatMode == ClassRepeatMode.WEEKLY) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            WEEKDAY_ORDER.forEach { dayIdx ->
                                val selected = dayIdx in selectedDays
                                Surface(
                                    onClick = {
                                        selectedDays = if (selected && selectedDays.size > 1) {
                                            selectedDays - dayIdx
                                        } else {
                                            selectedDays + dayIdx
                                        }
                                        validationError = null
                                    },
                                    color = if (selected) DeepBlue else PureWhite,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) DeepBlue else SubtleBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("class_weekday_$dayIdx")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = PlannerDateUtils.weekdayShortLabel(dayIdx).take(3),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) PureWhite else MidnightNavy,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Customizable Day of Month (1..31)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Repeats every ${PlannerDateUtils.formatOrdinalDay(dayOfMonth)} of the month",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MidnightNavy
                                )
                                Text(
                                    text = "Adjust to any day (1–31)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedNavy
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = { dayOfMonth = if (dayOfMonth <= 1) 31 else dayOfMonth - 1 },
                                    color = PureWhite,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("class_day_of_month_minus")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Remove,
                                            contentDescription = "Previous day of month",
                                            tint = MidnightNavy,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = DeepBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Surface(
                                    onClick = { dayOfMonth = if (dayOfMonth >= 31) 1 else dayOfMonth + 1 },
                                    color = PureWhite,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("class_day_of_month_plus")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Next day of month",
                                            tint = MidnightNavy,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 5, 10, 15, 20, 25, 30).forEach { presetDay ->
                                val isSel = dayOfMonth == presetDay
                                FilterChip(
                                    selected = isSel,
                                    onClick = { dayOfMonth = presetDay },
                                    label = { Text(PlannerDateUtils.formatOrdinalDay(presetDay)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DeepBlue,
                                        selectedLabelColor = PureWhite,
                                        containerColor = PureWhite,
                                        labelColor = MidnightNavy
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSel,
                                        borderColor = SubtleBorder,
                                        selectedBorderColor = DeepBlue
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Class Time (Start & End)
            TonalInsetPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Class time",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TimeSelectorChip(
                            label = "Starts",
                            value = PlannerDateUtils.formatTimeOfDay(startTimeMinutes),
                            onClick = { activeTimePicker = "start" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("class_start_time_button")
                        )
                        TimeSelectorChip(
                            label = "Ends",
                            value = PlannerDateUtils.formatTimeOfDay(endTimeMinutes),
                            onClick = { activeTimePicker = "end" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("class_end_time_button")
                        )
                    }
                }
            }

            // Semester Duration (Repeatable by number of months)
            TonalInsetPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Semester repeat",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy
                            )
                            Text(
                                text = "${PlannerDateUtils.formatCompactDate(semesterStartDate)} – ${PlannerDateUtils.formatCompactDate(endInclusiveDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                        }
                        Surface(
                            onClick = { showStartDatePicker = true },
                            color = PureWhite,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.testTag("class_semester_start_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = "Choose semester start date",
                                    tint = DeepBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Starts ${PlannerDateUtils.formatCompactDate(semesterStartDate)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MidnightNavy
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Months in semester",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = { semesterMonths = (semesterMonths - 1).coerceAtLeast(1) },
                                color = PureWhite,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("class_months_minus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Fewer months",
                                        tint = MidnightNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$semesterMonths ${if (semesterMonths == 1) "month" else "months"}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = DeepBlue
                            )
                            Surface(
                                onClick = { semesterMonths = (semesterMonths + 1).coerceAtMost(12) },
                                color = PureWhite,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("class_months_plus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "More months",
                                        tint = MidnightNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MONTH_PRESETS.forEach { months ->
                            val isSel = semesterMonths == months
                            FilterChip(
                                selected = isSel,
                                onClick = { semesterMonths = months },
                                label = { Text("$months mo") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepBlue,
                                    selectedLabelColor = PureWhite,
                                    containerColor = PureWhite,
                                    labelColor = MidnightNavy
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSel,
                                    borderColor = SubtleBorder,
                                    selectedBorderColor = DeepBlue
                                )
                            )
                        }
                    }
                }
            }

            // Notification Preferences
            TonalInsetPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (notifyEnabled) Icons.Rounded.NotificationsActive else Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                tint = if (notifyEnabled) DeepBlue else MutedNavy,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Class notification",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MidnightNavy
                                )
                                Text(
                                    text = if (notifyEnabled) {
                                        "Alert ${PlannerDateUtils.formatOffsetLabel(notifyOffsetMinutes).lowercase()}"
                                    } else {
                                        "Notifications turned off for this class"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedNavy
                                )
                            }
                        }
                        Switch(
                            checked = notifyEnabled,
                            onCheckedChange = { notifyEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = DeepBlue,
                                uncheckedThumbColor = PureWhite,
                                uncheckedTrackColor = SubtleBorder
                            ),
                            modifier = Modifier.testTag("class_notify_switch")
                        )
                    }

                    AnimatedVisibility(visible = notifyEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "When should Bunny Memo notify you?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedNavy
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                NOTIFY_PRESETS.forEach { (mins, label) ->
                                    val isSel = notifyOffsetMinutes == mins
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { notifyOffsetMinutes = mins },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DeepBlue,
                                            selectedLabelColor = PureWhite,
                                            containerColor = PureWhite,
                                            labelColor = MidnightNavy
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSel,
                                            borderColor = SubtleBorder,
                                            selectedBorderColor = DeepBlue
                                        ),
                                        modifier = Modifier.testTag("class_notify_offset_$mins")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Soft Pastel Cell Color
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Cell tint",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClassColorKey.entries.forEach { key ->
                        val (fill, accent) = classColorPalette(key)
                        val isSel = colorKey == key
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(fill)
                                .border(
                                    width = if (isSel) 2.5.dp else 1.dp,
                                    color = if (isSel) accent else SubtleBorder,
                                    shape = CircleShape
                                )
                                .clickable { colorKey = key }
                                .testTag("class_color_${key.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = OverdueTerracotta
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Footer buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isEditing && onDelete != null) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.testTag("class_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = OverdueTerracotta,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = OverdueTerracotta)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = requestDismiss,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Text("Cancel", color = MidnightNavy)
                }
                Button(
                    enabled = !isSaving,
                    onClick = {
                        val trimmed = subject.trim()
                        if (trimmed.isBlank()) {
                            validationError = "Please enter a subject name."
                            return@Button
                        }
                        if (repeatMode == ClassRepeatMode.WEEKLY && selectedDays.isEmpty()) {
                            validationError = "Pick at least one day of the week."
                            return@Button
                        }
                        val safeEnd = if (endTimeMinutes <= startTimeMinutes) {
                            (startTimeMinutes + 60).coerceAtMost(23 * 60 + 55)
                        } else {
                            endTimeMinutes
                        }
                        onSave(
                            trimmed,
                            room.trim().takeIf { it.isNotEmpty() },
                            repeatMode,
                            selectedDays.sorted(),
                            dayOfMonth.coerceIn(1, 31),
                            startTimeMinutes,
                            safeEnd,
                            semesterStartDate.toIsoString(),
                            semesterMonths.coerceIn(1, 12),
                            notifyEnabled,
                            notifyOffsetMinutes,
                            colorKey
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = PureWhite
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("class_save_button")
                ) {
                    Text(if (isEditing) "Save changes" else "Add class")
                }
            }
        }
    }

    if (showStartDatePicker) {
        BunnyMemoDatePickerModal(
            title = "Semester start date",
            initialDate = semesterStartDate,
            onDateSelected = { picked ->
                semesterStartDate = picked
                if (repeatMode == ClassRepeatMode.MONTHLY_DAY && existing == null) {
                    dayOfMonth = picked.day
                }
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (activeTimePicker != null) {
        val pickingStart = activeTimePicker == "start"
        val currentMins = if (pickingStart) startTimeMinutes else endTimeMinutes
        BunnyMemoTimePickerModal(
            title = if (pickingStart) "Class start time" else "Class end time",
            initialHour = currentMins / 60,
            initialMinute = currentMins % 60,
            onTimeSelected = { h, m ->
                val chosen = h * 60 + m
                if (pickingStart) {
                    startTimeMinutes = chosen
                    if (endTimeMinutes <= chosen) {
                        endTimeMinutes = (chosen + 60).coerceAtMost(23 * 60 + 55)
                    }
                } else {
                    endTimeMinutes = if (chosen <= startTimeMinutes) {
                        (startTimeMinutes + 30).coerceAtMost(23 * 60 + 55)
                    } else {
                        chosen
                    }
                }
                activeTimePicker = null
            },
            onDismiss = { activeTimePicker = null }
        )
    }

    if (showDeleteConfirm && existing != null && onDelete != null) {
        BunnyMemoConfirmModal(
            title = "Remove class?",
            message = "Remove “${existing.subject}” from your semester timetable and cancel its class reminders?",
            confirmLabel = "Remove",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete(existing)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun TimeSelectorChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = PureWhite,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MidnightNavy
                )
            }
        }
    }
}

/**
 * Shared airy pastel palette mapping for class timetable cells (zero dark-on-darker clutter).
 * Returns Pair(softContainerFill, accentColor).
 */
fun classColorPalette(key: ClassColorKey): Pair<Color, Color> = when (key) {
    ClassColorKey.SKY -> SkyBlue.copy(alpha = 0.45f) to DeepBlue
    ClassColorKey.CORNFLOWER -> SoftIceSurface to ForgetMeNotBlue
    ClassColorKey.SAGE -> CalmSageContainer to CalmSage
    ClassColorKey.POLLEN -> PollenSoft to PollenYellow
}
