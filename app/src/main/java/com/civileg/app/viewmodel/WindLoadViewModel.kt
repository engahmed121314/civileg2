package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WindLoadViewModel @Inject constructor(
    private val repository: com.civileg.app.db.DesignRepository
) : ViewModel() {

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private val _result = MutableLiveData<WindLoadResult?>()
    val result: LiveData<WindLoadResult?> = _result

    private val _isCalculating = MutableLiveData(false)
    val isCalculating: LiveData<Boolean> = _isCalculating

    fun saveDesign(projectId: Long, name: String) {
        val res = _result.value ?: return
        viewModelScope.launch {
            repository.saveWindLoadDesign(projectId, name, res)
        }
    }

    private val _k2Table = MutableLiveData<List<Pair<Double, Double>>>(emptyList())
    val k2Table: LiveData<List<Pair<Double, Double>>> = _k2Table

    // ------------------------------------------------------------------
    // Input fields
    // ------------------------------------------------------------------

    val basicWindSpeed = MutableLiveData("30.0")
    val terrainCategory = MutableLiveData(TerrainCategory.SUBURBAN)
    val buildingHeight = MutableLiveData("20.0")
    val buildingWidth = MutableLiveData("15.0")
    val buildingDepth = MutableLiveData("10.0")
    val buildingShape = MutableLiveData(BuildingShape.RECTANGULAR)
    val roofType = MutableLiveData(RoofType.FLAT)
    val roofSlope = MutableLiveData("0.0")
    val importanceFactor = MutableLiveData("1.0")
    val topographyFactor = MutableLiveData("1.0")
    val numberOfFloors = MutableLiveData("5")
    val openingsInWindward = MutableLiveData(false)
    val isFlexibleStructure = MutableLiveData(false)
    val naturalFrequency = MutableLiveData("1.0")
    val dampingRatio = MutableLiveData("0.02")

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun MutableLiveData<String>.doubleValue(default: Double = 0.0): Double {
        return try {
            value?.toDoubleOrNull() ?: default
        } catch (_: NumberFormatException) {
            default
        }
    }

    private fun MutableLiveData<String>.intValue(default: Int = 1): Int {
        return try {
            value?.toIntOrNull() ?: default
        } catch (_: NumberFormatException) {
            default
        }
    }

    // ------------------------------------------------------------------
    // Build input
    // ------------------------------------------------------------------

    private fun buildInput(): WindLoadInput {
        return WindLoadInput(
            basicWindSpeed      = basicWindSpeed.doubleValue(30.0),
            terrainCategory    = terrainCategory.value ?: TerrainCategory.SUBURBAN,
            buildingHeight     = buildingHeight.doubleValue(20.0),
            buildingWidth      = buildingWidth.doubleValue(15.0),
            buildingDepth      = buildingDepth.doubleValue(10.0),
            buildingShape      = buildingShape.value ?: BuildingShape.RECTANGULAR,
            roofType           = roofType.value ?: RoofType.FLAT,
            roofSlope          = roofSlope.doubleValue(0.0),
            importanceFactor   = importanceFactor.doubleValue(1.0),
            topographyFactor   = topographyFactor.doubleValue(1.0),
            numberOfFloors     = numberOfFloors.intValue(5).coerceAtLeast(1),
            openingsInWindward = openingsInWindward.value ?: false,
            isFlexibleStructure = isFlexibleStructure.value ?: false,
            naturalFrequency   = naturalFrequency.doubleValue(1.0),
            dampingRatio       = dampingRatio.doubleValue(0.02)
        )
    }

    // ------------------------------------------------------------------
    // Core calculation
    // ------------------------------------------------------------------

    fun calculate() {
        _isCalculating.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val calculator = WindLoadCalculator()
                val input = buildInput()
                val res = calculator.calculate(input)
                _result.postValue(res)
            } catch (e: Exception) {
                _result.postValue(null)
            } finally {
                _isCalculating.postValue(false)
            }
        }
    }

    // ------------------------------------------------------------------
    // Generate k2 table for current terrain and height
    // ------------------------------------------------------------------

    fun updateK2Table() {
        val terrain = terrainCategory.value ?: TerrainCategory.SUBURBAN
        val height = buildingHeight.doubleValue(20.0)
        val calculator = WindLoadCalculator()
        _k2Table.postValue(calculator.getK2Table(terrain, height))
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    fun resetToDefaults() {
        basicWindSpeed.postValue("30.0")
        terrainCategory.postValue(TerrainCategory.SUBURBAN)
        buildingHeight.postValue("20.0")
        buildingWidth.postValue("15.0")
        buildingDepth.postValue("10.0")
        buildingShape.postValue(BuildingShape.RECTANGULAR)
        roofType.postValue(RoofType.FLAT)
        roofSlope.postValue("0.0")
        importanceFactor.postValue("1.0")
        topographyFactor.postValue("1.0")
        numberOfFloors.postValue("5")
        openingsInWindward.postValue(false)
        isFlexibleStructure.postValue(false)
        naturalFrequency.postValue("1.0")
        dampingRatio.postValue("0.02")
        _result.postValue(null)
        _k2Table.postValue(emptyList())
    }

    // ------------------------------------------------------------------
    // Selection handlers
    // ------------------------------------------------------------------

    fun onTerrainSelected(terrain: TerrainCategory) {
        terrainCategory.postValue(terrain)
        updateK2Table()
    }

    fun onShapeSelected(shape: BuildingShape) {
        buildingShape.postValue(shape)
    }

    fun onRoofTypeSelected(type: RoofType) {
        roofType.postValue(type)
        // Auto-set slope for flat/gabled
        when (type) {
            RoofType.FLAT   -> roofSlope.postValue("0.0")
            RoofType.GABLED -> roofSlope.postValue("15.0")
            RoofType.HIP    -> roofSlope.postValue("20.0")
        }
    }

    // ------------------------------------------------------------------
    // Presets
    // ------------------------------------------------------------------

    /** Apply a quick preset for common building types. */
    fun applyPreset(preset: WindPreset) {
        when (preset) {
            WindPreset.LOW_RISE_RESIDENTIAL -> {
                basicWindSpeed.postValue("33.0")
                terrainCategory.postValue(TerrainCategory.SUBURBAN)
                buildingHeight.postValue("10.0")
                buildingWidth.postValue("12.0")
                buildingDepth.postValue("10.0")
                buildingShape.postValue(BuildingShape.RECTANGULAR)
                roofType.postValue(RoofType.GABLED)
                roofSlope.postValue("15.0")
                importanceFactor.postValue("1.0")
                topographyFactor.postValue("1.0")
                numberOfFloors.postValue("3")
                openingsInWindward.postValue(false)
                isFlexibleStructure.postValue(false)
            }
            WindPreset.HIGH_RISE_OFFICE -> {
                basicWindSpeed.postValue("47.0")
                terrainCategory.postValue(TerrainCategory.URBAN)
                buildingHeight.postValue("60.0")
                buildingWidth.postValue("25.0")
                buildingDepth.postValue("20.0")
                buildingShape.postValue(BuildingShape.RECTANGULAR)
                roofType.postValue(RoofType.FLAT)
                roofSlope.postValue("0.0")
                importanceFactor.postValue("1.0")
                topographyFactor.postValue("1.0")
                numberOfFloors.postValue("15")
                openingsInWindward.postValue(false)
                isFlexibleStructure.postValue(true)
                naturalFrequency.postValue("0.5")
                dampingRatio.postValue("0.02")
            }
            WindPreset.WAREHOUSE -> {
                basicWindSpeed.postValue("39.0")
                terrainCategory.postValue(TerrainCategory.OPEN_TERRAIN)
                buildingHeight.postValue("8.0")
                buildingWidth.postValue("30.0")
                buildingDepth.postValue("50.0")
                buildingShape.postValue(BuildingShape.RECTANGULAR)
                roofType.postValue(RoofType.GABLED)
                roofSlope.postValue("10.0")
                importanceFactor.postValue("0.9")
                topographyFactor.postValue("1.0")
                numberOfFloors.postValue("1")
                openingsInWindward.postValue(true)
                isFlexibleStructure.postValue(false)
            }
            WindPreset.COASTAL_TOWER -> {
                basicWindSpeed.postValue("50.0")
                terrainCategory.postValue(TerrainCategory.SEA_COAST)
                buildingHeight.postValue("100.0")
                buildingWidth.postValue("20.0")
                buildingDepth.postValue("15.0")
                buildingShape.postValue(BuildingShape.CIRCULAR)
                roofType.postValue(RoofType.FLAT)
                roofSlope.postValue("0.0")
                importanceFactor.postValue("1.15")
                topographyFactor.postValue("1.1")
                numberOfFloors.postValue("25")
                openingsInWindward.postValue(false)
                isFlexibleStructure.postValue(true)
                naturalFrequency.postValue("0.3")
                dampingRatio.postValue("0.015")
            }
        }
        updateK2Table()
        _result.postValue(null)
    }
}

/** Quick-load presets for typical building scenarios. */
enum class WindPreset(val label: String) {
    LOW_RISE_RESIDENTIAL("Low-Rise Residential"),
    HIGH_RISE_OFFICE("High-Rise Office"),
    WAREHOUSE("Warehouse / Industrial"),
    COASTAL_TOWER("Coastal Tower")
}
