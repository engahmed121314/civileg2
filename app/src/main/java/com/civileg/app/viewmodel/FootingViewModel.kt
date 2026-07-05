package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
                val fileName = "Footing_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val exporter = ComprehensivePdfExporter(context)

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
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val exportedFile = exporter.exportFootingReport(
                    projectName = "تقرير تصميم أساسات - ${res.type.displayName}",
                    designCode = res.code,
                    result = res,
                    outputPath = file.absolutePath,
                    drawingBitmap = drawingBitmap
                )
                
                withContext(Dispatchers.Main) {
                    exportedFile?.let {
                        com.civileg.app.utils.ExportUtils.openPdf(context, it)
                    }
                    onComplete(exportedFile)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "خطأ في تصدير PDF: ${e.message}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
