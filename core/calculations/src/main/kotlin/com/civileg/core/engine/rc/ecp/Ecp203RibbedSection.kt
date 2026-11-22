package com.civileg.core.engine.rc.ecp

/**
 * Coupled one-way interior rib SECTION assessment, ECP203-2020.
 * Uses the same supplied factored section forces for every component. No load
 * analysis, redistribution, axial force, torsion or anchorage. Optional immediate
 * simply-supported UDL deflection is separate from the supplied factored forces.
 * Optional common-age long-term deflection is explicit. Crack-width, detailing
 * and whole-member approval remain outside this method.
 */
object Ecp203RibbedSection {
    enum class Region { FLANGE_IN_COMPRESSION, FLANGE_IN_TENSION }

    data class ImmediateDeflectionInput(
        val analysisSpanMm: Double,
        val characteristicDeadLoadKnM2: Double,
        val longTermInput: Ecp203LongTermDeflection.Input? = null
    )

    data class Context(
        val coverToOutsideLinksMm: Double,
        val nominalMaximumAggregateMm: Double,
        val tensionLayers: List<Ecp203BarLayout.Layer>,
        val steelSurface: Ecp203RectangularFlexure.SteelSurface,
        val region: Region,
        val contraflexureDistanceMm: Double?,
        val characteristicLiveLoadKnM2: Double,
        val links: Ecp203WebShear.VerticalLinks,
        val immediateDeflectionInput: ImmediateDeflectionInput? = null
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
        val flexure: Ecp203LayeredFlexure.Assessment,
        val immediateDeflection: Ecp203ShortTermDeflection.Assessment?,
        val sectionChecksPass: Boolean,
        val longTermDeflection: Ecp203LongTermDeflection.Assessment? = null
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
        val flangeWidth = when (context.region) {
            Region.FLANGE_IN_COMPRESSION -> Ecp203TSectionFlexure.effectiveFlangeWidthMm(webWidthMm,
                toppingMm, requireNotNull(context.contraflexureDistanceMm) {
                    "Compression-flange assessment needs the actual distance between contraflexure points L2."
                }, centerSpacingMm)
            Region.FLANGE_IN_TENSION -> webWidthMm
        }
        val flexure = Ecp203LayeredFlexure.assess(section, flangeWidth, toppingMm,
            context.tensionLayers, layout.layerCentersFromTensionFaceMm, momentKnM)
        val shear = Ecp203WebShear.assess(Ecp203WebShear.Section(fcuMpa, webWidthMm, d,
            totalDepthMm, toppingMm), shearKn, context.links)
        val crossRibs = Ecp203RibbedGeometry.requiredOneWayCrossRibs(spanMm,
            context.characteristicLiveLoadKnM2)
        var longTerm: Ecp203LongTermDeflection.Assessment? = null
        val immediate = context.immediateDeflectionInput?.let { input ->
            require(context.region == Region.FLANGE_IN_COMPRESSION) {
                "The simply supported gravity UDL model requires the top flange in compression."
            }
            require(kotlin.math.abs(input.analysisSpanMm - requireNotNull(context.contraflexureDistanceMm)) <= 1e-6) {
                "For this simply supported model, the analysis span must equal the supplied distance between zero-moment points L2."
            }
            require(input.characteristicDeadLoadKnM2.isFinite() && input.characteristicDeadLoadKnM2 >= 0.0)
            val serviceSection = Ecp203ShortTermDeflection.Section(fcuMpa, webWidthMm,
                flangeWidth, toppingMm, totalDepthMm,
                Ecp203ShortTermDeflection.FlangeLocation.COMPRESSION_FACE,
                context.tensionLayers.mapIndexed { i, layer ->
                    Ecp203ShortTermDeflection.SteelLayer(layer.count * kotlin.math.PI * layer.diameterMm * layer.diameterMm / 4.0,
                        totalDepthMm - layout.layerCentersFromTensionFaceMm[i])
                })
            Ecp203ShortTermDeflection.assessSimplySupportedUdl(serviceSection, input.analysisSpanMm,
                input.characteristicDeadLoadKnM2 * centerSpacingMm / 1000.0,
                context.characteristicLiveLoadKnM2 * centerSpacingMm / 1000.0).also { service ->
                    require(service.deadPlusLive.maximumMomentKnM <= service.stiffness.crackingMomentKnM ||
                        service.deadPlusLive.fullyCrackedElasticTensionSteelStressMpa <= fyMpa) {
                        "Cracked service-load steel stress exceeds characteristic yield; the elastic deflection model is outside its verified domain. Redesign the section or use a separately verified nonlinear analysis."
                    }
                    longTerm = input.longTermInput?.let {
                        Ecp203LongTermDeflection.assess(serviceSection, fyMpa, input.analysisSpanMm,
                            input.characteristicDeadLoadKnM2 * centerSpacingMm / 1000.0,
                            context.characteristicLiveLoadKnM2 * centerSpacingMm / 1000.0, it)
                    }
                }
        }
        return Assessment(layout, geometry, flangeWidth, flexure.requiredSteelMm2,
            flexure.momentResistanceKnM, flexure.flexureChecksPass, shear, crossRibs, flexure, immediate,
            geometry.geometryChecksPass && flexure.flexureChecksPass && shear.shearChecksPass, longTerm)
    }
}
