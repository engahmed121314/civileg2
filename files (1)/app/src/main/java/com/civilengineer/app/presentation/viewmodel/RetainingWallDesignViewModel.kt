package com.civilengineer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.RetainingWall
import com.civilengineer.app.data.models.WallType
import com.civilengineer.app.data.repository.RetainingWallRepository
import com.civilengineer.app.domain.calculator.RetainingWallCalculator
import com.civilengineer.app.domain.calculator.CostCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RetainingWallDesignUIState(
    val walls: List<RetainingWall> = emptyList(),
    val selectedWall: RetainingWall? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val designEquations: String = ""
)

@HiltViewModel
class RetainingWallDesignViewModel @Inject constructor(
    private val wallRepository: RetainingWallRepository,
    private val wallCalculator: RetainingWallCalculator,
    private val costCalculator: CostCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(RetainingWallDesignUIState())
    val uiState: StateFlow<RetainingWallDesignUIState> = _uiState.asStateFlow()

    init {
        loadAllWalls()
    }

    private fun loadAllWalls() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                wallRepository.getAllWalls().collect { walls ->
                    _uiState.update { it.copy(walls = walls, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun saveWall(wall: RetainingWall) {
        viewModelScope.launch {
            try {
                if (wall.id == 0) {
                    wallRepository.insertWall(wall)
                } else {
                    wallRepository.updateWall(wall)
                }
                loadAllWalls()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectWall(wall: RetainingWall) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedWall = wall,
                    designEquations = wallCalculator.createDesignEquations(wall)
                )
            }
        }
    }

    fun deleteWall(id: Int) {
        viewModelScope.launch {
            try {
                wallRepository.deleteWallById(id)
                _uiState.update { it.copy(selectedWall = null) }
                loadAllWalls()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun performDesignCalculation(
        wallHeightM: Double,
        soilHeightBehindM: Double,
        soilUnitWeightKNM3: Double,
        soilFrictionAngleDeg: Double,
        soilCohesionKNM2: Double,
        bearingCapacityKNM2: Double,
        concreteGrade: String,
        steelGrade: String,
        codeType: CodeType,
        wallType: WallType,
        wallName: String = "حائط سند جديد"
    ) {
        viewModelScope.launch {
            try {
                val activePressure = wallCalculator.calculateActivePressure(
                    soilHeightBehindM,
                    soilUnitWeightKNM3,
                    soilFrictionAngleDeg
                )

                val passivePressure = wallCalculator.calculatePassivePressure(
                    wallHeightM,
                    soilUnitWeightKNM3,
                    soilFrictionAngleDeg
                )

                val overturbingMoment = wallCalculator.calculateOverturbingMoment(
                    activePressure,
                    wallHeightM
                )

                val wallWeight = wallHeightM * 0.4 * 25 // تقريبي
                val resistanceMoment = wallCalculator.calculateResistanceMoment(
                    wallWeight,
                    0.6
                )

                val fsOverturning = wallCalculator.calculateOverturbingSafetyFactor(
                    resistanceMoment,
                    overturbingMoment
                )

                val frictionForce = wallCalculator.calculateFrictionForce(
                    wallWeight,
                    soilFrictionAngleDeg
                )

                val fsSliding = wallCalculator.calculateSlidingSafetyFactor(
                    frictionForce,
                    activePressure
                )

                val eccentricity = wallCalculator.calculateEccentricity(
                    0.6,
                    wallWeight,
                    overturbingMoment,
                    activePressure
                )

                val (maxPressure, minPressure) = wallCalculator.calculateBasePressure(
                    wallWeight,
                    0.6,
                    1.0,
                    eccentricity
                )

                val isSafeOverturning = fsOverturning >= 1.5
                val isSafeSliding = fsSliding >= 1.5
                val isSafeBearing = maxPressure <= bearingCapacityKNM2 && minPressure >= 0

                val stemSteelArea = wallCalculator.calculateStemSteelArea(
                    activePressure,
                    wallHeightM,
                    0.4,
                    steelGrade,
                    concreteGrade,
                    codeType
                )

                val baseSteelArea = wallCalculator.calculateBaseSteelArea(
                    wallWeight,
                    0.6,
                    0.5,
                    steelGrade,
                    concreteGrade,
                    codeType
                )

                val concreteVolume = wallHeightM * 0.4 + (0.6 * 0.5)
                val concreteCost = costCalculator.calculateConcreteCost(concreteVolume, 500.0)
                val steelCost = costCalculator.calculateSteelCost(
                    stemSteelArea + baseSteelArea,
                    wallHeightM,
                    costPerTon = 5000.0
                )
                val totalCost = costCalculator.calculateTotalCost(concreteCost, steelCost)

                val designedWall = RetainingWall(
                    wallName = wallName,
                    wallHeightM = wallHeightM,
                    soilHeightBehindM = soilHeightBehindM,
                    soilUnitWeightKNM3 = soilUnitWeightKNM3,
                    soilFrictionAngleDeg = soilFrictionAngleDeg,
                    soilCohesionKNM2 = soilCohesionKNM2,
                    bearingCapacityKNM2 = bearingCapacityKNM2,
                    concreteGrade = concreteGrade,
                    steelGrade = steelGrade,
                    codeType = codeType,
                    wallType = wallType,
                    baseWidthM = 0.6,
                    topWidthM = 0.4,
                    stemThicknessM = 0.4,
                    baseThicknessM = 0.5,
                    activePressureKNM = activePressure,
                    passivePressureKNM = passivePressure,
                    totalHorizontalForceKN = activePressure,
                    totalVerticalLoadKN = wallWeight,
                    overturbingMomentKNM = overturbingMoment,
                    resistanceMomentKNM = resistanceMoment,
                    factorOfSafetyOverturning = fsOverturning,
                    isSafeOverturning = isSafeOverturning,
                    slidingFrictionKN = frictionForce,
                    factorOfSafetySliding = fsSliding,
                    isSafeSliding = isSafeSliding,
                    eccentricityM = eccentricity,
                    maxToePressureKNM2 = maxPressure,
                    minHeelPressureKNM2 = minPressure,
                    isSafeBearing = isSafeBearing,
                    stemSteelAreaMM2 = stemSteelArea,
                    baseSteelAreaMM2 = baseSteelArea,
                    totalCost = totalCost,
                    equationsUsed = wallCalculator.createDesignEquations(RetainingWall(
                        wallName = wallName,
                        wallHeightM = wallHeightM,
                        soilHeightBehindM = soilHeightBehindM,
                        soilUnitWeightKNM3 = soilUnitWeightKNM3,
                        soilFrictionAngleDeg = soilFrictionAngleDeg,
                        soilCohesionKNM2 = soilCohesionKNM2,
                        bearingCapacityKNM2 = bearingCapacityKNM2,
                        concreteGrade = concreteGrade,
                        steelGrade = steelGrade,
                        codeType = codeType,
                        wallType = wallType,
                        activePressureKNM = activePressure,
                        totalHorizontalForceKN = activePressure,
                        totalVerticalLoadKN = wallWeight,
                        overturbingMomentKNM = overturbingMoment,
                        resistanceMomentKNM = resistanceMoment,
                        factorOfSafetyOverturning = fsOverturning,
                        slidingFrictionKN = frictionForce,
                        factorOfSafetySliding = fsSliding,
                        maxToePressureKNM2 = maxPressure,
                        minHeelPressureKNM2 = minPressure
                    ))
                )

                saveWall(designedWall)
                selectWall(designedWall)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "خطأ في الحساب: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}