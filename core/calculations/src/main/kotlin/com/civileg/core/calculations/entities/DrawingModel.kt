package com.civileg.core.calculations.entities

import com.civileg.core.engineering.BeamDesignFacade
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.CodeVersion
import com.civileg.core.engineering.FrameAnalysisDetailResult
import com.civileg.core.engineering.SeismicDetailResult
import com.civileg.core.engineering.toFootingReinforcement
import com.civileg.core.engineering.toReinforcementResult
import com.civileg.core.engineering.toSlabReinforcement
import com.civileg.core.engineering.toStairReinforcement
import com.civileg.core.engineering.toTankReinforcement
import com.civileg.core.engineering.toRetainingWallReinforcement
import java.util.Locale

/**
 * CIVILEG UNIFIED DrawingModel (Pillar 2).
 *
 * Single source of truth for all structural drawing output.
 * Everything that goes to PDF, DXF, or on-screen rendering must be
 * derivable from this model — NO independent computation.
 *
 * Rules:
 *   1. All coordinates in millimetres (model-space).
 *   2. No NaN / Infinity values allowed (validated at construction).
 *   3. Every reinforcing bar carries a [CodeReference].
 *   4. [DrawingState] tracks code edition and overall sanity status.
 */
data class DrawingModel(
    /** Project metadata */
    val project: String,
    val drawingNumber: String,
    val sheetNumber: String,
    val titleBlock: TitleBlock,

    /** Section geometry per element type */
    val beamSection: BeamSectionGeometry? = null,
    val columnSection: ColumnSectionGeometry? = null,
    val slabSection: SlabSectionGeometry? = null,
    val footingSection: FootingSectionGeometry? = null,
    val stairSection: StairSectionGeometry? = null,
    val tankSection: TankSectionGeometry? = null,
    val retainingWallSection: RetainingWallSectionGeometry? = null,
    val shearWallSection: ShearWallSectionGeometry? = null,
    val steelSection: SteelSectionGeometry? = null,
    val frameGeometry: FrameGeometry? = null,
    val seismicChart: SeismicChartGeometry? = null,
    val beamElevation: BeamElevationGeometry? = null,

    /** Steel member schedule rows (MARK / SECTION / LENGTH / QTY). */
    val steelMembers: List<SteelMemberMark> = emptyList(),

    /** Frame member schedule rows (MARK / MEMBER / SECTION / LENGTH / QTY). */
    val frameMembers: List<FrameMemberMark> = emptyList(),

    /** Reinforcement data — the "identity" of every bar */
    val reinforcement: ReinforcementSet = ReinforcementSet(),

    /** Dimension lines (all derived, not hand-drawn) */
    val dimensions: DimensionSet = DimensionSet(),

    /** Title block annotations */
    val annotations: AnnotationSet = AnnotationSet(),

    /** Code edition and validation */
    val state: DrawingState,

    /** QA flags — set by the engine before the model is "frozen" */
    val qaFlags: QaFlags = QaFlags()
) {
    /** Lazy-computed bounding box for the entire drawing. */
    val bounds: BoundingBox get() = computeDrawingBounds(this)

    override fun toString() = "DrawingModel($drawingNumber,$sheetNumber/${state.edition.key})"
}

/** Drawing issuance stage (mirror of the app-side block, kept in core). */
enum class DrawingStatus(val label: String) {
    PRELIMINARY("PRELIMINARY"),
    FOR_REVIEW("FOR REVIEW"),
    FOR_CONSTRUCTION("FOR CONSTRUCTION"),
    AS_BUILT("AS BUILT")
}

/** Self-contained title block — core must not depend on the Android module. */
data class TitleBlock(
    val project: String,
    val drawingTitle: String,
    val drawingNumber: String,
    val date: String,
    val scale: String,
    val designCode: String,
    val client: String = "",
    val consultant: String = "",
    val revision: String = "00",
    val status: DrawingStatus = DrawingStatus.FOR_CONSTRUCTION,
    val sheet: String = "1/1"
)

/** Code edition that produced this model. */
data class DrawingState(
    val code: DesignCode,
    val edition: CodeVersion,
    val overallStatus: CheckStatus,
    val generatedAt: String = java.time.Instant.now().toString()
)

/** QA flags for the drawing model. */
data class QaFlags(
    /** NaN / Infinity detected somewhere in the model */
    val hasInvalid: Boolean = false,
    /** Sanity engine found violations */
    val sanityWarnings: Boolean = false,
    /** DXF export validated OK */
    val dxfValid: Boolean = false,
    /** PDF export validated OK */
    val pdfValid: Boolean = false
)

/** -----------------------------------------------------------------------
 *  §1  Beam Section Geometry
 * ----------------------------------------------------------------------- */

/** Complete cross-section geometry for a beam, ready for rendering. */
data class BeamSectionGeometry(
    val overallHeight: Double,   // mm total height h
    val overallWidth: Double,    // mm total width b
    val effectiveDepth: Double,  // mm d = h - cover - bar_dia/2
    val concreteCover: Double,   // mm clear cover
    /** Main tension reinforcement — ordered by position (bottom then top) */
    val tensionBars: List<SectionBar>,
    /** Main compression reinforcement */
    val compressionBars: List<SectionBar>,
    /** Stirrups/ties arrangement */
    val stirrups: List<StirrupGeometry>,
    /** Axis-aligned bounding box of the section */
    val sectionBounds: BoundingBox
)

/** A single bar position in the section. */
data class SectionBar(
    val diameter: Double,       // mm
    val position: Double,       // mm from extreme fibre (bottom=0, top=h)
    val codeReference: String,
    val barString: String = "Ø${diameterLabel(diameter)}"
)

/** Stirrup/tie geometry at a given section. */
data class StirrupGeometry(
    val diameter: Double,       // mm
    val spacing: Double,        // mm centre-to-centre
    val hookType: String,       // "90°", "135°", "180°"
    val hookLength: Double,     // mm (12d, 6d+75, etc.)
    val codeReference: String
)

/** -----------------------------------------------------------------------
 *  §2  Column Section Geometry
 * ----------------------------------------------------------------------- */

/** Complete cross-section geometry for a column. */
data class ColumnSectionGeometry(
    val overallHeight: Double,      // mm h
    val overallWidth: Double,       // mm b (rectangular) or diameter (circular)
    val concreteCover: Double,      // mm
    val coreBars: List<SectionBar>,       // core bars group
    val outerBars: List<SectionBar>,      // outer perimeter bars
    val ties: List<StirrupGeometry>,
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §3  Slab Section Geometry
 * ----------------------------------------------------------------------- */

/** Union type for [DrawingModel.slabSection]. */
sealed interface SlabSectionGeometry

/** Geometry for a one-way slab section (top/bottom reinforcement). */
data class SlabSectionGeometryOneWay(
    val thickness: Double,      // mm total thickness h
    val effectiveDepth: Double, // mm d
    val concreteCover: Double,  // mm
    val topBars: List<SectionBar>,
    val bottomBars: List<SectionBar>,
    val sectionBounds: BoundingBox
) : SlabSectionGeometry

/** Geometry for a two-way slab section. */
data class SlabSectionGeometryTwoWay(
    val thickness: Double,
    val effectiveDepth: Double,
    val concreteCover: Double,
    /** Primary moment direction (short span) */
    val shortTopBars: List<SectionBar>,
    val shortBottomBars: List<SectionBar>,
    /** Secondary moment direction (long span) */
    val longTopBars: List<SectionBar>,
    val longBottomBars: List<SectionBar>,
    val sectionBounds: BoundingBox
) : SlabSectionGeometry

/**
 * Geometry for a flat-slab panel section (DDM/EFM strip reinforcement).
 *
 * Layout mirrors the panel plan: X = long span, Y = short span. The four
 * strip groups are carried exactly as the engine decides them
 * (column/middle × top/bottom); the column strip band is `columnStripWidthMm`
 * wide along the long span and the middle strip the remainder. A drop panel
 * (input dropSize × dropDepth, both mm, 0 = none) is carried for the
 * schematic top-face outline. `dropBars` is reserved for any explicit
 * drop-panel steel (always empty today — the engine reports no drop bars).
 */
data class SlabSectionGeometryFlat(
    val thickness: Double,
    val effectiveDepth: Double,
    val concreteCover: Double,
    val columnStripWidthMm: Double,
    val columnStripTopBars: List<SectionBar>,
    val columnStripBottomBars: List<SectionBar>,
    val middleStripTopBars: List<SectionBar>,
    val middleStripBottomBars: List<SectionBar>,
    val dropBars: List<SectionBar>,
    val dropDepth: Double,
    val dropSize: Double,
    val sectionBounds: BoundingBox
) : SlabSectionGeometry

/** -----------------------------------------------------------------------
 *  §4  Footing Section Geometry
 * ----------------------------------------------------------------------- */

/** Isolated footing geometry. */
data class FootingSectionGeometry(
    val length: Double,         // mm L (x-direction)
    val width: Double,          // mm B (y-direction)
    val thickness: Double,      // mm total depth h
    val concreteCover: Double,  // mm
    val bottomBars: List<SectionBar>,
    val topBars: List<SectionBar>,        // distribution bars
    val anchorBars: List<SectionBar>?,    // if needed
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §4a  Stair Section Geometry
 * ----------------------------------------------------------------------- */

/** Waist-slab section geometry (longitudinal main + transverse distribution). */
data class StairSectionGeometry(
    val waistThickness: Double,   // mm total depth h
    val effectiveDepth: Double,   // mm d
    val concreteCover: Double,    // mm
    val mainBars: List<SectionBar>,         // longitudinal bars on the slope
    val distributionBars: List<SectionBar>, // transverse bars across the width
    val stirrups: List<StirrupGeometry>,
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §4b  Tank Section Geometry
 * ----------------------------------------------------------------------- */

/** Tank wall + base section geometry (wall vertical / wall horizontal / base). */
data class TankSectionGeometry(
    val wallThickness: Double,        // mm
    val baseThickness: Double,        // mm
    val effectiveDepth: Double,       // mm d — wall flexural effective depth
    val concreteCover: Double,        // mm
    val wallVerticalBars: List<SectionBar>,    // vertical bars on the inner (water) face
    val wallHorizontalBars: List<SectionBar>,  // horizontal bars on the outer face
    val baseBars: List<SectionBar>,            // base bars near the bottom face
    val sectionBounds: BoundingBox
)

/** Cantilever earth-retaining wall section (stem + base: toe | stem | heel). */
data class RetainingWallSectionGeometry(
    val wallHeight: Double,           // mm overall stem height H
    val stemBaseThickness: Double,    // mm tBase at the base of the stem
    val baseWidth: Double,            // mm B — toe + stem base + heel
    val baseThickness: Double,        // mm tFooting
    val toeLength: Double,            // mm
    val heelLength: Double,           // mm
    val concreteCover: Double,        // mm
    val stemMainBars: List<SectionBar>,       // vertical flexure bars on the earth face
    val distributionBars: List<SectionBar>,   // transverse bars on the outer face
    val toeBars: List<SectionBar>,            // bottom flexure bars under the toe
    val heelBars: List<SectionBar>,           // top flexure bars over the heel
    val sectionBounds: BoundingBox
)

/**
 * Schematic flange leg of an L/T-shaped wall (perpendicular projection), mirroring
 * the canvas renderer's [ProfessionalShearWallDrawing] proportions.
 */
data class ShearWallFlange(
    val shape: String,          // "L-shaped" | "T-shaped"
    val projectionMm: Double,   // leg depth measured from the wall face
    val thicknessMm: Double     // leg thickness (parallel to the wall section)
)

/**
 * Shear-wall horizontal section (plan of one story: length × thickness).
 *
 * Layout mirrors the canvas renderer's [ProfessionalShearWallDrawing]
 * drawWallSection: the longitudinal (vertical) steel is shown distributed along
 * the wall length, the horizontal (shear) steel as face rows near both thickness
 * faces, and boundary-element zones as shaded end regions with confinement ties
 * when the engine demands a boundary element. X = wall length, Y = wall
 * thickness. A flange leg (optional, schematic) extends the section for L/T
 * shapes and is absorbed into [sectionBounds]. Coupling-beam steel is scheduled
 * but is not part of this plan section.
 */
data class ShearWallSectionGeometry(
    val wallLength: Double,                   // mm (X)
    val wallThickness: Double,                // mm (Y)
    val concreteCover: Double,                // mm
    val webVerticalBars: List<SectionBar>,    // longitudinal steel, position = wallThickness/2
    val horizontalFaceBars: List<SectionBar>, // face rows, positions near both thickness faces
    val boundaryElementLengthMm: Double,      // mm per end — 0 = no boundary element zone
    val boundaryTies: List<StirrupGeometry>,  // confinement ties within the end zones
    val flange: ShearWallFlange? = null,      // L/T leg (none for rectangular)
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §4a  Steel Member Section Geometry
 * ----------------------------------------------------------------------- */

/** A steel member schedule row (MARK / SECTION / LENGTH / QTY). */
data class SteelMemberMark(
    val mark: String,            // e.g. "COL-1"
    val sectionName: String,     // e.g. "HEB 300"
    val lengthMm: Double,
    val quantity: Int
)

/**
 * Steel-member elevation + cut A-A, mirroring the on-screen renderer's
 * [ProfessionalSteelDrawing] conventions: a long elevation box (length ×
 * depth) with flange-interface lines, and a schematic cut A-A to its right
 * showing the profile plates (top flange, web, bottom flange) bounded by the
 * engine's depth / width / flange / web quantities. Steel has no reinforcing
 * bars — the section IS the steel — so only the qualitative check status is
 * carried (for the sheet note); X spans the elevation length plus the cut, Y
 * is the member depth.
 */
data class SteelSectionGeometry(
    val sectionName: String,            // catalogue name for the schedule/note
    val memberLengthMm: Double,         // mm (X of the elevation box)
    val depthMm: Double,                // mm (Y of the drawing)
    val widthMm: Double,                // mm flange width (X extent of cut A-A)
    val webThicknessMm: Double,         // mm
    val flangeThicknessMm: Double,      // mm
    val isSafe: Boolean,                // engine verdict → drawing note
    val utilizationRatio: Double,       // engine UX → drawing note
    val codeReference: String,          // engine's own citation (AISC/ECP-205/…)
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §4b  Frame Elevation Geometry
 * ----------------------------------------------------------------------- */

/** A frame member schedule row (MARK / MEMBER / SECTION / LENGTH / QTY). */
data class FrameMemberMark(
    val mark: String,            // e.g. "FM-1"
    val memberType: String,      // COLUMN / BEAM / BRACE
    val sectionName: String,     // e.g. "300x600" / "HEB 300"
    val lengthMm: Double,        // member length along its centreline (mm)
    val quantity: Int
)

/**
 * One member drawn in the frame elevation: a rectangle (the [outline],
 * parallel to the centreline, spanning [bandMm] across it) + the [start]→[end]
 * centreline. The outline corners are derived once in the builder (pure
 * layout — node topology + section band in); the emitter only renders the
 * polyline, the centreline and the support symbols.
 */
data class FrameMemberGeometry(
    val mark: String,
    val memberType: String,      // COLUMN / BEAM / BRACE
    val materialType: String,    // CONCRETE / STEEL
    val sectionName: String,     // schedule label
    val bandMm: Double,          // thickness across the member axis
    val outline: List<Point2D>,  // 4 corners, parallel to the centreline
    val start: Point2D,          // node I (mm)
    val end: Point2D,            // node J (mm)
    val isSafe: Boolean,         // engine verdict (tone only)
    val utilization: Double      // engine utilization (note only)
)

/** A base support symbol: position + type, drawn by the emitter. */
data class FrameSupportGeometry(
    val xMm: Double, val yMm: Double,
    val supportType: String,     // FIXED / PIN / ROLLER / VERTICAL_ROLLER
    val halfWidthMm: Double      // schematic symbol half-width (from the largest column band)
)

/**
 * Frame elevation: the full in-span frame drawn from member centreline
 * topology (the app frame templates are rectilinear, so the elevation is a
 * rectilinear grid of column/beam rectangles). X spans the total bay length,
 * Y the total height; the base (support) level is y=0 with symbol and ground
 * hatches extending a little below. Bay-width dimensions below, story-height
 * dimensions to the right; the title block carries the frame-level note.
 */
data class FrameGeometry(
    val totalSpanMm: Double,
    val totalHeightMm: Double,
    val members: List<FrameMemberGeometry>,
    val supports: List<FrameSupportGeometry>,
    val isSafe: Boolean,             // all members safe → drawing note/state
    val maxUtilization: Double,      // engine max → drawing note
    val codeReference: String,       // code citation for title note/annotations
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §4b  Seismic chart geometry
 * ----------------------------------------------------------------------- */

/**
 * One response-spectrum point on the paper: the engine's (T, Sa) pair passed
 * through verbatim plus the normalized plot position derived once in the
 * builder (pure layout — curve range in, mm position out). The emitter only
 * renders the curve through [xMm], [yMm].
 */
data class SeismicChartSpectrumPoint(
    val period: Double,        // s — engine passthrough
    val acceleration: Double,  // g — engine passthrough
    val xMm: Double,           // normalized spectrum-pane x
    val yMm: Double            // normalized spectrum-pane y
)

/**
 * One floor's lateral-force bar: the engine force passed through verbatim plus
 * the bar's paper position (length ∝ force, centred on the floor's level).
 */
data class SeismicForceBar(
    val floorIndex: Int,       // engine 0-based floor index (label only)
    val forceKn: Double,       // kN — engine passthrough
    val floorHeightMm: Double, // vertical centre of the bar (floor level)
    val barHalfMm: Double,     // half the bar thickness
    val barLengthMm: Double    // horizontal length ∝ force
)

/**
 * Seismic chart sheet: two panes — the design response spectrum (Sa vs T) and
 * the lateral force distribution by floor. Values are the engine's own (base
 * shear, zone/soil/importance/reduction factors, fundamental period, spectrum
 * curve, per-floor forces); only the paper placement of curve points and bars
 * is derived. [isSafe] + [codeReference] drive the drawing state/note, and the
 * base-shear terms feed the parameter ledger in the sheet table.
 */
data class SeismicChartGeometry(
    val spectrumBox: BoundingBox,            // Sa–T plot pane
    val forceBox: BoundingBox,               // force-distribution pane
    val spectrumPoints: List<SeismicChartSpectrumPoint>,
    val forceBars: List<SeismicForceBar>,
    val maxPeriod: Double,                   // T-axis ceiling (s)
    val maxAcceleration: Double,             // Sa-axis ceiling (g)
    val fundamentalPeriod: Double,           // design T1 (s) — engine passthrough
    val spectralAccel: Double,               // Sa(T1) (g) — engine passthrough
    val baseShearKn: Double,                 // V (kN) — engine passthrough
    val zoneFactor: Double,                  // Z
    val soilFactor: Double,                  // S
    val importanceFactor: Double,            // I
    val responseModification: Double,        // R
    val calculationFormula: String,          // engine formula for the note
    val codeReference: String,               // code citation
    val isSafe: Boolean,                     // overall verdict (tone only)
    val warnings: List<String> = emptyList(),
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §4b  Beam elevation (support-cases matrix: loads + BMD/SFD per case)
 * ----------------------------------------------------------------------- */

/**
 * A single support symbol beneath a beam elevation.
 * @param kind one of "PIN", "ROLLER", "FIXED", "NONE" — the same string set
 *              ProfessionalBeamDrawing uses; "NONE" means a free (cantilever) end.
 */
data class BeamSupportSymbol(
    val kind: String,
    val xMm: Double,            // beam soffit X (drawing space, mm)
    val soffitY: Double,        // beam soffit level (drawing space, mm)
    val symbolHeightMm: Double  // vertical extent of the drawn symbol
)

/**
 * A UDL load arrow drawn above the beam. Values are layout-only: the shaft
 * runs from [shaftTopY] down to the arrowhead tip at [headY] (beam top edge).
 */
data class BeamLoadArrow(
    val xMm: Double,
    val shaftTopY: Double,
    val headY: Double
)

/** A single point of the normalized BMD/SFD polyline (drawing space, mm). */
data class BeamDiagramPoint(
    val xMm: Double,
    val yMm: Double
)

/**
 * Beam elevation drawing for the support-cases matrix.
 *
 * The beam member outline, per-case support symbols and UDL arrows sit in the
 * [BeamElevationGeometry.upPane]; the bending-moment and shear-force diagram
 * live in [momentPane] / [shearPane].
 *
 * All engineering values (span, appliedMoment, appliedShear, support type and
 * the safety verdict) pass straight through from the engine (Pillar 2: no
 * design formula is recomputed here). The curve ordinates are *normalized*
 * to the diagram panes, so the drawn shape is exactly the engine's envelope
 * shape, and the equivalent UDL feeding the load arrows (w = 2M/L² for the
 * cantilever, 12M/L² for fixed–fixed, 8M/L² otherwise) is only a paper label +
 * arrow-ratio back-calc.
 */
data class BeamElevationGeometry(
    val beamBox: BoundingBox,                // beam member outline pane
    val supports: List<BeamSupportSymbol>,   // drawn support symbols (≤ 2)
    val loadArrows: List<BeamLoadArrow>,     // UDL arrows above the beam
    val momentPane: BoundingBox,             // BMD pane (baseline = vertical centre)
    val shearPane: BoundingBox,              // SFD pane (baseline = vertical centre)
    val momentCurve: List<BeamDiagramPoint>,
    val shearCurve: List<BeamDiagramPoint>,
    val captionTop: String,                  // CASE / span / UDL line
    val captionBottom: String,               // Mmax / Vmax / verdict line
    val captionX: Double,
    val captionTopY: Double,
    val captionBottomY: Double,
    val spanMm: Double,                      // engine passthrough
    val appliedMomentKnM: Double,            // M (kN·m) — engine passthrough
    val appliedShearKn: Double,              // V (kN) — engine passthrough
    val supportTypeName: String,             // engine passthrough
    val isSafe: Boolean,                     // engine passthrough (tone only)
    /** R2 (P044): engine stirrup zones, placed verbatim along the member —
     *  dense at the support confinement zones, wider in the mid span. */
    val stirrupZones: List<StirrupZone> = emptyList(),
    /** R2 (P044): stirrup Ø (mm) for the elevation note fallback when a zone
     *  does not carry its own diameter. */
    val stirrupDiameter: Double = 8.0,
    val sectionBounds: BoundingBox
)

/** -----------------------------------------------------------------------
 *  §5  Reinforcement Set (the "identity" of every bar)
 * ----------------------------------------------------------------------- */

/**
 * Holds ALL reinforcement information for a complete drawing.
 * Every bar ever mentioned in any element is tracked here with full identity.
 * This is the "single source of truth" — PDF, DXF, and on-screen rendering
 * all read from this set; no bar is defined independently in each view.
 */
data class ReinforcementSet(
    /** All main tension bars (beams: bottom + top; columns: longitudinal) */
    val mainTensionBars: List<ReinforcementBar> = emptyList(),
    /** All main compression bars */
    val mainCompressionBars: List<ReinforcementBar> = emptyList(),
    /** All stirrups/ties across all elements, each with unique ID */
    val stirrups: List<ReinforcementBar> = emptyList(),
    /** All distribution/mesh bars */
    val distributionBars: List<ReinforcementBar> = emptyList()
) {
    /** Every bar in the model. */
    val all: List<ReinforcementBar> get() = mainTensionBars + mainCompressionBars + stirrups + distributionBars

    /** Map: bar_diameter -> list of bars of that diameter (for quick lookup). */
    val byDiameter: Map<Double, List<ReinforcementBar>> get() = all.groupBy { it.diameter }

    /** Total weight of all reinforcement (kg), computed once here. */
    val totalWeightKg: Double
        get() = all.sumOf { bar ->
            val unitWeightKgM = 0.006165 * bar.diameter * bar.diameter // kg/m
            (bar.totalLengthMm / 1000.0) * unitWeightKgM * bar.quantity
        }

    /** Map: element_id -> list of bar marks used in that element. */
    val elementBars: Map<String, List<String>> get() = all.groupBy({ it.element }, { it.mark })

    /** Convenience: human-readable bar schedule (BBS/PDF/DXF text). */
    val barSchedule: String
        get() = buildString {
            appendLine("BAR SCHEDULE")
            appendLine("────────────────────")
            all.sortedBy { it.diameter }.forEach { bar ->
                val spacing = bar.spacing?.let { " @${fmt(it)}mm" } ?: ""
                appendLine("${bar.mark}  Ø${diameterLabel(bar.diameter)}$spacing  L=${fmt(bar.totalLengthMm)}mm  Qty=${bar.quantity}")
            }
        }
}

/** A single reinforcing bar with full traceability. */
data class ReinforcementBar(
    val mark: String,               // e.g. "B-T1", "C-L1", "S1"
    val diameter: Double,           // mm
    val totalLengthMm: Double,      // total cut length
    val shape: String,              // "STRAIGHT", "HOOK_90", "HOOK_135", "HOOK_180"
    val element: String,            // which element this bar belongs to
    val codeReference: String,      // ECP §/ACI §/SBC §
    val quantity: Int,              // how many of this bar in this element
    val spacing: Double? = null,    // stirrup spacing or bar spacing (mm)
    val hookType: String? = null,   // "90°", "135°", "180°" if applicable
    val hookLength: Double? = null  // actual hook length (mm) if applicable
) {
    val totalLengthM: Double get() = totalLengthMm / 1000.0
}

/** -----------------------------------------------------------------------
 *  §6  Dimension Set
 * ----------------------------------------------------------------------- */

/**
 * All dimension lines for a drawing — derived from the model, never hand-drawn.
 * Each carries a code reference so the user can see which equation produced it.
 * (The line primitive itself lives in [GeometryEntities.DimensionLine].)
 */
data class DimensionSet(
    val beamDimensions: List<DimensionLine> = emptyList(),
    val columnDimensions: List<DimensionLine> = emptyList(),
    val slabDimensions: List<DimensionLine> = emptyList(),
    val footingDimensions: List<DimensionLine> = emptyList(),
    val stairDimensions: List<DimensionLine> = emptyList(),
    val tankDimensions: List<DimensionLine> = emptyList(),
    val retainingWallDimensions: List<DimensionLine> = emptyList(),
    val shearWallDimensions: List<DimensionLine> = emptyList(),
    val steelDimensions: List<DimensionLine> = emptyList(),
    val frameDimensions: List<DimensionLine> = emptyList()
) {
    val all: List<DimensionLine>
        get() = beamDimensions + columnDimensions + slabDimensions + footingDimensions +
            stairDimensions + tankDimensions + retainingWallDimensions + shearWallDimensions +
            steelDimensions + frameDimensions
}

/** -----------------------------------------------------------------------
 *  §7  Annotation Set
 * ----------------------------------------------------------------------- */

data class AnnotationLine(
    val id: String,
    val text: String,
    val position: Point2D,        // mm coordinates
    val height: Double = 3.0,     // text height mm
    val layer: String = "TEXT",   // DXF layer
    val codeReference: String? = null
)

data class AnnotationSet(
    val titleBlockAnnotations: List<AnnotationLine> = emptyList(),
    val generalAnnotations: List<AnnotationLine> = emptyList()
) {
    val all: List<AnnotationLine> get() = titleBlockAnnotations + generalAnnotations
}

/** -----------------------------------------------------------------------
 *  §8  Bounding Box
 * ----------------------------------------------------------------------- */

data class BoundingBox(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)

/** -----------------------------------------------------------------------
 *  §9  Builder / Validator
 * ----------------------------------------------------------------------- */

object DrawingModelBuilder {

    /**
     * Build a model for a beam from a core [ReinforcementResult].
     *
     * @param beamLength clear span (mm) — drives bar cut lengths and stirrup count.
     */
    fun buildBeam(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        overallHeight: Double,
        overallWidth: Double,
        effectiveDepth: Double,
        concreteCover: Double,
        beamLength: Double,
        beamResult: ReinforcementResult,
        supportTypeName: String = "HINGED_HINGED",
        appliedMomentKnM: Double = 0.0,
        appliedShearKn: Double = 0.0
    ): DrawingModel {

        val flexureRef = CodeReference.getReference(code, "BEAM_FLEXURE")
        val shearRef = CodeReference.getReference(code, "BEAM_SHEAR")

        val tensionBars = (0 until beamResult.numberOfBars).map { i ->
            ReinforcementBar(
                mark = "B-T${i + 1}",
                diameter = beamResult.barDiameter,
                totalLengthMm = beamLength + 2.0 * 12.0 * beamResult.barDiameter,
                shape = "HOOK_90",
                element = "beam",
                codeReference = flexureRef,
                quantity = 1,
                hookType = "90°",
                hookLength = 12.0 * beamResult.barDiameter
            )
        }

        val tiePerimeter = 2.0 * (overallWidth + overallHeight) - 8.0 * concreteCover
        // R2 (P044): with engine confinement zones present, each zone is emitted
        // with its own count/spacing/mark — the BBS schedule groups by (Ø,
        // length, spacing), so dense support zones surface as real '@X' rows.
        // Falls back to the single uniform spacing when zones are absent
        // (facade path). No strength recompute: count/spacing are pure layout.
        val stirrups = if (beamResult.zones.isNotEmpty()) {
            val dispositions = mutableListOf<ReinforcementBar>()
            val firstStirrupOffset = 50.0 // first stirrup 5 cm from the bearing face
            var seq = 0
            beamResult.zones.forEachIndexed { zi, zone ->
                val dia = if (zone.diameter > 0) zone.diameter.toDouble() else beamResult.tiesDiameter
                val zLen = (zone.endLocation - zone.startLocation).coerceAtLeast(0.0)
                val n = if (zone.spacing > 0) (zLen / zone.spacing).toInt() + 1 else 0
                repeat(n) { i ->
                    val offset = if (zi == 0) firstStirrupOffset else 0.0
                    if (zone.startLocation + offset + i * zone.spacing <= beamLength) {
                        seq += 1
                        dispositions += ReinforcementBar(
                            mark = "B-S$seq",
                            diameter = dia,
                            totalLengthMm = tiePerimeter + 2.0 * 12.0 * dia,
                            shape = "HOOK_135",
                            element = "beam",
                            codeReference = shearRef,
                            quantity = 1,
                            spacing = zone.spacing,
                            hookType = "135°",
                            hookLength = 12.0 * dia
                        )
                    }
                }
            }
            dispositions
        } else {
            val stirrupCount =
                if (beamResult.tiesSpacing > 0.0) kotlin.math.ceil(beamLength / beamResult.tiesSpacing).toInt() else 0
            (1..stirrupCount).map { i ->
                ReinforcementBar(
                    mark = "B-S$i",
                    diameter = beamResult.tiesDiameter,
                    totalLengthMm = tiePerimeter + 2.0 * 12.0 * beamResult.tiesDiameter,
                    shape = "HOOK_135",
                    element = "beam",
                    codeReference = shearRef,
                    quantity = 1,
                    spacing = beamResult.tiesSpacing,
                    hookType = "135°",
                    hookLength = 12.0 * beamResult.tiesDiameter
                )
            }
        }

        val sectionBounds = BoundingBox(0.0, 0.0, overallWidth, overallHeight)

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            beamSection = BeamSectionGeometry(
                overallHeight = overallHeight,
                overallWidth = overallWidth,
                effectiveDepth = effectiveDepth,
                concreteCover = concreteCover,
                tensionBars = tensionBars.map { bar ->
                    SectionBar(
                        diameter = bar.diameter,
                        position = concreteCover + bar.diameter / 2.0,
                        codeReference = bar.codeReference
                    )
                },
                compressionBars = emptyList(),
                stirrups = listOf(
                    StirrupGeometry(
                        diameter = beamResult.tiesDiameter,
                        spacing = beamResult.tiesSpacing,
                        hookType = "135°",
                        hookLength = 12.0 * beamResult.tiesDiameter,
                        codeReference = shearRef
                    )
                ),
                sectionBounds = sectionBounds
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = tensionBars,
                stirrups = stirrups
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (beamResult.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            ),
            beamElevation = if (appliedMomentKnM > 0.0 && beamLength > 0.0) {
                buildBeamElevation(
                    supportTypeName = supportTypeName,
                    spanMm = beamLength,
                    maxMomentKnM = appliedMomentKnM,
                    maxShearKn = appliedShearKn,
                    isSafe = beamResult.isSafe,
                    stirrupZones = beamResult.zones,
                    stirrupDiameter = beamResult.tiesDiameter
                )
            } else null
        )
    }

    /**
     * Build the beam-elevation drawing layer (support-cases matrix).
     *
     * Layout-derived only (Pillar 2): support symbols, UDL arrows and the
     * normalized BMD/SFD ordinates are placed in drawing space; the envelope
     * values (span, M, V, support type, verdict) pass straight through.
     *
     * Curve shapes mirror the engine case factors:
     *  - CANTILEVER:      M = −w(L−x)²/2  (fixed end hogging, free end zero)
     *  - FIXED_FIXED:     ends −wL²/12, mid +wL²/24
     *  - FIXED_HINGED:    fixed end −wL²/8, mid +9wL²/128 (~5L/8)
     *  - HINGED_HINGED / ROLLER_HINGED: parabola wL²/8
     *  - shear: linear, ±wL/2 (0.625·(+wL/2) at the fixed end for FIXED_HINGED).
     */
    fun buildBeamElevation(
        supportTypeName: String,
        spanMm: Double,
        maxMomentKnM: Double,
        maxShearKn: Double,
        isSafe: Boolean,
        stirrupZones: List<StirrupZone> = emptyList(),
        stirrupDiameter: Double = 8.0,
        elevationBox: BoundingBox = BoundingBox(30.0, 20.0, 460.0, 240.0)
    ): BeamElevationGeometry {

        val minX = elevationBox.minX + 40.0
        val maxX = elevationBox.maxX - 40.0
        val beamSoffitY = 140.0
        val beamDepth = 28.0
        val beamTopY = beamSoffitY + beamDepth
        val beamBox = BoundingBox(minX, beamSoffitY, maxX, beamTopY)

        val momentPane = BoundingBox(
            elevationBox.minX + 10.0, elevationBox.minY + 4.0,
            elevationBox.maxX - 20.0, elevationBox.minY + 44.0
        )
        val shearPane = BoundingBox(
            elevationBox.minX + 10.0, elevationBox.minY + 52.0,
            elevationBox.maxX - 20.0, elevationBox.minY + 92.0
        )

        fun supportKind(edge: String): String = when (edge) {
            "LEFT" -> when (supportTypeName) {
                "CANTILEVER", "FIXED_HINGED", "FIXED_FIXED" -> "FIXED"
                "ROLLER_HINGED" -> "ROLLER"
                else -> "PIN"
            }
            else -> when (supportTypeName) {
                "CANTILEVER" -> "NONE"
                "FIXED_FIXED" -> "FIXED"
                "HINGED_HINGED" -> "ROLLER"
                "ROLLER_HINGED" -> "PIN"
                else -> "ROLLER" // FIXED_HINGED hinged end
            }
        }

        val supports = buildList {
            val leftKind = supportKind("LEFT")
            if (leftKind != "NONE") add(BeamSupportSymbol(leftKind, minX, beamSoffitY, symbolHeightMm = 16.0))
            val rightKind = supportKind("RIGHT")
            if (rightKind != "NONE") add(BeamSupportSymbol(rightKind, maxX, beamSoffitY, symbolHeightMm = 16.0))
        }

        val loadArrows = (0 until 5).map { i ->
            val t = (i + 0.5) / 5.0
            BeamLoadArrow(
                xMm = minX + t * (maxX - minX),
                shaftTopY = beamTopY + 34.0,
                headY = beamTopY
            )
        }

        // R1: dimensionless case statics come from BeamDiagramStatics — the
        // single source shared by the screen drawing, the PDF generator and this
        // DXF elevation (no strength recompute: peak == engine envelope).
        val wEq = BeamDiagramStatics.equivalentUdl(supportTypeName, maxMomentKnM, spanMm / 1000.0)

        val n = 32
        val maxAbsM = BeamDiagramStatics.maxAbsMoment(supportTypeName)
        val maxAbsV = BeamDiagramStatics.maxAbsShear(supportTypeName)

        val momentBaseline = (momentPane.minY + momentPane.maxY) / 2.0
        val shearBaseline = (shearPane.minY + shearPane.maxY) / 2.0
        val mScale = if (maxAbsM > 0.0) ((momentPane.maxY - momentPane.minY) / 2.0) * 0.9 / maxAbsM else 0.0
        val vScale = if (maxAbsV > 0.0) ((shearPane.maxY - shearPane.minY) / 2.0) * 0.9 / maxAbsV else 0.0

        val momentCurve = (0..n).map { i ->
            val t = i.toDouble() / n
            BeamDiagramPoint(
                xMm = momentPane.minX + t * (momentPane.maxX - momentPane.minX),
                yMm = momentBaseline - BeamDiagramStatics.normalizedMoment(supportTypeName, t) * mScale // positive (sagging) drawn below (tension side)
            )
        }
        val shearCurve = (0..n).map { i ->
            val t = i.toDouble() / n
            BeamDiagramPoint(
                xMm = shearPane.minX + t * (shearPane.maxX - shearPane.minX),
                yMm = shearBaseline + BeamDiagramStatics.normalizedShear(supportTypeName, t) * vScale // positive shear drawn above the axis
            )
        }

        val captionX = elevationBox.minX + 6.0
        val captionTopY = elevationBox.maxY - 12.0
        val captionBottomY = elevationBox.maxY - 24.0
        val captionTop = "CASE: ${supportTypeName.replace('_', '-')}   L = ${fmt(spanMm)} mm   w (UDL)"
        // R2 (P044): concise stirrup distribution note (EN) appended to the
        // bottom caption — values pass straight through from the engine zones.
        val stirrupNote = if (stirrupZones.isNotEmpty()) {
            val support = stirrupZones.firstOrNull { it.name.contains("Support", ignoreCase = true) } ?: stirrupZones.first()
            val mid = stirrupZones.lastOrNull { it.name.contains("Mid", ignoreCase = true) } ?: stirrupZones.first()
            val d = if (support.diameter > 0) support.diameter else stirrupDiameter.toInt()
            "   \u00D8${d} @ ${support.spacing.toInt()}/${mid.spacing.toInt()} c/c  ${support.numLegs}-LEG"
        } else ""
        val captionBottom = "M max = ${fmt(maxMomentKnM)} kN.m   V max = ${fmt(maxShearKn)} kN   " +
            "${if (isSafe) "OK" else "NOT OK"}   w eq = ${fmt(wEq)} kN/m" + stirrupNote

        return BeamElevationGeometry(
            beamBox = beamBox,
            supports = supports,
            loadArrows = loadArrows,
            momentPane = momentPane,
            shearPane = shearPane,
            momentCurve = momentCurve,
            shearCurve = shearCurve,
            captionTop = captionTop,
            captionBottom = captionBottom,
            captionX = captionX,
            captionTopY = captionTopY,
            captionBottomY = captionBottomY,
            spanMm = spanMm,
            appliedMomentKnM = maxMomentKnM,
            appliedShearKn = maxShearKn,
            supportTypeName = supportTypeName,
            isSafe = isSafe,
            stirrupZones = stirrupZones,
            stirrupDiameter = stirrupDiameter,
            sectionBounds = elevationBox
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link from a real design.
     *
     * Maps a [BeamDesignFacade.BeamOutcome] into the [ReinforcementResult]
     * consumed by [buildBeam]. No formula is recomputed here: the flexure bar
     * selection and the shear stirrup sizing decided by the unified engines are
     * read as-is via [toReinforcementResult] (the single adapter), the member
     * geometry (which the outcome does not carry) is supplied so the stirrup
     * zones mirror the engine's confinement layout (R2/P044), and the
     * outcome's own sanity warnings are carried into the model.
     */
    fun buildBeamFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        overallHeight: Double,
        overallWidth: Double,
        effectiveDepth: Double,
        concreteCover: Double,
        beamLength: Double,
        outcome: BeamDesignFacade.BeamOutcome
    ): DrawingModel {
        val beamResult = outcome.toReinforcementResult(
            hMm = overallHeight, dMm = effectiveDepth, spanMm = beamLength
        )
        return buildBeam(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            overallHeight = overallHeight,
            overallWidth = overallWidth,
            effectiveDepth = effectiveDepth,
            concreteCover = concreteCover,
            beamLength = beamLength,
            beamResult = beamResult
        )
    }

    /**
     * Build a model for a tied column from a core [ReinforcementResult].
     *
     * @param columnLength floor-to-floor height (mm) — drives bar cut lengths
     * (incl. lap) and tie count.
     */
    fun buildColumn(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        overallHeight: Double,
        overallWidth: Double,
        concreteCover: Double,
        columnLength: Double,
        columnResult: ReinforcementResult
    ): DrawingModel {

        val axialRef = CodeReference.getReference(code, "COLUMN_AXIAL")
        val tieRef = CodeReference.getReference(code, "COLUMN_TIES")

        val longitudinalBars = (0 until columnResult.numberOfBars).map { i ->
            ReinforcementBar(
                mark = "C-L${i + 1}",
                diameter = columnResult.barDiameter,
                totalLengthMm = columnLength + 2.0 * 12.0 * columnResult.barDiameter,
                shape = "STRAIGHT",
                element = "column",
                codeReference = axialRef,
                quantity = 1
            )
        }

        val tiePerimeter = 2.0 * (overallWidth + overallHeight) - 8.0 * concreteCover
        val tieCount =
            if (columnResult.tiesSpacing > 0.0) kotlin.math.ceil(columnLength / columnResult.tiesSpacing).toInt() else 0
        val ties = (1..tieCount).map { i ->
            ReinforcementBar(
                mark = "C-T$i",
                diameter = columnResult.tiesDiameter,
                totalLengthMm = tiePerimeter + 2.0 * 12.0 * columnResult.tiesDiameter,
                shape = "HOOK_135",
                element = "column",
                codeReference = tieRef,
                quantity = 1,
                spacing = columnResult.tiesSpacing,
                hookType = "135°",
                hookLength = 12.0 * columnResult.tiesDiameter
            )
        }

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            columnSection = ColumnSectionGeometry(
                overallHeight = overallHeight,
                overallWidth = overallWidth,
                concreteCover = concreteCover,
                coreBars = emptyList(),
                outerBars = longitudinalBars.map { bar ->
                    SectionBar(
                        diameter = bar.diameter,
                        position = concreteCover + bar.diameter / 2.0,
                        codeReference = bar.codeReference
                    )
                },
                ties = listOf(
                    StirrupGeometry(
                        diameter = columnResult.tiesDiameter,
                        spacing = columnResult.tiesSpacing,
                        hookType = "135°",
                        hookLength = 12.0 * columnResult.tiesDiameter,
                        codeReference = tieRef
                    )
                ),
                sectionBounds = BoundingBox(0.0, 0.0, overallWidth, overallHeight)
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = longitudinalBars,
                stirrups = ties
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (columnResult.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link for a column.
     *
     * Maps a [com.civileg.core.engineering.UnifiedColumnDesign.Outcome] into
     * the [ReinforcementResult] consumed by [buildColumn] via the single
     * adapter [com.civileg.core.engineering.toReinforcementResult]; nothing is
     * recomputed and the outcome's sanity warnings are carried into the model.
     */
    fun buildColumnFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        overallHeight: Double,
        overallWidth: Double,
        concreteCover: Double,
        columnLength: Double,
        outcome: com.civileg.core.engineering.UnifiedColumnDesign.Outcome
    ): DrawingModel {
        return buildColumn(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            overallHeight = overallHeight,
            overallWidth = overallWidth,
            concreteCover = concreteCover,
            columnLength = columnLength,
            columnResult = outcome.toReinforcementResult()
        )
    }

    /**
     * Build a two-way slab model from the directional [SlabReinforcementResult]
     * produced by the unified slab design (via toSlabReinforcement).
     *
     * @param shortSpanMm / longSpanMm clear spans (mm) — drive bar cut lengths
     * (incl. lap) and the section bounds; passed as geometry, not recomputed.
     * Every mesh bar instance carries element = "slab" and a @spacing so the
     * per-bar identity survives into the RebarModel.
     */
    fun buildSlab(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        thickness: Double,
        effectiveDepth: Double,
        concreteCover: Double,
        shortSpanMm: Double,
        longSpanMm: Double,
        slab: com.civileg.core.engineering.SlabReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "SLAB_TWO_WAY")
        val (ns, ds) = slab.shortBarSelection
        val (nl, dl) = slab.longBarSelection

        fun meshBars(prefix: String, dia: Double, spacing: Double, count: Int, lengthMm: Double): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = lengthMm,
                    shape = "STRAIGHT",
                    element = "slab",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        // Mesh density: short-dir bars span the long direction (and vice versa).
        val shortCount = (longSpanMm / slab.shortSpacingMm).toInt()
        val longCount = (shortSpanMm / slab.longSpacingMm).toInt()

        val shortTop = meshBars("S-ST-T-", ds, slab.shortSpacingMm, shortCount, shortSpanMm + 2.0 * 12.0 * ds)
        val shortBottom = meshBars("S-ST-B-", ds, slab.shortSpacingMm, shortCount, shortSpanMm + 2.0 * 12.0 * ds)
        val longTop = meshBars("S-LT-T-", dl, slab.longSpacingMm, longCount, longSpanMm + 2.0 * 12.0 * dl)
        val longBottom = meshBars("S-LT-B-", dl, slab.longSpacingMm, longCount, longSpanMm + 2.0 * 12.0 * dl)

        val bottomFace = concreteCover + ds / 2.0
        val topFace = thickness - concreteCover - ds / 2.0

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            slabSection = SlabSectionGeometryTwoWay(
                thickness = thickness,
                effectiveDepth = effectiveDepth,
                concreteCover = concreteCover,
                shortTopBars = sectionBars(shortTop, topFace),
                shortBottomBars = sectionBars(shortBottom, bottomFace),
                longTopBars = sectionBars(longTop, topFace),
                longBottomBars = sectionBars(longBottom, bottomFace),
                sectionBounds = BoundingBox(0.0, 0.0, longSpanMm, shortSpanMm)
            ),
            reinforcement = ReinforcementSet(distributionBars = shortTop + shortBottom + longTop + longBottom),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (slab.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Build a flat-slab panel model from the strip [FlatSlabReinforcementResult]
     * filled by the app's live adapter (passthrough of the FlatSlabResult's four
     * strip groups).
     *
     * @param shortSpanMm / longSpanMm panel clear spans lx/ly (mm) — drive bar
     * cut lengths (incl. lap) and the section bounds; passed as geometry, not
     * recomputed.
     * @param columnStripWidthMm engine column-strip band width along the long
     * span (columnStripWidthX) — the emitter splits the panel at this width to
     * zone the strip rebar groups.
     * @param dropDepthMm / dropSizeMm input drop panel (0/0 = none) — schematic
     * top-face outline only.
     * Strip bars run the SHORT panel span (the X-direction strips), exactly as
     * the flat-slab engine designs them for the lx direction; each instance
     * carries element = "flatSlab" and a @spacing so the per-bar identity
     * survives into the RebarModel.
     */
    fun buildFlatSlab(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        thickness: Double,
        effectiveDepth: Double,
        concreteCover: Double,
        shortSpanMm: Double,
        longSpanMm: Double,
        columnStripWidthMm: Double,
        dropDepthMm: Double,
        dropSizeMm: Double,
        slab: com.civileg.core.engineering.FlatSlabReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "SLAB_FLAT")
        val (nCT, dCT) = slab.columnStripTop.barSelection
        val (nCB, dCB) = slab.columnStripBottom.barSelection
        val (nMT, dMT) = slab.middleStripTop.barSelection
        val (nMB, dMB) = slab.middleStripBottom.barSelection

        fun stripBars(prefix: String, dia: Double, spacing: Double, count: Int, barLengthMm: Double): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = barLengthMm,
                    shape = "STRAIGHT",
                    element = "flatSlab",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        // Strip bars run the short panel span; lap = 12Ø added each end (mesh convention).
        val colTop = stripBars("FL-CS-T-", dCT, slab.columnStripTop.spacingMm, nCT, shortSpanMm + 2.0 * 12.0 * dCT)
        val colBottom = stripBars("FL-CS-B-", dCB, slab.columnStripBottom.spacingMm, nCB, shortSpanMm + 2.0 * 12.0 * dCB)
        val midTop = stripBars("FL-MS-T-", dMT, slab.middleStripTop.spacingMm, nMT, shortSpanMm + 2.0 * 12.0 * dMT)
        val midBottom = stripBars("FL-MS-B-", dMB, slab.middleStripBottom.spacingMm, nMB, shortSpanMm + 2.0 * 12.0 * dMB)

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            slabSection = SlabSectionGeometryFlat(
                thickness = thickness,
                effectiveDepth = effectiveDepth,
                concreteCover = concreteCover,
                columnStripWidthMm = columnStripWidthMm,
                columnStripTopBars = sectionBars(colTop, thickness - concreteCover - dCT / 2.0),
                columnStripBottomBars = sectionBars(colBottom, concreteCover + dCB / 2.0),
                middleStripTopBars = sectionBars(midTop, thickness - concreteCover - dMT / 2.0),
                middleStripBottomBars = sectionBars(midBottom, concreteCover + dMB / 2.0),
                dropBars = emptyList(),
                dropDepth = dropDepthMm,
                dropSize = dropSizeMm,
                sectionBounds = BoundingBox(0.0, 0.0, longSpanMm, shortSpanMm)
            ),
            reinforcement = ReinforcementSet(distributionBars = colTop + colBottom + midTop + midBottom),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (slab.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link for a slab.
     *
     * Maps a [com.civileg.core.engineering.UnifiedSlabDesign.Outcome] into the
     * directional [com.civileg.core.engineering.SlabReinforcementResult] via the
     * single adapter [com.civileg.core.engineering.toSlabReinforcement]; nothing
     * is recomputed and the outcome's sanity warnings are carried into the model.
     */
    fun buildSlabFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        thickness: Double,
        effectiveDepth: Double,
        concreteCover: Double,
        shortSpanMm: Double,
        longSpanMm: Double,
        outcome: com.civileg.core.engineering.UnifiedSlabDesign.Outcome
    ): DrawingModel {
        return buildSlab(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            thickness = thickness,
            effectiveDepth = effectiveDepth,
            concreteCover = concreteCover,
            shortSpanMm = shortSpanMm,
            longSpanMm = longSpanMm,
            slab = outcome.toSlabReinforcement()
        )
    }

    /**
     * Build an isolated-footing model from the directional
     * [com.civileg.core.engineering.FootingReinforcementResult] produced by the
     * unified footing engine (via toFootingReinforcement).
     *
     * @param lengthMm / widthMm plan dimensions (mm) — the section bounds;
     * @param thicknessMm total depth (mm); @param concreteCover clear cover (mm).
     * Bottom mesh in both directions + the distribution (top) mesh when present.
     * Every mesh bar instance carries element = "footing" and a @spacing so the
     * per-bar identity survives into the RebarModel.
     */
    fun buildFooting(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        lengthMm: Double,
        widthMm: Double,
        thicknessMm: Double,
        concreteCover: Double,
        footing: com.civileg.core.engineering.FootingReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "FOOTING")

        fun meshBars(prefix: String, dia: Double, spacing: Double, count: Int, barLengthMm: Double): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = barLengthMm,
                    shape = "STRAIGHT",
                    element = "footing",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        val (ns, ds) = footing.shortBarSelection
        val (nl, dl) = footing.longBarSelection

        // Short-dir bottom bars run along the width, distributed over the length.
        val shortCount = (lengthMm / footing.shortSpacingMm).toInt()
        val longCount = (widthMm / footing.longSpacingMm).toInt()
        val shortBottom = meshBars("F-SB-", ds, footing.shortSpacingMm, shortCount, widthMm + 2.0 * 12.0 * ds)
        val longBottom = meshBars("F-LB-", dl, footing.longSpacingMm, longCount, lengthMm + 2.0 * 12.0 * dl)

        val distribution = footing.distribution
        val distBottom = emptyList<ReinforcementBar>()
        val distTop = if (distribution != null && distribution.spacingMm > 0.0) {
            val distCount = (widthMm / distribution.spacingMm).toInt()
            meshBars("F-DT-", distribution.diameterMm, distribution.spacingMm, distCount, lengthMm + 2.0 * 12.0 * distribution.diameterMm)
        } else {
            emptyList()
        }

        val bottomFace = concreteCover + maxOf(ds, dl) / 2.0
        val topFace = thicknessMm - concreteCover - (distribution?.diameterMm ?: ds) / 2.0

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            footingSection = FootingSectionGeometry(
                length = lengthMm,
                width = widthMm,
                thickness = thicknessMm,
                concreteCover = concreteCover,
                bottomBars = sectionBars(shortBottom + longBottom, bottomFace) + sectionBars(distBottom, bottomFace),
                topBars = sectionBars(distTop, topFace),
                anchorBars = null,
                sectionBounds = BoundingBox(0.0, 0.0, lengthMm, widthMm)
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = shortBottom + longBottom,
                distributionBars = distTop
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (footing.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link for a footing.
     *
     * Maps a [com.civileg.core.engineering.UnifiedFootingDesign.Outcome] into
     * the directional [com.civileg.core.engineering.FootingReinforcementResult]
     * via the single adapter [com.civileg.core.engineering.toFootingReinforcement];
     * nothing is recomputed and the outcome's sanity warnings are carried into
     * the model. Geometry (length/width/thickness) comes from the outcome itself.
     *
     * @param thicknessMm optional total depth override (mm); default = outcome.
     */
    fun buildFootingFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        concreteCover: Double,
        outcome: com.civileg.core.engineering.UnifiedFootingDesign.Outcome,
        thicknessMm: Double = outcome.requiredThickness
    ): DrawingModel {
        return buildFooting(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            lengthMm = outcome.requiredLength,
            widthMm = outcome.requiredWidth,
            thicknessMm = thicknessMm,
            concreteCover = concreteCover,
            footing = outcome.toFootingReinforcement()
        )
    }

    /**
     * Build a waist-slab model from the [StairReinforcementResult] produced by
     * the unified stair engine (via toStairReinforcement).
     *
     * @param stairWidthMm clear width (mm); @param inclinedLengthMm flight length
     * on the slope (mm) — drive bar counts and cut lengths, passed as geometry,
     * not recomputed. Longitudinal main bars run along the slope and are spread
     * over the width; transverse distribution bars run across the width and are
     * spread over the inclined length. Every bar instance carries element =
     * "stair" and a @spacing so the per-bar identity survives into the RebarModel.
     */
    fun buildStair(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        waistThicknessMm: Double,
        effectiveDepthMm: Double,
        concreteCoverMm: Double,
        stairWidthMm: Double,
        inclinedLengthMm: Double,
        stair: com.civileg.core.engineering.StairReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "STAIR")

        fun meshBars(prefix: String, dia: Double, spacing: Double, count: Int, barLengthMm: Double): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = barLengthMm,
                    shape = "STRAIGHT",
                    element = "stair",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        val mainCount = if (stair.mainSpacingMm > 0.0) (stairWidthMm / stair.mainSpacingMm).toInt() else 0
        val distCount = if (stair.distributionSpacingMm > 0.0) (inclinedLengthMm / stair.distributionSpacingMm).toInt() else 0

        val mainBars = meshBars("S-M-", stair.mainDiameter, stair.mainSpacingMm, mainCount, inclinedLengthMm + 2.0 * 12.0 * stair.mainDiameter)
        val distBars = meshBars("S-D-", stair.distributionDiameter, stair.distributionSpacingMm, distCount, stairWidthMm + 2.0 * 12.0 * stair.distributionDiameter)

        val bottomFace = concreteCoverMm + stair.mainDiameter / 2.0
        val topFace = waistThicknessMm - concreteCoverMm - stair.distributionDiameter / 2.0

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            stairSection = StairSectionGeometry(
                waistThickness = waistThicknessMm,
                effectiveDepth = effectiveDepthMm,
                concreteCover = concreteCoverMm,
                mainBars = sectionBars(mainBars, bottomFace),
                distributionBars = sectionBars(distBars, topFace),
                stirrups = emptyList(),
                sectionBounds = BoundingBox(0.0, 0.0, stairWidthMm, waistThicknessMm)
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = if (stair.mainSpacingMm > 0.0) mainBars else emptyList(),
                distributionBars = distBars
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (stair.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link for a staircase.
     *
     * Maps a [com.civileg.core.engineering.UnifiedStairDesign.Outcome] into the
     * [com.civileg.core.engineering.StairReinforcementResult] via the single
     * adapter [com.civileg.core.engineering.toStairReinforcement]; nothing is
     * recomputed and the outcome's sanity warnings are carried into the model.
     * Geometry (waist thickness / depth) comes from the outcome itself.
     */
    fun buildStairFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        concreteCoverMm: Double,
        stairWidthM: Double,
        outcome: com.civileg.core.engineering.UnifiedStairDesign.Outcome
    ): DrawingModel {
        return buildStair(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            waistThicknessMm = outcome.waistThickness,
            effectiveDepthMm = outcome.effectiveDepth,
            concreteCoverMm = concreteCoverMm,
            stairWidthMm = stairWidthM * 1000.0,
            inclinedLengthMm = outcome.inclinedLength * 1000.0,
            stair = outcome.toStairReinforcement()
        )
    }

    /**
     * Build a model for a water-retaining tank from a core
     * [com.civileg.core.engineering.TankReinforcementResult].
     *
     * Three reinforcement families are scheduled: wall vertical (bars climbing
     * the wall around the perimeter), wall horizontal (bars wrapping the
     * perimeter), and base bars (tension steel near the bottom face). Mark
     * prefixes: T-WV-, T-WH-, T-B-. Every bar instance carries element = "tank"
     * and a @spacing so the per-bar identity survives into the RebarModel.
     */
    fun buildTank(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        lengthMm: Double,
        widthMm: Double,
        heightMm: Double,
        wallThicknessMm: Double,
        baseThicknessMm: Double,
        effectiveDepthMm: Double,
        concreteCoverMm: Double,
        tank: com.civileg.core.engineering.TankReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "TANK")

        fun meshBars(prefix: String, dia: Double, spacing: Double, count: Int, barLengthMm: Double): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = barLengthMm,
                    shape = "STRAIGHT",
                    element = "tank",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        val wallPerimeterMm = 2.0 * (lengthMm + widthMm)
        val vertCount = if (tank.wallSpacingMm > 0.0) (wallPerimeterMm / tank.wallSpacingMm).toInt() else 0
        val horizCount = if (tank.wallHorizontalSpacingMm > 0.0) (heightMm / tank.wallHorizontalSpacingMm).toInt() else 0
        val baseCount = if (tank.baseSpacingMm > 0.0) (lengthMm / tank.baseSpacingMm).toInt() else 0

        val wallVertical = meshBars("T-WV-", tank.wallDiameter, tank.wallSpacingMm, vertCount, heightMm + 2.0 * 12.0 * tank.wallDiameter)
        val wallHorizontal = meshBars("T-WH-", tank.wallHorizontalDiameter, tank.wallHorizontalSpacingMm, horizCount, wallPerimeterMm + 2.0 * 12.0 * tank.wallHorizontalDiameter)
        val base = meshBars("T-B-", tank.baseDiameter, tank.baseSpacingMm, baseCount, lengthMm + 2.0 * 12.0 * tank.baseDiameter)

        val innerFace = concreteCoverMm + tank.wallDiameter / 2.0
        val outerFace = wallThicknessMm - concreteCoverMm - tank.wallHorizontalDiameter / 2.0
        val baseFace = concreteCoverMm + tank.baseDiameter / 2.0

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            tankSection = TankSectionGeometry(
                wallThickness = wallThicknessMm,
                baseThickness = baseThicknessMm,
                effectiveDepth = effectiveDepthMm,
                concreteCover = concreteCoverMm,
                wallVerticalBars = sectionBars(wallVertical, innerFace),
                wallHorizontalBars = sectionBars(wallHorizontal, outerFace),
                baseBars = sectionBars(base, baseFace),
                sectionBounds = BoundingBox(0.0, 0.0, maxOf(lengthMm, widthMm), wallThicknessMm + baseThicknessMm)
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = if (tank.wallSpacingMm > 0.0) wallVertical else emptyList(),
                distributionBars = (if (tank.wallHorizontalSpacingMm > 0.0) wallHorizontal else emptyList()) +
                    (if (tank.baseSpacingMm > 0.0) base else emptyList())
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (tank.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link for a water-retaining
     * tank.
     *
     * Maps a [com.civileg.core.engineering.UnifiedTankDesign.Outcome] into the
     * [com.civileg.core.engineering.TankReinforcementResult] via the single
     * adapter [com.civileg.core.engineering.toTankReinforcement]; nothing is
     * recomputed and the outcome's sanity warnings are carried into the model.
     * Geometry (wall/base thickness, wall effective depth) comes from the outcome.
     */
    fun buildTankFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        concreteCoverMm: Double,
        outcome: com.civileg.core.engineering.UnifiedTankDesign.Outcome
    ): DrawingModel {
        return buildTank(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            lengthMm = lengthM * 1000.0,
            widthMm = widthM * 1000.0,
            heightMm = heightM * 1000.0,
            wallThicknessMm = outcome.wallThickness,
            baseThicknessMm = outcome.baseThickness,
            effectiveDepthMm = outcome.wallEffectiveDepth,
            concreteCoverMm = concreteCoverMm,
            tank = outcome.toTankReinforcement()
        )
    }

    /**
     * Build a cantilever earth-retaining wall model from a core
     * [com.civileg.core.engineering.RetainingWallReinforcementResult].
     *
     * Four reinforcement families are scheduled over the 1 m run: stem main
     * (vertical flexure bars on the earth face), stem distribution (transverse
     * bars up the stem), toe bottom bars, and heel top bars. The DXF exporter
     * lays the section out [toe][stem/base][heel]; mark prefixes: R-SM-, R-D-,
     * R-T-, R-H-. Every bar instance carries element = "retainingWall" and a
     * @spacing so the per-bar identity survives into the RebarModel.
     *
     * @param stemHeightMm free vertical height of the stem (H − tFooting) —
     *        drives stem bar cut length (embedment into the base) and the
     *        distribution-bar count along the stem.
     */
    fun buildRetainingWall(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        wallHeightMm: Double,
        stemHeightMm: Double,
        stemBaseThicknessMm: Double,
        baseWidthMm: Double,
        baseThicknessMm: Double,
        toeLengthMm: Double,
        heelLengthMm: Double,
        concreteCoverMm: Double,
        wall: com.civileg.core.engineering.RetainingWallReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "RETAINING_WALL")

        fun bars(prefix: String, dia: Double, spacing: Double, count: Int, barLengthMm: Double, hooks: Boolean): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = barLengthMm,
                    shape = if (hooks) "HOOK_90" else "STRAIGHT",
                    element = "retainingWall",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing,
                    hookType = if (hooks) "90°" else null,
                    hookLength = if (hooks) 12.0 * dia else null
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        val stemMain = if (wall.stemMainSpacingMm > 0.0)
            bars("R-SM-", wall.stemMainDiameter, wall.stemMainSpacingMm, wall.stemMainCount,
                stemHeightMm + baseThicknessMm, hooks = false)
        else emptyList()

        val distCount = if (wall.distributionSpacingMm > 0.0) (stemHeightMm / wall.distributionSpacingMm).toInt() else 0
        val distribution = if (distCount > 0)
            bars("R-D-", wall.distributionDiameter, wall.distributionSpacingMm, distCount,
                1000.0 + 2.0 * 12.0 * wall.distributionDiameter, hooks = false)
        else emptyList()

        val toe = if (wall.toeSpacingMm > 0.0)
            bars("R-T-", wall.toeDiameter, wall.toeSpacingMm, wall.toeBarsCount,
                toeLengthMm + 2.0 * 12.0 * wall.toeDiameter, hooks = true)
        else emptyList()

        val heel = if (wall.heelSpacingMm > 0.0)
            bars("R-H-", wall.heelDiameter, wall.heelSpacingMm, wall.heelBarsCount,
                heelLengthMm + 2.0 * 12.0 * wall.heelDiameter, hooks = true)
        else emptyList()

        val stemFace = stemBaseThicknessMm - concreteCoverMm - wall.stemMainDiameter / 2.0
        val outerFace = concreteCoverMm + wall.distributionDiameter / 2.0
        val bottomFace = concreteCoverMm + wall.toeDiameter / 2.0
        val topFace = baseThicknessMm - concreteCoverMm - wall.heelDiameter / 2.0

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            retainingWallSection = RetainingWallSectionGeometry(
                wallHeight = wallHeightMm,
                stemBaseThickness = stemBaseThicknessMm,
                baseWidth = baseWidthMm,
                baseThickness = baseThicknessMm,
                toeLength = toeLengthMm,
                heelLength = heelLengthMm,
                concreteCover = concreteCoverMm,
                stemMainBars = sectionBars(stemMain, stemFace),
                distributionBars = sectionBars(distribution, outerFace),
                toeBars = sectionBars(toe, bottomFace),
                heelBars = sectionBars(heel, topFace),
                sectionBounds = BoundingBox(0.0, 0.0, baseWidthMm, wallHeightMm)
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = if (wall.stemMainSpacingMm > 0.0) stemMain else emptyList(),
                distributionBars = distribution + toe + heel
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (wall.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Adapter — the roadmap's "feeds ReinforcementSet" link for an earth-retaining
     * wall.
     *
     * Maps a [com.civileg.core.engineering.UnifiedRetainingWallDesign.Outcome]
     * into the [com.civileg.core.engineering.RetainingWallReinforcementResult]
     * via the single adapter [com.civileg.core.engineering.toRetainingWallReinforcement];
     * nothing is recomputed and the outcome's sanity warnings are carried into the
     * model. Wall geometry follows the app's RetainingWallInput units (metres).
     */
    fun buildRetainingWallFromFacade(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        wallHeightM: Double,
        stemBaseThicknessM: Double,
        baseWidthM: Double,
        baseThicknessM: Double,
        toeLengthM: Double,
        heelLengthM: Double,
        concreteCoverMm: Double,
        outcome: com.civileg.core.engineering.UnifiedRetainingWallDesign.Outcome
    ): DrawingModel {
        return buildRetainingWall(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            code = code,
            edition = edition,
            wallHeightMm = wallHeightM * 1000.0,
            stemHeightMm = (wallHeightM - baseThicknessM) * 1000.0,
            stemBaseThicknessMm = stemBaseThicknessM * 1000.0,
            baseWidthMm = baseWidthM * 1000.0,
            baseThicknessMm = baseThicknessM * 1000.0,
            toeLengthMm = toeLengthM * 1000.0,
            heelLengthMm = heelLengthM * 1000.0,
            concreteCoverMm = concreteCoverMm,
            wall = outcome.toRetainingWallReinforcement()
        )
    }

    /**
     * Build a shear-wall horizontal-section model from the family
     * [com.civileg.core.engineering.ShearWallReinforcementResult] filled by the
     * app's live adapter (pure passthrough of the ShearWallResult families).
     *
     * Steel identity:
     *   • webVertical — the engine's total longitudinal count, distributed along
     *     the wall length at the thickness centre (the plan cut intersects every
     *     vertical bar); one splice length added per supply bar.
     *   • horizontalFace — one story-run of horizontal (shear) steel per thickness
     *     face; a bar runs the full wall length with 2×12Ø hooks.
     *   • boundary — the end-zone vertical steel (both ends) is SCHEDULED (not
     *     re-drawn; the web row already crosses the zones) and its confinement
     *     ties are carried for the emitter's end-zone loop.
     *   • couplingBeam — diagonal + transverse steel of a coupled wall, scheduled
     *     only (not part of the plan section). Diagonal cut length is the beam
     *     diagonal √(clear² + height²); transverse is the bundle tie perimeter.
     *
     * A flange leg (optional, schematic) mirrors the canvas renderer's
     * ProfessionalShearWallDrawing proportions: L → projection 3t, leg 0.8t at
     * the top-left; T → projection 0.8L, leg 0.8t centred on the top face. It is
     * absorbed into [ShearWallSectionGeometry.sectionBounds]. Mark prefixes:
     * SW-V-, SW-H-, SW-B-, SW-CB-D-, SW-CB-T-; element = "shearWall".
     */
    fun buildShearWall(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        wallLengthMm: Double,
        wallThicknessMm: Double,
        storyHeightMm: Double,
        couplingBeamClearSpanMm: Double,
        couplingBeamHeightMm: Double,
        boundaryElementLengthMm: Double,
        wallShape: String,
        concreteCoverMm: Double,
        wall: com.civileg.core.engineering.ShearWallReinforcementResult
    ): DrawingModel {

        val ref = CodeReference.getReference(code, "SHEAR_WALL")
        val vertical = wall.vertical
        val horizontal = wall.horizontal

        fun meshBars(prefix: String, dia: Double, spacing: Double?, count: Int, barLengthMm: Double, hooks: Boolean): List<ReinforcementBar> =
            (0 until count.coerceAtLeast(1)).map { i ->
                ReinforcementBar(
                    mark = "$prefix${i + 1}",
                    diameter = dia,
                    totalLengthMm = barLengthMm,
                    shape = if (hooks) "HOOK_90" else "STRAIGHT",
                    element = "shearWall",
                    codeReference = ref,
                    quantity = 1,
                    spacing = spacing,
                    hookType = if (hooks) "90°" else null,
                    hookLength = if (hooks) 12.0 * dia else null
                )
            }

        fun sectionBars(bars: List<ReinforcementBar>, face: Double): List<SectionBar> =
            bars.map { SectionBar(diameter = it.diameter, position = face, codeReference = it.codeReference) }

        val verticalCount = vertical.count.coerceAtLeast(2)
        val webVertical = meshBars(
            "SW-V-", vertical.diameterMm, vertical.spacingMm, verticalCount,
            storyHeightMm + 12.0 * vertical.diameterMm, hooks = false
        )

        // One story-run per face: the plan cut crosses the horizontal layers near
        // the two thickness faces, mirroring the renderer's edge-face steel.
        val faceCount = (wallLengthMm / horizontal.spacingMm.coerceAtLeast(50.0)).toInt().coerceAtLeast(3)
        val hDia = horizontal.diameterMm.coerceAtLeast(1.0)
        val bottomFace = concreteCoverMm + hDia / 2.0
        val topFace = wallThicknessMm - concreteCoverMm - hDia / 2.0
        val bottomFaceBars = meshBars(
            "SW-H-", hDia, horizontal.spacingMm, faceCount,
            wallLengthMm + 2.0 * 12.0 * hDia, hooks = true
        )
        val topFaceBars = meshBars(
            "SW-H-", hDia, horizontal.spacingMm, faceCount,
            wallLengthMm + 2.0 * 12.0 * hDia, hooks = true
        )

        // Boundary element: end-zone vertical steel scheduled (both ends); the
        // confinement ties drive the emitter's end-zone loop when required.
        val boundaryBars = if (wall.boundary != null && boundaryElementLengthMm > 0) {
            val b = wall.boundary
            meshBars(
                "SW-B-", b.diameterMm, b.spacingMm, b.bars.coerceAtLeast(2) * 2,
                storyHeightMm + 12.0 * b.diameterMm, hooks = false
            )
        } else {
            emptyList()
        }
        val boundaryTies = if (wall.boundary != null && boundaryElementLengthMm > 0) {
            val b = wall.boundary
            listOf(
                StirrupGeometry(
                    diameter = b.diameterMm,
                    spacing = b.spacingMm,
                    hookType = "135°",
                    hookLength = 12.0 * b.diameterMm,
                    codeReference = ref
                )
            )
        } else {
            emptyList()
        }

        // Coupling beam (schedule only): the diagonal + transverse bundle steel.
        val couplingDiagonal = wall.couplingBeam?.let { cb ->
            val diagLength = if (couplingBeamClearSpanMm > 0)
                kotlin.math.sqrt(
                    couplingBeamClearSpanMm * couplingBeamClearSpanMm +
                        couplingBeamHeightMm * couplingBeamHeightMm
                )
            else couplingBeamHeightMm
            meshBars(
                "SW-CB-D-", cb.diagonalDiameterMm, spacing = null,
                cb.diagonalBars.coerceAtLeast(2), diagLength, hooks = false
            )
        } ?: emptyList()
        val couplingTransverse = wall.couplingBeam?.let { cb ->
            val tiePerimeter = 2.0 * (couplingBeamHeightMm + wallThicknessMm) - 8.0 * concreteCoverMm
            val tieCount = if (couplingBeamClearSpanMm > 0)
                (couplingBeamClearSpanMm / cb.transverseSpacingMm).toInt().coerceAtLeast(1)
            else 1
            meshBars(
                "SW-CB-T-", cb.transverseDiameterMm.toDouble(), cb.transverseSpacingMm.toDouble(),
                tieCount, tiePerimeter, hooks = true
            )
        } ?: emptyList()

        // Flange leg — schematic, mirroring the canvas renderer's proportions.
        val flange = when (wallShape) {
            "L-shaped" -> ShearWallFlange(
                shape = "L-shaped",
                projectionMm = 3.0 * wallThicknessMm,
                thicknessMm = 0.8 * wallThicknessMm
            )
            "T-shaped" -> ShearWallFlange(
                shape = "T-shaped",
                projectionMm = 0.8 * wallLengthMm,
                thicknessMm = 0.8 * wallThicknessMm
            )
            else -> null
        }

        val boundsMinX = if (flange?.shape == "L-shaped") -flange.projectionMm else 0.0
        val boundsMaxY = if (flange?.shape == "T-shaped")
            wallThicknessMm + flange.thicknessMm
        else wallThicknessMm

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            shearWallSection = ShearWallSectionGeometry(
                wallLength = wallLengthMm,
                wallThickness = wallThicknessMm,
                concreteCover = concreteCoverMm,
                webVerticalBars = sectionBars(webVertical, wallThicknessMm / 2.0),
                horizontalFaceBars = sectionBars(bottomFaceBars, bottomFace) +
                    sectionBars(topFaceBars, topFace),
                boundaryElementLengthMm = boundaryElementLengthMm,
                boundaryTies = boundaryTies,
                flange = flange,
                sectionBounds = BoundingBox(
                    minX = boundsMinX,
                    minY = 0.0,
                    maxX = wallLengthMm,
                    maxY = boundsMaxY
                )
            ),
            reinforcement = ReinforcementSet(
                mainTensionBars = webVertical + boundaryBars + couplingDiagonal,
                distributionBars = bottomFaceBars + topFaceBars,
                stirrups = couplingTransverse
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (wall.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Build a steel-member model: a long elevation box (member length × depth)
     * with flange-interface lines, plus a schematic cut A-A to its right showing
     * the profile plates. Layout mirrors the canvas renderer's
     * [ProfessionalSteelDrawing]: the elevation occupies x∈[0,L], y∈[0,d]; cut
     * A-A is centred in a reserved gap of 60 mm to its right (plate geometry
     * conveyed to the emitter via depth/width/web-flange quantities — the
     * emitter renders flange + web plates from those five numbers). No bars:
     * [ReinforcementSet] stays empty; the sheet table becomes a steel member
     * schedule (MARK / SECTION / LENGTH / QTY) and the qualitative check status
     * rides on [DrawingState] + a note annotation. All dimension/annotation
     * endpoints and the A-A plates are absorbed into the bounds; the member's
     * steel citation is the engine's own passed-through [SteelMemberReinforcementResult.codeReference].
     */
    fun buildSteelMember(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        member: com.civileg.core.engineering.SteelMemberReinforcementResult,
        steelMembers: List<SteelMemberMark> = emptyList()
    ): DrawingModel {
        val L = member.memberLengthMm.coerceAtLeast(1.0)
        val d = member.depthMm.coerceAtLeast(1.0)
        val w = member.widthMm.coerceAtLeast(1.0)
        val tw = member.webThicknessMm.coerceIn(1.0, d / 2.0)
        val tf = member.flangeThicknessMm.coerceIn(1.0, d / 2.0)

        // Layout: elevation left (x∈[0,L], y∈[0,d]), cut A-A to the right.
        val gapA = 60.0
        val cx = L + gapA + w / 2.0
        val sectionBounds = BoundingBox(0.0, 0.0, L + gapA + w, d)

        val note = "UX = ${fmt(member.utilizationRatio)} ${if (member.isSafe) "OK" else "NOT OK"}"
        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            steelSection = SteelSectionGeometry(
                sectionName = member.sectionName,
                memberLengthMm = L,
                depthMm = d,
                widthMm = w,
                webThicknessMm = tw,
                flangeThicknessMm = tf,
                isSafe = member.isSafe,
                utilizationRatio = member.utilizationRatio,
                codeReference = member.codeReference,
                sectionBounds = sectionBounds
            ),
            steelMembers = steelMembers,
            dimensions = DimensionSet(
                steelDimensions = listOf(
                    // Elevation length (below the box).
                    DimensionLine(Point2D(0.0, -30.0), Point2D(L, -30.0), fmt(L)),
                    // Cut depth (right of the A-A plates).
                    DimensionLine(
                        Point2D(cx + w / 2.0 + 15.0, 0.0),
                        Point2D(cx + w / 2.0 + 15.0, d),
                        fmt(d)
                    ),
                    // Cut width (above the A-A plates).
                    DimensionLine(
                        Point2D(cx - w / 2.0, d + 20.0),
                        Point2D(cx + w / 2.0, d + 20.0),
                        fmt(w)
                    )
                )
            ),
            annotations = AnnotationSet(
                generalAnnotations = listOf(
                    AnnotationLine(
                        id = "STEEL_ELEVATION",
                        text = "ELEVATION",
                        position = Point2D(L / 2.0, d + 50.0),
                        height = 3.0,
                        layer = "TEXT",
                        codeReference = member.codeReference
                    ),
                    AnnotationLine(
                        id = "STEEL_SECTION_AA",
                        text = "SECTION A-A",
                        position = Point2D(cx, d + 50.0),
                        height = 3.0,
                        layer = "TEXT",
                        codeReference = member.codeReference
                    ),
                    AnnotationLine(
                        id = "STEEL_NOTE",
                        text = note,
                        position = Point2D(0.0, d - 8.0),
                        height = 3.0,
                        layer = "TEXT",
                        codeReference = member.codeReference
                    )
                )
            ),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (member.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Build a frame-elevation model from the app's solved/designed frame.
     *
     * Pure passthrough of the analysis topology + member design verdicts
     * ([detail]): node coordinates (mm), member centreline endpoints and the
     * section band are carried as-is; layout mirrors the canvas renderer's
     * longitudinal section — member rectangles around their centrelines
     * (columns thick via their width, beams thin via their depth, braces
     * schematic), support symbols at base nodes, ground hatches, bay-width
     * dimensions below the foundation band and story-height dimensions to the
     * right. No strength quantity is computed here; the frame-level note and
     * drawing state derive from the engine's [detail.isSafe] and max
     * utilization. The X axis spans the bays translated so the base level sits
     * at y=0; [frameMembers] carry the sheet schedule rows (same order as
     * [detail.members]).
     */
    fun buildFrame(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        detail: FrameAnalysisDetailResult,
        frameMembers: List<FrameMemberMark> = emptyList()
    ): DrawingModel {
        val nodes = detail.nodes
        if (nodes.isEmpty()) {
            return DrawingModel(
                project = project,
                drawingNumber = drawingNumber,
                sheetNumber = sheetNumber,
                titleBlock = titleBlock,
                state = DrawingState(code = code, edition = edition, overallStatus = CheckStatus.FAIL)
            )
        }

        val xs = nodes.map { it.xMm }
        val ys = nodes.map { it.yMm }
        val minX = xs.min()
        val minY = ys.min()
        val totalSpan = (xs.max() - minX).coerceAtLeast(1.0)
        val totalHeight = (ys.max() - minY).coerceAtLeast(1.0)

        val tx = { x: Double -> x - minX }
        val ty = { y: Double -> y - minY }

        // Support symbol size + below-ground drawable band scale with the
        // largest column band so they read at the same scale as the frame.
        val halfWidth = (detail.members.maxOfOrNull { it.bandMm / 2.0 } ?: 100.0).coerceAtLeast(100.0)
        val supportDepth = (halfWidth * 1.6 + 40.0).coerceAtLeast(120.0)

        val geoms = detail.members.mapIndexed { i, m ->
            val x1 = tx(m.x1Mm); val y1 = ty(m.y1Mm)
            val x2 = tx(m.x2Mm); val y2 = ty(m.y2Mm)
            val band = m.bandMm.coerceAtLeast(1.0)
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-9)
            val ux = dx / len; val uy = dy / len
            val px = -uy; val py = ux     // unit perpendicular
            val hb = band / 2.0
            val c1 = Point2D(x1 + px * hb, y1 + py * hb)
            val c2 = Point2D(x1 - px * hb, y1 - py * hb)
            val c3 = Point2D(x2 - px * hb, y2 - py * hb)
            val c4 = Point2D(x2 + px * hb, y2 + py * hb)
            FrameMemberGeometry(
                mark = frameMembers.getOrNull(i)?.mark ?: "FM-${i + 1}",
                memberType = m.memberType,
                materialType = m.materialType,
                sectionName = m.sectionName,
                bandMm = band,
                outline = listOf(c1, c2, c3, c4),
                start = Point2D(x1, y1),
                end = Point2D(x2, y2),
                isSafe = m.isSafe,
                utilization = m.utilization
            )
        }

        // Base supports (nodes sitting on the base level).
        val supports = nodes
            .filter { (it.yMm - minY).let { d -> d * d < 1e-6 } }
            .mapNotNull { n ->
                if (n.supportType.equals("FREE", true)) null
                else FrameSupportGeometry(tx(n.xMm), ty(n.yMm), n.supportType, halfWidth)
            }
            .sortedBy { it.xMm }

        // ── Dimensions: bay widths below the foundation band, floor levels
        //    to the right (clear of the widest member band). ───────────────
        val dims = mutableListOf<DimensionLine>()
        val rightClearance = halfWidth + 40.0
        val bayBase = -supportDepth
        val baseXs = supports.map { it.xMm }.distinct().sorted()
        if (baseXs.size > 1) {
            for (i in 0 until baseXs.size - 1) {
                val a = baseXs[i]; val b = baseXs[i + 1]
                dims += DimensionLine(Point2D(a, bayBase - 25.0), Point2D(b, bayBase - 25.0), fmt(b - a))
            }
        }
        dims += DimensionLine(Point2D(0.0, bayBase - 45.0), Point2D(totalSpan, bayBase - 45.0), fmt(totalSpan))
        val levels = ys.distinct().sorted().map { ty(it) }
        if (levels.size > 2) {
            for (i in 0 until levels.size - 1) {
                dims += DimensionLine(
                    Point2D(totalSpan + rightClearance, levels[i]),
                    Point2D(totalSpan + rightClearance, levels[i + 1]),
                    fmt(levels[i + 1] - levels[i])
                )
            }
            dims += DimensionLine(
                Point2D(totalSpan + rightClearance + 30.0, levels.first()),
                Point2D(totalSpan + rightClearance + 30.0, levels.last()),
                fmt(totalHeight)
            )
        } else if (levels.size == 2) {
            dims += DimensionLine(
                Point2D(totalSpan + rightClearance, levels[0]),
                Point2D(totalSpan + rightClearance, levels[1]),
                fmt(totalHeight)
            )
        }

        // ── Annotations: title + frame-level note + per-member marks. ─────
        val maxUtil = detail.members.maxOfOrNull { it.utilization } ?: 0.0
        val note = "MAX UX = ${fmt(maxUtil)} ${if (detail.isSafe) "OK" else "NOT OK"}"
        val annos = mutableListOf(
            AnnotationLine(
                id = "FRAME_TITLE",
                text = "FRAME ELEVATION",
                position = Point2D(totalSpan / 2.0, totalHeight + 40.0),
                height = 3.5,
                layer = "TEXT",
                codeReference = detail.codeReference
            ),
            AnnotationLine(
                id = "FRAME_NOTE",
                text = note,
                position = Point2D(12.0, totalHeight + 40.0),
                height = 3.0,
                layer = "TEXT",
                codeReference = detail.codeReference
            )
        )
        geoms.forEachIndexed { i, g ->
            val dx = g.end.x - g.start.x
            val dy = g.end.y - g.start.y
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-9)
            var px = -dy / len; var py = dx / len
            // Always offset outward: right of columns, above beams.
            if (px < 0.0 || (px == 0.0 && py < 0.0)) { px = -px; py = -py }
            val mo = g.bandMm / 2.0 + 12.0
            annos += AnnotationLine(
                id = "FRAME_MARK_${i + 1}",
                text = g.mark,
                position = Point2D((g.start.x + g.end.x) / 2.0 + px * mo, (g.start.y + g.end.y) / 2.0 + py * mo),
                height = 2.5,
                layer = "TEXT",
                codeReference = detail.codeReference
            )
        }

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            frameGeometry = FrameGeometry(
                totalSpanMm = totalSpan,
                totalHeightMm = totalHeight,
                members = geoms,
                supports = supports,
                isSafe = detail.isSafe,
                maxUtilization = maxUtil,
                codeReference = detail.codeReference,
                sectionBounds = BoundingBox(0.0, -supportDepth, totalSpan, totalHeight)
            ),
            frameMembers = frameMembers,
            dimensions = DimensionSet(frameDimensions = dims),
            annotations = AnnotationSet(generalAnnotations = annos),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (detail.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * P043H — Seismic chart sheet (response spectrum + lateral force
     * distribution). Pure passthrough of the app-side seismic engine results
     * ([SeismicDetailResult]): every curve point, force, base-shear term and
     * the code citation arrive verbatim; only the paper placement of the curve
     * and the force bars is computed here (pure layout). The spectrum pane maps
     * T ∈ [0, maxPeriod] and Sa ∈ [0, maxAcceleration] into the left plot box;
     * the force pane maps each floor's lateral force into a horizontal bar
     * (length ∝ force) on the floor's level. Nothing strength-related is
     * recomputed; [SeismicDetailResult.isSafe] plus its own citation drive the
     * drawing state, note and WARNING-layer warnings.
     */
    fun buildSeismic(
        project: String,
        drawingNumber: String,
        sheetNumber: String,
        titleBlock: TitleBlock,
        code: DesignCode,
        edition: CodeVersion,
        detail: SeismicDetailResult
    ): DrawingModel {
        if (detail.spectrumPoints.isEmpty()) {
            return DrawingModel(
                project = project,
                drawingNumber = drawingNumber,
                sheetNumber = sheetNumber,
                titleBlock = titleBlock,
                state = DrawingState(code = code, edition = edition, overallStatus = CheckStatus.FAIL)
            )
        }

        val spectrumBox = BoundingBox(30.0, 40.0, 280.0, 228.0)
        val forceBox = BoundingBox(330.0, 40.0, 460.0, 228.0)

        val maxPeriod = (detail.spectrumPoints.maxOfOrNull { it.period } ?: 1.0).coerceAtLeast(1e-9)
        val maxAcc = (detail.spectrumPoints.maxOfOrNull { it.acceleration } ?: 1.0).coerceAtLeast(1e-9)

        fun tx(t: Double) = spectrumBox.minX + (t / maxPeriod).coerceIn(0.0, 1.0) * (spectrumBox.maxX - spectrumBox.minX)
        fun ty(a: Double) = spectrumBox.minY + (a / maxAcc).coerceIn(0.0, 1.0) * (spectrumBox.maxY - spectrumBox.minY)

        val curve = detail.spectrumPoints.map { p ->
            SeismicChartSpectrumPoint(
                period = p.period,
                acceleration = p.acceleration,
                xMm = tx(p.period),
                yMm = ty(p.acceleration)
            )
        }

        val n = detail.floorForces.size
        val forceBars = if (n == 0) emptyList() else {
            val maxForce = (detail.floorForces.maxOfOrNull { it.forceKn } ?: 1.0).coerceAtLeast(1e-9)
            val barSpace = (forceBox.maxY - forceBox.minY - 10.0) / n
            val barHalf = (barSpace * 0.6 / 2.0).coerceIn(2.0, 8.0)
            val maxBarLen = (forceBox.maxX - forceBox.minX) - 40.0
            detail.floorForces.mapIndexed { i, f ->
                SeismicForceBar(
                    floorIndex = f.floorIndex,
                    forceKn = f.forceKn,
                    floorHeightMm = forceBox.minY + (i + 0.5) * barSpace,
                    barHalfMm = barHalf,
                    barLengthMm = (f.forceKn / maxForce * maxBarLen).coerceAtLeast(1.0)
                )
            }
        }

        val cx1 = (spectrumBox.minX + spectrumBox.maxX) / 2.0
        val cx2 = (forceBox.minX + forceBox.maxX) / 2.0
        val annos = mutableListOf<AnnotationLine>(
            AnnotationLine(
                id = "SEISMIC_TITLE_SPECTRUM",
                text = "RESPONSE SPECTRUM - Sa (g) VS T (s)",
                position = Point2D(cx1, spectrumBox.maxY + 10.0),
                height = 3.0, layer = "TEXT",
                codeReference = detail.codeReference
            ),
            AnnotationLine(
                id = "SEISMIC_TITLE_FORCE",
                text = "LATERAL FORCE DISTRIBUTION",
                position = Point2D(cx2, forceBox.maxY + 10.0),
                height = 3.0, layer = "TEXT",
                codeReference = detail.codeReference
            ),
            AnnotationLine(
                id = "SEISMIC_AXIS_T",
                text = "T (s)",
                position = Point2D(spectrumBox.maxX + 8.0, spectrumBox.minY - 1.0),
                height = 2.5, layer = "TEXT",
                codeReference = detail.codeReference
            ),
            AnnotationLine(
                id = "SEISMIC_AXIS_SA",
                text = "Sa (g)",
                position = Point2D(spectrumBox.minX + 1.0, spectrumBox.maxY + 3.0),
                height = 2.5, layer = "TEXT",
                codeReference = detail.codeReference
            ),
            AnnotationLine(
                id = "SEISMIC_AXIS_FI",
                text = "FI (kN)",
                position = Point2D(forceBox.minX + 1.0, forceBox.maxY + 3.0),
                height = 2.5, layer = "TEXT",
                codeReference = detail.codeReference
            )
        )

        if (detail.fundamentalPeriod > 0.0) {
            val t1x = tx(detail.fundamentalPeriod)
            val s1y = ty(detail.spectralAccel.coerceAtLeast(0.0))
            annos += AnnotationLine(
                id = "SEISMIC_T1_MARK",
                text = String.format(
                    Locale.US, "T1 = %.2f s  Sa = %.3f g",
                    detail.fundamentalPeriod, detail.spectralAccel
                ),
                position = Point2D(t1x + 3.0, s1y + 12.0),
                height = 2.5, layer = "TEXT",
                codeReference = detail.codeReference
            )
        }

        annos += AnnotationLine(
            id = "SEISMIC_NOTE",
            text = "V = ${fmt(detail.baseShearKn)} kN   ${if (detail.isSafe) "OK" else "NOT OK"}" +
                "   ${detail.calculationFormula}",
            position = Point2D(spectrumBox.minX, forceBox.maxY + 30.0),
            height = 3.0, layer = "TEXT",
            codeReference = detail.codeReference
        )

        if (n > 0) {
            val labelEvery = maxOf(1, kotlin.math.ceil(n.toDouble() / 12.0).toInt())
            forceBars.forEachIndexed { i, b ->
                val labelled = i == 0 || i == n - 1 || (b.floorIndex % labelEvery == 0)
                if (labelled) {
                    annos += AnnotationLine(
                        id = "SEISMIC_FLOOR_${i + 1}",
                        text = "FLOOR ${b.floorIndex + 1}",
                        position = Point2D(forceBox.minX - 30.0, b.floorHeightMm - 1.0),
                        height = 2.0, layer = "TEXT",
                        codeReference = detail.codeReference
                    )
                }
            }
        }

        detail.warnings.take(2).forEachIndexed { i, w ->
            annos += AnnotationLine(
                id = "SEISMIC_WARN_${i + 1}",
                text = "WARNING: $w",
                position = Point2D(spectrumBox.minX, forceBox.maxY + 26.0 - i * 4.0),
                height = 2.5, layer = "WARNING",
                codeReference = detail.codeReference
            )
        }

        return DrawingModel(
            project = project,
            drawingNumber = drawingNumber,
            sheetNumber = sheetNumber,
            titleBlock = titleBlock,
            seismicChart = SeismicChartGeometry(
                spectrumBox = spectrumBox,
                forceBox = forceBox,
                spectrumPoints = curve,
                forceBars = forceBars,
                maxPeriod = maxPeriod,
                maxAcceleration = maxAcc,
                fundamentalPeriod = detail.fundamentalPeriod,
                spectralAccel = detail.spectralAccel,
                baseShearKn = detail.baseShearKn,
                zoneFactor = detail.zoneFactor,
                soilFactor = detail.soilFactor,
                importanceFactor = detail.importanceFactor,
                responseModification = detail.responseModification,
                calculationFormula = detail.calculationFormula,
                codeReference = detail.codeReference,
                isSafe = detail.isSafe,
                warnings = detail.warnings,
                sectionBounds = BoundingBox(30.0, 40.0, 460.0, 228.0)
            ),
            dimensions = DimensionSet(),
            annotations = AnnotationSet(generalAnnotations = annos),
            state = DrawingState(
                code = code,
                edition = edition,
                overallStatus = if (detail.isSafe) CheckStatus.PASS else CheckStatus.FAIL
            )
        )
    }

    /**
     * Validate that the model has no invalid values before freezing.
     * Returns fresh [QaFlags] — pure function, never throws.
     */
    fun validate(model: DrawingModel): QaFlags {
        fun bad(v: Double) = v.isNaN() || v.isInfinite()

        var invalid = false
        fun sectionBoundsOf(section: SlabSectionGeometry): BoundingBox = when (section) {
            is SlabSectionGeometryOneWay -> section.sectionBounds
            is SlabSectionGeometryTwoWay -> section.sectionBounds
            is SlabSectionGeometryFlat -> section.sectionBounds
        }
        val boundsList = buildList {
            model.beamSection?.let { add(it.sectionBounds) }
            model.columnSection?.let { add(it.sectionBounds) }
            model.slabSection?.let { add(sectionBoundsOf(it)) }
            model.footingSection?.let { add(it.sectionBounds) }
            model.stairSection?.let { add(it.sectionBounds) }
            model.tankSection?.let { add(it.sectionBounds) }
            model.retainingWallSection?.let { add(it.sectionBounds) }
            model.shearWallSection?.let { add(it.sectionBounds) }
            model.steelSection?.let { add(it.sectionBounds) }
            model.frameGeometry?.let { add(it.sectionBounds) }
            model.seismicChart?.let { add(it.sectionBounds) }
            model.beamElevation?.let { add(it.sectionBounds) }
        }
        for (b in boundsList) {
            if (bad(b.minX) || bad(b.minY) || bad(b.maxX) || bad(b.maxY) || b.maxX < b.minX || b.maxY < b.minY) {
                invalid = true
            }
        }
        model.beamElevation?.let { e ->
            e.supports.forEach { s ->
                if (bad(s.xMm) || bad(s.soffitY) || bad(s.symbolHeightMm)) invalid = true
            }
            e.loadArrows.forEach { a ->
                if (bad(a.xMm) || bad(a.shaftTopY) || bad(a.headY)) invalid = true
            }
            e.momentCurve.forEach { p -> if (bad(p.xMm) || bad(p.yMm)) invalid = true }
            e.shearCurve.forEach { p -> if (bad(p.xMm) || bad(p.yMm)) invalid = true }
            if (bad(e.spanMm) || bad(e.appliedMomentKnM) || bad(e.appliedShearKn)) invalid = true
        }
        for (bar in model.reinforcement.all) {
            val spacingBad = bar.spacing != null && (bar.spacing!!.isNaN() || bar.spacing!!.isInfinite())
            if (bad(bar.diameter) || bad(bar.totalLengthMm) || bar.quantity <= 0 || spacingBad) invalid = true
        }
        for (dim in model.dimensions.all) {
            if (bad(dim.start.x) || bad(dim.start.y) || bad(dim.end.x) || bad(dim.end.y)) invalid = true
        }
        for (anno in model.annotations.all) {
            if (bad(anno.position.x) || bad(anno.position.y)) invalid = true
        }
        if (model.slabSection is SlabSectionGeometryOneWay) {
            if (bad(model.slabSection.thickness) || bad(model.slabSection.effectiveDepth)) invalid = true
        }
        if (model.slabSection is SlabSectionGeometryTwoWay) {
            if (bad(model.slabSection.thickness) || bad(model.slabSection.effectiveDepth)) invalid = true
        }
        if (model.slabSection is SlabSectionGeometryFlat) {
            if (bad(model.slabSection.thickness) || bad(model.slabSection.effectiveDepth) ||
                bad(model.slabSection.dropDepth) || bad(model.slabSection.dropSize) ||
                bad(model.slabSection.columnStripWidthMm)
            ) invalid = true
        }
        model.stairSection?.let {
            if (bad(it.waistThickness) || bad(it.effectiveDepth)) invalid = true
        }
        model.tankSection?.let {
            if (bad(it.wallThickness) || bad(it.baseThickness) || bad(it.effectiveDepth)) invalid = true
        }
        model.retainingWallSection?.let {
            if (bad(it.wallHeight) || bad(it.baseWidth) || bad(it.baseThickness)) invalid = true
        }
        model.shearWallSection?.let {
            if (bad(it.wallLength) || bad(it.wallThickness) || bad(it.boundaryElementLengthMm)) invalid = true
            it.flange?.let { f ->
                if (bad(f.projectionMm) || bad(f.thicknessMm)) invalid = true
            }
        }
        model.steelSection?.let {
            if (bad(it.memberLengthMm) || bad(it.depthMm) || bad(it.widthMm) ||
                bad(it.webThicknessMm) || bad(it.flangeThicknessMm) || bad(it.utilizationRatio)
            ) invalid = true
        }
        for (m in model.steelMembers) {
            if (bad(m.lengthMm) || m.quantity <= 0) invalid = true
        }
        model.frameGeometry?.let { f ->
            if (bad(f.totalSpanMm) || bad(f.totalHeightMm) || bad(f.maxUtilization)) invalid = true
            f.members.forEach { m ->
                if (bad(m.bandMm) || bad(m.utilization) ||
                    m.outline.any { bad(it.x) || bad(it.y) } ||
                    bad(m.start.x) || bad(m.start.y) || bad(m.end.x) || bad(m.end.y)
                ) invalid = true
            }
            f.supports.forEach { s ->
                if (bad(s.xMm) || bad(s.yMm) || bad(s.halfWidthMm)) invalid = true
            }
        }
        for (m in model.frameMembers) {
            if (bad(m.lengthMm) || m.quantity <= 0) invalid = true
        }
        model.seismicChart?.let { s ->
            listOf(s.spectrumBox, s.forceBox).forEach { b ->
                if (bad(b.minX) || bad(b.minY) || bad(b.maxX) || bad(b.maxY) ||
                    b.maxX < b.minX || b.maxY < b.minY
                ) invalid = true
            }
            s.spectrumPoints.forEach { p ->
                if (bad(p.period) || bad(p.acceleration) || bad(p.xMm) || bad(p.yMm)) invalid = true
            }
            s.forceBars.forEach { b ->
                if (bad(b.forceKn) || bad(b.floorHeightMm) || bad(b.barHalfMm) || bad(b.barLengthMm)) invalid = true
            }
            if (bad(s.maxPeriod) || bad(s.maxAcceleration) || bad(s.fundamentalPeriod) || bad(s.spectralAccel) ||
                bad(s.baseShearKn) || bad(s.zoneFactor) || bad(s.soilFactor) ||
                bad(s.importanceFactor) || bad(s.responseModification)
            ) invalid = true
        }

        return QaFlags(
            hasInvalid = invalid,
            sanityWarnings = model.state.overallStatus != CheckStatus.PASS,
            dxfValid = false,
            pdfValid = false
        )
    }
}

/** -----------------------------------------------------------------------
 *  §10  Internal helpers
 * ----------------------------------------------------------------------- */

/** Bounding box of every geometric element in the model. */
private fun computeDrawingBounds(model: DrawingModel): BoundingBox {
    val xs = mutableListOf(0.0)
    val ys = mutableListOf(0.0)
    fun addBox(b: BoundingBox) {
        xs += b.minX; xs += b.maxX
        ys += b.minY; ys += b.maxY
    }
    model.beamSection?.let { addBox(it.sectionBounds) }
    model.columnSection?.let { addBox(it.sectionBounds) }
    when (val s = model.slabSection) {
        is SlabSectionGeometryOneWay -> addBox(s.sectionBounds)
        is SlabSectionGeometryTwoWay -> addBox(s.sectionBounds)
        is SlabSectionGeometryFlat -> addBox(s.sectionBounds)
        null -> Unit
    }
    model.footingSection?.let { addBox(it.sectionBounds) }
    model.stairSection?.let { addBox(it.sectionBounds) }
    model.tankSection?.let { addBox(it.sectionBounds) }
    model.retainingWallSection?.let { addBox(it.sectionBounds) }
    model.shearWallSection?.let { addBox(it.sectionBounds) }
    model.steelSection?.let { addBox(it.sectionBounds) }
    model.frameGeometry?.let { addBox(it.sectionBounds) }
    model.seismicChart?.let { s ->
        addBox(s.spectrumBox)
        addBox(s.forceBox)
    }
    model.beamElevation?.let { addBox(it.sectionBounds) }
    model.dimensions.all.forEach { d ->
        xs += d.start.x; xs += d.end.x
        ys += d.start.y; ys += d.end.y
    }
    model.annotations.all.forEach { a ->
        xs += a.position.x; ys += a.position.y
    }
    return BoundingBox(xs.min(), ys.min(), xs.max(), ys.max())
}

/** Format a length/dimension without trailing zeros. */
internal fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else java.lang.String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

/** Diameter label, e.g. 20 for Ø20. */
internal fun diameterLabel(d: Double): String = fmt(d)