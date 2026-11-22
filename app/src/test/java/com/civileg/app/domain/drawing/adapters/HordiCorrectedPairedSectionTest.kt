package com.civileg.app.domain.drawing.adapters

import com.civileg.app.domain.drawing.Point2D
import com.civileg.app.domain.drawing.Primitive
import com.civileg.app.domain.entities.SlabCalculatedSection
import com.civileg.app.ui.compose.components.drawings.HordiSectionPreviewStrings
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/** Actual adopted source handoff. Verifies representation, not whole-member engineering approval. */
class HordiCorrectedPairedSectionTest {
    private val name = "bottom-three-plus-two-corrected"
    private val revision = "ab047eebff0cd4e5ecedcb5e8665725b7399d862d76ea2c47347afd4afa35b78"
    private fun fixture() = HordiCalculatedSectionAdapterTest().fixture(name)
    private fun record() = requireNotNull(javaClass.getResourceAsStream("/drawing/hordi/$name-frozen-record.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("record") }

    @Test fun `corrected five bars match actual frozen record result and revision`() {
        val f = fixture()
        val r = record()
        val inputs = r.get("inputJson").asString
        val outputs = r.get("resultJson").asString
        // Exact schema1 wire format from source FrozenDesignRecord, including JVM String lengths.
        val content = "civileg/completed-design/${r.get("schemaVersion").asInt}\n${inputs.length}:$inputs${outputs.length}:$outputs"
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        assertEquals(revision, digest)
        assertEquals(digest, r.get("revisionSha256").asString)
        assertEquals(digest, f.revisionId)
        val input = JsonParser.parseString(inputs).asJsonObject
        val result = JsonParser.parseString(outputs).asJsonObject
        assertEquals("STANDARD_PAIRED_UPPER", input.get("fourBarArrangementPolicy").asString)
        assertEquals(listOf(3, 2), input.getAsJsonObject("sectionContext").getAsJsonArray("tensionLayers").map { it.asJsonObject.get("count").asInt })
        assertEquals(f.section, Gson().fromJson(result.get("calculatedSection"), SlabCalculatedSection::class.java))
        assertEquals(f.requiredSteelMm2, result.get("requiredReinforcement").asDouble, 0.0)
        assertEquals(f.providedSteelMm2, result.get("providedReinforcement").asDouble, 0.0)
        assertFalse(result.get("isSafe").asBoolean)
        assertTrue(result.get("safetyAssessmentBlocked").asBoolean)
        assertTrue(result.get("boundedSectionChecksPass").asBoolean)
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H-CORRECTED")
        assertEquals(listOf(Point2D(243.0, 43.0), Point2D(300.0, 43.0), Point2D(357.0, 43.0),
            Point2D(243.0, 93.0), Point2D(357.0, 93.0)), drawing.view.scene.primitives.filterIsInstance<Primitive.RebarDot>().map { it.center })
        assertEquals(List(5) { 20.0 }, drawing.view.scene.primitives.filterIsInstance<Primitive.RebarDot>().map { it.diameterMm })
        assertEquals(1570.7963267948967, drawing.providedSteelMm2, 1e-9)
        assertFalse(drawing.executionExportAllowed)
        assertTrue(drawing.incompleteChecks.containsAll(f.incompleteChecks + f.section.incompleteGeometry))
    }

    @Test fun `selected project policy note exactly matches frozen source note without code mandate`() {
        val f = fixture()
        val result = JsonParser.parseString(record().get("resultJson").asString).asJsonObject
        val sourceNote = result.getAsJsonArray("codeNotes").map { it.asString }
            .single { it.startsWith("Four-bar arrangement policy:") }
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H-CORRECTED")
        assertEquals(sourceNote, drawing.arrangementPolicyNote)
        val arabic = HordiSectionPreviewStrings.policyNote(sourceNote, true)
        assertTrue(arabic.contains("3+2"))
        assertTrue(arabic.contains("اختيار تفصيلي للمشروع"))
        assertTrue(arabic.contains("ليس قاعدة يفرضها الكود"))
        assertTrue(arabic.contains("قيود التنفيذ ما زالت قائمة"))
        assertFalse(arabic.contains(Regex("[A-Za-z]")))
        assertEquals(sourceNote, HordiSectionPreviewStrings.policyNote(sourceNote, false))
        val legacy = HordiCalculatedSectionAdapterTest().fixture("bottom-two-layers")
        assertNull(HordiCalculatedSectionAdapter.build(legacy.handoff(), "H-LEGACY").arrangementPolicyNote)
    }

    @Test fun `corrected English review PDF retains five coordinate rows policy and all execution holds`() {
        val f = fixture()
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H-CORRECTED")
        val bytes = HordiCalculatedSectionAdapter.renderReviewPdfBytes(f.handoff(), "H-CORRECTED")
        PdfDocument(PdfReader(ByteArrayInputStream(bytes))).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1), SimpleTextExtractionStrategy())
                .replace(Regex("-\\s*\\n\\s*"), "-").replace(Regex("\\s+"), " ")
            assertTrue(text.contains(revision))
            assertTrue(text.contains(requireNotNull(drawing.arrangementPolicyNote)))
            assertTrue(text.contains("L2/B1: x 43, y 93, dia 20 mm"))
            assertTrue(text.contains("L2/B2: x 157, y 93, dia 20 mm"))
            assertTrue(text.contains("provided As = 1570.796 mm²"))
            drawing.incompleteChecks.forEach { assertTrue(text.contains(it)) }
            assertTrue(text.contains("REVIEW ONLY, NOT FOR CONSTRUCTION"))
            assertFalse(text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC })
        }
    }
}
