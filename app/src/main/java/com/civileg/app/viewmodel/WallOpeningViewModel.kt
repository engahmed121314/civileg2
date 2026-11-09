package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.WallOpeningDesign
import com.civileg.app.domain.calculations.wallopenings.ECPWallOpening
import com.civileg.app.domain.calculations.wallopenings.ACIWallOpening
import com.civileg.app.domain.calculations.wallopenings.SBCWallOpening
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WallOpeningViewModel @Inject constructor(
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<WallOpeningDesign.Result?>()
    val result: LiveData<WallOpeningDesign.Result?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastCode: DesignCode = DesignCode.ECP

    fun calculate(
        wallHeightMm: Double,
        wallThicknessMm: Double,
        openingWidthMm: Double,
        openingHeightMm: Double,
        openingFromBaseMm: Double,
        wallLengthMm: Double,
        fcu: Double,
        fy: Double,
        loadAboveOpeningKNm: Double,
        code: DesignCode
    ) {
        lastCode = code
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val design = CalculationFactory.getWallOpeningDesign(code)
                val inputs = WallOpeningDesign.Inputs(
                    wallHeightMm = wallHeightMm,
                    wallThicknessMm = wallThicknessMm,
                    openingWidthMm = openingWidthMm,
                    openingHeightMm = openingHeightMm,
                    openingFromBaseMm = openingFromBaseMm,
                    wallLengthMm = wallLengthMm,
                    fcu = fcu,
                    fy = fy,
                    loadAboveOpeningKNm = loadAboveOpeningKNm
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

    /** Export Wall Opening Design report to PDF with 3-view engineering drawings. */
    fun exportToPdf(
        context: android.content.Context,
        wallHeightMm: Double,
        wallThicknessMm: Double,
        openingWidthMm: Double,
        openingHeightMm: Double,
        openingFromBaseMm: Double,
        wallLengthMm: Double,
        fcu: Double,
        fy: Double,
        onComplete: (java.io.File?) -> Unit = {}
    ) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val drawingBitmap = com.civileg.app.utils.PdfDrawingGenerator.generateWallOpeningDrawing(
                    wallHeight = wallHeightMm,
                    wallThickness = wallThicknessMm,
                    openingWidth = openingWidthMm,
                    openingHeight = openingHeightMm,
                    openingFromBase = openingFromBaseMm,
                    wallLength = wallLengthMm,
                    fcu = fcu,
                    fy = fy,
                    isSafe = res.isSafe
                )

                val inputsMap = mapOf(
                    "Wall Height" to "$wallHeightMm mm",
                    "Wall Thickness" to "$wallThicknessMm mm",
                    "Wall Length" to "$wallLengthMm mm",
                    "Opening Width" to "$openingWidthMm mm",
                    "Opening Height" to "$openingHeightMm mm",
                    "Opening From Base" to "$openingFromBaseMm mm",
                    "fcu" to "$fcu MPa",
                    "fy" to "$fy MPa"
                )

                val resultsMap = mapOf(
                    "Lintel Rebar" to "${res.headerBeamReinforcement.numberOfBars}T${res.headerBeamReinforcement.barDiameterMm.toInt()}",
                    "Sill Rebar" to "${res.sillReinforcement.numberOfBars}T${res.sillReinforcement.barDiameterMm.toInt()}",
                    "Side Vertical Rebar" to "${res.verticalReinforcementEachSide.numberOfBars}T${res.verticalReinforcementEachSide.barDiameterMm.toInt()}",
                    "Max Concrete Stress" to "${"%.2f".format(res.maxStressMpa)} MPa",
                    "Safety Status" to if (res.isSafe) "SAFE (PASS)" else "UNSAFE (FAIL)"
                )

                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, "Wall_Opening_Report_${System.currentTimeMillis()}.pdf")

                val bundle = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم فتحات الحوائط",
                    titleEn = "Wall Opening Design Report",
                    subtitle = "Structural Lintels & Opening Detailing",
                    designType = "Wall Opening",
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