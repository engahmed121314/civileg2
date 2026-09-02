package com.civileg.app.engineering

import com.civileg.app.domain.calculations.BeamDesignEngine
import com.civileg.core.calculations.entities.DesignCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 00 STEP 3 — Integration proof that [BeamDesignEngine.designBeam] actually
 * routes beam torsion through the unified engine for ECP/ACI (not the legacy routine).
 *
 * Discriminator: for ECP b=300,h=600,fcu=30,cover=40 the unified threshold is
 * ~0.555 kN·m while the legacy b²·d form gives ~18.04 kN·m. With Tu=2.0 the new
 * route flags needsTorsionDesign=true; the legacy route would flag it false.
 */
class TorsionRouteIntegrationTest {

    @Test
    fun ecpBeamDesign_routesTorsionThroughUnifiedEngine() {
        val r = BeamDesignEngine.designBeam(
            b = 300.0, h = 600.0, span = 6.0,
            deadLoad = 10.0, liveLoad = 10.0,
            fcu = 30.0, fy = 360.0, preferredDia = 20,
            code = DesignCode.ECP, supportType = "SS",
            cover = 40.0, torsionalMoment = 2.0
        )
        assertTrue(r.needsTorsionDesign)
        assertEquals(0.555, r.torsionalThreshold, 5e-3)
        assertEquals("11Ø25", r.torsionalLongitudinalBars)
        assertTrue(r.torsionalReinforcement.contains("Ø10"))
        assertTrue(r.torsionalReinforcement.contains("185"))
        assertEquals(185.0, r.torsionalStirrupSpacing, 1e-9)
        assertTrue(r.torsionIsSafe)
        assertFalse(r.torsionalReinforcement.contains("No torsion reinforcement required"))
    }

    @Test
    fun aciBeamDesign_routesTorsionThroughUnifiedEngine() {
        val r = BeamDesignEngine.designBeam(
            b = 300.0, h = 600.0, span = 6.0,
            deadLoad = 10.0, liveLoad = 10.0,
            fcu = 30.0, fy = 420.0, preferredDia = 20,
            code = DesignCode.ACI, supportType = "SS",
            cover = 40.0, torsionalMoment = 5.0
        )
        assertTrue(r.needsTorsionDesign)
        // designBeam passes raw fcu=30 -> ACI cylinder f'c=0.8·30=24 -> Tth≈0.608 kN·m
        // (the direct cross-gate used fcu=37.5 to simulate f'c=30 -> 0.680; ∝√f'c)
        assertEquals(0.608, r.torsionalThreshold, 5e-3)
        assertEquals("12Ø19", r.torsionalLongitudinalBars)
        assertTrue(r.torsionalReinforcement.contains("Ø8"))
        assertEquals(185.0, r.torsionalStirrupSpacing, 1e-9)
        assertTrue(r.torsionIsSafe)
    }
}
