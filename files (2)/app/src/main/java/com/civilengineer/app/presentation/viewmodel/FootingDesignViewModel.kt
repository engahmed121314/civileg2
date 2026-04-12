package com.civilengineer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.FootingDesign
import com.civilengineer.app.data.models.FootingType
import com.civilengineer.app.data.repository.FootingRepository
import com.civilengineer.app.domain.calculator.FootingCalculator
import com.civilengineer.app.domain.calculator.CostCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FootingDesignUIState(
    val footings: List<FootingDesign> = emptyList(),
    val selectedFooting: FootingDesign? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val designEquations: String = ""
)

@HiltViewModel
class FootingDesignViewModel @Inject constructor(
    private val footingRepository: FootingRepository,
    private val footingCalculator: FootingCalculator,
    private val costCalculator: CostCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(FootingDesignUIState())
    val uiState: StateFlow<FootingDesignUIState> = _uiState.asStateFlow()

    init {
        loadAllFootings()
    }

    private fun loadAllFootings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                footingRepository.getAllFootings().collect { footings ->
                    _uiState.update { it.copy(footings = footings, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun saveFooting(footing: FootingDesign) {
        viewModelScope.launch {
            try {
                if (footing.id == 0) {
                    footingRepository.insertFooting(footing)
                } else {
                    footingRepository.updateFooting(footing)
                }
                loadAllFootings()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectFooting(footing: FootingDesign) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedFooting = footing,
                    designEquations = footingCalculator.createDesignEquations(footing)
                )
            }
        }
    }

    fun deleteFooting(id: Int) {
        viewModelScope.launch {
            try {
                footingRepository.deleteFootingById(id)
                _uiState.update { it.copy(selectedFooting = null) }
                loadAllFootings()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun performDesignCalculation(
        columnLoadKN: Double,
        soilBearingCapacityKNM2: Double,
        concreteGrade: String,
        steelGrade: String,
        codeType: CodeType,
        footingType: FootingType,
        footingName: String = "قاعدة جديدة"
    ) {
        viewModelScope.launch {
            try {
                val requiredArea = footingCalculator.calculateRequiredFootingArea(
                    columnLoadKN,
                    soilBearingCapacityKNM2,
                    1.5
                )

                val footingLength = kotlin.math.sqrt(requiredArea)
                val footingWidth = footingLength
                val footingDepth = 0.75

                val actualPressure = footingCalculator.calculateActualSoilPressure(
                    columnLoadKN,
                    footingLength * footingWidth
                )

                val maxMoment = footingCalculator.calculateMaxMomentInFooting(
                    footingLength,
                    footingWidth,
                    0.3,
                    0.3,
                    actualPressure
                )

                val shearForce = footingCalculator.calculateShearForceInFooting(
                    footingWidth,
                    footingLength,
                    0.3,
                    actualPressure
                )

                val requiredSteel = footingCalculator.calculateRequiredSteelArea(
                    maxMoment,
                    (footingDepth * 1000).toInt(),
                    50,
                    steelGrade,
                    concreteGrade,
                    codeType
                )

                val minSteelArea = footingCalculator.calculateMinimumSteelArea(
                    footingLength * footingWidth,
                    concreteGrade,
                    codeType
                )

                val requiredSteelFinal = maxOf(requiredSteel, minSteelArea)

                val (steelDia, steelSpacing) = footingCalculator.selectSteelDiameterAndSpacing(
                    requiredSteelFinal,
                    footingLength
                )

                val isSafeBearing = actualPressure <= soilBearingCapacityKNM2
                val isSafeFlexure = requiredSteelFinal > 0
                val isSafeShear = shearForce > 0

                val concreteVolume = footingLength * footingWidth * footingDepth
                val concreteCost = costCalculator.calculateConcreteCost(concreteVolume, 500.0)
                val steelCost = costCalculator.calculateSteelCost(
                    requiredSteelFinal,
                    footingLength,
                    costPerTon = 5000.0
                )
                val totalCost = costCalculator.calculateTotalCost(concreteCost, steelCost)

                val designedFooting = FootingDesign(
                    footingName = footingName,
                    columnLoadKN = columnLoadKN,
                    soilBearingCapacityKNM2 = soilBearingCapacityKNM2,
                    concreteGrade = concreteGrade,
                    steelGrade = steelGrade,
                    codeType = codeType,
                    footingType = footingType,
                    lengthM = footingLength,
                    widthM = footingWidth,
                    depthM = footingDepth,
                    footingAreaM2 = footingLength * footingWidth,
                    actualSoilPressureKNM2 = actualPressure,
                    bearingCapacityRatio = soilBearingCapacityKNM2 / actualPressure,
                    isSafeBearing = isSafeBearing,
                    maxMomentKNM = maxMoment,
                    shearForceKN = shearForce,
                    steelAreaMM2 = requiredSteelFinal,
                    steelDiaMM = steelDia,
                    steelSpacingMM = steelSpacing,
                    minSteelAreaMM2 = minSteelArea,
                    isSafeFlexure = isSafeFlexure,
                    isSafeShear = isSafeShear,
                    totalCost = totalCost,
                    equationsUsed = footingCalculator.createDesignEquations(FootingDesign(
                        footingName = footingName,
                        columnLoadKN = columnLoadKN,
                        soilBearingCapacityKNM2 = soilBearingCapacityKNM2,
                        concreteGrade = concreteGrade,
                        steelGrade = steelGrade,
                        codeType = codeType,
                        footingType = footingType,
                        footingAreaM2 = footingLength * footingWidth,
                        actualSoilPressureKNM2 = actualPressure,
                        bearingCapacityRatio = soilBearingCapacityKNM2 / actualPressure,
                        maxMomentKNM = maxMoment,
                        shearForceKN = shearForce,
                        factorOfSafetyOverturning = 1.5,
                        factorOfSafetySliding = 1.5
                    ))
                )

                saveFooting(designedFooting)
                selectFooting(designedFooting)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "خطأ في الحساب: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}