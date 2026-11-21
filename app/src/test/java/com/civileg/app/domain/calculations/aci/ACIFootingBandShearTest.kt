package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.BoundaryConstraints
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Golden vectors for F-03 (one-way shear critical section at d, not d/2 —
 * ACI 318M-14 §13.2.7.1, printed p.307) and F-04 (§13.3.3.3 band rule
 * replaces the fake "20% / 13.3.4" distribution rule, printed pp.310-311).
 * Owner-approved fixes, 2026-11-21. Hand-computed 2026-11-21.
 */
class ACIFootingBandShearTest {

    private lateinit var footing: ACIFooting

    @Before
    fun setup() {
        footing = ACIFooting()
    }

    // Case: fcu=30, fy=400, col 400x900, axial=2000 kN (factored), SBC=200,
    // h=500, no moments, DEAD_LIVE (ACI factor 1.4):
    //   P_service=1428.57, netSBC=187.5, A=7.6190, ratio=1.5
    //   B=2300, L=3400, A=7.82, q=182.68, qf=255.75... (see CLAUSE_LOG F-03)
    //   d = 500-75-10 = 415
    //   Vu_y @d  = 255.85 x 2.300 x (1.250-0.415) = 491.2 <= Vc_y = 596.2  PASS
    //   Vu_y @d/2 (old) = 613.2 > 596.2 -> old code falsely failed isSafe
    //   Vu_x @d = 465.2 <= Vc_x = 881.3; punching 1726 <= 2086
    @Test
    fun goldenF03_oneWayShearAtD_passesWhereD2Failed() {
        val result = footing.designIsolatedFooting(
            30.0, 400.0, 400.0, 900.0, 2000.0, 0.0, 0.0, 200.0, 500.0,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertEquals(2300.0, result.requiredWidth, 0.001)
        assertEquals(3400.0, result.requiredLength, 0.001)
        assertTrue(
            "no one-way shear warning expected at the correct section d",
            result.warnings.none { it.contains("One-way shear") }
        )
        assertTrue(result.isSafe)
    }

    // Band rule (same case): beta = 3400/2300 = 1.4783, gamma_s = 2/(beta+1) = 0.8070
    // Short-direction steel: rho-governed 789.8 < As,min = 0.002x1000x500 = 1000 mm2/m
    //   total = 1000 x 3.4 = 3400; band per m = 0.80703 x 3400/2.3 = 1192.9
    //   -> 11 dia-12 per meter, spacing floor(1000/11)=90 -> coerced to 100
    //   outside band per m = 0.19297 x 3400/1.1 = 596.4 -> "596"
    @Test
    fun goldenF04_bandRuleDetailing() {
        val result = footing.designIsolatedFooting(
            30.0, 400.0, 400.0, 900.0, 2000.0, 0.0, 0.0, 200.0, 500.0,
            LoadCombination.DEAD_LIVE, BoundaryConstraints()
        )
        assertTrue(
            "band rule note expected",
            result.codeNotes.any {
                it.startsWith("Band rule 13.3.3.3(b): beta=1.48, gamma_s=0.807, band width=2300 mm")
            }
        )
        assertTrue(
            "band bar detailing note expected",
            result.codeNotes.any { it.startsWith("Short-dir band: 11Ø12 @ 100mm; outside band: 596 mm2/m") }
        )
        assertTrue(
            "old fake 20% / 13.3.4 rule must be gone",
            result.codeNotes.none { it.contains("13.3.4") || it.contains("Distribution:") }
        )
    }
}
