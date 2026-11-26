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

    private fun square(columns: List<BuildingColumn>, footings: List<BuildingFooting> = emptyList(), live: Double = 2.0) = BuildingModel(
        id = "b2",
        nodes = listOf(BuildingNode("n1", 0.0, 0.0), BuildingNode("n2", 6.0, 0.0), BuildingNode("n3", 6.0, 6.0), BuildingNode("n4", 0.0, 6.0)),
        columns = columns, footings = footings,
        slabs = listOf(BuildingSlab("s1", listOf("n1", "n2", "n3", "n4"), thickness = 0.2, liveLoad = live, deadLoad = 1.5))
    )

    private val four = listOf(
        BuildingColumn("c1", 0.0, 0.0), BuildingColumn("c2", 6.0, 0.0), BuildingColumn("c3", 6.0, 6.0), BuildingColumn("c4", 0.0, 6.0)
    )

    @Test
    fun hand_values_corner_columns_share_the_panel_equally() {
        // Slab dead = 0.2 x 24 + 1.5 = 6.3 kN/m2 x 36 m2 = 226.8 kN; live 2.0 x 36 = 72 kN. Each of 4 columns: D 56.7, L 18.0.
        // Column self weight 0.3 x 0.6 x 3.5 x 24 = 15.12 kN.  Service = 56.7 + 18 + 15.12 = 89.82 kN.
        val t = BuildingLoadDistributionEngine.takedown(square(four, listOf(BuildingFooting("f1", 0.0, 0.0))))
        val c = t.columnLoads.first { it.id == "c1" }
        assertEquals(56.7 + 15.12, c.dead, 1e-9)
        assertEquals(18.0, c.live, 1e-9)
        assertEquals(89.82, c.service, 1e-9)
        assertEquals(89.8, t.model.columns[0].axialLoad, 1e-9)
        // Footing 1.8 x 1.8 x 0.6 x 24 = 46.656 kN self weight
        assertEquals(89.82 + 46.656, t.footingLoads[0].service, 1e-9)
        assertEquals(136.5, t.model.footings[0].axialLoad, 1e-9)
        // Load balance: the four column shares add up to the slab load plus four column self weights
        assertEquals(226.8 + 72.0 + 4 * 15.12, t.columnLoads.sumOf { it.service }, 1e-9)
        assertTrue(t.warnings.isEmpty())
    }

    @Test
    fun no_hidden_minimum_and_no_far_column_gets_the_load() {
        // Only two corner columns plus one far away. The far column receives nothing, the corner pair share the panel, no 100 kN floor.
        val cols = listOf(BuildingColumn("c1", 0.0, 0.0), BuildingColumn("c2", 6.0, 0.0), BuildingColumn("far", 40.0, 40.0, width = 0.2, depth = 0.2, height = 1.0))
        val t = BuildingLoadDistributionEngine.takedown(square(cols))
        val far = t.columnLoads.first { it.id == "far" }
        assertEquals(0.2 * 0.2 * 1.0 * 24.0, far.service, 1e-9)       // 0.96 kN, not raised to 100 kN
        assertEquals(0.0, far.live, 0.0)
        assertEquals((226.8 + 72.0) / 2 + 15.12, t.columnLoads.first { it.id == "c1" }.service, 1e-9)
        assertTrue(t.warnings.any { it.contains("only 2 of 4 corners") })
        assertTrue(t.warnings.any { it.contains("far") })
    }

    @Test
    fun footing_without_a_column_is_flagged_and_not_given_a_load() {
        val t = BuildingLoadDistributionEngine.takedown(square(four, listOf(BuildingFooting("lone", 3.0, 3.0, axialLoad = 600.0))))
        assertEquals("NO_COLUMN", t.model.footings[0].status)
        assertEquals(600.0, t.model.footings[0].axialLoad, 0.0)
        assertTrue(t.warnings.any { it.contains("Footing lone") })
    }

    @Test
    fun slab_without_columns_is_reported() {
        val t = BuildingLoadDistributionEngine.takedown(square(listOf(BuildingColumn("c9", 20.0, 20.0))))
        assertTrue(t.warnings.any { it.contains("no column stands on a slab corner") })
    }

    @Test
    fun invalid_input_is_rejected() {
        try {
            BuildingLoadDistributionEngine.takedown(square(four, live = -1.0)); fail("accepted negative live load")
        } catch (e: IllegalArgumentException) { }
        try {
            BuildingLoadDistributionEngine.takedown(square(four), concreteUnitWeight = 0.0); fail("accepted zero unit weight")
        } catch (e: IllegalArgumentException) { }
    }
}
