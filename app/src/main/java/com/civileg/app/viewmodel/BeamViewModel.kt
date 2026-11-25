package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.shared.reporting.PdfDrawingGenerator
import com.civileg.app.domain.calculations.utils.CalculationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.calculations.InputGuard
import javax.inject.Inject

@HiltViewModel
class BeamViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.BeamResult?>()
    val result: LiveData<CalculatorEngine.BeamResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    private var lastSpan: Double = 5.0
    private var lastWidth: Double = 250.0
    private var lastHeight: Double = 600.0
    private var lastDeadLoad: Double = 15.0
    private var lastLiveLoad: Double = 10.0
    private var lastFcu: Double = 25.0
    private var lastFy: Double = 360.0
    val solvedFcu: Double get() = lastFcu
    val solvedFy: Double get() = lastFy

    private var lastSupportType: CalculatorEngine.SupportType = CalculatorEngine.SupportType.HINGED_HINGED

    fun clearResult() {
        _result.value = null
        _validationReport.value = null
        _error.value = null
        pendingDrawingBitmap = null
    }

    fun calculateBeamPro(
        width: Double,
        height: Double,
        span: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: DesignCode,
        supportType: CalculatorEngine.SupportType
    ) {
        InputGuard.positive("width", width)
        InputGuard.positive("height", height)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("deadLoad", deadLoad)
        InputGuard.nonNegative("liveLoad", liveLoad)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("preferredDiameter", preferredDiameter)

        viewModelScope.launch {
            _isLoading.value = true
            _result.value = null
            pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
            try {
                // Store all inputs for PDF export
                lastWidth = width
                lastHeight = height
                lastSpan = span
                lastDeadLoad = deadLoad
                lastLiveLoad = liveLoad
                lastFcu = fcu
                lastFy = fy
                lastSupportType = supportType

                val res = calculatorEngine.designBeam(
                    width = width,
                    height = height,
                    span = span,
                    fcu = fcu,
                    fy = fy,
                    deadLoad = deadLoad,
                    liveLoad = liveLoad,
                    preferredDiameter = preferredDiameter,
                    code = code,
                    supportType = supportType,
                    autoIncludeSelfWeight = true
                )
                
                // Validate results for consistency & dead load logic
                val report = CalculationValidator.validateBeam(res)
                val dlReport = CalculationValidator.inspectDeadLoadConsistency("BEAM", mapOf("width" to width, "depth" to height), deadLoad)
                
                val combinedWarnings = report.warnings + dlReport.warnings
                _validationReport.value = report.copy(warnings = combinedWarnings)
                
                _result.value = res
                lastSpan = span
                _error.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                _errorMessage.value = "Calculation error / خطأ في الحساب: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculateBeam(span: Double, load: Double, fcu: Double, fy: Double, code: com.civileg.app.domain.entities.DesignCode = com.civileg.app.ui.defaultDesignCode()) {
        calculateBeamPro(250.0, 600.0, span, load * 0.6, load * 0.4, fcu, fy, 16, code, CalculatorEngine.SupportType.HINGED_HINGED)
    }

    fun saveBeam(projectId: Long, name: String, result: CalculatorEngine.BeamResult) {
        viewModelScope.launch {
            try {
                repository.saveBeamDesign(projectId, name, result, lastSpan, lastFcu, lastFy)
            } catch (e: Exception) {
                _errorMessage.value = "Save failed / فشل الحفظ: ${e.message}"
            }
        }
    }

    /** Standalone DXF sheet for the current beam design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? {
        val res = _result.value ?: return null
        if (!res.codeAcceptanceComplete) return null
        val section = com.civileg.app.utils.ElementDxfDrawings.beamSection(
            title = "BEAM ${lastSupportType.displayName}",
            widthMm = res.width.toDouble(),
            depthMm = res.depth.toDouble(),
            coverMm = 50.0,
            mainBarDiaMm = res.reinforcementBottom.diameter.toDouble(),
            mainBarCount = res.reinforcementBottom.numBars,
            stirrupDiaMm = res.stirrups.diameter.toDouble(),
            stirrupSpacingMm = res.stirrups.spacing.toDouble()
        )
        // The beam BBS is not issued (no verified cutting-length basis); say so inside the DXF itself.
        return com.civileg.app.utils.DxfDrawable { exporter ->
            section.exportToDxf(exporter)
            exporter.addText(0.0, -2500.0, "BAR BENDING SCHEDULE NOT ISSUED - cutting lengths (hooks, anchorage, laps, bends) have no verified code basis", 120.0, "0_BBS_TABLE")
            exporter.addText(0.0, -2750.0, "Section shows bar counts, diameters and stirrup spacing only. Not a complete reinforcement detail.", 100.0, "0_BBS_TABLE")
        }
    }

    /** Export just the CAD drawing (no PDF) for the current design. */
    fun exportToDxf(context: android.content.Context, onComplete: (java.io.File?) -> Unit = {}) {
        if (_result.value?.codeAcceptanceComplete != true) {
            _errorMessage.value = "Export pending: complete the listed case/code checks before issuing drawings or BBS."
            onComplete(null)
            return
        }
        val res = _result.value
        val spanMm = ((if (res != null && res.span > 0) res.span else lastSpan) * 1000.0)
        val bbs = if (res != null) {
            runCatching { com.civileg.app.domain.calculations.utils.BbsGenerator.generateBeamBbs("B1", res, spanMm) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(),
            baseName = "Beam_${lastSupportType.displayName}",
            scope = viewModelScope,
            bbsEntries = bbs,
            onComplete = onComplete
        )
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val res = _result.value ?: return
        if (!res.codeAcceptanceComplete) {
            _errorMessage.value = "Export pending: complete the listed case/code checks before issuing drawings or BBS."
            onComplete(null)
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                // The old NativePdfExporter (android.graphics.pdf + Canvas) was removed:
                // it can SIGSEGV inside Skia with no catchable exception.
                val fileName = "Beam_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                // Same solved-case UDL equilibrium samples as the design screen.
                val maxM = res.appliedMoment
                val maxV = res.appliedShear
                val diagram = com.civileg.app.domain.calculations.utils.BeamUdlDiagram.samples(res.span,res.appliedShear,res.supportType)
                val momentPoints = diagram.map { it.xM to it.momentKnM }
                val shearPoints = diagram.map { it.xM to it.shearKn }

                // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                val drawingBitmap = pendingDrawingBitmap ?: try {
                    PdfDrawingGenerator.generateBeamDrawingWithDiagrams(
                        beamWidth = res.width.toDouble(),
                        beamDepth = res.depth.toDouble(),
                        span = lastSpan * 1000.0,
                        mainRebarDia = res.reinforcementBottom.diameter.toDouble(),
                        mainRebarCount = res.reinforcementBottom.numBars,
                        stirrupDia = res.stirrups.diameter.toDouble(),
                        stirrupSpacing = res.stirrups.spacing.toDouble(),
                        cover = 50.0,
                        hasTopSteel = res.reinforcementTop.numBars > 0,
                        topRebarDia = res.reinforcementTop.diameter.toDouble(),
                        topRebarCount = res.reinforcementTop.numBars,
                        momentPoints = momentPoints,
                        shearPoints = shearPoints,
                        maxMoment = maxM,
                        maxShear = maxV,
                        isSafe = res.isSafe
                    )
                } catch (e: Exception) { e.printStackTrace(); null }
                pendingDrawingBitmap = null  // consume after use

                val codeName = when(res.code) {
                    DesignCode.ACI -> "ACI 318"
                    DesignCode.SBC -> "SBC 304"
                    else -> "ECP 203"
                }

                val inputsMap = mapOf(
                    "Span" to "${lastSpan} m",
                    "Width" to "${res.width} mm",
                    "Depth" to "${res.depth} mm",
                    "Support" to lastSupportType.displayName,
                    "f'cu" to "${lastFcu} MPa",
                    "fy" to "${lastFy} MPa",
                    "Dead Load" to "${lastDeadLoad} kN/m",
                    "Live Load" to "${lastLiveLoad} kN/m",
                    "Design Code" to codeName
                )
                val resultsMap = mutableMapOf(
                    "Max Moment Mu" to "${String.format("%.2f", res.mu)} kN.m",
                    "Max Shear Vu" to "${String.format("%.2f", res.vu)} kN",
                    "Moment Capacity" to "${String.format("%.1f", res.momentCapacity)} kN.m",
                    "Shear Capacity" to "${String.format("%.1f", res.shearCapacity)} kN",
                    "Neutral Axis Depth" to "${String.format("%.1f", res.neutralAxisDepth)} mm",
                    "Steel Ratio" to String.format("%.4f", res.steelRatio),
                    "Bottom Reinforcement" to res.reinforcementBottom.barString,
                    "Top Reinforcement" to res.reinforcementTop.barString,
                    "Stirrups" to res.stirrups.description,
                    "Deflection" to "${String.format("%.2f", res.deflection)} mm",
                    "Allowable Deflection" to "${String.format("%.2f", res.allowableDeflection)} mm",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%",
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel Weight (rough estimate, not for procurement: no hooks, anchorage, laps or bends; uniform stirrups; full-span top steel)" to "${String.format("%.1f", res.steelWeight)} kg",
                    "Bar Bending Schedule" to com.civileg.app.domain.calculations.utils.BbsGenerator.BEAM_BBS_UNAVAILABLE_REASON
                )
                
                if (res.stirrups.zones.isNotEmpty()) {
                    res.stirrups.zones.forEachIndexed { i, zone ->
                         resultsMap["Distribution Zone ${i+1}"] = "${zone.name}: ${zone.description} [${String.format("%.1f", (zone.endLocation-zone.startLocation)/1000.0)}m]"
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
                    titleAr = "????? ????? ????? - ${lastSupportType.displayName}",
                    titleEn = "Beam Design Report - ${lastSupportType.displayName}",
                    subtitle = "Code: $codeName    Span=${lastSpan}m, ${res.width}x${res.depth}mm",
                    designType = "Beam (${lastSupportType.displayName})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    calculationSteps = res.calculationSteps,
                    bbsEntries = runCatching {
                        com.civileg.app.domain.calculations.utils.BbsGenerator.generateBeamBbs("B1", res, res.span * 1000.0)
                    }.getOrDefault(emptyList()),
                    boqRows = BoqEstimator.beamRows(res, "B1"),
                    drawingBitmap = drawingBitmap,
                    drawing = buildDxfDrawing(),
                    outputPath = file.absolutePath
                ).pdfFile

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
                    _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message ?: "Unknown error"}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
