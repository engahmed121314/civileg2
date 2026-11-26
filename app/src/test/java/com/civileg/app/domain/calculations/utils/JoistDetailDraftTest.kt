package com.civileg.app.domain.calculations.utils

import org.junit.Assert.*
import org.junit.Test

class JoistDetailDraftTest {
    @Test fun `empty draft does not select loads support exposure or seismic`() {
        val c = JoistDetailDraft().toCase(5000.0, 20.0, 5.0, 25.0)
        assertNull(c.actualRibCount)
        assertNull(c.leftSupport)
        assertNull(c.loadBasis)
        assertNull(c.serviceability)
        assertNull(c.detailing)
    }
    @Test fun `explicit no seismic does not invent a bar layout`() {
        val c = JoistDetailDraft(seismicRequired = false).toCase(5000.0, 20.0, 5.0, 25.0)
        assertFalse(c.detailing!!.seismicDetailingRequired)
        assertEquals(0, c.detailing!!.barsPerRib)
        assertTrue(c.unresolved().any { it.contains("main-bar") })
    }
    @Test fun `blank demand input stays invalid not zero`() {
        val c = JoistDetailDraft(uniformlyDistributed = true).toCase(5000.0, 20.0, 5.0, 25.0)
        assertTrue(c.loadBasis!!.deadKnM2.isNaN())
        assertTrue(c.unresolved().any { it.contains("dead/live") })
    }
    @Test fun `malformed optional partition time is not treated as not applicable`() {
        val c = JoistDetailDraft(values = mapOf("partitionTime" to "bad", "sustainedFraction" to "0.5", "duration" to "60"),
            exposure = JoistDetailCase.Exposure.PROTECTED_INTERIOR, sensitivePartitions = false)
            .toCase(5000.0, 20.0, 5.0, 25.0)
        assertTrue(c.serviceability!!.partitionInstallationMonths!!.isNaN())
        assertTrue(c.unresolved().contains("Valid partition installation time"))
    }
}
