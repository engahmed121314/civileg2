package com.civileg.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.FrameAnalysisSettings
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real FrameAnalysisViewModel init/onCleared/autosave wiring over real SharedPreferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameAnalysisViewModelDraftRecoveryTest {
    private lateinit var app: Application
    private fun prefs() = app.getSharedPreferences("frame_modeler_draft", Context.MODE_PRIVATE)
    private val created = ArrayList<FrameAnalysisViewModel>()
    private fun vm() = FrameAnalysisViewModel(app, mockk<DesignRepository>(relaxed = true)).also { created.add(it) }
    private fun seed(key: String, raw: String) { assertTrue(prefs().edit().putString(key, raw).commit()); assertEquals(raw, prefs().getString(key, null)) }
    private fun quarantineKeys() = prefs().all.keys.filter { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) }
    /** Exact-once: a VM released here is removed from tracking so @After never clears it again. */
    private fun clear(v: FrameAnalysisViewModel) {
        created.remove(v)
        val f = FrameAnalysisViewModel::class.java.getDeclaredMethod("onCleared")
        f.isAccessible = true; f.invoke(v)
    }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        assertTrue(prefs().edit().clear().commit())
    }

    @org.junit.After fun tearDown() {
        // Stops delayed autosave handlers of every VM this test created, even after a failure.
        // Limitation: the VMs are built directly (not through a ViewModelStore), so cleanup invokes
        // onCleared reflectively. Every VM is attempted and the first failure is rethrown after all ran.
        var failure: Throwable? = null
        created.toList().forEach { try { clear(it) } catch (e: Throwable) { if (failure == null) failure = e } }
        idle()
        failure?.let { throw it }
    }

    private val badV1 = "{\"a\":[{\"zz\":1}],\"b\":7}"

    @Test fun pendingRecoveryIsSetAtInitAndNothingOverwritesOrDeletesTheOldRaw() {
        seed(FrameDraftStore.V1_KEY, badV1)
        val v = vm()
        assertNotNull(v.draftRecovery.value)
        v.updateSteelFy(275.0)             // an incidental mutation path must not persist anything
        idle()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500)) // well past the 600 ms autosave delay
        clear(v)
        assertNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
        val q = quarantineKeys()
        assertEquals(1, q.size)
        assertEquals(badV1, prefs().getString(q[0], null))
    }

    @Test fun startNewResetsMemoryWritesBlankV2AndRestartRestoresIt() {
        seed(FrameDraftStore.V1_KEY, badV1)
        val v = vm()
        assertNotNull(v.draftRecovery.value)
        v.startNewDraft()
        assertNull(v.draftRecovery.value)
        assertTrue(v.nodes.value!!.isEmpty())
        assertTrue(v.members.value!!.isEmpty())
        assertEquals(355.0, v.steelFy.value!!, 0.0)
        assertEquals(FrameAnalysisSettings(), v.settings.value)
        val v2 = prefs().getString(FrameDraftStore.V2_KEY, null)
        assertNotNull(v2)
        val blank = FrameDraftData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), FrameAnalysisSettings(), 355.0)
        assertEquals(blank, FrameDraftCodec.decode(v2!!, FrameDraftSource.V2).copy(warnings = emptyList()))
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
        assertEquals(1, quarantineKeys().size)
        clear(v)                                      // first VM torn down BEFORE the second exists
        idle()
        val again = vm()
        assertNull(again.draftRecovery.value)         // the chosen v2 is authoritative on restart
        assertTrue(again.nodes.value!!.isEmpty())
    }

    @Test fun steelFyChangeIsAutosavedAndRestoredAfterRestart() {
        val v = vm()
        assertNull(v.draftRecovery.value)
        v.updateSteelFy(275.0)
        idle()
        assertNotNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        clear(v); idle()
        val again = vm()
        assertEquals(275.0, again.steelFy.value!!, 0.0)
    }

    @Test fun badV2StaysRetainedAndPending() {
        val badV2 = "{\"format\":2,\"nodes\":\"x\"}"
        seed(FrameDraftStore.V2_KEY, badV2)
        val v = vm()
        assertNotNull(v.draftRecovery.value)
        idle()
        assertEquals(badV2, prefs().getString(FrameDraftStore.V2_KEY, null))
        assertFalse(v.draftSaveProblem.value == true)
    }
}
