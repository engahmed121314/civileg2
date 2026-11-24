package com.civileg.app.shared.reporting

import android.graphics.Bitmap
import android.graphics.Color
import com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the slab fallback drawings and checks the unit contract of
 * PdfDrawingGenerator.generateSlabDrawingByType: the same physical span given in
 * metres (spansInMm = false) or millimetres (spansInMm = true) must produce the
 * same picture. Each bitmap is also written as a PNG so the pixels can be
 * inspected by a person; the test does not rely only on "no exception".
 *
 * Output directory: <java.io.tmpdir>/slab-drawing-fixtures
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlabDrawingRenderTest {

    private val outDir = File(System.getProperty("java.io.tmpdir"), "slab-drawing-fixtures").also { it.mkdirs() }

    private fun render(type: SlabType, spansInMm: Boolean): Bitmap {
        val lx = if (spansInMm) 6000.0 else 6.0
        val ly = if (spansInMm) 5000.0 else 5.0
        return PdfDrawingGenerator.generateSlabDrawingByType(
            slabType = type,
            spanX = lx, spanY = ly, thickness = 200.0,
            mainDia = 12.0, mainSpacing = 150.0,
            distDia = 10.0, distSpacing = 200.0,
            cover = 25.0,
            dropPanelSize = 2000.0,
            dropPanelThickness = 100.0,
            ribWidth = 100.0, ribSpacing = 600.0,
            columnSize = 400.0,
            spansInMm = spansInMm
        )
    }

    private fun save(name: String, bitmap: Bitmap) {
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun nonBackgroundFraction(bitmap: Bitmap): Double {
        val background = bitmap.getPixel(0, 0)
        var different = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
            if (bitmap.getPixel(x, y) != background) different++
        }
        return different.toDouble() / ((bitmap.width / 4) * (bitmap.height / 4))
    }

    private fun checkEquivalent(type: SlabType) {
        val metres = render(type, spansInMm = false)
        val millimetres = render(type, spansInMm = true)
        save("${type.name}_metres", metres)
        save("${type.name}_mm", millimetres)
        assertEquals(metres.width, millimetres.width)
        assertEquals(metres.height, millimetres.height)
        assertTrue("drawing must not be blank", nonBackgroundFraction(millimetres) > 0.02)
        assertTrue("metre and millimetre input must render identically for $type", metres.sameAs(millimetres))
    }

    @Test fun flatSlabMetresEqualMillimetres() = checkEquivalent(SlabType.FLAT)

    @Test fun hollowBlockMetresEqualMillimetres() = checkEquivalent(SlabType.HOLLOW_BLOCK)

    @Test fun waffleMetresEqualMillimetres() = checkEquivalent(SlabType.WAFFLE)
}
