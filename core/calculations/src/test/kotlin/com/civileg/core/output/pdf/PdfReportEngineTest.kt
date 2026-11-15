package com.civileg.core.output.pdf

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تدقيق محرك تقارير PDF الثنائية اللغة (عربي/إنجليزي).
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالテンプレت المعتمد للتقارير الهندسية.
 *
 * // المرجع: مواصفات ملف PDF (ISO 32000-1 / PDF 1.4).
 * // الترميز: UTF-8 لدعم النص العربي (RTL).
 */
class PdfReportEngineTest {

    // =========================================================================
    // 1. رأس ملف PDF (PDF Header)
    // =========================================================================

    @Test
    fun testPdfHeader() {
        val header = PdfReportEngine.pdfHeader()
        assertTrue(header.startsWith("%PDF-1.4"))
    }

    // =========================================================================
    // 2. كائنات PDF (PDF Objects)
    // =========================================================================

    @Test
    fun testPdfObject_Wrapping() {
        val obj = PdfReportEngine.pdfObject(1, 0, "Hello World")
        assertTrue(obj.startsWith("1 0 obj"))
        assertTrue(obj.contains("Hello World"))
        assertTrue(obj.contains("endobj"))
    }

    @Test
    fun testPdfObject_Stream() {
        val streamObj = PdfReportEngine.pdfStreamObject(2, 0, "BT /F1 12 Tf 50 700 Td (Hello) Tj ET")
        assertTrue(streamObj.startsWith("2 0 obj"))
        assertTrue(streamObj.contains("stream"))
        assertTrue(streamObj.contains("BT /F1 12 Tf 50 700 Td (Hello) Tj ET"))
        assertTrue(streamObj.contains("endstream"))
        assertTrue(streamObj.contains("endobj"))
    }

    // =========================================================================
    // 3. قاموس كتالوج المستند (Document Catalog)
    // =========================================================================

    @Test
    fun testDocumentCatalog() {
        val catalog = PdfReportEngine.documentCatalog(pagesRef = "2 0 R")
        assertTrue(catalog.contains("/Type"))
        assertTrue(catalog.contains("/Catalog"))
        assertTrue(catalog.contains("/Pages"))
        assertTrue(catalog.contains("2 0 R"))
    }

    // =========================================================================
    // 4. قاموس الصفحات (Pages Dictionary)
    // =========================================================================

    @Test
    fun testPagesDictionary() {
        val pages = PdfReportEngine.pagesDictionary(
            kidsRef = "3 0 R",
            count = 1,
            mediaBox = "[0 0 595 842]"
        )
        assertTrue(pages.contains("/Type"))
        assertTrue(pages.contains("/Pages"))
        assertTrue(pages.contains("/Kids"))
        assertTrue(pages.contains("/Count"))
        assertTrue(pages.contains("/MediaBox"))
    }

    // =========================================================================
    // 5. قاموس الصفحة الواحدة (Page Dictionary)
    // =========================================================================

    @Test
    fun testPageDictionary() {
        val page = PdfReportEngine.pageDictionary(
            parentRef = "2 0 R",
            contentsRef = "4 0 R",
            resourcesRef = "5 0 R"
        )
        assertTrue(page.contains("/Type"))
        assertTrue(page.contains("/Page"))
        assertTrue(page.contains("/Parent"))
        assertTrue(page.contains("/Contents"))
        assertTrue(page.contains("/Resources"))
    }

    // =========================================================================
    // 6. تيار المحتوى (Content Stream) — مشغلات النص
    // =========================================================================

    @Test
    fun testContentStream_Text() {
        val stream = PdfReportEngine.contentStreamText(
            text = "Reporte de cálculo estructural",
            x = 50.0, y = 700.0,
            fontSize = 12.0,
            fontRef = "/F1"
        )
        assertTrue(stream.contains("BT"))
        assertTrue(stream.contains("/F1"))
        assertTrue(stream.contains("12.0"))
        assertTrue(stream.contains("Td"))
        assertTrue(stream.contains("Reporte de cálculo estructural"))
        assertTrue(stream.contains("Tj"))
        assertTrue(stream.contains("ET"))
    }

    @Test
    fun testContentStream_Text_Arabic() {
        val stream = PdfReportEngine.contentStreamText(
            text = "ملاحظة حساب خرسانية",
            x = 50.0, y = 700.0,
            fontSize = 14.0,
            fontRef = "/F2"
        )
        assertTrue(stream.contains("ملاحظة حساب خرسانية"))
        assertTrue(stream.contains("/F2"))
        assertTrue(stream.contains("14.0"))
    }

    // =========================================================================
    // 7. قاموس الموارد (Resources Dictionary) — الخطوط
    // =========================================================================

    @Test
    fun testResourcesDictionary() {
        val resources = PdfReportEngine.resourcesDictionary(
            fontF1Ref = "6 0 R",
            fontF2Ref = "7 0 R"
        )
        assertTrue(resources.contains("/Type"))
        assertTrue(resources.contains("/Resources"))
        assertTrue(resources.contains("/Font"))
        assertTrue(resources.contains("/F1"))
        assertTrue(resources.contains("/F2"))
    }

    // =========================================================================
    // 8. قاموس الخط (Font Dictionary)
    // =========================================================================

    @Test
    fun testFontDictionary() {
        val font = PdfReportEngine.fontDictionary(
            fontName = "/F1",
            baseFont = "Helvetica",
            encoding = "WinAnsiEncoding"
        )
        assertTrue(font.contains("/Type"))
        assertTrue(font.contains("/Font"))
        assertTrue(font.contains("/Subtype"))
        assertTrue(font.contains("/Type1"))
        assertTrue(font.contains("/BaseFont"))
        assertTrue(font.contains("/Helvetica"))
        assertTrue(font.contains("/Encoding"))
        assertTrue(font.contains("/WinAnsiEncoding"))
    }

    // =========================================================================
    // 9. جدول المراجع التبادلي (Cross-Reference Table)
    // =========================================================================

    @Test
    fun testXrefTable() {
        val xref = PdfReportEngine.xrefTable(
            entries = mapOf(
                1 to 9L,
                2 to 99L,
                3 to 200L
            )
        )
        assertTrue(xref.contains("xref"))
        assertTrue(xref.contains("0 4"))
        assertTrue(xref.contains("0000000000"))
        assertTrue(xref.contains("0000000009"))
        assertTrue(xref.contains("0000000099"))
        assertTrue(xref.contains("0000000200"))
    }

    // =========================================================================
    // 10. ملف PDF كامل — تجميع جميع الأجزاء
    // =========================================================================

    @Test
    fun testCompletePdfReport() {
        val pdf = PdfReportEngine.generateReport(
            titleAr = "ملاحظة حساب كمرة خرسانية",
            titleEn = "Concrete Beam Calculation Note",
            sections = listOf(
                PdfReportEngine.PdfSection(
                    titleAr = "المدخلات",
                    titleEn = "Inputs",
                    contentAr = "البعد: 300×500 مم، الطول: 6 أمتار",
                    contentEn = "Section: 300×500 mm, Length: 6 m"
                ),
                PdfReportEngine.PdfSection(
                    titleAr = "النتيجة",
                    titleEn = "Result",
                    contentAr = "مساحة الحديد المطلوبة: 1256 مم²",
                    contentEn = "Required steel area: 1256 mm²"
                )
            )
        )
        assertTrue(pdf.contains("%PDF-1.4"))
        assertTrue(pdf.contains("%%EOF"))
        assertTrue(pdf.contains("startxref"))
        assertTrue(pdf.contains("ملاحظة حساب كمرة خرسانية"))
        assertTrue(pdf.contains("Concrete Beam Calculation Note"))
        assertTrue(pdf.contains("المدخلات"))
        assertTrue(pdf.contains("Inputs"))
        assertTrue(pdf.contains("النتيجة"))
        assertTrue(pdf.contains("Result"))
    }

    // =========================================================================
    // 11. هروب الأحرف الخاصة في سلاسل PDF
    // =========================================================================

    @Test
    fun testPdfStringEscape() {
        val escaped = PdfReportEngine.escapePdfString("Line1\nLine2\tTab (parens)")
        assertTrue(escaped.contains("\\n"))
        assertTrue(escaped.contains("\\t"))
        assertTrue(escaped.contains("\\("))
        assertTrue(escaped.contains("\\)"))
    }

    // =========================================================================
    // 12. Seguridad: قيم غير صالحة تُرفض
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testPdfObject_NegativeNumberRejected() {
        PdfReportEngine.pdfObject(-1, 0, "test")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testContentStream_NegativeFontSizeRejected() {
        PdfReportEngine.contentStreamText("test", 0.0, 0.0, -5.0, "/F1")
    }
}
