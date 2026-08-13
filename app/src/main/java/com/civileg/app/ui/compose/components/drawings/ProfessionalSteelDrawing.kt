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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.entities.*
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
private val CentroidYellow = Color(0xFFFFD700)

/**
 * Professional steel section drawing with accurate geometry for ALL section types.
 * Draws: Elevation view, Cross-section, Connection detail, Properties table.
 *
 * @param section The steel section type with full geometric data
 * @param memberLength Member length in mm
 * @param memberType Beam, Column, Bracing, etc.
 * @param boltDia Bolt diameter in mm
 * @param boltCount Number of bolts
 * @param boltGauge Bolt gauge (transverse spacing) in mm
 * @param boltPitch Bolt pitch (longitudinal spacing) in mm
 * @param endPlateThickness End plate thickness in mm
 * @param hasStiffener Whether stiffener plates are present
 * @param weldSize Fillet weld size in mm
 * @param isArabic If true, labels are in Arabic; otherwise English
 * @param modifier Compose modifier
 */
@Composable
fun ProfessionalSteelDrawing(
    section: SteelSectionType,
    memberLength: Double = 6000.0,
    memberType: SteelMemberType = SteelMemberType.BEAM,
    boltDia: Double = 20.0,
    boltCount: Int = 4,
    boltGauge: Double = 90.0,
    boltPitch: Double = 75.0,
    endPlateThickness: Double = 12.0,
    hasStiffener: Boolean = false,
    weldSize: Double = 6.0,
    isArabic: Boolean = false,
    appliedMoment: Double = 0.0,      // Mu in kN.m
    appliedShear: Double = 0.0,       // Vu in kN
    appliedAxial: Double = 0.0,      // Pu in kN
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        val cw = size.width
        val ch = size.height

        val margin = 24f
        val tableHeight = 120f
        val tableTop = ch - tableHeight - margin

        val isBeam = memberType == SteelMemberType.BEAM || memberType == SteelMemberType.GIRDERS
        val hasLoads = appliedMoment > 0 || appliedShear > 0 || appliedAxial > 0

        // Elevation view (top zone)
        val elevLeft = margin + 40f
        val elevRight = cw - margin - 40f
        val elevTop = margin + 40f
        val elevBottom = if (isBeam && hasLoads) ch * 0.35f else ch * 0.42f

        // BMD/SFD zone (only for beams with loads, between elevation and cross-section)
        val bmdLeft = margin + 20f
        val bmdRight = cw - margin - 20f
        val bmdTop = elevBottom + 10f
        val bmdBottom = if (isBeam && hasLoads) ch * 0.55f else elevBottom + 10f

        // Cross-section (bottom-left)
        val sectLeft = margin + 20f
        val sectRight = if (isBeam && hasLoads) cw * 0.50f else cw * 0.50f
        val sectTop = bmdBottom + 20f
        val sectBottom = tableTop - 10f

        // Connection detail (bottom-right)
        val connLeft = cw * 0.52f
        val connRight = cw - margin - 20f
        val connTop = sectTop
        val connBottom = sectBottom

        // 1. ELEVATION VIEW (with load arrows for beams)
        drawElevationView(
            elevLeft, elevTop, elevRight, elevBottom,
            section, memberLength, memberType,
            boltDia, boltCount, boltGauge, boltPitch, endPlateThickness,
            hasStiffener, weldSize, isArabic,
            appliedMoment, appliedShear, appliedAxial
        )

        // 2. BMD/SFD DIAGRAMS (beams only)
        if (isBeam && hasLoads) {
            drawBmdSfd(
                bmdLeft, bmdTop, bmdRight, bmdBottom,
                memberLength, appliedMoment, appliedShear, isArabic
            )
        }

        // 3. CROSS-SECTION VIEW (accurate geometry)
        drawAccurateCrossSection(
            sectLeft, sectTop, sectRight, sectBottom,
            section, isArabic
        )

        // 3. CONNECTION DETAIL
        drawConnectionDetail(
            connLeft, connTop, connRight, connBottom,
            section, boltDia, boltCount, boltGauge, boltPitch,
            endPlateThickness, weldSize, isArabic
        )

        // 4. PROPERTIES TABLE
        drawPropertiesTable(
            margin, tableTop, cw - margin * 2f, tableHeight,
            section, memberLength, isArabic
        )

        // Labels
        val lblElev = if (isArabic) "منظر جانبي" else "ELEVATION"
        val lblSect = if (isArabic) "مقطع A-A" else "SECTION A-A"
        val lblConn = if (isArabic) "تفاصيل الوصلة" else "CONNECTION"
        drawTextAnnotated(lblElev, (elevLeft + elevRight) / 2, elevTop - 15f, DimWhite, 16f, center = true, bold = true)
        drawTextAnnotated(lblSect, (sectLeft + sectRight) / 2, sectTop - 15f, DimWhite, 14f, center = true, bold = true)
        drawTextAnnotated(lblConn, (connLeft + connRight) / 2, connTop - 15f, DimWhite, 14f, center = true, bold = true)
    }
}

// ============================================================================
// ELEVATION VIEW
// ============================================================================

private fun DrawScope.drawElevationView(
    left: Float, top: Float, right: Float, bottom: Float,
    section: SteelSectionType,
    memberLength: Double,
    memberType: SteelMemberType,
    boltDia: Double, boltCount: Int, boltGauge: Double, boltPitch: Double,
    endPlateThickness: Double, hasStiffener: Boolean, weldSize: Double,
    isArabic: Boolean,
    appliedMoment: Double = 0.0,
    appliedShear: Double = 0.0,
    appliedAxial: Double = 0.0
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val d = section.depth.toFloat()
    val w = section.width.toFloat()

    // Scale: fit length in view width (with some padding for end plates)
    val availW = viewW - 60f
    val scaleX = availW / memberLength.toFloat()
    val scaleY = (viewH * 0.5f) / d
    val scale = min(scaleX, scaleY)

    val sLen = memberLength.toFloat() * scale
    val sDep = d * scale
    val sTf = section.flangeThickness.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale

    val mLeft = cx - sLen / 2f
    val mRight = cx + sLen / 2f
    val mTop = cy - sDep / 2f
    val mBot = cy + sDep / 2f

    // Main body outline
    when (section) {
        is SteelSectionType.ISection, is SteelSectionType.PlateGirder -> {
            // I-section elevation: show top/bottom flanges and web
            drawRect(SectionFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelDarkGray, Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.5f))
            // Flange-web boundaries
            drawLine(SteelGray, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 1f)
            drawLine(SteelGray, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), 1f)
            // Web center line
            drawCenterLine(cx, mTop - 8f, cx, mBot + 8f, CentroidYellow, 0.8f)
        }
        is SteelSectionType.CHS, is SteelSectionType.Pipe -> {
            // Circular section: draw as rectangle with rounded ends
            drawRoundRect(SectionFill, Offset(mLeft, mTop), Size(sLen, sDep), androidx.compose.ui.geometry.CornerRadius(sDep / 2f))
            drawRoundRect(SteelDarkGray, Offset(mLeft, mTop), Size(sLen, sDep), androidx.compose.ui.geometry.CornerRadius(sDep / 2f), style = Stroke(1.5f))
            drawCenterLine(cx, mTop - 8f, cx, mBot + 8f, CentroidYellow, 0.8f)
        }
        is SteelSectionType.RHS -> {
            // Rectangular hollow: solid rectangle
            drawRect(SectionFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelDarkGray, Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.5f))
            drawCenterLine(cx, mTop - 8f, cx, mBot + 8f, CentroidYellow, 0.8f)
        }
        is SteelSectionType.CSection -> {
            drawRect(SectionFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelDarkGray, Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.5f))
            drawLine(SteelGray, Offset(mLeft, mTop + sTf), Offset(mRight, mTop + sTf), 1f)
            drawLine(SteelGray, Offset(mLeft, mBot - sTf), Offset(mRight, mBot - sTf), 1f)
        }
        is SteelSectionType.LSection -> {
            // Angle: L shape in elevation (show depth and leg)
            val legThick = section.webThickness.toFloat() * scale
            val path = Path().apply {
                moveTo(mLeft, mTop)
                lineTo(mRight, mTop)
                lineTo(mRight, mTop + legThick)
                lineTo(mLeft + legThick, mTop + legThick)
                lineTo(mLeft + legThick, mBot)
                lineTo(mLeft, mBot)
                close()
            }
            drawPath(path, SectionFill)
            drawPath(path, SteelDarkGray, style = Stroke(1.5f))
        }
        is SteelSectionType.TSection -> {
            // T-section: flange on top, web below
            val webW = section.webThickness.toFloat() * scale
            val webLeft = cx - webW / 2f
            // Flange
            drawRect(SectionFill, Offset(mLeft, mTop), Size(sLen, sTf))
            // Web (stem)
            drawRect(SectionFill, Offset(webLeft, mTop + sTf), Size(webW, sDep - sTf))
            // Outline
            val tPath = Path().apply {
                moveTo(mLeft, mTop)
                lineTo(mRight, mTop)
                lineTo(mRight, mTop + sTf)
                lineTo(webLeft + webW, mTop + sTf)
                lineTo(webLeft + webW, mBot)
                lineTo(webLeft, mBot)
                lineTo(webLeft, mTop + sTf)
                lineTo(mLeft, mTop + sTf)
                close()
            }
            drawPath(tPath, SteelDarkGray, style = Stroke(1.5f))
        }
        is SteelSectionType.BuiltUp -> {
            drawRect(SectionFill, Offset(mLeft, mTop), Size(sLen, sDep))
            drawRect(SteelDarkGray, Offset(mLeft, mTop), Size(sLen, sDep), style = Stroke(1.5f))
            drawCenterLine(cx, mTop - 8f, cx, mBot + 8f, CentroidYellow, 0.8f)
        }
    }

    // End plates (both ends)
    drawRect(PlateGray, Offset(mLeft - sEp, mTop - 5f), Size(sEp, sDep + 10f))
    drawRect(SteelDarkGray, Offset(mLeft - sEp, mTop - 5f), Size(sEp, sDep + 10f), style = Stroke(1f))
    drawRect(PlateGray, Offset(mRight, mTop - 5f), Size(sEp, sDep + 10f))
    drawRect(SteelDarkGray, Offset(mRight, mTop - 5f), Size(sEp, sDep + 10f), style = Stroke(1f))

    // Weld symbols at flange-to-end-plate junctions
    val weldLabel = if (isArabic) "لحام $weldSize" else "$weldSize".format(weldSize)
    drawTextAnnotated(weldLabel, mLeft - sEp / 2f, mTop - 12f, WeldRed, 10f, center = true)

    // Stiffener plates
    if (hasStiffener) {
        val stiffSpacing = when (section) {
            is SteelSectionType.PlateGirder -> section.stiffenerSpacing.toFloat() * scale
            else -> sLen / 4f
        }
        if (stiffSpacing > 20f) {
            var sx = mLeft + stiffSpacing
            while (sx < mRight - 10f) {
                drawLine(StiffenerColor, Offset(sx, mTop + sTf), Offset(sx, mBot - sTf), 1.5f)
                sx += stiffSpacing
            }
        }
    }

    // Section cut line at mid-span
    drawSectionCutLine(mLeft - 10f, mBot + 20f, mRight + 10f, mBot + 20f, "A", SectionCut, 10f, 16f)

    // Dimensions
    val lblLen = if (isArabic) "L=${memberLength.toInt()} mm" else "L=${memberLength.toInt()} mm"
    val lblDepth = if (isArabic) "d=${d.toInt()}" else "d=${d.toInt()}"
    drawHorizontalDimension(mLeft, mRight, mBot + 30f, lblLen, DimWhite, 12f, 15f)
    drawVerticalDimension(mTop, mBot, mRight + sEp + 10f, lblDepth, DimWhite, 12f, 20f)

    // Section name label
    drawTextAnnotated(section.sectionName, cx, mTop - 12f, Color.Cyan, 13f, center = true, bold = true)

    // ── Load arrows and support symbols (beams/girders with loads) ──
    val isBeamType = memberType == SteelMemberType.BEAM || memberType == SteelMemberType.GIRDERS
    if (isBeamType && (appliedMoment > 0 || appliedShear > 0)) {
        // Support symbols (pin at left, roller at right)
        val supportSize = min(12f, sDep * 0.3f)
        // Left pin support (triangle)
        drawPath(
            path = Path().apply {
                moveTo(mLeft, mBot)
                lineTo(mLeft - supportSize, mBot + supportSize * 1.2f)
                lineTo(mLeft + supportSize, mBot + supportSize * 1.2f)
                close()
            },
            color = ExtGray
        )
        // Right roller support (triangle + circle)
        drawPath(
            path = Path().apply {
                moveTo(mRight, mBot)
                lineTo(mRight - supportSize, mBot + supportSize * 1.2f)
                lineTo(mRight + supportSize, mBot + supportSize * 1.2f)
                close()
            },
            color = ExtGray
        )
        drawCircle(ExtGray, supportSize * 0.25f, center = Offset(mRight, mBot + supportSize * 1.5f))

        // UDL arrows (distributed load)
        if (appliedMoment > 0) {
            val arrowColor = Color(0xFF3498DB)
            val numArrows = max(5, (sLen / 30f).toInt().coerceIn(5, 20))
            val arrowSpacing = sLen / (numArrows + 1)
            val arrowLen = min(20f, (mTop - top - 15f) * 0.7f)
            val arrowTop = mTop - arrowLen - 6f

            // Top distribution line
            drawLine(arrowColor, Offset(mLeft, arrowTop), Offset(mRight, arrowTop), 1.2f)

            for (i in 1..numArrows) {
                val ax = mLeft + i * arrowSpacing
                // Arrow shaft
                drawLine(arrowColor, Offset(ax, arrowTop), Offset(ax, mTop - 2f), 0.8f)
                // Arrow head
                drawPath(
                    path = Path().apply {
                        moveTo(ax, mTop - 2f)
                        lineTo(ax - 3f, mTop - 8f)
                        lineTo(ax + 3f, mTop - 8f)
                        close()
                    },
                    color = arrowColor
                )
            }

            // Load value label
            // From Mmax = wL²/8 → w = 8*M/(L²) in kN/m
            val Lm = memberLength / 1000.0  // convert mm to m
            val wUDL = if (Lm > 0) 8.0 * appliedMoment / (Lm * Lm) else 0.0
            val udlLbl = if (isArabic) {
                "w=${"%.1f".format(wUDL)} kN/m"
            } else {
                "w=${"%.1f".format(wUDL)} kN/m"
            }
            drawTextAnnotated(udlLbl, cx, arrowTop - 6f, arrowColor, 10f, center = true, bold = true)
        }

        // Axial load arrow (for columns or beam with axial)
        if (appliedAxial > 0) {
            val axialColor = Color(0xFF9B59B6)
            val aArrowLen = min(18f, sLen * 0.08f)
            // Downward arrow at mid-span top
            drawLine(axialColor, Offset(cx, mTop - aArrowLen - 20f), Offset(cx, mTop - 2f), 1.5f)
            drawPath(
                path = Path().apply {
                    moveTo(cx, mTop - 2f)
                    lineTo(cx - 4f, mTop - 10f)
                    lineTo(cx + 4f, mTop - 10f)
                    close()
                },
                color = axialColor
            )
            val axialLbl = if (isArabic) "P=${appliedAxial.toInt()} kN" else "P=${appliedAxial.toInt()} kN"
            drawTextAnnotated(axialLbl, cx + 8f, mTop - aArrowLen - 14f, axialColor, 9f, bold = true)
        }
    }

    // Column axial load arrow
    if (memberType == SteelMemberType.COLUMN && appliedAxial > 0) {
        val axialColor = Color(0xFF9B59B6)
        val aLen = min(25f, (mTop - top) * 0.5f)
        drawLine(axialColor, Offset(cx, mTop - aLen), Offset(cx, mTop - 2f), 2f)
        drawPath(
            path = Path().apply {
                moveTo(cx, mTop - 2f)
                lineTo(cx - 5f, mTop - 12f)
                lineTo(cx + 5f, mTop - 12f)
                close()
            },
            color = axialColor
        )
        val pLbl = if (isArabic) "P=${appliedAxial.toInt()} kN" else "P=${appliedAxial.toInt()} kN"
        drawTextAnnotated(pLbl, cx + 10f, mTop - aLen + 4f, axialColor, 11f, bold = true)

        // Moment curved arrow at top
        if (appliedMoment > 0) {
            val mColor = Color(0xFFE67E22)
            drawTextAnnotated("M=${appliedMoment.toInt()} kN.m", cx, mBot + 44f, mColor, 10f, center = true, bold = true)
        }
    }
}

// ============================================================================
// BMD / SFD DIAGRAMS
// ============================================================================

private val BmdColor = Color(0xFF3498DB)
private val SfdColor = Color(0xFFE74C3C)

private fun DrawScope.drawBmdSfd(
    left: Float, top: Float, right: Float, bottom: Float,
    memberLength: Double,
    maxMoment: Double,  // kN.m
    maxShear: Double,   // kN
    isArabic: Boolean
) {
    val viewW = right - left
    val viewH = bottom - top
    val midX = (left + right) / 2f

    // Split into two halves: BMD (left) and SFD (right)
    val halfW = (viewW - 20f) / 2f
    val bmdL = left
    val bmdR = left + halfW
    val sfdL = left + halfW + 20f
    val sfdR = right

    // ── BMD (parabolic for UDL on simply supported beam) ──
    val bmdLabel = if (isArabic) "مخطط العزم (BMD)" else "BENDING MOMENT (BMD)"
    drawTextAnnotated(bmdLabel, (bmdL + bmdR) / 2f, top + 2f, BmdColor, 11f, center = true, bold = true)

    // Baseline
    val bmdBaseY = (top + bottom) / 2f + 8f
    val bmdHeight = (bottom - top) * 0.35f
    drawLine(ExtGray, Offset(bmdL + 8f, bmdBaseY), Offset(bmdR - 8f, bmdBaseY), 0.8f)

    // Parabolic BMD: M(x) = Mmax * 4*x*(L-x)/L²
    val bmdPath = Path().apply {
        val steps = 40
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = bmdL + 12f + t * (halfW - 24f)
            // Parabolic: max at t=0.5, zero at t=0 and t=1
            val mRatio = 4.0 * t * (1.0 - t)
            val y = bmdBaseY - mRatio * bmdHeight
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    // Fill under curve
    val bmdFillPath = Path().apply {
        val steps = 40
        moveTo(bmdL + 12f, bmdBaseY)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = bmdL + 12f + t * (halfW - 24f)
            val mRatio = 4.0 * t * (1.0 - t)
            val y = bmdBaseY - mRatio * bmdHeight
            lineTo(x, y)
        }
        lineTo(bmdR - 12f, bmdBaseY)
        close()
    }
    drawPath(bmdFillPath, BmdColor.copy(alpha = 0.15f))
    drawPath(bmdPath, BmdColor, style = Stroke(2f))

    // Max moment value at midspan
    val mVal = if (isArabic) "Mu=${"%.1f".format(maxMoment)}" else "Mu=${"%.1f".format(maxMoment)}"
    drawTextAnnotated(mVal, (bmdL + bmdR) / 2f, bmdBaseY - bmdHeight - 4f, BmdColor, 10f, center = true, bold = true)
    drawTextAnnotated("kN.m", (bmdL + bmdR) / 2f, bmdBaseY - bmdHeight + 8f, BmdColor.copy(alpha = 0.7f), 8f, center = true)

    // ── SFD (linear for UDL on simply supported beam) ──
    val sfdLabel = if (isArabic) "مخطط القص (SFD)" else "SHEAR FORCE (SFD)"
    drawTextAnnotated(sfdLabel, (sfdL + sfdR) / 2f, top + 2f, SfdColor, 11f, center = true, bold = true)

    val sfdBaseY = (top + bottom) / 2f + 8f
    val sfdHeight = (bottom - top) * 0.35f
    drawLine(ExtGray, Offset(sfdL + 8f, sfdBaseY), Offset(sfdR - 8f, sfdBaseY), 0.8f)

    // Linear SFD: V(x) = Vmax*(1 - 2*x/L), crosses zero at midspan
    val sfdPath = Path().apply {
        val x1 = sfdL + 12f
        val y1 = sfdBaseY - sfdHeight
        val x2 = sfdR - 12f
        val y2 = sfdBaseY + sfdHeight
        moveTo(x1, y1)
        lineTo((x1 + x2) / 2f, sfdBaseY)
        lineTo(x2, y2)
    }
    // Fill triangles
    val sfdFillPath = Path().apply {
        val x1 = sfdL + 12f
        val y1 = sfdBaseY - sfdHeight
        val x2 = sfdR - 12f
        moveTo(x1, sfdBaseY)
        lineTo(x1, y1)
        lineTo((x1 + x2) / 2f, sfdBaseY)
        close()
    }
    val sfdFillPath2 = Path().apply {
        val x2 = sfdR - 12f
        val y2 = sfdBaseY + sfdHeight
        val x1 = sfdL + 12f
        moveTo(x2, sfdBaseY)
        lineTo(x2, y2)
        lineTo((x1 + x2) / 2f, sfdBaseY)
        close()
    }
    drawPath(sfdFillPath, SfdColor.copy(alpha = 0.12f))
    drawPath(sfdFillPath2, SfdColor.copy(alpha = 0.12f))
    drawPath(sfdPath, SfdColor, style = Stroke(2f))

    // Max shear values at supports
    val vLeft = if (isArabic) "+${"%.1f".format(maxShear)}" else "+${"%.1f".format(maxShear)}"
    val vRight = if (isArabic) "-${"%.1f".format(maxShear)}" else "-${"%.1f".format(maxShear)}"
    drawTextAnnotated(vLeft, sfdL + 12f, sfdBaseY - sfdHeight - 4f, SfdColor, 10f, center = true, bold = true)
    drawTextAnnotated(vRight, sfdR - 12f, sfdBaseY + sfdHeight + 12f, SfdColor, 10f, center = true, bold = true)
    drawTextAnnotated("kN", sfdL + 12f, sfdBaseY - sfdHeight + 8f, SfdColor.copy(alpha = 0.7f), 8f, center = true)
    drawTextAnnotated("kN", sfdR - 12f, sfdBaseY + sfdHeight + 22f, SfdColor.copy(alpha = 0.7f), 8f, center = true)
}

// ============================================================================
// ACCURATE CROSS-SECTION VIEW
// ============================================================================

private fun DrawScope.drawAccurateCrossSection(
    left: Float, top: Float, right: Float, bottom: Float,
    section: SteelSectionType, isArabic: Boolean
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    when (section) {
        is SteelSectionType.ISection -> drawIBeamCrossSection(cx, cy, viewW, viewH, section.h, section.bf, section.tf, section.tw, section.rootRadius, section.flangeSlope, isArabic)
        is SteelSectionType.CSection -> drawChannelCrossSection(cx, cy, viewW, viewH, section.h, section.bf, section.tf, section.tw, section.rootRadius, section.flangeSlope, isArabic)
        is SteelSectionType.LSection -> drawAngleCrossSection(cx, cy, viewW, viewH, section.legA, section.legB, section.thickness, isArabic)
        is SteelSectionType.CHS -> drawCHSCrossSection(cx, cy, viewW, viewH, section.outerDiameter, section.thickness, isArabic)
        is SteelSectionType.RHS -> drawRHSCrossSection(cx, cy, viewW, viewH, section.width, section.height, section.thickness, isArabic)
        is SteelSectionType.TSection -> drawTCrossSection(cx, cy, viewW, viewH, section.flangeWidth, section.flangeThickness, section.webDepth, section.webThickness, isArabic)
        is SteelSectionType.PlateGirder -> drawPlateGirderCrossSection(cx, cy, viewW, viewH, section, isArabic)
        is SteelSectionType.Pipe -> drawCHSCrossSection(cx, cy, viewW, viewH, section.outerDiameter, section.wallThickness, isArabic, section.pipeSchedule)
        is SteelSectionType.BuiltUp -> drawBuiltUpCrossSection(cx, cy, viewW, viewH, section, isArabic)
    }
}

// --- I/H Beam Cross Section ---
private fun DrawScope.drawIBeamCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    h: Double, bf: Double, tf: Double, tw: Double, rootR: Double, slope: Double,
    isArabic: Boolean
) {
    val scale = min((viewW * 0.55f) / bf.toFloat(), (viewH * 0.55f) / h.toFloat())
    val sH = h.toFloat() * scale
    val sBf = bf.toFloat() * scale
    val sTf = tf.toFloat() * scale
    val sTw = tw.toFloat() * scale
    val sR = rootR.toFloat() * scale
    val sSlope = slope.toFloat()

    // Flange half-width reduction due to slope (taper)
    val flangeTaperReduction = (sH / 2f - sTf) * sSlope * 0.5f
    val topBfHalf = sBf / 2f - flangeTaperReduction
    val botBfHalf = sBf / 2f + flangeTaperReduction
    val webTopHalf = sTw / 2f + sR * 0.5f
    val webBotHalf = sTw / 2f + sR * 0.5f

    // Top edge: left flange corner, web-flange junction (left), (right), right flange corner
    val topLeftX = cx - topBfHalf
    val topRightX = cx + topBfHalf
    val botLeftX = cx - botBfHalf
    val botRightX = cx + botBfHalf

    // Build I-beam path with root radius fillets
    val path = Path().apply {
        // Start at top-left outer corner
        moveTo(topLeftX, cy - sH / 2f)
        // Top flange top edge
        lineTo(topRightX, cy - sH / 2f)
        // Right side of top flange (going down)
        lineTo(cx + webTopHalf, cy - sH / 2f + sTf - sR)
        // Root radius (top-right of web)
        arcTo(
            androidx.compose.ui.geometry.Rect(cx + webTopHalf - sR, cy - sH / 2f + sTf - sR, cx + webTopHalf + sR, cy - sH / 2f + sTf + sR),
            0f, -90f, false
        )
        // Right web going down
        lineTo(cx + webBotHalf, cy + sH / 2f - sTf - sR)
        // Root radius (bottom-right of web)
        arcTo(
            androidx.compose.ui.geometry.Rect(cx + webBotHalf - sR, cy + sH / 2f - sTf - sR, cx + webBotHalf + sR, cy + sH / 2f - sTf + sR),
            -90f, -90f, false
        )
        // Bottom flange right side going right
        lineTo(botRightX, cy + sH / 2f - sTf)
        // Bottom flange bottom edge
        lineTo(botRightX, cy + sH / 2f)
        // Bottom flange going left
        lineTo(botLeftX, cy + sH / 2f)
        // Bottom flange left side going up
        lineTo(cx - webBotHalf, cy + sH / 2f - sTf)
        // Root radius (bottom-left of web)
        arcTo(
            androidx.compose.ui.geometry.Rect(cx - webBotHalf - sR, cy + sH / 2f - sTf - sR, cx - webBotHalf + sR, cy + sH / 2f - sTf + sR),
            180f, -90f, false
        )
        // Left web going up
        lineTo(cx - webTopHalf, cy - sH / 2f + sTf + sR)
        // Root radius (top-left of web)
        arcTo(
            androidx.compose.ui.geometry.Rect(cx - webTopHalf - sR, cy - sH / 2f + sTf - sR, cx - webTopHalf + sR, cy - sH / 2f + sTf + sR),
            90f, -90f, false
        )
        // Top flange left side going right to close
        lineTo(topLeftX, cy - sH / 2f + sTf)
        close()
    }

    // Fill and stroke
    drawPath(path, SectionFill)
    drawPath(path, SteelDarkGray, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(cx - topBfHalf - 20f, cy, cx + topBfHalf + 20f, cy, CentroidYellow, 0.8f)
    drawCenterLine(cx, cy - sH / 2f - 15f, cx, cy + sH / 2f + 15f, CentroidYellow, 0.8f)

    // Centroid mark
    drawCircle(CentroidYellow, 3f, center = Offset(cx, cy))

    // Dimensions
    val lblD = if (isArabic) "h=${h.toInt()}" else "h=${h.toInt()}"
    val lblB = if (isArabic) "bf=${bf.toInt()}" else "bf=${bf.toInt()}"
    val lblTf = if (isArabic) "tf=${tf}" else "tf=${tf}"
    val lblTw = if (isArabic) "tw=${tw}" else "tw=${tw}"

    drawVerticalDimension(cy - sH / 2f, cy + sH / 2f, cx + topBfHalf + 15f, lblD, DimWhite, 11f, 18f)
    drawHorizontalDimension(cx - topBfHalf, cx + topBfHalf, cy - sH / 2f - 12f, lblB, DimWhite, 11f, 12f)
    // Flange thickness dimension (on right side, top flange)
    drawVerticalDimension(cy - sH / 2f, cy - sH / 2f + sTf, cx + topBfHalf + 45f, lblTf, DimWhite, 10f, 15f)
    // Web thickness dimension (on left side)
    drawHorizontalDimension(cx - sTw / 2f, cx + sTw / 2f, cy, lblTw, DimWhite, 10f, 12f)
}

// --- C Channel Cross Section ---
private fun DrawScope.drawChannelCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    h: Double, bf: Double, tf: Double, tw: Double, rootR: Double, slope: Double,
    isArabic: Boolean
) {
    val scale = min((viewW * 0.55f) / bf.toFloat(), (viewH * 0.55f) / h.toFloat())
    val sH = h.toFloat() * scale
    val sBf = bf.toFloat() * scale
    val sTf = tf.toFloat() * scale
    val sTw = tw.toFloat() * scale
    val sR = rootR.toFloat() * scale

    // Channel: web on the left, flange on the right (back of channel faces left)
    val webLeft = cx - sH / 2f  // channel depth goes horizontal for clarity in this view
    // Actually, let's orient it properly: depth vertical, flange pointing right
    val webX = cx - sBf / 2f  // left edge of web
    val webRightX = webX + sTw

    val path = Path().apply {
        // Start at top-left of web
        moveTo(webX, cy - sH / 2f)
        // Left side of web going down
        lineTo(webX, cy + sH / 2f)
        // Bottom flange going right
        lineTo(webX + sBf, cy + sH / 2f)
        // Right side of bottom flange going up
        lineTo(webX + sBf, cy + sH / 2f - sTf)
        // Inner bottom flange-web junction
        lineTo(webRightX + sR, cy + sH / 2f - sTf)
        // Root radius bottom
        arcTo(
            androidx.compose.ui.geometry.Rect(webRightX, cy + sH / 2f - sTf - sR, webRightX + 2 * sR, cy + sH / 2f - sTf + sR),
            180f, -90f, false
        )
        // Inner web going up
        lineTo(webRightX, cy - sH / 2f + sTf + sR)
        // Root radius top
        arcTo(
            androidx.compose.ui.geometry.Rect(webRightX, cy - sH / 2f + sTf - sR, webRightX + 2 * sR, cy - sH / 2f + sTf + sR),
            90f, -90f, false
        )
        // Inner top flange going right
        lineTo(webX + sBf, cy - sH / 2f + sTf)
        // Right side of top flange going up
        lineTo(webX + sBf, cy - sH / 2f)
        // Top flange going left back to start
        lineTo(webRightX + sR, cy - sH / 2f)
        close()
    }

    drawPath(path, SectionFill)
    drawPath(path, SteelDarkGray, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(webX - 15f, cy, webX + sBf + 15f, cy, CentroidYellow, 0.8f)
    drawCenterLine(webX + sBf / 2f, cy - sH / 2f - 12f, webX + sBf / 2f, cy + sH / 2f + 12f, CentroidYellow, 0.8f)

    // Centroid mark
    drawCircle(CentroidYellow, 3f, center = Offset(webX + sBf / 2f, cy))

    // Dimensions
    val lblH = if (isArabic) "h=${h.toInt()}" else "h=${h.toInt()}"
    val lblBf = if (isArabic) "bf=${bf.toInt()}" else "bf=${bf.toInt()}"
    val lblTf = if (isArabic) "tf=${tf}" else "tf=${tf}"
    val lblTw = if (isArabic) "tw=${tw}" else "tw=${tw}"

    drawVerticalDimension(cy - sH / 2f, cy + sH / 2f, webX + sBf + 12f, lblH, DimWhite, 11f, 18f)
    drawHorizontalDimension(webX, webX + sBf, cy + sH / 2f + 12f, lblBf, DimWhite, 11f, 12f)
    drawVerticalDimension(cy + sH / 2f - sTf, cy + sH / 2f, webX + sBf + 40f, lblTf, DimWhite, 10f, 15f)
    drawHorizontalDimension(webX, webRightX, cy - sH / 2f - 15f, lblTw, DimWhite, 10f, 12f)
}

// --- L Angle Cross Section ---
private fun DrawScope.drawAngleCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    legA: Double, legB: Double, thickness: Double,
    isArabic: Boolean
) {
    val maxDim = max(legA, legB).toFloat()
    val scale = min((viewW * 0.55f) / maxDim, (viewH * 0.55f) / maxDim)
    val sA = legA.toFloat() * scale  // vertical leg
    val sB = legB.toFloat() * scale  // horizontal leg
    val sT = thickness.toFloat() * scale
    val toeR = sT * 0.6f  // toe radius

    // L-shape: vertical leg going down, horizontal leg going right
    // Outer boundary
    val path = Path().apply {
        // Start at top-left (outer corner of vertical leg)
        moveTo(cx - sT / 2f, cy - sA / 2f)
        // Down the outer face of vertical leg
        lineTo(cx - sT / 2f, cy + sT / 2f + toeR)
        // Toe radius at bottom of vertical leg
        arcTo(
            androidx.compose.ui.geometry.Rect(cx - sT / 2f, cy + sT / 2f, cx - sT / 2f + 2 * toeR, cy + sT / 2f + 2 * toeR),
            180f, 90f, false
        )
        // Along bottom of horizontal leg
        lineTo(cx + sB / 2f - toeR, cy + sT / 2f)
        // Toe radius at right end
        arcTo(
            androidx.compose.ui.geometry.Rect(cx + sB / 2f - 2 * toeR, cy + sT / 2f - 2 * toeR, cx + sB / 2f, cy + sT / 2f),
            90f, 90f, false
        )
        // Up the outer face of horizontal leg
        lineTo(cx + sB / 2f, cy - sT / 2f)
        // Along top of horizontal leg (inner)
        lineTo(cx + sT / 2f + toeR, cy - sT / 2f)
        // Root radius (inner corner)
        arcTo(
            androidx.compose.ui.geometry.Rect(cx + sT / 2f, cy - sT / 2f, cx + sT / 2f + 2 * toeR, cy - sT / 2f + 2 * toeR),
            0f, -90f, false
        )
        // Down the inner face of vertical leg
        lineTo(cx + sT / 2f, cy + sA / 2f - toeR)
        // Root radius (inner corner bottom)
        arcTo(
            androidx.compose.ui.geometry.Rect(cx + sT / 2f - 2 * toeR, cy + sA / 2f - 2 * toeR, cx + sT / 2f, cy + sA / 2f),
            90f, -90f, false
        )
        // Along inner top of vertical leg
        lineTo(cx - sT / 2f, cy + sA / 2f)
        close()
    }

    drawPath(path, SectionFill)
    drawPath(path, SteelDarkGray, style = Stroke(1.5f))

    // Center lines (through the angle heel)
    drawCenterLine(cx - sB / 2f - 15f, cy, cx + sB / 2f + 15f, cy, CentroidYellow, 0.8f)
    drawCenterLine(cx, cy - sA / 2f - 12f, cx, cy + sA / 2f + 12f, CentroidYellow, 0.8f)

    // Centroid mark
    drawCircle(CentroidYellow, 3f, center = Offset(cx, cy))

    // Dimensions
    val lblA = if (isArabic) "a=${legA.toInt()}" else "a=${legA.toInt()}"
    val lblB = if (isArabic) "b=${legB.toInt()}" else "b=${legB.toInt()}"
    val lblT = if (isArabic) "t=${thickness.toInt()}" else "t=${thickness.toInt()}"

    drawVerticalDimension(cy - sA / 2f, cy + sA / 2f, cx - sB / 2f - 20f, lblA, DimWhite, 11f, 18f)
    drawHorizontalDimension(cx - sB / 2f, cx + sB / 2f, cy + sA / 2f + 12f, lblB, DimWhite, 11f, 12f)
    drawHorizontalDimension(cx - sT / 2f, cx + sT / 2f, cy - sA / 2f - 12f, lblT, DimWhite, 10f, 12f)
}

// --- CHS / Pipe Cross Section ---
private fun DrawScope.drawCHSCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    outerD: Double, thickness: Double, isArabic: Boolean,
    schedule: String = ""
) {
    val scale = min((viewW * 0.55f) / outerD.toFloat(), (viewH * 0.55f) / outerD.toFloat())
    val sD = outerD.toFloat() * scale
    val sT = thickness.toFloat() * scale
    val innerD = sD - 2 * sT

    // Outer circle
    drawCircle(SectionFill, radius = sD / 2f, center = Offset(cx, cy))
    drawCircle(SteelDarkGray, radius = sD / 2f, center = Offset(cx, cy), style = Stroke(1.5f))

    // Inner circle (hollow)
    if (innerD > 2f) {
        drawCircle(Color(0xFF1A1A1A), radius = innerD / 2f, center = Offset(cx, cy))
        drawCircle(SteelGray, radius = innerD / 2f, center = Offset(cx, cy), style = Stroke(0.8f))
    }

    // Center lines
    drawCenterLine(cx - sD / 2f - 15f, cy, cx + sD / 2f + 15f, cy, CentroidYellow, 0.8f)
    drawCenterLine(cx, cy - sD / 2f - 15f, cx, cy + sD / 2f + 15f, CentroidYellow, 0.8f)

    // Centroid mark
    drawCircle(CentroidYellow, 3f, center = Offset(cx, cy))

    // Dimensions
    val lblD = if (isArabic) "Ø${outerD.toInt()}" else "Ø${outerD.toInt()}"
    val lblT = if (isArabic) "t=${thickness}" else "t=${thickness}"

    drawHorizontalDimension(cx - sD / 2f, cx + sD / 2f, cy + sD / 2f + 12f, lblD, DimWhite, 11f, 12f)
    // Thickness callout
    drawTextAnnotated(lblT, cx + sD / 2f + 8f, cy - 6f, DimWhite, 10f)

    // Schedule label for Pipe
    if (schedule.isNotEmpty()) {
        drawTextAnnotated(schedule, cx, cy, DimWhite, 9f, center = true)
    }
}

// --- RHS / SHS Cross Section ---
private fun DrawScope.drawRHSCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    w: Double, h: Double, thickness: Double,
    isArabic: Boolean
) {
    val scale = min((viewW * 0.55f) / w.toFloat(), (viewH * 0.55f) / h.toFloat())
    val sW = w.toFloat() * scale
    val sH = h.toFloat() * scale
    val sT = thickness.toFloat() * scale
    val cornerR = min(sT * 2f, min(sW, sH) * 0.08f)  // corner radius
    val innerW = sW - 2 * sT
    val innerH = sH - 2 * sT
    val innerR = maxOf(0f, cornerR - sT)

    // Outer rectangle with rounded corners
    drawRoundRect(SectionFill, Offset(cx - sW / 2f, cy - sH / 2f), Size(sW, sH), androidx.compose.ui.geometry.CornerRadius(cornerR))
    drawRoundRect(SteelDarkGray, Offset(cx - sW / 2f, cy - sH / 2f), Size(sW, sH), androidx.compose.ui.geometry.CornerRadius(cornerR), style = Stroke(1.5f))

    // Inner rectangle (hollow) with rounded corners
    if (innerW > 2f && innerH > 2f) {
        drawRoundRect(Color(0xFF1A1A1A), Offset(cx - innerW / 2f, cy - innerH / 2f), Size(innerW, innerH), androidx.compose.ui.geometry.CornerRadius(innerR))
        drawRoundRect(SteelGray, Offset(cx - innerW / 2f, cy - innerH / 2f), Size(innerW, innerH), androidx.compose.ui.geometry.CornerRadius(innerR), style = Stroke(0.8f))
    }

    // Center lines
    drawCenterLine(cx - sW / 2f - 15f, cy, cx + sW / 2f + 15f, cy, CentroidYellow, 0.8f)
    drawCenterLine(cx, cy - sH / 2f - 12f, cx, cy + sH / 2f + 12f, CentroidYellow, 0.8f)

    // Centroid mark
    drawCircle(CentroidYellow, 3f, center = Offset(cx, cy))

    // Dimensions
    val lblH = if (isArabic) "h=${h.toInt()}" else "h=${h.toInt()}"
    val lblB = if (isArabic) "b=${w.toInt()}" else "b=${w.toInt()}"
    val lblT = if (isArabic) "t=${thickness}" else "t=${thickness}"

    drawVerticalDimension(cy - sH / 2f, cy + sH / 2f, cx + sW / 2f + 12f, lblH, DimWhite, 11f, 18f)
    drawHorizontalDimension(cx - sW / 2f, cx + sW / 2f, cy - sH / 2f - 12f, lblB, DimWhite, 11f, 12f)
    drawTextAnnotated(lblT, cx + sW / 2f + 6f, cy - 6f, DimWhite, 10f)
}

// --- T Section Cross Section ---
private fun DrawScope.drawTCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    flangeW: Double, flangeT: Double, webD: Double, webT: Double,
    isArabic: Boolean
) {
    val totalH = webD + flangeT
    val scale = min((viewW * 0.55f) / flangeW.toFloat(), (viewH * 0.55f) / totalH.toFloat())
    val sFw = flangeW.toFloat() * scale
    val sFt = flangeT.toFloat() * scale
    val sWd = webD.toFloat() * scale
    val sWt = webT.toFloat() * scale
    val rootR = min(sFt * 0.8f, 6f)

    // T-section: flange on top, stem (web) below center
    val path = Path().apply {
        // Top-left of flange
        moveTo(cx - sFw / 2f, cy - sWd / 2f - sFt)
        // Top edge of flange
        lineTo(cx + sFw / 2f, cy - sWd / 2f - sFt)
        // Right side of flange going down
        lineTo(cx + sFw / 2f, cy - sWd / 2f)
        // Inner right of flange going left to web
        lineTo(cx + sWt / 2f + rootR, cy - sWd / 2f)
        // Root radius top-right
        arcTo(
            androidx.compose.ui.geometry.Rect(cx + sWt / 2f, cy - sWd / 2f - rootR, cx + sWt / 2f + 2 * rootR, cy - sWd / 2f + rootR),
            0f, -90f, false
        )
        // Right side of web going down
        lineTo(cx + sWt / 2f, cy + sWd / 2f)
        // Bottom of web going left
        lineTo(cx - sWt / 2f, cy + sWd / 2f)
        // Left side of web going up
        lineTo(cx - sWt / 2f, cy - sWd / 2f + rootR)
        // Root radius top-left
        arcTo(
            androidx.compose.ui.geometry.Rect(cx - sWt / 2f - 2 * rootR, cy - sWd / 2f - rootR, cx - sWt / 2f, cy - sWd / 2f + rootR),
            90f, -90f, false
        )
        // Inner left of flange going left
        lineTo(cx - sFw / 2f, cy - sWd / 2f)
        // Left side of flange going up to close
        lineTo(cx - sFw / 2f, cy - sWd / 2f - sFt)
        close()
    }

    drawPath(path, SectionFill)
    drawPath(path, SteelDarkGray, style = Stroke(1.5f))

    // Center lines
    drawCenterLine(cx - sFw / 2f - 15f, cy, cx + sFw / 2f + 15f, cy, CentroidYellow, 0.8f)
    drawCenterLine(cx, cy - sWd / 2f - sFt - 12f, cx, cy + sWd / 2f + 12f, CentroidYellow, 0.8f)

    // Centroid mark
    drawCircle(CentroidYellow, 3f, center = Offset(cx, cy))

    // Dimensions
    val lblD = if (isArabic) "d=${totalH.toInt()}" else "d=${totalH.toInt()}"
    val lblBf = if (isArabic) "bf=${flangeW.toInt()}" else "bf=${flangeW.toInt()}"
    val lblTf = if (isArabic) "tf=${flangeT}" else "tf=${flangeT}"
    val lblTw = if (isArabic) "tw=${webT}" else "tw=${webT}"

    drawVerticalDimension(cy - sWd / 2f - sFt, cy + sWd / 2f, cx + sFw / 2f + 12f, lblD, DimWhite, 11f, 18f)
    drawHorizontalDimension(cx - sFw / 2f, cx + sFw / 2f, cy - sWd / 2f - sFt - 12f, lblBf, DimWhite, 11f, 12f)
    drawVerticalDimension(cy - sWd / 2f - sFt, cy - sWd / 2f, cx + sFw / 2f + 42f, lblTf, DimWhite, 10f, 15f)
    drawHorizontalDimension(cx - sWt / 2f, cx + sWt / 2f, cy + 10f, lblTw, DimWhite, 10f, 12f)
}

// --- Plate Girder Cross Section ---
private fun DrawScope.drawPlateGirderCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    pg: SteelSectionType.PlateGirder,
    isArabic: Boolean
) {
    val scale = min((viewW * 0.55f) / pg.width.toFloat(), (viewH * 0.55f) / pg.h.toFloat())
    val sH = pg.h.toFloat() * scale
    val sBfTop = pg.bfTop.toFloat() * scale
    val sBfBot = pg.bfBot.toFloat() * scale
    val sTfTop = pg.tfTop.toFloat() * scale
    val sTfBot = pg.tfBot.toFloat() * scale
    val sTw = pg.tw.toFloat() * scale

    // Build path for potentially asymmetric plate girder
    val path = Path().apply {
        val topY = cy - sH / 2f
        val botY = cy + sH / 2f
        val topFlangeBot = topY + sTfTop
        val botFlangeTop = botY - sTfBot

        // Start top-left outer
        moveTo(cx - sBfTop / 2f, topY)
        // Top flange top edge
        lineTo(cx + sBfTop / 2f, topY)
        // Right side of top flange
        lineTo(cx + sBfTop / 2f, topFlangeBot)
        // Top flange inner to web
        lineTo(cx + sTw / 2f, topFlangeBot)
        // Right web going down
        lineTo(cx + sTw / 2f, botFlangeTop)
        // Bottom flange inner right
        lineTo(cx + sBfBot / 2f, botFlangeTop)
        // Right side of bottom flange
        lineTo(cx + sBfBot / 2f, botY)
        // Bottom flange bottom edge
        lineTo(cx - sBfBot / 2f, botY)
        // Left side of bottom flange
        lineTo(cx - sBfBot / 2f, botFlangeTop)
        // Bottom flange inner left
        lineTo(cx - sTw / 2f, botFlangeTop)
        // Left web going up
        lineTo(cx - sTw / 2f, topFlangeBot)
        // Top flange inner left
        lineTo(cx - sBfTop / 2f, topFlangeBot)
        // Left side of top flange going up
        lineTo(cx - sBfTop / 2f, topY)
        close()
    }

    drawPath(path, SectionFill)
    drawPath(path, SteelDarkGray, style = Stroke(1.5f))

    // Stiffener plates (intermediate transverse)
    if (pg.stiffenerSpacing > 0) {
        val stiffExtend = 8f
        drawLine(StiffenerColor, Offset(cx - sTw / 2f - stiffExtend, cy), Offset(cx + sTw / 2f + stiffExtend, cy), 2f)
    }

    // Center lines
    val maxBf = max(sBfTop, sBfBot)
    drawCenterLine(cx - maxBf / 2f - 15f, cy, cx + maxBf / 2f + 15f, cy, CentroidYellow, 0.8f)
    drawCenterLine(cx, cy - sH / 2f - 15f, cx, cy + sH / 2f + 15f, CentroidYellow, 0.8f)
    drawCircle(CentroidYellow, 3f, center = Offset(cx, cy))

    // Dimensions
    val lblH = if (isArabic) "h=${pg.h.toInt()}" else "h=${pg.h.toInt()}"
    val lblBfT = if (isArabic) "bft=${pg.bfTop.toInt()}" else "bft=${pg.bfTop.toInt()}"
    val lblBfB = if (isArabic) "bfb=${pg.bfBot.toInt()}" else "bfb=${pg.bfBot.toInt()}"
    val lblTw = if (isArabic) "tw=${pg.tw}" else "tw=${pg.tw}"

    drawVerticalDimension(cy - sH / 2f, cy + sH / 2f, cx + maxBf / 2f + 12f, lblH, DimWhite, 11f, 18f)
    drawHorizontalDimension(cx - sBfTop / 2f, cx + sBfTop / 2f, cy - sH / 2f - 15f, lblBfT, DimWhite, 10f, 12f)
    drawHorizontalDimension(cx - sBfBot / 2f, cx + sBfBot / 2f, cy + sH / 2f + 12f, lblBfB, DimWhite, 10f, 12f)
    drawHorizontalDimension(cx - sTw / 2f, cx + sTw / 2f, cy + 14f, lblTw, DimWhite, 10f, 12f)
}

// --- Built-up Section Cross Section ---
private fun DrawScope.drawBuiltUpCrossSection(
    cx: Float, cy: Float, viewW: Float, viewH: Float,
    builtUp: SteelSectionType.BuiltUp,
    isArabic: Boolean
) {
    // For built-up sections, draw a schematic showing combined sub-sections
    val label = if (isArabic) "مقطع مركب" else "BUILT-UP"
    drawTextAnnotated(label, cx, cy - 20f, DimWhite, 14f, center = true, bold = true)

    // Draw each sub-section as a small block
    val subCount = builtUp.sections.size
    if (subCount > 0) {
        val blockW = min(40f, (viewW - 40f) / subCount)
        val startX = cx - (subCount * blockW) / 2f
        builtUp.sections.forEachIndexed { i, sub ->
            val bx = startX + i * blockW + 2f
            val by = cy - 8f
            drawRect(SectionFill, Offset(bx, by), Size(blockW - 4f, 16f))
            drawRect(SteelDarkGray, Offset(bx, by), Size(blockW - 4f, 16f), style = Stroke(1f))
            drawTextAnnotated(sub.sectionName, bx + (blockW - 4f) / 2f, by + 12f, ExtGray, 8f, center = true)
        }
    }
    drawCenterLine(cx - viewW * 0.3f, cy, cx + viewW * 0.3f, cy, CentroidYellow, 0.8f)
}

// ============================================================================
// CONNECTION DETAIL
// ============================================================================

private fun DrawScope.drawConnectionDetail(
    left: Float, top: Float, right: Float, bottom: Float,
    section: SteelSectionType,
    boltDia: Double, boltCount: Int, boltGauge: Double, boltPitch: Double,
    endPlateThickness: Double, weldSize: Double,
    isArabic: Boolean
) {
    val viewW = right - left
    val viewH = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val d = section.depth.toFloat()
    val w = section.width.toFloat()
    val scale = min((viewW * 0.6f) / w, (viewH * 0.6f) / d)
    val sW = w * scale
    val sH = d * scale
    val sTf = section.flangeThickness.toFloat() * scale
    val sEp = endPlateThickness.toFloat() * scale
    val sBoltDia = boltDia.toFloat() * scale

    // End plate (front view)
    val epLeft = cx - sW / 2f - 10f
    val epTop = cy - sH / 2f - 5f
    val epW = sW + 20f
    val epH = sH + 10f

    drawRect(PlateGray.copy(alpha = 0.4f), Offset(epLeft, epTop), Size(epW, epH))
    drawRect(PlateGray, Offset(epLeft, epTop), Size(epW, epH), style = Stroke(1.2f))

    // Section outline on end plate (show where beam meets plate)
    drawRect(SteelDarkGray, Offset(cx - sW / 2f, cy - sH / 2f), Size(sW, sH), style = Stroke(1f))
    drawLine(SteelGray, Offset(cx - sW / 2f, cy - sH / 2f + sTf), Offset(cx + sW / 2f, cy - sH / 2f + sTf), 0.8f)
    drawLine(SteelGray, Offset(cx - sW / 2f, cy + sH / 2f - sTf), Offset(cx + sW / 2f, cy + sH / 2f - sTf), 0.8f)

    // Bolt pattern (grid layout)
    val boltR = max(sBoltDia / 2f, 3f)
    val sGauge = boltGauge.toFloat() * scale
    val sPitch = boltPitch.toFloat() * scale
    val cols = 2  // Two columns of bolts
    val rows = max(boltCount / cols, 1)

    val gridStartY = cy - (rows - 1) * sPitch / 2f
    val gridStartX = cx - sGauge / 2f

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val bx = gridStartX + col * sGauge
            val by = gridStartY + row * sPitch
            // Bolt circle (outer)
            drawCircle(BoltOrange, boltR, center = Offset(bx, by))
            drawCircle(Color(0xFF1A1A1A), boltR * 0.4f, center = Offset(bx, by))
            // Cross on bolt
            drawLine(BoltOrange, Offset(bx - boltR * 0.7f, by), Offset(bx + boltR * 0.7f, by), 0.8f)
            drawLine(BoltOrange, Offset(bx, by - boltR * 0.7f), Offset(bx, by + boltR * 0.7f), 0.8f)
        }
    }

    // Weld symbols along flanges
    val weldLbl = if (isArabic) "لحام ${weldSize}mm" else "Fillet ${weldSize}mm"
    drawTextAnnotated(weldLbl, cx, epTop - 12f, WeldRed, 10f, center = true)
    // Weld triangle symbols
    for (wx in listOf(cx - sW / 2f, cx - sW / 4f, cx, cx + sW / 4f, cx + sW / 2f)) {
        val wy = cy - sH / 2f + sTf
        drawPath(
            path = Path().apply {
                moveTo(wx - 4f, wy)
                lineTo(wx + 4f, wy)
                lineTo(wx, wy + 6f)
                close()
            },
            color = WeldRed
        )
    }

    // Bolt info label
    val boltInfo = if (isArabic) {
        "M${boltDia.toInt()} x $boltCount"
    } else {
        "${boltCount}x M${boltDia.toInt()}"
    }
    drawTextAnnotated(boltInfo, cx, epTop + epH + 16f, BoltOrange, 11f, center = true)

    // End plate thickness dimension
    val epLbl = if (isArabic) "tp=${endPlateThickness.toInt()}" else "tp=${endPlateThickness.toInt()}"
    drawTextAnnotated(epLbl, cx, epTop + epH + 30f, DimWhite, 10f, center = true)
}

// ============================================================================
// PROPERTIES TABLE
// ============================================================================

private fun DrawScope.drawPropertiesTable(
    x: Float, y: Float, width: Float, height: Float,
    section: SteelSectionType, memberLength: Double, isArabic: Boolean
) {
    // Background and border
    drawRect(Color(0x22FFFFFF), Offset(x, y), Size(width, height))
    drawRect(ExtGray.copy(alpha = 0.3f), Offset(x, y), Size(width, height), style = Stroke(1f))

    // Header
    val headerText = if (isArabic) "خواص القطاع: ${section.sectionName}" else "STEEL PROPERTIES: ${section.sectionName}"
    drawRect(TblHeaderBg, Offset(x, y), Size(width, 25f))
    drawTextAnnotated(headerText, x + 10f, y + 18f, DimWhite, 12f, bold = true)

    // Properties in 2-column layout
    val area = section.area  // mm²
    val areaCm2 = area / 100.0
    val ixVal = section.ix / 1e4  // mm⁴ → cm⁴
    val sxVal = section.sx / 1e3  // mm³ → cm³
    val zxVal = section.zx / 1e3  // mm³ → cm³
    val wt = section.weight  // kg/m
    val grade = when (section) {
        is SteelSectionType.ISection -> section.grade.displayName
        is SteelSectionType.CSection -> section.grade.displayName
        is SteelSectionType.LSection -> section.grade.displayName
        is SteelSectionType.CHS -> section.grade.displayName
        is SteelSectionType.RHS -> section.grade.displayName
        is SteelSectionType.TSection -> section.grade.displayName
        is SteelSectionType.PlateGirder -> section.grade.displayName
        is SteelSectionType.Pipe -> section.grade.displayName
        is SteelSectionType.BuiltUp -> "-"
    }
    val rxVal = section.rx  // mm

    val rows = if (isArabic) {
        listOf(
            "المساحة: ${"%.1f".format(areaCm2)} cm²" to "الوزن: ${"%.1f".format(wt)} kg/m",
            "القصور Ix: ${"%.0f".format(ixVal)} cm⁴" to "المقطع Sx: ${"%.0f".format(sxVal)} cm³",
            "اللدن Zx: ${"%.0f".format(zxVal)} cm³" to "نصف القطر rx: ${"%.1f".format(rxVal)} mm",
            "الطول: ${"%.0f".format(memberLength)} mm" to "الدرجة: $grade"
        )
    } else {
        listOf(
            "Area: ${"%.1f".format(areaCm2)} cm²" to "Wt: ${"%.1f".format(wt)} kg/m",
            "Ix: ${"%.0f".format(ixVal)} cm⁴" to "Sx: ${"%.0f".format(sxVal)} cm³",
            "Zx: ${"%.0f".format(zxVal)} cm³" to "rx: ${"%.1f".format(rxVal)} mm",
            "Length: ${"%.0f".format(memberLength)} mm" to "Grade: $grade"
        )
    }

    rows.forEachIndexed { i, (left, right) ->
        val ry = y + 45f + i * 18f
        drawTextAnnotated(left, x + 10f, ry, ExtGray, 11f)
        drawTextAnnotated(right, x + width / 2f, ry, ExtGray, 11f)
    }
}
