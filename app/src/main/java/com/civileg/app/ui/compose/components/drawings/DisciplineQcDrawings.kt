package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.ceil
import kotlin.math.max

// ============================================================================
// DISCIPLINE QC DRAWINGS
// ============================================================================
//
// Drawings that are derived directly from the values entered in the QC tabs:
//   * MasonryQcDrawing  - block coursing elevation + wall/plaster section.
//   * DrainageQcDrawing - sewer longitudinal section with manholes and slope.
// Every annotated value comes from an input; the pass/fail colours reuse the
// same limits as QualityControlEngineNew so the drawing cannot contradict the
// check list.
// ============================================================================

private val QcPassGreen = Color(0xFF2ECC71)
private val QcFailRed = Color(0xFFE74C3C)
private val QcAccent = Color(0xFF00BCD4)
private val QcLabelGray = Color(0xFFB0BEC5)
private val QcBlockFill = Color(0xFF5A5A5A)
private val QcMortarFill = Color(0xFF9E9E9E)
private val QcPlasterFill = Color(0xFF7E7E7E)
private val QcSoilFill = Color(0xFF4E3B1F)
private val QcPipeFill = Color(0xFF4A90D9)
private val QcWaterFill = Color(0x803498DB)
private val QcLayerFillA = Color(0xFF455A64)
private val QcLayerFillB = Color(0xFF607D8B)

// ============================================================================
// MASONRY
// ============================================================================

@Composable
fun MasonryQcDrawing(
    blockDimensions: String,
    mortarType: String,
    jointThickness: Double,
    wallThickness: Double,
    plasterThickness: Double,
    surfaceFlatness: Double,
    verticality: Double,
    mortarStrength: Double,
    designCode: DesignCode = DesignCode.ECP,
    modifier: Modifier = Modifier
) {
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxSize()) {
        val cw = size.width
        val ch = size.height
        val margin = cfg.margin
        val headerH = cfg.headerTextSize * 2.2f
        val footerH = cfg.annotationTextSmall * 2.4f
        val top = margin + headerH
        val bottom = ch - footerH - margin

        val dims = blockDimensions.split(Regex("[xX*]"))
            .mapNotNull { it.trim().toDoubleOrNull() }
        val blockLength = dims.getOrNull(2) ?: dims.getOrNull(0) ?: 400.0
        val blockHeight = dims.getOrNull(1) ?: dims.getOrNull(0) ?: 200.0
        val dimsValid = dims.size == 3 && dims.all { it > 0.0 }

        drawTextAnnotated(
            "MASONRY QC - BLOCK COURSING",
            margin, margin + cfg.headerTextSize,
            DrawingColorDefaults.DimensionWhite, cfg.headerTextSize, bold = true
        )
        drawTextAnnotated(
            "${designCode.version}  |  ${mortarType.ifBlank { "Mortar" }}  |  " +
                "Block $blockLength x $blockHeight mm",
            margin, margin + headerH - cfg.annotationTextSmall * 0.3f,
            QcAccent, cfg.annotationTextSmall
        )

        // ── Elevation (left) ─────────────────────────────────────────
        val splitX = margin + (cw - 2 * margin) * 0.62f
        drawBlockElevation(
            left = margin,
            right = splitX - margin * 0.6f,
            top = top,
            bottom = bottom,
            blockLength = blockLength,
            blockHeight = blockHeight,
            jointThickness = jointThickness,
            dimsValid = dimsValid,
            cfg = cfg
        )

        // ── Wall section (right) ─────────────────────────────────────
        drawWallSection(
            left = splitX + margin * 0.2f,
            right = cw - margin,
            top = top,
            bottom = bottom,
            wallThickness = wallThickness,
            plasterThickness = plasterThickness,
            jointThickness = jointThickness,
            mortarStrength = mortarStrength,
            surfaceFlatness = surfaceFlatness,
            verticality = verticality,
            cfg = cfg
        )

        // ── Footer: tolerances ───────────────────────────────────────
        val flatOk = surfaceFlatness <= 3.0
        val vertOk = verticality <= 5.0
        val jointOk = jointThickness in 8.0..12.0
        val strengthOk = mortarStrength >= 5.0
        val footerY = ch - footerH - margin * 0.3f
        val items = listOf(
            Triple("Flatness", "%.1f mm/m".format(surfaceFlatness), flatOk),
            Triple("Verticality", "%.1f mm/m".format(verticality), vertOk),
            Triple("Joint", "%.0f mm".format(jointThickness), jointOk),
            Triple("Mortar f'm", "%.1f MPa".format(mortarStrength), strengthOk),
            Triple("Block dims", if (dimsValid) "valid" else "invalid", dimsValid)
        )
        val cellW = (cw - 2 * margin) / items.size
        items.forEachIndexed { i, (label, value, ok) ->
            val x = margin + cellW * i
            drawTextAnnotated(label, x, footerY + cfg.annotationTextSmall,
                QcLabelGray, cfg.annotationTextSmall)
            drawTextAnnotated(value, x, footerY + cfg.annotationTextSmall * 2.3f,
                if (ok) QcPassGreen else QcFailRed, cfg.annotationTextSmall * 1.05f, bold = true)
        }
    }
}

private fun DrawScope.drawBlockElevation(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    blockLength: Double,
    blockHeight: Double,
    jointThickness: Double,
    dimsValid: Boolean,
    cfg: DrawingDimensionsConfig
) {
    val bandW = right - left
    val bandH = bottom - top
    if (bandW <= 0f || bandH <= 0f) return

    drawZoneLabel(left, top, "BLOCK COURSING", cfg)

    val panelTop = top + cfg.annotationTextSmall * 2.4f
    val panelH = bandH - cfg.annotationTextSmall * 5.0f
    val panelW = bandW * 0.86f
    if (panelH <= 0f || panelW <= 0f) return

    val courses = 7
    val courseH = panelH / courses
    val safeBlockH = if (blockHeight > 0.0) blockHeight else 200.0
    val safeBlockL = if (blockLength > 0.0) blockLength else 400.0
    val jointPx = max(1.0f, (jointThickness / safeBlockH * courseH).toFloat())
    val blockW = max(12f, (courseH * (safeBlockL / safeBlockH)).toFloat().coerceIn(0.4f * courseH, 3.0f * courseH))
    val blocksPerCourse = ceil(panelW / blockW).toInt() + 1

    // Wall background
    drawRect(
        color = QcMortarFill,
        topLeft = Offset(left, panelTop),
        size = Size(panelW, panelH)
    )

    for (course in 0 until courses) {
        val courseTop = panelTop + course * courseH
        val offsetX = if (course % 2 == 0) 0f else -blockW / 2f
        for (i in 0..blocksPerCourse) {
            val bx = left + offsetX + i * blockW
            val drawLeft = max(bx, left)
            val drawRight = minOf(bx + blockW - jointPx, left + panelW)
            if (drawRight - drawLeft <= 1f) continue
            val drawTop = courseTop + jointPx / 2f
            val drawBottom = courseTop + courseH - jointPx / 2f
            if (drawBottom - drawTop <= 1f) continue
            drawRect(
                color = if (dimsValid) QcBlockFill else QcFailRed.copy(alpha = 0.45f),
                topLeft = Offset(drawLeft, drawTop),
                size = Size(drawRight - drawLeft, drawBottom - drawTop)
            )
        }
    }

    // Panel outline
    drawRect(
        color = DrawingColorDefaults.DimensionWhite,
        topLeft = Offset(left, panelTop),
        size = Size(panelW, panelH),
        style = Stroke(cfg.strokeDefault)
    )

    // Dimensions: one block + one joint
    val dimX = left + panelW + cfg.annotationTextSmall * 1.2f
    drawDimArrow(dimX, panelTop, dimX, panelTop + courseH, cfg)
    drawTextAnnotated(
        "%.0f".format(safeBlockH), dimX + cfg.annotationTextSmall * 0.4f,
        panelTop + courseH * 0.5f + cfg.annotationTextSmall * 0.35f,
        QcLabelGray, cfg.annotationTextSmall
    )
    drawDimArrow(dimX, panelTop + courseH, dimX, panelTop + 2 * courseH, cfg)
    drawTextAnnotated(
        "%.0f".format(safeBlockL), dimX + cfg.annotationTextSmall * 0.4f,
        panelTop + courseH * 1.5f + cfg.annotationTextSmall * 0.35f,
        QcLabelGray, cfg.annotationTextSmall
    )

    drawTextAnnotated(
        "Joint %.0f mm".format(jointThickness),
        left, panelTop + panelH + cfg.annotationTextSmall * 1.6f,
        if (jointThickness in 8.0..12.0) QcPassGreen else QcFailRed,
        cfg.annotationTextSmall, bold = true
    )
}

private fun DrawScope.drawWallSection(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    wallThickness: Double,
    plasterThickness: Double,
    jointThickness: Double,
    mortarStrength: Double,
    surfaceFlatness: Double,
    verticality: Double,
    cfg: DrawingDimensionsConfig
) {
    val bandW = right - left
    val bandH = bottom - top
    if (bandW <= 0f || bandH <= 0f) return

    drawZoneLabel(left, top, "WALL SECTION", cfg)

    val panelTop = top + cfg.annotationTextSmall * 2.6f
    val panelH = bandH * 0.48f
    val wallPx = max(18f, minOf(bandW * 0.42f, panelH * 0.45f))
    val plasterPx = max(
        3f,
        (plasterThickness / max(wallThickness, 1.0) * wallPx).toFloat()
    )
    val totalW = wallPx + plasterPx * 2f
    val startX = left + (bandW - totalW) / 2f

    // Plaster layers
    drawRect(
        color = QcPlasterFill,
        topLeft = Offset(startX, panelTop),
        size = Size(plasterPx, panelH)
    )
    drawRect(
        color = QcPlasterFill,
        topLeft = Offset(startX + plasterPx + wallPx, panelTop),
        size = Size(plasterPx, panelH)
    )
    // Blockwork core
    drawRect(
        color = QcBlockFill,
        topLeft = Offset(startX + plasterPx, panelTop),
        size = Size(wallPx, panelH)
    )
    // Joint lines in section
    val rows = 6
    for (r in 1 until rows) {
        val y = panelTop + panelH * r / rows
        drawLine(
            color = QcMortarFill,
            start = Offset(startX + plasterPx, y),
            end = Offset(startX + plasterPx + wallPx, y),
            strokeWidth = max(1f, cfg.strokeDefault * 0.6f)
        )
    }
    drawRect(
        color = DrawingColorDefaults.DimensionWhite,
        topLeft = Offset(startX, panelTop),
        size = Size(totalW, panelH),
        style = Stroke(cfg.strokeDefault)
    )

    // Wall thickness dimension under the section
    drawDimArrow(
        startX + plasterPx, panelTop + panelH + cfg.annotationTextSmall,
        startX + plasterPx + wallPx, panelTop + panelH + cfg.annotationTextSmall, cfg
    )
    drawTextAnnotated(
        "t = %.0f mm".format(wallThickness),
        startX + plasterPx + wallPx / 2f,
        panelTop + panelH + cfg.annotationTextSmall * 2.2f,
        QcLabelGray, cfg.annotationTextSmall, center = true
    )
    drawTextAnnotated(
        "Plaster %.0f mm / face".format(plasterThickness),
        startX + totalW / 2f,
        panelTop + panelH + cfg.annotationTextSmall * 3.6f,
        QcLabelGray, cfg.annotationTextSmall, center = true
    )

    // Tolerance status block
    val statusTop = panelTop + panelH + cfg.annotationTextSmall * 5.2f
    val flatOk = surfaceFlatness <= 3.0
    val vertOk = verticality <= 5.0
    val jointOk = jointThickness in 8.0..12.0
    val strengthOk = mortarStrength >= 5.0
    val rowsText = listOf(
        Triple("Flatness", "%.1f mm/m".format(surfaceFlatness), flatOk),
        Triple("Verticality", "%.1f mm/m".format(verticality), vertOk),
        Triple("Mortar joint", "%.0f mm".format(jointThickness), jointOk),
        Triple("Mortar f'm", "%.1f MPa".format(mortarStrength), strengthOk)
    )
    val rowH = cfg.annotationTextSmall * 1.6f
    rowsText.forEachIndexed { i, (label, value, ok) ->
        val y = statusTop + i * rowH
        if (y > bottom) return
        drawTextAnnotated(label, left, y, QcLabelGray, cfg.annotationTextSmall)
        drawTextAnnotated(value, right, y, if (ok) QcPassGreen else QcFailRed,
            cfg.annotationTextSmall, bold = true)
    }
}

// ============================================================================
// DRAINAGE
// ============================================================================

@Composable
fun DrainageQcDrawing(
    pipeMaterial: String,
    pipeDiameter: Double,
    pipeLength: Double,
    slope: Double,
    manholeSpacing: Double,
    manholeDepth: Double,
    invertLevel: Double,
    backfillCompaction: Double,
    modifier: Modifier = Modifier
) {
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxSize()) {
        val cw = size.width
        val ch = size.height
        val margin = cfg.margin
        val headerH = cfg.headerTextSize * 2.2f
        val footerH = cfg.annotationTextSmall * 2.4f
        val top = margin + headerH
        val bottom = ch - footerH - margin

        drawTextAnnotated(
            "DRAINAGE QC - LONGITUDINAL SECTION",
            margin, margin + cfg.headerTextSize,
            DrawingColorDefaults.DimensionWhite, cfg.headerTextSize, bold = true
        )
        drawTextAnnotated(
            "${pipeMaterial.ifBlank { "Pipe" }}  |  DN %.0f mm  |  L = %.1f m  |  S = %.2f %%"
                .format(pipeDiameter, pipeLength, slope),
            margin, margin + headerH - cfg.annotationTextSmall * 0.3f,
            QcAccent, cfg.annotationTextSmall
        )

        val bandW = cw - 2 * margin
        val bandH = bottom - top
        if (bandW <= 0f || bandH <= 0f) return@Canvas

        drawZoneLabel(margin, top, "SECTION A-A", cfg)

        val len = if (pipeLength > 0.0) pipeLength else 1.0
        val safeDiameter = pipeDiameter.coerceAtLeast(100.0)
        val drop = len * (slope / 100.0).coerceIn(0.0, 0.2)

        val sectionTop = top + cfg.annotationTextSmall * 2.6f
        val sectionH = bandH * 0.62f
        val pipeY = sectionTop + sectionH * 0.72f
        val startX = margin + cfg.annotationTextSmall * 2.0f
        val endX = margin + bandW - cfg.annotationTextSmall * 2.0f
        val dx = endX - startX
        // Vertical drop exaggerated for legibility, but labelled with real value.
        val dropPx = if (len > 0.0) (drop / len).toFloat() * dx * 3.0f else 0f

        // Ground line + backfill
        val groundY = sectionTop + sectionH * 0.30f
        drawLine(
            color = DrawingColorDefaults.SoilBrown,
            start = Offset(startX - 20f, groundY),
            end = Offset(endX + 20f, groundY),
            strokeWidth = cfg.strokeDefault * 1.5f
        )
        drawRect(
            color = QcSoilFill.copy(alpha = 0.55f),
            topLeft = Offset(startX - 20f, groundY),
            size = Size(
                dx + 40f,
                max(0f, pipeY + (safeDiameter / 2000.0).toFloat() * sectionH - groundY)
            )
        )
        // Backfill compaction annotation
        drawTextAnnotated(
            "Backfill %.0f %%".format(backfillCompaction),
            margin, groundY - cfg.annotationTextSmall * 0.8f,
            if (backfillCompaction >= 95.0) QcPassGreen else QcFailRed,
            cfg.annotationTextSmall, bold = true
        )

        // Pipe: two parallel lines, sloping down towards the outlet.
        val pipePx = max(6f, (safeDiameter / 1000.0 / len * dx * 1.6f).toFloat())
        val startInvert = pipeY
        val endInvert = pipeY + dropPx
        val pipePath = listOf(
            Offset(startX, startInvert),
            Offset(endX, endInvert)
        )
        drawLine(
            color = QcPipeFill,
            start = pipePath[0],
            end = pipePath[1],
            strokeWidth = pipePx
        )
        // Water surface inside the pipe
        drawLine(
            color = QcWaterFill,
            start = Offset(startX, startInvert + pipePx * 0.2f),
            end = Offset(endX, endInvert + pipePx * 0.2f),
            strokeWidth = pipePx * 0.5f
        )

        // Manholes at the requested spacing
        val spacing = if (manholeSpacing > 0.0) manholeSpacing else len
        val count = max(2, ceil(len / spacing).toInt() + 1).coerceAtMost(6)
        val manholeW = max(14f, cfg.annotationTextSmall * 1.4f)
        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1)
            val x = startX + dx * t
            val invertY = startInvert + (endInvert - startInvert) * t
            val coverY = groundY
            val depthPx = invertY - coverY
            if (depthPx <= 0f) continue
            drawRect(
                color = QcSoilFill.copy(alpha = 0.0f),
                topLeft = Offset(x - manholeW / 2f, coverY),
                size = Size(manholeW, depthPx),
                style = Stroke(cfg.strokeDefault)
            )
            // Cover slab
            drawLine(
                color = DrawingColorDefaults.DimensionWhite,
                start = Offset(x - manholeW, coverY),
                end = Offset(x + manholeW, coverY),
                strokeWidth = cfg.strokeDefault * 2f
            )
            // Invert level tag
            val level = invertLevel - drop * t
            drawTextAnnotated(
                "IL %.2f".format(level),
                x, invertY + cfg.annotationTextSmall * 1.5f,
                QcLabelGray, cfg.annotationTextSmall, center = true
            )
        }

        // Slope + length dimensions
        drawDimArrow(startX, endInvert + cfg.annotationTextSmall * 3.2f,
            endX, endInvert + cfg.annotationTextSmall * 3.2f, cfg)
        drawTextAnnotated(
            "L = %.1f m".format(pipeLength),
            startX + dx / 2f, endInvert + cfg.annotationTextSmall * 4.4f,
            QcLabelGray, cfg.annotationTextSmall, center = true
        )

        // Manhole depth dimension on the first manhole
        drawDimArrow(startX - cfg.annotationTextSmall * 1.4f, groundY,
            startX - cfg.annotationTextSmall * 1.4f, startInvert, cfg)
        drawTextAnnotated(
            "H = %.1f m".format(manholeDepth),
            startX - cfg.annotationTextSmall * 1.9f,
            (groundY + startInvert) / 2f, QcLabelGray, cfg.annotationTextSmall
        )

        // ── Footer ───────────────────────────────────────────────────
        val slopeOk = slope >= 1.0
        val spacingOk = manholeSpacing <= 50.0
        val depthOk = manholeDepth > 0.0
        val pipeOk = pipeMaterial.trim().lowercase() in setOf(
            "rcp", "reinforced concrete", "concrete", "pvc", "upvc", "hdpe",
            "di", "ductile iron", "steel", "grp", "vitrified clay"
        )
        val items = listOf(
            Triple("Slope", "%.2f %%".format(slope), slopeOk),
            Triple("Manhole spacing", "%.1f m".format(manholeSpacing), spacingOk),
            Triple("Manhole depth", "%.1f m".format(manholeDepth), depthOk),
            Triple("Pipe material", pipeMaterial.ifBlank { "-" }, pipeOk)
        )
        val footerY = ch - footerH - margin * 0.3f
        val cellW = (cw - 2 * margin) / items.size
        items.forEachIndexed { i, (label, value, ok) ->
            val x = margin + cellW * i
            drawTextAnnotated(label, x, footerY + cfg.annotationTextSmall,
                QcLabelGray, cfg.annotationTextSmall)
            drawTextAnnotated(value, x, footerY + cfg.annotationTextSmall * 2.3f,
                if (ok) QcPassGreen else QcFailRed, cfg.annotationTextSmall * 1.05f, bold = true)
        }
    }
}

// ============================================================================
// GENERIC DISCIPLINE SECTION
// ============================================================================

data class QcDrawingLayer(val label: String, val thickness: Double)
data class QcDrawingMetric(val label: String, val value: String)

@Composable
fun DisciplineQcGenericDrawing(
    title: String,
    subtitle: String,
    layers: List<QcDrawingLayer>,
    metrics: List<QcDrawingMetric>,
    passed: Boolean,
    designCode: DesignCode = DesignCode.ECP,
    modifier: Modifier = Modifier
) {
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxSize()) {
        val cw = size.width
        val ch = size.height
        val margin = cfg.margin
        val headerH = cfg.headerTextSize * 2.4f
        val footerH = cfg.annotationTextSmall * 1.8f
        val top = margin + headerH
        val bottom = ch - footerH - margin

        drawTextAnnotated(
            title,
            margin, margin + cfg.headerTextSize,
            DrawingColorDefaults.DimensionWhite, cfg.headerTextSize, bold = true
        )
        drawTextAnnotated(
            "${designCode.version}  |  $subtitle",
            margin, margin + headerH - cfg.annotationTextSmall * 0.3f,
            QcAccent, cfg.annotationTextSmall
        )
        drawTextAnnotated(
            if (passed) "PASS" else "REVIEW",
            cw - margin - cfg.annotationTextSmall * 4.0f, margin + cfg.headerTextSize,
            if (passed) QcPassGreen else QcFailRed,
            cfg.annotationTextSmall * 1.1f, bold = true
        )

        val bandW = cw - 2f * margin
        val bandH = bottom - top
        if (bandW <= 0f || bandH <= 0f) return@Canvas

        val splitX = margin + bandW * 0.58f
        drawZoneLabel(margin, top, "TYPICAL SECTION", cfg)

        val stackTop = top + cfg.annotationTextSmall * 2.8f
        val stackBottom = bottom - cfg.annotationTextSmall * 1.8f
        val stackH = (stackBottom - stackTop).coerceAtLeast(1f)
        val stack = if (layers.isEmpty()) listOf(QcDrawingLayer("Section", 1.0)) else layers
        val totalThickness = stack.sumOf { it.thickness.coerceAtLeast(0.001) }
        val stackW = (splitX - margin - cfg.annotationTextSmall * 1.4f).coerceAtLeast(1f)

        var y = stackTop
        stack.forEachIndexed { index, layer ->
            val share = (layer.thickness.coerceAtLeast(0.001) / totalThickness).toFloat()
            val h = (stackH * share).coerceAtLeast(1f)
            val fill = if (index % 2 == 0) QcLayerFillA else QcLayerFillB
            drawRect(fill, Offset(margin, y), Size(stackW, h))
            drawRect(
                DrawingColorDefaults.DimensionWhite,
                Offset(margin, y), Size(stackW, h),
                style = Stroke(cfg.strokeDefault * 0.7f)
            )
            if (h > cfg.annotationTextSmall * 1.1f) {
                drawTextAnnotated(
                    layer.label,
                    margin + cfg.annotationTextSmall * 0.4f,
                    y + h * 0.55f,
                    DrawingColorDefaults.DimensionWhite,
                    cfg.annotationTextSmall * 0.8f
                )
            }
            drawTextAnnotated(
                "%.1f mm".format(layer.thickness),
                margin + stackW + cfg.annotationTextSmall * 0.3f,
                y + h * 0.55f,
                QcLabelGray, cfg.annotationTextSmall * 0.8f
            )
            y += h
        }

        drawDimArrow(
            margin - cfg.annotationTextSmall * 1.1f, stackTop,
            margin - cfg.annotationTextSmall * 1.1f, stackBottom, cfg
        )
        drawTextAnnotated(
            "Total %.0f mm".format(totalThickness),
            margin, stackBottom + cfg.annotationTextSmall * 1.2f,
            QcLabelGray, cfg.annotationTextSmall
        )

        val metricX = splitX + cfg.annotationTextSmall * 1.2f
        val metricTop = top + cfg.annotationTextSmall * 3.0f
        val metricStep = if (metrics.isEmpty()) 0f else
            (bottom - metricTop - cfg.annotationTextSmall) / metrics.size
        metrics.take(6).forEachIndexed { index, metric ->
            val my = metricTop + metricStep * index
            drawTextAnnotated(
                metric.label, metricX, my,
                QcLabelGray, cfg.annotationTextSmall * 0.8f
            )
            drawTextAnnotated(
                metric.value, metricX, my + cfg.annotationTextSmall * 1.1f,
                QcAccent, cfg.annotationTextSmall, bold = true
            )
        }

        drawTextAnnotated(
            "Layers ${stack.size}  |  ${if (passed) "compliant" else "review required"}",
            margin, ch - margin,
            QcLabelGray, cfg.annotationTextSmall
        )
    }
}

// ============================================================================
// SHARED HELPERS
// ============================================================================

private fun DrawScope.drawDimArrow(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    cfg: DrawingDimensionsConfig
) {
    drawLine(
        color = QcLabelGray,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = max(0.8f, cfg.strokeDefault * 0.6f),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
    )
    val tick = cfg.annotationTextSmall * 0.45f
    drawLine(QcLabelGray, Offset(x1, y1 - tick), Offset(x1, y1 + tick), cfg.strokeDefault)
    drawLine(QcLabelGray, Offset(x2, y2 - tick), Offset(x2, y2 + tick), cfg.strokeDefault)
}
