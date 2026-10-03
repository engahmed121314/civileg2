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
 * Professional Seismic Engineering Drawing
 * Renders building elevation (stick model with lateral forces),
 * response spectrum curve, force distribution bar chart,
 * and results table with key seismic parameters.
 *
 * ECP 201-2012: seismic hazard, zone factor Z, soil type S
 * ASCE 7-22:    equivalent lateral force procedure, Section 12.8
 * IBC 2021:     Cs = Sds / (R / Ie)
 */
data class FloorForce(
    val floorIndex: Int,
    val force: Double,
    val height: Double
)

@Composable
fun ProfessionalSeismicDrawing(
    buildingHeight: Double,
    totalWeight: Double,
    baseShear: Double,
    zoneFactor: Double,
    soilFactor: Double,
    importanceFactor: Double,
    responseModFactor: Double,
    numFloors: Int,
    floorForces: List<FloorForce>,
    viewMode: Int = 0,
    designCode: DesignCode = DesignCode.ECP,
    modifier: Modifier = Modifier
) {
    // ── InputGuard: validate key dimensions before drawing ─────────────────
    InputGuard.positive("buildingHeight", buildingHeight)
    InputGuard.positive("totalWeight", totalWeight)
    InputGuard.positive("baseShear", baseShear)
    InputGuard.positive("zoneFactor", zoneFactor)
    InputGuard.positive("importanceFactor", importanceFactor)
    InputGuard.positive("responseModFactor", responseModFactor)

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
        val safeHeight = buildingHeight.coerceAtLeast(3.0)
        val safeWeight = totalWeight.coerceAtLeast(100.0)
        val safeBaseShear = baseShear.coerceAtLeast(10.0)
        val safeNumFloors = numFloors.coerceIn(1, 50)
        val safeFloors = if (floorForces.isNotEmpty()) floorForces else (1..safeNumFloors).map { i ->
            val fi = safeBaseShear * i / (safeNumFloors * (safeNumFloors + 1) / 2.0)
            FloorForce(i, fi, safeHeight * i / safeNumFloors)
        }
        val maxForce = (safeFloors.maxOfOrNull { it.force }?.coerceAtLeast(1.0)) ?: 1.0

        // ── Color Palette ──────────────────────────────────────────
        val C = DrawingColorDefaults
        val textColor = C.DimensionWhite
        val headerBg = Color(0x55333333)
        val dimColor = C.ExtensionGray
        val buildingColor = C.ConcreteGray
        val buildingFill = Color(0xFF4A4A4A)
        val forceArrowColor = C.UnsafeRed
        val baseShearColor = C.WarningOrange
        val spectrumColor = C.RebarBlue
        val barColor = C.SafeGreen
        val tableHeaderBg = Color(0x55333333)
        val hatchColor = Color(0x44AAAAAA)

        // ── Layout zones (viewMode-aware) ────────────────────────
        val margin = 30f
        val elevH = when (viewMode) { 1 -> h * 0.82f; 0 -> h * 0.32f; else -> h * 0.10f }
        val specH = when (viewMode) { 2 -> h * 0.50f; 0 -> h * 0.18f; else -> h * 0.10f }
        val barChartH = when (viewMode) { 3 -> h * 0.40f; 0 -> h * 0.18f; else -> 0f }
        val tableH = when (viewMode) { 3 -> h * 0.88f; 0 -> h * 0.25f; else -> h * 0.10f }

        val elevTop = h * 0.05f
        val elevBottom = elevTop + elevH
        val specTop = elevBottom + h * 0.02f
        val specBottom = specTop + specH
        val barTop = specBottom + h * 0.01f
        val barBottom = barTop + barChartH
        val tblTop = if (viewMode == 0 && barChartH > 0f) barBottom + h * 0.02f else specBottom + h * 0.02f

        // ══════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════
        drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(w, 40f))
        drawTextAnnotated(
            "SEISMIC ANALYSIS — $codeLabel",
            w / 2f, 27f, textColor, 13f * density, center = true, bold = true
        )

        // ══════════════════════════════════════════════════════════
        //  BUILDING ELEVATION — Stick model with lateral forces
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 1) {
            val elevLeft = margin + 80f
            val elevRight = w - margin
            val elevW = elevRight - elevLeft
            val drawH = elevH - 40f

            // Building stick
            val bldCenterX = elevLeft + elevW * 0.35f
            val bldBase = elevTop + drawH + 10f
            val bldTopY = elevTop + 20f
            val bldHeight = bldBase - bldTopY

            // Ground line
            drawLine(Color(0xFF888888), Offset(elevLeft, bldBase), Offset(elevLeft + elevW * 0.7f, bldBase), strokeWidth = 2f)
            drawHatchPattern(elevLeft, bldBase, elevW * 0.7f, 12f, spacing = 6f, angleDeg = -45f, color = C.SoilBrown.copy(alpha = 0.5f))

            // Floor levels and forces
            val floorStep = bldHeight / safeNumFloors.toFloat()
            for (i in 0..safeNumFloors) {
                val fy = bldBase - i * floorStep

                // Floor line
                val floorW = 40f + 10f * (i.toFloat() / safeNumFloors.toFloat())
                drawLine(buildingColor, Offset(bldCenterX - floorW / 2f, fy), Offset(bldCenterX + floorW / 2f, fy), strokeWidth = 2f)

                // Building outline (vertical line)
                if (i < safeNumFloors) {
                    drawLine(buildingColor, Offset(bldCenterX - floorW / 2f, fy), Offset(bldCenterX - floorW / 2f, fy - floorStep), strokeWidth = 1.5f)
                    drawLine(buildingColor, Offset(bldCenterX + floorW / 2f, fy), Offset(bldCenterX + floorW / 2f, fy - floorStep), strokeWidth = 1.5f)
                }

                // Lateral force arrow at each floor
                if (i > 0 && i <= safeFloors.size) {
                    val fi = safeFloors[i - 1]
                    val arrowLen = (fi.force / maxForce).toFloat() * 50f + 10f
                    val arrowStartX = bldCenterX + floorW / 2f + 5f
                    val arrowEndX = arrowStartX + arrowLen

                    // Arrow line
                    drawLine(forceArrowColor, Offset(arrowStartX, fy), Offset(arrowEndX, fy), strokeWidth = 1.5f)
                    // Arrow head
                    drawLine(forceArrowColor, Offset(arrowEndX, fy), Offset(arrowEndX - 4f, fy - 2.5f), strokeWidth = 1.5f)
                    drawLine(forceArrowColor, Offset(arrowEndX, fy), Offset(arrowEndX - 4f, fy + 2.5f), strokeWidth = 1.5f)

                    // Force label
                    drawTextAnnotated("F${i}=${fi.force.toInt()}", arrowEndX + 4f, fy + 3f, forceArrowColor, 7f * density)
                }
            }

            // Base shear arrow (large, at base)
            val vbArrowLen = 70f
            val vbStartX = bldCenterX - 20f
            val vbEndX = vbStartX - vbArrowLen
            drawLine(baseShearColor, Offset(vbStartX, bldBase), Offset(vbEndX, bldBase), strokeWidth = 2.5f)
            drawLine(baseShearColor, Offset(vbEndX, bldBase), Offset(vbEndX + 5f, bldBase - 4f), strokeWidth = 2.5f)
            drawLine(baseShearColor, Offset(vbEndX, bldBase), Offset(vbEndX + 5f, bldBase + 4f), strokeWidth = 2.5f)
            drawTextAnnotated("Vb=${safeBaseShear.toInt()}", vbEndX - 5f, bldBase - 8f, baseShearColor, 9f * density, bold = true)

            // Height dimension
            drawVerticalDimension(bldTopY, bldBase, bldCenterX - 40f, "H=${safeHeight.toInt()}m", dimColor, 8f * density, offset = -30f)

            // Floor labels
            for (i in 1..safeNumFloors) {
                val fy = bldBase - i * floorStep
                drawTextAnnotated("${i}F", bldCenterX - 30f, fy + 3f, dimColor, 7f * density, center = true)
            }

            drawTextAnnotated("ELEVATION", elevLeft, bldBase + 18f, dimColor, 9f * density, bold = true)
        } // end elevation view

        // ══════════════════════════════════════════════════════════
        //  RESPONSE SPECTRUM — Sa vs T curve
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 2) {
            val specLeft = margin + 80f
            val specRight = w - margin
            val specW = specRight - specLeft

            drawTextAnnotated("RESPONSE SPECTRUM", specLeft, specTop + 4f, dimColor, 9f * density, bold = true)

            val plotLeft = specLeft + 40f
            val plotRight = specRight - 20f
            val plotTop = specTop + 20f
            val plotBottom = specBottom - 20f
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            // Axes
            drawLine(dimColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 1f)
            drawLine(dimColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 1f)
            drawTextAnnotated("T (s)", plotRight - 10f, plotBottom + 14f, dimColor, 8f * density)
            drawTextAnnotated("Sa", plotLeft - 20f, plotTop + 4f, dimColor, 8f * density)

            // Spectrum curve: Sa = SDS / (R/Ie) for T > Ts; plateau for T <= Ts
            val sds = zoneFactor * soilFactor * 2.5  // simplified
            val rOverI = responseModFactor / importanceFactor
            val csMax = (sds / rOverI).toFloat()
            val ts = 0.4f / sds.toFloat().coerceAtLeast(0.1f)  // approximate Ts
            val maxT = 4.0f
            val maxSa = csMax * 1.2f

            val nPoints = 60
            val path = Path().apply {
                for (i in 0..nPoints) {
                    val t = i.toFloat() / nPoints * maxT
                    val sa = if (t <= ts) csMax else csMax * ts / t.coerceAtLeast(0.01f)
                    val px = plotLeft + (t / maxT) * plotW
                    val py = plotBottom - (sa / maxSa).coerceIn(0f, 1f) * plotH
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
            drawPath(path, color = spectrumColor, style = Stroke(width = 2f))

            // Design point marker
            val designT = ts * 1.5f
            val designSa = csMax * ts / designT.coerceAtLeast(0.01f)
            val dpX = plotLeft + (designT / maxT) * plotW
            val dpY = plotBottom - (designSa / maxSa).coerceIn(0f, 1f) * plotH
            drawCircle(spectrumColor, radius = 4f, center = Offset(dpX, dpY))
            drawTextAnnotated("Design", dpX + 8f, dpY + 3f, spectrumColor, 7f * density)

            // Cs value
            drawTextAnnotated("Cs=${String.format("%.3f", csMax)}", plotRight - 60f, plotTop + 10f, spectrumColor, 8f * density)
            drawTextAnnotated("Ts=${String.format("%.2f", ts)}s", plotLeft + plotW * (ts / maxT) + 4f, plotBottom + 14f, dimColor, 7f * density)

            // Grid lines
            for (gi in 1..4) {
                val gy = plotBottom - gi * plotH / 4f
                drawLine(Color(0x22FFFFFF), Offset(plotLeft, gy), Offset(plotRight, gy), strokeWidth = 0.5f)
            }
            for (gi in 1..4) {
                val gx = plotLeft + gi * plotW / 4f
                drawLine(Color(0x22FFFFFF), Offset(gx, plotTop), Offset(gx, plotBottom), strokeWidth = 0.5f)
                drawTextAnnotated("${gi * maxT / 4f}".take(3), gx, plotBottom + 14f, dimColor, 7f * density, center = true)
            }
        } // end spectrum view

        // ══════════════════════════════════════════════════════════
        //  FORCE DISTRIBUTION — Bar chart
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            if (barChartH > h * 0.05f) {
                val barLeft = margin + 80f
                val barRight = w - margin
                val barW = barRight - barLeft

                drawTextAnnotated("FORCE DISTRIBUTION", barLeft, barTop + 2f, dimColor, 8f * density, bold = true)

                val chartLeft = barLeft + 30f
                val chartRight = barRight - 20f
                val chartTop = barTop + 16f
                val chartBottom = barBottom - 14f
                val chartW = chartRight - chartLeft
                val chartH = chartBottom - chartTop

                // Axis
                drawLine(dimColor, Offset(chartLeft, chartBottom), Offset(chartRight, chartBottom), strokeWidth = 1f)
                drawLine(dimColor, Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), strokeWidth = 1f)

                // Bars
                val barStep = chartW / safeFloors.size.toFloat()
                val barWidthPx = barStep * 0.6f
                for ((idx, ff) in safeFloors.withIndex()) {
                    val bx = chartLeft + idx * barStep + barStep * 0.2f
                    val barH = (ff.force / maxForce).toFloat() * chartH
                    drawRect(color = barColor, topLeft = Offset(bx, chartBottom - barH), size = Size(barWidthPx, barH))
                    drawRect(color = barColor.copy(alpha = 0.8f), topLeft = Offset(bx, chartBottom - barH), size = Size(barWidthPx, barH), style = Stroke(width = 1f))
                    drawTextAnnotated("${ff.floorIndex}F", bx + barWidthPx / 2f, chartBottom + 12f, dimColor, 7f * density, center = true)
                    drawTextAnnotated("${ff.force.toInt()}", bx + barWidthPx / 2f, chartBottom - barH - 4f, textColor, 6f * density, center = true)
                }
            }
        } // end bar chart

        // ══════════════════════════════════════════════════════════
        //  RESULTS TABLE
        // ══════════════════════════════════════════════════════════
        if (viewMode == 0 || viewMode == 3) {
            val tblLeft = margin
            val tblWidth = w - 2 * margin

            val headers = listOf("Parameter", "Value", "Unit")
            val colWidths = listOf(tblWidth * 0.40f, tblWidth * 0.35f, tblWidth * 0.25f)
            val rows = listOf(
                listOf("Base Shear Vb", "${safeBaseShear.toInt()}", "kN"),
                listOf("Total Weight W", "${safeWeight.toInt()}", "kN"),
                listOf("Zone Factor Z", "${String.format("%.2f", zoneFactor)}", "—"),
                listOf("Soil Factor S", "${String.format("%.2f", soilFactor)}", "—"),
                listOf("Importance I", "${String.format("%.2f", importanceFactor)}", "—"),
                listOf("Response Mod. R", "${String.format("%.1f", responseModFactor)}", "—"),
                listOf("Building Height", "${safeHeight.toInt()}", "m"),
                listOf("Number of Floors", "$safeNumFloors", "—")
            )

            drawReinforcementTable(
                x = tblLeft, y = tblTop,
                colWidths = colWidths,
                headers = headers,
                rows = rows,
                rowHeight = 20f,
                headerHeight = 24f,
                headerBg = tableHeaderBg,
                altRowBg = Color(0x1AFFFFFF),
                textColor = textColor,
                textSize = 9f * density
            )
        } // end results table
    }
}
