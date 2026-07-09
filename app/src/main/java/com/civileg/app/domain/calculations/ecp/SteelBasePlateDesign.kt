package com.civileg.app.domain.calculations.ecp

import kotlin.math.*

/**
 * تصميم القواعد المعدنية للمنشآت الحديدية
 * Design of Steel Base Plates for Steel Structures
 *
 * يغطي:
 * - القاعدة المركزية (Concentrically Loaded Base Plates)
 * - القواعد غير المركزية (Eccentric Base Plates)
 * - قواعد الجيب (Pocket Base Plates)
 *
 * المراجع:
 * - ECP 205 (الكود المصري للمنشآت المعدنية) - البنود الخاصة بالقواعد
 * - AISC 360-16 Chapter 14 (Base Plate Design)
 * - AISC Design Guide 1 (Base Plate and Anchor Rod Design, 2nd Ed.)
 *
 * @author Civileg Engineering Team
 */
class SteelBasePlateDesign {

    companion object {
        // ===================== معاملات المقاومة (Resistance Factors) =====================
        // φ factors per AISC 360-16 / ECP 205 LRFD
        private const val PHI_BEARING_CONCRETE = 0.65  // معامل المقاومة لضغط الخرسانة (AISC 360-16 §J8)
        private const val PHI_BENDING_PLATE = 0.90     // معامل المقاومة لانحناء اللوح (AISC 360-16 §F)
        private const val PHI_TENSION_BOLT = 0.75      // معامل المقاومة لشد البراغي (AISC 360-16 Table J3.2)

        // ===================== ثوابت التصميم (Design Constants) =====================
        private const val MIN_OVERHANG_C = 50.0       // أقل تبريز (mm) - الحد الأدنى
        private const val MAX_OVERHANG_C = 100.0      // أقصى تبريز (mm) - الحد الأقصى
        private const val DEFAULT_OVERHANG_C = 75.0   // التبريز الافتراضي (mm)
        private const val MIN_PLATE_THICKNESS = 12.0  // أقل سماكة للوحة القاعدة (mm)

        // ===================== ثوابت المونة (Grout Constants) =====================
        private const val MIN_GROUT_THICKNESS = 25.0   // أقل سماكة للمونة (mm)
        private const val MAX_GROUT_THICKNESS = 75.0   // أقصى سماكة للمونة (mm)
        private const val DEFAULT_GROUT_THICKNESS = 50.0 // سماكة المونة الافتراضية (mm)

        // ===================== ثوابت البراغي الارتكازية (Anchor Bolt Constants) =====================
        private const val MIN_EDGE_DISTANCE_BOLT = 40.0  // أقل مسافة حافة للبرغي (mm)
        private const val MIN_EMBEDMENT_HOOK = 12.0       // أقل طول تثبيت للبرغي المعقوف (× db)
        private const val MIN_EMBEDMENT_HEADED = 8.0      // أقل طول تثبيت للبرغي برأس (× db)
        private const val BOLT_SPACING_FACTOR = 3.0       // أقل مسافة بين البراغي (× db)

        // ===================== درجات البراغي (Bolt Grades) =====================
        /**
         * خواص درجات البراغي حسب ISO 898-1 / ECP 205
         */
        enum class BoltGrade(
            val designation: String,
            val fu: Double,  // MPa - إجهاد الشد الأقصى
            val fy: Double   // MPa - إجهاد الخضوع
        ) {
            GRADE_4_6("4.6", 400.0, 240.0),
            GRADE_4_8("4.8", 400.0, 320.0),
            GRADE_5_6("5.6", 500.0, 300.0),
            GRADE_5_8("5.8", 500.0, 400.0),
            GRADE_6_8("6.8", 600.0, 480.0),
            GRADE_8_8("8.8", 800.0, 640.0),
            GRADE_10_9("10.9", 1000.0, 900.0);

            fun getGradeName(): String = designation
        }

        // ===================== أقطار البراغي القياسية (Standard Bolt Diameters) =====================
        val STANDARD_BOLT_DIAMETERS = listOf(
            12.0, 16.0, 20.0, 22.0, 24.0, 27.0, 30.0, 33.0, 36.0, 42.0, 48.0
        )

        // ===================== سماكات اللوح القياسية (Standard Plate Thicknesses) =====================
        val STANDARD_PLATE_THICKNESSES = listOf(
            12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 30.0,
            32.0, 35.0, 38.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0, 90.0, 100.0
        )
    }

    // ===================== كيانات البيانات (Data Classes) =====================

    /**
     * نتيجة تصميم البراغي الارتكازية
     * Anchor Bolt Design Result
     */
    data class AnchorBoltResult(
        val numberOfBolts: Int,          // عدد البراغي
        val boltDiameter: Double,        // قطر البرغي (mm)
        val boltGrade: String,           // درجة البرغي ("4.6", "8.8", etc.)
        val boltSpacing: Double,         // المسافة بين البراغي (mm)
        val edgeDistance: Double,        // المسافة من الحافة (mm)
        val tensionCapacity: Double,     // سعة الشد لكل برغي (kN)
        val requiredTension: Double,     // الشد المطلوب لكل برغي (kN)
        val shearCapacity: Double,       // سعة القص لكل برغي (kN)
        val requiredShear: Double,       // القص المطلوب لكل برغي (kN)
        val embedmentLength: Double,     // طول التثبيت (mm)
        val boltArea: Double,            // مساحة البرغي (mm²)
        val isSafe: Boolean              // هل التصميم آمن؟
    )

    /**
     * نتيجة تصميم المونة
     * Grout Design Result
     */
    data class GroutResult(
        val thickness: Double,           // سماكة المونة (mm)
        val requiredStrength: Double,    // مقاومة المونة المطلوبة (MPa)
        val providedStrength: Double,    // مقاومة المونة المتاحة (MPa)
        val isAdequate: Boolean          // هل المونة كافية؟
    )

    /**
     * نتيجة تصميم قاعدة الجيب
     * Pocket Base Plate Design Result
     */
    data class PocketResult(
        val pocketDepth: Double,         // عمق الجيب (mm)
        val pocketWidth: Double,         // عرض الجيب (mm)
        val pocketLength: Double,        // طول الجيب (mm)
        val boltDiameter: Double,        // قطر البرغي (mm)
        val concreteCover: Double,           // غطاء الخرسانة (mm)
        val isSafe: Boolean              // هل التصميم آمن؟
    )

    /**
     * نتيجة التصميم الشاملة للقاعدة المعدنية
     * Comprehensive Base Plate Design Result
     */
    data class BasePlateResult(
        val plateWidth: Double,           // عرض اللوح (mm) - B
        val plateLength: Double,          // طول اللوح (mm) - L
        val plateThickness: Double,       // سماكة اللوح (mm) - tp
        val maxBearingPressure: Double,   // أقصى ضغط تحمل (MPa)
        val concreteCapacity: Double,     // سعة الخرسانة (MPa)
        val anchorBolts: AnchorBoltResult, // نتيجة تصميم البراغي
        val grout: GroutResult,           // نتيجة تصميم المونة
        val isSafe: Boolean,              // هل التصميم آمن؟
        val utilizationRatio: Double,     // معامل الاستغلال
        val warnings: List<String>,       // تحذيرات التصميم
        val codeNotes: List<String>       // ملاحظات الكود
    )

    /**
     * نتيجة تصميم القاعدة غير المركزية
     * Eccentric Base Plate Design Result
     */
    data class EccentricBasePlateResult(
        val plateWidth: Double,           // عرض اللوح (mm) - B
        val plateLength: Double,          // طول اللوح (mm) - L
        val plateThickness: Double,       // سماكة اللوح (mm) - tp
        val maxBearingPressure: Double,   // أقصى ضغط تحمل (MPa)
        val minBearingPressure: Double,   // أقل ضغط تحمل (MPa) (قد يكون سالب = شد)
        val effectiveBearingLength: Double, // الطول الفعال للتحمل (mm)
        val anchorBolts: AnchorBoltResult, // نتيجة تصميم البراغي
        val grout: GroutResult,           // نتيجة تصميم المونة
        val isSafe: Boolean,              // هل التصميم آمن؟
        val tensionSide: Boolean,         // هل يوجد شد في أحد الجانبين؟
        val utilizationRatio: Double,     // معامل الاستغلال
        val warnings: List<String>,       // تحذيرات التصميم
        val codeNotes: List<String>       // ملاحظات الكود
    )

    /**
     * نتيجة تصميم قاعدة الجيب
     * Pocket Base Plate Result
     */
    data class PocketBasePlateResult(
        val plateWidth: Double,           // عرض اللوح (mm) - B
        val plateLength: Double,          // طول اللوح (mm) - L
        val plateThickness: Double,       // سماكة اللوح (mm) - tp
        val pocket: PocketResult,         // تفاصيل الجيب
        val anchorBolts: AnchorBoltResult, // نتيجة تصميم البراغي
        val grout: GroutResult,           // نتيجة تصميم المونة
        val isSafe: Boolean,              // هل التصميم آمن؟
        val utilizationRatio: Double,     // معامل الاستغلال
        val warnings: List<String>,       // تحذيرات التصميم
        val codeNotes: List<String>       // ملاحظات الكود
    )

    /**
     * مدخلات تصميم القاعدة المركزية
     * Concentric Base Plate Design Input
     */
    data class ConcentricInput(
        val Pu: Double,             // محصلة القوة المحورية (kN) - محصلة الأحمال
        val Mux: Double,            // العزم حول المحور X (kN.m)
        val Muy: Double,            // العزم حول المحور Y (kN.m)
        val Vu: Double,             // قوة القص (kN)
        val bf: Double,             // عرض شفة العمود (mm)
        val dc: Double,             // عمق العمود (mm)
        val Fy: Double,             // إجهاد الخضوع للصلب (MPa)
        val fpc: Double,            // مقاومة ضغط الخرسانة (MPa)
        val groutStrength: Double = 0.0, // مقاومة المونة (MPa) - إذا صفر = تساوي الخرسانة
        val overhangC: Double = DEFAULT_OVERHANG_C, // التبريز (mm)
        val numAnchorBolts: Int = 4,       // عدد البراغي
        val boltGrade: BoltGrade = BoltGrade.GRADE_4_6, // درجة البراغي
        val boltDiameter: Double = 20.0,   // قطر البرغي (mm)
        val isHookedBolt: Boolean = true   // هل البرغي معقوف؟
    )

    /**
     * مدخلات تصميم القاعدة غير المركزية
     * Eccentric Base Plate Design Input
     */
    data class EccentricInput(
        val Pu: Double,             // القوة المحورية (kN) - موجبة للضغط
        val Mux: Double,            // العزم حول المحور X (kN.m)
        val Muy: Double,            // العزم حول المحور Y (kN.m)
        val Vu: Double,             // قوة القص (kN)
        val bf: Double,             // عرض شفة العمود (mm)
        val dc: Double,             // عمق العمود (mm)
        val Fy: Double,             // إجهاد الخضوع للصلب (MPa)
        val fpc: Double,            // مقاومة ضغط الخرسانة (MPa)
        val groutStrength: Double = 0.0, // مقاومة المونة (MPa)
        val overhangC: Double = DEFAULT_OVERHANG_C, // التبريز (mm)
        val boltGrade: BoltGrade = BoltGrade.GRADE_4_6, // درجة البراغي
        val boltDiameter: Double = 24.0,   // قطر البرغي (mm)
        val isHookedBolt: Boolean = true   // هل البرغي معقوف؟
    )

    /**
     * مدخلات تصميم قاعدة الجيب
     * Pocket Base Plate Design Input
     */
    data class PocketInput(
        val Pu: Double,             // محصلة القوة المحورية (kN)
        val Mux: Double,            // العزم حول المحور X (kN.m)
        val Muy: Double,            // العزم حول المحور Y (kN.m)
        val Vu: Double,             // قوة القص (kN)
        val bf: Double,             // عرض شفة العمود (mm)
        val dc: Double,             // عمق العمود (mm)
        val Fy: Double,             // إجهاد الخضوع للصلب (MPa)
        val fpc: Double,            // مقاومة ضغط الخرسانة (MPa)
        val boltGrade: BoltGrade = BoltGrade.GRADE_4_6, // درجة البراغي
        val boltDiameter: Double = 20.0,   // قطر البرغي (mm)
        val concreteCover: Double = 50.0   // غطاء الخرسانة (mm)
    )

    // ============================================================================
    // ==================== 1. القاعدة المركزية (Concentric Base Plate) ===========
    // ============================================================================
    // المرجع: AISC 360-16 Chapter 14 / ECP 205
    // ============================================================================

    /**
     * تصميم القاعدة المركزية - الأحمال المركزة على العمود
     * Design of concentrically loaded base plates
     *
     * يتعامل مع:
     * - الأحمال المحورية فقط
     * - الأحمال المحورية مع عزوم صغيرة (انحراف مركزي صغير)
     *
     * المرجع: AISC 360-16 §14.1 / ECP 205 البند الخاص بالقواعد المركزية
     *
     * @param input مدخلات التصميم
     * @return نتيجة التصميم الشاملة
     */
    fun designConcentricBasePlate(input: ConcentricInput): BasePlateResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("التصميم حسب AISC 360-16 الفصل 14 و ECP 205")
        codeNotes.add("طريقة التصميم: LRFD (Load and Resistance Factor Design)")

        // ==================== 1.1 حساب الانحراف المركزي (Eccentricity) ====================
        // e = √(Mux² + Muy²) / Pu  - المعادلة (1)
        val Mu_resultant = sqrt(input.Mux.pow(2) + input.Muy.pow(2)) // kN.m
        val e_mm = if (input.Pu > 0.01) {
            (Mu_resultant * 1000.0) / input.Pu  // تحويل kN.m إلى kN.mm ثم قسمة على kN = mm
        } else {
            0.0
        }

        codeNotes.add(String.format(
            "الانحراف المركزي e = √(Mux² + Muy²)/Pu = %.1f mm",
            e_mm
        ))

        // ==================== 1.2 حساب المساحة المطلوبة للوح القاعدة ====================
        // A_req = Pu / (φ * f'c)  - المعادلة (2)
        // مع مراعاة معامل التركيز للخرسانة A2/A1
        val Pu_N = input.Pu * 1000.0 // تحويل kN إلى N
        val phi_fc = PHI_BEARING_CONCRETE * input.fpc // MPa - سعة ضغط الخرسانة المعدلة

        // المساحة المطلوبة مع معامل تركيز مقدرة (A2/A1 ≈ 2)
        // √(A2/A1) ≤ 2 per AISC 360-16 §J8
        val concentrationFactor = min(sqrt(2.0), 2.0)
        val A_req = Pu_N / (phi_fc * concentrationFactor) // mm²

        codeNotes.add(String.format(
            "المساحة المطلوبة A_req = Pu/(φ·f'c·√(A2/A1)) = %.0f mm²",
            A_req
        ))

        // ==================== 1.3 حساب أبعاد اللوح (B و L) ====================
        // الحد الأدنى: B ≥ bf + 2c , L ≥ dc + 2c  - AISC 360-16 §14.1
        val c = input.overhangC.coerceIn(MIN_OVERHANG_C, MAX_OVERHANG_C)

        // B: عرض اللوح (عمودي على الشفة) - AISC 360-16 Eq. 14.1
        var B = input.bf + 2 * c
        var L = input.dc + 2 * c

        // التأكد من أن المساحة كافية
        var A_provided = B * L
        if (A_provided < A_req) {
            // زيادة الأبعاد بشكل متناسب لتغطية المساحة المطلوبة
            val scaleFactor = sqrt(A_req / A_provided)
            B *= scaleFactor
            L *= scaleFactor
            // تقريب للأعلى لأقرب 5 مم
            B = ceil(B / 5.0) * 5.0
            L = ceil(L / 5.0) * 5.0
        }

        // التأكد من أن B ≥ bf + 2*c و L ≥ dc + 2*c بعد التعديل
        B = max(B, input.bf + 2 * c)
        L = max(L, input.dc + 2 * c)

        A_provided = B * L

        codeNotes.add(String.format(
            "أبعاد اللوح: B = %.0f mm, L = %.0f mm, المساحة = %.0f mm²",
            B, L, A_provided
        ))

        // ==================== 1.4 فحص الانحراف المركزي (Small vs Large Eccentricity) ====================
        val isSmallEccentricity = e_mm < B / 6.0

        if (!isSmallEccentricity) {
            warnings.add(String.format(
                "الانحراف المركزي (e=%.1f mm) أكبر من B/6 (%.1f mm) - يُنصح باستخدام تصميم القاعدة غير المركزية",
                e_mm, B / 6.0
            ))
        }

        // ==================== 1.5 حساب ضغط التحمل الفعلي ====================
        // للحالة المركزية: f_pu = Pu / (B × L)
        // للحالة ذات الانحراف الصغير: f_pu = Pu / (B × L) × [1 + 6e/B]
        val f_pu_uniform = Pu_N / (B * L) // MPa - ضغط التحمل المنتظم

        val maxBearingPressure = if (isSmallEccentricity) {
            // معادلة ضغط التحمل مع الانحراف: f_max = P/(BL) × (1 + 6e/B)
            // باستخدام e الناتج عن العزوم
            val e_normalized = if (B > 0) e_mm / B else 0.0
            f_pu_uniform * (1.0 + 6.0 * e_normalized)
        } else {
            // للحالة ذات الانحراف الكبير: استخدام طريقة الكابولي
            f_pu_uniform * 2.0  // تقريب محافظ
        }

        // ==================== 1.6 فحص ضغط التحمل على الخرسانة ====================
        // φ·f'c·√(A2/A1) ≤ 2·φ·f'c  - AISC 360-16 §J8
        val concreteCapacity = phi_fc * concentrationFactor
        val bearingRatio = maxBearingPressure / concreteCapacity

        if (bearingRatio > 1.0) {
            warnings.add(String.format(
                "ضغط التحمل (%.2f MPa) يتجاوز سعة الخرسانة (%.2f MPa) - يجب زيادة أبعاد القاعدة",
                maxBearingPressure, concreteCapacity
            ))
        }

        // ==================== 1.7 حساب سماكة اللوح (Plate Thickness) ====================
        // المرجع: AISC 360-16 §14.1 / AISC Design Guide 1

        // حساب أطوال الكابولي - AISC 360-16 Eq. 14.3, 14.4
        // m = (B - 0.8*bf) / 2  - مسافة تبريز من الشفة
        val m = (B - 0.8 * input.bf) / 2.0
        // n = (L - 0.95*dc) / 2  - مسافة تبريز من العمق
        val n = (L - 0.95 * input.dc) / 2.0

        // λ = max(√(2m²), √(2n²), √(m² + n²))  - AISC 360-16 Eq. 14.5
        val lambda_m = sqrt(2.0 * m.pow(2))
        val lambda_n = sqrt(2.0 * n.pow(2))
        val lambda_mn = sqrt(m.pow(2) + n.pow(2))
        val lambda = maxOf(lambda_m, lambda_n, lambda_mn)

        codeNotes.add(String.format(
            "أطوال الكابولي: m = %.1f mm, n = %.1f mm, λ = %.1f mm",
            m, n, lambda
        ))

        // حساب سماكة اللوح المطلوبة - AISC 360-16 Eq. 14.6
        // tp = λ × √(2 × Pu / (0.9 × Fy × B × L))
        // Pu هنا بالنيوتن
        val tp_required = lambda * sqrt(2.0 * Pu_N / (PHI_BENDING_PLATE * input.Fy * B * L))

        // حساب السماكة الأدنى البديلة - المعادلة المحافظة
        // tp_min = √(2 × Pu × m²) / (0.9 × Fy × B × L)
        val tp_min_from_m = sqrt(2.0 * Pu_N * m.pow(2)) / (PHI_BENDING_PLATE * input.Fy * B * L)

        // السماكة المطلوبة هي الأكبر
        val tp_calc = max(tp_required, tp_min_from_m)

        // تقريب لأقرب سماكة قياسية
        val tp = selectStandardThickness(tp_calc)

        if (tp < MIN_PLATE_THICKNESS) {
            warnings.add(String.format(
                "السماكة المحسوبة (%.1f mm) أقل من الحد الأدنى (%.0f mm) - تم استخدام الحد الأدنى",
                tp_calc, MIN_PLATE_THICKNESS
            ))
        }

        codeNotes.add(String.format(
            "سماكة اللوح المحسوبة = %.1f mm → السماكة القياسية = %.0f mm",
            tp_calc, tp
        ))

        // ==================== 1.8 تصميم البراغي الارتكازية ====================
        val anchorBolts = designAnchorBolts(
            Pu = input.Pu,
            Mux = input.Mux,
            Muy = input.Muy,
            Vu = input.Vu,
            B = B,
            L = L,
            bf = input.bf,
            dc = input.dc,
            boltGrade = input.boltGrade,
            boltDiameter = input.boltDiameter,
            numBolts = input.numAnchorBolts,
            isHookedBolt = input.isHookedBolt
        )

        // ==================== 1.9 تصميم المونة ====================
        val grout = designGrout(
            fpc = input.fpc,
            groutStrength = input.groutStrength,
            thickness = DEFAULT_GROUT_THICKNESS
        )

        // ==================== 1.10 حساب معامل الاستغلال ====================
        // معامل الاستغلال = أقصى (نسبة ضغط التحمل، نسبة سماكة اللوح)
        val bearingUtilization = maxBearingPressure / concreteCapacity
        val thicknessUtilization = tp_calc / tp // tp هو السماكة القياسية ≥ tp_calc
        val boltUtilization = if (anchorBolts.tensionCapacity > 0) {
            anchorBolts.requiredTension / anchorBolts.tensionCapacity
        } else 0.0

        val utilizationRatio = maxOf(bearingUtilization, thicknessUtilization, boltUtilization)

        // ==================== 1.11 التحقق النهائي من الأمان ====================
        val isSafe = bearingUtilization <= 1.0 && thicknessUtilization <= 1.0 && anchorBolts.isSafe

        return BasePlateResult(
            plateWidth = B,
            plateLength = L,
            plateThickness = tp,
            maxBearingPressure = maxBearingPressure,
            concreteCapacity = concreteCapacity,
            anchorBolts = anchorBolts,
            grout = grout,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================================
    // ========== 2. القاعدة غير المركزية (Eccentric Base Plate) =================
    // ============================================================================
    // المرجع: AISC 360-16 Chapter 14 / ECP 205 / AISC Design Guide 1
    // ============================================================================

    /**
     * تصميم القاعدة غير المركزية - أحمال محورية مع عزوم كبيرة
     * Design of eccentrically loaded base plates
     *
     * حالات التصميم:
     * - ضغط كامل (Full Compression): لا يوجد شد في البراغي
     * - ضغط جزئي (Partial Compression): ضغط على جزء فقط + شد في البراغي
     * - رفع (Uplift): القوة المحورية صفر أو سحب
     *
     * المرجع: AISC 360-16 §14.3 / ECP 205 / AISC Design Guide 1 Sec. 3.3
     *
     * @param input مدخلات التصميم
     * @return نتيجة التصميم
     */
    fun designEccentricBasePlate(input: EccentricInput): EccentricBasePlateResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("التصميم حسب AISC 360-16 الفصل 14 و ECP 205 - قاعدة غير مركزية")
        codeNotes.add("طريقة التصميم: LRFD")

        // ==================== 2.1 تحويل الوحدات ====================
        val Pu_N = input.Pu * 1000.0 // kN → N
        val Mx_Nmm = input.Mux * 1e6  // kN.m → N.mm
        val My_Nmm = input.Muy * 1e6  // kN.m → N.mm
        val Mu_Nmm = sqrt(Mx_Nmm.pow(2) + My_Nmm.pow(2)) // العزم المحصلة

        // ==================== 2.2 تحديد عدد البراغي حسب حجم العزوم ====================
        // 4 براغي للعزوم الصغيرة، 6 أو 8 للعزوم الكبيرة
        val e_total = if (input.Pu > 0.01) Mu_Nmm / Pu_N else 0.0
        val numBolts = when {
            e_total > 200.0 -> 8  // عزوم كبيرة جداً
            e_total > 100.0 -> 6  // عزوم متوسطة
            else -> 4             // عزوم صغيرة
        }

        codeNotes.add(String.format(
            "الانحراف المركزي الكلي e = %.1f mm → عدد البراغي = %d",
            e_total, numBolts
        ))

        // ==================== 2.3 حساب الأبعاد الأولية ====================
        // أبعاد أكبر للتعامل مع العزوم
        val c = input.overhangC.coerceIn(MIN_OVERHANG_C, MAX_OVERHANG_C)
        var B = input.bf + 2 * c
        var L = input.dc + 2 * c

        // للقواعد غير المركزية، نزيد الأبعاد بنسبة تتناسب مع العزم
        if (input.Pu > 0.01) {
            val eccentricityFactor = 1.0 + (e_total / (max(B, L) / 6.0)).coerceAtMost(1.0)
            B *= sqrt(eccentricityFactor)
            L *= sqrt(eccentricityFactor)
        }

        // تقريب
        B = ceil(B / 5.0) * 5.0
        L = ceil(L / 5.0) * 5.0

        // التأكد من الحد الأدنى
        B = max(B, input.bf + 2 * MIN_OVERHANG_C)
        L = max(L, input.dc + 2 * MIN_OVERHANG_C)

        codeNotes.add(String.format(
            "أبعاد اللوح المبدئية: B = %.0f mm, L = %.0f mm",
            B, L
        ))

        // ==================== 2.4 حساب ضغط التحمل مع العزوم ====================
        // تحديد اتجاه العزم الرئيسي (نستخدم المحور الأطول)
        // f_max = P/(BL) + M×y/I = P/(BL) × (1 + 6e/L)
        // f_min = P/(BL) × (1 - 6e/L)

        val f_uniform = if (B * L > 0) Pu_N / (B * L) else 0.0 // MPa

        // حساب الانحراف حول المحورين
        val ex = if (input.Pu > 0.01) Mx_Nmm / Pu_N else 0.0
        val ey = if (input.Pu > 0.01) My_Nmm / Pu_N else 0.0

        // نستخدم المحور الأكثر حرجاً
        val e_critical = maxOf(
            if (L > 0) ex / L else 0.0,
            if (B > 0) ey / B else 0.0
        )

        val maxBearingPressure = f_uniform * (1.0 + 6.0 * e_critical)
        val minBearingPressure = f_uniform * (1.0 - 6.0 * e_critical)

        val tensionSide = minBearingPressure < 0

        codeNotes.add(String.format(
            "ضغط التحمل: أقصى = %.2f MPa، أدنى = %.2f MPa%s",
            maxBearingPressure, minBearingPressure,
            if (tensionSide) " (يوجد شد - يحتاج براغي)" else " (ضغط كامل)"
        ))

        // ==================== 2.5 حساب الطول الفعال للتحمل ====================
        // إذا كان يوجد شد (tension side)، نحسب الطول الفعال للضغط
        val effectiveBearingLength = if (tensionSide && L > 0) {
            // للعزوم الكبيرة: ضغط على جزء فقط
            // y = L/2 - e, طول الضغط = 3y
            val y = L / 2.0 - ex
            if (y > 0 && y < L / 2.0) {
                3.0 * y
            } else {
                L // ضغط كامل
            }
        } else {
            L // ضغط كامل على كل المساحة
        }

        // ==================== 2.6 فحص ضغط التحمل ====================
        val phi_fc = PHI_BEARING_CONCRETE * input.fpc
        val concentrationFactor = min(sqrt(2.0), 2.0)
        val concreteCapacity = phi_fc * concentrationFactor

        if (maxBearingPressure > concreteCapacity) {
            warnings.add(String.format(
                "ضغط التحمل (%.2f MPa) يتجاوز سعة الخرسانة (%.2f MPa)",
                maxBearingPressure, concreteCapacity
            ))

            // محاولة زيادة الأبعاد
            val scaleFactor = sqrt(maxBearingPressure / concreteCapacity)
            B = ceil(B * scaleFactor / 5.0) * 5.0
            L = ceil(L * scaleFactor / 5.0) * 5.0
            codeNotes.add(String.format(
                "تم زيادة الأبعاد إلى: B = %.0f mm, L = %.0f mm",
                B, L
            ))
        }

        // ==================== 2.7 حساب سماكة اللوح ====================
        // للقواعد غير المركزية: استخدام طريقة الكابولي
        // AISC Design Guide 1, Section 3.4
        val m = (B - 0.8 * input.bf) / 2.0
        val n = (L - 0.95 * input.dc) / 2.0
        val lambda_m = sqrt(2.0 * m.pow(2))
        val lambda_n = sqrt(2.0 * n.pow(2))
        val lambda_mn = sqrt(m.pow(2) + n.pow(2))
        val lambda = maxOf(lambda_m, lambda_n, lambda_mn)

        // معامل زيادة للعزوم
        val momentFactor = if (tensionSide) 1.5 else 1.2

        // tp = λ × √(2 × Pu × momentFactor / (0.9 × Fy × B × L))
        val tp_required = lambda * sqrt(
            2.0 * Pu_N * momentFactor / (PHI_BENDING_PLATE * input.Fy * B * L)
        )
        val tp = selectStandardThickness(tp_required)

        codeNotes.add(String.format(
            "سماكة اللوح: المحسوبة = %.1f mm → القياسية = %.0f mm (معامل عزوم = %.1f)",
            tp_required, tp, momentFactor
        ))

        // ==================== 2.8 تصميم البراغي ====================
        val anchorBolts = designAnchorBolts(
            Pu = input.Pu,
            Mux = input.Mux,
            Muy = input.Muy,
            Vu = input.Vu,
            B = B,
            L = L,
            bf = input.bf,
            dc = input.dc,
            boltGrade = input.boltGrade,
            boltDiameter = input.boltDiameter,
            numBolts = numBolts,
            isHookedBolt = true
        )

        // ==================== 2.9 تصميم المونة ====================
        val grout = designGrout(
            fpc = input.fpc,
            groutStrength = input.groutStrength,
            thickness = DEFAULT_GROUT_THICKNESS
        )

        // ==================== 2.10 حساب معامل الاستغلال ====================
        val bearingUtil = maxBearingPressure / concreteCapacity
        val thickUtil = tp_required / tp
        val boltUtil = if (anchorBolts.tensionCapacity > 0) {
            anchorBolts.requiredTension / anchorBolts.tensionCapacity
        } else 0.0

        val utilizationRatio = maxOf(bearingUtil, thickUtil, boltUtil)
        val isSafe = bearingUtil <= 1.0 && thickUtil <= 1.0 && anchorBolts.isSafe

        if (tensionSide && !anchorBolts.isSafe) {
            warnings.add("يوجد شد في أحد الجانبين - البراغي المختارة غير كافية!")
        }

        return EccentricBasePlateResult(
            plateWidth = B,
            plateLength = L,
            plateThickness = tp,
            maxBearingPressure = maxBearingPressure,
            minBearingPressure = minBearingPressure,
            effectiveBearingLength = effectiveBearingLength,
            anchorBolts = anchorBolts,
            grout = grout,
            isSafe = isSafe,
            tensionSide = tensionSide,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================================
    // =========== 3. قاعدة الجيب (Pocket Base Plate) ============================
    // ============================================================================
    // المرجع: AISC 360-16 / PCI Design Handbook / ECP 205
    // ============================================================================

    /**
     * تصميم قاعدة الجيب للوصلات المسبقة الصب
     * Design of pocket base plates for precast connections
     *
     * يُستخدم في وصلات الأعمدة المسبقة الصب مع القواعد المصبوبة في الموقع
     *
     * المرجع: PCI Design Handbook / ECP 205 / ACI 318 Chapter 17
     *
     * @param input مدخلات التصميم
     * @return نتيجة التصميم
     */
    fun designPocketBasePlate(input: PocketInput): PocketBasePlateResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        codeNotes.add("التصميم حسب ECP 205 و PCI Design Handbook - قاعدة جيب")
        codeNotes.add("يُستخدم في وصلات الأعمدة المسبقة الصب")

        // ==================== 3.1 حساب أبعاد اللوح ====================
        val c = DEFAULT_OVERHANG_C
        val B = input.bf + 2 * c
        val L = input.dc + 2 * c

        codeNotes.add(String.format(
            "أبعاد اللوح: B = %.0f mm, L = %.0f mm",
            B, L
        ))

        // ==================== 3.2 حساب سماكة اللوح ====================
        val Pu_N = input.Pu * 1000.0
        val m = (B - 0.8 * input.bf) / 2.0
        val n = (L - 0.95 * input.dc) / 2.0
        val lambda = maxOf(sqrt(2.0 * m.pow(2)), sqrt(2.0 * n.pow(2)), sqrt(m.pow(2) + n.pow(2)))

        val tp_required = lambda * sqrt(2.0 * Pu_N / (PHI_BENDING_PLATE * input.Fy * B * L))
        val tp = selectStandardThickness(tp_required)

        // ==================== 3.3 تصميم الجيب (Pocket Design) ====================
        // المرجع: PCI Design Handbook / ACI 318 Chapter 17
        // عمق الجيب: 1.5 × قطر البرغي + 50 mm
        val pocketDepth = 1.5 * input.boltDiameter + 50.0

        // عرض الجيب: قطر البرغي + 75 mm لكل جانب
        val pocketWidth = input.boltDiameter + 2 * 75.0

        // طول الجيب يعتمد على عدد البراغي وتباعدها
        val boltSpacing = input.boltDiameter * BOLT_SPACING_FACTOR
        val pocketLength = if (boltSpacing > 0) {
            input.boltDiameter + 2 * 75.0 + (4 - 1) * boltSpacing
        } else {
            pocketWidth
        }

        val pocket = PocketResult(
            pocketDepth = pocketDepth,
            pocketWidth = pocketWidth,
            pocketLength = pocketLength,
            boltDiameter = input.boltDiameter,
            concreteCover = input.concreteCover,
            isSafe = pocketDepth >= 1.5 * input.boltDiameter
        )

        codeNotes.add(String.format(
            "أبعاد الجيب: عمق = %.0f mm, عرض = %.0f mm, طول = %.0f mm",
            pocketDepth, pocketWidth, pocketLength
        ))
        codeNotes.add(String.format(
            "عمق الجيب = 1.5×db + 50 = 1.5×%.0f + 50 = %.0f mm",
            input.boltDiameter, pocketDepth
        ))

        // ==================== 3.4 تصميم البراغي ====================
        val anchorBolts = designAnchorBolts(
            Pu = input.Pu,
            Mux = input.Mux,
            Muy = input.Muy,
            Vu = input.Vu,
            B = B,
            L = L,
            bf = input.bf,
            dc = input.dc,
            boltGrade = input.boltGrade,
            boltDiameter = input.boltDiameter,
            numBolts = 4,
            isHookedBolt = true
        )

        // ==================== 3.5 تصميم المونة ====================
        val grout = designGrout(
            fpc = input.fpc,
            groutStrength = 0.0,
            thickness = MIN_GROUT_THICKNESS
        )

        // ==================== 3.6 حساب معامل الاستغلال ====================
        val boltUtil = if (anchorBolts.tensionCapacity > 0) {
            anchorBolts.requiredTension / anchorBolts.tensionCapacity
        } else 0.0
        val utilizationRatio = maxOf(tp_required / tp, boltUtil)
        val isSafe = utilizationRatio <= 1.0 && anchorBolts.isSafe && pocket.isSafe

        return PocketBasePlateResult(
            plateWidth = B,
            plateLength = L,
            plateThickness = tp,
            pocket = pocket,
            anchorBolts = anchorBolts,
            grout = grout,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ============================================================================
    // =========== 4. تصميم البراغي الارتكازية (Anchor Bolt Design) ===============
    // ============================================================================
    // المرجع: AISC 360-16 Table 14-2 / ECP 205 / ACI 318 Chapter 17
    // ============================================================================

    /**
     * تصميم البراغي الارتكازية
     * Design of anchor bolts for base plates
     *
     * @param Pu القوة المحورية (kN)
     * @param Mux العزم حول X (kN.m)
     * @param Muy العزم حول Y (kN.m)
     * @param Vu قوة القص (kN)
     * @param B عرض اللوح (mm)
     * @param L طول اللوح (mm)
     * @param bf عرض شفة العمود (mm)
     * @param dc عمق العمود (mm)
     * @param boltGrade درجة البرغي
     * @param boltDiameter قطر البرغي (mm)
     * @param numBolts عدد البراغي
     * @param isHookedBolt هل البرغي معقوف
     * @return نتيجة تصميم البراغي
     */
    private fun designAnchorBolts(
        Pu: Double,
        Mux: Double,
        Muy: Double,
        Vu: Double,
        B: Double,
        L: Double,
        bf: Double,
        dc: Double,
        boltGrade: BoltGrade,
        boltDiameter: Double,
        numBolts: Int,
        isHookedBolt: Boolean
    ): AnchorBoltResult {
        // ==================== 4.1 حساب مساحة البرغي ====================
        // Ab = π × db² / 4  (مساحة الاسمية)
        val Ab = PI * boltDiameter.pow(2) / 4.0 // mm²

        // ==================== 4.2 حساب سعة الشد ====================
        // φRn = φ × Fu × Ab  - AISC 360-16 Table J3.2 / §14.3
        // φ = 0.75 for tension
        val tensionCapacity = PHI_TENSION_BOLT * boltGrade.fu * Ab / 1000.0 // kN

        // ==================== 4.3 حساب سعة القص ====================
        // φRn = φ × 0.4 × Fu × Ab  (for threaded bolts in shear)
        // φ = 0.75 for shear (AISC 360-16)
        val shearCapacity = PHI_TENSION_BOLT * 0.4 * boltGrade.fu * Ab / 1000.0 // kN

        // ==================== 4.4 حساب القوى المؤثرة على كل برغي ====================
        // الشد في البراغي ناتج عن العزوم
        // T_per_bolt = (M - P × L/2) / (n_bolt_tension × arm)
        // حيث arm = المسافة من مركز الضغط إلى مركز البرغي

        val Mu_total = sqrt(Mux.pow(2) + Muy.pow(2)) * 1000.0 // kN.mm

        // الذراع المؤثر (من مركز الضغط إلى مركز مجموعة البراغي المعرضة للشد)
        // للمربع الرباعي: arm ≈ 0.4 × L (تقريباً)
        val arm = 0.4 * min(B, L)

        // عدد البراغي في جهة الشد (نصف العدد تقريباً)
        val numTensionBolts = max(numBolts / 2, 1)

        // الشد المطلوب لكل برغي (kN)
        val tensionFromMoment = if (arm > 0) {
            Mu_total / (numTensionBolts * arm)
        } else 0.0

        // الشد المطلوب لكل برغي (يُضاف تأثير الضغط المحوري)
        // إذا كان Pu موجب (ضغط)، يقلل الشد في البراغي
        // إذا كان Pu سالب (سحب)، يزيد الشد
        val axialTension = max(0.0, -Pu / numBolts) // فقط إذا كان هناك سحب
        val requiredTension = tensionFromMoment + axialTension // kN

        // ==================== 4.5 حساب القص المطلوب لكل برغي ====================
        val requiredShear = Vu / numBolts // kN (توزيع متساوٍ)

        // ==================== 4.6 فحص التفاعل بين الشد والقص ====================
        // AISC 360-16 Eq. J3-3a:
        // (Tu / φTn) + (Vu / φVn) ≤ 1.0
        val interactionRatio = if (tensionCapacity > 0 && shearCapacity > 0) {
            (requiredTension / tensionCapacity) + (requiredShear / shearCapacity)
        } else 0.0

        // ==================== 4.7 حساب المسافات ====================
        // المسافة بين البراغي: 3×db (الحد الأدنى)
        val boltSpacing = (boltDiameter * BOLT_SPACING_FACTOR).coerceAtLeast(MIN_EDGE_DISTANCE_BOLT)

        // المسافة من الحافة: أقل قيمة من 3×db و 40mm
        val edgeDistance = max(boltDiameter * 1.5, MIN_EDGE_DISTANCE_BOLT)

        // ==================== 4.8 حساب طول التثبيت ====================
        // للبرغي المعقوف: 12 × db
        // للبرغي برأس: 8 × db
        val embedmentLength = if (isHookedBolt) {
            boltDiameter * MIN_EMBEDMENT_HOOK
        } else {
            boltDiameter * MIN_EMBEDMENT_HEADED
        }

        // ==================== 4.9 التحقق النهائي ====================
        val isSafe = interactionRatio <= 1.0 && requiredTension <= tensionCapacity

        return AnchorBoltResult(
            numberOfBolts = numBolts,
            boltDiameter = boltDiameter,
            boltGrade = boltGrade.getGradeName(),
            boltSpacing = boltSpacing,
            edgeDistance = edgeDistance,
            tensionCapacity = tensionCapacity,
            requiredTension = requiredTension,
            shearCapacity = shearCapacity,
            requiredShear = requiredShear,
            embedmentLength = embedmentLength,
            boltArea = Ab,
            isSafe = isSafe
        )
    }

    // ============================================================================
    // =========== 5. تصميم المونة (Grout Design) =================================
    // ============================================================================
    // المرجع: AISC Design Guide 1 / ACI 318 / ECP 205
    // ============================================================================

    /**
     * تصميم طبقة المونة تحت القاعدة المعدنية
     * Design of grout layer under the base plate
     *
     * @param fpc مقاومة ضغط الخرسانة (MPa)
     * @param groutStrength مقاومة المونة (MPa) - إذا صفر = تساوي الخرسانة
     * @param thickness سماكة المونة (mm)
     * @return نتيجة تصميم المونة
     */
    private fun designGrout(
        fpc: Double,
        groutStrength: Double,
        thickness: Double
    ): GroutResult {
        // مقاومة المونة يجب أن تكون ≥ مقاومة الخرسانة
        // ACI 351.3R / AISC Design Guide 1
        val requiredStrength = fpc
        val providedStrength = if (groutStrength > 0) groutStrength else fpc
        val isAdequate = providedStrength >= requiredStrength

        // التأكد من أن السماكة ضمن الحدود المسموحة
        val finalThickness = thickness.coerceIn(MIN_GROUT_THICKNESS, MAX_GROUT_THICKNESS)

        return GroutResult(
            thickness = finalThickness,
            requiredStrength = requiredStrength,
            providedStrength = providedStrength,
            isAdequate = isAdequate
        )
    }

    // ============================================================================
    // =========== 6. دوال مساعدة (Utility Functions) ============================
    // ============================================================================

    /**
     * اختيار أقرب سماكة قياسية أعلى من أو تساوي السماكة المحسوبة
     * Select the nearest standard plate thickness ≥ required thickness
     *
     * @param required السماكة المحسوبة (mm)
     * @return السماكة القياسية (mm)
     */
    private fun selectStandardThickness(required: Double): Double {
        return STANDARD_PLATE_THICKNESSES.firstOrNull { it >= required }
            ?: STANDARD_PLATE_THICKNESSES.last()
    }

    /**
     * اختيار أقرب قطر قياسي للبرغي أعلى من أو يساوي القطر المطلوب
     * Select the nearest standard bolt diameter ≥ required diameter
     *
     * @param required القطر المطلوب (mm)
     * @return القطر القياسي (mm)
     */
    fun selectStandardBoltDiameter(required: Double): Double {
        return STANDARD_BOLT_DIAMETERS.firstOrNull { it >= required }
            ?: STANDARD_BOLT_DIAMETERS.last()
    }

    /**
     * حساب مساحة البرغي الاسمية
     * Calculate nominal bolt area
     *
     * @param diameter قطر البرغي (mm)
     * @return المساحة (mm²)
     */
    fun calculateBoltArea(diameter: Double): Double {
        return PI * diameter.pow(2) / 4.0
    }

    /**
     * حساب سعة الشد لبرغي واحد
     * Calculate tensile capacity of a single bolt
     *
     * المرجع: AISC 360-16 Table J3.2
     *
     * @param diameter قطر البرغي (mm)
     * @param fu إجهاد الشد الأقصى (MPa)
     * @return سعة الشد (kN)
     */
    fun calculateBoltTensionCapacity(diameter: Double, fu: Double): Double {
        val Ab = calculateBoltArea(diameter)
        return PHI_TENSION_BOLT * fu * Ab / 1000.0 // kN
    }

    /**
     * حساب سعة القص لبرغي واحد
     * Calculate shear capacity of a single bolt
     *
     * المرجع: AISC 360-16 Table J3.2
     *
     * @param diameter قطر البرغي (mm)
     * @param fu إجهاد الشد الأقصى (MPa)
     * @return سعة القص (kN)
     */
    fun calculateBoltShearCapacity(diameter: Double, fu: Double): Double {
        val Ab = calculateBoltArea(diameter)
        return PHI_TENSION_BOLT * 0.4 * fu * Ab / 1000.0 // kN
    }

    /**
     * فحص التفاعل بين الشد والقص للبراغي
     * Check tension-shear interaction for anchor bolts
     *
     * المرجع: AISC 360-16 Eq. J3-3a
     * (Tu/φTn) + (Vu/φVn) ≤ 1.0
     *
     * @param Tu الشد المؤثر (kN)
     * @param Vu القص المؤثر (kN)
     * @param phiTn سعة الشد المعدلة (kN)
     * @param phiVn سعة القص المعدلة (kN)
     * @return معامل التفاعل (≤ 1.0 آمن)
     */
    fun checkBoltInteraction(
        Tu: Double,
        Vu: Double,
        phiTn: Double,
        phiVn: Double
    ): Double {
        if (phiTn <= 0 || phiVn <= 0) return Double.MAX_VALUE
        return (Tu / phiTn) + (Vu / phiVn)
    }

    /**
     * حساب ضغط التحمل الأقصى على الخرسانة (مع العزوم)
     * Calculate maximum bearing pressure on concrete (with moments)
     *
     * f_max = P/(B×L) × (1 + 6e/B)  - للعزم في اتجاه واحد
     *
     * @param Pu القوة المحورية (kN)
     * @param Mu العزم (kN.m)
     * @param B عرض القاعدة (mm)
     * @param L طول القاعدة (mm)
     * @param isMomentAlongB هل العزم في اتجاه B؟
     * @return أقصى ضغط تحمل (MPa)
     */
    fun calculateMaxBearingPressure(
        Pu: Double,
        Mu: Double,
        B: Double,
        L: Double,
        isMomentAlongB: Boolean = true
    ): Double {
        val Pu_N = Pu * 1000.0 // kN → N
        val Mu_Nmm = Mu * 1e6  // kN.m → N.mm
        val dimension = if (isMomentAlongB) B else L
        val e = if (Pu > 0.01) Mu_Nmm / Pu_N else 0.0
        val f_uniform = Pu_N / (B * L)

        return if (dimension > 0 && e < dimension / 2.0) {
            f_uniform * (1.0 + 6.0 * e / dimension)
        } else {
            Double.MAX_VALUE // فقدان التلامس - يحتاج تصميم مختلف
        }
    }

    /**
     * التحقق من سعة ضغط الخرسانة
     * Check concrete bearing capacity
     *
     * المرجع: AISC 360-16 §J8
     * φPp = φ × 0.85 × fc' × A1 × √(A2/A1) ≤ φ × 1.7 × fc' × A1
     *
     * @param Pu القوة المحورية (kN)
     * @param fpc مقاومة الخرسانة (MPa)
     * @param A1 مساحة اللوح (mm²)
     * @param A2 مساحة الخرسانة المحيطة (mm²) - إذا صفر = A1
     * @return زوج (سعة التحمل بـ kN، معامل الاستغلال)
     */
    fun checkConcreteBearing(
        Pu: Double,
        fpc: Double,
        A1: Double,
        A2: Double = 0.0
    ): Pair<Double, Double> {
        // معامل التركيز √(A2/A1) ≤ 2
        val concentrationFactor = if (A2 > A1) {
            min(sqrt(A2 / A1), 2.0)
        } else {
            1.0
        }

        // سعة التحمل: φ × 0.85 × fc' × A1 × √(A2/A1)
        val capacity = PHI_BEARING_CONCRETE * 0.85 * fpc * A1 * concentrationFactor / 1000.0 // kN

        // الحد الأقصى: φ × 1.7 × fc' × A1
        val maxCapacity = PHI_BEARING_CONCRETE * 1.7 * fpc * A1 / 1000.0 // kN

        val finalCapacity = min(capacity, maxCapacity)
        val utilizationRatio = if (finalCapacity > 0) Pu / finalCapacity else Double.MAX_VALUE

        return Pair(finalCapacity, utilizationRatio)
    }

    /**
     * توليد تقرير نصي شامل لنتائج التصميم
     * Generate a comprehensive text report for design results
     *
     * @param result نتيجة تصميم القاعدة
     * @return التقرير النصي
     */
    fun generateDesignReport(result: BasePlateResult): String {
        val sb = StringBuilder()

        sb.appendLine("=" .repeat(60))
        sb.appendLine("        تقرير تصميم القاعدة المعدنية")
        sb.appendLine("        Steel Base Plate Design Report")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        sb.appendLine("─── أبعاد اللوح (Plate Dimensions) ───")
        sb.appendLine(String.format("  عرض اللوح B              = %.0f mm", result.plateWidth))
        sb.appendLine(String.format("  طول اللوح L              = %.0f mm", result.plateLength))
        sb.appendLine(String.format("  سماكة اللوح tp           = %.0f mm", result.plateThickness))
        sb.appendLine()

        sb.appendLine("─── ضغط التحمل (Bearing Pressure) ───")
        sb.appendLine(String.format("  أقصى ضغط تحمل           = %.2f MPa", result.maxBearingPressure))
        sb.appendLine(String.format("  سعة الخرسانة            = %.2f MPa", result.concreteCapacity))
        sb.appendLine(String.format("  معامل الاستغلال          = %.2f", result.utilizationRatio))
        sb.appendLine()

        sb.appendLine("─── البراغي الارتكازية (Anchor Bolts) ───")
        val bolt = result.anchorBolts
        sb.appendLine(String.format("  عدد البراغي              = %d", bolt.numberOfBolts))
        sb.appendLine(String.format("  قطر البرغي               = %.0f mm (%s)", bolt.boltDiameter, bolt.boltGrade))
        sb.appendLine(String.format("  سعة الشد لكل برغي        = %.1f kN", bolt.tensionCapacity))
        sb.appendLine(String.format("  الشد المطلوب لكل برغي    = %.1f kN", bolt.requiredTension))
        sb.appendLine(String.format("  سعة القص لكل برغي        = %.1f kN", bolt.shearCapacity))
        sb.appendLine(String.format("  القص المطلوب لكل برغي    = %.1f kN", bolt.requiredShear))
        sb.appendLine(String.format("  طول التثبيت              = %.0f mm", bolt.embedmentLength))
        sb.appendLine(String.format("  المسافة من الحافة         = %.0f mm", bolt.edgeDistance))
        sb.appendLine()

        sb.appendLine("─── المونة (Grout) ───")
        sb.appendLine(String.format("  سماكة المونة              = %.0f mm", result.grout.thickness))
        sb.appendLine(String.format("  مقاومة المونة المطلوبة    = %.0f MPa", result.grout.requiredStrength))
        sb.appendLine(String.format("  مقاومة المونة المتاحة     = %.0f MPa", result.grout.providedStrength))
        sb.appendLine()

        sb.appendLine("─── النتيجة النهائية (Final Result) ───")
        sb.appendLine(String.format("  الحالة: %s", if (result.isSafe) "آمن ✓" else "غير آمن ✗"))
        sb.appendLine(String.format("  معامل الاستغلال الكلي     = %.2f", result.utilizationRatio))
        sb.appendLine()

        // التحذيرات
        if (result.warnings.isNotEmpty()) {
            sb.appendLine("─── تحذيرات (Warnings) ───")
            result.warnings.forEachIndexed { index, warning ->
                sb.appendLine("  ${index + 1}. $warning")
            }
            sb.appendLine()
        }

        // ملاحظات الكود
        if (result.codeNotes.isNotEmpty()) {
            sb.appendLine("─── ملاحظات الكود (Code Notes) ───")
            result.codeNotes.forEachIndexed { index, note ->
                sb.appendLine("  ${index + 1}. $note")
            }
        }

        sb.appendLine()
        sb.appendLine("=".repeat(60))
        sb.appendLine("  ECP 205 / AISC 360-16 Chapter 14")
        sb.appendLine("=".repeat(60))

        return sb.toString()
    }

    /**
     * توليد تقرير نصي شامل لنتائج تصميم القاعدة غير المركزية
     * Generate a comprehensive text report for eccentric base plate results
     *
     * @param result نتيجة التصميم
     * @return التقرير النصي
     */
    fun generateEccentricDesignReport(result: EccentricBasePlateResult): String {
        val sb = StringBuilder()

        sb.appendLine("=" .repeat(60))
        sb.appendLine("    تقرير تصميم القاعدة المعدنية غير المركزية")
        sb.appendLine("    Eccentric Steel Base Plate Design Report")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        sb.appendLine("─── أبعاد اللوح (Plate Dimensions) ───")
        sb.appendLine(String.format("  عرض اللوح B              = %.0f mm", result.plateWidth))
        sb.appendLine(String.format("  طول اللوح L              = %.0f mm", result.plateLength))
        sb.appendLine(String.format("  سماكة اللوح tp           = %.0f mm", result.plateThickness))
        sb.appendLine()

        sb.appendLine("─── ضغط التحمل (Bearing Pressure) ───")
        sb.appendLine(String.format("  أقصى ضغط تحمل           = %.2f MPa", result.maxBearingPressure))
        sb.appendLine(String.format("  أقل ضغط تحمل            = %.2f MPa", result.minBearingPressure))
        sb.appendLine(String.format("  الطول الفعال للتحمل      = %.0f mm", result.effectiveBearingLength))
        sb.appendLine(String.format("  يوجد شد في أحد الجوانب  = %s", if (result.tensionSide) "نعم" else "لا"))
        sb.appendLine()

        sb.appendLine("─── البراغي الارتكازية (Anchor Bolts) ───")
        val bolt = result.anchorBolts
        sb.appendLine(String.format("  عدد البراغي              = %d", bolt.numberOfBolts))
        sb.appendLine(String.format("  قطر البرغي               = %.0f mm (%s)", bolt.boltDiameter, bolt.boltGrade))
        sb.appendLine(String.format("  سعة الشد لكل برغي        = %.1f kN", bolt.tensionCapacity))
        sb.appendLine(String.format("  الشد المطلوب لكل برغي    = %.1f kN", bolt.requiredTension))
        sb.appendLine(String.format("  طول التثبيت              = %.0f mm", bolt.embedmentLength))
        sb.appendLine()

        sb.appendLine("─── النتيجة النهائية (Final Result) ───")
        sb.appendLine(String.format("  الحالة: %s", if (result.isSafe) "آمن ✓" else "غير آمن ✗"))
        sb.appendLine(String.format("  معامل الاستغلال الكلي     = %.2f", result.utilizationRatio))
        sb.appendLine()

        if (result.warnings.isNotEmpty()) {
            sb.appendLine("─── تحذيرات (Warnings) ───")
            result.warnings.forEachIndexed { index, warning ->
                sb.appendLine("  ${index + 1}. $warning")
            }
            sb.appendLine()
        }

        if (result.codeNotes.isNotEmpty()) {
            sb.appendLine("─── ملاحظات الكود (Code Notes) ───")
            result.codeNotes.forEachIndexed { index, note ->
                sb.appendLine("  ${index + 1}. $note")
            }
        }

        sb.appendLine()
        sb.appendLine("=".repeat(60))

        return sb.toString()
    }

    /**
     * توليد تقرير نصي لنتائج تصميم قاعدة الجيب
     * Generate a text report for pocket base plate results
     *
     * @param result نتيجة التصميم
     * @return التقرير النصي
     */
    fun generatePocketDesignReport(result: PocketBasePlateResult): String {
        val sb = StringBuilder()

        sb.appendLine("=" .repeat(60))
        sb.appendLine("      تقرير تصميم قاعدة الجيب المعدنية")
        sb.appendLine("      Pocket Steel Base Plate Design Report")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        sb.appendLine("─── أبعاد اللوح (Plate Dimensions) ───")
        sb.appendLine(String.format("  عرض اللوح B              = %.0f mm", result.plateWidth))
        sb.appendLine(String.format("  طول اللوح L              = %.0f mm", result.plateLength))
        sb.appendLine(String.format("  سماكة اللوح tp           = %.0f mm", result.plateThickness))
        sb.appendLine()

        sb.appendLine("─── أبعاد الجيب (Pocket Dimensions) ───")
        sb.appendLine(String.format("  عمق الجيب                = %.0f mm", result.pocket.pocketDepth))
        sb.appendLine(String.format("  عرض الجيب                = %.0f mm", result.pocket.pocketWidth))
        sb.appendLine(String.format("  طول الجيب                = %.0f mm", result.pocket.pocketLength))
        sb.appendLine()

        sb.appendLine("─── البراغي (Anchor Bolts) ───")
        val bolt = result.anchorBolts
        sb.appendLine(String.format("  عدد البراغي              = %d", bolt.numberOfBolts))
        sb.appendLine(String.format("  قطر البرغي               = %.0f mm (%s)", bolt.boltDiameter, bolt.boltGrade))
        sb.appendLine(String.format("  سعة الشد لكل برغي        = %.1f kN", bolt.tensionCapacity))
        sb.appendLine(String.format("  الشد المطلوب لكل برغي    = %.1f kN", bolt.requiredTension))
        sb.appendLine()

        sb.appendLine("─── النتيجة النهائية (Final Result) ───")
        sb.appendLine(String.format("  الحالة: %s", if (result.isSafe) "آمن ✓" else "غير آمن ✗"))
        sb.appendLine(String.format("  معامل الاستغلال الكلي     = %.2f", result.utilizationRatio))
        sb.appendLine()
        sb.appendLine("=".repeat(60))

        return sb.toString()
    }
}