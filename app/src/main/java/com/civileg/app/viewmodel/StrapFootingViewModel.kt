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

import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import android.graphics.Bitmap
import java.io.File
import android.content.Context
import android.os.Environment
import com.civileg.app.domain.calculations.InputGuard

@HiltViewModel
class StrapFootingViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine,
    private val pdfExporter: ComprehensivePdfExporter
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.StrapFootingResult?>()
    val result: LiveData<CalculatorEngine.StrapFootingResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private var lastCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN

    fun calculate(
        col1Load: Double, col2Load: Double, distance: Double,
        col1W: Double, col1D: Double, col2W: Double, col2D: Double,
        soil: Double, fcu: Double, fy: Double,
        code: CalculatorEngine.DesignCode, preferredDiameter: Int, strapWidth: Double
    ) {
        InputGuard.positive("col1Load", col1Load)
        InputGuard.positive("col2Load", col2Load)
        InputGuard.positive("distance", distance)
        InputGuard.positive("col1W", col1W)
        InputGuard.positive("col1D", col1D)
        InputGuard.positive("col2W", col2W)
        InputGuard.positive("col2D", col2D)
        InputGuard.positive("soil", soil)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("strapWidth", strapWidth)

        lastCode = code
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.calculateStrapFooting(
                    col1Load, col2Load, distance, col1W, col1D, col2W, col2D,
                    soil, fcu, fy, code, preferredDiameter, strapWidth
                )
                _result.value = res
                _error.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                _errorMessage.value = "Strap footing calculation error / خطأ في حساب القاعدة الشريطية: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val fileName = "StrapFooting_${System.currentTimeMillis()}.pdf"
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                val file = File(dir, fileName)
                
                val success = pdfExporter.exportStrapFootingReport(
                    "Strap Footing Design",
                    lastCode,
                    res,
                    file.absolutePath,
                    null // No drawing for now
                )
                onComplete(success)
            } catch (e: Exception) {
                onComplete(null)
                _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }
}
