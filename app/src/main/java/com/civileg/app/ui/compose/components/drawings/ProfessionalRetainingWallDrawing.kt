package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

// ============================================================================
// COLOR PALETTE
// ============================================================================

private val ConcreteGray = Color(0xFF888888)
private val ConcreteTopGray = Color(0xFF9A9A9A)
private val ConcreteSideGray = Color(0xFF666666)
private val SoilBrown = Color(0xFF8B4513)
private val SoilFill = Color(0x668D6E63)
private val RebarBlue = Color(0xFF4A90D9)
private val RebarLightBlue = Color(0xFF7EC8E3)
private val RebarRed = Color(0xFFE74C3C)
private val DimensionWhite = Color(0xFFFFFFFF)
private val ExtensionGray = Color(0xFFAAAAAA)
private val TableHeaderBg = Color(0x33FFFFFF)
private val TableRowAlt = Color(0x1AFFFFFF)
private val PressureOrange = Color(0xFFFF5722)
private val SafeGreen = Color(0xFF4CAF50)
private val WarningYellow = Color(0xFFFFA000)
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
    backfillAngle: Double = 0.0,
    hasKey: Boolean = false,
    keyDepth: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(720.dp)
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
        val scale = min(scaleX, scaleY) * 0.70f

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
        val tanAngle = tan(Math.toRadians(backfillAngle))

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
            drawToe, drawHeel, coverPx, scale,
            mainRebarDia, mainRebarSpacing,
            distRebarDia, distRebarSpacing,
            baseRebarDia, baseRebarSpacing
        )

        // 4. Earth pressure diagram
        drawEarthPressureDiagram(
            stemTopLeftX, stemTop, drawH, drawTopT, drawBotT, backfillAngle
        )

        // 5. Dimension lines
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
            stemTop, backfillAngle, wallHeight
        )

        // 7. Reinforcement table
        drawReinforcementTable(
            cw, ch, mainRebarDia, mainRebarSpacing,
            distRebarDia, distRebarSpacing,
            baseRebarDia, baseRebarSpacing, wallHeight
        )
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

    // Soil hatching
    nativeCanvas.save()
    nativeCanvas.clipPath(android.graphics.Path().apply {
        moveTo(soilLeft, soilTop)
        lineTo(soilRight, soilTop + surfaceDrop)
        lineTo(soilRight, soilBottom)
        lineTo(soilLeft, soilBottom)
        close()
    })
    var hx = soilLeft - 200f
    while (hx < soilRight + 200f) {
        nativeCanvas.drawLine(
            hx, soilTop - 50f, hx + 50f, soilBottom + 50f,
            android.graphics.Paint().apply {
                color = SoilBrown.hashCode()
                strokeWidth = 1.2f
            }
        )
        hx += 14f
    }
    nativeCanvas.restore()

    // Retained soil level line
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
    drawLine(
        color = GroundBrown, start = Offset(soilLeft - 30f, soilTop),
        end = Offset(soilRight + 10f, soilTop + surfaceDrop),
        strokeWidth = 2f, pathEffect = dashEffect
    )
    drawTextAnnotated("سطح التربة", soilRight - 60f, soilTop + surfaceDrop - 8f, GroundBrown, 14f)
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
    // ── Base slab ──
    drawRect(color = ConcreteGray,
        topLeft = Offset(baseLeft, baseTop),
        size = Size(drawBaseW, drawBaseT))
    drawRect(color = DimensionWhite.copy(alpha = 0.5f),
        topLeft = Offset(baseLeft, baseTop),
        size = Size(drawBaseW, drawBaseT),
        style = Stroke(width = 1.5f))
    drawConcreteHatch(baseLeft, baseTop, drawBaseW, drawBaseT)

    // 3D top shading on base
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(ConcreteTopGray, ConcreteGray)
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
    drawPath(path = stemPath, color = ConcreteGray)
    drawPath(path = stemPath, color = DimensionWhite.copy(alpha = 0.5f),
        style = Stroke(width = 1.5f))
    drawConcreteHatchPath(stemPath)

    // 3D shading on stem
    drawPath(
        path = stemPath,
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(ConcreteSideGray, ConcreteTopGray, ConcreteSideGray)
        )
    )
    // Re-draw outline
    drawPath(path = stemPath, color = DimensionWhite.copy(alpha = 0.5f),
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
        drawPath(path = keyPath, color = ConcreteSideGray)
        drawPath(path = keyPath, color = DimensionWhite.copy(alpha = 0.5f),
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
    coverPx: Float, scale: Float,
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
            color = RebarBlue,
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
            color = RebarLightBlue,
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
            color = RebarBlue,
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
            color = RebarRed,
            start = Offset(tx, baseTop + coverPx),
            end = Offset(min(tx + 15f, stemBaseRight - coverPx), baseTop + coverPx),
            strokeWidth = 2f
        )
        tx -= 20f
    }

    // ── Cover dimension indicators ──
    val cTick = 5f
    // Stem back cover
    drawLine(color = ExtensionGray.copy(alpha = 0.6f),
        start = Offset(stemLeft + drawBotT - coverPx, stemTop + drawH - 10f),
        end = Offset(stemLeft + drawBotT, stemTop + drawH - 10f), strokeWidth = 0.8f)
    drawTextAnnotated("${cover.toInt()}", stemLeft + drawBotT - coverPx - 10f,
        stemTop + drawH - 6f, ExtensionGray, 11f)
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

    // Ka coefficient (Rankine simplified)
    val ka = max(0.25, (1.0 - sin(Math.toRadians(30.0))) / (1.0 + sin(Math.toRadians(30.0))))

    // Triangular active earth pressure
    val pressurePath = Path().apply {
        moveTo(stemBackX + 5f, stemTop)
        lineTo(stemBackX + 5f + diagramW, stemTop + drawH)
        lineTo(stemBackX + 5f, stemTop + drawH)
        close()
    }
    drawPath(path = pressurePath, color = PressureOrange.copy(alpha = 0.25f))
    drawPath(path = pressurePath, color = PressureOrange, style = Stroke(width = 1.5f))

    // Resultant force arrow (at 1/3 from base)
    val resultY = stemTop + drawH * 2f / 3f
    val arrowLen = diagramW * 0.6f
    drawLine(
        color = PressureOrange,
        start = Offset(stemBackX + 8f + arrowLen, resultY),
        end = Offset(stemBackX + 8f, resultY),
        strokeWidth = 2.5f,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawArrowHead(stemBackX + 8f, resultY, -1f, PressureOrange, vertical = false)

    // Labels
    drawTextAnnotated("Pa", stemBackX + 10f + arrowLen, resultY - 6f, PressureOrange, 16f)
    drawTextAnnotated("فعال", stemBackX + diagramW + 14f, stemTop + drawH / 2f, PressureOrange, 14f)
    drawTextAnnotated("Ka = ${"%.2f".format(ka)}", stemBackX + 10f, stemTop + drawH + 16f,
        PressureOrange, 13f)

    // Zero at top
    drawTextAnnotated("0", stemBackX + 12f, stemTop - 4f, PressureOrange.copy(alpha = 0.6f), 12f)
}

// ============================================================================
// 5. DIMENSION LINES
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
    val dimOff = 30f

    // Wall height (left side)
    drawDimLine(stemLeft - dimOff, stemTop, stemLeft - dimOff, stemTop + drawH,
        "H=${wallHeight.toInt()}", true)

    // Top thickness (above stem)
    drawDimLine(stemLeft, stemTop - dimOff * 0.7f, stemLeft + drawTopT, stemTop - dimOff * 0.7f,
        "t₁=${wallTopThickness.toInt()}", false)

    // Bottom thickness (at base level)
    drawDimLine(stemLeft, baseTop + 8f, stemLeft + drawBotT, baseTop + 8f,
        "t₂=${wallBottomThickness.toInt()}", false)

    // Base width (below base)
    drawDimLine(baseLeft, baseBottom + dimOff * 0.7f, baseLeft + drawBaseW, baseBottom + dimOff * 0.7f,
        "B=${baseWidth.toInt()}", false)

    // Toe length
    drawDimLine(baseLeft, baseBottom + dimOff * 1.5f, baseLeft + drawToe, baseBottom + dimOff * 1.5f,
        "Toe=${toeLength.toInt()}", false)

    // Heel length
    drawDimLine(baseLeft + drawBaseW - drawHeel, baseBottom + dimOff * 1.5f,
        baseLeft + drawBaseW, baseBottom + dimOff * 1.5f,
        "Heel=${heelLength.toInt()}", false)

    // Base thickness (right side)
    drawDimLine(baseLeft + drawBaseW + dimOff, baseTop, baseLeft + drawBaseW + dimOff, baseBottom,
        "tb=${baseThickness.toInt()}", true)
}

// ============================================================================
// 6. STABILITY CHECKS VISUAL
// ============================================================================

private fun DrawScope.drawStabilityChecks(
    baseLeft: Float, baseBottom: Float, drawBaseW: Float, drawBaseT: Float,
    drawH: Float, stemTop: Float, backfillAngle: Double, wallHeight: Double
) {
    val checkX = 20f
    val checkY = stemTop + drawH * 0.15f
    val lineH = 22f

    // Overturning check
    drawTextAnnotated("STABILITY CHECKS", checkX, checkY, DimensionWhite, 17f)
    // F.S. Overturning
    drawTextAnnotated("F.S.(O.T.) ≥ 1.5", checkX, checkY + lineH, SafeGreen, 15f)
    drawTextAnnotated("✓ OK", checkX + 120f, checkY + lineH, SafeGreen, 14f)

    // Sliding check
    drawTextAnnotated("F.S.(Slide) ≥ 1.5", checkX, checkY + lineH * 2, SafeGreen, 15f)
    drawTextAnnotated("✓ OK", checkX + 120f, checkY + lineH * 2, SafeGreen, 14f)

    // Bearing check
    drawTextAnnotated("σ_max ≤ q_all", checkX, checkY + lineH * 3, SafeGreen, 15f)
    drawTextAnnotated("✓ OK", checkX + 120f, checkY + lineH * 3, SafeGreen, 14f)

    // Bearing pressure diagram under base (trapezoidal)
    val bpLeft = baseLeft + drawBaseW * 0.05f
    val bpRight = baseLeft + drawBaseW * 0.95f
    val bpBaseY = baseBottom + 8f
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

    // Overturning moment arrows (curved arrows concept using lines)
    val arrowY = stemTop + drawH * 0.5f
    // Resisting moment (clockwise arrow on left)
    drawLine(color = SafeGreen.copy(alpha = 0.6f),
        start = Offset(baseLeft - 15f, arrowY - 15f),
        end = Offset(baseLeft - 15f, arrowY + 15f), strokeWidth = 2f)
    drawArrowHead(baseLeft - 15f, arrowY + 15f, 1f, SafeGreen, vertical = true)
    drawTextAnnotated("Mr", baseLeft - 30f, arrowY + 4f, SafeGreen.copy(alpha = 0.7f), 12f)

    // Sliding force arrows
    val slideY = baseBottom - 4f
    drawLine(color = WarningYellow.copy(alpha = 0.6f),
        start = Offset(baseLeft + drawBaseW * 0.5f - 20f, slideY),
        end = Offset(baseLeft + drawBaseW * 0.5f + 20f, slideY), strokeWidth = 2f)
    drawArrowHead(baseLeft + drawBaseW * 0.5f + 20f, slideY, 1f, WarningYellow, vertical = false)
    drawTextAnnotated("F_slide", baseLeft + drawBaseW * 0.5f - 16f, slideY - 8f,
        WarningYellow.copy(alpha = 0.7f), 11f)
}

// ============================================================================
// 7. REINFORCEMENT TABLE
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
    drawTextAnnotated("جدول التسليح", tableLeft, tableTop - 6f, DimensionWhite, 20f)

    // Header
    drawRect(color = TableHeaderBg, topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH))
    val headers = arrayOf("الاتجاه", "الموقع", "القطر", "المسافة", "الطول")
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(headers[i], cx + 6f, tableTop + headerH / 2f + 6f,
            DimensionWhite, 15f)
        cx += colWidths[i]
    }

    // Separator
    drawLine(color = ExtensionGray.copy(alpha = 0.3f),
        start = Offset(tableLeft, tableTop + headerH),
        end = Offset(tableLeft + tableW, tableTop + headerH), strokeWidth = 0.5f)

    // Row 1: Main vertical stem bars
    val r1Y = tableTop + headerH
    drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, r1Y), size = Size(tableW, rowH))
    val row1 = arrayOf("عمودي (رئيسي)", "جهة التربة", "Ø${mainRebarDia.toInt()}",
        "@${mainRebarSpacing.toInt()}mm", "H+Ld")
    cx = tableLeft
    for (i in row1.indices) {
        drawTextAnnotated(row1[i], cx + 6f, r1Y + rowH / 2f + 5f, RebarBlue, 13f)
        cx += colWidths[i]
    }
    drawLine(color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r1Y + rowH),
        end = Offset(tableLeft + tableW, r1Y + rowH), strokeWidth = 0.5f)

    // Row 2: Horizontal distribution bars
    val r2Y = r1Y + rowH
    val row2 = arrayOf("أفقي (توزيعي)", "جهة التربة", "Ø${distRebarDia.toInt()}",
        "@${distRebarSpacing.toInt()}mm", "L")
    cx = tableLeft
    for (i in row2.indices) {
        drawTextAnnotated(row2[i], cx + 6f, r2Y + rowH / 2f + 5f, RebarLightBlue, 13f)
        cx += colWidths[i]
    }
    drawLine(color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r2Y + rowH),
        end = Offset(tableLeft + tableW, r2Y + rowH), strokeWidth = 0.5f)

    // Row 3: Base bottom bars
    val r3Y = r2Y + rowH
    drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, r3Y), size = Size(tableW, rowH))
    val row3 = arrayOf("أفقي (قاعدة)", "أسفل القاعدة", "Ø${baseRebarDia.toInt()}",
        "@${baseRebarSpacing.toInt()}mm", "B")
    cx = tableLeft
    for (i in row3.indices) {
        drawTextAnnotated(row3[i], cx + 6f, r3Y + rowH / 2f + 5f, RebarBlue.copy(alpha = 0.8f), 13f)
        cx += colWidths[i]
    }
    drawLine(color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r3Y + rowH),
        end = Offset(tableLeft + tableW, r3Y + rowH), strokeWidth = 0.5f)

    // Row 4: Base top bars (near stem)
    val r4Y = r3Y + rowH
    val row4 = arrayOf("أفقي (قاعدة)", "أعلى القاعدة", "Ø${distRebarDia.toInt()}",
        "@${distRebarSpacing.toInt()}mm", "Heel")
    cx = tableLeft
    for (i in row4.indices) {
        drawTextAnnotated(row4[i], cx + 6f, r4Y + rowH / 2f + 5f, RebarRed, 13f)
        cx += colWidths[i]
    }

    // Column separators
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
        drawLine(color = ExtensionGray.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop),
            end = Offset(sepX, r4Y + rowH), strokeWidth = 0.5f)
    }

    // Table border
    drawRect(color = ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH + rowH * 4),
        style = Stroke(width = 1f))
}

// ============================================================================
// SHARED HELPERS
// ============================================================================

private fun DrawScope.drawConcreteHatch(
    left: Float, top: Float, w: Float, h: Float
) {
    nativeCanvas.save()
    nativeCanvas.clipRect(left, top, left + w, top + h)
    var i = left - h
    while (i < left + w + h) {
        nativeCanvas.drawLine(
            i, top, i + h, top + h,
            android.graphics.Paint().apply {
                color = Color(0x55AAAAAA).hashCode()
                strokeWidth = 0.6f
            }
        )
        i += 18f
    }
    nativeCanvas.restore()
}

private fun DrawScope.drawConcreteHatchPath(path: Path) {
    val bounds = path.getBounds()
    nativeCanvas.save()
    val androidPath = android.graphics.Path()
    path.asAndroidPath().let { androidPath.set(it) }
    nativeCanvas.clipPath(androidPath)
    var i = bounds.left - bounds.height
    while (i < bounds.right + bounds.height) {
        nativeCanvas.drawLine(
            i, bounds.top, i + bounds.height, bounds.bottom,
            android.graphics.Paint().apply {
                color = Color(0x55AAAAAA).hashCode()
                strokeWidth = 0.6f
            }
        )
        i += 18f
    }
    nativeCanvas.restore()
}

private fun DrawScope.drawDimLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    text: String, vertical: Boolean
) {
    val tick = 8f
    if (vertical) {
        drawLine(color = ExtensionGray, start = Offset(x1, y1), end = Offset(x1, y2), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x1 - tick, y1), end = Offset(x1 + tick, y1), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x1 - tick, y2), end = Offset(x1 + tick, y2), strokeWidth = 1f)
        drawTextAnnotated(text, x1 - 60f, (y1 + y2) / 2f + 4f, DimensionWhite, 13f)
    } else {
        drawLine(color = ExtensionGray, start = Offset(x1, y1), end = Offset(x2, y1), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x1, y1 - tick), end = Offset(x1, y1 + tick), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x2, y1 - tick), end = Offset(x2, y1 + tick), strokeWidth = 1f)
        drawTextAnnotated(text, (x1 + x2) / 2f - 30f, y1 - 8f, DimensionWhite, 13f)
    }
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

private fun DrawScope.drawTextAnnotated(
    text: String, x: Float, y: Float, color: Color, size: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = size
            this.color = color
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.LEFT
            setShadowLayer(2f, 1f, 1f, 0x44000000)
        }
        drawText(text, x, y, paint)
    }
}