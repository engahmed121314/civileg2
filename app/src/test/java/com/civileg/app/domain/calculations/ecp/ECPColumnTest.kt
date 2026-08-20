package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.LoadCombination
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
        assertEquals(0.08, maxRatio, 0.0001)   // 8%
        assertTrue("Min should be less than max", minRatio < maxRatio)
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
