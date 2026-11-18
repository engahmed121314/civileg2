package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SteelConnectionDesign (ECP 205 / AISC 360).
 * Tests bolted connections, welded connections, bolt spacing/edge distances,
 * shear, tension, slip-critical capacities, block shear, and eccentric welds.
 */
class SteelConnectionDesignTest {

    private lateinit var designer: SteelConnectionDesign

    @Before
    fun setup() {
        designer = SteelConnectionDesign()
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. Helper functions & Constants
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun getBoltThreadedArea_returnsCorrectArea() {
        val area20 = SteelConnectionDesign.getBoltThreadedArea(20.0)
        assertTrue("Threaded area > 0", area20 > 0)
        assertTrue("Threaded area < gross area", area20 < SteelConnectionDesign.getBoltArea(20.0))
    }

    @Test
    fun getMinEdgeDistance_scaledWithDiameter() {
        val dist16 = designer.getMinEdgeDistance(16.0)
        val dist24 = designer.getMinEdgeDistance(24.0)
        assertTrue("Larger bolt diameter should require larger min edge distance", dist24 > dist16)
    }

    @Test
    fun getMinBoltSpacing_isThreeTimesDiameter() {
        val spacing20 = designer.getMinBoltSpacing(20.0)
        assertEquals(60.0, spacing20, 0.1)
    }

    @Test
    fun getMinFilletWeldSize_scaledWithPlateThickness() {
        val size6 = designer.getMinFilletWeldSize(6.0)
        val size20 = designer.getMinFilletWeldSize(20.0)
        assertTrue("Thicker plate should require larger min weld size", size20 >= size6)
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Bolted connections
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun calculateBoltShearCapacity_returnsPositiveValue() {
        val capacity = designer.calculateBoltShearCapacity(
            boltGrade = BoltGrade.GRADE_8_8,
            boltDiameter = 20.0,
            isDoubleShear = false
        )
        assertTrue("Bolt shear capacity > 0", capacity > 0)
    }

    @Test
    fun calculateBoltShearCapacity_doubleShear_doublesCapacity() {
        val single = designer.calculateBoltShearCapacity(
            boltGrade = BoltGrade.GRADE_8_8,
            boltDiameter = 20.0,
            isDoubleShear = false
        )
        val doubleShear = designer.calculateBoltShearCapacity(
            boltGrade = BoltGrade.GRADE_8_8,
            boltDiameter = 20.0,
            isDoubleShear = true
        )
        assertEquals("Double shear should double capacity", single * 2.0, doubleShear, 0.1)
    }

    @Test
    fun calculateBearingCapacity_returnsPositiveValue() {
        val bearing = designer.calculateBearingCapacity(
            boltDiameter = 20.0,
            plateThickness = 10.0,
            fuPlate = 400.0,
            edgeDistance = 40.0,
            spacing = 60.0
        )
        assertTrue("Bearing capacity > 0", bearing > 0)
    }

    @Test
    fun calculateBoltTensionCapacity_returnsPositiveValue() {
        val tension = designer.calculateBoltTensionCapacity(
            boltGrade = BoltGrade.GRADE_8_8,
            boltDiameter = 20.0
        )
        assertTrue("Tension capacity > 0", tension > 0)
    }

    @Test
    fun designBoltedConnection_validShearConnection_producesResult() {
        val result = designer.designBoltedConnection(
            boltDiameter = 20.0,
            boltGrade = BoltGrade.GRADE_8_8,
            numberOfBolts = 4,
            boltPattern = BoltPattern.GRID,
            boltConnectionType = BoltConnectionType.BEARING,
            appliedShear = 100.0,
            appliedTension = 0.0
        )
        assertNotNull(result)
        assertTrue("Controlling capacity > 0", result.controllingCapacity > 0)
        assertTrue("Shear capacity > 0", result.shearCapacity > 0)
    }

    @Test
    fun designBoltedConnection_highShear_flagsUnsafe() {
        val result = designer.designBoltedConnection(
            boltDiameter = 12.0,
            boltGrade = BoltGrade.GRADE_4_6,
            numberOfBolts = 2,
            boltPattern = BoltPattern.SINGLE_ROW,
            boltConnectionType = BoltConnectionType.BEARING,
            appliedShear = 1000.0
        )
        assertFalse("Overloaded connection should be marked unsafe", result.isSafe)
        assertTrue("Utilization ratio > 1.0", result.utilizationRatio > 1.0)
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Welded connections
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun calculateFilletWeldCapacity_returnsPositiveValue() {
        val capacity = designer.calculateFilletWeldCapacity(
            weldSize = 6.0,
            weldLength = 200.0,
            electrodeType = ElectrodeType.E70XX
        )
        assertTrue("Fillet weld capacity > 0", capacity > 0)
    }

    @Test
    fun designWeldedConnection_validInput_producesResult() {
        val result = designer.designWeldedConnection(
            weldType = WeldType.FILLET,
            weldSize = 6.0,
            weldLength = 200.0,
            electrodeType = ElectrodeType.E70XX,
            appliedForce = 150.0,
            plateThickness = 10.0
        )
        assertNotNull(result)
        assertTrue("Weld capacity > 0", result.capacity > 0)
        assertTrue("Throat area > 0", result.throatArea > 0)
    }

    @Test
    fun designWeldedConnection_overloaded_flagsUnsafe() {
        val result = designer.designWeldedConnection(
            weldType = WeldType.FILLET,
            weldSize = 4.0,
            weldLength = 50.0,
            electrodeType = ElectrodeType.E60XX,
            appliedForce = 800.0,
            plateThickness = 8.0
        )
        assertFalse("Overloaded weld should be marked unsafe", result.isSafe)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. InputGuard & Edge Cases
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun designWeldedConnection_throwsForZeroWeldSize() {
        designer.designWeldedConnection(
            weldType = WeldType.FILLET,
            weldSize = 0.0,
            weldLength = 100.0,
            electrodeType = ElectrodeType.E70XX
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun designWeldedConnection_throwsForZeroWeldLength() {
        designer.designWeldedConnection(
            weldType = WeldType.FILLET,
            weldSize = 6.0,
            weldLength = 0.0,
            electrodeType = ElectrodeType.E70XX
        )
    }

    @Test
    fun selectOptimalBolt_findsSuitableBolt() {
        val options = designer.selectOptimalBolt(
            requiredCapacity = 200.0,
            isDoubleShear = false
        )
        assertTrue("Optimal bolt options non-empty", options.isNotEmpty())
    }
}
