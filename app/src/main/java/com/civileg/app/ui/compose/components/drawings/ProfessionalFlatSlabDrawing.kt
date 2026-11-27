package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.PanelType
import com.civileg.app.domain.RebarResult
import kotlin.math.*

// ============================================================================
// PROFESSIONAL FLAT SLAB ENGINEERING DRAWING
// v2.0 — Comprehensive Multi-View Flat Slab Detailing
// Codes: ECP 203-2020 Ch. 6, ACI 318-19 Ch. 8 & 13, SBC 304-2018
// ============================================================================

/**
 * Professional Flat Slab Engineering Drawing
 *
 * Renders:
 *  - viewMode 0 (All):         Plan + Section A-A (Column Strip / Drop) +
 *                              Section B-B (Middle Strip) + BBS Schedule Table
 *  - viewMode 1 (Plan View):   Full panel plan with column & middle strip boundaries,
 *                              drop panels, columns, punching shear bo perimeter,
 *                              top and bottom reinforcement tags, and dimensions
 *  - viewMode 2 (Section A-A): Column strip cross-section showing slab thickness,
 *                              drop panel depth, column stub, top negative rebar
 *                              curtailment (0.3 Ln), bottom positive rebar, punching cone
 *  - viewMode 3 (Section B-B): Middle strip cross-section showing continuous bottom rebar,
 *                              curtailed top rebar (0.22 Ln), spacer chairs
 *  - viewMode 4 (BBS Table):   Full Bar Bending Schedule with bar marks, shapes,
 *                              diameters, counts, lengths, and total steel weight
 */
@Composable
fun ProfessionalFlatSlabDrawing(
    lx: Double,            // m
    ly: Double,            // m
    slabThickness: Double, // mm
    columnWidth: Double,   // mm
    columnDepth: Double,   // mm
    dropSizeX: Double = 0.0,
    dropSizeY: Double = 0.0,
    dropThickness: Double = 0.0, // mm (additional thickness below slab, e.g. 50-100mm)
    colStripWidthX: Double = 0.0,
    colStripWidthY: Double = 0.0,
    colTopRebar: RebarResult,
    colBotRebar: RebarResult,
    midTopRebar: RebarResult,
    midBotRebar: RebarResult,
    isSafe: Boolean = true,
    panelType: PanelType = PanelType.INTERIOR,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard ─────────────────────────────────────────────────────────
    InputGuard.positive("lx", lx)
    InputGuard.positive("ly", ly)
    InputGuard.positive("slabThickness", slabThickness)
    InputGuard.positive("columnWidth", columnWidth)
    InputGuard.positive("columnDepth", columnDepth)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxSize()) {
        // Too small to draw truthfully: omit rather than distort geometry.
        if (isDegenerateForDrawing()) return@Canvas
        val W = size.width
        val H = size.height

        // ── Color Palette ──────────────────────────────────────────────────
        val C = DrawingColorDefaults
        val bgDark          = Color(0xFF1A1A2E)
        val panelFill       = Color(0xFF242438)
        val panelStroke     = Color(0xFF546E7A)
        val columnFill      = Color(0xFF78909C)
        val columnStroke    = Color(0xFFB0BEC5)
        val dropFill        = Color(0xFF37474F)
        val dropStroke      = Color(0xFF78909C)
        val colStripColor   = Color(0xFF2E7D32)
        val midStripColor   = Color(0xFF1565C0)
        val topBarColor     = C.SecondaryRed
        val botBarColor     = C.RebarBlue
        val dimColor        = C.DimensionWhite
        val textColor       = C.DimensionWhite
        val safeColor       = C.SafeGreen
        val unsafeColor     = C.UnsafeRed
        val shearPerimColor = C.WarningOrange
        val gridColor       = C.GridLine

        val safeDropThick = if (dropThickness > 10.0) dropThickness else if (dropSizeX > 0.0) slabThickness * 0.25 else 0.0

        val margin = cfg.margin
        val headerH = cfg.headerHeight + 10f

        // ── Header band ────────────────────────────────────────────────────
        drawFlatSlabHeader(W, cfg, codeLabel, panelType, isSafe, textColor, safeColor, unsafeColor)

        // ── Routing by viewMode ────────────────────────────────────────────
        when (viewMode) {
            1 -> {
                // Plan View Only
                drawZoneLabel(margin, headerH + 6f, "PANEL PLAN & STRIPS", cfg)
                drawFlatSlabPlanView(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    lx, ly, slabThickness, columnWidth, columnDepth,
                    dropSizeX, dropSizeY, colStripWidthX, colStripWidthY,
                    colTopRebar, colBotRebar, midTopRebar, midBotRebar,
                    panelFill, panelStroke, columnFill, columnStroke,
                    dropFill, dropStroke, colStripColor, midStripColor,
                    topBarColor, botBarColor, shearPerimColor, dimColor, textColor, gridColor,
                    cfg
                )
            }
            2 -> {
                // Section A-A: Column Strip & Drop Panel
                drawZoneLabel(margin, headerH + 6f, "SECTION A-A: COLUMN STRIP & DROP PANEL", cfg)
                drawFlatSlabSectionAA(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    lx, slabThickness, columnWidth, dropSizeX, safeDropThick,
                    colTopRebar, colBotRebar,
                    panelFill, panelStroke, columnFill, columnStroke,
                    dropFill, dropStroke, topBarColor, botBarColor, shearPerimColor,
                    dimColor, textColor, cfg, designCode
                )
            }
            3 -> {
                // Section B-B: Middle Strip
                drawZoneLabel(margin, headerH + 6f, "SECTION B-B: MIDDLE STRIP", cfg)
                drawFlatSlabSectionBB(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    lx, slabThickness, columnWidth,
                    midTopRebar, midBotRebar,
                    panelFill, panelStroke, columnFill, columnStroke,
                    topBarColor, botBarColor, dimColor, textColor, cfg, designCode
                )
            }
            4 -> {
                // BBS Schedule Table View
                drawZoneLabel(margin, headerH + 6f, "BAR BENDING SCHEDULE (BBS)", cfg)
                drawFlatSlabBBSTable(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    lx, ly, slabThickness, columnWidth, columnDepth,
                    colTopRebar, colBotRebar, midTopRebar, midBotRebar,
                    topBarColor, botBarColor, dimColor, textColor, cfg
                )
            }
            else -> {
                // viewMode 0 (All): Multi-view layout
                val topH = (H - headerH - margin) * 0.46f
                val botH = (H - headerH - margin) * 0.44f
                val topY = headerH + 10f
                val botY = topY + topH + 14f
                val halfW = (W - 3 * margin) / 2f

                drawSectionSeparator(botY - 7f, W, "SECTIONS & BBS REINFORCEMENT SCHEDULE", cfg)
                drawCombinedSectionLabel(H * 0.015f, W, listOf("Plan", "Sec A-A", "Sec B-B", "BBS"), cfg)

                // Top-Left: Plan View
                drawZoneLabel(margin, topY + 2f, "PLAN", cfg)
                drawFlatSlabPlanView(
                    margin, topY + 12f, halfW, topH - 16f,
                    lx, ly, slabThickness, columnWidth, columnDepth,
                    dropSizeX, dropSizeY, colStripWidthX, colStripWidthY,
                    colTopRebar, colBotRebar, midTopRebar, midBotRebar,
                    panelFill, panelStroke, columnFill, columnStroke,
                    dropFill, dropStroke, colStripColor, midStripColor,
                    topBarColor, botBarColor, shearPerimColor, dimColor, textColor, gridColor,
                    cfg
                )

                // Top-Right: Section A-A (Column Strip)
                val secLeft = margin + halfW + margin
                drawZoneLabel(secLeft, topY + 2f, "SECTION A-A (COL STRIP)", cfg)
                drawFlatSlabSectionAA(
                    secLeft, topY + 12f, halfW, topH - 16f,
                    lx, slabThickness, columnWidth, dropSizeX, safeDropThick,
                    colTopRebar, colBotRebar,
                    panelFill, panelStroke, columnFill, columnStroke,
                    dropFill, dropStroke, topBarColor, botBarColor, shearPerimColor,
                    dimColor, textColor, cfg, designCode
                )

                // Bottom-Left: Section B-B (Middle Strip)
                drawZoneLabel(margin, botY + 2f, "SECTION B-B (MID STRIP)", cfg)
                drawFlatSlabSectionBB(
                    margin, botY + 12f, halfW, botH - 16f,
                    lx, slabThickness, columnWidth,
                    midTopRebar, midBotRebar,
                    panelFill, panelStroke, columnFill, columnStroke,
                    topBarColor, botBarColor, dimColor, textColor, cfg, designCode
                )

                // Bottom-Right: BBS Schedule Table
                val tblLeft = margin + halfW + margin
                drawZoneLabel(tblLeft, botY + 2f, "BBS SCHEDULE", cfg)
                drawFlatSlabBBSTable(
                    tblLeft, botY + 12f, halfW, botH - 16f,
                    lx, ly, slabThickness, columnWidth, columnDepth,
                    colTopRebar, colBotRebar, midTopRebar, midBotRebar,
                    topBarColor, botBarColor, dimColor, textColor, cfg
                )
            }
        }

        // ── Responsive Title Block & Annotations ───────────────────────────
        val blockTopLeft = drawMeasuredSheetBlock(
            summary = resultSummary,
            drawingTitle = "Flat Slab Detailing",
            designCode = codeLabel,
            right = W - margin * 0.3f,
            bottom = H - margin * 0.3f,
            cfg = cfg
        )
        drawNorthArrow(
            x = blockTopLeft.x - cfg.annotationTextSmall * 2.6f,
            y = blockTopLeft.y + cfg.annotationTextSmall * 2.2f,
            size = cfg.annotationTextSmall * 1.4f
        )
        drawScaleBar(
            x = margin, y = H - cfg.margin * 0.8f,
            scale = 0.05f, realLengthMm = 1000f,
            cfg = cfg
        )
    }
}

// ============================================================================
// 1. HEADER BAND
// ============================================================================

private fun DrawScope.drawFlatSlabHeader(
    W: Float, cfg: DrawingDimensionsConfig,
    codeLabel: String, panelType: PanelType, isSafe: Boolean,
    textColor: Color, safeColor: Color, unsafeColor: Color
) {
    val hBg = Color(0xFF263238)
    drawRect(color = hBg, topLeft = Offset(0f, 0f), size = Size(W, cfg.headerHeight))
    drawTextAnnotated(
        "FLAT SLAB STRUCTURAL DETAIL — ${panelType.displayName.uppercase()} PANEL  |  $codeLabel",
        W / 2f, cfg.headerHeight * 0.65f,
        textColor, cfg.headerTextSize, center = true, bold = true
    )
    // Subtitle
    val statusTxt = if (isSafe) "STATUS: SAFE ✓" else "STATUS: UNSAFE ✗"
    val statusClr = if (isSafe) safeColor else unsafeColor
    drawTextAnnotated(
        "Direct Design Method per ECP 203 §6 / ACI 318 §13  •  $statusTxt",
        W / 2f, cfg.headerHeight * 0.90f,
        statusClr, cfg.annotationTextSmall * 0.85f, center = true, bold = true
    )
}

// ============================================================================
// 2. PLAN VIEW: COLUMNS, STRIPS, DROP PANELS, REBAR CALLOUTS
// ============================================================================

private fun DrawScope.drawFlatSlabPlanView(
    left: Float, top: Float, width: Float, height: Float,
    lx: Double, ly: Double, slabThickness: Double, columnWidth: Double, columnDepth: Double,
    dropSizeX: Double, dropSizeY: Double, colStripWidthX: Double, colStripWidthY: Double,
    colTopRebar: RebarResult, colBotRebar: RebarResult,
    midTopRebar: RebarResult, midBotRebar: RebarResult,
    panelFill: Color, panelStroke: Color, columnFill: Color, columnStroke: Color,
    dropFill: Color, dropStroke: Color, colStripColor: Color, midStripColor: Color,
    topBarColor: Color, botBarColor: Color, shearPerimColor: Color,
    dimColor: Color, textColor: Color, gridColor: Color,
    cfg: DrawingDimensionsConfig
) {
    val pad = 35f
    val avW = width - 2 * pad
    val avH = height - 2 * pad

    val panelAspect = lx / ly
    val areaAspect = avW / avH
    val panelW: Float
    val panelH: Float
    if (panelAspect > areaAspect) {
        panelW = avW
        panelH = (avW / panelAspect).toFloat()
    } else {
        panelH = avH
        panelW = (avH * panelAspect).toFloat()
    }
    val panelX = left + pad + (avW - panelW) / 2f
    val panelY = top + pad + (avH - panelH) / 2f

    val scaleX = panelW / lx.toFloat()
    val scaleY = panelH / ly.toFloat()

    val colWPx = (columnWidth / 1000.0 * scaleX).toFloat().coerceInMin(12f, panelW * 0.25f)
    val colDPx = (columnDepth / 1000.0 * scaleY).toFloat().coerceInMin(12f, panelH * 0.25f)
    val dropXPx = (dropSizeX / 1000.0 * scaleX).toFloat()
    val dropYPx = (dropSizeY / 1000.0 * scaleY).toFloat()

    val cStripXPx = if (colStripWidthX > 0) (colStripWidthX / 1000.0 * scaleX).toFloat() else panelW * 0.5f
    val cStripYPx = if (colStripWidthY > 0) (colStripWidthY / 1000.0 * scaleY).toFloat() else panelH * 0.5f

    // Panel background
    drawRect(color = panelFill, topLeft = Offset(panelX, panelY), size = Size(panelW, panelH))
    drawRect(color = panelStroke, topLeft = Offset(panelX, panelY), size = Size(panelW, panelH),
        style = Stroke(2f))

    // ── Strip boundaries (dashed) ─────────────────────────────────────────
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)

    // Column strip X boundaries
    val csLeftX = panelX + (panelW - cStripXPx) / 2f
    val csRightX = csLeftX + cStripXPx
    drawLine(colStripColor, Offset(csLeftX, panelY), Offset(csLeftX, panelY + panelH), 1.5f, pathEffect = dash)
    drawLine(colStripColor, Offset(csRightX, panelY), Offset(csRightX, panelY + panelH), 1.5f, pathEffect = dash)

    // Column strip Y boundaries
    val csTopY = panelY + (panelH - cStripYPx) / 2f
    val csBotY = csTopY + cStripYPx
    drawLine(colStripColor, Offset(panelX, csTopY), Offset(panelX + panelW, csTopY), 1.5f, pathEffect = dash)
    drawLine(colStripColor, Offset(panelX, csBotY), Offset(panelX + panelW, csBotY), 1.5f, pathEffect = dash)

    // Strip labels
    drawTextAnnotated("Column Strip", panelX + panelW / 2f, panelY + 14f,
        colStripColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)
    drawTextAnnotated("Mid Strip", csLeftX / 2f + panelX / 2f, panelY + panelH / 2f,
        midStripColor, cfg.annotationTextSmall * 0.85f, center = true)

    // ── Drop panel (if present) ───────────────────────────────────────────
    if (dropXPx > 0 && dropYPx > 0) {
        val dropX = panelX + (panelW - dropXPx) / 2f
        val dropY = panelY + (panelH - dropYPx) / 2f
        drawRect(color = dropFill, topLeft = Offset(dropX, dropY), size = Size(dropXPx, dropYPx))
        drawRect(color = dropStroke, topLeft = Offset(dropX, dropY), size = Size(dropXPx, dropYPx),
            style = Stroke(1.5f))
        drawTextAnnotated("Drop Panel", dropX + dropXPx / 2f, dropY + 12f,
            textColor, cfg.annotationTextSmall * 0.75f, center = true)
    }

    // ── Punching shear critical perimeter bo (d/2 from column face) ────────
    val dOffset = ((slabThickness * 0.5) / 1000.0 * scaleX).toFloat().coerceIn(6f, 25f)
    val punchW = colWPx + 2 * dOffset
    val punchH = colDPx + 2 * dOffset
    val punchX = panelX + (panelW - punchW) / 2f
    val punchY = panelY + (panelH - punchH) / 2f

    drawRect(color = shearPerimColor, topLeft = Offset(punchX, punchY), size = Size(punchW, punchH),
        style = Stroke(1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f), 0f)))
    drawTextAnnotated("bo (d/2)", punchX + punchW + 4f, punchY + punchH / 2f,
        shearPerimColor, cfg.annotationTextSmall * 0.8f, bold = true)

    // ── Columns at 4 corners of the panel ─────────────────────────────────
    val colCoords = listOf(
        Offset(panelX, panelY),
        Offset(panelX + panelW - colWPx, panelY),
        Offset(panelX, panelY + panelH - colDPx),
        Offset(panelX + panelW - colWPx, panelY + panelH - colDPx),
        // Central column
        Offset(panelX + (panelW - colWPx) / 2f, panelY + (panelH - colDPx) / 2f)
    )
    colCoords.forEach { pos ->
        drawRect(color = columnFill, topLeft = pos, size = Size(colWPx, colDPx))
        drawRect(color = columnStroke, topLeft = pos, size = Size(colWPx, colDPx), style = Stroke(1.5f))
        // Hatch lines inside column
        drawLine(columnStroke, Offset(pos.x, pos.y), Offset(pos.x + colWPx, pos.y + colDPx), 1f)
        drawLine(columnStroke, Offset(pos.x + colWPx, pos.y), Offset(pos.x, pos.y + colDPx), 1f)
    }

    // ── Reinforcement callouts in strips ───────────────────────────────────
    val cy = panelY + panelH / 2f
    drawTextAnnotated("Top: ${colTopRebar.barString}", panelX + panelW / 2f, cy - 16f,
        topBarColor, cfg.annotationTextSmall, center = true, bold = true)
    drawTextAnnotated("Bot: ${colBotRebar.barString}", panelX + panelW / 2f, cy + 4f,
        botBarColor, cfg.annotationTextSmall, center = true, bold = true)

    if (midBotRebar.spacing > 0) {
        val mx = (panelX + csLeftX) / 2f
        drawTextAnnotated("B: ${midBotRebar.barString}", mx, cy,
            botBarColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.85f, center = true)
    }

    // ── Dimensions ────────────────────────────────────────────────────────
    drawHorizontalDimension(panelX, panelX + panelW, panelY + panelH + 4f,
        "Lx = ${String.format("%.2f", lx)} m", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawVerticalDimension(panelY, panelY + panelH, panelX + panelW + 6f,
        "Ly = ${String.format("%.2f", ly)} m", dimColor, cfg.dimTextSize * 0.85f, 18f, cfg)
}

// ============================================================================
// 3. SECTION A-A: COLUMN STRIP & DROP PANEL SECTION
// ============================================================================

private fun DrawScope.drawFlatSlabSectionAA(
    left: Float, top: Float, width: Float, height: Float,
    lx: Double, slabThickness: Double, columnWidth: Double, dropSizeX: Double, dropThickness: Double,
    colTopRebar: RebarResult, colBotRebar: RebarResult,
    panelFill: Color, panelStroke: Color, columnFill: Color, columnStroke: Color,
    dropFill: Color, dropStroke: Color, topBarColor: Color, botBarColor: Color, shearPerimColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig, designCode: DesignCode
) {
    val pad = 30f
    val avW = width - 2 * pad
    val secScale = (avW / lx.toFloat()).coerceAtLeast(30f)

    val spanPx = (lx * secScale).toFloat().coerceAtMost(avW)
    val totalThickMm = slabThickness + dropThickness
    val thickScale = ((height * 0.35f) / totalThickMm.toFloat()).coerceAtLeast(0.1f)

    val slabThickPx = (slabThickness * thickScale).toFloat().coerceIn(18f, 50f)
    val dropThickPx = (dropThickness * thickScale).toFloat().coerceIn(8f, 30f)

    val secL = left + pad + (avW - spanPx) / 2f
    val secY = top + height * 0.35f
    val secR = secL + spanPx

    val colWPx = (columnWidth / 1000.0 * secScale).toFloat().coerceIn(25f, 60f)
    val dropWPx = if (dropSizeX > 0) (dropSizeX / 1000.0 * secScale).toFloat().coerceInMin(colWPx * 1.5f, spanPx * 0.45f) else 0f

    // ── Continuous Slab Body ──────────────────────────────────────────────
    drawRect(color = panelFill, topLeft = Offset(secL, secY), size = Size(spanPx, slabThickPx))
    drawHatchPattern(secL, secY, spanPx, slabThickPx, cfg.hatchSpacing, 45f, panelStroke.copy(alpha = 0.4f))

    // ── Drop Panel at Left & Right Column Supports ────────────────────────
    if (dropWPx > 0 && dropThickPx > 0) {
        // Left drop panel (centered at secL + colWPx/2)
        val lDropX = secL - dropWPx * 0.2f
        drawRect(color = dropFill, topLeft = Offset(lDropX, secY + slabThickPx), size = Size(dropWPx, dropThickPx))
        drawRect(color = dropStroke, topLeft = Offset(lDropX, secY + slabThickPx), size = Size(dropWPx, dropThickPx),
            style = Stroke(1.5f))

        // Right drop panel
        val rDropX = secR - dropWPx * 0.8f
        drawRect(color = dropFill, topLeft = Offset(rDropX, secY + slabThickPx), size = Size(dropWPx, dropThickPx))
        drawRect(color = dropStroke, topLeft = Offset(rDropX, secY + slabThickPx), size = Size(dropWPx, dropThickPx),
            style = Stroke(1.5f))
    }

    // ── Columns below slab ────────────────────────────────────────────────
    val colBelowH = height * 0.25f
    val colBelowTop = secY + slabThickPx + (if (dropWPx > 0) dropThickPx else 0f)

    // Left column
    drawRect(color = columnFill, topLeft = Offset(secL, colBelowTop), size = Size(colWPx, colBelowH))
    drawRect(color = columnStroke, topLeft = Offset(secL, colBelowTop), size = Size(colWPx, colBelowH),
        style = Stroke(1.5f))

    // Right column
    drawRect(color = columnFill, topLeft = Offset(secR - colWPx, colBelowTop), size = Size(colWPx, colBelowH))
    drawRect(color = columnStroke, topLeft = Offset(secR - colWPx, colBelowTop), size = Size(colWPx, colBelowH),
        style = Stroke(1.5f))

    // ── Column stubs above slab ───────────────────────────────────────────
    val colAboveH = height * 0.15f
    drawRect(color = columnFill, topLeft = Offset(secL, secY - colAboveH), size = Size(colWPx, colAboveH))
    drawRect(color = columnStroke, topLeft = Offset(secL, secY - colAboveH), size = Size(colWPx, colAboveH),
        style = Stroke(1.5f))
    drawRect(color = columnFill, topLeft = Offset(secR - colWPx, secY - colAboveH), size = Size(colWPx, colAboveH))
    drawRect(color = columnStroke, topLeft = Offset(secR - colWPx, secY - colAboveH), size = Size(colWPx, colAboveH),
        style = Stroke(1.5f))

    // Slab outline
    drawRect(color = panelStroke, topLeft = Offset(secL, secY), size = Size(spanPx, slabThickPx),
        style = Stroke(2f))

    // ── Punching Shear 45° Cone (Dashed) ──────────────────────────────────
    val coneL = Path().apply {
        moveTo(secL + colWPx, colBelowTop)
        lineTo(secL + colWPx + slabThickPx, secY)
    }
    drawPath(coneL, color = shearPerimColor, style = Stroke(1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f)))
    drawTextAnnotated("45° Shear Cone", secL + colWPx + slabThickPx + 4f, secY - 6f,
        shearPerimColor, cfg.annotationTextSmall * 0.75f, bold = true)

    // ── Top Negative Reinforcement (over columns, curtailed at 0.3 Ln) ────
    val lnPx = spanPx - colWPx
    val curtailPx = lnPx * 0.30f // ECP/ACI detailing rule: top steel extends 0.3 Ln
    val topRebarY = secY + 6f

    // Left negative steel
    drawLine(color = topBarColor, start = Offset(secL - 10f, topRebarY), end = Offset(secL + colWPx + curtailPx, topRebarY),
        strokeWidth = 3f, cap = StrokeCap.Round)
    // 90° hook at left end
    drawLine(color = topBarColor, start = Offset(secL - 10f, topRebarY), end = Offset(secL - 10f, topRebarY + 10f),
        strokeWidth = 3f, cap = StrokeCap.Round)

    // Right negative steel
    drawLine(color = topBarColor, start = Offset(secR - colWPx - curtailPx, topRebarY), end = Offset(secR + 10f, topRebarY),
        strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = topBarColor, start = Offset(secR + 10f, topRebarY), end = Offset(secR + 10f, topRebarY + 10f),
        strokeWidth = 3f, cap = StrokeCap.Round)

    drawTextAnnotated("Top Steel: ${colTopRebar.barString} (0.3 Ln)", secL + colWPx + curtailPx / 2f, topRebarY - 8f,
        topBarColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // ── Bottom Positive Reinforcement (continuous through midspan) ────────
    val botRebarY = secY + slabThickPx - 6f
    drawLine(color = botBarColor, start = Offset(secL + colWPx * 0.5f, botRebarY), end = Offset(secR - colWPx * 0.5f, botRebarY),
        strokeWidth = 3f, cap = StrokeCap.Round)
    drawTextAnnotated("Bot Steel: ${colBotRebar.barString} (Continuous)", secL + spanPx / 2f, botRebarY + 12f,
        botBarColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // ── Dimensions ────────────────────────────────────────────────────────
    drawHorizontalDimension(secL, secR, colBelowTop + colBelowH + 4f,
        "L = ${String.format("%.2f", lx)} m", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawVerticalDimension(secY, secY + slabThickPx, secR + 8f,
        "ts=${slabThickness.toInt()}mm", dimColor, cfg.dimTextSize * 0.85f, 16f, cfg)
    if (dropThickness > 10.0) {
        drawVerticalDimension(secY + slabThickPx, secY + slabThickPx + dropThickPx, secR + 8f,
            "td=${dropThickness.toInt()}mm", dimColor, cfg.dimTextSize * 0.75f, 32f, cfg)
    }
}

// ============================================================================
// 4. SECTION B-B: MIDDLE STRIP SECTION
// ============================================================================

private fun DrawScope.drawFlatSlabSectionBB(
    left: Float, top: Float, width: Float, height: Float,
    lx: Double, slabThickness: Double, columnWidth: Double,
    midTopRebar: RebarResult, midBotRebar: RebarResult,
    panelFill: Color, panelStroke: Color, columnFill: Color, columnStroke: Color,
    topBarColor: Color, botBarColor: Color, dimColor: Color, textColor: Color,
    cfg: DrawingDimensionsConfig, designCode: DesignCode
) {
    val pad = 30f
    val avW = width - 2 * pad
    val spanPx = avW.coerceAtMost(width - 60f)
    val thickScale = ((height * 0.35f) / slabThickness.toFloat()).coerceAtLeast(0.1f)
    val slabThickPx = (slabThickness * thickScale).toFloat().coerceIn(18f, 50f)

    val secL = left + pad
    val secY = top + height * 0.40f
    val secR = secL + spanPx

    // Slab body
    drawRect(color = panelFill, topLeft = Offset(secL, secY), size = Size(spanPx, slabThickPx))
    drawHatchPattern(secL, secY, spanPx, slabThickPx, cfg.hatchSpacing, 45f, panelStroke.copy(alpha = 0.4f))
    drawRect(color = panelStroke, topLeft = Offset(secL, secY), size = Size(spanPx, slabThickPx),
        style = Stroke(2f))

    // Middle strip top rebar (curtailed at 0.22 Ln per ECP/ACI)
    val curtailPx = spanPx * 0.22f
    val topY = secY + 6f
    drawLine(color = topBarColor, start = Offset(secL, topY), end = Offset(secL + curtailPx, topY),
        strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawLine(color = topBarColor, start = Offset(secR - curtailPx, topY), end = Offset(secR, topY),
        strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawTextAnnotated("Top: ${midTopRebar.barString} (0.22 Ln)", secL + curtailPx / 2f, topY - 7f,
        topBarColor, cfg.annotationTextSmall * 0.85f, center = true)

    // Middle strip bottom rebar (100% continuous)
    val botY = secY + slabThickPx - 6f
    drawLine(color = botBarColor, start = Offset(secL, botY), end = Offset(secR, botY),
        strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawTextAnnotated("Bottom: ${midBotRebar.barString} (Continuous Mesh)", secL + spanPx / 2f, botY + 12f,
        botBarColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // Dimensions
    drawHorizontalDimension(secL, secR, secY + slabThickPx + 20f,
        "L = ${String.format("%.2f", lx)} m", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawVerticalDimension(secY, secY + slabThickPx, secR + 8f,
        "ts=${slabThickness.toInt()}mm", dimColor, cfg.dimTextSize * 0.85f, 16f, cfg)
}

// ============================================================================
// 5. BAR BENDING SCHEDULE (BBS) & DETAILING TABLE
// ============================================================================

private fun DrawScope.drawFlatSlabBBSTable(
    left: Float, top: Float, width: Float, height: Float,
    lx: Double, ly: Double, slabThickness: Double, columnWidth: Double, columnDepth: Double,
    colTopRebar: RebarResult, colBotRebar: RebarResult,
    midTopRebar: RebarResult, midBotRebar: RebarResult,
    topBarColor: Color, botBarColor: Color, dimColor: Color, textColor: Color,
    cfg: DrawingDimensionsConfig
) {
    val headers = listOf("Mark", "Location", "Bar Size", "Cut Length", "Shape", "Weight (kg)")

    // Estimate bar counts and cut lengths
    val colTopLen = (lx * 0.60 + 0.3).coerceAtLeast(1.0)
    val colBotLen = (lx + 0.2).coerceAtLeast(1.0)
    val midTopLen = (lx * 0.45).coerceAtLeast(1.0)
    val midBotLen = (lx).coerceAtLeast(1.0)

    val cTopDia = colTopRebar.diameter.coerceAtLeast(10).toDouble()
    val cBotDia = colBotRebar.diameter.coerceAtLeast(10).toDouble()
    val mTopDia = midTopRebar.diameter.coerceAtLeast(10).toDouble()
    val mBotDia = midBotRebar.diameter.coerceAtLeast(10).toDouble()

    val colTopCount = if (colTopRebar.spacing > 0) ((ly * 500.0) / colTopRebar.spacing).toInt().coerceAtLeast(6) else 10
    val colBotCount = if (colBotRebar.spacing > 0) ((ly * 500.0) / colBotRebar.spacing).toInt().coerceAtLeast(6) else 10
    val midTopCount = if (midTopRebar.spacing > 0) ((ly * 500.0) / midTopRebar.spacing).toInt().coerceAtLeast(6) else 10
    val midBotCount = if (midBotRebar.spacing > 0) ((ly * 500.0) / midBotRebar.spacing).toInt().coerceAtLeast(6) else 10

    val w1 = colTopCount * colTopLen * cTopDia * cTopDia * 0.00617
    val w2 = colBotCount * colBotLen * cBotDia * cBotDia * 0.00617
    val w3 = midTopCount * midTopLen * mTopDia * mTopDia * 0.00617
    val w4 = midBotCount * midBotLen * mBotDia * mBotDia * 0.00617
    val totalW = w1 + w2 + w3 + w4

    val rows = listOf(
        listOf("CS-T", "Col Strip Top", colTopRebar.barString, "${(colTopLen * 1000).toInt()} mm", "Cranked Hook", "%.1f".format(w1)),
        listOf("CS-B", "Col Strip Bot", colBotRebar.barString, "${(colBotLen * 1000).toInt()} mm", "Straight", "%.1f".format(w2)),
        listOf("MS-T", "Mid Strip Top", midTopRebar.barString, "${(midTopLen * 1000).toInt()} mm", "Curtailed", "%.1f".format(w3)),
        listOf("MS-B", "Mid Strip Bot", midBotRebar.barString, "${(midBotLen * 1000).toInt()} mm", "Straight", "%.1f".format(w4))
    )

    val colFracs = listOf(0.12f, 0.22f, 0.22f, 0.16f, 0.14f, 0.14f)
    val colWidths = colFracs.map { it * width }

    drawResponsiveReinforcementTable(
        x = left, y = top,
        colWidths = colWidths,
        headers = headers,
        rows = rows,
        cfg = cfg,
        headerBg = DrawingColorDefaults.TableHeaderBg,
        altRowBg = DrawingColorDefaults.TableRowAlt,
        textColor = textColor
    )

    // Summary line below table
    val sumY = top + cfg.tableHeaderHeight + cfg.tableRowHeight * rows.size + 14f
    drawTextAnnotated("Total Steel: ${String.format("%.1f", totalW)} kg  (≈ ${String.format("%.1f", totalW / (lx * ly))} kg/m²)",
        left + width / 2f, sumY,
        DrawingColorDefaults.SafeGreen, cfg.annotationTextSmall, center = true, bold = true)
}
