package com.civileg.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.civileg.app.domain.calculations.ConcreteFrameDesign
import com.civileg.app.domain.calculations.FrameAnalysisEngine
import com.civileg.app.domain.calculations.SteelFrameDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.*

@HiltViewModel
class FrameAnalysisViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    // === Frame Data (mutable state) ===
    private val _nodes = MutableLiveData<List<FrameNode>>(emptyList())
    private val _members = MutableLiveData<List<FrameMember>>(emptyList())
    private val _nodalLoads = MutableLiveData<List<NodalLoad>>(emptyList())
    private val _memberLoads = MutableLiveData<List<MemberLoad>>(emptyList())
    private val _settings = MutableLiveData(FrameAnalysisSettings())
    private val _steelFy = MutableLiveData(355.0)

    val nodes: LiveData<List<FrameNode>> get() = _nodes
    val members: LiveData<List<FrameMember>> get() = _members
    val nodalLoads: LiveData<List<NodalLoad>> get() = _nodalLoads
    val memberLoads: LiveData<List<MemberLoad>> get() = _memberLoads
    val settings: LiveData<FrameAnalysisSettings> get() = _settings

    // === Results ===
    private val _result = MutableLiveData<FrameAnalysisResult>()
    val result: LiveData<FrameAnalysisResult> get() = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _activeDiagramType = MutableLiveData(DiagramType.BMD)
    val activeDiagramType: LiveData<DiagramType> get() = _activeDiagramType

    private val _selectedMemberId = MutableLiveData<Int?>(null)
    val selectedMemberId: LiveData<Int?> get() = _selectedMemberId

    private val _drawingViewMode = MutableLiveData(0)
    val drawingViewMode: LiveData<Int> get() = _drawingViewMode

    fun setDrawingViewMode(mode: Int) {
        _drawingViewMode.value = mode.coerceIn(0, 3)
    }

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    // === Design Results ===
    private val _concreteResults = MutableLiveData<List<ConcreteMemberDesignResult>>()
    val concreteResults: LiveData<List<ConcreteMemberDesignResult>> get() = _concreteResults

    private val _steelResults = MutableLiveData<List<SteelMemberDesignResult>>()
    val steelResults: LiveData<List<SteelMemberDesignResult>> get() = _steelResults

    val hasConcreteMembers: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(_members) { value = it.any { m -> m.materialType == FrameMaterialType.Concrete } }
    }

    val hasSteelMembers: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(_members) { value = it.any { m -> m.materialType == FrameMaterialType.Steel } }
    }

    // ========================================================================
    // Node Management
    // ========================================================================

    fun addNode(x: Double, y: Double, support: SupportType = SupportType.Free) {
        val current = _nodes.value?.toMutableList() ?: mutableListOf()
        val id = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(FrameNode(id, x, y, support))
        _nodes.value = current
    }

    fun updateNode(nodeId: Int, x: Double, y: Double, support: SupportType) {
        val current = _nodes.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == nodeId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(x = x, y = y, support = support)
            _nodes.value = current
        }
    }

    fun removeNode(nodeId: Int) {
        _nodes.value = _nodes.value?.filter { it.id != nodeId }
        _members.value = _members.value?.filter { it.nodeI != nodeId && it.nodeJ != nodeId }
        _nodalLoads.value = _nodalLoads.value?.filter { it.nodeId != nodeId }
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
    ) {
        val current = _members.value?.toMutableList() ?: mutableListOf()
        val id = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(FrameMember(id, nodeI, nodeJ, materialType, memberType, concreteSection, steelSectionName, name))
        _members.value = current
    }

    fun updateMember(member: FrameMember) {
        val current = _members.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.id == member.id }
        if (idx >= 0) {
            current[idx] = member
            _members.value = current
        }
    }

    fun removeMember(memberId: Int) {
        _members.value = _members.value?.filter { it.id != memberId }
        _memberLoads.value = _memberLoads.value?.filter { it.memberId != memberId }
    }

    // ========================================================================
    // Load Management
    // ========================================================================

    fun addNodalLoad(nodeId: Int, fx: Double, fy: Double, mz: Double, loadCase: String) {
        val current = _nodalLoads.value?.toMutableList() ?: mutableListOf()
        current.add(NodalLoad(nodeId, fx, fy, mz, loadCase))
        _nodalLoads.value = current
    }

    fun removeNodalLoad(index: Int) {
        _nodalLoads.value = _nodalLoads.value?.filterIndexed { i, _ -> i != index }
    }

    fun updateNodalLoad(index: Int, load: NodalLoad) {
        val current = _nodalLoads.value?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = load
            _nodalLoads.value = current
        }
    }

    fun addMemberLoad(memberId: Int, loadType: MemberLoadType, value: Double, position: Double, loadCase: String) {
        val current = _memberLoads.value?.toMutableList() ?: mutableListOf()
        current.add(MemberLoad(memberId, loadType, value, position, loadCase))
        _memberLoads.value = current
    }

    fun removeMemberLoad(index: Int) {
        _memberLoads.value = _memberLoads.value?.filterIndexed { i, _ -> i != index }
    }

    fun updateMemberLoad(index: Int, load: MemberLoad) {
        val current = _memberLoads.value?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = load
            _memberLoads.value = current
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
    // Solve & Design (runs on Dispatchers.Default — NOT main thread)
    // ========================================================================

    fun solveFrame() {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val nodes = _nodes.value ?: emptyList()
                val members = _members.value ?: emptyList()
                val nodalLoads = _nodalLoads.value ?: emptyList()
                val memberLoads = _memberLoads.value ?: emptyList()
                val settings = _settings.value ?: FrameAnalysisSettings()

                Log.d(TAG, "solveFrame: nodes=${nodes.size}, members=${members.size}")

                // Input validation
                if (nodes.isEmpty() || members.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Add at least 2 nodes and 1 member"
                        _isLoading.value = false
                    }
                    return@launch
                }

                val nodeIds = nodes.map { it.id }.toSet()
                for (m in members) {
                    if (m.nodeI !in nodeIds || m.nodeJ !in nodeIds) {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Member #${m.id} references non-existent node(s)"
                            _isLoading.value = false
                        }
                        return@launch
                    }
                }

                if (nodes.none { it.support != SupportType.Free }) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Structure must have at least one support"
                        _isLoading.value = false
                    }
                    return@launch
                }

                // Run analysis off main thread
                val analysisResult = FrameAnalysisEngine.solveFrame(nodes, members, nodalLoads, memberLoads, settings)

                if (!analysisResult.isSolved) {
                    Log.w(TAG, "Analysis failed: ${analysisResult.errorMessage}")
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = analysisResult.errorMessage
                        _result.value = analysisResult
                        _isLoading.value = false
                    }
                    return@launch
                }

                // Validate results for NaN
                val hasNaN = analysisResult.nodeResults.any { nr ->
                    nr.dx.isNaN() || nr.dy.isNaN() || nr.rz.isNaN()
                }
                if (hasNaN) {
                    Log.e(TAG, "NaN detected in results")
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "Analysis produced invalid results. Check supports and loads."
                        _isLoading.value = false
                    }
                    return@launch
                }

                // Run concrete design
                val concreteDesignResults = try {
                    val concreteMembers = members.filter { it.materialType == FrameMaterialType.Concrete }
                    if (concreteMembers.isNotEmpty()) {
                        ConcreteFrameDesign.designAllConcreteMembers(
                            members, analysisResult.memberEndForces, analysisResult.memberDiagrams, settings.designCode
                        )
                    } else emptyList()
                } catch (e: Throwable) {
                    Log.e(TAG, "Concrete design error", e)
                    emptyList()
                }

                // Run steel design
                val steelDesignResults = try {
                    val steelMembers = members.filter { it.materialType == FrameMaterialType.Steel }
                    if (steelMembers.isNotEmpty()) {
                        SteelFrameDesign.designAllSteelMembers(
                            members, analysisResult.memberEndForces, analysisResult.memberDiagrams,
                            settings.designCode, _steelFy.value ?: 355.0
                        )
                    } else emptyList()
                } catch (e: Throwable) {
                    Log.e(TAG, "Steel design error", e)
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    _result.value = analysisResult.copy(
                        concreteDesignResults = concreteDesignResults,
                        steelDesignResults = steelDesignResults
                    )
                    _concreteResults.value = concreteDesignResults
                    _steelResults.value = steelDesignResults
                    _isLoading.value = false
                    Log.d(TAG, "solveFrame completed successfully")
                }

            } catch (e: Throwable) {
                Log.e(TAG, "solveFrame crashed", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Frame analysis error: ${e.message ?: "Unknown error"}"
                    _isLoading.value = false
                }
            }
        }
    }

    companion object {
        private const val TAG = "FrameAnalysisVM"
    }

    // ========================================================================
    // Templates
    // ========================================================================

    fun loadSimplePortalFrame(span: Double, height: Double, udl: Double) {
        val (nodes, members, loads) = FrameAnalysisEngine.createSimplePortalFrame(span, height, udl)
        _nodes.value = nodes
        _members.value = members
        _memberLoads.value = loads
        _nodalLoads.value = emptyList()
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _errorMessage.value = null
    }

    fun loadTwoStoryFrame(span: Double, height1: Double, height2: Double, udl: Double) {
        val (ns, ms, ls) = FrameAnalysisEngine.createMultiStoryFrame(
            listOf(span), listOf(height1, height2), udl
        )
        _nodes.value = ns
        _members.value = ms
        _memberLoads.value = ls
        _nodalLoads.value = emptyList()
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _errorMessage.value = null
    }

    fun clearAll() {
        _nodes.value = emptyList()
        _members.value = emptyList()
        _nodalLoads.value = emptyList()
        _memberLoads.value = emptyList()
        _result.value = null
        _concreteResults.value = emptyList()
        _steelResults.value = emptyList()
        _errorMessage.value = null
        _selectedMemberId.value = null
    }

    // ========================================================================
    // Stored Inputs for PDF Export
    // ========================================================================

    data class FrameStoredInputs(
        val nodes: List<FrameNode>,
        val members: List<FrameMember>,
        val nodalLoads: List<NodalLoad>,
        val memberLoads: List<MemberLoad>,
        val settings: FrameAnalysisSettings,
        val result: FrameAnalysisResult?
    )

    fun getStoredInputs(): FrameStoredInputs {
        return FrameStoredInputs(
            nodes = _nodes.value ?: emptyList(),
            members = _members.value ?: emptyList(),
            nodalLoads = _nodalLoads.value ?: emptyList(),
            memberLoads = _memberLoads.value ?: emptyList(),
            settings = _settings.value ?: FrameAnalysisSettings(),
            result = _result.value
        )
    }
}

enum class DiagramType(val displayNameAr: String) {
    BMD("مخطط العزوم"),
    SFD("مخطط القص"),
    AFD("مخطط المحوري");

    @Composable
    fun localizedDisplayName(): String = when (this) {
        BMD -> "BMD"
        SFD -> "SFD"
        AFD -> "AFD"
    }
}