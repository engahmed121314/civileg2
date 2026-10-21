package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.civileg.app.domain.entities.GenericSafetyCheck
import android.graphics.Bitmap
import java.io.File
import android.content.Context
import android.os.Environment
import com.civileg.app.domain.calculations.InputGuard

@HiltViewModel
class StrapFootingViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
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

    private var lastCode: DesignCode = DesignCode.ECP
    private var lastDistanceMm: Double = 0.0
    private var lastInputs: List<Pair<String, String>> = emptyList()

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    fun calculate(
        col1Load: Double, col2Load: Double, distance: Double,
        col1W: Double, col1D: Double, col2W: Double, col2D: Double,
        soil: Double, fcu: Double, fy: Double,
        code: DesignCode, preferredDiameter: Int, strapWidth: Double
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
        lastDistanceMm = distance
        lastInputs = listOf(
            "Column 1 Load" to "$col1Load kN",
            "Column 2 Load" to "$col2Load kN",
            "Column Distance" to "${distance / 1000.0} m",
            "Column 1 Size" to "${col1W}x${col1D} mm",
            "Column 2 Size" to "${col2W}x${col2D} mm",
            "Strap Width" to "$strapWidth mm",
            "Allowable Soil Pressure" to "$soil kN/m2",
            "fcu" to "$fcu MPa",
            "fy" to "$fy MPa",
            "Preferred Bar Diameter" to "$preferredDiameter mm"
        )
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
                
                val drawingBitmap = pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use

                val codeName = when (lastCode) {
                    DesignCode.ACI -> "ACI 318"
                    DesignCode.SBC -> "SBC 304"
                    else -> "ECP 203"
                }
                val inputsMap = mapOf(
                    "Design Code" to codeName
                ) + lastInputs.toMap()
                val resultsMap = mutableMapOf(
                    "Footing 1 Width" to String.format("%.0f", res.footing1.width) + " mm",
                    "Footing 1 Length" to String.format("%.0f", res.footing1.length) + " mm",
                    "Footing 2 Width" to String.format("%.0f", res.footing2.width) + " mm",
                    "Footing 2 Length" to String.format("%.0f", res.footing2.length) + " mm",
                    "Strap Width" to String.format("%.0f", res.strapBeamWidth) + " mm",
                    "Strap Depth" to String.format("%.0f", res.strapBeamDepth) + " mm",
                    "Concrete Volume" to String.format("%.3f", res.concreteVolume) + " m³",
                    "Steel Weight" to String.format("%.1f", res.steelWeight) + " kg",
                    "Utilization" to String.format("%.0f", res.utilizationRatio * 100) + "%",
                    "Is Safe" to if (res.isSafe) "YES" else "NO"
                )
                val safetyChecks = res.safetyChecks.map { GenericSafetyCheck(it.name, it.value, it.limit, it.unit, it.isSafe) }
                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم قاعدة شريطية",
                    titleEn = "Strap Footing Design Report",
                    subtitle = "Code: $codeName",
                    designType = "Strap Footing",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    calculationSteps = res.calculationSteps,
                    bbsEntries = runCatching {
                        com.civileg.app.utils.BbsGenerator.generateStrapFootingBbs("SF1", res, lastDistanceMm)
                    }.getOrDefault(emptyList()),
                    boqRows = com.civileg.app.utils.BoqEstimator.strapFootingRows(
                        res, lastDistanceMm / 1000.0, "SF1"
                    ),
                    drawingBitmap = drawingBitmap,
                    drawing = com.civileg.app.utils.ElementDxfDrawings.generic(
                        "STRAP FOOTING", res.footing1.length, res.footing1.width
                    ),
                    outputPath = file.absolutePath
                ).pdfFile
                onComplete(generated)
            } catch (e: Exception) {
                onComplete(null)
                _errorMessage.value = "PDF export failed / فشل تصدير PDF: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }
}
