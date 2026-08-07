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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.sqrt

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
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Auto-design foundations for multiple pieces given soil area dimensions and total soil load.
     * Enhanced: computes grid layout, tributary areas, per-footing loads, and expansion.
     */
    fun autoDesignFromSoil(
        soilLengthM: Double,
        soilWidthM: Double,
        totalSoilLoadKN: Double,
        numberOfFootings: Int,
        fcu: Double,
        fy: Double,
        soilCapacity: Double,
        colWidth: Double,
        colDepth: Double,
        code: CalculatorEngine.DesignCode,
        preferredDiameter: Int = 16
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val soilArea = soilLengthM * soilWidthM
                if (soilArea <= 0 || numberOfFootings <= 0) {
                    _error.value = "Invalid soil area or footing count"
                    _isLoading.value = false
                    return@launch
                }

                // ── Compute grid layout ──
                val aspectRatio = soilLengthM / soilWidthM.coerceAtLeast(0.1)
                val gridCols = kotlin.math.ceil(sqrt(numberOfFootings.toDouble() * aspectRatio)).toInt().coerceIn(1, numberOfFootings)
                val gridRows = kotlin.math.ceil(numberOfFootings.toDouble() / gridCols).toInt().coerceIn(1, numberOfFootings)

                // ── Distribute loads with tributary factors ──
                // Interior footings carry more than corner/edge footings
                val results = mutableListOf<CalculatorEngine.FootingResult>()
                for (i in 0 until numberOfFootings) {
                    val row = i / gridCols
                    val col = i % gridCols
                    val isEdge = (row == 0 || row == gridRows - 1 || col == 0 || col == gridCols - 1)
                    val isCorner = (row == 0 || row == gridRows - 1) && (col == 0 || col == gridCols - 1)

                    // Tributary factor: interior=1.0, edge=0.85, corner=0.65
                    val tributaryFactor = when {
                        isCorner -> 0.65
                        isEdge -> 0.85
                        else -> 1.0
                    }

                    // Load per footing with self-weight estimate (10%)
                    val loadPerFooting = (totalSoilLoadKN / numberOfFootings) * tributaryFactor * 1.10

                    val res = calculatorEngine.calculateFooting(
                        type = CalculatorEngine.FootingType.ISOLATED,
                        p = loadPerFooting,
                        fcu = fcu,
                        fy = fy,
                        soil = soilCapacity,
                        colB = colWidth,
                        colT = colDepth,
                        code = code,
                        preferredDiameter = preferredDiameter,
                        preferredSpacing = 150.0
                    )
                    results.add(res)
                }

                // Return the largest (most critical) footing result
                val criticalResult = results.maxByOrNull { it.width * it.length } ?: results.first()
                _result.value = criticalResult
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Auto-design error: ${e.message}"
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
                val drawingBitmap = try {
                    PdfDrawingGenerator.generateFootingDrawing(
                        footingLX = res.width,
                        footingLY = res.length,
                        footingThickness = res.thickness,
                        colW = res.column1Size.first,
                        colD = res.column1Size.second,
                        rebarXCount = res.barsX,
                        rebarXDia = res.barDiameter.toDouble(),
                        rebarXSpacing = res.reinforcementBottom.spacing,
                        rebarYCount = res.barsY,
                        rebarYDia = res.barDiameter.toDouble(),
                        rebarYSpatial = res.reinforcementBottom.spacing
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

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
                    "Design Code" to codeName
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
                    safetyChecks = emptyList(),
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
