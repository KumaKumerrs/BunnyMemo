package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.example.R
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyCompanionExpression
import com.example.ui.components.BunnyCompanionStateLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Resizable Home Screen Interactive Bunny Family Widget:
 * - Renders the current month's breed as a resizable Big Bunny in the foreground
 * - Renders 3..5 widget-exclusive cute baby bunnies peeking and hopping behind him
 * - Routes direct touch interactions securely through non-exported [WidgetActionReceiver]
 * - Stays strictly within Android's RemoteViews Binder IPC bitmap budget (< 380 KB)
 */
class InteractiveBunnyAppWidgetProvider : AppWidgetProvider() {

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
            } catch (e: Exception) {
                Log.w(TAG, "Interactive bunny widget update failed: ${e.message}")
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
            } catch (e: Exception) {
                Log.w(TAG, "Interactive bunny widget resize update failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BunnyWidgetProvider"

        const val ACTION_BUNNY_PAT = "com.example.widgets.ACTION_BUNNY_PAT"
        const val ACTION_BUNNY_PINCH_LEFT = "com.example.widgets.ACTION_BUNNY_PINCH_LEFT"
        const val ACTION_BUNNY_PINCH_RIGHT = "com.example.widgets.ACTION_BUNNY_PINCH_RIGHT"
        const val ACTION_BUNNY_PINCH_BOTH = "com.example.widgets.ACTION_BUNNY_PINCH_BOTH"
        const val ACTION_BUNNY_TALK = "com.example.widgets.ACTION_BUNNY_TALK"

        internal const val PREFS_NAME = "bunny_interactive_widget_prefs_v1"
        internal const val KEY_LAST_EXPRESSION = "last_expression"
        internal const val KEY_LAST_PINCH_SIDE = "last_pinch_side"
        internal const val KEY_LAST_DIALOGUE = "last_dialogue"
        internal const val KEY_LAST_INTERACTION_MS = "last_interaction_ms"
        internal const val KEY_INTERACTION_COUNT = "interaction_count"

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetSnapshot
        ) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            val lastInteractMs = prefs.getLong(KEY_LAST_INTERACTION_MS, 0L)
            val isRecentInteraction = (nowMs - lastInteractMs) < 5_000L

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
            val patIntent = WidgetIntents.bunnyActionBroadcastPendingIntent(context, ACTION_BUNNY_PAT, 1602)
            val pinchLeftIntent = WidgetIntents.bunnyActionBroadcastPendingIntent(context, ACTION_BUNNY_PINCH_LEFT, 1603)
            val pinchRightIntent = WidgetIntents.bunnyActionBroadcastPendingIntent(context, ACTION_BUNNY_PINCH_RIGHT, 1604)
            val pinchBothIntent = WidgetIntents.bunnyActionBroadcastPendingIntent(context, ACTION_BUNNY_PINCH_BOTH, 1605)
            val talkIntent = WidgetIntents.bunnyActionBroadcastPendingIntent(context, ACTION_BUNNY_TALK, 1606)
            val isDark = WidgetBitmapRenderer.isDarkMode(context)

            appWidgetIds.forEach { widgetId ->
                val options = runCatching { appWidgetManager.getAppWidgetOptions(widgetId) }.getOrNull()
                val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)?.coerceAtLeast(140) ?: 220
                val maxW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)?.coerceAtLeast(minW) ?: minW
                val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)?.coerceAtLeast(120) ?: 180
                val maxH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)?.coerceAtLeast(minH) ?: minH

                val avgW = (minW + maxW) / 2
                val avgH = (minH + maxH) / 2

                // Bounded resolution strictly within RemoteViews Binder IPC budget (< 380 KB)
                val bitmapW = (avgW * 1.4f).toInt().coerceIn(260, 400)
                val bitmapH = ((avgH - 64).coerceAtLeast(100) * 1.4f).toInt().coerceIn(180, 260)

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
                    heightPx = bitmapH,
                    isDark = isDark
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

                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_head, patIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_left_cheek, pinchLeftIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_right_cheek, pinchRightIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_talk, talkIntent)
                    setOnClickPendingIntent(R.id.widget_bunny_hotspot_bottom, talkIntent)

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
