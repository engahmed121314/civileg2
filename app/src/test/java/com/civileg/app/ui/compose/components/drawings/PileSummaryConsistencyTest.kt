package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.PileInput
import com.civileg.app.domain.PileQualification
import com.civileg.app.domain.calculations.ecp.ECPPileFoundation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pile drawing sheet, screen banner and PDF fallback must tell the same PRELIMINARY story. */
class PileSummaryConsistencyTest {
    @Test
    fun `passing pile estimate reads unverified with the preliminary banner and names no edition`() {
        val r = ECPPileFoundation().designPile(PileInput(axialLoad = 100.0))
        assertTrue(r.isSafe)
        val s = pileDrawingSummary(r)
        assertEquals("UNVERIFIED", s.safetyLabel)
        assertFalse(s.listedChecksFailed)
        assertEquals(PileQualification.PRELIMINARY_BANNER, s.codeNotes.first())
        assertEquals(PileQualification.DRAWING_CODE_LABEL, s.designCodeLabel)
        assertFalse(s.designCodeLabel.contains("203"))
    }

    @Test
    fun `failed pile estimate reads fail without the preliminary banner`() {
        val r = ECPPileFoundation().designPile(PileInput(axialLoad = 200000.0))
        val s = pileDrawingSummary(r)
        assertTrue(s.listedChecksFailed)
        assertEquals("FAIL", s.safetyLabel)
        assertFalse(s.codeNotes.contains(PileQualification.PRELIMINARY_BANNER))
    }
}
