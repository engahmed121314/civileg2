package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorEngine
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** §16/§17 gate — column layout coordinates, tie zones and genuine P-M curve. */
class ColumnDetailingEngineTest {

    private fun eng() = CalculatorEngine(mockk(relaxed = true))

    private fun build(code: CalculatorEngine.DesignCode) =
        run {
            val res = eng().designColumn(
                width = 300.0, depth = 500.0,
                pu = 1500.0, mx = 120.0, my = 40.0,
                fcu = 25.0, fy = 400.0,
                code = code,
                clearHeight = 3200.0,
                preferredDiameter = 16
            )
            val biaxial = try {
                res.javaClass.kotlin.members.firstOrNull { it.name == "biaxialCheck" }
                    ?.call(res) as? com.civileg.app.domain.entities.BiaxialCheckResult
            } catch (_: Exception) { null }
            ColumnDetailingEngine.build(
                result = res, clearHeightMm = 3200.0, coverMm = 40.0,
                fcuMPa = 25.0, fyMPa = 400.0, codeName = "ECP 203",
                biaxial = biaxial
            )
        }

    @Test
    fun `layout places exact bar count with corners first`() {
        val d = build(CalculatorEngine.DesignCode.EGYPTIAN)
        assertEquals(4, d.layout.cornerBars.size)
        assertEquals(d.mainCount, d.layout.allBars.size)
        // corner bars sit at the four section corners (inset symmetrically)
        val xs = d.layout.cornerBars.map { it.xMm }.distinct().sorted()
        val ys = d.layout.cornerBars.map { it.yMm }.distinct().sorted()
        assertTrue(xs.size <= 2 && ys.size <= 2)
    }

    @Test
    fun `bars keep minimum clear spacing`() {
        val d = build(CalculatorEngine.DesignCode.EGYPTIAN)
        val minClear = d.layout.minBarSpacing() - d.mainDiaMm
        if (minClear < 40.0) {
            // §27: congested layout is acceptable ONLY when surfaced as a
            // constructability warning (DESIGN SAFE, CONSTRUCTION DIFFICULT)
            assertTrue(
                "congested layout ($minClear mm clear) must raise a CONSTRUCTABILITY warning",
                d.warnings.any { it.contains("CONSTRUCTABILITY") && it.contains("clear bar spacing") }
            )
        }
    }

    @Test
    fun `tie zones cover full height with densified ends`() {
        val d = build(CalculatorEngine.DesignCode.EGYPTIAN)
        assertTrue(d.tieZones.isNotEmpty())
        assertEquals(0.0, d.tieZones.first().startMm, 1e-6)
        assertEquals(3200.0, d.tieZones.last().endMm, 1e-6)
        if (d.tieZones.size == 3) {
            assertTrue(d.tieZones[0].spacingMm <= d.tieZones[1].spacingMm)
        }
    }

    @Test
    fun `pm interaction curve is genuine for ECP and honestly omitted for ACI`() {
        val ecp = build(CalculatorEngine.DesignCode.EGYPTIAN)
        assertTrue("ECP must produce a fiber P-M curve", ecp.pmCurve.size >= 10)
        assertTrue(ecp.pmCurve.any { it.P > 0 })
        assertTrue(ecp.pmCurve.maxOf { it.M } > 0)

        val aci = build(CalculatorEngine.DesignCode.ACI)
        assertTrue(aci.pmCurve.isEmpty())
        assertTrue(aci.warnings.any { it.contains("P-M") })
    }

    @Test
    fun `design point lies inside the demand plane`() {
        val d = build(CalculatorEngine.DesignCode.EGYPTIAN)
        val expectedMu = sqrt(120.0 * 120.0 + 40.0 * 40.0)
        assertEquals(expectedMu, d.designMuKnM, 1e-6)
        // Engine adds FACTORED member self-weight (ECP γg=1.4):
        // 0.3×0.5×3.2 m × 25 kN/m³ × 1.4 = 16.8 kN on top of the applied 1500
        val selfWeightKn = (300.0 / 1000) * (500.0 / 1000) * (3200.0 / 1000) * 25.0
        assertEquals(1500.0 + 1.4 * selfWeightKn, d.designPuKn, 0.5)
    }
}
