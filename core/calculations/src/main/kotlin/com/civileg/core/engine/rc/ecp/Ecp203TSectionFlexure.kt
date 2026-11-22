package com.civileg.core.engine.rc.ecp

import kotlin.math.max
import kotlin.math.min

/**
 * Monolithic T-section flexure from ECP203-2020 equilibrium/strain assumptions
 * (printed4-1 to4-4, 4-8) and no-redistribution neutral-axis limit (Table4-1).
 * Flange must be in compression. Negative regions must use the web rectangle.
 * Section checks do not establish member detailing or serviceability.
 */
object Ecp203TSectionFlexure {
    data class Section(
        val concrete: Ecp203RectangularFlexure.Section,
        val effectiveFlangeWidthMm: Double,
        val flangeThicknessMm: Double
    )

    data class Assessment(
        val requiredSteelMm2: Double?,
        val minimumSteelMm2: Double,
        val maximumSteelMm2: Double,
        val maximumMomentKnM: Double,
        val compressionBlockDepthMm: Double?,
        val neutralAxisDepthMm: Double?,
        val withinSinglyReinforcedDomain: Boolean
    )

    data class ProvidedSteelCheck(
        val compressionBlockDepthMm: Double,
        val neutralAxisDepthMm: Double,
        val momentResistanceKnM: Double,
        val meetsMinimumSteel: Boolean,
        val withinNeutralAxisLimit: Boolean,
        val carriesMoment: Boolean,
        val flexureChecksPass: Boolean
    )

    /** Printed6-42, clause6-3-1-8(a). L2 is the contraflexure distance, not automatically the span. */
    fun effectiveFlangeWidthMm(webWidthMm: Double, flangeThicknessMm: Double,
        contraflexureDistanceMm: Double, availableFlangeWidthMm: Double): Double {
        require(listOf(webWidthMm, flangeThicknessMm, contraflexureDistanceMm, availableFlangeWidthMm)
            .all { it.isFinite() && it > 0.0 }) { "Flange geometry must be finite and positive." }
        require(availableFlangeWidthMm >= webWidthMm) { "Available flange width cannot be less than web width." }
        return min(16.0 * flangeThicknessMm + webWidthMm,
            min(contraflexureDistanceMm / 5.0 + webWidthMm, availableFlangeWidthMm))
    }

    fun assess(section: Section, designMomentKnM: Double): Assessment {
        validate(section)
        require(designMomentKnM.isFinite() && designMomentKnM >= 0.0) { "Moment must be finite and non-negative." }
        val s = section.concrete
        val minimum = Ecp203RectangularFlexure.minimumSteelMm2(s) // Web, not flange, per printed4-9.
        // Use the conservative neutral-axis envelope implied by all rounded
        // rectangular table maxima. This does not cap T resistance to web-only Rmax.
        val rectangularEnvelope = Ecp203RectangularFlexure.assess(s, 0.0)
        val aMax = rectangularEnvelope.maximumSinglyReinforcedSteelMm2 * designSteelStress(s) /
            (concreteStress(s) * s.widthMm)
        val maximumMoment = momentKnM(section, aMax)
        val maximumSteel = compressionForceN(section, aMax) / designSteelStress(s)
        if (designMomentKnM > maximumMoment + 1e-9 || minimum > maximumSteel + 1e-9) {
            return Assessment(null, minimum, maximumSteel, maximumMoment, null, null, false)
        }
        var lo = 0.0
        var hi = aMax
        repeat(80) {
            val mid = (lo + hi) / 2.0
            if (momentKnM(section, mid) < designMomentKnM) lo = mid else hi = mid
        }
        val demandA = (lo + hi) / 2.0
        val required = max(minimum, compressionForceN(section, demandA) / designSteelStress(s))
        val requiredA = blockDepthForSteel(section, required)
        return Assessment(required, minimum, maximumSteel, maximumMoment, requiredA, requiredA / 0.8, true)
    }

    fun checkProvided(section: Section, designMomentKnM: Double, providedSteelMm2: Double): ProvidedSteelCheck {
        require(providedSteelMm2.isFinite() && providedSteelMm2 >= 0.0) { "Provided steel must be finite and non-negative." }
        val assessment = assess(section, designMomentKnM)
        val a = blockDepthForSteel(section, providedSteelMm2)
        val c = a / 0.8
        require(a.isFinite() && c.isFinite()) { "Provided steel is outside numeric range." }
        val capacity = min(momentKnM(section, a), assessment.maximumMomentKnM)
        val minimum = providedSteelMm2 + 1e-9 >= assessment.minimumSteelMm2
        val within = providedSteelMm2 <= assessment.maximumSteelMm2 + 1e-9
        val carries = designMomentKnM <= capacity + 1e-9
        return ProvidedSteelCheck(a, c, capacity, minimum, within, carries,
            assessment.withinSinglyReinforcedDomain && minimum && within && carries)
    }

    private fun compressionForceN(t: Section, a: Double): Double {
        val compressionArea = t.effectiveFlangeWidthMm * min(a, t.flangeThicknessMm) +
            t.concrete.widthMm * max(0.0, a - t.flangeThicknessMm)
        return concreteStress(t.concrete) * compressionArea
    }

    private fun momentKnM(t: Section, a: Double): Double {
        val s = t.concrete
        val flangeA = min(a, t.flangeThicknessMm)
        val webA = max(0.0, a - t.flangeThicknessMm)
        val firstMoment = t.effectiveFlangeWidthMm * flangeA * (s.effectiveDepthMm - flangeA / 2.0) +
            s.widthMm * webA * (s.effectiveDepthMm - t.flangeThicknessMm - webA / 2.0)
        // Same 0.95d internal-arm ceiling as the rectangular component.
        val moment = min(concreteStress(s) * firstMoment,
            compressionForceN(t, a) * 0.95 * s.effectiveDepthMm)
        require(moment.isFinite()) { "Section moment is outside numeric range." }
        return max(0.0, moment) / 1e6
    }

    private fun blockDepthForSteel(t: Section, areaMm2: Double): Double {
        val concreteArea = areaMm2 * designSteelStress(t.concrete) / concreteStress(t.concrete)
        val flangeArea = t.effectiveFlangeWidthMm * t.flangeThicknessMm
        return if (concreteArea <= flangeArea) concreteArea / t.effectiveFlangeWidthMm
            else t.flangeThicknessMm + (concreteArea - flangeArea) / t.concrete.widthMm
    }

    private fun concreteStress(s: Ecp203RectangularFlexure.Section) = 0.67 * s.fcuMpa / Ecp203RectangularFlexure.GAMMA_C
    private fun designSteelStress(s: Ecp203RectangularFlexure.Section) = s.fyMpa / Ecp203RectangularFlexure.GAMMA_S

    private fun validate(t: Section) {
        Ecp203RectangularFlexure.minimumSteelMm2(t.concrete)
        require(t.effectiveFlangeWidthMm.isFinite() && t.effectiveFlangeWidthMm >= t.concrete.widthMm)
        require(t.flangeThicknessMm.isFinite() && t.flangeThicknessMm > 0.0 && t.flangeThicknessMm < t.concrete.totalDepthMm)
        require((t.effectiveFlangeWidthMm * t.concrete.totalDepthMm * t.concrete.effectiveDepthMm * t.concrete.fcuMpa).isFinite())
    }
}
