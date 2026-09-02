package com.civileg.app.engineering

import com.civileg.app.domain.calculations.aci.ACIRetainingWall
import com.civileg.app.domain.calculations.aci.ACITank
import com.civileg.app.domain.calculations.base.RetainingWallInput
import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.calculations.ecp.ECPRetainingWall
import com.civileg.app.domain.calculations.ecp.ECPTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier-0 golden gates (PHASE00_AUDIT Addendum A):
 *
 *  A4 — retaining wall water-table physics: submerged soil was dropped
 *       entirely and the hydrostatic arm double-counted the dry depth.
 *       Correct layered Rankine model (ECP+ACI, identical block):
 *         pDry   = Ka*gamma*zw^2/2          arm H - 2zw/3
 *         pRect  = Ka*gamma*zw*hw           arm hw/2
 *         pSubTr = Ka*(gamma-9.81)*hw^2/2   arm hw/3
 *         pWater = 9.81*hw^2/2              arm hw/3
 *  A5 — underground-tank buoyancy now driven by external groundwater via
 *       groundWaterDepth (legacy default keeps full-head envelope).
 */
class Tier0RetainingUpliftGoldenTest {

    // Geometry shared by both engines; every number below is hand-derived.
    // H=6, tBase=0.5, tTop=0.3, B=4, tF=0.6, toe=1.0, heel=2.5
    // W  = stem 60 + base 60 + heel soil 243            = 363.0 kN
    // MR = 60x2.375 + 60x2.0 + 243x2.5                  = 870.0 kN.m
    // Ka = tan(30deg)^2 = 1/3 ; Kp = 3 ; mu = 0.5
    private fun input(zwt: Double) = RetainingWallInput(
        wallHeight = 6.0, stemBaseThickness = 0.5, stemTopThickness = 0.3,
        baseWidth = 4.0, baseThickness = 0.6,
        toeLength = 1.0, heelLength = 2.5,
        soilDensity = 18.0, frictionAngle = 30.0, surchargeLoad = 0.0,
        waterTableDepth = zwt, fcu = 25.0, fy = 400.0,
        baseFrictionCoeff = 0.5, soilBearingCapacity = 500.0
    )

    @Test
    fun `A4 ECP wet case matches hand-derived stability numbers`() {
        val r = ECPRetainingWall().designRetainingWall(input(2.0))
        // OTM = 12x4.6667 + 48x2 + 21.84x1.3333 + 78.48x1.3333 = 285.76 kN.m
        assertEquals(3.0442, r.overturningFS, 0.02)      // 870 / 285.76
        // totalPa = 12 + 48 + 21.84 + 78.48 = 160.32 kN
        assertEquals(1.1624, r.slidingFS, 0.01)          // (181.5 + 4.86) / 160.32
    }

    @Test
    fun `A4 ACI wet case matches hand-derived stability numbers`() {
        val r = ACIRetainingWall().designRetainingWall(input(2.0))
        assertEquals(3.0442, r.overturningFS, 0.02)
        assertEquals(1.1624, r.slidingFS, 0.01)
    }

    @Test
    fun `A4 dry case collapses to classic triangle`() {
        // zw=6, hw=0: OTM = 108 x 2 = 216 -> FS_ot = 4.0278 ; FS_sl = 186.36/108 = 1.7256
        for (engine in listOf(ECPRetainingWall(), ACIRetainingWall())) {
            val r = engine.designRetainingWall(input(50.0))
            assertEquals(4.0278, r.overturningFS, 0.02)
            assertEquals(1.7256, r.slidingFS, 0.01)
        }
    }

    @Test
    fun `A4 high water table raises sliding demand decisively`() {
        // Submerged components add 69.84+78.48 kN of lateral demand that the old
        // model ignored entirely -> wet/dry sliding-FS ratio must be well below 1.
        for (engine in listOf(ECPRetainingWall(), ACIRetainingWall())) {
            val dry = engine.designRetainingWall(input(50.0))
            val wet = engine.designRetainingWall(input(2.0))
            assertEquals(0.6737, wet.slidingFS / dry.slidingFS, 0.01)
            assertTrue("wet must not be more stable than dry", wet.overturningFS < dry.overturningFS)
        }
    }

    // ------------------------------------------------------------------
    // A5 — tank uplift vs external groundwater (ECP shown; same block all codes)
    // ------------------------------------------------------------------

    private fun undergroundTank() = ECPTank().calculateTank(
        length = 10000.0, width = 10000.0, height = 4000.0,
        waterDepth = 0.0, fcu = 25.0, fy = 400.0,
        type = TankType.CIRCULAR_UNDERGROUND
    )

    @Test
    fun `A5 legacy envelope keeps worst-case head`() {
        // t_wall=350, t_base=1000 -> V = 2pi*5*4*0.35 + 10*10*1 = 143.98 m3
        // W = 3599.6 kN ; U(full 4m) = 3924 kN -> FS = 0.917 FAIL (empty tank)
        val res = undergroundTank()
        assertEquals(0.9173, res.factorOfSafetyUplift, 0.005)
        assertFalse(res.safetyChecks.first { it.name == "Uplift Safety Factor" }.isSafe)
    }

    @Test
    fun `A5 explicit groundwater governs submergence depth`() {
        // GW at 2 m below top: U = 10x10x2x9.81 = 1962 -> FS = 3599.6/1962 = 1.8346 PASS
        val res = ECPTank().calculateTank(
            length = 10000.0, width = 10000.0, height = 4000.0,
            waterDepth = 0.0, fcu = 25.0, fy = 400.0,
            type = TankType.CIRCULAR_UNDERGROUND, groundWaterDepth = 2000.0
        )
        assertEquals(1.8346, res.factorOfSafetyUplift, 0.005)
        assertTrue(res.safetyChecks.first { it.name == "Uplift Safety Factor" }.isSafe)
    }

    @Test
    fun `A5 dry formation skips uplift check`() {
        val res = ECPTank().calculateTank(
            length = 10000.0, width = 10000.0, height = 4000.0,
            waterDepth = 0.0, fcu = 25.0, fy = 400.0,
            type = TankType.CIRCULAR_UNDERGROUND, groundWaterDepth = 60000.0
        )
        assertEquals(99.0, res.factorOfSafetyUplift, 0.0)
        assertFalse(res.safetyChecks.any { it.name == "Uplift Safety Factor" })
    }
}
