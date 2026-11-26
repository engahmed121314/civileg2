package com.civileg.app.domain.calculations.ecp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Concrete shear stress capacities of slabs and footings per ECP 203-2025
 * (scanned PDF file-6f2d5588.pdf, read as page images).
 *
 * One-way:  PDF p.105 (printed 4-25), 4-2-2-2 and Eq 4-29:  qcu = 0.16 sqrt(fcu/gc), concrete only.
 * Punching: PDF p.107-108 (printed 4-27/4-28), Eq 4-46-a/b/c:
 *   (a) 0.8  ls (alpha d / b0 + 0.2) sqrt(fcu/gc)     alpha = 4 interior, 3 edge, 2 corner
 *   (b) 0.316 ls (0.5 + a/b) sqrt(fcu/gc)             a = longer, b = shorter side of loaded area
 *   (c) 0.316 ls sqrt(fcu/gc), at most 1.70 N/mm2
 *   ls = (1000/d)^0.25 <= 1 ; qcup = smallest of (a), (b), (c) and 1.70.
 * With punching steel: qup,max = 0.45 sqrt(fcu/gc) (Eq 4-47-c, PDF p.109, printed 4-29). Not used here.
 */
object ECP2025ConcreteShear {
    const val GAMMA_C = 1.5

    enum class ColumnPosition(val alpha: Double) { INTERIOR(4.0), EDGE(3.0), CORNER(2.0) }

    /** One-way (beam-type) concrete shear stress of slabs/footings, N/mm2. */
    fun oneWayQcu(fcu: Double, gammaC: Double = GAMMA_C): Double = 0.16 * sqrt(fcu / gammaC)

    fun lambdaS(d: Double): Double = min(1.0, (1000.0 / d).pow(0.25))

    /** Punching concrete stress capacity qcup (N/mm2); d and b0 in mm, a >= b are the loaded-area sides. */
    fun punchingQcup(
        fcu: Double, d: Double, b0: Double, sideA: Double, sideB: Double,
        position: ColumnPosition = ColumnPosition.INTERIOR, gammaC: Double = GAMMA_C
    ): Double {
        // Non-positive geometry has no capacity: return 0 so the check fails instead of throwing or clamping.
        if (!(d > 0 && b0 > 0 && sideA > 0 && sideB > 0 && fcu > 0)) return 0.0
        val a = max(sideA, sideB)
        val b = min(sideA, sideB)
        val root = sqrt(fcu / gammaC)
        val ls = lambdaS(d)
        val qa = 0.8 * ls * (position.alpha * d / b0 + 0.2) * root
        val qb = 0.316 * ls * (0.5 + a / b) * root
        val qc = 0.316 * ls * root
        return minOf(qa, qb, qc, 1.70)
    }
}
