package com.civileg.app.utils

import com.civileg.app.domain.entities.DesignCode
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * P0-2 parity guard for CalculatorEngine.designRetainingWall.
 *
 * The 2026-10 parity probe found the retaining wall to be one of only two elements whose
 * result was identical for all three codes. The engines themselves do differ (ECP: γs=1.15
 * K-method, cover 50, 0.26√fcu/fy minimum steel, OT FS ≥ 1.5; ACI: φ=0.9 Rn-ρ with f'c
 * = 0.8fcu, cover 75, ρmin 0.0018, OT FS ≥ 2.0; SBC: same Rn-ρ but ρmin 0.002 and OT FS
 * ≥ 1.5) - the facade's parseBarSpec collapsed every "nΦd" engine spec to Φ6@50, wiping
 * the code difference. parseBarSpec now understands both engine spec shapes, and the
 * user's preferred bar diameter is honored against the engine's required area.
 */
class RetainingWallCodeDivergenceTest {

    private val engine = CalculatorEngine(mockk(relaxed = true))

    /** 3.0 m wall, no surcharge: minimum-steel governs everywhere → 3-way bar divergence. */
    private fun shortWall(code: DesignCode, diameter: Int = 16) = engine.designRetainingWall(
        height = 3.0, soilDensity = 18.0, frictionAngle = 30.0, surcharge = 0.0,
        fcu = 30.0, fy = 420.0, preferredDiameter = diameter, code = code
    )

    /** 5.0 m wall with 10 kN/m² surcharge: flexure governs; limits still code-specific. */
    private fun tallWall(code: DesignCode, diameter: Int = 16) = engine.designRetainingWall(
        height = 5.0, soilDensity = 18.0, frictionAngle = 30.0, surcharge = 10.0,
        fcu = 30.0, fy = 420.0, preferredDiameter = diameter, code = code
    )

    private fun requiredStem(r: CalculatorEngine.RetainingWallResult): Double =
        r.safetyChecks.first { it.name == "Stem Flexure" }.limit

    private fun otFsLimit(r: CalculatorEngine.RetainingWallResult): Double =
        r.safetyChecks.first { it.name == "OT FS" }.limit

    private fun shearLimit(r: CalculatorEngine.RetainingWallResult): Double =
        r.safetyChecks.first { it.name == "Shear" }.limit

    @Test
    fun `all three codes return a complete result with parsed bars`() {
        for (code in listOf(DesignCode.ECP, DesignCode.ACI, DesignCode.SBC)) {
            val r = shortWall(code)
            assertEquals("engine code", code, r.code)
            assertTrue("volume", r.concreteVolume > 0.0)
            assertTrue("steel weight", r.steelWeight > 0.0)
            assertTrue("stem bars parsed", r.stemReinforcement.numBars >= 1)
            assertTrue("stem diameter parsed", r.stemReinforcement.diameter >= 10)
            assertTrue("stem spacing derived", r.stemReinforcement.spacing > 0.0)
            assertEquals("spacing = 1000/n", 1000.0 / r.stemReinforcement.numBars,
                r.stemReinforcement.spacing, 1e-9)
            assertTrue("base bar diameter parsed", r.baseReinforcement.diameter >= 6)
            assertTrue("base bar weight derived from bars", r.baseReinforcement.weightKg > 0.0)
            assertTrue("checks present", r.safetyChecks.size >= 5)
        }
    }

    @Test
    fun `preferred diameter is honored with code-specific bar counts`() {
        // ρmin-governed: ECP 0.26√fcu/fy·b·d (cover 50) > SBC 0.002·b·d > ACI 0.0018·b·d
        val ecp = shortWall(DesignCode.ECP)
        val aci = shortWall(DesignCode.ACI)
        val sbc = shortWall(DesignCode.SBC)

        assertEquals("ECP uses preferred Φ16", 16, ecp.stemReinforcement.diameter)
        assertEquals("ACI uses preferred Φ16", 16, aci.stemReinforcement.diameter)
        assertEquals("SBC uses preferred Φ16", 16, sbc.stemReinforcement.diameter)

        // Counts must differ: ECP needs ~2x the ACI minimum steel on this thin stem.
        assertNotEquals("ECP vs ACI bar count", ecp.stemReinforcement.numBars, aci.stemReinforcement.numBars)
        assertNotEquals("SBC vs ACI bar count", sbc.stemReinforcement.numBars, aci.stemReinforcement.numBars)
        assertNotEquals("ECP vs SBC required steel", requiredStem(ecp), requiredStem(sbc), 1e-9)

        // Provided bars must cover the code-required area in every case.
        for (r in listOf(ecp, aci, sbc)) {
            assertTrue("provided >= required (${r.code})",
                r.stemReinforcement.area >= requiredStem(r) - 1e-6)
        }
    }

    @Test
    fun `stability limits are code specific`() {
        val ecp = tallWall(DesignCode.ECP)
        val aci = tallWall(DesignCode.ACI)
        val sbc = tallWall(DesignCode.SBC)

        // OT FS limit: ECP 1.5, ACI 2.0, SBC 1.5 (unfactored FS value itself is shared).
        assertEquals("ECP OT limit", 1.5, otFsLimit(ecp), 1e-9)
        assertEquals("ACI OT limit", 2.0, otFsLimit(aci), 1e-9)
        assertEquals("SBC OT limit", 1.5, otFsLimit(sbc), 1e-9)
        assertNotEquals("ECP vs ACI OT limit", otFsLimit(ecp), otFsLimit(aci), 1e-9)

        // Shear resistance: ECP qcu = 0.24√(fcu/γc) vs ACI/SBC φVc = 0.75·0.17√(0.8fcu).
        assertNotEquals("ECP vs ACI shear limit", shearLimit(ecp), shearLimit(aci), 1e-9)
    }

    @Test
    fun `flexure-governed wall shows different required steel ECP vs ACI-SBC`() {
        val ecp = tallWall(DesignCode.ECP)
        val aci = tallWall(DesignCode.ACI)
        // ECP designs with γs=1.15 lever arm off fcu and cover 50; ACI with φ=0.9 off
        // f'c=0.8fcu and cover 75 - the required areas differ even though both are
        // flexure-governed.
        assertNotEquals("ECP vs ACI required steel", requiredStem(ecp), requiredStem(aci), 1e-9)
        for (r in listOf(ecp, aci)) {
            assertTrue("provided >= required (${r.code})",
                r.stemReinforcement.area >= requiredStem(r) - 1e-6)
        }
    }

    @Test
    fun `bar count scales with the preferred diameter`() {
        val d12 = shortWall(DesignCode.ECP, diameter = 12)
        val d20 = shortWall(DesignCode.ECP, diameter = 20)
        assertEquals("Φ12 count", 12, d12.stemReinforcement.diameter)
        assertEquals("Φ20 count", 20, d20.stemReinforcement.diameter)
        assertTrue("Φ12 needs more bars than Φ20", d12.stemReinforcement.numBars > d20.stemReinforcement.numBars)
        assertTrue("both cover the same requirement",
            d12.stemReinforcement.area >= requiredStem(d12) - 1e-6 &&
                d20.stemReinforcement.area >= requiredStem(d20) - 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height is rejected loudly`() {
        engine.designRetainingWall(
            height = 0.0, soilDensity = 18.0, frictionAngle = 30.0, surcharge = 0.0,
            fcu = 30.0, fy = 420.0, preferredDiameter = 16, code = DesignCode.ECP
        )
    }
}
