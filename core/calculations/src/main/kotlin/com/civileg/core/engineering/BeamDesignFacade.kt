package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport

/**
 * Beam Design Facade (STEP 5 completion, spec §20/§81).
 *
 * Combines flexure + shear + deflection + crack-control + torsion into a single
 * unified call with an aggregated [CalculationTrace].  The overall status
 * is the worst of the checks (FAIL > WARNING > NOT_CHECKED > PASS).
 *
 * One object per code family; consumers construct with the appropriate
 * [ConcreteCodeParams], [ConcreteMaterial], and [SteelMaterial].
 */
class BeamDesignFacade(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {
    private val flexureEngine = UnifiedBeamFlexure(params, concrete, steel)
    private val shearEngine = UnifiedBeamShear(params, concrete, steel)
    private val deflectionEngine = UnifiedBeamDeflection(params, steel)
    private val crackEngine = UnifiedCrackControl(params)
    private val torsionEngine = UnifiedBeamTorsion(params)

    /**
     * Full beam design: flexure + shear + deflection screening + crack control.
     *
     * @param b section width (mm)
     * @param d effective depth (mm)
     * @param h overall depth (mm)
     * @param muKnm factored moment (kN·m)
     * @param vuKn factored shear (kN)
     * @param spanM span length (m)
     * @param support support condition for deflection screening
     * @param tensionRatioPercent midspan tension steel ratio (%)
     * @param clearCoverMm clear cover to tension bars (mm)
     * @param tensionBarSpacingMm actual bar centre-to-centre spacing (mm)
     * @param loadCombo optional load combination for trace labelling
     * @param computedDeflectionMm optional service-load deflection for Layer 2 check
     * @param tuKnm optional factored torsion (kN·m); 0 / omitted → torsion not checked
     */
    fun design(
        b: Double,
        d: Double,
        h: Double,
        muKnm: Double,
        vuKn: Double,
        spanM: Double,
        support: SupportCondition,
        tensionRatioPercent: Double,
        clearCoverMm: Double,
        tensionBarSpacingMm: Double,
        loadCombo: FactoredCombination? = null,
        computedDeflectionMm: Double? = null,
        tuKnm: Double = 0.0
    ): BeamOutcome {

        // 1. Flexure
        val flex = flexureEngine.design(b, d, h, muKnm, loadCombo)

        // 2. Shear
        val shr = shearEngine.design(b, d, vuKn)

        // 3. Deflection screening
        val defl = deflectionEngine.screen(spanM, h, tensionRatioPercent, support, computedDeflectionMm)

        // 4. Crack control
        val crack = crackEngine.check(tensionBarSpacingMm, clearCoverMm, steel.yieldMpa)

        // 5. Torsion (optional)
        val torsion: UnifiedBeamTorsion.Outcome? = if (tuKnm > 0.0) {
            torsionEngine.design(
                UnifiedBeamTorsion.Input(
                    bMm = b, hMm = h, coverMm = clearCoverMm,
                    concrete = concrete, steel = steel, tuKnm = tuKnm, vuKn = vuKn, dMm = d
                )
            )
        } else null

        // Aggregate trace
        val trace = CalculationTrace()
        val subTraces = listOfNotNull(flex.trace, shr.trace, defl.trace, crack.trace, torsion?.trace)
        subTraces.forEach { sub ->
            sub.all.forEach { e ->
                trace.add(
                    title = e.title,
                    formula = e.formula,
                    substitution = e.substitution,
                    result = e.result,
                    limit = e.limit,
                    status = e.status,
                    utilization = e.utilization,
                    codeReference = e.codeReference
                )
            }
        }

        val outcome = BeamOutcome(
            flexure = flex,
            shear = shr,
            deflection = defl,
            crackControl = crack,
            torsion = torsion,
            overallStatus = trace.overall,
            trace = trace
        )
        // P0 safety gate: independently re-validate the result for physical /
        // code sanity. A sanity ERROR forces the overall status to FAIL even if
        // the calculation trace passed — a result must never be trusted blindly.
        val sanityReport = EngineeringSanityEngine.check(outcome)
        val overallStatus = when {
            sanityReport.hasError -> CheckStatus.FAIL
            sanityReport.hasWarning && trace.overall == CheckStatus.PASS -> CheckStatus.WARNING
            else -> trace.overall
        }
        return outcome.copy(sanity = sanityReport, overallStatus = overallStatus)
    }

    data class BeamOutcome(
        val flexure: UnifiedBeamFlexure.Outcome,
        val shear: UnifiedBeamShear.Outcome,
        val deflection: UnifiedBeamDeflection.Outcome,
        val crackControl: UnifiedCrackControl.Outcome,
        val torsion: UnifiedBeamTorsion.Outcome?,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace,
        val sanity: SanityReport = SanityReport("BeamOutcome", emptyList())
    ) {
        val isSafe: Boolean get() =
            overallStatus == CheckStatus.PASS || overallStatus == CheckStatus.WARNING
    }
}
