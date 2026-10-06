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
 * Professional Strap Footing Engineering Drawing
 * Renders plan view (two footings connected by strap beam), section view
 * (longitudinal section with strap beam and soil pressure), strap beam
 * detail (cross-section with reinforcement), and reinforcement table.
 *
 * ECP 203-2020: Chapter 10 — strap (cantilever) footings
 * ACI 318-19:  Chapter 13 — strap beam design, cantilever action
 */
@Composable
fun ProfessionalStrapFootingDrawing(
    footing1Length: Double,
    footing1Width: Double,
    footing1Thickness: Double,
    footing2Length: Double,
    footing2Width: Double,
    footing2Thickness: Double,
    strapWidth: Double,
    strapThickness: Double,
    distanceBetweenColumns: Double,
    col1Width: Double,
    col2Width: Double,
    rebar1Dia: Double,
    rebar1Count: Int,
    rebar2Dia: Double,
    rebar2Count: Int,
    strapDia: Double,
    strapCount: Int,
    cover: Double,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard: validate key dimensions before drawing ─────────────────
    InputGuard.positive("footing1Length", footing1Length)
    InputGuard.positive("footing1Width", footing1Width)
    InputGuard.positive("footing1Thickness", footing1Thickness)
    InputGuard.positive("footing2Length", footing2Length)
    InputGuard.positive("footing2Width", footing2Width)
    InputGuard.positive("footing2Thickness", footing2Thickness)
    InputGuard.positive("strapWidth", strapWidth)
    InputGuard.positive("strapThickness", strapThickness)
    InputGuard.positive("distanceBetweenColumns", distanceBetweenColumns)
    InputGuard.positive("col1Width", col1Width)
    InputGuard.positive("col2Width", col2Width)
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
        val safeF1L = footing1Length.coerceAtLeast(0.5)
        val safeF1W = footing1Width.coerceAtLeast(0.5)
        val safeF1T = footing1Thickness.coerceAtLeast(300.0)
        val safeF2L = footing2Length.coerceAtLeast(0.5)
        val safeF2W = footing2Width.coerceAtLeast(0.5)
        val safeF2T = footing2Thickness.coerceAtLeast(300.0)
        val safeStrapW = strapWidth.coerceAtLeast(200.0)
        val safeStrapT = strapThickness.coerceAtLeast(300.0)
        val safeDist = distanceBetweenColumns.coerceAtLeast(1.0)
        val safeC1W = col1Width.coerceAtLeast(200.0)
        val safeC2W = col2Width.coerceAtLeast(200.0)

        // Normalize to mm
        val f1LMm = if (safeF1L < 50.0) safeF1L * 1000.0 else safeF1L
        val f1WMm = if (safeF1W < 50.0) safeF1W * 1000.0 else safeF1W
        val f2LMm = if (safeF2L < 50.0) safeF2L * 1000.0 else safeF2L
        val f2WMm = if (safeF2W < 50.0) safeF2W * 1000.0 else safeF2W
        val distMm = if (safeDist < 50.0) safeDist * 1000.0 else safeDist
        val totalLen = f1LMm + distMm + f2LMm  // approximate total length

        // ── Color Palette ──────────────────────────────────────────
        val C = DrawingColorDefaults
        val concreteFill = C.ConcreteFill
        val concreteStroke = C.ConcreteGray
        val strapFill = Color(0xFF4A4A4A)
        val colFill = Color(0xFF555555)
        val colStroke = Color(0xFF333333)
        val bar1Color = C.RebarBlue
        val bar2Color = C.TopRebarBlue
        val barStrapColor = C.SafeGreen
        val dimColor = C.ExtensionGray
        val textColor = C.DimensionWhite
        val headerBg = Color(0x55333333)
        val tableHeaderBg = Color(0x55333333)
        val hatchColor = Color(0x55AAAAAA)
        val soilColor = C.SoilBrown
        val soilHatchColor = C.SoilBrown.copy(alpha = 0.7f)
        val stirrupColor = C.StirrupPurple

        // ── Layout zones (viewMode-aware) ────────────────────────
        val margin = cfg.margin
        val planH = when (viewMode) { 1 -> h * 0.82f; 0 -> h * 0.33f; else -> h * 0.10f }
        val secH = when (viewMode) { 2 -> h * 0.45f; 0 -> h * 0.30f; else -> h * 0.10f }
        val detailH = when (viewMode) { 3 -> h * 0.40f; 0 -> h * 0.10f; else -> 0f }
        val tableH = when (viewMode) { 3 -> h * 0.88f; 0 -> h * cfg.tableHeightFraction; else -> h * 0.10f }

        val planTop = h * 0.05f
        val planBottom = planTop + planH
        val secTop = planBottom + h * 0.02f
        val secBottom = secTop + secH
        val detTop = secBottom + h * 0.01f
        val detBottom = detTop + detailH
        val tblTop = if (viewMode == 0 && detailH > 0f) detBottom + h * 0.02f else secBottom + h * 0.02f

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, cfg.headerHeight))
        drawTextAnnotated(
            "STRAP FOOTING DETAIL — $codeLabel",
            w / 2f, cfg.headerHeight * 0.65f, textColor, cfg.headerTextSize, center = true, bold = true
        )

        // ── Responsive section separators & zone labels ───────
        if (viewMode == 0) {
            drawCombinedSectionLabel(h * 0.015f, w, listOf("Plan", "Section", "Detail", "Table"), cfg)
            drawSectionSeparator(planTop - h * 0.01f, w, "PLAN / SECTION", cfg)
        } else if (viewMode == 1) {
            drawZoneLabel(margin, planTop + 4f, "PLAN", cfg)
        } else if (viewMode == 2) {
            drawZoneLabel(margin, secTop + 4f, "SECTION", cfg)
        } else if (viewMode == 3) {
            drawZoneLabel(margin, tblTop + 4f, "TABLE", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW — Two footings + strap beam
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            val planLeft = margin + 50f
            val planRight = w - margin
            val planW = planRight - planLeft
            val planDrawH = planBottom - planTop

            // Scale to fit both footings + strap
            val maxW = max(f1WMm, f2WMm)
            val scaleX = planW / totalLen.toFloat()
            val scaleY = planDrawH / (maxW * 1.2).toFloat()
            val scale = min(scaleX, scaleY) * 0.85f

            val drawTotalLen = totalLen.toFloat() * scale
            val drawF1L = f1LMm.toFloat() * scale
            val drawF1W = f1WMm.toFloat() * scale
            val drawF2L = f2LMm.toFloat() * scale
            val drawF2W = f2WMm.toFloat() * scale
            val drawStrapW = safeStrapW.toFloat() * scale
            val drawDist = distMm.toFloat() * scale

            val centerX = planLeft + planW / 2f
            val centerY = planTop + planDrawH / 2f

            // Footing 1 (left)
            val f1Left = centerX - drawTotalLen / 2f
            val f1Right = f1Left + drawF1L
            val f1Top = centerY - drawF1W / 2f
            val f1Bottom = centerY + drawF1W / 2f

            // Footing 2 (right)
            val f2Right = centerX + drawTotalLen / 2f
            val f2Left = f2Right - drawF2L
            val f2Top = centerY - drawF2W / 2f
            val f2Bottom = centerY + drawF2W / 2f

            // Strap beam (between footings)
            val strapLeft = f1Right
            val strapRight = f2Left
            val strapTop = centerY - drawStrapW / 2f
            val strapBottom = centerY + drawStrapW / 2f

            // Footing 1 body
            drawRect(color = concreteFill, topLeft = Offset(f1Left, f1Top), size = Size(drawF1L, drawF1W))
            drawHatchPattern(f1Left, f1Top, drawF1L, drawF1W, spacing = 16f, angleDeg = 45f, color = hatchColor)

            // Footing 2 body
            drawRect(color = concreteFill, topLeft = Offset(f2Left, f2Top), size = Size(drawF2L, drawF2W))
            drawHatchPattern(f2Left, f2Top, drawF2L, drawF2W, spacing = 16f, angleDeg = 45f, color = hatchColor)

            // Strap beam
            drawRect(color = strapFill, topLeft = Offset(strapLeft, strapTop), size = Size(strapRight - strapLeft, drawStrapW))
            drawHatchPattern(strapLeft, strapTop, strapRight - strapLeft, drawStrapW, spacing = 10f, angleDeg = -45f, color = Color(0x44999999))

            // Strap beam reinforcement lines
            if (strapCount > 1) {
                val step = drawStrapW / (strapCount + 1).toFloat()
                for (i in 1..strapCount) {
                    val sy = strapTop + i * step
                    drawLine(barStrapColor, Offset(strapLeft + 4f, sy), Offset(strapRight - 4f, sy), strokeWidth = 1.2f)
                }
            }

            // Footing 1 reinforcement
            if (rebar1Count > 1) {
                val barStep = drawF1W / (rebar1Count + 1).toFloat()
                for (i in 1..rebar1Count) {
                    val by = f1Top + i * barStep
                    drawLine(bar1Color, Offset(f1Left + 4f, by), Offset(f1Right - 4f, by), strokeWidth = 1.2f)
                }
            }

            // Footing 2 reinforcement
            if (rebar2Count > 1) {
                val barStep = drawF2W / (rebar2Count + 1).toFloat()
                for (i in 1..rebar2Count) {
                    val by = f2Top + i * barStep
                    drawLine(bar2Color, Offset(f2Left + 4f, by), Offset(f2Right - 4f, by), strokeWidth = 1.0f)
                }
            }

            // Column 1 on footing 1
            val c1Wpx = (safeC1W * scale).toFloat()
            val c1Cx = f1Left + drawF1L / 2f
            drawRect(color = colFill, topLeft = Offset(c1Cx - c1Wpx / 2f, centerY - c1Wpx / 2f), size = Size(c1Wpx, c1Wpx))
            drawRect(color = colStroke, topLeft = Offset(c1Cx - c1Wpx / 2f, centerY - c1Wpx / 2f), size = Size(c1Wpx, c1Wpx), style = Stroke(width = 1.5f))
            drawHatchPattern(c1Cx - c1Wpx / 2f, centerY - c1Wpx / 2f, c1Wpx, c1Wpx, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C1", c1Cx, centerY + 3f, textColor, cfg.annotationTextSmall, center = true, bold = true)

            // Column 2 on footing 2
            val c2Wpx = (safeC2W * scale).toFloat()
            val c2Cx = f2Left + drawF2L / 2f
            drawRect(color = colFill, topLeft = Offset(c2Cx - c2Wpx / 2f, centerY - c2Wpx / 2f), size = Size(c2Wpx, c2Wpx))
            drawRect(color = colStroke, topLeft = Offset(c2Cx - c2Wpx / 2f, centerY - c2Wpx / 2f), size = Size(c2Wpx, c2Wpx), style = Stroke(width = 1.5f))
            drawHatchPattern(c2Cx - c2Wpx / 2f, centerY - c2Wpx / 2f, c2Wpx, c2Wpx, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C2", c2Cx, centerY + 3f, textColor, cfg.annotationTextSmall, center = true, bold = true)

            // Borders
            drawRect(color = concreteStroke, topLeft = Offset(f1Left, f1Top), size = Size(drawF1L, drawF1W), style = Stroke(width = 2.5f))
            drawRect(color = concreteStroke, topLeft = Offset(f2Left, f2Top), size = Size(drawF2L, drawF2W), style = Stroke(width = 2.5f))
            drawRect(color = concreteStroke, topLeft = Offset(strapLeft, strapTop), size = Size(strapRight - strapLeft, drawStrapW), style = Stroke(width = 2f))

            // Bar marks
            drawTextAnnotated("\u2460", f1Right + 10f, f1Bottom + 10f, bar1Color, cfg.dimTextSize, bold = true)
            drawTextAnnotated("\u2461", f2Right + 10f, f2Bottom + 10f, bar2Color, cfg.dimTextSize, bold = true)
            drawTextAnnotated("\u2462", (strapLeft + strapRight) / 2f, strapBottom + 12f, barStrapColor, cfg.dimTextSize, center = true, bold = true)

            // Dimensions
            drawHorizontalDimension(f1Left, f1Right, f1Top, "L1=${f1LMm.toInt()}", dimColor, cfg.valueLabelTextSize, offset = -12f)
            drawHorizontalDimension(f2Left, f2Right, f2Top, "L2=${f2LMm.toInt()}", dimColor, cfg.valueLabelTextSize, offset = -12f)
            drawVerticalDimension(f1Top, f1Bottom, f1Left, "B1=${f1WMm.toInt()}", dimColor, cfg.valueLabelTextSize, offset = -14f)
            drawVerticalDimension(f2Top, f2Bottom, f2Right, "B2=${f2WMm.toInt()}", dimColor, cfg.valueLabelTextSize, offset = 10f)
            drawHorizontalDimension(f1Right, f2Left, centerY + drawStrapW, "D=${distMm.toInt()}", C.ExtensionGray, cfg.annotationTextSmall, offset = 10f)

            drawTextAnnotated("PLAN", f1Left + 20f, max(f1Bottom, f2Bottom) + 18f, C.ExtensionGray, cfg.dimTextSize, bold = true)

            // Section cut line
            if (viewMode == 0) {
                drawSectionCutLine(
                    x1 = centerX, y1 = min(f1Top, f2Top) - 20f,
                    x2 = centerX, y2 = max(f1Bottom, f2Bottom) + 6f,
                    label = "A", color = C.SectionLine
                )
            }
        } // end plan view

        // ── Section separator: plan → section ─────────────────
        if (viewMode == 0) {
            drawSectionSeparator(secTop - h * 0.01f, w, "SECTION / DETAIL", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  SECTION A-A — Longitudinal section
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val secLeft = margin + 90f
            val secRight = w - margin
            val maxSecW = secRight - secLeft - 80f

            drawTextAnnotated("SECTION A-A", secLeft - 50f, secTop + 4f, C.ExtensionGray, cfg.dimTextSize, bold = true)

            val secScale = maxSecW / totalLen.toFloat()
            val f1LenPx = f1LMm.toFloat() * secScale
            val f2LenPx = f2LMm.toFloat() * secScale
            val distPx = distMm.toFloat() * secScale

            val maxThick = max(safeF1T, max(safeF2T, safeStrapT))
            val thickPx = (maxThick * secScale).toFloat().coerceIn(25f, 80f)
            val strapThickPx = (safeStrapT * secScale).toFloat().coerceIn(20f, thickPx * 0.9f)

            val sLeft = secLeft + 40f
            val sRight = sLeft + maxSecW
            val sCenterY = secTop + secH / 2f + 5f

            // Footing 1 (left, deeper)
            val f1TopY = sCenterY - thickPx / 2f
            val f1BottomY = sCenterY + thickPx / 2f
            drawRect(color = concreteFill, topLeft = Offset(sLeft, f1TopY), size = Size(f1LenPx, thickPx))
            drawHatchPattern(sLeft, f1TopY, f1LenPx, thickPx, spacing = 8f, angleDeg = 45f, color = hatchColor)
            drawRect(color = concreteStroke, topLeft = Offset(sLeft, f1TopY), size = Size(f1LenPx, thickPx), style = Stroke(width = 2f))

            // Strap beam (middle, thinner)
            val strapLeft = sLeft + f1LenPx
            val strapRight = strapLeft + distPx
            val strapTopY = sCenterY - strapThickPx / 2f
            val strapBottomY = sCenterY + strapThickPx / 2f
            drawRect(color = strapFill, topLeft = Offset(strapLeft, strapTopY), size = Size(distPx, strapThickPx))
            drawHatchPattern(strapLeft, strapTopY, distPx, strapThickPx, spacing = 8f, angleDeg = -45f, color = Color(0x44999999))
            drawRect(color = concreteStroke, topLeft = Offset(strapLeft, strapTopY), size = Size(distPx, strapThickPx), style = Stroke(width = 1.5f))

            // Footing 2 (right, deeper)
            val f2LeftX = strapRight
            drawRect(color = concreteFill, topLeft = Offset(f2LeftX, f1TopY), size = Size(f2LenPx, thickPx))
            drawHatchPattern(f2LeftX, f1TopY, f2LenPx, thickPx, spacing = 8f, angleDeg = 45f, color = hatchColor)
            drawRect(color = concreteStroke, topLeft = Offset(f2LeftX, f1TopY), size = Size(f2LenPx, thickPx), style = Stroke(width = 2f))

            // Soil below
            val soilDepth = 16f
            drawRect(color = soilColor.copy(alpha = 0.4f), topLeft = Offset(sLeft - 10f, f1BottomY), size = Size(maxSecW + 20f, soilDepth))
            drawHatchPattern(sLeft - 10f, f1BottomY, maxSecW + 20f, soilDepth, spacing = 6f, angleDeg = -45f, color = soilHatchColor)

            // Columns
            val colH = 40f
            val c1Wpx = (safeC1W * secScale).toFloat().coerceIn(12f, f1LenPx * 0.4f)
            val c2Wpx = (safeC2W * secScale).toFloat().coerceIn(12f, f2LenPx * 0.4f)

            // Column 1
            val c1LeftX = sLeft + f1LenPx / 2f - c1Wpx / 2f
            drawRect(color = colFill, topLeft = Offset(c1LeftX, f1TopY - colH), size = Size(c1Wpx, colH))
            drawRect(color = colStroke, topLeft = Offset(c1LeftX, f1TopY - colH), size = Size(c1Wpx, colH), style = Stroke(width = 1.5f))
            drawHatchPattern(c1LeftX, f1TopY - colH, c1Wpx, colH, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C1", c1LeftX + c1Wpx / 2f, f1TopY - colH / 2f + 3f, textColor, cfg.annotationTextSmall, center = true)

            // Column 2
            val c2LeftX = f2LeftX + f2LenPx / 2f - c2Wpx / 2f
            drawRect(color = colFill, topLeft = Offset(c2LeftX, f1TopY - colH), size = Size(c2Wpx, colH))
            drawRect(color = colStroke, topLeft = Offset(c2LeftX, f1TopY - colH), size = Size(c2Wpx, colH), style = Stroke(width = 1.5f))
            drawHatchPattern(c2LeftX, f1TopY - colH, c2Wpx, colH, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C2", c2LeftX + c2Wpx / 2f, f1TopY - colH / 2f + 3f, textColor, cfg.annotationTextSmall, center = true)

            // Bottom reinforcement in footings
            val barCountF1 = rebar1Count.coerceIn(2, 10)
            val stepF1 = (f1LenPx - 8f) / (barCountF1 - 1)
            for (i in 0 until barCountF1) {
                drawRebarCircle(sLeft + 4f + i * stepF1, f1BottomY - 5f, rebar1Dia.toFloat(), secScale / 2f, bar1Color)
            }
            drawTextAnnotated("\u2460", sLeft + f1LenPx / 2f, f1BottomY + 10f, bar1Color, cfg.valueLabelTextSize, center = true, bold = true)

            val barCountF2 = rebar2Count.coerceIn(2, 10)
            val stepF2 = (f2LenPx - 8f) / (barCountF2 - 1)
            for (i in 0 until barCountF2) {
                drawRebarCircle(f2LeftX + 4f + i * stepF2, f1BottomY - 5f, rebar2Dia.toFloat(), secScale / 2f, bar2Color)
            }
            drawTextAnnotated("\u2461", f2LeftX + f2LenPx / 2f, f1BottomY + 10f, bar2Color, cfg.valueLabelTextSize, center = true, bold = true)

            // Strap beam reinforcement
            val barCountStrap = strapCount.coerceIn(2, 8)
            val stepStrap = (distPx - 8f) / (barCountStrap - 1)
            for (i in 0 until barCountStrap) {
                drawRebarCircle(strapLeft + 4f + i * stepStrap, strapBottomY - 4f, strapDia.toFloat(), secScale / 2f, barStrapColor)
            }
            drawTextAnnotated("\u2462", (strapLeft + strapRight) / 2f, strapBottomY + 10f, barStrapColor, cfg.valueLabelTextSize, center = true, bold = true)

            // Stirrup indicators in strap
            val stirStep = distPx / 6f
            for (s in 0..5) {
                val sx = strapLeft + s * stirStep + stirStep / 2f
                drawLine(stirrupColor, Offset(sx, strapTopY + 2f), Offset(sx, strapBottomY - 2f), strokeWidth = 0.7f)
            }

            // Thickness dimensions
            drawVerticalDimension(f1TopY, f1BottomY, sRight, "t=${maxThick.toInt()}", dimColor, cfg.annotationTextSmall, offset = 14f)
            drawVerticalDimension(strapTopY, strapBottomY, sRight, "ts=${safeStrapT.toInt()}", dimColor, cfg.annotationTextSmall, offset = 30f)

            // Cover
            val coverPx = 4f
            drawLine(C.SafeGreen, Offset(sLeft + 10f, f1BottomY), Offset(sLeft + 10f, f1BottomY - coverPx), strokeWidth = 1f)
            drawTextAnnotated("c=${cover.toInt()}", sLeft + 24f, f1BottomY - coverPx / 2f + 3f, C.SafeGreen, cfg.annotationTextSmall)
        } // end section view

        // ── Section separator: section → detail ───────────────
        if (viewMode == 0) {
            drawSectionSeparator(detTop - h * 0.01f, w, "STRAP BEAM DETAIL", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  STRAP BEAM DETAIL (cross-section)
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            if (detailH > h * 0.05f) {
                val detLeft = margin + 90f
                val detRight = w - margin
                val detW = detRight - detLeft

                drawTextAnnotated("STRAP BEAM X-SECTION", detLeft, detTop + 2f, C.ExtensionGray, cfg.valueLabelTextSize, bold = true)

                val beamW = min(detW * 0.3f, detailH * 0.7f)
                val beamH = beamW * (safeStrapT / safeStrapW).toFloat().coerceIn(0.5f, 2.5f)
                val beamLeft = detLeft + detW * 0.2f
                val beamTopY = detTop + (detailH - beamH) / 2f + 6f

                // Beam section
                drawRect(color = strapFill, topLeft = Offset(beamLeft, beamTopY), size = Size(beamW, beamH))
                drawHatchPattern(beamLeft, beamTopY, beamW, beamH, spacing = 6f, angleDeg = 45f, color = hatchColor)
                drawRect(color = concreteStroke, topLeft = Offset(beamLeft, beamTopY), size = Size(beamW, beamH), style = Stroke(width = 2f))

                // Bottom bars
                val barStepX = beamW / (strapCount + 1).toFloat()
                for (i in 1..strapCount) {
                    val bx = beamLeft + i * barStepX
                    drawRebarCircle(bx, beamTopY + beamH - 5f, strapDia.toFloat(), 0.8f, barStrapColor)
                }
                drawTextAnnotated("\u2462", beamLeft + beamW + 14f, beamTopY + beamH - 5f, barStrapColor, cfg.dimTextSize, bold = true)

                // Top bars (same diameter, 2 bars)
                for (i in listOf(beamW * 0.33f, beamW * 0.67f)) {
                    drawRebarCircle(beamLeft + i, beamTopY + 5f, strapDia.toFloat(), 0.8f, barStrapColor)
                }

                // Stirrups
                drawLine(stirrupColor, Offset(beamLeft + 3f, beamTopY + 3f), Offset(beamLeft + 3f, beamTopY + beamH - 3f), strokeWidth = 1f)
                drawLine(stirrupColor, Offset(beamLeft + beamW - 3f, beamTopY + 3f), Offset(beamLeft + beamW - 3f, beamTopY + beamH - 3f), strokeWidth = 1f)
                drawLine(stirrupColor, Offset(beamLeft + 3f, beamTopY + 3f), Offset(beamLeft + beamW - 3f, beamTopY + 3f), strokeWidth = 0.7f)
                drawLine(stirrupColor, Offset(beamLeft + 3f, beamTopY + beamH - 3f), Offset(beamLeft + beamW - 3f, beamTopY + beamH - 3f), strokeWidth = 0.7f)

                // Dimensions
                drawHorizontalDimension(beamLeft, beamLeft + beamW, beamTopY, "b=${safeStrapW.toInt()}", dimColor, cfg.valueLabelTextSize, offset = -14f)
                drawVerticalDimension(beamTopY, beamTopY + beamH, beamLeft + beamW, "h=${safeStrapT.toInt()}", dimColor, cfg.valueLabelTextSize, offset = 14f)

                // Cover
                val cpx = 4f
                drawLine(C.SafeGreen, Offset(beamLeft + beamW - 2f, beamTopY + beamH), Offset(beamLeft + beamW - 2f, beamTopY + beamH - cpx), strokeWidth = 1f)
                drawTextAnnotated("c", beamLeft + beamW + 2f, beamTopY + beamH - cpx / 2f + 3f, C.SafeGreen, cfg.annotationTextSmall)
            }
        } // end strap beam detail

        // ── Section separator: detail → table ─────────────────
        if (viewMode == 0) {
            drawSectionSeparator(tblTop - h * 0.01f, w, "REINFORCEMENT TABLE", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val tblLeft = margin
            val tblWidth = w - 2 * margin

            val headers = listOf("Mark", "Location", "Dia (mm)", "Count", "Length (mm)", "Notes")
            val colWidths = listOf(
                tblWidth * 0.08f, tblWidth * 0.20f, tblWidth * 0.12f,
                tblWidth * 0.12f, tblWidth * 0.20f, tblWidth * cfg.sectionHeightFraction
            )
            val rows = listOf(
                listOf("\u2460", "Footing 1 bot", rebar1Dia.toInt().toString(), rebar1Count.toString(), f1LMm.toInt().toString(), "Long. direction"),
                listOf("\u2461", "Footing 2 bot", rebar2Dia.toInt().toString(), rebar2Count.toString(), f2LMm.toInt().toString(), "Long. direction"),
                listOf("\u2462", "Strap beam", strapDia.toInt().toString(), strapCount.toString(), distMm.toInt().toString(), "Bottom + 2 top")
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

        // ── Responsive title block ─────────────────────────────
        drawResponsiveTitleBlock(
            x = w - cfg.titleBlockWidth - cfg.margin * 0.3f,
            y = h - cfg.titleBlockHeight - cfg.margin * 0.3f,
            cfg = cfg,
            drawingTitle = "Strap Footing Detail",
            designCode = codeLabel
        )
    }
}
