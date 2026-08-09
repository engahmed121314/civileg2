package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================================
// COLOR PALETTE
// ============================================================================

private val WaterBlue = Color(0x604A90D9)
private val WaterStroke = Color(0xFF4A90D9)
private val ConcreteFill = Color(0xFFB0B0B0)
private val ConcreteDark = Color(0xFF808080)
private val ConcreteLight = Color(0xFFD0D0D0)
private val SoilBrown = Color(0xFF8B4513)
private val SoilFill = Color(0x558B4513)
private val RebarBlue = Color(0xFF2255CC)
private val RebarLightBlue = Color(0xFF5599DD)
private val DimColor = Color(0xFFDDDDDD)
private val AccentOrange = Color(0xFFFF9800)
private val PressurePink = Color(0xFFE91E8C)
private val GroundColor = Color(0xFFA0522D)
private val HoopGreen = Color(0xFF4CAF50)
private val TextColor = Color(0xFFEEEEEE)
private val PanelBg = Color(0x33000000)
private val TableHeaderBg = Color(0x44FFFFFF)
private val TableRowAlt = Color(0x1AFFFFFF)

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
    viewMode: Int = 0,
    modifier: Modifier = Modifier
) {
    Canvas(
<<<<<<< HEAD
        modifier = modifier.fillMaxSize()
=======
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
>>>>>>> github/master
    ) {
        val cw = size.width
        val ch = size.height

        val isCircular = tankType.contains("Circular")
        val isElevated = tankType.contains("Elevated")
        val isUnderground = foundationDepth > 0

<<<<<<< HEAD
        // ── Layout zones (adjusted per viewMode) ──
        val margin = 30f
        val mainTop = 50f
        val mainBottom = when (viewMode) {
            1 -> ch * 0.92f  // Elevation only: fill most of canvas
            else -> ch * 0.58f
        }
        val mainLeft = 60f
        val mainRight = cw - 60f
=======
        // ── Layout: 70% elevation, 30% table ──
        val margin = 40f
        val tableBottom = ch
        val tableTop = ch * 0.72f
        val elevBottom = tableTop - 10f
        val elevTop = 50f
        val elevLeft = 80f
        val elevRight = cw * 0.62f  // leave room for pressure diagram
>>>>>>> github/master

        // ── Scaling ──
        val totalH = height + baseThickness / 1000.0 + (if (isUnderground) foundationDepth else 0.0)
        val scaleX = (elevRight - elevLeft) / length.toFloat()
        val scaleY = (elevBottom - elevTop) / totalH.toFloat()
        val scale = min(scaleX, scaleY) * 0.82f

        val drawL = length.toFloat() * scale
        val drawH = height.toFloat() * scale
        val drawWT = max((wallThickness / 1000.0 * scale).toFloat(), 22f)  // MIN 22px for visibility
        val drawBT = max((baseThickness / 1000.0 * scale).toFloat(), 16f)
        val drawWL = waterLevel.toFloat() * scale
        val drawFD = foundationDepth.toFloat() * scale
        val cover = max(50f * scale, 6f)

<<<<<<< HEAD
        val tankLeft = mainLeft + (mainRight - mainLeft - drawL) / 2f
        val tankTop = mainTop + 35f + (if (isUnderground) drawFD else 0f)
=======
        // Center tank in elevation area
        val tankLeft = elevLeft + (elevRight - elevLeft - drawL) / 2f
        val tankTop = elevTop + (elevBottom - elevTop - drawH - drawBT - (if (isUnderground) drawFD else 0f)) / 2f + (if (isUnderground) drawFD else 0f)
>>>>>>> github/master
        val tankRight = tankLeft + drawL
        val tankBottom = tankTop + drawH
        val baseBottom = tankBottom + drawBT

<<<<<<< HEAD
        // ── Drawing Backdrop ──
        drawRect(color = Color(0xFF1A1A2E), size = size) // Professional engineering background

        // ── Title & Annotations ──
        drawTextAnnotated("MASTER STRUCTURAL DRAWING - $tankType", 20f, 30f, Color.Cyan, 20f, bold = true)
        // Zone 1: Elevation / Perspective (viewMode 0 or 1)
        if (viewMode == 0 || viewMode == 1) {
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
                drawWT, drawBT, drawL, drawH, isCircular, length, height
            )

            // Plan view (top-right)
            drawPlanView(cw, tankType, length, width, isCircular)
        }

        // Zone 2: Water Pressure diagram (viewMode 0 or 2)
        if (viewMode == 0 || viewMode == 2) {
            val pressureTop = if (viewMode == 2) 50f else tankTop
            drawWaterPressureDiagram(cw, tankLeft, pressureTop, drawL, drawH, drawWL, waterLevel, isElevated)
        }

        // Zone 3: Reinforcement details + table (viewMode 0 or 3)
        if (viewMode == 0 || viewMode == 3) {
            // Reinforcement on elevation
            drawElevationReinforcement(
                tankLeft, tankTop, tankRight, tankBottom, baseBottom,
                drawWT, drawBT, drawWL, cover, scale,
                verticalRebarDia, verticalRebarSpacing,
                horizontalRebarDia, horizontalRebarSpacing,
                isCircular
            )

            // Wall detail inset
            drawWallDetailInset(cw, ch, wallThickness, verticalRebarDia, horizontalRebarDia, cover, scale)

            // Reinforcement table
            drawReinforcementTable(cw, ch, tankType, verticalRebarDia, verticalRebarSpacing,
                horizontalRebarDia, horizontalRebarSpacing, height, length)
        }
=======
        // ── Background ──
        drawRoundRect(
            color = Color(0xFF1A1A2E),
            topLeft = Offset.Zero,
            size = Size(cw, ch),
            cornerRadius = CornerRadius(12f, 12f)
        )

        // ── Title ──
        val paint = android.graphics.Paint().apply {
            color = Color.White.toArgb()
            textSize = 18f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            "WATER TANK - CROSS SECTION", cw / 2f, 30f, paint
        )

        // ── Underground soil ──
        if (isUnderground && drawFD > 0) {
            drawSoilRegion(tankLeft - 60f, tankTop - drawFD, drawL + 120f, drawFD + 30f)
            // GL line
            val glY = tankTop - drawFD
            drawLine(
                color = GroundColor,
                start = Offset(tankLeft - 80f, glY),
                end = Offset(tankRight + 80f, glY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
            )
            drawTextAnnotated("GL +/-0.00", tankLeft - 100f, glY - 6f, GroundColor, 14f)
        }

        // ── Elevated supports ──
        if (isElevated) {
            drawElevatedColumns(tankLeft, tankRight, baseBottom, ch * 0.72f)
        }

        // ── Draw Tank Body ──
        if (isCircular) {
            drawCircularTankBody(tankLeft, tankTop, drawL, drawH, drawWT, drawBT)
        } else {
            drawRectangularTankBody(tankLeft, tankTop, drawL, drawH, drawWT, drawBT)
        }

        // ── Water Fill ──
        if (drawWL > 2f) {
            drawWaterFill(tankLeft, tankTop, drawL, drawH, drawWT, drawWL, isCircular)
        }

        // ── Reinforcement ──
        drawReinforcement(
            tankLeft, tankTop, tankRight, tankBottom, baseBottom,
            drawWT, drawBT, drawWL, cover, scale,
            verticalRebarDia, verticalRebarSpacing,
            horizontalRebarDia, horizontalRebarSpacing,
            isCircular
        )

        // ── Dimensions ──
        drawDimensions(tankLeft, tankTop, tankRight, tankBottom, baseBottom,
            drawWT, drawBT, drawL, drawH, isCircular, length, height, wallThickness, baseThickness)

        // ── Pressure Diagram ──
        if (drawWL > 2f) {
            drawPressureDiagram(tankRight + 50f, tankTop, drawH, drawWL, waterLevel, isElevated, elevBottom)
        }

        // ── Plan View ──
        drawPlanView(cw, tankType, length, width, isCircular, tableTop)

        // ── Reinforcement Table ──
        drawRebarTable(cw, tableTop, ch, tankType,
            verticalRebarDia, verticalRebarSpacing,
            horizontalRebarDia, horizontalRebarSpacing, height, length)
>>>>>>> github/master
    }
}

// ============================================================================
// RECTANGULAR TANK BODY — clear U-shape vessel
// ============================================================================

private fun DrawScope.drawRectangularTankBody(
    left: Float, top: Float, l: Float, h: Float, wt: Float, bt: Float
) {
    // Left wall - solid concrete
    drawRect(
        color = ConcreteFill,
        topLeft = Offset(left, top),
        size = Size(wt, h + bt)
    )
    // Left wall 3D effect
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(ConcreteLight, ConcreteFill, ConcreteDark)
        ),
        topLeft = Offset(left, top),
        size = Size(wt, h + bt)
    )

    // Right wall
    drawRect(
        color = ConcreteFill,
        topLeft = Offset(left + l - wt, top),
        size = Size(wt, h + bt)
    )
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(ConcreteDark, ConcreteFill, ConcreteLight)
        ),
        topLeft = Offset(left + l - wt, top),
        size = Size(wt, h + bt)
    )

    // Base slab - full width
    drawRect(
        color = ConcreteFill,
        topLeft = Offset(left, top + h),
        size = Size(l, bt)
    )
    // Base 3D effect
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(ConcreteLight, ConcreteFill, ConcreteDark)
        ),
        topLeft = Offset(left, top + h),
        size = Size(l, bt)
    )

    // Interior void (dark background to show hollow interior)
    drawRect(
        color = Color(0xFF0D1117),
        topLeft = Offset(left + wt, top),
        size = Size(l - 2 * wt, h)
    )

    // Clean border - outer
    drawRect(
        color = Color.White.copy(alpha = 0.7f),
        topLeft = Offset(left, top),
        size = Size(l, h + bt),
        style = Stroke(width = 2f)
    )

    // Inner cutout border (open top = no top line)
    val innerPath = Path().apply {
        // Left inner edge (top to bottom)
        moveTo(left + wt, top)
        lineTo(left + wt, top + h)
        // Bottom inner edge
        lineTo(left + l - wt, top + h)
        // Right inner edge (bottom to top)
        lineTo(left + l - wt, top)
    }
    drawPath(innerPath, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 1.5f))

    // Hatching on walls
    drawConcreteHatching(left, top, wt, h + bt)
    drawConcreteHatching(left + l - wt, top, wt, h + bt)
    drawConcreteHatching(left, top + h, l, bt)

    // Wall-base joint emphasis
    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(left, top + h),
        end = Offset(left + l, top + h),
        strokeWidth = 1f
    )
}

// ============================================================================
// CIRCULAR TANK BODY — correct U-shape cross section through diameter
// ============================================================================

private fun DrawScope.drawCircularTankBody(
    left: Float, top: Float, l: Float, h: Float, wt: Float, bt: Float
) {
    val cx = left + l / 2f
    val r = l / 2f
    val wallR = r + wt

    // Draw as rectangular cross-section through diameter (what you actually see in a section)
    // Same as rectangular but with curved inner wall edges
    // Left wall
    drawRect(color = ConcreteFill, topLeft = Offset(left, top), size = Size(wt, h + bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ConcreteLight, ConcreteFill, ConcreteDark)),
        topLeft = Offset(left, top), size = Size(wt, h + bt)
    )
    // Right wall
    drawRect(color = ConcreteFill, topLeft = Offset(left + l - wt, top), size = Size(wt, h + bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ConcreteDark, ConcreteFill, ConcreteLight)),
        topLeft = Offset(left + l - wt, top), size = Size(wt, h + bt)
    )
    // Base slab
    drawRect(color = ConcreteFill, topLeft = Offset(left, top + h), size = Size(l, bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ConcreteLight, ConcreteFill, ConcreteDark)),
        topLeft = Offset(left, top + h), size = Size(l, bt)
    )

    // Interior
    drawRect(color = Color(0xFF0D1117), topLeft = Offset(left + wt, top), size = Size(l - 2 * wt, h))

    // Borders
    drawRect(
        color = Color.White.copy(alpha = 0.7f),
        topLeft = Offset(left, top), size = Size(l, h + bt), style = Stroke(2f)
    )
    val innerPath = Path().apply {
        moveTo(left + wt, top); lineTo(left + wt, top + h)
        lineTo(left + l - wt, top + h); lineTo(left + l - wt, top)
    }
    drawPath(innerPath, color = Color.White.copy(alpha = 0.5f), style = Stroke(1.5f))

    drawConcreteHatching(left, top, wt, h + bt)
    drawConcreteHatching(left + l - wt, top, wt, h + bt)
    drawConcreteHatching(left, top + h, l, bt)

    // Circular indicator arcs at top of walls
    drawPath(
        Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(left, top - 8f, left + l, top + 8f))
        },
        color = HoopGreen.copy(alpha = 0.4f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )
    drawTextAnnotated("(circular section)", cx - 40f, top - 14f, HoopGreen.copy(alpha = 0.6f), 11f)
}

// ============================================================================
// WATER FILL
// ============================================================================

private fun DrawScope.drawWaterFill(
    left: Float, top: Float, l: Float, h: Float,
    wt: Float, wl: Float, isCircular: Boolean
) {
    val waterTop = top + h - wl
    val wLeft = left + wt + 2f
    val wRight = left + l - wt - 2f
    val wTop = max(waterTop, top + 2f)
    val wH = top + h - 2f - wTop

    if (wH <= 0) return

    // Water gradient fill
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                WaterBlue.copy(alpha = 0.3f),
                WaterBlue.copy(alpha = 0.6f)
            )
        ),
        topLeft = Offset(wLeft, wTop),
        size = Size(wRight - wLeft, wH)
    )

    // Water surface line
    drawLine(
        color = WaterStroke,
        start = Offset(wLeft, waterTop),
        end = Offset(wRight, waterTop),
        strokeWidth = 2.5f
    )

    // Wave marks
    val waveY = waterTop + 5f
    repeat(5) { i ->
        val wx = wLeft + 15f + i * (wRight - wLeft - 30f) / 4f
        drawLine(
            color = WaterStroke.copy(alpha = 0.5f),
            start = Offset(wx, waveY),
            end = Offset(wx + 10f, waveY - 4f),
            strokeWidth = 1.2f
        )
    }

    // WL label
    drawTextAnnotated("WL", wRight + 6f, waterTop + 4f, WaterStroke, 13f)
}

// ============================================================================
// SOIL REGION
// ============================================================================

private fun DrawScope.drawSoilRegion(left: Float, top: Float, w: Float, h: Float) {
    drawRect(color = SoilFill, topLeft = Offset(left, top), size = Size(w, h))
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    nc.clipRect(left, top, left + w, top + h)
    var hx = left - h
    while (hx < left + w + h) {
        nc.drawLine(hx, top, hx + h, top + h,
            android.graphics.Paint().apply { color = SoilBrown.toArgb(); strokeWidth = 1f })
        hx += 16f
    }
    nc.restore()
}

// ============================================================================
// ELEVATED COLUMNS
// ============================================================================

private fun DrawScope.drawElevatedColumns(left: Float, right: Float, baseBottom: Float, groundY: Float) {
    val colW = 20f
    val groundLine = groundY - 10f
    listOf(left + 40f, right - 40f - colW).forEach { cx ->
        drawRect(
            color = ConcreteDark,
            topLeft = Offset(cx, baseBottom),
            size = Size(colW, groundLine - baseBottom)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(cx, baseBottom),
            size = Size(colW, groundLine - baseBottom),
            style = Stroke(1f)
        )
    }
    // Ground line
    drawLine(
        color = GroundColor,
        start = Offset(left - 40f, groundLine),
        end = Offset(right + 40f, groundLine),
        strokeWidth = 2f
    )
    drawTextAnnotated("GL +/-0.00", left - 40f, groundLine + 14f, GroundColor, 12f)
}

// ============================================================================
// REINFORCEMENT
// ============================================================================

private fun DrawScope.drawReinforcement(
    tankLeft: Float, tankTop: Float, tankRight: Float, tankBottom: Float,
    baseBottom: Float, wt: Float, bt: Float, wl: Float, cover: Float, scale: Float,
    vDia: Double, vSpacing: Double, hDia: Double, hSpacing: Double,
    isCircular: Boolean
) {
    val hSpacingPx = max(hSpacing.toFloat() * scale, 20f)
    val vSpacingPx = max(vSpacing.toFloat() * scale, 18f)
    val barW = max(vDia.toFloat() * 0.4f, 2f)

    if (isCircular) {
        // Hoop reinforcement markers
        var hy = tankTop + cover + 10f
        while (hy < tankBottom - cover) {
            drawCircle(color = HoopGreen, radius = 3.5f, center = Offset(tankLeft + wt / 2f, hy))
            drawCircle(color = HoopGreen, radius = 3.5f, center = Offset(tankRight - wt / 2f, hy))
            hy += hSpacingPx
        }
        // Vertical bars
        var vx = tankLeft + cover
        while (vx < tankLeft + wt) {
            drawLine(color = RebarBlue, start = Offset(vx, tankTop + cover), end = Offset(vx, baseBottom - cover), strokeWidth = barW)
            vx += max(vDia.toFloat() * 1.5f, 8f)
        }
        vx = tankRight - cover
        while (vx > tankRight - wt) {
            drawLine(color = RebarBlue, start = Offset(vx, tankTop + cover), end = Offset(vx, baseBottom - cover), strokeWidth = barW)
            vx -= max(vDia.toFloat() * 1.5f, 8f)
        }
    } else {
        // Vertical bars on inner face of left wall
        var vy = tankTop + cover
        while (vy < tankBottom) {
            drawLine(
                color = RebarBlue,
                start = Offset(tankLeft + cover, vy),
                end = Offset(tankLeft + cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)),
                strokeWidth = 2.5f
            )
            // Outer face bars
            drawLine(
                color = RebarBlue,
                start = Offset(tankLeft + wt - cover, vy),
                end = Offset(tankLeft + wt - cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)),
                strokeWidth = 2.5f
            )
            // Right wall inner
            drawLine(
                color = RebarBlue,
                start = Offset(tankRight - cover, vy),
                end = Offset(tankRight - cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)),
                strokeWidth = 2.5f
            )
            drawLine(
                color = RebarBlue,
                start = Offset(tankRight - wt + cover, vy),
                end = Offset(tankRight - wt + cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)),
                strokeWidth = 2.5f
            )
            vy += vSpacingPx
        }

        // Horizontal bars (stirrups) on walls
        var hx = tankTop + cover
        while (hx < tankBottom) {
            drawLine(color = RebarLightBlue, start = Offset(tankLeft + 4f, hx), end = Offset(tankLeft + wt - 4f, hx), strokeWidth = 1.5f)
            drawLine(color = RebarLightBlue, start = Offset(tankRight - wt + 4f, hx), end = Offset(tankRight - 4f, hx), strokeWidth = 1.5f)
            hx += hSpacingPx
        }

        // Base bottom rebar
        var bx = tankLeft + cover
        while (bx < tankRight - cover) {
            drawLine(
                color = RebarBlue,
                start = Offset(bx, baseBottom - cover),
                end = Offset(bx + min(vSpacingPx * 0.6f, tankRight - cover - bx), baseBottom - cover),
                strokeWidth = 2.5f
            )
            bx += vSpacingPx
        }
        // Base top rebar (lighter)
        bx = tankLeft + cover
        while (bx < tankRight - cover) {
            drawLine(
                color = RebarLightBlue,
                start = Offset(bx, tankBottom + cover),
                end = Offset(bx + min(vSpacingPx * 0.6f, tankRight - cover - bx), tankBottom + cover),
                strokeWidth = 1.5f
            )
            bx += vSpacingPx
        }
    }
}

// ============================================================================
// DIMENSIONS
// ============================================================================

private fun DrawScope.drawDimensions(
    left: Float, top: Float, right: Float, bottom: Float,
    baseBottom: Float, wt: Float, bt: Float, l: Float, h: Float,
    isCircular: Boolean, lengthM: Double, heightM: Double,
    wallThickM: Double, baseThickM: Double
) {
    val ext = 30f
    // Height - left side
    drawDimLine(left - ext, top, left - ext, bottom, "H=${(heightM * 1000).toInt()}mm", true)
    // Length - top
    val lengthLabel = if (isCircular) "D=${(lengthM * 1000).toInt()}mm" else "L=${(lengthM * 1000).toInt()}mm"
    drawDimLine(left, top - ext, right, top - ext, lengthLabel, false)
    // Wall thickness
    drawDimLine(left - ext - 30f, top + 15f, left, top + 15f, "t=${wallThickM.toInt()}mm", false)
    // Base thickness - right side
    drawDimLine(right + ext, bottom, right + ext, baseBottom, "tb=${baseThickM.toInt()}mm", true)
}

// ============================================================================
// PRESSURE DIAGRAM
// ============================================================================

private fun DrawScope.drawPressureDiagram(
    x: Float, tankTop: Float, drawH: Float, drawWL: Float,
    waterLevelM: Double, isElevated: Boolean, elevBottom: Float
) {
    val waterTop = tankTop + drawH - drawWL
    val diagW = 55f
    val gammaW = 9.81
    val maxP = waterLevelM * gammaW
    val tankBottom = tankTop + drawH

    // Triangular pressure
    val pPath = Path().apply {
        moveTo(x, waterTop)
        lineTo(x + diagW, tankBottom)
        lineTo(x, tankBottom)
        close()
    }
    drawPath(pPath, color = PressurePink.copy(alpha = 0.25f))
    drawPath(pPath, color = PressurePink, style = Stroke(1.5f))

    drawTextAnnotated("Pw", x + diagW + 5f, tankBottom - 8f, PressurePink, 14f)
    drawTextAnnotated("q = gw x h = ${"%.1f".format(maxP)} kN/m2", x - 10f, tankBottom + 16f, PressurePink, 11f)
    drawTextAnnotated("0", x - 12f, waterTop + 4f, PressurePink.copy(alpha = 0.6f), 11f)
    drawLine(color = PressurePink.copy(alpha = 0.5f), start = Offset(x - 4f, waterTop), end = Offset(x + 6f, waterTop), strokeWidth = 1f)

    if (isElevated) {
        drawTextAnnotated("(+ seismic)", x - 10f, tankBottom + 30f, PressurePink.copy(alpha = 0.5f), 10f)
    }
}

// ============================================================================
// PLAN VIEW
// ============================================================================

private fun DrawScope.drawPlanView(
    cw: Float, tankType: String, lengthM: Double, widthM: Double,
    isCircular: Boolean, tableTop: Float
) {
<<<<<<< HEAD
    val insetSize = min(100f, cw * 0.18f)
=======
    val insetSize = min(100f, cw * 0.15f)
>>>>>>> github/master
    val insetLeft = cw - insetSize - 20f
    val insetTop = tableTop - insetSize - 40f

    drawRoundRect(color = PanelBg, topLeft = Offset(insetLeft - 8f, insetTop - 22f),
        size = Size(insetSize + 16f, insetSize + 34f), cornerRadius = CornerRadius(6f))
    drawTextAnnotated("PLAN", insetLeft, insetTop - 6f, TextColor, 13f)

    val cx = insetLeft + insetSize / 2f
    val cy = insetTop + 12f + insetSize / 2f

    if (isCircular) {
        val r = insetSize / 2f - 6f
        drawCircle(color = ConcreteFill, radius = r, center = Offset(cx, cy))
        drawCircle(color = Color.White.copy(alpha = 0.5f), radius = r, center = Offset(cx, cy), style = Stroke(1.5f))
        // Hoop indicators
        for (i in 0..7) {
            val a = Math.toRadians(i * 45.0)
            drawCircle(color = HoopGreen, radius = 2f, center = Offset(cx + (r - 4f) * Math.cos(a).toFloat(), cy + (r - 4f) * Math.sin(a).toFloat()))
        }
        drawTextAnnotated("D=${lengthM.toInt()}m", cx - 18f, cy + r + 14f, TextColor, 11f)
    } else {
        val rw = insetSize - 12f
        val rh = (widthM / lengthM).toFloat() * rw
        val rl = cx - rw / 2f
        val rt = cy - rh / 2f
        drawRect(color = ConcreteFill, topLeft = Offset(rl, rt), size = Size(rw, rh))
        drawRect(color = Color.White.copy(alpha = 0.5f), topLeft = Offset(rl, rt), size = Size(rw, rh), style = Stroke(1.5f))
        drawLine(color = RebarBlue, start = Offset(rl + 4f, rt + 4f), end = Offset(rl + 4f, rt + rh - 4f), strokeWidth = 1.5f)
        drawLine(color = RebarLightBlue, start = Offset(rl + 4f, cy), end = Offset(rl + rw - 4f, cy), strokeWidth = 1f)
        drawTextAnnotated("${lengthM.toInt()}x${widthM.toInt()}", cx - 20f, rt + rh + 14f, TextColor, 11f)
    }
}

// ============================================================================
// REINFORCEMENT TABLE
// ============================================================================

<<<<<<< HEAD
private fun DrawScope.drawWallDetailInset(
    cw: Float, ch: Float,
    wallThickness: Double, vDia: Double, hDia: Double,
    cover: Float, scale: Float
) {
    // Position wall detail below plan view, dynamically calculated
    val planViewBottom = 12f + 20f + min(100f, cw * 0.18f) + 16f
    val tableTop = ch * 0.64f
    val insetW = min(150f, cw * 0.20f)
    val maxInsetH = (tableTop - planViewBottom - 20f).coerceAtLeast(80f)
    val insetH = min(200f, maxInsetH).coerceAtLeast(100f)
    val insetLeft = cw - insetW - 16f
    val insetTop = (planViewBottom + 10f).coerceAtMost(tableTop - insetH - 10f)

    // Background
    drawRoundRect(
        color = Color(0x22000000),
        topLeft = Offset(insetLeft - 8f, insetTop - 8f),
        size = Size(insetW + 16f, insetH + 16f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    val wallDetailLabel = "WALL DETAIL"
    drawTextAnnotated(wallDetailLabel, insetLeft, insetTop - 2f, DimensionWhite, 16f)

    // Wall cross-section (vertical rectangle)
    val wallW = min(insetW * 0.45f, 50f)
    val wallH = insetH - 60f
    val wallLeft = insetLeft + (insetW - wallW) / 2f
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
    drawTextAnnotated("50", cDimX - 16f, wallTop + 12f, ExtensionGray, 12f)

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
    cw: Float, tankLeft: Float, tankTop: Float, l: Float, h: Float,
    wl: Float, waterLevel: Double, isElevated: Boolean
) {
    if (wl <= 0) return

    val diagramX = min(tankLeft + l + 30f, cw - 180f)
    val diagramW = 60f
    val waterTop = tankTop + h - wl
    val gammaW = 9.81 // kN/m³
    val maxPressure = waterLevel * gammaW  // waterLevel is already in meters

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
=======
private fun DrawScope.drawRebarTable(
    cw: Float, tableTop: Float, ch: Float, tankType: String,
>>>>>>> github/master
    vDia: Double, vSpacing: Double,
    hDia: Double, hSpacing: Double,
    height: Double, length: Double
) {
    val tableLeft = 16f
<<<<<<< HEAD
    // Table starts below main zone with generous gap (moved from 0.62f to 0.64f)
    val tableTop = ch * 0.64f
    val tableW = cw - 32f
    val rowH = if (ch < 400f) 22f else 24f
    val headerH = 28f
    val colWidths = floatArrayOf(
        tableW * 0.22f,  // Direction
        tableW * 0.20f,  // Location
        tableW * 0.16f,  // Dia
        tableW * 0.22f,  // Spacing
        tableW * 0.20f   // As
    )

    // Title (positioned below tableTop to avoid overlap with drawing zone)
    val tableTitle = "REINFORCEMENT SCHEDULE"
    drawTextAnnotated(tableTitle, tableLeft, tableTop + 16f, DimensionWhite, 20f, bold = true)

    // Header
    val headerTop = tableTop + 22f
    val headers = arrayOf("Direction", "Location", "Dia.", "Spacing", "As")
    drawRect(color = TableHeaderBg, topLeft = Offset(tableLeft, headerTop),
        size = Size(tableW, headerH))
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(headers[i], cx + 6f, headerTop + headerH / 2f + 6f,
            DimensionWhite, 15f, bold = true)
        cx += colWidths[i]
    }

    // Separator
    drawLine(color = ExtensionGray.copy(alpha = 0.3f),
        start = Offset(tableLeft, headerTop + headerH),
        end = Offset(tableLeft + tableW, headerTop + headerH), strokeWidth = 0.5f)

    val isCircular = tankType.contains("Circular")

    // Row 1: Vertical / Hoop
    val r1Y = headerTop + headerH
    drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, r1Y), size = Size(tableW, rowH))
    val dir1 = if (isCircular) "Hoop" else "Vertical"
    val loc1 = "Wall"
    val as1Label = if (isCircular) "Hoop" else "Vert"
    val row1 = arrayOf(dir1, loc1, "Ø${vDia.toInt()}", "@${vSpacing.toInt()}mm", as1Label)
    cx = tableLeft
    for (i in row1.indices) {
        drawTextAnnotated(row1[i], cx + 6f, r1Y + rowH / 2f + 5f, RebarBlue, 14f)
=======
    val tableW = cw - 32f
    val rowH = 22f
    val headerH = 26f
    val isCircular = tankType.contains("Circular")

    drawRoundRect(
        color = Color(0x22FFFFFF),
        topLeft = Offset(tableLeft - 4f, tableTop - 22f),
        size = Size(tableW + 8f, headerH + rowH * 3 + 26f),
        cornerRadius = CornerRadius(6f)
    )

    drawTextAnnotated("REINFORCEMENT SCHEDULE", tableLeft, tableTop - 6f, TextColor, 16f, bold = true)

    val headers = arrayOf("Direction", "Location", "Dia.", "Spacing", "As Provided")
    val colWidths = floatArrayOf(tableW * 0.22f, tableW * 0.20f, tableW * 0.16f, tableW * 0.22f, tableW * 0.20f)

    // Header row
    drawRect(color = TableHeaderBg, topLeft = Offset(tableLeft, tableTop), size = Size(tableW, headerH))
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(headers[i], cx + 6f, tableTop + headerH / 2f + 5f, TextColor, 13f, bold = true)
>>>>>>> github/master
        cx += colWidths[i]
    }

    val rows = listOf(
        arrayOf(
            if (isCircular) "Hoop" else "Vertical",
            "Wall (both faces)",
            "O${vDia.toInt()}",
            "@${vSpacing.toInt()}mm",
            if (isCircular) "Hoop" else "T&S"
        ),
        arrayOf(
            if (isCircular) "Vertical" else "Horizontal",
            "Wall",
            "O${hDia.toInt()}",
            "@${hSpacing.toInt()}mm",
            "Distribution"
        ),
        arrayOf(
            "Both", "Base Slab",
            "O${vDia.toInt()}",
            "@${hSpacing.toInt()}mm",
            "Top & Bottom"
        )
    )

    rows.forEachIndexed { idx, row ->
        val rY = tableTop + headerH + idx * rowH
        if (idx % 2 == 0) {
            drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, rY), size = Size(tableW, rowH))
        }
        cx = tableLeft
        val barColor = if (idx == 0) RebarBlue else if (idx == 1) RebarLightBlue else RebarBlue.copy(alpha = 0.7f)
        for (i in row.indices) {
            drawTextAnnotated(row[i], cx + 6f, rY + rowH / 2f + 4f, barColor, 12f)
            cx += colWidths[i]
        }
    }

    // Table border
    drawRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(tableLeft, tableTop),
        size = Size(tableW, headerH + rowH * 3),
        style = Stroke(width = 1f)
    )

    // Column separators
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
<<<<<<< HEAD
        drawLine(color = ExtensionGray.copy(alpha = 0.15f),
            start = Offset(sepX, headerTop),
            end = Offset(sepX, r3Y + rowH), strokeWidth = 0.5f)
    }

    // Table border
    drawRect(color = ExtensionGray.copy(alpha = 0.4f),
        topLeft = Offset(tableLeft, headerTop),
        size = Size(tableW, headerH + rowH * 3),
        style = Stroke(width = 1f))
=======
        drawLine(color = Color.White.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop), end = Offset(sepX, tableTop + headerH + rowH * 3), strokeWidth = 0.5f)
    }
>>>>>>> github/master
}

// ============================================================================
// HELPERS
// ============================================================================

private fun DrawScope.drawConcreteHatching(left: Float, top: Float, w: Float, h: Float) {
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    nc.clipRect(left, top, left + w, top + h)
    var i = left - h
    while (i < left + w + h) {
        nc.drawLine(i, top, i + h, top + h,
            android.graphics.Paint().apply { color = Color(0x44AAAAAA).toArgb(); strokeWidth = 0.6f })
        i += 16f
    }
    nc.restore()
}

private fun DrawScope.drawTextAnnotated(
    text: String, x: Float, y: Float, color: Color, size: Float, bold: Boolean = false
) {
    val p = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = size
        isFakeBoldText = bold
        textAlign = android.graphics.Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

private fun DrawScope.drawDimLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    text: String, vertical: Boolean
) {
    val tick = 6f
    if (vertical) {
        drawLine(color = DimColor, start = Offset(x1, y1), end = Offset(x1, y2), strokeWidth = 1f)
        drawLine(color = DimColor, start = Offset(x1 - tick, y1), end = Offset(x1 + tick, y1), strokeWidth = 1f)
        drawLine(color = DimColor, start = Offset(x1 - tick, y2), end = Offset(x1 + tick, y2), strokeWidth = 1f)
        // Arrows
        drawArrowHead(x1, y1, 1f, DimColor, true)
        drawArrowHead(x1, y2, -1f, DimColor, true)
        val midY = (y1 + y2) / 2f
        drawTextAnnotated(text, x1 - 50f, midY + 4f, DimColor, 12f)
    } else {
        drawLine(color = DimColor, start = Offset(x1, y1), end = Offset(x2, y1), strokeWidth = 1f)
        drawLine(color = DimColor, start = Offset(x1, y1 - tick), end = Offset(x1, y1 + tick), strokeWidth = 1f)
        drawLine(color = DimColor, start = Offset(x2, y1 - tick), end = Offset(x2, y1 + tick), strokeWidth = 1f)
        drawArrowHead(x1, y1, 1f, DimColor, false)
        drawArrowHead(x2, y1, -1f, DimColor, false)
        val midX = (x1 + x2) / 2f
        drawTextAnnotated(text, midX - 30f, y1 - 8f, DimColor, 12f)
    }
}

private fun DrawScope.drawArrowHead(
    x: Float, y: Float, dir: Float, color: Color, vertical: Boolean
) {
    val s = 5f
    val path = Path()
    if (vertical) {
        path.moveTo(x, y)
        path.lineTo(x - s * 0.5f, y - dir * s)
        path.lineTo(x + s * 0.5f, y - dir * s)
    } else {
        path.moveTo(x, y)
        path.lineTo(x - dir * s, y - s * 0.5f)
        path.lineTo(x - dir * s, y + s * 0.5f)
    }
    path.close()
    drawPath(path, color = color)
}
<<<<<<< HEAD

private fun DrawScope.drawTextAnnotated(
    text: String, x: Float, y: Float, color: Color, size: Float, bold: Boolean = false
) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        this.textSize = size
        this.isFakeBoldText = bold
        this.isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

=======
>>>>>>> github/master
