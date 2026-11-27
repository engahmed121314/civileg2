package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
// PROFESSIONAL SOIL-STRUCTURE INTERACTION DRAWING
// v2.0 — Comprehensive Multi-View Geotechnical & Structural Engineering Drawing
// Codes: ECP 203 / ECP 202 (Soil & Foundations), ACI 318-19, ASCE 7-16, SBC 303/304
// ============================================================================

/**
 * Professional Soil-Structure Interaction Engineering Drawing
 *
 * Renders:
 *  - viewMode 0 (All):       Elevation (Foundation + Soil Layers + Pressure) +
 *                            Plan (Footing + Eccentricity + Kern Zone) +
 *                            Boussinesq Stress Bulb / Settlement + Stability Table
 *  - viewMode 1 (Elevation): Detailed elevation with soil stratification, P & M loads,
 *                            contact pressure trapezoid/triangle, uplift indicator
 *  - viewMode 2 (Plan & Kern): Plan view with column, center of pressure, kern diamond,
 *                            e_x eccentricity and 100% compression vs partial uplift check
 *  - viewMode 3 (Stress Bulb): Boussinesq stress isobars (0.1q to 0.9q) beneath foundation
 *                            and settlement depth profile
 *  - viewMode 4 (Table):     Complete Geotechnical Capacity & Stability Table with F.S.
 */
@Composable
fun ProfessionalSoilStructureDrawing(
    foundationLengthMm: Double,
    foundationWidthMm: Double,
    foundationThicknessMm: Double,
    columnLoadKN: Double,
    momentKNm: Double,
    soilBearingCapacityKpa: Double,
    maxPressureKpa: Double,
    minPressureKpa: Double,
    averagePressureKpa: Double,
    settlementMm: Double,
    overturningFS: Double,
    slidingFS: Double,
    bearingFS: Double,
    cover: Double,
    columnWidthMm: Double = 500.0,
    columnDepthMm: Double = 500.0,
    waterTableDepthM: Double = 2.5,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard ─────────────────────────────────────────────────────────
    InputGuard.positive("foundationLengthMm", foundationLengthMm)
    InputGuard.positive("foundationWidthMm", foundationWidthMm)
    InputGuard.positive("foundationThicknessMm", foundationThicknessMm)
    InputGuard.positive("columnLoadKN", columnLoadKN)
    InputGuard.positive("soilBearingCapacityKpa", soilBearingCapacityKpa)
    InputGuard.positive("cover", cover)

    val codeLabel = designCode.version
    val cfg = drawingDimensionsConfig()

    Canvas(modifier = modifier.fillMaxWidth().fillMaxSize()) {
        // Too small to draw truthfully: omit rather than distort geometry.
        if (isDegenerateForDrawing()) return@Canvas
        val W = size.width
        val H = size.height

        // ── Color Palette from DrawingColorDefaults ───────────────────────
        val C = DrawingColorDefaults
        val concreteFill       = C.ConcreteFill
        val concreteStroke     = C.ConcreteTopGray
        val concreteLight      = Color(0xFF616161)
        val soilBrown          = C.SoilBrown
        val soilSandColor      = Color(0xFF795548)
        val soilClayColor      = Color(0xFF5D4037)
        val waterBlue          = C.WaterBlue
        val pressureColor      = Color(0xFF29B6F6)
        val allowableColor     = C.SafeGreen
        val unsafeColor        = C.UnsafeRed
        val warningColor       = C.WarningOrange
        val kernColor          = Color(0xFFAB47BC)
        val dimColor           = C.DimensionWhite
        val textColor          = C.DimensionWhite
        val gridColor          = C.GridLine

        // ── Safe Dimensions ────────────────────────────────────────────────
        val safeL = foundationLengthMm.coerceAtLeast(600.0)
        val safeW = foundationWidthMm.coerceAtLeast(600.0)
        val safeT = foundationThicknessMm.coerceAtLeast(200.0)
        val safeColW = columnWidthMm.coerceAtLeast(200.0)
        val safeColD = columnDepthMm.coerceAtLeast(200.0)

        // Eccentricity: e = M / P (in mm)
        val eccentricityMm = if (columnLoadKN > 0.001) (abs(momentKNm) / columnLoadKN) * 1000.0 else 0.0
        val kernLimitMm = safeL / 6.0 // Middle third limit: e <= L/6 for no tension
        val isFullCompression = eccentricityMm <= kernLimitMm

        // ── Layout zones (viewMode-aware) ─────────────────────────────────
        val margin = cfg.margin
        val headerH = cfg.headerHeight + 10f

        // ── Header band ───────────────────────────────────────────────────
        drawSoilStructureHeader(W, cfg, codeLabel, textColor)

        // ── View Mode Routing ──────────────────────────────────────────────
        when (viewMode) {
            1 -> {
                // Elevation mode only: full canvas height
                drawZoneLabel(margin, headerH + 6f, "ELEVATION & SOIL STRATA", cfg)
                drawSoilStructureElevation(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeL, safeW, safeT, safeColW, safeColD,
                    columnLoadKN, momentKNm, eccentricityMm, isFullCompression,
                    soilBearingCapacityKpa, maxPressureKpa, minPressureKpa, averagePressureKpa,
                    waterTableDepthM,
                    concreteFill, concreteStroke, concreteLight,
                    soilBrown, soilSandColor, soilClayColor, waterBlue,
                    pressureColor, allowableColor, unsafeColor, warningColor,
                    dimColor, textColor, cfg, designCode
                )
            }
            2 -> {
                // Plan & Kern View: full canvas height
                drawZoneLabel(margin, headerH + 6f, "FOUNDATION PLAN & KERN ZONE", cfg)
                drawFoundationPlanKern(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeL, safeW, safeColW, safeColD,
                    eccentricityMm, kernLimitMm, isFullCompression,
                    concreteFill, concreteStroke, kernColor, pressureColor,
                    dimColor, textColor, cfg
                )
            }
            3 -> {
                // Stress Bulb & Settlement View
                drawZoneLabel(margin, headerH + 6f, "BOUSSINESQ STRESS BULB & SETTLEMENT", cfg)
                drawBoussinesqStressBulb(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    safeL, safeT, maxPressureKpa, settlementMm,
                    soilBrown, waterBlue, pressureColor, dimColor, textColor, cfg
                )
            }
            4 -> {
                // Stability & Check Table View
                drawZoneLabel(margin, headerH + 6f, "GEOTECHNICAL STABILITY SCHEDULE", cfg)
                drawSoilStructureTable(
                    margin, headerH + 16f, W - 2 * margin, H - headerH - margin * 2.5f,
                    maxPressureKpa, minPressureKpa, averagePressureKpa, soilBearingCapacityKpa,
                    settlementMm, overturningFS, slidingFS, bearingFS,
                    eccentricityMm, kernLimitMm, isFullCompression,
                    textColor, cfg
                )
            }
            else -> {
                // viewMode 0 (All): Split into Top (Elevation & Plan side-by-side) + Bottom (Stress Bulb & Table)
                val topH = (H - headerH - margin) * 0.48f
                val botH = (H - headerH - margin) * 0.44f
                val topY = headerH + 10f
                val botY = topY + topH + 14f
                val halfW = (W - 3 * margin) / 2f

                drawSectionSeparator(botY - 7f, W, "SOIL MECHANICS & STABILITY ANALYSIS", cfg)
                drawCombinedSectionLabel(H * 0.015f, W, listOf("Elevation", "Kern Plan", "Stress Bulb", "Stability"), cfg)

                // Top-Left: Elevation
                drawZoneLabel(margin, topY + 2f, "ELEVATION", cfg)
                drawSoilStructureElevation(
                    margin, topY + 12f, halfW, topH - 16f,
                    safeL, safeW, safeT, safeColW, safeColD,
                    columnLoadKN, momentKNm, eccentricityMm, isFullCompression,
                    soilBearingCapacityKpa, maxPressureKpa, minPressureKpa, averagePressureKpa,
                    waterTableDepthM,
                    concreteFill, concreteStroke, concreteLight,
                    soilBrown, soilSandColor, soilClayColor, waterBlue,
                    pressureColor, allowableColor, unsafeColor, warningColor,
                    dimColor, textColor, cfg, designCode
                )

                // Top-Right: Foundation Plan & Kern
                val planLeft = margin + halfW + margin
                drawZoneLabel(planLeft, topY + 2f, "KERN PLAN", cfg)
                drawFoundationPlanKern(
                    planLeft, topY + 12f, halfW, topH - 16f,
                    safeL, safeW, safeColW, safeColD,
                    eccentricityMm, kernLimitMm, isFullCompression,
                    concreteFill, concreteStroke, kernColor, pressureColor,
                    dimColor, textColor, cfg
                )

                // Bottom-Left: Stress Bulb
                drawZoneLabel(margin, botY + 2f, "STRESS BULB", cfg)
                drawBoussinesqStressBulb(
                    margin, botY + 12f, halfW, botH - 16f,
                    safeL, safeT, maxPressureKpa, settlementMm,
                    soilBrown, waterBlue, pressureColor, dimColor, textColor, cfg
                )

                // Bottom-Right: Stability Table
                val tblLeft = margin + halfW + margin
                drawZoneLabel(tblLeft, botY + 2f, "STABILITY", cfg)
                drawSoilStructureTable(
                    tblLeft, botY + 12f, halfW, botH - 16f,
                    maxPressureKpa, minPressureKpa, averagePressureKpa, soilBearingCapacityKpa,
                    settlementMm, overturningFS, slidingFS, bearingFS,
                    eccentricityMm, kernLimitMm, isFullCompression,
                    textColor, cfg
                )
            }
        }

        // ── Responsive Title Block & Scale Bar ─────────────────────────────
        val blockTopLeft = drawMeasuredSheetBlock(
            summary = resultSummary,
            drawingTitle = "Soil-Structure Interaction",
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

private fun DrawScope.drawSoilStructureHeader(
    W: Float, cfg: DrawingDimensionsConfig,
    codeLabel: String, textColor: Color
) {
    val hBg = Color(0xFF263238)
    drawRect(color = hBg, topLeft = Offset(0f, 0f), size = Size(W, cfg.headerHeight))
    drawTextAnnotated(
        "SOIL-STRUCTURE INTERACTION & BEARING CAPACITY  |  $codeLabel",
        W / 2f, cfg.headerHeight * 0.65f,
        textColor, cfg.headerTextSize, center = true, bold = true
    )
    drawTextAnnotated(
        "ECP 202/203 §10 | ACI 318-19 §13 | ASCE 7-16 §12 | SBC 303/304",
        W / 2f, cfg.headerHeight * 0.90f,
        textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.85f, center = true
    )
}

// ============================================================================
// 2. ELEVATION VIEW: FOUNDATION + SOIL STRATA + CONTACT PRESSURE
// ============================================================================

private fun DrawScope.drawSoilStructureElevation(
    left: Float, top: Float, width: Float, height: Float,
    safeL: Double, safeW: Double, safeT: Double, safeColW: Double, safeColD: Double,
    columnLoadKN: Double, momentKNm: Double, eccentricityMm: Double, isFullCompression: Boolean,
    soilBearingCapacityKpa: Double, maxPressureKpa: Double, minPressureKpa: Double, averagePressureKpa: Double,
    waterTableDepthM: Double,
    concreteFill: Color, concreteStroke: Color, concreteLight: Color,
    soilBrown: Color, soilSandColor: Color, soilClayColor: Color, waterBlue: Color,
    pressureColor: Color, allowableColor: Color, unsafeColor: Color, warningColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig,
    designCode: DesignCode
) {
    // Degenerate zone (tiny/negative canvas while scrolling or off-screen): nothing safe to draw.
    if (width <= 0f || height <= 0f) return
    val scale = min(width / (safeL * 1.35f).toFloat(), height / (safeT * 4.5f).toFloat())
    val drawL = (safeL * scale).toFloat()
    val drawT = (safeT * scale).toFloat()
    val drawColW = (safeColW * scale).toFloat().coerceInMin(20f, drawL * 0.35f)
    val drawColH = drawT * 0.9f

    val footX = left + (width - drawL) / 2f
    val footY = top + drawColH + 20f
    val footBotY = footY + drawT

    // ── Ground level line ─────────────────────────────────────────────────
    val groundY = footY - 10f
    drawLine(
        color = Color(0xFFA1887F),
        start = Offset(left, groundY),
        end = Offset(left + width, groundY),
        strokeWidth = 2f
    )
    // Ground hatch (slant lines above ground)
    for (i in 0 until (width / 20f).toInt()) {
        val gx = left + i * 20f
        drawLine(Color(0xFFA1887F).copy(alpha = 0.5f),
            Offset(gx, groundY), Offset(gx - 8f, groundY - 6f), 1f)
    }
    drawTextAnnotated("G.L. ±0.00", left + 8f, groundY - 8f,
        textColor.copy(alpha = 0.7f), cfg.annotationTextSmall * 0.85f)

    // ── Soil Strata Layers (Below Foundation) ─────────────────────────────
    val stratum1Bot = footBotY + drawT * 1.2f
    val stratum2Bot = top + height - 30f

    // Stratum 1: Dense Sand / Gravel
    drawRect(
        color = soilSandColor.copy(alpha = 0.25f),
        topLeft = Offset(left, footBotY),
        size = Size(width, stratum1Bot - footBotY)
    )
    drawHatchPattern(left, footBotY, width, stratum1Bot - footBotY,
        cfg.hatchSpacing * 1.5f, 45f, soilSandColor.copy(alpha = 0.3f))
    drawTextAnnotated("Layer 1: Medium to Dense Sand (γ=18 kN/m³)", left + 6f, footBotY + 14f,
        textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.75f)

    // Stratum 2: Stiff Clay / Bedrock
    drawRect(
        color = soilClayColor.copy(alpha = 0.35f),
        topLeft = Offset(left, stratum1Bot),
        size = Size(width, stratum2Bot - stratum1Bot)
    )
    drawHatchPattern(left, stratum1Bot, width, stratum2Bot - stratum1Bot,
        cfg.hatchSpacing * 1.2f, -45f, soilClayColor.copy(alpha = 0.35f))
    drawTextAnnotated("Layer 2: Stiff Silty Clay (cu=60 kPa)", left + 6f, stratum1Bot + 14f,
        textColor.copy(alpha = 0.6f), cfg.annotationTextSmall * 0.75f)

    // Water Table line
    val wtY = footBotY + (waterTableDepthM.toFloat() * 1000f * scale * 0.15f).coerceInMin(15f, stratum1Bot - footBotY - 5f)
    val dashWater = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
    drawLine(waterBlue, Offset(left, wtY), Offset(left + width, wtY), 1.8f, pathEffect = dashWater)
    // Water symbols
    drawTextAnnotated("▼ W.T. (-${String.format("%.1f", waterTableDepthM)}m)",
        left + width - 90f, wtY - 4f, waterBlue, cfg.annotationTextSmall * 0.85f, bold = true)

    // ── Foundation Slab ───────────────────────────────────────────────────
    drawRect(color = concreteFill, topLeft = Offset(footX, footY), size = Size(drawL, drawT))
    drawHatchPattern(footX, footY, drawL, drawT, cfg.hatchSpacing, 45f, concreteStroke.copy(alpha = 0.5f))
    drawRect(color = concreteStroke, topLeft = Offset(footX, footY), size = Size(drawL, drawT),
        style = Stroke(cfg.strokeBold * 0.8f))

    // ── Column ────────────────────────────────────────────────────────────
    val colX = footX + (drawL - drawColW) / 2f
    val colY = footY - drawColH
    drawRect(color = concreteLight, topLeft = Offset(colX, colY), size = Size(drawColW, drawColH))
    drawRect(color = concreteStroke, topLeft = Offset(colX, colY), size = Size(drawColW, drawColH),
        style = Stroke(cfg.strokeBold * 0.8f))

    // ── Load Arrows (Axial Load P & Moment M) ─────────────────────────────
    val arrowLen = 28f
    val colCenterX = colX + drawColW / 2f
    // Downward axial load arrow
    drawLine(
        color = Color(0xFFFF5252),
        start = Offset(colCenterX, colY - arrowLen),
        end   = Offset(colCenterX, colY),
        strokeWidth = 3f, cap = StrokeCap.Round
    )
    // Arrowhead
    drawPath(Path().apply {
        moveTo(colCenterX, colY)
        lineTo(colCenterX - 5f, colY - 8f)
        lineTo(colCenterX + 5f, colY - 8f)
        close()
    }, color = Color(0xFFFF5252))
    drawTextAnnotated("P = ${columnLoadKN.toInt()} kN", colCenterX, colY - arrowLen - 6f,
        Color(0xFFFF5252), cfg.annotationTextSmall, center = true, bold = true)

    // Moment curved arrow (if M > 0)
    if (abs(momentKNm) > 0.1) {
        val arcR = drawColW * 0.6f
        val arcCenter = Offset(colCenterX + drawColW * 0.6f, colY - drawColH * 0.4f)
        val mPath = Path().apply {
            arcTo(
                androidx.compose.ui.geometry.Rect(
                    arcCenter.x - arcR, arcCenter.y - arcR,
                    arcCenter.x + arcR, arcCenter.y + arcR
                ),
                -120f, 150f, false
            )
        }
        drawPath(mPath, color = warningColor, style = Stroke(2.2f))
        drawTextAnnotated("M = ${momentKNm.toInt()} kN·m", arcCenter.x + arcR + 4f, arcCenter.y,
            warningColor, cfg.annotationTextSmall * 0.9f, bold = true)
    }

    // ── Contact Soil Pressure Diagram (Trapezoid / Triangle) ──────────────
    val maxPressureH = (height * 0.22f).coerceAtLeast(35f)
    val maxRatio = (maxPressureKpa / max(soilBearingCapacityKpa * 1.2, 1.0)).toFloat().coerceIn(0.1f, 1.0f)
    val pMaxDrawH = maxPressureH * maxRatio
    val pMinDrawH = (pMaxDrawH * (minPressureKpa / max(maxPressureKpa, 1.0))).toFloat()

    val pDiagY = footBotY + 4f
    val pPath = Path()
    val pFillPath = Path()

    if (isFullCompression && pMinDrawH >= 0f) {
        // Trapezoidal distribution
        pPath.moveTo(footX, pDiagY)
        pPath.lineTo(footX, pDiagY + pMaxDrawH)
        pPath.lineTo(footX + drawL, pDiagY + pMinDrawH)
        pPath.lineTo(footX + drawL, pDiagY)

        pFillPath.moveTo(footX, pDiagY)
        pFillPath.lineTo(footX, pDiagY + pMaxDrawH)
        pFillPath.lineTo(footX + drawL, pDiagY + pMinDrawH)
        pFillPath.lineTo(footX + drawL, pDiagY)
        pFillPath.close()

        drawPath(pFillPath, color = pressureColor.copy(alpha = 0.25f))
        drawPath(pPath, color = pressureColor, style = Stroke(2f))

        // Vertical pressure ordinates
        for (i in 0..6) {
            val fx = footX + drawL * (i / 6f)
            val fy = pDiagY + pMaxDrawH - (pMaxDrawH - pMinDrawH) * (i / 6f)
            drawLine(pressureColor.copy(alpha = 0.6f), Offset(fx, pDiagY), Offset(fx, fy), 1f)
            // Arrow pointing up to footing
            drawPath(Path().apply {
                moveTo(fx, pDiagY)
                lineTo(fx - 3f, pDiagY + 5f)
                lineTo(fx + 3f, pDiagY + 5f)
                close()
            }, color = pressureColor.copy(alpha = 0.6f))
        }
    } else {
        // Triangular distribution with uplift separation (e > L/6)
        val compressionLengthRatio = (3.0 * (safeL / 2.0 - eccentricityMm) / safeL).toFloat().coerceIn(0.2f, 0.95f)
        val compDrawL = drawL * compressionLengthRatio
        val sepX = footX + compDrawL

        pPath.moveTo(footX, pDiagY)
        pPath.lineTo(footX, pDiagY + pMaxDrawH)
        pPath.lineTo(sepX, pDiagY)

        pFillPath.moveTo(footX, pDiagY)
        pFillPath.lineTo(footX, pDiagY + pMaxDrawH)
        pFillPath.lineTo(sepX, pDiagY)
        pFillPath.close()

        drawPath(pFillPath, color = warningColor.copy(alpha = 0.3f))
        drawPath(pPath, color = warningColor, style = Stroke(2f))

        // Uplift / Separation dashed zone
        val dashSep = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
        drawLine(unsafeColor, Offset(sepX, pDiagY), Offset(footX + drawL, pDiagY), 2f, pathEffect = dashSep)
        drawTextAnnotated("Uplift (No Contact)", (sepX + footX + drawL) / 2f, pDiagY + 14f,
            unsafeColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)
    }

    // Allowable bearing capacity horizontal reference line
    val qAllDrawH = maxPressureH * (soilBearingCapacityKpa / max(soilBearingCapacityKpa * 1.2, 1.0)).toFloat()
    val dashAll = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
    drawLine(allowableColor, Offset(footX - 15f, pDiagY + qAllDrawH), Offset(footX + drawL + 15f, pDiagY + qAllDrawH),
        1.5f, pathEffect = dashAll)
    drawTextAnnotated("q_all = ${soilBearingCapacityKpa.toInt()} kPa", footX + drawL + 20f, pDiagY + qAllDrawH + 3f,
        allowableColor, cfg.annotationTextSmall * 0.85f, bold = true)

    // Values of q_max and q_min
    drawTextAnnotated("q_max = ${maxPressureKpa.toInt()} kPa",
        footX - 4f, pDiagY + pMaxDrawH + 14f,
        if (maxPressureKpa <= soilBearingCapacityKpa) allowableColor else unsafeColor,
        cfg.annotationTextSmall, bold = true)

    if (isFullCompression) {
        drawTextAnnotated("q_min = ${minPressureKpa.toInt()} kPa",
            footX + drawL - 10f, pDiagY + pMinDrawH + 14f,
            pressureColor, cfg.annotationTextSmall * 0.9f)
    }

    // ── Dimensions ────────────────────────────────────────────────────────
    // Footing length (bottom)
    drawHorizontalDimension(
        footX, footX + drawL, pDiagY + pMaxDrawH + 24f,
        "L = ${safeL.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 16f, cfg
    )
    // Footing thickness (left)
    drawVerticalDimension(
        footY, footBotY, footX - 10f,
        "t = ${safeT.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, -20f, cfg
    )
}

// ============================================================================
// 3. PLAN VIEW & KERN (MIDDLE THIRD) DIAMOND ZONE
// ============================================================================

private fun DrawScope.drawFoundationPlanKern(
    left: Float, top: Float, width: Float, height: Float,
    safeL: Double, safeW: Double, safeColW: Double, safeColD: Double,
    eccentricityMm: Double, kernLimitMm: Double, isFullCompression: Boolean,
    concreteFill: Color, concreteStroke: Color, kernColor: Color, pressureColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    // Degenerate zone (tiny/negative canvas while scrolling or off-screen): nothing safe to draw.
    if (width <= 0f || height <= 0f) return
    val scale = min((width * 0.85f) / safeL.toFloat(), (height * 0.75f) / safeW.toFloat())
    val drawL = (safeL * scale).toFloat()
    val drawW = (safeW * scale).toFloat()
    val drawColW = (safeColW * scale).toFloat().coerceAtLeast(15f)
    val drawColD = (safeColD * scale).toFloat().coerceAtLeast(15f)

    val planX = left + (width - drawL) / 2f
    val planY = top + (height - drawW) / 2f - 10f
    val centerX = planX + drawL / 2f
    val centerY = planY + drawW / 2f

    // ── Foundation boundary ───────────────────────────────────────────────
    drawRect(color = concreteFill, topLeft = Offset(planX, planY), size = Size(drawL, drawW))
    drawHatchPattern(planX, planY, drawL, drawW, cfg.hatchSpacing * 1.5f, 45f, concreteStroke.copy(alpha = 0.3f))
    drawRect(color = concreteStroke, topLeft = Offset(planX, planY), size = Size(drawL, drawW),
        style = Stroke(cfg.strokeBold * 0.8f))

    // ── Centerlines ───────────────────────────────────────────────────────
    val dashCL = PathEffect.dashPathEffect(floatArrayOf(12f, 4f, 3f, 4f), 0f)
    drawLine(DrawingColorDefaults.CenterLine, Offset(planX - 15f, centerY), Offset(planX + drawL + 15f, centerY),
        1f, pathEffect = dashCL)
    drawLine(DrawingColorDefaults.CenterLine, Offset(centerX, planY - 15f), Offset(centerX, planY + drawW + 15f),
        1f, pathEffect = dashCL)

    // ── Kern (Diamond Middle Third Zone) ──────────────────────────────────
    // Kern diamond vertices: (±L/6, 0) and (0, ±W/6)
    val kernLx = (drawL / 6f)
    val kernWy = (drawW / 6f)

    val kernPath = Path().apply {
        moveTo(centerX, centerY - kernWy) // Top
        lineTo(centerX + kernLx, centerY) // Right
        lineTo(centerX, centerY + kernWy) // Bottom
        lineTo(centerX - kernLx, centerY) // Left
        close()
    }
    drawPath(kernPath, color = kernColor.copy(alpha = 0.25f))
    drawPath(kernPath, color = kernColor, style = Stroke(1.8f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f)))

    drawTextAnnotated("Kern Zone (e ≤ L/6)", centerX, centerY - kernWy - 8f,
        kernColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // ── Column Outline ────────────────────────────────────────────────────
    val colLeft = centerX - drawColW / 2f
    val colTop  = centerY - drawColD / 2f
    drawRect(color = Color(0xFF546E7A), topLeft = Offset(colLeft, colTop), size = Size(drawColW, drawColD))
    drawRect(color = concreteStroke, topLeft = Offset(colLeft, colTop), size = Size(drawColW, drawColD),
        style = Stroke(1.5f))

    // ── Center of Pressure (Point of resultant force P) ───────────────────
    val eDrawX = (eccentricityMm * scale).toFloat()
    val pressurePtX = centerX + eDrawX
    val pressurePtY = centerY

    // Draw eccentricity dimension bracket
    if (abs(eccentricityMm) > 1.0) {
        val bY = centerY - drawColD / 2f - 14f
        drawLine(pressureColor, Offset(centerX, bY), Offset(pressurePtX, bY), 1.2f)
        drawLine(pressureColor.copy(alpha = 0.5f), Offset(centerX, centerY), Offset(centerX, bY), 0.8f)
        drawLine(pressureColor.copy(alpha = 0.5f), Offset(pressurePtX, centerY), Offset(pressurePtX, bY), 0.8f)
        drawTextAnnotated("e = ${eccentricityMm.toInt()} mm", (centerX + pressurePtX) / 2f, bY - 4f,
            pressureColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)
    }

    // Point of Action mark
    val ptColor = if (isFullCompression) DrawingColorDefaults.SafeGreen else DrawingColorDefaults.UnsafeRed
    drawCircle(color = ptColor, radius = 6f, center = Offset(pressurePtX, pressurePtY))
    drawCircle(color = Color.White, radius = 2f, center = Offset(pressurePtX, pressurePtY))

    // Status label
    val compStatus = if (isFullCompression) "100% Bearing Area in Compression (Safe)" else "Partial Tension / Uplift (e > L/6)"
    drawTextAnnotated(compStatus, centerX, planY + drawW + 28f,
        ptColor, cfg.annotationTextSmall, center = true, bold = true)

    // ── Dimensions ────────────────────────────────────────────────────────
    drawHorizontalDimension(planX, planX + drawL, planY + drawW + 4f,
        "L = ${safeL.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 14f, cfg)
    drawVerticalDimension(planY, planY + drawW, planX + drawL + 6f,
        "B = ${safeW.toInt()} mm", dimColor, cfg.dimTextSize * 0.85f, 18f, cfg)
}

// ============================================================================
// 4. BOUSSINESQ STRESS BULB & SETTLEMENT PROFILE
// ============================================================================

private fun DrawScope.drawBoussinesqStressBulb(
    left: Float, top: Float, width: Float, height: Float,
    safeL: Double, safeT: Double, maxPressureKpa: Double, settlementMm: Double,
    soilBrown: Color, waterBlue: Color, pressureColor: Color,
    dimColor: Color, textColor: Color, cfg: DrawingDimensionsConfig
) {
    // Degenerate zone (tiny/negative canvas while scrolling or off-screen): nothing safe to draw.
    if (width <= 0f || height <= 0f) return
    val fW = (width * 0.65f).coerceAtLeast(80f)
    val fH = 14f
    val fX = left + (width - fW) / 2f
    val fY = top + 22f
    val fCenterX = fX + fW / 2f

    // Draw miniature foundation strip
    drawRect(color = DrawingColorDefaults.ConcreteFill, topLeft = Offset(fX, fY), size = Size(fW, fH))
    drawRect(color = DrawingColorDefaults.ConcreteTopGray, topLeft = Offset(fX, fY), size = Size(fW, fH),
        style = Stroke(1.5f))
    drawTextAnnotated("Foundation q_contact", fCenterX, fY - 5f,
        textColor, cfg.annotationTextSmall * 0.85f, center = true, bold = true)

    // Isobar lines (Boussinesq stress bulbs): 0.8q, 0.5q, 0.3q, 0.1q
    val isobars = listOf(
        Triple(0.8f, fW * 0.40f, fW * 0.35f), // (ratio, widthRadius, depthRadius)
        Triple(0.5f, fW * 0.65f, fW * 0.65f),
        Triple(0.3f, fW * 0.95f, fW * 1.10f),
        Triple(0.1f, fW * 1.30f, fW * 1.80f)
    )
    val bulbColors = listOf(
        Color(0xFFFF5252), // 0.8q
        Color(0xFFFFB74D), // 0.5q
        Color(0xFF64B5F6), // 0.3q
        Color(0xFF81C784)  // 0.1q (depth of significant stress ~ 2B)
    )

    isobars.forEachIndexed { i, (ratio, rx, ry) ->
        val bulbH = (ry * 0.55f).coerceAtMost(height - 60f)
        val bulbW = rx * 0.85f
        val color = bulbColors[i]

        val bulbPath = Path().apply {
            moveTo(fCenterX - bulbW / 2f, fY + fH)
            cubicTo(
                fCenterX - bulbW / 2f, fY + fH + bulbH * 0.7f,
                fCenterX + bulbW / 2f, fY + fH + bulbH * 0.7f,
                fCenterX + bulbW / 2f, fY + fH
            )
        }
        drawPath(bulbPath, color.copy(alpha = 0.12f))
        drawPath(bulbPath, color, style = Stroke(1.5f,
            pathEffect = if (i == 3) PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f) else null))

        // Isobar label
        drawTextAnnotated("σz = ${ratio}q", fCenterX + 2f, fY + fH + bulbH * 0.65f,
            color, cfg.annotationTextSmall * 0.75f, center = true, bold = true)
    }

    // Significant depth of influence indicator (z = 2B)
    drawTextAnnotated("Depth of Influence ≈ 2B (0.1q)", fX + fW + 12f, fY + fH + 45f,
        DrawingColorDefaults.ExtensionGray, cfg.annotationTextSmall * 0.75f)

    // Total settlement annotation badge
    val sColor = if (settlementMm <= 25.0) DrawingColorDefaults.SafeGreen else DrawingColorDefaults.UnsafeRed
    val badgeY = top + height - 26f
    drawRoundRect(
        color = sColor.copy(alpha = 0.2f),
        topLeft = Offset(left + 10f, badgeY),
        size = Size(width - 20f, 22f),
        cornerRadius = CornerRadius(4f)
    )
    drawTextAnnotated(
        "Estimated Settlement: ${String.format("%.1f", settlementMm)} mm (Allowable ≤ 25 mm ECP/ACI)",
        left + width / 2f, badgeY + 14f,
        sColor, cfg.annotationTextSmall, center = true, bold = true
    )
}

// ============================================================================
// 5. STABILITY & CAPACITY SUMMARY TABLE
// ============================================================================

private fun DrawScope.drawSoilStructureTable(
    left: Float, top: Float, width: Float, height: Float,
    maxPressureKpa: Double, minPressureKpa: Double, averagePressureKpa: Double, soilBearingCapacityKpa: Double,
    settlementMm: Double, overturningFS: Double, slidingFS: Double, bearingFS: Double,
    eccentricityMm: Double, kernLimitMm: Double, isFullCompression: Boolean,
    textColor: Color, cfg: DrawingDimensionsConfig
) {
    // Degenerate zone (tiny/negative canvas while scrolling or off-screen): nothing safe to draw.
    if (width <= 0f || height <= 0f) return
    val otPass = overturningFS >= 1.5
    val slPass = slidingFS >= 1.5
    val brPass = maxPressureKpa <= soilBearingCapacityKpa * 1.05
    val stPass = settlementMm <= 25.0

    val headers = listOf("Check", "Demand", "Limit", "F.S.", "Status")
    val rows = listOf(
        listOf("Bearing (q_max)", "${maxPressureKpa.toInt()} kPa", "${soilBearingCapacityKpa.toInt()} kPa",
            "%.2f".format(bearingFS), if (brPass) "SAFE ✓" else "FAIL ✗"),
        listOf("Overturning", "M_act", "M_res",
            "%.2f".format(overturningFS), if (otPass) "SAFE ✓" else "FAIL ✗"),
        listOf("Sliding", "H_act", "R_fric",
            "%.2f".format(slidingFS), if (slPass) "SAFE ✓" else "FAIL ✗"),
        listOf("Settlement", "${String.format("%.1f", settlementMm)}mm", "25 mm",
            "—", if (stPass) "OK ✓" else "HIGH ✗"),
        listOf("Kern (e ≤ L/6)", "${eccentricityMm.toInt()} mm", "${kernLimitMm.toInt()} mm",
            "—", if (isFullCompression) "100% Comp" else "Uplift ⚠")
    )

    val colFracs = listOf(0.32f, 0.20f, 0.20f, 0.14f, 0.14f)
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
}
