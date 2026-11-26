package com.example

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end cold-start launch verification for [MainActivity] in both fresh-install Auth mode
 * and returning-user Offline/Local Planner mode (including without Firebase google-services.json).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityLaunchTest {

    @Test
    fun mainActivity_freshInstall_launchesCleanlyToAuth() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bunny_memo_mode_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("local_mode_enabled", false).commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse("MainActivity should not finish on startup", activity.isFinishing)
            }
        }
    }

    @Test
    fun mainActivity_localMode_launchesCleanlyToPlannerShell() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bunny_memo_mode_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("local_mode_enabled", true).commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse("MainActivity should not finish in local planner mode", activity.isFinishing)
            }
        }
    }
}
