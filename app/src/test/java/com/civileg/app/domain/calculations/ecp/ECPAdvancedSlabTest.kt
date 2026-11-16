package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ECPAdvancedSlabTest {

    private lateinit var engine: ECPAdvancedSlab
    private val supportConditions = SlabSupportConditions(
        edgeA = EdgeCondition.SIMPLY_SUPPORTED,
        edgeB = EdgeCondition.SIMPLY_SUPPORTED,
        edgeC = EdgeCondition.SIMPLY_SUPPORTED,
        edgeD = EdgeCondition.SIMPLY_SUPPORTED
    )

    @Before
    fun setUp() {
        engine = ECPAdvancedSlab()
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
            fcu = 25.0,
            fy = 400.0,
            deadLoad = 4.0,
            liveLoad = 3.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.requiredReinforcement > 0.0)
        assertTrue(result.flexureResult.providedReinforcement >= result.flexureResult.requiredReinforcement)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeFcu_rejectedByInputGuard() {
        val slabType = SlabType.Solid(
            thickness = 150.0,
            shortSpan = 4.0,
            longSpan = 5.0,
            supportConditions = supportConditions
        )

        engine.designSlab(
            slabType = slabType,
            fcu = -25.0,
            fy = 400.0,
            deadLoad = 4.0,
            liveLoad = 3.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test
    fun testFlatSlab_goldenVector() {
        val slabType = SlabType.FlatSlab(
            thickness = 220.0,
            dropPanelThickness = 80.0,
            dropPanelSize = 2000.0,
            panelLength = 6.0,
            panelWidth = 6.0,
            columnSize = 400.0
        )

        val result = engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 400.0,
            deadLoad = 5.0,
            liveLoad = 4.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.requiredReinforcement > 0.0)
    }
}
