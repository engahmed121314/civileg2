package com.civileg.app.domain.calculations.ecp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Single-reinforcement flexure limits and lever arm per ECP 203-2025 (scanned PDF file-6f2d5588.pdf).
 *
 * Eq 4-4 (PDF p.85, printed 4-5): Mumax = Rmax * fcu * b * d^2 / gamma_c.
 * Table 4-1 (PDF p.86, printed 4-6): Rmax by steel grade (fy MPa): 240 0.214, 350 0.196, 400 0.189, 420 0.186,
 * 500 0.176, 600 0.165, 700 0.155. For a fy between tabulated grades the next HIGHER grade is used (smaller Rmax,
 * conservative); interpolation is not stated by the code.
 * Eq 4-1/4-2 (PDF p.84-85): Mu = (As fy/gs)(d - a/2), a = (As fy/gs)/((0.67 fcu/gc) b); lever arm z <= 0.95 d (p.85).
 */
object ECP2025Flexure {
    const val GAMMA_C = 1.5
    private val table = listOf(
        240.0 to 0.214, 350.0 to 0.196, 400.0 to 0.189, 420.0 to 0.186, 500.0 to 0.176, 600.0 to 0.165, 700.0 to 0.155
    )

    /** Rmax of Table 4-1; grades above 700 MPa are outside the table and use the 700 row (flag at the call site if needed). */
    fun rMax(fy: Double): Double = (table.firstOrNull { fy <= it.first } ?: table.last()).second

    /** Largest allowed Mu/(fcu b d^2) for single reinforcement: Rmax / gamma_c (Eq 4-4). */
    fun kLimit(fy: Double): Double = rMax(fy) / GAMMA_C

    /** Lever arm z for K = Mu/(fcu b d^2), capped at 0.95 d. */
    fun leverArm(k: Double, d: Double): Double = min(0.95 * d, d * (0.5 + sqrt(max(0.0, 0.25 - k / 0.893))))
}
