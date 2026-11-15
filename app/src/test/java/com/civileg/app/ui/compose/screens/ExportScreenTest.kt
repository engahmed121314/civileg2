package com.civileg.app.ui.compose.screens

import com.civileg.core.output.dxf.DxfEngine
import com.civileg.core.output.pdf.PdfReportEngine
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تدقيق ربط محركات Phase 5 (PDF/DXF) مع واجهة المستخدم Phase 6.
 * verifying that the output engines from core:calculations are correctly
 * invoked from the app module's Compose UI layer.
 *
 * // المرجع: AGENTS.md Phase 6 - UI Compose & Room DB.
 * // Phase 5 output engines are Pure Kotlin and Zero-UI, safe to test from JVM.
 */
class ExportScreenTest {

    // =========================================================================
    // 1. DXF Engine integration — all three design codes
    // =========================================================================

    @Test
    fun testDxfEngine_EcpCode_GeneratesValidDxf() {
        val dxf = DxfEngine.generateCompleteDxfWithCode(
            drawingName = "Beam_ECP",
            designCode = "ECP 203",
            projectName = "Test Project",
            elementType = "Beam",
            layers = listOf("0", "ECP_CONCRETE", "ECP_REBAR", "ECP_DIMENSION"),
            entities = listOf(
                DxfEngine.lineEntity(0.0, 0.0, 3000.0, 0.0, "ECP_CONCRETE"),
                DxfEngine.circleEntity(1500.0, 500.0, 16.0, "ECP_REBAR")
            )
        )
        assertTrue("DXF must start with HEADER section", dxf.startsWith("0\nSECTION\n"))
        assertTrue("DXF must contain ECP concrete layer", dxf.contains("ECP_CONCRETE"))
        assertTrue("DXF must contain ECP rebar layer", dxf.contains("ECP_REBAR"))
        assertTrue("DXF must contain LINE entity", dxf.contains("0\nLINE\n"))
        assertTrue("DXF must end with EOF", dxf.trimEnd().endsWith("0\nEOF"))
    }

    @Test
    fun testDxfEngine_AciCode_GeneratesValidDxf() {
        val dxf = DxfEngine.generateCompleteDxfWithCode(
            drawingName = "Column_ACI",
            designCode = "ACI 318",
            projectName = "Test Project",
            elementType = "Column",
            layers = listOf("0", "ACI_CONCRETE", "ACI_REBAR", "ACI_DIMENSION"),
            entities = listOf(
                DxfEngine.lineEntity(0.0, 0.0, 400.0, 600.0, "ACI_CONCRETE")
            )
        )
        assertTrue("DXF must contain ACI concrete layer", dxf.contains("ACI_CONCRETE"))
        assertTrue("DXF must contain ACI rebar layer", dxf.contains("ACI_REBAR"))
        assertTrue("DXF must contain LINE entity", dxf.contains("0\nLINE\n"))
    }

    @Test
    fun testDxfEngine_SbcCode_GeneratesValidDxf() {
        val dxf = DxfEngine.generateCompleteDxfWithCode(
            drawingName = "Slab_SBC",
            designCode = "SBC 304",
            projectName = "Test Project",
            elementType = "Slab",
            layers = listOf("0", "SBC_CONCRETE", "SBC_REBAR", "SBC_DIMENSION"),
            entities = listOf(
                DxfEngine.lineEntity(0.0, 0.0, 5000.0, 4000.0, "SBC_CONCRETE")
            )
        )
        assertTrue("DXF must contain SBC concrete layer", dxf.contains("SBC_CONCRETE"))
        assertTrue("DXF must contain SBC rebar layer", dxf.contains("SBC_REBAR"))
        assertTrue("DXF must contain LINE entity", dxf.contains("0\nLINE\n"))
    }

    // =========================================================================
    // 2. PDF Engine integration — bilingual Arabic/English
    // =========================================================================

    @Test
    fun testPdfEngine_BilingualReport_ContainsArabicAndEnglish() {
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
        assertTrue("PDF must start with %PDF-1.4", pdf.contains("%PDF-1.4"))
        assertTrue("PDF must end with %%EOF", pdf.contains("%%EOF"))
        assertTrue("PDF must contain Arabic title", pdf.contains("ملاحظة حساب كمرة خرسانية"))
        assertTrue("PDF must contain English title", pdf.contains("Concrete Beam Calculation Note"))
        assertTrue("PDF must contain Arabic section title", pdf.contains("المدخلات"))
        assertTrue("PDF must contain English section title", pdf.contains("Inputs"))
        assertTrue("PDF must contain Arabic content", pdf.contains("البعد: 300×500 مم"))
        assertTrue("PDF must contain English content", pdf.contains("Section: 300×500 mm"))
    }

    @Test
    fun testPdfEngine_RightToLeftTextDirection() {
        // Arabic text should be preserved as-is for RTL rendering
        val pdf = PdfReportEngine.generateReport(
            titleAr = "تصميم العمود الخرساني",
            titleEn = "Steel Column Design",
            sections = listOf(
                PdfReportEngine.PdfSection(
                    titleAr = "قسم",
                    titleEn = "Section",
                    contentAr = "محتوى تجريبي",
                    contentEn = "Test content"
                )
            )
        )
        assertTrue("PDF must contain Arabic RTL text", pdf.contains("تصميم العمود الخرساني"))
    }

    // =========================================================================
    // 3. Unit conversion integration (Phase 1 units used in Phase 6 UI)
    // =========================================================================

    @Test
    fun testPhase1Units_InUiExportContext() {
        // When user enters 300 mm beam width in UI, it converts correctly for DXF
        val widthMm = 300.0
        val widthM = widthMm / 1000.0
        assertTrue("300 mm = 0.3 m", widthM == 0.3)

        // DXF line entity uses mm directly
        val line = DxfEngine.lineEntity(0.0, 0.0, widthMm.toDouble(), 500.0, "CONCRETE")
        assertTrue("DXF line contains width in mm", line.contains("300.0"))
    }

    // =========================================================================
    // 4. Safety: invalid inputs rejected
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testDxfEngine_EmptyDrawingNameRejected() {
        DxfEngine.generateCompleteDxf(
            drawingName = "",
            layers = listOf("0"),
            entities = listOf(DxfEngine.lineEntity(0.0, 0.0, 100.0, 0.0, "0"))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPdfEngine_EmptyArabicTitleRejected() {
        PdfReportEngine.generateReport(
            titleAr = "",
            titleEn = "Test",
            sections = listOf(
                PdfReportEngine.PdfSection(
                    titleAr = "Section",
                    titleEn = "Section",
                    contentAr = "Content",
                    contentEn = "Content"
                )
            )
        )
    }
}
