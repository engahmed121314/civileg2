package com.civileg.app.domain.calculations

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.aci.ACIShearWall
import com.civileg.app.domain.calculations.ecp.ECPShearWall
import com.civileg.app.domain.calculations.sbc.SBCShearWall
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Cross-code divergence tests for Shear Walls across ECP 203, ACI 318, and SBC 304.
 * Verifies that all three code implementations accept identical wall input,
 * produce valid non-null design results, and reflect known code formula differences.
 */
class ShearWallCodeDivergenceTest {

    private lateinit var ecpWall: ECPShearWall
    private lateinit var aciWall: ACIShearWall
    private lateinit var sbcWall: SBCShearWall

    @Before
    fun setup() {
        ecpWall = ECPShearWall()
        aciWall = ACIShearWall()
        sbcWall = SBCShearWall()
    }

    private fun sampleInput() = ShearWallInput(
        wallType = WallType.ORDINARY,
        wallLength = 4000.0,       // mm
        wallThickness = 300.0,      // mm
        wallHeight = 3000.0,        // mm
        numberOfStories = 10,
        axialLoad = 4000.0,         // kN
        shearForce = 600.0,         // kN
        bendingMoment = 2500.0,     // kN.m
        fcu = 30.0,                 // MPa
        fy = 400.0,                 // MPa
        fyv = 250.0,                // MPa
        clearCover = 25.0           // mm
    )

    @Test
    fun allThreeCodes_produceValidResultsForSameInput() {
        val input = sampleInput()
        val ecpRes = ecpWall.designWall(input)
        val aciRes = aciWall.designWall(input)
        val sbcRes = sbcWall.designWall(input)

        assertNotNull("ECP result should not be null", ecpRes)
        assertNotNull("ACI result should not be null", aciRes)
        assertNotNull("SBC result should not be null", sbcRes)

        assertTrue("ECP moment capacity > 0", ecpRes.momentCapacity > 0)
        assertTrue("ACI moment capacity > 0", aciRes.momentCapacity > 0)
        assertTrue("SBC moment capacity > 0", sbcRes.momentCapacity > 0)

        assertTrue("ECP shear capacity > 0", ecpRes.shearCapacity > 0)
        assertTrue("ACI shear capacity > 0", aciRes.shearCapacity > 0)
        assertTrue("SBC shear capacity > 0", sbcRes.shearCapacity > 0)
    }

    @Test
    fun codeDivergence_shearCapacitiesReflectCodeFormulas() {
        val input = sampleInput()
        val ecpRes = ecpWall.designWall(input)
        val aciRes = aciWall.designWall(input)
        val sbcRes = sbcWall.designWall(input)

        // All shear capacities should be positive and order of magnitude consistent
        val maxCap = maxOf(ecpRes.shearCapacity, aciRes.shearCapacity, sbcRes.shearCapacity)
        val minCap = minOf(ecpRes.shearCapacity, aciRes.shearCapacity, sbcRes.shearCapacity)

        assertTrue("Shear capacity ratio should be reasonable (< 2.5x)", maxCap / minCap < 2.5)
    }

    @Test
    fun codeDivergence_momentCapacitiesReflectCodeFormulas() {
        val input = sampleInput()
        val ecpRes = ecpWall.designWall(input)
        val aciRes = aciWall.designWall(input)
        val sbcRes = sbcWall.designWall(input)

        val maxCap = maxOf(ecpRes.momentCapacity, aciRes.momentCapacity, sbcRes.momentCapacity)
        val minCap = minOf(ecpRes.momentCapacity, aciRes.momentCapacity, sbcRes.momentCapacity)

        assertTrue("Moment capacity ratio should be reasonable (< 2.5x)", maxCap / minCap < 2.5)
    }
}
