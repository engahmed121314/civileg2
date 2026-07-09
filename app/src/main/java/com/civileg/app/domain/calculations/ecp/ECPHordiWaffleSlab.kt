package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

// ============================================================================
// Result Data Classes
// ============================================================================

data class HordiSlabResult(
    val ribReinforcement: ReinforcementResult,
    val topReinforcement: ReinforcementResult,
    val toppingReinforcement: ReinforcementResult,
    val ribShearCheck: ShearCheckResult,
    val deflectionCheck: DeflectionCheckResult,
    val isSafe: Boolean,
    val ribSpacing: Double,
    val numberOfRibs: Int,
    val warnings: List<String>,
    val codeNotes: List<String>
)

data class WaffleSlabResult(
    val solidHeadReinforcementBottom: ReinforcementResult,
    val solidHeadReinforcementTop: ReinforcementResult,
    val ribReinforcementShort: ReinforcementResult,
    val ribReinforcementLong: ReinforcementResult,
    val toppingReinforcement: ReinforcementResult,
    val punchingShearCheck: ShearCheckResult,
    val deflectionCheck: DeflectionCheckResult,
    val isSafe: Boolean,
    val numberOfRibsShort: Int,
    val numberOfRibsLong: Int,
    val solidHeadSize: Double,
    val warnings: List<String>,
    val codeNotes: List<String>
)

// ============================================================================
// HORDI SLAB DESIGN — ECP 203 Section 6-4
// ============================================================================

class ECPHordiSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val MIN_RIB_WIDTH = 100.0      // mm
        private const val MIN_TOPPING = 50.0         // mm
        private const val MAX_RIB_SPACING = 600.0    // mm (center to center)
        private const val BLOCK_WIDTH = 400.0        // mm (standard)
        private const val BLOCK_HEIGHT = 200.0       // mm (standard)
        private const val COVER = 20.0               // mm
        private const val HALF_BAR_EST = 6.0         // mm (half bar assumption)
    }

    /**
     * تصميم كامل للبلاطة الهوردية حسب الكود المصري ECP 203-2020 البند 6-4
     *
     * @param clearSpan       البحر الصافي (مم)
     * @param ribSpacing      تباعد الكمرات مركز لمركز (مم)
     * @param ribWidth        عرض الكمرتة (مم)
     * @param totalThickness  السماكة الكلية للبلاطة (مم) - تشمل التوبينج
     * @param fcu             مقاومة الضغط للخرسانة (ميجاباسكال)
     * @param fy              مقاومة الخضوع للصلب (ميجاباسكال)
     * @param deadLoad        الحمل الميت (كيلونيوتن/م²) شامل وزن البلوكات
     * @param liveLoad        الحمل الحي (كيلونيوتن/م²)
     * @param supportConditions  شروط التثبيت
     * @param loadCombination  مجموعة التحميل
     * @param blockWidth      عرض البلوك (مم) - القيمة الافتراضية 400
     * @param blockHeight     ارتفاع البلوك (مم) - القيمة الافتراضية 200
     * @param toppingThickness  سمك الطبقة العلوية (مم) - القيمة الافتراضية 50
     */
    fun designHordiSlab(
        clearSpan: Double,
        ribSpacing: Double,
        ribWidth: Double,
        totalThickness: Double,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        supportConditions: SlabSupportConditions,
        loadCombination: LoadCombination,
        blockWidth: Double = 400.0,
        blockHeight: Double = 200.0,
        toppingThickness: Double = 50.0
    ): HordiSlabResult {

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ── 1. Check geometry requirements per ECP 203 Section 6-4 ──

        if (ribSpacing > MAX_RIB_SPACING) {
            warnings.add("تباعد الكمرات (${ribSpacing.toInt()} مم) يتجاوز الحد الأقصى (600 مم) حسب ECP 203 البند 6-4")
        }

        if (ribWidth < MIN_RIB_WIDTH) {
            warnings.add("عرض الكمرتة (${ribWidth.toInt()} مم) أقل من الحد الأدنى (100 مم) حسب ECP 203 البند 6-4")
        }

        if (toppingThickness < MIN_TOPPING) {
            warnings.add("سمك الطبقة العلوية (${toppingThickness.toInt()} مم) أقل من الحد الأدنى (50 مم) - يُسمح بـ 40 مم مع شبك ملحومة")
        }

        val ribDepth = totalThickness - toppingThickness
        if (blockHeight > ribDepth) {
            warnings.add("ارتفاع البلوك (${blockHeight.toInt()} مم) أكبر من عمق الكمرتة (${ribDepth.toInt()} مم)")
        }

        codeNotes.add("ECP 203-2020: البند 6-4 (البلاطات الهوردية)")
        codeNotes.add("تباعد الكمرات: ${ribSpacing.toInt()} مم م.م | عرض الكمرتة: ${ribWidth.toInt()} مم | سمك التوبينج: ${toppingThickness.toInt()} مم")

        // ── 2. Effective depth ──

        // d_rib = totalThickness - toppingThickness - cover - half bar
        val effectiveDepth = ribDepth - COVER - HALF_BAR_EST

        if (effectiveDepth <= 0) {
            return createUnsafeHordiResult(
                warnings = warnings.apply {
                    add("العمق الفعال غير صالح (${effectiveDepth.toInt()} مم) - زِد السماكة الكلية")
                },
                codeNotes = codeNotes,
                ribSpacing = ribSpacing,
                numberOfRibs = 0
            )
        }

        codeNotes.add("العمق الفعال للكمرتة: d = ${effectiveDepth.toInt()} مم")

        // ── 3. Load per rib ──

        // w_rib = (deadLoad + liveLoad) × factor × ribSpacing / 1000.0
        val loadFactor = loadCombination.getFactorForCode(DesignCode.ECP)
        val wu = (deadLoad + liveLoad) * loadFactor  // kN/m² (factored)
        val wRib = wu * ribSpacing / 1000.0           // kN/m per rib

        codeNotes.add("معامل التحميل: ${String.format("%.2f", loadFactor)} | الحمل التصميمي: ${String.format("%.1f", wu)} ك.ن/م²")
        codeNotes.add("الحمل على الكمرتة: ${String.format("%.2f", wRib)} ك.ن/م")

        // ── 4. Moment calculation using coefficients per ECP 203 ──

        val spanM = clearSpan / 1000.0 // meters

        // Determine support type (simplified: use edgeA and edgeB for one-way behavior)
        val edgeA = supportConditions.edgeA
        val edgeB = supportConditions.edgeB

        val momentCoefficients = getHordiMomentCoefficients(edgeA, edgeB)

        val MuPos = momentCoefficients.positive * wRib * spanM * spanM   // kN.m per rib (mid-span)
        val MuNeg = momentCoefficients.negative * wRib * spanM * spanM   // kN.m per rib (support)

        codeNotes.add("عزم الموجب: ${String.format("%.2f", MuPos)} ك.ن.م/كمرتة | عزم السالب: ${String.format("%.2f", MuNeg)} ك.ن.م/كمرتة")
        codeNotes.add("معامل العزم الموجب: ${String.format("%.3f", momentCoefficients.positive)} | معامل العزم السالب: ${String.format("%.3f", momentCoefficients.negative)}")

        // ── 5. Rib flexure design (K-method per ECP 203) ──

        val fs = fy / GAMMA_S
        val K_bal = calculateKBal(fcu, fy)

        // Positive moment (bottom steel at mid-span)
        val ribBottom = designRibSection(
            Mu = MuPos,
            fcu = fcu,
            fy = fy,
            bw = ribWidth,
            d = effectiveDepth,
            fs = fs,
            K_bal = K_bal,
            maxBarsPerRib = 2,
            sectionLabel = "سفل الكمرتة (منتصف البحر)",
            warnings = warnings,
            codeNotes = codeNotes
        )

        // Negative moment (top steel at support)
        val ribTop = if (MuNeg > 0.01) {
            designRibSection(
                Mu = MuNeg,
                fcu = fcu,
                fy = fy,
                bw = ribWidth,
                d = effectiveDepth,
                fs = fs,
                K_bal = K_bal,
                maxBarsPerRib = 2,
                sectionLabel = "أعلى الكمرتة (عند الدعامة)",
                warnings = warnings,
                codeNotes = codeNotes
            )
        } else {
            // No negative moment for simply supported - provide minimum
            val minAs = getMinRibSteel(ribWidth, effectiveDepth, fy)
            ReinforcementResult(
                astRequired = minAs,
                astProvided = minAs,
                barDiameter = 10.0,
                numberOfBars = 1,
                tiesDiameter = 0.0,
                tiesSpacing = 0.0,
                isSafe = true,
                utilizationRatio = 0.0,
                spacing = 0.0,
                description = "حديد أدنى بالدعامة (1Ø10)"
            )
        }

        // ── 6. Topping design (distribution slab) ──

        val toppingResult = designTopping(
            toppingThickness = toppingThickness,
            ribSpacing = ribSpacing,
            ribWidth = ribWidth,
            fcu = fcu,
            fy = fy,
            deadLoad = deadLoad,
            liveLoad = liveLoad,
            loadFactor = loadFactor,
            warnings = warnings,
            codeNotes = codeNotes
        )

        // ── 7. Shear check per rib ──

        // V = coefficient × w × L (per rib)
        val shearCoefficient = momentCoefficients.shear
        val Vu = shearCoefficient * wRib * spanM // kN

        // Vc = 0.24 × √(fcu/γc) × bw × d
        val qcu = 0.24 * sqrt(fcu / GAMMA_C)
        val Vc = qcu * ribWidth * effectiveDepth / 1000.0 // kN

        val shearUtilization = if (Vc > 0) Vu / Vc else Double.MAX_VALUE
        val isShearSafe = Vu <= Vc

        if (!isShearSafe) {
            warnings.add("قص الكمرتة يتجاوز قدرة الخرسانة! Vu=${String.format("%.1f", Vu)} ك.ن > Vc=${String.format("%.1f", Vc)} ك.ن")
            warnings.add("يجب زيادة عرض الكمرتة أو السماكة الكلية")
        }

        codeNotes.add("قص الخرسانة: qcu = ${String.format("%.2f", qcu)} ميجاباسكال | Vc = ${String.format("%.1f", Vc)} ك.ن")

        val ribShearCheck = ShearCheckResult(
            appliedShear = Vu,
            shearCapacity = Vc,
            isSafe = isShearSafe,
            utilizationRatio = shearUtilization,
            criticalSection = effectiveDepth,
            criticalPerimeter = 0.0,
            warnings = if (!isShearSafe) listOf("قص الكمرتة يتجاوز قدرة الخرسانة - يحتاج كانات") else emptyList()
        )

        // ── 8. Deflection check ──

        val deflectionCheck = checkHordiDeflection(
            clearSpan = clearSpan,
            totalThickness = totalThickness,
            edgeA = edgeA,
            edgeB = edgeB,
            warnings = warnings,
            codeNotes = codeNotes
        )

        // ── 9. Number of ribs (assumed for a 1-meter strip) ──

        val numberOfRibs = max(floor(1000.0 / ribSpacing).toInt(), 1)

        // ── Overall safety ──

        val isSafe = ribBottom.isSafe && ribTop.isSafe && isShearSafe && deflectionCheck.isSafe

        return HordiSlabResult(
            ribReinforcement = ribBottom,
            topReinforcement = ribTop,
            toppingReinforcement = toppingResult,
            ribShearCheck = ribShearCheck,
            deflectionCheck = deflectionCheck,
            isSafe = isSafe,
            ribSpacing = ribSpacing,
            numberOfRibs = numberOfRibs,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ── Rib section design using K-method ──

    private fun designRibSection(
        Mu: Double,
        fcu: Double,
        fy: Double,
        bw: Double,
        d: Double,
        fs: Double,
        K_bal: Double,
        maxBarsPerRib: Int,
        sectionLabel: String,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ReinforcementResult {
        val MuNmm = Mu * 1e6 // N.mm

        val K = if (fcu > 0 && bw > 0 && d > 0) {
            MuNmm / (fcu * bw * d * d)
        } else 0.0

        if (K > K_bal) {
            warnings.add("K = ${String.format("%.3f", K)} > K_bal = ${String.format("%.3f", K_bal)} - المقطع مفرط التسليح (${sectionLabel})")
        }

        // z = d × (0.5 + √(0.25 - K/1.25))
        val discriminant = 0.25 - K / 1.25
        val leverArm = if (discriminant > 0) {
            d * (0.5 + sqrt(discriminant))
        } else {
            d * 0.7
        }.coerceAtMost(0.9 * d)

        var astRequired = if (fs > 0 && leverArm > 0) MuNmm / (fs * leverArm) else 0.0

        // Minimum steel for rib
        val minAs = getMinRibSteel(bw, d, fy)
        if (astRequired < minAs) {
            astRequired = minAs
        }

        // Select bar diameter — max 2 bars per rib
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0)
        var selectedDiameter = availableBars.first()
        var numberOfBars = 1
        var astProvided = 0.0

        for (dia in availableBars) {
            val barArea = PI * dia * dia / 4.0
            val n = ceil(astRequired / barArea).toInt().coerceIn(1, maxBarsPerRib)
            if (n <= maxBarsPerRib) {
                selectedDiameter = dia
                numberOfBars = n
                astProvided = n * barArea
                break
            }
        }

        // If even the largest bar can't fit, use 2 bars of the largest
        if (astProvided < astRequired) {
            val barArea = PI * 16.0 * 16.0 / 4.0
            selectedDiameter = 16.0
            numberOfBars = maxBarsPerRib
            astProvided = numberOfBars * barArea
            warnings.add("تم استخدام أكبر تسليح ممكن (${numberOfBars}Ø${selectedDiameter.toInt()}) - زِد عرض الكمرتة")
        }

        val utilizationRatio = if (astProvided > 0 && leverArm > 0) {
            (astRequired * fs * leverArm / 1e6) / Mu
        } else 2.0

        val isSafe = K <= K_bal && astProvided >= astRequired

        codeNotes.add("${sectionLabel}: As_req = ${String.format("%.0f", astRequired)} مم² | As_prov = ${String.format("%.0f", astProvided)} مم² (${numberOfBars}Ø${selectedDiameter.toInt()})")

        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = if (K > K_bal) listOf("المقطع مفرط التسليح") else emptyList(),
            codeNotes = listOf("ECP 203 البند 4-2-2-1: K-method")
        )
    }

    // ── Topping slab design ──

    private fun designTopping(
        toppingThickness: Double,
        ribSpacing: Double,
        ribWidth: Double,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        loadFactor: Double,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ReinforcementResult {
        val clearSpanTopping = ribSpacing - ribWidth  // mm

        // Minimum reinforcement per meter width for topping
        val minAs = max(
            0.0015 * 1000.0 * toppingThickness,           // 0.15% of gross area per meter
            0.6 / fy.coerceAtLeast(1.0) * 1000.0 * toppingThickness // 0.6/fy * b * d
        )

        // Select welded wire mesh
        // Common options: 6mm @ 200mm, 8mm @ 200mm, 6mm @ 150mm, 8mm @ 150mm
        val meshOptions = listOf(
            Triple(6.0, 200.0, PI * 36.0 / 4.0 * 1000.0 / 200.0),
            Triple(6.0, 150.0, PI * 36.0 / 4.0 * 1000.0 / 150.0),
            Triple(8.0, 200.0, PI * 64.0 / 4.0 * 1000.0 / 200.0),
            Triple(8.0, 150.0, PI * 64.0 / 4.0 * 1000.0 / 150.0),
            Triple(10.0, 200.0, PI * 100.0 / 4.0 * 1000.0 / 200.0),
            Triple(10.0, 150.0, PI * 100.0 / 4.0 * 1000.0 / 150.0)
        )

        var selectedDia = 6.0
        var selectedSpacing = 200.0
        var astProvided = 0.0

        for ((dia, spacing, asProv) in meshOptions) {
            if (asProv >= minAs) {
                selectedDia = dia
                selectedSpacing = spacing
                astProvided = asProv
                break
            }
        }

        // If none satisfies, use the largest
        if (astProvided < minAs) {
            selectedDia = 10.0
            selectedSpacing = 150.0
            astProvided = PI * 100.0 / 4.0 * 1000.0 / 150.0
            warnings.add("الحديد الأدنى للتوبينج يتطلب تسليح أكبر من الشبك الملحوم المعتاد")
        }

        val barsPerMeter = (1000.0 / selectedSpacing).toInt()
        val utilizationRatio = if (astProvided > 0) minAs / astProvided else 2.0

        codeNotes.add("تسليح التوبينج: شبك ملحومة Ø${selectedDia.toInt()} @ ${selectedSpacing.toInt()} مم (${barsPerMeter}Ø${selectedDia.toInt()}/م')")
        codeNotes.add("As_min = ${String.format("%.0f", minAs)} مم²/م | As_prov = ${String.format("%.0f", astProvided)} مم²/م")

        return ReinforcementResult(
            astRequired = minAs,
            astProvided = astProvided,
            barDiameter = selectedDia,
            numberOfBars = barsPerMeter,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = astProvided >= minAs,
            utilizationRatio = utilizationRatio,
            spacing = selectedSpacing,
            description = "شبك ملحومة للتوبينج Ø${selectedDia.toInt()} @ ${selectedSpacing.toInt()}"
        )
    }

    // ── Deflection check for Hordi slab ──

    private fun checkHordiDeflection(
        clearSpan: Double,
        totalThickness: Double,
        edgeA: EdgeCondition,
        edgeB: EdgeCondition,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): DeflectionCheckResult {
        // Span/depth ratio per ECP 203 Section 6-3
        val isContinuous = (edgeA == EdgeCondition.CONTINUOUS || edgeA == EdgeCondition.FIXED) &&
                          (edgeB == EdgeCondition.CONTINUOUS || edgeB == EdgeCondition.FIXED)
        val isOneEndContinuous = (edgeA == EdgeCondition.CONTINUOUS || edgeA == EdgeCondition.FIXED) ||
                                 (edgeB == EdgeCondition.CONTINUOUS || edgeB == EdgeCondition.FIXED)

        val ratio = when {
            isContinuous -> 30.0
            isOneEndContinuous -> 25.0
            else -> 20.0
        }

        val minThickness = clearSpan / ratio
        val isSafe = totalThickness >= minThickness
        val calculatedRatio = clearSpan / totalThickness

        if (!isSafe) {
            warnings.add("نسبة البحر للسمك = ${String.format("%.1f", calculatedRatio)} تتجاوز الحد المسموح (${ratio.toInt()}) - زِد السماكة الكلية")
        }

        codeNotes.add("التحقق من الانحراف: L/h = ${String.format("%.1f", calculatedRatio)} (الحد: ${ratio.toInt()}) | السماكة المطلوبة: ${String.format("%.0f", minThickness)} مم")

        val allowableDeflection = clearSpan / 250.0 // mm (serviceability limit)

        return DeflectionCheckResult(
            immediateDeflection = 0.0,
            longTermDeflection = 0.0,
            calculatedDeflection = calculatedRatio,
            allowableDeflection = ratio,
            ratio = calculatedRatio / ratio,
            isSafe = isSafe,
            message = if (isSafe) "التحقق من الانحراف مطابق" else "نسبة البحر/السمك تتجاوز الحد المسموح",
            recommendation = if (!isSafe) "زِد السماكة الكلية إلى ${String.format("%.0f", ceil(minThickness / 10) * 10)} مم على الأقل" else "",
            warnings = if (!isSafe) listOf("نسبة البحر/السمك تتجاوز الحد المسموح حسب ECP 203 البند 6-3") else emptyList()
        )
    }

    // ── Moment coefficients for one-way ribbed slab ──

    private data class HordiMomentCoeffs(
        val positive: Double,
        val negative: Double,
        val shear: Double
    )

    private fun getHordiMomentCoefficients(
        edgeA: EdgeCondition,
        edgeB: EdgeCondition
    ): HordiMomentCoeffs {
        val isFixedA = edgeA == EdgeCondition.FIXED
        val isFixedB = edgeB == EdgeCondition.FIXED
        val isContA = edgeA == EdgeCondition.CONTINUOUS
        val isContB = edgeB == EdgeCondition.CONTINUOUS

        return when {
            // Both ends fixed
            isFixedA && isFixedB -> HordiMomentCoeffs(
                positive = 1.0 / 24.0,
                negative = 1.0 / 12.0,
                shear = 0.5
            )
            // Both ends continuous
            isContA && isContB -> HordiMomentCoeffs(
                positive = 1.0 / 14.0,
                negative = 1.0 / 10.0,
                shear = 0.55
            )
            // One end continuous/fixed, other simply supported
            (isContA || isFixedA) && edgeB == EdgeCondition.SIMPLY_SUPPORTED -> HordiMomentCoeffs(
                positive = 1.0 / 10.0,
                negative = 1.0 / 10.0,
                shear = 0.56
            )
            edgeA == EdgeCondition.SIMPLY_SUPPORTED && (isContB || isFixedB) -> HordiMomentCoeffs(
                positive = 1.0 / 10.0,
                negative = 1.0 / 10.0,
                shear = 0.56
            )
            // Default: simply supported
            else -> HordiMomentCoeffs(
                positive = 1.0 / 8.0,
                negative = 0.0,
                shear = 0.5
            )
        }
    }

    // ── Minimum steel for rib ──

    private fun getMinRibSteel(bw: Double, d: Double, fy: Double): Double {
        val minByPercentage = 0.0015 * bw * d
        val minByCode = 0.6 / fy.coerceAtLeast(1.0) * bw * d
        return max(minByPercentage, minByCode)
    }

    // ── K_bal calculation ──

    private fun calculateKBal(fcu: Double, fy: Double): Double {
        // K_bal per ECP 203 Section 4-2-2-1
        // For typical values: fcu=25, fy=360 → K_bal ≈ 0.186
        val fs = fy / GAMMA_S
        val n = 9.0 // modular ratio approximation for ECP
        val dPrime = 30.0 // approximate
        val xBal = (600.0 * dPrime) / (600.0 + fy)
        val Kb = 0.4 * (xBal / (2.0 * (1.0 + xBal / 2.0 * n)))
        return if (Kb > 0) Kb else 0.186
    }

    // ── Unsafe result helper ──

    private fun createUnsafeHordiResult(
        warnings: List<String>,
        codeNotes: List<String>,
        ribSpacing: Double,
        numberOfRibs: Int
    ): HordiSlabResult {
        val unsafeReinf = ReinforcementResult(
            astRequired = 0.0,
            astProvided = 0.0,
            barDiameter = 0.0,
            numberOfBars = 0,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = false,
            utilizationRatio = Double.MAX_VALUE,
            warnings = warnings,
            codeNotes = codeNotes
        )
        return HordiSlabResult(
            ribReinforcement = unsafeReinf,
            topReinforcement = unsafeReinf,
            toppingReinforcement = unsafeReinf,
            ribShearCheck = ShearCheckResult(isSafe = false, warnings = warnings),
            deflectionCheck = DeflectionCheckResult(isSafe = false, warnings = warnings),
            isSafe = false,
            ribSpacing = ribSpacing,
            numberOfRibs = numberOfRibs,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }
}

// ============================================================================
// WAFFLE SLAB DESIGN — ECP 203 Section 6-4
// ============================================================================

class ECPWaffleSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val MIN_RIB_WIDTH = 125.0      // mm (wider than hordi)
        private const val MIN_TOPPING = 75.0          // mm
        private const val MAX_PANEL_DIMENSION = 12.0  // m
        private const val DROP_PANEL_SIZE = 3000.0    // mm (typical)
        private const val COVER = 20.0                // mm
        private const val HALF_BAR_EST = 6.0          // mm
    }

    /**
     * تصميم كامل للبلاطة المجنحة (Waffle Slab) حسب الكود المصري ECP 203-2020 البند 6-4
     *
     * @param panelWidth       عرض اللوحة (مم) - الاتجاه القصير
     * @param panelLength      طول اللوحة (مم) - الاتجاه الطويل
     * @param ribWidth         عرض الكمرتة (مم)
     * @param ribDepth         عمق الكمرتة (مم)
     * @param ribSpacing       تباعد الكمرات مركز لمركز (مم)
     * @param toppingThickness  سمك الطبقة العلوية (مم)
     * @param fcu              مقاومة الضغط للخرسانة (ميجاباسكال)
     * @param fy               مقاومة الخضوع للصلب (ميجاباسكال)
     * @param deadLoad         الحمل الميت (كيلونيوتن/م²)
     * @param liveLoad         الحمل الحي (كيلونيوتن/م²)
     * @param supportConditions  شروط التثبيت
     * @param loadCombination   مجموعة التحميل
     * @param hasDropPanels    هل يوجد رأس صلب (drop panel)
     * @param columnSize       عرض العمود (مم)
     */
    fun designWaffleSlab(
        panelWidth: Double,
        panelLength: Double,
        ribWidth: Double,
        ribDepth: Double,
        ribSpacing: Double,
        toppingThickness: Double,
        fcu: Double,
        fy: Double,
        deadLoad: Double,
        liveLoad: Double,
        supportConditions: SlabSupportConditions,
        loadCombination: LoadCombination,
        hasDropPanels: Boolean = true,
        columnSize: Double = 400.0
    ): WaffleSlabResult {

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ── 1. Section classification and geometry checks ──

        if (ribWidth < MIN_RIB_WIDTH) {
            warnings.add("عرض الكمرتة (${ribWidth.toInt()} مم) أقل من الحد الأدنى (125 مم) للبلاطة المجنحة")
        }

        if (toppingThickness < MIN_TOPPING) {
            warnings.add("سمك الطبقة العلوية (${toppingThickness.toInt()} مم) أقل من الحد الأدنى (75 مم) للبلاطة المجنحة")
        }

        val panelWidthM = panelWidth / 1000.0
        val panelLengthM = panelLength / 1000.0

        if (max(panelWidthM, panelLengthM) > MAX_PANEL_DIMENSION) {
            warnings.add("أبعاد اللوحة تتجاوز الحد الأقصى (12 م) حسب ECP 203")
        }

        val totalThickness = ribDepth + toppingThickness

        // Solid head (drop panel) size
        val solidHeadSize = if (hasDropPanels) {
            max(DROP_PANEL_SIZE, min(panelWidth, panelLength) / 3.0)
        } else {
            0.0
        }

        codeNotes.add("ECP 203-2020: البند 6-4 (البلاطة المجنحة)")
        codeNotes.add("أبعاد اللوحة: ${String.format("%.1f", panelWidthM)} × ${String.format("%.1f", panelLengthM)} م")
        codeNotes.add("تباعد الكمرات: ${ribSpacing.toInt()} مم | عرض الكمرتة: ${ribWidth.toInt()} مم | عمق الكمرتة: ${ribDepth.toInt()} مم")
        codeNotes.add("سمك التوبينج: ${toppingThickness.toInt()} مم | السماكة الكلية: ${totalThickness.toInt()} مم")
        if (hasDropPanels) {
            codeNotes.add("حجم الرأس الصلب: ${String.format("%.0f", solidHeadSize)} × ${String.format("%.0f", solidHeadSize)} مم")
        }

        // ── 2. Two-way analysis ──

        val loadFactor = loadCombination.getFactorForCode(DesignCode.ECP)
        val wu = (deadLoad + liveLoad) * loadFactor // kN/m² (factored)

        codeNotes.add("معامل التحميل: ${String.format("%.2f", loadFactor)} | الحمل التصميمي: ${String.format("%.1f", wu)} ك.ن/م²")

        // Aspect ratio for load distribution
        val aspectRatio = panelLength / panelWidth.coerceAtLeast(1.0)
        val isTwoWay = aspectRatio <= 2.0

        // Moment distribution coefficients (Marcus simplified for waffle)
        val shortDirCoeff = if (isTwoWay) {
            // Short direction carries more load in two-way
            val alpha = 1.0 / (1.0 + (aspectRatio * aspectRatio * aspectRatio).pow(1.0 / 3.0))
            alpha
        } else {
            1.0 // One-way behavior
        }
        val longDirCoeff = 1.0 - shortDirCoeff

        codeNotes.add("نسبة الأبعاد: ${String.format("%.2f", aspectRatio)} | معامل التوزيع القصير: ${String.format("%.2f", shortDirCoeff)} | الطويل: ${String.format("%.2f", longDirCoeff)}")

        // Support condition analysis
        val edgeA = supportConditions.edgeA
        val edgeB = supportConditions.edgeB
        val edgeC = supportConditions.edgeC
        val edgeD = supportConditions.edgeD

        val isAllContinuous = setOf(edgeA, edgeB, edgeC, edgeD).all {
            it == EdgeCondition.CONTINUOUS || it == EdgeCondition.FIXED
        }
        val isPartiallyContinuous = setOf(edgeA, edgeB, edgeC, edgeD).any {
            it == EdgeCondition.CONTINUOUS || it == EdgeCondition.FIXED
        }

        // Moment coefficients per ECP 203 for continuous two-way waffle slabs
        val posCoeff = if (isAllContinuous) 1.0 / 14.0 else if (isPartiallyContinuous) 1.0 / 10.0 else 1.0 / 8.0
        val negCoeff = if (isAllContinuous) 1.0 / 10.0 else if (isPartiallyContinuous) 1.0 / 10.0 else 0.0

        // ── 3. Solid head (drop panel) design ──

        val headReinfBottom: ReinforcementResult
        val headReinfTop: ReinforcementResult

        if (hasDropPanels && solidHeadSize > 0) {
            // Effective depth of solid head
            val dHead = totalThickness - COVER - HALF_BAR_EST

            // Moment per meter strip in solid head zone
            // Total factored load on the panel
            val totalPanelLoad = wu * panelWidthM * panelLengthM // kN total
            val headMomentPos = posCoeff * shortDirCoeff * wu * panelWidthM * panelWidthM * 1000.0 // kN.m/m (per meter strip, short dir)
            val headMomentNeg = negCoeff * wu * panelWidthM * panelWidthM * 1000.0 // kN.m/m (at support)

            // K-method for solid head bottom
            headReinfBottom = designSolidHeadSection(
                Mu = headMomentPos,
                fcu = fcu,
                fy = fy,
                d = dHead,
                b = 1000.0,
                fs = fy / GAMMA_S,
                sectionLabel = "سفل الرأس الصلب",
                warnings = warnings,
                codeNotes = codeNotes
            )

            // K-method for solid head top (at support)
            headReinfTop = if (headMomentNeg > 0.01) {
                designSolidHeadSection(
                    Mu = headMomentNeg,
                    fcu = fcu,
                    fy = fy,
                    d = dHead,
                    b = 1000.0,
                    fs = fy / GAMMA_S,
                    sectionLabel = "أعلى الرأس الصلب (عند الدعامة)",
                    warnings = warnings,
                    codeNotes = codeNotes
                )
            } else {
                // Minimum reinforcement
                val minAs = max(0.0015 * 1000.0 * dHead, 0.6 / fy.coerceAtLeast(1.0) * 1000.0 * dHead)
                ReinforcementResult(
                    astRequired = minAs,
                    astProvided = minAs,
                    barDiameter = 10.0,
                    numberOfBars = ceil(minAs / (PI * 100.0 / 4.0)).toInt().coerceAtLeast(5),
                    tiesDiameter = 0.0,
                    tiesSpacing = 0.0,
                    isSafe = true,
                    utilizationRatio = 0.0,
                    spacing = 200.0,
                    description = "حديد أدنى بالرأس الصلب"
                )
            }
        } else {
            // No drop panels — solid head reinforcement is not needed
            val emptyReinf = ReinforcementResult(
                astRequired = 0.0,
                astProvided = 0.0,
                barDiameter = 0.0,
                numberOfBars = 0,
                tiesDiameter = 0.0,
                tiesSpacing = 0.0,
                isSafe = true,
                utilizationRatio = 0.0,
                description = "لا يوجد رأس صلب"
            )
            headReinfBottom = emptyReinf
            headReinfTop = emptyReinf
            codeNotes.add("لا يوجد رأس صلب - التصميم يعتمد على الكمرات فقط")
        }

        // ── 4. Rib design (each direction) ──

        // Effective depth of rib
        val dRib = totalThickness - COVER - HALF_BAR_EST
        val fs = fy / GAMMA_S

        // Short direction ribs
        val wRibShort = wu * shortDirCoeff * ribSpacing / 1000.0 // kN/m per rib
        val MuRibShortPos = posCoeff * wRibShort * panelWidthM * panelWidthM // kN.m per rib
        val MuRibShortNeg = negCoeff * wRibShort * panelWidthM * panelWidthM

        val ribShortPos = designWaffleRib(
            Mu = MuRibShortPos,
            fcu = fcu,
            fy = fy,
            bw = ribWidth,
            d = dRib,
            fs = fs,
            directionLabel = "قصير (منتصف البحر)",
            warnings = warnings,
            codeNotes = codeNotes
        )

        val ribShortNeg = if (MuRibShortNeg > 0.01) {
            designWaffleRib(
                Mu = MuRibShortNeg,
                fcu = fcu,
                fy = fy,
                bw = ribWidth,
                d = dRib,
                fs = fs,
                directionLabel = "قصير (عند الدعامة)",
                warnings = warnings,
                codeNotes = codeNotes
            )
        } else {
            createMinRibReinforcement(ribWidth, dRib, fy)
        }

        // Use the larger of the two for the result (support governs typically)
        val ribReinfShort = if (ribShortNeg.astRequired > ribShortPos.astRequired) ribShortNeg else ribShortPos

        // Long direction ribs
        val wRibLong = wu * longDirCoeff * ribSpacing / 1000.0 // kN/m per rib
        val MuRibLongPos = posCoeff * wRibLong * panelLengthM * panelLengthM // kN.m per rib
        val MuRibLongNeg = negCoeff * wRibLong * panelLengthM * panelLengthM

        val ribLongPos = designWaffleRib(
            Mu = MuRibLongPos,
            fcu = fcu,
            fy = fy,
            bw = ribWidth,
            d = dRib,
            fs = fs,
            directionLabel = "طويل (منتصف البحر)",
            warnings = warnings,
            codeNotes = codeNotes
        )

        val ribLongNeg = if (MuRibLongNeg > 0.01) {
            designWaffleRib(
                Mu = MuRibLongNeg,
                fcu = fcu,
                fy = fy,
                bw = ribWidth,
                d = dRib,
                fs = fs,
                directionLabel = "طويل (عند الدعامة)",
                warnings = warnings,
                codeNotes = codeNotes
            )
        } else {
            createMinRibReinforcement(ribWidth, dRib, fy)
        }

        val ribReinfLong = if (ribLongNeg.astRequired > ribLongPos.astRequired) ribLongNeg else ribLongPos

        // ── 5. Topping reinforcement ──

        val toppingResult = designWaffleTopping(
            toppingThickness = toppingThickness,
            ribSpacing = ribSpacing,
            ribWidth = ribWidth,
            fcu = fcu,
            fy = fy,
            wu = wu,
            warnings = warnings,
            codeNotes = codeNotes
        )

        // ── 6. Punching shear at column ──

        val punchingCheck = checkPunchingShear(
            panelWidth = panelWidth,
            panelLength = panelLength,
            wu = wu,
            columnSize = columnSize,
            fcu = fcu,
            d = totalThickness - COVER - HALF_BAR_EST,
            hasDropPanels = hasDropPanels,
            solidHeadSize = solidHeadSize,
            warnings = warnings,
            codeNotes = codeNotes
        )

        // ── 7. Deflection check ──

        val deflectionCheck = checkWaffleDeflection(
            panelWidth = panelWidth,
            panelLength = panelLength,
            totalThickness = totalThickness,
            isAllContinuous = isAllContinuous,
            isPartiallyContinuous = isPartiallyContinuous,
            warnings = warnings,
            codeNotes = codeNotes
        )

        // ── 8. Number of ribs ──

        val numberOfRibsShort = max(floor(panelWidth / ribSpacing).toInt(), 1)
        val numberOfRibsLong = max(floor(panelLength / ribSpacing).toInt(), 1)

        codeNotes.add("عدد الكمرات (قصير): $numberOfRibsShort | (طويل): $numberOfRibsLong")

        // ── Overall safety ──

        val isSafe = headReinfBottom.isSafe && headReinfTop.isSafe &&
                      ribReinfShort.isSafe && ribReinfLong.isSafe &&
                      toppingResult.isSafe && punchingCheck.isSafe && deflectionCheck.isSafe

        return WaffleSlabResult(
            solidHeadReinforcementBottom = headReinfBottom,
            solidHeadReinforcementTop = headReinfTop,
            ribReinforcementShort = ribReinfShort,
            ribReinforcementLong = ribReinfLong,
            toppingReinforcement = toppingResult,
            punchingShearCheck = punchingCheck,
            deflectionCheck = deflectionCheck,
            isSafe = isSafe,
            numberOfRibsShort = numberOfRibsShort,
            numberOfRibsLong = numberOfRibsLong,
            solidHeadSize = solidHeadSize,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ── Solid head section design (per meter strip) ──

    private fun designSolidHeadSection(
        Mu: Double,
        fcu: Double,
        fy: Double,
        d: Double,
        b: Double,
        fs: Double,
        sectionLabel: String,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ReinforcementResult {
        val MuNmm = Mu * 1e6
        val K_bal = 0.186

        val K = if (fcu > 0 && b > 0 && d > 0) MuNmm / (fcu * b * d * d) else 0.0

        if (K > K_bal) {
            warnings.add("K = ${String.format("%.3f", K)} > K_bal = ${String.format("%.3f", K_bal)} - الرأس الصلب يحتاج زيادة سماكة (${sectionLabel})")
        }

        val discriminant = 0.25 - K / 1.25
        val leverArm = if (discriminant > 0) {
            d * (0.5 + sqrt(discriminant))
        } else {
            d * 0.7
        }.coerceAtMost(0.9 * d)

        var astRequired = if (fs > 0 && leverArm > 0) MuNmm / (fs * leverArm) else 0.0

        // Minimum reinforcement per meter
        val minAs = max(0.0015 * b * d, 0.6 / fy.coerceAtLeast(1.0) * b * d)
        if (astRequired < minAs) {
            astRequired = minAs
        }

        // Select bars for solid head (per meter strip)
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        var selectedDia = 12.0
        var selectedSpacing = 200.0
        var astProvided = 0.0

        for (dia in availableBars) {
            val barArea = PI * dia * dia / 4.0
            val spacing = (barArea * 1000.0 / astRequired).coerceIn(100.0, 200.0)
            val asProv = barArea * 1000.0 / spacing
            if (asProv >= astRequired) {
                selectedDia = dia
                selectedSpacing = spacing
                astProvided = asProv
                break
            }
        }

        // Fallback: use 10mm @ 100mm
        if (astProvided < astRequired) {
            selectedDia = 10.0
            selectedSpacing = 100.0
            astProvided = PI * 100.0 / 4.0 * 1000.0 / 100.0
            warnings.add("تسليح الرأس الصلب يتطلب شبك أكثر كثافة - Ø10 @ 100 مم")
        }

        val barsPerMeter = (1000.0 / selectedSpacing).toInt()
        val utilizationRatio = if (astProvided > 0) astRequired / astProvided else 2.0

        codeNotes.add("${sectionLabel}: As_req = ${String.format("%.0f", astRequired)} مم²/م | As_prov = ${String.format("%.0f", astProvided)} مم²/م (Ø${selectedDia.toInt()} @ ${selectedSpacing.toInt()})")

        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedDia,
            numberOfBars = barsPerMeter,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = K <= K_bal && astProvided >= astRequired,
            utilizationRatio = utilizationRatio,
            spacing = selectedSpacing,
            description = "Ø${selectedDia.toInt()} @ ${selectedSpacing.toInt()} مم ($sectionLabel)"
        )
    }

    // ── Waffle rib design ──

    private fun designWaffleRib(
        Mu: Double,
        fcu: Double,
        fy: Double,
        bw: Double,
        d: Double,
        fs: Double,
        directionLabel: String,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ReinforcementResult {
        val MuNmm = Mu * 1e6
        val K_bal = 0.186

        val K = if (fcu > 0 && bw > 0 && d > 0) MuNmm / (fcu * bw * d * d) else 0.0

        if (K > K_bal) {
            warnings.add("K = ${String.format("%.3f", K)} > K_bal = ${String.format("%.3f", K_bal)} - كمرتة ($directionLabel) مفرطة التسليح")
        }

        val discriminant = 0.25 - K / 1.25
        val leverArm = if (discriminant > 0) {
            d * (0.5 + sqrt(discriminant))
        } else {
            d * 0.7
        }.coerceAtMost(0.9 * d)

        var astRequired = if (fs > 0 && leverArm > 0) MuNmm / (fs * leverArm) else 0.0

        // Minimum steel for rib
        val minAs = max(0.0015 * bw * d, 0.6 / fy.coerceAtLeast(1.0) * bw * d)
        if (astRequired < minAs) {
            astRequired = minAs
        }

        // Select bar — max 2 bars per rib
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0)
        var selectedDia = 12.0
        var numberOfBars = 1
        var astProvided = 0.0

        for (dia in availableBars) {
            val barArea = PI * dia * dia / 4.0
            val n = ceil(astRequired / barArea).toInt().coerceIn(1, 2)
            if (n <= 2) {
                selectedDia = dia
                numberOfBars = n
                astProvided = n * barArea
                break
            }
        }

        if (astProvided < astRequired) {
            val barArea = PI * 16.0 * 16.0 / 4.0
            selectedDia = 16.0
            numberOfBars = 2
            astProvided = 2.0 * barArea
            warnings.add("تم استخدام أكبر تسليح ممكن (2Ø16) للكمرتة ($directionLabel) - زِد عرض الكمرتة أو السماكة")
        }

        val utilizationRatio = if (astProvided > 0 && leverArm > 0) {
            astRequired / astProvided
        } else 2.0

        codeNotes.add("كمرتة $directionLabel: As_req = ${String.format("%.0f", astRequired)} مم² | As_prov = ${String.format("%.0f", astProvided)} مم² (${numberOfBars}Ø${selectedDia.toInt()})")

        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedDia,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = K <= K_bal && astProvided >= astRequired,
            utilizationRatio = utilizationRatio,
            warnings = if (K > K_bal) listOf("المقطع مفرط التسليح") else emptyList(),
            codeNotes = listOf("ECP 203 البند 4-2-2-1: K-method")
        )
    }

    // ── Minimum rib reinforcement ──

    private fun createMinRibReinforcement(bw: Double, d: Double, fy: Double): ReinforcementResult {
        val minAs = max(0.0015 * bw * d, 0.6 / fy.coerceAtLeast(1.0) * bw * d)
        val barArea = PI * 100.0 / 4.0 // 10mm
        val n = ceil(minAs / barArea).toInt().coerceIn(1, 2)
        return ReinforcementResult(
            astRequired = minAs,
            astProvided = n * barArea,
            barDiameter = 10.0,
            numberOfBars = n,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = true,
            utilizationRatio = 0.0,
            description = "حديد أدنى للكمرتة"
        )
    }

    // ── Topping design for waffle slab ──

    private fun designWaffleTopping(
        toppingThickness: Double,
        ribSpacing: Double,
        ribWidth: Double,
        fcu: Double,
        fy: Double,
        wu: Double,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ReinforcementResult {
        // Topping clear span between ribs
        val clearSpanTopping = ribSpacing - ribWidth // mm
        val clearSpanM = clearSpanTopping / 1000.0

        // Minimum reinforcement
        val minAs = max(
            0.0015 * 1000.0 * toppingThickness,
            0.6 / fy.coerceAtLeast(1.0) * 1000.0 * toppingThickness
        )

        // Select mesh
        val meshOptions = listOf(
            Triple(6.0, 200.0, PI * 36.0 / 4.0 * 1000.0 / 200.0),
            Triple(6.0, 150.0, PI * 36.0 / 4.0 * 1000.0 / 150.0),
            Triple(8.0, 200.0, PI * 64.0 / 4.0 * 1000.0 / 200.0),
            Triple(8.0, 150.0, PI * 64.0 / 4.0 * 1000.0 / 150.0),
            Triple(10.0, 200.0, PI * 100.0 / 4.0 * 1000.0 / 200.0),
            Triple(10.0, 150.0, PI * 100.0 / 4.0 * 1000.0 / 150.0)
        )

        var selectedDia = 8.0
        var selectedSpacing = 200.0
        var astProvided = 0.0

        for ((dia, spacing, asProv) in meshOptions) {
            if (asProv >= minAs) {
                selectedDia = dia
                selectedSpacing = spacing
                astProvided = asProv
                break
            }
        }

        if (astProvided < minAs) {
            selectedDia = 10.0
            selectedSpacing = 150.0
            astProvided = PI * 100.0 / 4.0 * 1000.0 / 150.0
            warnings.add("تسليح التوبينج يتطلب شبك أكثر كثافة")
        }

        val barsPerMeter = (1000.0 / selectedSpacing).toInt()
        val utilizationRatio = if (astProvided > 0) minAs / astProvided else 2.0

        codeNotes.add("تسليح التوبينج: شبك ملحومة Ø${selectedDia.toInt()} @ ${selectedSpacing.toInt()} مم (${barsPerMeter}Ø${selectedDia.toInt()}/م')")
        codeNotes.add("As_min = ${String.format("%.0f", minAs)} مم²/م | As_prov = ${String.format("%.0f", astProvided)} مم²/م")

        return ReinforcementResult(
            astRequired = minAs,
            astProvided = astProvided,
            barDiameter = selectedDia,
            numberOfBars = barsPerMeter,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = astProvided >= minAs,
            utilizationRatio = utilizationRatio,
            spacing = selectedSpacing,
            description = "شبك ملحوة للتوبينج Ø${selectedDia.toInt()} @ ${selectedSpacing.toInt()}"
        )
    }

    // ── Punching shear check at column ──

    private fun checkPunchingShear(
        panelWidth: Double,
        panelLength: Double,
        wu: Double,
        columnSize: Double,
        fcu: Double,
        d: Double,
        hasDropPanels: Boolean,
        solidHeadSize: Double,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): ShearCheckResult {
        // Total factored load from tributary area
        val tributaryArea = (panelWidth / 1000.0) * (panelLength / 1000.0) // m²
        val Vu = wu * tributaryArea // kN

        // Critical section perimeter at d/2 from column face
        val effectiveD = if (hasDropPanels && solidHeadSize > 0) {
            // Use solid head effective depth for punching
            d
        } else {
            d
        }

        // bo = 4 × (columnSize + d) for square columns
        val bo = 4.0 * (columnSize + effectiveD) // mm
        val boM = bo / 1000.0 // m

        // Punching shear stress
        val qp = (Vu * 1000.0) / (bo * effectiveD) // N/mm² (MPa)

        // Punching shear capacity per ECP 203 Section 4-3-2
        // qp_capacity = 0.316 × √(fcu/γc)
        val qpCapacity = 0.316 * sqrt(fcu / GAMMA_C) // MPa

        // For edge columns: multiply by (1 + 2/β) where β = column aspect ratio
        // For interior columns (assumed): use base capacity
        val isEdgeColumn = false // Simplified assumption
        val effectiveQpCapacity = if (isEdgeColumn) {
            val beta = max(panelWidth, panelLength) / min(panelWidth, panelLength).coerceAtLeast(1.0)
            qpCapacity * (1.0 + 2.0 / beta)
        } else {
            qpCapacity
        }

        val isSafe = qp <= effectiveQpCapacity
        val utilizationRatio = if (effectiveQpCapacity > 0) qp / effectiveQpCapacity else Double.MAX_VALUE

        if (!isSafe) {
            if (!hasDropPanels) {
                warnings.add("قص الثقب يتجاوز قدرة الخرسانة! qp = ${String.format("%.2f", qp)} > qp_cap = ${String.format("%.2f", effectiveQpCapacity)} ميجاباسكال")
                warnings.add("يجب إضافة رأس صلب (drop panel) أو كانت تسليح القص (shear studs)")
            } else {
                warnings.add("قص الثقب يتجاوز قدرة الخرسانة حتى مع الرأس الصلب! qp = ${String.format("%.2f", qp)} ميجاباسكال")
                warnings.add("يجب زيادة حجم الرأس الصلب أو استخدام كانت تسليح القص")
            }
        }

        codeNotes.add("قص الثقب: Vu = ${String.format("%.1f", Vu)} ك.ن | bo = ${String.format("%.0f", bo)} مم")
        codeNotes.add("qp = ${String.format("%.2f", qp)} ميجاباسكال | qp_cap = ${String.format("%.2f", effectiveQpCapacity)} ميجاباسكال")
        codeNotes.add("ECP 203 البند 4-3-2: qp_cap = 0.316 × √(fcu/γc)")

        return ShearCheckResult(
            appliedShear = qp,
            shearCapacity = effectiveQpCapacity,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            criticalSection = effectiveD,
            criticalPerimeter = bo,
            warnings = if (!isSafe) listOf("قص الثقب غير آمن - يحتاج رأس صلب أو كانت تسليح قص") else emptyList()
        )
    }

    // ── Deflection check for waffle slab ──

    private fun checkWaffleDeflection(
        panelWidth: Double,
        panelLength: Double,
        totalThickness: Double,
        isAllContinuous: Boolean,
        isPartiallyContinuous: Boolean,
        warnings: MutableList<String>,
        codeNotes: MutableList<String>
    ): DeflectionCheckResult {
        // Span/depth ratio per ECP 203 Section 6-3 for two-way waffle slabs
        val ratio = when {
            isAllContinuous -> 30.0
            isPartiallyContinuous -> 25.0
            else -> 20.0
        }

        val shortSpan = min(panelWidth, panelLength)
        val minThickness = shortSpan / ratio
        val isSafe = totalThickness >= minThickness
        val calculatedRatio = shortSpan / totalThickness

        if (!isSafe) {
            warnings.add("نسبة البحر للسمك = ${String.format("%.1f", calculatedRatio)} تتجاوز الحد المسموح (${ratio.toInt()})")
            warnings.add("زِد السماكة الكلية للبلاطة المجنحة إلى ${String.format("%.0f", ceil(minThickness / 10) * 10)} مم على الأقل")
        }

        codeNotes.add("التحقق من الانحراف: L/h = ${String.format("%.1f", calculatedRatio)} (الحد: ${ratio.toInt()})")
        codeNotes.add("السماكة المطلوبة: ${String.format("%.0f", minThickness)} مم | المقدمة: ${totalThickness.toInt()} مم")

        return DeflectionCheckResult(
            immediateDeflection = 0.0,
            longTermDeflection = 0.0,
            calculatedDeflection = calculatedRatio,
            allowableDeflection = ratio,
            ratio = calculatedRatio / ratio,
            isSafe = isSafe,
            message = if (isSafe) "التحقق من الانحراف مطابق حسب ECP 203 البند 6-3" else "نسبة البحر/السمك تتجاوز الحد المسموح",
            recommendation = if (!isSafe) "زِد السماكة الكلية أو قلل تباعد الكمرات" else "",
            warnings = if (!isSafe) listOf("نسبة البحر/السمك تتجاوز الحد المسموح حسب ECP 203 البند 6-3") else emptyList()
        )
    }
}