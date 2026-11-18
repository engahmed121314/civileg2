package com.civileg.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG math used by the legibility guards (R13, §12.2(c)).
 */
class ContrastTest {

    @Test
    fun blackOnWhiteIs21() {
        assertEquals(21.0f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun identicalColorsAre1() {
        assertEquals(1.0f, contrastRatio(Color(0xFF0B192C), Color(0xFF0B192C)), 0.001f)
    }

    @Test
    fun onColorForWhiteBackgroundIsDark() {
        assertEquals(Color(0xFF0B192C), onColorFor(Color.White))
    }

    @Test
    fun onColorForDarkBackgroundIsLight() {
        assertEquals(Color.White, onColorFor(Color(0xFF0B0F19)))
    }

    @Test
    fun onColorForAlwaysMeetsTextThreshold() {
        val backgrounds = listOf(
            Color(0xFFFFFFFF), Color(0xFFF4F6F9), Color(0xFF0B0F19), Color(0xFF131B2E),
            Color(0xFF00A8E8), Color(0xFF0B192C), Color(0xFF161A30), Color(0xFF7DD3FC)
        )
        backgrounds.forEach { bg ->
            val fg = onColorFor(bg)
            assertTrue("onColorFor($bg) = $fg", contrastRatio(fg, bg) >= 4.5f)
        }
    }
}
