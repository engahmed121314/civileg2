package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import com.civileg.app.domain.calculations.base.posttensioned.PtWarning
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered-locale check for the PT warning/code-note mapping: every stable key
 * resolves to the active app language, and Arabic output carries no English
 * warning sentence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PTWarningTextI18nTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val warnings = listOf(
        PtWarning("not_converged"),
        PtWarning("prestress_less"),
        PtWarning("losses_25", listOf("26.5")),
        PtWarning("cover_below", listOf("10", "Severe", "40")),
        PtWarning("fcu_below", listOf("20.0", "Severe", "30.0")),
        PtWarning("transfer_stress"),
        PtWarning("service_stress"),
        PtWarning("not_tension_controlled"),
        PtWarning("flexure_insufficient"),
        PtWarning("shear_insufficient"),
        PtWarning("deflection_exceeds"),
        PtWarning("anchorage_insufficient")
    )

    private val notes = listOf(
        PtWarning("version", listOf("ECP 203-2020")),
        PtWarning("jacking", listOf("0.75", "0.90")),
        PtWarning("transfer", listOf("0.60", "fci", "0.25")),
        PtWarning("service", listOf("0.45", "fcu", "0.50"))
    )

    private fun pinLocale(lang: String) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
    }

    private fun setThemedContent(arabic: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            val themed: @Composable () -> Unit = {
                CivilEngineerPremiumTheme(darkTheme = false, content = content)
            }
            if (arabic) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { themed() }
            } else {
                themed()
            }
        }
        rule.waitForIdle()
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "expected visible text: $text",
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun assertTextAbsent(text: String) {
        assertTrue(
            "expected absent text: $text",
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun ptWarningTexts_english() {
        pinLocale("en")
        setThemedContent(arabic = false) {
            Column {
                warnings.forEach { Text(ptWarningText(it)) }
                notes.forEach { Text(ptCodeNoteText(it)) }
            }
        }
        assertTextPresent("Provided prestress is less than required")
        assertTextPresent("Flexural capacity insufficient")
        assertTextPresent("Deflection exceeds L/250")
        assertTextPresent("Post-tensioned design")
        assertTextAbsent("الإجهاد المسبق المتاح")
    }

    @Test
    fun ptWarningTexts_arabic() {
        pinLocale("ar")
        setThemedContent(arabic = true) {
            Column {
                warnings.forEach { Text(ptWarningText(it)) }
                notes.forEach { Text(ptCodeNoteText(it)) }
            }
        }
        assertTextPresent("الإجهاد المسبق المتاح أقل من المطلوب")
        assertTextPresent("مقاومة الانحناء غير كافية")
        assertTextPresent("الترخيم يتجاوز L/250")
        assertTextPresent("تصميم لاحق الإجهاد")
        assertTextAbsent("Provided prestress is less than required")
        assertTextAbsent("Flexural capacity insufficient")
    }
}
