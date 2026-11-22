package com.civileg.app.domain.entities

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class FrozenDesignRecordTest {
    @Test fun `same pair has stable identity and either side changes it`() {
        val a = FrozenDesignRecord.capture("{\"span\":5000}", "{\"As\":628.3}")
        assertTrue(a.hasValidFingerprint())
        assertEquals(64, a.revisionSha256.length)
        assertEquals(a.revisionSha256, FrozenDesignRecord.capture(a.inputJson, a.resultJson).revisionSha256)
        assertNotEquals(a.revisionSha256, FrozenDesignRecord.capture("{\"span\":4000}", a.resultJson).revisionSha256)
        assertNotEquals(a.revisionSha256, FrozenDesignRecord.capture(a.inputJson, "{\"As\":700}").revisionSha256)
    }

    @Test fun `roundtrip preserves exact strings and validates the pair`() {
        val a = FrozenDesignRecord.capture("{\"name\":\"R1\"}\n", "{\"isSafe\":false}")
        val b = Gson().fromJson(Gson().toJson(a), FrozenDesignRecord::class.java)
        assertEquals(a.inputJson, b.inputJson)
        assertEquals(a.resultJson, b.resultJson)
        assertEquals(a.revisionSha256, b.revisionSha256)
        assertTrue(b.hasValidFingerprint())
    }

    @Test fun `changed data or unknown schema does not validate`() {
        val a = FrozenDesignRecord.capture("{}", "{}")
        for (key in listOf("inputJson", "resultJson", "revisionSha256", "schemaVersion")) {
            val json = JsonParser.parseString(Gson().toJson(a)).asJsonObject
            if (key == "schemaVersion") json.addProperty(key, 2) else json.addProperty(key, "changed")
            assertFalse(key, Gson().fromJson(json, FrozenDesignRecord::class.java).hasValidFingerprint())
        }
    }

    @Test fun `length framing separates concatenation lookalikes`() {
        assertNotEquals(FrozenDesignRecord.capture("ab", "c").revisionSha256,
            FrozenDesignRecord.capture("a", "bc").revisionSha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank input is rejected`() { FrozenDesignRecord.capture(" ", "{}") }

    @Test(expected = IllegalArgumentException::class)
    fun `blank result is rejected`() { FrozenDesignRecord.capture("{}", " ") }
}
