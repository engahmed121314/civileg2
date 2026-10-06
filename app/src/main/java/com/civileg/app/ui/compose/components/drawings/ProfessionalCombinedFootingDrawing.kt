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
 * Professional Combined Footing Engineering Drawing
 * Renders plan view (rectangular footing + two columns), section B-B
 * (longitudinal section with soil pressure trapezoid), soil pressure diagram,
 * and reinforcement table.
 *
 * ECP 203-2020: Chapter 10 — combined footings, trapezoidal soil pressure
 * ACI 318-19:  Chapter 13 — two-column combined footings
 */
@Composable
fun ProfessionalCombinedFootingDrawing(
    footingLength: Double,
    footingWidth: Double,
    footingThickness: Double,
    col1X: Double,
    col1Width: Double,
    col1Depth: Double,
    col2X: Double,
    col2Width: Double,
    col2Depth: Double,
    longBottomDia: Double,
    longBottomCount: Int,
    longTopDia: Double,
    longTopCount: Int,
    transBottomDia: Double,
    transBottomCount: Int,
    cover: Double,
    soilPressureMax: Double,
    soilPressureMin: Double,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard: validate key dimensions before drawing ─────────────────
    InputGuard.positive("footingLength", footingLength)
    InputGuard.positive("footingWidth", footingWidth)
    InputGuard.positive("footingThickness", footingThickness)
    InputGuard.positive("col1Width", col1Width)
    InputGuard.positive("col1Depth", col1Depth)
    InputGuard.positive("col2Width", col2Width)
    InputGuard.positive("col2Depth", col2Depth)
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
        val safeLen = footingLength.coerceAtLeast(1.0)
        val safeWid = footingWidth.coerceAtLeast(0.5)
        val safeThick = footingThickness.coerceAtLeast(300.0)
        val safeC1W = col1Width.coerceAtLeast(200.0)
        val safeC1D = col1Depth.coerceAtLeast(200.0)
        val safeC2W = col2Width.coerceAtLeast(200.0)
        val safeC2D = col2Depth.coerceAtLeast(200.0)

        // Normalize to mm
        val lenMm = if (safeLen < 50.0) safeLen * 1000.0 else safeLen
        val widMm = if (safeWid < 50.0) safeWid * 1000.0 else safeWid
        val thickMm = if (safeThick < 50.0) safeThick * 1000.0 else safeThick

        // ── Color Palette ──────────────────────────────────────────
        val C = DrawingColorDefaults
        val concreteFill = C.ConcreteFill
        val concreteStroke = C.ConcreteGray
        val colFill = Color(0xFF555555)
        val colStroke = Color(0xFF333333)
        val barLongColor = C.RebarBlue
        val barTransColor = C.TopRebarBlue
        val barTopColor = C.SafeGreen
        val dimColor = C.ExtensionGray
        val textColor = C.DimensionWhite
        val headerBg = Color(0x55333333)
        val tableHeaderBg = Color(0x55333333)
        val hatchColor = Color(0x55AAAAAA)
        val soilColor = C.SoilBrown
        val soilHatchColor = C.SoilBrown.copy(alpha = 0.7f)
        val pressureColor = C.UnsafeRed

        // ── Layout zones (viewMode-aware) ────────────────────────
        val margin = cfg.margin
        val planH = when (viewMode) { 1 -> h * 0.82f; 0 -> h * 0.33f; else -> h * 0.10f }
        val secH = when (viewMode) { 2 -> h * 0.45f; 0 -> h * cfg.sectionHeightFraction; else -> h * 0.10f }
        val pressH = when (viewMode) { 2 -> h * 0.20f; 0 -> h * 0.08f; else -> 0f }
        val tableH = when (viewMode) { 3 -> h * 0.88f; 0 -> h * cfg.tableHeightFraction; else -> h * 0.10f }

        val planTop = h * 0.05f
        val planBottom = planTop + planH
        val secTop = planBottom + h * 0.02f
        val secBottom = secTop + secH
        val pressTop = secBottom + h * 0.01f
        val pressBottom = pressTop + pressH
        val tblTop = if (viewMode == 0) pressBottom + h * 0.02f else secBottom + h * 0.02f

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, cfg.headerHeight))
        drawTextAnnotated(
            "COMBINED FOOTING DETAIL — $codeLabel",
            w / 2f, cfg.headerHeight * 0.65f, textColor, cfg.headerTextSize, center = true, bold = true
        )

        // ── Responsive section separators & zone labels ───────
        if (viewMode == 0) {
            drawCombinedSectionLabel(h * 0.015f, w, listOf("Plan", "Section", "Pressure", "Table"), cfg)
            drawSectionSeparator(planTop - h * 0.01f, w, "PLAN / SECTION", cfg)
        } else if (viewMode == 1) {
            drawZoneLabel(margin, planTop + 4f, "PLAN", cfg)
        } else if (viewMode == 2) {
            drawZoneLabel(margin, secTop + 4f, "SECTION", cfg)
        } else if (viewMode == 3) {
            drawZoneLabel(margin, tblTop + 4f, "TABLE", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW — Rectangular footing + two columns
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            val planLeft = margin + 50f
            val planRight = w - margin
            val planW = planRight - planLeft
            val planDrawH = planBottom - planTop

            val scaleX = planW / lenMm.toFloat()
            val scaleY = planDrawH / widMm.toFloat()
            val scale = min(scaleX, scaleY) * 0.90f
            val drawLen = lenMm.toFloat() * scale
            val drawWid = widMm.toFloat() * scale

            val fLeft = planLeft + (planW - drawLen) / 2f
            val fTop = planTop + (planDrawH - drawWid) / 2f
            val fRight = fLeft + drawLen
            val fBottom = fTop + drawWid
            val fCenterY = fTop + drawWid / 2f

            // Footing body
            drawRect(color = concreteFill, topLeft = Offset(fLeft, fTop), size = Size(drawLen, drawWid))
            drawHatchPattern(fLeft, fTop, drawLen, drawWid, spacing = 16f, angleDeg = 45f, color = hatchColor)

            // ── Longitudinal reinforcement (bottom, vertical lines) ──
            if (longBottomCount > 1) {
                val barLeft = fLeft + (cover * scale).toFloat()
                val barRight = fRight - (cover * scale).toFloat()
                val step = (barRight - barLeft) / (longBottomCount - 1)
                for (i in 0 until longBottomCount) {
                    val bx = barLeft + i * step
                    drawLine(barLongColor, Offset(bx, fTop + 4f), Offset(bx, fBottom - 4f), strokeWidth = 1.6f)
                }
            }
            drawTextAnnotated("\u2460", fRight + 14f, fCenterY, barLongColor, cfg.valueLabelTextSize, bold = true)

            // ── Transverse reinforcement (bottom, horizontal lines) ──
            if (transBottomCount > 1) {
                val barTopY = fTop + (cover * scale).toFloat()
                val barBottomY = fBottom - (cover * scale).toFloat()
                val step = (barBottomY - barTopY) / (transBottomCount - 1)
                for (i in 0 until transBottomCount) {
                    val by = barTopY + i * step
                    drawLine(barTransColor, Offset(fLeft + 4f, by), Offset(fRight - 4f, by), strokeWidth = 1.0f)
                }
            }
            drawTextAnnotated("\u2461", fLeft - 14f, fBottom + 12f, barTransColor, cfg.valueLabelTextSize, bold = true)

            // ── Two columns ──────────────────────────────────────────
            val c1Norm = if (lenMm > 0) (col1X / lenMm).toFloat().coerceIn(0.05f, 0.95f) else 0.2f
            val c2Norm = if (lenMm > 0) (col2X / lenMm).toFloat().coerceIn(0.05f, 0.95f) else 0.8f

            val c1DrawW = (safeC1W * scale).toFloat()
            val c1DrawD = (safeC1D * scale).toFloat()
            val c1Left = fLeft + c1Norm * drawLen - c1DrawW / 2f
            val c1Top = fCenterY - c1DrawD / 2f

            val c2DrawW = (safeC2W * scale).toFloat()
            val c2DrawD = (safeC2D * scale).toFloat()
            val c2Left = fLeft + c2Norm * drawLen - c2DrawW / 2f
            val c2Top = fCenterY - c2DrawD / 2f

            // Column 1
            drawRect(color = colFill, topLeft = Offset(c1Left, c1Top), size = Size(c1DrawW, c1DrawD))
            drawRect(color = colStroke, topLeft = Offset(c1Left, c1Top), size = Size(c1DrawW, c1DrawD), style = Stroke(width = 1.5f))
            drawHatchPattern(c1Left, c1Top, c1DrawW, c1DrawD, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C1", c1Left + c1DrawW / 2f, c1Top + c1DrawD / 2f + 3f, textColor, cfg.valueLabelTextSize, center = true, bold = true)

            // Column 2
            drawRect(color = colFill, topLeft = Offset(c2Left, c2Top), size = Size(c2DrawW, c2DrawD))
            drawRect(color = colStroke, topLeft = Offset(c2Left, c2Top), size = Size(c2DrawW, c2DrawD), style = Stroke(width = 1.5f))
            drawHatchPattern(c2Left, c2Top, c2DrawW, c2DrawD, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C2", c2Left + c2DrawW / 2f, c2Top + c2DrawD / 2f + 3f, textColor, cfg.valueLabelTextSize, center = true, bold = true)

            // Border
            drawRect(color = concreteStroke, topLeft = Offset(fLeft, fTop), size = Size(drawLen, drawWid), style = Stroke(width = 3f))

            // Dimensions
            drawHorizontalDimension(fLeft, fRight, fTop, "L=${lenMm.toInt()}", dimColor, cfg.dimTextSize, offset = -14f)
            drawVerticalDimension(fTop, fBottom, fLeft, "B=${widMm.toInt()}", dimColor, cfg.dimTextSize, offset = -14f)

            drawTextAnnotated("PLAN", fLeft + 20f, fBottom + 22f, C.ExtensionGray, cfg.dimTextSize, bold = true)

            // Section cut line
            if (viewMode == 0) {
                drawSectionCutLine(
                    x1 = fLeft + drawLen / 2f, y1 = fTop - 20f,
                    x2 = fLeft + drawLen / 2f, y2 = fBottom + 6f,
                    label = "B", color = C.SectionLine
                )
            }
        } // end plan view

        // ── Section separator: plan → section ─────────────────
        if (viewMode == 0) {
            drawSectionSeparator(secTop - h * 0.01f, w, "SECTION / PRESSURE", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  SECTION B-B — Longitudinal section through footing
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val secLeft = margin + 90f
            val secRight = w - margin
            val maxSecW = secRight - secLeft - 120f

            drawTextAnnotated("SECTION B-B", secLeft - 50f, secTop + 4f, C.ExtensionGray, cfg.dimTextSize, bold = true)

            val secScale = maxSecW / lenMm.toFloat()
            val secSpanPx = maxSecW
            val thickPx = (thickMm * secScale).toFloat().coerceIn(30f, 100f)
            val sLeft = secLeft + 60f
            val sTop = secTop + (secH - thickPx) / 2f + 8f
            val sBottom = sTop + thickPx
            val sRight = sLeft + secSpanPx

            // Soil below footing
            val soilBottom = min(sBottom + 20f, secTop + secH - 10f)
            val soilDepth = (soilBottom - sBottom).coerceAtLeast(0f)
            drawRect(color = soilColor.copy(alpha = 0.4f), topLeft = Offset(sLeft - 10f, sBottom), size = Size(secSpanPx + 20f, soilDepth))
            drawHatchPattern(sLeft - 10f, sBottom, secSpanPx + 20f, soilDepth, spacing = 8f, angleDeg = -45f, color = soilHatchColor)

            // Footing concrete
            drawRect(color = concreteFill, topLeft = Offset(sLeft, sTop), size = Size(secSpanPx, thickPx))
            drawHatchPattern(sLeft, sTop, secSpanPx, thickPx, spacing = 10f, angleDeg = 45f, color = hatchColor)

            // Column 1 above footing
            val c1xNorm = if (lenMm > 0) (col1X / lenMm).toFloat().coerceIn(0.05f, 0.95f) else 0.2f
            val c2xNorm = if (lenMm > 0) (col2X / lenMm).toFloat().coerceIn(0.05f, 0.95f) else 0.8f

            val c1Wpx = (safeC1W * secScale).toFloat().coerceIn(15f, secSpanPx * 0.15f)
            val c2Wpx = (safeC2W * secScale).toFloat().coerceIn(15f, secSpanPx * 0.15f)
            val colHpx = 45f

            // Column 1
            val c1Left = sLeft + c1xNorm * secSpanPx - c1Wpx / 2f
            drawRect(color = colFill, topLeft = Offset(c1Left, sTop - colHpx), size = Size(c1Wpx, colHpx))
            drawRect(color = colStroke, topLeft = Offset(c1Left, sTop - colHpx), size = Size(c1Wpx, colHpx), style = Stroke(width = 1.5f))
            drawHatchPattern(c1Left, sTop - colHpx, c1Wpx, colHpx, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C1", c1Left + c1Wpx / 2f, sTop - colHpx / 2f + 3f, textColor, cfg.annotationTextSmall, center = true)

            // Column 2
            val c2Left = sLeft + c2xNorm * secSpanPx - c2Wpx / 2f
            drawRect(color = colFill, topLeft = Offset(c2Left, sTop - colHpx), size = Size(c2Wpx, colHpx))
            drawRect(color = colStroke, topLeft = Offset(c2Left, sTop - colHpx), size = Size(c2Wpx, colHpx), style = Stroke(width = 1.5f))
            drawHatchPattern(c2Left, sTop - colHpx, c2Wpx, colHpx, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
            drawTextAnnotated("C2", c2Left + c2Wpx / 2f, sTop - colHpx / 2f + 3f, textColor, cfg.annotationTextSmall, center = true)

            // Bottom reinforcement
            val barCountSec = longBottomCount.coerceIn(3, 18)
            val barStepSec = (secSpanPx - 16f) / (barCountSec - 1)
            for (i in 0 until barCountSec) {
                val bx = sLeft + 8f + i * barStepSec
                drawRebarCircle(bx, sBottom - 5f, longBottomDia.toFloat(), secScale / 2f, barLongColor)
            }
            drawTextAnnotated("\u2460", sLeft + secSpanPx / 2f, sBottom + 12f, barLongColor, cfg.dimTextSize, center = true, bold = true)

            // Top reinforcement (if any)
            if (longTopCount > 0) {
                val topBarCount = longTopCount.coerceIn(3, 14)
                val topStep = (secSpanPx - 16f) / (topBarCount - 1)
                for (i in 0 until topBarCount) {
                    val bx = sLeft + 8f + i * topStep
                    drawRebarCircle(bx, sTop + 5f, longTopDia.toFloat(), secScale / 2f, barTopColor)
                }
                drawTextAnnotated("\u2462", sLeft + secSpanPx / 2f, sTop - 8f, barTopColor, cfg.dimTextSize, center = true, bold = true)
            }

            // Footing border
            drawRect(color = concreteStroke, topLeft = Offset(sLeft, sTop), size = Size(secSpanPx, thickPx), style = Stroke(width = 2.5f))

            // Thickness dimension
            drawVerticalDimension(sTop, sBottom, sRight, "t=${safeThick.toInt()}", dimColor, cfg.valueLabelTextSize, offset = 16f)

            // Cover
            val coverPx = (cover * secScale).toFloat().coerceIn(3f, 10f)
            drawLine(C.SafeGreen, Offset(sLeft + 15f, sBottom), Offset(sLeft + 15f, sBottom - coverPx), strokeWidth = 1f)
            drawTextAnnotated("c=${cover.toInt()}", sLeft + 30f, sBottom - coverPx / 2f + 3f, C.SafeGreen, cfg.annotationTextSmall)

            // ══════════════════════════════════════════════════════════
            //  SOIL PRESSURE DIAGRAM (Trapezoidal)
            // ══════════════════════════════════════════════════════════
            val prTop = pressTop
            val prBottom = pressBottom
            if (prBottom > prTop + 10f) {
                val prLeft = sLeft
                val prRight = sRight

                drawTextAnnotated("SOIL PRESSURE", prLeft, prTop - 2f, C.ExtensionGray, cfg.valueLabelTextSize, bold = true)

                val maxP = if (soilPressureMax > 0) soilPressureMax.toFloat() else 250f
                val minP = if (soilPressureMin > 0) soilPressureMin.toFloat() else 120f
                val pressureScaleVal = if (maxP > 0) (pressH - 12f) / maxP else 0f
                val maxBarH = maxP * pressureScaleVal
                val minBarH = minP * pressureScaleVal

                // Trapezoid
                val path = Path().apply {
                    moveTo(prLeft, prBottom)
                    lineTo(prLeft, prBottom - maxBarH)
                    lineTo(prRight, prBottom - minBarH)
                    lineTo(prRight, prBottom)
                    close()
                }
                drawPath(path, color = pressureColor.copy(alpha = 0.3f))
                drawPath(path, color = pressureColor, style = Stroke(width = 1.5f))

                drawTextAnnotated("q_max=${maxP.toInt()}", prLeft + 5f, prBottom - maxBarH - 4f, pressureColor, cfg.valueLabelTextSize)
                drawTextAnnotated("q_min=${minP.toInt()}", prRight - 5f, prBottom - minBarH - 4f, pressureColor, cfg.valueLabelTextSize, center = true)

                // Resultant
                val resultantX = prLeft + secSpanPx * 0.5f
                val resultantH = (maxP + minP) / 2f * pressureScaleVal
                drawLine(C.WarningOrange, Offset(resultantX, prBottom), Offset(resultantX, prBottom - resultantH), strokeWidth = 2f)
                drawLine(C.WarningOrange, Offset(resultantX, prBottom - resultantH), Offset(resultantX - 3f, prBottom - resultantH + 5f), strokeWidth = 2f)
                drawLine(C.WarningOrange, Offset(resultantX, prBottom - resultantH), Offset(resultantX + 3f, prBottom - resultantH + 5f), strokeWidth = 2f)
                drawTextAnnotated("R", resultantX, prBottom - resultantH - 6f, C.WarningOrange, cfg.dimTextSize, center = true, bold = true)
            }
        } // end section view

        // ── Section separator: section → table ────────────────
        if (viewMode == 0) {
            drawSectionSeparator(tblTop - h * 0.01f, w, "REINFORCEMENT TABLE", cfg)
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val tblLeft = margin
            val tblWidth = w - 2 * margin

            val longSpacing = if (longBottomCount > 1) (widMm / (longBottomCount - 1)).toInt() else widMm.toInt()
            val transSpacing = if (transBottomCount > 1) (lenMm / (transBottomCount - 1)).toInt() else lenMm.toInt()
            val topSpacing = if (longTopCount > 1) (widMm / (longTopCount - 1)).toInt() else widMm.toInt()

            val headers = listOf("Mark", "Direction", "Dia (mm)", "Count", "Spacing (mm)", "Length (mm)")
            val colWidths = listOf(
                tblWidth * 0.08f, tblWidth * cfg.tableHeightFraction, tblWidth * 0.14f,
                tblWidth * 0.14f, tblWidth * 0.20f, tblWidth * cfg.tableHeightFraction
            )
            val rows = buildList {
                add(listOf("\u2460", "Long. bottom", longBottomDia.toInt().toString(), longBottomCount.toString(), longSpacing.toString(), lenMm.toInt().toString()))
                add(listOf("\u2461", "Trans. bottom", transBottomDia.toInt().toString(), transBottomCount.toString(), transSpacing.toString(), widMm.toInt().toString()))
                if (longTopCount > 0) {
                    add(listOf("\u2462", "Long. top", longTopDia.toInt().toString(), longTopCount.toString(), topSpacing.toString(), lenMm.toInt().toString()))
                }
            }

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
            drawingTitle = "Combined Footing Detail",
            designCode = codeLabel
        )
    }
}
