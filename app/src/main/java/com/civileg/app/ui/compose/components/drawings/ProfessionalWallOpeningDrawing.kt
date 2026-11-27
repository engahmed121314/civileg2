package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

// ============================================================================
// PROFESSIONAL WALL OPENING ENGINEERING DRAWING
// v2.0 — Full redesign with 3-view layout, BBS table, stress distribution
// ECP 203-2020: Chapter 10 — Wall openings and lintel design
// ACI 318-19:   Chapter 14 — Walls, Sections 14.3 & 14.5 — opening reinf.
// SBC 304-2018: Adopts ACI provisions with SBC load combinations
// ============================================================================

/**
 * Professional Wall Opening Engineering Drawing
 *
 * Renders:
 *  - viewMode 0 (All):       Elevation + Section A-A + Section B-B + BBS Table
 *  - viewMode 1 (Elevation): Full wall elevation with opening, all reinforcement
 *  - viewMode 2 (Section):   Cross-section through wall at opening midpoint
 *  - viewMode 3 (Table):     Detailed BBS / reinforcement schedule with weights
 *
 * Features:
 *  - 3D perspective wall body with opening void
 *  - Header (lintel) beam with accurate bar layout and hooks
 *  - Sill beam reinforcement
 *  - Vertical jamb bars each side of opening with development lengths
 *  - Diagonal corner bars (45°) at opening corners per ECP/ACI
 *  - Stirrups in header with zone labeling
 *  - Stress distribution diagram (arching action)
 *  - Dimension lines (wall height, length, opening W×H, clearances)
 *  - Full BBS table with mark, dia, count, cut-length, shape, weight
 *  - Title block + scale bar
 */
@Composable
fun ProfessionalWallOpeningDrawing(
    wallHeightMm: Double,
    wallThicknessMm: Double,
    openingWidthMm: Double,
    openingHeightMm: Double,
    openingFromBaseMm: Double,
    wallLengthMm: Double,
    headerBarDia: Double,
    headerBarCount: Int,
    sillBarDia: Double,
    sillBarCount: Int,
    verticalBarDia: Double,
    verticalBarCount: Int,
    cover: Double,
    // Additional parameters for expanded detail
    headerStirrupDia: Double = 8.0,
    headerStirrupSpacing: Double = 150.0,
    diagonalBarDia: Double = 12.0,
    diagonalBarCount: Int = 2,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard ─────────────────────────────────────────────────────────
    InputGuard.positive("wallHeightMm", wallHeightMm)
    InputGuard.positive("wallThicknessMm", wallThicknessMm)
    InputGuard.positive("openingWidthMm", openingWidthMm)
    InputGuard.positive("openingHeightMm", openingHeightMm)
    InputGuard.positive("wallLengthMm", wallLengthMm)
    InputGuard.positive("cover", cover)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxWidth().fillMaxSize()) {
        val W = size.width
        val H = size.height

        // ── Color Palette (from DrawingColorDefaults) ─────────────────────
        val C = DrawingColorDefaults
        val wallFill         = Color(0xFF4A4A4A)
        val wallStroke       = C.ConcreteTopGray
        val wallLight        = Color(0xFF5E5E5E)
        val openingFill      = Color(0xFF1A1A2E) // dark "void"
        val openingStroke    = Color(0xFF37474F)
        val headerBarColor   = C.RebarBlue
        val sillBarColor     = C.TopRebarBlue
        val vertBarColor     = C.SecondaryRed
        val diagBarColor     = C.WarningOrange
        val stirrupColor     = C.StirrupPurple
        val dimColor         = C.DimensionWhite
        val hatchColor       = C.HatchColor
        val stressColor      = C.StressPink
        val safeColor        = C.SafeGreen
        val textColor        = C.DimensionWhite
        val sectionLineColor = C.SectionLine

        // ── Layout zones (viewMode-aware) ─────────────────────────────────
        val margin    = cfg.margin
        val headerH   = cfg.headerHeight + 10f
        val elevFrac  = when (viewMode) { 1 -> 0.90f; 0 -> 0.48f; else -> 0.0f }
        val secFrac   = when (viewMode) { 2 -> 0.60f; 0 -> 0.22f; else -> 0.0f }
        val tblFrac   = when (viewMode) { 3 -> 0.90f; 0 -> 0.22f; else -> 0.0f }

        val elevTop    = headerH + 10f
        val elevBottom = elevTop + H * elevFrac
        val secTop     = if (viewMode == 0) elevBottom + H * 0.04f else headerH + 10f
        val secBottom  = secTop + H * secFrac
        val tblTop     = if (viewMode == 0) secBottom + H * 0.02f else headerH + 10f
        val tblBottom  = tblTop + H * tblFrac

        // ── Global drawing header ─────────────────────────────────────────
        drawWallOpeningHeader(W, cfg, codeLabel, textColor)

        // ── Section separators ────────────────────────────────────────────
        if (viewMode == 0) {
            drawSectionSeparator(elevBottom + H * 0.01f, W, "SECTIONS A-A / B-B", cfg)
            drawSectionSeparator(tblTop - H * 0.01f, W, "REINFORCEMENT SCHEDULE (BBS)", cfg)
            drawCombinedSectionLabel(H * 0.015f, W, listOf("Elevation", "Sections", "BBS"), cfg)
        } else if (viewMode == 1) {
            drawZoneLabel(margin, elevTop + 4f, "ELEVATION", cfg)
        } else if (viewMode == 2) {
            drawZoneLabel(margin, secTop + 4f, "SECTIONS", cfg)
        } else if (viewMode == 3) {
            drawZoneLabel(margin, tblTop + 4f, "BBS TABLE", cfg)
        }

        // ── Compute drawing scale ─────────────────────────────────────────
        val safeWH = wallHeightMm.coerceAtLeast(500.0)
        val safeWT = wallThicknessMm.coerceAtLeast(100.0)
        val safeOW = openingWidthMm.coerceAtLeast(200.0)
        val safeOH = openingHeightMm.coerceAtLeast(200.0)
        val safeWL = wallLengthMm.coerceAtLeast(1000.0)
        val safeBase = openingFromBaseMm.coerceAtLeast(0.0).coerceAtMost(safeWH - safeOH - 100.0)

        val availW = W - 4 * margin
        val availH = elevBottom - elevTop - 2 * margin
        val scaleX = availW / safeWL.toFloat()
        val scaleY = availH / safeWH.toFloat()
        val scale  = min(scaleX, scaleY) * 0.90f

        // Wall dimensions in pixels
        val wallW  = safeWL.toFloat() * scale
        val wallH  = safeWH.toFloat() * scale
        val wallL  = (W - wallW) / 2f
        val wallT  = elevTop + margin + (availH - wallH) / 2f
        val wallR  = wallL + wallW
        val wallBot = wallT + wallH

        // Opening dimensions in pixels
        @Suppress("UNUSED_VARIABLE")
    val openLeft   = wallL + (safeWL - safeOW - safeOW * 0.5) * scale  // offset opening (used as reference)
        val openLeft2  = wallL + safeWL.toFloat() * scale * 0.3f          // 30% from left
        val openTop2   = wallT + (safeWH - safeBase - safeOH).toFloat() * scale
        val openW2     = safeOW.toFloat() * scale
        val openH2     = safeOH.toFloat() * scale
        val openRight2 = openLeft2 + openW2
        val openBot2   = openTop2 + openH2

        val coverPx = cover.toFloat() * scale

        // ── ZONE 1: ELEVATION VIEW ────────────────────────────────────────
        if (viewMode == 0 || viewMode == 1) {
            drawWallElevation(
                wallL, wallT, wallW, wallH, wallR, wallBot,
                openLeft2, openTop2, openW2, openH2, openRight2, openBot2,
                coverPx, scale,
                headerBarDia, headerBarCount,
                sillBarDia, sillBarCount,
                verticalBarDia, verticalBarCount,
                headerStirrupDia, headerStirrupSpacing,
                diagonalBarDia, diagonalBarCount,
                wallFill, wallLight, wallStroke, openingFill, openingStroke,
                headerBarColor, sillBarColor, vertBarColor, diagBarColor,
                stirrupColor, hatchColor, stressColor, dimColor, textColor,
                sectionLineColor,
                cfg,
                // Dimension values
                safeWH, safeWL, safeOW, safeOH, safeBase, cover,
                designCode
            )
        }

        // ── ZONE 2: CROSS-SECTIONS ─────────────────────────────────────────
        if (viewMode == 0 || viewMode == 2) {
            drawWallOpeningSections(
                secTop, secBottom, W, margin, cfg,
                safeWT, safeOW, safeOH, cover,
                headerBarDia, headerBarCount,
                sillBarDia, sillBarCount,
                headerStirrupDia, verticalBarDia,
                headerBarColor, sillBarColor, stirrupColor, vertBarColor,
                wallFill, wallStroke, openingFill, hatchColor, dimColor, textColor
            )
        }

        // ── ZONE 3: BBS TABLE ─────────────────────────────────────────────
        if (viewMode == 0 || viewMode == 3) {
            drawWallOpeningBBS(
                tblTop, tblBottom, W, margin, cfg,
                safeWH, safeWL, safeOW, safeOH, safeBase, safeWT,
                headerBarDia, headerBarCount,
                sillBarDia, sillBarCount,
                verticalBarDia, verticalBarCount,
                headerStirrupDia, headerStirrupSpacing,
                diagonalBarDia, diagonalBarCount,
                cover,
                headerBarColor, sillBarColor, vertBarColor, stirrupColor, diagBarColor,
                dimColor, textColor
            )
        }

        // ── Title block + scale bar ────────────────────────────────────────
        val blockTopLeft = drawMeasuredSheetBlock(
            summary = resultSummary,
            drawingTitle = "Wall Opening Detail",
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
            scale = scale, realLengthMm = 1000f,
            cfg = cfg
        )
    }
}

// ============================================================================
// HEADER BAND
// ============================================================================

private fun DrawScope.drawWallOpeningHeader(
    W: Float, cfg: DrawingDimensionsConfig,
    codeLabel: String, textColor: Color
) {
    val hBg = Color(0xFF263238)
    drawRect(color = hBg, topLeft = Offset(0f, 0f), size = Size(W, cfg.headerHeight))
    drawTextAnnotated(
        "WALL OPENING — REINFORCEMENT DETAIL  |  $codeLabel",
        W / 2f, cfg.headerHeight * 0.65f,
        textColor, cfg.headerTextSize, center = true, bold = true
    )
    // ECP/ACI code reference strip
    drawTextAnnotated(
        "ECP 203-2020 §10 | ACI 318-19 §14 | SBC 304-2018",
        W / 2f, cfg.headerHeight * 0.90f,
        textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.85f, center = true
    )
}

// ============================================================================
// ZONE 1: WALL ELEVATION
// ============================================================================

private fun DrawScope.drawWallElevation(
    wallL: Float, wallT: Float, wallW: Float, wallH: Float,
    wallR: Float, wallBot: Float,
    openLeft: Float, openTop: Float,
    openW: Float, openH: Float,
    openRight: Float, openBot: Float,
    coverPx: Float, scale: Float,
    headerBarDia: Double, headerBarCount: Int,
    sillBarDia: Double, sillBarCount: Int,
    verticalBarDia: Double, verticalBarCount: Int,
    headerStirrupDia: Double, headerStirrupSpacing: Double,
    diagonalBarDia: Double, diagonalBarCount: Int,
    wallFill: Color, wallLight: Color, wallStroke: Color,
    openingFill: Color, openingStroke: Color,
    headerBarColor: Color, sillBarColor: Color,
    vertBarColor: Color, diagBarColor: Color,
    stirrupColor: Color, hatchColor: Color,
    stressColor: Color, dimColor: Color, textColor: Color,
    sectionLineColor: Color,
    cfg: DrawingDimensionsConfig,
    // Dimension values
    wallHeightMm: Double, wallLengthMm: Double,
    openingWidthMm: Double, openingHeightMm: Double,
    openingFromBaseMm: Double, cover: Double,
    designCode: DesignCode
) {
    val hairline = cfg.strokeDefault * 0.5f
    val thick    = cfg.strokeBold * 0.8f
    // Used by the dimension lines and the legend below; was only defined in the
    // sibling drawing functions, which left these four references unresolved.
    val margin = cfg.margin

    // ── 1. WALL BODY (hatched concrete) ───────────────────────────────────
    // Left panel (left of opening)
    drawRect(color = wallFill, topLeft = Offset(wallL, wallT), size = Size(openLeft - wallL, wallH))
    drawHatchPattern(wallL, wallT, openLeft - wallL, wallH, cfg.hatchSpacing, 45f, hatchColor)

    // Right panel (right of opening)
    drawRect(color = wallFill, topLeft = Offset(openRight, wallT), size = Size(wallR - openRight, wallH))
    drawHatchPattern(openRight, wallT, wallR - openRight, wallH, cfg.hatchSpacing, 45f, hatchColor)

    // Above opening (spandrel / header zone)
    drawRect(color = wallLight, topLeft = Offset(openLeft, wallT), size = Size(openW, openTop - wallT))
    drawHatchPattern(openLeft, wallT, openW, openTop - wallT, cfg.hatchSpacing, 45f, hatchColor.copy(alpha = 0.4f))

    // Below opening (sill zone)
    if (openBot < wallBot) {
        drawRect(color = wallFill, topLeft = Offset(openLeft, openBot), size = Size(openW, wallBot - openBot))
        drawHatchPattern(openLeft, openBot, openW, wallBot - openBot, cfg.hatchSpacing, 45f, hatchColor)
    }

    // ── 2. OPENING VOID ───────────────────────────────────────────────────
    drawRect(color = openingFill, topLeft = Offset(openLeft, openTop), size = Size(openW, openH))
    drawRect(color = openingStroke,
        topLeft = Offset(openLeft, openTop), size = Size(openW, openH),
        style = Stroke(width = thick, join = StrokeJoin.Miter))

    // Wall outline
    val dash4 = PathEffect.dashPathEffect(floatArrayOf(12f, 5f), 0f)
    drawRect(color = wallStroke,
        topLeft = Offset(wallL, wallT), size = Size(wallW, wallH),
        style = Stroke(width = thick))

    // ── 3. HEADER (LINTEL) BARS — above opening ───────────────────────────
    val headerBeamTop  = openTop - coverPx * 2.0f - headerBarDia.toFloat() * scale
    val headerBeamBot  = openTop - coverPx
    val headerBarR     = (headerBarDia / 2.0 * scale * 0.5).toFloat().coerceAtLeast(cfg.rebarMinRadius)
    val headerSpacing  = (openW - 2 * coverPx) / (headerBarCount + 1).toFloat()

    // Header zone highlight
    drawRect(color = headerBarColor.copy(alpha = 0.08f),
        topLeft = Offset(openLeft, openTop - openH * 0.25f),
        size = Size(openW, openH * 0.25f))

    // Header bars (top + bottom layer)
    for (i in 1..headerBarCount) {
        val bx = openLeft + headerSpacing * i
        // Top layer bars
        drawCircle(color = headerBarColor, radius = headerBarR,
            center = Offset(bx, headerBeamTop + headerBarR))
        // Ring
        drawCircle(color = headerBarColor.copy(alpha = 0.4f), radius = headerBarR + 2f,
            center = Offset(bx, headerBeamTop + headerBarR),
            style = Stroke(0.8f))
        // Bottom layer bars (if 2+ rows needed)
        if (headerBarCount > 3) {
            drawCircle(color = headerBarColor, radius = headerBarR,
                center = Offset(bx, headerBeamBot - headerBarR))
        }
    }
    // Header bar running lines
    drawLine(color = headerBarColor.copy(alpha = 0.5f),
        start = Offset(openLeft - 20f, headerBeamTop + headerBarR),
        end   = Offset(openRight + 20f, headerBeamTop + headerBarR),
        strokeWidth = headerBarR * 1.5f, cap = StrokeCap.Round)

    // Label
    val hbLabel = "${headerBarCount}Ø${headerBarDia.toInt()} (Header)"
    drawTextAnnotated(hbLabel, openLeft + openW / 2f, openTop - openH * 0.28f - 6f,
        headerBarColor, cfg.annotationTextSmall, center = true, bold = true)

    // ── 4. HEADER STIRRUPS ────────────────────────────────────────────────
    val sSpacingPx = (headerStirrupSpacing * scale).toFloat().coerceAtLeast(8f)
    val stirrupH   = (openTop - wallT) * 0.7f
    val stirrupT   = openTop - stirrupH - coverPx
    val stirrupB   = openTop - coverPx
    val sLineW     = cfg.strokeRebar * 0.6f

    var sx = openLeft + coverPx
    while (sx < openRight - coverPx) {
        drawRect(color = stirrupColor,
            topLeft = Offset(sx - 1f, stirrupT),
            size = Size(sSpacingPx.coerceAtMost(openW * 0.3f), stirrupH),
            style = Stroke(width = sLineW))
        // Hook at top
        drawLine(stirrupColor, Offset(sx, stirrupT),
            Offset(sx - 6f, stirrupT - 8f), sLineW, cap = StrokeCap.Round)
        sx += sSpacingPx
    }
    drawTextAnnotated("Ø${headerStirrupDia.toInt()}@${headerStirrupSpacing.toInt()}",
        openLeft + 4f, stirrupT - 10f, stirrupColor, cfg.annotationTextSmall * 0.9f)

    // ── 5. SILL BARS — below opening ──────────────────────────────────────
    val sillBarR    = (sillBarDia / 2.0 * scale * 0.5).toFloat().coerceAtLeast(cfg.rebarMinRadius)
    val sillBarY    = openBot + coverPx + sillBarR
    val sillSpacing = (openW - 2 * coverPx) / (sillBarCount + 1).toFloat()

    for (i in 1..sillBarCount) {
        val bx = openLeft + sillSpacing * i
        drawCircle(color = sillBarColor, radius = sillBarR,
            center = Offset(bx, sillBarY))
        drawCircle(color = sillBarColor.copy(alpha = 0.4f), radius = sillBarR + 2f,
            center = Offset(bx, sillBarY), style = Stroke(0.8f))
    }
    drawLine(color = sillBarColor.copy(alpha = 0.5f),
        start = Offset(openLeft - 15f, sillBarY),
        end   = Offset(openRight + 15f, sillBarY),
        strokeWidth = sillBarR * 1.5f, cap = StrokeCap.Round)
    drawTextAnnotated("${sillBarCount}Ø${sillBarDia.toInt()} (Sill)",
        openLeft + openW / 2f, sillBarY + sillBarR + 10f,
        sillBarColor, cfg.annotationTextSmall, center = true)

    // ── 6. VERTICAL JAMB BARS — each side ────────────────────────────────
    val vertBarR      = (verticalBarDia / 2.0 * scale * 0.5).toFloat().coerceAtLeast(cfg.rebarMinRadius)
    val vertSpacing   = openH / (verticalBarCount + 1).toFloat()
    val developPx     = (40.0 * verticalBarDia * scale).toFloat().coerceAtMost(openH * 0.3f)

    for (i in 1..verticalBarCount) {
        val barY = openTop + vertSpacing * i
        // Left jamb
        drawCircle(color = vertBarColor, radius = vertBarR,
            center = Offset(openLeft - coverPx - vertBarR, barY))
        // Right jamb
        drawCircle(color = vertBarColor, radius = vertBarR,
            center = Offset(openRight + coverPx + vertBarR, barY))
    }
    // Vertical bar running lines (left and right)
    val vLineW = vertBarR * 1.4f
    drawLine(color = vertBarColor.copy(alpha = 0.5f),
        start = Offset(openLeft - coverPx - vertBarR, openTop - developPx),
        end   = Offset(openLeft - coverPx - vertBarR, openBot + developPx),
        strokeWidth = vLineW, cap = StrokeCap.Round)
    drawLine(color = vertBarColor.copy(alpha = 0.5f),
        start = Offset(openRight + coverPx + vertBarR, openTop - developPx),
        end   = Offset(openRight + coverPx + vertBarR, openBot + developPx),
        strokeWidth = vLineW, cap = StrokeCap.Round)

    // Development length annotation (left side)
    val devLabel = "Ld = ${(40 * verticalBarDia).toInt()} mm"
    drawTextAnnotated(devLabel, openLeft - coverPx - 50f, openTop - developPx / 2f,
        vertBarColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.85f)
    // Dev length bracket
    val devX = openLeft - coverPx - 60f
    drawLine(vertBarColor.copy(alpha = 0.5f), Offset(devX, openTop - developPx), Offset(devX, openTop),
        cfg.strokeDefault * 0.7f)

    // Label
    drawTextAnnotated("${verticalBarCount}Ø${verticalBarDia.toInt()} each side (Jamb)",
        openLeft - coverPx - vertBarR - 8f, openTop + openH * 0.5f,
        vertBarColor, cfg.annotationTextSmall * 0.85f)

    // ── 7. DIAGONAL CORNER BARS (45°) ─────────────────────────────────────
    val diagLen = (openH * 0.4f).coerceAtLeast(100f * scale)
    val diagBarW = (diagonalBarDia / 2.0 * scale * 0.3).toFloat().coerceAtLeast(1.5f)
    // 4 corners
    val corners = listOf(
        openLeft  to openTop,   // top-left
        openRight to openTop,   // top-right
        openLeft  to openBot,   // bottom-left
        openRight to openBot    // bottom-right
    )
    val diagDirs = listOf(
        -1f to -1f,  // top-left: extends up-left
        +1f to -1f,  // top-right: extends up-right
        -1f to +1f,  // bottom-left: extends down-left
        +1f to +1f   // bottom-right: extends down-right
    )
    corners.zip(diagDirs).forEach { (corner, dir) ->
        val (cx, cy) = corner
        val (dx, dy) = dir
        val ex = cx + dx * diagLen * 0.707f
        val ey = cy + dy * diagLen * 0.707f
        // Draw diagonal bar for each count
        for (k in 0 until diagonalBarCount.coerceAtMost(2)) {
            val offset = k * (diagonalBarDia * scale * 0.6f).toFloat()
            drawLine(
                color = diagBarColor,
                start = Offset(cx + dy * offset, cy + dx * offset),
                end   = Offset(ex + dy * offset, ey + dx * offset),
                strokeWidth = diagBarW,
                cap = StrokeCap.Round
            )
        }
    }
    // Diagonal label (top-right corner)
    drawTextAnnotated("${diagonalBarCount}Ø${diagonalBarDia.toInt()} 45° Diag.",
        corners[1].first + 10f, corners[1].second - 20f,
        diagBarColor, cfg.annotationTextSmall * 0.85f)

    // ── 8. ARCHING STRESS DIAGRAM ─────────────────────────────────────────
    drawWallArchStress(
        openLeft, openTop, openW, openH,
        wallL, wallT, wallH,
        stressColor, cfg
    )

    // ── 9. SECTION CUT LINES (A-A and B-B) ────────────────────────────────
    run {
        // Section A-A: through header (vertical line)
        val aaCutX = openLeft + openW * 0.5f
        drawSectionCutLine(aaCutX, wallT - 8f, aaCutX, openTop + 10f,
            "A", sectionLineColor, 11f, cfg.annotationTextSmall)
        // Section B-B: through wall thickness (horizontal line through opening)
        val bbCutY = openTop + openH * 0.5f
        drawSectionCutLine(wallL - 8f, bbCutY, wallR + 8f, bbCutY,
            "B", sectionLineColor.copy(green = 0.8f), 11f, cfg.annotationTextSmall)
    }

    // ── 10. DIMENSION LINES ───────────────────────────────────────────────
    // Wall overall height (right side)
    drawVerticalDimension(
        y1 = wallT, y2 = wallBot, x = wallR + 15f,
        text = "H=${wallHeightMm.toInt()}mm",
        color = dimColor, textSize = cfg.dimTextSize, offset = 24f, cfg = cfg
    )
    // Wall overall length (bottom)
    drawHorizontalDimension(
        x1 = wallL, x2 = wallR, y = wallBot + margin * 0.4f,
        text = "L=${wallLengthMm.toInt()}mm",
        color = dimColor, textSize = cfg.dimTextSize, offset = 20f, cfg = cfg
    )
    // Opening width (at opening top)
    drawHorizontalDimension(
        x1 = openLeft, x2 = openRight, y = openTop,
        text = "b=${openingWidthMm.toInt()}mm",
        color = dimColor.copy(alpha = 0.8f), textSize = cfg.dimTextSize * 0.9f,
        offset = -20f, cfg = cfg
    )
    // Opening height (left side)
    drawVerticalDimension(
        y1 = openTop, y2 = openBot,
        x = openLeft - margin * 0.3f,
        text = "h=${openingHeightMm.toInt()}mm",
        color = dimColor.copy(alpha = 0.8f), textSize = cfg.dimTextSize * 0.9f,
        offset = -22f, cfg = cfg
    )
    // Height from base to opening sill
    if (openingFromBaseMm > 50) {
        drawVerticalDimension(
            y1 = openBot, y2 = wallBot,
            x = openLeft - margin * 0.3f,
            text = "hs=${openingFromBaseMm.toInt()}mm",
            color = dimColor.copy(alpha = 0.6f), textSize = cfg.dimTextSize * 0.85f,
            offset = -40f, cfg = cfg
        )
    }

    // ── 11. LEGEND ────────────────────────────────────────────────────────
    val legX = wallL
    val legY = wallBot + margin * 1.5f
    val legItems = listOf(
        headerBarColor to "Header Bars",
        sillBarColor   to "Sill Bars",
        vertBarColor   to "Jamb Bars",
        diagBarColor   to "Diagonal Bars",
        stirrupColor   to "Stirrups"
    )
    legItems.forEachIndexed { i, (color, label) ->
        val lx = legX + i * (wallW / legItems.size)
        drawCircle(color = color, radius = 5f, center = Offset(lx, legY))
        drawTextAnnotated(label, lx + 10f, legY + 4f, color, cfg.annotationTextSmall * 0.85f)
    }
}

// ============================================================================
// ARCHING STRESS DIAGRAM
// ============================================================================

private fun DrawScope.drawWallArchStress(
    openLeft: Float, openTop: Float, openW: Float, openH: Float,
    wallL: Float, wallT: Float, wallH: Float,
    stressColor: Color, cfg: DrawingDimensionsConfig
) {
    // Draw an arch above the opening to represent load transfer
    val archHeight = (openH * 0.6f).coerceAtMost((openTop - wallT) * 0.8f)
    val archCenterX = openLeft + openW / 2f
    val archBottomY  = openTop
    val archTopY     = openTop - archHeight

    val archPath = Path()
    archPath.moveTo(openLeft - openW * 0.1f, archBottomY)
    archPath.cubicTo(
        openLeft - openW * 0.1f, archTopY,          // left control
        openRight2helper(openLeft, openW) + openW * 0.1f, archTopY,  // right control
        openLeft + openW + openW * 0.1f, archBottomY // right end
    )

    drawPath(archPath, stressColor.copy(alpha = 0.3f), style = Stroke(cfg.strokeBold * 0.7f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)))

    // Compression arrows along arch
    val numArrows = 5
    for (i in 0..numArrows) {
        val t = i.toFloat() / numArrows
        val ax = openLeft - openW * 0.1f + (openW * 1.2f) * t
        val ay = archBottomY - archHeight * sin(t * PI.toFloat()) * 0.9f
        // Small arrow downward at each point
        val arrowLen = 10f
        drawLine(stressColor.copy(alpha = 0.5f),
            Offset(ax, ay - arrowLen), Offset(ax, ay),
            cfg.strokeDefault * 0.8f)
        drawPath(Path().apply {
            moveTo(ax, ay)
            lineTo(ax - 3f, ay - 5f)
            lineTo(ax + 3f, ay - 5f)
            close()
        }, stressColor.copy(alpha = 0.5f))
    }

    drawTextAnnotated("Arching Action", archCenterX, archTopY - 10f,
        stressColor.copy(alpha = 0.7f), cfg.annotationTextSmall * 0.85f, center = true)
}

private fun openRight2helper(openLeft: Float, openW: Float) = openLeft + openW

// ============================================================================
// ZONE 2: WALL SECTIONS (A-A THROUGH HEADER, B-B THROUGH WALL)
// ============================================================================

private fun DrawScope.drawWallOpeningSections(
    secTop: Float, secBottom: Float, W: Float, margin: Float,
    cfg: DrawingDimensionsConfig,
    wallThicknessMm: Double, openingWidthMm: Double, openingHeightMm: Double,
    cover: Double,
    headerBarDia: Double, headerBarCount: Int,
    sillBarDia: Double, sillBarCount: Int,
    headerStirrupDia: Double, verticalBarDia: Double,
    headerBarColor: Color, sillBarColor: Color,
    stirrupColor: Color, vertBarColor: Color,
    wallFill: Color, wallStroke: Color, openingFill: Color,
    hatchColor: Color, dimColor: Color, textColor: Color
) {
    val sectionH = secBottom - secTop
    val halfW    = W / 2f - margin

    // ── SECTION A-A: through header (vertical cross-section of wall thickness) ──
    val aaLeft = margin
    val aaRight = halfW
    val aaW = aaRight - aaLeft
    val aaH = sectionH * 0.9f
    val aaTop = secTop + (sectionH - aaH) / 2f

    drawTextAnnotated("SECTION A-A", aaLeft + aaW / 2f, aaTop - 10f,
        textColor, cfg.sectionLabelTextSize, center = true, bold = true)

    // Wall thickness scale
    val wallThPx = (wallThicknessMm * aaH / (wallThicknessMm + 100.0)).toFloat().coerceAtLeast(40f)
    val headerH_px = (openingHeightMm * 0.3f * aaH / wallThicknessMm).toFloat().coerceAtLeast(30f)
    val secScale = wallThPx / wallThicknessMm.toFloat()
    val coverPx  = cover.toFloat() * secScale

    val rectX = aaLeft + (aaW - wallThPx) / 2f
    val rectY = aaTop + aaH * 0.1f
    val rectW = wallThPx
    val rectH = headerH_px + 20f

    // Wall section rectangle (concrete)
    drawRect(color = wallFill, topLeft = Offset(rectX, rectY), size = Size(rectW, rectH))
    drawHatchPattern(rectX, rectY, rectW, rectH, cfg.hatchSpacing * 0.8f, 45f, hatchColor)
    drawRect(color = wallStroke, topLeft = Offset(rectX, rectY), size = Size(rectW, rectH),
        style = Stroke(cfg.strokeBold * 0.8f))

    // Stirrup rectangle
    val sL = rectX + coverPx; val sT = rectY + coverPx
    val sW = rectW - 2 * coverPx; val sHh = rectH - 2 * coverPx
    drawRect(color = stirrupColor, topLeft = Offset(sL, sT), size = Size(sW, sHh),
        style = Stroke(cfg.strokeRebar * 0.7f))

    // Header bars (circles in section)
    val barSpacing = (sW - 2 * coverPx) / (headerBarCount + 1).toFloat()
    val barR = (headerBarDia / 2.0 * secScale * 0.5).toFloat().coerceAtLeast(cfg.rebarMinRadius * 0.8f)
    for (i in 1..headerBarCount) {
        val bx = sL + coverPx + barSpacing * i
        // Bottom bars
        drawCircle(color = headerBarColor, radius = barR, center = Offset(bx, sT + sHh - coverPx - barR))
        // Top bars (hanger)
        drawCircle(color = headerBarColor.copy(alpha = 0.6f), radius = barR * 0.7f,
            center = Offset(bx, sT + coverPx + barR * 0.7f))
    }

    // Cover dimensions
    drawTextAnnotated("c=${cover.toInt()}mm", rectX + 2f, sT + sHh / 2f,
        dimColor.copy(alpha = 0.7f), cfg.annotationTextSmall * 0.75f)

    // Width dimension
    drawHorizontalDimension(rectX, rectX + rectW, rectY + rectH + 5f,
        "t=${wallThicknessMm.toInt()}mm",
        dimColor, cfg.dimTextSize * 0.85f, 16f, cfg)

    // ── SECTION B-B: horizontal through opening (plan view of wall) ──
    val bbLeft = W / 2f + margin
    val bbRight = W - margin
    val bbW = bbRight - bbLeft
    val bbTop = secTop + sectionH * 0.1f
    val bbBot = secBottom - sectionH * 0.1f
    val bbH   = bbBot - bbTop

    drawTextAnnotated("SECTION B-B", bbLeft + bbW / 2f, bbTop - 10f,
        textColor, cfg.sectionLabelTextSize, center = true, bold = true)

    // Plan view: wall (top) + opening (void in middle) + wall (bottom)
    val planScale = bbH / (openingWidthMm + 2 * wallThicknessMm).toFloat()
    val wallThPx2 = wallThicknessMm.toFloat() * planScale
    val openWPx2  = openingWidthMm.toFloat() * planScale

    val wallTopRect  = Offset(bbLeft, bbTop)
    val wallTopSize  = Size(bbW, wallThPx2)
    val openRect     = Offset(bbLeft, bbTop + wallThPx2)
    val openSize     = Size(bbW, openWPx2)
    val wallBotRect  = Offset(bbLeft, bbTop + wallThPx2 + openWPx2)
    val wallBotSize  = Size(bbW, wallThPx2)

    // Top wall section
    drawRect(color = wallFill, topLeft = wallTopRect, size = wallTopSize)
    drawHatchPattern(wallTopRect.x, wallTopRect.y, wallTopSize.width, wallTopSize.height,
        cfg.hatchSpacing * 0.8f, 45f, hatchColor)
    drawRect(color = wallStroke, topLeft = wallTopRect, size = wallTopSize, style = Stroke(cfg.strokeBold * 0.6f))

    // Opening void (plan view shows bars crossing)
    drawRect(color = openingFill, topLeft = openRect, size = openSize)
    // Vertical bars in plan (circles)
    val vBarR2 = (verticalBarDia / 2.0 * planScale * 0.5).toFloat().coerceAtLeast(cfg.rebarMinRadius * 0.6f)
    val planCovPx = cover.toFloat() * planScale
    val numVBars = 4.coerceAtLeast(headerBarCount)
    val vBarSpacing2 = (bbW - 2 * planCovPx) / (numVBars + 1)
    for (i in 1..numVBars) {
        val vbx = bbLeft + planCovPx + vBarSpacing2 * i
        drawCircle(color = vertBarColor, radius = vBarR2,
            center = Offset(vbx, bbTop + wallThPx2 + openWPx2 / 2f))
    }
    drawTextAnnotated("Ø${verticalBarDia.toInt()} Vertical", bbLeft + bbW / 2f,
        bbTop + wallThPx2 + openWPx2 / 2f - 10f, vertBarColor, cfg.annotationTextSmall * 0.75f, center = true)

    // Bottom wall section
    drawRect(color = wallFill, topLeft = wallBotRect, size = wallBotSize)
    drawHatchPattern(wallBotRect.x, wallBotRect.y, wallBotSize.width, wallBotSize.height,
        cfg.hatchSpacing * 0.8f, 45f, hatchColor)
    drawRect(color = wallStroke, topLeft = wallBotRect, size = wallBotSize, style = Stroke(cfg.strokeBold * 0.6f))

    // Height dimension (opening width shown in this section)
    drawVerticalDimension(
        y1 = bbTop + wallThPx2, y2 = bbTop + wallThPx2 + openWPx2,
        x = bbRight + 5f,
        text = "bw=${openingWidthMm.toInt()}mm",
        color = dimColor, textSize = cfg.dimTextSize * 0.85f, offset = 20f, cfg = cfg
    )
}

// ============================================================================
// ZONE 3: BBS TABLE (BAR BENDING SCHEDULE)
// ============================================================================

private fun DrawScope.drawWallOpeningBBS(
    tblTop: Float, tblBottom: Float, W: Float, margin: Float,
    cfg: DrawingDimensionsConfig,
    wallHeightMm: Double, wallLengthMm: Double,
    openingWidthMm: Double, openingHeightMm: Double,
    openingFromBaseMm: Double, wallThicknessMm: Double,
    headerBarDia: Double, headerBarCount: Int,
    sillBarDia: Double, sillBarCount: Int,
    verticalBarDia: Double, verticalBarCount: Int,
    headerStirrupDia: Double, headerStirrupSpacing: Double,
    diagonalBarDia: Double, diagonalBarCount: Int,
    cover: Double,
    headerBarColor: Color, sillBarColor: Color, vertBarColor: Color,
    stirrupColor: Color, diagBarColor: Color,
    dimColor: Color, textColor: Color
) {
    val tblH    = tblBottom - tblTop
    val tblLeft = margin
    val tblRight = W - margin
    val tblW    = tblRight - tblLeft

    // ── Table title ────────────────────────────────────────────────────────
    drawTextAnnotated("BAR BENDING SCHEDULE — Wall Opening",
        tblLeft + tblW / 2f, tblTop + 2f,
        textColor, cfg.sectionLabelTextSize * 0.9f, center = true, bold = true)

    // ── Compute bar data ───────────────────────────────────────────────────
    // Header bars: length = openingWidth + 2 × development (40Ø)
    val headerDevLength = 40.0 * headerBarDia
    val headerBarLength = openingWidthMm + 2.0 * headerDevLength
    val headerBarWeight = headerBarCount * headerBarLength * headerBarDia * headerBarDia * 0.00617 / 1000.0 // kg

    // Sill bars
    val sillDevLength = 40.0 * sillBarDia
    val sillBarLength = openingWidthMm + 2.0 * sillDevLength
    val sillBarWeight = sillBarCount * sillBarLength * sillBarDia * sillBarDia * 0.00617 / 1000.0

    // Vertical jamb bars (each side: 2 sides)
    val vertBarLength = openingHeightMm + 2.0 * (40.0 * verticalBarDia)
    val vertBarTotal  = verticalBarCount * 2 // both sides
    val vertBarWeight = vertBarTotal * vertBarLength * verticalBarDia * verticalBarDia * 0.00617 / 1000.0

    // Header stirrups
    val stirrupCount = ((openingWidthMm / headerStirrupSpacing) + 1).toInt()
    val stirrupPerimeter = 2.0 * (wallThicknessMm - 2.0 * cover) + 2.0 * (openingHeightMm * 0.3)
    val stirrupLength = stirrupPerimeter + 2.0 * (10.0 * headerStirrupDia) // 10Ø hooks
    val stirrupWeight = stirrupCount * stirrupLength * headerStirrupDia * headerStirrupDia * 0.00617 / 1000.0

    // Diagonal bars (4 corners × diagonalBarCount)
    val diagLength    = sqrt(2.0) * (openingHeightMm * 0.4) + 2.0 * (40.0 * diagonalBarDia)
    val diagTotal     = diagonalBarCount * 4 // 4 corners
    val diagWeight    = diagTotal * diagLength * diagonalBarDia * diagonalBarDia * 0.00617 / 1000.0

    val totalWeight = headerBarWeight + sillBarWeight + vertBarWeight + stirrupWeight + diagWeight

    // ── Table structure ────────────────────────────────────────────────────
    val rows = listOf(
        listOf("T1", "Ø${headerBarDia.toInt()}", "$headerBarCount", "${headerBarLength.toInt()} mm",
            "Straight + Hook", "${String.format("%.2f", headerBarWeight)} kg", "Header"),
        listOf("T2", "Ø${sillBarDia.toInt()}", "$sillBarCount", "${sillBarLength.toInt()} mm",
            "Straight + Hook", "${String.format("%.2f", sillBarWeight)} kg", "Sill"),
        listOf("V1", "Ø${verticalBarDia.toInt()}", "$vertBarTotal", "${vertBarLength.toInt()} mm",
            "Straight (×2 sides)", "${String.format("%.2f", vertBarWeight)} kg", "Jamb Vert."),
        listOf("S1", "Ø${headerStirrupDia.toInt()}", "$stirrupCount",
            "${stirrupLength.toInt()} mm",
            "Closed + 135° hook", "${String.format("%.2f", stirrupWeight)} kg", "Stirrup"),
        listOf("D1", "Ø${diagonalBarDia.toInt()}", "$diagTotal", "${diagLength.toInt()} mm",
            "45° Diagonal", "${String.format("%.2f", diagWeight)} kg", "Corner Diag.")
    )
    val headers = listOf("Mark", "Dia.", "No.", "Cut Length", "Shape", "Weight (kg)", "Location")
    val colFracs = listOf(0.07f, 0.07f, 0.06f, 0.14f, 0.22f, 0.17f, 0.27f)
    val colWidths = colFracs.map { it * tblW }

    val tblBodyTop = tblTop + 18f
    drawResponsiveReinforcementTable(
        x = tblLeft, y = tblBodyTop,
        colWidths = colWidths,
        headers = headers,
        rows = rows,
        cfg = cfg,
        headerBg = DrawingColorDefaults.TableHeaderBg,
        altRowBg = DrawingColorDefaults.TableRowAlt,
        textColor = textColor
    )

    // ── Color legend for each row ──────────────────────────────────────────
    val rowColors = listOf(headerBarColor, sillBarColor, vertBarColor, stirrupColor, diagBarColor)
    val rowH      = cfg.tableRowHeight
    val hdrH      = cfg.tableHeaderHeight
    rowColors.forEachIndexed { i, color ->
        val dotY = tblBodyTop + hdrH + rowH * i + rowH / 2f
        drawCircle(color = color, radius = 4f, center = Offset(tblLeft + 4f, dotY))
    }

    // ── Totals row ─────────────────────────────────────────────────────────
    val totY = tblBodyTop + hdrH + rowH * rows.size + 4f
    drawLine(DrawingColorDefaults.ExtensionGray.copy(alpha = 0.5f),
        Offset(tblLeft, totY - 2f), Offset(tblRight, totY - 2f), cfg.strokeDefault)
    drawTextAnnotated("TOTAL STEEL WEIGHT:",
        tblLeft + 2f, totY + cfg.dimTextSize * 1.2f,
        textColor, cfg.dimTextSize, bold = true)
    drawTextAnnotated("${String.format("%.2f", totalWeight)} kg",
        tblLeft + tblW * 0.4f, totY + cfg.dimTextSize * 1.2f,
        DrawingColorDefaults.SafeGreen, cfg.dimTextSize, bold = true)
    drawTextAnnotated("(≈ ${String.format("%.0f", totalWeight / 7850.0 * 1e6)} cm³ steel volume)",
        tblLeft + tblW * 0.6f, totY + cfg.dimTextSize * 1.2f,
        textColor.copy(alpha = 0.6f), cfg.dimTextSize * 0.85f)

    // ── Notes ──────────────────────────────────────────────────────────────
    val notesY = totY + cfg.dimTextSize * 2.5f
    val notes = listOf(
        "1. All bars to BS 4449 / ASTM A615 / ECP deformed bars.",
        "2. Development lengths per governing code (ECP §6.5 / ACI §25.5).",
        "3. Diagonal bars placed at ±45° at all 4 corners of opening.",
        "4. Stirrups in header: closed links with 135° hooks, 10Ø extensions."
    )
    notes.forEachIndexed { i, note ->
        drawTextAnnotated(note, tblLeft + 4f, notesY + i * cfg.dimTextSize * 1.4f,
            textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.85f)
    }
}
