package com.civileg.app.support

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DonationPromptStateTest {

    private lateinit var context: Context
    private lateinit var state: DonationPromptState

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("donation_prompt", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        state = DonationPromptState(context)
    }

    @Test
    fun `prompt waits for the configured number of launches`() {
        assumeTrue(DonationConfig.ENABLED)

        repeat(DonationConfig.MIN_LAUNCHES_BEFORE_PROMPT - 1) {
            assertFalse(state.registerLaunchAndCheck())
        }
        assertTrue(state.registerLaunchAndCheck())
    }

    @Test
    fun `an answer is final for the install`() {
        assumeTrue(DonationConfig.ENABLED)

        repeat(DonationConfig.MIN_LAUNCHES_BEFORE_PROMPT) { state.registerLaunchAndCheck() }
        state.markAnswered()

        repeat(5) { assertFalse(state.registerLaunchAndCheck()) }
    }

    @Test
    fun `declining never changes application state`() {
        assumeTrue(DonationConfig.ENABLED)

        repeat(DonationConfig.MIN_LAUNCHES_BEFORE_PROMPT) { state.registerLaunchAndCheck() }
        state.markAnswered()

        // The only persisted effects are the launch counter and the "answered" flag.
        val prefs = context.getSharedPreferences("donation_prompt", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("answered", false))
        assertFalse(prefs.contains("is_premium_user"))
    }
}
