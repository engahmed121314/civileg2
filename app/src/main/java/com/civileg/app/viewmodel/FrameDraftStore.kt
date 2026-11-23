package com.civileg.app.viewmodel

import com.google.gson.JsonParseException
import java.security.MessageDigest

/** Minimal prefs surface so the policy can be tested with failure injection. */
internal interface DraftPrefs {
    fun get(key: String): String?
    /** Atomically applies [updates]; returns whether the write was durably accepted. */
    fun commit(updates: Map<String, String>): Boolean
}

internal sealed class FrameDraftStartup {
    /** Nothing stored (or an empty legacy v1). Writes are allowed. */
    object Blank : FrameDraftStartup()
    data class Restored(
        val data: FrameDraftData,
        val source: FrameDraftSource,
        /** False when a v1 migration write failed or did not read back; v1 is still intact. */
        val migrationPersisted: Boolean = true
    ) : FrameDraftStartup()
    /** Stored data exists that was not restored. Automatic v2 writes stay blocked. */
    data class RecoveryPending(val reasons: List<String>, val quarantineRetained: Boolean) : FrameDraftStartup()
}

internal enum class FrameDraftStartNew { STARTED, RETENTION_FAILED, WRITE_FAILED }

internal enum class FrameDraftSave { WRITTEN, BLOCKED_RECOVERY_PENDING, SKIPPED_NOT_ROUNDTRIPPABLE, WRITE_FAILED }

/**
 * Draft storage policy.
 * - A valid v2 value is authoritative, including an empty or settings-only draft. v1 is never
 *   consulted when v2 exists, so clearing after a migration cannot resurrect v1.
 * - Without v2, a legacy v1 is adopted only when it is the exact named-key format. The release
 *   obfuscated v1 shape is NOT auto-adopted (no release-generated fixture proves the mapping);
 *   it is retained and reported for manual recovery.
 * - Anything stored but not restored is retained (v1 untouched; each distinct raw also copied
 *   under a hash-named quarantine key) and automatic v2 writes are blocked until the user makes
 *   a deliberate choice via [resolveStartFresh]. A blank screen is not consent.
 * - Every write is checked for success; a v2 value is written only if decoding it back yields
 *   the same data.
 */
internal class FrameDraftStore(private val prefs: DraftPrefs) {
    private var pending = false
    private val retained = LinkedHashMap<String, String>()   // quarantine key -> raw

    val recoveryPending: Boolean get() = pending

    fun load(): FrameDraftStartup {
        val v2 = prefs.get(V2_KEY)
        if (v2 != null) {
            return try {
                val d = FrameDraftCodec.decode(v2, FrameDraftSource.V2)
                FrameDraftStartup.Restored(d, FrameDraftSource.V2)
            } catch (e: FrameDraftInvalid) {
                hold("v2", v2, "v2: ${e.message}")
            } catch (e: JsonParseException) {
                hold("v2", v2, "v2: unreadable JSON")
            }
        }
        val v1 = prefs.get(V1_KEY) ?: return FrameDraftStartup.Blank
        return try {
            val d = FrameDraftCodec.decode(v1, FrameDraftSource.V1_NAMED)
            // Whole draft restores, including empty-node / settings-only. Migrate only after a
            // verified write (commit + read-back decodes to equal data); v1 stays in place.
            val enc = FrameDraftCodec.encode(d)
            val persisted = prefs.commit(mapOf(V2_KEY to enc)) && verifyV2(d)
            FrameDraftStartup.Restored(d, FrameDraftSource.V1_NAMED, persisted)
        } catch (e: FrameDraftInvalid) {
            hold("v1", v1, "v1 not the exact named format: ${e.message}")
        } catch (e: JsonParseException) {
            hold("v1", v1, "v1: unreadable JSON")
        }
    }

    private fun hold(tag: String, raw: String, reason: String): FrameDraftStartup {
        pending = true
        val key = QUARANTINE_PREFIX + tag + "_" + sha12(raw)
        retained[key] = raw
        val ok = ensureRetained()
        return FrameDraftStartup.RecoveryPending(listOf(reason), ok)
    }

    private fun ensureRetained(): Boolean {
        var all = true
        for ((key, raw) in retained) {
            if (prefs.get(key) == raw) continue
            if (!prefs.commit(mapOf(key to raw)) || prefs.get(key) != raw) all = false
        }
        return all
    }

    private fun verifyV2(expected: FrameDraftData): Boolean {
        val raw = prefs.get(V2_KEY) ?: return false
        val back = try { FrameDraftCodec.decode(raw, FrameDraftSource.V2) } catch (e: FrameDraftInvalid) { return false }
        catch (e: JsonParseException) { return false }
        return back.copy(warnings = emptyList()) == expected.copy(warnings = emptyList())
    }

    fun save(data: FrameDraftData): FrameDraftSave {
        if (pending) return FrameDraftSave.BLOCKED_RECOVERY_PENDING
        val encoded = FrameDraftCodec.encode(data)
        val back = try { FrameDraftCodec.decode(encoded, FrameDraftSource.V2) } catch (e: FrameDraftInvalid) { null }
        if (back == null || back.copy(warnings = emptyList()) != data.copy(warnings = emptyList()))
            return FrameDraftSave.SKIPPED_NOT_ROUNDTRIPPABLE
        return if (prefs.commit(mapOf(V2_KEY to encoded)) && verifyV2(data)) FrameDraftSave.WRITTEN
        else FrameDraftSave.WRITE_FAILED
    }

    /**
     * Explicit user choice to start a new draft. Only after every stored raw is retained
     * (read-back equal) AND the new blank draft is durably written and read back. On any
     * failure pending stays set and nothing is lost. Originals are never deleted.
     */
    fun startNew(blank: FrameDraftData): FrameDraftStartNew {
        if (!pending) return FrameDraftStartNew.STARTED
        if (!ensureRetained()) return FrameDraftStartNew.RETENTION_FAILED
        if (!prefs.commit(mapOf(V2_KEY to FrameDraftCodec.encode(blank))) || !verifyV2(blank))
            return FrameDraftStartNew.WRITE_FAILED
        pending = false
        return FrameDraftStartNew.STARTED
    }

    private fun sha12(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    companion object {
        const val V2_KEY = "frame_modeler_draft_v2"
        const val V1_KEY = "frame_modeler_draft_v1"
        const val QUARANTINE_PREFIX = "frame_modeler_draft_quarantine_"
    }
}
