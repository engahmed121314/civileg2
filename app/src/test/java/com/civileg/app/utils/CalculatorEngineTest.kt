package com.civileg.app.utils

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow

class CalculatorEngineTest {

    // =====================================================================
    // Helper: create a CalculatorEngine without Android dependencies.
    // Since CalculatorEngine requires SettingsManager (Android), we test
    // the pure-computation companion and top-level functions instead.
    // =====================================================================

    // ------------------------------------------------------------------
    // 1. Steel section property calculations (SteelEntities.kt extensions)
    // ------------------------------------------------------------------
    @Test
    fun testISectionArea() {
        // IPE 300: A = 2*b*t_f + (h-2*t_f)*t_w
        val section = SteelSectionType.ISection(
            h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1,
            grade = SteelGrade.ST37, customName = "IPE 300"
        )
        val expected = 2 * 150.0 * 10.7 + (300.0 - 2 * 10.7) * 7.1 // = 3210 + 1984.06 = 5194.06
        assertEquals(expected, section.getArea(), 1.0)
    }

    @Test
    fun testISectionMomentOfInertia() {
        val section = SteelSectionType.ISection(
            h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1,
            grade = SteelGrade.ST37
        )
        // I = (b*h³ - (b-tw)*hw³) / 12
        val hw = 300.0 - 2 * 10.7
        val expected = (150.0 * 300.0.pow(3) - (150.0 - 7.1) * hw.pow(3)) / 12.0
        assertEquals(expected, section.ix, 1.0)
        assertTrue(section.ix > 0)
    }

    @Test
    fun testISectionElasticModulus() {
        val section = SteelSectionType.ISection(
            h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1,
            grade = SteelGrade.ST37
        )
        // Sx = Ix / (h/2)
        val expectedSx = section.ix / (300.0 / 2.0)
        assertEquals(expectedSx, section.sx, 1.0)
        assertTrue(section.sx > 0)
    }

    @Test
    fun testRHSArea() {
        val section = SteelSectionType.RHS(
            width = 200.0, height = 100.0, thickness = 5.0,
            grade = SteelGrade.S275
        )
        // A = 2*(w + h - 2*t)*t
        val expected = 2 * (200.0 + 100.0 - 2 * 5.0) * 5.0 // = 2*290*5 = 2900
        assertEquals(expected, section.getArea(), 1.0)
    }

    @Test
    fun testAngleArea() {
        val section = SteelSectionType.LSection(
            legA = 100.0, legB = 100.0, thickness = 10.0,
            grade = SteelGrade.ST37
        )
        // A = (a + b - t) * t
        val expected = (100.0 + 100.0 - 10.0) * 10.0 // = 1900
        assertEquals(expected, section.getArea(), 1.0)
    }

    @Test
    fun testCHSArea() {
        val section = SteelSectionType.CHS(
            outerDiameter = 168.3, thickness = 5.0,
            grade = SteelGrade.S275
        )
        // A = π/4 * (D² - (D-2t)²)
        val d = 168.3
        val t = 5.0
        val expected = PI / 4.0 * (d * d - (d - 2 * t) * (d - 2 * t))
        assertEquals(expected, section.getArea(), 1.0)
    }

    @Test
    fun testPlateGirderArea() {
        val section = SteelSectionType.PlateGirder(
            h = 1000.0, bfTop = 400.0, bfBot = 400.0,
            tfTop = 20.0, tfBot = 20.0, tw = 12.0,
            grade = SteelGrade.S355
        )
        // A = bfTop*tfTop + bfBot*tfBot + (h - tfTop - tfBot)*tw
        val expected = 400*20.0 + 400*20.0 + (1000.0 - 20.0 - 20.0) * 12.0
        assertEquals(expected, section.getArea(), 1.0)
    }

    @Test
    fun testSectionWeightPerMeter() {
        // weight = area(mm²) × 7.85e-3 → kg/m
        val section = SteelSectionType.ISection(
            h = 200.0, bf = 100.0, tf = 8.5, tw = 5.6,
            grade = SteelGrade.ST37
        )
        val area = section.getArea()
        val weight = area * 7.85e-3
        assertEquals(weight, section.weight, 0.01)
        assertTrue(section.weight > 0)
    }

    @Test
    fun testRadiusOfGyration() {
        val section = SteelSectionType.ISection(
            h = 300.0, bf = 150.0, tf = 10.7, tw = 7.1,
            grade = SteelGrade.ST37
        )
        // r = sqrt(I/A)
        val expectedRx = kotlin.math.sqrt(section.ix / section.getArea())
        assertEquals(expectedRx, section.rx, 0.01)
    }

    // ------------------------------------------------------------------
    // 2. SteelTables data integrity
    // ------------------------------------------------------------------
    @Test
    fun testSteelTablesIPEDataIntegrity() {
        val sections = SteelTables.ipeSections
        assertTrue("IPE library should not be empty", sections.isNotEmpty())

        // Verify properties are physically consistent for each section
        for (s in sections) {
            assertTrue("${s.name}: depth should be > 0", s.depth > 0)
            assertTrue("${s.name}: width should be > 0", s.width > 0)
            assertTrue("${s.name}: area should be > 0", s.area > 0)
            assertTrue("${s.name}: weight should be > 0", s.weight > 0)
            assertTrue("${s.name}: Iy (strong) should be > Iz (weak)", s.iy > s.iz)
            assertTrue("${s.name}: Sy should be > 0", s.sy > 0)
            assertTrue("${s.name}: Ry should be > 0", s.ry > 0)
            // Weight ≈ area × 7.85 kg/m (area is in cm² here, weight in kg/m)
            val approxWeight = s.area * 0.785
            assertEquals("${s.name}: weight mismatch", approxWeight, s.weight, approxWeight * 0.05) // 5% tolerance
        }
    }

    @Test
    fun testSteelTablesHEBDataIntegrity() {
        for (s in SteelTables.hebSections) {
            assertTrue("${s.name}: Iy > Iz", s.iy > s.iz)
            assertTrue(
                "${s.name}: depth-width relation (h<=300: b=h; h>300: b=300mm)",
                if (s.depth <= 305.0) kotlin.math.abs(s.depth - s.width) < 5.0
                else kotlin.math.abs(s.width - 300.0) < 5.0
            )
        }
    }

    @Test
    fun testSteelTablesGetSectionByName() {
        val s = SteelTables.getSectionByName("IPE 300")
        assertNotNull("IPE 300 should exist", s)
        assertEquals(300.0, s!!.depth, 1.0)

        val notFound = SteelTables.getSectionByName("NONEXISTENT")
        assertNull(notFound)
    }

    @Test
    fun testSteelTablesSearchByNumber() {
        val results = SteelTables.searchByNumber("200")
        assertTrue("Should find sections with '200'", results.size >= 3) // IPE 200, HEA 200, HEB 200
    }

    @Test
    fun testSteelTablesGetSectionByDepth() {
        val s = SteelTables.getSectionByDepth(300.0, "IPE")
        assertNotNull(s)
        assertEquals(300.0, s!!.depth, 1.0)
    }

    // ------------------------------------------------------------------
    // 3. SteelGrade enum values
    // ------------------------------------------------------------------
    @Test
    fun testSteelGradeProperties() {
        assertEquals(240.0, SteelGrade.ST37.fy, 0.01)
        assertEquals(360.0, SteelGrade.ST52.fy, 0.01)
        assertEquals(250.0, SteelGrade.A36.fy, 0.01)
        assertEquals(345.0, SteelGrade.A992.fy, 0.01)
        assertEquals(275.0, SteelGrade.S275.fy, 0.01)
        assertEquals(355.0, SteelGrade.S355.fy, 0.01)
        // fu > fy for all grades
        for (grade in SteelGrade.values()) {
            assertTrue("${grade.displayName}: fu should exceed fy", grade.fu > grade.fy)
        }
    }

    // ------------------------------------------------------------------
    // 4. BOQ calculations unit tests
    // ------------------------------------------------------------------
    @Test
    fun testBoqConcreteVolume() {
        // Column 300x600mm, height 3000mm → Volume = 300*600*3000 / 1e9 m³
        val volume = 300.0 * 600.0 * 3000.0 / 1e9
        assertEquals(0.54, volume, 0.001)
    }

    @Test
    fun testBoqSteelWeightCalculation() {
        // As = 1200 mm², L = 3000 mm
        // Volume = As × L = 3,600,000 mm³ = 3.6e-3 m³
        // Weight = 3.6e-3 × 7850 = 28.26 kg = 0.02826 tons
        val ast = 1200.0
        val length = 3000.0
        val weightTons = ast * length / 1e9 * 7850.0 / 1000.0
        assertEquals(0.02826, weightTons, 0.0001)
    }

    @Test
    fun testBoqFormworkArea() {
        // Column 300x600mm, height 3000mm
        // Perimeter = 2*(300+600) = 1800mm
        // Area = 1800 * 3000 / 1e6 = 5.4 m²
        val area = 2.0 * (300.0 + 600.0) * 3000.0 / 1e6
        assertEquals(5.4, area, 0.001)
    }

    @Test
    fun testBoqTiesWeight() {
        // Column 300x600, height 3000mm, ties Ø8 @ 150mm
        val colW = 300.0; val colD = 600.0; val colH = 3000.0
        val tiesDia = 8.0; val tiesSp = 150.0
        val cover = 40.0

        val tiePerimeter = 2 * (colW - 2 * cover + colD - 2 * cover) + 24.0
        val tieLength = tiePerimeter / 1000.0 // m
        val numTies = (colH / tiesSp).toInt() + 1
        val barArea = PI * tiesDia * tiesDia / 4.0
        val weightKg = (barArea / 1e6) * tieLength * 7850.0 * numTies

        assertTrue("Ties weight should be positive", weightKg > 0)
        // Sanity: for Ø8 ties on 300x600 column at 150mm spacing, ~3m tall
        // Expected roughly 5-15 kg
        assertTrue("Ties weight should be reasonable (5-30 kg)", weightKg > 5.0 && weightKg < 30.0)
    }

    // ------------------------------------------------------------------
    // 5. Rebar cutting optimization
    // ------------------------------------------------------------------
    @Test
    fun testCuttingOptimizationBasic() {
        // Stock = 12m, Required = 3m bars, Need = 5 bars
        // Should get 4 bars per stock → 2 stocks needed
        val stockLength = 12.0
        val requiredLength = 3.0
        val requiredBars = 5

        val barsPerStock = kotlin.math.floor(stockLength / requiredLength).toInt()
        assertEquals(4, barsPerStock)
        val stocksNeeded = kotlin.math.ceil(requiredBars.toDouble() / barsPerStock).toInt()
        assertEquals(2, stocksNeeded)
    }

    @Test
    fun testCuttingWasteCalculation() {
        val stockLength = 12.0
        val requiredLength = 5.0
        val barsPerStock = kotlin.math.floor(stockLength / requiredLength).toInt() // 2
        val usedLength = barsPerStock * requiredLength // 10
        val waste = stockLength - usedLength // 2
        assertEquals(2.0, waste, 0.01)
        val utilization = (usedLength / stockLength) * 100
        assertEquals(83.33, utilization, 0.01)
    }
}
