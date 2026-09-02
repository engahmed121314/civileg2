package com.civileg.core.engineering

/**
 * Shear-wall reinforcement families, fed to the DrawingModel shear-wall builder.
 *
 * A shear wall carries longitudinal (vertical) steel along the wall length, web
 * horizontal (shear) steel on both faces, and — when the engine demands it —
 * concentrated boundary-element vertical steel + confinement ties at the ends.
 * A coupled wall additionally reports diagonal + transverse coupling-beam steel
 * (scheduled only; not part of the wall plan section). Pure passthrough of the
 * app-side domain [com.civileg.app.domain.ShearWallResult]: no recomputation.
 */
data class ShearWallVerticalReinforcement(
    /** Total vertical (longitudinal) bar count across the wall length — the
     *  web-distributed steel; the boundary families are reported separately. */
    val count: Int,
    val diameterMm: Double,
    /** Centre-to-centre spacing along the length. */
    val spacingMm: Double
)

data class ShearWallHorizontalReinforcement(
    val diameterMm: Double,
    /** Vertical centre-to-centre spacing of the horizontal (shear) layers. */
    val spacingMm: Double
)

data class ShearWallBoundaryReinforcement(
    /** Vertical bar count within ONE boundary element zone (per wall end). */
    val bars: Int,
    val diameterMm: Double,
    /** Confinement tie spacing within the zone. */
    val spacingMm: Double
)

data class ShearWallCouplingReinforcement(
    val diagonalBars: Int,
    val diagonalDiameterMm: Double,
    val transverseDiameterMm: Double,
    val transverseSpacingMm: Double
)

data class ShearWallReinforcementResult(
    val vertical: ShearWallVerticalReinforcement,
    val horizontal: ShearWallHorizontalReinforcement,
    /** Present only when the engine requires a boundary element. */
    val boundary: ShearWallBoundaryReinforcement? = null,
    /** Present only for coupled walls with a designed coupling beam. */
    val couplingBeam: ShearWallCouplingReinforcement? = null,
    val isSafe: Boolean,
    val warnings: List<String>
)