package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.SoilStructureInteraction
import com.civileg.app.domain.entities.DesignCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoilStructureViewModel @Inject constructor() : ViewModel() {

    private val _result = MutableLiveData<SoilStructureInteraction.Result?>()
    val result: LiveData<SoilStructureInteraction.Result?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastCode: DesignCode = DesignCode.ECP

    fun calculate(
        foundationLengthMm: Double,
        foundationWidthMm: Double,
        foundationThicknessMm: Double,
        columnLoadKN: Double,
        momentKNm: Double,
        soilBearingCapacityKpa: Double,
        soilUnitWeightKNm3: Double,
        frictionAngleDeg: Double,
        fcu: Double,
        fy: Double,
        embedmentDepthMm: Double,
        code: DesignCode
    ) {
        lastCode = code
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val design = CalculationFactory.getSoilStructureDesign(code)
                val inputs = SoilStructureInteraction.Inputs(
                    foundationLengthMm = foundationLengthMm,
                    foundationWidthMm = foundationWidthMm,
                    foundationThicknessMm = foundationThicknessMm,
                    columnLoadKN = columnLoadKN,
                    momentKNm = momentKNm,
                    soilBearingCapacityKpa = soilBearingCapacityKpa,
                    soilUnitWeightKnm3 = soilUnitWeightKNm3,
                    frictionAngleDeg = frictionAngleDeg,
                    fcu = fcu,
                    fy = fy,
                    embedmentDepthMm = embedmentDepthMm
                )
                val res = design.design(inputs)
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "فشل الحساب / Calculation failed: ${e.message}"
                _result.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reset() {
        _result.value = null
        _error.value = null
        _isLoading.value = false
    }

    /** Export Soil Structure Interaction report to PDF with 3-view engineering drawings. */
    fun exportToPdf(
        context: android.content.Context,
        foundationLengthMm: Double,
        foundationWidthMm: Double,
        foundationThicknessMm: Double,
        columnLoadKN: Double,
        momentKNm: Double,
        soilBearingCapacityKpa: Double,
        soilUnitWeightKNm3: Double,
        frictionAngleDeg: Double,
        embedmentDepthMm: Double,
        onComplete: (java.io.File?) -> Unit = {}
    ) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val drawingBitmap = com.civileg.app.utils.PdfDrawingGenerator.generateSoilBearingDrawing(
                    width = foundationWidthMm / 1000.0,
                    length = foundationLengthMm / 1000.0,
                    depth = embedmentDepthMm / 1000.0,
                    cohesion = 0.0,
                    phi = frictionAngleDeg,
                    qUlt = res.bearing.maxPressureKpa,
                    qAllow = soilBearingCapacityKpa,
                    gwt = 5.0,
                    isSafe = res.isSafe
                )

                val inputsMap = mapOf(
                    "Foundation Length" to "$foundationLengthMm mm",
                    "Foundation Width" to "$foundationWidthMm mm",
                    "Thickness" to "$foundationThicknessMm mm",
                    "Column Load" to "$columnLoadKN kN",
                    "Bending Moment" to "$momentKNm kN.m",
                    "Allowable Soil Capacity" to "$soilBearingCapacityKpa kPa",
                    "Embedment Depth" to "$embedmentDepthMm mm"
                )

                val resultsMap = mapOf(
                    "Max Soil Pressure" to "${"%.1f".format(res.bearing.maxPressureKpa)} kPa",
                    "Min Soil Pressure" to "${"%.1f".format(res.bearing.minPressureKpa)} kPa",
                    "Average Pressure" to "${"%.1f".format(res.bearing.averagePressureKpa)} kPa",
                    "Settlement" to "${"%.1f".format(res.settlement.totalSettlementMm)} mm",
                    "F.S Overturning" to "${"%.2f".format(res.stability.factorOfSafetyOverturning)}",
                    "F.S Sliding" to "${"%.2f".format(res.stability.factorOfSafetySliding)}",
                    "F.S Bearing" to "${"%.2f".format(res.stability.factorOfSafetyBearing)}"
                )

                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, "Soil_Structure_Report_${System.currentTimeMillis()}.pdf")

                val bundle = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تفاعل المنشأ مع التربة",
                    titleEn = "Soil-Structure Interaction Analysis Report",
                    subtitle = "Soil Pressure, Stability & Settlement Analysis",
                    designType = "Soil Structure",
                    inputs = inputsMap,
                    results = resultsMap,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath,
                    openPdfOnComplete = true
                )
                onComplete(bundle.pdfFile)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            }
        }
    }
}