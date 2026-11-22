package com.civileg.core.engine.rc.ecp

/**
 * Coupled one-way interior rib SECTION assessment, ECP203-2020.
 * Uses the same supplied factored section forces for every component. No load
 * analysis, redistribution, axial force, torsion, serviceability or anchorage.
 * A passing section must never be promoted to a whole-member safety approval.
 */
object Ecp203RibbedSection {
    enum class Region { FLANGE_IN_COMPRESSION, FLANGE_IN_TENSION }

    data class Context(
        val coverToOutsideLinksMm: Double,
        val nominalMaximumAggregateMm: Double,
        val tensionLayers: List<Ecp203BarLayout.Layer>,
        val steelSurface: Ecp203RectangularFlexure.SteelSurface,
        val region: Region,
        val contraflexureDistanceMm: Double?,
        val characteristicLiveLoadKnM2: Double,
        val links: Ecp203WebShear.VerticalLinks
    )

    data class Assessment(
        val layout: Ecp203BarLayout.Assessment,
        val geometry: Ecp203RibbedGeometry.Assessment,
        val effectiveFlangeWidthMm: Double,
        val requiredSteelMm2: Double?,
        val momentResistanceKnM: Double,
        val flexureChecksPass: Boolean,
        val shear: Ecp203WebShear.Assessment,
        val requiredCrossRibs: Ecp203RibbedGeometry.CrossRibs,
        val sectionChecksPass: Boolean
    )

    fun assess(fcuMpa: Double, fyMpa: Double, webWidthMm: Double, centerSpacingMm: Double,
        totalDepthMm: Double, toppingMm: Double, spanMm: Double, momentKnM: Double,
        shearKn: Double, context: Context): Assessment {
        val geometry = Ecp203RibbedGeometry.assess(webWidthMm, centerSpacingMm, totalDepthMm, toppingMm)
        // A flange wider than the tributary spacing cannot be assigned to an interior rib.
        require(centerSpacingMm > webWidthMm) { "Rib center spacing must exceed web width." }
        val layout = Ecp203BarLayout.assess(webWidthMm, totalDepthMm,
            context.coverToOutsideLinksMm, context.links.diameterMm,
            context.nominalMaximumAggregateMm, context.tensionLayers)
        val d = requireNotNull(layout.effectiveDepthMm) {
            "Selected tension bars do not fit inside the covered rib and links. Change the actual layout or rib dimensions."
        }
        val section = Ecp203RectangularFlexure.Section(fcuMpa, fyMpa, webWidthMm, d,
            totalDepthMm, context.steelSurface)
        val required: Double?
        val capacity: Double
        val passes: Boolean
        val flangeWidth: Double
        when (context.region) {
            Region.FLANGE_IN_COMPRESSION -> {
                val l2 = requireNotNull(context.contraflexureDistanceMm) {
                    "Compression-flange assessment needs the actual distance between contraflexure points L2."
                }
                flangeWidth = Ecp203TSectionFlexure.effectiveFlangeWidthMm(webWidthMm,
                    toppingMm, l2, centerSpacingMm)
                val t = Ecp203TSectionFlexure.Section(section, flangeWidth, toppingMm)
                val demand = Ecp203TSectionFlexure.assess(t, momentKnM)
                val provided = Ecp203TSectionFlexure.checkProvided(t, momentKnM, layout.providedSteelMm2)
                required = demand.requiredSteelMm2
                capacity = provided.momentResistanceKnM
                passes = provided.flexureChecksPass
            }
            Region.FLANGE_IN_TENSION -> {
                // The topping cannot supply compression area in this region.
                flangeWidth = webWidthMm
                val demand = Ecp203RectangularFlexure.assess(section, momentKnM)
                val provided = Ecp203RectangularFlexure.checkProvided(section, momentKnM, layout.providedSteelMm2)
                required = if (demand.withinSinglyReinforcedDomain) demand.requiredSteelMm2 else null
                capacity = provided.designMomentResistanceKnM
                passes = provided.flexureChecksPass
            }
        }
        val shear = Ecp203WebShear.assess(Ecp203WebShear.Section(fcuMpa, webWidthMm, d,
            totalDepthMm, toppingMm), shearKn, context.links)
        val crossRibs = Ecp203RibbedGeometry.requiredOneWayCrossRibs(spanMm,
            context.characteristicLiveLoadKnM2)
        return Assessment(layout, geometry, flangeWidth, required, capacity, passes, shear,
            crossRibs, geometry.geometryChecksPass && passes && shear.shearChecksPass)
    }
}
