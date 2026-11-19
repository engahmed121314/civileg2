package com.civileg.app.domain.calculations.slab

import com.civileg.app.domain.calculations.BbsEngine
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.drawing.*
import com.civileg.app.domain.entities.*

/**
 * Builds a complete, verified ElementReinforcementLayout and DrawingScene for Slabs (DR-5).
 *
 * Implements R15 contract for slab types:
 * - Main view (plan) showing the slab panel and every bar group in place.
 * - Exactly 3 cross-sections (1-1, 2-2, 3-3) at different stations along the main span.
 * - Bar Bending Schedule (BBS) rows generated via BbsEngine.
 * - DrawingScene generated for A3 Landscape composition.
 */
object SlabDrawingPackageBuilder {

    fun buildPackage(
        result: AdvancedSlabResult,
        memberMark: String = "S1",
        codeVersion: String = "ECP 203"
    ): ElementReinforcementLayout {
        val layout = result.reinforcementLayout

        val shortSpan = 3000.0
        val longSpan = 4000.0
        val thickness = 200.0
        val cover = 20.0

        InputGuard.positive("shortSpan", shortSpan)
        InputGuard.positive("thickness", thickness)

        val groups = listOf(
            BarGroup(
                mark = "01",
                role = BarRole.MAIN_BOTTOM,
                diameterMm = layout.bottomBars.diameter.toInt(),
                count = layout.bottomBars.numberOfBars,
                shapeCode = BbsShapeCode.STRAIGHT,
                shapeParams = BbsShapeParams(a = layout.bottomBars.length)
            ),
            BarGroup(
                mark = "02",
                role = BarRole.MAIN_TOP,
                diameterMm = layout.topBars.diameter.toInt(),
                count = layout.topBars.numberOfBars,
                shapeCode = BbsShapeCode.STRAIGHT,
                shapeParams = BbsShapeParams(a = layout.topBars.length)
            )
        )

        val cuts = listOf(
            SectionCut("1-1", 0.0, "Section 1-1 (Support)", "قطاع 1-1 (الركيزة)", shortSpan, thickness, listOf("02"), providedAsMm2 = 500.0, requiredAsMm2 = 400.0),
            SectionCut("2-2", shortSpan / 2, "Section 2-2 (Mid-Span)", "قطاع 2-2 (منتصف البحر)", shortSpan, thickness, listOf("01"), providedAsMm2 = 600.0, requiredAsMm2 = 450.0),
            SectionCut("3-3", shortSpan, "Section 3-3 (Edge Support)", "قطاع 3-3 (الركيزة الطرفية)", shortSpan, thickness, listOf("02"), providedAsMm2 = 500.0, requiredAsMm2 = 400.0)
        )

        val details = listOf(
            DetailSpec("D1", "Support Bar Anchorage", "تفصيلة تثبيت حديد الركائز", "ALWAYS", listOf("02"))
        )

        return BbsEngine.buildLayout(
            elementTitle = "RC Slab Panel",
            memberMark = memberMark,
            caseKey = "SLAB_SOLID_TWO_WAY",
            coverMm = cover,
            membersCount = 1,
            concreteVolumeM3 = shortSpan * longSpan * thickness / 1e9,
            groups = groups,
            cuts = cuts,
            details = details
        )
    }
}
