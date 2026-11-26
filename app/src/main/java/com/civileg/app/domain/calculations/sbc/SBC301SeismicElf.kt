package com.civileg.app.domain.calculations.sbc

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Equivalent lateral force procedure for buildings, SBC 301-CR-2018 (the Saudi Loading Code, 2018 edition, chapters 11 and 12).
 * Page numbers are the printed page numbers of the supplied PDF (printed page = PDF page - 18).
 *
 *  Ss and S1 (MCER mapped, Figures 22-1 and 22-2), TL (Figure 22-4) and the site class are INPUTS entered by the user from the maps / the
 *  geotechnical report. The app does not invent zone factors from a city name.                                   11.4.1 p.62, 11.4.5 p.63
 *  Eq 11-1, 11-2  SMS = Fa Ss, SM1 = Fv S1                                                                      p.63
 *  Eq 11-3, 11-4  SDS = 2/3 SMS, SD1 = 2/3 SM1                                                                  p.63
 *  Table 11-1 Fa, Table 11-2 Fv (straight-line interpolation, site class F needs site-specific study 11.4.7)    p.66
 *  Eq 11-5 to 11-8 design spectrum (T0 = 0.2 SD1/SDS, Ts = SD1/SDS)                                              p.63
 *  Table 11-3 / 11-4 Seismic Design Category, 11.6.1.1 / 11.6.1.2 (S1 >= 0.75 gives E or F), 11.6.2 more severe  p.64, p.66
 *  Table 1-3 Ie = 1.00 / 1.00 / 1.25 / 1.50 for risk category I / II / III / IV                                 p.11
 *  Eq 12-12 V = Cs W, Eq 12-13 Cs = SDS/(R/Ie), Eq 12-14 and 12-15 upper limit, Eq 12-16 lower limit
 *  (0.044 SDS Ie >= 0.01), Eq 12-17 lower limit when S1 >= 0.6 g                                                p.80
 *  12.8.1.3.1 Ss may be taken as at most 1.5 in Cs for regular structures of 5 stories or less with T <= 0.5 s   p.80
 *  12.8.2.2 T <= Cu Ta (Table 12-6), Eq 12-18 Ta = Ct hn^x (Table 12-7), Eq 12-19 Ta = 0.1 N                    p.81, p.104
 *  Eq 12-21 to 12-23 vertical distribution, k = 1 for T <= 0.5 s, 2 for T >= 2.5 s, linear between            p.81
 *  Eq 12-24 story shear                                                                                           p.81
 *  Eq 12-26 delta_x = Cd delta_xe / Ie, Table 12-8 allowable drift                                              p.82, p.104
 *  Eq 12-27 stability coefficient theta, Eq 12-28 theta_max = 0.5/(beta Cd) <= 0.25                             p.83
 *  Table 12-1 R, Omega0, Cd, height limits (subset of rows below)                                                p.98-99
 *
 * Story drift and P-delta checks on user-entered elastic deflections: [SbcDrift301] (12.8.6, 12.8.7, 12.12.1, Table 12-8).
 * NOT ASSESSED (no calculation is made, the result says so): site-specific ground motion (Chapter 21), irregularity checks (Tables 12-2,
 * 12-3), redundancy factor rho (12.3.4, entered by the user, default 1.0 only where the code says so), torsion (12.8.4.3 to 12.8.4.5),
 * modal response spectrum analysis, diaphragm design, footnotes of Table 12-1, the drift itself (needs an elastic analysis, 12.8.6).
 */
enum class SbcRiskCategory(val label: String, val ie: Double) {
    I("I", 1.00), II("II", 1.00), III("III", 1.25), IV("IV", 1.50)
}

enum class SbcSiteClass(val label: String) { A("A"), B("B"), C("C"), D("D"), E("E"), F("F") }

enum class SbcPeriodSystem(val label: String, val ct: Double, val x: Double) {
    CONCRETE_MOMENT_FRAME("Concrete moment-resisting frames", 0.0466, 0.9),
    STEEL_MOMENT_FRAME("Steel moment-resisting frames", 0.0724, 0.8),
    STEEL_EBF_OR_BRB("Steel eccentrically braced / buckling-restrained braced frames", 0.0731, 0.75),
    ALL_OTHER("All other structural systems", 0.0488, 0.75)
}

/** Table 12-1 row. A null height limit = NL (no limit), -1 = NP (not permitted). Limits are for SDC B, C, D, E, F. */
data class SbcSeismicSystem(
    val code: String, val name: String, val r: Double, val omega0: Double, val cd: Double,
    val limB: Double?, val limC: Double?, val limD: Double?, val limE: Double?, val limF: Double?,
    val periodSystem: SbcPeriodSystem
) {
    fun heightLimit(sdc: String): Double? = when (sdc) { "B" -> limB; "C" -> limC; "D" -> limD; "E" -> limE; "F" -> limF; else -> null }
}

object SbcSeismicSystems {
    private const val NP = -1.0
    val all: List<SbcSeismicSystem> = listOf(
        SbcSeismicSystem("A1", "Bearing wall: special reinforced concrete shear walls", 4.0, 2.5, 5.0, null, null, 50.0, 50.0, 30.0, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("A2", "Bearing wall: ordinary reinforced concrete shear walls", 3.0, 2.5, 4.0, null, null, NP, NP, NP, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("B2", "Building frame: steel special concentrically braced frames", 5.0, 2.0, 5.0, null, null, 50.0, 50.0, 30.0, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("B3", "Building frame: steel ordinary concentrically braced frames", 3.0, 2.0, 3.25, null, null, 11.0, 11.0, NP, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("B4", "Building frame: special reinforced concrete shear walls", 5.0, 2.5, 5.0, null, null, 50.0, 50.0, 30.0, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("B5", "Building frame: ordinary reinforced concrete shear walls", 4.0, 2.5, 4.5, null, null, NP, NP, NP, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("C1", "Moment frame: steel special moment frames", 7.0, 3.0, 5.5, null, null, null, null, null, SbcPeriodSystem.STEEL_MOMENT_FRAME),
        SbcSeismicSystem("C2", "Moment frame: steel intermediate moment frames", 4.0, 3.0, 4.0, null, null, 11.0, NP, NP, SbcPeriodSystem.STEEL_MOMENT_FRAME),
        SbcSeismicSystem("C3", "Moment frame: steel ordinary moment frames", 3.0, 3.0, 3.0, null, null, NP, NP, NP, SbcPeriodSystem.STEEL_MOMENT_FRAME),
        SbcSeismicSystem("C4", "Moment frame: special reinforced concrete moment frames", 6.5, 3.0, 5.5, null, null, null, null, null, SbcPeriodSystem.CONCRETE_MOMENT_FRAME),
        SbcSeismicSystem("C5", "Moment frame: intermediate reinforced concrete moment frames", 4.0, 3.0, 4.5, null, null, NP, NP, NP, SbcPeriodSystem.CONCRETE_MOMENT_FRAME),
        SbcSeismicSystem("C6", "Moment frame: ordinary reinforced concrete moment frames", 2.5, 3.0, 2.5, null, NP, NP, NP, NP, SbcPeriodSystem.CONCRETE_MOMENT_FRAME),
        SbcSeismicSystem("D3", "Dual system (special moment frames): special reinforced concrete shear walls", 6.0, 2.5, 5.5, null, null, null, null, null, SbcPeriodSystem.ALL_OTHER),
        SbcSeismicSystem("D4", "Dual system (special moment frames): ordinary reinforced concrete shear walls", 5.5, 2.5, 5.0, null, null, NP, NP, NP, SbcPeriodSystem.ALL_OTHER)
    )
    fun byCode(code: String): SbcSeismicSystem = all.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown Table 12-1 row '$code'")
}

data class SbcElfInput(
    val ss: Double,
    val s1: Double,
    val tl: Double,
    val siteClass: SbcSiteClass,
    val riskCategory: SbcRiskCategory,
    val system: SbcSeismicSystem,
    /** Structural height hn in m, base to highest level (Section 11.2). */
    val hn: Double,
    val storyWeights: List<Double>,   // kN, level 1 (first above base) first
    val storyHeights: List<Double>,   // m, height from base to each level, ascending
    /** Period from a substantiated analysis (12.8.2.1). Null = use Ta. */
    val analysisPeriod: Double? = null,
    /** Use Ta = 0.1 N (12.8.2.4.2): only valid for concrete/steel moment frames up to 12 stories with story height >= 3 m. */
    val useTaFromStories: Boolean = false,
    /** Apply 12.8.1.3.1 (regular, <= 5 stories, T <= 0.5 s): Ss limited to 1.5 in Cs. */
    val regularLowRise: Boolean = false
)

data class SbcElfResult(
    val fa: Double, val fv: Double, val sms: Double, val sm1: Double, val sds: Double, val sd1: Double,
    val sdc: String, val sdcNote: String,
    val ie: Double, val r: Double, val omega0: Double, val cd: Double,
    val ta: Double, val cu: Double, val t: Double, val periodNote: String,
    val csCalculated: Double, val csUpper: Double, val csLowerEq1216: Double, val csLowerEq1217: Double?, val cs: Double, val csGoverning: String,
    val w: Double, val v: Double, val k: Double,
    val levelForces: List<Double>, val storyShears: List<Double>, val overturningAtBase: Double,
    val heightLimit: Double?, val heightLimitOk: Boolean?, val systemNote: String,
    val notAssessed: List<String>, val warnings: List<String>
)

object SbcSeismic301 {

    private val SS_NODES = doubleArrayOf(0.25, 0.5, 0.75, 1.0, 1.25)
    private val FA = mapOf(
        SbcSiteClass.A to doubleArrayOf(0.8, 0.8, 0.8, 0.8, 0.8),
        SbcSiteClass.B to doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0),
        SbcSiteClass.C to doubleArrayOf(1.2, 1.2, 1.1, 1.0, 1.0),
        SbcSiteClass.D to doubleArrayOf(1.6, 1.4, 1.2, 1.1, 1.0),
        SbcSiteClass.E to doubleArrayOf(2.5, 1.7, 1.2, 0.9, 0.9)
    )
    private val S1_NODES = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5)
    private val FV = mapOf(
        SbcSiteClass.A to doubleArrayOf(0.8, 0.8, 0.8, 0.8, 0.8),
        SbcSiteClass.B to doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0),
        SbcSiteClass.C to doubleArrayOf(1.7, 1.6, 1.5, 1.4, 1.3),
        SbcSiteClass.D to doubleArrayOf(2.4, 2.0, 1.8, 1.6, 1.5),
        SbcSiteClass.E to doubleArrayOf(3.5, 3.2, 2.8, 2.4, 2.4)
    )

    private fun interp(nodes: DoubleArray, values: DoubleArray, x: Double): Double {
        if (x <= nodes.first()) return values.first()
        if (x >= nodes.last()) return values.last()
        for (i in 0 until nodes.size - 1) {
            if (x <= nodes[i + 1]) return values[i] + (values[i + 1] - values[i]) * (x - nodes[i]) / (nodes[i + 1] - nodes[i])
        }
        return values.last()
    }

    /** Table 11-1, p.66. Site class F: site-specific study required (11.4.7), so no value is returned. */
    fun fa(siteClass: SbcSiteClass, ss: Double): Double {
        require(siteClass != SbcSiteClass.F) { "Site Class F needs a site-specific ground motion study (SBC 301 11.4.7), Fa is NOT ASSESSED / الموقع F يحتاج دراسة خاصة" }
        return interp(SS_NODES, FA.getValue(siteClass), ss)
    }

    /** Table 11-2, p.66. */
    fun fv(siteClass: SbcSiteClass, s1: Double): Double {
        require(siteClass != SbcSiteClass.F) { "Site Class F needs a site-specific ground motion study (SBC 301 11.4.7), Fv is NOT ASSESSED / الموقع F يحتاج دراسة خاصة" }
        return interp(S1_NODES, FV.getValue(siteClass), s1)
    }

    /** Table 12-6, p.104. Linear interpolation between the listed SD1 values (the table lists 0.1, 0.15, 0.2, 0.3, 0.4). */
    fun cu(sd1: Double): Double {
        val nodes = doubleArrayOf(0.1, 0.15, 0.2, 0.3, 0.4)
        val values = doubleArrayOf(1.7, 1.6, 1.5, 1.4, 1.4)
        return interp(nodes, values, sd1)
    }

    private fun sdcFromTable(value: Double, cuts: DoubleArray, riskIv: Boolean): String {
        // Tables 11-3 / 11-4: rows A (below first cut), B, C, D. Risk category IV gives A, C, D, D.
        val idx = cuts.count { value >= it }          // 0..3
        return if (!riskIv) "ABCD"[idx].toString() else "ACDD"[idx].toString()
    }

    /** Section 11.6 and Tables 11-3, 11-4 (p.64, p.66). Returns SDC letter and a note. */
    fun seismicDesignCategory(risk: SbcRiskCategory, sds: Double, sd1: Double, s1: Double): Pair<String, String> {
        val iv = risk == SbcRiskCategory.IV
        if (s1 >= 0.75) return (if (iv) "F" else "E") to "11.6.1.1 / 11.6.1.2: S1 >= 0.75 g"
        val a = sdcFromTable(sds, doubleArrayOf(0.167, 0.33, 0.50), iv)
        val b = sdcFromTable(sd1, doubleArrayOf(0.067, 0.133, 0.20), iv)
        val sdc = if (a >= b) a else b           // letters sort A < B < C < D
        return sdc to "11.6.2: more severe of Table 11-3 (SDS) = $a and Table 11-4 (SD1) = $b; Table 11-3 alone only where 11.6.3 applies"
    }

    /** Design spectrum Sa(T), Eq 11-5 to 11-8, p.63. */
    fun designSpectrum(t: Double, sds: Double, sd1: Double, tl: Double): Double {
        require(t >= 0.0 && sds > 0.0 && sd1 > 0.0 && tl > 0.0)
        val t0 = 0.2 * sd1 / sds
        val ts = sd1 / sds
        return when {
            t < t0 -> sds * (0.4 + 0.6 * t / t0)
            t <= ts -> sds
            t <= tl -> sd1 / t
            else -> sd1 * tl / (t * t)
        }
    }

    /** Eq 12-23 exponent, 12.8.3 p.81. */
    fun kExponent(t: Double): Double = when {
        t <= 0.5 -> 1.0
        t >= 2.5 -> 2.0
        else -> 1.0 + (t - 0.5) / 2.0
    }

    /** Table 12-8 (p.104), "all other structures" row. The full table with every row is [SbcDriftStructureType]. */
    fun allowableDriftRatioAllOthers(risk: SbcRiskCategory): Double = when (risk) {
        SbcRiskCategory.I, SbcRiskCategory.II -> 0.020
        SbcRiskCategory.III -> 0.015
        SbcRiskCategory.IV -> 0.010
    }

    /** Eq 12-26 (p.82). The full story-drift check on analysis deflections is [SbcDrift301.check]. */
    fun amplifiedDeflection(deltaXe: Double, cd: Double, ie: Double): Double = cd * deltaXe / ie

    /** Eq 12-27 and 12-28 p.83. beta is the shear demand/capacity ratio, 1.0 is permitted as conservative. */
    fun stabilityCoefficient(px: Double, delta: Double, ie: Double, vx: Double, hsx: Double, cd: Double): Double = px * delta * ie / (vx * hsx * cd)
    fun thetaMax(beta: Double, cd: Double): Double = min(0.5 / (beta * cd), 0.25)

    fun compute(input: SbcElfInput): SbcElfResult {
        require(input.ss.isFinite() && input.ss > 0.0) { "Ss (0.2 s mapped acceleration, g) must be positive / Ss يجب أن يكون موجباً" }
        require(input.s1.isFinite() && input.s1 > 0.0) { "S1 (1 s mapped acceleration, g) must be positive / S1 يجب أن يكون موجباً" }
        require(input.tl.isFinite() && input.tl > 0.0) { "TL (long-period transition, s) must be positive / TL يجب أن يكون موجباً" }
        require(input.hn.isFinite() && input.hn > 0.0) { "Structural height hn (m) must be positive / ارتفاع المنشأ يجب أن يكون موجباً" }
        require(input.storyWeights.isNotEmpty()) { "Story weights are required / أوزان الأدوار مطلوبة" }
        require(input.storyWeights.size == input.storyHeights.size) { "One height per story weight is required / ارتفاع لكل وزن دور" }
        require(input.storyWeights.all { it.isFinite() && it > 0.0 }) { "Each story weight must be positive / وزن كل دور موجب" }
        require(input.storyHeights.all { it.isFinite() && it > 0.0 }) { "Each level height must be positive / ارتفاع كل منسوب موجب" }
        require(input.storyHeights.zipWithNext().all { (a, b) -> b > a }) { "Level heights must increase from the base / المناسيب تزيد من الأساس" }

        val warnings = mutableListOf<String>()
        val fa = fa(input.siteClass, input.ss)
        val fv = fv(input.siteClass, input.s1)
        val sms = fa * input.ss
        val sm1 = fv * input.s1
        val sds = 2.0 / 3.0 * sms
        val sd1 = 2.0 / 3.0 * sm1
        val (sdc, sdcNote) = seismicDesignCategory(input.riskCategory, sds, sd1, input.s1)
        if (input.ss <= 0.15 && input.s1 <= 0.04) warnings.add("11.4.1: Ss <= 0.15 and S1 <= 0.04, the structure may be assigned SDC A and only needs Section 11.7")

        val ie = input.riskCategory.ie
        val sys = input.system
        val w = input.storyWeights.sum()

        // Period, 12.8.2
        val ctx = sys.periodSystem
        val ta = if (input.useTaFromStories) {
            require(ctx == SbcPeriodSystem.CONCRETE_MOMENT_FRAME || ctx == SbcPeriodSystem.STEEL_MOMENT_FRAME) {
                "Ta = 0.1 N (12.8.2.4.2) applies only where the seismic force-resisting system is entirely concrete or steel moment frames / المعادلة 12-19 للإطارات فقط"
            }
            require(input.storyWeights.size <= 12 && input.hn / input.storyWeights.size >= 3.0) { "Ta = 0.1 N needs N <= 12 and average story height >= 3 m (12.8.2.4.2) / شروط 12-19 غير متحققة" }
            0.1 * input.storyWeights.size
        } else ctx.ct * input.hn.pow(ctx.x)
        val cu = cu(sd1)
        val t: Double
        val periodNote: String
        if (input.analysisPeriod != null) {
            require(input.analysisPeriod.isFinite() && input.analysisPeriod > 0.0) { "Analysis period must be positive / الدور يجب أن يكون موجباً" }
            t = min(input.analysisPeriod, cu * ta)
            periodNote = if (input.analysisPeriod > cu * ta) "12.8.2.2: analysis period ${"%.3f".format(input.analysisPeriod)} s exceeds Cu*Ta, T = Cu*Ta = ${"%.3f".format(cu * ta)} s used"
            else "12.8.2.1: period from analysis"
        } else {
            t = ta
            periodNote = if (input.useTaFromStories) "12.8.2.3 / Eq 12-19: Ta = 0.1 N" else "12.8.2.3 / Eq 12-18: Ta = Ct hn^x, Ct = ${ctx.ct}, x = ${ctx.x} (Table 12-7)"
        }

        // Cs, 12.8.1.1
        val rOverIe = sys.r / ie
        val sdsForCs = if (input.regularLowRise && input.storyWeights.size <= 5 && t <= 0.5) {
            2.0 / 3.0 * fa * min(input.ss, 1.5)
        } else {
            if (input.regularLowRise) warnings.add("12.8.1.3.1 not applicable (needs <= 5 stories and T <= 0.5 s), Ss not limited")
            sds
        }
        val csCalc = sdsForCs / rOverIe
        val csUpper = if (t <= input.tl) sd1 / (t * rOverIe) else sd1 * input.tl / (t * t * rOverIe)
        val csLower1216 = max(0.044 * sds * ie, 0.01)
        val csLower1217 = if (input.s1 >= 0.6) max(0.5 * input.s1 / rOverIe, 0.01) else null
        var cs = min(csCalc, csUpper)
        var gov = if (csCalc <= csUpper) "Eq 12-13" else if (t <= input.tl) "Eq 12-14 (upper limit)" else "Eq 12-15 (upper limit)"
        if (cs < csLower1216) { cs = csLower1216; gov = "Eq 12-16 (lower limit)" }
        if (csLower1217 != null && cs < csLower1217) { cs = csLower1217; gov = "Eq 12-17 (lower limit, S1 >= 0.6 g)" }
        val v = cs * w

        // Vertical distribution, 12.8.3
        val k = kExponent(t)
        val wh = input.storyWeights.indices.sumOf { input.storyWeights[it] * input.storyHeights[it].pow(k) }
        val forces = input.storyWeights.indices.map { input.storyWeights[it] * input.storyHeights[it].pow(k) / wh * v }
        val shears = forces.indices.map { x -> forces.subList(x, forces.size).sum() }
        val otm = forces.indices.sumOf { forces[it] * input.storyHeights[it] }

        // Table 12-1 height limit
        val lim = sys.heightLimit(sdc)
        val limOk: Boolean?
        val sysNote: String
        if (sdc == "A") { limOk = null; sysNote = "SDC A: Table 12-1 limits start at SDC B, 11.7 applies" }
        else if (lim == null) { limOk = true; sysNote = "Table 12-1 row ${sys.code}: no height limit (NL) in SDC $sdc" }
        else if (lim < 0.0) { limOk = false; sysNote = "Table 12-1 row ${sys.code}: system NOT PERMITTED in SDC $sdc"; warnings.add(sysNote) }
        else { limOk = input.hn <= lim; sysNote = "Table 12-1 row ${sys.code}: height limit $lim m in SDC $sdc"; if (!limOk) warnings.add("$sysNote, hn = ${input.hn} m exceeds it") }

        val na = mutableListOf(
            "Irregularity checks (Tables 12-2, 12-3) and the redundancy factor (12.3.4)",
            "Torsion (12.8.4.3 to 12.8.4.5)",
            "The elastic deflections for drift and P-delta (12.8.6.5): they come from the structural analysis; the checks of entered deflections are SbcDrift301 (Eq 12-26 to 12-28, Table 12-8)",
            "Footnotes of Table 12-1 and the detailing chapter 14 requirements",
            "Site-specific ground motion (Chapter 21)"
        )
        if (input.siteClass == SbcSiteClass.E) warnings.add("Site Class E: check 11.8 / geotechnical requirements for liquefiable or soft soils")
        return SbcElfResult(fa, fv, sms, sm1, sds, sd1, sdc, sdcNote, ie, sys.r, sys.omega0, sys.cd, ta, cu, t, periodNote,
            csCalc, csUpper, csLower1216, csLower1217, cs, gov, w, v, k, forces, shears, otm, lim, limOk, sysNote, na, warnings)
    }
}
