package com.civileg.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import com.civileg.app.BuildConfig
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.civileg.app.ui.components.AppButton
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Thresholds come from the actual bundled-font native measurement, not guessed character counts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArabicUiButtonFitNativeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `English-theme Arabic button uses identical font and explicit weight for fit and draw`() {
        val full = "حفظ التصميم الإنشائي"
        val short = "حفظ"
        var measuredWidth = 0
        lateinit var host: View
        compose.setContent {
            host = LocalView.current
            MaterialTheme(typography = typographyForUiLanguage(PremiumTypography.Typography, "en")) {
                val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                val annotated = arabicUiText(full)
                measuredWidth = rememberTextMeasurer().measure(annotated, style = style, maxLines = 1).size.width
                val density = LocalDensity.current
                val justTooNarrow = with(density) { (measuredWidth - 4).toDp() } + 32.dp
                val justWideEnough = with(density) { (measuredWidth + 4).toDp() } + 32.dp
                Column {
                    AppButton(full, {}, modifier = Modifier.width(justTooNarrow), labelShort = short,
                        fontWeight = FontWeight.Bold)
                    AppButton(full, {}, modifier = Modifier.width(justWideEnough), labelShort = short,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val image = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(image))
            val directory = File("build/reports/tests/arabic-ui-font/${BuildConfig.FLAVOR}").apply { mkdirs() }
            File(directory, "english-theme-arabic-button-fit.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
        }
        assertTrue("Native text measurement must be nonzero", measuredWidth > 0)
        for (chosen in listOf(short, full)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(chosen, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val result = layouts.single()
            assertEquals(chosen, result.layoutInput.text.text)
            assertEquals(FontWeight.Bold, result.layoutInput.style.fontWeight)
            assertTrue(result.layoutInput.text.spanStyles.any { it.item.fontFamily == ArabicUiFontFamily })
            assertFalse(result.hasVisualOverflow)
            assertFalse(result.isLineEllipsized(0))
        }
    }
}
