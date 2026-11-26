package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ECPAdvancedColumnTest {

    private lateinit var engine: ECPAdvancedColumn
    private val endConditions = ColumnEndConditions(
        topCondition = EndCondition.FIXED,
        bottomCondition = EndCondition.FIXED
    )

    @Before
    fun setUp() {
        engine = ECPAdvancedColumn()
    }

    @Test
    fun testRectangularColumn_goldenVector() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        val result = engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 30.0,
            fy = 400.0,
            axialLoad = 1200.0, // kN
            momentX = 80.0,     // kN.m
            momentY = 40.0,     // kN.m
            unsupportedLength = 3.5, // m
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.SOLID,
            hasCap = false,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.axialCapacity > 0.0)
        assertTrue(result.reinforcementResult.astRequired > 0.0)
        assertTrue(result.reinforcementResult.astProvided >= result.reinforcementResult.astRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeAxialLoad_rejectedByInputGuard() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 25.0,
            fy = 400.0,
            axialLoad = -500.0,
            momentX = 50.0,
            momentY = 0.0,
            unsupportedLength = 3.0,
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.SOLID,
            hasCap = false,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test
    fun testCircularColumn_goldenVector() {
        val columnType = ColumnType.Circular(500.0)
        val result = engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 30.0,
            fy = 400.0,
            axialLoad = 1500.0,
            momentX = 100.0,
            momentY = 0.0,
            unsupportedLength = 3.0,
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.FLAT,
            hasCap = true,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.axialCapacity > 0.0)
    }

    private fun design300(lengthM: Double) = engine.designAdvancedColumn(
        columnType = ColumnType.Rectangular(300.0, 300.0),
        fcu = 30.0, fy = 400.0, axialLoad = 600.0, momentX = 20.0, momentY = 10.0,
        unsupportedLength = lengthM, endConditions = endConditions,
        connectedSlab = ConnectedSlabType.SOLID, hasCap = false, inventory = null,
        loadCombination = LoadCombination.DEAD_LIVE
    )

    @Test
    fun table68_veryLongColumnFails_verdictFlagAndFailWarning() {
        val r = design300(30.0)  // lambda_i >= 0.5 * 30000 / 90 = 166 > 100 (Table 6-8, p.6-43)
        assertTrue(r.slendernessMaxExceeded)
        assertEquals(100.0, r.slendernessMaxLimit, 0.0)
        assertTrue(r.warnings.any { it.startsWith("FAIL") && it.contains("Table 6-8") })
    }

    @Test
    fun table68_shortColumnDoesNotFail() {
        val r = design300(3.0)   // lambda_i <= 2.2 * 3000 / 90 = 73 < 100
        assertFalse(r.slendernessMaxExceeded)
        assertTrue(r.warnings.none { it.startsWith("FAIL") })
    }
}
