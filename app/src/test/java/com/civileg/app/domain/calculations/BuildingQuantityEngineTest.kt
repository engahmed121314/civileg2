package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.BuildingModel
import com.civileg.app.domain.entities.BuildingNode
import com.civileg.app.domain.entities.BuildingSlab
import org.junit.Test

class BuildingQuantityEngineTest {

    @Test(expected = IllegalArgumentException::class)
    fun calculateQuantitiesRejectsSlabWithMissingNodes() {
        val model = BuildingModel(
            nodes = listOf(
                BuildingNode("n1", 0.0, 0.0),
                BuildingNode("n2", 4.0, 0.0)
            ),
            slabs = listOf(BuildingSlab("s1", listOf("n1", "n2", "missing")))
        )

        BuildingQuantityEngine.calculateQuantities(model)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateQuantitiesRejectsZeroAreaSlab() {
        val model = BuildingModel(
            nodes = listOf(
                BuildingNode("n1", 0.0, 0.0),
                BuildingNode("n2", 1.0, 0.0),
                BuildingNode("n3", 2.0, 0.0)
            ),
            slabs = listOf(BuildingSlab("s1", listOf("n1", "n2", "n3")))
        )

        BuildingQuantityEngine.calculateQuantities(model)
    }
}
