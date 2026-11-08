package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.SoilStructureInteraction
import com.civileg.app.domain.entities.DesignCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoilStructureViewModel @Inject constructor() : ViewModel() {

    private val _result = MutableLiveData<SoilStructureInteraction.Result?>()
    val result: LiveData<SoilStructureInteraction.Result?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastCode: DesignCode = DesignCode.ECP

    fun calculate(
        foundationLengthMm: Double,
        foundationWidthMm: Double,
        foundationThicknessMm: Double,
        columnLoadKN: Double,
        momentKNm: Double,
        soilBearingCapacityKpa: Double,
        soilUnitWeightKNm3: Double,
        frictionAngleDeg: Double,
        fcu: Double,
        fy: Double,
        embedmentDepthMm: Double,
        code: DesignCode
    ) {
        lastCode = code
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val design = CalculationFactory.getSoilStructureDesign(code)
                val inputs = SoilStructureInteraction.Inputs(
                    foundationLengthMm = foundationLengthMm,
                    foundationWidthMm = foundationWidthMm,
                    foundationThicknessMm = foundationThicknessMm,
                    columnLoadKN = columnLoadKN,
                    momentKNm = momentKNm,
                    soilBearingCapacityKpa = soilBearingCapacityKpa,
                    soilUnitWeightKnm3 = soilUnitWeightKNm3,
                    frictionAngleDeg = frictionAngleDeg,
                    fcu = fcu,
                    fy = fy,
                    embedmentDepthMm = embedmentDepthMm
                )
                val res = design.design(inputs)
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "فشل الحساب / Calculation failed: ${e.message}"
                _result.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reset() {
        _result.value = null
        _error.value = null
        _isLoading.value = false
    }
}