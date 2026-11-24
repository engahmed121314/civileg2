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
    private val calculatedCase = com.civileg.app.domain.calculations.utils.CalculatedCaseState<HordiSlabInputParams, SlabDesignResult>()
    val calculatedInput: HordiSlabInputParams? get() = calculatedCase.snapshot?.input
    private var activeCalculation: kotlinx.coroutines.Job? = null

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    private var pendingDrawingBitmap: Pair<com.civileg.app.domain.calculations.utils.CalculatedCaseState.Snapshot<HordiSlabInputParams, SlabDesignResult>, Bitmap>? = null

    fun currentSnapshot() = calculatedCase.snapshot

    fun acceptDrawingCapture(
        snapshot: com.civileg.app.domain.calculations.utils.CalculatedCaseState.Snapshot<HordiSlabInputParams, SlabDesignResult>?,
        bitmap: Bitmap?
    ): Boolean {
        if (snapshot == null || snapshot !== calculatedCase.snapshot) return false
        pendingDrawingBitmap = bitmap?.let { snapshot to it }
        return true
    }

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
        coverMm: Double = 25.0,
        detailCase: com.civileg.app.domain.calculations.utils.JoistDetailCase = com.civileg.app.domain.calculations.utils.JoistDetailCase()
    ) {
        clearResult()
        val ticket = calculatedCase.begin()
        activeCalculation = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                InputGuard.positive("fcu", fcu)
                InputGuard.positive("fy", fy)
                InputGuard.positive("ribWidth", ribWidth)
                InputGuard.positive("ribSpacing", ribSpacing)
                InputGuard.positive("totalThickness", totalThickness)
                InputGuard.positive("toppingThickness", toppingThickness)
                InputGuard.positive("span", span)
                InputGuard.nonNegative("designMoment", designMoment)
                InputGuard.nonNegative("designShear", designShear)
                InputGuard.inRange("cover", coverMm, 10.0, 75.0)
                val bindingConflicts = com.civileg.app.domain.calculations.utils.HordiCaseBinding.conflicts(
                    detailCase, span, designMoment, designShear, coverMm)
                require(bindingConflicts.isEmpty()) { bindingConflicts.joinToString("; ") }
                val code = DesignCode.valueOf(designCode)
                val input = HordiSlabInputParams(
                    fcu = fcu, fy = fy, ribWidth = ribWidth, ribSpacing = ribSpacing,
                    totalThickness = totalThickness, toppingThickness = toppingThickness,
                    span = span, designMoment = designMoment, designShear = designShear,
                    loadCombination = loadCombination, designCode = designCode, coverMm = coverMm, detailCase = detailCase
                )

                if (input.referenceBasis == com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020) {
                    val geometry = com.civileg.app.domain.calculations.ecp.ECP2020JoistRules.geometry(
                        ribWidth, ribSpacing, totalThickness, toppingThickness
                    )
                    require(geometry.all { it.passed }) {
                        geometry.filterNot { it.passed }.joinToString("; ") { "${it.clause}: ${it.requirement}" }
                    }
                }
                if (input.referenceBasis == com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020) {
                    com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview.review(
                        detailCase, fcu, fy, ribWidth, ribSpacing, totalThickness, toppingThickness
                    )
                }
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
                val frozenResult = result.copy(warnings = result.warnings.toList(), codeNotes = result.codeNotes.toList())
                if (calculatedCase.complete(ticket, input, frozenResult)) _result.value = frozenResult
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (calculatedCase.isCurrent(ticket)) _error.value = "Error: ${e.message}"
            } finally {
                if (calculatedCase.isCurrent(ticket)) _isLoading.value = false
            }
        }
    }

    /**
     * Export results to PDF report.
     */
    /** Standalone DXF sheet for the current hordi slab design (null when no result). */
    fun buildDxfDrawing(): com.civileg.app.utils.DxfDrawable? = null

    /** Explicit main-bar-body review takeoff, not a complete adopted layout. */
    private fun scheduleFor(snapshot: com.civileg.app.domain.calculations.utils.CalculatedCaseState.Snapshot<HordiSlabInputParams, SlabDesignResult>): com.civileg.app.domain.calculations.utils.AdoptedReinforcementSchedule? {
        val p = snapshot.input
        val caseReview = if (p.referenceBasis == com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020)
            com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview.review(
                p.detailCase, p.fcu, p.fy, p.ribWidth, p.ribSpacing, p.totalThickness, p.toppingThickness
            ) else null
        // Never issue adopted body quantities from a failed section/shear case.
        if (p.referenceBasis == com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020 &&
            (caseReview == null || !com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview.permitsPartialBodyTakeoff(caseReview))) return null
        val specific = caseReview?.section
        val source = specific?.let { check ->
            snapshot.result.copy(requiredReinforcement = requireNotNull(check.requiredAreaWithMinimumMm2),
                providedReinforcement = check.providedAreaMm2,
                barDiameter = requireNotNull(p.detailCase.detailing).mainBarDiameterMm.toDouble(),
                isSafe = check.sectionPasses && caseReview.shearDemandWithinConcreteThreshold == true)
        } ?: snapshot.result
        return com.civileg.app.domain.calculations.utils.HordiReviewTakeoff.schedule(
            source, p.designCode, p.span, p.ribSpacing, p.coverMm
        )
    }

    fun reviewSchedule() = calculatedCase.snapshot?.let { scheduleFor(it) }

    fun codeSpecificReview(): com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview.Result? {
        val p = calculatedInput ?: return null
        if (p.referenceBasis != com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020) return null
        return com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview.review(
            p.detailCase, p.fcu, p.fy, p.ribWidth, p.ribSpacing, p.totalThickness, p.toppingThickness
        )
    }

    private fun panelBottomTakeoffFor(p: HordiSlabInputParams): com.civileg.app.domain.calculations.ecp.ECP2020JoistBottomTakeoff.Result? {
        if (p.referenceBasis != com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020) return null
        return com.civileg.app.domain.calculations.ecp.ECP2020JoistBottomTakeoff.build(
            p.detailCase, p.fcu, p.fy, p.ribWidth, p.ribSpacing, p.totalThickness, p.toppingThickness
        )
    }

    fun panelBottomTakeoff() = calculatedInput?.let { panelBottomTakeoffFor(it) }
    fun panelBottomEntries() = panelBottomTakeoff()?.schedule?.let {
        com.civileg.app.domain.calculations.utils.BbsGenerator.fromAdoptedSchedule("HP", it)
    }.orEmpty()

    fun reviewBbsEntries() = reviewSchedule()?.let {
        com.civileg.app.domain.calculations.utils.BbsGenerator.fromAdoptedSchedule("H1", it)
    }.orEmpty()

    fun reviewBoqRows(): List<com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.BoqRow> {
        val snapshot = calculatedCase.snapshot ?: return emptyList()
        val p = snapshot.input
        return com.civileg.app.domain.calculations.utils.BoqEstimator.hordiReviewRows(
            p.span, p.totalThickness, p.toppingThickness, p.ribWidth, p.ribSpacing,
            reviewSchedule()?.totalWeightKg, panelBottomTakeoff()?.schedule?.totalWeightKg
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
        val snapshot = calculatedCase.snapshot ?: return
        val res = snapshot.result
        val params = snapshot.input
        val capturedDrawing = pendingDrawingBitmap?.takeIf { it.first === snapshot }?.second
        pendingDrawingBitmap = null
        val dxfDrawing = buildDxfDrawing()
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
                    "Clear Span Between Support Faces" to "${params.span / 1000.0} m",
                    "Design Moment" to "${"%.2f".format(params.designMoment)} kN.m/rib",
                    "Design Shear" to "${"%.2f".format(params.designShear)} kN/rib",
                    "f'cu" to "${params.fcu} MPa",
                    "fy" to "${params.fy} MPa",
                    "Load Combination" to params.loadCombination.description,
                    "Selected Reference Basis" to params.referenceBasis.edition,
                    "Method Status" to "Legacy calculation pending rebuild to selected supplied reference"
                )

                val resultsMap = mapOf(
                    "Legacy Engine Required As (unverified)" to "${"%.2f".format(res.requiredReinforcement)} mm²",
                    "Legacy Engine Provided As (unverified)" to "${"%.2f".format(res.providedReinforcement)} mm²",
                    "Bar Diameter" to "${res.barDiameter} mm",
                    "Rib Pitch (not bar spacing)" to "${params.ribSpacing} mm",
                    "Min Thickness" to "${res.minThickness} mm",
                    "Legacy Engine Shear Capacity (unverified)" to "${"%.1f".format(res.shearCapacity)} kN",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%",
                    "Engine Check Flag (unverified)" to if (res.isSafe) "PASS (unverified method)" else "FAIL",
                    "Detailing Status" to com.civileg.app.domain.calculations.utils.HordiReviewTakeoff.LIMITATION,
                    "Engineering Approval" to "NOT ISSUED - selected edition and adopted detailing not verified"
                )

                val specificReview = if (params.referenceBasis == com.civileg.app.domain.calculations.utils.RcReferenceBasis.ECP_2020)
                    com.civileg.app.domain.calculations.ecp.ECP2020JoistCaseReview.review(params.detailCase,
                        params.fcu, params.fy, params.ribWidth, params.ribSpacing, params.totalThickness, params.toppingThickness)
                    else null
                val partialPanelBottom = panelBottomTakeoffFor(params)
                val mainBodySchedule = scheduleFor(snapshot)
                val reviewInputRows = mapOf(
                    "Actual Rib Count" to (params.detailCase.actualRibCount?.toString() ?: "Missing"),
                    "Effective Flange Width" to (params.detailCase.effectiveFlangeWidthMm?.let { "$it mm, explicit input, code-width validation pending" } ?: "Missing"),
                    "Left Support Input" to params.detailCase.leftSupport.toString(),
                    "Right Support Input" to params.detailCase.rightSupport.toString(),
                    "Load Decomposition Input" to params.detailCase.loadBasis.toString(),
                    "Serviceability Input" to params.detailCase.serviceability.toString(),
                    "Detailing Input" to params.detailCase.detailing.toString(),
                    "Quantity Basis" to if (partialPanelBottom?.schedule != null)
                        "PARTIAL PANEL bottom straight bars with full straight development; concrete separately per periodic 1 m width, NOT same basis"
                        else "If issued: straight bodies over clear span for ONE RIB, no anchorage; concrete separately per periodic 1 m width",
                    "Main-Body Bar Source" to when {
                        specificReview != null && mainBodySchedule != null -> "Explicit case main-bar diameter/count, partial body review only"
                        specificReview != null -> "WITHHELD: explicit ECP case is incomplete, unsupported or failed section/shear checks; no legacy-bar fallback"
                        else -> "Legacy engine adopted diameter/count, unverified"
                    }
                )
                fun reviewNumber(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
                val specificRows = specificReview?.let { review -> buildMap<String, String> {
                    put("ECP 2020 Review Scope", "Partial case review only. No construction approval")
                    put("Unverified Design Requirements", review.limitations)
                    put("Missing Case Inputs", review.missingInputs.joinToString("; ").ifBlank { "None in input contract" })
                    put("Unsupported Case Conditions", review.unsupported.joinToString("; ").ifBlank { "None in bounded strategy" })
                    review.section?.let { check ->
                        put("ECP2020 Section Required Area incl Minimum", check.requiredAreaWithMinimumMm2?.let { "${reviewNumber(it)} mm2" } ?: "No singly reinforced solution")
                        put("ECP2020 Section Provided Area", "${reviewNumber(check.providedAreaMm2)} mm2")
                        put("ECP2020 Section Check ONLY", if (check.sectionPasses) "PASS - not full design" else "FAIL")
                        put("ECP2020 Effective Depth", "${reviewNumber(check.effectiveDepthMm)} mm")
                    }
                    review.concreteOnlyShearKn?.let { put("ECP2020 Concrete-only Threshold", "${reviewNumber(it)} kN") }
                    review.shearDemandWithinConcreteThreshold?.let { put("ECP2020 Shear Threshold Check", it.toString()) }
                    review.bottomStraightDevelopmentMm?.let { put("ECP2020 Straight Bottom Development", "${reviewNumber(it)} mm") }
                    review.leftAnchorageFits?.let { put("Left Straight Anchorage Fits", it.toString()) }
                    review.rightAnchorageFits?.let { put("Right Straight Anchorage Fits", it.toString()) }
                    review.effectiveSpanMm?.let { put("ECP2020 Simple Rib Effective Span", "${reviewNumber(it)} mm, total slab thickness and support center cap; demand not reanalysed") }
                    review.crossRibsRequired?.let { put("Cross Ribs Required - NOT DESIGNED", it.toString()) }
                    review.crossRibMinimums?.let { minimum ->
                        put("Cross-rib Minimum Section ONLY, 6-2-2-2", "Web >= ${reviewNumber(minimum.minimumWebWidthMm)} mm; total depth >= ${reviewNumber(minimum.minimumTotalDepthMm)} mm")
                        put("Cross-rib Minimum Bottom Steel ONLY", "As >= ${reviewNumber(minimum.minimumBottomAreaMm2)} mm2, main-rib provided area; not a bar design")
                        put("Cross-rib Top Steel Rule", "As top >= 0.5 x ACTUAL cross-rib bottom As. Demand, bars, positions and anchorage not designed or included in quantities")
                    }
                } }.orEmpty()

                val safetyChecks = emptyList<GenericSafetyCheck>()

                // No generated substitute for a missing real capture.
                val drawingBitmap = capturedDrawing

                val bbsEntries = mainBodySchedule?.let {
                    com.civileg.app.domain.calculations.utils.BbsGenerator.fromAdoptedSchedule("H1", it)
                }.orEmpty()
                val partialPanelEntries = partialPanelBottom?.schedule?.let {
                    com.civileg.app.domain.calculations.utils.BbsGenerator.fromAdoptedSchedule("HP", it)
                }.orEmpty()
                val mainBodiesWeightKg = bbsEntries.takeIf { it.isNotEmpty() }?.sumOf { it.totalWeightKg }

                val codeName = params.referenceBasis.edition
                val generated = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "",
                    titleEn = "Hordi Slab Calculation Review - Incomplete Detailing",
                    subtitle = "Code: $codeName | Span=${params.span/1000.0}m",
                    designType = "Hordi Slab",
                    inputs = inputsMap + reviewInputRows,
                    results = resultsMap + specificRows + mapOf(
                        "Partial Panel Bottom Steel Status" to (partialPanelBottom?.reasons?.joinToString("; ")?.ifBlank {
                            com.civileg.app.domain.calculations.ecp.ECP2020JoistBottomTakeoff.LIMITATION
                        } ?: "Not available for selected reference"),
                        "Schedule Basis" to when {
                            partialPanelEntries.isNotEmpty() -> "PARTIAL PANEL BOTTOM BARS ONLY, includes straight development. All other steel omitted. Concrete remains periodic 1m."
                            bbsEntries.isNotEmpty() -> "ONE RIB straight bodies only, no anchorage. Concrete remains periodic 1m."
                            else -> "NO STEEL SCHEDULE ISSUED. Concrete-only review quantity remains periodic 1m."
                        }
                    ),
                    safetyChecks = safetyChecks,
                    isSafe = false,
                    incompleteReview = true,
                    bbsEntries = partialPanelEntries.ifEmpty { bbsEntries },
                    boqRows = com.civileg.app.domain.calculations.utils.BoqEstimator.hordiReviewRows(
                        params.span, params.totalThickness, params.toppingThickness,
                        params.ribWidth, params.ribSpacing, mainBodiesWeightKg, partialPanelBottom?.schedule?.totalWeightKg
                    ),
                    drawingBitmap = drawingBitmap,
                    drawing = dxfDrawing,
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
        activeCalculation?.cancel()
        activeCalculation = null
        calculatedCase.invalidate()
        pendingDrawingBitmap = null
        _result.value = null
        _isLoading.value = false
        _error.value = null
    }

    fun saveDesign(projectId: Long, name: String) {
        // The current storage schema cannot preserve an explicit review-only
        // quantity basis. Do not persist partial steel as a completed design.
        _error.value = "Save is unavailable until the adopted detailing and quantity basis can be preserved"
    }

    /**
     * Helper data class to store input parameters for PDF export.
     */
    data class HordiSlabInputParams(
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
        val designCode: String,
        val coverMm: Double = 25.0,
        val detailCase: com.civileg.app.domain.calculations.utils.JoistDetailCase = com.civileg.app.domain.calculations.utils.JoistDetailCase(),
        val referenceBasis: com.civileg.app.domain.calculations.utils.RcReferenceBasis =
            com.civileg.app.domain.calculations.utils.RcReferenceBasis.forCode(designCode)
    )
}
