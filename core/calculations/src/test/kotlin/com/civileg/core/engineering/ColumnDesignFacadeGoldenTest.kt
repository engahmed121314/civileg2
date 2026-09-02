package com.civileg.core.engineering

import org.junit.Assert.*
import org.junit.Test

/**
 * Golden tests for [ColumnDesignFacade] (Column DoD §82 integration).
 *
 * Verifies axial + shear + ties run together, trace is aggregated, and overall
 * status reflects the worst individual result. Regression gate: spec §76-78.
 */
class ColumnDesignFacadeGoldenTest {

    private val concreteEcp = ConcreteMaterial(fcuMpa = 25.0)
    private val steelEcp = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val concreteAci = ConcreteMaterial(fcuMpa = 25.0)
    private val steelAci = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)

    @Test
    fun `ECP full column design all PASS`() {
        val facade = ColumnDesignFacade(Ecp203Params, concreteEcp, steelEcp)
        val result = facade.design(
            b = 400.0, h = 400.0, puKnm = 2000.0, momentXKnm = 100.0,
            vuKn = 50.0, isSpiral = false
        )
        assertEquals(CheckStatus.PASS, result.overallStatus)
        assertTrue(result.isSafe)
        val titles = result.trace.all.map { it.title }
        assertTrue(titles.any { it.contains("Axial capacity") })
        assertTrue(titles.any { it.contains("Ties") })
        assertTrue(titles.any { it.contains("Column shear") })
    }

    @Test
    fun `ECP shear WARNING degrades overall`() {
        val facade = ColumnDesignFacade(Ecp203Params, concreteEcp, steelEcp)
        val vc = Ecp203Params.concreteShearCapacityKn(400.0, 360.0, concreteEcp)
        val result = facade.design(
            b = 400.0, h = 400.0, puKnm = 2000.0,
            vuKn = vc * 1.5, isSpiral = false
        )
        assertEquals(CheckStatus.WARNING, result.overallStatus)
        assertTrue(result.isSafe)
    }

    @Test
    fun `ECP max steel FAIL degrades overall`() {
        val facade = ColumnDesignFacade(Ecp203Params, concreteEcp, steelEcp)
        val result = facade.design(
            b = 200.0, h = 200.0, puKnm = 750.0, vuKn = 50.0, isSpiral = false
        )
        assertEquals(CheckStatus.FAIL, result.overallStatus)
        assertFalse(result.isSafe)
    }

    @Test
    fun `ACI full column design all PASS`() {
        val facade = ColumnDesignFacade(Aci318Params, concreteAci, steelAci)
        val result = facade.design(
            b = 400.0, h = 400.0, puKnm = 2000.0, momentXKnm = 100.0,
            vuKn = 50.0, isSpiral = false
        )
        assertEquals(CheckStatus.PASS, result.overallStatus)
        assertTrue(result.isSafe)
    }

    @Test
    fun `Facade without shear still produces axial trace`() {
        val facade = ColumnDesignFacade(Ecp203Params, concreteEcp, steelEcp)
        val result = facade.design(b = 400.0, h = 400.0, puKnm = 2000.0)
        assertNull(result.shear)
        assertEquals(CheckStatus.PASS, result.overallStatus)
        assertTrue(result.trace.all.any { it.title.contains("Axial capacity") })
    }

    @Test
    fun `Aggregated trace has axial and shear entries`() {
        val facade = ColumnDesignFacade(Ecp203Params, concreteEcp, steelEcp)
        val result = facade.design(
            b = 400.0, h = 400.0, puKnm = 2000.0, momentXKnm = 100.0,
            vuKn = 50.0, isSpiral = false
        )
        // axial subtrace ≥ 5 (eccentricity? + limits + bar + capacity + ties + shear-vc)
        // shear subtrace = 2
        assertTrue("trace too small: ${result.trace.all.size}", result.trace.all.size >= 7)
    }
}
