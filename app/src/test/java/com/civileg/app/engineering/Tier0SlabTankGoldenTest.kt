package com.civileg.app.engineering

import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.calculations.ecp.ECPFlatSlab
import com.civileg.app.domain.calculations.ecp.ECPSlab
import com.civileg.app.domain.calculations.ecp.ECPTank
import com.civileg.app.domain.calculations.aci.ACITank
import com.civileg.app.domain.calculations.sbc.SBCTank
import com.civileg.app.domain.entities.EdgeCondition
import com.civileg.app.domain.entities.SlabSupportConditions
import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier-0 golden gates for slab/tank defects (PHASE00_AUDIT Addendum A):
 *
 *  A11 — two-way slab moments were x1000 (span mm fed into m² divisor).
 *        ECPSlab was corrected by a parallel session; this test LOCKS the fix.
 *  A12 — flat-slab lever-arm divisor was 1.25 while the rest of the ECP
 *        K-method family uses 0.893, under-reinforcing flat slabs ~10%.
 *  A3  — circular tank hoop crack stress computed in kPa but compared to an
 *        MPa limit (~1000x understated -> check inert in all three codes).
 */
class Tier0SlabTankGoldenTest {

    // ------------------------------------------------------------------
    // A11 guard — two-way slab K-method with spans in mm interface
    // ------------------------------------------------------------------
    // NOTE: MomentCoefficients field order is (negativeShort, positiveShort,
    // negativeLong, positiveLong); for aspect 1.5 simply-supported the table
    // row (0.067, 0.050, 0.034, 0.026) therefore yields positiveShort=0.050.

    private fun twoWaySlab(totalLoad: Double) = ECPSlab().designTwoWaySlab(
        fcu = 25.0, fy = 400.0, slabThickness = 180.0,
        shortSpan = 4000.0, longSpan = 6000.0,
        supportConditions = SlabSupportConditions(
            EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED,
            EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED
        ),
        totalLoad = totalLoad, loadCombination = LoadCombination.DEAD_LIVE
    )

    @Test
    fun `A11 coefficient mapping puts 0_050 on positive short direction`() {
        val coeffs = twoWaySlab(75.0).momentCoefficients
        assertEquals(0.050, coeffs.positiveShort, 1e-9)
        assertEquals(0.026, coeffs.positiveLong, 1e-9)
    }

    @Test
    fun `A11 two-way slab short-direction steel matches hand-derived value`() {
        // fcu=25, fy=400, t=180 -> d = 180-20-6 = 154mm; b=1000mm; aspect=1.5
        // MuShort = 0.050 x 75 kN/m2 x (4.0 m)^2 = 60.0 kN.m/m
        // K   = 60e6 / (25 x 1000 x 154^2) = 0.101203
        // z/d = 0.5 + sqrt(0.25 - K/0.893) = 0.869693 -> z = 133.93mm
        // fs  = 400/1.15 = 347.826 MPa
        // As  = 60e6 / (347.826 x 133.93) = 1288.4 mm2/m  (> min 500.5)
        val result = twoWaySlab(75.0)
        assertEquals(1288.4, result.shortDirection.requiredReinforcement, 6.0)
    }

    @Test
    fun `A11 two-way slab long-direction steel matches hand-derived value`() {
        // MuLong = 0.026 x 75 x (6.0)^2 = 70.2 kN.m/m
        // K = 70.2e6 / (25 x 1000 x 154^2) = 0.118406
        // z/d = 0.842651 -> z = 129.77mm ; As = 1555.0 mm2/m
        val result = twoWaySlab(75.0)
        assertEquals(1555.0, result.longDirection.requiredReinforcement, 8.0)
    }

    // ------------------------------------------------------------------
    // A12 — flat-slab lever arm must match the family constant 0.893
    // ------------------------------------------------------------------

    @Test
    fun `A12 flat slab strip reinforcement uses 0_893 lever arm`() {
        // moment = 120 kN.m per 1000mm strip, d=160, fcu=30, fy=400:
        // K = 120e6/(30x1000x160^2) = 0.15625 (< K_bal 0.182)
        // z = 160 x (0.5+sqrt(0.25-0.15625/0.893)) = 160 x 0.773911 = 123.83mm
        // As = 120e6 / (347.826 x 123.83) = 2785.6 mm2
        // (old 1.25 divisor gave z=136.57 -> As=2526.0, ~10% under-designed)
        val flat = ECPFlatSlab()
        val rd = flat.designReinforcement(
            moment = 120.0, fcu = 30.0, fy = 400.0,
            effectiveDepth = 160.0, stripWidth = 1000.0, cover = 25.0
        )
        assertEquals(2785.6, rd.asRequired, 4.0)
        assertFalse(rd.isMinSteel)
    }

    // ------------------------------------------------------------------
    // A3 — circular tank hoop crack stress units (ACI + SBC + ECP)
    // ------------------------------------------------------------------

    private fun hoopCheck(checks: List<com.civileg.app.domain.calculations.base.TankSafetyCheck>) =
        checks.first { it.name == "Hoop Tension Stress" }

    @Test
    fun `A3 ACI circular tank crack check reports MPa and can fail`() {
        // L=B=44m -> R=22m, H=hW=2.5m -> t = ceil25(max(2500/12,200)) = 225mm
        // T = 9.81 x 2.5 x 22 x 1.4 = 754.17 kN/m
        // sigma = 754.17/225 = 3.3519 MPa > fct = 0.62*sqrt(24) = 3.0374 -> FAIL
        // (old code reported 0.00335 vs limit and passed)
        val res = ACITank().calculateTank(
            length = 44000.0, width = 44000.0, height = 2500.0,
            waterDepth = 2500.0, fcu = 30.0, fy = 420.0, type = TankType.CIRCULAR_GROUND
        )
        val check = hoopCheck(res.safetyChecks)
        assertEquals(3.3519, check.value, 0.02)
        assertEquals(3.0374, check.limit, 0.01)
        assertFalse("crack check must fail at sigma>fct", check.isSafe)
    }

    @Test
    fun `A3 SBC circular tank crack check reports MPa and can fail`() {
        val res = SBCTank().calculateTank(
            length = 44000.0, width = 44000.0, height = 2500.0,
            waterDepth = 2500.0, fcu = 30.0, fy = 420.0, type = TankType.CIRCULAR_GROUND
        )
        val check = hoopCheck(res.safetyChecks)
        assertEquals(3.3519, check.value, 0.02)
        assertFalse(check.isSafe)
    }

    @Test
    fun `A3 ECP circular tank crack failure flags wall reinforcement`() {
        // L=B=60m -> R=30m, H=hW=2.5m, fcu=25: t=225mm
        // T = 9.81 x 2.5 x 30 = 735.75 kN/m ; sigma = 735.75/225 = 3.270 > fct = 0.6*5 = 3.0
        // Hoop As = T*1000/fs = 735750/347.826 = 2115.3 mm2/m
        val res = ECPTank().calculateTank(
            length = 60000.0, width = 60000.0, height = 2500.0,
            waterDepth = 2500.0, fcu = 25.0, fy = 400.0, type = TankType.CIRCULAR_GROUND
        )
        assertFalse(res.wallReinforcement.isSafe)
        assertTrue(res.warnings.any { it.contains("شقوق") })
        assertEquals(2115.3, res.wallReinforcement.astRequired, 10.0)
    }

    @Test
    fun `A3 sane tank passes the corrected crack check`() {
        // Small ground tank: R=2m, hW=2m, t=ceil25(max(2000/12,200))=200mm
        // ECP: T = 9.81x2x2 = 39.24 kN/m -> sigma = 0.196 << fct 3.0
        val res = ECPTank().calculateTank(
            length = 4000.0, width = 4000.0, height = 2000.0,
            waterDepth = 2000.0, fcu = 25.0, fy = 400.0, type = TankType.CIRCULAR_GROUND
        )
        assertTrue(res.wallReinforcement.isSafe)
        assertFalse(res.warnings.any { it.contains("شقوق") })
    }
}
