package com.civileg.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionExportStatusTest {
    @Test fun capacityOnlyIsNotAssessedAndNotSafe() {
        assertFalse(ConnectionExportStatus.isAssessed(null, null))
        assertFalse(ConnectionExportStatus.isSafe(null, null))
        assertTrue(ConnectionExportStatus.checks("Weld Capacity", 250.0, null, null).isEmpty())
        assertTrue(ConnectionExportStatus.statusText(null, null).contains("not assessed"))
    }
    @Test fun zeroDemandIsNotAssessed() {
        assertFalse(ConnectionExportStatus.isAssessed(0.0, true))
        assertFalse(ConnectionExportStatus.isSafe(0.0, true))
    }
    @Test fun realPassAndFailAreKept() {
        assertTrue(ConnectionExportStatus.isSafe(0.6, true))
        assertFalse(ConnectionExportStatus.isSafe(1.2, false))
        val c = ConnectionExportStatus.checks("Bolt Capacity", 100.0, 1.2, false).single()
        assertEquals(120.0, c.calculated, 1e-9)
        assertFalse(c.passed)
    }
}
