package com.civileg.app.domain.calculations.ecp

import kotlin.math.sqrt

/**
 * Serviceability deflection building blocks, ECP 203-2025 chapter 4, clause 4-3-1 (printed pp. 4-60 to 4-66).
 * Units: mm, N/mm2, N.mm. Every equation below was transcribed from the page images of the supplied edition
 * (PDF page = printed page + 80 for chapter 4). See docs/evidence/ecp2025-deflection.md.
 *
 * SCOPE: these are member-level code formulas. The ECP text read (4-60..4-66, and TOC for 6-2-5) gives NO
 * column-strip / middle-strip deflection procedure for flat slabs, and Eq 4-82 is for two-way slabs on stiff
 * beams, not flat slabs. Flat slab deflection itself therefore stays NOT ASSESSED; this object only provides
 * the code's inertia, long-term factor and limit checks for deflections that a caller obtains elsewhere.
 */
object ECP2025Deflection {

    /** Eq 4-71-b / 4-71-c (p.4-61), transcribed as printed: 0.6 sqrt(fcu) for fy <= 500, 0.37 sqrt(fcu) for fy > 500. */
    fun fctr(fcu: Double, fy: Double): Double {
        require(fcu > 0.0 && fy > 0.0)
        return if (fy <= 500.0) 0.6 * sqrt(fcu) else 0.37 * sqrt(fcu)
    }

    /** Eq 4-71-a (p.4-61): Mcr = fctr * Ig / yt. */
    fun crackingMoment(fctr: Double, ig: Double, yt: Double): Double {
        require(ig > 0.0 && yt > 0.0)
        return fctr * ig / yt
    }

    /**
     * Eq 4-70 (p.4-60): Ie = (Mcr/Ma)^3 Ig + [1 - (Mcr/Ma)^3] Icr, Icr not to exceed Ig (p.4-61).
     * Ma <= Mcr: the section is uncracked, Ie = Ig (the formula would exceed Ig; this guard is an implementation
     * convention, not a printed clause).
     */
    fun effectiveInertia(mcr: Double, ma: Double, ig: Double, icr: Double): Double {
        require(ig > 0.0 && icr > 0.0 && mcr > 0.0 && ma >= 0.0)
        if (ma <= mcr) return ig
        val r3 = Math.pow(mcr / ma, 3.0)
        val icrCapped = minOf(icr, ig)
        return r3 * ig + (1.0 - r3) * icrCapped
    }

    /** Eq 4-72-a (first printed form, both ends continuous): Ie = 0.70 Ie,mid + 0.15 (Ie1 + Ie2). */
    fun continuousInertiaBothEnds(ieMid: Double, ie1: Double, ie2: Double): Double =
        0.70 * ieMid + 0.15 * (ie1 + ie2)

    /** Eq 4-72 (second printed form, labelled 4-72-a again in the book; one end continuous): Ie = 0.85 Ie,mid + 0.15 Ie1. */
    fun continuousInertiaOneEnd(ieMid: Double, ie1: Double): Double =
        0.85 * ieMid + 0.15 * ie1

    /**
     * Time factor xi (p.4-62, Fig 4-31): 3 months 1.0, 6 months 1.2, 12 months 1.4, five years or more 2.0.
     * Other durations need the Fig 4-31 curve, which was not digitised: returns null (NOT ASSESSED).
     */
    fun timeFactorXi(months: Int): Double? = when {
        months >= 60 -> 2.0
        months == 12 -> 1.4
        months == 6 -> 1.2
        months == 3 -> 1.0
        else -> null
    }

    /** Eq 4-73 (p.4-62): alpha = xi / (1 + 50 rho'), rho' = compression steel ratio at mid-span (support for cantilevers). */
    fun longTermMultiplier(xi: Double, rhoPrime: Double): Double {
        require(xi > 0.0 && rhoPrime >= 0.0)
        return xi / (1.0 + 50.0 * rhoPrime)
    }

    /** Total = immediate + alpha x immediate-from-sustained-load (4-3-1-1-2 / 4-3-1-1-3, pp.4-62, 4-63). */
    fun totalDeflection(immediateTotal: Double, immediateSustained: Double, alpha: Double): Double =
        immediateTotal + alpha * immediateSustained

    enum class Limit(val eq: String, val divisor: Double, val page: String) {
        TOTAL_ONE_OR_TWO_WAY("4-74", 250.0, "4-63"),       // beams, one- and two-way slabs
        CANTILEVER_TOTAL("4-75", 450.0, "4-63"),           // cantilevers
        LIVE_IMMEDIATE("4-76", 360.0, "4-63"),             // immediate deflection from live load, non-sensitive elements
        INCREMENTAL_SENSITIVE("4-78", 480.0, "4-64"),      // added deflection after finishes, elements likely damaged
        INCREMENTAL_NOT_SENSITIVE("4-79", 240.0, "4-64")   // added deflection after finishes, elements not likely damaged
    }

    /** Allowable deflection in mm for span L in mm (L = short span for two-way, long span for flat slabs, p.4-64). */
    fun allowable(limit: Limit, spanMm: Double): Double {
        require(spanMm > 0.0)
        return spanMm / limit.divisor
    }

    /** Eq 4-77 (p.4-63): live-load immediate deflection = (D+L deflection) - (D deflection). */
    fun liveImmediate(deltaDplusL: Double, deltaD: Double): Double = deltaDplusL - deltaD

    /**
     * Eq 4-80 (p.4-64), transcribed as printed: dIncr = [dD + dLs][alpha1 - alpha2] + [dL - dLs].
     * The printed brackets are unbalanced; this is the product of the first two brackets plus the third.
     */
    fun incrementalDeflection(dD: Double, dLs: Double, alpha1: Double, alpha2: Double, dL: Double): Double =
        (dD + dLs) * (alpha1 - alpha2) + (dL - dLs)

    data class LimitCheck(val limit: Limit, val value: Double, val allowable: Double) {
        val ok: Boolean get() = value <= allowable
    }

    fun check(limit: Limit, valueMm: Double, spanMm: Double) = LimitCheck(limit, valueMm, allowable(limit, spanMm))

    enum class Element { SOLID_SLAB, RIBBED_SLAB_OR_BURIED_BEAM, RIGID_BEAM }
    enum class Support { SIMPLE, ONE_END_CONTINUOUS, BOTH_ENDS_CONTINUOUS, CANTILEVER }

    /**
     * Table 4-10 (p.4-65): span / thickness ratios. One-way members, spans <= 6 m, cantilevers < 2 m.
     * Table values are for fy = 420. For other steel with fy <= 500 the code says the table values are DIVIDED by
     * Eq 4-81 = 0.40 + fy/700 (printed word: "qisma"). For fy > 500 deflection must be computed: returns null.
     * WARNING: dividing lowers the allowed ratio for fy > 420 and RAISES it for fy < 420, the opposite sense of
     * the ACI 318 table 7.3.1.1 footnote (multiply). Implemented as printed; flagged for lead decision.
     */
    fun spanToDepthRatio(element: Element, support: Support, fy: Double): Double? {
        if (fy > 500.0) return null
        val base = when (element) {
            Element.SOLID_SLAB -> when (support) { Support.SIMPLE -> 25.0; Support.ONE_END_CONTINUOUS -> 28.0; Support.BOTH_ENDS_CONTINUOUS -> 31.0; Support.CANTILEVER -> 10.0 }
            Element.RIBBED_SLAB_OR_BURIED_BEAM -> when (support) { Support.SIMPLE -> 20.0; Support.ONE_END_CONTINUOUS -> 23.0; Support.BOTH_ENDS_CONTINUOUS -> 24.0; Support.CANTILEVER -> 8.0 }
            Element.RIGID_BEAM -> when (support) { Support.SIMPLE -> 12.0; Support.ONE_END_CONTINUOUS -> 14.0; Support.BOTH_ENDS_CONTINUOUS -> 16.0; Support.CANTILEVER -> 5.0 }
        }
        return base / fyModifier(fy)
    }

    /** Eq 4-81 (p.4-65): 0.40 + fy/700. */
    fun fyModifier(fy: Double): Double = 0.40 + fy / 700.0

    /**
     * Eq 4-82 (p.4-66): t = a (0.85 + fy/1600) / (15 + 25/(b/a) + 10 betaP); governing minimum is max(100 mm, t).
     * ONLY for two-way slabs ON STIFF BEAMS (4-3-1-3-2), spans < 6 m, live load <= 5 kN/m2, no deflection-sensitive
     * non-structural elements. NOT a flat slab method. Returns null when a precondition is not met.
     * a = shorter dimension (mm), b = longer dimension (mm), betaP = continuous edge length / total perimeter.
     */
    fun twoWaySlabOnBeamsMinThickness(a: Double, b: Double, fy: Double, betaP: Double, liveLoadKnM2: Double, longerSpanMm: Double, sensitiveNonStructural: Boolean): Double? {
        require(a > 0.0 && b >= a && betaP in 0.0..1.0)
        if (longerSpanMm >= 6000.0 || liveLoadKnM2 > 5.0 || sensitiveNonStructural) return null
        val t = a * (0.85 + fy / 1600.0) / (15.0 + 25.0 / (b / a) + 10.0 * betaP)
        return maxOf(100.0, t)
    }
}
