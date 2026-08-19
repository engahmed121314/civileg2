package com.civileg.app.utils

import kotlin.math.*

// ============================================================
// Enums
// ============================================================

enum class BearingMethod { TERZAGHI, MEYERHOF, HANSEN, VESIC }

enum class SoilType { CLAY, SAND, ROCK, MIXED }

// ============================================================
// Input / Output data classes
// ============================================================

data class SoilBearingInput(
    val method: BearingMethod = BearingMethod.TERZAGHI,
    val soilType: SoilType = SoilType.CLAY,
    val foundationWidth: Double = 1.5,       // m
    val foundationLength: Double = 1.5,      // m
    val foundationDepth: Double = 1.0,       // m
    val cohesion: Double = 25.0,             // kPa
    val frictionAngle: Double = 30.0,        // degrees
    val unitWeight: Double = 18.0,           // kN/m³
    val waterTableDepth: Double = 5.0,       // m below surface
    val eccentricityX: Double = 0.0,        // m
    val eccentricityY: Double = 0.0,        // m
    val loadInclinationX: Double = 0.0,     // degrees
    val loadInclinationY: Double = 0.0,     // degrees
    val safetyFactor: Double = 3.0
)

data class SoilBearingResult(
    val grossBearingCapacity: Double,        // kPa
    val netBearingCapacity: Double,          // kPa
    val allowableBearingCapacity: Double,    // kPa
    val nc: Double,
    val nq: Double,
    val ngamma: Double,
    val shapeFactorC: Double,
    val shapeFactorQ: Double,
    val shapeFactorGamma: Double,
    val depthFactorC: Double,
    val depthFactorQ: Double,
    val depthFactorGamma: Double,
    val inclinationFactorC: Double,
    val inclinationFactorQ: Double,
    val inclinationFactorGamma: Double,
    val effectiveWidth: Double,              // m (after eccentricity)
    val effectiveLength: Double,             // m
    val waterTableCorrection: Double,        // correction factor
    val settlement: Double,                  // mm (estimated)
    val isSafe: Boolean
)

// ============================================================
// N-factor table (pre-computed lookup for φ = 0..45 step 5)
// ============================================================

/**
 * Holds pre-computed bearing-capacity factors indexed by integer
 * degree values (0, 5, 10 … 45) for fast lookup.
 */
object NFactorTable {

    data class NFactors(val nc: Double, val nq: Double, val ngammaTerzaghi: Double)

    /**
     * Terzaghi's Nγ values are obtained from his published chart/table
     * and are not well-approximated by a single closed-form formula.
     */
    private val terzaghiTable: Map<Int, NFactors> = mapOf(
        0  to NFactors(5.14,  1.00,   0.0),
        5  to NFactors(6.49,  1.57,   0.5),
        10 to NFactors(8.35,  2.47,   1.2),
        15 to NFactors(10.90, 3.94,   2.5),
        20 to NFactors(14.83, 6.40,   5.0),
        25 to NFactors(20.72, 10.66,  9.7),
        28 to NFactors(25.80, 14.72,  13.6),
        30 to NFactors(30.14, 18.40,  15.7),
        32 to NFactors(35.49, 23.18,  18.1),
        34 to NFactors(41.99, 29.44,  21.0),
        35 to NFactors(46.12, 33.30,  22.5),
        36 to NFactors(48.06, 37.75,  24.4),
        38 to NFactors(61.35, 48.93,  29.5),
        40 to NFactors(75.31, 64.20,  37.7),
        42 to NFactors(93.71, 85.38,  48.9),
        43 to NFactors(105.11, 99.92,  56.3),
        45 to NFactors(133.88, 134.88, 73.4)
    )

    /**
     * Look up the nearest pre-computed row.  If [phi] is not exactly
     * on the table, linear interpolation between the two nearest rows
     * is performed for Terzaghi Nγ; the others are computed exactly.
     */
    fun lookup(phi: Double): Triple<Double, Double, Double> {
        val nq = SoilBearingCalculator.bearingNq(phi)
        val nc = SoilBearingCalculator.bearingNc(phi, nq)
        // Nγ – interpolate from table
        val keys = terzaghiTable.keys.sorted()
        val lower = keys.lastOrNull { it <= ceil(phi).toInt() } ?: 0
        val upper = keys.firstOrNull { it >= ceil(phi).toInt() } ?: 45
        val ngamma = if (lower == upper) {
            terzaghiTable[lower]!!.ngammaTerzaghi
        } else {
            val fL = terzaghiTable[lower]!!
            val fU = terzaghiTable[upper]!!
            val t = (ceil(phi).toInt() - lower).toDouble() / (upper - lower)
            fL.ngammaTerzaghi + t * (fU.ngammaTerzaghi - fL.ngammaTerzaghi)
        }
        return Triple(nc, nq, ngamma)
    }
}

// ============================================================
// Calculator
// ============================================================

/**
 * Comprehensive soil bearing-capacity calculator supporting four
 * classical methods: Terzaghi (1943), Meyerhof (1963), Hansen (1970),
 * and Vesic (1973/1975).
 *
 * General bearing-capacity equation:
 *
 *   qu = c·Nc·sc·dc·ic + q·Nq·sq·dq·iq + ½·γ·B·Nγ·sγ·dγ·iγ
 *
 * where q = γ·Df  (surcharge at foundation base).
 */
class SoilBearingCalculator {

    companion object {
        private const val DEG2RAD = PI / 180.0
        private const val SUBMERGED_UNIT_WEIGHT = 9.81  // kN/m³ (γ_sub ≈ γ_w)

        /** Nq = e^(π·tan φ) · tan²(π/4 + φ/2) */
        internal fun bearingNq(phiDeg: Double): Double {
            if (phiDeg == 0.0) return 1.0
            val phi = phiDeg * (PI / 180.0)
            return exp(PI * tan(phi)) * tan(PI / 4 + phi / 2).pow(2)
        }

        /** Nc = (Nq − 1) · cot φ  ;  Nc = 5.14 when φ = 0 */
        internal fun bearingNc(phiDeg: Double, nq: Double): Double {
            if (phiDeg == 0.0) return 5.14
            val phi = phiDeg * (PI / 180.0)
            return (nq - 1.0) * (1.0 / tan(phi))
        }
    }

    // ----------------------------------------------------------
    // Public entry points
    // ----------------------------------------------------------

    fun calculateTerzaghi(input: SoilBearingInput): SoilBearingResult =
        calculate(input, BearingMethod.TERZAGHI)

    fun calculateMeyerhof(input: SoilBearingInput): SoilBearingResult =
        calculate(input, BearingMethod.MEYERHOF)

    fun calculateHansen(input: SoilBearingInput): SoilBearingResult =
        calculate(input, BearingMethod.HANSEN)

    fun calculateVesic(input: SoilBearingInput): SoilBearingResult =
        calculate(input, BearingMethod.VESIC)

    // ----------------------------------------------------------
    // Core calculation
    // ----------------------------------------------------------

    private fun calculate(input: SoilBearingInput, method: BearingMethod): SoilBearingResult {
        val phiRad = input.frictionAngle * DEG2RAD

        // 1. Effective dimensions (one-way eccentricity reduction)
        val (Beff, Leff) = calculateEffectiveDimensions(
            input.foundationWidth, input.foundationLength,
            input.eccentricityX, input.eccentricityY
        )

        // 2. Bearing-capacity factors
        val (nc, nq, ngamma) = getBearingCapacityFactors(input.frictionAngle, method)

        // 3. Shape factors
        val (sc, sq, sg) = getShapeFactors(method, Beff, Leff, input.frictionAngle)

        // 4. Depth factors
        val (dc, dq, dg) = getDepthFactors(method, input.foundationDepth, Beff, input.frictionAngle)

        // 5. Inclination factors (resultant from two components)
        val (ic, iq, ig) = getInclinationFactors(
            method, input.frictionAngle,
            input.loadInclinationX, input.loadInclinationY
        )

        // 6. Water-table correction
        val rw = applyWaterTableCorrection(input, 0.0) // returns factor for γ
        val gammaEffective = input.unitWeight * rw

        // 7. Surcharge
        val q = gammaEffective * input.foundationDepth

        // 8. Gross ultimate bearing capacity
        val termC = input.cohesion * nc * sc * dc * ic
        val termQ = q * nq * sq * dq * iq
        val termG = 0.5 * gammaEffective * Beff * ngamma * sg * dg * ig
        val qu = termC + termQ + termG

        // 9. Net & allowable
        val qNet = qu - q  // subtract overburden pressure at base
        val qAllow = qNet / input.safetyFactor

        // 10. Settlement estimate
        val settlement = estimateSettlement(input, qNet)

        // 11. Safety check
        val isSafe = qAllow > 0

        return SoilBearingResult(
            grossBearingCapacity = qu,
            netBearingCapacity = qNet,
            allowableBearingCapacity = qAllow,
            nc = nc, nq = nq, ngamma = ngamma,
            shapeFactorC = sc, shapeFactorQ = sq, shapeFactorGamma = sg,
            depthFactorC = dc, depthFactorQ = dq, depthFactorGamma = dg,
            inclinationFactorC = ic, inclinationFactorQ = iq, inclinationFactorGamma = ig,
            effectiveWidth = Beff,
            effectiveLength = Leff,
            waterTableCorrection = rw,
            settlement = settlement,
            isSafe = isSafe
        )
    }

    // ----------------------------------------------------------
    // Bearing-capacity factors Nc, Nq, Nγ
    // ----------------------------------------------------------

    fun getBearingCapacityFactors(phiDeg: Double, method: BearingMethod): Triple<Double, Double, Double> {
        val nq = SoilBearingCalculator.bearingNq(phiDeg)
        val nc = SoilBearingCalculator.bearingNc(phiDeg, nq)
        val ngamma = when (method) {
            BearingMethod.TERZAGHI -> nGammaTerzaghi(phiDeg)
            BearingMethod.MEYERHOF -> nGammaMeyerhof(phiDeg, nq)
            BearingMethod.HANSEN  -> nGammaHansen(phiDeg, nq)
            BearingMethod.VESIC   -> nGammaVesic(phiDeg, nq)
        }
        return Triple(nc, nq, ngamma)
    }

    /** Nq = e^(π·tan φ) · tan²(π/4 + φ/2) */
    internal fun bearingNq(phiDeg: Double): Double {
        if (phiDeg == 0.0) return 1.0
        val phi = phiDeg * DEG2RAD
        return exp(PI * tan(phi)) * tan(PI / 4 + phi / 2).pow(2)
    }

    /** Nc = (Nq − 1) · cot φ  ;  Nc = 5.14 when φ = 0 */
    internal fun bearingNc(phiDeg: Double, nq: Double): Double {
        if (phiDeg == 0.0) return 5.14
        val phi = phiDeg * DEG2RAD
        return (nq - 1.0) * (1.0 / tan(phi))
    }

    // --- method-specific Nγ ---

    /** Terzaghi uses empirical chart values (interpolated from table). */
    private fun nGammaTerzaghi(phiDeg: Double): Double {
        val (nc, nq, ngamma) = NFactorTable.lookup(phiDeg)
        return ngamma
    }

    /** Meyerhof: Nγ = (Nq − 1) · tan(1.4φ) */
    internal fun nGammaMeyerhof(phiDeg: Double, nq: Double): Double {
        if (phiDeg == 0.0) return 0.0
        val phi = phiDeg * DEG2RAD
        return (nq - 1.0) * tan(1.4 * phi)
    }

    /** Hansen: Nγ = 1.5 · (Nq − 1) · tan φ */
    internal fun nGammaHansen(phiDeg: Double, nq: Double): Double {
        if (phiDeg == 0.0) return 0.0
        val phi = phiDeg * DEG2RAD
        return 1.5 * (nq - 1.0) * tan(phi)
    }

    /** Vesic: Nγ = 2 · (Nq + 1) · tan φ */
    internal fun nGammaVesic(phiDeg: Double, nq: Double): Double {
        if (phiDeg == 0.0) return 0.0
        val phi = phiDeg * DEG2RAD
        return 2.0 * (nq + 1.0) * tan(phi)
    }

    // ----------------------------------------------------------
    // Shape factors
    // ----------------------------------------------------------

    fun getShapeFactors(
        method: BearingMethod,
        B: Double, L: Double,
        phiDeg: Double
    ): Triple<Double, Double, Double> {
        val ratio = if (L > 0) B / L else 1.0
        val phi = phiDeg * DEG2RAD
        return when (method) {
            BearingMethod.TERZAGHI -> {
                // Terzaghi (1943) shape factors for square/circular/strip
                // For rectangular: use interpolation via 1 + 0.3·B/L
                val sc = if (phiDeg == 0.0) 1.0 else 1.0 + 0.3 * ratio
                val sq = 1.0
                val sg = if (phiDeg == 0.0) 1.0 else 1.0 + 0.2 * ratio
                Triple(sc, sq, sg)
            }
            BearingMethod.MEYERHOF -> {
                // Meyerhof shape factors
                val sc = 1.0 + 0.2 * ratio
                val sq = 1.0 + 0.2 * ratio
                val sg = 1.0 + 0.1 * ratio
                Triple(sc, sq, sg)
            }
            BearingMethod.HANSEN -> {
                // Hansen (1970) shape factors
                val sc = 1.0 + (ratio) * (nqVal(phiDeg) / ncVal(phiDeg))
                val sq = 1.0 + ratio * tan(phi)
                val sg = 1.0 - 0.4 * ratio
                Triple(sc, sq, sg)
            }
            BearingMethod.VESIC -> {
                // Vesic shape factors (same form as Hansen)
                val sc = 1.0 + ratio * (nqVal(phiDeg) / ncVal(phiDeg))
                val sq = 1.0 + ratio * tan(phi)
                val sg = 1.0 - 0.4 * ratio
                Triple(sc, sq, sg)
            }
        }
    }

    // ----------------------------------------------------------
    // Depth factors
    // ----------------------------------------------------------

    fun getDepthFactors(
        method: BearingMethod,
        Df: Double, B: Double,
        phiDeg: Double
    ): Triple<Double, Double, Double> {
        val phi = phiDeg * DEG2RAD
        val dB = if (B > 0) Df / B else 0.0
        val k = if (phiDeg > 0) tan(phi) else 0.0

        return when (method) {
            BearingMethod.TERZAGHI -> {
                // Terzaghi original does not have explicit depth factors;
                // surcharge q = γ·Df already accounts for depth.
                // We use dc = dq = dγ = 1.0
                Triple(1.0, 1.0, 1.0)
            }
            BearingMethod.MEYERHOF -> {
                // Meyerhof depth factors
                val dLimit = tan(PI / 4 + phi / 2).pow(2)
                val dRatio = minOf(dB, dLimit)
                val dq = 1.0 + 0.2 * dRatio
                val dc = 1.0 + 0.2 * dRatio
                val dg = 1.0 + 0.1 * dRatio
                Triple(dc, dq, dg)
            }
            BearingMethod.HANSEN -> {
                // Hansen depth factors
                val cap = if (phiDeg > 0) minOf(dB, 1.0) else 0.0
                val dq = 1.0 + 2.0 * tan(phi) * (1.0 - sin(phi)).pow(2) * cap
                val dc = if (phiDeg > 0) dq - (1.0 - dq) / (nqVal(phiDeg) * tan(phi)) else 1.0
                val dg = 1.0
                Triple(dc, dq, dg)
            }
            BearingMethod.VESIC -> {
                // Vesic depth factors
                val cap = if (phiDeg > 0) minOf(dB, 1.0) else 0.0
                val dq = 1.0 + 2.0 * tan(phi) * (1.0 - sin(phi)).pow(2) * cap
                val dc = if (phiDeg > 0) dq - (1.0 - dq) / (nqVal(phiDeg) * tan(phi)) else 1.0
                val dg = 1.0
                Triple(dc, dq, dg)
            }
        }
    }

    // ----------------------------------------------------------
    // Inclination factors
    // ----------------------------------------------------------

    fun getInclinationFactors(
        method: BearingMethod,
        phiDeg: Double,
        alphaXDeg: Double,
        alphaYDeg: Double
    ): Triple<Double, Double, Double> {
        // Resultant inclination from two components
        val alphaRad = sqrt(alphaXDeg.pow(2) + alphaYDeg.pow(2)) * DEG2RAD

        return when (method) {
            BearingMethod.TERZAGHI -> {
                // Terzaghi does not provide explicit inclination factors.
                // Use a simplified reduction.
                val factor = cos(alphaRad).coerceAtLeast(0.1)
                Triple(factor, factor, factor)
            }
            BearingMethod.MEYERHOF -> {
                // Meyerhof inclination factors
                val phi = phiDeg * DEG2RAD
                val iq = (1.0 - alphaRad / (PI / 2.0)).pow(2)
                val ic = iq
                val ig = (1.0 - alphaRad / (phi + PI / 4.0)).pow(2).coerceAtLeast(0.0)
                Triple(ic, iq, ig)
            }
            BearingMethod.HANSEN -> {
                // Hansen inclination factors
                val phi = phiDeg * DEG2RAD
                val eta = alphaRad
                val iq = (1.0 - 0.5 * tan(eta)).pow(5)
                val ic = iq - (1.0 - iq) / (nqVal(phiDeg) * tan(phi)).coerceAtLeast(0.0)
                val ig = (1.0 - 0.5 * tan(eta)).pow(5)
                Triple(ic.coerceAtLeast(0.1), iq.coerceAtLeast(0.1), ig.coerceAtLeast(0.1))
            }
            BearingMethod.VESIC -> {
                // Vesic inclination factors
                val phi = phiDeg * DEG2RAD
                val m = 2.0 + (1.0 + phi / (PI / 2.0)) // Vesic's m parameter
                val H = sin(alphaRad)
                val V = cos(alphaRad)
                val iq = (1.0 - H / (V + m * V)).pow(m)
                val ic = iq - (1.0 - iq) / (nqVal(phiDeg) * tan(phi).let { if (it == 0.0) 1e-10 else it })
                val ig = (1.0 - H / (V + m * V)).pow(m + 1)
                Triple(ic.coerceAtLeast(0.1), iq.coerceAtLeast(0.1), ig.coerceAtLeast(0.1))
            }
        }
    }

    // ----------------------------------------------------------
    // Water-table correction
    // ----------------------------------------------------------

    /**
     * Returns a correction factor [rw] (0..1) to multiply by γ.
     *
     * Three cases:
     *   - Water table at or above base (Dw ≤ Df): rw = 0.5
     *   - Water table between base and B below base: rw = 0.5 + 0.5·(Dw−Df)/B
     *   - Water table deeper than B below base: rw = 1.0
     */
    fun applyWaterTableCorrection(input: SoilBearingInput, qu: Double): Double {
        val Dw = input.waterTableDepth  // depth below ground surface
        val Df = input.foundationDepth
        val B  = input.foundationWidth

        return when {
            Dw <= Df -> 0.5
            Dw <= Df + B -> 0.5 + 0.5 * (Dw - Df) / B
            else -> 1.0
        }
    }

    // ----------------------------------------------------------
    // Effective dimensions
    // ----------------------------------------------------------

    /**
     * One-way eccentricity reduction (Meyerhof's approach):
     *   B' = B − 2·ex
     *   L' = L − 2·ey
     */
    fun calculateEffectiveDimensions(
        B: Double, L: Double,
        ex: Double, ey: Double
    ): Pair<Double, Double> {
        val Beff = (B - 2.0 * ex).coerceAtLeast(0.1)
        val Leff = (L - 2.0 * ey).coerceAtLeast(0.1)
        return Pair(Beff, Leff)
    }

    // ----------------------------------------------------------
    // Settlement estimation (simplified Schmertmann / Hough)
    // ----------------------------------------------------------

    /**
     * Very simplified settlement estimation in mm.
     * Uses a correlation with net bearing pressure and soil type.
     *
     *   S = (q_net / Es) · influence_depth
     *
     * Es is estimated from soil type; influence depth ≈ 2B.
     */
    fun estimateSettlement(input: SoilBearingInput, qNet: Double): Double {
        if (qNet <= 0) return 0.0

        // Estimated Young's modulus (kPa) by soil type
        val Es = when (input.soilType) {
            SoilType.CLAY  -> 5000.0 + 150.0 * input.cohesion       // ~5–20 MPa
            SoilType.SAND  -> 10000.0 + 500.0 * input.frictionAngle // ~10–30 MPa
            SoilType.ROCK  -> 50000.0                                // 50 MPa
            SoilType.MIXED -> 7500.0 + 200.0 * input.cohesion
        }

        val influenceDepth = 2.0 * input.foundationWidth * 1000.0 // mm
        val settlement = (qNet / Es) * influenceDepth
        return settlement.coerceAtLeast(0.0)
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private fun ncVal(phiDeg: Double): Double {
        val nq = bearingNq(phiDeg)
        return bearingNc(phiDeg, nq)
    }

    private fun nqVal(phiDeg: Double): Double = bearingNq(phiDeg)

    /** Compare all four methods side-by-side and return a list of results. */
    fun compareAllMethods(input: SoilBearingInput): Map<BearingMethod, SoilBearingResult> {
        return BearingMethod.entries.associateWith { method ->
            val modifiedInput = input.copy(method = method)
            calculate(modifiedInput, method)
        }
    }
}
