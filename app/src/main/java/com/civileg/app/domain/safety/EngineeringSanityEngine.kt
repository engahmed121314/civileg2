package com.civileg.app.domain.safety

import com.civileg.core.engineering.BeamDesignFacade
import com.civileg.core.engineering.UnifiedColumnDesign
import com.civileg.core.engineering.UnifiedSlabDesign
import com.civileg.core.sanity.EngineeringSanityEngine as CoreSanity
import com.civileg.core.sanity.SanityReport
import com.civileg.core.sanity.SanitySeverity
import com.civileg.app.utils.CalculationValidator

/**
 * App-layer engineering safety gate (Strategic Roadmap — Pillar 1.1).
 *
 * This is the UI/domain-facing façade. ALL checks are performed by the
 * [CoreSanity] engine in `:core:calculations` (the single source of truth,
 * which is pure-JVM and unit-tested there). This object only:
 *   1. wraps a computed core [SanityReport] into the roadmap's [SanityResult], and
 *   2. adds the generic pre-checks (geometry/rebar shape) from the roadmap §1.1.
 *
 * Rule of thumb: never re-implement an equation check here — delegate to core.
 */

enum class SanityStatus { OK, WARNING, ERROR }

data class SanityCheck(
    val rule: String,
    val message: String,
    val clause: String? = null,
    val severity: SanityStatus
)

data class SanityResult(
    val status: SanityStatus,
    val checks: List<SanityCheck>,
    val blockedFromOutput: Boolean
) {
    val ok: Boolean get() = status != SanityStatus.ERROR
}

object EngineeringSanityEngine {

    /** Wrap an already-computed core report (single source of truth). */
    fun from(report: SanityReport): SanityResult {
        val checks = report.findings.map {
            SanityCheck(
                rule = it.code,
                message = it.message,
                clause = it.codeReference,
                severity = when (it.severity) {
                    SanitySeverity.ERROR -> SanityStatus.ERROR
                    SanitySeverity.WARNING -> SanityStatus.WARNING
                    SanitySeverity.INFO -> SanityStatus.OK
                }
            )
        }
        val status = when {
            report.hasError -> SanityStatus.ERROR
            report.hasWarning -> SanityStatus.WARNING
            else -> SanityStatus.OK
        }
        return SanityResult(status, checks, blockedFromOutput = report.hasError)
    }

    /**
     * Adapt the generic per-result QA report ([CalculationValidator.ValidationReport])
     * into the same [SanityResult] contract the UI/PDF consume. The consistency
     * checks themselves stay in [CalculationValidator]; this only re-shapes its
     * findings so `out.sanity.warnings` is uniform across all producers.
     */
    fun fromValidation(report: CalculationValidator.ValidationReport): SanityResult {
        val checks = buildList {
            report.errors.forEach { add(SanityCheck("VALIDATION", it, "Calculation QA", SanityStatus.ERROR)) }
            report.warnings.forEach { add(SanityCheck("VALIDATION", it, "Calculation QA", SanityStatus.WARNING)) }
        }
        val status = when {
            report.errors.isNotEmpty() -> SanityStatus.ERROR
            report.warnings.isNotEmpty() -> SanityStatus.WARNING
            else -> SanityStatus.OK
        }
        return SanityResult(status, checks, blockedFromOutput = report.errors.isNotEmpty())
    }

    // ── Outcome-level gates (delegate to core engines) ──

    fun validate(outcome: BeamDesignFacade.BeamOutcome): SanityResult = from(CoreSanity.check(outcome))
    fun validate(outcome: UnifiedColumnDesign.Outcome): SanityResult = from(CoreSanity.check(outcome))
    fun validate(outcome: UnifiedColumnDesign.ShearOutcome): SanityResult = from(CoreSanity.check(outcome))
    fun validate(outcome: UnifiedSlabDesign.Outcome): SanityResult = from(CoreSanity.check(outcome))

    /**
     * Generic pre-gate over raw inputs (roadmap §1.1, rules 1–4).
     * Catches impossible geometry / reinforcement BEFORE any engine runs.
     */
    fun validateGeometry(
        dimensions: Map<String, Double>,
        reinforcement: Map<String, Double>,
        cover: Double,
        effectiveDepth: Double,
        maxBarDiameter: Double,
        minMemberDimension: Double
    ): SanityResult {
        val checks = mutableListOf<SanityCheck>()

        // قاعدة 1: ممنوع أبعاد سالبة أو صفرية
        dimensions.forEach { (name, v) ->
            if (v <= 0.0)
                checks += SanityCheck(
                    "GEOMETRY_POSITIVE",
                    "$name = $v غير مسموح (≤ 0)",
                    "General Engineering",
                    SanityStatus.ERROR
                )
        }

        // قاعدة 2: As ≤ 0 / NaN / Infinite = ERROR
        reinforcement.forEach { (name, v) ->
            when {
                v <= 0.0 -> checks += SanityCheck("REBAR_NEGATIVE", "$name = $v ≤ 0", null, SanityStatus.ERROR)
                v.isNaN() -> checks += SanityCheck("REBAR_NAN", "$name is NaN", null, SanityStatus.ERROR)
                v.isInfinite() -> checks += SanityCheck("REBAR_INF", "$name is Infinite", null, SanityStatus.ERROR)
            }
        }

        // قاعدة 3: Cover ≥ effective depth = ERROR
        if (cover >= effectiveDepth)
            checks += SanityCheck(
                "COVER_VS_DEPTH",
                "Cover ($cover) ≥ d ($effectiveDepth) مستحيل هندسياً",
                null,
                SanityStatus.ERROR
            )

        // قاعدة 4: قطر الحديد > بُعد القطاع = FAIL
        if (maxBarDiameter > minMemberDimension)
            checks += SanityCheck(
                "BAR_FITS_SECTION",
                "Ø$maxBarDiameter لا يدخل في القطاع $minMemberDimension",
                null,
                SanityStatus.ERROR
            )

        val status = when {
            checks.any { it.severity == SanityStatus.ERROR } -> SanityStatus.ERROR
            checks.any { it.severity == SanityStatus.WARNING } -> SanityStatus.WARNING
            else -> SanityStatus.OK
        }
        return SanityResult(status, checks, blockedFromOutput = status == SanityStatus.ERROR)
    }
}
