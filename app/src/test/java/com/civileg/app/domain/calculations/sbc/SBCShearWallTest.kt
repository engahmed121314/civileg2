package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SBCShearWall — SBC 304 shear wall design engine.
 * Covers flexural design, shear design, boundary elements, slenderness,
 * InputGuard validation, and edge cases.
 */
class SBCShearWallTest {

    private lateinit var wall: SBCShearWall

    @Before
    fun setup() {
        wall = SBCShearWall()
    }

    private fun defaultInput(
        wallType: WallType = WallType.ORDINARY,
        wallLength: Double = 4000.0,
        wallThickness: Double = 300.0,
        wallHeight: Double = 3000.0,
        numberOfStories: Int = 10,
        axialLoad: Double = 5000.0,
        shearForce: Double = 800.0,
        bendingMoment: Double = 3000.0,
        fcu: Double = 30.0,
        fy: Double = 400.0,
        fyv: Double = 250.0,
        clearCover: Double = 25.0
    ) = ShearWallInput(
        wallType = wallType,
        wallLength = wallLength,
        wallThickness = wallThickness,
        wallHeight = wallHeight,
        numberOfStories = numberOfStories,
        axialLoad = axialLoad,
        shearForce = shearForce,
        bendingMoment = bendingMoment,
        fcu = fcu,
        fy = fy,
        fyv = fyv,
        clearCover = clearCover
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — designWall
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designWall_validInputs_producesResult() {
        val result = wall.designWall(defaultInput())
        assertNotNull(result)
        assertTrue("Moment capacity > 0", result.momentCapacity > 0)
        assertTrue("Axial capacity > 0", result.axialCapacity > 0)
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun designWall_reinforcementResultsAreValid() {
        val result = wall.designWall(defaultInput())
        assertTrue("Vertical bars > 0", result.verticalReinforcement.bars > 0)
        assertTrue("Vertical diameter > 0", result.verticalReinforcement.diameter > 0)
        assertTrue("Horizontal bars > 0", result.horizontalReinforcement.bars > 0)
        assertTrue("Horizontal diameter > 0", result.horizontalReinforcement.diameter > 0)
    }

    @Test
    fun designWall_concreteVolumeAndSafetyChecksExist() {
        val result = wall.designWall(defaultInput())
        assertTrue("Concrete volume > 0", result.concreteVolumePerStory > 0)
        assertTrue("Safety checks non-empty", result.safetyChecks.isNotEmpty())
        assertTrue("Code notes non-empty", result.codeNotes.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Wall types
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designWall_ordinaryWall_producesResult() {
        val result = wall.designWall(defaultInput(wallType = WallType.ORDINARY))
        assertNotNull(result)
    }

    @Test
    fun designWall_specialWall_producesResult() {
        val result = wall.designWall(defaultInput(wallType = WallType.SPECIAL))
        assertNotNull(result)
    }

    @Test
    fun designWall_coupledWall_producesResult() {
        val input = defaultInput(wallType = WallType.COUPLED).copy(
            couplingBeamLength = 2000.0,
            couplingBeamHeight = 600.0,
            couplingBeamClearSpan = 1500.0
        )
        val result = wall.designWall(input)
        assertNotNull(result)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Flexural & Shear strength helper functions
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun calculateFlexuralStrength_returnsPositiveValues() {
        val (Mn, Pn) = wall.calculateFlexuralStrength(defaultInput())
        assertTrue("Mn > 0", Mn > 0)
        assertTrue("Pn > 0", Pn > 0)
    }

    @Test
    fun calculateFlexuralStrength_longerWall_higherMomentCapacity() {
        val shortWall = wall.calculateFlexuralStrength(defaultInput(wallLength = 3000.0))
        val longWall = wall.calculateFlexuralStrength(defaultInput(wallLength = 6000.0))
        assertTrue("Longer wall higher capacity", longWall.first > shortWall.first)
    }

    @Test
    fun calculateShearStrength_returnsPositiveValues() {
        val (Vc, Vs) = wall.calculateShearStrength(defaultInput())
        assertTrue("Vc >= 0", Vc >= 0)
        assertTrue("Vs >= 0", Vs >= 0)
    }

    @Test
    fun calculateShearStrength_thickerWall_higherShearCapacity() {
        val thin = wall.calculateShearStrength(defaultInput(wallThickness = 200.0))
        val thick = wall.calculateShearStrength(defaultInput(wallThickness = 400.0))
        val thinTotal = thin.first + thin.second
        val thickTotal = thick.first + thick.second
        assertTrue("Thicker wall higher shear capacity", thickTotal > thinTotal)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Boundary elements & Slenderness
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designBoundaryElements_returnsValidType() {
        val (type, _) = wall.designBoundaryElements(defaultInput())
        assertNotNull(type)
    }

    @Test
    fun checkSlenderness_returnsValidResult() {
        val (_, ratio) = wall.checkSlenderness(defaultInput())
        assertTrue("Slenderness ratio should be > 0", ratio > 0)
    }

    @Test
    fun checkSlenderness_thickWall_betterRatio() {
        val thin = wall.checkSlenderness(defaultInput(wallThickness = 200.0))
        val thick = wall.checkSlenderness(defaultInput(wallThickness = 500.0))
        assertTrue("Thicker wall lower slenderness ratio", thick.second < thin.second)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designWall_throwsForZeroFcu() {
        wall.designWall(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designWall_throwsForZeroFy() {
        wall.designWall(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designWall_throwsForZeroWallLength() {
        wall.designWall(defaultInput(wallLength = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designWall_throwsForZeroWallThickness() {
        wall.designWall(defaultInput(wallThickness = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Edge cases & scaling
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designWall_lowLoads_isSafe() {
        val result = wall.designWall(defaultInput(axialLoad = 500.0, shearForce = 100.0, bendingMoment = 200.0))
        assertTrue("Low loads should be safe for flexure", result.flexuralOk)
    }

    @Test
    fun designWall_highConcreteGrade_producesValidResult() {
        val result = wall.designWall(defaultInput(fcu = 50.0))
        assertNotNull(result)
        assertTrue("Moment capacity > 0", result.momentCapacity > 0)
    }
}
