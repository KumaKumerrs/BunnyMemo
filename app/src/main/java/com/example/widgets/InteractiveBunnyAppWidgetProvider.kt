package com.example.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.audio.BunnyVoiceMood
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyCompanionExpression
import com.example.ui.components.BunnyCompanionStateLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Resizable Home Screen Interactive Bunny Family Widget:
 * - Renders the current month's breed as a resizable Big Bunny in the foreground
 * - Renders 3..5 widget-exclusive cute baby bunnies peeking and hopping behind him
 * - Supports direct touch interactions on the bunny's head (Pat), left/right cheeks (Pinch),
 *   and body/buttons (8-Bit Talk / Celebrate), playing 8-bit chiptune voice cues & updating expressions.
 */
class InteractiveBunnyAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_BUNNY_PAT &&
            action != ACTION_BUNNY_PINCH_LEFT &&
            action != ACTION_BUNNY_PINCH_RIGHT &&
            action != ACTION_BUNNY_PINCH_BOTH &&
            action != ACTION_BUNNY_TALK
        ) {
            return
        }

        val appContext = context.applicationContext
        val feedback = BunnyFeedbackManager.getInstance(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_INTERACTION_COUNT, 0) + 1

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataLoader.load(appContext)
                val breed = BunnyBreed.forMonth(snapshot.today.month)
                val habitSnap = snapshot.dailyHabitSnapshot
                val allHabitsDone = habitSnap.totalHabitsCount > 0 &&
                    habitSnap.completedTodayCount >= habitSnap.totalHabitsCount
                val taskSummary = BunnyCompanionStateLogic.analyzeTasks(
                    items = snapshot.allItems,
                    steps = snapshot.allSteps,
                    classSchedules = snapshot.allClasses,
                    nowMillis = snapshot.nowMillis
                )

                var reactionExpr = BunnyCompanionExpression.NEUTRAL
                var pinchSide = 0
                val dialogue: String

                when (action) {
                    ACTION_BUNNY_PAT -> {
                        reactionExpr = BunnyCompanionExpression.PATTED
                        feedback.onBunnyPat()
                        val patLines = listOf(
                            "Hehe~ Cozy head pats! The baby bunnies behind me are purring too!",
                            "Purr~ My ${breed.breedName} ears love gentle pats!",
                            "So warm and happy! Look at the little baby kits peeking behind me~",
                            "Yay~ Head pats recharge all our bunny family energy!"
                        )
                        dialogue = patLines[count % patLines.size]
                    }

                    ACTION_BUNNY_PINCH_LEFT,
                    ACTION_BUNNY_PINCH_RIGHT,
                    ACTION_BUNNY_PINCH_BOTH -> {
                        reactionExpr = BunnyCompanionExpression.CHEEK_PINCHED
                        pinchSide = when (action) {
                            ACTION_BUNNY_PINCH_LEFT -> -1
                            ACTION_BUNNY_PINCH_RIGHT -> 1
                            else -> 0
                        }
                        feedback.onBunnyCheekPinch()
                        val pinchLines = listOf(
                            "Mwee~! My chubby ${breed.breedName} cheeks are super squishy!",
                            "Pwee~! Hehe, the baby bunnies behind me are giggling at my squishy cheeks!",
                            "Squish squish~ That tickles my whiskers!",
                            "Waaah~ Did you just pinch my big fluffy cheeks?!"
                        )
                        dialogue = pinchLines[count % pinchLines.size]
                    }

                    else -> {
                        if (allHabitsDone) {
                            reactionExpr = BunnyCompanionExpression.HAPPY
                            feedback.onHabitRewardEarned(
                                allDoneToday = true,
                                isStreakMilestone = habitSnap.overallStreakDays >= 2
                            )
                            feedback.playBunnyVoice(BunnyVoiceMood.HABIT_REWARD)
                            dialogue =
                                "Woohoo!! All ${habitSnap.totalHabitsCount} daily habits done! (${habitSnap.overallStreakDays.coerceAtLeast(1)}d streak · 100% Happy!)"
                        } else if (taskSummary.missedCount > 0 && taskSummary.firstMissedTitle != null) {
                            reactionExpr = BunnyCompanionExpression.SAD
                            feedback.playBunnyVoice(BunnyVoiceMood.SAD_MISSED)
                            dialogue =
                                "Aww… we missed \"${taskSummary.firstMissedTitle}\". Finish it so our bunny family can smile again!"
                        } else if (taskSummary.nextTaskTitle != null) {
                            reactionExpr = BunnyCompanionExpression.HAPPY
                            feedback.playBunnyVoice(BunnyVoiceMood.REMINDER)
                            dialogue =
                                "Next up: \"${taskSummary.nextTaskTitle}\" (${taskSummary.nextTaskWhen ?: "today"})! You've got this!"
                        } else {
                            reactionExpr = BunnyCompanionExpression.HAPPY
                            feedback.playBunnyVoice(BunnyVoiceMood.GREETING)
                            dialogue =
                                "${BunnyCompanionStateLogic.greetingForTimeOfDay(snapshot.nowMillis)} Resize me for a giant bunny & more baby kits!"
                        }
                    }
                }

                prefs.edit()
                    .putString(KEY_LAST_EXPRESSION, reactionExpr.name)
                    .putInt(KEY_LAST_PINCH_SIDE, pinchSide)
                    .putString(KEY_LAST_DIALOGUE, dialogue)
                    .putLong(KEY_LAST_INTERACTION_MS, System.currentTimeMillis())
                    .putInt(KEY_INTERACTION_COUNT, count)
                    .apply()

                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, InteractiveBunnyAppWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    updateWidgets(appContext, manager, ids, snapshot)
                }

                // Bounce back from transient pat/pinch after a short moment
                if (reactionExpr == BunnyCompanionExpression.PATTED ||
                    reactionExpr == BunnyCompanionExpression.CHEEK_PINCHED
                ) {
                    delay(2400L)
                    val latestMs = prefs.getLong(KEY_LAST_INTERACTION_MS, 0L)
                    if (System.currentTimeMillis() - latestMs >= 2200L) {
                        prefs.edit()
                            .remove(KEY_LAST_EXPRESSION)
                            .putInt(KEY_LAST_PINCH_SIDE, 0)
                            .apply()
                        val refreshed = WidgetDataLoader.load(appContext)
                        val currentIds = manager.getAppWidgetIds(
                            ComponentName(appContext, InteractiveBunnyAppWidgetProvider::class.java)
                        )
                        if (currentIds.isNotEmpty()) {
                            updateWidgets(appContext, manager, currentIds, refreshed)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataLoader.load(context)
                updateWidgets(context, appWidgetManager, appWidgetIds, snapshot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataLoader.load(context)
                updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId), snapshot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BUNNY_PAT = "com.example.widgets.ACTION_BUNNY_PAT"
        const val ACTION_BUNNY_PINCH_LEFT = "com.example.widgets.ACTION_BUNNY_PINCH_LEFT"
        const val ACTION_BUNNY_PINCH_RIGHT = "com.example.widgets.ACTION_BUNNY_PINCH_RIGHT"
        const val ACTION_BUNNY_PINCH_BOTH = "com.example.widgets.ACTION_BUNNY_PINCH_BOTH"
        const val ACTION_BUNNY_TALK = "com.example.widgets.ACTION_BUNNY_TALK"

        private const val PREFS_NAME = "bunny_interactive_widget_prefs_v1"
        private const val KEY_LAST_EXPRESSION = "last_expression"
        private const val KEY_LAST_PINCH_SIDE = "last_pinch_side"
        private const val KEY_LAST_DIALOGUE = "last_dialogue"
        private const val KEY_LAST_INTERACTION_MS = "last_interaction_ms"
        private const val KEY_INTERACTION_COUNT = "interaction_count"

        private fun actionBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, InteractiveBunnyAppWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            val lastInteractMs = prefs.getLong(KEY_LAST_INTERACTION_MS, 0L)
            val isRecentInteraction = (nowMs - lastInteractMs) < 3500L

            val breed = BunnyBreed.forMonth(snapshot.today.month)
            val habitSnap = snapshot.dailyHabitSnapshot
            val allHabitsDoneToday = habitSnap.totalHabitsCount > 0 &&
                habitSnap.completedTodayCount >= habitSnap.totalHabitsCount
            val taskSummary = BunnyCompanionStateLogic.analyzeTasks(
                items = snapshot.allItems,
                steps = snapshot.allSteps,
                classSchedules = snapshot.allClasses,
                nowMillis = snapshot.nowMillis
            )
            val happinessPct = BunnyCompanionStateLogic.computeHabitHappinessPercent(
                habitSnapshot = habitSnap,
                missedTasksCount = taskSummary.missedCount
            )

            val savedExprRaw = if (isRecentInteraction) prefs.getString(KEY_LAST_EXPRESSION, null) else null
            val activeExpression = savedExprRaw?.let { raw ->
                runCatching { BunnyCompanionExpression.valueOf(raw) }.getOrNull()
            } ?: when {
                taskSummary.missedCount > 0 -> BunnyCompanionExpression.SAD
                allHabitsDoneToday || habitSnap.completedTodayCount > 0 -> BunnyCompanionExpression.HAPPY
                taskSummary.totalTodayRemaining == 0 && taskSummary.totalCompletedCount > 0 -> BunnyCompanionExpression.HAPPY
                else -> BunnyCompanionExpression.NEUTRAL
            }

            val pinchSide = if (activeExpression == BunnyCompanionExpression.CHEEK_PINCHED) {
                prefs.getInt(KEY_LAST_PINCH_SIDE, 0)
            } else {
                0
            }

            val savedDialogue = if ((nowMs - lastInteractMs) < 12_000L) {
                prefs.getString(KEY_LAST_DIALOGUE, null)
            } else {
                null
            }

            val defaultDialogue = when {
                allHabitsDoneToday ->
                    "Woohoo! All ${habitSnap.totalHabitsCount} daily habits complete! (${habitSnap.overallStreakDays.coerceAtLeast(1)}d streak · 100% Happy!)"
                taskSummary.missedCount > 0 && taskSummary.firstMissedTitle != null ->
                    "Aww… we missed \"${taskSummary.firstMissedTitle}\". Finish it so our bunny family can smile!"
                taskSummary.nextTaskTitle != null ->
                    "Reminder: \"${taskSummary.nextTaskTitle}\" (${taskSummary.nextTaskWhen ?: "today"}). Pat or pinch me!"
                habitSnap.completedTodayCount > 0 ->
                    "Happy $happinessPct%! ${habitSnap.completedTodayCount}/${habitSnap.totalHabitsCount} habits done · Baby kits are cheering!"
                else ->
                    "Hi! I'm your ${breed.monthName} ${breed.breedName} with my baby bunnies! Pat my head or pinch my cheeks~"
            }
            val displayDialogue = savedDialogue?.takeIf { it.isNotBlank() } ?: defaultDialogue

            val openTodayIntent = WidgetIntents.openMainActivityPendingIntent(
                context = context,
                requestCode = 1601,
                tab = WidgetIntents.TAB_TODAY
            )
            val patIntent = actionBroadcastPendingIntent(context, ACTION_BUNNY_PAT, 1602)
            val pinchLeftIntent = actionBroadcastPendingIntent(context, ACTION_BUNNY_PINCH_LEFT, 1603)
            val pinchRightIntent = actionBroadcastPendingIntent(context, ACTION_BUNNY_PINCH_RIGHT, 1604)
            val pinchBothIntent = actionBroadcastPendingIntent(context, ACTION_BUNNY_PINCH_BOTH, 1605)
            val talkIntent = actionBroadcastPendingIntent(context, ACTION_BUNNY_TALK, 1606)

            appWidgetIds.forEach { widgetId ->
                val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }.getOrNull()
                val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)?.coerceAtLeast(140) ?: 220
                val maxW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)?.coerceAtLeast(minW) ?: minW
                val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)?.coerceAtLeast(120) ?: 180
                val maxH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)?.coerceAtLeast(minH) ?: minH

                val avgW = (minW + maxW) / 2
                val avgH = (minH + maxH) / 2

                // Scale bitmap resolution to the widget's resized dimensions so the Big Bunny grows super crisp!
                val bitmapW = (avgW * 2.8f).toInt().coerceIn(420, 1100)
                val bitmapH = ((avgH - 64).coerceAtLeast(100) * 2.8f).toInt().coerceIn(280, 960)

                // More baby bunnies appear behind the big bunny when resized wider or as habit streak grows!
                val babyCount = when {
                    avgW >= 300 || avgH >= 280 || snapshot.habitStreakDays >= 7 -> 5
                    avgW >= 230 || avgH >= 220 || snapshot.habitStreakDays >= 2 -> 4
                    else -> 3
                }

                val familyBitmap = WidgetBitmapRenderer.renderInteractiveBunnyWidgetBitmap(
                    month = snapshot.today.month,
                    expression = activeExpression,
                    pinchSide = pinchSide,
                    babyBunnyCount = babyCount,
                    allHabitsDoneToday = allHabitsDoneToday,
                    widthPx = bitmapW,
                    heightPx = bitmapH
                )

                val badgeText = if (allHabitsDoneToday) {
                    "★ ${snapshot.habitStreakDays.coerceAtLeast(1)}d · 100% · $babyCount kits"
                } else {
                    "${snapshot.habitStreakDays}d · $happinessPct% · $babyCount kits"
                }

                val views = RemoteViews(context.packageName, R.layout.widget_interactive_bunny).apply {
                    setTextViewText(R.id.widget_bunny_title, "${breed.shortMonthName} ${breed.breedName} & Kits")
                    setTextViewText(R.id.widget_bunny_streak_badge, badgeText)
                    setImageViewBitmap(R.id.widget_bunny_canvas_image, familyBitmap)
                    setTextViewText(R.id.widget_bunny_dialogue_text, displayDialogue)

                    // Direct touch zones right on the big rabbit & baby bunnies
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_head, patIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_left_cheek, pinchLeftIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_right_cheek, pinchRightIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_talk, talkIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_bottom, talkIntent)

                    // Bottom action pills + header to open app
                    setOnClickPendingIntent(R.id.widget_bunny_btn_pat, patIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_btn_pinch, pinchBothIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_btn_talk, talkIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_dialogue_card, talkIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_header, openTodayIntent)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
