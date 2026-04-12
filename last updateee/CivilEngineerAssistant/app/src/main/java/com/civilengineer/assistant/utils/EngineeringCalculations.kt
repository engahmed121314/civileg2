package com.civilengineer.assistant.utils

import com.civilengineer.assistant.models.*
import kotlin.math.*

/**
 * محرك الحسابات الهندسية الرئيسي
 * Main Engineering Calculations Engine
 *
 * يحتوي على جميع المعادلات الأساسية المستخدمة في التصميم الإنشائي
 * حسب الكود المصري ECP 203، الكود السعودي SBC 304، الكود الأمريكي ACI 318
 */
object EngineeringCalculations {

    // ═══════════════════════════════════════════════════════════
    // 1. حسابات القطاع المعرض للانحناء - Flexure Design
    // ═══════════════════════════════════════════════════════════

    /**
     * حساب عمق المحور المتعادل ومساحة التسليح المطلوبة
     * Calculate neutral axis depth and required reinforcement area
     *
     * @param Mu العزم التصميمي (kN.m)
     * @param b عرض القطاع (mm)
     * @param d العمق الفعال (mm)
     * @param fcu رتبة الخرسانة (N/mm²)
     * @param fy رتبة الحديد (N/mm²)
     * @param code الكود المستخدم
     * @return مساحة التسليح المطلوبة (mm²) وخطوات الحل
     */
    fun flexureDesign(
        Mu: Double,
        b: Double,
        d: Double,
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): FlexureResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        return when (code) {
            DesignCode.EGYPTIAN -> {
                // الكود المصري ECP 203-2020
                val gammaC = 1.50
                val gammaS = 1.15

                // R = Mu / (fcu * b * d²)
                val R = Mu * 1e6 / (fcu / gammaC * b * d * d)
                equations.add(EquationStep(
                    stepNum++, "حساب معامل العزم R",
                    "R = Mu / (fcu/γc × b × d²)",
                    "R = ${formatNum(Mu * 1e6)} / (${formatNum(fcu)}/${gammaC} × ${formatNum(b)} × ${formatNum(d)}²)",
                    "R = ${formatNum(R)}",
                    "ECP 203 - Clause 4.2.1",
                    "معامل العزم النسبي يستخدم لتحديد ما إذا كان القطاع يحتاج تسليح مزدوج"
                ))

                // Check Rmax
                val Rmax = 0.194  // For fy = 360/400
                val needsDoublyReinforced = R > Rmax

                if (needsDoublyReinforced) {
                    equations.add(EquationStep(
                        stepNum++, "فحص تسليح مفرد/مزدوج",
                        "R > R_max = ${formatNum(Rmax)}",
                        "R = ${formatNum(R)} > ${formatNum(Rmax)}",
                        "القطاع يحتاج تسليح مزدوج",
                        "ECP 203 - Clause 4.2.1.2",
                        "عندما يتجاوز R قيمة Rmax يجب استخدام تسليح ضغط"
                    ))
                }

                // ω = 1 - √(1 - 2R)
                val omega = if (R < 0.5) 1.0 - sqrt(1.0 - 2.0 * R) else R // Simplified for large R
                equations.add(EquationStep(
                    stepNum++, "حساب معامل التسليح ω",
                    "ω = 1 - √(1 - 2R)",
                    "ω = 1 - √(1 - 2 × ${formatNum(R)})",
                    "ω = ${formatNum(omega)}",
                    "ECP 203",
                    "معامل التسليح يربط بين عمق المحور المتعادل ومساحة التسليح"
                ))

                // As = ω × (fcu/γc) × b × d / (fy/γs)
                val As = omega * (fcu / gammaC) * b * d / (fy / gammaS)
                equations.add(EquationStep(
                    stepNum++, "حساب مساحة التسليح المطلوبة As",
                    "As = ω × (fcu/γc) × b × d / (fy/γs)",
                    "As = ${formatNum(omega)} × (${formatNum(fcu)}/${gammaC}) × ${formatNum(b)} × ${formatNum(d)} / (${formatNum(fy)}/${gammaS})",
                    "As = ${formatNum(As)} mm²",
                    "ECP 203 - Clause 4.2.1",
                    "مساحة حديد التسليح المطلوبة لمقاومة العزم التصميمي"
                ))

                // As_min
                val rhoMin = EngineeringConstants.getBeamMinRatio(code, fcu, fy)
                val AsMin = rhoMin * b * d
                equations.add(EquationStep(
                    stepNum++, "حساب الحد الأدنى للتسليح As_min",
                    "As_min = ρ_min × b × d",
                    "As_min = ${formatNum(rhoMin)} × ${formatNum(b)} × ${formatNum(d)}",
                    "As_min = ${formatNum(AsMin)} mm²",
                    "ECP 203 - Clause 4.2.1.1",
                    "الحد الأدنى للتسليح يضمن عدم الانهيار المفاجئ"
                ))

                val finalAs = maxOf(As, AsMin)
                val a = finalAs * (fy / gammaS) / (0.67 * fcu / gammaC * b) // depth of stress block
                val rho = finalAs / (b * d)

                FlexureResult(
                    requiredAs = finalAs,
                    neutralAxisDepth = a / 0.8,
                    stressBlockDepth = a,
                    isDoublyReinforced = needsDoublyReinforced,
                    ratio = rho,
                    equations = equations
                )
            }

            DesignCode.SAUDI -> {
                // الكود السعودي SBC 304 (مشابه للكود المصري مع بعض الاختلافات)
                val gammaC = 1.50
                val gammaS = 1.15

                val R = Mu * 1e6 / (fcu / gammaC * b * d * d)
                equations.add(EquationStep(
                    stepNum++, "حساب معامل العزم R",
                    "R = Mu / (fcu/γc × b × d²)",
                    "R = ${formatNum(Mu * 1e6)} / (${formatNum(fcu)}/${gammaC} × ${formatNum(b)} × ${formatNum(d)}²)",
                    "R = ${formatNum(R)}",
                    "SBC 304 - Section 10.2",
                    "معامل العزم النسبي حسب الكود السعودي"
                ))

                val omega = if (R < 0.5) 1.0 - sqrt(1.0 - 2.0 * R) else R
                equations.add(EquationStep(
                    stepNum++, "حساب معامل التسليح ω",
                    "ω = 1 - √(1 - 2R)",
                    "ω = 1 - √(1 - 2 × ${formatNum(R)})",
                    "ω = ${formatNum(omega)}",
                    "SBC 304"
                ))

                val As = omega * (fcu / gammaC) * b * d / (fy / gammaS)
                equations.add(EquationStep(
                    stepNum++, "حساب مساحة التسليح As",
                    "As = ω × (fcu/γc) × b × d / (fy/γs)",
                    "As = ${formatNum(omega)} × (${formatNum(fcu)}/${gammaC}) × ${formatNum(b)} × ${formatNum(d)} / (${formatNum(fy)}/${gammaS})",
                    "As = ${formatNum(As)} mm²",
                    "SBC 304 - Section 10.3"
                ))

                val rhoMin = EngineeringConstants.getBeamMinRatio(code, fcu, fy)
                val AsMin = rhoMin * b * d
                val finalAs = maxOf(As, AsMin)
                val a = finalAs * (fy / gammaS) / (0.67 * fcu / gammaC * b)
                val rho = finalAs / (b * d)

                FlexureResult(
                    requiredAs = finalAs,
                    neutralAxisDepth = a / 0.8,
                    stressBlockDepth = a,
                    isDoublyReinforced = false,
                    ratio = rho,
                    equations = equations
                )
            }

            DesignCode.AMERICAN -> {
                // الكود الأمريكي ACI 318-19
                val phi = 0.90  // φ for flexure
                val fc = fcu * 0.80  // f'c = 0.8 * fcu

                // Rn = Mu / (φ × b × d²)
                val Rn = Mu * 1e6 / (phi * b * d * d)
                equations.add(EquationStep(
                    stepNum++, "حساب مقاومة العزم الاسمية Rn",
                    "Rn = Mu / (φ × b × d²)",
                    "Rn = ${formatNum(Mu * 1e6)} / (${phi} × ${formatNum(b)} × ${formatNum(d)}²)",
                    "Rn = ${formatNum(Rn)} N/mm²",
                    "ACI 318-19 - Section 9.5.1",
                    "Rn هو معامل مقاومة العزم الاسمي"
                ))

                // ρ = 0.85f'c/fy × (1 - √(1 - 2Rn/(0.85f'c)))
                val term = 2.0 * Rn / (0.85 * fc)
                val rho = if (term < 1.0) {
                    0.85 * fc / fy * (1.0 - sqrt(1.0 - term))
                } else {
                    0.85 * fc / fy * 0.5
                }
                equations.add(EquationStep(
                    stepNum++, "حساب نسبة التسليح ρ",
                    "ρ = (0.85f'c/fy) × [1 - √(1 - 2Rn/(0.85f'c))]",
                    "ρ = (0.85×${formatNum(fc)}/${formatNum(fy)}) × [1 - √(1 - 2×${formatNum(Rn)}/(0.85×${formatNum(fc)}))]",
                    "ρ = ${formatNum(rho, 6)}",
                    "ACI 318-19 - Section 9.5.1"
                ))

                val As = rho * b * d
                equations.add(EquationStep(
                    stepNum++, "حساب مساحة التسليح As",
                    "As = ρ × b × d",
                    "As = ${formatNum(rho, 6)} × ${formatNum(b)} × ${formatNum(d)}",
                    "As = ${formatNum(As)} mm²",
                    "ACI 318-19"
                ))

                val rhoMin = EngineeringConstants.getBeamMinRatio(code, fcu, fy)
                val AsMin = rhoMin * b * d
                val finalAs = maxOf(As, AsMin)
                val a = finalAs * fy / (0.85 * fc * b)

                val beta1 = if (fc <= 28) 0.85 else maxOf(0.65, 0.85 - 0.05 * (fc - 28) / 7)

                FlexureResult(
                    requiredAs = finalAs,
                    neutralAxisDepth = a / beta1,
                    stressBlockDepth = a,
                    isDoublyReinforced = false,
                    ratio = finalAs / (b * d),
                    equations = equations
                )
            }
        }
    }

    data class FlexureResult(
        val requiredAs: Double,
        val neutralAxisDepth: Double,
        val stressBlockDepth: Double,
        val isDoublyReinforced: Boolean,
        val ratio: Double,
        val equations: List<EquationStep>
    )

    // ═══════════════════════════════════════════════════════════
    // 2. حسابات القص - Shear Design
    // ═══════════════════════════════════════════════════════════

    /**
     * تصميم القطاع لمقاومة القص
     *
     * @param Vu قوة القص التصميمية (kN)
     * @param b عرض القطاع (mm)
     * @param d العمق الفعال (mm)
     * @param fcu رتبة الخرسانة (N/mm²)
     * @param fy رتبة حديد الكانات (N/mm²)
     * @param code الكود المستخدم
     */
    fun shearDesign(
        Vu: Double,
        b: Double,
        d: Double,
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): ShearDesignResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        return when (code) {
            DesignCode.EGYPTIAN -> {
                val gammaC = 1.50

                // qcu = 0.24 × √(fcu/γc) (N/mm²)
                val qcu = 0.24 * sqrt(fcu / gammaC)
                equations.add(EquationStep(
                    stepNum++, "مقاومة الخرسانة للقص qcu",
                    "qcu = 0.24 × √(fcu/γc)",
                    "qcu = 0.24 × √(${formatNum(fcu)}/${gammaC})",
                    "qcu = ${formatNum(qcu)} N/mm²",
                    "ECP 203 - Clause 4.2.2.1",
                    "مقاومة القص التي تتحملها الخرسانة بدون كانات"
                ))

                // Vc = qcu × b × d
                val Vc = qcu * b * d / 1000.0 // kN
                equations.add(EquationStep(
                    stepNum++, "قوة القص المقاومة بالخرسانة Vc",
                    "Vc = qcu × b × d",
                    "Vc = ${formatNum(qcu)} × ${formatNum(b)} × ${formatNum(d)} / 1000",
                    "Vc = ${formatNum(Vc)} kN",
                    "ECP 203 - Clause 4.2.2.1"
                ))

                // qu = Vu / (b × d)
                val qu = Vu * 1000.0 / (b * d)
                equations.add(EquationStep(
                    stepNum++, "إجهاد القص الفعلي qu",
                    "qu = Vu / (b × d)",
                    "qu = ${formatNum(Vu * 1000)} / (${formatNum(b)} × ${formatNum(d)})",
                    "qu = ${formatNum(qu)} N/mm²",
                    "ECP 203"
                ))

                // qu_max = 0.7 × √(fcu/γc) ≤ 4.4 N/mm²
                val quMax = minOf(0.7 * sqrt(fcu / gammaC), 4.4)
                equations.add(EquationStep(
                    stepNum++, "أقصى إجهاد قص مسموح qu_max",
                    "qu_max = 0.7 × √(fcu/γc) ≤ 4.4 N/mm²",
                    "qu_max = 0.7 × √(${formatNum(fcu)}/${gammaC})",
                    "qu_max = ${formatNum(quMax)} N/mm²",
                    "ECP 203 - Clause 4.2.2.1",
                    "إذا تجاوز إجهاد القص هذه القيمة يجب تكبير القطاع"
                ))

                val sectionAdequate = qu <= quMax

                // حساب الكانات
                val Vs = Vu - Vc
                var stirrupSpacing = 200.0
                var stirrupDiameter = 8.0
                var legs = 2

                if (Vs > 0) {
                    // Av/s = Vs × 1000 / (fy/γs × d)
                    val gammaS = 1.15
                    val AvsRequired = Vs * 1000.0 / ((fy / gammaS) * d)
                    equations.add(EquationStep(
                        stepNum++, "حساب تسليح القص المطلوب",
                        "Av/s = Vs / (fy/γs × d)",
                        "Av/s = ${formatNum(Vs * 1000)} / (${formatNum(fy)}/${gammaS} × ${formatNum(d)})",
                        "Av/s = ${formatNum(AvsRequired)} mm²/mm",
                        "ECP 203 - Clause 4.2.2.2"
                    ))

                    // اختيار قطر الكانة والتباعد
                    stirrupDiameter = if (AvsRequired > 0.5) 10.0 else 8.0
                    val Av = legs * PI * stirrupDiameter * stirrupDiameter / 4.0
                    stirrupSpacing = minOf(Av / AvsRequired, d / 2.0, 200.0)
                    stirrupSpacing = (floor(stirrupSpacing / 25.0) * 25.0).coerceAtLeast(75.0)

                    equations.add(EquationStep(
                        stepNum++, "تباعد الكانات",
                        "s = Av / (Av/s) ≤ d/2 ≤ 200mm",
                        "s = ${formatNum(Av)} / ${formatNum(AvsRequired)}",
                        "s = ${formatNum(stirrupSpacing)} mm",
                        "ECP 203 - Clause 4.2.2.2",
                        "يجب ألا يزيد التباعد عن d/2 أو 200mm"
                    ))
                } else {
                    // Minimum stirrups
                    stirrupSpacing = minOf(d / 2.0, 200.0)
                    equations.add(EquationStep(
                        stepNum++, "كانات إنشائية (حد أدنى)",
                        "s ≤ min(d/2, 200mm)",
                        "s ≤ min(${formatNum(d)}/2, 200)",
                        "s = ${formatNum(stirrupSpacing)} mm",
                        "ECP 203 - Clause 4.2.2.2"
                    ))
                }

                ShearDesignResult(
                    concreteCapacity = Vc,
                    steelRequired = maxOf(Vs, 0.0),
                    maxShearStress = qu,
                    allowableShearStress = quMax,
                    isSectionAdequate = sectionAdequate,
                    stirrupDiameter = stirrupDiameter,
                    stirrupLegs = legs,
                    stirrupSpacing = stirrupSpacing,
                    equations = equations
                )
            }

            DesignCode.SAUDI -> {
                val gammaC = 1.50

                val qcu = 0.24 * sqrt(fcu / gammaC)
                equations.add(EquationStep(
                    stepNum++, "مقاومة الخرسانة للقص qcu",
                    "qcu = 0.24 × √(fcu/γc)",
                    "qcu = 0.24 × √(${formatNum(fcu)}/${gammaC})",
                    "qcu = ${formatNum(qcu)} N/mm²",
                    "SBC 304 - Section 11.2"
                ))

                val Vc = qcu * b * d / 1000.0
                val qu = Vu * 1000.0 / (b * d)
                val quMax = minOf(0.7 * sqrt(fcu / gammaC), 4.4)

                val Vs = Vu - Vc
                var stirrupSpacing = 200.0
                var stirrupDiameter = 8.0
                var legs = 2

                if (Vs > 0) {
                    val gammaS = 1.15
                    val AvsRequired = Vs * 1000.0 / ((fy / gammaS) * d)
                    stirrupDiameter = if (AvsRequired > 0.5) 10.0 else 8.0
                    val Av = legs * PI * stirrupDiameter * stirrupDiameter / 4.0
                    stirrupSpacing = minOf(Av / AvsRequired, d / 2.0, 200.0)
                    stirrupSpacing = (floor(stirrupSpacing / 25.0) * 25.0).coerceAtLeast(75.0)
                }

                ShearDesignResult(
                    concreteCapacity = Vc,
                    steelRequired = maxOf(Vs, 0.0),
                    maxShearStress = qu,
                    allowableShearStress = quMax,
                    isSectionAdequate = qu <= quMax,
                    stirrupDiameter = stirrupDiameter,
                    stirrupLegs = legs,
                    stirrupSpacing = stirrupSpacing,
                    equations = equations
                )
            }

            DesignCode.AMERICAN -> {
                val phi = 0.75
                val fc = fcu * 0.80  // f'c

                // Vc = 0.17 × √f'c × b × d (ACI 318-19)
                val Vc = 0.17 * sqrt(fc) * b * d / 1000.0 // kN
                equations.add(EquationStep(
                    stepNum++, "مقاومة الخرسانة للقص Vc",
                    "Vc = 0.17 × √f'c × bw × d",
                    "Vc = 0.17 × √${formatNum(fc)} × ${formatNum(b)} × ${formatNum(d)} / 1000",
                    "Vc = ${formatNum(Vc)} kN",
                    "ACI 318-19 - Section 22.5.5.1",
                    "مقاومة القص المقدمة من الخرسانة فقط"
                ))

                // φVn_max = φ × (Vc + 0.66√f'c × b × d)
                val VsMax = 0.66 * sqrt(fc) * b * d / 1000.0
                val phiVnMax = phi * (Vc + VsMax)
                equations.add(EquationStep(
                    stepNum++, "أقصى مقاومة قص",
                    "φVn_max = φ(Vc + 0.66√f'c × bw × d)",
                    "φVn_max = ${phi} × (${formatNum(Vc)} + ${formatNum(VsMax)})",
                    "φVn_max = ${formatNum(phiVnMax)} kN",
                    "ACI 318-19 - Section 22.5.1.2"
                ))

                val Vs = maxOf(Vu / phi - Vc, 0.0)
                var stirrupSpacing = 200.0
                var stirrupDiameter = 10.0 // #3 bar
                var legs = 2

                if (Vs > 0) {
                    val AvsRequired = Vs * 1000.0 / (fy * d)
                    equations.add(EquationStep(
                        stepNum++, "حساب تسليح القص Av/s",
                        "Av/s = Vs / (fy × d)",
                        "Av/s = ${formatNum(Vs * 1000)} / (${formatNum(fy)} × ${formatNum(d)})",
                        "Av/s = ${formatNum(AvsRequired)} mm²/mm",
                        "ACI 318-19 - Section 22.5.10.5.3"
                    ))

                    stirrupDiameter = if (AvsRequired > 0.5) 12.0 else 10.0
                    val Av = legs * PI * stirrupDiameter * stirrupDiameter / 4.0
                    stirrupSpacing = minOf(Av / AvsRequired, d / 2.0, 600.0)
                    stirrupSpacing = (floor(stirrupSpacing / 25.0) * 25.0).coerceAtLeast(75.0)

                    equations.add(EquationStep(
                        stepNum++, "تباعد الكانات",
                        "s = Av / (Av/s) ≤ d/2",
                        "s = ${formatNum(Av)} / ${formatNum(AvsRequired)}",
                        "s = ${formatNum(stirrupSpacing)} mm",
                        "ACI 318-19 - Section 9.7.6.2.2"
                    ))
                }

                ShearDesignResult(
                    concreteCapacity = Vc,
                    steelRequired = Vs,
                    maxShearStress = Vu * 1000.0 / (b * d),
                    allowableShearStress = phiVnMax * 1000.0 / (b * d),
                    isSectionAdequate = Vu <= phiVnMax,
                    stirrupDiameter = stirrupDiameter,
                    stirrupLegs = legs,
                    stirrupSpacing = stirrupSpacing,
                    equations = equations
                )
            }
        }
    }

    data class ShearDesignResult(
        val concreteCapacity: Double,
        val steelRequired: Double,
        val maxShearStress: Double,
        val allowableShearStress: Double,
        val isSectionAdequate: Boolean,
        val stirrupDiameter: Double,
        val stirrupLegs: Int,
        val stirrupSpacing: Double,
        val equations: List<EquationStep>
    )

    // ═══════════════════════════════════════════════════════════
    // 3. حسابات الأعمدة - Column Design
    // ═══════════════════════════════════════════════════════════

    /**
     * تصميم عمود قصير تحت حمل محوري مع عزم
     */
    fun columnDesign(
        Pu: Double,      // kN - حمل محوري تصميمي
        Mu: Double,       // kN.m - عزم تصميمي
        b: Double,        // mm - عرض
        h: Double,        // mm - ارتفاع
        length: Double,   // mm - طول العمود
        fcu: Double,      // N/mm²
        fy: Double,       // N/mm²
        code: DesignCode,
        columnType: ColumnType = ColumnType.SHORT_RECTANGULAR
    ): ColumnDesignResult {
        val equations = mutableListOf<EquationStep>()
        val codeChecks = mutableListOf<CodeCheck>()
        var stepNum = 1

        // فحص نحافة العمود
        val kFactor = 1.0 // effective length factor (conservative for braced)
        val effectiveLength = kFactor * length
        val radius = h / sqrt(12.0)
        val slendernessRatio = effectiveLength / radius

        equations.add(EquationStep(
            stepNum++, "حساب نسبة النحافة",
            "λ = K × Lu / r",
            "λ = ${formatNum(kFactor)} × ${formatNum(length)} / ${formatNum(radius)}",
            "λ = ${formatNum(slendernessRatio)}",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP 203 - Clause 6.3.1"
                DesignCode.SAUDI -> "SBC 304 - Section 6.2.5"
                DesignCode.AMERICAN -> "ACI 318-19 - Section 6.2.5"
            },
            "نسبة النحافة تحدد ما إذا كان العمود قصيراً أو طويلاً"
        ))

        val isShort = when (code) {
            DesignCode.EGYPTIAN -> slendernessRatio <= 15
            DesignCode.SAUDI -> slendernessRatio <= 22
            DesignCode.AMERICAN -> slendernessRatio <= 22
        }

        codeChecks.add(CodeCheck(
            "فحص نحافة العمود",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP: λ ≤ 15 (قصير)"
                DesignCode.SAUDI -> "SBC: λ ≤ 22 (قصير)"
                DesignCode.AMERICAN -> "ACI: klu/r ≤ 22 (قصير)"
            },
            if (isShort) "≤ الحد" else "> الحد",
            formatNum(slendernessRatio),
            isShort,
            if (isShort) "العمود قصير - لا حاجة لتأثيرات الرتبة الثانية" else "العمود طويل - يجب مراعاة تأثيرات الرتبة الثانية"
        ))

        return when (code) {
            DesignCode.EGYPTIAN -> {
                val gammaC = 1.50
                val gammaS = 1.15
                val Ag = b * h

                // اللامركزية
                var e = if (Mu > 0) Mu * 1000.0 / Pu else 0.0
                val eMin = maxOf(0.05 * h, 20.0) // Minimum eccentricity ECP
                e = maxOf(e, eMin)

                equations.add(EquationStep(
                    stepNum++, "حساب اللامركزية",
                    "e = Mu/Pu ≥ e_min = max(0.05h, 20mm)",
                    "e = ${if (Mu > 0) "${formatNum(Mu * 1000)}/${formatNum(Pu)}" else "0"}, e_min = max(0.05×${formatNum(h)}, 20)",
                    "e = ${formatNum(e)} mm",
                    "ECP 203 - Clause 6.2.1",
                    "اللامركزية الدنيا تراعي عدم الدقة في التنفيذ"
                ))

                // معادلة تفاعل الأعمدة المبسطة (ECP)
                // Pu = 0.35 fcu × Ac + 0.67 fy × As (for e/h ≤ 0.05 → axial)
                // For combined: use interaction diagram approach

                val eRatio = e / h

                val As: Double
                if (eRatio <= 0.1) {
                    // حمل محوري مع لامركزية صغيرة
                    // Pu ≤ 0.35 × fcu × Ac/γc + 0.67 × fy × As/γs
                    // As = (Pu - 0.35 × fcu × Ag/γc) / (0.67 × fy/γs)
                    val PuCapacityConcrete = 0.35 * fcu / gammaC * Ag / 1000.0
                    As = maxOf(
                        (Pu - PuCapacityConcrete) * 1000.0 / (0.67 * fy / gammaS),
                        EngineeringConstants.getColumnMinRatio(code) * Ag
                    )

                    equations.add(EquationStep(
                        stepNum++, "حساب تسليح العمود (لامركزية صغيرة)",
                        "Pu = 0.35(fcu/γc)Ag + 0.67(fy/γs)As",
                        "As = (Pu - 0.35×fcu/γc×Ag) / (0.67×fy/γs)",
                        "As = ${formatNum(As)} mm²",
                        "ECP 203 - Clause 6.2",
                        "معادلة العمود تحت حمل محوري مع لامركزية صغيرة"
                    ))
                } else {
                    // حمل محوري مع عزم - استخدام منحنيات التفاعل
                    val cover = 40.0 // mm
                    val d = h - cover
                    val dPrime = cover

                    // Simplified interaction: As on each face
                    // Using rectangular stress block approach
                    val MuDesign = Pu * e / 1000.0 // kN.m

                    // Balanced condition check
                    val cb = 600.0 * d / (600.0 + fy / gammaS)
                    val ab = 0.8 * cb

                    val Pb = (0.67 * fcu / gammaC * ab * b) / 1000.0 // kN
                    val Mb = Pb * (h / 2 - ab / 2) / 1000.0

                    if (Pu > Pb) {
                        // Compression controlled
                        val AsCalc = maxOf(
                            (Pu * 1000.0 - 0.35 * fcu / gammaC * Ag) / (0.67 * fy / gammaS - 0.35 * fcu / gammaC),
                            EngineeringConstants.getColumnMinRatio(code) * Ag
                        )
                        As = AsCalc
                    } else {
                        // Tension controlled - beam-column behavior
                        val AsFlexure = flexureDesign(MuDesign, b, d, fcu, fy, code)
                        As = maxOf(
                            AsFlexure.requiredAs,
                            EngineeringConstants.getColumnMinRatio(code) * Ag
                        )
                    }

                    equations.add(EquationStep(
                        stepNum++, "حساب تسليح العمود (لامركزية كبيرة)",
                        "باستخدام منحنى التفاعل (Interaction Diagram)",
                        "e/h = ${formatNum(eRatio)}, Pu = ${formatNum(Pu)} kN, Mu = ${formatNum(MuDesign)} kN.m",
                        "As_total = ${formatNum(As)} mm²",
                        "ECP 203 - Clause 6.2",
                        "عند وجود لامركزية كبيرة يتم استخدام منحنيات التفاعل أو المعادلة المباشرة"
                    ))
                }

                // حدود التسليح
                val AsMin = EngineeringConstants.getColumnMinRatio(code) * Ag
                val AsMax = EngineeringConstants.getColumnMaxRatio(code) * Ag
                val AsFinal = As.coerceIn(AsMin, AsMax)

                codeChecks.add(CodeCheck(
                    "نسبة التسليح",
                    "ECP: 0.8% ≤ ρ ≤ 4%",
                    "${formatNum(AsMin)} - ${formatNum(AsMax)} mm²",
                    "${formatNum(AsFinal)} mm²",
                    AsFinal >= AsMin && AsFinal <= AsMax
                ))

                // اختيار الأقطار
                val selectedRebar = selectRebar(AsFinal, b, h, 40.0)

                // حساب الكانات
                val stirrupDia = if (selectedRebar.barDiameter >= 25) 10.0 else 8.0
                val stirrupSpacing = minOf(
                    15.0 * selectedRebar.barDiameter,
                    minOf(b, h),
                    200.0
                )

                equations.add(EquationStep(
                    stepNum++, "تباعد كانات العمود",
                    "s ≤ min(15φ, أقل بعد, 200mm)",
                    "s ≤ min(15×${formatNum(selectedRebar.barDiameter)}, ${formatNum(minOf(b, h))}, 200)",
                    "s = ${formatNum(stirrupSpacing)} mm",
                    "ECP 203 - Clause 6.4.2",
                    "كانات العمود تمنع انبعاج حديد التسليح الطولي"
                ))

                // سعة العمود
                val PuCapacity = (0.35 * fcu / gammaC * (Ag - AsFinal) + 0.67 * fy / gammaS * AsFinal) / 1000.0
                val safetyFactor = PuCapacity / Pu

                ColumnDesignResult(
                    requiredAs = AsFinal,
                    providedAs = selectedRebar.totalArea,
                    numberOfBars = selectedRebar.count,
                    barDiameter = selectedRebar.barDiameter,
                    ratio = AsFinal / Ag,
                    stirrupDiameter = stirrupDia,
                    stirrupSpacing = stirrupSpacing,
                    capacity = PuCapacity,
                    safetyFactor = safetyFactor,
                    isSafe = safetyFactor >= 1.0,
                    isShortColumn = isShort,
                    slendernessRatio = slendernessRatio,
                    equations = equations,
                    codeChecks = codeChecks
                )
            }

            DesignCode.SAUDI, DesignCode.AMERICAN -> {
                // ACI / SBC approach with φ factors
                val phi = if (columnType == ColumnType.SHORT_CIRCULAR) 0.75 else 0.65
                val fc = if (code == DesignCode.AMERICAN) fcu * 0.80 else fcu
                val Ag = b * h
                val cover = 40.0
                val d = h - cover

                var e = if (Mu > 0) Mu * 1000.0 / Pu else 0.0
                val eMin = maxOf(0.05 * h, 15.0)
                e = maxOf(e, eMin)

                equations.add(EquationStep(
                    stepNum++, "حساب اللامركزية",
                    "e = Mu/Pu ≥ e_min",
                    "e = ${formatNum(e)} mm",
                    "e_min = ${formatNum(eMin)} mm",
                    if (code == DesignCode.AMERICAN) "ACI 318-19 - 6.6.4.5.4" else "SBC 304"
                ))

                // Pn_max = 0.80 × [0.85f'c(Ag - Ast) + fyAst] for tied columns
                val rhoTarget = 0.02 // Start with 2%
                val AsEstimate = rhoTarget * Ag
                val PnMax = 0.80 * (0.85 * fc * (Ag - AsEstimate) + fy * AsEstimate) / 1000.0

                // Required Ast
                val AstRequired = if (Pu / phi > 0.85 * fc * Ag / 1000.0) {
                    ((Pu / phi * 1000.0 - 0.85 * fc * Ag) / (fy - 0.85 * fc)).coerceAtLeast(0.01 * Ag)
                } else {
                    0.01 * Ag
                }

                val AsFinal = AstRequired.coerceIn(0.01 * Ag, 0.08 * Ag)

                equations.add(EquationStep(
                    stepNum++, "حساب تسليح العمود",
                    "φPn = φ × 0.80[0.85f'c(Ag-Ast) + fyAst]",
                    "Ast = (Pu/φ - 0.85f'cAg) / (fy - 0.85f'c)",
                    "Ast = ${formatNum(AsFinal)} mm²",
                    if (code == DesignCode.AMERICAN) "ACI 318-19 - Section 22.4.2" else "SBC 304 - Section 10.3"
                ))

                val selectedRebar = selectRebar(AsFinal, b, h, cover)
                val stirrupDia = if (selectedRebar.barDiameter >= 25) 10.0 else 8.0
                val stirrupSpacing = minOf(
                    16.0 * selectedRebar.barDiameter,
                    48.0 * stirrupDia,
                    minOf(b, h)
                )

                val PuCapacity = phi * 0.80 * (0.85 * fc * (Ag - selectedRebar.totalArea) + fy * selectedRebar.totalArea) / 1000.0
                val safetyFactor = PuCapacity / Pu

                ColumnDesignResult(
                    requiredAs = AsFinal,
                    providedAs = selectedRebar.totalArea,
                    numberOfBars = selectedRebar.count,
                    barDiameter = selectedRebar.barDiameter,
                    ratio = AsFinal / Ag,
                    stirrupDiameter = stirrupDia,
                    stirrupSpacing = stirrupSpacing,
                    capacity = PuCapacity,
                    safetyFactor = safetyFactor,
                    isSafe = safetyFactor >= 1.0,
                    isShortColumn = isShort,
                    slendernessRatio = slendernessRatio,
                    equations = equations,
                    codeChecks = codeChecks
                )
            }
        }
    }

    data class ColumnDesignResult(
        val requiredAs: Double,
        val providedAs: Double,
        val numberOfBars: Int,
        val barDiameter: Double,
        val ratio: Double,
        val stirrupDiameter: Double,
        val stirrupSpacing: Double,
        val capacity: Double,
        val safetyFactor: Double,
        val isSafe: Boolean,
        val isShortColumn: Boolean,
        val slendernessRatio: Double,
        val equations: List<EquationStep>,
        val codeChecks: List<CodeCheck>
    )

    // ═══════════════════════════════════════════════════════════
    // 4. حسابات القواعد - Foundation Design
    // ═══════════════════════════════════════════════════════════

    /**
     * تصميم قاعدة منفصلة
     */
    fun isolatedFootingDesign(
        Pu: Double,               // kN - حمل تصميمي
        Mu: Double,               // kN.m - عزم
        columnB: Double,          // mm - عرض العمود
        columnH: Double,          // mm - ارتفاع العمود
        soilBearing: Double,      // kN/m² - قدرة تحمل التربة
        fcu: Double,
        fy: Double,
        code: DesignCode,
        depthBelowGround: Double = 1500.0  // mm
    ): FootingDesignResult {
        val equations = mutableListOf<EquationStep>()
        val codeChecks = mutableListOf<CodeCheck>()
        var stepNum = 1

        // حمل الخدمة (إزالة معاملات الأمان)
        val loadFactor = when (code) {
            DesignCode.EGYPTIAN -> 1.50  // average
            DesignCode.SAUDI -> 1.50
            DesignCode.AMERICAN -> 1.40
        }
        val Pservice = Pu / loadFactor

        equations.add(EquationStep(
            stepNum++, "حمل الخدمة",
            "P_service = Pu / γ_avg",
            "P_service = ${formatNum(Pu)} / ${formatNum(loadFactor)}",
            "P_service = ${formatNum(Pservice)} kN",
            "تحويل من أحمال تصميمية إلى أحمال خدمة"
        ))

        // مساحة القاعدة المطلوبة (مع 10% إضافية للوزن الذاتي)
        val requiredArea = Pservice * 1.10 / soilBearing  // m²
        equations.add(EquationStep(
            stepNum++, "المساحة المطلوبة للقاعدة",
            "A_req = 1.1P / q_allow",
            "A_req = 1.1 × ${formatNum(Pservice)} / ${formatNum(soilBearing)}",
            "A_req = ${formatNum(requiredArea)} m²",
            "1.1 لمراعاة الوزن الذاتي للقاعدة والتربة فوقها"
        ))

        // أبعاد القاعدة (مربعة)
        val sideLength = ceil(sqrt(requiredArea) * 10.0) / 10.0  // round up to 0.1m
        val B = sideLength * 1000.0  // mm
        val L = B  // square footing

        equations.add(EquationStep(
            stepNum++, "أبعاد القاعدة",
            "B = L = √A_req (تقريب لأقرب 0.1م)",
            "B = √${formatNum(requiredArea)}",
            "B = L = ${formatNum(sideLength)} m = ${formatNum(B)} mm",
            "قاعدة مربعة"
        ))

        // إجهاد التربة الفعلي
        val actualSoilPressure = Pservice / (sideLength * sideLength)
        codeChecks.add(CodeCheck(
            "إجهاد التربة",
            "q_actual ≤ q_allowable",
            "${formatNum(soilBearing)} kN/m²",
            "${formatNum(actualSoilPressure)} kN/m²",
            actualSoilPressure <= soilBearing,
            "التحقق من أن إجهاد التربة لا يتجاوز قدرة التحمل المسموحة"
        ))

        // إجهاد التربة النهائي (للتصميم)
        val quSoil = Pu / (sideLength * sideLength)  // kN/m²
        equations.add(EquationStep(
            stepNum++, "ضغط التربة التصميمي",
            "q_u = Pu / A",
            "q_u = ${formatNum(Pu)} / ${formatNum(sideLength * sideLength)}",
            "q_u = ${formatNum(quSoil)} kN/m²",
            "إجهاد التربة التصميمي (أحمال معاملة)"
        ))

        // سمك القاعدة المبدئي
        val cover = when (code) {
            DesignCode.EGYPTIAN -> 50.0
            DesignCode.SAUDI -> 75.0
            DesignCode.AMERICAN -> 75.0
        }
        val estimatedDepth = maxOf(B / 5.0, 300.0)
        val t = ceil(estimatedDepth / 50.0) * 50.0  // Round to nearest 50mm
        val d = t - cover - 10.0  // assuming 20mm bar

        equations.add(EquationStep(
            stepNum++, "سمك القاعدة المبدئي",
            "t ≥ B/5 أو 300mm",
            "t = ${formatNum(estimatedDepth)} → ${formatNum(t)} mm",
            "d = t - cover - φ/2 = ${formatNum(d)} mm",
            "العمق الفعال للقاعدة"
        ))

        // فحص الاختراق (Punching Shear)
        val punchPerimeter: Double
        val punchArea: Double
        val criticalD = d

        when (code) {
            DesignCode.EGYPTIAN -> {
                // المحيط الحرج على بعد d/2 من وجه العمود
                val b0 = columnB + d
                val h0 = columnH + d
                punchPerimeter = 2.0 * (b0 + h0)
                punchArea = B * L - b0 * h0

                val Vp = quSoil * punchArea / 1e6  // kN
                val qp = Vp * 1000.0 / (punchPerimeter * d)  // N/mm²

                val gammaC = 1.50
                val alpha = 4.0  // for interior column
                val beta = columnB / columnH
                val qpAllowable = minOf(
                    0.316 * sqrt(fcu / gammaC),
                    0.316 * (0.50 + 1.0 / beta) * sqrt(fcu / gammaC),
                    0.8 * (0.20 + alpha * d / punchPerimeter) * sqrt(fcu / gammaC)
                )

                equations.add(EquationStep(
                    stepNum++, "فحص اختراق القص (Punching)",
                    "qp = Vp / (b₀ × d) ≤ qp_allow",
                    "qp = ${formatNum(qp)} N/mm², qp_allow = ${formatNum(qpAllowable)} N/mm²",
                    if (qp <= qpAllowable) "آمن ✓" else "غير آمن ✗",
                    "ECP 203 - Clause 4.2.2.3",
                    "فحص مقاومة الاختراق عند محيط حرج على بعد d/2 من العمود"
                ))

                codeChecks.add(CodeCheck(
                    "اختراق القص (Punching)",
                    "ECP 203 - 4.2.2.3",
                    "${formatNum(qpAllowable)} N/mm²",
                    "${formatNum(qp)} N/mm²",
                    qp <= qpAllowable
                ))
            }
            DesignCode.SAUDI, DesignCode.AMERICAN -> {
                val fc = if (code == DesignCode.AMERICAN) fcu * 0.80 else fcu
                val phi = 0.75
                val b0 = columnB + d
                val h0 = columnH + d
                punchPerimeter = 2.0 * (b0 + h0)
                punchArea = B * L - b0 * h0

                val Vp = quSoil * punchArea / 1e6
                val vc = minOf(
                    0.33 * sqrt(fc),
                    0.17 * (1 + 2.0 / (columnB / columnH)) * sqrt(fc),
                    0.083 * (2 + 40.0 * d / punchPerimeter) * sqrt(fc)
                )
                val phiVc = phi * vc * punchPerimeter * d / 1000.0

                codeChecks.add(CodeCheck(
                    "اختراق القص (Punching)",
                    if (code == DesignCode.AMERICAN) "ACI 318-19 - 22.6.5" else "SBC 304",
                    "${formatNum(phiVc)} kN",
                    "${formatNum(Vp)} kN",
                    Vp <= phiVc
                ))
            }
        }

        // تصميم الانحناء
        val overhang = (B - columnB) / 2.0  // mm
        val MuFooting = quSoil / 1e6 * B * overhang * overhang / 2.0 * 1e-3  // kN.m per mm width → kN.m

        val MuPerMeter = quSoil * (overhang / 1000.0).pow(2) / 2.0 * sideLength  // kN.m

        equations.add(EquationStep(
            stepNum++, "عزم الانحناء في القاعدة",
            "Mu = q_u × B × l² / 2",
            "Mu = ${formatNum(quSoil)} × ${formatNum(sideLength)} × (${formatNum(overhang / 1000)})² / 2",
            "Mu = ${formatNum(MuPerMeter)} kN.m",
            "العزم عند وجه العمود"
        ))

        // حساب التسليح
        val flexResult = flexureDesign(MuPerMeter, B, d, fcu, fy, code)
        equations.addAll(flexResult.equations)

        // التسليح الأدنى
        val AsMinRatio = EngineeringConstants.getSlabMinRatio(code, fy)
        val AsMin = AsMinRatio * B * d
        val AsFinal = maxOf(flexResult.requiredAs, AsMin)

        // اختيار الحديد
        val selectedRebar = selectRebarForSlab(AsFinal, B)

        val isSafe = actualSoilPressure <= soilBearing

        FootingDesignResult(
            footingWidth = sideLength,
            footingLength = sideLength,
            footingDepth = t / 1000.0,
            effectiveDepth = d,
            soilPressure = actualSoilPressure,
            requiredAs = AsFinal,
            providedAs = selectedRebar.totalArea,
            barDiameter = selectedRebar.barDiameter,
            numberOfBars = selectedRebar.count,
            spacing = selectedRebar.spacing,
            isSafe = isSafe,
            equations = equations,
            codeChecks = codeChecks
        )
    }

    data class FootingDesignResult(
        val footingWidth: Double,       // m
        val footingLength: Double,      // m
        val footingDepth: Double,       // m
        val effectiveDepth: Double,     // mm
        val soilPressure: Double,       // kN/m²
        val requiredAs: Double,         // mm²
        val providedAs: Double,         // mm²
        val barDiameter: Double,        // mm
        val numberOfBars: Int,
        val spacing: Double,            // mm
        val isSafe: Boolean,
        val equations: List<EquationStep>,
        val codeChecks: List<CodeCheck>
    )

    // ═══════════════════════════════════════════════════════════
    // 5. تصميم البلاطات - Slab Design
    // ═══════════════════════════════════════════════════════════

    /**
     * تصميم بلاطة مصمتة
     */
    fun solidSlabDesign(
        spanX: Double,        // m - البحر في اتجاه X
        spanY: Double,        // m - البحر في اتجاه Y
        deadLoad: Double,     // kN/m² - حمل ميت إضافي (بدون الوزن الذاتي)
        liveLoad: Double,     // kN/m² - حمل حي
        fcu: Double,
        fy: Double,
        code: DesignCode,
        slabType: SlabType = SlabType.SOLID_TWO_WAY
    ): SlabDesignResult {
        val equations = mutableListOf<EquationStep>()
        val codeChecks = mutableListOf<CodeCheck>()
        var stepNum = 1

        // نسبة الأبعاد
        val ratio = if (spanY > spanX) spanY / spanX else spanX / spanY
        val isOneWay = ratio >= 2.0
        val shortSpan = minOf(spanX, spanY)
        val longSpan = maxOf(spanX, spanY)

        equations.add(EquationStep(
            stepNum++, "تحديد اتجاه البلاطة",
            "r = L_long / L_short",
            "r = ${formatNum(longSpan)} / ${formatNum(shortSpan)}",
            "r = ${formatNum(ratio)} → ${if (isOneWay) "باتجاه واحد" else "باتجاهين"}",
            "إذا r ≥ 2 → باتجاه واحد، إذا r < 2 → باتجاهين"
        ))

        // سمك البلاطة
        val typeStr = if (isOneWay) "one_way" else "two_way"
        val ts = EngineeringConstants.getInitialSlabThickness(shortSpan, typeStr, code)
        val thickness = ceil(ts / 10.0) * 10.0  // Round up to nearest 10mm
        val t = maxOf(thickness, 120.0)

        equations.add(EquationStep(
            stepNum++, "سمك البلاطة",
            when (code) {
                DesignCode.EGYPTIAN -> if (isOneWay) "t ≥ L/25" else "t ≥ L/32"
                DesignCode.SAUDI -> if (isOneWay) "t ≥ L/24" else "t ≥ L/30"
                DesignCode.AMERICAN -> if (isOneWay) "t ≥ L/24" else "t ≥ L/33"
            },
            "t = ${formatNum(shortSpan * 1000)} / ${if (isOneWay) "25" else "32"}",
            "t = ${formatNum(t)} mm",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP 203 - Table 4.1"
                DesignCode.SAUDI -> "SBC 304 - Table 9.5"
                DesignCode.AMERICAN -> "ACI 318-19 - Table 7.3.1.1"
            }
        ))

        val cover = when (code) {
            DesignCode.EGYPTIAN -> 20.0
            DesignCode.SAUDI -> 20.0
            DesignCode.AMERICAN -> 20.0
        }
        val d = t - cover - 5.0  // Assuming 10mm bar

        // الأحمال
        val selfWeight = t / 1000.0 * 25.0  // kN/m²
        val totalDead = selfWeight + deadLoad

        equations.add(EquationStep(
            stepNum++, "حساب الأحمال",
            "w_dead = γ_c × t + DL_additional",
            "w_dead = 25 × ${formatNum(t / 1000)} + ${formatNum(deadLoad)}",
            "w_dead = ${formatNum(totalDead)} kN/m²",
            "الحمل الميت الكلي"
        ))

        val (dfFactor, lfFactor) = EngineeringConstants.getUltimateLoadFactor(code)
        val wu = dfFactor * totalDead + lfFactor * liveLoad

        equations.add(EquationStep(
            stepNum++, "الحمل التصميمي النهائي",
            "wu = ${formatNum(dfFactor)}×DL + ${formatNum(lfFactor)}×LL",
            "wu = ${formatNum(dfFactor)}×${formatNum(totalDead)} + ${formatNum(lfFactor)}×${formatNum(liveLoad)}",
            "wu = ${formatNum(wu)} kN/m²",
            when (code) {
                DesignCode.EGYPTIAN -> "ECP 201"
                DesignCode.SAUDI -> "SBC 301"
                DesignCode.AMERICAN -> "ACI 318-19 - Section 5.3"
            }
        ))

        // حساب العزوم
        val MuShort: Double
        val MuLong: Double

        if (isOneWay) {
            // بلاطة باتجاه واحد
            MuShort = wu * shortSpan.pow(2) / 8.0  // kN.m per m
            MuLong = 0.0

            equations.add(EquationStep(
                stepNum++, "عزم الانحناء (اتجاه واحد)",
                "Mu = wu × L² / 8",
                "Mu = ${formatNum(wu)} × ${formatNum(shortSpan)}² / 8",
                "Mu = ${formatNum(MuShort)} kN.m/m",
                "عزم منتصف البحر لبلاطة بسيطة الإسناد"
            ))
        } else {
            // بلاطة باتجاهين - معاملات ماركوس أو الكود
            val alpha = when {
                ratio <= 1.0 -> 0.062
                ratio <= 1.1 -> 0.074
                ratio <= 1.2 -> 0.084
                ratio <= 1.3 -> 0.093
                ratio <= 1.4 -> 0.099
                ratio <= 1.5 -> 0.104
                ratio <= 1.6 -> 0.108
                ratio <= 1.7 -> 0.111
                ratio <= 1.8 -> 0.113
                ratio <= 1.9 -> 0.115
                else -> 0.118
            }
            val betaCoeff = when {
                ratio <= 1.0 -> 0.062
                ratio <= 1.1 -> 0.049
                ratio <= 1.2 -> 0.040
                ratio <= 1.3 -> 0.033
                ratio <= 1.4 -> 0.028
                ratio <= 1.5 -> 0.024
                ratio <= 1.6 -> 0.021
                ratio <= 1.7 -> 0.018
                ratio <= 1.8 -> 0.016
                ratio <= 1.9 -> 0.014
                else -> 0.013
            }

            MuShort = alpha * wu * shortSpan.pow(2)
            MuLong = betaCoeff * wu * shortSpan.pow(2)

            equations.add(EquationStep(
                stepNum++, "عزوم البلاطة (باتجاهين)",
                "Mu_short = α × wu × L²\nMu_long = β × wu × L²",
                "Mu_s = ${formatNum(alpha)} × ${formatNum(wu)} × ${formatNum(shortSpan)}²\nMu_l = ${formatNum(betaCoeff)} × ${formatNum(wu)} × ${formatNum(shortSpan)}²",
                "Mu_short = ${formatNum(MuShort)} kN.m/m\nMu_long = ${formatNum(MuLong)} kN.m/m",
                when (code) {
                    DesignCode.EGYPTIAN -> "ECP 203 - معاملات العزوم"
                    DesignCode.SAUDI -> "SBC 304"
                    DesignCode.AMERICAN -> "ACI 318-19 - Direct Design Method"
                }
            ))
        }

        // تسليح الاتجاه القصير
        val flexShort = flexureDesign(MuShort, 1000.0, d, fcu, fy, code)
        val AsMinRatio = EngineeringConstants.getSlabMinRatio(code, fy)
        val AsMin = AsMinRatio * 1000.0 * t  // mm²/m
        val AsShort = maxOf(flexShort.requiredAs, AsMin)

        // تسليح الاتجاه الطويل
        val AsLong: Double
        if (!isOneWay) {
            val dLong = d - 10.0  // second layer
            val flexLong = flexureDesign(maxOf(MuLong, 0.001), 1000.0, dLong, fcu, fy, code)
            AsLong = maxOf(flexLong.requiredAs, AsMin)
        } else {
            AsLong = AsMin  // Distribution steel
        }

        equations.addAll(flexShort.equations)

        // اختيار الحديد
        val rebarShort = selectRebarForSlab(AsShort, 1000.0)
        val rebarLong = selectRebarForSlab(AsLong, 1000.0)

        SlabDesignResult(
            thickness = t,
            effectiveDepth = d,
            isOneWay = isOneWay,
            spanRatio = ratio,
            ultimateLoad = wu,
            momentShortSpan = MuShort,
            momentLongSpan = MuLong,
            asShortRequired = AsShort,
            asLongRequired = AsLong,
            asShortProvided = rebarShort.totalArea,
            asLongProvided = rebarLong.totalArea,
            shortBarDiameter = rebarShort.barDiameter,
            shortBarSpacing = rebarShort.spacing,
            longBarDiameter = rebarLong.barDiameter,
            longBarSpacing = rebarLong.spacing,
            equations = equations,
            codeChecks = codeChecks
        )
    }

    data class SlabDesignResult(
        val thickness: Double,
        val effectiveDepth: Double,
        val isOneWay: Boolean,
        val spanRatio: Double,
        val ultimateLoad: Double,
        val momentShortSpan: Double,
        val momentLongSpan: Double,
        val asShortRequired: Double,
        val asLongRequired: Double,
        val asShortProvided: Double,
        val asLongProvided: Double,
        val shortBarDiameter: Double,
        val shortBarSpacing: Double,
        val longBarDiameter: Double,
        val longBarSpacing: Double,
        val equations: List<EquationStep>,
        val codeChecks: List<CodeCheck>
    )

    // ═══════════════════════════════════════════════════════════
    // 6. حسابات الزلازل - Seismic Analysis
    // ═══════════════════════════════════════════════════════════

    /**
     * تحليل زلزالي بطريقة القوة الاستاتيكية المكافئة
     */
    fun equivalentStaticAnalysis(
        totalWeight: Double,          // kN
        buildingHeight: Double,       // m
        storyHeights: List<Double>,   // m - ارتفاعات الأدوار
        storyWeights: List<Double>,   // kN - أوزان الأدوار
        zone: SeismicZone,
        importance: ImportanceCategory,
        soilType: SoilType,
        resistanceSystem: SeismicResistanceSystem,
        code: DesignCode
    ): SeismicResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        return when (code) {
            DesignCode.EGYPTIAN -> {
                // ECP 201-2012 (Egyptian Code for Loads)
                val ag = zone.zoneFactorECP  // Zone acceleration (g)
                val I = importance.factorECP
                val R = resistanceSystem.responseFactor

                // Soil factor S and TB, TC, TD
                val (S, TB, TC, TD) = when (soilType) {
                    SoilType.ROCK, SoilType.DENSE_GRAVEL -> SoilParams(1.0, 0.05, 0.25, 1.20)
                    SoilType.MEDIUM_GRAVEL, SoilType.DENSE_SAND -> SoilParams(1.2, 0.05, 0.25, 1.20)
                    SoilType.MEDIUM_SAND, SoilType.STIFF_CLAY -> SoilParams(1.15, 0.10, 0.25, 1.20)
                    SoilType.MEDIUM_CLAY -> SoilParams(1.35, 0.10, 0.30, 1.20)
                    SoilType.SOFT_CLAY, SoilType.LOOSE_SAND -> SoilParams(1.40, 0.15, 0.40, 2.00)
                    else -> SoilParams(1.40, 0.15, 0.40, 2.00)
                }

                equations.add(EquationStep(
                    stepNum++, "معاملات الزلازل",
                    "ag/g = ${formatNum(ag)}, I = ${formatNum(I)}, R = ${formatNum(R)}, S = ${formatNum(S)}",
                    "المنطقة: ${zone.displayName}\nالأهمية: ${importance.displayName}\nالنظام: ${resistanceSystem.displayName}",
                    "معاملات الكود المصري",
                    "ECP 201-2012"
                ))

                // Natural Period T
                val Ct = 0.075  // for RC frames
                val T = Ct * buildingHeight.pow(0.75)

                equations.add(EquationStep(
                    stepNum++, "الزمن الدوري الطبيعي T",
                    "T = Ct × H^0.75",
                    "T = ${formatNum(Ct)} × ${formatNum(buildingHeight)}^0.75",
                    "T = ${formatNum(T)} sec",
                    "ECP 201 - Clause 8.5.3"
                ))

                // Spectral acceleration Sd(T)
                val Sd: Double = when {
                    T < TB -> ag * S * (2.0 / 3.0 + T / TB * (2.5 / R * I - 2.0 / 3.0))
                    T in TB..TC -> ag * S * 2.5 / R * I
                    T in TC..TD -> ag * S * 2.5 / R * I * TC / T
                    else -> ag * S * 2.5 / R * I * TC * TD / (T * T)
                }

                equations.add(EquationStep(
                    stepNum++, "معامل الاستجابة الطيفية Sd(T)",
                    when {
                        T < TB -> "Sd = ag×S×(2/3 + T/TB×(2.5I/R - 2/3))"
                        T in TB..TC -> "Sd = ag×S×2.5×I/R"
                        T in TC..TD -> "Sd = ag×S×2.5×I/R×TC/T"
                        else -> "Sd = ag×S×2.5×I/R×TC×TD/T²"
                    },
                    "T = ${formatNum(T)}, ag = ${formatNum(ag)}, S = ${formatNum(S)}",
                    "Sd = ${formatNum(Sd)} g",
                    "ECP 201 - Clause 8.5.2"
                ))

                // Base Shear V = Sd × W / g
                val V = Sd * totalWeight
                equations.add(EquationStep(
                    stepNum++, "قوة القص القاعدية V",
                    "V = Sd × W",
                    "V = ${formatNum(Sd)} × ${formatNum(totalWeight)}",
                    "V = ${formatNum(V)} kN",
                    "ECP 201 - Clause 8.5.1",
                    "قوة القص القاعدية الكلية التي يقاومها المبنى"
                ))

                // توزيع القوى على الأدوار
                val storyForces = distributeSeismicForces(V, storyHeights, storyWeights, T)
                equations.add(EquationStep(
                    stepNum++, "توزيع القوى الأفقية",
                    "Fi = V × (wi×hi^k) / Σ(wi×hi^k)",
                    "k = ${if (T <= 0.5) "1.0" else if (T >= 2.5) "2.0" else formatNum(1.0 + (T - 0.5) / 2.0)}",
                    storyForces.joinToString("\n") { "الدور ${it.storyNumber}: F = ${formatNum(it.force)} kN" },
                    "ECP 201 - Clause 8.5.4"
                ))

                SeismicResult(
                    baseShear = V,
                    storyForces = storyForces,
                    naturalPeriod = T,
                    seismicCoefficient = Sd,
                    responseFactor = R,
                    importanceFactor = I,
                    soilFactor = S,
                    zoneFactor = ag,
                    equations = equations
                )
            }

            DesignCode.SAUDI -> {
                // SBC 301 (Saudi Building Code for Loads)
                val Ss = zone.zoneFactorSBC * 2.5  // Short period spectral acceleration
                val S1 = zone.zoneFactorSBC        // 1-second spectral acceleration
                val I = importance.factorSBC
                val R = resistanceSystem.responseFactor

                // Site coefficients
                val Fa = 1.0  // Simplified
                val Fv = 1.5

                val SDS = 2.0 / 3.0 * Fa * Ss
                val SD1 = 2.0 / 3.0 * Fv * S1

                val T = 0.075 * buildingHeight.pow(0.75)
                val Cs = minOf(SDS / (R / I), SD1 / (T * R / I))
                val CsMin = 0.044 * SDS * I

                val CsFinal = maxOf(Cs, CsMin)
                val V = CsFinal * totalWeight

                equations.add(EquationStep(
                    stepNum++, "قوة القص القاعدية V (SBC)",
                    "V = Cs × W",
                    "Cs = min(SDS/(R/I), SD1/(T×R/I)) ≥ 0.044×SDS×I\nCs = ${formatNum(CsFinal)}",
                    "V = ${formatNum(V)} kN",
                    "SBC 301 - Section 12.8"
                ))

                val storyForces = distributeSeismicForces(V, storyHeights, storyWeights, T)

                SeismicResult(
                    baseShear = V,
                    storyForces = storyForces,
                    naturalPeriod = T,
                    seismicCoefficient = CsFinal,
                    responseFactor = R,
                    importanceFactor = I,
                    soilFactor = Fv,
                    zoneFactor = Ss,
                    equations = equations
                )
            }

            DesignCode.AMERICAN -> {
                // ASCE 7-22 / ACI 318-19
                val Ss = zone.zoneFactorSBC * 2.5
                val S1 = zone.zoneFactorSBC
                val Ie = importance.factorACI
                val R = resistanceSystem.responseFactor

                val Fa = 1.0
                val Fv = 1.5
                val SDS = 2.0 / 3.0 * Fa * Ss
                val SD1 = 2.0 / 3.0 * Fv * S1

                val T = 0.0724 * buildingHeight.pow(0.8)  // for RC moment frames
                val Cs = minOf(SDS / (R / Ie), SD1 / (T * (R / Ie)))
                val CsMin = maxOf(0.044 * SDS * Ie, 0.01)
                val CsFinal = maxOf(Cs, CsMin)
                val V = CsFinal * totalWeight

                equations.add(EquationStep(
                    stepNum++, "Base Shear V (ASCE 7)",
                    "V = Cs × W",
                    "Cs = min(SDS/(R/Ie), SD1/(T×R/Ie)) ≥ max(0.044SDS×Ie, 0.01)\nCs = ${formatNum(CsFinal)}",
                    "V = ${formatNum(V)} kN",
                    "ASCE 7-22 - Section 12.8"
                ))

                val storyForces = distributeSeismicForces(V, storyHeights, storyWeights, T)

                SeismicResult(
                    baseShear = V,
                    storyForces = storyForces,
                    naturalPeriod = T,
                    seismicCoefficient = CsFinal,
                    responseFactor = R,
                    importanceFactor = Ie,
                    soilFactor = Fv,
                    zoneFactor = Ss,
                    equations = equations
                )
            }
        }
    }

    data class SoilParams(val S: Double, val TB: Double, val TC: Double, val TD: Double)

    private fun distributeSeismicForces(
        V: Double,
        storyHeights: List<Double>,
        storyWeights: List<Double>,
        T: Double
    ): List<StoryForce> {
        val k = when {
            T <= 0.5 -> 1.0
            T >= 2.5 -> 2.0
            else -> 1.0 + (T - 0.5) / 2.0
        }

        // Cumulative heights
        val heights = mutableListOf<Double>()
        var cumHeight = 0.0
        for (h in storyHeights) {
            cumHeight += h
            heights.add(cumHeight)
        }

        val sumWH = storyWeights.zip(heights).sumOf { (w, h) -> w * h.pow(k) }

        var cumulativeShear = 0.0
        val forces = mutableListOf<StoryForce>()

        for (i in storyWeights.indices.reversed()) {
            val Fi = V * storyWeights[i] * heights[i].pow(k) / sumWH
            cumulativeShear += Fi
            val moment = cumulativeShear * heights[i]

            forces.add(0, StoryForce(
                storyNumber = i + 1,
                height = heights[i],
                weight = storyWeights[i],
                force = Fi,
                shear = cumulativeShear,
                moment = moment
            ))
        }

        return forces
    }

    // ═══════════════════════════════════════════════════════════
    // 7. حسابات حائط السند - Retaining Wall Design
    // ═══════════════════════════════════════════════════════════

    fun retainingWallDesign(
        wallHeight: Double,       // m
        soilDensity: Double,      // kN/m³
        soilAngle: Double,        // degrees - زاوية الاحتكاك الداخلي
        surcharge: Double,        // kN/m² - حمل إضافي فوق التربة
        fcu: Double,
        fy: Double,
        code: DesignCode,
        wallType: RetainingWallType = RetainingWallType.CANTILEVER
    ): RetainingWallResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val phi = Math.toRadians(soilAngle)

        // معامل الضغط الأرضي الفعال Ka (Rankine)
        val Ka = (1 - sin(phi)) / (1 + sin(phi))
        equations.add(EquationStep(
            stepNum++, "معامل الضغط الأرضي الفعال Ka",
            "Ka = (1 - sinφ) / (1 + sinφ)",
            "Ka = (1 - sin${formatNum(soilAngle)}°) / (1 + sin${formatNum(soilAngle)}°)",
            "Ka = ${formatNum(Ka)}",
            "نظرية Rankine"
        ))

        // معامل الضغط الأرضي السالب Kp
        val Kp = 1.0 / Ka
        equations.add(EquationStep(
            stepNum++, "معامل الضغط الأرضي السالب Kp",
            "Kp = 1 / Ka",
            "Kp = 1 / ${formatNum(Ka)}",
            "Kp = ${formatNum(Kp)}"
        ))

        val H = wallHeight

        // الضغط الأرضي النشط
        val Pa = 0.5 * Ka * soilDensity * H * H  // kN/m
        val PaSurcharge = Ka * surcharge * H       // kN/m

        equations.add(EquationStep(
            stepNum++, "قوة الضغط الأرضي النشط Pa",
            "Pa = ½ × Ka × γ × H² + Ka × q × H",
            "Pa = ½ × ${formatNum(Ka)} × ${formatNum(soilDensity)} × ${formatNum(H)}² + ${formatNum(Ka)} × ${formatNum(surcharge)} × ${formatNum(H)}",
            "Pa = ${formatNum(Pa)} + ${formatNum(PaSurcharge)} = ${formatNum(Pa + PaSurcharge)} kN/m",
            "ECP 202 / SBC 303"
        ))

        // أبعاد الحائط المقترحة (كابولي)
        val baseWidth = 0.5 * H + 0.3  // m (تجريبي)
        val toeLength = baseWidth * 0.33
        val heelLength = baseWidth - toeLength - 0.3  // stem thickness at base ≈ 0.3m
        val stemThicknessBase = maxOf(H / 10.0, 0.25)
        val stemThicknessTop = maxOf(stemThicknessBase * 0.6, 0.20)
        val baseThickness = maxOf(H / 12.0, 0.30)

        equations.add(EquationStep(
            stepNum++, "الأبعاد المقترحة",
            "عرض القاعدة ≈ 0.5H + 0.3\nسمك الجذع عند القاعدة ≈ H/10\nسمك القاعدة ≈ H/12",
            "B = ${formatNum(baseWidth)} m\nt_stem = ${formatNum(stemThicknessBase)} m\nt_base = ${formatNum(baseThickness)} m",
            "أبعاد مبدئية للتحقق",
            "توصيات تصميمية"
        ))

        // فحص الانقلاب
        val Moverturning = Pa * H / 3.0 + PaSurcharge * H / 2.0
        val Wwall = 25.0 * stemThicknessBase * H + 25.0 * baseWidth * baseThickness
        val Wsoil = soilDensity * heelLength * H
        val Mresisting = Wwall * baseWidth / 2.0 + Wsoil * (baseWidth - heelLength / 2.0)
        val FOS_overturning = Mresisting / Moverturning

        equations.add(EquationStep(
            stepNum++, "فحص الانقلاب",
            "FOS = M_resisting / M_overturning ≥ 2.0",
            "FOS = ${formatNum(Mresisting)} / ${formatNum(Moverturning)}",
            "FOS = ${formatNum(FOS_overturning)} ${if (FOS_overturning >= 2.0) "✓ آمن" else "✗ غير آمن"}",
            "ECP 202 / SBC 303"
        ))

        // فحص الانزلاق
        val Hresisting = (Wwall + Wsoil) * tan(phi) * 0.5 // µ = tan(2φ/3) simplified
        val Hsliding = Pa + PaSurcharge
        val FOS_sliding = Hresisting / Hsliding

        equations.add(EquationStep(
            stepNum++, "فحص الانزلاق",
            "FOS = H_resisting / H_sliding ≥ 1.5",
            "FOS = ${formatNum(Hresisting)} / ${formatNum(Hsliding)}",
            "FOS = ${formatNum(FOS_sliding)} ${if (FOS_sliding >= 1.5) "✓ آمن" else "✗ غير آمن"}",
            "ECP 202 / SBC 303"
        ))

        // تصميم الجذع (كعمود كابولي)
        val MuStem = Pa * H / 3.0 + PaSurcharge * H / 2.0  // kN.m/m

        val dStem = stemThicknessBase * 1000 - 50.0  // mm
        val flexStem = flexureDesign(MuStem, 1000.0, dStem, fcu, fy, code)

        RetainingWallResult(
            baseWidth = baseWidth,
            toeLength = toeLength,
            heelLength = heelLength,
            stemThicknessBase = stemThicknessBase,
            stemThicknessTop = stemThicknessTop,
            baseThickness = baseThickness,
            activePressure = Pa + PaSurcharge,
            overturningFOS = FOS_overturning,
            slidingFOS = FOS_sliding,
            stemMoment = MuStem,
            stemAs = flexStem.requiredAs,
            equations = equations,
            isSafe = FOS_overturning >= 2.0 && FOS_sliding >= 1.5
        )
    }

    data class RetainingWallResult(
        val baseWidth: Double,
        val toeLength: Double,
        val heelLength: Double,
        val stemThicknessBase: Double,
        val stemThicknessTop: Double,
        val baseThickness: Double,
        val activePressure: Double,
        val overturningFOS: Double,
        val slidingFOS: Double,
        val stemMoment: Double,
        val stemAs: Double,
        val equations: List<EquationStep>,
        val isSafe: Boolean
    )

    // ═══════════════════════════════════════════════════════════
    // 8. تصميم الخزانات - Tank Design
    // ═══════════════════════════════════════════════════════════

    fun rectangularTankDesign(
        length: Double,           // m - الطول الداخلي
        width: Double,            // m - العرض الداخلي
        height: Double,           // m - ارتفاع الماء
        freeBoard: Double,        // m - الارتفاع الحر
        fcu: Double,
        fy: Double,
        code: DesignCode,
        isUnderground: Boolean = true
    ): TankDesignResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val H = height + freeBoard
        val waterPressure = 10.0 * height  // kN/m² at base

        equations.add(EquationStep(
            stepNum++, "ضغط الماء عند القاعدة",
            "p = γw × h",
            "p = 10 × ${formatNum(height)}",
            "p = ${formatNum(waterPressure)} kN/m²",
            "ضغط هيدروستاتيكي"
        ))

        // سمك الجدران
        val wallThickness = maxOf(H * 1000 / 12.0, 200.0)
        val tWall = ceil(wallThickness / 25.0) * 25.0

        // سمك القاعدة
        val baseThickness = maxOf(tWall, 200.0)

        equations.add(EquationStep(
            stepNum++, "سمك الجدران والقاعدة",
            "t_wall ≥ max(H/12, 200mm)",
            "t_wall = max(${formatNum(H * 1000)}/12, 200)",
            "t_wall = ${formatNum(tWall)} mm, t_base = ${formatNum(baseThickness)} mm"
        ))

        // عزم الجدار (كابولي أو مثبت من الأسفل)
        val MuWall = waterPressure * H / 6.0  // kN.m/m (triangular load, fixed at base)

        equations.add(EquationStep(
            stepNum++, "عزم الجدار عند القاعدة",
            "Mu = γw × h × H / 6 (مثبت من الأسفل)",
            "Mu = ${formatNum(waterPressure)} × ${formatNum(H)} / 6",
            "Mu = ${formatNum(MuWall)} kN.m/m"
        ))

        val cover = 40.0  // mm - increased for water tightness
        val d = tWall - cover - 8.0

        val flexResult = flexureDesign(MuWall, 1000.0, d, fcu, fy, code)

        // فحص الشروخ (لمنع تسرب الماء)
        // wcr ≤ 0.1mm for liquid retaining structures
        equations.add(EquationStep(
            stepNum++, "فحص عرض الشروخ",
            "wcr ≤ 0.1 mm (منشآت حاجزة للسوائل)",
            "يجب التحقق من عرض الشروخ لمنع التسرب",
            "حسب الكود: ${when(code) {
                DesignCode.EGYPTIAN -> "ECP 203 Clause 4.3.2"
                DesignCode.SAUDI -> "SBC 304"
                DesignCode.AMERICAN -> "ACI 350"
            }}"
        ))

        TankDesignResult(
            wallThickness = tWall,
            baseThickness = baseThickness,
            waterPressure = waterPressure,
            wallMoment = MuWall,
            wallAs = maxOf(flexResult.requiredAs, 0.003 * 1000.0 * tWall), // min 0.3% for water tightness
            baseMoment = 0.0,
            baseAs = 0.003 * 1000.0 * baseThickness,
            equations = equations,
            isSafe = true
        )
    }

    data class TankDesignResult(
        val wallThickness: Double,
        val baseThickness: Double,
        val waterPressure: Double,
        val wallMoment: Double,
        val wallAs: Double,
        val baseMoment: Double,
        val baseAs: Double,
        val equations: List<EquationStep>,
        val isSafe: Boolean
    )

    // ═══════════════════════════════════════════════════════════
    // 9. تصميم السلالم - Stairs Design
    // ═══════════════════════════════════════════════════════════

    fun stairsDesign(
        floorHeight: Double,      // m - ارتفاع الدور
        stairWidth: Double,       // m - عرض السلم
        riserHeight: Double,      // mm - ارتفاع القائمة
        treadWidth: Double,       // mm - عرض النائمة
        liveLoad: Double,         // kN/m²
        fcu: Double,
        fy: Double,
        code: DesignCode
    ): StairsDesignResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        // عدد الدرجات
        val numberOfRisers = ceil(floorHeight * 1000 / riserHeight).toInt()
        val actualRiser = floorHeight * 1000 / numberOfRisers

        equations.add(EquationStep(
            stepNum++, "عدد الدرجات",
            "n = H / riser_height",
            "n = ${formatNum(floorHeight * 1000)} / ${formatNum(riserHeight)}",
            "n = $numberOfRisers درجة, ارتفاع القائمة الفعلي = ${formatNum(actualRiser)} mm"
        ))

        // فحص 2R + T
        val comfortCheck = 2 * actualRiser + treadWidth
        val isComfortable = comfortCheck in 580.0..640.0
        equations.add(EquationStep(
            stepNum++, "فحص الراحة",
            "2R + T = 580 ~ 640 mm",
            "2 × ${formatNum(actualRiser)} + ${formatNum(treadWidth)}",
            "${formatNum(comfortCheck)} mm ${if (isComfortable) "✓" else "✗"}",
            "معادلة الراحة لتصميم السلالم"
        ))

        // البحر المائل
        val horizontalLength = (numberOfRisers - 1) * treadWidth / 1000.0  // m
        val slopeAngle = atan(floorHeight / horizontalLength)
        val inclinedLength = floorHeight / sin(slopeAngle)

        equations.add(EquationStep(
            stepNum++, "الطول المائل",
            "L_inclined = H / sin(θ)",
            "θ = atan(${formatNum(floorHeight)}/${formatNum(horizontalLength)}) = ${formatNum(Math.toDegrees(slopeAngle))}°",
            "L = ${formatNum(inclinedLength)} m"
        ))

        // سمك البلاطة
        val effectiveSpan = inclinedLength / 2.0 + 1.0  // half flight + landing
        val ts = effectiveSpan * 1000 / 20.0  // L/20
        val thickness = maxOf(ceil(ts / 10.0) * 10.0, 120.0)

        equations.add(EquationStep(
            stepNum++, "سمك بلاطة السلم",
            "t ≥ L_eff / 20",
            "t = ${formatNum(effectiveSpan * 1000)} / 20",
            "t = ${formatNum(thickness)} mm"
        ))

        // الأحمال
        val selfWeightHorizontal = 25.0 * thickness / 1000.0 / cos(slopeAngle)
        val stepWeight = 25.0 * actualRiser / 2000.0  // average step weight
        val finishLoad = 1.5  // kN/m²

        val totalDead = selfWeightHorizontal + stepWeight + finishLoad
        val (df, lf) = EngineeringConstants.getUltimateLoadFactor(code)
        val wu = df * totalDead + lf * liveLoad

        equations.add(EquationStep(
            stepNum++, "الأحمال",
            "DL = w_self + w_step + w_finish = ${formatNum(totalDead)} kN/m²",
            "wu = ${formatNum(df)}×${formatNum(totalDead)} + ${formatNum(lf)}×${formatNum(liveLoad)}",
            "wu = ${formatNum(wu)} kN/m²"
        ))

        // العزم
        val Mu = wu * stairWidth * effectiveSpan.pow(2) / 8.0
        val Vu = wu * stairWidth * effectiveSpan / 2.0

        equations.add(EquationStep(
            stepNum++, "العزم وقوة القص",
            "Mu = wu × b × L² / 8\nVu = wu × b × L / 2",
            "Mu = ${formatNum(wu)} × ${formatNum(stairWidth)} × ${formatNum(effectiveSpan)}² / 8",
            "Mu = ${formatNum(Mu)} kN.m, Vu = ${formatNum(Vu)} kN"
        ))

        val d = thickness - 20.0 - 6.0
        val flexResult = flexureDesign(Mu / stairWidth, 1000.0, d, fcu, fy, code)

        StairsDesignResult(
            numberOfRisers = numberOfRisers,
            actualRiser = actualRiser,
            comfortCheck = comfortCheck,
            thickness = thickness,
            inclinedLength = inclinedLength,
            effectiveSpan = effectiveSpan,
            ultimateLoad = wu,
            moment = Mu,
            shear = Vu,
            mainAs = maxOf(flexResult.requiredAs, EngineeringConstants.getSlabMinRatio(code, fy) * 1000.0 * thickness),
            equations = equations,
            isSafe = true
        )
    }

    data class StairsDesignResult(
        val numberOfRisers: Int,
        val actualRiser: Double,
        val comfortCheck: Double,
        val thickness: Double,
        val inclinedLength: Double,
        val effectiveSpan: Double,
        val ultimateLoad: Double,
        val moment: Double,
        val shear: Double,
        val mainAs: Double,
        val equations: List<EquationStep>,
        val isSafe: Boolean
    )

    // ═══════════════════════════════════════════════════════════
    // أدوات مساعدة - Helper Functions
    // ═══════════════════════════════════════════════════════════

    data class RebarSelection(
        val barDiameter: Double,
        val count: Int,
        val totalArea: Double,
        val spacing: Double = 0.0
    )

    /**
     * اختيار أقطار حديد التسليح للأعمدة/الكمرات
     */
    fun selectRebar(requiredAs: Double, b: Double, h: Double, cover: Double): RebarSelection {
        val availableDiameters = listOf(12.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        val effectiveWidth = b - 2 * cover

        var bestSelection = RebarSelection(25.0, 4, 4 * RebarDiameter.D25.area * 100)

        for (dia in availableDiameters) {
            val barArea = PI * dia * dia / 4.0  // mm²
            val nBars = ceil(requiredAs / barArea).toInt().coerceAtLeast(4)
            val totalArea = nBars * barArea

            // Check spacing
            val spacing = (effectiveWidth - nBars * dia) / (nBars - 1)
            if (spacing >= maxOf(dia, 25.0) && nBars >= 4 && totalArea >= requiredAs) {
                if (totalArea < bestSelection.totalArea || (totalArea == bestSelection.totalArea && dia < bestSelection.barDiameter)) {
                    bestSelection = RebarSelection(dia, nBars, totalArea)
                }
            }
        }

        return bestSelection
    }

    /**
     * اختيار حديد البلاطات
     */
    fun selectRebarForSlab(requiredAsPerM: Double, width: Double): RebarSelection {
        val availableDiameters = listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        val availableSpacings = listOf(100.0, 125.0, 150.0, 175.0, 200.0, 225.0, 250.0)

        var bestSelection = RebarSelection(10.0, 10, 10 * PI * 100 / 4.0, 100.0)

        for (dia in availableDiameters) {
            val barArea = PI * dia * dia / 4.0
            for (spacing in availableSpacings) {
                val nBars = ceil(width / spacing).toInt()
                val totalArea = nBars * barArea * (1000.0 / width)  // per meter

                if (totalArea >= requiredAsPerM) {
                    val areaPerM = barArea * 1000.0 / spacing
                    if (areaPerM >= requiredAsPerM) {
                        val current = RebarSelection(dia, nBars, areaPerM, spacing)
                        if (areaPerM < bestSelection.totalArea * 1.5) {
                            bestSelection = current
                        }
                    }
                }
            }
        }

        return bestSelection
    }

    /**
     * تنسيق الأرقام
     */
    fun formatNum(value: Double, decimals: Int = 2): String {
        return String.format("%.${decimals}f", value)
    }
}
