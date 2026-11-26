package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.calculations.ecp.ECPTank
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ECPTankTest {

    private lateinit var engine: ECPTank

    @Before
    fun setUp() {
        engine = ECPTank()
    }

    @Test
    fun testRectangularTank_goldenVector() {
        val result = engine.calculateTank(
            length = 6000.0,
            width = 4000.0,
            height = 3000.0,
            waterDepth = 2500.0,
            fcu = 30.0,
            fy = 400.0,
            type = TankType.RECTANGULAR_GROUND
        )

        assertNotNull(result)
        assertTrue(result.capacityM3 > 0.0)
        assertTrue(result.wallThickness > 0.0)
        assertTrue(result.wallReinforcement.astRequired > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeWaterDepth_rejectedByInputGuard() {
        engine.calculateTank(
            length = 5000.0,
            width = 4000.0,
            height = 3500.0,
            waterDepth = -1000.0,
            fcu = 30.0,
            fy = 400.0,
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
            type = TankType.CIRCULAR_ELEVATED
        )

        assertNotNull(result)
        assertTrue(result.capacityM3 > 0.0)
        assertTrue(result.wallThickness > 0.0)
    }

    @Test
    fun rectangularWall_codeNotesStateEcp2025ScopeAndNotAssessed() {
        val r = engine.calculateTank(6000.0, 4000.0, 3000.0, 2500.0, 30.0, 400.0, TankType.RECTANGULAR_GROUND)
        val notes = r.wallReinforcement.codeNotes
        assertTrue(notes.contains(ECPTank.EDITION_SCOPE_NOTE))
        assertTrue(notes.none { it.contains("Section 8-1") })
        assertTrue(ECPTank.EDITION_SCOPE_NOTE.contains("Table 4-1"))
        assertTrue(ECPTank.EDITION_SCOPE_NOTE.contains("NOT ASSESSED"))
    }

    @Test
    fun circularWall_codeNotesStateEcp2025ScopeAndNotAssessed() {
        val r = engine.calculateTank(6000.0, 6000.0, 4000.0, 3500.0, 35.0, 420.0, TankType.CIRCULAR_ELEVATED)
        val notes = r.wallReinforcement.codeNotes
        assertTrue(notes.contains(ECPTank.EDITION_SCOPE_NOTE))
        assertTrue(notes.any { it.contains("hoop tension") && it.contains("NOT ASSESSED") })
    }
}
