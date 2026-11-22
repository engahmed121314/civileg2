package com.civileg.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.civileg.app.ui.components.AppButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.civileg.app.BuildConfig
import com.civileg.app.ui.components.AppText
import com.civileg.app.ui.components.AppTextRole
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production text/typography with bundled fonts. Pixel review remains a separate gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArabicUiFontNativeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var host: View

    private fun inspect(scale: Float, language: String = "ar") {
        compose.setContent {
            host = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, scale),
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(typography = typographyForUiLanguage(PremiumTypography.Typography, language)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        AppText("مراجعة التصميم الإنشائي", role = AppTextRole.Headline,
                            modifier = Modifier.testTag("heading"))
                        AppText("فَحْصُ التَّسْلِيحِ وَالأَحْمَالِ", role = AppTextRole.Body,
                            modifier = Modifier.testTag("marks"))
                        AppText("التسليح: 9Ø20 | As = 2827.43 mm² | σ ≤ 420 MPa",
                            role = AppTextRole.Result, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("mixed"))
                        AppText("١٢٣٤٥٦٧٨٩٠ | 0123456789", role = AppTextRole.Body,
                            modifier = Modifier.testTag("digits"))
                        AppButton(label = "حفظ المشروع", onClick = {}, fillMaxWidth = false)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val image = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(image))
            val directory = File("build/reports/tests/arabic-ui-font/${BuildConfig.FLAVOR}").apply { mkdirs() }
            File(directory, "arabic-ui-${host.width}x${host.height}-scale$scale-$language.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
        }
        for (tag in listOf("heading", "marks", "mixed", "digits")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val result = layouts.single()
            assertFalse("Text overflow in $tag at scale=$scale", result.hasVisualOverflow)
            assertTrue(result.lineCount > 0)
            assertTrue(result.getLineTop(0) >= 0)
            assertTrue(result.getLineBottom(result.lineCount - 1) <= result.size.height + 1)
        }

    }

    @Test fun `Arabic text and mixed engineering units render at ordinary phone size`() = inspect(1f)

    @Test @Config(sdk = [34], qualifiers = "w320dp-h800dp-mdpi")
    fun `small phone and larger accessibility text retain full result content`() = inspect(1.5f)

    @Test @Config(sdk = [34], qualifiers = "w600dp-h900dp-mdpi")
    fun `wide layout retains Arabic marks and both numeral sets`() = inspect(1.3f)

    @Test fun `English theme retains Latin typography while Arabic spans keep the chosen font`() = inspect(1f, "en")
}
