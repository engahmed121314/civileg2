package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.InventoryItem
import com.civileg.app.db.InventoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedType: InventoryType? = null
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        loadAllItems()
    }

    private fun loadAllItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getAllInventoryItems().collect { items ->
                _uiState.value = _uiState.value.copy(items = items, isLoading = false)
            }
        }
    }

    fun filterByType(type: InventoryType?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        viewModelScope.launch {
            if (type == null) {
                repository.getAllInventoryItems().collect { items ->
                    _uiState.value = _uiState.value.copy(items = items)
                }
            } else {
                repository.getInventoryItemsByType(type).collect { items ->
                    _uiState.value = _uiState.value.copy(items = items)
                }
            }
        }
    }

    fun addItem(name: String, type: InventoryType, quantity: Double, unit: String, alertQty: Double) {
        viewModelScope.launch {
            val newItem = InventoryItem(
                name = name,
                type = type,
                quantity = quantity,
                unit = unit,
                alertQuantity = alertQty,
                registrationDate = Date(),
                lastUpdated = Date()
            )
            repository.saveInventoryItem(newItem)
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteInventoryItem(item)
        }
    }

    fun updateQuantity(item: InventoryItem, newQuantity: Double) {
        viewModelScope.launch {
            repository.updateInventoryItem(item.copy(quantity = newQuantity, lastUpdated = Date()))
        }
    }
}
