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

// ============================================================================
// COLOR PALETTE
// ============================================================================

private val WaterBlue = Color(0x804A90D9)        // 50% alpha water fill
private val WaterStroke = Color(0xFF4A90D9)       // water outline
private val ConcreteGray = Color(0xFF888888)       // concrete fill
private val ConcreteTopGray = Color(0xFF9A9A9A)    // 3D top face
private val ConcreteSideGray = Color(0xFF666666)   // 3D side face
private val SoilBrown = Color(0xFF8B4513)          // soil hatching
private val SoilFill = Color(0x668B4513)           // soil fill
private val RebarBlue = Color(0xFF4A90D9)          // main vertical rebar
private val RebarLightBlue = Color(0xFF7EC8E3)     // horizontal rebar
private val DimensionWhite = Color(0xFFFFFFFF)
private val ExtensionGray = Color(0xFFAAAAAA)
private val TableHeaderBg = Color(0x33FFFFFF)
private val TableRowAlt = Color(0x1AFFFFFF)
private val PressurePink = Color(0xFFE91E8C)
private val GroundLineBrown = Color(0xFFA0522D)
private val HoopGreen = Color(0xFF4CAF50)          // hoop reinforcement for circular

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

/**
 * Professional Water Tank Engineering Drawing
 * Renders elevation view, plan view, wall detail inset,
 * water pressure diagram, and reinforcement table.
 * Supports: RectangularGround, CircularGround, RectangularElevated, CircularElevated
 */
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
            .height(720.dp)
    ) {
        val cw = size.width
        val ch = size.height

        val isCircular = tankType.contains("Circular")
        val isElevated = tankType.contains("Elevated")
        val isUnderground = foundationDepth > 0

        // ── Layout zones ──
        val margin = 30f
        val mainTop = 50f
        val mainBottom = ch * 0.58f
        val mainLeft = 100f
        val mainRight = cw - 180f

        // ── Scaling ──
        val effectiveHeight = height + baseThickness + foundationDepth
        val effectiveLength = if (isCircular) length else length
        val scaleX = (mainRight - mainLeft) / effectiveLength.toFloat()
        val scaleY = (mainBottom - mainTop) / effectiveHeight.toFloat()
        val scale = min(scaleX, scaleY) * 0.72f

        val drawL = length.toFloat() * scale
        val drawH = height.toFloat() * scale
        val drawWT = wallThickness.toFloat() * scale
        val drawBT = baseThickness.toFloat() * scale
        val drawWL = waterLevel.toFloat() * scale
        val drawFD = foundationDepth.toFloat() * scale
        val cover = 40f * scale

        val tankLeft = mainLeft + (mainRight - mainLeft - drawL) / 2f
        val tankTop = mainTop + 20f + (if (isUnderground) drawFD else 0f)
        val tankRight = tankLeft + drawL
        val tankBottom = tankTop + drawH
        val baseBottom = tankBottom + drawBT

        // ── Draw layers ──
        if (isUnderground) {
            drawSoilBelowBase(cw, ch, tankLeft, baseBottom, drawWT, tankRight, drawFD, tankTop)
        }

        if (isElevated) {
            drawElevatedSupports(tankLeft, tankRight, baseBottom, ch)
        }

        if (isCircular) {
            drawCircularElevation(tankLeft, tankTop, drawL, drawH, drawWT, drawBT, drawWL)
        } else {
            drawRectangularElevation(tankLeft, tankTop, drawL, drawH, drawWT, drawBT, drawWL)
        }

        // Water fill
        if (drawWL > 0) {
            drawWaterFill(tankLeft, tankTop, drawL, drawH, drawWT, drawWL, isCircular)
        }

        // Reinforcement on elevation
        drawElevationReinforcement(
            tankLeft, tankTop, tankRight, tankBottom, baseBottom,
            drawWT, drawBT, drawWL, cover, scale,
            verticalRebarDia, verticalRebarSpacing,
            horizontalRebarDia, horizontalRebarSpacing,
            isCircular
        )

        // Ground level line
        if (isUnderground) {
            val glY = tankTop - drawFD
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            drawLine(
                color = GroundLineBrown,
                start = Offset(tankLeft - 60f, glY),
                end = Offset(tankRight + 60f, glY),
                strokeWidth = 2f,
                pathEffect = dashEffect
            )
            drawTextAnnotated("GL ±0.00", tankLeft - 90f, glY - 6f, GroundLineBrown, 16f)
        }

        // Dimension lines
        drawTankDimensions(
            tankLeft, tankTop, tankRight, tankBottom, baseBottom,
            drawWT, drawBT, drawL, drawH, isCircular, length
        )

        // Plan view (top-right)
        drawPlanView(cw, tankType, length, width, isCircular)

        // Wall detail inset
        drawWallDetailInset(cw, ch, wallThickness, verticalRebarDia, horizontalRebarDia, cover, scale)

        // Water pressure diagram
        drawWaterPressureDiagram(tankLeft, tankTop, drawL, drawH, drawWL, waterLevel, isElevated)

        // Reinforcement table
        drawReinforcementTable(cw, ch, tankType, verticalRebarDia, verticalRebarSpacing,
            horizontalRebarDia, horizontalRebarSpacing, height, length)
    }
}

// ============================================================================
// 1. RECTANGULAR TANK ELEVATION
// ============================================================================

private fun DrawScope.drawRectangularElevation(
    left: Float, top: Float, l: Float, h: Float,
    wt: Float, bt: Float, wl: Float
) {
    // Left wall
    drawRect(
        color = ConcreteGray,
        topLeft = Offset(left, top),
        size = Size(wt, h + bt)
    )
    // Right wall
    drawRect(
        color = ConcreteGray,
        topLeft = Offset(left + l - wt, top),
        size = Size(wt, h + bt)
    )
    // Base slab
    drawRect(
        color = ConcreteGray,
        topLeft = Offset(left, top + h),
        size = Size(l, bt)
    )
    // 3D shading - left wall
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(ConcreteGray, ConcreteSideGray)
        ),
        topLeft = Offset(left, top),
        size = Size(wt, h + bt)
    )
    // 3D shading - right wall (lighter from inside)
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(ConcreteTopGray, ConcreteGray)
        ),
        topLeft = Offset(left + l - wt, top),
        size = Size(wt, h + bt)
    )
    // Borders
    val borderPath = Path().apply {
        // Outer
        moveTo(left, top)
        lineTo(left + l, top)
        lineTo(left + l, top + h + bt)
        lineTo(left, top + h + bt)
        close()
        // Inner cutout
        moveTo(left + wt, top)
        lineTo(left + wt, top + h)
        lineTo(left + l - wt, top + h)
        lineTo(left + l - wt, top)
        close()
    }
    drawPath(path = borderPath, color = DimensionWhite.copy(alpha = 0.5f),
        style = Stroke(width = 1.5f))
    // Concrete hatching on walls
    drawConcreteHatchingOnRect(left, top, wt, h + bt)
    drawConcreteHatchingOnRect(left + l - wt, top, wt, h + bt)
    drawConcreteHatchingOnRect(left, top + h, l, bt)
}

// ============================================================================
// 2. CIRCULAR TANK ELEVATION (section through diameter)
// ============================================================================

private fun DrawScope.drawCircularElevation(
    left: Float, top: Float, l: Float, h: Float,
    wt: Float, bt: Float, wl: Float
) {
    val cx = left + l / 2f
    val r = l / 2f
    val outerRect = androidx.compose.ui.geometry.Rect(cx - r - wt, top, cx + r + wt, top + 2 * r)
    val innerRect = androidx.compose.ui.geometry.Rect(cx - r, top, cx + r, top + 2 * r)

    // Left curved wall
    val lw = Path().apply {
        moveTo(cx - r, top); arcTo(outerRect, 180f, 90f, false)
        lineTo(cx - r + wt, top + r); arcTo(innerRect, 90f, -90f, false); close()
    }
    drawPath(lw, color = ConcreteGray)
    drawPath(lw, color = DimensionWhite.copy(alpha = 0.5f), style = Stroke(1.5f))

    // Right curved wall
    val rw = Path().apply {
        moveTo(cx + r, top); arcTo(outerRect, 0f, -90f, false)
        lineTo(cx + r - wt, top + r); arcTo(innerRect, 90f, 90f, false); close()
    }
    drawPath(rw, color = ConcreteGray)
    drawPath(rw, color = DimensionWhite.copy(alpha = 0.5f), style = Stroke(1.5f))

    // Base slab
    drawRect(color = ConcreteGray, topLeft = Offset(left, top + h), size = Size(l, bt))
    drawRect(color = DimensionWhite.copy(alpha = 0.5f),
        topLeft = Offset(left, top + h), size = Size(l, bt), style = Stroke(1.5f))
    drawConcreteHatchingOnRect(left, top + h, l, bt)
}

// ============================================================================
// 3. WATER FILL
// ============================================================================

private fun DrawScope.drawWaterFill(
    left: Float, top: Float, l: Float, h: Float,
    wt: Float, wl: Float, isCircular: Boolean
) {
    val waterTop = top + h - wl
    if (wl <= 0) return

    val waterLeft = if (isCircular) left + 10f else left + wt
    val waterRight = if (isCircular) left + l - 10f else left + l - wt
    val wTop = max(waterTop, top + 20f)
    drawRect(color = WaterBlue, topLeft = Offset(waterLeft, wTop), size = Size(waterRight - waterLeft, top + h - wTop))
    drawLine(color = WaterStroke, start = Offset(waterLeft, waterTop), end = Offset(waterRight, waterTop), strokeWidth = 2f)

    // Water wave marks
    val waveY = waterTop + 6f
    for (i in 0..3) {
        val wx = waterLeft + 10f + i * (waterRight - waterLeft - 20f) / 3f
        drawLine(color = WaterStroke.copy(alpha = 0.5f), start = Offset(wx, waveY), end = Offset(wx + 12f, waveY - 4f), strokeWidth = 1.2f)
    }
}

// ============================================================================
// 4. SOIL BELOW BASE
// ============================================================================

private fun DrawScope.drawSoilBelowBase(
    cw: Float, ch: Float,
    left: Float, baseBottom: Float, wt: Float,
    right: Float, fd: Float, tankTop: Float
) {
    if (fd <= 0) return
    val soilBottom = tankTop - fd + (ch - tankTop) * 0.3f
    // Soil fill
    drawRect(
        color = SoilFill,
        topLeft = Offset(left - 80f, baseBottom),
        size = Size(right - left + 160f, soilBottom - baseBottom)
    )
    // Soil hatching
    nativeCanvas.save()
    nativeCanvas.clipRect(left - 80f, baseBottom, right + 80f, soilBottom)
    var hx = left - 80f
    while (hx < right + 80f + (soilBottom - baseBottom)) {
        nativeCanvas.drawLine(
            hx, baseBottom, hx - 30f, soilBottom,
            android.graphics.Paint().apply {
                color = SoilBrown.hashCode()
                strokeWidth = 1.2f
            }
        )
        hx += 14f
    }
    nativeCanvas.restore()
}

// ============================================================================
// 5. ELEVATED SUPPORTS
// ============================================================================

private fun DrawScope.drawElevatedSupports(
    left: Float, right: Float, baseBottom: Float, ch: Float
) {
    val colW = 18f
    val groundY = ch - 30f
    listOf(left + 30f, right - 30f - colW).forEach { cx ->
        drawRect(color = ConcreteSideGray, topLeft = Offset(cx, baseBottom), size = Size(colW, groundY - baseBottom))
        drawRect(color = DimensionWhite.copy(alpha = 0.3f), topLeft = Offset(cx, baseBottom), size = Size(colW, groundY - baseBottom), style = Stroke(1f))
    }
    drawLine(color = GroundLineBrown, start = Offset(left, groundY), end = Offset(right, groundY), strokeWidth = 2f)
}

// ============================================================================
// 6. ELEVATION REINFORCEMENT
// ============================================================================

private fun DrawScope.drawElevationReinforcement(
    tankLeft: Float, tankTop: Float, tankRight: Float, tankBottom: Float,
    baseBottom: Float, wt: Float, bt: Float, wl: Float, cover: Float, scale: Float,
    vDia: Double, vSpacing: Double, hDia: Double, hSpacing: Double,
    isCircular: Boolean
) {
    val hSpacingPx = hSpacing.toFloat() * scale
    val effH = if (hSpacingPx > 8f) hSpacingPx else 30f
    val vSpacingPx = vSpacing.toFloat() * scale
    val effV = if (vSpacingPx > 8f) vSpacingPx else 25f

    if (isCircular) {
        // Hoop reinforcement (dots + tension ring arrows)
        var hy = tankTop + cover + 10f
        while (hy < tankBottom - cover) {
            drawCircle(color = HoopGreen, radius = 3.5f, center = Offset(tankLeft + wt / 2f, hy))
            drawCircle(color = HoopGreen, radius = 3.5f, center = Offset(tankRight - wt / 2f, hy))
            drawArrowHead(tankLeft + wt / 2f + 8f, hy, 1f, HoopGreen)
            drawArrowHead(tankRight - wt / 2f - 8f, hy, -1f, HoopGreen)
            hy += effH
        }
        // Vertical bars in walls
        var vx = tankLeft + cover
        while (vx < tankLeft + wt - cover) {
            drawLine(color = RebarBlue, start = Offset(vx, tankTop + cover), end = Offset(vx, baseBottom - cover), strokeWidth = 2f)
            vx += max(vDia.toFloat() * scale * 1.5f, 8f)
        }
    } else {
        // Vertical reinforcement on both walls
        var vy = tankTop + cover
        while (vy < tankBottom) {
            drawLine(color = RebarBlue, start = Offset(tankLeft + cover, vy), end = Offset(tankLeft + cover, vy + effV * 0.8f), strokeWidth = 2.5f)
            drawLine(color = RebarBlue, start = Offset(tankRight - cover, vy), end = Offset(tankRight - cover, vy + effV * 0.8f), strokeWidth = 2.5f)
            vy += effV
        }
        // Horizontal reinforcement
        var hx = tankTop + cover
        while (hx < tankBottom) {
            drawLine(color = RebarLightBlue, start = Offset(tankLeft + 4f, hx), end = Offset(tankLeft + wt - 4f, hx), strokeWidth = 1.5f)
            drawLine(color = RebarLightBlue, start = Offset(tankRight - wt + 4f, hx), end = Offset(tankRight - 4f, hx), strokeWidth = 1.5f)
            hx += effH
        }
        // Base bottom rebar
        var bx = tankLeft + cover
        while (bx < tankRight - cover) {
            drawLine(color = RebarBlue, start = Offset(bx, baseBottom - cover), end = Offset(bx + min(effV * 0.8f, tankRight - cover - bx), baseBottom - cover), strokeWidth = 2.5f)
            bx += effV
        }
    }
}

// ============================================================================
// 7. TANK DIMENSION LINES
// ============================================================================

private fun DrawScope.drawTankDimensions(
    left: Float, top: Float, right: Float, bottom: Float,
    baseBottom: Float, wt: Float, bt: Float, l: Float, h: Float,
    isCircular: Boolean, length: Double
) {
    val dimOffset = 35f
    // Height dimension (left side)
    drawDimLine(left - dimOffset, top, left - dimOffset, bottom,
        if (isCircular) "H = ${h.toInt()/0.72f}mm" else "H", true)
    // Length dimension (top)
    val label = if (isCircular) "Ø${length.toInt()}mm" else "L = ${length.toInt()}mm"
    drawDimLine(left, top - dimOffset, right, top - dimOffset, label, false)
    // Wall thickness (left)
    drawDimLine(left - dimOffset - 30f, top + 10f, left, top + 10f, "t", false)
    // Base thickness
    drawDimLine(right + dimOffset, bottom, right + dimOffset, baseBottom, "tb", true)
}

// ============================================================================
// 8. PLAN VIEW (top-right corner)
// ============================================================================

private fun DrawScope.drawPlanView(
    cw: Float, tankType: String, length: Double, width: Double, isCircular: Boolean
) {
    val insetSize = min(120f, cw * 0.18f)
    val insetLeft = cw - insetSize - 20f
    val insetTop = 12f

    // Background panel
    drawRoundRect(
        color = Color(0x22000000),
        topLeft = Offset(insetLeft - 10f, insetTop - 10f),
        size = Size(insetSize + 20f, insetSize + 40f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawTextAnnotated("المسقط", insetLeft, insetTop - 2f, DimensionWhite, 18f)

    val cx = insetLeft + insetSize / 2f
    val cy = insetTop + 20f + insetSize / 2f

    if (isCircular) {
        // Circle
        val r = insetSize / 2f - 8f
        drawCircle(color = ConcreteGray, radius = r, center = Offset(cx, cy))
        drawCircle(color = DimensionWhite.copy(alpha = 0.5f), radius = r,
            center = Offset(cx, cy), style = Stroke(width = 1.5f))
        // Diameter dimension
        drawLine(
            color = ExtensionGray, start = Offset(cx - r, cy), end = Offset(cx + r, cy),
            strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
        )
        // Hoop direction arrows
        for (i in 0..7) {
            val angle = Math.toRadians(i * 45.0)
            val dx = cx + (r - 5f) * Math.cos(angle).toFloat()
            val dy = cy + (r - 5f) * Math.sin(angle).toFloat()
            drawCircle(color = HoopGreen, radius = 2.5f, center = Offset(dx, dy))
        }
        drawTextAnnotated("Ø${length.toInt()}", cx - 16f, cy + r + 16f, DimensionWhite, 14f)
    } else {
        // Rectangle
        val rw = insetSize - 16f
        val rh = (width / length) * rw
        val rl = cx - rw / 2f
        val rt = cy - rh / 2f
        drawRect(color = ConcreteGray, topLeft = Offset(rl, rt), size = Size(rw, rh))
        drawRect(color = DimensionWhite.copy(alpha = 0.5f), topLeft = Offset(rl, rt),
            size = Size(rw, rh), style = Stroke(width = 1.5f))
        // Rebar direction arrows
        drawLine(color = RebarBlue, start = Offset(rl + 5f, rt + 5f),
            end = Offset(rl + 5f, rt + rh - 5f), strokeWidth = 1.5f)
        drawLine(color = RebarLightBlue, start = Offset(rl + 5f, rt + rh / 2f),
            end = Offset(rl + rw - 5f, rt + rh / 2f), strokeWidth = 1f)
        drawTextAnnotated("${length.toInt()}×${width.toInt()}", cx - 24f, rt + rh + 16f, DimensionWhite, 14f)
    }
}

// ============================================================================
// 9. WALL DETAIL INSET (zoomed cross-section)
// ============================================================================

private fun DrawScope.drawWallDetailInset(
    cw: Float, ch: Float,
    wallThickness: Double, vDia: Double, hDia: Double,
    cover: Float, scale: Float
) {
    val insetW = min(150f, cw * 0.20f)
    val insetH = min(200f, ch * 0.30f)
    val insetLeft = cw - insetW - 16f
    val insetTop = ch * 0.42f

    // Background
    drawRoundRect(
        color = Color(0x22000000),
        topLeft = Offset(insetLeft - 8f, insetTop - 8f),
        size = Size(insetW + 16f, insetH + 16f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawTextAnnotated("تفصيل الحائط", insetLeft, insetTop - 2f, DimensionWhite, 16f)

    // Wall cross-section (vertical rectangle)
    val wallW = min(insetW * 0.45f, 50f)
    val wallH = insetH - 60f
    val wallLeft = insetLeft + (insetWidth - wallW) / 2f
    val wallTop = insetTop + 24f

    drawRect(color = ConcreteGray, topLeft = Offset(wallLeft, wallTop),
        size = Size(wallW, wallH))
    drawRect(color = DimensionWhite.copy(alpha = 0.5f), topLeft = Offset(wallLeft, wallTop),
        size = Size(wallW, wallH), style = Stroke(width = 1.5f))

    // Concrete hatching
    drawConcreteHatchingOnRect(wallLeft, wallTop, wallW, wallH)

    // Vertical bars (blue circles)
    val vBarR = max(vDia.toFloat() / 2f * 0.8f, 2.5f)
    var vy = wallTop + 10f
    while (vy < wallTop + wallH - 10f) {
        drawCircle(color = RebarBlue, radius = vBarR,
            center = Offset(wallLeft + wallW / 2f - 4f, vy))
        vy += 22f
    }

    // Horizontal bars (lighter blue circles)
    val hBarR = max(hDia.toFloat() / 2f * 0.6f, 2f)
    drawCircle(color = RebarLightBlue, radius = hBarR,
        center = Offset(wallLeft + wallW / 2f + 4f, wallTop + wallH / 3f))
    drawCircle(color = RebarLightBlue, radius = hBarR,
        center = Offset(wallLeft + wallW / 2f + 4f, wallTop + 2 * wallH / 3f))

    // Cover dimension
    val cDimX = wallLeft - 12f
    drawLine(color = ExtensionGray, start = Offset(cDimX, wallTop),
        end = Offset(cDimX, wallTop + 15f), strokeWidth = 1f)
    drawTextAnnotated("40", cDimX - 16f, wallTop + 12f, ExtensionGray, 12f)

    // Water pressure arrows (right side, increasing with depth)
    var py = wallTop + 10f
    while (py < wallTop + wallH - 5f) {
        val depth = (py - wallTop) / wallH
        val arrowLen = 8f + depth * 25f
        drawLine(
            color = WaterStroke.copy(alpha = 0.4f + depth * 0.4f),
            start = Offset(wallLeft + wallW + 4f, py),
            end = Offset(wallLeft + wallW + 4f + arrowLen, py),
            strokeWidth = 1.2f
        )
        drawArrowHead(wallLeft + wallW + 4f, py, -1f, WaterStroke, vertical = false)
        py += 18f
    }

    // Wall thickness label
    drawTextAnnotated("t=${wallThickness.toInt()}", wallLeft + wallW / 2f - 16f,
        wallTop + wallH + 18f, DimensionWhite, 13f)
}

// ============================================================================
// 10. WATER PRESSURE DIAGRAM
// ============================================================================

private fun DrawScope.drawWaterPressureDiagram(
    tankLeft: Float, tankTop: Float, l: Float, h: Float,
    wl: Float, waterLevel: Double, isElevated: Boolean
) {
    if (wl <= 0) return

    val diagramX = tankLeft + l + 50f
    val diagramW = 60f
    val waterTop = tankTop + h - wl
    val gammaW = 9.81 // kN/m³
    val maxPressure = (waterLevel / 1000.0) * gammaW

    // Triangular pressure distribution
    val pressurePath = Path().apply {
        moveTo(diagramX, waterTop)
        lineTo(diagramX + diagramW, tankTop + h)
        lineTo(diagramX, tankTop + h)
        close()
    }
    drawPath(path = pressurePath, color = PressurePink.copy(alpha = 0.3f))
    drawPath(path = pressurePath, color = PressurePink, style = Stroke(width = 1.5f))

    // Labels
    drawTextAnnotated("Pw", diagramX + diagramW + 6f, tankTop + h - 8f, PressurePink, 16f)
    drawTextAnnotated(
        "q = γw × h", diagramX - 10f, tankTop + h + 20f, PressurePink, 13f
    )
    drawTextAnnotated(
        "= ${"%.1f".format(maxPressure)} kN/m²", diagramX - 10f, tankTop + h + 36f,
        PressurePink, 13f
    )

    // Zero line at top
    drawLine(
        color = PressurePink.copy(alpha = 0.6f),
        start = Offset(diagramX - 5f, waterTop),
        end = Offset(diagramX + 8f, waterTop),
        strokeWidth = 1f
    )
    drawTextAnnotated("0", diagramX - 14f, waterTop + 4f, PressurePink.copy(alpha = 0.6f), 12f)

    if (isElevated) {
        // Add uniform seismic component
        drawTextAnnotated("(+ seismic)", diagramX - 10f, tankTop + h + 52f,
            PressurePink.copy(alpha = 0.6f), 11f)
    }
}

// ============================================================================
// 11. REINFORCEMENT TABLE
// ============================================================================

private fun DrawScope.drawReinforcementTable(
    cw: Float, ch: Float, tankType: String,
    vDia: Double, vSpacing: Double,
    hDia: Double, hSpacing: Double,
    height: Double, length: Double
) {
    val tableLeft = 16f
    val tableTop = ch * 0.70f
    val tableW = cw - 32f
    val rowH = 24f
    val headerH = 28f
    val colWidths = floatArrayOf(
        tableW * 0.22f,  // Direction
        tableW * 0.20f,  // Location
        tableW * 0.16f,  // Dia
        tableW * 0.22f,  // Spacing
        tableW * 0.20f   // As
    )

    // Title
    drawTextAnnotated("جدول التسليح", tableLeft, tableTop - 6f, DimensionWhite, 20f)

    // Header
    drawRect(color = TableHeaderBg, topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH))
    val headers = arrayOf("الاتجاه", "الموقع", "القطر", "المسافة", "As")
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

    val isCircular = tankType.contains("Circular")

    // Row 1: Vertical / Hoop
    val r1Y = tableTop + headerH
    drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, r1Y), size = Size(tableW, rowH))
    val dir1 = if (isCircular) "حلقي" else "عمودي"
    val loc1 = if (isCircular) "الحائط" else "حائط جانبي"
    val as1 = if (isCircular) "Hoop" else "Vert"
    val row1 = arrayOf(dir1, loc1, "Ø${vDia.toInt()}", "@${vSpacing.toInt()}mm", as1)
    cx = tableLeft
    for (i in row1.indices) {
        drawTextAnnotated(row1[i], cx + 6f, r1Y + rowH / 2f + 5f, RebarBlue, 14f)
        cx += colWidths[i]
    }

    // Separator
    drawLine(color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r1Y + rowH),
        end = Offset(tableLeft + tableW, r1Y + rowH), strokeWidth = 0.5f)

    // Row 2: Horizontal / Vertical distribution
    val r2Y = r1Y + rowH
    val dir2 = if (isCircular) "عمودي" else "أفقي"
    val loc2 = if (isCircular) "الحائط" else "حائط جانبي"
    val row2 = arrayOf(dir2, loc2, "Ø${hDia.toInt()}", "@${hSpacing.toInt()}mm", "Dist")
    cx = tableLeft
    for (i in row2.indices) {
        drawTextAnnotated(row2[i], cx + 6f, r2Y + rowH / 2f + 5f, RebarLightBlue, 14f)
        cx += colWidths[i]
    }

    // Separator
    drawLine(color = ExtensionGray.copy(alpha = 0.2f),
        start = Offset(tableLeft, r2Y + rowH),
        end = Offset(tableLeft + tableW, r2Y + rowH), strokeWidth = 0.5f)

    // Row 3: Base reinforcement
    val r3Y = r2Y + rowH
    drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, r3Y), size = Size(tableW, rowH))
    val row3 = arrayOf("أفقي", "القاعدة", "Ø${vDia.toInt()}", "@${hSpacing.toInt()}mm", "Base")
    cx = tableLeft
    for (i in row3.indices) {
        drawTextAnnotated(row3[i], cx + 6f, r3Y + rowH / 2f + 5f, RebarBlue.copy(alpha = 0.7f), 14f)
        cx += colWidths[i]
    }

    // Column separators
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
        drawLine(color = ExtensionGray.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop),
            end = Offset(sepX, r3Y + rowH), strokeWidth = 0.5f)
    }

    // Table border
    drawRect(color = ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH + rowH * 3),
        style = Stroke(width = 1f))
}

// ============================================================================
// SHARED HELPERS
// ============================================================================

private fun DrawScope.drawConcreteHatchingOnRect(
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

private fun DrawScope.drawDimLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    text: String, vertical: Boolean
) {
    val tick = 8f
    if (vertical) {
        drawLine(color = ExtensionGray, start = Offset(x1, y1), end = Offset(x1, y2), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x1 - tick, y1), end = Offset(x1 + tick, y1), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x1 - tick, y2), end = Offset(x1 + tick, y2), strokeWidth = 1f)
        val midY = (y1 + y2) / 2f
        drawTextAnnotated(text, x1 - 30f, midY + 4f, DimensionWhite, 14f)
    } else {
        drawLine(color = ExtensionGray, start = Offset(x1, y1), end = Offset(x2, y1), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x1, y1 - tick), end = Offset(x1, y1 + tick), strokeWidth = 1f)
        drawLine(color = ExtensionGray, start = Offset(x2, y1 - tick), end = Offset(x2, y1 + tick), strokeWidth = 1f)
        val midX = (x1 + x2) / 2f
        drawTextAnnotated(text, midX - 20f, y1 - 8f, DimensionWhite, 14f)
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