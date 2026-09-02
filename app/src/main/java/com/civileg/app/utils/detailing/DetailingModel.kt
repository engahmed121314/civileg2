package com.civileg.app.utils.detailing

import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// CIVILEG STRUCTURAL DETAILING DOMAIN MODEL
// All coordinates / lengths are in millimetres unless annotated otherwise.
// ─────────────────────────────────────────────────────────────────────────────

// ═══════════════════════════════════════════════════════════════════════════
// Unit system
// ═══════════════════════════════════════════════════════════════════════════

enum class UnitSystem {
    SI,    // mm / kN / MPa
    IMPERIAL // in / kip / ksi
}

data class AnnotatedValue(
    val value: Double,
    val unit: String,
    val label: String = ""
) {
    override fun toString(): String =
        if (label.isNotEmpty()) "$label = ${fmt(value)} $unit"
        else "${fmt(value)} $unit"

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}

// ═══════════════════════════════════════════════════════════════════════════
// Drawing scale
// ═══════════════════════════════════════════════════════════════════════════

enum class DrawingScale(val ratio: Double, val label: String) {
    S_1_1(1.0, "1:1"),
    S_1_2(2.0, "1:2"),
    S_1_5(5.0, "1:5"),
    S_1_10(10.0, "1:10"),
    S_1_20(20.0, "1:20"),
    S_1_25(25.0, "1:25"),
    S_1_50(50.0, "1:50"),
    S_1_100(100.0, "1:100"),
    NTS(1.0, "NTS");

    /** Convert a real-world mm distance to paper mm at this scale. */
    fun toPaper(realMm: Double) = realMm / ratio

    /** Convert a paper mm distance back to real-world mm. */
    fun toReal(paperMm: Double) = paperMm * ratio
}

/** Determines the most appropriate scale and paper viewport for a given element size. */
data class FitResult(
    val scale: DrawingScale,
    val viewportWidthMm: Double,
    val viewportHeightMm: Double,
    val textHeightMm: Double,   // recommended text height at this scale (paper units)
    val dimScaleMm: Double      // recommended dimension line scale
)

object ScaleEngine {

    private val PAPER_SIZES = listOf(
        "A3" to (420.0 to 297.0),
        "A2" to (594.0 to 420.0),
        "A1" to (841.0 to 594.0)
    )

    /**
     * Fit a real-world element of [realWidthMm] x [realHeightMm] onto paper
     * leaving [marginMm] on each side.
     */
    fun fitDrawingToSheet(
        realWidthMm: Double,
        realHeightMm: Double,
        paperWidthMm: Double = 420.0,
        paperHeightMm: Double = 297.0,
        marginMm: Double = 25.0
    ): FitResult {
        require(realWidthMm > 0.0) { "fitDrawingToSheet: realWidthMm must be > 0" }
        require(realHeightMm > 0.0) { "fitDrawingToSheet: realHeightMm must be > 0" }

        val availW = paperWidthMm - 2 * marginMm
        val availH = paperHeightMm - 2 * marginMm

        // Find smallest standard scale that fits
        val best = DrawingScale.values()
            .filter { it != DrawingScale.NTS }
            .sortedBy { it.ratio }
            .firstOrNull { scale ->
                scale.toPaper(realWidthMm) <= availW &&
                scale.toPaper(realHeightMm) <= availH
            } ?: DrawingScale.S_1_100

        return FitResult(
            scale = best,
            viewportWidthMm = best.toPaper(realWidthMm),
            viewportHeightMm = best.toPaper(realHeightMm),
            textHeightMm = (2.5 * best.ratio / 10.0).coerceIn(2.0, 7.0),
            dimScaleMm = best.ratio / 5.0
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Drawing status / revision
// ═══════════════════════════════════════════════════════════════════════════

enum class DrawingStatus(val label: String) {
    PRELIMINARY("PRELIMINARY"),
    FOR_REVIEW("FOR REVIEW"),
    FOR_CONSTRUCTION("FOR CONSTRUCTION"),
    AS_BUILT("AS BUILT")
}

// ═══════════════════════════════════════════════════════════════════════════
// Drawing view types
// ═══════════════════════════════════════════════════════════════════════════

enum class DrawingViewType {
    PLAN,
    ELEVATION,
    SECTION,
    DETAIL,
    BBS,
    CALCULATION,
    PRESSURE,
    INTERACTION,
    SCHEDULE,
    ISOMETRIC
}

// ═══════════════════════════════════════════════════════════════════════════
// Title block
// ═══════════════════════════════════════════════════════════════════════════

data class TitleBlock(
    val project: String,
    val client: String = "",
    val consultant: String = "",
    val drawingTitle: String,
    val drawingNumber: String,
    val revision: String = "00",
    val date: String,
    val scale: String,
    val designCode: String = "ECP 203",
    val designedBy: String = "",
    val checkedBy: String = "",
    val approvedBy: String = "",
    val sheet: String = "1/1",
    val status: DrawingStatus = DrawingStatus.FOR_CONSTRUCTION,
    val software: String = "CivilEG"
)

// ═══════════════════════════════════════════════════════════════════════════
// Rebar schedule (BBS row)
// ═══════════════════════════════════════════════════════════════════════════

data class RebarScheduleRow(
    val mark: String,
    val memberLocation: String,
    val diameterMm: Int,
    val shapeCode: String,          // e.g. "00", "11", "21"
    val qty: Int,
    val dimA: Double = 0.0,         // mm
    val dimB: Double = 0.0,
    val dimC: Double = 0.0,
    val dimD: Double = 0.0,
    val cuttingLengthMm: Double,
    val totalLengthM: Double,
    val unitWeightKgM: Double,
    val totalWeightKg: Double
) {
    companion object {
        fun unitWeight(diamMm: Int): Double = 0.006165 * diamMm * diamMm // kg/m
    }
}

data class RebarSchedule(
    val rows: List<RebarScheduleRow>,
    val totalWeightKg: Double = rows.sumOf { it.totalWeightKg },
    val generatedAt: String = ""
)

// ═══════════════════════════════════════════════════════════════════════════
// Annotation entity
// ═══════════════════════════════════════════════════════════════════════════

data class AnnotationEntity(
    val text: String,
    val x: Double,
    val y: Double,
    val heightMm: Double = 3.0,
    val layer: String = CadLayers.TEXT,
    val rotation: Double = 0.0
)

// ═══════════════════════════════════════════════════════════════════════════
// Dimension entity
// ═══════════════════════════════════════════════════════════════════════════

data class DimensionEntity(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val offsetMm: Double,                  // perpendicular offset of dimension line
    val text: String = "",                 // empty = auto from distance
    val layer: String = CadLayers.DIM,
    val unit: String = "mm"
)

// ═══════════════════════════════════════════════════════════════════════════
// Drawing view
// ═══════════════════════════════════════════════════════════════════════════

data class DrawingView(
    val type: DrawingViewType,
    val title: String,
    val originX: Double,
    val originY: Double,
    val scale: DrawingScale,
    val dimensions: List<DimensionEntity> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    /** Raw CAD entities for this view — added by detailing engines */
    val entities: List<Any> = emptyList()
)

// ═══════════════════════════════════════════════════════════════════════════
// Drawing sheet
// ═══════════════════════════════════════════════════════════════════════════

data class DrawingSheet(
    val sheetNumber: String,
    val title: String,
    val paperWidthMm: Double = 420.0,
    val paperHeightMm: Double = 297.0,
    val titleBlock: TitleBlock,
    val views: List<DrawingView> = emptyList(),
    val dimensions: List<DimensionEntity> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    val notes: List<String> = emptyList()
)

// ═══════════════════════════════════════════════════════════════════════════
// Structural drawing package
// ═══════════════════════════════════════════════════════════════════════════

data class StructuralDrawing(
    val drawingId: String,
    val title: String,
    val elementType: String,
    val sheets: List<DrawingSheet>,
    val notes: List<String> = emptyList(),
    val schedules: List<RebarSchedule> = emptyList(),
    val warnings: List<String> = emptyList()
)

// ═══════════════════════════════════════════════════════════════════════════
// Drawing manifest entry (for multi-sheet output)
// ═══════════════════════════════════════════════════════════════════════════

data class DrawingManifestEntry(
    val sheetNumber: String,
    val element: String,
    val revision: String,
    val code: String,
    val status: DrawingStatus,
    val file: String
)

data class DrawingManifest(
    val project: String,
    val entries: List<DrawingManifestEntry>
) {
    fun toText(): String = buildString {
        appendLine("CIVILEG DRAWING MANIFEST")
        appendLine("PROJECT: $project")
        appendLine("─".repeat(80))
        appendLine(
            "%-12s %-30s %-6s %-16s %-18s %s"
                .format("SHEET", "ELEMENT", "REV", "CODE", "STATUS", "FILE")
        )
        appendLine("─".repeat(80))
        for (e in entries) {
            appendLine(
                "%-12s %-30s %-6s %-16s %-18s %s"
                    .format(e.sheetNumber, e.element, e.revision, e.code, e.status.label, e.file)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CAD layer constants
// ═══════════════════════════════════════════════════════════════════════════

object CadLayers {
    const val BORDER = "BORDER"
    const val TITLE = "TITLE"
    const val TEXT = "TEXT"
    const val TEXT_AR = "TEXT_AR"
    const val DIM = "DIM"
    const val CENTER = "CENTER"
    const val HIDDEN = "HIDDEN"
    const val CONC = "CONC"
    const val CONC_HATCH = "CONC_HATCH"
    const val REBAR = "REBAR"
    const val REBAR_HOOK = "REBAR_HOOK"
    const val REBAR_DIM = "REBAR_DIM"
    const val STIRRUP = "STIRRUP"
    const val COLUMN = "COLUMN"
    const val FOUNDATION = "FOUNDATION"
    const val FOOTING = "FOOTING"
    const val WALL = "WALL"
    const val SOIL = "SOIL"
    const val WATER = "WATER"
    const val STEEL = "STEEL"
    const val STEEL_HIDDEN = "STEEL_HIDDEN"
    const val ANALYSIS = "ANALYSIS"
    const val PRESSURE = "PRESSURE"
    const val LOAD = "LOAD"
    const val GRID = "GRID"
    const val OPENING = "OPENING"
    const val JOINT = "JOINT"
    const val BBS = "BBS"
    const val SCHEDULE = "SCHEDULE"
    const val CALCULATION = "CALCULATION"
    const val WARNING = "WARNING"
}

// ═══════════════════════════════════════════════════════════════════════════
// CAD line type constants
// ═══════════════════════════════════════════════════════════════════════════

object CadLineTypes {
    const val CONTINUOUS = "CONTINUOUS"
    const val CENTER = "CENTER"
    const val HIDDEN = "HIDDEN"
    const val DASHED = "DASHED"
    const val PHANTOM = "PHANTOM"
    const val DOT = "DOT"
}

// ═══════════════════════════════════════════════════════════════════════════
// AutoCAD Color Index (ACI) constants with semantic meaning
// ═══════════════════════════════════════════════════════════════════════════

object CadColors {
    const val RED = 1           // Rebar, Steel (primary)
    const val YELLOW = 2        // Dimensions
    const val GREEN = 3         // Hatch (concrete)
    const val CYAN = 4          // Analysis, Diagrams
    const val BLUE = 5          // Section lines
    const val MAGENTA = 6       // Warning
    const val WHITE = 7         // Concrete outline, Text
    const val GRAY = 8          // Concrete (gray)
    const val DARK_GRAY = 9     // Soil
    const val BROWN = 30        // Soil / earth
    const val LIGHT_BLUE = 151  // Water

    // Semantic layer color map
    val layerColors = mapOf(
        CadLayers.BORDER to WHITE,
        CadLayers.TITLE to WHITE,
        CadLayers.TEXT to WHITE,
        CadLayers.TEXT_AR to WHITE,
        CadLayers.DIM to YELLOW,
        CadLayers.CENTER to CYAN,
        CadLayers.HIDDEN to GRAY,
        CadLayers.CONC to WHITE,
        CadLayers.CONC_HATCH to GRAY,
        CadLayers.REBAR to RED,
        CadLayers.REBAR_HOOK to RED,
        CadLayers.REBAR_DIM to YELLOW,
        CadLayers.STIRRUP to RED,
        CadLayers.COLUMN to WHITE,
        CadLayers.FOUNDATION to WHITE,
        CadLayers.FOOTING to WHITE,
        CadLayers.WALL to WHITE,
        CadLayers.SOIL to BROWN,
        CadLayers.WATER to LIGHT_BLUE,
        CadLayers.STEEL to RED,
        CadLayers.STEEL_HIDDEN to GRAY,
        CadLayers.ANALYSIS to CYAN,
        CadLayers.PRESSURE to CYAN,
        CadLayers.LOAD to GREEN,
        CadLayers.GRID to GRAY,
        CadLayers.OPENING to MAGENTA,
        CadLayers.JOINT to YELLOW,
        CadLayers.BBS to WHITE,
        CadLayers.SCHEDULE to WHITE,
        CadLayers.CALCULATION to WHITE,
        CadLayers.WARNING to MAGENTA
    )

    val layerLineTypes = mapOf(
        CadLayers.CENTER to CadLineTypes.CENTER,
        CadLayers.HIDDEN to CadLineTypes.HIDDEN,
        CadLayers.STEEL_HIDDEN to CadLineTypes.HIDDEN,
        CadLayers.GRID to CadLineTypes.DASHED
    )
}
