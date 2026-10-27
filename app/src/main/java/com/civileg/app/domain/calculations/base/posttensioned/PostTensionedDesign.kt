package com.civileg.app.domain.calculations.base.posttensioned

import com.civileg.app.domain.entities.DesignCode

/**
 * واجهة تصميم العناصر مسبقة الإجهاد (Post-Tensioned) وفق ECP 203 / ACI 318 / SBC 304.
 *
 * الوحدات: الأبعاد بالمليمتر، الأحمال kN/m، العزوم kN.m، القوى kN، الإجهادات MPa
 * ما لم يُذكر غير ذلك.
 */
interface PostTensionedDesign {

    fun designPostTensioned(input: PostTensionedInput): PostTensionedResult

    fun calculateLosses(input: PostTensionedInput): PTLosses

    fun calculateRequiredPrestress(input: PostTensionedInput): PrestressDesign

    fun checkStresses(input: PostTensionedInput, losses: PTLosses): StressCheckResult

    fun checkUltimateStrength(input: PostTensionedInput, losses: PTLosses): UltimateStrengthResult

    fun designAnchorageZone(input: PostTensionedInput, jackingForce: Double): AnchorageZoneResult

    fun getMinimumPrestress(input: PostTensionedInput): Double

    fun calculateDeflection(input: PostTensionedInput, losses: PTLosses): PTDeflectionResult
}

/**
 * مدخلات تصميم عنصر مسبق الإجهاد.
 */
data class PostTensionedInput(
    val elementType: PostTensionedElementType = PostTensionedElementType.Beam,
    val span: Double,                    // m
    val width: Double,                   // mm
    val depth: Double,                   // mm
    val flangeWidth: Double = 0.0,       // mm (for T/L sections)
    val flangeThickness: Double = 0.0,   // mm
    val fcu: Double,                     // MPa (cube strength)
    val fy: Double,                      // MPa (mild rebar)
    val fpk: Double,                     // MPa (tendon ultimate)
    val fp01k: Double,                   // MPa (tendon 0.1% proof)
    val tendonType: PTTendonType = PTTendonType.Strand_7Wire_12_7mm,
    val numTendons: Int = 1,
    val tendonArea: Double = 98.7,       // mm² per tendon
    val deadLoad: Double,                // kN/m (superimposed dead, excluding self-weight)
    val liveLoad: Double,                // kN/m
    val supportType: PTSupportType = PTSupportType.SimplySupported,
    val cover: Double = 40.0,            // mm
    val designCode: DesignCode = DesignCode.ECP,
    val exposureClass: PTExposureClass = PTExposureClass.Moderate,
    val fireRating: Int = 0,             // minutes (0 = no requirement)
    val isContinuous: Boolean = false,
    val leftSpan: Double = 0.0,          // m (for continuous)
    val rightSpan: Double = 0.0,         // m
    val leftSupportWidth: Double = 300.0, // mm
    val rightSupportWidth: Double = 300.0, // mm
    val frictionCoefficient: Double = 0.2,
    val wobbleCoefficient: Double = 0.003, // 1/m
    val anchorageSlip: Double = 6.0,       // mm
    val relaxationClass: Int = 2           // 1 = normal, 2 = low relaxation
) {
    fun effectiveDepth(): Double = depth - cover - 20.0

    /**
     * الوزن الذاتي مع احتساب شفة T/L إن وجدت.
     * العرض الكلي للشفة لا يقل عن عرض الويب، والسماكة محدودة بالعمق.
     */
    fun selfWeight(): Double {
        val bf = if (flangeWidth > width) flangeWidth else width
        val hf = if (bf > width) flangeThickness.coerceIn(0.0, depth) else 0.0
        val area = width * depth + (bf - width) * hf
        return area * 25.0 / 1e6 // kN/m
    }

    fun totalDeadLoad(): Double = deadLoad + selfWeight()

    fun tendonTotalArea(): Double = numTendons * tendonArea

    fun validate() {
        require(span > 0) { "Span must be positive / يجب أن يكون البحر موجباً" }
        require(width > 0) { "Width must be positive / يجب أن يكون العرض موجباً" }
        require(depth > 0) { "Depth must be positive / يجب أن يكون العمق موجباً" }
        require(fcu > 0) { "fcu must be positive / يجب أن تكون مقاومة الخرسانة موجبة" }
        require(fy > 0) { "fy must be positive / يجب أن تكون مقاومة الحديد موجبة" }
        require(fpk > 0) { "fpk must be positive / يجب أن تكون مقاومة الكابل موجبة" }
        require(fp01k > 0) { "fp01k must be positive / يجب أن تكون مقاومة الكابل 0.1% موجبة" }
        require(fp01k <= fpk) { "fp01k must not exceed fpk / fp01k يجب ألا يتجاوز fpk" }
        require(tendonArea > 0) { "tendonArea must be positive / مساحة الكابل يجب أن تكون موجبة" }
        require(numTendons > 0) { "numTendons must be positive / عدد الكابلات يجب أن يكون موجباً" }
        require(deadLoad >= 0) { "deadLoad must be non-negative / الحمل الميت يجب أن يكون غير سالب" }
        require(liveLoad >= 0) { "liveLoad must be non-negative / الحمل الحي يجب أن يكون غير سالب" }
        require(cover >= 0) { "cover must be non-negative / الغطاء يجب أن يكون غير سالب" }
        require(cover < depth) { "cover must be less than depth / الغطاء يجب أن يقل عن العمق" }
        require(flangeWidth >= 0 && flangeThickness >= 0) {
            "flange dimensions must be non-negative / أبعاد الشفة يجب أن تكون غير سالبة"
        }
        require(frictionCoefficient >= 0 && wobbleCoefficient >= 0 && anchorageSlip >= 0) {
            "prestress loss parameters must be non-negative / معاملات الفقد يجب أن تكون غير سالبة"
        }
        require(leftSpan >= 0 && rightSpan >= 0) {
            "adjacent spans must be non-negative / البحور المجاورة يجب أن تكون غير سالبة"
        }
        require(leftSupportWidth >= 0 && rightSupportWidth >= 0) {
            "support widths must be non-negative / عروض الاستناد يجب أن تكون غير سالبة"
        }
        require(relaxationClass in 1..2) { "relaxationClass must be 1 or 2 / فئة الاسترخاء يجب أن تكون 1 أو 2" }
        listOf(
            span, width, depth, fcu, fy, fpk, fp01k, tendonArea, deadLoad, liveLoad,
            cover, flangeWidth, flangeThickness, frictionCoefficient, wobbleCoefficient,
            anchorageSlip, leftSpan, rightSpan, leftSupportWidth, rightSupportWidth
        ).forEach {
            require(it.isFinite()) { "Inputs must be finite / المدخلات يجب أن تكون أرقاماً منتهية" }
        }
    }
}

/**
 * أنواع الكابلات (Tendons) — الأسماء تبدأ بـ PT لتجنب التعارض مع TendonType في SlabEntities.
 */
enum class PTTendonType(
    val areaMm2: Double,
    val diameterMm: Double,
    val fpkMpa: Double,
    val fp01kMpa: Double,
    val description: String
) {
    Strand_7Wire_9_5mm(51.6, 9.53, 1860.0, 1610.0, "7-wire strand 9.5mm"),
    Strand_7Wire_11_1mm(74.2, 11.11, 1860.0, 1610.0, "7-wire strand 11.1mm"),
    Strand_7Wire_12_7mm(98.7, 12.7, 1860.0, 1610.0, "7-wire strand 12.7mm"),
    Strand_7Wire_15_2mm(139.4, 15.24, 1860.0, 1610.0, "7-wire strand 15.2mm"),
    Strand_7Wire_18_0mm(199.0, 18.0, 1860.0, 1610.0, "7-wire strand 18.0mm"),
    Wire_5mm(19.6, 5.0, 1770.0, 1540.0, "Single wire 5mm"),
    Wire_7mm(38.5, 7.0, 1770.0, 1540.0, "Single wire 7mm"),
    Bar_25mm(491.0, 25.0, 1030.0, 880.0, "High-strength bar 25mm"),
    Bar_32mm(804.0, 32.0, 1030.0, 880.0, "High-strength bar 32mm");

    companion object {
        fun fromDescription(desc: String): PTTendonType =
            entries.firstOrNull { it.description == desc } ?: Strand_7Wire_12_7mm
    }
}

/**
 * نوع العنصر المسبق الإجهاد.
 */
enum class PostTensionedElementType(val displayName: String) {
    Beam("Beam"),
    Slab("Slab - One Way"),
    SlabTwoWay("Slab - Two Way"),
    BandBeam("Band Beam"),
    TransferBeam("Transfer Beam"),
    Girder("Girder"),
    BridgeGirder("Bridge Girder"),
    TankWall("Tank Wall"),
    SiloWall("Silo Wall")
}

/**
 * نوع الاستناد ومعاملات العزم/القص التقريبية.
 */
enum class PTSupportType(val momentCoeff: Double, val shearCoeff: Double, val displayName: String) {
    SimplySupported(1.0 / 8.0, 1.0 / 2.0, "Simply Supported"),
    FixedFixed(1.0 / 12.0, 1.0 / 2.0, "Fixed-Fixed"),
    FixedHinged(1.0 / 8.0, 5.0 / 8.0, "Fixed-Hinged"),
    Cantilever(1.0 / 2.0, 1.0, "Cantilever"),
    Continuous(1.0 / 10.0, 1.0 / 2.0, "Continuous (interior span)");

    companion object {
        fun fromString(str: String): PTSupportType =
            entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: SimplySupported
    }
}

/**
 * فئة التعرض البيئي — تحدد الغطاء الأدنى وعرض الشرخ المسموح.
 */
enum class PTExposureClass(
    val minCover: Double,
    val maxWc: Double,
    val minFcu: Double,
    val maxCrackWidth: Double,
    val displayName: String
) {
    Mild(25.0, 0.65, 20.0, 0.3, "Mild"),
    Moderate(30.0, 0.60, 25.0, 0.2, "Moderate"),
    Severe(40.0, 0.50, 30.0, 0.1, "Severe"),
    VerySevere(50.0, 0.45, 35.0, 0.1, "Very Severe"),
    Extreme(60.0, 0.40, 40.0, 0.1, "Extreme")
}

/**
 * تفصيل فقد الإجهاد (Prestress Losses) — القيم بـ MPa.
 */
data class PTLosses(
    val elasticShortening: Double = 0.0,
    val creep: Double = 0.0,
    val shrinkage: Double = 0.0,
    val steelRelaxation: Double = 0.0,
    val friction: Double = 0.0,
    val anchorageSlip: Double = 0.0,
    val temperature: Double = 0.0,
    val total: Double = 0.0,
    val totalPercent: Double = 0.0,
    val effectiveStress: Double = 0.0,
    val effectiveForce: Double = 0.0
) {
    fun isAcceptable(maxPercent: Double = 25.0): Boolean = totalPercent <= maxPercent
}

/**
 * تصميم الإجهاد المسبق المطلوب.
 */
data class PrestressDesign(
    val requiredForce: Double,           // kN (after losses)
    val jackingForce: Double,            // kN (at jacking)
    val initialStress: Double,           // MPa (at transfer)
    val effectiveStress: Double,         // MPa (after losses)
    val requiredEccentricity: Double,    // mm
    val providedEccentricity: Double,    // mm
    val numTendons: Int,
    val tendonProfile: TendonProfile,
    val isAdequate: Boolean,
    val notes: List<String> = emptyList()
)

/**
 * شكل مسار الكابل.
 */
enum class TendonProfile(val displayName: String) {
    Parabolic("Parabolic (Drape)"),
    Harped("Harped (Two straight segments)"),
    Straight("Straight (Constant eccentricity)"),
    Variable("Variable (Multiple points)")
}

/**
 * نتائج فحص الإجهادات عند النقل والخدمة.
 */
data class StressCheckResult(
    val transferTopCompression: Double = 0.0,
    val transferBottomCompression: Double = 0.0,
    val transferTopTension: Double = 0.0,
    val transferBottomTension: Double = 0.0,
    val serviceTopCompression: Double = 0.0,
    val serviceBottomCompression: Double = 0.0,
    val serviceTopTension: Double = 0.0,
    val serviceBottomTension: Double = 0.0,
    val allowableTransferComp: Double = 0.0,
    val allowableTransferTens: Double = 0.0,
    val allowableServiceComp: Double = 0.0,
    val allowableServiceTens: Double = 0.0,
    val isTransferSafe: Boolean = false,
    val isServiceSafe: Boolean = false,
    val criticalLocation: String = "Midspan",
    val notes: List<String> = emptyList()
) {
    val overallSafe: Boolean get() = isTransferSafe && isServiceSafe
}

/**
 * نتائج فحص المقاومة القصوى.
 */
data class UltimateStrengthResult(
    val flexuralCapacity: Double = 0.0,   // kN.m
    val requiredMoment: Double = 0.0,     // kN.m
    val shearCapacity: Double = 0.0,      // kN
    val requiredShear: Double = 0.0,      // kN
    val torsionCapacity: Double = 0.0,    // kN.m
    val requiredTorsion: Double = 0.0,    // kN.m
    val flexuralUtilization: Double = 0.0,
    val shearUtilization: Double = 0.0,
    val torsionUtilization: Double = 0.0,
    val isFlexureSafe: Boolean = false,
    val isShearSafe: Boolean = false,
    val isTorsionSafe: Boolean = true,
    val requiredStirrups: StirrupDesign? = null,
    val requiredLongitudinal: Double = 0.0, // mm²
    val notes: List<String> = emptyList()
) {
    val overallSafe: Boolean get() = isFlexureSafe && isShearSafe && isTorsionSafe
}

/**
 * تصميم الكانات.
 */
data class StirrupDesign(
    val diameter: Double,
    val spacing: Double,
    val legs: Int,
    val requiredArea: Double,
    val providedArea: Double,
    val isAdequate: Boolean
)

/**
 * تصميم منطقة التثبيت (Anchorage Zone).
 */
data class AnchorageZoneResult(
    val burstingForce: Double = 0.0,
    val spallingForce: Double = 0.0,
    val burstingReinforcement: Double = 0.0,
    val spallingReinforcement: Double = 0.0,
    val stirrupDiameter: Double = 0.0,
    val stirrupSpacing: Double = 0.0,
    val stirrupLegs: Int = 2,
    val zoneLength: Double = 0.0,
    val isAdequate: Boolean = false,
    val notes: List<String> = emptyList()
)

/**
 * نتائج فحص الترخيم مع الإجهاد المسبق.
 */
data class PTDeflectionResult(
    val deadLoadDeflection: Double = 0.0,
    val liveLoadDeflection: Double = 0.0,
    val prestressDeflection: Double = 0.0,
    val totalImmediateDeflection: Double = 0.0,
    val longTermDeflection: Double = 0.0,
    val totalDeflection: Double = 0.0,
    val allowableDeflection: Double = 0.0,
    val isSafe: Boolean = false,
    val preCamber: Double = 0.0,
    val notes: List<String> = emptyList()
)

/**
 * النتيجة الكاملة لتصميم عنصر مسبق الإجهاد.
 */
data class PostTensionedResult(
    val input: PostTensionedInput,
    val prestressDesign: PrestressDesign,
    val losses: PTLosses,
    val stressCheck: StressCheckResult,
    val ultimateStrength: UltimateStrengthResult,
    val anchorageZone: AnchorageZoneResult,
    val deflection: PTDeflectionResult,
    val isSafe: Boolean,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList(),
    val quantity: PTQuantityEstimate = PTQuantityEstimate()
)

/**
 * تقدير الكميات للعناصر مسبقة الإجهاد.
 */
data class PTQuantityEstimate(
    val concreteVolume: Double = 0.0,
    val rebarWeight: Double = 0.0,
    val ptTendonWeight: Double = 0.0,
    val ductWeight: Double = 0.0,
    val anchorageWeight: Double = 0.0,
    val groutVolume: Double = 0.0,
    val formworkArea: Double = 0.0,
    val laborHours: Double = 0.0,
    val totalCost: Double = 0.0
)