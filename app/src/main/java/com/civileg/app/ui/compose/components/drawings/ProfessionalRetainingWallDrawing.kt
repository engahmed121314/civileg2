package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// ============================================================================
// DRAWING-SPECIFIC COLORS
// (Shared colors from DrawingColors are used directly; only unique colors here)
// ============================================================================

private val SoilFill = Color(0x668D6E63)
private val PressureOrange = Color(0xFFFF5722)
private val GroundBrown = Color(0xFFA0522D)
private val BearingBlue = Color(0xFF2196F3)

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

/**
 * Professional Retaining Wall Engineering Drawing
 * Renders cross-section, earth pressure diagram, reinforcement detail,
 * stability checks, dimension lines, and reinforcement table.
 */
@Composable
fun ProfessionalRetainingWallDrawing(
    wallHeight: Double,
    wallTopThickness: Double,
    wallBottomThickness: Double,
    baseWidth: Double,
    baseThickness: Double,
    toeLength: Double,
    heelLength: Double,
    mainRebarDia: Double,
    mainRebarSpacing: Double,
    distRebarDia: Double,
    distRebarSpacing: Double,
    baseRebarDia: Double,
    baseRebarSpacing: Double,
    cover: Double,
    backfillAngle: Double = 30.0,
    hasKey: Boolean = false,
    keyDepth: Double = 0.0,
    fsOverturning: Double = 2.0,
    fsSliding: Double = 1.5,
    maxBearingPressure: Double = 0.0,
    allowableBearingPressure: Double = 200.0,
    // Enhanced design values
    activePressureCoeff: Double = 0.0,
    stemMoment: Double = 0.0,
    heelMoment: Double = 0.0,
    toeMoment: Double = 0.0,
    isSafe: Boolean = true,
    fcu: Double = 25.0,
    fy: Double = 360.0,
    isArabic: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
    ) {
        val cw = size.width
        val ch = size.height

        // ── Layout ──
        val mainLeft = 110f
        val mainRight = cw - 110f
        val mainTop = 60f
        val mainBottom = ch * 0.52f

        // ── Scaling ──
        val totalH = wallHeight + baseThickness + (if (hasKey) keyDepth else 0.0)
        val scaleX = (mainRight - mainLeft) / baseWidth.toFloat()
        val scaleY = (mainBottom - mainTop) / totalH.toFloat()
        val scale = min(scaleX, scaleY) * 0.85f

        val drawH = wallHeight.toFloat() * scale
        val drawTopT = wallTopThickness.toFloat() * scale
        val drawBotT = wallBottomThickness.toFloat() * scale
        val drawBaseW = baseWidth.toFloat() * scale
        val drawBaseT = baseThickness.toFloat() * scale
        val drawToe = toeLength.toFloat() * scale
        val drawHeel = heelLength.toFloat() * scale
        val drawKeyD = keyDepth.toFloat() * scale
        val drawKeyW = drawBotT * 0.6f
        val coverPx = cover.toFloat() * scale
        val tanAngle = tan(Math.toRadians(backfillAngle)).toFloat()

        // ── Positioning ──
        val stemTopLeftX = mainLeft + (mainRight - mainLeft - drawBaseW) / 2f + drawToe - drawBotT / 2f + (drawBotT - drawTopT) / 2f
        val baseLeft = stemTopLeftX + drawTopT / 2f - drawToe
        val baseRight = baseLeft + drawBaseW
        val stemTop = mainTop + 30f
        val baseTop = stemTop + drawH
        val baseBottom = baseTop + drawBaseT

        // ── Draw layers ──
        // 1. Backfill soil
        drawBackfillSoil(baseRight, stemTop, drawHeel, drawH, tanAngle, cw, baseTop)

        // 2. Wall cross-section
        drawWallCrossSection(
            stemTopLeftX, stemTop, drawH, drawTopT, drawBotT,
            baseLeft, baseTop, drawBaseW, drawBaseT,
            drawToe, drawHeel, hasKey, drawKeyD, drawKeyW
        )

        // 3. Reinforcement
        drawReinforcementDetail(
            stemTopLeftX, stemTop, drawH, drawTopT, drawBotT,
            baseLeft, baseTop, baseBottom, drawBaseW, drawBaseT,
            drawToe, drawHeel, coverPx, cover, scale,
            mainRebarDia, mainRebarSpacing,
            distRebarDia, distRebarSpacing,
            baseRebarDia, baseRebarSpacing
        )

        // 4. Earth pressure diagram
        drawEarthPressureDiagram(
            stemTopLeftX, stemTop, drawH, drawTopT, drawBotT, backfillAngle
        )

        // 5. Dimension lines (using shared DrawingUtils)
        drawDimensions(
            stemTopLeftX, stemTop, drawH, drawTopT, drawBotT,
            baseLeft, baseTop, baseBottom, drawBaseW, drawBaseT,
            drawToe, drawHeel, coverPx,
            wallHeight, wallTopThickness, wallBottomThickness,
            baseWidth, baseThickness, toeLength, heelLength, cover
        )

        // 6. Stability checks visual
        drawStabilityChecks(
            baseLeft, baseBottom, drawBaseW, drawBaseT, drawH,
            stemTop, wallHeight,
            fsOverturning, fsSliding, maxBearingPressure, allowableBearingPressure
        )

        // 7. Reinforcement table (using shared DrawingUtils)
        drawReinforcementTable(
            cw, ch, mainRebarDia, mainRebarSpacing,
            distRebarDia, distRebarSpacing,
            baseRebarDia, baseRebarSpacing, wallHeight
        )

        // ══════════════════════════════════════════════════════════
        //  8. DESIGN VALUES OVERLAY (top-right)
        // ══════════════════════════════════════════════════════════
        val designRows = mutableListOf<Pair<String, String>>()
        designRows.add("fcu" to "${"%.0f".format(fcu)} MPa")
        designRows.add("fy" to "${"%.0f".format(fy)} MPa")
        if (activePressureCoeff > 0) designRows.add("Ka" to "${"%.3f".format(activePressureCoeff)}")
        if (stemMoment > 0) designRows.add("Mu stem" to "${"%.1f".format(stemMoment)} kN.m/m")
        if (heelMoment > 0) designRows.add("Mu heel" to "${"%.1f".format(heelMoment)} kN.m/m")
        if (toeMoment > 0) designRows.add("Mu toe" to "${"%.1f".format(toeMoment)} kN.m/m")

        if (designRows.size > 2) {
            val ovW = 155f
            val ovH = 24f + designRows.size * 18f
            val ovX = cw - ovW - 12f
            val ovY = 44f
            drawRoundRect(
                color = Color(0xCC222244),
                topLeft = Offset(ovX, ovY),
                size = Size(ovW, ovH),
                cornerRadius = CornerRadius(6f)
            )
            drawRoundRect(
                color = Color(0x66AAAAAA),
                topLeft = Offset(ovX, ovY),
                size = Size(ovW, ovH),
                cornerRadius = CornerRadius(6f),
                style = Stroke(1f)
            )
            drawTextAnnotated("Design Values", ovX + ovW / 2f, ovY + 14f, DrawingColors.DimensionWhite, 12f, center = true, bold = true)
            designRows.forEachIndexed { idx, (label, value) ->
                val ry = ovY + 28f + idx * 18f
                drawTextAnnotated(label, ovX + 8f, ry + 10f, Color(0xFFAAAAAA), 10f)
                drawTextAnnotated(value, ovX + ovW - 8f, ry + 10f, DrawingColors.DimensionWhite, 10f, center = true)
            }
        }

        // SAFE / UNSAFE badge
        val badgeColor = if (isSafe) DrawingColors.SafeGreen else DrawingColors.UnsafeRed
        val badgeLabel = if (isSafe) "SAFE" else "UNSAFE"
        val badgePaint = android.graphics.Paint().apply {
            color = badgeColor.toArgb()
            textSize = 14f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        val badgeX = cw - 60f
        val badgeY = if (designRows.size > 2) 44f + 24f + designRows.size * 18f + 10f else 52f
        drawRoundRect(color = badgeColor.copy(alpha = 0.25f), topLeft = Offset(badgeX, badgeY), size = Size(52f, 22f), cornerRadius = CornerRadius(11f))
        drawRoundRect(color = badgeColor, topLeft = Offset(badgeX, badgeY), size = Size(52f, 22f), cornerRadius = CornerRadius(11f), style = Stroke(1.5f))
        drawContext.canvas.nativeCanvas.drawText(badgeLabel, badgeX + 26f, badgeY + 16f, badgePaint)
    }
}

// ============================================================================
// 1. BACKFILL SOIL
// ============================================================================

private fun DrawScope.drawBackfillSoil(
    baseRight: Float, stemTop: Float,
    drawHeel: Float, drawH: Float,
    tanAngle: Float, cw: Float, baseTop: Float
) {
    // Soil behind the wall
    val soilLeft = baseRight - drawHeel
    val soilRight = cw - 20f
    val soilTop = stemTop
    val soilBottom = baseTop

    // Angled backfill surface
    val surfaceDrop = (soilRight - soilLeft) * tanAngle
    val soilPath = Path().apply {
        moveTo(soilLeft, soilTop)
        lineTo(soilRight, soilTop + surfaceDrop)
        lineTo(soilRight, soilBottom)
        lineTo(soilLeft, soilBottom)
        close()
    }
    drawPath(path = soilPath, color = SoilFill)

    // Soil hatching using shared drawHatchPattern approach
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    nc.clipPath(android.graphics.Path().apply {
        moveTo(soilLeft, soilTop)
        lineTo(soilRight, soilTop + surfaceDrop)
        lineTo(soilRight, soilBottom)
        lineTo(soilLeft, soilBottom)
        close()
    })
    var hx = soilLeft - 200f
    while (hx < soilRight + 200f) {
        nc.drawLine(
            hx, soilTop - 50f, hx + 50f, soilBottom + 50f,
            android.graphics.Paint().apply {
                color = DrawingColors.SoilBrown.toArgb()
                strokeWidth = 1.2f
            }
        )
        hx += 14f
    }
    nc.restore()

    // Retained soil level line
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
    drawLine(
        color = GroundBrown, start = Offset(soilLeft - 30f, soilTop),
        end = Offset(soilRight + 10f, soilTop + surfaceDrop),
        strokeWidth = 2f, pathEffect = dashEffect
    )
    drawTextAnnotated("Ground Level", soilRight - 100f, soilTop + surfaceDrop - 8f, GroundBrown, 14f)
}

// ============================================================================
// 2. WALL CROSS-SECTION
// ============================================================================

private fun DrawScope.drawWallCrossSection(
    stemLeft: Float, stemTop: Float, drawH: Float,
    drawTopT: Float, drawBotT: Float,
    baseLeft: Float, baseTop: Float,
    drawBaseW: Float, drawBaseT: Float,
    drawToe: Float, drawHeel: Float,
    hasKey: Boolean, drawKeyD: Float, drawKeyW: Float
) {
    val dimWhite = DrawingColors.DimensionWhite

    // ── Base slab ──
    drawRect(color = DrawingColors.ConcreteGray,
        topLeft = Offset(baseLeft, baseTop),
        size = Size(drawBaseW, drawBaseT))
    drawRect(color = dimWhite.copy(alpha = 0.5f),
        topLeft = Offset(baseLeft, baseTop),
        size = Size(drawBaseW, drawBaseT),
        style = Stroke(width = 1.5f))
    // Use shared drawHatchPattern for rectangular base
    drawHatchPattern(baseLeft, baseTop, drawBaseW, drawBaseT,
        spacing = 18f, angleDeg = 45f, color = DrawingColors.HatchColor)

    // 3D top shading on base
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(DrawingColors.ConcreteTopGray, DrawingColors.ConcreteGray)
        ),
        topLeft = Offset(baseLeft, baseTop),
        size = Size(drawBaseW, drawBaseT * 0.3f)
    )

    // ── Stem (trapezoidal) ──
    val stemPath = Path().apply {
        moveTo(stemLeft, stemTop)
        lineTo(stemLeft + drawTopT, stemTop)
        lineTo(stemLeft + drawBotT, stemTop + drawH)
        lineTo(stemLeft, stemTop + drawH)
        close()
    }
    drawPath(path = stemPath, color = DrawingColors.ConcreteGray)
    drawPath(path = stemPath, color = dimWhite.copy(alpha = 0.5f),
        style = Stroke(width = 1.5f))
    // Hatch for trapezoidal shape (uses clipPath - cannot use rect-based drawHatchPattern)
    drawConcreteHatchPath(stemPath)

    // 3D shading on stem
    drawPath(
        path = stemPath,
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(DrawingColors.ConcreteSideGray, DrawingColors.ConcreteTopGray, DrawingColors.ConcreteSideGray)
        )
    )
    // Re-draw outline
    drawPath(path = stemPath, color = dimWhite.copy(alpha = 0.5f),
        style = Stroke(width = 1.5f))

    // ── Key (if present) ──
    if (hasKey && drawKeyD > 0) {
        val keyLeft = baseLeft + drawBaseW * 0.35f
        val keyPath = Path().apply {
            moveTo(keyLeft, baseTop + drawBaseT)
            lineTo(keyLeft + drawKeyW, baseTop + drawBaseT)
            lineTo(keyLeft + drawKeyW, baseTop + drawBaseT + drawKeyD)
            lineTo(keyLeft, baseTop + drawBaseT + drawKeyD)
            close()
        }
        drawPath(path = keyPath, color = DrawingColors.ConcreteSideGray)
        drawPath(path = keyPath, color = dimWhite.copy(alpha = 0.5f),
            style = Stroke(width = 1.5f))
    }

    // ── Ground line in front ──
    val groundY = baseTop + drawBaseT
    drawLine(color = GroundBrown,
        start = Offset(baseLeft - 40f, groundY),
        end = Offset(baseLeft + drawToe + 20f, groundY),
        strokeWidth = 2f)
}

// ============================================================================
// 3. REINFORCEMENT DETAIL
// ============================================================================

private fun DrawScope.drawReinforcementDetail(
    stemLeft: Float, stemTop: Float, drawH: Float,
    drawTopT: Float, drawBotT: Float,
    baseLeft: Float, baseTop: Float, baseBottom: Float,
    drawBaseW: Float, drawBaseT: Float,
    drawToe: Float, drawHeel: Float,
    coverPx: Float, coverC: Double, scale: Float,
    mainRebarDia: Double, mainRebarSpacing: Double,
    distRebarDia: Double, distRebarSpacing: Double,
    baseRebarDia: Double, baseRebarSpacing: Double
) {
    val stemBottom = stemTop + drawH

    // ── Main vertical bars in stem (back face — tension side) ──
    val vSpacingPx = max(mainRebarSpacing.toFloat() * scale, 18f)
    var vy = stemTop + coverPx + 8f
    while (vy < stemBottom - coverPx) {
        // Calculate wall thickness at this height (linear interpolation)
        val frac = (vy - stemTop) / drawH
        val localT = drawTopT + (drawBotT - drawTopT) * frac
        val barX = stemLeft + localT - coverPx

        drawLine(
            color = DrawingColors.RebarBlue,
            start = Offset(barX, vy),
            end = Offset(barX, min(vy + vSpacingPx * 0.7f, stemBottom - coverPx)),
            strokeWidth = 2.5f
        )
        vy += vSpacingPx
    }

    // ── Horizontal distribution bars (front face, lighter) ──
    val hSpacingPx = max(distRebarSpacing.toFloat() * scale, 18f)
    var hx = stemTop + coverPx
    while (hx < stemBottom - coverPx) {
        val frac = (hx - stemTop) / drawH
        val localT = drawTopT + (drawBotT - drawTopT) * frac
        drawLine(
            color = DrawingColors.TopRebarBlue,
            start = Offset(stemLeft + coverPx, hx),
            end = Offset(stemLeft + localT - coverPx, hx),
            strokeWidth = 1.5f
        )
        hx += hSpacingPx
    }

    // ── Base bottom bars (blue) — full width tension side ──
    val bSpacingPx = max(baseRebarSpacing.toFloat() * scale, 18f)
    var bx = baseLeft + coverPx
    while (bx < baseLeft + drawBaseW - coverPx) {
        drawLine(
            color = DrawingColors.RebarBlue,
            start = Offset(bx, baseBottom - coverPx),
            end = Offset(min(bx + bSpacingPx * 0.7f, baseLeft + drawBaseW - coverPx), baseBottom - coverPx),
            strokeWidth = 2.5f
        )
        bx += bSpacingPx
    }

    // ── Base top bars (red) — near stem ──
    val stemBaseRight = stemLeft + drawBotT
    var tx = stemBaseRight - coverPx - 30f
    while (tx > baseLeft + coverPx) {
        drawLine(
            color = DrawingColors.SecondaryRed,
            start = Offset(tx, baseTop + coverPx),
            end = Offset(min(tx + 15f, stemBaseRight - coverPx), baseTop + coverPx),
            strokeWidth = 2f
        )
        tx -= 20f
    }

    // ── Cover dimension indicators ──
    // Stem back cover
    drawLine(color = DrawingColors.ExtensionGray.copy(alpha = 0.6f),
        start = Offset(stemLeft + drawBotT - coverPx, stemTop + drawH - 10f),
        end = Offset(stemLeft + drawBotT, stemTop + drawH - 10f), strokeWidth = 0.8f)
    drawTextAnnotated("${(coverC * 1000).toInt()}mm", stemLeft + drawBotT - coverPx - 20f,
        stemTop + drawH - 6f, DrawingColors.ExtensionGray, 11f)
}

// ============================================================================
// 4. EARTH PRESSURE DIAGRAM
// ============================================================================

private fun DrawScope.drawEarthPressureDiagram(
    stemLeft: Float, stemTop: Float, drawH: Float,
    drawTopT: Float, drawBotT: Float, backfillAngle: Double
) {
    val stemBackX = stemLeft + drawBotT
    val diagramW = 50f

    // Ka coefficient (Rankine) using actual backfill friction angle
    // Ka = (1 - sin φ) / (1 + sin φ) = tan²(45° - φ/2)
    val ka = max(0.25, (1.0 - sin(Math.toRadians(backfillAngle))) / (1.0 + sin(Math.toRadians(backfillAngle))))

    // Triangular active earth pressure
    val pressurePath = Path().apply {
        moveTo(stemBackX + 5f, stemTop)
        lineTo(stemBackX + 5f + diagramW, stemTop + drawH)
        lineTo(stemBackX + 5f, stemTop + drawH)
        close()
    }
    drawPath(path = pressurePath, color = PressureOrange.copy(alpha = 0.25f))
    drawPath(path = pressurePath, color = PressureOrange, style = Stroke(width = 1.5f))

    // Resultant force arrow (at H/3 from base = 2H/3 from top)
    val resultY = stemTop + drawH * 2f / 3f
    val arrowLen = diagramW * 0.6f
    drawLine(
        color = PressureOrange,
        start = Offset(stemBackX + 8f + arrowLen, resultY),
        end = Offset(stemBackX + 8f, resultY),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round
    )
    drawArrowHead(stemBackX + 8f, resultY, -1f, PressureOrange, vertical = false)

    // Labels
    drawTextAnnotated("Pa", stemBackX + 10f + arrowLen, resultY - 6f, PressureOrange, 16f)
    drawTextAnnotated("Active", stemBackX + diagramW + 14f, stemTop + drawH / 2f, PressureOrange, 14f)
    drawTextAnnotated("Ka = ${"%.2f".format(ka)}", stemBackX + 10f, stemTop + drawH + 40f,
        PressureOrange, 13f)

    // Zero at top
    drawTextAnnotated("0", stemBackX + 12f, stemTop - 4f, PressureOrange.copy(alpha = 0.6f), 12f)
}

// ============================================================================
// 5. DIMENSION LINES (using shared DrawingUtils)
// ============================================================================

private fun DrawScope.drawDimensions(
    stemLeft: Float, stemTop: Float, drawH: Float,
    drawTopT: Float, drawBotT: Float,
    baseLeft: Float, baseTop: Float, baseBottom: Float,
    drawBaseW: Float, drawBaseT: Float,
    drawToe: Float, drawHeel: Float, coverPx: Float,
    wallHeight: Double, wallTopThickness: Double, wallBottomThickness: Double,
    baseWidth: Double, baseThickness: Double, toeLength: Double, heelLength: Double, cover: Double
) {
    val dimColor = DrawingColors.DimensionWhite
    val dimTextSize = 13f

    // Wall height (left side, vertical)
    drawVerticalDimension(
        y1 = stemTop, y2 = stemTop + drawH,
        x = stemLeft, text = "H=${wallHeight.toInt()}m",
        color = dimColor, textSize = dimTextSize, offset = 30f
    )

    // Top thickness (above stem, horizontal)
    drawHorizontalDimension(
        x1 = stemLeft, x2 = stemLeft + drawTopT,
        y = stemTop, text = "t\u2081=${"%.0f".format(wallTopThickness * 100)}cm",
        color = dimColor, textSize = dimTextSize, offset = 18f
    )

    // Bottom thickness (at base level, horizontal)
    drawHorizontalDimension(
        x1 = stemLeft, x2 = stemLeft + drawBotT,
        y = baseTop, text = "t\u2082=${"%.0f".format(wallBottomThickness * 100)}cm",
        color = dimColor, textSize = dimTextSize, offset = 8f
    )

    // Base width (below base, horizontal)
    drawHorizontalDimension(
        x1 = baseLeft, x2 = baseLeft + drawBaseW,
        y = baseBottom, text = "B=${"%.1f".format(baseWidth)}m",
        color = dimColor, textSize = dimTextSize, offset = 18f
    )

    // Toe length (below base, second row)
    drawHorizontalDimension(
        x1 = baseLeft, x2 = baseLeft + drawToe,
        y = baseBottom, text = "Toe=${"%.1f".format(toeLength)}m",
        color = dimColor, textSize = dimTextSize * 0.9f, offset = 38f
    )

    // Heel length (below base, second row)
    drawHorizontalDimension(
        x1 = baseLeft + drawBaseW - drawHeel, x2 = baseLeft + drawBaseW,
        y = baseBottom, text = "Heel=${"%.1f".format(heelLength)}m",
        color = dimColor, textSize = dimTextSize * 0.9f, offset = 38f
    )

    // Base thickness (right side, vertical)
    drawVerticalDimension(
        y1 = baseTop, y2 = baseBottom,
        x = baseLeft + drawBaseW, text = "tb=${"%.0f".format(baseThickness * 100)}cm",
        color = dimColor, textSize = dimTextSize, offset = 25f
    )
}

// ============================================================================
// 6. STABILITY CHECKS VISUAL
// ============================================================================

private fun DrawScope.drawStabilityChecks(
    baseLeft: Float, baseBottom: Float, drawBaseW: Float, drawBaseT: Float,
    drawH: Float, stemTop: Float, wallHeight: Double,
    fsOverturning: Double, fsSliding: Double,
    maxBearingPressure: Double, allowableBearingPressure: Double
) {
    val checkX = 20f
    val checkY = baseBottom + 60f
    val lineH = 22f
    val dimWhite = DrawingColors.DimensionWhite

    val otPass = fsOverturning >= 1.5
    val slidePass = fsSliding >= 1.5
    val bearingPass = maxBearingPressure <= allowableBearingPressure && maxBearingPressure > 0

    // Title
    drawTextAnnotated("STABILITY CHECKS", checkX, checkY, dimWhite, 17f)
    // F.S. Overturning
    val otColor = if (otPass) DrawingColors.SafeGreen else DrawingColors.UnsafeRed
    drawTextAnnotated("F.S.(O.T.) = ${"%.2f".format(fsOverturning)}", checkX, checkY + lineH, otColor, 15f)
    drawTextAnnotated(if (otPass) "✓ OK" else "✗ FAIL", checkX + 160f, checkY + lineH, otColor, 14f)

    // Sliding check
    val slideColor = if (slidePass) DrawingColors.SafeGreen else DrawingColors.UnsafeRed
    drawTextAnnotated("F.S.(Slide) = ${"%.2f".format(fsSliding)}", checkX, checkY + lineH * 2, slideColor, 15f)
    drawTextAnnotated(if (slidePass) "✓ OK" else "✗ FAIL", checkX + 160f, checkY + lineH * 2, slideColor, 14f)

    // Bearing check
    val bpColor = if (bearingPass) DrawingColors.SafeGreen else DrawingColors.UnsafeRed
    drawTextAnnotated("σ_max = ${"%.1f".format(maxBearingPressure)} kPa", checkX, checkY + lineH * 3, bpColor, 15f)
    drawTextAnnotated(if (bearingPass) "✓ OK" else "✗ FAIL", checkX + 160f, checkY + lineH * 3, bpColor, 14f)

    // Bearing pressure diagram under base (trapezoidal)
    val bpLeft = baseLeft + drawBaseW * 0.05f
    val bpRight = baseLeft + drawBaseW * 0.95f
    val bpBaseY = baseBottom + 145f
    val bpMaxH = 20f
    val bpMinH = 6f

    // Trapezoidal pressure
    val bpPath = Path().apply {
        moveTo(bpLeft, bpBaseY)
        lineTo(bpRight, bpBaseY)
        lineTo(bpRight, bpBaseY + bpMaxH)
        lineTo(bpLeft, bpBaseY + bpMinH)
        close()
    }
    drawPath(path = bpPath, color = BearingBlue.copy(alpha = 0.25f))
    drawPath(path = bpPath, color = BearingBlue, style = Stroke(width = 1f))

    // Labels
    drawTextAnnotated("σ_min", bpLeft - 8f, bpBaseY + bpMinH + 14f, BearingBlue.copy(alpha = 0.7f), 10f)
    drawTextAnnotated("σ_max", bpRight - 12f, bpBaseY + bpMaxH + 14f, BearingBlue.copy(alpha = 0.7f), 10f)
}

// ============================================================================
// 7. REINFORCEMENT TABLE (using shared DrawingUtils)
// ============================================================================

private fun DrawScope.drawReinforcementTable(
    cw: Float, ch: Float,
    mainRebarDia: Double, mainRebarSpacing: Double,
    distRebarDia: Double, distRebarSpacing: Double,
    baseRebarDia: Double, baseRebarSpacing: Double,
    wallHeight: Double
) {
    val tableLeft = 16f
    val tableTop = ch * 0.68f
    val tableW = cw - 32f
    val rowH = 24f
    val headerH = 28f
    val colWidths = floatArrayOf(
        tableW * 0.20f,  // Direction
        tableW * 0.22f,  // Location
        tableW * 0.14f,  // Dia
        tableW * 0.22f,  // Spacing
        tableW * 0.22f   // Length
    )

    // Title
    drawTextAnnotated("REINFORCEMENT SCHEDULE", tableLeft, tableTop - 6f, DrawingColors.DimensionWhite, 20f)

    // Header
    drawRect(color = DrawingColors.TableHeaderBg, topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH))
    val headers = arrayOf("Direction", "Location", "Dia.", "Spacing", "Length")
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(headers[i], cx + 6f, tableTop + headerH / 2f + 6f,
            DrawingColors.DimensionWhite, 15f)
        cx += colWidths[i]
    }

    // Separator
    drawLine(color = DrawingColors.ExtensionGray.copy(alpha = 0.3f),
        start = Offset(tableLeft, tableTop + headerH),
        end = Offset(tableLeft + tableW, tableTop + headerH), strokeWidth = 0.5f)

    // Row 1: Main vertical stem bars
    val r1Y = tableTop + headerH
    drawRect(color = DrawingColors.TableRowAlt, topLeft = Offset(tableLeft, r1Y), size = Size(tableW, rowH))
    val row1 = arrayOf("Vertical (Main)", "Soil Side", "Ø${(mainRebarDia * 1000).toInt()}",
        "@${(mainRebarSpacing * 1000).toInt()}mm", "H+Ld")
    cx = tableLeft
    for (i in row1.indices) {
        drawTextAnnotated(row1[i], cx + 6f, r1Y + rowH / 2f + 5f, DrawingColors.RebarBlue, 13f)
        cx += colWidths[i]
    }
    drawLine(color = DrawingColors.ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r1Y + rowH),
        end = Offset(tableLeft + tableW, r1Y + rowH), strokeWidth = 0.5f)

    // Row 2: Horizontal distribution bars
    val r2Y = r1Y + rowH
    val row2 = arrayOf("Horizontal (Dist.)", "Soil Side", "Ø${(distRebarDia * 1000).toInt()}",
        "@${(distRebarSpacing * 1000).toInt()}mm", "L")
    cx = tableLeft
    for (i in row2.indices) {
        drawTextAnnotated(row2[i], cx + 6f, r2Y + rowH / 2f + 5f, DrawingColors.TopRebarBlue, 13f)
        cx += colWidths[i]
    }
    drawLine(color = DrawingColors.ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r2Y + rowH),
        end = Offset(tableLeft + tableW, r2Y + rowH), strokeWidth = 0.5f)

    // Row 3: Base bottom bars
    val r3Y = r2Y + rowH
    drawRect(color = DrawingColors.TableRowAlt, topLeft = Offset(tableLeft, r3Y), size = Size(tableW, rowH))
    val row3 = arrayOf("Horiz. (Base)", "Bottom", "Ø${(baseRebarDia * 1000).toInt()}",
        "@${(baseRebarSpacing * 1000).toInt()}mm", "B")
    cx = tableLeft
    for (i in row3.indices) {
        drawTextAnnotated(row3[i], cx + 6f, r3Y + rowH / 2f + 5f, DrawingColors.RebarBlue.copy(alpha = 0.8f), 13f)
        cx += colWidths[i]
    }
    drawLine(color = DrawingColors.ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r3Y + rowH),
        end = Offset(tableLeft + tableW, r3Y + rowH), strokeWidth = 0.5f)

    // Row 4: Base top bars near stem
    val r4Y = r3Y + rowH
    val row4 = arrayOf("Horiz. (Base)", "Top", "Ø${(distRebarDia * 1000).toInt()}",
        "@${(distRebarSpacing * 1000).toInt()}mm", "Heel")
    cx = tableLeft
    for (i in row4.indices) {
        drawTextAnnotated(row4[i], cx + 6f, r4Y + rowH / 2f + 5f, DrawingColors.SecondaryRed, 13f)
        cx += colWidths[i]
    }

    // Column separators
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
        drawLine(color = DrawingColors.ExtensionGray.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop),
            end = Offset(sepX, r4Y + rowH), strokeWidth = 0.5f)
    }

    // Table border
    drawRect(color = DrawingColors.ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH + rowH * 4),
        style = Stroke(width = 1f))
}

// ============================================================================
// SHARED HELPERS (drawing-specific, not available in DrawingUtils)
// ============================================================================

/**
 * Hatching for non-rectangular paths (e.g., trapezoidal stem).
 * Uses native canvas clipPath — needed because [DrawScope.drawHatchPattern]
 * only supports rectangular areas.
 */
private fun DrawScope.drawConcreteHatchPath(path: Path) {
    val bounds = path.getBounds()
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    val androidPath = android.graphics.Path()
    path.asAndroidPath().let { androidPath.set(it) }
    nc.clipPath(androidPath)
    var i = bounds.left - bounds.height
    while (i < bounds.right + bounds.height) {
        nc.drawLine(
            i, bounds.top, i + bounds.height, bounds.bottom,
            android.graphics.Paint().apply {
                color = DrawingColors.HatchColor.toArgb()
                strokeWidth = 0.6f
            }
        )
        i += 18f
    }
    nc.restore()
}

private fun DrawScope.drawArrowHead(
    x: Float, y: Float, direction: Float,
    color: Color, vertical: Boolean = false
) {
    val arrowSize = 6f
    val path = Path()
    if (!vertical) {
        path.moveTo(x, y)
        path.lineTo(x - direction * arrowSize, y - arrowSize * 0.5f)
        path.lineTo(x - direction * arrowSize, y + arrowSize * 0.5f)
        path.close()
    } else {
        path.moveTo(x, y)
        path.lineTo(x - arrowSize * 0.5f, y - direction * arrowSize)
        path.lineTo(x + arrowSize * 0.5f, y - direction * arrowSize)
        path.close()
    }
    drawPath(path = path, color = color)
}
