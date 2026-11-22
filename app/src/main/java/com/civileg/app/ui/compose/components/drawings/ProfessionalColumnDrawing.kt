package com.civileg.app.ui.compose.components.drawings

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.StirrupZone
import java.util.Locale
import kotlin.math.*

data class BarInfo(val x: Double, val y: Double, val diameter: Double, val isCorner: Boolean = false)

/** Display boxes only, never an engineering geometry/layout generator. Units are logical pixels. */
internal data class ColumnSheetBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    fun inset(p: Float) = ColumnSheetBox(x + p, y + p, width - 2 * p, height - 2 * p)
    fun contains(other: ColumnSheetBox, tolerance: Float = 0.1f) =
        other.x >= x - tolerance && other.y >= y - tolerance &&
            other.right <= right + tolerance && other.bottom <= bottom + tolerance
    fun intersects(other: ColumnSheetBox) = x < other.right && right > other.x && y < other.bottom && bottom > other.y
}

internal data class ColumnSheetLayout(
    val width: Float, val height: Float, val scale: Float, val offsetX: Float, val offsetY: Float,
    val header: ColumnSheetBox, val projection: ColumnSheetBox?, val section: ColumnSheetBox?,
    val parameters: ColumnSheetBox, val footer: ColumnSheetBox
) {
    val boxes get() = listOfNotNull(header, projection, section, parameters, footer)
}

/** A single layout model owns every panel. Header, metadata and footer cannot share drawing space. */
internal fun columnSheetLayout(w: Float, h: Float, mode: Int, parameterRows: Int): ColumnSheetLayout? {
    if (!w.isFinite() || !h.isFinite() || w <= 0 || h <= 0 || mode !in 0..3 || parameterRows < 0) return null
    val pageWidth = 720f
    val parameterHeight = 52f + parameterRows * 27f
    val minimumHeight = 100f + (if (mode == 3) 0f else 440f) + parameterHeight + 164f + 36f
    val pageHeight = max(minimumHeight, pageWidth * h / w)
    val scale = min(w / pageWidth, h / pageHeight)
    // Do not squeeze text into unreadable output. Fail closed instead of clipping rows.
    if (!scale.isFinite() || scale < 0.40f) return null
    val header = ColumnSheetBox(16f, 12f, 688f, 76f)
    val footer = ColumnSheetBox(16f, pageHeight - 164f, 688f, 148f)
    val parameters = ColumnSheetBox(16f, footer.y - 12f - parameterHeight, 688f, parameterHeight)
    val top = 100f
    val bodyHeight = parameters.y - 12f - top
    val projection = when (mode) {
        0 -> ColumnSheetBox(16f, top, 338f, bodyHeight)
        1 -> ColumnSheetBox(16f, top, 688f, bodyHeight)
        else -> null
    }
    val section = when (mode) {
        0 -> ColumnSheetBox(366f, top, 338f, bodyHeight)
        2 -> ColumnSheetBox(16f, top, 688f, bodyHeight)
        else -> null
    }
    return ColumnSheetLayout(pageWidth, pageHeight, scale, (w - pageWidth * scale) / 2,
        (h - pageHeight * scale) / 2, header, projection, section, parameters, footer)
}

/** Finite model-to-display transform. The independent width/height limits use all available portrait height. */
internal fun columnFitScale(box: ColumnSheetBox, modelWidth: Double, modelHeight: Double): Double? {
    if (!box.width.isFinite() || !box.height.isFinite() || box.width <= 0 || box.height <= 0 ||
        !modelWidth.isFinite() || !modelHeight.isFinite() || modelWidth <= 0 || modelHeight <= 0) return null
    return min(box.width.toDouble() / modelWidth, box.height.toDouble() / modelHeight)
        .takeIf { it.isFinite() && it > 0 }
}

/** Reject obviously unusable coordinates, without moving, adding, deleting or normalizing supplied bars.
 * Passing this check does NOT establish cover, spacing, containment, anchorage or code compliance.
 */
internal fun columnCoordinateIssue(width: Double, depth: Double, height: Double, bars: List<BarInfo>, circular: Boolean): String? {
    if (listOf(width, depth, height).any { !it.isFinite() || it <= 0 || it > 1e8 }) return "Invalid member dimensions. Geometry withheld."
    if (bars.any { !it.x.isFinite() || !it.y.isFinite() || !it.diameter.isFinite() || it.diameter <= 0 }) return "Invalid supplied bar data. Geometry withheld."
    if (bars.any { it.x !in 0.0..width || it.y !in 0.0..depth }) return "Supplied centers outside section. Geometry withheld."
    if (bars.map { it.x to it.y }.distinct().size != bars.size) return "Duplicate supplied bar centers. Geometry withheld."
    if (circular && (width != depth || bars.any { hypot(it.x - width / 2, it.y - depth / 2) > width / 2 }))
        return "Inconsistent circular coordinates. Geometry withheld."
    return null
}

internal data class ColumnSheetText(val text: String, val bounds: ColumnSheetBox, val panel: ColumnSheetBox)
internal data class ColumnSheetEvidence(
    val layout: ColumnSheetLayout?, val text: MutableList<ColumnSheetText> = mutableListOf(),
    val suppliedBars: List<BarInfo>, val drawnSectionCenters: MutableList<Pair<Double, Double>> = mutableListOf(),
    val geometryIssue: String?, var allTextFits: Boolean = true,
    var projectionScale: Double? = null, var sectionScale: Double? = null
)

@Composable
fun ProfessionalColumnDrawing(
    columnWidth: Double, columnDepth: Double, columnHeight: Double, longitudinalBars: List<BarInfo>,
    tieDia: Double, tieSpacing: Double, cover: Double, isSpiral: Boolean = false, spiralPitch: Double = 0.0,
    sectionType: String = "Rectangular", zones: List<StirrupZone> = emptyList(),
    interactionPoints: List<Pair<Double, Double>> = emptyList(), designPoint: Pair<Double, Double> = Pair(0.0, 0.0),
    designCode: DesignCode = DesignCode.ECP, viewMode: Int = 0, resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        renderColumnSheet(drawContext.canvas.nativeCanvas, size.width, size.height, columnWidth, columnDepth,
            columnHeight, longitudinalBars, tieDia, tieSpacing, cover, isSpiral, spiralPitch, sectionType,
            zones, interactionPoints, designPoint, designCode, viewMode, resultSummary)
    }
}

/** The actual production Canvas painter is also used by native pixel/PDF regression tests.
 * No inferred slab, hook, anchorage, crosstie or default/capped tie-position geometry is drawn.
 * Bar positions are caller-supplied legacy schematic coordinates, not a verified detail contract.
 */
internal fun renderColumnSheet(
    canvas: AndroidCanvas, widthPx: Float, heightPx: Float,
    columnWidth: Double, columnDepth: Double, columnHeight: Double, longitudinalBars: List<BarInfo>,
    tieDia: Double, tieSpacing: Double, cover: Double, isSpiral: Boolean = false, spiralPitch: Double = 0.0,
    sectionType: String = "Rectangular", zones: List<StirrupZone> = emptyList(),
    interactionPoints: List<Pair<Double, Double>> = emptyList(), designPoint: Pair<Double, Double> = Pair(0.0, 0.0),
    designCode: DesignCode = DesignCode.ECP, viewMode: Int = 0, resultSummary: DrawingResultSummary? = null
): ColumnSheetEvidence {
    val bars = longitudinalBars.toList()
    val circular = sectionType.equals("Circular", true)
    val issue = columnCoordinateIssue(columnWidth, columnDepth, columnHeight, bars, circular)
    fun number(value: Double) = if (value.isFinite()) String.format(Locale.US, "%.1f", value) else "unavailable"
    val groups = bars.groupBy { it.diameter }
    val rows = mutableListOf(
        "Member: ${number(columnWidth)} x ${number(columnDepth)} x ${number(columnHeight)} mm",
        "Supplied centers: ${bars.size}. Coordinate arrangement is not verified."
    )
    groups.forEach { (diameter, group) -> rows += "Supplied group: ${group.size} bars, diameter ${number(diameter)} mm" }
    if (bars.isEmpty()) rows += "No bar coordinates supplied. No substitute bars generated."
    rows += "Tie parameter: diameter ${number(tieDia)} mm; ${if (isSpiral) "pitch ${number(spiralPitch)}" else "spacing ${number(tieSpacing)}"} mm"
    rows += "Cover parameter: ${number(cover)} mm, legacy input, not a verified detail"
    zones.forEachIndexed { index, zone ->
        rows += "Zone ${index + 1}: ${number(zone.startLocation)} to ${number(zone.endLocation)} mm; spacing ${number(zone.spacing)} mm"
    }
    if (zones.isEmpty()) rows += "No tie zones supplied. Tie positions not inferred."
    // This legacy API has no verified curve coordinate convention or provenance. Do not imply a safe region.
    if (interactionPoints.isNotEmpty() || designPoint != (0.0 to 0.0)) rows += "Interaction data supplied. Verified curve plot is not available in this schematic."
    val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f }
    val parameterLines = rows.flatMap { row ->
        val lines = mutableListOf<String>()
        var line = ""
        row.split(' ').forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && measurePaint.measureText(next) > 664f) { lines += line; line = word }
            else line = next
        }
        if (line.isNotEmpty()) lines += line
        lines
    }
    val layout = columnSheetLayout(widthPx, heightPx, viewMode, parameterLines.size)
    val evidence = ColumnSheetEvidence(layout, suppliedBars = bars, geometryIssue = issue)
    val background = 0xFF1A1A2E.toInt()
    val ink = 0xFFE5EDF4.toInt()
    val accent = 0xFF38BDF8.toInt()
    val border = 0xFF657487.toInt()
    canvas.drawColor(background)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (layout == null) {
        paint.color = ink; paint.textSize = 13f
        canvas.drawText("Viewport too small. Enlarge or select", 8f, 24f, paint)
        canvas.drawText("a single view. Geometry withheld.", 8f, 44f, paint)
        return evidence
    }
    val save = canvas.save()
    canvas.translate(layout.offsetX, layout.offsetY)
    canvas.scale(layout.scale, layout.scale)
    fun text(value: String, panel: ColumnSheetBox, x: Float, baseline: Float, size: Float = 22f, color: Int = ink, bold: Boolean = false) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size; paint.isFakeBoldText = bold
        val metrics = paint.fontMetrics
        val bounds = ColumnSheetBox(x, baseline + metrics.ascent, paint.measureText(value), metrics.descent - metrics.ascent)
        evidence.text += ColumnSheetText(value, bounds, panel)
        if (!panel.contains(bounds)) evidence.allTextFits = false
        canvas.drawText(value, x, baseline, paint)
    }
    fun wrapped(value: String, panel: ColumnSheetBox, startY: Float, size: Float = 22f): Float {
        paint.textSize = size; paint.isFakeBoldText = false
        val words = value.split(' ')
        var line = ""; var baseline = startY
        words.forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(next) > panel.width - 24f) {
                text(line, panel, panel.x + 12f, baseline, size); baseline += size + 5f; line = word
            } else line = next
        }
        if (line.isNotEmpty()) { text(line, panel, panel.x + 12f, baseline, size); baseline += size + 5f }
        return baseline
    }
    fun frame(box: ColumnSheetBox, label: String) {
        paint.color = border; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        canvas.drawRect(box.x, box.y, box.right, box.bottom, paint)
        text(label, box, box.x + 12f, box.y + 30f, 23f, accent, true)
    }
    text("COLUMN SCHEMATIC | ${listOf("OVERVIEW", "3D PROJECTION", "SECTION", "PARAMETERS")[viewMode]}", layout.header, 20f, 38f, 26f, accent, true)
    text("Recorded inputs only. NOT FOR EXECUTION.", layout.header, 20f, 70f, 22f)

    layout.projection?.let { box ->
        frame(box, "MEMBER PROJECTION")
        if (issue != null) wrapped(issue, box, box.y + 80f)
        else {
            val inner = ColumnSheetBox(box.x + 32f, box.y + 66f, box.width - 64f, box.height - 112f)
            val cos30 = sqrt(3.0) / 2
            val modelW = (columnWidth + columnDepth) * cos30
            val modelH = columnHeight + (columnWidth + columnDepth) / 2
            val scale = requireNotNull(columnFitScale(inner, modelW, modelH))
            evidence.projectionScale = scale
            val originX = inner.x + (inner.width - modelW * scale).toFloat() / 2 + (columnDepth * cos30 * scale).toFloat()
            val originY = inner.y + (inner.height - modelH * scale).toFloat() / 2
            fun point(x: Double, y: Double, z: Double) =
                (originX + ((x - y) * cos30 * scale).toFloat()) to
                    (originY + ((x + y) / 2 * scale + (columnHeight - z) * scale).toFloat())
            fun segment(a: Pair<Float, Float>, b: Pair<Float, Float>, color: Int, stroke: Float) {
                paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = stroke
                canvas.drawLine(a.first, a.second, b.first, b.second, paint)
            }
            if (!circular) {
                val corners = listOf(0.0 to 0.0, columnWidth to 0.0, columnWidth to columnDepth, 0.0 to columnDepth)
                for (i in corners.indices) {
                    val a = corners[i]; val b = corners[(i + 1) % corners.size]
                    segment(point(a.first, a.second, 0.0), point(b.first, b.second, 0.0), border, 2f)
                    segment(point(a.first, a.second, columnHeight), point(b.first, b.second, columnHeight), ink, 2f)
                    segment(point(a.first, a.second, 0.0), point(a.first, a.second, columnHeight), border, 2f)
                }
            } else {
                // Isometric projection of the supplied circular section, not a guessed rectangular body.
                for (z in listOf(0.0, columnHeight)) {
                    val outline = Path()
                    for (i in 0..96) {
                        val angle = 2 * PI * i / 96
                        val p = point(columnWidth / 2 * (1 + cos(angle)), columnDepth / 2 * (1 + sin(angle)), z)
                        if (i == 0) outline.moveTo(p.first, p.second) else outline.lineTo(p.first, p.second)
                    }
                    paint.style = Paint.Style.STROKE; paint.color = border; paint.strokeWidth = 2f; canvas.drawPath(outline, paint)
                }
                for (angle in listOf(-PI / 4, 3 * PI / 4)) {
                    val x = columnWidth / 2 * (1 + cos(angle)); val y = columnDepth / 2 * (1 + sin(angle))
                    segment(point(x, y, 0.0), point(x, y, columnHeight), border, 2f)
                }
            }
            bars.forEach { bar -> segment(point(bar.x, bar.y, 0.0), point(bar.x, bar.y, columnHeight), accent, 2f) }
            text("H = ${number(columnHeight)} mm", box, box.x + 12f, box.bottom - 18f, 20f)
        }
    }
    layout.section?.let { box ->
        frame(box, "SUPPLIED SECTION")
        if (issue != null) wrapped(issue, box, box.y + 80f)
        else {
            val inner = ColumnSheetBox(box.x + 34f, box.y + 90f, box.width - 68f, box.height - 156f)
            val scale = requireNotNull(columnFitScale(inner, columnWidth, columnDepth))
            evidence.sectionScale = scale
            val dw = (columnWidth * scale).toFloat(); val dh = (columnDepth * scale).toFloat()
            val left = inner.x + (inner.width - dw) / 2; val top = inner.y + (inner.height - dh) / 2
            paint.style = Paint.Style.FILL; paint.color = 0xFF454C59.toInt()
            val outline = RectF(left, top, left + dw, top + dh)
            if (circular) canvas.drawOval(outline, paint) else canvas.drawRect(outline, paint)
            paint.style = Paint.Style.STROKE; paint.color = border; paint.strokeWidth = 2f
            if (circular) canvas.drawOval(outline, paint) else canvas.drawRect(outline, paint)
            bars.forEach { bar ->
                val x = left + (bar.x * scale).toFloat(); val y = top + (bar.y * scale).toFloat()
                paint.style = Paint.Style.FILL; paint.color = accent
                canvas.drawCircle(x, y, max(1.5f, (bar.diameter * scale / 2).toFloat()), paint)
                evidence.drawnSectionCenters += bar.x to bar.y
            }
            text("W = ${number(columnWidth)} mm", box, box.x + 12f, box.y + 66f, 20f)
            text("D = ${number(columnDepth)} mm", box, box.x + 12f, box.bottom - 40f, 20f)
            text("Markers enlarged; tie paths withheld.", box, box.x + 12f, box.bottom - 16f, 16f)
        }
    }
    frame(layout.parameters, "SUPPLIED PARAMETERS, NOT A BBS")
    var baseline = layout.parameters.y + 59f
    parameterLines.forEach { row -> text(row, layout.parameters, layout.parameters.x + 12f, baseline, 21f); baseline += 27f }
    val footer = layout.footer
    frame(footer, "SCHEMATIC LIMITS")
    val state = when (resultSummary?.isSafe) {
        false -> "Engine flag: UNSAFE"
        true -> "Engine flag: REPORTED SAFE, method not verified"
        null -> "Engine status not supplied"
    }
    text(state, footer, footer.x + 12, footer.y + 60, 21f,
        if (resultSummary?.isSafe == false) 0xFFFF7373.toInt() else ink)
    text("Requested code family: ${designCode.name}. Edition not verified.", footer, footer.x + 12, footer.y + 86, 20f)
    text("Tie paths, hooks, anchorage and connected slabs not drawn.", footer, footer.x + 12, footer.y + 112, 20f)
    text("Fitted display, NTS. Dimensions in mm. Not a checked layout.", footer, footer.x + 12, footer.y + 136, 20f)
    canvas.restoreToCount(save)
    return evidence
}
