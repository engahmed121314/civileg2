package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
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
                
                val domainCode = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
                    CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
                    else -> com.civileg.app.domain.entities.DesignCode.ECP
                }

                val beamType = com.civileg.app.domain.entities.BeamType.SimplySupported(5.0)
                
                val advResult = com.civileg.app.domain.entities.AdvancedBeamResult(
                    beamType = beamType,
                    sectionType = com.civileg.app.domain.entities.BeamSectionType.RECTANGULAR,
                    flexureResult = com.civileg.app.domain.entities.ReinforcementResult(
                        astRequired = 0.0,
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
                        isSafe = true,
                        calculatedDeflection = res.deflection,
                        allowableDeflection = res.allowableDeflection
                    ),
                    momentDiagram = emptyList(),
                    shearDiagram = emptyList(),
                    inventoryAnalysis = null,
                    crackWidthCheck = null,
                    developmentLengthCheck = null,
                    warnings = emptyList(),
                    codeNotes = emptyList()
                )

                val exportedFile = exporter.exportBeamReport(
                    projectName = "Beam Design Report",
                    designCode = domainCode,
                    beamType = beamType,
                    inputs = com.civileg.app.domain.entities.BeamInputs(
                        fcu = 25.0, fy = 400.0, width = res.width, totalDepth = res.depth,
                        effectiveDepth = res.depth - 50, designMoment = res.mu, designShear = res.vu,
                        span = 5.0, deadLoad = 2.0, liveLoad = 1.0
                    ),
                    result = advResult,
                    inventoryAnalysis = null,
                    momentShearDiagrams = com.civileg.app.domain.entities.MomentShearDiagrams(emptyList(), emptyList(), emptyList()),
                    outputPath = file.absolutePath
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
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }
}
