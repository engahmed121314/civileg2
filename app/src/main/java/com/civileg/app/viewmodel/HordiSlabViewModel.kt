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
        designCode: String = "ECP",
        cylinderStrength: Double? = null
    ) {
        // SBC 304-CR-2018 is cylinder-based and no cube conversion basis exists
        // (SJ-8): refuse the computation up front with an explicit incomplete
        // input state instead of letting it fail mid-engine.
        if (designCode == "SBC" && cylinderStrength == null) {
            _error.value = "SBC 304-CR-2018 requires the cylinder strength f'c (MPa) — enter it explicitly; the cube value cannot be converted / الكود السعودي يتطلب مقاومة الأسطوانة صراحة — أدخل قيمة f'c"
            return
        }
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
                    loadCombination = loadCombination, designCode = designCode,
                    cylinderStrength = cylinderStrength
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
                    loadCombination = loadCombination,
                    cylinderStrengthMPa = cylinderStrength
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
    /** Standalone DXF sheet for the current hordi slab design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? {
        val res = _result.value ?: return null
        val params = lastInputParams ?: return null
        return com.civileg.app.utils.ElementDxfDrawings.slabSection(
            title = "HORDI SLAB",
            spanMm = params.span,
            thicknessMm = params.totalThickness,
            coverMm = 25.0,
            mainBarDiaMm = res.barDiameter.toDouble(),
            mainSpacingMm = res.barSpacing.toDouble()
        )
    }

    /** Export just the CAD drawing (no PDF) for the current design. */
    fun exportToDxf(context: android.content.Context, onComplete: (java.io.File?) -> Unit = {}) {
        com.civileg.app.utils.DxfQuickExport.export(
            context = context,
            drawing = buildDxfDrawing(),
            baseName = "HordiSlab",
            scope = viewModelScope,
            onComplete = onComplete
        )
    }

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

                val inputsMap = com.civileg.app.ui.compose.screens.HordiResultPresentation.exportInputsMap(
                    fcu = params.fcu, fy = params.fy,
                    ribWidth = params.ribWidth, ribSpacing = params.ribSpacing,
                    totalThickness = params.totalThickness, toppingThickness = params.toppingThickness,
                    span = params.span, designMoment = params.designMoment,
                    designShear = params.designShear, cylinderStrength = params.cylinderStrength,
                    loadCombinationDescription = params.loadCombination.description,
                    designCode = params.designCode
                )

                val resultsMap = com.civileg.app.ui.compose.screens.HordiResultPresentation.exportResultsMap(
                    requiredReinforcement = res.requiredReinforcement,
                    providedReinforcement = res.providedReinforcement,
                    barDiameter = res.barDiameter, barSpacing = res.barSpacing,
                    minThickness = res.minThickness,
                    shearCapacity = res.shearCapacity,
                    utilizationRatio = res.utilizationRatio,
                    isSafe = res.isSafe
                )

                val safetyChecks = emptyList<GenericSafetyCheck>()

                val drawingBitmap = pendingDrawingBitmap ?: runCatching {
                    com.civileg.app.shared.reporting.PdfDrawingGenerator.generateSlabDrawingByType(
                        slabType = com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabType.HOLLOW_BLOCK,
                        spanX = params.span, spanY = params.span, thickness = params.totalThickness,
                        mainDia = res.barDiameter, mainSpacing = res.barSpacing,
                        distDia = res.barDiameter, distSpacing = res.barSpacing,
                        cover = 25.0, ribWidth = params.ribWidth, ribSpacing = params.ribSpacing
                    )
                }.getOrNull()
                pendingDrawingBitmap = null  // consume after use

                val bbsEntries = runCatching {
                    com.civileg.app.domain.calculations.utils.BbsGenerator.generateHordiSlabBbs(
                        "H1", res, params.span, params.ribSpacing
                    )
                }.getOrDefault(emptyList())
                val steelWeightKg = bbsEntries.sumOf { it.totalWeightKg }

                val codeName = if (params.designCode == "SBC") "SBC 304-CR-2018" else params.designCode
                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "????? ????? ????? ?????",
                    titleEn = "Hordi Slab Design Report",
                    subtitle = "Code: $codeName    Span=${params.span/1000.0}m",
                    designType = "Hordi Slab",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    bbsEntries = bbsEntries,
                    boqRows = com.civileg.app.domain.calculations.utils.BoqEstimator.hordiSlabRows(
                        params.span / 1000.0, params.totalThickness / 1000.0, steelWeightKg, "H1"
                    ),
                    drawingBitmap = drawingBitmap,
                    calculationSteps = res.calculationSteps,
                    drawing = buildDxfDrawing(),
                    outputPath = file.absolutePath
                ).pdfFile

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
                "ACI" -> DesignCode.ACI
                "SBC" -> DesignCode.SBC
                else -> DesignCode.ECP
            }
            // FIX: Estimate concrete volume and steel weight from available data
            val inputParams = lastInputParams
            val estimatedVolume = if (inputParams != null) {
                // Approximate: (topping + rib_ratio × rib_height) × span² per m²
                val ribRatio = inputParams.ribWidth / inputParams.ribSpacing
                val thicknessEff = inputParams.toppingThickness + ribRatio * (inputParams.totalThickness - inputParams.toppingThickness)
                (thicknessEff / 1000.0) * (inputParams.span / 1000.0) * (inputParams.span / 1000.0)
            } else 0.0
            val estimatedSteel = res.providedReinforcement * 7850.0 / 1e6  // As (mm²) × density / 1e6 → kg/m approx
            repository.saveSlabDesign(projectId, name, com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabResult(
                type = com.civileg.app.domain.calculations.utils.CalculatorEngine.SlabType.HOLLOW_BLOCK,
                thickness = res.minThickness,
                isSafe = res.isSafe,
                concreteVolume = estimatedVolume,
                steelWeight = estimatedSteel,
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
        val cylinderStrength: Double? = null,
        val loadCombination: LoadCombination,
        val designCode: String
    )
}
