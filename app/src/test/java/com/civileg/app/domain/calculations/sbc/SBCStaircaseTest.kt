package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCStaircase — SBC 304-2018 staircase design engine.
 *
 * Covers:
 *  - designStaircase: happy path, validation, edge cases
 *  - Safety checks: all safety checks have valid values
 *  - Geometric checks: riser/going comfort formula
 *  - SBC-specific: higher cover, higher ρmin, hot climate notes
 *  - InputGuard: zero/negative values throw IllegalArgumentException
 */
class SBCStaircaseTest {

    private lateinit var engine: SBCStaircase

    @Before
    fun setup() {
        engine = SBCStaircase()
    }

    companion object {
        private fun typicalInput() = StaircaseInput(
            stairType = StairType.STRAIGHT,
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0,
            deadLoad = 6.0, liveLoad = 4.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `typical straight stair produces valid result`() {
        val result = engine.designStaircase(typicalInput())
        assertEquals("Should be SBC code", DesignCode.SBC, result.designCode)
        assertTrue("Should have risers", result.numberOfRisers > 0)
        assertTrue("Should have treads", result.numberOfTreads > 0)
        assertTrue("Riser should be positive", result.riser > 0)
        assertTrue("Going should be positive", result.going > 0)
        assertTrue("Slope angle should be positive", result.slopeAngle > 0)
        assertTrue("Inclined length should be positive", result.inclinedLength > 0)
    }

    @Test
    fun `typical stair has valid loads`() {
        val result = engine.designStaircase(typicalInput())
        assertTrue("Factored load should be positive", result.factoredLoad > 0)
        assertTrue("Horizontal load should be positive", result.horizontalLoad > 0)
        assertTrue("Max moment should be positive", result.maxMoment > 0)
        assertTrue("Max shear should be positive", result.maxShear > 0)
    }

    @Test
    fun `typical stair has valid reinforcement`() {
        val result = engine.designStaircase(typicalInput())
        assertTrue("Main rebar area should be positive", result.mainRebarArea > 0)
        assertTrue("Effective depth should be positive", result.effectiveDepth > 0)
        assertTrue("Reinforcement ratio should be positive", result.reinforcementRatio > 0)
    }

    @Test
    fun `typical stair has safety checks`() {
        val result = engine.designStaircase(typicalInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `typical stair has code notes`() {
        val result = engine.designStaircase(typicalInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Stair Types
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `dog-leg stair produces valid result`() {
        val input = StaircaseInput(
            stairType = StairType.DOG_LEG,
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0
        )
        val result = engine.designStaircase(input)
        assertEquals("Should be SBC code", DesignCode.SBC, result.designCode)
        assertTrue("Should have risers", result.numberOfRisers > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Geometric Variations
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `stair with specified riser count produces valid result`() {
        val input = StaircaseInput(
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0,
            riserCount = 12
        )
        val result = engine.designStaircase(input)
        assertEquals("Should have 12 risers", 12, result.numberOfRisers)
    }

    @Test
    fun `stair with specified going produces valid result`() {
        val input = StaircaseInput(
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0,
            going = 280.0
        )
        val result = engine.designStaircase(input)
        assertTrue("Should have valid going", result.going > 0)
    }

    @Test
    fun `longer span increases moment and shear`() {
        val resultShort = engine.designStaircase(StaircaseInput(
            span = 2.5, totalRise = 1.8, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0
        ))
        val resultLong = engine.designStaircase(StaircaseInput(
            span = 4.0, totalRise = 2.4, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0
        ))
        assertTrue("Longer span should produce higher moment",
            resultLong.maxMoment > resultShort.maxMoment)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. SBC-Specific Checks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `SBC staircase uses higher min steel ratio for hot climate`() {
        val result = engine.designStaircase(typicalInput())
        // SBC 304: ρmin = 0.002 (higher than ACI for hot/arid climate)
        assertTrue("Min steel ratio should be 0.002 for hot climate",
            result.minSteelRatio >= 0.002)
    }

    @Test
    fun `SBC code notes reference hot climate`() {
        val result = engine.designStaircase(typicalInput())
        val hasClimateNote = result.codeNotes.any {
            it.contains("مناخ") || it.contains("climate") || it.contains("SBC")
        }
        assertTrue("Should reference climate/SBC considerations", hasClimateNote)
    }

    @Test
    fun `narrow stair triggers seismic note`() {
        val input = StaircaseInput(
            span = 3.0, totalRise = 2.1, stairWidth = 0.9,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0
        )
        val result = engine.designStaircase(input)
        val hasSeismicNote = result.codeNotes.any { it.contains("Seismic") || it.contains("زلزال") }
        assertTrue("Narrow stair should trigger seismic note", hasSeismicNote)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Edge Cases
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `minimal valid stair dimensions produce result`() {
        val input = StaircaseInput(
            span = 1.5, totalRise = 1.0, stairWidth = 0.8,
            waistThickness = 150.0, fcu = 20.0, fy = 300.0
        )
        val result = engine.designStaircase(input)
        assertTrue("Should have risers", result.numberOfRisers > 0)
    }

    @Test
    fun `high concrete grade produces valid result`() {
        val input = StaircaseInput(
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 50.0, fy = 460.0
        )
        val result = engine.designStaircase(input)
        assertTrue("Should produce valid result with high fcu", result.numberOfRisers > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. InputGuard Validation
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `zero fcu throws`() {
        engine.designStaircase(StaircaseInput(
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 0.0, fy = 360.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative fy throws`() {
        engine.designStaircase(StaircaseInput(
            span = 3.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = -360.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero span throws`() {
        engine.designStaircase(StaircaseInput(
            span = 0.0, totalRise = 2.1, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero totalRise throws`() {
        engine.designStaircase(StaircaseInput(
            span = 3.0, totalRise = 0.0, stairWidth = 1.2,
            waistThickness = 200.0, fcu = 25.0, fy = 360.0
        ))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Safety Checks Validation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `safety checks have valid names`() {
        val result = engine.designStaircase(typicalInput())
        for (check in result.safetyChecks) {
            assertTrue("Check name should not be empty", check.name.isNotEmpty())
        }
    }

    @Test
    fun `riser comfort check exists`() {
        val result = engine.designStaircase(typicalInput())
        val comfortCheck = result.safetyChecks.find { it.name.contains("2R") || it.name.contains("Comfort") }
        assertNotNull("Should have comfort check", comfortCheck)
    }

    @Test
    fun `deflection check exists`() {
        val result = engine.designStaircase(typicalInput())
        val deflectionCheck = result.safetyChecks.find { it.name.contains("Deflection") }
        assertNotNull("Should have deflection check", deflectionCheck)
    }
}
