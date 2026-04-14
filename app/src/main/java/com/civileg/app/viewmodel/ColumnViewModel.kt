package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.AppDatabase
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ColumnUiState(
    val width: Double = 300.0,
    val depth: Double = 300.0,
    val height: Double = 3.0,
    val fcu: Double = 25.0,
    val fy: Double = 400.0,
    val axialLoad: Double = 1000.0,
    val designCode: DesignCode = DesignCode.ECP,
    val loadCombination: LoadCombination = LoadCombination.DEAD_LIVE,
    val result: CalculatorEngine.ColumnResult? = null,
    val isLoading: Boolean = false,
    val errors: List<String> = emptyList()
)

@HiltViewModel
class ColumnViewModel @Inject constructor(
    private val db: AppDatabase,
    private val calculatorEngine: CalculatorEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnUiState())
    val uiState: StateFlow<ColumnUiState> = _uiState.asStateFlow()
    
    val uiStateLiveData: LiveData<ColumnUiState> = _uiState.asLiveData()
    val result: LiveData<CalculatorEngine.ColumnResult?> = _uiState.map { it.result }.asLiveData()

    fun updateInputs(
        width: Double? = null,
        depth: Double? = null,
        height: Double? = null,
        fcu: Double? = null,
        fy: Double? = null,
        axialLoad: Double? = null
    ) {
        _uiState.update { state ->
            state.copy(
                width = width ?: state.width,
                depth = depth ?: state.depth,
                height = height ?: state.height,
                fcu = fcu ?: state.fcu,
                fy = fy ?: state.fy,
                axialLoad = axialLoad ?: state.axialLoad
            )
        }
        calculate()
    }

    fun updateDesignCode(code: DesignCode) {
        _uiState.update { it.copy(designCode = code) }
        calculate()
    }

    fun updateLoadCombination(combination: LoadCombination) {
        _uiState.update { it.copy(loadCombination = combination) }
        calculate()
    }

    fun calculateColumnPro(width: Double, depth: Double, height: Double, fcu: Double, fy: Double, load: Double, diameter: Int, code: CalculatorEngine.DesignCode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // تصحيح: تمرير البارامترات حسب تعريف الدالة في CalculatorEngine
                // الدالة تتوقع: (width, depth, pu, fcu, fy, code)
                val res = calculatorEngine.designColumn(
                    width = width,
                    depth = depth,
                    pu = load,
                    fcu = fcu,
                    fy = fy,
                    code = code
                )
                _uiState.update { it.copy(result = res, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errors = listOf(e.message ?: "Error")) }
            }
        }
    }

    private fun calculate() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                // تصحيح: استخدام البارامترات الصحيحة للدالة الحالية
                val res = calculatorEngine.designColumn(
                    width = state.width,
                    depth = state.depth,
                    pu = state.axialLoad * state.loadCombination.factor,
                    fcu = state.fcu,
                    fy = state.fy,
                    code = mapDesignCode(state.designCode)
                )
                _uiState.update { it.copy(result = res, errors = emptyList()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errors = listOf(e.message ?: "Error")) }
            }
        }
    }

    private fun mapDesignCode(code: DesignCode): CalculatorEngine.DesignCode = when(code) {
        DesignCode.ECP -> CalculatorEngine.DesignCode.EGYPTIAN
        DesignCode.ACI -> CalculatorEngine.DesignCode.ACI
        DesignCode.SBC -> CalculatorEngine.DesignCode.SAUDI
    }

    fun reset() {
        _uiState.value = ColumnUiState()
    }
}
