package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.domain.calculations.utils.CalculationValidator
import com.civileg.app.shared.reporting.PdfDrawingGenerator
import com.civileg.app.utils.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Bitmap
import javax.inject.Inject
import com.civileg.app.domain.calculations.InputGuard

/** Owns a frozen pixel copy for the exact completed revision rendered by the capture area.
 * Callers can edit/recycle their bitmap or a returned copy without changing this capture.
 */
class ColumnDrawingCapture(val revisionSha256: String, bitmap: Bitmap) {
    private val frozenBitmap = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) {
        "Unable to freeze completed Column drawing"
    }
    val bitmap: Bitmap get() = requireNotNull(frozenBitmap.copy(Bitmap.Config.ARGB_8888, false)) {
        "Unable to read frozen completed Column drawing"
    }
}

data class ColumnUiState(
    val width: String = "300",
    val depth: String = "300",
    val height: String = "3.0",
    val fcu: String = "25",
    val fy: String = "400",
    val axialLoad: String = "1000",
    val mx: String = "0",
    val my: String = "0",
    val isSeismic: Boolean = false,
    val isCircular: Boolean = false,              // FIX: support circular columns
    val connectedSlab: String = "SOLID",          // FIX: slab type for K-factor
    val hasCap: Boolean = false,                  // FIX: column capital
    val designCode: DesignCode = DesignCode.ECP,
    val loadCombination: LoadCombination = LoadCombination.DEAD_LIVE,
    val preferredDiameter: String = "16",
    val manualNumBars: String = "",
    val autoOptimize: Boolean = true,
    val completedDesign: ColumnDesignSnapshot? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList(),
    val validationReport: CalculationValidator.ValidationReport? = null
) {
    val result: CalculatorEngine.ColumnResult? get() = completedDesign?.result()
}

@HiltViewModel
class ColumnViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnUiState())
    val uiState: StateFlow<ColumnUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    init {
        // Initialize with default code from settings
        _uiState.update { it.copy(designCode = settingsManager.defaultDesignCode) }
    }

    private var calculationGeneration = 0L

    private fun invalidateOutput() {
        calculationGeneration++
        _uiState.update { it.copy(completedDesign = null, isLoading = false, validationReport = null) }
    }

    fun isCurrent(snapshot: ColumnDesignSnapshot): Boolean = snapshot.hasValidFingerprint() &&
        !_uiState.value.isLoading && _uiState.value.completedDesign?.revisionSha256 == snapshot.revisionSha256

    val uiStateLiveData: LiveData<ColumnUiState> = _uiState.asLiveData()
    val result: LiveData<CalculatorEngine.ColumnResult?> = _uiState.map { it.result }.asLiveData()

    fun updateInputs(
        width: String? = null,
        depth: String? = null,
        height: String? = null,
        fcu: String? = null,
        fy: String? = null,
        axialLoad: String? = null,
        mx: String? = null,
        my: String? = null,
        isSeismic: Boolean? = null,
        isCircular: Boolean? = null,
        connectedSlab: String? = null,
        hasCap: Boolean? = null,
        preferredDiameter: String? = null,
        manualNumBars: String? = null
    ) {
        invalidateOutput()
        _uiState.update { state ->
            val newManualNumBars = manualNumBars ?: state.manualNumBars
            val newPreferredDiameter = preferredDiameter ?: state.preferredDiameter
            
            // If user manually edits number of bars or diameter, disable auto-optimization
            val shouldDisableAuto = manualNumBars != null || preferredDiameter != null

            state.copy(
                width = width ?: state.width,
                depth = depth ?: state.depth,
                height = height ?: state.height,
                fcu = fcu ?: state.fcu,
                fy = fy ?: state.fy,
                axialLoad = axialLoad ?: state.axialLoad,
                mx = mx ?: state.mx,
                my = my ?: state.my,
                isSeismic = isSeismic ?: state.isSeismic,
                isCircular = isCircular ?: state.isCircular,
                connectedSlab = connectedSlab ?: state.connectedSlab,
                hasCap = hasCap ?: state.hasCap,
                preferredDiameter = newPreferredDiameter,
                manualNumBars = newManualNumBars,
                autoOptimize = if (shouldDisableAuto) false else state.autoOptimize
            )
        }
    }

    fun applyEconomicalDesign() {
        invalidateOutput()
        _uiState.update { it.copy(autoOptimize = true, manualNumBars = "") }
        calculate()
    }

    fun applySafetyDesign() {
        val previousCount = _uiState.value.result?.reinforcement?.numBars ?: 4
        invalidateOutput()
        _uiState.update { state ->
            val currentDia = state.preferredDiameter.toIntOrNull() ?: 16
            state.copy(
                autoOptimize = false,
                preferredDiameter = (currentDia + 2).toString(), // Suggest larger bar
                manualNumBars = (previousCount + 2).toString()
            )
        }
        calculate()
    }

    fun updateDesignCode(code: DesignCode) {
        invalidateOutput()
        _uiState.update {
            val validCombos = LoadCombination.getByCode(code)
            val combo = if (it.loadCombination in validCombos) it.loadCombination else validCombos.first()
            it.copy(designCode = code, loadCombination = combo)
        }
        calculate()
    }

    fun updateLoadCombination(combination: LoadCombination) {
        invalidateOutput()
        _uiState.update { it.copy(loadCombination = combination) }
        calculate()
    }

    fun updateAutoOptimize(optimize: Boolean) {
        invalidateOutput()
        _uiState.update { it.copy(autoOptimize = optimize, manualNumBars = if (optimize) "" else it.manualNumBars) }
        calculate()
    }

    fun calculateColumnPro(
        width: Double, depth: Double, height: Double, fcu: Double, fy: Double,
        load: Double, diameter: Int, code: DesignCode,
        mx: Double = 0.0, my: Double = 0.0,
        isCircular: Boolean = false, connectedSlab: String = "SOLID", hasCap: Boolean = false,
        isSeismic: Boolean = false
    ) {
        startCalculation(ColumnCalculationInputs(
            width, depth, height, fcu, fy, load, load, mx, my, code, null,
            "CALLER_SUPPLIED_SECTION_ACTIONS_UNVERIFIED", 1.0, isCircular,
            connectedSlab, hasCap, diameter, true, null, isSeismic, true,
            reinforcementIntent = ColumnReinforcementIntent.REQUESTED_LAYOUT
        ))
    }

    fun calculateManual() = calculate()

    private fun calculate() {
        val state = _uiState.value
        try {
            fun number(label: String, value: String): Double = requireNotNull(value.toDoubleOrNull()) {
                "$label must be numeric"
            }.also { require(it.isFinite()) { "$label must be finite" } }
            val enteredLoad = number("Axial load", state.axialLoad)
            val multiplier = state.loadCombination.getFactorForCode(state.designCode)
            val diameter = requireNotNull(state.preferredDiameter.toIntOrNull()) { "Bar diameter must be an integer" }
            val manual = if (state.manualNumBars.isBlank()) null else
                requireNotNull(state.manualNumBars.toIntOrNull()) { "Bar count must be an integer" }
            startCalculation(ColumnCalculationInputs(
                number("Width", state.width), number("Depth", state.depth), number("Height", state.height),
                number("Concrete strength", state.fcu), number("Steel strength", state.fy),
                enteredLoad, enteredLoad * multiplier, number("Moment X", state.mx), number("Moment Y", state.my),
                state.designCode, state.loadCombination, "LEGACY_UI_AGGREGATE_AXIAL_MULTIPLIER_UNVERIFIED",
                multiplier, state.isCircular, state.connectedSlab, state.hasCap,
                diameter, state.autoOptimize, manual, state.isSeismic, true
            ))
        } catch (e: Exception) {
            invalidateOutput()
            _uiState.update { it.copy(errors = listOf(e.message ?: "Invalid column inputs")) }
            _errorMessage.value = e.message ?: "Invalid column inputs"
        }
    }

    private fun startCalculation(input: ColumnCalculationInputs) {
        invalidateOutput()
        val generation = calculationGeneration
        _uiState.update { it.copy(isLoading = true, errors = emptyList()) }
        viewModelScope.launch {
            try {
                InputGuard.positive("width", input.widthMm)
                InputGuard.positive("depth", input.depthMm)
                InputGuard.positive("height", input.heightM)
                InputGuard.positive("fcu", input.fcuMpa)
                InputGuard.positive("fy", input.fyMpa)
                InputGuard.positive("diameter", input.preferredDiameterMm)
                InputGuard.nonNegative("load", input.axialLoadPassedToEngineKn)
                val res = calculatorEngine.designColumn(
                    width = input.widthMm, depth = input.depthMm, clearHeight = input.heightM * 1000.0,
                    pu = input.axialLoadPassedToEngineKn, mx = input.momentXKnM, my = input.momentYKnM,
                    fcu = input.fcuMpa, fy = input.fyMpa, code = input.code,
                    isCircular = input.isCircular, connectedSlab = input.connectedSlab, hasCap = input.hasCap,
                    preferredDiameter = input.preferredDiameterMm, autoOptimize = input.autoOptimize,
                    manualNumBars = input.manualBarCount, autoIncludeSelfWeight = input.autoIncludeSelfWeight,
                    isSeismic = input.isSeismic, reinforcementIntent = input.reinforcementIntent
                )
                val report = CalculationValidator.validateColumn(res)
                val dl = CalculationValidator.inspectDeadLoadConsistency("COLUMN", mapOf(
                    "width" to input.widthMm, "depth" to input.depthMm, "height" to input.heightM
                ), input.enteredAxialLoadKn)
                val completed = ColumnDesignSnapshot.capture(input, res)
                if (generation == calculationGeneration) {
                    _uiState.update { it.copy(completedDesign = completed, isLoading = false,
                        validationReport = report.copy(warnings = report.warnings + dl.warnings), errors = emptyList()) }
                }
            } catch (e: Exception) {
                if (generation == calculationGeneration) {
                    _uiState.update { it.copy(completedDesign = null, isLoading = false,
                        errors = listOf(e.message ?: "Column calculation failed"), validationReport = null) }
                    _errorMessage.value = e.message ?: "Column calculation failed"
                }
            }
        }
    }

    /** Standalone DXF sheet for the current column design (null when no result). */
    fun buildDxfDrawing(snapshot: ColumnDesignSnapshot): com.civileg.app.utils.DxfDrawable? {
        require(snapshot.hasValidFingerprint())
        val res = snapshot.result()
        // This legacy builder draws rectangles only. Do not label one as a circular result.
        if (snapshot.input().isCircular || res.columnType.contains("CIRCULAR", ignoreCase = true)) return null
        return com.civileg.app.utils.ElementDxfDrawings.columnSection(
            title = "COLUMN ${res.columnType}",
            widthMm = res.width.toDouble(),
            depthMm = res.depth.toDouble(),
            coverMm = 40.0,
            mainBarDiaMm = res.reinforcement.diameter.toDouble(),
            mainBarCount = res.reinforcement.numBars,
            tieDiaMm = res.stirrups.diameter.toDouble(),
            tieSpacingMm = res.stirrups.spacing.toDouble()
        )
    }

    /** Export just the CAD drawing (no PDF) for the current design. */
    fun exportToDxf(context: Context, onComplete: (File?) -> Unit = {}) {
        val snapshot = _uiState.value.completedDesign ?: return
        if (!isCurrent(snapshot)) return
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(snapshot),
            baseName = "Column",
            scope = viewModelScope,
            onComplete = onComplete
        )
    }

    fun exportToPdf(context: Context, snapshot: ColumnDesignSnapshot, capture: ColumnDrawingCapture? = null,
        onComplete: (File?) -> Unit) {
        if (!isCurrent(snapshot) || _uiState.value.isExporting) { onComplete(null); return }
        val input = snapshot.input()
        val res = snapshot.result()
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                val fileName = "Column_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val h = input.heightM
                val fcuVal = input.fcuMpa
                val fyVal = input.fyMpa

                // Generate drawing for PDF
                // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                val isSpiralTies = res.stirrups.description.contains("SPIRAL", ignoreCase = true)
                val drawingBitmap = capture?.takeIf { it.revisionSha256 == snapshot.revisionSha256 }?.bitmap ?: try {
                    PdfDrawingGenerator.generateColumnDrawing(
                        columnWidth = res.width,
                        columnDepth = res.depth,
                        columnHeight = h * 1000.0,
                        numBars = res.reinforcement.numBars,
                        barDia = res.reinforcement.diameter.toDouble(),
                        tieDia = res.stirrups.diameter.toDouble(),
                        tieSpacing = res.stirrups.spacing,
                        cover = 40.0,
                        isSpiral = isSpiralTies,
                        spiralPitch = if (isSpiralTies) res.stirrups.spacing else 0.0,
                        sectionType = if (input.isCircular) "Circular" else "Rectangular"
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

                val codeName = when(input.code) {
                    com.civileg.app.domain.entities.DesignCode.ACI -> "ACI 318"
                    com.civileg.app.domain.entities.DesignCode.SBC -> "SBC 304"
                    else -> "ECP 203"
                }

                val inputsMap = mutableMapOf(
                    "Completed Revision" to snapshot.revisionSha256,
                    "Output Scope" to "Recorded engine output and schematic only; this repair does not verify the member-design method or grant execution approval.",
                    "Reinforcement Selection Intent" to input.reinforcementIntent.name,
                    "Preferred Diameter Request (not adopted layout)" to "${input.preferredDiameterMm} mm",
                    "Action Basis (unverified convention)" to input.actionBasis,
                    "Entered Axial Load" to "${input.enteredAxialLoadKn} kN",
                    "Engine Axial Argument" to "${input.axialLoadPassedToEngineKn} kN",
                    "Aggregate Axial Multiplier" to input.appliedAggregateLoadMultiplier.toString(),
                    "Engine Includes Self Weight" to input.autoIncludeSelfWeight.toString(),
                    "Column Type" to res.columnType,
                    "Shape" to if (input.isCircular) "Circular" else "Rectangular",
                    "Width" to "${res.width} mm",
                    "Depth" to "${res.depth} mm",
                    "Height" to "${h} m",
                    "Axial Load Pu" to "${String.format("%.1f", res.pu)} kN",
                    "f'cu" to "${fcuVal} MPa",
                    "fy" to "${fyVal} MPa",
                    "Connected Slab" to input.connectedSlab,
                    "Has Capital" to if (input.hasCap) "Yes" else "No",
                    "Design Code" to codeName,
                    "Load Combination" to (input.loadCombination?.name ?: "Caller supplied")
                )
                val resultsMap = mutableMapOf(
                    "Axial Capacity" to "${String.format("%.1f", res.axialCapacity)} kN",
                    "Reinforcement" to res.reinforcement.barString,
                    "Stirrups" to res.stirrups.description,
                    "Slenderness Ratio" to String.format("%.1f", res.slenderness),
                    "Is Slender" to if (res.isSlender) "Yes" else "No",
                    "Punching Safe" to if (res.punchingSafe) "Yes" else "No",
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel Weight" to "${String.format("%.1f", res.steelWeight)} kg"
                )
                
                if (res.stirrups.zones.isNotEmpty()) {
                    res.stirrups.zones.forEachIndexed { i, zone ->
                        resultsMap["Tie Distribution Zone ${i+1}"] = "${zone.name}: ${zone.description} [${String.format("%.1f", (zone.endLocation-zone.startLocation)/1000.0)}m]"
                    }
                }
                val safetyChecks = res.safetyChecks.map { chk ->
                    GenericSafetyCheck(
                        name = chk.name, calculated = chk.value,
                        limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                    )
                }

                // Professional English PDF Report — English only, no Arabic encoding issues
                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم العمود - ${res.columnType}",
                    titleEn = "Column Design Report - ${res.columnType}",
                    subtitle = "Code: $codeName | ${res.width}x${res.depth}mm, H=${h}m",
                    designType = "Column (${res.columnType})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    calculationSteps = res.calculationSteps,
                    bbsEntries = runCatching {
                        com.civileg.app.domain.calculations.utils.BbsGenerator.generateColumnBbs("C1", res, h * 1000.0)
                    }.getOrDefault(emptyList()),
                    boqRows = BoqEstimator.columnRows(res, h, "C1"),
                    drawingBitmap = drawingBitmap,
                    drawing = buildDxfDrawing(snapshot),
                    outputPath = file.absolutePath
                ).pdfFile

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false) }
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                }
            } catch (e: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false, errors = listOf("PDF Error: ${e.message}")) }
                    _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message}"
                    onComplete(null)
                }
            }
        }
    }

    fun saveColumn(projectId: Long, name: String, snapshot: ColumnDesignSnapshot, onComplete: (Boolean) -> Unit = {}) {
        if (projectId <= 0 || name.isBlank() || !isCurrent(snapshot) || _uiState.value.isSaving) {
            onComplete(false)
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.saveColumnDesignSnapshot(projectId, name.trim(), snapshot)
                onComplete(true)
            } catch (e: Exception) {
                _errorMessage.value = "Save failed: ${e.message}"
                onComplete(false)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun mapDesignCode(code: DesignCode): DesignCode = when(code) {
        DesignCode.ECP -> DesignCode.ECP
        DesignCode.ACI -> DesignCode.ACI
        DesignCode.SBC -> DesignCode.SBC
    }

    fun reset() {
        invalidateOutput()
        _uiState.value = ColumnUiState(isSaving = _uiState.value.isSaving, isExporting = _uiState.value.isExporting)
    }
}
