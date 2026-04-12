package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TankViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.TankResult?>()
    val result: LiveData<CalculatorEngine.TankResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun calculateTankPro(
        type: CalculatorEngine.TankType,
        capacity: Double,
        height: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designTank(type, capacity, height, fcu, fy, preferredDiameter, code)
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Legacy method
    fun calculateTank(type: CalculatorEngine.TankType, capacity: Double, height: Double, fcu: Double, fy: Double) {
        calculateTankPro(type, capacity, height, fcu, fy, 12, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveTank(projectId: Long, name: String, result: CalculatorEngine.TankResult) {
        viewModelScope.launch {
            repository.saveTankDesign(projectId, name, result)
        }
    }
}
