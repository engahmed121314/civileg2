package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.utils.JoistDetailCase

/** An explicit case can unlock checks, not silently unlock execution drawings.
 * Unsupported loading/support cases are named before a section is evaluated.
 */
object ECP2020JoistCaseReview {
    data class Result(
        val missingInputs: List<String>,
        val unsupported: List<String>,
        val section: ECP2020JoistSection.Result?,
        val concreteOnlyShearKn: Double?,
        val shearDemandWithinConcreteThreshold: Boolean?,
        val bottomStraightDevelopmentMm: Double?,
        val leftAnchorageFits: Boolean?,
        val rightAnchorageFits: Boolean?,
        val crossRibsRequired: Int?,
        val constructionReady: Boolean = false,
        val effectiveSpanMm: Double? = null,
        val crossRibMinimums: ECP2020JoistRules.CrossRibMinimums? = null,
        val limitations: String = "PARTIAL CASE REVIEW. Effective flange-width applicability, maximum bar spacing, durability Tables2-21..23, fire assembly/rating/cover/protection, serviceability, topping/top/cross-rib steel, complete support anchorage and all execution details remain unverified."
    )

    /** Body-only quantity eligibility is narrower than having a section object.
     * Failed area, ductility or concrete-only shear must not appear as adopted
     * physical bars, even in the review-only one-rib output.
     */
    fun permitsPartialBodyTakeoff(result: Result): Boolean =
        result.missingInputs.isEmpty() && result.unsupported.isEmpty() &&
            result.section?.sectionPasses == true && result.shearDemandWithinConcreteThreshold == true

    fun review(case: JoistDetailCase, fcuMpa: Double, fyMpa: Double,
        webWidthMm: Double, ribPitchMm: Double, totalDepthMm: Double, toppingMm: Double): Result {
        // Validate the section envelope before any arithmetic helper can throw.
        // Invalid data remains a named incomplete review, never a partial pass.
        val missing = case.unresolved().toMutableList()
        if (!fcuMpa.isFinite() || fcuMpa !in 20.0..60.0)
            missing.add("Concrete cube strength must be within the implemented 20..60 MPa range")
        if (fyMpa !in setOf(350.0, 400.0, 420.0, 500.0) || (fyMpa == 500.0 && fcuMpa < 30.0))
            missing.add("Steel grade and concrete strength are outside the implemented material combinations")
        if (listOf(webWidthMm, ribPitchMm, totalDepthMm, toppingMm).any { !it.isFinite() || it <= 0 })
            missing.add("Positive finite web width, rib pitch, total depth and topping thickness")
        else {
            if (webWidthMm >= ribPitchMm) missing.add("Rib pitch must exceed web width for a hollow-block case")
            if (toppingMm >= totalDepthMm) missing.add("Topping thickness must be below total slab depth")
            case.effectiveFlangeWidthMm?.let {
                if (it < webWidthMm) missing.add("Effective flange width must not be below web width")
            }
            case.detailing?.let {
                val effectiveDepth = totalDepthMm - it.coverToLinkOutsideMm - it.linkDiameterMm - it.mainBarDiameterMm / 2.0
                if (!effectiveDepth.isFinite() || effectiveDepth <= 0)
                    missing.add("Actual cover and bar geometry leave no positive effective depth")
            }
        }
        if (missing.isNotEmpty()) return Result(missing.distinct(), emptyList(), null, null, null, null, null, null, null)
        val d = requireNotNull(case.detailing)
        val load = requireNotNull(case.loadBasis)
        val left = requireNotNull(case.leftSupport)
        val right = requireNotNull(case.rightSupport)
        val unsupported = buildList {
            if (!load.uniformlyDistributed) add("Non-uniform loading requires a separate analysis and serviceability strategy")
            if (d.seismicDetailingRequired) add("Seismic detailing is not implemented in this strategy")
            if (left.restraint != JoistDetailCase.Restraint.SIMPLE || right.restraint != JoistDetailCase.Restraint.SIMPLE)
                add("Only a simply supported positive-moment section review is implemented")
            if (left.ultimateNegativeMomentKnM != 0.0 || right.ultimateNegativeMomentKnM != 0.0)
                add("Negative-moment section and top-bar design are not implemented")
            if (d.linkDiameterMm != 0.0) add("Designed web reinforcement and its anchorage are not implemented")
            if (d.linkDiameterMm == 0.0 && !ECP2020JoistRules.concreteOnlyGeometryEligible(totalDepthMm, toppingMm, webWidthMm))
                add("Rib geometry is outside the concrete-only beam exception in 4-2-2-1-6(d)(2); web reinforcement design is required")
            if (!ECP2020JoistRules.individualBarsFit(webWidthMm, d.coverToLinkOutsideMm, d.linkDiameterMm,
                    d.mainBarDiameterMm, d.barsPerRib, d.aggregateSizeMm))
                add("Individual main bars fail minimum clear spacing or one-layer fit per 7-3-3-1")
            if (d.coverToLinkOutsideMm < ECP2020JoistRules.minimumCrackControlRibCoverMm(fcuMpa,
                    requireNotNull(case.serviceability).exposure, maxOf(d.mainBarDiameterMm.toDouble(), d.linkDiameterMm)))
                add("Rib cover is below Table4-13 crack-control general-elements branch or largest-bar floor")
            if (requireNotNull(case.effectiveFlangeWidthMm) > ribPitchMm)
                add("Effective flange width exceeds tributary rib pitch; full code-width validation remains pending")
            if ((left.material == JoistDetailCase.SupportMaterial.MASONRY && left.widthMm < 200) ||
                (right.material == JoistDetailCase.SupportMaterial.MASONRY && right.widthMm < 200))
                add("Masonry bearing width is below 200 mm per 6-2-2-4")
            if (left.availableAnchorageMm > left.widthMm || right.availableAnchorageMm > right.widthMm)
                add("Available anchorage exceeds support width; support and anchorage geometry must be reconciled")
            if (left.material != JoistDetailCase.SupportMaterial.CONCRETE ||
                right.material != JoistDetailCase.SupportMaterial.CONCRETE)
                add("Straight development into supports is implemented only for concrete supports; masonry or other bearing requires an explicit reinforced-concrete anchorage region and a separate verified detail")
        }
        if (unsupported.isNotEmpty()) return Result(emptyList(), unsupported, null, null, null, null, null, null, null)
        val geometry = ECP2020JoistRules.geometry(webWidthMm, ribPitchMm, totalDepthMm, toppingMm)
        val geometryFailures = geometry.filterNot { it.passed }.map { "${it.clause}: ${it.requirement}" }
        if (geometryFailures.isNotEmpty()) return Result(emptyList(), geometryFailures, null, null, null, null, null, null, null)
        val section = ECP2020JoistSection.check(ECP2020JoistSection.Input(
            fcuMpa, fyMpa, webWidthMm, requireNotNull(case.effectiveFlangeWidthMm), toppingMm, totalDepthMm,
            d.coverToLinkOutsideMm, d.linkDiameterMm, d.mainBarDiameterMm, d.barsPerRib, load.ultimatePositiveMomentKnMPerRib
        ))
        val shear = ECP2020JoistRules.concreteOnlyShearKn(fcuMpa, webWidthMm, section.effectiveDepthMm)
        val development = ECP2020JoistRules.straightTensionDevelopmentMm(fcuMpa, fyMpa, d.mainBarDiameterMm,
            d.coverToLinkOutsideMm + d.mainBarDiameterMm / 2.0)
        val effectiveSpan = ECP2020JoistRules.simpleRibEffectiveSpanMm(requireNotNull(case.clearSpanMm), totalDepthMm,
            left.widthMm, right.widthMm)
        return Result(emptyList(), emptyList(), section, shear, load.ultimateShearKnPerRib <= shear,
            development, left.availableAnchorageMm >= development, right.availableAnchorageMm >= development,
            ECP2020JoistRules.crossRibCount(effectiveSpan, load.liveKnM2), effectiveSpanMm = effectiveSpan,
            crossRibMinimums = ECP2020JoistRules.crossRibMinimums(effectiveSpan, load.liveKnM2,
                webWidthMm, totalDepthMm, section.providedAreaMm2))
    }
}
