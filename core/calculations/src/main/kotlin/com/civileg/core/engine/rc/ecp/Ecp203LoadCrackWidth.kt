package com.civileg.core.engine.rc.ecp

/**
 * Load-induced crack-width component, ECP203-2020 printed4-66..4-70,
 * Eqs4-83/84/85 and Tables4-11/12. Service steel-stress limits are the
 * explicitly cross-referenced Table(m1-1), printedm1-2.
 *
 * The caller must supply the actual cracked-section service stresses and the
 * effective concrete tension area from Fig4-24. This component does not derive
 * those values from ULS forces, total slab area or a guessed member type.
 * Individual equal-diameter bars, pure bending or pure axial tension only.
 * Combined strain, bundles, oblique cracks, restraint/shrinkage/thermal cracking,
 * cover/spacing, anchorage and whole-member approval are outside this component.
 */
object Ecp203LoadCrackWidth {
    enum class Exposure(val maximumWidthMm: Double) {
        I_PROTECTED(0.30), II_UNPROTECTED(0.20), III_HARMFUL(0.15), IV_CORROSIVE(0.10)
    }
    enum class Loading(val beta2: Double) {
        SHORT_TERM(1.0), PERMANENT_OR_REPEATED(0.5)
    }
    enum class TensionDistribution(val k2: Double) { PURE_BENDING(0.5), PURE_AXIAL_TENSION(1.0) }

    data class Input(
        val characteristicYieldMpa: Double,
        val surface: Ecp203RectangularFlexure.SteelSurface,
        val commonIndividualBarDiameterMm: Double,
        val tensionSteelAreaMm2: Double,
        val effectiveConcreteTensionAreaMm2: Double,
        /** Maximum characteristic service envelope, not an unloading state. */
        val crackedServiceSteelStressMpa: Double,
        /** Same steel location and cracked-section model, at first load-induced cracking. */
        val firstCrackSteelStressMpa: Double,
        val distribution: TensionDistribution,
        val loading: Loading,
        val exposure: Exposure
    )

    data class Assessment(
        val rhoR: Double,
        val beta: Double,
        val beta1: Double,
        val beta2: Double,
        val k1: Double,
        val k2: Double,
        val meanCrackSpacingMm: Double,
        val meanRelativeSteelStrain: Double,
        val characteristicCrackWidthMm: Double,
        val maximumCrackWidthMm: Double,
        val widthLimitPass: Boolean,
        /** Null for fy500: the supplied source table has no row for that grade. */
        val sourceWorkingStressLimitMpa: Double?,
        val workingStressLimitPass: Boolean?,
        /** Only these two bounded checks. Never a whole-cracking or member verdict. */
        val boundedChecksPass: Boolean?
    )

    fun assessCrackedSection(input: Input): Assessment = with(input) {
        require(characteristicYieldMpa in listOf(240.0, 350.0, 400.0, 420.0, 500.0))
        require(surface != Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD || characteristicYieldMpa == 240.0)
        require(listOf(commonIndividualBarDiameterMm, tensionSteelAreaMm2,
            effectiveConcreteTensionAreaMm2, crackedServiceSteelStressMpa, firstCrackSteelStressMpa)
            .all { it.isFinite() && it > 0.0 })
        require(tensionSteelAreaMm2 < effectiveConcreteTensionAreaMm2)
        require(firstCrackSteelStressMpa <= crackedServiceSteelStressMpa) {
            "Use the maximum cracked service envelope at or above first cracking; unloading history needs a separate model."
        }
        require(crackedServiceSteelStressMpa <= characteristicYieldMpa) {
            "The elastic cracked-section model cannot admit yielded service reinforcement."
        }
        val (beta1, k1) = when (surface) {
            Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED -> 0.8 to 0.8
            Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD -> 0.5 to 1.6
        }
        val rho = tensionSteelAreaMm2 / effectiveConcreteTensionAreaMm2
        val srm = 50.0 + 0.25 * k1 * distribution.k2 * commonIndividualBarDiameterMm / rho
        val ratio = firstCrackSteelStressMpa / crackedServiceSteelStressMpa
        // Printed Eq4-85, without importing a lower-bound rule from another code.
        val meanStrain = crackedServiceSteelStressMpa / 200000.0 *
            (1.0 - beta1 * loading.beta2 * ratio * ratio)
        val width = 1.7 * srm * meanStrain
        require(listOf(rho, srm, meanStrain, width).all { it.isFinite() && it > 0.0 })
        val sourceLimit = when (characteristicYieldMpa) {
            240.0 -> 140.0
            350.0 -> 200.0
            400.0 -> 220.0
            420.0 -> 230.0
            else -> null
        }
        val widthPass = width <= exposure.maximumWidthMm
        val stressPass = sourceLimit?.let { crackedServiceSteelStressMpa <= it }
        val boundedPass = when {
            !widthPass || stressPass == false -> false
            stressPass == null -> null
            else -> true
        }
        Assessment(rho, 1.7, beta1, loading.beta2, k1, distribution.k2, srm,
            meanStrain, width, exposure.maximumWidthMm, widthPass, sourceLimit, stressPass, boundedPass)
    }
}
