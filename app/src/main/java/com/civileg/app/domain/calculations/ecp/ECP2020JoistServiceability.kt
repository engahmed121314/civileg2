package com.civileg.app.domain.calculations.ecp

/** Partial serviceability arithmetic from supplied ECP203-2020 PDF197,
 * printed doc4-61, Eq4-73. This is NOT a complete deflection check.
 * Initial partial Eq4-73 helper, extended with separately verified Eq2-1
 * and Eqs4-70/71. The full physical-case deflection chain is not integrated.
 * PDF197 specifies compression-steel ratio at midspan for simply supported
 * and continuous members, and at the fixed support for cantilevers; its
 * cross-reference resolves to4-2-1-2-2(d), PDF87-88, but no explicit
 * compression-ratio denominator has been verified in that clause. The page also warns about higher
 * steel stresses/grades. Callers must not interpret this arithmetic as an
 * acceptance check for such a section.
 */
object ECP2020JoistServiceability {
    /** Supplied primary PDF51 / doc2-23 Eq2-1, explicitly referenced by
     * the immediate-deflection clause on PDF142. fcu is the characteristic
     * cube strength, not cylinder strength (PDF49 / doc2-21). The20..60MPa
     * range matches the bounded reinforced-concrete section strategy here.
     */
    fun concreteElasticModulusMpa(fcuMpa: Double): Double {
        require(fcuMpa.isFinite() && fcuMpa in 20.0..60.0)
        return 4400.0 * kotlin.math.sqrt(fcuMpa)
    }

    /** PDF143 / doc4-60, Eqs4-71a,b. Gross concrete inertia and distance
     * to the extreme tensile fibre must be supplied from a verified section.
     * Units: N, mm; return kN.m. Not the ultimate factored moment.
     */
    fun crackingMomentKnM(fcuMpa: Double, grossInertiaMm4: Double,
        extremeTensionFibreMm: Double): Double {
        require(listOf(fcuMpa, grossInertiaMm4, extremeTensionFibreMm).all { it.isFinite() && it > 0 })
        val result = 0.6 * kotlin.math.sqrt(fcuMpa) * grossInertiaMm4 / extremeTensionFibreMm / 1e6
        require(result.isFinite())
        return result
    }

    /** PDF142-143 / doc4-59..60, Eq4-70. Only the printed cracked-demand
     * range Ma >= Mcr is evaluated. Uncracked/zero-demand handling is left
     * unresolved rather than extrapolating the equation outside that range.
     * Icr is supplied from a separately verified transformed cracked section;
     * the page expressly limits it to Ig. No steel modulus/ratio is assumed.
     */
    fun crackedRangeEffectiveInertiaMm4(grossInertiaMm4: Double, crackedInertiaMm4: Double,
        crackingMomentKnM: Double, maximumServiceMomentKnM: Double): Double? {
        require(listOf(grossInertiaMm4, crackedInertiaMm4, crackingMomentKnM).all { it.isFinite() && it > 0 })
        require(crackedInertiaMm4 <= grossInertiaMm4)
        require(maximumServiceMomentKnM.isFinite() && maximumServiceMomentKnM >= 0)
        if (maximumServiceMomentKnM < crackingMomentKnM) return null
        val ratio = crackingMomentKnM / maximumServiceMomentKnM
        val weight = ratio * ratio * ratio
        return weight * grossInertiaMm4 + (1 - weight) * crackedInertiaMm4
    }

    /** Only printed discrete durations and >=5 years are evaluated. The
     * intervening curve is not replaced with an invented interpolation.
     * Compression-steel ratio mu' is supplied explicitly, never default0.
     * Its section-area definition and applicability must be checked against
     * the referenced clause before integration with a physical joist case.
     */
    fun additionalLongTermMultiplier(durationMonths: Double, compressionSteelRatio: Double): Double? {
        require(durationMonths.isFinite() && durationMonths > 0)
        require(compressionSteelRatio.isFinite() && compressionSteelRatio >= 0 && compressionSteelRatio < 1)
        val xi = when {
            durationMonths >= 60.0 -> 2.0
            durationMonths == 12.0 -> 1.4
            durationMonths == 6.0 -> 1.2
            durationMonths == 3.0 -> 1.0
            else -> return null
        }
        return xi / (1.0 + 50.0 * compressionSteelRatio)
    }

    /** Additional creep/shrinkage deflection from immediate deflection caused
     * by sustained loads. Not total-load immediate deflection. Input must have
     * been obtained by a separately verified section/analysis calculation.
     */
    fun additionalLongTermDeflectionMm(immediateSustainedDeflectionMm: Double,
        durationMonths: Double, compressionSteelRatio: Double): Double? {
        require(immediateSustainedDeflectionMm.isFinite() && immediateSustainedDeflectionMm >= 0)
        return additionalLongTermMultiplier(durationMonths, compressionSteelRatio)?.let {
            immediateSustainedDeflectionMm * it
        }
    }
}
