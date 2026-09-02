package com.civileg.app.engineering

import com.civileg.app.domain.calculations.aci.ACIBeam
import com.civileg.app.domain.calculations.ecp.ECPBeam
import com.civileg.core.calculations.entities.BarLocation
import com.civileg.core.calculations.entities.CoatingType
import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * PHASE 00 STEP 1 — Golden-number benchmark suite (spec §76–78 case format: Input / Expected / Tolerance / Code).
 *
 * Every expected value below was derived BY HAND from the governing equation, independent of the
 * implementation. See PHASE00_AUDIT.md Addendum A for defect IDs referenced in test names.
 *
 * Reference cases:
 *  ECP-E1  ECPBeam flexure   b=250,d=500, fcu=25, fy=360, Mu=200 kN.m  -> As=1545.8 mm², 5Ø20
 *  ECP-S1  ECPBeam shear     Vu=150 kN                                  -> Vc=122.47 kN, As/s=375(min governs), s=200
 *  ECP-D1  ECPBeam dev.len   Ø16 bottom/top                             -> 450 / 550 mm
 *  ACI-A1  ACIBeam flexure   b=300,d=540, fcu=30(fc'=24), fy=420, Mu=300 -> As=1640.7 mm², 6Ø19
 *  ACI-S1  ACIBeam shear     Vu=180 kN                                  -> Vc=134.92 kN, As/s=463.4
 *  ACI-D1  ACIBeam dev.len   Ø16 bottom / Ø25 top                       -> 825 / 1025 mm
 */
class BeamGoldenBenchmarkTest {

    // ------------------------------------------------------------------
    // ECP 203 — Flexure (K-method)
    // ------------------------------------------------------------------

    @Test
    fun ecpFlexure_E1_handCalculation() {
        val r = ECPBeam().calculateFlexureReinforcement(
            fcu = 25.0, fy = 360.0, width = 250.0,
            effectiveDepth = 500.0, totalDepth = 560.0,
            designMoment = 200.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        // K = 200e6/(25*250*500²) = 0.128 ; z = 500*(0.5+sqrt(0.25-K/0.893)) = 413.296 mm
        // As = 200e6/((360/1.15)*413.296) = 1545.83 mm²
        assertEquals(1545.83, r.astRequired, 1545.83 * 0.01)
        // Bar selector: first diameter with ceil(As/Abar)<=6 is Ø20 -> ceil(1545.83/314.16)=5 bars
        assertEquals(20.0, r.barDiameter, 0.001)
        assertEquals(5, r.numberOfBars)
        assertEquals(1570.80, r.astProvided, 1570.80 * 0.005)
        // Capacity check: a=176.1mm -> phiMn = 202.5 kN.m -> util = 0.9875 ; As_req < As_max -> safe
        assertEquals(0.9875, r.utilizationRatio, 0.9875 * 0.01)
        assertTrue(r.isSafe)
    }

    @Test
    fun ecpFlexure_lowMoment_governsMinimumSteel() {
        // Mu tiny -> As_req below min; ECP min ratio post-A10 = max(0.25*sqrt(25)/360, 0.0013) = 0.0034722
        // minAs = 0.0034722 * 250 * 500 = 434.03 mm²  (A10 corrected from EC2 0.26 to ECP 0.25)
        val r = ECPBeam().calculateFlexureReinforcement(
            fcu = 25.0, fy = 360.0, width = 250.0,
            effectiveDepth = 500.0, totalDepth = 560.0,
            designMoment = 5.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertEquals(434.03, r.astRequired, 434.03 * 0.01)
        assertTrue(r.warnings.any { it.contains("Minimum reinforcement") })
    }

    @Test
    fun ecpFlexure_overReinforcedSection_flagsWarningAndUnsafe() {
        // Mu = 400 kN.m -> K = 400e6/(25*250*500²) = 0.256 > K_bal = 0.18605
        val r = ECPBeam().calculateFlexureReinforcement(
            fcu = 25.0, fy = 360.0, width = 250.0,
            effectiveDepth = 500.0, totalDepth = 560.0,
            designMoment = 400.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue(r.warnings.any { it.contains("over-reinforced", ignoreCase = true) })
    }

    // ------------------------------------------------------------------
    // ECP 203 — Shear
    // ------------------------------------------------------------------

    @Test
    fun ecpShear_S1_handCalculation_minimumGoverns() {
        val r = ECPBeam().calculateShearReinforcement(
            fcu = 25.0, fy = 360.0, width = 250.0, effectiveDepth = 500.0,
            designShear = 150.0, axialLoad = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        // qcu = 0.24*sqrt(25/1.5) = 0.97980 MPa ; Vc = 0.97980*250*500/1000 = 122.47 kN
        assertEquals(122.47, r.concreteShearCapacity, 122.47 * 0.01)
        // required = (150-122.47)e3/(313.04*500)*1000 = 175.9 mm²/m < min 0.0015*250*1000 = 375 -> min governs
        assertEquals(375.0, r.requiredShearReinforcement, 375.0 * 0.01)
        assertEquals(10.0, r.stirrupDiameter, 0.001)      // width not < 250 -> Ø10
        assertEquals(200.0, r.stirrupSpacing, 0.001)      // s=418.9 coerced to 200 max
        // qmax = 0.7*sqrt(25/1.5)*250*500/1000 = 357.2 kN -> safe, util = 0.42
        assertEquals(0.42, r.utilizationRatio, 0.42 * 0.02)
        assertTrue(r.isSafe)
    }

    @Test
    fun ecpShear_overMaxStress_fails() {
        // Vu = 500 kN > qmax capacity 357.2 kN -> isSafe false with warning
        val r = ECPBeam().calculateShearReinforcement(
            fcu = 25.0, fy = 360.0, width = 250.0, effectiveDepth = 500.0,
            designShear = 500.0, axialLoad = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("maximum limit", ignoreCase = true) })
    }

    // ------------------------------------------------------------------
    // ECP 203 — Development length
    // ------------------------------------------------------------------

    @Test
    fun ecpDevelopment_D1_bottom_and_top() {
        val eng = ECPBeam()
        // Ld = (360/1.15)*16/(4*0.6*sqrt(25)) = 417.7 -> ceil to 450 ; top *= 1.3 -> 550
        assertEquals(450.0, eng.calculateDevelopmentLength(16.0, 360.0, 25.0, BarLocation.BOTTOM, CoatingType.UNCOATED), 0.001)
        assertEquals(550.0, eng.calculateDevelopmentLength(16.0, 360.0, 25.0, BarLocation.TOP, CoatingType.UNCOATED), 0.001)
    }

    // ------------------------------------------------------------------
    // ACI 318 — Flexure (Rn-rho)
    // ------------------------------------------------------------------

    @Test
    fun aciFlexure_A1_handCalculation() {
        val r = ACIBeam().calculateFlexureReinforcement(
            fcu = 30.0, fy = 420.0, width = 300.0,
            effectiveDepth = 540.0, totalDepth = 600.0,
            designMoment = 300.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        // fc'=24 ; Rn = 300e6/(0.9*300*540²) = 3.8104 ; m = 420/(0.85*24) = 20.588
        // rho = (1-sqrt(1-2*m*Rn/fy))/m = 0.0101276 ; As = 0.0101276*162000 = 1640.66 mm²
        assertEquals(1640.66, r.astRequired, 1640.66 * 0.01)
        // Selector over {12,16,19,22,...}: Ø16 needs 9 bars (>6) ; Ø19 needs 6 -> Ø19
        assertEquals(19.0, r.barDiameter, 0.001)
        assertEquals(6, r.numberOfBars)
        assertEquals(1701.17, r.astProvided, 1701.17 * 0.005)
        // a = 116.75mm ; phiMn = 309.71 kN.m ; util = 0.9687
        assertEquals(0.9687, r.utilizationRatio, 0.9687 * 0.01)
        assertTrue(r.isSafe)
    }

    @Test
    fun aciFlexure_lowMoment_minSteelIsLargerTerm_1_4_over_fy() {
        // min = max(0.25*sqrt(24)/420, 1.4/420)*bd = max(0.0029161, 0.0033333)*162000 = 540 mm²
        val r = ACIBeam().calculateFlexureReinforcement(
            fcu = 30.0, fy = 420.0, width = 300.0,
            effectiveDepth = 540.0, totalDepth = 600.0,
            designMoment = 10.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertEquals(540.0, r.astRequired, 0.5)
        assertTrue(r.warnings.any { it.contains("Minimum reinforcement") })
    }

    // ------------------------------------------------------------------
    // ACI 318 — Shear
    // ------------------------------------------------------------------

    @Test
    fun aciShear_S1_handCalculation() {
        val r = ACIBeam().calculateShearReinforcement(
            fcu = 30.0, fy = 420.0, width = 300.0, effectiveDepth = 540.0,
            designShear = 180.0, axialLoad = 0.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        // Vc = 0.17*sqrt(24)*300*540 = 134.92 kN
        assertEquals(134.92, r.concreteShearCapacity, 134.92 * 0.01)
        // Vs = (180 - 101.19)/0.75 = 105.08 kN ; Av/s = 105.08e3/(420*540)*1000 = 463.4 mm²/m
        assertEquals(463.4, r.requiredShearReinforcement, 463.4 * 0.01)
        // max capacity = phiVc + 0.75*0.66*sqrt(fc')bd = 101.19 + 392.9 = 494.1 kN -> safe
        assertEquals(0.3643, r.utilizationRatio, 0.3643 * 0.02)
        assertTrue(r.isSafe)
    }

    // ------------------------------------------------------------------
    // ACI 318 — Development length
    // ------------------------------------------------------------------

    @Test
    fun aciDevelopment_D1_db16_bottom_db25_top() {
        val eng = ACIBeam()
        // W10 CORRECTED per ACI 318-19 Table 25.4.2.5 (psi_s = 0.8 for db <= 19, else 1.0).
        // fc' = 0.8*30 = 24 ; denominator = 1.7*sqrt(24) = 8.3283
        // Ø16 bottom (psi_t=1.0, psi_s=0.8): 420*0.8/8.3283*16 = 645.5 -> round-up 650
        assertEquals(650.0, eng.calculateDevelopmentLength(16.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED), 0.001)
        // Ø25 bottom (psi_s = 1.0 — previously pinned to the inverted 0.8): 420/8.3283*25 = 1260.8 -> 1275
        assertEquals(1275.0, eng.calculateDevelopmentLength(25.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED), 0.001)
    }

    // ------------------------------------------------------------------
    // Cross-check: identical input through both codes must differ (parity guard)
    // ------------------------------------------------------------------

    @Test
    fun codes_produceDifferentRequiredSteel_forSameDemand() {
        val ecp = ECPBeam().calculateFlexureReinforcement(
            fcu = 30.0, fy = 400.0, width = 300.0, effectiveDepth = 540.0, totalDepth = 600.0,
            designMoment = 350.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        val aci = ACIBeam().calculateFlexureReinforcement(
            fcu = 30.0, fy = 400.0, width = 300.0, effectiveDepth = 540.0, totalDepth = 600.0,
            designMoment = 350.0, loadCombination = LoadCombination.DEAD_LIVE
        )
        assertFalse(abs(ecp.astRequired - aci.astRequired) < 1.0)
    }
}
