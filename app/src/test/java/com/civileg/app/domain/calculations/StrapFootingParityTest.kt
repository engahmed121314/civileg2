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
 * تكافؤ القواعد الشريطية (Strap Footing Parity) عبر الأكواد ECP / ACI / SBC.
 *
 * يتأكد هذا الاختبار من أن:
 *  1. المحرك الموحد StrapFootingDesignEngine يُستدعى من جميع محركات الأكواد (ECP/ACI/SBC).
 *  2. لنفس المدخلات، تنتج جميع الأكواد نفس الأبعاد والتسليح (لأن المحرك الموحد يستخدم
 *     loadFactor = max(γD, γL) = 1.6 للجميع).
 *  3. الفارق بين الأكواد يظهر في ملاحظات الكود (codeNotes) فقط.
 *  4. جميع النتائج آمنة وإيجابية.
 *  5. واجهة CalculationFactory تُحيل الطلب لجميع الأكواد الثلاثة.
 */
class StrapFootingParityTest {

    private val baseInputs = StrapFootingDesignEngine.Inputs(
        column1Load = 500.0, column2Load = 300.0,
        distanceBetweenColumns = 4000.0,
        column1Width = 300.0, column1Depth = 500.0,
        column2Width = 300.0, column2Depth = 500.0,
        soilBearingCapacity = 150.0, fcu = 25.0, fy = 360.0,
        strapBeamWidth = 400.0
    )

    // ════════════════════════════════════════════════════════════════════════════
    // 1. الربط بالمحرك الموحد — جميع الأكواد تستخدم StrapFootingDesignEngine
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun allCodeEnginesDelegateToUnifiedEngine() {
        val engines: List<StrapFootingDesign> = listOf(
            ECPStrapFooting(),
            ACIStrapFooting(),
            SBCStrapFooting()
        )
        engines.forEach { engine ->
            val res = engine.design(baseInputs)
            assertTrue("reactions must be positive", res.reactions.first > 0.0 && res.reactions.second > 0.0)
            assertTrue("footings must have positive dimensions", res.footing1.width > 0.0 && res.footing1.length > 0.0)
            assertTrue("strap beam must have positive dimensions", res.strapBeam.width > 0.0 && res.strapBeam.depth > 0.0)
            assertTrue("calculation steps must be populated", res.calculationSteps.isNotEmpty())
            assertTrue("code notes must be non-empty", res.codeNotes.isNotEmpty())
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. Unified engine → identical dimensions & steel for all codes (loadFactor = max(γD,γL) = 1.6)
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun allCodesProduceIdenticalDimensionsAndSteelForSameInputs() {
        val ecp = ECPStrapFooting().design(baseInputs)
        val aci = ACIStrapFooting().design(baseInputs)
        val sbc = SBCStrapFooting().design(baseInputs)

        assertEquals("footing1 width must match across codes", ecp.footing1.width, aci.footing1.width, 0.001)
        assertEquals("footing1 width must match across codes", ecp.footing1.width, sbc.footing1.width, 0.001)
        assertEquals("footing1 length must match across codes", ecp.footing1.length, aci.footing1.length, 0.001)
        assertEquals("footing1 length must match across codes", ecp.footing1.length, sbc.footing1.length, 0.001)
        assertEquals("footing1 thickness must match", ecp.footing1.thickness, aci.footing1.thickness, 0.001)
        assertEquals("footing2 width must match across codes", ecp.footing2.width, aci.footing2.width, 0.001)
        assertEquals("footing2 length must match across codes", ecp.footing2.length, aci.footing2.length, 0.001)
        assertEquals("strap depth must match across codes", ecp.strapBeam.depth, aci.strapBeam.depth, 0.001)
        assertEquals("strap top bars must match across codes", ecp.strapBeam.topReinforcement.numberOfBars, aci.strapBeam.topReinforcement.numberOfBars)
        assertEquals("strap top bars must match across codes", ecp.strapBeam.topReinforcement.numberOfBars, sbc.strapBeam.topReinforcement.numberOfBars)
        assertEquals("strap area provided must match", ecp.strapBeam.topReinforcement.areaProvided, aci.strapBeam.topReinforcement.areaProvided, 1.0)
        assertEquals("reactions must match across codes", ecp.reactions.first, aci.reactions.first, 0.5)
        assertEquals("reactions must match across codes", ecp.reactions.first, sbc.reactions.first, 0.5)
    }

    @Test
    fun eachCodeHasDistinctNotesForItsStandard() {
        val ecp = ECPStrapFooting().design(baseInputs)
        val aci = ACIStrapFooting().design(baseInputs)
        val sbc = SBCStrapFooting().design(baseInputs)

        assertTrue("ECP notes must reference ECP 203", ecp.codeNotes.any { it.contains("ECP 203") })
        assertTrue("ACI notes must reference ACI 318", aci.codeNotes.any { it.contains("ACI 318") })
        assertTrue("SBC notes must reference SBC 304", sbc.codeNotes.any { it.contains("SBC 304") })

        // Ensure notes are not shared/copied incorrectly
        assertFalse("ECP must not reference ACI", ecp.codeNotes.any { it.contains("ACI 318") })
        assertFalse("ACI must not reference ECP", aci.codeNotes.any { it.contains("ECP 203") })
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. CalculationFactory forwards to each code engine
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun calculationFactoryReturnsCompleteDesignForEachCode() {
        listOf(DesignCode.ECP, DesignCode.ACI, DesignCode.SBC).forEach { code ->
            val design = com.civileg.app.domain.calculations.CalculationFactory.getStrapFootingDesign(code)
            val res = design.design(baseInputs)
            assertTrue("footing dimensions must be positive", res.footing1.width > 0.0 && res.footing1.length > 0.0)
            assertTrue("reaction values must be finite", res.reactions.first.isFinite() && res.reactions.second.isFinite())
            assertTrue(" code notes must reference the code", res.codeNotes.any { it.contains(code.name) })
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // 4. InputGuard rejects invalid inputs
    // ═════════════════════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun ecpRejectsZeroFcu() {
        ECPStrapFooting().design(baseInputs.copy(fcu = 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aciRejectsNegativeColumnLoad() {
        ACIStrapFooting().design(baseInputs.copy(column1Load = -100.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sbcRejectsZeroDistance() {
        SBCStrapFooting().design(baseInputs.copy(distanceBetweenColumns = 0.0))
    }
}
