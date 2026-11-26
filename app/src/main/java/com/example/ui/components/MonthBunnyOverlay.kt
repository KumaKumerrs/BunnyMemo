package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.audio.BunnyHaptic
import com.example.audio.BunnyVoiceMood
import com.example.audio.TaskAudioMemoManager
import com.example.data.DailyHabitRepository
import com.example.data.DailyHabitSnapshot
import com.example.ui.theme.CalmSage
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueContainer
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import com.example.viewmodel.PlannerUiState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthBunnyOverlay(
    state: PlannerUiState.Ready,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    val habitRepo = remember(context) { DailyHabitRepository.getInstance(context) }
    val audioMemoManager = remember(context) { TaskAudioMemoManager.getInstance(context) }
    val habitSnapshot by habitRepo.snapshot.collectAsStateWithLifecycle()
    val audioPlaybackState by audioMemoManager.playbackState.collectAsStateWithLifecycle()
    val audioBunnyEvent by audioMemoManager.bunnyEvent.collectAsStateWithLifecycle()
    val today = state.todayDate()
    val monthBreed = remember(today.month) { BunnyBreed.forMonth(today.month) }
    val summary = remember(state.items, state.steps, state.classSchedules, state.nowMillis) {
        BunnyCompanionStateLogic.analyzeTasks(
            items = state.items,
            steps = state.steps,
            classSchedules = state.classSchedules,
            nowMillis = state.nowMillis
        )
    }

    var bubbleExpanded by remember { mutableStateOf(true) }
    var topic by remember {
        mutableStateOf(
            if (summary.missedCount > 0) BunnyBubbleTopic.MISSED else BunnyBubbleTopic.GREETING
        )
    }
    var transientReaction by remember { mutableStateOf(BunnyTransientReaction.NONE) }
    var reactionNonce by remember { mutableIntStateOf(0) }
    var patCounter by remember { mutableIntStateOf(0) }
    var pinchCounter by remember { mutableIntStateOf(0) }
    var patPulse by remember { mutableFloatStateOf(0f) }
    var leftCheekPull by remember { mutableFloatStateOf(0f) }
    var rightCheekPull by remember { mutableFloatStateOf(0f) }
    var isTalking by remember { mutableStateOf(false) }
    var talkingNonce by remember { mutableIntStateOf(0) }
    var customDialogue by remember { mutableStateOf<String?>(null) }
    var customActionItemId by remember { mutableStateOf<String?>(null) }
    var showHowAreYouChips by remember { mutableStateOf(summary.missedCount == 0) }
    var lastVoiceTriggerMs by remember { mutableLongStateOf(0L) }

    // Previous task & habit snapshots tracked cleanly via BunnyCompanionStateLogic
    var previousSnapshot by remember { mutableStateOf<BunnyTaskStatusSnapshot?>(null) }
    var previousHabitSnapshot by remember { mutableStateOf<DailyHabitSnapshot?>(null) }
    var lastHandledAudioEventId by remember { mutableLongStateOf(0L) }

    fun triggerSpeakingVoice(mood: BunnyVoiceMood) {
        val now = System.currentTimeMillis()
        if (now - lastVoiceTriggerMs >= 260L) {
            lastVoiceTriggerMs = now
            feedback.playBunnyVoice(mood)
        }
        isTalking = true
        talkingNonce++
    }

    fun triggerPatReaction(fromRub: Boolean = false) {
        patCounter++
        transientReaction = BunnyTransientReaction.PATTED
        reactionNonce++
        bubbleExpanded = true
        showHowAreYouChips = false
        patPulse = 1f
        val patLines = listOf(
            "Hehe~ Thank you for the cozy head pats!",
            "Purr~ My ${monthBreed.breedName} ears love gentle pats!",
            "So warm and happy! You've got this today!",
            "Yay~ Head pats recharge all my bunny energy!"
        )
        customDialogue = patLines[patCounter % patLines.size]
        customActionItemId = null
        val now = System.currentTimeMillis()
        if (!fromRub || now - lastVoiceTriggerMs >= 700L) {
            lastVoiceTriggerMs = now
            feedback.onBunnyPat()
            isTalking = true
            talkingNonce++
        } else {
            feedback.triggerHaptic(BunnyHaptic.BUNNY_PAT)
        }
    }

    fun triggerPinchReaction(side: Int = 0, livePullAmount: Float = 0.95f) {
        pinchCounter++
        transientReaction = BunnyTransientReaction.CHEEK_PINCHED
        reactionNonce++
        bubbleExpanded = true
        showHowAreYouChips = false
        when {
            side < 0 -> {
                leftCheekPull = livePullAmount.coerceIn(0.6f, 1.35f)
                rightCheekPull = 0.15f
            }
            side > 0 -> {
                rightCheekPull = livePullAmount.coerceIn(0.6f, 1.35f)
                leftCheekPull = 0.15f
            }
            else -> {
                leftCheekPull = livePullAmount.coerceIn(0.6f, 1.3f)
                rightCheekPull = livePullAmount.coerceIn(0.6f, 1.3f)
            }
        }
        val pinchLines = listOf(
            "Mwee~! My chubby cheeks are super squishy!",
            "Pwee~! Hey, gentle with my ${monthBreed.breedName} cheeks!",
            "Squish squish~ Hehe, that tickles!",
            "Waaah~ Did you just pinch both my cheeks?!"
        )
        customDialogue = pinchLines[pinchCounter % pinchLines.size]
        customActionItemId = null
        val now = System.currentTimeMillis()
        if (now - lastVoiceTriggerMs >= 520L) {
            lastVoiceTriggerMs = now
            feedback.onBunnyCheekPinch()
            isTalking = true
            talkingNonce++
        }
    }

    fun cycleChatOrReminder() {
        bubbleExpanded = true
        transientReaction = BunnyTransientReaction.NONE
        customDialogue = null
        customActionItemId = null
        topic = when (topic) {
            BunnyBubbleTopic.GREETING -> if (summary.missedCount > 0) BunnyBubbleTopic.MISSED else BunnyBubbleTopic.REMINDER
            BunnyBubbleTopic.REMINDER -> if (summary.missedCount > 0) BunnyBubbleTopic.MISSED else BunnyBubbleTopic.GREETING
            BunnyBubbleTopic.MISSED -> BunnyBubbleTopic.REMINDER
        }
        showHowAreYouChips = topic == BunnyBubbleTopic.GREETING
        val voiceMood = when (topic) {
            BunnyBubbleTopic.GREETING -> BunnyVoiceMood.GREETING
            BunnyBubbleTopic.REMINDER -> if (summary.missedCount > 0 && summary.nextTaskTitle == null) BunnyVoiceMood.SAD_MISSED else BunnyVoiceMood.REMINDER
            BunnyBubbleTopic.MISSED -> BunnyVoiceMood.SAD_MISSED
        }
        triggerSpeakingVoice(voiceMood)
    }

    // Initial welcome voice & expression on workspace enter
    LaunchedEffect(Unit) {
        delay(650L)
        if (summary.missedCount > 0) {
            topic = BunnyBubbleTopic.MISSED
            showHowAreYouChips = false
            triggerSpeakingVoice(BunnyVoiceMood.SAD_MISSED)
        } else {
            topic = BunnyBubbleTopic.GREETING
            showHowAreYouChips = true
            triggerSpeakingVoice(BunnyVoiceMood.GREETING)
        }
    }

    // Automatic logic-layer transitions: reacts both to task completion (HAPPY) AND newly missed deadlines (SAD)
    LaunchedEffect(summary) {
        val prev = previousSnapshot
        previousSnapshot = summary
        if (prev == null) return@LaunchedEffect

        val outcome = BunnyCompanionStateLogic.resolveAutomaticTransition(
            previous = prev,
            current = summary,
            currentTopic = topic,
            currentReaction = transientReaction
        )

        when (outcome.triggeredEvent) {
            BunnyAutoEvent.TASK_COMPLETED_HAPPY -> {
                transientReaction = outcome.nextTransientReaction
                reactionNonce++
                topic = outcome.nextTopic
                bubbleExpanded = true
                showHowAreYouChips = outcome.showMoodReplyChips
                customDialogue = outcome.dialogueOverride
                customActionItemId = outcome.actionItemId
                delay(180L)
                outcome.soundCue?.let { triggerSpeakingVoice(it) }
            }
            BunnyAutoEvent.DEADLINE_MISSED_SAD -> {
                transientReaction = outcome.nextTransientReaction
                topic = outcome.nextTopic
                bubbleExpanded = true
                showHowAreYouChips = outcome.showMoodReplyChips
                customDialogue = outcome.dialogueOverride
                customActionItemId = outcome.actionItemId
                outcome.soundCue?.let { triggerSpeakingVoice(it) }
            }
            BunnyAutoEvent.NONE -> {
                topic = outcome.nextTopic
                showHowAreYouChips = outcome.showMoodReplyChips
            }
        }
    }

    // Daily habit happiness boost & special 8-bit sound rewards
    LaunchedEffect(habitSnapshot) {
        val prev = previousHabitSnapshot
        previousHabitSnapshot = habitSnapshot
        val reward = BunnyCompanionStateLogic.analyzeHabitsAndRewards(prev, habitSnapshot)
        if (reward.rewarded) {
            transientReaction = BunnyTransientReaction.HABIT_REWARD
            reactionNonce++
            bubbleExpanded = true
            showHowAreYouChips = false
            customDialogue = reward.dialogue
            customActionItemId = null
            feedback.onHabitRewardEarned(
                allDoneToday = reward.allDoneToday,
                isStreakMilestone = reward.isStreakMilestone
            )
            delay(320L)
            triggerSpeakingVoice(reward.soundMood)
        }
    }

    // Bunny listening cue while recording/playing a task voice memo
    val listeningTaskTitle = audioPlaybackState.isListeningForTaskTitle
    LaunchedEffect(listeningTaskTitle) {
        if (!listeningTaskTitle.isNullOrBlank()) {
            bubbleExpanded = true
            showHowAreYouChips = false
            if (audioPlaybackState.playingItemId != null) {
                // Keep playback crystal clear without talking over the memo audio
                isTalking = false
            }
        }
    }

    // Tailored bunny feedback once audio memo recording or playback completes
    LaunchedEffect(audioBunnyEvent?.eventId) {
        val ev = audioBunnyEvent ?: return@LaunchedEffect
        if (ev.eventId == lastHandledAudioEventId) return@LaunchedEffect
        lastHandledAudioEventId = ev.eventId
        val (msg, voiceMood) = BunnyCompanionStateLogic.buildAudioMemoFeedback(
            itemTitle = ev.itemTitle,
            durationSeconds = ev.durationSeconds,
            isTaskCompleted = ev.isTaskCompleted,
            isNewRecording = ev.isNewRecording
        )
        transientReaction = BunnyTransientReaction.BUNNY_AUDIO_FEEDBACK
        reactionNonce++
        bubbleExpanded = true
        showHowAreYouChips = false
        customDialogue = msg
        customActionItemId = ev.itemId
        delay(160L)
        triggerSpeakingVoice(voiceMood)
    }

    // Clear talking mouth animation shortly after 8-bit voice finishes
    LaunchedEffect(talkingNonce) {
        if (talkingNonce > 0) {
            delay(620L)
            isTalking = false
        }
    }

    // Smooth bounce-back and transient expression expiration
    LaunchedEffect(reactionNonce) {
        if (reactionNonce > 0) {
            val currentReaction = transientReaction
            if (currentReaction == BunnyTransientReaction.PATTED) {
                delay(240L)
                patPulse = 0f
            } else if (currentReaction == BunnyTransientReaction.CHEEK_PINCHED) {
                delay(550L)
                leftCheekPull = 0f
                rightCheekPull = 0f
            }
            val holdMs = when (currentReaction) {
                BunnyTransientReaction.JUST_COMPLETED -> 4800L
                BunnyTransientReaction.BUNNY_AUDIO_FEEDBACK,
                BunnyTransientReaction.HABIT_REWARD -> 5200L
                BunnyTransientReaction.CHECK_IN_REPLY -> 4200L
                BunnyTransientReaction.CHEEK_PINCHED -> 2200L
                else -> 2800L
            }
            delay(holdMs)
            leftCheekPull = 0f
            rightCheekPull = 0f
            if (transientReaction == currentReaction) {
                transientReaction = BunnyTransientReaction.NONE
                customDialogue = null
                customActionItemId = null
                if (summary.missedCount > 0) {
                    topic = BunnyBubbleTopic.MISSED
                    showHowAreYouChips = false
                }
            }
        }
    }

    val isListening = !listeningTaskTitle.isNullOrBlank()
    val activeExpression = BunnyCompanionStateLogic.resolveVisualExpression(
        snapshot = summary,
        transientReaction = transientReaction,
        isTalking = isTalking,
        isListening = isListening,
        habitSnapshot = habitSnapshot
    )

    val displayedMessage: String
    val actionTaskId: String?
    if (isListening) {
        val elapsedStr = if (audioPlaybackState.recordingItemId != null) {
            TaskAudioMemoManager.formatSeconds(audioPlaybackState.recordingElapsedSeconds)
        } else {
            "${TaskAudioMemoManager.formatSeconds(audioPlaybackState.playbackPositionSeconds)} / ${TaskAudioMemoManager.formatSeconds(audioPlaybackState.playbackDurationSeconds)}"
        }
        displayedMessage = if (audioPlaybackState.recordingItemId != null) {
            "Ears perked! I'm listening to your voice memo for \"${listeningTaskTitle}\" ($elapsedStr)…"
        } else {
            "Listening closely to \"${listeningTaskTitle}\" ($elapsedStr)…"
        }
        actionTaskId = audioPlaybackState.recordingItemId ?: audioPlaybackState.playingItemId
    } else if (!customDialogue.isNullOrBlank()) {
        displayedMessage = customDialogue!!
        actionTaskId = customActionItemId
    } else {
        when (topic) {
            BunnyBubbleTopic.MISSED -> {
                if (summary.missedCount > 0 && summary.firstMissedTitle != null) {
                    val whenStr = summary.firstMissedWhen?.let { " ($it)" }.orEmpty()
                    displayedMessage = "Aww… we missed \"${summary.firstMissedTitle}\"$whenStr. Finish it so I can smile again!"
                    actionTaskId = summary.firstMissedItemId
                } else {
                    displayedMessage = "All caught up! No missed tasks making me sad today."
                    actionTaskId = null
                }
            }
            BunnyBubbleTopic.REMINDER -> {
                when {
                    summary.nextTaskTitle != null -> {
                        val whenStr = summary.nextTaskWhen ?: "today"
                        displayedMessage = "Reminder: \"${summary.nextTaskTitle}\" is $whenStr!"
                        actionTaskId = summary.nextTaskItemId
                    }
                    summary.upcomingClassLabel != null -> {
                        displayedMessage = "Next class: ${summary.upcomingClassLabel}."
                        actionTaskId = null
                    }
                    habitSnapshot.completedTodayCount > 0 -> {
                        displayedMessage = "Happy bunny mood! ${habitSnapshot.completedTodayCount} daily habit(s) checked today!"
                        actionTaskId = null
                    }
                    else -> {
                        displayedMessage = "No urgent tasks right now! Want to plant a tiny goal or pet my head?"
                        actionTaskId = null
                    }
                }
            }
            BunnyBubbleTopic.GREETING -> {
                val baseGreet = BunnyCompanionStateLogic.greetingForTimeOfDay(state.nowMillis)
                displayedMessage = when {
                    summary.nextTaskTitle != null ->
                        "$baseGreet Next up: \"${summary.nextTaskTitle}\" (${summary.nextTaskWhen ?: "today"})."
                    summary.upcomingClassLabel != null ->
                        "$baseGreet Class coming up: ${summary.upcomingClassLabel}."
                    habitSnapshot.completedTodayCount > 0 ->
                        "$baseGreet Loving your ${habitSnapshot.overallStreakDays}d habit energy!"
                    else -> baseGreet
                }
                actionTaskId = summary.nextTaskItemId
            }
        }
    }

    val bubbleBorderColor = when (activeExpression) {
        BunnyCompanionExpression.SAD -> OverdueTerracotta.copy(alpha = 0.55f)
        BunnyCompanionExpression.LISTENING -> DeepBlue.copy(alpha = 0.65f)
        BunnyCompanionExpression.HAPPY, BunnyCompanionExpression.PATTED -> CalmSage.copy(alpha = 0.6f)
        BunnyCompanionExpression.CHEEK_PINCHED -> Color(0xFFFF7AA2).copy(alpha = 0.65f)
        else -> SubtleBorder
    }

    val allHabitsDoneToday = habitSnapshot.totalHabitsCount > 0 &&
        habitSnapshot.completedTodayCount >= habitSnapshot.totalHabitsCount
    val bunnyHappinessPct = remember(habitSnapshot, summary.missedCount) {
        BunnyCompanionStateLogic.computeHabitHappinessPercent(
            habitSnapshot = habitSnapshot,
            missedTasksCount = summary.missedCount
        )
    }

    val moodLabel = when (activeExpression) {
        BunnyCompanionExpression.LISTENING -> stringResource(R.string.month_bunny_listening_badge)
        BunnyCompanionExpression.SAD -> "Sad · Missed task"
        BunnyCompanionExpression.PATTED -> "Happy · Purring ($bunnyHappinessPct%)"
        BunnyCompanionExpression.CHEEK_PINCHED -> "Squishy cheeks!"
        BunnyCompanionExpression.HAPPY -> when {
            allHabitsDoneToday && summary.missedCount == 0 ->
                "Overjoyed 100% · ${habitSnapshot.overallStreakDays.coerceAtLeast(1)}d streak ★"
            habitSnapshot.completedTodayCount > 0 && summary.missedCount == 0 ->
                "Happy $bunnyHappinessPct% · ${habitSnapshot.overallStreakDays.coerceAtLeast(1)}d streak"
            else ->
                "Happy ($bunnyHappinessPct%)"
        }
        BunnyCompanionExpression.TALKING -> "Speaking…"
        BunnyCompanionExpression.NEUTRAL -> "${monthBreed.shortMonthName} · ${monthBreed.breedName}"
    }

    // Bottom-left overlay: larger bunny at BottomStart + speech bubble comfortably above bottom-right FAB
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 8.dp, bottom = 8.dp, end = 14.dp)
            .testTag("month_bunny_companion_root"),
        contentAlignment = Alignment.BottomStart
    ) {
        // Speech bubble tucked neatly to the right of the bunny's upper head/ears, clear of Create FAB
        AnimatedVisibility(
            visible = bubbleExpanded,
            modifier = Modifier.padding(start = 124.dp, bottom = 78.dp),
            enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                expandHorizontally(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
            exit = fadeOut(tween(150)) +
                shrinkHorizontally(tween(180), shrinkTowards = Alignment.Start)
        ) {
            Surface(
                color = PureWhite.copy(alpha = 0.96f),
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 18.dp,
                    bottomStart = 6.dp
                ),
                border = BorderStroke(1.25.dp, bubbleBorderColor),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 246.dp)
                    .testTag("month_bunny_speech_bubble")
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = when (activeExpression) {
                                    BunnyCompanionExpression.SAD -> OverdueTerracotta
                                    BunnyCompanionExpression.HAPPY, BunnyCompanionExpression.PATTED -> CalmSage
                                    else -> DeepBlue
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = moodLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when (activeExpression) {
                                    BunnyCompanionExpression.SAD -> OverdueTerracotta
                                    BunnyCompanionExpression.HAPPY, BunnyCompanionExpression.PATTED -> CalmSage
                                    else -> DeepBlue
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { bubbleExpanded = false },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("month_bunny_bubble_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_month_bunny_hide_bubble),
                                tint = MutedNavy,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = displayedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MidnightNavy,
                        modifier = Modifier.clickable { cycleChatOrReminder() }
                    )

                    if (showHowAreYouChips && transientReaction == BunnyTransientReaction.NONE) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val moodOptions = listOf(
                                "Great!" to "Yay! Let's ride that happy wave and bloom a forget-me-not!",
                                "Tired" to "Take it gently today~ One tiny step at a time, okay?",
                                "Focused" to "Ooh! Let's tackle your next task together!"
                            )
                            moodOptions.forEach { (chipLabel, replyText) ->
                                Surface(
                                    color = SoftIceSurface,
                                    shape = RoundedCornerShape(100.dp),
                                    border = BorderStroke(0.75.dp, SkyBlue),
                                    modifier = Modifier.clickable {
                                        transientReaction = BunnyTransientReaction.CHECK_IN_REPLY
                                        reactionNonce++
                                        showHowAreYouChips = false
                                        customDialogue = replyText
                                        customActionItemId = summary.nextTaskItemId ?: summary.firstMissedItemId
                                        triggerSpeakingVoice(
                                            if (chipLabel == "Tired") BunnyVoiceMood.GREETING else BunnyVoiceMood.HAPPY_PAT
                                        )
                                    }
                                ) {
                                    Text(
                                        text = chipLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = DeepBlue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (actionTaskId != null) {
                        Surface(
                            color = if (activeExpression == BunnyCompanionExpression.SAD) OverdueContainer else SkyBlue.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .testTag("month_bunny_open_task")
                                .clickable {
                                    feedback.onLightTick()
                                    onOpenItem(actionTaskId)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.month_bunny_view_task),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (activeExpression == BunnyCompanionExpression.SAD) OverdueTerracotta else DeepBlue
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = if (activeExpression == BunnyCompanionExpression.SAD) OverdueTerracotta else DeepBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.month_bunny_gesture_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Larger bottom-left Month Bunny (direct pat & cheek-pinch touch gestures on the rabbit itself)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.testTag("month_bunny_avatar_container")
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("month_bunny_avatar")
                    .semantics {
                        contentDescription =
                            "${monthBreed.monthName} bunny ${monthBreed.breedName}. $moodLabel. Pat head, pinch cheeks, or tap to talk."
                        role = Role.Button
                    }
                    .pointerInput(monthBreed, summary.missedCount) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val startPos = firstDown.position
                            val downTime = System.currentTimeMillis()
                            var totalMoveDist = 0f
                            var isTwoFingerPinch = false
                            var initialPinchDist = -1f
                            var rubAccumulator = 0f
                            var cheekTriggered = false

                            val inHeadZone = startPos.y < h * 0.44f
                            val inLeftCheekZone = startPos.y in (h * 0.42f)..(h * 0.78f) && startPos.x < w * 0.44f
                            val inRightCheekZone = startPos.y in (h * 0.42f)..(h * 0.78f) && startPos.x > w * 0.56f

                            if (inLeftCheekZone) {
                                leftCheekPull = 0.85f
                                cheekTriggered = true
                                triggerPinchReaction(side = -1, livePullAmount = 0.85f)
                            } else if (inRightCheekZone) {
                                rightCheekPull = 0.85f
                                cheekTriggered = true
                                triggerPinchReaction(side = 1, livePullAmount = 0.85f)
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.size >= 2) {
                                    val p1 = activePointers[0].position
                                    val p2 = activePointers[1].position
                                    val dist = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
                                    if (initialPinchDist < 0f) {
                                        initialPinchDist = dist
                                    }
                                    if (!isTwoFingerPinch || abs(dist - initialPinchDist) > 8f) {
                                        isTwoFingerPinch = true
                                        val delta = abs(initialPinchDist - dist) / (w * 0.42f)
                                        triggerPinchReaction(side = 0, livePullAmount = 0.9f + delta)
                                    }
                                    activePointers.forEach { it.consume() }
                                } else if (activePointers.size == 1) {
                                    val change = activePointers.first()
                                    val delta = change.position - change.previousPosition
                                    val stepDist = hypot(delta.x.toDouble(), delta.y.toDouble()).toFloat()
                                    totalMoveDist += stepDist

                                    if (inHeadZone && stepDist > 1.5f) {
                                        rubAccumulator += stepDist
                                        patPulse = 0.95f
                                        if (rubAccumulator >= 16f) {
                                            rubAccumulator = 0f
                                            triggerPatReaction(fromRub = true)
                                        }
                                        change.consume()
                                    } else if (inLeftCheekZone || inRightCheekZone) {
                                        val pull = (abs(change.position.x - startPos.x) / (w * 0.28f) + 0.78f)
                                            .coerceIn(0.65f, 1.35f)
                                        if (inLeftCheekZone) leftCheekPull = pull else rightCheekPull = pull
                                        if (!cheekTriggered) {
                                            cheekTriggered = true
                                            triggerPinchReaction(
                                                side = if (inLeftCheekZone) -1 else 1,
                                                livePullAmount = pull
                                            )
                                        }
                                        change.consume()
                                    }
                                } else {
                                    val elapsed = System.currentTimeMillis() - downTime
                                    patPulse = 0f
                                    if (!isTwoFingerPinch && !cheekTriggered && totalMoveDist < 18f) {
                                        if (inHeadZone) {
                                            triggerPatReaction(fromRub = false)
                                        } else if (elapsed > 350L) {
                                            triggerPinchReaction(side = 0, livePullAmount = 1.0f)
                                        } else {
                                            cycleChatOrReminder()
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.TopEnd
            ) {
                BunnyCompanionAvatar(
                    breed = monthBreed,
                    expression = activeExpression,
                    isTalking = isTalking,
                    patPulse = patPulse,
                    leftCheekPull = leftCheekPull,
                    rightCheekPull = rightCheekPull,
                    size = 136.dp
                )

                if (!bubbleExpanded && summary.missedCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 12.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(OverdueTerracotta),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                color = if (allHabitsDoneToday) CalmSage.copy(alpha = 0.16f) else SoftIceSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(
                    0.85.dp,
                    if (allHabitsDoneToday) CalmSage.copy(alpha = 0.65f) else SubtleBorder
                ),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .widthIn(max = 138.dp)
                    .testTag("month_bunny_streak_counter_pill")
                    .clickable {
                        bubbleExpanded = true
                        if (allHabitsDoneToday) {
                            transientReaction = BunnyTransientReaction.HABIT_REWARD
                            reactionNonce++
                            showHowAreYouChips = false
                            customDialogue =
                                "Woohoo!! All ${habitSnapshot.totalHabitsCount} daily habits completed today! (${habitSnapshot.overallStreakDays.coerceAtLeast(1)}d streak · 100% Happy!)"
                            feedback.onHabitRewardEarned(
                                allDoneToday = true,
                                isStreakMilestone = habitSnapshot.overallStreakDays >= 2
                            )
                            triggerSpeakingVoice(BunnyVoiceMood.HABIT_REWARD)
                        } else {
                            cycleChatOrReminder()
                        }
                    }
            ) {
                val badgeText = when {
                    allHabitsDoneToday ->
                        "★ ${habitSnapshot.overallStreakDays.coerceAtLeast(1)}d Streak · 100%"
                    habitSnapshot.totalHabitsCount > 0 ->
                        "${habitSnapshot.overallStreakDays}d Streak · ${habitSnapshot.completedTodayCount}/${habitSnapshot.totalHabitsCount}"
                    else ->
                        "${monthBreed.shortMonthName} · ${monthBreed.breedName}"
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allHabitsDoneToday) CalmSage else MidnightNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
        }
    }
}
