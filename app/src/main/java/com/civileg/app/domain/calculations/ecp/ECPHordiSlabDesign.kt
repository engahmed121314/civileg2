package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.domain.entities.SlabDesignResult
import kotlin.math.*

/**
 * Explicit-context calls assess the source-based ECP203-2020 rib section.
 * No-context calls retain a held legacy preview for unconverted callers.
 * Neither path establishes whole-member safety or permits execution outputs.
 */
class ECPHordiSlabDesign : HordiSlabDesign {

    companion object {
        private const val GAMMA_C = 1.5
        private const val GAMMA_S = 1.15
        private const val PHI_FLEXURE = 0.9
        private const val PHI_SHEAR = 0.75
    }

    override fun designHordiSlab(
        fcu: Double,
        fy: Double,
        ribWidth: Double,
        ribSpacing: Double,
        totalThickness: Double,
        toppingThickness: Double,
        span: Double,
        designMoment: Double,
        designShear: Double,
        loadCombination: LoadCombination,
        sectionContext: com.civileg.core.engine.rc.ecp.Ecp203RibbedSection.Context?
    ): SlabDesignResult {
        if (sectionContext != null) return assessSourceSection(fcu, fy, ribWidth, ribSpacing,
            totalThickness, toppingThickness, span, designMoment, designShear, sectionContext)
        // ── InputGuard (ADR-010) — ECP 203 ──
        InputGuard.positive("fcu", fcu)
        InputGuard.positive("fy", fy)
        InputGuard.positive("ribWidth", ribWidth)
        InputGuard.positive("ribSpacing", ribSpacing)
        InputGuard.positive("totalThickness", totalThickness)
        InputGuard.positive("span", span)
        InputGuard.notNull("loadCombination", loadCombination)
        InputGuard.nonNegative("designMoment", designMoment)
        InputGuard.nonNegative("designShear", designShear)
        val fc = fcu / GAMMA_C
        val d = totalThickness - 20.0 - 10.0 // cover + half bar diameter

        InputGuard.positive("effectiveDepth", d)

        // Required area of steel per rib
        val Mu = designMoment * 1e6 // N.mm
        val Rn = Mu / (PHI_FLEXURE * ribWidth * d * d)
        val m = fy / (0.85 * fc)
        val discriminant = 1.0 - 2.0 * m * Rn / fy
        val rho = (1.0 - sqrt(max(0.0, discriminant))) / m
        val AsReq = rho * ribWidth * d

        // Min steel
        val minAs = max(0.15 / 100.0 * ribWidth * d, 1.3 * AsReq / 100.0)
        val AsProv = maxOf(AsReq, minAs)

        // Bar selection
        val barDia = 12.0
        val barArea = PI * barDia * barDia / 4.0
        val nBars = ceil(AsProv / barArea).toInt().coerceIn(1, 4)
        val AsProvActual = nBars * barArea

        // Shear capacity
        val Vc = 0.24 * sqrt(fc) * ribWidth * d / 1000.0 * PHI_SHEAR

        // Safety must include the steel actually selected, not just shear.
        // The four-bar cap can leave flexure under-provided at low shear.
        // This consistency guard does not validate the legacy design method.
        val flexureWithinModel = discriminant.isFinite() && discriminant >= 0.0
        val steelUtilization = AsProv / AsProvActual
        val shearUtilization = if (Vc > 0) designShear / Vc else 2.0
        val utilizationRatio = maxOf(
            steelUtilization, shearUtilization,
            if (flexureWithinModel) 0.0 else 2.0
        )
        // Full ECP method verification is still open. Legacy numeric previews
        // must never become an overall safety approval, even at low demand.
        val isSafe = false
        val warnings = buildList {
            add("ECP ribbed-slab method verification is incomplete. Safety assessment and execution exports are blocked.")
            if (!flexureWithinModel) add("Moment exceeds the implemented flexural model range.")
            if (AsProvActual < AsProv) add("Selected rib bars do not provide the required flexural reinforcement.")
        }

        return SlabDesignResult(
            requiredReinforcement = AsReq,
            providedReinforcement = AsProvActual,
            barDiameter = barDia,
            barSpacing = ribSpacing,
            minThickness = totalThickness,
            shearCapacity = Vc,
            isSafe = isSafe,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            safetyAssessmentBlocked = true
        )
    }

    /** Factored forces are already per rib. No load-combination multiplier is applied again. */
    private fun assessSourceSection(fcu: Double, fy: Double, ribWidth: Double, ribSpacing: Double,
        totalThickness: Double, toppingThickness: Double, span: Double, designMoment: Double,
        designShear: Double, context: com.civileg.core.engine.rc.ecp.Ecp203RibbedSection.Context
    ): SlabDesignResult {
        val a = com.civileg.core.engine.rc.ecp.Ecp203RibbedSection.assess(fcu, fy,
            ribWidth, ribSpacing, totalThickness, toppingThickness, span, designMoment, designShear, context)
        val required = requireNotNull(a.requiredSteelMm2) {
            "Moment exceeds the verified singly reinforced section domain. Increase section size or use a separately verified doubly reinforced design (ECP203-2020 printed4-5/6, Table4-1)."
        }
        require(a.momentResistanceKnM > 0.0 && a.shear.shearResistanceKn > 0.0) {
            "Actual reinforcement is outside the supported positive-resistance section domain."
        }
        // Numeric strength ratios stay numeric. Geometry/detailing failures are
        // separate explicit checks, never an invented utilization of 200 percent.
        val utilization = maxOf(required / a.layout.providedSteelMm2,
            designMoment / a.momentResistanceKnM, designShear / a.shear.shearResistanceKn)
        val bars = context.tensionLayers.joinToString(" + ") { "${it.count} x ${it.diameterMm} mm" }
        return SlabDesignResult(
            requiredReinforcement = required,
            providedReinforcement = a.layout.providedSteelMm2,
            barDiameter = context.tensionLayers.first().diameterMm,
            barSpacing = ribSpacing,
            minThickness = totalThickness,
            shearCapacity = a.shear.shearResistanceKn,
            isSafe = false,
            utilizationRatio = utilization,
            safetyAssessmentBlocked = true,
            warnings = buildList {
                add("Section forces, actual bar layout, flexure and web shear assessed. Whole-member serviceability approval, durability, topping reinforcement, cross-rib provision and anchorage are incomplete; execution exports remain blocked.")
                if (!a.geometry.geometryChecksPass) {
                    add("Rib/topping geometry does not meet ECP ribbed-slab dimensional limits.")
                    add("Source-based action: satisfy clear rib gap <=700 mm, rib width >=${a.geometry.minimumRibWidthMm} mm and topping >=${a.geometry.minimumToppingMm} mm, then recalculate all checks (ECP203-2020 printed6-15/16, Fig6-4).")
                }
                if (!a.flexureChecksPass) {
                    add("Selected actual bars fail the section flexure or minimum/maximum steel check.")
                    add("Source-based action: redesign the section or actual fitting bar layout to meet equilibrium, minimum steel and the singly reinforced maximum. Adding bars beyond the Table4-1 limit is not a remedy (ECP203-2020 printed4-1..4-9, Eqs4-1/2/9 and Table4-1).")
                }
                if (!a.shear.shearChecksPass) {
                    add("Section shear strength or link detailing check fails.")
                    when {
                        a.shear.concreteOnlyRequired -> add("Source-based action: resize the concrete section for concrete-only shear where h<=${a.shear.concreteOnlyDepthLimitMm} mm; additional links cannot increase this permitted resistance (ECP203-2020 printed4-22, clause4-2-2-1-6(d)(2)).")
                        !a.shear.withinConcreteMaximum -> add("Source-based action: revise the concrete section to meet q<=qmax before selecting links; extra links cannot exceed the concrete maximum (ECP203-2020 printed4-17, Eq4-27).")
                        else -> add("Source-based action: redesign the actual vertical links for the required steel ratio and detailing, including diameter>=8 mm, spacing<=200 mm and applicable transverse-leg requirements (ECP203-2020 printed4-20..4-22, Eq4-33 and clause4-2-2-1-6).")
                    }
                }
                if (a.immediateDeflection != null) add("Immediate simply supported UDL deflection is calculated separately. Long-term deformation, loading/finish timing, crack width and full serviceability limits are not yet approved.")
            },
            codeNotes = listOf(
                "ECP203-2020 bounded section method: printed4-1..4-9,4-17..4-22,6-15/16,6-42,7-6/7-7.",
                "Section checks pass=${a.sectionChecksPass}; region=${context.region}; effective flange width=${a.effectiveFlangeWidthMm} mm; centroid effective depth=${a.layout.effectiveDepthMm} mm.",
                "Actual tension layers, from tension face inward: $bars. As=${a.layout.providedSteelMm2} mm2; MRd=${a.momentResistanceKnM} kN.m/rib.",
                "Layer compatibility: neutral axis=${a.flexure.neutralAxisDepthMm} mm; actual layer design stresses=${a.flexure.layerStressMpa} MPa; all layers at design yield=${a.flexure.allLayersAtDesignYield}. Plane sections, Es=200000 MPa, concrete ultimate strain=0.003.",
                "Cover to outside links=${context.coverToOutsideLinksMm} mm; aggregate=${context.nominalMaximumAggregateMm} mm; clear bar gap=${a.layout.minimumClearGapMm} mm. Exposure/fire cover not approved.",
                "Vertical links=${context.links.legs} legs x ${context.links.diameterMm} mm @ ${context.links.spacingMm} mm, fy=${context.links.fyMpa} MPa. Concrete-only clause applies=${a.shear.concreteOnlyRequired}.",
                "Required one-way cross ribs=${a.requiredCrossRibs}; provision/detailing not checked. Characteristic live load=${context.characteristicLiveLoadKnM2} kN/m2.",
                "Input moment and shear are already factored section forces. Entered total thickness is not a verified minimum-depth or serviceability result.",
                "Applied flexure equations: fcd=0.67*fcu/1.50; fyd=fy/1.15; a=0.8*c; sigma_i=min(fyd,Es*0.003*(di-c)/c); sum(As_i*sigma_i)=C(c). MRd uses force/first-moment equilibrium with the0.95d arm and verified singly reinforced ceilings (printed4-1..4-9)."
            ) + (a.immediateDeflection?.let { sls ->
                fun f(value: Double) = String.format(java.util.Locale.US, "%.6g", value)
                listOf(
                    "Immediate deflection only, simply supported uniform characteristic loads: L=${f(context.immediateDeflectionInput!!.analysisSpanMm)} mm; qD=${f(sls.dead.characteristicLineLoadKnPerM)} kN/m/rib; qD+L=${f(sls.deadPlusLive.characteristicLineLoadKnPerM)} kN/m/rib. Dead load includes self weight. No ULS force or load factor is reused.",
                    "Ec=4400*sqrt(fcu)=${f(sls.stiffness.elasticModulusMpa)} MPa (Eq2-1, printed2-23); n=Es/Ec=${f(sls.stiffness.modularRatio)}. Gross T-section Ig=${f(sls.stiffness.grossInertiaMm4)} mm4; transformed cracked Icr=${f(sls.stiffness.crackedInertiaMm4)} mm4; cracked neutral axis=${f(sls.stiffness.crackedNeutralAxisMm)} mm. Fully cracked elastic tension-steel stress bound=${f(sls.deadPlusLive.fullyCrackedElasticTensionSteelStressMpa)} MPa; no cracked steel yielding is admitted by this elastic model.",
                    "Mcr=0.6*sqrt(fcu)*Ig/yt=${f(sls.stiffness.crackingMomentKnM)} kN.m (Eq4-71, printed4-60). Ie=(Mcr/Ma)^3*Ig+[1-(Mcr/Ma)^3]*Icr<=Ig (Eq4-70, printed4-59). IeD=${f(sls.dead.effectiveInertiaMm4)}; IeD+L=${f(sls.deadPlusLive.effectiveInertiaMm4)} mm4.",
                    "Elastic simply supported UDL: delta=5*q*L^4/(384*Ec*Ie). deltaD=${f(sls.dead.immediateDeflectionMm)} mm; deltaD+L=${f(sls.deadPlusLive.immediateDeflectionMm)} mm. deltaLive=deltaD+L-deltaD=${f(sls.immediateLiveDeflectionMm)} mm, using separate load-case stiffness (Eq4-77, printed4-62). Not a whole-serviceability pass."
                )
            } ?: emptyList())
        )
    }

}