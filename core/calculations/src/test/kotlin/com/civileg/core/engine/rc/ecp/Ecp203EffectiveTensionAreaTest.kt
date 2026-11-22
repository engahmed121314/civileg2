package com.civileg.core.engine.rc.ecp

import org.junit.Assert.*
import org.junit.Test

class Ecp203EffectiveTensionAreaTest {
    @Test fun `beam uses actual centroid cover not cover to the outside of links`() {
        val value = Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, 350.0, 25.0 + 8.0 + 20.0 / 2.0)
        assertEquals(107.5, value.effectiveDepthPerFaceMm, 0.0)
        assertEquals(21500.0, value.effectiveTotalAreaMm2, 0.0)
        assertEquals(1, value.reinforcedFaces)
        assertEquals(Ecp203EffectiveTensionArea.Case.RECTANGULAR_BEAM_BAND, value.case)
        assertTrue(value.source.contains("Fig4-24(a)"))
    }

    @Test fun `beam accepts the supplied area weighted centroid of multiple layers`() {
        val centroid = (3.0 * 43.0 + 93.0) / 4.0
        val value = Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, 350.0, centroid)
        assertEquals(138.75, value.effectiveDepthPerFaceMm, 0.0)
        assertEquals(27750.0, value.effectiveTotalAreaMm2, 0.0)
    }

    @Test fun `beam does not import a slab one third depth cap`() {
        val value = Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, 350.0, 60.0)
        assertEquals(150.0, value.effectiveDepthPerFaceMm, 0.0)
        assertTrue(value.effectiveDepthPerFaceMm > 350.0 / 3.0)
    }

    @Test fun `beam band may meet but not exceed its full constant width section`() {
        assertEquals(60000.0, Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, 300.0, 120.0)
            .effectiveTotalAreaMm2, 0.0)
    }

    @Test fun `slab cover and individual diameter branch is literal`() {
        val value = Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 300.0, 20.0, 12.0, 60.0)
        assertEquals(65.0, value.effectiveDepthPerFaceMm, 0.0)
        assertEquals(65000.0, value.effectiveTotalAreaMm2, 0.0)
        assertEquals(Ecp203EffectiveTensionArea.Case.SOLID_SLAB_SINGLE_LAYER, value.case)
        assertTrue(value.source.contains("Fig4-24(b)"))
    }

    @Test fun `slab neutral axis cap uses total depth minus cracked compression depth`() {
        val value = Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 20.0, 12.0, 50.0)
        assertEquals(50.0, value.effectiveDepthPerFaceMm, 0.0)
        assertEquals(50000.0, value.effectiveTotalAreaMm2, 0.0)
    }

    @Test fun `slab does not add an unprinted minimum depth when its cap controls`() {
        val value = Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 100.0, 20.0, 12.0, 40.0)
        assertEquals(20.0, value.effectiveDepthPerFaceMm, 0.0)
    }

    @Test fun `slab branches meet at the same area`() {
        val value = Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 250.0, 20.0, 12.0, 55.0)
        assertEquals(65.0, value.effectiveDepthPerFaceMm, 0.0)
    }

    @Test fun `zero cover is a geometry input not a cover compliance verdict`() {
        val value = Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 0.0, 16.0, 40.0)
        assertEquals(20.0, value.effectiveDepthPerFaceMm, 0.0)
    }

    @Test fun `equal axial face bands are capped at half thickness without double counting`() {
        val value = Ecp203EffectiveTensionArea.axialTensionEqualFaces(1000.0, 100.0, 20.0, 12.0)
        assertEquals(50.0, value.effectiveDepthPerFaceMm, 0.0)
        assertEquals(50000.0, value.effectiveAreaPerFaceMm2, 0.0)
        assertEquals(100000.0, value.effectiveTotalAreaMm2, 0.0)
        assertEquals(2, value.reinforcedFaces)
        assertTrue(value.source.contains("Fig4-24(c)"))
    }

    @Test fun `axial area reports both faces and preserves equal face reinforcement ratio`() {
        val value = Ecp203EffectiveTensionArea.axialTensionEqualFaces(1000.0, 300.0, 20.0, 16.0)
        assertEquals(70.0, value.effectiveDepthPerFaceMm, 0.0)
        assertEquals(140000.0, value.effectiveTotalAreaMm2, 0.0)
        assertEquals(500.0 / value.effectiveAreaPerFaceMm2, 1000.0 / value.effectiveTotalAreaMm2, 0.0)
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid or out-of-scope geometry was accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `beam rejects nonfinite nonpositive and overflowing bands rather than clipping`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            rejects { Ecp203EffectiveTensionArea.rectangularBeamBand(invalid, 350.0, 43.0) }
            rejects { Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, invalid, 43.0) }
            rejects { Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, 350.0, invalid) }
        }
        rejects { Ecp203EffectiveTensionArea.rectangularBeamBand(200.0, 300.0, 121.0) }
        rejects { Ecp203EffectiveTensionArea.rectangularBeamBand(Double.MAX_VALUE, 350.0, 43.0) }
    }

    @Test fun `slab requires real tension reinforcement and a valid cracked neutral axis`() {
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, -1.0, 12.0, 50.0) }
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 20.0, 0.0, 50.0) }
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 20.0, 12.0, 174.0) }
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 20.0, 12.0, 0.0) }
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 190.0, 12.0, 5.0) }
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, Double.NaN, 12.0, 50.0) }
        rejects { Ecp203EffectiveTensionArea.solidSlabSingleLayer(1000.0, 200.0, 20.0, 12.0, Double.POSITIVE_INFINITY) }
    }

    @Test fun `axial opposing bar layers must fit in the actual thickness`() {
        rejects { Ecp203EffectiveTensionArea.axialTensionEqualFaces(1000.0, 60.0, 20.0, 12.0) }
        rejects { Ecp203EffectiveTensionArea.axialTensionEqualFaces(1000.0, 100.0, -1.0, 12.0) }
        rejects { Ecp203EffectiveTensionArea.axialTensionEqualFaces(1000.0, 100.0, 20.0, Double.NaN) }
        rejects { Ecp203EffectiveTensionArea.axialTensionEqualFaces(0.0, 100.0, 20.0, 12.0) }
        rejects { Ecp203EffectiveTensionArea.axialTensionEqualFaces(Double.MAX_VALUE, 100.0, 20.0, 12.0) }
    }
}
