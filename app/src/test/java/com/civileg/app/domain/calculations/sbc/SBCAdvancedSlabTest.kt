package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SBCAdvancedSlabTest {

    private lateinit var engine: SBCAdvancedSlab
    private val supportConditions = SlabSupportConditions(
        edgeA = EdgeCondition.SIMPLY_SUPPORTED,
        edgeB = EdgeCondition.SIMPLY_SUPPORTED,
        edgeC = EdgeCondition.SIMPLY_SUPPORTED,
        edgeD = EdgeCondition.SIMPLY_SUPPORTED
    )

    @Before
    fun setUp() {
        engine = SBCAdvancedSlab()
    }

    @Test
    fun testSolidSlab_goldenVector() {
        val slabType = SlabType.Solid(
            thickness = 150.0,
            shortSpan = 4.0,
            longSpan = 5.0,
            supportConditions = supportConditions
        )

        val result = engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = 4.0,
            liveLoad = 3.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isCorrosiveEnvironment = false
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.requiredReinforcement > 0.0)
        assertTrue(result.flexureResult.providedReinforcement >= result.flexureResult.requiredReinforcement)
    }

    @Test
    fun testHordiSlab_goldenVector() {
        val slabType = SlabType.Hordi(
            totalThickness = 270.0,
            ribWidth = 150.0,
            ribSpacing = 550.0,
            blockWidth = 400.0,
            blockHeight = 200.0,
            span = 5.0,
            supportConditions = supportConditions
        )

        val result = engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = 5.0,
            liveLoad = 2.5,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE,
            isCorrosiveEnvironment = false
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.requiredReinforcement > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroFy_rejectedByInputGuard() {
        val slabType = SlabType.Solid(
            thickness = 150.0,
            shortSpan = 4.0,
            longSpan = 5.0,
            supportConditions = supportConditions
        )

        engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 0.0,
            deadLoad = 4.0,
            liveLoad = 3.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }
}
