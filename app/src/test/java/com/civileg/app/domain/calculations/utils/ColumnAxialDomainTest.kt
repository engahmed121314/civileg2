package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.entities.DesignCode
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ECP 203-2020 Eq. 4-12-a is valid for e <= max(0.05t, 20 mm). 300x300: limit 20 mm. */
class ColumnAxialDomainTest {
    private val engine = CalculatorEngine(mockk(relaxed = true))

    @Test
    fun `ECP column with e above the Eq 4-12-a limit is axial not assessed and never confirmed`() {
        // Pu 600 kN, Mx 90 kN.m -> e = 150 mm (> 20 mm)
        val r = engine.designColumn(width = 300.0, depth = 300.0, pu = 600.0, mx = 90.0, my = 0.0,
            fcu = 25.0, fy = 420.0, code = DesignCode.ECP, autoIncludeSelfWeight = false)
        assertFalse(r.axialAssessed)
        assertEquals(0.0, r.axialCapacity, 0.0)
        assertFalse(r.isSafe)
        assertFalse(r.confirmedSafe)
        assertTrue(r.axialCapacityLabel.startsWith("Not assessed"))
        assertTrue(r.warnings.any { it.contains("AXIAL CAPACITY NOT ASSESSED") })
    }

    @Test
    fun `ECP column with small e keeps the Eq 4-12-a axial capacity`() {
        // Pu 300 kN, Mx 3 kN.m -> e = 10 mm (<= 20 mm)
        val r = engine.designColumn(width = 300.0, depth = 300.0, pu = 300.0, mx = 3.0, my = 0.0,
            fcu = 25.0, fy = 420.0, code = DesignCode.ECP, autoIncludeSelfWeight = false)
        assertTrue(r.axialAssessed)
        assertTrue(r.axialCapacity > 0.0)
        assertFalse(r.confirmedSafe)
    }
}
