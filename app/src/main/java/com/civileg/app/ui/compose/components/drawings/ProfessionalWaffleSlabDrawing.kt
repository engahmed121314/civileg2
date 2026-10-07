package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

/**
 * Professional Waffle Slab Engineering Drawing
 * Renders plan view, section A-A (T-rib profile), solid head detail,
 * and reinforcement table for two-way waffle slab construction.
 *
 * ECP 203-2020: Chapter 6 — ribbed slabs, solid head per 6.3.5
 * ACI 318-19:  Chapter 8 — one-way joist construction, 8.13
 */
@Composable
fun ProfessionalWaffleSlabDrawing(
    lx: Double,
    ly: Double,
    ribSpacing: Double,
    ribWidth: Double,
    ribHeight: Double,
    toppingThickness: Double,
    solidHeadSize: Double,
    columnWidth: Double,
    ribBottomDia: Double,
    ribBottomCount: Int,
    ribTopDia: Double,
    ribTopCount: Int,
    headBottomDia: Double,
    headBottomCount: Int,
    cover: Double,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard: validate key dimensions before drawing ─────────────────
    InputGuard.positive("lx", lx)
    InputGuard.positive("ly", ly)
    InputGuard.positive("ribSpacing", ribSpacing)
    InputGuard.positive("ribWidth", ribWidth)
    InputGuard.positive("ribHeight", ribHeight)
    InputGuard.positive("toppingThickness", toppingThickness)
    InputGuard.positive("solidHeadSize", solidHeadSize)
    InputGuard.positive("columnWidth", columnWidth)
    InputGuard.positive("cover", cover)

    // ── Code-reference annotation ──────────────────────────────────────────
    val codeLabel = designCode.version
    // ── Responsive config ──────────────────────────────────────────────────
    val cfg = drawingDimensionsConfig()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        // ── Safety checks ─────────────────────────────────────────
        val safeLx = lx.coerceAtLeast(1.0)
        val safeLy = ly.coerceAtLeast(1.0)
        val safeRibSpacing = ribSpacing.coerceAtLeast(300.0)
        val safeRibWidth = ribWidth.coerceAtLeast(100.0)
        val safeRibHeight = ribHeight.coerceAtLeast(200.0)
        val safeTopping = toppingThickness.coerceAtLeast(50.0)
        val safeSolidHead = solidHeadSize.coerceAtLeast(500.0)
        val safeColW = columnWidth.coerceAtLeast(200.0)
        val totalDepth = safeRibHeight + safeTopping

        // Normalize to mm
        val lxMm = if (safeLx < 50.0) safeLx * 1000.0 else safeLx
        val lyMm = if (safeLy < 50.0) safeLy * 1000.0 else safeLy

        // ── Color Palette ──────────────────────────────────────────
        val C = DrawingColorDefaults
        val concreteFill = C.ConcreteFill
        val concreteStroke = C.ConcreteGray
        val toppingFill = Color(0xFF4A4A4A)
        val ribFill = Color(0xFF3D3D3D)
        val colFill = Color(0xFF555555)
        val colStroke = Color(0xFF333333)
        val barBottomColor = C.RebarBlue
        val barTopColor = C.TopRebarBlue
        val barHeadColor = C.SafeGreen
        val dimColor = C.ExtensionGray
        val textColor = C.DimensionWhite
        val headerBg = Color(0x55333333)
        val tableHeaderBg = Color(0x55333333)
        val hatchColor = Color(0x55AAAAAA)

        // ── Layout zones (viewMode-aware) ────────────────────────
        val margin = cfg.margin
        val planH = when (viewMode) { 1 -> h * 0.82f; 0 -> h * cfg.planHeightFraction; else -> h * 0.10f }
        val secH = when (viewMode) { 2 -> h * 0.50f; 0 -> h * cfg.sectionHeightFraction; else -> h * 0.10f }
        val headH = when (viewMode) { 3 -> h * 0.40f; 0 -> h * 0.12f; else -> 0f }
        val tableH = when (viewMode) { 3 -> h * 0.88f; 0 -> h * 0.20f; else -> h * 0.10f }

        val planTop = h * 0.05f
        val planBottom = planTop + planH
        val secTop = planBottom + h * 0.02f
        val secBottom = secTop + secH
        val headTop = secBottom + h * 0.01f
        val headBottom = headTop + headH
        val tblTop = if (viewMode == 0 && headH > 0f) headBottom + h * 0.02f else secBottom + h * 0.02f

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, cfg.headerHeight))
        drawTextAnnotated(
            "WAFFLE SLAB DETAIL — $codeLabel",
            w / 2f, cfg.headerHeight * 0.65f, textColor, cfg.headerTextSize, center = true, bold = true
        )

        // ── Section separators & zone labels ───────────────────────
        if (viewMode == 0) {
            drawSectionSeparator(secTop - h * 0.01f, w, "PLAN / SECTION", cfg)
            drawSectionSeparator(tblTop - h * 0.01f, w, "SECTION / TABLE", cfg)
            drawCombinedSectionLabel(h * 0.04f, w, listOf("Plan", "Section", "Table"), cfg)
        } else if (viewMode == 1) {
            drawZoneLabel(margin, planTop + 4f, "PLAN", cfg)
        } else if (viewMode == 2) {
            drawZoneLabel(margin, secTop + 4f, "SECTION", cfg)
        } else if (viewMode == 3) {
            drawZoneLabel(margin, tblTop + 4f, "TABLE", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW — Waffle grid
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            val planLeft = margin * 2f
            val planRight = w - margin
            val planW = planRight - planLeft
            val planDrawH = planBottom - planTop

            val scaleX = planW / lxMm.toFloat()
            val scaleY = planDrawH / lyMm.toFloat()
            val scale = min(scaleX, scaleY) * 0.92f
            val drawLx = lxMm.toFloat() * scale
            val drawLy = lyMm.toFloat() * scale

            val fLeft = (w - drawLx) / 2f
            val fTop = planTop + (planH - drawLy) / 2f
            val fRight = fLeft + drawLx
            val fBottom = fTop + drawLy
            val fCenterX = fLeft + drawLx / 2f
            val fCenterY = fTop + drawLy / 2f

            // Slab outline (topping)
            drawRect(color = toppingFill, topLeft = Offset(fLeft, fTop), size = Size(drawLx, drawLy))
            drawHatchPattern(fLeft, fTop, drawLx, drawLy, spacing = 18f, angleDeg = 45f, color = hatchColor)

            // Ribs in X-direction (vertical lines)
            val numRibsX = max(2, (lxMm / safeRibSpacing).toInt() + 1)
            val ribWpx = (safeRibWidth * scale).toFloat().coerceIn(3f, 30f)
            for (i in 0 until numRibsX) {
                val rx = fLeft + drawLx * (i + 1f) / (numRibsX + 1f)
                drawRect(
                    color = ribFill,
                    topLeft = Offset(rx - ribWpx / 2f, fTop),
                    size = Size(ribWpx, drawLy)
                )
            }

            // Ribs in Y-direction (horizontal lines)
            val numRibsY = max(2, (lyMm / safeRibSpacing).toInt() + 1)
            for (j in 0 until numRibsY) {
                val ry = fTop + drawLy * (j + 1f) / (numRibsY + 1f)
                drawRect(
                    color = ribFill,
                    topLeft = Offset(fLeft, ry - ribWpx / 2f),
                    size = Size(drawLx, ribWpx)
                )
            }

            // Solid heads at column locations (corners + center)
            val headSizePx = (safeSolidHead * scale).toFloat().coerceIn(20f, drawLx * 0.3f)
            val colWPx = (safeColW * scale).toFloat().coerceIn(10f, headSizePx * 0.6f)
            val headPositions = listOf(
                Offset(fLeft + drawLx / 2f, fTop + drawLy / 2f),  // center
                Offset(fLeft + drawLx / 2f, fTop),                 // top mid
                Offset(fLeft + drawLx / 2f, fBottom),              // bottom mid
                Offset(fLeft, fTop + drawLy / 2f),                 // left mid
                Offset(fRight, fTop + drawLy / 2f)                 // right mid
            )
            for (pos in headPositions) {
                // Solid head fill
                drawRect(
                    color = Color(0xFF5A5A5A),
                    topLeft = Offset(pos.x - headSizePx / 2f, pos.y - headSizePx / 2f),
                    size = Size(headSizePx, headSizePx)
                )
                drawHatchPattern(
                    pos.x - headSizePx / 2f, pos.y - headSizePx / 2f,
                    headSizePx, headSizePx,
                    spacing = 6f, angleDeg = -45f, color = Color(0x77666666)
                )
                // Column
                drawRect(
                    color = colFill,
                    topLeft = Offset(pos.x - colWPx / 2f, pos.y - colWPx / 2f),
                    size = Size(colWPx, colWPx)
                )
                drawRect(
                    color = colStroke,
                    topLeft = Offset(pos.x - colWPx / 2f, pos.y - colWPx / 2f),
                    size = Size(colWPx, colWPx),
                    style = Stroke(width = 1.5f)
                )
            }

            // Border
            drawRect(
                color = concreteStroke,
                topLeft = Offset(fLeft, fTop),
                size = Size(drawLx, drawLy),
                style = Stroke(width = 2.5f)
            )

            // Dimensions
            drawHorizontalDimension(fLeft, fRight, fTop, "Lx=${lxMm.toInt()}", dimColor, cfg.dimTextSize, offset = -14f)
            drawVerticalDimension(fTop, fBottom, fLeft, "Ly=${lyMm.toInt()}", dimColor, cfg.dimTextSize, offset = -14f)

            // Rib spacing label
            if (numRibsX > 1) {
                val r1 = fLeft + drawLx / (numRibsX + 1f)
                val r2 = fLeft + drawLx * 2f / (numRibsX + 1f)
                drawHorizontalDimension(r1, r2, fBottom, "s=${safeRibSpacing.toInt()}", C.ExtensionGray, cfg.valueLabelTextSize, offset = 10f)
            }

            drawTextAnnotated("PLAN", fLeft + 20f, fBottom + 22f, C.ExtensionGray, cfg.dimTextSize, bold = true)

            // Section cut line
            if (viewMode == 0) {
                drawSectionCutLine(
                    x1 = fCenterX, y1 = fTop - 20f,
                    x2 = fCenterX, y2 = fBottom + 6f,
                    label = "A", color = C.SectionLine
                )
            }
        } // end plan view

        // ══════════════════════════════════════════════════════════
        //  SECTION A-A — T-rib profile
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val secLeft = margin + 90f
            val secRight = w - margin
            val maxSecW = secRight - secLeft - 120f

            drawTextAnnotated("SECTION A-A", secLeft - 50f, secTop + 4f, C.ExtensionGray, cfg.dimTextSize, bold = true)

            // Scale rib profile
            val ribSpacingPx = maxSecW * 0.35f
            val ribWPx = ribSpacingPx * (safeRibWidth / safeRibSpacing).toFloat().coerceIn(0.1f, 0.6f)
            val toppingPx = ribSpacingPx * (safeTopping / safeRibSpacing).toFloat().coerceIn(8f, ribSpacingPx * 0.3f)
            val ribHPx = ribSpacingPx * (safeRibHeight / safeRibSpacing).toFloat().coerceIn(20f, ribSpacingPx * 0.7f)
            val totalHPx = ribHPx + toppingPx

            // Center the T-section
            val tCenterX = secLeft + maxSecW / 2f
            val tTop = secTop + (secH - totalHPx) / 2f + 10f
            val ribTop = tTop + toppingPx
            val tBottom = ribTop + ribHPx

            // Draw 2 T-ribs with void between
            for (offsetIdx in listOf(-1, 0, 1)) {
                val ribCenterX = tCenterX + offsetIdx * ribSpacingPx

                // Topping slab (flange)
                drawRect(
                    color = toppingFill,
                    topLeft = Offset(ribCenterX - ribSpacingPx / 2f, tTop),
                    size = Size(ribSpacingPx, toppingPx)
                )

                // Rib web
                drawRect(
                    color = ribFill,
                    topLeft = Offset(ribCenterX - ribWPx / 2f, ribTop),
                    size = Size(ribWPx, ribHPx)
                )

                // Hatching on rib
                drawHatchPattern(
                    ribCenterX - ribWPx / 2f, ribTop, ribWPx, ribHPx,
                    spacing = 6f, angleDeg = 45f, color = hatchColor
                )

                // Rib border
                drawRect(
                    color = concreteStroke,
                    topLeft = Offset(ribCenterX - ribWPx / 2f, ribTop),
                    size = Size(ribWPx, ribHPx),
                    style = Stroke(width = 1.5f)
                )

                // Bottom reinforcement circles
                if (ribBottomCount > 0) {
                    val barStep = ribWPx / (ribBottomCount + 1).toFloat()
                    for (b in 1..ribBottomCount) {
                        val bx = ribCenterX - ribWPx / 2f + b * barStep
                        drawRebarCircle(bx, tBottom - 4f, ribBottomDia.toFloat(), 0.8f, barBottomColor)
                    }
                    drawTextAnnotated(
                        "\u2460", ribCenterX, tBottom + 10f,
                        barBottomColor, cfg.dimTextSize, center = true, bold = true
                    )
                }

                // Top reinforcement (in topping)
                if (ribTopCount > 0) {
                    val barStepT = ribSpacingPx / (ribTopCount + 1).toFloat()
                    for (b in 1..ribTopCount) {
                        val bx = ribCenterX - ribSpacingPx / 2f + b * barStepT
                        drawRebarCircle(bx, tTop + toppingPx - 4f, ribTopDia.toFloat(), 0.6f, barTopColor)
                    }
                }
            }

            // Void labels between ribs
            drawTextAnnotated("VOID", tCenterX, ribTop + ribHPx / 2f, C.ExtensionGray, cfg.valueLabelTextSize, center = true)

            // Topping outline
            drawRect(
                color = concreteStroke,
                topLeft = Offset(tCenterX - ribSpacingPx * 1.5f, tTop),
                size = Size(ribSpacingPx * 3f, toppingPx),
                style = Stroke(width = 2f)
            )

            // Dimensions
            drawVerticalDimension(tTop, tBottom, tCenterX + ribSpacingPx * 1.5f, "h=${totalDepth.toInt()}", dimColor, cfg.valueLabelTextSize, offset = 16f)
            drawVerticalDimension(tTop, ribTop, tCenterX + ribSpacingPx * 1.5f, "tf=${safeTopping.toInt()}", C.SafeGreen, cfg.annotationTextSmall, offset = 34f)
            drawHorizontalDimension(
                tCenterX - ribWPx / 2f, tCenterX + ribWPx / 2f,
                tBottom, "bw=${safeRibWidth.toInt()}", dimColor, cfg.valueLabelTextSize, offset = 10f
            )

            // Top bar mark
            drawTextAnnotated("\u2461", tCenterX + ribSpacingPx * 0.5f, tTop + toppingPx + 10f, barTopColor, cfg.dimTextSize, center = true, bold = true)

            // Cover dimension
            val coverPx = 5f
            drawLine(C.SafeGreen, Offset(tCenterX - ribSpacingPx * 1.5f + 10f, tBottom), Offset(tCenterX - ribSpacingPx * 1.5f + 10f, tBottom - coverPx), strokeWidth = 1f)
            drawTextAnnotated("c=${cover.toInt()}", tCenterX - ribSpacingPx * 1.5f + 25f, tBottom - coverPx / 2f + 3f, C.SafeGreen, cfg.annotationTextSmall)
        } // end section view

        // ══════════════════════════════════════════════════════════
        //  SOLID HEAD DETAIL
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            if (viewMode == 3 && headH > h * 0.1f) {
                val hdLeft = margin + 90f
                val hdRight = w - margin
                val hdW = hdRight - hdLeft

                drawTextAnnotated("SOLID HEAD DETAIL", hdLeft, headTop + 4f, C.ExtensionGray, cfg.dimTextSize, bold = true)

                val headSizePx = min(hdW, headH) * 0.6f
                val colWPx = headSizePx * 0.3f
                val headLeft = hdLeft + (hdW - headSizePx) / 2f
                val headTopY = headTop + (headH - headSizePx) / 2f + 10f

                // Solid head
                drawRect(color = Color(0xFF5A5A5A), topLeft = Offset(headLeft, headTopY), size = Size(headSizePx, headSizePx))
                drawHatchPattern(headLeft, headTopY, headSizePx, headSizePx, spacing = 6f, angleDeg = -45f, color = Color(0x77666666))
                drawRect(color = concreteStroke, topLeft = Offset(headLeft, headTopY), size = Size(headSizePx, headSizePx), style = Stroke(width = 2f))

                // Column
                val colLeft = headLeft + (headSizePx - colWPx) / 2f
                val colTopY = headTopY + (headSizePx - colWPx) / 2f
                drawRect(color = colFill, topLeft = Offset(colLeft, colTopY), size = Size(colWPx, colWPx))
                drawRect(color = colStroke, topLeft = Offset(colLeft, colTopY), size = Size(colWPx, colWPx), style = Stroke(width = 1.5f))

                // Head reinforcement circles
                if (headBottomCount > 0) {
                    val gridN = ceil(sqrt(headBottomCount.toDouble())).toInt().coerceIn(2, 8)
                    val step = headSizePx / (gridN + 1).toFloat()
                    var placed = 0
                    for (gi in 1..gridN) {
                        for (gj in 1..gridN) {
                            if (placed >= headBottomCount) break
                            val bx = headLeft + gi * step
                            val by = headTopY + headSizePx - 6f
                            drawRebarCircle(bx, by, headBottomDia.toFloat(), 0.6f, barHeadColor)
                            placed++
                        }
                    }
                    drawTextAnnotated("\u2462", headLeft + headSizePx + 14f, headTopY + headSizePx / 2f, barHeadColor, cfg.valueLabelTextSize, bold = true)
                }

                // Dimensions
                drawHorizontalDimension(headLeft, headLeft + headSizePx, headTopY, "SH=${safeSolidHead.toInt()}", dimColor, cfg.valueLabelTextSize, offset = -14f)
                drawHorizontalDimension(colLeft, colLeft + colWPx, headTopY + headSizePx, "c=${safeColW.toInt()}", dimColor, cfg.annotationTextSmall, offset = 10f)
            }
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val tblLeft = margin
            val tblWidth = w - 2 * margin

            val ribSpacingMm = safeRibSpacing
            val ribBarLen = lxMm.toInt()
            val topBarLen = lxMm.toInt()
            val headBarLen = safeSolidHead.toInt()

            val headers = listOf("Mark", "Location", "Dia (mm)", "Count/rib", "Spacing (mm)", "Length (mm)")
            val colWidths = listOf(
                tblWidth * 0.08f, tblWidth * cfg.tableHeightFraction, tblWidth * 0.14f,
                tblWidth * 0.14f, tblWidth * 0.20f, tblWidth * cfg.tableHeightFraction
            )
            val rows = listOf(
                listOf("\u2460", "Rib bottom", ribBottomDia.toInt().toString(), ribBottomCount.toString(), ribSpacingMm.toInt().toString(), ribBarLen.toString()),
                listOf("\u2461", "Topping top", ribTopDia.toInt().toString(), ribTopCount.toString(), ribSpacingMm.toInt().toString(), topBarLen.toString()),
                listOf("\u2462", "Solid head bot", headBottomDia.toInt().toString(), headBottomCount.toString(), "—", headBarLen.toString())
            )

            drawReinforcementTable(
                x = tblLeft, y = tblTop,
                colWidths = colWidths,
                headers = headers,
                rows = rows,
                rowHeight = cfg.tableRowHeight,
                headerHeight = cfg.tableHeaderHeight,
                headerBg = tableHeaderBg,
                altRowBg = Color(0x1AFFFFFF),
                textColor = textColor,
                textSize = cfg.dimTextSize
            )
        } // end reinforcement table

        // ── Responsive Title Block ───────────────────────────────
        drawResponsiveTitleBlock(
            x = w - cfg.titleBlockWidth - margin * 0.3f,
            y = h - cfg.titleBlockHeight - margin * 0.3f,
            cfg = cfg,
            drawingTitle = "Waffle Slab Detail",
            designCode = codeLabel
        )
    }
}
