package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.FrameAnalysisSettings
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM. These tests do NOT reproduce R8 and do not prove the minified fix; the synthetic
 * "obfuscated" fixture is generated here from a holder with fields a..g, not captured from
 * the shipped release. Minified instrumented proof is separate.
 */
class FrameDraftCodecTest {
    private val gson = Gson()

    private fun full() = FrameDraftData(
        nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed), FrameNode(2, 4.0, 0.0), FrameNode(3, 4.0, 3.0),
            FrameNode(4, 0.0, 3.0, SupportType.Pin)
        ),
        members = listOf(
            FrameMember(10, 1, 4, memberType = FrameMemberType.Column,
                concreteSection = ConcreteSectionProps(300.0, 600.0, 30.0, 420.0, 40.0), name = "C1", releaseJ = true),
            FrameMember(11, 4, 3, steelSectionName = "IPE300")
        ),
        nodalLoads = listOf(NodalLoad(3, 5.0, -20.0, 1.5, "LL")),
        memberLoads = listOf(MemberLoad(11, value = 12.5, position = 1.0, loadCase = "WL")),
        slabs = listOf(SlabPanel(20, listOf(1, 2, 3, 4), 180.0, 5.0, "DL")),
        settings = FrameAnalysisSettings(steelFy = 275.0, includeSelfWeight = true),
        steelFy = 420.0
    )

    private fun v2(d: FrameDraftData = full()) = FrameDraftCodec.encode(d)

    private class HolderNamed(val version: Int, val nodes: List<FrameNode>, val members: List<FrameMember>,
        val nodalLoads: List<NodalLoad>, val memberLoads: List<MemberLoad>, val slabs: List<SlabPanel>,
        val settings: FrameAnalysisSettings, val steelFy: Double)
    private class HolderObf(val a: List<FrameNode>, val b: List<FrameMember>, val c: List<NodalLoad>,
        val d: List<MemberLoad>, val e: List<SlabPanel>, val f: FrameAnalysisSettings, val g: Double)

    private fun namedV1(d: FrameDraftData = full()) = gson.toJson(
        HolderNamed(1, d.nodes, d.members, d.nodalLoads, d.memberLoads, d.slabs, d.settings, d.steelFy))
    private fun obfV1(d: FrameDraftData = full()) = gson.toJson(
        HolderObf(d.nodes, d.members, d.nodalLoads, d.memberLoads, d.slabs, d.settings, d.steelFy))


    private class FakePrefs : DraftPrefs {
        val map = HashMap<String, String>()
        var failCommits = false
        var commitCalls = 0
        override fun get(key: String) = map[key]
        override fun commit(updates: Map<String, String>): Boolean {
            commitCalls++
            if (failCommits) return false
            map.putAll(updates); return true
        }
    }

    private fun emptyData(settings: FrameAnalysisSettings = FrameAnalysisSettings(), fy: Double = 355.0) =
        FrameDraftData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), settings, fy)

    private fun dec(raw: String, s: FrameDraftSource) = FrameDraftCodec.decode(raw, s)
    private fun rejects(raw: String, s: FrameDraftSource = FrameDraftSource.V2) {
        try { dec(raw, s); throw AssertionError("accepted") } catch (_: FrameDraftInvalid) {
        } catch (_: com.google.gson.JsonParseException) { }
    }

    @Test fun v2RoundTripKeepsEveryListSettingsAndFy() {
        assertEquals(full(), dec(v2(), FrameDraftSource.V2))
    }
    @Test fun namedV1ExactFormatDecodes() { assertEquals(full(), dec(namedV1(), FrameDraftSource.V1_NAMED)) }
    @Test fun obfuscatedDecodeIsOnlyAvailableExplicitlyAndRolesMustValidate() {
        assertEquals(full(), dec(obfV1(), FrameDraftSource.V1_RELEASE_1_8_0_OBFUSCATED))
        rejects(obfV1().replace("\"c\":[{\"nodeId\"", "\"c\":[{\"memberId\""), FrameDraftSource.V1_RELEASE_1_8_0_OBFUSCATED)
    }
    @Test fun structuralProblemsReject() {
        val ok = v2()
        rejects(ok.replace("\"nodes\":[", "\"nodes\":{\"x\":1},\"zz\":["))
        rejects(ok.replace("\"nodes\":[", "\"nodes\":[null,"))
        rejects(ok.replace("\"x\":4.0,\"y\":0.0", "\"y\":0.0"))
        rejects(ok.replace("\"support\":\"Fixed\"", "\"support\":\"Nope\""))
        rejects(ok.replace("\"x\":4.0", "\"x\":1e999"))
        rejects(ok.replace("\"id\":1,", "\"id\":1.5,"))
        rejects(ok.replace("\"format\":2", "\"format\":2.5"))
        rejects(ok.replace("\"format\":2", "\"format\":3"))
        rejects(namedV1().replace("\"version\":1", "\"version\":2"), FrameDraftSource.V1_NAMED)
        rejects(namedV1().replace("\"version\":1", "\"version\":1.5"), FrameDraftSource.V1_NAMED)
        rejects(namedV1().replace("\"version\":1,", ""), FrameDraftSource.V1_NAMED)
        rejects(namedV1().replace("\"eSteel\":", "\"eSteelX\":"), FrameDraftSource.V1_NAMED)
        rejects("[1,2]"); rejects("{not json")
    }
    @Test fun reachableIncompleteEditsRestoreAsStoredWithWarnings() {
        val ok = v2()
        val a = dec(ok.replace("\"nodeJ\":3", "\"nodeJ\":99"), FrameDraftSource.V2)
        assertEquals(99, a.members.last().nodeJ); assertTrue(a.warnings.single().contains("member"))
        assertTrue(dec(ok.replace("\"nodeId\":3", "\"nodeId\":99"), FrameDraftSource.V2).warnings.single().contains("nodal"))
        assertTrue(dec(ok.replace("\"memberId\":11", "\"memberId\":99"), FrameDraftSource.V2).warnings.single().contains("member load"))
        assertTrue(dec(ok.replace("\"nodeIds\":[1,2,3,4]", "\"nodeIds\":[1,2,3]"), FrameDraftSource.V2).warnings.single().contains("slab"))
        assertTrue(dec(ok.replace("\"id\":2,", "\"id\":1,"), FrameDraftSource.V2).warnings.any { it.contains("duplicate node") })
        val z = dec(ok.replace("\"steelFy\":420.0}", "\"steelFy\":0.0}"), FrameDraftSource.V2)
        assertEquals(0.0, z.steelFy, 0.0); assertTrue(z.warnings.isEmpty())
    }

    // ---- store policy (fake prefs, "restart" = new store over the same prefs) ----

    @Test fun nothingStoredIsBlankAndAutosaveWorks() {
        val p = FakePrefs(); val s = FrameDraftStore(p)
        assertTrue(s.load() === FrameDraftStartup.Blank)
        assertEquals(FrameDraftSave.WRITTEN, s.save(full()))
    }

    @Test fun namedV1MigratesAfterVerifiedWriteAndV1StaysUntouched() {
        val p = FakePrefs(); p.map[FrameDraftStore.V1_KEY] = namedV1()
        val r = FrameDraftStore(p).load() as FrameDraftStartup.Restored
        assertEquals(full(), r.data)
        assertEquals(namedV1(), p.map[FrameDraftStore.V1_KEY])
        assertEquals(full(), dec(p.map[FrameDraftStore.V2_KEY]!!, FrameDraftSource.V2))
    }

    @Test fun clearAfterMigrationThenRestartDoesNotResurrectV1() {
        val p = FakePrefs(); p.map[FrameDraftStore.V1_KEY] = namedV1()
        var s = FrameDraftStore(p); s.load()
        assertEquals(FrameDraftSave.WRITTEN, s.save(emptyData()))          // user cleared everything
        s = FrameDraftStore(p)                                             // restart
        val r = s.load() as FrameDraftStartup.Restored
        assertTrue(r.data.nodes.isEmpty())                                 // empty v2 is authoritative
        assertTrue(!s.recoveryPending)
    }

    @Test fun settingsOnlyV2IsAuthoritativeAndRestored() {
        val p = FakePrefs(); p.map[FrameDraftStore.V1_KEY] = namedV1()
        val custom = emptyData(FrameAnalysisSettings(steelFy = 275.0, includeSelfWeight = true), 420.0)
        p.map[FrameDraftStore.V2_KEY] = FrameDraftCodec.encode(custom)
        val r = FrameDraftStore(p).load() as FrameDraftStartup.Restored
        assertEquals(custom, r.data)
    }

    @Test fun releaseObfuscatedV1IsRetainedAndBlocksAutosaveAcrossRestarts() {
        val p = FakePrefs(); p.map[FrameDraftStore.V1_KEY] = obfV1()
        var s = FrameDraftStore(p)
        val pend = s.load() as FrameDraftStartup.RecoveryPending
        assertTrue(pend.quarantineRetained)
        assertEquals(FrameDraftSave.BLOCKED_RECOVERY_PENDING, s.save(emptyData()))   // blank is not consent
        assertEquals(FrameDraftSave.BLOCKED_RECOVERY_PENDING, s.save(full()))
        assertTrue(FrameDraftStore.V2_KEY !in p.map)
        assertEquals(obfV1(), p.map[FrameDraftStore.V1_KEY])
        assertEquals(1, p.map.keys.count { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) })
        s = FrameDraftStore(p)                                                         // restart
        assertTrue(s.load() is FrameDraftStartup.RecoveryPending)
        assertEquals(1, p.map.keys.count { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) }) // no duplicate
    }

    @Test fun invalidV2IsNeverReplacedByBlankAndDistinctCorruptionsAreEachRetained() {
        val p = FakePrefs()
        p.map[FrameDraftStore.V2_KEY] = "{bad one"
        var s = FrameDraftStore(p); assertTrue(s.load() is FrameDraftStartup.RecoveryPending)
        assertEquals(FrameDraftSave.BLOCKED_RECOVERY_PENDING, s.save(emptyData()))
        assertEquals("{bad one", p.map[FrameDraftStore.V2_KEY])
        p.map[FrameDraftStore.V2_KEY] = "{bad two"                                     // a different corruption later
        s = FrameDraftStore(p); s.load()
        val q = p.map.filterKeys { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) }.values.toSet()
        assertEquals(setOf("{bad one", "{bad two"), q)
    }

    @Test fun startNewNeedsRetentionAndDurableChoiceAndNeverDeletesOriginals() {
        val p = FakePrefs(); p.map[FrameDraftStore.V1_KEY] = obfV1(); p.failCommits = true
        val s = FrameDraftStore(p)
        val pend = s.load() as FrameDraftStartup.RecoveryPending
        assertTrue(!pend.quarantineRetained)
        assertEquals(FrameDraftSave.BLOCKED_RECOVERY_PENDING, s.save(full()))
        assertEquals(FrameDraftStartNew.RETENTION_FAILED, s.startNew(emptyData()))
        assertTrue(s.recoveryPending)
        p.failCommits = false
        // retention ok but the durable choice write fails -> still pending
        val flaky = object : DraftPrefs {
            override fun get(key: String) = p.get(key)
            override fun commit(updates: Map<String, String>): Boolean =
                if (updates.containsKey(FrameDraftStore.V2_KEY)) false else p.commit(updates)
        }
        val s2 = FrameDraftStore(flaky); s2.load()
        assertEquals(FrameDraftStartNew.WRITE_FAILED, s2.startNew(emptyData()))
        assertTrue(s2.recoveryPending)
        assertEquals(FrameDraftSave.BLOCKED_RECOVERY_PENDING, s2.save(full()))
        // now everything works: explicit choice
        val s3 = FrameDraftStore(p); s3.load()
        assertEquals(FrameDraftStartNew.STARTED, s3.startNew(emptyData()))
        assertEquals(FrameDraftSave.WRITTEN, s3.save(full()))
        assertEquals(obfV1(), p.map[FrameDraftStore.V1_KEY])                           // original kept
        assertTrue(p.map.keys.any { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) })
        val again = FrameDraftStore(p); assertTrue(again.load() is FrameDraftStartup.Restored)  // restart: v2 chosen
    }

    @Test fun emptyOrSettingsOnlyNamedV1RestoresWholeAndMigrates() {
        val p = FakePrefs()
        val custom = emptyData(FrameAnalysisSettings(steelFy = 275.0), 420.0)
        p.map[FrameDraftStore.V1_KEY] = namedV1(custom)
        val r = FrameDraftStore(p).load() as FrameDraftStartup.Restored
        assertEquals(custom, r.data); assertTrue(r.migrationPersisted)
        assertEquals(custom, dec(p.map[FrameDraftStore.V2_KEY]!!, FrameDraftSource.V2))
    }

    @Test fun writeFailureIsReportedAndNotTreatedAsSaved() {
        val p = FakePrefs(); val s = FrameDraftStore(p); s.load()
        assertEquals(FrameDraftSave.WRITTEN, s.save(full()))
        p.failCommits = true
        assertEquals(FrameDraftSave.WRITE_FAILED, s.save(full().copy(steelFy = 500.0)))
        assertEquals(420.0, dec(p.map[FrameDraftStore.V2_KEY]!!, FrameDraftSource.V2).steelFy, 0.0)
    }

    @Test fun migrationWriteFailureKeepsV1AndStillRestoresInMemory() {
        val p = FakePrefs(); p.map[FrameDraftStore.V1_KEY] = namedV1(); p.failCommits = true
        val r = FrameDraftStore(p).load() as FrameDraftStartup.Restored
        assertEquals(full(), r.data); assertTrue(!r.migrationPersisted)
        assertTrue(FrameDraftStore.V2_KEY !in p.map); assertEquals(namedV1(), p.map[FrameDraftStore.V1_KEY])
    }
}
