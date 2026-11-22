package com.civileg.app.domain.drawing.adapters

import com.civileg.app.domain.drawing.Primitive
import com.civileg.app.ui.compose.components.drawings.HordiSectionPreviewStrings
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/** Language is presentation only, never a second design or a changed source hold. */
class HordiSectionPreviewStringsTest {
    private val fixtures = listOf("bottom-two-bars", "top-two-bars", "bottom-two-layers", "bottom-three-plus-two-corrected")

    @Test fun `English preview and warnings retain their source text`() {
        for (name in fixtures) {
            val f = HordiCalculatedSectionAdapterTest().fixture(name)
            val scene = HordiCalculatedSectionAdapter.build(f.handoff(), "H1").view.scene
            assertSame(scene, HordiSectionPreviewStrings.scene(scene, false))
            f.incompleteChecks.forEach { assertEquals(it, HordiSectionPreviewStrings.warning(it, false)) }
        }
    }

    @Test fun `Arabic captions preserve every geometry coordinate and dimension`() {
        for (name in fixtures) {
            val f = HordiCalculatedSectionAdapterTest().fixture(name)
            val source = HordiCalculatedSectionAdapter.build(f.handoff(), "H1").view.scene
            val arabic = HordiSectionPreviewStrings.scene(source, true)
            assertEquals(source.bounds, arabic.bounds)
            assertEquals(source.primitives.size, arabic.primitives.size)
            source.primitives.zip(arabic.primitives).forEach { (a, b) ->
                when (a) {
                    is Primitive.Text -> {
                        b as Primitive.Text
                        assertEquals(a, b.copy(text = a.text))
                        assertFalse(b.text.startsWith("نص أصلي"))
                        assertFalse(b.text.contains(Regex("Analytical|Tension|Repeating|adopted bars| mm")))
                    }
                    is Primitive.Dimension -> {
                        b as Primitive.Dimension
                        assertEquals(a, b.copy(text = a.text))
                        assertFalse(b.text.startsWith("نص أصلي"))
                        assertFalse(b.text.contains(Regex("Pitch|Web| mm")))
                    }
                    else -> assertSame(a, b)
                }
            }
        }
    }

    @Test fun `every current source hold has a separate Arabic translation without source mutation`() {
        for (name in fixtures) {
            val f = HordiCalculatedSectionAdapterTest().fixture(name)
            val source = (f.incompleteChecks + f.section.incompleteGeometry).distinct()
            val original = source.toList()
            val translated = source.map {
                assertTrue("Missing Arabic safety limitation: $it", HordiSectionPreviewStrings.hasWarningTranslation(it))
                HordiSectionPreviewStrings.warning(it, true).also { text ->
                    assertFalse(text.contains(Regex("[A-Za-z]")))
                    assertNotEquals(it, text)
                }
            }
            assertEquals(source.size, translated.distinct().size)
            assertEquals(original, source)
            assertTrue(translated.first().contains("غير مكتمل"))
            assertTrue(translated.first().contains("التصدير التنفيذي محجوبًا"))
        }
    }

    @Test fun `unknown source hold is retained with explicit Arabic untranslated warning never hidden`() {
        val source = "Future calculation limitation requiring source review."
        assertFalse(HordiSectionPreviewStrings.hasWarningTranslation(source))
        val displayed = HordiSectionPreviewStrings.warning(source, true)
        assertTrue(displayed.startsWith("قيد أصلي لم تتوفر ترجمته بعد، ولا يجوز تجاهله:"))
        assertTrue(displayed.endsWith(source))
    }

    @Test fun `Arabic method description keeps exact code and placement identifiers`() {
        val f = HordiCalculatedSectionAdapterTest().fixture("bottom-two-bars")
        val translated = HordiSectionPreviewStrings.methodReference(f.methodReference, true)
        assertTrue(translated.contains("ECP203-2020"))
        assertTrue(translated.contains(f.section.placementBasis))
        assertTrue(translated.contains("قطاع عصب محدود النطاق"))
        assertFalse(translated.contains("bounded rib section"))
        assertEquals(f.methodReference, HordiSectionPreviewStrings.methodReference(f.methodReference, false))
    }

    @Test fun `Arabic preview cannot mutate the English report or its preserved source holds`() {
        val f = HordiCalculatedSectionAdapterTest().fixture("top-two-bars")
        val drawing = HordiCalculatedSectionAdapter.build(f.handoff(), "H1")
        val originalLabels = drawing.view.scene.primitives.filterIsInstance<Primitive.Text>().map { it.text }
        HordiSectionPreviewStrings.scene(drawing.view.scene, true)
        f.incompleteChecks.forEach { HordiSectionPreviewStrings.warning(it, true) }
        assertEquals(originalLabels, drawing.view.scene.primitives.filterIsInstance<Primitive.Text>().map { it.text })
        PdfDocument(PdfReader(ByteArrayInputStream(HordiCalculatedSectionAdapter.renderReviewPdfBytes(f.handoff(), "H1")))).use { pdf ->
            assertEquals(1, pdf.numberOfPages)
            val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
            assertTrue(text.contains("REVIEW ONLY, NOT FOR CONSTRUCTION"))
            assertTrue(text.contains("SUPPLIED LINKS"))
            assertFalse(text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC })
        }
    }
}
