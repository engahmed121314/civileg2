package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.utils.StairFlightGeometry
import com.civileg.app.domain.calculations.utils.BbsGenerator
import com.civileg.app.domain.calculations.utils.CalculationValidator
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.shared.reporting.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.calculations.InputGuard
import javax.inject.Inject
import com.civileg.app.domain.calculations.base.StairSupportCondition

@HiltViewModel
class StairViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.StairResult?>()
    val result: LiveData<CalculatorEngine.StairResult?> = _result

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

    fun calculateStairPro(
        type: CalculatorEngine.StairType,
        span: Double,
        riser: Double,
        tread: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: DesignCode,
        stairWidthMm: Double = 1200.0,
        supportCondition: StairSupportCondition = StairSupportCondition.UNSPECIFIED,
        landingLengthMm: Double? = null, landingWidthMm: Double? = null, landingThicknessMm: Double? = null,
        manualMainDia: Int? = null, manualMainSpacing: Double? = null, manualTopDia: Int? = null, manualTopSpacing: Double? = null
    ) {
        InputGuard.positive("span", span)
        InputGuard.positive("riser", riser)
        InputGuard.positive("tread", tread)
        InputGuard.nonNegative("deadLoad", deadLoad)
        InputGuard.nonNegative("liveLoad", liveLoad)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("preferredDiameter", preferredDiameter)
        InputGuard.positive("stairWidthMm", stairWidthMm)

        viewModelScope.launch {
            _isLoading.value = true
            pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
            try {
                val res = calculatorEngine.designStaircase(
                    type = type,
                    span = span,
                    riser = riser,
                    tread = tread,
                    deadLoad = deadLoad,
                    liveLoad = liveLoad,
                    fcu = fcu,
                    fy = fy,
                    preferredDiameter = preferredDiameter,
                    code = code,
                    stairWidthMm = stairWidthMm,
                    supportCondition = supportCondition, landingLengthMm = landingLengthMm,
                    landingWidthMm = landingWidthMm, landingThicknessMm = landingThicknessMm,
                    manualMainDia = manualMainDia, manualMainSpacing = manualMainSpacing,
                    manualTopDia = manualTopDia, manualTopSpacing = manualTopSpacing
                )
                
                // Validate result for engineering consistency
                val report = CalculationValidator.validateStair(res)
                _validationReport.value = report
                
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                _errorMessage.value = "Calculation error / خطأ في الحساب: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Drop the current result (inputs changed or a new attempt started). */
    fun clearResult() {
        _result.value = null
        _validationReport.value = null
        _error.value = null
        pendingDrawingBitmap = null
    }

    // Fixed legacy support
    fun calculateStair(rise: Double, run: Double, width: Double, load: Double, fcu: Double, fy: Double, code: com.civileg.app.domain.entities.DesignCode = com.civileg.app.ui.defaultDesignCode()) {
        calculateStairPro(CalculatorEngine.StairType.SINGLE_FLIGHT, run, rise, 300.0, load * 0.6, load * 0.4, fcu, fy, 12, code, stairWidthMm = width)
    }

    fun saveStair(projectId: Long, name: String, result: CalculatorEngine.StairResult) {
        if (!com.civileg.app.utils.StairResultGate.isDesignUsable(result.analysisStatus)) return
        viewModelScope.launch {
            repository.saveStairDesign(projectId, name, result)
        }
    }

    /** Parses "Ø12 @ 150 mm c/c" into (diameter, spacing); (0, 0) when the text has no bar. */
    private fun barOf(text: String): Pair<Double, Double> {
        val m = Regex("Ø\\s*(\\d+(?:\\.\\d+)?)\\s*@\\s*(\\d+(?:\\.\\d+)?)").find(text) ?: return 0.0 to 0.0
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }

    /** Standalone DXF sheet for the current stair design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? {
        val currentResult = _result.value ?: return null
        if (!com.civileg.app.utils.StairResultGate.isDesignUsable(currentResult.analysisStatus)) return null
        // Same geometry as CalculatorEngine.designStaircase: span in metres, riser and tread in mm.
        val geometry = StairFlightGeometry.from(currentResult.span, currentResult.riser, currentResult.tread)
        val pdfCover = when (currentResult.code) {
            DesignCode.ACI -> 38.0
            DesignCode.SBC -> 40.0
            else -> 25.0
        }
        return com.civileg.app.utils.ElementDxfDrawings.stairSection(
            title = "STAIR ${currentResult.type.displayNameEn}",
            runMm = geometry.runMm,
            riseMm = currentResult.riser,
            steps = geometry.riserCount,
            treadCount = geometry.treadCount,
            waistThicknessMm = currentResult.thickness,
            coverMm = pdfCover,
            barDiaMm = currentResult.reinforcement.diameter.toDouble(),
            barSpacingMm = currentResult.reinforcement.spacing,
            distBarDiaMm = currentResult.distributionReinforcement.diameter.toDouble(),
            distBarSpacingMm = currentResult.distributionReinforcement.spacing,
            topBarDiaMm = barOf(currentResult.flightTopRebar).first,
            topBarSpacingMm = barOf(currentResult.flightTopRebar).second,
            landingBarDiaMm = barOf(currentResult.landingRebar).first,
            landingBarSpacingMm = barOf(currentResult.landingRebar).second,
            landingLengthMm = currentResult.landingLengthMm ?: 0.0,
            landingThicknessMm = currentResult.landingThicknessMm ?: 0.0,
            reviewNotes = listOf(
                "REVIEW ONLY - NOT A FABRICATION DETAIL",
                currentResult.documentaryBasisLabel,
                "Detailing layout after El-Leithy 2013 p.15, p.30 (practice, not a code requirement)",
                "Top bars: size from ECP 203-2025 Eq. 6-2, extension L/4 derived (code page 4-2-5 pending), hook length derived (code page pending)",
                "Landing bars: preliminary one-way strip, minimum steel ratio per ECP 6-2-1-3-2 item 1. Step bars per El-Leithy p.30, diameter not calculated",
                "Final steel / cutting lengths / support anchorage unavailable",
                currentResult.barMaterialClassification
            )
        )
    }

    /** Export just the CAD drawing (no PDF) for the current design. */
    fun exportToDxf(context: android.content.Context, onComplete: (java.io.File?) -> Unit = {}) {
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(),
            baseName = "Stair",
            scope = viewModelScope,
            onComplete = onComplete
        )
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val currentResult = _result.value ?: return
        if (!com.civileg.app.utils.StairResultGate.isDesignUsable(currentResult.analysisStatus)) { onComplete(null); return }
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val exportedFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                    val fileName = "Stair_Report_${System.currentTimeMillis()}.pdf"
                    val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?: context.cacheDir
                    directory.mkdirs()
                    val file = java.io.File(directory, fileName)

                    // Generate drawing for PDF
                    // Same geometry as CalculatorEngine.designStaircase: span is in metres, riser and tread in mm.
                    val geometry = StairFlightGeometry.from(currentResult.span, currentResult.riser, currentResult.tread)
                    val pdfCover = when(currentResult.code) {
                        DesignCode.ACI -> 38.0
                        DesignCode.SBC -> 40.0
                        else -> 25.0
                    }
                    // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                    val hasCapturedScreenDrawing = pendingDrawingBitmap != null
                    val drawingBitmap = pendingDrawingBitmap ?: try {
                        PdfDrawingGenerator.generateStairDrawing(
                            totalHeight = geometry.riseMm,
                            totalLength = geometry.runMm, // mm
                            stairWidth = currentResult.stairWidthMm,
                            riserHeight = currentResult.riser,
                            treadWidth = currentResult.tread,
                            slabThickness = currentResult.thickness,
                            mainDia = currentResult.reinforcement.diameter.toDouble(),
                            mainSpacing = currentResult.reinforcement.spacing,
                            distDia = currentResult.distributionReinforcement.diameter.toDouble(),
                            distSpacing = currentResult.distributionReinforcement.spacing,
                            cover = pdfCover
                        )
                    } catch (e: Exception) { e.printStackTrace(); null }
                    pendingDrawingBitmap = null  // consume after use

                    val codeName = currentResult.documentaryBasisLabel

                    val inputsMap = mapOf(
                        "Stair Type" to currentResult.type.displayNameEn,
                        "Span" to "${currentResult.span} m",
                        "Width" to "${currentResult.stairWidthMm} mm",
                        "Riser" to "${currentResult.riser} mm",
                        "Tread" to "${currentResult.tread} mm",
                        "Thickness" to "${currentResult.thickness} mm",
                        "f'cu" to "${currentResult.fcu} MPa",
                        "fy" to "${currentResult.fy} MPa",
                        "Bar material classification" to currentResult.barMaterialClassification,
                        "Factored Load wu" to "${String.format("%.2f", currentResult.wu)} kN/m²",
                        "Support condition" to currentResult.supportCondition.name,
                        "Landing length" to (currentResult.landingLengthMm?.let { "$it mm" } ?: "Not declared"),
                        "Landing width" to (currentResult.landingWidthMm?.let { "$it mm" } ?: "Not declared"),
                        "Landing thickness" to (currentResult.landingThicknessMm?.let { "$it mm" } ?: "Not declared"),
                        "Design Code" to codeName
                    )
                    val resultsMap = mapOf(
                        "Analysis status" to currentResult.analysisStatus.name,
                        "Code acceptance" to "INCOMPLETE: not an accepted construction design",
                        "Qualification notes" to currentResult.qualificationNotes.joinToString("; "),
                        "Top / landing steel" to "Not designed",
                        "Moment Mu" to "${String.format("%.2f", currentResult.mu)} kN.m",
                        "Main Reinforcement" to currentResult.reinforcement.barString,
                        "Distribution" to currentResult.distributionReinforcement.barString,
                        "Concrete Volume" to "${String.format("%.3f", currentResult.concreteVolume)} m³",
                        "Net flight steel only (not final)" to "${String.format("%.1f", currentResult.steelWeight)} kg",
                        "Quantity qualification" to "Final steel/BBS unavailable: excludes anchorage, bends, laps, top and landing steel",
                        "Flexure check ratio (${currentResult.flexureRatioBasis.ifEmpty { "n/a" }}; one check, not an overall utilization)" to (currentResult.flexureRatio?.let { "${String.format("%.0f", it * 100)}%" } ?: "Unavailable (no assessed flexure row)")
                    )
                    val safetyChecks = currentResult.safetyChecks.map { chk ->
                        GenericSafetyCheck(
                            name = chk.name, calculated = chk.value,
                            limit = chk.limit, unit = chk.unit, passed = chk.isSafe, notAssessed = chk.notAssessed
                        )
                    }

                    // Professional English PDF Report — English only, no Arabic encoding issues
                    val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                        titleAr = "تقرير تصميم سلم - ${currentResult.type.displayNameEn}",
                        titleEn = "Stair Design Report — ${currentResult.type.displayNameEn}",
                        subtitle = "Code: $codeName  •  Span=${currentResult.span}m",
                        designType = "Stair (${currentResult.type.displayNameEn})",
                        inputs = inputsMap,
                        results = resultsMap,
                        safetyChecks = safetyChecks,
                        isSafe = currentResult.isSafe && currentResult.codeAcceptanceComplete,
                        incompleteReview = !currentResult.codeAcceptanceComplete,
                        includeDiagnosticDrawing = hasCapturedScreenDrawing,
                        calculationSteps = currentResult.calculationSteps,
                        bbsEntries = BbsGenerator.generateStairBbs("ST1", currentResult, currentResult.stairWidthMm),
                        boqRows = BoqEstimator.stairRows(currentResult, currentResult.stairWidthMm / 1000.0, "ST1"),
                        drawingBitmap = drawingBitmap,
                        drawing = buildDxfDrawing(),
                        outputPath = file.absolutePath
                    ).pdfFile
                    generated
                }
                exportedFile?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                onComplete(exportedFile)
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
                _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message ?: "Unknown error"}"
                onComplete(null)
            } finally {
                _isExporting.value = false
            }
        }
    }
}

data class StairInputData(
    val rise: Double,
    val run: Double,
    val width: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double
)
