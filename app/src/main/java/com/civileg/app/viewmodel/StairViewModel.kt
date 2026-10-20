package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.BbsGenerator
import com.civileg.app.utils.CalculationValidator
import com.civileg.app.utils.BoqEstimator
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.civileg.app.utils.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.calculations.InputGuard
import javax.inject.Inject

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

    /** Flight width (mm) captured with the last calculation; used by the fallback drawing. */
    @Volatile
    private var lastStairWidthMm: Double = 1200.0

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
        stairWidthMm: Double = 1200.0
    ) {
        InputGuard.positive("span", span)
        InputGuard.positive("riser", riser)
        InputGuard.positive("tread", tread)
        InputGuard.nonNegative("deadLoad", deadLoad)
        InputGuard.nonNegative("liveLoad", liveLoad)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("preferredDiameter", preferredDiameter)

        viewModelScope.launch {
            _isLoading.value = true
            lastStairWidthMm = stairWidthMm.coerceAtLeast(300.0)
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
                    code = code
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

    // Fixed legacy support
    fun calculateStair(rise: Double, run: Double, width: Double, load: Double, fcu: Double, fy: Double) {
        calculateStairPro(CalculatorEngine.StairType.SINGLE_FLIGHT, run, rise, 300.0, load * 0.6, load * 0.4, fcu, fy, 12, DesignCode.ECP, stairWidthMm = width)
    }

    fun saveStair(projectId: Long, name: String, result: CalculatorEngine.StairResult) {
        viewModelScope.launch {
            repository.saveStairDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val currentResult = _result.value ?: return
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
                    val totalHeight = currentResult.span * (currentResult.riser / currentResult.tread)
                    val nRisers = (totalHeight / currentResult.riser).toInt().coerceAtLeast(1)
                    val pdfCover = when(currentResult.code) {
                        DesignCode.ACI -> 38.0
                        DesignCode.SBC -> 40.0
                        else -> 25.0
                    }
                    // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                    val drawingBitmap = pendingDrawingBitmap ?: try {
                        PdfDrawingGenerator.generateStairDrawing(
                            totalHeight = totalHeight,
                            totalLength = nRisers.toDouble() * currentResult.tread,
                            stairWidth = lastStairWidthMm,
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

                    val codeName = when(currentResult.code) {
                        DesignCode.ACI -> "ACI 318"
                        DesignCode.SBC -> "SBC 304"
                        else -> "ECP 203"
                    }
                    val inputsMap = mapOf(
                        "Stair Type" to currentResult.type.displayName,
                        "Span" to "${currentResult.span} m",
                        "Riser" to "${currentResult.riser} mm",
                        "Tread" to "${currentResult.tread} mm",
                        "Thickness" to "${currentResult.thickness} mm",
                        "f'cu" to "${currentResult.fcu} MPa",
                        "fy" to "${currentResult.fy} MPa",
                        "Factored Load wu" to "${String.format("%.2f", currentResult.wu)} kN/m²",
                        "Design Code" to codeName
                    )
                    val resultsMap = mapOf(
                        "Moment Mu" to "${String.format("%.2f", currentResult.mu)} kN.m",
                        "Main Reinforcement" to currentResult.reinforcement.barString,
                        "Distribution" to currentResult.distributionReinforcement.barString,
                        "Concrete Volume" to "${String.format("%.3f", currentResult.concreteVolume)} m³",
                        "Steel Weight" to "${String.format("%.1f", currentResult.steelWeight)} kg",
                        "Utilization" to "${(currentResult.utilizationRatio * 100).toInt()}%"
                    )
                    val safetyChecks = currentResult.safetyChecks.map { chk ->
                        GenericSafetyCheck(
                            name = chk.name, calculated = chk.value,
                            limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                        )
                    }

                    // Professional English PDF Report — English only, no Arabic encoding issues
                    val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                        titleAr = "تقرير تصميم سلم - ${currentResult.type.displayName}",
                        titleEn = "Stair Design Report — ${currentResult.type.displayName}",
                        subtitle = "Code: $codeName  •  Span=${currentResult.span}m",
                        designType = "Stair (${currentResult.type.displayName})",
                        inputs = inputsMap,
                        results = resultsMap,
                        safetyChecks = safetyChecks,
                        isSafe = currentResult.isSafe,
                        calculationSteps = currentResult.calculationSteps,
                        bbsEntries = runCatching {
                            BbsGenerator.generateStairBbs("ST1", currentResult, lastStairWidthMm)
                        }.getOrDefault(emptyList()),
                        boqRows = BoqEstimator.stairRows(currentResult, lastStairWidthMm / 1000.0, "ST1"),
                        drawingBitmap = drawingBitmap,
                        drawing = com.civileg.app.utils.ElementDxfDrawings.stairSection(
                            title = "STAIR ${currentResult.type.displayName}",
                            runMm = nRisers.toDouble() * currentResult.tread,
                            riseMm = currentResult.riser,
                            steps = nRisers,
                            waistThicknessMm = currentResult.thickness,
                            coverMm = pdfCover,
                            barDiaMm = currentResult.reinforcement.diameter.toDouble(),
                            barSpacingMm = currentResult.reinforcement.spacing
                        ),
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
