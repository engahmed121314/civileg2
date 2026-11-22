package com.civileg.app.ui.compose.components.charts

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.drawToBitmap
import com.civileg.app.domain.calculations.base.SpectrumValue
import com.civileg.app.ui.compose.screens.PostTensionedScreen
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.PostTensionedViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Rendered-locale gate for the charts + Post-Tensioned i18n slice: the real
 * composables rendered under the real theme in English and Arabic, with Arabic
 * wrapped in LayoutDirection.Rtl mirroring production (LocaleHelper.wrapContext).
 * Verifies the extracted strings actually switch with the app language and that
 * no inline bilingual "EN / AR" literal remains on these surfaces. Captures go
 * to compose-shots for pixel audit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class I18nChartsPtVisualTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

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

    private fun capture(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = content.drawToBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "expected visible text: $text",
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun assertTextAbsent(text: String) {
        assertEquals(
            "unexpected text present: $text",
            0,
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
        )
    }

    @Composable
    private fun charts() {
        Column {
            InteractionDiagramChart(
                axialCapacities = listOf(500.0, 1500.0, 2500.0),
                momentCapacities = listOf(120.0, 200.0, 150.0),
                currentLoad = 1200.0 to 160.0
            )
            SeismicResponseChart(
                spectrumValues = listOf(
                    SpectrumValue(0.25, 0.1, 0.05, "a"),
                    SpectrumValue(0.5, 0.4, 0.05, "b"),
                    SpectrumValue(0.35, 1.0, 0.05, "c")
                ),
                designPeriod = 0.6
            )
        }
    }

    @Test
    fun `charts render English strings under English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) { charts() }
        assertTextPresent("P-M Interaction Diagram")
        assertTextPresent("Response Spectrum Analysis")
        assertTextPresent("Current Load")
        assertTextAbsent("مخطط التفاعل")
        assertTextAbsent("تحليل طيف الاستجابة")
        capture("i18n-charts-en")
    }

    @Test
    fun `charts render Arabic strings under Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) { charts() }
        assertTextPresent("مخطط التفاعل")
        assertTextPresent("تحليل طيف الاستجابة")
        assertTextPresent("الحمل الحالي")
        assertTextAbsent("P-M Interaction Diagram")
        assertTextAbsent("Response Spectrum Analysis")
        capture("i18n-charts-ar")
    }

    @Test
    fun `post-tensioned screen renders English strings under English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) { PostTensionedScreen(viewModel = PostTensionedViewModel()) }
        assertTextPresent("Post-Tensioned Element")
        assertTextPresent("Element Type")
        assertTextAbsent("عنصر مسبق الإجهاد")
        capture("i18n-pt-en")
    }

    @Test
    fun `post-tensioned screen renders Arabic strings under Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) { PostTensionedScreen(viewModel = PostTensionedViewModel()) }
        assertTextPresent("عنصر مسبق الإجهاد")
        assertTextPresent("نوع العنصر")
        assertTextAbsent("Post-Tensioned Element /")
        capture("i18n-pt-ar")
    }
}
