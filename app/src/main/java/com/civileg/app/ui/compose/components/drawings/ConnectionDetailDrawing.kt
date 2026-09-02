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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.calculations.ecp.BoltDesignResult
import com.civileg.app.domain.calculations.ecp.WeldDesignResult
import com.civileg.app.domain.entities.BoltPattern
import kotlin.math.*

// ============================================================================
// COLOR PALETTE
// ============================================================================
private val PlateColor = Color(0xFF95A5A6)
private val PlateFill = Color(0x44444444)
private val BoltOrange = Color(0xFFF39C12)
private val BoltDark = Color(0xFF1A1A1A)
private val WeldRed = Color(0xFFE74C3C)
private val DimWhite = Color(0xFFFFFFFF)
private val ExtGray = Color(0xFFAAAAAA)
private val SafeGreen = Color(0xFF2E7D32)
private val FailRed = Color(0xFFD32F2F)
private val SectionDark = Color(0xFF2C3E50)
private val SectionGray = Color(0xFF7F8C8D)
private val BackgroundDark = Color(0xFF1E272E)
private val LabelBg = Color(0x33FFFFFF)

/**
 * Professional connection detail drawing for the Connections tab.
 * Shows bolted, welded, or combined connection geometry with accurate dimensions.
 */
@Composable
fun ConnectionDetailDrawing(
    boltResult: BoltDesignResult?,
    weldResult: WeldDesignResult?,
    connectionType: Int, // 0: bolted shear, 1: bolted moment, 2: welded, 3: combined
    isArabic: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        val cw = size.width
        val ch = size.height

        // Dark background
        drawRect(BackgroundDark, Offset.Zero, size)

        if (boltResult != null) {
            if (connectionType <= 1) {
                // Pure bolted connection
                drawBoltedConnectionDetail(0f, 0f, cw, ch, boltResult, isArabic)
            } else {
                // Combined: show bolted on left half
                drawBoltedConnectionDetail(0f, 0f, cw / 2f - 4f, ch, boltResult, isArabic)
            }
        }
        if (weldResult != null) {
            if (connectionType >= 2 && connectionType < 3) {
                // Pure welded
                drawWeldedConnectionDetail(0f, 0f, cw, ch, weldResult, isArabic)
            } else if (connectionType >= 3) {
                // Combined: show welded on right half
                drawWeldedConnectionDetail(cw / 2f + 4f, 0f, cw / 2f - 4f, ch, weldResult, isArabic)
                // Divider line
                drawLine(ExtGray.copy(alpha = 0.4f), Offset(cw / 2f, 8f), Offset(cw / 2f, ch - 8f), 1f)
            }
        }

        if (boltResult == null && weldResult == null) {
            val hint = if (isArabic) "اضغط حساب للعرض" else "Press CALCULATE to view"
            drawTextAnnotated(hint, cw / 2f, ch / 2f, ExtGray, 16f, center = true)
        }
    }
}

// ============================================================================
// BOLTED CONNECTION DETAIL
// ============================================================================
private fun DrawScope.drawBoltedConnectionDetail(
    left: Float, top: Float, right: Float, bottom: Float,
    res: BoltDesignResult, isArabic: Boolean
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = left + viewW / 2f
    val cy = top + viewH / 2f

    // ── Title ──
    val title = if (isArabic) "وصلة مساميرية" else "BOLTED CONNECTION"
    drawRect(LabelBg, Offset(left, top), Size(viewW, 28f))
    drawTextAnnotated(title, left + viewW / 2f, top + 20f, DimWhite, 13f, center = true, bold = true)

    val drawTop = top + 36f
    val drawH = viewH - 90f  // leave room for title + bottom info

    // ── Compute plate geometry ──
    val boltDia = res.boltDiameter
    val numBolts = res.numberOfBolts
    val spacing = res.boltSpacing
    val edgeDist = res.edgeDistance
    val gauge = res.gaugeDistance

    // Determine grid layout from bolt pattern and count
    val (cols, rows) = when (res.boltPattern) {
        BoltPattern.SINGLE_ROW -> 1 to numBolts
        BoltPattern.DOUBLE_ROW -> 2 to max(1, ceil(numBolts / 2.0).toInt())
        BoltPattern.STAGGERED -> 2 to max(1, ceil(numBolts / 2.0).toInt())
        BoltPattern.GRID -> {
 val c = if (numBolts >= 4) 2 else 1; c to max(1, ceil(numBolts / c.toDouble()).toInt())
        }
    }

    // Plate dimensions in real mm
    val plateW = edgeDist * 2 + (cols - 1) * gauge + boltDia * 2
    val plateH = edgeDist * 2 + (rows - 1) * spacing + boltDia * 2

    // Scale to fit
    val maxPlateW = viewW * 0.45f
    val maxPlateH = drawH * 0.65f
    val scale = min(maxPlateW / plateW.toFloat(), maxPlateH / plateH.toFloat())

    val spW = plateW.toFloat() * scale
    val spH = plateH.toFloat() * scale
    val spEdgeD = edgeDist.toFloat() * scale
    val spGauge = gauge.toFloat() * scale
    val spSpacing = spacing.toFloat() * scale
    val spBoltR = max(boltDia.toFloat() * scale / 2f, 4f)

    // Plate position (left side of drawing area, beam extends to the right)
    val plateLeft = cx - spW * 0.3f
    val plateTop = cy - spH / 2f

    // ── Draw supporting column/beam (I-beam web) ──
    val beamW = spW * 0.6f
    val beamH = spH * 1.3f
    val beamLeft = plateLeft + spW + 2f
    val beamTop = cy - beamH / 2f

    // Beam flanges
    val flangeT = beamH * 0.08f
    val webT = beamW * 0.15f

    // Top flange
    drawRect(SectionDark, Offset(beamLeft, beamTop), Size(beamW, flangeT))
    drawRect(SectionGray, Offset(beamLeft, beamTop), Size(beamW, flangeT), style = Stroke(1f))
    // Bottom flange
    drawRect(SectionDark, Offset(beamLeft, beamTop + beamH - flangeT), Size(beamW, flangeT))
    drawRect(SectionGray, Offset(beamLeft, beamTop + beamH - flangeT), Size(beamW, flangeT), style = Stroke(1f))
    // Web
    drawRect(SectionDark, Offset(beamLeft + beamW / 2f - webT / 2f, beamTop + flangeT), Size(webT, beamH - 2 * flangeT))
    drawRect(SectionGray, Offset(beamLeft + beamW / 2f - webT / 2f, beamTop + flangeT), Size(webT, beamH - 2 * flangeT), style = Stroke(1f))

    // ── Draw end/gusset plate ──
    // Plate shadow
    drawRect(Color(0x11111111), Offset(plateLeft + 2f, plateTop + 2f), Size(spW, spH))
    // Plate body
    drawRect(PlateFill, Offset(plateLeft, plateTop), Size(spW, spH))
    drawRect(PlateColor, Offset(plateLeft, plateTop), Size(spW, spH), style = Stroke(1.5f))

    // Hatch lines on plate (diagonal lines for steel plate)
    val hatchSpacing = 12f
    var hx = -spH
    while (hx <= spW) {
        val x1 = plateLeft + max(0f, hx)
        val y1 = plateTop + max(0f, -hx)
        val x2 = plateLeft + min(spW, hx + spH)
        val y2 = plateTop + min(spH, spH - (hx + spW - spW))
        val y2c = plateTop + min(spH, spH - max(0f, hx + spW - spW))
        val x2c = plateLeft + min(spW, max(0f, hx) + (plateTop + spH - y1))
        if (x1 < x2c && y1 < y2) {
            drawLine(PlateColor.copy(alpha = 0.25f), Offset(x1, y1), Offset(x2c, y2), 0.5f)
        }
        hx += hatchSpacing
    }

    // ── Draw bolts ──
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val bx = plateLeft + spEdgeD + boltDia.toFloat() * scale / 2f + c * spGauge
            val by = plateTop + spEdgeD + boltDia.toFloat() * scale / 2f + r * spSpacing

            // Staggered offset for odd rows
            val offsetX = if (res.boltPattern == BoltPattern.STAGGERED && r % 2 == 1) spGauge / 2f else 0f

            // Bolt hole (dark circle)
            drawCircle(BoltDark, spBoltR + 1.5f, center = Offset(bx + offsetX, by))
            // Bolt shank
            drawCircle(BoltOrange, spBoltR, center = Offset(bx + offsetX, by))
            // Bolt head cross
            val cr = spBoltR * 0.6f
            drawLine(BoltDark, Offset(bx + offsetX - cr, by), Offset(bx + offsetX + cr, by), 1.2f)
            drawLine(BoltDark, Offset(bx + offsetX, by - cr), Offset(bx + offsetX, by + cr), 1.2f)
        }
    }

    // ── Dimensions ──
    val dimY = plateTop + spH + 18f

    // Edge distance (left)
    val edLbl = if (isArabic) "e=${edgeDist.toInt()}" else "e=${edgeDist.toInt()}"
    drawHorizontalDimension(plateLeft, plateLeft + spEdgeD, dimY, edLbl, DimWhite, 9f, 10f)

    // Spacing (between first two bolts vertically if multiple rows)
    if (rows >= 2) {
        val spLbl = if (isArabic) "s=${spacing.toInt()}" else "s=${spacing.toInt()}"
        val dimX = plateLeft - 18f
        val by1 = plateTop + spEdgeD + boltDia.toFloat() * scale / 2f
        val by2 = by1 + spSpacing
        drawVerticalDimension(by1, by2, dimX, spLbl, DimWhite, 9f, 14f)
    }

    // Gauge (horizontal between bolt columns)
    if (cols >= 2) {
        val gLbl = if (isArabic) "g=${gauge.toInt()}" else "g=${gauge.toInt()}"
        val gDimY = plateTop - 12f
        val bx1 = plateLeft + spEdgeD + boltDia.toFloat() * scale / 2f
        val bx2 = bx1 + spGauge
        drawHorizontalDimension(bx1, bx2, gDimY, gLbl, DimWhite, 9f, 10f)
    }

    // Plate width (overall)
    val pwLbl = if (isArabic) "B=${plateW.toInt()}" else "B=${plateW.toInt()}"
    drawHorizontalDimension(plateLeft, plateLeft + spW, dimY + 18f, pwLbl, DimWhite, 9f, 12f)

    // ── Bottom info bar ──
    val infoY = bottom - 40f
    drawRect(LabelBg, Offset(left, infoY - 4f), Size(viewW, 36f))

    val gradeLbl = if (isArabic) "درجة: " else "Grade: "
    val patternLbl = if (isArabic) "نمط: " else "Pattern: "
    val boltLabel = "M${boltDia.toInt()} x $numBolts"

    drawTextAnnotated("$boltLabel  |  $gradeLbl${res.boltGrade.displayName}  |  $patternLbl${res.boltPattern.name}",
        left + viewW / 2f, infoY + 16f, DimWhite, 10f, center = true)

    // ── Status badge ──
    val statusColor = if (res.isSafe) SafeGreen else FailRed
    val statusText = if (res.isSafe) {
        if (isArabic) "آمن  UR=${"%.2f".format(res.utilizationRatio)}" else "SAFE  UR=${"%.2f".format(res.utilizationRatio)}"
    } else {
        if (isArabic) "غير آمن  UR=${"%.2f".format(res.utilizationRatio)}" else "FAIL  UR=${"%.2f".format(res.utilizationRatio)}"
    }
    drawRoundRect(statusColor.copy(alpha = 0.2f), Offset(right - 120f, top + 4f), Size(116f, 22f), cornerRadius = CornerRadius(6f))
    drawRoundRect(statusColor, Offset(right - 120f, top + 4f), Size(116f, 22f), cornerRadius = CornerRadius(6f), style = Stroke(1f))
    drawTextAnnotated(statusText, right - 62f, top + 19f, statusColor, 10f, center = true, bold = true)
}

// ============================================================================
// WELDED CONNECTION DETAIL
// ============================================================================
private fun DrawScope.drawWeldedConnectionDetail(
    left: Float, top: Float, right: Float, bottom: Float,
    res: WeldDesignResult, isArabic: Boolean
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = left + viewW / 2f
    val cy = top + viewH / 2f

    // ── Title ──
    val title = if (isArabic) "وصلة ملحومة" else "WELDED CONNECTION"
    drawRect(LabelBg, Offset(left, top), Size(viewW, 28f))
    drawTextAnnotated(title, left + viewW / 2f, top + 20f, DimWhite, 13f, center = true, bold = true)

    val drawTop = top + 36f
    val drawH = viewH - 90f

    // ── Compute geometry ──
    val weldSize = res.weldSize
    val weldLength = res.weldLength
    val throat = weldSize / sqrt(2.0)  // effective throat for fillet weld

    // Scale: show a plate-to-plate fillet weld joint
    val plateThick = weldSize * 3  // supporting plate thickness ~3x weld size
    val plateLen = weldLength * 1.4  // plate length a bit longer than weld

    val maxW = viewW * 0.7f
    val maxH = drawH * 0.6f
    val scale = min(maxW / plateLen.toFloat(), maxH / (plateThick * 3).toFloat())

    val spPlateLen = plateLen.toFloat() * scale
    val spPlateThick = plateThick.toFloat() * scale
    val spWeldSize = weldSize.toFloat() * scale
    val spWeldLen = weldLength.toFloat() * scale
    val spThroat = throat.toFloat() * scale

    // ── Vertical plate (column/beam flange) ──
    val vpLeft = cx - spPlateThick / 2f
    val vpTop = cy - spPlateLen / 2f - 10f
    val vpH = spPlateLen + 20f

    drawRect(SectionDark, Offset(vpLeft, vpTop), Size(spPlateThick, vpH))
    drawRect(SectionGray, Offset(vpLeft, vpTop), Size(spPlateThick, vpH), style = Stroke(1.5f))

    // ── Horizontal plate (gusset/bracket) — on the right of vertical plate ──
    val hpLeft = vpLeft + spPlateThick
    val hpTop = cy - spPlateThick / 2f
    val hpW = spPlateLen * 0.6f

    drawRect(SectionDark, Offset(hpLeft, hpTop), Size(hpW, spPlateThick))
    drawRect(SectionGray, Offset(hpLeft, hpTop), Size(hpW, spPlateThick), style = Stroke(1.5f))

    // ── Fillet weld triangles (along the T-junction) ──
    val weldStartY = cy - spWeldLen / 2f
    val weldEndY = cy + spWeldLen / 2f
    val junctionX = vpLeft + spPlateThick
    val junctionYtop = hpTop
    val junctionYbot = hpTop + spPlateThick

    // Top weld (horizontal fillet along top of horizontal plate)
    val numWeldSymbols = max(3, (spWeldLen / 20f).toInt().coerceIn(3, 12))
    val weldSpacing = spWeldLen / (numWeldSymbols + 1)

    for (i in 1..numWeldSymbols) {
        val wy = weldStartY + i * weldSpacing
        // Fillet weld triangle on the left side (vertical plate to horizontal plate)
        drawPath(
            path = Path().apply {
                moveTo(junctionX, wy - spWeldSize * 0.7f)
                lineTo(junctionX + spWeldSize, wy)
                lineTo(junctionX, wy + spWeldSize * 0.7f)
                close()
            },
            color = WeldRed.copy(alpha = 0.7f)
        )
    }

    // Weld line along the junction
    drawLine(WeldRed, Offset(junctionX, weldStartY), Offset(junctionX, weldEndY), 2f)

    // ── Weld symbol (standard welding symbol) ──
    val symX = junctionX + spWeldSize + 15f
    val symY = weldStartY - 10f

    // Reference line
    drawLine(DimWhite, Offset(symX - 20f, symY), Offset(symX + 20f, symY), 1.2f)
    // Arrow pointing to weld
    drawLine(DimWhite, Offset(symX - 20f, symY), Offset(junctionX + 2f, cy), 1f)
    // Arrow head
    drawPath(
        path = Path().apply {
            moveTo(junctionX + 2f, cy)
            lineTo(junctionX + 8f, cy - 3f)
            lineTo(junctionX + 8f, cy + 3f)
            close()
        },
        color = DimWhite
    )
    // Fillet weld triangle symbol
    drawPath(
        path = Path().apply {
            moveTo(symX - 8f, symY)
            lineTo(symX + 4f, symY)
            lineTo(symX - 8f, symY + 10f)
            close()
        },
        color = WeldRed
    )
    // Weld size text above reference line
    val szLbl = "${weldSize.toInt()}"
    drawTextAnnotated(szLbl, symX - 2f, symY - 8f, DimWhite, 10f, center = true, bold = true)
    // Weld length text below reference line
    val lenLbl = "${weldLength.toInt()}"
    drawTextAnnotated(lenLbl, symX - 2f, symY + 16f, DimWhite, 9f, center = true)

    // ── Throat dimension ──
    val throatLbl = if (isArabic) "a=${"%.1f".format(throat)}" else "a=${"%.1f".format(throat)}"
    val throatDimX = junctionX + spWeldSize + 8f
    val throatDimY1 = cy
    val throatDimY2 = cy + spThroat
    // Small dimension showing throat
    drawTextAnnotated(throatLbl, throatDimX + 10f, cy + spThroat / 2f + 3f, WeldRed, 9f)

    // ── Weld length dimension ──
    val wLenLbl = if (isArabic) "Lw=${weldLength.toInt()}" else "Lw=${weldLength.toInt()}"
    drawVerticalDimension(weldStartY, weldEndY, vpLeft - 18f, wLenLbl, DimWhite, 9f, 14f)

    // ── Weld size dimension ──
    val wSzLbl = if (isArabic) "S=${weldSize.toInt()}" else "S=${weldSize.toInt()}"
    drawHorizontalDimension(junctionX, junctionX + spWeldSize, weldEndY + 14f, wSzLbl, DimWhite, 9f, 10f)

    // ── Bottom info bar ──
    val infoY = bottom - 40f
    drawRect(LabelBg, Offset(left, infoY - 4f), Size(viewW, 36f))

    val typeLbl = if (isArabic) "النوع: " else "Type: "
    val elecLbl = if (isArabic) "القطب: " else "Electrode: "
    val throatAreaLbl = if (isArabic) "مساحة الحلق: " else "Throat Area: "

    drawTextAnnotated(
        "$typeLbl${res.weldType.name}  |  $elecLbl${res.electrodeType.displayName}  |  $throatAreaLbl${"%.0f".format(res.throatArea)} mm²",
        left + viewW / 2f, infoY + 16f, DimWhite, 10f, center = true
    )

    // ── Status badge ──
    val statusColor = if (res.isSafe) SafeGreen else FailRed
    val statusText = if (res.isSafe) {
        if (isArabic) "آمن  UR=${"%.2f".format(res.utilizationRatio)}" else "SAFE  UR=${"%.2f".format(res.utilizationRatio)}"
    } else {
        if (isArabic) "غير آمن  UR=${"%.2f".format(res.utilizationRatio)}" else "FAIL  UR=${"%.2f".format(res.utilizationRatio)}"
    }
    drawRoundRect(statusColor.copy(alpha = 0.2f), Offset(right - 120f, top + 4f), Size(116f, 22f), cornerRadius = CornerRadius(6f))
    drawRoundRect(statusColor, Offset(right - 120f, top + 4f), Size(116f, 22f), cornerRadius = CornerRadius(6f), style = Stroke(1f))
    drawTextAnnotated(statusText, right - 62f, top + 19f, statusColor, 10f, center = true, bold = true)
}

