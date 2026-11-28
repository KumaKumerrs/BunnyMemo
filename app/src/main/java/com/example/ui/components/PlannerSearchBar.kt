package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CalendarDate
import com.example.data.ClassScheduleItem
import com.example.data.ItemType
import com.example.data.PlannerDateUtils
import com.example.data.PlannerItem
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState
import androidx.compose.foundation.shape.CircleShape

enum class PlannerSearchFilter(val label: String) {
    ALL("All"),
    TASKS("Tasks & Notes"),
    EVENTS("Events"),
    PLANS("Plans"),
    CLASSES("Classes")
}

private data class MatchedPlannerItem(
    val item: PlannerItem,
    val matchedStepTitle: String? = null
)

/**
 * Sleek pill-shaped search bar anchored at the top of the planner workspace.
 */
@Composable
fun PlannerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val hasQuery = query.isNotBlank()

    ModernPanel(
        shape = CircleShape,
        containerColor = PureWhite,
        borderColor = if (hasQuery) DeepBlue.copy(alpha = 0.55f) else SubtleBorder.copy(alpha = 0.95f),
        borderWidth = if (hasQuery) 1.25.dp else 0.75.dp,
        elevation = if (hasQuery) 4.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search_icon_cd),
                tint = if (hasQuery) DeepBlue else MutedNavy,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MidnightNavy),
                    cursorBrush = SolidColor(DeepBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("planner_search_input")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.testTag("planner_search_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.search_clear_cd),
                        tint = MutedNavy,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Live keyword search results list across Tasks, Quick Notes, Events, Plans, Milestones, and Classes.
 */
@Composable
fun PlannerSearchResultsView(
    query: String,
    state: PlannerUiState.Ready,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    onToggleTask: (PlannerItem, Boolean) -> Unit,
    onEditClass: (ClassScheduleItem) -> Unit,
    onClearSearch: () -> Unit,
    onSaveQueryAsQuickNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalized = query.trim().lowercase()
    val today = state.todayDate()
    var activeFilter by rememberSaveable { mutableStateOf(PlannerSearchFilter.ALL) }
    var priorityFilter by rememberSaveable { mutableStateOf(TaskPriorityFilter.ALL) }
    var categoryFilter by rememberSaveable { mutableStateOf(TaskCategoryFilter.ALL) }

    val matchedItems = remember(state.items, state.steps, normalized) {
        if (normalized.isEmpty()) {
            emptyList()
        } else {
            val stepsByItemId = state.steps.groupBy { it.itemId }
            state.items.mapNotNull { item ->
                val titleMatch = item.title.lowercase().contains(normalized)
                val notesMatch = item.notes?.lowercase()?.contains(normalized) == true
                val matchedStep = stepsByItemId[item.id]
                    ?.firstOrNull { it.title.lowercase().contains(normalized) }
                    ?.title
                if (titleMatch || notesMatch || matchedStep != null) {
                    MatchedPlannerItem(
                        item = item,
                        matchedStepTitle = if (!titleMatch && !notesMatch) matchedStep else null
                    )
                } else {
                    null
                }
            }.sortedWith(
                compareByDescending<MatchedPlannerItem> {
                    it.item.title.lowercase().startsWith(normalized)
                }
                    .thenBy { it.item.completed }
                    .thenBy {
                        if (it.item.itemType() == ItemType.TASK) it.item.taskPriority().sortRank else 99
                    }
                    .thenBy { it.item.dueDate ?: "9999-99-99" }
            )
        }
    }

    val matchedClasses = remember(state.classSchedules, normalized) {
        if (normalized.isEmpty()) {
            emptyList()
        } else {
            state.classSchedules.filter { cls ->
                cls.subject.lowercase().contains(normalized) ||
                    cls.room?.lowercase()?.contains(normalized) == true
            }.sortedBy { it.startTimeMinutes }
        }
    }

    val matchedTasks = remember(matchedItems) { matchedItems.filter { it.item.itemType() == ItemType.TASK } }
    val taskCount = matchedTasks.size
    val eventCount = remember(matchedItems) { matchedItems.count { it.item.itemType() == ItemType.EVENT } }
    val planCount = remember(matchedItems) { matchedItems.count { it.item.itemType() == ItemType.PLAN } }
    val classCount = matchedClasses.size
    val totalCount = matchedItems.size + classCount

    val priorityCounts = remember(matchedTasks) {
        mapOf(
            TaskPriorityFilter.ALL to matchedTasks.size,
            TaskPriorityFilter.HIGH to matchedTasks.count { it.item.taskPriority() == com.example.data.TaskPriority.HIGH },
            TaskPriorityFilter.MEDIUM to matchedTasks.count { it.item.taskPriority() == com.example.data.TaskPriority.MEDIUM },
            TaskPriorityFilter.LOW to matchedTasks.count { it.item.taskPriority() == com.example.data.TaskPriority.LOW }
        )
    }

    val showPriorityChips = (activeFilter == PlannerSearchFilter.ALL || activeFilter == PlannerSearchFilter.TASKS) &&
        (matchedTasks.isNotEmpty() || priorityFilter != TaskPriorityFilter.ALL)

    val categoryCounts = remember(matchedItems) {
        buildMap {
            put(TaskCategoryFilter.ALL, matchedItems.size)
            com.example.data.TaskCategory.entries.forEach { cat ->
                val filterKey = TaskCategoryFilter.entries.firstOrNull { it.category == cat }
                if (filterKey != null) {
                    put(filterKey, matchedItems.count { it.item.taskCategory() == cat })
                }
            }
        }
    }

    val filteredItems = remember(matchedItems, activeFilter, priorityFilter, categoryFilter) {
        val base = when (activeFilter) {
            PlannerSearchFilter.ALL -> matchedItems
            PlannerSearchFilter.TASKS -> matchedItems.filter { it.item.itemType() == ItemType.TASK }
            PlannerSearchFilter.EVENTS -> matchedItems.filter { it.item.itemType() == ItemType.EVENT }
            PlannerSearchFilter.PLANS -> matchedItems.filter { it.item.itemType() == ItemType.PLAN }
            PlannerSearchFilter.CLASSES -> emptyList()
        }
        val targetPriority = priorityFilter.priority
        val priorityFiltered = if (
            targetPriority != null &&
            (activeFilter == PlannerSearchFilter.ALL || activeFilter == PlannerSearchFilter.TASKS)
        ) {
            base.filter { it.item.itemType() == ItemType.TASK && it.item.taskPriority() == targetPriority }
        } else {
            base
        }
        val targetCategory = categoryFilter.category
        if (targetCategory != null) {
            priorityFiltered.filter { it.item.taskCategory() == targetCategory }
        } else {
            priorityFiltered
        }
    }

    val filteredClasses = remember(matchedClasses, activeFilter, priorityFilter, categoryFilter) {
        val isFilteredByPriority = priorityFilter != TaskPriorityFilter.ALL &&
            (activeFilter == PlannerSearchFilter.ALL || activeFilter == PlannerSearchFilter.TASKS)
        val isFilteredByCategory = categoryFilter != TaskCategoryFilter.ALL
        if (!isFilteredByPriority && !isFilteredByCategory &&
            (activeFilter == PlannerSearchFilter.ALL || activeFilter == PlannerSearchFilter.CLASSES)
        ) {
            matchedClasses
        } else {
            emptyList()
        }
    }
    val hasActiveFilters = activeFilter != PlannerSearchFilter.ALL ||
        priorityFilter != TaskPriorityFilter.ALL ||
        categoryFilter != TaskCategoryFilter.ALL

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPad = adaptiveHorizontalPadding(maxWidth)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("planner_search_results_list"),
            contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 6.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter chips row with live counts
            item(key = "search_filter_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        PlannerSearchFilter.ALL to totalCount,
                        PlannerSearchFilter.TASKS to taskCount,
                        PlannerSearchFilter.EVENTS to eventCount,
                        PlannerSearchFilter.PLANS to planCount,
                        PlannerSearchFilter.CLASSES to classCount
                    )
                    chips.forEach { (filter, count) ->
                        val selected = activeFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { activeFilter = filter },
                            label = {
                                Text("${filter.label} ($count)")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SkyBlue.copy(alpha = 0.75f),
                                selectedLabelColor = MidnightNavy,
                                containerColor = PureWhite,
                                labelColor = MutedNavy
                            ),
                            modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (showPriorityChips) {
                item(key = "search_priority_filter_chips") {
                    TaskPriorityFilterBar(
                        selectedFilter = priorityFilter,
                        counts = priorityCounts,
                        onSelectFilter = { priorityFilter = it },
                        tagPrefix = "search_priority_filter"
                    )
                }
            }

            if (matchedItems.isNotEmpty() || categoryFilter != TaskCategoryFilter.ALL) {
                item(key = "search_category_filter_chips") {
                    TaskCategoryFilterBar(
                        selectedFilter = categoryFilter,
                        counts = categoryCounts,
                        onSelectFilter = { categoryFilter = it },
                        tagPrefix = "search_category_filter"
                    )
                }
            }

            if (filteredItems.isEmpty() && filteredClasses.isEmpty()) {
                item(key = "search_empty_state") {
                    ModernPanel(
                        shape = MaterialTheme.shapes.large,
                        elevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_empty_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SmallBunnyIllustration(
                                pose = BunnyPose.RESTING,
                                size = 86.dp,
                                contentDesc = stringResource(R.string.cd_bunny_resting)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.search_no_results_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MidnightNavy
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasActiveFilters) {
                                    "Try resetting your active filters or clearing the search."
                                } else {
                                    stringResource(R.string.search_no_results_subtitle)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedNavy
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasActiveFilters) {
                                    OutlinedButton(
                                        onClick = {
                                            activeFilter = PlannerSearchFilter.ALL
                                            priorityFilter = TaskPriorityFilter.ALL
                                            categoryFilter = TaskCategoryFilter.ALL
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("search_reset_filters_button")
                                    ) {
                                        Text("Reset filters", color = DeepBlue)
                                    }
                                }
                                OutlinedButton(
                                    onClick = onClearSearch,
                                    shape = CircleShape,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.search_clear_action), color = MidnightNavy)
                                }
                                Button(
                                    onClick = { onSaveQueryAsQuickNote(query.trim()) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeepBlue,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("search_save_quick_note_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.search_save_as_quick_note))
                                }
                            }
                        }
                    }
                }
            } else {
                if (filteredItems.isNotEmpty()) {
                    item(key = "search_items_header") {
                        ModernSectionHeader(
                            title = "Matching planner items",
                            count = filteredItems.size
                        )
                    }
                    items(filteredItems, key = { "search_item_${it.item.id}" }) { match ->
                        SearchResultItemCard(
                            match = match,
                            state = state,
                            today = today,
                            isSelected = match.item.id == selectedItemId,
                            onToggleTask = { checked -> onToggleTask(match.item, checked) },
                            onClick = { onSelectItem(match.item.id) }
                        )
                    }
                }

                if (filteredClasses.isNotEmpty()) {
                    item(key = "search_classes_header") {
                        ModernSectionHeader(
                            title = "Matching classes",
                            count = filteredClasses.size
                        )
                    }
                    items(filteredClasses, key = { "search_cls_${it.id}" }) { cls ->
                        SearchResultClassCard(
                            cls = cls,
                            onClick = { onEditClass(cls) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultItemCard(
    match: MatchedPlannerItem,
    state: PlannerUiState.Ready,
    today: CalendarDate,
    isSelected: Boolean,
    onToggleTask: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val item = match.item
    val itemType = item.itemType()
    val isQuickNote = itemType == ItemType.TASK && item.dueDate.isNullOrBlank() && item.parentPlanId.isNullOrBlank()
    val progress = state.progressFor(item)

    val (badgeLabel, badgeContainer, badgeContent, accentBarColor) = when {
        isQuickNote -> Quadruple("Quick Note", PollenSoft, MidnightNavy, DeepBlue)
        itemType == ItemType.EVENT -> Quadruple("Event", SkyBlue.copy(alpha = 0.65f), DeepBlue, DeepBlue)
        itemType == ItemType.PLAN -> Quadruple("Plan", SoftIceSurface, MidnightNavy, ForgetMeNotBlue)
        else -> Quadruple("Task", CalmSageContainer, CalmSage, CalmSage)
    }

    val typeIcon = when {
        isQuickNote -> Icons.Outlined.EditNote
        itemType == ItemType.EVENT -> Icons.Outlined.Event
        itemType == ItemType.PLAN -> Icons.Outlined.Flag
        else -> Icons.Outlined.TaskAlt
    }

    val dateSummary = remember(item, today) {
        when (itemType) {
            ItemType.EVENT -> {
                val dateLabel = item.primaryCalendarDate()?.let {
                    "${PlannerDateUtils.formatRelativeDateLabel(it, today)} · ${PlannerDateUtils.formatCompactDate(it)}"
                }
                val timeRange = PlannerDateUtils.formatEventTimeRange(item.startAtUtcMillis, item.endAtUtcMillis)
                listOfNotNull(dateLabel, timeRange.takeIf { it.isNotBlank() }).joinToString(" · ")
            }
            ItemType.PLAN -> {
                val due = CalendarDate.parse(item.dueDate)
                if (due != null) {
                    "Deadline ${PlannerDateUtils.formatRelativeDateLabel(due, today)} · ${PlannerDateUtils.formatCompactDate(due)}"
                } else {
                    "Ongoing plan"
                }
            }
            ItemType.TASK -> {
                val due = CalendarDate.parse(item.dueDate)
                if (due != null) {
                    val timePart = item.dueTimeMinutes?.let { " · ${PlannerDateUtils.formatTimeOfDay(it)}" } ?: ""
                    "Due ${PlannerDateUtils.formatRelativeDateLabel(due, today)} (${PlannerDateUtils.formatCompactDate(due)})$timePart"
                } else {
                    "Saved spontaneous note"
                }
            }
        }
    }

    ModernPanel(
        containerColor = selectedPanelContainer(isSelected),
        borderColor = selectedPanelBorder(isSelected),
        shape = MaterialTheme.shapes.medium,
        elevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_${item.id}")
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
                    .background(accentBarColor)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (itemType == ItemType.TASK) {
                    Checkbox(
                        checked = item.completed,
                        onCheckedChange = onToggleTask,
                        colors = CheckboxDefaults.colors(
                            checkedColor = DeepBlue,
                            uncheckedColor = MutedNavy.copy(alpha = 0.55f),
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("search_task_checkbox_${item.id}")
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp, start = 4.dp)
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(badgeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = badgeContent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.completed) MutedNavy else MidnightNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TaskCategoryBadge(
                            category = item.taskCategory(),
                            compact = true
                        )
                        if (itemType == ItemType.TASK) {
                            TaskPriorityBadge(
                                priority = item.taskPriority(),
                                compact = true
                            )
                        }
                        ModernPillBadge(
                            text = badgeLabel,
                            containerColor = badgeContainer,
                            contentColor = badgeContent
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MutedNavy,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = dateSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!item.notes.isNullOrBlank()) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedNavy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!match.matchedStepTitle.isNullOrBlank()) {
                        Text(
                            text = "Matches step: ${match.matchedStepTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (progress.totalCount > 0 && itemType != ItemType.TASK) {
                        FlowerProgressRow(
                            completed = progress.completedCount,
                            total = progress.totalCount,
                            summaryText = progress.summaryLabel(itemType),
                            flowerSize = 16.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultClassCard(
    cls: ClassScheduleItem,
    onClick: () -> Unit
) {
    val (fill, accent) = classColorPalette(cls.colorKeyEnum())
    ModernPanel(
        shape = MaterialTheme.shapes.medium,
        borderColor = accent.copy(alpha = 0.35f),
        elevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_class_${cls.id}")
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
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.small)
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
                        append(" · ")
                        append(cls.repeatSummaryLabel())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ModernPillBadge(
                text = "Class",
                containerColor = fill,
                contentColor = accent
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
