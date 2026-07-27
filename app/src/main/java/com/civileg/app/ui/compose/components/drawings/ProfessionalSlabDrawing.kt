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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Professional Slab Engineering Drawing — DATA-DRIVEN + LAYOUT-MATCHED (2026-07-27 v2)
 *
 * ********************************************************************************
 * FIXES IN v2:
 * ********************************************************************************
 * 1. Canvas heights now MATCH the parent InteractiveDrawingScreen heights exactly
 *    (1000 / 380 / 280 / 520 dp). Previous version used 1100dp which caused the
 *    table's last rows to be CLIPPED — the user saw "no reinforcement in the
 *    reinforcement table" because the totals row was off-canvas.
 *
 * 2. Layout zones redesigned: Plan view gets a LARGER allocation in viewMode=0
 *    (50% instead of 34%). Section view gets 25%. Table gets 25%. This makes
 *    drawings look "logical" instead of squashed.
 *
 * 3. Reinforcement table: cleaner column widths, larger fonts, clearer visual
 *    hierarchy with status colors (green SAFE / red UNSAFE).
 *
 * 4. Bilingual labels: when LocaleHelper.isArabic(), labels are Arabic.
 *
 * Supports: SOLID, FLAT, HOLLOW_BLOCK, WAFFLE, POST_TENSION.
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
    viewMode: Int = 0,  // 0=All, 1=Plan, 2=Section, 3=Table
    modifier: Modifier = Modifier,
    momentX: Double = 0.0,
    momentY: Double = 0.0,
    factoredLoad: Double = 0.0,
    fcu: Double = 25.0,
    fy: Double = 360.0,
    isSafe: Boolean = true,
    utilizationRatio: Double = 0.0
) {
    // CRITICAL FIX v2: Match these heights EXACTLY to the parent's drawingHeightDp.
    // SlabScreen passes drawingHeightDp = 1000/380/280/520 — we use the SAME values here.
    val canvasHeight = when (viewMode) {
        1 -> 380.dp
        2 -> 280.dp
        3 -> 520.dp
        else -> 1000.dp  // All — MUST match SlabScreen.drawingHeight
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
    ) {
        val w = size.width
        val h = size.height
        val density = this.density

        // ── Bilingual labels ──────────────────────────────────────────
        val isArabic = com.civileg.app.utils.LocaleHelper.isArabic()
        fun t(ar: String, en: String) = if (isArabic) ar else en

        // ── Color Palette ──────────────────────────────────────────────
        val concreteFill = Color(0xFFE0E0E0)
        val concreteStroke = Color(0xFF424242)
        val mainBarColor = Color(0xFF1565C0)
        val distBarColor = Color(0xFF43A047)
        val topBarColor = Color(0xFFC62828)
        val dimColor = Color(0xFF6A1B9A)
        val textColor = Color(0xFFFFFFFF)
        val headerBg = Color(0xFF1A237E)
        val columnFill = Color(0xFF616161)
        val ribFill = Color(0xFFBDBDBD)
        val blockFill = Color(0xFFFFF59D)
        val dropFill = Color(0xFFB0BEC5)
        val shearPerimColor = Color(0xFFE65100)
        val stripColor = Color(0xFF757575)
        val safeColor = Color(0xFF2E7D32)
        val unsafeColor = Color(0xFFC62828)
        val tableHeaderText = Color(0xFFFFFFFF)
        val tableCellText = Color(0xFF212121)
        val tableRowColor = Color(0xFFFAFAFA)
        val tableRowAltColor = Color(0xFFF5F5F5)

        // ── Text helpers ───────────────────────────────────────────────
        // Use cached Arabic typeface via ArabicFontProvider (loads from app context once,
        // then cached for subsequent calls — much faster than loading per-drawText call).
        val arabicTf: android.graphics.Typeface? = try {
            com.civileg.app.utils.LocaleHelper.getAppContext()?.let {
                com.civileg.app.utils.ArabicFontProvider.getArabicTypeface(it, false)
            }
        } catch (_: Exception) { null }
        val arabicTfBold: android.graphics.Typeface? = try {
            com.civileg.app.utils.LocaleHelper.getAppContext()?.let {
                com.civileg.app.utils.ArabicFontProvider.getArabicTypeface(it, true)
            }
        } catch (_: Exception) { null }

        /**
         * CRITICAL FIX (2026-07-27): Use StaticLayout for ALL text containing Arabic
         * characters. Canvas.drawText alone DOES shape Arabic letters via HarfBuzz,
         * BUT it does NOT perform BIDI reordering — Arabic text would appear in
         * logical (LTR) order with letters connected but in WRONG sequence (visually
         * reversed). StaticLayout uses Android's full text pipeline (HarfBuzz + Bidi
         * + LineBreaker) and produces correctly-rendered Arabic.
         *
         * Latin-only text uses Canvas.drawText directly (faster, no layout overhead).
         */
        fun drawText(
            text: String, x: Float, y: Float,
            color: Color = textColor, size: Float = 11f, bold: Boolean = false,
            align: android.graphics.Paint.Align = android.graphics.Paint.Align.CENTER
        ) {
            drawContext.canvas.nativeCanvas.apply {
                val hasArabic = com.civileg.app.utils.ArabicFontProvider.containsArabic(text)

                // For pure Latin/numeric text, use Canvas.drawText directly (faster)
                if (!hasArabic) {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color.hashCode()
                        this.textSize = size * density
                        this.isFakeBoldText = bold
                        this.textAlign = align
                    }
                    this.drawText(text, x, y, paint)
                    return@apply
                }

                // For Arabic text, use StaticLayout for proper BIDI + shaping
                val tf = if (bold) arabicTfBold ?: arabicTf else arabicTf
                val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.hashCode()
                    this.textSize = size * density
                    this.isFakeBoldText = bold
                    this.typeface = tf ?: android.graphics.Typeface.DEFAULT
                }
                val layoutWidth = (this.width - x).toInt().coerceAtLeast(1)
                val layoutAlign = when (align) {
                    android.graphics.Paint.Align.CENTER -> android.text.Layout.Alignment.ALIGN_CENTER
                    android.graphics.Paint.Align.RIGHT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                    else -> android.text.Layout.Alignment.ALIGN_NORMAL
                }
                val sl = android.text.StaticLayout.Builder
                    .obtain(text, 0, text.length, tp, layoutWidth)
                    .setAlignment(layoutAlign)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
                val drawX = when (align) {
                    android.graphics.Paint.Align.CENTER -> x - sl.width / 2f
                    android.graphics.Paint.Align.RIGHT -> x - sl.width
                    else -> x
                }
                this.save()
                this.translate(drawX, y - tp.ascent() - tp.descent() / 2f)
                sl.draw(this)
                this.restore()
            }
        }

        // ── Layout zones — MATCHED to canvasHeight values ──────────────
        // viewMode=0 (All, 1000dp): Plan 50% / Section 22% / Table 28%
        // viewMode=1 (Plan, 380dp): Plan 90% (with header)
        // viewMode=2 (Section, 280dp): Section 90%
        // viewMode=3 (Table, 520dp): Table 90%
        val planH = when (viewMode) {
            1 -> h * 0.90f
            0 -> h * 0.50f
            else -> h * 0.10f
        }
        val sectionH = when (viewMode) {
            2 -> h * 0.90f
            0 -> h * 0.22f
            else -> h * 0.10f
        }
        val tableH = when (viewMode) {
            3 -> h * 0.92f
            0 -> h * 0.28f
            else -> h * 0.10f
        }
        val margin = 28f
        val planTop = 44f  // Leave room for header
        val planLeft = margin + 50f
        val planRight = w - margin
        val planBottom = planTop + planH - 30f
        val planW = planRight - planLeft
        val planDrawH = planBottom - planTop

        // ── Slab type detection ────────────────────────────────────────
        val isHordi = slabType.contains("Hordi", ignoreCase = true) ||
                      slabType.contains("هردي", ignoreCase = true) ||
                      slabType.contains("Hollow", ignoreCase = true) ||
                      slabType.contains("هولو", ignoreCase = true)
        val isWaffle = slabType.contains("Waffle", ignoreCase = true) ||
                       slabType.contains("وافل", ignoreCase = true)
        val isCantilever = slabType.contains("Cantilever", ignoreCase = true) ||
                           slabType.contains("ناتئ", ignoreCase = true)
        val isFlat = slabType.contains("Flat", ignoreCase = true) ||
                     slabType.contains("مسطحة", ignoreCase = true)
        val spanRatio = if (spanY > 0) spanX / spanY else 1.0
        val isOneWay = spanRatio < 0.5

        // ── Scaling for plan view ──────────────────────────────────────
        val scaleX = planW / spanX.toFloat()
        val scaleY = planDrawH / spanY.toFloat()
        val scale = min(scaleX, scaleY) * 0.85f
        val drawSpanX = spanX.toFloat() * scale
        val drawSpanY = spanY.toFloat() * scale
        val slabLeft = planLeft + (planW - drawSpanX) / 2f
        val slabTop = planTop + (planDrawH - drawSpanY) / 2f
        val slabRight = slabLeft + drawSpanX
        val slabBottom = slabTop + drawSpanY

        // ══════════════════════════════════════════════════════════
        // HEADER (always visible)
        // ══════════════════════════════════════════════════════════
        val headerColor = if (isSafe) Color(0xFF1B5E20) else Color(0xFF7F0000)
        drawRoundRect(
            color = headerColor, topLeft = Offset(0f, 0f),
            size = Size(w, 36f), cornerRadius = CornerRadius(0f)
        )
        val statusLabel = if (isSafe) "✓ ${t("آمن", "SAFE")}" else "✗ ${t("غير آمن", "UNSAFE")}"
        val headerText = "${t("تفاصيل البلاطة", "SLAB DETAIL")} — ${slabType.uppercase()}  •  $statusLabel  •  U=${(utilizationRatio * 100).toInt()}%"
        drawText(headerText, w / 2f, 24f, textColor, size = 11f, bold = true)

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            // Plan background card
            drawRoundRect(
                color = Color(0xFF37474F), topLeft = Offset(slabLeft - 6f, slabTop - 6f),
                size = Size(drawSpanX + 12f, drawSpanY + 12f), cornerRadius = CornerRadius(3f)
            )
            // Slab concrete fill
            drawRect(
                color = concreteFill,
                topLeft = Offset(slabLeft, slabTop),
                size = Size(drawSpanX, drawSpanY)
            )

            // Concrete hatching (subtle)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.clipRect(slabLeft, slabTop, slabRight, slabBottom)
            val hatchStep = 22f
            var hx = slabLeft - drawSpanY
            val hatchPaint = android.graphics.Paint().apply {
                color = Color(0xFFBDBDBD).hashCode()
                strokeWidth = 0.6f * density
                isAntiAlias = true
            }
            while (hx < slabRight) {
                drawContext.canvas.nativeCanvas.drawLine(
                    hx, slabTop, hx + drawSpanY, slabBottom, hatchPaint
                )
                hx += hatchStep
            }
            drawContext.canvas.nativeCanvas.restore()

            // ── Hordi / Waffle ribs & blocks ───────────────────────
            if (isHordi || isWaffle) {
                val rs = if (ribSpacing > 0) ribSpacing else 500.0
                val rw = if (ribWidth > 0) ribWidth else 100.0
                val ribStepPx = (rs * scale).toFloat()
                val ribWPx = (rw * scale).toFloat()
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
                }
            }

            // ── Flat Plate: column strip / drop panel ──────────────
            if (isFlat) {
                val stripW = drawSpanY / 6f
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
                if (dropPanelSize > 0) {
                    val dpPx = (dropPanelSize * scale).toFloat()
                    val dpL = (drawSpanX - dpPx) / 2f + slabLeft
                    val dpT = (drawSpanY - dpPx) / 2f + slabTop
                    drawRect(color = dropFill, topLeft = Offset(dpL, dpT), size = Size(dpPx, dpPx))
                    drawRect(
                        color = concreteStroke, topLeft = Offset(dpL, dpT),
                        size = Size(dpPx, dpPx), style = Stroke(width = 1.5f)
                    )
                }
            }

            // ── Main reinforcement (parallel blue lines, direction X) ──
            val safeMainSpacing = if (mainRebarSpacing > 0) mainRebarSpacing else 200.0
            val mainStepPx = (safeMainSpacing * scale).toFloat().coerceAtLeast(10f)
            var my = slabTop + mainStepPx / 2f
            while (my < slabBottom - mainStepPx / 4f) {
                drawLine(
                    mainBarColor, Offset(slabLeft + 6f, my),
                    Offset(slabRight - 6f, my), strokeWidth = 2.0f
                )
                my += mainStepPx
            }
            drawText("①", slabRight + 16f, slabTop + drawSpanY * 0.3f, mainBarColor, 12f, true)

            // ── Distribution reinforcement (direction Y) ─────────────
            val safeDistSpacing = if (distRebarSpacing > 0) distRebarSpacing else 200.0
            val distStepPx = (safeDistSpacing * scale).toFloat().coerceAtLeast(10f)
            var dx = slabLeft + distStepPx / 2f
            while (dx < slabRight - distStepPx / 4f) {
                drawLine(
                    distBarColor, Offset(dx, slabTop + 6f),
                    Offset(dx, slabBottom - 6f), strokeWidth = 1.2f
                )
                dx += distStepPx
            }
            drawText("②", slabLeft - 16f, slabBottom + 18f, distBarColor, 12f, true)

            // ── Supports ───────────────────────────────────────────
            val colSize = 24f
            when {
                isCantilever -> {
                    drawRect(
                        color = columnFill,
                        topLeft = Offset(slabLeft - 12f, slabTop - 8f),
                        size = Size(16f, drawSpanY + 16f)
                    )
                }
                isOneWay -> {
                    for (side in listOf(slabLeft - 12f, slabRight - 4f)) {
                        drawRect(
                            color = columnFill,
                            topLeft = Offset(side, slabTop - 8f),
                            size = Size(16f, drawSpanY + 16f)
                        )
                    }
                }
                else -> {
                    val corners = listOf(
                        Offset(slabLeft, slabTop), Offset(slabRight - colSize, slabTop),
                        Offset(slabLeft, slabBottom - colSize), Offset(slabRight - colSize, slabBottom - colSize)
                    )
                    corners.forEach {
                        drawRect(color = columnFill, topLeft = it, size = Size(colSize, colSize))
                    }
                    if (isFlat) {
                        val cx = slabLeft + drawSpanX / 2f - colSize / 2f
                        val cy = slabTop + drawSpanY / 2f - colSize / 2f
                        drawRect(color = columnFill, topLeft = Offset(cx, cy), size = Size(colSize, colSize))
                        val d = slabThickness - cover
                        val dPx = (d * scale).toFloat().coerceAtLeast(colSize * 0.6f)
                        val psSize = colSize + dPx
                        drawRect(
                            color = shearPerimColor,
                            topLeft = Offset(cx + colSize / 2f - psSize / 2f, cy + colSize / 2f - psSize / 2f),
                            size = Size(psSize, psSize),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f)))
                        )
                        drawText("Bo", cx + colSize / 2f, cy - 8f, shearPerimColor, 10f)
                    }
                }
            }

            // Slab border
            drawRect(
                color = concreteStroke,
                topLeft = Offset(slabLeft, slabTop),
                size = Size(drawSpanX, drawSpanY),
                style = Stroke(width = 2.5f)
            )

            // Dimension lines (display in mm)
            drawDimensionLine(
                slabLeft, slabTop - 16f, slabRight, slabTop - 16f,
                "${(spanX * 1000).toInt()} mm", dimColor, density
            )
            drawDimensionLineV(
                slabLeft - 16f, slabTop, slabLeft - 16f, slabBottom,
                "${(spanY * 1000).toInt()} mm", dimColor, density
            )
            drawText(t("مسقط", "PLAN"), slabLeft + 22f, slabBottom + 18f, Color(0xFF757575), 10f, true)
        }

        // ══════════════════════════════════════════════════════════
        //  SECTION VIEW
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val secTop = if (viewMode == 0) planBottom + 16f else 44f
            val secBottom = secTop + sectionH - 30f
            val secLeft = margin + 80f
            val secRight = w - margin

            drawText(t("قطاع A-A", "SECTION A-A"), secLeft, secTop - 4f, Color(0xFF757575), 10f, true)

            val maxSectionW = secRight - secLeft - 100f
            val sectionSpanPx = min(drawSpanX, maxSectionW)
            val sectionScale = sectionSpanPx / spanX.toFloat()
            val thickPx = (slabThickness * sectionScale).toFloat().coerceIn(20f, 80f)
            val sSlabLeft = secLeft + (maxSectionW - sectionSpanPx) / 2f
            val sSlabTop = secTop + (sectionH - thickPx) / 2f
            val sSlabBottom = sSlabTop + thickPx

            if (isHordi || isWaffle) {
                val toppingH = (slabThickness * 0.3 * sectionScale).toFloat().coerceAtLeast(12f)
                val ribH = thickPx - toppingH
                val ribWPx = (ribWidth * sectionScale).toFloat().coerceAtLeast(8f)
                val ribSPx = (ribSpacing * sectionScale).toFloat().coerceAtLeast(24f)
                drawRect(
                    color = concreteFill,
                    topLeft = Offset(sSlabLeft, sSlabTop),
                    size = Size(sectionSpanPx, toppingH)
                )
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
                    drawCircle(
                        color = mainBarColor,
                        radius = 4f,
                        center = Offset(rrx, sSlabTop + toppingH + ribH - 6f)
                    )
                    rrx += ribSPx
                }
            } else {
                // Standard slab section
                drawRect(
                    color = concreteFill,
                    topLeft = Offset(sSlabLeft, sSlabTop),
                    size = Size(sectionSpanPx, thickPx)
                )

                // Main bottom bars
                val barR = (mainRebarDia * sectionScale / 2f).toFloat().coerceIn(3f, 6f)
                val safeMainSpacing = if (mainRebarSpacing > 0) mainRebarSpacing else 200.0
                val barCount = ((sectionSpanPx - 20f) / (safeMainSpacing * sectionScale).toFloat())
                    .toInt().coerceIn(3, 25)
                if (barCount > 1) {
                    val barStep = (sectionSpanPx - 20f) / (barCount - 1)
                    for (i in 0 until barCount) {
                        val bx = sSlabLeft + 10f + i * barStep
                        val by = sSlabBottom - 6f
                        drawCircle(color = mainBarColor, radius = barR, center = Offset(bx, by))
                    }
                }

                // Top steel at supports
                if (!isCantilever) {
                    val topBarR = barR * 0.9f
                    val topCount = min(barCount, 6)
                    if (topCount > 1) {
                        val topBarStep = (sectionSpanPx * 0.3f) / (topCount - 1)
                        for (i in 0 until topCount) {
                            val bx = sSlabLeft + 10f + i * topBarStep
                            val by = sSlabTop + 6f
                            drawCircle(color = topBarColor, radius = topBarR, center = Offset(bx, by))
                        }
                        for (i in 0 until topCount) {
                            val bx = sSlabLeft + sectionSpanPx - 10f - i * topBarStep
                            val by = sSlabTop + 6f
                            drawCircle(color = topBarColor, radius = topBarR, center = Offset(bx, by))
                        }
                    }
                } else {
                    val topCount = barCount
                    if (topCount > 1) {
                        val topStep = (sectionSpanPx - 20f) / (topCount - 1)
                        for (i in 0 until topCount) {
                            val bx = sSlabLeft + 10f + i * topStep
                            val by = sSlabTop + 6f
                            drawCircle(color = topBarColor, radius = barR, center = Offset(bx, by))
                        }
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

            // Cover dimension
            val coverPx = (cover * sectionScale).toFloat().coerceIn(5f, 18f)
            drawLine(
                Color(0xFF2E7D32), Offset(sSlabLeft + 20f, sSlabBottom),
                Offset(sSlabLeft + 20f, sSlabBottom - coverPx), strokeWidth = 1f
            )
            drawText("c=${cover.toInt()}", sSlabLeft + 44f, sSlabBottom - coverPx / 2f + 4f, Color(0xFF2E7D32), 9f)

            // Thickness dimension
            val dimX = sSlabLeft + sectionSpanPx + 14f
            drawLine(dimColor, Offset(dimX, sSlabTop), Offset(dimX, sSlabBottom), strokeWidth = 1.2f)
            drawLine(dimColor, Offset(dimX - 4f, sSlabTop), Offset(dimX + 4f, sSlabTop), strokeWidth = 1.2f)
            drawLine(dimColor, Offset(dimX - 4f, sSlabBottom), Offset(dimX + 4f, sSlabBottom), strokeWidth = 1.2f)
            drawText("t=${slabThickness.toInt()}", dimX + 26f, sSlabTop + thickPx / 2f + 4f, dimColor, 9f)

            // Bar labels
            drawText("①", sSlabLeft + sectionSpanPx / 2f, sSlabBottom + 16f, mainBarColor, 10f, true)
            if (!isCantilever && !isHordi && !isWaffle) {
                drawText("③", sSlabLeft + 14f, sSlabTop - 6f, topBarColor, 10f, true)
            }
            // Section markers (A-A)
            if (viewMode == 0) {
                drawText("A", slabLeft - 26f, slabTop + drawSpanY / 2f + 4f, Color(0xFFC62828), 11f, true)
                drawText("A", slabRight + 26f, slabTop + drawSpanY / 2f + 4f, Color(0xFFC62828), 11f, true)
            }
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE (data-driven, 8 columns)
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val secBottom = if (viewMode == 0) planBottom + sectionH + 16f else 44f
            val tblTop = secBottom
            val tblLeft = margin
            val tblRight = w - margin
            val tblWidth = tblRight - tblLeft

            // Calculate row count to size rows to fit available space
            val effDepth = (slabThickness - cover).coerceAtLeast(50.0)  // mm
            val z = 0.9 * effDepth  // mm lever arm
            val wu = if (factoredLoad > 0) factoredLoad else 10.0  // kN/m²
            val realMx = if (momentX > 0) momentX else wu * spanX * spanX / 8.0
            val realMy = if (momentY > 0) momentY else wu * spanY * spanY / 8.0
            val mainAsReq = (realMx * 1e6 / (0.87 * fy * z)).coerceAtLeast(0.0)
            val distAsReq = (realMy * 1e6 / (0.87 * fy * z)).coerceAtLeast(0.0)
            val topAsReq = mainAsReq * 0.5
            val shrinkageAsReq = 0.0018 * 1000.0 * slabThickness

            // As provided
            val safeMainSpacing = if (mainRebarSpacing > 0) mainRebarSpacing else 200.0
            val safeDistSpacing = if (distRebarSpacing > 0) distRebarSpacing else 200.0
            val safeMainDia = if (mainRebarDia > 0) mainRebarDia else 12.0
            val safeDistDia = if (distRebarDia > 0) distRebarDia else 10.0
            val mainAsProvided = (Math.PI * safeMainDia * safeMainDia / 4.0) * (1000.0 / safeMainSpacing)
            val distAsProvided = (Math.PI * safeDistDia * safeDistDia / 4.0) * (1000.0 / safeDistSpacing)

            val mainLength = spanX
            val distLength = spanY
            val topBarLength = if (spanRatio < 0.5) 0.0 else spanX * 0.3

            // Build rows
            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                "①",
                t("رئيسي سفلي (X)", "Main Bottom (X)"),
                safeMainDia.toInt().toString(),
                safeMainSpacing.toInt().toString(),
                "${(mainLength * 1000).toInt()}",
                String.format("%.0f", mainAsReq),
                String.format("%.0f", mainAsProvided),
                String.format("%.1f", mainAsProvided * mainLength * 0.00785)
            ))
            rows.add(listOf(
                "②",
                t("توزيع سفلي (Y)", "Dist. Bottom (Y)"),
                safeDistDia.toInt().toString(),
                safeDistSpacing.toInt().toString(),
                "${(distLength * 1000).toInt()}",
                String.format("%.0f", distAsReq),
                String.format("%.0f", distAsProvided),
                String.format("%.1f", distAsProvided * distLength * 0.00785)
            ))
            if (topBarLength > 0) {
                val topAsProvided = mainAsProvided
                rows.add(listOf(
                    "③",
                    t("علوي فوق المساند", "Top Support"),
                    safeMainDia.toInt().toString(),
                    safeMainSpacing.toInt().toString(),
                    "${(topBarLength * 1000).toInt()}",
                    String.format("%.0f", topAsReq),
                    String.format("%.0f", topAsProvided),
                    String.format("%.1f", topAsProvided * topBarLength * 0.00785)
                ))
            }
            val shrinkageDia = 10.0
            val shrinkageSpacing = (Math.PI * shrinkageDia * shrinkageDia / 4.0 * 1000.0 / shrinkageAsReq)
                .coerceIn(150.0, 300.0)
            val shrinkageAsProvided = (Math.PI * shrinkageDia * shrinkageDia / 4.0) * (1000.0 / shrinkageSpacing)
            rows.add(listOf(
                "④",
                t("انكماش §4-2-5", "Shrinkage §4-2-5"),
                shrinkageDia.toInt().toString(),
                shrinkageSpacing.toInt().toString(),
                "${(distLength * 1000).toInt()}",
                String.format("%.0f", shrinkageAsReq),
                String.format("%.0f", shrinkageAsProvided),
                String.format("%.1f", shrinkageAsProvided * distLength * 0.00785)
            ))
            if (isHordi || isWaffle) {
                val ribBarLen = (slabThickness * 0.7 * 1000).toInt().toString()
                val safeRibSpacing = if (ribSpacing > 0) ribSpacing else 500.0
                val ribAs = (Math.PI * safeMainDia * safeMainDia / 4.0) * (1000.0 / safeRibSpacing)
                rows.add(listOf(
                    "⑤",
                    t("تسليح الكمرات", "Rib Bars"),
                    safeMainDia.toInt().toString(),
                    safeRibSpacing.toInt().toString(),
                    ribBarLen,
                    String.format("%.0f", mainAsReq * 0.6),
                    String.format("%.0f", ribAs),
                    String.format("%.1f", ribAs * slabThickness * 0.7 * 0.00785)
                ))
            }

            // Compute available height for table rows
            val totalRows = rows.size + 1 /* header */ + 1 /* totals */
            val availH = tblTop + tableH - tblTop  // = tableH
            val headerRowH = 32f * density
            val totalsRowH = 28f * density
            val dataRowH = ((availH - headerRowH - totalsRowH - 16f) / rows.size.toFloat())
                .coerceAtLeast(22f * density)
                .coerceAtMost(38f * density)

            val colWidths = floatArrayOf(
                tblWidth * 0.06f, tblWidth * 0.22f, tblWidth * 0.07f,
                tblWidth * 0.10f, tblWidth * 0.11f, tblWidth * 0.13f,
                tblWidth * 0.13f, tblWidth * 0.18f
            )
            var rowY = tblTop

            // ── Table Title bar ─────────────────────────────────────
            drawRect(color = Color(0xFF263238), topLeft = Offset(tblLeft, rowY - 22f * density), size = Size(tblWidth, 22f * density))
            drawText(t("جدول التسليح - Reinforcement Schedule", "Reinforcement Schedule"), tblLeft + tblWidth / 2f, rowY - 7f * density, Color.White, 11f, true)

            // ── Header row ─────────────────────────────────────────
            drawRect(color = headerBg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, headerRowH))
            val headers = listOf(
                t("الرمز", "Mark"),
                t("الاتجاه", "Direction"),
                t("قطر", "Dia"),
                t("تباعد", "Spacing"),
                t("الطول", "Length"),
                "As ${t("مطلوب", "Req")}",
                "As ${t("موجود", "Prov")}",
                t("الوزن", "Weight")
            )
            var colX = tblLeft
            headers.forEachIndexed { idx, hdr ->
                drawText(hdr, colX + colWidths[idx] / 2f, rowY + headerRowH / 2f + 4f * density, tableHeaderText, 10f, true)
                colX += colWidths[idx]
            }
            rowY += headerRowH

            // ── Data rows ──────────────────────────────────────────
            rows.forEachIndexed { idx, row ->
                val bg = if (idx % 2 == 0) tableRowColor else tableRowAltColor
                drawRect(color = bg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, dataRowH))
                colX = tblLeft
                row.forEachIndexed { cIdx, cell ->
                    val cellColor = when {
                        cIdx == 0 -> mainBarColor  // Mark column
                        cIdx == 6 -> {  // As Prov — color based on ≥ As Req
                            val provided = cell.toDoubleOrNull() ?: 0.0
                            val required = row[5].toDoubleOrNull() ?: 0.0
                            if (provided >= required) safeColor else unsafeColor
                        }
                        else -> tableCellText
                    }
                    drawText(cell, colX + colWidths[cIdx] / 2f, rowY + dataRowH / 2f + 4f * density, cellColor, 10f, bold = cIdx == 0)
                    colX += colWidths[cIdx]
                }
                // Row border
                drawLine(Color(0xFFBDBDBD), Offset(tblLeft, rowY + dataRowH), Offset(tblLeft + tblWidth, rowY + dataRowH), strokeWidth = 0.5f)
                rowY += dataRowH
            }

            // ── Totals row ─────────────────────────────────────────
            val totalSteelWeight = rows.sumOf { it.last().replace(",", ".").toDoubleOrNull() ?: 0.0 }
            drawRect(color = headerBg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, totalsRowH))
            drawText("Σ", tblLeft + colWidths[0] / 2f, rowY + totalsRowH / 2f + 4f * density, Color(0xFFFFD700), 12f, true)
            drawText(t("إجمالي وزن التسليح", "Total Steel Weight"), tblLeft + colWidths[0] + colWidths[1] / 2f, rowY + totalsRowH / 2f + 4f * density, Color(0xFFFFD700), 10f, true)
            // Span empty cells
            var totX = tblLeft + colWidths[0] + colWidths[1]
            for (i in 2 until colWidths.size - 1) {
                drawText("—", totX + colWidths[i] / 2f, rowY + totalsRowH / 2f + 4f * density, Color(0xFFFFD700), 10f)
                totX += colWidths[i]
            }
            val slabArea = spanX * spanY
            drawText(
                String.format("%.2f kg / %.1f m²", totalSteelWeight, slabArea),
                totX + colWidths.last() / 2f,
                rowY + totalsRowH / 2f + 4f * density,
                Color(0xFFFFD700), 10f, true
            )
            rowY += totalsRowH

            // ── Table outer border ─────────────────────────────────
            drawRect(
                color = Color(0xFF37474F), topLeft = Offset(tblLeft, tblTop),
                size = Size(tblWidth, rowY - tblTop), style = Stroke(width = 1.5f)
            )
            // Vertical separators
            var cx = tblLeft
            for (i in 0 until colWidths.size - 1) {
                cx += colWidths[i]
                drawLine(Color(0xFF90A4AE), Offset(cx, tblTop), Offset(cx, rowY), strokeWidth = 0.5f)
            }
        }
    }
}

// ── Horizontal dimension line helper ────────────────────────────
private fun DrawScope.drawDimensionLine(
    x1: Float, y1: Float, x2: Float, y2: Float, text: String, color: Color, density: Float
) {
    val tick = 6f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x1, y1 - tick), Offset(x1, y1 + tick), strokeWidth = 1f)
    drawLine(color, Offset(x2, y2 - tick), Offset(x2, y2 + tick), strokeWidth = 1f)
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.hashCode()
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        this.drawText(text, (x1 + x2) / 2f, y1 - 4f, paint)
    }
}

// ── Vertical dimension line helper ──────────────────────────────
private fun DrawScope.drawDimensionLineV(
    x1: Float, y1: Float, x2: Float, y2: Float, text: String, color: Color, density: Float
) {
    val tick = 6f
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1f)
    drawLine(color, Offset(x1 - tick, y1), Offset(x1 + tick, y1), strokeWidth = 1f)
    drawLine(color, Offset(x2 - tick, y2), Offset(x2 + tick, y2), strokeWidth = 1f)
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.hashCode()
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        save()
        rotate(-90f, x1 - 4f, (y1 + y2) / 2f)
        this.drawText(text, x1 - 4f, (y1 + y2) / 2f + 4f, paint)
        restore()
    }
}
