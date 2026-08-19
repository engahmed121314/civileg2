package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.utils.BearingMethod
import com.civileg.app.utils.SoilBearingCalculator
import com.civileg.app.utils.SoilBearingInput
import com.civileg.app.utils.SoilBearingResult
import com.civileg.app.utils.SoilType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoilBearingViewModel @Inject constructor() : ViewModel() {

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private val _result = MutableLiveData<SoilBearingResult?>()
    val result: LiveData<SoilBearingResult?> = _result

    private val _isCalculating = MutableLiveData(false)
    val isCalculating: LiveData<Boolean> = _isCalculating

    private val _comparisonResults = MutableLiveData<Map<BearingMethod, SoilBearingResult>>(emptyMap())
    val comparisonResults: LiveData<Map<BearingMethod, SoilBearingResult>> = _comparisonResults

    // ------------------------------------------------------------------
    // Input fields (two-way via MutableLiveData)
    // ------------------------------------------------------------------

    val method = MutableLiveData(BearingMethod.TERZAGHI)
    val soilType = MutableLiveData(SoilType.CLAY)

    val foundationWidth = MutableLiveData("1.5")
    val foundationLength = MutableLiveData("1.5")
    val foundationDepth = MutableLiveData("1.0")

    val cohesion = MutableLiveData("25.0")
    val frictionAngle = MutableLiveData("30.0")
    val unitWeight = MutableLiveData("18.0")

    val waterTableDepth = MutableLiveData("5.0")

    val eccentricityX = MutableLiveData("0.0")
    val eccentricityY = MutableLiveData("0.0")

    val loadInclinationX = MutableLiveData("0.0")
    val loadInclinationY = MutableLiveData("0.0")

    val safetyFactor = MutableLiveData("3.0")

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    private fun Double?.orDefault(default: Double) = this ?: default

    private fun MutableLiveData<String>.doubleValue(default: Double = 0.0): Double {
        return try {
            value?.toDoubleOrNull() ?: default
        } catch (_: NumberFormatException) {
            default
        }
    }

    // ------------------------------------------------------------------
    // Build input object from current LiveData values
    // ------------------------------------------------------------------

    private fun buildInput(): SoilBearingInput {
        return SoilBearingInput(
            method      = method.value ?: BearingMethod.TERZAGHI,
            soilType    = soilType.value ?: SoilType.CLAY,
            foundationWidth    = foundationWidth.doubleValue(1.5),
            foundationLength   = foundationLength.doubleValue(1.5),
            foundationDepth    = foundationDepth.doubleValue(1.0),
            cohesion           = cohesion.doubleValue(25.0),
            frictionAngle      = frictionAngle.doubleValue(30.0),
            unitWeight         = unitWeight.doubleValue(18.0),
            waterTableDepth    = waterTableDepth.doubleValue(5.0),
            eccentricityX      = eccentricityX.doubleValue(0.0),
            eccentricityY      = eccentricityY.doubleValue(0.0),
            loadInclinationX   = loadInclinationX.doubleValue(0.0),
            loadInclinationY   = loadInclinationY.doubleValue(0.0),
            safetyFactor       = safetyFactor.doubleValue(3.0)
        )
    }

    // ------------------------------------------------------------------
    // Core calculation
    // ------------------------------------------------------------------

    /** Run the selected method and post the result. */
    fun calculate() {
        _isCalculating.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val calculator = SoilBearingCalculator()
                val input = buildInput()
                val res = when (input.method) {
                    BearingMethod.TERZAGHI -> calculator.calculateTerzaghi(input)
                    BearingMethod.MEYERHOF -> calculator.calculateMeyerhof(input)
                    BearingMethod.HANSEN   -> calculator.calculateHansen(input)
                    BearingMethod.VESIC    -> calculator.calculateVesic(input)
                }
                _result.postValue(res)
            } catch (e: Exception) {
                _result.postValue(null)
            } finally {
                _isCalculating.postValue(false)
            }
        }
    }

    /** Compare all four methods and post results. */
    fun compareAllMethods() {
        _isCalculating.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val calculator = SoilBearingCalculator()
                val input = buildInput()
                val map = calculator.compareAllMethods(input)
                _comparisonResults.postValue(map)
                // Also set the single result to the selected method
                _result.postValue(map[input.method])
            } catch (e: Exception) {
                _result.postValue(null)
                _comparisonResults.postValue(emptyMap())
            } finally {
                _isCalculating.postValue(false)
            }
        }
    }

    // ------------------------------------------------------------------
    // Convenience: reset to defaults
    // ------------------------------------------------------------------

    fun resetToDefaults() {
        method.postValue(BearingMethod.TERZAGHI)
        soilType.postValue(SoilType.CLAY)
        foundationWidth.postValue("1.5")
        foundationLength.postValue("1.5")
        foundationDepth.postValue("1.0")
        cohesion.postValue("25.0")
        frictionAngle.postValue("30.0")
        unitWeight.postValue("18.0")
        waterTableDepth.postValue("5.0")
        eccentricityX.postValue("0.0")
        eccentricityY.postValue("0.0")
        loadInclinationX.postValue("0.0")
        loadInclinationY.postValue("0.0")
        safetyFactor.postValue("3.0")
        _result.postValue(null)
        _comparisonResults.postValue(emptyMap())
    }

    // ------------------------------------------------------------------
    // Quick presets based on soil type
    // ------------------------------------------------------------------

    fun applySoilPreset(soilType: SoilType) {
        this.soilType.postValue(soilType)
        when (soilType) {
            SoilType.CLAY -> {
                cohesion.postValue("25.0")
                frictionAngle.postValue("5.0")
                unitWeight.postValue("17.0")
            }
            SoilType.SAND -> {
                cohesion.postValue("0.0")
                frictionAngle.postValue("35.0")
                unitWeight.postValue("19.0")
            }
            SoilType.ROCK -> {
                cohesion.postValue("100.0")
                frictionAngle.postValue("45.0")
                unitWeight.postValue("24.0")
            }
            SoilType.MIXED -> {
                cohesion.postValue("15.0")
                frictionAngle.postValue("20.0")
                unitWeight.postValue("18.5")
            }
        }
    }

    fun onMethodSelected(method: BearingMethod) {
        this.method.postValue(method)
    }

    fun onSoilTypeSelected(soilType: SoilType) {
        this.soilType.postValue(soilType)
    }
}
