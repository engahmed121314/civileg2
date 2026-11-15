package com.civileg.core.output.dxf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تدقيق محرك ملفات DXF (Drawing Exchange Format) لبرنامج AutoCAD.
 * نسبة التفاوت المستهدفة: ≤ ±0.1% مقارنة بالمواصفات المعتمدة من Autodesk DXF Reference.
 *
 * // المرجع: Autodesk DXF Reference (Group Codes) — إصدار R12/R14/2000+
 * // تنسيق DXF: أزواج (كود المجموعة: القيمة) بأسطر متتالية.
 * // الوحدات: الأبعاد بالملم (mm) وفقًا لاتفاقية المشروع.
 */
class DxfEngineTest {

    // =========================================================================
    // 1. تنسيق أزواج أكواد المجموعة (Group Code Pairs)
    // =========================================================================

    @Test
    fun testGroupCodePair_Formatting() {
        // كل زوج endsWith newline وفقًا لتنسيق DXF
        val pair0 = DxfEngine.groupCode(0, "LINE")
        val pair8 = DxfEngine.groupCode(8, "0")
        assertEquals("0\nLINE\n", pair0)
        assertEquals("8\n0\n", pair8)
    }

    @Test
    fun testGroupCodePair_DoubleValue() {
        val pair = DxfEngine.groupCode(10, 1500.0)
        assertEquals("10\n1500.0\n", pair)
    }

    @Test
    fun testGroupCodePair_IntegerValue() {
        val pair = DxfEngine.groupCode(70, 1)
        assertEquals("70\n1\n", pair)
    }

    // =========================================================================
    // 2. قسم头 HEADER
    // =========================================================================

    @Test
    fun testHeaderSection() {
        val header = DxfEngine.headerSection()
        assertTrue(header.startsWith("0\nSECTION\n"))
        assertTrue(header.contains("2\nHEADER\n"))
        assertTrue(header.contains("0\nENDSEC\n"))
        assertTrue(header.contains("\$ACADVER"))
        assertTrue(header.contains("1\nAC1015"))
    }

    // =========================================================================
    // 3. جدول الطبقات TABLES — LAYER
    // =========================================================================

    @Test
    fun testLayerTable() {
        val layerTable = DxfEngine.layerTable(listOf("0", "CONCRETE", "REBAR", "DIMENSION"))
        assertTrue(layerTable.contains("0\nTABLE\n"))
        assertTrue(layerTable.contains("2\nLAYER\n"))
        assertTrue(layerTable.contains("70\n4"))
        assertTrue(layerTable.contains("0\nLAYER\n"))
        assertTrue(layerTable.contains("2\n0\n"))
        assertTrue(layerTable.contains("2\nCONCRETE\n"))
        assertTrue(layerTable.contains("2\nREBAR\n"))
        assertTrue(layerTable.contains("2\nDIMENSION\n"))
        assertTrue(layerTable.contains("0\nENDTAB\n"))
    }

    // =========================================================================
    // 4. الكيانات — LINE
    // =========================================================================

    @Test
    fun testLineEntity() {
        val line = DxfEngine.lineEntity(
            startX = 100.0, startY = 200.0,
            endX = 500.0, endY = 600.0,
            layer = "CONCRETE"
        )
        assertTrue(line.contains("0\nLINE\n"))
        assertTrue(line.contains("8\nCONCRETE\n"))
        assertTrue(line.contains("10\n100.0\n"))
        assertTrue(line.contains("20\n200.0\n"))
        assertTrue(line.contains("11\n500.0\n"))
        assertTrue(line.contains("21\n600.0\n"))
    }

    // =========================================================================
    // 5. الكيانات — CIRCLE
    // =========================================================================

    @Test
    fun testCircleEntity() {
        val circle = DxfEngine.circleEntity(
            centerX = 300.0, centerY = 400.0,
            radius = 50.0,
            layer = "REBAR"
        )
        assertTrue(circle.contains("0\nCIRCLE\n"))
        assertTrue(circle.contains("8\nREBAR\n"))
        assertTrue(circle.contains("10\n300.0\n"))
        assertTrue(circle.contains("20\n400.0\n"))
        assertTrue(circle.contains("40\n50.0\n"))
    }

    // =========================================================================
    // 6. الكيانات — TEXT
    // =========================================================================

    @Test
    fun testTextEntity() {
        val text = DxfEngine.textEntity(
            text = "الخرسانة المسلحة / Reinforced Concrete",
            x = 100.0, y = 100.0,
            height = 25.0,
            layer = "DIMENSION"
        )
        assertTrue(text.contains("0\nTEXT\n"))
        assertTrue(text.contains("8\nDIMENSION\n"))
        assertTrue(text.contains("10\n100.0\n"))
        assertTrue(text.contains("20\n100.0\n"))
        assertTrue(text.contains("40\n25.0\n"))
        assertTrue(text.contains("1\nالخرسانة المسلحة / Reinforced Concrete\n"))
    }

    // =========================================================================
    // 7. قسم الكيانات ENTITIES — تجميع كيانات متعددة
    // =========================================================================

    @Test
    fun testEntitiesSection() {
        val entities = DxfEngine.entitiesSection(listOf(
            DxfEngine.lineEntity(0.0, 0.0, 1000.0, 0.0, "0"),
            DxfEngine.circleEntity(500.0, 500.0, 100.0, "REBAR")
        ))
        assertTrue(entities.startsWith("0\nSECTION\n"))
        assertTrue(entities.contains("2\nENTITIES\n"))
        assertTrue(entities.contains("0\nLINE\n"))
        assertTrue(entities.contains("0\nCIRCLE\n"))
        assertTrue(entities.contains("0\nENDSEC\n"))
    }

    // =========================================================================
    // 8. ملف DXF كامل — تجميع جميع الأقسام
    // =========================================================================

    @Test
    fun testCompleteDxfFile() {
        val dxf = DxfEngine.generateCompleteDxf(
            drawingName = "Beam_Reinforcement",
            layers = listOf("0", "CONCRETE", "REBAR", "DIMENSION"),
            entities = listOf(
                DxfEngine.lineEntity(0.0, 0.0, 3000.0, 0.0, "CONCRETE"),
                DxfEngine.circleEntity(1500.0, 500.0, 16.0, "REBAR"),
                DxfEngine.textEntity("Ø16 mm", 1500.0, 500.0, 25.0, "DIMENSION")
            )
        )
        assertTrue(dxf.startsWith("0\nSECTION\n"))
        assertTrue(dxf.contains("2\nHEADER\n"))
        assertTrue(dxf.contains("0\nTABLE\n"))
        assertTrue(dxf.contains("2\nLAYER\n"))
        assertTrue(dxf.contains("0\nSECTION\n"))
        assertTrue(dxf.contains("2\nENTITIES\n"))
        assertTrue(dxf.contains("0\nEOF\n"))
        assertTrue(dxf.contains("0\nLINE\n"))
        assertTrue(dxf.contains("0\nCIRCLE\n"))
        assertTrue(dxf.contains("0\nTEXT\n"))
    }

    // =========================================================================
    // 9. أبعاد الحديد—جدول التفريد BBS كرسومات DXF
    // =========================================================================

    @Test
    fun testRebarDrawing_Dxf() {
        val rebarDxf = DxfEngine.rebarEntity(
            startX = 0.0, startY = 0.0,
            endX = 6000.0, endY = 0.0,
            diameterMm = 16.0,
            layer = "REBAR"
        )
        assertTrue(rebarDxf.contains("0\nLINE\n"))
        assertTrue(rebarDxf.contains("8\nREBAR\n"))
        assertTrue(rebarDxf.contains("10\n0.0\n"))
        assertTrue(rebarDxf.contains("11\n6000.0\n"))
    }

    // =========================================================================
    // 10. الأبعاد по سلامة الملف — EOF
    // =========================================================================

    @Test
    fun testDxfEndsWithEof() {
        val dxf = DxfEngine.generateCompleteDxf(
            drawingName = "Test",
            layers = listOf("0"),
            entities = listOf(DxfEngine.lineEntity(0.0, 0.0, 100.0, 0.0, "0"))
        )
        assertTrue(dxf.trimEnd().endsWith("0\nEOF"))
    }
}
