package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
                _result.value = res
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
                    val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)
                    val fileName = "Stair_Report_${System.currentTimeMillis()}.pdf"
                    val file = java.io.File(context.cacheDir, fileName)
                    exporter.exportStairReport(
                        projectName = "تقرير تصميم سلم",
                        designCode = currentResult.code,
                        result = currentResult,
                        outputPath = file.absolutePath
                    )
                }
                exportedFile?.let {
                    com.civileg.app.utils.ExportUtils.openPdf(context, it)
                }
                onComplete(exportedFile)
            } catch (e: Exception) {
                e.printStackTrace()
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
