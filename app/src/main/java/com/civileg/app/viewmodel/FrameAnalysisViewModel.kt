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
import com.civileg.app.domain.modeler.FrameModelValidator
import com.civileg.app.domain.modeler.FrameSceneView
import com.civileg.app.domain.modeler.FrameValidationIssue
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
    private val _settings = MutableLiveData(FrameAnalysisSettings())
    private val _steelFy = MutableLiveData(355.0)

    val nodes: LiveData<List<FrameNode>> get() = _nodes
    val members: LiveData<List<FrameMember>> get() = _members
    val nodalLoads: LiveData<List<NodalLoad>> get() = _nodalLoads
    val memberLoads: LiveData<List<MemberLoad>> get() = _memberLoads
    val slabs: LiveData<List<SlabPanel>> get() = _slabs
    val settings: LiveData<FrameAnalysisSettings> get() = _settings
    val steelFy: LiveData<Double> get() = _steelFy

    // === Undo / Redo ===
    private data class ModelSnapshot(
        val nodes: List<FrameNode>,
        val members: List<FrameMember>,
        val nodalLoads: List<NodalLoad>,
        val memberLoads: List<MemberLoad>,
        val slabs: List<SlabPanel>
    )

    private val undoStack = ArrayDeque<ModelSnapshot>()
    private val redoStack = ArrayDeque<ModelSnapshot>()
    private var nodeDragSnapshot: ModelSnapshot? = null
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
        _slabs.value ?: emptyList()
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
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _selectedMemberId.value = null
        restoringHistory = false
    }

    fun beginNodeDrag() {
        if (nodeDragSnapshot == null) {
            nodeDragSnapshot = snapshot()
        }
    }

    fun dragNode(nodeId: Int, x: Double, y: Double) {
        val current = _nodes.value?.toMutableList() ?: return
        val index = current.indexOfFirst { it.id == nodeId }
        if (index < 0) return
        val node = current[index]
        if (node.x == x && node.y == y) return
        current[index] = node.copy(x = x, y = y)
        _nodes.value = current
        invalidateResults()
    }

    fun endNodeDrag() {
        val before = nodeDragSnapshot ?: return
        nodeDragSnapshot = null
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
        gridMm: Int
    ) {
        val view = FrameSceneView(scale, panX, panY, width, height, gridMm)
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

    private data class FrameDraft(
        val version: Int = 1,
        val nodes: List<FrameNode> = emptyList(),
        val members: List<FrameMember> = emptyList(),
        val nodalLoads: List<NodalLoad> = emptyList(),
        val memberLoads: List<MemberLoad> = emptyList(),
        val slabs: List<SlabPanel> = emptyList(),
        val settings: FrameAnalysisSettings = FrameAnalysisSettings(),
        val steelFy: Double = 355.0
    )

    private val draftPrefs =
        application.getSharedPreferences("frame_modeler_draft", Context.MODE_PRIVATE)
    private val draftGson = Gson()
    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { persistDraft() }

    private fun scheduleAutosave() {
        autosaveHandler.removeCallbacks(autosaveRunnable)
        autosaveHandler.postDelayed(autosaveRunnable, 600)
    }

    private fun persistDraft() {
        try {
            val draft = FrameDraft(
                nodes = _nodes.value.orEmpty(),
                members = _members.value.orEmpty(),
                nodalLoads = _nodalLoads.value.orEmpty(),
                memberLoads = _memberLoads.value.orEmpty(),
                slabs = _slabs.value.orEmpty(),
                settings = _settings.value ?: FrameAnalysisSettings(),
                steelFy = _steelFy.value ?: 355.0
            )
            draftPrefs.edit().putString(DRAFT_KEY, draftGson.toJson(draft)).apply()
        } catch (_: Exception) {
        }
    }

    private fun restoreDraft(): Boolean {
        return try {
            val raw = draftPrefs.getString(DRAFT_KEY, null) ?: return false
            val draft = draftGson.fromJson(raw, FrameDraft::class.java) ?: return false
            if (draft.nodes.isEmpty()) return false
            _nodes.value = draft.nodes
            _members.value = draft.members
            _nodalLoads.value = draft.nodalLoads
            _memberLoads.value = draft.memberLoads
            _slabs.value = draft.slabs
            _settings.value = draft.settings
            _steelFy.value = draft.steelFy
            _historyState.value = (_historyState.value ?: 0) + 1
            true
        } catch (_: Exception) {
            false
        }
    }

    private val nodesAutosaveObserver = Observer<List<FrameNode>> { scheduleAutosave() }
    private val membersAutosaveObserver = Observer<List<FrameMember>> { scheduleAutosave() }
    private val nodalLoadsAutosaveObserver = Observer<List<NodalLoad>> { scheduleAutosave() }
    private val memberLoadsAutosaveObserver = Observer<List<MemberLoad>> { scheduleAutosave() }
    private val slabsAutosaveObserver = Observer<List<SlabPanel>> { scheduleAutosave() }
    private val settingsAutosaveObserver = Observer<FrameAnalysisSettings> { scheduleAutosave() }

    init {
        // Start with a blank drawing page; templates remain available from the toolbar.
        restoreDraft()
        _nodes.observeForever(nodesAutosaveObserver)
        _members.observeForever(membersAutosaveObserver)
        _nodalLoads.observeForever(nodalLoadsAutosaveObserver)
        _memberLoads.observeForever(memberLoadsAutosaveObserver)
        _slabs.observeForever(slabsAutosaveObserver)
        _settings.observeForever(settingsAutosaveObserver)
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
        super.onCleared()
    }

    // ========================================================================
    // Node Management
    // ========================================================================

    fun addNode(x: Double, y: Double, support: SupportType = SupportType.Free): Int {
        recordUndo()
        val current = _nodes.value?.toMutableList() ?: mutableListOf()
        val id = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(FrameNode(id, x, y, support))
        _nodes.value = current
        return id
    }

    fun updateNode(nodeId: Int, x: Double, y: Double, support: SupportType) {
        recordUndo()
        val current = _nodes.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == nodeId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(x = x, y = y, support = support)
            _nodes.value = current
        }
    }

    fun setNodeSupport(nodeId: Int, support: SupportType) {
        val node = _nodes.value?.find { it.id == nodeId } ?: return
        updateNode(nodeId, node.x, node.y, support)
    }

    fun removeNode(nodeId: Int) {
        recordUndo()
        val removedMemberIds = _members.value.orEmpty()
            .filter { it.nodeI == nodeId || it.nodeJ == nodeId }
            .map { it.id }
            .toSet()
        _nodes.value = _nodes.value?.filter { it.id != nodeId }
        // Remove connected members and loads
        _members.value = _members.value?.filter { it.nodeI != nodeId && it.nodeJ != nodeId }
        _memberLoads.value = _memberLoads.value?.filter { it.memberId !in removedMemberIds }
        _nodalLoads.value = _nodalLoads.value?.filter { it.nodeId != nodeId }
        _slabs.value = _slabs.value?.filter { !it.nodeIds.contains(nodeId) }
    }

    // ========================================================================
    // Member Management
    // ========================================================================

    fun addMember(
        nodeI: Int, nodeJ: Int,
        materialType: FrameMaterialType,
        memberType: FrameMemberType,
        concreteSection: ConcreteSectionProps? = null,
        steelSectionName: String? = null,
        name: String = ""
    ): Int {
        recordUndo()
        val current = _members.value?.toMutableList() ?: mutableListOf()
        val id = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(FrameMember(id, nodeI, nodeJ, materialType, memberType, concreteSection, steelSectionName, name))
        _members.value = current
        return id
    }

    fun addMemberBetween(
        nodeI: Int,
        nodeJ: Int,
        memberType: FrameMemberType = FrameMemberType.Beam
    ): Int? {
        if (nodeI == nodeJ) return null
        val exists = _members.value?.any { m ->
            (m.nodeI == nodeI && m.nodeJ == nodeJ) || (m.nodeI == nodeJ && m.nodeJ == nodeI)
        } ?: false
        if (exists) return null
        return addMember(
            nodeI = nodeI,
            nodeJ = nodeJ,
            materialType = FrameMaterialType.Concrete,
            memberType = memberType,
            concreteSection = ConcreteSectionProps(
                width = if (memberType == FrameMemberType.Column) 300.0 else 250.0,
                depth = if (memberType == FrameMemberType.Column) 300.0 else 500.0
            )
        )
    }

    fun updateMember(member: FrameMember) {
        recordUndo()
        val current = _members.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == member.id }
        if (idx >= 0) {
            current[idx] = member
            _members.value = current
        }
    }

    fun updateMemberReleases(memberId: Int, releaseI: Boolean, releaseJ: Boolean) {
        recordUndo()
        val current = _members.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == memberId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(releaseI = releaseI, releaseJ = releaseJ)
            _members.value = current
        }
    }

    fun removeMember(memberId: Int) {
        recordUndo()
        _members.value = _members.value?.filter { it.id != memberId }
        _memberLoads.value = _memberLoads.value?.filter { it.memberId != memberId }
    }

    // ========================================================================
    // Load Management
    // ========================================================================

    fun addNodalLoad(nodeId: Int, fx: Double, fy: Double, mz: Double, loadCase: String) {
        recordUndo()
        val current = _nodalLoads.value?.toMutableList() ?: mutableListOf()
        current.add(NodalLoad(nodeId, fx, fy, mz, loadCase))
        _nodalLoads.value = current
    }

    fun removeNodalLoad(index: Int) {
        recordUndo()
        _nodalLoads.value = _nodalLoads.value?.filterIndexed { i, _ -> i != index }
    }

    fun updateNodalLoad(index: Int, load: NodalLoad) {
        recordUndo()
        val current = _nodalLoads.value?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = load
            _nodalLoads.value = current
        }
    }

    fun addMemberLoad(memberId: Int, loadType: MemberLoadType, value: Double, position: Double, loadCase: String) {
        recordUndo()
        val current = _memberLoads.value?.toMutableList() ?: mutableListOf()
        current.add(MemberLoad(memberId, loadType, value, position, loadCase))
        _memberLoads.value = current
    }

    fun removeMemberLoad(index: Int) {
        recordUndo()
        _memberLoads.value = _memberLoads.value?.filterIndexed { i, _ -> i != index }
    }

    fun updateMemberLoad(index: Int, load: MemberLoad) {
        recordUndo()
        val current = _memberLoads.value?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = load
            _memberLoads.value = current
        }
    }

    // ========================================================================
    // Slab Panel Management
    // ========================================================================

    fun addSlabPanel(
        nodeIds: List<Int>,
        loadKNm2: Double,
        loadCase: String = "DL",
        thicknessMm: Double = 200.0,
        distribution: SlabDistribution = SlabDistribution.TwoWay
    ): Int? {
        if (nodeIds.size != 4 || nodeIds.distinct().size != 4) return null
        val nodes = _nodes.value ?: return null
        if (nodeIds.any { id -> nodes.none { it.id == id } }) return null
        val current = _slabs.value?.toMutableList() ?: mutableListOf()
        val id = (current.maxOfOrNull { it.id } ?: 0) + 1
        recordUndo()
        current.add(
            SlabPanel(
                id = id,
                nodeIds = nodeIds,
                thicknessMm = thicknessMm,
                loadKNm2 = loadKNm2,
                loadCase = loadCase,
                distribution = distribution
            )
        )
        _slabs.value = current
        return id
    }

    fun removeSlabPanel(panelId: Int) {
        recordUndo()
        _slabs.value = _slabs.value?.filter { it.id != panelId }
    }

    fun updateSlabPanel(panel: SlabPanel) {
        recordUndo()
        val current = _slabs.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == panel.id }
        if (idx >= 0) {
            current[idx] = panel
            _slabs.value = current
        }
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
        _settings.value = _settings.value?.copy(designCode = code)
    }

    fun updateSteelFy(fy: Double) {
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
            settings = _settings.value ?: FrameAnalysisSettings(),
            result = _result.value,
            sceneView = modelerSceneView
        )
    }

    private companion object {
        const val DRAFT_KEY = "frame_modeler_draft_v1"
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