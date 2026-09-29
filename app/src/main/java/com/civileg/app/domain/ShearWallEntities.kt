package com.civileg.app.domain

// ══════════════════════════════════════════════════════════════
// ENUMS
// ══════════════════════════════════════════════════════════════

enum class WallType(val displayName: String) {
    ORDINARY("Ordinary Shear Wall"),
    SPECIAL("Special Shear Wall"),
    COUPLED("Coupled Shear Wall")
}

enum class BoundaryElementType(val displayName: String) {
    NONE("No Boundary Element Required"),
    STANDARD("Standard Boundary Element"),
    SPECIAL("Special Confined Boundary Element")
}

// ══════════════════════════════════════════════════════════════
// INPUT
// ══════════════════════════════════════════════════════════════

data class ShearWallInput(
    val wallType: WallType = WallType.ORDINARY,
    val wallLength: Double = 4000.0,       // mm (total length)
    val wallThickness: Double = 300.0,    // mm
    val wallHeight: Double = 3000.0,      // mm (story height)
    val numberOfStories: Int = 10,
    val axialLoad: Double = 5000.0,       // kN (at base)
    val shearForce: Double = 800.0,       // kN (at base)
    val bendingMoment: Double = 3000.0,   // kN.m (at base)
    val fcu: Double = 30.0,               // MPa
    val fy: Double = 400.0,               // MPa
    val fyv: Double = 250.0,              // MPa (for horizontal bars/stirrups)
    val clearCover: Double = 25.0,        // mm
    val flangeWidth: Double = 0.0,        // mm (for L/T walls, 0 = rectangular)
    val flangeThickness: Double = 0.0,    // mm
    val endZoneLength: Double = 0.0,      // mm (0 = auto)
    val couplingBeamLength: Double = 0.0, // mm (for coupled walls)
    val couplingBeamHeight: Double = 0.0, // mm
    val couplingBeamClearSpan: Double = 0.0 // mm
)

// ══════════════════════════════════════════════════════════════
// COUPLING BEAM RESULT
// ══════════════════════════════════════════════════════════════

data class CouplingBeamResult(
    val diagonalBars: Int,
    val diagonalBarDiameter: Int,
    val transverseBarsDiameter: Int,
    val transverseBarsSpacing: Int,
    val isSafe: Boolean,
    val utilizationRatio: Double
)

// ══════════════════════════════════════════════════════════════
// COMPLETE SHEAR WALL RESULT
// ══════════════════════════════════════════════════════════════

data class ShearWallResult(
    val isSafe: Boolean,
    val utilizationRatio: Double,
    // Flexure
    val flexuralOk: Boolean,
    val momentCapacity: Double,            // kN.m
    val axialCapacity: Double,             // kN
    val compressionDepth: Double,          // mm
    val verticalReinforcement: RebarResult,
    val boundaryElementType: BoundaryElementType,
    val boundaryElementReinforcement: RebarResult?,
    // Shear
    val shearOk: Boolean,
    val shearCapacity: Double,             // kN
    val concreteShearCapacity: Double,     // kN
    val steelShearCapacity: Double,        // kN
    val horizontalReinforcement: RebarResult,
    // Stability
    val slendernessOk: Boolean,
    val slendernessRatio: Double,
    // Coupling beam (if applicable)
    val couplingBeamResult: CouplingBeamResult?,
    // Quantities
    val concreteVolumePerStory: Double,    // m³
    val steelWeightPerStory: Double,       // kg
    // Warnings & notes
    val warnings: List<String> = emptyList(),
    val codeNotes: List<String> = emptyList(),
    val safetyChecks: List<ShearWallSafetyCheck> = emptyList()
)

data class ShearWallSafetyCheck(
    val name: String,
    val value: Double,
    val limit: Double,
    val unit: String,
    val isSafe: Boolean
)
