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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Professional Footing Engineering Drawing
 * Renders plan view, section view, punching shear perimeter,
 * soil pressure diagram, reinforcement table, and design value overlay.
 * Supports: Isolated, Combined, Raft
 */
@Composable
fun ProfessionalFootingDrawing(
    footingType: String,
    footingLengthX: Double,
    footingLengthY: Double,
    footingThickness: Double,
    columnWidth: Double,
    columnDepth: Double,
    rebarXDia: Double,
    rebarXCount: Int,
    rebarYDia: Double,
    rebarYCount: Int,
    cover: Double,
    col1X: Double = 0.0,
    col2X: Double = 0.0,
    soilPressureMax: Double = 0.0,
    soilPressureMin: Double = 0.0,
    isArabic: Boolean = false,
    modifier: Modifier = Modifier,
    axialLoad: Double = 0.0,
    punchingShear: Double = 0.0,
    punchingCapacity: Double = 0.0,
    bendingMoment: Double = 0.0,
    netPressure: Double = 0.0,
    isSafe: Boolean = true,
    utilizationRatio: Double = 0.0,
    fcu: Double = 25.0,
    fy: Double = 360.0
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(580.dp)
    ) {
        val w = size.width
        val h = size.height

        // ── Safety checks ─────────────────────────────────────────
        val safeLX = footingLengthX.coerceAtLeast(100.0)
        val safeLY = footingLengthY.coerceAtLeast(100.0)
        val safeThick = footingThickness.coerceAtLeast(50.0)
        val safeColW = columnWidth.coerceAtLeast(50.0)
        val safeColD = columnDepth.coerceAtLeast(50.0)

        // ── Color Palette ─────────────────────────────────────────
        val C = DrawingColors
        val concreteFill = Color(0xFF3D3D3D)
        val concreteStroke = Color(0xFF6B6B6B)
        val footingBorder = Color(0xFF8A8A8A)
        val barXColor = C.RebarBlue
        val barYColor = C.TopRebarBlue
        val dimColor = C.ExtensionGray
        val textColor = C.DimensionWhite
        val headerBg = Color(0x55333333)
        val columnFill = Color(0xFF555555)
        val columnStroke = Color(0xFF333333)
        val soilColor = C.SoilBrown
        val soilHatchColor = C.SoilBrown.copy(alpha = 0.7f)
        val shearPerimColor = C.WarningOrange
        val pressureColor = C.UnsafeRed
        val critSectionColor = C.SafeGreen

        // ── Dark background ───────────────────────────────────────
        drawRect(color = Color(0xFF1A1A2E), size = size)

        // ── Layout zones ──────────────────────────────────────────
        val margin = 20f
        val planH = h * 0.36f
        val sectionTop = planH
        val sectionH = h * 0.28f
        val pressureH = if (footingType == "Combined" || footingType == "Raft") h * 0.08f else 0f
        val planLeft = margin + 50f
        val planRight = w - margin
        val planTop = 44f
        val planBottom = planTop + planH - 16f
        val planW = planRight - planLeft
        val planDrawH = planBottom - planTop

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, 40f))
        drawTextAnnotated(
            "FOOTING DETAIL — ${footingType.uppercase()}",
            w / 2f, 27f, textColor, 13f * density, center = true, bold = true
        )

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW
        // ══════════════════════════════════════════════════════════
        val scaleX = planW / safeLX.toFloat()
        val scaleY = planDrawH / safeLY.toFloat()
        val scale = min(scaleX, scaleY) * 0.85f
        val drawLX = safeLX.toFloat() * scale
        val drawLY = safeLY.toFloat() * scale
        val fLeft = planLeft + (planW - drawLX) / 2f
        val fTop = planTop + (planDrawH - drawLY) / 2f
        val fRight = fLeft + drawLX
        val fBottom = fTop + drawLY
        val fCenterX = fLeft + drawLX / 2f
        val fCenterY = fTop + drawLY / 2f

        // Footing body
        drawRect(
            color = concreteFill,
            topLeft = Offset(fLeft, fTop),
            size = Size(drawLX, drawLY)
        )
        drawHatchPattern(
            fLeft, fTop, drawLX, drawLY,
            spacing = 16f, angleDeg = 45f,
            color = Color(0x55AAAAAA)
        )

        // ── Bottom reinforcement X-direction (blue lines, vertical) ──
        if (rebarXCount > 1) {
            val barAreaLeft = fLeft + (cover * scale).toFloat()
            val barAreaRight = fRight - (cover * scale).toFloat()
            val step = (barAreaRight - barAreaLeft) / (rebarXCount - 1)
            for (i in 0 until rebarXCount) {
                val bx = barAreaLeft + i * step
                drawLine(
                    barXColor,
                    Offset(bx, fTop + (cover * scale).toFloat()),
                    Offset(bx, fBottom - (cover * scale).toFloat()),
                    strokeWidth = 1.8f
                )
            }
        }
        drawTextAnnotated("\u2460", fRight + 16f, fCenterY + 3f, barXColor, 11f * density, bold = true)

        // ── Bottom reinforcement Y-direction (lighter, horizontal) ─
        if (rebarYCount > 1) {
            val barAreaTop = fTop + (cover * scale).toFloat()
            val barAreaBottom = fBottom - (cover * scale).toFloat()
            val step = (barAreaBottom - barAreaTop) / (rebarYCount - 1)
            for (i in 0 until rebarYCount) {
                val by = barAreaTop + i * step
                drawLine(
                    barYColor,
                    Offset(fLeft + (cover * scale).toFloat(), by),
                    Offset(fRight - (cover * scale).toFloat(), by),
                    strokeWidth = 1.0f
                )
            }
        }
        drawTextAnnotated("\u2461", fLeft - 16f, fBottom + 14f, barYColor, 11f * density, bold = true)

        // ── Column(s) ─────────────────────────────────────────────
        val colDrawW = (safeColW * scale).toFloat()
        val colDrawD = (safeColD * scale).toFloat()

        when (footingType) {
            "Isolated" -> {
                val cL = fCenterX - colDrawW / 2f
                val cT = fCenterY - colDrawD / 2f
                drawRect(color = columnFill, topLeft = Offset(cL, cT), size = Size(colDrawW, colDrawD))
                drawRect(
                    color = columnStroke,
                    topLeft = Offset(cL, cT), size = Size(colDrawW, colDrawD),
                    style = Stroke(width = 1.5f)
                )
                drawHatchPattern(cL, cT, colDrawW, colDrawD, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))

                // Punching shear perimeter
                val d = safeThick - cover
                val dPx = (d * scale).toFloat().coerceAtLeast(1f)
                val boOffset = dPx / 2f
                val psLeft = cL - boOffset
                val psTop = cT - boOffset
                val psWidth = colDrawW + dPx
                val psHeight = colDrawD + dPx
                drawRect(
                    color = shearPerimColor,
                    topLeft = Offset(psLeft, psTop),
                    size = Size(psWidth, psHeight),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f)))
                )
                drawTextAnnotated("Bo", cL + colDrawW / 2f, psTop - 4f, shearPerimColor, 8f * density)
                val arrowLen = 10f
                val arrowPositions = listOf(
                    Offset(psLeft + 4f, psTop - 2f),
                    Offset(psLeft + psWidth - 4f, psTop - 2f),
                    Offset(psLeft + 4f, psTop + psHeight + 2f),
                    Offset(psLeft + psWidth - 4f, psTop + psHeight + 2f)
                )
                arrowPositions.forEach { pos ->
                    val dirY = if (pos.y < fCenterY) -1f else 1f
                    drawLine(shearPerimColor, Offset(pos.x, pos.y), Offset(pos.x, pos.y + dirY * arrowLen), strokeWidth = 1f)
                    val headY = pos.y + dirY * arrowLen
                    drawLine(shearPerimColor, Offset(pos.x, headY), Offset(pos.x - 2f, headY - dirY * 3f), strokeWidth = 1f)
                    drawLine(shearPerimColor, Offset(pos.x, headY), Offset(pos.x + 2f, headY - dirY * 3f), strokeWidth = 1f)
                }
            }
            "Combined" -> {
                val c1xNorm = if (safeLX > 0) (col1X / safeLX).toFloat() else 0.3f
                val c2xNorm = if (safeLX > 0) (col2X / safeLX).toFloat() else 0.7f
                val c1L = fLeft + c1xNorm * drawLX - colDrawW / 2f
                val c2L = fLeft + c2xNorm * drawLX - colDrawW / 2f
                for (cL in listOf(c1L, c2L)) {
                    val cT = fCenterY - colDrawD / 2f
                    drawRect(color = columnFill, topLeft = Offset(cL, cT), size = Size(colDrawW, colDrawD))
                    drawRect(
                        color = columnStroke,
                        topLeft = Offset(cL, cT), size = Size(colDrawW, colDrawD),
                        style = Stroke(width = 1.5f)
                    )
                    drawHatchPattern(cL, cT, colDrawW, colDrawD, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))
                }
            }
            "Raft" -> {
                val colsX = 3
                val colsY = 2
                for (ci in 0 until colsX) {
                    for (cj in 0 until colsY) {
                        val cx = fLeft + drawLX * (ci + 1) / (colsX + 1) - colDrawW / 2f
                        val cy = fTop + drawLY * (cj + 1) / (colsY + 1) - colDrawD / 2f
                        drawRect(color = columnFill, topLeft = Offset(cx, cy), size = Size(colDrawW, colDrawD))
                        drawRect(
                            color = columnStroke,
                            topLeft = Offset(cx, cy), size = Size(colDrawW, colDrawD),
                            style = Stroke(width = 1.2f)
                        )
                    }
                }
            }
        }

        // Footing border
        drawRect(
            color = footingBorder,
            topLeft = Offset(fLeft, fTop),
            size = Size(drawLX, drawLY),
            style = Stroke(width = 3f)
        )

        // Dimension lines
        drawHorizontalDimension(fLeft, fRight, fTop, "L=${safeLX.toInt()}", dimColor, 9f * density, offset = -14f)
        drawVerticalDimension(fTop, fBottom, fLeft, "B=${safeLY.toInt()}", dimColor, 9f * density, offset = -14f)

        if (footingType == "Isolated") {
            val cL = fCenterX - colDrawW / 2f
            val cT = fCenterY - colDrawD / 2f
            drawHorizontalDimension(cL, cL + colDrawW, fBottom, "b=${safeColW.toInt()}", C.ExtensionGray, 8f * density, offset = 10f)
            drawVerticalDimension(cT, cT + colDrawD, cL + colDrawW, "h=${safeColD.toInt()}", C.ExtensionGray, 8f * density, offset = 10f)
            val edgeLeft = ((cL - fLeft) / scale).coerceAtLeast(0f)
            val edgeRight = ((fRight - (cL + colDrawW)) / scale).coerceAtLeast(0f)
            drawTextAnnotated("e\u2081=${edgeLeft.toInt()}", fLeft + (cL - fLeft) / 2f, fBottom + 10f, C.ExtensionGray, 7f * density, center = true)
            drawTextAnnotated("e\u2082=${edgeRight.toInt()}", cL + colDrawW + (fRight - cL - colDrawW) / 2f, fBottom + 10f, C.ExtensionGray, 7f * density, center = true)
        }

        drawTextAnnotated("PLAN", fLeft + 20f, fBottom + 22f, C.ExtensionGray, 9f * density, bold = true)
        drawSectionCutLine(
            x1 = fCenterX, y1 = fTop - 20f,
            x2 = fCenterX, y2 = fBottom + 6f,
            label = "A", color = C.SectionLine
        )

        // ══════════════════════════════════════════════════════════
        //  SECTION VIEW
        // ══════════════════════════════════════════════════════════
        val secTopY = sectionTop + 8f
        val secLeft = margin + 90f
        val secRight = w - margin

        drawTextAnnotated("SECTION A-A", secLeft - 50f, secTopY + 4f, C.ExtensionGray, 9f * density, bold = true)

        val maxSecW = secRight - secLeft - 120f
        val secSpanPx = min(drawLX, maxSecW)
        val secScale = secSpanPx / safeLX.toFloat()
        val thickPx = (safeThick * secScale).toFloat().coerceIn(20f, 70f)
        val sLeft = secLeft + (maxSecW - secSpanPx) / 2f
        val sTop = secTopY + (sectionH - thickPx) / 2f + 8f
        val sBottom = sTop + thickPx
        val sCenterX = sLeft + secSpanPx / 2f

        // Soil below footing
        val soilDepth = 30f
        drawRect(
            color = soilColor.copy(alpha = 0.4f),
            topLeft = Offset(sLeft - 20f, sBottom),
            size = Size(secSpanPx + 40f, soilDepth)
        )
        drawHatchPattern(
            sLeft - 20f, sBottom, secSpanPx + 40f, soilDepth,
            spacing = 8f, angleDeg = -45f, color = soilHatchColor
        )

        // Footing concrete
        drawRect(
            color = concreteFill,
            topLeft = Offset(sLeft, sTop),
            size = Size(secSpanPx, thickPx)
        )

        // Column above
        val colWpx = (safeColW * secScale).toFloat()
        val colHpx = 40f
        val colLeft = sCenterX - colWpx / 2f
        drawRect(
            color = columnFill,
            topLeft = Offset(colLeft, sTop - colHpx),
            size = Size(colWpx, colHpx)
        )
        drawRect(
            color = columnStroke,
            topLeft = Offset(colLeft, sTop - colHpx),
            size = Size(colWpx, colHpx),
            style = Stroke(width = 1.5f)
        )
        drawHatchPattern(colLeft, sTop - colHpx, colWpx, colHpx, spacing = 5f, angleDeg = -45f, color = Color(0x66666666))

        // Pedestal
        if (safeLX > safeColW * 2.5) {
            val pedW = colWpx * 1.8f
            val pedH = 20f
            drawRect(color = Color(0xFF999999), topLeft = Offset(sCenterX - pedW / 2f, sTop - pedH), size = Size(pedW, pedH))
            drawRect(color = concreteStroke, topLeft = Offset(sCenterX - pedW / 2f, sTop - pedH), size = Size(pedW, pedH), style = Stroke(width = 1f))
        }

        // Main reinforcement (bottom rebar circles)
        val barCountSec = if (rebarXCount > 1) rebarXCount.coerceIn(3, 18) else 6
        val barStepSec = (secSpanPx - 16f) / (barCountSec - 1)
        for (i in 0 until barCountSec) {
            val bx = sLeft + 8f + i * barStepSec
            val by = sBottom - 5f
            drawRebarCircle(bx, by, rebarXDia.toFloat(), secScale / 2f, barXColor)
        }
        drawTextAnnotated("\u2460", sCenterX, sBottom + 12f, barXColor, 9f * density, center = true, bold = true)

        // Footing border
        drawRect(
            color = footingBorder,
            topLeft = Offset(sLeft, sTop),
            size = Size(secSpanPx, thickPx),
            style = Stroke(width = 2.5f)
        )

        // Cover dimension
        val coverPx = (cover * secScale).toFloat().coerceIn(3f, 12f)
        drawLine(C.SafeGreen, Offset(sLeft + 20f, sBottom), Offset(sLeft + 20f, sBottom - coverPx), strokeWidth = 1f)
        drawTextAnnotated("c=${cover.toInt()}", sLeft + 38f, sBottom - coverPx / 2f + 3f, C.SafeGreen, 8f * density)

        // Critical section at d/2 from column face
        val d = safeThick - cover
        val dPxSec = (d * secScale).toFloat().coerceAtLeast(1f)
        val critOffset = dPxSec / 2f
        drawLine(
            critSectionColor,
            Offset(colLeft + colWpx + critOffset, sTop + 2f),
            Offset(colLeft + colWpx + critOffset, sBottom - 2f),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
        )
        drawTextAnnotated("d/2", colLeft + colWpx + critOffset + 10f, sTop + thickPx / 2f + 3f, critSectionColor, 7f * density)

        // Thickness dimension
        drawVerticalDimension(sTop, sBottom, sLeft + secSpanPx, "t=${safeThick.toInt()}", dimColor, 8f * density, offset = 16f)

        // ══════════════════════════════════════════════════════════
        //  SOIL PRESSURE DIAGRAM (Combined / Raft)
        // ══════════════════════════════════════════════════════════
        var prBottom = sBottom + soilDepth + 4f
        if (footingType == "Combined" || footingType == "Raft") {
            val prTopY = prBottom
            prBottom = prTopY + pressureH
            val prLeft = secLeft
            val prRight = prLeft + secSpanPx

            drawTextAnnotated("SOIL PRESSURE", prLeft, prTopY - 4f, C.ExtensionGray, 9f * density, bold = true)

            val maxP = if (soilPressureMax > 0) soilPressureMax.toFloat() else 250f
            val minP = if (soilPressureMin > 0) soilPressureMin.toFloat() else 120f
            val pressureScaleVal = if (maxP > 0) (pressureH - 20f) / maxP else 0f
            val maxBarH = maxP * pressureScaleVal
            val minBarH = minP * pressureScaleVal

            val path = Path().apply {
                moveTo(prLeft, prBottom)
                lineTo(prLeft, prBottom - maxBarH)
                lineTo(prRight, prBottom - minBarH)
                lineTo(prRight, prBottom)
                close()
            }
            drawPath(path, color = pressureColor.copy(alpha = 0.3f))
            drawPath(path, color = pressureColor, style = Stroke(width = 1.5f))

            drawTextAnnotated("q_max=${maxP.toInt()}", prLeft, prBottom - maxBarH - 4f, pressureColor, 8f * density)
            drawTextAnnotated("q_min=${minP.toInt()}", prRight, prBottom - minBarH - 4f, pressureColor, 8f * density, center = true)

            val resultantX = prLeft + secSpanPx * 0.55f
            val resultantH = (maxP + minP) / 2f * pressureScaleVal
            drawLine(C.WarningOrange, Offset(resultantX, prBottom), Offset(resultantX, prBottom - resultantH), strokeWidth = 2f)
            drawLine(C.WarningOrange, Offset(resultantX, prBottom - resultantH), Offset(resultantX - 3f, prBottom - resultantH + 5f), strokeWidth = 2f)
            drawLine(C.WarningOrange, Offset(resultantX, prBottom - resultantH), Offset(resultantX + 3f, prBottom - resultantH + 5f), strokeWidth = 2f)
            drawTextAnnotated("R", resultantX, prBottom - resultantH - 6f, C.WarningOrange, 9f * density, center = true, bold = true)
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        val tblTop = prBottom + 10f
        val tblLeft = margin
        val tblWidth = w - 2 * margin

        val xSpacing = if (rebarXCount > 1) (safeLY / (rebarXCount - 1)).toInt() else safeLY.toInt()
        val ySpacing = if (rebarYCount > 1) (safeLX / (rebarYCount - 1)).toInt() else safeLX.toInt()
        val barLengthX = safeLY.toInt()
        val barLengthY = safeLX.toInt()

        val headers = listOf("Mark", "Direction", "Dia (mm)", "Count", "Spacing (mm)", "Length (mm)")
        val colWidths = listOf(
            tblWidth * 0.10f, tblWidth * 0.22f, tblWidth * 0.16f,
            tblWidth * 0.16f, tblWidth * 0.18f, tblWidth * 0.18f
        )
        val rows = listOf(
            listOf("\u2460", "X-bottom", rebarXDia.toInt().toString(), rebarXCount.toString(), xSpacing.toString(), barLengthX.toString()),
            listOf("\u2461", "Y-bottom", rebarYDia.toInt().toString(), rebarYCount.toString(), ySpacing.toString(), barLengthY.toString())
        )

        drawReinforcementTable(
            x = tblLeft, y = tblTop,
            colWidths = colWidths,
            headers = headers,
            rows = rows,
            rowHeight = 22f,
            headerHeight = 26f,
            headerBg = headerBg,
            altRowBg = Color(0x1AFFFFFF),
            textColor = textColor,
            textSize = 9f * density
        )

        // ══════════════════════════════════════════════════════════
        //  OVERLAY BOX (top-right): Pu, Vu, Vc, UR, net σ
        // ══════════════════════════════════════════════════════════
        val overlayRows = mutableListOf<List<String>>()
        if (axialLoad > 0) overlayRows.add(listOf("Pu", "${"%.0f".format(axialLoad)} kN"))
        if (punchingShear > 0) overlayRows.add(listOf("Vu", "${"%.1f".format(punchingShear)} kN"))
        if (punchingCapacity > 0) overlayRows.add(listOf("Vc", "${"%.1f".format(punchingCapacity)} kN"))
        if (utilizationRatio > 0) overlayRows.add(listOf("UR", "${"%.2f".format(utilizationRatio)}"))
        if (netPressure > 0) overlayRows.add(listOf("net \u03C3", "${"%.1f".format(netPressure)} kPa"))

        if (overlayRows.isNotEmpty()) {
            val ovW = 140f
            val ovH = 28f + overlayRows.size * 22f
            val ovX = w - ovW - 10f
            val ovY = 44f

            drawRoundRect(
                color = Color(0xCC222244),
                topLeft = Offset(ovX, ovY),
                size = Size(ovW, ovH),
                cornerRadius = CornerRadius(8f)
            )
            drawRoundRect(
                color = Color(0x66AAAAAA),
                topLeft = Offset(ovX, ovY),
                size = Size(ovW, ovH),
                cornerRadius = CornerRadius(8f),
                style = Stroke(1f)
            )

            drawTextAnnotated("Design Check", ovX + ovW / 2f, ovY + 14f, textColor, 11f * density, center = true, bold = true)
            overlayRows.forEachIndexed { idx, row ->
                val ry = ovY + 28f + idx * 22f
                drawTextAnnotated(row[0], ovX + 8f, ry + 10f, C.ExtensionGray, 10f * density)
                val valColor = when {
                    row[0] == "UR" && utilizationRatio > 1.0 -> C.UnsafeRed
                    row[0] == "Vu" && punchingCapacity > 0 && punchingShear > punchingCapacity -> C.UnsafeRed
                    else -> textColor
                }
                drawTextAnnotated(row[1], ovX + ovW - 8f, ry + 10f, valColor, 10f * density, center = true)
            }
        }

        // ══════════════════════════════════════════════════════════
        //  SAFE / UNSAFE BADGE
        // ══════════════════════════════════════════════════════════
        val badgeColor = if (isSafe) C.SafeGreen else C.UnsafeRed
        val badgeLabel = if (isSafe) "SAFE" else "UNSAFE"
        val badgeW = 72f
        val badgeH = 24f
        val badgeX = w - badgeW - 10f
        val badgeY = if (overlayRows.isEmpty()) 44f else 44f + 28f + overlayRows.size * 22f + 8f

        drawRoundRect(
            color = badgeColor.copy(alpha = 0.25f),
            topLeft = Offset(badgeX, badgeY),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(12f)
        )
        drawRoundRect(
            color = badgeColor,
            topLeft = Offset(badgeX, badgeY),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(12f),
            style = Stroke(1.5f)
        )
        val bp = android.graphics.Paint().apply {
            color = badgeColor.toArgb()
            this.textSize = 13f * density
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText(badgeLabel, badgeX + badgeW / 2f, badgeY + badgeH / 2f + 5f * density, bp)

        // Title block
        drawTitleBlock(
            x = w - 180f, y = h - 48f,
            width = 175f, height = 44f,
            drawingTitle = "Footing - ${footingType}"
        )
    }
}
