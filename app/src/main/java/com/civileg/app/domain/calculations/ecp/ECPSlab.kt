package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

class ECPSlab : SlabDesign {
    
    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
    }

    override fun designOneWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        clearSpan: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination
    ): SlabDesignResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("slabThickness", slabThickness)
        InputGuard.positive("clearSpan", clearSpan)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // عرض الوحدة = 1 متر = 1000 مم
        val width = 1000.0
        val effectiveDepth = slabThickness - getMinCover() - 6  // تقريب للحديد (يفترض سيخ 12 مم)
        
        // حساب التسليح للانحناء - K-method حسب ECP 203 البند 4-2-2-1
        // Mu يجب أن يكون العزم التصميمي (Ultimate) بدون قسمة على معامل التحميل
        val Mu = designMoment * 1e6  // N.mm/m (العزم التصميمي المطلق)
        val fs = fy / GAMMA_S
        
        // K = Mu / (fcu × b × d²) - نستخدم fcu مباشرة وليس fc
        // K_bal محسوب ديناميكياً حسب fcu و fy - ECP 203 البند 4-2-2-1
        // K_bal = (0.67/γc) × (a/d) × (1 - a/(2d))
        // حيث a/d = 0.9 × εcu/(εcu + fy/(Es×γs))
        val epsilonCu = 0.003
        val epsilonY = fy / (200000.0 * GAMMA_S)
        val aOverD = 0.9 * epsilonCu / (epsilonCu + epsilonY)
        val K_bal = (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
        val K = if (fcu > 0 && effectiveDepth > 0) Mu / (fcu * width * effectiveDepth * effectiveDepth) else 0.0
        
        if (K > K_bal) {
            warnings.add(String.format("K=%.3f > K_bal=%.3f - Section is over-reinforced, increase slab thickness", K, K_bal))
        }
        
        // ذراع القوة: z = d × (0.5 + √(0.25 - K/0.893)) حسب ECP 203 K-method
        // 0.893 = γc/(2×0.67) = 1.5/1.34
        val leverArm = if (0.25 - K / 0.893 > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / 0.893))
        } else {
            effectiveDepth * 0.7  // تقريب آمن للحالات الحرجة
        }
        
        // As = Mu / (fy/γs × z) حسب ECP 203
        var astRequired = if (fs > 0 && leverArm > 0) Mu / (fs * leverArm) else 0.0 // mm²/m
        
        // الحد الأدنى للتسليح للبلاطات (0.6/fy * b * d or 0.15% Ag)
        val minSteel = max(0.26 * sqrt(fcu) / fy * width * effectiveDepth, 0.0013 * width * effectiveDepth)  // ECP 203: based on d
        if (astRequired < minSteel) {
            astRequired = minSteel
            warnings.add("Minimum reinforcement applied")
        }
        
        // اختيار قطر وتباعد الحديد
        val availableBars = listOf(8.0, 10.0, 12.0, 14.0)
        val barDiameter = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            val spacing = area * 1000 / astRequired
            spacing <= getMaxBarSpacing() && spacing >= 50
        } ?: 10.0
        
        val barArea = PI * barDiameter * barDiameter / 4
        val barSpacing = if (astRequired > 0) (barArea * 1000 / astRequired).coerceIn(50.0, getMaxBarSpacing()) else getMaxBarSpacing()
        val astProvided = barArea * 1000 / barSpacing
        
        // التحقق من القص - ECP 203 البند 4-3-1-2
        // qcu = 0.24 × √(fcu) / γc per ECP 203 §4-3-1-2
        val qcu = 0.24 * sqrt(fcu / GAMMA_C)  // MPa — ECP 203 §4-3-1-2 (includes γc)
        val shearCapacity = qcu * width * effectiveDepth / 1000.0  // kN/m
        
        // designShear بالفعل هو القوة القصية التصميمية (kN/m)
        val isShearSafe = designShear <= shearCapacity
        if (!isShearSafe) {
            warnings.add("Shear capacity exceeded - increase thickness")
        }
        
        codeNotes.add(String.format("ECP 203: qcu = %.2f MPa", qcu))
        
        // التحقق من السمك للانحراف
        val minThickness = getMinSlabThickness(clearSpan, SupportCondition.SIMPLY_SUPPORTED)
        if (slabThickness < minThickness) {
            warnings.add("Thickness below minimum for deflection control")
        }
        
        codeNotes.add(CodeReference.ECP.SLAB_ONE_WAY)
        codeNotes.add(String.format("K = %.3f, z = %.0f mm", K, leverArm))
        codeNotes.add(String.format("As_req = %.0f mm²/m, As_prov = %.0f mm²/m", astRequired, astProvided))
        
        return SlabDesignResult(
            requiredReinforcement = astRequired,
            providedReinforcement = astProvided,
            barDiameter = barDiameter,
            barSpacing = barSpacing,
            minThickness = minThickness,
            shearCapacity = shearCapacity,
            isSafe = isShearSafe && slabThickness >= minThickness,
            utilizationRatio = if (astProvided > 0) designMoment / (astProvided * fs * leverArm / 1e6) else 2.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun designTwoWaySlab(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        shortSpan: Double,
        longSpan: Double,
        supportConditions: SlabSupportConditions,
        totalLoad: Double,
        loadCombination: LoadCombination
    ): TwoWaySlabResult {
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("slabThickness", slabThickness)
        InputGuard.positive("shortSpan", shortSpan)
        InputGuard.positive("longSpan", longSpan)
        InputGuard.positive("totalLoad", totalLoad)

        // معاملات العزم حسب حالة التثبيت
        val aspectRatio = longSpan / shortSpan.coerceAtLeast(1.0)
        val coefficients = calculateMomentCoefficients(aspectRatio, supportConditions)
        
        val MuShort = coefficients.positiveShort * totalLoad * shortSpan * shortSpan / 1_000_000.0 // kN.m/m
        val resultShort = designOneWaySlab(
            fcu, fy, slabThickness, shortSpan,
            MuShort, totalLoad * shortSpan / 2000.0, loadCombination
        )

        val MuLong = coefficients.positiveLong * totalLoad * longSpan * longSpan / 1_000_000.0 // kN.m/m
        val resultLong = designOneWaySlab(
            fcu, fy, slabThickness, longSpan,
            MuLong, totalLoad * longSpan / 2000.0, loadCombination
        )
        
        return TwoWaySlabResult(
            shortDirection = resultShort,
            longDirection = resultLong,
            momentCoefficients = coefficients,
            isSafe = resultShort.isSafe && resultLong.isSafe
        )
    }

    /**
     * معاملات عزم الانحناء للبلاطة ثنائية الاتجاه حسب ECP 203-2020 جدول 6-1
     *
     * يُميّز الجدول 9 حالات حافة رئيسية بحسب حالة التثبيت على كل حافة:
     *   F = مثبتة (Fixed), S = مفصلية (Simply Supported), C = حرة (Free)
     *
     * قيم المعاملات: αsx (إيجابي - الاتجاه القصير)، αsy (إيجابي - الاتجاه الطويل)
     * نسبة البحر: r = Ly/Lx  (دائماً ≥ 1)
     *
     * المصدر: ECP 203-2020 الجدول 6-1 (Marcus Method)
     */
    private fun calculateMomentCoefficients(
        aspectRatio: Double,
        supports: SlabSupportConditions
    ): MomentCoefficients {
        // تحديد نوع التثبيت لكل زوج من الحافات المتقابلة
        val longFixed  = supports.edgeA == EdgeCondition.FIXED && supports.edgeC == EdgeCondition.FIXED
        val longSimple = supports.edgeA == EdgeCondition.SIMPLY_SUPPORTED && supports.edgeC == EdgeCondition.SIMPLY_SUPPORTED
        val shortFixed = supports.edgeB == EdgeCondition.FIXED && supports.edgeD == EdgeCondition.FIXED

        // عدد الحافات المثبتة
        val fixedCount = listOf(supports.edgeA, supports.edgeB, supports.edgeC, supports.edgeD)
            .count { it == EdgeCondition.FIXED }

        val r = aspectRatio.coerceAtLeast(1.0).coerceAtMost(2.5)

        // ── ECP 203 جدول 6-1: المعاملات الإيجابية (وسط البحر) والسلبية (عند الدعامة) ──
        // MomentCoefficients(αsx_pos, αsy_pos, αsx_neg, αsy_neg)
        // αsx_neg و αsy_neg = معاملات العزم السالب عند الحافة المثبتة (= 4/3 × αsx_pos تقريباً)

        return when {
            // حالة 1: كل الحافات مثبتة (FFFF)
            fixedCount == 4 -> when {
                r <= 1.0 -> MomentCoefficients(0.031, 0.024, 0.042, 0.032)
                r <= 1.1 -> MomentCoefficients(0.035, 0.025, 0.047, 0.034)
                r <= 1.2 -> MomentCoefficients(0.039, 0.026, 0.052, 0.035)
                r <= 1.3 -> MomentCoefficients(0.043, 0.027, 0.057, 0.036)
                r <= 1.4 -> MomentCoefficients(0.047, 0.028, 0.062, 0.037)
                r <= 1.5 -> MomentCoefficients(0.050, 0.029, 0.066, 0.038)
                r <= 1.75-> MomentCoefficients(0.057, 0.031, 0.075, 0.040)
                else     -> MomentCoefficients(0.062, 0.032, 0.083, 0.041)
            }
            // حالة 2: ثلاث حافات مثبتة (الحافة القصيرة الأولى حرة)
            fixedCount == 3 && supports.edgeA != EdgeCondition.FIXED -> when {
                r <= 1.0 -> MomentCoefficients(0.045, 0.030, 0.060, 0.040)
                r <= 1.2 -> MomentCoefficients(0.050, 0.031, 0.067, 0.041)
                r <= 1.4 -> MomentCoefficients(0.056, 0.032, 0.075, 0.042)
                r <= 1.75-> MomentCoefficients(0.063, 0.033, 0.084, 0.043)
                else     -> MomentCoefficients(0.069, 0.034, 0.092, 0.044)
            }
            // حالة 3: ثلاث حافات مثبتة (الحافة الطولية الأولى حرة)
            fixedCount == 3 && supports.edgeB != EdgeCondition.FIXED -> when {
                r <= 1.0 -> MomentCoefficients(0.039, 0.030, 0.052, 0.040)
                r <= 1.2 -> MomentCoefficients(0.044, 0.031, 0.059, 0.041)
                r <= 1.4 -> MomentCoefficients(0.050, 0.032, 0.067, 0.042)
                r <= 1.75-> MomentCoefficients(0.056, 0.033, 0.075, 0.043)
                else     -> MomentCoefficients(0.062, 0.034, 0.082, 0.044)
            }
            // حالة 4: حافتان طوليتان مثبتتان، حافتان قصيرتان مفصليتان (SFSF)
            longFixed && !shortFixed -> when {
                r <= 1.0 -> MomentCoefficients(0.048, 0.036, 0.064, 0.048)
                r <= 1.2 -> MomentCoefficients(0.055, 0.037, 0.073, 0.049)
                r <= 1.4 -> MomentCoefficients(0.061, 0.038, 0.081, 0.050)
                r <= 1.75-> MomentCoefficients(0.069, 0.039, 0.092, 0.051)
                else     -> MomentCoefficients(0.075, 0.040, 0.100, 0.052)
            }
            // حالة 5: حافتان قصيرتان مثبتتان، حافتان طوليتان مفصليتان (FSFS)
            !longFixed && shortFixed -> when {
                r <= 1.0 -> MomentCoefficients(0.036, 0.036, 0.048, 0.048)
                r <= 1.2 -> MomentCoefficients(0.040, 0.037, 0.053, 0.049)
                r <= 1.4 -> MomentCoefficients(0.044, 0.038, 0.059, 0.050)
                r <= 1.75-> MomentCoefficients(0.049, 0.039, 0.065, 0.051)
                else     -> MomentCoefficients(0.052, 0.040, 0.069, 0.052)
            }
            // حالة 6: حافتان متقابلتان مثبتة (قطري - FFSS)
            fixedCount == 2 -> when {
                r <= 1.0 -> MomentCoefficients(0.045, 0.030, 0.060, 0.000)
                r <= 1.2 -> MomentCoefficients(0.050, 0.031, 0.067, 0.000)
                r <= 1.4 -> MomentCoefficients(0.056, 0.032, 0.075, 0.000)
                r <= 1.75-> MomentCoefficients(0.063, 0.033, 0.083, 0.000)
                else     -> MomentCoefficients(0.069, 0.034, 0.092, 0.000)
            }
            // حالة 7: حافة واحدة مثبتة فقط
            fixedCount == 1 -> when {
                r <= 1.0 -> MomentCoefficients(0.058, 0.044, 0.077, 0.000)
                r <= 1.2 -> MomentCoefficients(0.065, 0.045, 0.087, 0.000)
                r <= 1.4 -> MomentCoefficients(0.072, 0.046, 0.096, 0.000)
                r <= 1.75-> MomentCoefficients(0.080, 0.047, 0.107, 0.000)
                else     -> MomentCoefficients(0.087, 0.048, 0.116, 0.000)
            }
            // حالة 8: كل الحافات مفصلية (SSSS) — لا توجد عزوم سالبة
            else -> when {
                r <= 1.0 -> MomentCoefficients(0.062, 0.062, 0.000, 0.000)
                r <= 1.2 -> MomentCoefficients(0.074, 0.061, 0.000, 0.000)
                r <= 1.4 -> MomentCoefficients(0.084, 0.059, 0.000, 0.000)
                r <= 1.75-> MomentCoefficients(0.093, 0.055, 0.000, 0.000)
                else     -> MomentCoefficients(0.099, 0.051, 0.000, 0.000)
            }
        }
    }

    override fun checkSlabThickness(
        span: Double,
        supportCondition: SupportCondition,
        fy: Double,
        isTwoWay: Boolean
    ): ThicknessCheckResult {
        InputGuard.positive("span", span)
        InputGuard.positive("fy", fy)

        val minThickness = getMinSlabThickness(span, supportCondition)
        val providedThickness = max(120.0, ceil(minThickness / 10) * 10) // افتراض سمك مبدئي
        
        val deflectionRatio = if (providedThickness > 0) minThickness / providedThickness else 2.0
        
        return ThicknessCheckResult(
            requiredThickness = minThickness,
            providedThickness = providedThickness,
            isSafe = providedThickness >= minThickness,
            deflectionRatio = deflectionRatio,
            recommendation = if (providedThickness < minThickness) 
                "Increase thickness to ${minThickness.toInt()} mm" 
                else "Thickness adequate"
        )
    }

    override fun getMinSlabThickness(span: Double, supportCondition: SupportCondition): Double {
        // حسب الكود المصري: نسب span/depth للبلاطات المصمتة
        val ratio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 25.0
            SupportCondition.CONTINUOUS -> 30.0
            SupportCondition.CANTILEVER -> 10.0
        }
        return max(span / ratio, 100.0)
    }

    override fun getMinReinforcementRatio(): Double = 0.0013 // 0.13% Ag
    override fun getMaxBarSpacing(): Double = 200.0
    override fun getMinCover(): Double = 20.0 // 20 مم للبلاطات المحمية
}
