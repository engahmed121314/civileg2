package com.civileg.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.SlabDesignResult
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import com.civileg.app.utils.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HordiSlabViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _result = MutableLiveData<SlabDesignResult?>()
    val result: LiveData<SlabDesignResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Store last input for PDF export
    private var lastInputParams: HordiSlabInputParams? = null

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    private val _validationReport = MutableLiveData<String?>()
    val validationReport: LiveData<String?> = _validationReport

    /**
     * Main calculation entry point for Hordi/ribbed slab design.
     */
    fun calculateHordiSlab(
        fcu: Double,              // MPa
        fy: Double,               // MPa
        ribWidth: Double,         // mm
        ribSpacing: Double,       // mm (center-to-center)
        totalThickness: Double,   // mm
        toppingThickness: Double, // mm
        span: Double,             // mm
        designMoment: Double,     // kN.m/rib
        designShear: Double,      // kN/rib
        loadCombination: LoadCombination,
        designCode: String = "ECP"
    ) {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("toppingThickness", toppingThickness)
        InputGuard.positive("span", span)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val code = DesignCode.valueOf(designCode)
                lastInputParams = HordiSlabInputParams(
                    fcu = fcu, fy = fy, ribWidth = ribWidth, ribSpacing = ribSpacing,
                    totalThickness = totalThickness, toppingThickness = toppingThickness,
                    span = span, designMoment = designMoment, designShear = designShear,
                    loadCombination = loadCombination, designCode = designCode
                )

                val designer: HordiSlabDesign = CalculationFactory.getHordiSlabDesign(code)
                val result = designer.designHordiSlab(
                    fcu = fcu,
                    fy = fy,
                    ribWidth = ribWidth,
                    ribSpacing = ribSpacing,
                    totalThickness = totalThickness,
                    toppingThickness = toppingThickness,
                    span = span,
                    designMoment = designMoment,
                    designShear = designShear,
                    loadCombination = loadCombination
                )
                _result.value = result
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Export results to PDF report.
     */
    fun exportToPdf(
        context: Context,
        onComplete: (File?) -> Unit
    ) {
        val res = _result.value ?: return
        val params = lastInputParams ?: return
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "HordiSlab_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val inputsMap = mapOf(
                    "Rib Width" to "${params.ribWidth} mm",
                    "Rib Spacing" to "${params.ribSpacing} mm",
                    "Total Thickness" to "${params.totalThickness} mm",
                    "Topping Thickness" to "${params.toppingThickness} mm",
                    "Span" to "${params.span / 1000.0} m",
                    "Design Moment" to "${"%.2f".format(params.designMoment)} kN.m/rib",
                    "Design Shear" to "${"%.2f".format(params.designShear)} kN/rib",
                    "f'cu" to "${params.fcu} MPa",
                    "fy" to "${params.fy} MPa",
                    "Load Combination" to params.loadCombination.description,
                    "Design Code" to params.designCode
                )

                val resultsMap = mapOf(
                    "Required As" to "${"%.2f".format(res.requiredReinforcement)} mm²",
                    "Provided As" to "${"%.2f".format(res.providedReinforcement)} mm²",
                    "Bar Diameter" to "${res.barDiameter} mm",
                    "Bar Spacing" to "${res.barSpacing} mm",
                    "Min Thickness" to "${res.minThickness} mm",
                    "Shear Capacity" to "${"%.1f".format(res.shearCapacity)} kN",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%",
                    "Is Safe" to if (res.isSafe) "YES" else "NO"
                )

                val safetyChecks = emptyList<GenericSafetyCheck>()

                val drawingBitmap = pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use

                val exporter = ComprehensivePdfExporter(context)
                exporter.setLanguage(LocaleHelper.getLocale(context))
                val generated = exporter.exportHordiSlabReport(
                    projectName = "Hordi Slab",
                    designCode = DesignCode.valueOf(params.designCode),
                    fcu = params.fcu,
                    fy = params.fy,
                    ribWidth = params.ribWidth,
                    ribSpacing = params.ribSpacing,
                    totalThickness = params.totalThickness,
                    toppingThickness = params.toppingThickness,
                    span = params.span,
                    designMoment = params.designMoment,
                    designShear = params.designShear,
                    resultAsProvided = res.providedReinforcement,
                    isSafe = res.isSafe,
                    utilizationRatio = res.utilizationRatio,
                    outputPath = file.absolutePath,
                    drawingBitmap = drawingBitmap
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    fun clearResult() {
        _result.value = null
        _error.value = null
    }

    fun saveDesign(projectId: Long, name: String) {
        val res = _result.value ?: return
        viewModelScope.launch {
            val engineCode = when (lastInputParams?.designCode ?: "ECP") {
                "ACI" -> com.civileg.app.utils.CalculatorEngine.DesignCode.ACI
                "SBC" -> com.civileg.app.utils.CalculatorEngine.DesignCode.SAUDI
                else -> com.civileg.app.utils.CalculatorEngine.DesignCode.EGYPTIAN
            }
            repository.saveSlabDesign(projectId, name, com.civileg.app.utils.CalculatorEngine.SlabResult(
                type = com.civileg.app.utils.CalculatorEngine.SlabType.HOLLOW_BLOCK,
                thickness = res.minThickness,
                isSafe = res.isSafe,
                concreteVolume = 0.0,
                steelWeight = 0.0,
                code = engineCode,
                utilizationRatio = res.utilizationRatio
            ))
        }
    }

    /**
     * Helper data class to store input parameters for PDF export.
     */
    private data class HordiSlabInputParams(
        val fcu: Double,
        val fy: Double,
        val ribWidth: Double,
        val ribSpacing: Double,
        val totalThickness: Double,
        val toppingThickness: Double,
        val span: Double,
        val designMoment: Double,
        val designShear: Double,
        val loadCombination: LoadCombination,
        val designCode: String
    )
}
