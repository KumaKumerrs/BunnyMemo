package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class AccomplishmentHighlight(
    val itemId: String,
    val title: String,
    val type: ItemType,
    val dateLabel: String,
    val detailLabel: String
)

data class CategoryTaskSummary(
    val category: TaskCategory,
    val completedCount: Int,
    val totalCount: Int
)

data class MilestoneTimeEntry(
    val stepId: String,
    val stepTitle: String,
    val parentTitle: String,
    val minutesSpent: Int,
    val completed: Boolean
)

data class MonthlyAccomplishmentReport(
    val year: Int,
    val month: Int, // 1..12
    val monthName: String,
    val shortMonthName: String,
    val totalBlooms: Int,
    val plansCompleted: Int,
    val plansTotal: Int,
    val milestonesCompleted: Int,
    val milestonesTotal: Int,
    val tasksCompleted: Int,
    val tasksTotal: Int,
    val eventsCompleted: Int,
    val eventsTotal: Int,
    val completionPercent: Int,
    val weeklyBlooms: List<Int>, // 5 buckets: weeks 1..5
    val weeklyCompletedTasks: List<Int>, // 5 buckets: weeks 1..5
    val weeklyMilestoneMinutes: List<Int>, // 5 buckets: weeks 1..5
    val totalMilestoneMinutes: Int,
    val categoryBreakdown: List<CategoryTaskSummary>,
    val topTimedMilestones: List<MilestoneTimeEntry>,
    val highlights: List<AccomplishmentHighlight>,
    val reflectionMessage: String
) {
    val hasAnyActivity: Boolean
        get() = totalBlooms > 0 || plansTotal > 0 || tasksTotal > 0 || eventsTotal > 0 || totalMilestoneMinutes > 0
}

data class YearEndAccomplishmentReport(
    val year: Int,
    val monthlyReports: List<MonthlyAccomplishmentReport>, // always 12 entries (Jan..Dec)
    val totalBlooms: Int,
    val plansCompleted: Int,
    val plansTotal: Int,
    val milestonesCompleted: Int,
    val milestonesTotal: Int,
    val tasksCompleted: Int,
    val tasksTotal: Int,
    val eventsCompleted: Int,
    val eventsTotal: Int,
    val totalMilestoneMinutes: Int,
    val overallCompletionPercent: Int,
    val activeMonthsCount: Int,
    val longestActiveMonthStreak: Int,
    val peakMonth: MonthlyAccomplishmentReport?,
    val grandHighlights: List<AccomplishmentHighlight>,
    val grandReflection: String
)

object AccomplishmentReportEngine {

    private fun monthNames(year: Int, month: Int): Pair<String, String> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val full = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
        val short = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
        return full to short
    }

    private fun dateForItem(item: PlannerItem): CalendarDate {
        return item.primaryCalendarDate()
            ?: item.updatedAt?.let { CalendarDate.fromUtcMillis(it.toDate().time) }
            ?: item.createdAt?.let { CalendarDate.fromUtcMillis(it.toDate().time) }
            ?: CalendarDate.today()
    }

    private fun dateForStep(step: PlannerStep, parent: PlannerItem?): CalendarDate {
        return CalendarDate.parse(step.dueDate)
            ?: parent?.let { dateForItem(it) }
            ?: step.updatedAt?.let { CalendarDate.fromUtcMillis(it.toDate().time) }
            ?: step.createdAt?.let { CalendarDate.fromUtcMillis(it.toDate().time) }
            ?: CalendarDate.today()
    }

    private fun isEventAccomplished(
        event: PlannerItem,
        progress: ItemProgress,
        nowMillis: Long
    ): Boolean {
        val endMs = event.endAtUtcMillis ?: event.startAtUtcMillis?.plus(3_600_000L)
        val tookPlace = endMs != null && endMs <= nowMillis
        val fullyPrepped = progress.totalCount > 0 && progress.allStepsDone
        return tookPlace || event.completed || fullyPrepped
    }

    fun buildMonthlyReport(
        year: Int,
        month: Int,
        allItems: List<PlannerItem>,
        allSteps: List<PlannerStep>,
        nowMillis: Long
    ): MonthlyAccomplishmentReport {
        val (fullMonth, shortMonth) = monthNames(year, month)
        val itemMap = allItems.associateBy { it.id }

        val monthItems = allItems.filter { item ->
            val d = dateForItem(item)
            d.year == year && d.month == month
        }

        val plans = monthItems.filter { it.itemType() == ItemType.PLAN }
        val tasks = monthItems.filter { it.itemType() == ItemType.TASK }
        val events = monthItems.filter { it.itemType() == ItemType.EVENT }

        val monthSteps = allSteps.filter { step ->
            val d = dateForStep(step, itemMap[step.itemId])
            d.year == year && d.month == month
        }

        val completedPlans = plans.filter { plan ->
            PlannerLogic.computeProgress(plan, allSteps, allItems, nowMillis).allStepsDone
        }
        val completedTasks = tasks.filter { it.completed }
        val completedEvents = events.filter { ev ->
            val prog = PlannerLogic.computeProgress(ev, allSteps, allItems, nowMillis)
            isEventAccomplished(ev, prog, nowMillis)
        }
        val completedSteps = monthSteps.filter { it.completed }

        // Weekly blooms breakdown (5 buckets: days 1-7, 8-14, 15-21, 22-28, 29-31)
        val weeklyBuckets = MutableList(5) { 0 }
        val weeklyCompletedTaskBuckets = MutableList(5) { 0 }
        val weeklyMilestoneMinuteBuckets = MutableList(5) { 0 }

        fun addToWeek(date: CalendarDate) {
            val idx = ((date.day - 1) / 7).coerceIn(0, 4)
            weeklyBuckets[idx] = weeklyBuckets[idx] + 1
        }

        completedSteps.forEach { step ->
            addToWeek(dateForStep(step, itemMap[step.itemId]))
        }
        completedTasks.forEach { task ->
            val d = dateForItem(task)
            addToWeek(d)
            val idx = ((d.day - 1) / 7).coerceIn(0, 4)
            weeklyCompletedTaskBuckets[idx] = weeklyCompletedTaskBuckets[idx] + 1
        }
        completedPlans.forEach { plan ->
            val prog = PlannerLogic.computeProgress(plan, allSteps, allItems, nowMillis)
            if (prog.totalCount == 0) addToWeek(dateForItem(plan))
        }
        completedEvents.forEach { ev ->
            val prog = PlannerLogic.computeProgress(ev, allSteps, allItems, nowMillis)
            if (prog.totalCount == 0) addToWeek(dateForItem(ev))
        }

        monthSteps.forEach { step ->
            val mins = step.timeSpentMinutes.coerceAtLeast(0)
            if (mins > 0) {
                val d = dateForStep(step, itemMap[step.itemId])
                val idx = ((d.day - 1) / 7).coerceIn(0, 4)
                weeklyMilestoneMinuteBuckets[idx] = weeklyMilestoneMinuteBuckets[idx] + mins
            }
        }
        val totalMilestoneMinutes = monthSteps.sumOf { it.timeSpentMinutes.coerceAtLeast(0) }

        val topTimedMilestones = monthSteps
            .filter { it.timeSpentMinutes > 0 }
            .sortedByDescending { it.timeSpentMinutes }
            .take(5)
            .map { step ->
                MilestoneTimeEntry(
                    stepId = step.id,
                    stepTitle = step.title,
                    parentTitle = itemMap[step.itemId]?.title ?: "Plan",
                    minutesSpent = step.timeSpentMinutes,
                    completed = step.completed
                )
            }

        val categoryBreakdown = TaskCategory.entries.mapNotNull { cat ->
            val inCat = monthItems.filter { it.taskCategory() == cat }
            if (inCat.isEmpty()) {
                null
            } else {
                val doneCount = inCat.count { item ->
                    when (item.itemType()) {
                        ItemType.PLAN -> PlannerLogic.computeProgress(item, allSteps, allItems, nowMillis).allStepsDone
                        ItemType.EVENT -> {
                            val prog = PlannerLogic.computeProgress(item, allSteps, allItems, nowMillis)
                            isEventAccomplished(item, prog, nowMillis)
                        }
                        ItemType.TASK -> item.completed
                    }
                }
                CategoryTaskSummary(
                    category = cat,
                    completedCount = doneCount,
                    totalCount = inCat.size
                )
            }
        }.sortedByDescending { it.totalCount }

        val totalBlooms = weeklyBuckets.sum()

        // Overall completion rate across trackable units in this month
        val trackableTotal = monthSteps.size + tasks.size +
            plans.count { PlannerLogic.computeProgress(it, allSteps, allItems, nowMillis).totalCount == 0 } +
            events.count { PlannerLogic.computeProgress(it, allSteps, allItems, nowMillis).totalCount == 0 }

        val completionPercent = if (trackableTotal == 0) {
            0
        } else {
            ((totalBlooms.toDouble() / trackableTotal.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        }

        // Highlights: completed plans first, then events, then standalone tasks
        val highlights = buildList {
            completedPlans.forEach { plan ->
                val prog = PlannerLogic.computeProgress(plan, allSteps, allItems, nowMillis)
                add(
                    AccomplishmentHighlight(
                        itemId = plan.id,
                        title = plan.title,
                        type = ItemType.PLAN,
                        dateLabel = PlannerDateUtils.formatCompactDate(dateForItem(plan)),
                        detailLabel = if (prog.totalCount > 0) {
                            "${prog.completedCount} of ${prog.totalCount} milestones bloomed"
                        } else {
                            "Plan completed"
                        }
                    )
                )
            }
            completedEvents.forEach { ev ->
                val prog = PlannerLogic.computeProgress(ev, allSteps, allItems, nowMillis)
                add(
                    AccomplishmentHighlight(
                        itemId = ev.id,
                        title = ev.title,
                        type = ItemType.EVENT,
                        dateLabel = PlannerDateUtils.formatCompactDate(dateForItem(ev)),
                        detailLabel = if (prog.totalCount > 0) {
                            "${prog.completedCount}/${prog.totalCount} prep steps ready"
                        } else {
                            "Event enjoyed"
                        }
                    )
                )
            }
            completedTasks.filter { it.parentPlanId.isNullOrBlank() }.forEach { task ->
                add(
                    AccomplishmentHighlight(
                        itemId = task.id,
                        title = task.title,
                        type = ItemType.TASK,
                        dateLabel = PlannerDateUtils.formatCompactDate(dateForItem(task)),
                        detailLabel = "${task.taskCategory().label} · Task finished"
                    )
                )
            }
        }

        val reflection = when {
            totalBlooms == 0 && monthItems.isEmpty() ->
                "A quiet month waiting for its first forget-me-not."
            totalBlooms == 0 ->
                "Seeds are planted for $fullMonth. Every finished step will bloom here."
            completionPercent >= 90 ->
                "What a radiant $fullMonth — $totalBlooms forget-me-nots bloomed with $completionPercent% completion."
            totalBlooms >= 10 ->
                "A wonderfully full $fullMonth! You bloomed $totalBlooms accomplishments at your own gentle pace."
            else ->
                "In $fullMonth, you brought $totalBlooms ${if (totalBlooms == 1) "moment" else "moments"} into bloom."
        }

        return MonthlyAccomplishmentReport(
            year = year,
            month = month,
            monthName = fullMonth,
            shortMonthName = shortMonth,
            totalBlooms = totalBlooms,
            plansCompleted = completedPlans.size,
            plansTotal = plans.size,
            milestonesCompleted = completedSteps.size,
            milestonesTotal = monthSteps.size,
            tasksCompleted = completedTasks.size,
            tasksTotal = tasks.size,
            eventsCompleted = completedEvents.size,
            eventsTotal = events.size,
            completionPercent = completionPercent,
            weeklyBlooms = weeklyBuckets,
            weeklyCompletedTasks = weeklyCompletedTaskBuckets,
            weeklyMilestoneMinutes = weeklyMilestoneMinuteBuckets,
            totalMilestoneMinutes = totalMilestoneMinutes,
            categoryBreakdown = categoryBreakdown,
            topTimedMilestones = topTimedMilestones,
            highlights = highlights,
            reflectionMessage = reflection
        )
    }

    fun buildYearEndReport(
        year: Int,
        allItems: List<PlannerItem>,
        allSteps: List<PlannerStep>,
        nowMillis: Long
    ): YearEndAccomplishmentReport {
        val months = (1..12).map { m ->
            buildMonthlyReport(year, m, allItems, allSteps, nowMillis)
        }

        val totalBlooms = months.sumOf { it.totalBlooms }
        val plansCompleted = months.sumOf { it.plansCompleted }
        val plansTotal = months.sumOf { it.plansTotal }
        val milestonesCompleted = months.sumOf { it.milestonesCompleted }
        val milestonesTotal = months.sumOf { it.milestonesTotal }
        val tasksCompleted = months.sumOf { it.tasksCompleted }
        val tasksTotal = months.sumOf { it.tasksTotal }
        val eventsCompleted = months.sumOf { it.eventsCompleted }
        val eventsTotal = months.sumOf { it.eventsTotal }
        val totalMilestoneMinutes = months.sumOf { it.totalMilestoneMinutes }

        val activeMonthsCount = months.count { it.totalBlooms > 0 }

        // Longest consecutive streak of months with >= 1 bloom
        var bestStreak = 0
        var currentStreak = 0
        months.forEach { report ->
            if (report.totalBlooms > 0) {
                currentStreak += 1
                if (currentStreak > bestStreak) bestStreak = currentStreak
            } else {
                currentStreak = 0
            }
        }

        val peakMonth = months.filter { it.totalBlooms > 0 }.maxByOrNull { it.totalBlooms }

        val totalPossible = months.sumOf { m ->
            val pctDenom = if (m.completionPercent > 0) {
                ((m.totalBlooms * 100.0) / m.completionPercent).roundToInt()
            } else {
                m.milestonesTotal + m.tasksTotal + m.plansTotal + m.eventsTotal
            }
            pctDenom.coerceAtLeast(m.totalBlooms)
        }
        val overallPercent = if (totalPossible == 0) 0
        else ((totalBlooms.toDouble() / totalPossible.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        // Curate grand highlights across all 12 months (plans & events first)
        val grandHighlights = months
            .flatMap { m -> m.highlights }
            .sortedBy {
                when (it.type) {
                    ItemType.PLAN -> 0
                    ItemType.EVENT -> 1
                    ItemType.TASK -> 2
                }
            }
            .take(12)

        val grandReflection = when {
            totalBlooms == 0 ->
                "Your $year garden is ready and waiting. As each plan, milestone, task, and event finishes, your year-end keepsake grows here."
            activeMonthsCount >= 6 ->
                "Across $activeMonthsCount blooming months in $year, you brought $totalBlooms forget-me-nots to life — ${peakMonth?.monthName ?: "this year"} shone brightest."
            else ->
                "In $year, you bloomed $totalBlooms ${if (totalBlooms == 1) "accomplishment" else "accomplishments"} across $activeMonthsCount ${if (activeMonthsCount == 1) "month" else "months"}. Every thoughtful step counts."
        }

        return YearEndAccomplishmentReport(
            year = year,
            monthlyReports = months,
            totalBlooms = totalBlooms,
            plansCompleted = plansCompleted,
            plansTotal = plansTotal,
            milestonesCompleted = milestonesCompleted,
            milestonesTotal = milestonesTotal,
            tasksCompleted = tasksCompleted,
            tasksTotal = tasksTotal,
            eventsCompleted = eventsCompleted,
            eventsTotal = eventsTotal,
            totalMilestoneMinutes = totalMilestoneMinutes,
            overallCompletionPercent = overallPercent,
            activeMonthsCount = activeMonthsCount,
            longestActiveMonthStreak = bestStreak,
            peakMonth = peakMonth,
            grandHighlights = grandHighlights,
            grandReflection = grandReflection
        )
    }

    fun formatMonthlyShareText(report: MonthlyAccomplishmentReport): String = buildString {
        appendLine("Bunny Memo · ${report.monthName} ${report.year} Accomplishment Report")
        appendLine("--------------------------------------------------")
        appendLine("Blooms earned: ${report.totalBlooms} (${report.completionPercent}% completion)")
        appendLine("Plans finished: ${report.plansCompleted}/${report.plansTotal}")
        appendLine("Milestones & prep steps bloomed: ${report.milestonesCompleted}/${report.milestonesTotal} (${report.totalMilestoneMinutes}m invested)")
        appendLine("Tasks completed: ${report.tasksCompleted}/${report.tasksTotal}")
        appendLine("Events enjoyed: ${report.eventsCompleted}/${report.eventsTotal}")
        if (report.highlights.isNotEmpty()) {
            appendLine()
            appendLine("Highlights:")
            report.highlights.take(8).forEach { h ->
                appendLine("• ${h.title} (${h.dateLabel}) — ${h.detailLabel}")
            }
        }
        appendLine()
        appendLine(report.reflectionMessage)
    }

    fun formatYearEndShareText(report: YearEndAccomplishmentReport): String = buildString {
        appendLine("Bunny Memo · ${report.year} Grand Year-End Report")
        appendLine("==================================================")
        appendLine("Total forget-me-nots bloomed: ${report.totalBlooms}")
        appendLine("Active blooming months: ${report.activeMonthsCount} of 12 (longest streak: ${report.longestActiveMonthStreak})")
        report.peakMonth?.let { peak ->
            appendLine("Brightest month: ${peak.monthName} (${peak.totalBlooms} blooms)")
        }
        appendLine("Plans finished: ${report.plansCompleted}/${report.plansTotal}")
        appendLine("Milestones & prep steps bloomed: ${report.milestonesCompleted}/${report.milestonesTotal} (${report.totalMilestoneMinutes}m invested)")
        appendLine("Tasks completed: ${report.tasksCompleted}/${report.tasksTotal}")
        appendLine("Events enjoyed: ${report.eventsCompleted}/${report.eventsTotal}")
        appendLine()
        appendLine("Month by Month:")
        report.monthlyReports.forEach { m ->
            appendLine("  ${m.shortMonthName}: ${m.totalBlooms} blooms (${m.plansCompleted} plans, ${m.tasksCompleted} tasks, ${m.eventsCompleted} events)")
        }
        if (report.grandHighlights.isNotEmpty()) {
            appendLine()
            appendLine("Cherished Highlights of ${report.year}:")
            report.grandHighlights.forEach { h ->
                appendLine("• ${h.title} (${h.dateLabel}) — ${h.detailLabel}")
            }
        }
        appendLine()
        appendLine(report.grandReflection)
    }
}
