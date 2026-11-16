package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ACIAdvancedColumnTest {

    private lateinit var engine: ACIAdvancedColumn
    private val endConditions = ColumnEndConditions(
        topCondition = EndCondition.FIXED,
        bottomCondition = EndCondition.FIXED
    )

    @Before
    fun setUp() {
        engine = ACIAdvancedColumn()
    }

    @Test
    fun testRectangularColumn_goldenVector() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        val result = engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 30.0,
            fy = 420.0,
            axialLoad = 1500.0, // kN
            momentX = 90.0,     // kN.m
            momentY = 45.0,     // kN.m
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
    fun testNegativeFcu_rejectedByInputGuard() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        engine.designAdvancedColumn(
            columnType = columnType,
            fcu = -30.0,
            fy = 420.0,
            axialLoad = 1000.0,
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
    fun testSpiralColumn_goldenVector() {
        val columnType = ColumnType.Circular(500.0)
        val result = engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 35.0,
            fy = 420.0,
            axialLoad = 1800.0,
            momentX = 120.0,
            momentY = 0.0,
            unsupportedLength = 3.0,
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.FLAT,
            hasCap = true,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isSpiral = true
        )

        assertNotNull(result)
        assertTrue(result.axialCapacity > 0.0)
    }
}
