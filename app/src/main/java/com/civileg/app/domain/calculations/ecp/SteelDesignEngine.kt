package com.civileg.app.domain.calculations.ecp

import kotlin.math.*

/**
 * محرك تصميم المنشآت المعدنية حسب ECP 205 (الكود المصري للمنشآت المعدنية)
 * يعتمد على طريقة LRFD (Load and Resistance Factor Design)
 * يغطي: الكمرات، الأعمدة، الالتواء البعرضي، الانبعاج المحلي
 */
class SteelDesignEngine {

    companion object {
        // معاملات المقاومة حسب LRFD
        private const val PHI_BENDING = 0.9       // معامل المقاومة للانحناء
        private const val PHI_COMPRESSION = 0.9   // معامل المقاومة للضغط
        private const val PHI_SHEAR = 0.9         // معامل المقاومة للقص
        private const val E_STEEL = 200000.0       // معامل مرونة الصلب MPa
        private const val G_STEEL = 76923.0        // معامل القص MPa
    }

    enum class SteelGrade(val fy: Double, val fu: Double) {
        ST37(240.0, 360.0),  // S235
        ST44(280.0, 430.0),  // S275
        ST52(355.0, 510.0);  // S355

        fun getGradeName() = when (this) {
            ST37 -> "ST37 (S235)"
            ST44 -> "ST44 (S275)"
            ST52 -> "ST52 (S355)"
        }
    }

    // ===================== نتائج الفحص =====================
    data class SteelCheckResult(
        val utilizationRatio: Double,
        val isSafe: Boolean,
        val classification: String,
        val details: String = ""
    )

    data class BeamDesignResult(
        val momentCheck: SteelCheckResult,
        val shearCheck: SteelCheckResult,
        val deflectionCheck: SteelCheckResult,
        val ltbCheck: SteelCheckResult?,
        val localBucklingCheck: SteelCheckResult?,
        val isSafe: Boolean,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList()
    )

    data class ColumnDesignResult(
        val axialCheck: SteelCheckResult,
        val flexuralCheck: SteelCheckResult?,
        val slendernessRatio: Double,
        val isSlender: Boolean,
        val isSafe: Boolean,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList()
    )

    data class SectionProperties(
        val name: String,
        val h: Double,        // mm - الارتفاع الكلي
        val b: Double,        // mm - عرض الشفة
        val tw: Double,       // mm - سماكة الجذع
        val tf: Double,       // mm - سماكة الشفة
        val Ix: Double,       // mm⁴ - عزم القصور حول X
        val Iy: Double,       // mm⁴ - عزم القصور حول Y
        val Zx: Double,       // mm³ - معامل المقطع اللدن حول X
        val Zy: Double,       // mm³ - معامل المقطع اللدن حول Y
        val Sx: Double,       // mm³ - معامل المقطع المرن حول X
        val Sy: Double,       // mm³ - معامل المقطع المرن حول Y
        val rx: Double,       // mm - نصف القطر الدوراني حول X
        val ry: Double,       // mm - نصف القطر الدوراني حول Y
        val A: Double,        // mm² - مساحة المقطع
        val J: Double         // mm⁴ - ثابت الالتواء
    )

    // ===================== تصميم الكمرات =====================

    /**
     * فحص قدرة الكمرة على الانحناء
     * Mn = φ × Fy × Zx (للمقاطع المدمجة)
     * مع فحص الالتواء البعرضي (Lateral-Torsional Buckling)
     */
    fun checkBeamMoment(
        Mu: Double,           // kN.m - العزم التصميمي
        section: SectionProperties,
        grade: SteelGrade = SteelGrade.ST37,
        Lb: Double = 0.0,     // mm - طول الدعم البعرضي (0 = مدعوم بالكامل)
        Cb: Double = 1.0      // معامل التحميل (1.0 للتحميل المنتظم)
    ): SteelCheckResult {
        val Zx_mm3 = section.Zx // mm³
        val Mn = grade.fy * Zx_mm3 / 1e6 // kN.m (القدرة الإسمية)

        // فحص الالتواء البعرضي (LTB)
        val Mn_design = if (Lb > 0) {
            calculateLtbCapacity(Mn, Lb, section, grade, Cb)
        } else {
            Mn // لا حاجة لفحص LTB إذا كان مدعوماً بالكامل
        }

        val phiMn = PHI_BENDING * Mn_design
        val ratio = Mu / phiMn

        return SteelCheckResult(
            utilizationRatio = ratio,
            isSafe = ratio <= 1.0,
            classification = when {
                ratio < 0.5 -> "Economic"
                ratio < 0.85 -> "Adequate"
                ratio <= 1.0 -> "Near Limit"
                else -> "Unsafe"
            },
            details = "φMn = ${String.format("%.1f", phiMn)} kN.m, Mn = ${String.format("%.1f", Mn_design)} kN.m"
        )
    }

    /**
     * فحص قدرة القص
     * Vn = 0.6 × Fy × Aw (Aw = d × tw)
     */
    fun checkBeamShear(
        Vu: Double,           // kN - القوة القصية التصميمية
        section: SectionProperties,
        grade: SteelGrade = SteelGrade.ST37
    ): SteelCheckResult {
        val Aw = section.h * section.tw // mm² - مساحة الجذع
        val Vn = 0.6 * grade.fy * Aw / 1000.0 // kN
        val phiVn = PHI_SHEAR * Vn
        val ratio = Vu / phiVn

        return SteelCheckResult(
            utilizationRatio = ratio,
            isSafe = ratio <= 1.0,
            classification = if (ratio < 0.7) "Economic" else "Adequate",
            details = "φVn = ${String.format("%.1f", phiVn)} kN, Aw = ${String.format("%.0f", Aw)} mm²"
        )
    }

    /**
     * فحص الانحراف (الحمولة الحية فقط)
     * δ = 5×w×L⁴ / (384×E×I)
     */
    fun checkBeamDeflection(
        w_LL: Double,         // kN/m - الحمل الحي
        span: Double,         // mm - البحر
        section: SectionProperties,
        maxDeflection: Double = span / 360.0 // mm
    ): SteelCheckResult {
        val L_m = span / 1000.0
        val I_m4 = section.Ix / 1e12 // m⁴
        val delta = 5 * w_LL * L_m.pow(4) / (384 * E_STEEL / 1e6 * I_m4) * 1000 // mm
        val ratio = delta / maxDeflection

        return SteelCheckResult(
            utilizationRatio = ratio,
            isSafe = ratio <= 1.0,
            classification = if (ratio < 0.7) "Economic" else "Adequate",
            details = "δ = ${String.format("%.1f", delta)} mm, δ_allow = ${String.format("%.1f", maxDeflection)} mm"
        )
    }

    /**
     * تصميم كمرة شامل
     */
    fun designBeam(
        Mu: Double, Vu: Double, w_LL: Double, span: Double,
        section: SectionProperties,
        grade: SteelGrade = SteelGrade.ST37,
        Lb: Double = 0.0
    ): BeamDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val momentCheck = checkBeamMoment(Mu, section, grade, Lb)
        val shearCheck = checkBeamShear(Vu, section, grade)
        val deflectionCheck = checkBeamDeflection(w_LL, span, section)

        // فحص الالتواء البعرضي
        val ltbCheck = if (Lb > 0) {
            val ltbCapacity = calculateLtbCapacity(
                grade.fy * section.Zx / 1e6, Lb, section, grade
            )
            val phiMnlb = PHI_BENDING * ltbCapacity
            SteelCheckResult(
                utilizationRatio = Mu / phiMnlb,
                isSafe = Mu <= phiMnlb,
                classification = if (Mu / phiMnlb < 0.85) "OK" else "Near Limit",
                details = "φMnlb = ${String.format("%.1f", phiMnlb)} kN.m"
            )
        } else null

        // فحص الانبعاج المحلي
        val localBucklingCheck = checkLocalBuckling(section, grade)

        codeNotes.add("ECP 205 / AISC 360-22: LRFD Method")
        codeNotes.add("Section: ${section.name} (${grade.getGradeName()})")
        codeNotes.add(String.format("Ag=%.0f mm², Zx=%.0f mm³, Sx=%.0f mm³", section.A, section.Zx, section.Sx))
        if (Lb > 0) {
            val ry = section.ry
            val Lp = 1.76 * ry * sqrt(E_STEEL / grade.fy)
            codeNotes.add(String.format("Lb=%.0fmm, Lp=%.0fmm, Lb%s Lp", Lb, Lp, if (Lb <= Lp) "≤" else ">"))
        }

        if (!momentCheck.isSafe) warnings.add("العزم يتجاوز القدرة - اختر مقطع أكبر")
        if (!shearCheck.isSafe) warnings.add("القص يتجاوز القدرة - زِد سماكة الجذع")
        if (!deflectionCheck.isSafe) warnings.add("الانحراف يتجاوز الحد المسموح")

        return BeamDesignResult(
            momentCheck = momentCheck,
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            ltbCheck = ltbCheck,
            localBucklingCheck = localBucklingCheck,
            isSafe = momentCheck.isSafe && shearCheck.isSafe && deflectionCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== تصميم الأعمدة =====================

    /**
     * فحص قدرة العمود على الضغط
     * ECP 205 / AISC: Pn = Fcr × Ag
     * Fcr يُحسب حسب منحنيات الانبعاج (a, b, c)
     */
    fun checkColumnCompression(
        Pu: Double,           // kN - الحمل المحوري التصميمي
        section: SectionProperties,
        grade: SteelGrade = SteelGrade.ST37,
        K: Double = 1.0,      // معامل الطول الفعال
        L: Double = 3000.0    // mm - طول العمود
    ): ColumnDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // نسبة النحافة
        val lambda = (K * L) / section.ry
        val isSlender = lambda > 100.0 // ECP 205: حد النحافة ~100

        // إجهاد الانبعاج الحرج Fcr
        val Fcr = calculateFcr(lambda, grade)

        // القدرة المحورية
        val Pn = Fcr * section.A / 1000.0 // kN
        val phiPn = PHI_COMPRESSION * Pn
        val axialRatio = Pu / phiPn

        val axialCheck = SteelCheckResult(
            utilizationRatio = axialRatio,
            isSafe = axialRatio <= 1.0,
            classification = when {
                axialRatio < 0.5 -> "Economic"
                axialRatio < 0.85 -> "Adequate"
                axialRatio <= 1.0 -> "Near Limit"
                else -> "Unsafe"
            },
            details = "λ=$lambda, Fcr=${String.format("%.1f", Fcr)} MPa, φPn=${String.format("%.1f", phiPn)} kN"
        )

        codeNotes.add("ECP 205 / AISC 360: Compression Members")
        codeNotes.add("Slenderness ratio λ = ${String.format("%.1f", lambda)}")
        codeNotes.add("Fcr = ${String.format("%.1f", Fcr)} MPa")
        if (isSlender) warnings.add("عمود نحيف (λ>100) - فحص P-Delta مطلوب")

        return ColumnDesignResult(
            axialCheck = axialCheck,
            flexuralCheck = null,
            slendernessRatio = lambda,
            isSlender = isSlender,
            isSafe = axialCheck.isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * فحص عمود تحت حمل محوري + عزم انحناء (معادلة التفاعل)
     * AISC H1: Pu/φPn + Mux/(φb×Mnx) + Muy/(φb×Mny) ≤ 1.0
     */
    fun checkColumnCombined(
        Pu: Double,           // kN
        Mux: Double,          // kN.m
        Muy: Double,          // kN.m
        section: SectionProperties,
        grade: SteelGrade = SteelGrade.ST37,
        K: Double = 1.0,
        L: Double = 3000.0
    ): ColumnDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // 1. فحص الضغط المحوري
        val compressionResult = checkColumnCompression(Pu, section, grade, K, L)
        val phiPn = PHI_COMPRESSION * calculateFcr(compressionResult.slendernessRatio, grade) * section.A / 1000.0

        // 2. فحص الانحناء حول X
        val Mnx = PHI_BENDING * grade.fy * section.Zx / 1e6  // kN.m
        val Mny = PHI_BENDING * grade.fy * section.Zy / 1e6  // kN.m

        // 3. معادلة التفاعل (AISC H1-1a) عندما Pu/φPn ≥ 0.2:
        // Pu/(φPn) + 8/9 × (Mux/φMnx + Muy/φMny) ≤ 1.0
        val pr = Pu / phiPn
        val interaction = if (pr >= 0.2) {
            // AISC H1-1a
            pr + (8.0 / 9.0) * (Mux / Mnx + Muy / Mny)
        } else {
            // AISC H1-1b: Pu/(2φPn) + (Mux/φMnx + Muy/φMny) ≤ 1.0
            pr / 2.0 + (Mux / Mnx + Muy / Mny)
        }

        val flexuralCheck = SteelCheckResult(
            utilizationRatio = interaction,
            isSafe = interaction <= 1.0,
            classification = when {
                interaction < 0.5 -> "Economic"
                interaction < 0.85 -> "Adequate"
                interaction <= 1.0 -> "Near Limit"
                else -> "Unsafe"
            },
            details = String.format("P/(φPn)=%.2f, ΣM/(φMn)=%.2f, Interaction=%.2f", pr, Mux/Mnx + Muy/Mny, interaction)
        )

        val isSafe = interaction <= 1.0
        if (!isSafe) warnings.add(String.format("معادلة التفاعل = %.2f > 1.0 - زِد المقطع", interaction))

        codeNotes.add("AISC H1: Combined Axial + Flexure")
        codeNotes.add(String.format("Eq: %s (Pu/φPn=%.2f)", if (pr >= 0.2) "H1-1a" else "H1-1b", pr))

        return ColumnDesignResult(
            axialCheck = compressionResult.axialCheck,
            flexuralCheck = flexuralCheck,
            slendernessRatio = compressionResult.slendernessRatio,
            isSlender = compressionResult.isSlender,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== حسابات مساعدة =====================

    /**
     * حساب قدرة الالتواء البعرضي (Lateral-Torsional Buckling)
     * ECP 205 / AISC Chapter F2
     */
    private fun calculateLtbCapacity(
        Mn_full: Double,      // kN.m - قدرة الانحناء الكاملة
        Lb: Double,           // mm - طول الدعم البعرضي
        section: SectionProperties,
        grade: SteelGrade,
        Cb: Double = 1.0
    ): Double {
        val ry = section.ry
        // Lp = 1.76 × ry × √(E/Fy) (AISC F2-5)
        val Lp = 1.76 * ry * sqrt(E_STEEL / grade.fy) // mm

        // Lr حساب حسب AISC F2-6
        // rt = √(√(Iyc × Cw) / Sx) - تقريبي للمقاطع المدرفلة
        val Iyc = section.Iy
        val Cw = if (section.tw > 0 && section.tf > 0) {
            val h_web = (section.h - 2 * section.tf).coerceAtLeast(1.0)
            h_web * h_web * section.b * section.b * section.tw / 12.0
        } else {
            section.Iy * 1000.0
        }
        val rt = sqrt(sqrt(Iyc * Cw) / section.Sx) * 0.9
        // Lr = 1.95 × rt × √(E / (0.7 × Fy)) (AISC F2-6)
        val Lr = 1.95 * rt * sqrt(E_STEEL / (0.7 * grade.fy))

        return when {
            Lb <= Lp -> Mn_full // المنطقة البلاستيكية - قدرة كاملة
            Lb <= Lr -> {
                // المنطقة اللدنة (Inelastic LTB) - AISC F2-2
                val Mn_Lr = 0.7 * grade.fy * section.Sx / 1e6
                Cb * Mn_full - (Cb * Mn_full - Mn_Lr) * ((Lb - Lp) / (Lr - Lp).coerceAtLeast(1.0))
            }
            else -> {
                // المنطقة المرنة (Elastic LTB) - AISC F2-3
                val Fcr_ltb = Cb * PI * PI * E_STEEL / ((Lb / ry).pow(2))
                Fcr_ltb * section.Sx / 1e6
            }
        }
    }

    /**
     * حساب إجهاد الانبعاج الحرج
     * AISC Table 4-14 / ECP 205
     */
    private fun calculateFcr(lambda: Double, grade: SteelGrade): Double {
        val Fe = PI * PI * E_STEEL / (lambda * lambda) // Euler buckling stress
        val Fy = grade.fy

        return when {
            lambda <= 4.71 * sqrt(E_STEEL / Fy) -> {
                // Inelastic buckling
                0.658.pow(Fy / Fe) * Fy
            }
            else -> {
                // Elastic buckling
                0.877 * Fe
            }
        }
    }

    /**
     * فحص الانبعاج المحلي للقطاعات المدرفلة
     * ECP 205 Table 5.1 / AISC Table B4.1a
     */
    private fun checkLocalBuckling(section: SectionProperties, grade: SteelGrade): SteelCheckResult {
        // فحص الشفة: λf = (b - tw) / (2 × tf)
        val lambdaFlange = (section.b - section.tw) / (2 * section.tf)
        val lambdaFlangeLimit = 0.56 * sqrt(E_STEEL / grade.fy)
        val flangeCompact = lambdaFlange <= lambdaFlangeLimit

        // فحص الجذع: λw = h / tw
        val lambdaWeb = (section.h - 2 * section.tf) / section.tw
        val lambdaWebCompact = lambdaWeb <= 1.49 * sqrt(E_STEEL / grade.fy)

        val isCompact = flangeCompact && lambdaWebCompact
        val classification = when {
            isCompact -> "Compact (مدمج)"
            lambdaFlange <= lambdaFlangeLimit * 1.5 && lambdaWeb <= 3.0 * sqrt(E_STEEL / grade.fy) -> "Noncompact (غير مدمج)"
            else -> "Slender (نحيف)"
        }

        return SteelCheckResult(
            utilizationRatio = max(lambdaFlange / lambdaFlangeLimit, lambdaWeb / (1.49 * sqrt(E_STEEL / grade.fy))),
            isSafe = true, // هذا فحص تصنيف وليس فحص أمان
            classification = classification,
            details = "Flange λf=${String.format("%.1f", lambdaFlange)} (limit=${String.format("%.1f", lambdaFlangeLimit)}), Web λw=${String.format("%.1f", lambdaWeb)}"
        )
    }

    // حساب النحافة (طريقة قديمة للمتوافقية)
    fun calculateSlenderness(k: Double, L: Double, r: Double): Double {
        return (k * L) / r
    }

    // فحص سريع (طريقة قديمة للمتوافقية)
    fun checkBeamCapacity(
        mu: Double,
        phi: Double = PHI_BENDING,
        fy: Double,
        zx: Double  // cm³
    ): SteelCheckResult {
        // تحويل Zx من cm³ إلى mm³
        val Zx_mm3 = zx * 1000.0
        val nominalCapacity = fy * Zx_mm3 / 1e6 // kN.m
        val designCapacity = phi * nominalCapacity
        val ratio = mu / designCapacity

        return SteelCheckResult(
            utilizationRatio = ratio,
            isSafe = ratio <= 1.0,
            classification = if (ratio < 0.8) "Economic" else "Optimized",
            details = "φMn = ${String.format("%.1f", designCapacity)} kN.m"
        )
    }
}