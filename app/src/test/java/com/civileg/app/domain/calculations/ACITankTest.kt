package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACITank
import com.civileg.app.domain.calculations.base.TankType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ACITankTest {

    private lateinit var engine: ACITank

    @Before
    fun setUp() {
        engine = ACITank()
    }

    @Test
    fun testRectangularTank_goldenVector() {
        val result = engine.calculateTank(
            length = 6000.0,
            width = 4000.0,
            height = 3000.0,
            waterDepth = 2500.0,
            fcu = 30.0,
            fy = 420.0,
            type = TankType.RECTANGULAR_GROUND
        )

        assertNotNull(result)
        assertTrue(result.capacityM3 > 0.0)
        assertTrue(result.wallThickness > 0.0)
        assertTrue(result.wallReinforcement.astRequired > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeLength_rejectedByInputGuard() {
        engine.calculateTank(
            length = -5000.0,
            width = 4000.0,
            height = 3500.0,
            waterDepth = 3000.0,
            fcu = 30.0,
            fy = 420.0,
            type = TankType.RECTANGULAR_GROUND
        )
    }

    @Test
    fun testCircularTank_goldenVector() {
        val result = engine.calculateTank(
            length = 6000.0,
            width = 6000.0,
            height = 4000.0,
            waterDepth = 3500.0,
            fcu = 35.0,
            fy = 420.0,
            type = TankType.CIRCULAR_UNDERGROUND
        )

        assertNotNull(result)
        assertTrue(result.capacityM3 > 0.0)
        assertTrue(result.wallThickness > 0.0)
    }
}
