package com.civileg.app.viewmodel

import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
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

    @Test fun estimatedDriftIsNeverAPassOrAFailure() {
        for (x in listOf(r(12.0, 20.0, true), r(30.0, 20.0, false))) {
            assertTrue(x.driftAssessed)
            assertTrue(SeismicExportStatus.assessmentPending(x))
            assertFalse(SeismicExportStatus.driftFailed(x))
            assertTrue(SeismicExportStatus.checks(x).isEmpty())
            assertFalse(SeismicExportStatus.archivedIsSafe(x))
        }
    }
}
