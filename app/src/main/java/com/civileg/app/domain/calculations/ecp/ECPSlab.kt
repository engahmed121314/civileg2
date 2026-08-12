package com.civileg.app.domain.calculations.ecp

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
        
        // ذراع القوة: z = d × (0.5 + √(0.25 - K/1.25)) حسب ECP 203
        val leverArm = if (0.25 - K / 1.25 > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / 1.25))
        } else {
            effectiveDepth * 0.7  // تقريب آمن للحالات الحرجة
        }
        
        // As = Mu / (fy/γs × z) حسب ECP 203
        var astRequired = if (fs > 0 && leverArm > 0) Mu / (fs * leverArm) else 0.0 // mm²/m
        
        // الحد الأدنى للتسليح للبلاطات (0.6/fy * b * d or 0.15% Ag)
        val minSteel = max(0.6 / fy.coerceAtLeast(1.0) * width * effectiveDepth, 0.0015 * width * slabThickness)
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
        val qcu = 0.24 * sqrt(fcu) / GAMMA_C  // MPa
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
        // معاملات العزم حسب حالة التثبيت
        val aspectRatio = longSpan / shortSpan.coerceAtLeast(1.0)
        val coefficients = calculateMomentCoefficients(aspectRatio, supportConditions)
        
        // العزم في الاتجاه القصير
        val MuShort = coefficients.positiveShort * totalLoad * shortSpan * shortSpan / 1000.0 // kN.m/m
        val resultShort = designOneWaySlab(
            fcu, fy, slabThickness, shortSpan, 
            MuShort, totalLoad * shortSpan / 2, loadCombination
        )
        
        // العزم في الاتجاه الطويل
        val MuLong = coefficients.positiveLong * totalLoad * longSpan * longSpan / 1000.0 // kN.m/m
        val resultLong = designOneWaySlab(
            fcu, fy, slabThickness, longSpan,
            MuLong, totalLoad * longSpan / 2, loadCombination
        )
        
        return TwoWaySlabResult(
            shortDirection = resultShort,
            longDirection = resultLong,
            momentCoefficients = coefficients,
            isSafe = resultShort.isSafe && resultLong.isSafe
        )
    }

    private fun calculateMomentCoefficients(
        aspectRatio: Double,
        supports: SlabSupportConditions
    ): MomentCoefficients {
        // جداول مبسطة من الكود المصري (قريبة من قيم ماركوس)
        val isAllFixed = supports.edgeA == EdgeCondition.FIXED && 
                        supports.edgeB == EdgeCondition.FIXED &&
                        supports.edgeC == EdgeCondition.FIXED &&
                        supports.edgeD == EdgeCondition.FIXED
        
        return if (isAllFixed) {
            when {
                aspectRatio <= 1.0 -> MomentCoefficients(0.031, 0.024, 0.031, 0.024)
                aspectRatio <= 1.2 -> MomentCoefficients(0.036, 0.027, 0.028, 0.021)
                aspectRatio <= 1.4 -> MomentCoefficients(0.040, 0.030, 0.025, 0.019)
                else -> MomentCoefficients(0.044, 0.033, 0.022, 0.017)
            }
        } else {
            when {
                aspectRatio <= 1.0 -> MomentCoefficients(0.048, 0.036, 0.048, 0.036)
                aspectRatio <= 1.2 -> MomentCoefficients(0.055, 0.041, 0.043, 0.032)
                aspectRatio <= 1.4 -> MomentCoefficients(0.061, 0.046, 0.038, 0.029)
                else -> MomentCoefficients(0.067, 0.050, 0.034, 0.026)
            }
        }
    }

    override fun checkSlabThickness(
        span: Double,
        supportCondition: SupportCondition,
        fy: Double,
        isTwoWay: Boolean
    ): ThicknessCheckResult {
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

    override fun getMinReinforcementRatio(): Double = 0.0015 // 0.15% Ag
    override fun getMaxBarSpacing(): Double = 200.0
    override fun getMinCover(): Double = 20.0 // 20 مم للبلاطات المحمية
}
