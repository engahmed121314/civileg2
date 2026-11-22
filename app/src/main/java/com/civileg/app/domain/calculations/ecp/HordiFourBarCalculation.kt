package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.HordiSlabDesign
import com.civileg.app.domain.entities.HordiSlabDesignSnapshot
import com.civileg.app.domain.entities.HordiSlabInputs
import com.civileg.core.engine.rc.ecp.Ecp203FourBarArrangement

/** Assesses supplied layers unchanged unless a project candidate is explicitly selected.
 * An explicit candidate is recalculated before one frozen capture.
 * Does not rewrite saved results, choose bar diameters, infer bar lengths/weights,
 * or expose an unrequested economy option through an unrelated UI control.
 */
object HordiFourBarCalculation {
    fun calculate(entered: HordiSlabInputs, designer: HordiSlabDesign,
        policy: Ecp203FourBarArrangement.Policy? = null
    ): HordiSlabDesignSnapshot {
        val input = prepare(entered, policy)
        val result = with(input) {
            designer.designHordiSlab(fcu, fy, ribWidth, ribSpacing, totalThickness,
                toppingThickness, span, designMoment, designShear, loadCombination, sectionContext)
        }
        if (input.fourBarArrangementPolicy == null) return HordiSlabDesignSnapshot.capture(input, result)
        require(result.boundedSectionChecksPass == true) {
            "Selected reinforcement arrangement fails the bounded section checks. Revise the section, bars or links; adding a paired bar cannot override minimum/maximum steel, flexure, shear or geometry limits."
        }
        val section = requireNotNull(result.calculatedSection)
        val note = "Four-bar arrangement policy: ${input.fourBarArrangementPolicy}; actual adopted rows=" +
            requireNotNull(input.sectionContext).tensionLayers.joinToString("+") { it.count.toString() } +
            ". Project detailing choice, not a code-mandated bar-count parity rule. All execution holds remain."
        return HordiSlabDesignSnapshot.capture(input, result.copy(
            calculatedSection = section.copy(placementBasis = section.placementBasis +
                "__FOUR_BAR_" + input.fourBarArrangementPolicy.name),
            codeNotes = result.codeNotes + note))
    }

    internal fun prepare(input: HordiSlabInputs, policy: Ecp203FourBarArrangement.Policy?): HordiSlabInputs {
        val context = input.sectionContext
        val layers = context?.tensionLayers.orEmpty()
        // The selected policy covers the supplied bottom-tension case only.
        // Do not silently mirror a project detailing preference into support/top steel.
        val bottomTension = context?.region ==
            com.civileg.core.engine.rc.ecp.Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION
        val targetFour = layers.map { it.count } == listOf(4) ||
            layers.map { it.count } == listOf(3, 1)
        val equalDiameter = layers.isNotEmpty() && layers.all { it.diameterMm == layers.first().diameterMm }
        input.fourBarArrangementPolicy?.let { recorded ->
            require(input.designCode == "ECP" && context != null && bottomTension && equalDiameter) {
                "Recorded four-bar policy requires its actual ECP source-section layout."
            }
            val recordedRows = Ecp203FourBarArrangement.arrange(input.ribWidth, input.totalThickness,
                context.coverToOutsideLinksMm, context.links.diameterMm,
                context.nominalMaximumAggregateMm, layers.first().diameterMm, recorded)
            require(layers == recordedRows) { "Recorded four-bar policy does not match the actual adopted rows." }
        }
        // A new calculation must never silently adopt a different bar count.
        // Existing recorded policy/layout pairs were validated above and remain intact.
        if (policy == null) return input
        if (input.designCode != "ECP" || context == null || !bottomTension || !targetFour || !equalDiameter) {
            require(policy != Ecp203FourBarArrangement.Policy.EXPLICIT_ECONOMY_FOUR_BARS ||
                input.fourBarArrangementPolicy == policy) {
                "Explicit four-bar economy requires the supported equal-diameter four-bar bottom-tension ECP source-section input."
            }
            require(input.fourBarArrangementPolicy == null || input.designCode == "ECP")
            return input
        }
        val adopted = Ecp203FourBarArrangement.arrange(input.ribWidth, input.totalThickness,
            context.coverToOutsideLinksMm, context.links.diameterMm,
            context.nominalMaximumAggregateMm, layers.first().diameterMm, policy)
        return input.copy(sectionContext = context.copy(tensionLayers = adopted),
            fourBarArrangementPolicy = policy)
    }
}
