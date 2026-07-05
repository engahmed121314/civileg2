package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
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
                val fileName = "Tank_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val exporter = ComprehensivePdfExporter(context)

                val drawingBitmap = try {
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
                        waterLevel = res.height * 0.85
                    )
                } catch (e: Exception) { null }

                val exportedFile = exporter.exportTankReport(
                    projectName = "تقرير تصميم خزان",
                    designCode = res.code,
                    result = res,
                    outputPath = file.absolutePath,
                    drawingBitmap = drawingBitmap
                )
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exportedFile?.let {
                        com.civileg.app.utils.ExportUtils.openPdf(context, it)
                    }
                    onComplete(exportedFile)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF Export Error: ${e.message}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
