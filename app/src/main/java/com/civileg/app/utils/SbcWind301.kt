package com.civileg.app.utils

import kotlin.math.E
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Wind loads on enclosed buildings, MWFRS, Directional Procedure, per SBC 301-CR-2018 (the Saudi Loading Code, 2018 edition).
 * Page numbers are the printed page numbers of the supplied PDF (printed page = PDF page - 18).
 *
 *  Eq 27-1 qz = 0.613 Kz Kzt Kd V^2 (N/m2)                          p.250 (27.3.2.1)
 *  Table 26-1 Kd = 0.85 for buildings                                p.241
 *  Table 26-2 exposure constants (alpha, zg, ...)                    p.241
 *  Table 27-2 / note 1 Kz = 2.01 (z/zg)^(2/alpha), z < 5 m uses 5 m  p.253
 *  26.9.1, 26.9.4 rigid G = 0.85                                     p.238-239
 *  26.9.5 Eq 26-12 to 26-18 flexible Gf                              p.239
 *  Table 26-3 GCpi = +-0.18 enclosed, +-0.55 partially enclosed      p.241
 *  Figure 27-2 Cp: windward 0.8 (qz), leeward -0.5/-0.3/-0.2 for L/B 0-1/2/>=4 (qh), side -0.7 (qh), roof table (qh)   p.255
 *  27.1.5.1 minimum 0.8 kN/m2 on wall area and 0.4 kN/m2 on projected roof area   p.249
 *
 * Not covered here (NOT ASSESSED): the ultimate wind speed map/city values (26.5, entered by the user), topographic Kzt computation
 * (user input), components and cladding, open and monoslope buildings, domes and arches, across-wind and torsional cases.
 */
enum class SbcExposure(val label: String, val alpha: Double, val zg: Double, val c: Double, val lengthScale: Double, val epsBar: Double,
                       val bBar: Double, val alphaBar: Double, val zMin: Double) {
    B("B", 7.0, 365.0, 0.30, 100.0, 1.0 / 3.0, 0.45, 1.0 / 4.0, 10.0),
    C("C", 9.5, 275.0, 0.20, 150.0, 1.0 / 5.0, 0.65, 1.0 / 6.5, 5.0),
    D("D", 11.5, 215.0, 0.15, 200.0, 1.0 / 8.0, 0.80, 1.0 / 9.0, 2.5)
}

object SbcWind301 {
    const val KD_BUILDING_MWFRS = 0.85
    const val G_RIGID = 0.85
    const val GCPI_ENCLOSED = 0.18
    const val GCPI_PARTIALLY_ENCLOSED = 0.55
    const val MIN_WALL_KN_M2 = 0.8
    const val MIN_ROOF_KN_M2 = 0.4

    /** Kz per Table 27-2 note 1: 2.01 (z/zg)^(2/alpha) for 5 m <= z <= zg, 5 m value below 5 m (table row 0-5 m: B 0.59, C 0.86, D 1.04). */
    fun kz(exposure: SbcExposure, z: Double): Double {
        val zz = z.coerceAtLeast(5.0).coerceAtMost(exposure.zg)
        return 2.01 * (zz / exposure.zg).pow(2.0 / exposure.alpha)
    }

    /** Eq 27-1 in kN/m2. */
    fun qz(exposure: SbcExposure, z: Double, v: Double, kzt: Double = 1.0, kd: Double = KD_BUILDING_MWFRS): Double =
        0.613 * kz(exposure, z) * kzt * kd * v * v / 1000.0

    /** Leeward wall Cp, Figure 27-2: L/B 0-1 -> -0.5, 2 -> -0.3, >=4 -> -0.2, linear between. */
    fun cpLeeward(lOverB: Double): Double = when {
        lOverB <= 1.0 -> -0.5
        lOverB <= 2.0 -> -0.5 + 0.2 * (lOverB - 1.0)
        lOverB <= 4.0 -> -0.3 + 0.05 * (lOverB - 2.0)
        else -> -0.2
    }
    const val CP_WINDWARD = 0.8
    const val CP_SIDE = -0.7

    /**
     * Most negative roof Cp of Figure 27-2 (use with qh). theta < 10 deg: h/L <= 0.5 gives -0.9 (0 to h/2, windward edge), h/L >= 1 gives -1.3 (0 to h/2),
     * linear between. The area reduction of the table (to 0.8) is not applied. theta >= 10: windward suction row of the table, interpolated in h/L and theta
     * between the tabulated values of like sign (rows h/L 0.25, 0.5, 1.0; theta 10..45, only windward suction values) versus the leeward value, whichever is more negative.
     */
    fun cpRoofMostNegative(thetaDeg: Double, hOverL: Double): Double {
        if (thetaDeg < 10.0) return when {
            hOverL <= 0.5 -> -0.9
            hOverL >= 1.0 -> -1.3
            else -> -0.9 + (-0.4) * (hOverL - 0.5) / 0.5
        }
        val th = doubleArrayOf(10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 45.0)
        val rows = listOf(
            0.25 to doubleArrayOf(-0.7, -0.5, -0.3, -0.2, -0.2, 0.0, 0.0),
            0.5 to doubleArrayOf(-0.9, -0.7, -0.4, -0.3, -0.2, -0.2, 0.0),
            1.0 to doubleArrayOf(-1.3, -1.0, -0.7, -0.5, -0.3, -0.2, 0.0)
        )
        fun at(row: DoubleArray, t: Double): Double {
            val tt = t.coerceIn(10.0, 45.0)
            val i = (th.indices).last { th[it] <= tt }.coerceAtMost(th.size - 2)
            return row[i] + (row[i + 1] - row[i]) * (tt - th[i]) / (th[i + 1] - th[i])
        }
        val h = hOverL.coerceIn(0.25, 1.0)
        val j = if (h <= 0.5) 0 else 1
        val (h0, r0) = rows[j]; val (h1, r1) = rows[j + 1]
        val w = at(r0, thetaDeg) + (at(r1, thetaDeg) - at(r0, thetaDeg)) * (h - h0) / (h1 - h0)
        // leeward: 10 deg -0.3/-0.5/-0.7, 15 deg -0.5/-0.5/-0.6, >=20 deg -0.6 (rows h/L <=0.25, 0.5, >=1.0)
        val lee = when {
            thetaDeg >= 20.0 -> -0.6
            else -> {
                val l10 = -0.3 + (-0.4) * ((h - 0.25) / 0.75).coerceIn(0.0, 1.0)
                val l15 = -0.5 + (-0.1) * ((h - 0.5) / 0.5).coerceIn(0.0, 1.0)
                if (thetaDeg <= 15.0) l10 + (l15 - l10) * (thetaDeg - 10.0) / 5.0 else l15 + (-0.6 - l15) * (thetaDeg - 15.0) / 5.0
            }
        }
        return minOf(w, lee)
    }

    /** Gf for flexible buildings, Eq 26-12 to 26-18 (p.239). Lengths in m, n1 in Hz, beta as a fraction (0.02 = 2%). */
    fun gustFlexible(exposure: SbcExposure, v: Double, h: Double, bAcross: Double, lAlong: Double, n1: Double, beta: Double): Double {
        val zb = maxOf(0.6 * h, exposure.zMin)
        val iz = exposure.c * (10.0 / zb).pow(1.0 / 6.0)
        val lz = exposure.lengthScale * (zb / 10.0).pow(exposure.epsBar)
        val q = 1.0 / sqrt(1.0 + 0.63 * ((bAcross + h) / lz).pow(0.63))
        val vbar = exposure.bBar * (zb / 10.0).pow(exposure.alphaBar) * v
        val n1c = n1 * lz / vbar
        val rn = 7.47 * n1c / (1.0 + 10.3 * n1c).pow(5.0 / 3.0)
        fun rl(eta: Double) = if (eta <= 0.0) 1.0 else 1.0 / eta - (1.0 - exp(-2.0 * eta)) / (2.0 * eta * eta)
        val rh = rl(4.6 * n1 * h / vbar)
        val rB = rl(4.6 * n1 * bAcross / vbar)
        val rL = rl(15.4 * n1 * lAlong / vbar)
        val r = sqrt((1.0 / beta) * rn * rh * rB * (0.53 + 0.47 * rL))
        val gq = 3.4; val gv = 3.4
        val lnTerm = sqrt(2.0 * ln(3600.0 * n1))
        val gr = lnTerm + 0.577 / lnTerm
        return 0.925 * (1.0 + 1.7 * iz * sqrt(gq * gq * q * q + gr * gr * r * r)) / (1.0 + 1.7 * gv * iz)
    }
}
