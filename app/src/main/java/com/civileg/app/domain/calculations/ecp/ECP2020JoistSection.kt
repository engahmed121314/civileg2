package com.civileg.app.domain.calculations.ecp

import kotlin.math.max

/** Positive-moment, singly reinforced T-rib section check, ECP203-2020.
 * Eqs4-1/2, equivalent block a=0.8c (primary PDF84), Table4-1 (PDF86),
 * web minimum-steel rule (PDF89). No redistribution, axial force, compression
 * reinforcement credit, negative moment, shear, anchorage or serviceability.
 * Its result cannot authorize an adopted detailing schedule.
 */
object ECP2020JoistSection {
    data class Input(
        val fcuMpa: Double,
        val fyMpa: Double,
        val webWidthMm: Double,
        val effectiveFlangeWidthMm: Double,
        val toppingMm: Double,
        val totalDepthMm: Double,
        val coverToLinkOutsideMm: Double,
        val linkDiameterMm: Double,
        val mainBarDiameterMm: Int,
        val barsPerRib: Int,
        val ultimatePositiveMomentKnM: Double
    )

    data class Result(
        val effectiveDepthMm: Double,
        val requiredFlexuralAreaMm2: Double?,
        val requiredAreaWithMinimumMm2: Double?,
        val providedAreaMm2: Double,
        val neutralAxisMm: Double,
        val blockDepthMm: Double,
        val momentResistanceKnM: Double,
        val singlyReinforcedLimitKnM: Double,
        val withinSinglyReinforcedLimit: Boolean,
        val areaSufficient: Boolean,
        val sectionPasses: Boolean,
        val limitations: String = "SECTION CHECK ONLY. Bar fit, shear, top steel, anchorage, serviceability and complete detailing are not checked."
    )

    fun check(p: Input): Result {
        require(p.fcuMpa.isFinite() && p.fcuMpa in 20.0..60.0)
        require(p.fyMpa in setOf(350.0, 400.0, 420.0, 500.0))
        require(p.fyMpa < 500 || p.fcuMpa >= 30)
        require(listOf(p.webWidthMm, p.effectiveFlangeWidthMm, p.toppingMm, p.totalDepthMm).all { it.isFinite() && it > 0 })
        require(p.effectiveFlangeWidthMm >= p.webWidthMm && p.toppingMm < p.totalDepthMm)
        require(p.coverToLinkOutsideMm.isFinite() && p.coverToLinkOutsideMm >= 0)
        require(p.linkDiameterMm.isFinite() && p.linkDiameterMm >= 0)
        require(p.mainBarDiameterMm in setOf(8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 32))
        require(p.barsPerRib > 0)
        require(p.ultimatePositiveMomentKnM.isFinite() && p.ultimatePositiveMomentKnM >= 0)
        val d = p.totalDepthMm - p.coverToLinkOutsideMm - p.linkDiameterMm - p.mainBarDiameterMm / 2.0
        require(d > 0)
        val stress = 0.67 * p.fcuMpa / 1.5
        val steelStress = p.fyMpa / 1.15
        val cMaxRatio = when (p.fyMpa) { 350.0 -> 0.45; 400.0 -> 0.42; 420.0 -> 0.41; else -> 0.39 }
        val aMax = 0.8 * cMaxRatio * d
        fun force(a: Double): Double = stress * (
            p.effectiveFlangeWidthMm * minOf(a, p.toppingMm) + p.webWidthMm * max(0.0, a - p.toppingMm))
        fun moment(a: Double): Double {
            if (a == 0.0) return 0.0
            val flangeDepth = minOf(a, p.toppingMm)
            val webDepth = max(0.0, a - p.toppingMm)
            val flangeForce = stress * p.effectiveFlangeWidthMm * flangeDepth
            val webForce = stress * p.webWidthMm * webDepth
            // Eq4-1 lever arm cannot exceed0.95d (primary PDF85).
            val lever = minOf(0.95 * d, d - (flangeForce * flangeDepth / 2 + webForce * (p.toppingMm + webDepth / 2)) / (flangeForce + webForce))
            return (flangeForce + webForce) * lever / 1e6
        }
        val limit = moment(aMax)
        val demandFeasible = p.ultimatePositiveMomentKnM <= limit
        fun blockForMoment(target: Double): Double {
            var lo = 0.0
            var hi = aMax
            repeat(80) { val mid = (lo + hi) / 2; if (moment(mid) < target) lo = mid else hi = mid }
            return (lo + hi) / 2
        }
        val requiredFlexure = if (demandFeasible) force(blockForMoment(p.ultimatePositiveMomentKnM)) / steelStress else null
        val requiredMinimum = requiredFlexure?.let {
            max(it, ECP2020JoistRules.minimumFlexuralSteelMm2(p.fcuMpa, p.fyMpa, p.webWidthMm, p.totalDepthMm, d, it, true))
        }
        val provided = p.barsPerRib * Math.PI * p.mainBarDiameterMm * p.mainBarDiameterMm / 4
        val tensileForce = provided * steelStress
        val flangeForceAtInterface = stress * p.effectiveFlangeWidthMm * p.toppingMm
        val a = if (tensileForce <= flangeForceAtInterface) tensileForce / (stress * p.effectiveFlangeWidthMm)
            else p.toppingMm + (tensileForce - flangeForceAtInterface) / (stress * p.webWidthMm)
        val within = demandFeasible && a <= aMax
        val capacity = if (a <= aMax) moment(a) else limit // Never credit excess over the ductile limit.
        val sufficient = requiredMinimum != null && provided >= requiredMinimum
        return Result(d, requiredFlexure, requiredMinimum, provided, a / 0.8, a, capacity, limit,
            within, sufficient, within && sufficient && capacity >= p.ultimatePositiveMomentKnM)
    }
}
