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
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// COLOR PALETTE — steel structural drawing
// ============================================================================

/** Steel profile main fill — DARK GRAY */
private val SteelGray = Color(0xFF7F8C8D)

/** Steel profile top face (3D highlight) */
private val SteelTopGray = Color(0xFF95A5A6)

/** Steel profile side face (3D shadow) */
private val SteelSideGray = Color(0xFF5D6D7E)

/** Cross-section fill */
private val SectionFill = Color(0xFF3D3D3D)

/** Bolt circles — ORANGE/AMBER */
private val BoltOrange = Color(0xFFF39C12)

/** Weld symbol — RED */
private val WeldRed = Color(0xFFE74C3C)

/** Connection plates */
private val PlateGray = Color(0xFF95A5A6)

/** Dimension lines and text — WHITE */
private val DimWhite = Color(0xFFFFFFFF)

/** Extension lines */
private val ExtGray = Color(0xFFAAAAAA)

/** Table header background */
private val TblHeaderBg = Color(0x33FFFFFF)

/** Table row alternate */
private val TblRowAlt = Color(0x1AFFFFFF)

/** Center line */
private val CtrLine = Color(0x4488FF88)

/** Section cut indicator */
private val SectionCut = Color(0xFFE74C3C)

/** Stiffener highlight */
private val StiffenerColor = Color(0xFF6C7A89)

// ============================================================================
// COMPOSABLE ENTRY POINT
// ============================================================================

/**
 * Professional Steel Structural Member Engineering Drawing.
 *
 * Renders:
 * - Main elevation view (left ~55%) with 3D profile, end plates, bolt groups, stiffeners
 * - Cross-section view (right-top ~35% width, 40% height) with full geometry & dimension annotations
 * - Connection detail (right-bottom) with bolt pattern, end plate, weld symbols
 * - Properties table (bottom) with section designation, A, Ix, Sx, Zx, weight
 * - Professional title block
 *
 * Supports: I-BEAM, W-SECTION, HSS, CHANNEL, ANGLE
 *
 * Background is transparent — the parent composable supplies the dark theme.
 */
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

        val isIBeam = sectionType == "I-BEAM" || sectionType == "W-SECTION"
        val isHSS = sectionType == "HSS"
        val isChannel = sectionType == "CHANNEL"
        val isAngle = sectionType == "ANGLE"

        // ── Layout zones ──
        val margin = 24f
        val tableHeight = 120f
        val tableTop = ch - tableHeight - margin

        // Elevation view (left ~55%)
        val elevLeft = margin + 50f
        val elevRight = cw * 0.55f
        val elevTop = margin + 30f
        val elevBottom = tableTop - 16f

        // Cross-section view (right-top)
        val sectLeft = elevRight + 40f
        val sectRight = cw - margin
        val sectTop = margin + 30f
        val sectBottom = elevBottom * 0.52f

        // Connection detail (right-bottom)
        val connLeft = sectLeft
        val connRight = sectRight
        val connTop = sectBottom + 30f
        val connBottom = tableTop - 16f

        // ════════════════════════════════════════════════════════════════════
        //  1. ELEVATION VIEW
        // ════════════════════════════════════════════════════════════════════
        drawElevationView(
            elevLeft = elevLeft, elevTop = elevTop,
            elevRight = elevRight, elevBottom = elevBottom,
            isIBeam = isIBeam, isHSS = isHSS, isChannel = isChannel, isAngle = isAngle,
            memberLength = memberLength, depth = depth, flangeWidth = flangeWidth,
            flangeThickness = flangeThickness, webThickness = webThickness,
            boltDia = boltDia, boltCount = boltCount, boltGauge = boltGauge,
            boltPitch = boltPitch, endPlateThickness = endPlateThickness,
            hasStiffener = hasStiffener, weldSize = weldSize,
            isColumn = isColumn, sectionName = sectionName
        )

        // Section cut indicator on elevation
        if (isColumn) {
            drawSectionCutLine(
                x1 = elevLeft - 20f, y1 = (elevTop + elevBottom) * 0.45f,
                x2 = elevRight + 10f, y2 = (elevTop + elevBottom) * 0.45f,
                label = "A"
            )
        } else {
            drawSectionCutLine(
                x1 = (elevLeft + elevRight) * 0.5f, y1 = elevTop - 15f,
                x2 = (elevLeft + elevRight) * 0.5f, y2 = elevBottom + 15f,
                label = "A"
            )
        }

        // ════════════════════════════════════════════════════════════════════
        //  2. CROSS-SECTION VIEW
        // ════════════════════════════════════════════════════════════════════
        drawCrossSectionView(
            sectLeft = sectLeft, sectTop = sectTop,
            sectRight = sectRight, sectBottom = sectBottom,
            isIBeam = isIBeam, isHSS = isHSS, isChannel = isChannel, isAngle = isAngle,
            depth = depth, flangeWidth = flangeWidth,
            flangeThickness = flangeThickness, webThickness = webThickness,
            radius = radius, area = area, ix = ix, sx = sx, zx = zx
        )

        // ════════════════════════════════════════════════════════════════════
        //  3. CONNECTION DETAIL
        // ════════════════════════════════════════════════════════════════════
        drawConnectionDetail(
            connLeft = connLeft, connTop = connTop,
            connRight = connRight, connBottom = connBottom,
            isIBeam = isIBeam, isHSS = isHSS, isChannel = isChannel, isAngle = isAngle,
            depth = depth, flangeWidth = flangeWidth,
            flangeThickness = flangeThickness, webThickness = webThickness,
            boltDia = boltDia, boltCount = boltCount, boltGauge = boltGauge,
            boltPitch = boltPitch, endPlateThickness = endPlateThickness,
            weldSize = weldSize
        )

        // ════════════════════════════════════════════════════════════════════
        //  4. PROPERTIES TABLE
        // ════════════════════════════════════════════════════════════════════
        drawPropertiesTable(
            x = margin, y = tableTop,
            width = cw - margin * 2f, height = tableHeight,
            sectionName = sectionName, sectionType = sectionType,
            area = area, ix = ix, sx = sx, zx = zx,
            weightPerMeter = weightPerMeter,
            memberLength = memberLength
        )

        // ════════════════════════════════════════════════════════════════════
        //  5. TITLE BLOCK
        // ════════════════════════════════════════════════════════════════════
        drawTitleBlock(
            x = cw - 280f, y = ch - 70f,
            width = 265f, height = 58f,
            projectName = "CivilEG",
            drawingTitle = "Steel Member - $sectionName",
            scale = "NTS",
            drawingNo = "S-001"
        )

        // ── View labels ──
        drawTextAnnotated(
            "ELEVATION", elevLeft + (elevRight - elevLeft) / 2f, elevTop - 14f,
            DimWhite, 20f, center = true, bold = true
        )
        drawTextAnnotated(
            "SECTION A-A", sectLeft + (sectRight - sectLeft) / 2f, sectTop - 14f,
            DimWhite, 20f, center = true, bold = true
        )
        drawTextAnnotated(
            "CONN. DETAIL", connLeft + (connRight - connLeft) / 2f, connTop - 14f,
            DimWhite, 20f, center = true, bold = true
        )
    }
}

// ============================================================================
// PRIVATE DRAWSCOPE EXTENSION FUNCTIONS
// ============================================================================

/**
 * Draw the main elevation view of the steel member.
 * Shows the side profile with 3D effect, end plates, bolts, and stiffeners.
 */
private fun DrawScope.drawElevationView(
    elevLeft: Float, elevTop: Float,
    elevRight: Float, elevBottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    memberLength: Double, depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    hasStiffener: Boolean, weldSize: Double,
    isColumn: Boolean, sectionName: String
) {
    val viewW = elevRight - elevLeft
    val viewH = elevBottom - elevTop
    val pad = 40f

    // Compute scale to fit member in the view
    val availableW = viewW - pad * 2
    val availableH = viewH - pad * 2

    val drawLength = if (isColumn) depth.toFloat() else memberLength.toFloat()
    val drawDepth = if (isColumn) memberLength.toFloat() else depth.toFloat()

    val scaleX = availableW / drawLength
    val scaleY = availableH / drawDepth
    val scale = min(scaleX, scaleY) * 0.80f

    val sLen = drawLength * scale
    val sDep = drawDepth * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    val sFw = flangeWidth.toFloat() * scale * 0.35f  // 3D offset
    val sBd = boltDia.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale
    val sWs = weldSize.toFloat() * scale

    // Center the member in the view
    val cx = (elevLeft + elevRight) / 2f
    val cy = (elevTop + elevBottom) / 2f

    // Member bounding box
    val memLeft = cx - sLen / 2f
    val memRight = cx + sLen / 2f
    val memTop = cy - sDep / 2f
    val memBot = cy + sDep / 2f

    // ── 3D offset direction ──
    val offset3dX = sFw
    val offset3dY = -sFw * 0.5f

    if (isIBeam || isChannel) {
        // Draw I-beam / W-Section / Channel elevation with 3D effect
        drawIBeamElevation(
            memLeft, memTop, memRight, memBot,
            sTf, sTw, offset3dX, offset3dY,
            isChannel, sEp
        )
    } else if (isHSS) {
        // Draw HSS elevation with 3D box
        drawHSSElevation(
            memLeft, memTop, memRight, memBot,
            offset3dX, offset3dY, sEp
        )
    } else if (isAngle) {
        // Draw angle elevation
        drawAngleElevation(
            memLeft, memTop, memRight, memBot,
            sTf, sTw, offset3dX, offset3dY, sEp
        )
    }

    // ── Center line ──
    if (isColumn) {
        drawCenterLine(cx, memTop - 20f, cx, memBot + 20f)
    } else {
        drawCenterLine(memLeft - 20f, cy, memRight + 20f, cy)
    }

    // ── End plates ──
    drawEndPlate(
        if (isColumn) memLeft else memLeft,
        memTop, memBot, sEp, sDep, isColumn,
        boltDia = boltDia, boltCount = boltCount,
        boltGauge = boltGauge, boltPitch = boltPitch, scale = scale,
        isLeft = true
    )
    drawEndPlate(
        if (isColumn) memRight else memRight,
        memTop, memBot, sEp, sDep, isColumn,
        boltDia = boltDia, boltCount = boltCount,
        boltGauge = boltGauge, boltPitch = boltPitch, scale = scale,
        isLeft = false
    )

    // ── Stiffeners ──
    if (hasStiffener && isIBeam) {
        drawStiffeners(
            cx, memTop, memBot, sTf, sDep, offset3dX, offset3dY
        )
    }

    // ── Weld symbols at connections ──
    if (!isHSS) {
        drawWeldSymbolVertical(memLeft - sEp - 8f, cy - 12f, sWs)
        drawWeldSymbolVertical(memRight + sEp + 8f, cy - 12f, sWs)
    }

    // ── Dimension lines ──
    if (isColumn) {
        // Horizontal dimension for depth (width of column)
        drawHorizontalDimension(
            memLeft, memRight, memBot,
            "%.0f".format(depth), DimWhite, 18f, 24f
        )
        // Vertical dimension for length
        drawVerticalDimension(
            memTop, memBot, memRight + 10f,
            "%.0f".format(memberLength), DimWhite, 18f, 24f
        )
    } else {
        // Horizontal dimension for length
        drawHorizontalDimension(
            memLeft, memRight, memBot,
            "%.0f".format(memberLength), DimWhite, 18f, 24f
        )
        // Vertical dimension for depth
        drawVerticalDimension(
            memTop, memBot, memRight + 10f,
            "d=%.0f".format(depth), DimWhite, 18f, 28f
        )
    }

    // Section name label
    drawTextAnnotated(
        sectionName, cx, memTop - 6f,
        SteelTopGray, 22f, center = true, bold = true
    )
}

/**
 * Draw I-beam / W-Section elevation profile with 3D effect.
 */
private fun DrawScope.drawIBeamElevation(
    left: Float, top: Float, right: Float, bottom: Float,
    tf: Float, tw: Float,
    offX: Float, offY: Float,
    isChannel: Boolean,
    ep: Float
) {
    val webTop = top + tf
    val webBot = bottom - tf

    // ── 3D top flange (top face parallelogram) ──
    drawPath(
        path = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right + offX, top + offY)
            lineTo(left + offX, top + offY)
            close()
        },
        color = SteelTopGray
    )

    // ── 3D side face (right side) ──
    drawPath(
        path = Path().apply {
            moveTo(right, top)
            lineTo(right + offX, top + offY)
            lineTo(right + offX, bottom + offY)
            lineTo(right, bottom)
            close()
        },
        color = SteelSideGray
    )

    // ── Front face — flanges and web ──
    // Top flange
    drawRect(
        color = SteelGray,
        topLeft = Offset(left, top),
        size = Size(right - left, tf)
    )
    // Bottom flange
    drawRect(
        color = SteelGray,
        topLeft = Offset(left, webBot),
        size = Size(right - left, tf)
    )
    // Web
    if (isChannel) {
        // Channel: web offset to one side
        val webW = max(tw * 3f, 4f)
        drawRect(
            color = SteelGray.copy(alpha = 0.85f),
            topLeft = Offset(left, webTop),
            size = Size(webW, webBot - webTop)
        )
    } else {
        drawRect(
            color = SteelGray.copy(alpha = 0.85f),
            topLeft = Offset(left, webTop),
            size = Size(right - left, webBot - webTop)
        )
    }

    // ── Outline ──
    drawRect(
        color = Color.White.copy(alpha = 0.5f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(1.2f)
    )

    // Inner flange-web lines
    drawLine(SteelSideGray, Offset(left, webTop), Offset(right, webTop), 1f)
    drawLine(SteelSideGray, Offset(left, webBot), Offset(right, webBot), 1f)
}

/**
 * Draw HSS (hollow structural section) elevation with 3D box effect.
 */
private fun DrawScope.drawHSSElevation(
    left: Float, top: Float, right: Float, bottom: Float,
    offX: Float, offY: Float, ep: Float
) {
    // Front face
    drawRect(
        color = SteelGray,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top)
    )

    // Top face (3D)
    drawPath(
        path = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right + offX, top + offY)
            lineTo(left + offX, top + offY)
            close()
        },
        color = SteelTopGray
    )

    // Right face (3D)
    drawPath(
        path = Path().apply {
            moveTo(right, top)
            lineTo(right + offX, top + offY)
            lineTo(right + offX, bottom + offY)
            lineTo(right, bottom)
            close()
        },
        color = SteelSideGray
    )

    // Outline
    drawRect(
        color = Color.White.copy(alpha = 0.5f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(1.2f)
    )

    // Dashed hollow indicator (show it's hollow)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
    val inset = 4f
    drawRect(
        color = Color.White.copy(alpha = 0.2f),
        topLeft = Offset(left + inset, top + inset),
        size = Size(right - left - inset * 2f, bottom - top - inset * 2f),
        style = Stroke(0.8f, pathEffect = dashEffect)
    )
}

/**
 * Draw angle elevation profile with 3D effect.
 */
private fun DrawScope.drawAngleElevation(
    left: Float, top: Float, right: Float, bottom: Float,
    tf: Float, tw: Float,
    offX: Float, offY: Float, ep: Float
) {
    // Angle: L-shaped cross-section. In elevation, draw as a rectangular profile
    // with the leg thickness indicated.
    val legH = (bottom - top) * 0.35f  // vertical leg height

    // Vertical leg
    drawRect(
        color = SteelGray,
        topLeft = Offset(left, top),
        size = Size(tw * 2.5f, bottom - top)
    )
    // Horizontal leg (top)
    drawRect(
        color = SteelGray,
        topLeft = Offset(left, top),
        size = Size(right - left, legH)
    )

    // Top face 3D
    drawPath(
        path = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right + offX, top + offY)
            lineTo(left + offX, top + offY)
            close()
        },
        color = SteelTopGray
    )

    // Right face 3D
    drawPath(
        path = Path().apply {
            moveTo(right, top)
            lineTo(right + offX, top + offY)
            lineTo(right + offX, bottom + offY)
            lineTo(right, bottom)
            close()
        },
        color = SteelSideGray
    )

    // Outline
    drawRect(
        color = Color.White.copy(alpha = 0.5f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(1.2f)
    )

    // Inner line showing angle profile
    drawLine(SteelSideGray, Offset(left, top + legH), Offset(right, top + legH), 1f)
    drawLine(
        SteelSideGray, Offset(left + tw * 2.5f, top),
        Offset(left + tw * 2.5f, top + legH), 1f
    )
}

/**
 * Draw an end plate with bolt holes at one end of the member.
 */
private fun DrawScope.drawEndPlate(
    plateX: Float,
    memTop: Float, memBot: Float,
    ep: Float, sDep: Float, isColumn: Boolean,
    boltDia: Double, boltCount: Int,
    boltGauge: Double, boltPitch: Double,
    scale: Float,
    isLeft: Boolean
) {
    // End plate rectangle
    val plateW = max(ep, 4f)
    val px = if (isLeft) plateX - plateW else plateX
    drawRect(
        color = PlateGray,
        topLeft = Offset(px, memTop - 4f),
        size = Size(plateW, sDep + 8f)
    )
    drawRect(
        color = Color.White.copy(alpha = 0.4f),
        topLeft = Offset(px, memTop - 4f),
        size = Size(plateW, sDep + 8f),
        style = Stroke(1f)
    )

    // Bolt holes on the end plate
    val sGauge = boltGauge.toFloat() * scale
    val sPitch = boltPitch.toFloat() * scale
    val sBd = max(boltDia.toFloat() * scale, 4f)
    val boltCx = px + plateW / 2f

    if (isColumn) {
        // Column: bolts spread horizontally across the flange width
        val cy = (memTop + memBot) / 2f
        val rows = max(boltCount / 2, 1)
        for (i in 0 until rows) {
            val yOff = (i - (rows - 1) / 2f) * sPitch
            drawBoltCircle(boltCx - sGauge / 2f, cy + yOff, sBd)
            drawBoltCircle(boltCx + sGauge / 2f, cy + yOff, sBd)
        }
    } else {
        // Beam: bolts along depth of end plate
        val boltStartY = memTop + 10f
        val usableH = sDep - 20f
        for (i in 0 until boltCount) {
            val by = if (boltCount > 1) {
                boltStartY + i * usableH / (boltCount - 1)
            } else {
                cy_center(memTop, memBot)
            }
            drawBoltCircle(boltCx, by, sBd)
        }
    }
}

/**
 * Draw a single bolt circle with cross mark.
 */
private fun DrawScope.drawBoltCircle(
    x: Float, y: Float, diameter: Float
) {
    val r = max(diameter / 2f, 3f)

    // Filled bolt circle
    drawCircle(color = BoltOrange, radius = r, center = Offset(x, y))
    // Inner cross mark
    val cr = r * 0.5f
    drawLine(Color(0xFF1A1A1A), Offset(x - cr, y), Offset(x + cr, y), 1.2f)
    drawLine(Color(0xFF1A1A1A), Offset(x, y - cr), Offset(x, y + cr), 1.2f)
    // Outer ring
    drawCircle(
        color = BoltOrange.copy(alpha = 0.7f), radius = r + 1.5f, center = Offset(x, y),
        style = Stroke(0.8f)
    )
}

/**
 * Draw stiffener plates at mid-span of I-beam.
 */
private fun DrawScope.drawStiffeners(
    cx: Float, memTop: Float, memBot: Float,
    tf: Float, sDep: Float,
    offX: Float, offY: Float
) {
    val stiffW = max(tf * 1.2f, 3f)
    val stiffH = sDep - tf * 2f
    val stiffY = memTop + tf

    // Left stiffener
    drawRect(
        color = StiffenerColor,
        topLeft = Offset(cx - stiffW - 6f, stiffY),
        size = Size(stiffW, stiffH)
    )
    drawRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(cx - stiffW - 6f, stiffY),
        size = Size(stiffW, stiffH),
        style = Stroke(0.8f)
    )

    // Right stiffener
    drawRect(
        color = StiffenerColor,
        topLeft = Offset(cx + 6f, stiffY),
        size = Size(stiffW, stiffH)
    )
    drawRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(cx + 6f, stiffY),
        size = Size(stiffW, stiffH),
        style = Stroke(0.8f)
    )

    // Label
    drawTextAnnotated(
        "STIFF.", cx, stiffY + stiffH + 14f,
        StiffenerColor, 14f, center = true
    )
}

/**
 * Draw a fillet weld symbol (triangle pointing down).
 */
private fun DrawScope.drawWeldSymbolVertical(
    x: Float, y: Float, size: Float
) {
    val s = max(size, 4f)
    drawPath(
        path = Path().apply {
            moveTo(x, y)
            lineTo(x - s, y + s * 1.5f)
            lineTo(x + s, y + s * 1.5f)
            close()
        },
        color = WeldRed
    )
    // Horizontal reference line
    drawLine(
        WeldRed, Offset(x - s * 1.5f, y), Offset(x + s * 1.5f, y), 1f
    )
    // Tail line
    drawLine(
        WeldRed, Offset(x + s * 1.5f, y), Offset(x + s * 2.2f, y - s), 1f
    )
}

/**
 * Draw the cross-section view showing full section geometry.
 */
private fun DrawScope.drawCrossSectionView(
    sectLeft: Float, sectTop: Float,
    sectRight: Float, sectBottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    radius: Double, area: Double,
    ix: Double, sx: Double, zx: Double
) {
    val viewW = sectRight - sectLeft
    val viewH = sectBottom - sectTop
    val cx = (sectLeft + sectRight) / 2f
    val cy = (sectTop + sectBottom) / 2f

    // Available drawing area (leave room for dimensions on sides)
    val dimSpace = 50f
    val availW = viewW - dimSpace * 2
    val availH = viewH - dimSpace * 1.5f

    // Determine section extents
    val secW = flangeWidth.toFloat()   // width across
    val secH = depth.toFloat()         // height across

    val scaleX = availW / secW
    val scaleY = availH / secH
    val scale = min(scaleX, scaleY) * 0.65f

    val sBf = secW * scale
    val sD = secH * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    val sR = max(radius.toFloat() * scale, 2f)

    // Section center in view
    val scx = cx
    val scy = cy - 8f

    // Background panel
    drawRect(
        Color(0x0AFFFFFF),
        topLeft = Offset(sectLeft, sectTop),
        size = Size(viewW, viewH)
    )
    drawRect(
        Color(0x33FFFFFF),
        topLeft = Offset(sectLeft, sectTop),
        size = Size(viewW, viewH),
        style = Stroke(0.8f)
    )

    if (isIBeam) {
        drawIBeamCrossSection(scx, scy, sD, sBf, sTf, sTw, sR)
    } else if (isHSS) {
        drawHSSCrossSection(scx, scy, sD, sBf, sTw)
    } else if (isChannel) {
        drawChannelCrossSection(scx, scy, sD, sBf, sTf, sTw, sR)
    } else if (isAngle) {
        drawAngleCrossSection(scx, scy, sD, sBf, sTf, sTw)
    }

    // ── Center lines ──
    drawCenterLine(scx - sBf / 2f - 14f, scy, scx + sBf / 2f + 14f, scy)
    drawCenterLine(scx, scy - sD / 2f - 14f, scx, scy + sD / 2f + 14f)

    // ── Dimension annotations ──
    // Horizontal: bf (flange width)
    drawHorizontalDimension(
        scx - sBf / 2f, scx + sBf / 2f, scy + sD / 2f + 4f,
        "bf=%.1f".format(flangeWidth), DimWhite, 14f, 20f
    )
    // Vertical: d (depth)
    drawVerticalDimension(
        scy - sD / 2f, scy + sD / 2f, scx + sBf / 2f + 4f,
        "d=%.0f".format(depth), DimWhite, 14f, 22f
    )

    // tf and tw callout
    if (isIBeam || isChannel) {
        // tf label at top flange
        drawTextAnnotated(
            "tf=%.1f".format(flangeThickness),
            scx + sBf / 4f + 10f, scy - sD / 2f + sTf / 2f + 4f,
            Color(0xFFAADDFF), 13f
        )
        // tw label at web
        drawTextAnnotated(
            "tw=%.1f".format(webThickness),
            scx + 6f, scy + 4f,
            Color(0xFFAADDFF), 13f
        )
    }

    // ── Section properties mini-table ──
    drawSectionPropsMiniTable(
        sectLeft + 8f, sectBottom - 80f,
        area, ix, sx, zx
    )
}

/**
 * Draw I-beam cross-section with fillets.
 */
private fun DrawScope.drawIBeamCrossSection(
    cx: Float, cy: Float,
    d: Float, bf: Float, tf: Float, tw: Float, r: Float
) {
    val halfBf = bf / 2f
    val halfD = d / 2f
    val halfTw = tw / 2f
    val webTop = cy - halfD + tf
    val webBot = cy + halfD - tf

    // Fill entire section
    drawPath(
        path = Path().apply {
            moveTo(cx - halfBf, cy - halfD)
            lineTo(cx + halfBf, cy - halfD)
            lineTo(cx + halfBf, cy - halfD + tf)
            lineTo(cx + halfTw + r, cy - halfD + tf)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    cx + halfTw, cy - halfD + tf,
                    cx + halfTw + r * 2f, cy - halfD + tf + r * 2f
                ),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(cx + halfTw, webBot - r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    cx + halfTw, webBot - r * 2f,
                    cx + halfTw + r * 2f, webBot
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(cx + halfBf, webBot)
            lineTo(cx + halfBf, cy + halfD)
            lineTo(cx - halfBf, cy + halfD)
            lineTo(cx - halfBf, webBot)
            lineTo(cx - halfTw - r, webBot)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    cx - halfTw - r * 2f, webBot - r * 2f,
                    cx - halfTw, webBot
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(cx - halfTw, cy - halfD + tf + r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    cx - halfTw - r * 2f, cy - halfD + tf,
                    cx - halfTw, cy - halfD + tf + r * 2f
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(cx - halfBf, cy - halfD + tf)
            close()
        },
        color = SectionFill
    )

    // Outline
    drawPath(
        path = Path().apply {
            moveTo(cx - halfBf, cy - halfD)
            lineTo(cx + halfBf, cy - halfD)
            lineTo(cx + halfBf, cy + halfD)
            lineTo(cx - halfBf, cy + halfD)
            close()
        },
        color = SteelGray,
        style = Stroke(1.5f)
    )

    // Flange-web junction lines
    drawLine(
        SteelGray, Offset(cx - halfTw, webTop), Offset(cx + halfTw, webTop), 1f
    )
    drawLine(
        SteelGray, Offset(cx - halfTw, webBot), Offset(cx + halfTw, webBot), 1f
    )
    // Web vertical lines
    drawLine(
        SteelGray, Offset(cx - halfTw, webTop), Offset(cx - halfTw, webBot), 1f
    )
    drawLine(
        SteelGray, Offset(cx + halfTw, webTop), Offset(cx + halfTw, webBot), 1f
    )
}

/**
 * Draw HSS (rectangular) cross-section.
 */
private fun DrawScope.drawHSSCrossSection(
    cx: Float, cy: Float, d: Float, bf: Float, tw: Float
) {
    val halfBf = bf / 2f
    val halfD = d / 2f
    val halfTw = tw / 2f

    // Outer rectangle
    drawRect(
        color = SectionFill,
        topLeft = Offset(cx - halfBf, cy - halfD),
        size = Size(bf, d)
    )
    // Inner hollow (dark cut-out)
    drawRect(
        color = Color(0xFF1A1A1A),
        topLeft = Offset(cx - halfBf + halfTw, cy - halfD + halfTw),
        size = Size(bf - tw, d - tw)
    )
    // Outer outline
    drawRect(
        color = SteelGray,
        topLeft = Offset(cx - halfBf, cy - halfD),
        size = Size(bf, d),
        style = Stroke(1.5f)
    )
    // Inner outline
    drawRect(
        color = SteelGray.copy(alpha = 0.5f),
        topLeft = Offset(cx - halfBf + halfTw, cy - halfD + halfTw),
        size = Size(bf - tw, d - tw),
        style = Stroke(1f)
    )
}

/**
 * Draw channel (C-shape) cross-section.
 */
private fun DrawScope.drawChannelCrossSection(
    cx: Float, cy: Float,
    d: Float, bf: Float, tf: Float, tw: Float, r: Float
) {
    val halfD = d / 2f
    val webLeft = cx - bf / 2f
    val webRight = webLeft + tw
    val webTop = cy - halfD + tf
    val webBot = cy + halfD - tf

    // Fill
    drawPath(
        path = Path().apply {
            // Start top-left of top flange
            moveTo(webLeft, cy - halfD)
            lineTo(webLeft + bf, cy - halfD)
            lineTo(webLeft + bf, cy - halfD + tf)
            lineTo(webRight + r, cy - halfD + tf)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    webRight, cy - halfD + tf,
                    webRight + r * 2f, cy - halfD + tf + r * 2f
                ),
                startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false
            )
            lineTo(webRight, webBot - r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    webRight, webBot - r * 2f,
                    webRight + r * 2f, webBot
                ),
                startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false
            )
            lineTo(webLeft + bf, webBot)
            lineTo(webLeft, webBot)
            lineTo(webLeft, cy + halfD)
            lineTo(webLeft, cy - halfD)
            close()
        },
        color = SectionFill
    )

    // Outline
    drawPath(
        path = Path().apply {
            moveTo(webLeft, cy - halfD)
            lineTo(webLeft + bf, cy - halfD)
            lineTo(webLeft + bf, cy + halfD)
            lineTo(webLeft, cy + halfD)
            close()
        },
        color = SteelGray,
        style = Stroke(1.5f)
    )

    // Flange-web lines
    drawLine(SteelGray, Offset(webRight, webTop), Offset(webLeft + bf, webTop), 1f)
    drawLine(SteelGray, Offset(webRight, webBot), Offset(webLeft + bf, webBot), 1f)
    drawLine(SteelGray, Offset(webRight, webTop), Offset(webRight, webBot), 1f)
}

/**
 * Draw angle (L-shape) cross-section.
 */
private fun DrawScope.drawAngleCrossSection(
    cx: Float, cy: Float,
    d: Float, bf: Float, tf: Float, tw: Float
) {
    val halfBf = bf / 2f
    val halfD = d / 2f
    val legW = tw * 3f  // scale up for visibility

    // Vertical leg
    drawRect(
        color = SectionFill,
        topLeft = Offset(cx - halfBf, cy - halfD),
        size = Size(legW, d)
    )
    // Horizontal leg
    drawRect(
        color = SectionFill,
        topLeft = Offset(cx - halfBf, cy - halfD),
        size = Size(bf, legW)
    )

    // Outlines
    drawPath(
        path = Path().apply {
            moveTo(cx - halfBf, cy - halfD)
            lineTo(cx - halfBf + bf, cy - halfD)
            lineTo(cx - halfBf + bf, cy - halfD + legW)
            lineTo(cx - halfBf + legW, cy - halfD + legW)
            lineTo(cx - halfBf + legW, cy + halfD)
            lineTo(cx - halfBf, cy + halfD)
            close()
        },
        color = SteelGray,
        style = Stroke(1.5f)
    )
}

/**
 * Mini table showing section properties (A, Ix, Sx, Zx).
 */
private fun DrawScope.drawSectionPropsMiniTable(
    x: Float, y: Float,
    area: Double, ix: Double, sx: Double, zx: Double
) {
    val colW = 80f
    val rowH = 18f
    val headerH = 20f

    // Header
    drawRect(TblHeaderBg, Offset(x, y), Size(colW * 2f, headerH))
    drawTextAnnotated("Property", x + 4f, y + headerH / 2f - 5f, DimWhite, 13f, bold = true)
    drawTextAnnotated("Value", x + colW + 4f, y + headerH / 2f - 5f, DimWhite, 13f, bold = true)

    // Rows
    val props = listOf(
        "A" to "%.1f cm²".format(area),
        "Ix" to "%.0f cm⁴".format(ix),
        "Sx" to "%.0f cm³".format(sx),
        "Zx" to "%.0f cm³".format(zx)
    )

    props.forEachIndexed { i, (name, value) ->
        val ry = y + headerH + i * rowH
        if (i % 2 == 1) {
            drawRect(TblRowAlt, Offset(x, ry), Size(colW * 2f, rowH))
        }
        drawTextAnnotated(name, x + 4f, ry + rowH / 2f - 4f, ExtGray, 12f)
        drawTextAnnotated(value, x + colW + 4f, ry + rowH / 2f - 4f, DimWhite, 12f)
        drawLine(ExtGray.copy(alpha = 0.3f), Offset(x, ry + rowH), Offset(x + colW * 2f, ry + rowH), 0.5f)
    }

    // Border
    drawRect(
        Color(0x44FFFFFF), Offset(x, y), Size(colW * 2f, headerH + props.size * rowH),
        style = Stroke(0.8f)
    )
}

/**
 * Draw the connection detail view with bolt pattern, end plate, and weld symbols.
 */
private fun DrawScope.drawConnectionDetail(
    connLeft: Float, connTop: Float,
    connRight: Float, connBottom: Float,
    isIBeam: Boolean, isHSS: Boolean, isChannel: Boolean, isAngle: Boolean,
    depth: Double, flangeWidth: Double,
    flangeThickness: Double, webThickness: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    weldSize: Double
) {
    val viewW = connRight - connLeft
    val viewH = connBottom - connTop
    val cx = (connLeft + connRight) / 2f
    val cy = (connTop + connBottom) / 2f

    // Background panel
    drawRect(
        Color(0x0AFFFFFF),
        topLeft = Offset(connLeft, connTop),
        size = Size(viewW, viewH)
    )
    drawRect(
        Color(0x33FFFFFF),
        topLeft = Offset(connLeft, connTop),
        size = Size(viewW, viewH),
        style = Stroke(0.8f)
    )

    // Scale to fit the connection in the view
    val pad = 36f
    val availW = viewW - pad * 2
    val availH = viewH - pad * 2

    // Connection extent: flange width + some margin for gauge/pitch
    val connExtentW = max(flangeWidth + boltGauge * 1.5, boltPitch * (boltCount + 1))
    val connExtentH = depth

    val scaleX = availW / connExtentW.toFloat()
    val scaleY = availH / connExtentH.toFloat()
    val scale = min(scaleX, scaleY) * 0.7f

    val sD = depth.toFloat() * scale
    val sBf = flangeWidth.toFloat() * scale
    val sTf = flangeThickness.toFloat() * scale
    val sTw = webThickness.toFloat() * scale
    val sEp = max(endPlateThickness.toFloat() * scale, 4f)
    val sGauge = boltGauge.toFloat() * scale
    val sPitch = boltPitch.toFloat() * scale
    val sBd = max(boltDia.toFloat() * scale, 5f)

    // End plate (face-on view from the connection side)
    val epLeft = cx - sBf / 2f - sEp / 2f
    val epTop = cy - sD / 2f

    // Draw end plate rectangle
    drawRect(
        color = PlateGray.copy(alpha = 0.3f),
        topLeft = Offset(epLeft, epTop),
        size = Size(sBf, sD)
    )
    drawRect(
        color = PlateGray,
        topLeft = Offset(epLeft, epTop),
        size = Size(sBf, sD),
        style = Stroke(1.5f)
    )

    // Draw flange outlines on end plate (dashed)
    if (isIBeam) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
        // Top flange zone
        drawRect(
            color = SteelGray.copy(alpha = 0.4f),
            topLeft = Offset(epLeft, epTop),
            size = Size(sBf, sTf),
            style = Stroke(0.8f, pathEffect = dashEffect)
        )
        // Bottom flange zone
        drawRect(
            color = SteelGray.copy(alpha = 0.4f),
            topLeft = Offset(epLeft, epTop + sD - sTf),
            size = Size(sBf, sTf),
            style = Stroke(0.8f, pathEffect = dashEffect)
        )
        // Web zone
        drawRect(
            color = SteelGray.copy(alpha = 0.3f),
            topLeft = Offset(cx - sTw / 2f, epTop + sTf),
            size = Size(sTw, sD - sTf * 2f),
            style = Stroke(0.6f, pathEffect = dashEffect)
        )
    } else if (isHSS) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
        val wt = webThickness.toFloat() * scale
        drawRect(
            color = SteelGray.copy(alpha = 0.3f),
            topLeft = Offset(epLeft + wt, epTop + wt),
            size = Size(sBf - wt * 2f, sD - wt * 2f),
            style = Stroke(0.6f, pathEffect = dashEffect)
        )
    }

    // ── Bolt pattern ──
    // Flange bolts: arranged in rows at top and bottom flange
    val flangeBoltsPerRow = max(boltCount / 2, 2)
    val topFlangeCY = epTop + sTf / 2f
    val botFlangeCY = epTop + sD - sTf / 2f
    val webCY = cy

    // Top flange bolts
    val topBoltStartX = cx - (flangeBoltsPerRow - 1) / 2f * sGauge
    for (i in 0 until flangeBoltsPerRow) {
        drawBoltCircle(topBoltStartX + i * sGauge, topFlangeCY, sBd)
    }

    // Bottom flange bolts
    for (i in 0 until flangeBoltsPerRow) {
        drawBoltCircle(topBoltStartX + i * sGauge, botFlangeCY, sBd)
    }

    // Web bolts (if boltCount > 2)
    if (boltCount > 2 && isIBeam) {
        val webBoltsVert = max((boltCount - 2) / 2, 1)
        for (i in 0 until webBoltsVert) {
            val yOff = (i + 1) * sPitch
            if (cy + yOff < botFlangeCY - sTf) {
                drawBoltCircle(cx, cy + yOff, sBd * 0.9f)
            }
            if (cy - yOff > topFlangeCY + sTf) {
                drawBoltCircle(cx, cy - yOff, sBd * 0.9f)
            }
        }
    }

    // ── Gauge dimension (horizontal between bolt rows) ──
    if (flangeBoltsPerRow >= 2) {
        drawHorizontalDimension(
            topBoltStartX, topBoltStartX + (flangeBoltsPerRow - 1) * sGauge,
            topFlangeCY,
            "g=%.0f".format(boltGauge), Color(0xFFAADDFF), 12f, 16f
        )
    }

    // ── Pitch dimension (vertical between flange bolt rows) ──
    drawVerticalDimension(
        topFlangeCY, botFlangeCY, epLeft - 8f,
        "p=%.0f".format(depth - flangeThickness * 2), Color(0xFFAADDFF), 12f, 18f
    )

    // ── Bolt diameter callout ──
    drawTextAnnotated(
        "⌀%.0f".format(boltDia),
        epLeft + sBf + 6f, topFlangeCY + 4f,
        BoltOrange, 14f
    )

    // ── End plate thickness callout ──
    drawTextAnnotated(
        "tp=%.0f".format(endPlateThickness),
        epLeft + sBf + 6f, botFlangeCY + 4f,
        PlateGray, 14f
    )

    // ── Weld symbol ──
    drawWeldSymbolHorizontal(epLeft - 4f, cy - 10f, max(weldSize.toFloat() * scale, 3f))
    drawTextAnnotated(
        "%.0f".format(weldSize), epLeft - 22f, cy - 14f,
        WeldRed, 12f
    )
}

/**
 * Draw a horizontal weld symbol (triangle pointing right).
 */
private fun DrawScope.drawWeldSymbolHorizontal(
    x: Float, y: Float, size: Float
) {
    val s = max(size, 3f)
    drawPath(
        path = Path().apply {
            moveTo(x, y)
            lineTo(x + s * 1.5f, y - s)
            lineTo(x + s * 1.5f, y + s)
            close()
        },
        color = WeldRed
    )
    // Reference line
    drawLine(WeldRed, Offset(x, y), Offset(x, y + s * 2f), 1f)
}

/**
 * Draw the properties table at the bottom of the drawing.
 */
private fun DrawScope.drawPropertiesTable(
    x: Float, y: Float,
    width: Float, height: Float,
    sectionName: String, sectionType: String,
    area: Double, ix: Double, sx: Double, zx: Double,
    weightPerMeter: Double, memberLength: Double
) {
    // Background
    drawRect(Color(0x11FFFFFF), Offset(x, y), Size(width, height))
    // Border
    drawRect(ExtGray.copy(alpha = 0.4f), Offset(x, y), Size(width, height), style = Stroke(1f))

    // Table title
    drawTextAnnotated(
        "STEEL MEMBER PROPERTIES", x + 12f, y + 22f,
        DimWhite, 18f, bold = true
    )

    // Divider
    drawLine(ExtGray.copy(alpha = 0.3f), Offset(x, y + 32f), Offset(x + width, y + 32f), 0.8f)

    // Column definitions
    val colWidths = listOf(
        width * 0.18f,  // Section
        width * 0.12f,  // Type
        width * 0.13f,  // Area
        width * 0.17f,  // Ix
        width * 0.13f,  // Sx
        width * 0.13f,  // Zx
        width * 0.14f   // Weight/m
    )

    val headers = listOf("Section", "Type", "A (cm²)", "Ix (cm⁴)", "Sx (cm³)", "Zx (cm³)", "W (kg/m)")
    val values = listOf(
        sectionName,
        sectionType,
        "%.1f".format(area),
        "%.0f".format(ix),
        "%.0f".format(sx),
        "%.0f".format(zx),
        "%.1f".format(weightPerMeter)
    )

    val rowY = y + 50f
    val rowH = 24f

    // Header background
    drawRect(TblHeaderBg, Offset(x, rowY - 6f), Size(width, 22f))

    // Draw header
    var cx = x + 8f
    headers.forEachIndexed { i, header ->
        drawTextAnnotated(header, cx, rowY + 6f, DimWhite, 14f, bold = true)
        cx += colWidths[i]
    }

    // Draw values
    cx = x + 8f
    values.forEachIndexed { i, value ->
        drawTextAnnotated(value, cx, rowY + 30f, SteelTopGray, 15f, bold = (i == 0))
        cx += colWidths[i]
    }

    // Column dividers
    cx = x + colWidths[0]
    for (i in 1 until colWidths.size) {
        drawLine(
            ExtGray.copy(alpha = 0.2f), Offset(cx, y + 32f), Offset(cx, y + height), 0.5f
        )
        cx += colWidths[i]
    }

    // Bottom row: member length and bolt info
    drawLine(ExtGray.copy(alpha = 0.2f), Offset(x, rowY + 42f), Offset(x + width, rowY + 42f), 0.5f)
    drawTextAnnotated(
        "Member Length: %.0f mm".format(memberLength),
        x + 8f, rowY + 60f,
        ExtGray, 13f
    )
}

// ============================================================================
// UTILITY HELPERS
// ============================================================================

/** Center Y between two Y coordinates. */
private fun cy_center(top: Float, bottom: Float): Float = (top + bottom) / 2f