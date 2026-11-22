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
// PROFESSIONAL HORDI (ONE-WAY RIBBED) SLAB ENGINEERING DRAWING
// v2.0 — Comprehensive Multi-View Structural Detailing
// Codes: ECP 203-2020 Ch. 6, ACI 318-19 Ch. 8, SBC 304-2018
// ============================================================================

/**
 * Professional Hordi (Ribbed) Slab Engineering Drawing
 *
 * Renders:
 *  - viewMode 0 (All):       Plan (Ribs + Solid Part + Cross Rib) +
 *                            Section A-A (Transverse T-Section + Blocks) +
 *                            Section B-B (Longitudinal Rib Profile) + BBS Table
 *  - viewMode 1 (Plan View): Slab plan showing support beams, solid parts at supports,
 *                            longitudinal ribs, hollow blocks, cross-rib (if L > 5m),
 *                            topping mesh, dimensions and rebar callouts
 *  - viewMode 2 (Section A-A): Transverse cross-section through multiple ribs showing
 *                            topping flange ($t_f$), rib web ($b_w$), block voids ($e$),
 *                            bottom bars, top hangers, stirrups, mesh wires, dimensions
 *  - viewMode 3 (Section B-B / Detail): Longitudinal section along a rib showing support
 *                            bearings, solid part ($b_s$), bottom continuous bars,
 *                            top negative moment bars ($0.25 L$), cross-rib detail
 *  - viewMode 4 (BBS Table): Full Bar Bending Schedule with bar marks, shapes,
 *                            diameters, counts, lengths, and total steel weight
 */
@Composable
fun ProfessionalHordiSlabDrawing(
    span: Double,
    ribWidth: Double,
    ribSpacing: Double,
    totalThickness: Double,
    toppingThickness: Double,
    ribBottomDia: Double,
    ribBottomCount: Int,
    stirrupDia: Double,
    stirrupSpacing: Double,
    cover: Double,
    // Expanded engineering parameters with safe defaults
    solidPartWidthMm: Double = 300.0,
    hasCrossRib: Boolean = false,
    crossRibWidthMm: Double = 150.0,
    toppingMeshDia: Double = 6.0,
    toppingMeshSpacing: Double = 200.0,
    topRebarDia: Double = 10.0,
    topRebarCount: Int = 2,
    slabWidthM: Double = 4.0,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard ─────────────────────────────────────────────────────────
    InputGuard.positive("span", span)
    InputGuard.positive("ribWidth", ribWidth)
    InputGuard.positive("ribSpacing", ribSpacing)
    InputGuard.positive("totalThickness", totalThickness)
    InputGuard.positive("toppingThickness", toppingThickness)
    InputGuard.positive("cover", cover)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxWidth().fillMaxSize()) {
        // Too small to draw truthfully: omit rather than distort geometry.
        if (isDegenerateForDrawing()) return@Canvas
        val W = size.width
        val H = size.height

        // ── Safe Dimensions (all in mm) ────────────────────────────────────
        val safeSpan = (if (span < 50.0) span * 1000.0 else span).coerceAtLeast(1000.0)
        val safeRibWidth = ribWidth.coerceAtLeast(80.0)
        val safeRibSpacing = ribSpacing.coerceAtLeast(300.0)
        val safeTotalThick = totalThickness.coerceAtLeast(150.0)
        val safeTopping = toppingThickness.coerceAtLeast(40.0)
        val ribHeight = (safeTotalThick - safeTopping).coerceAtLeast(100.0)
        val blockWidth = (safeRibSpacing - safeRibWidth).coerceAtLeast(200.0)
        val safeSolidPart = solidPartWidthMm.coerceInMin(200.0, safeSpan * 0.25)
        val needCrossRib = hasCrossRib || (safeSpan >= 5000.0) // ECP/ACI rule: cross-rib if L >= 5m

        // ── Color Palette ──────────────────────────────────────────────────
        val C = DrawingColorDefaults
        val concreteFill       = Color(0xFF2C2C3E)
        val concreteStroke     = Color(0xFF78909C)
        val toppingFill        = Color(0xFF37474F)
        val ribFill            = Color(0xFF455A64)
        val solidPartFill      = Color(0xFF37474F)
        val voidFill           = Color(0xFF1B1B26) // hollow blocks
        val blockBorder        = Color(0xFF546E7A)
        val barBottomColor     = C.RebarBlue
        val barTopColor        = C.SecondaryRed
        val stirrupColor       = C.StirrupPurple
        val meshColor          = C.TopRebarBlue
        val dimColor           = C.DimensionWhite
        val textColor          = C.DimensionWhite
        val hatchColor         = C.HatchColor.copy(alpha = 0.3f)
        val crossRibFill       = Color(0xFF3F51B5).copy(alpha = 0.35f)

        val margin = cfg.margin
        val headerH = cfg.headerHeight + 10f

        // ── Global header ──────────────────────────────────────────────────
        drawHordiHeader(W, cfg, codeLabel, textColor)

        // ── Route by viewMode ──────────────────────────────────────────────
        when (viewMode) {
            1 -> {
                // Plan View Only
                drawZoneLabel(margin, headerH + 6f, "SLAB PLAN (RIBS & HOLLOW BLOCKS)", cfg)
                drawHordiPlanView(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeSpan, slabWidthM * 1000.0, safeRibWidth, safeRibSpacing,
                    safeSolidPart, needCrossRib, crossRibWidthMm,
                    toppingMeshDia, toppingMeshSpacing,
                    ribBottomDia, ribBottomCount,
                    concreteFill, concreteStroke, toppingFill, ribFill, solidPartFill,
                    voidFill, blockBorder, barBottomColor, crossRibFill,
                    dimColor, textColor, cfg
                )
            }
            2 -> {
                // Transverse Section A-A (T-Beams across ribs)
                drawZoneLabel(margin, headerH + 6f, "SECTION A-A: TRANSVERSE T-RIBS & BLOCKS", cfg)
                drawHordiSectionAA(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeRibWidth, safeRibSpacing, safeTotalThick, safeTopping, ribHeight, blockWidth,
                    ribBottomDia, ribBottomCount, topRebarDia, topRebarCount,
                    stirrupDia, stirrupSpacing, toppingMeshDia, cover,
                    toppingFill, ribFill, voidFill, blockBorder, concreteStroke,
                    barBottomColor, barTopColor, stirrupColor, meshColor, hatchColor,
                    dimColor, textColor, cfg
                )
            }
            3 -> {
                // Longitudinal Section B-B along Rib
                drawZoneLabel(margin, headerH + 6f, "SECTION B-B: LONGITUDINAL RIB PROFILE", cfg)
                drawHordiSectionBB(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeSpan, safeTotalThick, safeTopping, safeSolidPart, needCrossRib, crossRibWidthMm,
                    ribBottomDia, ribBottomCount, topRebarDia, topRebarCount,
                    stirrupDia, stirrupSpacing, cover,
                    toppingFill, ribFill, solidPartFill, voidFill, concreteStroke,
                    barBottomColor, barTopColor, stirrupColor, crossRibFill, hatchColor,
                    dimColor, textColor, cfg, designCode
                )
            }
            4 -> {
                // BBS Table View
                drawZoneLabel(margin, headerH + 6f, "BAR BENDING SCHEDULE (BBS)", cfg)
                drawHordiBBSTable(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeSpan, slabWidthM * 1000.0, safeRibSpacing, safeTotalThick, safeRibWidth,
                    ribBottomDia, ribBottomCount, topRebarDia, topRebarCount,
                    stirrupDia, stirrupSpacing, toppingMeshDia, toppingMeshSpacing,
                    barBottomColor, barTopColor, stirrupColor, meshColor,
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

                drawSectionSeparator(botY - 7f, W, "SECTIONS & BBS REINFORCEMENT SCHEDULE", cfg)
                drawCombinedSectionLabel(H * 0.015f, W, listOf("Plan", "Sec A-A", "Sec B-B", "BBS"), cfg)

                // Top-Left: Plan View
                drawZoneLabel(margin, topY + 2f, "PLAN", cfg)
                drawHordiPlanView(
                    margin, topY + 12f, halfW, topH - 16f,
                    safeSpan, slabWidthM * 1000.0, safeRibWidth, safeRibSpacing,
                    safeSolidPart, needCrossRib, crossRibWidthMm,
                    toppingMeshDia, toppingMeshSpacing,
                    ribBottomDia, ribBottomCount,
                    concreteFill, concreteStroke, toppingFill, ribFill, solidPartFill,
                    voidFill, blockBorder, barBottomColor, crossRibFill,
                    dimColor, textColor, cfg
                )

                // Top-Right: Section A-A (T-Ribs)
                val secLeft = margin + halfW + margin
                drawZoneLabel(secLeft, topY + 2f, "SECTION A-A (T-RIBS)", cfg)
                drawHordiSectionAA(
                    secLeft, topY + 12f, halfW, topH - 16f,
                    safeRibWidth, safeRibSpacing, safeTotalThick, safeTopping, ribHeight, blockWidth,
                    ribBottomDia, ribBottomCount, topRebarDia, topRebarCount,
                    stirrupDia, stirrupSpacing, toppingMeshDia, cover,
                    toppingFill, ribFill, voidFill, blockBorder, concreteStroke,
                    barBottomColor, barTopColor, stirrupColor, meshColor, hatchColor,
                    dimColor, textColor, cfg
                )

                // Bottom-Left: Section B-B (Longitudinal Profile)
                drawZoneLabel(margin, botY + 2f, "SECTION B-B (LONGITUDINAL)", cfg)
                drawHordiSectionBB(
                    margin, botY + 12f, halfW, botH - 16f,
                    safeSpan, safeTotalThick, safeTopping, safeSolidPart, needCrossRib, crossRibWidthMm,
                    ribBottomDia, ribBottomCount, topRebarDia, topRebarCount,
                    stirrupDia, stirrupSpacing, cover,
                    toppingFill, ribFill, solidPartFill, voidFill, concreteStroke,
                    barBottomColor, barTopColor, stirrupColor, crossRibFill, hatchColor,
                    dimColor, textColor, cfg, designCode
                )

                // Bottom-Right: BBS Schedule Table
                val tblLeft = margin + halfW + margin
                drawZoneLabel(tblLeft, botY + 2f, "BBS SCHEDULE", cfg)
                drawHordiBBSTable(
                    tblLeft, botY + 12f, halfW, botH - 16f,
                    safeSpan, slabWidthM * 1000.0, safeRibSpacing, safeTotalThick, safeRibWidth,
                    ribBottomDia, ribBottomCount, topRebarDia, topRebarCount,
                    stirrupDia, stirrupSpacing, toppingMeshDia, toppingMeshSpacing,
                    barBottomColor, barTopColor, stirrupColor, meshColor,
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
            drawingTitle = "Hordi Ribbed Slab",
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

private fun DrawScope.drawHordiHeader(
    W: Float, cfg: DrawingDimensionsConfig,
    codeLabel: String, textColor: Color
) {
    val hBg = Color(0xFF263238)
    drawRect(color = hBg, topLeft = Offset(0f, 0f), size = Size(W, cfg.headerHeight))
    drawTextAnnotated(
        "HORDI (ONE-WAY RIBBED) SLAB STRUCTURAL DETAIL  |  $codeLabel",
        W / 2f, cfg.headerHeight * 0.65f,
        textColor, cfg.headerTextSize, center = true, bold = true
    )
    drawTextAnnotated(
        "ECP 203 §6.3 | ACI 318 §8.13 (Joist Construction) | SBC 304 §8",
        W / 2f, cfg.headerHeight * 0.90f,
        textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.85f, center = true
    )
}

// ============================================================================
// 2. PLAN VIEW: RIBS, SOLID PARTS, BLOCKS, CROSS RIB
// ============================================================================

private fun DrawScope.drawHordiPlanView(
    left: Float, top: Float, width: Float, height: Float,
    safeSpanMm: Double, safeWidthMm: Double, ribWidthMm: Double, ribSpacingMm: Double,
    solidPartMm: Double, needCrossRib: Boolean, crossRibWidthMm: Double,
    toppingMeshDia: Double, toppingMeshSpacing: Double,
    ribBottomDia: Double, ribBottomCount: Int,
    concreteFill: Color, concreteStroke: Color, toppingFill: Color, ribFill: Color, solidPartFill: Color,
    voidFill: Color, blockBorder: Color, barBottomColor: Color, crossRibFill: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val pad = 35f
    val avW = width - 2 * pad
    val avH = height - 2 * pad

    val scaleX = (avW / safeSpanMm.toFloat()).coerceAtLeast(0.01f)
    val scaleY = (avH / safeWidthMm.toFloat()).coerceAtLeast(0.01f)
    val scale = min(scaleX, scaleY) * 0.92f

    val drawSpan = (safeSpanMm * scale).toFloat()
    val drawWidth = (safeWidthMm * scale).toFloat().coerceInMin(avH * 0.45f, avH)

    val pX = left + pad + (avW - drawSpan) / 2f
    val pY = top + pad + (avH - drawWidth) / 2f
    val solidPartPx = (solidPartMm * scale).toFloat().coerceInMin(20f, drawSpan * 0.25f)

    // Base slab outline (concrete backing)
    drawRect(color = concreteFill, topLeft = Offset(pX, pY), size = Size(drawSpan, drawWidth))

    // ── Solid Parts at Left & Right Supports (no blocks, solid concrete for shear) ──
    drawRect(color = solidPartFill, topLeft = Offset(pX, pY), size = Size(solidPartPx, drawWidth))
    drawHatchPattern(pX, pY, solidPartPx, drawWidth, cfg.hatchSpacing, 45f, concreteStroke.copy(alpha = 0.4f))
    drawTextAnnotated("Solid Part", pX + solidPartPx / 2f, pY + drawWidth / 2f,
        textColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.75f, center = true, rotation = -90f)

    val rightSolidX = pX + drawSpan - solidPartPx
    drawRect(color = solidPartFill, topLeft = Offset(rightSolidX, pY), size = Size(solidPartPx, drawWidth))
    drawHatchPattern(rightSolidX, pY, solidPartPx, drawWidth, cfg.hatchSpacing, 45f, concreteStroke.copy(alpha = 0.4f))
    drawTextAnnotated("Solid Part", rightSolidX + solidPartPx / 2f, pY + drawWidth / 2f,
        textColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.75f, center = true, rotation = 90f)

    // ── Ribs and Hollow Blocks Zone (between solid parts) ─────────────────
    val blockZoneLeft = pX + solidPartPx
    val blockZoneRight = rightSolidX
    val blockZoneW = blockZoneRight - blockZoneLeft

    val numRibs = ((safeWidthMm / ribSpacingMm).toInt() + 1).coerceAtLeast(3)
    val ribWPx = (ribWidthMm * scale).toFloat().coerceIn(6f, 25f)
    val ribStep = drawWidth / numRibs.toFloat()

    // Draw hollow blocks & ribs
    val blockLenMm = 400.0 // standard block length
    val blockLenPx = (blockLenMm * scale).toFloat().coerceIn(18f, 50f)
    val numBlockCols = (blockZoneW / blockLenPx).toInt().coerceAtLeast(2)

    for (r in 0 until numRibs) {
        val ry = pY + r * ribStep

        // Rib line across the span
        drawRect(color = ribFill, topLeft = Offset(pX, ry), size = Size(drawSpan, ribWPx))
        drawRect(color = concreteStroke, topLeft = Offset(pX, ry), size = Size(drawSpan, ribWPx),
            style = Stroke(1f))

        // Rebar representation in rib (centered line)
        drawLine(barBottomColor, Offset(pX + 5f, ry + ribWPx / 2f), Offset(pX + drawSpan - 5f, ry + ribWPx / 2f),
            1.5f, cap = StrokeCap.Round)

        // Hollow blocks in row above rib
        if (r < numRibs - 1) {
            val voidTop = ry + ribWPx
            val voidH = (ribStep - ribWPx).coerceAtLeast(6f)

            for (c in 0 until numBlockCols) {
                val bx = blockZoneLeft + c * blockLenPx
                val bw = min(blockLenPx - 2f, blockZoneRight - bx)
                if (bw > 4f) {
                    drawRect(color = voidFill, topLeft = Offset(bx, voidTop), size = Size(bw, voidH))
                    drawRect(color = blockBorder, topLeft = Offset(bx, voidTop), size = Size(bw, voidH),
                        style = Stroke(0.8f))
                    // Block cross line
                    drawLine(blockBorder.copy(alpha = 0.4f), Offset(bx, voidTop), Offset(bx + bw, voidTop + voidH), 0.5f)
                }
            }
        }
    }

    // ── Transverse Cross-Rib (if span >= 5m) ──────────────────────────────
    if (needCrossRib) {
        val crossRibWPx = (crossRibWidthMm * scale).toFloat().coerceIn(10f, 30f)
        val crossRibX = pX + drawSpan / 2f - crossRibWPx / 2f
        drawRect(color = crossRibFill, topLeft = Offset(crossRibX, pY), size = Size(crossRibWPx, drawWidth))
        drawRect(color = Color(0xFF3949AB), topLeft = Offset(crossRibX, pY), size = Size(crossRibWPx, drawWidth),
            style = Stroke(1.5f))
        drawTextAnnotated("Cross-Rib (L ≥ 5m)", crossRibX + crossRibWPx / 2f, pY + 12f,
            Color(0xFF8C9EFF), cfg.annotationTextSmall * 0.75f, center = true, bold = true)
    }

    // Slab outline
    drawRect(color = concreteStroke, topLeft = Offset(pX, pY), size = Size(drawSpan, drawWidth),
        style = Stroke(2f))

    // ── Rebar Callout Tag ─────────────────────────────────────────────────
    val tagY = pY + drawWidth * 0.75f
    drawTextAnnotated("Ribs: ${ribBottomCount}Ø${ribBottomDia.toInt()} Bottom  |  Mesh: Ø${toppingMeshDia.toInt()}@${toppingMeshSpacing.toInt()}",
        pX + drawSpan / 2f, pY + drawWidth + 24f,
        barBottomColor, cfg.annotationTextSmall, center = true, bold = true)

    // ── Dimensions ────────────────────────────────────────────────────────
    drawHorizontalDimension(pX, pX + drawSpan, pY + drawWidth + 4f,
        "L = ${safeSpanMm.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawVerticalDimension(pY, pY + drawWidth, pX + drawSpan + 6f,
        "W = ${safeWidthMm.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 18f, cfg)
    drawHorizontalDimension(pX, pX + solidPartPx, pY - 6f,
        "bs=${solidPartMm.toInt()}", dimColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.8f, -14f, cfg)
}

// ============================================================================
// 3. SECTION A-A: TRANSVERSE T-RIBS & BLOCKS
// ============================================================================

private fun DrawScope.drawHordiSectionAA(
    left: Float, top: Float, width: Float, height: Float,
    ribWidthMm: Double, ribSpacingMm: Double, totalThickMm: Double, toppingThickMm: Double,
    ribHeightMm: Double, blockWidthMm: Double,
    ribBottomDia: Double, ribBottomCount: Int, topRebarDia: Double, topRebarCount: Int,
    stirrupDia: Double, stirrupSpacing: Double, toppingMeshDia: Double, cover: Double,
    toppingFill: Color, ribFill: Color, voidFill: Color, blockBorder: Color, concreteStroke: Color,
    barBottomColor: Color, barTopColor: Color, stirrupColor: Color, meshColor: Color, hatchColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val pad = 35f
    val avW = width - 2 * pad
    val numModules = 3 // show 3 T-ribs with 2 hollow blocks between

    val moduleSpacingPx = (avW / numModules.toFloat()).coerceIn(60f, 140f)
    val scale = (moduleSpacingPx / ribSpacingMm.toFloat())

    val ribWPx = (ribWidthMm * scale).toFloat().coerceInMin(16f, moduleSpacingPx * 0.40f)
    val toppingPx = (toppingThickMm * scale).toFloat().coerceIn(14f, 35f)
    val ribHPx = (ribHeightMm * scale).toFloat().coerceIn(30f, 85f)
    val totalHPx = toppingPx + ribHPx

    val secL = left + pad + (avW - numModules * moduleSpacingPx) / 2f
    val secY = top + (height - totalHPx) / 2f - 10f
    val toppingBotY = secY + toppingPx
    val secBotY = secY + totalHPx
    val totalDrawW = numModules * moduleSpacingPx

    // ── Continuous Topping Slab Flange ─────────────────────────────────────
    drawRect(color = toppingFill, topLeft = Offset(secL, secY), size = Size(totalDrawW, toppingPx))
    drawHatchPattern(secL, secY, totalDrawW, toppingPx, cfg.hatchSpacing, 45f, hatchColor)
    drawRect(color = concreteStroke, topLeft = Offset(secL, secY), size = Size(totalDrawW, toppingPx),
        style = Stroke(1.5f))

    // ── Topping Welded Wire Mesh (Shrinkage Steel) ─────────────────────────
    val meshY = secY + toppingPx / 2f
    drawLine(meshColor, Offset(secL + 10f, meshY), Offset(secL + totalDrawW - 10f, meshY), 1.5f)
    // Transverse dots of the mesh
    val meshDotStep = moduleSpacingPx / 3f
    for (i in 0..((totalDrawW - 20f) / meshDotStep).toInt()) {
        val mx = secL + 10f + i * meshDotStep
        drawCircle(color = meshColor, radius = 2f, center = Offset(mx, meshY - 2f))
    }
    drawTextAnnotated("Mesh: Ø${toppingMeshDia.toInt()}/200", secL + totalDrawW * 0.5f, secY - 6f,
        meshColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // ── Ribs & Blocks Loop ────────────────────────────────────────────────
    for (m in 0 until numModules) {
        val ribCenterX = secL + m * moduleSpacingPx + moduleSpacingPx / 2f
        val ribLeft = ribCenterX - ribWPx / 2f

        // Rib Web
        drawRect(color = ribFill, topLeft = Offset(ribLeft, toppingBotY), size = Size(ribWPx, ribHPx))
        drawHatchPattern(ribLeft, toppingBotY, ribWPx, ribHPx, cfg.hatchSpacing * 0.8f, 45f, hatchColor)
        drawRect(color = concreteStroke, topLeft = Offset(ribLeft, toppingBotY), size = Size(ribWPx, ribHPx),
            style = Stroke(1.5f))

        // Stirrup Link inside Rib
        val stirL = ribLeft + 4f
        val stirW = ribWPx - 8f
        val stirT = toppingBotY + 2f
        val stirH = ribHPx - 6f
        drawRect(color = stirrupColor, topLeft = Offset(stirL, stirT), size = Size(stirW, stirH),
            style = Stroke(1.2f))
        // 135° hook at top of stirrup into flange
        drawLine(stirrupColor, Offset(stirL, stirT), Offset(stirL - 4f, stirT - 6f), 1.2f, cap = StrokeCap.Round)
        drawLine(stirrupColor, Offset(stirL + stirW, stirT), Offset(stirL + stirW + 4f, stirT - 6f), 1.2f, cap = StrokeCap.Round)

        // Bottom Rebar (Circles)
        val barR = (ribBottomDia * scale * 0.45).toFloat().coerceIn(3f, 7f)
        val barY = secBotY - 4f - barR
        val step = ribWPx / (ribBottomCount + 1).toFloat()
        for (b in 1..ribBottomCount) {
            val bx = ribLeft + b * step
            drawCircle(color = barBottomColor, radius = barR, center = Offset(bx, barY))
            drawCircle(color = Color.White, radius = barR * 0.3f, center = Offset(bx, barY))
        }

        // Top Hanger Bars (in flange above rib)
        val topBarR = (topRebarDia * scale * 0.40).toFloat().coerceIn(2.5f, 5.5f)
        val tby = secY + toppingPx * 0.35f
        for (tb in 1..topRebarCount.coerceAtMost(2)) {
            val tbx = ribLeft + tb * (ribWPx / (topRebarCount + 1).toFloat())
            drawCircle(color = barTopColor, radius = topBarR, center = Offset(tbx, tby))
        }

        // Hollow Block between this rib and next
        if (m < numModules - 1) {
            val blockL = ribLeft + ribWPx + 2f
            val nextRibL = ribCenterX + moduleSpacingPx - ribWPx / 2f
            val blockW = nextRibL - blockL - 2f

            drawRect(color = voidFill, topLeft = Offset(blockL, toppingBotY + 2f), size = Size(blockW, ribHPx - 4f))
            drawRect(color = blockBorder, topLeft = Offset(blockL, toppingBotY + 2f), size = Size(blockW, ribHPx - 4f),
                style = Stroke(1f))
            // Cross line in block
            drawLine(blockBorder.copy(alpha = 0.4f), Offset(blockL, toppingBotY + 2f),
                Offset(blockL + blockW, toppingBotY + ribHPx - 2f), 0.8f)
            drawLine(blockBorder.copy(alpha = 0.4f), Offset(blockL + blockW, toppingBotY + 2f),
                Offset(blockL, toppingBotY + ribHPx - 2f), 0.8f)
            drawTextAnnotated("BLOCK", blockL + blockW / 2f, toppingBotY + ribHPx / 2f,
                Color(0xFF90A4AE), cfg.annotationTextSmall * 0.75f, center = true)
        }
    }

    // ── Labels & Dimensions ───────────────────────────────────────────────
    val midRibX = secL + moduleSpacingPx / 2f
    drawTextAnnotated("${ribBottomCount}Ø${ribBottomDia.toInt()}", midRibX, secBotY + 12f,
        barBottomColor, cfg.annotationTextSmall, center = true, bold = true)

    // Overall height dimension
    drawVerticalDimension(secY, secBotY, secL + totalDrawW + 6f,
        "ts=${totalThickMm.toInt()}mm", dimColor, cfg.dimTextSize * 0.85f, 16f, cfg)
    // Topping thickness dimension
    drawVerticalDimension(secY, toppingBotY, secL + totalDrawW + 6f,
        "tf=${toppingThickMm.toInt()}mm", dimColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.8f, 32f, cfg)
    // Rib width dimension
    drawHorizontalDimension(midRibX - ribWPx / 2f, midRibX + ribWPx / 2f, secBotY + 4f,
        "b=${ribWidthMm.toInt()}", dimColor, cfg.annotationTextSmall * 0.8f, 16f, cfg)
}

// ============================================================================
// 4. SECTION B-B: LONGITUDINAL RIB PROFILE (SOLID PART + REBAR CURTAILMENT)
// ============================================================================

private fun DrawScope.drawHordiSectionBB(
    left: Float, top: Float, width: Float, height: Float,
    safeSpanMm: Double, totalThickMm: Double, toppingThickMm: Double,
    solidPartMm: Double, needCrossRib: Boolean, crossRibWidthMm: Double,
    ribBottomDia: Double, ribBottomCount: Int, topRebarDia: Double, topRebarCount: Int,
    stirrupDia: Double, stirrupSpacing: Double, cover: Double,
    toppingFill: Color, ribFill: Color, solidPartFill: Color, voidFill: Color, concreteStroke: Color,
    barBottomColor: Color, barTopColor: Color, stirrupColor: Color, crossRibFill: Color, hatchColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig, designCode: DesignCode
) {
    val pad = 35f
    val avW = width - 2 * pad
    val scale = (avW / safeSpanMm.toFloat()).coerceAtLeast(0.01f) * 0.95f

    val drawSpan = (safeSpanMm * scale).toFloat()
    val thickScale = ((height * 0.35f) / totalThickMm.toFloat()).coerceAtLeast(0.1f)
    val totalHPx = (totalThickMm * thickScale).toFloat().coerceIn(24f, 65f)
    val toppingHPx = (toppingThickMm * thickScale).toFloat().coerceIn(8f, 22f)

    val secL = left + pad + (avW - drawSpan) / 2f
    val secY = top + height * 0.35f
    val secR = secL + drawSpan
    val secBotY = secY + totalHPx

    val solidPartPx = (solidPartMm * scale).toFloat().coerceInMin(18f, drawSpan * 0.25f)
    val supportW = 35f

    // ── Supporting Beams at Left & Right ──────────────────────────────────
    drawRect(color = Color(0xFF37474F), topLeft = Offset(secL - supportW, secY), size = Size(supportW, totalHPx + 25f))
    drawRect(color = concreteStroke, topLeft = Offset(secL - supportW, secY), size = Size(supportW, totalHPx + 25f),
        style = Stroke(1.5f))
    drawTextAnnotated("Beam", secL - supportW / 2f, secY + totalHPx / 2f,
        textColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.75f, center = true, rotation = -90f)

    drawRect(color = Color(0xFF37474F), topLeft = Offset(secR, secY), size = Size(supportW, totalHPx + 25f))
    drawRect(color = concreteStroke, topLeft = Offset(secR, secY), size = Size(supportW, totalHPx + 25f),
        style = Stroke(1.5f))
    drawTextAnnotated("Beam", secR + supportW / 2f, secY + totalHPx / 2f,
        textColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.75f, center = true, rotation = 90f)

    // ── Solid Concrete Parts (Left & Right adjacent to support) ───────────
    drawRect(color = solidPartFill, topLeft = Offset(secL, secY), size = Size(solidPartPx, totalHPx))
    drawHatchPattern(secL, secY, solidPartPx, totalHPx, cfg.hatchSpacing, 45f, hatchColor)
    drawRect(color = concreteStroke, topLeft = Offset(secL, secY), size = Size(solidPartPx, totalHPx), style = Stroke(1.5f))

    drawRect(color = solidPartFill, topLeft = Offset(secR - solidPartPx, secY), size = Size(solidPartPx, totalHPx))
    drawHatchPattern(secR - solidPartPx, secY, solidPartPx, totalHPx, cfg.hatchSpacing, 45f, hatchColor)
    drawRect(color = concreteStroke, topLeft = Offset(secR - solidPartPx, secY), size = Size(solidPartPx, totalHPx), style = Stroke(1.5f))

    // ── Topping Flange across span ────────────────────────────────────────
    drawRect(color = toppingFill, topLeft = Offset(secL, secY), size = Size(drawSpan, toppingHPx))

    // ── Blocks along rib length ───────────────────────────────────────────
    val ribZoneL = secL + solidPartPx
    val ribZoneR = secR - solidPartPx
    val ribZoneW = ribZoneR - ribZoneL

    drawRect(color = ribFill, topLeft = Offset(ribZoneL, secY + toppingHPx), size = Size(ribZoneW, totalHPx - toppingHPx))
    drawHatchPattern(ribZoneL, secY + toppingHPx, ribZoneW, totalHPx - toppingHPx, cfg.hatchSpacing * 1.5f, 45f, hatchColor)
    drawRect(color = concreteStroke, topLeft = Offset(secL, secY), size = Size(drawSpan, totalHPx), style = Stroke(2f))

    // ── Cross-Rib in the center ───────────────────────────────────────────
    if (needCrossRib) {
        val crWPx = (crossRibWidthMm * scale).toFloat().coerceIn(10f, 25f)
        val crX = secL + drawSpan / 2f - crWPx / 2f
        drawRect(color = crossRibFill, topLeft = Offset(crX, secY), size = Size(crWPx, totalHPx))
        drawRect(color = Color(0xFF3949AB), topLeft = Offset(crX, secY), size = Size(crWPx, totalHPx), style = Stroke(1.5f))
    }

    // ── Bottom Continuous Reinforcement (anchored into support beam) ─────
    val botBarY = secBotY - 6f
    drawLine(color = barBottomColor,
        start = Offset(secL - supportW * 0.7f, botBarY),
        end   = Offset(secR + supportW * 0.7f, botBarY),
        strokeWidth = 3f, cap = StrokeCap.Round)
    // 90° anchor hooks at support ends
    drawLine(color = barBottomColor,
        start = Offset(secL - supportW * 0.7f, botBarY),
        end   = Offset(secL - supportW * 0.7f, botBarY - totalHPx * 0.5f),
        strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = barBottomColor,
        start = Offset(secR + supportW * 0.7f, botBarY),
        end   = Offset(secR + supportW * 0.7f, botBarY - totalHPx * 0.5f),
        strokeWidth = 3f, cap = StrokeCap.Round)

    drawTextAnnotated("Bottom: ${ribBottomCount}Ø${ribBottomDia.toInt()} (Continuous)",
        secL + drawSpan / 2f, botBarY + 14f,
        barBottomColor, cfg.annotationTextSmall, center = true, bold = true)

    // ── Top Negative Reinforcement (curtailed at 0.25 Ln per ECP/ACI) ──────
    val topBarY = secY + 6f
    val curtailPx = drawSpan * 0.25f

    // Left negative bar
    drawLine(color = barTopColor,
        start = Offset(secL - supportW * 0.7f, topBarY),
        end   = Offset(secL + curtailPx, topBarY),
        strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawLine(color = barTopColor,
        start = Offset(secL - supportW * 0.7f, topBarY),
        end   = Offset(secL - supportW * 0.7f, topBarY + totalHPx * 0.6f),
        strokeWidth = 2.5f, cap = StrokeCap.Round)

    // Right negative bar
    drawLine(color = barTopColor,
        start = Offset(secR - curtailPx, topBarY),
        end   = Offset(secR + supportW * 0.7f, topBarY),
        strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawLine(color = barTopColor,
        start = Offset(secR + supportW * 0.7f, topBarY),
        end   = Offset(secR + supportW * 0.7f, topBarY + totalHPx * 0.6f),
        strokeWidth = 2.5f, cap = StrokeCap.Round)

    drawTextAnnotated("Top: ${topRebarCount}Ø${topRebarDia.toInt()} (0.25 Ln)",
        secL + curtailPx / 2f, topBarY - 8f,
        barTopColor, cfg.annotationTextSmall * 0.85f, center = true)

    // ── Dimensions ────────────────────────────────────────────────────────
    drawHorizontalDimension(secL, secR, secBotY + 22f,
        "L = ${safeSpanMm.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawHorizontalDimension(secL, secL + solidPartPx, secY - 6f,
        "bs=${solidPartMm.toInt()}", dimColor.copy(alpha = 0.8f), cfg.annotationTextSmall * 0.8f, -14f, cfg)
}

// ============================================================================
// 5. BAR BENDING SCHEDULE (BBS) & DETAILING TABLE
// ============================================================================

private fun DrawScope.drawHordiBBSTable(
    left: Float, top: Float, width: Float, height: Float,
    safeSpanMm: Double, safeWidthMm: Double, ribSpacingMm: Double, totalThickMm: Double, ribWidthMm: Double,
    ribBottomDia: Double, ribBottomCount: Int, topRebarDia: Double, topRebarCount: Int,
    stirrupDia: Double, stirrupSpacing: Double, toppingMeshDia: Double, toppingMeshSpacing: Double,
    barBottomColor: Color, barTopColor: Color, stirrupColor: Color, meshColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    val numRibs = ((safeWidthMm / ribSpacingMm).toInt() + 1).coerceAtLeast(3)

    // Bar cut lengths
    val botCutLenM = (safeSpanMm + 600.0) / 1000.0 // + hooks
    val topCutLenM = (safeSpanMm * 0.25 + 400.0) / 1000.0
    val stirPerimeterM = (2.0 * (ribWidthMm - 40.0) + 2.0 * (totalThickMm - 50.0) + 200.0) / 1000.0
    val stirCountPerRib = ((safeSpanMm / stirrupSpacing) + 1).toInt()
    val totalStirCount = stirCountPerRib * numRibs

    // Weights (kg = count * length * dia^2 * 0.00617)
    val totalBotCount = ribBottomCount * numRibs
    val totalTopCount = topRebarCount * numRibs * 2 // two ends
    val wBot = totalBotCount * botCutLenM * ribBottomDia * ribBottomDia * 0.00617
    val wTop = totalTopCount * topCutLenM * topRebarDia * topRebarDia * 0.00617
    val wStir = totalStirCount * stirPerimeterM * stirrupDia * stirrupDia * 0.00617

    // Mesh weight for slab area
    val slabAreaM2 = (safeSpanMm * safeWidthMm) / 1e6
    val wMesh = slabAreaM2 * 2.22 // ~2.22 kg/m2 for 5Ø6 mesh
    val totalWeight = wBot + wTop + wStir + wMesh

    val headers = listOf("Mark", "Component", "Rebar Size", "Cut Length", "Total No.", "Weight (kg)")
    val rows = listOf(
        listOf("RB-B", "Rib Bottom", "${ribBottomCount}Ø${ribBottomDia.toInt()}", "${(botCutLenM * 1000).toInt()} mm", "$totalBotCount", "%.1f".format(wBot)),
        listOf("RB-T", "Rib Top Hanger", "${topRebarCount}Ø${topRebarDia.toInt()}", "${(topCutLenM * 1000).toInt()} mm", "$totalTopCount", "%.1f".format(wTop)),
        listOf("ST-1", "Rib Stirrup", "Ø${stirrupDia.toInt()}@${stirrupSpacing.toInt()}", "${(stirPerimeterM * 1000).toInt()} mm", "$totalStirCount", "%.1f".format(wStir)),
        listOf("MSH", "Topping Mesh", "Ø${toppingMeshDia.toInt()}@${toppingMeshSpacing.toInt()}", "Mesh Rolls", "Both Ways", "%.1f".format(wMesh))
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
