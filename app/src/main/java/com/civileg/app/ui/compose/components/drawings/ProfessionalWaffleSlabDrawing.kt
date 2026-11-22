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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import kotlin.math.*

// ============================================================================
// PROFESSIONAL WAFFLE (TWO-WAY RIBBED) SLAB ENGINEERING DRAWING
// v2.0 — Comprehensive Multi-View Two-Way Ribbed Slab Detailing
// Codes: ECP 203-2020 Ch. 6, ACI 318-19 Ch. 8, SBC 304-2018
// ============================================================================

/**
 * Professional Waffle Slab Engineering Drawing
 *
 * Renders:
 *  - viewMode 0 (All):       Plan (2D Coffer Grid + Solid Heads) +
 *                            Section A-A (T-Ribs with Coffer Voids & Stirrups) +
 *                            Solid Head Detail + BBS Schedule Table
 *  - viewMode 1 (Plan View): Full 2-way coffer grid with recessed square pans,
 *                            solid heads around columns, column outlines, punching shear
 *                            critical perimeter ($b_o$), and grid dimensions
 *  - viewMode 2 (Section A-A): Transverse cross-section through multiple ribs showing
 *                            topping flange ($t_f$), rib web ($b_w$), tapered coffer pans,
 *                            bottom bars, top mesh, stirrups, cover
 *  - viewMode 3 (Detail):    Solid Head & Column Connection Detail with punching shear
 *                            perimeter ($d/2$), solid head flexure steel, column stub
 *  - viewMode 4 (BBS Table): Complete Bar Bending Schedule with bar marks, shapes,
 *                            diameters, counts, lengths, and total steel weight
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
    // ── InputGuard ─────────────────────────────────────────────────────────
    InputGuard.positive("lx", lx)
    InputGuard.positive("ly", ly)
    InputGuard.positive("ribSpacing", ribSpacing)
    InputGuard.positive("ribWidth", ribWidth)
    InputGuard.positive("ribHeight", ribHeight)
    InputGuard.positive("toppingThickness", toppingThickness)
    InputGuard.positive("solidHeadSize", solidHeadSize)
    InputGuard.positive("columnWidth", columnWidth)
    InputGuard.positive("cover", cover)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxWidth().fillMaxSize()) {
        // Too small to draw truthfully: omit rather than distort geometry.
        if (isDegenerateForDrawing()) return@Canvas
        val W = size.width
        val H = size.height

        // ── Safe Dimensions (mm) ──────────────────────────────────────────
        val safeLx = (if (lx < 50.0) lx * 1000.0 else lx).coerceAtLeast(1000.0)
        val safeLy = (if (ly < 50.0) ly * 1000.0 else ly).coerceAtLeast(1000.0)
        val safeRibSpacing = ribSpacing.coerceAtLeast(300.0)
        val safeRibWidth = ribWidth.coerceAtLeast(80.0)
        val safeRibHeight = ribHeight.coerceAtLeast(150.0)
        val safeTopping = toppingThickness.coerceAtLeast(40.0)
        val safeSolidHead = solidHeadSize.coerceInMin(400.0, min(safeLx, safeLy) * 0.45)
        val safeColW = columnWidth.coerceInMin(200.0, safeSolidHead * 0.7)
        val totalDepth = safeRibHeight + safeTopping

        // ── Color Palette ──────────────────────────────────────────────────
        val C = DrawingColorDefaults
        val concreteFill       = Color(0xFF242438)
        val concreteStroke     = Color(0xFF78909C)
        val toppingFill        = Color(0xFF37474F)
        val ribFill            = Color(0xFF455A64)
        val cofferVoidFill     = Color(0xFF161622) // dark recessed coffers
        val cofferBorder       = Color(0xFF546E7A)
        val solidHeadFill      = Color(0xFF424242)
        val colFill            = Color(0xFF546E7A)
        val colStroke          = Color(0xFF263238)
        val barBottomColor     = C.RebarBlue
        val barTopColor        = C.SecondaryRed
        val barHeadColor       = C.SafeGreen
        val stirrupColor       = C.StirrupPurple
        val shearPerimColor    = C.WarningOrange
        val dimColor           = C.DimensionWhite
        val textColor          = C.DimensionWhite
        val hatchColor         = C.HatchColor.copy(alpha = 0.35f)

        val margin = cfg.margin
        val headerH = cfg.headerHeight + 10f

        // ── Header band ────────────────────────────────────────────────────
        drawWaffleHeader(W, cfg, codeLabel, textColor)

        // ── Route by viewMode ──────────────────────────────────────────────
        when (viewMode) {
            1 -> {
                // Plan View Only
                drawZoneLabel(margin, headerH + 6f, "WAFFLE PLAN (COFFER GRID & SOLID HEADS)", cfg)
                drawWafflePlanView(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeLx, safeLy, safeRibSpacing, safeRibWidth, safeSolidHead, safeColW,
                    totalDepth,
                    concreteFill, concreteStroke, toppingFill, ribFill, cofferVoidFill,
                    cofferBorder, solidHeadFill, colFill, colStroke, shearPerimColor,
                    dimColor, textColor, cfg
                )
            }
            2 -> {
                // Transverse Section A-A (T-Ribs with Coffer Pans)
                drawZoneLabel(margin, headerH + 6f, "SECTION A-A: TWO-WAY T-RIBS & COFFERS", cfg)
                drawWaffleSectionAA(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeRibSpacing, safeRibWidth, totalDepth, safeTopping, safeRibHeight,
                    ribBottomDia, ribBottomCount, ribTopDia, ribTopCount,
                    stirrupColor, cover,
                    toppingFill, ribFill, cofferVoidFill, cofferBorder, concreteStroke,
                    barBottomColor, barTopColor, hatchColor, dimColor, textColor, cfg
                )
            }
            3 -> {
                // Solid Head & Punching Shear Detail
                drawZoneLabel(margin, headerH + 6f, "SOLID HEAD & PUNCHING SHEAR DETAIL", cfg)
                drawWaffleSolidHeadDetail(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeSolidHead, safeColW, totalDepth, safeTopping,
                    headBottomDia, headBottomCount,
                    solidHeadFill, colFill, colStroke, concreteStroke,
                    barHeadColor, shearPerimColor, hatchColor, dimColor, textColor, cfg
                )
            }
            4 -> {
                // BBS Schedule Table View
                drawZoneLabel(margin, headerH + 6f, "BAR BENDING SCHEDULE (BBS)", cfg)
                drawWaffleBBSTable(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeLx, safeLy, safeRibSpacing, safeSolidHead,
                    ribBottomDia, ribBottomCount, ribTopDia, ribTopCount,
                    headBottomDia, headBottomCount,
                    barBottomColor, barTopColor, barHeadColor,
                    dimColor, textColor, cfg
                )
            }
            else -> {
                // viewMode 0 (All): Multi-view layout
                val topH = (H - headerH - margin) * 0.46f
                val botH = (H - headerH - margin) * 0.44f
                val topY = headerH + 10f
                val botY = topY + topH + 14f
                val halfW = (W - 3 * margin) / 2f

                drawSectionSeparator(botY - 7f, W, "SECTIONS & SOLID HEAD DETAIL", cfg)
                drawCombinedSectionLabel(H * 0.015f, W, listOf("Plan", "Sec A-A", "Solid Head", "BBS"), cfg)

                // Top-Left: Plan View
                drawZoneLabel(margin, topY + 2f, "PLAN", cfg)
                drawWafflePlanView(
                    margin, topY + 12f, halfW, topH - 16f,
                    safeLx, safeLy, safeRibSpacing, safeRibWidth, safeSolidHead, safeColW,
                    totalDepth,
                    concreteFill, concreteStroke, toppingFill, ribFill, cofferVoidFill,
                    cofferBorder, solidHeadFill, colFill, colStroke, shearPerimColor,
                    dimColor, textColor, cfg
                )

                // Top-Right: Section A-A
                val secLeft = margin + halfW + margin
                drawZoneLabel(secLeft, topY + 2f, "SECTION A-A (T-RIBS)", cfg)
                drawWaffleSectionAA(
                    secLeft, topY + 12f, halfW, topH - 16f,
                    safeRibSpacing, safeRibWidth, totalDepth, safeTopping, safeRibHeight,
                    ribBottomDia, ribBottomCount, ribTopDia, ribTopCount,
                    stirrupColor, cover,
                    toppingFill, ribFill, cofferVoidFill, cofferBorder, concreteStroke,
                    barBottomColor, barTopColor, hatchColor, dimColor, textColor, cfg
                )

                // Bottom-Left: Solid Head Detail
                drawZoneLabel(margin, botY + 2f, "SOLID HEAD", cfg)
                drawWaffleSolidHeadDetail(
                    margin, botY + 12f, halfW, botH - 16f,
                    safeSolidHead, safeColW, totalDepth, safeTopping,
                    headBottomDia, headBottomCount,
                    solidHeadFill, colFill, colStroke, concreteStroke,
                    barHeadColor, shearPerimColor, hatchColor, dimColor, textColor, cfg
                )

                // Bottom-Right: BBS Schedule Table
                val tblLeft = margin + halfW + margin
                drawZoneLabel(tblLeft, botY + 2f, "BBS SCHEDULE", cfg)
                drawWaffleBBSTable(
                    tblLeft, botY + 12f, halfW, botH - 16f,
                    safeLx, safeLy, safeRibSpacing, safeSolidHead,
                    ribBottomDia, ribBottomCount, ribTopDia, ribTopCount,
                    headBottomDia, headBottomCount,
                    barBottomColor, barTopColor, barHeadColor,
                    dimColor, textColor, cfg
                )
            }
        }

        // ── Responsive Title Block & Annotations ───────────────────────────
        drawSheetAnnotations(
            x = W - cfg.titleBlockWidth - margin * 0.3f,
            y = H - cfg.titleBlockHeight - margin * 0.3f,
            cfg = cfg,
            summary = resultSummary
        )
        drawResponsiveTitleBlock(
            x = W - cfg.titleBlockWidth - margin * 0.3f,
            y = H - cfg.titleBlockHeight - margin * 0.3f,
            cfg = cfg,
            drawingTitle = "Waffle Slab Detail",
            designCode = codeLabel
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

private fun DrawScope.drawWaffleHeader(
    W: Float, cfg: DrawingDimensionsConfig,
    codeLabel: String, textColor: Color
) {
    val hBg = Color(0xFF263238)
    drawRect(color = hBg, topLeft = Offset(0f, 0f), size = Size(W, cfg.headerHeight))
    drawTextAnnotated(
        "WAFFLE (TWO-WAY RIBBED) SLAB STRUCTURAL DETAIL  |  $codeLabel",
        W / 2f, cfg.headerHeight * 0.65f,
        textColor, cfg.headerTextSize, center = true, bold = true
    )
    drawTextAnnotated(
        "ECP 203 §6.3.5 (Solid Heads & Waffle Coffer Pans) | ACI 318 §8.13 | SBC 304",
        W / 2f, cfg.headerHeight * 0.90f,
        textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.85f, center = true
    )
}

// ============================================================================
// 2. PLAN VIEW: COFFER PANS, INTERSECTING RIBS, SOLID HEADS
// ============================================================================

private fun DrawScope.drawWafflePlanView(
    left: Float, top: Float, width: Float, height: Float,
    safeLx: Double, safeLy: Double, safeRibSpacing: Double, safeRibWidth: Double,
    safeSolidHead: Double, safeColW: Double, totalDepthMm: Double,
    concreteFill: Color, concreteStroke: Color, toppingFill: Color, ribFill: Color,
    cofferVoidFill: Color, cofferBorder: Color, solidHeadFill: Color, colFill: Color,
    colStroke: Color, shearPerimColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val pad = 35f
    val avW = width - 2 * pad
    val avH = height - 2 * pad

    val scaleX = (avW / safeLx.toFloat()).coerceAtLeast(0.01f)
    val scaleY = (avH / safeLy.toFloat()).coerceAtLeast(0.01f)
    val scale = min(scaleX, scaleY) * 0.92f

    val drawLx = (safeLx * scale).toFloat()
    val drawLy = (safeLy * scale).toFloat()

    val pX = left + pad + (avW - drawLx) / 2f
    val pY = top + pad + (avH - drawLy) / 2f

    // Slab Base
    drawRect(color = concreteFill, topLeft = Offset(pX, pY), size = Size(drawLx, drawLy))

    // ── Two-Way Coffer Grid ───────────────────────────────────────────────
    val numPansX = ((safeLx / safeRibSpacing).toInt()).coerceAtLeast(3)
    val numPansY = ((safeLy / safeRibSpacing).toInt()).coerceAtLeast(3)
    val panSpacingX = drawLx / numPansX.toFloat()
    val panSpacingY = drawLy / numPansY.toFloat()
    val ribWPx = (safeRibWidth * scale).toFloat().coerceIn(3f, 15f)

    // Draw individual coffer pans (square recesses with double borders for 3D depth)
    for (i in 0 until numPansX) {
        for (j in 0 until numPansY) {
            val panL = pX + i * panSpacingX + ribWPx / 2f
            val panT = pY + j * panSpacingY + ribWPx / 2f
            val panW = panSpacingX - ribWPx
            val panH = panSpacingY - ribWPx

            if (panW > 6f && panH > 6f) {
                // Outer coffer slope
                drawRect(color = ribFill.copy(alpha = 0.5f), topLeft = Offset(panL, panT), size = Size(panW, panH))
                // Inner recessed void
                val inset = (min(panW, panH) * 0.18f).coerceAtLeast(2f)
                drawRect(color = cofferVoidFill,
                    topLeft = Offset(panL + inset, panT + inset),
                    size = Size(panW - 2 * inset, panH - 2 * inset))
                drawRect(color = cofferBorder,
                    topLeft = Offset(panL, panT), size = Size(panW, panH), style = Stroke(0.6f))
            }
        }
    }

    // ── Solid Heads around Columns (Corners + Center) ─────────────────────
    val headSizePx = (safeSolidHead * scale).toFloat().coerceInMin(24f, drawLx * 0.38f)
    val colWPx = (safeColW * scale).toFloat().coerceInMin(12f, headSizePx * 0.55f)

    val headPositions = listOf(
        Offset(pX, pY),                                                 // Top-Left
        Offset(pX + drawLx - headSizePx, pY),                          // Top-Right
        Offset(pX, pY + drawLy - headSizePx),                          // Bottom-Left
        Offset(pX + drawLx - headSizePx, pY + drawLy - headSizePx),    // Bottom-Right
        Offset(pX + (drawLx - headSizePx) / 2f, pY + (drawLy - headSizePx) / 2f) // Center
    )

    headPositions.forEach { hPos ->
        // Solid Head concrete
        drawRect(color = solidHeadFill, topLeft = hPos, size = Size(headSizePx, headSizePx))
        drawHatchPattern(hPos.x, hPos.y, headSizePx, headSizePx, cfg.hatchSpacing * 0.8f, 45f, concreteStroke.copy(alpha = 0.4f))
        drawRect(color = concreteStroke, topLeft = hPos, size = Size(headSizePx, headSizePx), style = Stroke(1.5f))

        // Column within solid head
        val cX = hPos.x + (headSizePx - colWPx) / 2f
        val cY = hPos.y + (headSizePx - colWPx) / 2f
        drawRect(color = colFill, topLeft = Offset(cX, cY), size = Size(colWPx, colWPx))
        drawRect(color = colStroke, topLeft = Offset(cX, cY), size = Size(colWPx, colWPx), style = Stroke(1.5f))

        // Punching critical perimeter bo (dashed orange line)
        val dPx = ((totalDepthMm * 0.5) * scale).toFloat().coerceIn(4f, 15f)
        drawRect(color = shearPerimColor,
            topLeft = Offset(cX - dPx, cY - dPx),
            size = Size(colWPx + 2 * dPx, colWPx + 2 * dPx),
            style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 2f), 0f)))
    }

    // Slab border
    drawRect(color = concreteStroke, topLeft = Offset(pX, pY), size = Size(drawLx, drawLy), style = Stroke(2.5f))

    // ── Dimensions ────────────────────────────────────────────────────────
    drawHorizontalDimension(pX, pX + drawLx, pY + drawLy + 4f,
        "Lx = ${safeLx.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawVerticalDimension(pY, pY + drawLy, pX + drawLx + 6f,
        "Ly = ${safeLy.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 18f, cfg)
    drawHorizontalDimension(pX, pX + headSizePx, pY - 6f,
        "SH = ${safeSolidHead.toInt()} mm", dimColor.copy(alpha = 0.85f), cfg.annotationTextSmall * 0.8f, -14f, cfg)
}

// ============================================================================
// 3. SECTION A-A: TWO-WAY T-RIBS WITH COFFER VOIDS
// ============================================================================

private fun DrawScope.drawWaffleSectionAA(
    left: Float, top: Float, width: Float, height: Float,
    ribSpacingMm: Double, ribWidthMm: Double, totalDepthMm: Double, toppingThickMm: Double, ribHeightMm: Double,
    ribBottomDia: Double, ribBottomCount: Int, ribTopDia: Double, ribTopCount: Int,
    stirrupColor: Color, cover: Double,
    toppingFill: Color, ribFill: Color, cofferVoidFill: Color, cofferBorder: Color, concreteStroke: Color,
    barBottomColor: Color, barTopColor: Color, hatchColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val pad = 35f
    val avW = width - 2 * pad
    val numT = 3 // show 3 ribs

    val ribSpacingPx = (avW / numT.toFloat()).coerceIn(60f, 130f)
    val scale = (ribSpacingPx / ribSpacingMm.toFloat())

    val ribWPx = (ribWidthMm * scale).toFloat().coerceInMin(16f, ribSpacingPx * 0.40f)
    val toppingPx = (toppingThickMm * scale).toFloat().coerceIn(14f, 32f)
    val ribHPx = (ribHeightMm * scale).toFloat().coerceIn(28f, 75f)
    val totalHPx = toppingPx + ribHPx

    val secL = left + pad + (avW - numT * ribSpacingPx) / 2f
    val secY = top + (height - totalHPx) / 2f - 10f
    val toppingBotY = secY + toppingPx
    val secBotY = secY + totalHPx
    val totalDrawW = numT * ribSpacingPx

    // ── Continuous Topping Slab Flange ─────────────────────────────────────
    drawRect(color = toppingFill, topLeft = Offset(secL, secY), size = Size(totalDrawW, toppingPx))
    drawHatchPattern(secL, secY, totalDrawW, toppingPx, cfg.hatchSpacing, 45f, hatchColor)
    drawRect(color = concreteStroke, topLeft = Offset(secL, secY), size = Size(totalDrawW, toppingPx), style = Stroke(1.5f))

    // Topping reinforcement mesh
    val meshY = secY + toppingPx * 0.5f
    drawLine(barTopColor, Offset(secL + 10f, meshY), Offset(secL + totalDrawW - 10f, meshY), 1.5f)
    drawTextAnnotated("Mesh: Ø${ribTopDia.toInt()}/200", secL + totalDrawW / 2f, secY - 6f,
        barTopColor, cfg.annotationTextSmall * 0.8f, center = true, bold = true)

    // ── T-Ribs & Coffer Voids ─────────────────────────────────────────────
    for (m in 0 until numT) {
        val ribCenterX = secL + m * ribSpacingPx + ribSpacingPx / 2f
        val ribLeft = ribCenterX - ribWPx / 2f

        // Rib Web
        drawRect(color = ribFill, topLeft = Offset(ribLeft, toppingBotY), size = Size(ribWPx, ribHPx))
        drawHatchPattern(ribLeft, toppingBotY, ribWPx, ribHPx, cfg.hatchSpacing * 0.8f, 45f, hatchColor)
        drawRect(color = concreteStroke, topLeft = Offset(ribLeft, toppingBotY), size = Size(ribWPx, ribHPx), style = Stroke(1.5f))

        // Stirrups in Rib
        val sL = ribLeft + 3f
        val sW = ribWPx - 6f
        val sT = toppingBotY + 2f
        val sH = ribHPx - 4f
        drawRect(color = stirrupColor, topLeft = Offset(sL, sT), size = Size(sW, sH), style = Stroke(1.2f))

        // Bottom Rebar (Circles)
        val barR = (ribBottomDia * scale * 0.45).toFloat().coerceIn(3f, 7f)
        val barY = secBotY - 4f - barR
        val step = ribWPx / (ribBottomCount + 1).toFloat()
        for (b in 1..ribBottomCount) {
            val bx = ribLeft + b * step
            drawCircle(color = barBottomColor, radius = barR, center = Offset(bx, barY))
            drawCircle(color = Color.White, radius = barR * 0.3f, center = Offset(bx, barY))
        }

        // Tapered Coffer Void between this rib and next
        if (m < numT - 1) {
            val voidL = ribLeft + ribWPx + 3f
            val nextRibL = ribCenterX + ribSpacingPx - ribWPx / 2f
            val voidW = nextRibL - voidL - 3f

            // Draw tapered coffer pan (trapezoidal profile typical of waffle domes)
            val taper = voidW * 0.10f
            val panPath = Path().apply {
                moveTo(voidL + taper, toppingBotY + 2f)
                lineTo(voidL + voidW - taper, toppingBotY + 2f)
                lineTo(voidL + voidW, toppingBotY + ribHPx - 2f)
                lineTo(voidL, toppingBotY + ribHPx - 2f)
                close()
            }
            drawPath(panPath, color = cofferVoidFill)
            drawPath(panPath, color = cofferBorder, style = Stroke(1f))
            drawTextAnnotated("COFFER", voidL + voidW / 2f, toppingBotY + ribHPx / 2f,
                Color(0xFF90A4AE), cfg.annotationTextSmall * 0.75f, center = true)
        }
    }

    // Callout
    val midRibX = secL + ribSpacingPx / 2f
    drawTextAnnotated("${ribBottomCount}Ø${ribBottomDia.toInt()}", midRibX, secBotY + 12f,
        barBottomColor, cfg.annotationTextSmall, center = true, bold = true)

    // Dimensions
    drawVerticalDimension(secY, secBotY, secL + totalDrawW + 6f,
        "h=${totalDepthMm.toInt()}mm", dimColor, cfg.dimTextSize * 0.85f, 16f, cfg)
    drawVerticalDimension(secY, toppingBotY, secL + totalDrawW + 6f,
        "tf=${toppingThickMm.toInt()}mm", dimColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.8f, 32f, cfg)
    drawHorizontalDimension(midRibX - ribWPx / 2f, midRibX + ribWPx / 2f, secBotY + 4f,
        "bw=${ribWidthMm.toInt()}", dimColor, cfg.annotationTextSmall * 0.8f, 16f, cfg)
}

// ============================================================================
// 4. SOLID HEAD & PUNCHING SHEAR DETAIL
// ============================================================================

private fun DrawScope.drawWaffleSolidHeadDetail(
    left: Float, top: Float, width: Float, height: Float,
    safeSolidHead: Double, safeColW: Double, totalDepthMm: Double, toppingThickMm: Double,
    headBottomDia: Double, headBottomCount: Int,
    solidHeadFill: Color, colFill: Color, colStroke: Color, concreteStroke: Color,
    barHeadColor: Color, shearPerimColor: Color, hatchColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val pad = 35f
    val avW = width - 2 * pad
    val avH = height - 2 * pad

    val headSizePx = min(avW, avH) * 0.70f
    val colWPx = (headSizePx * (safeColW / safeSolidHead).toFloat()).coerceInMin(20f, headSizePx * 0.60f)

    val hLeft = left + pad + (avW - headSizePx) / 2f
    val hTop = top + pad + (avH - headSizePx) / 2f

    // ── Solid Head Slab Outline ───────────────────────────────────────────
    drawRect(color = solidHeadFill, topLeft = Offset(hLeft, hTop), size = Size(headSizePx, headSizePx))
    drawHatchPattern(hLeft, hTop, headSizePx, headSizePx, cfg.hatchSpacing * 0.9f, 45f, hatchColor)
    drawRect(color = concreteStroke, topLeft = Offset(hLeft, hTop), size = Size(headSizePx, headSizePx), style = Stroke(2f))

    // ── Column in Center ──────────────────────────────────────────────────
    val cX = hLeft + (headSizePx - colWPx) / 2f
    val cY = hTop + (headSizePx - colWPx) / 2f
    drawRect(color = colFill, topLeft = Offset(cX, cY), size = Size(colWPx, colWPx))
    drawRect(color = colStroke, topLeft = Offset(cX, cY), size = Size(colWPx, colWPx), style = Stroke(2f))

    // ── Punching Shear Critical Perimeter bo (at d/2) ─────────────────────
    val dPx = (colWPx * 0.40f).coerceIn(10f, 30f)
    drawRect(color = shearPerimColor,
        topLeft = Offset(cX - dPx, cY - dPx),
        size = Size(colWPx + 2 * dPx, colWPx + 2 * dPx),
        style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f)))
    drawTextAnnotated("Critical bo (d/2)", cX + colWPx / 2f, cY - dPx - 8f,
        shearPerimColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // ── Solid Head Flexure Reinforcement Grid (Top/Bottom) ────────────────
    val numBars = min(headBottomCount, 6).coerceAtLeast(3)
    val step = headSizePx / (numBars + 1).toFloat()
    for (i in 1..numBars) {
        val bx = hLeft + i * step
        // Vertical bars
        drawLine(barHeadColor.copy(alpha = 0.7f), Offset(bx, hTop + 6f), Offset(bx, hTop + headSizePx - 6f), 2f)
        // Horizontal bars
        val by = hTop + i * step
        drawLine(barHeadColor.copy(alpha = 0.7f), Offset(hLeft + 6f, by), Offset(hLeft + headSizePx - 6f, by), 2f)
    }
    drawTextAnnotated("Head Steel: ${headBottomCount}Ø${headBottomDia.toInt()} Mesh",
        hLeft + headSizePx / 2f, hTop + headSizePx + 16f,
        barHeadColor, cfg.annotationTextSmall, center = true, bold = true)

    // Dimensions
    drawHorizontalDimension(hLeft, hLeft + headSizePx, hTop - 6f,
        "SH = ${safeSolidHead.toInt()} mm", dimColor, cfg.annotationTextSmall, -12f, cfg)
}

// ============================================================================
// 5. BAR BENDING SCHEDULE (BBS) & DETAILING TABLE
// ============================================================================

private fun DrawScope.drawWaffleBBSTable(
    left: Float, top: Float, width: Float, height: Float,
    safeLxMm: Double, safeLyMm: Double, safeRibSpacingMm: Double, safeSolidHeadMm: Double,
    ribBottomDia: Double, ribBottomCount: Int, ribTopDia: Double, ribTopCount: Int,
    headBottomDia: Double, headBottomCount: Int,
    barBottomColor: Color, barTopColor: Color, barHeadColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val numRibsX = ((safeLyMm / safeRibSpacingMm).toInt() + 1).coerceAtLeast(3)
    val numRibsY = ((safeLxMm / safeRibSpacingMm).toInt() + 1).coerceAtLeast(3)

    // Bar cut lengths
    val ribBarLenXM = (safeLxMm + 400.0) / 1000.0
    val ribBarLenYM = (safeLyMm + 400.0) / 1000.0
    val headBarLenM = (safeSolidHeadMm + 300.0) / 1000.0

    // Counts
    val countBotX = ribBottomCount * numRibsX
    val countBotY = ribBottomCount * numRibsY
    val countHead = headBottomCount * 2 * 4 // 2 layers, 4 drop heads

    // Weights (kg = count * length * dia^2 * 0.00617)
    val wBotX = countBotX * ribBarLenXM * ribBottomDia * ribBottomDia * 0.00617
    val wBotY = countBotY * ribBarLenYM * ribBottomDia * ribBottomDia * 0.00617
    val wHead = countHead * headBarLenM * headBottomDia * headBottomDia * 0.00617
    val slabAreaM2 = (safeLxMm * safeLyMm) / 1e6
    val wTopping = slabAreaM2 * 2.22 // mesh
    val totalWeight = wBotX + wBotY + wHead + wTopping

    val headers = listOf("Mark", "Component", "Rebar Size", "Cut Length", "Total No.", "Weight (kg)")
    val rows = listOf(
        listOf("RB-X", "X-Rib Bottom", "${ribBottomCount}Ø${ribBottomDia.toInt()}", "${(ribBarLenXM * 1000).toInt()} mm", "$countBotX", "%.1f".format(wBotX)),
        listOf("RB-Y", "Y-Rib Bottom", "${ribBottomCount}Ø${ribBottomDia.toInt()}", "${(ribBarLenYM * 1000).toInt()} mm", "$countBotY", "%.1f".format(wBotY)),
        listOf("SH-B", "Solid Head", "${headBottomCount}Ø${headBottomDia.toInt()}", "${(headBarLenM * 1000).toInt()} mm", "$countHead", "%.1f".format(wHead)),
        listOf("MSH", "Topping Mesh", "Ø${ribTopDia.toInt()}/200", "Mesh Rolls", "Both Ways", "%.1f".format(wTopping))
    )

    val colFracs = listOf(0.10f, 0.24f, 0.22f, 0.16f, 0.14f, 0.14f)
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

    val sumY = top + cfg.tableHeaderHeight + cfg.tableRowHeight * rows.size + 14f
    drawTextAnnotated("Total Steel: ${String.format("%.1f", totalWeight)} kg  (≈ ${String.format("%.1f", totalWeight / slabAreaM2)} kg/m² for ${String.format("%.1f", slabAreaM2)} m² slab)",
        left + width / 2f, sumY,
        DrawingColorDefaults.SafeGreen, cfg.annotationTextSmall, center = true, bold = true)
}
