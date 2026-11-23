package com.civileg.app.costcontrol

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.civileg.app.data.repository.*
import com.civileg.app.ui.compose.screens.costcontrol.*
import com.civileg.app.viewmodel.costcontrol.*
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import org.junit.Assert.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="en-rUS")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BoqEditorComposeTest {
    @Before fun installMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun restoreMainDispatcher() { Dispatchers.resetMain() }

    private fun awaitOpened(vm:CostBoqViewModel) {
        try { rule.waitUntil(5000){!vm.state.value.loading} }
        catch(failure:Exception) { throw AssertionError("openProject did not finish; state=${vm.state.value}; main=${Dispatchers.Main}; thread=${Thread.currentThread().name}",failure) }
        val state=vm.state.value
        assertFalse("Recovery failed before UI assertions: $state",state.recoveryBlocked)
        assertNotNull("No candidate after checkpoint open: $state",state.draft)
    }
    @get:Rule val rule=createComposeRule()

    @Test fun unappliedTextSurvivesLazyDisposalAndIsRestoredFromRealCheckpoint() {
        val context=DraftContext()
        try {
            val store=BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context))
            store.write(checkpoint(draft(count=24)))
            val repo=NativeRepo();val vm=CostBoqViewModel(repo,store)
            rule.setContent {
                val state by vm.state.collectAsState()
                MaterialTheme { Box(Modifier.height(700.dp)) {
                    CostBoqEditorDraft(state,vm::chooseMethod,vm::addItem,vm::changeItemInput,vm::applyItemInput,vm::confirmManualQuantity,vm::save,{})
                } }
            }
            rule.runOnIdle{vm.openProject(10)}
            awaitOpened(vm)
            rule.onNode(hasSetTextAction() and hasText("Work item 0")).performTextReplacement("Unapplied exact text")
            rule.waitUntil(5000){vm.state.value.checkpointAcknowledged}
            val list=rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            list.performScrollToIndex(23);list.performScrollToIndex(0)
            rule.onNode(hasSetTextAction() and hasText("Unapplied exact text")).assertExists()
            rule.runOnIdle{vm.save()};assertEquals(0,repo.writes)
            val restored=CostBoqViewModel(repo,BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context)))
            rule.runOnIdle{restored.openProject(10)}
            awaitOpened(restored)
            assertEquals("Unapplied exact text",restored.state.value.itemInputs["item-0"]?.description)
        } finally {context.root.deleteRecursively()}
    }

    @Test fun backDialogShowsCheckpointFailureAndCanRetryWithoutCommittingHistory() {
        val context=DraftContext()
        try {
            val durable=BoundedCostBoqCheckpointStore(AndroidCostBoqAtomicStorage(context))
            durable.write(checkpoint(draft()))
            var fail=false
            val store=object:CostBoqCheckpointStore {
                override fun load(projectId:Long)=durable.load(projectId)
                override fun write(value:CostBoqCheckpoint){if(fail)error("injected storage failure");durable.write(value)}
                override fun delete(projectId:Long)=durable.delete(projectId)
            }
            val repo=NativeRepo();val vm=CostBoqViewModel(repo,store);var left=false
            var back:OnBackPressedDispatcher?=null
            rule.setContent{back=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher;MaterialTheme{CostBoqEditorHost(vm,{left=true},{})}}
            rule.runOnIdle{vm.openProject(10)}
            awaitOpened(vm)
            rule.onNode(hasSetTextAction() and hasText("Work item 0")).performTextReplacement("Do not lose this")
            rule.waitUntil(5000){vm.state.value.checkpointAcknowledged}
            fail=true
            rule.runOnIdle{back!!.onBackPressed()}
            rule.onNodeWithText("Keep draft and leave").performClick()
            rule.waitUntil(5000){vm.state.value.errorKey=="cost_boq_checkpoint_failed" && !vm.state.value.loading}
            val warning="Unsaved-work preservation was not confirmed. Stay here or retry; your current text remains."
            rule.onAllNodesWithText(warning).assertCountEquals(1)
            rule.onNode(hasText(warning) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            assertFalse(left);assertEquals(0,repo.writes)
            fail=false
            rule.onNodeWithText("Keep draft and leave").performClick()
            rule.waitUntil(5000){left}
            assertEquals("Do not lose this",durable.load(10)!!.inputs["item-0"]?.description)
            assertEquals(0,repo.writes)
        } finally {context.root.deleteRecursively()}
    }
}
