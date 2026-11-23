package com.civileg.app.viewmodel

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.DesignType
import com.civileg.app.domain.calculations.ConcreteFrameDesign
import com.civileg.app.domain.calculations.FrameAnalysisEngine
import com.civileg.app.domain.calculations.SteelFrameDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.modeler.FrameNodeMoveValidation
import com.civileg.app.domain.modeler.FrameMutationResult
import com.civileg.app.domain.modeler.FrameMutationValidation
import com.civileg.app.domain.modeler.FrameEditCandidate
import com.civileg.app.domain.modeler.FrameValidationCode
import com.civileg.app.domain.modeler.FrameModelValidator
import com.civileg.app.domain.modeler.FrameSceneView
import com.civileg.app.domain.modeler.ModelerClipboard
import com.civileg.app.domain.modeler.FrameValidationIssue
import com.civileg.app.domain.modeler.ModelerLayerVisibility
import com.civileg.app.domain.modeler.SlabLoadDistributor
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import com.civileg.app.domain.calculations.InputGuard

@HiltViewModel
class FrameAnalysisViewModel @Inject constructor(
    application: Application,
    private val designRepository: DesignRepository
) : AndroidViewModel(application) {

    // === Frame Data (mutable state) ===
    private val _nodes = MutableLiveData<List<FrameNode>>(emptyList())
    private val _members = MutableLiveData<List<FrameMember>>(emptyList())
    private val _nodalLoads = MutableLiveData<List<NodalLoad>>(emptyList())
    private val _memberLoads = MutableLiveData<List<MemberLoad>>(emptyList())
    private val _slabs = MutableLiveData<List<SlabPanel>>(emptyList())
    private val _dimensions = MutableLiveData<List<Dimension>>(emptyList())
    private val _settings = MutableLiveData(FrameAnalysisSettings())
    private val _steelFy = MutableLiveData(355.0)

    // === Multi-selection state ===
    private val _selectedNodeIds = MutableLiveData<Set<Int>>(emptySet())
    private val _selectedMemberIds = MutableLiveData<Set<Int>>(emptySet())

    val nodes: LiveData<List<FrameNode>> get() = _nodes
    val members: LiveData<List<FrameMember>> get() = _members
    val nodalLoads: LiveData<List<NodalLoad>> get() = _nodalLoads
    val memberLoads: LiveData<List<MemberLoad>> get() = _memberLoads
    val slabs: LiveData<List<SlabPanel>> get() = _slabs
    val dimensions: LiveData<List<Dimension>> get() = _dimensions
    val settings: LiveData<FrameAnalysisSettings> get() = _settings
    val steelFy: LiveData<Double> get() = _steelFy
    val selectedNodeIds: LiveData<Set<Int>> get() = _selectedNodeIds
    val selectedMemberIds: LiveData<Set<Int>> get() = _selectedMemberIds

    // === Undo / Redo ===
    private data class ModelSnapshot(
        val nodes: List<FrameNode>,
        val members: List<FrameMember>,
        val nodalLoads: List<NodalLoad>,
        val memberLoads: List<MemberLoad>,
        val slabs: List<SlabPanel>,
        val dimensions: List<Dimension>,
        val selectedNodeIds: Set<Int>,
        val selectedMemberIds: Set<Int>
    )

    private val undoStack = ArrayDeque<ModelSnapshot>()
    private val redoStack = ArrayDeque<ModelSnapshot>()
    private var nodeDragSnapshot: ModelSnapshot? = null
    private var nodeDragValidation: Pair<Int,FrameNodeMoveValidation>? = null
    private var restoringHistory = false
    private val _historyState = MutableLiveData(0)

    /** Increments whenever undo/redo availability changes; UI can observe it. */
    val historyState: LiveData<Int> get() = _historyState

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun snapshot() = ModelSnapshot(
        _nodes.value ?: emptyList(),
        _members.value ?: emptyList(),
        _nodalLoads.value ?: emptyList(),
        _memberLoads.value ?: emptyList(),
        _slabs.value ?: emptyList(),
        _dimensions.value ?: emptyList(),
        _selectedNodeIds.value ?: emptySet(),
        _selectedMemberIds.value ?: emptySet()
    )

    private fun recordUndo() {
        if (restoringHistory) return
        undoStack.addLast(snapshot())
        while (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        _historyState.value = (_historyState.value ?: 0) + 1
        invalidateResults()
    }

    private fun invalidateResults() {
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _errorMessage.value = null
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(previous)
        _historyState.value = (_historyState.value ?: 0) + 1
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next)
        _historyState.value = (_historyState.value ?: 0) + 1
    }

    private fun restore(s: ModelSnapshot) {
        restoringHistory = true
        _nodes.value = s.nodes
        _members.value = s.members
        _nodalLoads.value = s.nodalLoads
        _memberLoads.value = s.memberLoads
        _slabs.value = s.slabs
        _dimensions.value = s.dimensions
        _selectedNodeIds.value = s.selectedNodeIds
        _selectedMemberIds.value = s.selectedMemberIds
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _selectedMemberId.value = null
        restoringHistory = false
    }

    fun beginNodeDrag() {
        if (nodeDragSnapshot == null) {
            nodeDragSnapshot = snapshot()
            nodeDragValidation = null
        }
    }

    fun dragNode(nodeId: Int, x: Double, y: Double) {
        val current=_nodes.value.orEmpty()
        val index=current.indexOfFirst { it.id==nodeId }
        if(index<0) return
        val checker=nodeDragValidation?.takeIf { it.first==nodeId }?.second
            ?: FrameNodeMoveValidation(editCandidate(),nodeId).also { nodeDragValidation=nodeId to it }
        val issues=checker.validate(x,y)
        if(issues.isNotEmpty()) { _validationIssues.value=issues;return }
        val node=current[index]
        if(node.x==x && node.y==y) return
        _nodes.value=current.toMutableList().also { it[index]=node.copy(x=x,y=y) }
        _validationIssues.value=emptyList()
        invalidateResults()
    }

    fun endNodeDrag() {
        val before = nodeDragSnapshot ?: return
        nodeDragSnapshot = null
        nodeDragValidation = null
        if (before.nodes != snapshot().nodes) {
            undoStack.addLast(before)
            while (undoStack.size > 60) undoStack.removeFirst()
            redoStack.clear()
            _historyState.value = (_historyState.value ?: 0) + 1
        }
    }

    fun cancelNodeDrag() {
        val before = nodeDragSnapshot ?: return
        nodeDragSnapshot = null
        nodeDragValidation = null
        restore(before)
    }

    // === Results ===
    private val _result = MutableLiveData<FrameAnalysisResult?>()
    val result: LiveData<FrameAnalysisResult?> get() = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _activeDiagramType = MutableLiveData(DiagramType.BMD)
    val activeDiagramType: LiveData<DiagramType> get() = _activeDiagramType

    private val _selectedMemberId = MutableLiveData<Int?>(null)
    val selectedMemberId: LiveData<Int?> get() = _selectedMemberId

    @Volatile
    private var modelerSceneView: FrameSceneView? = null

    fun updateModelerSceneView(
        scale: Float,
        panX: Float,
        panY: Float,
        width: Int,
        height: Int,
        gridMm: Int,
        layers: ModelerLayerVisibility = ModelerLayerVisibility()
    ) {
        val view = FrameSceneView(scale, panX, panY, width, height, gridMm, layers)
        if (view.isValid) modelerSceneView = view
    }

    // FIX: Persist drawing view mode across tab switches (was previously local to DrawingTab,
    // causing it to reset to Frame view every time user navigated away and back).
    private val _drawingViewMode = MutableLiveData(0)  // 0=Frame, 1=Long.Section, 2=Cross Section, 3=Plan
    val drawingViewMode: LiveData<Int> get() = _drawingViewMode

    fun setDrawingViewMode(mode: Int) {
        _drawingViewMode.value = mode.coerceIn(0, 3)
    }

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    private val _validationIssues = MutableLiveData<List<FrameValidationIssue>>(emptyList())
    val validationIssues: LiveData<List<FrameValidationIssue>> get() = _validationIssues

    fun clearValidationIssues() {
        _validationIssues.value = emptyList()
    }

    // === Multi-selection Methods ===
    fun selectNode(nodeId: Int, addToSelection: Boolean = false) {
        if (addToSelection) {
            _selectedNodeIds.value = (_selectedNodeIds.value ?: emptySet()) + nodeId
        } else {
            _selectedNodeIds.value = setOf(nodeId)
        }
        _selectedMemberIds.value = emptySet()
    }

    fun selectMember(memberId: Int, addToSelection: Boolean = false) {
        if (addToSelection) {
            _selectedMemberIds.value = (_selectedMemberIds.value ?: emptySet()) + memberId
        } else {
            _selectedMemberIds.value = setOf(memberId)
        }
        _selectedNodeIds.value = emptySet()
    }

    fun toggleNodeSelection(nodeId: Int) {
        _selectedNodeIds.value = if ((_selectedNodeIds.value ?: emptySet()).contains(nodeId)) {
            (_selectedNodeIds.value ?: emptySet()) - nodeId
        } else {
            (_selectedNodeIds.value ?: emptySet()) + nodeId
        }
        _selectedMemberIds.value = emptySet()
    }

    fun toggleMemberSelection(memberId: Int) {
        _selectedMemberIds.value = if ((_selectedMemberIds.value ?: emptySet()).contains(memberId)) {
            (_selectedMemberIds.value ?: emptySet()) - memberId
        } else {
            (_selectedMemberIds.value ?: emptySet()) + memberId
        }
        _selectedNodeIds.value = emptySet()
    }

    fun clearSelection() {
        _selectedNodeIds.value = emptySet()
        _selectedMemberIds.value = emptySet()
    }

    fun selectAllNodes() {
        val allIds = _nodes.value?.let { it.map { it.id }.toSet() } ?: emptySet()
        _selectedNodeIds.value = allIds
        _selectedMemberIds.value = emptySet()
    }

    fun selectAllMembers() {
        val allIds = _members.value?.let { it.map { it.id }.toSet() } ?: emptySet()
        _selectedMemberIds.value = allIds
        _selectedNodeIds.value = emptySet()
    }

    fun selectAll() {
        selectAllNodes()
        val allMemberIds = _members.value?.let { it.map { it.id }.toSet() } ?: emptySet()
        _selectedMemberIds.value = allMemberIds
    }

    fun getSelectedNodes(): List<FrameNode> {
        val ids = _selectedNodeIds.value ?: emptySet()
        return _nodes.value?.filter { it.id in ids } ?: emptyList()
    }

    fun getSelectedMembers(): List<FrameMember> {
        val ids = _selectedMemberIds.value ?: emptySet()
        return _members.value?.filter { it.id in ids } ?: emptyList()
    }

    fun setSelection(nodeIds: Set<Int>, memberIds: Set<Int>) {
        _selectedNodeIds.value = nodeIds
        _selectedMemberIds.value = memberIds
    }

    // === Clipboard for Copy/Paste ===
    private var clipboard: ModelerClipboard.Content? = null
    private var pasteCount = 0

    fun copySelection() {
        val content = ModelerClipboard.capture(
            nodes = _nodes.value.orEmpty(),
            members = _members.value.orEmpty(),
            nodalLoads = _nodalLoads.value.orEmpty(),
            memberLoads = _memberLoads.value.orEmpty(),
            slabs = _slabs.value.orEmpty(),
            selectedNodeIds = _selectedNodeIds.value.orEmpty(),
            selectedMemberIds = _selectedMemberIds.value.orEmpty()
        )
        // A copy with nothing selected must not silently wipe a previous clipboard.
        if (!content.isEmpty) {
            clipboard = content
            pasteCount = 0
        }
    }

    fun pasteClipboard(): Boolean {
        val content = clipboard ?: return false
        val before = editCandidate()
        val offset = PASTE_OFFSET_METERS * (pasteCount.toLong()+1L)
        val result = try { ModelerClipboard.paste(content,before.nodes,before.members,before.slabs,offset) }
            catch (_: IllegalArgumentException) { rejectEdit<Unit>("تعذر لصق عناصر غير صالحة. لم يتغير النموذج.", "Cannot paste invalid items. The model was not changed."); return false }
        if (result.isEmpty) return false
        // Do not accept a helper dropping broken references from a partial paste.
        if (result.nodes.size != content.nodes.size || result.members.size != content.members.size ||
            result.nodalLoads.size != content.nodalLoads.size || result.memberLoads.size != content.memberLoads.size || result.slabs.size != content.slabs.size) {
            rejectEdit<Unit>("الحافظة تحتوي على مراجع ناقصة. أعد نسخ العناصر المتصلة. لم يتغير النموذج.", "The clipboard has missing references. Copy connected items again. The model was not changed.")
            return false
        }
        val applied = commitEdit(before.copy(nodes=before.nodes+result.nodes,members=before.members+result.members,
            nodalLoads=before.nodalLoads+result.nodalLoads,memberLoads=before.memberLoads+result.memberLoads,slabs=before.slabs+result.slabs),Unit)
        if (applied !is FrameMutationResult.Applied) return false
        _selectedNodeIds.value=result.nodes.map { it.id }.toSet()
        _selectedMemberIds.value=result.members.map { it.id }.toSet()
        pasteCount++
        return true
    }


    fun hasClipboard(): Boolean = clipboard != null

    private val _designNotice = MutableLiveData<FrameDesignNotice?>()
    val designNotice: LiveData<FrameDesignNotice?> get() = _designNotice

    private val _modelLoadVersion = MutableLiveData(0)
    val modelLoadVersion: LiveData<Int> get() = _modelLoadVersion

    fun clearDesignNotice() {
        _designNotice.value = null
    }

    // === Design Results (combined) ===
    private val _concreteResults = MutableLiveData<List<ConcreteMemberDesignResult>>()
    val concreteResults: LiveData<List<ConcreteMemberDesignResult>> get() = _concreteResults

    private val _steelResults = MutableLiveData<List<SteelMemberDesignResult>>()
    val steelResults: LiveData<List<SteelMemberDesignResult>> get() = _steelResults

    // Computed: has any concrete members?
    val hasConcreteMembers: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(_members) { value = it.any { m -> m.materialType == FrameMaterialType.Concrete } }
    }

    val hasSteelMembers: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(_members) { value = it.any { m -> m.materialType == FrameMaterialType.Steel } }
    }

    private val _savedDesignId = MutableLiveData<Long?>(null)
    val savedDesignId: LiveData<Long?> get() = _savedDesignId

    private val draftPrefs =
        application.getSharedPreferences("frame_modeler_draft", Context.MODE_PRIVATE)
    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { persistDraft() }

    private fun scheduleAutosave() {
        autosaveHandler.removeCallbacks(autosaveRunnable)
        autosaveHandler.postDelayed(autosaveRunnable, 600)
    }

    private val draftStore = FrameDraftStore(object : DraftPrefs {
        override fun get(key: String): String? = draftPrefs.getString(key, null)
        override fun keysWithPrefix(prefix: String): Set<String> =
            draftPrefs.all.keys.filter { it.startsWith(prefix) }.toSet()
        override fun commit(updates: Map<String, String>): Boolean {
            val e = draftPrefs.edit()
            updates.forEach { (k, v) -> e.putString(k, v) }
            return e.commit()
        }
    })

    /** True while the last autosave attempt failed or could not be verified. */
    private val _draftSaveProblem = MutableLiveData(false)
    val draftSaveProblem: LiveData<Boolean> get() = _draftSaveProblem

    /** Non-null while a stored draft was not restored; editing is gated by the screen. */
    private val _draftRecovery = MutableLiveData<DraftRecoveryUi?>(null)
    val draftRecovery: LiveData<DraftRecoveryUi?> get() = _draftRecovery

    /**
     * User-initiated: writes a NEW export file with every retained raw. Returns the file, or null
     * and flags a visible failure. Never modifies preferences.
     */
    fun exportRetainedRaw(dir: java.io.File): java.io.File? {
        val cur = _draftRecovery.value
        return try {
            val raws = draftStore.rawsForExport()
            if (raws.isEmpty()) throw java.io.IOException("nothing retained")
            val version = try {
                getApplication<Application>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName } ?: "unknown"
            } catch (e: Exception) { "unknown" }
            val now = System.currentTimeMillis()
            val f = FrameDraftRawExport.writeNew(dir, FrameDraftRawExport.build(raws, version, now), now, raws)
            if (cur != null) _draftRecovery.value = cur.copy(exportFailed = false)
            f
        } catch (e: java.io.IOException) {
            if (cur != null) _draftRecovery.value = cur.copy(exportFailed = true)
            null
        } catch (e: RuntimeException) {
            if (cur != null) _draftRecovery.value = cur.copy(exportFailed = true)
            null
        }
    }

    /** The share sheet could not be launched (any provider/security/activity reason). Raws stay retained. */
    fun reportExportShareFailed() {
        _draftRecovery.value?.let { _draftRecovery.value = it.copy(exportFailed = true) }
    }

    /** Explicit user choice. Failure keeps the gate and says why; originals are never deleted. */
    fun startNewDraft() {
        val blank = FrameDraftData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            FrameAnalysisSettings(), 355.0)
        when (draftStore.startNew(blank)) {
            FrameDraftStartNew.STARTED -> {
                // Memory now equals the durably written blank draft exactly, before the editor shows.
                restoringHistory = true
                _nodes.value = emptyList(); _members.value = emptyList()
                _nodalLoads.value = emptyList(); _memberLoads.value = emptyList()
                _slabs.value = emptyList(); _dimensions.value = emptyList()
                _settings.value = blank.settings; _steelFy.value = blank.steelFy
                _selectedNodeIds.value = emptySet(); _selectedMemberIds.value = emptySet()
                _selectedMemberId.value = null
                _result.value = null; _concreteResults.value = emptyList(); _steelResults.value = emptyList()
                _errorMessage.value = null
                restoringHistory = false
                undoStack.clear(); redoStack.clear()
                _historyState.value = (_historyState.value ?: 0) + 1
                _draftSaveProblem.value = false
                _draftRecovery.value = null
            }
            FrameDraftStartNew.RETENTION_FAILED ->
                _draftRecovery.value = DraftRecoveryUi(retained = false, startNewFailed = true)
            FrameDraftStartNew.WRITE_FAILED ->
                _draftRecovery.value = DraftRecoveryUi(retained = true, startNewFailed = true)
        }
    }

    private fun persistDraft() {
        try {
            val draft = FrameDraftData(
                nodes = _nodes.value.orEmpty(),
                members = _members.value.orEmpty(),
                nodalLoads = _nodalLoads.value.orEmpty(),
                memberLoads = _memberLoads.value.orEmpty(),
                slabs = _slabs.value.orEmpty(),
                settings = _settings.value ?: FrameAnalysisSettings(),
                steelFy = _steelFy.value ?: 355.0
            )
            // Blocked while recovery is pending; never touches v1 or quarantine copies.
            val r = draftStore.save(draft)
            if (r != FrameDraftSave.BLOCKED_RECOVERY_PENDING) {
                val failed = r == FrameDraftSave.WRITE_FAILED || r == FrameDraftSave.SKIPPED_NOT_ROUNDTRIPPABLE
                if (failed != (_draftSaveProblem.value == true)) _draftSaveProblem.postValue(failed)
            }
        } catch (_: RuntimeException) {
            _draftSaveProblem.postValue(true)
        }
    }

    private fun restoreDraft(): Boolean {
        return when (val r = draftStore.load()) {
            FrameDraftStartup.Blank -> false
            is FrameDraftStartup.RecoveryPending -> {
                _draftRecovery.value = DraftRecoveryUi(retained = r.quarantineRetained, startNewFailed = false)
                false
            }
            is FrameDraftStartup.Restored -> {
                val draft = r.data
                _nodes.value = draft.nodes
                _members.value = draft.members
                _nodalLoads.value = draft.nodalLoads
                _memberLoads.value = draft.memberLoads
                _slabs.value = draft.slabs
                _settings.value = draft.settings
                _steelFy.value = draft.steelFy
                _historyState.value = (_historyState.value ?: 0) + 1
                if (!r.migrationPersisted) _draftSaveProblem.value = true
                if (draft.warnings.isNotEmpty()) {
                    _errorMessage.value = "Draft restored as stored, but: " + draft.warnings.joinToString("; ") +
                        ". Check the model before analysing."
                }
                true
            }
        }
    }

    private val nodesAutosaveObserver = Observer<List<FrameNode>> { scheduleAutosave() }
    private val membersAutosaveObserver = Observer<List<FrameMember>> { scheduleAutosave() }
    private val nodalLoadsAutosaveObserver = Observer<List<NodalLoad>> { scheduleAutosave() }
    private val memberLoadsAutosaveObserver = Observer<List<MemberLoad>> { scheduleAutosave() }
    private val slabsAutosaveObserver = Observer<List<SlabPanel>> { scheduleAutosave() }
    private val settingsAutosaveObserver = Observer<FrameAnalysisSettings> { scheduleAutosave() }
    private val steelFyAutosaveObserver = Observer<Double> { scheduleAutosave() }

    init {
        // Start with a blank drawing page; templates remain available from the toolbar.
        restoreDraft()
        _nodes.observeForever(nodesAutosaveObserver)
        _members.observeForever(membersAutosaveObserver)
        _nodalLoads.observeForever(nodalLoadsAutosaveObserver)
        _memberLoads.observeForever(memberLoadsAutosaveObserver)
        _slabs.observeForever(slabsAutosaveObserver)
        _settings.observeForever(settingsAutosaveObserver)
        _steelFy.observeForever(steelFyAutosaveObserver)
        scheduleAutosave()
    }

    override fun onCleared() {
        autosaveHandler.removeCallbacks(autosaveRunnable)
        persistDraft()
        _nodes.removeObserver(nodesAutosaveObserver)
        _members.removeObserver(membersAutosaveObserver)
        _nodalLoads.removeObserver(nodalLoadsAutosaveObserver)
        _memberLoads.removeObserver(memberLoadsAutosaveObserver)
        _slabs.removeObserver(slabsAutosaveObserver)
        _settings.removeObserver(settingsAutosaveObserver)
        _steelFy.removeObserver(steelFyAutosaveObserver)
        super.onCleared()
    }

    // ========================================================================
    // Node Management
    // ========================================================================

    private fun editCandidate() = FrameEditCandidate(_nodes.value.orEmpty(), _members.value.orEmpty(),
        _nodalLoads.value.orEmpty(), _memberLoads.value.orEmpty(), _slabs.value.orEmpty())

    private fun <T> rejectEdit(ar: String, en: String): FrameMutationResult<T> {
        val issues = listOf(FrameValidationIssue(FrameValidationCode.INVALID_NUMBER, ar, en))
        _validationIssues.value = issues
        return FrameMutationResult.Rejected(issues)
    }

    /** Validate the WHOLE proposed batch before undo, mutation, autosave observers or invalidation. */
    private fun <T> commitEdit(next: FrameEditCandidate, value: T): FrameMutationResult<T> {
        val before = editCandidate()
        if (before == next) return FrameMutationResult.Unchanged
        val issues = FrameMutationValidation.introduced(before, next)
        if (issues.isNotEmpty()) { _validationIssues.value = issues; return FrameMutationResult.Rejected(issues) }
        recordUndo()
        if (next.nodes != before.nodes) _nodes.value = next.nodes
        if (next.members != before.members) _members.value = next.members
        if (next.nodalLoads != before.nodalLoads) _nodalLoads.value = next.nodalLoads
        if (next.memberLoads != before.memberLoads) _memberLoads.value = next.memberLoads
        if (next.slabs != before.slabs) _slabs.value = next.slabs
        _validationIssues.value = emptyList()
        regenerateDimensions()
        return FrameMutationResult.Applied(value)
    }

    fun addNode(x: Double, y: Double, support: SupportType = SupportType.Free): FrameMutationResult<Int> {
        val before = editCandidate()
        val max = before.nodes.maxOfOrNull { it.id } ?: 0
        if (max == Int.MAX_VALUE) return rejectEdit("أرقام العقد ممتلئة. لم تتم الإضافة.", "Joint IDs are exhausted. No joint was added.")
        val node = FrameNode(max + 1, x, y, support)
        return commitEdit(before.copy(nodes = before.nodes + node), node.id)
    }


    fun updateNode(nodeId: Int, x: Double, y: Double, support: SupportType): FrameMutationResult<Unit> {
        val before = editCandidate()
        if (before.nodes.none { it.id == nodeId }) return rejectEdit("العقدة $nodeId غير موجودة. اختر عقدة من القائمة.", "Joint $nodeId does not exist. Select one from the joints list.")
        return commitEdit(before.copy(nodes = before.nodes.map { if(it.id == nodeId) it.copy(x=x,y=y,support=support) else it }), Unit)
    }


    fun setNodeSupport(nodeId: Int, support: SupportType) {
        val node = _nodes.value?.find { it.id == nodeId } ?: return
        updateNode(nodeId, node.x, node.y, support)
    }

    fun removeNode(nodeId: Int) {
        if (_nodes.value.orEmpty().none { it.id == nodeId }) return
        recordUndo()
        val removedMemberIds = _members.value.orEmpty()
            .filter { it.nodeI == nodeId || it.nodeJ == nodeId }
            .map { it.id }
            .toSet()
        _nodes.value = _nodes.value?.filter { it.id != nodeId }
        // Remove connected members, their loads, the node load and any slab using the node.
        _members.value = _members.value?.filter { it.nodeI != nodeId && it.nodeJ != nodeId }
        _memberLoads.value = _memberLoads.value?.filter { it.memberId !in removedMemberIds }
        _nodalLoads.value = _nodalLoads.value?.filter { it.nodeId != nodeId }
        _slabs.value = _slabs.value?.filter { !it.nodeIds.contains(nodeId) }
        _selectedNodeIds.value = _selectedNodeIds.value.orEmpty() - nodeId
        _selectedMemberIds.value = _selectedMemberIds.value.orEmpty() - removedMemberIds
        regenerateDimensions()
    }

    // ========================================================================
    // Member Management
    // ========================================================================

    fun addMember(nodeI: Int, nodeJ: Int, materialType: FrameMaterialType, memberType: FrameMemberType,
        concreteSection: ConcreteSectionProps? = null, steelSectionName: String? = null, name: String = ""): FrameMutationResult<Int> {
        val before = editCandidate()
        val max = before.members.maxOfOrNull { it.id } ?: 0
        if (max == Int.MAX_VALUE) return rejectEdit("أرقام الأعضاء ممتلئة. لم تتم الإضافة.", "Member IDs are exhausted. No member was added.")
        val member = FrameMember(max+1,nodeI,nodeJ,materialType,memberType,concreteSection,steelSectionName,name)
        return commitEdit(before.copy(members = before.members + member), member.id)
    }


    fun addMemberBetween(nodeI: Int, nodeJ: Int, memberType: FrameMemberType = FrameMemberType.Beam): FrameMutationResult<Int> =
        addMember(nodeI,nodeJ,FrameMaterialType.Concrete,memberType,
            ConcreteSectionProps(if(memberType == FrameMemberType.Column) 300.0 else 250.0,
                if(memberType == FrameMemberType.Column) 300.0 else 500.0))


    fun createMemberFromDrag(startId: Int, endId: Int?, x: Double, y: Double): FrameMutationResult<Int> {
        val before = editCandidate()
        val start = before.nodes.find { it.id == startId }
            ?: return rejectEdit("عقدة البداية غير موجودة. اخترها مرة أخرى.", "The start joint no longer exists. Select it again.")
        val nodeMax = before.nodes.maxOfOrNull { it.id } ?: 0
        val memberMax = before.members.maxOfOrNull { it.id } ?: 0
        if (memberMax == Int.MAX_VALUE || (endId == null && nodeMax == Int.MAX_VALUE))
            return rejectEdit("تعذر تخصيص رقم جديد. لم يتغير النموذج.", "A new ID could not be allocated. The model was not changed.")
        val end = if(endId == null) FrameNode(nodeMax+1,x,y) else before.nodes.find { it.id == endId }
            ?: return rejectEdit("عقدة النهاية غير موجودة. اخترها مرة أخرى.", "The end joint no longer exists. Select it again.")
        val type = if(kotlin.math.abs(start.x-end.x) < 1e-6) FrameMemberType.Column else FrameMemberType.Beam
        val member = FrameMember(memberMax+1,start.id,end.id,memberType=type,concreteSection=ConcreteSectionProps(
            if(type == FrameMemberType.Column) 300.0 else 250.0,if(type == FrameMemberType.Column) 300.0 else 500.0))
        return commitEdit(before.copy(nodes = if(endId == null) before.nodes+end else before.nodes,members=before.members+member),member.id)
    }

    fun updateMember(member: FrameMember): FrameMutationResult<Unit> {
        val before = editCandidate()
        if (before.members.none { it.id == member.id }) return rejectEdit("العنصر غير موجود. أعد اختياره من النموذج.", "This item no longer exists. Select it again from the model.")
        return commitEdit(before.copy(members = before.members.map { if(it.id == member.id) member else it }), Unit)
    }


    fun updateMemberReleases(memberId: Int, releaseI: Boolean, releaseJ: Boolean) {
        val current = _members.value?.toMutableList() ?: return
        val index = current.indexOfFirst { it.id == memberId }
        if (index < 0) return
        val updated = current[index].copy(releaseI = releaseI, releaseJ = releaseJ)
        if (updated == current[index]) return
        recordUndo()
        current[index] = updated
        _members.value = current
    }

    fun removeMember(memberId: Int) {
        if (_members.value.orEmpty().none { it.id == memberId }) return
        recordUndo()
        _members.value = _members.value?.filter { it.id != memberId }
        _memberLoads.value = _memberLoads.value?.filter { it.memberId != memberId }
        _selectedMemberIds.value = _selectedMemberIds.value.orEmpty() - memberId
        regenerateDimensions()
    }

    private fun regenerateDimensions() {
        val members = _members.value ?: emptyList()
        val dims = mutableListOf<Dimension>()
        var dimId = 1
        for (member in members) {
            dims.add(Dimension(
                id = dimId++,
                point1Id = member.nodeI,
                point2Id = member.nodeJ,
                offsetDistance = 0.5,
                dimensionType = DimensionType.Aligned
            ))
        }
        _dimensions.value = dims
    }

    // ========================================================================
    // Load Management
    // ========================================================================

    fun addNodalLoad(nodeId: Int, fx: Double, fy: Double, mz: Double, loadCase: String): FrameMutationResult<Unit> {
        val before = editCandidate()
        return commitEdit(before.copy(nodalLoads = before.nodalLoads + NodalLoad(nodeId,fx,fy,mz,loadCase)), Unit)
    }


    fun removeNodalLoad(index: Int) {
        val current = _nodalLoads.value ?: return
        if (index !in current.indices) return
        recordUndo()
        _nodalLoads.value = current.filterIndexed { i, _ -> i != index }
    }

    fun updateNodalLoad(index: Int, load: NodalLoad): FrameMutationResult<Unit> {
        val before = editCandidate()
        if (index !in before.nodalLoads.indices) return rejectEdit("الحمل غير موجود. أعد اختياره من القائمة.", "This load no longer exists. Select it again from the list.")
        return commitEdit(before.copy(nodalLoads = before.nodalLoads.mapIndexed { i, old -> if(i == index) load else old }), Unit)
    }


    fun addMemberLoad(memberId: Int, loadType: MemberLoadType, value: Double, position: Double, loadCase: String): FrameMutationResult<Unit> {
        val before = editCandidate()
        return commitEdit(before.copy(memberLoads = before.memberLoads + MemberLoad(memberId,loadType,value,position,loadCase)), Unit)
    }


    fun removeMemberLoad(index: Int) {
        val current = _memberLoads.value ?: return
        if (index !in current.indices) return
        recordUndo()
        _memberLoads.value = current.filterIndexed { i, _ -> i != index }
    }

    fun updateMemberLoad(index: Int, load: MemberLoad): FrameMutationResult<Unit> {
        val before = editCandidate()
        if (index !in before.memberLoads.indices) return rejectEdit("الحمل غير موجود. أعد اختياره من القائمة.", "This load no longer exists. Select it again from the list.")
        return commitEdit(before.copy(memberLoads = before.memberLoads.mapIndexed { i, old -> if(i == index) load else old }), Unit)
    }


    // ========================================================================
    // Slab Panel Management
    // ========================================================================

    fun addSlabPanel(nodeIds: List<Int>, loadKNm2: Double, loadCase: String = "DL", thicknessMm: Double = 200.0,
        distribution: SlabDistribution = SlabDistribution.TwoWay): FrameMutationResult<Int> {
        val before = editCandidate()
        val max = before.slabs.maxOfOrNull { it.id } ?: 0
        if (max == Int.MAX_VALUE) return rejectEdit("أرقام البلاطات ممتلئة. لم تتم الإضافة.", "Slab IDs are exhausted. No panel was added.")
        val panel = SlabPanel(id=max+1,nodeIds=nodeIds.toList(),thicknessMm=thicknessMm,loadKNm2=loadKNm2,loadCase=loadCase,distribution=distribution)
        return commitEdit(before.copy(slabs = before.slabs + panel), panel.id)
    }


    fun removeSlabPanel(panelId: Int) {
        if (_slabs.value.orEmpty().none { it.id == panelId }) return
        recordUndo()
        _slabs.value = _slabs.value?.filter { it.id != panelId }
    }

    fun updateSlabPanel(panel: SlabPanel): FrameMutationResult<Unit> {
        val before = editCandidate()
        if (before.slabs.none { it.id == panel.id }) return rejectEdit("العنصر غير موجود. أعد اختياره من النموذج.", "This item no longer exists. Select it again from the model.")
        return commitEdit(before.copy(slabs = before.slabs.map { if(it.id == panel.id) panel else it }), Unit)
    }


    fun slabMemberLoads(nodes: List<FrameNode>, members: List<FrameMember>): List<MemberLoad> {
        return _slabs.value.orEmpty().flatMap { panel ->
            SlabLoadDistributor.distribute(panel, nodes, members).memberLoads
        }
    }

    // ========================================================================
    // Settings
    // ========================================================================

    fun updateDesignCode(code: DesignCode) {
        val current = _settings.value
        val newDefaults = FrameAnalysisSettings.defaultLoadCombinations(code)
        _settings.value = when {
            current == null -> FrameAnalysisSettings(designCode = code, loadCombinations = newDefaults)
            // Swap in the code-appropriate combinations, preserving any user-customized set
            FrameAnalysisSettings.isDefaultComboSet(current.loadCombinations) ->
                current.copy(designCode = code, loadCombinations = newDefaults)
            else -> current.copy(designCode = code)
        }
    }

    fun updateSteelFy(fy: Double) {
        if (!fy.isFinite() || fy <= 0.0) { rejectEdit<Unit>("مقاومة الحديد fy: أدخل رقمًا موجبًا محدودًا بالميجاباسكال.", "Steel fy: enter a finite positive value in MPa."); return }
        _steelFy.value = fy
    }


    fun setDiagramType(type: DiagramType) {
        _activeDiagramType.value = type
    }

    fun setSelectedMember(memberId: Int?) {
        _selectedMemberId.value = memberId
    }

    // ========================================================================
    // Solve & Design
    // ========================================================================

    fun solveFrame() {
        val issues = FrameModelValidator.validate(
            nodes = _nodes.value.orEmpty(),
            members = _members.value.orEmpty(),
            nodalLoads = _nodalLoads.value.orEmpty(),
            memberLoads = _memberLoads.value.orEmpty(),
            slabs = _slabs.value.orEmpty()
        )
        if (issues.isNotEmpty()) {
            _validationIssues.value = issues
            return
        }
        _validationIssues.value = emptyList()
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val nodes = _nodes.value ?: emptyList()
                val members = _members.value ?: emptyList()
                val nodalLoads = _nodalLoads.value ?: emptyList()
                val memberLoads = _memberLoads.value ?: emptyList()
                val slabs = _slabs.value ?: emptyList()
                val settings = _settings.value ?: FrameAnalysisSettings()

                val slabLoads = slabs.flatMap { panel ->
                    SlabLoadDistributor.distribute(panel, nodes, members).memberLoads
                }

                // Run analysis
                val analysisResult = FrameAnalysisEngine.solveFrame(
                    nodes, members, nodalLoads, memberLoads + slabLoads, settings
                )

                withContext(Dispatchers.Main) {
                    if (!analysisResult.isSolved) {
                        _errorMessage.value = analysisResult.errorMessage
                        _result.value = analysisResult
                        _isLoading.value = false
                        return@withContext
                    }

                    // Run concrete design
                    val concreteMembers = members.filter { it.materialType == FrameMaterialType.Concrete }
                    val concreteDesignResults = if (concreteMembers.isNotEmpty()) {
                        ConcreteFrameDesign.designAllConcreteMembers(
                            members, analysisResult.memberEndForces, analysisResult.memberDiagrams, settings.designCode
                        )
                    } else emptyList()

                    // Run steel design
                    val steelMembers = members.filter { it.materialType == FrameMaterialType.Steel }
                    val steelDesignResults = if (steelMembers.isNotEmpty()) {
                        SteelFrameDesign.designAllSteelMembers(
                            members, analysisResult.memberEndForces, analysisResult.memberDiagrams,
                            settings.designCode, _steelFy.value ?: 355.0
                        )
                    } else emptyList()

                    _result.value = analysisResult.copy(
                        concreteDesignResults = concreteDesignResults,
                        steelDesignResults = steelDesignResults
                    )
                    _concreteResults.value = concreteDesignResults
                    _steelResults.value = steelDesignResults
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Frame analysis error: ${e.message ?: ""}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // ========================================================================
    // Templates
    // ========================================================================

    fun loadSimplePortalFrame(span: Double, height: Double, udl: Double) {
        InputGuard.positive("span", span)
        InputGuard.positive("height", height)
        InputGuard.nonNegative("udl", udl)

        clearAll()
        val (nodes, members, loads) = FrameAnalysisEngine.createSimplePortalFrame(span, height, udl)
        _nodes.value = nodes
        _members.value = members
        _memberLoads.value = loads
    }

    fun loadTwoStoryFrame(span: Double, height1: Double, height2: Double, udl: Double) {
        InputGuard.positive("span", span)
        InputGuard.positive("height1", height1)
        InputGuard.positive("height2", height2)
        InputGuard.nonNegative("udl", udl)

        clearAll()
        val (ns, ms, ls) = FrameAnalysisEngine.createMultiStoryFrame(
            listOf(span), listOf(height1, height2), udl
        )
        _nodes.value = ns
        _members.value = ms
        _memberLoads.value = ls
    }

    fun clearAll() {
        recordUndo()
        _nodes.value = emptyList()
        _members.value = emptyList()
        _nodalLoads.value = emptyList()
        _memberLoads.value = emptyList()
        _slabs.value = emptyList()
        _dimensions.value = emptyList()
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _errorMessage.value = null
        _selectedMemberId.value = null
        modelerSceneView = null
    }

    // ========================================================================
    // Save Frame Design to Database
    // ========================================================================

    fun saveFrameDesign(projectId: Long, name: String) {
        val res = _result.value ?: FrameAnalysisResult()
        val ns = _nodes.value ?: emptyList()
        val ms = _members.value ?: emptyList()
        val nl = _nodalLoads.value ?: emptyList()
        val ml = _memberLoads.value ?: emptyList()
        val slabs = _slabs.value ?: emptyList()
        val dims = _dimensions.value ?: emptyList()
        val st = _settings.value ?: FrameAnalysisSettings()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                designRepository.saveFrameAnalysisDesign(
                    projectId = projectId,
                    name = name,
                    nodes = ns,
                    members = ms,
                    nodalLoads = nl,
                    memberLoads = ml,
                    slabs = slabs,
                    dimensions = dims,
                    result = res,
                    designCode = st.designCode.displayName,
                    steelFy = _steelFy.value ?: 355.0
                )
                withContext(Dispatchers.Main) {
                    _errorMessage.value = null
                    _designNotice.value = FrameDesignNotice.SAVED
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to save frame design: ${e.message}"
                }
            }
        }
    }

    fun loadFrameDesign(designId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val design = designRepository.getDesignById(designId)
                    ?: throw IllegalStateException("Design not found")
                if (design.type != DesignType.FRAME_ANALYSIS) {
                    throw IllegalStateException("Not a frame design")
                }
                val data = JSONObject(design.inputData)
                val gson = Gson()
                fun <T> readList(key: String, type: Class<Array<T>>): List<T> {
                    val raw = data.optString(key, "[]")
                    return gson.fromJson(raw, type)?.toList().orEmpty()
                }
                val loadedNodes = readList("nodes", Array<FrameNode>::class.java)
                val loadedMembers = readList("members", Array<FrameMember>::class.java)
                val loadedNodalLoads = readList("nodalLoads", Array<NodalLoad>::class.java)
                val loadedMemberLoads = readList("memberLoads", Array<MemberLoad>::class.java)
                val loadedSlabs = readList("slabs", Array<SlabPanel>::class.java)
                val loadedDimensions = readList("dimensions", Array<Dimension>::class.java)
                if (loadedNodes.isEmpty()) {
                    throw IllegalStateException("Saved model has no joints")
                }
                val steelFy = data.optDouble("steelFy", _steelFy.value ?: 355.0)
                val code = DesignCode.entries.firstOrNull { it.displayName == design.codeUsed }
                    ?: (_settings.value?.designCode ?: DesignCode.ECP)
                withContext(Dispatchers.Main) {
                    _nodes.value = loadedNodes
                    _members.value = loadedMembers
                    _nodalLoads.value = loadedNodalLoads
                    _memberLoads.value = loadedMemberLoads
                    _slabs.value = loadedSlabs
                    _dimensions.value = loadedDimensions
                    _settings.value = (_settings.value ?: FrameAnalysisSettings()).copy(designCode = code)
                    _steelFy.value = steelFy
                    _result.value = null
                    _concreteResults.value = emptyList()
                    _steelResults.value = emptyList()
                    _selectedMemberId.value = null
                    modelerSceneView = null
                    undoStack.clear()
                    redoStack.clear()
                    _historyState.value = (_historyState.value ?: 0) + 1
                    _modelLoadVersion.value = (_modelLoadVersion.value ?: 0) + 1
                    _designNotice.value = FrameDesignNotice.LOADED
                    scheduleAutosave()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to open frame design: ${e.message}"
                }
            }
        }
    }

    // ========================================================================
    // PDF Export (export logic lives in Screen via FrameAnalysisPdfExporter)
    // ========================================================================

    // ========================================================================
    // Stored Inputs for PDF Export (used by Screen's FrameAnalysisPdfExporter)
    // ========================================================================

    data class FrameStoredInputs(
        val nodes: List<FrameNode>,
        val members: List<FrameMember>,
        val nodalLoads: List<NodalLoad>,
        val memberLoads: List<MemberLoad>,
        val slabs: List<SlabPanel>,
        val dimensions: List<Dimension>,
        val settings: FrameAnalysisSettings,
        val result: FrameAnalysisResult?,
        val sceneView: FrameSceneView?
    )

    fun getStoredInputs(): FrameStoredInputs {
        return FrameStoredInputs(
            nodes = _nodes.value ?: emptyList(),
            members = _members.value ?: emptyList(),
            nodalLoads = _nodalLoads.value ?: emptyList(),
            memberLoads = _memberLoads.value ?: emptyList(),
            slabs = _slabs.value ?: emptyList(),
            dimensions = _dimensions.value ?: emptyList(),
            settings = _settings.value ?: FrameAnalysisSettings(),
            result = _result.value,
            sceneView = modelerSceneView
        )
    }

    private companion object {
        const val PASTE_OFFSET_METERS = 1.0
    }
}

enum class FrameDesignNotice {
    SAVED,
    LOADED
}

enum class DiagramType(
    val displayNameAr: String,
    val displayNameEn: String,
    val code: String
) {
    BMD("مخطط العزوم", "Bending Moment", "BMD"),
    SFD("مخطط القص", "Shear Force", "SFD"),
    AFD("القوة المحورية", "Axial Force", "AFD");

    /** Localized concise label */
    val localizedLabel: String
        get() = if (com.civileg.app.utils.LocaleHelper.isArabic()) "$displayNameAr ($code)" else "$displayNameEn ($code)"

    fun localizedDisplayName(): String = localizedLabel
}

/** What the recovery gate needs to say truthfully. */
data class DraftRecoveryUi(val retained: Boolean, val startNewFailed: Boolean, val exportFailed: Boolean = false)
