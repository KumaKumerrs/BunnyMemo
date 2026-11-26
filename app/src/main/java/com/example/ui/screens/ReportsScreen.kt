package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AccomplishmentHighlight
import com.example.data.AccomplishmentReportEngine
import com.example.data.CalendarDate
import com.example.data.MonthlyAccomplishmentReport
import com.example.data.YearEndAccomplishmentReport
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DailyHabitRepository
import com.example.ui.components.BouquetKeepsakeShelf
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyPose
import com.example.ui.components.FlowerProgressRow
import com.example.ui.components.ForgetMeNotFlower
import com.example.ui.components.ForgetMeNotHabitCalendarCard
import com.example.ui.components.ModernPanel
import com.example.ui.components.ModernPillBadge
import com.example.ui.components.ModernSegmentedToggle
import com.example.ui.components.MonthlyBunnyAlmanacModal
import com.example.ui.components.MonthlyCompletedTasksChartCard
import com.example.ui.components.MonthlyMilestoneTimeChartCard
import com.example.ui.components.ResponsiveCardGrid
import com.example.ui.components.SmallBunnyIllustration
import com.example.ui.components.TonalInsetPanel
import com.example.ui.components.YearBouquetCard
import com.example.ui.components.adaptiveHorizontalPadding
import com.example.ui.components.computeYearBouquetFlowerCount
import com.example.ui.theme.CalmSage
import com.example.ui.theme.CalmSageContainer
import com.example.ui.theme.CloudWhite
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
import com.example.viewmodel.PlannerUiState

private enum class ReportScope { MONTHLY, YEAR_END }

@Composable
fun ReportsScreen(
    state: PlannerUiState.Ready,
    onSelectItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = state.todayDate()
    var scope by rememberSaveable { mutableStateOf(ReportScope.MONTHLY) }
    var selectedYear by rememberSaveable { mutableIntStateOf(today.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(today.month) }
    var showBunnyAlmanac by rememberSaveable { mutableStateOf(false) }

    // Key on CalendarDate (today) instead of raw nowMillis so 60s ticks never re-run the report engine
    val monthlyReport = remember(selectedYear, selectedMonth, state.items, state.steps, today) {
        AccomplishmentReportEngine.buildMonthlyReport(
            year = selectedYear,
            month = selectedMonth,
            allItems = state.items,
            allSteps = state.steps,
            nowMillis = state.nowMillis
        )
    }

    val yearEndReport = remember(selectedYear, state.items, state.steps, today) {
        AccomplishmentReportEngine.buildYearEndReport(
            year = selectedYear,
            allItems = state.items,
            allSteps = state.steps,
            nowMillis = state.nowMillis
        )
    }

    val habitRepo = remember(context) { DailyHabitRepository.getInstance(context) }
    val habitSnapshot by habitRepo.snapshot.collectAsStateWithLifecycle()
    val yearBouquetCount = remember(selectedYear, state.items, state.steps, habitSnapshot.checkInCountByDate) {
        computeYearBouquetFlowerCount(
            year = selectedYear,
            items = state.items,
            steps = state.steps,
            habitCheckInCountByDate = habitSnapshot.checkInCountByDate,
            nowMillis = state.nowMillis
        )
    }

    fun shareText(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share keepsake report"))
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontalPad = adaptiveHorizontalPadding(maxWidth)
        val useWideMetricsRow = maxWidth >= 720.dp
        val cardColumns = if (maxWidth >= 700.dp) 2 else 1

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1080.dp)
                .testTag("reports_screen_list"),
            contentPadding = PaddingValues(start = horizontalPad, end = horizontalPad, top = 14.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Scope Toggle (Monthly Report vs Grand Year-End Report) & Period Switcher
        item(key = "reports_controls") {
            ModernPanel(
                shape = RoundedCornerShape(24.dp),
                elevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModernSegmentedToggle(
                        options = listOf(
                            ReportScope.MONTHLY to stringResource(R.string.report_monthly_tab),
                            ReportScope.YEAR_END to stringResource(R.string.report_year_end_tab)
                        ),
                        selected = scope,
                        onSelect = { scope = it },
                        tagPrefix = "report_scope"
                    )

                    // Period Navigator (Month + Year, or Year)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (scope == ReportScope.MONTHLY) {
                                    if (selectedMonth == 1) {
                                        selectedMonth = 12
                                        selectedYear -= 1
                                    } else {
                                        selectedMonth -= 1
                                    }
                                } else {
                                    selectedYear -= 1
                                }
                            },
                            modifier = Modifier.testTag("report_prev_period")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous period",
                                tint = MidnightNavy
                            )
                        }

                        AnimatedContent(
                            targetState = if (scope == ReportScope.MONTHLY) {
                                "${monthlyReport.monthName} $selectedYear" to "Monthly accomplishment report"
                            } else {
                                "$selectedYear Grand Year-End" to "12-month keepsake celebration"
                            },
                            transitionSpec = {
                                fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(160, easing = FastOutSlowInEasing))
                            },
                            label = "report_period_header"
                        ) { (headline, sub) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = headline,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MidnightNavy
                                )
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (scope == ReportScope.MONTHLY) {
                                    if (selectedMonth == 12) {
                                        selectedMonth = 1
                                        selectedYear += 1
                                    } else {
                                        selectedMonth += 1
                                    }
                                } else {
                                    selectedYear += 1
                                }
                            },
                            modifier = Modifier.testTag("report_next_period")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next period",
                                tint = MidnightNavy
                            )
                        }
                    }

                    // Quick month jump strip when viewing Monthly Reports
                    AnimatedVisibility(
                        visible = scope == ReportScope.MONTHLY,
                        enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            yearEndReport.monthlyReports.forEach { m ->
                                val isSel = m.month == selectedMonth
                                Surface(
                                    onClick = { selectedMonth = m.month },
                                    color = when {
                                        isSel -> DeepBlue
                                        m.totalBlooms > 0 -> SoftIceSurface
                                        else -> CloudWhite
                                    },
                                    shape = CircleShape,
                                    border = if (isSel) null else BorderStroke(0.75.dp, SubtleBorder),
                                    modifier = Modifier.testTag("report_month_pill_${m.month}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        ForgetMeNotFlower(
                                            bloomed = m.totalBlooms > 0,
                                            size = 14.dp
                                        )
                                        Text(
                                            text = m.shortMonthName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MidnightNavy
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (scope == ReportScope.MONTHLY) {
            // --- MONTHLY ACCOMPLISHMENT REPORT ---
            item(key = "monthly_hero") {
                MonthlyHeroCard(
                    report = monthlyReport,
                    onOpenAlmanac = { showBunnyAlmanac = true },
                    onShare = {
                        shareText(
                            subject = "${monthlyReport.monthName} ${monthlyReport.year} Accomplishment Report",
                            body = AccomplishmentReportEngine.formatMonthlyShareText(monthlyReport)
                        )
                    }
                )
            }

            item(key = "monthly_stats_grid") {
                ReportMetricsGrid(
                    plansCompleted = monthlyReport.plansCompleted,
                    plansTotal = monthlyReport.plansTotal,
                    milestonesCompleted = monthlyReport.milestonesCompleted,
                    milestonesTotal = monthlyReport.milestonesTotal,
                    tasksCompleted = monthlyReport.tasksCompleted,
                    tasksTotal = monthlyReport.tasksTotal,
                    eventsCompleted = monthlyReport.eventsCompleted,
                    eventsTotal = monthlyReport.eventsTotal,
                    singleRow = useWideMetricsRow
                )
            }

            item(key = "monthly_weekly_chart") {
                WeeklyBloomChartCard(
                    monthName = monthlyReport.monthName,
                    weeklyBlooms = monthlyReport.weeklyBlooms
                )
            }

            // Native Compose Canvas productivity charts (completed tasks & milestone effort)
            item(key = "monthly_productivity_charts") {
                if (useWideMetricsRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        MonthlyCompletedTasksChartCard(
                            report = monthlyReport,
                            modifier = Modifier.weight(1f)
                        )
                        MonthlyMilestoneTimeChartCard(
                            report = monthlyReport,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MonthlyCompletedTasksChartCard(report = monthlyReport)
                        MonthlyMilestoneTimeChartCard(report = monthlyReport)
                    }
                }
            }

            // Forget-Me-Not daily habit calendar (app usage tracker moved to Profile)
            item(key = "monthly_habits_calendar") {
                ForgetMeNotHabitCalendarCard(
                    items = state.items,
                    steps = state.steps,
                    today = today,
                    initialMonth = CalendarDate(selectedYear, selectedMonth, 1)
                )
            }

            item(key = "monthly_highlights_header") {
                Text(
                    text = "Finished in ${monthlyReport.monthName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightNavy,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            if (monthlyReport.highlights.isEmpty()) {
                item(key = "monthly_highlights_empty") {
                    ModernPanel(
                        shape = RoundedCornerShape(20.dp),
                        elevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Completed plans, tasks, and events from ${monthlyReport.monthName} will appear here as keepsake highlights.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedNavy,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                item(key = "monthly_highlights_grid") {
                    ResponsiveCardGrid(
                        items = monthlyReport.highlights,
                        columns = cardColumns
                    ) { highlight ->
                        HighlightRowCard(
                            highlight = highlight,
                            onClick = { onSelectItem(highlight.itemId) }
                        )
                    }
                }
            }
        } else {
            // --- GRAND YEAR-END ACCOMPLISHMENT REPORT ---
            item(key = "year_end_grand_hero") {
                GrandYearEndHeroCard(
                    report = yearEndReport,
                    onJumpToMonth = { m ->
                        selectedMonth = m
                        scope = ReportScope.MONTHLY
                    },
                    onOpenAlmanac = { showBunnyAlmanac = true },
                    onShare = {
                        shareText(
                            subject = "${yearEndReport.year} Grand Year-End Accomplishment Report",
                            body = AccomplishmentReportEngine.formatYearEndShareText(yearEndReport)
                        )
                    }
                )
            }

            // Annual Forget-Me-Not Bouquet filled with every bloomed task & handed out each year
            item(key = "year_end_bouquet_card") {
                YearBouquetCard(
                    year = selectedYear,
                    flowerCount = yearBouquetCount,
                    today = today,
                    dedicationText = state.profile.dedicationText
                )
            }

            item(key = "year_end_bouquet_shelf") {
                BouquetKeepsakeShelf(
                    dedicationText = state.profile.dedicationText
                )
            }

            item(key = "year_end_metrics") {
                ReportMetricsGrid(
                    plansCompleted = yearEndReport.plansCompleted,
                    plansTotal = yearEndReport.plansTotal,
                    milestonesCompleted = yearEndReport.milestonesCompleted,
                    milestonesTotal = yearEndReport.milestonesTotal,
                    tasksCompleted = yearEndReport.tasksCompleted,
                    tasksTotal = yearEndReport.tasksTotal,
                    eventsCompleted = yearEndReport.eventsCompleted,
                    eventsTotal = yearEndReport.eventsTotal,
                    singleRow = useWideMetricsRow
                )
            }

            item(key = "year_end_awards") {
                YearEndBadgesRow(report = yearEndReport)
            }

            item(key = "year_end_12_month_chart") {
                YearBloomTimelineCard(report = yearEndReport)
            }

            item(key = "year_end_rollup_header") {
                Text(
                    text = "Month-by-month garden",
                    style = MaterialTheme.typography.titleMedium,
                    color = MidnightNavy,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            item(key = "year_end_rollup_grid") {
                ResponsiveCardGrid(
                    items = yearEndReport.monthlyReports,
                    columns = cardColumns
                ) { monthRep ->
                    MonthRollupRowCard(
                        report = monthRep,
                        isPeak = yearEndReport.peakMonth?.month == monthRep.month && monthRep.totalBlooms > 0,
                        onOpenMonth = {
                            selectedMonth = monthRep.month
                            scope = ReportScope.MONTHLY
                        }
                    )
                }
            }

            if (yearEndReport.grandHighlights.isNotEmpty()) {
                item(key = "year_end_highlights_header") {
                    Text(
                        text = "Cherished highlights of ${yearEndReport.year}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MidnightNavy,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                item(key = "year_end_highlights_grid") {
                    ResponsiveCardGrid(
                        items = yearEndReport.grandHighlights,
                        columns = cardColumns
                    ) { highlight ->
                        HighlightRowCard(
                            highlight = highlight,
                            onClick = { onSelectItem(highlight.itemId) }
                        )
                    }
                }
            }
        }
    }

    if (showBunnyAlmanac) {
        MonthlyBunnyAlmanacModal(
            highlightedMonth = if (scope == ReportScope.MONTHLY) selectedMonth else today.month,
            onDismiss = { showBunnyAlmanac = false },
            onSelectMonth = { pickedMonth ->
                selectedMonth = pickedMonth
                scope = ReportScope.MONTHLY
            }
        )
    }
    }
}

@Composable
private fun MonthlyHeroCard(
    report: MonthlyAccomplishmentReport,
    onOpenAlmanac: () -> Unit,
    onShare: () -> Unit
) {
    val monthBreed = remember(report.month) { BunnyBreed.forMonth(report.month) }
    ModernPanel(
        shape = RoundedCornerShape(26.dp),
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_report_hero_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SkyBlue.copy(alpha = 0.16f),
                            PureWhite,
                            PureWhite
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SoftIceSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenAlmanac)
                        .testTag("report_monthly_bunny_badge")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SmallBunnyIllustration(
                            pose = if (report.totalBlooms > 0) BunnyPose.CELEBRATING else BunnyPose.RESTING,
                            breed = monthBreed,
                            size = 64.dp,
                            contentDesc = stringResource(
                                R.string.cd_monthly_bunny_badge,
                                monthBreed.monthName,
                                monthBreed.breedName
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${report.totalBlooms}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MidnightNavy
                    )
                    Text(
                        text = if (report.totalBlooms == 1) "Forget-me-not bloomed" else "Forget-me-nots bloomed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MutedNavy
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SoftIceSurface,
                            shape = CircleShape,
                            border = BorderStroke(0.75.dp, SubtleBorder),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenAlmanac)
                        ) {
                            Text(
                                text = "${report.shortMonthName} · ${monthBreed.breedName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = DeepBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (report.completionPercent > 0) {
                            ModernPillBadge(
                                text = "${report.completionPercent}% done",
                                containerColor = CalmSageContainer,
                                contentColor = CalmSage
                            )
                        }
                    }
                }
            }

            if (report.totalBlooms > 0) {
                FlowerProgressRow(
                    completed = report.totalBlooms.coerceAtMost(10),
                    total = report.totalBlooms.coerceIn(1, 10),
                    summaryText = "",
                    flowerSize = 22.dp
                )
            }

            Text(
                text = report.reflectionMessage,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MutedNavy
            )

            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.testTag("share_monthly_report_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share ${report.monthName} keepsake")
            }
        }
    }
}

@Composable
private fun GrandYearEndHeroCard(
    report: YearEndAccomplishmentReport,
    onJumpToMonth: (Int) -> Unit,
    onOpenAlmanac: () -> Unit,
    onShare: () -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(28.dp),
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grand_year_end_hero_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PollenSoft.copy(alpha = 0.55f),
                            PureWhite,
                            PureWhite
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = PureWhite,
                shape = CircleShape,
                border = BorderStroke(0.75.dp, SubtleBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${report.year} GRAND YEAR-END KEEPSAKE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MidnightNavy
                    )
                }
            }

            SmallBunnyIllustration(
                pose = if (report.totalBlooms > 0) BunnyPose.CELEBRATING else BunnyPose.GREETING,
                size = 92.dp,
                contentDesc = stringResource(R.string.cd_bunny_celebrating),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenAlmanac)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${report.totalBlooms} Blooms in ${report.year}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MidnightNavy,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${report.activeMonthsCount} of 12 months bloomed · ${report.overallCompletionPercent}% overall completion",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedNavy,
                    textAlign = TextAlign.Center
                )
            }

            // 12-Month Forget-Me-Not Garden inside borderless tonal inset
            TonalInsetPanel(
                containerColor = SoftIceSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Your 12-month garden (tap a flower to inspect)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    report.monthlyReports.chunked(6).forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowMonths.forEach { m ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onJumpToMonth(m.month) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .testTag("garden_flower_${m.month}")
                                ) {
                                    ForgetMeNotFlower(
                                        bloomed = m.totalBlooms > 0,
                                        size = 26.dp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = m.shortMonthName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MidnightNavy
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = report.grandReflection,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MutedNavy,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_year_end_report_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share ${report.year} Grand Report")
            }
        }
    }
}

@Composable
private fun ReportMetricsGrid(
    plansCompleted: Int,
    plansTotal: Int,
    milestonesCompleted: Int,
    milestonesTotal: Int,
    tasksCompleted: Int,
    tasksTotal: Int,
    eventsCompleted: Int,
    eventsTotal: Int,
    singleRow: Boolean = false
) {
    if (singleRow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                title = "Plans finished",
                value = "$plansCompleted / $plansTotal",
                icon = Icons.Outlined.Flag,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Steps bloomed",
                value = "$milestonesCompleted / $milestonesTotal",
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Tasks completed",
                value = "$tasksCompleted / $tasksTotal",
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Events enjoyed",
                value = "$eventsCompleted / $eventsTotal",
                icon = Icons.Outlined.EventAvailable,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Plans finished",
                    value = "$plansCompleted / $plansTotal",
                    icon = Icons.Outlined.Flag,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Steps bloomed",
                    value = "$milestonesCompleted / $milestonesTotal",
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Tasks completed",
                    value = "$tasksCompleted / $tasksTotal",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Events enjoyed",
                    value = "$eventsCompleted / $eventsTotal",
                    icon = Icons.Outlined.EventAvailable,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    ModernPanel(
        shape = RoundedCornerShape(20.dp),
        elevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = SoftIceSurface,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MidnightNavy
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MutedNavy
            )
        }
    }
}

@Composable
private fun YearEndBadgesRow(report: YearEndAccomplishmentReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModernPanel(
            shape = RoundedCornerShape(20.dp),
            elevation = 2.dp,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = PollenSoft,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "Brightest month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy
                )
                Text(
                    text = report.peakMonth?.let { "${it.monthName} (${it.totalBlooms})" } ?: "Waiting to bloom",
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
            }
        }
        ModernPanel(
            shape = RoundedCornerShape(20.dp),
            elevation = 2.dp,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = SoftIceSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ForgetMeNotFlower(bloomed = report.longestActiveMonthStreak > 0, size = 18.dp)
                    }
                }
                Text(
                    text = "Longest streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedNavy
                )
                Text(
                    text = "${report.longestActiveMonthStreak} ${if (report.longestActiveMonthStreak == 1) "month" else "months"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
            }
        }
    }
}

@Composable
private fun WeeklyBloomChartCard(
    monthName: String,
    weeklyBlooms: List<Int>
) {
    val maxVal = (weeklyBlooms.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = listOf("Wk 1", "Wk 2", "Wk 3", "Wk 4", "Wk 5")
    val chartDesc = remember(monthName, weeklyBlooms) {
        val summary = labels.mapIndexed { idx, lbl -> "$lbl: ${weeklyBlooms.getOrElse(idx) { 0 }}" }.joinToString(", ")
        "$monthName weekly blooms bar chart. $summary"
    }

    ModernPanel(
        shape = RoundedCornerShape(22.dp),
        elevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$monthName weekly rhythm",
                style = MaterialTheme.typography.titleMedium,
                color = MidnightNavy
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .semantics { contentDescription = chartDesc }
            ) {
                val count = weeklyBlooms.size
                if (count == 0) return@Canvas
                val slotW = size.width / count
                val barW = (slotW * 0.46f).coerceAtMost(38.dp.toPx())
                val chartH = size.height - 10.dp.toPx()

                weeklyBlooms.forEachIndexed { idx, value ->
                    val ratio = value.toFloat() / maxVal.toFloat()
                    val barH = if (value == 0) 6.dp.toPx() else (chartH * ratio).coerceAtLeast(14.dp.toPx())
                    val left = idx * slotW + (slotW - barW) / 2f
                    val top = size.height - barH
                    val isPeak = value > 0 && value == maxVal

                    drawRoundRect(
                        color = when {
                            value == 0 -> SkyBlue.copy(alpha = 0.45f)
                            isPeak -> DeepBlue
                            else -> ForgetMeNotBlue
                        },
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                    if (isPeak) {
                        drawCircle(
                            color = PollenYellow,
                            radius = 4.dp.toPx(),
                            center = Offset(left + barW / 2f, (top - 7.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, lbl ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${weeklyBlooms.getOrElse(i) { 0 }}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MidnightNavy
                        )
                        Text(
                            text = lbl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearBloomTimelineCard(report: YearEndAccomplishmentReport) {
    val values = report.monthlyReports.map { it.totalBlooms }
    val maxVal = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val chartDesc = remember(report) {
        val summary = report.monthlyReports.joinToString(", ") { "${it.shortMonthName}: ${it.totalBlooms}" }
        "${report.year} blooms across all 12 months bar chart. $summary"
    }

    ModernPanel(
        shape = RoundedCornerShape(22.dp),
        elevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${report.year} blooms across all 12 months",
                style = MaterialTheme.typography.titleMedium,
                color = MidnightNavy
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .semantics { contentDescription = chartDesc }
            ) {
                val count = values.size
                if (count == 0) return@Canvas
                val slotW = size.width / count
                val barW = (slotW * 0.56f).coerceAtMost(22.dp.toPx())
                val chartH = size.height - 14.dp.toPx()

                values.forEachIndexed { idx, value ->
                    val ratio = value.toFloat() / maxVal.toFloat()
                    val barH = if (value == 0) 5.dp.toPx() else (chartH * ratio).coerceAtLeast(12.dp.toPx())
                    val left = idx * slotW + (slotW - barW) / 2f
                    val top = size.height - barH
                    val isPeak = value > 0 && value == maxVal

                    drawRoundRect(
                        color = when {
                            value == 0 -> SkyBlue.copy(alpha = 0.4f)
                            isPeak -> DeepBlue
                            else -> ForgetMeNotBlue
                        },
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    if (isPeak) {
                        drawCircle(
                            color = PollenYellow,
                            radius = 4.dp.toPx(),
                            center = Offset(left + barW / 2f, (top - 7.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                report.monthlyReports.forEach { m ->
                    Text(
                        text = m.shortMonthName.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (m.totalBlooms > 0) MidnightNavy else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthRollupRowCard(
    report: MonthlyAccomplishmentReport,
    isPeak: Boolean,
    onOpenMonth: () -> Unit
) {
    ModernPanel(
        containerColor = PureWhite,
        borderColor = if (isPeak) PollenYellow else SubtleBorder.copy(alpha = 0.85f),
        borderWidth = if (isPeak) 1.dp else 0.75.dp,
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp,
        onClick = onOpenMonth,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("year_rollup_month_${report.month}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForgetMeNotFlower(bloomed = report.totalBlooms > 0, size = 24.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = report.monthName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MidnightNavy
                    )
                    if (isPeak) {
                        ModernPillBadge(
                            text = "Peak",
                            containerColor = PollenSoft,
                            contentColor = MidnightNavy
                        )
                    }
                }
                Text(
                    text = if (report.totalBlooms == 0) {
                        "Quiet month"
                    } else {
                        "${report.plansCompleted} plans · ${report.tasksCompleted} tasks · ${report.eventsCompleted} events"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            }
            ModernPillBadge(
                text = "${report.totalBlooms} ${if (report.totalBlooms == 1) "bloom" else "blooms"}",
                containerColor = if (report.totalBlooms > 0) SkyBlue.copy(alpha = 0.55f) else SoftIceSurface,
                contentColor = if (report.totalBlooms > 0) DeepBlue else MutedNavy
            )
        }
    }
}

@Composable
private fun HighlightRowCard(
    highlight: AccomplishmentHighlight,
    onClick: () -> Unit
) {
    ModernPanel(
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_highlight_${highlight.itemId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForgetMeNotFlower(bloomed = true, size = 22.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlight.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${highlight.dateLabel} · ${highlight.detailLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            }
            ModernPillBadge(
                text = highlight.type.label,
                containerColor = SoftIceSurface,
                contentColor = MutedNavy
            )
        }
    }
}
