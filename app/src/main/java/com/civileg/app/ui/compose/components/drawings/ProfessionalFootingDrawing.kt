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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Professional Footing Engineering Drawing
 * Renders plan view, section view, punching shear perimeter,
 * soil pressure diagram, and reinforcement table.
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
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(680.dp)
    ) {
        val w = size.width
        val h = size.height

        // ── Color Palette ──────────────────────────────────────────
        val concreteFill = Color(0xFFD6D6D6)
        val concreteStroke = Color(0xFF555555)
        val footingBorder = Color(0xFF777777)
        val barXColor = Color(0xFF4A90D9)       // Blue – X bars
        val barYColor = Color(0xFF7AB3E8)       // Lighter blue – Y bars
        val dimColor = Color(0xFF9B59B6)        // Purple – dimensions
        val textColor = Color(0xFFFFFFFF)
        val headerBg = Color(0xFF2C3E50)
        val columnFill = Color(0xFF555555)
        val columnStroke = Color(0xFF333333)
        val soilColor = Color(0xFF8D6E63)
        val soilHatchColor = Color(0xFF6D4C41)
        val shearPerimColor = Color(0xFFE67E22)
        val pressureColor = Color(0xFFE74C3C)
        val critSectionColor = Color(0xFF2ECC71)

        // ── Text helper ────────────────────────────────────────────
        fun drawText(
            text: String, x: Float, y: Float,
            color: Color = textColor, size: Float = 11f, bold: Boolean = false,
            align: android.graphics.Paint.Align = android.graphics.Paint.Align.CENTER
        ) {
            nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.hashCode()
                    this.textSize = size * density
                    this.isFakeBoldText = bold
                    this.textAlign = align
                }
                drawText(text, x, y, paint)
            }
        }

        // ── Layout zones ───────────────────────────────────────────
        val margin = 30f
        val planH = h * 0.38f
        val sectionH = h * 0.30f
        val pressureH = if (footingType == "Combined" || footingType == "Raft") h * 0.10f else 0f
        val tableH = h * 0.22f
        val planLeft = margin + 50f
        val planRight = w - margin
        val planTop = 50f
        val planBottom = planTop + planH - 20f
        val planW = planRight - planLeft
        val planDrawH = planBottom - planTop

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRoundRect(
            color = headerBg, topLeft = Offset(0f, 0f),
            size = Size(w, 40f), cornerRadius = CornerRadius(0f)
        )
        drawText(
            "FOOTING DETAIL — ${footingType.uppercase()}",
            w / 2f, 27f, textColor, size = 13f, bold = true
        )

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW
        // ══════════════════════════════════════════════════════════
        val scaleX = planW / footingLengthX.toFloat()
        val scaleY = planDrawH / footingLengthY.toFloat()
        val scale = min(scaleX, scaleY) * 0.85f
        val drawLX = footingLengthX * scale
        val drawLY = footingLengthY * scale
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

        // Concrete hatching
        nativeCanvas.save()
        nativeCanvas.clipRect(fLeft, fTop, fRight, fBottom)
        var hx = fLeft - drawLY
        while (hx < fRight) {
            nativeCanvas.drawLine(
                hx, fTop, hx + drawLY, fBottom,
                android.graphics.Paint().apply {
                    color = Color(0xFFBBBBBB).hashCode()
                    strokeWidth = 0.8f * density
                }
            )
            hx += 16f
        }
        nativeCanvas.restore()

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
        // Bar mark for X
        drawText("\u2460", fRight + 16f, fCenterY + 3f, barXColor, 11f, true)

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
        // Bar mark for Y
        drawText("\u2461", fLeft - 16f, fBottom + 14f, barYColor, 11f, true)

        // ── Column(s) ─────────────────────────────────────────────
        val colDrawW = columnWidth * scale
        val colDrawD = columnDepth * scale

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
                // Column cross hatching
                nativeCanvas.save()
                nativeCanvas.clipRect(cL, cT, cL + colDrawW, cT + colDrawD)
                var chx = cL
                while (chx < cL + colDrawW + colDrawD) {
                    nativeCanvas.drawLine(
                        chx, cT, chx - colDrawD, cT + colDrawD,
                        android.graphics.Paint().apply {
                            color = Color(0xFF444444).hashCode()
                            strokeWidth = 0.6f * density
                        }
                    )
                    chx += 5f
                }
                nativeCanvas.restore()

                // ── Punching shear perimeter ───────────────────────
                val d = footingThickness - cover
                val dPx = (d * scale).toFloat()
                val boOffset = dPx / 2f
                val psLeft = cL - boOffset
                val psTop = cT - boOffset
                val psWidth = colDrawW + dPx
                val psHeight = colDrawD + dPx
                drawRect(
                    color = shearPerimColor,
                    topLeft = Offset(psLeft, psTop),
                    size = Size(psWidth, psHeight),
                    style = Stroke(width = 1.5f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f))
                )
                // Bo label
                drawText("Bo", cL + colDrawW / 2f, psTop - 4f, shearPerimColor, 8f)
                // Vc arrows
                val arrowLen = 10f
                val arrowPositions = listOf(
                    Offset(psLeft + 4f, psTop - 2f),
                    Offset(psLeft + psWidth - 4f, psTop - 2f),
                    Offset(psLeft + 4f, psTop + psHeight + 2f),
                    Offset(psLeft + psWidth - 4f, psTop + psHeight + 2f)
                )
                arrowPositions.forEach { pos ->
                    val dirY = if (pos.y < fCenterY) -1f else 1f
                    drawLine(
                        shearPerimColor,
                        Offset(pos.x, pos.y),
                        Offset(pos.x, pos.y + dirY * arrowLen),
                        strokeWidth = 1f
                    )
                    // Arrow head
                    val headY = pos.y + dirY * arrowLen
                    drawLine(
                        shearPerimColor,
                        Offset(pos.x, headY),
                        Offset(pos.x - 2f, headY - dirY * 3f),
                        strokeWidth = 1f
                    )
                    drawLine(
                        shearPerimColor,
                        Offset(pos.x, headY),
                        Offset(pos.x + 2f, headY - dirY * 3f),
                        strokeWidth = 1f
                    )
                }
            }
            "Combined" -> {
                // Two columns
                val c1xNorm = if (footingLengthX > 0) col1X / footingLengthX else 0.3
                val c2xNorm = if (footingLengthX > 0) col2X / footingLengthX else 0.7
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
                }
            }
            "Raft" -> {
                // Multiple columns in a grid
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

        // ── Dimension lines ───────────────────────────────────────
        // Footing B × L
        drawDimensionLineH(fLeft, fTop - 14f, fRight, fTop - 14f, "L=${footingLengthX.toInt()}", dimColor)
        drawDimensionLineV(fLeft - 14f, fTop, fLeft - 14f, fBottom, "B=${footingLengthY.toInt()}", dimColor)

        // Column b × h (for isolated)
        if (footingType == "Isolated") {
            val cL = fCenterX - colDrawW / 2f
            val cT = fCenterY - colDrawD / 2f
            drawDimensionLineH(cL, fBottom + 10f, cL + colDrawW, fBottom + 10f, "b=${columnWidth.toInt()}", Color(0xFFAAAAAA))
            drawDimensionLineV(cL + colDrawW + 10f, cT, cL + colDrawW + 10f, cT + colDrawD, "h=${columnDepth.toInt()}", Color(0xFFAAAAAA))
        }

        // Edge distances (isolated)
        if (footingType == "Isolated") {
            val cL = fCenterX - colDrawW / 2f
            val edgeLeft = ((cL - fLeft) / scale)
            val edgeRight = ((fRight - (cL + colDrawW)) / scale)
            drawText("e₁=${edgeLeft.toInt()}", fLeft + (cL - fLeft) / 2f, fBottom + 10f, Color(0xFF888888), 7f)
            drawText("e₂=${edgeRight.toInt()}", cL + colDrawW + (fRight - cL - colDrawW) / 2f, fBottom + 10f, Color(0xFF888888), 7f)
        }

        // Plan label
        drawText("PLAN", fLeft + 20f, fBottom + 22f, Color(0xFFAAAAAA), 9f, true)

        // Section cut line
        drawLine(
            Color(0xFFE74C3C), Offset(fCenterX, fTop - 20f),
            Offset(fCenterX, fBottom + 6f), strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 4f))
        )
        drawText("A", fCenterX - 12f, fBottom + 18f, Color(0xFFE74C3C), 9f, true)
        drawText("A", fCenterX + 12f, fBottom + 18f, Color(0xFFE74C3C), 9f, true)

        // ══════════════════════════════════════════════════════════
        //  SECTION VIEW
        // ══════════════════════════════════════════════════════════
        val secTop = fBottom + 36f
        val secBottom = secTop + sectionH
        val secLeft = margin + 90f
        val secRight = w - margin

        drawText("SECTION A-A", secLeft - 50f, secTop + 4f, Color(0xFFAAAAAA), 9f, true)

        val maxSecW = secRight - secLeft - 120f
        val secSpanPx = min(drawLX, maxSecW)
        val secScale = secSpanPx / footingLengthX.toFloat()
        val thickPx = (footingThickness * secScale).toFloat().coerceIn(20f, 70f)
        val sLeft = secLeft + (maxSecW - secSpanPx) / 2f
        val sTop = secTop + (sectionH - thickPx) / 2f + 8f
        val sBottom = sTop + thickPx
        val sCenterX = sLeft + secSpanPx / 2f

        // Soil below footing (hatched area)
        val soilDepth = 30f
        drawRect(
            color = soilColor,
            topLeft = Offset(sLeft - 20f, sBottom),
            size = Size(secSpanPx + 40f, soilDepth)
        )
        // Soil hatching
        nativeCanvas.save()
        nativeCanvas.clipRect(sLeft - 20f, sBottom, sLeft + secSpanPx + 20f, sBottom + soilDepth)
        var shx = sLeft - 20f
        while (shx < sLeft + secSpanPx + 20f + soilDepth) {
            nativeCanvas.drawLine(
                shx, sBottom, shx - soilDepth, sBottom + soilDepth,
                android.graphics.Paint().apply {
                    color = soilHatchColor.hashCode()
                    strokeWidth = 1f * density
                }
            )
            shx += 8f
        }
        nativeCanvas.restore()

        // Footing concrete
        drawRect(
            color = concreteFill,
            topLeft = Offset(sLeft, sTop),
            size = Size(secSpanPx, thickPx)
        )

        // Column above
        val colWpx = columnWidth * secScale
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
        // Column hatching
        nativeCanvas.save()
        nativeCanvas.clipRect(colLeft, sTop - colHpx, colLeft + colWpx, sTop)
        var chx2 = colLeft
        while (chx2 < colLeft + colWpx + colHpx) {
            nativeCanvas.drawLine(
                chx2, sTop - colHpx, chx2 - colHpx, sTop,
                android.graphics.Paint().apply {
                    color = Color(0xFF444444).hashCode()
                    strokeWidth = 0.6f * density
                }
            )
            chx2 += 5f
        }
        nativeCanvas.restore()

        // Pedestal (if footing is much wider than column)
        if (footingLengthX > columnWidth * 2.5) {
            val pedW = colWpx * 1.8f
            val pedH = 20f
            drawRect(
                color = Color(0xFF999999),
                topLeft = Offset(sCenterX - pedW / 2f, sTop - pedH),
                size = Size(pedW, pedH)
            )
            drawRect(
                color = concreteStroke,
                topLeft = Offset(sCenterX - pedW / 2f, sTop - pedH),
                size = Size(pedW, pedH),
                style = Stroke(width = 1f)
            )
            drawText("pedestal", sCenterX + pedW / 2f + 4f, sTop - pedH / 2f + 3f, Color(0xFF999999), 7f)
        }

        // Main reinforcement (blue circles at bottom)
        val barR = (rebarXDia * secScale / 2f).coerceIn(2.5f, 5f)
        val barCountSec = if (rebarXCount > 1) rebarXCount.coerceIn(3, 18) else 6
        val barStepSec = (secSpanPx - 16f) / (barCountSec - 1)
        for (i in 0 until barCountSec) {
            val bx = sLeft + 8f + i * barStepSec
            val by = sBottom - 5f
            drawCircle(color = barXColor, radius = barR, center = Offset(bx, by))
        }
        drawText("\u2460", sCenterX, sBottom + 12f, barXColor, 9f, true)

        // Footing border
        drawRect(
            color = footingBorder,
            topLeft = Offset(sLeft, sTop),
            size = Size(secSpanPx, thickPx),
            style = Stroke(width = 2.5f)
        )

        // Cover dimension
        val coverPx = (cover * secScale).toFloat().coerceIn(3f, 12f)
        drawLine(
            Color(0xFF27AE60),
            Offset(sLeft + 20f, sBottom),
            Offset(sLeft + 20f, sBottom - coverPx),
            strokeWidth = 1f
        )
        drawText("c=${cover.toInt()}", sLeft + 38f, sBottom - coverPx / 2f + 3f, Color(0xFF27AE60), 8f)

        // Critical section at d/2 from column face
        val d = footingThickness - cover
        val dPxSec = (d * secScale).toFloat()
        val critOffset = dPxSec / 2f
        drawLine(
            critSectionColor,
            Offset(colLeft + colWpx + critOffset, sTop + 2f),
            Offset(colLeft + colWpx + critOffset, sBottom - 2f),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
        )
        drawText("d/2", colLeft + colWpx + critOffset + 10f, sTop + thickPx / 2f + 3f, critSectionColor, 7f)

        // Thickness dimension
        val dimX = sLeft + secSpanPx + 16f
        drawLine(dimColor, Offset(dimX, sTop), Offset(dimX, sBottom), strokeWidth = 1.2f)
        drawLine(dimColor, Offset(dimX - 4f, sTop), Offset(dimX + 4f, sTop), strokeWidth = 1.2f)
        drawLine(dimColor, Offset(dimX - 4f, sBottom), Offset(dimX + 4f, sBottom), strokeWidth = 1.2f)
        drawText("t=${footingThickness.toInt()}", dimX + 28f, sTop + thickPx / 2f + 3f, dimColor, 8f)

        // ══════════════════════════════════════════════════════════
        //  SOIL PRESSURE DIAGRAM (Combined / Raft)
        // ══════════════════════════════════════════════════════════
        if (footingType == "Combined" || footingType == "Raft") {
            val prTop = secBottom + 10f
            val prBottom = prTop + pressureH
            val prLeft = secLeft
            val prRight = prLeft + secSpanPx

            drawText("SOIL PRESSURE", prLeft, prTop - 4f, Color(0xFFAAAAAA), 9f, true)

            // Trapezoidal pressure: max at one end, min at the other
            val maxP = 250f   // kN/m² placeholder
            val minP = 120f
            val pressureScale = (pressureH - 20f) / maxP
            val maxBarH = maxP * pressureScale
            val minBarH = minP * pressureScale

            // Trapezoid fill
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(prLeft, prBottom)
                lineTo(prLeft, prBottom - maxBarH)
                lineTo(prRight, prBottom - minBarH)
                lineTo(prRight, prBottom)
                close()
            }
            drawPath(path, color = pressureColor.copy(alpha = 0.3f))
            drawPath(path, color = pressureColor, style = Stroke(width = 1.5f))

            // Max / min labels
            drawText("q_max=${maxP.toInt()}", prLeft, prBottom - maxBarH - 4f, pressureColor, 8f)
            drawText("q_min=${minP.toInt()}", prRight, prBottom - minBarH - 4f, pressureColor, 8f)

            // Resultant arrow
            val resultantX = prLeft + secSpanPx * 0.55f
            val resultantH = (maxP + minP) / 2f * pressureScale
            drawLine(
                Color(0xFFF39C12),
                Offset(resultantX, prBottom),
                Offset(resultantX, prBottom - resultantH),
                strokeWidth = 2f
            )
            // Arrow head
            drawLine(
                Color(0xFFF39C12),
                Offset(resultantX, prBottom - resultantH),
                Offset(resultantX - 3f, prBottom - resultantH + 5f),
                strokeWidth = 2f
            )
            drawLine(
                Color(0xFFF39C12),
                Offset(resultantX, prBottom - resultantH),
                Offset(resultantX + 3f, prBottom - resultantH + 5f),
                strokeWidth = 2f
            )
            drawText("R", resultantX, prBottom - resultantH - 6f, Color(0xFFF39C12), 9f, true)
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        val tblTop = if (pressureH > 0) secBottom + pressureH + 18f else secBottom + 14f
        val tblLeft = margin
        val tblRight = w - margin
        val tblWidth = tblRight - tblLeft
        val rowH = 22f
        val headerRowH = 26f
        val colWidths = floatArrayOf(tblWidth * 0.10f, tblWidth * 0.22f, tblWidth * 0.16f, tblWidth * 0.16f, tblWidth * 0.18f, tblWidth * 0.18f)
        var rowY = tblTop

        // Table header
        drawRoundRect(
            color = headerBg, topLeft = Offset(tblLeft, rowY),
            size = Size(tblWidth, headerRowH), cornerRadius = CornerRadius(4f, 4f, 0f, 0f)
        )
        val headers = listOf("Mark", "Direction", "Dia (mm)", "Count", "Spacing (mm)", "Length (mm)")
        var colX = tblLeft
        headers.forEachIndexed { idx, h ->
            drawText(h, colX + colWidths[idx] / 2f, rowY + headerRowH / 2f + 4f, textColor, 9f, true)
            colX += colWidths[idx]
        }
        rowY += headerRowH

        val xSpacing = if (rebarXCount > 1) (footingLengthY / (rebarXCount - 1)).toInt() else footingLengthY.toInt()
        val ySpacing = if (rebarYCount > 1) (footingLengthX / (rebarYCount - 1)).toInt() else footingLengthX.toInt()
        val barLengthX = footingLengthY.toInt()
        val barLengthY = footingLengthX.toInt()

        val tableRowColor = Color(0xFF263238)
        val tableRowAltColor = Color(0xFF1E2A33)

        val rows = listOf(
            listOf("\u2460", "X-bottom", rebarXDia.toInt().toString(), rebarXCount.toString(), xSpacing.toString(), barLengthX.toString()),
            listOf("\u2461", "Y-bottom", rebarYDia.toInt().toString(), rebarYCount.toString(), ySpacing.toString(), barLengthY.toString())
        )

        rows.forEachIndexed { idx, row ->
            val bg = if (idx % 2 == 0) tableRowColor else tableRowAltColor
            drawRect(color = bg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, rowH))
            colX = tblLeft
            row.forEachIndexed { cIdx, cell ->
                val cellColor = when (cIdx) {
                    0 -> barXColor
                    else -> Color(0xFFDDDDDD)
                }
                drawText(cell, colX + colWidths[cIdx] / 2f, rowY + rowH / 2f + 3f, cellColor, 9f)
                colX += colWidths[cIdx]
            }
            rowY += rowH
        }

        // Table border
        drawRect(
            color = Color(0xFF455A64), topLeft = Offset(tblLeft, tblTop),
            size = Size(tblWidth, rowY - tblTop), style = Stroke(width = 1f)
        )
        // Column lines
        cx = tblLeft
        for (i in 0 until colWidths.size - 1) {
            cx += colWidths[i]
            drawLine(Color(0xFF37474F), Offset(cx, tblTop), Offset(cx, rowY), strokeWidth = 0.5f)
        }
    }
}

// ── Horizontal dimension line ───────────────────────────────────
private fun DrawScope.drawDimensionLineH(
    x1: Float, y1: Float, x2: Float, y2: Float, text: String, color: Color
) {
    val tick = 6f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x1, y1 - tick), Offset(x1, y1 + tick), strokeWidth = 1f)
    drawLine(color, Offset(x2, y2 - tick), Offset(x2, y2 + tick), strokeWidth = 1f)
    nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.hashCode()
            textSize = 9f * density
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(text, (x1 + x2) / 2f, y1 - 4f, paint)
    }
}

// ── Vertical dimension line ─────────────────────────────────────
private fun DrawScope.drawDimensionLineV(
    x1: Float, y1: Float, x2: Float, y2: Float, text: String, color: Color
) {
    val tick = 6f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x1 - tick, y1), Offset(x1 + tick, y1), strokeWidth = 1f)
    drawLine(color, Offset(x2 - tick, y2), Offset(x2 + tick, y2), strokeWidth = 1f)
    nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.hashCode()
            textSize = 9f * density
            textAlign = android.graphics.Paint.Align.CENTER
        }
        save()
        rotate(-90f, x1 - 4f, (y1 + y2) / 2f)
        drawText(text, x1 - 4f, (y1 + y2) / 2f + 4f, paint)
        restore()
    }
}