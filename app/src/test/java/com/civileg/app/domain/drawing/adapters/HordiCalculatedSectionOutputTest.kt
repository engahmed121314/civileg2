package com.civileg.app.domain.drawing.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import com.civileg.app.ui.compose.components.drawings.HordiCalculatedSectionOutput
import com.civileg.app.ui.compose.components.drawings.HordiSectionPreviewStrings
import com.civileg.app.utils.EnglishFontProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/** Actual component pixels from calculation-produced fixtures; not execution acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h1800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HordiCalculatedSectionOutputTest {
    @get:Rule val compose = createComposeRule()
    @Before fun setup() { EnglishFontProvider.init(RuntimeEnvironment.getApplication()) }

    private fun capture(name: String, arabic: Boolean) {
        val fixture = HordiCalculatedSectionAdapterTest().fixture(name)
        val output = fixture.handoff()
        val policyNote = HordiCalculatedSectionAdapter.build(output, "H-REVIEW").arrangementPolicyNote
        val directory = File("build/reports/tests/hordi-section-shots").apply { mkdirs() }
        File(directory, "$name-${if (arabic) "ar" else "en"}-review.pdf")
            .writeBytes(HordiCalculatedSectionAdapter.renderReviewPdfBytes(output, "H-REVIEW"))
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        HordiCalculatedSectionOutput(output, "H-REVIEW", arabic, Modifier.testTag("section-output"))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(fixture.revisionId, substring = true).assertExists()
        fixture.incompleteChecks.forEach {
            compose.onNodeWithText(HordiSectionPreviewStrings.warning(it, arabic)).assertExists()
            if (arabic) compose.onNodeWithText(it).assertDoesNotExist()
        }
        if (arabic) compose.onNodeWithText("Required As", substring = true).assertDoesNotExist()
        policyNote?.let {
            compose.onNodeWithText(HordiSectionPreviewStrings.policyNote(it, arabic)).assertExists()
            if (arabic) compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithText(if (arabic) "التنفيذ والتصدير التنفيذي محجوبان. قيود الحساب والتنفيذ:" else
            "Execution and execution exports are blocked. Original calculation limitations:").assertExists()
        val bounds = compose.onNodeWithTag("section-output").fetchSemanticsNode().boundsInRoot
        (fixture.incompleteChecks + fixture.section.incompleteGeometry).distinct().forEach { warning ->
            val warningNode = compose.onNodeWithText(HordiSectionPreviewStrings.warning(warning, arabic)).fetchSemanticsNode()
            assertTrue("Source hold is clipped below the component", warningNode.positionInRoot.y + warningNode.size.height <= bounds.bottom)
            assertTrue("Source hold is clipped horizontally", warningNode.positionInRoot.x >= bounds.left && warningNode.positionInRoot.x + warningNode.size.width <= bounds.right)
        }
        policyNote?.let {
            val displayed = HordiSectionPreviewStrings.policyNote(it, arabic)
            val policyNode = compose.onNodeWithText(displayed)
            val node = policyNode.fetchSemanticsNode()
            assertTrue("Selected policy disclosure is clipped", node.positionInRoot.y + node.size.height <= bounds.bottom)
            if (arabic) {
                val rows = it.substringAfter("actual adopted rows=").substringBefore(". ")
                val start = displayed.indexOf(rows)
                assertTrue(start >= 0)
                val layouts = mutableListOf<TextLayoutResult>()
                policyNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
                val layout = layouts.single()
                val first = layout.getBoundingBox(start)
                val last = layout.getBoundingBox(start + rows.lastIndex)
                assertTrue("Actual Arabic numeric row order is reversed", first.left < last.left && first.top == last.top)
            }
        }
        val bitmap = compose.runOnIdle {
            assertTrue("Component exceeds the captured viewport; do not hide warnings", bounds.bottom <= view.height)
            assertTrue(bounds.width > 0 && bounds.height > 0)
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            Bitmap.createBitmap(full, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
        }
        File(directory, "$name-${if (arabic) "ar" else "en"}-ui.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Expected a phone-width output proof", bitmap.width == 320)
    }

    @Test fun `bottom bars English preview preserves frozen section and all holds`() = capture("bottom-two-bars", false)
    @Test fun `top bars Arabic preview retains physical pitch and blocked execution status`() = capture("top-two-bars", true)
    @Test fun `multiple layers English preview retains all adopted bar levels`() = capture("bottom-two-layers", false)
    @Test fun `bottom bars Arabic preview localizes captions and every source hold`() = capture("bottom-two-bars", true)
    @Test fun `top bars English preview retains the English presentation`() = capture("top-two-bars", false)
    @Test fun `multiple layers Arabic preview localizes captions and every source hold`() = capture("bottom-two-layers", true)
    @Test fun `corrected paired upper five bars English preview uses adopted source revision`() = capture("bottom-three-plus-two-corrected", false)
    @Test fun `corrected paired upper five bars Arabic preview preserves policy and holds`() = capture("bottom-three-plus-two-corrected", true)
}
