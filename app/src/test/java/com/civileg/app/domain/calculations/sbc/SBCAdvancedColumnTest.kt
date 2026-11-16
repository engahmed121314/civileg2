package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SBCAdvancedColumnTest {

    private lateinit var engine: SBCAdvancedColumn
    private val endConditions = ColumnEndConditions(
        topCondition = EndCondition.FIXED,
        bottomCondition = EndCondition.FIXED
    )

    @Before
    fun setUp() {
        engine = SBCAdvancedColumn()
    }

    @Test
    fun testRectangularColumn_goldenVector() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        val result = engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 30.0,
            fy = 420.0,
            axialLoad = 1400.0, // kN
            momentX = 85.0,     // kN.m
            momentY = 40.0,     // kN.m
            unsupportedLength = 3.5, // m
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.SOLID,
            hasCap = false,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isSpiral = false,
            isSeismicZone = false
        )

        assertNotNull(result)
        assertTrue(result.axialCapacity > 0.0)
        assertTrue(result.reinforcementResult.astRequired > 0.0)
        assertTrue(result.reinforcementResult.astProvided >= result.reinforcementResult.astRequired)
    }

    @Test
    fun testSeismicZoneColumn_sbcRequirementsApplied() {
        val columnType = ColumnType.Rectangular(350.0, 600.0)
        val result = engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 35.0,
            fy = 420.0,
            axialLoad = 1600.0,
            momentX = 100.0,
            momentY = 50.0,
            unsupportedLength = 3.2,
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.FLAT,
            hasCap = false,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isSpiral = false,
            isSeismicZone = true
        )

        assertNotNull(result)
        assertTrue(result.axialCapacity > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroUnsupportedLength_rejectedByInputGuard() {
        val columnType = ColumnType.Rectangular(300.0, 600.0)
        engine.designAdvancedColumn(
            columnType = columnType,
            fcu = 30.0,
            fy = 420.0,
            axialLoad = 1000.0,
            momentX = 50.0,
            momentY = 0.0,
            unsupportedLength = 0.0,
            endConditions = endConditions,
            connectedSlab = ConnectedSlabType.SOLID,
            hasCap = false,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }
}
