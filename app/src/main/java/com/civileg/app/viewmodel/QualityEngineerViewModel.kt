package com.civileg.app.viewmodel

import com.civileg.app.domain.entities.DesignCode

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.data.local.PreferencesManager
import com.civileg.app.db.CategorySummary
import com.civileg.app.db.DailyTrend
import com.civileg.app.db.InspectionTestPlan
import com.civileg.app.db.NonConformity
import com.civileg.app.db.QualityDao
import com.civileg.app.db.QualityRecord
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CsvEncoder
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.ItpTemplates
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
    val designCode: DesignCode = DesignCode.ECP,
    val elementId: String = "",
    val volume: String = "",
    val slump: String = "",
    val fcu: String = "25",
    val sampleId: String = "",
    val strength7Days: String = "",
    val strength28Days: String = "",
    val airContent: String = "4.0",
    val concreteTemp: String = "25",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class SoilQCState(
    val designCode: DesignCode = DesignCode.ECP,
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
    val designCode: DesignCode = DesignCode.ECP,
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
    val designCode: DesignCode = DesignCode.ECP,
    val sectionKey: String = "IPE 200",
    val appliedMoment: String = "",
    val appliedShear: String = "",
    val steelFy: String = "275",
    val span: String = "",
    val steelChecks: List<GenericSafetyCheck>? = null,
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class RoadsQCState(
    val designCode: DesignCode = DesignCode.ECP,
    val surfaceLayerThickness: String = "50",
    val baseLayerThickness: String = "150",
    val subbaseLayerThickness: String = "200",
    val compactionPercent: String = "97",
    val asphaltDensity: String = "23",
    val airVoids: String = "4",
    val gmb: String = "2.35",
    val gmm: String = "2.45",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class BridgesQCState(
    val designCode: DesignCode = DesignCode.ECP,
    val concreteStrength: String = "",
    val requiredConcreteStrength: String = "",
    val span: String = "20",
    val naturalFrequency: String = "",
    val measuredDeflection: String = "",
    val allowableDeflection: String = "",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class TunnelsQCState(
    val designCode: DesignCode = DesignCode.ECP,
    val rockClass: String = "III",
    val tunnelConvergence: String = "20",
    val tunnelDiameter: String = "6",
    val supportThickness: String = "150",
    val outcome: QualityControlEngine.QcOutcome? = null
)

data class BuildingsQCState(
    val designCode: DesignCode = DesignCode.ECP,
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
    val dailyTrend: List<DailyTrend> = emptyList(),
    val itpItems: List<InspectionTestPlan> = emptyList(),
    val ncrItems: List<NonConformity> = emptyList(),
    val openNcrCount: Int = 0
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
        viewModelScope.launch {
            qualityDao.getAllItp().collect { items ->
                _reportsState.value = _reportsState.value.copy(itpItems = items)
            }
        }
        viewModelScope.launch {
            qualityDao.getAllNcr().collect { items ->
                _reportsState.value = _reportsState.value.copy(
                    ncrItems = items,
                    openNcrCount = items.count { it.status != "closed" }
                )
            }
        }
    }

    // ---------------------------------------------------------------
    // ITP (Inspection & Test Plan) actions
    // ---------------------------------------------------------------
    fun loadDefaultItp() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { qualityDao.insertItpList(ItpTemplates.defaultTemplate()) }
        }
    }

    fun updateItpStatus(id: Long, status: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { qualityDao.updateItpStatus(id, status) }
        }
    }

    fun deleteItp(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { qualityDao.deleteItp(id) } }
    }

    fun clearItp() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { qualityDao.deleteAllItp() } }
    }

    // ---------------------------------------------------------------
    // NCR (Non-Conformity Report) actions
    // ---------------------------------------------------------------
    fun createNcr(
        discipline: String,
        elementId: String,
        description: String,
        referenceCode: String,
        severity: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                qualityDao.insertNcr(
                    NonConformity(
                        ncrNumber = "NCR-${System.currentTimeMillis().toString().takeLast(6)}",
                        discipline = discipline,
                        elementId = elementId,
                        description = description,
                        referenceCode = referenceCode,
                        severity = severity
                    )
                )
            }
        }
    }

    fun closeNcr(id: Long, correctiveAction: String, closedBy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { qualityDao.updateNcrStatus(id, "closed", correctiveAction, closedBy) }
        }
    }

    fun deleteNcr(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { qualityDao.deleteNcr(id) } }
    }

    // ---------------------------------------------------------------
    // Design code
    // ---------------------------------------------------------------
    fun updateDesignCode(code: DesignCode) {
        _concreteState.value = _concreteState.value.copy(designCode = code, outcome = null)
        _soilState.value = _soilState.value.copy(designCode = code, soilResult = null, outcome = null)
        _rebarState.value = _rebarState.value.copy(
            designCode = code,
            rebarChecks = null,
            outcome = null
        )
        _steelState.value = _steelState.value.copy(
            designCode = code,
            steelChecks = null,
            outcome = null
        )
        _roadsState.value = _roadsState.value.copy(designCode = code, outcome = null)
        _bridgesState.value = _bridgesState.value.copy(designCode = code, outcome = null)
        _tunnelsState.value = _tunnelsState.value.copy(designCode = code, outcome = null)
        _buildingsState.value = _buildingsState.value.copy(
            designCode = code,
            buildingChecks = null,
            outcome = null
        )
    }

    // ---------------------------------------------------------------
    // Field updaters
    // ---------------------------------------------------------------
    fun updateConcreteField(field: String, value: String) {
        val current = _concreteState.value
        val updated = when (field) {
            "elementId" -> current.copy(elementId = value)
            "volume" -> current.copy(volume = value)
            "slump" -> current.copy(slump = value)
            "fcu" -> current.copy(fcu = value)
            "sampleId" -> current.copy(sampleId = value)
            "strength7Days" -> current.copy(strength7Days = value)
            "strength28Days" -> current.copy(strength28Days = value)
            "airContent" -> current.copy(airContent = value)
            "concreteTemp" -> current.copy(concreteTemp = value)
            else -> current
        }
        _concreteState.value = if (updated == current) current else updated.copy(outcome = null)
    }

    fun updateSoilField(field: String, value: String) {
        val current = _soilState.value
        val updated = when (field) {
            "cohesion" -> current.copy(cohesion = value)
            "frictionAngle" -> current.copy(frictionAngle = value)
            "unitWeight" -> current.copy(unitWeight = value)
            "waterTableDepth" -> current.copy(waterTableDepth = value)
            "foundationWidth" -> current.copy(foundationWidth = value)
            "foundationLength" -> current.copy(foundationLength = value)
            "foundationDepth" -> current.copy(foundationDepth = value)
            "safetyFactor" -> current.copy(safetyFactor = value)
            else -> current
        }
        _soilState.value = if (updated == current) current else updated.copy(soilResult = null, outcome = null)
    }

    fun updateRebarField(field: String, value: String) {
        val current = _rebarState.value
        val updated = when (field) {
            "barDiameter" -> current.copy(barDiameter = value)
            "numBars" -> current.copy(numBars = value)
            "fy" -> current.copy(fy = value)
            "fcu" -> current.copy(fcu = value)
            "cover" -> current.copy(cover = value)
            "beamWidth" -> current.copy(beamWidth = value)
            "effectiveDepth" -> current.copy(effectiveDepth = value)
            else -> current
        }
        _rebarState.value = if (updated == current) current else updated.copy(rebarChecks = null, outcome = null)
    }

    fun updateSteelField(field: String, value: String) {
        val current = _steelState.value
        val updated = when (field) {
            "sectionKey" -> current.copy(sectionKey = value)
            "appliedMoment" -> current.copy(appliedMoment = value)
            "appliedShear" -> current.copy(appliedShear = value)
            "steelFy" -> current.copy(steelFy = value)
            "span" -> current.copy(span = value)
            else -> current
        }
        _steelState.value = if (updated == current) current else updated.copy(steelChecks = null, outcome = null)
    }

    fun updateRoadsField(field: String, value: String) {
        val current = _roadsState.value
        val updated = when (field) {
            "surfaceLayerThickness" -> current.copy(surfaceLayerThickness = value)
            "baseLayerThickness" -> current.copy(baseLayerThickness = value)
            "subbaseLayerThickness" -> current.copy(subbaseLayerThickness = value)
            "compactionPercent" -> current.copy(compactionPercent = value)
            "asphaltDensity" -> current.copy(asphaltDensity = value)
            "airVoids" -> current.copy(airVoids = value)
            "gmb" -> current.copy(gmb = value)
            "gmm" -> current.copy(gmm = value)
            else -> current
        }
        _roadsState.value = if (updated == current) current else updated.copy(outcome = null)
    }

    fun updateBridgesField(field: String, value: String) {
        val current = _bridgesState.value
        val updated = when (field) {
            "concreteStrength" -> current.copy(concreteStrength = value)
            "requiredConcreteStrength" -> current.copy(requiredConcreteStrength = value)
            "span" -> current.copy(span = value)
            "naturalFrequency" -> current.copy(naturalFrequency = value)
            "measuredDeflection" -> current.copy(measuredDeflection = value)
            "allowableDeflection" -> current.copy(allowableDeflection = value)
            else -> current
        }
        _bridgesState.value = if (updated == current) current else updated.copy(outcome = null)
    }

    fun updateTunnelsField(field: String, value: String) {
        val current = _tunnelsState.value
        val updated = when (field) {
            "rockClass" -> current.copy(rockClass = value)
            "tunnelConvergence" -> current.copy(tunnelConvergence = value)
            "tunnelDiameter" -> current.copy(tunnelDiameter = value)
            "supportThickness" -> current.copy(supportThickness = value)
            else -> current
        }
        _tunnelsState.value = if (updated == current) current else updated.copy(outcome = null)
    }

    fun updateBuildingsField(field: String, value: String) {
        val current = _buildingsState.value
        val updated = when (field) {
            "buildingType" -> current.copy(buildingType = value)
            "numFloors" -> current.copy(numFloors = value)
            "floorHeight" -> current.copy(floorHeight = value)
            "totalWeight" -> current.copy(totalWeight = value)
            "importanceFactor" -> current.copy(importanceFactor = value)
            "sds" -> current.copy(sds = value)
            "responseR" -> current.copy(responseR = value)
            "storyDrift" -> current.copy(storyDrift = value)
            else -> current
        }
        _buildingsState.value = if (updated == current) current else updated.copy(buildingChecks = null, outcome = null)
    }

    // ---------------------------------------------------------------
    // CONCRETE
    // ---------------------------------------------------------------
    fun saveConcretePour(context: Context) {
        val s = _concreteState.value
        val volume = InputGuard.positive("volume", s.volume.toDoubleOrNull() ?: Double.NaN)
        val slump = InputGuard.inRange("slump", s.slump.toDoubleOrNull() ?: Double.NaN, 0.0, 500.0)
        val fcu = InputGuard.positive("fcu", s.fcu.toDoubleOrNull() ?: Double.NaN)
        val s7 = InputGuard.nonNegative("strength7Days", s.strength7Days.toDoubleOrNull() ?: Double.NaN)
        val s28 = InputGuard.nonNegative("strength28Days", s.strength28Days.toDoubleOrNull() ?: Double.NaN)
        val air = InputGuard.inRange("airContent", s.airContent.toDoubleOrNull() ?: Double.NaN, 0.0, 10.0)
        val temp = InputGuard.inRange("concreteTemp", s.concreteTemp.toDoubleOrNull() ?: Double.NaN, 0.0, 50.0)
        val elementId = InputGuard.notBlank("elementId", s.elementId.ifBlank { s.sampleId.ifBlank { "CONCRETE" } })
        
        val outcome = QualityControlEngine.concreteQc(
            code = s.designCode,
            elementId = elementId,
            volumeM3 = volume,
            slumpMm = slump,
            fcuDesignMpa = fcu,
            strength7DaysMpa = s7,
            strength28DaysMpa = s28,
            airContent = air,
            concreteTemp = temp
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
            foundationWidth = s.foundationWidth.toDoubleOrNull() ?: Double.NaN,
            foundationLength = s.foundationLength.toDoubleOrNull() ?: Double.NaN,
            foundationDepth = s.foundationDepth.toDoubleOrNull() ?: Double.NaN,
            cohesion = s.cohesion.toDoubleOrNull() ?: Double.NaN,
            frictionAngle = s.frictionAngle.toDoubleOrNull() ?: Double.NaN,
            unitWeight = s.unitWeight.toDoubleOrNull() ?: Double.NaN,
            waterTableDepth = s.waterTableDepth.toDoubleOrNull() ?: Double.NaN,
            safetyFactor = s.safetyFactor.toDoubleOrNull() ?: Double.NaN
        )
        InputGuard.positive("foundationWidth", input.foundationWidth)
        InputGuard.positive("foundationLength", input.foundationLength)
        InputGuard.positive("foundationDepth", input.foundationDepth)
        InputGuard.nonNegative("cohesion", input.cohesion)
        InputGuard.inRange("frictionAngle", input.frictionAngle, 0.0, 89.0)
        InputGuard.positive("unitWeight", input.unitWeight)
        InputGuard.nonNegative("waterTableDepth", input.waterTableDepth)
        InputGuard.positive("safetyFactor", input.safetyFactor)
        val result = when (method) {
            BearingMethod.TERZAGHI -> bearing.calculateTerzaghi(input)
            BearingMethod.MEYERHOF -> bearing.calculateMeyerhof(input)
            BearingMethod.HANSEN -> bearing.calculateHansen(input)
            BearingMethod.VESIC -> bearing.calculateVesic(input)
        }

        // Persist as a quality record (reuse the concrete-style summary path)
        val checks = listOf(
            GenericSafetyCheck("Allowable bearing capacity > 0", result.allowableBearingCapacity, 0.0, "kPa",
                result.allowableBearingCapacity > 0.0, "Terzaghi / Meyerhof"),
            GenericSafetyCheck("Estimated settlement ≤ 25 mm", result.settlement, 25.0, "mm",
                result.settlement <= 25.0, "Serviceability limit")
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
            passed = checks.all { it.passed },
            utilizationRatio = if (result.allowableBearingCapacity > 0) result.netBearingCapacity / result.allowableBearingCapacity else 0.0,
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
        val fy = InputGuard.positive("steelFy", s.steelFy.toDoubleOrNull() ?: Double.NaN)
        val span = InputGuard.positive("span", s.span.toDoubleOrNull() ?: Double.NaN)
        val mu = InputGuard.nonNegative("appliedMoment", s.appliedMoment.toDoubleOrNull() ?: Double.NaN)
        val vu = InputGuard.nonNegative("appliedShear", s.appliedShear.toDoubleOrNull() ?: Double.NaN)
        
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
        persist(QualityControlEngine.Category.ROADS, "PAVEMENT", s.designCode, outcome)
    }

    // ---------------------------------------------------------------
    // BRIDGES
    // ---------------------------------------------------------------
    fun calculateBridgeChecks() {
        val s = _bridgesState.value
        val concStrength = InputGuard.positive(
            "concreteStrength",
            s.concreteStrength.toDoubleOrNull() ?: Double.NaN
        )
        val requiredStrength = InputGuard.positive(
            "requiredConcreteStrength",
            s.requiredConcreteStrength.toDoubleOrNull() ?: Double.NaN
        )
        val span = InputGuard.positive("span", s.span.toDoubleOrNull() ?: Double.NaN)
        val freq = InputGuard.positive("naturalFrequency", s.naturalFrequency.toDoubleOrNull() ?: Double.NaN)
        val measuredDeflection = InputGuard.nonNegative(
            "measuredDeflection",
            s.measuredDeflection.toDoubleOrNull() ?: Double.NaN
        )
        val allowableDeflection = InputGuard.positive(
            "allowableDeflection",
            s.allowableDeflection.toDoubleOrNull() ?: Double.NaN
        )

        val outcome = QualityControlEngine.bridgeQc(
            code = s.designCode,
            concreteStrengthMpa = concStrength,
            requiredConcreteStrengthMpa = requiredStrength,
            spanM = span,
            naturalFrequencyHz = freq,
            measuredDeflectionMm = measuredDeflection,
            allowableDeflectionMm = allowableDeflection
        )
        _bridgesState.value = s.copy(outcome = outcome)
        persist(QualityControlEngine.Category.BRIDGES, "BRIDGE", s.designCode, outcome)
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
        persist(QualityControlEngine.Category.TUNNELS, "TUNNEL-${s.rockClass}", s.designCode, outcome)
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
        code: DesignCode,
        outcome: QualityControlEngine.QcOutcome,
        inspectorName: String = "",
        location: String = "",
        weatherConditions: String = "",
        complianceStandard: String = "",
        inspectionType: String = "routine",
        notes: String = ""
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
                ),
                inspectorName = inspectorName,
                location = location,
                weatherConditions = weatherConditions,
                complianceStandard = complianceStandard,
                inspectionType = inspectionType,
                notes = notes
            )
            runCatching { qualityDao.insertRecord(record) }

            if (!outcome.passed) {
                runCatching {
                    val existing = qualityDao.countOpenNcrFor(category, elementId)
                    if (existing == 0) {
                        val failedChecks = outcome.checks.filter { !it.passed }
                        val severity = when {
                            outcome.utilizationRatio >= 1.5 -> "critical"
                            failedChecks.size > 1 -> "major"
                            else -> "minor"
                        }
                        qualityDao.insertNcr(
                            NonConformity(
                                ncrNumber = "NCR-${System.currentTimeMillis().toString().takeLast(6)}",
                                discipline = category,
                                elementId = elementId,
                                description = "Failed checks: " +
                                    failedChecks.joinToString("; ") { it.name },
                                referenceCode = failedChecks.firstOrNull()?.reference ?: complianceStandard,
                                severity = severity
                            )
                        )
                    }
                }
            }
        }
    }

    fun persistDisciplineOutcome(
        category: String,
        elementId: String,
        code: DesignCode,
        outcome: QualityControlEngine.QcOutcome
    ) {
        persist(category, elementId, code, outcome)
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
                sb.appendLine(
                    CsvEncoder.row(
                        listOf(
                            "ID", "Category", "Element ID", "Design Code", "Passed",
                            "Utilization Ratio", "Summary", "Inspector", "Location", "Weather",
                            "Compliance Standard", "Inspection Type", "Created At", "Notes"
                        )
                    )
                )
                records.forEach { r ->
                    sb.appendLine(
                        CsvEncoder.row(
                            listOf(
                                r.id, r.category, r.elementId, r.designCode, r.passed,
                                r.utilizationRatio, r.summary, r.inspectorName, r.location,
                                r.weatherConditions, r.complianceStandard, r.inspectionType,
                                r.createdAt.time, r.notes
                            )
                        )
                    )
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

    fun exportItpCsv(
        context: Context,
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, "ITP_Report_${System.currentTimeMillis()}.csv")
                val items = qualityDao.getAllItp().first()
                val sb = StringBuilder()
                sb.appendLine(
                    CsvEncoder.row(
                        listOf(
                            "Discipline", "Activity", "Inspection Item", "Reference",
                            "Acceptance Criteria", "Frequency", "Responsible", "Status",
                            "Result", "Checked By", "Witness", "Date", "Remarks"
                        )
                    )
                )
                items.forEach { t ->
                    sb.appendLine(
                        CsvEncoder.row(
                            listOf(
                                t.discipline, t.activity, t.inspectionItem, t.referenceCode,
                                t.acceptanceCriteria, t.frequency, t.responsibleParty, t.status,
                                t.resultValue, t.checkedBy, t.witness, t.inspectionDate.time, t.remarks
                            )
                        )
                    )
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

    fun exportNcrCsv(
        context: Context,
        onComplete: (File?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExporting.value = true
            try {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = File(directory, "NCR_Report_${System.currentTimeMillis()}.csv")
                val items = qualityDao.getAllNcr().first()
                val sb = StringBuilder()
                sb.appendLine(
                    CsvEncoder.row(
                        listOf(
                            "NCR Number", "Discipline", "Element", "Severity", "Status",
                            "Reference", "Description", "Root Cause", "Corrective Action",
                            "Responsible", "Target Date", "Closed By", "Created At"
                        )
                    )
                )
                items.forEach { n ->
                    sb.appendLine(
                        CsvEncoder.row(
                            listOf(
                                n.ncrNumber, n.discipline, n.elementId, n.severity, n.status,
                                n.referenceCode, n.description, n.rootCause, n.correctiveAction,
                                n.responsibleParty, n.targetDate.time, n.closedBy, n.createdAt.time
                            )
                        )
                    )
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
                                passed = c.passed,
                                reference = c.reference
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
                resultMap["ITP · Total items"] = stats.itpItems.size.toString()
                resultMap["ITP · Passed"] = stats.itpItems.count { it.status == "passed" }.toString()
                resultMap["ITP · Failed"] = stats.itpItems.count { it.status == "failed" }.toString()
                resultMap["NCR · Open"] = stats.openNcrCount.toString()
                resultMap["NCR · Total"] = stats.ncrItems.size.toString()

                if (inputMap.isEmpty() && safetyChecks.isEmpty() && stats.records.isEmpty()) {
                    withContext(Dispatchers.Main) { _isExporting.value = false }
                    onComplete(null)
                    return@launch
                }

                val isSafe = safetyChecks.all { it.passed } && stats.failedInspections == 0 &&
                    stats.openNcrCount == 0
                val photos = loadRecordPhotos(stats.records)
                val generated = ProfessionalEnglishPdfReporter.generateReport(
                    reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
                    title = "Site Quality Control Report",
                    subtitle = "Concrete · Soil · Rebar · Steel · Roads · Bridges · Tunnels · Buildings · ITP · NCR",
                    designCode = "ECP 203-2020 / ACI 318-19 / SBC 304-2018",
                    inputs = inputMap,
                    results = resultMap,
                    safetyChecks = safetyChecks,
                    isSafe = isSafe,
                    photos = photos,
                    config = ProfessionalEnglishPdfReporter.ReportConfig(
                        projectName = projectName,
                        engineerName = engineerName,
                        companyName = companyName,
                        showSignature = true
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
        designCode: DesignCode = DesignCode.ECP,
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
                                passed = c.passed,
                                reference = c.reference
                            )
                        )
                    }
                }

                val isSafe = outcome?.passed == true && safetyChecks.all { it.passed }
                val generated = ProfessionalEnglishPdfReporter.generateReport(
                    reportType = ProfessionalEnglishPdfReporter.ReportType.GENERIC,
                    title = title,
                    subtitle = subtitle,
                    designCode = designCode.version,
                    inputs = inputMap,
                    results = resultMap,
                    safetyChecks = safetyChecks,
                    isSafe = isSafe,
                    drawingBitmap = drawingBitmap,
                    config = ProfessionalEnglishPdfReporter.ReportConfig(
                        projectName = projectName,
                        engineerName = engineerName,
                        companyName = companyName,
                        showSignature = true
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
    // Load inspection photos from records (decode file paths to bitmaps)
    // ---------------------------------------------------------------
    private fun loadRecordPhotos(records: List<QualityRecord>): List<android.graphics.Bitmap> {
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        records.forEach { r ->
            if (r.photoPathsJson.isBlank()) return@forEach
            val paths = runCatching {
                gson.fromJson(r.photoPathsJson, Array<String>::class.java)?.toList()
            }.getOrNull() ?: return@forEach
            paths.forEach { path ->
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(path)
                }.getOrNull()?.let { bitmaps.add(it) }
                if (bitmaps.size >= 12) return bitmaps
            }
        }
        return bitmaps
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
