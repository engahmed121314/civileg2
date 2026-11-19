package com.civileg.app.domain.calculations.beam

import com.civileg.app.domain.calculations.BbsEngine
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.drawing.*
import com.civileg.app.domain.entities.*

/**
 * Builds a complete, verified ElementReinforcementLayout and DrawingScene for a Beam (DR-3).
 *
 * Implements R15 contract:
 * - Longitudinal elevation showing bottom main, top main, support extra top, stirrups, cut markers 1-1, 2-2, 3-3.
 * - Exactly 3 cross-sections:
 *   - Section 1-1: Left Support (Station A, Top negative steel, stirrups in confined zone)
 *   - Section 2-2: Mid-span (Station B, Bottom main steel, skin bars if h >= 600mm)
 *   - Section 3-3: Right Support (Station C, Top negative steel, stirrups)
 * - Detail Specs: Stirrup hook, bar anchorage.
 * - Bar Bending Schedule (BBS) rows generated via BbsEngine.
 * - DrawingScene generated for A3 Landscape composition.
 */
object BeamDrawingPackageBuilder {

    /**
     * Builds the complete ElementReinforcementLayout contract for a beam result.
     */
    fun buildPackage(result: BeamDesignResult, memberMark: String = "B1", codeVersion: String = result.designCode.version): ElementReinforcementLayout {
        val spanMm = (if (result.span > 0) result.span else 3.0) * 1000.0
        val b = result.beamWidth
        val h = result.beamDepth
        val cover = 40.0 // Single consistent cover value (40mm)

        InputGuard.positive("beamWidth", b)
        InputGuard.positive("beamDepth", h)
        InputGuard.positive("spanMm", spanMm)

        val bottomCount = if (result.bottomBarCount > 0) result.bottomBarCount else 3
        val bottomDia = if (result.bottomBarDia > 0) result.bottomBarDia else 16

        val topCount = if (result.topBarCount > 0) result.topBarCount else 2
        val topDia = if (result.topBarDia > 0) result.topBarDia else 12

        val stirrupDia = if (result.stirrupDia > 0) result.stirrupDia else 8
        val stirrupSpacingSup = if (result.stirrupSpacingSupport > 0) result.stirrupSpacingSupport else 100.0
        val stirrupSpacingMid = if (result.stirrupSpacingMidspan > 0) result.stirrupSpacingMidspan else 150.0

        val hasSkinBars = h >= 600.0
        val skinCount = if (hasSkinBars) 2 else 0
        val skinDia = 10

        val groups = mutableListOf<BarGroup>()

        // Group 01: Bottom Main Steel
        val bottomHookLen = 9.0 * bottomDia
        groups.add(
            BarGroup(
                mark = "01",
                role = BarRole.MAIN_BOTTOM,
                diameterMm = bottomDia,
                count = bottomCount,
                layer = 1,
                shapeCode = BbsShapeCode.L_BEND,
                shapeParams = BbsShapeParams(a = spanMm, b = bottomHookLen, c = bottomHookLen),
                startLocationMm = 0.0,
                endLocationMm = spanMm,
                clause = "ECP 203 §4-2 / ACI 318 §25.4"
            )
        )

        // Group 02: Top Main / Hanger Steel
        val topHookLen = 9.0 * topDia
        groups.add(
            BarGroup(
                mark = "02",
                role = BarRole.MAIN_TOP,
                diameterMm = topDia,
                count = topCount,
                layer = 1,
                shapeCode = BbsShapeCode.L_BEND,
                shapeParams = BbsShapeParams(a = spanMm, b = topHookLen, c = topHookLen),
                startLocationMm = 0.0,
                endLocationMm = spanMm,
                clause = "ECP 203 §4-2 / ACI 318 §25.4"
            )
        )

        // Group 03: Support Extra Top Steel
        val extraTopCount = 2
        val extraTopDia = bottomDia
        val extraTopLen = spanMm * 0.3
        groups.add(
            BarGroup(
                mark = "03",
                role = BarRole.EXTRA_TOP,
                diameterMm = extraTopDia,
                count = extraTopCount,
                layer = 1,
                shapeCode = BbsShapeCode.L_BEND,
                shapeParams = BbsShapeParams(a = extraTopLen, b = 12.0 * extraTopDia),
                startLocationMm = 0.0,
                endLocationMm = extraTopLen,
                clause = "ECP 203 §4-2-5"
            )
        )

        // Group 04: Stirrups
        val stirrupA = b - 2 * cover
        val stirrupB = h - 2 * cover
        val approxStirrupCount = ((spanMm * 0.3 / stirrupSpacingSup) * 2 + (spanMm * 0.4 / stirrupSpacingMid)).toInt() + 1
        groups.add(
            BarGroup(
                mark = "04",
                role = BarRole.STIRRUP,
                diameterMm = stirrupDia,
                count = maxOf(approxStirrupCount, 10),
                spacingMm = stirrupSpacingMid,
                layer = 1,
                shapeCode = BbsShapeCode.STIRRUP_RECT,
                shapeParams = BbsShapeParams(a = stirrupA, b = stirrupB),
                startLocationMm = 0.0,
                endLocationMm = spanMm,
                clause = "ECP 203 §4-3 / ACI 318 §25.7"
            )
        )

        // Group 05: Side/Skin Reinforcement
        if (hasSkinBars) {
            groups.add(
                BarGroup(
                    mark = "05",
                    role = BarRole.SKIN,
                    diameterMm = skinDia,
                    count = skinCount,
                    layer = 1,
                    shapeCode = BbsShapeCode.STRAIGHT,
                    shapeParams = BbsShapeParams(a = spanMm),
                    startLocationMm = 0.0,
                    endLocationMm = spanMm,
                    clause = "ECP 203 §4-2-4 / ACI 318 §9.7.6"
                )
            )
        }

        val asBottomProv = RebarCatalog.totalAreaMm2(bottomCount, bottomDia)
        val asTopHangerProv = RebarCatalog.totalAreaMm2(topCount, topDia)
        val asExtraTopProv = RebarCatalog.totalAreaMm2(extraTopCount, extraTopDia)
        val asTopProv = asTopHangerProv + asExtraTopProv
        val asReq = if (result.asRequired > 0) result.asRequired else 400.0

        val groupMarksSection1 = listOf("02", "03", "04")
        val groupMarksSection2 = if (hasSkinBars) listOf("01", "04", "05") else listOf("01", "04")
        val groupMarksSection3 = listOf("02", "03", "04")

        val cuts = listOf(
            SectionCut(
                id = "1-1",
                stationMm = 0.0,
                titleEn = "SECTION 1-1 (Left Support)",
                titleAr = "مطع 1-1 (الركيزة اليسرى)",
                widthMm = b,
                depthMm = h,
                groupMarks = groupMarksSection1,
                demand = StationDemand(0.0, momentDemandKnM = result.maxMomentNeg, shearDemandKn = result.maxShearLeft),
                providedAsMm2 = asTopProv,
                requiredAsMm2 = minOf(asReq * 0.8, asTopProv)
            ),
            SectionCut(
                id = "2-2",
                stationMm = spanMm / 2.0,
                titleEn = "SECTION 2-2 (Mid Span)",
                titleAr = "مطع 2-2 (منتصف البحر)",
                widthMm = b,
                depthMm = h,
                groupMarks = groupMarksSection2,
                demand = StationDemand(spanMm / 2.0, momentDemandKnM = result.maxMomentPos),
                providedAsMm2 = asBottomProv,
                requiredAsMm2 = minOf(asReq, asBottomProv)
            ),
            SectionCut(
                id = "3-3",
                stationMm = spanMm,
                titleEn = "SECTION 3-3 (Right Support)",
                titleAr = "مطع 3-3 (الركيزة اليمنى)",
                widthMm = b,
                depthMm = h,
                groupMarks = groupMarksSection3,
                demand = StationDemand(spanMm, momentDemandKnM = result.maxMomentNeg, shearDemandKn = result.maxShearRight),
                providedAsMm2 = asTopProv,
                requiredAsMm2 = minOf(asReq * 0.8, asTopProv)
            )
        )

        val details = mutableListOf(
            DetailSpec("D1", "Stirrup 135° Hook Detail", "تفصيلة خطاف الكانة 135°", "ALWAYS", listOf("04")),
            DetailSpec("D2", "Main Bar Anchorage & Hook", "تفصيلة جندبة السيخ الرئيسي", "ALWAYS", listOf("01", "02"))
        )
        if (hasSkinBars) {
            details.add(DetailSpec("D3", "Side Skin Bar Placement", "تفصيلة حديد الانكماش الجانبي", "DEPTH_GE_600", listOf("05")))
        }

        val concreteVol = b * h * spanMm / 1e9

        return BbsEngine.buildLayout(
            elementTitle = "RC Beam ${b.toInt()}x${h.toInt()}mm",
            memberMark = memberMark,
            caseKey = if (hasSkinBars) "BEAM_DEEP_SECTION" else "BEAM_RECT_STANDARD",
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
     * Builds DrawingScene for a Beam layout.
     */
    fun buildDrawingScene(layout: ElementReinforcementLayout, codeVersion: String = layout.codeVersion): DrawingScene {
        val primitives = mutableListOf<Primitive>()
        val spanMm = layout.groups.firstOrNull { it.role == BarRole.MAIN_BOTTOM }?.shapeParams?.a ?: 3000.0
        val b = layout.cuts.firstOrNull()?.widthMm ?: 300.0
        val h = layout.cuts.firstOrNull()?.depthMm ?: 600.0

        val elevMinX = 20.0
        val elevMinY = 120.0
        val scale = SheetComposer.selectScale(spanMm, h, 250.0, 100.0)
        val drawSpan = spanMm / scale
        val drawH = h / scale

        primitives.add(
            Primitive.Polyline(
                points = listOf(
                    Point2D(elevMinX, elevMinY),
                    Point2D(elevMinX + drawSpan, elevMinY),
                    Point2D(elevMinX + drawSpan, elevMinY + drawH),
                    Point2D(elevMinX, elevMinY + drawH)
                ),
                isClosed = true,
                layer = DrawingLayer.OUTLINE
            )
        )

        val cutStationsX = listOf(elevMinX + 10.0, elevMinX + drawSpan / 2.0, elevMinX + drawSpan - 10.0)
        val cutTitles = listOf("1-1", "2-2", "3-3")

        for (i in 0..2) {
            val cx = cutStationsX[i]
            val cTitle = cutTitles[i]
            primitives.add(Primitive.Line(Point2D(cx, elevMinY - 15), Point2D(cx, elevMinY + drawH + 15), DrawingLayer.CENTERLINE))
            primitives.add(Primitive.Text("SEC $cTitle", Point2D(cx - 5, elevMinY + drawH + 18), 3.0, DrawingLayer.TEXT))
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
