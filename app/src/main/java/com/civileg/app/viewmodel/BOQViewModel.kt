package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.CalculateElementBoq
import com.civileg.app.utils.EstimationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BOQViewModel @Inject constructor(
    private val estimationEngine: EstimationEngine,
    private val calculateElementBoq: CalculateElementBoq
) : ViewModel() {

    private val _estimationResult = MutableLiveData<EstimationEngine.EstimationResult?>()
    val estimationResult: LiveData<EstimationEngine.EstimationResult?> = _estimationResult

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ── Element-level BOQ (CalculateElementBoq integration) ──
    private val _elementBoqItems = MutableLiveData<List<BoqItem>>(emptyList())
    val elementBoqItems: LiveData<List<BoqItem>> = _elementBoqItems

    private val _elementBoqTotal = MutableLiveData<Double>(0.0)
    val elementBoqTotal: LiveData<Double> = _elementBoqTotal

    /**
     * حساب كميات عمود من نتائج التصميم
     */
    fun calculateColumnBoq(
        width: Double, depth: Double, height: Double,
        reinforcementResult: ReinforcementResult,
        prices: MaterialPrices = MaterialPrices()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateColumnBoq(
                    width, depth, height, reinforcementResult, prices
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * حساب كميات كمرة من نتائج التصميم
     */
    fun calculateBeamBoq(
        width: Double, depth: Double, span: Double,
        flexureResult: ReinforcementResult,
        shearResult: ShearReinforcementResult,
        prices: MaterialPrices = MaterialPrices()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateBeamBoq(
                    width, depth, span, flexureResult, shearResult, prices
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * حساب كميات بلاطة
     */
    fun calculateSlabBoq(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double = 25.0,
        prices: MaterialPrices = MaterialPrices()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateSlabBoq(
                    spanX, spanY, thickness, mainDia, mainSpacing,
                    distDia, distSpacing, cover, prices
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * حساب كميات قاعدة منفردة
     */
    fun calculateFootingBoq(
        length: Double, width: Double, thickness: Double,
        concreteGrade: Double,
        astBottomX: Double, astBottomY: Double,
        rebarDia: Double, rebarSpacingX: Double, rebarSpacingY: Double,
        prices: MaterialPrices = MaterialPrices(),
        excavationDepth: Double = 0.0, concreteCover: Double = 75.0
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateFootingBoq(
                    length, width, thickness, concreteGrade,
                    astBottomX, astBottomY, rebarDia, rebarSpacingX, rebarSpacingY,
                    prices, excavationDepth, concreteCover
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * حساب كميات سلم
     */
    fun calculateStairBoq(
        stairWidth: Double, totalHeight: Double, stairLength: Double,
        slabThickness: Double, waistThickness: Double,
        riserHeight: Double, treadWidth: Double,
        mainRebarArea: Double, mainRebarDia: Double, numMainBars: Int,
        stirrupDia: Double = 8.0, stirrupSpacing: Double = 200.0,
        prices: MaterialPrices = MaterialPrices()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateStairBoq(
                    stairWidth, totalHeight, stairLength, slabThickness, waistThickness,
                    riserHeight, treadWidth, mainRebarArea, mainRebarDia, numMainBars,
                    stirrupDia, stirrupSpacing, prices
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * حساب كميات خزان مياه
     */
    fun calculateTankBoq(
        tankLength: Double, tankWidth: Double, tankHeight: Double,
        wallThickness: Double, baseThickness: Double,
        wallRebarDia: Double, wallRebarSpacingH: Double, wallRebarSpacingV: Double,
        baseRebarDia: Double, baseRebarSpacing: Double,
        prices: MaterialPrices = MaterialPrices(),
        excavationDepth: Double = 0.5
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateTankBoq(
                    tankLength, tankWidth, tankHeight, wallThickness, baseThickness,
                    wallRebarDia, wallRebarSpacingH, wallRebarSpacingV,
                    baseRebarDia, baseRebarSpacing, prices, excavationDepth
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * حساب كميات حائط ساند
     */
    fun calculateRetainingWallBoq(
        wallLength: Double, totalHeight: Double, baseWidth: Double,
        baseThickness: Double, stemTopThickness: Double, stemBottomThickness: Double,
        mainRebarDia: Double, verticalRebarSpacing: Double,
        horizontalRebarDia: Double = 12.0, horizontalRebarSpacing: Double = 200.0,
        prices: MaterialPrices = MaterialPrices(),
        excavationDepth: Double = 0.0, backfillLength: Double = 0.0
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = calculateElementBoq.calculateRetainingWallBoq(
                    wallLength, totalHeight, baseWidth, baseThickness,
                    stemTopThickness, stemBottomThickness,
                    mainRebarDia, verticalRebarSpacing,
                    horizontalRebarDia, horizontalRebarSpacing,
                    prices, excavationDepth, backfillLength
                )
                _elementBoqItems.value = items
                _elementBoqTotal.value = items.sumOf { it.total }
            } catch (e: Exception) {
                _elementBoqItems.value = emptyList()
                _elementBoqTotal.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Project Estimation (existing functionality) ──

    fun estimateFullProject(
        type: EstimationEngine.FullProjectType,
        area: Double,
        floors: Int,
        hasBasement: Boolean,
        factoryType: EstimationEngine.FactoryStructureType? = null,
        landPrice: Double = 0.0,
        expectedSellingPrice: Double = 0.0,
        currency: String = "EGP"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = estimationEngine.estimateFullProject(
                    type, area, floors, hasBasement, factoryType, landPrice, expectedSellingPrice, currency
                )
                _estimationResult.value = result
            } catch (e: Exception) {
                _estimationResult.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun estimateApartmentFinishingPro(area: Double, currency: String = "EGP") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = estimationEngine.estimateApartmentFinishingPro(area, currency)
                _estimationResult.value = result
            } catch (e: Exception) {
                _estimationResult.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun estimateSpecificItem(name: String, qty: Double, price: Double, currency: String = "EGP") {
        viewModelScope.launch {
            _isLoading.value = true
            _estimationResult.value = estimationEngine.estimateSpecificItem(name, qty, price, currency)
            _isLoading.value = false
        }
    }

    fun clearResult() {
        _estimationResult.value = null
        _elementBoqItems.value = emptyList()
        _elementBoqTotal.value = 0.0
    }
}
