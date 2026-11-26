package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.example.data.CalendarDate
import com.example.data.ItemType
import com.example.data.PlannerItem
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.example.data.UserProfile
import com.example.notifications.DailyDigestSummary
import com.example.notifications.DailyReminderConfig
import com.example.ui.components.MonthBunnyOverlay
import com.example.ui.components.PlannerSearchResultsView
import com.example.ui.screens.DedicationScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.theme.DarkForgetMeNotColors
import com.example.ui.theme.ForgetMeNotTheme
import com.example.viewmodel.PlannerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI regression coverage for the audited frontend issues: dark-mode legibility,
 * 360dp detail layout, bunny accessibility action, search filter recovery, dedication cancel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun readyState(items: List<PlannerItem>): PlannerUiState.Ready = PlannerUiState.Ready(
        profile = UserProfile(id = "u", hasSeenDedication = true),
        items = items,
        steps = emptyList(),
        reminders = emptyList(),
        classSchedules = emptyList(),
        nowMillis = CalendarDate(2026, 5, 10).toLocalEpochMillis(10, 0),
        inboxEntries = emptyList(),
        dailyReminderConfig = DailyReminderConfig(),
        dailyDigestSummary = DailyDigestSummary("Today", "Nothing due", 0, 0, null)
    )

    private val sampleTask = PlannerItem(
        id = "task_long",
        type = ItemType.TASK.name,
        title = "Submit the biology lab report",
        dueDate = "2026-05-10",
        priority = TaskPriority.HIGH.name,
        category = TaskCategory.STUDY.name
    )

    @Test
    fun darkTheme_textInheritsLegibleOnSurfaceColor() {
        var contentColor = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            ForgetMeNotTheme(darkTheme = true) {
                Surface {
                    onSurface = MaterialTheme.colorScheme.onSurface
                    contentColor = LocalContentColor.current
                    Text("Dark legibility", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        composeRule.onNodeWithText("Dark legibility").assertIsDisplayed()
        assertEquals(DarkForgetMeNotColors.midnightNavy, onSurface)
        assertEquals(onSurface, contentColor)
    }

    @Test
    fun itemDetail_on360dp_keepsHeroTitleWithinViewport() {
        val state = readyState(listOf(sampleTask))
        composeRule.setContent {
            ForgetMeNotTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    ItemDetailScreen(
                        item = sampleTask,
                        state = state,
                        isCelebrating = false,
                        showBackButton = true,
                        onBack = {},
                        onEditItem = {},
                        onToggleItemCompleted = { _, _ -> },
                        onDeleteItem = {},
                        onAddStep = { _, _, _ -> },
                        onToggleStep = { _, _ -> },
                        onDeleteStep = {},
                        onAddReminder = { _, _, _ -> },
                        onDeleteReminder = {},
                        onDismissCelebration = {}
                    )
                }
            }
        }
        val titleNode = composeRule.onNodeWithText(sampleTask.title, useUnmergedTree = true)
        titleNode.assertIsDisplayed()
        val bounds = titleNode.getBoundsInRoot()
        assertTrue("Title overflows 360dp: right=${bounds.right}", bounds.right <= 360.dp)
        assertTrue("Title starts off-screen: left=${bounds.left}", bounds.left >= 0.dp)
    }

    @Test
    fun monthBunny_exposesAccessibleClickAction() {
        val state = readyState(emptyList())
        composeRule.setContent {
            ForgetMeNotTheme(darkTheme = false) {
                MonthBunnyOverlay(state = state, onOpenItem = {})
            }
        }
        composeRule.onNodeWithTag("month_bunny_avatar")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun searchResults_resetFiltersRecoversHiddenMatches() {
        val state = readyState(listOf(sampleTask))
        composeRule.setContent {
            ForgetMeNotTheme(darkTheme = false) {
                PlannerSearchResultsView(
                    query = "biology",
                    state = state,
                    selectedItemId = null,
                    onSelectItem = {},
                    onToggleTask = { _, _ -> },
                    onEditClass = {},
                    onClearSearch = {},
                    onSaveQueryAsQuickNote = {}
                )
            }
        }
        composeRule.onNodeWithTag("search_result_task_long").assertIsDisplayed()

        composeRule.onNodeWithTag("search_filter_events").performClick()
        composeRule.onNodeWithTag("search_result_task_long").assertDoesNotExist()

        composeRule.onNodeWithTag("planner_search_results_list")
            .performScrollToNode(hasTestTag("search_reset_filters_button"))
        composeRule.onNodeWithTag("search_reset_filters_button").performClick()

        composeRule.onNodeWithTag("planner_search_results_list")
            .performScrollToNode(hasTestTag("search_result_task_long"))
        composeRule.onNodeWithTag("search_result_task_long").assertIsDisplayed()
    }

    @Test
    fun dedication_cancelInvokesCallbackWithoutSaving() {
        var cancelled = false
        var saved = false
        composeRule.setContent {
            ForgetMeNotTheme(darkTheme = false) {
                DedicationScreen(
                    initialDedication = "For you",
                    isSaving = false,
                    onOpenPlanner = { saved = true },
                    onCancel = { cancelled = true }
                )
            }
        }
        composeRule.onNodeWithTag("dedication_cancel_button").performClick()
        composeRule.runOnIdle {
            assertTrue(cancelled)
            assertTrue(!saved)
        }
    }

    @Test
    fun dedication_firstRun_hasNoCancelButton() {
        composeRule.setContent {
            ForgetMeNotTheme(darkTheme = false) {
                DedicationScreen(initialDedication = "For you", isSaving = false, onOpenPlanner = {})
            }
        }
        composeRule.onNodeWithTag("dedication_cancel_button").assertDoesNotExist()
    }
}
