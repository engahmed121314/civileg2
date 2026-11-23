package com.civileg.app.db

import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.components.drawings.slabDrawingSummary
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class StoredDesignSafetyTest {
    private fun saved(
        codeUsed: String = "Egyptian Code",
        results: String = """{"type":"HOLLOW_BLOCK","code":"ECP","isSafe":true}""",
        input: String = "{}",
        type: DesignType = DesignType.SLAB
    ) = Design(id = 17L, projectId = 2L, type = type, name = "Archived slab", inputData = input,
        results = results, isSafe = true, codeUsed = codeUsed, concreteVolume = 3.0, steelWeight = 60.0)

    @Test fun `pre-flag ECP shared slab is held despite stored SAFE`() {
        val old = saved()
        assertEquals(StoredDesignSafety.Status.ECP_HORDI_HOLD, StoredDesignSafety.status(old))
        val read = StoredDesignSafety.forDisplay(old)
        assertFalse(read.isSafe)
        assertTrue(old.isSafe)
        assertEquals(old.results, read.results)
        assertEquals(old.inputData, read.inputData)
        assertEquals(old.id, read.id)
        assertEquals(old.concreteVolume, read.concreteVolume, 0.0)
    }

    @Test fun `false flag does not clear known legacy ECP hold`() {
        assertTrue(StoredDesignSafety.isBlocked(saved(results =
            """{"type":"HOLLOW_BLOCK","code":"ECP","safetyAssessmentBlocked":false,"isSafe":true}""")))
    }

    @Test fun `explicit ACI and SBC records are not blanket blocked`() {
        for (code in listOf(DesignCode.ACI, DesignCode.SBC)) {
            val old = saved(codeUsed = code.displayName,
                results = """{"type":"HOLLOW_BLOCK","code":"${code.name}","isSafe":true}""")
            assertFalse(StoredDesignSafety.isBlocked(old))
            assertSame(old, StoredDesignSafety.forDisplay(old))
        }
    }

    @Test fun `input type and metadata identify ECP if old result has no discriminator`() {
        assertTrue(StoredDesignSafety.isBlocked(saved(codeUsed = "ECP 203", results = "{}",
            input = """{"type":"HOLLOW_BLOCK"}""")))
    }

    @Test fun `legacy dedicated ECP record is held with missing JSON flags`() {
        assertTrue(StoredDesignSafety.isBlocked(saved(type = DesignType.HORDI_SLAB, results = "{}")))
    }

    @Test fun `legacy dedicated combined-code label remains unresolved not attributed to ECP`() {
        val old = saved(type = DesignType.HORDI_SLAB, codeUsed = "ECP/ACI", results = "{}")
        assertEquals(StoredDesignSafety.Status.HORDI_CODE_UNRESOLVED, StoredDesignSafety.status(old))
        assertFalse(StoredDesignSafety.forDisplay(old).isSafe)
    }

    @Test fun `explicit result ACI can resolve a compatible broad metadata label`() {
        assertFalse(StoredDesignSafety.isBlocked(saved(type = DesignType.HORDI_SLAB, codeUsed = "ECP/ACI",
            results = """{"code":"ACI","isSafe":true}""")))
    }

    @Test fun `contradictory code identifiers are unresolved rather than guessed`() {
        assertEquals(StoredDesignSafety.Status.HORDI_CODE_UNRESOLVED,
            StoredDesignSafety.status(saved(results = """{"type":"HOLLOW_BLOCK","code":"ACI"}""")))
    }

    @Test fun `explicit method hold is retained on any named slab code`() {
        assertEquals(StoredDesignSafety.Status.EXPLICIT_METHOD_HOLD, StoredDesignSafety.status(
            saved(codeUsed = "American Code", results = """{"code":"ACI","safetyAssessmentBlocked":true}""")))
    }

    @Test fun `solid ECP and unrelated member records retain their status`() {
        assertFalse(StoredDesignSafety.isBlocked(saved(results = """{"type":"SOLID","code":"ECP"}""")))
        assertFalse(StoredDesignSafety.isBlocked(saved(type = DesignType.BEAM)))
    }

    @Test fun `malformed dedicated payload cannot turn known ECP Hordi into verified SAFE`() {
        assertTrue(StoredDesignSafety.isBlocked(saved(type = DesignType.HORDI_SLAB, results = "{")))
    }

    @Test fun `missing code on a known Hordi record is unresolved`() {
        assertEquals(StoredDesignSafety.Status.HORDI_CODE_UNRESOLVED,
            StoredDesignSafety.status(saved(codeUsed = "", results = """{"type":"HOLLOW_BLOCK"}""")))
    }

    @Test fun `old SlabResult JSON cannot bypass drawing or execution flags`() {
        val result = Gson().fromJson(saved().results, CalculatorEngine.SlabResult::class.java)
        assertFalse(result.safetyAssessmentBlocked)
        assertTrue(result.isSafe)
        assertTrue(result.executionAssessmentBlocked)
        assertEquals("NOT VERIFIED", slabDrawingSummary(result).safetyLabel)
    }

    @Test fun `old explicit other-code SlabResult is not classified as ECP`() {
        val result = Gson().fromJson("""{"type":"HOLLOW_BLOCK","code":"SBC","isSafe":true}""",
            CalculatorEngine.SlabResult::class.java)
        assertFalse(result.executionAssessmentBlocked)
    }
    @Test fun `known Hordi JSON missing its code cannot bypass direct execution guard`() {
        val result = Gson().fromJson("""{"type":"HOLLOW_BLOCK","isSafe":true}""", CalculatorEngine.SlabResult::class.java)
        assertTrue(result.executionAssessmentBlocked)
    }

}
