package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CalendarDate
import com.example.data.ClassRepeatMode
import com.example.data.ClassScheduleItem
import com.example.data.PlannerDateUtils
import com.example.ui.components.BunnyPose
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.ModernSectionHeader
import com.example.ui.components.ResponsiveCardGrid
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.adaptiveHorizontalPadding
import com.example.ui.components.classColorPalette
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState

private val TIMETABLE_DAYS = listOf(1, 2, 3, 4, 5, 6, 0) // Mon..Sun

@Composable
fun ClassScheduleScreen(
    state: PlannerUiState.Ready,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onAddClassForDay: (dayOfWeek: Int?) -> Unit,
    onEditClass: (ClassScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = state.todayDate()
    val todayDayOfWeek = today.dayOfWeekIndex()
    val allClasses = state.classSchedules
    val weeklyClasses = remember(allClasses) {
        allClasses.filter { it.repeatModeEnum() == ClassRepeatMode.WEEKLY }
    }
    val monthlyClasses = remember(allClasses) {
        allClasses.filter { it.repeatModeEnum() == ClassRepeatMode.MONTHLY_DAY }
    }
    val weeklyClassesByDay = remember(weeklyClasses) {
        TIMETABLE_DAYS.associateWith { dayIdx ->
            weeklyClasses
                .filter { dayIdx in it.daysOfWeek }
                .sortedBy { it.startTimeMinutes }
        }
    }
    val notifiedCount = remember(allClasses) { allClasses.count { it.notifyEnabled } }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPad = adaptiveHorizontalPadding(maxWidth)
        val fitAllSevenDays = maxWidth >= 840.dp
        val subjectColumns = if (maxWidth >= 660.dp) 2 else 1

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1220.dp)
                .testTag("class_schedule_screen"),
            contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 16.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Editorial header
        item(key = "class_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "CLASS SCHEDULE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ),
                        color = MutedNavy
                    )
                    Text(
                        text = "Semester Timetable",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MidnightNavy
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModernPillBadge(
                            text = "${allClasses.size} ${if (allClasses.size == 1) "class" else "classes"}",
                            containerColor = SkyBlue.copy(alpha = 0.55f),
                            contentColor = DeepBlue
                        )
                        if (notifiedCount > 0) {
                            ModernPillBadge(
                                text = "$notifiedCount notified",
                                containerColor = CalmSageContainer,
                                contentColor = CalmSage
                            )
                        }
                    }
                }
                Button(
                    onClick = { onAddClassForDay(todayDayOfWeek) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepBlue,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("class_schedule_add_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Add class", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Notification permission prompt when class reminders are active
        if (!hasNotificationPermission && notifiedCount > 0) {
            item(key = "class_notif_permission") {
                ModernPanel(
                    containerColor = SkyBlue.copy(alpha = 0.35f),
                    borderColor = ForgetMeNotBlue.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow class alerts",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy
                            )
                            Text(
                                text = "Enable notifications so Bunny Memo can remind you before each class.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onRequestNotificationPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepBlue,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Allow", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Day-at-top Timetable Grid (Days across top, Subject + Time cells below each day)
        item(key = "timetable_grid_panel") {
            ModernPanel(
                shape = RoundedCornerShape(24.dp),
                elevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Weekly Grid",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MidnightNavy
                            )
                            Text(
                                text = if (fitAllSevenDays) {
                                    "Full week view · tap any class cell to edit or + Add below any day"
                                } else {
                                    "Swipe across days · tap a cell to edit or + Add below any day"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                        }
                    }

                    if (fitAllSevenDays) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TIMETABLE_DAYS.forEach { dayIndex ->
                                TimetableDayColumn(
                                    dayIndex = dayIndex,
                                    isToday = dayIndex == todayDayOfWeek,
                                    classes = weeklyClassesByDay[dayIndex].orEmpty(),
                                    onAddForDay = { onAddClassForDay(dayIndex) },
                                    onEditClass = onEditClass,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TIMETABLE_DAYS.forEach { dayIndex ->
                                TimetableDayColumn(
                                    dayIndex = dayIndex,
                                    isToday = dayIndex == todayDayOfWeek,
                                    classes = weeklyClassesByDay[dayIndex].orEmpty(),
                                    onAddForDay = { onAddClassForDay(dayIndex) },
                                    onEditClass = onEditClass,
                                    modifier = Modifier.width(136.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Day-of-Month Repeating Classes (if any exist)
        if (monthlyClasses.isNotEmpty()) {
            item(key = "monthly_classes_header") {
                ModernSectionHeader(
                    title = "Day-of-Month Classes",
                    count = monthlyClasses.size
                )
            }
            item(key = "monthly_classes_grid") {
                ResponsiveCardGrid(
                    items = monthlyClasses,
                    columns = subjectColumns
                ) { cls ->
                    SemesterClassSummaryCard(
                        schedule = cls,
                        today = today,
                        onClick = { onEditClass(cls) }
                    )
                }
            }
        }

        // Empty state or All Semester Subjects overview
        if (allClasses.isEmpty()) {
            item(key = "empty_class_schedule") {
                ModernPanel(
                    shape = RoundedCornerShape(24.dp),
                    elevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SmallBunnyIllustration(
                            pose = BunnyPose.GREETING,
                            size = 64.dp,
                            contentDesc = "Bunny ready for class schedule"
                        )
                        Text(
                            text = "No classes scheduled yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MidnightNavy
                        )
                        Text(
                            text = "Tap any day’s + Add cell above to add your subjects, class hours, semester months, and class notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                        Button(
                            onClick = { onAddClassForDay(1) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepBlue,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create first class")
                        }
                    }
                }
            }
        } else {
            item(key = "all_semester_classes_header") {
                ModernSectionHeader(
                    title = "All Semester Subjects",
                    count = allClasses.size
                )
            }
            item(key = "all_semester_classes_grid") {
                ResponsiveCardGrid(
                    items = allClasses,
                    columns = subjectColumns
                ) { cls ->
                    SemesterClassSummaryCard(
                        schedule = cls,
                        today = today,
                        onClick = { onEditClass(cls) }
                    )
                }
            }
        }
    }
    }
}

/**
 * Single vertical day column in the timetable grid:
 * Day pill header at the top, followed by stacked Subject + Time cells underneath,
 * and a quick "+ Add" cell at the bottom.
 */
@Composable
private fun TimetableDayColumn(
    dayIndex: Int,
    isToday: Boolean,
    classes: List<ClassScheduleItem>,
    onAddForDay: () -> Unit,
    onEditClass: (ClassScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isToday) SkyBlue.copy(alpha = 0.24f) else SoftIceSurface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isToday) 1.25.dp else 0.75.dp,
            color = if (isToday) ForgetMeNotBlue.copy(alpha = 0.65f) else SubtleBorder
        ),
        modifier = modifier.testTag("timetable_column_$dayIndex")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Day Header at the top
            Surface(
                color = if (isToday) DeepBlue else PureWhite,
                shape = RoundedCornerShape(12.dp),
                border = if (isToday) null else BorderStroke(0.75.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = PlannerDateUtils.weekdayShortLabel(dayIndex),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isToday) PureWhite else MidnightNavy
                    )
                    if (isToday) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = PureWhite.copy(alpha = 0.92f)
                        )
                    } else if (classes.isNotEmpty()) {
                        Text(
                            text = "${classes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }
            }

            // Cells below the day header
            if (classes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PureWhite.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy.copy(alpha = 0.8f)
                    )
                }
            } else {
                classes.forEach { cls ->
                    TimetableClassCell(
                        schedule = cls,
                        onClick = { onEditClass(cls) }
                    )
                }
            }

            // Quick "+ Add" cell at bottom of day column
            Surface(
                onClick = onAddForDay,
                color = PureWhite,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timetable_add_day_$dayIndex")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add class for ${PlannerDateUtils.weekdayShortLabel(dayIndex)}",
                        tint = DeepBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = DeepBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableClassCell(
    schedule: ClassScheduleItem,
    onClick: () -> Unit
) {
    val (fill, accent) = classColorPalette(schedule.colorKeyEnum())
    Surface(
        onClick = onClick,
        color = PureWhite,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timetable_cell_${schedule.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Soft pastel top accent strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(fill)
            )
            Column(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = schedule.subject,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MidnightNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = schedule.formattedTimeRange(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = DeepBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!schedule.room.isNullOrBlank()) {
                    Text(
                        text = schedule.room,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(
                    color = SubtleBorder.copy(alpha = 0.7f),
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${schedule.semesterMonths} mo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy
                    )
                    if (schedule.notifyEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Notification enabled",
                                tint = DeepBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = shortOffsetBadge(schedule.notifyOffsetMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepBlue
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = "Notifications off",
                            tint = MutedNavy.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SemesterClassSummaryCard(
    schedule: ClassScheduleItem,
    today: CalendarDate,
    onClick: () -> Unit
) {
    val (fill, accent) = classColorPalette(schedule.colorKeyEnum())
    val active = schedule.isActiveOnOrAfter(today)

    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        borderColor = accent.copy(alpha = 0.32f),
        elevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("semester_class_card_${schedule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = schedule.subject,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!active) {
                        ModernPillBadge(
                            text = "Ended",
                            containerColor = SoftIceSurface,
                            contentColor = MutedNavy
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${schedule.formattedTimeRange()} · ${schedule.repeatSummaryLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MutedNavy,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = schedule.formattedSemesterSpan(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }

                if (!schedule.room.isNullOrBlank() || schedule.notifyEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!schedule.room.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Room,
                                    contentDescription = null,
                                    tint = MutedNavy,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = schedule.room,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedNavy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (schedule.notifyEnabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = DeepBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = PlannerDateUtils.formatOffsetLabel(schedule.notifyOffsetMinutes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shortOffsetBadge(offsetMinutes: Int): String = when {
    offsetMinutes == 0 -> "Start"
    offsetMinutes >= 1440 -> "${offsetMinutes / 1440}d"
    offsetMinutes >= 60 -> "${offsetMinutes / 60}h"
    else -> "${offsetMinutes}m"
}
