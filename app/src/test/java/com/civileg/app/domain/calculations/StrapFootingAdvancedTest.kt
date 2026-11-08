package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIStrapFooting
import com.civileg.app.domain.calculations.base.StrapFootingDesign
import com.civileg.app.domain.calculations.ecp.ECPStrapFooting
import com.civileg.app.domain.calculations.sbc.SBCStrapFooting
import com.civileg.app.domain.entities.DesignCode
import com.civileg.core.engineering.StrapFootingDesignEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبار متقدم للقواعد الشريطية (Strap Footing Advanced Test).
 * يغطي: حالات الحدود، توازن العزوم، أحمال مختلفة، أبعاد متنوعة، التحقق من المحرك الموحد.
 */
class StrapFootingAdvancedTest {

    // ════════════════════════════════════════════════════════════════════════════
    // مجموعات مدخلات للاختبار
    // ════════════════════════════════════════════════════════════════════════════

    private val lightLoad = StrapFootingDesignEngine.Inputs(
        column1Load = 200.0, column2Load = 150.0,
        distanceBetweenColumns = 3000.0,
        column1Width = 300.0, column1Depth = 400.0,
        column2Width = 300.0, column2Depth = 400.0,
        soilBearingCapacity = 200.0, fcu = 25.0, fy = 360.0,
        strapBeamWidth = 350.0
    )

    private val heavyLoad = StrapFootingDesignEngine.Inputs(
        column1Load = 1500.0, column2Load = 1200.0,
        distanceBetweenColumns = 6000.0,
        column1Width = 500.0, column1Depth = 700.0,
        column2Width = 500.0, column2Depth = 700.0,
        soilBearingCapacity = 150.0, fcu = 30.0, fy = 420.0,
        strapBeamWidth = 500.0
    )

    private val unbalancedLoad = StrapFootingDesignEngine.Inputs(
        column1Load = 100.0, column2Load = 900.0,  // عمود خارجي خفيف، داخلي ثقيل جداً
        distanceBetweenColumns = 4000.0,
        column1Width = 300.0, column1Depth = 500.0,
        column2Width = 300.0, column2Depth = 500.0,
        soilBearingCapacity = 150.0, fcu = 25.0, fy = 360.0,
        strapBeamWidth = 400.0
    )

    // ════════════════════════════════════════════════════════════════════════════
    // 1. Vertical Equilibrium — R1 + R2 = P1 + P2
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun verticalEquilibriumHoldsForLightLoad() {
        val engines = listOf(ECPStrapFooting(), ACIStrapFooting(), SBCStrapFooting())
        engines.forEach { engine ->
            val res = engine.design(lightLoad)
            val totalReaction = res.reactions.first + res.reactions.second
            val totalLoad = lightLoad.column1Load + lightLoad.column2Load
            assertTrue(
                "Vertical equilibrium: R1 + R2 must equal P1 + P2",
                Math.abs(totalReaction - totalLoad) < 1.0
            )
        }
    }

    @Test
    fun verticalEquilibriumHoldsForHeavyLoad() {
        val engines = listOf(ECPStrapFooting(), ACIStrapFooting(), SBCStrapFooting())
        engines.forEach { engine ->
            val res = engine.design(heavyLoad)
            val totalReaction = res.reactions.first + res.reactions.second
            val totalLoad = heavyLoad.column1Load + heavyLoad.column2Load
            assertTrue(
                "Vertical equilibrium for heavy load",
                Math.abs(totalReaction - totalLoad) < 5.0
            )
        }
    }

    @Test
    fun verticalEquilibriumHoldsForUnbalancedLoad() {
        val res = ECPStrapFooting().design(unbalancedLoad)
        val totalReaction = res.reactions.first + res.reactions.second
        val totalLoad = unbalancedLoad.column1Load + unbalancedLoad.column2Load
        assertTrue(
            "Vertical equilibrium for unbalanced load",
            Math.abs(totalReaction - totalLoad) < 1.0
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. Moment Equilibrium — R1 × S = P1 × L (around column 2)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun momentEquilibriumAroundColumn2() {
        val engine = ECPStrapFooting()
        val res = engine.design(lightLoad)

        val x1 = lightLoad.column1Depth / 2000.0 // m from edge to col1 center
        val x2 = x1 + lightLoad.distanceBetweenColumns / 1000.0
        val footingCentroid = res.footing1.length / 2000.0

        val momentFromR1 = res.reactions.first * (x2 - footingCentroid) // kN.m
        val momentFromP1 = lightLoad.column1Load * (lightLoad.distanceBetweenColumns / 1000.0) // kN.m

        assertTrue(
            "Moment equilibrium: R1 × S must equal P1 × L",
            Math.abs(momentFromR1 - momentFromP1) < 5.0
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. Soil Bearing Pressure — must be positive and within allowable
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun soilPressureMustBePositive() {
        val engines = listOf(ECPStrapFooting(), ACIStrapFooting(), SBCStrapFooting())
        engines.forEach { engine ->
            val res = engine.design(lightLoad)
            assertTrue("soil pressure 1 must be positive", res.soilPressure1 > 0.0)
            assertTrue("soil pressure 2 must be positive", res.soilPressure2 > 0.0)
        }
    }

    @Test
    fun soilPressureWithinAllowableForNormalLoads() {
        val res = ECPStrapFooting().design(lightLoad)
        assertTrue(
            "soil pressure 1 must be within allowable pressure",
            res.soilPressure1 <= res.allowableSoilPressure * 1.05
        )
        assertTrue(
            "soil pressure 2 must be within allowable pressure",
            res.soilPressure2 <= res.allowableSoilPressure * 1.05
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 4. Punching Shear — capacity must be computed and positive
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun punchingCapacityMustBeComputed() {
        val engines = listOf(ECPStrapFooting(), ACIStrapFooting(), SBCStrapFooting())
        engines.forEach { engine ->
            val res = engine.design(heavyLoad)
            assertTrue("punching capacity 1 must be positive", res.punchingCapacity1 > 0.0)
            assertTrue("punching capacity 2 must be positive", res.punchingCapacity2 > 0.0)
            assertTrue("punching shear 1 must be non-negative", res.punchingShear1 >= 0.0)
            assertTrue("punching shear 2 must be non-negative", res.punchingShear2 >= 0.0)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 5. Strap Beam — moment and shear must be positive
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun strapBeamMomentAndShearPositive() {
        val engines = listOf(ECPStrapFooting(), ACIStrapFooting(), SBCStrapFooting())
        engines.forEach { engine ->
            val res = engine.design(lightLoad)
            assertTrue("strap moment must be positive", res.strapMoment > 0.0)
            assertTrue("strap shear must be non-negative", res.strapShear >= 0.0)
            assertTrue("strap top reinforcement provided > 0", res.strapBeam.topReinforcement.areaProvided > 0.0)
            assertTrue("strap bottom reinforcement provided > 0", res.strapBeam.bottomReinforcement.areaProvided > 0.0)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 6. Heavier loads produce larger footings and more steel
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun heavierLoadProducesLargerFootings() {
        val light = ECPStrapFooting().design(lightLoad)
        val heavy = ECPStrapFooting().design(heavyLoad)

        assertTrue(
            "Heavier load must produce a larger footing1 area",
            heavy.footing1.width * heavy.footing1.length > light.footing1.width * light.footing1.length
        )
        assertTrue(
            "Heavier load: footing2 area must be larger",
            heavy.footing2.width * heavy.footing2.length > light.footing2.width * light.footing2.length
        )
        assertTrue(
            "Heavier load: strap steel must be more",
            heavy.strapBeam.topReinforcement.areaProvided >= light.strapBeam.topReinforcement.areaProvided
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 7. All three codes return complete results and explain unsafe cases
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun allCodesProduceCompleteResultsAndReportUnsafeCases() {
        val testCases = listOf(
            "light" to lightLoad,
            "heavy" to heavyLoad,
            "unbalanced" to unbalancedLoad
        )

        val engines = listOf(
            "ECP" to ECPStrapFooting(),
            "ACI" to ACIStrapFooting(),
            "SBC" to SBCStrapFooting()
        )

        testCases.forEach { (name, inputs) ->
            engines.forEach { (codeName, engine) ->
                val res = engine.design(inputs)
                assertTrue("$codeName/$name reaction 1 must be positive", res.reactions.first > 0.0)
                assertTrue("  must have positive reactions", res.reactions.first > 0.0 && res.reactions.second > 0.0)
                assertTrue("  must have calculation steps", res.calculationSteps.isNotEmpty())
                assertTrue("  must have code notes", res.codeNotes.isNotEmpty())
                assertTrue("$codeName/$name unsafe result must explain why", res.isSafe || res.warnings.isNotEmpty())
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 8. InputGuard rejects all invalid inputs for each code
    // ════════════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun ecpRejectsNegativeFy() {
        ECPStrapFooting().design(lightLoad.copy(fy = -100.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aciRejectsZeroSoilBearing() {
        ACIStrapFooting().design(lightLoad.copy(soilBearingCapacity = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sbcRejectsNegativeColumnWidth() {
        SBCStrapFooting().design(lightLoad.copy(column1Width = -50.0))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 9. CalculationSteps are populated and meaningful
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun calculationStepsContainKeyInfo() {
        val res = ECPStrapFooting().design(lightLoad)
        val stepsText = res.calculationSteps.joinToString(" ")

        assertTrue("steps must mention reactions", stepsText.contains("R1") || stepsText.contains("رد الفعل"))
        assertTrue("steps must mention footing dimensions", stepsText.contains("قاعدة") || stepsText.contains("footing"))
        assertTrue("steps must mention strap beam", stepsText.contains("strap", ignoreCase = true))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 10. Code-specific notes are present
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun ecpNotesReferenceECP203() {
        val res = ECPStrapFooting().design(lightLoad)
        assertTrue("ECP notes must reference ECP 203", res.codeNotes.any { it.contains("ECP 203") })
    }

    @Test
    fun aciNotesReferenceACI318() {
        val res = ACIStrapFooting().design(lightLoad)
        assertTrue("ACI notes must reference ACI 318", res.codeNotes.any { it.contains("ACI 318") })
    }

    @Test
    fun sbcNotesReferenceSBC304() {
        val res = SBCStrapFooting().design(lightLoad)
        assertTrue("SBC notes must reference SBC 304", res.codeNotes.any { it.contains("SBC 304") })
    }
}
