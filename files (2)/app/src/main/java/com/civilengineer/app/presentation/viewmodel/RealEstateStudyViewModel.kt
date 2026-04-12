package com.civilengineer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civilengineer.app.data.models.*
import com.civilengineer.app.data.repository.RealEstateStudyRepository
import com.civilengineer.app.domain.calculator.RealEstateCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealEstateStudyUIState(
    val studies: List<RealEstateStudy> = emptyList(),
    val selectedStudy: RealEstateStudy? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RealEstateStudyViewModel @Inject constructor(
    private val repository: RealEstateStudyRepository,
    private val calculator: RealEstateCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(RealEstateStudyUIState())
    val uiState: StateFlow<RealEstateStudyUIState> = _uiState.asStateFlow()

    init {
        loadAllStudies()
    }

    private fun loadAllStudies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.getAllStudies().collect { studies ->
                    _uiState.update { it.copy(studies = studies, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun performStudy(
        studyName: String,
        landAreaM2: Double,
        buildingBudget: Double,
        numberOfFloors: Int,
        projectType: ProjectType,
        buildOnFullLand: Boolean,
        buildingAreaPercentage: Double
    ) {
        viewModelScope.launch {
            try {
                // حساب مساحة البناء
                val buildingAreaM2 = if (buildOnFullLand) {
                    landAreaM2
                } else {
                    landAreaM2 * (buildingAreaPercentage / 100)
                }

                // حساب التكلفة للمتر المربع بناءً على نوع المشروع والأدوار
                val costPerM2Estimated = calculator.estimateCostPerM2(
                    projectType,
                    numberOfFloors
                )

                // حساب التكلفة الإجمالية
                val estimatedTotalCost = buildingAreaM2 * costPerM2Estimated

                // حساب نسبة تغطية الميزانية
                val budgetCoveragePercentage = (buildingBudget / estimatedTotalCost) * 100

                // تحديد حالة الجدوى
                val feasibilityStatus = when {
                    budgetCoveragePercentage >= 100 -> FeasibilityStatus.FEASIBLE
                    budgetCoveragePercentage >= 85 -> FeasibilityStatus.FEASIBLE_WITH_REDUCTION
                    budgetCoveragePercentage >= 70 -> FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE
                    else -> FeasibilityStatus.NOT_FEASIBLE
                }

                // إنشاء توصيات
                val recommendations = generateRecommendations(
                    feasibilityStatus,
                    budgetCoveragePercentage,
                    buildingAreaM2,
                    numberOfFloors
                )

                // إنشاء توزيع التكاليف
                val costBreakdown = calculator.generateCostBreakdown(projectType)

                val study = RealEstateStudy(
                    studyName = studyName,
                    projectType = projectType,
                    landAreaM2 = landAreaM2,
                    buildingAreaM2 = buildingAreaM2,
                    buildOnFullLand = buildOnFullLand,
                    numberOfFloors = numberOfFloors,
                    buildingBudget = buildingBudget,
                    costPerM2Estimated = costPerM2Estimated,
                    estimatedTotalCost = estimatedTotalCost,
                    budgetCoveragePercentage = budgetCoveragePercentage,
                    costBreakdown = costBreakdown,
                    feasibilityStatus = feasibilityStatus,
                    recommendations = recommendations
                )

                repository.insertStudy(study)
                _uiState.update { it.copy(selectedStudy = study) }
                loadAllStudies()

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "خطأ في الحساب: ${e.message}") }
            }
        }
    }

    private fun generateRecommendations(
        feasibilityStatus: FeasibilityStatus,
        budgetCoveragePercentage: Double,
        buildingAreaM2: Double,
        numberOfFloors: Int
    ): String {
        return when (feasibilityStatus) {
            FeasibilityStatus.FEASIBLE -> {
                "المشروع ممكن التنفيذ بنجاح. الميزانية كافية للعمل على المساحة المطلوبة بعدد أدوار $numberOfFloors"
            }
            FeasibilityStatus.FEASIBLE_WITH_REDUCTION -> {
                "يمكن تنفيذ المشروع مع تقليل مساحة البناء بنسبة ${"%.1f".format(100 - budgetCoveragePercentage)}%\n" +
                "أو تقليل عدد الأدوار من $numberOfFloors إلى ${ numberOfFloors - 1}"
            }
            FeasibilityStatus.FEASIBLE_WITH_BUDGET_INCREASE -> {
                "يتطلب المشروع زيادة في الميزانية بمعدل ${"%.1f".format(100 - budgetCoveragePercentage)}%\n" +
                "يمكنك تقليل مساحة البناء بشكل كبير أو تقليل مستوى التشطيب"
            }
            FeasibilityStatus.NOT_FEASIBLE -> {
                "المشروع غير ممكن بالميزانية الحالية. يتطلب زيادة بنسبة ${"%.1f".format(100 - budgetCoveragePercentage)}%\n" +
                "يفضل إعادة دراسة نطاق المشروع أو زيادة الميزانية بشكل كبير"
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}