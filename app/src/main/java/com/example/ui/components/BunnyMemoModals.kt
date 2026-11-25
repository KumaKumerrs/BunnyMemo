package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CalendarDate
import com.example.data.PlannerDateUtils
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ForgetMeNotBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PollenSoft
import com.example.ui.theme.PollenYellow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom animated forget-me-not stationery modal container.
 * Replaces generic Android AlertDialog / Dialog popups with a softly dimmed Midnight Navy
 * backdrop, smooth spring slide+scale entrance/exit, subtle botanical corner bloom, and
 * a Cloud White stationery sheet aesthetic.
 */
@Composable
fun BunnyMemoModalContainer(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    maxWidth: Dp = 460.dp,
    showCloseButton: Boolean = true,
    headerLeading: (@Composable () -> Unit)? = { ForgetMeNotFlower(bloomed = true, size = 24.dp) },
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val requestDismiss: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            visible = false
            scope.launch {
                if (!reducedMotion) delay(190L)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            val resolvedMaxWidth = if (this@BoxWithConstraints.maxWidth >= 840.dp) {
                maxWidth.coerceAtLeast(520.dp)
            } else {
                maxWidth
            }
            val resolvedMaxHeight = (this@BoxWithConstraints.maxHeight * 0.90f).coerceAtLeast(300.dp)

            // Smoothly animated soft mist backdrop
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(190, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.example.ui.theme.MutedNavy.copy(alpha = 0.24f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = requestDismiss
                        )
                )
            }

            // Crisp spring-animated forget-me-not stationery card
            AnimatedVisibility(
                visible = visible,
                enter = if (reducedMotion) {
                    fadeIn(tween(130))
                } else {
                    fadeIn(tween(190)) +
                        scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) +
                        slideInVertically(
                            initialOffsetY = { it / 16 },
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(110))
                } else {
                    fadeOut(tween(150)) +
                        scaleOut(targetScale = 0.96f, animationSpec = tween(150)) +
                        slideOutVertically(targetOffsetY = { it / 18 }, animationSpec = tween(150))
                }
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .fillMaxWidth()
                        .widthIn(max = resolvedMaxWidth)
                        .heightIn(max = resolvedMaxHeight),
                    shape = RoundedCornerShape(28.dp),
                    color = PureWhite,
                    shadowElevation = 18.dp,
                    border = BorderStroke(0.75.dp, SubtleBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithCache {
                                val topGlow = Brush.verticalGradient(
                                    colors = listOf(CloudWhite, Color.Transparent),
                                    startY = 0f,
                                    endY = 84.dp.toPx()
                                )
                                val center = Offset(size.width - 24.dp.toPx(), 26.dp.toPx())
                                val radius = 22.dp.toPx()
                                val petalDist = radius * 0.48f
                                val petalRadius = radius * 0.36f
                                val petalOffsets = Array(5) { i ->
                                    val rad = ((-90.0 + i * 72.0) * PI / 180.0)
                                    Offset(
                                        center.x + (petalDist * cos(rad)).toFloat(),
                                        center.y + (petalDist * sin(rad)).toFloat()
                                    )
                                }
                                onDrawBehind {
                                    drawRect(brush = topGlow)
                                    for (pt in petalOffsets) {
                                        drawCircle(
                                            color = ForgetMeNotBlue.copy(alpha = 0.11f),
                                            radius = petalRadius,
                                            center = pt
                                        )
                                    }
                                    drawCircle(
                                        color = PollenYellow.copy(alpha = 0.18f),
                                        radius = radius * 0.19f,
                                        center = center
                                    )
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 20.dp)
                    ) {
                        // Subtle neutral pill handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 14.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(SubtleBorder)
                        )

                        // Modal Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (headerLeading != null) {
                                    headerLeading()
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MidnightNavy
                                    )
                                    if (!subtitle.isNullOrBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (showCloseButton) {
                                IconButton(
                                    onClick = requestDismiss,
                                    modifier = Modifier.testTag("modal_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MidnightNavy
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        content(requestDismiss)
                    }
                }
            }
        }
    }
}

/**
 * Custom forget-me-not themed confirmation modal (replaces standard Android AlertDialog).
 */
@Composable
fun BunnyMemoConfirmModal(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String = "Keep it",
    isDestructive: Boolean = false,
    confirmTestTag: String = "modal_confirm_button",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BunnyMemoModalContainer(
        title = title,
        onDismiss = onDismiss,
        maxWidth = 400.dp,
        headerLeading = {
            SmallBunnyIllustration(
                pose = if (isDestructive) BunnyPose.RESTING else BunnyPose.GREETING,
                size = 42.dp,
                contentDesc = null
            )
        }
    ) { requestDismiss ->
        Surface(
            color = PureWhite,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MidnightNavy,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = requestDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(cancelLabel)
            }
            Button(
                onClick = {
                    onConfirm()
                    requestDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) OverdueTerracotta else DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag(confirmTestTag)
            ) {
                Text(confirmLabel)
            }
        }
    }
}

/**
 * Custom Forget-Me-Not Stationery Date Picker Modal (replaces standard Material DatePickerDialog).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BunnyMemoDatePickerModal(
    title: String = "Choose a date",
    initialDate: CalendarDate,
    onDateSelected: (CalendarDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { CalendarDate.today() }
    var pickedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var visibleMonth by remember(initialDate) {
        mutableStateOf(CalendarDate(initialDate.year, initialDate.month, 1))
    }
    var monthDirection by remember { mutableIntStateOf(1) }
    val reducedMotion = rememberReducedMotion()

    BunnyMemoModalContainer(
        title = title,
        subtitle = PlannerDateUtils.formatFullDateHeader(pickedDate),
        onDismiss = onDismiss,
        maxWidth = 420.dp
    ) { requestDismiss ->
        // Quick one-tap date chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val quickDates = listOf(
                "Today" to today,
                "Tomorrow" to today.plusDays(1),
                "+3 days" to today.plusDays(3),
                "Next week" to today.plusDays(7)
            )
            quickDates.forEach { (label, date) ->
                FilterChip(
                    selected = pickedDate == date,
                    onClick = {
                        pickedDate = date
                        visibleMonth = CalendarDate(date.year, date.month, 1)
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SkyBlue,
                        selectedLabelColor = MidnightNavy
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mini stationery calendar card
        Surface(
            color = PureWhite,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            monthDirection = -1
                            visibleMonth = visibleMonth.plusMonths(-1)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous month",
                            tint = MidnightNavy
                        )
                    }
                    Text(
                        text = PlannerDateUtils.formatMonthYear(visibleMonth),
                        style = MaterialTheme.typography.titleMedium,
                        color = MidnightNavy
                    )
                    IconButton(
                        onClick = {
                            monthDirection = 1
                            visibleMonth = visibleMonth.plusMonths(1)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next month",
                            tint = MidnightNavy
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                        Text(
                            text = d,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = visibleMonth,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(120))
                        } else {
                            (slideInHorizontally(tween(240)) { w -> monthDirection * w / 5 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { w -> -monthDirection * w / 5 } + fadeOut(tween(180)))
                        }
                    },
                    label = "modal_calendar_month"
                ) { monthStart ->
                    val cells = remember(monthStart) { PlannerDateUtils.buildMonthGrid(monthStart) }
                    val weeks = remember(cells) { cells.chunked(7) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Spacer(modifier = Modifier.weight(1f).height(40.dp))
                                    } else {
                                        val isSelected = day == pickedDate
                                        val isToday = day == today
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> DeepBlue
                                                        isToday -> SkyBlue.copy(alpha = 0.6f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { pickedDate = day },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = day.day.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) PureWhite else MidnightNavy
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(PollenYellow)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                    onDateSelected(pickedDate)
                    requestDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("modal_date_confirm_button")
            ) {
                Text("Choose date")
            }
        }
    }
}

/**
 * Custom Forget-Me-Not Stationery Time Picker Modal (replaces standard Dialog + raw TimePicker).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BunnyMemoTimePickerModal(
    title: String = "Choose a time",
    subtitle: String = "Pick a calm moment for your reminder",
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour24 by remember(initialHour) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember(initialMinute) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }

    val isPm = hour24 >= 12
    val displayHour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }

    BunnyMemoModalContainer(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        maxWidth = 400.dp
    ) { requestDismiss ->
        // Tactile Forget-Me-Not time dial card
        Surface(
            color = PureWhite,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hour stepper
                TimeUnitStepper(
                    valueText = displayHour12.toString().padStart(2, '0'),
                    label = "Hour",
                    onIncrement = {
                        val next12 = if (displayHour12 == 12) 1 else displayHour12 + 1
                        hour24 = (if (isPm) 12 else 0) + (next12 % 12)
                    },
                    onDecrement = {
                        val prev12 = if (displayHour12 == 1) 12 else displayHour12 - 1
                        hour24 = (if (isPm) 12 else 0) + (prev12 % 12)
                    }
                )

                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineLarge,
                    color = DeepBlue,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Minute stepper (steps by 5 mins for quick friendly adjustment)
                TimeUnitStepper(
                    valueText = minute.toString().padStart(2, '0'),
                    label = "Min",
                    onIncrement = { minute = (minute + 5) % 60 },
                    onDecrement = { minute = (minute + 55) % 60 }
                )

                Spacer(modifier = Modifier.width(14.dp))

                // AM / PM pill stack
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { if (isPm) hour24 -= 12 },
                        color = if (!isPm) DeepBlue else SoftIceSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (!isPm) DeepBlue else SubtleBorder)
                    ) {
                        Text(
                            text = "AM",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (!isPm) PureWhite else MidnightNavy,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    Surface(
                        onClick = { if (!isPm) hour24 += 12 },
                        color = if (isPm) DeepBlue else SoftIceSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isPm) DeepBlue else SubtleBorder)
                    ) {
                        Text(
                            text = "PM",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isPm) PureWhite else MidnightNavy,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Gentle forget-me-not time presets
        Text(
            text = "Gentle presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val presets = listOf(
                Triple("8:00 AM · Morning", 8, 0),
                Triple("9:00 AM · Desk", 9, 0),
                Triple("12:30 PM · Midday", 12, 30),
                Triple("6:00 PM · Evening", 18, 0),
                Triple("8:30 PM · Wind-down", 20, 30)
            )
            presets.forEach { (label, pH, pM) ->
                FilterChip(
                    selected = hour24 == pH && minute == pM,
                    onClick = {
                        hour24 = pH
                        minute = pM
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PollenSoft,
                        selectedLabelColor = MidnightNavy
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

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
                    onTimeSelected(hour24, minute)
                    requestDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("modal_time_confirm_button")
            ) {
                Text("Set ${PlannerDateUtils.formatTimeOfDay(hour24 * 60 + minute)}")
            }
        }
    }
}

@Composable
private fun TimeUnitStepper(
    valueText: String,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onIncrement) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase $label",
                tint = DeepBlue
            )
        }
        Surface(
            color = SoftIceSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SkyBlue),
            modifier = Modifier.size(width = 70.dp, height = 54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MidnightNavy
                )
            }
        }
        IconButton(onClick = onDecrement) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease $label",
                tint = DeepBlue
            )
        }
    }
}

/**
 * Custom Forget-Me-Not modal allowing users in device-local mode to sign in with Google
 * whenever they are ready for private cloud backup & cross-device sync.
 */
@Composable
fun BunnyMemoCloudSyncModal(
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignInWithGoogle: () -> Unit,
    onDismiss: () -> Unit
) {
    BunnyMemoModalContainer(
        title = "Back up with Google",
        subtitle = "Keep your memos safe across devices",
        onDismiss = onDismiss,
        maxWidth = 420.dp,
        headerLeading = {
            SmallBunnyIllustration(
                pose = BunnyPose.GREETING,
                size = 44.dp,
                contentDesc = null
            )
        }
    ) { requestDismiss ->
        TonalInsetPanel(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Currently saved on this device",
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
                Text(
                    text = "Everything you add works right away locally. When you sign in with Google, we’ll copy all of your existing local plans, tasks, milestones, and dedication note into your private cloud planner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = com.example.ui.theme.OverdueContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = OverdueTerracotta,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = requestDismiss,
                enabled = !isSigningIn,
                border = BorderStroke(1.dp, SubtleBorder),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Maybe later", color = MidnightNavy)
            }
            Button(
                onClick = onSignInWithGoogle,
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlue,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("cloud_modal_sign_in_button")
            ) {
                Text(if (isSigningIn) "Signing in…" else "Sign in with Google")
            }
        }
    }
}
