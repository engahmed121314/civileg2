package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.ElementType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class UnifiedDesignUiState(
    val selectedElementType: ElementType? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UnifiedDesignViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedDesignUiState())
    val uiState: StateFlow<UnifiedDesignUiState> = _uiState.asStateFlow()

    fun selectElement(type: ElementType) {
        _uiState.update { it.copy(selectedElementType = type) }
    }

    fun resetSelection() {
        _uiState.update { it.copy(selectedElementType = null) }
    }
}
