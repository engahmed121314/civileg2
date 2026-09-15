package com.civileg.core.calculations.entities

enum class CodeFamily { ECP_203, ACI_318, SBC_304 }

data class ConcreteCodeParams(
    val family: CodeFamily,
    val gammas: CodeFactors? = null
) {
    fun retainingWallLateralLoadFactor(): Double = 1.6
    fun retainingWallCoverMm(): Double = 50.0
    fun retainingWallStirrupEstimateMm(): Double = 10.0
    fun retainingWallMinSteelStressCoef(): Double = 0.8
    fun retainingWallMinSteelRatio(): Double = 0.0018
    fun retainingWallDistributionAreaMm2(b: Double, d: Double): Double = 0.20 * b * d
    fun retainingWallDeadLoadFactor(): Double = 1.2
    fun retainingWallOtFsLimit(): Double = 1.5
    fun retainingWallSlidingFsLimit(): Double = 1.5
    fun retainingWallMaxFlexuralSteelRatio(): Double = 0.04
    val retainingWallShearPhi: Double = 0.75
    val phiFlexure: Double = 0.9
    fun retainingWallMinSteelRatioNote(): String = "Min Steel applied"
    fun retainingWallBarMenuMm(): List<Double> = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
}

data class CodeFactors(val gammaC: Double, val gammaS: Double)

data class ConcreteMaterial(val fcuMpa: Double, val cylinderStrengthMpa: Double)

data class SteelMaterial(val yieldMpa: Double)
