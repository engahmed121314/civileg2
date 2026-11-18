package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BeamDesignEnginePart2.designTorsion across ECP, ACI, and SBC codes.
 */
class BeamTorsionTest {

    @Test
    fun designTorsion_belowThreshold_returnsNoTorsionNeeded() {
        val result = BeamDesignEnginePart2.designTorsion(
            b = 300.0,
            h = 600.0,
            d = 550.0,
            Tu = 1.0, // Very low torsion moment below threshold
            Vu = 50.0,
            fcu = 30.0,
            fy = 400.0,
            code = DesignCode.ECP,
            cover = 50.0
        )
        assertNotNull(result)
        assertTrue("Torsion below threshold should not require design", result.steps.any { it.result.contains("No torsion design needed") })
    }

    @Test
    fun designTorsion_aboveThreshold_ecpCode_computesReinforcement() {
        val result = BeamDesignEnginePart2.designTorsion(
            b = 300.0,
            h = 600.0,
            d = 550.0,
            Tu = 30.0, // Significant torsion moment
            Vu = 100.0,
            fcu = 30.0,
            fy = 400.0,
            code = DesignCode.ECP,
            cover = 50.0
        )
        assertNotNull(result)
        assertTrue("Combined stress computed", result.combinedStress > 0)
        assertTrue("Longitudinal torsion steel As > 0", result.longitudinalAs > 0)
        assertTrue("Stirrup spacing > 0", result.stirrupSpacing > 0)
    }

    @Test
    fun designTorsion_aboveThreshold_aciCode_computesReinforcement() {
        val result = BeamDesignEnginePart2.designTorsion(
            b = 300.0,
            h = 600.0,
            d = 550.0,
            Tu = 30.0,
            Vu = 100.0,
            fcu = 30.0,
            fy = 400.0,
            code = DesignCode.ACI,
            cover = 50.0
        )
        assertNotNull(result)
        assertTrue("ACI torsion result valid", result.combinedStress > 0)
    }

    @Test
    fun designTorsion_aboveThreshold_sbcCode_computesReinforcement() {
        val result = BeamDesignEnginePart2.designTorsion(
            b = 300.0,
            h = 600.0,
            d = 550.0,
            Tu = 30.0,
            Vu = 100.0,
            fcu = 30.0,
            fy = 400.0,
            code = DesignCode.SBC,
            cover = 50.0
        )
        assertNotNull(result)
        assertTrue("SBC torsion result valid", result.combinedStress > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designTorsion_throwsForZeroWidth() {
        BeamDesignEnginePart2.designTorsion(0.0, 600.0, 550.0, 20.0, 50.0, 30.0, 400.0, DesignCode.ECP, 50.0)
    }
}
