package com.civileg.core.engine.rc.ecp

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ECP203-2020 constant-depth web shear, zero axial force and no torsion.
 * Printed4-17 Eq4-24/27, 4-18 Eq4-29, 4-20 Eq4-30/33,
 * 4-21 Eq4-34 and 4-22 Eq4-40, clauses(a,b,d,e,f).
 * Supplied shear must already be the applicable factored section force.
 */
object Ecp203WebShear {
    data class Section(val fcuMpa: Double, val webWidthMm: Double, val effectiveDepthMm: Double,
        val totalDepthMm: Double, val flangeThicknessMm: Double)

    data class VerticalLinks(val fyMpa: Double, val surface: Ecp203RectangularFlexure.SteelSurface,
        val diameterMm: Double, val legs: Int, val spacingMm: Double,
        val maximumTransverseLegSpacingMm: Double? = null)

    data class Assessment(
        val designShearStressMpa: Double,
        val concreteUncrackedStressMpa: Double,
        val concreteCrackedStressMpa: Double,
        val maximumShearStressMpa: Double,
        val concreteOnlyDepthLimitMm: Double,
        val concreteOnlyRequired: Boolean,
        val minimumLinksRatio: Double?,
        val requiredLinksRatio: Double?,
        val providedLinksRatio: Double,
        val designLinksYieldMpa: Double?,
        val shearResistanceKn: Double,
        val withinConcreteMaximum: Boolean,
        val linksDetailingPass: Boolean,
        val carriesShear: Boolean,
        val shearChecksPass: Boolean
    )

    fun assess(section: Section, designShearKn: Double, links: VerticalLinks? = null): Assessment {
        val s = section
        require(listOf(s.fcuMpa, s.webWidthMm, s.effectiveDepthMm, s.totalDepthMm, s.flangeThicknessMm)
            .all { it.isFinite() && it > 0.0 }) { "Web geometry and strength must be finite and positive." }
        require(s.effectiveDepthMm < s.totalDepthMm && s.flangeThicknessMm < s.totalDepthMm)
        require(designShearKn.isFinite() && designShearKn >= 0.0)
        val bd = s.webWidthMm * s.effectiveDepthMm
        require(bd.isFinite() && bd > 0.0 && (designShearKn * 1000.0).isFinite())
        val stress = designShearKn * 1000.0 / bd
        val rootStrength = sqrt(s.fcuMpa / Ecp203RectangularFlexure.GAMMA_C)
        require(stress.isFinite() && rootStrength > 0.0) { "Shear inputs are outside numeric range." }
        val uncracked = 0.16 * rootStrength
        val cracked = 0.12 * rootStrength
        val maximum = min(0.70 * rootStrength, 4.4)
        // Printed4-22(d)(2): largest of all three dimensions, not their minimum.
        val depthLimit = max(250.0, max(2.5 * s.flangeThicknessMm, s.webWidthMm / 2.0))
        require(depthLimit.isFinite()) { "Concrete-only depth limit is outside numeric range." }
        val concreteOnly = s.totalDepthMm <= depthLimit
        val withinMaximum = stress <= maximum + 1e-12
        val effectiveFy = links?.let {
            require(it.fyMpa.isFinite() && it.fyMpa > 0.0)
            require(it.diameterMm.isFinite() && it.diameterMm > 0.0 && it.legs > 0)
            require(it.spacingMm.isFinite() && it.spacingMm > 0.0)
            require(it.surface != Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD || it.fyMpa == 240.0)
            // Printed4-22(e) limits the design value, not the physical steel grade.
            min(it.fyMpa, 420.0)
        }
        if (concreteOnly) {
            // This clause requires concrete-only sizing. Links do not buy added resistance.
            val capacity = min(uncracked, maximum) * bd / 1000.0
            require(capacity.isFinite())
            val carries = designShearKn <= capacity + 1e-9
            return Assessment(stress, uncracked, cracked, maximum, depthLimit, true,
                0.0, 0.0, 0.0, null, capacity, withinMaximum, true, carries, withinMaximum && carries)
        }
        val providedRatio = links?.let { it.legs * PI * it.diameterMm * it.diameterMm / 4.0 / (s.webWidthMm * it.spacingMm) } ?: 0.0
        require(providedRatio.isFinite())
        val minimumRatio = effectiveFy?.let {
            max(0.4 / it, if (links.surface == Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD) 0.0015 else 0.0010)
        }
        val requiredStrengthRatio = if (effectiveFy != null && stress > uncracked) max(0.0, stress - cracked) / (effectiveFy / Ecp203RectangularFlexure.GAMMA_S) else 0.0
        val requiredRatio = minimumRatio?.let { max(it, requiredStrengthRatio) }
        val wide = s.webWidthMm >= 400.0 || s.webWidthMm > s.totalDepthMm
        val transverseDetail = if (wide) links != null && links.legs >= 4 &&
            links.maximumTransverseLegSpacingMm?.let { it.isFinite() && it > 0.0 && it <= 250.0 } == true
            else links != null && links.legs >= 2
        val detailing = links != null && links.diameterMm >= 8.0 && links.spacingMm <= 200.0 &&
            minimumRatio?.let { providedRatio + 1e-12 >= it } == true && transverseDetail
        val linksStress = if (effectiveFy != null) providedRatio * effectiveFy / Ecp203RectangularFlexure.GAMMA_S else 0.0
        require(linksStress.isFinite()) { "Link resistance is outside numeric range." }
        val availableStress = min(maximum, max(uncracked, cracked + linksStress))
        val capacity = availableStress * bd / 1000.0
        require(capacity.isFinite())
        val carries = designShearKn <= capacity + 1e-9
        return Assessment(stress, uncracked, cracked, maximum, depthLimit, false,
            minimumRatio, requiredRatio, providedRatio, effectiveFy, capacity, withinMaximum,
            detailing, carries, withinMaximum && detailing && carries)
    }
}
