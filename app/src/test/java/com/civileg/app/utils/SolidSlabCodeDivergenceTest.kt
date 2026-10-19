package com.civileg.app.utils

import com.civileg.app.domain.entities.*
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * P0-2 regression guard for CalculatorEngine.designSlab (solid panels).
 *
 * The old inline path applied ECP's 1.4D+1.6L factored load and an ECP-style 0.87·fy lever
 * arm to every code, so ACI and SBC returned ECP numbers byte-for-byte — the 2026-10 parity
 * probe found the solid slab to be one of only two elements that were identical for all three
 * codes. designSlab now delegates to CalculationFactory.getSlabDesign(code) (γs K-method with
 * fcu for ECP vs φ=0.9 Rn-ρ with 0.8·fcu for ACI/SBC, plus per-code ρmin, max bar spacing and
 * deflection limits) and only maps the engine result onto SlabResult.
 */
class SolidSlabCodeDivergenceTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))

    private val allCodes = listOf(DesignCode.ECP, DesignCode.ACI, DesignCode.SBC)

    /** 4.0 x 5.0 m panel — two-way (ly/lx = 1.25 ≤ 2), 6+3 kN/m², fcu 30 / fy 420. */
    private fun twoWay(code: DesignCode, ts: Double = 150.0) = engine.designSlab(
        lx = 4.0, ly = 5.0, deadLoad = 6.0, liveLoad = 3.0,
        fcu = 30.0, fy = 420.0, ts = ts, preferredDiameter = 12,
        code = code, type = CalculatorEngine.SlabType.SOLID
    )

    /** 3.0 x 8.0 m strip — one-way (ly/lx = 2.67 > 2). */
    private fun oneWay(code: DesignCode) = engine.designSlab(
        lx = 3.0, ly = 8.0, deadLoad = 6.0, liveLoad = 3.0,
        fcu = 30.0, fy = 420.0, ts = 150.0, preferredDiameter = 12,
        code = code, type = CalculatorEngine.SlabType.SOLID
    )

    /** The code-required main steel (mm²/m) as reported by the "Flexure (main)" check row. */
    private fun requiredSteel(r: CalculatorEngine.SlabResult): Double =
        r.safetyChecks.first { it.name.startsWith("Flexure (main)") }.limit

    @Test
    fun `every code returns a complete two-way result`() {
        for (code in allCodes) {
            val r = twoWay(code)
            assertEquals("code is echoed back", code, r.code)
            assertTrue("$code momentX finite and > 0", r.momentX.isFinite() && r.momentX > 0.0)
            assertTrue("$code momentY finite and > 0 (two-way)", r.momentY.isFinite() && r.momentY > 0.0)
            assertTrue("$code totalLoad > 0", r.totalLoad > 0.0)
            assertTrue("$code minThickness > 0", r.minThickness > 0.0)
            assertTrue("$code main steel placed", r.reinforcementMain.spacing > 0.0)
            assertTrue("$code secondary steel placed", r.reinforcementSecondary.spacing > 0.0)
            assertEquals("preferred bar diameter kept for main steel", 12, r.reinforcementMain.diameter)
            assertEquals("preferred bar diameter kept for secondary steel", 12, r.reinforcementSecondary.diameter)
            assertTrue("$code concrete volume > 0", r.concreteVolume > 0.0)
            assertTrue("$code steel weight > 0", r.steelWeight > 0.0)
            assertTrue("$code utilization > 0", r.utilizationRatio > 0.0)
            assertTrue("$code has the 4 safety checks (flexure x2, shear, thickness)",
                r.safetyChecks.size >= 4)
        }
    }

    @Test
    fun `factored panel load is code specific`() {
        // ECP 203 §2-3-1: 1.4D + 1.6L  |  ACI 318 §5.3 / SBC 304: 1.2D + 1.6L
        assertEquals("ECP", 1.4 * 6.0 + 1.6 * 3.0, twoWay(DesignCode.ECP).totalLoad, 1e-9)
        assertEquals("ACI", 1.2 * 6.0 + 1.6 * 3.0, twoWay(DesignCode.ACI).totalLoad, 1e-9)
        assertEquals("SBC", 1.2 * 6.0 + 1.6 * 3.0, twoWay(DesignCode.SBC).totalLoad, 1e-9)
        assertNotEquals("ECP load differs from ACI/SBC",
            twoWay(DesignCode.ECP).totalLoad, twoWay(DesignCode.ACI).totalLoad, 0.0)
    }

    @Test
    fun `two-way display moments follow each engine's own convention`() {
        // ECP: positive coefficient x span of that direction.
        // ACI/SBC: max(positive, support) x lx² (their long coefficient is pre-scaled by 1/ratio²).
        // ratio = 1.25 → ECP (neg/pos short 0.061/0.046, neg/pos long 0.038/0.029),
        // ACI·SBC shortCoeff 0.070, cs 0.076, cm 0.071.
        val wuEcp = 1.4 * 6.0 + 1.6 * 3.0
        val wuAci = 1.2 * 6.0 + 1.6 * 3.0
        assertEquals("ECP Mx", 0.046 * wuEcp * 16.0, twoWay(DesignCode.ECP).momentX, 1e-9)
        assertEquals("ECP My", 0.029 * wuEcp * 25.0, twoWay(DesignCode.ECP).momentY, 1e-9)
        assertEquals("ACI Mx", 0.076 * wuAci * 16.0, twoWay(DesignCode.ACI).momentX, 1e-9)
        assertEquals("ACI My", 0.071 * wuAci * 16.0, twoWay(DesignCode.ACI).momentY, 1e-9)
        // SBC 304 follows ACI for two-way panels (SBCSlab.designTwoWaySlab)
        assertEquals("SBC Mx == ACI Mx", twoWay(DesignCode.ACI).momentX, twoWay(DesignCode.SBC).momentX, 1e-9)
    }

    @Test
    fun `ECP and ACI require different steel for the same panel`() {
        val ecp = twoWay(DesignCode.ECP)
        val aci = twoWay(DesignCode.ACI)
        // ECP: γs K-method + its own 0.26√fcu/fy minimum → ~422 mm²/m
        // ACI: φ=0.9 Rn-ρ + 0.0018·b·d minimum → ~320 mm²/m
        assertNotEquals("different required steel", requiredSteel(ecp), requiredSteel(aci), 1e-9)
        assertNotEquals("different spacing", ecp.reinforcementMain.spacing, aci.reinforcementMain.spacing, 1e-9)
        assertNotEquals("different steel weight", ecp.steelWeight, aci.steelWeight, 1e-9)
    }

    @Test
    fun `SBC minimum steel ratio makes it differ from ACI`() {
        // ACI ρmin = 0.0018 (ACI 318 §7.6.1.1) vs SBC ρmin = 0.25·√fc'/fy (SBC 304 §8.4)
        // → same flexural demand, different governing minimum steel
        val aci = twoWay(DesignCode.ACI)
        val sbc = twoWay(DesignCode.SBC)
        assertNotEquals("SBC must demand more minimum steel than ACI",
            requiredSteel(aci), requiredSteel(sbc), 1e-9)
        assertNotEquals("different spacing", aci.reinforcementMain.spacing, sbc.reinforcementMain.spacing, 1e-9)
        assertNotEquals("different steel weight", aci.steelWeight, sbc.steelWeight, 1e-9)
    }

    @Test
    fun `deflection limits are code specific - ECP span25 vs ACI-SBC span20`() {
        assertEquals("ECP L/25", 160.0, twoWay(DesignCode.ECP).minThickness, 1e-9)
        assertEquals("ACI L/20", 200.0, twoWay(DesignCode.ACI).minThickness, 1e-9)
        assertEquals("SBC L/20", 200.0, twoWay(DesignCode.SBC).minThickness, 1e-9)
    }

    @Test
    fun `too-thin slab is never reported safe for any code`() {
        // 4 m simply supported span: ECP needs ≥160 mm, ACI/SBC ≥200 mm
        for (code in allCodes) {
            val r = twoWay(code, ts = 150.0)
            assertFalse("$code: 150 mm over a 4 m span is below the code minimum", r.isSafe)
            val thickness = r.safetyChecks.first { it.name.contains("Thickness", ignoreCase = true) }
            assertFalse("$code min-thickness row must be flagged", thickness.isSafe)
            assertEquals("$code reported thickness", 150.0, thickness.value, 1e-9)
            assertTrue("$code required thickness", thickness.limit >= 160.0)
        }
    }

    @Test
    fun `adequately thick two-way slab is safe for every code`() {
        for (code in allCodes) {
            val r = twoWay(code, ts = 250.0)
            val thickness = r.safetyChecks.first { it.name.contains("Thickness", ignoreCase = true) }
            assertTrue("$code: 250 mm ≥ L/20 = 200 mm", thickness.isSafe)
            assertTrue("$code 250 mm panel must be safe overall", r.isSafe)
        }
    }

    @Test
    fun `one-way strip uses L over 8 and code specific temperature steel`() {
        val ecp = oneWay(DesignCode.ECP)
        val aci = oneWay(DesignCode.ACI)
        assertEquals("one-way strip has no long-span moment", 0.0, ecp.momentY, 0.0)
        assertEquals("ECP Mu = wu·L²/8", (1.4 * 6.0 + 1.6 * 3.0) * 9.0 / 8.0, ecp.momentX, 1e-9)
        assertEquals("ACI Mu = wu·L²/8", (1.2 * 6.0 + 1.6 * 3.0) * 9.0 / 8.0, aci.momentX, 1e-9)
        // transverse (temperature/shrinkage) steel comes from the code minimum ratio
        // 0.13 % (ECP) vs 0.18 % (ACI 318 §7.6.1.1) → different spacing
        assertNotEquals("ECP vs ACI temperature steel",
            ecp.reinforcementSecondary.spacing, aci.reinforcementSecondary.spacing, 1e-9)
    }
}
