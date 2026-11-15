package com.civileg.app.ui.compose.screens

import com.civileg.app.utils.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression tests for the footing drawing dispatch.
 *
 * Bug being guarded: `FootingScreen` mapped STRAP / STRIP / PILE_CAP to the
 * "Isolated" drawing, so three footing types rendered the wrong plan (on screen
 * and in the PDF report). Every enum value must map to its own drawing name.
 */
class FootingDrawingTypeMappingTest {

    @Test
    fun `every footing type maps to its own drawing`() {
        val names = CalculatorEngine.FootingType.entries.map { footingDrawingTypeName(it) }

        assertEquals(
            "Each footing type must have a distinct drawing",
            CalculatorEngine.FootingType.entries.size,
            names.toSet().size
        )
        assertEquals("Isolated", footingDrawingTypeName(CalculatorEngine.FootingType.ISOLATED))
        assertEquals("Combined", footingDrawingTypeName(CalculatorEngine.FootingType.COMBINED))
        assertEquals("Strap", footingDrawingTypeName(CalculatorEngine.FootingType.STRAP))
        assertEquals("Raft", footingDrawingTypeName(CalculatorEngine.FootingType.RAFT))
        assertEquals("Strip", footingDrawingTypeName(CalculatorEngine.FootingType.STRIP))
        assertEquals("Pile Cap", footingDrawingTypeName(CalculatorEngine.FootingType.PILE_CAP))
    }

    @Test
    fun `strap strip and pile cap are not drawn as isolated`() {
        val isolated = footingDrawingTypeName(CalculatorEngine.FootingType.ISOLATED)

        listOf(
            CalculatorEngine.FootingType.STRAP,
            CalculatorEngine.FootingType.STRIP,
            CalculatorEngine.FootingType.PILE_CAP
        ).forEach { type ->
            assertNotEquals(
                "$type must not use the isolated footing drawing",
                isolated,
                footingDrawingTypeName(type)
            )
        }
    }

    @Test
    fun `combined column centres follow the entered spacing`() {
        val (first, second) = combinedColumnPositions(columnWidthMm = 300.0, distanceM = 3.5)

        assertEquals(200.0, first, 1e-9)
        assertEquals(3700.0, second, 1e-9)
    }
}
