package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.AppDatabase
import com.civileg.app.db.Stair
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StairViewModel @Inject constructor(
    private val db: AppDatabase,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.StairResult?>()
    val result: LiveData<CalculatorEngine.StairResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun calculateStairPro(
        type: CalculatorEngine.StairType,
        span: Double,
        riser: Double,
        tread: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designStaircase(
                    type = type,
                    span = span,
                    riser = riser,
                    tread = tread,
                    deadLoad = deadLoad,
                    liveLoad = liveLoad,
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

    // Fixed legacy support
    fun calculateStair(rise: Double, run: Double, width: Double, load: Double, fcu: Double, fy: Double) {
        calculateStairPro(CalculatorEngine.StairType.SINGLE_FLIGHT, run, rise, 300.0, load * 0.6, load * 0.4, fcu, fy, 12, CalculatorEngine.DesignCode.EGYPTIAN)
    }

    fun saveStair(projectId: Long, input: StairInputData, result: CalculatorEngine.StairResult) {
        viewModelScope.launch {
            val stair = Stair(
                projectId = projectId,
                thickness = result.thickness,
                load = input.load,
                fcu = input.fcu,
                fy = input.fy,
                reinforcement = result.reinforcement.barString,
                concreteVolume = result.concreteVolume,
                steelWeight = result.steelWeight,
                cost = result.cost
            )
            db.stairDao().insertStair(stair)
        }
    }
}

data class StairInputData(
    val rise: Double,
    val run: Double,
    val width: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double
)
