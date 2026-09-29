package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.utils.ConcreteMixDesigner
import com.civileg.app.utils.ConcreteMixDesigner.CementType
import com.civileg.app.utils.ConcreteMixDesigner.Exposure
import com.civileg.app.utils.ConcreteMixDesigner.STANDARD_GRADES
import com.civileg.app.utils.ConcreteMixDesigner.MixResult
import com.civileg.app.utils.ConcreteMixDesigner.MixInput
import androidx.lifecycle.viewModelScope
import com.civileg.app.db.DesignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConcreteMixViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    // ── Tab state ──
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) { _selectedTab.value = index }

    // ── Results ──
    private val _mixResult = MutableStateFlow<MixResult?>(null)
    val mixResult: StateFlow<MixResult?> = _mixResult.asStateFlow()

    // ── Quick grade results ──
    private val _gradeResults = MutableStateFlow<List<Pair<Int, MixResult>>>(emptyList())
    val gradeResults: StateFlow<List<Pair<Int, MixResult>>> = _gradeResults.asStateFlow()

    // Full design
    fun designMix(
        targetStrength: Double,
        standardDeviation: Double,
        maxAggSize: Double,
        slump: Double,
        exposure: Exposure,
        cementType: CementType,
        fm: Double,
        hasAdmixture: Boolean,
        admixtureType: String,
        admixtureDosage: Double,
        isPumpable: Boolean,
        weatherCondition: String,
        useNoTestData: Boolean,
        fineAggSG: Double,
        coarseAggSG: Double
    ) {
        try {
            InputGuard.positive("targetStrength", targetStrength)
            InputGuard.positive("standardDeviation", standardDeviation)
            InputGuard.positive("maxAggSize", maxAggSize)
            InputGuard.positive("slump", slump)
            InputGuard.positive("fm", fm)
            InputGuard.positive("fineAggSG", fineAggSG)
            InputGuard.positive("coarseAggSG", coarseAggSG)

            val input = MixInput(
                targetStrength = targetStrength,
                standardDeviation = standardDeviation,
                maxAggregateSize = maxAggSize,
                slump = slump,
                exposure = exposure,
                cementType = cementType,
                finenessModulus = fm,
                hasAdmixture = hasAdmixture,
                admixtureType = admixtureType,
                admixtureDosage = admixtureDosage,
                isPumpable = isPumpable,
                weatherCondition = weatherCondition,
                useNoTestData = useNoTestData,
                fineAggSG = fineAggSG,
                coarseAggSG = coarseAggSG
            )
            _mixResult.value = ConcreteMixDesigner.designMix(input)
        } catch (e: IllegalArgumentException) {
        } catch (e: ArithmeticException) {
        } catch (e: Exception) {
        }
    }

    // Quick design for all standard grades
    fun designAllGrades(exposure: Exposure, cementType: CementType) {
        try {
            val results = STANDARD_GRADES.map { grade ->
                grade to ConcreteMixDesigner.quickDesign(grade, exposure, cementType)
            }
            _gradeResults.value = results
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
        }
    }

    // Quick design for single grade
    fun quickDesignGrade(grade: Int, exposure: Exposure, cementType: CementType) {
        try {
            _mixResult.value = ConcreteMixDesigner.quickDesign(grade, exposure, cementType)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
        }
    }

    fun clearResults() {
        _mixResult.value = null
        _gradeResults.value = emptyList()
    }

    fun saveDesign(projectId: Long, name: String) {
        val res = _mixResult.value ?: return
        viewModelScope.launch {
            repository.saveConcreteMixDesign(projectId, name, res)
        }
    }
}
