package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.BoltConnectionType
import com.civileg.app.domain.entities.BoltGrade
import com.civileg.app.domain.entities.BoltPattern
import com.civileg.app.domain.entities.ConnectionType
import com.civileg.app.domain.entities.ElectrodeType
import com.civileg.app.domain.entities.SteelGrade
import com.civileg.app.domain.entities.WeldType
import kotlin.math.*

/**
 * ============================
 * تصميم الوصلات المعدنية (Steel Connection Design)
 * ============================
 *
 * فحص الوصلات المساميرية والملحومة حسب:
 * - الكود المصري ECP 205 (الفصل الخامس والسادس)
 * - الكود الأمريكي AISC 360 (الفصل J2 للوصلة الملحومة، الفصل J3 للوصلة المساميرية، الفصل J4 للقص الكتلي)
 *
 * يغطي الملف:
 * - حساب مقاومة القص للمسمار (Bolt Shear Capacity)
 * - حساب مقاومة التحمل (Bearing Capacity)
 * - حساب مقاومة الشد للمسمار (Bolt Tension Capacity)
 * - فحص القص المشترك مع الشد (Combined Shear-Tension)
 * - فحص الوصلات الانزلاقية (Slip-Critical Connections)
 * - فحص القص الكتلي (Block Shear)
 * - حساب مقاومة اللحام التفريغي (Fillet Weld Capacity)
 * - المسافات الدنيا والعظمى (Minimum/Maximum Edge Distance & Spacing)
 * - أحجام اللحام الدنيا والعظمى (Min/Max Fillet Weld Size)
 */

// ===================== كيانات النتائج =====================

/**
 * نتيجة تصميم وصلة مساميرية
 * تحتوي على كل المعطيات الحسابية والمخرجات لفحص الوصلة
 */
data class BoltDesignResult(
    val boltDiameter: Double,         // قطر المسمار (مم)
    val boltGrade: BoltGrade,         // درجة المسمار
    val numberOfBolts: Int,           // عدد المسامير
    val boltPattern: BoltPattern,     // نمط توزيع المسامير
    val shearCapacity: Double,        // مقاومة القص لكل مسمار (كيلونيوتن)
    val bearingCapacity: Double,      // مقاومة التحمل لكل مسمار (كيلونيوتن)
    val tensionCapacity: Double,      // مقاومة الشد لكل مسمار (كيلونيوتن)
    val controllingCapacity: Double,  // المقاومة الحاكمة (كيلونيوتن) - أقل مقاومة
    val isSafe: Boolean,              // هل الوصلة آمنة؟
    val utilizationRatio: Double,     // نسبة الإجهاد المستخدم (Utilization Ratio)
    val edgeDistance: Double,         // المسافة من حافة القطاع لمركز المسمار (مم)
    val boltSpacing: Double,          // المسافة بين مراكز المسامير (مم)
    val gaugeDistance: Double,        // المسافة عرضية بين صفوف المسامير (مم)
    val blockShearCheck: BlockShearResult?, // نتيجة فحص القص الكتلي
    val warnings: List<String>,       // تحذيرات التصميم
    val codeNotes: List<String>       // ملاحظات الكود
)

/**
 * نتيجة تصميم وصلة ملحومة
 * تحتوي على كل المعطيات الحسابية والمخرجات لفحص اللحام
 */
data class WeldDesignResult(
    val weldType: WeldType,           // نوع اللحام (تفريغي / خيط / إسفنجي / خاني)
    val weldSize: Double,             // حجم اللحام (مم) - طول ضلع اللحام التفريغي
    val weldLength: Double,           // الطول الفعال لللحام (مم)
    val electrodeType: ElectrodeType, // نوع القطب الملحوم
    val capacity: Double,             // مقاومة اللحام (كيلونيوتن)
    val throatArea: Double,           // مساحة الحلق الفعالة (مم²)
    val isSafe: Boolean,              // هل اللحام آمن؟
    val utilizationRatio: Double,     // نسبة الإجهاد المستخدم
    val warnings: List<String>,       // تحذيرات التصميم
    val codeNotes: List<String>       // ملاحظات الكود
)

/**
 * نتيجة فحص القص الكتلي (Block Shear)
 * حسب AISC 360 القسم J4.3 و ECP 205
 */
data class BlockShearResult(
    val ruptureStrength: Double,      // مقاومة التمزق (كيلونيوتن)
    val yieldStrength: Double,        // مقاومة الخضوع (كيلونيوتن)
    val capacity: Double,             // المقاومة التصميمية (كيلونيوتن)
    val isSafe: Boolean,              // هل يحقق متطلبات الأمان؟
    val utilizationRatio: Double      // نسبة الإجهاد المستخدم
)

/**
 * ============================
 * الفئة الرئيسية لتصميم الوصلات المعدنية
 * ============================
 */
class SteelConnectionDesign {

    companion object {
        // ===================== معاملات المقاومة (Resistance Factors) =====================

        // معامل المقاومة للوصلة المساميرية (تحمل / قص / شد) - AISC 360-J3 و ECP 205-5
        private const val PHI_BEARING_CONNECTION = 0.75
        // معامل المقاومة للوصلة الانزلاقية عند أحمال الخدمة - AISC 360-J3.8
        private const val PHI_SLIP_CRITICAL = 1.0
        // معامل المقاومة للقص الكتلي - AISC 360-J4
        private const val PHI_BLOCK_SHEAR = 0.75
        // معامل المقاومة لللحام التفريغي - AISC 360-J2 و ECP 205-6
        private const val PHI_WELD = 0.75
        // معامل ثبات الاحتكاك للوصلة الانزلاقية - AISC 360-J3.8
        // (معامل الاحتكاك μ مضروب في معامل الثبات Du)
        private const val DU_SLIP_FACTOR = 1.13  // Du = 1.13 for standard holes

        // ===================== جدول معاملات الاحتكاك للوصلات الانزلاقية =====================
        // حسب AISC 360 الجدول J3.8 - معامل الاحتكاك (μ)
        private val FRICTION_COEFFICIENTS = mapOf(
            "CLASS_A" to 0.35,   // سطح فولاذي نظيف (طريقة الشد بالزيت)
            "CLASS_B" to 0.50,   // سطح فولاذي منزوع الصدأ (طريقة الشد بالرمل)
            "CLASS_C" to 0.40    // سطح فولاذي مُعالج بالجلفنة
        )

        // ===================== جدول أقطار المسامير القياسية (مم) =====================
        // المساحات الاسمية والمساحات المقطوعة المسمارية
        private val BOLT_DATA = mapOf(
            // المفتاح: قطر المسمار (مم)، القيمة: مصفوفة [المساحة الاسمية (مم²)، المساحة المقطوعة (مم²)]
            12.0 to arrayOf(113.1, 84.3),
            14.0 to arrayOf(153.9, 115.0),
            16.0 to arrayOf(201.1, 157.0),
            18.0 to arrayOf(254.5, 192.0),
            20.0 to arrayOf(314.2, 245.0),
            22.0 to arrayOf(380.1, 303.0),
            24.0 to arrayOf(452.4, 353.0),
            27.0 to arrayOf(572.6, 459.0),
            30.0 to arrayOf(706.9, 561.0),
            33.0 to arrayOf(855.3, 694.0),
            36.0 to arrayOf(1017.9, 817.0)
        )

        // ===================== جدول المسافات الدنيا من الحافة (مم) =====================
        // حسب AISC 360 الجدول J3.4 و ECP 205 جدول (5.1)
        // القيمة: المسافة الدنيا من حافة القطاع لمركز المسمار
        private val MIN_EDGE_DISTANCE = mapOf(
            12.0 to 22.0,
            14.0 to 25.0,
            16.0 to 28.0,
            18.0 to 31.0,
            20.0 to 34.0,
            22.0 to 38.0,
            24.0 to 41.0,
            27.0 to 44.0,
            30.0 to 47.0,
            33.0 to 51.0,
            36.0 to 54.0
        )

        // ===================== جدول أحجام اللحام التفريغي الدنيا (مم) =====================
        // حسب AISC 360 الجدول J2.4 و ECP 205 جدول (6.2)
        // المفتاح: أقصى سماكة للجزء الأرفع (مم)، القيمة: الحجم الأدنى لللحام التفريغي (مم)
        // الطول الفعال للقيم في النطاقات: الحد الأدنى ≤ السماكة < الحد الأعلى
        private val MIN_FILLET_WELD_SIZE = arrayOf(
            // زوج: [الحد الأعلى للسماكة (مم)، الحجم الأدنى لللحام (مم)]
            doubleArrayOf(6.0, 3.0),
            doubleArrayOf(13.0, 5.0),
            doubleArrayOf(19.0, 6.0),
            doubleArrayOf(38.0, 8.0),
            doubleArrayOf(57.0, 10.0),
            doubleArrayOf(Double.MAX_VALUE, 13.0)
        )

        // ===================== معامل إجهاد القص الاسمي للمسمار (MPa) =====================
        // حسب AISC 360-J3.6 و ECP 205-5.3
        // Fn = إجهاد القص الاسمي - يعتمد على درجة المسمار وحالة السطح
        // للحساب: φ * Fn * Ab (قص بسيط) أو φ * Fn * Ab * 2 (قص مزدوج)
        private fun getBoltShearStress(boltGrade: BoltGrade): Double {
            // إجهاد القص الاسمي Fn - يكون تقريباً 0.4 * Fu للمسمار
            // أو يمكن أخذه من جداول AISC Table J3.6
            return when (boltGrade) {
                BoltGrade.GRADE_4_6 -> boltGrade.fu * 0.40    // Fn ≈ 160 MPa
                BoltGrade.GRADE_8_8 -> boltGrade.fu * 0.40    // Fn ≈ 320 MPa
                BoltGrade.GRADE_10_9 -> boltGrade.fu * 0.35   // Fn ≈ 350 MPa
                BoltGrade.A325 -> 372.0                         // AISC Table J3.6 (السطح المسماري)
                BoltGrade.A490 -> 457.0                         // AISC Table J3.6 (السطح المسماري)
            }
        }

        // ===================== إجهاد الشد الاسمي للمسمار (MPa) =====================
        // حسب AISC 360-J3.5 و ECP 205-5.4
        // Ft = إجهاد الشد الاسمي
        private fun getBoltTensionStress(boltGrade: BoltGrade): Double {
            return when (boltGrade) {
                BoltGrade.GRADE_4_6 -> boltGrade.fu * 0.60    // Ft ≈ 240 MPa
                BoltGrade.GRADE_8_8 -> boltGrade.fu * 0.50    // Ft ≈ 400 MPa
                BoltGrade.GRADE_10_9 -> boltGrade.fu * 0.50   // Ft ≈ 500 MPa
                BoltGrade.A325 -> 620.0                         // AISC Table J3.5
                BoltGrade.A490 -> 780.0                         // AISC Table J3.5
            }
        }

        // ===================== معامل قوة الشد المسبق للمسمار (kN) =====================
        // حسب AISC 360 الجدول J3.1 - قوة الشد المسبق Tb
        private fun getBoltPretension(boltGrade: BoltGrade, boltDiameter: Double): Double {
            // Tb = 0.70 * Fu * Ab (لكل مسمار)
            val ab = getBoltThreadedArea(boltDiameter)
            val tb = 0.70 * boltGrade.fu * ab / 1000.0 // التحويل إلى كيلونيوتن
            return tb
        }
    }

    // ===================== وظائف مساعدة =====================

    /**
     * حساب المساحة الاسمية للمسمار (مم²)
     * المساحة الاسمية = π/4 × d²
     * [مرجع: AISC 360-J3.6 / ECP 205-5]
     *
     * @param diameter قطر المسمار (مم)
     * @return المساحة الاسمية للمسمار (مم²)
     */
    fun getBoltArea(diameter: Double): Double {
        // البحث في الجدول القياسي أولاً
        val standardEntry = BOLT_DATA.entries.minByOrNull {
            abs(it.key - diameter)
        }
        if (standardEntry != null && abs(standardEntry.key - diameter) < 0.1) {
            return standardEntry.value[0]
        }
        // حساب يدوي إذا لم يكن قياسياً
        return (PI / 4.0) * diameter * diameter
    }

    /**
     * حساب مساحة المقطع المسماري (المساحة الفعالة للمسمار المقطوع) (مم²)
     * تُستخدم عند وجود القص في منطقة الخيوط
     * [مرجع: AISC 360-J3.6 / ECP 205-5]
     *
     * @param diameter قطر المسمار (مم)
     * @return مساحة المقطع المسماري (مم²)
     */
    fun getBoltThreadedArea(diameter: Double): Double {
        // البحث في الجدول القياسي أولاً
        val standardEntry = BOLT_DATA.entries.minByOrNull {
            abs(it.key - diameter)
        }
        if (standardEntry != null && abs(standardEntry.key - diameter) < 0.1) {
            return standardEntry.value[1]
        }
        // تقريب: المساحة المقطوعة ≈ 0.75 × المساحة الاسمية
        return 0.75 * getBoltArea(diameter)
    }

    /**
     * حساب المسافة الدنيا من حافة القطاع لمركز المسمار (مم)
     * حسب AISC 360 الجدول J3.4 و ECP 205 جدول (5.1)
     * تعتمد على قطر المسمار
     *
     * @param boltDiameter قطر المسمار (مم)
     * @return المسافة الدنيا من الحافة (مم)
     */
    fun getMinEdgeDistance(boltDiameter: Double): Double {
        // البحث في الجدول القياسي - أقرب قطر
        val standardEntry = MIN_EDGE_DISTANCE.entries.minByOrNull {
            abs(it.key - boltDiameter)
        }
        if (standardEntry != null && abs(standardEntry.key - boltDiameter) < 0.1) {
            return standardEntry.value
        }
        // حساب تقريبي: 1.5 × قطر المسمار (لكن لا تقل عن 19 مم) - AISC J3.4
        return max(1.5 * boltDiameter, 19.0)
    }

    /**
     * حساب المسافة الدنيا بين مراكز المسامير (مم)
     * حسب AISC 360-J3.3 و ECP 205-5.2
     * المسافة الدنيا = 3 × قطر المسمار (من المركز للمركز)
     *
     * @param boltDiameter قطر المسمار (مم)
     * @return المسافة الدنيا بين المسامير (مم)
     */
    fun getMinBoltSpacing(boltDiameter: Double): Double {
        // 3 أضعاف قطر المسمار - AISC J3.3 و ECP 205-5.2
        return 3.0 * boltDiameter
    }

    /**
     * حساب الحجم الأدنى لللحام التفريغي (مم)
     * حسب AISC 360 الجدول J2.4 و ECP 205 جدول (6.2)
     * يعتمد على سماكة الجزء الأرفع من الوصلة
     *
     * @param plateThickness سماكة الجزء الأرفع (مم)
     * @return الحجم الأدنى لللحام التفريغي (مم)
     */
    fun getMinFilletWeldSize(plateThickness: Double): Double {
        // البحث في جدول الأحجام الدنيا حسب السماكة
        for (entry in MIN_FILLET_WELD_SIZE) {
            if (plateThickness <= entry[0]) {
                return entry[1]
            }
        }
        // إذا كانت السماكة أكبر من كل الحدود
        return MIN_FILLET_WELD_SIZE.last()[1]
    }

    /**
     * حساب الحجم الأقصى لللحام التفريغي (مم)
     * حسب AISC 360-J2.2b و ECP 205-6.2
     * - للقطاعات المعدنية: لا يتجاوز سماكة الجزء الأرفع
     * - للألواح: سماكة الجزء الأرفح ناقص 1.5 مم
     *
     * @param plateThickness سماكة الجزء الأرفق (الجزء الأرفع المراد لحامه) (مم)
     * @param isEdgeWeld هل اللحام على حافة لوح؟ (يُطبق الخصم 1.5 مم)
     * @return الحجم الأقصى لللحام التفريغي (مم)
     */
    fun getMaxFilletWeldSize(plateThickness: Double, isEdgeWeld: Boolean = false): Double {
        return if (isEdgeWeld) {
            // عند لحام حافة لوح: السماكة ناقص 1.5 مم - AISC J2.2b
            max(plateThickness - 1.5, 0.0)
        } else {
            // للحالات العادية: لا يتجاوز سماكة الجزء الأرفق
            plateThickness
        }
    }

    // ===================== حسابات الوصلات المساميرية =====================

    /**
     * حساب مقاومة القص لكل مسمار (كيلونيوتن)
     * حسب AISC 360-J3.6 و ECP 205-5.3
     *
     * الصيغة (LRFD):
     *   قص بسيط:  φRn = φ × Fn × Ab
     *   قص مزدوج: φRn = φ × Fn × Ab × 2
     *
     * حيث:
     *   φ = 0.75 (معامل المقاومة للوصلة المساميرية)
     *   Fn = إجهاد القص الاسمي (MPa)
     *   Ab = مساحة المسمار (مم²) - تُستخدم المساحة المقطوعة عند القص في منطقة الخيوط
     *
     * @param boltGrade درجة المسمار
     * @param boltDiameter قطر المسمار (مم)
     * @param isDoubleShear هل القص مزدوج؟
     * @param includeThreadedArea هل القص في منطقة الخيوط؟ (الافتراضي: نعم)
     * @return مقاومة القص لكل مسمار (كيلونيوتن)
     */
    fun calculateBoltShearCapacity(
        boltGrade: BoltGrade,
        boltDiameter: Double,
        isDoubleShear: Boolean = false,
        includeThreadedArea: Boolean = true
    ): Double {
        // اختيار المساحة المناسبة (مساحة الخيوط أو المساحة الاسمية)
        val ab = if (includeThreadedArea) {
            getBoltThreadedArea(boltDiameter)
        } else {
            getBoltArea(boltDiameter)
        }

        // إجهاد القص الاسمي Fn
        val fn = getBoltShearStress(boltGrade)

        // معامل القص (بسيط أو مزدوج)
        val shearMultiplier = if (isDoubleShear) 2.0 else 1.0

        // حساب مقاومة القص - AISC J3.6 / ECP 205-5.3
        // φRn = φ × Fn × Ab × (معامل القص)
        val rn = PHI_BEARING_CONNECTION * fn * ab * shearMultiplier

        // التحويل من N إلى kN
        return rn / 1000.0
    }

    /**
     * حساب مقاومة التحمل لكل مسمار (كيلونيوتن)
     * حسب AISC 360-J3.10 و ECP 205-5.5
     *
     * يتم أخذ الأقل من:
     *   1) φRn = φ × 1.2 × Lc × t × Fu  (عند حافة الثقب)
     *   2) φRn = φ × 2.4 × d × t × Fu   (عند المسافة بين المسامير)
     *
     * حيث:
     *   φ = 0.75
     *   Lc = المسافة الصافية بين حافة الثقب ومركز المسمار (مم)
     *   t = سماكة الجزء المتصل (مم)
     *   Fu = مقاومة الشد القصوى للجزء المتصل (MPa)
     *   d = قطر المسمار (مم)
     *
     * @param boltDiameter قطر المسمار (مم)
     * @param plateThickness سماكة الجزء المتصل (مم)
     * @param fuPlate مقاومة الشد القصوى للوحة المتصلة (MPa)
     * @param edgeDistance المسافة من الحافة لمركز المسمار (مم)
     * @param spacing المسافة بين مراكز المسامير (مم) - تُستخدم لحساب Lc
     * @param holeDiameter قطر الثقب (مم) - الافتراضي: قطر المسمار + 2 مم
     * @return مقاومة التحمل لكل مسمار (كيلونيوتن)
     */
    fun calculateBearingCapacity(
        boltDiameter: Double,
        plateThickness: Double,
        fuPlate: Double,
        edgeDistance: Double,
        spacing: Double,
        holeDiameter: Double = boltDiameter + 2.0
    ): Double {
        // حساب المسافة الصافية Lc عند الحافة
        // Lc = المسافة من الحافة لمركز المسمار - نصف قطر الثقب
        val lcEdge = edgeDistance - holeDiameter / 2.0

        // حساب المسافة الصافية Lc بين المسامير
        // Lc = المسافة بين المراكز - قطر الثقب
        val lcSpacing = spacing - holeDiameter

        // الحالة الأولى: عند حافة الثقب - AISC J3.10(a)
        // φRn = φ × 1.2 × Lc × t × Fu
        val rnEdge = PHI_BEARING_CONNECTION * 1.2 * lcEdge * plateThickness * fuPlate

        // الحالة الثانية: عند المسافة بين المسامير - AISC J3.10(b)
        // φRn = φ × 2.4 × d × t × Fu
        val rnSpacing = PHI_BEARING_CONNECTION * 2.4 * boltDiameter * plateThickness * fuPlate

        // الحالة الثالثة: استخدام Lc عند المسافة بين المسامير أيضاً
        val rnSpacingLc = PHI_BEARING_CONNECTION * 1.2 * lcSpacing * plateThickness * fuPlate

        // أخذ الأقل من الحالات الثلاث
        val controllingRn = minOf(rnEdge, rnSpacing, rnSpacingLc)

        // التحويل من N إلى kN
        return controllingRn / 1000.0
    }

    /**
     * حساب مقاومة الشد لكل مسمار (كيلونيوتن)
     * حسب AISC 360-J3.5 و ECP 205-5.4
     *
     * الصيغة (LRFD):
     *   φRn = φ × Ft × Ab
     *
     * حيث:
     *   φ = 0.75
     *   Ft = إجهاد الشد الاسمي للمسمار (MPa)
     *   Ab = مساحة المقطع المسماري (مم²)
     *
     * @param boltGrade درجة المسمار
     * @param boltDiameter قطر المسمار (مم)
     * @return مقاومة الشد لكل مسمار (كيلونيوتن)
     */
    fun calculateBoltTensionCapacity(
        boltGrade: BoltGrade,
        boltDiameter: Double
    ): Double {
        // مساحة المقطع المسماري (الخيوط)
        val ab = getBoltThreadedArea(boltDiameter)

        // إجهاد الشد الاسمي Ft
        val ft = getBoltTensionStress(boltGrade)

        // حساب مقاومة الشد - AISC J3.5 / ECP 205-5.4
        // φRn = φ × Ft × Ab
        val rn = PHI_BEARING_CONNECTION * ft * ab

        // التحويل من N إلى kN
        return rn / 1000.0
    }

    /**
     * فحص القص المشترك مع الشد (Combined Shear and Tension)
     * حسب AISC 360-J3.7 و ECP 205-5.6
     *
     * معادلة التفاعل:
     *   (Vu / φVn)² + (Tu / φTn)² ≤ 1.0
     *
     * حيث:
     *   Vu = القص المطبق
     *   φVn = مقاومة القص التصميمية
     *   Tu = الشد المطبق
     *   φTn = مقاومة الشد التصميمية
     *
     * @param appliedShear قوة القص المطبقة (كيلونيوتن)
     * @param appliedTension قوة الشد المطبقة (كيلونيوتن)
     * @param shearCapacity مقاومة القص التصميمية لكل مسمار (كيلونيوتن)
     * @param tensionCapacity مقاومة الشد التصميمية لكل مسمار (كيلونيوتن)
     * @return زوج: (هل يحقق الأمان؟، نسبة معادلة التفاعل)
     */
    fun checkCombinedShearTension(
        appliedShear: Double,
        appliedTension: Double,
        shearCapacity: Double,
        tensionCapacity: Double
    ): Pair<Boolean, Double> {
        // حساب معادلة التفاعل - AISC J3.7
        // (Vu / φVn)² + (Tu / φTn)² ≤ 1.0
        val ratioV = if (shearCapacity > 0) appliedShear / shearCapacity else 0.0
        val ratioT = if (tensionCapacity > 0) appliedTension / tensionCapacity else 0.0
        val interactionRatio = ratioV * ratioV + ratioT * ratioT

        return Pair(interactionRatio <= 1.0, interactionRatio)
    }

    /**
     * حساب مقاومة الوصلة الانزلاقية (Slip-Critical Connection)
     * حسب AISC 360-J3.8 و ECP 205-5.7
     *
     * الصيغة:
     *   φRn = φ × μ × Du × Ns × Tb
     *
     * حيث:
     *   φ = 1.0 (لأحمال الخدمة) أو 0.85 (لأحمال.factor)
     *   μ = معامل الاحتكاك (0.35 للسطح A، 0.50 للسطح B، 0.40 للسطح C)
     *   Du = 1.13 (معامل ثبات قوة الشد المسبق)
     *   Ns = عدد أسطح القص (1 للقص البسيط، 2 للقص المزدوج)
     *   Tb = قوة الشد المسبق لكل مسمار (kN)
     *
     * @param boltGrade درجة المسمار
     * @param boltDiameter قطر المسمار (مم)
     * @param frictionClass فئة سطح الاحتكاك ("CLASS_A" أو "CLASS_B" أو "CLASS_C")
     * @param numberOfShearPlanes عدد أسطح القص (1 أو 2)
     * @return مقاومة الوصلة الانزلاقية لكل مسمار (كيلونيوتن)
     */
    fun calculateSlipCriticalCapacity(
        boltGrade: BoltGrade,
        boltDiameter: Double,
        frictionClass: String = "CLASS_A",
        numberOfShearPlanes: Int = 1
    ): Double {
        // معامل الاحتكاك من الجدول - AISC Table J3.8
        val mu = FRICTION_COEFFICIENTS[frictionClass] ?: 0.35

        // قوة الشد المسبق Tb
        val tb = getBoltPretension(boltGrade, boltDiameter)

        // حساب مقاومة الوصلة الانزلاقية - AISC J3.8
        // φRn = φ × μ × Du × Ns × Tb
        val rn = PHI_SLIP_CRITICAL * mu * DU_SLIP_FACTOR * numberOfShearPlanes * tb

        return rn
    }

    /**
     * فحص القص الكتلي (Block Shear Strength)
     * حسب AISC 360-J4.3 و ECP 205-5.8
     *
     * الصيغة (LRFD):
     *   φRn = φ × [0.6 × Fu × Anv + Ubs × Fu × Ant]
     *   أو
     *   φRn = φ × [0.6 × Fy × Agv + Ubs × Fu × Ant]
     *
     * وتُؤخذ القيمة الأكبر
     *
     * حيث:
     *   φ = 0.75
     *   Fu = مقاومة الشد القصوى (MPa)
     *   Fy = إجهاد الخضوع (MPa)
     *   Anv = المساحة الصافية للقص (مم²)
     *   Ant = المساحة الصافية للشد (مم²)
     *   Agv = المساحة الإجمالية للقص (مم²)
     *   Ubs = معامل القص الكتلي (1.0 للتوتير الموحد، 0.5 للتوتير غير الموحد)
     *
     * @param shearAreaGross المساحة الإجمالية لمنطقة القص (مم²)
     * @param shearAreaNet المساحة الصافية لمنطقة القص (مم²) = المساحة الإجمالية - مساحة الثقوب
     * @param tensionAreaNet المساحة الصافية لمنطقة الشد (مم²)
     * @param fy إجهاد الخضوع للقطاع (MPa)
     * @param fu مقاومة الشد القصوى للقطاع (MPa)
     * @param ubs معامل القص الكتلي (الافتراضي: 1.0 للتوتير الموحد)
     * @return كائن BlockShearResult يحتوي على النتائج
     */
    fun checkBlockShear(
        shearAreaGross: Double,
        shearAreaNet: Double,
        tensionAreaNet: Double,
        fy: Double,
        fu: Double,
        ubs: Double = 1.0
    ): BlockShearResult {
        // الحالة الأولى: التمزق في القص والشد - AISC J4.3(a)
        // Rn = 0.6 × Fu × Anv + Ubs × Fu × Ant
        val ruptureStrength = 0.6 * fu * shearAreaNet + ubs * fu * tensionAreaNet

        // الحالة الثانية: الخضوع في القص والتمزق في الشد - AISC J4.3(b)
        // Rn = 0.6 × Fy × Agv + Ubs × Fu × Ant
        val yieldStrength = 0.6 * fy * shearAreaGross + ubs * fu * tensionAreaNet

        // المقاومة التصميمية = φ × max(Case1, Case2)
        val capacity = PHI_BLOCK_SHEAR * max(ruptureStrength, yieldStrength)

        // حساب نسبة الاستخدام (يحتاج القوة المطبقة من خارج الدالة)
        // هنا نُرجع المقاومة فقط - نسبة الاستخدام تُحسب عند الاستخدام
        return BlockShearResult(
            ruptureStrength = ruptureStrength / 1000.0,  // كيلونيوتن
            yieldStrength = yieldStrength / 1000.0,       // كيلونيوتن
            capacity = capacity / 1000.0,                 // كيلونيوتن
            isSafe = true,  // يُحدد لاحقاً عند مقارنة مع القوة المطبقة
            utilizationRatio = 0.0   // يُحدد لاحقاً
        )
    }

    // ===================== وظيفة التصميم الشاملة للوصلة المساميرية =====================

    /**
     * تصميم وصلة مساميرية شاملة
     * يجمع كل فحوصات الوصلة المساميرية في نتيجة واحدة
     *
     * [مرجع: ECP 205 الفصل الخامس / AISC 360 الفصل J3]
     *
     * @param boltDiameter قطر المسمار (مم)
     * @param boltGrade درجة المسمار
     * @param numberOfBolts عدد المسامير
     * @param boltPattern نمط توزيع المسامير
     * @param boltConnectionType نوع الوصلة (تحمل / انزلاقية / شد / مشترك)
     * @param appliedShear قوة القص المطبقة (كيلونيوتن) - الافتراضي: 0
     * @param appliedTension قوة الشد المطبقة (كيلونيوتن) - الافتراضي: 0
     * @param plateThickness سماكة الجزء المتصل (مم) - للتحقق من التحمل
     * @param fuPlate مقاومة الشد القصوى للوحة المتصلة (MPa)
     * @param isDoubleShear هل القص مزدوج؟ - الافتراضي: لا
     * @param edgeDistance المسافة من الحافة (مم) - الافتراضي: الحد الأدنى
     * @param spacing المسافة بين المسامير (مم) - الافتراضي: الحد الأدنى
     * @param gaugeDistance المسافة العرضية بين صفوف المسامير (مم) - الافتراضي: 0
     * @param frictionClass فئة الاحتكاك للوصلات الانزلاقية - الافتراضي: CLASS_A
     * @param checkBlockShear هل يتم فحص القص الكتلي؟ - الافتراضي: نعم
     * @param shearAreaGross المساحة الإجمالية للقص (مم²) - للقص الكتلي
     * @param shearAreaNet المساحة الصافية للقص (مم²) - للقص الكتلي
     * @param tensionAreaNet المساحة الصافية للشد (مم²) - للقص الكتلي
     * @param fyPlate إجهاد الخضوع للوحة (MPa) - للقص الكتلي
     * @return كائن BoltDesignResult يحتوي على كل النتائج
     */
    fun designBoltedConnection(
        boltDiameter: Double,
        boltGrade: BoltGrade,
        numberOfBolts: Int,
        boltPattern: BoltPattern,
        boltConnectionType: BoltConnectionType,
        appliedShear: Double = 0.0,
        appliedTension: Double = 0.0,
        plateThickness: Double = 10.0,
        fuPlate: Double = 400.0,
        isDoubleShear: Boolean = false,
        edgeDistance: Double? = null,
        spacing: Double? = null,
        gaugeDistance: Double = 0.0,
        frictionClass: String = "CLASS_A",
        checkBlockShear: Boolean = true,
        shearAreaGross: Double = 0.0,
        shearAreaNet: Double = 0.0,
        tensionAreaNet: Double = 0.0,
        fyPlate: Double = 250.0
    ): BoltDesignResult {
        // قائمة التحذيرات وملاحظات الكود
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // ===================== التحقق من المسافات =====================

        // حساب المسافات الدنيا
        val minEdgeDist = getMinEdgeDistance(boltDiameter)
        val minSpacing = getMinBoltSpacing(boltDiameter)

        // استخدام المسافات المُدخلة أو القيم الدنيا
        val finalEdgeDistance = edgeDistance ?: max(minEdgeDist, 1.5 * boltDiameter)
        val finalSpacing = spacing ?: minSpacing

        // التحقق من المسافة من الحافة - AISC J3.4 / ECP 205 جدول (5.1)
        if (finalEdgeDistance < minEdgeDist) {
            warnings.add("⚠️ المسافة من الحافة (${finalEdgeDistance.toInt()} مم) أقل من الحد الأدنى (${minEdgeDist.toInt()} مم) حسب AISC J3.4")
        }

        // التحقق من المسافة بين المسامير - AISC J3.3 / ECP 205-5.2
        if (finalSpacing < minSpacing) {
            warnings.add("⚠️ المسافة بين المسامير (${finalSpacing.toInt()} مم) أقل من الحد الأدنى (3×Ø = ${minSpacing.toInt()} مم) حسب AISC J3.3")
        }

        // المسافة العظمى بين المسامير = 24 × سمك أرفع جزء متصل أو 305 مم
        val maxSpacing = min(24.0 * plateThickness, 305.0)
        if (finalSpacing > maxSpacing) {
            warnings.add("⚠️ المسافة بين المسامير (${finalSpacing.toInt()} مم) تتجاوز الحد الأقصى (${maxSpacing.toInt()} مم) حسب AISC J3.5")
        }

        codeNotes.add("📌 AISC 360-J3.4: المسافة الدنيا من الحافة = ${minEdgeDist.toInt()} مم")
        codeNotes.add("📌 AISC 360-J3.3: المسافة الدنيا بين المسامير = 3×Ø = ${minSpacing.toInt()} مم")
        codeNotes.add("📌 AISC 360-J3.5: المسافة القصوى = min(24t, 305) = ${maxSpacing.toInt()} مم")

        // ===================== حساب مقاومة القص =====================

        // مقاومة القص لكل مسمار - AISC J3.6 / ECP 205-5.3
        var shearCapacity = calculateBoltShearCapacity(
            boltGrade = boltGrade,
            boltDiameter = boltDiameter,
            isDoubleShear = isDoubleShear
        )
        codeNotes.add("📌 AISC 360-J3.6: مقاومة القص = φ × Fn × Ab = 0.75 × ${getBoltShearStress(boltGrade).toInt()} × ${getBoltThreadedArea(boltDiameter).toInt()} = ${(shearCapacity * 1000).toInt()} N${if (isDoubleShear) " × 2 (قص مزدوج)" else ""}")

        // ===================== حساب مقاومة التحمل =====================

        // مقاومة التحمل لكل مسمار - AISC J3.10 / ECP 205-5.5
        val bearingCapacity = calculateBearingCapacity(
            boltDiameter = boltDiameter,
            plateThickness = plateThickness,
            fuPlate = fuPlate,
            edgeDistance = finalEdgeDistance,
            spacing = finalSpacing
        )
        codeNotes.add("📌 AISC 360-J3.10: مقاومة التحمل = φ × min(1.2×Lc×t×Fu, 2.4×d×t×Fu) = ${(bearingCapacity * 1000).toInt()} N")

        // ===================== حساب مقاومة الشد =====================

        // مقاومة الشد لكل مسمار - AISC J3.5 / ECP 205-5.4
        var tensionCapacity = calculateBoltTensionCapacity(
            boltGrade = boltGrade,
            boltDiameter = boltDiameter
        )
        codeNotes.add("📌 AISC 360-J3.5: مقاومة الشد = φ × Ft × Ab = 0.75 × ${getBoltTensionStress(boltGrade).toInt()} × ${getBoltThreadedArea(boltDiameter).toInt()} = ${(tensionCapacity * 1000).toInt()} N")

        // ===================== فحص حسب نوع الوصلة =====================

        var controllingCapacity = shearCapacity
        var isSafe = true
        var utilizationRatio = 0.0

        when (boltConnectionType) {
            BoltConnectionType.BEARING -> {
                // وصلة تحمل - المقاومة الحاكمة = أقل من (القص، التحمل)
                controllingCapacity = min(shearCapacity, bearingCapacity)
                if (appliedShear > 0) {
                    utilizationRatio = appliedShear / (controllingCapacity * numberOfBolts)
                    isSafe = utilizationRatio <= 1.0
                }
                codeNotes.add("📌 AISC 360-J3: وصلة تحمل - المقاومة الحاكمة = min(القص، التحمل) = ${controllingCapacity * 1000} N/مسمار")
            }

            BoltConnectionType.SLIP_CRITICAL -> {
                // وصلة انزلاقية - AISC J3.8 / ECP 205-5.7
                val slipCapacity = calculateSlipCriticalCapacity(
                    boltGrade = boltGrade,
                    boltDiameter = boltDiameter,
                    frictionClass = frictionClass,
                    numberOfShearPlanes = if (isDoubleShear) 2 else 1
                )
                controllingCapacity = slipCapacity
                if (appliedShear > 0) {
                    utilizationRatio = appliedShear / (slipCapacity * numberOfBolts)
                    isSafe = utilizationRatio <= 1.0
                }
                codeNotes.add("📌 AISC 360-J3.8: وصلة انزلاقية - مقاومة الانزلاق = φ × μ × Du × Ns × Tb = ${slipCapacity * 1000} N/مسمار")
            }

            BoltConnectionType.TENSION -> {
                // وصلة شد فقط
                controllingCapacity = tensionCapacity
                if (appliedTension > 0) {
                    utilizationRatio = appliedTension / (tensionCapacity * numberOfBolts)
                    isSafe = utilizationRatio <= 1.0
                }
                codeNotes.add("📌 AISC 360-J3.5: وصلة شد - المقاومة = ${tensionCapacity * 1000} N/مسمار")
            }

            BoltConnectionType.COMBINED -> {
                // وصلة مشتركة (قص + شد) - AISC J3.7 / ECP 205-5.6
                val (combinedSafe, interactionRatio) = checkCombinedShearTension(
                    appliedShear = appliedShear / numberOfBolts,
                    appliedTension = appliedTension / numberOfBolts,
                    shearCapacity = shearCapacity,
                    tensionCapacity = tensionCapacity
                )
                isSafe = combinedSafe
                utilizationRatio = interactionRatio
                controllingCapacity = min(shearCapacity, tensionCapacity, bearingCapacity)
                codeNotes.add("📌 AISC 360-J3.7: معادلة التفاعل = (Vu/φVn)² + (Tu/φTn)² = $interactionRatio ≤ 1.0")

                // تعديل مقاومة الشد عند وجود قص مشترك - AISC J3.7
                if (appliedShear > 0 && shearCapacity > 0) {
                    val shearRatio = (appliedShear / numberOfBolts) / shearCapacity
                    if (shearRatio > 0.0) {
                        // Ft' = Ft × (1 - fv/Fn) - تعديل مقاومة الشد
                        val fn = getBoltShearStress(boltGrade)
                        val fv = shearRatio * fn
                        val ftModified = getBoltTensionStress(boltGrade) * (1.0 - fv / fn)
                        tensionCapacity = PHI_BEARING_CONNECTION * ftModified * getBoltThreadedArea(boltDiameter) / 1000.0
                        codeNotes.add("📌 AISC 360-J3.7: مقاومة الشد المعدلة = Ft' = Ft × (1 - fv/Fn) = ${ftModified.toInt()} MPa")
                    }
                }
            }
        }

        // ===================== فحص القص الكتلي =====================

        var blockShearResult: BlockShearResult? = null
        if (checkBlockShear && shearAreaGross > 0) {
            blockShearResult = checkBlockShear(
                shearAreaGross = shearAreaGross,
                shearAreaNet = shearAreaNet,
                tensionAreaNet = tensionAreaNet,
                fy = fyPlate,
                fu = fuPlate,
                ubs = 1.0  // افتراض: توتير موحد
            )

            // حساب نسبة الاستخدام للقص الكتلي
            val totalApplied = if (appliedTension > appliedShear) appliedTension else appliedShear
            if (totalApplied > 0) {
                val blockShearUtilization = totalApplied / blockShearResult.capacity
                val bsResult = blockShearResult.copy(
                    isSafe = blockShearUtilization <= 1.0,
                    utilizationRatio = blockShearUtilization
                )
                blockShearResult = bsResult

                if (!bsResult.isSafe) {
                    warnings.add("⚠️ فحص القص الكتلي غير مُطابق - نسبة الاستخدام = ${"%.2f".format(blockShearUtilization)} > 1.0 (AISC J4.3)")
                }
            }

            codeNotes.add("📌 AISC 360-J4.3: مقاومة القص الكتلي = φ × [0.6×Fu×Anv + Ubs×Fu×Ant] = ${(blockShearResult.capacity * 1000).toInt()} N")
        }

        // ===================== التحقق من عدد المسامير =====================

        if (numberOfBolts < 1) {
            warnings.add("⚠️ عدد المسامير يجب أن يكون 1 على الأقل")
        }

        // التحقق من المسافة العرضية للأنماط متعددة الصفوف
        if (boltPattern == BoltPattern.DOUBLE_ROW || boltPattern == BoltPattern.GRID) {
            if (gaugeDistance < minSpacing) {
                warnings.add("⚠️ المسافة العرضية (${gaugeDistance.toInt()} مم) أقل من الحد الأدنى (${minSpacing.toInt()} مم)")
            }
        }

        // ===================== تحذيرات نهائية =====================

        if (!isSafe) {
            warnings.add("⚠️ الوصلة المساميرية غير آمنة - نسبة الاستخدام = ${"%.2f".format(utilizationRatio)} > 1.0")
        }

        if (utilizationRatio > 0.9 && utilizationRatio <= 1.0) {
            warnings.add("⚡ نسبة الاستخدام عالية (${"%.2f".format(utilizationRatio)}) - يُنصح بزيادة عدد المسامير أو قطرها")
        }

        return BoltDesignResult(
            boltDiameter = boltDiameter,
            boltGrade = boltGrade,
            numberOfBolts = numberOfBolts,
            boltPattern = boltPattern,
            shearCapacity = shearCapacity,
            bearingCapacity = bearingCapacity,
            tensionCapacity = tensionCapacity,
            controllingCapacity = controllingCapacity,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            edgeDistance = finalEdgeDistance,
            boltSpacing = finalSpacing,
            gaugeDistance = gaugeDistance,
            blockShearCheck = blockShearResult,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== حسابات الوصلات الملحومة =====================

    /**
     * حساب مقاومة اللحام التفريغي (كيلونيوتن)
     * حسب AISC 360-J2.4 و ECP 205-6.3
     *
     * الصيغة (LRFD):
     *   φRn = φ × Fw × Aw
     *
     * حيث:
     *   φ = 0.75 (معامل المقاومة لللحام)
     *   Fw = 0.60 × FEXX (إجهاد اللحام = 0.6 × مقاومة الشد للقطب)
     *   Aw = المساحة الفعالة = الحلق (0.707 × حجم اللحام) × الطول الفعال
     *
     * @param weldSize حجم اللحام التفريغي (مم) - طول الضلع
     * @param weldLength الطول الفعال لللحام (مم)
     * @param electrodeType نوع القطب الملحوم
     * @param isLongWeld هل اللحام طويل (> 300 مم)؟ - يُطبق معامل تخفيض
     * @return مقاومة اللحام (كيلونيوتن)
     */
    fun calculateFilletWeldCapacity(
        weldSize: Double,
        weldLength: Double,
        electrodeType: ElectrodeType,
        isLongWeld: Boolean = false
    ): Double {
        // حساب سماكة الحلق الفعالة - AISC J2.2a
        // throat = 0.707 × leg size (للحام بزاوية 90 درجة)
        val throat = 0.707 * weldSize

        // معامل تخفيض اللحام الطويل - AISC J2.2b
        // لللحامات الطويلة (> 300 مم) يُطبق معامل تخفيض
        val lengthReductionFactor = if (isLongWeld && weldLength > 300.0) {
            1.0 - 0.002 * (weldLength - 300.0) / weldLength
        } else {
            1.0
        }

        // الطول الفعال مع معامل التخفيض
        val effectiveLength = weldLength * lengthReductionFactor

        // إجهاد اللحام الفعال - AISC J2.4
        // Fw = 0.60 × FEXX
        val fw = 0.60 * electrodeType.tensileStrength

        // المساحة الفعالة لللحام
        val aw = throat * effectiveLength

        // حساب مقاومة اللحام - AISC J2.4 / ECP 205-6.3
        // φRn = φ × Fw × Aw
        val rn = PHI_WELD * fw * aw

        // التحويل من N إلى kN
        return rn / 1000.0
    }

    /**
     * حساب مساحة الحلق الفعالة لللحام (مم²)
     *
     * @param weldSize حجم اللحام (مم) - طول الضلع
     * @param weldLength الطول الفعال (مم)
     * @return مساحة الحلق الفعالة (مم²)
     */
    fun calculateThroatArea(
        weldSize: Double,
        weldLength: Double
    ): Double {
        // الحلق = 0.707 × حجم اللحام (للحام بزاوية 90°) - AISC J2.2a
        val throat = 0.707 * weldSize
        return throat * weldLength
    }

    /**
     * تصميم وصلة ملحومة شاملة
     * يجمع كل فحوصات اللحام في نتيجة واحدة
     *
     * [مرجع: AISC 360 الفصل J2 / ECP 205 الفصل السادس]
     *
     * @param weldType نوع اللحام
     * @param weldSize حجم اللحام (مم) - طول الضلع للتفريغي
     * @param weldLength الطول الكلي لللحام (مم)
     * @param electrodeType نوع القطب الملحوم
     * @param appliedForce القوة المطبقة (كيلونيوتن)
     * @param plateThickness سماكة الجزء الأرفق (مم) - للتحقق من الحجم الأدنى والأقصى
     * @param isEdgeWeld هل اللحام على حافة؟
     * @return كائن WeldDesignResult يحتوي على النتائج
     */
    fun designWeldedConnection(
        weldType: WeldType,
        weldSize: Double,
        weldLength: Double,
        electrodeType: ElectrodeType,
        appliedForce: Double = 0.0,
        plateThickness: Double = 10.0,
        isEdgeWeld: Boolean = false
    ): WeldDesignResult {
        // قائمة التحذيرات وملاحظات الكود
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        if (weldType != WeldType.FILLET) {
            warnings.add("⚠️ هذا الفحص مُصمم للحام التفريغي (Fillet Weld) فقط - لحامات أخرى تتطلب فحوصات مختلفة")
        }

        // ===================== التحقق من حجم اللحام =====================

        // الحجم الأدنى لللحام التفريغي - AISC Table J2.4 / ECP 205 جدول (6.2)
        val minWeldSize = getMinFilletWeldSize(plateThickness)
        if (weldSize < minWeldSize) {
            warnings.add("⚠️ حجم اللحام (${weldSize.toInt()} مم) أقل من الحد الأدنى (${minWeldSize.toInt()} مم) حسب AISC Table J2.4")
        }

        // الحجم الأقصى لللحام التفريغي - AISC J2.2b / ECP 205-6.2
        val maxWeldSize = getMaxFilletWeldSize(plateThickness, isEdgeWeld)
        if (weldSize > maxWeldSize) {
            warnings.add("⚠️ حجم اللحام (${weldSize.toInt()} مم) يتجاوز الحد الأقصى (${maxWeldSize.toInt()} مم) حسب AISC J2.2b")
        }

        codeNotes.add("📌 AISC 360-Table J2.4: الحجم الأدنى لللحام = ${minWeldSize.toInt()} مم (لسماكة ${plateThickness.toInt()} مم)")
        codeNotes.add("📌 AISC 360-J2.2b: الحجم الأقصى لللحام = ${maxWeldSize.toInt()} مم")

        // ===================== حساب مقاومة اللحام =====================

        // التحقق من اللحام الطويل
        val isLongWeld = weldLength > 300.0
        val lengthReductionFactor = if (isLongWeld) {
            1.0 - 0.002 * (weldLength - 300.0) / weldLength
        } else {
            1.0
        }

        if (isLongWeld) {
            codeNotes.add("📌 AISC 360-J2.2b: معامل تخفيض اللحام الطويل = ${"%.3f".format(lengthReductionFactor)}")
        }

        // حساب المقاومة - AISC J2.4 / ECP 205-6.3
        val capacity = calculateFilletWeldCapacity(
            weldSize = weldSize,
            weldLength = weldLength,
            electrodeType = electrodeType,
            isLongWeld = isLongWeld
        )

        // حساب مساحة الحلق الفعالة
        val throatArea = calculateThroatArea(weldSize, weldLength)

        // ===================== إجهاد اللحام =====================

        val fw = 0.60 * electrodeType.tensileStrength
        val throat = 0.707 * weldSize
        codeNotes.add("📌 AISC 360-J2.4: إجهاد اللحام Fw = 0.60 × FEXX = 0.60 × ${electrodeType.tensileStrength.toInt()} = ${fw.toInt()} MPa")
        codeNotes.add("📌 AISC 360-J2.2a: الحلق الفعال = 0.707 × ${weldSize.toInt()} = ${"%.2f".format(throat)} مم")
        codeNotes.add("📌 AISC 360-J2.4: مقاومة اللحام = φ × Fw × Aw = 0.75 × ${fw.toInt()} × ${throatArea.toInt()} = ${(capacity * 1000).toInt()} N")

        // ===================== فحص الأمان =====================

        var isSafe = true
        var utilizationRatio = 0.0

        if (appliedForce > 0) {
            utilizationRatio = appliedForce / capacity
            isSafe = utilizationRatio <= 1.0
        }

        if (!isSafe) {
            warnings.add("⚠️ اللحام غير آمن - نسبة الاستخدام = ${"%.2f".format(utilizationRatio)} > 1.0")
        }

        if (utilizationRatio > 0.9 && utilizationRatio <= 1.0) {
            warnings.add("⚡ نسبة الاستخدام عالية (${"%.2f".format(utilizationRatio)}) - يُنصح بزيادة حجم اللحام أو طوله")
        }

        // ===================== التحقق من الطول الأدنى =====================

        // الطول الأدنى لللحام التفريغي = 4 أضعاف حجم اللحام - AISC J2.2b
        val minWeldLength = 4.0 * weldSize
        if (weldLength < minWeldLength) {
            warnings.add("⚠️ طول اللحام (${weldLength.toInt()} مم) أقل من الحد الأدنى (4×w = ${minWeldLength.toInt()} مم) حسب AISC J2.2b")
        }

        return WeldDesignResult(
            weldType = weldType,
            weldSize = weldSize,
            weldLength = weldLength,
            electrodeType = electrodeType,
            capacity = capacity,
            throatArea = throatArea,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    // ===================== تحليل اللحامات المركبة (للوصلات اللامركزية) =====================

    /**
     * تحليل اللحامات المركبة تحت أحمال لامركزية
     * يستخدم التحليل الشعاعي (Vector Analysis) - AISC 360-J2.4
     *
     * عند وجود لحامات متعددة تحت حمل لامركزي:
     *   - يتم تقسيم الحمل إلى مركزي وعزم دوران
     *   - يُحسب الإجهاد الناتج عن كل مكون
     *   - يتم جمع الإجهادات شعاعياً
     *
     * @param appliedForce القوة المطبقة (كيلونيوتن)
     * @param eccentricity اللامركزية (مم) - المسافة بين خط عمل القوة ومركز اللحام
     * @param weldSize حجم اللحام التفريغي (مم)
     * @param weldLength الطول الفعال الكلي لللحام (مم)
     * @param electrodeType نوع القطب
     * @return زوج: (هل آمن؟، الإجهاد المركب الفعلي MPa)
     */
    fun analyzeEccentricWeld(
        appliedForce: Double,
        eccentricity: Double,
        weldSize: Double,
        weldLength: Double,
        electrodeType: ElectrodeType
    ): Pair<Boolean, Double> {
        // إجهاد اللحام المسموح - AISC J2.4
        val fw = PHI_WELD * 0.60 * electrodeType.tensileStrength

        // الحلق الفعال
        val throat = 0.707 * weldSize

        // المساحة الفعالة
        val effectiveArea = throat * weldLength

        // عزم القصور لللحام حول المحور المطلوب
        // لوحظ أن اللحام يعامل كخط (لحام تفريغي)
        val momentOfInertia = weldLength * weldLength * weldLength / 12.0 // I = L³/12

        // تحويل القوة إلى نيوتن
        val forceN = appliedForce * 1000.0

        // الإجهاد المباشر (قص مباشر)
        val directStress = forceN / effectiveArea

        // العزم الدوراني
        val moment = forceN * eccentricity

        // الإجهاد الناتج عن العزم (أقصى إجهاد عند الأطراف)
        // σ = M × c / I حيث c = L/2
        val bendingStress = moment * (weldLength / 2.0) / (throat * momentOfInertia)

        // الإجهاد المركب (جمع شعاعي)
        // هنا نبسط: الإجهاد الكلي ≈ الجذر التربيعي لمجموع المربعات
        val combinedStress = sqrt(directStress * directStress + bendingStress * bendingStress)

        // مقارنة مع الإجهاد المسموح
        return Pair(combinedStress <= fw, combinedStress)
    }

    // ===================== حساب العدد المطلوب من المسامير =====================

    /**
     * حساب الحد الأدنى لعدد المسامير المطلوب
     *
     * @param appliedForce القوة المطبقة (كيلونيوتن)
     * @param capacityPerBolt مقاومة المسمار الواحد (كيلونيوتن)
     * @return العدد الأدنى للمسامير (مُقرب لأعلى)
     */
    fun calculateRequiredBolts(appliedForce: Double, capacityPerBolt: Double): Int {
        if (capacityPerBolt <= 0) return Int.MAX_VALUE
        return ceil(appliedForce / capacityPerBolt).toInt()
    }

    // ===================== حساب الطول المطلوب لللحام =====================

    /**
     * حساب الطول الأدنى لللحام التفريغي المطلوب
     *
     * @param appliedForce القوة المطبقة (كيلونيوتن)
     * @param weldSize حجم اللحام (مم)
     * @param electrodeType نوع القطب
     * @return الطول الأدنى لللحام (مم)
     */
    fun calculateRequiredWeldLength(
        appliedForce: Double,
        weldSize: Double,
        electrodeType: ElectrodeType
    ): Double {
        val fw = 0.60 * electrodeType.tensileStrength
        val throat = 0.707 * weldSize

        // φRn = φ × Fw × throat × L
        // L = φRn / (φ × Fw × throat)
        val rn = appliedForce * 1000.0  // التحويل إلى نيوتن
        val requiredLength = rn / (PHI_WELD * fw * throat)

        // يجب ألا يقل الطول عن 4 أضعاف حجم اللحام - AISC J2.2b
        return max(requiredLength, 4.0 * weldSize)
    }

    // ===================== فحص شامل للوصلة (مسامير أو لحام) =====================

    /**
     * فحص شامل لنوع الوصلة (مساميرية أو ملحومة)
     * يُستخدم عند وجود كائن ConnectionType من الكيانات
     *
     * [مرجع: ECP 205 الفصل 5 و 6 / AISC 360 الفصل J]
     *
     * @param connectionType كائن نوع الوصلة
     * @param appliedShear قوة القص المطبقة (كيلونيوتن)
     * @param appliedTension قوة الشد المطبقة (كيلونيوتن)
     * @param plateThickness سماكة الجزء المتصل (مم)
     * @param fuPlate مقاومة الشد القصوى للوحة (MPa)
     * @param fyPlate إجهاد الخضوع للوحة (MPa)
     * @return خريطة تحتوي على نتائج الفحص
     */
    fun checkConnection(
        connectionType: ConnectionType,
        appliedShear: Double = 0.0,
        appliedTension: Double = 0.0,
        plateThickness: Double = 10.0,
        fuPlate: Double = 400.0,
        fyPlate: Double = 250.0
    ): Map<String, Any> {
        val results = mutableMapOf<String, Any>()

        when (connectionType) {
            is ConnectionType.Welded -> {
                // فحص اللحام
                val weldResult = designWeldedConnection(
                    weldType = connectionType.weldType,
                    weldSize = connectionType.weldSize,
                    weldLength = connectionType.weldLength,
                    electrodeType = connectionType.electrodeType,
                    appliedForce = max(appliedShear, appliedTension),
                    plateThickness = plateThickness
                )
                results["weldResult"] = weldResult
                results["isSafe"] = weldResult.isSafe
            }

            is ConnectionType.Bolted -> {
                // فحص المسامير
                val isDoubleShear = plateThickness > connectionType.boltDiameter * 2
                val boltResult = designBoltedConnection(
                    boltDiameter = connectionType.boltDiameter,
                    boltGrade = connectionType.boltGrade,
                    numberOfBolts = connectionType.numberOfBolts,
                    boltPattern = connectionType.boltPattern,
                    boltConnectionType = connectionType.connectionType,
                    appliedShear = appliedShear,
                    appliedTension = appliedTension,
                    plateThickness = plateThickness,
                    fuPlate = fuPlate,
                    isDoubleShear = isDoubleShear,
                    fyPlate = fyPlate
                )
                results["boltResult"] = boltResult
                results["isSafe"] = boltResult.isSafe
            }

            is ConnectionType.Hybrid -> {
                // فحص وصلة مركبة (لحام + مسامير)
                // فحص اللحام
                val weldResult = designWeldedConnection(
                    weldType = connectionType.welded.weldType,
                    weldSize = connectionType.welded.weldSize,
                    weldLength = connectionType.welded.weldLength,
                    electrodeType = connectionType.welded.electrodeType,
                    appliedForce = max(appliedShear, appliedTension) * 0.5,
                    plateThickness = plateThickness
                )
                results["weldResult"] = weldResult

                // فحص المسامير
                val boltResult = designBoltedConnection(
                    boltDiameter = connectionType.bolted.boltDiameter,
                    boltGrade = connectionType.bolted.boltGrade,
                    numberOfBolts = connectionType.bolted.numberOfBolts,
                    boltPattern = connectionType.bolted.boltPattern,
                    boltConnectionType = connectionType.bolted.connectionType,
                    appliedShear = appliedShear * 0.5,
                    appliedTension = appliedTension * 0.5,
                    plateThickness = plateThickness,
                    fuPlate = fuPlate,
                    fyPlate = fyPlate
                )
                results["boltResult"] = boltResult

                // الأمان الكلي: كلا الجزأين يجب أن يكونا آمنين
                results["isSafe"] = weldResult.isSafe && boltResult.isSafe
            }

            is ConnectionType.Pressed -> {
                // وصلة بالضغط - فحص بسيط
                val bearingStress = connectionType.pressForce * 1000.0 / connectionType.contactArea
                val isSafe = bearingStress <= fyPlate
                results["isSafe"] = isSafe
                results["bearingStress"] = bearingStress
                results["message"] = if (isSafe) {
                    "✅ وصلة الضغط آمنة - إجهاد التحمل = ${"%.1f".format(bearingStress)} MPa ≤ Fy = $fyPlate MPa"
                } else {
                    "❌ وصلة الضغط غير آمنة - إجهاد التحمل = ${"%.1f".format(bearingStress)} MPa > Fy = $fyPlate MPa"
                }
            }
        }

        return results
    }

    // ===================== جدول سريع: اختيار المسمار المناسب =====================

    /**
     * اختيار المسمار الأنسب بناءً على القوة المطلوبة
     * يُرجع قائمة بالمسامير المُرتبة حسب الكفاءة (السعر/المقاومة)
     *
     * @param requiredCapacity المقاومة المطلوبة (كيلونيوتن)
     * @param isDoubleShear هل القص مزدوج؟
     * @param preferredGrades الدرجات المفضلة (الافتراضي: كل الدرجات)
     * @return قائمة بالخيارات المتاحة مرتبة حسب الكفاءة
     */
    fun selectOptimalBolt(
        requiredCapacity: Double,
        isDoubleShear: Boolean = false,
        preferredGrades: List<BoltGrade> = BoltGrade.entries
    ): List<BoltOption> {
        val options = mutableListOf<BoltOption>()

        // الأقطار القياسية المتاحة
        val standardDiameters = listOf(16.0, 20.0, 22.0, 24.0, 27.0, 30.0)

        for (grade in preferredGrades) {
            for (diameter in standardDiameters) {
                val shearCap = calculateBoltShearCapacity(grade, diameter, isDoubleShear)
                val bearingCap = shearCap * 1.2 // تقريب
                val capacity = min(shearCap, bearingCap)
                val requiredBolts = calculateRequiredBolts(requiredCapacity, capacity)

                options.add(
                    BoltOption(
                        diameter = diameter,
                        grade = grade,
                        capacityPerBolt = capacity,
                        requiredBolts = requiredBolts,
                        totalCapacity = capacity * requiredBolts,
                        efficiency = capacity / getBoltArea(diameter) // مقاومة لكل وحدة مساحة
                    )
                )
            }
        }

        // ترتيب حسب الكفاءة (الأقل عدد مسامير أولاً، ثم الأعلى كفاءة)
        return options.sortedWith(
            compareBy({ it.requiredBolts }, { -it.efficiency })
        )
    }

    /**
     * خيار مسمار واحد للاستخدام في جدول الاختيار
     */
    data class BoltOption(
        val diameter: Double,           // قطر المسمار (مم)
        val grade: BoltGrade,           // درجة المسمار
        val capacityPerBolt: Double,    // مقاومة المسمار الواحد (كيلونيوتن)
        val requiredBolts: Int,         // عدد المسامير المطلوب
        val totalCapacity: Double,      // المقاومة الكلية (كيلونيوتن)
        val efficiency: Double          // الكفاءة (مقاومة / مساحة)
    )
}