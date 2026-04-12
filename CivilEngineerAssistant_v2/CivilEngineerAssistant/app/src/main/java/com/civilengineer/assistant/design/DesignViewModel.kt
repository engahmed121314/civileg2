package com.civilengineer.assistant.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.utils.EngineeringCalculations
import com.civilengineer.assistant.utils.EngineeringConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel مشترك لجميع شاشات التصميم
 * يدير حالة الإدخال والنتائج والتصدير
 */
@HiltViewModel
class DesignViewModel @Inject constructor() : ViewModel() {

    // ═══════════════════════════════════════════
    // حالة الإعدادات العامة
    // ═══════════════════════════════════════════

    private val _selectedCode = MutableStateFlow(DesignCode.EGYPTIAN)
    val selectedCode: StateFlow<DesignCode> = _selectedCode.asStateFlow()

    private val _selectedConcreteGrade = MutableStateFlow(ConcreteGrade.C25)
    val selectedConcreteGrade: StateFlow<ConcreteGrade> = _selectedConcreteGrade.asStateFlow()

    private val _selectedSteelGrade = MutableStateFlow(SteelGrade.ST_360)
    val selectedSteelGrade: StateFlow<SteelGrade> = _selectedSteelGrade.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(Currency.EGP)
    val selectedCurrency: StateFlow<Currency> = _selectedCurrency.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun updateCode(code: DesignCode) { _selectedCode.value = code }
    fun updateConcreteGrade(grade: ConcreteGrade) { _selectedConcreteGrade.value = grade }
    fun updateSteelGrade(grade: SteelGrade) { _selectedSteelGrade.value = grade }
    fun updateCurrency(currency: Currency) { _selectedCurrency.value = currency }

    // ═══════════════════════════════════════════
    // نتائج تصميم العمود
    // ═══════════════════════════════════════════

    private val _columnResult = MutableStateFlow<EngineeringCalculations.ColumnDesignResult?>(null)
    val columnResult: StateFlow<EngineeringCalculations.ColumnDesignResult?> = _columnResult.asStateFlow()

    fun designColumn(
        Pu: Double, Mu: Double, b: Double, h: Double, length: Double,
        columnType: ColumnType = ColumnType.SHORT_RECTANGULAR
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _columnResult.value = EngineeringCalculations.columnDesign(
                Pu = Pu, Mu = Mu, b = b, h = h, length = length,
                fcu = _selectedConcreteGrade.value.fcu,
                fy = _selectedSteelGrade.value.fy,
                code = _selectedCode.value,
                columnType = columnType
            )
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════
    // نتائج تصميم البلاطة
    // ═══════════════════════════════════════════

    private val _slabResult = MutableStateFlow<EngineeringCalculations.SlabDesignResult?>(null)
    val slabResult: StateFlow<EngineeringCalculations.SlabDesignResult?> = _slabResult.asStateFlow()

    fun designSlab(
        spanX: Double, spanY: Double, deadLoad: Double, liveLoad: Double,
        slabType: SlabType = SlabType.SOLID_TWO_WAY
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _slabResult.value = EngineeringCalculations.solidSlabDesign(
                spanX = spanX, spanY = spanY, deadLoad = deadLoad, liveLoad = liveLoad,
                fcu = _selectedConcreteGrade.value.fcu,
                fy = _selectedSteelGrade.value.fy,
                code = _selectedCode.value,
                slabType = slabType
            )
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════
    // نتائج تصميم القاعدة
    // ═══════════════════════════════════════════

    private val _footingResult = MutableStateFlow<EngineeringCalculations.FootingDesignResult?>(null)
    val footingResult: StateFlow<EngineeringCalculations.FootingDesignResult?> = _footingResult.asStateFlow()

    fun designFooting(
        Pu: Double, Mu: Double, colB: Double, colH: Double,
        soilBearing: Double, depth: Double = 1500.0
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _footingResult.value = EngineeringCalculations.isolatedFootingDesign(
                Pu = Pu, Mu = Mu, columnB = colB, columnH = colH,
                soilBearing = soilBearing,
                fcu = _selectedConcreteGrade.value.fcu,
                fy = _selectedSteelGrade.value.fy,
                code = _selectedCode.value,
                depthBelowGround = depth
            )
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════
    // نتائج الانحناء والقص (للكمرات)
    // ═══════════════════════════════════════════

    private val _flexureResult = MutableStateFlow<EngineeringCalculations.FlexureResult?>(null)
    val flexureResult: StateFlow<EngineeringCalculations.FlexureResult?> = _flexureResult.asStateFlow()

    private val _shearResult = MutableStateFlow<EngineeringCalculations.ShearDesignResult?>(null)
    val shearResult: StateFlow<EngineeringCalculations.ShearDesignResult?> = _shearResult.asStateFlow()

    fun designBeam(Mu: Double, Vu: Double, b: Double, d: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            _flexureResult.value = EngineeringCalculations.flexureDesign(
                Mu = Mu, b = b, d = d,
                fcu = _selectedConcreteGrade.value.fcu,
                fy = _selectedSteelGrade.value.fy,
                code = _selectedCode.value
            )
            _shearResult.value = EngineeringCalculations.shearDesign(
                Vu = Vu, b = b, d = d,
                fcu = _selectedConcreteGrade.value.fcu,
                fy = _selectedSteelGrade.value.fy,
                code = _selectedCode.value
            )
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════
    // نتائج الزلازل
    // ═══════════════════════════════════════════

    private val _seismicResult = MutableStateFlow<SeismicResult?>(null)
    val seismicResult: StateFlow<SeismicResult?> = _seismicResult.asStateFlow()

    fun analyzeSeismic(
        totalWeight: Double, buildingHeight: Double,
        storyHeights: List<Double>, storyWeights: List<Double>,
        zone: SeismicZone, importance: ImportanceCategory,
        soilType: SoilType, system: SeismicResistanceSystem
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _seismicResult.value = EngineeringCalculations.equivalentStaticAnalysis(
                totalWeight = totalWeight,
                buildingHeight = buildingHeight,
                storyHeights = storyHeights,
                storyWeights = storyWeights,
                zone = zone,
                importance = importance,
                soilType = soilType,
                resistanceSystem = system,
                code = _selectedCode.value
            )
            _isLoading.value = false
        }
    }

    // ═══════════════════════════════════════════
    // حساب الحصر
    // ═══════════════════════════════════════════

    fun calculateQuantitySurvey(
        concreteVolume: Double,
        steelArea: Double,   // mm²
        length: Double,      // mm
        formworkArea: Double, // m²
        elementType: String
    ): QuantitySurveyResult {
        val steelWeight = steelArea * length / 1e6 * 7850.0 / 1000.0 // kg
        val wasteC = EngineeringConstants.WasteFactors.getConcreteWaste(elementType)
        val wasteS = EngineeringConstants.WasteFactors.getSteelWaste("straight")

        return QuantitySurveyResult(
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            formworkArea = formworkArea,
            wasteFactor = wasteC,
            steelWithWaste = steelWeight * (1 + wasteS),
            concreteWithWaste = concreteVolume * (1 + wasteC)
        )
    }

    // ═══════════════════════════════════════════
    // حساب التكلفة
    // ═══════════════════════════════════════════

    fun calculateCost(quantitySurvey: QuantitySurveyResult): CostResult {
        val prices = EngineeringConstants.getDefaultPrices(_selectedCurrency.value.name)
        val concreteCost = quantitySurvey.concreteWithWaste * prices.concretePerM3
        val steelCost = quantitySurvey.steelWithWaste / 1000.0 * prices.steelPerTon
        val formworkCost = quantitySurvey.formworkArea * prices.formworkPerM2
        val laborCost = quantitySurvey.concreteWithWaste * prices.laborPerM3
        val totalCost = concreteCost + steelCost + formworkCost + laborCost

        return CostResult(
            concreteCost = concreteCost,
            steelCost = steelCost,
            formworkCost = formworkCost,
            laborCost = laborCost,
            totalCost = totalCost,
            currency = _selectedCurrency.value,
            breakdown = listOf(
                CostItem("خرسانة", quantitySurvey.concreteWithWaste, "م³", prices.concretePerM3, concreteCost),
                CostItem("حديد تسليح", quantitySurvey.steelWithWaste / 1000.0, "طن", prices.steelPerTon, steelCost),
                CostItem("شدات", quantitySurvey.formworkArea, "م²", prices.formworkPerM2, formworkCost),
                CostItem("عمالة", quantitySurvey.concreteWithWaste, "م³", prices.laborPerM3, laborCost)
            )
        )
    }

    // ═══════════════════════════════════════════
    // إعادة تعيين
    // ═══════════════════════════════════════════

    fun resetAll() {
        _columnResult.value = null
        _slabResult.value = null
        _footingResult.value = null
        _flexureResult.value = null
        _shearResult.value = null
        _seismicResult.value = null
    }
}
