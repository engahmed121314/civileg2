package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BeamDesignEnginePart2 (shear design helper).
 */
class BeamDesignEnginePart2Test {

    @Test
    fun designShear_ecpCode_producesValidResult() {
        val result = BeamDesignEnginePart2.designShear(
            b = 250.0,
            d = 500.0,
            vu = 120.0,
            fcu = 30.0,
            fy = 400.0,
            code = DesignCode.ECP,
            span = 6000.0
        )
        assertNotNull(result)
        assertTrue("vc > 0", result.vc > 0)
        assertTrue("appliedStress > 0", result.appliedStress > 0)
        assertTrue("steps non-empty", result.steps.isNotEmpty())
    }

    @Test
    fun designShear_aciCode_producesValidResult() {
        val result = BeamDesignEnginePart2.designShear(
            b = 250.0,
            d = 500.0,
            vu = 120.0,
            fcu = 30.0,
            fy = 400.0,
            code = DesignCode.ACI,
            span = 6000.0
        )
        assertNotNull(result)
        assertTrue("vc > 0", result.vc > 0)
    }

    @Test
    fun designShear_highShear_requiresStirrups() {
        val lowShear = BeamDesignEnginePart2.designShear(250.0, 500.0, 30.0, 30.0, 400.0, DesignCode.ECP, 6000.0)
        val highShear = BeamDesignEnginePart2.designShear(250.0, 500.0, 250.0, 30.0, 400.0, DesignCode.ECP, 6000.0)
        assertTrue("High shear force should increase required stirrup capacity", highShear.vs >= lowShear.vs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designShear_throwsForZeroWidth() {
        BeamDesignEnginePart2.designShear(0.0, 500.0, 100.0, 30.0, 400.0, DesignCode.ECP, 6000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designShear_throwsForZeroDepth() {
        BeamDesignEnginePart2.designShear(250.0, 0.0, 100.0, 30.0, 400.0, DesignCode.ECP, 6000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun designShear_throwsForZeroFcu() {
        BeamDesignEnginePart2.designShear(250.0, 500.0, 100.0, 0.0, 400.0, DesignCode.ECP, 6000.0)
    }
}
