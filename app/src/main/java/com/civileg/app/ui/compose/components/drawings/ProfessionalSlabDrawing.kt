package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Professional Slab Engineering Drawing
 * Renders plan view, section view, and reinforcement table for various slab types.
 * Supports: OneWay, TwoWay, FlatPlate, Hordi, Waffle, Cantilever
 */
@Composable
fun ProfessionalSlabDrawing(
    slabType: String,
    slabThickness: Double,
    spanX: Double,
    spanY: Double,
    mainRebarDia: Double,
    mainRebarSpacing: Double,
    distRebarDia: Double,
    distRebarSpacing: Double,
    cover: Double,
    dropPanelSize: Double = 0.0,
    ribWidth: Double = 0.0,
    ribSpacing: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(620.dp)
    ) {
        val w = size.width
        val h = size.height

        // ── Color Palette ──────────────────────────────────────────
        val concreteFill = Color(0xFFD6D6D6)
        val concreteStroke = Color(0xFF555555)
        val mainBarColor = Color(0xFF4A90D9)
        val distBarColor = Color(0xFF7AB3E8)
        val topBarColor = Color(0xFFE74C3C)
        val dimColor = Color(0xFF9B59B6)
        val textColor = Color(0xFFFFFFFF)
        val headerBg = Color(0xFF2C3E50)
        val columnFill = Color(0xFF666666)
        val ribFill = Color(0xFFBDBDBD)
        val blockFill = Color(0xFFF0E68C)
        val dropFill = Color(0xFFB0BEC5)
        val shearPerimColor = Color(0xFFE67E22)
        val stripColor = Color(0xFF95A5A6)

        // ── Paint helpers via nativeCanvas ─────────────────────────
        fun drawText(
            text: String, x: Float, y: Float,
            color: Color = textColor, size: Float = 11f, bold: Boolean = false
        ) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.hashCode()
                    this.textSize = size * density
                    this.isFakeBoldText = bold
                    this.textAlign = android.graphics.Paint.Align.CENTER
                }
                this.drawText(text, x, y, paint)
            }
        }

        fun drawTextLeft(
            text: String, x: Float, y: Float,
            color: Color = textColor, size: Float = 11f, bold: Boolean = false
        ) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.hashCode()
                    this.textSize = size * density
                    this.isFakeBoldText = bold
                    this.textAlign = android.graphics.Paint.Align.LEFT
                }
                this.drawText(text, x, y, paint)
            }
        }

        // ── Layout zones ───────────────────────────────────────────
        val planH = h * 0.42f
        val sectionH = h * 0.28f
        val tableH = h * 0.30f
        val margin = 35f
        val planLeft = margin + 40f
        val planRight = w - margin
        val planTop = 50f
        val planBottom = planTop + planH - 20f
        val planW = planRight - planLeft
        val planDrawH = planBottom - planTop

        // ── Scaling ────────────────────────────────────────────────
        val maxSpan = maxOf(spanX, spanY)
        val scaleX = planW / spanX.toFloat()
        val scaleY = planDrawH / spanY.toFloat()
        val scale = min(scaleX, scaleY) * 0.88f
        val drawSpanX = spanX.toFloat() * scale
        val drawSpanY = spanY.toFloat() * scale
        val slabLeft = planLeft + (planW - drawSpanX) / 2f
        val slabTop = planTop + (planDrawH - drawSpanY) / 2f
        val slabRight = slabLeft + drawSpanX
        val slabBottom = slabTop + drawSpanY

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRoundRect(
            color = headerBg, topLeft = Offset(0f, 0f),
            size = Size(w, 40f), cornerRadius = CornerRadius(0f)
        )
        drawText(
            "SLAB DETAIL — ${slabType.uppercase()}",
            w / 2f, 27f, textColor, size = 13f, bold = true
        )

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW
        // ══════════════════════════════════════════════════════════
        drawRoundRect(
            color = Color(0xFF1A252F), topLeft = Offset(slabLeft - 5f, slabTop - 5f),
            size = Size(drawSpanX + 10f, drawSpanY + 10f), cornerRadius = CornerRadius(2f)
        )
        drawRect(
            color = concreteFill,
            topLeft = Offset(slabLeft, slabTop),
            size = Size(drawSpanX, drawSpanY)
        )

        // Concrete hatching
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.clipRect(
            slabLeft, slabTop, slabRight, slabBottom
        )
        val hatchStep = 18f
        var hx = slabLeft - drawSpanY
        while (hx < slabRight) {
            drawContext.canvas.nativeCanvas.drawLine(
                hx, slabTop, hx + drawSpanY, slabBottom,
                android.graphics.Paint().apply {
                    color = Color(0xFFBBBBBB).hashCode()
                    strokeWidth = 0.8f * density
                }
            )
            hx += hatchStep
        }
        drawContext.canvas.nativeCanvas.restore()

        // ── Hordi / Waffle ribs & blocks ───────────────────────────
        if (slabType == "Hordi" || slabType == "Waffle") {
            val rs = if (ribSpacing > 0) ribSpacing else 500.0
            val rw = if (ribWidth > 0) ribWidth else 100.0
            val ribStepPx = (rs * scale).toFloat()
            val ribWPx = (rw * scale).toFloat()
            // One-directional ribs for Hordi, two-directional for Waffle
            var rx = slabLeft + ribStepPx
            while (rx < slabRight) {
                drawRect(
                    color = ribFill,
                    topLeft = Offset(rx - ribWPx / 2f, slabTop),
                    size = Size(ribWPx, drawSpanY)
                )
                rx += ribStepPx
            }
            if (slabType == "Waffle") {
                var ry = slabTop + ribStepPx
                while (ry < slabBottom) {
                    drawRect(
                        color = ribFill,
                        topLeft = Offset(slabLeft, ry - ribWPx / 2f),
                        size = Size(drawSpanX, ribWPx)
                    )
                    ry += ribStepPx
                }
                // Fill block areas
                var bx = slabLeft
                while (bx < slabRight) {
                    var by = slabTop
                    while (by < slabBottom) {
                        drawRect(
                            color = blockFill,
                            topLeft = Offset(bx + 1f, by + 1f),
                            size = Size(ribStepPx - ribWPx - 2f, ribStepPx - ribWPx - 2f)
                        )
                        by += ribStepPx
                    }
                    bx += ribStepPx
                }
            }
        }

        // ── Flat Plate: column strip / middle strip / drop panel ──
        if (slabType == "FlatPlate") {
            val stripW = drawSpanY / 6f
            // Column strips (top/bottom)
            drawRect(
                color = Color(0xFFCFD8DC),
                topLeft = Offset(slabLeft, slabTop),
                size = Size(drawSpanX, stripW),
                alpha = 0.35f
            )
            drawRect(
                color = Color(0xFFCFD8DC),
                topLeft = Offset(slabLeft, slabBottom - stripW),
                size = Size(drawSpanX, stripW),
                alpha = 0.35f
            )
            // Strip boundaries
            drawLine(
                stripColor, Offset(slabLeft, slabTop + stripW),
                Offset(slabRight, slabTop + stripW),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
            )
            drawLine(
                stripColor, Offset(slabLeft, slabBottom - stripW),
                Offset(slabRight, slabBottom - stripW),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
            )
            // Drop panel
            if (dropPanelSize > 0) {
                val dpPx = (dropPanelSize * scale).toFloat()
                val dpL = (drawSpanX - dpPx) / 2f + slabLeft
                val dpT = (drawSpanY - dpPx) / 2f + slabTop
                drawRect(
                    color = dropFill,
                    topLeft = Offset(dpL, dpT),
                    size = Size(dpPx, dpPx)
                )
                drawRect(
                    color = concreteStroke,
                    topLeft = Offset(dpL, dpT),
                    size = Size(dpPx, dpPx),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // ── Main reinforcement (parallel blue lines) ───────────────
        val mainStepPx = (mainRebarSpacing * scale).toFloat().coerceAtLeast(8f)
        var my = slabTop + mainStepPx
        while (my < slabBottom - mainStepPx / 2f) {
            drawLine(
                mainBarColor, Offset(slabLeft + 4f, my),
                Offset(slabRight - 4f, my), strokeWidth = 1.8f
            )
            my += mainStepPx
        }
        // Bar mark ①
        drawText("\u2460", slabRight + 14f, slabTop + drawSpanY * 0.3f, mainBarColor, 11f, true)

        // ── Distribution reinforcement (lighter, perpendicular) ────
        val distStepPx = (distRebarSpacing * scale).toFloat().coerceAtLeast(8f)
        var dx = slabLeft + distStepPx
        while (dx < slabRight - distStepPx / 2f) {
            drawLine(
                distBarColor, Offset(dx, slabTop + 4f),
                Offset(dx, slabBottom - 4f), strokeWidth = 1.0f
            )
            dx += distStepPx
        }
        // Bar mark ②
        drawText("\u2461", slabLeft - 14f, slabBottom + 14f, distBarColor, 11f, true)

        // ── Column / Wall supports ─────────────────────────────────
        val colSize = 28f
        val isCantilever = slabType == "Cantilever"
        val isOneWay = slabType == "OneWay"
        val supports = mutableListOf<Offset>()

        when {
            slabType == "Cantilever" -> {
                // Fixed support at left, free at right
                val wallLeft = slabLeft - 10f
                drawRect(
                    color = columnFill,
                    topLeft = Offset(wallLeft, slabTop - 6f),
                    size = Size(14f, drawSpanY + 12f)
                )
                // Hatching for wall
                var whx = wallLeft - 4f
                while (whx < wallLeft + 14f) {
                    drawLine(
                        Color(0xFF444444), Offset(whx, slabTop - 6f),
                        Offset(whx - 8f, slabTop + 2f), strokeWidth = 1f
                    )
                    whx += 6f
                }
                supports.add(Offset(wallLeft + 7f, slabTop + drawSpanY / 2f))
            }
            slabType == "OneWay" -> {
                // Walls on left & right
                for (side in listOf(slabLeft, slabRight - 14f)) {
                    drawRect(
                        color = columnFill,
                        topLeft = Offset(side - if (side == slabLeft) 10f else 0f, slabTop - 6f),
                        size = Size(14f, drawSpanY + 12f)
                    )
                    supports.add(Offset(side + if (side == slabLeft) -3f else 7f, slabTop + drawSpanY / 2f))
                }
            }
            else -> {
                // Columns at corners
                val corners = listOf(
                    Offset(slabLeft, slabTop), Offset(slabRight - colSize, slabTop),
                    Offset(slabLeft, slabBottom - colSize), Offset(slabRight - colSize, slabBottom - colSize)
                )
                corners.forEach {
                    drawRect(color = columnFill, topLeft = it, size = Size(colSize, colSize))
                }
                // Centre columns for TwoWay/FlatPlate
                if (slabType == "TwoWay" || slabType == "FlatPlate") {
                    val cx = slabLeft + drawSpanX / 2f - colSize / 2f
                    val cy = slabTop + drawSpanY / 2f - colSize / 2f
                    drawRect(color = columnFill, topLeft = Offset(cx, cy), size = Size(colSize, colSize))
                    // Punching shear perimeter for FlatPlate
                    if (slabType == "FlatPlate") {
                        val d = slabThickness - cover
                        val dPx = (d * scale).toFloat().coerceAtLeast(colSize * 0.6f)
                        val psSize = colSize + dPx
                        drawRect(
                            color = shearPerimColor,
                            topLeft = Offset(cx + colSize / 2f - psSize / 2f, cy + colSize / 2f - psSize / 2f),
                            size = Size(psSize, psSize),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f)))
                        )
                        drawText("Bo", cx + colSize / 2f, cy - 6f, shearPerimColor, 9f)
                    }
                }
            }
        }

        // ── Slab border ───────────────────────────────────────────
        drawRect(
            color = concreteStroke,
            topLeft = Offset(slabLeft, slabTop),
            size = Size(drawSpanX, drawSpanY),
            style = Stroke(width = 2.5f)
        )

        // ── Dimension lines ───────────────────────────────────────
        drawDimensionLine(
            slabLeft, slabTop - 14f, slabRight, slabTop - 14f,
            "${spanX.toInt()} mm", dimColor
        )
        drawDimensionLineV(
            slabLeft - 14f, slabTop, slabLeft - 14f, slabBottom,
            "${spanY.toInt()} mm", dimColor
        )

        // Plan label
        drawText("PLAN", slabLeft + 20f, slabBottom + 16f, Color(0xFFAAAAAA), 9f, true)

        // ══════════════════════════════════════════════════════════
        //  SECTION VIEW
        // ══════════════════════════════════════════════════════════
        val secTop = planBottom + 32f
        val secBottom = secTop + sectionH - 20f
        val secLeft = margin + 80f
        val secRight = w - margin

        // Section label
        drawText("SECTION A-A", secLeft, secTop - 6f, Color(0xFFAAAAAA), 9f, true)

        // Scale section: slab thickness exaggerated for visibility
        val maxSectionW = secRight - secLeft - 100f
        val sectionSpanPx = min(drawSpanX, maxSectionW)
        val sectionScale = sectionSpanPx / spanX.toFloat()
        val thickPx = (slabThickness * sectionScale).toFloat().coerceIn(18f, 60f)
        val sSlabLeft = secLeft + (maxSectionW - sectionSpanPx) / 2f
        val sSlabTop = secTop + (sectionH - thickPx) / 2f + 10f
        val sSlabBottom = sSlabTop + thickPx

        // Hordi/Waffle: show rib profile
        if (slabType == "Hordi" || slabType == "Waffle") {
            val toppingH = (slabThickness * 0.3 * sectionScale).toFloat().coerceAtLeast(10f)
            val ribH = thickPx - toppingH
            val ribWPx = (ribWidth * sectionScale).toFloat().coerceAtLeast(6f)
            val ribSPx = (ribSpacing * sectionScale).toFloat().coerceAtLeast(20f)
            // Draw topping slab
            drawRect(
                color = concreteFill,
                topLeft = Offset(sSlabLeft, sSlabTop),
                size = Size(sectionSpanPx, toppingH)
            )
            // Draw ribs below topping
            var rrx = sSlabLeft + ribSPx
            while (rrx < sSlabLeft + sectionSpanPx) {
                drawRect(
                    color = ribFill,
                    topLeft = Offset(rrx - ribWPx / 2f, sSlabTop + toppingH),
                    size = Size(ribWPx, ribH)
                )
                drawRect(
                    color = concreteStroke,
                    topLeft = Offset(rrx - ribWPx / 2f, sSlabTop + toppingH),
                    size = Size(ribWPx, ribH),
                    style = Stroke(width = 1f)
                )
                // Rebar circle in rib
                drawCircle(
                    color = mainBarColor,
                    radius = 3.5f,
                    center = Offset(rrx, sSlabTop + toppingH + ribH - 6f)
                )
                rrx += ribSPx
            }
            // Topping label
            drawText("topping", sSlabLeft + sectionSpanPx + 8f, sSlabTop + toppingH / 2f + 3f, Color(0xFFAAAAAA), 8f)
        } else {
            // Standard slab section
            drawRect(
                color = concreteFill,
                topLeft = Offset(sSlabLeft, sSlabTop),
                size = Size(sectionSpanPx, thickPx)
            )

            // Main bottom steel (blue circles)
            val barR = (mainRebarDia * sectionScale / 2f).toFloat().coerceIn(2.5f, 5f)
            val barCount = ((sectionSpanPx - 20f) / (mainRebarSpacing * sectionScale).toFloat())
                .toInt().coerceIn(3, 20)
            val barStep = (sectionSpanPx - 20f) / (barCount - 1)
            for (i in 0 until barCount) {
                val bx = sSlabLeft + 10f + i * barStep
                val by = sSlabBottom - 5f
                drawCircle(color = mainBarColor, radius = barR, center = Offset(bx, by))
            }

            // Top steel at supports (red circles) - for continuous slabs
            if (slabType != "Cantilever") {
                val topBarR = barR * 0.9f
                val topCount = min(barCount, 6)
                val topBarStep = (sectionSpanPx * 0.3f) / (topCount - 1).coerceAtLeast(1)
                for (i in 0 until topCount) {
                    val bx = sSlabLeft + 8f + i * topBarStep
                    val by = sSlabTop + 5f
                    drawCircle(color = topBarColor, radius = topBarR, center = Offset(bx, by))
                }
                // Right support top bars
                for (i in 0 until topCount) {
                    val bx = sSlabLeft + sectionSpanPx - 8f - i * topBarStep
                    val by = sSlabTop + 5f
                    drawCircle(color = topBarColor, radius = topBarR, center = Offset(bx, by))
                }
            } else {
                // Cantilever: top steel full length
                val topCount = barCount
                val topStep = (sectionSpanPx - 20f) / (topCount - 1)
                for (i in 0 until topCount) {
                    val bx = sSlabLeft + 10f + i * topStep
                    val by = sSlabTop + 5f
                    drawCircle(color = topBarColor, radius = barR, center = Offset(bx, by))
                }
            }
        }

        // Slab border in section
        drawRect(
            color = concreteStroke,
            topLeft = Offset(sSlabLeft, sSlabTop),
            size = Size(sectionSpanPx, thickPx),
            style = Stroke(width = 2f)
        )

        // Supports in section
        when {
            slabType == "Cantilever" -> {
                // Wall at left
                drawRect(
                    color = columnFill,
                    topLeft = Offset(sSlabLeft - 12f, sSlabTop - 8f),
                    size = Size(14f, thickPx + 16f)
                )
                // Ground hatching
                for (i in 0..5) {
                    val lx = sSlabLeft - 14f + i * 5f
                    drawLine(
                        Color(0xFF444444), Offset(lx, sSlabBottom + 10f),
                        Offset(lx - 6f, sSlabBottom + 18f), strokeWidth = 1f
                    )
                }
            }
            slabType == "OneWay" -> {
                // Walls at ends
                for (wx in listOf(sSlabLeft - 12f, sSlabLeft + sectionSpanPx - 2f)) {
                    drawRect(
                        color = columnFill,
                        topLeft = Offset(wx, sSlabTop - 8f),
                        size = Size(14f, thickPx + 16f)
                    )
                }
            }
            else -> {
                // Columns at ends
                val colW = 22f
                val colH = thickPx + 40f
                for (cx in listOf(sSlabLeft - colW / 2f, sSlabLeft + sectionSpanPx - colW / 2f)) {
                    drawRect(
                        color = columnFill,
                        topLeft = Offset(cx, sSlabBottom),
                        size = Size(colW, colH)
                    )
                    drawRect(
                        color = concreteStroke,
                        topLeft = Offset(cx, sSlabBottom),
                        size = Size(colW, colH),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }

        // Cover dimension
        val coverPx = (cover * sectionScale).toFloat().coerceIn(4f, 15f)
        drawLine(
            Color(0xFF27AE60), Offset(sSlabLeft + 20f, sSlabBottom),
            Offset(sSlabLeft + 20f, sSlabBottom - coverPx), strokeWidth = 1f
        )
        drawText("c=${cover.toInt()}", sSlabLeft + 44f, sSlabBottom - coverPx / 2f + 3f, Color(0xFF27AE60), 8f)

        // Thickness dimension
        val dimX = sSlabLeft + sectionSpanPx + 14f
        drawLine(dimColor, Offset(dimX, sSlabTop), Offset(dimX, sSlabBottom), strokeWidth = 1.2f)
        drawLine(dimColor, Offset(dimX - 4f, sSlabTop), Offset(dimX + 4f, sSlabTop), strokeWidth = 1.2f)
        drawLine(dimColor, Offset(dimX - 4f, sSlabBottom), Offset(dimX + 4f, sSlabBottom), strokeWidth = 1.2f)
        drawText("t=${slabThickness.toInt()}", dimX + 24f, sSlabTop + thickPx / 2f + 3f, dimColor, 8f)

        // Bar labels in section
        drawText("\u2460", sSlabLeft + sectionSpanPx / 2f, sSlabBottom + 14f, mainBarColor, 9f, true)
        if (slabType != "OneWay") {
            drawText("\u2462", sSlabLeft + 14f, sSlabTop - 5f, topBarColor, 9f, true)
        }

        // Section label indicator
        drawText("A", slabLeft - 24f, slabTop + drawSpanY / 2f + 4f, Color(0xFFE74C3C), 10f, true)
        drawText("A", slabRight + 24f, slabTop + drawSpanY / 2f + 4f, Color(0xFFE74C3C), 10f, true)

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        val tblTop = secBottom + 16f
        val tblLeft = margin
        val tblRight = w - margin
        val tblWidth = tblRight - tblLeft
        val rowH = 22f
        val headerRowH = 26f
        val colWidths = floatArrayOf(tblWidth * 0.10f, tblWidth * 0.22f, tblWidth * 0.18f, tblWidth * 0.22f, tblWidth * 0.28f)
        var rowY = tblTop

        // Table header bg
        drawRoundRect(
            color = headerBg, topLeft = Offset(tblLeft, rowY),
            size = Size(tblWidth, headerRowH), cornerRadius = CornerRadius(4f)
        )
        val headers = listOf("Mark", "Direction", "Dia (mm)", "Spacing (mm)", "Length (mm)")
        var colX = tblLeft
        headers.forEachIndexed { idx, h ->
            drawText(h, colX + colWidths[idx] / 2f, rowY + headerRowH / 2f + 4f, textColor, 9f, true)
            colX += colWidths[idx]
        }
        rowY += headerRowH

        // Calculate lengths
        val mainLength = if (slabType == "Cantilever") spanX * 1.2 else spanX
        val distLength = spanY
        val topBarLength = if (slabType == "OneWay") 0.0 else spanX * 0.3
        val tableRowColor = Color(0xFF263238)
        val tableRowAltColor = Color(0xFF1E2A33)

        val rows = mutableListOf<List<String>>()
        rows.add(listOf("\u2460", "Main (bottom)", mainRebarDia.toInt().toString(), mainRebarSpacing.toInt().toString(), mainLength.toInt().toString()))
        rows.add(listOf("\u2461", "Dist. (bottom)", distRebarDia.toInt().toString(), distRebarSpacing.toInt().toString(), distLength.toInt().toString()))
        if (topBarLength > 0) {
            rows.add(listOf("\u2462", "Top (support)", mainRebarDia.toInt().toString(), mainRebarSpacing.toInt().toString(), topBarLength.toInt().toString()))
        }
        // Hordi/Waffle: rib bars
        if (slabType == "Hordi" || slabType == "Waffle") {
            val ribBarLen = if (slabThickness > 0) (slabThickness * 0.7).toInt().toString() else "0"
            rows.add(listOf("\u2463", "Rib bars", mainRebarDia.toInt().toString(), (ribSpacing).toInt().toString(), ribBarLen))
        }

        rows.forEachIndexed { idx, row ->
            val bg = if (idx % 2 == 0) tableRowColor else tableRowAltColor
            drawRect(color = bg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, rowH))
            colX = tblLeft
            row.forEachIndexed { cIdx, cell ->
                val cellColor = when (cIdx) {
                    0 -> mainBarColor
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
        var cx = tblLeft
        for (i in 0 until colWidths.size - 1) {
            cx += colWidths[i]
            drawLine(Color(0xFF37474F), Offset(cx, tblTop), Offset(cx, rowY), strokeWidth = 0.5f)
        }
    }
}

// ── Horizontal dimension line helper ────────────────────────────
private fun DrawScope.drawDimensionLine(
    x1: Float, y1: Float, x2: Float, y2: Float, text: String, color: Color
) {
    val tick = 6f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x1, y1 - tick), Offset(x1, y1 + tick), strokeWidth = 1f)
    drawLine(color, Offset(x2, y2 - tick), Offset(x2, y2 + tick), strokeWidth = 1f)
    // Text centered
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.hashCode()
            textSize = 9f * density
            textAlign = android.graphics.Paint.Align.CENTER
        }
        this.drawText(text, (x1 + x2) / 2f, y1 - 4f, paint)
    }
}

// ── Vertical dimension line helper ──────────────────────────────
private fun DrawScope.drawDimensionLineV(
    x1: Float, y1: Float, x2: Float, y2: Float, text: String, color: Color
) {
    val tick = 6f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x1 - tick, y1), Offset(x1 + tick, y1), strokeWidth = 1f)
    drawLine(color, Offset(x2 - tick, y2), Offset(x2 + tick, y2), strokeWidth = 1f)
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.hashCode()
            textSize = 9f * density
            textAlign = android.graphics.Paint.Align.CENTER
        }
        save()
        rotate(-90f, x1 - 4f, (y1 + y2) / 2f)
        this.drawText(text, x1 - 4f, (y1 + y2) / 2f + 4f, paint)
        restore()
    }
}