package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.civileg.app.R
import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.BoqItem
import com.civileg.app.domain.entities.BoqPricing
import com.civileg.app.domain.entities.SavedMaterialPrices
import com.civileg.app.domain.entities.SavedPrice
import com.civileg.app.testutils.saveNativeComposeScreenshot
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real BOQ detail card (the composable BOQScreen uses) with lines produced by the real
 * BoqPricing.apply. Fixture data, not a full-screen real-flow run: the ViewModel and DB are not started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoqPricedStateUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun line(c: BoqCategory, desc: String, q: Double, unit: String) =
        BoqItem(itemId = c.name, description = desc, category = c, unit = unit, quantity = q, unitPrice = 1.0, total = q)

    private val raw = listOf(
        line(BoqCategory.CONCRETE, "Concrete C30", 2.0, "m3"),
        line(BoqCategory.REINFORCEMENT, "Steel", 1.0, "ton"),
        line(BoqCategory.FORMWORK, "Formwork", 10.0, "m2"),
        line(BoqCategory.EXCAVATION, "Excavation", 5.0, "m3")
    )

    private fun show(saved: SavedMaterialPrices, currency: String) {
        val r = BoqPricing.apply(raw, saved, currency)
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                BoqDetailCard(r.items, r.pricedTotal, r.unpricedCount, r.unknownCurrencyCount, currency)
            }
        }
        rule.waitForIdle()
    }

    private fun str(id: Int, vararg a: Any) = rule.activity.getString(id, *a)

    private fun shot(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, name) }
    }

    @Test @Config(qualifiers = "en")
    fun partialTotalEnglish() {
        show(SavedMaterialPrices(steel = SavedPrice(38000.0, "SAR")), "SAR")
        assertEquals(2, rule.onAllNodesWithText("38,000 SAR").fetchSemanticsNodes().size) // steel line and priced-only total
        assertEquals(3, rule.onAllNodesWithText("Price not set").fetchSemanticsNodes().size)
        rule.onNodeWithText("Total of priced items only (3 without price)").assertIsDisplayed()
        shot("boq-partial-en")
    }

    @Test @Config(qualifiers = "ar")
    fun partialTotalArabic() {
        show(SavedMaterialPrices(steel = SavedPrice(38000.0, "SAR")), "SAR")
        val notSet = str(R.string.boq_price_not_set)
        assertEquals(3, rule.onAllNodesWithText(notSet).fetchSemanticsNodes().size)
        rule.onNodeWithText(str(R.string.boq_total_priced_only, 3)).assertIsDisplayed()
        assertEquals(false, notSet == "Price not set")
        shot("boq-partial-ar")
    }

    @Test @Config(qualifiers = "en")
    fun nothingSavedShowsNoPricedTotalFigure() {
        show(SavedMaterialPrices(), "EGP")
        assertEquals(4, rule.onAllNodesWithText("Price not set").fetchSemanticsNodes().size)
        rule.onNodeWithText("0 EGP").assertIsDisplayed()
        rule.onNodeWithText("Total of priced items only (4 without price)").assertIsDisplayed()
        shot("boq-nothing-saved-en")
    }

    @Test @Config(qualifiers = "en")
    fun legacyPriceWithoutCurrencyIsNotPricedAndExplained() {
        show(SavedMaterialPrices(steel = SavedPrice(18000.0, null)), "EGP")
        assertEquals(4, rule.onAllNodesWithText("Price not set").fetchSemanticsNodes().size)
        rule.onNodeWithText(str(R.string.boq_currency_not_recorded, 1)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("18,000", substring = true).fetchSemanticsNodes().size)
        shot("boq-legacy-currency-en")
    }

    @Test @Config(qualifiers = "ar")
    fun legacyPriceWithoutCurrencyArabic() {
        show(SavedMaterialPrices(steel = SavedPrice(18000.0, null)), "EGP")
        rule.onNodeWithText(str(R.string.boq_currency_not_recorded, 1)).assertIsDisplayed()
        shot("boq-legacy-currency-ar")
    }

    @Test @Config(qualifiers = "en")
    fun completePricingShowsPlainTotal() {
        val all = SavedMaterialPrices(SavedPrice(1000.0, "EGP"), SavedPrice(38000.0, "EGP"), SavedPrice(100.0, "EGP"))
        val r = BoqPricing.apply(raw.dropLast(1), all, "EGP")
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                BoqDetailCard(r.items, r.pricedTotal, r.unpricedCount, r.unknownCurrencyCount, "EGP")
            }
        }
        rule.onNodeWithText("Total").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Price not set").fetchSemanticsNodes().size)
        rule.onNodeWithText("41,000 EGP").assertIsDisplayed()
        shot("boq-complete-en")
    }
}
