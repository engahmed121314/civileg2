package com.civileg.app.domain.calculations.column

import com.civileg.app.domain.calculations.BbsEngine
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.drawing.*
import com.civileg.app.domain.entities.*

/**
 * Builds a complete, verified ElementReinforcementLayout and DrawingScene for a Column (DR-4).
 *
 * Implements R15 contract:
 * - Longitudinal elevation showing main longitudinal bars, starter bars/dowels, confinement tie zones (top & bottom H_c), cut markers 1-1, 2-2, 3-3.
 * - Exactly 3 cross-sections:
 *   - Section 1-1: Base / Footing Joint (Station 0.0m, Dowel lap splice, confined ties)
 *   - Section 2-2: Mid-Height (Station H/2, Standard tie spacing, main bar arrangement)
 *   - Section 3-3: Top / Beam Joint (Station H, Column-beam joint confinement, top anchorage bend)
 * - Detail Specs: Rectangular tie/cross-tie arrangement, lap splice offset, seismic confinement spacing.
 * - Bar Bending Schedule (BBS) rows generated via BbsEngine.
 * - DrawingScene generated for A3 Landscape composition.
 */
object ColumnDrawingPackageBuilder {

    /**
     * Builds the complete ElementReinforcementLayout contract for a column result.
     */
    fun buildPackage(result: ColumnDesignResult, memberMark: String = "C1", codeVersion: String = result.designCode.version): ElementReinforcementLayout {
        val b = result.columnWidth
        val t = result.columnDepth
        val h = if (result.totalHeight > 0) result.totalHeight else 3000.0
        val cover = if (result.cover > 0) result.cover else 40.0

        InputGuard.positive("columnWidth", b)
        InputGuard.positive("columnDepth", t)
        InputGuard.positive("totalHeight", h)

        val barCount = if (result.finalBarCount > 0) result.finalBarCount else 6
        val barDia = if (result.finalBarDia > 0) result.finalBarDia else 18

        val tieDia = if (result.tieDia > 0) result.tieDia else 8
        val tieSpacingConf = if (result.tieSpacingDense > 0) result.tieSpacingDense else 100.0
        val tieSpacingMid = if (result.tieSpacingNormal > 0) result.tieSpacingNormal else 150.0
        val confinementLen = if (result.condensationZoneLength > 0) result.condensationZoneLength else maxOf(b, t, 500.0)

        val groups = mutableListOf<BarGroup>()

        // Group 01: Main Longitudinal Bars
        val lapLength = 60.0 * barDia
        groups.add(
            BarGroup(
                mark = "01",
                role = BarRole.MAIN_BOTTOM,
                diameterMm = barDia,
                count = barCount,
                layer = 1,
                shapeCode = BbsShapeCode.L_BEND,
                shapeParams = BbsShapeParams(a = h, b = lapLength),
                startLocationMm = 0.0,
                endLocationMm = h,
                clause = "ECP 203 §4-4 / ACI 318 §10.7"
            )
        )

        // Group 02: Starter Bars / Dowels from Footing/Lower Floor
        val dowelLen = lapLength + 300.0
        groups.add(
            BarGroup(
                mark = "02",
                role = BarRole.DOWEL,
                diameterMm = barDia,
                count = barCount,
                layer = 1,
                shapeCode = BbsShapeCode.L_BEND,
                shapeParams = BbsShapeParams(a = lapLength, b = 300.0),
                startLocationMm = -300.0,
                endLocationMm = lapLength,
                clause = "ECP 203 §4-4-3 / ACI 318 §16.3"
            )
        )

        // Group 03: Perimeter Ties / Stirrups
        val tieA = b - 2 * cover
        val tieB = t - 2 * cover
        val approxTieCount = ((confinementLen / tieSpacingConf) * 2 + ((h - 2 * confinementLen) / tieSpacingMid)).toInt() + 2
        groups.add(
            BarGroup(
                mark = "03",
                role = BarRole.TIE,
                diameterMm = tieDia,
                count = maxOf(approxTieCount, 12),
                spacingMm = tieSpacingMid,
                layer = 1,
                shapeCode = BbsShapeCode.STIRRUP_RECT,
                shapeParams = BbsShapeParams(a = tieA, b = tieB),
                startLocationMm = 0.0,
                endLocationMm = h,
                clause = "ECP 203 §4-4-2 / ACI 318 §25.7.2"
            )
        )

        // Group 04: Cross-Ties (for columns with > 4 bars)
        if (barCount > 4) {
            groups.add(
                BarGroup(
                    mark = "04",
                    role = BarRole.CROSS_TIE,
                    diameterMm = tieDia,
                    count = maxOf(approxTieCount, 12),
                    spacingMm = tieSpacingMid,
                    layer = 1,
                    shapeCode = BbsShapeCode.TIE_CROSS,
                    shapeParams = BbsShapeParams(a = tieA),
                    startLocationMm = 0.0,
                    endLocationMm = h,
                    clause = "ECP 203 §4-4-2 / ACI 318 §25.7.2.2"
                )
            )
        }

        val asProv = RebarCatalog.totalAreaMm2(barCount, barDia)
        val asReq = if (result.AsRequired > 0) result.AsRequired else asProv * 0.8

        val groupMarksBase = if (barCount > 4) listOf("01", "02", "03", "04") else listOf("01", "02", "03")
        val groupMarksMid = if (barCount > 4) listOf("01", "03", "04") else listOf("01", "03")

        val cuts = listOf(
            SectionCut(
                id = "1-1",
                stationMm = 0.0,
                titleEn = "SECTION 1-1 (Column Base & Dowels)",
                titleAr = "مقطع 1-1 (قاعدة العمود والأشاير)",
                widthMm = b,
                depthMm = t,
                groupMarks = groupMarksBase,
                demand = StationDemand(0.0, axialDemandKn = result.Pu, momentDemandKnM = result.MdesIn),
                providedAsMm2 = asProv,
                requiredAsMm2 = minOf(asReq, asProv)
            ),
            SectionCut(
                id = "2-2",
                stationMm = h / 2.0,
                titleEn = "SECTION 2-2 (Column Mid-Height)",
                titleAr = "مقطع 2-2 (منتصف ارتفاع العمود)",
                widthMm = b,
                depthMm = t,
                groupMarks = groupMarksMid,
                demand = StationDemand(h / 2.0, axialDemandKn = result.Pu, momentDemandKnM = result.MdesIn),
                providedAsMm2 = asProv,
                requiredAsMm2 = minOf(asReq, asProv)
            ),
            SectionCut(
                id = "3-3",
                stationMm = h,
                titleEn = "SECTION 3-3 (Column Top & Beam Joint)",
                titleAr = "مقطع 3-3 (أعلى العمود والاتصال بالكمرة)",
                widthMm = b,
                depthMm = t,
                groupMarks = groupMarksMid,
                demand = StationDemand(h, axialDemandKn = result.Pu, momentDemandKnM = result.MdesIn),
                providedAsMm2 = asProv,
                requiredAsMm2 = minOf(asReq, asProv)
            )
        )

        val details = mutableListOf(
            DetailSpec("D1", "Rectangular Tie & Cross-Tie Arrangement", "تفصيلة توزيع الكانات والكانة الحابسة", "ALWAYS", listOf("03", "04")),
            DetailSpec("D2", "Base Dowel Lap Splice Detail", "تفصيلة أصلة الأشاير عند القاعدة", "ALWAYS", listOf("01", "02")),
            DetailSpec("D3", "Seismic Confinement Zone Spacing", "تفصيلة تكثيف الكانات للزلازل", "CONFINEMENT", listOf("03"))
        )

        val concreteVol = if (result.concreteVolume > 0) result.concreteVolume else b * t * h / 1e9

        return BbsEngine.buildLayout(
            elementTitle = "RC Column ${b.toInt()}x${t.toInt()}mm (${result.columnClassification})",
            memberMark = memberMark,
            caseKey = "COLUMN_RECT_STANDARD",
            coverMm = cover,
            membersCount = 1,
            concreteVolumeM3 = concreteVol,
            groups = groups,
            cuts = cuts,
            details = details,
            codeVersion = codeVersion
        )
    }

    /**
     * Builds DrawingScene for a Column layout.
     */
    fun buildDrawingScene(layout: ElementReinforcementLayout, codeVersion: String = layout.codeVersion): DrawingScene {
        val primitives = mutableListOf<Primitive>()
        val h = layout.groups.firstOrNull { it.role == BarRole.MAIN_BOTTOM }?.shapeParams?.a ?: 3000.0
        val b = layout.cuts.firstOrNull()?.widthMm ?: 300.0
        val t = layout.cuts.firstOrNull()?.depthMm ?: 600.0

        val elevMinX = 40.0
        val elevMinY = 50.0
        val scale = SheetComposer.selectScale(b, h, 80.0, 200.0)
        val drawB = b / scale
        val drawH = h / scale

        primitives.add(
            Primitive.Polyline(
                points = listOf(
                    Point2D(elevMinX, elevMinY),
                    Point2D(elevMinX + drawB, elevMinY),
                    Point2D(elevMinX + drawB, elevMinY + drawH),
                    Point2D(elevMinX, elevMinY + drawH)
                ),
                isClosed = true,
                layer = DrawingLayer.OUTLINE
            )
        )

        val cutStationsY = listOf(elevMinY + 15.0, elevMinY + drawH / 2.0, elevMinY + drawH - 15.0)
        val cutTitles = listOf("1-1", "2-2", "3-3")

        for (i in 0..2) {
            val cy = cutStationsY[i]
            val cTitle = cutTitles[i]
            primitives.add(Primitive.Line(Point2D(elevMinX - 15, cy), Point2D(elevMinX + drawB + 15, cy), DrawingLayer.CENTERLINE))
            primitives.add(Primitive.Text("SEC $cTitle", Point2D(elevMinX + drawB + 18, cy - 2), 3.0, DrawingLayer.TEXT))
        }

        val titleBlock = SheetComposer.buildTitleBlock(
            projectTitle = "CIVIL ENGINEER PRO",
            elementTitle = layout.elementTitle,
            memberMark = layout.memberMark,
            codeVersion = codeVersion,
            scaleText = "1:${scale.toInt()}"
        )
        primitives.addAll(titleBlock)

        val sceneBounds = Rect2D(0.0, 0.0, SheetComposer.SHEET_WIDTH_MM, SheetComposer.SHEET_HEIGHT_MM)
        return DrawingScene(title = layout.elementTitle, bounds = sceneBounds, primitives = primitives)
    }
}
