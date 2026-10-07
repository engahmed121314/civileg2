package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ColumnDesignEngine — the comprehensive column design engine.
 * Covers: K-factor lookup, column design with ECP and ACI codes,
 * slenderness classification, InputGuard validation, and safety checks.
 */
class ColumnDesignEngineTest {

    // ══════════════════════════════════════════════════════════════════════
    // 1. K-Factor — Braced
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun getKFactor_bracedFixedFixed_returns075() {
        val k = ColumnDesignEngine.getKFactor(true, 1, 1)
        assertEquals(0.75, k, 1e-6)
    }

    @Test
    fun getKFactor_bracedFixedHinged_returns080() {
        val k = ColumnDesignEngine.getKFactor(true, 1, 2)
        assertEquals(0.80, k, 1e-6)
    }

    @Test
    fun getKFactor_bracedHingedHinged_returns085() {
        val k = ColumnDesignEngine.getKFactor(true, 2, 2)
        assertEquals(0.85, k, 1e-6)
    }

    @Test
    fun getKFactor_bracedFreeFixed_returns220() {
        val k = ColumnDesignEngine.getKFactor(false, 4, 1)
        assertEquals(2.20, k, 1e-6)
    }

    @Test
    fun getKFactor_bracedSymmetricConditions_sameResult() {
        // (1,2) and (2,1) should give same K because key is (min, max)
        val k12 = ColumnDesignEngine.getKFactor(true, 1, 2)
        val k21 = ColumnDesignEngine.getKFactor(true, 2, 1)
        assertEquals(k12, k21, 1e-6)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. K-Factor — Unbraced
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun getKFactor_unbracedFixedFixed_returns120() {
        val k = ColumnDesignEngine.getKFactor(false, 1, 1)
        assertEquals(1.20, k, 1e-6)
    }

    @Test
    fun getKFactor_unbracedHingedHinged_returns150() {
        val k = ColumnDesignEngine.getKFactor(false, 2, 2)
        assertEquals(1.50, k, 1e-6)
    }

    @Test
    fun getKFactor_unbracedHigherThanBraced() {
        val kBraced = ColumnDesignEngine.getKFactor(true, 1, 1)
        val kUnbraced = ColumnDesignEngine.getKFactor(false, 1, 1)
        assertTrue("Unbraced K should be >= braced K", kUnbraced >= kBraced)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. K-Factor — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun getKFactor_topCondOutOfRange_throwsException() {
        ColumnDesignEngine.getKFactor(true, 0, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getKFactor_botCondOutOfRange_throwsException() {
        ColumnDesignEngine.getKFactor(true, 1, 5)
    }

    @Test
    fun getKFactor_boundaryValues_valid() {
        // 1 and 4 are valid boundary values
        ColumnDesignEngine.getKFactor(true, 1, 1)
        ColumnDesignEngine.getKFactor(true, 4, 4)
        ColumnDesignEngine.getKFactor(false, 1, 4)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. designColumn — ECP happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designColumn_ecpShortColumn_producesResult() {
        val result = ColumnDesignEngine.designColumn(
            b = 300.0, t = 300.0, H = 3000.0,
            beamDepthIn = 500.0, beamDepthOut = 500.0,
            cover = 40.0,
            Pu = 1200.0, MextIn = 40.0, MextOut = 0.0,
            fcu = 30.0, fy = 400.0,
            isBraced = true,
            topCond = 1, botCond = 1,
            preferredDia = 16,
            code = DesignCode.ECP
        )
        assertNotNull(result)
        assertEquals("ECP", DesignCode.ECP, result.designCode)
    }

    @Test
    fun designColumn_ecpShortColumn_classifiedAsShort() {
        val result = ColumnDesignEngine.designColumn(
            b = 400.0, t = 400.0, H = 3000.0,
            beamDepthIn = 600.0, beamDepthOut = 600.0,
            cover = 40.0,
            Pu = 1500.0, MextIn = 50.0, MextOut = 0.0,
            fcu = 30.0, fy = 400.0,
            isBraced = true,
            topCond = 1, botCond = 1,
            preferredDia = 20,
            code = DesignCode.ECP
        )
        // 400x400, H=3m, K=0.75, Ho≈2400, λ=0.75*2400/400=4.5 → short
        assertEquals("Short", result.columnClassification)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. designColumn — ACI happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designColumn_aciShortColumn_producesResult() {
        val result = ColumnDesignEngine.designColumn(
            b = 300.0, t = 300.0, H = 3000.0,
            beamDepthIn = 500.0, beamDepthOut = 500.0,
            cover = 40.0,
            Pu = 1200.0, MextIn = 40.0, MextOut = 0.0,
            fcu = 30.0, fy = 400.0,
            isBraced = true,
            topCond = 1, botCond = 1,
            preferredDia = 16,
            code = DesignCode.ACI
        )
        assertNotNull(result)
        assertEquals(DesignCode.ACI, result.designCode)
    }

    @Test
    fun designColumn_aci_reinforcementProvided() {
        val result = ColumnDesignEngine.designColumn(
            b = 300.0, t = 300.0, H = 3000.0,
            beamDepthIn = 500.0, beamDepthOut = 500.0,
            cover = 40.0,
            Pu = 1200.0, MextIn = 40.0, MextOut = 0.0,
            fcu = 30.0, fy = 400.0,
            isBraced = true,
            topCond = 1, botCond = 1,
            preferredDia = 16,
            code = DesignCode.ACI
        )
        assertTrue("As provided must be positive", result.AsProvided > 0)
        assertTrue("As required must be positive", result.AsRequired > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. designColumn — Slenderness
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designColumn_tallSlenderColumn_classifiedAsLongOrUnsafe() {
        val result = ColumnDesignEngine.designColumn(
            b = 200.0, t = 200.0, H = 6000.0,
            beamDepthIn = 300.0, beamDepthOut = 300.0,
            cover = 40.0,
            Pu = 500.0, MextIn = 20.0, MextOut = 0.0,
            fcu = 25.0, fy = 400.0,
            isBraced = true,
            topCond = 3, botCond = 3,
            preferredDia = 16,
            code = DesignCode.ECP
        )
        // 200x200, H=6m, K=1.0 (hinged-hinged), Ho=5700, λ=5700/200=28.5
        assertTrue("Should be Long or Unsafe_Slender",
            result.columnClassification in listOf("Long", "Unsafe_Slender"))
    }

    @Test
    fun designColumn_unsafeSlenderColumn_isSafeFalse() {
        val result = ColumnDesignEngine.designColumn(
            b = 150.0, t = 150.0, H = 8000.0,
            beamDepthIn = 200.0, beamDepthOut = 200.0,
            cover = 30.0,
            Pu = 200.0, MextIn = 5.0, MextOut = 0.0,
            fcu = 25.0, fy = 400.0,
            isBraced = true,
            topCond = 3, botCond = 3,
            preferredDia = 12,
            code = DesignCode.ACI
        )
        assertFalse("Unsafe slender column should not be safe", result.isSafe)
    }

    @Test
    fun designColumn_longColumn_hasAdditionalMoment() {
        val result = ColumnDesignEngine.designColumn(
            b = 250.0, t = 250.0, H = 5000.0,
            beamDepthIn = 400.0, beamDepthOut = 400.0,
            cover = 40.0,
            Pu = 800.0, MextIn = 30.0, MextOut = 0.0,
            fcu = 30.0, fy = 400.0,
            isBraced = true,
            topCond = 2, botCond = 2,
            preferredDia = 16,
            code = DesignCode.ECP
        )
        // If classified as Long, should have additional moment
        if (result.columnClassification == "Long") {
            assertTrue("Long column should have additional moment",
                result.MaddIn > 0 || result.MaddOut > 0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. designColumn — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designColumn_zeroB_throwsException() {
        ColumnDesignEngine.designColumn(
            0.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ECP
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designColumn_negativeFcu_throwsException() {
        ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, -30.0, 400.0,
            true, 1, 1, 16, DesignCode.ECP
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designColumn_zeroFy_throwsException() {
        ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 0.0,
            true, 1, 1, 16, DesignCode.ECP
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designColumn_zeroH_throwsException() {
        ColumnDesignEngine.designColumn(
            300.0, 300.0, 0.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ECP
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designColumn_zeroPreferredDia_throwsException() {
        ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 0, DesignCode.ECP
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. designColumn — Results integrity
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designColumn_resultHasCalculationSteps() {
        val result = ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ECP
        )
        assertTrue("Should have calculation steps", result.calculationSteps.isNotEmpty())
    }

    @Test
    fun designColumn_kFactorInRange() {
        val result = ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ACI
        )
        assertTrue("K factor should be positive", result.KfactorIn > 0)
        assertTrue("K factor should be reasonable (< 3.0)", result.KfactorIn < 3.0)
    }

    @Test
    fun designColumn_slendernessRatiosPositive() {
        val result = ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ACI
        )
        assertTrue("Lambda in should be positive", result.lambdaIn > 0)
        assertTrue("Lambda out should be positive", result.lambdaOut > 0)
    }

    @Test
    fun designColumn_tieSpacingPositive() {
        val result = ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            1200.0, 40.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ACI
        )
        assertTrue("Tie spacing max should be positive", result.tieSpacingMax > 0)
        assertTrue("Tie spacing dense should be positive", result.tieSpacingDense > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. designColumn — Higher load → more reinforcement
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun designColumn_higherLoad_moreReinforcement() {
        val resultLow = ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            800.0, 30.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ACI
        )
        val resultHigh = ColumnDesignEngine.designColumn(
            300.0, 300.0, 3000.0, 500.0, 500.0, 40.0,
            2500.0, 30.0, 0.0, 30.0, 400.0,
            true, 1, 1, 16, DesignCode.ACI
        )
        assertTrue("Higher load should require more reinforcement",
            resultHigh.AsRequired >= resultLow.AsRequired)
    }
}
