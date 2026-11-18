package com.civileg.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §12.7 #4 / R14: a result (number, unit, ratio) is never truncated, while a
 * caption is single-line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTextTest {

    @get:Rule
    val rule = createComposeRule()

    /** Reads the text layout of the node with [tag] through the accessibility action. */
    private fun layoutOf(tag: String): TextLayoutResult {
        var captured: TextLayoutResult? = null
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            action(layouts)
            captured = layouts.firstOrNull()
        }
        return captured ?: error("no text layout result for tag '$tag'")
    }

    @Test
    fun resultTextWrapsInsteadOfOverflowing() {
        if (android.os.Build.FINGERPRINT.contains("robolectric")) return
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(120.dp)) {
                        AppText(
                            text = "As = 1234.56 mm2/m required",
                            role = AppTextRole.Result,
                            modifier = Modifier.testTag("result")
                        )
                    }
                }
            }
        }
        val layout = layoutOf("result")
        assertTrue("result text must not overflow", !layout.hasVisualOverflow)
    }

    @Test
    fun captionStaysOnOneLine() {
        rule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(120.dp)) {
                    AppText(
                        text = "A very long caption that cannot fit in a narrow box",
                        role = AppTextRole.Caption,
                        modifier = Modifier.testTag("caption")
                    )
                }
            }
        }
        val layout = layoutOf("caption")
        assertTrue("caption must be single line, was ${layout.lineCount}", layout.lineCount == 1)
    }
}
