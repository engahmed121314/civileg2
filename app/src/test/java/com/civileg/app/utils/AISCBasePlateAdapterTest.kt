package com.civileg.app.utils

import com.civileg.app.domain.calculations.aci.AISCSteelDesignEngine
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AISC engine exposes a base-plate entry point that must stay numerically
 * identical to the single shared implementation ([SteelBasePlateDesign]).
 * This guards against a future divergent "port" silently changing results.
 */
class AISCBasePlateAdapterTest {

    private val input = SteelBasePlateDesign.ConcentricInput(
        Pu = 500.0,
        Mux = 0.0,
        Muy = 0.0,
        Vu = 0.0,
        bf = 300.0,
        dc = 300.0,
        Fy = 250.0,
        fpc = 25.0,
        boltGrade = SteelBasePlateDesign.Companion.BoltGrade.GRADE_4_6
    )

    @Test
    fun `AISC base plate delegates to the shared AISC DG1 implementation`() {
        val direct = SteelBasePlateDesign().designConcentricBasePlate(input)
        val viaEngine = AISCSteelDesignEngine().designConcentricBasePlate(input)

        assertEquals("plate length must match", direct.plateLength, viaEngine.plateLength, 1e-9)
        assertEquals("plate width must match", direct.plateWidth, viaEngine.plateWidth, 1e-9)
        assertEquals("plate thickness must match", direct.plateThickness, viaEngine.plateThickness, 1e-9)
        assertEquals("utilization ratio must match", direct.utilizationRatio, viaEngine.utilizationRatio, 1e-9)
        assertEquals("safety must match", direct.isSafe, viaEngine.isSafe)
    }

    @Test
    fun `AISC base plate adapter returns a positive plate for a real column load`() {
        val res = AISCSteelDesignEngine().designConcentricBasePlate(input)
        assertTrue("plate length must be > 0", res.plateLength > 0.0)
        assertTrue("plate width must be > 0", res.plateWidth > 0.0)
        assertTrue("plate thickness must be >= 12 mm", res.plateThickness >= 12.0)
    }
}
