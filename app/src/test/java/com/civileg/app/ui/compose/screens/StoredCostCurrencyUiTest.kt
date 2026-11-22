package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.civileg.app.R
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.civileg.app.testutils.saveNativeComposeScreenshot
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real saved-design row (DesignBOQCard) and the real project cost card (TotalCostCard).
 * Stored design costs have no recorded currency, so the row shows the raw number with a
 * "currency not recorded" note and the card shows no summed headline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoredCostCurrencyUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val design = Design(
        projectId = 1L, type = DesignType.BEAM, name = "B1", inputData = "{}", results = "{}", isSafe = true,
        codeUsed = "ECP", totalCost = 123456.0
    )

    private fun show() {
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                Column {
                    DesignBOQCard(design)
                    TotalCostCard(123456.0 + 654321.0)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun str(id: Int) = rule.activity.getString(id)

    private fun shot(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, name) }
    }

    private fun check() {
        rule.onNodeWithText("123,456").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.boq_cost_currency_not_recorded_short)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.boq_total_not_available)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.boq_stored_cost_currency_unknown)).assertIsDisplayed()
        // no summed headline and no currency claim
        assertEquals(0, rule.onAllNodesWithText("777,777", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("EGP", substring = true).fetchSemanticsNodes().size)
    }

    @Test @Config(qualifiers = "en")
    fun englishRowNoteAndNoSummedHeadline() {
        show(); check()
        assertEquals("Currency not recorded", str(R.string.boq_cost_currency_not_recorded_short))
        shot("stored-cost-currency-en")
    }

    @Test @Config(qualifiers = "ar")
    fun arabicRowNoteAndNoSummedHeadline() {
        show(); check()
        assertNotEquals("Currency not recorded", str(R.string.boq_cost_currency_not_recorded_short))
        shot("stored-cost-currency-ar")
    }
}
