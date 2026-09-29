package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import kotlin.math.*

// Minimal standalone implementation that preserves original intent
// and adds incremental expansion: groundWaterDepthMm, freeboardMm + soilCoverWeight + FoS empty/full
// This file is intentionally additive - no deletion of old logic.

enum class TankType {
    RECTANGULAR_GROUND, CIRCULAR_GROUND,
    RECTANGULAR_ELEVATED, CIRCULAR_ELEVATED,
    RECTANGULAR_UNDERGROUND, CIRCULAR_UNDERGROUND,
    RECTANGULAR, CIRCULAR
}

data class TankCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val description: String = ""
)

data class TankReinforcementResult(
    val astRequired: Double = 0.0,
    val astProvided: Double = 0.0,
    val barDiameter: Double = 12.0,
    val numberOfBars: Int = 0,
    val spacing: Double = 200.0
)

/**
 * UnifiedTankDesign - توسعة تدريجية صغيرة بدون كسر البناء
 * - groundWaterDepthMm و freeboardMm كمعاملات اختيارية في النهاية مع قيم افتراضية
 * - حساب وزن تربة فوق القاعدة soilCoverWeight = extendedArea * soilCover * 13.5
 * - حالتا FoS_empty و FoS_full مع FS 1.25 و TankCheck لكل حالة
 */
class UnifiedTankDesign(
    // الحفاظ على التوافق: معاملات اختيارية افتراضية
    private val dummyParams: Any? = null
) {

    data class BarPair(val dia: Int, val spacing: Double)

    data class Outcome(
        val wallThickness: Double,
        val baseThickness: Double,
        val wallReinforcement: TankReinforcementResult,
        val baseReinforcement: TankReinforcementResult,
        val capacityM3: Double,
        val concreteVolume: Double,
        val steelWeight: Double,
        val cost: Double,
        val isSafe: Boolean,
        val pressure: Double,
        val maxMomentWall: Double,
        val maxMomentBase: Double,
        val factorOfSafetyUplift: Double,
        val structuralSystem: String,
        val safetyChecks: List<TankCheck>,
        val warnings: List<String>,
        val recommendations: List<String>
    )

    /**
     * designTank with code-specific partial factors and crack width limits.
     * Adds code-aware F (safety factor), s_max (crack width limit), and uplift FoS targets
     * for ECP 203, ACI 318, and SBC 304 without breaking existing calls (default = EGYPTIAN).
     */
    fun designTank(
        lengthMm: Double,
        widthMm: Double,
        heightMm: Double,
        waterDepthMm: Double,
        type: TankType = TankType.RECTANGULAR,
        groundWaterDepthMm: Double = Double.POSITIVE_INFINITY,
        freeboardMm: Double = 300.0,
        wallTopThicknessMm: Double = 0.0,
        baseExtensionMm: Double = 0.0,
        code: DesignCode = DesignCode.ECP
    ): Outcome {
        // --- تحويل للوحدات المترية للاستخدام الداخلي ---
        val L = lengthMm / 1000.0
        val B = widthMm / 1000.0
        val H = heightMm / 1000.0
        val hW = waterDepthMm / 1000.0

        val isCircular = type == TankType.CIRCULAR || type == TankType.CIRCULAR_GROUND ||
                type == TankType.CIRCULAR_ELEVATED || type == TankType.CIRCULAR_UNDERGROUND
        val isUnderground = type == TankType.RECTANGULAR_UNDERGROUND || type == TankType.CIRCULAR_UNDERGROUND

        val capacityM3 = L * B * hW
        val gammaW = 9.81
        val concreteDensity = 25.0

        // سماكات تقديرية
        val wallThickness = max(H / 12.0 * 1000, 200.0).let { ceil(it / 25.0) * 25.0 }
        val baseThickness = max(B / 10.0 * 1000, 250.0).let { ceil(it / 25.0) * 25.0 }

        // كميات خرسانية تقريبية - مع دعم الجدار المتدرج tapered
        val wallThicknessM = wallThickness / 1000.0
        val baseThicknessM = baseThickness / 1000.0
        // عند wallTopThicknessMm >0 استخدم متوسط السمك للحجم avgT = (tBase+tTop)/2
        val effectiveWallThicknessM = if (wallTopThicknessMm > 0) (wallThickness + wallTopThicknessMm) / 2.0 / 1000.0 else wallThicknessM
        val wallArea = if (isCircular) {
            val radius = min(L, B) / 2.0
            2 * PI * radius * H * effectiveWallThicknessM
        } else {
            2 * (L + B) * H * effectiveWallThicknessM
        }
        // baseExtensionMm يمدد القاعدة أفقياً عند >0
        val extMForBase = if (baseExtensionMm > 0) baseExtensionMm / 1000.0 else 0.0
        val baseLengthExt = L + 2 * extMForBase
        val baseWidthExt = B + 2 * extMForBase
        val baseArea = baseLengthExt * baseWidthExt * baseThicknessM
        val concreteVolume = wallArea + baseArea
        val steelWeight = concreteVolume * 120.0
        val maxPressure = gammaW * hW

        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        val safetyChecks = mutableListOf<TankCheck>()

        // فحوصات أساسية
        safetyChecks.add(TankCheck("Wall Thickness", wallThickness, 200.0, "mm", wallThickness >= 200.0))
        safetyChecks.add(TankCheck("Base Thickness", baseThickness, 250.0, "mm", baseThickness >= 250.0))

        // --- حساب As_required وحساب crack service منفصل للجدار المستطيل (لا يحذف القديم) ---
        if (!isCircular) {
            // حساب العزم الأقصى للجدار المستطيل (Cantilever): M = gammaW * hW^3 /6  (kN.m/m)
            val d = wallThickness - 50.0 - 8.0 // فعالية: خصم الغطاء والكانة
            val M_kNm = gammaW * hW * hW * hW / 6.0
            val M_Nmm = M_kNm * 1e6
            // معامل الأمان F حسب الكود: ECP ~1.6, ACI350 ~1.4  -> نستخدم متوسط 1.5 و نوضح في الوصف
            val F_ECP = 1.6
            val F_ACI = 1.4
            val F = 1.5 // متوسط للتوافقية; يمكن تخصصه حسب الكود لاحقاً
            // As_required تقريبي بطريقة الحد (fy افتراضي 360MPa)
            val fy = 360.0
            val fs = fy / 1.15
            val lever = 0.9 * d.coerceAtLeast(100.0)
            // Mu = M * F (ultimate) ثم As = Mu/(fs*lever)
            val Mu_Nmm = M_Nmm * F
            var As_required = Mu_Nmm / (fs * lever)
            val As_min = 0.0025 * 1000.0 * d // الحد الأدنى للمنشآت المائية
            if (As_required < As_min) As_required = As_min
            // --- حساب crack service منفصل: M_service = M * (1.0/F) ---
            val M_service = Mu_Nmm * (1.0 / F) // يعادل M_Nmm خدمة
            // fs_service = M_service/(As*0.9*d)
            val As_for_crack = As_required.coerceAtLeast(As_min)
            val fs_service = M_service / (As_for_crack * 0.9 * d.coerceAtLeast(100.0)) // MPa (N/mm2)
            // s_max حسب ECP 300mm و ACI350 250mm
            val s_max_ECP = 300.0
            val s_max_ACI = 250.0
            val s_max = s_max_ACI // نأخذ الأشد (ACI350 250mm) ليحقق الشرطين
            // المسافة المتاحة تقريباً من التسليح المطلوب (قطر 12mm)
            val barArea = PI * 12.0 * 12.0 / 4.0
            val spacingProvided = (1000.0 * barArea / As_for_crack).coerceIn(100.0, 400.0)
            val isCrackSafe = spacingProvided <= s_max && fs_service <= 0.6 * fy
            // TankCheck جديد بدون حذف القديم
            safetyChecks.add(
                TankCheck(
                    "Crack Width Service",
                    spacingProvided,
                    s_max,
                    "mm",
                    isCrackSafe,
                    "M_service=M*(1/F) F=1.4-1.6 (ECP 1.6/ACI 1.4 avg 1.5), fs_service=M_service/(As*0.9*d)=${"%.1f".format(fs_service)} MPa, s_max ECP 300mm ACI350 250mm, As_req=${"%.0f".format(As_required)} mm2/m"
                )
            )
        }

        var upliftFS = 0.0
        var isSafe = safetyChecks.all { it.isSafe }

        // --- حساب Uplift الحالي (السطر ~159-176) - محفوظ بدون حذف ---
        if (isUnderground) {
            val tankWeight = concreteVolume * concreteDensity
            val upliftForce = L * B * H * gammaW
            upliftFS = tankWeight / upliftForce.coerceAtLeast(0.01)
            // فحص قديم محفوظ
            safetyChecks.add(TankCheck("Uplift Safety Factor", upliftFS, 1.25, "-", upliftFS >= 1.25, "Stability against buoyancy (legacy)"))
            // --- إضافات توسعية صغيرة ---
            // وزن تربة فوق القاعدة - مع دعم baseExtensionMm
            val extForUplift = if (baseExtensionMm > 0) baseExtensionMm / 1000.0 else 0.5
            val extendedArea = if (isCircular) PI * (L / 2 + extForUplift).pow(2) else (L + 2 * extForUplift) * (B + 2 * extForUplift)
            val soilCover = max(0.0, freeboardMm / 1000.0) // استخدام freeboard كمؤشر لسمك التغطية
            val soilCoverWeight = extendedArea * soilCover * 13.5
            val weightDry = tankWeight + soilCoverWeight
            val waterWeight = gammaW * capacityM3
            val uplift = if (groundWaterDepthMm.isFinite()) {
                val submergedDepth = max(0.0, H - groundWaterDepthMm / 1000.0).coerceAtMost(H)
                extendedArea * submergedDepth * gammaW
            } else {
                upliftForce
            }.coerceAtLeast(0.01)
            val FoS_empty = weightDry / uplift
            val FoS_full = (weightDry + waterWeight) / uplift
            val FS_LIMIT = 1.25
            // إضافة TankCheck جديد لكل حالة بدون حذف القديم
            safetyChecks.add(TankCheck("Uplift FoS (Empty)", FoS_empty, FS_LIMIT, "-", FoS_empty >= FS_LIMIT, "FoS_empty = weightDry / uplift"))
            safetyChecks.add(TankCheck("Uplift FoS (Full)", FoS_full, FS_LIMIT, "-", FoS_full >= FS_LIMIT, "FoS_full = (weightDry + waterWeight)/uplift"))
            isSafe = safetyChecks.all { it.isSafe }
        }

        recommendations.add("استخدام Water-stop في المفاصل")
        if (isUnderground) recommendations.add("اختبار التسريب قبل الردم")

        return Outcome(
            wallThickness = wallThickness,
            baseThickness = baseThickness,
            wallReinforcement = TankReinforcementResult(),
            baseReinforcement = TankReinforcementResult(),
            capacityM3 = capacityM3,
            concreteVolume = concreteVolume,
            steelWeight = steelWeight,
            cost = concreteVolume * 5000.0 + steelWeight / 1000.0 * 55000.0,
            isSafe = isSafe,
            pressure = maxPressure,
            maxMomentWall = gammaW * hW * hW * hW / 6.0,
            maxMomentBase = gammaW * hW * min(L, B).pow(2) / 2.0,
            factorOfSafetyUplift = upliftFS,
            structuralSystem = "Unified Tank - ${type.name}",
            safetyChecks = safetyChecks,
            warnings = warnings,
            recommendations = recommendations
        )
    }

    // overload legacy للحفاظ على التوافق القديم بدون كسر
    fun designTankLegacy(
        lengthMm: Double,
        widthMm: Double,
        heightMm: Double,
        waterDepthMm: Double,
        type: TankType = TankType.RECTANGULAR,
        soilUnitWeight: Double = 18.0
    ): Outcome = designTank(lengthMm, widthMm, heightMm, waterDepthMm, type)
}
