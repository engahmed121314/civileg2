package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.EcpColumnDuctilityMode
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

/**
 * Golden vectors for the ECP 203-2020 §6-8 column ductility modes.
 * Clauses verified by direct page renders of the owner's ECP 203-2020 scan on
 * 2026-11-21; printed pages recorded in docs/CLAUSE_LOG.md (E-SC-1..E-SC-9).
 */
class ECPColumnDuctilityTest {

    private val column = ECPColumn()

    // LIMITED, 400x600, Hc=3000, fcu=25, fy=400, 8Φ20, ties Φ10 — printed 6-92
    @Test
    fun `limited mode golden vector - Lo and S0 per printed 6-87 and 6-92`() {
        val ast = 8.0 * PI * 20.0 * 20.0 / 4.0 // 8Φ20 = 2513.27 mm²
        val d = column.designSeismicDetailing(
            mode = EcpColumnDuctilityMode.LIMITED,
            fcu = 25.0, fy = 400.0, width = 400.0, depth = 600.0,
            clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 10.0,
            providedLongitudinalArea = ast
        )
        // Lo = max(3000/6, 600, 500) = 600  (§6-8-1-3(و) + fig 6-35, printed 6-87..6-88)
        assertEquals(600.0, d.criticalRegionLengthMm, 1e-6)
        // S0 = min(8x20=160 [fy≤420], 24x10=240, 400/2=200, 150) = 150  (printed 6-92)
        assertEquals(150.0, d.criticalTieSpacingMm, 1e-6)
        assertEquals(75.0, d.firstTieMaxOffsetMm, 1e-6)               // ≤ S0/2
        assertEquals(200.0, d.elsewhereTieSpacingMm, 1e-6)            // ≤ min(2S0, 200)
        assertFalse("ductile mode is never a complete design", d.isComplete)
        // frame-context capacity shear stays blocked (fig 6-39, printed 6-93)
        assertEquals(2, d.blockedChecks.size)
        // order-independent: bars-per-face is blocked first, capacity shear second
        val capShear = d.blockedChecks.single { it.name.contains("Capacity-design shear") }
        assertTrue(capShear.reason.contains("frame analysis"))
        // >=3 bars/face is NOT derivable from the total bar count alone:
        // arrangement unknown -> not evaluated, never a fabricated pass (printed 6-92/6-95)
        val barsBlocked = d.blockedChecks.single { it.name.contains("bars per face") }
        assertTrue(barsBlocked.reason.contains("arrangement"))
        assertTrue(d.checks.none { it.name.contains("bars per face") })
        assertTrue(d.clauseReferences.any { it.contains("6-92") })
    }

    // fy = 500 switches the bar multiplier from 8db to 6db (printed 6-92)
    @Test
    fun `limited mode fy500 uses 6db term`() {
        val d = column.designSeismicDetailing(
            mode = EcpColumnDuctilityMode.LIMITED,
            fcu = 25.0, fy = 500.0, width = 400.0, depth = 600.0,
            clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 10.0,
            providedLongitudinalArea = 2513.0
        )
        // S0 = min(6x20=120, 240, 200, 150) = 120
        assertEquals(120.0, d.criticalTieSpacingMm, 1e-6)
        assertEquals(60.0, d.firstTieMaxOffsetMm, 1e-6)
        assertEquals(200.0, d.elsewhereTieSpacingMm, 1e-6) // min(240, 200)
    }

    // ADEQUATE dimension/ratio checks — §6-8-2-3-2 (printed 6-95..6-97)
    @Test
    fun `adequate mode dimension and rho checks`() {
        val ast = 8.0 * PI * 20.0 * 20.0 / 4.0 // 2513.27 mm² → rho = 1.047% on 400x600
        val d = column.designSeismicDetailing(
            mode = EcpColumnDuctilityMode.ADEQUATE,
            fcu = 30.0, fy = 400.0, width = 400.0, depth = 600.0,
            clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 16.0,
            providedLongitudinalArea = ast, tieLegs = 4
        )
        val dim = d.checks.single { it.name.contains("Smallest section dimension") }
        assertTrue(dim.satisfied)                    // 400 ≥ 300
        val aspect = d.checks.single { it.name.contains("aspect ratio") }
        assertTrue(aspect.satisfied)                 // 400/600 = 0.667 ≥ 0.40
        val rho = d.checks.single { it.name.contains("reinforcement ratio") }
        assertTrue(rho.satisfied)                    // 1.047% ≥ 1%
        assertEquals(600.0, d.criticalRegionLengthMm, 1e-6)
        // S0 = min(160, 24x16=384, 200, 150) = 150
        assertEquals(150.0, d.criticalTieSpacingMm, 1e-6)
    }

    // Eqs [6-63a/b] confinement tie area — printed 6-98, hand-computed golden
    @Test
    fun `adequate mode confinement tie area golden vector per Eqs 6-63a and 6-63b`() {
        // 400x600, cover 40 → core 320x520, y1 = 520, Ag/Ak-1 = 240000/166400 - 1 = 0.442307...
        // fcu/γc = 30/1.5 = 20; fyst/γs = 400/1.15 = 347.8261; s = S0 = 150
        // Ash_req = 0.313 x 78000 x 0.0575 x 0.4423077 = 620.9 mm²
        // Ast_min = 0.1   x 78000 x 0.0575              = 448.5 mm²
        val fystD = 400.0 / 1.15
        val ashReq = 0.313 * 150.0 * 520.0 * (20.0 / fystD) * (240000.0 / 166400.0 - 1.0)
        val astMin = 0.1 * 150.0 * 520.0 * (20.0 / fystD)
        assertEquals(620.9, ashReq, 0.2)
        assertEquals(448.5, astMin, 0.2)

        val ast = 8.0 * PI * 20.0 * 20.0 / 4.0
        // 2 legs Φ10 = 157 mm² < 621.6 → confinement check must FAIL (not silently pass)
        val failCase = column.designSeismicDetailing(
            mode = EcpColumnDuctilityMode.ADEQUATE,
            fcu = 30.0, fy = 400.0, width = 400.0, depth = 600.0,
            clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 10.0,
            providedLongitudinalArea = ast, tieLegs = 2
        )
        val tieFail = failCase.checks.single { it.name.contains("tie area") }
        assertFalse("2Φ10 legs cannot satisfy Eq 6-63a", tieFail.satisfied)

        // 4 legs Φ16 = 804 mm² ≥ 621.6 → passes
        val passCase = column.designSeismicDetailing(
            mode = EcpColumnDuctilityMode.ADEQUATE,
            fcu = 30.0, fy = 400.0, width = 400.0, depth = 600.0,
            clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 16.0,
            providedLongitudinalArea = ast, tieLegs = 4
        )
        val tiePass = passCase.checks.single { it.name.contains("tie area") }
        assertTrue(tiePass.satisfied)
    }

    // 250x500 section must fail the adequate-mode 300 mm minimum dimension
    @Test
    fun `adequate mode rejects section smaller than 300mm`() {
        val d = column.designSeismicDetailing(
            mode = EcpColumnDuctilityMode.ADEQUATE,
            fcu = 30.0, fy = 400.0, width = 250.0, depth = 500.0,
            clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 10.0,
            providedLongitudinalArea = 2513.0
        )
        val dim = d.checks.single { it.name.contains("Smallest section dimension") }
        assertFalse(dim.satisfied)
        val aspect = d.checks.single { it.name.contains("aspect ratio") }
        assertTrue(aspect.satisfied) // 250/500 = 0.5 ≥ 0.40 still passes independently
    }

    @Test
    fun `ordinary mode is rejected by the detailing entry point`() {
        try {
            column.designSeismicDetailing(
                mode = EcpColumnDuctilityMode.ORDINARY,
                fcu = 25.0, fy = 400.0, width = 400.0, depth = 600.0,
                clearHeightMm = 3000.0, barDiameter = 20.0, tieDiameter = 10.0,
                providedLongitudinalArea = 2513.0
            )
            fail("ORDINARY must be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ── End-to-end semantics through CalculatorEngine ────────────────────────

    private val engine = CalculatorEngine(mockk(relaxed = true))

    @Test
    fun `ductile mode never returns an overall design pass`() {
        val res = engine.designColumn(
            width = 400.0, depth = 600.0, pu = 500.0, fcu = 30.0, fy = 400.0,
            code = DesignCode.ECP, clearHeight = 3000.0,
            ecpDuctilityMode = EcpColumnDuctilityMode.LIMITED
        )
        assertNotNull(res.seismicDetailing)
        assertFalse("ductile modes must not yield a clean overall pass", res.isSafe)
        assertTrue(res.warnings.any { it.contains("INCOMPLETE") })
        assertTrue(res.safetyChecks.any { it.name.startsWith("BLOCKED") && !it.isSafe })
        // confinement length carries the ECP Lo, not the ACI-style 6db/450 rule
        assertEquals(600.0, res.confinementLength, 1e-6)
    }

    @Test
    fun `default ordinary mode keeps prior result semantics - regression`() {
        val res = engine.designColumn(
            width = 400.0, depth = 600.0, pu = 500.0, fcu = 30.0, fy = 400.0,
            code = DesignCode.ECP, clearHeight = 3000.0
        )
        assertNull(res.seismicDetailing)
        assertTrue(res.safetyChecks.none { it.name.startsWith("BLOCKED") })
        // non-seismic confinement length rule unchanged
        val expected = maxOf(6.0 * res.reinforcement.diameter, 300.0)
        assertEquals(expected, res.confinementLength, 1e-6)
    }

    @Test
    fun `circular section with ductile mode is incomplete, never silent`() {
        val res = engine.designColumn(
            width = 500.0, depth = 500.0, pu = 500.0, fcu = 30.0, fy = 400.0,
            code = DesignCode.ECP, clearHeight = 3000.0, isCircular = true,
            ecpDuctilityMode = EcpColumnDuctilityMode.ADEQUATE
        )
        assertNull(res.seismicDetailing)
        assertFalse(res.isSafe)
        assertTrue(res.safetyChecks.any { it.name.startsWith("BLOCKED") })
    }

    @Test
    fun `non-ECP codes ignore the ECP ductility mode selector`() {
        val res = engine.designColumn(
            width = 400.0, depth = 600.0, pu = 500.0, fcu = 30.0, fy = 400.0,
            code = DesignCode.ACI, clearHeight = 3000.0,
            ecpDuctilityMode = EcpColumnDuctilityMode.LIMITED
        )
        assertNull(res.seismicDetailing)
        assertTrue(res.safetyChecks.none { it.name.startsWith("BLOCKED") })
    }
}
