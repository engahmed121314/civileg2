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
    val storyHeight: Double = 3.0      // m
)

// ══════════════════════════════════════════════════════════════════
// REBAR RESULT
// ══════════════════════════════════════════════════════════════════

data class RebarResult(
    val bars: Int,
    val diameter: Int,
    val spacing: Int,
    val providedArea: Double,
    val requiredArea: Double,
    val ratio: Double
) {
    val barString: String get() = "φ$diameter@$spacing mm"
}

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
    val safetyChecks: List<SafetyCheckItem> = emptyList()
)

data class SafetyCheckItem(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
)
