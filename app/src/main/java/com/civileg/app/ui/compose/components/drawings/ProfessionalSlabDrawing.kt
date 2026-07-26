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
    viewMode: Int = 0,  // 0=All, 1=Plan, 2=Section, 3=Reinforcement Table
    modifier: Modifier = Modifier
) {
    // Dynamic height based on view mode — single views need less space
    // CRITICAL FIX (2026-07-27): Increased "All" view height from 900dp to 1000dp
    // to prevent the reinforcement table from being clipped. The "All" view
    // contains: 40px header + plan(36%) + 32px gap + section(24%) + 16dp gap
    // + table(40%) = 100% + 72px + 16dp. With 900dp canvas, the table's last
    // 2-3 rows were getting clipped, making it appear "empty" to the user.
    val canvasHeight = when (viewMode) {
        1 -> 380.dp  // Plan only
        2 -> 280.dp  // Section only
        3 -> 520.dp  // Table only — increased from 460 to fit all 5 rows + totals
        else -> 1000.dp  // All — increased from 900 to fit table fully
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
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
        // Adjust zone proportions based on view mode:
        // - viewMode 0 (All): plan 36%, section 24%, table 40%
        // - viewMode 1 (Plan only): plan 90%
        // - viewMode 2 (Section only): section 90%
        // - viewMode 3 (Table only): table 90%
        val planH = when (viewMode) { 1 -> h * 0.88f; 0 -> h * 0.36f; else -> h * 0.10f }
        val sectionH = when (viewMode) { 2 -> h * 0.88f; 0 -> h * 0.24f; else -> h * 0.10f }
        val tableH = when (viewMode) { 3 -> h * 0.88f; 0 -> h * 0.40f; else -> h * 0.10f }
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
        // FIX: SlabType.HOLLOW_BLOCK has displayName "Hollow Block" (not "Hordi"),
        // so the original check (slabType.contains("Hordi")) never triggered.
        // Now we check for both English display names AND legacy Arabic keywords.
        val isHordi = slabType.contains("Hordi", ignoreCase = true) ||
                      slabType.contains("هردي", ignoreCase = true) ||
                      slabType.contains("Hollow", ignoreCase = true) ||
                      slabType.contains("هولو", ignoreCase = true)
        val isWaffle = slabType.contains("Waffle", ignoreCase = true) ||
                       slabType.contains("وافل", ignoreCase = true) ||
                       slabType.contains("مجازين", ignoreCase = true)
        if (isHordi || isWaffle) {
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
            if (isWaffle) {
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
        val isFlat = slabType.contains("Flat", ignoreCase = true) ||
                     slabType.contains("مسطحة", ignoreCase = true)
        if (isFlat) {
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
        // FIX: SlabType enum doesn't have Cantilever or OneWay values.
        // Detect OneWay vs TwoWay based on span ratio (Lx/Ly < 0.5 = OneWay per ECP 203).
        val spanRatio = if (spanY > 0) spanX / spanY else 1.0
        val isOneWay = spanRatio < 0.5
        val isCantilever = slabType.contains("Cantilever", ignoreCase = true) ||
                           slabType.contains("كنتيل", ignoreCase = true) ||
                           slabType.contains("ناتئ", ignoreCase = true)
        val supports = mutableListOf<Offset>()

        when {
            isCantilever -> {
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
            isOneWay -> {
                // Walls on left & right (one-way slab spans between them)
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
                // Columns at corners (TwoWay / FlatPlate / Waffle / Hollow Block)
                val corners = listOf(
                    Offset(slabLeft, slabTop), Offset(slabRight - colSize, slabTop),
                    Offset(slabLeft, slabBottom - colSize), Offset(slabRight - colSize, slabBottom - colSize)
                )
                corners.forEach {
                    drawRect(color = columnFill, topLeft = it, size = Size(colSize, colSize))
                }
                // Centre columns for TwoWay/FlatPlate (skip if OneWay)
                if (!isOneWay && (isFlat || spanRatio >= 0.5)) {
                    val cx = slabLeft + drawSpanX / 2f - colSize / 2f
                    val cy = slabTop + drawSpanY / 2f - colSize / 2f
                    drawRect(color = columnFill, topLeft = Offset(cx, cy), size = Size(colSize, colSize))
                    // Punching shear perimeter for FlatPlate
                    if (isFlat) {
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
        // CRITICAL FIX: spanX/spanY are in METERS, multiply by 1000 to display in mm
        drawDimensionLine(
            slabLeft, slabTop - 14f, slabRight, slabTop - 14f,
            "${(spanX * 1000).toInt()} mm", dimColor
        )
        drawDimensionLineV(
            slabLeft - 14f, slabTop, slabLeft - 14f, slabBottom,
            "${(spanY * 1000).toInt()} mm", dimColor
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
        if (isHordi || isWaffle) {
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
            if (!isCantilever) {
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
            isCantilever -> {
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
            isOneWay -> {
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
        if (!isOneWay) {
            drawText("\u2462", sSlabLeft + 14f, sSlabTop - 5f, topBarColor, 9f, true)
        }

        // Section label indicator
        drawText("A", slabLeft - 24f, slabTop + drawSpanY / 2f + 4f, Color(0xFFE74C3C), 10f, true)
        drawText("A", slabRight + 24f, slabTop + drawSpanY / 2f + 4f, Color(0xFFE74C3C), 10f, true)

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE (8 columns, density-aware row heights)
        //  FIX: rowH/headerRowH were in raw px (22f/26f), but text uses
        //  density scaling (size * density). On xxhdpi (3x), text=27px
        //  but row=22px → text overlapped rows → table looked empty.
        //  Now using .dp.toPx() so row heights scale with density.
        // ══════════════════════════════════════════════════════════
        val tblTop = secBottom + 16f * density
        val tblLeft = margin
        val tblRight = w - margin
        val tblWidth = tblRight - tblLeft
        val rowH = 26f * density       // ~9dp at 3x density
        val headerRowH = 32f * density  // ~10.7dp at 3x density
        // 8 columns: Mark(7%), Direction(15%), Dia(10%), Spacing(11%), Length(13%), As Req(13%), As Prov(13%), Weight(18%)
        val colWidths = floatArrayOf(
            tblWidth * 0.07f, tblWidth * 0.15f, tblWidth * 0.10f,
            tblWidth * 0.11f, tblWidth * 0.13f, tblWidth * 0.13f,
            tblWidth * 0.13f, tblWidth * 0.18f
        )
        var rowY = tblTop

        // Table header bg
        drawRoundRect(
            color = headerBg, topLeft = Offset(tblLeft, rowY),
            size = Size(tblWidth, headerRowH), cornerRadius = CornerRadius(4f)
        )
        val headers = listOf("Mark", "Direction", "Dia (mm)", "Spacing (mm)", "Length (mm)", "As Req (mm²/m)", "As Prov (mm²/m)", "Weight (kg)")
        var colX = tblLeft
        headers.forEachIndexed { idx, h ->
            drawText(h, colX + colWidths[idx] / 2f, rowY + headerRowH / 2f + 4f * density, textColor, 9f, true)
            colX += colWidths[idx]
        }
        rowY += headerRowH

        // Calculate lengths — CRITICAL: convert meters to mm for table display
        val mainLength = if (isCantilever) spanX * 1.2 else spanX
        val distLength = spanY
        val topBarLength = if (isOneWay) 0.0 else spanX * 0.3
        val tableRowColor = Color(0xFF263238)
        val tableRowAltColor = Color(0xFF1E2A33)

        // Calculate As provided and weight — guard against zero spacing/diameter
        val safeMainSpacing = if (mainRebarSpacing > 0) mainRebarSpacing else 200.0
        val safeDistSpacing = if (distRebarSpacing > 0) distRebarSpacing else 200.0
        val safeMainDia = if (mainRebarDia > 0) mainRebarDia else 12.0
        val safeDistDia = if (distRebarDia > 0) distRebarDia else 10.0
        val mainAsProvided = (Math.PI * safeMainDia * safeMainDia / 4.0) * (1000.0 / safeMainSpacing) // mm²/m
        val distAsProvided = (Math.PI * safeDistDia * safeDistDia / 4.0) * (1000.0 / safeDistSpacing) // mm²/m
        val mainWeightPerM2 = (mainAsProvided / 1000.0) * 0.00785 * 1000.0 * safeMainDia / 12.5 // approx kg/m²
        val slabArea = spanX * spanY // m²

        // As required estimates based on ECP 203 §4-2-2-1 (simplified for table display)
        // As_req = Mu / (0.87 * fy * z), where z ≈ 0.9 * d, d ≈ t - 25mm cover
        val fyDesign = 360.0 // MPa (default high-grade steel)
        val effDepth = (slabThickness - 25.0).coerceAtLeast(50.0) // mm
        val z = 0.9 * effDepth // mm lever arm
        // Approximate moments per meter width: wu * L² / 8 (simply supported)
        // Assume wu ≈ 10 kN/m² for residential (DL+LL factored)
        val wu = 10.0 // kN/m² (rough estimate for table display)
        val mainAsReq = (wu * spanX * spanX * 1e6 / 8.0) / (0.87 * fyDesign * z) // mm²/m
        val distAsReq = (wu * spanY * spanY * 1e6 / 8.0) / (0.87 * fyDesign * z) // mm²/m
        // Top steel required over supports ≈ 50% of bottom main
        val topAsReq = mainAsReq * 0.5
        // Shrinkage & temperature steel per ECP §4-2-5: 0.0018 * b * t for fy=360
        val shrinkageAsReq = 0.0018 * 1000.0 * slabThickness // mm²/m

        // Expanded table: Mark, Direction, Dia, Spacing, Length, As Req, As Prov, Weight
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "①",
            "Main Bottom (X)",
            safeMainDia.toInt().toString(),
            safeMainSpacing.toInt().toString(),
            (mainLength * 1000).toInt().toString(),
            String.format("%.0f", mainAsReq),
            String.format("%.0f", mainAsProvided),
            String.format("%.1f", mainAsProvided * mainLength * 0.00785)
        ))
        rows.add(listOf(
            "②",
            "Dist. Bottom (Y)",
            safeDistDia.toInt().toString(),
            safeDistSpacing.toInt().toString(),
            (distLength * 1000).toInt().toString(),
            String.format("%.0f", distAsReq),
            String.format("%.0f", distAsProvided),
            String.format("%.1f", distAsProvided * distLength * 0.00785)
        ))
        if (topBarLength > 0) {
            val topAsProvided = (Math.PI * safeMainDia * safeMainDia / 4.0) * (1000.0 / safeMainSpacing)
            rows.add(listOf(
                "③",
                "Top Support",
                safeMainDia.toInt().toString(),
                safeMainSpacing.toInt().toString(),
                (topBarLength * 1000).toInt().toString(),
                String.format("%.0f", topAsReq),
                String.format("%.0f", topAsProvided),
                String.format("%.1f", topAsProvided * topBarLength * 0.00785)
            ))
        }
        // Shrinkage & temperature steel row
        val shrinkageDia = 10.0
        val shrinkageSpacing = (Math.PI * shrinkageDia * shrinkageDia / 4.0 * 1000.0 / shrinkageAsReq)
            .coerceIn(150.0, 300.0)
        val shrinkageAsProvided = (Math.PI * shrinkageDia * shrinkageDia / 4.0) * (1000.0 / shrinkageSpacing)
        rows.add(listOf(
            "④",
            "Shrinkage (ECP §4-2-5)",
            shrinkageDia.toInt().toString(),
            shrinkageSpacing.toInt().toString(),
            (distLength * 1000).toInt().toString(),
            String.format("%.0f", shrinkageAsReq),
            String.format("%.0f", shrinkageAsProvided),
            String.format("%.1f", shrinkageAsProvided * distLength * 0.00785)
        ))
        // Hordi/Waffle: rib bars
        if (isHordi || isWaffle) {
            val ribBarLen = if (slabThickness > 0) (slabThickness * 0.7 * 1000).toInt().toString() else "0"
            val safeRibSpacing = if (ribSpacing > 0) ribSpacing else 500.0
            val ribAs = (Math.PI * safeMainDia * safeMainDia / 4.0) * (1000.0 / safeRibSpacing)
            rows.add(listOf(
                "⑤",
                "Rib Bars",
                safeMainDia.toInt().toString(),
                safeRibSpacing.toInt().toString(),
                ribBarLen,
                String.format("%.0f", mainAsReq * 0.6),
                String.format("%.0f", ribAs),
                String.format("%.1f", ribAs * slabThickness * 0.7 * 0.00785)
            ))
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
                drawText(cell, colX + colWidths[cIdx] / 2f, rowY + rowH / 2f + 3f * density, cellColor, 9f)
                colX += colWidths[cIdx]
            }
            rowY += rowH
        }
        // Totals row
        val totalSteelWeight = rows.sumOf { it.last().replace(",", ".").toDoubleOrNull() ?: 0.0 }
        drawRect(color = headerBg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, rowH))
        drawText("TOTAL", tblLeft + colWidths[0] / 2f, rowY + rowH / 2f + 3f * density, Color(0xFFFFD700), 9f, true)
        drawText("Steel weight per slab", tblLeft + colWidths[0] + colWidths[1] / 2f, rowY + rowH / 2f + 3f * density, Color(0xFFFFD700), 9f, true)
        // Span total across remaining columns
        var totX = tblLeft + colWidths[0] + colWidths[1]
        for (i in 2 until colWidths.size - 1) {
            drawText("", totX + colWidths[i] / 2f, rowY + rowH / 2f, Color(0xFFFFD700), 9f)
            totX += colWidths[i]
        }
        drawText(String.format("%.2f kg (%.1f m² slab)", totalSteelWeight, slabArea), totX + colWidths.last() / 2f, rowY + rowH / 2f + 3f * density, Color(0xFFFFD700), 9f, true)
        rowY += rowH
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