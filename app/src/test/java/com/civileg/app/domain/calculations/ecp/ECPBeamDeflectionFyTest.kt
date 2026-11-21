package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.SupportCondition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden guard for CLAUSE_LOG E-01: ECP 203 §6-3-1 modification factor
 * MF = 0.55 + 477 / (fy × ρ%) must use the actual fy, not a hardcoded 360.
 *
 * span 6 m, totalDepth 500 mm, ρ = 0.01 (ρ% = 1.0), simply supported:
 *   actual ratio = 6000/500 = 12; basic ratio = 20
 *   fy=240: MF = 0.55+477/240 = 2.5375 -> capped 2.5  -> allowable 50.0     -> ratio 0.2400
 *   fy=360: MF = 0.55+477/360 = 1.875                 -> allowable 37.5     -> ratio 0.3200
 *   fy=400: MF = 0.55+477/400 = 1.7425                -> allowable 34.85    -> ratio 0.34433
 */
class ECPBeamDeflectionFyTest {

    private val beam = ECPBeam()

    private fun ratio(fy: Double) = beam.checkDeflection(
        span = 6.0, totalDepth = 500.0, reinforcementRatio = 0.01,
        supportCondition = SupportCondition.SIMPLY_SUPPORTED, fy = fy
    ).ratio

    @Test
    fun `fy 240 increases allowable depth`() {
        assertEquals(0.24, ratio(240.0), 1e-3)
    }

    @Test
    fun `fy 360 matches legacy default`() {
        assertEquals(0.32, ratio(360.0), 1e-3)
    }

    @Test
    fun `fy 400 decreases allowable depth`() {
        assertEquals(0.34433, ratio(400.0), 1e-3)
    }

    @Test
    fun `default parameter preserves legacy behavior`() {
        val legacy = beam.checkDeflection(6.0, 500.0, 0.01, SupportCondition.SIMPLY_SUPPORTED)
        assertEquals(ratio(360.0), legacy.ratio, 1e-9)
    }
}
