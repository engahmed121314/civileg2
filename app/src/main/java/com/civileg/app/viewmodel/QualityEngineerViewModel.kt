package com.civileg.app.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.data.local.PreferencesManager
import com.civileg.app.db.CategorySummary
import com.civileg.app.db.DailyTrend
import com.civileg.app.db.QualityDao
import com.civileg.app.db.QualityRecord
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.QualityControlEngine
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.SoilType
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════
// State holders
// ═══════════════════════════════════════════════════════════════════════

data class ConcreteQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val elementId: String = "",
    val volume: String = "",
    val slump: String = "",
    val fcu: String = "25",
    val sampleId: String = "",
    val strength7Days: String = "",
    val strength28Days: String = "",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class SoilQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val cohesion: String = "25",
    val frictionAngle: String = "30",
    val unitWeight: String = "18",
    val waterTableDepth: String = "5",
    val foundationWidth: String = "1.5",
    val foundationLength: String = "1.5",
    val foundationDepth: String = "1.0",
    val safetyFactor: String = "3",
    val soilResult: SoilBearingResult? = null,
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class RebarQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val barDiameter: String = "16",
    val numBars: String = "3",
    val fy: String = "400",
    val fcu: String = "25",
    val cover: String = "40",
    val beamWidth: String = "250",
    val effectiveDepth: String = "560",
    val rebarChecks: List<GenericSafetyCheck>? = null,
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class SteelQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val sectionKey: String = "IPE 200",
    val appliedMoment: String = "",
    val appliedShear: String = "",
    val steelFy: String = "275",
    val span: String = "",
    val steelChecks: List<GenericSafetyCheck>? = null,
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class RoadsQCState(
    val surfaceLayerThickness: String = "50",
    val baseLayerThickness: String = "150",
    val subbaseLayerThickness: String = "200",
    val compactionPercent: String = "97",
    val asphaltDensity: String = "23",
    val airVoids: String = "4",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class BridgesQCState(
    val concreteStrength: String = "30",
    val span: String = "20",
    val naturalFrequency: String = "2.5",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class TunnelsQCState(
    val rockClass: String = "III",
    val tunnelConvergence: String = "20",
    val tunnelDiameter: String = "6",
    val supportThickness: String = "150",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class BuildingsQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val buildingType: String = "Residential",
    val numFloors: String = "5",
    val floorHeight: String = "3",
    val totalWeight: String = "50000",
    val importanceFactor: String = "1",
    val sds: String = "0.5",
    val responseR: String = "5",
    val storyDrift: String = "0.015",
    val buildingChecks: List<GenericSafetyCheck>? = null,
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class ReportsQCState(
    val totalInspections: Int = 0,
    val passedInspections: Int = 0,
    val failedInspections: Int = 0,
    val successRate: Double = 0.0,
    val records: List<QualityRecord> = emptyList(),
    val categorySummary: List<CategorySummary> = emptyList(),
    val dailyTrend: List<DailyTrend> = emptyList()
)

// ═══════════════════════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════════════════════

@HiltViewModel
class QualityEngineerViewModel @Inject constructor(
    private val qualityDao: QualityDao,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val gson = Gson()
    private val bearing = SoilBearingCalculator()

    private val _concreteState = MutableStateFlow(ConcreteQCState())
    val concreteState: StateFlow<ConcreteQCState> = _concreteState.asStateFlow()

    private val _soilState = MutableStateFlow(SoilQCState())
    val soilState: StateFlow<SoilQCState> = _soilState.asStateFlow()

    private val _rebarState = MutableStateFlow(RebarQCState())
    val rebarState: StateFlow<RebarQCState> = _rebarState.asStateFlow()

    private val _steelState = MutableStateFlow(SteelQCState())
    val steelState: StateFlow<SteelQCState> = _steelState.asStateFlow()

    private val _roadsState = MutableStateFlow(RoadsQCState())
    val roadsState: StateFlow<RoadsQCState> = _roadsState.asStateFlow()

    private val _bridgesState = MutableStateFlow(BridgesQCState())
    val bridgesState: StateFlow<BridgesQCState> = _bridgesState.asStateFlow()

    private val _tunnelsState = MutableStateFlow(TunnelsQCState())
    val tunnelsState: StateFlow<TunnelsQCState> = _tunnelsState.asStateFlow()

    private val _buildingsState = MutableStateFlow(BuildingsQCState())
    val buildingsState: StateFlow<BuildingsQCState> = _buildingsState.asStateFlow()

    private val _reportsState = MutableStateFlow(ReportsQCState())
    val reportsState: StateFlow<ReportsQCState> = _reportsState.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                qualityDao.getAllRecords(),
                qualityDao.countAll(),
                qualityDao.countPassed(),
                qualityDao.getCategorySummary(),
                qualityDao.getDailyTrend()
            ) { records, total, passed, summary, trend ->
                ReportsQCState(
                    totalInspections = total,
                    passedInspections = passed,
                    failedInspections = total - passed,
                    successRate = if (total > 0) passed * 100.0 / total else 0.0,
                    records = records,
                    categorySummary = summary,
                    dailyTrend = trend
                )
            }.collect { _reportsState.value = it }
        }
    }

    // ---------------------------------------------------------------
    // Design code
    // ---------------------------------------------------------------
    fun updateDesignCode(code: CalculatorEngine.DesignCode) {
        _concreteState.value = _concreteState.value.copy(designCode = code)
        _soilState.value = _soilState.value.copy(designCode = code)
        _rebarState.value = _rebarState.value.copy(designCode = code)
        _steelState.value = _steelState.value.copy(designCode = code)
        _buildingsState.value = _buildingsState.value.copy(designCode = code)
    }

    // ---------------------------------------------------------------
    // Field updaters
    // ---------------------------------------------------------------
    fun updateConcreteField(field: String, value: String) {
        _concreteState.value = when (field) {
            "elementId" -> _concreteState.value.copy(elementId = value)
            "volume" -> _concreteState.value.copy(volume = value)
            "slump" -> _concreteState.value.copy(slump = value)
            "fcu" -> _concreteState.value.copy(fcu = value)
            "sampleId" -> _concreteState.value.copy(sampleId = value)
            "strength7Days" -> _concreteState.value.copy(strength7Days = value)
            "strength28Days" -> _concreteState.value.copy(strength28Days = value)
            else -> _concreteState.value
        }
    }

    fun updateSoilField(field: String, value: String) {
        _soilState.value = when (field) {
            "cohesion" -> _soilState.value.copy(cohesion = value)
            "frictionAngle" -> _soilState.value.copy(frictionAngle = value)
            "unitWeight" -> _soilState.value.copy(unitWeight = value)
            "waterTableDepth" -> _soilState.value.copy(waterTableDepth = value)
            "foundationWidth" -> _soilState.value.copy(foundationWidth = value)
            "foundationLength" -> _soilState.value.copy(foundationLength = value)
            "foundationDepth" -> _soilState.value.copy(foundationDepth = value)
            "safetyFactor" -> _soilState.value.copy(safetyFactor = value)
            else -> _soilState.value
        }
    }

    fun updateRebarField(field: String, value: String) {
        _rebarState.value = when (field) {
            "barDiameter" -> _rebarState.value.copy(barDiameter = value)
            "numBars" -> _rebarState.value.copy(numBars = value)
            "fy" -> _rebarState.value.copy(fy = value)
            "fcu" -> _rebarState.value.copy(fcu = value)
            "cover" -> _rebarState.value.copy(cover = value)
            "beamWidth" -> _rebarState.value.copy(beamWidth = value)
            "effectiveDepth" -> _rebarState.value.copy(effectiveDepth = value)
            else -> _rebarState.value
        }
    }

    fun updateSteelField(field: String, value: String) {
        _steelState.value = when (field) {
            "sectionKey" -> _steelState.value.copy(sectionKey = value)
            "appliedMoment" -> _steelState.value.copy(appliedMoment = value)
            "appliedShear" -> _steelState.value.copy(appliedShear = value)
            "steelFy" -> _steelState.value.copy(steelFy = value)
            "span" -> _steelState.value.copy(span = value)
            else -> _steelState.value
        }
    }

    fun updateRoadsField(field: String, value: String) {
        _roadsState.value = when (field) {
            "surfaceLayerThickness" -> _roadsState.value.copy(surfaceLayerThickness = value)
            "baseLayerThickness" -> _roadsState.value.copy(baseLayerThickness = value)
            "subbaseLayerThickness" -> _roadsState.value.copy(subbaseLayerThickness = value)
            "compactionPercent" -> _roadsState.value.copy(compactionPercent = value)
            "asphaltDensity" -> _roadsState.value.copy(asphaltDensity = value)
            "airVoids" -> _roadsState.value.copy(airVoids = value)
            else -> _roadsState.value
        }
    }

    fun updateBridgesField(field: String, value: String) {
        _bridgesState.value = when (field) {
            "concreteStrength" -> _bridgesState.value.copy(concreteStrength = value)
            "span" -> _bridgesState.value.copy(span = value)
            "naturalFrequency" -> _bridgesState.value.copy(naturalFrequency = value)
            else -> _bridgesState.value
        }
    }

    fun updateTunnelsField(field: String, value: String) {
        _tunnelsState.value = when (field) {
            "rockClass" -> _tunnelsState.value.copy(rockClass = value)
            "tunnelConvergence" -> _tunnelsState.value.copy(tunnelConvergence = value)
            "tunnelDiameter" -> _tunnelsState.value.copy(tunnelDiameter = value)
            "supportThickness" -> _tunnelsState.value.copy(supportThickness = value)
            else -> _tunnelsState.value
        }
    }

    fun updateBuildingsField(field: String, value: String) {
        _buildingsState.value = when (field) {
            "buildingType" -> _buildingsState.value.copy(buildingType = value)
            "numFloors" -> _buildingsState.value.copy(numFloors = value)
            "floorHeight" -> _buildingsState.value.copy(floorHeight = value)
            "totalWeight" -> _buildingsState.value.copy(totalWeight = value)
            "importanceFactor" -> _buildingsState.value.copy(importanceFactor = value)
            "sds" -> _buildingsState.value.copy(sds = value)
            "responseR" -> _buildingsState.value.copy(responseR = value)
            "storyDrift" -> _buildingsState.value.copy(storyDrift = value)
            else -> _buildingsState.value
        }
    }

    // ---------------------------------------------------------------
    // CONCRETE
    // ---------------------------------------------------------------
    fun saveConcretePour(context: Context) {
        val s = _concreteState.value
        val volume = InputGuard.nonNegative("volume", s.volume.toDoubleOrNull() ?: 0.0)
        val slump = InputGuard.inRange("slump", s.slump.toDoubleOrNull() ?: 0.0, 0.0, 500.0)
        val fcu = InputGuard.positive("fcu", s.fcu.toDoubleOrNull() ?: 25.0)
        val s7 = InputGuard.nonNegative("strength7Days", s.strength7Days.toDoubleOrNull() ?: 0.0)
        val s28 = InputGuard.nonNegative("strength28Days", s.strength28Days.toDoubleOrNull() ?: 0.0)
        val elementId = InputGuard.notBlank("elementId", s.elementId.ifBlank { s.sampleId.ifBlank { "CONCRETE" } })
        
        val outcome = QualityControlEngine.concreteQc(
            code = s.designCode,
            elementId = elementId,
            volumeM3 = volume,
            slumpMm = slump,
            fcuDesignMpa = fcu,
            strength7DaysMpa = s7,
            strength28DaysMpa = s28
        )
        _concreteState.value = s.copy(outcome = outcome)
        persist(
            category = QualityControlEngine.Category.CONCRETE,
            elementId = elementId,
            code = s.designCode,
            outcome = outcome
        )
    }

    // ---------------------------------------------------------------
    // SOIL
    // ---------------------------------------------------------------
    fun calculateSoilBearing(method: BearingMethod, soilType: SoilType) {
        val s = _soilState.value
        val input = SoilBearingInput(
            method = method,
            soilType = soilType,
            foundationWidth = s.foundationWidth.toDoubleOrNull() ?: 1.5,
            foundationLength = s.foundationLength.toDoubleOrNull() ?: 1.5,
            foundationDepth = s.foundationDepth.toDoubleOrNull() ?: 1.0,
            cohesion = s.cohesion.toDoubleOrNull() ?: 25.0,
            frictionAngle = s.frictionAngle.toDoubleOrNull() ?: 30.0,
            unitWeight = s.unitWeight.toDoubleOrNull() ?: 18.0,
            waterTableDepth = s.waterTableDepth.toDoubleOrNull() ?: 5.0,
            safetyFactor = s.safetyFactor.toDoubleOrNull() ?: 3.0
        )
        val result = when (method) {
            BearingMethod.TERZAGHI -> bearing.calculateTerzaghi(input)
            BearingMethod.MEYERHOF -> bearing.calculateMeyerhof(input)
            BearingMethod.HANSEN -> bearing.calculateHansen(input)
            BearingMethod.VESIC -> bearing.calculateVesic(input)
        }

        // Persist as a quality record (reuse the concrete-style summary path)
        val checks = listOf(
            GenericSafetyCheck("Allowable bearing capacity", result.allowableBearingCapacity, 0.0, "kPa", result.isSafe),
            GenericSafetyCheck("Estimated settlement", result.settlement, 25.0, "mm", result.settlement <= 25.0)
        )
        val outcome = QualityControlEngine.QcOutcome(
            title = "Soil & Foundation Quality Control",
            checks = checks,
            details = listOf(
                "Method" to method.name,
                "Soil type" to soilType.name,
                "Gross bearing q_u" to "%.1f kPa".format(result.grossBearingCapacity),
                "Net bearing q_net" to "%.1f kPa".format(result.netBearingCapacity),
                "Allowable q_all" to "%.1f kPa".format(result.allowableBearingCapacity),
                "Settlement" to "%.1f mm".format(result.settlement)
            ),
            passed = result.isSafe,
            utilizationRatio = 0.0,
            summary = "Soil bearing capacity (${method.name}) — ${if (result.isSafe) "safe" else "unsafe"}"
        )
        _soilState.value = s.copy(soilResult = result, outcome = outcome)
        persist(
            category = QualityControlEngine.Category.SOIL,
            elementId = "FOUNDATION",
            code = s.designCode,
            outcome = outcome
        )
    }

    // ---------------------------------------------------------------
    // REBAR
    // ---------------------------------------------------------------
    fun calculateRebarChecks() {
        val s = _rebarState.value
        val barDia = InputGuard.positive("barDiameter", s.barDiameter.toDoubleOrNull() ?: 16.0)
        val nBars = InputGuard.positive("numBars", s.numBars.toIntOrNull() ?: 3)
        val fy = InputGuard.positive("fy", s.fy.toDoubleOrNull() ?: 400.0)
        val fcu = InputGuard.positive("fcu", s.fcu.toDoubleOrNull() ?: 25.0)
        val cover = InputGuard.inRange("cover", s.cover.toDoubleOrNull() ?: 40.0, 10.0, 100.0)
        val b = InputGuard.positive("beamWidth", s.beamWidth.toDoubleOrNull() ?: 250.0)
        val d = InputGuard.positive("effectiveDepth", s.effectiveDepth.toDoubleOrNull() ?: 560.0)
        
        val outcome = QualityControlEngine.rebarQc(
            code = s.designCode,
            barDiameterMm = barDia,
            numBars = nBars,
            fyMpa = fy,
            fcuMpa = fcu,
            coverMm = cover,
            beamWidthMm = b,
            effectiveDepthMm = d
        )
        _rebarState.value = s.copy(rebarChecks = outcome.checks, outcome = outcome)
        persist(
            category = QualityControlEngine.Category.REBAR,
            elementId = "REBAR-${barDia.toInt()}x${nBars}",
            code = s.designCode,
            outcome = outcome
        )
    }

    // ---------------------------------------------------------------
    // STEEL
    // ---------------------------------------------------------------
    fun calculateSteelChecks(section: String) {
        val s = _steelState.value.copy(sectionKey = section)
        val fy = InputGuard.positive("steelFy", s.steelFy.toDoubleOrNull() ?: 275.0)
        val span = InputGuard.positive("span", s.span.toDoubleOrNull() ?: 5.0)
        val mu = InputGuard.nonNegative("appliedMoment", s.appliedMoment.toDoubleOrNull() ?: 0.0)
        val vu = InputGuard.nonNegative("appliedShear", s.appliedShear.toDoubleOrNull() ?: 0.0)
        
        val outcome = QualityControlEngine.steelQc(
            code = s.designCode,
            sectionKey = section,
            fyMpa = fy,
            spanM = span,
            muKnM = mu,
            vuKn = vu
        )
        _steelState.value = s.copy(steelChecks = outcome.checks, outcome = outcome)
        persist(
            category = QualityControlEngine.Category.STEEL,
            elementId = section,
            code = s.designCode,
            outcome = outcome
        )
    }

    // ---------------------------------------------------------------
    // ROADS
    // ---------------------------------------------------------------
    fun calculateRoadsChecks() {
        val s = _roadsState.value
        val surface = InputGuard.inRange("surfaceLayerThickness", s.surfaceLayerThickness.toDoubleOrNull() ?: 50.0, 20.0, 100.0)
        val base = InputGuard.inRange("baseLayerThickness", s.baseLayerThickness.toDoubleOrNull() ?: 150.0, 100.0, 500.0)
        val subbase = InputGuard.inRange("subbaseLayerThickness", s.subbaseLayerThickness.toDoubleOrNull() ?: 200.0, 100.0, 500.0)
        val compaction = InputGuard.inRange("compactionPercent", s.compactionPercent.toDoubleOrNull() ?: 97.0, 80.0, 100.0)
        val density = InputGuard.inRange("asphaltDensity", s.asphaltDensity.toDoubleOrNull() ?: 23.0, 20.0, 26.0)
        val voids = InputGuard.inRange("airVoids", s.airVoids.toDoubleOrNull() ?: 4.0, 0.0, 10.0)
        
        val outcome = QualityControlEngine.roadsQc(
            surfaceThicknessMm = surface,
            baseThicknessMm = base,
            subbaseThicknessMm = subbase,
            compactionPercent = compaction,
            asphaltDensityKnM3 = density,
            airVoidsPercent = voids
        )
        _roadsState.value = s.copy(outcome = outcome)
        persist(QualityControlEngine.Category.ROADS, "PAVEMENT", CalculatorEngine.DesignCode.EGYPTIAN, outcome)
    }

    // ---------------------------------------------------------------
    // BRIDGES
    // ---------------------------------------------------------------
    fun calculateBridgeChecks() {
        val s = _bridgesState.value
        val concStrength = InputGuard.positive("concreteStrength", s.concreteStrength.toDoubleOrNull() ?: 30.0)
        val span = InputGuard.positive("span", s.span.toDoubleOrNull() ?: 20.0)
        val freq = InputGuard.inRange("naturalFrequency", s.naturalFrequency.toDoubleOrNull() ?: 2.5, 0.1, 10.0)
        
        val outcome = QualityControlEngine.bridgeQc(
            code = CalculatorEngine.DesignCode.EGYPTIAN,
            concreteStrengthMpa = concStrength,
            spanM = span,
            naturalFrequencyHz = freq
        )
        _bridgesState.value = s.copy(outcome = outcome)
        persist(QualityControlEngine.Category.BRIDGES, "BRIDGE", CalculatorEngine.DesignCode.EGYPTIAN, outcome)
    }

    // ---------------------------------------------------------------
    // TUNNELS
    // ---------------------------------------------------------------
    fun calculateTunnelChecks() {
        val s = _tunnelsState.value
        val convergence = InputGuard.nonNegative("tunnelConvergence", s.tunnelConvergence.toDoubleOrNull() ?: 20.0)
        val diameter = InputGuard.positive("tunnelDiameter", s.tunnelDiameter.toDoubleOrNull() ?: 6.0)
        val support = InputGuard.positive("supportThickness", s.supportThickness.toDoubleOrNull() ?: 150.0)
        
        val outcome = QualityControlEngine.tunnelQc(
            rockClass = s.rockClass,
            convergenceMm = convergence,
            diameterM = diameter,
            supportThicknessMm = support
        )
        _tunnelsState.value = s.copy(outcome = outcome)
        persist(QualityControlEngine.Category.TUNNELS, "TUNNEL-${s.rockClass}", CalculatorEngine.DesignCode.EGYPTIAN, outcome)
    }

    // ---------------------------------------------------------------
    // BUILDINGS
    // ---------------------------------------------------------------
    fun calculateBuildingChecks(buildingType: String) {
        val s = _buildingsState.value.copy(buildingType = buildingType)
        val nFloors = InputGuard.inRange("numFloors", s.numFloors.toIntOrNull() ?: 5, 1, 100)
        val floorH = InputGuard.inRange("floorHeight", s.floorHeight.toDoubleOrNull() ?: 3.0, 2.0, 5.0)
        val weight = InputGuard.positive("totalWeight", s.totalWeight.toDoubleOrNull() ?: 50000.0)
        val importance = InputGuard.inRange("importanceFactor", s.importanceFactor.toDoubleOrNull() ?: 1.0, 1.0, 1.5)
        val sdsVal = InputGuard.positive("sds", s.sds.toDoubleOrNull() ?: 0.5)
        val rVal = InputGuard.inRange("responseR", s.responseR.toDoubleOrNull() ?: 5.0, 1.0, 10.0)
        val drift = InputGuard.positive("storyDrift", s.storyDrift.toDoubleOrNull() ?: 0.015)
        
        val outcome = QualityControlEngine.buildingQc(
            code = s.designCode,
            numFloors = nFloors,
            floorHeightM = floorH,
            totalWeightKn = weight,
            importanceFactor = importance,
            sds = sdsVal,
            responseR = rVal,
            storyDrift = drift
        )
        _buildingsState.value = s.copy(buildingChecks = outcome.checks, outcome = outcome)
        persist(
            category = QualityControlEngine.Category.BUILDINGS,
            elementId = s.buildingType,
            code = s.designCode,
            outcome = outcome
        )
    }

    // ---------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------
    private fun persist(
        category: String,
        elementId: String,
        code: CalculatorEngine.DesignCode,
        outcome: QualityControlEngine.QcOutcome
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = QualityRecord(
                category = category,
                elementId = elementId,
                designCode = QualityControlEngine.codeName(code),
                passed = outcome.passed,
                utilizationRatio = outcome.utilizationRatio,
                summary = outcome.summary,
                dataJson = gson.toJson(
                    mapOf(
                        "title" to outcome.title,
                        "details" to outcome.details.toMap(),
                        "checks" to outcome.checks
                    )
                )
            )
            runCatching { qualityDao.insertRecord(record) }
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch(Dispatchers.IO) { qualityDao.deleteAll() }
    }

    fun deleteQualityRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { qualityDao.deleteById(id) }
    }

    // ---------------------------------------------------------------
    // CSV export
    // ---------------------------------------------------------------
    fun exportQualityCsv(
        context: Context,
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, "Quality_Records_${System.currentTimeMillis()}.csv")

                val records = qualityDao.getAllRecords().first()
                val sb = StringBuilder()
                sb.appendLine("ID,Category,Element ID,Design Code,Passed,Utilization Ratio,Summary,Created At,Notes")
                records.forEach { r ->
                    sb.append("${r.id},${r.category},${r.elementId},${r.designCode},${r.passed},${r.utilizationRatio},${r.summary.replace(",", ";")},${r.createdAt.time},${r.notes.replace(",", ";")}")
                    sb.appendLine()
                }
                file.writeText(sb.toString())

                withContext(Dispatchers.Main) {
                    ExportUtils.openFile(context, file, "text/csv")
                    _isExporting.value = false
                    onComplete(file)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // JSON Backup / Restore
    // ---------------------------------------------------------------
    fun exportQualityBackup(
        context: Context,
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, "Quality_Backup_${System.currentTimeMillis()}.json")

                val records = qualityDao.getAllRecords().first()
                val backup = mapOf(
                    "exportedAt" to System.currentTimeMillis(),
                    "version" to 1,
                    "records" to records.map { r ->
                        mapOf(
                            "id" to r.id,
                            "projectId" to r.projectId,
                            "category" to r.category,
                            "elementId" to r.elementId,
                            "designCode" to r.designCode,
                            "passed" to r.passed,
                            "utilizationRatio" to r.utilizationRatio,
                            "summary" to r.summary,
                            "dataJson" to r.dataJson,
                            "notes" to r.notes,
                            "createdAt" to r.createdAt.time,
                            "inspectorName" to r.inspectorName,
                            "location" to r.location,
                            "weatherConditions" to r.weatherConditions,
                            "complianceStandard" to r.complianceStandard,
                            "inspectionType" to r.inspectionType,
                            "photoPathsJson" to r.photoPathsJson
                        )
                    }
                )
                file.writeText(gson.toJson(backup))

                withContext(Dispatchers.Main) {
                    ExportUtils.openFile(context, file, "application/json")
                    _isExporting.value = false
                    onComplete(file)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    fun importQualityBackup(
        context: Context,
        file: File,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val json = file.readText()
                val jsonObject = com.google.gson.JsonParser.parseString(json).asJsonObject
                val recordsJson = jsonObject.getAsJsonArray("records") ?: com.google.gson.JsonArray()
                var imported = 0
                recordsJson.forEach { element ->
                    val m = element.asJsonObject
                    val record = QualityRecord(
                        id = m.get("id")?.asLong?.toLong() ?: 0,
                        projectId = m.get("projectId")?.asLong?.toLong() ?: 0,
                        category = m.get("category")?.asString ?: "",
                        elementId = m.get("elementId")?.asString ?: "",
                        designCode = m.get("designCode")?.asString ?: "ECP",
                        passed = m.get("passed")?.asBoolean ?: false,
                        utilizationRatio = m.get("utilizationRatio")?.asDouble ?: 0.0,
                        summary = m.get("summary")?.asString ?: "",
                        dataJson = m.get("dataJson")?.asString ?: "{}",
                        notes = m.get("notes")?.asString ?: "",
                        createdAt = Date(m.get("createdAt")?.asLong ?: System.currentTimeMillis()),
                        inspectorName = m.get("inspectorName")?.asString ?: "",
                        location = m.get("location")?.asString ?: "",
                        weatherConditions = m.get("weatherConditions")?.asString ?: "",
                        complianceStandard = m.get("complianceStandard")?.asString ?: "",
                        inspectionType = m.get("inspectionType")?.asString ?: "routine",
                        photoPathsJson = m.get("photoPathsJson")?.asString ?: "[]"
                    )
                    qualityDao.insertRecord(record)
                    imported++
                }
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(true)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(false)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Record details / photos
    // ---------------------------------------------------------------
    fun updateQualityRecord(record: QualityRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { qualityDao.insertRecord(record) }
        }
    }

    fun attachPhoto(
        context: Context,
        record: QualityRecord,
        source: Uri,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = runCatching {
                val dir = File(context.filesDir, "inspection_photos").apply { mkdirs() }
                val dest = File(dir, "qc_${record.id}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(source)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                dest.absolutePath
            }.getOrNull()

            if (path == null) {
                withContext(Dispatchers.Main) { onComplete(null) }
                return@launch
            }

            val paths = parsePhotoPaths(record.photoPathsJson) + path
            qualityDao.insertRecord(record.copy(photoPathsJson = gson.toJson(paths)))
            withContext(Dispatchers.Main) { onComplete(path) }
        }
    }

    fun removePhoto(record: QualityRecord, path: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(path).delete() }
            val paths = parsePhotoPaths(record.photoPathsJson).filterNot { it == path }
            qualityDao.insertRecord(record.copy(photoPathsJson = gson.toJson(paths)))
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private fun parsePhotoPaths(json: String): List<String> =
        runCatching { gson.fromJson(json, Array<String>::class.java)?.toList() }
            .getOrNull() ?: emptyList()

    fun importQualityBackupUri(
        context: Context,
        uri: Uri,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Cannot read backup file")
                val jsonObject = com.google.gson.JsonParser.parseString(json).asJsonObject
                val recordsJson = jsonObject.getAsJsonArray("records") ?: com.google.gson.JsonArray()
                recordsJson.forEach { element ->
                    val m = element.asJsonObject
                    val record = QualityRecord(
                        id = 0,
                        projectId = m.get("projectId")?.asLong?.toLong() ?: 0,
                        category = m.get("category")?.asString ?: "",
                        elementId = m.get("elementId")?.asString ?: "",
                        designCode = m.get("designCode")?.asString ?: "ECP",
                        passed = m.get("passed")?.asBoolean ?: false,
                        utilizationRatio = m.get("utilizationRatio")?.asDouble ?: 0.0,
                        summary = m.get("summary")?.asString ?: "",
                        dataJson = m.get("dataJson")?.asString ?: "{}",
                        notes = m.get("notes")?.asString ?: "",
                        createdAt = Date(m.get("createdAt")?.asLong ?: System.currentTimeMillis()),
                        inspectorName = m.get("inspectorName")?.asString ?: "",
                        location = m.get("location")?.asString ?: "",
                        weatherConditions = m.get("weatherConditions")?.asString ?: "",
                        complianceStandard = m.get("complianceStandard")?.asString ?: "",
                        inspectionType = m.get("inspectionType")?.asString ?: "routine",
                        photoPathsJson = m.get("photoPathsJson")?.asString ?: "[]"
                    )
                    qualityDao.insertRecord(record)
                }
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(true)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(false)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // PDF export (English only — ADR-009)
    // ---------------------------------------------------------------
    fun exportQualityReport(
        context: Context,
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, "Quality_Report_${System.currentTimeMillis()}.pdf")

                val projectName = preferencesManager.projectName.first()
                val engineerName = preferencesManager.engineerName.first()
                val companyName = preferencesManager.companyName.first()

                val inputMap = LinkedHashMap<String, String>()
                val resultMap = LinkedHashMap<String, String>()
                val safetyChecks = mutableListOf<ProfessionalEnglishPdfReporter.SafetyCheck>()

                fun absorb(outcome: QualityControlEngine.QcOutcome?) {
                    outcome ?: return
                    outcome.details.forEach { (k, v) -> inputMap["${outcome.title.split(" ").first()} · $k"] = v }
                    outcome.checks.forEach { c ->
                        safetyChecks.add(
                            ProfessionalEnglishPdfReporter.SafetyCheck(
                                name = "${outcome.title.split(" ").first()}: ${c.name}",
                                calculated = c.calculated,
                                limit = c.limit,
                                unit = c.unit,
                                passed = c.passed
                            )
                        )
                    }
                }

                absorb(_concreteState.value.outcome)
                absorb(_rebarState.value.outcome)
                absorb(_steelState.value.outcome)
                absorb(_roadsState.value.outcome)
                absorb(_bridgesState.value.outcome)
                absorb(_tunnelsState.value.outcome)
                absorb(_buildingsState.value.outcome)

                // Soil outcome is stored separately as a bearing result
                _soilState.value.soilResult?.let { r ->
                    resultMap["Soil · Allowable bearing"] = "%.1f kPa".format(r.allowableBearingCapacity)
                    resultMap["Soil · Settlement"] = "%.1f mm".format(r.settlement)
                    safetyChecks.add(
                        ProfessionalEnglishPdfReporter.SafetyCheck(
                            name = "SOIL: Allowable bearing capacity",
                            calculated = r.allowableBearingCapacity,
                            limit = 0.0,
                            unit = "kPa",
                            passed = r.isSafe
                        )
                    )
                }

                val stats = _reportsState.value
                resultMap["Session · Total records"] = stats.totalInspections.toString()
                resultMap["Session · Passed"] = stats.passedInspections.toString()
                resultMap["Session · Failed"] = stats.failedInspections.toString()
                resultMap["Session · Success rate"] = "%.1f %%".format(stats.successRate)

                if (inputMap.isEmpty() && safetyChecks.isEmpty() && stats.records.isEmpty()) {
                    withContext(Dispatchers.Main) { _isExporting.value = false }
                    onComplete(null)
                    return@launch
                }

                val isSafe = safetyChecks.all { it.passed } && stats.failedInspections == 0
                val generated = ProfessionalEnglishPdfReporter.generateReport(
                    reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
                    title = "Site Quality Control Report",
                    subtitle = "Concrete · Soil · Rebar · Steel · Roads · Bridges · Tunnels · Buildings",
                    designCode = "ECP 203-2020 / ACI 318-19 / SBC 304-2018",
                    inputs = inputMap,
                    results = resultMap,
                    safetyChecks = safetyChecks,
                    isSafe = isSafe,
                    config = ProfessionalEnglishPdfReporter.ReportConfig(
                        projectName = projectName,
                        engineerName = engineerName,
                        companyName = companyName
                    ),
                    outputPath = file.absolutePath
                )

                withContext(Dispatchers.Main) {
                    generated?.let { ExportUtils.openPdf(context, it) }
                    _isExporting.value = false
                    onComplete(generated)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Per-discipline PDF export (uses current tab outcome + drawing)
    // ---------------------------------------------------------------
    fun exportDisciplineReport(
        context: Context,
        title: String,
        subtitle: String,
        outcome: QualityControlEngine.QcOutcome?,
        drawingBitmap: android.graphics.Bitmap? = null,
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, "${title.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")

                val projectName = preferencesManager.projectName.first()
                val engineerName = preferencesManager.engineerName.first()
                val companyName = preferencesManager.companyName.first()

                val inputMap = LinkedHashMap<String, String>()
                val resultMap = LinkedHashMap<String, String>()
                val safetyChecks = mutableListOf<ProfessionalEnglishPdfReporter.SafetyCheck>()

                outcome?.let { o ->
                    o.details.forEach { (k, v) -> inputMap[k] = v }
                    o.checks.forEach { c ->
                        safetyChecks.add(
                            ProfessionalEnglishPdfReporter.SafetyCheck(
                                name = c.name,
                                calculated = c.calculated,
                                limit = c.limit,
                                unit = c.unit,
                                passed = c.passed
                            )
                        )
                    }
                }

                val isSafe = outcome?.passed == true && safetyChecks.all { it.passed }
                val generated = ProfessionalEnglishPdfReporter.generateReport(
                    reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
                    title = title,
                    subtitle = subtitle,
                    designCode = "ECP 203-2020 / ACI 318-19 / SBC 304-2018",
                    inputs = inputMap,
                    results = resultMap,
                    safetyChecks = safetyChecks,
                    isSafe = isSafe,
                    drawingBitmap = drawingBitmap,
                    config = ProfessionalEnglishPdfReporter.ReportConfig(
                        projectName = projectName,
                        engineerName = engineerName,
                        companyName = companyName
                    ),
                    outputPath = file.absolutePath
                )

                withContext(Dispatchers.Main) {
                    generated?.let { ExportUtils.openPdf(context, it) }
                    _isExporting.value = false
                    onComplete(generated)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _isExporting.value = false
                    onComplete(null)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Capture composable as bitmap for drawing attachment
    // ---------------------------------------------------------------
    // Note: Must be called from a Compose context (screen) with a ComposeView.
    // See screen implementation for usage.
    fun captureComposableAsBitmap(
        composeView: androidx.compose.ui.platform.ComposeView,
        width: Int = 800,
        height: Int = 600
    ): android.graphics.Bitmap {
        composeView.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
        composeView.layout(0, 0, width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        composeView.draw(canvas)
        return bitmap
    }
}
