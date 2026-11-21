package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.ui.compose.screens.HordiResultPresentation
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * The SBC joist blocked state (SJ-7 shear hold) must survive every boundary as
 * an explicit NOT-EVALUATED status: strict-valid JSON, safe legacy restore,
 * locale-aware UI display and English-only export rows. No NaN, no fake zero.
 */
class SbcJoistBlockedStateTest {

    private fun blockedResult() = SBCJoistSlab().designHordiSlab(
        fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 650.0,
        totalThickness = 300.0, toppingThickness = 60.0, span = 6000.0,
        designMoment = 45.0, designShear = 30.0,
        loadCombination = LoadCombination.DEAD_LIVE,
        cylinderStrengthMPa = 24.0
    )

    @Test
    fun `blocked result serializes as strict-valid JSON - no NaN token`() {
        val json = Gson().toJson(blockedResult())
        assertFalse("no NaN literal may reach storage", json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
        // Strict-mode reader (RFC 4627) must accept the payload
        val reader = JsonReader(StringReader(json))
        reader.isLenient = false
        com.google.gson.JsonParser.parseReader(reader)  // throws on any non-strict token
        // Nulls round-trip as absent/null, restored as null
        val restored = Gson().fromJson(json, com.civileg.app.domain.entities.SlabDesignResult::class.java)
        assertNull(restored.shearCapacity)
        assertNull(restored.utilizationRatio)
        assertFalse(restored.isSafe)
    }

    @Test
    fun `legacy payload with NaN token restores without crash and maps to null-safe state`() {
        // Old saves (pre-rework engine) may carry literal NaN in the JSON blob.
        // The default (lenient) Gson reader must keep loading them - no destructive
        // failure of legacy projects - and the blocked state stays non-numeric.
        val legacy = """{"requiredReinforcement":475.6,"providedReinforcement":615.75,""" +
            """"barDiameter":14.0,"barSpacing":650.0,"minThickness":300.0,""" +
            """"shearCapacity":NaN,"isSafe":false,"utilizationRatio":NaN,"warnings":[],"codeNotes":[]}"""
        val restored = Gson().fromJson(legacy, com.civileg.app.domain.entities.SlabDesignResult::class.java)
        assertFalse(restored.isSafe)
        // NaN from legacy data is never presented as a number
        val cap = restored.shearCapacity
        assertTrue(cap == null || cap.isNaN())
        assertEquals("N/E", HordiResultPresentation.exportUtilization(
            restored.utilizationRatio?.takeUnless { it.isNaN() }))
    }

    @Test
    fun `locale display - blocked labels in English and Arabic`() {
        val r = blockedResult()
        assertEquals("NOT EVALUATED — shear check blocked",
            HordiResultPresentation.shearCapacityText(r.shearCapacity, arabic = false))
        assertEquals("غير مقيّمة — فحص القص معلّق",
            HordiResultPresentation.shearCapacityText(r.shearCapacity, arabic = true))
        assertEquals("N/E", HordiResultPresentation.utilizationText(r.utilizationRatio, arabic = false))
        assertEquals("غير مقيّمة", HordiResultPresentation.utilizationText(r.utilizationRatio, arabic = true))
        assertEquals("INCOMPLETE ✗", HordiResultPresentation.statusText(r.isSafe, evaluated = false, arabic = false))
        assertEquals("غير مكتمل ✗", HordiResultPresentation.statusText(r.isSafe, evaluated = false, arabic = true))
        // evaluated values still format as numbers
        assertEquals("45%", HordiResultPresentation.utilizationText(0.45, arabic = false))
        assertEquals("12.5 kN", HordiResultPresentation.shearCapacityText(12.5, arabic = true))
    }

    @Test
    fun `export rows are English regardless of locale`() {
        val r = blockedResult()
        assertEquals("NOT EVALUATED (shear blocked)",
            HordiResultPresentation.exportShearCapacity(r.shearCapacity))
        assertEquals("N/E", HordiResultPresentation.exportUtilization(r.utilizationRatio))
        assertEquals("40.0 kN", HordiResultPresentation.exportShearCapacity(40.0))
    }
}
