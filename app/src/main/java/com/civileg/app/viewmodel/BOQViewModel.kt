package com.civileg.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.CalculateElementBoq
import com.civileg.app.utils.EstimationEngine
import com.civileg.app.db.Design
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
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
     * حساب كميات من كيان التصميم المحفوظ في قاعدة البيانات
     * يستخدم القيم المجمعة المخزنة (concreteVolume, steelWeight) إذا لم يتوفر inputData تفصيلي،
     * أو يحلل inputData JSON لاستخراج المعلمات التفصيلية إذا توفر.
     */
    fun calculateDesignBoq(design: Design, prices: MaterialPrices = MaterialPrices()) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = generateBoqFromDesign(design, prices)
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
     * توليد عناصر BOQ من كيان التصميم
     * يحاول أولاً تحليل inputData JSON، وإذا كان فارغاً يعتمد على القيم المجمعة.
     */
    private fun generateBoqFromDesign(design: Design, prices: MaterialPrices): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        val typeLabel = design.type.name

        // محاولة تحليل inputData التفصيلي
        val inputJson = try { JSONObject(design.inputData) } catch (_: Exception) { null }
        val resultsJson = try { JSONObject(design.results) } catch (_: Exception) { null }

        // إذا توفرت بيانات تفصيلية، استخدم CalculateElementBoq
        if (inputJson != null && inputJson.length() > 2) {
            val detailedItems = calculateFromDetailedInput(design.type, inputJson, resultsJson, prices)
            if (detailedItems.isNotEmpty()) return detailedItems
        }

        // خطة بديلة: استخدم القيم المجمعة المخزنة
        if (design.concreteVolume > 0) {
            items += BoqItem(
                itemId = "${typeLabel}_CONC_001",
                description = "${design.name} — Concrete",
                category = BoqCategory.CONCRETE,
                unit = "m³",
                quantity = design.concreteVolume,
                unitPrice = prices.concretePerM3
            )
        }
        if (design.steelWeight > 0) {
            items += BoqItem(
                itemId = "${typeLabel}_REINF_001",
                description = "${design.name} — Reinforcement Steel",
                category = BoqCategory.REINFORCEMENT,
                unit = "ton",
                quantity = design.steelWeight,
                unitPrice = prices.steelPerTon
            )
        }

        // تقدير الشدة (Formwork) بناءً على نوع العنصر
        val formworkArea = estimateFormwork(design)
        if (formworkArea > 0) {
            items += BoqItem(
                itemId = "${typeLabel}_FORM_001",
                description = "${design.name} — Formwork",
                category = BoqCategory.FORMWORK,
                unit = "m²",
                quantity = formworkArea,
                unitPrice = prices.formworkPerM2
            )
        }

        return items
    }

    /**
     * تقدير مساحة الشدة بناءً على نوع العنصر والقيم المخزنة
     */
    private fun estimateFormwork(design: Design): Double {
        // تقدير تقريبي: مساحة الشدة ≈ 2-6 × حجم الخرسانة (حسب العنصر)
        val factor = when (design.type) {
            com.civileg.app.db.DesignType.SLAB -> 1.0 / 0.15  // مساحة = حجم / سماكة تقريبية
            com.civileg.app.db.DesignType.COLUMN -> 4.0 * (design.concreteVolume / 0.3).coerceAtLeast(1.0)
            com.civileg.app.db.DesignType.BEAM -> 3.0 * (design.concreteVolume / 0.25).coerceAtLeast(1.0)
            com.civileg.app.db.DesignType.FOOTING -> 5.0 * (design.concreteVolume / 0.5).coerceAtLeast(1.0)
            com.civileg.app.db.DesignType.STAIRCASE -> 2.5 * (design.concreteVolume / 0.15).coerceAtLeast(1.0)
            com.civileg.app.db.DesignType.WATER_TANK -> 4.0 * (design.concreteVolume / 0.3).coerceAtLeast(1.0)
            com.civileg.app.db.DesignType.RETAINING_WALL -> 3.0 * (design.concreteVolume / 0.3).coerceAtLeast(1.0)
            else -> 3.0
        }
        return if (design.concreteVolume > 0) {
            (design.concreteVolume * factor).coerceIn(0.1, 10000.0)
        } else 0.0
    }

    /**
     * محاولة حساب BOQ تفصيلي من inputData JSON
     */
    private fun calculateFromDetailedInput(
        type: com.civileg.app.db.DesignType,
        input: JSONObject,
        results: JSONObject?,
        prices: MaterialPrices
    ): List<BoqItem> {
        return try {
            when (type) {
                com.civileg.app.db.DesignType.SLAB -> {
                    val spanX = input.optDouble("spanX", input.optDouble("lx", 0.0))
                    val spanY = input.optDouble("spanY", input.optDouble("ly", 0.0))
                    val thickness = input.optDouble("thickness", input.optDouble("h", 0.0))
                    val mainDia = input.optDouble("mainDia", input.optDouble("d1", 0.0))
                    val mainSpacing = input.optDouble("mainSpacing", input.optDouble("s1", 0.0))
                    val distDia = input.optDouble("distDia", input.optDouble("d2", 12.0))
                    val distSpacing = input.optDouble("distSpacing", input.optDouble("s2", 200.0))
                    if (spanX > 0 && spanY > 0 && thickness > 0 && mainDia > 0) {
                        calculateElementBoq.calculateSlabBoq(
                            spanX, spanY, thickness, mainDia, mainSpacing,
                            distDia, distSpacing, 25.0, prices
                        )
                    } else emptyList()
                }
                com.civileg.app.db.DesignType.BEAM -> {
                    val w = input.optDouble("width", input.optDouble("b", 0.0))
                    val d = input.optDouble("depth", input.optDouble("h", 0.0))
                    val span = input.optDouble("span", input.optDouble("L", 0.0))
                    val mainDia = input.optDouble("mainDia", input.optDouble("d1", 0.0))
                    val mainBars = input.optInt("mainBars", input.optInt("n1", 0))
                    val astRequired = input.optDouble("astRequired", input.optDouble("ast", 0.0))
                    val astProvided = input.optDouble("astProvided", astRequired)
                    val stirDia = input.optDouble("stirrupDia", input.optDouble("d2", 8.0))
                    val stirSp = input.optDouble("stirrupSpacing", input.optDouble("s2", 200.0))
                    if (w > 0 && d > 0 && span > 0 && mainDia > 0) {
                        calculateElementBoq.calculateBeamBoq(
                            w, d, span,
                            ReinforcementResult(
                                astRequired = astRequired,
                                astProvided = astProvided,
                                barDiameter = mainDia,
                                numberOfBars = mainBars,
                                tiesDiameter = 8.0,
                                tiesSpacing = 200.0,
                                isSafe = true,
                                utilizationRatio = 0.5
                            ),
                            ShearReinforcementResult(
                                stirrupDiameter = stirDia,
                                stirrupSpacing = stirSp
                            ),
                            prices
                        )
                    } else emptyList()
                }
                com.civileg.app.db.DesignType.COLUMN -> {
                    val w = input.optDouble("width", input.optDouble("b", 0.0))
                    val d = input.optDouble("depth", input.optDouble("h", 0.0))
                    val h = input.optDouble("height", input.optDouble("L", 0.0))
                    val mainDia = input.optDouble("mainDia", input.optDouble("d1", 0.0))
                    val mainBars = input.optInt("mainBars", input.optInt("n1", 0))
                    val astRequired = input.optDouble("astRequired", input.optDouble("ast", 0.0))
                    val astProvided = input.optDouble("astProvided", astRequired)
                    val tieDia = input.optDouble("tiesDiameter", input.optDouble("d2", 8.0))
                    val tieSp = input.optDouble("tiesSpacing", input.optDouble("s2", 200.0))
                    if (w > 0 && d > 0 && h > 0 && mainDia > 0) {
                        calculateElementBoq.calculateColumnBoq(
                            w, d, h,
                            ReinforcementResult(
                                astRequired = astRequired,
                                astProvided = astProvided,
                                barDiameter = mainDia,
                                numberOfBars = mainBars,
                                tiesDiameter = tieDia,
                                tiesSpacing = tieSp,
                                isSafe = true,
                                utilizationRatio = 0.5
                            ),
                            prices
                        )
                    } else emptyList()
                }
                com.civileg.app.db.DesignType.FOOTING -> {
                    val length = input.optDouble("length", input.optDouble("L", 0.0))
                    val width = input.optDouble("width", input.optDouble("B", 0.0))
                    val thickness = input.optDouble("thickness", input.optDouble("t", 0.0))
                    val fcu = input.optDouble("fcu", input.optDouble("concreteGrade", 25.0))
                    val astX = input.optDouble("astBottomX", input.optDouble("astX", 0.0))
                    val astY = input.optDouble("astBottomY", input.optDouble("astY", 0.0))
                    val rebarDia = input.optDouble("rebarDia", input.optDouble("d1", 12.0))
                    val spacingX = input.optDouble("rebarSpacingX", input.optDouble("s1", 200.0))
                    val spacingY = input.optDouble("rebarSpacingY", input.optDouble("s2", 200.0))
                    val excDepth = input.optDouble("excavationDepth", input.optDouble("excDepth", 0.0))
                    val cover = input.optDouble("cover", 75.0)
                    if (length > 0 && width > 0 && thickness > 0) {
                        calculateElementBoq.calculateFootingBoq(
                            length, width, thickness, fcu,
                            if (astX > 0) astX else 100.0,
                            if (astY > 0) astY else 100.0,
                            rebarDia, spacingX, spacingY,
                            prices, excDepth, cover
                        )
                    } else emptyList()
                }
                com.civileg.app.db.DesignType.STAIRCASE -> {
                    val stairWidth = input.optDouble("stairWidth", input.optDouble("width", 0.0))
                    val totalHeight = input.optDouble("totalHeight", input.optDouble("H", 0.0))
                    val stairLength = input.optDouble("stairLength", input.optDouble("L", 0.0))
                    val slabThickness = input.optDouble("slabThickness", input.optDouble("ts", 0.0))
                    val waistThickness = input.optDouble("waistThickness", input.optDouble("tw", 0.0))
                    val riserHeight = input.optDouble("riserHeight", input.optDouble("R", 0.0))
                    val treadWidth = input.optDouble("treadWidth", input.optDouble("T", 0.0))
                    val mainArea = input.optDouble("astRequired", input.optDouble("ast", 0.0))
                    val mainDia = input.optDouble("mainDia", input.optDouble("d1", 12.0))
                    val numBars = input.optInt("mainBars", input.optInt("n1", 3))
                    val stirDia = input.optDouble("stirrupDia", input.optDouble("d2", 8.0))
                    val stirSp = input.optDouble("stirrupSpacing", input.optDouble("s2", 200.0))
                    if (stairWidth > 0 && totalHeight > 0 && stairLength > 0 && waistThickness > 0) {
                        calculateElementBoq.calculateStairBoq(
                            stairWidth, totalHeight, stairLength, slabThickness, waistThickness,
                            riserHeight, treadWidth, mainArea, mainDia, numBars,
                            stirDia, stirSp, prices
                        )
                    } else emptyList()
                }
                com.civileg.app.db.DesignType.WATER_TANK -> {
                    val tankL = input.optDouble("tankLength", input.optDouble("length", 0.0))
                    val tankW = input.optDouble("tankWidth", input.optDouble("width", 0.0))
                    val tankH = input.optDouble("tankHeight", input.optDouble("height", 0.0))
                    val wallT = input.optDouble("wallThickness", input.optDouble("tw", 0.0))
                    val baseT = input.optDouble("baseThickness", input.optDouble("tb", 0.0))
                    val wallDia = input.optDouble("wallRebarDia", input.optDouble("d1", 12.0))
                    val wallSpH = input.optDouble("wallRebarSpacingH", input.optDouble("s1", 200.0))
                    val wallSpV = input.optDouble("wallRebarSpacingV", input.optDouble("s2", 200.0))
                    val baseDia = input.optDouble("baseRebarDia", input.optDouble("d2", 12.0))
                    val baseSp = input.optDouble("baseRebarSpacing", input.optDouble("s3", 200.0))
                    val excDepth = input.optDouble("excavationDepth", 0.5)
                    if (tankL > 0 && tankW > 0 && tankH > 0 && wallT > 0 && baseT > 0) {
                        calculateElementBoq.calculateTankBoq(
                            tankL, tankW, tankH, wallT, baseT,
                            wallDia, wallSpH, wallSpV,
                            baseDia, baseSp, prices, excDepth
                        )
                    } else emptyList()
                }
                com.civileg.app.db.DesignType.RETAINING_WALL -> {
                    val wallLength = input.optDouble("wallLength", input.optDouble("L", 0.0))
                    val totalHeight = input.optDouble("totalHeight", input.optDouble("H", 0.0))
                    val baseWidth = input.optDouble("baseWidth", input.optDouble("B", 0.0))
                    val baseThickness = input.optDouble("baseThickness", input.optDouble("tb", 0.0))
                    val stemTopT = input.optDouble("stemTopThickness", input.optDouble("tt", 0.0))
                    val stemBotT = input.optDouble("stemBottomThickness", input.optDouble("tb2", 0.0))
                    val mainDia = input.optDouble("mainRebarDia", input.optDouble("d1", 16.0))
                    val vertSp = input.optDouble("verticalRebarSpacing", input.optDouble("s1", 200.0))
                    val horDia = input.optDouble("horizontalRebarDia", input.optDouble("d2", 12.0))
                    val horSp = input.optDouble("horizontalRebarSpacing", input.optDouble("s2", 200.0))
                    val excDepth = input.optDouble("excavationDepth", 0.0)
                    val backfillLen = input.optDouble("backfillLength", input.optDouble("bfl", 0.0))
                    if (wallLength > 0 && totalHeight > 0 && baseWidth > 0 && baseThickness > 0) {
                        calculateElementBoq.calculateRetainingWallBoq(
                            wallLength, totalHeight, baseWidth, baseThickness,
                            stemTopT, stemBotT, mainDia, vertSp,
                            horDia, horSp, prices, excDepth, backfillLen
                        )
                    } else emptyList()
                }
                else -> emptyList() // FRAME_ANALYSIS and other types without BOQ
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

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
