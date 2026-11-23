package com.civileg.app.viewmodel.costcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.costcontrol.CostBoqHeadEntity
import com.civileg.app.domain.repository.CostBoqRepository
import com.civileg.app.domain.repository.SavedCostBoq
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.civileg.app.data.repository.CostBoqCheckpoint
import com.civileg.app.data.repository.CostBoqCheckpointStore

/** Work method, never a user identity/qualification choice. */
enum class BoqWorkMethod { ENTER_ITEMS_AND_QUANTITIES, DERIVE_FROM_PROJECT_DATA }

data class CostBoqUiState(
    val projectId: Long? = null,
    val method: BoqWorkMethod = BoqWorkMethod.ENTER_ITEMS_AND_QUANTITIES,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val saved: SavedCostBoq? = null,
    val draft: SavedCostBoq? = null,
    val unsavedChanges: Boolean = false,
    val itemInputs: Map<String,CostBoqItemInput> = emptyMap(),
    val checkpointPending:Boolean=false,
    val checkpointAcknowledged:Boolean=false,
    val recoveryBlocked:Boolean=false,
    val currentHeadAdvanced:Boolean=false,
    val errorKey: String? = null
)

/** Unpublished/unbound. No in-memory repository is passed off as durable storage. */
class CostBoqViewModel(private val repository: CostBoqRepository,private val checkpoints:CostBoqCheckpointStore) : ViewModel() {
    private val mutable = MutableStateFlow(CostBoqUiState())
    val state: StateFlow<CostBoqUiState> = mutable
    @Volatile private var sequence = 0L
    @Volatile private var checkpointGeneration=0L
    private val checkpointMutex=Mutex()
    private var readJob: Job? = null
    private var saveJob: Job? = null

    /** Caller confirms discard before changing project. Refuse switching during a save. */
    fun openProject(projectId: Long, discardConfirmed: Boolean = false) {
        require(projectId > 0)
        check(!mutable.value.saving && !mutable.value.checkpointPending)
        check((!mutable.value.unsavedChanges && mutable.value.itemInputs.isEmpty()) || discardConfirmed) { "Confirm discard of unsaved edits" }
        val token = ++sequence
        readJob?.cancel()
        mutable.value = CostBoqUiState(projectId = projectId, method = mutable.value.method, loading = true)
        readJob = viewModelScope.launch {
            try {
                val recovery=withContext(Dispatchers.IO){ checkpointMutex.withLock { checkpoints.load(projectId) } }
                val saved = repository.loadCurrent(projectId)
                if(token==sequence) {
                    if(recovery==null) mutable.value=mutable.value.copy(loading=false,saved=saved,draft=saved)
                    else mutable.value=mutable.value.copy(loading=false,method=recovery.method,saved=recovery.base,
                        draft=recovery.candidate,itemInputs=recovery.inputs,unsavedChanges=recovery.candidate!=recovery.base,
                        checkpointAcknowledged=true,currentHeadAdvanced=saved?.revision!=recovery.base?.revision)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == sequence) mutable.value = mutable.value.copy(loading = false, recoveryBlocked=true, errorKey = "cost_boq_recovery_failed") }
        }
    }

    fun chooseMethod(method: BoqWorkMethod) {
        check(!mutable.value.saving && mutable.value.itemInputs.isEmpty()) { "Apply or discard pending item text before switching method" }
        mutable.value = mutable.value.copy(method = method)
        checkpoint()
    }

    /** Candidate already has source scope, strict payload and revision identity from editor boundary. */
    fun edit(candidate: SavedCostBoq) {
        val current = mutable.value
        check(!current.loading && !current.saving && !current.recoveryBlocked && !current.currentHeadAdvanced && candidate.revision.projectId == current.projectId)
        mutable.value = current.copy(draft = candidate.copy(items = candidate.items.toList()), unsavedChanges = true, errorKey = null)
        checkpoint()
    }

    fun canLeave():Boolean = !mutable.value.recoveryBlocked && !mutable.value.currentHeadAdvanced && !mutable.value.saving && !mutable.value.checkpointPending && !mutable.value.unsavedChanges && mutable.value.itemInputs.isEmpty()

    fun discardPendingItemText() { check(!mutable.value.saving); mutable.value=mutable.value.copy(itemInputs=emptyMap());checkpoint() }

    fun changeItemInput(itemId:String,input:CostBoqItemInput) {
        val current=mutable.value
        if(current.loading || current.saving || current.recoveryBlocked || current.currentHeadAdvanced) return
        val item=current.draft?.items?.find { it.itemId==itemId } ?: return
        mutable.value=current.copy(itemInputs=if(input==CostBoqItemInput.from(item)) current.itemInputs-itemId else current.itemInputs+(itemId to input))
        checkpoint()
    }

    fun applyItemInput(itemId:String) {
        val input=mutable.value.itemInputs[itemId] ?: return
        editItem(itemId,input.number,input.description,input.unit,input.quantity,input.rate)
        val current=mutable.value
        val item=current.draft?.items?.find { it.itemId==itemId } ?: return
        if(input==CostBoqItemInput.from(item)) { mutable.value=current.copy(itemInputs=current.itemInputs-itemId);checkpoint() }
    }

    fun addItem() {
        val current = mutable.value
        if (current.loading || current.saving || current.recoveryBlocked || current.currentHeadAdvanced) return
        val projectId = current.projectId ?: return
        try {
            edit(com.civileg.app.data.repository.CostBoqDraftEditor.add(projectId,current.saved,current.draft,System.currentTimeMillis()))
        } catch (_: Exception) { mutable.value = mutable.value.copy(errorKey = "cost_boq_invalid_item") }
    }

    fun editItem(itemId:String,number:String,description:String,unit:String,quantity:String,rate:String) {
        val current = mutable.value
        if (current.loading || current.saving || current.recoveryBlocked || current.currentHeadAdvanced) return
        val projectId = current.projectId ?: return
        val draft = current.draft ?: return
        try {
            edit(com.civileg.app.data.repository.CostBoqDraftEditor.edit(projectId,current.saved,draft,itemId,
                number,description,unit,quantity,rate,System.currentTimeMillis()))
        } catch (_: Exception) { mutable.value = mutable.value.copy(errorKey = "cost_boq_invalid_item") }
    }

    fun confirmManualQuantity(itemId:String) {
        val current=mutable.value
        if(current.loading || current.saving || current.itemInputs.isNotEmpty()) return
        val project=current.projectId ?: return
        val draft=current.draft ?: return
        try { edit(com.civileg.app.data.repository.CostBoqDraftEditor.confirmManualQuantity(project,current.saved,draft,itemId,System.currentTimeMillis())) }
        catch(_:Exception) { mutable.value=mutable.value.copy(errorKey="cost_boq_review_failed") }
    }

    fun save() {
        val current = mutable.value
        if (current.loading || current.saving || current.recoveryBlocked || current.currentHeadAdvanced || !current.unsavedChanges || current.itemInputs.isNotEmpty()) return
        val candidate = current.draft ?: return
        val token = sequence
        val expected = current.saved?.revision?.let { CostBoqHeadEntity(it.projectId,it.revisionId,it.contentHash) }
        mutable.value = current.copy(saving = true, errorKey = null)
        saveJob = viewModelScope.launch {
            try {
                val saved = repository.append(expected,candidate)
                // Durable-history success is separate from whether this revision is still current.
                if (token == sequence) {
                    mutable.value = mutable.value.copy(saving=false,saved=saved,draft=saved,unsavedChanges=false)
                    checkpoint()
                    try {
                        val head=repository.loadCurrent(saved.revision.projectId)
                        if(token==sequence) mutable.value=mutable.value.copy(currentHeadAdvanced=head?.revision!=saved.revision)
                    } catch(_:Exception) { if(token==sequence) mutable.value=mutable.value.copy(currentHeadAdvanced=true,errorKey="cost_boq_head_unverified") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (token == sequence) mutable.value = mutable.value.copy(saving = false, errorKey = "cost_boq_save_failed")
                // Draft remains available for retry. No success/history/export claim on a failed write.
            }
        }
    }

    private fun checkpoint() {
        val current=mutable.value
        val project=current.projectId ?: return
        if(current.loading || current.recoveryBlocked) return
        val token=sequence
        val generation=++checkpointGeneration
        val payload=CostBoqCheckpoint(projectId=project,method=current.method,base=current.saved,candidate=current.draft,inputs=current.itemInputs.toMap())
        mutable.value=current.copy(checkpointPending=true,checkpointAcknowledged=false)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { checkpointMutex.withLock { if(token==sequence && generation==checkpointGeneration) checkpoints.write(payload) } }
                if(token==sequence && generation==checkpointGeneration) mutable.value=mutable.value.copy(checkpointPending=false,checkpointAcknowledged=true)
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception) { if(token==sequence && generation==checkpointGeneration) mutable.value=mutable.value.copy(checkpointPending=false,checkpointAcknowledged=false,errorKey="cost_boq_checkpoint_failed") }
        }
    }

    /** Host calls only after explicit discard confirmation, or to reload after a visible head conflict. */
    /** Explicit leave choice: keep writes a checkpoint; discard deletes it. Neither commits Room history. */
    fun leaveEditor(keepDraft:Boolean,onLeft:()->Unit) {
        val current=mutable.value
        if(current.saving || current.loading) return
        val project=current.projectId ?: return onLeft()
        val token=++sequence
        val generation=++checkpointGeneration
        val payload=CostBoqCheckpoint(projectId=project,method=current.method,base=current.saved,candidate=current.draft,inputs=current.itemInputs.toMap())
        mutable.value=current.copy(loading=true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { checkpointMutex.withLock {
                    check(token==sequence && generation==checkpointGeneration && mutable.value.projectId==project) { "Superseded leave action" }
                    if(keepDraft) {
                        check(!current.recoveryBlocked) { "Cannot overwrite unreadable recovery state" }
                        checkpoints.write(payload)
                    } else checkpoints.delete(project)
                } }
                if(token==sequence) { mutable.value=CostBoqUiState(method=current.method);onLeft() }
            } catch(_:Exception) { if(token==sequence) mutable.value=mutable.value.copy(loading=false,errorKey="cost_boq_checkpoint_failed") }
        }
    }

    fun discardAndReload() {
        val current=mutable.value
        if(current.saving || current.loading) return
        val project=current.projectId ?: return
        val token=++sequence
        val generation=++checkpointGeneration
        mutable.value=current.copy(loading=true,checkpointPending=false,checkpointAcknowledged=false)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){ checkpointMutex.withLock {
                    check(token==sequence && generation==checkpointGeneration) { "Superseded discard" }
                    checkpoints.delete(project)
                } }
                if(token==sequence) openProject(project,discardConfirmed=true)
            } catch(_:Exception) { if(token==sequence) mutable.value=mutable.value.copy(loading=false,errorKey="cost_boq_checkpoint_failed") }
        }
    }

    /** Project deletion in another screen invalidates async replies and cancels pending work. */
    fun projectRemoved(projectId: Long) {
        if (mutable.value.projectId != projectId) return
        val token=++sequence
        ++checkpointGeneration
        viewModelScope.launch {
            try { withContext(Dispatchers.IO){checkpointMutex.withLock {if(token==sequence && mutable.value.projectId==null) checkpoints.delete(projectId)}} }
            catch(_:Exception){ if(token==sequence && mutable.value.projectId==null) mutable.value=mutable.value.copy(errorKey="cost_boq_checkpoint_cleanup_failed") }
        }
        readJob?.cancel(); saveJob?.cancel()
        mutable.value = CostBoqUiState(method = mutable.value.method, errorKey = "cost_boq_project_removed")
    }
}
