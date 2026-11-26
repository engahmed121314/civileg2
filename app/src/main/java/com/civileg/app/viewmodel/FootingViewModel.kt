package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.utils.BoqEstimator
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.shared.reporting.PdfDrawingGenerator
import com.civileg.app.domain.calculations.utils.CalculationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Bitmap
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.calculations.InputGuard
import javax.inject.Inject

@HiltViewModel
class FootingViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.FootingResult?>()
    val result: LiveData<CalculatorEngine.FootingResult?> = _result

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

    // Store actual inputs for save
    /** Everything the shown result was solved with. BOQ, PDF and save read this, never live UI fields. */
    data class FootingSolvedInputs(
        val type: CalculatorEngine.FootingType,
        val pu: Double, val pu2: Double, val distanceMm: Double,
        val fcu: Double, val fy: Double, val soil: Double,
        val colB: Double, val colT: Double,
        val code: DesignCode,
        val preferredDiameter: Int, val preferredSpacing: Double,
        val maxLeftMm: Double?, val maxRightMm: Double?,
        val excavationDepthM: Double, val workingSpaceM: Double, val blindingThicknessM: Double
    )
    private val _solvedInputs = MutableLiveData<FootingSolvedInputs?>()
    val solvedInputs: LiveData<FootingSolvedInputs?> = _solvedInputs

    fun calculateFooting(
        type: CalculatorEngine.FootingType,
        p: Double,
        fcu: Double,
        fy: Double,
        soil: Double,
        colB: Double,
        colT: Double,
        code: DesignCode,
        preferredDiameter: Int,
        preferredSpacing: Double,
        p2: Double,
        distance: Double,
        maxLeft: Double?,
        maxRight: Double?,
        excavationDepthM: Double,
        workingSpaceM: Double,
        blindingThicknessM: Double
    ) {
        InputGuard.positive("p", p)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("soil", soil)
        InputGuard.positive("colB", colB)
        InputGuard.positive("colT", colT)
        InputGuard.positive("preferredDiameter", preferredDiameter)
        InputGuard.positive("preferredSpacing", preferredSpacing)
        InputGuard.nonNegative("excavationDepthM", excavationDepthM)
        InputGuard.nonNegative("workingSpaceM", workingSpaceM)
        InputGuard.nonNegative("blindingThicknessM", blindingThicknessM)

        viewModelScope.launch {
            _isLoading.value = true
            pendingDrawingBitmap = null  // Reset stale bitmap from previous calculation
            try {
                val res = calculatorEngine.calculateFooting(
                    type = type,
                    p = p,
                    fcu = fcu,
                    fy = fy,
                    soil = soil,
                    colB = colB,
                    colT = colT,
                    code = code,
                    preferredDiameter = preferredDiameter,
                    preferredSpacing = preferredSpacing,
                    p2 = p2,
                    distance = distance,
                    maxLeft = maxLeft,
                    maxRight = maxRight
                )
                
                // Validate Footing
                val report = CalculationValidator.validate(res)
                val dlReport = CalculationValidator.inspectDeadLoadConsistency("FOOTING", mapOf("width" to colB, "depth" to colT), p)
                
                val combinedWarnings = report.warnings + dlReport.warnings
                _validationReport.value = report.copy(warnings = combinedWarnings)
                
                _solvedInputs.value = FootingSolvedInputs(
                    type, p, p2, distance, fcu, fy, soil, colB, colT, code,
                    preferredDiameter, preferredSpacing, maxLeft, maxRight,
                    excavationDepthM, workingSpaceM, blindingThicknessM
                )
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
        _solvedInputs.value = null
        pendingDrawingBitmap = null
    }

    fun saveFooting(projectId: Long, name: String, result: CalculatorEngine.FootingResult) {
        val solved = _solvedInputs.value
        if (solved == null) {
            _errorMessage.value = "No solved inputs for this result; design again before saving / أعد التصميم قبل الحفظ"
            return
        }
        viewModelScope.launch {
            repository.saveFootingDesign(projectId, name, result, solved.fcu, solved.fy)
        }
    }

    /** Standalone DXF sheet for the current footing design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? {
        val res = _result.value ?: return null
        return com.civileg.app.utils.ElementDxfDrawings.footingSection(
            title = "FOOTING ${res.type.displayNameEn}",
            widthMm = res.width.toDouble(),
            thicknessMm = res.thickness.toDouble(),
            coverMm = res.coverMm,
            barDiaMm = res.barDiameter.toDouble(),
            barSpacingMm = res.reinforcementBottom.spacing.toDouble(),
            columnWidthMm = res.column1Size.first.toDouble()
        )
    }

    /** Export just the CAD drawing (no PDF) for the current design. */
    fun exportToDxf(context: Context, onComplete: (File?) -> Unit = {}) {
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(),
            baseName = "Footing",
            scope = viewModelScope,
            onComplete = onComplete
        )
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        val solved = _solvedInputs.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                // PDF + DXF via ExportManager.exportReportAndDxf (iText, EN-only per ADR-005/009).
                val fileName = "Footing_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                // Generate drawing for PDF
                // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                val drawingBitmap = pendingDrawingBitmap ?: try {
                    PdfDrawingGenerator.generateFootingDrawing(
                        footingLX = res.length,
                        footingLY = res.width,
                        footingThickness = res.thickness,
                        colW = res.column1Size.second,
                        colD = res.column1Size.first,
                        rebarXCount = res.barsX,
                        rebarXDia = res.barDiameter.toDouble(),
                        rebarXSpacing = res.reinforcementBottom.spacing,
                        rebarYCount = res.barsY,
                        rebarYDia = res.barDiameter.toDouble(),
                        rebarYSpatial = res.reinforcementBottom.spacing,
                        footingType = res.type.displayName,
                        cover = res.coverMm,
                        soilPressureMax = res.soilPressure,
                        soilPressureMin = res.soilPressure
                    )
                } catch (e: Exception) { e.printStackTrace(); null }
                pendingDrawingBitmap = null  // consume after use

                val codeName = when(res.code) {
                    DesignCode.ACI -> "ACI 318"
                    DesignCode.SBC -> "SBC 304"
                    else -> "ECP 203"
                }
                val inputsMap = mapOf(
                    "Footing Type" to res.type.displayNameEn, // the English-only PDF printed a blank for the Arabic name
                    "Width" to "${res.width} mm",
                    "Length" to "${res.length} mm",
                    "Thickness" to "${res.thickness} mm",
                    "Column Size" to "${res.column1Size.first}×${res.column1Size.second} mm",
                    "Soil Pressure" to "${String.format("%.2f", res.soilPressure)} kN/m²",
                    "Allowable Pressure" to "${String.format("%.2f", res.allowablePressure)} kN/m²",
                    "Design Code" to codeName,
                    "fcu" to "${solved.fcu} MPa",
                    "fy" to "${solved.fy} MPa",
                    "Excavation Depth" to "${solved.excavationDepthM} m",
                    "Working Space" to "${solved.workingSpaceM} m",
                    "Blinding Thickness" to "${solved.blindingThicknessM} m",
                    // Entered load from the solved snapshot. The old line printed soil pressure x area,
                    // a derived service load, under the label "Applied Load".
                    "Entered axial load Pu" to "${String.format("%.1f", solved.pu)} kN"
                )
                val resultsMap = mapOf(
                    "Reinforcement" to res.reinforcementBottom.barString,
                    "Bars X" to "${res.barsX} Ø${res.barDiameter}",
                    "Bars Y" to "${res.barsY} Ø${res.barDiameter}",
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel Weight (bars x length, no laps or hooks)" to "${String.format("%.1f", res.steelWeight)} kg",
                    // Efficiency and Optimal were unassessed defaults (100% / Yes) printed beside an UNSAFE verdict
                )
                // Professional English PDF Report — English only, no Arabic encoding issues
                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم قاعدة - ${res.type.displayName}",
                    titleEn = "Footing Design Report — ${res.type.displayName}",
                    subtitle = "Code: $codeName  •  ${res.width}×${res.length}×${res.thickness}mm",
                    designType = "Footing (${res.type.displayName})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = res.safetyChecks.map {
                        GenericSafetyCheck(
                            it.name, it.value, it.limit, it.unit, it.isSafe
                        )
                    },
                    isSafe = res.isSafe,
                    calculationSteps = res.calculationSteps,
                    bbsEntries = runCatching {
                        com.civileg.app.domain.calculations.utils.BbsGenerator.generateFootingBbs("F1", res)
                    }.getOrDefault(emptyList()),
                    boqRows = BoqEstimator.footingRows(res, "F1", solved.excavationDepthM, solved.workingSpaceM, solved.blindingThicknessM),
                    drawingBitmap = drawingBitmap,
                    drawing = buildDxfDrawing(),
                    outputPath = file.absolutePath
                ).pdfFile

                withContext(Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: ""}"
                    _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message ?: ""}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
