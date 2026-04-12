package com.civilengineer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.data.models.SlabType
import com.civilengineer.app.data.models.SupportType
import com.civilengineer.app.data.repository.SlabRepository
import com.civilengineer.app.domain.calculator.SlabCalculator
import com.civilengineer.app.domain.calculator.CostCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlabDesignUIState(
    val slabs: List<SlabDesign> = emptyList(),
    val selectedSlab: SlabDesign? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val designEquations: String = ""
)

@HiltViewModel
class SlabDesignViewModel @Inject constructor(
    private val slabRepository: SlabRepository,
    private val slabCalculator: SlabCalculator,
    private val costCalculator: CostCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlabDesignUIState())
    val uiState: StateFlow<SlabDesignUIState> = _uiState.asStateFlow()

    init {
        loadAllSlabs()
    }

    private fun loadAllSlabs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                slabRepository.getAllSlabs().collect { slabs ->
                    _uiState.update { it.copy(slabs = slabs, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun saveSlab(slab: SlabDesign) {
        viewModelScope.launch {
            try {
                if (slab.id == 0) {
                    slabRepository.insertSlab(slab)
                } else {
                    slabRepository.updateSlab(slab)
                }
                loadAllSlabs()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectSlab(slab: SlabDesign) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedSlab = slab,
                    designEquations = slabCalculator.createDesignEquations(slab)
                )
            }
        }
    }

    fun deleteSlab(id: Int) {
        viewModelScope.launch {
            try {
                slabRepository.deleteSlabById(id)
                _uiState.update { it.copy(selectedSlab = null) }
                loadAllSlabs()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun performDesignCalculation(
        lengthM: Double,
        widthM: Double,
        thicknessMM: Int,
        liveLoadKNM2: Double,
        deadLoadKNM2: Double,
        concreteGrade: String,
        steelGrade: String,
        codeType: CodeType,
        slabType: SlabType,
        supportType: SupportType,
        slabName: String = "بلاطة جديدة"
    ) {
        viewModelScope.launch {
            try {
                val totalLoad = slabCalculator.calculateTotalLoad(
                    deadLoadKNM2,
                    liveLoadKNM2,
                    lengthM,
                    widthM
                )

                val maxMoment = slabCalculator.calculateMaxBendingMoment(
                    lengthM,
                    widthM,
                    totalLoad,
                    slabType,
                    supportType,
                    codeType
                )

                val shearForce = slabCalculator.calculateMaxShearForce(
                    lengthM,
                    widthM,
                    totalLoad,
                    supportType
                )

                val requiredSteelBottom = slabCalculator.calculateRequiredSteelArea(
                    maxMoment,
                    thicknessMM,
                    25,
                    steelGrade,
                    concreteGrade,
                    codeType
                )

                val minSteelArea = slabCalculator.calculateMinimumSteelArea(
                    lengthM,
                    thicknessMM,
                    concreteGrade,
                    codeType
                )

                val requiredSteelBottomFinal = maxOf(requiredSteelBottom, minSteelArea)

                val (bottomDia, bottomSpacing) = slabCalculator.selectSteelDiameterAndSpacing(
                    requiredSteelBottomFinal,
                    lengthM
                )

                val deflection = slabCalculator.calculateDeflection(
                    liveLoadKNM2 + deadLoadKNM2,
                    lengthM,
                    thicknessMM,
                    concreteGrade,
                    codeType
                )

                val maxAllowedDeflection = slabCalculator.calculateMaxAllowedDeflection(
                    lengthM,
                    supportType,
                    codeType
                )

                val isSafe = slabCalculator.checkSafety(
                    deflection,
                    maxAllowedDeflection,
                    maxMoment * 1.5,
                    maxMoment,
                    shearForce * 1.5,
                    shearForce
                )

                val concreteVolume = lengthM * widthM * (thicknessMM / 1000.0)
                val concreteCost = costCalculator.calculateConcreteCost(concreteVolume, 500.0)
                val steelCost = costCalculator.calculateSteelCost(
                    requiredSteelBottomFinal,
                    lengthM,
                    costPerTon = 5000.0
                )
                val totalCost = costCalculator.calculateTotalCost(concreteCost, steelCost)

                val designedSlab = SlabDesign(
                    slabName = slabName,
                    lengthM = lengthM,
                    widthM = widthM,
                    thicknessMM = thicknessMM,
                    liveLoadKNM2 = liveLoadKNM2,
                    deadLoadKNM2 = deadLoadKNM2,
                    concreteGrade = concreteGrade,
                    steelGrade = steelGrade,
                    codeType = codeType,
                    slabType = slabType,
                    supportType = supportType,
                    maxMomentKNM = maxMoment,
                    shearForceKN = shearForce,
                    bottomSteelDiaMM = bottomDia,
                    bottomSteelSpacingMM = bottomSpacing,
                    topSteelDiaMM = bottomDia,
                    topSteelSpacingMM = bottomSpacing,
                    bottomSteelAreaMM2M = requiredSteelBottomFinal,
                    topSteelAreaMM2M = requiredSteelBottomFinal * 0.5,
                    isSafe = isSafe,
                    deflectionMM = deflection,
                    maxAllowedDeflectionMM = maxAllowedDeflection,
                    totalCost = totalCost,
                    equationsUsed = slabCalculator.createDesignEquations(SlabDesign(
                        slabName = slabName,
                        lengthM = lengthM,
                        widthM = widthM,
                        thicknessMM = thicknessMM,
                        liveLoadKNM2 = liveLoadKNM2,
                        deadLoadKNM2 = deadLoadKNM2,
                        concreteGrade = concreteGrade,
                        steelGrade = steelGrade,
                        codeType = codeType,
                        slabType = slabType,
                        supportType = supportType,
                        maxMomentKNM = maxMoment,
                        shearForceKN = shearForce,
                        deflectionMM = deflection,
                        maxAllowedDeflectionMM = maxAllowedDeflection
                    ))
                )

                saveSlab(designedSlab)
                selectSlab(designedSlab)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "خطأ في الحساب: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}