package com.civilengineer.assistant.models

/**
 * نظام الأكواد المدعومة
 * Supported Design Codes
 */
enum class DesignCode(val displayName: String, val shortName: String) {
    EGYPTIAN("الكود المصري - ECP 203", "ECP"),
    SAUDI("الكود السعودي - SBC 304", "SBC"),
    AMERICAN("الكود الأمريكي - ACI 318", "ACI")
}

/**
 * أنواع الأعمدة
 */
enum class ColumnType(val displayName: String) {
    SHORT_RECTANGULAR("عمود قصير مستطيل"),
    SHORT_CIRCULAR("عمود قصير دائري"),
    LONG_RECTANGULAR("عمود طويل مستطيل"),
    LONG_CIRCULAR("عمود طويل دائري"),
    BIAXIAL("عمود ثنائي المحور"),
    L_SHAPED("عمود على شكل L"),
    T_SHAPED("عمود على شكل T")
}

/**
 * أنواع البلاطات
 */
enum class SlabType(val displayName: String) {
    SOLID_ONE_WAY("بلاطة مصمتة باتجاه واحد"),
    SOLID_TWO_WAY("بلاطة مصمتة باتجاهين"),
    HOLLOW_BLOCK("بلاطة هوردي"),
    FLAT_SLAB("بلاطة مسطحة (فلات سلاب)"),
    FLAT_PLATE("بلاطة لاكمرية (فلات بليت)"),
    RIBBED("بلاطة مضلعة"),
    WAFFLE("بلاطة وافل"),
    POST_TENSION("بلاطة سابقة الإجهاد"),
    CANTILEVER("بلاطة كابولية")
}

/**
 * أنواع القواعد
 */
enum class FoundationType(val displayName: String) {
    ISOLATED_CENTRIC("قاعدة منفصلة مركزية"),
    ISOLATED_ECCENTRIC("قاعدة منفصلة لا مركزية"),
    COMBINED_RECTANGULAR("قاعدة مشتركة مستطيلة"),
    COMBINED_TRAPEZOIDAL("قاعدة مشتركة شبه منحرفة"),
    STRIP("قاعدة شريطية"),
    RAFT("لبشة (حصيرة)"),
    PILE_CAP("غطاء خوازيق"),
    PILE("خازوق"),
    WALL_FOOTING("قاعدة حائط"),
    STEPPED("قاعدة مدرجة")
}

/**
 * أنواع الكمرات
 */
enum class BeamType(val displayName: String) {
    SIMPLE_RECTANGULAR("كمرة بسيطة مستطيلة"),
    SIMPLE_T("كمرة بسيطة T"),
    SIMPLE_L("كمرة بسيطة L"),
    CONTINUOUS("كمرة مستمرة"),
    DEEP("كمرة عميقة"),
    CANTILEVER("كمرة كابولية"),
    DOUBLY_REINFORCED("كمرة مزدوجة التسليح"),
    PRESTRESSED("كمرة سابقة الإجهاد"),
    LINTEL("كمرة عتب")
}

/**
 * أنواع حوائط السند
 */
enum class RetainingWallType(val displayName: String) {
    GRAVITY("حائط سند جاذبي"),
    CANTILEVER("حائط سند كابولي"),
    COUNTERFORT("حائط سند بدعامات"),
    BUTTRESSED("حائط سند مدعم"),
    SHEET_PILE("ستائر لوحية"),
    BASEMENT("حائط بدروم")
}

/**
 * أنواع الخزانات
 */
enum class TankType(val displayName: String) {
    UNDERGROUND_RECTANGULAR("خزان أرضي مستطيل"),
    UNDERGROUND_CIRCULAR("خزان أرضي دائري"),
    ELEVATED_RECTANGULAR("خزان علوي مستطيل"),
    ELEVATED_CIRCULAR("خزان علوي دائري"),
    SWIMMING_POOL("حمام سباحة"),
    WATER_TOWER("برج مياه")
}

/**
 * أنواع السلالم
 */
enum class StairsType(val displayName: String) {
    STRAIGHT("سلم مستقيم"),
    DOG_LEGGED("سلم متعرج"),
    OPEN_WELL("سلم ببئر مفتوح"),
    SPIRAL("سلم حلزوني"),
    HELICAL("سلم لولبي"),
    CANTILEVER("سلم كابولي")
}

/**
 * رتبة الخرسانة
 */
enum class ConcreteGrade(val fcu: Double, val fc: Double, val displayName: String) {
    C20(20.0, 16.0, "C20 - 20 N/mm²"),
    C25(25.0, 20.0, "C25 - 25 N/mm²"),
    C30(30.0, 24.0, "C30 - 30 N/mm²"),
    C35(35.0, 28.0, "C35 - 35 N/mm²"),
    C40(40.0, 32.0, "C40 - 40 N/mm²"),
    C45(45.0, 36.0, "C45 - 45 N/mm²"),
    C50(50.0, 40.0, "C50 - 50 N/mm²"),
    C55(55.0, 44.0, "C55 - 55 N/mm²"),
    C60(60.0, 48.0, "C60 - 60 N/mm²")
}

/**
 * رتبة حديد التسليح
 */
enum class SteelGrade(val fy: Double, val displayName: String) {
    ST_240(240.0, "حديد أملس 24/35 - 240 N/mm²"),
    ST_360(360.0, "حديد مشرشر 36/52 - 360 N/mm²"),
    ST_400(400.0, "حديد عالي المقاومة 40/60 - 400 N/mm²"),
    ST_420(420.0, "Grade 60 - 420 N/mm²"),
    ST_500(500.0, "حديد عالي 50/70 - 500 N/mm²"),
    ST_520(520.0, "Grade 75 - 520 N/mm²")
}

/**
 * أقطار حديد التسليح المتاحة
 */
enum class RebarDiameter(val diameter: Double, val area: Double, val weight: Double) {
    D6(6.0, 0.2827, 0.222),
    D8(8.0, 0.5027, 0.395),
    D10(10.0, 0.7854, 0.617),
    D12(12.0, 1.1310, 0.888),
    D14(14.0, 1.5394, 1.208),
    D16(16.0, 2.0106, 1.578),
    D18(18.0, 2.5447, 1.998),
    D20(20.0, 3.1416, 2.466),
    D22(22.0, 3.8013, 2.984),
    D25(25.0, 4.9087, 3.853),
    D28(28.0, 6.1575, 4.834),
    D32(32.0, 8.0425, 6.313),
    D36(36.0, 10.1788, 7.990),
    D40(40.0, 12.5664, 9.865);

    companion object {
        fun fromDiameter(d: Double): RebarDiameter {
            return entries.minByOrNull { kotlin.math.abs(it.diameter - d) } ?: D12
        }

        fun getWeightPerMeter(d: Double): Double {
            return fromDiameter(d).weight
        }

        fun getArea(d: Double): Double {
            return fromDiameter(d).area
        }
    }
}

/**
 * نوع التربة
 */
enum class SoilType(val displayName: String, val bearingCapacity: Double, val description: String) {
    ROCK("صخر", 1000.0, "قدرة تحمل عالية جداً"),
    DENSE_GRAVEL("زلط كثيف", 400.0, "قدرة تحمل عالية"),
    MEDIUM_GRAVEL("زلط متوسط", 250.0, "قدرة تحمل متوسطة-عالية"),
    DENSE_SAND("رمل كثيف", 300.0, "قدرة تحمل جيدة"),
    MEDIUM_SAND("رمل متوسط", 150.0, "قدرة تحمل متوسطة"),
    LOOSE_SAND("رمل ناعم/سائب", 80.0, "قدرة تحمل ضعيفة"),
    STIFF_CLAY("طين متماسك", 200.0, "قدرة تحمل متوسطة-عالية"),
    MEDIUM_CLAY("طين متوسط", 100.0, "قدرة تحمل متوسطة"),
    SOFT_CLAY("طين لين", 50.0, "قدرة تحمل ضعيفة"),
    FILL("ردم", 0.0, "غير مناسب للتأسيس المباشر")
}

/**
 * نوع العملة
 */
enum class Currency(val displayName: String, val symbol: String) {
    EGP("جنيه مصري", "ج.م"),
    SAR("ريال سعودي", "ر.س"),
    USD("دولار أمريكي", "$"),
    AED("درهم إماراتي", "د.إ"),
    KWD("دينار كويتي", "د.ك"),
    QAR("ريال قطري", "ر.ق"),
    BHD("دينار بحريني", "د.ب"),
    OMR("ريال عماني", "ر.ع"),
    JOD("دينار أردني", "د.أ"),
    EUR("يورو", "€"),
    GBP("جنيه إسترليني", "£")
}

/**
 * نتيجة التصميم
 */
data class DesignResult(
    val isSafe: Boolean,
    val safetyFactor: Double,
    val mainReinforcement: ReinforcementResult,
    val secondaryReinforcement: ReinforcementResult? = null,
    val shearReinforcement: ShearReinforcementResult? = null,
    val quantitySurvey: QuantitySurveyResult? = null,
    val costEstimation: CostResult? = null,
    val equations: List<EquationStep> = emptyList(),
    val warnings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val codeChecks: List<CodeCheck> = emptyList()
)

/**
 * نتيجة التسليح
 */
data class ReinforcementResult(
    val requiredArea: Double,        // cm²
    val providedArea: Double,        // cm²
    val numberOfBars: Int,
    val barDiameter: Double,         // mm
    val spacing: Double = 0.0,       // mm (for slabs)
    val ratio: Double,               // percentage
    val minRatio: Double,
    val maxRatio: Double,
    val description: String = ""
)

/**
 * نتيجة تسليح القص
 */
data class ShearReinforcementResult(
    val requiredShear: Double,       // kN
    val concreteCapacity: Double,    // kN
    val stirrupDiameter: Double,     // mm
    val numberOfLegs: Int,
    val spacing: Double,             // mm
    val description: String = ""
)

/**
 * نتيجة الحصر
 */
data class QuantitySurveyResult(
    val concreteVolume: Double,      // m³
    val steelWeight: Double,         // kg
    val formworkArea: Double,        // m²
    val wasteFactor: Double,         // percentage
    val steelWithWaste: Double,      // kg
    val concreteWithWaste: Double,   // m³
    val rebarDetails: List<RebarDetail> = emptyList(),
    val description: String = ""
)

/**
 * تفاصيل الحديد
 */
data class RebarDetail(
    val description: String,
    val diameter: Double,            // mm
    val length: Double,              // m
    val count: Int,
    val totalWeight: Double          // kg
)

/**
 * نتيجة التكلفة
 */
data class CostResult(
    val concreteCost: Double,
    val steelCost: Double,
    val formworkCost: Double,
    val laborCost: Double,
    val totalCost: Double,
    val currency: Currency,
    val breakdown: List<CostItem> = emptyList()
)

data class CostItem(
    val description: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double
)

/**
 * خطوة معادلة (للشرح)
 */
data class EquationStep(
    val stepNumber: Int,
    val title: String,
    val equation: String,
    val substitution: String,
    val result: String,
    val codeReference: String = "",
    val explanation: String = ""
)

/**
 * فحص الكود
 */
data class CodeCheck(
    val checkName: String,
    val codeClause: String,
    val requiredValue: String,
    val actualValue: String,
    val isPassed: Boolean,
    val description: String = ""
)

/**
 * نتيجة تحليل الزلازل
 */
data class SeismicResult(
    val baseShear: Double,           // kN
    val storyForces: List<StoryForce>,
    val naturalPeriod: Double,       // seconds
    val seismicCoefficient: Double,
    val responseFactor: Double,
    val importanceFactor: Double,
    val soilFactor: Double,
    val zoneFactor: Double,
    val equations: List<EquationStep> = emptyList(),
    val codeChecks: List<CodeCheck> = emptyList()
)

data class StoryForce(
    val storyNumber: Int,
    val height: Double,              // m
    val weight: Double,              // kN
    val force: Double,               // kN
    val shear: Double,               // kN
    val moment: Double               // kN.m
)

/**
 * معطيات التحليل الإنشائي
 */
data class StructuralAnalysisInput(
    val spans: List<Double>,         // m
    val loads: List<LoadCase>,
    val supportConditions: List<SupportType>,
    val sectionProperties: SectionProperties
)

data class LoadCase(
    val deadLoad: Double,            // kN/m or kN
    val liveLoad: Double,            // kN/m or kN
    val windLoad: Double = 0.0,      // kN/m or kN
    val seismicLoad: Double = 0.0    // kN/m or kN
)

enum class SupportType(val displayName: String) {
    FIXED("مثبت"),
    PINNED("مفصلي"),
    ROLLER("أسطواني"),
    FREE("حر"),
    CONTINUOUS("مستمر")
}

data class SectionProperties(
    val width: Double,               // mm
    val height: Double,              // mm
    val momentOfInertia: Double = 0.0,
    val sectionModulus: Double = 0.0,
    val area: Double = 0.0
)

/**
 * المنطقة الزلزالية
 */
enum class SeismicZone(val displayName: String, val zoneFactorECP: Double, val zoneFactorSBC: Double) {
    ZONE_1("المنطقة 1 - منخفضة", 0.10, 0.07),
    ZONE_2("المنطقة 2 - متوسطة منخفضة", 0.15, 0.10),
    ZONE_3("المنطقة 3 - متوسطة", 0.20, 0.15),
    ZONE_4("المنطقة 4 - متوسطة عالية", 0.25, 0.20),
    ZONE_5A("المنطقة 5A - عالية", 0.30, 0.25),
    ZONE_5B("المنطقة 5B - عالية جداً", 0.35, 0.30)
}

/**
 * فئة أهمية المبنى
 */
enum class ImportanceCategory(val displayName: String, val factorECP: Double, val factorSBC: Double, val factorACI: Double) {
    CATEGORY_1("منشآت ذات أهمية منخفضة", 0.80, 0.80, 1.00),
    CATEGORY_2("منشآت عادية (سكنية/تجارية)", 1.00, 1.00, 1.00),
    CATEGORY_3("منشآت ذات أهمية كبيرة (مدارس/مستشفيات)", 1.20, 1.25, 1.25),
    CATEGORY_4("منشآت حيوية (محطات طاقة/طوارئ)", 1.40, 1.50, 1.50)
}

/**
 * نظام المقاومة الزلزالية
 */
enum class SeismicResistanceSystem(val displayName: String, val responseFactor: Double) {
    MOMENT_FRAME_ORDINARY("إطارات عادية مقاومة للعزوم", 3.5),
    MOMENT_FRAME_INTERMEDIATE("إطارات متوسطة مقاومة للعزوم", 5.0),
    MOMENT_FRAME_SPECIAL("إطارات خاصة مقاومة للعزوم", 7.0),
    SHEAR_WALL_ORDINARY("حوائط قص عادية", 4.0),
    SHEAR_WALL_SPECIAL("حوائط قص خاصة", 5.5),
    DUAL_ORDINARY("نظام مزدوج عادي", 5.5),
    DUAL_SPECIAL("نظام مزدوج خاص", 7.0),
    BEARING_WALL("حوائط حاملة", 2.0)
}
