package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Bitmap
import javax.inject.Inject

@HiltViewModel
class TankViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.TankResult?>()
    val result: LiveData<CalculatorEngine.TankResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    fun calculateTankPro(
        type: CalculatorEngine.TankType,
        capacity: Double,
        height: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designTank(type, capacity, height, fcu, fy, preferredDiameter, code)
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Legacy method
    fun calculateTank(type: CalculatorEngine.TankType, capacity: Double, height: Double, fcu: Double, fy: Double) {
        calculateTankPro(type, capacity, height, fcu, fy, 12, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveTank(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        viewModelScope.launch {
            repository.saveTankDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // CRITICAL FIX (2026-07-27): Use NativePdfExporter (Android-native)
                val fileName = "Tank_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                // Use captured Compose drawing bitmap if available, otherwise fallback to PdfDrawingGenerator
                val drawingBitmap = pendingDrawingBitmap ?: try {
                    PdfDrawingGenerator.generateTankDrawing(
                        tankType = res.type.displayName,
                        length = res.length,
                        width = res.width,
                        height = res.height,
                        wallThickness = res.wallThickness,
                        baseThickness = res.baseThickness,
                        verticalRebarDia = res.wallReinforcement.diameter.toDouble(),
                        verticalRebarSpacing = res.wallReinforcement.spacing.toDouble(),
                        horizontalRebarDia = res.baseReinforcement.diameter.toDouble(),
                        horizontalRebarSpacing = res.baseReinforcement.spacing.toDouble(),
                        waterLevel = res.height * 0.85,
                        foundationDepth = if (res.type == CalculatorEngine.TankType.UNDERGROUND || res.type == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND) res.height * 0.3 else 0.0
                    )
                } catch (e: Exception) { null }
                pendingDrawingBitmap = null  // consume after use

                val codeName = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> "ACI 318"
                    CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
                    else -> "ECP 203"
                }
                val inputsMap = mapOf(
                    "Tank Type" to res.type.displayName,
                    "Length" to "${res.length} m",
                    "Width" to "${res.width} m",
                    "Height" to "${res.height} m",
                    "Wall Thickness" to "${res.wallThickness} mm",
                    "Base Thickness" to "${res.baseThickness} mm",
                    "Design Code" to codeName
                )
                val resultsMap = mapOf(
                    "Capacity" to "${String.format("%.2f", res.capacity)} m³",
                    "Water Pressure" to "${String.format("%.2f", res.waterPressure)} kN/m²",
                    "Wall Reinforcement" to res.wallReinforcement.barString,
                    "Base Reinforcement" to res.baseReinforcement.barString,
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel Weight" to "${String.format("%.1f", res.steelWeight)} kg"
                )
                val safetyChecks = res.safetyChecks.map { chk ->
                    com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = chk.name, calculated = chk.value,
                        limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                    )
                }

                // Professional English PDF Report — English only, no Arabic encoding issues
                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم خزان - ${res.type.displayName}",
                    titleEn = "Tank Design Report — ${res.type.displayName}",
                    subtitle = "Code: $codeName  •  ${res.length}×${res.width}×${res.height}m",
                    designType = "Tank (${res.type.displayName})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF Export Error: ${e.message}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
