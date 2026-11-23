package com.civileg.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.db.DesignRepository
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
    private fun vm() = FrameAnalysisViewModel(app, mockk<DesignRepository>(relaxed = true))
    private fun clear(v: FrameAnalysisViewModel) {
        val f = FrameAnalysisViewModel::class.java.getDeclaredMethod("onCleared")
        f.isAccessible = true; f.invoke(v)
    }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    private val badV1 = "{\"a\":[{\"zz\":1}],\"b\":7}"

    @Test fun pendingRecoveryIsSetAtInitAndNothingOverwritesOrDeletesTheOldRaw() {
        prefs().edit().putString(FrameDraftStore.V1_KEY, badV1).commit()
        val v = vm()
        assertNotNull(v.draftRecovery.value)
        v.updateSteelFy(275.0)             // an incidental mutation path must not persist anything
        idle()
        clear(v)
        assertNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
        assertTrue(prefs().all.keys.any { it.startsWith(FrameDraftStore.QUARANTINE_PREFIX) })
    }

    @Test fun startNewResetsMemoryWritesBlankV2AndRestartRestoresIt() {
        prefs().edit().putString(FrameDraftStore.V1_KEY, badV1).commit()
        val v = vm()
        assertNotNull(v.draftRecovery.value)
        v.startNewDraft()
        assertNull(v.draftRecovery.value)
        assertTrue(v.nodes.value!!.isEmpty())
        assertEquals(355.0, v.steelFy.value!!, 0.0)
        assertNotNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
        val again = vm()
        assertNull(again.draftRecovery.value)         // the chosen v2 is authoritative on restart
    }

    @Test fun steelFyChangeIsAutosavedAndRestoredAfterRestart() {
        val v = vm()
        assertNull(v.draftRecovery.value)
        v.updateSteelFy(275.0)
        idle()
        assertNotNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        val again = vm()
        assertEquals(275.0, again.steelFy.value!!, 0.0)
    }

    @Test fun badV2StaysRetainedAndPending() {
        val badV2 = "{\"format\":2,\"nodes\":\"x\"}"
        prefs().edit().putString(FrameDraftStore.V2_KEY, badV2).commit()
        val v = vm()
        assertNotNull(v.draftRecovery.value)
        idle()
        assertEquals(badV2, prefs().getString(FrameDraftStore.V2_KEY, null))
        assertFalse(v.draftSaveProblem.value == true)
    }
}
