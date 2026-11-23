package com.civileg.app

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import android.content.res.Configuration

/**
 * Compose UI smoke tests for the main design flow, running on an emulator in
 * CI (see the ui-test job in .github/workflows/android.yml).
 *
 * The app defaults to Arabic on a fresh install (LocaleHelper), so expected
 * strings are resolved against an Arabic configuration context.
 *
 * Covered flow: launch -> bottom nav -> Design Hub -> Column design ->
 * enter values -> run design -> results appear.
 */
class DesignPagesUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun dismissDonationPromptIfShown() {
        rule.waitForIdle()
        val decline = arString(R.string.donation_decline)
        if (rule.onAllNodesWithText(decline).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText(decline).performClick()
            rule.waitForIdle()
        }
    }

    private fun arString(@StringRes id: Int): String {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration)
        config.setLocale(Locale("ar"))
        return target.createConfigurationContext(config).getString(id)
    }

    private fun scrollToText(text: String, timeoutMs: Long = 15_000) {
        rule.waitUntil(timeoutMs) {
            runCatching {
                rule.onAllNodes(hasScrollAction())[0]
                    .performScrollToNode(hasText(text))
            }
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun home_bottomNavigationIsVisible() {
        rule.waitForIdle()
        // The Home label also appears elsewhere on screen (e.g. header), so
        // assert on the first matching node instead of a unique one.
        rule.onAllNodesWithText(arString(R.string.nav_home))[0].assertIsDisplayed()
        rule.onAllNodesWithText(arString(R.string.nav_design_hub))[0].assertIsDisplayed()
    }

    @Test
    fun designHub_opensColumnDesign() {
        rule.onNodeWithText(arString(R.string.nav_design_hub)).performClick()
        rule.waitForIdle()
        // Two cards (Columns, Biaxial) share this title; the first is the
        // standard column design.
        rule.onAllNodesWithText(arString(R.string.home_column))[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText(arString(R.string.screen_column_title)).assertIsDisplayed()
    }

    @Test
    fun designHub_opensFrameAnalysis_defaultStateRenders() {
        // Frame screen entry at the app's real entry path and default state:
        // the drawing tab (selected by default) must render without crashing.
        rule.onNodeWithText(arString(R.string.nav_design_hub)).performClick()
        rule.waitForIdle()
        scrollToText(arString(R.string.home_frame))
        rule.onNodeWithText(arString(R.string.home_frame)).performClick()
        rule.waitForIdle()
        // Top bar title is only composed if the drawing tab rendered.
        rule.onNodeWithText(arString(R.string.frame_title)).assertIsDisplayed()

        // The results tab must also render at empty/default state.
        rule.onNodeWithText(arString(R.string.frame_tab_results)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(arString(R.string.frame_title)).assertIsDisplayed()
    }

    @Test
    fun columnDesign_enterValues_producesResult() {
        // Navigate: bottom tab -> Design Hub -> column card.
        rule.onNodeWithText(arString(R.string.nav_design_hub)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(arString(R.string.home_column))[0].performClick()
        rule.waitForIdle()

        // Enter section width and axial load.
        rule.onNode(
            hasSetTextAction() and hasText(arString(R.string.column_width_t_mm))
        ).performTextReplacement("400")
        rule.onNode(
            hasSetTextAction() and hasText(arString(R.string.column_axial_load_pu))
        ).performTextReplacement("1500")

        // Run the design.
        val designNow = arString(R.string.column_design_now)
        scrollToText(designNow)
        rule.onNodeWithText(designNow).performClick()

        // A computed result section must appear.
        val consultantRatio = arString(R.string.consultant_ratio)
        scrollToText(consultantRatio)
        rule.onNodeWithText(consultantRatio).assertIsDisplayed()
    }
}
