package com.civileg.app.domain.calculations.base

/** End-support/continuity condition of a one-way slab or beam span, as used by
 * deemed-to-satisfy deflection span/depth tables:
 * ECP 203-2020 Table 4-10 (doc page 4-64) and SBC 304-CR-2018 Table 9.3.1.1. */
enum class SlabSupportCondition {
    SIMPLY_SUPPORTED, ONE_END_CONTINUOUS, BOTH_ENDS_CONTINUOUS, CANTILEVER
}

/** Result of a deemed-to-satisfy span/depth deflection check.
 * assessed=false means the table does not apply and no check was performed;
 * safe is meaningless unless assessed is true. */
data class DeemedToSatisfy(val assessed: Boolean, val safe: Boolean, val summary: String)

/** Code-grounded deemed-to-satisfy deflection limits for one-way ribbed (hordi) slabs.
 * Values are used verbatim from the cited tables; no formula is inferred. */
object HordiDeemedToSatisfy {

    fun label(cond: SlabSupportCondition): String = when (cond) {
        SlabSupportCondition.SIMPLY_SUPPORTED -> "simply supported"
        SlabSupportCondition.ONE_END_CONTINUOUS -> "one end continuous"
        SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> "both ends continuous"
        SlabSupportCondition.CANTILEVER -> "cantilever"
    }

    /** ECP 203-2020 Table 4-10 (doc page 4-64, PDF 147): clear-span to TOTAL depth
     * Ln/t for ribbed slabs (البلاطات ذات الأعصاب), valid for spans <= 6 m
     * (cantilevers <= 2 m) and ordinary buildings/loads; base steel fy = 420,
     * other grades scale by Eq. 4-81: 0.40 + fy/700. The span input is treated
     * as the clear span Ln (a center-to-center input is conservative here). */
    fun ecp(spanMm: Double, totalThicknessMm: Double, fy: Double, cond: SlabSupportCondition): DeemedToSatisfy {
        val spanCapMm = if (cond == SlabSupportCondition.CANTILEVER) 2000.0 else 6000.0
        if (spanMm > spanCapMm) {
            return DeemedToSatisfy(false, true,
                "span ${(spanMm / 1000).toInt()} m exceeds Table 4-10 applicability (${(spanCapMm / 1000).toInt()} m) — detailed deflection calculation required (§4-4-9); serviceabilityAssessed=false")
        }
        val base = when (cond) {
            SlabSupportCondition.SIMPLY_SUPPORTED -> 20.0
            SlabSupportCondition.ONE_END_CONTINUOUS -> 23.0
            SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> 24.0
            SlabSupportCondition.CANTILEVER -> 8.0
        }
        val factor = 0.40 + fy / 700.0 // Eq. 4-81
        val limit = base * factor
        val ratio = spanMm / totalThicknessMm
        return DeemedToSatisfy(true, ratio <= limit,
            "Ln/t = ${String.format("%.1f", ratio)} vs limit ${String.format("%.1f", limit)} (ECP 203-2020 Table 4-10 ribbed slabs, ${label(cond)}, Eq. 4-81 factor ${String.format("%.3f", factor)})")
    }

    /** SBC 304-CR-2018 §9.3.1.1 + Table 9.3.1.1 (minimum depth of nonprestressed
     * beams), applied to a one-way joist per §9.8.1 on the overall depth h;
     * fy != 420 scales by 9.3.1.1.1: 0.4 + fy/700. Applies only when the member
     * is not supporting/attached to construction likely to be damaged by large
     * deflections (9.3.1.1 scope). */
    fun sbc(spanMm: Double, totalThicknessMm: Double, fy: Double, cond: SlabSupportCondition): DeemedToSatisfy {
        val base = when (cond) {
            SlabSupportCondition.SIMPLY_SUPPORTED -> 16.0
            SlabSupportCondition.ONE_END_CONTINUOUS -> 18.5
            SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> 21.0
            SlabSupportCondition.CANTILEVER -> 8.0
        }
        val factor = 0.4 + fy / 700.0 // SBC 304 9.3.1.1.1
        val limit = base * factor
        val ratio = spanMm / totalThicknessMm
        return DeemedToSatisfy(true, ratio <= limit,
            "l/h = ${String.format("%.1f", ratio)} vs limit ${String.format("%.1f", limit)} (SBC 304-CR-2018 Table 9.3.1.1, ${label(cond)}, 9.3.1.1.1 factor ${String.format("%.3f", factor)})")
    }
}
