package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.PdfDrawingGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
        code: CalculatorEngine.DesignCode,
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
                    code = code,
                    supportType = supportType
                )
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
        calculateBeamPro(250.0, 600.0, span, load * 0.6, load * 0.4, fcu, fy, 16, CalculatorEngine.DesignCode.EGYPTIAN, CalculatorEngine.SupportType.HINGED_HINGED)
    }

    fun saveBeam(projectId: Long, name: String, result: CalculatorEngine.BeamResult) {
        viewModelScope.launch {
            repository.saveBeamDesign(projectId, name, result)
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

                // Generate drawing bitmap
                val drawingBitmap = try {
                    PdfDrawingGenerator.generateBeamDrawing(
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
                        topRebarCount = res.reinforcementTop.numBars
                    )
                } catch (e: Exception) { e.printStackTrace(); null }

                val codeName = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> "ACI 318"
                    CalculatorEngine.DesignCode.SAUDI -> "SBC 304"
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
                val resultsMap = mapOf(
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
                val safetyChecks = res.safetyChecks.map { chk ->
                    com.civileg.app.utils.NativePdfExporter.SafetyCheck(
                        name = chk.name, calculated = chk.value,
                        limit = chk.limit, unit = chk.unit, passed = chk.isSafe
                    )
                }

                val exporter = com.civileg.app.utils.NativePdfExporter(context)
                val generated = exporter.generateReport(
                    title = "Beam Design Report — ${lastSupportType.displayName}",
                    subtitle = "Code: $codeName  •  Span=${lastSpan}m, ${res.width}×${res.depth}mm",
                    designType = "Beam (${lastSupportType.displayName})",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
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
