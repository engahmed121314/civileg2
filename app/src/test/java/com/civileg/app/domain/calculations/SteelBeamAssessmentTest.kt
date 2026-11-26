package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.ecp.SteelDesignEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A beam whose flange is slender must carry an explicit NOT ASSESSED warning, because the
 * flexural capacity is computed from the plastic modulus without a local-buckling reduction.
 */
class SteelBeamAssessmentTest {

    private fun section(b: Double, tf: Double) = SteelDesignEngine.SectionProperties(
        name = "test", h = 300.0, b = b, tw = 7.0, tf = tf,
        Ix = 8.0e7, Iy = 6.0e6, Zx = 6.0e5, Zy = 1.0e5, Sx = 5.0e5, Sy = 8.0e4,
        rx = 120.0, ry = 40.0, A = 5000.0, J = 1.0e5
    )

    @Test
    fun slenderFlangeBeamIsFlaggedNotAssessed() {
        val r = SteelDesignEngine().designBeam(
            Mu = 10.0, Vu = 10.0, w_LL = 1.0, span = 4000.0,
            section = section(b = 400.0, tf = 8.0)
        )
        assertTrue(r.warnings.any { it.startsWith("NOT ASSESSED") })
        assertFalse(r.assessmentComplete)
        assertTrue(r.incompleteReasons.isNotEmpty())
    }

    @Test
    fun compactBeamHasNoNotAssessedWarning() {
        val r = SteelDesignEngine().designBeam(
            Mu = 10.0, Vu = 10.0, w_LL = 1.0, span = 4000.0,
            section = section(b = 150.0, tf = 12.0)
        )
        assertTrue(r.warnings.none { it.startsWith("NOT ASSESSED") })
        assertTrue(r.assessmentComplete)
    }
}
