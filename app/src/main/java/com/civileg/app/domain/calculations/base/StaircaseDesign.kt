package com.civileg.app.domain.calculations.base

import com.civileg.app.domain.entities.DesignCode

/**
 * أنواع السلالم المدعومة
 */
enum class StairType(val displayName: String) {
    STRAIGHT("Straight Stair"),
    DOG_LEG("Dog-Leg / Quarter-Turn"),
    SPIRAL("Spiral Stair"),
    OPEN_WELL("Open Well Stair")
}

/**
 * Support/continuity contract for a stair flight.
 * UNSPECIFIED is the honest default: no restraint may be ASSUMED from it.
 */
enum class StairSupportCondition {
    /** Flight on two simple supports (waist only). Conservative per-flight model. */
    SIMPLY_SUPPORTED,
    /** Flight continuous over an intermediate landing that restrains it. Only
     *  valid when landing geometry is explicitly supplied. */
    CONTINUOUS_OVER_LANDING,
    /** No support information: engines must use the conservative model and mark
     *  the restraint assumption unverified. */
    UNSPECIFIED
}

/**
 * Status of the analysis model behind a result — travels with the result so a
 * UI case name can never masquerade as a verified design.
 */
enum class StairAnalysisStatus {
    /**
     * The implemented analysis model matches the declared case and the supplied
     * support inputs. NOT complete case qualification: unimplemented detailing
     * (e.g. landing slab steel, top steel over a landing) is reported separately
     * via the empty result fields and code notes.
     */
    CASE_MODEL_IMPLEMENTED,
    /** Conservative fallback used because support/landing inputs were UNSPECIFIED. */
    CONSERVATIVE_FALLBACK_RESTRAINT_UNVERIFIED,
    /** No implemented model for the named case; numbers are not a verified design. */
    UNSUPPORTED_CASE
}

/**
 * بيانات الإدخال لتصميم السلم
 */
data class StaircaseInput(
    val stairType: StairType = StairType.STRAIGHT,
    val span: Double,            // m - horizontal projection of stair flight
    val totalRise: Double,       // m - total vertical height
    val stairWidth: Double,      // m - width of stair
    val waistThickness: Double,  // mm - thickness of waist slab
    val fcu: Double,             // MPa - concrete cube strength
    val fy: Double,              // MPa - steel yield strength
    val deadLoad: Double = 6.0,  // kN/m² - self-weight + finishes
    val liveLoad: Double = 4.0,  // kN/m² - variable load
    val riserCount: Int = 0,     // 0 = auto-calculate
    val going: Double = 0.0,     // 0 = auto-calculate

    // ── Case contract (2026-11-24, lead/specialty coordination) ──
    // UNSPECIFIED default: engines must NOT assume landing restraint from it.
    val supportCondition: StairSupportCondition = StairSupportCondition.UNSPECIFIED,
    // Landing geometry — required to claim CONTINUOUS_OVER_LANDING for DOG_LEG.
    val landingLengthMm: Double? = null,
    val landingWidthMm: Double? = null,
    val landingThicknessMm: Double? = null
)

/**
 * نتيجة فحص أمان
 */
data class StairSafetyCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val description: String = "",
    /** true = the check could not be assessed (e.g. outside a deemed-to-satisfy table); isSafe stays false, but this is not a computed failure. */
    val notAssessed: Boolean = false,
    /** What the row is: a code strength/serviceability check, a geometric limit from another document, or a model/practical assumption flag. */
    val kind: StairCheckKind = StairCheckKind.fromName(name)
) {
    /** Name shown on screen, drawing strip and PDF: non-code rows carry their kind so they never read as a code pass. */
    val displayName: String get() = kind.prefix + name
}

/** Main-steel step state: follows the flexure strength row only, never the overall result (which stays not-a-pass while any row is NOT ASSESSED). */
fun mainSteelStepPass(checks: List<StairSafetyCheck>): Boolean? = checks.firstOrNull { it.name.startsWith("Flexure") }?.isSafe

/** The measured ratio of the flexure strength row (value/limit, unclamped) and what it is. Null when no assessed flexure row exists. */
data class StairFlexureRatio(val ratio: Double, val basis: String)

/** ACI/SBC flexure row is rho vs rho_max_tc; the ECP row is K vs K_bal. No clamp and no fallback: absent or unassessed means null. */
fun flexureRatioOf(checks: List<StairSafetyCheck>): StairFlexureRatio? {
    val row = checks.firstOrNull { it.name.startsWith("Flexure") } ?: return null
    if (row.notAssessed || !row.value.isFinite() || !row.limit.isFinite() || row.limit <= 0.0 || row.value < 0.0) return null
    val basis = when {
        row.name.contains("Rmax") -> "K/(Rmax/γc)"
        row.name.contains("K_bal") -> "K/K_bal"
        else -> "rho/rho_max"
    }
    return StairFlexureRatio(row.value / row.limit, basis)
}

enum class FlexureCardState { UNAVAILABLE, EXCEEDS_LIMIT, PRELIMINARY, WITHIN_LIMIT }

/** Card state: never an approved/ideal reading while code acceptance is incomplete; unavailable when there is no measured ratio. */
fun flexureCardState(ratio: Double?, acceptanceComplete: Boolean): FlexureCardState = when {
    ratio == null -> FlexureCardState.UNAVAILABLE
    ratio > 1.0 -> FlexureCardState.EXCEEDS_LIMIT
    !acceptanceComplete -> FlexureCardState.PRELIMINARY
    else -> FlexureCardState.WITHIN_LIMIT
}

enum class StairCheckKind(val prefix: String) {
    CODE(""),
    /** Geometric limits (IBC 1011.5, comfort rule) taken from documents other than the design code of the lane. */
    GEOMETRY("Geometry limit: "),
    /** Practical minimums, model availability flags and slab analogies: not code strength checks. */
    ASSUMPTION("Assumption/model flag: ");

    companion object {
        fun fromName(name: String): StairCheckKind = when {
            name.startsWith("2R + G") || name.startsWith("Riser") || name.startsWith("Going") -> GEOMETRY
            name.startsWith("Waist Thickness") || name.startsWith("Case model support") || name.startsWith("Waist >=") -> ASSUMPTION
            else -> CODE
        }
    }
}

/**
 * نتيجة تصميم السلم الشاملة
 */
data class StaircaseResult(
    val isSafe: Boolean,
    val designCode: DesignCode,

    // التصميم الهندسي
    val riser: Double,          // mm - rise per step
    val going: Double,          // mm - going per step
    val numberOfRisers: Int,
    val numberOfTreads: Int,
    val slopeAngle: Double,     // degrees
    val inclinedLength: Double, // m - along slope

    // الأحمال
    val factoredLoad: Double,   // kN/m² on slope
    val horizontalLoad: Double, // kN/m² projected horizontal

    // نتائج التحليل
    val maxMoment: Double,      // kN.m per meter width
    val maxShear: Double,       // kN per meter width
    val reactionA: Double,      // kN
    val reactionB: Double,      // kN

    // تصميم الانحناء
    val mainRebar: String,      // e.g., "5Φ12"
    val mainRebarArea: Double,  // mm²/m
    val distributionRebar: String,
    val distributionRebarArea: Double,
    val effectiveDepth: Double, // mm
    val reinforcementRatio: Double,
    val minSteelRatio: Double,

    // تصميم القص
    val shearCapacity: Double,
    val requiredStirrups: String,
    val stirrupDiameter: Double,
    val stirrupSpacing: Double,

    // الانحراف
    val deflection: Double,
    val allowableDeflection: Double,
    val deflectionOk: Boolean,
    /** false = deflection and allowableDeflection are NOT COMPUTED placeholders (0.0); consumers must not display or compare them. */
    val deflectionComputed: Boolean = true,

    // فحوصات الأمان
    val safetyChecks: List<StairSafetyCheck>,
    val codeNotes: List<String>,

    // ── Case contract results (2026-11-24) ──
    // No silent implemented-model claim: a lane that has not adapted to the
    // case contract must default to UNSUPPORTED_CASE (ACI/SBC lanes pending).
    val analysisStatus: StairAnalysisStatus = StairAnalysisStatus.UNSUPPORTED_CASE,
    val analysisSpanM: Double = 0.0,        // span actually used in analysis
    val adoptedRunMm: Double = 0.0,         // discrete adopted run (treads × going)
    // Separated reinforcement outputs. Empty string = not designed.
    val flightBottomRebar: String = "",     // defaults mirror legacy mainRebar
    val flightTopRebar: String = "",        // top/negative-moment steel (continuity)
    val landingRebar: String = ""           // landing slab steel; empty = not designed
)

/**
 * واجهة موحدة لتصميم السلالم حسب أي كود إنشائي
 * Stair modeled as simply supported one-way slab (beam per meter width)
 */
interface StaircaseDesign {
    fun designStaircase(input: StaircaseInput): StaircaseResult
}

/**
 * Gooseneck (حبة البط) detail at re-entrant corner of stair slab
 * per ECP 203-2020 §4-2
 */
data class GooseneckDetailResult(
    val gooseneckRadius: Double,       // mm - recommended radius at re-entrant corner
    val minRadius: Double,             // mm - minimum radius (50mm per ECP)
    val additionalBars: Int,           // number of additional corner bars
    val additionalBarDia: Double,      // mm - bar diameter at gooseneck
    val additionalBarArea: Double,     // mm² - total additional steel area
    val developmentLength: Double,     // mm - development length for gooseneck bars
    val extensionLength: Double,       // mm - minimum extension each side of corner
    val gooseneckAngle: Double,        // degrees - stair slope angle
    val codeNote: String               // code reference note
)