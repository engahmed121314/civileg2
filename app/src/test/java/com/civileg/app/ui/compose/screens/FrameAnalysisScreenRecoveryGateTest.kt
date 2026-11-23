package com.civileg.app.ui.compose.screens

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.viewmodel.FrameAnalysisViewModel
import com.civileg.app.viewmodel.FrameDraftStore
import com.civileg.app.viewmodel.ProjectViewModel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real FrameAnalysisScreen with a real VM whose stored draft was not restored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameAnalysisScreenRecoveryGateTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var app: Application
    private fun prefs() = app.getSharedPreferences("frame_modeler_draft", Context.MODE_PRIVATE)
    private fun str(id: Int) = app.getString(id)
    private val badV1 = "{\"a\":[{\"zz\":1}],\"b\":7}"

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit().also { assertTrue(it) }
        assertTrue(prefs().edit().putString(FrameDraftStore.V1_KEY, badV1).commit())
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
    }

    private val projectVmOnce: ProjectViewModel = mockk<ProjectViewModel>(relaxed = true) {
        every { allProjects } returns MutableLiveData(emptyList())
        every { allDesigns } returns MutableLiveData(emptyList())
    }
    private fun projectVm(): ProjectViewModel = projectVmOnce   // created once, outside setContent
    private val vms = ArrayList<FrameAnalysisViewModel>()
    private val mounted = androidx.compose.runtime.mutableStateOf(true)
    private var contentInstalled = false
    private fun newVm() = FrameAnalysisViewModel(app, mockk<DesignRepository>(relaxed = true)).also { vms.add(it) }
    /** Exact-once: released VMs leave the tracked list so @After never clears them again. */
    private fun clearVm(v: FrameAnalysisViewModel) {
        vms.remove(v)
        val f = FrameAnalysisViewModel::class.java.getDeclaredMethod("onCleared"); f.isAccessible = true; f.invoke(v)
    }
    private fun install(screen: @androidx.compose.runtime.Composable () -> Unit) {
        contentInstalled = true
        rule.setContent { if (mounted.value) MaterialTheme { screen() } }
    }
    @org.junit.After fun tearDown() {
        if (contentInstalled) { rule.runOnIdle { mounted.value = false }; rule.waitForIdle() }  // dispose composition BEFORE releasing VMs
        var failure: Throwable? = null   // limitation: VMs built directly, so onCleared is invoked reflectively
        vms.toList().forEach { try { clearVm(it) } catch (e: Throwable) { if (failure == null) failure = e } }
        failure?.let { throw it }
    }

    @Test fun gateShowsRecoveryOnlyAndBackWorksWithoutAnyMutation() {
        val vm = newVm()
        var backs = 0
        install { FrameAnalysisScreen(viewModel = vm, projectViewModel = projectVm(), onNavigateBack = { backs++ }) }
        rule.onNodeWithText(str(R.string.frame_draft_recovery_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.frame_draft_recovery_retained)).assertIsDisplayed()
        // The editor is not composed at all: no editor title, no tabs.
        assertTrue(rule.onAllNodesCount(str(R.string.frame_title)) == 0)
        assertTrue(rule.onAllNodesCount(str(R.string.frame_tab_nodes)) == 0)
        rule.onNode(hasText(str(R.string.frame_draft_recovery_back)) and hasClickAction()).performClick()
        assertEquals(1, backs)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500))
        // Still gated; nothing written, nothing deleted.
        assertNotNull(vm.draftRecovery.value)
        assertNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
    }

    @Test fun startNewButtonWritesBlankChoiceThenHidesGateAndKeepsOldRaw() {
        val vm = newVm()
        install { FrameAnalysisScreen(viewModel = vm, projectViewModel = projectVm(), onNavigateBack = {}) }
        rule.onNode(hasText(str(R.string.frame_draft_recovery_start_new)) and hasClickAction()).performClick()
        rule.waitForIdle()
        assertNull(vm.draftRecovery.value)
        assertNotNull(prefs().getString(FrameDraftStore.V2_KEY, null))
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
        assertTrue(rule.onAllNodesCount(str(R.string.frame_draft_recovery_title)) == 0)
        assertTrue(rule.onAllNodesCount(str(R.string.frame_tab_nodes)) >= 1) // the editor is now composed
    }

    @Test fun freshVmOverSamePrefsAfterAnEarlierGatedVmWasReleasedStillShowsTheGate() {
        // Not activity/process recreation and not a Back-click flow (that is the first test).
        // The earlier VM is released BEFORE the single composition below, which uses the fresh VM.
        clearVm(newVm())
        val again = newVm()
        install { FrameAnalysisScreen(viewModel = again, projectViewModel = projectVm(), onNavigateBack = {}) }
        rule.onNodeWithText(str(R.string.frame_draft_recovery_title)).assertIsDisplayed()
        assertTrue(rule.onAllNodesCount(str(R.string.frame_tab_nodes)) == 0)
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
        assertNull(prefs().getString(FrameDraftStore.V2_KEY, null))
    }

    @Test fun freshVmAfterAnExplicitNewChoiceHasNoGate() {
        val first = newVm()
        first.startNewDraft()
        clearVm(first)
        val recreated = newVm()
        assertNull(recreated.draftRecovery.value)
        install { FrameAnalysisScreen(viewModel = recreated, projectViewModel = projectVm(), onNavigateBack = {}) }
        assertTrue(rule.onAllNodesCount(str(R.string.frame_draft_recovery_title)) == 0)
        assertTrue(rule.onAllNodesCount(str(R.string.frame_tab_nodes)) >= 1)
        assertEquals(badV1, prefs().getString(FrameDraftStore.V1_KEY, null))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesCount(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
