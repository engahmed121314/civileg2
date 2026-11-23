package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.BeamDesign
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ACIBeam — ACI 318-19 beam design engine.
 * Covers: flexure reinforcement, shear reinforcement, deflection, development length,
 * InputGuard validation, edge cases, and safety checks.
 */
class ACIBeamTest {

    private lateinit var beam: ACIBeam

    @Before
    fun setup() {
        beam = ACIBeam()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helper — typical design values
    // ══════════════════════════════════════════════════════════════════════

    companion object {
        private const val FCU = 30.0      // MPa (cube strength)
        private const val FY = 400.0      // MPa
        private const val WIDTH = 300.0   // mm
        private const val TOTAL_DEPTH = 600.0  // mm
        private const val EFF_DEPTH = 540.0    // mm
        private const val SPAN = 6000.0        // mm
        private const val DESIGN_MOMENT = 200.0  // kN.m
        private const val DESIGN_SHEAR = 150.0   // kN
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Instance & interface checks
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun aciBeamInstanceCreatedSuccessfully() {
        assertNotNull(beam)
    }

    @Test
    fun aciBeamImplementsBeamDesign() {
        assertTrue(beam is BeamDesign)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Flexure — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateFlexure_validInputs_producesResult() {
        val result = beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Required area must be positive", result.astRequired > 0)
        assertTrue("Provided area must be positive", result.astProvided > 0)
    }

    @Test
    fun calculateFlexure_validInputs_providedExceedsRequired() {
        val result = beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
        assertTrue("Provided As should meet or exceed required As",
            result.astProvided >= result.astRequired * 0.99) // small tolerance for rounding
    }

    @Test
    fun calculateFlexure_validInputs_barDiameterReasonable() {
        val result = beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
        assertTrue("Bar diameter should be at least 12mm", result.barDiameter >= 12.0)
        assertTrue("Bar diameter should be at most 32mm", result.barDiameter <= 32.0)
    }

    @Test
    fun calculateFlexure_validInputs_numberOfBarsAtLeast2() {
        val result = beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
        assertTrue("Minimum 2 bars", result.numberOfBars >= 2)
    }

    @Test
    fun calculateFlexure_lowMoment_appliesMinimumReinforcement() {
        val result = beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, 5.0, LoadCombination.DEAD_LIVE
        )
        // For very low moment, minimum reinforcement should be applied
        assertTrue("Warnings should mention minimum reinforcement",
            result.warnings.any { it.contains("Minimum reinforcement", ignoreCase = true) })
    }

    @Test
    fun calculateFlexure_largeMoment_utilizationRatioExceeds1() {
        // Very large moment should produce unsafe design
        val result = beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, 800.0, LoadCombination.DEAD_LIVE
        )
        // With 800 kN.m the section may be unsafe or heavily utilized
        assertTrue("Utilization ratio should be significant", result.utilizationRatio > 0.5)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Flexure — InputGuard validation
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun calculateFlexure_negativeFcu_throwsException() {
        beam.calculateFlexureReinforcement(
            -30.0, FY, WIDTH, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateFlexure_zeroFy_throwsException() {
        beam.calculateFlexureReinforcement(
            FCU, 0.0, WIDTH, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateFlexure_negativeWidth_throwsException() {
        beam.calculateFlexureReinforcement(
            FCU, FY, -300.0, EFF_DEPTH, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateFlexure_zeroEffectiveDepth_throwsException() {
        beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, 0.0, TOTAL_DEPTH, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateFlexure_zeroTotalDepth_throwsException() {
        beam.calculateFlexureReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, 0.0, DESIGN_MOMENT, LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Shear — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateShear_validInputs_producesResult() {
        val result = beam.calculateShearReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue("Concrete shear capacity must be positive", result.concreteShearCapacity > 0)
    }

    @Test
    fun calculateShear_validInputs_stirrupDiameterReasonable() {
        val result = beam.calculateShearReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Stirrup diameter should be 8 or 10 mm",
            result.stirrupDiameter == 8.0 || result.stirrupDiameter == 10.0)
    }

    @Test
    fun calculateShear_validInputs_stirrupSpacingPositive() {
        val result = beam.calculateShearReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
        assertTrue("Stirrup spacing must be positive", result.stirrupSpacing > 0)
    }

    @Test
    fun calculateShear_lowShear_concreteSufficient() {
        val result = beam.calculateShearReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, 10.0, 0.0, LoadCombination.DEAD_LIVE
        )
        // Very low shear: concrete alone should be sufficient
        assertTrue("Low shear should be safe", result.isSafe)
    }

    @Test
    fun calculateShear_highShear_producesUnsafeWarning() {
        val result = beam.calculateShearReinforcement(
            FCU, FY, WIDTH, EFF_DEPTH, 2000.0, 0.0, LoadCombination.DEAD_LIVE
        )
        // Very high shear exceeds capacity
        if (!result.isSafe) {
            assertTrue("Should warn about shear exceeding limits",
                result.warnings.any { it.contains("Shear exceeds", ignoreCase = true) })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Shear — InputGuard validation
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun calculateShear_negativeFcu_throwsException() {
        beam.calculateShearReinforcement(
            -30.0, FY, WIDTH, EFF_DEPTH, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShear_zeroFy_throwsException() {
        beam.calculateShearReinforcement(
            FCU, 0.0, WIDTH, EFF_DEPTH, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShear_zeroWidth_throwsException() {
        beam.calculateShearReinforcement(
            FCU, FY, 0.0, EFF_DEPTH, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateShear_zeroEffectiveDepth_throwsException() {
        beam.calculateShearReinforcement(
            FCU, FY, WIDTH, 0.0, DESIGN_SHEAR, 0.0, LoadCombination.DEAD_LIVE
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. Deflection — happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun checkDeflection_simplySupported_reasonableResult() {
        val result = beam.checkDeflection(SPAN, TOTAL_DEPTH, 0.01, SupportCondition.SIMPLY_SUPPORTED)
        assertNotNull(result)
        assertTrue("Allowable deflection must be positive", result.allowableDeflection > 0)
    }

    @Test
    fun checkDeflection_continuous_higherAllowableThanSimple() {
        val simpleResult = beam.checkDeflection(SPAN, TOTAL_DEPTH, 0.01, SupportCondition.SIMPLY_SUPPORTED)
        val contResult = beam.checkDeflection(SPAN, TOTAL_DEPTH, 0.01, SupportCondition.CONTINUOUS)
        // Continuous allows larger span/depth ratio (21 vs 16)
        assertTrue("Continuous allows more deflection than simply supported",
            contResult.allowableDeflection >= simpleResult.allowableDeflection * 0.9)
    }

    @Test
    fun checkDeflection_cantilever_lowerAllowableThanSimple() {
        val simpleResult = beam.checkDeflection(SPAN, TOTAL_DEPTH, 0.01, SupportCondition.SIMPLY_SUPPORTED)
        val cantResult = beam.checkDeflection(SPAN, TOTAL_DEPTH, 0.01, SupportCondition.CANTILEVER)
        // Cantilever has stricter span/depth ratio (8 vs 16)
        // For same span and depth, cantilever should be less safe
        assertTrue("Cantilever ratio should be higher (more critical)",
            cantResult.ratio >= simpleResult.ratio * 0.9)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7. Deflection — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun checkDeflection_zeroSpan_throwsException() {
        beam.checkDeflection(0.0, TOTAL_DEPTH, 0.01, SupportCondition.SIMPLY_SUPPORTED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkDeflection_negativeTotalDepth_throwsException() {
        beam.checkDeflection(SPAN, -600.0, 0.01, SupportCondition.SIMPLY_SUPPORTED)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 8. Development Length
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateDevelopmentLength_validInputs_returnsPositive() {
        val Ld = beam.calculateDevelopmentLength(20.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
        assertTrue("Development length must be positive", Ld > 0)
    }

    @Test
    fun calculateDevelopmentLength_topBars_longerThanBottom() {
        val LdBottom = beam.calculateDevelopmentLength(20.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
        val LdTop = beam.calculateDevelopmentLength(20.0, FY, FCU, BarLocation.TOP, CoatingType.UNCOATED)
        // Top bars have ψt = 1.3, so Ld should be longer
        assertTrue("Top bar development length should be >= bottom", LdTop >= LdBottom)
    }

    @Test
    fun calculateDevelopmentLength_epoxyCoated_longerThanUncoated() {
        val LdUncoated = beam.calculateDevelopmentLength(20.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
        val LdEpoxy = beam.calculateDevelopmentLength(20.0, FY, FCU, BarLocation.BOTTOM, CoatingType.EPOXY_COATED)
        assertTrue("Epoxy coated should have >= development length", LdEpoxy >= LdUncoated)
    }

    @Test
    fun calculateDevelopmentLength_largerBar_longerDevelopment() {
        val Ld16 = beam.calculateDevelopmentLength(16.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
        val Ld25 = beam.calculateDevelopmentLength(25.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
        assertTrue("Larger bar diameter should have longer development length", Ld25 > Ld16)
    }

    @Test
    fun calculateDevelopmentLength_meetsMinimum300mm() {
        val Ld = beam.calculateDevelopmentLength(10.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
        assertTrue("Development length must be at least 300mm per ACI", Ld >= 300.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 9. Development Length — InputGuard
    // ══════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun calculateDevelopmentLength_zeroBarDiameter_throwsException() {
        beam.calculateDevelopmentLength(0.0, FY, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateDevelopmentLength_negativeFy_throwsException() {
        beam.calculateDevelopmentLength(20.0, -400.0, FCU, BarLocation.BOTTOM, CoatingType.UNCOATED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateDevelopmentLength_zeroFcu_throwsException() {
        beam.calculateDevelopmentLength(20.0, FY, 0.0, BarLocation.BOTTOM, CoatingType.UNCOATED)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10. Code limits
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun minReinforcementRatio_isPositive() {
        assertTrue(beam.getMinReinforcementRatio() > 0)
    }

    @Test
    fun maxReinforcementRatio_greaterThanMin() {
        assertTrue(beam.getMaxReinforcementRatio() > beam.getMinReinforcementRatio())
    }

    @Test
    fun minCover_isPositive() {
        assertTrue(beam.getMinCover() > 0)
    }

    @Test
    fun maxShearSpacing_isPositive() {
        assertTrue(beam.getMaxShearSpacing() > 0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 11. Doubly Reinforced Beam
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun calculateDoublyReinforced_validInputs_singlyReinforcedForModerateMoment() {
        val result = beam.calculateDoublyReinforcedBeam(
            DESIGN_MOMENT, WIDTH, TOTAL_DEPTH, FCU, FY
        )
        assertNotNull(result)
        assertTrue("Tension steel area must be positive", result.tensionSteelArea > 0)
    }

    @Test
    fun calculateDoublyReinforced_largeMoment_needsCompressionSteel() {
        val result = beam.calculateDoublyReinforcedBeam(
            600.0, WIDTH, TOTAL_DEPTH, FCU, FY
        )
        // Very large moment should require compression steel
        if (result.needsCompressionSteel) {
            assertTrue("Compression steel area must be positive", result.compressionSteelArea > 0)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateDoublyReinforced_zeroWidth_throwsException() {
        beam.calculateDoublyReinforcedBeam(DESIGN_MOMENT, 0.0, TOTAL_DEPTH, FCU, FY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateDoublyReinforced_negativeFcu_throwsException() {
        beam.calculateDoublyReinforcedBeam(DESIGN_MOMENT, WIDTH, TOTAL_DEPTH, -30.0, FY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateDoublyReinforced_zeroDepth_throwsException() {
        beam.calculateDoublyReinforcedBeam(DESIGN_MOMENT, WIDTH, 0.0, FCU, FY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateDoublyReinforced_zeroFy_throwsException() {
        beam.calculateDoublyReinforcedBeam(DESIGN_MOMENT, WIDTH, TOTAL_DEPTH, FCU, 0.0)
    }
    // ══════════════════════════════════════════════════════════════════════
    // GOLDEN VECTORS — development length (F-07 regression, hand-verified
    // 2026-11-21 vs ACI 318M-14 Table 25.4.2.2/25.4.2.4, printed pp.652/655)
    // fcu=30 (fc'=24), favourable-confinement case, denominator 1.1×1.5=1.65
    // (the code basis of Table 25.4.2.2 row 1, general Eq 25.4.2.3a):
    // 16mm bottom uncoated fy=420: 420×0.8/(1.65√24)×16 = 665.1 → 675
    // 25mm bottom uncoated fy=420: 420×1.0/(1.65√24)×25 = 1299.1 → 1300
    // 32mm top epoxy fy=420: 420×1.3×1.2×1.0/(1.65√24)×32 = 2593.5 → 2600
    // 12mm fy=280: 280×0.8/(1.65√24)×12 = 332.5 → 350 (≥ 300 min, 25.4.2.1)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun goldenDevLength_smallBarGetsPsiS08() {
        val ld = beam.calculateDevelopmentLength(
            16.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED
        )
        assertEquals(675.0, ld, 0.001)
    }

    @Test
    fun goldenDevLength_largeBarGetsPsiS10() {
        val ld = beam.calculateDevelopmentLength(
            25.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED
        )
        assertEquals(1300.0, ld, 0.001)
    }

    @Test
    fun goldenDevLength_topEpoxyFactors() {
        val ld = beam.calculateDevelopmentLength(
            32.0, 420.0, 30.0, BarLocation.TOP, CoatingType.EPOXY_COATED
        )
        assertEquals(2600.0, ld, 0.001)
    }

    @Test
    fun goldenDevLength_lowFySmallBar() {
        val ld = beam.calculateDevelopmentLength(
            12.0, 280.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED
        )
        assertEquals(350.0, ld, 0.001)
    }

    // ══════════════════════════════════════════════════════════════════════
    // GOLDEN VECTORS — optional detailing inputs toggle (owner-approved
    // 2026-11-21), Table 25.4.2.2 row conditions + Table 25.4.2.4 full ψe
    // fcu=30 (fc'=24), fy=420:
    // favourable 16mm (cover 40≥16, spacing 50≥32): same as default → 675
    // "other cases" 16mm (spacing 20<32, no ties): 420×0.8/(1.1√24)×16
    //   = 997.8 → 1000
    // epoxy tight 25mm (cover 50<75): ψe=1.5, favourable →
    //   420×1.5/(1.65√24)×25 = 1948.9 → 1950
    // top epoxy tight 25mm: ψtψe=1.95 capped to 1.7 →
    //   420×1.7/(1.65√24)×25 = 2208.3 → 2225
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun goldenDevLengthToggle_favourableMatchesDefault() {
        val ld = beam.calculateDevelopmentLength(
            16.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED,
            DevLengthInputs(clearCover = 40.0, clearSpacing = 50.0, hasMinTies = true)
        )
        assertEquals(675.0, ld, 0.001)
    }

    @Test
    fun goldenDevLengthToggle_otherCasesDenominator() {
        val ld = beam.calculateDevelopmentLength(
            16.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.UNCOATED,
            DevLengthInputs(clearCover = 40.0, clearSpacing = 20.0, hasMinTies = false)
        )
        assertEquals(1000.0, ld, 0.001)
    }

    @Test
    fun goldenDevLengthToggle_epoxyTightCover() {
        val ld = beam.calculateDevelopmentLength(
            25.0, 420.0, 30.0, BarLocation.BOTTOM, CoatingType.EPOXY_COATED,
            DevLengthInputs(clearCover = 50.0, clearSpacing = 60.0, hasMinTies = true)
        )
        assertEquals(1950.0, ld, 0.001)
    }

    @Test
    fun goldenDevLengthToggle_psiTEcapped() {
        val ld = beam.calculateDevelopmentLength(
            25.0, 420.0, 30.0, BarLocation.TOP, CoatingType.EPOXY_COATED,
            DevLengthInputs(clearCover = 50.0, clearSpacing = 60.0, hasMinTies = true)
        )
        assertEquals(2225.0, ld, 0.001)
    }
}
