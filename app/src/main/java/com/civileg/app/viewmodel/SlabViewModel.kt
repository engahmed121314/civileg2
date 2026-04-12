package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.AppDatabase
import com.civileg.app.db.Slab
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SlabViewModel @Inject constructor(
    private val db: AppDatabase,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _result = MutableLiveData<CalculatorEngine.SlabResult?>()
    val result: LiveData<CalculatorEngine.SlabResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun calculateSlabPro(
        lx: Double,
        ly: Double,
        deadLoad: Double,
        liveLoad: Double,
        fcu: Double,
        fy: Double,
        ts: Double,
        preferredDiameter: Int,
        code: CalculatorEngine.DesignCode
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = calculatorEngine.designSlab(
                    lx = lx,
                    ly = ly,
                    deadLoad = deadLoad,
                    liveLoad = liveLoad,
                    fcu = fcu,
                    fy = fy,
                    ts = ts,
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

    // Updated method to handle full parameters from Compose Screen
    fun calculateSlab(
        spanX: Double,
        spanY: Double,
        load: Double,
        fcu: Double,
        fy: Double,
        type: CalculatorEngine.SlabType = CalculatorEngine.SlabType.SOLID,
        code: CalculatorEngine.DesignCode = CalculatorEngine.DesignCode.EGYPTIAN
    ) {
        calculateSlabPro(spanX, spanY, load * 0.6, load * 0.4, fcu, fy, 150.0, 10, code)
    }

    fun saveSlab(projectId: Long, input: SlabInputData, result: CalculatorEngine.SlabResult) {
        viewModelScope.launch {
            val slab = Slab(
                projectId = projectId,
                type = result.type.name,
                spanX = input.spanX,
                spanY = input.spanY,
                thickness = result.thickness,
                load = input.load,
                fcu = input.fcu,
                fy = input.fy,
                reinforcement = result.reinforcementMain.barString,
                concreteVolume = result.concreteVolume,
                steelWeight = result.steelWeight,
                cost = result.cost
            )
            db.slabDao().insertSlab(slab)
        }
    }
}

data class SlabInputData(
    val spanX: Double,
    val spanY: Double,
    val load: Double,
    val fcu: Double,
    val fy: Double,
    val type: CalculatorEngine.SlabType
)
