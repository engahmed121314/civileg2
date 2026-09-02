package com.civileg.core.calculations.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RebarModelTest {

    private fun sampleSet() = ReinforcementSet(
        mainTensionBars = listOf(
            ReinforcementBar(
                mark = "B-T1", diameter = 20.0, totalLengthMm = 5240.0,
                shape = "HOOK_90", element = "beam", codeReference = "ECP flexure",
                quantity = 5, hookType = "90°", hookLength = 240.0
            )
        ),
        mainCompressionBars = listOf(
            ReinforcementBar(
                mark = "B-B1", diameter = 12.0, totalLengthMm = 5000.0,
                shape = "STRAIGHT", element = "beam", codeReference = "ECP compression",
                quantity = 2
            )
        ),
        stirrups = listOf(
            ReinforcementBar(
                mark = "B-S1", diameter = 8.0, totalLengthMm = 1800.0,
                shape = "HOOK_135", element = "beam", codeReference = "ECP shear",
                quantity = 12, spacing = 150.0, hookType = "135°", hookLength = 96.0
            )
        ),
        distributionBars = emptyList()
    )

    @Test
    fun expansionCreatesOneInstancePerBarWithUniqueIds() {
        val set = sampleSet()
        val model = set.toRebarModel()

        assertEquals(5 + 2 + 12, model.bars.size)
        assertEquals(model.bars.size, model.ids.size)
        assertTrue(model.ids.contains("B-T1-beam-1"))
        assertTrue(model.ids.contains("B-T1-beam-5"))
        assertTrue(model.ids.contains("B-S1-beam-12"))
    }

    @Test
    fun expandedModelPreservesTraceability() {
        val model = sampleSet().toRebarModel()

        val tension = model.byElement.getValue("beam").filter { it.mark == "B-T1" }
        assertEquals(5, tension.size)
        tension.forEach { bar ->
            assertEquals(20.0, bar.diameter, 0.0)
            assertEquals("ECP flexure", bar.codeReference)
            assertEquals("HOOK_90", bar.shape)
            assertEquals(240.0, bar.hookLength ?: 0.0, 0.0)
        }
        assertEquals(
            setOf("B-T1", "B-B1", "B-S1"),
            model.elementMarks.getValue("beam")
        )
    }

    @Test
    fun expandedWeightMatchesReinforcementSet() {
        val set = sampleSet()
        val model = set.toRebarModel()

        assertEquals(set.totalWeightKg, model.totalWeightKg, 0.000001)
    }

    @Test
    fun emptySetExpandsToEmptyModel() {
        val model = ReinforcementSet().toRebarModel()

        assertTrue(model.bars.isEmpty())
        assertEquals(0.0, model.totalWeightKg, 0.0)
    }

    @Test
    fun roundTripCollapsesBackToEquivalentSet() {
        val set = sampleSet()
        val roundTripped = set.toRebarModel().toReinforcementSet()

        assertEquals(set.totalWeightKg, roundTripped.totalWeightKg, 0.000001)
        assertEquals(set.mainTensionBars.size, roundTripped.mainTensionBars.size)
        assertEquals(set.mainCompressionBars.size, roundTripped.mainCompressionBars.size)
        assertEquals(set.stirrups.size, roundTripped.stirrups.size)
        assertEquals(5, roundTripped.mainTensionBars.first().quantity)
        assertEquals(12, roundTripped.stirrups.first().quantity)
    }

    @Test
    fun buildRejectsDuplicateIds() {
        val dup = listOf(
            BarInstance("X-1", "X", 12.0, 1000.0, "STRAIGHT", "e", "r", 1),
            BarInstance("X-1", "X", 12.0, 1000.0, "STRAIGHT", "e", "r", 2)
        )
        assertThrows(IllegalArgumentException::class.java) { RebarModel.build(dup) }
    }

    @Test
    fun buildRejectsNonPositiveLength() {
        val bad = listOf(
            BarInstance("Y-1", "Y", 12.0, -5.0, "STRAIGHT", "e", "r", 1)
        )
        assertThrows(IllegalArgumentException::class.java) { RebarModel.build(bad) }
    }

    @Test
    fun scheduleTextListsEveryBar() {
        val text = sampleSet().toRebarModel().scheduleText

        assertTrue(text.startsWith("BAR SCHEDULE"))
        assertTrue(text.contains("B-T1-beam-1"))
        assertTrue(text.contains("B-S1-beam-12"))
        assertTrue(text.contains("Ø20"))
    }
}