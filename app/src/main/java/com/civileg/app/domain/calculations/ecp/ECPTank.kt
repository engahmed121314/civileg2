package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.TankDesign
import com.civileg.app.domain.calculations.base.TankResult
import com.civileg.app.domain.calculations.base.TankSafetyCheck
import com.civileg.app.domain.calculations.base.TankType
import com.civileg.app.domain.entities.ReinforcementResult
import kotlin.math.*
import kotlin.collections.mutableListOf

/**
 * تصميم خزانات المياه حسب الكود المصري ECP 203
 * يغطي الخزانات المستطيلة والدائرية (أرضية، مرتفعة، تحت الأرض)
 */
class ECPTank : TankDesign {

    companion object {
        private const val GAMMA_C = 1.5       // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15      // معامل أمان الحديد
        private const val GAMMA_W = 9.81      // وزن وحدة الحجم للماء kN/m³
        private const val CONCRETE_DENSITY = 25.0 // كثافة الخرسانة kN/m³
        private const val MIN_COVER = 50.0    // غطاء أدنى للمنشآت المائية mm
        private const val MIN_WALL_THICKNESS = 200.0 // سمك أدنى للجدران mm
        private const val MIN_BASE_THICKNESS = 250.0 // سمك أدنى للقاعدة mm
        private const val CRACK_WIDTH_LIMIT = 0.2 // أقصى عرض شق mm
        private const val MIN_RHO_WATER = 0.0025 // نسبة تسليح أدنى للمنشآت المائية
    }

    override fun calculateTank(
        length: Double,     // mm
        width: Double,      // mm
        height: Double,     // mm
        waterDepth: Double, // mm
        fcu: Double,
        fy: Double,
        type: TankType
    ): TankResult {
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        val safetyChecks = mutableListOf<TankSafetyCheck>()
        val codeNotes = mutableListOf<String>()

        // تحويل إلى متر للنمذجة الإنشائية
        val L = length / 1000.0   // m
        val B = width / 1000.0    // m
        val H = height / 1000.0   // m
        val hW = waterDepth / 1000.0 // m

        // 1. حساب السعة
        val capacityM3 = L * B * hW

        // 2. اختيار سمك الجدار حسب الارتفاع
        val wallThickness = max(H / 12.0 * 1000, MIN_WALL_THICKNESS).let {
            ceil(it / 25.0) * 25.0 // التقريب لأقرب 25mm
        }
        val baseThickness = max(B / 10.0 * 1000, MIN_BASE_THICKNESS).let {
            ceil(it / 25.0) * 25.0
        }

        // 3. حساب الإجهادات الهيدروستاتيكية
        val maxPressure = GAMMA_W * hW // kN/m² عند القاعدة

        // 4. تصميم الجدران حسب النوع
        val isCircular = type == TankType.CIRCULAR || type == TankType.CIRCULAR_GROUND ||
                type == TankType.CIRCULAR_ELEVATED || type == TankType.CIRCULAR_UNDERGROUND
        val isUnderground = type == TankType.RECTANGULAR_UNDERGROUND || type == TankType.CIRCULAR_UNDERGROUND

        val wallDesignResult = if (isCircular) {
            designCircularWall(L, B, H, hW, fcu, fy, wallThickness, warnings, codeNotes)
        } else {
            designRectangularWall(L, B, H, hW, fcu, fy, wallThickness, warnings, codeNotes, safetyChecks)
        }

        // 5. تصميم القاعدة
        val baseDesignResult = designBase(
            L, B, H, hW, fcu, fy, baseThickness, wallThickness,
            isCircular, isUnderground, warnings, codeNotes, safetyChecks
        )

        // 6. حساب الكميات
        val wallThicknessM = wallThickness / 1000.0
        val baseThicknessM = baseThickness / 1000.0
        val wallArea = if (isCircular) {
            val radius = min(L, B) / 2.0
            2 * PI * radius * H * wallThicknessM
        } else {
            2 * (L + B) * H * wallThicknessM
        }
        val baseArea = L * B * baseThicknessM
        val concreteVolume = wallArea + baseArea // m³
        val steelWeightKgPerM3 = 120.0 // kg/m³ متوسط للخزانات
        val steelWeight = concreteVolume * steelWeightKgPerM3 // kg
        val concreteCostPerM3 = 5000.0
        val steelCostPerTon = 55000.0
        val cost = concreteVolume * concreteCostPerM3 + (steelWeight / 1000.0) * steelCostPerTon

        // 7. فحص السلامة
        val isSafe = safetyChecks.all { it.isSafe }

        // 8. التحقق من الرفع للخزانات تحت الأرض
        var upliftFS = 0.0
        if (isUnderground) {
            val tankWeight = concreteVolume * CONCRETE_DENSITY
            val upliftForce = L * B * H * GAMMA_W
            upliftFS = tankWeight / upliftForce
            safetyChecks.add(TankSafetyCheck(
                "Uplift Safety Factor", upliftFS, 1.25, "-",
                upliftFS >= 1.25, "Stability against buoyancy (ECP 203 Sec. 8-1)"
            ))
        }

        // 9. التوصيات
        recommendations.add("استخدام SBR أو Water-stop في المفاصل الإنشائية")
        recommendations.add("الغطاء الأدنى: 50mm (جانب الماء)، 25mm (الجانب الخارجي)")
        recommendations.add("معالجة بمياه نظيفة لمدة 7 أيام على الأقل")
        recommendations.add("اختبار التسريب قبل الردم (للخزانات تحت الأرض)")
        if (isUnderground) recommendations.add("توفير طبقة ردم نظيفة حول الخزان")
        if (wallThickness > 350) recommendations.add("اعتبار استخدام سائل إضافي بلاستيكي (Superplasticizer) للقذف")

        return TankResult(
            wallThickness = wallThickness,
            baseThickness = baseThickness,
            wallReinforcement = wallDesignResult,
            baseReinforcement = baseDesignResult,
            capacityM3 = capacityM3,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            cost = cost,
            isSafe = isSafe,
            pressure = maxPressure,
            maxMomentWall = if (isCircular) GAMMA_W * hW * hW * hW / 15.0 else GAMMA_W * hW * hW * hW / 6.0,
            maxMomentBase = { val baseSelfWeight = baseThickness / 1000.0 * CONCRETE_DENSITY; val waterPressureOnBase = GAMMA_W * hW; val totalPressure = waterPressureOnBase + baseSelfWeight; val projection = if (isCircular) min(L, B) / 2.0 else min(L, B) / 2.0 - wallThickness / 2000.0; totalPressure * projection * projection / 2.0 }(),
            maxShearWall = GAMMA_W * hW * hW / 2.0,
            factorOfSafetyUplift = upliftFS,
            structuralSystem = when(type) {
                TankType.RECTANGULAR_GROUND -> "Ground Rectangular - Cantilever Wall Analysis"
                TankType.CIRCULAR_GROUND -> "Ground Circular - Hoop Tension Analysis"
                TankType.RECTANGULAR_ELEVATED -> "Elevated Rectangular - Cantilever Wall"
                TankType.CIRCULAR_ELEVATED -> "Elevated Circular - Hoop Tension"
                TankType.RECTANGULAR_UNDERGROUND -> "Underground Rectangular - Soil + Hydrostatic"
                TankType.CIRCULAR_UNDERGROUND -> "Underground Circular - Soil + Hydrostatic"
                TankType.RECTANGULAR -> "Rectangular Tank - Cantilever Method"
                TankType.CIRCULAR -> "Circular Tank - Hoop Tension Method"
            },
            recommendations = recommendations,
            safetyChecks = safetyChecks,
            warnings = warnings
        )
    }

    /**
     * تصميم جدار مستطيل كإطار مرتكز (Cantilever Wall)
     * العزم عند القاعدة: M = γw × h³ / 6
     * القص عند القاعدة: V = γw × h² / 2
     */
    private fun designRectangularWall(
        L: Double, B: Double, H: Double, hW: Double,
        fcu: Double, fy: Double, wallThickness: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankSafetyCheck>
    ): ReinforcementResult {
        // حساب الأبعاد الفعالة
        val d = wallThickness - MIN_COVER - 8.0 // فعالية (خصم الغطاء + كانة)
        val b = 1000.0 // عرض الوحدة = 1m

        // العزم الأقصى عند القاعدة (جدار مرتكز من أسفل)
        // M = γw × h³ / 6 (kN.m/m)
        val maxMoment = GAMMA_W * hW * hW * hW / 6.0

        // القص الأقصى عند القاعدة
        val maxShear = GAMMA_W * hW * hW / 2.0

        // تصميم الانحناء بطريقة K حسب ECP 203 البند 4-2-2-1
        val fs = fy / GAMMA_S

        val Mu = maxMoment * 1.15 // معامل تحميل للماء = 1.15 (بعض المراجع تستخدم 1.2)
        val Mu_Nmm = Mu * 1e6

        // معامل K
        // K-method حسب ECP 203 البند 4-2-2-1
        // K = Mu / (fcu * b * d^2)
        val K = Mu_Nmm / (fcu * b * d * d)
        val K_bal = 0.186

        if (K > K_bal) {
            warnings.add("المقطع مفرط التسليح - يُنصح بزيادة سمك الجدار")
        }

        // ذراع العزم: z = d * (0.5 + sqrt(0.25 - K/1.25)) حسب ECP 203
        val leverArm = if (0.25 - K / 1.25 > 0) {
            d * (0.5 + sqrt(0.25 - K / 1.25))
        } else {
            d * 0.7
        }

        // مساحة التسليح المطلوبة
        var asRequired = Mu_Nmm / (fs * leverArm)

        // الحد الأدنى للمنشآت المائية
        val asMin = MIN_RHO_WATER * b * d
        if (asRequired < asMin) {
            asRequired = asMin
            codeNotes.add("تم تطبيق التسليح الأدنى للمنشآت المائية (0.25%)")
        }

        // اختيار السيخ والتوزيع
        val barDiameter = selectBarDiameter(asRequired, wallThickness)
        val barArea = PI * barDiameter * barDiameter / 4
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(7, 20)
        val spacing = (1000.0 / barsPerMeter).let { ceil(it / 10.0) * 10.0 }
        val asProvided = (1000.0 / spacing) * barArea

        // التسليح الأفقي (تقريبي: 25-50% من العمودي)
        val asHorizontal = asProvided * 0.35
        val hBarDia = selectBarDiameter(asHorizontal, wallThickness)
        val hBarsPerMeter = ceil(asHorizontal / (PI * hBarDia * hBarDia / 4)).toInt().coerceIn(6, 16)
        val hSpacing = (1000.0 / hBarsPerMeter).let { ceil(it / 10.0) * 10.0 }

        // فحص القص
        val qcu = 0.24 * sqrt(fcu / GAMMA_C)
        val concreteShearCapacity = qcu * b * d / 1000.0
        val shearCheck = maxShear <= concreteShearCapacity
        safetyChecks.add(TankSafetyCheck(
            "Wall Shear Capacity", maxShear, concreteShearCapacity, "kN/m",
            shearCheck, "ECP 203: V = γw×h²/2 vs Vc = 0.24×√(fcu/γc)×b×d"
        ))

        // فحص عرض الشق
        val steelStress = Mu * 1e6 / (asProvided * leverArm)
        val crackWidth = 0.0001 * steelStress * max(2.0, (wallThickness - MIN_COVER) / barDiameter)
        safetyChecks.add(TankSafetyCheck(
            "Crack Width", crackWidth, CRACK_WIDTH_LIMIT, "mm",
            crackWidth <= CRACK_WIDTH_LIMIT, "ECP 203 Sec. 8: Max crack width 0.2mm for water-retaining structures"
        ))

        // فحص نسبة التسليح
        val rho = asProvided / (b * d)
        safetyChecks.add(TankSafetyCheck(
            "Wall Reinforcement Ratio", rho, MIN_RHO_WATER, "-",
            rho >= MIN_RHO_WATER, "Minimum reinforcement for water-retaining structures: 0.25%"
        ))

        codeNotes.add("ECP 203-2020: Section 8-1 (Water-Retaining Structures)")
        codeNotes.add("Wall: Cantilever method, M = γw×h³/6")
        codeNotes.add("Vertical: ${barsPerMeter}Ø${barDiameter.toInt()} @ ${spacing.toInt()}mm")
        codeNotes.add("Horizontal: ${hBarsPerMeter}Ø${hBarDia.toInt()} @ ${hSpacing.toInt()}mm")

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = barsPerMeter,
            tiesDiameter = hBarDia,
            tiesSpacing = hSpacing,
            isSafe = shearCheck && crackWidth <= CRACK_WIDTH_LIMIT && rho >= MIN_RHO_WATER,
            utilizationRatio = asRequired / asProvided,
            spacing = spacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = "V: ${barsPerMeter}Ø${barDiameter.toInt()}@${spacing.toInt()}mm, H: ${hBarsPerMeter}Ø${hBarDia.toInt()}@${hSpacing.toInt()}mm"
        )
    }

    /**
     * تصميم جدار دائري بطريقة شد الحلقة (Hoop Tension)
     * T = γw × h × R عند كل ارتفاع h
     * أقصى شد عند القاعدة: T_max = γw × H × R
     */
    private fun designCircularWall(
        L: Double, B: Double, H: Double, hW: Double,
        fcu: Double, fy: Double, wallThickness: Double,
        warnings: MutableList<String>, codeNotes: MutableList<String>
    ): ReinforcementResult {
        val radius = min(L, B) / 2.0
        val d = wallThickness - MIN_COVER - 8.0
        val b = 1000.0 // 1m strip

        // أقصى إجهاد شد حلقي عند القاعدة
        // T = γw × H × R (kN/m)
        val maxHoopTension = GAMMA_W * hW * radius

        // أقصى عزم انحناء (بسبب عدم التماثل الناتج عن الرياح/التحميل)
        // M = γw × H³ / 15 (للجدران الدائرية المثبتة من الأسفل)
        val maxMoment = GAMMA_W * hW * hW * hW / 15.0

        // تصميم التسليح الحلقي
        val fs = fy / GAMMA_S
        var asHoopRequired = maxHoopTension * 1000.0 / fs // mm²/m

        // تصميم التسليح العمودي (للانحناء) - K-method حسب ECP 203
        val Mu_Nmm = maxMoment * 1.15 * 1e6
        // K-method حسب ECP 203
        val K = Mu_Nmm / (fcu * b * d * d)
        val leverArm = if (0.25 - K / 1.25 > 0) {
            d * (0.5 + sqrt(0.25 - K / 1.25))
        } else {
            d * 0.7
        }
        var asVerticalRequired = Mu_Nmm / (fs * leverArm)

        // الحد الأدنى
        val asMin = MIN_RHO_WATER * b * d
        asHoopRequired = max(asHoopRequired, asMin)
        asVerticalRequired = max(asVerticalRequired, asMin * 0.6)

        // اختيار الأسياخ الحلقية
        val hoopBarDia = selectBarDiameter(asHoopRequired, wallThickness)
        val hoopBarArea = PI * hoopBarDia * hoopBarDia / 4
        val hoopBarsPerMeter = ceil(asHoopRequired / hoopBarArea).toInt().coerceIn(7, 20)
        val hoopSpacing = (1000.0 / hoopBarsPerMeter).let { ceil(it / 10.0) * 10.0 }
        val asHoopProvided = (1000.0 / hoopSpacing) * hoopBarArea

        // اختيار الأسياخ العمودية
        val vertBarDia = selectBarDiameter(asVerticalRequired, wallThickness)
        val vertBarArea = PI * vertBarDia * vertBarDia / 4
        val vertBarsPerMeter = ceil(asVerticalRequired / vertBarArea).toInt().coerceIn(6, 16)
        val vertSpacing = (1000.0 / vertBarsPerMeter).let { ceil(it / 10.0) * 10.0 }

        // فحص الشق
        val hoopStress = maxHoopTension / (wallThickness / 1000.0)
        val fct = 0.6 * sqrt(fcu) / 1.7
        val isCrackSafe = hoopStress <= fct

        codeNotes.add("ECP 203-2020: Section 8-1 (Circular Tank - Hoop Tension)")
        codeNotes.add("Max Hoop Tension T = γw×H×R = ${String.format("%.1f", maxHoopTension)} kN/m")
        codeNotes.add("Hoop: ${hoopBarsPerMeter}Ø${hoopBarDia.toInt()} @ ${hoopSpacing.toInt()}mm")
        codeNotes.add("Vertical: ${vertBarsPerMeter}Ø${vertBarDia.toInt()} @ ${vertSpacing.toInt()}mm")

        if (!isCrackSafe) {
            warnings.add("يجب زيادة سمك الجدار أو تقليل المسافات للتحكم في الشقوق")
        }

        return ReinforcementResult(
            astRequired = asHoopRequired,
            astProvided = asHoopProvided,
            barDiameter = hoopBarDia,
            numberOfBars = hoopBarsPerMeter,
            tiesDiameter = vertBarDia,
            tiesSpacing = vertSpacing,
            isSafe = isCrackSafe,
            utilizationRatio = asHoopRequired / asHoopProvided,
            spacing = hoopSpacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = "Hoop: ${hoopBarsPerMeter}Ø${hoopBarDia.toInt()}@${hoopSpacing.toInt()}mm, Vert: ${vertBarsPerMeter}Ø${vertBarDia.toInt()}@${vertSpacing.toInt()}mm"
        )
    }

    /**
     * تصميم قاعدة الخزان
     * للقواعد المستطيلة: تصميم كبلاطة باتجاه واحد (ناتئ من الوجه الداخلي للجدار)
     * للقواعد الدائرية: تصميم كبلاطة دائرية
     */
    private fun designBase(
        L: Double, B: Double, H: Double, hW: Double,
        fcu: Double, fy: Double, baseThickness: Double, wallThickness: Double,
        isCircular: Boolean, isUnderground: Boolean,
        warnings: MutableList<String>, codeNotes: MutableList<String>,
        safetyChecks: MutableList<TankSafetyCheck>
    ): ReinforcementResult {
        val d = baseThickness - MIN_COVER - 8.0
        val b = 1000.0

        // حمولة القاعدة:
        // 1) وزن الماء + وزن القاعدة الذاتي (للخزانات الأرضية)
        // 2) ضغط التربة + ضغط المياه الجوفية (للخزانات تحت الأرض)
        val baseSelfWeight = baseThickness / 1000.0 * CONCRETE_DENSITY
        val waterPressureOnBase = GAMMA_W * hW
        val totalPressure = waterPressureOnBase + baseSelfWeight

        // العزم التصميمي: ناتئ من الوجه الداخلي للجدار
        //projection length (from wall face to center of base)
        val projection = if (isCircular) {
            min(L, B) / 2.0
        } else {
            min(L, B) / 2.0 - wallThickness / 2000.0
        }
        val maxMomentBase = totalPressure * projection * projection / 2.0

        // تصميم الانحناء بطريقة K حسب ECP 203 البند 4-2-2-1
        val fs = fy / GAMMA_S
        val Mu_Nmm = maxMomentBase * 1.15 * 1e6

        // K-method حسب ECP 203 البند 4-2-2-1
        val K = Mu_Nmm / (fcu * b * d * d)
        val leverArm = if (0.25 - K / 1.25 > 0) {
            d * (0.5 + sqrt(0.25 - K / 1.25))
        } else {
            d * 0.7
        }

        var asRequired = Mu_Nmm / (fs * leverArm)
        val asMin = MIN_RHO_WATER * b * d
        if (asRequired < asMin) {
            asRequired = asMin
            codeNotes.add("تم تطبيق التسليح الأدنى للقاعدة (0.25%)")
        }

        // اختيار التسليح
        val barDiameter = selectBarDiameter(asRequired, baseThickness)
        val barArea = PI * barDiameter * barDiameter / 4
        val barsPerMeter = ceil(asRequired / barArea).toInt().coerceIn(6, 20)
        val spacing = (1000.0 / barsPerMeter).let { ceil(it / 10.0) * 10.0 }
        val asProvided = (1000.0 / spacing) * barArea

        // فحص قص الاختراق
        val punchingPerimeter = if (isCircular) {
            2 * PI * (wallThickness / 1000.0 + d / 1000.0)
        } else {
            2 * (wallThickness / 1000.0 + d / 1000.0 + wallThickness / 1000.0 + d / 1000.0)
        }
        val punchingShearCapacity = 0.316 * sqrt(fcu / GAMMA_C) * punchingPerimeter * d / 1000.0 // kN
        val punchingLoad = totalPressure * L * B // kN
        val punchingCheck = punchingLoad * 0.5 <= punchingShearCapacity // تقريبي

        safetyChecks.add(TankSafetyCheck(
            "Punching Shear (Base)", punchingLoad * 0.5, punchingShearCapacity, "kN",
            punchingCheck, "ECP 203: qp = 0.316×√(fcu/γc)×b₀×d"
        ))

        codeNotes.add("Base: ${barsPerMeter}Ø${barDiameter.toInt()} @ ${spacing.toInt()}mm (each way)")

        return ReinforcementResult(
            astRequired = asRequired,
            astProvided = asProvided,
            barDiameter = barDiameter,
            numberOfBars = barsPerMeter,
            tiesDiameter = barDiameter, // same both ways
            tiesSpacing = spacing,
            isSafe = punchingCheck,
            utilizationRatio = asRequired / asProvided,
            spacing = spacing,
            warnings = warnings,
            codeNotes = codeNotes,
            description = "${barsPerMeter}Ø${barDiameter.toInt()} @ ${spacing.toInt()}mm (each way)"
        )
    }

    /**
     * اختيار قطر السيخ المناسب
     */
    private fun selectBarDiameter(asRequired: Double, memberThickness: Double): Double {
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val maxBarsPerMeter = (1000.0 / (min(25.0, memberThickness / 10.0))).toInt().coerceAtMost(25)
        return availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4
            ceil(asRequired / area).toInt() <= maxBarsPerMeter
        } ?: 16.0
    }
}
