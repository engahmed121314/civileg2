package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPStaircase — ECP 203-2020 staircase design engine.
 * Covers geometry, loading, flexural design, shear design,
 * InputGuard validation, and edge cases.
 */
class ECPStaircaseTest {

    private lateinit var stair: ECPStaircase

    @Before
    fun setup() {
        stair = ECPStaircase()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun defaultInput(
        stairType: StairType = StairType.STRAIGHT,
        span: Double = 3.0,
        totalRise: Double = 1.8,
        stairWidth: Double = 1.2,
        waistThickness: Double = 200.0,
        fcu: Double = 25.0,
        fy: Double = 360.0,
        deadLoad: Double = 6.0,
        liveLoad: Double = 4.0,
        riserCount: Int = 0,
        going: Double = 0.0
    ) = StaircaseInput(
        stairType, span, totalRise, stairWidth, waistThickness,
        fcu, fy, deadLoad, liveLoad, riserCount, going
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_validInputs_producesResult`() {
        val result = stair.designStaircase(defaultInput())
        assertNotNull(result)
        assertEquals(DesignCode.ECP, result.designCode)
    }

    @Test
    fun `designStaircase_geometryIsReasonable`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Number of risers >= 3", result.numberOfRisers >= 3)
        assertTrue("Number of treads >= 2", result.numberOfTreads >= 2)
        assertTrue("Riser > 0", result.riser > 0)
        assertTrue("Going > 0", result.going > 0)
    }

    @Test
    fun `designStaircase_slopeAngleIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Slope angle > 0", result.slopeAngle > 0)
        assertTrue("Slope angle < 90", result.slopeAngle < 90.0)
    }

    @Test
    fun `designStaircase_inclinedLengthExceedsSpan`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Inclined length > span", result.inclinedLength >= defaultInput().span * 0.99)
    }

    @Test
    fun `designStaircase_momentAndShearArePositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Max moment > 0", result.maxMoment > 0)
        assertTrue("Max shear > 0", result.maxShear > 0)
    }

    @Test
    fun `designStaircase_reactionsArePositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Reaction A > 0", result.reactionA > 0)
        assertTrue("Reaction B > 0", result.reactionB > 0)
    }

    @Test
    fun `designStaircase_mainRebarIsNotEmpty`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Main rebar should be specified", result.mainRebar.isNotEmpty())
    }

    @Test
    fun `designStaircase_mainRebarAreaIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Main rebar area > 0", result.mainRebarArea > 0)
    }

    @Test
    fun `designStaircase_effectiveDepthIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Effective depth > 0", result.effectiveDepth > 0)
    }

    @Test
    fun `designStaircase_shearCapacityIsPositive`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun `designStaircase_safetyChecksExist`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `designStaircase_codeNotesExist`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Dog-leg stair
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_dogLeg_reducesMoment`() {
        val straight = stair.designStaircase(defaultInput(stairType = StairType.STRAIGHT))
        val dogLeg = stair.designStaircase(defaultInput(stairType = StairType.DOG_LEG))
        // Dog-leg uses M = wL²/10 vs wL²/8 for simply supported
        assertTrue("Dog-leg moment should be less than or equal to straight",
            dogLeg.maxMoment <= straight.maxMoment * 1.01) // allow small tolerance
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. User-specified geometry
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_specifiedRiserCount_respectsIt`() {
        val result = stair.designStaircase(defaultInput(riserCount = 10))
        assertEquals(10, result.numberOfRisers)
    }

    @Test
    fun `designStaircase_specifiedGoing_respectsIt`() {
        val result = stair.designStaircase(defaultInput(going = 280.0))
        // Going should be approximately 280 (may be adjusted)
        assertTrue("Going should be close to specified", result.going in 250.0..300.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroFcu`() {
        stair.designStaircase(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForNegativeFcu`() {
        stair.designStaircase(defaultInput(fcu = -25.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroFy`() {
        stair.designStaircase(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroTotalRise`() {
        stair.designStaircase(defaultInput(totalRise = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroSpan`() {
        stair.designStaircase(defaultInput(span = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroDeadLoad`() {
        stair.designStaircase(defaultInput(deadLoad = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroLiveLoad`() {
        stair.designStaircase(defaultInput(liveLoad = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designStaircase_throwsForZeroStairWidth`() {
        stair.designStaircase(defaultInput(stairWidth = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designStaircase_veryShortSpan_producesResult`() {
        val result = stair.designStaircase(defaultInput(span = 1.5, totalRise = 1.0))
        assertTrue("Should handle short span", result.numberOfRisers > 0)
    }

    @Test
    fun `designStaircase_thickWaist_isSafe`() {
        val result = stair.designStaircase(defaultInput(waistThickness = 300.0))
        // A thick waist slab should be safe for flexure
        val flexCheck = result.safetyChecks.find { it.name.contains("Flexure") || it.name.contains("K") }
        if (flexCheck != null) {
            assertTrue("Thick waist should pass flexure", flexCheck.isSafe)
        }
    }

    @Test
    fun `designStaircase_comfortCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val comfortCheck = result.safetyChecks.find { it.name.contains("2R") }
        assertNotNull("Should have 2R+G comfort check", comfortCheck)
    }

    @Test
    fun `designStaircase_riserHeightCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val riserCheck = result.safetyChecks.find { it.name.contains("Riser") }
        assertNotNull("Should have riser height check", riserCheck)
    }

    @Test
    fun `designStaircase_goingCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val goingCheck = result.safetyChecks.find { it.name.contains("Going") }
        assertNotNull("Should have going check", goingCheck)
    }

    @Test
    fun `designStaircase_deflectionCheckExists`() {
        val result = stair.designStaircase(defaultInput())
        val deflectionCheck = result.safetyChecks.find { it.name.contains("Deflection") }
        assertNotNull("Should have deflection check", deflectionCheck)
    }

    @Test
    fun `ECP minimum main steel is assessed per 6-2-1-3-2 and the issued steel meets it`() {
        for (fy in listOf(240.0, 360.0, 400.0)) {
            val result = stair.designStaircase(defaultInput(fy = fy))
            val row = result.safetyChecks.single { it.name.startsWith("Minimum main steel") }
            assertFalse("fy=$fy must be assessed, not flagged", row.notAssessed)
            assertTrue("fy=$fy provided >= required", row.isSafe)
            assertTrue("fy=$fy provided >= limit", row.value >= row.limit - 1e-6)
        }
    }

    @Test
    fun `ECP minimum main steel ratio follows the slab clause max of 0-6 over fy and class floor`() {
        // fy=360, fcu=25: max(0.6/360, 0.0015) = 0.001667 of b*d
        val r = stair.designStaircase(defaultInput(fy = 360.0, fcu = 25.0))
        val row = r.safetyChecks.single { it.name.startsWith("Minimum main steel") }
        assertEquals(maxOf(0.6 / 360.0, 0.0015) * 1000.0 * r.effectiveDepth, row.limit, 1e-6)
        // mild steel fy=240: floor 0.25% (= 0.6/240 anyway)
        val mild = stair.designStaircase(defaultInput(fy = 240.0, fcu = 25.0))
        val mildRow = mild.safetyChecks.single { it.name.startsWith("Minimum main steel") }
        assertEquals(0.0025 * 1000.0 * mild.effectiveDepth, mildRow.limit, 1e-6)
        // high-strength with fcu >= 40: floor 0.18%
        val strong = stair.designStaircase(defaultInput(fy = 400.0, fcu = 45.0))
        val strongRow = strong.safetyChecks.single { it.name.startsWith("Minimum main steel") }
        assertEquals(maxOf(0.6 / 400.0, 0.0018) * 1000.0 * strong.effectiveDepth, strongRow.limit, 1e-6)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. ECP 203-2025 Table 4-1 flexure cap (was unconservative K_bal)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `flexure limit is Table 4-1 Rmax over gamma_c, not the old balanced K`() {
        // fy=420: Rmax = 0.186 (no redistribution) → K_max = 0.186/1.5 = 0.124
        val row = stair.designStaircase(defaultInput(fy = 420.0))
            .safetyChecks.single { it.name.startsWith("Flexure") }
        assertEquals(0.186 / 1.5, row.limit, 1e-9)
        // fy=360: interpolated between 350 (0.196) and 400 (0.189) → 0.1946
        val row360 = stair.designStaircase(defaultInput(fy = 360.0))
            .safetyChecks.single { it.name.startsWith("Flexure") }
        assertEquals((0.196 + (0.189 - 0.196) * 10.0 / 50.0) / 1.5, row360.limit, 1e-9)
    }

    @Test
    fun `section between old K_bal and the Table 4-1 cap now fails flexure`() {
        // span 5.3 m, waist 150 mm, fy=420, fcu=25 gives K ≈ 0.15:
        // old K_bal ≈ 0.180 passed it; Table 4-1 caps K at 0.124.
        val result = stair.designStaircase(
            defaultInput(span = 5.3, totalRise = 1.8, waistThickness = 150.0, fy = 420.0)
        )
        val row = result.safetyChecks.single { it.name.startsWith("Flexure") }
        assertTrue("K should sit in the previously-passing band: ${row.value}", row.value in 0.13..0.18)
        assertTrue("K exceeds the Table 4-1 cap", row.value > row.limit)
        assertFalse("must fail flexure", row.isSafe)
        assertFalse("result not safe", result.isSafe)
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Support top steel (Eq. 6-2 + top mesh for t > 160 mm)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `support top steel is designed and meets Eq 6-2 plus the top mesh floor`() {
        // waist 200 mm > 160 mm → mesh floor max(20% main, 5Ø8/m = 251 mm²/m)
        val result = stair.designStaircase(defaultInput(waistThickness = 200.0))
        assertTrue("flightTopRebar filled", result.flightTopRebar.isNotEmpty())
        val row = result.safetyChecks.single { it.name.startsWith("Support top steel") }
        assertTrue(row.isSafe)
        assertTrue("top steel >= 5Ø8/m", row.value >= 251.0 - 1e-6)
    }

    @Test
    fun `support top steel exists for a thin waist governed by Eq 6-2 only`() {
        val result = stair.designStaircase(defaultInput(waistThickness = 140.0))
        assertTrue(result.flightTopRebar.isNotEmpty())
        val row = result.safetyChecks.single { it.name.startsWith("Support top steel") }
        assertTrue(row.isSafe)
        assertTrue(row.value >= row.limit - 1e-6)
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Distribution steel: 20% of main, ≥ 4 bars/m (spacing ≤ 250)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `distribution steel meets 20 percent of main and the 4 bars per metre rule`() {
        val result = stair.designStaircase(defaultInput())
        assertTrue("20% of main", result.distributionRebarArea >= 0.2 * result.mainRebarArea - 1e-6)
        val spacing = Regex("@ (\\d+)").find(result.distributionRebar)!!.groupValues[1].toInt()
        assertTrue("spacing <= 250 mm (≥ 4 bars/m): $spacing", spacing <= 250)
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. Thickness minimum a/35 (Eq. 6-6a), analysis-span consistency
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `waist minimum thickness follows Eq 6-6a a over 35`() {
        val row = stair.designStaircase(defaultInput(span = 3.5))
            .safetyChecks.single { it.name.startsWith("Waist >=") }
        assertEquals(3500.0 / 35.0, row.limit, 1e-6)
    }

    @Test
    fun `going follows the entered span when only the riser count is given`() {
        val r = stair.designStaircase(defaultInput(span = 3.0, riserCount = 10))
        assertEquals(3000.0 / 9.0, r.going, 1e-6)
        assertEquals(3000.0, r.adoptedRunMm, 1e-6)
    }

    @Test
    fun `analysis span is the larger of entered span and adopted run`() {
        val wider = stair.designStaircase(defaultInput(span = 3.05, riserCount = 11, going = 300.0))
        assertEquals(3.05, wider.analysisSpanM, 1e-9)
        val narrower = stair.designStaircase(defaultInput(span = 2.9, riserCount = 11, going = 300.0))
        assertEquals(3.0, narrower.analysisSpanM, 1e-9)
        assertEquals(3000.0, narrower.adoptedRunMm, 1e-9)
        assertTrue(narrower.codeNotes.any { it.contains("Adopted discrete run") })
    }
}
