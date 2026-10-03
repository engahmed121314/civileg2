package com.civileg.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import com.civileg.app.utils.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WaffleSlabViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _result = MutableLiveData<WaffleSlabDesign.WaffleSlabResult?>()
    val result: LiveData<WaffleSlabDesign.WaffleSlabResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Store last input for PDF export
    private var lastInput: WaffleSlabDesign.WaffleSlabInput? = null

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    /**
     * Main calculation entry point for waffle slab design.
     */
    fun calculateWaffleSlab(
        lx: Double,               // mm (shorter span)
        ly: Double,               // mm (longer span)
        ribSpacing: Double,       // mm (center-to-center)
        ribWidth: Double,         // mm
        ribHeight: Double,        // mm
        toppingThickness: Double, // mm
        solidHeadSize: Double,    // mm
        columnWidth: Double,      // mm
        columnDepth: Double,      // mm
        fcu: Double,              // MPa
        fy: Double,               // MPa
        liveLoad: Double,         // kN/m2
        deadLoad: Double,         // kN/m2
        clearCover: Double,       // mm
        designCode: String = "ECP"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val code = DesignCode.valueOf(designCode)
                val input = WaffleSlabDesign.WaffleSlabInput(
                    lx = lx,
                    ly = ly,
                    ribSpacing = ribSpacing,
                    ribWidth = ribWidth,
                    ribHeight = ribHeight,
                    toppingThickness = toppingThickness,
                    solidHeadSize = solidHeadSize,
                    columnWidth = columnWidth,
                    columnDepth = columnDepth,
                    fcu = fcu,
                    fy = fy,
                    liveLoad = liveLoad,
                    deadLoad = deadLoad,
                    clearCover = clearCover,
                    designCode = code
                )
                lastInput = input

                val designer: WaffleSlabDesign = CalculationFactory.getWaffleSlabDesign(code)
                val result = designer.design(input)
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
        val input = lastInput ?: return
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "WaffleSlab_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val inputsMap = mapOf(
                    "Lx" to "${input.lx / 1000.0} m",
                    "Ly" to "${input.ly / 1000.0} m",
                    "Rib Spacing" to "${input.ribSpacing} mm",
                    "Rib Width" to "${input.ribWidth} mm",
                    "Rib Height" to "${input.ribHeight} mm",
                    "Topping Thickness" to "${input.toppingThickness} mm",
                    "Solid Head Size" to "${input.solidHeadSize} mm",
                    "Column" to "${input.columnWidth}×${input.columnDepth} mm",
                    "f'cu" to "${input.fcu} MPa",
                    "fy" to "${input.fy} MPa",
                    "Dead Load" to "${input.deadLoad} kN/m²",
                    "Live Load" to "${input.liveLoad} kN/m²",
                    "Clear Cover" to "${input.clearCover} mm",
                    "Design Code" to input.designCode.displayName
                )

                val resultsMap = mutableMapOf(
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%",
                    "Is Safe" to if (res.isSafe) "YES" else "NO",
                    "Concrete Volume" to "${"%.3f".format(res.concreteVolume)} m³",
                    "Steel Weight" to "${"%.1f".format(res.steelWeight)} kg"
                )
                res.ribDesign?.let { rib ->
                    resultsMap["Rib Flexure Rebar"] = rib.flexureReinforcement.barString
                    resultsMap["Rib Shear Rebar"] = "φ${rib.shearReinforcement.stirrupDiameter.toInt()}@${rib.shearReinforcement.stirrupSpacing.toInt()} mm"
                    resultsMap["Rib Utilization"] = "${(rib.utilizationRatio * 100).toInt()}%"
                }
                res.punchingShearCheck?.let { punch ->
                    resultsMap["Punching Vu"] = "${"%.1f".format(punch.vu)} kN"
                    resultsMap["Punching Vc"] = "${"%.1f".format(punch.vc)} kN"
                }
                res.deflectionCheck?.let { defl ->
                    resultsMap["Deflection Check"] = if (defl.isSafe) "PASS" else "FAIL"
                }

                val safetyChecks = res.safetyChecks.map {
                    GenericSafetyCheck(
                        name = it.name, calculated = it.calculated,
                        limit = it.limit, unit = it.unit, passed = it.passed
                    )
                }

                val drawingBitmap = pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use

                val exporter = ComprehensivePdfExporter(context)
                exporter.setLanguage(LocaleHelper.getLocale(context))
                val generated = exporter.exportWaffleSlabReport(
                    projectName = "Waffle Slab",
                    designCode = input.designCode,
                    lx = input.lx,
                    ly = input.ly,
                    ribSpacing = input.ribSpacing,
                    ribWidth = input.ribWidth,
                    ribHeight = input.ribHeight,
                    toppingThickness = input.toppingThickness,
                    solidHeadSize = input.solidHeadSize,
                    columnWidth = input.columnWidth,
                    fcu = input.fcu,
                    fy = input.fy,
                    liveLoad = input.liveLoad,
                    deadLoad = input.deadLoad,
                    result = res,
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
            val engineCode = when (lastInput?.designCode ?: DesignCode.ECP) {
                DesignCode.ECP -> com.civileg.app.utils.CalculatorEngine.DesignCode.EGYPTIAN
                DesignCode.ACI -> com.civileg.app.utils.CalculatorEngine.DesignCode.ACI
                DesignCode.SBC -> com.civileg.app.utils.CalculatorEngine.DesignCode.SAUDI
            }
            repository.saveSlabDesign(projectId, name, com.civileg.app.utils.CalculatorEngine.SlabResult(
                type = com.civileg.app.utils.CalculatorEngine.SlabType.WAFFLE,
                thickness = res.concreteVolume,
                isSafe = res.isSafe,
                concreteVolume = res.concreteVolume,
                steelWeight = res.steelWeight,
                code = engineCode,
                utilizationRatio = res.utilizationRatio
            ))
        }
    }
}
