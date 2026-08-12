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
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
    ) {
        val cw = size.width
        val ch = size.height

        val isCircular = tankType.contains("Circular")
        val isElevated = tankType.contains("Elevated")
        val isUnderground = foundationDepth > 0

        // ── Background ──
        drawRoundRect(
            color = Color(0xFF1A1A2E),
            topLeft = Offset.Zero,
            size = Size(cw, ch),
            cornerRadius = CornerRadius(12f, 12f)
        )

        // ── Title ──
        val titleText = when (viewMode) {
            1 -> "WATER TANK - PERSPECTIVE VIEW"
            2 -> "WATER TANK - CROSS SECTION DETAIL"
            3 -> "WATER TANK - REINFORCEMENT DETAIL"
            else -> "WATER TANK - CROSS SECTION"
        }
        val paint = android.graphics.Paint().apply {
            color = Color.White.toArgb()
            textSize = 18f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            titleText, cw / 2f, 30f, paint
        )

        when (viewMode) {
            1 -> drawPerspectiveView(cw, ch, tankType, isCircular, isElevated, isUnderground,
                length, width, height, wallThickness, baseThickness, waterLevel, foundationDepth)
            2 -> drawSectionDetail(cw, ch, tankType, isCircular, isElevated, isUnderground,
                length, width, height, wallThickness, baseThickness, waterLevel, foundationDepth)
            3 -> drawReinforcementDetail(cw, ch, tankType, isCircular, isElevated, isUnderground,
                length, width, height, wallThickness, baseThickness, waterLevel, foundationDepth,
                verticalRebarDia, verticalRebarSpacing, horizontalRebarDia, horizontalRebarSpacing)
            else -> drawAllView(cw, ch, tankType, isCircular, isElevated, isUnderground,
                length, width, height, wallThickness, baseThickness, waterLevel, foundationDepth,
                verticalRebarDia, verticalRebarSpacing, horizontalRebarDia, horizontalRebarSpacing)
        }
    }
}

// ==================== VIEW 0: ALL (original combined view) ====================
private fun DrawScope.drawAllView(
    cw: Float, ch: Float,
    tankType: String, isCircular: Boolean, isElevated: Boolean, isUnderground: Boolean,
    length: Double, width: Double, height: Double,
    wallThickness: Double, baseThickness: Double, waterLevel: Double, foundationDepth: Double,
    verticalRebarDia: Double, verticalRebarSpacing: Double,
    horizontalRebarDia: Double, horizontalRebarSpacing: Double
) {

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
        color = HoopGreen.copy(alpha = 0.4f), style = Stroke(1.5f, PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
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
    val insetSize = min(100f, cw * 0.15f)
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
        drawLine(color = Color.White.copy(alpha = 0.15f),
            start = Offset(sepX, tableTop), end = Offset(sepX, tableTop + headerH + rowH * 3), strokeWidth = 0.5f)
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
