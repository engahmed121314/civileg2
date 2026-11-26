package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.utils.AdoptedBarGroup
import com.civileg.app.domain.calculations.utils.AdoptedReinforcementSchedule
import com.civileg.app.domain.calculations.utils.JoistDetailCase

/** Partial panel takeoff of straight longitudinal bottom bars only.
 * Starts/ends at the checked development distance beyond each support face.
 * No hooks or laps, no stock-length overrun, no automatic extra bars.
 * This is never a complete BBS or construction-ready reinforcement layout.
 */
object ECP2020JoistBottomTakeoff {
    const val LIMITATION = "PARTIAL PANEL BOTTOM STEEL ONLY. Straight bars with full straight development, no laps. Topping, top steel, links, cross ribs and other reinforcement are not included. Not a complete BBS or construction approval."
    data class Result(val schedule: AdoptedReinforcementSchedule?, val reasons: List<String>)

    fun build(case: JoistDetailCase, fcuMpa: Double, fyMpa: Double, webWidthMm: Double,
        ribPitchMm: Double, totalDepthMm: Double, toppingMm: Double): Result {
        val review = ECP2020JoistCaseReview.review(case, fcuMpa, fyMpa, webWidthMm, ribPitchMm, totalDepthMm, toppingMm)
        val reasons = buildList {
            if (review.missingInputs.isNotEmpty()) add("Case inputs are incomplete")
            if (review.unsupported.isNotEmpty()) add("Case uses an unsupported strategy")
            if (review.section?.sectionPasses != true) add("Positive-moment section has not passed")
            if (review.shearDemandWithinConcreteThreshold != true) add("Concrete-only shear threshold has not passed")
            if (review.leftAnchorageFits != true || review.rightAnchorageFits != true) add("Full straight development does not fit both supports")
        }
        if (reasons.isNotEmpty()) return Result(null, reasons)
        val detail = requireNotNull(case.detailing)
        val development = requireNotNull(review.bottomStraightDevelopmentMm)
        val span = requireNotNull(case.clearSpanMm)
        val cut = span + 2 * development
        if (cut > detail.stockLengthMm) return Result(null, listOf("Straight bar exceeds explicit stock length; a verified lap or mechanical splice strategy is required"))
        return Result(AdoptedReinforcementSchedule("ECP", listOf(AdoptedBarGroup(
            mark = "PB1", role = "Panel bottom longitudinal bars only, partial review",
            diameterMm = detail.mainBarDiameterMm, barsPerMember = detail.barsPerRib,
            membersCount = requireNotNull(case.actualRibCount), bodyLengthMm = cut,
            startExtensionMm = 0.0, endExtensionMm = 0.0,
            startStationMm = -development, endStationMm = span + development,
            code = "ECP", edition = ECP2020JoistRules.EDITION,
            clause = "4-2-5-1, Eqs4-66/67, Tables4-7/8",
            shapeDescription = "Straight, includes full development beyond both clear-span support faces. No lap. Other detailing checks incomplete.",
            verified = false
        ))), emptyList())
    }
}
