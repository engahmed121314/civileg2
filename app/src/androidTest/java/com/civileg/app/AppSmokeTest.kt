package com.civileg.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke tests used by tools/emulator/Test-App.ps1.
 *
 * These tests launch the real MainActivity (Compose + Hilt + Room) and fail
 * if the app crashes during startup, which catches wiring/DI regressions that
 * JVM unit tests cannot see.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @Test
    fun appContextIsCorrect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.civileg.app", context.packageName)
    }

    @Test
    fun mainActivityLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MainActivity finished during startup", activity.isFinishing)
            }
        }
    }
}
