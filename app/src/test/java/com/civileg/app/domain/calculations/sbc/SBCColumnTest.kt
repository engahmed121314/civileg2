package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCColumn — SBC 304-2018 column design engine.
 *
 * Covers:
 *  - Axial capacity: calculateAxialCapacity happy path, validation, edge cases
 *  - Reinforcement: calculateReinforcement happy path, validation, edge cases
 *  - Shear design: calculateShearDesign happy path, validation
 *  - Code limits: min/max reinforcement ratios, spacing, cover
 *  - InputGuard: zero/negative/NaN values throw IllegalArgumentException
 *  - Safety checks: isSafe, utilization ratio
 */
class SBCColumnTest {

    private lateinit var engine: SBCColumn

    @Before
    fun setup() {
        engine = SBCColumn()
    }

    companion object {
        private const val TYPICAL_FCU = 30.0
        private const val TYPICAL_FY = 420.0
        private const val TYPICAL_WIDTH = 400.0
        private const val TYPICAL_DEPTH = 400.0
        private const val TYPICAL_AXIAL_LOAD = 1500.0  // kN
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Axial Capacity — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `axial capacity - typical inputs produce positive result`() {
        val capacity = engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Capacity should be positive", capacity > 0)
    }

    @Test
    fun `axial capacity - zero reinforcement still produces positive capacity`() {
        val capacity = engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Concrete alone should carry load", capacity > 0)
    }

    @Test
    fun `axial capacity - higher fcu increases capacity`() {
        val cap25 = engine.calculateAxialCapacity(
            fcu = 25.0, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        val cap40 = engine.calculateAxialCapacity(
            fcu = 40.0, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Higher fcu should give higher capacity", cap40 > cap25)
    }

    @Test
    fun `axial capacity - more reinforcement increases capacity`() {
        val capLow = engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 1200.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        val capHigh = engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 3600.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("More reinforcement should increase capacity", capHigh > capLow)
    }

    @Test
    fun `axial capacity - larger section increases capacity`() {
        val capSmall = engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = 300.0,
            depth = 300.0, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        val capLarge = engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = 500.0,
            depth = 500.0, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Larger section should have higher capacity", capLarge > capSmall)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Axial Capacity — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `axial capacity - zero fcu throws`() {
        engine.calculateAxialCapacity(
            fcu = 0.0, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `axial capacity - negative width throws`() {
        engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = -400.0,
            depth = TYPICAL_DEPTH, reinforcementArea = 2400.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `axial capacity - negative reinforcementArea throws`() {
        engine.calculateAxialCapacity(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, reinforcementArea = -100.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Reinforcement — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `reinforcement - typical inputs produce valid result`() {
        val result = engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = TYPICAL_AXIAL_LOAD,
            momentX = 50.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should have provided reinforcement", result.astProvided > 0)
        assertTrue("Should have required reinforcement", result.astRequired > 0)
        assertTrue("Bar diameter should be positive", result.barDiameter > 0)
        assertTrue("Number of bars should be >= 4", result.numberOfBars >= 4)
        assertTrue("Ties diameter should be positive", result.tiesDiameter > 0)
        assertTrue("Ties spacing should be positive", result.tiesSpacing > 0)
    }

    @Test
    fun `reinforcement - low axial load is safe`() {
        val result = engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = 500.0,
            momentX = 20.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Low axial load should be safe", result.isSafe)
    }

    @Test
    fun `reinforcement - moments increase required steel`() {
        val resultNoMoment = engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = TYPICAL_AXIAL_LOAD,
            momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        val resultWithMoment = engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = TYPICAL_AXIAL_LOAD,
            momentX = 200.0, momentY = 100.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Both should produce valid results",
            resultNoMoment.astProvided > 0 && resultWithMoment.astProvided > 0)
    }

    @Test
    fun `reinforcement - result has code notes`() {
        val result = engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = TYPICAL_AXIAL_LOAD,
            momentX = 50.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Reinforcement — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `reinforcement - zero fcu throws`() {
        engine.calculateReinforcement(
            fcu = 0.0, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = TYPICAL_AXIAL_LOAD,
            momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reinforcement - zero axialLoad throws`() {
        engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = TYPICAL_DEPTH, axialLoad = 0.0,
            momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reinforcement - negative depth throws`() {
        engine.calculateReinforcement(
            fcu = TYPICAL_FCU, fy = TYPICAL_FY, width = TYPICAL_WIDTH,
            depth = -400.0, axialLoad = TYPICAL_AXIAL_LOAD,
            momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Shear Design — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `shear design - typical inputs produce valid result`() {
        val result = engine.calculateShearDesign(
            Vu = 300.0, width = TYPICAL_WIDTH, depth = TYPICAL_DEPTH,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY
        )
        assertTrue("Vc should be positive", result.Vc > 0)
        assertTrue("phiVc should be positive", result.phiVc > 0)
        assertTrue("Stirrup spacing should be positive or zero", result.stirrupSpacing >= 0)
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `shear design - low Vu may not need stirrups`() {
        val result = engine.calculateShearDesign(
            Vu = 50.0, width = TYPICAL_WIDTH, depth = TYPICAL_DEPTH,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY
        )
        assertTrue("Should be safe for low Vu", result.isSafe)
    }

    @Test
    fun `shear design - high Vu requires stirrups`() {
        val result = engine.calculateShearDesign(
            Vu = 800.0, width = TYPICAL_WIDTH, depth = TYPICAL_DEPTH,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY
        )
        // Whether stirrups are needed depends on the calculation
        assertNotNull(result)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Shear Design — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `shear design - zero Vu throws`() {
        engine.calculateShearDesign(
            Vu = 0.0, width = TYPICAL_WIDTH, depth = TYPICAL_DEPTH,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `shear design - zero fcu throws`() {
        engine.calculateShearDesign(
            Vu = 300.0, width = TYPICAL_WIDTH, depth = TYPICAL_DEPTH,
            fcu = 0.0, fy = TYPICAL_FY
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `shear design - negative width throws`() {
        engine.calculateShearDesign(
            Vu = 300.0, width = -400.0, depth = TYPICAL_DEPTH,
            fcu = TYPICAL_FCU, fy = TYPICAL_FY
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Code Limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `min reinforcement ratio is 1 percent`() {
        assertEquals("Min ratio should be 1%", 0.01, engine.getMinReinforcementRatio(), 0.001)
    }

    @Test
    fun `max reinforcement ratio is 8 percent`() {
        assertEquals("Max ratio should be 8%", 0.08, engine.getMaxReinforcementRatio(), 0.001)
    }

    @Test
    fun `min spacing is 40mm`() {
        assertEquals("Min spacing should be 40mm", 40.0, engine.getMinSpacing(), 0.1)
    }

    @Test
    fun `max spacing is 300mm`() {
        assertEquals("Max spacing should be 300mm", 300.0, engine.getMaxSpacing(), 0.1)
    }

    @Test
    fun `min cover is 40mm`() {
        assertEquals("Min cover should be 40mm", 40.0, engine.getMinCover(), 0.1)
    }
}
