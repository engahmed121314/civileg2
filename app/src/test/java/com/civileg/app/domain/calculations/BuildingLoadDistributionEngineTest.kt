package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BuildingLoadDistributionEngine (Tributary load distribution).
 */
class BuildingLoadDistributionEngineTest {

    @Test
    fun distributeLoads_emptyModel_returnsSameModel() {
        val model = BuildingModel(id = "b1", name = "Test Building")
        val updated = BuildingLoadDistributionEngine.distributeLoads(model)
        assertEquals("b1", updated.id)
        assertTrue("Columns should be empty", updated.columns.isEmpty())
        assertTrue("Footings should be empty", updated.footings.isEmpty())
    }

    @Test
    fun distributeLoads_withSlabAndColumns_calculatesAxialLoads() {
        val nodes = listOf(
            BuildingNode("n1", 0.0, 0.0),
            BuildingNode("n2", 6.0, 0.0),
            BuildingNode("n3", 6.0, 6.0),
            BuildingNode("n4", 0.0, 6.0)
        )

        val columns = listOf(
            BuildingColumn("c1", 0.0, 0.0, axialLoad = 100.0),
            BuildingColumn("c2", 6.0, 0.0, axialLoad = 100.0),
            BuildingColumn("c3", 6.0, 6.0, axialLoad = 100.0),
            BuildingColumn("c4", 0.0, 6.0, axialLoad = 100.0)
        )

        val footings = listOf(
            BuildingFooting("f1", 0.0, 0.0, axialLoad = 100.0),
            BuildingFooting("f2", 6.0, 0.0, axialLoad = 100.0)
        )

        val slab = BuildingSlab(
            id = "s1",
            nodeIds = listOf("n1", "n2", "n3", "n4"),
            thickness = 0.2,
            liveLoad = 2.0,
            deadLoad = 1.5
        )

        val model = BuildingModel(
            id = "b2",
            nodes = nodes,
            columns = columns,
            footings = footings,
            slabs = listOf(slab)
        )

        val updated = BuildingLoadDistributionEngine.distributeLoads(model)

        assertNotNull(updated)
        assertEquals(4, updated.columns.size)
        assertEquals(2, updated.footings.size)

        // Tributary loads should increase column axial loads above baseline self-weight
        updated.columns.forEach { col ->
            assertTrue("Column axial load should be updated to > 100.0", col.axialLoad > 100.0)
        }

        updated.footings.forEach { footing ->
            assertTrue("Footing axial load should be updated", footing.axialLoad > 100.0)
        }
    }
}
