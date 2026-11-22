package com.civileg.app.ui.compose.components.drawings

import android.graphics.Bitmap
import android.graphics.Canvas
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.StirrupZone
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** Production painter / layout fixtures, NOT checked member designs or full application export proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnDrawingLayoutTest {
    // Explicit supplied coordinate fixture. The renderer must not generate or normalize its bars.
    private fun rectangle() = listOf(
        BarInfo(43.0, 43.0, 20.0), BarInfo(150.0, 43.0, 20.0), BarInfo(257.0, 43.0, 20.0),
        BarInfo(43.0, 257.0, 20.0), BarInfo(150.0, 257.0, 20.0), BarInfo(257.0, 257.0, 20.0),
        BarInfo(43.0, 150.0, 20.0), BarInfo(257.0, 150.0, 20.0)
    )
    private fun circle() = listOf(
        BarInfo(100.0, 200.0, 16.0), BarInfo(200.0, 100.0, 16.0),
        BarInfo(300.0, 200.0, 16.0), BarInfo(200.0, 300.0, 16.0)
    )
    private val output get() = File("build/reports/tests/column-layout-shots").apply { mkdirs() }

    @Test fun `panels are finite disjoint and contained across modes and portrait landscape viewports`() {
        for ((w, h) in listOf(320f to 560f, 400f to 777f, 900f to 650f, 1200f to 900f)) {
            for (mode in 0..3) {
                val layout = requireNotNull(columnSheetLayout(w, h, mode, 9))
                val page = ColumnSheetBox(0f, 0f, layout.width, layout.height)
                assertTrue(layout.scale.isFinite() && layout.scale > 0)
                layout.boxes.forEach { assertTrue("Outside page: $it", page.contains(it)) }
                layout.boxes.forEachIndexed { index, box ->
                    layout.boxes.drop(index + 1).forEach { assertFalse("Overlapping panels: $box and $it", box.intersects(it)) }
                }
            }
        }
    }

    @Test fun `member fit is finite uses portrait height and rejects invalid or exhausted viewports`() {
        val box = ColumnSheetBox(0f, 0f, 200f, 800f)
        assertEquals(800.0 / 3000.0, requireNotNull(columnFitScale(box, 300.0, 3000.0)), 1e-12)
        for ((w, h) in listOf(300.0 to 3000.0, 10000.0 to 100.0, 100.0 to 1000000.0)) {
            val scale = requireNotNull(columnFitScale(box, w, h))
            assertTrue(scale.isFinite() && scale > 0)
            assertTrue(w * scale <= box.width + 1e-6)
            assertTrue(h * scale <= box.height + 1e-6)
        }
        assertNull(columnFitScale(box, Double.NaN, 3.0))
        assertNull(columnFitScale(box, 3.0, Double.POSITIVE_INFINITY))
        assertNull(columnFitScale(box.copy(width = -1f), 3.0, 4.0))
        assertNull(columnSheetLayout(100f, 100f, 0, 10))
        assertNull(columnSheetLayout(Float.NaN, 400f, 0, 1))
    }

    @Test fun `actual production painter keeps all text in owned panels and preserves each supplied center`() {
        for ((w, h) in listOf(400 to 777, 900 to 650, 1200 to 900)) {
            for (circular in listOf(false, true)) for (mode in 0..3) {
                val bars = if (circular) circle() else rectangle()
                val dimension = if (circular) 400.0 else 300.0
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val evidence = renderColumnSheet(Canvas(bitmap), w.toFloat(), h.toFloat(), dimension, dimension,
                    3000.0, bars, 10.0, 150.0, 40.0, sectionType = if (circular) "Circular" else "Rectangular",
                    zones = listOf(StirrupZone("Lower", 0.0, 600.0, 100.0), StirrupZone("Upper", 2400.0, 3000.0, 100.0)),
                    designCode = DesignCode.ECP, viewMode = mode,
                    resultSummary = DrawingResultSummary(isSafe = false, utilizationRatio = 0.48))
                assertNotNull(evidence.layout)
                assertNull(evidence.geometryIssue)
                assertTrue("Text exceeds its panel: ${evidence.text.filterNot { it.panel.contains(it.bounds) }}", evidence.allTextFits)
                assertEquals(bars, evidence.suppliedBars)
                if (mode == 0 || mode == 2) assertEquals(bars.map { it.x to it.y }, evidence.drawnSectionCenters)
                if (mode == 0 || mode == 1) assertTrue(requireNotNull(evidence.projectionScale).isFinite())
                if (mode == 0 || mode == 2) assertTrue(requireNotNull(evidence.sectionScale).isFinite())
                val name = "${if (circular) "circular" else "rectangular"}-${w}x${h}-mode$mode"
                val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                File(output, "$name.png").writeBytes(png)
                if (w == 400 && !circular) {
                    // Exact native drawing raster in a review PDF, not an ExportManager/full-report assertion.
                    PdfDocument(PdfWriter(File(output, "$name-review.pdf"))).use { pdf ->
                        val page = pdf.addNewPage(PageSize(w.toFloat(), h.toFloat()))
                        PdfCanvas(page).addImageFittedIntoRectangle(ImageDataFactory.create(png), Rectangle(0f, 0f, w.toFloat(), h.toFloat()), false)
                    }
                }
                bitmap.recycle()
            }
        }
    }

    @Test fun `duplicate centers fail closed rather than silently drawing fewer bars or moving a bar`() {
        // The old caller can supply the same center twice. It is NOT repaired into an invented layout.
        val bars = rectangle() + rectangle().first()
        val bitmap = Bitmap.createBitmap(400, 777, Bitmap.Config.ARGB_8888)
        val evidence = renderColumnSheet(Canvas(bitmap), 400f, 777f, 300.0, 300.0, 3000.0,
            bars, 10.0, 300.0, 40.0)
        assertTrue(evidence.geometryIssue.orEmpty().contains("Duplicate"))
        assertEquals(bars, evidence.suppliedBars)
        assertTrue(evidence.drawnSectionCenters.isEmpty())
        assertNull(evidence.sectionScale)
        assertNull(evidence.projectionScale)
        assertTrue(evidence.allTextFits)
        File(output, "duplicate-supplied-centers-withheld.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun `nonfinite and out of section coordinates never reach drawing transforms`() {
        assertNotNull(columnCoordinateIssue(300.0, 300.0, 3000.0, listOf(BarInfo(Double.NaN, 40.0, 20.0)), false))
        assertNotNull(columnCoordinateIssue(300.0, 300.0, 3000.0, listOf(BarInfo(301.0, 40.0, 20.0)), false))
        assertNotNull(columnCoordinateIssue(300.0, 400.0, 3000.0, circle(), true))
        assertNotNull(columnCoordinateIssue(Double.POSITIVE_INFINITY, 300.0, 3000.0, rectangle(), false))
    }
}
