package com.civileg.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.modeler.FrameMutationResult
import io.mockk.mockk
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class FrameMutationBoundaryTest {
    private lateinit var app:Application
    private lateinit var vm:FrameAnalysisViewModel
    private fun id(result:FrameMutationResult<Int>) = (result as FrameMutationResult.Applied).value
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("frame_modeler_draft",Context.MODE_PRIVATE).edit().clear().commit()
        vm=FrameAnalysisViewModel(app,mockk<DesignRepository>(relaxed=true))
    }
    @After fun cleanup() {
        FrameAnalysisViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible=true }.invoke(vm)
    }
    private fun autosavePending(): Boolean {
        val type=FrameAnalysisViewModel::class.java
        val handler=type.getDeclaredField("autosaveHandler").apply { isAccessible=true }.get(vm) as android.os.Handler
        val work=type.getDeclaredField("autosaveRunnable").apply { isAccessible=true }.get(vm) as Runnable
        return handler.hasCallbacks(work)
    }
    @Test fun constructionAloneSchedulesAndPersistsBlankDraftWithoutAnyEdit() {
        val prefs=app.getSharedPreferences("frame_modeler_draft",Context.MODE_PRIVATE)
        assertTrue(prefs.all.isEmpty())
        assertTrue(autosavePending())
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertFalse(autosavePending())
        assertEquals(setOf(FrameDraftStore.V2_KEY),prefs.all.keys)
        val draft=FrameDraftCodec.decode(prefs.getString(FrameDraftStore.V2_KEY,null)!!,FrameDraftSource.V2)
        assertTrue(draft.nodes.isEmpty());assertTrue(draft.members.isEmpty())
        assertTrue(draft.nodalLoads.isEmpty());assertTrue(draft.memberLoads.isEmpty());assertTrue(draft.slabs.isEmpty())
        assertFalse(vm.canUndo)
    }
    @Test fun partialModelIsEditableButRejectedNodeChangesNothingIncludingAutosaveUndo() {
        assertFalse(vm.canUndo)
        val prefs=app.getSharedPreferences("frame_modeler_draft",Context.MODE_PRIVATE)
        // Constructor schedules its own600ms write before any attempted edit. Drain only that
        // independent startup work; rejection must neither change storage nor queue another write.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertTrue(prefs.contains(FrameDraftStore.V2_KEY))
        assertFalse(autosavePending())
        val raw=prefs.all.toMap()
        val history=vm.historyState.value
        assertTrue(vm.addNode(Double.NaN,0.0) is FrameMutationResult.Rejected)
        assertTrue(vm.nodes.value.orEmpty().isEmpty());assertFalse(vm.canUndo)
        assertEquals(history,vm.historyState.value)
        assertFalse(autosavePending())
        assertEquals(raw,prefs.all)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertEquals(raw,prefs.all)
        assertFalse(autosavePending())
        assertEquals(history,vm.historyState.value)
        assertEquals(1,id(vm.addNode(0.0,0.0))) // No support/no members is valid WHILE drawing.
    }
    @Test fun rejectedAtomicDragCreatesNoOrphanAndOneUndoRemovesWholeValidDrag() {
        val start=id(vm.addNode(0.0,0.0))
        val h=vm.historyState.value
        assertTrue(vm.createMemberFromDrag(start,null,Double.POSITIVE_INFINITY,0.0) is FrameMutationResult.Rejected)
        assertEquals(1,vm.nodes.value.orEmpty().size);assertTrue(vm.members.value.orEmpty().isEmpty())
        assertEquals(h,vm.historyState.value)
        assertTrue(vm.createMemberFromDrag(start,null,4.0,0.0) is FrameMutationResult.Applied)
        assertEquals(2,vm.nodes.value.orEmpty().size);assertEquals(1,vm.members.value.orEmpty().size)
        vm.undo()
        assertEquals(listOf(start),vm.nodes.value.orEmpty().map { it.id });assertTrue(vm.members.value.orEmpty().isEmpty())
        vm.redo();assertEquals(2,vm.nodes.value.orEmpty().size);assertEquals(1,vm.members.value.orEmpty().size)
    }
    @Test fun collapseAndBadLoadsDoNotMutateOrConsumeUndo() {
        val a=id(vm.addNode(0.0,0.0));val b=id(vm.addNode(4.0,0.0))
        val m=id(vm.addMemberBetween(a,b));val history=vm.historyState.value
        assertTrue(vm.updateNode(b,0.0,0.0,SupportType.Free) is FrameMutationResult.Rejected)
        assertEquals(4.0,vm.nodes.value!!.last().x,0.0)
        assertTrue(vm.addMemberLoad(m,MemberLoadType.PointLoad,10.0,Double.NaN,"DL") is FrameMutationResult.Rejected)
        assertTrue(vm.addNodalLoad(999,0.0,1.0,0.0,"DL") is FrameMutationResult.Rejected)
        assertTrue(vm.memberLoads.value.orEmpty().isEmpty());assertTrue(vm.nodalLoads.value.orEmpty().isEmpty())
        assertEquals(history,vm.historyState.value)
        assertTrue(vm.addMemberLoad(m,MemberLoadType.PointLoad,-10.0,4.0,"DL") is FrameMutationResult.Applied)
    }
    @Test fun malformedClipboardBatchLeavesNoPartialNodesMembersOrUndo() {
        id(vm.addNode(0.0,0.0));val history=vm.historyState.value
        val bad=com.civileg.app.domain.modeler.ModelerClipboard.Content(
            nodes=listOf(FrameNode(1,1.0,1.0)),members=listOf(FrameMember(1,1,99)))
        // Deliberately inject a broken clipboard to exercise the public VM paste rejection path.
        FrameAnalysisViewModel::class.java.getDeclaredField("clipboard").apply{isAccessible=true}.set(vm,bad)
        assertFalse(vm.pasteClipboard())
        assertEquals(1,vm.nodes.value.orEmpty().size);assertTrue(vm.members.value.orEmpty().isEmpty())
        assertEquals(history,vm.historyState.value)
    }
    @Test fun validPasteBatchHasOneUndoAndRedoRestoresWholeBatch() {
        val a=id(vm.addNode(0.0,0.0));val b=id(vm.addNode(4.0,0.0));id(vm.addMemberBetween(a,b))
        vm.setSelection(setOf(a,b),emptySet());vm.copySelection()
        // First paste succeeds and includes joints/member in one history entry.
        assertTrue(vm.pasteClipboard());assertEquals(4,vm.nodes.value.orEmpty().size)
        vm.undo();assertEquals(2,vm.nodes.value.orEmpty().size);assertEquals(1,vm.members.value.orEmpty().size)
        vm.redo();assertEquals(4,vm.nodes.value.orEmpty().size);assertEquals(2,vm.members.value.orEmpty().size)
    }
}
