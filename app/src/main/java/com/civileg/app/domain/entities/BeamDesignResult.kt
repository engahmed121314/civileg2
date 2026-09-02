package com.civileg.app.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.civileg.core.calculations.entities.DesignCode

/**
 * Comprehensive Beam Design Result - covers ALL design aspects from the 9 reference PDFs:
 * 04: BMD/SFD, 05: Load Distribution, 06: Bending Behavior,
 * 07: First Principles Design, 08: Charts Design,
 * 09: Shear Design, 10: Torsion Design,
 * 11: Empirical Reinforcement, 12: Moment of Resistance
 */
@Parcelize
data class BeamDesignResult(
    // ===== 1. INPUT PARAMETERS =====
    val beamWidth: Double,          // mm - b
    val beamDepth: Double,          // mm - h
    val span: Double,               // m - L
    val clearSpan: Double,          // m - Ln (clear span)
    val effectiveDepth: Double,     // mm - d = h - cover - stirrup - bar/2
    val fcu: Double,                // MPa - f'cu (cube) or f'c (cylinder for ACI)
    val fy: Double,                 // MPa - yield strength
    val deadLoad: Double,           // kN/m - service dead load
    val liveLoad: Double,           // kN/m - service live load
    val designCode: DesignCode,
    val supportType: String,        // SS, FF, FH, CANTILEVER
    val sectionType: String,        // RECTANGULAR, T_SECTION, L_SECTION
    // T-beam specific
    val flangeWidth: Double = 0.0,  // mm - bf
    val flangeThickness: Double = 0.0, // mm - hf/ts
    // Self-weight
    val selfWeight: Double = 0.0,   // kN/m
    val totalDeadLoad: Double = 0.0, // kN/m (including self-weight)
    val totalLiveLoad: Double = 0.0, // kN/m

    // ===== 2. LOAD DISTRIBUTION (PDF 05) =====
    val loadDistributionNotes: String = "",
    val slabType: String = "",          // one-way, two-way
    val slabThickness: Double = 0.0,    // mm
    val tributaryWidth: Double = 0.0,   // m - width of load contribution
    val wallLoad: Double = 0.0,         // kN/m - load from walls
    val ownWeightLoad: Double = 0.0,    // kN/m - self weight of beam
    val flooringLoad: Double = 0.0,     // kN/m - flooring/mortar
    val plasterLoad: Double = 0.0,      // kN/m - plaster

    // ===== 3. LOAD COMBINATIONS & FACTORS =====
    val deadLoadFactor: Double = 1.4,
    val liveLoadFactor: Double = 1.6,
    val ultimateLoad: Double = 0.0,     // kN/m - wu
    val serviceTotalLoad: Double = 0.0, // kN/m

    // ===== 4. STRUCTURAL ANALYSIS (PDF 04 - BMD/SFD) =====
    val maxMoment: Double = 0.0,       // kN.m - Mu (ultimate)
    val maxShear: Double = 0.0,        // kN - Vu (ultimate)
    val maxMomentPos: Double = 0.0,    // kN.m - max positive (sagging)
    val maxMomentNeg: Double = 0.0,    // kN.m - max negative (hogging)
    val maxShearLeft: Double = 0.0,    // kN
    val maxShearRight: Double = 0.0,   // kN
    val reactionLeft: Double = 0.0,    // kN
    val reactionRight: Double = 0.0,   // kN
    val shearAtCriticalSection: Double = 0.0, // kN - at d from support
    val pointOfZeroShear: Double = 0.0, // m - location
    val momentAtMidspan: Double = 0.0,  // kN.m
    val diagramPoints: Int = 50,       // number of points in diagrams

    // ===== 5. FLEXURE DESIGN - FIRST PRINCIPLES (PDF 07) =====
    val K: Double = 0.0,               // ECP: K = Mu / (fcu/γc * b * d²)
    val Kbal: Double = 0.0,            // Balanced K value
    val omega: Double = 0.0,           // Reinforcement index ω = (As*fy/γs) / (fcu/γc * b * d)
    val leverArmZ: Double = 0.0,       // mm - z = d(0.5 + √(0.25 - K/0.9))
    val neutralAxisDepth: Double = 0.0, // mm - x (from compression face)
    val concreteStressBlockDepth: Double = 0.0, // mm - a = β * x (Whitney block)
    // ACI specific
    val Rn: Double = 0.0,              // ACI: Rn = Mu / (φ * b * d²)
    val rho: Double = 0.0,             // ACI: reinforcement ratio
    val rhoBalanced: Double = 0.0,     // Balanced reinforcement ratio
    val beta1: Double = 0.85,          // Whitney stress block factor
    // Moment of Resistance (PDF 12)
    val momentOfResistance: Double = 0.0, // kN.m - MR
    // Design Charts (PDF 08)
    val chartK: Double = 0.0,          // K from design chart
    val chartZD: Double = 0.0,         // z/d ratio from chart

    // ===== 6. REQUIRED REINFORCEMENT =====
    val asRequired: Double = 0.0,      // mm² - As req from analysis
    val asMin: Double = 0.0,           // mm² - As minimum (code)
    val asMax: Double = 0.0,           // mm² - As maximum (code)
    val asProvided: Double = 0.0,      // mm² - As provided (selected bars)
    val rhoActual: Double = 0.0,       // ρ = As / (b*d)
    val rhoMin: Double = 0.0,          // minimum ratio
    val rhoMax: Double = 0.0,          // maximum ratio (4% ECP, varies ACI)

    // ===== 7. SELECTED REINFORCEMENT =====
    val bottomBars: String = "",      // e.g. "4Ø16"
    val bottomBarCount: Int = 0,
    val bottomBarDia: Int = 0,
    val topBars: String = "",          // e.g. "2Ø12" (hanger bars)
    val topBarCount: Int = 0,
    val topBarDia: Int = 0,
    val topAsProvided: Double = 0.0,   // mm²
    // Compression steel (doubly reinforced)
    val needsCompressionSteel: Boolean = false,
    val compressionBars: String = "",
    val compressionBarCount: Int = 0,
    val compressionBarDia: Int = 0,
    val compressionAsProvided: Double = 0.0,

    // ===== 8. SHEAR DESIGN (PDF 09) =====
    // Concrete shear capacity
    val vc: Double = 0.0,              // MPa - concrete shear stress capacity
    val vcFormula: String = "",       // formula used
    val vMax: Double = 0.0,            // MPa - maximum shear stress allowed
    val appliedShearStress: Double = 0.0, // MPa - qu = Vu/(b*d)
    // Steel shear reinforcement
    val vs: Double = 0.0,              // MPa - shear carried by stirrups
    val avRequired: Double = 0.0,      // mm² - Av/s required
    val avProvided: Double = 0.0,      // mm² - Av provided per stirrup
    val stirrupDia: Int = 8,
    val stirrupLegs: Int = 2,
    val stirrupSpacingSupport: Double = 0.0, // mm - at support zone
    val stirrupSpacingMidspan: Double = 0.0,  // mm - at midspan
    val condensationZoneLength: Double = 0.0,  // mm
    // Min shear reinforcement
    val avMin: Double = 0.0,           // mm²/m - minimum shear reinforcement
    val maxSpacing: Double = 0.0,      // mm - maximum allowed spacing
    val minSpacing: Double = 0.0,      // mm - minimum allowed spacing

    // ===== 9. TORSION DESIGN (PDF 10) =====
    val torsionalMoment: Double = 0.0,    // kN.m - Tu applied
    val torsionalThreshold: Double = 0.0, // kN.m - Tu,th (threshold below which torsion is neglected)
    val needsTorsionDesign: Boolean = false,
    val torsionalReinforcement: String = "",
    val combinedShearStress: Double = 0.0, // MPa - combined shear + torsion
    val combinedCapacity: Double = 0.0,   // MPa
    val torsionIsSafe: Boolean = true,
    val torsionalStirrupSpacing: Double = 0.0, // mm
    val longitudinalTorsionAs: Double = 0.0,    // mm²
    val torsionalLongitudinalBars: String = "",

    // ===== 10. DEFLECTION CHECK (Enhanced Ie Method) =====
    val grossMomentOfInertia: Double = 0.0,     // mm⁴ - Ig = b*h³/12
    val crackedMomentOfInertia: Double = 0.0,  // mm⁴ - Icr
    val effectiveMomentOfInertia: Double = 0.0, // mm⁴ - Ie
    val modulusOfElasticity: Double = 0.0,       // MPa - Ec
    val crackingMoment: Double = 0.0,           // kN.m - Mcr
    val immediateDeflection: Double = 0.0,      // mm
    val longTermDeflection: Double = 0.0,       // mm (with creep + shrinkage)
    val allowableDeflection: Double = 0.0,      // mm
    val deflectionIsSafe: Boolean = true,
    val deflectionSpanRatio: String = "",      // e.g. "L/250"
    val basicSpanDepthRatio: Double = 0.0,
    val modifiedSpanDepthRatio: Double = 0.0,

    // ===== 11. CRACK WIDTH CHECK =====
    val crackWidthCalculated: Double = 0.0, // mm - wk
    val crackWidthAllowable: Double = 0.0, // mm
    val crackIsSafe: Boolean = true,
    val averageStrain: Double = 0.0,        // εm
    val surfaceStrain: Double = 0.0,       // εs
    val effectiveModularRatio: Double = 0.0, // αe = Es/Ec
    val neutralAxisRatio: Double = 0.0,    // x/d ratio

    // ===== 12. DEVELOPMENT LENGTH =====
    val developmentLengthRequired: Double = 0.0, // mm - Ld
    val developmentLengthProvided: Double = 0.0, // mm
    val bondStress: Double = 0.0,              // MPa - fbd
    val lapLength: Double = 0.0,               // mm
    val developmentIsSafe: Boolean = true,

    // ===== 13. SAFETY & ECONOMY =====
    val isSafe: Boolean = true,
    val utilizationRatio: Double = 0.0,      // overall utilization
    val flexureUtilization: Double = 0.0,
    val shearUtilization: Double = 0.0,
    val concreteVolume: Double = 0.0,        // m³
    val steelWeight: Double = 0.0,          // kg
    val cost: Double = 0.0,                 // currency units
    val safetyChecks: List<SafetyCheckItem> = emptyList(),
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList(),

    // ===== 14. STEP-BY-STEP CALCULATION LOG =====
    val calculationSteps: List<AppCalculationStep> = emptyList()
) : Parcelable

/**
 * A single calculation step with formula and computed value.
 * Used to display step-by-step calculations in the UI.
 */
@Parcelize
data class AppCalculationStep(
    val stepNumber: Int,
    val title: String,           // e.g. "Step 1: Ultimate Load"
    val subtitle: String = "", // subcategory
    val formula: String,        // e.g. "wu = 1.4*DL + 1.6*LL"
    val formulaWithValues: String, // e.g. "wu = 1.4*15.0 + 1.6*10.0 = 37.0 kN/m"
    val result: String,         // e.g. "wu = 37.0 kN/m"
    val unit: String = "",
    val codeReference: String = "", // e.g. "ECP 203 §4-2-2"
    val isPass: Boolean = true, // for checks
    val status: StepStatus = StepStatus.CALCULATION
) : Parcelable

@Parcelize
enum class StepStatus : Parcelable {
    CALCULATION,    // Normal calculation step
    CHECK_PASS,     // Safety check passed
    CHECK_FAIL,     // Safety check failed
    WARNING,        // Warning
    INFO,           // Informational note
    SECTION_HEADER  // Section divider
}

/**
 * Safety check item for display
 */
@Parcelize
data class SafetyCheckItem(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean,
    val codeRef: String = ""
) : Parcelable

/**
 * Loading case types for BMD/SFD diagrams
 */
enum class LoadingCase(val displayName: String, val description: String) {
    UDL("Uniform Distributed Load", "w (kN/m) across full span"),
    POINT_LOAD_CENTER("Point Load at Center", "P at L/2"),
    POINT_LOAD_ANYWHERE("Point Load at Position", "P at distance 'a' from left"),
    TWO_POINT_LOADS("Two Symmetric Point Loads", "P at L/3 from each support"),
    MOMENT_AT_END("Moment at One End", "M0 at left support"),
    TRAPEZOIDAL("Trapezoidal Load", "w1 at left, w2 at right"),
    TRIANGULAR("Triangular Load", "0 at left, w at right"),
    SELF_WEIGHT_ONLY("Self-Weight Only", "Own weight of beam section")
}

/**
 * Load source types for load distribution analysis (PDF 05)
 */
enum class LoadSource(val displayNameAr: String, val displayNameEn: String) {
    SELF_WEIGHT("الوزن الذاتي", "Self-Weight"),
    SLAB_LOAD("حمل البلاطة", "Slab Load"),
    WALL_LOAD("حمل الحائط", "Wall Load"),
    FLOORING("حمل الأرضيات", "Flooring/Mortar"),
    PLASTER("حمل البلاستر", "Plaster"),
    PARAPET("حمل البارابيت", "Parapet")
}