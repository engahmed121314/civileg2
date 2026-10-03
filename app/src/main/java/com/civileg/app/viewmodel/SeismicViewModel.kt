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
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import com.civileg.app.utils.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SeismicUiState(
    // Building parameters
    val totalWeight: String = "50000",            // kN
    val seismicZone: String = "ZONE_3",           // SeismicZone enum name
    val soilType: String = "C",                   // SoilType enum name
    val importanceFactor: String = "1.0",         // I
    val responseModFactor: String = "5.0",        // R
    val buildingHeight: String = "30.0",          // m
    // Floor data
    val floorWeights: List<Double> = emptyList(), // kN per floor
    val floorHeights: List<Double> = emptyList(), // m per floor from base
    // Code
    val designCode: String = "ECP",
    // Results
    val result: SeismicBaseShearResult? = null,
    val spectrumValues: List<SpectrumValue> = emptyList(),
    val forceDistribution: List<SeismicForceDistribution> = emptyList(),
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList()
)

@HiltViewModel
class SeismicViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeismicUiState())
    val uiState: StateFlow<SeismicUiState> = _uiState.asStateFlow()

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    private val _validationReport = MutableLiveData<String?>()
    val validationReport: LiveData<String?> = _validationReport

    fun updateDesignCode(code: String) {
        _uiState.update { it.copy(designCode = code) }
    }

    fun updateBuildingParams(
        totalWeight: String? = null,
        seismicZone: String? = null,
        soilType: String? = null,
        importanceFactor: String? = null,
        responseModFactor: String? = null,
        buildingHeight: String? = null
    ) {
        _uiState.update {
            it.copy(
                totalWeight = totalWeight ?: it.totalWeight,
                seismicZone = seismicZone ?: it.seismicZone,
                soilType = soilType ?: it.soilType,
                importanceFactor = importanceFactor ?: it.importanceFactor,
                responseModFactor = responseModFactor ?: it.responseModFactor,
                buildingHeight = buildingHeight ?: it.buildingHeight
            )
        }
    }

    fun updateFloorData(
        floorWeights: List<Double>,
        floorHeights: List<Double>
    ) {
        _uiState.update { it.copy(floorWeights = floorWeights, floorHeights = floorHeights) }
    }

    fun calculate() {
        val state = _uiState.value
        val totalWt = state.totalWeight.toDoubleOrNull() ?: return
        val impFactor = state.importanceFactor.toDoubleOrNull() ?: 1.0
        val respMod = state.responseModFactor.toDoubleOrNull() ?: 5.0
        val bldgH = state.buildingHeight.toDoubleOrNull() ?: 0.0

        val zone = try { SeismicZone.valueOf(state.seismicZone) } catch (_: Exception) { SeismicZone.ZONE_3 }
        val soil = try { SoilType.valueOf(state.soilType) } catch (_: Exception) { SoilType.C }

        InputGuard.positive("totalWeight", totalWt)
        InputGuard.positive("importanceFactor", impFactor)
        InputGuard.positive("responseModFactor", respMod)
        InputGuard.positive("buildingHeight", bldgH)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val designer: SeismicDesign = CalculationFactory.getSeismicDesign(
                    DesignCode.valueOf(state.designCode)
                )

                // Base shear
                val baseShearResult = designer.calculateBaseShear(
                    totalWeight = totalWt,
                    seismicZone = zone,
                    soilType = soil,
                    importanceFactor = impFactor,
                    responseModificationFactor = respMod,
                    buildingHeight = bldgH
                )

                // Response spectrum for multiple periods (0.1s to 2.0s)
                val spectrum = (1..20).map { i ->
                    val period = i * 0.1
                    designer.getResponseSpectrum(
                        period = period,
                        dampingRatio = 0.05,
                        soilType = soil,
                        importanceFactor = impFactor
                    )
                }

                // Force distribution (if floor data provided)
                val forces = if (state.floorWeights.isNotEmpty() && state.floorHeights.isNotEmpty()) {
                    designer.distributeSeismicForces(
                        baseShear = baseShearResult.baseShear,
                        floorWeights = state.floorWeights,
                        floorHeights = state.floorHeights
                    )
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        result = baseShearResult,
                        spectrumValues = spectrum,
                        forceDistribution = forces,
                        isLoading = false,
                        errors = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errors = listOf(e.message ?: "Error")) }
            }
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val state = _uiState.value
        val res = state.result ?: return

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "Seismic_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val inputsMap = mapOf(
                    "Total Weight" to "${state.totalWeight} kN",
                    "Seismic Zone" to state.seismicZone,
                    "Soil Type" to state.soilType,
                    "Importance Factor I" to state.importanceFactor,
                    "Response Mod. Factor R" to state.responseModFactor,
                    "Building Height" to "${state.buildingHeight} m",
                    "Design Code" to state.designCode
                )
                val resultsMap = mapOf(
                    "Base Shear V" to "${"%.1f".format(res.baseShear)} kN",
                    "Zone Factor Z" to "${"%.3f".format(res.zoneFactor)}",
                    "Soil Factor S" to "${"%.3f".format(res.soilFactor)}",
                    "Importance Factor I" to "${"%.2f".format(res.importanceFactor)}",
                    "Response Modification R" to "${"%.1f".format(res.responseModification)}",
                    "Code Reference" to res.codeReference
                )
                val safetyChecks = emptyList<GenericSafetyCheck>()

                val drawingBitmap = pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use

                val floorForces = state.forceDistribution.mapIndexed { idx, f ->
                    Pair(idx + 1, f.lateralForce)
                }
                val res = state.result
                val spectrumPeriod = state.spectrumValues.firstOrNull()?.period ?: 0.0
                val spectrumSa = state.spectrumValues.firstOrNull()?.spectralAcceleration ?: 0.0
                val exporter = ComprehensivePdfExporter(context)
                exporter.setLanguage(LocaleHelper.getLocale(context))
                val generated = exporter.exportSeismicReport(
                    projectName = "Seismic Design",
                    designCode = DesignCode.valueOf(state.designCode),
                    totalWeight = state.totalWeight.toDoubleOrNull() ?: 0.0,
                    baseShear = res.baseShear,
                    zoneFactor = res.zoneFactor,
                    soilFactor = res.soilFactor,
                    importanceFactor = res.importanceFactor,
                    responseModFactor = res.responseModification,
                    buildingHeight = state.buildingHeight.toDoubleOrNull() ?: 0.0,
                    period = spectrumPeriod,
                    spectralAcceleration = spectrumSa,
                    floorForces = floorForces,
                    isSafe = true,
                    outputPath = file.absolutePath,
                    drawingBitmap = drawingBitmap
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false) }
                    generated?.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                }
            } catch (e: Throwable) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false, errors = listOf("PDF Error: ${e.message}")) }
                    onComplete(null)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = SeismicUiState()
    }

    fun saveDesign(projectId: Long, name: String) {
        val res = _uiState.value.result ?: return
        viewModelScope.launch {
            val domainCode = DesignCode.valueOf(_uiState.value.designCode)
            val engineCode = when (domainCode) {
                DesignCode.ECP -> com.civileg.app.utils.CalculatorEngine.DesignCode.EGYPTIAN
                DesignCode.ACI -> com.civileg.app.utils.CalculatorEngine.DesignCode.ACI
                DesignCode.SBC -> com.civileg.app.utils.CalculatorEngine.DesignCode.SAUDI
            }
            repository.saveSeismicDesign(projectId, name, com.civileg.app.utils.CalculatorEngine.SeismicResult(
                baseShear = res.baseShear,
                storyDrift = 0.0,
                isSafe = true,
                code = engineCode,
                zone = res.zoneFactor,
                importance = res.importanceFactor,
                reductionFactor = res.responseModification
            ))
        }
    }
}
