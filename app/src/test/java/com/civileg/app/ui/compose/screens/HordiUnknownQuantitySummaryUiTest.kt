package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.entities.ProjectSummary
import com.civileg.app.testutils.saveNativeComposeScreenshot
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual summary KPI component, not a claim about the full project screen or navigation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HordiUnknownQuantitySummaryUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun verify(lang: String) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
        val summary = ProjectSummary(designCount = 4, nominalCount = 1, legacyCount = 2,
            unknownQuantityCount = 1, nominalConcrete = 2.5, nominalSteel = 1250.0,
            legacyConcrete = 5.0, legacySteel = 2500.0)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CivilEngineerPremiumTheme(darkTheme = false) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
                        SummaryKPIRow(summary)
                    }
                }
            }
        }
        rule.onNodeWithText("Measured Concrete").assertIsDisplayed()
        rule.onNodeWithText("Unknown Quantities").assertIsDisplayed()
        rule.onNodeWithText("0.0").assertDoesNotExist()
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, "hordi-unknown-summary-$lang") }
    }

    @Test fun englishSummaryDoesNotPromoteUnknownToMeasuredZero() { verify("en") }
    @Test fun rtlSummaryDoesNotPromoteUnknownToMeasuredZero() { verify("ar") }
}
