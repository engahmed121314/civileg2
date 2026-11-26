package com.civileg.app.domain.calculations.ecp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Flat slab helpers: (1) strip deflection ESTIMATE, (2) punching at drop panel / column head.
 * Units: mm, N/mm2, N, N.mm. Page numbers are printed pages of ECP 203-2025; see docs/evidence/ecp2025-flatslab-extras.md.
 *
 * (1) ECP 203-2025 gives NO column-strip / middle-strip deflection method for flat slabs (pp.4-60..4-66 read).
 *     What is code: Ie Eq 4-70/4-72, alpha Eq 4-73, limits Eq 4-74..4-80 with L = long span (p.4-64), strip
 *     moment shares Table 6-4 / 6-5 (pp.6-23, 6-24). What is NOT code: combining strip deflections as
 *     dColumn(x) + dMiddle(y) and averaging both directions (ACI practice, StructurePoint ACI 318-14 flat plate
 *     example, section 5). Every result is therefore labelled ESTIMATE_LABEL.
 *
 * (2) Punching with a drop panel: p.4-26 item 3-a, critical sections at d/2 from the load AND at the perimeter of the
 *     thickness change (drop panel edge), Fig 4-13 (p.4-27), Fig 6-6 (p.6-17). Capacity per Eq 4-46 (p.4-28).
 */
object ECP2025FlatSlabExtras {

    const val ESTIMATE_LABEL =
        "Estimate using ECP 203-2025 general deflection provisions (4-3-1). The code gives no flat slab column/middle strip method."

    // ---------------- (1) strip deflection estimate ----------------

    /**
     * Elastic midspan deflection of a strip of span [spanMm] under uniform service line load [wNPerMm], with service
     * end (negative) moments [mLeft], [mRight] (N.mm, magnitudes) and constant stiffness Ec*Ie:
     * d = 5 w L^4 / (384 Ec Ie) - (mLeft + mRight) L^2 / (16 Ec Ie). Exact beam statics; floored at 0.
     */
    fun stripMidspanDeflection(wNPerMm: Double, spanMm: Double, mLeft: Double, mRight: Double, ec: Double, ie: Double): Double {
        require(wNPerMm >= 0.0 && spanMm > 0.0 && mLeft >= 0.0 && mRight >= 0.0 && ec > 0.0 && ie > 0.0)
        val d = 5.0 * wNPerMm * spanMm.pow(4) / (384.0 * ec * ie) - (mLeft + mRight) * spanMm * spanMm / (16.0 * ec * ie)
        return max(0.0, d)
    }

    /** Gross inertia and extreme-fibre distance of a rectangular strip of width b and thickness t. */
    fun grossInertia(b: Double, t: Double): Double = b * t.pow(3) / 12.0

    /**
     * Ie of a strip for one load level, Eq 4-70 at midspan and at both supports, averaged by Eq 4-72 (both ends
     * continuous) or Eq 4-72 one-end form when [oneEndContinuous]. Ma values are service moment magnitudes (N.mm).
     * Icr values are supplied by the caller from the strip reinforcement.
     */
    fun stripEffectiveInertia(
        fcu: Double, fy: Double, ig: Double, yt: Double,
        maMid: Double, maSupportA: Double, maSupportB: Double,
        icrMid: Double, icrSupportA: Double, icrSupportB: Double,
        oneEndContinuous: Boolean = false
    ): Double {
        val mcr = ECP2025Deflection.crackingMoment(ECP2025Deflection.fctr(fcu, fy), ig, yt)
        val ieMid = ECP2025Deflection.effectiveInertia(mcr, maMid, ig, icrMid)
        val ieA = ECP2025Deflection.effectiveInertia(mcr, maSupportA, ig, icrSupportA)
        return if (oneEndContinuous) {
            ECP2025Deflection.continuousInertiaOneEnd(ieMid, ieA)
        } else {
            val ieB = ECP2025Deflection.effectiveInertia(mcr, maSupportB, ig, icrSupportB)
            ECP2025Deflection.continuousInertiaBothEnds(ieMid, ieA, ieB)
        }
    }

    /**
     * Panel midpoint estimate (ACI practice, NOT ECP): the deflection of a panel centre in one direction is
     * column-strip midspan deflection in that direction plus middle-strip midspan deflection in the orthogonal
     * direction. The two directions are averaged.
     */
    data class PanelEstimate(val viaX: Double, val viaY: Double, val average: Double, val label: String = ESTIMATE_LABEL)

    fun panelMidpoint(columnStripX: Double, middleStripX: Double, columnStripY: Double, middleStripY: Double): PanelEstimate {
        val viaX = columnStripX + middleStripY
        val viaY = columnStripY + middleStripX
        return PanelEstimate(viaX, viaY, (viaX + viaY) / 2.0)
    }

    data class DeflectionEstimate(
        val immediateTotal: Double, val immediateSustained: Double, val immediateLive: Double,
        val alpha: Double, val total: Double,
        val totalCheck: ECP2025Deflection.LimitCheck,   // Eq 4-74, L = long span
        val liveCheck: ECP2025Deflection.LimitCheck,    // Eq 4-76
        val label: String = ESTIMATE_LABEL
    )

    /**
     * Combines panel-midpoint immediate deflections for load levels D, D+sustained live and D+L. Long-term part by
     * Eq 4-73 with [rhoPrime] (compression steel ratio, use 0 if not provided) and time factor [xi] (p.4-62).
     * Immediate live deflection Eq 4-77 = (D+L) - D. Total = (D+L) + alpha x (D+Ls) per 4-3-1-1-2 as implemented in
     * ECP2025Deflection.totalDeflection; the sustained deflection fed in is the (D+Ls) deflection.
     * Limits use L = LONG span (p.4-64). Total limit Eq 4-74 (L/250), live limit Eq 4-76 (L/360).
     */
    fun estimate(dD: Double, dDplusLs: Double, dDplusL: Double, longSpanMm: Double, xi: Double, rhoPrime: Double): DeflectionEstimate {
        val alpha = ECP2025Deflection.longTermMultiplier(xi, rhoPrime)
        val live = ECP2025Deflection.liveImmediate(dDplusL, dD)
        val total = ECP2025Deflection.totalDeflection(dDplusL, dDplusLs, alpha)
        return DeflectionEstimate(
            dDplusL, dDplusLs, live, alpha, total,
            ECP2025Deflection.check(ECP2025Deflection.Limit.TOTAL_ONE_OR_TWO_WAY, total, longSpanMm),
            ECP2025Deflection.check(ECP2025Deflection.Limit.LIVE_IMMEDIATE, live, longSpanMm)
        )
    }

    // ---------------- (2) drop panel / column head ----------------

    /** 6-2-5-2-d-1 (p.6-19): drop thickness below the slab not less than t/4. */
    fun dropMinProjection(slabThickness: Double) = slabThickness / 4.0

    /**
     * 6-2-5-2-d-2 (p.6-19): drop extends from the column axis in each direction not less than 1/6 of the longer
     * span in that direction (centre-to-centre) and not more than 1/4 of the shorter span in that direction.
     * Returns the list of violated clauses (empty = OK). Dimensions are measured from column axis to drop edge.
     */
    fun dropDimensionIssues(slabThickness: Double, projection: Double, extentFromAxis: Double, spanLongerMm: Double, spanShorterMm: Double): List<String> {
        val out = mutableListOf<String>()
        if (projection < dropMinProjection(slabThickness) - 1e-6) out.add("Drop projection below slab < t/4 (6-2-5-2-d-1, p.6-19)")
        if (extentFromAxis < spanLongerMm / 6.0 - 1e-6) out.add("Drop extent from column axis < L/6 (6-2-5-2-d-2, p.6-19)")
        if (extentFromAxis > spanShorterMm / 4.0 + 1e-6) out.add("Drop extent from column axis > Lmin/4 (6-2-5-2-d-2, p.6-19)")
        return out
    }

    /** 6-2-5-2-c "column heads" (p.6-18): column head slope <= 45 deg from vertical, and effective head diameter D <= quarter of the shorter span. */
    fun columnHeadIssues(slopeFromVerticalDeg: Double, effectiveDMm: Double, shorterSpanMm: Double): List<String> {
        val out = mutableListOf<String>()
        if (slopeFromVerticalDeg > 45.0 + 1e-9) out.add("Column head slope > 45 deg from vertical (p.6-18, column heads item 1)")
        if (effectiveDMm > shorterSpanMm / 4.0 + 1e-6) out.add("Effective head diameter D > Lmin/4 (p.6-18, column heads item 2)")
        return out
    }

    data class Section(
        val name: String, val bo: Double, val d: Double, val qcup: Double,
        val vcKn: Double, val vuKn: Double, val quN: Double, val ok: Boolean, val note: String
    ) { val utilization: Double get() = if (vcKn > 0) vuKn / vcKn else 2.0 }

    /** Eq 4-46-a/b/c with the 1.70 N/mm2 cap (p.4-28). a,b = loaded-area sides (a <= b). */
    fun qcup(fcu: Double, d: Double, bo: Double, alpha: Double, a: Double, b: Double, gammaC: Double = 1.5): Double {
        val lambdaS = min(1.0, (1000.0 / d).pow(0.25))
        val root = sqrt(fcu / gammaC)
        val qa = 0.8 * lambdaS * (alpha * d / bo + 0.2) * root
        val qb = 0.316 * lambdaS * (0.5 + a / b) * root
        val qc = 0.316 * lambdaS * root
        return min(min(qa, min(qb, qc)), 1.70)
    }

    enum class Position(val alpha: Double) { INTERIOR(4.0), EDGE(3.0), CORNER(2.0) }

    private fun perimeter(pos: Position, b1: Double, b2: Double) = when (pos) {
        Position.INTERIOR -> 2.0 * (b1 + b2)
        Position.EDGE -> min(2.0 * b1 + b2, b1 + 2.0 * b2)   // smaller = conservative, orientation not an input
        Position.CORNER -> b1 + b2
    }

    /**
     * Two critical sections (p.4-26 item 3-a, concrete only, no shear reinforcement):
     *  1. d/2 from the column or column head face, d = slab + drop projection - cover - bar/2.
     *  2. d/2 from the drop panel edge (perimeter of the thickness change), d = slab thickness only.
     * [colX],[colY] = column (or column head at the soffit) sides; [dropX],[dropY] = drop panel plan sides.
     * [vuKn] = design shear incl. moment-transfer factor (Eq 6-25). Section 2 uses the SAME vuKn (conservative):
     * the printed text read gives no reduction for the load inside the outer perimeter: NOT FOUND.
     * Pass dropX = dropY = 0 when there is no drop: only section 1 is returned.
     */
    fun punchingSections(
        vuKn: Double, fcu: Double, slabThickness: Double, dropProjection: Double,
        colX: Double, colY: Double, dropX: Double, dropY: Double,
        cover: Double, barDia: Double, position: Position
    ): List<Section> {
        require(vuKn >= 0.0 && fcu > 0.0 && slabThickness > 0.0 && colX > 0.0 && colY > 0.0)
        val hasDrop = dropProjection > 0.0 && dropX > 0.0 && dropY > 0.0
        val dIn = slabThickness + (if (hasDrop) dropProjection else 0.0) - cover - barDia / 2.0
        val dOut = slabThickness - cover - barDia / 2.0
        fun section(name: String, d: Double, loadX: Double, loadY: Double, note: String): Section {
            val b1 = loadX + d
            val b2 = loadY + d
            val bo = perimeter(position, b1, b2)
            val q = qcup(fcu, d, bo, position.alpha, min(loadX, loadY), max(loadX, loadY))
            val vc = q * bo * d / 1000.0
            val qu = vuKn * 1000.0 / (bo * d)   // Eq 4-44
            return Section(name, bo, d, q, vc, vuKn, qu, qu <= q, note)
        }
        val list = mutableListOf(section("At column / head (d/2)", dIn, colX, colY, "Eq 4-44, 4-46, p.4-26 item 3-a"))
        if (hasDrop) list.add(section("At drop panel edge (d/2 outside drop)", dOut, dropX, dropY, "p.4-26 item 3-a, thickness-change perimeter; full Vu used (no reduction found in code text)"))
        return list
    }
}
