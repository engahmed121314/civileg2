package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.CalculationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Bitmap
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
    
    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    fun calculateFooting(
        type: CalculatorEngine.FootingType,
        p: Double,
        fcu: Double,
        fy: Double,
        soil: Double,
        colB: Double,
        colT: Double,
        code: CalculatorEngine.DesignCode,
        preferredDiameter: Int,
        preferredSpacing: Double,
        p2: Double = 0.0,
        distance: Double = 0.0,
        maxLeft: Double? = null,
        maxRight: Double? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
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
                val dlReport = CalculationValidator.inspectDeadLoadConsistency("COLUMN", mapOf("width" to colB, "depth" to colT), p)
                _validationReport.value = dlReport
                
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveFooting(projectId: Long, name: String, result: CalculatorEngine.FootingResult) {
        viewModelScope.launch {
            repository.saveFootingDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                // CRITICAL FIX (2026-07-27): Use NativePdfExporter (Android-native)
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
                        cover = 70.0,
                        soilPressureMax = res.soilPressure,
                        soilPressureMin = res.soilPressure
                    )
                } catch (e: Exception) { e.printStackTrace(); null }
                pendingDrawingBitmap = null  // consume after use

                val codeName = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> "ACI 318"
                    CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
                    else -> "ECP 203"
                }
                val inputsMap = mapOf(
                    "Footing Type" to res.type.displayName,
                    "Width" to "${res.width} mm",
                    "Length" to "${res.length} mm",
                    "Thickness" to "${res.thickness} mm",
                    "Column Size" to "${res.column1Size.first}×${res.column1Size.second} mm",
                    "Soil Pressure" to "${String.format("%.2f", res.soilPressure)} kN/m²",
                    "Allowable Pressure" to "${String.format("%.2f", res.allowablePressure)} kN/m²",
                    "Design Code" to codeName,
                    "Applied Load" to "${String.format("%.1f", res.soilPressure * res.width * res.length / 1e6)} kN"
                )
                val resultsMap = mapOf(
                    "Reinforcement" to res.reinforcementBottom.barString,
                    "Bars X" to "${res.barsX} Ø${res.barDiameter}",
                    "Bars Y" to "${res.barsY} Ø${res.barDiameter}",
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel Weight" to "${String.format("%.1f", res.steelWeight)} kg",
                    "Efficiency" to "${String.format("%.0f", res.efficiencyScore)}%",
                    "Optimal" to if (res.isOptimal) "Yes" else "No"
                )
                // Professional English PDF Report — English only, no Arabic encoding issues
                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم قاعدة - ${res.type.displayName}",
                    titleEn = "Footing Design Report — ${res.type.displayName}",
                    subtitle = "Code: $codeName  •  ${res.width}×${res.length}×${res.thickness}mm",
                    designType = "Footing (${res.type.displayName})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = res.safetyChecks.map {
                        com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                            it.name, it.value, it.limit, it.unit, it.isSafe
                        )
                    },
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath
                )

                withContext(Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: ""}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
