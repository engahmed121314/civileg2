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
        
        // Hand case, ECP 203-2020 Eq. [4-12-a]:
        // 0.35*25*(90000-1200) + 0.67*420*1200 = 777000 + 337680 = 1114680 N
        val expected = 1114.68 // kN
        
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
        assertEquals(0.04, maxRatio, 0.0001)   // ECP 203-2025 p.6-55 (PDF 246): 4% middle column
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

class ECPColumnEq412DomainTest {
    private val column = ECPColumn()

    @org.junit.Test
    fun `eccentricity above max 0_05t and 20 mm is outside Eq 4-12-a and never passes`() {
        // 300x300: e_min = max(20, 15) = 20 mm. Pu 500 kN, M 50 kN.m -> e = 100 mm.
        val r = column.calculateReinforcement(25.0, 420.0, 300.0, 300.0, 500.0, 50.0, 0.0,
            com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE)
        org.junit.Assert.assertFalse(r.isSafe)
        org.junit.Assert.assertTrue(r.warnings.any { it.contains("OUTSIDE FORMULA DOMAIN") })
    }

    @org.junit.Test
    fun `small eccentricity inside domain has no domain warning`() {
        val r = column.calculateReinforcement(25.0, 420.0, 300.0, 300.0, 300.0, 3.0, 0.0,
            com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE)
        org.junit.Assert.assertTrue(r.warnings.none { it.contains("OUTSIDE FORMULA DOMAIN") })
    }

    @Test
    fun `purely axial column is not reported unsafe by the interaction screen`() {
        // Regression: with Mx = My = 0 the old scan found no diagram point at M = 0 and returned capacity 0.
        val result = column.calculateReinforcement(
            fcu = 25.0, fy = 400.0, width = 300.0, depth = 300.0,
            axialLoad = 1000.0, momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertTrue("axial only, inside Eq 4-12-a domain: ${result.codeNotes}", result.isSafe)
    }

    @Test
    fun `interaction screen still fails an overloaded column`() {
        val result = column.calculateReinforcement(
            fcu = 25.0, fy = 400.0, width = 300.0, depth = 300.0,
            axialLoad = 1000.0, momentX = 150.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )
        assertFalse(result.isSafe)
    }
}
