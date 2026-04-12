package com.civilengineer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.data.models.CodeType
import com.civilengineer.app.data.models.ColumnType
import com.civilengineer.app.data.repository.ColumnRepository
import com.civilengineer.app.domain.calculator.ColumnCalculator
import com.civilengineer.app.domain.calculator.CostCalculator
import com.civilengineer.app.domain.calculator.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ColumnDesignUIState(
    val columns: List<ColumnDesign> = emptyList(),
    val selectedColumn: ColumnDesign? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCalculationDetails: Boolean = false,
    val designEquations: String = "",
    val searchQuery: String = "",
    val filterCodeType: CodeType? = null,
    val filterSafety: Boolean? = null
)

/**
 * ViewModel لإدارة تصميم الأعمدة
 */
@HiltViewModel
class ColumnDesignViewModel @Inject constructor(
    private val columnRepository: ColumnRepository,
    private val columnCalculator: ColumnCalculator,
    private val costCalculator: CostCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnDesignUIState())
    val uiState: StateFlow<ColumnDesignUIState> = _uiState.asStateFlow()

    init {
        loadAllColumns()
    }

    private fun loadAllColumns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                columnRepository.getAllColumns().collect { columns ->
                    _uiState.update { it.copy(columns = columns, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun searchColumns(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(searchQuery = query) }
            try {
                columnRepository.searchColumns(query).collect { columns ->
                    _uiState.update { it.copy(columns = columns) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun filterByCodeType(codeType: CodeType?) {
        viewModelScope.launch {
            _uiState.update { it.copy(filterCodeType = codeType) }
            try {
                if (codeType != null) {
                    columnRepository.getColumnsByCodeType(codeType.name).collect { columns ->
                        _uiState.update { it.copy(columns = columns) }
                    }
                } else {
                    loadAllColumns()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun filterBySafety(isSafe: Boolean?) {
        viewModelScope.launch {
            _uiState.update { it.copy(filterSafety = isSafe) }
            try {
                if (isSafe != null) {
                    columnRepository.getColumnsBySafety(isSafe).collect { columns ->
                        _uiState.update { it.copy(columns = columns) }
                    }
                } else {
                    loadAllColumns()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectColumn(column: ColumnDesign) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedColumn = column,
                    designEquations = columnCalculator.createDesignEquations(column)
                )
            }
        }
    }

    fun deleteColumn(id: Int) {
        viewModelScope.launch {
            try {
                columnRepository.deleteColumnById(id)
                _uiState.update { it.copy(selectedColumn = null) }
                loadAllColumns()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun saveColumn(column: ColumnDesign) {
        viewModelScope.launch {
            try {
                if (column.id == 0) {
                    columnRepository.insertColumn(column)
                } else {
                    columnRepository.updateColumn(column)
                }
                loadAllColumns()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun performDesignCalculation(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        axialLoadKN: Double,
        bendingMomentKNM: Double,
        concreteGrade: String,
        steelGrade: String,
        codeType: CodeType,
        columnType: ColumnType,
        coverMM: Int = 40,
        columnName: String = "عمود جديد"
    ) {
        viewModelScope.launch {
            try {
                // حساب النسبة الرشاقة
                val slendernessRatio = columnCalculator.calculateSlendernessRatio(
                    heightM,
                    if (columnType == ColumnType.CIRCULAR) widthM else minOf(lengthM, widthM)
                )

                // حساب معامل التخفيض
                val reductionFactor = when (codeType) {
                    CodeType.EGYPTIAN -> columnCalculator.calculateReductionFactorEgyptian(
                        slendernessRatio,
                        2.0
                    )
                    CodeType.AMERICAN -> columnCalculator.calculateReductionFactorACI(
                        slendernessRatio,
                        2.0
                    )
                    CodeType.SAUDI -> columnCalculator.calculateReductionFactorEgyptian(
                        slendernessRatio,
                        2.0
                    )
                }

                // حساب مساحة الفولاذ المطلوبة
                val requiredSteelArea = columnCalculator.calculateRequiredSteelArea(
                    axialLoadKN,
                    bendingMomentKNM,
                    concreteGrade,
                    steelGrade,
                    codeType
                )

                // اختيار قطر التسليح
                val (steelDia, spacing) = columnCalculator.selectSteelDiameter(requiredSteelArea)

                // حساب التحمل
                val (concreteCap, steelCap, totalCap) = columnCalculator.calculateColumnCapacity(
                    lengthM,
                    widthM,
                    heightM,
                    requiredSteelArea,
                    concreteGrade,
                    steelGrade,
                    codeType
                )

                // معامل الأمان
                val safetyFactor = columnCalculator.calculateSafetyFactor(axialLoadKN, totalCap)
                val isSafe = safetyFactor >= 1.5

                // حساب التكاليف
                val concreteVolume = lengthM * widthM * heightM
                val concreteCost = costCalculator.calculateConcreteCost(concreteVolume, 500.0)
                val steelWeight = (requiredSteelArea / 1_000_000) * heightM * 7.85
                val steelCost = costCalculator.calculateSteelCost(
                    requiredSteelArea,
                    heightM,
                    costPerTon = 5000.0
                )
                val totalCost = costCalculator.calculateTotalCost(concreteCost, steelCost)

                // إنشاء كائن التصميم
                val designedColumn = ColumnDesign(
                    columnName = columnName,
                    lengthM = lengthM,
                    widthM = widthM,
                    heightM = heightM,
                    axialLoadKN = axialLoadKN,
                    bendingMomentKNM = bendingMomentKNM,
                    concreteGrade = concreteGrade,
                    steelGrade = steelGrade,
                    codeType = codeType,
                    columnType = columnType,
                    mainSteelAreaMM2 = requiredSteelArea,
                    mainSteelDiaMM = steelDia,
                    stirrupsSpacingMM = spacing,
                    concreteCapacityKN = concreteCap,
                    steelCapacityKN = steelCap,
                    totalCapacityKN = totalCap,
                    safetyFactor = safetyFactor,
                    isSafe = isSafe,
                    slendernessRatio = slendernessRatio,
                    reductionFactor = reductionFactor,
                    mainSteelPercentage = (requiredSteelArea / (lengthM * widthM * 1_000_000)) * 100,
                    totalCost = totalCost,
                    equationsUsed = columnCalculator.createDesignEquations(
                        ColumnDesign(
                            columnName = columnName,
                            lengthM = lengthM,
                            widthM = widthM,
                            heightM = heightM,
                            axialLoadKN = axialLoadKN,
                            bendingMomentKNM = bendingMomentKNM,
                            concreteGrade = concreteGrade,
                            steelGrade = steelGrade,
                            codeType = codeType,
                            columnType = columnType,
                            mainSteelAreaMM2 = requiredSteelArea,
                            safetyFactor = safetyFactor,
                            slendernessRatio = slendernessRatio,
                            reductionFactor = reductionFactor,
                            mainSteelPercentage = (requiredSteelArea / (lengthM * widthM * 1_000_000)) * 100
                        )
                    )
                )

                saveColumn(designedColumn)
                selectColumn(designedColumn)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "خطأ في الحساب: ${e.message}") }
            }
        }
    }

    fun toggleCalculationDetails() {
        _uiState.update { it.copy(showCalculationDetails = !it.showCalculationDetails) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}