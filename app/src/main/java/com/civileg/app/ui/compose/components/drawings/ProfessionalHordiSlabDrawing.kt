package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.entities.DesignCode
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

/**
 * Professional Hordi (Ribbed) Slab Engineering Drawing
 * Renders plan view (one-direction ribs with hollow blocks), section view
 * (T-section with voids), and reinforcement table.
 *
 * ECP 203-2020: Chapter 6 — ribbed/hollow-core slabs
 * ACI 318-19:  Chapter 8 — one-way joist construction
 */
@Composable
fun ProfessionalHordiSlabDrawing(
    span: Double,
    ribWidth: Double,
    ribSpacing: Double,
    totalThickness: Double,
    toppingThickness: Double,
    ribBottomDia: Double,
    ribBottomCount: Int,
    stirrupDia: Double,
    stirrupSpacing: Double,
    cover: Double,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    // ── InputGuard: validate key dimensions before drawing ─────────────────
    InputGuard.positive("span", span)
    InputGuard.positive("ribWidth", ribWidth)
    InputGuard.positive("ribSpacing", ribSpacing)
    InputGuard.positive("totalThickness", totalThickness)
    InputGuard.positive("toppingThickness", toppingThickness)
    InputGuard.positive("cover", cover)

    // ── Code-reference annotation ──────────────────────────────────────────
    val codeLabel = designCode.version

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        // ── Safety checks ─────────────────────────────────────────
        val safeSpan = span.coerceAtLeast(1.0)
        val safeRibWidth = ribWidth.coerceAtLeast(100.0)
        val safeRibSpacing = ribSpacing.coerceAtLeast(300.0)
        val safeTotalThick = totalThickness.coerceAtLeast(200.0)
        val safeTopping = toppingThickness.coerceAtLeast(50.0)
        val ribHeight = safeTotalThick - safeTopping

        // Normalize to mm
        val spanMm = if (safeSpan < 50.0) safeSpan * 1000.0 else safeSpan

        // ── Color Palette ──────────────────────────────────────────
        val C = DrawingColorDefaults
        val concreteFill = C.ConcreteFill
        val concreteStroke = C.ConcreteGray
        val toppingFill = Color(0xFF4A4A4A)
        val ribFill = Color(0xFF3D3D3D)
        val voidFill = Color(0xFF1A1A1A)
        val barBottomColor = C.RebarBlue
        val stirrupColor = C.StirrupPurple
        val dimColor = C.ExtensionGray
        val textColor = C.DimensionWhite
        val headerBg = Color(0x55333333)
        val tableHeaderBg = Color(0x55333333)
        val hatchColor = Color(0x55AAAAAA)

        // ── Layout zones (viewMode-aware) ────────────────────────
        val margin = 30f
        val planH = when (viewMode) { 1 -> h * 0.82f; 0 -> h * 0.35f; else -> h * 0.10f }
        val secH = when (viewMode) { 2 -> h * 0.50f; 0 -> h * 0.35f; else -> h * 0.10f }
        val tableH = when (viewMode) { 3 -> h * 0.88f; 0 -> h * 0.25f; else -> h * 0.10f }

        val planTop = h * 0.05f
        val planBottom = planTop + planH
        val secTop = planBottom + h * 0.02f
        val secBottom = secTop + secH
        val tblTop = secBottom + h * 0.02f

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, 40f))
        drawTextAnnotated(
            "HORDI (RIBBED) SLAB DETAIL — $codeLabel",
            w / 2f, 27f, textColor, 13f * density, center = true, bold = true
        )

        // ══════════════════════════════════════════════════════════
        //  PLAN VIEW — One-direction ribs + hollow blocks
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            val planLeft = margin + 50f
            val planRight = w - margin
            val planW = planRight - planLeft
            val planDrawH = planBottom - planTop

            val scale = min(planW / spanMm.toFloat(), planDrawH / (spanMm * 0.6).toFloat()) * 0.88f
            val drawSpan = spanMm.toFloat() * scale
            val drawWidth = (spanMm * 0.6).toFloat() * scale

            val fLeft = planLeft + (planW - drawSpan) / 2f
            val fTop = planTop + (planDrawH - drawWidth) / 2f
            val fRight = fLeft + drawSpan
            val fBottom = fTop + drawWidth
            val fCenterY = fTop + drawWidth / 2f

            // Slab outline (topping)
            drawRect(color = toppingFill, topLeft = Offset(fLeft, fTop), size = Size(drawSpan, drawWidth))
            drawHatchPattern(fLeft, fTop, drawSpan, drawWidth, spacing = 20f, angleDeg = 45f, color = hatchColor)

            // Ribs (horizontal lines spanning the length)
            val numRibs = max(3, (drawWidth / (safeRibSpacing * scale).toFloat()).toInt() + 1)
            val ribWpx = (safeRibWidth * scale).toFloat().coerceIn(4f, 30f)
            val ribStep = drawWidth / numRibs.toFloat()

            for (i in 1 until numRibs) {
                val ry = fTop + i * ribStep

                // Rib (filled rectangle)
                drawRect(
                    color = ribFill,
                    topLeft = Offset(fLeft, ry - ribWpx / 2f),
                    size = Size(drawSpan, ribWpx)
                )
                drawRect(
                    color = concreteStroke,
                    topLeft = Offset(fLeft, ry - ribWpx / 2f),
                    size = Size(drawSpan, ribWpx),
                    style = Stroke(width = 1f)
                )

                // Hollow blocks between ribs (shown as lighter voids)
                if (i > 0) {
                    val voidTop = fTop + (i - 1) * ribStep + ribWpx / 2f
                    val voidBottom = ry - ribWpx / 2f
                    val voidH = (voidBottom - voidTop).coerceAtLeast(0f)
                    if (voidH > 4f) {
                        drawRect(
                            color = voidFill,
                            topLeft = Offset(fLeft + 20f, voidTop),
                            size = Size(drawSpan - 40f, voidH)
                        )
                        // Hollow block cross pattern
                        val blockSpacing = 60f
                        for (bx in 0..((drawSpan - 40f) / blockSpacing).toInt()) {
                            val bxx = fLeft + 20f + bx * blockSpacing
                            drawLine(
                                Color(0x44666666),
                                Offset(bxx, voidTop + 2f),
                                Offset(bxx, voidBottom - 2f),
                                strokeWidth = 0.5f
                            )
                        }
                    }
                }
            }

            // Border
            drawRect(
                color = concreteStroke,
                topLeft = Offset(fLeft, fTop),
                size = Size(drawSpan, drawWidth),
                style = Stroke(width = 2.5f)
            )

            // Dimensions
            drawHorizontalDimension(fLeft, fRight, fTop, "L=${spanMm.toInt()}", dimColor, 9f * density, offset = -14f)
            drawVerticalDimension(fTop, fBottom, fLeft, "W=${(spanMm * 0.6).toInt()}", dimColor, 9f * density, offset = -14f)

            // Rib spacing
            if (numRibs > 2) {
                val r1 = fTop + ribStep
                val r2 = fTop + 2f * ribStep
                drawHorizontalDimension(
                    fRight - 40f, fRight,
                    r1, "s=${safeRibSpacing.toInt()}", C.ExtensionGray, 8f * density, offset = 14f
                )
            }

            drawTextAnnotated("PLAN", fLeft + 20f, fBottom + 22f, C.ExtensionGray, 9f * density, bold = true)

            // Section cut line
            if (viewMode == 0) {
                drawSectionCutLine(
                    x1 = fLeft - 20f, y1 = fCenterY,
                    x2 = fRight + 6f, y2 = fCenterY,
                    label = "A", color = C.SectionLine
                )
            }
        } // end plan view

        // ══════════════════════════════════════════════════════════
        //  SECTION A-A — T-section with hollow void
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val secLeft = margin + 90f
            val secRight = w - margin
            val maxSecW = secRight - secLeft

            drawTextAnnotated("SECTION A-A", secLeft - 50f, secTop + 4f, C.ExtensionGray, 9f * density, bold = true)

            // Scale section
            val ribSpacingPx = maxSecW * 0.3f
            val ribWPx = ribSpacingPx * (safeRibWidth / safeRibSpacing).toFloat().coerceIn(0.15f, 0.5f)
            val toppingPx = ribSpacingPx * (safeTopping / safeRibSpacing).toFloat().coerceIn(10f, ribSpacingPx * 0.25f)
            val ribHPx = ribSpacingPx * (ribHeight / safeRibSpacing).toFloat().coerceIn(25f, ribSpacingPx * 0.55f)
            val totalHPx = toppingPx + ribHPx

            val tCenterX = secLeft + maxSecW / 2f
            val tTop = secTop + (secH - totalHPx) / 2f + 10f
            val toppingBottom = tTop + toppingPx
            val tBottom = toppingBottom + ribHPx

            // Draw 3 rib sections with voids between
            for (idx in listOf(-1, 0, 1)) {
                val ribCx = tCenterX + idx * ribSpacingPx

                // Topping slab (flange)
                drawRect(
                    color = toppingFill,
                    topLeft = Offset(ribCx - ribSpacingPx / 2f, tTop),
                    size = Size(ribSpacingPx, toppingPx)
                )

                // Rib web
                drawRect(
                    color = ribFill,
                    topLeft = Offset(ribCx - ribWPx / 2f, toppingBottom),
                    size = Size(ribWPx, ribHPx)
                )
                drawHatchPattern(
                    ribCx - ribWPx / 2f, toppingBottom, ribWPx, ribHPx,
                    spacing = 6f, angleDeg = 45f, color = hatchColor
                )
                drawRect(
                    color = concreteStroke,
                    topLeft = Offset(ribCx - ribWPx / 2f, toppingBottom),
                    size = Size(ribWPx, ribHPx),
                    style = Stroke(width = 1.5f)
                )

                // Hollow void between ribs
                if (idx < 1) {
                    val voidLeft = ribCx + ribWPx / 2f + 2f
                    val voidRight = ribCx + ribSpacingPx - ribWPx / 2f - 2f
                    val voidW = (voidRight - voidLeft).coerceAtLeast(0f)
                    if (voidW > 6f) {
                        drawRect(
                            color = voidFill,
                            topLeft = Offset(voidLeft, toppingBottom + 2f),
                            size = Size(voidW, ribHPx - 4f)
                        )
                        // Void outline (dashed)
                        drawRect(
                            color = C.ExtensionGray,
                            topLeft = Offset(voidLeft, toppingBottom + 2f),
                            size = Size(voidW, ribHPx - 4f),
                            style = Stroke(width = 0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                        )
                        drawTextAnnotated("HOLLOW", (voidLeft + voidRight) / 2f, toppingBottom + ribHPx / 2f, C.ExtensionGray, 7f * density, center = true)
                    }
                }

                // Bottom rebar
                if (ribBottomCount > 0) {
                    val barStep = ribWPx / (ribBottomCount + 1).toFloat()
                    for (b in 1..ribBottomCount) {
                        val bx = ribCx - ribWPx / 2f + b * barStep
                        drawRebarCircle(bx, tBottom - 4f, ribBottomDia.toFloat(), 0.8f, barBottomColor)
                    }
                }

                // Stirrup indicators
                val stirStep = ribHPx / 4f
                for (s in 0..3) {
                    val sy = toppingBottom + 4f + s * stirStep
                    drawLine(
                        stirrupColor,
                        Offset(ribCx - ribWPx / 2f + 2f, sy),
                        Offset(ribCx + ribWPx / 2f - 2f, sy),
                        strokeWidth = 0.8f
                    )
                }
            }

            // Bar marks
            drawTextAnnotated("\u2460", tCenterX, tBottom + 12f, barBottomColor, 9f * density, center = true, bold = true)
            drawTextAnnotated("\u2461", tCenterX + ribSpacingPx / 2f, tBottom + 12f, stirrupColor, 9f * density, center = true, bold = true)

            // Topping outline
            drawRect(
                color = concreteStroke,
                topLeft = Offset(tCenterX - ribSpacingPx * 1.5f, tTop),
                size = Size(ribSpacingPx * 3f, toppingPx),
                style = Stroke(width = 2f)
            )

            // Dimensions
            drawVerticalDimension(tTop, tBottom, tCenterX + ribSpacingPx * 1.5f, "h=${safeTotalThick.toInt()}", dimColor, 8f * density, offset = 16f)
            drawVerticalDimension(tTop, toppingBottom, tCenterX + ribSpacingPx * 1.5f, "tf=${safeTopping.toInt()}", C.SafeGreen, 7f * density, offset = 34f)
            drawHorizontalDimension(
                tCenterX - ribWPx / 2f, tCenterX + ribWPx / 2f,
                tBottom, "bw=${safeRibWidth.toInt()}", dimColor, 8f * density, offset = 10f
            )

            // Cover
            val coverPx = 5f
            drawLine(C.SafeGreen, Offset(tCenterX - ribSpacingPx * 1.5f + 10f, tBottom), Offset(tCenterX - ribSpacingPx * 1.5f + 10f, tBottom - coverPx), strokeWidth = 1f)
            drawTextAnnotated("c=${cover.toInt()}", tCenterX - ribSpacingPx * 1.5f + 25f, tBottom - coverPx / 2f + 3f, C.SafeGreen, 7f * density)
        } // end section view

        // ══════════════════════════════════════════════════════════
        //  REINFORCEMENT TABLE
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val tblLeft = margin
            val tblWidth = w - 2 * margin

            val ribBarLen = spanMm.toInt()
            val stirLen = safeTotalThick.toInt()

            val headers = listOf("Mark", "Location", "Dia (mm)", "Count/rib", "Spacing (mm)", "Length (mm)")
            val colWidths = listOf(
                tblWidth * 0.08f, tblWidth * 0.22f, tblWidth * 0.14f,
                tblWidth * 0.14f, tblWidth * 0.20f, tblWidth * 0.22f
            )
            val rows = listOf(
                listOf("\u2460", "Rib bottom", ribBottomDia.toInt().toString(), ribBottomCount.toString(), safeRibSpacing.toInt().toString(), ribBarLen.toString()),
                listOf("\u2461", "Stirrup", stirrupDia.toInt().toString(), "—", stirrupSpacing.toInt().toString(), stirLen.toString())
            )

            drawReinforcementTable(
                x = tblLeft, y = tblTop,
                colWidths = colWidths,
                headers = headers,
                rows = rows,
                rowHeight = 22f,
                headerHeight = 26f,
                headerBg = tableHeaderBg,
                altRowBg = Color(0x1AFFFFFF),
                textColor = textColor,
                textSize = 9f * density
            )
        } // end reinforcement table
    }
}
