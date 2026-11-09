package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    // ── Error message ──
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

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
        _errorMessage.value = null
        _mixResult.value = null
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
            _errorMessage.value = "Invalid input / مدخلات غير صالحة: ${e.message}"
        } catch (e: ArithmeticException) {
            _errorMessage.value = "Arithmetic error / خطأ حسابي: ${e.message}"
        } catch (e: Exception) {
            _errorMessage.value = "Calculation error / خطأ في الحساب: ${e.message}"
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
            _errorMessage.value = "Invalid input / مدخلات غير صالحة: ${e.message}"
        } catch (e: Exception) {
            _errorMessage.value = "Calculation error / خطأ في الحساب: ${e.message}"
        }
    }

    // Quick design for single grade
    fun quickDesignGrade(grade: Int, exposure: Exposure, cementType: CementType) {
        try {
            _mixResult.value = ConcreteMixDesigner.quickDesign(grade, exposure, cementType)
        } catch (e: IllegalArgumentException) {
            _errorMessage.value = "Invalid input / مدخلات غير صالحة: ${e.message}"
        } catch (e: Exception) {
            _errorMessage.value = "Calculation error / خطأ في الحساب: ${e.message}"
        }
    }

    fun clearResults() {
        _mixResult.value = null
        _gradeResults.value = emptyList()
        _errorMessage.value = null
    }

    fun saveDesign(projectId: Long, name: String) {
        val res = _mixResult.value ?: return
        viewModelScope.launch {
            repository.saveConcreteMixDesign(projectId, name, res)
        }
    }

    /** Export Concrete Mix Design report to PDF with 3-view drawings. */
    fun exportToPdf(context: android.content.Context, slump: Double = 100.0, onComplete: (java.io.File?) -> Unit = {}) {
        val res = _mixResult.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val drawingBitmap = com.civileg.app.utils.PdfDrawingGenerator.generateConcreteMixDrawing(
                    targetFc = res.targetStrength,
                    cement = res.cementContent,
                    water = res.waterContent,
                    fineAgg = res.fineAggContent,
                    coarseAgg = res.coarseAggContent,
                    wcRatio = res.waterCementRatio,
                    slump = slump,
                    isSafe = true
                )

                val inputsMap = mapOf(
                    "Target Strength (f_c')" to "${res.targetStrength} MPa",
                    "Required Strength (f_cr')" to "${"%.1f".format(res.requiredStrength)} MPa",
                    "Cement Type" to res.cementType,
                    "Exposure" to res.exposure,
                    "W/C Ratio" to "${"%.2f".format(res.waterCementRatio)}"
                )

                val resultsMap = mapOf(
                    "Cement Content" to "${"%.1f".format(res.cementContent)} kg/m³",
                    "Water Content" to "${"%.1f".format(res.waterContent)} kg/m³",
                    "Fine Aggregate" to "${"%.1f".format(res.fineAggContent)} kg/m³",
                    "Coarse Aggregate" to "${"%.1f".format(res.coarseAggContent)} kg/m³",
                    "Mix Ratio (C:S:G)" to res.mixRatio,
                    "Unit Weight" to "${"%.0f".format(res.unitWeight)} kg/m³"
                )

                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
                directory.mkdirs()
                val file = java.io.File(directory, "Concrete_Mix_Report_${System.currentTimeMillis()}.pdf")

                val bundle = com.civileg.app.utils.ExportManager(context).exportReportAndDxf(
                    titleAr = "تقرير تصميم الخلطة الخرسانية",
                    titleEn = "Concrete Mix Design Report",
                    subtitle = "Proportioning & Volumetric Yield Analysis",
                    designType = "Concrete Mix",
                    inputs = inputsMap,
                    results = resultsMap,
                    isSafe = true,
                    drawingBitmap = drawingBitmap,
                    outputPath = file.absolutePath,
                    openPdfOnComplete = true
                )
                onComplete(bundle.pdfFile)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            }
        }
    }
}
