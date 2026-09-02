package com.civileg.core.engineering

/**
 * Frame-analysis elevation detail, fed to the DrawingModel frame builder.
 *
 * Pure passthrough of the app-side frame topology and member designs
 * ([com.civileg.app.domain.entities.FrameNode] /
 * [com.civileg.app.domain.entities.FrameMember] /
 * [com.civileg.app.domain.entities.FrameAnalysisResult]): node coordinates in
 * mm, member end-node coordinates in mm, material/type strings, and the
 * section band the emitter uses to draw each member as a rectangle around its
 * centreline. Convenience layout numbers (column band from the section width,
 * beam band from the section depth, steel band schematic — the design result
 * carries no profile dimensions) are resolved by the app adapter, mirroring
 * the on-screen renderer's uniform-thickness member lines. Nothing strength-
 * related is recomputed here; per-member safety/utilization are the engine's
 * own verdicts passed through, and [FrameAnalysisDetailResult.isSafe] plus
 * [FrameAnalysisDetailResult.codeReference] drive the drawing state/note.
 */
data class FrameNodeDetail(
    /** Node coordinate (mm), origin at the app's model space. */
    val xMm: Double,
    val yMm: Double,
    /** Support type name — FIXED / PIN / ROLLER / VERTICAL_ROLLER / FREE. */
    val supportType: String
)

data class FrameMemberDetail(
    /** Engine member id (topology traceability only). */
    val memberId: Int,
    /** Start node (node I), mm. */
    val x1Mm: Double,
    val y1Mm: Double,
    /** End node (node J), mm. */
    val x2Mm: Double,
    val y2Mm: Double,
    /** Material type name — CONCRETE / STEEL. */
    val materialType: String,
    /** Member type name — COLUMN / BEAM / BRACE. */
    val memberType: String,
    /** Rectangle band (mm) drawn perpendicular to the member centreline. */
    val bandMm: Double,
    /** Section label for the schedule, e.g. "300x600" / "HEB 300". */
    val sectionName: String,
    /** Engine verdict for this member (drives tone, not geometry). */
    val isSafe: Boolean,
    /** Engine utilization for this member (moment/shear/combined max). */
    val utilization: Double
)

data class FrameAnalysisDetailResult(
    val nodes: List<FrameNodeDetail>,
    val members: List<FrameMemberDetail>,
    /** Overall verdict — all members passed. */
    val isSafe: Boolean,
    /** Code citation carried to title note / annotations. */
    val codeReference: String,
    val warnings: List<String> = emptyList()
)