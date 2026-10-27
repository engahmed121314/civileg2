package com.civileg.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.aci.ACIPostTensioned
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedDesign
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedInput
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedResult
import com.civileg.app.domain.calculations.ecp.ECPPostTensioned
import com.civileg.app.domain.calculations.sbc.SBCPostTensioned
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.exporters.PostTensionedExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel لتصميم العناصر مسبقة الإجهاد (Post-Tensioned).
 * يوجّه الحساب إلى محرك الكود المختار (ECP / ACI / SBC).
 */
@HiltViewModel
class PostTensionedViewModel @Inject constructor() : ViewModel() {

    private val _result = MutableStateFlow<PostTensionedResult?>(null)
    val result: StateFlow<PostTensionedResult?> = _result.asStateFlow()

    private val _lastInput = MutableStateFlow<PostTensionedInput?>(null)
    val lastInput: StateFlow<PostTensionedInput?> = _lastInput.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /** Drawing captured from Compose, consumed by the next PDF export. */
    var pendingDrawingBitmap: Bitmap? = null

    private fun outputFile(context: Context, extension: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
        dir.mkdirs()
        return File(dir, "PostTensioned_${System.currentTimeMillis()}.$extension")
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return onComplete(null)
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val bitmap = pendingDrawingBitmap
                pendingDrawingBitmap = null
                val file = PostTensionedExporter.exportPdf(
                    result = res,
                    outputPath = outputFile(context, "pdf").absolutePath,
                    drawingBitmap = bitmap
                )
                withContext(Dispatchers.Main) { onComplete(file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "PDF export failed / فشل تصدير PDF: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _isExporting.value = false }
            }
        }
    }

    fun exportDxf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return onComplete(null)
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val file = PostTensionedExporter.exportDxf(
                    result = res,
                    outputPath = outputFile(context, "dxf").absolutePath
                )
                withContext(Dispatchers.Main) { onComplete(file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "DXF export failed / فشل تصدير DXF: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _isExporting.value = false }
            }
        }
    }

    fun exportCsv(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return onComplete(null)
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val file = PostTensionedExporter.exportCsv(context, res)
                withContext(Dispatchers.Main) { onComplete(file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "CSV export failed / فشل تصدير CSV: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _isExporting.value = false }
            }
        }
    }

    private fun engineFor(code: DesignCode): PostTensionedDesign = when (code) {
        DesignCode.ECP -> ECPPostTensioned()
        DesignCode.ACI -> ACIPostTensioned()
        DesignCode.SBC -> SBCPostTensioned()
    }

    fun calculate(input: PostTensionedInput) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val design = engineFor(input.designCode)
                _lastInput.value = input
                _result.value = design.designPostTensioned(input)
            } catch (e: IllegalArgumentException) {
                _error.value = "Invalid input / مدخلات غير صالحة: ${e.message}"
                _result.value = null
            } catch (e: Exception) {
                _error.value = "Calculation error / خطأ في الحساب: ${e.message}"
                _result.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clear() {
        _result.value = null
        _lastInput.value = null
        _error.value = null
    }
}