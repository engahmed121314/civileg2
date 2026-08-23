package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.base.ShearWallDesign
import com.civileg.app.domain.calculations.ecp.ECPShearWall
import com.civileg.app.domain.calculations.aci.ACIShearWall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ShearWallUiState(
    // Wall type & shape
    val wallType: WallType = WallType.ORDINARY,
    val wallShape: String = "Rectangular",  // Rectangular, L-shaped, T-shaped
    val designCode: String = "ECP",
    // Geometry
    val wallLength: String = "4000",
    val wallThickness: String = "300",
    val storyHeight: String = "3.0",
    val numberOfStories: String = "10",
    // Loads
    val axialLoad: String = "5000",
    val shearForce: String = "800",
    val bendingMoment: String = "3000",
    // Materials
    val fcu: String = "30",
    val fy: String = "400",
    val fyv: String = "250",
    val clearCover: String = "25",
    // Flange (L/T walls)
    val flangeWidth: String = "0",
    val flangeThickness: String = "0",
    // Coupling beam
    val couplingBeamLength: String = "0",
    val couplingBeamHeight: String = "0",
    val couplingBeamClearSpan: String = "0",
    // Results
    val result: ShearWallResult? = null,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errors: List<String> = emptyList()
)

@HiltViewModel
class ShearWallViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShearWallUiState())
    val uiState: StateFlow<ShearWallUiState> = _uiState.asStateFlow()

    fun updateWallType(type: WallType) {
        _uiState.update { it.copy(wallType = type) }
        calculate()
    }

    fun updateWallShape(shape: String) {
        _uiState.update { it.copy(wallShape = shape) }
        // Show/hide flange fields
    }

    fun updateDesignCode(code: String) {
        _uiState.update { it.copy(designCode = code) }
        calculate()
    }

    fun updateGeometry(
        wallLength: String? = null,
        wallThickness: String? = null,
        storyHeight: String? = null,
        numberOfStories: String? = null
    ) {
        _uiState.update {
            it.copy(
                wallLength = wallLength ?: it.wallLength,
                wallThickness = wallThickness ?: it.wallThickness,
                storyHeight = storyHeight ?: it.storyHeight,
                numberOfStories = numberOfStories ?: it.numberOfStories
            )
        }
    }

    fun updateLoads(
        axialLoad: String? = null,
        shearForce: String? = null,
        bendingMoment: String? = null
    ) {
        _uiState.update {
            it.copy(
                axialLoad = axialLoad ?: it.axialLoad,
                shearForce = shearForce ?: it.shearForce,
                bendingMoment = bendingMoment ?: it.bendingMoment
            )
        }
    }

    fun updateMaterials(
        fcu: String? = null,
        fy: String? = null,
        fyv: String? = null,
        clearCover: String? = null
    ) {
        _uiState.update {
            it.copy(
                fcu = fcu ?: it.fcu,
                fy = fy ?: it.fy,
                fyv = fyv ?: it.fyv,
                clearCover = clearCover ?: it.clearCover
            )
        }
    }

    fun updateFlange(
        flangeWidth: String? = null,
        flangeThickness: String? = null
    ) {
        _uiState.update {
            it.copy(
                flangeWidth = flangeWidth ?: it.flangeWidth,
                flangeThickness = flangeThickness ?: it.flangeThickness
            )
        }
    }

    fun updateCoupling(
        couplingBeamLength: String? = null,
        couplingBeamHeight: String? = null,
        couplingBeamClearSpan: String? = null
    ) {
        _uiState.update {
            it.copy(
                couplingBeamLength = couplingBeamLength ?: it.couplingBeamLength,
                couplingBeamHeight = couplingBeamHeight ?: it.couplingBeamHeight,
                couplingBeamClearSpan = couplingBeamClearSpan ?: it.couplingBeamClearSpan
            )
        }
    }

    fun calculate() {
        val state = _uiState.value
        val wallLen = state.wallLength.toDoubleOrNull() ?: return
        val wallThk = state.wallThickness.toDoubleOrNull() ?: return
        val storyH = (state.storyHeight.toDoubleOrNull() ?: 3.0) * 1000.0  // m → mm
        val numStories = state.numberOfStories.toIntOrNull() ?: 10
        val axial = state.axialLoad.toDoubleOrNull() ?: 0.0
        val shear = state.shearForce.toDoubleOrNull() ?: 0.0
        val moment = state.bendingMoment.toDoubleOrNull() ?: 0.0
        val fcu = state.fcu.toDoubleOrNull() ?: 30.0
        val fy = state.fy.toDoubleOrNull() ?: 400.0
        val fyv = state.fyv.toDoubleOrNull() ?: 250.0
        val cover = state.clearCover.toDoubleOrNull() ?: 25.0
        val flangeW = state.flangeWidth.toDoubleOrNull() ?: 0.0
        val flangeT = state.flangeThickness.toDoubleOrNull() ?: 0.0
        val cbLen = state.couplingBeamLength.toDoubleOrNull() ?: 0.0
        val cbH = state.couplingBeamHeight.toDoubleOrNull() ?: 0.0
        val cbSpan = state.couplingBeamClearSpan.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val input = ShearWallInput(
                    wallType = state.wallType,
                    wallLength = wallLen,
                    wallThickness = wallThk,
                    wallHeight = storyH,
                    numberOfStories = numStories,
                    axialLoad = axial,
                    shearForce = shear,
                    bendingMoment = moment,
                    fcu = fcu,
                    fy = fy,
                    fyv = fyv,
                    clearCover = cover,
                    flangeWidth = flangeW,
                    flangeThickness = flangeT,
                    couplingBeamLength = cbLen,
                    couplingBeamHeight = cbH,
                    couplingBeamClearSpan = cbSpan
                )

                val designer: ShearWallDesign = when (state.designCode) {
                    "ACI" -> ACIShearWall()
                    else -> ECPShearWall()
                }

                val result = designer.designWall(input)
                _uiState.update { it.copy(result = result, isLoading = false, errors = emptyList()) }
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
                val fileName = "ShearWall_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val inputsMap = mapOf(
                    "Wall Type" to state.wallType.displayName,
                    "Wall Shape" to state.wallShape,
                    "Wall Length" to "${state.wallLength} mm",
                    "Wall Thickness" to "${state.wallThickness} mm",
                    "Story Height" to "${state.storyHeight} m",
                    "Number of Stories" to state.numberOfStories,
                    "f'cu" to "${state.fcu} MPa",
                    "fy" to "${state.fy} MPa",
                    "fyv" to "${state.fyv} MPa",
                    "Pu" to "${state.axialLoad} kN",
                    "Vu" to "${state.shearForce} kN",
                    "Mu" to "${state.bendingMoment} kN.m",
                    "Design Code" to state.designCode
                )
                val resultsMap = mapOf(
                    "Moment Capacity" to "${"%.1f".format(res.momentCapacity)} kN.m",
                    "Axial Capacity" to "${"%.1f".format(res.axialCapacity)} kN",
                    "Shear Capacity" to "${"%.1f".format(res.shearCapacity)} kN",
                    "Vertical Rebar" to "${res.verticalReinforcement.bars}Φ${res.verticalReinforcement.diameter} @ ${res.verticalReinforcement.spacing}mm",
                    "Horizontal Rebar" to "${res.horizontalReinforcement.bars}Φ${res.horizontalReinforcement.diameter} @ ${res.horizontalReinforcement.spacing}mm",
                    "Boundary Element" to res.boundaryElementType.displayName,
                    "Concrete/Story" to "${"%.3f".format(res.concreteVolumePerStory)} m³",
                    "Steel/Story" to "${"%.1f".format(res.steelWeightPerStory)} kg",
                    "Utilization" to "${(res.utilizationRatio * 100).toInt()}%"
                )
                val safetyChecks = res.safetyChecks.map {
                    com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = it.name, calculated = it.value,
                        limit = it.limit, unit = it.unit, passed = it.isSafe
                    )
                }

                val generated = com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.generateReportLegacy(
                    titleAr = "تقرير تصميم حائط قص",
                    titleEn = "Shear Wall Design Report",
                    subtitle = "${state.wallType.displayName} — ${state.designCode}",
                    designType = state.wallType.displayName,
                    inputs = inputsMap,
                    results = resultsMap,
                    safetyChecks = safetyChecks,
                    isSafe = res.isSafe,
                    drawingBitmap = null,
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
        _uiState.value = ShearWallUiState()
    }

    fun saveDesign(projectId: Long, name: String) {
        val res = _uiState.value.result ?: return
        viewModelScope.launch {
            repository.saveShearWallDesign(projectId, name, res)
        }
    }
}
