package com.civileg.app.domain.entities

import com.civileg.app.domain.calculations.ecp.ECPHordiSlabWrapper
import com.civileg.core.engine.rc.ecp.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class HordiSlabDesignSnapshotTest {
    private fun inputs(layers: List<Ecp203BarLayout.Layer> = listOf(Ecp203BarLayout.Layer(2, 20.0))) =
        HordiSlabInputs(30.0, 400.0, 200.0, 600.0, 350.0, 60.0, 5000.0, 62.0, 10.0,
            LoadCombination.DEAD_LIVE, "ECP", Ecp203RibbedSection.Context(25.0, 20.0, layers,
                Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
                Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION, 5000.0, 8.0,
                Ecp203WebShear.VerticalLinks(240.0, Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD,
                    8.0, 2, 200.0), Ecp203RibbedSection.ImmediateDeflectionInput(5000.0, 6.0)))

    private fun result(input: HordiSlabInputs) = with(input) {
        ECPHordiSlabWrapper().designHordiSlab(fcu, fy, ribWidth, ribSpacing, totalThickness,
            toppingThickness, span, designMoment, designShear, loadCombination, sectionContext)
    }

    @Test fun `round trip retains actual source inputs result equations and method hold`() {
        val input = inputs()
        val calculated = result(input)
        val snapshot = HordiSlabDesignSnapshot.capture(input, calculated)
        val restored = Gson().fromJson(Gson().toJson(snapshot), HordiSlabDesignSnapshot::class.java)
        assertEquals(1, restored.schemaVersion)
        assertTrue(restored.hasValidFingerprint())
        assertEquals(input, restored.input())
        assertEquals(calculated, restored.result())
        assertEquals(611.1777815875193, restored.result().requiredReinforcement, 1e-8)
        assertEquals(6.0, restored.input().sectionContext!!.immediateDeflectionInput!!.characteristicDeadLoadKnM2, 0.0)
        assertTrue(restored.result().codeNotes.any { it.contains("deltaLive=deltaD+L-deltaD=3.94892 mm") })
        assertTrue(restored.result().safetyAssessmentBlocked)
        assertFalse(restored.result().isSafe)
        assertFalse(restored.inputJson.contains("spanY"))
        assertFalse(restored.resultJson.contains("concreteVolume"))
        assertFalse(restored.resultJson.contains("steelWeight"))
    }

    @Test fun `identity is stable for identical pairs and changes with either side`() {
        val input = inputs()
        val calculated = result(input)
        val first = HordiSlabDesignSnapshot.capture(input, calculated)
        assertEquals(64, first.revisionSha256.length)
        assertEquals(first.revisionSha256, HordiSlabDesignSnapshot.capture(input, calculated).revisionSha256)
        assertNotEquals(first.revisionSha256,
            HordiSlabDesignSnapshot.capture(input.copy(fcu = 35.0), calculated).revisionSha256)
        assertNotEquals(first.revisionSha256,
            HordiSlabDesignSnapshot.capture(input, calculated.copy(warnings = listOf("Changed scope"))).revisionSha256)
    }

    @Test fun `mutable caller lists cannot change an already completed snapshot`() {
        val layers = mutableListOf(Ecp203BarLayout.Layer(2, 20.0))
        val input = inputs(layers)
        val snapshot = HordiSlabDesignSnapshot.capture(input, result(input))
        layers.clear()
        assertEquals(listOf(Ecp203BarLayout.Layer(2, 20.0)), snapshot.input().sectionContext!!.tensionLayers)
        assertTrue(snapshot.hasValidFingerprint())
    }

    @Test fun `consumers get fresh copies rather than changing stored input or output`() {
        val input = inputs()
        val snapshot = HordiSlabDesignSnapshot.capture(input, result(input))
        (snapshot.input().sectionContext!!.tensionLayers as MutableList).clear()
        (snapshot.result().codeNotes as MutableList).clear()
        assertEquals(1, snapshot.input().sectionContext!!.tensionLayers.size)
        assertTrue(snapshot.result().codeNotes.isNotEmpty())
        assertTrue(snapshot.hasValidFingerprint())
    }

    @Test fun `modified serialized inputs or unsupported schema fail fingerprint validation`() {
        val input = inputs()
        val snapshot = HordiSlabDesignSnapshot.capture(input, result(input))
        val json = JsonParser.parseString(Gson().toJson(snapshot)).asJsonObject
        json.getAsJsonObject("record").addProperty("inputJson", "{}")
        assertFalse(Gson().fromJson(json, HordiSlabDesignSnapshot::class.java).hasValidFingerprint())
        val unknown = JsonParser.parseString(Gson().toJson(snapshot)).asJsonObject
        unknown.getAsJsonObject("record").addProperty("schemaVersion", 2)
        assertFalse(Gson().fromJson(unknown, HordiSlabDesignSnapshot::class.java).hasValidFingerprint())
    }
}
