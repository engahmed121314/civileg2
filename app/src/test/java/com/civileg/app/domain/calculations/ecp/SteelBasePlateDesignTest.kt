package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SteelBasePlateDesign (ECP 205 / AISC 360).
 * Tests concentric, eccentric, and pocket base plate design calculations.
 */
class SteelBasePlateDesignTest {

    private lateinit var designer: SteelBasePlateDesign

    @Before
    fun setup() {
        designer = SteelBasePlateDesign()
    }

    private fun defaultConcentricInput(
        Pu: Double = 800.0,
        Mux: Double = 20.0,
        Muy: Double = 0.0,
        Vu: Double = 50.0,
        bf: Double = 200.0,
        dc: Double = 250.0,
        Fy: Double = 240.0,
        fpc: Double = 25.0
    ) = SteelBasePlateDesign.ConcentricInput(
        Pu = Pu,
        Mux = Mux,
        Muy = Muy,
        Vu = Vu,
        bf = bf,
        dc = dc,
        Fy = Fy,
        fpc = fpc
    )

    private fun defaultEccentricInput(
        Pu: Double = 500.0,
        Mux: Double = 150.0,
        Muy: Double = 0.0,
        Vu: Double = 100.0,
        bf: Double = 200.0,
        dc: Double = 300.0,
        Fy: Double = 240.0,
        fpc: Double = 25.0
    ) = SteelBasePlateDesign.EccentricInput(
        Pu = Pu,
        Mux = Mux,
        Muy = Muy,
        Vu = Vu,
        bf = bf,
        dc = dc,
        Fy = Fy,
        fpc = fpc
    )

    private fun defaultPocketInput(
        Pu: Double = 600.0,
        Mux: Double = 80.0,
        Muy: Double = 0.0,
        Vu: Double = 70.0,
        bf: Double = 200.0,
        dc: Double = 250.0,
        Fy: Double = 240.0,
        fpc: Double = 25.0
    ) = SteelBasePlateDesign.PocketInput(
        Pu = Pu,
        Mux = Mux,
        Muy = Muy,
        Vu = Vu,
        bf = bf,
        dc = dc,
        Fy = Fy,
        fpc = fpc
    )

    // ═══════════════════════════════════════════════════════════════
    // 1. Concentric Base Plate
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designConcentricBasePlate_validInputs_producesResult() {
        val result = designer.designConcentricBasePlate(defaultConcentricInput())
        assertNotNull(result)
        assertTrue("Base plate width > 0", result.plateWidth > 0)
        assertTrue("Base plate length > 0", result.plateLength > 0)
        assertTrue("Plate thickness > 0", result.plateThickness > 0)
    }

    @Test
    fun designConcentricBasePlate_largerLoad_requiresThickerPlate() {
        val light = designer.designConcentricBasePlate(defaultConcentricInput(Pu = 300.0))
        val heavy = designer.designConcentricBasePlate(defaultConcentricInput(Pu = 2000.0))
        assertTrue("Heavy load should require larger dimensions or thickness",
            heavy.plateThickness >= light.plateThickness || heavy.plateWidth >= light.plateWidth)
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Eccentric Base Plate
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designEccentricBasePlate_validInputs_producesResult() {
        val result = designer.designEccentricBasePlate(defaultEccentricInput())
        assertNotNull(result)
        assertTrue("Width > 0", result.plateWidth > 0)
        assertTrue("Length > 0", result.plateLength > 0)
        assertTrue("Thickness > 0", result.plateThickness > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Pocket Base Plate
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun designPocketBasePlate_validInputs_producesResult() {
        val result = designer.designPocketBasePlate(defaultPocketInput())
        assertNotNull(result)
        assertTrue("Pocket depth > 0", result.pocket.pocketDepth > 0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. InputGuard Validation
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designConcentricBasePlate_throwsForZeroFy() {
        designer.designConcentricBasePlate(defaultConcentricInput(Fy = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designConcentricBasePlate_throwsForZeroFpc() {
        designer.designConcentricBasePlate(defaultConcentricInput(fpc = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designConcentricBasePlate_throwsForZeroBf() {
        designer.designConcentricBasePlate(defaultConcentricInput(bf = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun designConcentricBasePlate_throwsForZeroDc() {
        designer.designConcentricBasePlate(defaultConcentricInput(dc = 0.0))
    }
}
