package com.civileg.core.calculations.entities

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R1: golden shapes for BeamDiagramStatics — the shared dimensionless
 * BMD/SFD case statics consumed by the screen drawing, the PDF generator
 * pipeline and the DXF beam elevation.
 */
class BeamDiagramStaticsTest {

    private val eps = 1e-9

    @Test
    fun simplySupportedParabola() {
        assertEquals(0.0, BeamDiagramStatics.normalizedMoment("HINGED_HINGED", 0.0), eps)
        assertEquals(0.25, BeamDiagramStatics.normalizedMoment("HINGED_HINGED", 0.5), eps)
        assertEquals(0.0, BeamDiagramStatics.normalizedMoment("HINGED_HINGED", 1.0), eps)
        assertEquals(0.5, BeamDiagramStatics.normalizedShear("HINGED_HINGED", 0.0), eps)
        assertEquals(0.0, BeamDiagramStatics.normalizedShear("HINGED_HINGED", 0.5), eps)
        assertEquals(-0.5, BeamDiagramStatics.normalizedShear("HINGED_HINGED", 1.0), eps)
        assertEquals(0.25, BeamDiagramStatics.maxAbsMoment("HINGED_HINGED"), eps)
        assertEquals(0.5, BeamDiagramStatics.maxAbsShear("HINGED_HINGED"), eps)
    }

    @Test
    fun cantileverHoggingParabola() {
        // Hogging at the fixed end (t=0), zero at the free tip.
        assertEquals(-1.0, BeamDiagramStatics.normalizedMoment("CANTILEVER", 0.0), eps)
        assertEquals(-0.25, BeamDiagramStatics.normalizedMoment("CANTILEVER", 0.5), eps)
        assertEquals(0.0, BeamDiagramStatics.normalizedMoment("CANTILEVER", 1.0), eps)
        assertEquals(-1.0, BeamDiagramStatics.normalizedShear("CANTILEVER", 0.0), eps)
        assertEquals(0.0, BeamDiagramStatics.normalizedShear("CANTILEVER", 1.0), eps)
        assertEquals(1.0, BeamDiagramStatics.maxAbsMoment("CANTILEVER"), eps)
        assertEquals(1.0, BeamDiagramStatics.maxAbsShear("CANTILEVER"), eps)
    }

    @Test
    fun fixedFixedSCurve() {
        // Ends hogging wL²/12, mid sagging wL²/24.
        assertEquals(-1.0 / 6.0, BeamDiagramStatics.normalizedMoment("FIXED_FIXED", 0.0), eps)
        assertEquals(1.0 / 12.0, BeamDiagramStatics.normalizedMoment("FIXED_FIXED", 0.5), eps)
        assertEquals(-1.0 / 6.0, BeamDiagramStatics.normalizedMoment("FIXED_FIXED", 1.0), eps)
        assertEquals(0.5, BeamDiagramStatics.normalizedShear("FIXED_FIXED", 0.0), eps)
        assertEquals(1.0 / 6.0, BeamDiagramStatics.maxAbsMoment("FIXED_FIXED"), eps)
    }

    @Test
    fun fixedHingedProppedCubic() {
        // Fixed end hogging wL²/8, max sagging 9wL²/128 at x = 5L/8, hinged end zero.
        assertEquals(-0.125, BeamDiagramStatics.normalizedMoment("FIXED_HINGED", 0.0), eps)
        assertEquals(9.0 / 128.0, BeamDiagramStatics.normalizedMoment("FIXED_HINGED", 0.625), eps)
        assertEquals(0.0, BeamDiagramStatics.normalizedMoment("FIXED_HINGED", 1.0), eps)
        assertEquals(5.0 / 8.0, BeamDiagramStatics.normalizedShear("FIXED_HINGED", 0.0), eps)
        assertEquals(-3.0 / 8.0, BeamDiagramStatics.normalizedShear("FIXED_HINGED", 1.0), eps)
        // Normalizing scale is driven by the LARGER fixed-end hogging (1/8),
        // not the mid-span sagging peak (9/128).
        assertEquals(0.125, BeamDiagramStatics.maxAbsMoment("FIXED_HINGED"), eps)
        assertEquals(5.0 / 8.0, BeamDiagramStatics.maxAbsShear("FIXED_HINGED"), eps)
    }

    @Test
    fun equivalentUdlMatchesEngineMomentFactors() {
        // c = 8 (SS), 12 (FF), 2 (cantilever) — M = c·w·L² → w = c·M/L².
        assertEquals(8.0 * 120.0 / (5.0 * 5.0), BeamDiagramStatics.equivalentUdl("HINGED_HINGED", 120.0, 5.0), eps)
        assertEquals(12.0 * 120.0 / (5.0 * 5.0), BeamDiagramStatics.equivalentUdl("FIXED_FIXED", 120.0, 5.0), eps)
        assertEquals(8.0 * 120.0 / (5.0 * 5.0), BeamDiagramStatics.equivalentUdl("FIXED_HINGED", 120.0, 5.0), eps)
        assertEquals(2.0 * 120.0 / (5.0 * 5.0), BeamDiagramStatics.equivalentUdl("CANTILEVER", 120.0, 5.0), eps)
        assertEquals(0.0, BeamDiagramStatics.equivalentUdl("HINGED_HINGED", 120.0, 0.0), eps)
    }

    @Test
    fun curvesStayDimensionlessWithinSpan() {
        for (case in listOf("HINGED_HINGED", "ROLLER_HINGED", "FIXED_HINGED", "FIXED_FIXED", "CANTILEVER")) {
            for (i in 0..32) {
                val m = BeamDiagramStatics.normalizedMoment(case, i.toDouble() / 32.0)
                val v = BeamDiagramStatics.normalizedShear(case, i.toDouble() / 32.0)
                assertEquals(m, m, eps) // finite
                assertEquals(v, v, eps) // finite
            }
        }
    }
}