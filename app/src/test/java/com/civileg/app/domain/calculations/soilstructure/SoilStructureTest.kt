package com.civileg.app.domain.calculations.soilstructure

import com.civileg.app.domain.calculations.base.SoilStructureInteraction
import com.civileg.app.domain.calculations.soilstructure.ACISOilStructure
import com.civileg.app.domain.calculations.soilstructure.ECPSoilStructure
import com.civileg.app.domain.calculations.soilstructure.SBCSoilStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبار شامل لتفاعل المنشأ مع التربة (Soil-Structure Interaction Test).
 * يغطي: ECP/ACI/SBC، توزيع الضغوط، الهبوط، الاستقرار (الانقلاب، الانزلاق، التحمل)، InputGuard.
 */
class SoilStructureTest {

    private val baseInputs = SoilStructureInteraction.Inputs(
        foundationLengthMm = 5000.0,
        foundationWidthMm = 4000.0,
        foundationThicknessMm = 400.0,
        columnLoadKN = 500.0,
        momentKNm = 100.0,
        soilBearingCapacityKpa = 150.0,
        soilUnitWeightKnm3 = 18.0,
        frictionAngleDeg = 30.0,
        fcu = 25.0,
        fy = 360.0,
        embedmentDepthMm = 500.0
    )

    @Test
    fun allCodesProduceSafeResultsForNormalInputs() {
        val engines = listOf(ECPSoilStructure(), ACISOilStructure(), SBCSoilStructure())
        engines.forEach { engine ->
            val res = engine.design(baseInputs)
            assertTrue(" must be safe", res.isSafe)
            assertTrue("max pressure > 0", res.maxSoilPressureKpa > 0.0)
            assertTrue("average pressure > 0", res.bearing.averagePressureKpa > 0.0)
            assertTrue("settlement >= 0", res.settlement.totalSettlementMm >= 0.0)
            assertTrue("overturning FS > 0", res.stability.factorOfSafetyOverturning > 0.0)
            assertTrue("sliding FS > 0", res.stability.factorOfSafetySliding > 0.0)
            assertTrue("bearing FS > 0", res.stability.factorOfSafetyBearing > 0.0)
            assertTrue("calculation steps not empty", res.calculationSteps.isNotEmpty())
            assertTrue("code notes not empty", res.codeNotes.isNotEmpty())
        }
    }

    @Test
    fun verticalEquilibriumAveragePressure() {
        val res = ECPSoilStructure().design(baseInputs)
        val area = (baseInputs.foundationLengthMm / 1000.0) * (baseInputs.foundationWidthMm / 1000.0)
        val expectedAvg = baseInputs.columnLoadKN / area
        assertEquals("average pressure must equal P/A", expectedAvg, res.bearing.averagePressureKpa, 0.01)
    }

    @Test
    fun maxPressureGreaterThanAverageWhenMomentPositive() {
        val res = ECPSoilStructure().design(baseInputs)
        assertTrue("max pressure >= average pressure", res.maxSoilPressureKpa >= res.bearing.averagePressureKpa)
    }

    @Test
    fun minPressureLessThanAverageWhenMomentPositive() {
        val res = ECPSoilStructure().design(baseInputs)
        assertTrue("min pressure <= average pressure", res.minSoilPressureKpa <= res.bearing.averagePressureKpa)
    }

    @Test
    fun zeroMomentGivesUniformPressure() {
        val uniformInputs = baseInputs.copy(momentKNm = 0.0)
        val res = ECPSoilStructure().design(uniformInputs)
        assertEquals("max == min when M=0", res.maxSoilPressureKpa, res.minSoilPressureKpa, 0.01)
        assertEquals("max == average when M=0", res.maxSoilPressureKpa, res.bearing.averagePressureKpa, 0.01)
    }

    @Test
    fun largerFootingReducesPressure() {
        val smallFooting = baseInputs.copy(foundationLengthMm = 1000.0, foundationWidthMm = 1000.0)
        val largeFooting = baseInputs.copy(foundationLengthMm = 3000.0, foundationWidthMm = 3000.0)
        val smallRes = ECPSoilStructure().design(smallFooting)
        val largeRes = ECPSoilStructure().design(largeFooting)
        assertTrue("Larger footing reduces max pressure",
            largeRes.maxSoilPressureKpa < smallRes.maxSoilPressureKpa)
    }

    @Test
    fun higherLoadIncreasesPressure() {
        val lowLoad = baseInputs.copy(columnLoadKN = 200.0)
        val highLoad = baseInputs.copy(columnLoadKN = 800.0)
        val lowRes = ECPSoilStructure().design(lowLoad)
        val highRes = ECPSoilStructure().design(highLoad)
        assertTrue("Higher load increases max pressure",
            highRes.maxSoilPressureKpa > lowRes.maxSoilPressureKpa)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ecpRejectsZeroFoundationLength() {
        ECPSoilStructure().design(baseInputs.copy(foundationLengthMm = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aciRejectsNegativeColumnLoad() {
        ACISOilStructure().design(baseInputs.copy(columnLoadKN = -100.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sbcRejectsZeroSoilBearing() {
        SBCSoilStructure().design(baseInputs.copy(soilBearingCapacityKpa = 0.0))
    }

    @Test
    fun signedMomentProducesSymmetricPressureEnvelope() {
        val positive = ECPSoilStructure().design(baseInputs.copy(momentKNm = 50.0))
        val negative = ECPSoilStructure().design(baseInputs.copy(momentKNm = -50.0))

        assertEquals(positive.maxSoilPressureKpa, negative.maxSoilPressureKpa, 0.001)
        assertEquals(positive.minSoilPressureKpa, negative.minSoilPressureKpa, 0.001)
    }

    @Test
    fun ecpNotesReferenceECP203() {
        val res = ECPSoilStructure().design(baseInputs)
        assertTrue("ECP notes must reference ECP 203", res.codeNotes.any { it.contains("ECP 203") })
    }

    @Test
    fun aciNotesReferenceACI318() {
        val res = ACISOilStructure().design(baseInputs)
        assertTrue("ACI notes must reference ACI 318", res.codeNotes.any { it.contains("ACI 318") })
    }

    @Test
    fun sbcNotesReferenceSBC304() {
        val res = SBCSoilStructure().design(baseInputs)
        assertTrue("SBC notes must reference SBC 304", res.codeNotes.any { it.contains("SBC 304") })
    }

    @Test
    fun calculationStepsContainKeyInfo() {
        val res = ECPSoilStructure().design(baseInputs)
        val stepsText = res.calculationSteps.joinToString(" ")
        assertTrue("steps must mention area", stepsText.contains("مساحة") || stepsText.contains("area"))
        assertTrue("steps must mention pressure", stepsText.contains("ضغط") || stepsText.contains("pressure"))
        assertTrue("steps must mention settlement", stepsText.contains("هبوط") || stepsText.contains("settlement"))
    }

    @Test
    fun aciHasStricterSettlementLimitThanEcp() {
        val ecpRes = ECPSoilStructure().design(baseInputs)
        val aciRes = ACISOilStructure().design(baseInputs)
        // ACI allowable settlement = 19mm, ECP = 25mm
        assertEquals("ACI allowable settlement = 19mm", 19.0, aciRes.settlement.allowableSettlementMm, 0.001)
        assertEquals("ECP allowable settlement = 25mm", 25.0, ecpRes.settlement.allowableSettlementMm, 0.001)
    }

    @Test
    fun bearingFSMustBeCalculated() {
        val res = ECPSoilStructure().design(baseInputs)
        val expectedFS = baseInputs.soilBearingCapacityKpa / res.maxSoilPressureKpa
        assertEquals("bearing factor of safety", expectedFS, res.stability.factorOfSafetyBearing, 0.01)
    }
}
