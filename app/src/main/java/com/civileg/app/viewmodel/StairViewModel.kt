package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculationValidator
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.CalculationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
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
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
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
                
                // Generic validation
                val report = CalculationValidator.validate(res)
                _validationReport.value = report
                
                _result.value = res

                // Validate result for engineering consistency
                val report = CalculationValidator.validateStair(res)
                _validationReport.value = report

                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Fixed legacy support
    fun calculateStair(rise: Double, run: Double, width: Double, load: Double, fcu: Double, fy: Double) {
        calculateStairPro(CalculatorEngine.StairType.SINGLE_FLIGHT, run, rise, 300.0, load * 0.6, load * 0.4, fcu, fy, 12, CalculatorEngine.DesignCode.EGYPTIAN)
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
                    // CRITICAL FIX (2026-07-27): Use NativePdfExporter (Android-native)
                    val fileName = "Stair_Report_${System.currentTimeMillis()}.pdf"
                    val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?: context.cacheDir
                    directory.mkdirs()
                    val file = java.io.File(directory, fileName)

                    // Generate drawing for PDF
                    val totalHeight = currentResult.span * (currentResult.riser / currentResult.tread)
                    val nRisers = (totalHeight / currentResult.riser).toInt().coerceAtLeast(1)
                    val pdfCover = when(currentResult.code) {
                        CalculatorEngine.DesignCode.ACI -> 38.0
                        CalculatorEngine.DesignCode.SAUDI -> 40.0
                        else -> 25.0
                    }
                    // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                    val drawingBitmap = pendingDrawingBitmap ?: try {
                        PdfDrawingGenerator.generateStairDrawing(
                            totalHeight = totalHeight,
                            totalLength = nRisers.toDouble() * currentResult.tread,
                            stairWidth = 1200.0,
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
                        CalculatorEngine.DesignCode.ACI -> "ACI 318"
                        CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
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
                        com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                            name = chk.name, calculated = chk.value,
                            limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                        )
                    }

                    // Professional English PDF Report — English only, no Arabic encoding issues
                    com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                        titleAr = "تقرير تصميم سلم - ${currentResult.type.displayName}",
                        titleEn = "Stair Design Report — ${currentResult.type.displayName}",
                        subtitle = "Code: $codeName  •  Span=${currentResult.span}m",
                        designType = "Stair (${currentResult.type.displayName})",
                        inputs = inputsMap,
                        results = resultsMap,
                        safetyChecks = safetyChecks,
                        isSafe = currentResult.isSafe,
                        drawingBitmap = drawingBitmap,
                        outputPath = file.absolutePath
                    )
                }
                exportedFile?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                onComplete(exportedFile)
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
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
