package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.SlabCalculatedSection
import com.civileg.app.domain.entities.SlabPlacedSectionBar
import com.civileg.core.engine.rc.ecp.Ecp203BarLayout
import com.civileg.core.engine.rc.ecp.Ecp203RibbedSection

/** Uses the completed numeric assessment; no rendering default or second section solve. */
internal object EcpHordiSectionGeometry {
    fun build(ribWidthMm: Double, ribPitchMm: Double, totalDepthMm: Double,
        toppingThicknessMm: Double, context: Ecp203RibbedSection.Context,
        assessment: Ecp203RibbedSection.Assessment): SlabCalculatedSection {
        val bottom = context.region == Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION
        val positions = Ecp203BarLayout.adoptSymmetricRows(ribWidthMm,
            context.coverToOutsideLinksMm, context.links.diameterMm,
            context.tensionLayers, assessment.layout)
        return SlabCalculatedSection(
            geometryVersion = 1,
            ribWidthMm = ribWidthMm,
            enteredRibPitchMm = ribPitchMm,
            totalDepthMm = totalDepthMm,
            toppingThicknessMm = toppingThicknessMm,
            analyticalFlangeWidthMm = assessment.effectiveFlangeWidthMm,
            tensionFace = if (bottom) "BOTTOM" else "TOP",
            coverToOutsideLinksMm = context.coverToOutsideLinksMm,
            linkDiameterMm = context.links.diameterMm,
            linkLegs = context.links.legs,
            linkSpacingMm = context.links.spacingMm,
            placementBasis = "ADOPTED_SYMMETRIC_ROWS_CALCULATED_LAYER_CENTRES_V1",
            bars = positions?.map { SlabPlacedSectionBar(it.layerIndex, it.indexInLayer,
                it.xFromWebLeftMm, if (bottom) it.distanceFromTensionFaceMm
                else totalDepthMm - it.distanceFromTensionFaceMm, it.diameterMm) },
            incompleteGeometry = buildList {
                add("One entered rib-pitch cell only; full-panel width and edge conditions are not supplied.")
                add("Longitudinal bar paths, lengths, support anchorage and curtailment are not supplied.")
                add("Link closure, bends, hooks and first longitudinal offset are not supplied.")
                add("Topping/distribution reinforcement and actual cross-rib provision are not supplied.")
                add("Adopted calculated section arrangement, not as-built or user-entered horizontal coordinates.")
                if (positions == null) add("Individual coordinates exceed the explicit export budget; no bar dots may be inferred.")
            }
        )
    }
}
