package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.aci.ACIFlatSlab
import com.civileg.app.domain.calculations.base.FlatSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPFlatSlab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlatSlabViewModel @Inject constructor() : ViewModel() {

    private val _result = MutableLiveData<FlatSlabResult?>()
    val result: LiveData<FlatSlabResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Store last input for PDF export
    private var lastInput: FlatSlabInput? = null

    /**
     * Main calculation entry point.
     * @param designCode "ECP" or "ACI"
     */
    fun calculateFlatSlab(
        panelType: PanelType,
        designMethod: DesignMethod,
        lx: Double,            // mm
        ly: Double,            // mm
        slabThickness: Double, // mm
        dropThickness: Double, // mm
        dropSizeX: Double,     // mm
        dropSizeY: Double,     // mm
        columnWidth: Double,   // mm
        columnDepth: Double,   // mm
        fcu: Double,           // MPa
        fy: Double,            // MPa
        liveLoad: Double,      // kN/m2
        floorFinish: Double,   // kN/m2
        numberOfFloors: Int,
        clearCover: Double,    // mm
        storyHeight: Double,   // m
        designCode: String = "ECP"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val input = FlatSlabInput(
                    panelType = panelType,
                    designMethod = designMethod,
                    lx = lx, ly = ly,
                    slabThickness = slabThickness,
                    dropThickness = dropThickness,
                    dropSizeX = dropSizeX, dropSizeY = dropSizeY,
                    columnWidth = columnWidth, columnDepth = columnDepth,
                    fcu = fcu, fy = fy,
                    liveLoad = liveLoad, floorFinish = floorFinish,
                    numberOfFloors = numberOfFloors,
                    clearCover = clearCover, storyHeight = storyHeight
                )
                lastInput = input

                val designer: FlatSlabDesign = when (designCode) {
                    "ACI" -> ACIFlatSlab()
                    else -> ECPFlatSlab()
                }

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
        context: android.content.Context,
        onComplete: (java.io.File?) -> Unit
    ) {
        val res = _result.value ?: return
        val input = lastInput ?: return
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "FlatSlab_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                val inputsMap = mapOf(
                    "Panel Type" to input.panelType.displayName,
                    "Method" to input.designMethod.displayName,
                    "Lx" to "${input.lx / 1000.0} m",
                    "Ly" to "${input.ly / 1000.0} m",
                    "Thickness" to "${input.slabThickness} mm",
                    "Column" to "${input.columnWidth}×${input.columnDepth} mm",
                    "f'cu / f'c" to "${input.fcu} MPa",
                    "fy" to "${input.fy} MPa",
                    "DL (finish)" to "${input.floorFinish} kN/m²",
                    "LL" to "${input.liveLoad} kN/m²",
                    "Wu" to "${"%.2f".format(res.totalFactoredLoad)} kN/m²"
                )

                val resultsMap = mapOf(
                    "MoX" to "${"%.1f".format(res.panelMomentX)} kN.m",
                    "MoY" to "${"%.1f".format(res.panelMomentY)} kN.m",
                    "Col Strip +M" to "${"%.1f".format(res.columnStripMomentPos)} kN.m",
                    "Col Strip -M" to "${"%.1f".format(res.columnStripMomentNeg)} kN.m",
                    "Col Top Rebar" to res.columnStripTopRebar.barString,
                    "Col Bot Rebar" to res.columnStripBotRebar.barString,
                    "Punching Vu" to "${"%.1f".format(res.punchingShearVu)} kN",
                    "Punching Vc" to "${"%.1f".format(res.punchingShearVc)} kN",
                    "Deflection" to "${"%.2f".format(res.deflection)} mm",
                    "Concrete Vol" to "${"%.3f".format(res.concreteVolumePerPanel)} m³",
                    "Steel Wt" to "${"%.1f".format(res.steelWeightPerPanel)} kg",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%"
                )

                val safetyChecks = res.safetyChecks.map {
                    com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = it.name, calculated = it.calculated, limit = it.limit,
                        unit = it.unit, passed = it.passed
                    )
                }

                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم بلاطة مسطحة",
                    titleEn = "Flat Slab Design Report",
                    subtitle = "${input.panelType.displayName} — ${input.designMethod.displayName}",
                    designType = input.panelType.displayName,
                    inputs = inputsMap, results = resultsMap,
                    safetyChecks = safetyChecks, isSafe = res.isSafe,
                    drawingBitmap = null, outputPath = file.absolutePath
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
}