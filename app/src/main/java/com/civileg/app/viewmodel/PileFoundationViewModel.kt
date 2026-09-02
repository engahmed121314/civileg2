package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.domain.*
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.PileFoundationDesign
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.SettingsManager
import com.civileg.app.data.local.PreferencesManager
import com.civileg.core.calculations.entities.DesignCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PileFoundationViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val settingsManager: SettingsManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _result = MutableLiveData<PileDesignResult?>()
    val result: LiveData<PileDesignResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Run the full pile foundation design.
     */
    fun designPileFoundation(
        pileType: PileType,
        pileDiameter: Double,
        pileLength: Double,
        numberOfPiles: Int,
        spacing: Double,
        axialLoad: Double,
        lateralLoad: Double,
        momentLoad: Double,
        fcu: Double,
        fy: Double,
        fyp: Double,
        soilType: SoilType,
        cu: Double,
        phi: Double,
        gammaSoil: Double,
        waterTableDepth: Double,
        embedmentDepth: Double,
        safetyFactor: Double,
        designCode: String = "",
        pileGroupPattern: String,
        eccentricityX: Double,
        eccentricityY: Double,
        scourDepth: Double,
        capConcreteCover: Double,
        columnWidth: Double,
        columnLength: Double
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val input = PileInput(
                    pileType = pileType,
                    pileDiameter = pileDiameter,
                    pileLength = pileLength,
                    numberOfPiles = numberOfPiles,
                    spacing = spacing,
                    axialLoad = axialLoad,
                    lateralLoad = lateralLoad,
                    momentLoad = momentLoad,
                    fcu = fcu,
                    fy = fy,
                    fyp = fyp,
                    soilType = soilType,
                    cu = cu,
                    phi = phi,
                    gammaSoil = gammaSoil,
                    waterTableDepth = waterTableDepth,
                    embedmentDepth = embedmentDepth,
                    safetyFactor = safetyFactor,
                    pileGroupPattern = pileGroupPattern,
                    eccentricityX = eccentricityX,
                    eccentricityY = eccentricityY,
                    scourDepth = scourDepth,
                    capConcreteCover = capConcreteCover,
                    columnWidth = columnWidth,
                    columnLength = columnLength
                )

                // ADR-002: dispatch through CalculationFactory only — code is now selectable (ECP/ACI/SBC)
                // ADR-003: default code resolved from DataStore (single source)
                val resolvedCode = CalculationFactory.parseDesignCode(designCode)
                    ?: preferencesManager.defaultDesignCodeEnum.first()
                val designEngine = CalculationFactory.getPileFoundationDesign(resolvedCode)
                val res = designEngine.designPile(input)
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Export the current result to PDF.
     */
    fun exportToPdf(context: Context, onComplete: (File?) -> Unit) {
        val res = _result.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isExporting.value = true }
            try {
                val fileName = "Pile_Foundation_Report_${System.currentTimeMillis()}.pdf"
                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, fileName)

                val inputsMap = mapOf(
                    "Pile Type" to res.pileType,
                    "Pile Diameter" to "${res.pileDiameterMm.toInt()} mm",
                    "Pile Length" to "${res.pileLengthM} m",
                    "Number of Piles" to "${res.numberOfPiles}",
                    "Soil Type" to res.soilType,
                    "fcu" to "${res.fcu} MPa",
                    "fy" to "${res.fy} MPa"
                )

                val capacity = res.capacityResult
                val group = res.groupResult
                val settlement = res.settlementResult
                val cap = res.capResult
                val reinf = res.pileReinforcement

                val resultsMap = mapOf(
                    "Ultimate Capacity" to "${"%.1f".format(capacity.ultimateCapacity)} kN",
                    "Allowable Capacity" to "${"%.1f".format(capacity.allowableCapacity)} kN",
                    "Shaft Resistance" to "${"%.1f".format(capacity.shaftResistance)} kN",
                    "End Bearing" to "${"%.1f".format(capacity.endBearingResistance)} kN",
                    "Group Efficiency" to "${"%.2f".format(group.efficiencyFactor)}",
                    "Group Capacity" to "${"%.1f".format(group.groupCapacity)} kN",
                    "Settlement" to "${"%.2f".format(settlement.totalSettlement)} mm",
                    "Lateral Capacity" to "${"%.1f".format(res.lateralCapacity)} kN",
                    "Cap Size" to "${cap.capWidth.toInt()}×${cap.capLength.toInt()}×${cap.capThickness.toInt()} mm",
                    "Pile Rebar" to reinf.barString
                )

                val safetyChecks = res.warnings.map { w ->
                    com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                        name = w, passed = false, calculated = 0.0, limit = 0.0, unit = ""
                    )
                }.toMutableList()
                safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                    name = "Punching Shear",
                    passed = cap.punchingShearOk,
                    calculated = cap.punchingShearStress,
                    limit = cap.punchingShearCapacity,
                    unit = "MPa"
                ))
                safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                    name = "Beam Shear",
                    passed = cap.beamShearOk,
                    calculated = cap.beamShearStress,
                    limit = cap.beamShearCapacity,
                    unit = "MPa"
                ))
                safetyChecks.add(com.civileg.app.utils.exporters.ComprehensivePdfExporter.GenericSafetyCheck(
                    name = "Settlement",                    passed = settlement.isOk,
                    calculated = settlement.totalSettlement,
                    limit = settlement.allowableSettlement,
                    unit = "mm"
                ))

                // ADR-011: attach engineering drawing (plan + section) to the report.
                // A bitmap failure is cosmetic — log & continue without blocking the report.
                val drawingBitmap = try {
                    com.civileg.app.utils.PdfDrawingGenerator.generatePileFoundationDrawing(
                        numberOfPiles = res.numberOfPiles,
                        pattern = group.pattern,
                        pileDiameterMm = res.pileDiameterMm,
                        pileSpacingMm = group.spacing,
                        capWidthMm = cap.capWidth,
                        capLengthMm = cap.capLength,
                        capThicknessMm = cap.capThickness,
                        columnWidthMm = res.columnWidth,
                        columnLengthMm = res.columnLength,
                        pileLengthM = res.pileLengthM
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PilePdf", "drawing generation skipped: ${e.message}")
                    null
                }

                val exporter = com.civileg.app.utils.exporters.PileFoundationPdfExporter(context)
                val generated = exporter.exportToDownload(
                    result = res,
                    projectName = "CIVILEG PILE FOUNDATION",
                    clientName = "Site Engineer"
                )

                withContext(Dispatchers.Main) {
                    generated.let { com.civileg.app.utils.ExportUtils.openPdf(context, it) }
                    onComplete(generated)
                    _isExporting.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "PDF export failed: ${e.message ?: ""}"
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Clear the current result.
     */
    fun clearResult() {
        _result.value = null
        _error.value = null
    }

    fun saveDesign(projectId: Long, name: String) {
        val res = _result.value ?: return
        viewModelScope.launch {
            repository.savePileFoundationDesign(projectId, name, res)
        }
    }
}
