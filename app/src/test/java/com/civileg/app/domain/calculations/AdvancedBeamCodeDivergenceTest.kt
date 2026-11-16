package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIAdvancedBeam
import com.civileg.app.domain.calculations.ecp.ECPAdvancedBeam
import com.civileg.app.domain.calculations.sbc.SBCAdvancedBeam
import com.civileg.app.domain.entities.BeamSectionType
import com.civileg.app.domain.entities.BeamType
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdvancedBeamCodeDivergenceTest {

    private lateinit var ecpEngine: ECPAdvancedBeam
    private lateinit var aciEngine: ACIAdvancedBeam
    private lateinit var sbcEngine: SBCAdvancedBeam

    @Before
    fun setUp() {
        ecpEngine = ECPAdvancedBeam()
        aciEngine = ACIAdvancedBeam()
        sbcEngine = SBCAdvancedBeam()
    }

    @Test
    fun testCrossCodeAdvancedBeamDivergence() {
        val beamType = BeamType.SimplySupported(6.0)
        val sectionType = BeamSectionType.RECTANGULAR
        val fcu = 30.0
        val fy = 400.0
        val dl = 20.0
        val ll = 15.0
        val width = 250.0
        val depth = 600.0

        val ecpRes = ecpEngine.designBeam(
            beamType, sectionType, fcu, fy, dl, ll, width, depth, null, LoadCombination.DEAD_LIVE
        )
        val aciRes = aciEngine.designBeam(
            beamType, sectionType, fcu, fy, dl, ll, width, depth, null, LoadCombination.DEAD_LIVE
        )
        val sbcRes = sbcEngine.designBeam(
            beamType, sectionType, fcu, fy, dl, ll, width, depth, null, LoadCombination.DEAD_LIVE, false
        )

        assertNotNull(ecpRes)
        assertNotNull(aciRes)
        assertNotNull(sbcRes)

        // All three codes must require positive flexural steel
        assertTrue(ecpRes.flexureResult.astRequired > 0.0)
        assertTrue(aciRes.flexureResult.astRequired > 0.0)
        assertTrue(sbcRes.flexureResult.astRequired > 0.0)

        // Expected code factors: ACI (1.2D+1.6L), ECP (1.4D+1.6L), SBC 304 (1.2D+1.6L)
        // Required steel areas should be within 25% across codes due to safety factors and fc' vs fcu conversions
        val ratioEcpAci = ecpRes.flexureResult.astRequired / aciRes.flexureResult.astRequired
        val ratioSbcAci = sbcRes.flexureResult.astRequired / aciRes.flexureResult.astRequired

        assertTrue("ECP/ACI required steel ratio within expected bounds", ratioEcpAci in 0.75..1.35)
        assertTrue("SBC/ACI required steel ratio within expected bounds", ratioSbcAci in 0.85..1.25)
    }
}
