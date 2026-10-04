package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.core.engineering.SettlementAnalysisEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.civileg.app.domain.calculations.InputGuard

@HiltViewModel
class SoilSettlementViewModel @Inject constructor() : ViewModel() {

    private val _result = MutableLiveData<SettlementAnalysisEngine.SettlementResult?>()
    val result: LiveData<SettlementAnalysisEngine.SettlementResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var lastInputs: SettlementInputs? = null
    
    private data class SettlementInputs(
        val pressure: Double,
        val width: Double,
        val length: Double,
        val layers: List<SettlementAnalysisEngine.SoilLayer>,
        val limit: Double
    )

    fun calculate(
        pressure: Double, width: Double, length: Double,
        layers: List<SettlementAnalysisEngine.SoilLayer>, limit: Double
    ) {
        InputGuard.positive("pressure", pressure)
        InputGuard.positive("width", width)
        InputGuard.positive("length", length)
        InputGuard.positive("limit", limit)
        InputGuard.notEmpty("layers", layers)

        lastInputs = SettlementInputs(pressure, width, length, layers, limit)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = SettlementAnalysisEngine.calculateSettlement(pressure, width, length, layers, limit)
                _result.value = res
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: e.message ?: "Calculation error — Settlement analysis failed / خطأ في حساب الهبوط"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        val inputs = lastInputs ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "Settlement_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                val inputsMap = mapOf(
                    "Applied Pressure" to "${inputs.pressure} kPa",
                    "Footing Width" to "${inputs.width} m",
                    "Footing Length" to "${inputs.length} m",
                    "Allowable Limit" to "${inputs.limit} mm"
                )
                val resultsMap = mapOf(
                    "Immediate Settlement" to "${"%.2f".format(res.immediateSettlement)} mm",
                    "Consolidation Settlement" to "${"%.2f".format(res.consolidationSettlement)} mm",
                    "Total Settlement" to "${"%.2f".format(res.totalSettlement)} mm",
                    "Status" to if (res.isSafe) "SAFE" else "EXCEEDS LIMIT"
                )
                
                val safetyChecks = listOf(
                    GenericSafetyCheck(
                        "Total Settlement", res.totalSettlement, inputs.limit, "mm", res.isSafe
                    )
                )
                
                val exported = ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تحليل الهبوط",
                    titleEn = "Soil Settlement Analysis Report",
                    subtitle = "Footing: ${inputs.width}x${inputs.length} m",
                    designType = "Settlement",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = null,
                    outputPath = file.absolutePath
                )

                withContext(Dispatchers.Main) {
                    exported?.let { ExportUtils.openPdf(context, it) }
                    onComplete(exported)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "PDF export failed: ${e.localizedMessage ?: e.message ?: "Unknown error"} / فشل تصدير PDF"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
