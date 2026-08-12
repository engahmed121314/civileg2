package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.utils.EstimationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BOQViewModel @Inject constructor(
    private val estimationEngine: EstimationEngine
) : ViewModel() {

    private val _estimationResult = MutableLiveData<EstimationEngine.EstimationResult?>()
    val estimationResult: LiveData<EstimationEngine.EstimationResult?> = _estimationResult

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun estimateFullProject(
        type: EstimationEngine.FullProjectType,
        area: Double,
        floors: Int,
        hasBasement: Boolean,
        factoryType: EstimationEngine.FactoryStructureType? = null,
        landPrice: Double = 0.0,
        expectedSellingPrice: Double = 0.0,
        currency: String = "EGP"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = estimationEngine.estimateFullProject(
                    type, area, floors, hasBasement, factoryType, landPrice, expectedSellingPrice, currency
                )
                _estimationResult.value = result
            } catch (e: Exception) {
                _estimationResult.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun estimateApartmentFinishingPro(area: Double, currency: String = "EGP") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = estimationEngine.estimateApartmentFinishingPro(area, currency)
                _estimationResult.value = result
            } catch (e: Exception) {
                _estimationResult.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun estimateSpecificItem(name: String, qty: Double, price: Double, currency: String = "EGP") {
        viewModelScope.launch {
            _isLoading.value = true
            _estimationResult.value = estimationEngine.estimateSpecificItem(name, qty, price, currency)
            _isLoading.value = false
        }
    }

    fun clearResult() {
        _estimationResult.value = null
    }
}
