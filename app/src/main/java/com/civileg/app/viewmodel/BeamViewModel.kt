package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.CalculationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import javax.inject.Inject

@HiltViewModel
class BeamViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.BeamResult?>()
    val result: LiveData<CalculatorEngine.BeamResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _validationReport = MutableLiveData<CalculationValidator.ValidationReport?>()
    val validationReport: LiveData<CalculationValidator.ValidationReport?> = _validationReport

    private val _sanityResult = MutableLiveData<com.civileg.app.domain.safety.SanityResult?>()
    val sanityResult: LiveData<com.civileg.app.domain.safety.SanityResult?> = _sanityResult

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    private var lastSpan: Double = 5.0
    private var lastWidth: Double = 250.0
    private var lastHeight: Double = 600.0
    private var lastDeadLoad: Double = 15.0
    private var lastLiveLoad: Double = 10.0
    private var lastFcu: Double = 25.0
    private var lastFy: Double = 360.0
    private var lastSupportType: CalculatorEngine.SupportType = CalculatorEngine.SupportType.HINGED_HINGED

    fun calculateBeamPro(
        width: Double,
        height: Double,
        span: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.AppDesignCode,
        supportType: CalculatorEngine.SupportType
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Store all inputs for PDF export
                lastWidth = width
                lastHeight = height
                lastSpan = span
                lastDeadLoad = deadLoad
                lastLiveLoad = liveLoad
                lastFcu = fcu
                lastFy = fy
                lastSupportType = supportType

                val res = calculatorEngine.designBeam(
                    width = width,
                    height = height,
                    span = span,
                    fcu = fcu,
                    fy = fy,
                    deadLoad = deadLoad,
                    liveLoad = liveLoad,
                    preferredDiameter = preferredDiameter,
                    code = code
                )
                
                // Validate results for consistency & dead load logic
                val report = CalculationValidator.validateBeam(res)
                val dlReport = CalculationValidator.inspectDeadLoadConsistency("BEAM", mapOf("width" to width, "depth" to height), deadLoad)
                
                val combinedWarnings = report.warnings + dlReport.warnings
                _validationReport.value = report.copy(warnings = combinedWarnings)
                _sanityResult.value = com.civileg.app.domain.safety.EngineeringSanityEngine
                    .fromValidation(report.copy(warnings = combinedWarnings))
                
                _result.value = res
                lastSpan = span
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculateBeam(span: Double, load: Double, fcu: Double, fy: Double) {
        calculateBeamPro(250.0, 600.0, span, load * 0.6, load * 0.4, fcu, fy, 16, CalculatorEngine.AppDesignCode.EGYPTIAN, CalculatorEngine.SupportType.HINGED_HINGED)
    }

    fun saveBeam(projectId: Long, name: String, result: CalculatorEngine.BeamResult) {
        viewModelScope.launch {
            repository.saveBeamDesign(projectId, name, result, lastSpan, lastFcu, lastFy)
        }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                // CRITICAL FIX (2026-07-27): Use NativePdfExporter (Android-native)
                // instead of iText-based ComprehensivePdfExporter.
                // iText 8 AGPL lacks pdfCalligraph → garbled Arabic text.
                val fileName = "Beam_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, fileName)

                // Generate moment/shear point arrays for PDF diagrams.
                // R1-P016: case-aware statics (sources verified per ADR-010:
                //  SS+UDL      M=wL²/8 mid, V linear ±wL/2
                //  Cantilever  M=wL²/2 hogging @fixed end, V=wL
                //  Fixed-Fixed M_ends=wL²/12, M_mid=wL²/24, V ±wL/2
                //  Propped     M_fix=wL²/8, M_mid=9wL²/128, R_A=5wL/8
                // Equivalent UDL back-calculated from the ENGINE's design envelope).
                val L = lastSpan // span in meters
                val maxM = res.appliedMoment  // kN.m
                val maxV = res.appliedShear    // kN
                val st = res.supportType
                val wUDL = if (L > 0) when (st) {
                    CalculatorEngine.SupportType.CANTILEVER -> 2.0 * maxM / (L * L)
                    CalculatorEngine.SupportType.FIXED_FIXED -> 12.0 * maxM / (L * L)
                    CalculatorEngine.SupportType.FIXED_HINGED -> 8.0 * maxM / (L * L)
                    else -> 8.0 * maxM / (L * L)
                } else 0.0
                val numPoints = 21
                val momentPoints = when (st) {
                    CalculatorEngine.SupportType.CANTILEVER ->
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, -wUDL * (L - x) * (L - x) / 2.0)
                        }
                    CalculatorEngine.SupportType.FIXED_HINGED -> {
                        val rA = 5.0 * wUDL * L / 8.0
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, -wUDL * L * L / 8.0 + rA * x - wUDL * x * x / 2.0)
                        }
                    }
                    CalculatorEngine.SupportType.FIXED_FIXED ->
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, -wUDL * L * L / 12.0 + wUDL * L * x / 2.0 - wUDL * x * x / 2.0)
                        }
                    else ->
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, wUDL * x * (L - x) / 2.0)
                        }
                }
                val shearPoints = when (st) {
                    CalculatorEngine.SupportType.CANTILEVER ->
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, wUDL * (L - x))
                        }
                    CalculatorEngine.SupportType.FIXED_HINGED -> {
                        val rA = 5.0 * wUDL * L / 8.0
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, rA - wUDL * x)
                        }
                    }
                    else ->
                        (0..numPoints).map { i ->
                            val x = L * i / numPoints
                            Pair(x, wUDL * (L / 2.0 - x))
                        }
                }

                // R1: the screen capture (viewMode=0) now carries the per-case
                // BMD/SFD itself, so the captured drawing stands alone. The
                // generator (which also renders case-aware diagrams) remains the
                // fallback so P016 — "report always carries diagrams" — holds
                // even when no screen capture is available.
                val capturedDrawing = pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use
                val drawingBitmap: android.graphics.Bitmap? = capturedDrawing ?: try {
                        PdfDrawingGenerator.generateBeamDrawingWithDiagrams(
                            beamWidth = res.width.toDouble(),
                            beamDepth = res.depth.toDouble(),
                            span = lastSpan * 1000.0,
                            mainRebarDia = res.reinforcementBottom.diameter.toDouble(),
                            mainRebarCount = res.reinforcementBottom.numBars,
                            stirrupDia = res.stirrups.diameter.toDouble(),
                            stirrupSpacing = res.stirrups.spacing.toDouble(),
                            cover = 50.0,
                            hasTopSteel = res.reinforcementTop.numBars > 0,
                            topRebarDia = res.reinforcementTop.diameter.toDouble(),
                            topRebarCount = res.reinforcementTop.numBars,
                            momentPoints = momentPoints,
                            shearPoints = shearPoints,
                            maxMoment = maxM,
                            maxShear = maxV,
                            isSafe = res.isSafe,
                            // R2 (P044): the fallback draws engineering stirrup
                            // zones verbatim (dense @support / relaxed @mid).
                            stirrupZones = res.stirrups.zones.map { z ->
                                com.civileg.core.calculations.entities.StirrupZone(
                                    name = z.name,
                                    startLocation = z.startLocation,
                                    endLocation = z.endLocation,
                                    spacing = z.spacing,
                                    numLegs = z.numLegs,
                                    diameter = z.diameter,
                                    description = z.description
                                )
                            }
                        )
                    } catch (e: Exception) { e.printStackTrace(); null }

                val codeName = when(res.code) {
                    CalculatorEngine.AppDesignCode.ACI -> "ACI 318"
                    CalculatorEngine.AppDesignCode.SAUDI -> "SBC 304"
                    else -> "ECP 203"
                }

                val inputsMap = mapOf(
                    "Span" to "${lastSpan} m",
                    "Width" to "${res.width} mm",
                    "Depth" to "${res.depth} mm",
                    "Support" to lastSupportType.displayName,
                    "f'cu" to "${lastFcu} MPa",
                    "fy" to "${lastFy} MPa",
                    "Dead Load" to "${lastDeadLoad} kN/m",
                    "Live Load" to "${lastLiveLoad} kN/m",
                    "Design Code" to codeName
                )
                val resultsMap = mutableMapOf(
                    "Max Moment Mu" to "${String.format("%.2f", res.mu)} kN.m",
                    "Max Shear Vu" to "${String.format("%.2f", res.vu)} kN",
                    "Bottom Reinforcement" to res.reinforcementBottom.barString,
                    "Top Reinforcement" to res.reinforcementTop.barString,
                    "Stirrups" to res.stirrups.description,
                    "Deflection" to "${String.format("%.2f", res.deflection)} mm",
                    "Allowable Deflection" to "${String.format("%.2f", res.allowableDeflection)} mm",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%",
                    "Concrete Volume" to "${String.format("%.3f", res.concreteVolume)} m³",
                    "Steel Weight" to "${String.format("%.1f", res.steelWeight)} kg"
                )
                
                if (res.stirrups.zones.isNotEmpty()) {
                    res.stirrups.zones.forEachIndexed { i, zone ->
                         resultsMap["Distribution Zone ${i+1}"] = "${zone.name}: ${zone.description} [${String.format("%.1f", (zone.endLocation-zone.startLocation)/1000.0)}m]"
                    }
                }
                val safetyChecks = res.safetyChecks.map { chk ->
                    com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = chk.name, calculated = chk.value,
                        limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                    )
                }

                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم كمرات - ${lastSupportType.displayName}",
                    titleEn = "Beam Design Report — ${lastSupportType.displayName}",
                    subtitle = "Code: $codeName  •  Span=${lastSpan}m, ${res.width}×${res.depth}mm",
                    designType = "Beam (${lastSupportType.displayName})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    warnings = _validationReport.value?.warnings.orEmpty(),
                    outputPath = file.absolutePath,
                    context = context,
                    trace = res.trace
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: "Unknown error"}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
