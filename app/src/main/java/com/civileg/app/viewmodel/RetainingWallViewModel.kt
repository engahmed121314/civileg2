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
                    val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)
                    val fileName = "RetainingWall_Report_${System.currentTimeMillis()}.pdf"
                    val file = java.io.File(context.cacheDir, fileName)

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

                    exporter.exportRetainingWallReport(
                        projectName = "تقرير تصميم حائط ساند",
                        designCode = currentResult.code,
                        result = currentResult,
                        outputPath = file.absolutePath,
                        drawingBitmap = drawingBitmap
                    )
                }
                exportedFile?.let {
                    com.civileg.app.utils.ExportUtils.openPdf(context, it)
                }
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
