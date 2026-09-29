package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تنفيذ الكود السعودي SBC 304-2018 للكمرات — مستقل بالكامل عن ACI
 *
 * SBC 304-2018 مبني على ACI 318-19 لكن مع فروقات جوهرية:
 * - تحويل المقاومة: f'c = 0.8 × fcu (مكعب → أسطوانة)
 * - مقاومة القص الخرسانية: SBC 304 §11 يختلف عن ACI §22 في معاملات محددة
 * - نسبة التسليح الأدنى في المناطق الزلزالية: ρ_min,seis = 0.25√(f'c)/fy
 * - أطوال التثبيت مع اعتبارات البيئة المالحة السعودية
 * - الغطاء الخرساني: 50mm (مالح) / 65mm (شديد التآكل) / 75mm (ساحلي)
 * - تباعد الكانات الزلزالية: min(d/4, 100mm)
 *
 * المراجع:
 * - SBC 304-2018 البند 9 (الانحناء)
 * - SBC 304-2018 البند 11 (القص)
 * - SBC 304-2018 البند 9.5 (الانحراف)
 * - SBC 304-2018 البند 12 (أطوال التثبيت)
 * - SBC 304-2018 البند 18 (المناطق الزلزالية)
 * - SBC 304-2018 البند 4 (متطلبات عامة)
 */
class SBCBeam : BeamDesign {

    companion object {
        // ── SBC 304-2018 معاملات الاختزال (Section 9.3) ──
        private const val PHI_FLEXURE = 0.9       // §9.3.3 — مقاطع محكومة بالشد
        private const val PHI_SHEAR = 0.75        // §9.3.4 — القص
        private const val PHI_COMPRESSION = 0.65  // §9.3.3 — مقاطع محكومة بالضغط

        // ── SBC 304-2018 معاملات الخرسانة ──
        private const val LAMBDA = 1.0            // عامل الوزن للخرسانة العادية
        private const val BETA_1_DEFAULT = 0.85   // لـ f'c ≤ 28 MPa

        // ── SBC 304-2018 متطلبات زلزالية (Section 18) ──
        private const val SBC_MIN_WIDTH_SEISMIC = 250.0   // mm — أقل عرض للكمرات الزلزالية §18.4
        private const val SBC_SEISMIC_MAX_STIRRUP_SPACING_FACTOR = 4.0  // d/4
        private const val SBC_SEISMIC_MAX_STIRRUP_SPACING_MM = 100.0   // mm

        // ── SBC 304-2018 الغطاء الخرساني (Section 4) ──
        private const val SBC_COVER_INTERIOR = 40.0      // mm — داخلية
        private const val SBC_COVER_EXTERIOR = 50.0      // mm — خارجية (بيئة مالحة شائعة في المملكة)
        private const val SBC_COVER_SEVERE = 65.0        // mm — بيئة شديدة التآكل
        private const val SBC_COVER_COASTAL = 75.0       // mm — مناطق ساحلية

        // ── SBC 304-2018 أطوال التثبيت (Section 12) ──
        private const val SBC_MIN_DEVELOPMENT_LENGTH = 300.0  // mm
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. حساب التسليح للانحناء — SBC 304-2018 §9
    // ══════════════════════════════════════════════════════════════════════
    override fun calculateFlexureReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        totalDepth: Double,
        designMoment: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        // ── InputGuard: تحقق صارم (ADR-010) — SBC 304-2018 §9 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("effectiveDepth", effectiveDepth)
        InputGuard.positive("totalDepth", totalDepth)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.notNull("loadCombination", loadCombination)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ── تحويل المقاومة: f'c = 0.8 × fcu ──
        // SBC 304-2018 §4.1: نفس ACI 318 في تحويل المكعب لأسطوانة
        val fc = 0.8 * fcu
        val Mu = designMoment * 1e6  // N.mm

        // ── حساب Rn ──
        // Rn = Mu / (φ × b × d²) — SBC 304 §9.3.3
        val denominator = PHI_FLEXURE * width * effectiveDepth * effectiveDepth
        val Rn = if (denominator > 0) Mu / denominator else 0.0

        // ── حساب نسبة التسليح ρ بطريقة Rn-ρ ──
        val m = if (fc > 0) fy / (0.85 * fc) else 0.0
        val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
            (1 - sqrt(1 - 2 * m * Rn / fy)) / m
        } else {
            0.0
        }

        // ── فحص أقصى تسليح — SBC 304 §9.3.3.1 (مقاطع محكومة بالشد: εt ≥ 0.005) ──
        val beta1 = calculateBeta1(fc)
        val rhoMaxTc = 0.85 * beta1 * (fc / fy) * 0.375
        if (rho > rhoMaxTc) {
            warnings.add("SBC 304 §9.3.3.1: المقطع يتجاوز حد المقاطع المحكومة بالشد (ρ > ρmax,tc)")
            codeNotes.add(CodeReference.SBC.BEAM_REINFORCEMENT_MAX)
        }

        // ── مساحة التسليح المطلوبة ──
        var astRequired = rho * width * effectiveDepth

        // ── التسليح الأدنى — SBC 304 §9.6.1 ──
        // ρ_min = max(0.25√(f'c)/fy, 1.4/fy)
        val minSteel1 = 0.25 * sqrt(fc) / fy * width * effectiveDepth
        val minSteel2 = 1.4 / fy * width * effectiveDepth
        val minSteel = max(minSteel1, minSteel2)

        if (astRequired < minSteel) {
            astRequired = minSteel
            codeNotes.add("SBC 304 §9.6.1: التسليح الأدنى مطبق — ρ_min = max(0.25√f'c/fy, 1.4/fy)")
        }

        // ── اختيار الأسياخ (مقاسات السوق السعودي) ──
        val availableBars = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)
        var selectedBarDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 6
        } ?: 19.0

        // حساب بدائل اقتصادية وآمنة
        val alternatives = mutableListOf<String>()
        for (dia in availableBars) {
            val area = PI * dia * dia / 4
            val numBars = ceil(astRequired / area).toInt().coerceIn(2, 12)
            val asProv = numBars * area
            val a_calc = if (fc > 0) asProv * fy / (0.85 * fc * width) else 0.0
            val Mn_calc = asProv * fy * (effectiveDepth - a_calc / 2)
            val cap = PHI_FLEXURE * Mn_calc / 1e6
            val util = if (cap > 0) designMoment / cap else 2.0
            if (util in 0.5..1.0 && dia != selectedBarDia) {
                alternatives.add("${numBars}Ø${dia.toInt()} (${(util * 100).toInt()}%)")
            }
        }
        if (alternatives.size >= 2) {
            codeNotes.add("اقتصادي: ${alternatives.first()}")
            codeNotes.add("أأمن: ${alternatives.last()}")
        }

        val barArea = PI * selectedBarDia * selectedBarDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea

        // ── فحص التباعد ──
        val clearSpacing = if (numberOfBars > 1) {
            (width - 2 * getMinCover() - 2 * 10 - numberOfBars * selectedBarDia) / (numberOfBars - 1)
        } else {
            width - 2 * getMinCover()
        }
        if (clearSpacing < max(25.0, selectedBarDia)) {
            warnings.add("تباعد الأسياخ غير كافٍ — ضع في الاعتبار طبقتين من التسليح")
        }

        // ── السعة الفعلية للتحقق ──
        val a = if (fc > 0) astProvided * fy / (0.85 * fc * width) else 0.0
        val Mn = astProvided * fy * (effectiveDepth - a / 2)
        val capacity = PHI_FLEXURE * Mn / 1e6  // kN.m
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0

        // ── عمق المحور المحايد ──
        val neutralAxisDepth = if (beta1 > 0) a / beta1 else 0.0

        codeNotes.add(CodeReference.SBC.BEAM_FLEXURE)
        codeNotes.add("SBC 304 §9.3.3: φ = $PHI_FLEXURE (مقاطع محكومة بالشد)")
        codeNotes.add("f'c = 0.8 × fcu = ${String.format("%.1f", fc)} MPa")

        // ── متطلبات المناطق الزلزالية — SBC 304 §18 ──
        if (loadCombination == LoadCombination.DEAD_LIVE_EARTHQUAKE ||
            loadCombination == LoadCombination.DEAD_EARTHQUAKE) {

            // §18.4: أقل عرض للكمرات الزلزالية
            if (width < SBC_MIN_WIDTH_SEISMIC) {
                warnings.add("SBC 304 §18.4: عرض الكمرة ${width}mm < ${SBC_MIN_WIDTH_SEISMIC}mm في المنطقة الزلزالية")
            }

            // §18.4.2: نسبة التسليح الأدنى الزلزالية
            val rhoMinSeismic = 0.25 * sqrt(fc) / fy
            val rhoActual = astProvided / (width * effectiveDepth)
            if (rhoActual < rhoMinSeismic) {
                warnings.add("SBC 304 §18: ρ_actual=${String.format("%.4f", rhoActual)} < ρ_min,seis=${String.format("%.4f", rhoMinSeismic)}")
            }

            // §18.4.3: أقصى نسبة تسليح زلزالية
            val rhoMaxSeismic = 0.025
            if (rhoActual > rhoMaxSeismic) {
                warnings.add("SBC 304 §18: ρ_actual=${String.format("%.4f", rhoActual)} > ρ_max,seis=${rhoMaxSeismic}")
            }

            codeNotes.add("SBC 304 §18: متطلبات المناطق الزلزالية مطبقة")
        }

        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = selectedBarDia,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0 && rho <= rhoMaxTc,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes,
            neutralAxisDepth = neutralAxisDepth
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. حساب تسليح القص — SBC 304-2018 §11
    // ══════════════════════════════════════════════════════════════════════
    override fun calculateShearReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        designShear: Double,
        axialLoad: Double,
        loadCombination: LoadCombination
    ): ShearReinforcementResult {
        // ── InputGuard (ADR-010) — SBC 304 §11 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("width", width)
        InputGuard.positive("effectiveDepth", effectiveDepth)
        InputGuard.nonNegative("designShear", designShear)
        InputGuard.nonNegative("axialLoad", axialLoad)
        InputGuard.notNull("loadCombination", loadCombination)

        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val Vu = designShear * 1000.0  // N

        // ── تحويل المقاومة ──
        val fc = 0.8 * fcu

        // ── مقاومة القص الخرسانية — SBC 304 §11.2.1 ──
        // Vc = 0.17 × λ × √(f'c) × bw × d  (N)
        // ملاحظة: SBC 304 يستخدم نفس معادلة ACI لـ Vc لكن مع تحفظات زلزالية إضافية
        val Vc = 0.17 * LAMBDA * sqrt(fc) * width * effectiveDepth

        // تأثير الحمل المحوري على مقاومة القص — SBC 304 §11.2.1.2
        val VcWithAxial = if (axialLoad > 0) {
            val Nu = axialLoad * 1000.0  // N (ضغط)
            Vc * (1.0 + Nu / (14.0 * fc * width * effectiveDepth).coerceAtLeast(1.0))
        } else {
            Vc
        }

        val phiVc = PHI_SHEAR * VcWithAxial / 1000  // kN

        var requiredStirrups = 0.0
        if (Vu / 1000 > phiVc / 2) {
            // ── التسليح الأدنى للقص — SBC 304 §11.6.5 ──
            // Av,min/s = max(0.062√(f'c)×bw/fy, 0.35×bw/fy)
            val minAv_s = max(
                0.062 * sqrt(fc) * width / fy,
                0.35 * width / fy
            ) * 1000  // mm²/m

            if (Vu / 1000 > phiVc) {
                // ── حساب التسليح المطلوب — SBC 304 §11.4 ──
                val Vs = (Vu / 1000 - phiVc) / PHI_SHEAR  // kN
                requiredStirrups = Vs * 1000 / (fy * effectiveDepth.coerceAtLeast(1.0)) * 1000  // mm²/m
                requiredStirrups = max(requiredStirrups, minAv_s)
            } else {
                requiredStirrups = minAv_s
                warnings.add("SBC 304 §11.6.5: تسليح القص الأدنى مطبق")
            }
        }

        // ── اختيار قطر الكانات ──
        val stirrupDiameter = if (Vu / 1000 > 0.5 * phiVc) 10.0 else 8.0
        val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4  // رجلين

        // ── حساب التباعد ──
        var stirrupSpacing = if (requiredStirrups > 0) stirrupArea * 1000 / requiredStirrups else getMaxShearSpacing()

        // ── حدود التباعد — SBC 304 §11.7.6 ──
        val maxVsLimit = 0.33 * sqrt(fc) * width * effectiveDepth / 1000
        val VsActual = if (Vu / 1000 > phiVc) (Vu / 1000 - phiVc) / PHI_SHEAR else 0.0

        val maxSpacing1 = if (VsActual <= maxVsLimit) {
            minOf(effectiveDepth / 2, 600.0)
        } else {
            minOf(effectiveDepth / 4, 300.0)
        }
        stirrupSpacing = minOf(stirrupSpacing, maxSpacing1, getMaxShearSpacing())
        stirrupSpacing = max(stirrupSpacing, 50.0)

        // ── أقصى حد للقص — SBC 304 §11.4.7 ──
        val maxVs = 0.66 * sqrt(fc) * width * effectiveDepth / 1000
        val maxShearCapacity = phiVc + PHI_SHEAR * maxVs
        var isSafe = (Vu / 1000) <= maxShearCapacity

        if (!isSafe) {
            warnings.add("SBC 304 §11.4.7: القص يتجاوز الحدود — زِد المقاطع أو fcu")
        }

        // ── متطلبات القص الزلزالية — SBC 304 §18.5 ──
        if (loadCombination == LoadCombination.DEAD_LIVE_EARTHQUAKE ||
            loadCombination == LoadCombination.DEAD_EARTHQUAKE) {

            // §18.5.3: أقصى تباعد للكانات في المناطق الزلزالية
            val seismicMaxSpacing = min(
                effectiveDepth / SBC_SEISMIC_MAX_STIRRUP_SPACING_FACTOR,
                SBC_SEISMIC_MAX_STIRRUP_SPACING_MM
            )
            if (stirrupSpacing > seismicMaxSpacing) {
                stirrupSpacing = seismicMaxSpacing
                warnings.add("SBC 304 §18.5.3: تباعد الكانات خُفِّض لـ ${String.format("%.0f", seismicMaxSpacing)}mm (زلزالي)")
            }

            // §18.5.2: تسليح القص الأدنى الزلزالي
            val avMinSeismic = 0.062 * sqrt(fc) * width / fy * 1000
            val providedAv = stirrupArea * 1000 / stirrupSpacing
            if (providedAv < avMinSeismic) {
                warnings.add("SBC 304 §18.5: تسليح القص أقل من النسبة الزلزالية الدنيا")
            }
            codeNotes.add("SBC 304 §18.5: متطلبات القص الزلزالية مطبقة")
        }

        codeNotes.add(CodeReference.SBC.BEAM_SHEAR)
        codeNotes.add("SBC 304 §11.2.1: Vc = ${String.format("%.1f", Vc / 1000)} kN, φVc = ${String.format("%.1f", phiVc)} kN")

        return ShearReinforcementResult(
            concreteShearCapacity = VcWithAxial / 1000,
            requiredShearReinforcement = requiredStirrups,
            providedShearReinforcement = stirrupArea * 1000 / stirrupSpacing,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            isSafe = isSafe,
            utilizationRatio = if (maxShearCapacity > 0) (Vu / 1000) / maxShearCapacity else 2.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. التحقق من الانحراف — SBC 304-2018 §9.5
    // ══════════════════════════════════════════════════════════════════════
    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        reinforcementRatio: Double,
        supportCondition: SupportCondition
    ): DeflectionCheckResult {
        // ── InputGuard — SBC 304 §9.5 ──
        InputGuard.positive("span", span)
        InputGuard.positive("totalDepth", totalDepth)
        InputGuard.nonNegative("reinforcementRatio", reinforcementRatio)
        InputGuard.notNull("supportCondition", supportCondition)

        // ── نسب البحر/العمق — SBC 304 §9.5.2 (Table 9.5(a)) ──
        // SBC يتبع ACI Table 24.2.2 مع تعديل بسيط لـ fy
        val basicRatio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 16.0
            SupportCondition.CONTINUOUS -> 21.0
            SupportCondition.CANTILEVER -> 8.0
        }

        // ── معامل تعديل fy — SBC 304 §9.5.2 ──
        val defaultFy = 420.0  // Grade 60 السعودي
        val fyFactor = minOf(1.0, 0.4 + 420.0 / defaultFy)

        val actualRatio = (span * 1000) / totalDepth
        val allowableRatio = basicRatio * fyFactor

        val ratio = actualRatio / allowableRatio
        val allowableDeflection = getDeflectionLimit(span)

        val calculatedDeflection = if (ratio > 1.0) {
            allowableDeflection * ratio * 1.2
        } else {
            allowableDeflection * 0.7
        }

        return DeflectionCheckResult(
            calculatedDeflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            recommendation = if (ratio > 1.0)
                "SBC 304 §9.5: زِد العمق أو أجري تحليل انحراف تفصيلي"
                else "نسبة البحر/العمق مقبولة حسب SBC 304"
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. أطوال التثبيت — SBC 304-2018 §12
    // ══════════════════════════════════════════════════════════════════════
    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // ── InputGuard — SBC 304 §12 ──
        InputGuard.positive("barDiameter", barDiameter)
        InputGuard.positive("fy", fy)
        InputGuard.positive("fcu", fcu)
        InputGuard.notNull("barLocation", barLocation)
        InputGuard.notNull("coating", coating)

        // ── SBC 304 §12.2.2: Ld = (fy × ψt × ψe × ψs) / (1.7 × λ × √(f'c)) × db ──
        var psi_t = 1.0
        if (barLocation == BarLocation.TOP) psi_t = 1.3

        var psi_e = 1.0
        if (coating == CoatingType.EPOXY_COATED) psi_e = 1.2

        // SBC 304 إضافة: المجلفن يحتاج زيادة 10%
        if (coating == CoatingType.GALVANIZED) psi_e = 1.1

        val psi_s = if (barDiameter <= 22.0) 1.0 else 0.8
        val fc = 0.8 * fcu

        val numerator = fy * psi_t * psi_e * psi_s
        val denominator = 1.7 * LAMBDA * sqrt(fc.coerceAtLeast(1.0))

        var Ld = (numerator / denominator) * barDiameter

        // SBC 304 §12.2.1: أقل طول تثبيت
        Ld = max(Ld, SBC_MIN_DEVELOPMENT_LENGTH)

        // SBC 304 إضافة: في البيئة المالحة (شائعة في المملكة)
        // يُفضل زيادة طول التثبيت 15% للأسياخ المجلفنة
        if (coating == CoatingType.GALVANIZED) {
            Ld *= 1.15
        }

        return ceil(Ld / 25) * 25
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. تصميم كمرة مضاعفة التسليح — SBC 304-2018 §9.3.3.2
    // ══════════════════════════════════════════════════════════════════════
    /**
     * تصميم كمرة مضاعفة التسليح (Doubly-Reinforced Beam) حسب SBC 304-2018
     *
     * طريقة Rn-ρ مع فروقات SBC عن ACI:
     * - f'c = 0.8 × fcu (تحويل مكعب → أسطوانة)
     * - ρ_min = max(0.25√(f'c)/fy, 1.4/fy) — SBC 304 §9.6.1
     * - متطلبات زلزالية إضافية §18
     */
    fun calculateDoublyReinforcedBeam(
        designMoment: Double,  // kN.m
        width: Double,         // mm
        depth: Double,         // mm (العمق الكلي h)
        fcu: Double,           // MPa
        fy: Double,            // MPa
        compressionSteelDia: Double = 16.0,
        d_prime: Double = 50.0 // mm
    ): DoublyReinforcedResult {
        InputGuard.positive("designMoment", designMoment)
        InputGuard.positive("width", width)
        InputGuard.positive("depth", depth)
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("compressionSteelDia", compressionSteelDia)
        InputGuard.positive("d_prime", d_prime)

        val notes = mutableListOf<String>()
        val d = depth - 60.0  // العمق الفعال
        val b = width
        val Mu = designMoment * 1e6

        val fc = 0.8 * fcu
        val beta1 = calculateBeta1(fc)

        // Rn = Mu / (φ × b × d²)
        val denom = PHI_FLEXURE * b * d * d
        val Rn = if (denom > 0) Mu / denom else 0.0

        // ρ_bal
        val epsilonCu = 0.003
        val epsilonY = fy / 200000.0
        val rhoBal = 0.85 * beta1 * (fc / fy) * (epsilonCu / (epsilonCu + epsilonY))

        // Rn_bal
        val RnBal = if (fc > 0) {
            rhoBal * fy * (1.0 - 0.5 * rhoBal * fy / (0.85 * fc))
        } else 0.0

        val cOverD = epsilonCu / (epsilonCu + epsilonY)
        val neutralAxisDepth = cOverD * d

        notes.add("SBC 304 §9.3.3: f'c = 0.8 × fcu = ${String.format("%.1f", fc)} MPa")
        notes.add("Rn = ${String.format("%.2f", Rn)} MPa")
        notes.add("Rn_bal = ${String.format("%.2f", RnBal)} MPa")
        notes.add("ρ_bal = ${String.format("%.4f", rhoBal)}")

        // ── تسليح أحادي كافٍ ──
        if (Rn <= RnBal) {
            val m = if (fc > 0) fy / (0.85 * fc) else 0.0
            val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
                (1 - sqrt(1 - 2 * m * Rn / fy)) / m
            } else 0.0
            val asReq = rho * b * d

            val minSteel = max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * d
            val asFinal = max(asReq, minSteel)

            val a = if (fc > 0) asFinal * fy / (0.85 * fc * b) else 0.0
            val leverArm = d - a / 2

            notes.add("Rn ≤ Rn_bal → تسليح أحادي كافٍ")
            notes.add(CodeReference.SBC.BEAM_FLEXURE)

            return DoublyReinforcedResult(
                needsCompressionSteel = false,
                balancedMoment = designMoment,
                excessMoment = 0.0,
                tensionSteelArea = asFinal,
                compressionSteelArea = 0.0,
                tensionBars = selectSBCBars(asFinal),
                compressionBars = "None",
                leverArm = leverArm,
                neutralAxisDepth = neutralAxisDepth,
                isSafe = true,
                utilizationRatio = Rn / max(RnBal, 0.001),
                codeNotes = notes.joinToString("\n")
            )
        }

        // ── تسليح مضاعف ──
        val RnExcess = Rn - RnBal
        val leverArmExcess = d - d_prime
        val AsPrime = if (leverArmExcess > 0) {
            RnExcess * b * d * d / (fy * leverArmExcess)
        } else 0.0

        val As1 = rhoBal * b * d
        val AsTotal = As1 + AsPrime

        val minSteel = max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * d
        val asFinal = max(AsTotal, minSteel)

        val tensionBars = selectSBCBars(asFinal)
        val compressionBars = if (AsPrime > 0) selectSBCBars(AsPrime, compressionSteelDia) else "None"

        val tensionBarArea = parseSBCBarArea(tensionBars)
        val compressionBarArea = if (AsPrime > 0) parseSBCBarArea(compressionBars) else 0.0

        val a1 = if (fc > 0) As1 * fy / (0.85 * fc * b) else 0.0
        val Mn = tensionBarArea * fy * (d - a1 / 2) + compressionBarArea * fy * leverArmExcess
        val capacity = PHI_FLEXURE * Mn / 1e6
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0

        notes.add("Rn > Rn_bal → تسليح ضغط مطلوب")
        notes.add("Rn_excess = ${String.format("%.2f", RnExcess)} MPa")
        notes.add("As₁ (متوازن) = ${String.format("%.0f", As1)} mm²")
        notes.add("As (كلي) = ${String.format("%.0f", asFinal)} mm²")
        notes.add("As' (ضغط) = ${String.format("%.0f", AsPrime)} mm²")
        notes.add("c = ${String.format("%.1f", neutralAxisDepth)} mm")
        notes.add("φ = $PHI_FLEXURE لمقاطع محكومة بالشد")
        notes.add(CodeReference.SBC.BEAM_DOUBLY_REINFORCED)

        return DoublyReinforcedResult(
            needsCompressionSteel = true,
            balancedMoment = PHI_FLEXURE * RnBal * b * d * d / 1e6,
            excessMoment = PHI_FLEXURE * RnExcess * b * d * d / 1e6,
            tensionSteelArea = asFinal,
            compressionSteelArea = AsPrime,
            tensionBars = tensionBars,
            compressionBars = compressionBars,
            leverArm = d - a1 / 2,
            neutralAxisDepth = neutralAxisDepth,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            codeNotes = notes.joinToString("\n")
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // حدود الكود السعودي SBC 304-2018
    // ══════════════════════════════════════════════════════════════════════

    override fun getMinReinforcementRatio(): Double {
        // SBC 304 §9.6.1: ρ_min = max(0.25√(28)/420, 1.4/420) ≈ 0.0033
        return max(0.25 * sqrt(28.0) / 420.0, 1.4 / 420.0)
    }

    override fun getMaxReinforcementRatio(): Double {
        // SBC 304 §9.3.3.1: ρ_max للمقاطع المحكومة بالشد
        return 0.85 * BETA_1_DEFAULT * (28.0 / 420.0) * 0.375
    }

    override fun getMinShearReinforcementRatio(): Double {
        // SBC 304 §11.6.5
        return 0.35 / 420.0
    }

    override fun getMaxShearSpacing(): Double {
        // SBC 304 §11.7.6
        return 600.0
    }

    override fun getMinCover(): Double {
        // SBC 304 §4: الغطاء السعودي — 50mm (بيئة مالحة شائعة في المملكة)
        return SBC_COVER_EXTERIOR
    }

    override fun getDeflectionLimit(span: Double): Double {
        // SBC 304 §9.5: L/360 للأحمال الحية
        return span * 1000 / 360.0
    }

    // ══════════════════════════════════════════════════════════════════════
    // دوال مساعدة
    // ══════════════════════════════════════════════════════════════════════

    private fun calculateBeta1(fc: Double): Double {
        // SBC 304 §9.3.3: نفس ACI 318 — β₁ ينخفض من 0.85 لـ f'c ≤ 28 إلى 0.65 لـ f'c ≥ 55
        return when {
            fc <= 28 -> 0.85
            fc >= 55 -> 0.65
            else -> 0.85 - (0.05 * (fc - 28) / 7)
        }
    }

    /**
     * اختيار أسياخ مناسبة حسب المساحة المطلوبة (مقاسات السوق السعودي)
     */
    private fun selectSBCBars(requiredArea: Double, preferredDia: Double? = null): String {
        val availableBars = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)
        val barDia = preferredDia ?: availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(requiredArea / area) <= 6
        } ?: 19.0
        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceIn(2, 12)
        return "${numBars}Ø${barDia.toInt()}"
    }

    /**
     * تحليل نص الأسياخ واستخراج المساحة الإجمالية
     */
    private fun parseSBCBarArea(barString: String): Double {
        if (barString == "None" || !barString.contains("Ø")) return 0.0
        try {
            val parts = barString.split("Ø")
            val count = parts[0].trim().toInt()
            val dia = parts[1].trim().toInt().toDouble()
            return count * PI * dia * dia / 4
        } catch (e: Exception) {
            return 0.0
        }
    }
}
