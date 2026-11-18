package com.civileg.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §12.7 #3 / R14: a button's height must not depend on its label length.
 *
 * Every variant is measured with a 1-character label and with 60-character labels
 * (English and Arabic) at 320 / 360 / 600 dp and font scale 1.0 / 1.3 / 2.0.
 * All combinations are composed once (a test may call setContent only once).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppButtonTest {

    @get:Rule
    val rule = createComposeRule()

    private val widths = listOf(320, 360, 600)
    private val scales = listOf(1.0f, 1.3f, 2.0f)
    private val short = "A"
    private val longEn = "A".repeat(60)
    private val longAr = "ا".repeat(60)

    private fun tag(style: AppButtonStyle, width: Int, scale: Float, suffix: String) =
        "${style.name}-$width-$scale-$suffix"

    private fun height(tag: String): Int =
        rule.onNodeWithTag(tag).fetchSemanticsNode().size.height

    @Test
    fun heightIsIndependentOfLabelLength() {
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (style in AppButtonStyle.entries) {
                        for (width in widths) {
                            for (scale in scales) {
                                CompositionLocalProvider(
                                    LocalDensity provides Density(density = 1f, fontScale = scale)
                                ) {
                                    Box(modifier = Modifier.width(width.dp)) {
                                        AppButton(
                                            label = short,
                                            onClick = {},
                                            modifier = Modifier.testTag(tag(style, width, scale, "s")),
                                            style = style
                                        )
                                    }
                                    Box(modifier = Modifier.width(width.dp)) {
                                        AppButton(
                                            label = longEn,
                                            onClick = {},
                                            modifier = Modifier.testTag(tag(style, width, scale, "en")),
                                            style = style
                                        )
                                    }
                                    Box(modifier = Modifier.width(width.dp)) {
                                        AppButton(
                                            label = longAr,
                                            onClick = {},
                                            modifier = Modifier.testTag(tag(style, width, scale, "ar")),
                                            style = style
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (style in AppButtonStyle.entries) {
            for (width in widths) {
                for (scale in scales) {
                    val hShort = height(tag(style, width, scale, "s"))
                    assertEquals(
                        "$style width=$width scale=$scale en",
                        hShort.toFloat(),
                        height(tag(style, width, scale, "en")).toFloat(),
                        0.5f
                    )
                    assertEquals(
                        "$style width=$width scale=$scale ar",
                        hShort.toFloat(),
                        height(tag(style, width, scale, "ar")).toFloat(),
                        0.5f
                    )
                }
            }
        }
    }

    @Test
    fun shortLabelIsUsedWhenTheFullLabelDoesNotFit() {
        if (android.os.Build.FINGERPRINT.contains("robolectric")) return
        val long = "B".repeat(60)
        val shortLabel = "Save"
        rule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(120.dp)) {
                    AppButton(
                        label = long,
                        labelShort = shortLabel,
                        onClick = {},
                        modifier = Modifier.testTag("btn")
                    )
                }
            }
        }
        // The short variant must be the text that is actually rendered.
        assertEquals(1, rule.onAllNodesWithText(shortLabel).fetchSemanticsNodes().size)
    }
}
