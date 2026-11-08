package com.civileg.app.domain.calculations.wallopenings

import com.civileg.app.domain.calculations.base.WallOpeningDesign
import com.civileg.app.domain.calculations.wallopenings.ACIWallOpening
import com.civileg.app.domain.calculations.wallopenings.ECPWallOpening
import com.civileg.app.domain.calculations.wallopenings.SBCWallOpening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبار شامل لتصميم فتحات الحوائط (Wall Opening Test).
 * يغطي: ECP/ACI/SBC، تسليح الكرم العلوي، العتبة، التسليح الرأسي، فحص الإجهاد، InputGuard.
 */
class WallOpeningTest {

    private val baseInputs = WallOpeningDesign.Inputs(
        wallHeightMm = 3000.0,
        wallThicknessMm = 200.0,
        openingWidthMm = 1000.0,
        openingHeightMm = 1200.0,
        openingFromBaseMm = 900.0,
        wallLengthMm = 4000.0,
        fcu = 25.0,
        fy = 360.0,
        loadAboveOpeningKNm = 50.0
    )

    @Test
    fun allCodesProduceSafeResultsForNormalInputs() {
        val engines = listOf(ECPWallOpening(), ACIWallOpening(), SBCWallOpening())
        engines.forEach { engine ->
            val res = engine.design(baseInputs)
            assertTrue(" must be safe", res.isSafe)
            assertTrue("opening reinforcement area > 0", res.openingReinforcement.areaProvidedMm2 > 0.0)
            assertTrue("header beam reinforcement area > 0", res.headerBeamReinforcement.areaProvidedMm2 > 0.0)
            assertTrue("sill reinforcement area > 0", res.sillReinforcement.areaProvidedMm2 > 0.0)
            assertTrue("vertical reinforcement area > 0", res.verticalReinforcementEachSide.areaProvidedMm2 > 0.0)
            assertTrue("max stress > 0", res.maxStressMpa > 0.0)
            assertTrue("calculation steps not empty", res.calculationSteps.isNotEmpty())
            assertTrue("code notes not empty", res.codeNotes.isNotEmpty())
        }
    }

    @Test
    fun reinforcementRatioMeetsMinimum() {
        val lostArea = baseInputs.openingWidthMm * baseInputs.openingHeightMm
        val ecpRes = ECPWallOpening().design(baseInputs)
        val aciRes = ACIWallOpening().design(baseInputs)
        val sbcRes = SBCWallOpening().design(baseInputs)

        val ecpMinRequired = lostArea * 0.002
        assertTrue("ECP opening reinforcement >= 0.2% of lost area",
            ecpRes.openingReinforcement.areaProvidedMm2 >= ecpMinRequired)

        val aciMinRequired = lostArea * 0.0025
        assertTrue("ACI opening reinforcement >= 0.25% of lost area",
            aciRes.openingReinforcement.areaProvidedMm2 >= aciMinRequired)

        val sbcMinRequired = lostArea * 0.0022
        assertTrue("SBC opening reinforcement >= 0.22% of lost area",
            sbcRes.openingReinforcement.areaProvidedMm2 >= sbcMinRequired)
    }

    @Test
    fun aciIsMoreConservativeThanEcp() {
        val ecpRes = ECPWallOpening().design(baseInputs)
        val aciRes = ACIWallOpening().design(baseInputs)
        assertTrue("ACI max stress >= ECP max stress",
            aciRes.maxStressMpa >= ecpRes.maxStressMpa)
    }

    @Test
    fun largerOpeningRequiresMoreReinforcement() {
        val smallOpening = baseInputs.copy(openingWidthMm = 600.0, openingHeightMm = 600.0)
        val largeOpening = baseInputs.copy(openingWidthMm = 1500.0, openingHeightMm = 1800.0)
        val smallRes = ECPWallOpening().design(smallOpening)
        val largeRes = ECPWallOpening().design(largeOpening)
        assertTrue("Larger opening requires more opening steel",
            largeRes.openingReinforcement.areaProvidedMm2 >= smallRes.openingReinforcement.areaProvidedMm2)
        assertTrue("Larger opening requires more header steel",
            largeRes.headerBeamReinforcement.areaProvidedMm2 >= smallRes.headerBeamReinforcement.areaProvidedMm2)
    }

    @Test
    fun higherLoadRequiresMoreHeaderSteel() {
        val lowLoad = baseInputs.copy(loadAboveOpeningKNm = 20.0)
        val highLoad = baseInputs.copy(loadAboveOpeningKNm = 100.0)
        val lowRes = ECPWallOpening().design(lowLoad)
        val highRes = ECPWallOpening().design(highLoad)
        assertTrue("Higher load requires more header steel",
            highRes.headerBeamReinforcement.areaProvidedMm2 >= lowRes.headerBeamReinforcement.areaProvidedMm2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ecpRejectsZeroWallThickness() {
        ECPWallOpening().design(baseInputs.copy(wallThicknessMm = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aciRejectsNegativeOpeningWidth() {
        ACIWallOpening().design(baseInputs.copy(openingWidthMm = -500.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sbcRejectsZeroFcu() {
        SBCWallOpening().design(baseInputs.copy(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ecpRejectsNegativeLoad() {
        ECPWallOpening().design(baseInputs.copy(loadAboveOpeningKNm = -10.0))
    }

    @Test
    fun ecpNotesReferenceECP203() {
        val res = ECPWallOpening().design(baseInputs)
        assertTrue("ECP notes must reference ECP 203", res.codeNotes.any { it.contains("ECP 203") })
    }

    @Test
    fun aciNotesReferenceACI318() {
        val res = ACIWallOpening().design(baseInputs)
        assertTrue("ACI notes must reference ACI 318", res.codeNotes.any { it.contains("ACI 318") })
    }

    @Test
    fun sbcNotesReferenceSBC304() {
        val res = SBCWallOpening().design(baseInputs)
        assertTrue("SBC notes must reference SBC 304", res.codeNotes.any { it.contains("SBC 304") })
    }

    @Test
    fun calculationStepsContainKeyInfo() {
        val res = ECPWallOpening().design(baseInputs)
        val stepsText = res.calculationSteps.joinToString(" ")
        assertTrue("steps must mention lost area", stepsText.contains("مفقودة") || stepsText.contains("lost"))
        assertTrue("steps must mention header beam", stepsText.contains("كمر") || stepsText.contains("header"))
        assertTrue("steps must mention stress", stepsText.contains("إجهاد") || stepsText.contains("stress"))
    }
}
