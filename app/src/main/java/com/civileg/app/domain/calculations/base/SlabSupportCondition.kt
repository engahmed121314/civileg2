package com.civileg.app.domain.calculations.base

/** End-support/continuity condition of a one-way slab or beam span, as used by
 * deemed-to-satisfy deflection span/depth tables:
 * ECP 203-2020 Table 4-10 (doc page 4-64) and SBC 304-CR-2018 Table 9.3.1.1.
 * UNSPECIFIED means the caller did not state the support system: the deemed
 * check MUST NOT run (no case is presumed; a simply-supported assumption is
 * NOT conservative against a cantilever, whose limit is far stricter). */
enum class SlabSupportCondition {
    UNSPECIFIED, SIMPLY_SUPPORTED, ONE_END_CONTINUOUS, BOTH_ENDS_CONTINUOUS, CANTILEVER
}

/** User-declared applicability of the deemed-to-satisfy tables with respect to
 * loading and supported elements:
 * ECP 203-2020 Table 4-10 notes ج/د exclude members carrying elements that
 * could be damaged by deflection, spans > 6 m (cantilevers > 2 m), heavy or
 * irregular loads and unusual buildings; SBC 304-CR-2018 §9.3.1.1 excludes
 * members supporting/attached to construction likely to be damaged by large
 * deflections. UNDECLARED (default) means applicability is unknown and the
 * deemed check MUST NOT run — a standing caveat note is not a substitute for
 * the user's declaration. ORDINARY_NO_SENSITIVE_ELEMENTS also declares normalweight
 * concrete: the tables are for normalweight concrete (lightweight modifiers are not implemented). */
enum class DeflectionLoadingApplicability {
    UNDECLARED, ORDINARY_NO_SENSITIVE_ELEMENTS, SENSITIVE_OR_IRREGULAR
}

/** Result of a deemed-to-satisfy span/depth deflection check.
 * assessed=false means no check was performed (table inapplicable or inputs
 * undeclared); safe is meaningless unless assessed is true. */
data class DeemedToSatisfy(val assessed: Boolean, val safe: Boolean, val summary: String)

/** Code-grounded deemed-to-satisfy deflection limits for one-way ribbed (hordi) slabs.
 * Values are used verbatim from the cited tables; no formula is inferred.
 * The check runs ONLY on an explicitly selected support condition AND an
 * explicit ordinary-loading/no-sensitive-elements declaration. */
object HordiDeemedToSatisfy {

    fun label(cond: SlabSupportCondition): String = when (cond) {
        SlabSupportCondition.UNSPECIFIED -> "unspecified"
        SlabSupportCondition.SIMPLY_SUPPORTED -> "simply supported"
        SlabSupportCondition.ONE_END_CONTINUOUS -> "one end continuous"
        SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> "both ends continuous"
        SlabSupportCondition.CANTILEVER -> "cantilever"
    }

    private fun gate(cond: SlabSupportCondition, loading: DeflectionLoadingApplicability, codeLabel: String): String? = when {
        cond == SlabSupportCondition.UNSPECIFIED ->
            "support condition not specified — deemed-to-satisfy NOT performed ($codeLabel); select the actual support system"
        loading == DeflectionLoadingApplicability.UNDECLARED ->
            "ordinary-loading / no-damage-sensitive-elements applicability not declared ($codeLabel) — deemed-to-satisfy NOT performed"
        loading == DeflectionLoadingApplicability.SENSITIVE_OR_IRREGULAR ->
            "deemed-to-satisfy table inapplicable to deflection-sensitive or irregular loading ($codeLabel) — detailed deflection calculation required"
        else -> null
    }

    /** ECP 203-2020 Table 4-10 (doc page 4-64, PDF 147 of the supplied scan):
     * clear-span to TOTAL depth Ln/t for ribbed slabs (البلاطات ذات الأعصاب),
     * valid for spans <= 6 m (cantilevers <= 2 m) and ordinary buildings/loads;
     * base steel fy = 420, other grades scale by Eq. 4-81: 0.40 + fy/700.
     * The span input is treated as the clear span Ln (a center-to-center input
     * is conservative here). */
    fun ecp(spanMm: Double, totalThicknessMm: Double, fy: Double, cond: SlabSupportCondition, loading: DeflectionLoadingApplicability): DeemedToSatisfy {
        gate(cond, loading, "ECP Table 4-10 notes ج/د")?.let { return DeemedToSatisfy(false, true, it) }
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
            SlabSupportCondition.UNSPECIFIED -> return DeemedToSatisfy(false, true, "unreachable: gated above")
        }
        val factor = 0.40 + fy / 700.0 // Eq. 4-81
        val limit = base * factor
        val ratio = spanMm / totalThicknessMm
        return DeemedToSatisfy(true, ratio <= limit,
            "Ln/t = ${String.format("%.1f", ratio)} vs limit ${String.format("%.1f", limit)} (declared normalweight concrete; ECP 203-2020 Table 4-10 ribbed slabs, ${label(cond)}, Eq. 4-81 factor ${String.format("%.3f", factor)})")
    }

    /** ACI 318M-14 §9.3.1.1 + Table 9.3.1.1 (minimum depth of nonprestressed
     * beams: l/16, l/18.5, l/21, l/8; fy != 420 scales by 9.3.1.1.1: 0.4 + fy/700),
     * read from the supplied bilingual ACI translation, vol 1 code page 211,
     * whose TOC/provenance matches the official ACI 318M-14 preview (independent
     * recovery check, Nov 25). Joist routing per §9.8.1 on the overall depth h. */
    fun aci(spanMm: Double, totalThicknessMm: Double, fy: Double, cond: SlabSupportCondition, loading: DeflectionLoadingApplicability): DeemedToSatisfy {
        gate(cond, loading, "ACI 318M-14 §9.3.1.1 scope")?.let { return DeemedToSatisfy(false, true, it) }
        val base = when (cond) {
            SlabSupportCondition.SIMPLY_SUPPORTED -> 16.0
            SlabSupportCondition.ONE_END_CONTINUOUS -> 18.5
            SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> 21.0
            SlabSupportCondition.CANTILEVER -> 8.0
            SlabSupportCondition.UNSPECIFIED -> return DeemedToSatisfy(false, true, "unreachable: gated above")
        }
        // 9.3.1.1.1 multiplies the minimum DEPTH h = l/base by (0.4 + fy/700), so the allowed l/h is base / factor.
        // (ECP Eq. 4-81 is the opposite: it multiplies the allowed Ln/t itself.)
        val factor = 0.4 + fy / 700.0
        val limit = base / factor
        val ratio = spanMm / totalThicknessMm
        return DeemedToSatisfy(true, ratio <= limit,
            "l/h = ${String.format("%.1f", ratio)} vs limit ${String.format("%.1f", limit)} (declared normalweight concrete; ACI 318M-14 Table 9.3.1.1, ${label(cond)}, 9.3.1.1.1 factor ${String.format("%.3f", factor)})")
    }

    /** SBC 304-CR-2018 §9.3.1.1 + Table 9.3.1.1 (minimum depth of nonprestressed
     * beams), applied to a one-way joist per §9.8.1 on the overall depth h;
     * fy != 420 scales by 9.3.1.1.1: 0.4 + fy/700. Scope condition of 9.3.1.1
     * (no deflection-sensitive attached construction) is gated by the explicit
     * loading declaration. */
    fun sbc(spanMm: Double, totalThicknessMm: Double, fy: Double, cond: SlabSupportCondition, loading: DeflectionLoadingApplicability): DeemedToSatisfy {
        gate(cond, loading, "SBC 304 §9.3.1.1 scope")?.let { return DeemedToSatisfy(false, true, it) }
        val base = when (cond) {
            SlabSupportCondition.SIMPLY_SUPPORTED -> 16.0
            SlabSupportCondition.ONE_END_CONTINUOUS -> 18.5
            SlabSupportCondition.BOTH_ENDS_CONTINUOUS -> 21.0
            SlabSupportCondition.CANTILEVER -> 8.0
            SlabSupportCondition.UNSPECIFIED -> return DeemedToSatisfy(false, true, "unreachable: gated above")
        }
        // 9.3.1.1.1 multiplies the minimum DEPTH h = l/base by (0.4 + fy/700), so the allowed l/h is base / factor.
        val factor = 0.4 + fy / 700.0
        val limit = base / factor
        val ratio = spanMm / totalThicknessMm
        return DeemedToSatisfy(true, ratio <= limit,
            "l/h = ${String.format("%.1f", ratio)} vs limit ${String.format("%.1f", limit)} (declared normalweight concrete; SBC 304-CR-2018 Table 9.3.1.1, ${label(cond)}, 9.3.1.1.1 factor ${String.format("%.3f", factor)})")
    }
}
