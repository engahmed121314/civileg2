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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.min

/**
 * Professional Slab Engineering Drawing — v4 (2026-07-27)
 *
 * CHANGES IN v4:
 * - Dark mode support: uses theme-aware [DrawingColors] palette via [drawingColors()].
 * - Distribution bars drawn in cross-section view (smaller green circles above main bars).
 * - Legend entry added for distribution bars in section view.
 *
 * PREVIOUS (v3):
 * - Uses shared DrawingUtils (drawTextAnnotated, drawHatchPattern, drawHorizontalDimension,
 *   drawVerticalDimension) instead of duplicated local helpers.
 * - Fixes lever arm: z = 0.87d (standard for slabs, was incorrectly 0.9d → unconservative).
 * - Fixes shrinkage steel ratio: now fy-dependent per ECP 203 (was hardcoded 0.0018).
 * - Arabic BIDI via shared drawTextAnnotated (StaticLayout pipeline).
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
    viewMode: Int = 0,
    modifier: Modifier = Modifier,
    momentX: Double = 0.0,
    momentY: Double = 0.0,
    factoredLoad: Double = 0.0,
    fcu: Double = 25.0,
    fy: Double = 360.0,
    isSafe: Boolean = true,
    utilizationRatio: Double = 0.0
) {
    // Obtain theme-aware palette (reacts to isSystemInDarkTheme)
    val palette = drawingColors()

    // Canvas fills the parent-constrained size from InteractiveDrawingScreen (responsive)
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        val density = this.density

        // ── Color Palette from theme-aware DrawingColors ─
        val concreteFill = palette.concreteFill
        val concreteStroke = palette.concreteStroke
        val mainBarColor = palette.rebarBlue
        val distBarColor = palette.distBarGreen
        val topBarColor = palette.unsafeRed
        val dimColor = palette.dimColor
        val textColor = palette.textOnHeader
        val headerBg = palette.tableHeaderBg
        val columnFill = palette.columnFill
        val ribFill = palette.ribFill
        val dropFill = palette.dropFill
        val shearPerimColor = palette.shearPerimColor
        val stripColor = palette.stripColor
        val safeColor = palette.safeGreen
        val unsafeColor = palette.unsafeRed
        val tableHeaderText = palette.tableHeaderText
        val tableCellText = palette.tableCellText
        val tableRowColor = palette.tableRowColor
        val tableRowAltColor = palette.tableRowAltColor

        // ── Local density-aware wrapper for drawTextAnnotated ───────
        // Shared DrawingUtils.drawTextAnnotated uses raw pixel sizes;
        // this wrapper accepts dp-like sizes (multiplied by density internally)
        // so existing call-site values remain readable.
        fun dt(
            text: String, x: Float, y: Float,
            color: Color = textColor, size: Float = 11f, bold: Boolean = false,
            center: Boolean = true
        ) = drawTextAnnotated(text, x, y, color, size * density, center = center, bold = bold)

        // ── Layout zones ─────────────────────────────────────────────
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
            0 -> h * 0.24f
            else -> h * 0.10f
        }
        val margin = 28f
        val planTop = h * 0.05f
        val planLeft = margin + 50f
        val planRight = w - margin
        val planBottom = h * 0.50f
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
        val headerColor = if (isSafe) safeColor else unsafeColor
        drawRoundRect(
            color = headerColor, topLeft = Offset(0f, 0f),
            size = Size(w, 36f), cornerRadius = CornerRadius(0f)
        )
        val statusLabel = if (isSafe) "✓ ${"SAFE"}" else "✗ ${"UNSAFE"}"
        val headerText = "${"SLAB DETAIL"} — ${slabType.uppercase()}  •  $statusLabel  •  U=${(utilizationRatio * 100).toInt()}%"
        dt(headerText, w / 2f, 24f, textColor, 11f, bold = true)

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            // Plan background card
            drawRoundRect(
                color = palette.planBackground, topLeft = Offset(slabLeft - 6f, slabTop - 6f),
                size = Size(drawSpanX + 12f, drawSpanY + 12f), cornerRadius = CornerRadius(3f)
            )
            // Slab concrete fill
            drawRect(
                color = concreteFill,
                topLeft = Offset(slabLeft, slabTop),
                size = Size(drawSpanX, drawSpanY)
            )

            // Concrete hatching — use shared DrawingUtils
            drawHatchPattern(slabLeft, slabTop, drawSpanX, drawSpanY,
                spacing = 22f, angleDeg = 45f, color = palette.hatchColor)

            // ── Hordi / Waffle ribs & blocks ───────────────────────
            if (isHordi || isWaffle) {
                val rs = if (ribSpacing > 0) ribSpacing else 500.0
                val rw = if (ribWidth > 0) ribWidth else 100.0
                val ribStepPx = (rs / 1000.0 * scale).toFloat()
                val ribWPx = (rw / 1000.0 * scale).toFloat()
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
                    color = palette.dropFill,
                    topLeft = Offset(slabLeft, slabTop),
                    size = Size(drawSpanX, stripW), alpha = 0.35f
                )
                drawRect(
                    color = palette.dropFill,
                    topLeft = Offset(slabLeft, slabBottom - stripW),
                    size = Size(drawSpanX, stripW), alpha = 0.35f
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
                    val dpPx = (dropPanelSize / 1000.0 * scale).toFloat()
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
            val mainStepPx = (safeMainSpacing / 1000.0 * scale).toFloat().coerceAtLeast(10f)
            var my = slabTop + mainStepPx / 2f
            while (my < slabBottom - mainStepPx / 4f) {
                drawLine(
                    mainBarColor, Offset(slabLeft + 6f, my),
                    Offset(slabRight - 6f, my), strokeWidth = 2.0f
                )
                my += mainStepPx
            }
            dt("①", slabRight + 16f, slabTop + drawSpanY * 0.3f, mainBarColor, 12f, bold = true)

            // ── Distribution reinforcement (direction Y) ─────────────
            val safeDistSpacing = if (distRebarSpacing > 0) distRebarSpacing else 200.0
            val distStepPx = (safeDistSpacing / 1000.0 * scale).toFloat().coerceAtLeast(10f)
            var dx = slabLeft + distStepPx / 2f
            while (dx < slabRight - distStepPx / 4f) {
                drawLine(
                    distBarColor, Offset(dx, slabTop + 6f),
                    Offset(dx, slabBottom - 6f), strokeWidth = 1.2f
                )
                dx += distStepPx
            }
            dt("②", slabLeft - 16f, slabBottom + 18f, distBarColor, 12f, bold = true)

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
                        val dPx = (d / 1000.0 * scale).toFloat().coerceAtLeast(colSize * 0.6f)
                        val psSize = colSize + dPx
                        drawRect(
                            color = shearPerimColor,
                            topLeft = Offset(cx + colSize / 2f - psSize / 2f, cy + colSize / 2f - psSize / 2f),
                            size = Size(psSize, psSize),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f)))
                        )
                        dt("Bo", cx + colSize / 2f, cy - 8f, shearPerimColor, 10f)
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

            // Dimension lines — use shared DrawingUtils
            drawHorizontalDimension(
                slabLeft, slabRight, slabTop - 16f,
                "${(spanX * 1000).toInt()} mm", dimColor, 10f * density, offset = 0f
            )
            drawVerticalDimension(
                slabTop, slabBottom, slabLeft - 16f,
                "${(spanY * 1000).toInt()} mm", dimColor, 10f * density, offset = 0f
            )
            dt("PLAN", slabLeft + 22f, slabBottom + 18f, palette.labelGray, 10f, bold = true)
        }

        // ══════════════════════════════════════════════════════════
        //  SECTION VIEW
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val secTop = if (viewMode == 0) h * 0.52f else 44f
            val secBottom = secTop + sectionH
            val secLeft = margin + 80f
            val secRight = w - margin

            dt("SECTION A-A", secLeft, secTop - 4f, palette.labelGray, 10f, bold = true)

            val maxSectionW = secRight - secLeft - 100f
            val sectionSpanPx = min(drawSpanX, maxSectionW)
            val sectionScale = sectionSpanPx / spanX.toFloat()
            val thickPx = (slabThickness / 1000.0 * sectionScale).toFloat().coerceIn(20f, 80f)
            val sSlabLeft = secLeft + (maxSectionW - sectionSpanPx) / 2f
            val sSlabTop = secTop + (sectionH - thickPx) / 2f
            val sSlabBottom = sSlabTop + thickPx

            // Pre-compute bar radii for use in drawing and labels
            val barR = (mainRebarDia / 1000.0 * sectionScale / 2f).toFloat().coerceIn(3f, 6f)
            val safeDistDiaSec = if (distRebarDia > 0) distRebarDia else 10.0
            val safeDistSpSec = if (distRebarSpacing > 0) distRebarSpacing else 200.0
            val distBarR = (safeDistDiaSec / 1000.0 * sectionScale / 2f).toFloat().coerceIn(2f, 4.5f)

            if (isHordi || isWaffle) {
                val toppingH = (slabThickness / 1000.0 * 0.3 * sectionScale).toFloat().coerceAtLeast(12f)
                val ribH = thickPx - toppingH
                val ribWPx = (ribWidth / 1000.0 * sectionScale).toFloat().coerceAtLeast(8f)
                val ribSPx = (ribSpacing / 1000.0 * sectionScale).toFloat().coerceAtLeast(24f)
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
                val safeMainSp = if (mainRebarSpacing > 0) mainRebarSpacing else 200.0
                val barCount = ((sectionSpanPx - 20f) / (safeMainSp / 1000.0 * sectionScale).toFloat())
                    .toInt().coerceIn(3, 25)
                if (barCount > 1) {
                    val barStep = (sectionSpanPx - 20f) / (barCount - 1)
                    for (i in 0 until barCount) {
                        val bx = sSlabLeft + 10f + i * barStep
                        val by = sSlabBottom - 6f
                        drawCircle(color = mainBarColor, radius = barR, center = Offset(bx, by))
                    }
                }

                // Distribution bars ABOVE main bars at bottom (both layers at bottom per engineering practice)
                val numDistBars = ((sectionSpanPx - 20f) / (safeDistSpSec / 1000.0 * sectionScale).toFloat())
                    .toInt().coerceIn(2, 10)
                if (numDistBars > 1) {
                    val distBarStep = (sectionSpanPx - 20f) / (numDistBars - 1)
                    val distBarY = sSlabBottom - 6f - barR - distBarR - 2f  // just above main bars
                    for (i in 0 until numDistBars) {
                        val bx = sSlabLeft + 10f + i * distBarStep
                        drawCircle(color = distBarColor, radius = distBarR, center = Offset(bx, distBarY))
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
                            drawCircle(color = topBarColor, radius = topBarR, center = Offset(bx, sSlabTop + 6f))
                        }
                        for (i in 0 until topCount) {
                            val bx = sSlabLeft + sectionSpanPx - 10f - i * topBarStep
                            drawCircle(color = topBarColor, radius = topBarR, center = Offset(bx, sSlabTop + 6f))
                        }
                    }
                } else {
                    val topCount = barCount
                    if (topCount > 1) {
                        val topStep = (sectionSpanPx - 20f) / (topCount - 1)
                        for (i in 0 until topCount) {
                            val bx = sSlabLeft + 10f + i * topStep
                            drawCircle(color = topBarColor, radius = barR, center = Offset(bx, sSlabTop + 6f))
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
            val coverPx = (cover / 1000.0 * sectionScale).toFloat().coerceIn(5f, 18f)
            drawLine(
                palette.coverColor, Offset(sSlabLeft + 20f, sSlabBottom),
                Offset(sSlabLeft + 20f, sSlabBottom - coverPx), strokeWidth = 1f
            )
            dt("c = ${cover.toInt()} mm", sSlabLeft + 44f, sSlabBottom - coverPx / 2f + 4f, palette.coverColor, 9f)

            // Thickness dimension
            val dimX = sSlabLeft + sectionSpanPx + 14f
            drawLine(dimColor, Offset(dimX, sSlabTop), Offset(dimX, sSlabBottom), strokeWidth = 1.2f)
            drawLine(dimColor, Offset(dimX - 4f, sSlabTop), Offset(dimX + 4f, sSlabTop), strokeWidth = 1.2f)
            drawLine(dimColor, Offset(dimX - 4f, sSlabBottom), Offset(dimX + 4f, sSlabBottom), strokeWidth = 1.2f)
            dt("t = ${slabThickness.toInt()} mm", dimX + 26f, sSlabTop + thickPx / 2f + 4f, dimColor, 9f)

            // Bar labels
            dt("①", sSlabLeft + sectionSpanPx / 2f, sSlabBottom + 16f, mainBarColor, 10f, bold = true)
            // Distribution bar label in section
            if (!isHordi && !isWaffle) {
                val distLabelY = sSlabBottom - 6f - barR - distBarR - 2f
                dt("②", sSlabLeft + sectionSpanPx / 2f + 18f, distLabelY, distBarColor, 9f, bold = true)
            }
            if (!isCantilever && !isHordi && !isWaffle) {
                dt("③", sSlabLeft + 14f, sSlabTop - 6f, topBarColor, 10f, bold = true)
            }
            // Section markers (A-A)
            if (viewMode == 0) {
                dt("A", max(8f, slabLeft - 26f), slabTop + drawSpanY / 2f + 4f, palette.sectionLineColor, 11f, bold = true)
                dt("A", slabRight + 26f, slabTop + drawSpanY / 2f + 4f, palette.sectionLineColor, 11f, bold = true)
            }
        }

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE (data-driven, 8 columns)
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val tblTop = if (viewMode == 0) h * 0.78f else 44f
            val tblLeft = margin
            val tblRight = w - margin
            val tblWidth = tblRight - tblLeft

            // Calculate required As using CORRECT equations
            val effDepth = (slabThickness - cover).coerceAtLeast(50.0)  // mm
            // FIX: Use 0.87d lever arm (standard for slabs), was 0.9d (unconservative)
            val z = 0.87 * effDepth  // mm lever arm
            val wu = if (factoredLoad > 0) factoredLoad else 10.0  // kN/m²
            val realMx = if (momentX > 0) momentX else wu * spanX * spanX / 8.0
            val realMy = if (momentY > 0) momentY else wu * spanY * spanY / 8.0
            val mainAsReq = (realMx * 1e6 / (0.87 * fy * z)).coerceAtLeast(0.0)
            val distAsReq = (realMy * 1e6 / (0.87 * fy * z)).coerceAtLeast(0.0)
            val topAsReq = mainAsReq * 0.5
            // FIX: Shrinkage ratio now fy-dependent per ECP 203 / ACI 318
            // ρ_min = max(0.26*(√fcu/fy)^(2/3)/fy, 0.0013) simplified to:
            val shrinkageRatio = when {
                fy <= 250.0 -> 0.0020
                fy <= 360.0 -> 0.0018
                fy <= 420.0 -> 0.0014
                else -> 0.0012
            }
            val shrinkageAsReq = shrinkageRatio * 1000.0 * slabThickness

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
                "Main Bottom (X)",
                safeMainDia.toInt().toString(),
                safeMainSpacing.toInt().toString(),
                "${(mainLength * 1000).toInt()}",
                String.format("%.0f", mainAsReq),
                String.format("%.0f", mainAsProvided),
                String.format("%.1f", mainAsProvided * mainLength * 0.00785)
            ))
            rows.add(listOf(
                "②",
                "Dist. Bottom (Y)",
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
                    "Top Support",
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
                "Shrinkage §4-2-5",
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
                    "Rib Bars",
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
            val availH = tableH
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
            drawRect(color = palette.tableTitleBg, topLeft = Offset(tblLeft, rowY - 22f * density), size = Size(tblWidth, 22f * density))
            dt("REINFORCEMENT SCHEDULE", tblLeft + tblWidth / 2f, rowY - 7f * density, Color.White, 11f, bold = true)

            // ── Header row ─────────────────────────────────────────
            drawRect(color = headerBg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, headerRowH))
            val headers = listOf(
                "Mark",
                "Direction",
                "Dia",
                "Spacing",
                "Length (mm)",
                "As Req",
                "As Prov",
                "Weight (kg)"
            )
            var colX = tblLeft
            headers.forEachIndexed { idx, hdr ->
                dt(hdr, colX + colWidths[idx] / 2f, rowY + headerRowH / 2f + 4f * density, tableHeaderText, 10f, bold = true)
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
                    dt(cell, colX + colWidths[cIdx] / 2f, rowY + dataRowH / 2f + 4f * density, cellColor, 10f, bold = cIdx == 0)
                    colX += colWidths[cIdx]
                }
                // Row border
                drawLine(palette.tableRowBorder, Offset(tblLeft, rowY + dataRowH), Offset(tblLeft + tblWidth, rowY + dataRowH), strokeWidth = 0.5f)
                rowY += dataRowH
            }

            // ── Totals row ─────────────────────────────────────────
            val totalSteelWeight = rows.sumOf { it.last().replace(",", ".").toDoubleOrNull() ?: 0.0 }
            drawRect(color = headerBg, topLeft = Offset(tblLeft, rowY), size = Size(tblWidth, totalsRowH))
            dt("Σ", tblLeft + colWidths[0] / 2f, rowY + totalsRowH / 2f + 4f * density, palette.totalsGold, 12f, bold = true)
            dt("Total Steel Weight", tblLeft + colWidths[0] + colWidths[1] / 2f, rowY + totalsRowH / 2f + 4f * density, palette.totalsGold, 10f, bold = true)
            // Span empty cells
            var totX = tblLeft + colWidths[0] + colWidths[1]
            for (i in 2 until colWidths.size - 1) {
                dt("—", totX + colWidths[i] / 2f, rowY + totalsRowH / 2f + 4f * density, palette.totalsGold, 10f)
                totX += colWidths[i]
            }
            val slabArea = spanX * spanY
            dt(
                String.format("%.2f kg / %.1f m²", totalSteelWeight, slabArea),
                totX + colWidths.last() / 2f,
                rowY + totalsRowH / 2f + 4f * density,
                palette.totalsGold, 10f, bold = true
            )
            rowY += totalsRowH

            // ── Table outer border ─────────────────────────────────
            drawRect(
                color = palette.tableBorder, topLeft = Offset(tblLeft, tblTop),
                size = Size(tblWidth, rowY - tblTop), style = Stroke(width = 1.5f)
            )
            // Vertical separators
            var cx = tblLeft
            for (i in 0 until colWidths.size - 1) {
                cx += colWidths[i]
                drawLine(palette.tableColumnBorder, Offset(cx, tblTop), Offset(cx, rowY), strokeWidth = 0.5f)
            }
        }
    }
}
