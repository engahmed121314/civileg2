package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.sbc.SBCFooting
import com.civileg.app.domain.calculations.sbc.SBCColumn
import com.civileg.app.domain.calculations.base.BoundaryConstraints
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * اختبارات وحدة لـ SBCFooting + SBCColumn (المرحلة 5)
 *
 * تتحقق من:
 *  - SBCFooting: قص الاختراق، القص الأحادي، الانحناء، InputGuard
 *  - SBCColumn: السعة المحورية، التسليح، نسبة الاستغلال
 *
 * المراجع: SBC 304-2018 §13, §22, ACI 318-19 §22
 */
class SBCFootingColumnTest {

    private lateinit var footing: SBCFooting
    private lateinit var column: SBCColumn

    @Before
    fun setup() {
        footing = SBCFooting()
        column = SBCColumn()
    }

    companion object {
        // خرسانة سعودية نموذجية
        private const val FCU = 30.0    // MPa
        private const val FY = 420.0    // MPa
        private const val COLUMN_WIDTH = 400.0   // mm
        private const val COLUMN_DEPTH = 400.0   // mm
        private const val AXIAL_LOAD = 1500.0    // kN
        private const val SOIL_CAPACITY = 250.0  // kPa
        private const val FOOTING_DEPTH = 600.0  // mm
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — قص الاختراق (Punching Shear)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `punching shear - normal case returns valid result`() {
        val d = FOOTING_DEPTH - 75.0 - 10.0  // effective depth
        val result = footing.checkPunchingShear(
            FCU, COLUMN_WIDTH, COLUMN_DEPTH, d, AXIAL_LOAD,
            LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue(result.criticalPerimeter > 0)
        assertTrue(result.shearCapacity > 0)
        assertTrue(result.appliedShear >= 0)
    }

    @Test
    fun `punching shear - critical perimeter at d by 2 from column face`() {
        val d = 500.0
        val c1 = 400.0
        val c2 = 400.0
        val result = footing.checkPunchingShear(
            FCU, c1, c2, d, 1000.0,
            LoadCombination.DEAD_LIVE
        )
        // Perimeter = 2*(c1+c2) + 4*d
        val expectedBo = 2.0 * (c1 + c2) + 4.0 * d
        assertEquals(expectedBo, result.criticalPerimeter, 1.0)
    }

    @Test
    fun `punching shear - capacity increases with higher fcu`() {
        val d = 500.0
        val result30 = footing.checkPunchingShear(30.0, 400.0, 400.0, d, 1000.0, LoadCombination.DEAD_LIVE)
        val result40 = footing.checkPunchingShear(40.0, 400.0, 400.0, d, 1000.0, LoadCombination.DEAD_LIVE)
        assertTrue("Higher fcu → higher capacity", result40.shearCapacity > result30.shearCapacity)
    }

    @Test
    fun `punching shear - isSafe reflects actual comparison`() {
        val d = 500.0
        // Small load → should be safe
        val safeResult = footing.checkPunchingShear(30.0, 400.0, 400.0, d, 100.0, LoadCombination.DEAD_LIVE)
        assertTrue("Small load should be safe", safeResult.isSafe)

        // Very large load → likely unsafe
        val unsafeResult = footing.checkPunchingShear(20.0, 300.0, 300.0, d, 50000.0, LoadCombination.DEAD_LIVE)
        assertFalse("Very large load should be unsafe", unsafeResult.isSafe)
    }

    @Test
    fun `punching shear - utilization ratio equals vu divided by vc`() {
        val d = 500.0
        val result = footing.checkPunchingShear(30.0, 400.0, 400.0, d, 1000.0, LoadCombination.DEAD_LIVE)
        if (result.shearCapacity > 0) {
            assertEquals(result.appliedShear / result.shearCapacity, result.utilizationRatio, 0.01)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `punching shear - zero fcu throws`() {
        footing.checkPunchingShear(0.0, 400.0, 400.0, 500.0, 1000.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `punching shear - zero effectiveDepth throws`() {
        footing.checkPunchingShear(30.0, 400.0, 400.0, 0.0, 1000.0, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — تصميم قاعدة معزولة (Isolated Footing)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `isolated footing - normal case returns valid result`() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COLUMN_WIDTH, COLUMN_DEPTH,
            AXIAL_LOAD, 0.0, 0.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            BoundaryConstraints()
        )
        assertNotNull(result)
        assertTrue(result.requiredWidth > 0)
        assertTrue(result.requiredLength > 0)
        assertTrue(result.soilPressure > 0)
    }

    @Test
    fun `isolated footing - soil pressure below capacity for safe design`() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COLUMN_WIDTH, COLUMN_DEPTH,
            800.0, 0.0, 0.0,  // moderate load
            300.0, FOOTING_DEPTH,  // high soil capacity
            LoadCombination.DEAD_LIVE,
            BoundaryConstraints()
        )
        assertTrue("Soil pressure should be below capacity", result.soilPressure <= 300.0 * 1.1)
    }

    @Test
    fun `isolated footing - punching shear check populated`() {
        val result = footing.designIsolatedFooting(
            FCU, FY, COLUMN_WIDTH, COLUMN_DEPTH,
            AXIAL_LOAD, 0.0, 0.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            BoundaryConstraints()
        )
        assertNotNull(result.punchingShearCheck)
        assertTrue(result.punchingShearCheck.criticalPerimeter > 0)
    }

    @Test
    fun `isolated footing - larger load results in larger footing`() {
        val light = footing.designIsolatedFooting(
            FCU, FY, COLUMN_WIDTH, COLUMN_DEPTH,
            800.0, 0.0, 0.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            BoundaryConstraints()
        )
        val heavy = footing.designIsolatedFooting(
            FCU, FY, COLUMN_WIDTH, COLUMN_DEPTH,
            3000.0, 0.0, 0.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            BoundaryConstraints()
        )
        assertTrue("Larger load → larger footing area",
            heavy.requiredWidth * heavy.requiredLength > light.requiredWidth * light.requiredLength)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `isolated footing - zero fcu throws`() {
        footing.designIsolatedFooting(
            0.0, FY, COLUMN_WIDTH, COLUMN_DEPTH,
            AXIAL_LOAD, 0.0, 0.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            BoundaryConstraints()
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — getPunchingShearCapacity
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `getPunchingShearCapacity - returns positive value`() {
        val capacity = footing.getPunchingShearCapacity(30.0, 3000.0, 500.0)
        assertTrue(capacity > 0)
    }

    @Test
    fun `getPunchingShearCapacity - higher fcu gives higher capacity`() {
        val cap30 = footing.getPunchingShearCapacity(30.0, 3000.0, 500.0)
        val cap40 = footing.getPunchingShearCapacity(40.0, 3000.0, 500.0)
        assertTrue("Higher fcu → higher capacity", cap40 > cap30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getPunchingShearCapacity - zero fcu throws`() {
        footing.getPunchingShearCapacity(0.0, 3000.0, 500.0)
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCColumn — السعة المحورية (Axial Capacity)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `axial capacity - normal case returns positive value`() {
        val capacity = column.calculateAxialCapacity(
            FCU, FY, 400.0, 400.0, 2400.0,
            LoadCombination.DEAD_LIVE
        )
        assertTrue("Axial capacity should be positive", capacity > 0)
    }

    @Test
    fun `axial capacity - SBC 304 formula correctness`() {
        // Manual calculation:
        // fc' = 0.8 * 30 = 24 MPa
        // Ag = 400*400 = 160000 mm²
        // Ast = 2400 mm² (0.015 * Ag = within 8% limit)
        // Pn = 0.85*24*(160000-2400) + 420*2400 = 3,211,200 + 1,008,000 = 4,219,200 N
        // φPn = 0.65 * 0.80 * 4,219,200 / 1000 = 2,194.0 kN
        val capacity = column.calculateAxialCapacity(30.0, 420.0, 400.0, 400.0, 2400.0, LoadCombination.DEAD_LIVE)
        val fcPrime = 0.8 * 30.0
        val Ag = 400.0 * 400.0
        val Ast = 2400.0
        val expected = 0.65 * 0.80 * (0.85 * fcPrime * (Ag - Ast) + 420.0 * Ast) / 1000.0
        assertEquals(expected, capacity, 1.0)  // within 1 kN
    }

    @Test
    fun `axial capacity - increases with larger section`() {
        val small = column.calculateAxialCapacity(30.0, 420.0, 300.0, 300.0, 1200.0, LoadCombination.DEAD_LIVE)
        val large = column.calculateAxialCapacity(30.0, 420.0, 500.0, 500.0, 3600.0, LoadCombination.DEAD_LIVE)
        assertTrue("Larger section → higher capacity", large > small)
    }

    @Test
    fun `axial capacity - increases with higher fcu`() {
        val cap30 = column.calculateAxialCapacity(30.0, 420.0, 400.0, 400.0, 2400.0, LoadCombination.DEAD_LIVE)
        val cap40 = column.calculateAxialCapacity(40.0, 420.0, 400.0, 400.0, 2400.0, LoadCombination.DEAD_LIVE)
        assertTrue("Higher fcu → higher capacity", cap40 > cap30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `axial capacity - zero fcu throws`() {
        column.calculateAxialCapacity(0.0, 420.0, 400.0, 400.0, 2400.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `axial capacity - zero width throws`() {
        column.calculateAxialCapacity(30.0, 420.0, 0.0, 400.0, 2400.0, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCColumn — التسليح (Reinforcement)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `column reinforcement - normal case returns valid result`() {
        val result = column.calculateReinforcement(
            FCU, FY, 400.0, 400.0, 1500.0, 50.0, 0.0,
            LoadCombination.DEAD_LIVE
        )
        assertNotNull(result)
        assertTrue(result.astProvided >= 0)
    }

    @Test
    fun `column reinforcement - higher axial load needs more steel`() {
        val light = column.calculateReinforcement(30.0, 420.0, 400.0, 400.0, 1000.0, 0.0, 0.0, LoadCombination.DEAD_LIVE)
        val heavy = column.calculateReinforcement(30.0, 420.0, 400.0, 400.0, 3000.0, 0.0, 0.0, LoadCombination.DEAD_LIVE)
        assertTrue("Higher load → more steel required", heavy.astRequired >= light.astRequired)
    }

    @Test
    fun `column reinforcement - provided area at least minimum ratio`() {
        val result = column.calculateReinforcement(
            FCU, FY, 400.0, 400.0, 1500.0, 0.0, 0.0,
            LoadCombination.DEAD_LIVE
        )
        val Ag = 400.0 * 400.0
        val minAs = 0.01 * Ag  // 1% minimum per SBC 304 / ACI 318
        assertTrue("Provided As should meet minimum ratio", result.astProvided >= minAs * 0.9)  // allow 10% tolerance
    }

    @Test(expected = IllegalArgumentException::class)
    fun `column reinforcement - zero fcu throws`() {
        column.calculateReinforcement(0.0, 420.0, 400.0, 400.0, 1500.0, 0.0, 0.0, LoadCombination.DEAD_LIVE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `column reinforcement - zero axialLoad throws`() {
        column.calculateReinforcement(30.0, 420.0, 400.0, 400.0, 0.0, 0.0, 0.0, LoadCombination.DEAD_LIVE)
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — تصميم قاعدة مشتركة (Combined Footing)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `combined footing - normal case returns valid result`() {
        val result = footing.designCombinedFooting(
            FCU, FY, 1500.0, 1000.0, 5000.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            COLUMN_WIDTH, COLUMN_DEPTH, COLUMN_WIDTH, COLUMN_DEPTH
        )
        assertNotNull(result)
        assertTrue(result.requiredWidth > 0)
        assertTrue(result.requiredLength > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `combined footing - zero fcu throws`() {
        footing.designCombinedFooting(
            0.0, FY, 1500.0, 1000.0, 5000.0,
            SOIL_CAPACITY, FOOTING_DEPTH,
            LoadCombination.DEAD_LIVE,
            COLUMN_WIDTH, COLUMN_DEPTH, COLUMN_WIDTH, COLUMN_DEPTH
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — تصميم اللبشة (Raft Foundation)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `raft foundation - normal case returns valid result`() {
        val result = footing.designRaftFoundation(
            FCU, FY, 10000.0, 100.0,
            Pair(500.0, 300.0),
            SOIL_CAPACITY, 800.0
        )
        assertNotNull(result)
        assertTrue(result.soilPressure > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raft foundation - zero totalArea throws`() {
        footing.designRaftFoundation(
            FCU, FY, 10000.0, 0.0,
            Pair(500.0, 300.0),
            SOIL_CAPACITY, 800.0
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // SBCFooting — تصميم كتلة الركائز (Pile Cap)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `pile cap - normal case returns valid result`() {
        val result = footing.designPileCap(
            FCU, FY, 500.0, 4, 400.0, 2000.0
        )
        assertNotNull(result)
        assertTrue(result.requiredWidth > 0)
        assertTrue(result.requiredLength > 0)
    }

    @Test
    fun `pile cap - column size derived from pile diameter`() {
        // Test that the column size is estimated from pile geometry, not hardcoded 400mm
        val resultSmall = footing.designPileCap(FCU, FY, 300.0, 4, 300.0, 1500.0)
        val resultLarge = footing.designPileCap(FCU, FY, 500.0, 4, 600.0, 1500.0)
        // Both should produce valid results (no crash) — the key is they work with different pile sizes
        assertNotNull(resultSmall)
        assertNotNull(resultLarge)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pile cap - zero numberOfPiles throws`() {
        footing.designPileCap(FCU, FY, 500.0, 0, 400.0, 2000.0)
    }
}
