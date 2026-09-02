package com.civileg.app.domain.calculations.ecp

import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Test

class ECPColumnTest {
    
    private val column = ECPColumn()
    
    @Test
    fun `calculateAxialCapacity returns correct value for basic case`() {
        val capacity = column.calculateAxialCapacity(
            fcu = 25.0,
            fy = 420.0,
            width = 300.0,
            depth = 300.0,
            reinforcementArea = 1200.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        
        // Manual calculation for verification (ECP 203-2020)
        val Ag = 300.0 * 300.0 // 90000 mm²
        val Ast = 1200.0
        val concreteStress = 0.67 * 25.0 / 1.5 // 11.167 MPa
        val steelStress = 420.0 / 1.15 // 365.22 MPa
        val nominalCapacity = 0.8 * (concreteStress * (Ag - Ast) + steelStress * Ast)
        val expected = 0.65 * nominalCapacity / 1000.0 // kN
        
        assertEquals(expected, capacity, 0.1)
    }
    
    @Test
    fun `calculateReinforcement returns safe result for reasonable loads`() {
        val result = column.calculateReinforcement(
            fcu = 25.0,
            fy = 420.0,
            width = 400.0,
            depth = 400.0,
            axialLoad = 1500.0,
            momentX = 80.0,
            momentY = 40.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        
        assertTrue("Steel area should be positive", result.astRequired > 0)
        assertTrue("Provided steel should meet required", result.astProvided >= result.astRequired)
        assertTrue("Utilization should be calculated", result.utilizationRatio >= 0)
    }
    
    @Test
    fun `min and max reinforcement ratios are within code limits`() {
        val minRatio = column.getMinReinforcementRatio()
        val maxRatio = column.getMaxReinforcementRatio()

        assertEquals(0.008, minRatio, 0.0001) // 0.8%
        assertEquals(0.06, maxRatio, 0.0001)   // ECP 203 §4-2-3: 6% max (4% at laps)
        assertTrue("Min should be less than max", minRatio < maxRatio)
    }

    @Test
    fun `W9 axial capacity caps reinforcement at ECP max ratio of 6 percent`() {
        // Ag = 300×300 = 90,000 mm²; supplied Ast = 8,000 mm² (8.9%) > 6% limit
        // Capped Ast = 0.06 × 90,000 = 5,400 mm²
        // concreteStress = 0.67×25/1.5 = 11.1667 MPa, steelStress = 420/1.15 = 365.217 MPa
        // Pu = 0.65 × 0.8 × [11.1667×(90000−5400) + 365.217×5400] = 1,516.77 kN
        // (uncapped would give 1,995.53 kN — the cap must govern)
        val capacity = column.calculateAxialCapacity(
            fcu = 25.0,
            fy = 420.0,
            width = 300.0,
            depth = 300.0,
            reinforcementArea = 8000.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        val astCapped = 0.06 * 300.0 * 300.0
        val expected =
            0.65 * 0.8 * ((0.67 * 25.0 / 1.5) * (90000.0 - astCapped) + (420.0 / 1.15) * astCapped) / 1000.0
        assertEquals(expected, capacity, 0.1)
        assertEquals(1516.77, capacity, 1.0)
    }
    
    @Test
    fun `ties spacing respects code limits`() {
        val result = column.calculateReinforcement(
            fcu = 30.0, fy = 420.0,
            width = 300.0, depth = 300.0,
            axialLoad = 1000.0, momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        
        assertTrue("Ties spacing >= min", result.tiesSpacing >= column.getMinSpacing())
        assertTrue("Ties spacing <= max", result.tiesSpacing <= column.getMaxSpacing())
    }
}
