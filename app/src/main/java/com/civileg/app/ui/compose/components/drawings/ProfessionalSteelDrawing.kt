package com.civileg.app.ui.compose.components.drawings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.civileg.app.domain.entities.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================================
// COLOR PALETTE — steel structural drawing on dark background
// ============================================================================

private val BgDark = Color(0xFF1A1A2E)
private val SteelFill = Color(0xFF3D3D3D)
private val SteelOutline = Color(0xFFC0C0C0)
private val BoltOrange = Color(0xFFF39C12)
private val WeldRed = Color(0xFFE74C3C)
private val PlateGray = Color(0xFF95A5A6)
private val DimWhite = Color(0xFFFFFFFF)
private val ExtGray = Color(0xFFAAAAAA)
private val CyanAccent = Color(0xFF00BCD4)
private val TblHeaderBg = Color(0x33FFFFFF)
private val TblRowAlt = Color(0x1AFFFFFF)
private val SectionCut = Color(0xFFE74C3C)
private val GreenOk = Color(0xFF2ECC71)
private val YellowWarn = Color(0xFFF39C12)
private val RedFail = Color(0xFFE74C3C)
private val RadiusArcClr = Color(0xFF4A90D9)
private val WebLineClr = Color(0xFF7F8C8D)

// ============================================================================
// Section kind enum for dispatching
// ============================================================================

private enum class SectionKind {
    I_SECTION, C_CHANNEL, L_ANGLE, CHS, RHS, T_SECTION, PLATE_GIRDER, PIPE, UNKNOWN
}

private fun resolveKindFromString(sectionType: String): SectionKind = when {
    sectionType.contains("I-BEAM", true) || sectionType.contains("W-SECTION", true) ||
        sectionType.contains("IPE", true) || sectionType.contains("HEA", true) ||
        sectionType.contains("HEB", true) || sectionType.contains("I/H Section", true) ||
        sectionType.contains("I-SECTION", true) -> SectionKind.I_SECTION

    sectionType.contains("CHANNEL", true) || sectionType.contains("UPN", true) ||
        sectionType.contains("C Channel", true) || sectionType.contains("UC", true) ->
        SectionKind.C_CHANNEL

    sectionType.contains("ANGLE", true) || sectionType.contains("L-SECTION", true) ||
        sectionType.contains("L Angle", true) -> SectionKind.L_ANGLE

    sectionType.contains("CHS", true) || sectionType.contains("Circular Hollow", true) ->
        SectionKind.CHS

    sectionType.contains("HSS", true) || sectionType.contains("RHS", true) ||
        sectionType.contains("SHS", true) || sectionType.contains("Rectangular Hollow", true) ->
        SectionKind.RHS

    sectionType.contains("T-SECTION", true) || sectionType.contains("T Section", true) ->
        SectionKind.T_SECTION

    sectionType.contains("PLATE GIRDER", true) || sectionType.contains("Plate Girder", true) ->
        SectionKind.PLATE_GIRDER

    sectionType.contains("PIPE", true) -> SectionKind.PIPE

    else -> SectionKind.UNKNOWN
}

// ============================================================================
// Dimension extraction helpers
// ============================================================================

private data class DimPack(
    val d: Double, val b: Double, val t: Double, val t2: Double,
    val w: Double, val r: Double, val pipeSchedule: String
)

private fun extractDimensions(
    kind: SectionKind,
    sst: SteelSectionType?,
    fallbackDepth: Double, fallbackBf: Double, fallbackTf: Double,
    fallbackTw: Double, fallbackR: Double
): DimPack {
    if (sst == null) {
        return DimPack(fallbackDepth, fallbackBf, fallbackTf, fallbackTw, fallbackTw, fallbackR, "")
    }
    return when (sst) {
        is SteelSectionType.ISection -> DimPack(
            sst.h, sst.bf, sst.tf, sst.tw, sst.tw, sst.rootRadius, ""
        )
        is SteelSectionType.CSection -> DimPack(
            sst.h, sst.bf, sst.tf, sst.tw, sst.tw, sst.rootRadius, ""
        )
        is SteelSectionType.LSection -> DimPack(
            sst.legA, sst.legB, sst.thickness, sst.thickness, sst.thickness, 0.0, ""
        )
        is SteelSectionType.CHS -> DimPack(
            sst.outerDiameter, sst.outerDiameter, sst.thickness, 0.0, sst.thickness, 0.0, ""
        )
        is SteelSectionType.RHS -> DimPack(
            sst.height, sst.width, sst.thickness, 0.0, sst.thickness, 0.0, ""
        )
        is SteelSectionType.TSection -> DimPack(
            sst.webDepth + sst.flangeThickness, sst.flangeWidth, sst.flangeThickness,
            sst.webThickness, sst.webThickness, 0.0, ""
        )
        is SteelSectionType.PlateGirder -> DimPack(
            sst.h, maxOf(sst.bfTop, sst.bfBot), maxOf(sst.tfTop, sst.tfBot),
            minOf(sst.tfTop, sst.tfBot), sst.tw, 0.0, ""
        )
        is SteelSectionType.Pipe -> DimPack(
            sst.outerDiameter, sst.outerDiameter, sst.wallThickness, 0.0,
            sst.wallThickness, 0.0, sst.pipeSchedule
        )
        is SteelSectionType.BuiltUp -> DimPack(
            fallbackDepth, fallbackBf, fallbackTf, fallbackTw, fallbackTw, fallbackR, ""
        )
    }
}

private data class PGDims(val bfTop: Double, val bfBot: Double, val tfTop: Double, val tfBot: Double)

private fun extractPlateGirderDims(sst: SteelSectionType?, defaultBf: Double, defaultTf: Double): PGDims {
    if (sst is SteelSectionType.PlateGirder) {
        return PGDims(sst.bfTop, sst.bfBot, sst.tfTop, sst.tfBot)
    }
    return PGDims(defaultBf, defaultBf, defaultTf, defaultTf)
}

private fun extractGrade(sst: SteelSectionType?): SteelGrade? = when (sst) {
    is SteelSectionType.ISection -> sst.grade
    is SteelSectionType.CSection -> sst.grade
    is SteelSectionType.LSection -> sst.grade
    is SteelSectionType.CHS -> sst.grade
    is SteelSectionType.RHS -> sst.grade
    is SteelSectionType.TSection -> sst.grade
    is SteelSectionType.PlateGirder -> sst.grade
    is SteelSectionType.Pipe -> sst.grade
    is SteelSectionType.BuiltUp -> null
}

// ============================================================================
// MAIN COMPOSABLE
// ============================================================================

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
    steelSectionType: SteelSectionType? = null,
    utilizationRatio: Double = 0.0,
    gradeName: String = "S355",
    fy: Double = 355.0,
    fu: Double = 510.0,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        drawRect(BgDark, Offset.Zero, size)

        val cw = size.width
        val ch = size.height

        // ── Resolve section kind ──
        val kind: SectionKind
        val sst: SteelSectionType? = steelSectionType
        if (sst != null) {
            kind = when (sst) {
                is SteelSectionType.ISection -> SectionKind.I_SECTION
                is SteelSectionType.CSection -> SectionKind.C_CHANNEL
                is SteelSectionType.LSection -> SectionKind.L_ANGLE
                is SteelSectionType.CHS -> SectionKind.CHS
                is SteelSectionType.RHS -> SectionKind.RHS
                is SteelSectionType.TSection -> SectionKind.T_SECTION
                is SteelSectionType.PlateGirder -> SectionKind.PLATE_GIRDER
                is SteelSectionType.Pipe -> SectionKind.PIPE
                is SteelSectionType.BuiltUp -> SectionKind.UNKNOWN
            }
        } else {
            kind = resolveKindFromString(sectionType)
        }

        // ── Extract geometry ──
        val dims = extractDimensions(
            kind, sst, depth, flangeWidth, flangeThickness, webThickness, radius
        )
        val pgDims = extractPlateGirderDims(sst, dims.b, dims.t)

        // ── Grade info ──
        val grade = extractGrade(sst)
        val resolvedGradeName = grade?.displayName ?: gradeName
        val resolvedFy = grade?.fy ?: fy
        val resolvedFu = grade?.fu ?: fu

        // ── Properties (from typed section when available, else fallback) ──
        // When sst is provided, values are in mm units → convert to display units (cm)
        // When sst is null, use fallback params as-is (caller provides display-ready values)
        val propArea = sst?.let { it.area / 100.0 } ?: area
        val propIx = sst?.let { it.ix / 10000.0 } ?: ix
        val propSx = sst?.let { it.sx / 1000.0 } ?: sx
        val propZx = sst?.let { it.zx / 1000.0 } ?: zx
        val propRx = sst?.let { it.rx / 10.0 } ?: if (area > 0.0) sqrt(ix / area) else 0.0
        val propWeight = sst?.weight ?: weightPerMeter

        // ── Layout zones ──
        val margin = 24f
        val tableHeight = if (utilizationRatio > 0.001) 140f else 120f
        val tableTop = ch - tableHeight - margin

        val elevLeft = margin + 40f
        val elevRight = cw - margin - 40f
        val elevTop = margin + 40f
        val elevBottom = ch * 0.43f

        val sectLeft = margin + 20f
        val sectRight = cw * 0.48f
        val sectTop = elevBottom + 45f
        val sectBottom = tableTop - 10f

        val connLeft = cw * 0.52f
        val connRight = cw - margin - 20f
        val connTop = sectTop
        val connBottom = sectBottom

        // ════════════════════════════════════════════════════════════════════
        //  1. ELEVATION VIEW
        // ════════════════════════════════════════════════════════════════════
        drawElevationView(
            elevLeft, elevTop, elevRight, elevBottom,
            kind, memberLength, dims.d, dims.b, dims.t, dims.w,
            boltDia, boltCount, boltGauge, boltPitch, endPlateThickness,
            hasStiffener, weldSize, isColumn, sectionName
        )

        // ════════════════════════════════════════════════════════════════════
        //  2. CROSS-SECTION VIEW (the most important part)
        // ════════════════════════════════════════════════════════════════════
        drawCrossSectionView(
            sectLeft, sectTop, sectRight, sectBottom,
            kind, dims, pgDims
        )

        // ════════════════════════════════════════════════════════════════════
        //  3. CONNECTION DETAIL
        // ════════════════════════════════════════════════════════════════════
        drawConnectionDetail(
            connLeft, connTop, connRight, connBottom,
            kind, dims.d, dims.b, dims.t, dims.w,
            boltDia, boltCount, boltGauge, boltPitch, endPlateThickness, weldSize
        )

        // ════════════════════════════════════════════════════════════════════
        //  4. PROPERTIES TABLE
        // ════════════════════════════════════════════════════════════════════
        drawPropertiesTable(
            margin, tableTop, cw - margin * 2f, tableHeight,
            sectionName, sectionType, propArea, propIx, propSx, propZx,
            propRx, propWeight, memberLength, resolvedGradeName, resolvedFy, resolvedFu,
            utilizationRatio
        )

        // Zone labels
        drawTextAnnotated("ELEVATION", (elevLeft + elevRight) / 2f, elevTop - 15f, DimWhite, 16f, center = true, bold = true)
        drawTextAnnotated("SECTION A-A", (sectLeft + sectRight) / 2f, sectTop - 15f, DimWhite, 14f, center = true, bold = true)
        drawTextAnnotated("CONNECTION", (connLeft + connRight) / 2f, connTop - 15f, DimWhite, 14f, center = true, bold = true)
    }
}

// ============================================================================
// 1. ELEVATION VIEW
// ============================================================================

private fun DrawScope.drawElevationView(
    left: Float, top: Float, right: Float, bottom: Float,
    kind: SectionKind,
    memberLength: Double, depth: Double, bf: Double,
    tf: Double, tw: Double,
    boltDia: Double, boltCount: Int, boltGauge: Double,
    boltPitch: Double, endPlateThickness: Double,
    hasStiffener: Boolean, weldSize: Double,
    isColumn: Boolean, sectionName: String
) {
    val viewW = right - left
    val viewH = bottom - top
    val scaleX = viewW / memberLength.toFloat()
    val scaleY = (viewH * 0.4f) / depth.toFloat()
    val scale = min(scaleX, scaleY)

    val sLen = memberLength.toFloat() * scale
    val sDep = depth.toFloat() * scale
    val sTf = tf.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale

    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val mLeft = cx - sLen / 2f
    val mRight = cx + sLen / 2f
    val mTop = cy - sDep / 2f
    val mBot = cy + sDep / 2f

    // Draw main body based on section type
    when (kind) {
        SectionKind.I_SECTION, SectionKind.PLATE_GIRDER -> {
            // Top flange
            drawRect(SteelFill, Offset(mLeft, mTop), Size(sLen, sTf))
            // Bottom flange
            drawRect(SteelFill, Offset(mLeft, mBot - sTf), Size(sLen, sTf))
            // Web
            drawRect(SteelFill, Offset(mLeft, mTop + sTf), Size(sLen, sDep - 2 * sTf))
            drawRect(SteelOutline.copy(alpha = 0.7f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))
            drawLine(WebLineClr, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 0.8f)
            drawLine(WebLineClr, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), 0.8f)
            drawCenterLine(mLeft, cy, mRight, cy)
        }
        SectionKind.CHS, SectionKind.PIPE -> {
            val r = sDep / 2f
            drawCircle(SteelFill, r, Offset(cx, cy))
            drawCircle(SteelOutline.copy(alpha = 0.7f), r, Offset(cx, cy), style = Stroke(1.2f))
            drawCenterLine(mLeft - 10f, cy, mRight + 10f, cy)
        }
        SectionKind.RHS, SectionKind.SHS -> {
            drawRect(SteelFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelOutline.copy(alpha = 0.7f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))
            drawCenterLine(mLeft, cy, mRight, cy)
        }
        SectionKind.C_CHANNEL -> {
            drawRect(SteelFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelOutline.copy(alpha = 0.7f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))
            drawLine(WebLineClr, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 0.8f)
            drawLine(WebLineClr, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), 0.8f)
            drawCenterLine(mLeft, cy, mRight, cy)
        }
        SectionKind.T_SECTION -> {
            drawRect(SteelFill, Offset(mLeft, mTop), Size(sLen, sTf))
            drawRect(SteelFill, Offset(mLeft, mTop + sTf), Size(sLen, sDep - sTf))
            drawRect(SteelOutline.copy(alpha = 0.7f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))
            drawLine(WebLineClr, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 0.8f)
        }
        SectionKind.L_ANGLE, SectionKind.UNKNOWN -> {
            drawRect(SteelFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelOutline.copy(alpha = 0.7f), Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.2f))
        }
    }

    // End plates
    drawRect(PlateGray, Offset(mLeft - sEp, mTop - 5f), Size(sEp, sDep + 10f))
    drawRect(PlateGray, Offset(mRight, mTop - 5f), Size(sEp, sDep + 10f))
    drawRect(SteelOutline.copy(alpha = 0.4f), Offset(mLeft - sEp, mTop - 5f), Size(sEp, sDep + 10f), style = Stroke(0.8f))
    drawRect(SteelOutline.copy(alpha = 0.4f), Offset(mRight, mTop - 5f), Size(sEp, sDep + 10f), style = Stroke(0.8f))

    // Dimensions
    drawHorizontalDimension(mLeft, mRight, mBot + 10f, "${memberLength.toInt()} mm", DimWhite, 12f, 15f)
    drawVerticalDimension(mTop, mBot, mRight + sEp + 10f, "h=${depth.toInt()}", DimWhite, 12f, 20f)
    drawTextAnnotated(sectionName, cx, mTop - 10f, CyanAccent, 14f, center = true, bold = true)
}

// ============================================================================
// 2. CROSS-SECTION VIEW — dispatches to type-specific drawings
// ============================================================================

private fun DrawScope.drawCrossSectionView(
    left: Float, top: Float, right: Float, bottom: Float,
    kind: SectionKind,
    dims: DimPack,
    pgDims: PGDims
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    // Reserve margin for dimension lines
    val drawW = viewW * 0.55f
    val drawH = viewH * 0.55f

    when (kind) {
        SectionKind.I_SECTION -> drawISectionCross(cx, cy, drawW, drawH,
            dims.d, dims.b, dims.t, dims.w, dims.r)
        SectionKind.C_CHANNEL -> drawCChannelCross(cx, cy, drawW, drawH,
            dims.d, dims.b, dims.t, dims.w, dims.r)
        SectionKind.L_ANGLE -> drawAngleCross(cx, cy, drawW, drawH,
            dims.d, dims.b, dims.t)
        SectionKind.CHS -> drawCHSCross(cx, cy, drawW, drawH,
            dims.d, dims.t)
        SectionKind.RHS -> drawRHSCross(cx, cy, drawW, drawH,
            dims.d, dims.b, dims.t)
        SectionKind.T_SECTION -> drawTSectionCross(cx, cy, drawW, drawH,
            dims.d, dims.b, dims.t, dims.w)
        SectionKind.PLATE_GIRDER -> drawPlateGirderCross(cx, cy, drawW, drawH,
            dims.d, pgDims.bfTop, pgDims.bfBot, pgDims.tfTop, pgDims.tfBot, dims.w)
        SectionKind.PIPE -> drawPipeCross(cx, cy, drawW, drawH,
            dims.d, dims.t, dims.pipeSchedule)
        SectionKind.UNKNOWN -> drawGenericRect(cx, cy, drawW, drawH, dims.d, dims.b)
    }
}

// ─── I-Section / H-Section ─────────────────────────────────────────────────

private fun DrawScope.drawISectionCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    h: Double, bf: Double, tf: Double, tw: Double, rootR: Double
) {
    val scale = min(availW / bf.toFloat(), availH / h.toFloat())
    val sH = h.toFloat() * scale
    val sBf = bf.toFloat() * scale
    val sTf = tf.toFloat() * scale
    val sTw = tw.toFloat() * scale
    // Clamp root radius to fit geometry
    val maxR = min(sTf * 0.8f, (sBf - sTw) / 4f)
    val sR = min(maxOf(rootR.toFloat() * scale, 0f), maxR)
    val hasR = sR > 1.0f

    val left = cx - sBf / 2f
    val top = cy - sH / 2f
    val right = cx + sBf / 2f
    val bottom = cy + sH / 2f
    val innerTop = top + sTf
    val innerBot = bottom - sTf
    val halfTw = sTw / 2f

    // Build I-section path
    val path = Path().apply {
        moveTo(left, top)
        // Top edge
        lineTo(right, top)
        // Right side of top flange
        lineTo(right, innerTop)
        if (hasR) {
            // Along inner face of top flange to arc start
            lineTo(cx + halfTw + sR, innerTop)
            // Top-right root radius arc
            // Arc center: (cx + halfTw + sR, innerTop + sR)
            arcTo(
                Rect(cx + halfTw, innerTop, cx + halfTw + 2 * sR, innerTop + 2 * sR),
                270f, -90f, false
            )
            // Now at (cx + halfTw, innerTop + sR)
        } else {
            lineTo(cx + halfTw, innerTop)
        }
        // Right web face down
        if (hasR) {
            lineTo(cx + halfTw, innerBot - sR)
            // Bottom-right root radius arc
            // Arc center: (cx + halfTw + sR, innerBot - sR)
            arcTo(
                Rect(cx + halfTw, innerBot - 2 * sR, cx + halfTw + 2 * sR, innerBot),
                180f, -90f, false
            )
            // Now at (cx + halfTw + sR, innerBot)
        } else {
            lineTo(cx + halfTw, innerBot)
        }
        // Inner face of bottom flange
        lineTo(right, innerBot)
        // Right side of bottom flange
        lineTo(right, bottom)
        // Bottom edge
        lineTo(left, bottom)
        // Left side of bottom flange
        lineTo(left, innerBot)
        if (hasR) {
            // Along inner face of bottom flange to arc start
            lineTo(cx - halfTw - sR, innerBot)
            // Bottom-left root radius arc
            // Arc center: (cx - halfTw - sR, innerBot - sR)
            arcTo(
                Rect(cx - halfTw - 2 * sR, innerBot - 2 * sR, cx - halfTw, innerBot),
                90f, -90f, false
            )
            // Now at (cx - halfTw, innerBot - sR)
        } else {
            lineTo(cx - halfTw, innerBot)
        }
        // Left web face up
        if (hasR) {
            lineTo(cx - halfTw, innerTop + sR)
            // Top-left root radius arc
            // Arc center: (cx - halfTw - sR, innerTop + sR)
            arcTo(
                Rect(cx - halfTw - 2 * sR, innerTop, cx - halfTw, innerTop + 2 * sR),
                0f, -90f, false
            )
            // Now at (cx - halfTw - sR, innerTop)
        } else {
            lineTo(cx - halfTw, innerTop)
        }
        // Inner face of top flange
        lineTo(left, innerTop)
        // Left side of top flange
        lineTo(left, top)
        close()
    }

    drawPath(path, SteelFill)
    drawPath(path, SteelOutline, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(cx, top - 10f, cx, bottom + 10f)
    drawCenterLine(left - 10f, cy, right + 10f, cy)

    // Dimension lines
    drawHorizontalDimension(left, right, bottom + 5f, "bf=$bf", DimWhite, 11f, 18f)
    drawVerticalDimension(top, bottom, right + 5f, "h=$h", DimWhite, 11f, 18f)

    // Flange thickness (left side, top flange)
    val tfDimX = left - 8f
    drawLine(ExtGray, Offset(left, top), Offset(tfDimX, top), 0.6f)
    drawLine(ExtGray, Offset(left, innerTop), Offset(tfDimX, innerTop), 0.6f)
    drawLine(ExtGray, Offset(tfDimX, top), Offset(tfDimX, innerTop), 0.8f)
    drawTextAnnotated("tf=$tf", tfDimX - 2f, (top + innerTop) / 2f, ExtGray, 9f, center = true, rotation = -90f)

    // Web thickness (above mid-height)
    val webLeft = cx - halfTw
    val webRight = cx + halfTw
    val twDimY = cy - 5f
    drawLine(ExtGray, Offset(webLeft, twDimY), Offset(webLeft, twDimY - 8f), 0.6f)
    drawLine(ExtGray, Offset(webRight, twDimY), Offset(webRight, twDimY - 8f), 0.6f)
    drawLine(ExtGray, Offset(webLeft, twDimY - 8f), Offset(webRight, twDimY - 8f), 0.8f)
    drawTextAnnotated("tw=$tw", (webLeft + webRight) / 2f, twDimY - 14f, ExtGray, 9f, center = true)

    // Root radius label
    if (hasR && rootR > 0.5) {
        val arcCx = cx + halfTw + sR
        val arcCy = innerTop + sR
        drawCircle(RadiusArcClr, sR, Offset(arcCx, arcCy), style = Stroke(0.8f))
        drawTextAnnotated("r=$rootR", arcCx + sR + 3f, arcCy - 3f, RadiusArcClr, 9f)
    }
}

// ─── C-Channel ─────────────────────────────────────────────────────────────

private fun DrawScope.drawCChannelCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    h: Double, bf: Double, tf: Double, tw: Double, rootR: Double
) {
    val totalW = bf // bf is the total section width (web + flange extension)
    val scale = min(availW / totalW.toFloat(), availH / h.toFloat())
    val sH = h.toFloat() * scale
    val sBf = totalW.toFloat() * scale
    val sTf = tf.toFloat() * scale
    val sTw = tw.toFloat() * scale
    val maxR = min(sTf * 0.8f, (sBf - sTw) / 4f)
    val sR = min(maxOf(rootR.toFloat() * scale, 0f), maxR)
    val hasR = sR > 1.0f

    // Web on the left, flanges extend to the right
    val webLeft = cx - sBf / 2f
    val webRight = webLeft + sTw
    val flangeRight = cx - sBf / 2f + sBf // = webLeft + sBf = cx + sBf/2
    val sectionTop = cy - sH / 2f
    val sectionBot = cy + sH / 2f

    val path = Path().apply {
        // Start top-left (web outer face, top)
        moveTo(webLeft, sectionTop)
        // Top edge of top flange (goes right)
        lineTo(flangeRight, sectionTop)
        // Right side of top flange
        lineTo(flangeRight, sectionTop + sTf)
        // Inner face of top flange going left toward arc
        if (hasR) {
            lineTo(webRight + sR, sectionTop + sTf)
            // Top root radius arc
            // Corner at (webRight, sectionTop + sTf)
            // Center: (webRight + sR, sectionTop + sTf + sR)
            arcTo(
                Rect(webRight, sectionTop + sTf, webRight + 2 * sR, sectionTop + sTf + 2 * sR),
                270f, -90f, false
            )
            // Now at (webRight, sectionTop + sTf + sR)
        } else {
            lineTo(webRight, sectionTop + sTf)
        }
        // Right face of web going down
        if (hasR) {
            lineTo(webRight, sectionBot - sTf - sR)
            // Bottom root radius arc
            // Corner at (webRight, sectionBot - sTf)
            // Center: (webRight + sR, sectionBot - sTf - sR)
            arcTo(
                Rect(webRight, sectionBot - sTf - 2 * sR, webRight + 2 * sR, sectionBot - sTf),
                180f, -90f, false
            )
            // Now at (webRight + sR, sectionBot - sTf)
        } else {
            lineTo(webRight, sectionBot - sTf)
        }
        // Inner face of bottom flange going right
        lineTo(flangeRight, sectionBot - sTf)
        // Right side of bottom flange
        lineTo(flangeRight, sectionBot)
        // Bottom edge
        lineTo(webLeft, sectionBot)
        // Left side (web outer face) going up
        lineTo(webLeft, sectionTop)
        close()
    }

    drawPath(path, SteelFill)
    drawPath(path, SteelOutline, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(webLeft - 10f, cy, flangeRight + 10f, cy)
    drawCenterLine(webLeft, sectionTop - 10f, webLeft, sectionBot + 10f)

    // Dimensions
    drawHorizontalDimension(webLeft, flangeRight, sectionBot + 5f, "bf=$bf", DimWhite, 11f, 18f)
    drawVerticalDimension(sectionTop, sectionBot, flangeRight + 5f, "h=$h", DimWhite, 11f, 18f)

    // Flange thickness
    drawLine(ExtGray, Offset(flangeRight, sectionTop), Offset(flangeRight + 8f, sectionTop), 0.6f)
    drawLine(ExtGray, Offset(flangeRight, sectionTop + sTf), Offset(flangeRight + 8f, sectionTop + sTf), 0.6f)
    drawLine(ExtGray, Offset(flangeRight + 8f, sectionTop), Offset(flangeRight + 8f, sectionTop + sTf), 0.8f)
    drawTextAnnotated("tf=$tf", flangeRight + 10f, (sectionTop + sectionTop + sTf) / 2f, ExtGray, 9f, rotation = -90f)

    // Web thickness
    drawLine(ExtGray, Offset(webLeft, sectionTop), Offset(webLeft, sectionTop - 8f), 0.6f)
    drawLine(ExtGray, Offset(webRight, sectionTop), Offset(webRight, sectionTop - 8f), 0.6f)
    drawLine(ExtGray, Offset(webLeft, sectionTop - 8f), Offset(webRight, sectionTop - 8f), 0.8f)
    drawTextAnnotated("tw=$tw", (webLeft + webRight) / 2f, sectionTop - 14f, ExtGray, 9f, center = true)

    // Root radius label
    if (hasR && rootR > 0.5) {
        val arcCx = webRight + sR
        val arcCy = sectionTop + sTf + sR
        drawCircle(RadiusArcClr, sR, Offset(arcCx, arcCy), style = Stroke(0.8f))
        drawTextAnnotated("r=$rootR", arcCx + sR + 3f, arcCy - 3f, RadiusArcClr, 9f)
    }
}

// ─── L-Angle ───────────────────────────────────────────────────────────────

private fun DrawScope.drawAngleCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    legA: Double, legB: Double, t: Double
) {
    val scale = min(availW / (legA + legB).toFloat(), availH / maxOf(legA, legB).toFloat()) * 0.7f
    val sA = legA.toFloat() * scale   // vertical leg height
    val sB = legB.toFloat() * scale   // horizontal leg width
    val sT = t.toFloat() * scale      // thickness

    // Angle: corner at top-left, vertical leg goes down, horizontal leg goes right
    val ox = cx - sB * 0.15f
    val oy = cy - sA * 0.35f

    val path = Path().apply {
        moveTo(ox, oy)
        lineTo(ox + sB, oy)                    // top of horizontal leg
        lineTo(ox + sB, oy + sT)               // right side of horizontal leg
        lineTo(ox + sT, oy + sT)               // inner corner (where legs meet)
        lineTo(ox + sT, oy + sA)               // right side of vertical leg
        lineTo(ox, oy + sA)                    // bottom of vertical leg
        close()
    }

    drawPath(path, SteelFill)
    drawPath(path, SteelOutline, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(ox - 8f, oy + sA / 2f, ox + sB + 8f, oy + sA / 2f)
    drawCenterLine(ox + sB / 2f, oy - 8f, ox + sB / 2f, oy + sA + 8f)

    // Dimensions
    drawHorizontalDimension(ox, ox + sB, oy - 5f, "b=$legB", DimWhite, 11f, 18f)
    drawVerticalDimension(oy, oy + sA, ox - 5f, "a=$legA", DimWhite, 11f, 18f)

    // Thickness label
    drawTextAnnotated("t=$t", ox + sT + 3f, oy + sA - sT - 5f, ExtGray, 10f)
}

// ─── CHS (Circular Hollow Section) ────────────────────────────────────────

private fun DrawScope.drawCHSCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    d: Double, t: Double
) {
    val scale = min(availW / d.toFloat(), availH / d.toFloat())
    val sD = d.toFloat() * scale
    val sT = t.toFloat() * scale
    val outerR = sD / 2f
    val innerR = (outerR - sT).coerceAtLeast(1f)

    // Outer circle filled
    drawCircle(SteelFill, outerR, Offset(cx, cy))
    // Inner circle with background color (hollow effect)
    drawCircle(BgDark, innerR, Offset(cx, cy))
    // Outlines
    drawCircle(SteelOutline, outerR, Offset(cx, cy), style = Stroke(1.5f))
    drawCircle(SteelOutline.copy(alpha = 0.7f), innerR, Offset(cx, cy), style = Stroke(1f))

    // Center lines
    drawCenterLine(cx - outerR - 12f, cy, cx + outerR + 12f, cy)
    drawCenterLine(cx, cy - outerR - 12f, cx, cy + outerR + 12f)

    // Small center mark
    val cm = min(innerR * 0.3f, 5f)
    drawLine(ExtGray, Offset(cx - cm, cy), Offset(cx + cm, cy), 0.5f)
    drawLine(ExtGray, Offset(cx, cy - cm), Offset(cx, cy + cm), 0.5f)

    // Diameter dimension
    drawTextAnnotated("Ø${d.toInt()}", cx, cy + outerR + 18f, DimWhite, 11f, center = true)
    // Thickness label
    drawTextAnnotated("t=${t.toInt()}", cx + outerR + 8f, cy - 5f, ExtGray, 10f)
}

// ─── RHS / SHS (Rectangular Hollow Section) ───────────────────────────────

private fun DrawScope.drawRHSCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    h: Double, b: Double, t: Double
) {
    val scale = min(availW / b.toFloat(), availH / h.toFloat())
    val sH = h.toFloat() * scale
    val sB = b.toFloat() * scale
    val sT = t.toFloat() * scale

    val left = cx - sB / 2f
    val top = cy - sH / 2f

    // Outer rectangle filled
    drawRect(SteelFill, Offset(left, top), Size(sB, sH))
    // Inner rectangle (hollow) with background color
    drawRect(BgDark, Offset(left + sT, top + sT), Size(sB - 2 * sT, sH - 2 * sT))
    // Outlines
    drawRect(SteelOutline, Offset(left, top), Size(sB, sH), style = Stroke(1.5f))
    if (sB - 2 * sT > 1f && sH - 2 * sT > 1f) {
        drawRect(SteelOutline.copy(alpha = 0.7f),
            Offset(left + sT, top + sT), Size(sB - 2 * sT, sH - 2 * sT), style = Stroke(1f))
    }

    // Center lines
    drawCenterLine(left - 10f, cy, left + sB + 10f, cy)
    drawCenterLine(cx, top - 10f, cx, top + sH + 10f)

    // Dimensions
    drawHorizontalDimension(left, left + sB, top + sH + 5f, "b=${b.toInt()}", DimWhite, 11f, 18f)
    drawVerticalDimension(top, top + sH, left + sB + 5f, "h=${h.toInt()}", DimWhite, 11f, 18f)
    drawTextAnnotated("t=${t.toInt()}", left + sB + 8f, cy + 5f, ExtGray, 10f)
}

// ─── T-Section ─────────────────────────────────────────────────────────────

private fun DrawScope.drawTSectionCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    totalH: Double, bf: Double, tf: Double, tw: Double
) {
    val scale = min(availW / bf.toFloat(), availH / totalH.toFloat())
    val sH = totalH.toFloat() * scale
    val sBf = bf.toFloat() * scale
    val sTf = tf.toFloat() * scale
    val sTw = tw.toFloat() * scale

    val left = cx - sBf / 2f
    val top = cy - sH / 2f
    val halfTw = sTw / 2f
    val flangeBot = top + sTf

    val path = Path().apply {
        // Top flange (left to right along top, down right side, left along bottom)
        moveTo(left, top)
        lineTo(left + sBf, top)
        lineTo(left + sBf, flangeBot)
        lineTo(cx + halfTw, flangeBot)
        // Right web face down
        lineTo(cx + halfTw, top + sH)
        // Bottom of web
        lineTo(cx - halfTw, top + sH)
        // Left web face up
        lineTo(cx - halfTw, flangeBot)
        lineTo(left, flangeBot)
        close()
    }

    drawPath(path, SteelFill)
    drawPath(path, SteelOutline, style = Stroke(1.5f))

    // Web center line
    drawCenterLine(cx, top - 10f, cx, top + sH + 10f)

    // Dimensions
    drawHorizontalDimension(left, left + sBf, top - 5f, "bf=$bf", DimWhite, 11f, 18f)
    drawVerticalDimension(top, top + sH, left - 5f, "h=$totalH", DimWhite, 11f, 18f)

    // Flange thickness
    val tfRight = left + sBf + 8f
    drawLine(ExtGray, Offset(left + sBf, top), Offset(tfRight, top), 0.6f)
    drawLine(ExtGray, Offset(left + sBf, flangeBot), Offset(tfRight, flangeBot), 0.6f)
    drawLine(ExtGray, Offset(tfRight, top), Offset(tfRight, flangeBot), 0.8f)
    drawTextAnnotated("tf=$tf", tfRight + 2f, (top + flangeBot) / 2f, ExtGray, 9f, rotation = -90f)

    // Web thickness
    val twY = top + sH + 12f
    drawLine(ExtGray, Offset(cx - halfTw, top + sH), Offset(cx - halfTw, twY), 0.6f)
    drawLine(ExtGray, Offset(cx + halfTw, top + sH), Offset(cx + halfTw, twY), 0.6f)
    drawLine(ExtGray, Offset(cx - halfTw, twY), Offset(cx + halfTw, twY), 0.8f)
    drawTextAnnotated("tw=$tw", cx, twY + 10f, ExtGray, 9f, center = true)
}

// ─── Plate Girder (asymmetric I-shape) ─────────────────────────────────────

private fun DrawScope.drawPlateGirderCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    h: Double, bfTop: Double, bfBot: Double, tfTop: Double, tfBot: Double, tw: Double
) {
    val maxBf = maxOf(bfTop, bfBot)
    val scale = min(availW / maxBf.toFloat(), availH / h.toFloat())
    val sH = h.toFloat() * scale
    val sBfTop = bfTop.toFloat() * scale
    val sBfBot = bfBot.toFloat() * scale
    val sTfTop = tfTop.toFloat() * scale
    val sTfBot = tfBot.toFloat() * scale
    val sTw = tw.toFloat() * scale
    val halfTw = sTw / 2f

    val top = cy - sH / 2f
    val bottom = cy + sH / 2f
    val innerTop = top + sTfTop
    val innerBot = bottom - sTfBot

    // Build plate girder path (asymmetric flanges)
    val path = Path().apply {
        // Top flange
        moveTo(cx - sBfTop / 2f, top)
        lineTo(cx + sBfTop / 2f, top)
        lineTo(cx + sBfTop / 2f, innerTop)
        lineTo(cx + halfTw, innerTop)
        // Right web down
        lineTo(cx + halfTw, innerBot)
        // Bottom flange
        lineTo(cx + sBfBot / 2f, innerBot)
        lineTo(cx + sBfBot / 2f, bottom)
        lineTo(cx - sBfBot / 2f, bottom)
        lineTo(cx - sBfBot / 2f, innerBot)
        // Left web up
        lineTo(cx - halfTw, innerBot)
        lineTo(cx - halfTw, innerTop)
        lineTo(cx - sBfTop / 2f, innerTop)
        close()
    }

    drawPath(path, SteelFill)
    drawPath(path, SteelOutline, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(cx, top - 10f, cx, bottom + 10f)
    drawCenterLine(cx - maxBf.toFloat() * scale / 2f - 10f, cy,
            cx + maxBf.toFloat() * scale / 2f + 10f, cy)

    // Dimensions
    val rightEdge = maxOf(sBfTop, sBfBot) / 2f
    drawVerticalDimension(top, bottom, cx + rightEdge + 5f, "h=$h", DimWhite, 11f, 18f)

    // Top flange
    drawHorizontalDimension(cx - sBfTop / 2f, cx + sBfTop / 2f, top - 5f, "bfT=$bfTop", DimWhite, 11f, 18f)
    val tfTopLeftX = cx - sBfTop / 2f - 8f
    drawLine(ExtGray, Offset(cx - sBfTop / 2f, top), Offset(tfTopLeftX, top), 0.6f)
    drawLine(ExtGray, Offset(cx - sBfTop / 2f, innerTop), Offset(tfTopLeftX, innerTop), 0.6f)
    drawLine(ExtGray, Offset(tfTopLeftX, top), Offset(tfTopLeftX, innerTop), 0.8f)
    drawTextAnnotated("tfT=$tfTop", tfTopLeftX - 2f, (top + innerTop) / 2f, ExtGray, 9f, center = true, rotation = -90f)

    // Bottom flange
    drawHorizontalDimension(cx - sBfBot / 2f, cx + sBfBot / 2f, bottom + 5f, "bfB=$bfBot", DimWhite, 11f, 18f)
    val tfBotLeftX = cx - sBfBot / 2f - 8f
    drawLine(ExtGray, Offset(cx - sBfBot / 2f, innerBot), Offset(tfBotLeftX, innerBot), 0.6f)
    drawLine(ExtGray, Offset(cx - sBfBot / 2f, bottom), Offset(tfBotLeftX, bottom), 0.6f)
    drawLine(ExtGray, Offset(tfBotLeftX, innerBot), Offset(tfBotLeftX, bottom), 0.8f)
    drawTextAnnotated("tfB=$tfBot", tfBotLeftX - 2f, (innerBot + bottom) / 2f, ExtGray, 9f, center = true, rotation = -90f)

    // Web thickness
    drawTextAnnotated("tw=$tw", cx, cy - 4f, ExtGray, 10f, center = true)

    // Weld symbols at flange-web junctions
    val ws = 3f
    listOf(
        Offset(cx - halfTw, innerTop),
        Offset(cx + halfTw, innerTop),
        Offset(cx - halfTw, innerBot),
        Offset(cx + halfTw, innerBot)
    ).forEach { p ->
        drawPath(
            path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x - ws, p.y + ws * 1.5f)
                lineTo(p.x + ws, p.y + ws * 1.5f)
                close()
            },
            color = WeldRed
        )
    }
}

// ─── Pipe (CHS with schedule label) ───────────────────────────────────────

private fun DrawScope.drawPipeCross(
    cx: Float, cy: Float, availW: Float, availH: Float,
    d: Double, t: Double, schedule: String
) {
    drawCHSCross(cx, cy, availW, availH, d, t)
    if (schedule.isNotBlank()) {
        drawTextAnnotated("Sch: $schedule", cx, cy - 5f, CyanAccent, 10f, center = true, bold = true)
    }
}

// ─── Generic Rectangle (fallback) ──────────────────────────────────────────

private fun DrawScope.drawGenericRect(
    cx: Float, cy: Float, availW: Float, availH: Float,
    depth: Double, bf: Double
) {
    val scale = min(availW / bf.toFloat(), availH / depth.toFloat())
    val sH = depth.toFloat() * scale
    val sBf = bf.toFloat() * scale

    val left = cx - sBf / 2f
    val top = cy - sH / 2f

    drawRect(SteelFill, Offset(left, top), Size(sBf, sH))
    drawRect(SteelOutline, Offset(left, top), Size(sBf, sH), style = Stroke(1.5f))
    drawCenterLine(left - 10f, cy, left + sBf + 10f, cy)
    drawCenterLine(cx, top - 10f, cx, top + sH + 10f)
    drawHorizontalDimension(left, left + sBf, top + sH + 5f, "b=${bf.toInt()}", DimWhite, 11f, 18f)
    drawVerticalDimension(top, top + sH, left + sBf + 5f, "d=${depth.toInt()}", DimWhite, 11f, 18f)
}

// ============================================================================
// 3. CONNECTION DETAIL
// ============================================================================

private fun DrawScope.drawConnectionDetail(
    left: Float, top: Float, right: Float, bottom: Float,
    kind: SectionKind,
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

    val maxDim = maxOf(depth, flangeWidth)
    val scale = min((viewW * 0.7f) / maxDim.toFloat(), (viewH * 0.7f) / maxDim.toFloat())
    val sW = flangeWidth.toFloat() * scale
    val sH = depth.toFloat() * scale

    val plateW: Float
    val plateH: Float
    when (kind) {
        SectionKind.CHS, SectionKind.PIPE -> { plateW = sH; plateH = sH }
        SectionKind.L_ANGLE -> { plateW = sW * 0.8f; plateH = sH * 0.8f }
        else -> { plateW = sW; plateH = sH }
    }

    val epLeft = cx - plateW / 2f
    val epTop = cy - plateH / 2f

    // End plate
    drawRect(PlateGray.copy(alpha = 0.3f), Offset(epLeft, epTop), Size(plateW, plateH))
    drawRect(PlateGray, Offset(epLeft, epTop), Size(plateW, plateH), style = Stroke(1.2f))

    // Bolt pattern
    if (boltCount > 0) {
        val rows = if (boltCount <= 2) 1 else boltCount / 2
        val cols = if (boltCount <= 2) boltCount else 2
        val hSpacing = plateW / (cols + 1)
        val vSpacing = plateH / (rows + 1)
        val r = maxOf(3f, (boltDia.toFloat() * scale) / 2f)

        for (row in 1..rows) {
            for (col in 1..cols) {
                val bx = epLeft + col * hSpacing
                val by = epTop + row * vSpacing
                drawCircle(BoltOrange, r, Offset(bx, by))
                drawCircle(Color.Black, r * 0.4f, Offset(bx, by))
                drawLine(Color.Black, Offset(bx - r * 0.5f, by), Offset(bx + r * 0.5f, by), 0.8f)
                drawLine(Color.Black, Offset(bx, by - r * 0.5f), Offset(bx, by + r * 0.5f), 0.8f)
            }
        }
    }

    // Weld symbols
    val weldR = 2.5f
    for (i in 0 until 4) {
        val wy = epTop + (i + 1) * plateH / 5f
        drawPath(
            path = Path().apply {
                moveTo(epLeft + 1f, wy)
                lineTo(epLeft - weldR, wy + weldR * 1.5f)
                lineTo(epLeft + weldR * 2f, wy + weldR * 1.5f)
                close()
            },
            color = WeldRed
        )
        drawPath(
            path = Path().apply {
                moveTo(epLeft + plateW - 1f, wy)
                lineTo(epLeft + plateW - weldR * 2f, wy + weldR * 1.5f)
                lineTo(epLeft + plateW + weldR, wy + weldR * 1.5f)
                close()
            },
            color = WeldRed
        )
    }

    // Labels
    drawTextAnnotated("Ø${boltDia.toInt()}", cx, epTop - 8f, BoltOrange, 10f, center = true)
    drawTextAnnotated("${boltCount} bolts", cx, epTop + plateH + 14f, ExtGray, 10f, center = true)
}

// ============================================================================
// 4. PROPERTIES TABLE
// ============================================================================

private fun DrawScope.drawPropertiesTable(
    x: Float, y: Float, width: Float, height: Float,
    name: String, type: String,
    area: Double, ix: Double, sx: Double, zx: Double,
    rx: Double, weight: Double, len: Double,
    gradeName: String, fy: Double, fu: Double,
    utilizationRatio: Double
) {
    // Background and border
    drawRect(Color(0x11FFFFFF), Offset(x, y), Size(width, height))
    drawRect(ExtGray.copy(alpha = 0.4f), Offset(x, y), Size(width, height), style = Stroke(1f))

    // Header
    drawRect(TblHeaderBg, Offset(x, y), Size(width, 26f))
    drawTextAnnotated("STEEL PROPERTIES: $name", x + 10f, y + 18f, DimWhite, 12f, bold = true)
    drawTextAnnotated("Grade: $gradeName", x + width - 10f, y + 18f, CyanAccent, 12f, bold = true)

    // Divider after header
    drawLine(ExtGray.copy(alpha = 0.3f), Offset(x, y + 26f), Offset(x + width, y + 26f), 0.5f)

    val colW = width / 4f
    val rowH = 18f
    val startY = y + 40f

    // Row 1: Area, Ix, Sx, Zx
    drawTextAnnotated("Area: ${"%.1f".format(area)} cm\u00B2", x + 10f, startY, ExtGray, 11f)
    drawTextAnnotated("Ix: ${"%.0f".format(ix)} cm\u2074", x + colW + 10f, startY, ExtGray, 11f)
    drawTextAnnotated("Sx: ${"%.1f".format(sx)} cm\u00B3", x + colW * 2f + 10f, startY, ExtGray, 11f)
    drawTextAnnotated("Zx: ${"%.1f".format(zx)} cm\u00B3", x + colW * 3f + 10f, startY, ExtGray, 11f)

    // Row 2: rx, Weight, fy, fu
    val row2Y = startY + rowH
    drawTextAnnotated("rx: ${"%.2f".format(rx)} cm", x + 10f, row2Y, ExtGray, 11f)
    drawTextAnnotated("W: ${"%.1f".format(weight)} kg/m", x + colW + 10f, row2Y, ExtGray, 11f)
    drawTextAnnotated("fy: ${"%.0f".format(fy)} MPa", x + colW * 2f + 10f, row2Y, ExtGray, 11f)
    drawTextAnnotated("fu: ${"%.0f".format(fu)} MPa", x + colW * 3f + 10f, row2Y, ExtGray, 11f)

    // Row 3: Length + Utilization ratio bar
    val row3Y = row2Y + rowH
    drawTextAnnotated("L: ${"%.0f".format(len)} mm", x + 10f, row3Y, ExtGray, 11f)

    if (utilizationRatio > 0.001) {
        drawTextAnnotated("Utilization:", x + colW + 10f, row3Y, ExtGray, 11f)

        val barX = x + colW * 2f + 10f
        val barW = colW * 2f - 20f
        val barH = 12f
        val barY = row3Y - 9f

        // Background
        drawRect(Color(0x33FFFFFF), Offset(barX, barY), Size(barW, barH))
        drawRect(ExtGray.copy(alpha = 0.4f), Offset(barX, barY), Size(barW, barH), style = Stroke(0.8f))

        // Fill
        val fillRatio = utilizationRatio.coerceIn(0.0, 1.5)
        val fillColor = when {
            utilizationRatio < 0.8 -> GreenOk
            utilizationRatio <= 1.0 -> YellowWarn
            else -> RedFail
        }
        val fillW = (barW * (fillRatio / 1.5)).toFloat()
        if (fillW > 0.5f) {
            drawRect(fillColor.copy(alpha = 0.7f), Offset(barX, barY), Size(fillW, barH))
        }

        // Ratio text
        val ratioText = "%.2f".format(utilizationRatio)
        drawTextAnnotated(ratioText, barX + barW + 5f, row3Y, fillColor, 11f, bold = true)

        // Status text in bar
        val statusText = when {
            utilizationRatio < 0.8 -> "OK"
            utilizationRatio <= 1.0 -> "WARN"
            else -> "FAIL"
        }
        if (barW > 40f) {
            drawTextAnnotated(statusText, barX + barW / 2f, barY + barH / 2f + 1f, Color.Black, 8f, center = true, bold = true)
        }
    }
}
