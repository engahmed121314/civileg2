package com.civileg.app.domain.calculations.base

import kotlin.math.sqrt

/**
 * Pure helpers for the shear wall engines. Units: N, mm, N.mm, N/mm2.
 */
object ShearWallChecks {

    /**
     * Compression block depth `a` that satisfies equilibrium WITHOUT any clamp.
     * Equilibrium: C = T + P with C = k*a (k = stress * bw) and moment about the tension steel:
     * (k*a - P)(d - a/2) = Mprime, where Mprime = M - P*(d - Lw/2) is already divided by phi where relevant.
     * Rearranged: (k/2) a^2 - (k d + P/2) a + (P d + Mprime) = 0. Returns the smaller root,
     * or null when no root exists (the section cannot resist the demand at all).
     */
    fun unclampedBlockDepth(k: Double, p: Double, d: Double, mPrime: Double): Double? {
        if (k <= 0.0 || d <= 0.0) return null
        val b = k * d + p / 2.0
        val c = p * d + mPrime
        val disc = b * b - 2.0 * k * c
        if (disc < 0.0) return null
        return (b - sqrt(disc)) / k
    }

    /**
     * True when the unclamped compression zone c = a / beta1 exceeds [limitRatio] * d, or no
     * equilibrium exists. In both cases the engine's clamped block depth would hide the problem.
     */
    fun compressionLimitExceeded(
        k: Double, p: Double, d: Double, mPrime: Double, beta1: Double, limitRatio: Double
    ): Boolean {
        val a = unclampedBlockDepth(k, p, d, mPrime) ?: return true
        if (beta1 <= 0.0) return true
        return a / beta1 > limitRatio * d
    }

    const val COUPLING_NOTE =
        "Coupling beam NOT ASSESSED: the beam shear is an estimated share of the wall demand and the " +
            "diagonal-reinforcement criteria are not verified against a supplied code text."
}
