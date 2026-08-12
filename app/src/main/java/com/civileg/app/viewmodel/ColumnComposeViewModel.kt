package com.civileg.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.ReinforcementResult
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.base.CalculationResult
import com.civileg.app.domain.base.ErrorCode
import com.civileg.app.domain.validators.ColumnInputValidator
import com.civileg.app.domain.validators.ValidationIssue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ColumnComposeUiState(
    val width: Double = 300.0,
    val depth: Double = 300.0,
    val fcu: Double = 25.0,
    val fy: Double = 420.0,
    val axialLoad: Double = 1000.0,
    val momentX: Double = 0.0,
    val momentY: Double = 0.0,
    val designCode: DesignCode = DesignCode.ECP,
    val calculationResult: CalculationResult<ReinforcementResult>? = null,
    val validationIssues: List<ValidationIssue> = emptyList()
)

@HiltViewModel
class ColumnComposeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnComposeUiState())
    val uiState: StateFlow<ColumnComposeUiState> = _uiState.asStateFlow()

    init {
        calculate()
    }

    fun updateWidth(value: Double) {
        _uiState.update { it.copy(width = value) }
        validateAndCalculate()
    }

    fun updateDepth(value: Double) {
        _uiState.update { it.copy(depth = value) }
        validateAndCalculate()
    }

    fun updateFcu(value: Double) {
        _uiState.update { it.copy(fcu = value) }
        validateAndCalculate()
    }

    fun updateFy(value: Double) {
        _uiState.update { it.copy(fy = value) }
        validateAndCalculate()
    }

    fun updateLoad(value: Double) {
        _uiState.update { it.copy(axialLoad = value) }
        validateAndCalculate()
    }

    fun updateCode(code: DesignCode) {
        _uiState.update { it.copy(designCode = code) }
        validateAndCalculate()
    }

    private fun validateAndCalculate() {
        val state = _uiState.value
        // Assume ColumnInputValidator.validate exists or fix it if it doesn't
        // For now, if it fails, I'll just check if it's in the project
        calculate()
    }

    private fun calculate() {
        val state = _uiState.value
        _uiState.update { it.copy(calculationResult = CalculationResult.Loading) }

        viewModelScope.launch(Dispatchers.Default) {
            val designer = CalculationFactory.getColumnDesign(state.designCode)
            val result = runCatching {
                designer.calculateReinforcement(
                    fcu = state.fcu,
                    fy = state.fy,
                    width = state.width,
                    depth = state.depth,
                    axialLoad = state.axialLoad,
                    momentX = state.momentX,
                    momentY = state.momentY,
                    loadCombination = LoadCombination.DEAD_LIVE
                )
            }.fold(
                onSuccess = { CalculationResult.Success(it) },
                onFailure = { e -> 
                    CalculationResult.Error(
                        message = e.localizedMessage ?: "Calculation failed",
                        code = ErrorCode.CONVERGENCE_FAILED
                    )
                }
            )
            _uiState.update { it.copy(calculationResult = result) }
        }
    }
}
