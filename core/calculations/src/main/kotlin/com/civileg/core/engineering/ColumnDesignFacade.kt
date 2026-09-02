package com.civileg.core.engineering

/**
 * Column Design Facade (Column DoD §82 completion, spec §20/§81).
 *
 * Combines axial+bending reinforcement, ties, and shear into a single call
 * with an aggregated [CalculationTrace]. Overall status = worst of the parts
 * (FAIL > WARNING > NOT_CHECKED > PASS). One object per code family.
 */
class ColumnDesignFacade(
    private val params: ConcreteCodeParams,
    private val concrete: ConcreteMaterial,
    private val steel: SteelMaterial
) {
    private val columnEngine = UnifiedColumnDesign(params, concrete, steel)

    /**
     * @param b section width (mm) · h section depth (mm)
     * @param puKnm factored axial load (kN)
     * @param momentXKnm factored moment about x (kN·m)
     * @param momentYKnm factored moment about y (kN·m)
     * @param vuKn factored shear (kN) — optional; omit to skip shear subtrace
     * @param isSpiral true for spiral, false for tied
     */
    fun design(
        b: Double,
        h: Double,
        puKnm: Double,
        momentXKnm: Double = 0.0,
        momentYKnm: Double = 0.0,
        vuKn: Double? = null,
        isSpiral: Boolean = false
    ): ColumnOutcome {

        val col = columnEngine.design(b, h, puKnm, momentXKnm, momentYKnm, isSpiral)

        val shear = vuKn?.let { columnEngine.designShear(vuKn = it, b = b, h = h) }

        val trace = CalculationTrace()
        col.trace.all.forEach { e ->
            trace.add(
                title = e.title, formula = e.formula, substitution = e.substitution,
                result = e.result, limit = e.limit, status = e.status,
                utilization = e.utilization, codeReference = e.codeReference
            )
        }
        shear?.trace?.all?.forEach { e ->
            trace.add(
                title = e.title, formula = e.formula, substitution = e.substitution,
                result = e.result, limit = e.limit, status = e.status,
                utilization = e.utilization, codeReference = e.codeReference
            )
        }

        return ColumnOutcome(
            axial = col,
            shear = shear,
            overallStatus = trace.overall,
            trace = trace
        )
    }

    data class ColumnOutcome(
        val axial: UnifiedColumnDesign.Outcome,
        val shear: UnifiedColumnDesign.ShearOutcome?,
        val overallStatus: CheckStatus,
        val trace: CalculationTrace
    ) {
        val isSafe: Boolean get() =
            overallStatus == CheckStatus.PASS || overallStatus == CheckStatus.WARNING
    }
}
