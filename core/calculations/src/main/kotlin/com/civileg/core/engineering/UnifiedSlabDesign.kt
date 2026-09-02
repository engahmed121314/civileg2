package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import com.civileg.core.math.SafeMath
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport
import kotlin.math.max

/**
 * Unified two-way slab design (Slab DoD §83, spec §20/§81).
 *
 * ONE skeleton — moment coefficients are injected via [ConcreteCodeParams],
 * then each direction's governing moment is solved by the SAME
 * [UnifiedBeamFlexure] engine already used for beams (no equation
 * duplication, spec §3). Shear uses [UnifiedBeamShear] on a 1 m strip.
 *
 * Golden-gated against the app ECP/ACI slab coefficient tables and the
 * underlying flexure engine.
 */
class UnifiedSlabDesign(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {
    private val flexureEngine = UnifiedBeamFlexure(params, concrete, steel)
    private val shearEngine = com.civileg.core.engineering.UnifiedBeamShear(params, concrete, steel)

    /**
     * @param shortSpanM short span Lx (m) · longSpanM long span Ly (m)
     * @param h slab thickness (mm) · totalLoadKnm2 total factored load w (kN/m²)
     * @param allEdgesFixed true for all-edges-fixed/continuous panel
     * @param coverMm clear cover (mm), default 20
     * @param support support condition for min-thickness screen
     */
    fun designTwoWay(
        shortSpanM: Double,
        longSpanM: Double,
        h: Double,
        totalLoadKnm2: Double,
        allEdgesFixed: Boolean,
        coverMm: Double = 20.0,
        support: SupportCondition = SupportCondition.CONTINUOUS
    ): Outcome {
        SafeMath.requirePositive(shortSpanM, "Lx"); SafeMath.requirePositive(longSpanM, "Ly")
        SafeMath.requirePositive(h, "h"); SafeMath.requirePositive(totalLoadKnm2, "w")
        val trace = CalculationTrace()
        val ref = params.reference + " §5 / §8"
        val lx = shortSpanM
        val ratio = longSpanM / lx

        val coeffs = params.twoWaySlabCoefficients(ratio, allEdgesFixed)
        trace.add(
            title = "Two-way slab moment coefficients",
            formula = "M = α·w·L² ; α by Ly/Lx=${"%.2f".format(ratio)}",
            substitution = "α+S=${coeffs.posShort}, α−S=${coeffs.negShort}, α+L=${coeffs.posLong}, α−L=${coeffs.negLong}",
            result = "Ly/Lx=${"%.2f".format(ratio)}",
            limit = "coefficient table",
            status = CheckStatus.PASS,
            codeReference = ref
        )

        val d = h - coverMm - 6.0   // effective depth (≈ Ø12 bar + half cover)
        val muShort = max(coeffs.posShort, coeffs.negShort) * totalLoadKnm2 * lx * lx   // kN·m/m
        val muLong = max(coeffs.posLong, coeffs.negLong) * totalLoadKnm2 * lx * lx

        // Route each direction through the shared flexure engine (1 m strip).
        val shortFlex = flexureEngine.design(b = 1000.0, d = d, h = h, muKnm = muShort)
        val longFlex = flexureEngine.design(b = 1000.0, d = d, h = h, muKnm = muLong)

        shortFlex.trace.all.forEach { e ->
            trace.add(title = "[Short dir] ${e.title}", formula = e.formula, substitution = e.substitution,
                result = e.result, limit = e.limit, status = e.status, utilization = e.utilization, codeReference = e.codeReference)
        }
        longFlex.trace.all.forEach { e ->
            trace.add(title = "[Long dir] ${e.title}", formula = e.formula, substitution = e.substitution,
                result = e.result, limit = e.limit, status = e.status, utilization = e.utilization, codeReference = e.codeReference)
        }

        // Shear on 1 m strip: Vu ≈ w·Lx/2 (kN/m)
        val vuShort = totalLoadKnm2 * lx / 2.0
        val shortShear = shearEngine.design(b = 1000.0, d = d, vuKn = vuShort)
        shortShear.trace.all.forEach { e ->
            trace.add(title = "[Short dir] ${e.title}", formula = e.formula, substitution = e.substitution,
                result = e.result, limit = e.limit, status = e.status, utilization = e.utilization, codeReference = e.codeReference)
        }

        // Minimum-thickness screen
        val minH = max(lx * 1000.0 / params.minSlabThicknessRatio(support), 100.0)
        val thickStatus = if (h >= minH) CheckStatus.PASS else CheckStatus.FAIL
        trace.add(
            title = "Minimum thickness screen",
            formula = "h ≥ L / ${params.minSlabThicknessRatio(support)}",
            substitution = "L = ${"%.0f".format(lx * 1000)} mm ; h = ${"%.0f".format(h)} mm",
            result = "${"%.0f".format(h)} mm",
            limit = "≥ ${"%.0f".format(minH)} mm",
            status = thickStatus,
            codeReference = ref
        )

        val outcome = Outcome(
            coefficients = coeffs,
            muShortKnm = muShort, muLongKnm = muLong,
            shortDir = shortFlex, longDir = longFlex,
            shear = shortShear, minThicknessMm = minH,
            isSafe = trace.overall != CheckStatus.FAIL,
            overallStatus = trace.overall,
            trace = trace
        )
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overallStatus = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(sanity = sanityReport, overallStatus = overallStatus)
    }

    data class Outcome(
        val coefficients: SlabMomentCoeffs,
        val muShortKnm: Double,
        val muLongKnm: Double,
        val shortDir: UnifiedBeamFlexure.Outcome,
        val longDir: UnifiedBeamFlexure.Outcome,
        val shear: UnifiedBeamShear.Outcome,
        val minThicknessMm: Double,
        val isSafe: Boolean,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace,
        val sanity: SanityReport = SanityReport("SlabOutcome", emptyList())
    )
}
