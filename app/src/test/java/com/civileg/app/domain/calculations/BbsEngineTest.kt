package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

class BbsEngineTest {

    @Test
    fun `test RebarCatalog commercial diameters and unit weights`() {
        val catalog = RebarCatalog.COMMERCIAL_DIAMETERS_MM
        assertEquals(11, catalog.size)
        assertTrue(RebarCatalog.isValidDiameter(16))
        assertFalse(RebarCatalog.isValidDiameter(13))

        // Unit weight for phi 12: 0.0061654 * 144 = ~0.888 kg/m
        val w12 = RebarCatalog.unitWeightKgPerM(12)
        assertEquals(0.888, w12, 0.005)

        // Unit weight for phi 16: 0.0061654 * 256 = ~1.578 kg/m
        val w16 = RebarCatalog.unitWeightKgPerM(16)
        assertEquals(1.578, w16, 0.005)

        // Nearest catalog diameter
        assertEquals(16, RebarCatalog.closestCatalogDiameter(15.2))
        assertEquals(12, RebarCatalog.closestCatalogDiameter(11.8))
    }

    @Test
    fun `test BbsShapeParams cut length calculations`() {
        // Straight bar: a = 5000 mm
        val straightParams = BbsShapeParams(a = 5000.0)
        assertEquals(5000.0, straightParams.calculateCutLength(BbsShapeCode.STRAIGHT, 16), 0.01)

        // L-Bend: a = 4000, b = 300
        val lParams = BbsShapeParams(a = 4000.0, b = 300.0)
        assertEquals(4300.0, lParams.calculateCutLength(BbsShapeCode.L_BEND, 16), 0.01)

        // Rectangular stirrup: a = 220, b = 520, diameter = 8 mm
        // Cut length = 2*(220+520) + 2*max(10*8, 100) = 1480 + 200 = 1680 mm
        val stirrupParams = BbsShapeParams(a = 220.0, b = 520.0)
        assertEquals(1680.0, stirrupParams.calculateCutLength(BbsShapeCode.STIRRUP_RECT, 8), 0.01)
    }

    @Test
    fun `test BbsEngine row generation and total computations`() {
        val groupBottom = BarGroup(
            mark = "01",
            role = BarRole.MAIN_BOTTOM,
            diameterMm = 16,
            count = 4,
            shapeCode = BbsShapeCode.STRAIGHT,
            shapeParams = BbsShapeParams(a = 5000.0)
        )
        val groupStirrups = BarGroup(
            mark = "02",
            role = BarRole.STIRRUP,
            diameterMm = 8,
            count = 25,
            shapeCode = BbsShapeCode.STIRRUP_RECT,
            shapeParams = BbsShapeParams(a = 220.0, b = 520.0)
        )

        val row1 = BbsEngine.generateRow("B1", groupBottom, membersCount = 2)
        assertEquals("B1", row1.memberMark)
        assertEquals("01", row1.barMark)
        assertEquals(8, row1.totalBarCount) // 2 members * 4 bars
        assertEquals(5000.0, row1.cutLengthMm, 0.01)
        assertEquals(40.0, row1.totalLengthM, 0.01) // 8 * 5.0m

        val row2 = BbsEngine.generateRow("B1", groupStirrups, membersCount = 2)
        assertEquals(50, row2.totalBarCount) // 2 * 25
        assertEquals(1680.0, row2.cutLengthMm, 0.01)
        assertEquals(84.0, row2.totalLengthM, 0.01) // 50 * 1.68m

        val totals = BbsEngine.computeTotals(listOf(row1, row2), concreteVolumeM3 = 1.5)
        assertTrue(totals.totalWeightKg > 0)
        assertEquals(2, totals.diameterSummaries.size)
        assertEquals(8, totals.diameterSummaries[0].diameterMm) // phi 8
        assertEquals(16, totals.diameterSummaries[1].diameterMm) // phi 16
        assertTrue(totals.steelRatioKgPerM3 > 0)
    }

    @Test
    fun `test ElementReinforcementLayout invariants G1 G2 G3 G4`() {
        val groupMain = BarGroup("01", BarRole.MAIN_BOTTOM, 16, 4, shapeParams = BbsShapeParams(a = 5000.0))
        val groupTop = BarGroup("02", BarRole.MAIN_TOP, 12, 2, shapeParams = BbsShapeParams(a = 5000.0))
        val groupStirrup = BarGroup("03", BarRole.STIRRUP, 8, 25, shapeCode = BbsShapeCode.STIRRUP_RECT, shapeParams = BbsShapeParams(a = 220.0, b = 520.0))

        val cuts = listOf(
            SectionCut("1-1", 0.0, "Left Support", "الركيزة اليسرى", 300.0, 600.0, listOf("02", "03"), providedAsMm2 = 600.0, requiredAsMm2 = 500.0),
            SectionCut("2-2", 2500.0, "Mid Span", "منتصف البحر", 300.0, 600.0, listOf("01", "03"), providedAsMm2 = 804.0, requiredAsMm2 = 700.0),
            SectionCut("3-3", 5000.0, "Right Support", "الركيزة اليمنى", 300.0, 600.0, listOf("02", "03"), providedAsMm2 = 600.0, requiredAsMm2 = 500.0)
        )

        val details = listOf(
            DetailSpec("D1", "Stirrup Hook Detail", "تفصيلة خطاف الكانة", "ALWAYS", listOf("03"))
        )

        val layout = BbsEngine.buildLayout(
            elementTitle = "Simply Supported Beam",
            memberMark = "B1",
            caseKey = "BEAM_SIMPLE_RECT",
            coverMm = 40.0,
            membersCount = 1,
            concreteVolumeM3 = 0.9,
            groups = listOf(groupMain, groupTop, groupStirrup),
            cuts = cuts,
            details = details
        )

        assertTrue(layout.validateG1()) // Exactly 3 cuts: 1-1, 2-2, 3-3
        assertTrue(layout.validateG2()) // All marks present in schedule
        assertTrue(layout.validateG3()) // All 3 cuts providedAs >= requiredAs
        assertTrue(layout.validateG4()) // Total weight matches sum of row weights
        assertEquals(3, layout.cuts.size)
        assertEquals(3, layout.schedule.size)
    }
}
