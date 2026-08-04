package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// COLOR PALETTE — steel structural drawing
// ============================================================================

private val SteelGray = Color(0xFF7F8C8D)
private val SteelDarkGray = Color(0xFF2C3E50)
private val SectionFill = Color(0xFF3D3D3D)
private val BoltOrange = Color(0xFFF39C12)
private val WeldRed = Color(0xFFE74C3C)
private val PlateGray = Color(0xFF95A5A6)
private val DimWhite = Color(0xFFFFFFFF)
private val ExtGray = Color(0xFFAAAAAA)
private val TblHeaderBg = Color(0x33FFFFFF)
private val TblRowAlt = Color(0x1AFFFFFF)
private val SectionCut = Color(0xFFE74C3C)
private val StiffenerColor = Color(0xFF6C7A89)

@Composable
fun ProfessionalSteelDrawing(
    sectionType: String = "I-BEAM",
    sectionName: String = "W12x26",
    memberLength: Double = 6000.0,
    depth: Double = 310.0,
    flangeWidth: Double = 165.0,
    flangeThickness: Double = 9.7,
    webThickness: Double = 5.8,
    radius: Double = 7.6,
    area: Double = 49.1,
    ix: Double = 8550.0,
    sx: Double = 551.0,
    zx: Double = 624.0,
    weightPerMeter: Double = 38.6,
    boltDia: Double = 20.0,
    boltCount: Int = 4,
    boltGauge: Double = 90.0,
    boltPitch: Double = 75.0,
    endPlateThickness: Double = 12.0,
    hasStiffener: Boolean = false,
    weldSize: Double = 6.0,
    isColumn: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        val cw = size.width
        val ch = size.height

        val isIBeam = sectionType.contains("I-BEAM") || sectionType.contains("W-SECTION") || sectionType.contains("IPE") || sectionType.contains("HEA") || sectionType.contains("HEB")
        val isHSS = sectionType.contains("HSS") || sectionType.contains("RHS") || sectionType.contains("SHS")
        val isChannel = sectionType.contains("CHANNEL") || sectionType.contains("UPN")
        val isAngle = sectionType.contains("ANGLE") || sectionType.contains("L-SECTION")

        // ── Layout zones ──
        val margin = 24f
        val tableHeight = 110f
        val tableTop = ch - tableHeight - margin

        // Elevation view (top half)
        val elevLeft = margin + 40f
        val elevRight = cw - margin - 40f
        val elevTop = margin + 40f
        val elevBottom = ch * 0.45f

        // Cross-section and Connection (bottom half, side by side)
        val sectLeft = margin + 20f
        val sectRight = cw * 0.48f
        val sectTop = elevBottom + 40f
        val sectBottom = tableTop - 20f   // 20f gap to table

        val connLeft = cw * 0.52f
        val connRight = cw - margin - 20f
        val connTop = sectTop
        val connBottom = sectBottom

        // ════════════════════════════════════════════════════════════════════
        //  1. ELEVATION VIEW (2D Orthographic)
        // ════════════════════════════════════════════════════════════════════
        drawElevationView(
            elevLeft, elevTop, elevRight, elevBottom,
            isIBeam, isHSS, isChannel, isAngle,
            memberLength, depth, flangeWidth, flangeThickness, webThickness,
            boltDia, boltCount, boltGauge, boltPitch, endPlateThickness,
            hasStiffener, weldSize, isColumn, sectionName
        )

        // ════════════════════════════════════════════════════════════════════
        //  2. CROSS-SECTION VIEW
        // ════════════════════════════════════════════════════════════════════
        drawCrossSectionView(
            sectLeft, sectTop, sectRight, sectBottom,
            isIBeam, isHSS, isChannel, isAngle,
            depth, flangeWidth, flangeThickness, webThickness, radius,
            area, ix, sx, zx
        )

        // ════════════════════════════════════════════════════════════════════
        //  3. CONNECTION DETAIL
        // ════════════════════════════════════════════════════════════════════
        drawConnectionDetail(
            connLeft, connTop, connRight, connBottom,
            isIBeam, isHSS, isChannel, isAngle,
            depth, flangeWidth, flangeThickness, webThickness,
            boltDia, boltCount, boltGauge, boltPitch, endPlateThickness, weldSize
        )

        // ════════════════════════════════════════════════════════════════════
        //  4. PROPERTIES TABLE
        // ════════════════════════════════════════════════════════════════════
        drawPropertiesTable(
            margin, tableTop, cw - margin * 2f, tableHeight,
            sectionName, sectionType, area, ix, sx, zx, weightPerMeter, memberLength
        )

        // Labels
        drawTextAnnotated("ELEVATION", (elevLeft + elevRight)/2, elevTop - 15f, DimWhite, 16f, center = true, bold = true)
        drawTextAnnotated("SECTION A-A", (sectLeft + sectRight)/2, sectTop - 15f, DimWhite, 14f, center = true, bold = true)
        drawTextAnnotated("CONNECTION", (connLeft + connRight)/2, connTop - 15f, DimWhite, 14f, center = true, bold = true)
    }
}

private fun DrawScope.drawElevationView(
    left: Float, top: Float, right: Float, bottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    memberLength: Double, depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    hasStiffener: Boolean, weldSize: Double,
    isColumn: Boolean, sectionName: String
) {
    val viewW = right - left
    val viewH = bottom - top
    
    // Scale: slightly distorted for better visibility of depth in long beams
    val scaleX = viewW / memberLength.toFloat()
    val scaleY = (viewH * 0.4f) / depth.toFloat()
    val scale = min(scaleX, scaleY)
    
    val sLen = memberLength.toFloat() * scale
    val sDep = depth.toFloat() * scale
    val sTf = flangeThickness.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale
    
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    
    val mLeft = cx - sLen / 2f
    val mRight = cx + sLen / 2f
    val mTop = cy - sDep / 2f
    val mBot = cy + sDep / 2f

    // Draw main body (2D)
    drawRect(SteelGray, Offset(mLeft, mTop), Size(sLen, sDep))
    drawRect(Color.White.copy(alpha = 0.5f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))

    if (isIBeam || isChannel) {
        // Draw top/bottom flange lines
        drawLine(SteelDarkGray, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 1f)
        drawLine(SteelDarkGray, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), 1f)
    }

    // End Plates
    drawRect(PlateGray, Offset(mLeft - sEp, mTop - 5f), Size(sEp, sDep + 10f))
    drawRect(PlateGray, Offset(mRight, mTop - 5f), Size(sEp, sDep + 10f))

    // Dimensions
    drawHorizontalDimension(mLeft, mRight, mBot + 15f, "${memberLength.toInt()} mm", DimWhite, 12f, 15f)
    drawVerticalDimension(mTop, mBot, mRight + sEp + 10f, "d=${depth.toInt()}", DimWhite, 12f, 20f)
    
    drawTextAnnotated(sectionName, cx, mTop - 8f, Color.Cyan, 14f, center = true, bold = true)
}

private fun DrawScope.drawCrossSectionView(
    left: Float, top: Float, right: Float, bottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    radius: Double, area: Double,
    ix: Double, sx: Double, zx: Double
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    
    val scale = min((viewW * 0.6f) / flangeWidth.toFloat(), (viewH * 0.6f) / depth.toFloat())
    val sW = flangeWidth.toFloat() * scale
    val sH = depth.toFloat() * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    
    // Simple 2D Cross Section
    val scx = cx - sW / 2f
    val scy = cy - sH / 2f
    
    drawRect(SectionFill, Offset(scx, scy), Size(sW, sH))
    drawRect(SteelGray, Offset(scx, scy), Size(sW, sH), style = Stroke(1.5f))
    
    if (isIBeam) {
        // Draw I-beam internal lines
        val halfTw = sTw / 2f
        val midX = cx
        drawLine(SteelGray, Offset(midX - halfTw, scy + sTf), Offset(midX - halfTw, scy + sH - sTf), 1f)
        drawLine(SteelGray, Offset(midX + halfTw, scy + sTf), Offset(midX + halfTw, scy + sH - sTf), 1f)
    }

    drawTextAnnotated("b=$flangeWidth", cx, scy + sH + 15f, DimWhite, 11f, center = true)
    drawTextAnnotated("d=$depth", scx - 10f, cy, DimWhite, 11f, center = true, rotation = -90f)
}

private fun DrawScope.drawConnectionDetail(
    left: Float, top: Float, right: Float, bottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    weldSize: Double
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    
    val scale = min((viewW * 0.7f) / flangeWidth.toFloat(), (viewH * 0.7f) / depth.toFloat())
    val sW = flangeWidth.toFloat() * scale
    val sH = depth.toFloat() * scale
    
    // Front view of end plate with bolts
    val epx = cx - sW / 2f
    val epy = cy - sH / 2f
    
    drawRect(PlateGray.copy(alpha = 0.3f), Offset(epx, epy), Size(sW, sH))
    drawRect(PlateGray, Offset(epx, epy), Size(sW, sH), style = Stroke(1.2f))
    
    // Bolts
    val r = 4f
    for (i in 0 until boltCount) {
        val by = epy + 20f + i * (sH - 40f) / max(boltCount - 1, 1)
        drawCircle(BoltOrange, r, Offset(cx - sW * 0.25f, by))
        drawCircle(BoltOrange, r, Offset(cx + sW * 0.25f, by))
    }
}

private fun DrawScope.drawPropertiesTable(
    x: Float, y: Float, width: Float, height: Float,
    name: String, type: String, area: Double, ix: Double, sx: Double, zx: Double, w: Double, len: Double
) {
    drawRect(TblHeaderBg, Offset(x, y), Size(width, 25f))
    drawTextAnnotated("STEEL PROPERTIES: $name", x + 10f, y + 18f, DimWhite, 12f, bold = true)
    
    val rows = listOf(
        "Area: ${"%.1f".format(area / 100.0)} cm²",
        "Inertia Ix: ${"%.0f".format(ix / 1e8)} cm⁴",
        "Weight: ${"%.1f".format(w)} kg/m",
        "Length: ${"%.0f".format(len)} mm"
    )
    
    rows.forEachIndexed { i, txt ->
        drawTextAnnotated(txt, x + 10f + (i % 2) * (width/2f), y + 45f + (i/2) * 20f, ExtGray, 11f)
    }
    
    drawRect(ExtGray.copy(alpha = 0.3f), Offset(x, y), Size(width, height), style = Stroke(1f))
}
