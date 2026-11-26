package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

/** ECP 203-2025 checks for the solid-slab engine (hand values, page-cited). */
class ECPSlab2025Test {
    private val slab = ECPSlab()

    @Test
    fun `oneWay_minThickness_6_2_1_2_1`() {
        // L/30 simple, L/35 continuous, floor 80 mm (p.6-4)
        assertEquals(200.0, slab.getMinSlabThickness(6000.0, SupportCondition.SIMPLY_SUPPORTED), 1e-6)
        assertEquals(6000.0 / 35.0, slab.getMinSlabThickness(6000.0, SupportCondition.CONTINUOUS), 1e-6)
        assertEquals(80.0, slab.getMinSlabThickness(1000.0, SupportCondition.SIMPLY_SUPPORTED), 1e-6)
    }

    @Test
    fun `oneWay_minSteel_isRatioOnGrossArea`() {
        // fy 400, fcu 30: max(0.6/400 = 0.0015, 0.0015) x 1000 x 150 = 225 mm2/m. Tiny moment -> min steel governs.
        val r = slab.designOneWaySlab(30.0, 400.0, 150.0, 3000.0, 1.0, 5.0, LoadCombination.DEAD_LIVE)
        assertEquals(225.0, r.requiredReinforcement, 0.5)
    }

    @Test
    fun `oneWay_kLimit_usesTable41_notOldBalancedK`() {
        // fy 420: Kmax ~ 0.124 (Rmax 0.186 / 1.5). d = 150 - 20 - 6 = 124. K = Mu/(fcu b d^2) = 0.15 -> Mu = 0.15 x 30 x 1000 x 124^2 = 69.2 kN.m
        val r = slab.designOneWaySlab(30.0, 420.0, 150.0, 3000.0, 69.2, 5.0, LoadCombination.DEAD_LIVE)
        assertFalse("K above Kmax must not be safe", r.isSafe)
        assertTrue(r.warnings.any { it.contains("Kmax") })
    }
}
