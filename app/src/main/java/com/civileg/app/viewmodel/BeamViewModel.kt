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
                val fileName = "Beam_Report_${System.currentTimeMillis()}.pdf"
                val file = java.io.File(context.cacheDir, fileName)
                val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)
                
                // Generate drawing for PDF
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
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val domainCode = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
                    CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
                    else -> com.civileg.app.domain.entities.DesignCode.ECP
                }

                // Map actual support type to domain BeamType
                val beamType = when (lastSupportType) {
                    CalculatorEngine.SupportType.FIXED_FIXED -> com.civileg.app.domain.entities.BeamType.Fixed(lastSpan)
                    CalculatorEngine.SupportType.FIXED_HINGED -> com.civileg.app.domain.entities.BeamType.Fixed(lastSpan)
                    CalculatorEngine.SupportType.CANTILEVER -> com.civileg.app.domain.entities.BeamType.Cantilever(lastSpan)
                    else -> com.civileg.app.domain.entities.BeamType.SimplySupported(lastSpan)
                }
                
                val cover = 50.0
                val advResult = com.civileg.app.domain.entities.AdvancedBeamResult(
                    beamType = beamType,
                    sectionType = com.civileg.app.domain.entities.BeamSectionType.RECTANGULAR,
                    flexureResult = com.civileg.app.domain.entities.ReinforcementResult(
                        astRequired = res.reinforcementBottom.area * 0.9, // approximate required
                        astProvided = res.reinforcementBottom.area,
                        barDiameter = res.reinforcementBottom.diameter.toDouble(),
                        numberOfBars = res.reinforcementBottom.numBars,
                        tiesDiameter = res.stirrups.diameter.toDouble(),
                        tiesSpacing = res.stirrups.spacing,
                        isSafe = res.isSafe,
                        utilizationRatio = res.utilizationRatio
                    ),
                    shearResult = com.civileg.app.domain.entities.ShearReinforcementResult(
                        isSafe = res.isSafe,
                        utilizationRatio = res.utilizationRatio,
                        stirrupDiameter = res.stirrups.diameter.toDouble(),
                        stirrupSpacing = res.stirrups.spacing
                    ),
                    deflectionCheck = com.civileg.app.domain.entities.DeflectionCheckResult(
                        isSafe = res.deflection <= res.allowableDeflection,
                        calculatedDeflection = res.deflection,
                        allowableDeflection = res.allowableDeflection
                    ),
                    momentDiagram = emptyList(),
                    shearDiagram = emptyList(),
                    inventoryAnalysis = null,
                    crackWidthCheck = null,
                    developmentLengthCheck = null,
                    warnings = emptyList(),
                    codeNotes = listOf("Design per ${domainCode.version}")
                )

                // Use ACTUAL input values instead of hardcoded
                val beamInputs = com.civileg.app.domain.entities.BeamInputs(
                    fcu = lastFcu,
                    fy = lastFy,
                    width = lastWidth,
                    totalDepth = lastHeight,
                    effectiveDepth = lastHeight - cover,
                    designMoment = res.mu,
                    designShear = res.vu,
                    span = lastSpan,
                    deadLoad = lastDeadLoad,
                    liveLoad = lastLiveLoad
                )

                val exportedFile = exporter.exportBeamReport(
                    projectName = "تقرير تصميم كمرات - ${lastSupportType.displayName}",
                    designCode = domainCode,
                    beamType = beamType,
                    inputs = beamInputs,
                    result = advResult,
                    inventoryAnalysis = null,
                    momentShearDiagrams = com.civileg.app.domain.entities.MomentShearDiagrams(emptyList(), emptyList(), emptyList()),
                    outputPath = file.absolutePath,
                    drawingBitmap = drawingBitmap
                )
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    exportedFile?.let {
                        com.civileg.app.utils.ExportUtils.openPdf(context, it)
                    }
                    onComplete(exportedFile)
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
