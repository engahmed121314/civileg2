package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ─── Data classes ───────────────────────────────────────────────────────────

data class BarInfo(
    val x: Double,
    val y: Double,
    val diameter: Double,
    val isCorner: Boolean = false
)

// ─── Color palette (matches beam drawing style) ─────────────────────────────

private object C {
    val Concrete = Color(0xFF888888)
    val ConcreteLight = Color(0xFFA0A0A0)
    val ConcreteDark = Color(0xFF606060)
    val Bar = Color(0xFF4A90D9)
    val CornerBar = Color(0xFF5BA0E9)
    val Tie = Color(0xFF9B59B6)
    val TieLight = Color(0xFFB07CC8)
    val White = Color(0xFFFFFFFF)
    val DimLine = Color(0xFFCCCCCC)
    val Safe = Color(0xFF27AE60)
    val Unsafe = Color(0xFFE74C3C)
    val Center = Color(0xFF666666)
    val Hatch = Color(0xFFAAAAAA)
    val TblBorder = Color(0xFF555555)
    val TblHeader = Color(0xFF3A3A3A)
    val TblBg = Color(0xFF2A2A2A)
    val Slab = Color(0xFF505050)
    val Grid = Color(0xFF444444)
    val BgDark = Color(0xFF2A2A2A)
    val BgDarker = Color(0xFF333333)
}

private val circledNums = listOf(
    "\u2460", "\u2461", "\u2462", "\u2463", "\u2464",
    "\u2465", "\u2466", "\u2467", "\u2468", "\u2469",
    "\u246A", "\u246B", "\u246C", "\u246D", "\u246E",
    "\u246F", "\u2470", "\u2471", "\u2472", "\u2473"
)

// ─── Main composable ────────────────────────────────────────────────────────

@Composable
fun ProfessionalColumnDrawing(
    columnWidth: Double,
    columnDepth: Double,
    columnHeight: Double,
    longitudinalBars: List<BarInfo>,
    tieDia: Double,
    tieSpacing: Double,
    cover: Double,
    isSpiral: Boolean = false,
    spiralPitch: Double = 0.0,
    sectionType: String = "Rectangular",
    interactionPoints: List<Pair<Double, Double>> = emptyList(),
    designPoint: Pair<Double, Double> = Pair(0.0, 0.0),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(680.dp)) {
        val W = size.width
        val H = size.height
        val divX = W * 0.40f
        val midY = H * 0.52f

        // 1. 3D Column Elevation (left)
        draw3DElevation(16f, 16f, divX - 32f, midY - 20f,
            columnWidth, columnDepth, columnHeight,
            longitudinalBars, tieDia, tieSpacing, cover, isSpiral, sectionType)

        // 2. Cross-Section View (right-top)
        drawCrossSection(divX + 16f, 16f, W - divX - 32f, midY - 20f,
            columnWidth, columnDepth, longitudinalBars, tieDia, cover, isSpiral, sectionType)

        // 3. Tie/Spiral Detail Inset (below elevation)
        drawTieDetailInset(16f, midY + 10f, divX - 32f, H - midY - 120f,
            columnWidth, columnDepth, longitudinalBars, tieDia, tieSpacing, cover,
            isSpiral, spiralPitch, sectionType)

        // 4. Section Dimensions (overlaid on cross-section)
        drawSectionDimensions(divX + 16f, 16f, W - divX - 32f, midY - 20f,
            columnWidth, columnDepth, cover, longitudinalBars)

        // 5. Interaction Diagram (bottom-right)
        if (interactionPoints.isNotEmpty()) {
            drawInteractionDiagram(divX + 20f, midY + 10f,
                W - divX - 36f, H - midY - 120f, interactionPoints, designPoint)
        }

        // 6. Reinforcement Table (bottom)
        drawReinforcementTable(16f, H - 100f, W - 32f, 90f,
            longitudinalBars, tieDia, tieSpacing, isSpiral, spiralPitch, sectionType)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 1. 3D COLUMN ELEVATION
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.draw3DElevation(
    left: Float, top: Float, width: Float, height: Float,
    colW: Double, colD: Double, colH: Double,
    bars: List<BarInfo>, tieDia: Double, tieSpacing: Double,
    cover: Double, isSpiral: Boolean, sectionType: String
) {
    val cosA = cos(Math.toRadians(30.0)).toFloat()
    val sinA = sin(Math.toRadians(30.0)).toFloat()
    val dScale = 0.45f
    val maxDim = maxOf(colW, colH)
    val scale = (min(width, height) * 0.45f) / maxDim.toFloat()

    val w = colW.toFloat() * scale
    val h = colH.toFloat() * scale
    val d = colD.toFloat() * scale * dScale
    val ox = left + width * 0.42f - w / 2f
    val oy = top + height * 0.5f - h / 2f
    val slabH = h * 0.06f

    // ── Top slab ───────────────────────────────────────────────────────
    val ext = w * 0.2f
    val topSlab = Path().apply {
        moveTo(ox - ext, oy - slabH); lineTo(ox + w + ext, oy - slabH)
        lineTo(ox + w + ext, oy); lineTo(ox - ext, oy); close()
        moveTo(ox - ext, oy - slabH); lineTo(ox - ext + d * cosA, oy - slabH - d * sinA)
        lineTo(ox + w + ext + d * cosA, oy - slabH - d * sinA)
        lineTo(ox + w + ext, oy - slabH); close()
        moveTo(ox + w + ext, oy - slabH); lineTo(ox + w + ext + d * cosA, oy - slabH - d * sinA)
        lineTo(ox + w + ext + d * cosA, oy - d * sinA); lineTo(ox + w + ext, oy); close()
    }
    drawPath(topSlab, color = C.Slab, style = Fill)
    drawPath(topSlab, color = C.ConcreteDark, style = Stroke(width = 1.5f))
    drawHatch(Offset(ox - ext, oy - slabH - d * sinA), w + 2 * ext + d * cosA, slabH + d * sinA)

    // ── Bottom slab ────────────────────────────────────────────────────
    val bY = oy + h
    val botSlab = Path().apply {
        moveTo(ox - ext, bY); lineTo(ox + w + ext, bY)
        lineTo(ox + w + ext, bY + slabH); lineTo(ox - ext, bY + slabH); close()
        moveTo(ox - ext, bY); lineTo(ox - ext + d * cosA, bY - d * sinA)
        lineTo(ox + w + ext + d * cosA, bY - d * sinA); lineTo(ox + w + ext, bY); close()
        moveTo(ox + w + ext, bY); lineTo(ox + w + ext + d * cosA, bY - d * sinA)
        lineTo(ox + w + ext + d * cosA, bY + slabH - d * sinA); lineTo(ox + w + ext, bY + slabH); close()
    }
    drawPath(botSlab, color = C.Slab, style = Fill)
    drawPath(botSlab, color = C.ConcreteDark, style = Stroke(width = 1.5f))

    // ── Front face (lighter) ───────────────────────────────────────────
    drawRect(color = C.ConcreteLight, topLeft = Offset(ox, oy), size = Size(w, h))

    // ── Right side face (darker) ───────────────────────────────────────
    val sidePath = Path().apply {
        moveTo(ox + w, oy); lineTo(ox + w + d * cosA, oy - d * sinA)
        lineTo(ox + w + d * cosA, oy + h - d * sinA); lineTo(ox + w, oy + h); close()
    }
    drawPath(sidePath, color = C.ConcreteDark, style = Fill)
    drawPath(sidePath, color = C.Concrete, style = Stroke(width = 1.5f))

    // ── Top face (isometric) ───────────────────────────────────────────
    val topPath = Path().apply {
        moveTo(ox, oy); lineTo(ox + d * cosA, oy - d * sinA)
        lineTo(ox + w + d * cosA, oy - d * sinA); lineTo(ox + w, oy); close()
    }
    drawPath(topPath, color = C.Concrete, style = Fill)
    drawPath(topPath, color = C.ConcreteDark, style = Stroke(width = 1.5f))

    // ── Front face border ──────────────────────────────────────────────
    drawRect(color = C.ConcreteDark, topLeft = Offset(ox, oy), size = Size(w, h), style = Stroke(width = 2f))

    // ── Longitudinal bars (blue vertical lines) ────────────────────────
    val visibleBars = bars.filter { it.y <= colD / 2 }.take(6)
    visibleBars.forEach { bar ->
        val bx = ox + (bar.x.toFloat() / colW.toFloat()) * w
        drawLine(color = C.Bar, start = Offset(bx, oy + 4f), end = Offset(bx, oy + h - 4f),
            strokeWidth = (bar.diameter.toFloat() * scale * 0.25f).coerceIn(2f, 5f))
    }
    // Side face bars (projected)
    bars.filter { it.x >= colW * 0.8 }.take(3).forEach { bar ->
        val yRatio = bar.y.toFloat() / colD.toFloat()
        val xOff = d * cosA * yRatio
        drawLine(color = C.Bar.copy(alpha = 0.5f),
            start = Offset(ox + w + xOff, oy + 4f - d * sinA * yRatio),
            end = Offset(ox + w + xOff, oy + h - 4f - d * sinA * yRatio),
            strokeWidth = (bar.diameter.toFloat() * scale * 0.2f).coerceIn(1.5f, 4f))
    }

    // ── Ties (purple horizontal lines) ─────────────────────────────────
    val numTies = (colH / tieSpacing).toInt().coerceIn(3, 20)
    val step = h / (numTies + 1)
    val covOff = cover.toFloat() * scale * 0.4f
    for (i in 1..numTies) {
        val ty = oy + i * step
        drawLine(color = C.Tie, start = Offset(ox + covOff, ty), end = Offset(ox + w - covOff, ty),
            strokeWidth = (tieDia.toFloat() * scale * 0.18f).coerceIn(1f, 3f))
        drawLine(color = C.Tie.copy(alpha = 0.5f), start = Offset(ox + w, ty),
            end = Offset(ox + w + d * cosA, ty - d * sinA),
            strokeWidth = (tieDia.toFloat() * scale * 0.14f).coerceIn(1f, 2.5f))
    }

    // Column dimensions label on elevation
    val dimLabelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE; textSize = 16f; isFakeBoldText = false
        textAlign = android.graphics.Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.apply {
        drawText("${colW.toInt()}×${colD.toInt()}×${colH.toInt()} mm", ox, oy - slabH - d * sinA - 14f, dimLabelPaint)
        // Section type label
        drawText("($sectionType)", ox, oy - slabH - d * sinA + 2f, dimLabelPaint)
    }

    // Elevation height dimension (right side of 3D view)
    val elevDimX = ox + w + d * cosA + 16f
    drawLine(C.DimLine, Offset(elevDimX, oy), Offset(elevDimX, oy + h), 1f)
    drawTriV(elevDimX, oy, 4f, true)
    drawTriV(elevDimX, oy + h, 4f, false)
    drawContext.canvas.nativeCanvas.apply {
        val dp = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; textSize = 14f
        }
        save(); rotate(-90f, elevDimX + 12f, oy + h / 2f)
        drawText("${colH.toInt()}", elevDimX + 12f, oy + h / 2f + 5f, dp)
        restore()
    }

    drawLabel("ELEVATION", left + width / 2f, top + height - 8f, 26f, true)
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. CROSS-SECTION VIEW
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawCrossSection(
    left: Float, top: Float, width: Float, height: Float,
    colW: Double, colD: Double, bars: List<BarInfo>,
    tieDia: Double, cover: Double, isSpiral: Boolean, sectionType: String
) {
    val pad = 60f
    val avW = width - 2 * pad
    val avH = height - 2 * pad - 20f
    val scale = min(avW / colW.toFloat(), avH / colD.toFloat()) * 0.85f
    val dw = colW.toFloat() * scale
    val dh = colD.toFloat() * scale
    val cx = left + pad + (avW - dw) / 2f
    val cy = top + pad + 20f + (avH - dh) / 2f
    val centerX = cx + dw / 2f
    val centerY = cy + dh / 2f
    val clExt = 18f
    val covPx = cover.toFloat() * scale

    // Concrete fill
    if (sectionType == "Circular") {
        val r = min(dw, dh) / 2f
        drawCircle(color = C.Concrete, radius = r, center = Offset(centerX, centerY))
        drawCircle(color = C.ConcreteDark, radius = r, center = Offset(centerX, centerY), style = Stroke(2.5f))
    } else {
        drawRect(color = C.Concrete, topLeft = Offset(cx, cy), size = Size(dw, dh))
        drawRect(color = C.ConcreteDark, topLeft = Offset(cx, cy), size = Size(dw, dh), style = Stroke(2.5f))
    }

    // Centerlines
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
    drawLine(C.Center, Offset(cx - clExt, centerY), Offset(cx + dw + clExt, centerY), 1f, pathEffect = dash)
    drawLine(C.Center, Offset(centerX, cy - clExt), Offset(centerX, cy + dh + clExt), 1f, pathEffect = dash)

    // Tie / Spiral
    val tieL = cx + covPx; val tieT = cy + covPx
    val tieW = dw - 2 * covPx; val tieH = dh - 2 * covPx
    val tieStroke = (tieDia.toFloat() * scale * 0.12f).coerceIn(1.5f, 3f)
    val tieDash = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))

    if (sectionType == "Circular") {
        val coreR = min(dw, dh) / 2f - covPx
        if (isSpiral) drawSpiralCircle(centerX, centerY, coreR, 4)
        else drawCircle(color = C.Tie, radius = coreR, center = Offset(centerX, centerY), style = Stroke(tieStroke))
    } else {
        if (isSpiral) {
            drawSpiralRect(tieL, tieT, tieW, tieH, 4)
        } else {
            drawRect(color = C.Tie, topLeft = Offset(tieL, tieT), size = Size(tieW, tieH),
                style = Stroke(width = tieStroke, pathEffect = tieDash))
            // Crossties for > 8 bars
            if (bars.size > 8) {
                val midX = tieL + tieW / 2f
                val csDash = PathEffect.dashPathEffect(floatArrayOf(6f, 3f))
                val csStroke = (tieDia.toFloat() * scale * 0.10f).coerceIn(1f, 2.5f)
                drawLine(C.Tie, Offset(tieL, centerY), Offset(midX, centerY), csStroke, pathEffect = csDash)
                drawLine(C.Tie, Offset(midX, tieT), Offset(midX, centerY), csStroke, pathEffect = csDash)
            }
        }
    }

    // Longitudinal bars
    bars.forEachIndexed { index, bar ->
        val bx = cx + (bar.x.toFloat() / colW.toFloat()) * dw
        val by = cy + (bar.y.toFloat() / colD.toFloat()) * dh
        val br = (bar.diameter.toFloat() / 2f * scale * 0.45f).coerceIn(3.5f, 9f)
        val barColor = if (bar.isCorner) C.CornerBar else C.Bar
        drawCircle(color = barColor, radius = br, center = Offset(bx, by))
        drawCircle(color = Color.Black, radius = br, center = Offset(bx, by), style = Stroke(0.8f))
        drawCircle(color = Color.White, radius = br * 0.25f, center = Offset(bx, by))
    }

    // Bar marks with leader lines
    val marked = mutableSetOf<Double>(); var mi = 0
    bars.forEach { bar ->
        if (marked.add(bar.diameter) && mi < circledNums.size) {
            val bx = cx + (bar.x.toFloat() / colW.toFloat()) * dw
            val by = cy + (bar.y.toFloat() / colD.toFloat()) * dh
            val br = (bar.diameter.toFloat() / 2f * scale * 0.45f).coerceIn(3.5f, 9f)
            val leaderEnd = Offset(bx + br + 14f, by - br - 14f)
            drawLine(C.White, Offset(bx + br, by - br), leaderEnd, 1f)
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 18f; isFakeBoldText = true }
                drawText(circledNums[mi], leaderEnd.x + 2f, leaderEnd.y - 2f, p)
                p.textSize = 14f; p.isFakeBoldText = false
                drawText("\u00D8${bar.diameter.toInt()}", leaderEnd.x + 2f, leaderEnd.y + 14f, p)
            }
            mi++
        }
    }

    // Bar spacing dimensions (horizontal bars on same row)
    val barsByRow = bars.groupBy { it.y }
    barsByRow.values.forEach { rowBars ->
        if (rowBars.size >= 2) {
            val sorted = rowBars.sortedBy { it.x }
            for (i in 0 until sorted.size - 1) {
                val b1x = cx + (sorted[i].x.toFloat() / colW.toFloat()) * dw
                val b2x = cx + (sorted[i + 1].x.toFloat() / colW.toFloat()) * dw
                val br1 = (sorted[i].diameter.toFloat() / 2f * scale * 0.45f).coerceIn(3.5f, 9f)
                val br2 = (sorted[i + 1].diameter.toFloat() / 2f * scale * 0.45f).coerceIn(3.5f, 9f)
                val spacingMm = sorted[i + 1].x - sorted[i].x
                // Small dimension between bars
                val spY = cy + (sorted[i].y.toFloat() / colD.toFloat()) * dh + br1 + 12f
                val barNative = drawContext.canvas.nativeCanvas
                val spPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#BBBBBB"); textSize = 11f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                barNative.drawText("${spacingMm.toInt()}", (b1x + b2x) / 2f, spY, spPaint)
                // Tiny bracket lines
                drawLine(C.DimLine.copy(alpha = 0.5f), Offset(b1x, spY - 10f), Offset(b1x, spY - 6f), 0.6f)
                drawLine(C.DimLine.copy(alpha = 0.5f), Offset(b2x, spY - 10f), Offset(b2x, spY - 6f), 0.6f)
                drawLine(C.DimLine.copy(alpha = 0.5f), Offset(b1x, spY - 8f), Offset(b2x, spY - 8f), 0.6f)
            }
        }
    }

    // Cover dimensions on right and bottom
    val native = drawContext.canvas.nativeCanvas
    // Right cover
    val rCovX = cx + dw + 14f
    drawLine(C.DimLine.copy(alpha = 0.7f), Offset(rCovX, centerY), Offset(rCovX + covPx, centerY), 0.8f)
    native.drawText("c", rCovX + covPx / 2f - 4f, centerY - 6f,
        android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#AAAAAA"); textSize = 12f })
    // Bottom cover
    val bCovY = cy + dh + 14f
    drawLine(C.DimLine.copy(alpha = 0.7f), Offset(centerX, bCovY), Offset(centerX, bCovY + covPx), 0.8f)
    native.drawText("c", centerX + 4f, bCovY + covPx / 2f + 4f,
        android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#AAAAAA"); textSize = 12f })

    drawLabel("SECTION A-A", left + width / 2f, top + height - 8f, 26f, true)
}

// ═══════════════════════════════════════════════════════════════════════════
// 3. TIE / SPIRAL DETAIL INSET
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawTieDetailInset(
    left: Float, top: Float, width: Float, height: Float,
    colW: Double, colD: Double, bars: List<BarInfo>, tieDia: Double,
    tieSpacing: Double, cover: Double, isSpiral: Boolean,
    spiralPitch: Double, sectionType: String
) {
    drawRect(color = C.BgDarker, topLeft = Offset(left, top), size = Size(width, height))
    drawRect(color = C.TblBorder, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(2f))

    val ip = 16f
    if (isSpiral) drawSpiralDetailZoom(left + ip, top + ip, width - 2 * ip, height - 2 * ip - 20f,
        colW, colD, tieDia, spiralPitch, cover, sectionType)
    else drawTieDetailZoom(left + ip, top + ip, width - 2 * ip, height - 2 * ip - 20f,
        colW, colD, bars, tieDia, tieSpacing, cover, sectionType)

    val spacingLabel = if (isSpiral) "pitch = ${spiralPitch.toInt()} mm" else "s = ${tieSpacing.toInt()} mm"
    drawLabel(spacingLabel, left + width / 2f, top + height - 8f, 20f, true)
    drawLabel(if (isSpiral) "SPIRAL DETAIL" else "TIE DETAIL", left + 6f, top + 18f, 20f, false)
}

private fun DrawScope.drawTieDetailZoom(
    left: Float, top: Float, width: Float, height: Float,
    colW: Double, colD: Double, bars: List<BarInfo>,
    tieDia: Double, tieSpacing: Double, cover: Double, sectionType: String
) {
    val numLoops = 3
    val loopSpacing = height / (numLoops + 1)
    val scale = min((width * 0.7f) / colW.toFloat(), (loopSpacing * 0.7f) / colD.toFloat()) * 0.85f
    val dw = colW.toFloat() * scale
    val dh = colD.toFloat() * scale * 0.3f
    val ox = left + (width - dw) / 2f
    val covPx = cover.toFloat() * scale
    val hookLen = (tieDia * 6).toFloat() * scale * 0.08f

    for (i in 1..numLoops) {
        val oy = top + i * loopSpacing - dh / 2f
        val tieL = ox + covPx; val tieW = dw - 2 * covPx

        // Tie rectangle
        drawRect(color = C.Tie, topLeft = Offset(tieL, oy), size = Size(tieW, dh),
            style = Stroke((tieDia.toFloat() * scale * 0.15f).coerceIn(1.5f, 3.5f)))

        // 135° hooks at 4 corners
        val corners = listOf(
            Offset(tieL, oy) to 135.0, Offset(tieL + tieW, oy) to 225.0,
            Offset(tieL + tieW, oy + dh) to 315.0, Offset(tieL, oy + dh) to 45.0
        )
        corners.forEach { (corner, angle) ->
            val rad = Math.toRadians(angle)
            drawLine(C.TieLight, corner,
                Offset(corner.x + hookLen * cos(rad).toFloat(), corner.y + hookLen * sin(rad).toFloat()),
                (tieDia.toFloat() * scale * 0.12f).coerceIn(1f, 2.5f))
        }
    }

    // Vertical bars between ties
    bars.map { it.x.toFloat() / colW.toFloat() }.distinct().take(6).forEach { ratio ->
        val bx = ox + ratio * dw
        drawLine(C.Bar, Offset(bx, top + loopSpacing - dh), Offset(bx, top + (numLoops + 0.5f) * loopSpacing), 2.5f)
    }

    // Spacing arrow
    drawDimArrow(ox + dw + 12f, top + loopSpacing, ox + dw + 12f, top + 2 * loopSpacing)
}

private fun DrawScope.drawSpiralDetailZoom(
    left: Float, top: Float, width: Float, height: Float,
    colW: Double, colD: Double, tieDia: Double, spiralPitch: Double,
    cover: Double, sectionType: String
) {
    val cx = left + width / 2f
    val cy = top + height / 2f
    val maxR = min(width, height) * 0.35f
    val path = Path()
    val turns = 4; val total = turns * 40

    for (i in 0..total) {
        val t = i.toFloat() / total
        val angle = t * turns * 2 * kotlin.math.PI.toFloat()
        val x = cx + maxR * cos(angle)
        val y = cy - height * 0.35f + t * height * 0.7f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = C.Tie, style = Stroke((tieDia.toFloat() * 0.15f).coerceIn(1.5f, 3f)))
    drawLine(C.Bar, Offset(cx, cy - height * 0.3f), Offset(cx, cy + height * 0.3f), 3f)
}

// ═══════════════════════════════════════════════════════════════════════════
// 4. DIMENSION LINES
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawSectionDimensions(
    left: Float, top: Float, width: Float, height: Float,
    colW: Double, colD: Double, cover: Double, bars: List<BarInfo>
) {
    val pad = 60f
    val avW = width - 2 * pad; val avH = height - 2 * pad - 20f
    val scale = min(avW / colW.toFloat(), avH / colD.toFloat()) * 0.85f
    val dw = colW.toFloat() * scale; val dh = colD.toFloat() * scale
    val cx = left + pad + (avW - dw) / 2f
    val cy = top + pad + 20f + (avH - dh) / 2f
    val covPx = cover.toFloat() * scale
    val native = drawContext.canvas.nativeCanvas

    // Width (top)
    val dimY = cy - 24f
    drawLine(C.DimLine, Offset(cx, dimY), Offset(cx + dw, dimY), 1f)
    drawLine(C.DimLine, Offset(cx, dimY - 6f), Offset(cx, cy - 4f), 0.8f)
    drawLine(C.DimLine, Offset(cx + dw, dimY - 6f), Offset(cx + dw, cy - 4f), 0.8f)
    drawTriH(cx, dimY, 5f, true); drawTriH(cx + dw, dimY, 5f, false)
    native.drawText("${colW.toInt()}", cx + dw / 2f - 20f, dimY - 5f,
        android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 18f })

    // Depth (right)
    val dimX = cx + dw + 24f
    drawLine(C.DimLine, Offset(dimX, cy), Offset(dimX, cy + dh), 1f)
    drawLine(C.DimLine, Offset(cx + dw + 4f, cy), Offset(dimX + 6f, cy), 0.8f)
    drawLine(C.DimLine, Offset(cx + dw + 4f, cy + dh), Offset(dimX + 6f, cy + dh), 0.8f)
    drawTriV(dimX, cy, 5f, true); drawTriV(dimX, cy + dh, 5f, false)
    native.apply {
        save(); rotate(-90f, dimX + 14f, cy + dh / 2f)
        drawText("${colD.toInt()}", dimX + 14f, cy + dh / 2f + 5f,
            android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 18f })
        restore()
    }

    // Cover (left side)
    val covDimX = cx - 20f
    drawLine(C.DimLine.copy(alpha = 0.7f), Offset(covDimX, cy), Offset(covDimX, cy + covPx), 0.8f)
    native.drawText("c=${cover.toInt()}", covDimX - 40f, cy + covPx / 2f + 4f,
        android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#AAAAAA"); textSize = 14f })
}

// ═══════════════════════════════════════════════════════════════════════════
// 5. INTERACTION DIAGRAM
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawInteractionDiagram(
    left: Float, top: Float, width: Float, height: Float,
    points: List<Pair<Double, Double>>, designPt: Pair<Double, Double>
) {
    drawRect(color = C.BgDark, topLeft = Offset(left, top), size = Size(width, height))
    drawRect(color = C.TblBorder, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(1.5f))

    val pad = 44f
    val pW = width - 2 * pad; val pH = height - 2 * pad
    val maxP = (points.maxOfOrNull { it.first } ?: 1000.0) * 1.15
    val maxM = (points.maxOfOrNull { it.second } ?: 100.0) * 1.15
    val native = drawContext.canvas.nativeCanvas

    fun toX(m: Double) = left + pad + (m / maxM * pW).toFloat()
    fun toY(p: Double) = top + height - pad - (p / maxP * pH).toFloat()

    // Grid
    for (i in 1..4) {
        drawLine(C.Grid, Offset(left + pad, toY(maxP * i / 5.0)), Offset(left + width - pad, toY(maxP * i / 5.0)), 0.5f)
        drawLine(C.Grid, Offset(toX(maxM * i / 5.0), top + pad), Offset(toX(maxM * i / 5.0), top + height - pad), 0.5f)
    }

    // Axes
    drawLine(C.DimLine, Offset(toX(0.0), top + pad - 10f), Offset(toX(0.0), top + height - pad), 1.5f)
    drawLine(C.DimLine, Offset(left + pad, toY(0.0)), Offset(left + width - pad + 10f, toY(0.0)), 1.5f)

    // Axis labels
    val lp = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CCCCCC"); textSize = 14f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    native.drawText("M (kN.m)", left + width / 2f, top + height - 4f, lp)
    native.apply { save(); rotate(-90f, left + 10f, top + height / 2f)
        drawText("P (kN)", left + 10f, top + height / 2f + 4f, lp); restore() }

    // Axis tick marks and values
    val tickPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#999999"); textSize = 11f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val tickLen = 4f
    // P-axis ticks (vertical)
    for (i in 0..5) {
        val valP = maxP * i / 5.0
        val ty = toY(valP)
        drawLine(C.DimLine, Offset(toX(0.0) - tickLen, ty), Offset(toX(0.0), ty), 1f)
        val label = if (valP >= 1000) "${(valP / 1000).toInt()}k" else "${valP.toInt()}"
        native.drawText(label, toX(0.0) - tickLen - 4f, ty + 4f,
            tickPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
    }
    // M-axis ticks (horizontal)
    for (i in 0..5) {
        val valM = maxM * i / 5.0
        val tx = toX(valM)
        drawLine(C.DimLine, Offset(tx, toY(0.0)), Offset(tx, toY(0.0) + tickLen), 1f)
        val label = if (valM >= 100) "${(valM / 1).toInt()}" else "${valM.toInt()}"
        native.drawText(label, tx, toY(0.0) + tickLen + 14f, tickPaint)
    }

    // Safe zone fill (green)
    if (points.size >= 3) {
        val fp = Path()
        points.forEachIndexed { i, (p, m) -> if (i == 0) fp.moveTo(toX(m), toY(p)) else fp.lineTo(toX(m), toY(p)) }
        fp.lineTo(toX(0.0), toY(0.0)); fp.close()
        drawPath(fp, color = C.Safe.copy(alpha = 0.15f), style = Fill)
    }

    // Unsafe tint background
    drawRect(color = C.Unsafe.copy(alpha = 0.06f), topLeft = Offset(left + pad, top + pad), size = Size(pW, pH))

    // Interaction curve
    if (points.size >= 2) {
        val cp = Path()
        points.forEachIndexed { i, (p, m) -> if (i == 0) cp.moveTo(toX(m), toY(p)) else cp.lineTo(toX(m), toY(p)) }
        cp.lineTo(toX(points.first().second), toY(points.first().first))
        drawPath(cp, color = C.Safe, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // Design point
    if (designPt.first != 0.0 || designPt.second != 0.0) {
        val dpx = toX(designPt.second); val dpy = toY(designPt.first)
        val inside = isInsideCurve(designPt, points)
        drawCircle(color = if (inside) C.Safe else C.Unsafe, radius = 7f, center = Offset(dpx, dpy))
        drawCircle(color = Color.White, radius = 3f, center = Offset(dpx, dpy))
        native.drawText("(${designPt.first.toInt()}, ${designPt.second.toInt()})", dpx + 12f, dpy - 8f,
            android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 12f; isFakeBoldText = true })
    }

    // Legend indicators
    val legY = top + height - 18f
    val legX = left + pad + 4f
    val legPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CCCCCC"); textSize = 11f
    }
    // Safe indicator
    drawCircle(color = C.Safe, radius = 4f, center = Offset(legX + 4f, legY - 4f))
    native.drawText("Safe", legX + 12f, legY, legPaint)
    // Curve indicator
    drawLine(C.Safe, Offset(legX + 46f, legY - 4f), Offset(legX + 60f, legY - 4f), 2f)
    native.drawText("Capacity", legX + 64f, legY, legPaint)
    // Point indicator
    drawCircle(color = C.Unsafe, radius = 4f, center = Offset(legX + 122f, legY - 4f))
    drawCircle(color = Color.White, radius = 1.5f, center = Offset(legX + 122f, legY - 4f))
    native.drawText("Design Pt", legX + 130f, legY, legPaint)

    drawLabel("P-M Interaction", left + 8f, top + 16f, 18f, false)
}

// ═══════════════════════════════════════════════════════════════════════════
// 6. REINFORCEMENT TABLE
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawReinforcementTable(
    left: Float, top: Float, width: Float, height: Float,
    bars: List<BarInfo>, tieDia: Double, tieSpacing: Double,
    isSpiral: Boolean, spiralPitch: Double, sectionType: String
) {
    drawRect(color = C.TblBg, topLeft = Offset(left, top), size = Size(width, height))
    drawRect(color = C.TblBorder, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(1.5f))

    val native = drawContext.canvas.nativeCanvas
    val hp = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 15f; isFakeBoldText = true }
    val tp = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 15f }

    // Color legend strip (top of table)
    val legendH = 14f
    var legOff = left + 4f
    drawRect(color = C.Bar, topLeft = Offset(legOff, top + 2f), size = Size(10f, 10f))
    legOff += 14f
    native.drawText("Bars", legOff, top + 12f, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE; textSize = 11f
    })
    legOff += 34f
    drawRect(color = C.Tie, topLeft = Offset(legOff, top + 2f), size = Size(10f, 10f))
    legOff += 14f
    native.drawText("Ties/Spiral", legOff, top + 12f, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE; textSize = 11f
    })
    legOff += 66f
    drawRect(color = C.Concrete, topLeft = Offset(legOff, top + 2f), size = Size(10f, 10f))
    legOff += 14f
    native.drawText("Concrete", legOff, top + 12f, android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE; textSize = 11f
    })

    // Header
    val hH = 22f
    val headerTop = top + legendH
    drawRect(color = C.TblHeader, topLeft = Offset(left, headerTop), size = Size(width, hH))
    val cols = listOf("Mark", "No.", "Dia.", "Position", "Tie/Spiral", "Type")
    val colW = listOf(0.12f, 0.13f, 0.13f, 0.20f, 0.22f, 1.0f).let { fracs ->
        fracs.dropLast(1).map { it * width }.toMutableList().also { it.add(width - it.sum()) }
    }
    var xOff = left
    cols.forEachIndexed { i, h ->
        native.drawText(h, xOff + 6f, headerTop + 16f, hp)
        xOff += colW[i]
        if (i < cols.lastIndex) drawLine(C.TblBorder, Offset(xOff, headerTop), Offset(xOff, top + height), 1f)
    }
    drawLine(C.TblBorder, Offset(left, headerTop + hH), Offset(left + width, headerTop + hH), 1f)

    // Rows
    val grouped = bars.groupBy { it.diameter }
    val rowY = headerTop + hH + 16f
    grouped.entries.forEachIndexed { idx, (dia, barList) ->
        val y = rowY + idx * 18f
        if (y > top + height - 4f) return@forEachIndexed
        val mark = if (idx < circledNums.size) circledNums[idx] else (idx + 1).toString()
        val pos = if (barList.any { it.isCorner }) "Corner+Side" else "Side"
        val tieInfo = if (isSpiral) "\u00D8${tieDia.toInt()} @ ${spiralPitch.toInt()}mm"
            else "\u00D8${tieDia.toInt()} @ ${tieSpacing.toInt()}mm"
        val tieType = if (isSpiral) "Spiral" else "Ties"
        val rowTexts = listOf(mark, "${barList.size}", "\u00D8${dia.toInt()}", pos, tieInfo, tieType)
        xOff = left
        rowTexts.forEachIndexed { i, txt ->
            native.drawText(txt, xOff + 6f, y, tp)
            xOff += colW[i]
        }
    }
    // Row separators
    grouped.entries.forEachIndexed { idx, _ ->
        val sepY = rowY + idx * 18f + 6f
        if (sepY < top + height) {
            drawLine(C.TblBorder.copy(alpha = 0.3f), Offset(left, sepY), Offset(left + width, sepY), 0.5f)
        }
    }

    // Total steel area summary (right-aligned)
    if (grouped.isNotEmpty()) {
        val totalAs = bars.sumOf { kotlin.math.PI * (it.diameter / 2.0) * (it.diameter / 2.0) }
        val sumPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#88CC88"); textSize = 13f
            textAlign = android.graphics.Paint.Align.RIGHT; isFakeBoldText = true
        }
        native.drawText("As = ${"%.1f".format(totalAs)} mm²", left + width - 8f, top + height - 6f, sumPaint)
    }

    if (grouped.isEmpty()) native.drawText("No reinforcement data", left + width / 2f - 60f, rowY, tp)
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawLabel(text: String, x: Float, y: Float, size: Float, center: Boolean) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y,
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; textSize = size; isFakeBoldText = true
            if (center) textAlign = android.graphics.Paint.Align.CENTER
        })
}

private fun DrawScope.drawHatch(origin: Offset, w: Float, h: Float) {
    val diag = sqrt(w * w + h * h)
    val steps = (diag / 6f).toInt()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        drawLine(C.Hatch.copy(alpha = 0.4f),
            Offset(origin.x + t * w, origin.y),
            Offset(origin.x, origin.y + t * h), 0.8f)
    }
}

private fun DrawScope.drawDimArrow(x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(C.DimLine, Offset(x1, y1), Offset(x2, y2), 1f)
    val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
    val len = 6f
    listOf(x1 to y1, x2 to y2).forEach { (px, py) ->
        val dir = if (px == x1) 1f else -1f
        for (s in listOf(1, -1)) {
            drawLine(C.DimLine, Offset(px, py),
                Offset((px + dir * len * cos(angle + s * 0.4).toFloat()), (py + dir * len * sin(angle + s * 0.4).toFloat())), 1f)
        }
    }
}

private fun DrawScope.drawTriH(x: Float, y: Float, s: Float, left: Boolean) {
    val d = if (left) -1f else 1f
    drawPath(Path().apply { moveTo(x, y); lineTo(x + d * s, y - s * 0.5f); lineTo(x + d * s, y + s * 0.5f); close() },
        color = C.DimLine, style = Fill)
}

private fun DrawScope.drawTriV(x: Float, y: Float, s: Float, up: Boolean) {
    val d = if (up) -1f else 1f
    drawPath(Path().apply { moveTo(x, y); lineTo(x - s * 0.5f, y + d * s); lineTo(x + s * 0.5f, y + d * s); close() },
        color = C.DimLine, style = Fill)
}

private fun DrawScope.drawSpiralCircle(cx: Float, cy: Float, radius: Float, turns: Int) {
    val path = Path(); val total = turns * 36
    for (i in 0..total) {
        val t = i.toFloat() / total
        val a = t * turns * 2 * kotlin.math.PI.toFloat()
        val r = radius * (0.85f + 0.15f * t)
        if (i == 0) path.moveTo(cx + r * cos(a), cy + r * sin(a))
        else path.lineTo(cx + r * cos(a), cy + r * sin(a))
    }
    drawPath(path, color = C.Tie, style = Stroke(2f))
}

private fun DrawScope.drawSpiralRect(left: Float, top: Float, w: Float, h: Float, turns: Int) {
    val cx = left + w / 2f; val cy = top + h / 2f
    val path = Path(); val total = turns * 36
    for (i in 0..total) {
        val t = i.toFloat() / total
        val a = t * turns * 2 * kotlin.math.PI.toFloat()
        val ca = cos(a); val sa = sin(a)
        val n = 4.0
        val r = 1.0 / ((ca.toDouble().pow(2.0)).pow(n / 2.0) +
                (sa.toDouble().pow(2.0)).pow(n / 2.0)).pow(1.0 / n)
        val x = cx + (w / 2f) * r.toFloat() * ca; val y = cy + (h / 2f) * r.toFloat() * sa
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = C.Tie, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f))))
}

private fun isInsideCurve(point: Pair<Double, Double>, curve: List<Pair<Double, Double>>): Boolean {
    if (curve.size < 3) return false
    val (px, py) = point; var inside = false; var j = curve.size - 1
    for (i in curve.indices) {
        val (xi, yi) = curve[i]; val (xj, yj) = curve[j]
        if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) inside = !inside
        j = i
    }
    return inside
}