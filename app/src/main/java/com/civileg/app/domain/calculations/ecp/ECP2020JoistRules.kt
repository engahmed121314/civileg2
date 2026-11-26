package com.civileg.app.domain.calculations.ecp

import kotlin.math.max
import kotlin.math.sqrt

/** ECP 203-2020 rules transcribed from the supplied primary scan.
 * This is a bounded rule set, not a complete joist design or detailing approval.
 * No hook, support length, exposure or load decomposition is assumed.
 */
object ECP2020JoistRules {
    const val EDITION = "ECP 203-2020"

    data class GeometryCheck(val clause: String, val requirement: String, val passed: Boolean)

    /** ECP 203-2020 6-2-2-1 (printed p. 6-15, supplied PDF248, verified against the page render).. Dimensions in mm. */
    fun geometry(webWidth: Double, ribSpacing: Double, depth: Double, topping: Double): List<GeometryCheck> {
        require(listOf(webWidth, ribSpacing, depth, topping).all { it.isFinite() && it > 0 })
        val clear = ribSpacing - webWidth
        return listOf(
            GeometryCheck("ECP 203-2020 6-2-2-1", "Clear block width must be positive and at most 700 mm", clear > 0 && clear <= 700),
            GeometryCheck("ECP 203-2020 6-2-2-1", "Rib width must be at least max(100 mm, total depth / 3)", webWidth >= max(100.0, depth / 3)),
            GeometryCheck("ECP 203-2020 6-2-2-1", "Topping must be at least max(50 mm, clear block width / 10) and less than total depth", topping >= max(50.0, clear / 10) && topping < depth)
        )
    }

    /** §6-2-1-1-1(a), doc6-3 / PDF236, expressly referenced for
     * rib spans by §6-2-2-4, doc6-17 / PDF250. Total slab thickness,
     * not topping or effective depth. Simple span only in this workflow.
     */
    fun simpleRibEffectiveSpanMm(clearSpanMm: Double, totalDepthMm: Double,
        leftSupportWidthMm: Double, rightSupportWidthMm: Double): Double {
        require(listOf(clearSpanMm, totalDepthMm, leftSupportWidthMm, rightSupportWidthMm).all { it.isFinite() && it > 0 })
        val centerToCenter = clearSpanMm + leftSupportWidthMm / 2 + rightSupportWidthMm / 2
        return minOf(maxOf(clearSpanMm + totalDepthMm, 1.05 * clearSpanMm), centerToCenter)
    }

    /** §6-2-2-2, doc6-16 / PDF249. This gives count only, not cross-rib design.
     * A live-load intensity is required, never inferred from a factored moment.
     */
    fun crossRibCount(effectiveSpanMm: Double, liveLoadKnM2: Double): Int {
        require(effectiveSpanMm.isFinite() && effectiveSpanMm > 0)
        require(liveLoadKnM2.isFinite() && liveLoadKnM2 >= 0)
        return if (liveLoadKnM2 <= 3.0) {
            if (effectiveSpanMm > 5000) 1 else 0
        } else when {
            effectiveSpanMm > 7000 -> 3
            effectiveSpanMm >= 4000 -> 1
            else -> 0
        }
    }

    data class CrossRibMinimums(val count: Int, val minimumWebWidthMm: Double,
        val minimumTotalDepthMm: Double, val minimumBottomAreaMm2: Double,
        val topToActualBottomAreaRatio: Double = 0.5)

    /** §6-2-2-2, doc6-16 / PDF249. Minimum section and bottom steel
     * are no less than the main ribs. Top steel must be at least half the
     * ACTUAL cross-rib bottom steel, not simply half this minimum.
     * No demand, position for three ribs, bar selection or anchorage is inferred.
     */
    fun crossRibMinimums(effectiveSpanMm: Double, liveLoadKnM2: Double,
        mainWebWidthMm: Double, mainTotalDepthMm: Double, mainBottomAreaMm2: Double): CrossRibMinimums? {
        require(listOf(mainWebWidthMm, mainTotalDepthMm, mainBottomAreaMm2).all { it.isFinite() && it > 0 })
        val count = crossRibCount(effectiveSpanMm, liveLoadKnM2)
        return if (count == 0) null else CrossRibMinimums(count, mainWebWidthMm, mainTotalDepthMm, mainBottomAreaMm2)
    }

    /** Eq4-9 / doc4-9 (PDF89). Web width for T/L sections.
     * Ac floors use the gross WEB area, not the flange or effective b*d area.
     * The 30% alternative uses calculated demand and is capped at the Eq4-9
     * amount. Independent gross-area floors remain mandatory.
     */
    fun minimumFlexuralSteelMm2(
        fcu: Double, fy: Double, webWidth: Double, depth: Double,
        effectiveDepth: Double, calculatedArea: Double, deformed: Boolean
    ): Double {
        require(listOf(fcu, fy, webWidth, depth, effectiveDepth).all { it.isFinite() && it > 0 })
        require(effectiveDepth < depth)
        require(calculatedArea.isFinite() && calculatedArea >= 0)
        val equationArea = max(0.225 * sqrt(fcu) / fy, 1.1 / fy) * webWidth * effectiveDepth
        val floorRatio = if (!deformed) 0.0025 else if (fcu < 30) 0.0015 else 0.0018
        val demandAlternative = if (calculatedArea < equationArea) minOf(1.3 * calculatedArea, equationArea) else equationArea
        return max(demandAlternative, floorRatio * webWidth * depth)
    }

    /** §4-2-2-1-6(d)(2), doc4-22 / PDF102. Geometric eligibility
     * only for concrete-only design of buried beams and hollow-block ribs.
     * Passing this limit is not the shear demand check or full detailing.
     */
    fun concreteOnlyGeometryEligible(depthMm: Double, toppingMm: Double, webWidthMm: Double): Boolean {
        require(listOf(depthMm, toppingMm, webWidthMm).all { it.isFinite() && it > 0 })
        return depthMm <= maxOf(250.0, 2.5 * toppingMm, webWidthMm / 2)
    }

    /** Table4-13, doc4-71 / PDF201: general elements, including ribs,
     * not the walls/solid-slab branch. Largest-bar floor also applies.
     * Tables2-21..23 durability exposure/mix requirements, fire, execution tolerance
     * and special exposure requirements remain separate. Table4-13 is not
     * the complete durability-cover chain.
     */
    fun minimumCrackControlRibCoverMm(fcu: Double, exposure: com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure,
        largestBarMm: Double): Double {
        require(fcu.isFinite() && fcu > 0 && largestBarMm.isFinite() && largestBarMm > 0)
        val lowStrength = fcu <= 25.0
        val table = when (exposure) {
            com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.PROTECTED_INTERIOR -> if (lowStrength) 25.0 else 20.0
            com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.UNPROTECTED -> if (lowStrength) 30.0 else 25.0
            com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.HARMFUL -> if (lowStrength) 35.0 else 30.0
            com.civileg.app.domain.calculations.utils.JoistDetailCase.Exposure.CORROSIVE -> if (lowStrength) 45.0 else 40.0
        }
        return maxOf(table, largestBarMm)
    }

    /** §7-3-3-1, doc7-6/7 / PDF361/362, Fig7-2a b.
     * Individual equal-diameter bars, one layer. No bundles or second layer.
     */
    fun minimumIndividualBarClearMm(diameterMm: Int, aggregateMm: Double): Double {
        require(diameterMm > 0 && aggregateMm.isFinite() && aggregateMm > 0)
        return maxOf(diameterMm.toDouble(), 1.5 * aggregateMm)
    }

    fun individualBarsFit(webWidthMm: Double, coverMm: Double, linkMm: Double,
        diameterMm: Int, count: Int, aggregateMm: Double): Boolean {
        require(count > 0 && listOf(webWidthMm, coverMm, linkMm).all { it.isFinite() && it >= 0 })
        val needed = count.toDouble() * diameterMm + (count - 1).toDouble() * minimumIndividualBarClearMm(diameterMm, aggregateMm)
        return needed <= webWidthMm - 2 * (coverMm + linkMm)
    }

    /** Eq4-29 / doc4-18 (PDF98). Concrete-only threshold, not total shear
     * resistance where web reinforcement is required. No ACI phi factor.
     */
    fun concreteOnlyShearKn(fcu: Double, webWidth: Double, effectiveDepth: Double): Double {
        require(listOf(fcu, webWidth, effectiveDepth).all { it.isFinite() && it > 0 })
        return 0.16 * sqrt(fcu / 1.5) * webWidth * effectiveDepth / 1000
    }

    /** Eqs4-66/67 and Tables4-7/8, doc4-49..52 (PDF129..133).
     * Straight, individual, deformed tension bars only. No excess-steel
     * reduction, bundling, hooks or laps. Top-position eta is explicit.
     */
    fun straightTensionDevelopmentMm(
        fcu: Double, fy: Double, diameterMm: Int, concreteCastBelowMm: Double
    ): Double {
        require(fcu.isFinite() && fcu in 20.0..60.0)
        require(fy.isFinite() && fy in setOf(350.0, 400.0, 420.0, 500.0))
        require(fy < 500 || fcu >= 30)
        require(diameterMm > 6 && diameterMm <= 50)
        require(concreteCastBelowMm.isFinite() && concreteCastBelowMm >= 0)
        val eta = if (concreteCastBelowMm > 300) 1.3 else 1.0
        val bond = 0.30 * sqrt(fcu / 1.5)
        val length = 0.75 * eta * (fy / 1.15) * diameterMm / (4 * bond)
        return max(length, max(40.0 * diameterMm, 300.0))
    }
}
