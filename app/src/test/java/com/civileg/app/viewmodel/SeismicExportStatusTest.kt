package com.civileg.app.viewmodel

import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeismicExportStatusTest {
    private fun r(drift: Double, allow: Double, ok: Boolean, warnings: List<String> = emptyList()) = SeismicBaseShearResult(
        baseShear = 500.0, zoneFactor = 0.15, soilFactor = 1.0, importanceFactor = 1.0, responseModification = 5.0,
        calculationFormula = "V", codeReference = "ref", warnings = warnings,
        maxStoryDrift = drift, allowableDrift = allow, driftIsSafe = ok
    )

    @Test fun neverComputedDriftIsNotAPass() {
        val x = r(0.0, 0.0, true)   // driftIsSafe defaults to true
        assertFalse(x.driftAssessed)
        assertTrue(SeismicExportStatus.assessmentPending(x))
        assertTrue(SeismicExportStatus.checks(x).isEmpty())
        assertFalse(SeismicExportStatus.archivedIsSafe(x))
    }

    @Test fun computedAndPassedIsStillPreliminary() {
        val x = r(12.0, 20.0, true)
        assertTrue(x.driftAssessed)
        assertTrue(SeismicExportStatus.assessmentPending(x))
        assertTrue(SeismicExportStatus.checks(x).single().passed)
        assertTrue(SeismicExportStatus.archivedIsSafe(x))
    }

    @Test fun computedAndFailedStaysAFailure() {
        val x = r(30.0, 20.0, false)
        assertTrue(SeismicExportStatus.driftFailed(x))
        assertFalse(SeismicExportStatus.isSafe(x))
        assertFalse(SeismicExportStatus.assessmentPending(x))
        val c = SeismicExportStatus.checks(x).single()
        assertFalse(c.passed)
        assertEquals(30.0, c.calculated, 1e-9)
        assertFalse(SeismicExportStatus.archivedIsSafe(x))
    }

    @Test fun warningsBlockTheArchivedPass() = assertFalse(SeismicExportStatus.archivedIsSafe(r(12.0, 20.0, true, listOf("w"))))
}
