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
    ): SlabDesignResult = designSlabStrip(
        fcu, fy, slabThickness, clearSpan, designMoment, designShear,
        getMinSlabThickness(clearSpan, SupportCondition.SIMPLY_SUPPORTED)
    )

    private fun designSlabStrip(
        fcu: Double,
        fy: Double,
        slabThickness: Double,
        clearSpan: Double,
        designMoment: Double,
        designShear: Double,
        minThickness: Double
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
        // Single-reinforcement limit: K,max = Rmax/gamma_c from ECP 203-2025 Table 4-1 (p.4-6) and Eq 4-4
        // (replaces the old undocumented 0.9-factor "balanced K", which was unconservative).
        val K_bal = ECPBeam.kMax(fy)
        val K = if (fcu > 0 && effectiveDepth > 0) Mu / (fcu * width * effectiveDepth * effectiveDepth) else 0.0
        
        var overReinforced = false
        if (K > K_bal) {
            warnings.add(String.format("K=%.3f > Kmax=%.3f (ECP 203-2025 Table 4-1) - compression steel needed, increase slab thickness", K, K_bal))
            overReinforced = true
        }
        
        // ذراع القوة: z = d × (0.5 + √(0.25 - K/0.893)) حسب ECP 203 K-method
        // 0.893 = γc/(2×0.67) = 1.5/1.34
        val leverArm = min(0.95 * effectiveDepth, if (0.25 - K / 0.893 > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / 0.893))
        } else {
            effectiveDepth * 0.7  // over-reinforced: flagged unsafe below
        })  // z <= 0.95 d, ECP 203-2025 4-1-2-2-a (p.4-5)
        
        // As = Mu / (fy/γs × z) حسب ECP 203
        var astRequired = if (fs > 0 && leverArm > 0) Mu / (fs * leverArm) else 0.0 // mm²/m
        
        // Min. steel on gross area b x h: ECP 203-2025 6-2-1-2-3 (p.6-6): max(0.6/fy, 0.25 % mild | 0.15-0.18 % high grade)
        val minSteel = max(0.6 / fy, ECPBeam.muMinFloor(fcu, fy)) * width * slabThickness
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
        
        codeNotes.add(String.format("qcu = %.2f MPa (slab shear formula 0.24 sqrt(fcu/gc) is from the earlier edition; ECP 203-2025 4-2-1 slab shear NOT VERIFIED)", qcu))
        warnings.add("Slab shear check not verified against ECP 203-2025")
        
        // التحقق من السمك للانحراف
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
            isSafe = isShearSafe && slabThickness >= minThickness && !overReinforced,
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
        // ECP 203-2025 Eq 6-6a/b/c (p.6-7): t,min = a/35 (simple), a/40 (continuous one side), a/45 (two sides); a = short span.
        // Mapping of 4 edges: no continuous edge -> 35; some -> 40; all four -> 45 (conservative). Floor 80 mm static loads (p.6-4).
        val contEdges = listOf(supportConditions.edgeA, supportConditions.edgeB, supportConditions.edgeC, supportConditions.edgeD)
            .count { it == EdgeCondition.FIXED || it == EdgeCondition.CONTINUOUS }
        val twoWayDiv = when (contEdges) { 0 -> 35.0; 4 -> 45.0; else -> 40.0 }
        val tMinTwo = max(shortSpan / twoWayDiv, 80.0)
        val resultShort = designSlabStrip(
            fcu, fy, slabThickness, shortSpan,
            MuShort, totalLoad * shortSpan / 2000.0, tMinTwo
        )

        val MuLong = coefficients.positiveLong * totalLoad * longSpan * longSpan / 1_000_000.0 // kN.m/m
        val resultLong = designSlabStrip(
            fcu, fy, slabThickness, longSpan,
            MuLong, totalLoad * longSpan / 2000.0, tMinTwo
        )
        
        return TwoWaySlabResult(
            shortDirection = resultShort,
            longDirection = resultLong,
            momentCoefficients = coefficients,
            isSafe = resultShort.isSafe && resultLong.isSafe
        )
    }

    /**
     * معاملات عزم الانحناء للبلاطة المصمتة ثنائية الاتجاه حسب ECP 203-2020
     * البند ٦-١-٢-٦-٣: «طريقة مبسطة لحساب عزوم الانحناء في البلاطات المصمتة ذات الاتجاهين
     * المعرضة لأحمال منتظمة التوزيع» — جدول (١-٦) والمعادلات [6-7a/b/c] و[6-8].
     *
     *   M_a = α·w·a²/d   ،   M_b = β·w·b²/d
     *
     * d = 8 للبحر بسيط الارتكاز من الناحيتين، 10 للمستمر من ناحية واحدة، 12 للمستمر من الناحيتين.
     * α = 0.5r − 0.15  ،  β = 0.35/r²   (معادلة 6-8، مطابقة لجدول 6-1، r = b/a ≤ 2)
     *
     * حدود الطريقة (نفس البند + البند ٦-٢-١-٦-١): بلاطات مستطيلة مرتكزة على جوانبها الأربعة
     * ومصبوبة ميليثيا مع الكمرات، r ≤ 2 (لـ r > 2 تُصمم كبلاطة أحادية الاتجاه)،
     * حمل حي ≤ 5 كيلونيوتن/م² (للأحمال الأكبر: جدول ٦-٣ — غير منفذ بعد).
     * البلاطات المرتكزة على حوائط مباني تستخدم جدول ٦-١(٢أ) — غير منفذ بعد.
     * العزوم السالبة بين البلاطات المتجاورة تُحسب بمعادلة 6-9 (خارج نطاق حاسبة اللوحة المفردة).
     * مرجعية التوثيق: docs/CLAUSE_LOG.md بنود TW-01 إلى TW-05 (متحقق منها من PDF الكود).
     */
    private fun calculateMomentCoefficients(
        aspectRatio: Double,
        supports: SlabSupportConditions
    ): MomentCoefficients {
        val r = aspectRatio.coerceAtLeast(1.0).coerceAtMost(2.0)
        val alpha = 0.5 * r - 0.15   // معادلة 6-8 = جدول 6-1
        val beta = 0.35 / (r * r)    // معادلة 6-8

        fun isContinuousEdge(e: EdgeCondition) =
            e == EdgeCondition.FIXED || e == EdgeCondition.CONTINUOUS

        fun spanDenominator(edge1: EdgeCondition, edge2: EdgeCondition): Double =
            when (listOf(edge1, edge2).count(::isContinuousEdge)) {
                2 -> 12.0  // مستمر من الناحيتين
                1 -> 10.0  // مستمر من ناحية واحدة
                else -> 8.0 // بسيط الارتكاز من الناحيتين
            }

        // الاتجاه القصير (a) يحدّه الحافتان الطويلتان A وC؛ الاتجاه الطويل (b) تحدّه القصيرتان B وD
        val dShort = spanDenominator(supports.edgeA, supports.edgeC)
        val dLong = spanDenominator(supports.edgeB, supports.edgeD)

        val positiveShort = alpha / dShort
        val positiveLong = beta / dLong

        // العزوم السالبة عند الاستمرار: بقيمة العزم المحسوب للبحر المستمر (للوحة مفردة؛
        // بين البلاطات المتجاورة تُطبق معادلة 6-9 خارج هذا النطاق)
        val negativeShort = if (dShort == 8.0) 0.0 else positiveShort
        val negativeLong = if (dLong == 8.0) 0.0 else positiveLong

        return MomentCoefficients(
            negativeShort = negativeShort,
            positiveShort = positiveShort,
            negativeLong = negativeLong,
            positiveLong = positiveLong
        )
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
        // ECP 203-2025 6-2-1-2-1 (p.6-4): one-way t,min = L/30 simple, L/35 continuous one end, L/40 continuous both ends;
        // not less than 80 mm (static loads). CONTINUOUS has no end-count here, so L/35 is used (conservative vs L/40).
        // CANTILEVER: clause not read, the earlier L/10 value is unsourced (NOT ASSESSED by the code).
        val ratio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 30.0
            SupportCondition.CONTINUOUS -> 35.0
            SupportCondition.CANTILEVER -> 10.0
        }
        return max(span / ratio, 80.0)
    }

    override fun getMinReinforcementRatio(): Double = 0.0013 // 0.13% Ag
    override fun getMaxBarSpacing(): Double = 200.0
    override fun getMinCover(): Double = 20.0 // 20 مم للبلاطات المحمية
}
