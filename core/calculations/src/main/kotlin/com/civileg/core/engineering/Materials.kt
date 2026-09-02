package com.civileg.core.engineering

import com.civileg.core.math.SafeMath
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Material models (PHASE 04 seed, spec §12).
 *
 * Materials carry PHYSICAL properties only. Code-dependent reduction
 * (partial safety factors, φ) belongs to the code family, not the material.
 */
enum class CodeFamily {
    /** Egyptian Code ECP 203 — partial safety factors γc, γs. */
    ECP_203,
    /** American Concrete Institute ACI 318 — strength reduction φ. */
    ACI_318,
    /** Saudi Building Code SBC 304-2018 — φ-based family, follows ACI 318 formulations. */
    SBC_304
}

/** Code-family partial safety factors (spec §13 — single source, never re-hardcoded). */
data class PartialSafetyFactors(val gammaC: Double, val gammaS: Double) {
    companion object {
        val ECP = PartialSafetyFactors(gammaC = 1.5, gammaS = 1.15)
    }
}

/**
 * Normal-weight concrete. fcu is CUBE strength (MPa) — the ECP convention.
 * ACI consumers convert once, explicitly, via [cylinderStrength].
 */
data class ConcreteMaterial(
    val fcuMpa: Double,
    val densityKnM3: Double = 25.0
) {
    init { SafeMath.requirePositive(fcuMpa, "fcu"); SafeMath.requirePositive(densityKnM3, "density") }

    /** ACI cylinder strength ≈ 0.80 × cube strength (single explicit conversion point). */
    val cylinderStrengthMpa: Double get() = 0.80 * fcuMpa

    /**
     * Modulus of elasticity:
     *  ECP 203: Ec = 4400 √fcu (MPa) · ACI 318-19 Table 19.2.2.1: Ec = 4700 √f'c (wc=2300 kg/m³).
     */
    fun elasticModulusMpa(family: CodeFamily): Double = when (family) {
        CodeFamily.ECP_203 -> 4400.0 * sqrt(fcuMpa)
        CodeFamily.ACI_318, CodeFamily.SBC_304 -> 4700.0 * sqrt(cylinderStrengthMpa)
    }

    /** Concrete tensile strength — ECP 203: fct(r) = 0.6 √fcu (MPa). */
    val tensileStrengthMpa: Double get() = 0.6 * sqrt(fcuMpa)

    /** β₁ equivalent-stress-block factor at CUBE strength, per family rule. */
    fun betaOne(family: CodeFamily): Double = when (family) {
        CodeFamily.ECP_203 ->
            if (fcuMpa <= 30.0) 0.85 else (0.85 - 0.05 * (fcuMpa - 30.0) / 5.0).coerceAtLeast(0.65)
        CodeFamily.ACI_318, CodeFamily.SBC_304 ->
            if (cylinderStrengthMpa <= 28.0) 0.85
            else if (cylinderStrengthMpa >= 55.0) 0.65
            else 0.85 - 0.05 * (cylinderStrengthMpa - 28.0) / 7.0
    }
}

/** Reinforcing steel. */
data class SteelMaterial(
    val yieldMpa: Double,
    val ultimateMpa: Double,
    val modulusMpa: Double = 200_000.0
) {
    init {
        SafeMath.requirePositive(yieldMpa, "fy")
        SafeMath.requirePositive(ultimateMpa, "fu")
        SafeMath.requirePositive(modulusMpa, "Es")
    }
}

/** Standard rebar diameters (mm) with exact area = πd²/4 (mm²). */
object RebarTable {
    val DIAMETERS_MM = listOf(8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)

    fun area(diameterMm: Double): Double = PI * diameterMm * diameterMm / 4.0

    /** Unit mass per metre, k = π/4 × ρ = 0.006165 kg/(m·mm²) → 0.00617 d² kg/m. */
    fun unitMassKgPerM(diameterMm: Double): Double = PI / 4.0 * 7850e-6 * diameterMm * diameterMm

    /** First-fit bar selection: smallest diameter needing ≤ [maxBars] bars. */
    fun select(requiredAreaMm2: Double, maxBars: Int = 6, diameters: List<Double> = DIAMETERS_MM): Pair<Int, Double> {
        SafeMath.requirePositive(requiredAreaMm2, "As")
        for (d in diameters) {
            val n = kotlin.math.ceil(requiredAreaMm2 / area(d)).toInt()
            if (n in 1..maxBars) return n to d
        }
        error("RebarTable.select: no diameter satisfies $requiredAreaMm2 mm² within $maxBars bars")
    }
}
