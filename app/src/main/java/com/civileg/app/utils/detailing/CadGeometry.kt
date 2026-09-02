package com.civileg.app.utils.detailing

import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CIVILEG CAD GEOMETRY PRIMITIVES
//
// All coordinates are in millimetres (paper-space after scale is applied by
// the detailing engine).  The DxfWriter is the only consumer that serialises
// these to DXF text — no other layer should know about DXF codes.
// ─────────────────────────────────────────────────────────────────────────────

// ═══════════════════════════════════════════════════════════════════════════
// Exceptions
// ═══════════════════════════════════════════════════════════════════════════

class MissingGeometryException(msg: String) : IllegalArgumentException(msg)
class UnsupportedShapeException(msg: String) : UnsupportedOperationException(msg)
class UnsupportedMemberTypeException(msg: String) : UnsupportedOperationException(msg)
class DxfExportException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
class DrawingValidationException(msg: String) : IllegalStateException(msg)

// ═══════════════════════════════════════════════════════════════════════════
// Base interface
// ═══════════════════════════════════════════════════════════════════════════

interface CadEntity {
    val layer: String
    val color: Int          // AutoCAD Color Index; -1 = BYLAYER
    val lineType: String    // e.g. "CONTINUOUS", "CENTER", "HIDDEN"
    val lineWeight: Int     // 1/100 mm; -1 = BYLAYER (e.g. 25 = 0.25 mm)

    /** Axis-aligned bounding box [minX, minY, maxX, maxY] */
    fun bounds(): DoubleArray
}

// Convenience default values shared by all primitives
private const val BY_LAYER_COLOR = -1
private const val BY_LAYER_WEIGHT = -1
private const val LT_CONTINUOUS = CadLineTypes.CONTINUOUS

// ═══════════════════════════════════════════════════════════════════════════
// Point (2-D helper; not a DXF entity on its own)
// ═══════════════════════════════════════════════════════════════════════════

data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(s: Double) = Pt(x * s, y * s)
    fun distanceTo(o: Pt) = hypot(x - o.x, y - o.y)
    fun angleTo(o: Pt) = atan2(o.y - y, o.x - x)          // radians
    fun rotate(rad: Double): Pt {
        val c = cos(rad); val s = sin(rad)
        return Pt(x * c - y * s, x * s + y * c)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LINE
// ═══════════════════════════════════════════════════════════════════════════

data class CadLine(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    override val layer: String = CadLayers.CONC,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    override fun bounds() = doubleArrayOf(
        min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)
    )

    val length get() = hypot(x2 - x1, y2 - y1)

    init {
        require(!x1.isNaN() && !y1.isNaN() && !x2.isNaN() && !y2.isNaN()) {
            "CadLine: NaN coordinate detected"
        }
        require(length > 1e-9) { "CadLine: zero-length line at ($x1,$y1)" }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// POLYLINE (LW)
// ═══════════════════════════════════════════════════════════════════════════

data class CadPolyline(
    val points: List<Pt>,
    val closed: Boolean = false,
    override val layer: String = CadLayers.CONC,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    init {
        require(points.size >= 2) { "CadPolyline: requires at least 2 points" }
        points.forEach { p ->
            require(!p.x.isNaN() && !p.y.isNaN()) { "CadPolyline: NaN point $p" }
        }
    }

    override fun bounds(): DoubleArray {
        val xs = points.map { it.x }; val ys = points.map { it.y }
        return doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }
}

/** Helper: build a rectangle as a closed polyline */
fun cadRect(x: Double, y: Double, w: Double, h: Double,
            layer: String = CadLayers.CONC, color: Int = BY_LAYER_COLOR,
            lineWeight: Int = BY_LAYER_WEIGHT): CadPolyline {
    require(w > 0 && h > 0) { "cadRect: width=$w height=$h must be > 0" }
    return CadPolyline(
        listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h)),
        closed = true, layer = layer, color = color, lineWeight = lineWeight
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// ARC
// ═══════════════════════════════════════════════════════════════════════════

data class CadArc(
    val cx: Double, val cy: Double,
    val radius: Double,
    val startAngleDeg: Double,
    val endAngleDeg: Double,
    override val layer: String = CadLayers.REBAR,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    init {
        require(radius > 0.0) { "CadArc: radius must be > 0" }
        require(!cx.isNaN() && !cy.isNaN()) { "CadArc: NaN centre" }
    }

    override fun bounds() = doubleArrayOf(
        cx - radius, cy - radius, cx + radius, cy + radius
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// CIRCLE
// ═══════════════════════════════════════════════════════════════════════════

data class CadCircle(
    val cx: Double, val cy: Double,
    val radius: Double,
    override val layer: String = CadLayers.REBAR,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    init {
        require(radius > 0.0) { "CadCircle: radius must be > 0" }
        require(!cx.isNaN() && !cy.isNaN()) { "CadCircle: NaN centre" }
    }

    override fun bounds() = doubleArrayOf(
        cx - radius, cy - radius, cx + radius, cy + radius
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// TEXT (single-line)
// ═══════════════════════════════════════════════════════════════════════════

data class CadText(
    val text: String,
    val x: Double, val y: Double,
    val heightMm: Double = 3.0,
    val rotation: Double = 0.0,     // degrees
    val hJustify: Int = 0,          // 0=left 1=centre 2=right
    val vJustify: Int = 0,          // 0=baseline 1=bottom 2=middle 3=top
    override val layer: String = CadLayers.TEXT,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    init {
        require(text.isNotEmpty()) { "CadText: text must not be empty" }
        require(heightMm > 0) { "CadText: heightMm must be > 0" }
    }

    override fun bounds(): DoubleArray {
        val estimatedWidth = text.length * heightMm * 0.6
        return doubleArrayOf(x, y, x + estimatedWidth, y + heightMm)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MTEXT (multi-line)
// ═══════════════════════════════════════════════════════════════════════════

data class CadMText(
    val lines: List<String>,
    val x: Double, val y: Double,
    val widthMm: Double = 100.0,
    val heightMm: Double = 3.5,
    val rotation: Double = 0.0,
    val attachment: Int = 1,        // 1=TL 2=TC 3=TR 4=ML 5=MC 6=MR 7=BL 8=BC 9=BR
    override val layer: String = CadLayers.TEXT,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    init {
        require(lines.isNotEmpty()) { "CadMText: lines must not be empty" }
    }

    override fun bounds() = doubleArrayOf(
        x, y - lines.size * heightMm, x + widthMm, y
    )

    /** Convert list to DXF MTEXT paragraph string (\\P separator) */
    fun toMTextContent(): String = lines.joinToString("\\P")
}

// ═══════════════════════════════════════════════════════════════════════════
// LEADER (annotation arrow)
// ═══════════════════════════════════════════════════════════════════════════

data class CadLeader(
    val vertices: List<Pt>,     // arrow tip is vertices[0]
    val text: String,
    val textHeightMm: Double = 3.0,
    override val layer: String = CadLayers.DIM,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    init {
        require(vertices.size >= 2) { "CadLeader: need at least tip + elbow vertex" }
        require(text.isNotEmpty()) { "CadLeader: text must not be empty" }
    }

    override fun bounds(): DoubleArray {
        val xs = vertices.map { it.x }; val ys = vertices.map { it.y }
        return doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    val tip get() = vertices.first()
    val elbow get() = vertices.last()
}

// ═══════════════════════════════════════════════════════════════════════════
// LINEAR DIMENSION
// ═══════════════════════════════════════════════════════════════════════════

data class CadDimLinear(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val offsetMm: Double,           // perpendicular offset of dimension line
    val overrideText: String = "",  // empty = automatic
    override val layer: String = CadLayers.DIM,
    override val color: Int = CadColors.YELLOW,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = 13   // 0.13 mm
) : CadEntity {
    val measuredLength get() = hypot(x2 - x1, y2 - y1)
    val displayText get() = overrideText.ifEmpty { "${measuredLength.toInt()} mm" }

    init {
        require(!x1.isNaN() && !y1.isNaN() && !x2.isNaN() && !y2.isNaN()) {
            "CadDimLinear: NaN coordinate"
        }
        require(measuredLength > 1e-6) { "CadDimLinear: zero-length dimension" }
    }

    override fun bounds() = doubleArrayOf(
        min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// CENTRE LINE (special DXF treatment with CENTER linetype)
// ═══════════════════════════════════════════════════════════════════════════

data class CadCenterLine(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    override val layer: String = CadLayers.CENTER,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = CadLineTypes.CENTER,
    override val lineWeight: Int = 13
) : CadEntity {
    override fun bounds() = doubleArrayOf(
        min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)
    )
}

/** Helper: emit a cross-shaped centre-line pair at (cx, cy) */
fun cadCentreLines(cx: Double, cy: Double, halfLen: Double,
                   layer: String = CadLayers.CENTER): List<CadCenterLine> = listOf(
    CadCenterLine(cx - halfLen, cy, cx + halfLen, cy, layer = layer),
    CadCenterLine(cx, cy - halfLen, cx, cy + halfLen, layer = layer)
)

// ═══════════════════════════════════════════════════════════════════════════
// HATCH
// ═══════════════════════════════════════════════════════════════════════════

data class HatchBoundaryLoop(val points: List<Pt>, val closed: Boolean = true) {
    init { require(points.size >= 3) { "HatchBoundaryLoop: need >= 3 points" } }
}

data class CadHatch(
    val boundary: List<HatchBoundaryLoop>,
    val patternName: String = "AR-CONC",  // e.g. "AR-CONC", "SOLID", "ANSI31", "AR-SAND"
    val scale: Double = 1.0,
    val angle: Double = 0.0,
    override val layer: String = CadLayers.CONC_HATCH,
    override val color: Int = CadColors.GRAY,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    override fun bounds(): DoubleArray {
        val pts = boundary.flatMap { it.points }
        if (pts.isEmpty()) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val xs = pts.map { it.x }; val ys = pts.map { it.y }
        return doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }
}

/** Helper: rectangular hatch */
fun cadRectHatch(x: Double, y: Double, w: Double, h: Double,
                 pattern: String = "AR-CONC",
                 layer: String = CadLayers.CONC_HATCH): CadHatch {
    require(w > 0 && h > 0) { "cadRectHatch: dimensions must be > 0" }
    return CadHatch(
        listOf(HatchBoundaryLoop(listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h)))),
        patternName = pattern, layer = layer
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// BLOCK DEFINITION & INSERT
// ═══════════════════════════════════════════════════════════════════════════

data class CadBlockDef(
    val name: String,
    val entities: List<CadEntity>
)

data class CadInsert(
    val blockName: String,
    val x: Double, val y: Double,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotation: Double = 0.0,
    override val layer: String = CadLayers.TEXT,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    override fun bounds() = doubleArrayOf(x, y, x, y) // actual depends on block extents
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION MARKER  (A-A bubble + cutting line)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Represents a section cut marker:
 *   ──●────────────────────●──
 *     A                    A
 *
 * Emitted as two circles (bubbles) + a dashed line + two text entities.
 */
data class CadSectionMarker(
    val x1: Double, val y1: Double,   // left bubble centre
    val x2: Double, val y2: Double,   // right bubble centre
    val label: String = "A",           // e.g. "A", "B", "1"
    val bubbleRadius: Double = 7.0,
    override val layer: String = CadLayers.CENTER,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = CadLineTypes.DASHED,
    override val lineWeight: Int = 13
) : CadEntity {
    override fun bounds() = doubleArrayOf(
        min(x1, x2) - bubbleRadius, min(y1, y2) - bubbleRadius,
        max(x1, x2) + bubbleRadius, max(y1, y2) + bubbleRadius
    )

    /** Decompose into primitive CAD entities for the DXF writer */
    fun toPrimitives(): List<CadEntity> = listOf(
        CadLine(x1, y1, x2, y2, layer = layer, lineType = CadLineTypes.DASHED),
        CadCircle(x1, y1, bubbleRadius, layer = layer),
        CadCircle(x2, y2, bubbleRadius, layer = layer),
        CadText(label, x1, y1 - 1.5, heightMm = bubbleRadius * 0.8,
                hJustify = 1, vJustify = 2, layer = CadLayers.TEXT),
        CadText(label, x2, y2 - 1.5, heightMm = bubbleRadius * 0.8,
                hJustify = 1, vJustify = 2, layer = CadLayers.TEXT)
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// ARROW  (filled arrowhead)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A filled arrowhead pointing from [tipX,tipY] in direction [angleDeg].
 * Rendered as a closed polyline (triangle).
 */
data class CadArrow(
    val tipX: Double, val tipY: Double,
    val angleDeg: Double,
    val lengthMm: Double = 4.0,
    val widthMm: Double = 1.5,
    override val layer: String = CadLayers.DIM,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    override fun bounds() = doubleArrayOf(
        tipX - lengthMm, tipY - widthMm, tipX + lengthMm, tipY + widthMm
    )

    fun toPolyline(): CadPolyline {
        val rad = Math.toRadians(angleDeg)
        val tip = Pt(tipX, tipY)
        val base = Pt(tipX - lengthMm * cos(rad), tipY - lengthMm * sin(rad))
        val left = base + Pt(-widthMm * sin(rad), widthMm * cos(rad))
        val right = base + Pt(widthMm * sin(rad), -widthMm * cos(rad))
        return CadPolyline(listOf(tip, left, right), closed = true,
                           layer = layer, color = color)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GRID LINE (axis label + dashed line)
// ═══════════════════════════════════════════════════════════════════════════

data class CadGridLine(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val label: String,              // "A", "B", "1", "2"
    val bubbleRadius: Double = 6.0,
    override val layer: String = CadLayers.GRID,
    override val color: Int = CadColors.GRAY,
    override val lineType: String = CadLineTypes.DASHED,
    override val lineWeight: Int = 13
) : CadEntity {
    override fun bounds() = doubleArrayOf(
        min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)
    )

    fun toPrimitives(): List<CadEntity> = listOf(
        CadLine(x1, y1, x2, y2, layer = layer, lineType = CadLineTypes.DASHED, color = color),
        CadCircle(x2, y2, bubbleRadius, layer = layer, color = color),
        CadText(label, x2, y2 - 2.0, heightMm = bubbleRadius * 0.8,
                hJustify = 1, vJustify = 2, layer = CadLayers.TEXT)
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// TABLE (schedule / BBS rows)
// ═══════════════════════════════════════════════════════════════════════════

data class CadTableCell(val text: String, val widthMm: Double, val heightMm: Double = 8.0)

data class CadTable(
    val x: Double, val y: Double,   // bottom-left origin
    val headers: List<CadTableCell>,
    val rows: List<List<String>>,
    val rowHeightMm: Double = 7.0,
    val textHeightMm: Double = 2.8,
    override val layer: String = CadLayers.SCHEDULE,
    override val color: Int = BY_LAYER_COLOR,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = BY_LAYER_WEIGHT
) : CadEntity {
    val totalWidthMm get() = headers.sumOf { it.widthMm }
    val totalHeightMm get() = (rows.size + 1) * rowHeightMm

    override fun bounds() = doubleArrayOf(
        x, y, x + totalWidthMm, y + totalHeightMm
    )

    /** Decompose into lines + text for DXF writer */
    fun toPrimitives(): List<CadEntity> {
        val result = mutableListOf<CadEntity>()
        val totalH = totalHeightMm

        // Outer border
        result.add(cadRect(x, y, totalWidthMm, totalH, layer = layer))

        var rowY = y + totalH
        // Header row
        rowY -= rowHeightMm
        var colX = x
        headers.forEach { cell ->
            result.add(CadLine(colX, rowY, colX, rowY + rowHeightMm, layer = layer))
            result.add(CadText(cell.text, colX + 2, rowY + rowHeightMm / 2 - textHeightMm / 2,
                               heightMm = textHeightMm, layer = layer))
            colX += cell.widthMm
        }
        result.add(CadLine(x, rowY, x + totalWidthMm, rowY, layer = layer))

        // Data rows
        rows.forEach { row ->
            rowY -= rowHeightMm
            colX = x
            headers.forEachIndexed { idx, cell ->
                val txt = row.getOrElse(idx) { "" }
                result.add(CadLine(colX, rowY, colX, rowY + rowHeightMm, layer = layer))
                result.add(CadText(txt, colX + 2, rowY + rowHeightMm / 2 - textHeightMm / 2,
                                   heightMm = textHeightMm, layer = layer))
                colX += cell.widthMm
            }
            result.add(CadLine(x, rowY, x + totalWidthMm, rowY, layer = layer))
        }
        return result
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PRESSURE / LOAD DIAGRAM (triangular distribution arrow set)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Represents a triangular or trapezoidal pressure distribution diagram.
 * [x1,y1] = top (zero or minimum pressure), [x2,y2] = base (maximum pressure).
 * [arrowCount] evenly-spaced arrows are drawn perpendicular to the wall face.
 */
data class CadPressureDiagram(
    val x1: Double, val y1: Double,     // start point (zero pressure side)
    val x2: Double, val y2: Double,     // end point (max pressure side)
    val pressureMaxKpa: Double,
    val pressureMinKpa: Double = 0.0,
    val diagramWidthMm: Double,         // paper width of max-pressure arrow
    val arrowCount: Int = 5,
    val label: String = "p",
    override val layer: String = CadLayers.PRESSURE,
    override val color: Int = CadColors.CYAN,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = 18
) : CadEntity {
    init {
        require(pressureMaxKpa >= 0) { "CadPressureDiagram: pressureMaxKpa must be >= 0" }
        require(arrowCount >= 2) { "CadPressureDiagram: arrowCount must be >= 2" }
        require(diagramWidthMm > 0) { "CadPressureDiagram: diagramWidthMm must be > 0" }
    }

    override fun bounds() = doubleArrayOf(
        min(x1, x2) - diagramWidthMm, min(y1, y2),
        max(x1, x2), max(y1, y2)
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// REBAR SYMBOL (filled circle with optional bar mark leader)
// ═══════════════════════════════════════════════════════════════════════════

data class CadRebarSymbol(
    val cx: Double, val cy: Double,
    val radiusMm: Double,           // ≈ diamMm / 2 scaled to paper
    val mark: String = "",
    val showMark: Boolean = true,
    override val layer: String = CadLayers.REBAR,
    override val color: Int = CadColors.RED,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = 18
) : CadEntity {
    init {
        require(radiusMm > 0) { "CadRebarSymbol: radius must be > 0" }
    }

    override fun bounds() = doubleArrayOf(
        cx - radiusMm, cy - radiusMm, cx + radiusMm, cy + radiusMm
    )

    fun toPrimitives(): List<CadEntity> {
        val result = mutableListOf<CadEntity>(
            CadCircle(cx, cy, radiusMm, layer = layer, color = color)
        )
        if (showMark && mark.isNotEmpty()) {
            result += CadText(mark, cx + radiusMm + 1.5, cy - 1.5,
                              heightMm = maxOf(radiusMm * 0.9, 1.8), layer = CadLayers.REBAR_DIM)
        }
        return result
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INTERACTION DIAGRAM POINT
// ═══════════════════════════════════════════════════════════════════════════

data class InteractionPoint(
    val axialKn: Double,
    val momentKnm: Double,
    val isCapacity: Boolean  // true = on capacity curve, false = design point
)

data class CadInteractionDiagram(
    val capacityPoints: List<InteractionPoint>,
    val designPoint: InteractionPoint,
    val x: Double, val y: Double,           // origin (P=0, M=0 corner)
    val widthMm: Double, val heightMm: Double,
    val maxAxialKn: Double,
    val maxMomentKnm: Double,
    override val layer: String = CadLayers.ANALYSIS,
    override val color: Int = CadColors.CYAN,
    override val lineType: String = LT_CONTINUOUS,
    override val lineWeight: Int = 18
) : CadEntity {
    init {
        require(capacityPoints.size >= 3) {
            "CadInteractionDiagram: need >= 3 capacity points"
        }
        require(maxAxialKn > 0 && maxMomentKnm > 0) {
            "CadInteractionDiagram: max values must be > 0"
        }
    }

    override fun bounds() = doubleArrayOf(x, y, x + widthMm, y + heightMm)
}

// ═══════════════════════════════════════════════════════════════════════════
// Utility: bounding box of a list of entities
// ═══════════════════════════════════════════════════════════════════════════

fun boundingBox(entities: List<CadEntity>): DoubleArray {
    if (entities.isEmpty()) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
    entities.forEach { e ->
        val b = e.bounds()
        if (b[0] < minX) minX = b[0]; if (b[1] < minY) minY = b[1]
        if (b[2] > maxX) maxX = b[2]; if (b[3] > maxY) maxY = b[3]
    }
    return doubleArrayOf(minX, minY, maxX, maxY)
}
