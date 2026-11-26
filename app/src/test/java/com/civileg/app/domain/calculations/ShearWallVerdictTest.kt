package com.civileg.app.domain.calculations

import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.aci.ACIShearWall
import com.civileg.app.domain.calculations.base.DesignCheckStatus
import com.civileg.app.domain.calculations.base.ShearWallChecks
import com.civileg.app.domain.calculations.ecp.ECPShearWall
import com.civileg.app.domain.calculations.sbc.SBCShearWall
import org.junit.Assert.*
import org.junit.Test

/**
 * Verdict containment for the shear wall engines. Expected values are hand computed:
 * with k = 1000 N/mm and d = 1000 mm, a block of a mm needs M' = k*a*(d - a/2) (P = 0):
 * a = 100 -> 1000*100*950 = 9.5e7 N.mm ; a = 400 -> 1000*400*800 = 3.2e8 N.mm.
 * The largest moment a block can carry at P = 0 is k*d^2/2 = 5e8 N.mm.
 */
class ShearWallVerdictTest {

    @Test fun unclampedBlock_matchesHandComputedDepth() {
        assertEquals(100.0, ShearWallChecks.unclampedBlockDepth(1000.0, 0.0, 1000.0, 9.5e7)!!, 1e-6)
        assertEquals(400.0, ShearWallChecks.unclampedBlockDepth(1000.0, 0.0, 1000.0, 3.2e8)!!, 1e-6)
    }

    @Test fun unclampedBlock_noRootAboveSectionMaximum() {
        assertNull(ShearWallChecks.unclampedBlockDepth(1000.0, 0.0, 1000.0, 6.0e8))
    }

    @Test fun limitExceeded_usesUnclampedBlock() {
        // a = 100, beta1 = 0.8 -> c = 125 mm, below 0.3 d = 300
        assertFalse(ShearWallChecks.compressionLimitExceeded(1000.0, 0.0, 1000.0, 9.5e7, 0.8, 0.3))
        // a = 400 -> c = 500 mm, above 300
        assertTrue(ShearWallChecks.compressionLimitExceeded(1000.0, 0.0, 1000.0, 3.2e8, 0.8, 0.3))
        // no equilibrium at all counts as exceeded
        assertTrue(ShearWallChecks.compressionLimitExceeded(1000.0, 0.0, 1000.0, 6.0e8, 0.8, 0.3))
    }

    private val engines: List<Pair<String, com.civileg.app.domain.calculations.base.ShearWallDesign>> =
        listOf("ECP" to ECPShearWall(), "SBC" to SBCShearWall(), "ACI" to ACIShearWall())

    @Test fun overloadedSection_failsAndIsNeverSafe() {
        // 40 000 kN.m exceeds the section maximum in every engine (about 20 580 kN.m ECP/SBC, 28 200 ACI)
        for ((name, e) in engines) {
            val r = e.designWall(ShearWallInput(axialLoad = 0.0, shearForce = 100.0, bendingMoment = 40000.0))
            assertFalse("$name flexure must fail", r.flexuralOk)
            assertEquals("$name status", DesignCheckStatus.FAIL, r.status)
            assertFalse("$name isSafe", r.isSafe)
        }
    }

    @Test fun lightlyLoadedWall_isPreliminaryNeverSafe() {
        for ((name, e) in engines) {
            // one storey: H/t = 3000/300 = 10 <= 25 (the engines use total height = storeys x storey height)
            val r = e.designWall(ShearWallInput(numberOfStories = 1, axialLoad = 500.0, shearForce = 100.0, bendingMoment = 200.0))
            assertTrue("$name flexure computed ok", r.flexuralOk)
            assertEquals("$name H/t", 10.0, r.slendernessRatio, 1e-9)
            assertEquals("$name status", DesignCheckStatus.PRELIMINARY, r.status)
            assertFalse("$name isSafe", r.isSafe)
        }
    }

    @Test fun couplingBeam_isNotAssessed_neverSafeAndNoLiteralDetailing() {
        for ((name, e) in engines) {
            val low = ShearWallInput(
                wallType = WallType.COUPLED, axialLoad = 500.0, shearForce = 5.0, bendingMoment = 10.0,
                couplingBeamLength = 2000.0, couplingBeamHeight = 600.0, couplingBeamClearSpan = 1500.0
            )
            val r = e.designWall(low)
            val c = r.couplingBeamResult!!
            assertEquals("$name coupling status", DesignCheckStatus.NOT_ASSESSED, c.status)
            assertFalse("$name coupling isSafe", c.isSafe)
            if (c.diagonalBars == 0) {
                assertEquals("$name no invented transverse bars", 0, c.transverseBarsDiameter)
                assertEquals("$name no invented spacing", 0, c.transverseBarsSpacing)
                assertEquals("$name no invented ratio", 0.0, c.utilizationRatio, 0.0)
            }
            assertTrue("$name lists coupling as not assessed", r.notAssessed.isNotEmpty())
            assertFalse("$name isSafe", r.isSafe)
            assertTrue("$name coupling check not shown as a pass/fail row",
                r.safetyChecks.none { it.name == "Coupling Beam" })
        }
    }
}
