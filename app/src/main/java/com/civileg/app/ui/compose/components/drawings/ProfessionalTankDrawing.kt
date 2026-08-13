package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.math.max
import kotlin.math.min

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
private val PressurePink = Color(0xFFE91E8C)
private val GroundColor = Color(0xFFA0522D)
private val HoopGreen = Color(0xFF4CAF50)
private val TextColor = Color(0xFFEEEEEE)
private val PanelBg = Color(0x33000000)
private val TableHeaderBg = Color(0x44FFFFFF)
private val TableRowAlt = Color(0x1AFFFFFF)
private val DimensionWhite = Color(0xFFEEEEEE)
private val ExtensionGray = Color(0xFF888888)
private val ConcreteGray = Color(0xFFB0B0B0)
private val GroundLineBrown = Color(0xFF8B4513)

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
        modifier = modifier.fillMaxSize()
    ) {
        val cw = size.width
        val ch = size.height

        val isCircular = tankType.contains("Circular")
        val isElevated = tankType.contains("Elevated")
        val isUnderground = foundationDepth > 0

        // ── Layout zones (adjusted per viewMode) ──
        val mainTop = 50f
        val mainBottom = when (viewMode) {
            1 -> ch * 0.92f  // Elevation only: fill most of canvas
            else -> ch * 0.58f
        }
        val mainLeft = 80f
        val mainRight = cw * 0.65f

        // ── Scaling ──
        val totalH = height + baseThickness / 1000.0 + (if (isUnderground) foundationDepth else 0.0)
        val scaleX = (mainRight - mainLeft) / length.toFloat()
        val scaleY = (mainBottom - mainTop) / totalH.toFloat()
        val scale = min(scaleX, scaleY) * 0.82f

        val drawL = length.toFloat() * scale
        val drawH = height.toFloat() * scale
        val drawWT = max((wallThickness / 1000.0 * scale).toFloat(), 22f)
        val drawBT = max((baseThickness / 1000.0 * scale).toFloat(), 16f)
        val drawWL = waterLevel.toFloat() * scale
        val drawFD = foundationDepth.toFloat() * scale
        val cover = max(50f * scale, 6f)

        val tankLeft = mainLeft + (mainRight - mainLeft - drawL) / 2f
        val tankTop = mainTop + 35f + (if (isUnderground) drawFD else 0f)
        val tankRight = tankLeft + drawL
        val tankBottom = tankTop + drawH
        val baseBottom = tankBottom + drawBT

        // ── Drawing Backdrop ──
        drawRect(color = Color(0xFF1A1A2E), size = size)

        // ── Title ──
        drawTextAnnotated("MASTER STRUCTURAL DRAWING - $tankType", cw / 2f, 30f, Color.Cyan, 20f, bold = true, center = true)

        // Zone 1: Elevation / Perspective (viewMode 0 or 1)
        if (viewMode == 0 || viewMode == 1) {
            if (isUnderground) {
                drawSoilRegion(tankLeft - 60f, tankTop - drawFD, drawL + 120f, drawFD + 30f)
                val glY = tankTop - drawFD
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                drawLine(
                    color = GroundLineBrown,
                    start = Offset(tankLeft - 80f, glY),
                    end = Offset(tankRight + 80f, glY),
                    strokeWidth = 2f,
                    pathEffect = dashEffect
                )
                drawTextAnnotated("GL ±0.00", tankLeft - 100f, glY - 6f, GroundLineBrown, 16f)
            }

            if (isElevated) {
                drawElevatedColumns(tankLeft, tankRight, baseBottom, ch * 0.72f)
            }

            if (isCircular) {
                drawCircularTankBody(tankLeft, tankTop, drawL, drawH, drawWT, drawBT)
            } else {
                drawRectangularTankBody(tankLeft, tankTop, drawL, drawH, drawWT, drawBT)
            }

            if (drawWL > 0) {
                drawWaterFill(tankLeft, tankTop, drawL, drawH, drawWT, drawWL, isCircular)
            }

            drawDimensions(tankLeft, tankTop, tankRight, tankBottom, baseBottom,
                drawWT, drawBT, drawL, drawH, isCircular, length, height, wallThickness, baseThickness)

            drawPlanView(cw, tankType, length, width, isCircular, ch * 0.64f)
        }

        // Zone 2: Water Pressure diagram (viewMode 0 or 2)
        if (viewMode == 0 || viewMode == 2) {
            val pressureTop = if (viewMode == 2) 50f else tankTop
            drawWaterPressureDiagram(cw, tankLeft, pressureTop, drawL, drawH, drawWL, waterLevel, isElevated)
        }

        // Zone 3: Reinforcement details + table (viewMode 0 or 3)
        if (viewMode == 0 || viewMode == 3) {
            drawReinforcement(
                tankLeft, tankTop, tankRight, tankBottom, baseBottom,
                drawWT, drawBT, drawWL, cover, scale,
                verticalRebarDia, verticalRebarSpacing,
                horizontalRebarDia, horizontalRebarSpacing,
                isCircular
            )

            drawWallDetailInset(cw, ch, wallThickness, verticalRebarDia, horizontalRebarDia, cover, scale)

            drawRebarTable(cw, ch * 0.64f, ch, tankType,
                verticalRebarDia, verticalRebarSpacing,
                horizontalRebarDia, horizontalRebarSpacing, height, length)
        }
    }
}

// ============================================================================
// RECTANGULAR TANK BODY
// ============================================================================

private fun DrawScope.drawRectangularTankBody(
    left: Float, top: Float, l: Float, h: Float, wt: Float, bt: Float
) {
    drawRect(color = ConcreteFill, topLeft = Offset(left, top), size = Size(wt, h + bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ConcreteLight, ConcreteFill, ConcreteDark)),
        topLeft = Offset(left, top), size = Size(wt, h + bt)
    )

    drawRect(color = ConcreteFill, topLeft = Offset(left + l - wt, top), size = Size(wt, h + bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ConcreteDark, ConcreteFill, ConcreteLight)),
        topLeft = Offset(left + l - wt, top), size = Size(wt, h + bt)
    )

    drawRect(color = ConcreteFill, topLeft = Offset(left, top + h), size = Size(l, bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ConcreteLight, ConcreteFill, ConcreteDark)),
        topLeft = Offset(left, top + h), size = Size(l, bt)
    )

    drawRect(color = Color(0xFF0D1117), topLeft = Offset(left + wt, top), size = Size(l - 2 * wt, h))

    drawRect(color = Color.White.copy(alpha = 0.7f), topLeft = Offset(left, top), size = Size(l, h + bt), style = Stroke(width = 2f))

    val innerPath = Path().apply {
        moveTo(left + wt, top)
        lineTo(left + wt, top + h)
        lineTo(left + l - wt, top + h)
        lineTo(left + l - wt, top)
    }
    drawPath(innerPath, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 1.5f))

    drawConcreteHatching(left, top, wt, h + bt)
    drawConcreteHatching(left + l - wt, top, wt, h + bt)
    drawConcreteHatching(left, top + h, l, bt)
}

// ============================================================================
// CIRCULAR TANK BODY
// ============================================================================

private fun DrawScope.drawCircularTankBody(
    left: Float, top: Float, l: Float, h: Float, wt: Float, bt: Float
) {
    val cx = left + l / 2f
    drawRect(color = ConcreteFill, topLeft = Offset(left, top), size = Size(wt, h + bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ConcreteLight, ConcreteFill, ConcreteDark)),
        topLeft = Offset(left, top), size = Size(wt, h + bt)
    )
    drawRect(color = ConcreteFill, topLeft = Offset(left + l - wt, top), size = Size(wt, h + bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(ConcreteDark, ConcreteFill, ConcreteLight)),
        topLeft = Offset(left + l - wt, top), size = Size(wt, h + bt)
    )
    drawRect(color = ConcreteFill, topLeft = Offset(left, top + h), size = Size(l, bt))
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ConcreteLight, ConcreteFill, ConcreteDark)),
        topLeft = Offset(left, top + h), size = Size(l, bt)
    )

    drawRect(color = Color(0xFF0D1117), topLeft = Offset(left + wt, top), size = Size(l - 2 * wt, h))

    drawRect(color = Color.White.copy(alpha = 0.7f), topLeft = Offset(left, top), size = Size(l, h + bt), style = Stroke(2f))
    val innerPath = Path().apply {
        moveTo(left + wt, top); lineTo(left + wt, top + h)
        lineTo(left + l - wt, top + h); lineTo(left + l - wt, top)
    }
    drawPath(innerPath, color = Color.White.copy(alpha = 0.5f), style = Stroke(1.5f))

    drawConcreteHatching(left, top, wt, h + bt)
    drawConcreteHatching(left + l - wt, top, wt, h + bt)
    drawConcreteHatching(left, top + h, l, bt)

    drawPath(
        Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(left, top - 8f, left + l, top + 8f))
        },
        color = HoopGreen.copy(alpha = 0.4f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )
    drawTextAnnotated("(circular section)", cx, top - 14f, HoopGreen.copy(alpha = 0.6f), 11f, center = true)
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

    drawLine(color = WaterStroke, start = Offset(wLeft, waterTop), end = Offset(wRight, waterTop), strokeWidth = 2.5f)

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
    drawLine(color = GroundColor, start = Offset(left - 40f, groundLine), end = Offset(right + 40f, groundLine), strokeWidth = 2f)
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
        var hy = tankTop + cover + 10f
        while (hy < tankBottom - cover) {
            drawCircle(color = HoopGreen, radius = 3.5f, center = Offset(tankLeft + wt / 2f, hy))
            drawCircle(color = HoopGreen, radius = 3.5f, center = Offset(tankRight - wt / 2f, hy))
            hy += hSpacingPx
        }
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
        var vy = tankTop + cover
        while (vy < tankBottom) {
            drawLine(color = RebarBlue, start = Offset(tankLeft + cover, vy), end = Offset(tankLeft + cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)), strokeWidth = 2.5f)
            drawLine(color = RebarBlue, start = Offset(tankLeft + wt - cover, vy), end = Offset(tankLeft + wt - cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)), strokeWidth = 2.5f)
            drawLine(color = RebarBlue, start = Offset(tankRight - cover, vy), end = Offset(tankRight - cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)), strokeWidth = 2.5f)
            drawLine(color = RebarBlue, start = Offset(tankRight - wt + cover, vy), end = Offset(tankRight - wt + cover, vy + min(vSpacingPx * 0.7f, tankBottom - vy)), strokeWidth = 2.5f)
            vy += vSpacingPx
        }

        var hx = tankTop + cover
        while (hx < tankBottom) {
            drawLine(color = RebarLightBlue, start = Offset(tankLeft + 4f, hx), end = Offset(tankLeft + wt - 4f, hx), strokeWidth = 1.5f)
            drawLine(color = RebarLightBlue, start = Offset(tankRight - wt + 4f, hx), end = Offset(tankRight - 4f, hx), strokeWidth = 1.5f)
            hx += hSpacingPx
        }

        var bx = tankLeft + cover
        while (bx < tankRight - cover) {
            drawLine(color = RebarBlue, start = Offset(bx, baseBottom - cover), end = Offset(bx + min(vSpacingPx * 0.6f, tankRight - cover - bx), baseBottom - cover), strokeWidth = 2.5f)
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
    drawDimLine(left - ext, top, left - ext, bottom, "H=${(heightM * 1000).toInt()}mm", true)
    val lengthLabel = if (isCircular) "D=${(lengthM * 1000).toInt()}mm" else "L=${(lengthM * 1000).toInt()}mm"
    drawDimLine(left, top - ext, right, top - ext, lengthLabel, false)
    drawDimLine(left - ext - 30f, top + 15f, left, top + 15f, "t=${wallThickM.toInt()}mm", false)
    drawDimLine(right + ext, bottom, right + ext, baseBottom, "tb=${baseThickM.toInt()}mm", true)
}

// ============================================================================
// PRESSURE DIAGRAM
// ============================================================================

private fun DrawScope.drawWaterPressureDiagram(
    cw: Float, tankLeft: Float, tankTop: Float, l: Float, h: Float,
    wl: Float, waterLevel: Double, isElevated: Boolean
) {
    if (wl <= 0) return
    val diagramX = min(tankLeft + l + 30f, cw - 180f)
    val diagramW = 60f
    val waterTop = tankTop + h - wl
    val gammaW = 9.81
    val maxPressure = waterLevel * gammaW

    val pressurePath = Path().apply {
        moveTo(diagramX, waterTop)
        lineTo(diagramX + diagramW, tankTop + h)
        lineTo(diagramX, tankTop + h)
        close()
    }
    drawPath(path = pressurePath, color = PressurePink.copy(alpha = 0.3f))
    drawPath(path = pressurePath, color = PressurePink, style = Stroke(width = 1.5f))

    drawTextAnnotated("Pw", diagramX + diagramW + 6f, tankTop + h - 8f, PressurePink, 16f)
    drawTextAnnotated("q = \u03B3w \u00D7 h = ${"%.1f".format(maxPressure)} kN/m\u00B2", diagramX - 10f, tankTop + h + 20f, PressurePink, 13f)
    drawLine(color = PressurePink.copy(alpha = 0.6f), start = Offset(diagramX - 5f, waterTop), end = Offset(diagramX + 8f, waterTop), strokeWidth = 1f)
    drawTextAnnotated("0", diagramX - 14f, waterTop + 4f, PressurePink.copy(alpha = 0.6f), 12f)

    if (isElevated) {
        drawTextAnnotated("(+ seismic)", diagramX - 10f, tankTop + h + 52f, PressurePink.copy(alpha = 0.6f), 11f)
    }
}

// ============================================================================
// WALL DETAIL INSET
// ============================================================================

private fun DrawScope.drawWallDetailInset(
    cw: Float, ch: Float,
    wallThickness: Double, vDia: Double, hDia: Double,
    cover: Float, scale: Float
) {
    val planViewBottom = 12f + 20f + min(100f, cw * 0.18f) + 16f
    val tableTop = ch * 0.64f
    val insetW = min(150f, cw * 0.20f)
    val maxInsetH = (tableTop - planViewBottom - 20f).coerceAtLeast(80f)
    val insetH = min(200f, maxInsetH).coerceAtLeast(100f)
    val insetLeft = cw - insetW - 16f
    val insetTop = (planViewBottom + 10f).coerceAtMost(tableTop - insetH - 10f)

    drawRoundRect(color = Color(0x22000000), topLeft = Offset(insetLeft - 8f, insetTop - 8f), size = Size(insetW + 16f, insetH + 16f), cornerRadius = CornerRadius(8f, 8f))
    drawTextAnnotated("WALL DETAIL", insetLeft, insetTop - 2f, DimensionWhite, 16f)

    val wallW = min(insetW * 0.45f, 50f)
    val wallH = insetH - 60f
    val wallLeft = insetLeft + (insetW - wallW) / 2f
    val wallTop = insetTop + 24f

    drawRect(color = ConcreteGray, topLeft = Offset(wallLeft, wallTop), size = Size(wallW, wallH))
    drawRect(color = DimensionWhite.copy(alpha = 0.5f), topLeft = Offset(wallLeft, wallTop), size = Size(wallW, wallH), style = Stroke(width = 1.5f))

    drawConcreteHatching(wallLeft, wallTop, wallW, wallH)

    val vBarR = max(vDia.toFloat() / 2f * 0.8f, 2.5f)
    var vy = wallTop + 10f
    while (vy < wallTop + wallH - 10f) {
        drawCircle(color = RebarBlue, radius = vBarR, center = Offset(wallLeft + wallW / 2f - 4f, vy))
        vy += 22f
    }

    val hBarR = max(hDia.toFloat() / 2f * 0.6f, 2f)
    drawCircle(color = RebarLightBlue, radius = hBarR, center = Offset(wallLeft + wallW / 2f + 4f, wallTop + wallH / 3f))
    drawCircle(color = RebarLightBlue, radius = hBarR, center = Offset(wallLeft + wallW / 2f + 4f, wallTop + 2 * wallH / 3f))

    val cDimX = wallLeft - 12f
    drawLine(color = ExtensionGray, start = Offset(cDimX, wallTop), end = Offset(cDimX, wallTop + 15f), strokeWidth = 1f)
    drawTextAnnotated("50", cDimX - 16f, wallTop + 12f, ExtensionGray, 12f)

    drawTextAnnotated("t=${wallThickness.toInt()}", wallLeft + wallW / 2f - 16f, wallTop + wallH + 18f, DimensionWhite, 13f)
}

// ============================================================================
// PLAN VIEW
// ============================================================================

private fun DrawScope.drawPlanView(
    cw: Float, tankType: String, lengthM: Double, widthM: Double,
    isCircular: Boolean, tableTop: Float
) {
    val insetSize = min(100f, cw * 0.15f)
    val insetLeft = cw - insetSize - 20f
    val insetTop = tableTop - insetSize - 40f

    drawRoundRect(color = PanelBg, topLeft = Offset(insetLeft - 8f, insetTop - 22f), size = Size(insetSize + 16f, insetSize + 34f), cornerRadius = CornerRadius(6f))
    drawTextAnnotated("PLAN", insetLeft, insetTop - 6f, TextColor, 13f)

    val cx = insetLeft + insetSize / 2f
    val cy = insetTop + 12f + insetSize / 2f

    if (isCircular) {
        val r = insetSize / 2f - 6f
        drawCircle(color = ConcreteFill, radius = r, center = Offset(cx, cy))
        drawCircle(color = Color.White.copy(alpha = 0.5f), radius = r, center = Offset(cx, cy), style = Stroke(1.5f))
        for (i in 0..7) {
            val a = Math.toRadians(i * 45.0)
            drawCircle(color = HoopGreen, radius = 2f, center = Offset(cx + (r - 4f) * Math.cos(a).toFloat(), cy + (r - 4f) * Math.sin(a).toFloat()))
        }
        drawTextAnnotated("D=${lengthM.toInt()}m", cx, cy + r + 14f, TextColor, 11f, center = true)
    } else {
        val rw = insetSize - 12f
        val rh = (widthM / lengthM).toFloat() * rw
        val rl = cx - rw / 2f
        val rt = cy - rh / 2f
        drawRect(color = ConcreteFill, topLeft = Offset(rl, rt), size = Size(rw, rh))
        drawRect(color = Color.White.copy(alpha = 0.5f), topLeft = Offset(rl, rt), size = Size(rw, rh), style = Stroke(1.5f))
        drawTextAnnotated("${lengthM.toInt()}x${widthM.toInt()}", cx, rt + rh + 14f, TextColor, 11f, center = true)
    }
}

// ============================================================================
// REINFORCEMENT TABLE
// ============================================================================

private fun DrawScope.drawRebarTable(
    cw: Float, tableTop: Float, ch: Float, tankType: String,
    vDia: Double, vSpacing: Double,
    hDia: Double, hSpacing: Double,
    height: Double, length: Double
) {
    val tableLeft = 16f
    val tableW = cw - 32f
    val rowH = 22f
    val headerH = 26f
    val isCircular = tankType.contains("Circular")

    drawRoundRect(color = Color(0x22FFFFFF), topLeft = Offset(tableLeft - 4f, tableTop - 22f), size = Size(tableW + 8f, headerH + rowH * 3 + 26f), cornerRadius = CornerRadius(6f))
    drawTextAnnotated("REINFORCEMENT SCHEDULE", tableLeft, tableTop - 6f, TextColor, 16f, bold = true)

    val headers = arrayOf("Direction", "Location", "Dia.", "Spacing", "As Provided")
    val colWidths = floatArrayOf(tableW * 0.22f, tableW * 0.20f, tableW * 0.16f, tableW * 0.22f, tableW * 0.20f)

    drawRect(color = TableHeaderBg, topLeft = Offset(tableLeft, tableTop), size = Size(tableW, headerH))
    var cx = tableLeft
    for (i in headers.indices) {
        drawTextAnnotated(headers[i], cx + 6f, tableTop + headerH / 2f + 5f, TextColor, 13f, bold = true)
        cx += colWidths[i]
    }

    val rows = listOf(
        arrayOf(if (isCircular) "Hoop" else "Vertical", "Wall (both faces)", "\u00D8${vDia.toInt()}", "@${vSpacing.toInt()}mm", if (isCircular) "Hoop" else "T&S"),
        arrayOf(if (isCircular) "Vertical" else "Horizontal", "Wall", "\u00D8${hDia.toInt()}", "@${hSpacing.toInt()}mm", "Distribution"),
        arrayOf("Both", "Base Slab", "\u00D8${vDia.toInt()}", "@${hSpacing.toInt()}mm", "Top & Bottom")
    )

    rows.forEachIndexed { idx, row ->
        val rY = tableTop + headerH + idx * rowH
        if (idx % 2 == 0) drawRect(color = TableRowAlt, topLeft = Offset(tableLeft, rY), size = Size(tableW, rowH))
        cx = tableLeft
        val barColor = if (idx == 0) RebarBlue else if (idx == 1) RebarLightBlue else RebarBlue.copy(alpha = 0.7f)
        for (i in row.indices) {
            drawTextAnnotated(row[i], cx + 6f, rY + rowH / 2f + 4f, barColor, 12f)
            cx += colWidths[i]
        }
    }

    drawRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(tableLeft, tableTop), size = Size(tableW, headerH + rowH * 3), style = Stroke(width = 1f))
    var sepX = tableLeft
    for (i in 0 until colWidths.size - 1) {
        sepX += colWidths[i]
        drawLine(color = Color.White.copy(alpha = 0.15f), start = Offset(sepX, tableTop), end = Offset(sepX, tableTop + headerH + rowH * 3), strokeWidth = 0.5f)
    }
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
    text: String, x: Float, y: Float, color: Color, size: Float, bold: Boolean = false, center: Boolean = false
) {
    val p = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = size
        isFakeBoldText = bold
        textAlign = if (center) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
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
        drawArrowHead(x1, y1, 1f, DimColor, true)
        drawArrowHead(x1, y2, -1f, DimColor, true)
        drawTextAnnotated(text, x1 - 50f, (y1 + y2) / 2f + 4f, DimColor, 12f)
    } else {
        drawLine(color = DimColor, start = Offset(x1, y1), end = Offset(x2, y1), strokeWidth = 1f)
        drawLine(color = DimColor, start = Offset(x1, y1 - tick), end = Offset(x1, y1 + tick), strokeWidth = 1f)
        drawLine(color = DimColor, start = Offset(x2, y1 - tick), end = Offset(x2, y1 + tick), strokeWidth = 1f)
        drawArrowHead(x1, y1, 1f, DimColor, false)
        drawArrowHead(x2, y1, -1f, DimColor, false)
        drawTextAnnotated(text, (x1 + x2) / 2f, y1 - 8f, DimColor, 12f, center = true)
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
