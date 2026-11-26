package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.audio.BunnyFeedbackManager
import com.example.audio.BunnyVoiceMood
import com.example.data.LocalPlannerRepository
import com.example.data.RoomFirestoreSyncService
import com.example.ui.components.BunnyBreed
import com.example.ui.components.BunnyCompanionExpression
import com.example.ui.components.BunnyCompanionStateLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Non-exported internal BroadcastReceiver handling interactive widget actions
 * (task completion toggle and interactive bunny pat/pinch/talk) so third-party apps
 * cannot spoof custom widget intents.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext

        when (action) {
            WidgetIntents.ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(WidgetIntents.EXTRA_TASK_ID) ?: return
                val newCompleted = intent.getBooleanExtra(WidgetIntents.EXTRA_TASK_COMPLETED, true)
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val dataSource = RoomFirestoreSyncService
                            .getInstance(appContext)
                            .createOfflineFirstDataSource()
                        dataSource.toggleItemCompleted(
                            userId = LocalPlannerRepository.LOCAL_USER_ID,
                            itemId = taskId,
                            completed = newCompleted
                        )
                        WidgetUpdateManager.refreshAllWidgetsSuspend(appContext)
                    } catch (err: Exception) {
                        Log.w(TAG, "Failed toggling task $taskId from widget: ${err.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PAT,
            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_LEFT,
            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_RIGHT,
            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_BOTH,
            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_TALK -> {
                handleBunnyInteraction(appContext, action)
            }
        }
    }

    private fun handleBunnyInteraction(appContext: Context, action: String) {
        val feedback = BunnyFeedbackManager.getInstance(appContext)
        val prefs = appContext.getSharedPreferences(
            InteractiveBunnyAppWidgetProvider.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val count = prefs.getInt(InteractiveBunnyAppWidgetProvider.KEY_INTERACTION_COUNT, 0) + 1

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

                val reactionExpr: BunnyCompanionExpression
                var pinchSide = 0
                val dialogue: String

                when (action) {
                    InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PAT -> {
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

                    InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_LEFT,
                    InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_RIGHT,
                    InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_BOTH -> {
                        reactionExpr = BunnyCompanionExpression.CHEEK_PINCHED
                        pinchSide = when (action) {
                            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_LEFT -> -1
                            InteractiveBunnyAppWidgetProvider.ACTION_BUNNY_PINCH_RIGHT -> 1
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
                    .putString(InteractiveBunnyAppWidgetProvider.KEY_LAST_EXPRESSION, reactionExpr.name)
                    .putInt(InteractiveBunnyAppWidgetProvider.KEY_LAST_PINCH_SIDE, pinchSide)
                    .putString(InteractiveBunnyAppWidgetProvider.KEY_LAST_DIALOGUE, dialogue)
                    .putLong(InteractiveBunnyAppWidgetProvider.KEY_LAST_INTERACTION_MS, System.currentTimeMillis())
                    .putInt(InteractiveBunnyAppWidgetProvider.KEY_INTERACTION_COUNT, count)
                    .apply()

                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, InteractiveBunnyAppWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    InteractiveBunnyAppWidgetProvider.updateWidgets(appContext, manager, ids, snapshot)
                }
            } catch (err: Exception) {
                Log.w(TAG, "Failed handling interactive bunny widget action $action: ${err.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"
    }
}
