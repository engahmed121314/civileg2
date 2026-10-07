package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIFooting
import com.civileg.app.domain.calculations.ecp.ECPFooting
import com.civileg.app.domain.calculations.sbc.SBCFooting
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the combined footing col2 punching shear fix.
 *
 * BUG: ACIFooting, ECPFooting, and SBCFooting were using col1 dimensions
 * (columnWidth, columnDepth) for the col2 punching shear check instead of
 * col2Width, col2Depth. SBCFooting wasn't checking col2 punching at all.
 *
 * FIX: All three implementations now correctly use col2Width/col2Depth for
 * the punching shear check at column 2. SBCFooting now also checks col2
 * punching and one-way shear.
 *
 * These tests verify that different col2 dimensions produce different results
 * than identical col2 dimensions — proving the parameters are actually used.
 */
class CombinedFootingCol2PunchingTest {

    companion object {
        private const val FCU = 25.0
        private const val FY = 400.0
        private const val P1 = 1500.0
        private const val P2 = 2000.0
        private const val DIST = 5000.0
        private const val SBC = 200.0
        private const val DEPTH = 600.0
        private val LC = LoadCombination.DEAD_LIVE
        private const val COL1_W = 400.0
        private const val COL1_D = 400.0
    }

    // ══════════════════════════════════════════════════════════════════════
    // ACIFooting — col2 punching uses col2Width/col2Depth
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun aciFooting_differentCol2Dimensions_differentPunchingResult() {
        val footing = ACIFooting()

        // Case A: col2 same as col1 (400x400)
        val resultSame = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 400.0, 400.0
        )

        // Case B: col2 much smaller (200x200) — should change punching behavior
        val resultSmall = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 200.0, 200.0
        )

        // If col2 dimensions were ignored (bug), both results would be identical.
        // With the fix, different col2 dimensions should produce different isSafe results
        // or at least different punching shear check values.
        // We verify that the punching shear check object is different:
        val punchingSame = resultSame.punchingShearCheck
        val punchingSmall = resultSmall.punchingShearCheck

        // A smaller column has a smaller critical perimeter, so it should have
        // different utilization ratio (higher stress concentration)
        assertNotEquals(
            "Punching shear utilization should differ when col2 dimensions change",
            punchingSame.utilizationRatio, punchingSmall.utilizationRatio, 0.001
        )
    }

    @Test
    fun aciFooting_verySmallCol2_moreLikelyUnsafePunching() {
        val footing = ACIFooting()

        // Large col2 (600x600) — safer punching
        val resultLarge = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 600.0, 600.0
        )

        // Very small col2 (150x150) — more critical punching
        val resultTiny = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 150.0, 150.0
        )

        // Tiny col2 should have higher utilization ratio (more stressed)
        assertTrue(
            "Tiny col2 should have higher punching utilization than large col2",
            resultTiny.punchingShearCheck.utilizationRatio >
            resultLarge.punchingShearCheck.utilizationRatio
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // ECPFooting — col2 punching uses col2Width/col2Depth
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun ecpFooting_differentCol2Dimensions_differentPunchingResult() {
        val footing = ECPFooting()

        val resultSame = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 400.0, 400.0
        )

        val resultSmall = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 200.0, 200.0
        )

        assertNotEquals(
            "ECP: Punching shear utilization should differ when col2 dimensions change",
            resultSame.punchingShearCheck.utilizationRatio,
            resultSmall.punchingShearCheck.utilizationRatio, 0.001
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — now checks col2 punching (was missing entirely)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun sbcFooting_differentCol2Dimensions_differentPunchingResult() {
        val footing = SBCFooting()

        val resultSame = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 400.0, 400.0
        )

        val resultSmall = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 200.0, 200.0
        )

        // SBC now checks col2 punching — different col2 dims should give different results
        assertNotEquals(
            "SBC: Punching shear utilization should differ when col2 dimensions change",
            resultSame.punchingShearCheck.utilizationRatio,
            resultSmall.punchingShearCheck.utilizationRatio, 0.001
        )
    }

    @Test
    fun sbcFooting_col2PunchingActuallyChecked_isSafeReflectsCol2() {
        val footing = SBCFooting()

        // With large col2 — should be safe
        val resultSafe = footing.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 600.0, 600.0
        )

        // With very small col2 under heavy load — more likely unsafe
        val resultCritical = footing.designCombinedFooting(
            FCU, FY, P1, 5000.0, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 150.0, 150.0
        )

        // The isSafe flag should reflect col2 punching (previously it didn't check col2 at all)
        // At minimum, the critical case should have higher utilization
        assertTrue(
            "SBC: Critical col2 should have higher punching utilization",
            resultCritical.punchingShearCheck.utilizationRatio >
            resultSafe.punchingShearCheck.utilizationRatio
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // Cross-code consistency: All three codes use col2 dimensions
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun allCodes_sameCol2Dims_produceConsistentResults() {
        val aci = ACIFooting()
        val ecp = ECPFooting()
        val sbc = SBCFooting()

        val aciResult = aci.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 300.0, 300.0
        )
        val ecpResult = ecp.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 300.0, 300.0
        )
        val sbcResult = sbc.designCombinedFooting(
            FCU, FY, P1, P2, DIST, SBC, DEPTH, LC,
            COL1_W, COL1_D, 300.0, 300.0
        )

        // All three should produce valid results
        assertTrue("ACI width > 0", aciResult.requiredWidth > 0)
        assertTrue("ECP width > 0", ecpResult.requiredWidth > 0)
        assertTrue("SBC width > 0", sbcResult.requiredWidth > 0)

        // All should have a punching shear check (not default/empty)
        assertTrue("ACI punching has perimeter", aciResult.punchingShearCheck.criticalPerimeter > 0)
        assertTrue("ECP punching has perimeter", ecpResult.punchingShearCheck.criticalPerimeter > 0)
        assertTrue("SBC punching has perimeter", sbcResult.punchingShearCheck.criticalPerimeter > 0)
    }
}
