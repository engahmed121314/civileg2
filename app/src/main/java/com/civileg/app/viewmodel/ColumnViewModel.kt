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
import com.civileg.app.domain.validation.FieldKind
import com.civileg.app.domain.validation.FieldSpec
import com.civileg.app.domain.calculations.InputGuard

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
    val result: CalculatorEngine.ColumnResult? = null,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList(),
    val validationReport: CalculationValidator.ValidationReport? = null,
    /** Outcome of the last Design attempt; the screen anchors each issue to its field. */
    val validation: com.civileg.app.domain.validation.ValidationResult? = null,
    /** Bumped on every failed attempt so the screen re-focuses even when the issue list is equal. */
    val validationTick: Int = 0,
    /** Parsed inputs, code, combination and adjusted Pu that [result] was solved with. Drives ALL outputs. */
    val snapshot: ColumnCalcSnapshot? = null
) {
    val rebarDraftKey: String get() = "$autoOptimize|$manualNumBars|$preferredDiameter"
    val solvedHeightM: Double? get() = snapshot?.heightM
    /** True while the rebar draft differs from the adopted result: design/export/save must not use it. */
    val resultIsStale: Boolean get() = result != null && (snapshot == null || snapshot.rebarKey != rebarDraftKey)
}

/** Everything the shown ColumnResult was computed from. Exports, BBS/BOQ and save read this, never live UI state. */
data class ColumnCalcSnapshot(
    val widthMm: Double, val depthMm: Double, val heightM: Double,
    val fcu: Double, val fy: Double,
    val axialLoad: Double, val mx: Double, val my: Double,
    val isSeismic: Boolean, val isCircular: Boolean,
    val connectedSlab: String, val hasCap: Boolean,
    val designCode: DesignCode, val loadCombination: LoadCombination,
    /** Pu actually sent to the engine = axialLoad x load-combination factor. */
    val adjustedPu: Double,
    val autoOptimize: Boolean, val manualNumBars: Int?, val preferredDiameter: Int,
    val rebarKey: String
)

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

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

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
        // Rebar-card edits (manualNumBars / preferredDiameter) keep the shown result until the
        // "update rebar" action; every other edit makes the old result stale.
        val invalidates = width != null || depth != null || height != null || fcu != null || fy != null ||
            axialLoad != null || mx != null || my != null || isSeismic != null || isCircular != null ||
            connectedSlab != null || hasCap != null
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
                mx = mx ?: state.mx,
                my = my ?: state.my,
                isSeismic = isSeismic ?: state.isSeismic,
                isCircular = isCircular ?: state.isCircular,
                connectedSlab = connectedSlab ?: state.connectedSlab,
                hasCap = hasCap ?: state.hasCap,
                preferredDiameter = newPreferredDiameter,
                manualNumBars = newManualNumBars,
                autoOptimize = if (shouldDisableAuto) false else state.autoOptimize,
                result = if (invalidates) null else state.result,
                validationReport = if (invalidates) null else state.validationReport,
                snapshot = if (invalidates) null else state.snapshot
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
        _uiState.update {
            val validCombos = LoadCombination.getByCode(code)
            val combo = if (it.loadCombination in validCombos) it.loadCombination else validCombos.first()
            it.copy(designCode = code, loadCombination = combo)
        }
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

    fun calculateColumnPro(
        width: Double, depth: Double, height: Double, fcu: Double, fy: Double,
        load: Double, diameter: Int, code: DesignCode,
        mx: Double = 0.0, my: Double = 0.0,
        isCircular: Boolean = false, connectedSlab: String = "SOLID", hasCap: Boolean = false,
        isSeismic: Boolean = false
    ) {
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("height", height)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("diameter", diameter)
        InputGuard.nonNegative("load", load)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = calculatorEngine.designColumn(
                    width = width,
                    depth = depth,
                    pu = load,
                    mx = mx,
                    my = my,
                    fcu = fcu,
                    fy = fy,
                    code = code,
                    isCircular = isCircular,
                    connectedSlab = connectedSlab,
                    hasCap = hasCap,
                    clearHeight = height * 1000.0,
                    preferredDiameter = diameter,
                    isSeismic = isSeismic
                )
                _uiState.update { it.copy(result = res, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errors = listOf(e.message ?: "Error")) }
                _errorMessage.value = "Column calculation error / خطأ في حساب العمود: ${e.message}"
            }
        }
    }

    fun calculateManual() {
        calculate()
    }

    /** Strict parse of the screen inputs. No field is defaulted: a blank or bad value is an issue. */
    fun validateInputs(state: ColumnUiState = _uiState.value): com.civileg.app.domain.validation.ValidationResult {
        // Only physical rules the engine itself enforces (InputGuard in designColumn):
        // positive width/depth/fcu/fy/height, non-negative pu/mx/my. No code range is invented.
        return com.civileg.app.domain.validation.FormValidator.validate(
            listOf(
                FieldSpec("width", state.width, FieldKind.POSITIVE),
                FieldSpec("depth", state.depth, FieldKind.POSITIVE),
                FieldSpec("fcu", state.fcu, FieldKind.POSITIVE),
                FieldSpec("fy", state.fy, FieldKind.POSITIVE),
                FieldSpec("axialLoad", state.axialLoad, FieldKind.NON_NEGATIVE),
                FieldSpec("height", state.height, FieldKind.POSITIVE),
                FieldSpec("mx", state.mx, FieldKind.NON_NEGATIVE),
                FieldSpec("my", state.my, FieldKind.NON_NEGATIVE),
                FieldSpec("preferredDiameter", state.preferredDiameter, FieldKind.POSITIVE_INT),
                // Custom rebar (auto-optimise off): the bar count is required, not guessed.
                FieldSpec("manualNumBars", state.manualNumBars, FieldKind.POSITIVE_INT, group = "manualRebar")
            ),
            activeGroups = if (state.autoOptimize) emptySet() else setOf("manualRebar")
        )
    }

    private fun calculate() {
        pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
        val state = _uiState.value
        val checked = validateInputs(state)
        if (!checked.isValid) {
            _uiState.update {
                // Keep the shown result only when every issue is on the custom-rebar card
                // (that card is part of the result view, so clearing it would hide the error).
                val rebarOnly = checked.issues.all { i -> i.fieldId == "manualNumBars" || i.fieldId == "preferredDiameter" }
                it.copy(
                    result = if (rebarOnly) it.result else null,
                    validationReport = if (rebarOnly) it.validationReport else null,
                    snapshot = if (rebarOnly) it.snapshot else null,
                    validation = checked, validationTick = it.validationTick + 1)
            }
            return
        }
        val w = checked.value("width")
        val d = checked.value("depth")
        val h = checked.value("height")
        val fcuVal = checked.value("fcu")
        val fyVal = checked.value("fy")
        val load = checked.value("axialLoad")
        val mxVal = checked.value("mx")
        val myVal = checked.value("my")
        val dia = checked.value("preferredDiameter").toInt()
        val manualBars = checked.valueOrNull("manualNumBars")?.toInt()
        val adjustedPu = load * state.loadCombination.getFactorForCode(state.designCode)
        val snap = ColumnCalcSnapshot(
            widthMm = w, depthMm = d, heightM = h, fcu = fcuVal, fy = fyVal,
            axialLoad = load, mx = mxVal, my = myVal,
            isSeismic = state.isSeismic, isCircular = state.isCircular,
            connectedSlab = state.connectedSlab, hasCap = state.hasCap,
            designCode = state.designCode, loadCombination = state.loadCombination,
            adjustedPu = adjustedPu,
            autoOptimize = state.autoOptimize, manualNumBars = manualBars, preferredDiameter = dia,
            rebarKey = state.rebarDraftKey
        )

        viewModelScope.launch {
            try {
                val res = calculatorEngine.designColumn(
                    width = w,
                    depth = d,
                    pu = adjustedPu,
                    mx = mxVal,
                    my = myVal,
                    fcu = fcuVal,
                    fy = fyVal,
                    code = mapDesignCode(state.designCode),
                    isCircular = state.isCircular,
                    connectedSlab = state.connectedSlab,
                    hasCap = state.hasCap,
                    clearHeight = h * 1000.0,
                    preferredDiameter = dia,
                    autoOptimize = state.autoOptimize,
                    manualNumBars = manualBars,
                    autoIncludeSelfWeight = true,
                    isSeismic = state.isSeismic
                )
                
                // Validate consistency & Dead Load (Axial logic)
                val report = CalculationValidator.validateColumn(res)
                val dlReport = CalculationValidator.inspectDeadLoadConsistency("COLUMN", mapOf("width" to w, "depth" to d, "height" to h), load)
                
                val combinedWarnings = report.warnings + dlReport.warnings
                _uiState.update { it.copy(result = res, validationReport = report.copy(warnings = combinedWarnings), errors = emptyList(), validation = null, snapshot = snap) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errors = listOf(e.message ?: "Error")) }
                _errorMessage.value = "Column calculation error / خطأ في حساب العمود: ${e.message}"
            }
        }
    }

    /** Standalone DXF sheet for the current column design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? {
        if (_uiState.value.resultIsStale) return null
        val res = _uiState.value.result ?: return null
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
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(),
            baseName = "Column",
            scope = viewModelScope,
            onComplete = onComplete
        )
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val state = _uiState.value
        if (state.resultIsStale) return  // rebar draft not yet adopted by Update
        val res = state.result ?: return
        val snap = state.snapshot ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                val fileName = "Column_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                // Use the inputs the shown result was solved with; no fallback values.
                val h = snap.heightM
                val fcuVal = snap.fcu
                val fyVal = snap.fy

                // Generate drawing for PDF
                // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                val isSpiralTies = res.stirrups.description.contains("SPIRAL", ignoreCase = true)
                val drawingBitmap = pendingDrawingBitmap ?: try {
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
                        sectionType = if (snap.isCircular) "Circular" else "Rectangular"
                    )
                } catch (e: Exception) { e.printStackTrace(); null }
                pendingDrawingBitmap = null  // consume after use

                val codeName = when(snap.designCode) {
                    com.civileg.app.domain.entities.DesignCode.ACI -> "ACI 318"
                    com.civileg.app.domain.entities.DesignCode.SBC -> "SBC 304"
                    else -> "ECP 203"
                }

                val inputsMap = mutableMapOf(
                    "Column Type" to res.columnType,
                    "Shape" to if (snap.isCircular) "Circular" else "Rectangular",
                    "Width" to "${res.width} mm",
                    "Depth" to "${res.depth} mm",
                    "Height" to "${h} m",
                    "Axial Load (input)" to "${String.format("%.1f", snap.axialLoad)} kN",
                    "Axial Load Pu (adjusted by combination)" to "${String.format("%.1f", snap.adjustedPu)} kN",
                    "Moment Mx" to "${snap.mx} kN.m",
                    "Moment My" to "${snap.my} kN.m",
                    "Seismic Confinement" to if (snap.isSeismic) "Yes" else "No",
                    "f'cu" to "${fcuVal} MPa",
                    "fy" to "${fyVal} MPa",
                    "Connected Slab" to snap.connectedSlab,
                    "Has Capital" to if (snap.hasCap) "Yes" else "No",
                    "Design Code" to codeName,
                    "Load Combination" to snap.loadCombination.name
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
                    titleAr = "????? ????? ????? - ${res.columnType}",
                    titleEn = "Column Design Report - ${res.columnType}",
                    subtitle = "Code: $codeName    ${res.width}x${res.depth}mm, H=${h}m",
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
                    drawing = buildDxfDrawing(),
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

    fun saveColumn(projectId: Long, name: String, result: CalculatorEngine.ColumnResult) {
        val snap = _uiState.value.snapshot
        if (snap == null || _uiState.value.resultIsStale) {
            _errorMessage.value = "Update the rebar first: the shown design is out of date / حدّث التسليح أولاً"
            return
        }
        viewModelScope.launch {
            try {
                repository.saveColumnDesign(projectId, name, result, snap.fcu, snap.fy)
            } catch (e: Exception) {
                _errorMessage.value = "Save failed / فشل الحفظ: ${e.message}"
            }
        }
    }

    private fun mapDesignCode(code: DesignCode): DesignCode = when(code) {
        DesignCode.ECP -> DesignCode.ECP
        DesignCode.ACI -> DesignCode.ACI
        DesignCode.SBC -> DesignCode.SBC
    }

    fun reset() {
        _uiState.value = ColumnUiState()
    }
}
