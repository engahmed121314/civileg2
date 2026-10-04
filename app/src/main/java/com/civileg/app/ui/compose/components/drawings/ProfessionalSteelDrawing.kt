package com.civileg.app.ui.compose.components.drawings

import android.graphics.Paint
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
import android.graphics.DashPathEffect
import kotlin.math.*

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
private val WeldSymbol = Color(0xFF9B59B6)
private val GridLine = Color(0xFF34495E)
private val AxisLine = Color(0xFF7F8C8D)

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
    // NEW: Enhanced parameters for professional drawings
    show3DView: Boolean = false,
    showBMD: Boolean = false,
    showSFD: Boolean = false,
    momentData: List<Pair<Double, Double>> = emptyList(),
    shearData: List<Pair<Double, Double>> = emptyList(),
    maxMoment: Double = 0.0,
    maxShear: Double = 0.0,
    connectionType: String = "EndPlate",
    hasMomentConnection: Boolean = false,
    stiffenerCount: Int = 0,
    stiffenerThickness: Double = 0.0,
    stiffenerWidth: Double = 0.0,
    stiffenerHeight: Double = 0.0,
    isSafe: Boolean = true,
    utilizationRatio: Double = 0.0,
    designCode: String = "ECP 205",
    viewMode: Int = 0,
    resultSummary: DrawingResultSummary? = null,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        val cw = size.width
        val ch = size.height

        val isIBeam = sectionType.contains("I-BEAM", ignoreCase = true) || sectionType.contains("IPE", ignoreCase = true) || sectionType.contains("HEB", ignoreCase = true) || sectionType.contains("HEA", ignoreCase = true)
        val isHSS = sectionType.contains("HSS", ignoreCase = true) || sectionType.contains("RHS", ignoreCase = true) || sectionType.contains("SHS", ignoreCase = true) || sectionType.contains("CHS", ignoreCase = true)
        val isChannel = sectionType.contains("CHANNEL", ignoreCase = true) || sectionType.contains("UPN", ignoreCase = true) || sectionType.contains("C-SECTION", ignoreCase = true)
        val isAngle = sectionType.contains("ANGLE", ignoreCase = true) || sectionType.contains("L-SECTION", ignoreCase = true)

        // ── Layout zones ──
        val margin = 24f
        val tableHeight = 110f
        val tableTop = ch - tableHeight - margin

        val showDiagrams = showBMD || showSFD
        val elevationAreaHeight = if (showDiagrams) ch * 0.32f else ch * 0.45f
        val diagramHeight = if (showDiagrams) ch * 0.28f else 0f

        // Elevation view
        val elevLeft = margin + 40f
        val elevRight = cw - margin - 40f
        val elevTop = margin + 40f
        val elevBottom = elevTop + elevationAreaHeight

        // Diagrams (BMD/SFD) below elevation
        val diagTop = elevBottom + 15f
        val diagBottom = diagTop + diagramHeight
        val diagLeft = elevLeft
        val diagRight = elevRight

        // Cross-section and Connection
        val sectTop = (if (showDiagrams) diagBottom else elevBottom) + 30f
        val sectBottom = tableTop - 15f
        val sectLeft = margin + 20f
        val sectRight = cw * 0.48f

        val connLeft = cw * 0.52f
        val connRight = cw - margin - 20f
        val connTop = sectTop
        val connBottom = sectBottom

        when (viewMode) {
            0 -> { // ALL VIEW (Enhanced)
                // 1. ELEVATION VIEW
                drawElevationView(
                    elevLeft, elevTop, elevRight, elevBottom,
                    isIBeam, isHSS, isChannel, isAngle,
                    memberLength, depth, flangeWidth, flangeThickness, webThickness,
                    boltDia, boltCount, boltGauge, boltPitch, endPlateThickness,
                    hasStiffener, weldSize, isColumn, sectionName,
                    stiffenerCount, stiffenerThickness, stiffenerWidth, stiffenerHeight,
                    utilizationRatio, hasMomentConnection
                )

                // 1B. BENDING MOMENT & SHEAR FORCE DIAGRAMS (if enabled)
                if (showDiagrams) {
                    drawMomentShearDiagrams(
                        diagLeft, diagTop, diagRight, diagBottom,
                        memberLength, maxMoment, maxShear,
                        momentData, shearData,
                        sectionName, utilizationRatio <= 1.0
                    )
                }

                // 2. CROSS-SECTION VIEW
                drawCrossSectionView(
                    sectLeft, sectTop, sectRight, sectBottom,
                    isIBeam, isHSS, isChannel, isAngle,
                    depth, flangeWidth, flangeThickness, webThickness, radius,
                    area, ix, sx, zx
                )

                // 3. CONNECTION DETAIL
                drawConnectionDetail(
                    connLeft, connTop, connRight, connBottom,
                    isIBeam, isHSS, isChannel, isAngle,
                    depth, flangeWidth, flangeThickness, webThickness,
                    boltDia, boltCount, boltGauge, boltPitch, endPlateThickness, weldSize,
                    connectionType, hasMomentConnection, hasStiffener, stiffenerCount,
                    stiffenerThickness, stiffenerWidth, stiffenerHeight
                )

                // 4. PROPERTIES TABLE
                drawPropertiesTable(
                    margin, tableTop, cw - margin * 2f, tableHeight,
                    sectionName, sectionType, area, ix, sx, zx, weightPerMeter, memberLength,
                    utilizationRatio, designCode
                )

                // Labels
                drawTextAnnotated("ELEVATION", (elevLeft + elevRight)/2, elevTop - 15f, DimWhite, 16f, center = true, bold = true)
                if (showDiagrams) {
                    drawTextAnnotated("BMD / SFD", (diagLeft + diagRight)/2, diagTop - 15f, DimWhite, 14f, center = true, bold = true)
                }
                drawTextAnnotated("SECTION A-A", (sectLeft + sectRight)/2, sectTop - 15f, DimWhite, 14f, center = true, bold = true)
                drawTextAnnotated("CONNECTION", (connLeft + connRight)/2, connTop - 15f, DimWhite, 14f, center = true, bold = true)

                // 3D View indicator badge
                if (show3DView) {
                    drawTextAnnotated("3D ISOMETRIC", cw - 120f, margin + 30f, Color(0xFFE67E22), 12f, center = true, bold = true)
                }

                // Utilization badge
                val urColor = when {
                    utilizationRatio > 1.0 -> Color.Red
                    utilizationRatio > 0.9 -> Color(0xFFF57C00)
                    utilizationRatio > 0 -> Color(0xFF2E7D32)
                    else -> DimWhite
                }
                if (utilizationRatio > 0) {
                    drawTextAnnotated("UR: ${(utilizationRatio * 100).toInt()}%", cw - 100f, margin + 50f, urColor, 14f, center = true, bold = true)
                }
            }
            1 -> { // ELEVATION ONLY
                drawElevationView(margin + 50f, margin + 50f, cw - margin - 50f, ch - margin - 150f, isIBeam, isHSS, isChannel, isAngle, memberLength, depth, flangeWidth, flangeThickness, webThickness, boltDia, boltCount, boltGauge, boltPitch, endPlateThickness, hasStiffener, weldSize, isColumn, sectionName, 0, 0.0, 0.0, 0.0, 0.0, false)
            }
            2 -> { // CROSS SECTION ONLY
                drawCrossSectionView(margin + 100f, margin + 100f, cw - margin - 100f, ch - margin - 100f, isIBeam, isHSS, isChannel, isAngle, depth, flangeWidth, flangeThickness, webThickness, radius, area, ix, sx, zx)
            }
            3 -> { // CONNECTION ONLY
                drawConnectionDetail(margin + 100f, margin + 100f, cw - margin - 100f, ch - margin - 100f, isIBeam, isHSS, isChannel, isAngle, depth, flangeWidth, flangeThickness, webThickness, boltDia, boltCount, boltGauge, boltPitch, endPlateThickness, weldSize, connectionType, hasMomentConnection, hasStiffener, stiffenerCount, stiffenerThickness, stiffenerWidth, stiffenerHeight)
            }
        }
    }
}

private fun DrawScope.drawStatusOverlay(x: Float, y: Float, isSafe: Boolean, utilizationRatio: Double) {
    val color = if (isSafe) Color(0xFF2E7D32) else Color.Red
    drawRect(color.copy(alpha = 0.1f), Offset(x, y), Size(110f, 45f))
    drawRect(color, Offset(x, y), Size(110f, 45f), style = Stroke(1.5f))
    drawTextCentered(if (isSafe) "SAFE" else "UNSAFE", x + 55f, y + 18f, color, 12f, true)
    drawTextCentered("U.R: ${(utilizationRatio).toInt()}%", x + 55f, y + 36f, color, 12f, true)
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
    
    val scaleX = viewW / memberLength.toFloat().coerceAtLeast(1f)
    val scaleY = (viewH * 0.6f) / depth.toFloat().coerceAtLeast(1f)
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

    // Draw member body
    drawRect(SteelGray, Offset(mLeft, mTop), Size(sLen, sDep))
    drawRect(Color.White.copy(alpha = 0.5f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))

    if (isIBeam || isChannel) {
        drawLine(SteelDarkGray, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 1f)
        drawLine(SteelDarkGray, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), 1f)
    }

    // End Plates and Connections
    drawRect(PlateGray, Offset(mLeft - sEp, mTop - 5f), Size(sEp, sDep + 10f))
    drawRect(PlateGray, Offset(mRight, mTop - 5f), Size(sEp, sDep + 10f))

    // Dimension lines
    drawHorizontalDimension(mLeft, mRight, mBot + 25f, "${memberLength.toInt()} mm", DimWhite, 12f)
    drawVerticalDimension(mTop, mBot, mRight + sEp + 15f, "d=${depth.toInt()}", DimWhite, 12f)
    
    drawTextCentered(sectionName, cx, mTop - 12f, Color.Cyan, 14f, true)
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
    
    val scale = min((viewW * 0.7f) / flangeWidth.toFloat().coerceAtLeast(1f), (viewH * 0.7f) / depth.toFloat().coerceAtLeast(1f))
    val sW = flangeWidth.toFloat() * scale
    val sH = depth.toFloat() * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    
    val scx = cx - sW / 2f
    val scy = cy - sH / 2f
    
    drawRect(SectionFill, Offset(scx, scy), Size(sW, sH))
    
    if (isIBeam) {
        val path = Path().apply {
            moveTo(scx, scy)
            lineTo(scx + sW, scy)
            lineTo(scx + sW, scy + sTf)
            lineTo(scx + sW/2 + sTw/2, scy + sTf)
            lineTo(scx + sW/2 + sTw/2, scy + sH - sTf)
            lineTo(scx + sW, scy + sH - sTf)
            lineTo(scx + sW, scy + sH)
            lineTo(scx, scy + sH)
            lineTo(scx, scy + sH - sTf)
            lineTo(scx + sW/2 - sTw/2, scy + sH - sTf)
            lineTo(scx + sW/2 - sTw/2, scy + sTf)
            lineTo(scx, scy + sTf)
            close()
        }
        drawPath(path, SteelGray)
        drawPath(path, Color.White, style = Stroke(1.5f))
    } else {
        drawRect(SteelGray, Offset(scx, scy), Size(sW, sH), style = Stroke(1.5f))
    }

    drawHorizontalDimension(scx, scx + sW, scy + sH + 20f, "b=${flangeWidth.toInt()}", DimWhite, 11f)
    drawVerticalDimension(scy, scy + sH, scx - 20f, "d=${depth.toInt()}", DimWhite, 11f)
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
    
    val scale = min((viewW * 0.7f) / flangeWidth.toFloat().coerceAtLeast(1f), (viewH * 0.7f) / depth.toFloat().coerceAtLeast(1f))
    val sW = flangeWidth.toFloat() * scale
    val sH = depth.toFloat() * scale
    
    val epx = cx - sW / 2f
    val epy = cy - sH / 2f
    
    drawRect(PlateGray.copy(alpha = 0.2f), Offset(epx, epy), Size(sW, sH))
    drawRect(PlateGray, Offset(epx, epy), Size(sW, sH), style = Stroke(1.5f))
    
    // Draw bolts
    val boltRadius = (boltDia.toFloat() / 2f) * scale * 0.5f
    val boltSpacingY = (sH - 40f) / max(boltCount - 1, 1)
    for (i in 0 until boltCount) {
        val by = epy + 20f + i * boltSpacingY
        drawCircle(BoltOrange, boltRadius.coerceAtLeast(5f), Offset(cx - sW * 0.3f, by))
        drawCircle(BoltOrange, boltRadius.coerceAtLeast(5f), Offset(cx + sW * 0.3f, by))
    }
}

private fun DrawScope.drawPropertiesTable(
    x: Float, y: Float, width: Float, height: Float,
    name: String, type: String, area: Double, ix: Double, sx: Double, zx: Double, w: Double, len: Double
) {
    drawRect(TblHeaderBg, Offset(x, y), Size(width, 25f))
    drawTextCentered("PROFILE: $name  ($type)", x + 10f, y + 18f, DimWhite, 12f, true)

    val rows = listOf(
        "Area: ${"%.1f".format(area / 100.0)} cm²",
        "Inertia Ix: ${"%.0f".format(ix / 1e4)} cm⁴",
        "Modulus Zx: ${"%.0f".format(zx / 1e3)} cm³",
        "Length: ${"%.0f".format(len)} mm"
    )

    rows.forEachIndexed { i, txt ->
        val col = i % 2
        val row = i / 2
        drawTextCentered(txt, x + 15f + col * (width/2.1f), y + 50f + row * 25f, ExtGray, 11f)
    }

    drawRect(ExtGray.copy(alpha = 0.2f), Offset(x, y), Size(width, height), style = Stroke(1f))
}

// ─── Drawing Utilities ───────────────────────────────────────────────────

// Use native Android canvas for complex drawing (dashed lines, etc.)
// Compose's drawLine/drawPath only support solid lines without custom PathEffect

private fun DrawScope.drawTextCentered(
    text: String, x: Float, y: Float, textColor: Color, size: Float, bold: Boolean = false
) {
    drawContext.canvas.nativeCanvas.drawText(
        text, x, y,
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = size
            isFakeBoldText = bold
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    )
}

private fun DrawScope.drawSteelText(
    text: String, x: Float, y: Float, textColor: Color, sizePx: Float,
    center: Boolean = false, bold: Boolean = false, rotation: Float = 0f
) {
    val textPaint = android.graphics.Paint().apply {
        color = textColor.toArgb()
        textSize = sizePx
        isFakeBoldText = bold
        textAlign = if (center) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }
    if (rotation != 0f) {
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.rotate(rotation, x, y)
        drawContext.canvas.nativeCanvas.drawText(text, x, y, textPaint)
        drawContext.canvas.nativeCanvas.restore()
    } else {
        drawContext.canvas.nativeCanvas.drawText(text, x, y, textPaint)
    }
}

// Dashed line using native Android canvas
private fun DrawScope.drawDashedLine(
    color: Color, start: Offset, end: Offset, strokeWidth: Float,
    intervals: FloatArray = floatArrayOf(6f, 4f)
) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        this.strokeWidth = strokeWidth
        this.style = android.graphics.Paint.Style.STROKE
        this.isAntiAlias = true
        this.pathEffect = android.graphics.DashPathEffect(intervals, 0f)
    }
    drawContext.canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paint)
}

// Solid line using native Android canvas (more reliable than Compose's drawLine)
private fun DrawScope.drawSolidLine(
    color: Color, start: Offset, end: Offset, strokeWidth: Float
) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        this.strokeWidth = strokeWidth
        this.style = android.graphics.Paint.Style.STROKE
        this.isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paint)
}

private fun DrawScope.drawHorizontalDimension(x1: Float, x2: Float, y: Float, text: String, color: Color, fontSize: Float) {
    drawSolidLine(color.copy(alpha = 0.5f), Offset(x1, y), Offset(x2, y), 1f)
    drawSolidLine(color.copy(alpha = 0.5f), Offset(x1, y - 5f), Offset(x1, y + 5f), 1f)
    drawSolidLine(color.copy(alpha = 0.5f), Offset(x2, y - 5f), Offset(x2, y + 5f), 1f)
    drawTextCentered(text, (x1 + x2) / 2, y + 15f, color, fontSize, true)
}

private fun DrawScope.drawVerticalDimension(y1: Float, y2: Float, x: Float, text: String, color: Color, fontSize: Float) {
    drawSolidLine(color.copy(alpha = 0.5f), Offset(x, y1), Offset(x, y2), 1f)
    drawSolidLine(color.copy(alpha = 0.5f), Offset(x - 5f, y1), Offset(x + 5f, y1), 1f)
    drawSolidLine(color.copy(alpha = 0.5f), Offset(x - 5f, y2), Offset(x + 5f, y2), 1f)
    drawSteelText(text, x - 10f, (y1 + y2) / 2, color, fontSize, center = true, rotation = -90f)
}

// ==================== NEW ENHANCED DRAWING FUNCTIONS ====================

private fun DrawScope.drawElevationView(
    left: Float, top: Float, right: Float, bottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    memberLength: Double, depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    hasStiffener: Boolean, weldSize: Double,
    isColumn: Boolean, sectionName: String,
    stiffenerCount: Int = 0, stiffenerThickness: Double = 0.0,
    stiffenerWidth: Double = 0.0, stiffenerHeight: Double = 0.0,
    utilizationRatio: Double = 0.0, hasMomentConnection: Boolean = false
) {
    val viewW = right - left
    val viewH = bottom - top

    val scaleX = viewW / memberLength.toFloat().coerceAtLeast(1f)
    val scaleY = (viewH * 0.6f) / depth.toFloat().coerceAtLeast(1f)
    val scale = min(scaleX, scaleY)

    val sLen = memberLength.toFloat() * scale
    val sDep = depth.toFloat() * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale * 2f
    val sWeld: Float = max((weldSize * scale * 2f), 2.0).toFloat()

    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val mLeft = cx - sLen / 2f
    val mRight = cx + sLen / 2f
    val mTop = cy - sDep / 2f
    val mBot = cy + sDep / 2f

    // Member body
    drawRect(SteelGray, Offset(mLeft, mTop), Size(sLen, sDep))
    drawRect(Color.White.copy(alpha = 0.5f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))

    if (isIBeam || isChannel) {
        // Flange lines
        drawLine(SteelDarkGray, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), strokeWidth = 1f)
        drawLine(SteelDarkGray, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), strokeWidth = 1f)

        // Web center line (dashed)
        drawDashedLine(SteelDarkGray, Offset(cx, mTop + sTf), Offset(cx, mBot - sTf), strokeWidth = 0.5f)
    }

    // Stiffeners on web (for moment connections)
    if (stiffenerCount > 0 && hasMomentConnection) {
        val stiffW = (stiffenerWidth.toFloat() * scale).coerceAtLeast(8f)
        val stiffH = (stiffenerHeight.toFloat() * scale).coerceAtLeast(sDep * 0.3f)

        for (i in 0 until stiffenerCount) {
            val xPos = if (stiffenerCount == 1) cx
            else mLeft + sTf + (i + 1) * (sLen - 2 * sTf) / (stiffenerCount + 1)

            drawRect(StiffenerColor, Offset(xPos - stiffW/2, mTop + sTf), Size(stiffW, stiffH))
            drawRect(SteelDarkGray, Offset(xPos - stiffW/2, mTop + sTf), Size(stiffW, stiffH), style = Stroke(1f))

            // Weld symbols on stiffener (using native canvas for simplicity)
            drawLine(WeldSymbol, Offset(xPos - stiffW/2, mTop + sTf + stiffH/2), Offset(xPos - stiffW/2 - 8f, mTop + sTf + stiffH/2 - 4f), strokeWidth = 1f)
            drawLine(WeldSymbol, Offset(xPos + stiffW/2, mTop + sTf + stiffH/2), Offset(xPos + stiffW/2 + 8f, mTop + sTf + stiffH/2 - 4f), strokeWidth = 1f)
        }
    }

    // End Plates
    drawRect(PlateGray, Offset(mLeft - sEp, mTop - 8f), Size(sEp, sDep + 16f))
    drawRect(PlateGray, Offset(mRight, mTop - 8f), Size(sEp, sDep + 16f))

    // Welds on end plates
    if (weldSize > 0) {
        // Top flange welds
        drawSolidLine(WeldSymbol, Offset(mLeft - sEp, mTop), Offset(mLeft, mTop), strokeWidth = sWeld)
        drawSolidLine(WeldSymbol, Offset(mRight, mTop), Offset(mRight + sEp, mTop), strokeWidth = sWeld)
        // Bottom flange welds
        drawSolidLine(WeldSymbol, Offset(mLeft - sEp, mBot), Offset(mLeft, mBot), strokeWidth = sWeld)
        drawSolidLine(WeldSymbol, Offset(mRight, mBot), Offset(mRight + sEp, mBot), strokeWidth = sWeld)
    }

    // Bolt holes on end plates (schematic)
    val boltR = (boltDia.toFloat() / 2f * scale * 0.6f).coerceAtLeast(3f)
    val boltSpacingY = if (boltCount > 1) (sDep - 2 * sTf) / (boltCount - 1).toFloat().coerceAtLeast(1f) else 0f

    for (i in 0 until boltCount) {
        val by = mTop + sTf + i * boltSpacingY
        // Left plate
        drawCircle(Color.White, boltR, Offset(mLeft - sEp/2, by))
        drawCircle(BoltOrange, boltR * 0.7f, Offset(mLeft - sEp/2, by))
        // Right plate
        drawCircle(Color.White, boltR, Offset(mRight + sEp/2, by))
        drawCircle(BoltOrange, boltR * 0.7f, Offset(mRight + sEp/2, by))
    }

    // Dimension lines
    drawHorizontalDimension(mLeft, mRight, mBot + 25f, "${memberLength.toInt()} mm", DimWhite, 12f)
    drawVerticalDimension(mTop, mBot, mRight + sEp + 15f, "d=${depth.toInt()}", DimWhite, 12f)

    drawTextCentered(sectionName, cx, mTop - 12f, Color.Cyan, 14f, true)

    // Section type label
    val typeLabel = when {
        isIBeam -> "I-SECTION"
        isHSS -> "HSS/RHS"
        isChannel -> "CHANNEL"
        isAngle -> "ANGLE"
        else -> "STEEL"
    }
    drawTextCentered(typeLabel, cx, mBot + 45f, ExtGray, 10f)
}

// ==================== MOMENT & SHEAR DIAGRAMS ====================

private fun DrawScope.drawMomentShearDiagrams(
    left: Float, top: Float, right: Float, bottom: Float,
    memberLength: Double, maxMoment: Double, maxShear: Double,
    momentData: List<Pair<Double, Double>>,
    shearData: List<Pair<Double, Double>>,
    sectionName: String, isSafe: Boolean
) {
    val w = right - left
    val h = bottom - top
    val halfH = h / 2f
    val midY = top + halfH

    // Background
    drawRect(Color(0xFF0D1117), Offset(left, top), Size(w, h))
    drawRect(Color(0xFF333333), Offset(left, top), Size(w, h), style = Stroke(0.5f))

    drawTextCentered("BENDING MOMENT DIAGRAM (BMD) — $sectionName", left + w/2, top + 20f, Color.Cyan, 13f, true)

    // ---- BMD (top half) ----
    val bmdTop = top + 30f
    val bmdBottom = midY - 5f
    val bmdH = bmdBottom - bmdTop

    // Zero baseline
    drawSolidLine(AxisLine, Offset(left + 30f, bmdBottom), Offset(right - 30f, bmdBottom), strokeWidth = 0.8f)

    if (momentData.isNotEmpty()) {
        val maxM = momentData.maxOfOrNull { it.second }?.coerceAtLeast(0.1) ?: 1.0
        val scale = (bmdH * 0.8f) / maxM.toFloat()

        // Filled area
        val bmdPath = Path().apply {
            moveTo(left + 30f, bmdBottom)
            for (pt in momentData) {
                val px = left + 30f + (pt.first / memberLength).toFloat() * (w - 60f)
                val py = bmdBottom - pt.second.toFloat() * scale
                lineTo(px, py)
            }
            lineTo(right - 30f, bmdBottom)
            close()
        }
        drawPath(bmdPath, Color(0xFF1A3A5C))
        drawPath(bmdPath, Color(0xFF4A90D9), style = Stroke(2f))

        // Max moment label
        val maxPt = momentData.maxByOrNull { it.second }
        maxPt?.let {
            val px = left + 30f + (it.first / memberLength).toFloat() * (w - 60f)
            val py = bmdBottom - it.second.toFloat() * scale
            drawTextCentered("Mmax = ${"%.1f".format(it.second)} kN.m", px + 8f, py - 8f, Color(0xFF4A90D9), 11f, true)
            // Dashed line to baseline
            drawDashedLine(Color(0xFF4A90D9), Offset(px, py), Offset(px, bmdBottom), strokeWidth = 0.8f, intervals = floatArrayOf(4f, 3f))
        }
    }

    // ---- SFD (bottom half) ----
    val sfdTop = midY + 5f
    val sfdBottom = bottom - 15f
    val sfdH = sfdBottom - sfdTop
    val sfdCenterY = sfdTop + sfdH / 2f

    drawTextCentered("SHEAR FORCE DIAGRAM (SFD)", left + w/2, sfdTop + 18f, Color.Cyan, 13f, true)

    // Zero center line
    drawDashedLine(AxisLine, Offset(left + 30f, sfdCenterY), Offset(right - 30f, sfdCenterY), strokeWidth = 0.5f, intervals = floatArrayOf(8f, 4f))

    if (shearData.isNotEmpty()) {
        val maxV = shearData.maxOfOrNull { abs(it.second) }?.coerceAtLeast(0.1) ?: 1.0
        val scale = (sfdH * 0.4f) / maxV.toFloat()

        // Positive area (above center)
        val posPath = Path().apply {
            moveTo(left + 30f, sfdCenterY)
            for (pt in shearData) {
                val px = left + 30f + (pt.first / memberLength).toFloat() * (w - 60f)
                val py = sfdCenterY - pt.second.toFloat() * scale
                lineTo(px, py.coerceIn(sfdTop + 10f, sfdBottom - 10f))
            }
            lineTo(right - 30f, sfdCenterY)
            close()
        }
        drawPath(posPath, Color(0xFF3D1111))

        // Negative area (below center)
        val negPath = Path().apply {
            moveTo(left + 30f, sfdCenterY)
            for (pt in shearData) {
                val px = left + 30f + (pt.first / memberLength).toFloat() * (w - 60f)
                val py = sfdCenterY - pt.second.toFloat() * scale
                lineTo(px, py.coerceIn(sfdTop + 10f, sfdBottom - 10f))
            }
            lineTo(right - 30f, sfdCenterY)
            close()
        }
        drawPath(negPath, Color(0xFF1A0D3A))

        // Line
        val sfdPath = Path()
        for ((i, pt) in shearData.withIndex()) {
            val px = left + 30f + (pt.first / memberLength).toFloat() * (w - 60f)
            val py = sfdCenterY - pt.second.toFloat() * scale
            if (i == 0) sfdPath.moveTo(px, py.coerceIn(sfdTop + 10f, sfdBottom - 10f))
            else sfdPath.lineTo(px, py.coerceIn(sfdTop + 10f, sfdBottom - 10f))
        }
        drawPath(sfdPath, Color(0xFFE74C3C), style = Stroke(2f))

        // Max shear label
        val maxPt = shearData.maxByOrNull { abs(it.second) }
        maxPt?.let {
            val px = left + 30f + (it.first / memberLength).toFloat() * (w - 60f)
            val py = sfdCenterY - it.second.toFloat() * scale
            drawTextCentered("Vmax = ${"%.1f".format(it.second)} kN", px + 8f, py - 8f, Color(0xFFE74C3C), 11f, true)
        }
    }

    // +V / -V labels
    drawTextCentered("+V", left + 5f, sfdTop + 15f, Color(0xFFE74C3C), 12f, true)
    drawTextCentered("-V", left + 5f, sfdBottom - 5f, Color(0xFFE74C3C), 12f, true)

    // Span dimension
    drawHorizontalDimension(left + 30f, right - 30f, bottom - 5f, "L = ${memberLength.toInt()} mm", DimWhite, 11f)
}

// ==================== UPDATED CONNECTION DETAIL ====================

private fun DrawScope.drawConnectionDetail(
    left: Float, top: Float, right: Float, bottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    weldSize: Double,
    connectionType: String = "EndPlate",
    hasMomentConnection: Boolean = false,
    hasStiffener: Boolean = false,
    stiffenerCount: Int = 0,
    stiffenerThickness: Double = 0.0,
    stiffenerWidth: Double = 0.0,
    stiffenerHeight: Double = 0.0
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val scale = min((viewW * 0.7f) / flangeWidth.toFloat().coerceAtLeast(1f), (viewH * 0.7f) / depth.toFloat().coerceAtLeast(1f))
    val sW = flangeWidth.toFloat() * scale
    val sH = depth.toFloat() * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale * 3f
    val sWeld: Float = max((weldSize * scale * 2f), 2.0).toFloat()

    val epx = cx - sW / 2f
    val epy = cy - sH / 2f

    // Connection type label
    drawTextCentered("CONNECTION: ${connectionType.uppercase()}", cx, top - 10f, Color.Cyan, 12f, true)

    // End plate
    drawRect(PlateGray.copy(alpha = 0.2f), Offset(epx - sEp, epy), Size(sW + 2 * sEp, sH))
    drawRect(PlateGray, Offset(epx - sEp, epy), Size(sW + 2 * sEp, sH), style = Stroke(1.5f))

    // Column section outline on plate
    drawRect(SteelGray.copy(alpha = 0.3f), Offset(epx, epy), Size(sW, sH))
    if (isIBeam || isChannel) {
        drawSolidLine(SteelDarkGray, Offset(epx, epy + sTf), Offset(epx + sW, epy + sTf), strokeWidth = 1f)
        drawSolidLine(SteelDarkGray, Offset(epx, epy + sH - sTf), Offset(epx + sW, epy + sH - sTf), strokeWidth = 1f)
        drawDashedLine(SteelDarkGray, Offset(cx, epy + sTf), Offset(cx, epy + sH - sTf), strokeWidth = 0.5f)
    }

    // Stiffeners on connection
    if (stiffenerCount > 0 && hasMomentConnection) {
        val stiffW = (stiffenerWidth.toFloat() * scale).coerceAtLeast(6f)
        val stiffH = (stiffenerHeight.toFloat() * scale).coerceAtLeast(sH * 0.4f)

        for (i in 0 until stiffenerCount) {
            val xPos = if (stiffenerCount == 1) cx
            else epx + sTf + (i + 1) * (sW - 2 * sTf) / (stiffenerCount + 1)

            drawRect(StiffenerColor, Offset(xPos - stiffW/2, epy + sTf), Size(stiffW, stiffH))
            drawRect(SteelDarkGray, Offset(xPos - stiffW/2, epy + sTf), Size(stiffW, stiffH), style = Stroke(1f))

            // Weld symbols
            drawLine(WeldSymbol, Offset(xPos - stiffW/2, epy + sTf + stiffH/2), Offset(xPos - stiffW/2 - 6f, epy + sTf + stiffH/2 - 3f), strokeWidth = 1f)
            drawLine(WeldSymbol, Offset(xPos + stiffW/2, epy + sTf + stiffH/2), Offset(xPos + stiffW/2 + 6f, epy + sTf + stiffH/2 - 3f), strokeWidth = 1f)
        }
    }

    // Welds around section on plate
    if (weldSize > 0) {
        // Top flange weld
        drawSolidLine(WeldSymbol, Offset(epx, epy), Offset(epx + sW, epy), strokeWidth = sWeld)
        // Bottom flange weld
        drawSolidLine(WeldSymbol, Offset(epx, epy + sH), Offset(epx + sW, epy + sH), strokeWidth = sWeld)
        // Web welds
        drawSolidLine(WeldSymbol, Offset(epx + sW/2 - sTw/2, epy + sTf), Offset(epx + sW/2 - sTw/2, epy + sH - sTf), strokeWidth = sWeld)
        drawSolidLine(WeldSymbol, Offset(epx + sW/2 + sTw/2, epy + sTf), Offset(epx + sW/2 + sTw/2, epy + sH - sTf), strokeWidth = sWeld)
    }

    // Bolts
    val boltR = (boltDia.toFloat() / 2f * scale * 0.6f).coerceAtLeast(4f)
    val boltSpacingY = if (boltCount > 1) (sH - 40f) / (boltCount - 1).toFloat().coerceAtLeast(1f) else 0f

    for (i in 0 until boltCount) {
        val by = epy + 20f + i * boltSpacingY
        // Left bolts
        drawCircle(Color.White, boltR, Offset(cx - sW * 0.3f - sEp, by))
        drawCircle(BoltOrange, boltR * 0.7f, Offset(cx - sW * 0.3f - sEp, by))
        // Right bolts
        drawCircle(Color.White, boltR, Offset(cx + sW * 0.3f + sEp, by))
        drawCircle(BoltOrange, boltR * 0.7f, Offset(cx + sW * 0.3f + sEp, by))
    }

    // Bolt labels
    drawTextCentered("${boltCount}xØ${boltDia.toInt()}", cx, epy - 15f, DimWhite, 11f, true)
    if (weldSize > 0) {
        drawTextCentered("weld=${weldSize.toInt()}mm", cx, epy + sH + 15f, WeldSymbol, 11f, true)
    }

    // Connection type specific annotations
    val connDesc = when (connectionType) {
        "EndPlate" -> "Extended End Plate"
        "FlushEndPlate" -> "Flush End Plate"
        "HeaderPlate" -> "Header Plate"
        "FinPlate" -> "Fin Plate"
        "Splice" -> "Beam Splice"
        "BasePlate" -> "Base Plate"
        else -> connectionType
    }
    drawTextCentered(connDesc, cx, epy + sH + 35f, ExtGray, 10f)
}

// ==================== UPDATED PROPERTIES TABLE ====================

private fun DrawScope.drawPropertiesTable(
    x: Float, y: Float, width: Float, height: Float,
    name: String, type: String, area: Double, ix: Double, sx: Double, zx: Double, w: Double, len: Double,
    utilizationRatio: Double = 0.0, designCode: String = "ECP 205"
) {
    drawRect(TblHeaderBg, Offset(x, y), Size(width, 28f))
    drawTextCentered("STEEL PROPERTIES: $name  ($type)", x + 10f, y + 20f, DimWhite, 13f, true)

    val col1 = x + 15f
    val col2 = x + width / 2f + 15f
    val row1 = y + 50f
    val row2 = y + 72f

    // Convert units for display
    val areaStr = if (area > 0) "%.1f".format(area / 100.0) else "--"
    val ixStr = if (ix > 0) "%.0f".format(ix / 1e4) else "--"
    val zxStr = if (zx > 0) "%.0f".format(zx / 1e3) else "--"
    val wStr = if (w > 0) "%.1f".format(w) else "--"

    drawTextCentered("Area: $areaStr cm²", col1, row1, ExtGray, 11f)
    drawTextCentered("Ix: $ixStr cm⁴", col2, row1, ExtGray, 11f)
    drawTextCentered("Zx: $zxStr cm³", col1, row2, ExtGray, 11f)
    drawTextCentered("Weight: $wStr kg/m", col2, row2, ExtGray, 11f)

    // Utilization & Code
    val urStr = if (utilizationRatio > 0) "${(utilizationRatio * 100).toInt()}%" else "--"
    drawTextCentered("Code: $designCode", x + width - 120f, row1, Color.Cyan, 11f, true)
    drawTextCentered("UR: $urStr", x + width - 120f, row2,
        if (utilizationRatio > 1.0) Color.Red else if (utilizationRatio > 0.9) Color(0xFFF57C00) else Color(0xFF2E7D32),
        12f, true)

    // Table border
    drawRect(ExtGray.copy(alpha = 0.3f), Offset(x, y), Size(width, height), style = Stroke(1f))
}
