package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIAdvancedSlab
import com.civileg.app.domain.calculations.ecp.ECPAdvancedSlab
import com.civileg.app.domain.calculations.sbc.SBCAdvancedSlab
import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdvancedSlabCodeDivergenceTest {

    private lateinit var ecpEngine: ECPAdvancedSlab
    private lateinit var aciEngine: ACIAdvancedSlab
    private lateinit var sbcEngine: SBCAdvancedSlab

    private val supportConditions = SlabSupportConditions(
        edgeA = EdgeCondition.SIMPLY_SUPPORTED,
        edgeB = EdgeCondition.SIMPLY_SUPPORTED,
        edgeC = EdgeCondition.SIMPLY_SUPPORTED,
        edgeD = EdgeCondition.SIMPLY_SUPPORTED
    )

    @Before
    fun setUp() {
        ecpEngine = ECPAdvancedSlab()
        aciEngine = ACIAdvancedSlab()
        sbcEngine = SBCAdvancedSlab()
    }

    @Test
    fun testCrossCodeAdvancedSlabDivergence() {
        val slabType = SlabType.Solid(
            thickness = 150.0,
            shortSpan = 4.0,
            longSpan = 5.0,
            supportConditions = supportConditions
        )
        val fcu = 30.0
        val fy = 400.0
        val dl = 4.0
        val ll = 3.0

        val ecpRes = ecpEngine.designSlab(slabType, fcu, fy, dl, ll, null, LoadCombination.DEAD_LIVE)
        val aciRes = aciEngine.designSlab(slabType, fcu, fy, dl, ll, null, LoadCombination.DEAD_LIVE)
        val sbcRes = sbcEngine.designSlab(slabType, fcu, fy, dl, ll, null, LoadCombination.DEAD_LIVE, false)

        assertNotNull(ecpRes)
        assertNotNull(aciRes)
        assertNotNull(sbcRes)

        assertTrue(ecpRes.flexureResult.requiredReinforcement > 0.0)
        assertTrue(aciRes.flexureResult.requiredReinforcement > 0.0)
        assertTrue(sbcRes.flexureResult.requiredReinforcement > 0.0)

        // Compare required reinforcement across codes
        val ratioEcpAci = ecpRes.flexureResult.requiredReinforcement / aciRes.flexureResult.requiredReinforcement
        val ratioSbcAci = sbcRes.flexureResult.requiredReinforcement / aciRes.flexureResult.requiredReinforcement

        assertTrue("ECP/ACI required reinforcement ratio within analytical bounds", ratioEcpAci in 0.5..2.0)
        assertTrue("SBC/ACI required reinforcement ratio within analytical bounds", ratioSbcAci in 0.5..2.0)
    }
}
