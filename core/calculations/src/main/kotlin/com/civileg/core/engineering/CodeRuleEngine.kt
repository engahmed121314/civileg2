package com.civileg.core.engineering

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.SupportCondition

/**
 * CodeRuleEngine — PHASE 09 single entry point (spec §13–14, §81).
 *
 * ONE object per code family that bundles every unified engine and facade.
 * ViewModels/consumers depend on this class (via DI) rather than the
 * individual god-engines, so all rules stay injected through
 * [ConcreteCodeParams] — the single source of truth.
 *
 * Engines contained:
 *  - [UnifiedBeamFlexure], [UnifiedBeamShear], [UnifiedBeamDeflection], [UnifiedCrackControl]
 *  - [UnifiedBeamTorsion]
 *  - [UnifiedColumnDesign]
 *  - [UnifiedSlabDesign]
 */
class CodeRuleEngine(
    val params: ConcreteCodeParams,
    val concrete: ConcreteMaterial,
    val steel: SteelMaterial
) {
    // Beam engines
    val beamFlexure = UnifiedBeamFlexure(params, concrete, steel)
    val beamShear = UnifiedBeamShear(params, concrete, steel)
    val beamDeflection = UnifiedBeamDeflection(params, steel)
    val crackControl = UnifiedCrackControl(params)
    val beamTorsion = UnifiedBeamTorsion(params)

    // Column + slab engines
    val column = UnifiedColumnDesign(params, concrete, steel)
    val slab = UnifiedSlabDesign(params, concrete, steel)

    // Facades (combined reports)
    private val beamFacade = BeamDesignFacade(params, concrete, steel)
    private val columnFacade = ColumnDesignFacade(params, concrete, steel)

    // ── Convenience delegates ──

    fun designBeam(
        b: Double, d: Double, h: Double, muKnm: Double, vuKn: Double,
        spanM: Double, support: SupportCondition, tensionRatioPercent: Double,
        clearCoverMm: Double, tensionBarSpacingMm: Double,
        loadCombo: FactoredCombination? = null, computedDeflectionMm: Double? = null,
        tuKnm: Double = 0.0
    ) = beamFacade.design(b, d, h, muKnm, vuKn, spanM, support, tensionRatioPercent,
        clearCoverMm, tensionBarSpacingMm, loadCombo, computedDeflectionMm, tuKnm)

    fun designColumn(
        b: Double, h: Double, puKnm: Double,
        momentXKnm: Double = 0.0, momentYKnm: Double = 0.0,
        vuKn: Double? = null, isSpiral: Boolean = false
    ) = columnFacade.design(b, h, puKnm, momentXKnm, momentYKnm, vuKn, isSpiral)

    fun designSlabTwoWay(
        shortSpanM: Double, longSpanM: Double, h: Double, totalLoadKnm2: Double,
        allEdgesFixed: Boolean, coverMm: Double = 20.0,
        support: SupportCondition = SupportCondition.CONTINUOUS
    ) = slab.designTwoWay(shortSpanM, longSpanM, h, totalLoadKnm2, allEdgesFixed, coverMm, support)

    fun designBeamTorsion(
        b: Double, h: Double, coverMm: Double, tuKnm: Double,
        vuKn: Double = 0.0, dMm: Double = 0.0, thetaDeg: Double = 45.0
    ) = beamTorsion.design(
        UnifiedBeamTorsion.Input(b, h, coverMm, concrete, steel, tuKnm, vuKn, dMm, thetaDeg)
    )

    companion object {
        fun forEcp(concrete: ConcreteMaterial, steel: SteelMaterial) =
            CodeRuleEngine(Ecp203Params, concrete, steel)

        fun forAci(concrete: ConcreteMaterial, steel: SteelMaterial) =
            CodeRuleEngine(Aci318Params, concrete, steel)

        /**
         * Version-aware entry point (Roadmap §1.2 — Code Version Control).
         * Selects the [ConcreteCodeParams] for the requested code edition via
         * [CodeVersionRegistry]; defaults to the family's active edition.
         */
        fun forDesignCode(
            code: DesignCode,
            concrete: ConcreteMaterial,
            steel: SteelMaterial,
            edition: String = CodeVersionRegistry.defaultFor(code).edition
        ) = CodeRuleEngine(CodeVersionRegistry.resolve(code, edition), concrete, steel)
    }
}
