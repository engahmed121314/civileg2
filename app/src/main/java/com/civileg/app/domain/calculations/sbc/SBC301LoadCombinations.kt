package com.civileg.app.domain.calculations.sbc

/**
 * Strength-design load combinations, SBC 301-CR-2018 (printed page = PDF page - 18).
 *
 *  Eq 2-1 (2.3.2, p.13):  1) 1.4D   2*) 1.4D + 1.7L + 0.5(Lr or R)   3) 1.2D + 1.6(Lr or R) + (L or 0.5W)
 *                         4) 1.2D + 1.0W + L + 0.5(Lr or R)   5) 1.2D + 1.0E + L   6) 0.9D + 1.0W   7) 0.9D + 1.0E
 *  Exception 1 (p.13): in combination 2* the factors may be 1.2D and 1.6L when a special inspection professional is employed and approved.
 *  Exception 2 (p.13): the factor on L in combinations 3, 4, 5 may be 0.5 where Lo of Table 4-1 <= 5 kN/m2 (not garages, not public assembly).
 *  2.3.2.3 (p.14): effects of loads not acting must be investigated, wind and earthquake need not act together.
 *  12.4.2.3.1, Eq 12-5 (p.76), replace combinations 5 and 7 where the seismic load effect is E = rho QE +- Ev:
 *      (1.2 + 0.2 SDS) D + rho QE + L         (0.9 - 0.2 SDS) D + rho QE
 *      Ev = 0.2 SDS D may be taken as zero when SDS <= 0.125 (12.4.2.2 exceptions, p.76).
 *  12.4.3.2.1, Eq 12-10 (p.77), overstrength: replace rho QE by Omega0 QE.
 *
 * Effects are signed member effects (axial, moment or shear at the same section), computed separately for each unfactored load. The
 * engine does not apply a lumped "average" factor to D + L: each load keeps its own factor.
 * NOT ASSESSED: flood (2.3.3), self-straining T (2.3.5), H and F loads (2.3.2.1, 2.3.2.2), ice (not applicable in KSA), allowable stress
 * design (2.4), ECP 201 and ACI 318 combinations (those codes are not in hand and are handled elsewhere).
 */
data class SbcLoadEffects(
    val d: Double,
    val l: Double = 0.0,
    val lr: Double = 0.0,
    val r: Double = 0.0,
    /** Signed wind effect from the Chapter 26-27 analysis; the reversed direction is generated automatically. */
    val w: Double = 0.0,
    /** Signed horizontal seismic effect QE (kN, kN.m...) from the base shear V; the reversed direction is generated automatically. */
    val qe: Double = 0.0
)

data class SbcCombo(val id: String, val text: String, val value: Double)

object SbcLoadCombinations301 {

    /**
     * @param halfLiveFactor Exception 2 (p.13): L factor 0.5 in combinations 3, 4, 5 (caller confirms Lo <= 5 kN/m2 and not garage/assembly).
     * @param specialInspection Exception 1 (p.13): combination 2 uses 1.2D + 1.6L.
     */
    fun basic(e: SbcLoadEffects, halfLiveFactor: Boolean = false, specialInspection: Boolean = false): List<SbcCombo> {
        val out = mutableListOf<SbcCombo>()
        val lf = if (halfLiveFactor) 0.5 else 1.0
        val lfTxt = if (halfLiveFactor) "0.5L" else "L"
        out += SbcCombo("1", "1.4D", 1.4 * e.d)
        val roofVariants = listOf("Lr" to e.lr, "R" to e.r)
        for ((nm, x) in roofVariants) {
            if (nm == "R" && e.r == 0.0) continue
            if (nm == "Lr" && e.lr == 0.0 && e.r != 0.0) continue
            if (specialInspection) out += SbcCombo("2 ($nm)", "1.2D + 1.6L + 0.5$nm", 1.2 * e.d + 1.6 * e.l + 0.5 * x)
            else out += SbcCombo("2* ($nm)", "1.4D + 1.7L + 0.5$nm", 1.4 * e.d + 1.7 * e.l + 0.5 * x)
        }
        for ((nm, x) in roofVariants) {
            if (nm == "R" && e.r == 0.0) continue
            if (nm == "Lr" && e.lr == 0.0 && e.r != 0.0) continue
            out += SbcCombo("3 ($nm, $lfTxt)", "1.2D + 1.6$nm + $lfTxt", 1.2 * e.d + 1.6 * x + lf * e.l)
            for (sg in listOf(1.0, -1.0)) {
                if (e.w == 0.0 && sg < 0) continue
                out += SbcCombo("3 ($nm, ${if (sg > 0) "+" else "-"}0.5W)", "1.2D + 1.6$nm ${if (sg > 0) "+" else "-"} 0.5W", 1.2 * e.d + 1.6 * x + 0.5 * sg * e.w)
            }
            for (sg in listOf(1.0, -1.0)) {
                if (e.w == 0.0 && sg < 0) continue
                out += SbcCombo("4 ($nm, ${if (sg > 0) "+" else "-"}W)", "1.2D ${if (sg > 0) "+" else "-"} 1.0W + $lfTxt + 0.5$nm", 1.2 * e.d + sg * e.w + lf * e.l + 0.5 * x)
            }
        }
        if (e.w != 0.0) for (sg in listOf(1.0, -1.0)) {
            out += SbcCombo("6 (${if (sg > 0) "+" else "-"}W)", "0.9D ${if (sg > 0) "+" else "-"} 1.0W", 0.9 * e.d + sg * e.w)
        } else out += SbcCombo("6", "0.9D (W = 0)", 0.9 * e.d)
        return out
    }

    /**
     * Seismic combinations 5 and 7. With [omega0] set, Eq 12-10 (overstrength) is used instead of Eq 12-5.
     * @param rho redundancy factor, 12.3.4 (entered by the user; the code value 1.0 or 1.3 depends on the structure, not decided here).
     */
    fun seismic(e: SbcLoadEffects, sds: Double, rho: Double, omega0: Double? = null, halfLiveFactor: Boolean = false): List<SbcCombo> {
        require(sds.isFinite() && sds >= 0.0) { "SDS must be non-negative / SDS غير سالب" }
        require(rho.isFinite() && rho >= 1.0) { "Redundancy factor rho must be entered (>= 1.0, 12.3.4) / معامل التكرار ρ مطلوب" }
        val lf = if (halfLiveFactor) 0.5 else 1.0
        val lfTxt = if (halfLiveFactor) "0.5L" else "L"
        val evOn = sds > 0.125
        val k = if (evOn) 0.2 * sds else 0.0
        val q = omega0 ?: rho
        val qName = if (omega0 != null) "Ω0 QE" else "ρ QE"
        val out = mutableListOf<SbcCombo>()
        for (sg in listOf(1.0, -1.0)) {
            val s = if (sg > 0) "+" else "-"
            out += SbcCombo("5 (${s}E)", "(1.2 + 0.2 SDS) D $s $qName + $lfTxt", (1.2 + k) * e.d + sg * q * e.qe + lf * e.l)
            out += SbcCombo("7 (${s}E)", "(0.9 - 0.2 SDS) D $s $qName", (0.9 - k) * e.d + sg * q * e.qe)
        }
        return out
    }

    fun governingMax(c: List<SbcCombo>): SbcCombo = c.maxByOrNull { it.value } ?: throw IllegalArgumentException("No combinations")
    fun governingMin(c: List<SbcCombo>): SbcCombo = c.minByOrNull { it.value } ?: throw IllegalArgumentException("No combinations")

    /** Largest absolute effect over a set of combinations (for sizing), with the combination that produced it. */
    fun governingAbs(c: List<SbcCombo>): SbcCombo = c.maxByOrNull { kotlin.math.abs(it.value) } ?: throw IllegalArgumentException("No combinations")
}
