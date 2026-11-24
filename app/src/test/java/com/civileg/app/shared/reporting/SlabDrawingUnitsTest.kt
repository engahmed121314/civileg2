package com.civileg.app.shared.reporting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SlabDrawingUnitsTest {
    @Test
    fun metresAreConvertedToMillimetres() {
        assertEquals(6000.0, SlabDrawingUnits.spanToMm(6.0, spanIsMm = false), 1e-9)
    }

    @Test
    fun millimetresAreKeptUnchanged() {
        assertEquals(6000.0, SlabDrawingUnits.spanToMm(6000.0, spanIsMm = true), 1e-9)
    }

    @Test
    fun sameSpanInEitherUnitGivesTheSameDrawingSpan() {
        assertEquals(
            SlabDrawingUnits.spanToMm(4.5, spanIsMm = false),
            SlabDrawingUnits.spanToMm(4500.0, spanIsMm = true),
            1e-9
        )
    }

    @Test
    fun invalidSpansAreRejectedNotDefaulted() {
        assertThrows(IllegalArgumentException::class.java) { SlabDrawingUnits.spanToMm(0.0, true) }
        assertThrows(IllegalArgumentException::class.java) { SlabDrawingUnits.spanToMm(-1.0, false) }
        assertThrows(IllegalArgumentException::class.java) { SlabDrawingUnits.spanToMm(Double.NaN, true) }
    }
}
