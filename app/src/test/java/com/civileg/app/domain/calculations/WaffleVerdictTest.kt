package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIWaffleSlabDesign
import com.civileg.app.domain.calculations.base.CheckStatus
import com.civileg.app.domain.calculations.base.WaffleSlabDesign.WaffleSlabInput
import com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign
import com.civileg.app.domain.calculations.sbc.SBCWaffleSlabDesign
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the waffle-slab verdict logic. Expected numbers are hand-computed independently
 * (fc = 0.8 x 30 = 24 MPa, fy = 400, rib 150 x 300, spacing 600, cover 25 => d = 265 mm).
 */
class WaffleVerdictTest {
    private val aci = ACIWaffleSlabDesign()
    private val ecp = ECPWaffleSlabDesign()
    private val sbc = SBCWaffleSlabDesign()
    private val lc = LoadCombination.DEAD_LIVE

    @Test
    fun `ACI rib demand uses rib line load and is not factored twice`() {
        // Wu = 8.4 kN/m2 -> w = 8.4 x 0.6 = 5.04 kN/m, L = 6 m: Mu = 22.68 kN.m, Vu = 15.12 kN
        val rib = aci.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 6000.0, 8.4, lc)
        // a = 33.15 mm, As,req = 253.6 mm2, As,min = 139.1 mm2 -> 2 phi16 = 402.1 mm2, phiMn = 34.56 kN.m
        assertEquals(253.6, rib.flexureReinforcement.requiredArea, 0.5)
        assertEquals(2, rib.flexureReinforcement.bars)
        assertEquals(402.12, rib.flexureReinforcement.providedArea, 0.05)
        // util = max(Mu/phiMn, Vu/phiVc) = max(0.6563, 0.6090)
        assertEquals(0.6563, rib.utilizationRatio, 0.002)
        // phiVc = 24.83 kN, Vu = 15.12 kN -> util 0.609, no stirrups required
        assertEquals(0.0, rib.shearReinforcement.requiredShearReinforcement, 1e-9)
        assertEquals(33.10, rib.shearReinforcement.concreteShearCapacity, 0.05)
        // Passing ACI values still read PRELIMINARY: coefficient tables are not in the supplied text.
        assertEquals(CheckStatus.PRELIMINARY, rib.status)
    }

    @Test
    fun `ACI rib that cannot resist the moment fails and is not hidden by a bar clamp`() {
        // Wu = 40 -> w = 24 kN/m, Mu = 108 kN.m: 2Mu/(0.85 fc phi b) = 78431 > d^2 = 70225 mm2
        val rib = aci.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 6000.0, 40.0, lc)
        assertEquals(CheckStatus.FAIL, rib.status)
        assertFalse(rib.isSafe)
    }

    @Test
    fun `ACI rib shear demand above phiVc requires stirrups`() {
        // Wu = 40, L = 3 m: Vu = 36 kN > phiVc = 24.83 kN. Vs = 36/0.75 - 33.10 = 14.90 kN, Av/s = 0.1405 mm2/mm
        val rib = aci.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 3000.0, 40.0, lc)
        assertEquals(0.1405, rib.shearReinforcement.requiredShearReinforcement, 0.001)
        assertEquals(CheckStatus.PRELIMINARY, rib.status)
        // Stirrups are required but not detailed, so no stirrup diameter or spacing is claimed.
        assertEquals(0.0, rib.shearReinforcement.stirrupSpacing, 0.0)
    }

    @Test
    fun `ACI concentric punching uses real demand and fails when demand exceeds capacity`() {
        // d = 350 - 25 - 10 = 315, bo = 4 (400 + 315) = 2860, phi vc = 0.75 x 1456.45 = 1092.34 kN
        val ok = aci.checkPunchingShear(30.0, 400.0, 1000.0, 400.0, 373.71, 373.71, 350.0)
        assertEquals(1092.34, ok.vc, 0.1)
        assertEquals(373.71, ok.vu, 1e-6)
        assertEquals(CheckStatus.PRELIMINARY, ok.status)
        val bad = aci.checkPunchingShear(30.0, 400.0, 1000.0, 400.0, 2669.33, 2669.33, 350.0)
        assertEquals(CheckStatus.FAIL, bad.status)
        assertFalse(bad.isSafe)
    }

    @Test
    fun `punching without a supplied demand or depth is NOT ASSESSED not a pass`() {
        val noDemand = aci.checkPunchingShear(30.0, 400.0, 1000.0, 400.0, 1000.0, 0.0, 350.0)
        assertEquals(CheckStatus.NOT_ASSESSED, noDemand.status)
        assertFalse(noDemand.isSafe)
        val noDepth = ecp.checkPunchingShear(30.0, 400.0, 1000.0, 400.0, 1000.0, 200.0)
        assertEquals(CheckStatus.NOT_ASSESSED, noDepth.status)
    }

    @Test
    fun `ECP and SBC passing values are NOT ASSESSED while a computed failure still fails`() {
        for (d in listOf(ecp, sbc)) {
            val ok = d.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 6000.0, 8.4, lc)
            assertEquals(CheckStatus.NOT_ASSESSED, ok.status)
            assertFalse(ok.isSafe)
            val bad = d.designRib(30.0, 400.0, 150.0, 300.0, 600.0, 6000.0, 40.0, lc)
            assertEquals(CheckStatus.FAIL, bad.status)
        }
    }

    @Test
    fun `waffle design never reports SAFE and lists what was not assessed`() {
        val input = WaffleSlabInput(
            lx = 6000.0, ly = 7500.0, ribSpacing = 600.0, ribWidth = 150.0, ribHeight = 300.0,
            toppingThickness = 50.0, solidHeadSize = 1000.0, columnWidth = 400.0, columnDepth = 400.0,
            fcu = 30.0, fy = 400.0, liveLoad = 3.0, deadLoad = 3.0, clearCover = 25.0
        )
        for (d in listOf(aci, ecp, sbc)) {
            val r = d.design(input)
            assertFalse(r.isSafe)
            assertFalse(r.deflectionAssessed)
            assertTrue(r.notAssessed.any { it.startsWith("Deflection") })
            assertTrue(r.notAssessed.any { it.startsWith("Solid head") })
            assertNotEquals(CheckStatus.PASS, r.status)
        }
        // ACI at Wu = 1.2 x 3 + 1.6 x 3 = 8.4 kN/m2: rib and punching computed and not failed
        assertEquals(CheckStatus.PRELIMINARY, aci.design(input).status)
        // A very heavy load must fail visibly
        assertEquals(CheckStatus.FAIL, aci.design(input.copy(deadLoad = 30.0, liveLoad = 20.0)).status)
    }
}
