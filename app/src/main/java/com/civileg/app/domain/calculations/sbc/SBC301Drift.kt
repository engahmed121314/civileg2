package com.civileg.app.domain.calculations.sbc

import kotlin.math.abs
import kotlin.math.min

/**
 * Story drift and P-delta checks, SBC 301-CR-2018 12.8.6, 12.8.7, 12.12.1 and Table 12-8.
 * Page numbers are the printed page numbers of the supplied SBC 301-CR-2018 PDF (printed page = PDF page - 18).
 *
 *  12.8.6.1 (p.82)   story drift Delta_x = delta_x - delta_(x-1) at the centers of mass
 *  Eq 12-26 (p.82)   delta_x = Cd delta_xe / Ie, delta_xe from an elastic analysis under the design forces
 *  12.12.1 (p.86)    Delta_x <= Delta_a (Table 12-8) for any story
 *  Table 12-8 (p.104) allowable drift ratio Delta_a/hsx by risk category and structure type;
 *                    footnote b: systems solely of moment frames in SDC D/E/F use Delta_a/rho (12.12.1.1, p.86);
 *                    footnote c: no drift limit for single-story structures whose walls accommodate the drift
 *  12.8.7.1 (p.83)   P-delta need not be considered where theta <= 0.10
 *  Eq 12-27 (p.83)   theta = Px Delta Ie / (Vx hsx Cd), Px with no individual load factor exceeding 1.0
 *  Eq 12-28 (p.83)   theta_max = 0.5/(beta Cd) <= 0.25; 12.8.7.5: theta > theta_max means potentially unstable
 *
 * The elastic deflections delta_xe are INPUTS from the structural analysis (12.8.6.5); none is invented here.
 * NOT ASSESSED: torsional amplification Ax (12.8.4.3 to 12.8.4.5), diaphragm deflection (12.12.2), structure
 * separation (12.12.3), and beta (the shear demand/capacity ratio, which 12.8.7.2 permits to take as 1.0
 * conservatively; it is a caller input, not a value decided here).
 */

/** Table 12-8 (p.104) rows: allowable story drift ratio Delta_a/hsx for risk category I-II / III / IV. */
enum class SbcDriftStructureType(val ratioRiskIorII: Double, val ratioRiskIII: Double, val ratioRiskIV: Double, val label: String) {
    NON_MASONRY_4_STORY_ACCOMMODATING(0.025, 0.020, 0.015, "Structures other than masonry shear wall structures, 4 stories or less, walls designed to accommodate the story drifts"),
    MASONRY_CANTILEVER_SHEAR_WALL(0.010, 0.010, 0.010, "Masonry cantilever shear wall structures"),
    MASONRY_OTHER_SHEAR_WALL(0.007, 0.007, 0.007, "Other masonry shear wall structures"),
    ALL_OTHER(0.020, 0.015, 0.010, "All other structures");

    fun allowableRatio(risk: SbcRiskCategory): Double = when (risk) {
        SbcRiskCategory.I, SbcRiskCategory.II -> ratioRiskIorII
        SbcRiskCategory.III -> ratioRiskIII
        SbcRiskCategory.IV -> ratioRiskIV
    }
}

/** One story of the drift check, level x (level 1 = first above base). */
data class SbcDriftStory(
    /** Vx, kN: design story shear between levels x and x-1 (12.8.4.1). */
    val vx: Double,
    /** Px, kN: total vertical design load at level x; no individual load factor need exceed 1.0 (12.8.7.1). */
    val px: Double,
    /** hsx, mm: story height below level x. */
    val hsxMm: Double,
    /** delta_xe, mm: elastic deflection at the center of mass of level x under the design forces (12.8.6.5). */
    val deltaXeMm: Double
)

data class SbcDriftRow(
    val level: Int,
    val deltaXeMm: Double,
    val deltaXMm: Double,
    val driftMm: Double,
    val driftRatio: Double,
    /** Null where Table 12-8 footnote c waives the drift limit (single story). */
    val allowableRatio: Double?,
    val driftOk: Boolean?,
    val theta: Double,
    val thetaMax: Double,
    /** True where theta <= 0.10 (P-delta not required, 12.8.7.1) or theta <= theta_max (12.8.7.2). */
    val pdeltaOk: Boolean,
    val notes: List<String>
)

data class SbcDriftResult(
    val rows: List<SbcDriftRow>,
    val maxDriftRatio: Double,
    val maxTheta: Double,
    /** True only where every checked story passed; false where any failed; null where footnote c waived the only limit. */
    val driftOk: Boolean?,
    val pdeltaOk: Boolean,
    val notAssessed: List<String>
)

object SbcDrift301 {

    fun check(
        stories: List<SbcDriftStory>,
        cd: Double,
        ie: Double,
        structureType: SbcDriftStructureType,
        risk: SbcRiskCategory,
        /** 12.12.1.1 (p.86): the seismic force-resisting system is solely moment frames in SDC D, E or F. */
        momentFramesOnlyInSdcDef: Boolean,
        /** Redundancy factor rho (12.3.4.2), required when momentFramesOnlyInSdcDef; entered by the user, not invented. */
        rho: Double? = null,
        /** beta = shear demand/capacity ratio (Eq 12-28); 1.0 is permitted as conservative (12.8.7.2). */
        beta: Double = 1.0
    ): SbcDriftResult {
        require(stories.isNotEmpty()) { "Stories are required / الأدوار مطلوبة" }
        require(cd.isFinite() && cd > 0.0) { "Cd (Table 12-1) must be positive / Cd يجب أن يكون موجباً" }
        require(ie.isFinite() && ie > 0.0) { "Ie (Table 1-3) must be positive / Ie يجب أن يكون موجباً" }
        require(beta.isFinite() && beta > 0.0) { "beta (12.8.7.2) must be positive / β يجب أن يكون موجباً" }
        stories.forEachIndexed { i, s ->
            require(s.hsxMm.isFinite() && s.hsxMm > 0.0) { "Story height hsx must be positive at level ${i + 1} / ارتفاع الدور موجب" }
            require(s.vx.isFinite() && s.vx > 0.0) { "Story shear Vx must be positive at level ${i + 1} / قص الدور موجب" }
            require(s.px.isFinite() && s.px >= 0.0) { "Story load Px must be non-negative at level ${i + 1} / حمل الدور غير سالب" }
            require(s.deltaXeMm.isFinite()) { "Elastic deflection must be finite at level ${i + 1} / الانحراف المرن مطلوب" }
        }
        val rhoEff: Double
        if (momentFramesOnlyInSdcDef) {
            require(rho != null && rho.isFinite() && rho >= 1.0) { "12.12.1.1 needs the redundancy factor rho (12.3.4.2), entered by the user / معامل التكرار ρ مطلوب" }
            rhoEff = rho
        } else {
            rhoEff = 1.0
        }

        val baseAllowable = structureType.allowableRatio(risk)
        val singleStoryNoLimit = stories.size == 1 && structureType == SbcDriftStructureType.NON_MASONRY_4_STORY_ACCOMMODATING
        val allowableRatio: Double? = if (singleStoryNoLimit) null else baseAllowable / rhoEff

        val thetaMax = min(0.5 / (beta * cd), 0.25)
        val rows = mutableListOf<SbcDriftRow>()
        var prevDeltaX = 0.0
        stories.forEachIndexed { i, s ->
            val deltaX = cd * s.deltaXeMm / ie                                  // Eq 12-26
            val drift = abs(deltaX - prevDeltaX)                                // 12.8.6.1
            prevDeltaX = deltaX
            val ratio = drift / s.hsxMm
            val driftOk: Boolean? = allowableRatio?.let { ratio <= it }
            val theta = s.px * drift * ie / (s.vx * s.hsxMm * cd)               // Eq 12-27
            val notes = mutableListOf<String>()
            if (singleStoryNoLimit) notes.add("Table 12-8 footnote c (p.104): no drift limit for a single-story structure whose walls accommodate the drift; 12.12.3 separation is not waived")
            val pdeltaOk = if (theta <= 0.10) {
                notes.add("12.8.7.1: theta <= 0.10, P-delta effects need not be considered")
                true
            } else {
                if (theta > thetaMax) notes.add("12.8.7.5: theta > theta_max, the structure is potentially unstable and shall be redesigned")
                else notes.add("12.8.7.2: theta <= theta_max, P-delta effects shall be considered (12.8.7.3 / 12.8.7.4)")
                theta <= thetaMax
            }
            rows += SbcDriftRow(i + 1, s.deltaXeMm, deltaX, drift, ratio, allowableRatio, driftOk, theta, thetaMax, pdeltaOk, notes)
        }

        val na = listOf(
            "Torsional effects and the amplification Ax (12.8.4.3 to 12.8.4.5): the deflections entered are used as given",
            "Diaphragm deflection (12.12.2)",
            "Structure separation (12.12.3)",
            "The elastic deflections themselves: they come from the structural analysis under the design forces (12.8.6.5), never from this check"
        )
        val allDriftOk = when {
            rows.all { it.driftOk == null } -> null
            else -> rows.all { it.driftOk != false }
        }
        return SbcDriftResult(rows, rows.maxOf { it.driftRatio }, rows.maxOf { it.theta }, allDriftOk, rows.all { it.pdeltaOk }, na)
    }
}
