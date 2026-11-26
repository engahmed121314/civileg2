package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand values, ECP 203-2025 Eq 6-65 (PDF p.285-286, printed 6-92/6-93) and Eq 4-30/4-31 (PDF p.101).
 * fcu=30, gc=1.5: sqrt(fcu/gc)=4.472136. Axial-threshold and ordinary-wall mapping are UNVERIFIED rules,
 * tested only as the implemented behaviour.
 */
class ECP2025WallShearTest {
    private val root = 4.472136

    @Test fun alphaCInterpolates() {
        assertEquals(0.18, ECP2025WallShear.alphaC(1500.0, 1000.0), 1e-12)
        assertEquals(0.133, ECP2025WallShear.alphaC(2000.0, 1000.0), 1e-12)
        assertEquals(0.1565, ECP2025WallShear.alphaC(1750.0, 1000.0), 1e-12)
        assertEquals(0.18, ECP2025WallShear.alphaC(500.0, 1000.0), 1e-12)
    }

    @Test fun ductileConcreteTermAndCap() {
        // 0.9*0.18*4.472136 = 0.724486
        assertEquals(0.724486, ECP2025WallShear.ductileConcreteStress(30.0, 1000.0, 2000.0, 2e6, 6e5, 1.5), 1e-5)
        assertEquals(2.459675, ECP2025WallShear.maxShearStress(30.0, 1.5), 1e-5)  // 0.55*root
    }

    @Test fun ductileConcreteIgnoredBelowFivePercent() {
        // 0.05*30*600000 = 900000 N; 800000 N below it
        assertEquals(0.0, ECP2025WallShear.ductileConcreteStress(30.0, 1000.0, 2000.0, 8e5, 6e5, 1.5), 0.0)
    }

    @Test fun ordinaryUsesCrackedWithDeltaC() {
        assertEquals(0.12 * root * 1.14, ECP2025WallShear.ordinaryConcreteStress(30.0, 2.0, 1.5), 1e-6)  // 0.611795
        assertEquals(0.12 * root * 1.5, ECP2025WallShear.ordinaryConcreteStress(30.0, 10.0, 1.5), 1e-6)  // cap 1.5
    }

    private fun input(type: WallType, n: Double, v: Double) = ShearWallInput(
        type, 3000.0, 250.0, 3000.0, 1, n, v, 1000.0, 30.0, 400.0, 400.0, 25.0)

    @Test fun engineSpecialWallVcAndCap() {
        // Lw=3000, bw=250, d=2400, N=4000 kN > 0.05*30*750000=1.125e6 N; hw/Lw=1 -> ac=0.18
        // Vc = 0.724486*250*2400/1000 = 434.69 kN
        val w = ECPShearWall()
        val (vc, _) = w.calculateShearStrength(input(WallType.SPECIAL, 4000.0, 500.0))
        assertEquals(434.69, vc, 0.01)
        // cap 2.459675*250*2400/1000 = 1475.8 kN
        assertEquals(1475.81, w.maxWallShearKn(input(WallType.SPECIAL, 4000.0, 500.0)), 0.01)
    }

    @Test fun engineSpecialWallSmallAxialGivesNoConcrete() {
        val (vc, _) = ECPShearWall().calculateShearStrength(input(WallType.SPECIAL, 500.0, 500.0))
        assertEquals(0.0, vc, 0.0)
    }

    @Test fun engineOrdinaryWallVc() {
        // N=4000 kN, Ac=750000 mm2 -> 5.3333 N/mm2, dc=1.37333; q=0.12*4.472136*1.373333=0.737015; *250*2400/1000=442.21 kN
        val (vc, _) = ECPShearWall().calculateShearStrength(input(WallType.ORDINARY, 4000.0, 500.0))
        assertEquals(442.21, vc, 0.01)
        assertTrue(ECPShearWall().maxWallShearKn(input(WallType.ORDINARY, 4000.0, 500.0)) > vc)
    }
}
