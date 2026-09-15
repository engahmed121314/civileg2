package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.core.engineering.SettlementAnalysisEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoilSettlementViewModel @Inject constructor() : ViewModel() {

    private val _result = MutableLiveData<SettlementAnalysisEngine.SettlementResult?>()
    val result: LiveData<SettlementAnalysisEngine.SettlementResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun calculate(
        pressure: Double, width: Double, length: Double,
        layers: List<SettlementAnalysisEngine.SoilLayer>, limit: Double
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = SettlementAnalysisEngine.calculateSettlement(pressure, width, length, layers, limit)
                _result.value = res
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
