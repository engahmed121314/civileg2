package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ColumnUiState(
    val width: String = "300",
    val depth: String = "300",
    val height: String = "3.0",
    val fcu: String = "25",
    val fy: String = "400",
    val axialLoad: String = "1000",
    val designCode: DesignCode = DesignCode.ECP,
    val loadCombination: LoadCombination = LoadCombination.DEAD_LIVE,
    val preferredDiameter: String = "16",
    val manualNumBars: String = "",
    val autoOptimize: Boolean = true,
    val result: CalculatorEngine.ColumnResult? = null,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList()
)

@HiltViewModel
class ColumnViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnUiState())
    val uiState: StateFlow<ColumnUiState> = _uiState.asStateFlow()
    
    init {
        // Initialize with default code from settings
        _uiState.update { it.copy(designCode = settingsManager.defaultDesignCode) }
    }

    val uiStateLiveData: LiveData<ColumnUiState> = _uiState.asLiveData()
    val result: LiveData<CalculatorEngine.ColumnResult?> = _uiState.map { it.result }.asLiveData()

    fun updateInputs(
        width: String? = null,
        depth: String? = null,
        height: String? = null,
        fcu: String? = null,
        fy: String? = null,
        axialLoad: String? = null,
        preferredDiameter: String? = null,
        manualNumBars: String? = null
    ) {
        _uiState.update { state ->
            val newManualNumBars = manualNumBars ?: state.manualNumBars
            val newPreferredDiameter = preferredDiameter ?: state.preferredDiameter
            
            // If user manually edits number of bars or diameter, disable auto-optimization
            val shouldDisableAuto = manualNumBars != null || (preferredDiameter != null && !state.autoOptimize)

            state.copy(
                width = width ?: state.width,
                depth = depth ?: state.depth,
                height = height ?: state.height,
                fcu = fcu ?: state.fcu,
                fy = fy ?: state.fy,
                axialLoad = axialLoad ?: state.axialLoad,
                preferredDiameter = newPreferredDiameter,
                manualNumBars = newManualNumBars,
                autoOptimize = if (shouldDisableAuto) false else state.autoOptimize
            )
        }
    }

    fun applyEconomicalDesign() {
        _uiState.update { it.copy(autoOptimize = true) }
        calculate()
    }

    fun applySafetyDesign() {
        _uiState.update { state ->
            val currentDia = state.preferredDiameter.toIntOrNull() ?: 16
            state.copy(
                autoOptimize = false,
                preferredDiameter = (currentDia + 2).toString(), // Suggest larger bar
                manualNumBars = ( (state.result?.reinforcement?.numBars ?: 4) + 2).toString()
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

    fun updateAutoOptimize(optimize: Boolean) {
        _uiState.update { it.copy(autoOptimize = optimize) }
        calculate()
    }

    fun calculateColumnPro(width: Double, depth: Double, height: Double, fcu: Double, fy: Double, load: Double, diameter: Int, code: CalculatorEngine.DesignCode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
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

    fun calculateManual() {
        calculate()
    }

    private fun calculate() {
        val state = _uiState.value
        val w = state.width.toDoubleOrNull() ?: return
        val d = state.depth.toDoubleOrNull() ?: return
        val h = state.height.toDoubleOrNull() ?: 3.0
        val fcuVal = state.fcu.toDoubleOrNull() ?: 25.0
        val fyVal = state.fy.toDoubleOrNull() ?: 400.0
        val load = state.axialLoad.toDoubleOrNull() ?: 0.0
        val dia = state.preferredDiameter.toIntOrNull() ?: 16
        val manualBars = state.manualNumBars.toIntOrNull()

        viewModelScope.launch {
            try {
                val res = calculatorEngine.designColumn(
                    width = w,
                    depth = d,
                    pu = load * state.loadCombination.getFactorForCode(state.designCode),
                    fcu = fcuVal,
                    fy = fyVal,
                    code = mapDesignCode(state.designCode),
                    clearHeight = h * 1000.0,
                    preferredDiameter = dia,
                    autoOptimize = state.autoOptimize,
                    manualNumBars = manualBars
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
                
                val h = state.height.toDoubleOrNull() ?: 3.0
                val fcuVal = state.fcu.toDoubleOrNull() ?: 25.0
                val fyVal = state.fy.toDoubleOrNull() ?: 400.0

                // Generate drawing for PDF
                val drawingBitmap = try {
                    PdfDrawingGenerator.generateColumnDrawing(
                        columnWidth = res.width,
                        columnDepth = res.depth,
                        columnHeight = h * 1000.0,
                        numBars = res.reinforcement.numBars,
                        barDia = res.reinforcement.diameter.toDouble(),
                        tieDia = res.stirrups.diameter.toDouble(),
                        tieSpacing = res.stirrups.spacing,
                        cover = 40.0
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val colType = if (res.columnType == "CIRCULAR") ColumnType.Circular(res.width) else ColumnType.Rectangular(res.width, res.depth)
                // Compute moment capacities from the column result
                // res.momentCapacity is the uniaxial moment capacity; approximate Y based on aspect ratio
                val mcX = res.momentCapacity
                val mcY = if (res.depth > 0 && res.width > 0) {
                    res.momentCapacity * (res.width / res.depth).coerceIn(0.5, 2.0)
                } else res.momentCapacity
                // Compute punching shear capacity estimate (ECP 203 approximation)
                val cover = 40.0
                val barDia = res.reinforcement.diameter.toDouble()
                val effectiveDepth = (res.depth - cover - barDia / 2.0).coerceAtLeast(50.0)
                val criticalPerimeter = if (res.columnType == "CIRCULAR") {
                    // Circular column: perimeter at d/2 from column face
                    Math.PI * (res.width + effectiveDepth)
                } else {
                    // Rectangular column: 2*(b + h) + 4*d at d/2 from faces
                    2.0 * (res.width + res.depth) + 4.0 * effectiveDepth
                }
                val punchingShearStress = 0.8 * 1.0 * kotlin.math.sqrt(fcuVal) // MPa, simplified ECP 203
                val punchingCapacity = punchingShearStress * criticalPerimeter * effectiveDepth / 1000.0 // kN
                val advResult = AdvancedColumnResult(
                    columnType = colType,
                    axialCapacity = res.axialCapacity,
                    momentCapacityX = mcX,
                    momentCapacityY = mcY,
                    slendernessRatio = res.slenderness,
                    isSlender = res.isSlender,
                    effectiveLength = h * 1000.0,
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
                    punchingCheck = PunchingCheckResult(res.pu, punchingCapacity, res.punchingSafe, false, criticalPerimeter),
                    warnings = emptyList(),
                    codeNotes = listOf("تم التصدير من تطبيق Civil EG Pro"),
                    steelWeightPerMeter = res.steelWeight / if(h > 0) h else 1.0,
                    concreteVolumePerMeter = res.concreteVolume / if(h > 0) h else 1.0
                )

                val inputs = ColumnInputs(
                    fcu = fcuVal,
                    fy = fyVal,
                    axialLoad = res.pu,
                    momentX = 0.0,
                    momentY = 0.0,
                    loadCombination = state.loadCombination,
                    unsupportedLength = h,
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
                    outputPath = file.absolutePath,
                    drawingBitmap = drawingBitmap
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
