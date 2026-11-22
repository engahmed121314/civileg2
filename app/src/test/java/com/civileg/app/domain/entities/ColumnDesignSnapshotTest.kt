package com.civileg.app.domain.entities

import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Revision consistency only. Synthetic DTOs are not engineering validation fixtures. */
class ColumnDesignSnapshotTest {
    private fun input() = ColumnCalculationInputs(300.0, 400.0, 3.0, 25.0, 400.0,
        1000.0, 1500.0, 10.0, 20.0, DesignCode.ECP, LoadCombination.DEAD_LIVE,
        "TEST_UNVERIFIED_ACTION_CONVENTION", 1.5, false, "SOLID", false, 16, true, null, false, true)
    private fun result(warnings: List<String> = emptyList()) = CalculatorEngine.ColumnResult(
        width = 300.0, depth = 400.0, code = DesignCode.ECP,
        reinforcement = CalculatorEngine.ReinforcementBar(numBars = 10, diameter = 16), warnings = warnings)

    @Test fun `same captured content has same valid revision`() {
        val a = ColumnDesignSnapshot.capture(input(), result())
        val b = ColumnDesignSnapshot.capture(input(), result())
        assertTrue(a.hasValidFingerprint())
        assertEquals(a.revisionSha256, b.revisionSha256)
        assertEquals(input(), a.input())
        assertEquals(result(), a.result())
    }

    @Test fun `material height actions selection and self weight affect completed identity`() {
        val i = input()
        val baseline = ColumnDesignSnapshot.capture(i, result()).revisionSha256
        listOf(i.copy(heightM = 4.0), i.copy(fcuMpa = 30.0), i.copy(fyMpa = 500.0),
            i.copy(actionBasis = "OTHER_UNVERIFIED_CONVENTION"), i.copy(autoIncludeSelfWeight = false),
            i.copy(manualBarCount = 12, autoOptimize = false, reinforcementIntent = ColumnReinforcementIntent.REQUESTED_LAYOUT), i.copy(preferredDiameterMm = 18),
            i.copy(reinforcementIntent = ColumnReinforcementIntent.REQUESTED_LAYOUT), i.copy(axialLoadPassedToEngineKn = 1600.0)).forEach {
            assertNotEquals(baseline, ColumnDesignSnapshot.capture(it, result()).revisionSha256)
        }
    }

    @Test fun `capture and every read isolate nested mutable result lists`() {
        val warnings = mutableListOf("Original limitation")
        val snapshot = ColumnDesignSnapshot.capture(input(), result(warnings))
        warnings.add("Later caller edit")
        val read = snapshot.result()
        (read.warnings as MutableList<String>).add("Later consumer edit")
        assertEquals(listOf("Original limitation"), snapshot.result().warnings)
        assertTrue(snapshot.hasValidFingerprint())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `input and result codes cannot be mixed`() {
        ColumnDesignSnapshot.capture(input(), result().copy(code = DesignCode.ACI))
    }

    @Test fun `changed saved input JSON cannot retain the old fingerprint`() {
        val snapshot = ColumnDesignSnapshot.capture(input(), result())
        val objectJson = JsonParser.parseString(Gson().toJson(snapshot)).asJsonObject
        objectJson.addProperty("inputJson", snapshot.inputJson.replace("TEST_UNVERIFIED_ACTION_CONVENTION", "CHANGED"))
        assertFalse(Gson().fromJson(objectJson, ColumnDesignSnapshot::class.java).hasValidFingerprint())
    }

    @Test fun `malformed null JSON fails closed without crashing a consumer`() {
        val objectJson = JsonParser.parseString(Gson().toJson(ColumnDesignSnapshot.capture(input(), result()))).asJsonObject
        objectJson.add("inputJson", com.google.gson.JsonNull.INSTANCE)
        assertFalse(Gson().fromJson(objectJson, ColumnDesignSnapshot::class.java).hasValidFingerprint())
    }

    @Test fun `stored revision restore returns the exact frozen payload`() {
        val original = ColumnDesignSnapshot.capture(input(), result())
        val restored = ColumnDesignSnapshot.restoreStored(original.schemaVersion, original.revisionSha256,
            original.inputJson, original.resultJson)
        assertEquals(original.inputJson, restored.inputJson)
        assertEquals(original.resultJson, restored.resultJson)
        assertEquals(original.revisionSha256, restored.revisionSha256)
        assertEquals(input(), restored.input())
    }

    @Test fun `stored restore rejects mismatched fingerprint and unsupported schema`() {
        val original = ColumnDesignSnapshot.capture(input(), result())
        assertThrows(IllegalArgumentException::class.java) {
            ColumnDesignSnapshot.restoreStored(99, original.revisionSha256, original.inputJson, original.resultJson)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ColumnDesignSnapshot.restoreStored(original.schemaVersion, "incorrect", original.inputJson, original.resultJson)
        }
    }

    @Test fun `unknown schema fails closed even when stored JSON is otherwise unchanged`() {
        val snapshot = ColumnDesignSnapshot.capture(input(), result())
        val objectJson = JsonParser.parseString(Gson().toJson(snapshot)).asJsonObject
        objectJson.addProperty("schemaVersion", 99)
        assertFalse(Gson().fromJson(objectJson, ColumnDesignSnapshot::class.java).hasValidFingerprint())
    }
}
