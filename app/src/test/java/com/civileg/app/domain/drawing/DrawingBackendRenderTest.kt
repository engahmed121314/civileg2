package com.civileg.app.domain.drawing

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.civileg.app.utils.EnglishFontProvider
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/** Pixel artifacts prove backend output only, never a structural member design. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
class DrawingBackendRenderTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setup() { EnglishFontProvider.init(RuntimeEnvironment.getApplication()) }

    private fun scene() = DrawingScene("Synthetic backend fixture, not a design", Rect2D(-20.0, -20.0, 120.0, 120.0), listOf(
        Primitive.Polyline(listOf(Point2D(0.0, 0.0), Point2D(100.0, 0.0), Point2D(100.0, 80.0), Point2D(0.0, 80.0)), true, DrawingLayer.OUTLINE),
        Primitive.Circle(Point2D(25.0, 25.0), 8.0, DrawingLayer.REBAR_BOTTOM),
        Primitive.RebarDot(Point2D(25.0, 25.0), 5.0, DrawingLayer.REBAR_TOP),
        Primitive.Arc(Point2D(70.0, 25.0), 12.0, 350.0, 170.0, DrawingLayer.REBAR_STIRRUP),
        Primitive.Text("φ16 λ δ √ × mm²", Point2D(0.0, 100.0), 4.0, DrawingLayer.TEXT),
        Primitive.Dimension(Point2D(0.0, -5.0), Point2D(100.0, -5.0), Point2D(42.0, -3.0), "100 mm"),
        Primitive.Leader(listOf(Point2D(70.0, 30.0), Point2D(80.0, 60.0)), "LINK")
    ))

    @Test fun `both backends render shared positioned geometry and preserve technical labels`() {
        val scene = scene()
        val output = File("build/reports/tests/drawing-shots").apply { mkdirs() }
        val bytes = PdfRenderer.renderToPdfBytes(scene)
        File(output, "shared-scene.pdf").writeBytes(bytes)
        PdfDocument(PdfReader(ByteArrayInputStream(bytes))).use { pdf ->
            val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
            listOf("φ16", "λ", "δ", "√", "×", "mm²", "100 mm", "LINK").forEach {
                assertTrue("Missing actual technical label: $it", text.contains(it))
            }
        }
        compose.setContent {
            Box(Modifier.size(480.dp, 540.dp).background(Color.White)) {
                ComposeRenderer.render(scene, Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(output, "shared-scene-ui.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var inkPixels = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val color = bitmap.getPixel(x, y)
            if (android.graphics.Color.red(color) < 200 && android.graphics.Color.green(color) < 200 && android.graphics.Color.blue(color) < 200) inkPixels++
        }
        assertTrue("Shared drawing unexpectedly blank", inkPixels > 100)
    }
}
