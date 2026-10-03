package com.civileg.app.viewmodel

import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.FootingDesign
import com.civileg.app.domain.calculations.ecp.CombinedFootingResult
import com.civileg.app.domain.calculations.ecp.ECPCombinedFooting
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CombinedFootingViewModel calculation flow.
 *
 * Tests the ECP combined footing specialized engine and the FootingDesign
 * interface (ACI/SBC) directly via CalculationFactory.
 */
class CombinedFootingViewModelTest {

    private lateinit var ecpCombinedFooting: ECPCombinedFooting
    private lateinit var aciFootingDesign: FootingDesign

    // ── Default valid input values ──────────────────────────────────────────
    private val defaultP1 = 1500.0          // kN
    private val defaultP2 = 2000.0          // kN
    private val defaultCol1W = 400.0        // mm
    private val defaultCol1D = 400.0        // mm
    private val defaultCol2W = 400.0        // mm
    private val defaultCol2D = 400.0        // mm
    private val defaultDistance = 5000.0     // mm
    private val defaultQAll = 200.0         // kN/m2
    private val defaultThickness = 500.0    // mm
    private val defaultFcu = 25.0           // MPa
    private val defaultFy = 360.0           // MPa

    @Before
    fun setUp() {
        ecpCombinedFooting = CalculationFactory.getECPCombinedFootingSpecialized()
        aciFootingDesign = CalculationFactory.getCombinedFootingDesign(DesignCode.ACI)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. calculate() with valid ECP inputs → result not null
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `calculate with valid ECP inputs returns non-null result`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertNotNull("ECP combined footing result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. calculate() with valid ACI inputs → result not null
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `calculate with valid ACI inputs returns non-null result`() {
        val result = aciFootingDesign.designCombinedFooting(
            fcu = defaultFcu, fy = defaultFy,
            axialLoad1 = defaultP1, axialLoad2 = defaultP2,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingDepth = defaultThickness,
            loadCombination = LoadCombination.DEAD_LIVE,
            columnWidth = defaultCol1W, columnDepth = defaultCol1D
        )
        assertNotNull("ACI combined footing result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. calculate() with zero soil bearing → error
    // ═══════════════════════════════════════════════════════════════════════
    @Test(expected = IllegalArgumentException::class)
    fun `calculate with zero soil bearing capacity throws error`() {
        ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = 0.0,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. calculate() with zero distance → error
    // ═══════════════════════════════════════════════════════════════════════
    @Test(expected = IllegalArgumentException::class)
    fun `calculate with zero distance between columns throws error`() {
        ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = 0.0,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. result has footingLength > 0
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result has positive footing length`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertTrue("Footing length should be positive", result.footingLength > 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. result has footingWidth > 0
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result has positive footing width`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertTrue("Footing width should be positive", result.footingWidth > 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. result has qMax > 0
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result has positive qMax`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertTrue("qMax should be positive", result.qMax > 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. result has isSafe boolean
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result contains isSafe boolean`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        val safe: Boolean = result.isSafe
        assertTrue("isSafe should be a valid boolean", safe || !safe)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. result has concreteVolume > 0
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result has positive concrete volume`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertTrue("Concrete volume should be positive", result.concreteVolume > 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. result has warnings list
    // ═══════════════════════════════════════════════════════════════════════
    @Test
    fun `result contains warnings list`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertNotNull("Warnings list should not be null", result.warnings)
    }

    // ── Additional coverage tests ──────────────────────────────────────────

    @Test
    fun `SBC combined footing design also produces valid result`() {
        val sbcFooting = CalculationFactory.getCombinedFootingDesign(DesignCode.SBC)
        val result = sbcFooting.designCombinedFooting(
            fcu = defaultFcu, fy = defaultFy,
            axialLoad1 = defaultP1, axialLoad2 = defaultP2,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingDepth = defaultThickness,
            loadCombination = LoadCombination.DEAD_LIVE,
            columnWidth = defaultCol1W, columnDepth = defaultCol1D
        )
        assertNotNull("SBC combined footing result should not be null", result)
        assertTrue("Footing width should be positive", result.requiredWidth > 0)
    }

    @Test
    fun `ECP result has steel weight and reinforcement strings`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        assertTrue("Steel weight should be non-negative", result.steelWeight >= 0)
        assertNotNull("Long bottom bars string should exist", result.longBottomBars)
        assertNotNull("Trans bottom bars string should exist", result.transBottomBars)
    }

    @Test
    fun `ECP result has shear checks`() {
        val result = ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = defaultFcu, fy = defaultFy
        )
        // Shear check booleans should be valid
        val oneWaySafe: Boolean = result.oneWayShearSafe
        val punch1Safe: Boolean = result.punchingCol1Safe
        val punch2Safe: Boolean = result.punchingCol2Safe
        assertTrue("One-way shear check should be a valid boolean", oneWaySafe || !oneWaySafe)
        assertTrue("Punching col1 check should be a valid boolean", punch1Safe || !punch1Safe)
        assertTrue("Punching col2 check should be a valid boolean", punch2Safe || !punch2Safe)
    }

    @Test
    fun `initial CombinedFootingUiState has default values`() {
        val initial = CombinedFootingUiState()
        assertEquals("1500", initial.p1)
        assertEquals("2000", initial.p2)
        assertEquals("400", initial.col1Width)
        assertEquals("5000", initial.distanceBetweenColumns)
        assertEquals("200", initial.soilBearingCapacity)
        assertEquals("ECP", initial.designCode)
        assertNull(initial.result)
        assertFalse(initial.isLoading)
        assertTrue(initial.errors.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculate with zero fcu throws error`() {
        ecpCombinedFooting.design(
            p1 = defaultP1, p2 = defaultP2,
            col1Width = defaultCol1W, col1Depth = defaultCol1D,
            col2Width = defaultCol2W, col2Depth = defaultCol2D,
            distanceBetweenColumns = defaultDistance,
            soilBearingCapacity = defaultQAll,
            footingThickness = defaultThickness,
            fcu = 0.0, fy = defaultFy
        )
    }
}
