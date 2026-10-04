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
import com.civileg.app.domain.calculations.base.FootingDesign
import com.civileg.app.domain.calculations.ecp.CombinedFootingResult
import com.civileg.app.domain.calculations.ecp.ECPCombinedFooting
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CombinedFootingUiState(
    // Column loads
    val p1: String = "1500",               // kN - column 1 axial load
    val p2: String = "2000",               // kN - column 2 axial load
    // Column dimensions
    val col1Width: String = "400",         // mm
    val col1Depth: String = "400",         // mm
    val col2Width: String = "400",         // mm
    val col2Depth: String = "400",         // mm
    // Geometry
    val distanceBetweenColumns: String = "5000",  // mm
    val soilBearingCapacity: String = "200",      // kN/m2
    val footingThickness: String = "500",         // mm
    // Materials
    val fcu: String = "25",                // MPa
    val fy: String = "360",                // MPa
    val cover: String = "75",              // mm
    // Code
    val designCode: String = "ECP",
    // Results
    val result: CombinedFootingResult? = null,
    val footingDesignResult: com.civileg.app.domain.calculations.base.FootingDesignResult? = null,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList()
)

@HiltViewModel
class CombinedFootingViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CombinedFootingUiState())
    val uiState: StateFlow<CombinedFootingUiState> = _uiState.asStateFlow()

    /** Bitmap captured from Compose drawing for PDF export. Set by Screen before calling exportToPdf. */
    @Volatile
    var pendingDrawingBitmap: Bitmap? = null

    private val _validationReport = MutableLiveData<String?>()
    val validationReport: LiveData<String?> = _validationReport

    fun updateDesignCode(code: String) {
        _uiState.update { it.copy(designCode = code) }
    }

    fun updateInputs(
        p1: String? = null,
        p2: String? = null,
        col1Width: String? = null,
        col1Depth: String? = null,
        col2Width: String? = null,
        col2Depth: String? = null,
        distanceBetweenColumns: String? = null,
        soilBearingCapacity: String? = null,
        footingThickness: String? = null,
        fcu: String? = null,
        fy: String? = null,
        cover: String? = null
    ) {
        _uiState.update {
            it.copy(
                p1 = p1 ?: it.p1,
                p2 = p2 ?: it.p2,
                col1Width = col1Width ?: it.col1Width,
                col1Depth = col1Depth ?: it.col1Depth,
                col2Width = col2Width ?: it.col2Width,
                col2Depth = col2Depth ?: it.col2Depth,
                distanceBetweenColumns = distanceBetweenColumns ?: it.distanceBetweenColumns,
                soilBearingCapacity = soilBearingCapacity ?: it.soilBearingCapacity,
                footingThickness = footingThickness ?: it.footingThickness,
                fcu = fcu ?: it.fcu,
                fy = fy ?: it.fy,
                cover = cover ?: it.cover
            )
        }
    }

    fun calculate() {
        val state = _uiState.value
        val p1 = state.p1.toDoubleOrNull() ?: return
        val p2 = state.p2.toDoubleOrNull() ?: return
        val c1w = state.col1Width.toDoubleOrNull() ?: return
        val c1d = state.col1Depth.toDoubleOrNull() ?: return
        val c2w = state.col2Width.toDoubleOrNull() ?: return
        val c2d = state.col2Depth.toDoubleOrNull() ?: return
        val dist = state.distanceBetweenColumns.toDoubleOrNull() ?: return
        val qAll = state.soilBearingCapacity.toDoubleOrNull() ?: return
        val tFtg = state.footingThickness.toDoubleOrNull() ?: 500.0
        val fcu = state.fcu.toDoubleOrNull() ?: 25.0
        val fy = state.fy.toDoubleOrNull() ?: 360.0

        InputGuard.positive("p1", p1)
        InputGuard.positive("p2", p2)
        InputGuard.positive("col1Width", c1w)
        InputGuard.positive("col1Depth", c1d)
        InputGuard.positive("col2Width", c2w)
        InputGuard.positive("col2Depth", c2d)
        InputGuard.positive("distanceBetweenColumns", dist)
        InputGuard.positive("soilBearingCapacity", qAll)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                when (state.designCode) {
                    "ECP" -> {
                        // Use specialized ECP combined footing engine
                        val designer: ECPCombinedFooting = CalculationFactory.getECPCombinedFootingSpecialized()
                        val result = designer.design(
                            p1 = p1,
                            p2 = p2,
                            col1Width = c1w,
                            col1Depth = c1d,
                            col2Width = c2w,
                            col2Depth = c2d,
                            distanceBetweenColumns = dist,
                            soilBearingCapacity = qAll,
                            footingThickness = tFtg,
                            fcu = fcu,
                            fy = fy
                        )
                        _uiState.update {
                            it.copy(result = result, footingDesignResult = null, isLoading = false, errors = emptyList())
                        }
                    }
                    else -> {
                        // ACI/SBC: route through FootingDesign interface
                        val code = DesignCode.valueOf(state.designCode)
                        val designer: FootingDesign = CalculationFactory.getCombinedFootingDesign(code)
                        val result = designer.designCombinedFooting(
                            fcu = fcu,
                            fy = fy,
                            axialLoad1 = p1,
                            axialLoad2 = p2,
                            distanceBetweenColumns = dist,
                            soilBearingCapacity = qAll,
                            footingDepth = tFtg,
                            loadCombination = LoadCombination.DEAD_LIVE,
                            columnWidth = c1w,
                            columnDepth = c1d,
                            col2Width = c2w,   // FIX: pass column 2 dimensions
                            col2Depth = c2d    // FIX: pass column 2 dimensions
                        )
                        _uiState.update {
                            it.copy(footingDesignResult = result, result = null, isLoading = false, errors = emptyList())
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errors = listOf(e.message ?: "Error")) }
            }
        }
    }

    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val state = _uiState.value
        val ecpRes = state.result
        val footingRes = state.footingDesignResult
        if (ecpRes == null && footingRes == null) return

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "CombinedFooting_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val inputsMap = mapOf(
                    "P1 (Col 1 Load)" to "${state.p1} kN",
                    "P2 (Col 2 Load)" to "${state.p2} kN",
                    "Col 1" to "${state.col1Width}×${state.col1Depth} mm",
                    "Col 2" to "${state.col2Width}×${state.col2Depth} mm",
                    "Distance Between Columns" to "${state.distanceBetweenColumns} mm",
                    "Soil Bearing Capacity" to "${state.soilBearingCapacity} kN/m²",
                    "Footing Thickness" to "${state.footingThickness} mm",
                    "f'cu" to "${state.fcu} MPa",
                    "fy" to "${state.fy} MPa",
                    "Cover" to "${state.cover} mm",
                    "Design Code" to state.designCode
                )

                val resultsMap = if (ecpRes != null) {
                    mapOf(
                        "Footing Length" to "${"%.2f".format(ecpRes.footingLength)} m",
                        "Footing Width" to "${"%.2f".format(ecpRes.footingWidth)} m",
                        "Effective Depth" to "${"%.0f".format(ecpRes.effectiveDepth)} mm",
                        "q Max" to "${"%.1f".format(ecpRes.qMax)} kN/m²",
                        "q Min" to "${"%.1f".format(ecpRes.qMin)} kN/m²",
                        "Long Bottom Rebar" to ecpRes.longBottomBars,
                        "Long Top Rebar" to ecpRes.longTopBars,
                        "Trans Bottom Rebar" to ecpRes.transBottomBars,
                        "One-Way Shear" to if (ecpRes.oneWayShearSafe) "PASS" else "FAIL",
                        "Punching Col 1" to if (ecpRes.punchingCol1Safe) "PASS" else "FAIL",
                        "Punching Col 2" to if (ecpRes.punchingCol2Safe) "PASS" else "FAIL",
                        "Concrete Volume" to "${"%.2f".format(ecpRes.concreteVolume)} m³",
                        "Steel Weight" to "${"%.1f".format(ecpRes.steelWeight)} kg",
                        "Is Safe" to if (ecpRes.isSafe) "YES" else "NO"
                    )
                } else {
                    val fr = footingRes!!
                    mapOf(
                        "Required Width" to "${"%.0f".format(fr.requiredWidth)} mm",
                        "Required Length" to "${"%.0f".format(fr.requiredLength)} mm",
                        "Soil Pressure" to "${"%.1f".format(fr.soilPressure)} kPa",
                        "Max Soil Pressure" to "${"%.1f".format(fr.maxSoilPressure)} kPa",
                        "Is Safe" to if (fr.isSafe) "YES" else "NO"
                    )
                }

                val isSafe = ecpRes?.isSafe ?: footingRes?.isSafe ?: true
                val safetyChecks = emptyList<GenericSafetyCheck>()

                val drawingBitmap = pendingDrawingBitmap
                pendingDrawingBitmap = null  // consume after use

                val codeName = state.designCode
                val generated = ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم قاعدة مشتركة",
                    titleEn = "Combined Footing Design Report",
                    subtitle = "Code: $codeName  •  P1=${state.p1}kN, P2=${state.p2}kN",
                    designType = "Combined Footing",
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = isSafe,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath
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
        _uiState.value = CombinedFootingUiState()
    }

    fun saveDesign(projectId: Long, name: String) {
        val ecpRes = _uiState.value.result ?: return
        viewModelScope.launch {
            val engineCode = when (DesignCode.valueOf(_uiState.value.designCode)) {
                DesignCode.ECP -> com.civileg.app.utils.CalculatorEngine.DesignCode.EGYPTIAN
                DesignCode.ACI -> com.civileg.app.utils.CalculatorEngine.DesignCode.ACI
                DesignCode.SBC -> com.civileg.app.utils.CalculatorEngine.DesignCode.SAUDI
            }
            repository.saveFootingDesign(projectId, name, com.civileg.app.utils.CalculatorEngine.FootingResult(
                type = com.civileg.app.utils.CalculatorEngine.FootingType.COMBINED,
                width = ecpRes.footingWidth * 1000, // m to mm
                length = ecpRes.footingLength * 1000,
                thickness = ecpRes.footingThickness,
                soilPressure = ecpRes.qMax,
                allowablePressure = ecpRes.qMax,
                reinforcementBottom = com.civileg.app.utils.CalculatorEngine.ReinforcementBar(),
                isSafe = ecpRes.isSafe,
                code = engineCode,
                concreteVolume = ecpRes.concreteVolume,
                steelWeight = ecpRes.steelWeight,
                cost = 0.0
            ))
        }
    }
}
