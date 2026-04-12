package com.civilengineer.assistant.utils

import com.civilengineer.assistant.models.*
import kotlin.math.*

/**
 * حسابات هندسية متقدمة إضافية
 * Advanced Engineering Calculations
 */
object AdvancedCalculations {

    // ═══════════════════════════════════════════════════════════
    // 1. تصميم كمرة T-Beam
    // ═══════════════════════════════════════════════════════════

    fun tBeamDesign(
        Mu: Double,            // kN.m
        bw: Double,            // mm - عرض الجسم
        bf: Double,            // mm - عرض الشفة
        hf: Double,            // mm - سمك الشفة
        d: Double,             // mm - العمق الفعال
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): TBeamResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val gammaC = if (code == DesignCode.AMERICAN) 1.0 else 1.50
        val gammaS = if (code == DesignCode.AMERICAN) 1.0 else 1.15
        val phi = if (code == DesignCode.AMERICAN) 0.90 else 1.0
        val fc = if (code == DesignCode.AMERICAN) fcu * 0.80 else fcu

        // عرض الشفة الفعال
        val effectiveBf = bf  // يمكن إضافة التحقق من الحدود
        equations.add(EquationStep(
            stepNum++, "عرض الشفة الفعال bf",
            "bf ≤ min(L/4, bw + 16hf, المسافة بين الكمرات)",
            "bf = ${EngineeringCalculations.formatNum(effectiveBf)} mm",
            "bf = ${EngineeringCalculations.formatNum(effectiveBf)} mm",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP 203 - Clause 4.2.1.3"
                DesignCode.SAUDI -> "SBC 304 - Section 8.12"
                DesignCode.AMERICAN -> "ACI 318-19 - Section 6.3.2"
            }
        ))

        // فحص: هل المحور المتعادل داخل الشفة أم خارجها؟
        val MufCapacity: Double = when (code) {
            DesignCode.EGYPTIAN, DesignCode.SAUDI -> {
                0.67 * (fcu / gammaC) * effectiveBf * hf * (d - hf / 2) / 1e6
            }
            DesignCode.AMERICAN -> {
                phi * 0.85 * fc * effectiveBf * hf * (d - hf / 2) / 1e6
            }
        }

        equations.add(EquationStep(
            stepNum++, "سعة عزم الشفة Muf",
            "Muf = 0.67(fcu/γc) × bf × hf × (d - hf/2)",
            "Muf = ${EngineeringCalculations.formatNum(MufCapacity)} kN.m",
            "Muf = ${EngineeringCalculations.formatNum(MufCapacity)} kN.m",
            "فحص موقع المحور المتعادل"
        ))

        val isNeutralAxisInFlange = Mu <= MufCapacity

        equations.add(EquationStep(
            stepNum++, "موقع المحور المتعادل",
            "Mu ${if (isNeutralAxisInFlange) "≤" else ">"} Muf",
            "Mu = ${EngineeringCalculations.formatNum(Mu)}, Muf = ${EngineeringCalculations.formatNum(MufCapacity)}",
            if (isNeutralAxisInFlange) "المحور المتعادل داخل الشفة → تصميم كمستطيل bf×d" else "المحور المتعادل في الجسم → تصميم كمرة T",
            "إذا كان المحور داخل الشفة يتم التعامل مع القطاع كمستطيل"
        ))

        val As: Double
        if (isNeutralAxisInFlange) {
            // تصميم كقطاع مستطيل بعرض bf
            val result = EngineeringCalculations.flexureDesign(Mu, effectiveBf, d, fcu, fy, code)
            As = result.requiredAs
            equations.addAll(result.equations)
        } else {
            // تصميم كمرة T
            // As = Asf + Asw
            val Asf: Double
            val Asw: Double
            val Muw: Double

            when (code) {
                DesignCode.EGYPTIAN, DesignCode.SAUDI -> {
                    Asf = 0.67 * (fcu / gammaC) * (effectiveBf - bw) * hf / (fy / gammaS)
                    Muw = Mu - MufCapacity + 0.67 * (fcu / gammaC) * (effectiveBf - bw) * hf * (d - hf / 2) / 1e6
                    // Now we need only bw×d for remaining moment
                    val result = EngineeringCalculations.flexureDesign(Muw, bw, d, fcu, fy, code)
                    Asw = result.requiredAs
                }
                DesignCode.AMERICAN -> {
                    Asf = 0.85 * fc * (effectiveBf - bw) * hf / fy
                    val Mnf = Asf * fy * (d - hf / 2) / 1e6
                    Muw = Mu / phi - Mnf
                    val result = EngineeringCalculations.flexureDesign(Muw * phi, bw, d, fcu, fy, code)
                    Asw = result.requiredAs
                }
            }

            As = Asf + Asw

            equations.add(EquationStep(
                stepNum++, "تسليح الشفة Asf",
                "Asf = 0.67(fcu/γc)(bf-bw)hf / (fy/γs)",
                "Asf = ${EngineeringCalculations.formatNum(Asf)} mm²",
                "Asf = ${EngineeringCalculations.formatNum(Asf)} mm²"
            ))
            equations.add(EquationStep(
                stepNum++, "تسليح الجسم Asw",
                "Asw من تصميم قطاع bw × d للعزم المتبقي",
                "Asw = ${EngineeringCalculations.formatNum(Asw)} mm²",
                "As_total = Asf + Asw = ${EngineeringCalculations.formatNum(As)} mm²"
            ))
        }

        return TBeamResult(
            requiredAs = As,
            isNeutralAxisInFlange = isNeutralAxisInFlange,
            flangeCapacity = MufCapacity,
            equations = equations
        )
    }

    data class TBeamResult(
        val requiredAs: Double,
        val isNeutralAxisInFlange: Boolean,
        val flangeCapacity: Double,
        val equations: List<EquationStep>
    )

    // ═══════════════════════════════════════════════════════════
    // 2. تصميم قاعدة مشتركة
    // ═══════════════════════════════════════════════════════════

    fun combinedFootingDesign(
        P1: Double,            // kN - حمل العمود 1
        P2: Double,            // kN - حمل العمود 2
        M1: Double,            // kN.m - عزم العمود 1
        M2: Double,            // kN.m - عزم العمود 2
        col1B: Double,         // mm - عرض العمود 1
        col2B: Double,         // mm - عرض العمود 2
        spacing: Double,       // m - المسافة بين العمودين
        soilBearing: Double,   // kN/m²
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): CombinedFootingResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val loadFactor = when (code) {
            DesignCode.EGYPTIAN, DesignCode.SAUDI -> 1.50
            DesignCode.AMERICAN -> 1.40
        }

        val P1service = P1 / loadFactor
        val P2service = P2 / loadFactor
        val totalP = P1service + P2service

        // مركز الأحمال
        val xBar = P2service * spacing / totalP
        equations.add(EquationStep(
            stepNum++, "مركز الأحمال",
            "x̄ = P2 × L / (P1 + P2)",
            "x̄ = ${EngineeringCalculations.formatNum(P2service)} × ${EngineeringCalculations.formatNum(spacing)} / ${EngineeringCalculations.formatNum(totalP)}",
            "x̄ = ${EngineeringCalculations.formatNum(xBar)} m من العمود 1"
        ))

        // طول القاعدة (بحيث يكون مركز القاعدة = مركز الأحمال)
        val L = 2 * xBar + 0.5 // add extra for edge
        val Lfinal = ceil(L * 10) / 10.0 // round up

        // عرض القاعدة
        val requiredArea = totalP * 1.10 / soilBearing
        val B = ceil(requiredArea / Lfinal * 10) / 10.0

        equations.add(EquationStep(
            stepNum++, "أبعاد القاعدة المشتركة",
            "L = 2x̄ + إضافي, B = A_req / L",
            "L = ${EngineeringCalculations.formatNum(Lfinal)} m, B = ${EngineeringCalculations.formatNum(B)} m",
            "المساحة = ${EngineeringCalculations.formatNum(Lfinal * B)} m²"
        ))

        // إجهاد التربة
        val actualPressure = totalP / (Lfinal * B)
        equations.add(EquationStep(
            stepNum++, "إجهاد التربة",
            "q = P / A",
            "q = ${EngineeringCalculations.formatNum(totalP)} / ${EngineeringCalculations.formatNum(Lfinal * B)}",
            "q = ${EngineeringCalculations.formatNum(actualPressure)} kN/m² ${if (actualPressure <= soilBearing) "✓" else "✗"}"
        ))

        // سمك القاعدة
        val t = maxOf(Lfinal * 1000 / 5.0, 400.0)
        val tFinal = ceil(t / 50) * 50

        // حساب العزوم والقص (كمرة مقلوبة)
        val quService = totalP / Lfinal // kN/m per unit width (already per m)
        val quUlt = quService * loadFactor / B // kN/m² then × B for per m

        // العزم الأقصى (في منطقة بين العمودين)
        val MuMax = (P1 + P2) / (2 * Lfinal) * spacing * spacing / 8 // simplified
        val VuMax = maxOf(P1, P2) // simplified

        equations.add(EquationStep(
            stepNum++, "العزوم والقص",
            "تحليل القاعدة ككمرة مقلوبة",
            "Mu_max ≈ ${EngineeringCalculations.formatNum(MuMax)} kN.m",
            "Vu_max ≈ ${EngineeringCalculations.formatNum(VuMax)} kN"
        ))

        CombinedFootingResult(
            length = Lfinal,
            width = B,
            depth = tFinal / 1000.0,
            soilPressure = actualPressure,
            maxMoment = MuMax,
            maxShear = VuMax,
            isSafe = actualPressure <= soilBearing,
            equations = equations
        )

        return CombinedFootingResult(
            length = Lfinal,
            width = B,
            depth = tFinal / 1000.0,
            soilPressure = actualPressure,
            maxMoment = MuMax,
            maxShear = VuMax,
            isSafe = actualPressure <= soilBearing,
            equations = equations
        )
    }

    data class CombinedFootingResult(
        val length: Double,
        val width: Double,
        val depth: Double,
        val soilPressure: Double,
        val maxMoment: Double,
        val maxShear: Double,
        val isSafe: Boolean,
        val equations: List<EquationStep>
    )

    // ═══════════════════════════════════════════════════════════
    // 3. تصميم الخوازيق
    // ═══════════════════════════════════════════════════════════

    fun pileDesign(
        Pu: Double,                // kN - الحمل التصميمي
        pileType: String,          // "bored" أو "driven"
        pileDiameter: Double,      // mm
        pileLength: Double,        // m
        soilLayers: List<SoilLayer>,
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): PileDesignResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val D = pileDiameter / 1000.0 // m
        val L = pileLength
        val Ap = PI * D * D / 4.0     // m² - مساحة القاعدة

        // سعة التحمل القصوى (End Bearing)
        val lastLayer = soilLayers.lastOrNull() ?: SoilLayer("طين متوسط", 18.0, 0.0, 100.0, 30.0)
        val Nc = 9.0 // for deep foundation
        val Qb = Nc * lastLayer.cohesion * Ap // kN (for cohesive soil)
        // لتربة رملية: Qb = Nq × σ'v × Ap

        equations.add(EquationStep(
            stepNum++, "سعة التحمل عند القاعدة Qb",
            "Qb = Nc × Cu × Ap",
            "Qb = $Nc × ${EngineeringCalculations.formatNum(lastLayer.cohesion)} × ${EngineeringCalculations.formatNum(Ap)}",
            "Qb = ${EngineeringCalculations.formatNum(Qb)} kN",
            "تحمل القاعدة"
        ))

        // سعة الاحتكاك الجانبي (Skin Friction)
        var Qs = 0.0
        val alpha = 0.5 // adhesion factor
        soilLayers.forEach { layer ->
            val perimeter = PI * D
            val qs = alpha * layer.cohesion
            val layerCapacity = qs * perimeter * layer.thickness
            Qs += layerCapacity
        }

        equations.add(EquationStep(
            stepNum++, "سعة الاحتكاك الجانبي Qs",
            "Qs = Σ(α × Cu × π × D × Li)",
            "Qs = ${EngineeringCalculations.formatNum(Qs)} kN",
            "Qs = ${EngineeringCalculations.formatNum(Qs)} kN"
        ))

        val Qult = Qb + Qs
        val FOS = 2.5 // Factor of Safety for piles
        val Qallow = Qult / FOS

        equations.add(EquationStep(
            stepNum++, "سعة التحمل المسموحة",
            "Q_allow = Q_ult / FOS",
            "Q_allow = ${EngineeringCalculations.formatNum(Qult)} / $FOS",
            "Q_allow = ${EngineeringCalculations.formatNum(Qallow)} kN",
            "معامل أمان = $FOS"
        ))

        // عدد الخوازيق المطلوبة
        val loadFactor = when (code) {
            DesignCode.EGYPTIAN, DesignCode.SAUDI -> 1.50
            DesignCode.AMERICAN -> 1.40
        }
        val Pservice = Pu / loadFactor
        val nPiles = ceil(Pservice / Qallow).toInt().coerceAtLeast(2)

        equations.add(EquationStep(
            stepNum++, "عدد الخوازيق",
            "n = P_service / Q_allow",
            "n = ${EngineeringCalculations.formatNum(Pservice)} / ${EngineeringCalculations.formatNum(Qallow)}",
            "n = $nPiles خازوق",
            "الحد الأدنى 2 خوازيق"
        ))

        // تسليح الخازوق
        val Ag = PI * (pileDiameter / 2.0).pow(2) // mm²
        val AsMin = 0.01 * Ag // 1% minimum
        val mainBarDia = if (pileDiameter >= 600) 20.0 else 16.0
        val barArea = PI * mainBarDia * mainBarDia / 4.0
        val nBars = ceil(AsMin / barArea).toInt().coerceAtLeast(6)

        equations.add(EquationStep(
            stepNum++, "تسليح الخازوق",
            "As_min = 1% × Ag",
            "As_min = 0.01 × ${EngineeringCalculations.formatNum(Ag)} = ${EngineeringCalculations.formatNum(AsMin)} mm²",
            "${nBars}φ${mainBarDia.toInt()} + حلزون φ8@150mm"
        ))

        return PileDesignResult(
            ultimateCapacity = Qult,
            endBearing = Qb,
            skinFriction = Qs,
            allowableCapacity = Qallow,
            numberOfPiles = nPiles,
            pileAs = AsMin,
            numberOfBars = nBars,
            barDiameter = mainBarDia,
            isSafe = Pservice / nPiles <= Qallow,
            equations = equations
        )
    }

    data class SoilLayer(
        val name: String,
        val density: Double,         // kN/m³
        val thickness: Double,       // m
        val cohesion: Double,        // kN/m² (Cu)
        val frictionAngle: Double    // degrees
    )

    data class PileDesignResult(
        val ultimateCapacity: Double,
        val endBearing: Double,
        val skinFriction: Double,
        val allowableCapacity: Double,
        val numberOfPiles: Int,
        val pileAs: Double,
        val numberOfBars: Int,
        val barDiameter: Double,
        val isSafe: Boolean,
        val equations: List<EquationStep>
    )

    // ═══════════════════════════════════════════════════════════
    // 4. تصميم كمرة عميقة (Deep Beam)
    // ═══════════════════════════════════════════════════════════

    fun deepBeamDesign(
        Pu: Double,            // kN - حمل مركز
        span: Double,          // mm - البحر
        b: Double,             // mm - العرض
        h: Double,             // mm - الارتفاع الكلي
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): DeepBeamResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val Ln = span // clear span
        val d = h - 50.0

        // فحص: هل هي كمرة عميقة؟
        val ratio = Ln / d
        val isDeep = ratio <= 4.0

        equations.add(EquationStep(
            stepNum++, "فحص الكمرة العميقة",
            "Ln/d ≤ 4.0 (أو L/h ≤ 4)",
            "Ln/d = ${EngineeringCalculations.formatNum(Ln)}/${EngineeringCalculations.formatNum(d)} = ${EngineeringCalculations.formatNum(ratio)}",
            if (isDeep) "كمرة عميقة ✓" else "كمرة عادية (استخدم التصميم العادي)",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP 203 - Clause 7.3"
                DesignCode.SAUDI -> "SBC 304 - Section 11.7"
                DesignCode.AMERICAN -> "ACI 318-19 - Section 9.9"
            }
        ))

        // نموذج الشد والضغط (Strut and Tie Model)
        // أبسط حالة: كمرة عميقة بسيطة بحمل مركز
        val Ru = Pu / 2.0 // ردود الأفعال

        // ذراع العزم الداخلي
        val jd = 0.6 * d // تقريبي للكمرات العميقة
        val Tu = Pu * span / (4 * jd) // tension in tie (kN × mm / mm = kN)

        equations.add(EquationStep(
            stepNum++, "قوة الشد في الوتر السفلي",
            "Tu = Pu × L / (4 × jd)",
            "Tu = ${EngineeringCalculations.formatNum(Pu)} × ${EngineeringCalculations.formatNum(span)} / (4 × ${EngineeringCalculations.formatNum(jd)})",
            "Tu = ${EngineeringCalculations.formatNum(Tu)} kN"
        ))

        // تسليح الشد
        val gammaS = if (code == DesignCode.AMERICAN) 1.0 else 1.15
        val As = Tu * 1000.0 / (fy / gammaS) // mm²

        equations.add(EquationStep(
            stepNum++, "تسليح الشد الرئيسي",
            "As = Tu / (fy/γs)",
            "As = ${EngineeringCalculations.formatNum(Tu * 1000.0)} / ${EngineeringCalculations.formatNum(fy / gammaS)}",
            "As = ${EngineeringCalculations.formatNum(As)} mm²",
            "التسليح يوزع في الثلث السفلي من الكمرة"
        ))

        // تسليح شبكي أفقي ورأسي
        val Ash = 0.0025 * b * h // horizontal web reinforcement
        val Asv = 0.0025 * b * span // vertical web reinforcement

        equations.add(EquationStep(
            stepNum++, "تسليح الشبكة",
            "Ash = 0.0025 × b × sv, Asv = 0.0025 × b × sh",
            "Ash_min = ${EngineeringCalculations.formatNum(Ash)} mm², Asv_min = ${EngineeringCalculations.formatNum(Asv)} mm²",
            "شبكة أفقية ورأسية بنسبة 0.25% كحد أدنى",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP 203 - Clause 7.3.3"
                DesignCode.SAUDI -> "SBC 304 - Section 11.7.4"
                DesignCode.AMERICAN -> "ACI 318-19 - Section 9.9.3"
            }
        ))

        return DeepBeamResult(
            isDeepBeam = isDeep,
            spanToDepthRatio = ratio,
            tieForce = Tu,
            mainAs = As,
            horizontalWebAs = Ash,
            verticalWebAs = Asv,
            equations = equations,
            isSafe = true
        )
    }

    data class DeepBeamResult(
        val isDeepBeam: Boolean,
        val spanToDepthRatio: Double,
        val tieForce: Double,
        val mainAs: Double,
        val horizontalWebAs: Double,
        val verticalWebAs: Double,
        val equations: List<EquationStep>,
        val isSafe: Boolean
    )

    // ═══════════════════════════════════════════════════════════
    // 5. حساب عمود طويل مع تأثيرات الرتبة الثانية
    // ═══════════════════════════════════════════════════════════

    fun slenderColumnDesign(
        Pu: Double,
        M1: Double,            // عزم أصغر عند الطرف
        M2: Double,            // عزم أكبر عند الطرف
        b: Double,
        h: Double,
        Lu: Double,            // الطول الحر
        kFactor: Double,       // معامل الطول الفعال
        fcu: Double,
        fy: Double,
        code: DesignCode,
        isBraced: Boolean = true
    ): SlenderColumnResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val r = h / sqrt(12.0) // radius of gyration
        val kLu = kFactor * Lu
        val lambda = kLu / r

        equations.add(EquationStep(
            stepNum++, "نسبة النحافة",
            "λ = k × Lu / r",
            "λ = ${EngineeringCalculations.formatNum(kFactor)} × ${EngineeringCalculations.formatNum(Lu)} / ${EngineeringCalculations.formatNum(r)}",
            "λ = ${EngineeringCalculations.formatNum(lambda)}"
        ))

        // هل يحتاج تكبير العزم؟
        val limitLambda = when (code) {
            DesignCode.EGYPTIAN -> if (isBraced) 15.0 else 10.0
            DesignCode.SAUDI, DesignCode.AMERICAN -> if (isBraced) 22.0 else 22.0
        }

        val isSlender = lambda > limitLambda
        equations.add(EquationStep(
            stepNum++, "فحص النحافة",
            "λ ${if (isSlender) ">" else "≤"} $limitLambda",
            "العمود ${if (isSlender) "طويل - يحتاج تكبير العزم" else "قصير"}",
            if (isSlender) "يجب مراعاة تأثيرات الرتبة الثانية (P-δ effect)" else "لا حاجة لتكبير العزم"
        ))

        var Mc = M2 // العزم المكبر
        var magnificationFactor = 1.0

        if (isSlender) {
            when (code) {
                DesignCode.EGYPTIAN -> {
                    // ECP: δ = 1 / (1 - Pu/Pcr)
                    val EI = 0.4 * (fcu / 1.5) * b * h * h * h / 12 / (1 + 0.6) // simplified
                    val Pcr = PI * PI * EI / (kLu * kLu) / 1000.0 // kN
                    val Cm = if (isBraced) maxOf(0.6 + 0.4 * M1 / M2, 0.4) else 1.0
                    magnificationFactor = Cm / (1 - Pu / Pcr)
                    magnificationFactor = maxOf(magnificationFactor, 1.0)
                    Mc = magnificationFactor * M2

                    equations.add(EquationStep(
                        stepNum++, "معامل تكبير العزم δ",
                        "δ = Cm / (1 - Pu/Pcr)",
                        "Pcr = ${EngineeringCalculations.formatNum(Pcr)} kN, Cm = ${EngineeringCalculations.formatNum(Cm)}",
                        "δ = ${EngineeringCalculations.formatNum(magnificationFactor)}, Mc = ${EngineeringCalculations.formatNum(Mc)} kN.m",
                        "ECP 203 - Clause 6.3"
                    ))
                }
                DesignCode.SAUDI, DesignCode.AMERICAN -> {
                    // ACI: Moment Magnification Method
                    val Ec = 4700 * sqrt(fcu * 0.8) // MPa
                    val Ig = b * h * h * h / 12 // mm⁴
                    val EI_eff = 0.4 * Ec * Ig / (1 + 0.6) // N.mm²
                    val Pcr = PI * PI * EI_eff / (kLu * kLu) / 1000.0 // kN
                    val Cm = if (isBraced) maxOf(0.6 + 0.4 * M1 / M2, 0.4) else 1.0
                    magnificationFactor = Cm / (1 - Pu / (0.75 * Pcr))
                    magnificationFactor = maxOf(magnificationFactor, 1.0)
                    Mc = magnificationFactor * M2

                    equations.add(EquationStep(
                        stepNum++, "Moment Magnification δ",
                        "δ = Cm / (1 - Pu/(0.75Pcr))",
                        "Pcr = ${EngineeringCalculations.formatNum(Pcr)} kN, Cm = ${EngineeringCalculations.formatNum(Cm)}",
                        "δ = ${EngineeringCalculations.formatNum(magnificationFactor)}, Mc = ${EngineeringCalculations.formatNum(Mc)} kN.m",
                        "ACI 318-19 - Section 6.6.4"
                    ))
                }
            }
        }

        // تصميم العمود بالعزم المكبر
        val columnResult = EngineeringCalculations.columnDesign(
            Pu = Pu, Mu = Mc, b = b, h = h, length = Lu,
            fcu = fcu, fy = fy, code = code
        )

        return SlenderColumnResult(
            isSlender = isSlender,
            slendernessRatio = lambda,
            magnificationFactor = magnificationFactor,
            magnifiedMoment = Mc,
            originalMoment = M2,
            columnResult = columnResult,
            equations = equations
        )
    }

    data class SlenderColumnResult(
        val isSlender: Boolean,
        val slendernessRatio: Double,
        val magnificationFactor: Double,
        val magnifiedMoment: Double,
        val originalMoment: Double,
        val columnResult: EngineeringCalculations.ColumnDesignResult,
        val equations: List<EquationStep>
    )

    // ═══════════════════════════════════════════════════════════
    // 6. حساب الترخيم (Deflection)
    // ═══════════════════════════════════════════════════════════

    fun calculateDeflection(
        span: Double,          // mm
        b: Double,             // mm
        h: Double,             // mm
        d: Double,             // mm
        As: Double,            // mm² - مساحة التسليح
        wDead: Double,         // kN/m
        wLive: Double,         // kN/m
        fcu: Double,
        code: DesignCode
    ): DeflectionResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val fc = if (code == DesignCode.AMERICAN) fcu * 0.8 else fcu

        // معامل المرونة
        val Ec = when (code) {
            DesignCode.EGYPTIAN -> 4400 * sqrt(fcu) // N/mm² (ECP)
            DesignCode.SAUDI -> 4700 * sqrt(fc)
            DesignCode.AMERICAN -> 4700 * sqrt(fc)
        }

        equations.add(EquationStep(
            stepNum++, "معامل مرونة الخرسانة Ec",
            when (code) {
                DesignCode.EGYPTIAN -> "Ec = 4400√fcu"
                else -> "Ec = 4700√f'c"
            },
            "Ec = ${EngineeringCalculations.formatNum(Ec)} N/mm²",
            "Ec = ${EngineeringCalculations.formatNum(Ec)} MPa"
        ))

        // عزم القصور الذاتي للقطاع الكامل
        val Ig = b * h * h * h / 12.0 // mm⁴

        // عزم التشقق
        val fr = when (code) {
            DesignCode.EGYPTIAN -> 0.6 * sqrt(fcu)
            else -> 0.62 * sqrt(fc)
        }
        val yt = h / 2.0
        val Mcr = fr * Ig / yt / 1e6 // kN.m

        equations.add(EquationStep(
            stepNum++, "عزم التشقق Mcr",
            "Mcr = fr × Ig / yt",
            "fr = ${EngineeringCalculations.formatNum(fr)} N/mm², Ig = ${EngineeringCalculations.formatNum(Ig / 1e8)} × 10⁸ mm⁴",
            "Mcr = ${EngineeringCalculations.formatNum(Mcr)} kN.m"
        ))

        // عزم القصور الذاتي الفعال (Cracked section)
        val n = 200000.0 / Ec // modular ratio
        val rho = As / (b * d)
        val k = sqrt(2 * rho * n + (rho * n).pow(2)) - rho * n
        val Icr = b * (k * d).pow(3) / 3.0 + n * As * (d - k * d).pow(2)

        // الحمل الفعلي (خدمة)
        val wService = wDead + wLive
        val Ma = wService * (span / 1000.0).pow(2) / 8.0 // kN.m

        // عزم القصور الذاتي الفعال
        val Ie = if (Ma <= Mcr) {
            Ig
        } else {
            val term = (Mcr / Ma).pow(3)
            (term * Ig + (1 - term) * Icr).coerceAtMost(Ig)
        }

        equations.add(EquationStep(
            stepNum++, "عزم القصور الذاتي الفعال Ie",
            "Ie = (Mcr/Ma)³ × Ig + [1-(Mcr/Ma)³] × Icr",
            "Ma = ${EngineeringCalculations.formatNum(Ma)} kN.m, Ie = ${EngineeringCalculations.formatNum(Ie / 1e8)} × 10⁸ mm⁴",
            "Ie = ${EngineeringCalculations.formatNum(Ie / 1e8)} × 10⁸ mm⁴"
        ))

        // الترخيم الفوري
        val immediateDeflection = 5 * wService * (span.pow(4)) / (384 * Ec * Ie) // mm

        // الترخيم طويل الأمد
        val xi = 2.0 // for 5+ years
        val rhoP = 0.0 // compression steel ratio (simplified)
        val lambdaFactor = xi / (1 + 50 * rhoP)
        val longTermDeflection = immediateDeflection * (1 + lambdaFactor)

        equations.add(EquationStep(
            stepNum++, "الترخيم",
            "Δ_immediate = 5wL⁴/(384EcIe)",
            "Δ_imm = ${EngineeringCalculations.formatNum(immediateDeflection)} mm",
            "Δ_long = ${EngineeringCalculations.formatNum(longTermDeflection)} mm (مع الزحف)"
        ))

        // الحد المسموح
        val allowable = span / 250.0
        equations.add(EquationStep(
            stepNum++, "فحص الترخيم",
            "Δ ≤ L/250",
            "Δ = ${EngineeringCalculations.formatNum(longTermDeflection)} mm, L/250 = ${EngineeringCalculations.formatNum(allowable)} mm",
            if (longTermDeflection <= allowable) "آمن ✓" else "تجاوز الحد ✗"
        ))

        return DeflectionResult(
            immediateDeflection = immediateDeflection,
            longTermDeflection = longTermDeflection,
            allowableDeflection = allowable,
            crackingMoment = Mcr,
            effectiveMomentOfInertia = Ie,
            isSafe = longTermDeflection <= allowable,
            equations = equations
        )
    }

    data class DeflectionResult(
        val immediateDeflection: Double,
        val longTermDeflection: Double,
        val allowableDeflection: Double,
        val crackingMoment: Double,
        val effectiveMomentOfInertia: Double,
        val isSafe: Boolean,
        val equations: List<EquationStep>
    )
}
