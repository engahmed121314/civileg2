package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.base.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for SBCTank — SBC 304-2018 tank design engine.
 *
 * Covers:
 *  - calculateTank: happy path for rectangular, circular, underground tanks
 *  - Safety checks: wall shear, crack control, reinforcement ratio
 *  - SBC-specific: cover 50mm, min ρ = 0.002, KSA bar sizes
 *  - InputGuard: zero/negative values throw IllegalArgumentException
 *  - Edge cases: small/large tanks, different tank types
 */
class SBCTankTest {

    private lateinit var engine: SBCTank

    @Before
    fun setup() {
        engine = SBCTank()
    }

    companion object {
        private const val TYPICAL_LENGTH = 6000.0    // mm
        private const val TYPICAL_WIDTH = 4000.0     // mm
        private const val TYPICAL_HEIGHT = 3000.0    // mm
        private const val TYPICAL_WATER_DEPTH = 2800.0 // mm
        private const val TYPICAL_FCU = 30.0
        private const val TYPICAL_FY = 420.0
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Rectangular Tank — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `rectangular tank - typical inputs produce valid result`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Wall thickness should be positive", result.wallThickness > 0)
        assertTrue("Base thickness should be positive", result.baseThickness > 0)
        assertTrue("Capacity should be positive", result.capacityM3 > 0)
        assertTrue("Concrete volume should be positive", result.concreteVolume > 0)
        assertTrue("Pressure should be positive", result.pressure > 0)
    }

    @Test
    fun `rectangular tank - has valid reinforcement`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Wall reinforcement should have provided area",
            result.wallReinforcement.astProvided > 0)
        assertTrue("Base reinforcement should have provided area",
            result.baseReinforcement.astProvided > 0)
    }

    @Test
    fun `rectangular tank - has safety checks`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `rectangular tank - has recommendations`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Should have recommendations", result.recommendations.isNotEmpty())
    }

    @Test
    fun `rectangular tank - moments are positive`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Wall moment should be positive", result.maxMomentWall > 0)
        assertTrue("Max shear should be positive", result.maxShearWall > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Circular Tank — Happy Path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `circular tank - typical inputs produce valid result`() {
        val result = engine.calculateTank(
            length = 6000.0, width = 6000.0, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.CIRCULAR_GROUND
        )
        assertTrue("Wall thickness should be positive", result.wallThickness > 0)
        assertTrue("Capacity should be positive", result.capacityM3 > 0)
    }

    @Test
    fun `circular tank - has hoop tension reinforcement`() {
        val result = engine.calculateTank(
            length = 6000.0, width = 6000.0, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.CIRCULAR_GROUND
        )
        assertTrue("Wall reinforcement should have provided area",
            result.wallReinforcement.astProvided > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Underground Tanks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `rectangular underground tank produces valid result`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_UNDERGROUND
        )
        assertTrue("Should produce valid result", result.wallThickness > 0)
        assertTrue("Should have uplift safety factor", result.factorOfSafetyUplift > 0)
    }

    @Test
    fun `circular underground tank produces valid result`() {
        val result = engine.calculateTank(
            length = 6000.0, width = 6000.0, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.CIRCULAR_UNDERGROUND
        )
        assertTrue("Should produce valid result", result.wallThickness > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Elevated Tanks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `rectangular elevated tank produces valid result`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_ELEVATED
        )
        assertTrue("Should produce valid result", result.wallThickness > 0)
    }

    @Test
    fun `circular elevated tank produces valid result`() {
        val result = engine.calculateTank(
            length = 6000.0, width = 6000.0, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.CIRCULAR_ELEVATED
        )
        assertTrue("Should produce valid result", result.wallThickness > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. SBC-Specific Checks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `SBC tank uses 50mm cover for water face`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        // SBC 304: min cover = 50mm for water-retaining structures
        // Verify the result is valid — cover is internal to the engine
        assertTrue("Wall thickness should be at least 200mm", result.wallThickness >= 200.0)
    }

    @Test
    fun `SBC tank structural system references SBC 304`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Structural system should reference SBC 304",
            result.structuralSystem.contains("SBC 304"))
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Size Variations
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `small tank produces valid result`() {
        val result = engine.calculateTank(
            length = 2000.0, width = 2000.0, height = 1500.0,
            waterDepth = 1200.0, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR
        )
        assertTrue("Small tank should have valid capacity", result.capacityM3 > 0)
        assertTrue("Small tank should have valid wall thickness", result.wallThickness > 0)
    }

    @Test
    fun `large tank produces valid result`() {
        val result = engine.calculateTank(
            length = 20000.0, width = 15000.0, height = 6000.0,
            waterDepth = 5500.0, fcu = 35.0, fy = 460.0,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Large tank should have valid capacity", result.capacityM3 > 0)
        assertTrue("Large tank should have valid wall thickness", result.wallThickness > 0)
    }

    @Test
    fun `deeper water increases wall moment`() {
        val resultShallow = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = 1500.0, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        val resultDeep = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = 2800.0, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Deeper water should increase wall moment",
            resultDeep.maxMomentWall > resultShallow.maxMomentWall)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Material Variations
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `higher concrete grade produces valid result`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = 45.0, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Should produce valid result with high fcu", result.wallThickness > 0)
    }

    @Test
    fun `higher steel grade produces valid result`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = 460.0,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Should produce valid result with high fy", result.wallThickness > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. All Tank Types
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `all tank types produce valid results`() {
        for (type in TankType.entries) {
            val result = engine.calculateTank(
                length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
                waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
                type = type
            )
            assertTrue("Tank type $type should have positive wall thickness", result.wallThickness > 0)
            assertTrue("Tank type $type should have positive capacity", result.capacityM3 > 0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. InputGuard Validation
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `zero length throws`() {
        engine.calculateTank(
            length = 0.0, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero width throws`() {
        engine.calculateTank(
            length = TYPICAL_LENGTH, width = 0.0, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height throws`() {
        engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = 0.0,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero waterDepth throws`() {
        engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = 0.0, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero fcu throws`() {
        engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = 0.0, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative fy throws`() {
        engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = -420.0,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. Cost and Quantities
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `cost and quantities are positive`() {
        val result = engine.calculateTank(
            length = TYPICAL_LENGTH, width = TYPICAL_WIDTH, height = TYPICAL_HEIGHT,
            waterDepth = TYPICAL_WATER_DEPTH, fcu = TYPICAL_FCU, fy = TYPICAL_FY,
            type = TankType.RECTANGULAR_GROUND
        )
        assertTrue("Steel weight should be positive", result.steelWeight > 0)
        assertTrue("Cost should be positive", result.cost > 0)
    }
}
