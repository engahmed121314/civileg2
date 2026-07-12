package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// COLOR PALETTE — matches ProfessionalBeamDrawing exactly
// ============================================================================

/** Main bottom rebar color — BLUE */
private val RebarBlue = Color(0xFF4A90D9)

/** Top rebar / secondary steel — LIGHT BLUE */
private val TopRebarBlue = Color(0xFF7EC8E3)

/** Distribution bars — PURPLE */
private val DistributionPurple = Color(0xFF9B59B6)

/** Concrete fill — GRAY */
private val ConcreteGray = Color(0xFF6B6B6B)

/** Concrete top face (lighter for 3D effect) */
private val ConcreteTopGray = Color(0xFF8A8A8A)

/** Concrete side face (darker for 3D effect) */
private val ConcreteSideGray = Color(0xFF505050)

/** Dimension lines and text — WHITE */
private val DimensionWhite = Color(0xFFFFFFFF)

/** Dimension extension lines — GRAY */
private val ExtensionGray = Color(0xFFAAAAAA)

/** Table header background */
private val TableHeaderBg = Color(0x33FFFFFF)

/** Table row alternate */
private val TableRowAlt = Color(0x1AFFFFFF)

/** Support color */
private val SupportColor = Color(0xFFCCCCCC)

/** Hatch pattern for cut concrete */
private val HatchColor = Color(0x99AAAAAA)

/** Section cut indicator */
private val SectionCutColor = Color(0xFFE74C3C)

/** Landing fill */
private val LandingFill = Color(0xFF5E5E5E)

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

/**
 * Professional Reinforced Concrete Staircase Drawing.
 *
 * Renders a complete engineering drawing with:
 * - Side elevation view showing staircase body, steps, and reinforcement
 * - Cross-section view (Section A-A) showing slab thickness and bars
 * - Plan view showing stair width, treads, and reinforcement layout
 * - Reinforcement schedule table
 *
 * Background is transparent — the parent composable supplies the dark theme.
 */
@Composable
fun ProfessionalStairDrawing(
    stairWidth: Double,           // mm - width of staircase
    totalHeight: Double,          // mm - total rise
    totalLength: Double,          // mm - total horizontal run (not including landing)
    riserHeight: Double,          // mm
    treadWidth: Double,           // mm
    slabThickness: Double,        // mm - waist slab thickness
    landingLength: Double = 0.0,  // mm - landing length if any
    landingThickness: Double = 0.0, // mm
    mainRebarDia: Double,         // mm - bottom main steel
    mainRebarSpacing: Double,     // mm
    topRebarDia: Double = 0.0,    // mm
    topRebarSpacing: Double = 0.0, // mm
    distributionDia: Double = 0.0, // mm
    distributionSpacing: Double = 0.0, // mm
    cover: Double = 25.0,         // mm
    numberOfRisers: Int = 0,      // calculated from totalHeight/riserHeight if 0
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(560.dp)
    ) {
        val cw = size.width
        val ch = size.height

        // ── Derived values ────────────────────────────────────────────
        val nRisers = if (numberOfRisers > 0) numberOfRisers
            else (totalHeight / riserHeight).toInt().coerceAtLeast(1)
        val nTreads = nRisers - 1
        val slopeLength = sqrt(
            (totalLength * totalLength + totalHeight * totalHeight).toDouble()
        )
        val slopeAngleRad = atan2(totalHeight, totalLength)
        val slopeAngleDeg = Math.toDegrees(slopeAngleRad)
        val cosSlope = cos(slopeAngleRad).toFloat()
        val sinSlope = sin(slopeAngleRad).toFloat()
        val hasLanding = landingLength > 0.0
        val actualLandingThickness = if (hasLanding && landingThickness > 0.0)
            landingThickness else slabThickness

        // ── Layout zones ──────────────────────────────────────────────
        // Left 58%: Elevation view
        // Right 42%: Cross-section (top) + Plan view (bottom)
        // Bottom strip: Reinforcement table
        val elevZoneRight = cw * 0.58f
        val tableTop = ch * 0.74f
        val rightZoneTop = 30f
        val rightZoneMid = (rightZoneTop + tableTop) / 2f

        // ── Draw all zones ────────────────────────────────────────────
        // 1. Main elevation view (left)
        drawElevationView(
            zoneLeft = 60f, zoneTop = 28f,
            zoneRight = elevZoneRight - 10f, zoneBottom = tableTop - 8f,
            cw = cw, ch = ch,
            nRisers = nRisers, nTreads = nTreads,
            riserHeight = riserHeight, treadWidth = treadWidth,
            slabThickness = slabThickness, stairWidth = stairWidth,
            totalHeight = totalHeight, totalLength = totalLength,
            hasLanding = hasLanding, landingLength = landingLength,
            actualLandingThickness = actualLandingThickness,
            mainRebarDia = mainRebarDia, mainRebarSpacing = mainRebarSpacing,
            topRebarDia = topRebarDia, topRebarSpacing = topRebarSpacing,
            distributionDia = distributionDia, distributionSpacing = distributionSpacing,
            cover = cover,
            slopeAngleRad = slopeAngleRad, cosSlope = cosSlope, sinSlope = sinSlope
        )

        // 2. Cross-section view A-A (right-top)
        drawCrossSectionView(
            zoneLeft = elevZoneRight + 10f, zoneTop = rightZoneTop,
            zoneRight = cw - 16f, zoneBottom = rightZoneMid - 4f,
            slabThickness = slabThickness, stairWidth = stairWidth,
            mainRebarDia = mainRebarDia, mainRebarSpacing = mainRebarSpacing,
            topRebarDia = topRebarDia, topRebarSpacing = topRebarSpacing,
            distributionDia = distributionDia, distributionSpacing = distributionSpacing,
            cover = cover,
            slopeAngleDeg = slopeAngleDeg
        )

        // 3. Plan view (right-bottom)
        drawPlanView(
            zoneLeft = elevZoneRight + 10f, zoneTop = rightZoneMid + 4f,
            zoneRight = cw - 16f, zoneBottom = tableTop - 8f,
            totalLength = totalLength, stairWidth = stairWidth,
            nTreads = nTreads, treadWidth = treadWidth,
            hasLanding = hasLanding, landingLength = landingLength,
            mainRebarDia = mainRebarDia, mainRebarSpacing = mainRebarSpacing,
            distributionDia = distributionDia, distributionSpacing = distributionSpacing
        )

        // 4. Reinforcement schedule table (bottom)
        drawReinforcementScheduleTable(
            cw = cw, ch = ch, tableTop = tableTop,
            nRisers = nRisers, nTreads = nTreads,
            totalLength = totalLength, totalHeight = totalHeight,
            slopeLength = slopeLength,
            stairWidth = stairWidth,
            slabThickness = slabThickness,
            mainRebarDia = mainRebarDia, mainRebarSpacing = mainRebarSpacing,
            topRebarDia = topRebarDia, topRebarSpacing = topRebarSpacing,
            distributionDia = distributionDia, distributionSpacing = distributionSpacing,
            cover = cover, hasLanding = hasLanding,
            landingLength = landingLength, actualLandingThickness = actualLandingThickness
        )

        // 5. Title block (bottom-right corner)
        drawTitleBlock(cw, ch, nRisers, riserHeight, treadWidth, totalHeight, totalLength)
    }
}

// ============================================================================
// 1. ELEVATION VIEW — Side elevation with steps and reinforcement
// ============================================================================

private fun DrawScope.drawElevationView(
    zoneLeft: Float, zoneTop: Float,
    zoneRight: Float, zoneBottom: Float,
    cw: Float, ch: Float,
    nRisers: Int, nTreads: Int,
    riserHeight: Double, treadWidth: Double,
    slabThickness: Double, stairWidth: Double,
    totalHeight: Double, totalLength: Double,
    hasLanding: Boolean, landingLength: Double,
    actualLandingThickness: Double,
    mainRebarDia: Double, mainRebarSpacing: Double,
    topRebarDia: Double, topRebarSpacing: Double,
    distributionDia: Double, distributionSpacing: Double,
    cover: Double,
    slopeAngleRad: Double, cosSlope: Float, sinSlope: Float
) {
    val zoneW = zoneRight - zoneLeft
    val zoneH = zoneBottom - zoneTop

    // ── Scaling: fit stair geometry into zone ────────────────────────
    val totalDrawLength = if (hasLanding) totalLength + landingLength else totalLength
    val scaleX = (zoneW * 0.82f) / totalDrawLength.toFloat()
    val scaleY = (zoneH * 0.70f) / (totalHeight + slabThickness + 80).toFloat()
    val scale = min(scaleX, scaleY)

    val stepW = treadWidth.toFloat() * scale
    val stepH = riserHeight.toFloat() * scale
    val slabT = slabThickness.toFloat() * scale
    val landT = actualLandingThickness.toFloat() * scale

    // 3D perspective offsets
    val depth3D = stairWidth.toFloat() * scale * 0.22f
    val angleX3D = 0.32f
    val angleY3D = 0.22f
    val dx3D = depth3D * angleX3D
    val dy3D = depth3D * angleY3D

    // Origin: bottom-left of stair (where first riser meets floor)
    val originX = zoneLeft + 50f
    val originY = zoneBottom - 40f

    // Stair top-right corner (end of last tread at top)
    val stairEndX = originX + nTreads * stepW
    val stairEndY = originY - nRisers * stepH

    // Landing extends from stair end
    val landingEndX = if (hasLanding) stairEndX + landingLength.toFloat() * scale else stairEndX

    // ── Title ────────────────────────────────────────────────────────
    drawTextAnnotated(
        text = "ELEVATION", x = zoneLeft + 4f, y = zoneTop + 4f,
        color = DimensionWhite, size = 20f
    )

    // ── Draw stair body with 3D shading ──────────────────────────────
    // Build the waist slab outline (bottom of slab, following the slope)
    // The waist slab runs from the soffit of the first step to the soffit of the last step
    // with thickness perpendicular to the slope.

    // Bottom of waist slab line (offset from nosing line by slab thickness)
    // The nosing line goes from (originX, originY) to (stairEndX, stairEndY)
    // We offset it perpendicular to the slope (downward-left from the nosing)
    val perpOffX = slabT * sinSlope
    val perpOffY = slabT * cosSlope

    // Front face of the stair body (nosing line top, soffit line bottom)
    val stairFrontPath = Path().apply {
        // Bottom-left corner (start of first riser at base level)
        moveTo(originX, originY)
        // Up the first riser
        lineTo(originX, originY - stepH)
        // Step profile: each tread then riser
        for (i in 1 until nRisers) {
            // Tread (horizontal)
            lineTo(originX + i * stepW, originY - i * stepH)
            // Riser (vertical up) — drawn as we go to next step
        }
        // Last step top
        lineTo(stairEndX, stairEndY)

        // Landing horizontal (if any)
        if (hasLanding) {
            lineTo(landingEndX, stairEndY)
        }

        // Now go down the back edge (top of slab soffit at the back)
        // The soffit follows the slope offset by slab thickness
        val soffitStartX = stairEndX + (if (hasLanding) landingLength.toFloat() * scale else 0f)
        val soffitStartY = stairEndY

        // Landing back edge
        if (hasLanding) {
            lineTo(landingEndX, stairEndY + landT)
        } else {
            lineTo(stairEndX, stairEndY)
        }

        // Soffit line: from end back to start, offset perpendicular to slope
        val soffitEndX = originX + perpOffX
        val soffitEndY = originY + perpOffY

        // Soffit follows the slope from start to end
        lineTo(stairEndX + perpOffX - (if (hasLanding) 0f else 0f),
            stairEndY + perpOffY)
        lineTo(soffitEndX, soffitEndY)

        close()
    }

    // Draw front face with gradient
    drawPath(
        path = stairFrontPath,
        brush = Brush.verticalGradient(
            colors = listOf(ConcreteGray, ConcreteSideGray)
        )
    )
    drawPath(
        path = stairFrontPath,
        color = ConcreteGray,
        style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Miter)
    )

    // ── 3D Top face ──────────────────────────────────────────────────
    val topFacePath = Path().apply {
        // Along the top of steps (nosing line)
        moveTo(originX, originY - stepH)
        for (i in 1 until nRisers) {
            lineTo(originX + i * stepW, originY - i * stepH)
        }
        lineTo(stairEndX, stairEndY)
        if (hasLanding) {
            lineTo(landingEndX, stairEndY)
        }
        // 3D offset back
        lineTo(landingEndX + dx3D, stairEndY - dy3D)
        lineTo(stairEndX + dx3D, stairEndY - dy3D)
        for (i in (nRisers - 1) downTo 1) {
            lineTo(originX + i * stepW + dx3D, originY - i * stepH - dy3D)
        }
        lineTo(originX + dx3D, originY - stepH - dy3D)
        close()
    }
    drawPath(path = topFacePath, color = ConcreteTopGray)
    drawPath(
        path = topFacePath,
        color = ConcreteTopGray,
        style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Miter)
    )

    // ── 3D Right side face (landing end or stair end) ───────────────
    val sideEndX = if (hasLanding) landingEndX else stairEndX
    val sideEndY = stairEndY
    val sidePath = Path().apply {
        moveTo(sideEndX, sideEndY)
        lineTo(sideEndX + dx3D, sideEndY - dy3D)
        lineTo(sideEndX + dx3D, sideEndY - dy3D +
            (if (hasLanding) landT else slabT))
        lineTo(sideEndX, sideEndY + (if (hasLanding) landT else slabT))
        close()
    }
    drawPath(path = sidePath, color = ConcreteSideGray)
    drawPath(
        path = sidePath,
        color = ConcreteSideGray,
        style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Miter)
    )

    // ── Step lines (riser and tread lines on front face) ─────────────
    for (i in 1 until nRisers) {
        val x = originX + i * stepW
        val y = originY - i * stepH
        // Riser line
        drawLine(
            color = ExtensionGray.copy(alpha = 0.5f),
            start = Offset(x, y),
            end = Offset(x, y - stepH),
            strokeWidth = 0.8.dp.toPx()
        )
    }
    // Tread lines (horizontal at each step level)
    for (i in 1 until nRisers) {
        val x1 = originX + (i - 1) * stepW
        val x2 = originX + i * stepW
        val y = originY - i * stepH
        drawLine(
            color = ExtensionGray.copy(alpha = 0.35f),
            start = Offset(x1, y),
            end = Offset(x2, y),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    // ── Soffit line (bottom of slab) ────────────────────────────────
    val soffitP1 = Offset(originX + perpOffX, originY + perpOffY)
    val soffitP2 = Offset(stairEndX + perpOffX, stairEndY + perpOffY)
    drawLine(
        color = DimensionWhite.copy(alpha = 0.6f),
        start = soffitP1,
        end = soffitP2,
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
    )

    // ── Landing slab outline ─────────────────────────────────────────
    if (hasLanding) {
        // Landing bottom line
        drawLine(
            color = DimensionWhite.copy(alpha = 0.6f),
            start = Offset(stairEndX, stairEndY + landT),
            end = Offset(landingEndX, stairEndY + landT),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
        )
        // Landing right edge
        drawLine(
            color = ExtensionGray.copy(alpha = 0.5f),
            start = Offset(landingEndX, stairEndY),
            end = Offset(landingEndX, stairEndY + landT),
            strokeWidth = 1.dp.toPx()
        )
    }

    // ── Supports ─────────────────────────────────────────────────────
    // Bottom support (pin/fixed)
    drawStairSupport(
        x = originX, y = originY,
        isFixed = true, supportH = 28f
    )
    // Top support (pin)
    drawStairSupport(
        x = sideEndX, y = sideEndY + (if (hasLanding) landT else 0f),
        isFixed = false, supportH = 28f
    )

    // ── Main reinforcement (bottom steel following stair slope) ──────
    if (mainRebarDia > 0) {
        val barR = max(mainRebarDia.toFloat() * scale * 0.12f, 2.5f)
        // Main bars run along the soffit, offset by cover from bottom
        val barOffset = (cover.toFloat() + mainRebarDia.toFloat() / 2f) * scale
        val barStartX = originX + perpOffX * (barOffset / slabT)
        val barStartY = originY + perpOffY * (barOffset / slabT)
        val barEndX = stairEndX + perpOffX * (barOffset / slabT)
        val barEndY = stairEndY + perpOffY * (barOffset / slabT)

        // Draw multiple main bars (show 2-3 for visual effect)
        for (offset in listOf(-barR * 0.8f, barR * 0.8f)) {
            drawLine(
                color = RebarBlue,
                start = Offset(barStartX, barStartY + offset),
                end = Offset(barEndX, barEndY + offset),
                strokeWidth = barR * 1.8f,
                cap = StrokeCap.Round
            )
        }

        // Main bar label
        val midBarX = (barStartX + barEndX) / 2f
        val midBarY = (barStartY + barEndY) / 2f + barR + 14f
        drawTextWithBackground(
            text = "Ø${mainRebarDia.toInt()} @ ${mainRebarSpacing.toInt()}",
            x = midBarX - 40f, y = midBarY,
            textColor = RebarBlue, bgColor = Color(0xCC1A1A2E),
            textSize = 16f
        )
    }

    // ── Top reinforcement at supports ────────────────────────────────
    if (topRebarDia > 0 && topRebarSpacing > 0) {
        val barR = max(topRebarDia.toFloat() * scale * 0.12f, 2f)
        val topBarLen = totalLength.toFloat() * scale * 0.18f

        // Bottom support top bars (hogging moment)
        drawLine(
            color = TopRebarBlue,
            start = Offset(originX, originY - stepH - slabT * 0.3f),
            end = Offset(originX + topBarLen, originY - stepH - slabT * 0.3f - topBarLen * sinSlope),
            strokeWidth = barR * 1.6f,
            cap = StrokeCap.Round
        )

        // Top support top bars
        val topSupportY = stairEndY + (if (hasLanding) landT else slabT) * 0.7f
        drawLine(
            color = TopRebarBlue,
            start = Offset(sideEndX, topSupportY),
            end = Offset(sideEndX - topBarLen, topSupportY + topBarLen * sinSlope),
            strokeWidth = barR * 1.6f,
            cap = StrokeCap.Round
        )

        // Top bar label
        drawTextWithBackground(
            text = "Ø${topRebarDia.toInt()} @ ${topRebarSpacing.toInt()}",
            x = originX + 4f, y = originY - stepH - slabT * 0.3f - 14f,
            textColor = TopRebarBlue, bgColor = Color(0xCC1A1A2E),
            textSize = 14f
        )
    }

    // ── Distribution bars indicators (perpendicular to slope) ────────
    if (distributionDia > 0 && distributionSpacing > 0) {
        val distR = max(distributionDia.toFloat() * scale * 0.10f, 1.5f)
        // Show a few distribution bar marks perpendicular to slope
        val numDistMarks = min(nRisers, 6)
        for (i in 1..numDistMarks) {
            val t = i.toFloat() / (numDistMarks + 1)
            val bx = originX + t * (stairEndX - originX)
            val by = originY + t * (stairEndY - originY)
            // Short perpendicular line (distribution bar indicator)
            val perpLen = slabT * 0.5f
            drawLine(
                color = DistributionPurple,
                start = Offset(bx + perpOffX * 0.5f - sinSlope * perpLen,
                    by + perpOffY * 0.5f - cosSlope * perpLen),
                end = Offset(bx + perpOffX * 0.5f + sinSlope * perpLen,
                    by + perpOffY * 0.5f + cosSlope * perpLen),
                strokeWidth = distR * 1.6f,
                cap = StrokeCap.Round
            )
        }

        // Distribution label
        drawTextWithBackground(
            text = "Ø${distributionDia.toInt()} @ ${distributionSpacing.toInt()}",
            x = stairEndX - 80f, y = stairEndY + slabT + 18f,
            textColor = DistributionPurple, bgColor = Color(0xCC1A1A2E),
            textSize = 14f
        )
    }

    // ── Section cut line A-A ─────────────────────────────────────────
    val cutX = originX + nTreads * stepW * 0.45f
    val cutTopY = originY - nRisers * stepH * 0.45f - 20f
    val cutBotY = originY + perpOffY * 0.45f + 15f
    drawLine(
        color = SectionCutColor,
        start = Offset(cutX - 20f, cutTopY),
        end = Offset(cutX + 20f, cutTopY),
        strokeWidth = 2.5f
    )
    drawLine(
        color = SectionCutColor,
        start = Offset(cutX, cutTopY),
        end = Offset(cutX, cutBotY),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
    )
    drawLine(
        color = SectionCutColor,
        start = Offset(cutX - 20f, cutBotY),
        end = Offset(cutX + 20f, cutBotY),
        strokeWidth = 2.5f
    )
    drawTextAnnotated(
        text = "A", x = cutX - 28f, y = cutTopY + 2f,
        color = SectionCutColor, size = 22f
    )
    drawTextAnnotated(
        text = "A", x = cutX + 14f, y = cutBotY + 4f,
        color = SectionCutColor, size = 22f
    )

    // ── Dimension lines ──────────────────────────────────────────────
    drawElevationDimensions(
        originX = originX, originY = originY,
        stairEndX = stairEndX, stairEndY = stairEndY,
        landingEndX = landingEndX, hasLanding = hasLanding,
        stepW = stepW, stepH = stepH,
        nRisers = nRisers, nTreads = nTreads,
        riserHeight = riserHeight, treadWidth = treadWidth,
        totalHeight = totalHeight, totalLength = totalLength,
        slabThickness = slabThickness, scale = scale
    )
}

// ============================================================================
// 2. CROSS-SECTION VIEW (Section A-A)
// ============================================================================

private fun DrawScope.drawCrossSectionView(
    zoneLeft: Float, zoneTop: Float,
    zoneRight: Float, zoneBottom: Float,
    slabThickness: Double, stairWidth: Double,
    mainRebarDia: Double, mainRebarSpacing: Double,
    topRebarDia: Double, topRebarSpacing: Double,
    distributionDia: Double, distributionSpacing: Double,
    cover: Double,
    slopeAngleDeg: Double
) {
    val zoneW = zoneRight - zoneLeft
    val zoneH = zoneBottom - zoneTop

    // ── Panel background ─────────────────────────────────────────────
    drawRoundRect(
        color = Color(0x18000000),
        topLeft = Offset(zoneLeft, zoneTop),
        size = Size(zoneW, zoneH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // ── Title ────────────────────────────────────────────────────────
    drawTextAnnotated(
        text = "SECTION A-A", x = zoneLeft + 6f, y = zoneTop + 18f,
        color = DimensionWhite, size = 18f
    )

    // ── Scale section to fit zone ────────────────────────────────────
    val padding = 36f
    val titleSpace = 28f
    val availW = zoneW - 2 * padding
    val availH = zoneH - 2 * padding - titleSpace
    val secScale = min(
        availW / stairWidth.toFloat(),
        availH / (slabThickness + 60).toFloat()
    ).coerceAtMost(1.8f)

    val secW = stairWidth.toFloat() * secScale
    val secT = slabThickness.toFloat() * secScale
    val secLeft = zoneLeft + (zoneW - secW) / 2f
    val secTop = zoneTop + titleSpace + (availH - secT) / 2f + 10f

    // ── Concrete section rectangle ───────────────────────────────────
    // In section, we see the slab as a thick band
    drawRect(
        color = ConcreteGray,
        topLeft = Offset(secLeft, secTop),
        size = Size(secW, secT)
    )
    // Gradient overlay
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(ConcreteTopGray, ConcreteGray, ConcreteSideGray)
        ),
        topLeft = Offset(secLeft, secTop),
        size = Size(secW, secT)
    )
    drawRect(
        color = DimensionWhite.copy(alpha = 0.5f),
        topLeft = Offset(secLeft, secTop),
        size = Size(secW, secT),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // ── Hatch pattern on concrete ────────────────────────────────────
    drawSectionHatch(secLeft, secTop, secW, secT)

    // ── Cover indication (dashed outline) ────────────────────────────
    val covPx = cover.toFloat() * secScale
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
    drawRect(
        color = ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(secLeft + covPx, secTop + covPx),
        size = Size(secW - 2 * covPx, secT - 2 * covPx),
        style = Stroke(width = 0.8f, pathEffect = dashEffect)
    )

    // ── Bottom main reinforcement bars ───────────────────────────────
    if (mainRebarDia > 0) {
        val barR = max((mainRebarDia.toFloat() / 2f) * secScale, 2.5f)
        val numBars = (stairWidth / mainRebarSpacing).toInt().coerceAtLeast(2).coerceAtMost(8)
        val usableW = secW - 2 * covPx
        val spacing = if (numBars > 1) usableW / (numBars - 1) else 0f
        val barY = secTop + secT - covPx - barR

        for (i in 0 until numBars) {
            val bx = secLeft + covPx + i * spacing
            // Draw rebar circle
            drawCircle(color = RebarBlue, radius = barR, center = Offset(bx, barY))
            drawCircle(
                color = RebarBlue.copy(alpha = 0.5f),
                radius = barR + 1.5f,
                center = Offset(bx, barY),
                style = Stroke(width = 0.5f)
            )
        }

        // Bar mark label
        if (numBars > 0) {
            drawTextAnnotated(
                text = "Ø${mainRebarDia.toInt()}",
                x = secLeft + secW + 8f,
                y = barY + 4f,
                color = RebarBlue, size = 14f
            )
        }
    }

    // ── Top reinforcement bars ───────────────────────────────────────
    if (topRebarDia > 0 && topRebarSpacing > 0) {
        val barR = max((topRebarDia.toFloat() / 2f) * secScale, 2f)
        val numBars = (stairWidth / topRebarSpacing).toInt().coerceAtLeast(2).coerceAtMost(6)
        val usableW = secW - 2 * covPx
        val spacing = if (numBars > 1) usableW / (numBars - 1) else 0f
        val barY = secTop + covPx + barR

        for (i in 0 until numBars) {
            val bx = secLeft + covPx + i * spacing
            drawCircle(color = TopRebarBlue, radius = barR, center = Offset(bx, barY))
            drawCircle(
                color = TopRebarBlue.copy(alpha = 0.5f),
                radius = barR + 1.5f,
                center = Offset(bx, barY),
                style = Stroke(width = 0.5f)
            )
        }

        if (numBars > 0) {
            drawTextAnnotated(
                text = "Ø${topRebarDia.toInt()}",
                x = secLeft + secW + 8f,
                y = barY + 4f,
                color = TopRebarBlue, size = 14f
            )
        }
    }

    // ── Distribution bars (shown as small circles along the thickness) ──
    if (distributionDia > 0 && distributionSpacing > 0) {
        val distR = max((distributionDia.toFloat() / 2f) * secScale, 1.5f)
        // Show a couple distribution bar marks at ends
        for (xOff in listOf(secLeft + covPx + 8f, secLeft + secW - covPx - 8f)) {
            val yPos = secTop + secT / 2f
            drawCircle(
                color = DistributionPurple,
                radius = distR,
                center = Offset(xOff, yPos)
            )
        }
    }

    // ── Cover dimension (left side) ──────────────────────────────────
    val cDimX = secLeft - 8f
    drawLine(
        color = ExtensionGray,
        start = Offset(cDimX, secTop),
        end = Offset(cDimX, secTop + covPx),
        strokeWidth = 0.8f
    )
    drawArrowHead(cDimX, secTop, direction = 1f, color = ExtensionGray, vertical = true)
    drawArrowHead(cDimX, secTop + covPx, direction = -1f, color = ExtensionGray, vertical = true)
    drawTextAnnotated(
        text = "${cover.toInt()}",
        x = cDimX - 22f, y = secTop + covPx / 2f + 4f,
        color = ExtensionGray, size = 12f
    )

    // ── Waist slab thickness dimension (right side) ──────────────────
    val tDimX = secLeft + secW + 6f
    val dimY1 = secTop
    val dimY2 = secTop + secT
    drawLine(
        color = DimensionWhite,
        start = Offset(tDimX, dimY1),
        end = Offset(tDimX, dimY2),
        strokeWidth = 0.8f
    )
    drawArrowHead(tDimX, dimY1, direction = 1f, color = DimensionWhite, vertical = true)
    drawArrowHead(tDimX, dimY2, direction = -1f, color = DimensionWhite, vertical = true)
    // Rotated label
    withTransform({
        rotate(degrees = -90f, pivot = Offset(tDimX + 16f, secTop + secT / 2f))
    }) {
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = 14f
                color = android.graphics.Color.WHITE
                isFakeBoldText = true
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            this.drawText(
                "t=${slabThickness.toInt()}",
                tDimX + 16f,
                secTop + secT / 2f + 4f,
                paint
            )
        }
    }

    // ── Width dimension (bottom) ─────────────────────────────────────
    val wDimY = secTop + secT + 16f
    drawLine(
        color = DimensionWhite,
        start = Offset(secLeft, wDimY),
        end = Offset(secLeft + secW, wDimY),
        strokeWidth = 0.8f
    )
    drawArrowHead(secLeft, wDimY, direction = 1f, color = DimensionWhite)
    drawArrowHead(secLeft + secW, wDimY, direction = -1f, color = DimensionWhite)
    drawTextAnnotated(
        text = "${stairWidth.toInt()}",
        x = secLeft + secW / 2f - 16f,
        y = wDimY + 14f,
        color = DimensionWhite, size = 13f
    )

    // ── Slope angle indicator ────────────────────────────────────────
    drawTextAnnotated(
        text = "θ = ${"%.1f".format(slopeAngleDeg)}°",
        x = secLeft + 4f, y = secTop - 6f,
        color = DimensionWhite.copy(alpha = 0.7f), size = 13f
    )
}

// ============================================================================
// 3. PLAN VIEW — Top-down view of staircase
// ============================================================================

private fun DrawScope.drawPlanView(
    zoneLeft: Float, zoneTop: Float,
    zoneRight: Float, zoneBottom: Float,
    totalLength: Double, stairWidth: Double,
    nTreads: Int, treadWidth: Double,
    hasLanding: Boolean, landingLength: Double,
    mainRebarDia: Double, mainRebarSpacing: Double,
    distributionDia: Double, distributionSpacing: Double
) {
    val zoneW = zoneRight - zoneLeft
    val zoneH = zoneBottom - zoneTop

    // ── Panel background ─────────────────────────────────────────────
    drawRoundRect(
        color = Color(0x18000000),
        topLeft = Offset(zoneLeft, zoneTop),
        size = Size(zoneW, zoneH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // ── Title ────────────────────────────────────────────────────────
    drawTextAnnotated(
        text = "PLAN VIEW", x = zoneLeft + 6f, y = zoneTop + 18f,
        color = DimensionWhite, size = 18f
    )

    // ── Scale ────────────────────────────────────────────────────────
    val pad = 30f
    val titleH = 28f
    val availW = zoneW - 2 * pad
    val availH = zoneH - 2 * pad - titleH
    val totalPlanLen = if (hasLanding) totalLength + landingLength else totalLength
    val planScaleX = availW / totalPlanLen.toFloat()
    val planScaleY = availH / stairWidth.toFloat()
    val planScale = min(planScaleX, planScaleY)

    val planW = totalPlanLen.toFloat() * planScale
    val planH = stairWidth.toFloat() * planScale
    val planLeft = zoneLeft + (zoneW - planW) / 2f
    val planTop = zoneTop + titleH + (availH - planH) / 2f + 8f

    val stairPlanW = totalLength.toFloat() * planScale

    // ── Stair outline (main flight) ──────────────────────────────────
    drawRect(
        color = ConcreteGray,
        topLeft = Offset(planLeft, planTop),
        size = Size(stairPlanW, planH)
    )
    drawRect(
        color = ConcreteTopGray,
        topLeft = Offset(planLeft, planTop),
        size = Size(stairPlanW, planH),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // ── Landing area ─────────────────────────────────────────────────
    if (hasLanding) {
        val landPlanW = landingLength.toFloat() * planScale
        drawRect(
            color = LandingFill,
            topLeft = Offset(planLeft + stairPlanW, planTop),
            size = Size(landPlanW, planH)
        )
        drawRect(
            color = ConcreteTopGray,
            topLeft = Offset(planLeft + stairPlanW, planTop),
            size = Size(landPlanW, planH),
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Landing label
        drawTextAnnotated(
            text = "Landing",
            x = planLeft + stairPlanW + 4f,
            y = planTop + planH / 2f + 4f,
            color = DimensionWhite.copy(alpha = 0.6f), size = 13f
        )
    }

    // ── Tread lines in plan ──────────────────────────────────────────
    if (nTreads > 0) {
        val treadPlanW = stairPlanW / (nTreads + 1)  // +1 for visual spacing
        for (i in 1..nTreads) {
            val lx = planLeft + i * treadPlanW
            if (lx < planLeft + stairPlanW) {
                drawLine(
                    color = ExtensionGray.copy(alpha = 0.4f),
                    start = Offset(lx, planTop),
                    end = Offset(lx, planTop + planH),
                    strokeWidth = 0.6f
                )
            }
        }
    }

    // ── Main reinforcement in plan (parallel to stair length) ────────
    if (mainRebarDia > 0) {
        val numBars = (stairWidth / mainRebarSpacing).toInt()
            .coerceAtLeast(2).coerceAtMost(7)
        val barSpacing = if (numBars > 1) planH / (numBars + 1) else planH / 2f
        for (i in 1..numBars) {
            val by = planTop + i * barSpacing
            drawLine(
                color = RebarBlue,
                start = Offset(planLeft + 4f, by),
                end = Offset(planLeft + stairPlanW - 4f, by),
                strokeWidth = max(mainRebarDia.toFloat() * planScale * 0.06f, 1.2f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
            )
        }
    }

    // ── Distribution bars in plan (perpendicular, across width) ──────
    if (distributionDia > 0 && distributionSpacing > 0) {
        val numDist = (totalLength / distributionSpacing).toInt()
            .coerceAtLeast(2).coerceAtMost(10)
        val distSpacing = stairPlanW / (numDist + 1)
        for (i in 1..numDist) {
            val dx = planLeft + i * distSpacing
            if (dx < planLeft + stairPlanW) {
                drawLine(
                    color = DistributionPurple,
                    start = Offset(dx, planTop + 3f),
                    end = Offset(dx, planTop + planH - 3f),
                    strokeWidth = max(distributionDia.toFloat() * planScale * 0.06f, 1f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                )
            }
        }
    }

    // ── Arrow indicating direction of ascent ─────────────────────────
    val arrowY = planTop - 8f
    val arrowX1 = planLeft + stairPlanW * 0.3f
    val arrowX2 = planLeft + stairPlanW * 0.7f
    drawLine(
        color = DimensionWhite.copy(alpha = 0.6f),
        start = Offset(arrowX1, arrowY),
        end = Offset(arrowX2, arrowY),
        strokeWidth = 1.5f
    )
    drawArrowHead(arrowX2, arrowY, direction = 1f, color = DimensionWhite.copy(alpha = 0.6f))
    drawTextAnnotated(
        text = "UP", x = (arrowX1 + arrowX2) / 2f - 10f, y = arrowY - 4f,
        color = DimensionWhite.copy(alpha = 0.5f), size = 12f
    )

    // ── Width dimension (left side) ──────────────────────────────────
    val wDimX = planLeft - 10f
    drawLine(
        color = DimensionWhite,
        start = Offset(wDimX, planTop),
        end = Offset(wDimX, planTop + planH),
        strokeWidth = 0.8f
    )
    drawArrowHead(wDimX, planTop, direction = 1f, color = DimensionWhite, vertical = true)
    drawArrowHead(wDimX, planTop + planH, direction = -1f, color = DimensionWhite, vertical = true)
    drawTextAnnotated(
        text = "${stairWidth.toInt()}",
        x = wDimX - 30f, y = planTop + planH / 2f + 4f,
        color = DimensionWhite, size = 12f
    )

    // ── Length dimension (bottom) ────────────────────────────────────
    val lDimY = planTop + planH + 12f
    drawLine(
        color = DimensionWhite,
        start = Offset(planLeft, lDimY),
        end = Offset(planLeft + stairPlanW, lDimY),
        strokeWidth = 0.8f
    )
    drawArrowHead(planLeft, lDimY, direction = 1f, color = DimensionWhite)
    drawArrowHead(planLeft + stairPlanW, lDimY, direction = -1f, color = DimensionWhite)
    drawTextAnnotated(
        text = "${totalLength.toInt()}",
        x = planLeft + stairPlanW / 2f - 18f, y = lDimY + 13f,
        color = DimensionWhite, size = 12f
    )

    // ── Number of treads label ───────────────────────────────────────
    drawTextAnnotated(
        text = "${nTreads} treads",
        x = planLeft + stairPlanW / 2f - 22f,
        y = planTop + planH / 2f + 4f,
        color = DimensionWhite.copy(alpha = 0.5f), size = 13f
    )
}

// ============================================================================
// 4. REINFORCEMENT SCHEDULE TABLE
// ============================================================================

private fun DrawScope.drawReinforcementScheduleTable(
    cw: Float, ch: Float, tableTop: Float,
    nRisers: Int, nTreads: Int,
    totalLength: Double, totalHeight: Double,
    slopeLength: Double,
    stairWidth: Double,
    slabThickness: Double,
    mainRebarDia: Double, mainRebarSpacing: Double,
    topRebarDia: Double, topRebarSpacing: Double,
    distributionDia: Double, distributionSpacing: Double,
    cover: Double, hasLanding: Boolean,
    landingLength: Double, actualLandingThickness: Double
) {
    val tableLeft = 16f
    val tableW = cw - 32f
    val rowH = 24f
    val headerH = 28f
    val colWidths = floatArrayOf(
        tableW * 0.12f,  // Mark
        tableW * 0.14f,  // Diameter
        tableW * 0.14f,  // Spacing
        tableW * 0.24f,  // Length
        tableW * 0.18f,  // No.
        tableW * 0.18f   // Weight
    )

    // ── Table title ──────────────────────────────────────────────────
    drawTextAnnotated(
        text = "REINFORCEMENT SCHEDULE",
        x = tableLeft, y = tableTop - 4f,
        color = DimensionWhite, size = 18f
    )

    // ── Header row ───────────────────────────────────────────────────
    drawRect(
        color = TableHeaderBg,
        topLeft = Offset(tableLeft, tableTop + 6f),
        size = Size(tableW, headerH)
    )

    val headers = arrayOf("Mark", "Dia.", "Spacing", "Length", "No.", "Weight")
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(
            text = headers[i],
            x = cx + 6f,
            y = tableTop + 6f + headerH / 2f + 5f,
            color = DimensionWhite, size = 16f
        )
        cx += colWidths[i]
    }

    // ── Separator ────────────────────────────────────────────────────
    drawLine(
        color = ExtensionGray.copy(alpha = 0.3f),
        start = Offset(tableLeft, tableTop + 6f + headerH),
        end = Offset(tableLeft + tableW, tableTop + 6f + headerH),
        strokeWidth = 0.5.dp.toPx()
    )

    // ── Row 1: Main bottom reinforcement ─────────────────────────────
    val row1Y = tableTop + 6f + headerH
    drawRect(
        color = TableRowAlt,
        topLeft = Offset(tableLeft, row1Y),
        size = Size(tableW, rowH)
    )
    val mainBarCount = (stairWidth / mainRebarSpacing).toInt().coerceAtLeast(1)
    val mainBarLength = slopeLength + 2 * 300  // 300mm anchorage at each end
    val mainBarWeight = mainBarCount * mainBarLength * (mainRebarDia * mainRebarDia * 0.006165 / 1000.0)

    val row1Data = arrayOf(
        "B1",
        "Ø${mainRebarDia.toInt()}",
        "@ ${mainRebarSpacing.toInt()}",
        "${mainBarLength.toInt()} mm",
        "$mainBarCount",
        "${"%.1f".format(mainBarWeight)} kg"
    )
    cx = tableLeft
    for (i in row1Data.indices) {
        drawTextAnnotated(
            text = row1Data[i],
            x = cx + 6f, y = row1Y + rowH / 2f + 5f,
            color = RebarBlue, size = 15f
        )
        cx += colWidths[i]
    }

    // ── Separator ────────────────────────────────────────────────────
    val row2Y = row1Y + rowH
    drawLine(
        color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, row2Y),
        end = Offset(tableLeft + tableW, row2Y),
        strokeWidth = 0.5.dp.toPx()
    )

    // ── Row 2: Top reinforcement (if present) ────────────────────────
    var currentY = row2Y
    if (topRebarDia > 0 && topRebarSpacing > 0) {
        drawRect(
            color = Color(0x0DFFFFFF),
            topLeft = Offset(tableLeft, currentY),
            size = Size(tableW, rowH)
        )
        val topBarCount = (stairWidth / topRebarSpacing).toInt().coerceAtLeast(1) * 2 // both ends
        val topBarLength = totalLength * 0.25 // ~L/4 at each support
        val topBarWeight = topBarCount * topBarLength * (topRebarDia * topRebarDia * 0.006165 / 1000.0)

        val row2Data = arrayOf(
            "T1",
            "Ø${topRebarDia.toInt()}",
            "@ ${topRebarSpacing.toInt()}",
            "${topBarLength.toInt()} mm",
            "$topBarCount",
            "${"%.1f".format(topBarWeight)} kg"
        )
        cx = tableLeft
        for (i in row2Data.indices) {
            drawTextAnnotated(
                text = row2Data[i],
                x = cx + 6f, y = currentY + rowH / 2f + 5f,
                color = TopRebarBlue, size = 15f
            )
            cx += colWidths[i]
        }
        currentY += rowH

        drawLine(
            color = ExtensionGray.copy(alpha = 0.2f),
            start = Offset(tableLeft, currentY),
            end = Offset(tableLeft + tableW, currentY),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    // ── Row 3: Distribution bars ─────────────────────────────────────
    if (distributionDia > 0 && distributionSpacing > 0) {
        drawRect(
            color = TableRowAlt,
            topLeft = Offset(tableLeft, currentY),
            size = Size(tableW, rowH)
        )
        val distBarCount = (slopeLength / distributionSpacing).toInt().coerceAtLeast(1)
        val distBarLength = stairWidth - 2 * cover
        val distBarWeight = distBarCount * distBarLength * (distributionDia * distributionDia * 0.006165 / 1000.0)

        val row3Data = arrayOf(
            "D1",
            "Ø${distributionDia.toInt()}",
            "@ ${distributionSpacing.toInt()}",
            "${distBarLength.toInt()} mm",
            "$distBarCount",
            "${"%.1f".format(distBarWeight)} kg"
        )
        cx = tableLeft
        for (i in row3Data.indices) {
            drawTextAnnotated(
                text = row3Data[i],
                x = cx + 6f, y = currentY + rowH / 2f + 5f,
                color = DistributionPurple, size = 15f
            )
            cx += colWidths[i]
        }
        currentY += rowH

        drawLine(
            color = ExtensionGray.copy(alpha = 0.2f),
            start = Offset(tableLeft, currentY),
            end = Offset(tableLeft + tableW, currentY),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    // ── Column separators ────────────────────────────────────────────
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
        drawLine(
            color = ExtensionGray.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop + 6f),
            end = Offset(sepX, currentY),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    // ── Table border ─────────────────────────────────────────────────
    drawRect(
        color = ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(tableLeft, tableTop + 6f),
        size = Size(tableW, currentY - tableTop - 6f),
        style = Stroke(width = 1.dp.toPx())
    )
}

// ============================================================================
// 5. TITLE BLOCK
// ============================================================================

private fun DrawScope.drawTitleBlock(
    cw: Float, ch: Float,
    nRisers: Int,
    riserHeight: Double,
    treadWidth: Double,
    totalHeight: Double,
    totalLength: Double
) {
    val blockW = 200f
    val blockH = 48f
    val blockLeft = cw - blockW - 16f
    val blockTop = ch - blockH - 8f

    drawRoundRect(
        color = Color(0x22000000),
        topLeft = Offset(blockLeft, blockTop),
        size = Size(blockW, blockH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    drawTextAnnotated(
        text = "STAIRCASE DETAIL",
        x = blockLeft + 8f, y = blockTop + 18f,
        color = DimensionWhite, size = 16f
    )
    drawTextAnnotated(
        text = "${nRisers}R × ${treadWidth.toInt()}T  |  H=${totalHeight.toInt()}  L=${totalLength.toInt()}",
        x = blockLeft + 8f, y = blockTop + 36f,
        color = ExtensionGray, size = 12f
    )
}

// ============================================================================
// 6. STAIR SUPPORT SYMBOLS
// ============================================================================

/**
 * Draws a structural support symbol at the given position.
 * [isFixed] = true draws a fixed support (hatched wall), false draws a pin support.
 */
private fun DrawScope.drawStairSupport(
    x: Float, y: Float,
    isFixed: Boolean, supportH: Float
) {
    val lineW = 1.5.dp.toPx()
    val supportColor = SupportColor

    if (isFixed) {
        // Fixed support — hatched wall
        val wallW = 12f
        drawRect(
            color = supportColor.copy(alpha = 0.3f),
            topLeft = Offset(x - wallW / 2f, y),
            size = Size(wallW, supportH)
        )
        drawLine(
            color = supportColor,
            start = Offset(x - wallW / 2f, y),
            end = Offset(x - wallW / 2f, y + supportH),
            strokeWidth = lineW
        )
        drawLine(
            color = supportColor,
            start = Offset(x + wallW / 2f, y),
            end = Offset(x + wallW / 2f, y + supportH),
            strokeWidth = lineW
        )
        // Ground hatching
        for (i in 0..3) {
            val hx = x - wallW / 2f + i * (wallW / 3f)
            drawLine(
                color = supportColor,
                start = Offset(hx, y + supportH),
                end = Offset(hx - 5f, y + supportH + 8f),
                strokeWidth = 1.2f
            )
        }
    } else {
        // Pin support — triangle
        val triW = 24f
        val triH = supportH * 0.65f
        val triPath = Path().apply {
            moveTo(x, y)
            lineTo(x - triW / 2f, y + triH)
            lineTo(x + triW / 2f, y + triH)
            close()
        }
        drawPath(path = triPath, color = supportColor.copy(alpha = 0.5f))
        drawPath(
            path = triPath, color = supportColor,
            style = Stroke(width = lineW, join = StrokeJoin.Miter)
        )
        // Pin circle
        drawCircle(
            color = supportColor, radius = 3.5f,
            center = Offset(x, y + triH + 5f)
        )
        // Ground line
        val groundY = y + triH + 10f
        drawLine(
            color = supportColor,
            start = Offset(x - triW * 0.7f, groundY),
            end = Offset(x + triW * 0.7f, groundY),
            strokeWidth = lineW
        )
        // Ground hatching
        for (i in 0..4) {
            val hx = x - triW * 0.7f + i * (triW * 1.4f / 4f)
            drawLine(
                color = supportColor,
                start = Offset(hx, groundY),
                end = Offset(hx - 5f, groundY + 8f),
                strokeWidth = 1.2f
            )
        }
    }
}

// ============================================================================
// 7. ELEVATION DIMENSION LINES
// ============================================================================

private fun DrawScope.drawElevationDimensions(
    originX: Float, originY: Float,
    stairEndX: Float, stairEndY: Float,
    landingEndX: Float, hasLanding: Boolean,
    stepW: Float, stepH: Float,
    nRisers: Int, nTreads: Int,
    riserHeight: Double, treadWidth: Double,
    totalHeight: Double, totalLength: Double,
    slabThickness: Double, scale: Float
) {
    val dimLineW = 0.8.dp.toPx()
    val extLineW = 0.6.dp.toPx()

    // ── Total Height dimension (left side) ───────────────────────────
    val heightX = originX - 36f
    drawLine(
        color = DimensionWhite,
        start = Offset(heightX, originY),
        end = Offset(heightX, stairEndY),
        strokeWidth = dimLineW
    )
    drawArrowHead(heightX, originY, direction = -1f, color = DimensionWhite, vertical = true)
    drawArrowHead(heightX, stairEndY, direction = 1f, color = DimensionWhite, vertical = true)
    // Extension lines
    drawLine(
        color = ExtensionGray,
        start = Offset(originX - 2f, originY),
        end = Offset(heightX - 4f, originY),
        strokeWidth = extLineW
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(originX - 2f, stairEndY),
        end = Offset(heightX - 4f, stairEndY),
        strokeWidth = extLineW
    )
    // Rotated label
    withTransform({
        rotate(degrees = -90f, pivot = Offset(heightX - 14f, originY - (originY - stairEndY) / 2f))
    }) {
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = 16f
                color = android.graphics.Color.WHITE
                isFakeBoldText = true
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            this.drawText(
                "H=${totalHeight.toInt()}",
                heightX - 14f,
                stairEndY + (originY - stairEndY) / 2f + 4f,
                paint
            )
        }
    }

    // ── Total Horizontal Length dimension (bottom) ───────────────────
    val endX = if (hasLanding) landingEndX else stairEndX
    val lenY = originY + 50f
    drawLine(
        color = DimensionWhite,
        start = Offset(originX, lenY),
        end = Offset(endX, lenY),
        strokeWidth = dimLineW
    )
    drawArrowHead(originX, lenY, direction = 1f, color = DimensionWhite)
    drawArrowHead(endX, lenY, direction = -1f, color = DimensionWhite)
    // Extension lines
    drawLine(
        color = ExtensionGray,
        start = Offset(originX, originY + 4f),
        end = Offset(originX, lenY + 6f),
        strokeWidth = extLineW
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(endX, originY + 4f),
        end = Offset(endX, lenY + 6f),
        strokeWidth = extLineW
    )
    drawTextAnnotated(
        text = "L = ${totalLength.toInt()} mm",
        x = (originX + endX) / 2f - 36f, y = lenY + 18f,
        color = DimensionWhite, size = 16f
    )

    // ── Riser height dimension (first riser, small) ──────────────────
    if (nRisers >= 2) {
        val rX = originX - 14f
        val rY1 = originY - stepH
        val rY2 = originY
        drawLine(
            color = TopRebarBlue.copy(alpha = 0.8f),
            start = Offset(rX, rY1),
            end = Offset(rX, rY2),
            strokeWidth = 0.6f
        )
        drawArrowHead(rX, rY1, direction = 1f, color = TopRebarBlue.copy(alpha = 0.8f), vertical = true)
        drawArrowHead(rX, rY2, direction = -1f, color = TopRebarBlue.copy(alpha = 0.8f), vertical = true)
        withTransform({
            rotate(degrees = -90f, pivot = Offset(rX - 10f, (rY1 + rY2) / 2f))
        }) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textSize = 11f
                    color = android.graphics.Color.parseColor("#7EC8E3")
                    isFakeBoldText = true
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                this.drawText(
                    "R=${riserHeight.toInt()}",
                    rX - 10f,
                    (rY1 + rY2) / 2f + 3f,
                    paint
                )
            }
        }
    }

    // ── Tread width dimension (first tread, small) ───────────────────
    if (nTreads >= 1) {
        val tY = originY - stepH + stepH + 16f
        val tX1 = originX
        val tX2 = originX + stepW
        drawLine(
            color = TopRebarBlue.copy(alpha = 0.8f),
            start = Offset(tX1, tY),
            end = Offset(tX2, tY),
            strokeWidth = 0.6f
        )
        drawArrowHead(tX1, tY, direction = 1f, color = TopRebarBlue.copy(alpha = 0.8f))
        drawArrowHead(tX2, tY, direction = -1f, color = TopRebarBlue.copy(alpha = 0.8f))
        drawTextAnnotated(
            text = "T=${treadWidth.toInt()}",
            x = (tX1 + tX2) / 2f - 16f, y = tY + 13f,
            color = TopRebarBlue.copy(alpha = 0.8f), size = 11f
        )
    }
}

// ============================================================================
// DRAWING HELPERS
// ============================================================================

/**
 * Draws an arrowhead at (x, y) pointing in [direction] (1 = right/down, -1 = left/up).
 * Set [vertical] to true for vertical arrows.
 */
private fun DrawScope.drawArrowHead(
    x: Float, y: Float,
    direction: Float,
    color: Color,
    vertical: Boolean = false
) {
    val arrowSize = 7f
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

/**
 * Draws text using nativeCanvas for full control over alignment and typeface.
 */
private fun DrawScope.drawTextAnnotated(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    size: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = size
            this.color = color.toArgb()
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.LEFT
            setShadowLayer(2f, 1f, 1f, 0x44000000)
        }
        this.drawText(text, x, y, paint)
    }
}

/**
 * Draws text with a semi-transparent background pill for labels.
 */
private fun DrawScope.drawTextWithBackground(
    text: String,
    x: Float,
    y: Float,
    textColor: Color,
    bgColor: Color,
    textSize: Float = 20f
) {
    val paint = android.graphics.Paint().apply {
        this.textSize = textSize
        color = textColor.toArgb()
        isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val metrics = paint.fontMetrics
    val textW = paint.measureText(text)
    val textH = metrics.descent - metrics.ascent

    drawRoundRect(
        color = bgColor,
        topLeft = Offset(x - 4f, y + metrics.ascent - 2f),
        size = Size(textW + 8f, textH + 4f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/**
 * Draws diagonal hatch lines on a rectangular section (for cut concrete indication).
 */
private fun DrawScope.drawSectionHatch(
    left: Float, top: Float,
    width: Float, height: Float
) {
    val step = 12f
    val hatchColor = HatchColor
    for (offset in -height.toInt()..(width + height).toInt() step step.toInt()) {
        val x1 = left + offset.toFloat()
        val y1 = top
        val x2 = left + offset.toFloat() - height
        val y2 = top + height
        // Clip to rect bounds
        val cx1 = x1.coerceIn(left, left + width)
        val cy1 = when {
            x1 < left -> top + (left - x1)
            x1 > left + width -> top
            else -> top
        }.coerceIn(top, top + height)
        val cx2 = x2.coerceIn(left, left + width)
        val cy2 = when {
            x2 < left -> top + (left - x2)
            x2 > left + width -> top + (x2 - (left + width))
            else -> top + height
        }.coerceIn(top, top + height)

        if (cx1 in left..(left + width) || cx2 in left..(left + width)) {
            drawLine(
                color = hatchColor,
                start = Offset(
                    x = x1.coerceIn(left, left + width),
                    y = y1.coerceIn(top, top + height)
                ),
                end = Offset(
                    x = x2.coerceIn(left, left + width),
                    y = y2.coerceIn(top, top + height)
                ),
                strokeWidth = 0.4f
            )
        }
    }
}

