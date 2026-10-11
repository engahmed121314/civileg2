package com.civileg.app.viewmodel

import com.civileg.app.domain.DesignMethod
import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.PanelType
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FlatSlabViewModel calculation flow.
 *
 * Tests FlatSlabDesign engines via CalculationFactory across ECP, ACI, and SBC
 * with zero dropThickness (flat slab without drop panels) and positive dropThickness.
 */
class FlatSlabViewModelTest {

    private lateinit var ecpFlatSlab: FlatSlabDesign
    private lateinit var aciFlatSlab: FlatSlabDesign
    private lateinit var sbcFlatSlab: FlatSlabDesign

    private fun createInput(dropThickness: Double = 0.0) = FlatSlabInput(
        panelType = PanelType.INTERIOR,
        designMethod = DesignMethod.DDM,
        lx = 6000.0,
        ly = 7500.0,
        slabThickness = 250.0,
        dropThickness = dropThickness,
        dropSizeX = if (dropThickness > 0) 2000.0 else 0.0,
        dropSizeY = if (dropThickness > 0) 2000.0 else 0.0,
        columnWidth = 400.0,
        columnDepth = 400.0,
        fcu = 30.0,
        fy = 400.0,
        liveLoad = 3.0,
        floorFinish = 2.0,
        numberOfFloors = 10,
        clearCover = 25.0,
        storyHeight = 3.0
    )

    @Before
    fun setUp() {
        ecpFlatSlab = CalculationFactory.getFlatSlabDesign(DesignCode.ECP)
        aciFlatSlab = CalculationFactory.getFlatSlabDesign(DesignCode.ACI)
        sbcFlatSlab = CalculationFactory.getFlatSlabDesign(DesignCode.SBC)
    }

    @Test
    fun `calculate ECP flat slab with zero dropThickness succeeds`() {
        val input = createInput(dropThickness = 0.0)
        val result = ecpFlatSlab.design(input)
        assertNotNull("ECP flat slab result should not be null", result)
        assertTrue("Total factored load > 0", result.totalFactoredLoad > 0)
    }

    @Test
    fun `calculate ECP flat slab with drop panel succeeds`() {
        val input = createInput(dropThickness = 75.0)
        val result = ecpFlatSlab.design(input)
        assertNotNull("ECP flat slab with drop panel result should not be null", result)
        assertTrue("Total factored load > 0", result.totalFactoredLoad > 0)
    }

    @Test
    fun `calculate ACI flat slab with zero dropThickness succeeds`() {
        val input = createInput(dropThickness = 0.0)
        val result = aciFlatSlab.design(input)
        assertNotNull("ACI flat slab result should not be null", result)
    }

    @Test
    fun `calculate SBC flat slab with zero dropThickness succeeds`() {
        val input = createInput(dropThickness = 0.0)
        val result = sbcFlatSlab.design(input)
        assertNotNull("SBC flat slab result should not be null", result)
    }
}
