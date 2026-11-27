package com.civileg.app.domain

// ══════════════════════════════════════════════════════════════════
// ENUMS
// ══════════════════════════════════════════════════════════════════

enum class PanelType(val displayName: String) {
    INTERIOR("Interior Panel"),
    EDGE("Edge Panel"),
    CORNER("Corner Panel")
}

enum class DesignMethod(val displayName: String) {
    DDM("Direct Design Method"),
    EFM("Equivalent Frame Method")
}

// ══════════════════════════════════════════════════════════════════
// INPUT
// ══════════════════════════════════════════════════════════════════

data class FlatSlabInput(
    val panelType: PanelType = PanelType.INTERIOR,
    val designMethod: DesignMethod = DesignMethod.DDM,
    val lx: Double = 6000.0,           // mm (shorter span)
    val ly: Double = 7500.0,           // mm (longer span)
    val slabThickness: Double = 250.0, // mm
    val dropThickness: Double = 0.0,   // mm (0 = no drop)
    val dropSizeX: Double = 0.0,       // mm
    val dropSizeY: Double = 0.0,       // mm
    val columnWidth: Double = 400.0,   // mm
    val columnDepth: Double = 400.0,   // mm
    val fcu: Double = 30.0,            // MPa
    val fy: Double = 400.0,            // MPa
    val liveLoad: Double = 3.0,        // kN/m2
    val floorFinish: Double = 2.0,     // kN/m2
    val numberOfFloors: Int = 10,
    val clearCover: Double = 25.0,     // mm
    val storyHeight: Double = 3.0,     // m
    val headEffectiveDiameter: Double = 0.0, // mm, effective column head diameter D (0 = no column head given)
    val headSlopeFromVerticalDeg: Double = 0.0 // degrees from vertical (only read when headEffectiveDiameter > 0)
)

// ══════════════════════════════════════════════════════════════════
// COMPLETE FLAT SLAB RESULT
// ══════════════════════════════════════════════════════════════════

data class FlatSlabResult(
    val isSafe: Boolean,
    val utilizationRatio: Double,
    val totalDeadLoad: Double,
    val totalFactoredLoad: Double,
    val panelMomentX: Double,
    val panelMomentY: Double,
    val columnStripMomentPos: Double,
    val columnStripMomentNeg: Double,
    val middleStripMomentPos: Double,
    val middleStripMomentNeg: Double,
    val columnStripWidthX: Double,
    val columnStripWidthY: Double,
    val columnStripTopRebar: RebarResult,
    val columnStripBotRebar: RebarResult,
    val middleStripTopRebar: RebarResult,
    val middleStripBotRebar: RebarResult,
    val punchingShearOk: Boolean,
    val punchingShearVu: Double,
    val punchingShearVc: Double,
    val punchingPerimeter: Double,
    val punchingReinforcement: RebarResult?,
    val dropRequired: Boolean,
    val dropThickness: Double,
    val deflectionOk: Boolean,
    val deflection: Double,
    val allowableDeflection: Double,
    val concreteVolumePerPanel: Double,
    val steelWeightPerPanel: Double,
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList(),
    val safetyChecks: List<SafetyCheckItem> = emptyList(),
    val calculationSteps: List<com.civileg.app.domain.entities.CalculationStep> = emptyList(),
    /** Y-direction reinforcement (null for engines that design X only). */
    val columnStripTopRebarY: RebarResult? = null,
    val columnStripBotRebarY: RebarResult? = null,
    val middleStripTopRebarY: RebarResult? = null,
    val middleStripBotRebarY: RebarResult? = null,
    /** false = deflection was NOT ASSESSED; deflectionOk/deflection/allowableDeflection are then meaningless. */
    val deflectionAssessed: Boolean = true
)

data class SafetyCheckItem(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
)
