package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.drawToBitmap
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.ui.compose.components.editor.NodalLoadDialog
import com.civileg.app.ui.compose.components.editor.SupportPicker
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
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
 * Rendered-locale gate for the frame-editor popup extraction: NodalLoadDialog
 * and SupportPicker rendered through the real composables under the real theme,
 * EN + AR (Arabic under LayoutDirection.Rtl, mirroring production wrapContext).
 * PropertyPanel/EditorToolbar wiring is compile-checked only (they need the
 * frame ViewModel stack); noted in the PR body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorPopupsI18nTest {

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

    private fun capture(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val out = File(dir, "$name.png")
        rule.waitForIdle()
        try {
            val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val bitmap = content.drawToBitmap()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            File(dir, "$name-capture-failed.txt").writeText(
                "window capture unavailable: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
            )
        }
    }

    @Test
    fun `nodal load dialog renders English resources in English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) { NodalLoadDialog(nodeId = 3, onConfirm = {}, onDismiss = {}) }
        assertTextPresent("Load on node 3")
        assertTextPresent("Fx (kN)")
        assertTextPresent("Fy (kN)")
        assertTextPresent("Mz (kN.m)")
        assertTextPresent("Add")
        assertTextPresent("Cancel")
        assertTextAbsent("حمولة على العقدة")
        capture("i18n-ed-nodal-en")
    }

    @Test
    fun `nodal load dialog renders Arabic resources and RTL in Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) { NodalLoadDialog(nodeId = 3, onConfirm = {}, onDismiss = {}) }
        assertTextPresent("حمولة على العقدة 3")
        assertTextPresent("Fx (kN)")
        assertTextPresent("Fy (kN)")
        assertTextPresent("Mz (kN.m)")
        assertTextPresent("إضافة")
        assertTextPresent("إلغاء")
        assertTextAbsent("Load on node")
        capture("i18n-ed-nodal-ar")
    }

    @Test
    fun `support picker renders English resources in English locale`() {
        pinLocale("en")
        setThemedContent(arabic = false) {
            SupportPicker(nodeId = 5, currentSupport = SupportType.Pin, onSupportSelected = {}, onDismiss = {})
        }
        assertTextPresent("Support type for node 5")
        assertTextPresent("Cancel")
        assertTextAbsent("نوع الارتكاز")
        capture("i18n-ed-support-en")
    }

    @Test
    fun `support picker renders Arabic resources and RTL in Arabic locale`() {
        pinLocale("ar")
        setThemedContent(arabic = true) {
            SupportPicker(nodeId = 5, currentSupport = SupportType.Pin, onSupportSelected = {}, onDismiss = {})
        }
        assertTextPresent("نوع الارتكاز للعقدة 5")
        assertTextPresent("إلغاء")
        assertTextAbsent("Support type for node")
        capture("i18n-ed-support-ar")
    }
}
