package com.civileg.core.engineering

/**
 * Steel-member profile properties, fed to the DrawingModel steel builder.
 *
 * A steel member carries no reinforcing bars: the section IS the steel. The
 * "detail" conveyed to the drawing is the profile geometry (depth × width ×
 * web/flange thicknesses) the emitter mirrors as an elevation + cut A-A for
 * I/channel/angle/tube-like sections, plus the section identity and the
 * qualitative check status (safe / utilization). Pure passthrough of the app
 * side domain [com.civileg.app.domain.SteelMemberResult] and its
 * [com.civileg.app.domain.entities.SteelSectionType] — nothing is recomputed
 * here; [codeReference] is the engine's own (e.g. "AISC 360-B4 / ECP 205-3").
 */
data class SteelMemberReinforcementResult(
    /** Catalogue name, e.g. "HEB 300" / "IPE 240" / "C 120x60". */
    val sectionName: String,
    /** Engine member type name (COLUMN / BEAM / BRACING / TRUSS_MEMBER / GIRDERS). */
    val memberType: String,
    /** Member length (mm) — X extent of the elevation box. */
    val memberLengthMm: Double,
    /** Overall section depth (mm) — Y extent of the drawing. */
    val depthMm: Double,
    /** Flange width / outer diameter (mm) — X extent of cut A-A. */
    val widthMm: Double,
    /** Web thickness (mm). */
    val webThicknessMm: Double,
    /** Flange thickness (mm). */
    val flangeThicknessMm: Double,
    /** Engine utilization ratio (0..∞); >1 implies [isSafe] false. */
    val utilizationRatio: Double,
    /** Engine verdict — the drawing note + drawing state derive from it. */
    val isSafe: Boolean,
    /** Section's own code citation, e.g. "AISC 360-B4 / ECP 205-3". */
    val codeReference: String,
    val warnings: List<String> = emptyList()
)