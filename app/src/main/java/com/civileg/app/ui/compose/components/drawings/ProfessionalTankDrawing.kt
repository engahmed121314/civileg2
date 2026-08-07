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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// COLOR PALETTE — Engineering Drawing Style (white background)
// ============================================================================

private val WaterBlue = Color(0x604A90D9)
private val WaterStroke = Color(0xFF1976D2)
private val ConcreteFill = Color(0xFFD6D6D6)
private val ConcreteStroke = Color(0xFF424242)
private val ConcreteHatch = Color(0xFF999999)
private val SoilBrown = Color(0xFF8B6914)
private val SoilFill = Color(0x55A0794D)
private val RebarColor = Color(0xFF1565C0)
private val StirrupColor = Color(0xFFE65100)
private val DimColor = Color(0xFF333333)
private val DimExtColor = Color(0xFF666666)
private val PressurePink = Color(0x55E91E63)
private val GroundLine = Color(0xFF5D4037)
private val HoopGreen = Color(0xFF2E7D32)
private val TextColor = Color(0xFF212121)
private val TableHeaderBg = Color(0xFF1565C0)
private val TableHeaderText = Color(0xFFFFFFFF)
private val TableRowAlt = Color(0xFFF5F5F5)
private val White = Color(0xFFFFFFFF)
private val TopSlabFill = Color(0xFFBDBDBD)

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

@Composable
fun ProfessionalTankDrawing(
    tankType: String,
    length: Double,
    width: Double,
    height: Double,
    wallThickness: Double,
    baseThickness: Double,
    waterLevel: Double,
    verticalRebarDia: Double,
    verticalRebarSpacing: Double,
    horizontalRebarDia: Double,
    horizontalRebarSpacing: Double,
    foundationDepth: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
    ) {
        val cw = size.width
        val ch = size.height

        val isCircular = tankType.contains("Circular", ignoreCase = true)
        val isElevated = tankType.contains("Elevated", ignoreCase = true)
        val isUnderground = foundationDepth > 0

        // ── Background ──
        drawRect(Color(0xFFFAFAFA))

        // ── Layout: 65% drawing, 35% table ──
        val drawAreaBottom = ch * 0.65f
        val tableTop = ch * 0.66f

        // ── Drawing area margins ──
        val leftMargin = 60f
        val rightMargin = cw * 0.68f  // room for pressure diagram
        val topMargin = 30f

        // ── Ground level ──
        val groundY = if (isUnderground) {
            drawAreaBottom * 0.45f
        } else if (isElevated) {
            drawAreaBottom * 0.82f
        } else {
            drawAreaBottom * 0.82f
        }

        // ── Scale calculation ──
        val totalHeight = height + baseThickness / 1000.0 + (if (isUnderground) foundationDepth else 0.0)
        val drawWidth = rightMargin - leftMargin
        val drawHeight = groundY - topMargin
        val scaleX = drawWidth / max(length, width, 0.5).toFloat()
        val scaleY = drawHeight / max(totalHeight, 0.5).toFloat()
        val scale = min(scaleX, scaleY) * 0.85f

        // ── Tank body pixel dimensions ──
        val twt = max(wallThickness / 1000.0 * scale, 8f)  // wall thickness in px
        val tbt = max(baseThickness / 1000.0 * scale, 6f)  // base thickness in px
        val tankH = height.toFloat() * scale
        val tankW = length.toFloat() * scale
        val tankD = width.toFloat() * scale  // depth for plan
        val wlPx = waterLevel.toFloat() * scale  // water level in px
        val fdPx = foundationDepth.toFloat() * scale

        // ── Tank position (bottom-left of base outer face) ──
        val baseOuterLeft = (cw - tankW) / 2f - 40f
        val baseOuterRight = baseOuterLeft + tankW
        val baseOuterTop = groundY - tbt
        val baseOuterBottom = groundY

        val wallOuterLeft = baseOuterLeft
        val wallOuterRight = baseOuterRight
        val wallInnerLeft = wallOuterLeft + twt
        val wallInnerRight = wallOuterRight - twt
        val wallTop = baseOuterTop - tankH
        val wallBottom = baseOuterTop

        val topSlabH = max(twt * 0.6f, 5f)

        // ── 1. Draw ground line ──
        drawGroundLine(groundY, cw, isUnderground, fdPx, baseOuterBottom)

        // ── 2. Draw elevated columns ──
        if (isElevated) {
            drawElevatedColumns(baseOuterLeft, baseOuterRight, baseOuterBottom, groundY, tankW)
        }

        // ── 3. Draw tank body (U-shape cross section) ──
        if (isCircular) {
            drawCircularTankCrossSection(
                centerX = (baseOuterLeft + baseOuterRight) / 2f,
                baseTop = baseOuterTop,
                radius = tankW / 2f,
                wallT = twt,
                baseT = tbt,
                tankH = tankH,
                topSlabH = topSlabH
            )
        } else {
            drawRectangularTankBody(
                wallOuterLeft, wallOuterRight, wallInnerLeft, wallInnerRight,
                wallTop, wallBottom, baseOuterTop, baseOuterBottom,
                topSlabH, twt
            )
        }

        // ── 4. Draw water fill ──
        val waterTop = wallBottom - wlPx
        if (waterLevel > 0) {
            drawWaterFill(wallInnerLeft, wallInnerRight, waterTop, wallBottom, isCircular,
                centerX = (baseOuterLeft + baseOuterRight) / 2f,
                innerRadius = tankW / 2f - twt
            )
        }

        // ── 5. Draw soil (underground) ──
        if (isUnderground) {
            drawSoilRegion(wallOuterLeft, wallOuterRight, wallBottom, wallBottom + fdPx, cw)
        }

        // ── 6. Draw reinforcement ──
        drawReinforcement(
            isCircular,
            wallOuterLeft, wallOuterRight, wallInnerLeft, wallInnerRight,
            wallTop, wallBottom, baseOuterTop, baseOuterBottom,
            twt, tbt, topSlabH,
            verticalRebarDia, verticalRebarSpacing,
            horizontalRebarDia, horizontalRebarSpacing,
            centerX = (baseOuterLeft + baseOuterRight) / 2f,
            radius = tankW / 2f,
            innerRadius = tankW / 2f - twt
        )

        // ── 7. Draw dimensions ──
        drawDimensions(
            isCircular, isElevated, isUnderground,
            wallOuterLeft, wallOuterRight, wallInnerLeft, wallInnerRight,
            wallTop, wallBottom, baseOuterBottom,
            twt, tbt, tankW, tankH, fdPx,
            centerX = (baseOuterLeft + baseOuterRight) / 2f,
            radius = tankW / 2f
        )

        // ── 8. Draw pressure diagram ──
        drawPressureDiagram(
            isElevated, waterLevel,
            wallOuterRight + 15f, wallTop, wallBottom,
            scale
        )

        // ── 9. Draw plan view (top-right inset) ──
        drawPlanView(
            isCircular,
            cw - 130f, 30f, 100f, 100f,
            tankW, tankD, twt
        )

        // ── 10. Draw title block ──
        drawTitleBlock(tankType, isCircular, cw, ch, length, width, height)

        // ── 11. Draw reinforcement table ──
        drawRebarTable(
            tableTop, cw, ch,
            verticalRebarDia, verticalRebarSpacing,
            horizontalRebarDia, horizontalRebarSpacing,
            wallThickness, baseThickness, isCircular
        )
    }
}

// ============================================================================
// RECTANGULAR TANK BODY — Proper U-shape with top slab
// ============================================================================

private fun DrawScope.drawRectangularTankBody(
    outerL: Float, outerR: Float, innerL: Float, innerR: Float,
    top: Float, bottom: Float, baseTop: Float, baseBottom: Float,
    topSlabH: Float, wallT: Float
) {
    // ── Left wall ──
    val leftWallPath = Path().apply {
        moveTo(outerL, bottom)
        lineTo(outerL, top)
        lineTo(innerL, top)
        lineTo(innerL, bottom)
        close()
    }
    drawPath(leftWallPath, ConcreteFill)
    drawPath(leftWallPath, Stroke(width = 1.5f, color = ConcreteStroke))
    drawConcreteHatching(outerL, top, wallT, bottom - top)

    // ── Right wall ──
    val rightWallPath = Path().apply {
        moveTo(innerR, bottom)
        lineTo(innerR, top)
        lineTo(outerR, top)
        lineTo(outerR, bottom)
        close()
    }
    drawPath(rightWallPath, ConcreteFill)
    drawPath(rightWallPath, Stroke(width = 1.5f, color = ConcreteStroke))
    drawConcreteHatching(innerR, top, wallT, bottom - top)

    // ── Base slab ──
    val basePath = Path().apply {
        moveTo(outerL, bottom)
        lineTo(outerR, bottom)
        lineTo(outerR, baseBottom)
        lineTo(outerL, baseBottom)
        close()
    }
    drawPath(basePath, ConcreteFill)
    drawPath(basePath, Stroke(width = 1.5f, color = ConcreteStroke))
    drawConcreteHatching(outerL, bottom, outerR - outerL, baseBottom - bottom)

    // ── Top slab / cover ──
    val topSlabPath = Path().apply {
        moveTo(outerL, top)
        lineTo(outerR, top)
        lineTo(outerR, top - topSlabH)
        lineTo(outerL, top - topSlabH)
        close()
    }
    drawPath(topSlabPath, TopSlabFill)
    drawPath(topSlabPath, Stroke(width = 1f, color = ConcreteStroke))

    // ── Joint lines (wall-base connections) ──
    drawLine(ConcreteStroke, Offset(outerL, bottom), Offset(innerL, bottom), strokeWidth = 1f)
    drawLine(ConcreteStroke, Offset(innerR, bottom), Offset(outerR, bottom), strokeWidth = 1f)

    // ── Waterproofing indication (inner face dashes) ──
    val wpPath = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
    drawLine(
        Color(0xFF42A5F5), Offset(innerL + 2f, top), Offset(innerL + 2f, bottom),
        strokeWidth = 1f, pathEffect = wpPath
    )
    drawLine(
        Color(0xFF42A5F5), Offset(innerR - 2f, top), Offset(innerR - 2f, bottom),
        strokeWidth = 1f, pathEffect = wpPath
    )
}

// ============================================================================
// CIRCULAR TANK CROSS SECTION — Arc-based U-shape
// ============================================================================

private fun DrawScope.drawCircularTankCrossSection(
    centerX: Float, baseTop: Float,
    radius: Float, wallT: Float, baseT: Float,
    tankH: Float, topSlabH: Float
) {
    val outerR = radius
    val innerR = radius - wallT
    val top = baseTop - tankH
    val bottom = baseTop

    // ── Outer wall arc (half circle, opening up) ──
    val wallPath = Path().apply {
        moveTo(centerX - outerR, bottom)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                centerX - outerR, top - outerR,
                centerX + outerR, bottom + outerR
            ),
            startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false
        )
        lineTo(centerX + innerR, bottom)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                centerX - innerR, top - innerR,
                centerX + innerR, bottom + innerR
            ),
            startAngleDegrees = 0f, sweepAngleDegrees = -180f, forceMoveTo = false
        )
        close()
    }
    drawPath(wallPath, ConcreteFill)
    drawPath(wallPath, Stroke(width = 1.5f, color = ConcreteStroke))

    // ── Hatching on walls ──
    val hatchStep = 6f
    for (angle in -180..0 step 15) {
        val rad = kotlin.math.toRadians(angle.toDouble())
        val ox = centerX + outerR * cos(rad).toFloat()
        val oy = (top + outerR) + outerR * sin(rad).toFloat()
        val ix = centerX + innerR * cos(rad).toFloat()
        val iy = (top + innerR) + innerR * sin(rad).toFloat()
        drawLine(ConcreteHatch, Offset(ix, iy), Offset(ox, oy), strokeWidth = 0.5f)
    }

    // ── Base slab ──
    val baseLeft = centerX - outerR - wallT * 0.5f
    val baseRight = centerX + outerR + wallT * 0.5f
    val basePath = Path().apply {
        moveTo(centerX - outerR, bottom)
        lineTo(centerX + outerR, bottom)
        lineTo(baseRight, bottom + baseT)
        lineTo(baseLeft, bottom + baseT)
        close()
    }
    drawPath(basePath, ConcreteFill)
    drawPath(basePath, Stroke(width = 1.5f, color = ConcreteStroke))
    drawConcreteHatching(baseLeft, bottom, baseRight - baseLeft, baseT)

    // ── Top slab ──
    val topSlabPath = Path().apply {
        moveTo(centerX - outerR, top)
        lineTo(centerX + outerR, top)
        lineTo(centerX + outerR, top - topSlabH)
        lineTo(centerX - outerR, top - topSlabH)
        close()
    }
    drawPath(topSlabPath, TopSlabFill)
    drawPath(topSlabPath, Stroke(width = 1f, color = ConcreteStroke))

    // ── Center line (dashed) ──
    val clPath = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
    drawLine(Color(0xFF9E9E9E), Offset(centerX, top - topSlabH - 10f), Offset(centerX, bottom + baseT + 5f),
        strokeWidth = 0.5f, pathEffect = clPath)

    // ── Label ──
    drawNativeText("O", centerX - 4f, top - topSlabH - 12f, 9f, Color(0xFF9E9E9E))
}

// ============================================================================
// WATER FILL
// ============================================================================

private fun DrawScope.drawWaterFill(
    innerL: Float, innerR: Float, waterTop: Float, waterBottom: Float,
    isCircular: Boolean, centerX: Float, innerRadius: Float
) {
    if (isCircular) {
        // Clip water to circular inner shape
        val waterPath = Path().apply {
            moveTo(centerX - innerRadius, waterBottom)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    centerX - innerRadius, waterBottom - innerRadius * 2,
                    centerX + innerRadius, waterBottom
                ),
                startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false
            )
            lineTo(centerX + innerRadius, waterTop)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    centerX - innerRadius, waterTop - innerRadius * 2,
                    centerX + innerRadius, waterTop
                ),
                startAngleDegrees = 0f, sweepAngleDegrees = -180f, forceMoveTo = false
            )
            close()
        }
        drawPath(waterPath, WaterBlue)
        drawPath(waterPath, Stroke(width = 1f, color = WaterStroke))
    } else {
        val waterPath = Path().apply {
            moveTo(innerL, waterBottom)
            lineTo(innerL, waterTop)
            lineTo(innerR, waterTop)
            lineTo(innerR, waterBottom)
            close()
        }
        drawPath(waterPath, WaterBlue)
        drawPath(waterPath, Stroke(width = 1f, color = WaterStroke))
    }

    // ── Water surface wave ──
    val waveY = waterTop
    val wavePath = Path().apply {
        val startX = if (isCircular) centerX - innerRadius else innerL
        val endX = if (isCircular) centerX + innerRadius else innerR
        moveTo(startX, waveY)
        var x = startX
        while (x < endX) {
            val cpx = x + 4f
            val cpy = waveY - 2f
            val endX2 = x + 8f
            quadraticBezierTo(Offset(cpx, cpy), Offset(endX2, waveY))
            x = endX2
        }
    }
    drawPath(wavePath, Stroke(width = 1f, color = WaterStroke))

    // ── WL label ──
    val labelX = (if (isCircular) centerX + innerRadius else innerR) + 8f
    drawNativeText("WL", labelX, waveY + 4f, 10f, WaterStroke)
}

// ============================================================================
// GROUND LINE & SOIL
// ============================================================================

private fun DrawScope.drawGroundLine(groundY: Float, canvasW: Float, isUnderground: Boolean, fdPx: Float, baseBottom: Float) {
    // Ground line
    drawLine(GroundLine, Offset(20f, groundY), Offset(canvasW - 20f, groundY), strokeWidth = 2f)

    // Ground hatching (below line)
    for (x in 25f..canvasW - 20f step 12f) {
        drawLine(GroundLine, Offset(x, groundY), Offset(x - 6f, groundY + 8f), strokeWidth = 0.8f)
    }

    // GL label
    drawNativeText("GL ±0.00", canvasW - 80f, groundY - 6f, 9f, GroundLine)
}

private fun DrawScope.drawSoilRegion(left: Float, right: Float, top: Float, bottom: Float, canvasW: Float) {
    val soilPath = Path().apply {
        moveTo(left - 20f, top)
        lineTo(right + 20f, top)
        lineTo(canvasW, bottom + 30f)
        lineTo(0f, bottom + 30f)
        close()
    }
    drawPath(soilPath, SoilFill)

    // Soil hatching
    for (x in (left - 20f).toInt()..(right + 20).toInt() step 10) {
        drawLine(SoilBrown, Offset(x.toFloat(), top), Offset(x.toFloat() - 8f, top + 12f), strokeWidth = 0.5f)
    }
}

// ============================================================================
// ELEVATED COLUMNS
// ============================================================================

private fun DrawScope.drawElevatedColumns(baseL: Float, baseR: Float, baseBottom: Float, groundY: Float, tankW: Float) {
    val colW = max(tankW * 0.08f, 10f)
    val colPositions = listOf(baseL + colW, baseR - colW * 2f)

    for (cx in colPositions) {
        // Column body
        drawRect(
            ConcreteFill,
            topLeft = Offset(cx, baseBottom),
            size = Size(colW, groundY - baseBottom)
        )
        drawRect(
            color = ConcreteStroke,
            topLeft = Offset(cx, baseBottom),
            size = Size(colW, groundY - baseBottom),
            style = Stroke(width = 1.5f)
        )

        // Column hatching
        drawConcreteHatching(cx, baseBottom, colW, groundY - baseBottom)
    }

    // Foundation pedestals
    for (cx in colPositions) {
        val pedW = colW * 1.8f
        val pedH = max(8f, colW * 0.4f)
        drawRect(
            ConcreteFill,
            topLeft = Offset(cx - (pedW - colW) / 2f, groundY),
            size = Size(pedW, pedH)
        )
        drawRect(
            ConcreteStroke,
            topLeft = Offset(cx - (pedW - colW) / 2f, groundY),
            size = Size(pedW, pedH),
            style = Stroke(width = 1.5f)
        )
    }
}

// ============================================================================
// REINFORCEMENT
// ============================================================================

private fun DrawScope.drawReinforcement(
    isCircular: Boolean,
    outerL: Float, outerR: Float, innerL: Float, innerR: Float,
    top: Float, bottom: Float, baseTop: Float, baseBottom: Float,
    wallT: Float, baseT: Float, topSlabH: Float,
    vDia: Double, vSpacing: Double,
    hDia: Double, hSpacing: Double,
    centerX: Float, radius: Float, innerRadius: Float
) {
    val vBarR = max(vDia / 2f, 1.5f)
    val hBarR = max(hDia / 2f, 1.2f)
    val vSpPx = max(vSpacing.toFloat() * 0.3f, 15f)  // scaled spacing
    val hSpPx = max(hSpacing.toFloat() * 0.3f, 15f)

    if (isCircular) {
        // ── Circular: Hoops (rings) + vertical bars ──
        // Hoop reinforcement (green rings at spacing intervals)
        val numHoops = ((bottom - top - topSlabH) / hSpPx).toInt().coerceAtLeast(2)
        for (i in 0..numHoops) {
            val y = top + topSlabH + i * (bottom - top - topSlabH) / numHoops
            val hoopR = radius - wallT / 2f
            drawCircle(
                color = HoopGreen, radius = hoopR,
                center = Offset(centerX, y),
                style = Stroke(width = max(hBarR, 1f))
            )
        }

        // Vertical bars (blue dots on inner and outer face)
        val numVertBars = max((2 * kotlin.math.PI * innerRadius / vSpPx).toInt(), 4)
        for (i in 0 until numVertBars) {
            val angle = 2 * kotlin.math.PI * i / numVertBars
            // Outer face bars
            val ox = centerX + radius * cos(angle).toFloat() - wallT / 2f
            val oy = (top + radius) + radius * sin(angle).toFloat()
            drawCircle(RebarColor, vBarR, center = Offset(ox, oy))
            // Inner face bars
            val ix = centerX + innerRadius * cos(angle).toFloat() + wallT / 2f
            val iy = (top + innerRadius) + innerRadius * sin(angle).toFloat()
            drawCircle(RebarColor, vBarR, center = Offset(ix, iy))
        }
    } else {
        // ── Rectangular: Vertical bars + Horizontal stirrups ──

        // Vertical bars on left wall (outer + inner face)
        val wallH = bottom - top - topSlabH
        val numVertBars = max((wallH / vSpPx).toInt(), 2)
        for (i in 0..numVertBars) {
            val y = top + topSlabH + i * wallH / numVertBars
            // Left wall - outer face
            drawCircle(RebarColor, vBarR, center = Offset(outerL + wallT * 0.3f, y))
            // Left wall - inner face
            drawCircle(RebarColor, vBarR, center = Offset(innerL - wallT * 0.3f, y))
            // Right wall - inner face
            drawCircle(RebarColor, vBarR, center = Offset(innerR + wallT * 0.3f, y))
            // Right wall - outer face
            drawCircle(RebarColor, vBarR, center = Offset(outerR - wallT * 0.3f, y))
        }

        // Horizontal stirrups (rectangular ties in each wall)
        val numStirrups = max((wallH / hSpPx).toInt(), 2)
        val stirrupInset = wallT * 0.25f
        for (i in 1 until numStirrups) {
            val y = top + topSlabH + i * wallH / numStirrups
            // Left wall stirrup
            drawRect(
                color = StirrupColor,
                topLeft = Offset(outerL + stirrupInset, y - hSpPx * 0.3f),
                size = Size(wallT - 2 * stirrupInset, hSpPx * 0.6f),
                style = Stroke(width = max(hBarR * 0.8f, 0.8f))
            )
            // Right wall stirrup
            drawRect(
                color = StirrupColor,
                topLeft = Offset(innerR + stirrupInset, y - hSpPx * 0.3f),
                size = Size(wallT - 2 * stirrupInset, hSpPx * 0.6f),
                style = Stroke(width = max(hBarR * 0.8f, 0.8f))
            )
        }

        // Base reinforcement (top and bottom mats)
        val baseW = outerR - outerL
        val numBaseBars = max((baseW / vSpPx).toInt(), 3)
        for (i in 0..numBaseBars) {
            val x = outerL + i * baseW / numBaseBars
            // Bottom mat
            drawCircle(RebarColor, vBarR, center = Offset(x, baseBottom - baseT * 0.25f))
            // Top mat
            drawCircle(RebarColor, vBarR, center = Offset(x, baseTop + baseT * 0.25f))
        }
    }
}

// ============================================================================
// DIMENSIONS
// ============================================================================

private fun DrawScope.drawDimensions(
    isCircular: Boolean, isElevated: Boolean, isUnderground: Boolean,
    outerL: Float, outerR: Float, innerL: Float, innerR: Float,
    top: Float, bottom: Float, baseBottom: Float,
    wallT: Float, baseT: Float, tankW: Float, tankH: Float, fdPx: Float,
    centerX: Float, radius: Float
) {
    val extLen = 8f
    val dimOff = 25f

    // ── Overall height (left side) ──
    val hTop = top - 5f
    val hBot = if (isUnderground) baseBottom + fdPx else baseBottom
    drawDimLine(outerL - dimOff, hTop, outerL - dimOff, hBot, extLen, DimColor)
    drawNativeText("H=${tankH / (tankH / (hBot - hTop))}m", outerL - dimOff - 5f, (hTop + hBot) / 2f, 9f, DimColor)

    // ── Length / Diameter (top) ──
    if (isCircular) {
        drawDimLine(centerX - radius, top - 20f - extLen, centerX + radius, top - 20f - extLen, extLen, DimColor)
        drawNativeText("D=${radius * 2}m", centerX - 15f, top - 20f - extLen - 10f, 9f, DimColor)
    } else {
        drawDimLine(outerL, top - 20f - extLen, outerR, top - 20f - extLen, extLen, DimColor)
        drawNativeText("L", (outerL + outerR) / 2f - 3f, top - 20f - extLen - 10f, 9f, DimColor)
    }

    // ── Wall thickness (detail callout) ──
    if (!isCircular) {
        drawNativeText("t=${wallT.toInt()}mm", outerL + 2f, (top + bottom) / 2f - 4f, 8f, Color(0xFF666666))
    }

    // ── Base thickness ──
    drawNativeText("tb=${baseT.toInt()}mm", outerR + 5f, (bottom + baseBottom) / 2f, 8f, Color(0xFF666666))
}

// ============================================================================
// PRESSURE DIAGRAM
// ============================================================================

private fun DrawScope.drawPressureDiagram(
    isElevated: Boolean, waterLevel: Double,
    startX: Float, top: Float, bottom: Float, scale: Float
) {
    val pH = waterLevel.toFloat() * scale
    val maxW = 30f

    val pressurePath = Path().apply {
        moveTo(startX, bottom)
        lineTo(startX, bottom - pH)
        lineTo(startX + maxW, bottom)
        close()
    }
    drawPath(pressurePath, PressurePink)
    drawPath(pressurePath, Stroke(width = 1f, color = Color(0xFFE91E63)))

    // Labels
    drawNativeText("0", startX + maxW + 3f, bottom - 3f, 8f, Color(0xFFE91E63))
    drawNativeText("wh", startX + maxW + 3f, bottom - pH - 3f, 8f, Color(0xFFE91E63))
    drawNativeText(if (isElevated) "+ seismic" else "hydrostatic", startX, bottom + 14f, 8f, Color(0xFFE91E63))
}

// ============================================================================
// PLAN VIEW (inset)
// ============================================================================

private fun DrawScope.drawPlanView(
    isCircular: Boolean,
    cx: Float, cy: Float, w: Float, h: Float,
    tankW: Float, tankD: Float, wallT: Float
) {
    // Border
    drawRect(Color.White, topLeft = Offset(cx - 5f, cy - 5f), size = Size(w + 10f, h + 10f))
    drawRect(Color(0xFFEEEEEE), topLeft = Offset(cx, cy), size = Size(w, h))
    drawRect(ConcreteStroke, topLeft = Offset(cx, cy), size = Size(w, h), style = Stroke(width = 1f))

    // Label
    drawNativeText("PLAN", cx + w / 2f - 12f, cy - 3f, 8f, DimColor)

    val pw = w * 0.7f
    val ph = h * 0.7f
    val px = cx + (w - pw) / 2f
    val py = cy + (h - ph) / 2f + 5f

    if (isCircular) {
        // Circle plan
        val r = min(pw, ph) / 2f
        drawCircle(ConcreteFill, r, center = Offset(px + pw / 2f, py + ph / 2f))
        drawCircle(ConcreteStroke, r, center = Offset(px + pw / 2f, py + ph / 2f), style = Stroke(width = 1.5f))
        // Inner circle
        drawCircle(Color.White, r - wallT, center = Offset(px + pw / 2f, py + ph / 2f))
        drawCircle(ConcreteStroke, r - wallT, center = Offset(px + pw / 2f, py + ph / 2f), style = Stroke(width = 1f))
        // Hoop indicator dots
        val numDots = 8
        for (i in 0 until numDots) {
            val angle = 2 * kotlin.math.PI * i / numDots
            val dx = (px + pw / 2f) + (r - wallT / 2f) * cos(angle).toFloat()
            val dy = (py + ph / 2f) + (r - wallT / 2f) * sin(angle).toFloat()
            drawCircle(HoopGreen, 2f, center = Offset(dx, dy))
        }
    } else {
        // Rectangle plan
        val planPath = Path().apply {
            moveTo(px, py + ph)
            lineTo(px, py)
            lineTo(px + pw, py)
            lineTo(px + pw, py + ph)
            close()
        }
        val innerPath = Path().apply {
            moveTo(px + wallT, py + ph)
            lineTo(px + wallT, py + wallT)
            lineTo(px + pw - wallT, py + wallT)
            lineTo(px + pw - wallT, py + ph)
            close()
        }
        drawPath(planPath, ConcreteFill)
        drawPath(planPath, Stroke(width = 1.5f, color = ConcreteStroke))
        drawPath(innerPath, Color.White)
        drawPath(innerPath, Stroke(width = 1f, color = ConcreteStroke))

        // Rebar dots in walls
        for (i in 1..3) {
            val fy = py + wallT + i * (ph - wallT) / 4f
            drawCircle(RebarColor, 1.5f, center = Offset(px + wallT / 2f, fy))
            drawCircle(RebarColor, 1.5f, center = Offset(px + pw - wallT / 2f, fy))
        }
    }
}

// ============================================================================
// TITLE BLOCK
// ============================================================================

private fun DrawScope.drawTitleBlock(tankType: String, isCircular: Boolean, cw: Float, ch: Float, length: Double, width: Double, height: Double) {
    // Small title block at top-left
    drawRect(Color(0xFFE3F2FD), topLeft = Offset(5f, 2f), size = Size(200f, 22f), alpha = 0.8f)
    val label = if (isCircular) "CIRCULAR TANK" else "RECTANGULAR TANK"
    drawNativeText(label, 10f, 15f, 10f, Color(0xFF1565C0))
    drawNativeText("${length}x${width}x${height}m", 120f, 15f, 9f, DimColor)
}

// ============================================================================
// REINFORCEMENT TABLE
// ============================================================================

private fun DrawScope.drawRebarTable(
    tableTop: Float, cw: Float, ch: Float,
    vDia: Double, vSpacing: Double,
    hDia: Double, hSpacing: Double,
    wallT: Double, baseT: Double, isCircular: Boolean
) {
    val left = 30f
    val right = cw - 30f
    val tableW = right - left
    val rowH = 22f
    val headerH = 26f
    val cols = floatArrayOf(tableW * 0.28f, tableW * 0.18f, tableW * 0.18f, tableW * 0.18f, tableW * 0.18f)
    var x = left

    // ── Header ──
    drawRect(TableHeaderBg, topLeft = Offset(left, tableTop), size = Size(tableW, headerH))
    val headers = if (isCircular)
        listOf("Direction", "Location", "Dia(mm)", "Spacing(mm)", "As(mm\u00B2/m)")
    else
        listOf("Direction", "Location", "Dia(mm)", "Spacing(mm)", "As(mm\u00B2/m)")

    for ((i, header) in headers.withIndex()) {
        drawRect(Color(0xFF0D47A1), topLeft = Offset(x, tableTop), size = Size(cols[i], headerH), style = Stroke(width = 0.5f))
        drawNativeText(header, x + 4f, tableTop + 16f, 9f, TableHeaderText)
        x += cols[i]
    }

    // ── Data rows ──
    val rows = if (isCircular) {
        listOf(
            listOf("Vertical", "Outer face", "\u03C6$vDia", "$vSpacing", "${(785.4 * vDia * vDia / vSpacing).formatNum()}"),
            listOf("Vertical", "Inner face", "\u03C6$vDia", "$vSpacing", "${(785.4 * vDia * vDia / vSpacing).formatNum()}"),
            listOf("Hoop", "Wall", "\u03C6$hDia", "$hSpacing", "${(785.4 * hDia * hDia / hSpacing).formatNum()}")
        )
    } else {
        listOf(
            listOf("Vertical", "Wall outer", "\u03C6$vDia", "$vSpacing", "${(785.4 * vDia * vDia / vSpacing).formatNum()}"),
            listOf("Vertical", "Wall inner", "\u03C6$vDia", "$vSpacing", "${(785.4 * vDia * vDia / vSpacing).formatNum()}"),
            listOf("Stirrup", "Wall", "\u03C6$hDia", "$hSpacing", "${(785.4 * hDia * hDia / hSpacing).formatNum()}"),
            listOf("Bottom", "Base mat", "\u03C6$vDia", "$vSpacing", "${(785.4 * vDia * vDia / vSpacing).formatNum()}"),
            listOf("Top", "Base mat", "\u03C6$vDia", "$vSpacing", "${(785.4 * vDia * vDia / vSpacing).formatNum()}")
        )
    }

    for ((ri, row) in rows.withIndex()) {
        val y = tableTop + headerH + ri * rowH
        x = left
        val bg = if (ri % 2 == 0) White else TableRowAlt
        drawRect(bg, topLeft = Offset(left, y), size = Size(tableW, rowH))

        for ((ci, cell) in row.withIndex()) {
            drawRect(Color(0xFFE0E0E0), topLeft = Offset(x, y), size = Size(cols[ci], rowH), style = Stroke(width = 0.5f))
            drawNativeText(cell, x + 4f, y + 14f, 9f, TextColor)
            x += cols[ci]
        }
    }
}

// ============================================================================
// HELPERS
// ============================================================================

private fun DrawScope.drawConcreteHatching(x: Float, y: Float, w: Float, h: Float) {
    val step = 8f
    val clipPath = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(x, y, x + w, y + h))
    }
    for (d in -w.toInt()..(w + h).toInt() step step) {
        val x1 = x + d.toFloat()
        val y1 = y
        val x2 = x + d.toFloat() + h
        val y2 = y + h
        // Clip to rect
        val cx1 = x1.coerceIn(x, x + w)
        val cy1 = y + (cx1 - x1).coerceAtLeast(0f)
        val cx2 = (x2).coerceIn(x, x + w)
        val cy2 = y + h - (x2 - cx2).coerceAtLeast(0f)
        if (cx1 < cx2 || (cx1 == cx2 && cy1 < cy2)) {
            drawLine(ConcreteHatch, Offset(cx1, cy1), Offset(cx2, cy2), strokeWidth = 0.5f)
        }
    }
}

private fun DrawScope.drawNativeText(text: String, x: Float, y: Float, size: Float, color: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            textSize = size
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawText(text, x, y, paint)
    }
}

private fun DrawScope.drawDimLine(x1: Float, y1: Float, x2: Float, y2: Float, ext: Float, color: Color) {
    // Extension lines
    drawLine(DimExtColor, Offset(x1, y1 - ext), Offset(x1, y1 + ext), strokeWidth = 0.5f)
    drawLine(DimExtColor, Offset(x2, y2 - ext), Offset(x2, y2 + ext), strokeWidth = 0.5f)
    // Dimension line
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    // Tick marks
    drawLine(color, Offset(x1 - 3f, y1 - 3f), Offset(x1, y1), strokeWidth = 1f)
    drawLine(color, Offset(x1 + 3f, y1 - 3f), Offset(x1, y1), strokeWidth = 1f)
    drawLine(color, Offset(x2 - 3f, y2 - 3f), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x2 + 3f, y2 - 3f), Offset(x2, y2), strokeWidth = 1f)
}

private fun Double.formatNum(): String = if (this >= 100) "%.0f".format(this) else "%.1f".format(this)
