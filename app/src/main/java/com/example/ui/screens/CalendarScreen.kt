package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.CalendarDate
import com.example.data.CalendarInvite
import com.example.data.ClassScheduleItem
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.TaskCategory
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyPose
import com.example.ui.components.FlowerProgressRow
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.ModernSegmentedToggle
import com.example.ui.components.MonthlyBunnyAlmanacModal
import com.example.ui.components.SharedScheduleCard
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.TaskCategoryBadge
import com.example.ui.components.TaskCategoryFilter
import com.example.ui.components.TaskCategoryFilterBar
import com.example.ui.components.TaskPriorityBadge
import com.example.ui.components.adaptiveHorizontalPadding
import com.example.ui.components.classColorPalette
import com.example.ui.components.rememberReducedMotion
import com.example.ui.components.selectedPanelBorder
import com.example.ui.components.selectedPanelContainer
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueContainer
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SharedInviteAccent
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState

private enum class CalendarMode { MONTH, WEEK }

private data class CalendarDayBucket(
    val items: List<PlannerItem>,
    val classes: List<ClassScheduleItem>,
    val invites: List<CalendarInvite>,
    val hasEvent: Boolean,
    val hasPlan: Boolean,
    val hasTask: Boolean,
    val hasCompletedDailyTask: Boolean,
    val hasOverdue: Boolean,
    val previewLabel: String?
) {
    val hasClass: Boolean get() = classes.isNotEmpty()
    val hasSharedInvite: Boolean get() = invites.isNotEmpty()
    val hasAny: Boolean get() = items.isNotEmpty() || classes.isNotEmpty() || invites.isNotEmpty()
}

private fun buildDayBucket(
    date: CalendarDate,
    state: PlannerUiState.Ready,
    overdueIds: Set<String>,
    invitesByDateIso: Map<String, List<CalendarInvite>>
): CalendarDayBucket {
    val itemsOnDay = state.items.filter { it.appearsOnDate(date) }
    val classesOnDay = state.classesOnDate(date)
    val invitesOnDay = invitesByDateIso[date.toIsoString()].orEmpty()
    var hasEvent = false
    var hasPlan = false
    var hasTask = false
    var hasCompletedDailyTask = false
    var hasOverdue = false
    for (item in itemsOnDay) {
        when (item.itemType()) {
            ItemType.EVENT -> hasEvent = true
            ItemType.PLAN -> hasPlan = true
            ItemType.TASK -> {
                hasTask = true
                if (item.completed) hasCompletedDailyTask = true
            }
        }
        if (item.id in overdueIds) hasOverdue = true
    }
    if (!hasCompletedDailyTask) {
        hasCompletedDailyTask = state.steps.any { step ->
            step.completed && CalendarDate.parse(step.dueDate) == date
        }
    }
    val previewLabel = itemsOnDay.firstOrNull()?.title
        ?: classesOnDay.firstOrNull()?.subject
        ?: invitesOnDay.firstOrNull()?.title
    return CalendarDayBucket(
        items = itemsOnDay,
        classes = classesOnDay,
        invites = invitesOnDay,
        hasEvent = hasEvent,
        hasPlan = hasPlan,
        hasTask = hasTask,
        hasCompletedDailyTask = hasCompletedDailyTask,
        hasOverdue = hasOverdue,
        previewLabel = previewLabel
    )
}

@Composable
fun CalendarScreen(
    state: PlannerUiState.Ready,
    selectedDate: CalendarDate,
    selectedItemId: String?,
    onSelectDate: (CalendarDate) -> Unit,
    onSelectItem: (String) -> Unit,
    onAddForDate: (CalendarDate) -> Unit,
    onEditClass: (ClassScheduleItem) -> Unit = {},
    calendarInvites: List<CalendarInvite> = emptyList(),
    currentUserId: String? = null,
    onInviteFriendClick: (CalendarDate) -> Unit = {},
    onOpenSharedInvite: (CalendarInvite) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val today = state.todayDate()
    val reducedMotion = rememberReducedMotion()
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    var visibleMonth by remember(selectedDate.year, selectedDate.month) {
        mutableStateOf(CalendarDate(selectedDate.year, selectedDate.month, 1))
    }
    var showBunnyAlmanac by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf(TaskCategoryFilter.ALL) }

    val invitesByDateIso = remember(calendarInvites) {
        calendarInvites.groupBy { it.plannedDate }
    }

    // Precompute overdue IDs once per data/day snapshot instead of inside every calendar cell
    val overdueItemIds = remember(state.items, state.steps, today) {
        state.items.mapNotNull { item ->
            if (state.progressFor(item).overdueCount > 0) item.id else null
        }.toSet()
    }

    val allDayItems = remember(state.items, selectedDate) {
        state.items
            .filter { it.appearsOnDate(selectedDate) }
            .sortedWith(
                compareBy<PlannerItem> {
                    when (it.itemType()) {
                        ItemType.EVENT -> 0
                        ItemType.TASK -> 1
                        ItemType.PLAN -> 2
                    }
                }.thenBy {
                    if (it.itemType() == ItemType.TASK) it.taskPriority().sortRank else 0
                }.thenBy { it.startAtUtcMillis ?: (it.dueTimeMinutes?.toLong() ?: Long.MAX_VALUE) }
            )
    }

    val categoryCounts = remember(allDayItems) {
        buildMap {
            put(TaskCategoryFilter.ALL, allDayItems.size)
            TaskCategory.entries.forEach { cat ->
                TaskCategoryFilter.entries.firstOrNull { it.category == cat }?.let { key ->
                    put(key, allDayItems.count { it.taskCategory() == cat })
                }
            }
        }
    }

    val selectedCat = categoryFilter.category
    val dayItems = remember(allDayItems, selectedCat) {
        if (selectedCat == null) allDayItems else allDayItems.filter { it.taskCategory() == selectedCat }
    }

    val dayClasses = remember(state.classSchedules, selectedDate) {
        state.classesOnDate(selectedDate)
    }
    val dayInvites = remember(invitesByDateIso, selectedDate) {
        invitesByDateIso[selectedDate.toIsoString()].orEmpty()
    }
    val totalDayEntries = allDayItems.size + dayClasses.size + dayInvites.size

    val weekTimingsDates = remember(selectedDate, mode) {
        if (mode == CalendarMode.WEEK) PlannerDateUtils.buildWeekDates(selectedDate) else emptyList()
    }
    val weekTimingsBuckets = remember(weekTimingsDates, state.items, state.classSchedules, overdueItemIds) {
        weekTimingsDates.associateWith { date ->
            val itemsForDay = state.items
                .filter { it.appearsOnDate(date) }
                .sortedBy { it.startAtUtcMillis ?: (it.dueTimeMinutes?.toLong() ?: Long.MAX_VALUE) }
            itemsForDay to state.classesOnDate(date)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPad = adaptiveHorizontalPadding(maxWidth)
        val useSideBySide = maxWidth >= 760.dp
        val isRoomyCells = maxWidth >= 600.dp

        if (useSideBySide) {
            // Canonical Tablet Side-by-Side Layout: Persistent Calendar Panel (Left) + Scrollable Agenda (Right)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1220.dp)
                    .padding(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.12f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 14.dp, bottom = 100.dp)
                ) {
                    CalendarGridPanel(
                        state = state,
                        mode = mode,
                        visibleMonth = visibleMonth,
                        selectedDate = selectedDate,
                        today = today,
                        overdueItemIds = overdueItemIds,
                        invitesByDateIso = invitesByDateIso,
                        reducedMotion = reducedMotion,
                        roomyCells = isRoomyCells,
                        onModeChange = {
                            feedback.onLightTick()
                            mode = it
                        },
                        onVisibleMonthChange = {
                            feedback.onLightTick()
                            visibleMonth = it
                        },
                        onSelectDate = {
                            feedback.onLightTick()
                            onSelectDate(it)
                        },
                        onOpenAlmanac = {
                            feedback.onPopOpen()
                            showBunnyAlmanac = true
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.88f)
                        .fillMaxHeight()
                        .testTag("calendar_screen_list"),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "selected_day_header") {
                        SelectedDayHeaderRow(
                            selectedDate = selectedDate,
                            totalDayEntries = totalDayEntries,
                            onInviteFriend = {
                                feedback.onPopOpen()
                                onInviteFriendClick(selectedDate)
                            },
                            onAddForDate = {
                                feedback.onPopOpen()
                                onAddForDate(selectedDate)
                            }
                        )
                    }

                    if (allDayItems.isNotEmpty()) {
                        item(key = "calendar_category_filter_wide") {
                            TaskCategoryFilterBar(
                                selectedFilter = categoryFilter,
                                counts = categoryCounts,
                                onSelectFilter = {
                                    feedback.onLightTick()
                                    categoryFilter = it
                                },
                                tagPrefix = "calendar_category_filter"
                            )
                        }
                    }

                    if (totalDayEntries == 0) {
                        item(key = "empty_selected_day") {
                            EmptyDayPanel(selectedDate = selectedDate)
                        }
                    } else if (selectedCat != null && dayItems.isEmpty() && dayInvites.isEmpty()) {
                        item(key = "filtered_empty_selected_day_wide") {
                            FilteredEmptyDayPanel(
                                filterLabel = categoryFilter.label,
                                onClearFilter = {
                                    feedback.onLightTick()
                                    categoryFilter = TaskCategoryFilter.ALL
                                }
                            )
                        }
                    } else {
                        if (dayInvites.isNotEmpty()) {
                            item(key = "calendar_shared_header_wide") {
                                Text(
                                    text = "Shared schedules",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = DeepBlue,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            items(dayInvites, key = { "cal_invite_${it.id}" }) { invite ->
                                SharedScheduleCard(
                                    invite = invite,
                                    currentUserId = currentUserId,
                                    onProfileIconClick = {
                                        feedback.onPopOpen()
                                        onOpenSharedInvite(invite)
                                    },
                                    onCardClick = {
                                        feedback.onPopOpen()
                                        onOpenSharedInvite(invite)
                                    }
                                )
                            }
                        }
                        if (dayClasses.isNotEmpty() && selectedCat == null) {
                            items(dayClasses, key = { "cal_cls_${it.id}" }) { cls ->
                                CalendarClassAgendaCard(
                                    schedule = cls,
                                    onClick = { onEditClass(cls) }
                                )
                            }
                        }
                        items(dayItems, key = { "cal_item_${it.id}" }) { item ->
                            CalendarAgendaCard(
                                item = item,
                                state = state,
                                isSelected = item.id == selectedItemId,
                                onClick = { onSelectItem(item.id) }
                            )
                        }
                    }

                    if (mode == CalendarMode.WEEK) {
                        item(key = "week_timings_title") {
                            Text(
                                text = "Week timings",
                                style = MaterialTheme.typography.titleMedium,
                                color = MidnightNavy,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                        items(weekTimingsDates, key = { "wk_day_${it.toIsoString()}" }) { date ->
                            val (itemsForDay, classesForDay) = weekTimingsBuckets[date] ?: (emptyList<PlannerItem>() to emptyList<ClassScheduleItem>())
                            WeekTimingsDayRow(
                                date = date,
                                isToday = date == today,
                                items = itemsForDay,
                                classes = classesForDay,
                                onSelectDate = { onSelectDate(date) },
                                onSelectItem = onSelectItem,
                                onEditClass = onEditClass
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("calendar_screen_list"),
                contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 14.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "calendar_controls") {
                    CalendarGridPanel(
                        state = state,
                        mode = mode,
                        visibleMonth = visibleMonth,
                        selectedDate = selectedDate,
                        today = today,
                        overdueItemIds = overdueItemIds,
                        invitesByDateIso = invitesByDateIso,
                        reducedMotion = reducedMotion,
                        roomyCells = isRoomyCells,
                        onModeChange = {
                            feedback.onLightTick()
                            mode = it
                        },
                        onVisibleMonthChange = {
                            feedback.onLightTick()
                            visibleMonth = it
                        },
                        onSelectDate = {
                            feedback.onLightTick()
                            onSelectDate(it)
                        },
                        onOpenAlmanac = {
                            feedback.onPopOpen()
                            showBunnyAlmanac = true
                        }
                    )
                }

                item(key = "selected_day_header") {
                    SelectedDayHeaderRow(
                        selectedDate = selectedDate,
                        totalDayEntries = totalDayEntries,
                        onInviteFriend = {
                            feedback.onPopOpen()
                            onInviteFriendClick(selectedDate)
                        },
                        onAddForDate = {
                            feedback.onPopOpen()
                            onAddForDate(selectedDate)
                        }
                    )
                }

                if (allDayItems.isNotEmpty()) {
                    item(key = "calendar_category_filter_compact") {
                        TaskCategoryFilterBar(
                            selectedFilter = categoryFilter,
                            counts = categoryCounts,
                            onSelectFilter = {
                                feedback.onLightTick()
                                categoryFilter = it
                            },
                            tagPrefix = "calendar_category_filter"
                        )
                    }
                }

                if (totalDayEntries == 0) {
                    item(key = "empty_selected_day") {
                        EmptyDayPanel(selectedDate = selectedDate)
                    }
                } else if (selectedCat != null && dayItems.isEmpty() && dayInvites.isEmpty()) {
                    item(key = "filtered_empty_selected_day_compact") {
                        FilteredEmptyDayPanel(
                            filterLabel = categoryFilter.label,
                            onClearFilter = {
                                feedback.onLightTick()
                                categoryFilter = TaskCategoryFilter.ALL
                            }
                        )
                    }
                } else {
                    if (dayInvites.isNotEmpty()) {
                        item(key = "calendar_shared_header_compact") {
                            Text(
                                text = "Shared schedules",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = DeepBlue,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        items(dayInvites, key = { "cal_invite_${it.id}" }) { invite ->
                            SharedScheduleCard(
                                invite = invite,
                                currentUserId = currentUserId,
                                onProfileIconClick = {
                                    feedback.onPopOpen()
                                    onOpenSharedInvite(invite)
                                },
                                onCardClick = {
                                    feedback.onPopOpen()
                                    onOpenSharedInvite(invite)
                                }
                            )
                        }
                    }
                    if (dayClasses.isNotEmpty() && selectedCat == null) {
                        items(dayClasses, key = { "cal_cls_${it.id}" }) { cls ->
                            CalendarClassAgendaCard(
                                schedule = cls,
                                onClick = { onEditClass(cls) }
                            )
                        }
                    }
                    items(dayItems, key = { "cal_item_${it.id}" }) { item ->
                        CalendarAgendaCard(
                            item = item,
                            state = state,
                            isSelected = item.id == selectedItemId,
                            onClick = { onSelectItem(item.id) }
                        )
                    }
                }

                if (mode == CalendarMode.WEEK) {
                    item(key = "week_timings_title") {
                        Text(
                            text = "Week timings",
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    items(weekTimingsDates, key = { "wk_day_${it.toIsoString()}" }) { date ->
                        val (itemsForDay, classesForDay) = weekTimingsBuckets[date] ?: (emptyList<PlannerItem>() to emptyList<ClassScheduleItem>())
                        WeekTimingsDayRow(
                            date = date,
                            isToday = date == today,
                            items = itemsForDay,
                            classes = classesForDay,
                            onSelectDate = { onSelectDate(date) },
                            onSelectItem = onSelectItem,
                            onEditClass = onEditClass
                        )
                    }
                }
            }
        }
    }

    if (showBunnyAlmanac) {
        val displayedMonth = if (mode == CalendarMode.MONTH) visibleMonth.month else selectedDate.month
        MonthlyBunnyAlmanacModal(
            highlightedMonth = displayedMonth,
            onDismiss = { showBunnyAlmanac = false },
            onSelectMonth = { pickedMonth ->
                val targetYear = if (mode == CalendarMode.MONTH) visibleMonth.year else selectedDate.year
                val targetMonthStart = CalendarDate(targetYear, pickedMonth, 1)
                visibleMonth = targetMonthStart
                if (mode == CalendarMode.WEEK) {
                    val maxDay = targetMonthStart.daysInMonth()
                    onSelectDate(CalendarDate(targetYear, pickedMonth, selectedDate.day.coerceIn(1, maxDay)))
                }
            }
        )
    }
}

@Composable
private fun CalendarGridPanel(
    state: PlannerUiState.Ready,
    mode: CalendarMode,
    visibleMonth: CalendarDate,
    selectedDate: CalendarDate,
    today: CalendarDate,
    overdueItemIds: Set<String>,
    invitesByDateIso: Map<String, List<CalendarInvite>>,
    reducedMotion: Boolean,
    roomyCells: Boolean,
    onModeChange: (CalendarMode) -> Unit,
    onVisibleMonthChange: (CalendarDate) -> Unit,
    onSelectDate: (CalendarDate) -> Unit,
    onOpenAlmanac: () -> Unit
) {
    val displayedDate = if (mode == CalendarMode.MONTH) visibleMonth else selectedDate
    val monthBreed = remember(displayedDate.month) { BunnyBreed.forMonth(displayedDate.month) }

    ModernPanel(
        shape = RoundedCornerShape(24.dp),
        elevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = PlannerDateUtils.formatMonthYear(displayedDate),
                        style = MaterialTheme.typography.titleLarge,
                        color = MidnightNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    val cdBadge = stringResource(
                        R.string.cd_monthly_bunny_badge,
                        monthBreed.monthName,
                        monthBreed.breedName
                    )
                    Surface(
                        color = SoftIceSurface,
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .semantics { contentDescription = cdBadge }
                            .clickable(onClick = onOpenAlmanac)
                            .testTag("calendar_monthly_bunny_chip")
                    ) {
                        AnimatedContent(
                            targetState = monthBreed,
                            transitionSpec = {
                                fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(140, easing = FastOutSlowInEasing))
                            },
                            label = "calendar_month_bunny"
                        ) { breed ->
                            Row(
                                modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                SmallBunnyIllustration(
                                    pose = BunnyPose.GREETING,
                                    breed = breed,
                                    size = 28.dp
                                )
                                Text(
                                    text = breed.breedName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = DeepBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (mode == CalendarMode.MONTH) {
                                onVisibleMonthChange(visibleMonth.plusMonths(-1))
                            } else {
                                onSelectDate(selectedDate.plusDays(-7))
                            }
                        },
                        modifier = Modifier.testTag("calendar_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = MidnightNavy
                        )
                    }
                    IconButton(
                        onClick = {
                            if (mode == CalendarMode.MONTH) {
                                onVisibleMonthChange(visibleMonth.plusMonths(1))
                            } else {
                                onSelectDate(selectedDate.plusDays(7))
                            }
                        },
                        modifier = Modifier.testTag("calendar_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = MidnightNavy
                        )
                    }
                }
            }

            ModernSegmentedToggle(
                options = listOf(
                    CalendarMode.MONTH to "Month",
                    CalendarMode.WEEK to "Week"
                ),
                selected = mode,
                onSelect = onModeChange,
                tagPrefix = "calendar_mode"
            )

            // Weekday headers with full day names for screen readers
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekdays = listOf(
                    "S" to "Sunday",
                    "M" to "Monday",
                    "T" to "Tuesday",
                    "W" to "Wednesday",
                    "T" to "Thursday",
                    "F" to "Friday",
                    "S" to "Saturday"
                )
                weekdays.forEach { (shortName, fullName) ->
                    Text(
                        text = shortName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MutedNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = fullName }
                    )
                }
            }

            AnimatedContent(
                targetState = Pair(mode, if (mode == CalendarMode.MONTH) visibleMonth else selectedDate),
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(130)) togetherWith fadeOut(tween(100))
                    } else {
                        val (prevMode, prevDate) = initialState
                        val (nextMode, nextDate) = targetState
                        if (prevMode != nextMode) {
                            fadeIn(tween(190, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(140, easing = FastOutSlowInEasing))
                        } else {
                            val dir = if (nextDate >= prevDate) 1 else -1
                            (slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { w -> dir * w / 10 } +
                                fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(170, easing = FastOutSlowInEasing)) { w -> -dir * w / 12 } +
                                    fadeOut(tween(140)))
                        }
                    }
                },
                label = "calendar_grid_transition"
            ) { (activeMode, anchorDate) ->
                if (activeMode == CalendarMode.MONTH) {
                    val gridCells = remember(anchorDate) {
                        PlannerDateUtils.buildMonthGrid(anchorDate)
                    }
                    // Precompute ALL day buckets for this month ONCE (including state.steps for accurate bloom dots)
                    val monthBuckets = remember(anchorDate, state.items, state.steps, state.classSchedules, overdueItemIds, invitesByDateIso) {
                        gridCells.filterNotNull().associateWith { date ->
                            buildDayBucket(date, state, overdueItemIds, invitesByDateIso)
                        }
                    }
                    val rows = remember(gridCells) { gridCells.chunked(7) }
                    val cellHeight = if (roomyCells) 68.dp else 60.dp

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                week.forEach { date ->
                                    if (date == null) {
                                        Spacer(modifier = Modifier.weight(1f).height(cellHeight))
                                    } else {
                                        val bucket = monthBuckets[date]
                                        MonthDayCell(
                                            date = date,
                                            isToday = date == today,
                                            isSelected = date == selectedDate,
                                            bucket = bucket,
                                            cellHeight = cellHeight,
                                            onClick = { onSelectDate(date) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val weekDates = remember(anchorDate) {
                        PlannerDateUtils.buildWeekDates(anchorDate)
                    }
                    val weekBuckets = remember(anchorDate, state.items, state.steps, state.classSchedules, overdueItemIds, invitesByDateIso) {
                        weekDates.associateWith { date -> buildDayBucket(date, state, overdueItemIds, invitesByDateIso) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weekDates.forEach { date ->
                            val bucket = weekBuckets[date]
                            WeekDayChip(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                bucket = bucket,
                                onClick = { onSelectDate(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Highlight color legend including forget-me-not bloom marker & shared invites
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ForgetMeNotFlower(bloomed = true, size = 13.dp)
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy
                    )
                }
                CalendarLegendItem(color = DeepBlue, fill = SkyBlue.copy(alpha = 0.56f), label = "Event")
                CalendarLegendItem(color = ForgetMeNotBlue, fill = SkyBlue.copy(alpha = 0.30f), label = "Plan")
                CalendarLegendItem(color = PollenYellow, fill = PollenSoft, label = "Task")
                CalendarLegendItem(color = CalmSage, fill = CalmSageContainer, label = "Class")
                CalendarLegendItem(color = SharedInviteAccent, fill = SharedInviteAccent.copy(alpha = 0.16f), label = "Shared")
            }
        }
    }
}

@Composable
private fun SelectedDayHeaderRow(
    selectedDate: CalendarDate,
    totalDayEntries: Int,
    onInviteFriend: () -> Unit,
    onAddForDate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = PlannerDateUtils.formatFullDateHeader(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                color = MidnightNavy
            )
            Text(
                text = if (totalDayEntries == 0) "No items on this day"
                else "$totalDayEntries ${if (totalDayEntries == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedNavy
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        OutlinedButton(
            onClick = onInviteFriend,
            border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(100.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("calendar_invite_friend_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.GroupAdd,
                contentDescription = stringResource(R.string.invite_friend_title),
                tint = DeepBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Invite", style = MaterialTheme.typography.labelMedium, color = DeepBlue)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Button(
            onClick = onAddForDate,
            colors = ButtonDefaults.buttonColors(
                containerColor = DeepBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(100.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            modifier = Modifier.testTag("calendar_add_for_date_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add here", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyDayPanel(selectedDate: CalendarDate) {
    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Clear day. Tap “Add here” to plan something for ${PlannerDateUtils.formatCompactDate(selectedDate)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedNavy,
            modifier = Modifier.padding(18.dp)
        )
    }
}

@Composable
private fun FilteredEmptyDayPanel(
    filterLabel: String,
    onClearFilter: () -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_filtered_empty_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No ${filterLabel.lowercase()} items on this day",
                style = MaterialTheme.typography.titleSmall,
                color = MidnightNavy
            )
            OutlinedButton(
                onClick = onClearFilter,
                border = BorderStroke(1.dp, SubtleBorder),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.testTag("calendar_clear_filter_button")
            ) {
                Text("Clear filter", color = DeepBlue)
            }
        }
    }
}

/**
 * Airy calendar cell with clear pastel date highlights whenever a date has an Event, Plan, Task, or Class.
 * Uses precomputed CalendarDayBucket for O(1) rendering during month transitions.
 */
@Composable
private fun MonthDayCell(
    date: CalendarDate,
    isToday: Boolean,
    isSelected: Boolean,
    bucket: CalendarDayBucket?,
    cellHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEvent = bucket?.hasEvent == true
    val hasPlan = bucket?.hasPlan == true
    val hasTask = bucket?.hasTask == true
    val hasClass = bucket?.hasClass == true
    val hasSharedInvite = bucket?.hasSharedInvite == true
    val hasCompletedDailyTask = bucket?.hasCompletedDailyTask == true
    val hasOverdue = bucket?.hasOverdue == true
    val hasAny = bucket?.hasAny == true || hasCompletedDailyTask

    val cellA11yLabel = remember(date, isToday, isSelected, bucket) {
        buildString {
            append(PlannerDateUtils.formatFullDateHeader(date))
            if (isToday) append(", Today")
            if (isSelected) append(", Selected")
            val total = (bucket?.items?.size ?: 0) + (bucket?.classes?.size ?: 0) + (bucket?.invites?.size ?: 0)
            if (total > 0) append(", $total scheduled")
            if (hasOverdue) append(", has overdue items")
            if (hasCompletedDailyTask) append(", completed bloom")
        }
    }

    val cellShape = RoundedCornerShape(12.dp)
    val highlightFill = when {
        hasOverdue -> OverdueContainer
        hasCompletedDailyTask -> SkyBlue.copy(alpha = 0.46f)
        hasEvent -> SkyBlue.copy(alpha = 0.56f)
        hasPlan -> SkyBlue.copy(alpha = 0.34f)
        hasTask -> PollenSoft
        hasClass -> CalmSageContainer
        isSelected -> SoftIceSurface
        else -> Color.Transparent
    }
    val highlightBorder = when {
        isSelected -> DeepBlue
        hasOverdue -> OverdueTerracotta.copy(alpha = 0.5f)
        hasCompletedDailyTask -> ForgetMeNotBlue.copy(alpha = 0.7f)
        hasEvent -> DeepBlue.copy(alpha = 0.40f)
        hasPlan -> ForgetMeNotBlue.copy(alpha = 0.45f)
        hasTask -> PollenYellow.copy(alpha = 0.75f)
        hasClass -> CalmSage.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .height(cellHeight)
            .clip(cellShape)
            .background(highlightFill)
            .then(
                if (highlightBorder != Color.Transparent) {
                    Modifier.border(
                        width = if (isSelected) 1.6.dp else 0.9.dp,
                        color = highlightBorder,
                        shape = cellShape
                    )
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = cellA11yLabel }
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .testTag("calendar_cell_${date.toIsoString()}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .clip(CircleShape)
                    .background(if (isToday) DeepBlue else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isToday || isSelected || hasAny) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MidnightNavy
                )
            }

            val previewLabel = bucket?.previewLabel
            if (hasCompletedDailyTask) {
                ForgetMeNotFlower(
                    bloomed = true,
                    size = 15.dp,
                    contentDesc = "Daily task completed"
                )
            } else if (hasAny && previewLabel != null) {
                Text(
                    text = previewLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasOverdue) OverdueTerracotta else MidnightNavy.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (hasAny) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (hasEvent) CalendarDot(color = DeepBlue)
                    if (hasPlan) CalendarDot(color = ForgetMeNotBlue)
                    if (hasTask && !hasCompletedDailyTask) CalendarDot(color = PollenYellow)
                    if (hasClass) CalendarDot(color = CalmSage)
                    if (hasSharedInvite) CalendarDot(color = SharedInviteAccent)
                }
            }
        }
    }
}

@Composable
private fun CalendarDot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun CalendarLegendItem(
    color: Color,
    fill: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(fill)
                .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedNavy
        )
    }
}

@Composable
private fun WeekDayChip(
    date: CalendarDate,
    isToday: Boolean,
    isSelected: Boolean,
    bucket: CalendarDayBucket?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEvent = bucket?.hasEvent == true
    val hasPlan = bucket?.hasPlan == true
    val hasTask = bucket?.hasTask == true
    val hasClass = bucket?.hasClass == true
    val hasSharedInvite = bucket?.hasSharedInvite == true
    val hasCompletedDailyTask = bucket?.hasCompletedDailyTask == true
    val hasAny = bucket?.hasAny == true || hasCompletedDailyTask

    val weekCellDesc = remember(date, isToday, isSelected, bucket) {
        buildString {
            append(PlannerDateUtils.formatFullDateHeader(date))
            if (isToday) append(", Today")
            if (isSelected) append(", Selected")
            val total = (bucket?.items?.size ?: 0) + (bucket?.classes?.size ?: 0) + (bucket?.invites?.size ?: 0)
            if (total > 0) append(", $total scheduled")
        }
    }

    val shape = RoundedCornerShape(14.dp)
    val bgColor = when {
        isToday -> DeepBlue
        hasCompletedDailyTask -> SkyBlue.copy(alpha = 0.56f)
        hasEvent -> SkyBlue.copy(alpha = 0.65f)
        hasPlan -> SkyBlue.copy(alpha = 0.42f)
        hasTask -> PollenSoft
        hasClass -> CalmSageContainer
        isSelected -> SkyBlue.copy(alpha = 0.5f)
        else -> SoftIceSurface
    }
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(bgColor)
            .then(
                if (isSelected && !isToday) Modifier.border(1.5.dp, DeepBlue, shape)
                else if (hasAny && !isToday) Modifier.border(0.9.dp, ForgetMeNotBlue.copy(alpha = 0.5f), shape)
                else Modifier
            )
            .semantics { contentDescription = weekCellDesc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isToday || hasAny) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isToday) onPrimaryColor else MidnightNavy
            )
            if (hasCompletedDailyTask) {
                Spacer(modifier = Modifier.height(2.dp))
                ForgetMeNotFlower(
                    bloomed = true,
                    size = 14.dp,
                    contentDesc = "Daily task completed"
                )
            } else if (hasAny) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                    if (hasEvent) CalendarDot(if (isToday) onPrimaryColor else DeepBlue)
                    if (hasPlan) CalendarDot(if (isToday) onPrimaryColor else ForgetMeNotBlue)
                    if (hasTask) CalendarDot(if (isToday) onPrimaryColor else PollenYellow)
                    if (hasClass) CalendarDot(if (isToday) onPrimaryColor else CalmSage)
                    if (hasSharedInvite) CalendarDot(if (isToday) onPrimaryColor else SharedInviteAccent)
                }
            }
        }
    }
}

@Composable
private fun CalendarClassAgendaCard(
    schedule: ClassScheduleItem,
    onClick: () -> Unit
) {
    val (fill, accent) = classColorPalette(schedule.colorKeyEnum())
    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        borderColor = accent.copy(alpha = 0.35f),
        elevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_class_${schedule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = schedule.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightNavy
                )
                Text(
                    text = buildString {
                        append(schedule.formattedTimeRange())
                        if (!schedule.room.isNullOrBlank()) {
                            append(" · ")
                            append(schedule.room)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (schedule.notifyEnabled) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Reminder active",
                        tint = DeepBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                ModernPillBadge(
                    text = "Class",
                    containerColor = CalmSageContainer,
                    contentColor = CalmSage
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarAgendaCard(
    item: PlannerItem,
    state: PlannerUiState.Ready,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val progress = state.progressFor(item)
    val timingText = when (item.itemType()) {
        ItemType.EVENT -> PlannerDateUtils.formatEventTimeRange(item.startAtUtcMillis, item.endAtUtcMillis)
        ItemType.TASK -> item.dueTimeMinutes?.let { "Due ${PlannerDateUtils.formatTimeOfDay(it)}" } ?: "Task"
        ItemType.PLAN -> {
            val due = CalendarDate.parse(item.dueDate)
            if (due != null) "Plan · Deadline ${PlannerDateUtils.formatCompactDate(due)}" else "Plan"
        }
    }

    ModernPanel(
        containerColor = selectedPanelContainer(isSelected),
        borderColor = selectedPanelBorder(isSelected),
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_agenda_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightNavy
                )
                Text(
                    text = timingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TaskCategoryBadge(
                        category = item.taskCategory(),
                        compact = true
                    )
                    if (item.itemType() == ItemType.TASK) {
                        TaskPriorityBadge(
                            priority = item.taskPriority(),
                            compact = true
                        )
                    }
                    ModernPillBadge(
                        text = item.itemType().label,
                        containerColor = SoftIceSurface,
                        contentColor = MutedNavy
                    )
                }
            }
            if (progress.totalCount > 0) {
                FlowerProgressRow(
                    completed = progress.completedCount,
                    total = progress.totalCount,
                    summaryText = progress.summaryLabel(item.itemType()),
                    flowerSize = 18.dp
                )
            }
        }
    }
}

@Composable
private fun WeekTimingsDayRow(
    date: CalendarDate,
    isToday: Boolean,
    items: List<PlannerItem>,
    classes: List<ClassScheduleItem>,
    onSelectDate: () -> Unit,
    onSelectItem: (String) -> Unit,
    onEditClass: (ClassScheduleItem) -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(18.dp),
        borderColor = if (isToday) DeepBlue.copy(alpha = 0.45f) else selectedPanelBorder(false),
        elevation = 2.dp,
        onClick = onSelectDate,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = PlannerDateUtils.formatShortDate(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
                if (isToday) {
                    ModernPillBadge(
                        text = "Today",
                        containerColor = SkyBlue.copy(alpha = 0.65f),
                        contentColor = DeepBlue
                    )
                }
            }
            if (items.isEmpty() && classes.isEmpty()) {
                Text(
                    text = "Open day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            } else {
                classes.forEach { cls ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CalmSageContainer)
                            .clickable { onEditClass(cls) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cls.subject,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MidnightNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cls.formattedTimeRange(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CalmSage
                        )
                    }
                }
                items.forEach { item ->
                    val timeLabel = when (item.itemType()) {
                        ItemType.EVENT -> PlannerDateUtils.formatEventTimeRange(item.startAtUtcMillis, item.endAtUtcMillis)
                        ItemType.TASK -> item.dueTimeMinutes?.let { PlannerDateUtils.formatTimeOfDay(it) } ?: "All day"
                        ItemType.PLAN -> "Plan"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftIceSurface)
                            .clickable { onSelectItem(item.id) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MidnightNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }
            }
        }
    }
}
