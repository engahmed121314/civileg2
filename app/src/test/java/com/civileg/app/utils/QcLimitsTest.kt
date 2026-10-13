package com.civileg.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QcLimits] — the unified ECP/ACI/SBC code-limit source
 * used by the QC engines.
 */
class QcLimitsTest {

    @Test
    fun minCover_foundationIs75mmForAllCodes() {
        assertEquals(75.0, QcLimits.minCover(ElementType.FOUNDATION, CalculatorEngine.DesignCode.EGYPTIAN).value, 1e-9)
        assertEquals(75.0, QcLimits.minCover(ElementType.FOUNDATION, CalculatorEngine.DesignCode.ACI).value, 1e-9)
        assertEquals(75.0, QcLimits.minCover(ElementType.FOUNDATION, CalculatorEngine.DesignCode.SAUDI).value, 1e-9)
    }

    @Test
    fun minCover_slabIs20AndBeamIs40() {
        val slab = QcLimits.minCover(ElementType.SLAB, CalculatorEngine.DesignCode.ACI)
        val beam = QcLimits.minCover(ElementType.BEAM, CalculatorEngine.DesignCode.ACI)
        assertEquals(20.0, slab.value, 1e-9)
        assertEquals(40.0, beam.value, 1e-9)
        assertTrue(slab.unit == "mm")
        assertTrue(slab.reference.isNotBlank())
    }

    @Test
    fun minCover_strongerExposureNeverBelowWeaker() {
        // foundation (ground contact) must require at least as much cover as a slab
        val codes = listOf(
            CalculatorEngine.DesignCode.EGYPTIAN,
            CalculatorEngine.DesignCode.ACI,
            CalculatorEngine.DesignCode.SAUDI
        )
        codes.forEach { code ->
            val slab = QcLimits.minCover(ElementType.SLAB, code).value
            val foundation = QcLimits.minCover(ElementType.FOUNDATION, code).value
            assertTrue("foundation cover < slab cover for $code", foundation >= slab)
        }
    }

    @Test
    fun slumpRange_differsByCode() {
        val ecp = QcLimits.slumpRange(CalculatorEngine.DesignCode.EGYPTIAN)
        val aci = QcLimits.slumpRange(CalculatorEngine.DesignCode.ACI)
        val sbc = QcLimits.slumpRange(CalculatorEngine.DesignCode.SAUDI)
        assertEquals(25.0, ecp.start, 1e-9)
        assertEquals(100.0, ecp.endInclusive, 1e-9)
        assertEquals(150.0, aci.endInclusive, 1e-9)
        assertEquals(120.0, sbc.endInclusive, 1e-9)
    }

    @Test
    fun airContent_alwaysThreeToEight() {
        val range = QcLimits.airContent(CalculatorEngine.DesignCode.ACI)
        assertEquals(3.0, range.start, 1e-9)
        assertEquals(8.0, range.endInclusive, 1e-9)
    }

    @Test
    fun compactionMin_is95Percent() {
        val c = QcLimits.compactionMin()
        assertEquals(95.0, c.value, 1e-9)
        assertEquals("%", c.unit)
    }

    @Test
    fun boltSpacingMin_is266d() {
        val q = QcLimits.boltSpacingMin(20.0)
        assertEquals(53.2, q.value, 1e-6)
        assertEquals("mm", q.unit)
    }

    @Test
    fun developmentLength_scalesWithDiameterAndSteelStrength() {
        val base = QcLimits.developmentLength(
            d = 20.0, fy = 400.0, fcu = 30.0, code = CalculatorEngine.DesignCode.ACI
        ).value
        val bigger = QcLimits.developmentLength(
            d = 25.0, fy = 400.0, fcu = 30.0, code = CalculatorEngine.DesignCode.ACI
        ).value
        assertTrue("larger bar should need longer development", bigger > base)

        val stronger = QcLimits.developmentLength(
            d = 20.0, fy = 500.0, fcu = 30.0, code = CalculatorEngine.DesignCode.ACI
        ).value
        assertTrue("higher fy should need longer development", stronger > base)
    }

    @Test
    fun developmentLength_allCodesReturnPositiveValue() {
        listOf(
            CalculatorEngine.DesignCode.EGYPTIAN,
            CalculatorEngine.DesignCode.ACI,
            CalculatorEngine.DesignCode.SAUDI
        ).forEach { code ->
            val ld = QcLimits.developmentLength(16.0, 420.0, 30.0, code)
            assertTrue("ld must be positive for $code", ld.value > 0.0)
            assertTrue(ld.reference.isNotBlank())
        }
    }

    @Test
    fun lapSplice_classBIsLargerThanClassA() {
        val classA = QcLimits.lapSpliceLength(20.0, 420.0, 30.0, CalculatorEngine.DesignCode.ACI, "A").value
        val classB = QcLimits.lapSpliceLength(20.0, 420.0, 30.0, CalculatorEngine.DesignCode.ACI, "B").value
        assertEquals(classA, classB / 1.3, 1e-6)
        assertTrue(classB > classA)
    }
}
