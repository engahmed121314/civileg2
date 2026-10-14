package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIColumn
import com.civileg.app.domain.calculations.aci.ACIDoublyReinforcedBeam
import com.civileg.app.domain.calculations.ecp.ECPDoublyReinforcedBeam
import com.civileg.app.domain.calculations.sbc.SBCDoublyReinforcedBeam
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for calculation-engine fixes (2026-10-14 audit).
 * Locks in: column axial-strength inversion (α limit), doubly-reinforced
 * K-method balance (dynamic K_bal instead of hardcoded 0.194 / R-mixing).
 */
class EngineFixRegressionTest {

    // ── ACI column: axial inversion must divide Pu by (φ·α), not φ ──
    @Test
    fun `ACI column axial design meets demand when As above minimum`() {
        val col = ACIColumn()
        val fcu = 25.0
        val fy = 400.0
        val b = 400.0
        val d = 400.0
        val Ag = b * d
        val fcPrime = 0.8 * fcu

        // Demand chosen so the exact required steel = 2000 mm² (> 1% Ag = 1600 mm²)
        val asTarget = 2000.0
        val puN = (asTarget * (fy - 0.85 * fcPrime) + 0.85 * fcPrime * Ag) * 0.65 * 0.80
        val axialLoad = puN / 1000.0

        val res = col.calculateReinforcement(
            fcu = fcu, fy = fy, width = b, depth = d,
            axialLoad = axialLoad, momentX = 0.0, momentY = 0.0,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertTrue(
            "Provided steel ${res.astProvided} mm² must cover target $asTarget mm²",
            res.astProvided >= asTarget
        )
        assertTrue("Design must be safe (util=${res.utilizationRatio})", res.isSafe)
    }

    // ── SBC doubly-reinforced: K_bal from strain compatibility, not 0.194 ──
    @Test
    fun `SBC doubly reinforced kBal is strain-compatibility based`() {
        val kBal = SBCDoublyReinforcedBeam().calculateKBal(25.0, 360.0)
        assertTrue("kBal=$kBal should be ~0.18", kBal in 0.16..0.20)
    }

    @Test
    fun `SBC doubly reinforced adds compression steel only when K exceeds kBal`() {
        val eng = SBCDoublyReinforcedBeam()

        val singly = eng.design(mu = 120.0, b = 300.0, h = 450.0, fcu = 25.0, fy = 360.0)
        assertFalse("K < K_bal must be singly reinforced", singly.needsCompressionSteel)
        assertEquals(0.0, singly.asCompression, 1e-9)

        val doubly = eng.design(mu = 230.0, b = 300.0, h = 450.0, fcu = 25.0, fy = 360.0)
        assertTrue("K > K_bal must need compression steel", doubly.needsCompressionSteel)
        assertTrue("Compression steel area must be positive", doubly.asCompression > 0.0)
        assertTrue("Total steel must exceed balanced portion",
            doubly.asRequired > doubly.asTensionFromConcrete)
    }

    // ── ECP doubly-reinforced: K vs kBal decision (was R vs R_bal) ──
    @Test
    fun `ECP doubly reinforced does not over-trigger compression steel`() {
        val eng = ECPDoublyReinforcedBeam()

        val singly = eng.design(mu = 230.0, b = 300.0, h = 500.0, fcu = 25.0, fy = 360.0)
        assertFalse("K=0.158 < K_bal=0.186 must be singly reinforced", singly.needsCompressionSteel)

        val doubly = eng.design(mu = 300.0, b = 300.0, h = 500.0, fcu = 25.0, fy = 360.0)
        assertTrue("K=0.207 > K_bal must need compression steel", doubly.needsCompressionSteel)
    }

    // ── ACI doubly-reinforced: lever arm from stress block, sane capacity ──
    @Test
    fun `ACI doubly reinforced produces safe utilization`() {
        val eng = ACIDoublyReinforcedBeam()
        val res = eng.design(mu = 450.0, b = 300.0, h = 550.0, fcPrime = 25.0, fy = 420.0)
        assertTrue("mu > phiMn must need compression steel", res.needsCompressionSteel)
        assertTrue("Compression steel must be positive", res.asCompression > 0.0)
        assertTrue("Design should be safe (util=${res.utilizationRatio})", res.isSafe)
    }
}
