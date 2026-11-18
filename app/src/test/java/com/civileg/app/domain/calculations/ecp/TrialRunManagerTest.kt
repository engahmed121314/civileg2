package com.civileg.app.domain.calculations.ecp

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TrialRunManager (Full integration simulation run).
 */
class TrialRunManagerTest {

    @Test
    fun runFullProjectSimulation_producesNonEmptyReport() {
        val report = TrialRunManager.runFullProjectSimulation()
        assertNotNull("Report should not be null", report)
        assertTrue("Report should contain header", report.contains("CivilEG v2.0"))
        assertTrue("Report should contain seismic calculations", report.contains("حسابات الزلازل"))
        assertTrue("Report should contain footing design", report.contains("تصميم القواعد"))
        assertTrue("Report should contain success message", report.contains("بنجاح"))
    }
}
