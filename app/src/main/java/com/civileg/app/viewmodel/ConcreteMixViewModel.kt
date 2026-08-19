package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import com.civileg.app.utils.ConcreteMixDesigner
import com.civileg.app.utils.ConcreteMixDesigner.CementType
import com.civileg.app.utils.ConcreteMixDesigner.Exposure
import com.civileg.app.utils.ConcreteMixDesigner.STANDARD_GRADES
import com.civileg.app.utils.ConcreteMixDesigner.MixResult
import com.civileg.app.utils.ConcreteMixDesigner.MixInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConcreteMixViewModel : ViewModel() {

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
    }

    // Quick design for all standard grades
    fun designAllGrades(exposure: Exposure, cementType: CementType) {
        val results = STANDARD_GRADES.map { grade ->
            grade to ConcreteMixDesigner.quickDesign(grade, exposure, cementType)
        }
        _gradeResults.value = results
    }

    // Quick design for single grade
    fun quickDesignGrade(grade: Int, exposure: Exposure, cementType: CementType) {
        _mixResult.value = ConcreteMixDesigner.quickDesign(grade, exposure, cementType)
    }

    fun clearResults() {
        _mixResult.value = null
        _gradeResults.value = emptyList()
    }
}
