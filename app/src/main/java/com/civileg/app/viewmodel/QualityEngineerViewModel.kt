package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.civileg.app.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

data class ConcreteQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val elementId: String = "",
    val volume: String = "",
    val slump: String = "",
    val fcu: String = "25",
    val sampleId: String = "",
    val strength7Days: String = "",
    val strength28Days: String = ""
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
    val soilResult: SoilBearingResult? = null
)

data class RebarQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val barDiameter: String = "16",
    val numBars: String = "3",
    val fy: String = "400",
    val cover: String = "40",
    val beamWidth: String = "250",
    val effectiveDepth: String = "560",
    val rebarChecks: List<GenericSafetyCheck>? = null
)

data class SteelQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val appliedMoment: String = "",
    val appliedShear: String = "",
    val steelFy: String = "400",
    val span: String = "",
    val steelChecks: List<GenericSafetyCheck>? = null
)

data class RoadsQCState(
    val surfaceLayerThickness: String = "50",
    val baseLayerThickness: String = "150",
    val subbaseLayerThickness: String = "200",
    val compactionPercent: String = "97",
    val asphaltDensity: String = "23",
    val airVoids: String = "4"
)

data class BridgesQCState(
    val concreteStrength: String = "30",
    val span: String = "20",
    val naturalFrequency: String = "2.5"
)

data class TunnelsQCState(
    val tunnelConvergence: String = "20",
    val supportThickness: String = "150"
)

data class BuildingsQCState(
    val designCode: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN,
    val numFloors: String = "5",
    val floorHeight: String = "3",
    val totalWeight: String = "50000",
    val importanceFactor: String = "1",
    val buildingChecks: List<GenericSafetyCheck>? = null
)

data class ReportsQCState(
    val totalInspections: Int = 0,
    val passedInspections: Int = 0,
    val failedInspections: Int = 0,
    val successRate: Double = 0.0
)

@HiltViewModel
class QualityEngineerViewModel @Inject constructor() : ViewModel() {

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

    fun updateDesignCode(code: CalculatorEngine.DesignCode) {
        _concreteState.value = _concreteState.value.copy(designCode = code)
        _soilState.value = _soilState.value.copy(designCode = code)
        _rebarState.value = _rebarState.value.copy(designCode = code)
        _steelState.value = _steelState.value.copy(designCode = code)
        _buildingsState.value = _buildingsState.value.copy(designCode = code)
    }

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
            "cover" -> _rebarState.value.copy(cover = value)
            "beamWidth" -> _rebarState.value.copy(beamWidth = value)
            "effectiveDepth" -> _rebarState.value.copy(effectiveDepth = value)
            else -> _rebarState.value
        }
    }

    fun updateSteelField(field: String, value: String) {
        _steelState.value = when (field) {
            "appliedMoment" -> _steelState.value.copy(appliedMoment = value)
            "appliedShear" -> _steelState.value.copy(appliedShear = value)
            "steelFy" -> _steelState.value.copy(steelFy = value)
            "span" -> _steelState.value.copy(span = value)
            else -> _steelState.value
        }
    }

    fun updateRoadsField(field: String, value: String) {
        _roadsState.value = when (field) {
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
            "tunnelConvergence" -> _tunnelsState.value.copy(tunnelConvergence = value)
            "supportThickness" -> _tunnelsState.value.copy(supportThickness = value)
            else -> _tunnelsState.value
        }
    }

    fun updateBuildingsField(field: String, value: String) {
        _buildingsState.value = when (field) {
            "numFloors" -> _buildingsState.value.copy(numFloors = value)
            "floorHeight" -> _buildingsState.value.copy(floorHeight = value)
            "totalWeight" -> _buildingsState.value.copy(totalWeight = value)
            "importanceFactor" -> _buildingsState.value.copy(importanceFactor = value)
            else -> _buildingsState.value
        }
    }

    fun saveConcretePour(context: android.content.Context) {
        viewModelScope.launch {
            // Save logic here
        }
    }

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
            eccentricityX = 0.0,
            eccentricityY = 0.0,
            loadInclinationX = 0.0,
            loadInclinationY = 0.0,
            safetyFactor = s.safetyFactor.toDoubleOrNull() ?: 3.0
        )

        val calculator = SoilBearingCalculator()
        val result = when (method) {
            BearingMethod.TERZAGHI -> calculator.calculateTerzaghi(input)
            BearingMethod.MEYERHOF -> calculator.calculateMeyerhof(input)
            BearingMethod.HANSEN -> calculator.calculateHansen(input)
            BearingMethod.VESIC -> calculator.calculateVesic(input)
        }

        _soilState.value = s.copy(soilResult = result)
    }

    fun calculateRebarChecks() {
        val s = _rebarState.value
        val dia = s.barDiameter.toDoubleOrNull() ?: 16.0
        val n = s.numBars.toIntOrNull() ?: 3
        val fy = s.fy.toDoubleOrNull() ?: 400.0
        val cover = s.cover.toDoubleOrNull() ?: 40.0
        val b = s.beamWidth.toDoubleOrNull() ?: 250.0
        val d = s.effectiveDepth.toDoubleOrNull() ?: 560.0

        val barArea = PI * dia * dia / 4.0
        val totalArea = n * barArea
        val rho = totalArea / (b * d)
        val rhoMin = max(0.25 * sqrt(fy) / fy, 1.4 / fy)
        val rhoMax = 0.04

        val checks = listOf(
            GenericSafetyCheck("نسبة التسليح ρ", rho * 100, 1.0, "%", rho >= rhoMin),
            GenericSafetyCheck("الحد الأدنى", rhoMin * 100, rho * 100, "%", rho >= rhoMin),
            GenericSafetyCheck("الحد الأقصى", rho * 100, rhoMax * 100, "%", rho <= rhoMax)
        )

        _rebarState.value = s.copy(rebarChecks = checks)
    }

    fun calculateSteelChecks(section: String) {
        val s = _steelState.value
        val mu = s.appliedMoment.toDoubleOrNull() ?: 0.0
        val vu = s.appliedShear.toDoubleOrNull() ?: 0.0
        val fy = s.steelFy.toDoubleOrNull() ?: 400.0
        val span = s.span.toDoubleOrNull() ?: 5.0

        val wn = fy * 1000.0 / 1000.0 // Simplified
        val mn = fy * 1000.0 * span * span / 8.0 / 1e6
        val checks = listOf(
            GenericSafetyCheck("قدرة الانحناء", mu, mn, "kN.m", mu <= mn),
            GenericSafetyCheck("قدرة القص", vu, wn * 0.6, "kN", vu <= wn * 0.6)
        )

        _steelState.value = s.copy(steelChecks = checks)
    }

    fun calculateBuildingChecks(buildingType: String) {
        val s = _buildingsState.value
        val numFloors = s.numFloors.toIntOrNull() ?: 5
        val floorHeight = s.floorHeight.toDoubleOrNull() ?: 3.0
        val totalWeight = s.totalWeight.toDoubleOrNull() ?: 50000.0
        val importance = s.importanceFactor.toDoubleOrNull() ?: 1.0

        val totalHeight = numFloors * floorHeight
        val period = 0.075 * totalHeight.pow(0.75)
        val baseShear = totalWeight * 0.1 * importance

        val checks = listOf(
            GenericSafetyCheck("الارتفاع الكلي", totalHeight, 100.0, "m", totalHeight <= 100),
            GenericSafetyCheck("الفترة الأساسية", period, 2.0, "s", period <= 2.0),
            GenericSafetyCheck("قوة القص القاعدية", baseShear, totalWeight, "kN", true)
        )

        _buildingsState.value = s.copy(buildingChecks = checks)
    }

    fun exportQualityReport() {
        viewModelScope.launch {
            // Export logic here
        }
    }
}