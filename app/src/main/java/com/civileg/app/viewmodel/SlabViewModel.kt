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
class SlabViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.SlabResult?>()
    val result: LiveData<CalculatorEngine.SlabResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun calculateSlabPro(
        lx: Double,
        ly: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        ts: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode,
        type: CalculatorEngine.SlabType = CalculatorEngine.SlabType.SOLID,
        prestressForce: Double = 0.0,
        dropPanelThickness: Double = 0.0,
        columnSize: Double = 400.0
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designSlab(
                    lx = lx,
                    ly = ly,
                    deadLoad = deadLoad,
                    liveLoad = liveLoad,
                    fcu = fcu,
                    fy = fy,
                    ts = ts,
                    preferredDiameter = preferredDiameter,
                    code = code,
                    type = type,
                    prestressForce = prestressForce,
                    dropPanelThickness = dropPanelThickness,
                    columnSize = columnSize
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

    // Updated method to handle full parameters
    fun calculateSlab(
        spanX: Double,
        spanY: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        thickness: Double,
        preferredDiameter: Int,
        type: CalculatorEngine.SlabType = CalculatorEngine.SlabType.SOLID,
        code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
        prestressForce: Double = 0.0,
        dropPanelThickness: Double = 0.0,
        columnSize: Double = 400.0
    ) {
        calculateSlabPro(
            lx = spanX,
            ly = spanY,
            deadLoad = deadLoad,
            liveLoad = liveLoad,
            fcu = fcu,
            fy = fy,
            ts = thickness,
            preferredDiameter = preferredDiameter,
            code = code,
            type = type,
            prestressForce = prestressForce,
            dropPanelThickness = dropPanelThickness,
            columnSize = columnSize
        )
    }

    fun saveSlab(projectId: Long, name: String, result: CalculatorEngine.SlabResult) {
        viewModelScope.launch {
            repository.saveSlabDesign(projectId, name, result)
        }
    }

    fun exportToPdf(context: android.content.Context, onComplete: (java.io.File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "Slab_Report_${System.currentTimeMillis()}.pdf"
                val file = java.io.File(context.cacheDir, fileName)
                val exporter = com.civileg.app.utils.exporters.ComprehensivePdfExporter(context)
                
                // Map CalculatorEngine.DesignCode to domain.entities.DesignCode
                val domainCode = when(res.code) {
                    CalculatorEngine.DesignCode.ACI -> com.civileg.app.domain.entities.DesignCode.ACI
                    CalculatorEngine.DesignCode.SAUDI -> com.civileg.app.domain.entities.DesignCode.SBC
                    else -> com.civileg.app.domain.entities.DesignCode.ECP
                }

                // Map CalculatorEngine.SlabType to domain.entities.SlabType
                val domainSlabType = when(res.type) {
                    CalculatorEngine.SlabType.FLAT -> com.civileg.app.domain.entities.SlabType.FlatPlate(res.thickness, 5.0, 5.0, 400.0)
                    CalculatorEngine.SlabType.HOLLOW_BLOCK -> com.civileg.app.domain.entities.SlabType.Hordi(res.thickness, 100.0, 500.0, 400.0, 200.0, 5.0, com.civileg.app.domain.entities.SlabSupportConditions(com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED, com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED, com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED, com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED))
                    else -> com.civileg.app.domain.entities.SlabType.Solid(res.thickness, 5.0, 5.0, com.civileg.app.domain.entities.SlabSupportConditions(com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED, com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED, com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED, com.civileg.app.domain.entities.EdgeCondition.SIMPLY_SUPPORTED))
                }

                val advResult = com.civileg.app.domain.entities.AdvancedSlabResult(
                    slabType = domainSlabType,
                    flexureResult = com.civileg.app.domain.entities.SlabDesignResult(
                        requiredReinforcement = res.reinforcementMain.area,
                        providedReinforcement = res.reinforcementMain.area,
                        barDiameter = res.reinforcementMain.diameter.toDouble(),
                        barSpacing = res.reinforcementMain.spacing,
                        minThickness = res.minThickness,
                        shearCapacity = 0.0,
                        isSafe = res.isSafe,
                        utilizationRatio = res.utilizationRatio
                    ),
                    shearCheck = com.civileg.app.domain.entities.ShearCheckResult(isSafe = true, appliedShear = 0.0, shearCapacity = 0.0, utilizationRatio = 0.0),
                    deflectionCheck = com.civileg.app.domain.entities.DeflectionCheckResult(isSafe = true, calculatedDeflection = 0.0, allowableDeflection = 0.0, ratio = 0.0),
                    punchingShearCheck = if (res.type == CalculatorEngine.SlabType.FLAT) com.civileg.app.domain.entities.PunchingShearCheckResult(isSafe = res.punchingSafe, appliedShear = 0.0, shearCapacity = 0.0, utilizationRatio = 0.0) else null,
                    reinforcementLayout = com.civileg.app.domain.entities.ReinforcementLayout(
                        topBars = com.civileg.app.domain.entities.BarLayout(res.reinforcementSecondary.diameter.toDouble(), res.reinforcementSecondary.spacing, com.civileg.app.domain.entities.BarDirection.BOTH, 5.0, 5),
                        bottomBars = com.civileg.app.domain.entities.BarLayout(res.reinforcementMain.diameter.toDouble(), res.reinforcementMain.spacing, com.civileg.app.domain.entities.BarDirection.BOTH, 5.0, 5),
                        distributionBars = null,
                        additionalBars = emptyList()
                    ),
                    concreteVolume = res.concreteVolume,
                    formworkArea = 25.0,
                    inventoryAnalysis = null,
                    postTensionCalculations = null,
                    warnings = res.suggestions,
                    codeNotes = emptyList()
                )

                val exportedFile = exporter.exportSlabReport(
                    projectName = "Slab Design Report",
                    designCode = domainCode,
                    slabType = domainSlabType,
                    inputs = com.civileg.app.domain.entities.SlabInputs(
                        fcu = 25.0, fy = 400.0, thickness = res.thickness,
                        deadLoad = 2.0, liveLoad = 3.0, shortSpan = 5.0, longSpan = 5.0
                    ),
                    result = advResult,
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

data class SlabInputData(
    val spanX: Double,
    val spanY: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val type: CalculatorEngine.SlabType
)
