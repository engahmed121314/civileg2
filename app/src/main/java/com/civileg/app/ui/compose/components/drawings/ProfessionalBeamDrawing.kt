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
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// COLOR PALETTE — matches the reference image exactly
// ============================================================================

/** Main bottom rebar color — BLUE */
private val RebarBlue = Color(0xFF4A90D9)

/** Top rebar / secondary steel — LIGHT BLUE */
private val TopRebarBlue = Color(0xFF7EC8E3)

/** Stirrups — PURPLE */
private val StirrupPurple = Color(0xFF9B59B6)

/** Secondary / extra bars — RED */
private val SecondaryRed = Color(0xFFE74C3C)

/** Concrete fill — GRAY */
private val ConcreteGray = Color(0xFF6B6B6B)

/** Concrete top face (lighter for 3D effect) */
private val ConcreteTopGray = Color(0xFF8A8A8A)

/** Concrete side face (darker for 3D effect) */
private val ConcreteSideGray = Color(0xFF505050)

/** Stress diagram color — PINK */
private val StressPink = Color(0xFFE91E8C)

/** Dimension lines and text — WHITE */
private val DimensionWhite = Color(0xFFFFFFFF)

/** Dimension extension lines — GRAY */
private val ExtensionGray = Color(0xFFAAAAAA)

/** Table header background */
private val TableHeaderBg = Color(0x33FFFFFF)

/** Table row alternate */
private val TableRowAlt = Color(0x1AFFFFFF)

/** Development length highlight */
private val DevLengthColor = Color(0xFF4A90D9)

/** Lap splice highlight */
private val LapSpliceColor = Color(0xFFE74C3C)

/** Support color */
private val SupportColor = Color(0xFFCCCCCC)

/** Hatch pattern for cut concrete */
private val HatchColor = Color(0x99AAAAAA)

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

/**
 * Professional 3D Beam Drawing with reinforcement detailing.
 *
 * Renders an isometric-like perspective beam with:
 * - 3D concrete body with shading
 * - Cutaway showing internal reinforcement (main rebar, stirrups, top steel)
 * - Pin / roller supports at beam ends
 * - Full dimensioning (span, section, cover, stirrup spacing)
 * - Development length (La) and lap splice annotations
 * - Inset cross-section view
 * - Reinforcement schedule table
 *
 * Background is transparent — the parent composable supplies the dark theme.
 */
@Composable
fun ProfessionalBeamDrawing(
    beamWidth: Double,         // mm  (b)
    beamDepth: Double,         // mm  (h)
    span: Double,              // mm  (L)
    mainRebarDia: Double,      // mm  (Ø)
    mainRebarCount: Int,       // n
    stirrupDia: Double,        // mm
    stirrupSpacing: Double,    // mm
    cover: Double,             // mm
    developmentLength: Double, // mm  (La)
    lapLength: Double,         // mm  (Lap)
    isContinuous: Boolean = false,
    hasTopSteel: Boolean = false,
    topRebarDia: Double = 0.0,
    topRebarCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(560.dp)
    ) {
        // ---------------------------------------------------------------
        // Layout constants (in px)
        // ---------------------------------------------------------------
        val cw = size.width
        val ch = size.height

        // 3D perspective angles
        val angleX = 0.30f          // horizontal skew factor
        val angleY = 0.20f          // vertical skew factor

        // Main beam drawing area
        val mainLeft = 80f
        val mainRight = cw - 80f
        val mainTop = 50f
        val mainBottom = ch * 0.50f

        // Scaling: fit span into horizontal space
        val availableW = mainRight - mainLeft - 60f   // leave room for La
        val availableH = mainBottom - mainTop - 40f
        val scaleW = availableW / span.toFloat()
        val scaleH = availableH / beamDepth.toFloat()
        val scale = min(scaleW, scaleH) * 0.75f

        val beamDrawW = span.toFloat() * scale
        val beamDrawH = beamDepth.toFloat() * scale
        val beamDrawD = beamWidth.toFloat() * scale * 0.35f   // depth for 3D effect

        val beamLeft = mainLeft + 40f
        val beamTop = mainTop + 40f + beamDrawD * angleY
        val beamRight = beamLeft + beamDrawW
        val beamBottom = beamTop + beamDrawH

        // ---------------------------------------------------------------
        // Draw all layers in order (back-to-front)
        // ---------------------------------------------------------------
        draw3DBeamBody(
            left = beamLeft, top = beamTop,
            w = beamDrawW, h = beamDrawH, d = beamDrawD,
            angleX = angleX, angleY = angleY
        )

        drawSupports(
            beamLeft = beamLeft, beamTop = beamTop,
            beamBottom = beamBottom, beamRight = beamRight,
            isContinuous = isContinuous
        )

        drawCutawayReinforcement(
            beamLeft = beamLeft, beamTop = beamTop,
            beamRight = beamRight, beamBottom = beamBottom,
            beamDrawW = beamDrawW, beamDrawH = beamDrawH,
            beamDrawD = beamDrawD,
            scale = scale,
            angleX = angleX, angleY = angleY,
            mainRebarDia = mainRebarDia, mainRebarCount = mainRebarCount,
            stirrupDia = stirrupDia, stirrupSpacing = stirrupSpacing,
            cover = cover,
            hasTopSteel = hasTopSteel,
            topRebarDia = topRebarDia, topRebarCount = topRebarCount
        )

        drawDevelopmentAndLap(
            beamLeft = beamLeft, beamTop = beamTop,
            beamRight = beamRight, beamBottom = beamBottom,
            beamDrawW = beamDrawW, beamDrawH = beamDrawH,
            scale = scale,
            developmentLength = developmentLength,
            lapLength = lapLength,
            mainRebarDia = mainRebarDia
        )

        drawDimensionLines(
            beamLeft = beamLeft, beamTop = beamTop,
            beamRight = beamRight, beamBottom = beamBottom,
            beamDrawH = beamDrawH,
            scale = scale,
            beamWidth = beamWidth, beamDepth = beamDepth,
            span = span, cover = cover,
            stirrupSpacing = stirrupSpacing
        )

        drawSectionInset(
            cw = cw, ch = ch,
            beamWidth = beamWidth, beamDepth = beamDepth,
            mainRebarDia = mainRebarDia, mainRebarCount = mainRebarCount,
            stirrupDia = stirrupDia,
            cover = cover,
            hasTopSteel = hasTopSteel,
            topRebarDia = topRebarDia, topRebarCount = topRebarCount
        )

        drawReinforcementSchedule(
            cw = cw, ch = ch,
            beamWidth = beamWidth, beamDepth = beamDepth,
            span = span,
            mainRebarDia = mainRebarDia, mainRebarCount = mainRebarCount,
            stirrupDia = stirrupDia, stirrupSpacing = stirrupSpacing,
            hasTopSteel = hasTopSteel,
            topRebarDia = topRebarDia, topRebarCount = topRebarCount,
            developmentLength = developmentLength
        )
    }
}

// ============================================================================
// 1. 3D BEAM BODY — isometric-like with top & side shading
// ============================================================================

/**
 * Draws the 3D beam body with three visible faces:
 * - Front face (darker gray)
 * - Top face (lighter gray, parallelogram)
 * - Right side face (medium gray, parallelogram)
 */
private fun DrawScope.draw3DBeamBody(
    left: Float, top: Float, w: Float, h: Float,
    d: Float, angleX: Float, angleY: Float
) {
    val dx = d * angleX
    val dy = d * angleY

    // --- Front face ---
    val frontPath = Path().apply {
        moveTo(left, top)
        lineTo(left + w, top)
        lineTo(left + w, top + h)
        lineTo(left, top + h)
        close()
    }
    drawPath(
        path = frontPath,
        color = ConcreteGray,
        style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Miter)
    )
    // Fill front with slight gradient
    drawPath(
        path = frontPath,
        brush = Brush.verticalGradient(
            colors = listOf(ConcreteGray, ConcreteSideGray),
            startY = top, endY = top + h
        )
    )

    // --- Top face (lighter) ---
    val topPath = Path().apply {
        moveTo(left, top)
        lineTo(left + dx, top - dy)
        lineTo(left + w + dx, top - dy)
        lineTo(left + w, top)
        close()
    }
    drawPath(
        path = topPath,
        color = ConcreteTopGray,
        style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Miter)
    )
    drawPath(path = topPath, color = ConcreteTopGray)

    // --- Right side face (medium) ---
    val rightPath = Path().apply {
        moveTo(left + w, top)
        lineTo(left + w + dx, top - dy)
        lineTo(left + w + dx, top + h - dy)
        lineTo(left + w, top + h)
        close()
    }
    drawPath(
        path = rightPath,
        color = ConcreteSideGray,
        style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Miter)
    )
    drawPath(path = rightPath, color = ConcreteSideGray)

    // --- Hatching on front face for concrete texture ---
    drawConcreteHatching(left, top, w, h)
}

/**
 * Subtle diagonal hatching lines across the front face to indicate concrete.
 */
private fun DrawScope.drawConcreteHatching(
    left: Float, top: Float, w: Float, h: Float
) {
    val step = 24f
    val paint = HatchColor
    for (offset in -h.toInt()..(w + h).toInt() step step.toInt()) {
        val x1 = left + offset.toFloat()
        val y1 = top
        val x2 = left + offset.toFloat() - h
        val y2 = top + h
        // Clip to front face bounds
        val clipX1 = max(left, min(x1, left + w))
        val clipY1 = when {
            x1 < left -> top + (left - x1)
            x1 > left + w -> top
            else -> top
        }
        val clipX2 = max(left, min(x2, left + w))
        val clipY2 = when {
            x2 < left -> top + (left - x2)
            x2 > left + w -> top + (x2 - (left + w))
            else -> top + h
        }
        if (clipX1 in left..(left + w) || clipX2 in left..(left + w)) {
            drawLine(
                color = paint,
                start = Offset(
                    x = max(left, min(left + w, x1)),
                    y = max(top, min(top + h, y1.coerceAtLeast(top)))
                ),
                end = Offset(
                    x = max(left, min(left + w, x2)),
                    y = min(top + h, y2.coerceAtMost(top + h))
                ),
                strokeWidth = 0.5f
            )
        }
    }
}

// ============================================================================
// 2. SUPPORTS — pin (left) and roller (right)
// ============================================================================

/**
 * Left support: pin support — triangle with a circle at the apex.
 * Right support: roller support — triangle with circles underneath.
 */
private fun DrawScope.drawSupports(
    beamLeft: Float, beamTop: Float,
    beamBottom: Float, beamRight: Float,
    isContinuous: Boolean
) {
    val supportH = 30f
    val supportW = 28f
    val circleR = 4f
    val lineW = 2.dp.toPx()

    val supportPaint = SupportColor
    val groundY = beamBottom + supportH + circleR * 2 + 6f

    // --- Left Pin Support ---
    if (!isContinuous) {
        // Triangle
        val triPath = Path().apply {
            moveTo(beamLeft, beamBottom)
            lineTo(beamLeft - supportW / 2, beamBottom + supportH)
            lineTo(beamLeft + supportW / 2, beamBottom + supportH)
            close()
        }
        drawPath(path = triPath, color = supportPaint)
        drawPath(
            path = triPath, color = supportPaint,
            style = Stroke(width = lineW, join = StrokeJoin.Miter)
        )
        // Pin circle at bottom
        drawCircle(
            color = supportPaint, radius = circleR,
            center = Offset(beamLeft, beamBottom + supportH + circleR + 2f)
        )
        // Ground line
        drawLine(
            color = supportPaint,
            start = Offset(beamLeft - supportW, groundY + circleR * 2 + 4f),
            end = Offset(beamLeft + supportW, groundY + circleR * 2 + 4f),
            strokeWidth = lineW
        )
        // Ground hatching
        for (i in 0..4) {
            val hx = beamLeft - supportW + i * (supportW * 2 / 4)
            drawLine(
                color = supportPaint,
                start = Offset(hx, groundY + circleR * 2 + 4f),
                end = Offset(hx - 6f, groundY + circleR * 2 + 12f),
                strokeWidth = 1.5f
            )
        }
    } else {
        // Continuous beam — fixed support indication
        drawLine(
            color = supportPaint,
            start = Offset(beamLeft, beamBottom),
            end = Offset(beamLeft, beamBottom + 20f),
            strokeWidth = lineW
        )
        // Small hatching for fixed
        for (i in 0..3) {
            val yy = beamBottom + i * 6f
            drawLine(
                color = supportPaint,
                start = Offset(beamLeft, yy),
                end = Offset(beamLeft - 8f, yy + 6f),
                strokeWidth = 1.5f
            )
        }
    }

    // --- Right Roller Support ---
    // Triangle
    val triPathR = Path().apply {
        moveTo(beamRight, beamBottom)
        lineTo(beamRight - supportW / 2, beamBottom + supportH)
        lineTo(beamRight + supportW / 2, beamBottom + supportH)
        close()
    }
    drawPath(path = triPathR, color = supportPaint)
    drawPath(
        path = triPathR, color = supportPaint,
        style = Stroke(width = lineW, join = StrokeJoin.Miter)
    )
    // Roller circles
    drawCircle(
        color = supportPaint, radius = circleR,
        center = Offset(beamRight - 6f, beamBottom + supportH + circleR + 2f)
    )
    drawCircle(
        color = supportPaint, radius = circleR,
        center = Offset(beamRight + 6f, beamBottom + supportH + circleR + 2f)
    )
    // Ground line
    drawLine(
        color = supportPaint,
        start = Offset(beamRight - supportW, groundY + circleR * 2 + 4f),
        end = Offset(beamRight + supportW, groundY + circleR * 2 + 4f),
        strokeWidth = lineW
    )
    // Ground hatching
    for (i in 0..4) {
        val hx = beamRight - supportW + i * (supportW * 2 / 4)
        drawLine(
            color = supportPaint,
            start = Offset(hx, groundY + circleR * 2 + 4f),
            end = Offset(hx - 6f, groundY + circleR * 2 + 12f),
            strokeWidth = 1.5f
        )
    }
}

// ============================================================================
// 3. CUTAWAY REINFORCEMENT — internal bars and stirrups
// ============================================================================

/**
 * Reveals internal reinforcement through a cutaway in the middle of the beam.
 * Shows main bottom rebar (blue), stirrups (purple), and top steel (light blue).
 */
private fun DrawScope.drawCutawayReinforcement(
    beamLeft: Float, beamTop: Float,
    beamRight: Float, beamBottom: Float,
    beamDrawW: Float, beamDrawH: Float,
    beamDrawD: Float,
    scale: Float,
    angleX: Float, angleY: Float,
    mainRebarDia: Double, mainRebarCount: Int,
    stirrupDia: Double, stirrupSpacing: Double,
    cover: Double,
    hasTopSteel: Boolean,
    topRebarDia: Double, topRebarCount: Int
) {
    val dimLineW = 1.dp.toPx()

    // Cover in pixels
    val coverPx = cover.toFloat() * scale

    // Cutaway zone: middle 50% of the beam
    val cutLeft = beamLeft + beamDrawW * 0.25f
    val cutRight = beamLeft + beamDrawW * 0.75f

    // Semi-transparent overlay to indicate cut zone
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset(cutLeft, beamTop),
        size = Size(cutRight - cutLeft, beamDrawH)
    )

    // "CUT" labels
    drawTextAnnotated(
        text = "قطع", x = cutLeft + 2f, y = beamTop - 4f,
        color = ExtensionGray, size = 22f
    )
    drawTextAnnotated(
        text = "قطع", x = cutRight - 30f, y = beamTop - 4f,
        color = ExtensionGray, size = 22f
    )

    // Cut section dashed lines
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
    drawLine(
        color = ExtensionGray,
        start = Offset(cutLeft, beamTop - 8f),
        end = Offset(cutLeft, beamBottom + 8f),
        strokeWidth = dimLineW,
        pathEffect = dashEffect
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(cutRight, beamTop - 8f),
        end = Offset(cutRight, beamBottom + 8f),
        strokeWidth = dimLineW,
        pathEffect = dashEffect
    )

    // --- Main bottom rebar (BLUE) ---
    val mainBarRadius = (mainRebarDia / 2f) * scale
    val mainBarClamped = max(mainBarRadius, 3f)
    val bottomBarY = beamBottom - coverPx - mainBarClamped

    if (mainRebarCount > 0) {
        val usableW = beamDrawW - 2 * coverPx
        val spacing = if (mainRebarCount > 1) {
            (usableW - 2 * mainBarClamped) / (mainRebarCount - 1)
        } else 0f

        for (i in 0 until mainRebarCount) {
            val barX = beamLeft + coverPx + mainBarClamped + i * spacing
            // Only draw bars in the cutaway zone with full visibility
            if (barX in cutLeft..cutRight) {
                drawCircle(
                    color = RebarBlue,
                    radius = mainBarClamped,
                    center = Offset(barX, bottomBarY)
                )
                // Highlight ring
                drawCircle(
                    color = RebarBlue.copy(alpha = 0.4f),
                    radius = mainBarClamped + 2f,
                    center = Offset(barX, bottomBarY),
                    style = Stroke(width = 1f)
                )
            } else {
                // Outside cutaway: draw as faded line (bar running through concrete)
                drawCircle(
                    color = RebarBlue.copy(alpha = 0.25f),
                    radius = mainBarClamped * 0.7f,
                    center = Offset(barX, bottomBarY)
                )
            }
        }

        // Main rebar running lines (extend full length)
        if (mainRebarCount >= 2) {
            val firstBarX = beamLeft + coverPx + mainBarClamped
            val lastBarX = beamLeft + coverPx + mainBarClamped + (mainRebarCount - 1) * spacing
            // Draw the extreme bars as lines through concrete
            drawLine(
                color = RebarBlue.copy(alpha = 0.35f),
                start = Offset(beamLeft, bottomBarY),
                end = Offset(cutLeft, bottomBarY),
                strokeWidth = mainBarClamped * 1.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = RebarBlue.copy(alpha = 0.35f),
                start = Offset(cutRight, bottomBarY),
                end = Offset(beamRight, bottomBarY),
                strokeWidth = mainBarClamped * 1.2f,
                cap = StrokeCap.Round
            )
        }
    }

    // --- Top steel (LIGHT BLUE) ---
    if (hasTopSteel && topRebarCount > 0 && topRebarDia > 0) {
        val topBarRadius = max((topRebarDia / 2f) * scale, 2.5f)
        val topBarY = beamTop + coverPx + topBarRadius
        val topUsableW = beamDrawW - 2 * coverPx
        val topSpacing = if (topRebarCount > 1) {
            (topUsableW - 2 * topBarRadius) / (topRebarCount - 1)
        } else 0f

        for (i in 0 until topRebarCount) {
            val barX = beamLeft + coverPx + topBarRadius + i * topSpacing
            if (barX in cutLeft..cutRight) {
                drawCircle(
                    color = TopRebarBlue,
                    radius = topBarRadius,
                    center = Offset(barX, topBarY)
                )
                drawCircle(
                    color = TopRebarBlue.copy(alpha = 0.4f),
                    radius = topBarRadius + 2f,
                    center = Offset(barX, topBarY),
                    style = Stroke(width = 1f)
                )
            } else {
                drawCircle(
                    color = TopRebarBlue.copy(alpha = 0.25f),
                    radius = topBarRadius * 0.7f,
                    center = Offset(barX, topBarY)
                )
            }
        }

        // Top rebar lines outside cutaway
        drawLine(
            color = TopRebarBlue.copy(alpha = 0.25f),
            start = Offset(beamLeft, topBarY),
            end = Offset(cutLeft, topBarY),
            strokeWidth = topBarRadius * 1.0f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = TopRebarBlue.copy(alpha = 0.25f),
            start = Offset(cutRight, topBarY),
            end = Offset(beamRight, topBarY),
            strokeWidth = topBarRadius * 1.0f,
            cap = StrokeCap.Round
        )
    }

    // --- Stirrups (PURPLE) ---
    val stirrupSpacingPx = stirrupSpacing.toFloat() * scale
    val stirrupLineW = max(stirrupDia.toFloat() * scale * 0.15f, 1.5f)

    if (stirrupSpacingPx > 5f) {
        var sx = cutLeft
        while (sx <= cutRight) {
            // Stirrup inner boundaries
            val stirrupInnerTop = beamTop + coverPx
            val stirrupInnerBottom = beamBottom - coverPx

            // Draw vertical stirrup legs
            drawLine(
                color = StirrupPurple,
                start = Offset(sx, stirrupInnerTop),
                end = Offset(sx, stirrupInnerBottom),
                strokeWidth = stirrupLineW,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f)
            )

            // Bottom horizontal leg of stirrup
            drawLine(
                color = StirrupPurple,
                start = Offset(sx, stirrupInnerBottom),
                end = Offset(
                    sx + min(stirrupSpacingPx * 0.8f, cutRight - sx),
                    stirrupInnerBottom
                ),
                strokeWidth = stirrupLineW
            )

            // Top hook (135° bend indication)
            val hookLen = 10f
            drawLine(
                color = StirrupPurple,
                start = Offset(sx, stirrupInnerTop),
                end = Offset(sx - hookLen, stirrupInnerTop - hookLen * 0.6f),
                strokeWidth = stirrupLineW,
                cap = StrokeCap.Round
            )

            sx += stirrupSpacingPx
        }
    }

    // --- Rebar label inside cutaway ---
    val labelX = cutLeft + (cutRight - cutLeft) / 2f - 40f
    val labelY = beamBottom - coverPx - mainBarClamped - 18f
    drawTextAnnotated(
        text = "${mainRebarCount}Ø${mainRebarDia.toInt()}",
        x = labelX, y = labelY,
        color = RebarBlue, size = 24f
    )

    if (hasTopSteel && topRebarCount > 0) {
        val topLabelY = beamTop + coverPx + max(topRebarDia / 2f * scale, 2.5f) + 22f
        drawTextAnnotated(
            text = "${topRebarCount}Ø${topRebarDia.toInt()}",
            x = labelX, y = topLabelY,
            color = TopRebarBlue, size = 22f
        )
    }

    // Stirrup label
    drawTextAnnotated(
        text = "Ø${stirrupDia.toInt()} @ ${stirrupSpacing.toInt()}",
        x = cutRight - 80f, y = beamTop + coverPx + 18f,
        color = StirrupPurple, size = 20f
    )
}

// ============================================================================
// 4. DEVELOPMENT LENGTH & LAP SPLICE
// ============================================================================

/**
 * Shows La (development length) as annotated bar extending into left support.
 * Shows lap splice zone with overlapping bars at right side.
 */
private fun DrawScope.drawDevelopmentAndLap(
    beamLeft: Float, beamTop: Float,
    beamRight: Float, beamBottom: Float,
    beamDrawW: Float, beamDrawH: Float,
    scale: Float,
    developmentLength: Double,
    lapLength: Double,
    mainRebarDia: Double
) {
    val laPx = developmentLength.toFloat() * scale
    val lapPx = lapLength.toFloat() * scale
    val barR = max((mainRebarDia / 2f) * scale, 3f)
    val mainBarY = beamBottom - (beamDrawH * 0.15f)  // approximate bottom bar position

    // --- Development Length (La) on LEFT side ---
    // The bar extends into the support
    val laEndX = beamLeft - laPx.coerceAtMost(80f)

    // Draw the extended bar into support
    drawLine(
        color = DevLengthColor,
        start = Offset(laEndX, mainBarY),
        end = Offset(beamLeft, mainBarY),
        strokeWidth = barR * 2f,
        cap = StrokeCap.Round
    )
    // 90° hook at end
    drawLine(
        color = DevLengthColor,
        start = Offset(laEndX, mainBarY - barR),
        end = Offset(laEndX, mainBarY + barR),
        strokeWidth = barR * 2f,
        cap = StrokeCap.Round
    )

    // La dimension bracket
    val bracketY = mainBarY + barR + 16f
    drawLine(
        color = DevLengthColor,
        start = Offset(laEndX, bracketY),
        end = Offset(beamLeft, bracketY),
        strokeWidth = 1.5.dp.toPx()
    )
    // Arrows
    drawArrowHead(laEndX, bracketY, direction = 1f, color = DevLengthColor)
    drawArrowHead(beamLeft, bracketY, direction = -1f, color = DevLengthColor)
    // Extension lines
    drawLine(
        color = ExtensionGray, start = Offset(laEndX, mainBarY + barR + 2f),
        end = Offset(laEndX, bracketY + 6f), strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = ExtensionGray, start = Offset(beamLeft, mainBarY + barR + 2f),
        end = Offset(beamLeft, bracketY + 6f), strokeWidth = 1.dp.toPx()
    )
    // Label
    drawTextAnnotated(
        text = "La = ${developmentLength.toInt()} mm",
        x = laEndX - 10f, y = bracketY + 22f,
        color = DevLengthColor, size = 22f
    )

    // --- Lap Splice Zone on RIGHT side ---
    val lapZoneStart = beamRight - lapPx.coerceAtMost(beamDrawW * 0.15f)
    val lapZoneEnd = beamRight

    // Highlight lap zone
    drawRect(
        color = LapSpliceColor.copy(alpha = 0.12f),
        topLeft = Offset(lapZoneStart, beamTop - 4f),
        size = Size(lapZoneEnd - lapZoneStart, beamDrawH + 8f)
    )

    // Two overlapping bars
    drawLine(
        color = RebarBlue,
        start = Offset(lapZoneStart - 20f, mainBarY - 4f),
        end = Offset(lapZoneEnd, mainBarY - 4f),
        strokeWidth = barR * 1.6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = SecondaryRed,
        start = Offset(lapZoneStart, mainBarY + 4f),
        end = Offset(lapZoneEnd + 20f, mainBarY + 4f),
        strokeWidth = barR * 1.6f,
        cap = StrokeCap.Round
    )

    // Lap dimension bracket (below beam)
    val lapBracketY = beamBottom + 50f
    drawLine(
        color = LapSpliceColor,
        start = Offset(lapZoneStart, lapBracketY),
        end = Offset(lapZoneEnd, lapBracketY),
        strokeWidth = 1.5.dp.toPx()
    )
    drawArrowHead(lapZoneStart, lapBracketY, direction = 1f, color = LapSpliceColor)
    drawArrowHead(lapZoneEnd, lapBracketY, direction = -1f, color = LapSpliceColor)

    // Extension lines
    drawLine(
        color = ExtensionGray,
        start = Offset(lapZoneStart, beamBottom + 8f),
        end = Offset(lapZoneStart, lapBracketY + 6f),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(lapZoneEnd, beamBottom + 8f),
        end = Offset(lapZoneEnd, lapBracketY + 6f),
        strokeWidth = 1.dp.toPx()
    )

    // Label
    drawTextAnnotated(
        text = "Lap = ${lapLength.toInt()} mm",
        x = lapZoneStart + 5f, y = lapBracketY + 20f,
        color = LapSpliceColor, size = 22f
    )
}

// ============================================================================
// 5. DIMENSION LINES — span, section, cover, stirrup spacing
// ============================================================================

/**
 * Draws comprehensive dimensioning:
 * - Overall span at bottom
 * - Beam width × depth at side
 * - Cover dimension
 * - Stirrup spacing
 */
private fun DrawScope.drawDimensionLines(
    beamLeft: Float, beamTop: Float,
    beamRight: Float, beamBottom: Float,
    beamDrawH: Float,
    scale: Float,
    beamWidth: Double, beamDepth: Double,
    span: Double, cover: Double,
    stirrupSpacing: Double
) {
    val dimLineW = 1.dp.toPx()
    val extLineW = 0.8.dp.toPx()
    val arrowSize = 8f

    // --- Overall SPAN dimension (bottom) ---
    val spanY = beamBottom + 80f
    drawLine(
        color = DimensionWhite,
        start = Offset(beamLeft, spanY),
        end = Offset(beamRight, spanY),
        strokeWidth = dimLineW
    )
    drawArrowHead(beamLeft, spanY, direction = 1f, color = DimensionWhite)
    drawArrowHead(beamRight, spanY, direction = -1f, color = DimensionWhite)

    // Extension lines
    drawLine(
        color = ExtensionGray,
        start = Offset(beamLeft, beamBottom + 4f),
        end = Offset(beamLeft, spanY + 6f),
        strokeWidth = extLineW
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(beamRight, beamBottom + 4f),
        end = Offset(beamRight, spanY + 6f),
        strokeWidth = extLineW
    )

    // Span label
    val spanLabel = "L = ${span.toInt()} mm"
    val spanLabelX = beamLeft + (beamRight - beamLeft) / 2f
    drawTextAnnotated(
        text = spanLabel, x = spanLabelX - 50f, y = spanY + 22f,
        color = DimensionWhite, size = 26f
    )

    // --- Beam DEPTH dimension (right side) ---
    val depthX = beamRight + 40f
    drawLine(
        color = DimensionWhite,
        start = Offset(depthX, beamTop),
        end = Offset(depthX, beamBottom),
        strokeWidth = dimLineW
    )
    drawArrowHead(depthX, beamTop, direction = 1f, color = DimensionWhite, vertical = true)
    drawArrowHead(depthX, beamBottom, direction = -1f, color = DimensionWhite, vertical = true)

    // Extension lines
    drawLine(
        color = ExtensionGray,
        start = Offset(beamRight + 4f, beamTop),
        end = Offset(depthX + 6f, beamTop),
        strokeWidth = extLineW
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(beamRight + 4f, beamBottom),
        end = Offset(depthX + 6f, beamBottom),
        strokeWidth = extLineW
    )

    // Depth label (rotated)
    withTransform({
        rotate(degrees = -90f, pivot = Offset(depthX + 22f, beamTop + beamDrawH / 2f))
    }) {
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = 22f
                color = android.graphics.Color.WHITE
                isFakeBoldText = true
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(
                "h = ${beamDepth.toInt()}",
                depthX + 22f,
                beamTop + beamDrawH / 2f + 6f,
                paint
            )
        }
    }

    // --- Section label (b × h) near depth dimension ---
    drawTextAnnotated(
        text = "${beamWidth.toInt()}×${beamDepth.toInt()}",
        x = beamRight + 60f, y = beamTop + beamDrawH / 2f + 6f,
        color = DimensionWhite, size = 24f
    )

    // --- COVER dimension (left side, small) ---
    val coverPx = cover.toFloat() * scale
    val coverX = beamLeft - 30f
    val coverTopY = beamBottom - coverPx
    val coverBottomY = beamBottom

    drawLine(
        color = ExtensionGray,
        start = Offset(coverX, coverTopY),
        end = Offset(coverX, coverBottomY),
        strokeWidth = 0.8.dp.toPx()
    )
    // Extension lines
    drawLine(
        color = ExtensionGray,
        start = Offset(beamLeft - 2f, coverTopY),
        end = Offset(coverX - 4f, coverTopY),
        strokeWidth = 0.6.dp.toPx()
    )
    drawLine(
        color = ExtensionGray,
        start = Offset(beamLeft - 2f, coverBottomY),
        end = Offset(coverX - 4f, coverBottomY),
        strokeWidth = 0.6.dp.toPx()
    )
    // Cover label
    withTransform({
        rotate(degrees = -90f, pivot = Offset(coverX - 14f, coverTopY + coverPx / 2f))
    }) {
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textSize = 16f
                color = android.graphics.Color.parseColor("#AAAAAA")
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(
                "c=${cover.toInt()}",
                coverX - 14f,
                coverTopY + coverPx / 2f + 4f,
                paint
            )
        }
    }

    // --- STIRRUP SPACING dimension (inside beam, in cutaway zone) ---
    val stirrupSpacingPx = stirrupSpacing.toFloat() * scale
    val ssY = beamTop + coverPx + 30f
    val ssLeft = beamLeft + (beamRight - beamLeft) * 0.35f
    val ssRight = ssLeft + stirrupSpacingPx.coerceAtMost(60f)

    drawLine(
        color = StirrupPurple.copy(alpha = 0.7f),
        start = Offset(ssLeft, ssY),
        end = Offset(ssRight, ssY),
        strokeWidth = 0.8.dp.toPx()
    )
    drawArrowHead(ssLeft, ssY, direction = 1f, color = StirrupPurple.copy(alpha = 0.7f))
    drawArrowHead(ssRight, ssY, direction = -1f, color = StirrupPurple.copy(alpha = 0.7f))

    drawTextAnnotated(
        text = "@${stirrupSpacing.toInt()}",
        x = ssLeft + 2f, y = ssY - 6f,
        color = StirrupPurple.copy(alpha = 0.8f), size = 18f
    )
}

// ============================================================================
// 6. SECTION VIEW INSET — cross-section in top-right corner
// ============================================================================

/**
 * Draws a smaller inset cross-section view in the top-right area.
 * Shows rectangular outline, rebar circles, stirrup rectangle, and cover dims.
 */
private fun DrawScope.drawSectionInset(
    cw: Float, ch: Float,
    beamWidth: Double, beamDepth: Double,
    mainRebarDia: Double, mainRebarCount: Int,
    stirrupDia: Double,
    cover: Double,
    hasTopSteel: Boolean,
    topRebarDia: Double, topRebarCount: Int
) {
    // Inset position and size
    val insetW = min(160f, cw * 0.22f)
    val insetH = min(220f, ch * 0.40f)
    val insetLeft = cw - insetW - 16f
    val insetTop = 12f

    // Background panel
    drawRoundRect(
        color = Color(0x22000000),
        topLeft = Offset(insetLeft - 8f, insetTop - 8f),
        size = Size(insetW + 16f, insetH + 16f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Title
    drawTextAnnotated(
        text = "المقطع", x = insetLeft, y = insetTop - 2f,
        color = DimensionWhite, size = 20f
    )

    // Section drawing area inside inset
    val padding = 16f
    val secAvailW = insetW - 2 * padding
    val secAvailH = insetH - 36f - 2 * padding  // leave room for title
    val secScale = min(secAvailW / beamWidth.toFloat(), secAvailH / beamDepth.toFloat())

    val secW = beamWidth.toFloat() * secScale
    val secH = beamDepth.toFloat() * secScale
    val secLeft = insetLeft + (insetW - secW) / 2f
    val secTop = insetTop + 28f

    // Concrete rectangle
    drawRect(
        color = ConcreteGray,
        topLeft = Offset(secLeft, secTop),
        size = Size(secW, secH)
    )
    drawRect(
        color = DimensionWhite.copy(alpha = 0.5f),
        topLeft = Offset(secLeft, secTop),
        size = Size(secW, secH),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Stirrup rectangle (dashed)
    val covPx = cover.toFloat() * secScale
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f), 0f)
    drawRect(
        color = StirrupPurple,
        topLeft = Offset(secLeft + covPx, secTop + covPx),
        size = Size(secW - 2 * covPx, secH - 2 * covPx),
        style = Stroke(width = 1.5f, pathEffect = dashEffect)
    )

    // Main bottom rebar circles (BLUE)
    val mainR = max((mainRebarDia / 2f) * secScale, 2.5f)
    val bottomY = secTop + secH - covPx - mainR
    if (mainRebarCount > 0) {
        val usableW = secW - 2 * covPx
        val spacing = if (mainRebarCount > 1) (usableW - 2 * mainR) / (mainRebarCount - 1) else 0f
        for (i in 0 until mainRebarCount) {
            val bx = secLeft + covPx + mainR + i * spacing
            drawCircle(color = RebarBlue, radius = mainR, center = Offset(bx, bottomY))
        }
    }

    // Top rebar circles (LIGHT BLUE)
    if (hasTopSteel && topRebarCount > 0 && topRebarDia > 0) {
        val topR = max((topRebarDia / 2f) * secScale, 2f)
        val topY = secTop + covPx + topR
        val usableW = secW - 2 * covPx
        val spacing = if (topRebarCount > 1) (usableW - 2 * topR) / (topRebarCount - 1) else 0f
        for (i in 0 until topRebarCount) {
            val bx = secLeft + covPx + topR + i * spacing
            drawCircle(color = TopRebarBlue, radius = topR, center = Offset(bx, topY))
        }
    }

    // Cover dimension (small, left side)
    val cDimX = secLeft - 12f
    drawLine(
        color = ExtensionGray,
        start = Offset(cDimX, secTop),
        end = Offset(cDimX, secTop + covPx),
        strokeWidth = 0.7.dp.toPx()
    )
    drawTextAnnotated(
        text = "${cover.toInt()}", x = cDimX - 18f, y = secTop + covPx / 2f + 4f,
        color = ExtensionGray, size = 14f
    )

    // Section label: b × h
    drawTextAnnotated(
        text = "${beamWidth.toInt()}×${beamDepth.toInt()}",
        x = secLeft + secW / 2f - 28f, y = secTop + secH + 18f,
        color = DimensionWhite, size = 18f
    )
}

// ============================================================================
// 7. REINFORCEMENT SCHEDULE TABLE — bottom area
// ============================================================================

/**
 * Draws a small reinforcement schedule table at the bottom.
 * Columns: Bar Mark | Diameter | Count | Length | Spacing
 */
private fun DrawScope.drawReinforcementSchedule(
    cw: Float, ch: Float,
    beamWidth: Double, beamDepth: Double,
    span: Double,
    mainRebarDia: Double, mainRebarCount: Int,
    stirrupDia: Double, stirrupSpacing: Double,
    hasTopSteel: Boolean,
    topRebarDia: Double, topRebarCount: Int,
    developmentLength: Double
) {
    val tableLeft = 16f
    val tableTop = ch * 0.68f
    val tableW = cw - 32f
    val rowH = 26f
    val headerH = 30f
    val colWidths = floatArrayOf(
        tableW * 0.14f,  // Mark
        tableW * 0.16f,  // Diameter
        tableW * 0.12f,  // Count
        tableW * 0.28f,  // Length
        tableW * 0.30f   // Spacing
    )

    // Table title
    drawTextAnnotated(
        text = "جدول التسليح",
        x = tableLeft, y = tableTop - 8f,
        color = DimensionWhite, size = 22f
    )

    // Header row background
    drawRect(
        color = TableHeaderBg,
        topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH)
    )

    // Header texts
    val headers = arrayOf("العلامة", "القطر", "العدد", "الطول", "المسافة")
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(
            text = headers[i], x = cx + 6f, y = tableTop + headerH / 2f + 6f,
            color = DimensionWhite, size = 18f
        )
        cx += colWidths[i]
    }

    // Row separator
    drawLine(
        color = ExtensionGray.copy(alpha = 0.3f),
        start = Offset(tableLeft, tableTop + headerH),
        end = Offset(tableLeft + tableW, tableTop + headerH),
        strokeWidth = 0.5.dp.toPx()
    )

    // --- Row 1: Main bottom bars ---
    val row1Y = tableTop + headerH
    // Alternate row background
    drawRect(
        color = TableRowAlt,
        topLeft = Offset(tableLeft, row1Y),
        size = Size(tableW, rowH)
    )

    val mainBarLength = span + 2 * developmentLength
    val row1Data = arrayOf(
        "T1",
        "Ø${mainRebarDia.toInt()}",
        "${mainRebarCount}",
        "${mainBarLength.toInt()} mm",
        "—"
    )
    cx = tableLeft
    for (i in row1Data.indices) {
        drawTextAnnotated(
            text = row1Data[i], x = cx + 6f, y = row1Y + rowH / 2f + 5f,
            color = RebarBlue, size = 16f
        )
        cx += colWidths[i]
    }

    // Separator
    drawLine(
        color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, row1Y + rowH),
        end = Offset(tableLeft + tableW, row1Y + rowH),
        strokeWidth = 0.5.dp.toPx()
    )

    // --- Row 2: Stirrups ---
    val row2Y = row1Y + rowH
    val stirrupCount = ((span / stirrupSpacing) + 1).toInt()
    val stirrupLength = 2 * (beamWidth + beamDepth) - 8 * 25  // approximate
    val row2Data = arrayOf(
        "S1",
        "Ø${stirrupDia.toInt()}",
        "${stirrupCount}",
        "${stirrupLength.toInt()} mm",
        "@ ${stirrupSpacing.toInt()} mm"
    )
    cx = tableLeft
    for (i in row2Data.indices) {
        drawTextAnnotated(
            text = row2Data[i], x = cx + 6f, y = row2Y + rowH / 2f + 5f,
            color = StirrupPurple, size = 16f
        )
        cx += colWidths[i]
    }

    // Separator
    drawLine(
        color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, row2Y + rowH),
        end = Offset(tableLeft + tableW, row2Y + rowH),
        strokeWidth = 0.5.dp.toPx()
    )

    // --- Row 3: Top bars (if present) ---
    if (hasTopSteel && topRebarCount > 0 && topRebarDia > 0) {
        val row3Y = row2Y + rowH
        drawRect(
            color = TableRowAlt,
            topLeft = Offset(tableLeft, row3Y),
            size = Size(tableW, rowH)
        )
        val topBarLength = span * 0.3  // top bars typically shorter
        val row3Data = arrayOf(
            "T2",
            "Ø${topRebarDia.toInt()}",
            "${topRebarCount}",
            "${topBarLength.toInt()} mm",
            "—"
        )
        cx = tableLeft
        for (i in row3Data.indices) {
            drawTextAnnotated(
                text = row3Data[i], x = cx + 6f, y = row3Y + rowH / 2f + 5f,
                color = TopRebarBlue, size = 16f
            )
            cx += colWidths[i]
        }
    }

    // Column separators (vertical lines)
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
        drawLine(
            color = ExtensionGray.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop),
            end = Offset(sepX, tableTop + headerH + rowH * (if (hasTopSteel && topRebarCount > 0) 3 else 2)),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    // Table border
    val totalRows = if (hasTopSteel && topRebarCount > 0) 3 else 2
    val tableTotalH = headerH + rowH * totalRows
    drawRect(
        color = ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, tableTotalH),
        style = Stroke(width = 1.dp.toPx())
    )
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
        // Horizontal arrow
        path.moveTo(x, y)
        path.lineTo(x - direction * arrowSize, y - arrowSize * 0.5f)
        path.lineTo(x - direction * arrowSize, y + arrowSize * 0.5f)
        path.close()
    } else {
        // Vertical arrow
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
            this.color = color
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.LEFT
            // Add subtle shadow for readability
            setShadowLayer(2f, 1f, 1f, 0x44000000)
        }
        drawText(text, x, y, paint)
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
        color = textColor
        isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val metrics = paint.fontMetrics
    val textW = paint.measureText(text)
    val textH = metrics.descent - metrics.ascent

    // Background pill
    drawRoundRect(
        color = bgColor,
        topLeft = Offset(x - 4f, y + metrics.ascent - 2f),
        size = Size(textW + 8f, textH + 4f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

// ============================================================================
// STRESS DIAGRAM (optional visual enhancement)
// ============================================================================

/**
 * Draws a simplified stress distribution diagram above the beam
 * (triangular compression zone + rectangular tension zone).
 */
private fun DrawScope.drawStressDiagram(
    beamLeft: Float, beamTop: Float,
    beamRight: Float, beamDrawW: Float,
    neutralAxisDepth: Float = 0.2f  // fraction of depth
) {
    val diagramH = 30f
    val diagramTop = beamTop - diagramH - 12f
    val naX = beamLeft + beamDrawW * neutralAxisDepth

    // Compression triangle (left side)
    val compPath = Path().apply {
        moveTo(beamLeft, diagramTop)
        lineTo(naX, diagramTop)
        lineTo(naX, diagramTop + diagramH)
        close()
    }
    drawPath(path = compPath, color = StressPink.copy(alpha = 0.3f))
    drawPath(
        path = compPath, color = StressPink,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Tension rectangle (right side)
    drawRect(
        color = StressPink.copy(alpha = 0.15f),
        topLeft = Offset(naX, diagramTop),
        size = Size(beamRight - naX, diagramH)
    )
    drawRect(
        color = StressPink,
        topLeft = Offset(naX, diagramTop),
        size = Size(beamRight - naX, diagramH),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Labels
    drawTextAnnotated(
        text = "ضغط", x = beamLeft + 4f, y = diagramTop + diagramH / 2f + 5f,
        color = StressPink, size = 16f
    )
    drawTextAnnotated(
        text = "شد", x = naX + (beamRight - naX) / 2f - 10f, y = diagramTop + diagramH / 2f + 5f,
        color = StressPink.copy(alpha = 0.7f), size = 16f
    )

    // N.A. line
    val naDash = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
    drawLine(
        color = StressPink,
        start = Offset(naX, diagramTop - 4f),
        end = Offset(naX, diagramTop + diagramH + 4f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = naDash
    )
}