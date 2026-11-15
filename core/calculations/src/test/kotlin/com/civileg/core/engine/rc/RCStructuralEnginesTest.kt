package com.civileg.core.engine.rc

import com.civileg.core.engine.exceptions.OverReinforcedException
import com.civileg.core.engine.exceptions.RebarCongestionException
import com.civileg.core.engine.exceptions.SectionUnsafeShearException
import com.civileg.core.engine.rc.aci.ACI318Engine
import com.civileg.core.engine.rc.ecp.ECP203Engine
import com.civileg.core.engine.rc.sbc.SBC304Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * اختبارات التدقيق الهندسي الصارمة للمحركات الخرسانية (Zero-Tolerance Verification Gate).
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالحلول التحليلية المعتمدة.
 */
class RCStructuralEnginesTest {

    private val tolerance = 0.001 // 0.1% tolerance

    // =========================================================================
    // 1. ECP 203 Engine Tests
    // =========================================================================

    @Test
    fun testECP203BeamFlexure_ExactFormulasAndAccuracy() {
        val input = ECP203Engine.BeamFlexureInput(
            muKnM = 200.0,
            fcuMpa = 30.0,
            fyMpa = 400.0,
            widthMm = 300.0,
            depthMm = 550.0,
            coverMm = 40.0,
            barDiameterMm = 16.0,
            stirrupDiameterMm = 8.0
        )

        val result = ECP203Engine.designBeamFlexure(input)

        // Ru = Mu / (fcu * b * d²) = 200e6 / (30 * 300 * 550²) = 0.0734876
        val expectedRu = 200e6 / (30.0 * 300.0 * 550.0 * 550.0)
        assertEquals(expectedRu, result.ru, expectedRu * tolerance)

        // μ = (1 / 1.15) * (1 - √(1 - 2 * Ru * 1.15 / 1.5))
        val expectedDiscriminant = 1.0 - (2.0 * expectedRu * 1.15 / 1.5)
        val expectedMu = (1.0 / 1.15) * (1.0 - Math.sqrt(expectedDiscriminant))
        assertEquals(expectedMu, result.muRatio, expectedMu * tolerance)

        // As = μ * (fcu / fy) * b * d
        val expectedAs = expectedMu * (30.0 / 400.0) * 300.0 * 550.0
        assertEquals(expectedAs, result.asRequiredMm2, expectedAs * tolerance)

        assertTrue("Beam must be safe", result.isSafe)
        assertTrue("As provided must cover As required", result.asProvidedMm2 >= result.asRequiredMm2)
        assertTrue("Clear spacing must be >= 25mm", result.clearSpacingMm >= 25.0)
    }

    @Test(expected = OverReinforcedException::class)
    fun testECP203BeamFlexure_ThrowsOverReinforcedException() {
        val input = ECP203Engine.BeamFlexureInput(
            muKnM = 900.0, // Extremely high moment for small section
            fcuMpa = 25.0,
            fyMpa = 400.0,
            widthMm = 200.0,
            depthMm = 400.0
        )
        ECP203Engine.designBeamFlexure(input)
    }

    @Test(expected = RebarCongestionException::class)
    fun testECP203BeamFlexure_ThrowsRebarCongestionException() {
        val input = ECP203Engine.BeamFlexureInput(
            muKnM = 260.0,
            fcuMpa = 30.0,
            fyMpa = 400.0,
            widthMm = 200.0, // Narrow width cannot accommodate required 16mm bars in single layer
            depthMm = 550.0,
            coverMm = 50.0,
            barDiameterMm = 16.0,
            stirrupDiameterMm = 10.0
        )
        ECP203Engine.designBeamFlexure(input)
    }

    @Test
    fun testECP203BeamShear_ValidAndMaxLimits() {
        val input = ECP203Engine.BeamShearInput(
            quKn = 120.0,
            fcuMpa = 30.0,
            widthMm = 250.0,
            depthMm = 500.0
        )

        val result = ECP203Engine.checkBeamShear(input)
        val expectedQu = (120.0 * 1000.0) / (250.0 * 500.0) // 0.96 MPa
        assertEquals(expectedQu, result.quMpa, 0.001)

        val expectedQcu = 0.24 * Math.sqrt(30.0 / 1.5) // ~1.073 MPa
        assertEquals(expectedQcu, result.qcuMpa, 0.005)
        assertTrue("Shear must be safe", result.isSafe)
    }

    @Test(expected = SectionUnsafeShearException::class)
    fun testECP203BeamShear_ThrowsSectionUnsafeShearException() {
        val input = ECP203Engine.BeamShearInput(
            quKn = 600.0, // Extreme shear exceeding qu_max
            fcuMpa = 25.0,
            widthMm = 200.0,
            depthMm = 400.0
        )
        ECP203Engine.checkBeamShear(input)
    }

    @Test
    fun testECP203ShortColumnCapacity_ExactBenchmark() {
        val input = ECP203Engine.ShortColumnInput(
            acMm2 = 180_000.0, // 300x600 mm
            astMm2 = 1_608.5,  // 8Ø16
            fcuMpa = 30.0,
            fyMpa = 400.0
        )

        val capacityKn = ECP203Engine.calculateShortColumnCapacityKn(input)
        // Pu,max = 0.35 * 30 * (180000 - 1608.5) + 0.67 * 400 * 1608.5 = 1,873,110.75 + 431,078 = 2,304.188 kN
        val expectedPuKn = (0.35 * 30.0 * (180_000.0 - 1608.5) + 0.67 * 400.0 * 1608.5) / 1000.0
        assertEquals(expectedPuKn, capacityKn, expectedPuKn * tolerance)
    }

    @Test
    fun testECP203SlenderColumn_AdditionalMomentCalculation() {
        val input = ECP203Engine.SlenderColumnInput(
            puKn = 1000.0,
            muKnM = 50.0,
            bMm = 250.0,
            clearHeightMm = 4500.0,
            effectiveLengthFactorK = 1.0,
            isBraced = true
        )

        val result = ECP203Engine.analyzeSlenderColumn(input)
        // lambda = 4500 / 250 = 18.0 > 15 -> isSlender = true
        assertEquals(18.0, result.slendernessRatioLambda, 0.01)
        assertTrue(result.isSlender)

        // delta = 18² * 250 / 2000 = 40.5 mm
        assertEquals(40.5, result.deflectionMm, 0.01)

        // Madd = Pu * delta = 1000 * 0.0405 = 40.5 kN.m
        assertEquals(40.5, result.additionalMomentKnM, 0.01)

        // Mtotal = 50 + 40.5 = 90.5 kN.m
        assertEquals(90.5, result.totalDesignMomentKnM, 0.01)
    }

    // =========================================================================
    // 2. ACI 318M-19 Engine Tests
    // =========================================================================

    @Test
    fun testACI318WhitneyBeta1_Steps() {
        assertEquals(0.85, ACI318Engine.calculateBeta1(25.0), 0.001)
        assertEquals(0.85, ACI318Engine.calculateBeta1(28.0), 0.001)
        // fc' = 35 -> 0.85 - 0.05*(7/7) = 0.80
        assertEquals(0.80, ACI318Engine.calculateBeta1(35.0), 0.001)
        // fc' = 42 -> 0.75
        assertEquals(0.75, ACI318Engine.calculateBeta1(42.0), 0.001)
        // fc' = 60 -> min 0.65
        assertEquals(0.65, ACI318Engine.calculateBeta1(60.0), 0.001)
    }

    @Test
    fun testACI318PhiFlexure_Transitions() {
        val fy = 420.0
        val ey = fy / 200_000.0 // 0.0021

        assertEquals(0.65, ACI318Engine.calculatePhiFlexure(0.0015, fy), 0.001)
        assertEquals(0.90, ACI318Engine.calculatePhiFlexure(0.0055, fy), 0.001)

        // Transition at midpoint between 0.0021 and 0.0050: et = 0.00355
        val expectedMidPhi = 0.65 + 0.25 * ((0.00355 - ey) / (0.005 - ey))
        assertEquals(expectedMidPhi, ACI318Engine.calculatePhiFlexure(0.00355, fy), 0.001)
    }

    @Test
    fun testACI318BeamFlexure_TensionControlled() {
        val input = ACI318Engine.ACIBeamFlexureInput(
            muKnM = 150.0,
            fcPrimeMpa = 28.0,
            fyMpa = 420.0,
            widthMm = 300.0,
            depthMm = 500.0,
            coverMm = 40.0,
            barDiameterMm = 16.0
        )

        val result = ACI318Engine.designBeamFlexure(input)
        assertTrue("Beam must be safe", result.isSafe)
        assertTrue("Design moment φMn >= Mu", result.designMomentPhiMnKnM >= 150.0)
        assertTrue("Must be tension controlled (εt >= 0.005)", result.isTensionControlled)
        assertEquals(0.90, result.phiFactor, 0.01)
    }

    @Test
    fun testACI318BeamShear_SizeEffectFactor() {
        // d = 750 mm -> lambda_s = sqrt(2 / (1 + 0.004 * 750)) = sqrt(2 / 4) = 0.7071
        val input = ACI318Engine.ACIBeamShearInput(
            vuKn = 100.0,
            fcPrimeMpa = 28.0,
            widthMm = 300.0,
            depthMm = 750.0
        )

        val result = ACI318Engine.checkBeamShear(input)
        val expectedLambdaS = Math.sqrt(2.0 / (1.0 + 0.004 * 750.0))
        assertEquals(expectedLambdaS, result.sizeEffectFactorLambdaS, 0.001)

        // Vc = 0.17 * 1.0 * lambdaS * sqrt(28) * 300 * 750 / 1000
        val expectedVcKn = (0.17 * 1.0 * expectedLambdaS * Math.sqrt(28.0) * 300.0 * 750.0) / 1000.0
        assertEquals(expectedVcKn, result.concreteShearVcKn, expectedVcKn * tolerance)
        assertTrue(result.isSafe)
    }

    // =========================================================================
    // 3. SBC 304 Engine Tests
    // =========================================================================

    @Test
    fun testSBC304FactoredLoads() {
        // SBC 301 Cl. 2.3: U = max(1.4D, 1.2D + 1.6L)
        val load1 = SBC304Engine.factoredLoad(deadLoad = 10.0, liveLoad = 5.0)
        // 1.4*10 = 14, 1.2*10 + 1.6*5 = 12 + 8 = 20 -> max is 20.0
        assertEquals(20.0, load1, 0.001)

        val load2 = SBC304Engine.factoredLoad(deadLoad = 50.0, liveLoad = 2.0)
        // 1.4*50 = 70, 1.2*50 + 1.6*2 = 60 + 3.2 = 63.2 -> max is 70.0
        assertEquals(70.0, load2, 0.001)
    }
}
