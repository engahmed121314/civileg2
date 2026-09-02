package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deflection screening gates — hand-derived:
 *  ECP: basic 20, MF = 0.55+477/(fy·ρ%) ; span 6 m, h=560, ρ%=1.2566 (As=1570.8/125000)
 *       MF = 0.55 + 477/(360·1.2566) = 1.6043 -> allowable 32.09, actual 10.714, util 0.334
 *  ACI: Table 24.2.2 SS=16, MF = 0.4+fy/700 (=1.0 @420) ; span 6 m, h=600 -> util 0.625
 */
class UnifiedBeamDeflectionGoldenTest {

    private val steel360 = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val steel420 = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 620.0)

    @Test
    fun `ECP screening with actual fy and honest NOT_CHECKED for computed deflection`() {
        val r = UnifiedBeamDeflection(Ecp203Params, steel360).screen(
            spanM = 6.0, totalDepthMm = 560.0, tensionRatioPercent = 100.0 * 1570.8 / (250.0 * 500.0),
            support = SupportCondition.SIMPLY_SUPPORTED
        )
        assertEquals(32.09, r.allowableRatio, 0.05)     // 20 × 1.6043
        assertEquals(10.714, r.actualRatio, 0.01)
        assertEquals(0.334, r.utilization, 0.005)
        assertTrue(r.isSafe)
        // spec §62: the computed-deflection layer never ran — overall must be NOT_CHECKED, NOT PASS
        assertEquals(CheckStatus.NOT_CHECKED, r.overall)
        assertEquals(1, r.trace.count(CheckStatus.NOT_CHECKED))
    }

    @Test
    fun `ECP supplied analysis upgrades the trace to PASS`() {
        val r = UnifiedBeamDeflection(Ecp203Params, steel360).screen(
            spanM = 6.0, totalDepthMm = 560.0, tensionRatioPercent = 1.2566,
            support = SupportCondition.SIMPLY_SUPPORTED,
            computedDeflectionMm = 18.0                  // ≤ L/250 = 24 mm
        )
        assertEquals(CheckStatus.PASS, r.overall)
        assertEquals(0, r.trace.count(CheckStatus.NOT_CHECKED))
    }

    @Test
    fun `ACI table values and fy footnote factor`() {
        val r = UnifiedBeamDeflection(Aci318Params, steel420).screen(
            spanM = 6.0, totalDepthMm = 600.0, tensionRatioPercent = 1.0,
            support = SupportCondition.SIMPLY_SUPPORTED
        )
        // ACI SS=16 ; MF=0.4+420/700=1.0 exactly at Grade-420
        assertEquals(16.0, r.allowableRatio, 1e-9)
        assertEquals(0.625, r.utilization, 0.001)
        assertTrue(r.isSafe)

        val lowFy = UnifiedBeamDeflection(Aci318Params, SteelMaterial(yieldMpa = 280.0, ultimateMpa = 430.0))
            .screen(spanM = 6.0, totalDepthMm = 600.0, tensionRatioPercent = 1.0,
                support = SupportCondition.SIMPLY_SUPPORTED)
        // MF = 0.4+280/700 = 0.8 -> allowable 12.8 < 16 : lower fy tightens the ratio
        assertEquals(12.8, lowFy.allowableRatio, 0.01)
    }

    @Test
    fun `ACI thin section fails screening and outranks NOT_CHECKED`() {
        val r = UnifiedBeamDeflection(Aci318Params, steel420).screen(
            spanM = 6.0, totalDepthMm = 200.0, tensionRatioPercent = 1.0,
            support = SupportCondition.SIMPLY_SUPPORTED
        )
        assertEquals(30.0, r.actualRatio, 1e-9)          // > 16
        assertFalse(r.isSafe)
        assertEquals(CheckStatus.FAIL, r.overall)         // FAIL outranks NOT_CHECKED
    }
}
