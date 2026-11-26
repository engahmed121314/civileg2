package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.ecp.EcpColumnProvisions2025.Exposure
import com.civileg.app.domain.calculations.ecp.EcpColumnProvisions2025.SectionKind
import com.civileg.app.domain.calculations.ecp.EcpColumnProvisions2025.SlendernessClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Values transcribed from ECP 203-2025 scan: Table 6-7/6-8 PDF 234 (p.6-43), Tables 2-21/2-22 PDF 60-61 (p.2-32/2-33). */
class EcpColumnProvisions2025Test {
    private val tol = 1e-9

    @Test fun table67_shortLimits() {
        assertEquals(15.0, EcpColumnProvisions2025.shortLimitDimensional(SectionKind.RECTANGULAR, true), tol)
        assertEquals(10.0, EcpColumnProvisions2025.shortLimitDimensional(SectionKind.RECTANGULAR, false), tol)
        assertEquals(12.0, EcpColumnProvisions2025.shortLimitDimensional(SectionKind.CIRCULAR, true), tol)
        assertEquals(8.0, EcpColumnProvisions2025.shortLimitDimensional(SectionKind.CIRCULAR, false), tol)
        assertEquals(50.0, EcpColumnProvisions2025.shortLimitLambdaI(true), tol)
        assertEquals(35.0, EcpColumnProvisions2025.shortLimitLambdaI(false), tol)
    }

    @Test fun table68_slenderMaxima() {
        assertEquals(100.0, EcpColumnProvisions2025.maxLambdaI(true), tol)
        assertEquals(70.0, EcpColumnProvisions2025.maxLambdaI(false), tol)
        assertEquals(30.0, EcpColumnProvisions2025.maxDimensional(SectionKind.RECTANGULAR, true), tol)
        assertEquals(23.0, EcpColumnProvisions2025.maxDimensional(SectionKind.RECTANGULAR, false), tol)
        assertEquals(25.0, EcpColumnProvisions2025.maxDimensional(SectionKind.CIRCULAR, true), tol)
        assertEquals(18.0, EcpColumnProvisions2025.maxDimensional(SectionKind.CIRCULAR, false), tol)
    }

    @Test fun classify_byLambdaI() {
        assertEquals(SlendernessClass.SHORT, EcpColumnProvisions2025.classifyByLambdaI(50.0, true))
        assertEquals(SlendernessClass.SLENDER, EcpColumnProvisions2025.classifyByLambdaI(50.1, true))
        assertEquals(SlendernessClass.SLENDER, EcpColumnProvisions2025.classifyByLambdaI(100.0, true))
        assertEquals(SlendernessClass.EXCEEDS_MAXIMUM, EcpColumnProvisions2025.classifyByLambdaI(100.1, true))
        assertEquals(SlendernessClass.SHORT, EcpColumnProvisions2025.classifyByLambdaI(35.0, false))
        assertEquals(SlendernessClass.SLENDER, EcpColumnProvisions2025.classifyByLambdaI(70.0, false))
        assertEquals(SlendernessClass.EXCEEDS_MAXIMUM, EcpColumnProvisions2025.classifyByLambdaI(70.1, false))
    }

    @Test fun legacyEngineLimitsMatchTable67LambdaB() {
        assertTrue(EcpColumnProvisions2025.legacyLimitMatchesTable67(true))
        assertTrue(EcpColumnProvisions2025.legacyLimitMatchesTable67(false))
    }

    @Test fun cover_tables221and222() {
        assertEquals(20.0, Exposure.CARBONATION_VERY_DRY.minCoverMm, tol)
        assertEquals(25.0, Exposure.CARBONATION_DRY.minCoverMm, tol)
        assertEquals(35.0, Exposure.CARBONATION_WET_RARELY_DRY.minCoverMm, tol)
        assertEquals(35.0, Exposure.CARBONATION_MODERATE_HUMIDITY.minCoverMm, tol)
        assertEquals(40.0, Exposure.CARBONATION_CYCLIC_WET_DRY.minCoverMm, tol)
        assertEquals(45.0, Exposure.CHLORIDE_MODERATE_HUMIDITY.minCoverMm, tol)
        assertEquals(50.0, Exposure.CHLORIDE_WET_RARELY_DRY.minCoverMm, tol)
        assertEquals(60.0, Exposure.CHLORIDE_CYCLIC_WET_DRY.minCoverMm, tol)
    }

    @Test fun cover_appFixed40_comparison() {
        assertTrue(EcpColumnProvisions2025.compareCover(Exposure.CARBONATION_CYCLIC_WET_DRY, 40.0).adequate)
        assertFalse(EcpColumnProvisions2025.compareCover(Exposure.CHLORIDE_MODERATE_HUMIDITY, 40.0).adequate)
        assertFalse(EcpColumnProvisions2025.compareCover(Exposure.CHLORIDE_CYCLIC_WET_DRY, 40.0).adequate)
        assertTrue(EcpColumnProvisions2025.coverNote(40.0).contains("Cover 40 mm"))
        assertTrue(EcpColumnProvisions2025.coverNote(40.0).contains("NOT ASSESSED"))
    }

    @Test
    fun table68WarningShownWithValueAndLimit_notFail() {
        val w = EcpColumnProvisions2025.maxSlendernessWarning(110.0, true)!!
        assertTrue(w.startsWith("WARNING"))
        assertTrue(w.contains("110.0") && w.contains("100") && w.contains("Table 6-8") && w.contains("تحذير"))
        assertNull(EcpColumnProvisions2025.maxSlendernessWarning(100.0, true))
        assertNotNull(EcpColumnProvisions2025.maxSlendernessWarning(71.0, false))
    }

    @Test
    fun checkSlendernessCarriesTable68Warning() {
        val r = ECPColumn().checkSlenderness(20000.0, 300.0, 300.0, true)  // lambda_i = 20000/90 = 222
        assertTrue(r.recommendation.startsWith("WARNING"))
        assertFalse(r.isShortColumn)
    }
}
