package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.EdgeCondition
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabSupportConditions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden vectors for the ECP 203-2020 two-way slab moment method.
 *
 * Clause refs (docs/CLAUSE_LOG.md TW-01..TW-05, VERIFIED from the owner's
 * ECP 203-2020 PDF, printed pages 6-9/6-10):
 *  - §6-1-2-6-3, Eq. 6-7a/b/c: M = coeff·w·L²/d, d = 8 / 10 / 12 by continuity
 *  - Table 6-1 / Eq. 6-8: alpha = 0.5r − 0.15, beta = 0.35/r²
 *
 * Panel: short span a = 5000 mm, long span b = 6000 mm → r = 1.2
 *   alpha = 0.5·1.2 − 0.15 = 0.45
 *   beta  = 0.35 / 1.44    = 0.2430556
 */
class ECPSlabTwoWayTable61Test {

    private val slab = ECPSlab()
    private val tol = 1e-6

    private fun coeffs(supports: SlabSupportConditions) = slab.designTwoWaySlab(
        25.0, 360.0, 200.0, 5000.0, 6000.0,
        supports, 12.0, LoadCombination.DEAD_LIVE
    ).momentCoefficients

    private fun supports(
        a: EdgeCondition, b: EdgeCondition, c: EdgeCondition, d: EdgeCondition
    ) = SlabSupportConditions(a, b, c, d)

    @Test
    fun `table61_r1_2_allContinuous_matchesEq6-7c`() {
        // All four edges fixed → short span d=12, long span d=12 (Eq 6-7c)
        val c = coeffs(
            supports(
                EdgeCondition.FIXED, EdgeCondition.FIXED,
                EdgeCondition.FIXED, EdgeCondition.FIXED
            )
        )
        assertEquals(0.45 / 12.0, c.positiveShort, tol)   // 0.0375
        assertEquals(0.2430556 / 12.0, c.positiveLong, tol) // 0.02025463
        assertEquals(c.positiveShort, c.negativeShort, tol)
        assertEquals(c.positiveLong, c.negativeLong, tol)
    }

    @Test
    fun `table61_r1_2_simplySupported_matchesEq6-7a`() {
        // All four edges simply supported → d=8 both directions (Eq 6-7a), no negatives
        val c = coeffs(
            supports(
                EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
                EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
            )
        )
        assertEquals(0.45 / 8.0, c.positiveShort, tol)     // 0.05625
        assertEquals(0.2430556 / 8.0, c.positiveLong, tol) // 0.03038194
        assertEquals(0.0, c.negativeShort, tol)
        assertEquals(0.0, c.negativeLong, tol)
    }

    @Test
    fun `table61_r1_2_shortSpanContinuousLongSimple_mixedDenominators`() {
        // Long edges A,C fixed (short span continuous both ends → 12);
        // short edges B,D simple (long span simply supported → 8)
        val c = coeffs(
            supports(
                EdgeCondition.FIXED, EdgeCondition.SIMPLY_SUPPORTED,
                EdgeCondition.FIXED, EdgeCondition.SIMPLY_SUPPORTED
            )
        )
        assertEquals(0.45 / 12.0, c.positiveShort, tol)
        assertEquals(0.2430556 / 8.0, c.positiveLong, tol)
        assertEquals(c.positiveShort, c.negativeShort, tol)
        assertEquals(0.0, c.negativeLong, tol)
    }

    @Test
    fun `table61_r1_2_oneEndContinuous_matchesEq6-7b`() {
        // Edge A fixed, C simple → short span continuous one end → d=10 (Eq 6-7b)
        val c = coeffs(
            supports(
                EdgeCondition.FIXED, EdgeCondition.SIMPLY_SUPPORTED,
                EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
            )
        )
        assertEquals(0.45 / 10.0, c.positiveShort, tol)    // 0.045
        assertEquals(c.positiveShort, c.negativeShort, tol)
    }

    @Test
    fun `table61_r1_0_squarePanel_alphaEqualsBeta`() {
        // Square panel (a = b = 5000): alpha = beta = 0.35 (Table 6-1, r = 1.0)
        val c = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 5000.0,
            supports(
                EdgeCondition.FIXED, EdgeCondition.FIXED,
                EdgeCondition.FIXED, EdgeCondition.FIXED
            ),
            12.0, LoadCombination.DEAD_LIVE
        ).momentCoefficients
        assertEquals(0.35 / 12.0, c.positiveShort, tol) // 0.02916667
        assertEquals(0.35 / 12.0, c.positiveLong, tol)
    }

    @Test
    fun `table61_r2_0_boundary_matchesEq6-8`() {
        // r = 2.0 (a = 5000, b = 10000): alpha = 0.85, beta = 0.0875 (Table 6-1)
        val c = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 10000.0,
            supports(
                EdgeCondition.FIXED, EdgeCondition.FIXED,
                EdgeCondition.FIXED, EdgeCondition.FIXED
            ),
            12.0, LoadCombination.DEAD_LIVE
        ).momentCoefficients
        assertEquals(0.85 / 12.0, c.positiveShort, tol)   // 0.07083333
        assertEquals(0.0875 / 12.0, c.positiveLong, tol)  // 0.00729167
    }

    @Test
    fun `table61_rAbove2_clampedToMethodScope`() {
        // r > 2 is outside the method's scope (§6-2-1-6-1: design one-way);
        // engine clamps to the r = 2.0 boundary values
        val wide = slab.designTwoWaySlab(
            25.0, 360.0, 200.0, 5000.0, 13000.0,
            supports(
                EdgeCondition.FIXED, EdgeCondition.FIXED,
                EdgeCondition.FIXED, EdgeCondition.FIXED
            ),
            12.0, LoadCombination.DEAD_LIVE
        ).momentCoefficients
        assertEquals(0.85 / 12.0, wide.positiveShort, tol)
        assertEquals(0.0875 / 12.0, wide.positiveLong, tol)
    }
}
