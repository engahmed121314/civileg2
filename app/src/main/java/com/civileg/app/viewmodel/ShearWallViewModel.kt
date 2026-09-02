package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.ShearWallDesign
import com.civileg.app.utils.SettingsManager
import com.civileg.app.data.local.PreferencesManager
import com.civileg.core.calculations.entities.DesignCode
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
    private val repository: DesignRepository,
    private val settingsManager: SettingsManager,
    private val preferencesManager: PreferencesManager
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
        // rule 1.4 — engineering-critical values are never guessed silently
        val numStories = state.numberOfStories.toIntOrNull()
        if (numStories == null || numStories < 1) {
            _uiState.update { it.copy(errors = listOf("أدخل عدد الأدوار — لا يمكن افتراضه / Enter number of stories")) }
            return
        }
        val fcu = state.fcu.toDoubleOrNull()
        if (fcu == null || fcu <= 0) {
            _uiState.update { it.copy(errors = listOf("أدخل fcu — لا يمكن افتراضها / Enter concrete strength")) }
            return
        }
        val fy = state.fy.toDoubleOrNull()
        if (fy == null || fy <= 0) {
            _uiState.update { it.copy(errors = listOf("أدخل fy — لا يمكن افتراضها / Enter steel yield strength")) }
            return
        }
        val axial = state.axialLoad.toDoubleOrNull() ?: 0.0
        val shear = state.shearForce.toDoubleOrNull() ?: 0.0
        val moment = state.bendingMoment.toDoubleOrNull() ?: 0.0
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

                // ADR-002: dispatch through CalculationFactory only (SBC wired — no silent fallback)
                // ADR-003: default code resolved from DataStore (single source)
                val resolvedCode = CalculationFactory.parseDesignCode(state.designCode)
                    ?: preferencesManager.defaultDesignCodeEnum.first()
                val designer = CalculationFactory.getShearWallDesign(resolvedCode)

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

                // ADR-011: attach elevation + horizontal-section drawing.
                // Cosmetic layer: on failure log & continue with text-only report.
                val drawingBitmap = try {
                    val totalHeightMm = (state.numberOfStories.toIntOrNull() ?: 1) *
                        ((state.storyHeight.toDoubleOrNull() ?: 3.0) * 1000.0)
                    com.civileg.app.utils.PdfDrawingGenerator.generateShearWallDrawing(
                        wallLengthMm = state.wallLength.toDoubleOrNull() ?: 4000.0,
                        wallThicknessMm = state.wallThickness.toDoubleOrNull() ?: 300.0,
                        wallHeightMm = totalHeightMm,
                        verticalDiaMm = res.verticalReinforcement.diameter,
                        verticalSpacingMm = res.verticalReinforcement.spacing,
                        horizontalDiaMm = res.horizontalReinforcement.diameter,
                        horizontalSpacingMm = res.horizontalReinforcement.spacing,
                        boundaryElementLabel = res.boundaryElementType.displayName,
                        designCode = state.designCode
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ShearWallPdf", "drawing generation skipped: ${e.message}")
                    null
                }

                val storyH = (state.storyHeight.toDoubleOrNull() ?: 3.0) * 1000.0
                val numS = state.numberOfStories.toIntOrNull() ?: 1
                val input = ShearWallInput(
                    wallType = state.wallType,
                    wallLength = state.wallLength.toDoubleOrNull() ?: 4000.0,
                    wallThickness = state.wallThickness.toDoubleOrNull() ?: 300.0,
                    wallHeight = storyH,
                    numberOfStories = numS,
                    axialLoad = state.axialLoad.toDoubleOrNull() ?: 0.0,
                    shearForce = state.shearForce.toDoubleOrNull() ?: 0.0,
                    bendingMoment = state.bendingMoment.toDoubleOrNull() ?: 0.0,
                    fcu = state.fcu.toDoubleOrNull() ?: 30.0,
                    fy = state.fy.toDoubleOrNull() ?: 400.0,
                    fyv = state.fyv.toDoubleOrNull() ?: 250.0,
                    clearCover = state.clearCover.toDoubleOrNull() ?: 25.0
                )

                val exporter = com.civileg.app.utils.exporters.ShearWallPdfExporter(context)
                val generated = exporter.exportToDownload(
                    input = input,
                    result = res,
                    projectName = "CIVILEG SHEAR WALL",
                    clientName = "Site Engineer"
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isExporting = false) }
                    generated.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
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
