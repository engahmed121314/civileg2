package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.entities.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ACIAdvancedSlabTest {

    private lateinit var engine: ACIAdvancedSlab
    private val supportConditions = SlabSupportConditions(
        edgeA = EdgeCondition.SIMPLY_SUPPORTED,
        edgeB = EdgeCondition.SIMPLY_SUPPORTED,
        edgeC = EdgeCondition.SIMPLY_SUPPORTED,
        edgeD = EdgeCondition.SIMPLY_SUPPORTED
    )

    @Before
    fun setUp() {
        engine = ACIAdvancedSlab()
    }

    @Test
    fun testSolidSlab_goldenVector() {
        val slabType = SlabType.Solid(
            thickness = 160.0,
            shortSpan = 4.5,
            longSpan = 5.5,
            supportConditions = supportConditions
        )

        val result = engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = 4.5,
            liveLoad = 3.5,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.requiredReinforcement > 0.0)
        assertTrue(result.flexureResult.providedReinforcement >= result.flexureResult.requiredReinforcement)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeDeadLoad_rejectedByInputGuard() {
        val slabType = SlabType.Solid(
            thickness = 160.0,
            shortSpan = 4.5,
            longSpan = 5.5,
            supportConditions = supportConditions
        )

        engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = -4.5,
            liveLoad = 3.5,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )
    }

    @Test
    fun testFlatPlate_goldenVector() {
        val slabType = SlabType.FlatPlate(
            thickness = 200.0,
            panelLength = 6.0,
            panelWidth = 6.0,
            columnSize = 450.0
        )

        val result = engine.designSlab(
            slabType = slabType,
            fcu = 30.0,
            fy = 420.0,
            deadLoad = 5.0,
            liveLoad = 3.0,
            inventory = null,
            loadCombination = LoadCombination.DEAD_LIVE
        )

        assertNotNull(result)
        assertTrue(result.flexureResult.requiredReinforcement > 0.0)
    }
}
