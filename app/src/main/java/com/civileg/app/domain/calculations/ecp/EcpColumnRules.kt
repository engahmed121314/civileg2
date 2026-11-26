package com.civileg.app.domain.calculations.ecp

import kotlin.math.max
import kotlin.math.min

/**
 * ECP 203-2025 column rules read from the scan (/downloads/file-6f2d5588.pdf), UNTESTED (not compiled in the sandbox).
 * Every constant carries its PDF page / printed page. Nothing here is invented; assumptions are labelled ASSUMPTION.
 */
object EcpColumnRules {
    enum class Position { MIDDLE, EDGE, CORNER }

    /** Eq 4-11 / 6-4-3 (PDF 91 p.4-11, PDF 233 p.6-42): e_min = max(0.05 x dimension in bending direction, 20 mm). */
    fun minEccentricity(bendingDimension: Double): Double = max(0.05 * bendingDimension, 20.0)

    /** Eq 6-32a (PDF 233 p.6-42): rectangular i = 0.30 b. */
    fun radiusRect(dimension: Double): Double = 0.30 * dimension

    /** Eq 6-32b (PDF 233 p.6-42): circular i = 0.25 D. */
    fun radiusCircular(diameter: Double): Double = 0.25 * diameter

    /** Table 6-7 (PDF 234 p.6-43): short-column limit on lambda_i: 50 braced, 35 unbraced. */
    fun shortLimitLambdaI(braced: Boolean): Double = if (braced) 50.0 else 35.0

    /** Max longitudinal steel (PDF 246 p.6-55): 4% middle, 5% edge, 6% corner columns; 8% only at laps. */
    fun maxSteelRatio(position: Position, atLap: Boolean = false): Double =
        if (atLap) 0.08 else when (position) {
            Position.MIDDLE -> 0.04
            Position.EDGE -> 0.05
            Position.CORNER -> 0.06
        }

    /**
     * Min steel, tied columns (PDF 246 p.6-55). requiredConcreteArea is the hand-calculated required area;
     * ASSUMPTION: the app has no such quantity, callers pass Ag, so the 0.6%-of-actual floor equals 0.6% Ag.
     * Within Table 6-7: 0.80% of required area, not below 0.60% of actual area.
     * Above Table 6-7 (rectangular, Eq 6-49): (0.25 + 0.052 lambda_b)% of required area.
     * General sections use Eq 6-48 (0.25 + 0.015 lambda_i); pass lambdaI for those.
     */
    fun minSteelArea(requiredConcreteArea: Double, actualArea: Double, slender: Boolean,
                     lambdaB: Double = 0.0, lambdaI: Double? = null): Double {
        val base = if (!slender) 0.0080 * requiredConcreteArea
        else if (lambdaI != null) (0.25 + 0.015 * lambdaI) / 100.0 * requiredConcreteArea
        else (0.25 + 0.052 * lambdaB) / 100.0 * requiredConcreteArea
        return max(base, 0.0060 * actualArea)
    }

    /** Ties (PDF 246 p.6-55): spacing <= min(15 x smallest long. bar dia, 200 mm). */
    fun tieMaxSpacing(smallestBarDia: Double): Double = min(15.0 * smallestBarDia, 200.0)

    /** Ties (PDF 246 p.6-55): dia >= max(8 mm, 1/4 largest long. bar). */
    fun tieMinDia(largestBarDia: Double): Double = max(8.0, largestBarDia / 4.0)

    /**
     * Table 6-12-c (PDF 245 p.6-54): alpha_n by Pu/Po: <=0.40: 1.20, 0.6: 1.25, 0.8: 1.30, 1.0: 1.40.
     * ASSUMPTION: linear interpolation between tabulated points (interpolation wording not read).
     */
    fun alphaN(puOverPo: Double): Double {
        val r = puOverPo.coerceIn(0.0, 1.0)
        return when {
            r <= 0.4 -> 1.20
            r <= 0.6 -> 1.20 + (r - 0.4) / 0.2 * 0.05
            r <= 0.8 -> 1.25 + (r - 0.6) / 0.2 * 0.05
            else -> 1.30 + (r - 0.8) / 0.2 * 0.10
        }
    }
}
