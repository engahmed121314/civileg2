package com.civileg.app.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.civileg.app.domain.entities.HordiSlabDesignSnapshot
import com.civileg.core.engine.rc.ecp.Ecp203LongTermDeflection
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Injected only by the explicit disposable release-QA init, never shipped in the app. */
@RunWith(AndroidJUnit4::class)
class MinifiedSourceSnapshotTest {
    private val gson = Gson()
    private fun fixture(name: String): HordiSlabDesignSnapshot {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val json = assets.open("hordi-source/$name-frozen-record.json").bufferedReader().use { it.readText() }
        return gson.fromJson(json, HordiSlabDesignSnapshot::class.java)
    }

    @Test fun preShrinkSourceJsonSurvivesTheConfiguredMinifiedApp() {
        val identities = mapOf(
            "bottom-two-bars" to "69579c76c3ee8f418343d6655719b3f0b9d6cfbaba6faa367ecb7a6e36574336",
            "top-two-bars" to "68e4c8fe7b336262339ef511fe707ade5ed192af7bf6969055fdcca0391f0d3e",
            "bottom-two-layers" to "b20f3b0168f8fea7cb62705e135d1e524269c09a15a7e8e7929b311991bce9f0",
            "long-term-source" to "009b62ba5bd5c2ab20fcb57443564c7ff8e7d57c901c6b8646af2937209f465e"
        )
        identities.forEach { (name, expectedRevision) ->
            val snapshot = fixture(name)
            assertTrue(name, snapshot.hasValidFingerprint())
            assertEquals(name, expectedRevision, snapshot.revisionSha256)
            assertEquals(JsonParser.parseString(snapshot.inputJson), gson.toJsonTree(snapshot.input()))
            assertEquals(JsonParser.parseString(snapshot.resultJson), gson.toJsonTree(snapshot.result()))
            assertTrue(snapshot.result().safetyAssessmentBlocked)
            assertFalse(snapshot.result().isSafe)
            val geometry = requireNotNull(snapshot.result().calculatedSection)
            assertEquals(600.0, geometry.enteredRibPitchMm, 0.0)
            assertEquals(if (name == "bottom-two-layers") 4 else 2, geometry.bars!!.size)
        }
    }

    @Test fun allNestedLongTermInputModelsAndFrozenStageValuesRemainReadable() {
        val snapshot = fixture("long-term-source")
        val input = snapshot.input()
        val longTerm = requireNotNull(input.sectionContext!!.immediateDeflectionInput!!.longTermInput)
        assertEquals(60, longTerm.endDuration.months)
        assertEquals(6, longTerm.installationDuration!!.months)
        assertEquals(2.0, longTerm.endDuration.xi, 0.0)
        assertEquals(Ecp203LongTermDeflection.Attachment.DEFLECTION_SENSITIVE, longTerm.attachment)
        assertEquals(1.5, longTerm.thermalTotalMm!!, 0.0)
        assertEquals(0.5, longTerm.thermalAfterInstallationMm!!, 0.0)
        assertTrue(longTerm.sustainedLoadsShareCommonApplicationAge)
        val result = snapshot.result()
        assertEquals(611.1777815875193, result.requiredReinforcement, 1e-8)
        assertEquals(24, result.calculationStages!!.size)
        assertEquals("deltaAfter = 4.39733 mm", result.calculationStages!![20].result)
        assertTrue(snapshot.hasValidFingerprint())
    }
}
