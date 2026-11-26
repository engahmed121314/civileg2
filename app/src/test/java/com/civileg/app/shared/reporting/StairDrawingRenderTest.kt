package com.civileg.app.shared.reporting

import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Generator-only fixture (not a Compose capture, not a PDF page, not a device render).
 * Renders PdfDrawingGenerator.generateStairDrawing for one case, with the geometry
 * StairViewModel.exportToPdf passes: span 3.0 m, riser 170 mm, tread 300 mm
 * -> 10 treads, 11 risers, 1870 mm rise, 3000 mm run (CalculatorEngine.designStaircase).
 * The "before" case reproduces the earlier call (height in metres, one riser).
 * PNGs: <java.io.tmpdir>/stair-drawing-fixtures
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StairDrawingRenderTest {
    private val outDir = File(System.getProperty("java.io.tmpdir"), "stair-drawing-fixtures").also { it.mkdirs() }

    private fun draw(h: Double, len: Double) = PdfDrawingGenerator.generateStairDrawing(
        totalHeight = h, totalLength = len, stairWidth = 1200.0,
        riserHeight = 170.0, treadWidth = 300.0, slabThickness = 150.0,
        mainDia = 12.0, mainSpacing = 150.0, distDia = 8.0, distSpacing = 200.0, cover = 25.0
    )

    private fun save(name: String, b: Bitmap) =
        File(outDir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun rendersAdoptedGeometryInMillimetres() {
        val b = draw(1870.0, 3000.0)
        save("stair_after_mm", b)
        assertTrue(b.width > 0 && b.height > 0)
    }

    @Test
    fun rendersPreviousGeometryForComparison() {
        val b = draw(1.87, 300.0)
        save("stair_before_metres_one_riser", b)
        assertTrue(b.width > 0 && b.height > 0)
    }
}
