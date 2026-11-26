package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.audio.TaskAudioMemoManager
import com.example.data.AppUsageSummary
import com.example.data.CalendarDate
import com.example.data.CalendarInvite
import com.example.data.ClassScheduleItem
import com.example.data.ItemProgress
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.data.TaskCategory
import com.example.data.UnifiedStep
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyPose
import com.example.ui.components.CompactAppUsageStrip
import com.example.ui.components.DailyHabitGrowthCard
import com.example.ui.components.FlowerProgressRow
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ForgetMeNotHabitCalendarCard
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.ModernSectionHeader
import com.example.ui.components.MonthlyBunnyAlmanacModal
import com.example.ui.components.SharedScheduleCard
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.TaskCategoryBadge
import com.example.ui.components.TaskCategoryFilter
import com.example.ui.components.TaskCategoryFilterBar
import com.example.ui.components.adaptiveHorizontalPadding
import com.example.ui.components.classColorPalette
import com.example.ui.components.selectedPanelBorder
import com.example.ui.components.selectedPanelContainer
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.LocalIsDarkTheme
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    state: PlannerUiState.Ready,
    usageSummary: AppUsageSummary,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    onToggleTask: (PlannerItem, Boolean) -> Unit,
    onToggleStep: (UnifiedStep, Boolean) -> Unit,
    onAddClick: (CalendarDate) -> Unit,
    onEditClass: (ClassScheduleItem) -> Unit = {},
    onOpenReports: () -> Unit = {},
    calendarInvites: List<CalendarInvite> = emptyList(),
    currentUserId: String? = null,
    onInviteFriendClick: () -> Unit = {},
    onOpenSharedInvite: (CalendarInvite) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val today = state.todayDate()
    val monthBreed = remember(today.month) { BunnyBreed.forMonth(today.month) }
    var showActivity by rememberSaveable { mutableStateOf(false) }
    var showBunnyAlmanac by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf(TaskCategoryFilter.ALL) }
    val nowMillis = state.nowMillis

    val todayClasses = remember(state.classSchedules, today) {
        state.classesOnDate(today)
    }

    val allTodayEvents = remember(state.items, today) {
        state.items
            .filter { it.itemType() == ItemType.EVENT && it.appearsOnDate(today) }
            .sortedBy { it.startAtUtcMillis ?: Long.MAX_VALUE }
    }

    val allTodayTasks = remember(state.items, today) {
        state.items
            .filter { item ->
                if (item.itemType() != ItemType.TASK) return@filter false
                val due = CalendarDate.parse(item.dueDate)
                due == today || (!item.completed && due != null && due < today)
            }
            .sortedWith(
                compareBy<PlannerItem> { it.completed }
                    .thenBy { it.dueDate ?: "" }
                    .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
            )
    }

    val itemTitleById = remember(state.items) {
        state.items.associate { it.id to it.title }
    }
    val itemCategoryById = remember(state.items) {
        state.items.associate { it.id to it.taskCategory() }
    }

    val allDueTodaySteps = remember(state.items, state.steps, today) {
        val itemMap = state.items.associateBy { it.id }
        state.steps
            .filter { step ->
                val parent = itemMap[step.itemId] ?: return@filter false
                val stepDate = CalendarDate.parse(step.dueDate)
                val isToday = stepDate == today
                val isOverdue = !step.completed && stepDate != null && stepDate < today
                (isToday || isOverdue) && parent.itemType() != ItemType.TASK
            }
            .mapNotNull { step ->
                val parent = itemMap[step.itemId] ?: return@mapNotNull null
                Triple(
                    UnifiedStep(
                        id = step.id,
                        parentItemId = parent.id,
                        title = step.title,
                        completed = step.completed,
                        dueDate = step.dueDate,
                        dueTimeMinutes = step.dueTimeMinutes,
                        isLinkedTask = false,
                        sortOrder = step.sortOrder,
                        timeSpentMinutes = step.timeSpentMinutes
                    ),
                    parent,
                    step.dueDate != null && (CalendarDate.parse(step.dueDate) ?: today) < today && !step.completed
                )
            }
    }

    val allApproachingPlans = remember(state.items, state.steps, today) {
        state.items
            .filter { item ->
                if (item.itemType() != ItemType.PLAN) return@filter false
                val progress = state.progressFor(item)
                val due = CalendarDate.parse(item.dueDate)
                val start = CalendarDate.parse(item.startDate)
                val isApproaching = (due != null && today.daysUntil(due) <= 14) ||
                    (start != null && start <= today) ||
                    progress.overdueCount > 0
                isApproaching && (!progress.allStepsDone || (due != null && due >= today))
            }
            .sortedWith(
                compareByDescending<PlannerItem> { state.progressFor(it).overdueCount > 0 }
                    .thenBy { it.dueDate ?: "9999-99-99" }
            )
    }

    val allQuickNotes = remember(state.items) {
        state.items
            .filter { item ->
                item.itemType() == ItemType.TASK &&
                    item.dueDate.isNullOrBlank() &&
                    item.parentPlanId.isNullOrBlank()
            }
            .sortedWith(
                compareBy<PlannerItem> { it.completed }
                    .thenByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            )
    }

    val todayIso = remember(today) { today.toIsoString() }
    val todaySharedInvites = remember(calendarInvites, todayIso) {
        calendarInvites.filter { it.plannedDate == todayIso }
    }

    val selectedCat = categoryFilter.category
    val categoryCounts = remember(allTodayEvents, allTodayTasks, allDueTodaySteps, allApproachingPlans, allQuickNotes, itemCategoryById) {
        val relevantCats = buildList {
            allTodayEvents.forEach { add(it.taskCategory()) }
            allTodayTasks.forEach { add(it.taskCategory()) }
            allDueTodaySteps.forEach { (_, parent, _) ->
                add(itemCategoryById[parent.id] ?: parent.taskCategory())
            }
            allApproachingPlans.forEach { add(it.taskCategory()) }
            allQuickNotes.forEach { add(it.taskCategory()) }
        }
        buildMap {
            put(TaskCategoryFilter.ALL, relevantCats.size)
            TaskCategory.entries.forEach { cat ->
                TaskCategoryFilter.entries.firstOrNull { it.category == cat }?.let { key ->
                    put(key, relevantCats.count { it == cat })
                }
            }
        }
    }
    val todayEvents = remember(allTodayEvents, selectedCat) {
        if (selectedCat == null) allTodayEvents else allTodayEvents.filter { it.taskCategory() == selectedCat }
    }
    val todayTasks = remember(allTodayTasks, selectedCat) {
        if (selectedCat == null) allTodayTasks else allTodayTasks.filter { it.taskCategory() == selectedCat }
    }
    val dueTodaySteps = remember(allDueTodaySteps, selectedCat, itemCategoryById) {
        if (selectedCat == null) allDueTodaySteps else allDueTodaySteps.filter { (_, parent, _) -> itemCategoryById[parent.id] == selectedCat }
    }
    val approachingPlans = remember(allApproachingPlans, selectedCat) {
        if (selectedCat == null) allApproachingPlans else allApproachingPlans.filter { it.taskCategory() == selectedCat }
    }
    val quickNotes = remember(allQuickNotes, selectedCat) {
        if (selectedCat == null) allQuickNotes else allQuickNotes.filter { it.taskCategory() == selectedCat }
    }

    val isQuietDay = todayClasses.isEmpty() && allTodayEvents.isEmpty() && allTodayTasks.isEmpty() && allDueTodaySteps.isEmpty() && allApproachingPlans.isEmpty() && todaySharedInvites.isEmpty()
    val hasFilteredItems = todayEvents.isNotEmpty() || todayTasks.isNotEmpty() || dueTodaySteps.isNotEmpty() || approachingPlans.isNotEmpty() || quickNotes.isNotEmpty()
    val isFilteredEmpty = !isQuietDay && categoryFilter != TaskCategoryFilter.ALL && !hasFilteredItems
    val totalDueCount = todayTasks.size + dueTodaySteps.size

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPad = adaptiveHorizontalPadding(maxWidth)
        val hasPrimaryColumnContent = todayClasses.isNotEmpty() || todayEvents.isNotEmpty() || todayTasks.isNotEmpty() || dueTodaySteps.isNotEmpty()
        val useTwoColumns = maxWidth >= 740.dp && !isQuietDay && hasPrimaryColumnContent && approachingPlans.isNotEmpty()
        val containerMaxWidth = if (useTwoColumns) 1120.dp else 720.dp

        LazyColumn(
            modifier = Modifier
                .widthIn(max = containerMaxWidth)
                .fillMaxSize()
                .testTag("today_screen_list"),
            contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Editorial borderless hero header directly on the 60% neutral canvas
            item(key = "today_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
                            ),
                            color = MutedNavy
                        )
                        Text(
                            text = PlannerDateUtils.formatFullDateHeader(today),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MidnightNavy
                        )
                        val agendaSummary = listOfNotNull(
                            "$totalDueCount due".takeIf { totalDueCount > 0 },
                            "${todayClasses.size} ${if (todayClasses.size == 1) "class" else "classes"}".takeIf { todayClasses.isNotEmpty() },
                            "${todayEvents.size} ${if (todayEvents.size == 1) "event" else "events"}".takeIf { todayEvents.isNotEmpty() }
                        ).joinToString(" · ")
                        if (agendaSummary.isNotEmpty()) {
                            Text(agendaSummary, style = MaterialTheme.typography.bodyMedium, color = MutedNavy)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val badgeDesc = stringResource(
                        R.string.cd_monthly_bunny_badge,
                        monthBreed.monthName,
                        monthBreed.breedName
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = if (LocalIsDarkTheme.current) SoftIceSurface else PureWhite,
                            shape = CircleShape,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .semantics { contentDescription = badgeDesc }
                                .clickable { showBunnyAlmanac = true }
                                .testTag("today_monthly_bunny_badge")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SmallBunnyIllustration(
                                    pose = BunnyPose.GREETING,
                                    breed = monthBreed,
                                    size = 48.dp
                                )
                            }
                        }
                        Text(
                            text = "${monthBreed.shortMonthName} · ${monthBreed.breedName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    feedback.onPopOpen()
                                    showBunnyAlmanac = true
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (allTodayEvents.isNotEmpty() || allTodayTasks.isNotEmpty() || allDueTodaySteps.isNotEmpty() || allApproachingPlans.isNotEmpty() || allQuickNotes.isNotEmpty()) {
                item(key = "today_category_filter_bar") {
                    TaskCategoryFilterBar(
                        selectedFilter = categoryFilter,
                        counts = categoryCounts,
                        onSelectFilter = {
                            feedback.onLightTick()
                            categoryFilter = it
                        },
                        tagPrefix = "today_category_filter"
                    )
                }
            }

            if (todaySharedInvites.isNotEmpty()) {
                item(key = "today_shared_invites_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ModernSectionHeader(
                                title = "Shared schedules today",
                                count = todaySharedInvites.size
                            )
                        }
                        TextButton(
                            onClick = {
                                feedback.onPopOpen()
                                onInviteFriendClick()
                            },
                            modifier = Modifier.testTag("today_invite_friend_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Invite")
                        }
                    }
                }
                items(todaySharedInvites, key = { "today_invite_${it.id}" }) { invite ->
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

        if (isQuietDay) {
            // Calm elevated empty state card
            item(key = "quiet_day_card") {
                ModernPanel(
                    shape = RoundedCornerShape(26.dp),
                    elevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quiet_day_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SmallBunnyIllustration(
                            pose = BunnyPose.RESTING,
                            breed = monthBreed,
                            size = 104.dp,
                            contentDesc = stringResource(R.string.cd_bunny_resting)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.quiet_day_message),
                            style = MaterialTheme.typography.titleLarge,
                            color = MidnightNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.quiet_day_subtext),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedNavy
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = { onAddClick(today) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepBlue,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("quiet_day_add_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_add))
                        }
                    }
                }
            }
        } else if (isFilteredEmpty) {
            item(key = "today_filtered_empty_card") {
                ModernPanel(
                    shape = RoundedCornerShape(22.dp),
                    elevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_filtered_empty_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No ${categoryFilter.label.lowercase()} items due today",
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy
                        )
                        Text(
                            text = "Your other categories or classes are still scheduled for today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                        OutlinedButton(
                            onClick = {
                                feedback.onLightTick()
                                categoryFilter = TaskCategoryFilter.ALL
                            },
                            border = BorderStroke(1.dp, SubtleBorder),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.testTag("today_clear_filter_button")
                        ) {
                            Text("Clear filter", color = DeepBlue)
                        }
                    }
                }
            }
        } else if (useTwoColumns) {
            // Wide Tablet 2-Column Editorial Dashboard: Schedule & Due Today (Left) | Approaching Plans (Right)
            item(key = "today_tablet_two_columns") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (todayClasses.isNotEmpty()) {
                            ModernSectionHeader(title = "Classes today", count = todayClasses.size)
                            todayClasses.forEach { cls ->
                                TodayClassRowCard(cls = cls, onClick = { onEditClass(cls) })
                            }
                        }
                        if (todayEvents.isNotEmpty()) {
                            ModernSectionHeader(title = "Events today", count = todayEvents.size)
                            todayEvents.forEach { event ->
                                EventTodayCard(
                                    event = event,
                                    progress = state.progressFor(event),
                                    nowMillis = nowMillis,
                                    isSelected = event.id == selectedItemId,
                                    onClick = { onSelectItem(event.id) }
                                )
                            }
                        }
                        if (todayTasks.isNotEmpty() || dueTodaySteps.isNotEmpty()) {
                            ModernSectionHeader(title = "Due today", count = totalDueCount)
                            DueTodayGroupedPanel(
                                todayTasks = todayTasks,
                                dueTodaySteps = dueTodaySteps,
                                itemTitleById = itemTitleById,
                                today = today,
                                selectedItemId = selectedItemId,
                                onToggleTask = onToggleTask,
                                onToggleStep = onToggleStep,
                                onSelectItem = onSelectItem
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ModernSectionHeader(title = "Approaching plans", count = approachingPlans.size)
                        approachingPlans.forEach { plan ->
                            PlanTodayCard(
                                plan = plan,
                                progress = state.progressFor(plan),
                                today = today,
                                isSelected = plan.id == selectedItemId,
                                onClick = { onSelectItem(plan.id) }
                            )
                        }
                    }
                }
            }
        } else {
            // Section 0: Today's scheduled classes
            if (todayClasses.isNotEmpty()) {
                item(key = "sec_classes") {
                    ModernSectionHeader(title = "Classes today", count = todayClasses.size)
                }
                items(todayClasses, key = { "today_cls_${it.id}" }) { cls ->
                    TodayClassRowCard(cls = cls, onClick = { onEditClass(cls) })
                }
            }

            // Section 1: Events in time order
            if (todayEvents.isNotEmpty()) {
                item(key = "sec_events") {
                    ModernSectionHeader(title = "Events today", count = todayEvents.size)
                }
                items(todayEvents, key = { "ev_${it.id}" }) { event ->
                    val progress = state.progressFor(event)
                    EventTodayCard(
                        event = event,
                        progress = progress,
                        nowMillis = nowMillis,
                        isSelected = event.id == selectedItemId,
                        onClick = { onSelectItem(event.id) }
                    )
                }
            }

            // Section 2: Tasks & Milestones grouped inside one sleek elevated sheet
            if (todayTasks.isNotEmpty() || dueTodaySteps.isNotEmpty()) {
                item(key = "sec_tasks") {
                    ModernSectionHeader(
                        title = "Due today",
                        count = totalDueCount
                    )
                }
                item(key = "due_today_grouped_panel") {
                    DueTodayGroupedPanel(
                        todayTasks = todayTasks,
                        dueTodaySteps = dueTodaySteps,
                        itemTitleById = itemTitleById,
                        today = today,
                        selectedItemId = selectedItemId,
                        onToggleTask = onToggleTask,
                        onToggleStep = onToggleStep,
                        onSelectItem = onSelectItem
                    )
                }
            }

            // Section 3: Approaching plans
            if (approachingPlans.isNotEmpty()) {
                item(key = "sec_plans") {
                    ModernSectionHeader(title = "Approaching plans", count = approachingPlans.size)
                }
                items(approachingPlans, key = { "plan_${it.id}" }) { plan ->
                    val progress = state.progressFor(plan)
                    PlanTodayCard(
                        plan = plan,
                        progress = progress,
                        today = today,
                        isSelected = plan.id == selectedItemId,
                        onClick = { onSelectItem(plan.id) }
                    )
                }
            }
        }

            // Spontaneous Quick Notes & Ideas captured via the Quick Note FAB
            if (quickNotes.isNotEmpty()) {
                item(key = "sec_quick_notes") {
                    ModernSectionHeader(
                        title = stringResource(R.string.quick_note_section_title),
                        count = quickNotes.size
                    )
                }
                item(key = "quick_notes_grouped_panel") {
                    QuickNotesGroupedPanel(
                        quickNotes = quickNotes,
                        selectedItemId = selectedItemId,
                        onToggleNote = onToggleTask,
                        onSelectNote = onSelectItem
                    )
                }
            }

            item(key = "daily_habit_growth_card") {
                DailyHabitGrowthCard(modifier = Modifier.fillMaxWidth())
            }

            item(key = "activity_toggle") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { showActivity = !showActivity }) {
                        Text(if (showActivity) "Hide activity & habit garden" else "Show activity & habit garden")
                    }
                    TextButton(
                        onClick = {
                            feedback.onPopOpen()
                            onInviteFriendClick()
                        },
                        modifier = Modifier.testTag("today_quick_invite_friend_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_invite_friend_cta))
                    }
                }
            }
            if (showActivity) {
                item(key = "today_usage_strip") {
                    CompactAppUsageStrip(usage = usageSummary, onClick = onOpenReports)
                }
                item(key = "today_habit_garden") {
                    ForgetMeNotHabitCalendarCard(
                        items = state.items,
                        steps = state.steps,
                        today = today,
                        onAddTaskForDate = { d -> onAddClick(d) }
                    )
                }
            }
        }
    }

    if (showBunnyAlmanac) {
        MonthlyBunnyAlmanacModal(
            highlightedMonth = today.month,
            onDismiss = { showBunnyAlmanac = false }
        )
    }
}

@Composable
private fun QuickNotesGroupedPanel(
    quickNotes: List<PlannerItem>,
    selectedItemId: String?,
    onToggleNote: (PlannerItem, Boolean) -> Unit,
    onSelectNote: (String) -> Unit
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    ModernPanel(
        shape = RoundedCornerShape(22.dp),
        elevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_quick_notes_panel")
    ) {
        quickNotes.forEachIndexed { idx, note ->
            val isSelected = note.id == selectedItemId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) SkyBlue.copy(alpha = 0.25f) else PureWhite)
                    .clickable { onSelectNote(note.id) }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("today_quick_note_${note.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = note.completed,
                    onCheckedChange = { checked ->
                        feedback.onTaskToggled(checked)
                        onToggleNote(note, checked)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = DeepBlue,
                        uncheckedColor = MutedNavy.copy(alpha = 0.55f),
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("today_quick_note_checkbox_${note.id}")
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (note.completed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (note.completed) MutedNavy else MidnightNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!note.notes.isNullOrBlank()) {
                        Text(
                            text = note.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TaskCategoryBadge(category = note.taskCategory(), compact = true)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(PollenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (idx < quickNotes.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.75.dp,
                    color = SubtleBorder.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TodayClassRowCard(
    cls: ClassScheduleItem,
    onClick: () -> Unit
) {
    val (fill, accent) = classColorPalette(cls.colorKeyEnum())
    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        borderColor = accent.copy(alpha = 0.35f),
        elevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_class_${cls.id}")
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
                    text = cls.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(cls.formattedTimeRange())
                        if (!cls.room.isNullOrBlank()) {
                            append(" · ")
                            append(cls.room)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (cls.notifyEnabled) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Class reminder active",
                    tint = DeepBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DueTodayGroupedPanel(
    todayTasks: List<PlannerItem>,
    dueTodaySteps: List<Triple<UnifiedStep, PlannerItem, Boolean>>,
    itemTitleById: Map<String, String>,
    today: CalendarDate,
    selectedItemId: String?,
    onToggleTask: (PlannerItem, Boolean) -> Unit,
    onToggleStep: (UnifiedStep, Boolean) -> Unit,
    onSelectItem: (String) -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(22.dp),
        elevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        val totalRows = todayTasks.size + dueTodaySteps.size
        var rowIdx = 0

        todayTasks.forEach { task ->
            val parentPlanTitle = task.parentPlanId?.let { itemTitleById[it] }
            val due = CalendarDate.parse(task.dueDate)
            val isOverdue = !task.completed && due != null && due < today
            TaskRowItem(
                task = task,
                parentPlanTitle = parentPlanTitle,
                isOverdue = isOverdue,
                isSelected = task.id == selectedItemId,
                onToggle = { checked -> onToggleTask(task, checked) },
                onClick = { onSelectItem(task.id) }
            )
            rowIdx++
            if (rowIdx < totalRows) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.75.dp,
                    color = SubtleBorder.copy(alpha = 0.8f)
                )
            }
        }

        dueTodaySteps.forEach { (step, parent, isOverdue) ->
            DueStepRowItem(
                step = step,
                parentTitle = parent.title,
                isOverdue = isOverdue,
                onToggle = { checked -> onToggleStep(step, checked) },
                onOpenParent = { onSelectItem(parent.id) }
            )
            rowIdx++
            if (rowIdx < totalRows) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.75.dp,
                    color = SubtleBorder.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventTodayCard(
    event: PlannerItem,
    progress: ItemProgress,
    nowMillis: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val countdown = PlannerDateUtils.formatEventCountdown(
        event.startAtUtcMillis,
        event.endAtUtcMillis,
        nowMillis
    )
    ModernPanel(
        containerColor = selectedPanelContainer(isSelected),
        borderColor = selectedPanelBorder(isSelected),
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_event_${event.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Subtle 10% accent strip on left edge
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(DeepBlue)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    ModernPillBadge(
                        text = countdown,
                        containerColor = SkyBlue.copy(alpha = 0.65f),
                        contentColor = DeepBlue
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MutedNavy,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = PlannerDateUtils.formatEventTimeRange(event.startAtUtcMillis, event.endAtUtcMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy
                        )
                    }
                    TaskCategoryBadge(category = event.taskCategory(), compact = true)
                    if (event.hasAudioMemo()) {
                        AudioMemoMiniTag(durationSeconds = event.audioMemoDurationSeconds)
                    }
                }

                if (progress.totalCount > 0) {
                    FlowerProgressRow(
                        completed = progress.completedCount,
                        total = progress.totalCount,
                        summaryText = if (progress.allStepsDone) {
                            "Ready · ${progress.summaryLabel(ItemType.EVENT)}"
                        } else {
                            progress.summaryLabel(ItemType.EVENT)
                        },
                        flowerSize = 19.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMemoMiniTag(durationSeconds: Int) {
    Surface(
        shape = CircleShape,
        color = SkyBlue.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = stringResource(R.string.audio_memo_section_title),
                tint = DeepBlue,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = TaskAudioMemoManager.formatSeconds(durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = DeepBlue
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRowItem(
    task: PlannerItem,
    parentPlanTitle: String?,
    isOverdue: Boolean,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) SkyBlue.copy(alpha = 0.25f) else PureWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("today_task_${task.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.completed,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = DeepBlue,
                uncheckedColor = MutedNavy.copy(alpha = 0.55f),
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.testTag("today_task_checkbox_${task.id}")
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (task.completed) MutedNavy else MidnightNavy
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TaskCategoryBadge(category = task.taskCategory(), compact = true)
                if (parentPlanTitle != null) {
                    Text(
                        text = parentPlanTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepBlue
                    )
                }
                if (task.dueTimeMinutes != null) {
                    Text(
                        text = PlannerDateUtils.formatTimeOfDay(task.dueTimeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy
                    )
                }
                if (task.hasAudioMemo()) {
                    AudioMemoMiniTag(durationSeconds = task.audioMemoDurationSeconds)
                }
                if (isOverdue) {
                    Text(
                        text = "Overdue",
                        style = MaterialTheme.typography.labelSmall,
                        color = OverdueTerracotta
                    )
                }
            }
        }
        if (parentPlanTitle != null) {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                ForgetMeNotFlower(bloomed = task.completed, size = 19.dp)
            }
        }
    }
}

@Composable
private fun DueStepRowItem(
    step: UnifiedStep,
    parentTitle: String,
    isOverdue: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenParent: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenParent)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("today_step_${step.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = step.completed,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = DeepBlue,
                uncheckedColor = MutedNavy.copy(alpha = 0.55f),
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.testTag("today_step_checkbox_${step.id}")
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (step.completed) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (step.completed) MutedNavy else MidnightNavy
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = parentTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy
                )
                if (isOverdue) {
                    Text(
                        text = "Overdue step",
                        style = MaterialTheme.typography.labelSmall,
                        color = OverdueTerracotta
                    )
                }
            }
        }
        Box(modifier = Modifier.padding(end = 8.dp)) {
            ForgetMeNotFlower(bloomed = step.completed, size = 19.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanTodayCard(
    plan: PlannerItem,
    progress: ItemProgress,
    today: CalendarDate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dueDate = CalendarDate.parse(plan.dueDate)
    val accentColor = when {
        progress.overdueCount > 0 -> OverdueTerracotta
        progress.allStepsDone && progress.totalCount > 0 -> CalmSage
        else -> ForgetMeNotBlue
    }
    ModernPanel(
        containerColor = selectedPanelContainer(isSelected),
        borderColor = selectedPanelBorder(isSelected, progress.overdueCount > 0, OverdueTerracotta),
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_plan_${plan.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MidnightNavy
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            TaskCategoryBadge(category = plan.taskCategory(), compact = true)
                            if (dueDate != null) {
                                Text(
                                    text = "Deadline ${PlannerDateUtils.formatRelativeDateLabel(dueDate, today)} · ${PlannerDateUtils.formatCompactDate(dueDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedNavy
                                )
                            }
                            if (plan.hasAudioMemo()) {
                                AudioMemoMiniTag(durationSeconds = plan.audioMemoDurationSeconds)
                            }
                        }
                    }
                    if (progress.overdueCount > 0) {
                        Surface(
                            color = OverdueContainer,
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = OverdueTerracotta,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${progress.overdueCount} overdue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OverdueTerracotta
                                )
                            }
                        }
                    } else if (progress.allStepsDone && progress.totalCount > 0) {
                        ModernPillBadge(
                            text = "Done",
                            containerColor = CalmSageContainer,
                            contentColor = CalmSage
                        )
                    }
                }

                if (progress.totalCount > 0) {
                    FlowerProgressRow(
                        completed = progress.completedCount,
                        total = progress.totalCount,
                        summaryText = progress.summaryLabel(ItemType.PLAN)
                    )
                } else {
                    Text(
                        text = if (plan.completed) "Completed manually" else "No steps yet · Tap to add first step",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedNavy
                    )
                }
            }
        }
    }
}
