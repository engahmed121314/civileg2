package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ColumnUiState(
    val width: Double = 300.0,
    val depth: Double = 300.0,
    val height: Double = 3.0,
    val fcu: Double = 25.0,
    val fy: Double = 400.0,
    val axialLoad: Double = 1000.0,
    val designCode: DesignCode = DesignCode.ECP,
    val loadCombination: LoadCombination = LoadCombination.DEAD_LIVE,
    val result: CalculatorEngine.ColumnResult? = null,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList()
)

@HiltViewModel
class ColumnViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnUiState())
    val uiState: StateFlow<ColumnUiState> = _uiState.asStateFlow()
    
    val uiStateLiveData: LiveData<ColumnUiState> = _uiState.asLiveData()
    val result: LiveData<CalculatorEngine.ColumnResult?> = _uiState.map { it.result }.asLiveData()

    fun updateInputs(
        width: Double? = null,
        depth: Double? = null,
        height: Double? = null,
        fcu: Double? = null,
        fy: Double? = null,
        axialLoad: Double? = null
    ) {
        _uiState.update { state ->
            state.copy(
                width = width ?: state.width,
                depth = depth ?: state.depth,
                height = height ?: state.height,
                fcu = fcu ?: state.fcu,
                fy = fy ?: state.fy,
                axialLoad = axialLoad ?: state.axialLoad
            )
        }
        calculate()
    }

    fun updateDesignCode(code: DesignCode) {
        _uiState.update { it.copy(designCode = code) }
        calculate()
    }

    fun updateLoadCombination(combination: LoadCombination) {
        _uiState.update { it.copy(loadCombination = combination) }
        calculate()
    }

    fun calculateColumnPro(width: Double, depth: Double, height: Double, fcu: Double, fy: Double, load: Double, diameter: Int, code: CalculatorEngine.DesignCode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // تصحيح: تمرير البارامترات حسب تعريف الدالة في CalculatorEngine
                // الدالة تتوقع: (width, depth, pu, fcu, fy, code)
                val res = calculatorEngine.designColumn(
                    width = width,
                    depth = depth,
                    pu = load,
                    fcu = fcu,
                    fy = fy,
                    code = code
                )
                _uiState.update { it.copy(result = res, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errors = listOf(e.message ?: "Error")) }
            }
        }
    }

    private fun calculate() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                // تصحيح: استخدام البارامترات الصحيحة للدالة الحالية
                val res = calculatorEngine.designColumn(
                    width = state.width,
                    depth = state.depth,
                    pu = state.axialLoad * state.loadCombination.factor,
                    fcu = state.fcu,
                    fy = state.fy,
                    code = mapDesignCode(state.designCode),
                    clearHeight = state.height * 1000.0 // التحويل لـ mm
                )
                _uiState.update { it.copy(result = res, errors = emptyList()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errors = listOf(e.message ?: "Error")) }
            }
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val state = _uiState.value
        val res = state.result ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val exporter = ComprehensivePdfExporter(context)
                val fileName = "Column_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val colType = if (res.columnType == "CIRCULAR") ColumnType.Circular(res.width) else ColumnType.Rectangular(res.width, res.depth)
                val advResult = AdvancedColumnResult(
                    columnType = colType,
                    axialCapacity = res.axialCapacity,
                    momentCapacityX = 0.0,
                    momentCapacityY = 0.0,
                    slendernessRatio = res.slenderness,
                    isSlender = res.isSlender,
                    effectiveLength = state.height * 1000.0,
                    reinforcementResult = ReinforcementResult(
                        astRequired = res.reinforcementArea,
                        astProvided = res.reinforcementArea,
                        barDiameter = res.reinforcement.diameter.toDouble(),
                        numberOfBars = res.reinforcement.numBars,
                        tiesDiameter = res.stirrups.diameter.toDouble(),
                        tiesSpacing = res.stirrups.spacing,
                        isSafe = res.isSafe,
                        utilizationRatio = (res.pu / (if(res.axialCapacity > 0) res.axialCapacity else 1.0)).coerceIn(0.0, 1.2)
                    ),
                    inventoryAnalysis = null,
                    biaxialCheck = null,
                    punchingCheck = PunchingCheckResult(res.pu, 1000.0, res.punchingSafe, false, 2000.0),
                    warnings = emptyList(),
                    codeNotes = listOf("تم التصدير من تطبيق Civil EG Pro"),
                    steelWeightPerMeter = res.steelWeight / if(state.height > 0) state.height else 1.0,
                    concreteVolumePerMeter = res.concreteVolume / if(state.height > 0) state.height else 1.0
                )

                val inputs = ColumnInputs(
                    fcu = state.fcu,
                    fy = state.fy,
                    axialLoad = res.pu,
                    momentX = 0.0,
                    momentY = 0.0,
                    loadCombination = state.loadCombination,
                    unsupportedLength = state.height,
                    columnType = colType
                )

                val exportedFile = exporter.exportColumnReport(
                    projectName = "تقرير تصميم أعمدة",
                    designCode = state.designCode,
                    columnType = colType,
                    inputs = inputs,
                    result = advResult,
                    inventoryAnalysis = null,
                    alternatives = emptyList(),
                    outputPath = file.absolutePath
                )
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false) }
                    exportedFile?.let {
                        com.civileg.app.utils.ExportUtils.openPdf(context, it)
                    }
                    onComplete(exportedFile)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false, errors = listOf("PDF Error: ${e.message}")) }
                    onComplete(null)
                }
            }
        }
    }

    fun saveColumn(projectId: Long, name: String, result: CalculatorEngine.ColumnResult) {
        viewModelScope.launch {
            repository.saveColumnDesign(projectId, name, result)
        }
    }

    private fun mapDesignCode(code: DesignCode): CalculatorEngine.DesignCode = when(code) {
        DesignCode.ECP -> CalculatorEngine.DesignCode.EGYPTIAN
        DesignCode.ACI -> CalculatorEngine.DesignCode.ACI
        DesignCode.SBC -> CalculatorEngine.DesignCode.SAUDI
    }

    fun reset() {
        _uiState.value = ColumnUiState()
    }
}
