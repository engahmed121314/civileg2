package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.calculations.base.WallOpeningDesign
import com.civileg.app.domain.calculations.wallopenings.ECPWallOpening
import com.civileg.app.domain.calculations.wallopenings.ACIWallOpening
import com.civileg.app.domain.calculations.wallopenings.SBCWallOpening
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WallOpeningViewModel @Inject constructor(
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<WallOpeningDesign.Result?>()
    val result: LiveData<WallOpeningDesign.Result?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var lastCode: DesignCode = DesignCode.ECP

    fun calculate(
        wallHeightMm: Double,
        wallThicknessMm: Double,
        openingWidthMm: Double,
        openingHeightMm: Double,
        openingFromBaseMm: Double,
        wallLengthMm: Double,
        fcu: Double,
        fy: Double,
        loadAboveOpeningKNm: Double,
        code: DesignCode
    ) {
        lastCode = code
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val design = CalculationFactory.getWallOpeningDesign(code)
                val inputs = WallOpeningDesign.Inputs(
                    wallHeightMm = wallHeightMm,
                    wallThicknessMm = wallThicknessMm,
                    openingWidthMm = openingWidthMm,
                    openingHeightMm = openingHeightMm,
                    openingFromBaseMm = openingFromBaseMm,
                    wallLengthMm = wallLengthMm,
                    fcu = fcu,
                    fy = fy,
                    loadAboveOpeningKNm = loadAboveOpeningKNm
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