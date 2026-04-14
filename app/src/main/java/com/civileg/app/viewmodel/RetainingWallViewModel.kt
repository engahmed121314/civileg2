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
class RetainingWallViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.RetainingWallResult?>()
    val result: LiveData<CalculatorEngine.RetainingWallResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun calculateRetainingWallPro(
        height: Double,
        soilDensity: Double,
        frictionAngle: Double,
        surcharge: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designRetainingWall(
                    height = height,
                    soilDensity = soilDensity,
                    frictionAngle = frictionAngle,
                    surcharge = surcharge,
                    fcu = fcu,
                    fy = fy,
                    preferredDiameter = preferredDiameter,
                    code = code
                )
                _result.value = res
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Legacy method for backward compatibility
    fun calculateRetainingWall(height: Double, soilDensity: Double, frictionAngle: Double, surcharge: Double, fcu: Double, fy: Double) {
        calculateRetainingWallPro(height, soilDensity, frictionAngle, surcharge, fcu, fy, 16, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveRetainingWall(projectId: Long, name: String, result: CalculatorEngine.RetainingWallResult) {
        viewModelScope.launch {
            repository.saveRetainingWallDesign(projectId, name, result)
        }
    }
}
