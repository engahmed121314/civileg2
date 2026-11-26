package com.civileg.app.domain.calculations.ecp

/**
 * Single-reinforcement flexural limit per ECP 203-2025, read on the page images of the supplied edition:
 * Table 4-1 (p.4-6): c_max/d by steel yield stress fy; Eq 4-4: Mu,max = Rmax fcu b d^2 / gamma_c; stress block a = 0.8 c (p.4-4, item 9).
 * fy between tabulated rows is interpolated linearly (an interpolation, not a tabulated value). Outside 240..700 N/mm2 the end rows are used.
 * Replaces the balanced-strain K with the 0.9 factor, which allowed about 45% more moment than the code (fcu 25, fy 360: 0.186 vs 0.130).
 */
object ECP2025FlexureLimits {
    private val CMAX_OVER_D = listOf(240.0 to 0.50, 350.0 to 0.45, 400.0 to 0.42, 420.0 to 0.41, 500.0 to 0.39, 600.0 to 0.36, 700.0 to 0.33)
    const val GAMMA_C = 1.5

    fun cMaxOverD(fy: Double): Double {
        val t = CMAX_OVER_D
        if (fy <= t.first().first) return t.first().second
        if (fy >= t.last().first) return t.last().second
        val i = t.indexOfLast { it.first <= fy }
        val (f0, c0) = t[i]; val (f1, c1) = t[i + 1]
        return c0 + (c1 - c0) * (fy - f0) / (f1 - f0)
    }

    /** K,max = Rmax / gamma_c with K = Mu / (fcu b d^2). */
    fun kMax(fy: Double): Double {
        val u = 0.8 * cMaxOverD(fy)
        return (0.67 / GAMMA_C) * u * (1.0 - u / 2.0)
    }
}
