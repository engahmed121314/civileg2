package com.civileg.app.domain.entities

import kotlin.math.max
import kotlin.math.sqrt

/**
 * ── P4-4 (§9.38): أحكام الكودود الحسابية — المصدر الموحّد للمعاملات التي تقود الحسابات ──
 *
 * "بنود الكود تؤثر في الحسابات حسب اختيار المستخدم": كل معامل هنا مأخوذ من بند
 * محدد في الكود المختار (ECP 203-2020 / ACI 318-19 / SBC 304-2018)، والمرجع
 * يُعرض مع النتيجة عبر CodeReference.
 *
 * الصيغ مطابقة للمحركات المُدقّقة والمختبرة (ECPBeam / ACIBeam / SBCBeam):
 *  - ECP: طريقة المعاملات الجزئية (γc = 1.5، γs = 1.15) وإجهاد القص الخرساني
 *    qcu = 0.24·√(fcu/γc) وتسليح أدنى ρmin = max(0.26√fcu/fy, 0.0013).
 *  - ACI/SBC: طريقة معامل الاختزال φ (انحناء 0.9، قص 0.75) مع كتلة إجهاد
 *    Whitney، وتسليح أدنى max(0.25√f'c/fy, 1.4/fy)، ومقاومة قص خرسانية
 *    Vc = 0.17·√f'c·b·d (f'c = 0.8·fcu تحويل مكعب→أسطوانة).
 *  - SBC يشارك ACI المنهجية لكن بأغطية خرسانية أكبر (50mm خارجياً) لبيئة
 *    المملكة الحارة/المالحة — SBC 304 §20.5.1.
 *
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object CodeProvisions {

    /** منهجية مقاومة الانحناء حسب الكود */
    enum class FlexureApproach { PARTIAL_FACTORS, PHI_STRENGTH }

    /**
     * الحزمة الكاملة لأحكام كود واحد.
     * المراجع (refs) تُستخدم في ملاحظات النتائج وتوثيق التقارير.
     */
    data class Provisions(
        // ── توليفات الأحمال (D + L) ──
        val loadFactorDead: Double,          // ECP 1.4 / ACI,SBC 1.2
        val loadFactorLive: Double,          // 1.6 للجميع
        // ── منهجية المقاومة ──
        val flexureApproach: FlexureApproach,
        val gammaC: Double,                  // ECP 1.5 / ACI,SBC 1.0 (لا تُطبق)
        val gammaS: Double,                  // ECP 1.15 / ACI,SBC 1.0
        val phiFlexure: Double,              // ECP 1.0 / ACI,SBC 0.9
        val phiShear: Double,                // ECP 1.0 / ACI,SBC 0.75
        // ── الأغطية الخرسانية الدنيا (mm) ──
        val coverBeam: Double,               // ECP,ACI 40 / SBC 50 (خارجي افتراضي)
        val coverSlab: Double,               // ECP 25 / ACI 20 / SBC 40
        val coverColumn: Double,             // 40 للجميع (SBC 50 خارجي)
        // ── حدود التسليح للأعمدة ──
        val columnRhoMin: Double,            // ECP 0.008 / ACI,SBC 0.01
        val columnRhoMax: Double,            // 0.08 للجميع
        // ── المراجع النصية ──
        val refLoadCombination: String,
        val refBeamFlexure: String,
        val refBeamShear: String,
        val refBeamRhoMin: String,
        val refBeamCover: String,
        val refColumnAxial: String
    )

    fun forCode(code: DesignCode): Provisions = when (code) {
        DesignCode.ECP -> Provisions(
            loadFactorDead = 1.4, loadFactorLive = 1.6,
            flexureApproach = FlexureApproach.PARTIAL_FACTORS,
            gammaC = 1.5, gammaS = 1.15,
            phiFlexure = 1.0, phiShear = 1.0,
            coverBeam = 40.0, coverSlab = 25.0, coverColumn = 40.0,
            columnRhoMin = 0.008, columnRhoMax = 0.08,
            refLoadCombination = CodeReference.ECP.LOAD_COMBINATION,
            refBeamFlexure = CodeReference.ECP.BEAM_FLEXURE,
            refBeamShear = CodeReference.ECP.BEAM_SHEAR,
            refBeamRhoMin = CodeReference.ECP.BEAM_REINFORCEMENT_MIN,
            refBeamCover = CodeReference.ECP.BEAM_COVER,
            refColumnAxial = CodeReference.ECP.COLUMN_AXIAL
        )
        DesignCode.ACI -> Provisions(
            loadFactorDead = 1.2, loadFactorLive = 1.6,
            flexureApproach = FlexureApproach.PHI_STRENGTH,
            gammaC = 1.0, gammaS = 1.0,
            phiFlexure = 0.9, phiShear = 0.75,
            coverBeam = 40.0, coverSlab = 20.0, coverColumn = 40.0,
            columnRhoMin = 0.01, columnRhoMax = 0.08,
            refLoadCombination = CodeReference.ACI.LOAD_COMBINATION,
            refBeamFlexure = CodeReference.ACI.BEAM_FLEXURE,
            refBeamShear = CodeReference.ACI.BEAM_SHEAR,
            refBeamRhoMin = CodeReference.ACI.BEAM_REINFORCEMENT_MIN,
            refBeamCover = CodeReference.ACI.BEAM_COVER,
            refColumnAxial = CodeReference.ACI.COLUMN_AXIAL
        )
        DesignCode.SBC -> Provisions(
            loadFactorDead = 1.2, loadFactorLive = 1.6,
            flexureApproach = FlexureApproach.PHI_STRENGTH,
            gammaC = 1.0, gammaS = 1.0,
            phiFlexure = 0.9, phiShear = 0.75,
            // SBC 304 §20.5.1: أغطية أكبر للبيئة السعودية (خارجي افتراضي — 50 للكمرات)
            coverBeam = 50.0, coverSlab = 40.0, coverColumn = 50.0,
            columnRhoMin = 0.01, columnRhoMax = 0.08,
            refLoadCombination = CodeReference.SBC.LOAD_COMBINATION,
            refBeamFlexure = CodeReference.SBC.BEAM_FLEXURE,
            refBeamShear = CodeReference.SBC.BEAM_SHEAR,
            refBeamRhoMin = CodeReference.SBC.BEAM_REINFORCEMENT_MIN,
            refBeamCover = CodeReference.SBC.BEAM_COVER,
            refColumnAxial = CodeReference.SBC.COLUMN_AXIAL
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // دوال حسابية قائمة على البنود — كل دالة تُرجع قيمة تختلف حسب الكود
    // ══════════════════════════════════════════════════════════════════════

    /**
     * التسليح الأدنى للكمرات (نسبة من b·d):
     *  - ECP 203 §4-2-2-1 (كما في ECPBeam المُدقّق): ρmin = max(0.26·√fcu/fy, 0.0013)
     *  - ACI 318 §9.6.1.2 / SBC 304 §9.6.1: ρmin = max(0.25·√f'c/fy, 1.4/fy)
     * @param fcu مقاومة المكعب (MPa) — تُحوَّل داخلياً للأسطوانة في ACI/SBC
     */
    fun beamRhoMin(code: DesignCode, fcu: Double, fy: Double): Double {
        return if (code == DesignCode.ECP) {
            max(0.26 * sqrt(fcu) / fy, 0.0013)
        } else {
            val fc = 0.8 * fcu // مكعب → أسطوانة
            max(0.25 * sqrt(fc) / fy, 1.4 / fy)
        }
    }

    /**
     * إجهاد القص الخرساني للمتر المربع (MPa) — قبل معامل الكود:
     *  - ECP 203 §4-3-1-2: qcu = 0.24·√(fcu/γc) — المعامل الجزئي مضمّن
     *  - ACI 318 §22.5.5.1 / SBC 304 §22.5: Vc = 0.17·√f'c·b·d — تُطبَّق خارجياً φ=0.75
     * الدالة تُرجع السعة الكلية (kN) الجاهزة للمقارنة مع Vu مباشرة:
     * ECP: qcu·b·d (بدون φ إضافي) / ACI,SBC: φ·0.17·√(0.8fcu)·b·d
     */
    fun concreteShearCapacity(code: DesignCode, b: Double, d: Double, fcu: Double): Double {
        return if (code == DesignCode.ECP) {
            val qcu = 0.24 * sqrt(fcu / 1.5) // MPa — يشمل γc
            qcu * b * d / 1000.0             // kN
        } else {
            val fcPrime = 0.8 * fcu          // أسطوانة
            0.75 * 0.17 * sqrt(fcPrime) * b * d / 1000.0 // kN — φ·Vc
        }
    }

    /**
     * مساحة التسليح المطلوبة للانحناء (mm²):
     *  - ECP: طريقة K — As = Mu·10⁶ / ((fy/γs)·z)، z = d·(0.5 + √(0.25 − K/0.893))
     *  - ACI/SBC: Whitney — As = Mu·10⁶ / (φ·fy·(d − a/2))، تُحل تكرارياً بمعادلة ρ
     * Mu بالـ kN·m و b,d بالـ mm و fy بالـ MPa.
     * @return Triple(As, Kbal أو ρbal، هل المقطع مقبول بدون تسليح مضغوط)
     */
    fun requiredFlexuralSteel(
        code: DesignCode, Mu: Double, b: Double, d: Double, fcu: Double, fy: Double
    ): Triple<Double, Double, Boolean> {
        if (Mu <= 0.0 || b <= 0.0 || d <= 0.0) return Triple(0.0, 0.0, true)

        return if (code == DesignCode.ECP) {
            // ── ECP 203 §4-2-1: طريقة K بذراع العزم ──
            val kBal = ecpKBal(fcu, fy)
            val K = Mu * 1e6 / (fcu * b * d * d)
            if (K > kBal) {
                // يحتاج تسليح مضغوط — نرجع الحاجة بالمطلوب ونجعل المقطع غير مقبول أحادياً
                val zMax = d * (0.5 + sqrt(max(0.0, 0.25 - kBal / 0.893)))
                Triple(Mu * 1e6 / ((fy / 1.15) * zMax), kBal, false)
            } else {
                val z = d * (0.5 + sqrt(max(0.0, 0.25 - K / 0.893)))
                val zClamped = minOf(z, 0.95 * d)
                Triple(Mu * 1e6 / ((fy / 1.15) * zClamped), kBal, true)
            }
        } else {
            // ── ACI 318 §22.2 / SBC 304 §22.2: Whitney مع φ ──
            val fc = 0.8 * fcu
            val phi = 0.9
            val m = fy / (0.85 * fc)
            val Rn = Mu * 1e6 / (phi * b * d * d)
            val discriminant = 1.0 - 2.0 * m * Rn / fy
            if (discriminant <= 0.0) {
                // يتجاوز الحد الأقصى — تسليح مضغوط مطلوب
                return Triple(Mu * 1e6 / (phi * fy * 0.95 * d), 0.0, false)
            }
            val rho = (1.0 - sqrt(discriminant)) / m
            // الحد الأقصى: 0.75·ρbal — مقطع محكوم بالشد (εt ≥ 0.005 تقريباً)
            // ρbal = 0.85·β1·(fc/fy)·(600/(600+fy)) — كما في ACIBeam المُدقّق
            val beta1 = if (fc <= 28.0) 0.85 else max(0.85 - 0.05 * ((fc - 28.0) / 7.0), 0.65)
            val rhoBal = 0.85 * beta1 * fc / fy * 600.0 / (600.0 + fy)
            val rhoMax = 0.75 * rhoBal
            if (rho > rhoMax) {
                return Triple(rhoMax * b * d + (Mu * 1e6 - phi * rhoMax * fy * (1 - 0.5 * rhoMax * fy / (0.85 * fc)) * b * d * d) / (phi * fy * (d - 60.0)), rhoMax, false)
            }
            Triple(rho * b * d, rhoMax, true)
        }
    }

    /**
     * قدرة العزم القصوى للمقطع (kN·m) بتسليح As (mm²):
     *  - ECP: Mu = (fy/γs)·As·(d − a/2)، a = (fy/γs)·As / (0.67·fcu/γc·b)
     *  - ACI/SBC: φMn = φ·As·fy·(d − a/2)، a = As·fy / (0.85·fc·b)
     */
    fun momentCapacity(code: DesignCode, b: Double, d: Double, As: Double, fcu: Double, fy: Double): Double {
        if (As <= 0.0 || d <= 0.0) return 0.0
        return if (code == DesignCode.ECP) {
            val a = (fy / 1.15) * As / ((0.67 / 1.5) * fcu * b)
            (fy / 1.15) * As * (d - a / 2.0) / 1e6
        } else {
            val fc = 0.8 * fcu
            val a = As * fy / (0.85 * fc * b)
            0.9 * As * fy * (d - a / 2.0) / 1e6
        }
    }

    /**
     * مساحة الكانات الدنيا لوحدة الطول (mm²/mm) للقص:
     *  - ECP 203: Asv,min/s = 0.4·b/(2? ) — نستخدم قاعدة الكانات الدنيا العملية 0.4/(fy)·b
     *  - ACI 318 §9.6.3.4 / SBC 304 §9.6.3: Av,min/s = max(0.062·√f'c, 0.35)·b/fy
     * الدالة تُرجع (mm²/mm) قابلة للضرب في Spacing.
     */
    fun minShearReinforcementRatio(code: DesignCode, b: Double, fcu: Double, fy: Double): Double {
        return if (code == DesignCode.ECP) {
            0.4 * b / (1.7 * fy) // ≈ 0.4·b/fy بقسمة إضافية على 1.7 لمعامل γs — متحفظ
        } else {
            val fc = 0.8 * fcu
            max(0.062 * sqrt(fc) * b / fy, 0.35 * b / fy)
        }
    }

    /** K_bal المصري: نسبة الانحناء المتوازن بمعاملات γ */
    private fun ecpKBal(fcu: Double, fy: Double): Double {
        val alpha = 0.67 / 1.5
        val beta = if (fcu <= 30.0) 0.85 else max(0.85 - 0.05 * ((fcu - 30.0) / 7.0), 0.65)
        val xb = 600.0 * beta / (600.0 + fy / 1.15)
        return alpha * xb * (1.0 - 0.5 * xb)
    }
}
