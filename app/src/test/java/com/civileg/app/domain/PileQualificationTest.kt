package com.civileg.app.domain

import com.civileg.app.domain.calculations.aci.ACIPileFoundation
import com.civileg.app.domain.calculations.ecp.ECPPileFoundation
import com.civileg.app.domain.calculations.sbc.SBCPileFoundation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Geotechnical estimates are never a blanket code acceptance, and a failed estimate stays visibly failed. */
class PileQualificationTest {

    @Test
    fun `status never reads accepted while acceptance is incomplete and a failed estimate stays failed`() {
        assertEquals(PileStatus.CHECKS_FAILED, PileQualification.status(isSafe = false, codeAcceptanceComplete = false))
        assertEquals(PileStatus.CHECKS_FAILED, PileQualification.status(isSafe = false, codeAcceptanceComplete = true))
        assertEquals(PileStatus.PRELIMINARY, PileQualification.status(isSafe = true, codeAcceptanceComplete = false))
        assertEquals(PileStatus.ACCEPTED, PileQualification.status(isSafe = true, codeAcceptanceComplete = true))
    }

    @Test
    fun `all three lanes return a preliminary result with qualification notes`() {
        val lanes = mapOf("ECP" to ECPPileFoundation(), "ACI" to ACIPileFoundation(), "SBC" to SBCPileFoundation())
        for ((n, lane) in lanes) for (soil in listOf(SoilType.CLAY, SoilType.SAND)) {
            val r = lane.designPile(PileInput(soilType = soil))
            assertFalse("$n $soil", r.codeAcceptanceComplete)
            assertTrue("$n $soil", r.qualificationNotes.any { it.contains("geotechnical estimates") })
            assertTrue("$n $soil", r.qualificationNotes.any { it.contains("not a code-accepted") })
        }
    }

    @Test
    fun `an overloaded pile stays failed in every lane`() {
        val lanes = mapOf("ECP" to ECPPileFoundation(), "ACI" to ACIPileFoundation(), "SBC" to SBCPileFoundation())
        for ((n, lane) in lanes) {
            val r = lane.designPile(PileInput(axialLoad = 200000.0))
            assertFalse(n, r.isSafe)
            assertEquals(n, PileStatus.CHECKS_FAILED, PileQualification.status(r.isSafe, r.codeAcceptanceComplete))
        }
    }

    @Test
    fun `pile lanes no longer cite SBC chapter 7 or unverified ECP clause numbers`() {
        val base = listOf("src/main/java", "app/src/main/java").map { File(it) }.firstOrNull { it.exists() } ?: return
        for (rel in listOf("sbc/SBCPileFoundation.kt", "ecp/ECPPileFoundation.kt")) {
            val f = File(base, "com/civileg/app/domain/calculations/$rel")
            assertTrue(f.path, f.exists())
            val t = f.readText()
            assertFalse("$rel SBC 304 §7", Regex("SBC 304 §7").containsMatchIn(t))
            assertFalse("$rel SBC 304 Table 7", Regex("SBC 304 Table 7").containsMatchIn(t))
            assertFalse("$rel ECP 203/7-2", Regex("ECP 203/7-2|ECP 203 Clause 7-2").containsMatchIn(t))
        }
    }

    @Test
    fun `failed pile screen text is a failed estimate and not a categorical unsafe verdict`() {
        val f = java.io.File("src/main/java/com/civileg/app/ui/compose/screens/PileFoundationScreen.kt")
        if (!f.exists()) return
        val t = f.readText()
        assertTrue(t.contains("CHECK FAILED (approximate estimate)"))
        assertFalse(t.contains("PileStatus.CHECKS_FAILED -> stringResource(R.string.pile_unsafe)"))
    }
}
