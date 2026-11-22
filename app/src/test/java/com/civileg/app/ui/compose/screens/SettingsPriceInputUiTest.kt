package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.civileg.app.R
import com.civileg.app.testutils.saveNativeComposeScreenshot
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real Settings price field: unset shows "Not set", never a default; a legacy value is explained, not used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPriceInputUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(value: String, legacy: Double?) {
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    SettingsPriceInput(label = "Steel", value = value, currency = "SAR", legacyValue = legacy, onValueChange = {})
                }
            }
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, name) }
    }

    @Test @Config(qualifiers = "en")
    fun unsetShowsNotSetAndNoDefaultNumber() {
        show("", null)
        rule.onNodeWithText("Not set").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("18000", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("45000", substring = true).fetchSemanticsNodes().size)
        shot("settings-price-unset-en")
    }

    @Test @Config(qualifiers = "en")
    fun legacyValueIsExplainedNotPrefilled() {
        show("", 18000.0)
        rule.onNodeWithText(rule.activity.getString(R.string.settings_price_legacy_unknown_currency, "18000.0")).assertIsDisplayed()
        shot("settings-price-legacy-en")
    }

    @Test @Config(qualifiers = "ar")
    fun legacyValueArabic() {
        show("", 18000.0)
        rule.onNodeWithText(rule.activity.getString(R.string.settings_price_legacy_unknown_currency, "18000.0")).assertIsDisplayed()
        assertEquals(false, rule.activity.getString(R.string.settings_price_not_set) == "Not set")
        shot("settings-price-legacy-ar")
    }

    @Test @Config(qualifiers = "en")
    fun enteredValueShowsInFieldWithCurrency() {
        show("38000.0", null)
        rule.onNodeWithText("SAR").assertIsDisplayed()
        rule.onNodeWithText("38000.0").assertIsDisplayed()
    }
}
