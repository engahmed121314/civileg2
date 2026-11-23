package com.civileg.app.viewmodel

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDraftRawExportTest {
    // Synthetic strings only (no real user data): quotes, backslashes, unicode, newline, braces.
    private val tricky = "{\"a\":\"b\\\"c\",\"u\":\"\u0645\u0646\u0634\u0623 \\u00e9\",\"n\":\"x\\ny\"}\n\t<>&'"

    @Test fun envelopeRoundTripsEveryRawByteExact() {
        val raws = linkedMapOf("v1" to tricky, "q" to "", "z" to "plain")
        val text = FrameDraftRawExport.build(raws, "1.8.0", 123L)
        val back = FrameDraftRawExport.parse(text)
        assertEquals(raws, back)
        assertArrayEquals(tricky.toByteArray(Charsets.UTF_8), back.getValue("v1").toByteArray(Charsets.UTF_8))
        assertTrue(text.contains("\"format\":\"" + FrameDraftRawExport.FORMAT + "\""))
    }

    @Test fun writeNewNeverOverwritesAndReadsBackIdentical() {
        val dir = java.nio.file.Files.createTempDirectory("exp").toFile()
        val text = FrameDraftRawExport.build(mapOf("v1" to tricky), "x", 5L)
        val a = FrameDraftRawExport.writeNew(dir, text, 5L)
        val first = a.readBytes()
        val b = FrameDraftRawExport.writeNew(dir, FrameDraftRawExport.build(mapOf("v1" to "other"), "x", 5L), 5L)
        assertNotEquals(a.absolutePath, b.absolutePath)
        assertArrayEquals(first, a.readBytes())
        assertEquals(text.toByteArray(Charsets.UTF_8).size, first.size)
        assertEquals(mapOf("v1" to tricky), FrameDraftRawExport.parse(a.readText(Charsets.UTF_8)))
    }

    @Test fun failedWriteThrowsAndLeavesNoPartialFile() {
        val notADir = File.createTempFile("blocker", ".tmp")
        val failed = try { FrameDraftRawExport.writeNew(File(notADir, "sub"), "x", 1L); false } catch (e: java.io.IOException) { true }
        assertTrue(failed)
        assertTrue(notADir.isFile)
    }

    @Test fun storeExportsAllRetainedRawsAndDoesNotMutatePrefs() {
        val map = HashMap<String, String>()
        map[FrameDraftStore.V1_KEY] = tricky
        val store = FrameDraftStore(object : DraftPrefs {
            override fun get(key: String) = map[key]
            override fun commit(updates: Map<String, String>): Boolean { map.putAll(updates); return true }
            override fun keysWithPrefix(prefix: String) = map.keys.filter { it.startsWith(prefix) }.toSet()
        })
        store.load()
        val before = HashMap(map)
        val raws = store.rawsForExport()
        assertEquals(tricky, raws[FrameDraftStore.V1_KEY])
        assertTrue(raws.keys.any { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) })
        assertEquals(tricky, raws.entries.first { it.key.startsWith(FrameDraftStore.QUARANTINE_PREFIX) }.value)
        assertEquals(before, map)
    }

    @Test fun reconstructedStoreExportsPersistedQuarantineCopiesOfEarlierSessionsOnly() {
        val map = HashMap<String, String>()
        val prefs = object : DraftPrefs {
            override fun get(key: String) = map[key]
            override fun commit(updates: Map<String, String>): Boolean { map.putAll(updates); return true }
            override fun keysWithPrefix(prefix: String) = map.keys.filter { it.startsWith(prefix) }.toSet()
        }
        map[FrameDraftStore.V1_KEY] = "bad-one"
        FrameDraftStore(prefs).load()                       // session 1 quarantines "bad-one"
        map[FrameDraftStore.V1_KEY] = "bad-two"             // a later, different raw
        map["unrelated_other_pref"] = "must-not-be-exported"
        val s2 = FrameDraftStore(prefs); s2.load()          // session 2: new store object
        val raws = s2.rawsForExport()
        assertEquals(2, raws.keys.count { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) })
        assertTrue(raws.values.containsAll(listOf("bad-one", "bad-two")))
        assertTrue(!raws.containsKey("unrelated_other_pref"))
        val dir = java.nio.file.Files.createTempDirectory("exp2").toFile()
        val f = FrameDraftRawExport.writeNew(dir, FrameDraftRawExport.build(raws, "x", 9L), 9L, raws)
        assertEquals(raws, FrameDraftRawExport.parse(f.readText(Charsets.UTF_8)))
    }
}
