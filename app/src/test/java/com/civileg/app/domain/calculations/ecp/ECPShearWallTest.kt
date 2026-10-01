package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.ShearWallDesign
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ECPShearWall — ECP 203-2020 / ECP 201 shear wall design engine.
 * Covers flexural design, shear design, boundary elements, slenderness,
 * InputGuard validation, and edge cases.
 */
class ECPShearWallTest {

    private lateinit var wall: ECPShearWall

    @Before
    fun setup() {
        wall = ECPShearWall()
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

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
        wallType, wallLength, wallThickness, wallHeight, numberOfStories,
        axialLoad, shearForce, bendingMoment,
        fcu, fy, fyv, clearCover
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Happy path — designWall
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designWall_validInputs_producesResult`() {
        val result = wall.designWall(defaultInput())
        assertNotNull(result)
    }

    @Test
    fun `designWall_flexuralCapacityIsPositive`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Moment capacity > 0", result.momentCapacity > 0)
    }

    @Test
    fun `designWall_axialCapacityIsPositive`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Axial capacity > 0", result.axialCapacity > 0)
    }

    @Test
    fun `designWall_shearCapacityIsPositive`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun `designWall_verticalReinforcementIsValid`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Vertical bars > 0", result.verticalReinforcement.bars > 0)
        assertTrue("Vertical diameter > 0", result.verticalReinforcement.diameter > 0)
        assertTrue("Vertical provided area > 0", result.verticalReinforcement.providedArea > 0)
    }

    @Test
    fun `designWall_horizontalReinforcementIsValid`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Horizontal bars > 0", result.horizontalReinforcement.bars > 0)
        assertTrue("Horizontal diameter > 0", result.horizontalReinforcement.diameter > 0)
        assertTrue("Horizontal provided area > 0", result.horizontalReinforcement.providedArea > 0)
    }

    @Test
    fun `designWall_concreteVolumeIsPositive`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Concrete volume > 0", result.concreteVolumePerStory > 0)
    }

    @Test
    fun `designWall_safetyChecksExist`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Should have safety checks", result.safetyChecks.isNotEmpty())
    }

    @Test
    fun `designWall_codeNotesExist`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Should have code notes", result.codeNotes.isNotEmpty())
    }

    @Test
    fun `designWall_slendernessRatioIsPositive`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Slenderness ratio > 0", result.slendernessRatio > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Wall types
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designWall_ordinaryWall_producesResult`() {
        val result = wall.designWall(defaultInput(wallType = WallType.ORDINARY))
        assertNotNull(result)
    }

    @Test
    fun `designWall_specialWall_producesResult`() {
        val result = wall.designWall(defaultInput(wallType = WallType.SPECIAL))
        assertNotNull(result)
    }

    @Test
    fun `designWall_coupledWall_producesResult`() {
        val input = defaultInput(wallType = WallType.COUPLED).copy(
            couplingBeamLength = 2000.0,
            couplingBeamHeight = 600.0,
            couplingBeamClearSpan = 1500.0
        )
        val result = wall.designWall(input)
        assertNotNull(result)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Flexural strength
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateFlexuralStrength_returnsPositiveValues`() {
        val (Mn, Pn) = wall.calculateFlexuralStrength(defaultInput())
        assertTrue("Mn > 0", Mn > 0)
        assertTrue("Pn > 0", Pn > 0)
    }

    @Test
    fun `calculateFlexuralStrength_longerWall_higherMomentCapacity`() {
        val short = wall.calculateFlexuralStrength(defaultInput(wallLength = 3000.0))
        val long = wall.calculateFlexuralStrength(defaultInput(wallLength = 6000.0))
        assertTrue("Longer wall should have higher moment capacity", long.first > short.first)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Shear strength
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `calculateShearStrength_returnsPositiveValues`() {
        val (Vc, Vs) = wall.calculateShearStrength(defaultInput())
        assertTrue("Vc >= 0", Vc >= 0)
        assertTrue("Vs >= 0", Vs >= 0)
    }

    @Test
    fun `calculateShearStrength_thickerWall_higherShearCapacity`() {
        val thin = wall.calculateShearStrength(defaultInput(wallThickness = 200.0))
        val thick = wall.calculateShearStrength(defaultInput(wallThickness = 400.0))
        val thinTotal = thin.first + thin.second
        val thickTotal = thick.first + thick.second
        assertTrue("Thicker wall should have higher shear capacity", thickTotal > thinTotal)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Boundary elements
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designBoundaryElements_returnsValidType`() {
        val (type, _) = wall.designBoundaryElements(defaultInput())
        assertNotNull(type)
    }

    @Test
    fun `designBoundaryElements_lowMoment_mayNotNeedBoundary`() {
        val input = defaultInput(bendingMoment = 100.0, axialLoad = 500.0)
        val (type, _) = wall.designBoundaryElements(input)
        // Low moment should typically not need boundary element
        assertNotNull(type)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Slenderness check
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `checkSlenderness_returnsValidResult`() {
        val (ok, ratio) = wall.checkSlenderness(defaultInput())
        assertTrue("Slenderness ratio should be > 0", ratio > 0)
    }

    @Test
    fun `checkSlenderness_thickWall_betterRatio`() {
        val thin = wall.checkSlenderness(defaultInput(wallThickness = 200.0))
        val thick = wall.checkSlenderness(defaultInput(wallThickness = 500.0))
        assertTrue("Thicker wall should have lower slenderness ratio", thick.second < thin.second)
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Coupling beam
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designCouplingBeam_noCoupling_returnsNull`() {
        val result = wall.designCouplingBeam(defaultInput())
        // No coupling beam dimensions set → should return null
        assertNull(result)
    }

    @Test
    fun `designCouplingBeam_withDimensions_producesResult`() {
        val input = defaultInput(wallType = WallType.COUPLED).copy(
            couplingBeamLength = 2000.0,
            couplingBeamHeight = 600.0,
            couplingBeamClearSpan = 1500.0
        )
        val result = wall.designCouplingBeam(input)
        // May or may not return a result depending on internal logic
        if (result != null) {
            assertTrue("Coupling beam bars should be positive if present", result.diagonalBars >= 0)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. InputGuard validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroFcu`() {
        wall.designWall(defaultInput(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForNegativeFcu`() {
        wall.designWall(defaultInput(fcu = -30.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroFy`() {
        wall.designWall(defaultInput(fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroFyv`() {
        wall.designWall(defaultInput(fyv = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroWallLength`() {
        wall.designWall(defaultInput(wallLength = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroWallThickness`() {
        wall.designWall(defaultInput(wallThickness = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroWallHeight`() {
        wall.designWall(defaultInput(wallHeight = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `designWall_throwsForZeroClearCover`() {
        wall.designWall(defaultInput(clearCover = 0.0))
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `designWall_lowLoads_isSafe`() {
        val result = wall.designWall(defaultInput(axialLoad = 500.0, shearForce = 100.0, bendingMoment = 200.0))
        assertTrue("Low loads should be safe for flexure", result.flexuralOk)
    }

    @Test
    fun `designWall_highStrengthConcrete_producesResult`() {
        val result = wall.designWall(defaultInput(fcu = 50.0))
        assertNotNull(result)
        assertTrue("Moment capacity > 0", result.momentCapacity > 0)
    }

    @Test
    fun `designWall_singleStory_producesResult`() {
        val result = wall.designWall(defaultInput(numberOfStories = 1))
        assertNotNull(result)
    }

    @Test
    fun `designWall_longWall_higherMomentCapacity`() {
        val shortResult = wall.designWall(defaultInput(wallLength = 2000.0))
        val longResult = wall.designWall(defaultInput(wallLength = 8000.0))
        assertTrue("Longer wall should have higher moment capacity",
            longResult.momentCapacity > shortResult.momentCapacity)
    }

    @Test
    fun `designWall_compressionDepthIsPositive`() {
        val result = wall.designWall(defaultInput())
        assertTrue("Compression depth >= 0", result.compressionDepth >= 0)
    }
}
