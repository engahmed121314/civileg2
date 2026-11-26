package com.civileg.app.domain.calculations.beam

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tension development and lap lengths of deformed bars, transcribed from the primary code texts.
 *
 * Sources (checked against page images / text, not recall):
 *  - ECP 203-2020 (5th update) section 4-2-5-1, Eq. [4-66] and [4-67], Tables 4-7, 4-8, 4-9 (consistency),
 *    item (c) minimum length, and section 4-2-5-4-1 lap splices.
 *  - SBC 304-CR-2018 (text) 25.4.2.1, Table 25.4.2.2, Table 25.4.2.4, 25.4.1.4, 25.4.3.1, Table 25.5.2.1.
 *  - ACI 318M-14: the supplied bilingual volume 4 shows the same 25.4.2.1 (300 mm) and the same Table 25.4.2.2
 *    coefficients, with "No. 19 and smaller" where SBC says "Dia 20 and smaller".
 *
 * Scope limits (the caller must say so wherever a result is shown):
 *  - Straight deformed bars in tension. No bundled bars, no epoxy coating, no lightweight concrete, no headed bars.
 *  - It is a length from the code formula. It does not check available anchorage, curtailment, or detailing.
 */
object RcDevelopmentLength {

    /** ECP 203-2020 material factors used by Eq. [4-66] and [4-67]. */
    const val ECP_GAMMA_S = 1.15
    const val ECP_GAMMA_C = 1.5

    data class Result(
        /** Development length from the code formula, before the code minimum. */
        val formulaLength: Double,
        /** Length to use: formula length not less than the code minimum. */
        val length: Double,
        /** Minimum that governed, mm. */
        val minimum: Double,
        /** One-line statement of the clause and the inputs used. */
        val basis: String
    )

    /**
     * ECP 203-2020 Eq. [4-66]: Ld = alpha*beta*eta*(fy/gammaS) / (4*fbu) * phi, with Eq. [4-67]
     * fbu = 0.30*sqrt(fcu/gammaC).
     *  alpha: Table 4-7, 1.0 for a straight end (0.75 for the hook/bend shapes, tension).
     *  beta: Table 4-8 in tension, 1.00 plain bar, 0.75 deformed bar.
     *  eta: 1.30 for horizontal tension bars with more than 300 mm of concrete cast below them, else 1.00.
     * Minimum (item c): 40 phi or 300 mm, whichever is larger, for deformed bars.
     */
    fun ecpTension(
        fcuMpa: Double, fyMpa: Double, dbMm: Double,
        topBarWithMoreThan300mmBelow: Boolean = false,
        endShapeAlpha: Double = 1.0
    ): Result {
        require(fcuMpa > 0 && fyMpa > 0 && dbMm > 0) { "fcu, fy and db must be positive" }
        val fbu = 0.30 * sqrt(fcuMpa / ECP_GAMMA_C)
        val eta = if (topBarWithMoreThan300mmBelow) 1.30 else 1.00
        val beta = 0.75
        val formula = endShapeAlpha * beta * eta * (fyMpa / ECP_GAMMA_S) / (4.0 * fbu) * dbMm
        val minimum = max(40.0 * dbMm, 300.0)
        return Result(
            formulaLength = formula,
            length = max(formula, minimum),
            minimum = minimum,
            basis = "ECP 203-2020 Eq. 4-66/4-67: alpha=$endShapeAlpha, beta=0.75 (deformed), eta=$eta, " +
                "fbu=0.30*sqrt(fcu/1.5); minimum max(40 phi, 300 mm)"
        )
    }

    /**
     * ECP 203-2020 section 4-2-5-4-1 tension lap: Ld when the bars are staggered and the provided area is at
     * least twice the required area and not more than 50 percent of the bars are lapped at the section,
     * otherwise 1.3 Ld. [asProvidedOverRequired] is null when the required area at the splice is not known,
     * which takes the 1.3 Ld case.
     */
    fun ecpTensionLap(ld: Double, asProvidedOverRequired: Double?, fractionLapped: Double?): Double {
        val relaxed = asProvidedOverRequired != null && fractionLapped != null &&
            asProvidedOverRequired >= 2.0 && fractionLapped <= 0.50
        return if (relaxed) ld else 1.3 * ld
    }

    /**
     * SBC 304-CR-2018 25.4.2 / ACI 318M-14 25.4.2 deformed bars in tension, normalweight concrete (lambda = 1),
     * uncoated (psi_e = 1.0):
     *  ld = (fy * psi_t * psi_e * psi_s / (k * lambda * sqrt(fc'))) * db, not less than 300 mm (25.4.2.1),
     *  with sqrt(fc') not more than 8.3 MPa (25.4.1.4) and psi_t*psi_e not more than 1.7.
     * Table 25.4.2.2: [spacingAndCoverMet] true gives k = 2.1 (small bars) / 1.7 (large bars), false ("Other cases")
     * gives 1.4 / 1.1. The caller should pass false unless spacing, cover and ties are shown to meet the table row.
     * Small bars: SBC "Dia 20 and smaller"; ACI 318M-14 "No. 19 and smaller". [smallBarLimitMm] is 20.0 for SBC
     * and 19.0 for ACI. psi_s = 0.8 for small bars, 1.0 otherwise. psi_t = 1.3 for more than 300 mm of fresh concrete
     * below the bar (top bars in a deep beam), else 1.0.
     */
    fun aciSbcTension(
        fcPrimeMpa: Double, fyMpa: Double, dbMm: Double,
        smallBarLimitMm: Double,
        spacingAndCoverMet: Boolean = false,
        topBarWithMoreThan300mmBelow: Boolean = false
    ): Result {
        require(fcPrimeMpa > 0 && fyMpa > 0 && dbMm > 0) { "fc', fy and db must be positive" }
        val small = dbMm <= smallBarLimitMm
        val k = when {
            spacingAndCoverMet && small -> 2.1
            spacingAndCoverMet -> 1.7
            small -> 1.4
            else -> 1.1
        }
        val psiT = if (topBarWithMoreThan300mmBelow) 1.3 else 1.0
        val psiE = 1.0
        val psiS = if (small) 0.8 else 1.0
        val sqrtFc = min(sqrt(fcPrimeMpa), 8.3)
        val formula = fyMpa * min(psiT * psiE, 1.7) * psiS / (k * 1.0 * sqrtFc) * dbMm
        val minimum = 300.0
        return Result(
            formulaLength = formula,
            length = max(formula, minimum),
            minimum = minimum,
            basis = "SBC 304-CR-2018 / ACI 318M-14 25.4.2: k=$k, psi_t=$psiT, psi_e=1.0, psi_s=$psiS, lambda=1; minimum 300 mm"
        )
    }

    /** SBC 304-CR-2018 Table 25.5.2.1 tension lap: Class A = 1.0 ld, Class B = 1.3 ld, both not less than 300 mm. */
    fun aciSbcTensionLap(ld: Double, classB: Boolean = true): Double =
        max(if (classB) 1.3 * ld else ld, 300.0)

    /**
     * SBC 304-CR-2018 25.4.3.1 standard hook, tension: the greatest of (0.24 fy psi_e psi_c psi_r /(lambda sqrt(fc'))) db,
     * 8 db and 150 mm, taking psi_e = psi_c = psi_r = lambda = 1.0 (uncoated, normalweight; psi_c and psi_r may
     * be taken as 1.0 per 25.4.3.2).
     */
    fun sbcHookTension(fcPrimeMpa: Double, fyMpa: Double, dbMm: Double): Double {
        require(fcPrimeMpa > 0 && fyMpa > 0 && dbMm > 0) { "fc', fy and db must be positive" }
        val sqrtFc = min(sqrt(fcPrimeMpa), 8.3)
        return maxOf(0.24 * fyMpa / sqrtFc * dbMm, 8.0 * dbMm, 150.0)
    }
}
