package com.civileg.core.engine.rc.ecp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ECP 203-2020 singly reinforced RECTANGULAR section, no moment redistribution.
 *
 * Sources: printed 3-5 Eqs 3-15; 4-4 Eqs 4-1/4-2; 4-5 Eq 4-4;
 * 4-6 Table 4-1; 4-9 Eq 4-9 and absolute gross-concrete-area floors.
 * This component does not establish slab analysis, T-section flange action,
 * shear, deflection, anchorage, bar fit or whole-member safety.
 */
object Ecp203RectangularFlexure {
    const val GAMMA_C = 1.50
    const val GAMMA_S = 1.15
    private const val BLOCK_STRESS_FACTOR = 0.67
    private const val BLOCK_DEPTH_FACTOR = 0.80

    enum class SteelSurface { SMOOTH_MILD, HIGH_YIELD_DEFORMED }

    data class Section(
        val fcuMpa: Double,
        val fyMpa: Double,
        val widthMm: Double,
        val effectiveDepthMm: Double,
        val totalDepthMm: Double,
        val steelSurface: SteelSurface
    )

    data class Limits(
        val rMax: Double,
        val cOverDMax: Double,
        val muMaxCoefficient: Double
    )

    data class Assessment(
        val normalizedMoment: Double,
        val demandSteelMm2: Double?,
        val minimumSteelMm2: Double,
        val requiredSteelMm2: Double?,
        val leverArmMm: Double?,
        val maximumSinglyReinforcedMomentKnM: Double,
        val maximumSinglyReinforcedSteelMm2: Double,
        val withinSinglyReinforcedDomain: Boolean,
        val tableLimits: Limits
    )

    data class ProvidedSteelCheck(
        val compressionBlockDepthMm: Double,
        val neutralAxisDepthMm: Double,
        val momentFromEquilibriumKnM: Double,
        val designMomentResistanceKnM: Double,
        val meetsMinimumSteel: Boolean,
        val withinMaximumSteel: Boolean,
        val withinNeutralAxisLimit: Boolean,
        val carriesMoment: Boolean,
        val flexureChecksPass: Boolean
    )

    /** Literal Table 4-1 values. No interpolation into an unverified steel grade. */
    fun limits(fyMpa: Double): Limits = when (fyMpa) {
        240.0 -> Limits(0.214, 0.50, 8.56e-4)
        350.0 -> Limits(0.196, 0.45, 5.23e-4)
        400.0 -> Limits(0.189, 0.42, 4.36e-4)
        420.0 -> Limits(0.186, 0.41, 4.08e-4)
        500.0 -> Limits(0.176, 0.39, 3.19e-4)
        else -> throw IllegalArgumentException("Steel grade is outside verified ECP 203-2020 Table 4-1 grades.")
    }

    fun minimumSteelMm2(section: Section): Double {
        validate(section)
        val minimumRatio = max(0.225 * sqrt(section.fcuMpa) / section.fyMpa, 1.1 / section.fyMpa)
        val absoluteRatio = when (section.steelSurface) {
            SteelSurface.SMOOTH_MILD -> 0.0025
            SteelSurface.HIGH_YIELD_DEFORMED -> if (section.fcuMpa < 30.0) 0.0015 else 0.0018
        }
        // Eq 4-9 acts on b*d. The separate absolute floor acts on Ac=b*h.
        return max(minimumRatio * section.widthMm * section.effectiveDepthMm,
            absoluteRatio * section.widthMm * section.totalDepthMm)
    }

    fun assess(section: Section, designMomentKnM: Double): Assessment {
        validate(section)
        require(designMomentKnM.isFinite() && designMomentKnM >= 0.0) { "Design moment must be finite and non-negative." }
        val table = limits(section.fyMpa)
        val b = section.widthMm
        val d = section.effectiveDepthMm
        val fcd = BLOCK_STRESS_FACTOR * section.fcuMpa / GAMMA_C
        val fyd = section.fyMpa / GAMMA_S
        val momentNmm = designMomentKnM * 1e6
        require(momentNmm.isFinite()) { "Moment is outside numeric range." }
        val normalized = momentNmm / (section.fcuMpa * b * d * d)

        // Apply ALL printed table maxima. Their rounding is not a licence to
        // exceed another printed bound or to use Rmax as normalized K.
        val aFromR = d * (1.0 - sqrt(1.0 - 2.0 * table.rMax / BLOCK_STRESS_FACTOR))
        val aFromC = BLOCK_DEPTH_FACTOR * table.cOverDMax * d
        val steelFromMu = table.muMaxCoefficient * section.fcuMpa * b * d
        val aFromMu = steelFromMu * fyd / (fcd * b)
        val aMax = min(aFromR, min(aFromC, aFromMu))
        val maximumSteel = fcd * b * aMax / fyd
        val maximumMoment = fcd * b * aMax * leverArm(d, aMax) / 1e6

        val discriminant = 1.0 - 2.0 * momentNmm / (fcd * b * d * d)
        // No fabricated fallback arm or clamped discriminant for an impossible section.
        val a = if (discriminant >= 0.0) d * (1.0 - sqrt(discriminant)) else null
        val z = a?.let { leverArm(d, it) }
        val demandSteel = z?.let { momentNmm / (fyd * it) }
        val minimumSteel = minimumSteelMm2(section)
        val required = demandSteel?.let { max(it, minimumSteel) }
        val within = required != null && required <= maximumSteel + 1e-9 &&
            designMomentKnM <= maximumMoment + 1e-9
        return Assessment(normalized, demandSteel, minimumSteel, required, z,
            maximumMoment, maximumSteel, within, table)
    }

    fun checkProvided(section: Section, designMomentKnM: Double, providedSteelMm2: Double): ProvidedSteelCheck {
        require(providedSteelMm2.isFinite() && providedSteelMm2 >= 0.0) { "Provided steel must be finite and non-negative." }
        val demand = assess(section, designMomentKnM)
        val fcd = BLOCK_STRESS_FACTOR * section.fcuMpa / GAMMA_C
        val fyd = section.fyMpa / GAMMA_S
        val a = providedSteelMm2 * fyd / (fcd * section.widthMm)
        require(a.isFinite()) { "Provided steel is outside numeric range." }
        val c = a / BLOCK_DEPTH_FACTOR
        val equilibriumMoment = providedSteelMm2 * fyd * max(0.0, leverArm(section.effectiveDepthMm, a)) / 1e6
        val resistance = min(equilibriumMoment, demand.maximumSinglyReinforcedMomentKnM)
        val minimum = providedSteelMm2 + 1e-9 >= demand.minimumSteelMm2
        val maximum = providedSteelMm2 <= demand.maximumSinglyReinforcedSteelMm2 + 1e-9
        val neutralAxis = c <= demand.tableLimits.cOverDMax * section.effectiveDepthMm + 1e-9
        val carries = designMomentKnM <= resistance + 1e-9
        val passes = demand.withinSinglyReinforcedDomain && minimum && maximum && neutralAxis && carries
        return ProvidedSteelCheck(a, c, equilibriumMoment, resistance, minimum, maximum, neutralAxis, carries, passes)
    }

    // Printed4-5: a/d >=0.1 and internal lever arm <=0.95d.
    private fun leverArm(d: Double, a: Double): Double = min(d - a / 2.0, 0.95 * d)

    private fun validate(s: Section) {
        require(listOf(s.fcuMpa, s.fyMpa, s.widthMm, s.effectiveDepthMm, s.totalDepthMm)
            .all { it.isFinite() && it > 0.0 }) { "Section inputs must be finite and positive." }
        // Printed2-21, clause2-3-2-1: reinforced concrete fcu is 20..60 MPa.
        require(s.fcuMpa in 20.0..60.0) { "Reinforced-concrete strength is outside the verified ECP203-2020 range20..60 MPa." }
        require(s.effectiveDepthMm < s.totalDepthMm) { "Effective depth must be less than total depth." }
        limits(s.fyMpa)
        require(listOf(s.fcuMpa * s.widthMm * s.effectiveDepthMm * s.effectiveDepthMm,
            s.widthMm * s.totalDepthMm, BLOCK_STRESS_FACTOR * s.fcuMpa / GAMMA_C)
            .all { it.isFinite() && it > 0.0 }) { "Section is outside numeric range." }
        require(s.steelSurface != SteelSurface.SMOOTH_MILD || s.fyMpa == 240.0) {
            "Only the verified Table4-1 grade240 smooth-mild case is supported."
        }
    }
}
