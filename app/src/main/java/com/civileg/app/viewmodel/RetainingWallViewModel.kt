package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RetainingWallViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.RetainingWallResult?>()
    val result: LiveData<CalculatorEngine.RetainingWallResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun calculateRetainingWallPro(
        height: Double,
        soilDensity: Double,
        frictionAngle: Double,
        surcharge: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designRetainingWall(
                    height = height,
                    soilDensity = soilDensity,
                    frictionAngle = frictionAngle,
                    surcharge = surcharge,
                    fcu = fcu,
                    fy = fy,
                    preferredDiameter = preferredDiameter,
                    code = code
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

    // Legacy method for backward compatibility
    fun calculateRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double) {
        calculateRetainingWallPro(height, soilDensity, frictionAngle, surcharge, fcu, fy, 16, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveRetainingWall(projectId: Long, name: String, result: CalculatorEngine.RetainingWallResult) {
        viewModelScope.launch {
            repository.saveRetainingWallDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val currentResult = _result.value ?: return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val exportedFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // CRITICAL FIX (2026-07-27): Use NativePdfExporter (Android-native)
                    val fileName = "RetainingWall_Report_${System.currentTimeMillis()}.pdf"
                    val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?: context.cacheDir
                    directory.mkdirs()
                    val file = java.io.File(directory, fileName)

                    val drawingBitmap = try {
                        PdfDrawingGenerator.generateRetainingWallDrawing(
                            wallHeight = currentResult.height,
                            wallTopThickness = currentResult.stemThickness * 0.6,
                            wallBottomThickness = currentResult.stemThickness,
                            baseWidth = currentResult.baseWidth,
                            baseThickness = currentResult.stemThickness * 1.2,
                            toeLength = currentResult.baseWidth * 0.25,
                            heelLength = currentResult.baseWidth * 0.6,
                            mainRebarDia = currentResult.stemReinforcement.diameter.toDouble(),
                            mainRebarSpacing = currentResult.stemReinforcement.spacing.toDouble(),
                            distRebarDia = currentResult.stemReinforcement.diameter.toDouble() * 0.7,
                            distRebarSpacing = currentResult.stemReinforcement.spacing.toDouble() * 1.5,
                            baseRebarDia = currentResult.baseReinforcement.diameter.toDouble(),
                            baseRebarSpacing = currentResult.baseReinforcement.spacing.toDouble(),
                            cover = 50.0,
                            backfillAngle = currentResult.backfillAngle,
                            hasKey = true,
                            keyDepth = 150.0
                        )
                    } catch (e: Exception) { null }

                    val codeName = when(currentResult.code) {
                        CalculatorEngine.DesignCode.ACI -> "ACI 318"
                        CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
                        else -> "ECP 203"
                    }
                    val inputsMap = mapOf(
                        "Wall Height" to "${currentResult.height} m",
                        "Stem Thickness" to "${currentResult.stemThickness} mm",
                        "Base Width" to "${currentResult.baseWidth} mm",
                        "Backfill Angle" to "${String.format("%.1f", currentResult.backfillAngle)}°",
                        "Design Code" to codeName
                    )
                    val resultsMap = mapOf(
                        "Active Pressure Pa" to "${String.format("%.2f", currentResult.pa)} kN/m",
                        "Stem Moment" to "${String.format("%.2f", currentResult.muStem)} kN.m/m",
                        "Stem Reinforcement" to currentResult.stemReinforcement.barString,
                        "Base Reinforcement" to currentResult.baseReinforcement.barString,
                        "FS Overturning" to String.format("%.2f", currentResult.factorOfSafetyOverturning),
                        "FS Sliding" to String.format("%.2f", currentResult.factorOfSafetySliding),
                        "Concrete Volume" to "${String.format("%.3f", currentResult.concreteVolume)} m³",
                        "Steel Weight" to "${String.format("%.1f", currentResult.steelWeight)} kg"
                    )
                    val safetyChecks = currentResult.safetyChecks.map { chk ->
                        com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                            name = chk.name, calculated = chk.value,
                            limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                        )
                    }

                    // Professional English PDF Report — English only, no Arabic encoding issues
                    com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                        titleAr = "تقرير تصميم حائط ساند",
                        titleEn = "Retaining Wall Design Report",
                        subtitle = "Code: $codeName  •  H=${currentResult.height}m",
                        designType = "Retaining Wall",
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
            } catch (e: Throwable) {
                e.printStackTrace()
                _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
                onComplete(null)
            } finally {
                _isExporting.value = false
            }
        }
    }
}
