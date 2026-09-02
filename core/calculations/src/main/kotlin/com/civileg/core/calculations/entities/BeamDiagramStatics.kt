package com.civileg.core.calculations.entities

import kotlin.math.abs
import kotlin.math.max

/**
 * R1: single source of truth for the dimensionless beam statics used to shape
 * the Bending Moment / Shear Force diagrams across every output channel
 * (screen ProfessionalBeamDrawing, PDF generator, DXF beam elevation).
 *
 * These functions return NORMALIZED curve ordinates (t ∈ [0,1] across the span);
 * they reproduce the classic per-case shape only (SS parabola, cantilever
 * hogging parabola, propped cubic, fixed-fixed S-curve, and the linear shear
 * ramps). The ENGINE's design envelope (appliedMoment / appliedShear) is scaled
 * at the drawing layer so the curve PEAK equals the engine value exactly — no
 * strength value is recomputed here (Pillar-2 / P016). [equivalentUdl] is a
 * label-only engineering back-calculation of an equivalent uniform load.
 */
object BeamDiagramStatics {

    /**
     * Dimensionless bending-moment ordinate for a case at span fraction [t].
     * Sign convention: positive = sagging (drawn on the tension/below side).
     */
    fun normalizedMoment(supportTypeName: String, t: Double): Double = when (supportTypeName) {
        "CANTILEVER" -> -(1.0 - t) * (1.0 - t)
        "FIXED_FIXED" -> t * (1.0 - t) - 1.0 / 6.0
        "FIXED_HINGED" -> (5.0 / 8.0) * t - t * t / 2.0 - 1.0 / 8.0
        else -> t * (1.0 - t) // SS (HINGED_HINGED / ROLLER_HINGED)
    }

    /**
     * Dimensionless shear ordinate for a case at span fraction [t].
     * Sign convention: positive shear drawn above the axis.
     */
    fun normalizedShear(supportTypeName: String, t: Double): Double = when (supportTypeName) {
        "CANTILEVER" -> -(1.0 - t)
        "FIXED_HINGED" -> 5.0 / 8.0 - t // propped reaction R_A = 5wL/8
        else -> 0.5 - t // SS, FIXED_FIXED, ROLLER_HINGED
    }

    /**
     * Max absolute dimensionless ordinate over a dense sampling of the span —
     * the factor used to normalize drawing scale so the engine peak is exact.
     */
    fun maxAbsMoment(supportTypeName: String): Double = maxAbs(supportTypeName, ::normalizedMoment)

    /** @see [maxAbsMoment] */
    fun maxAbsShear(supportTypeName: String): Double = maxAbs(supportTypeName, ::normalizedShear)

    /**
     * Equivalent UDL (kN/m) whose elastic extreme reproduces the engine's
     * design moment envelope for the case (c = 2/12/8 — matching
     * CalculatorEngine.designBeam momentFactor). LABEL-ONLY annotation.
     */
    fun equivalentUdl(supportTypeName: String, maxMomentKnM: Double, spanM: Double): Double {
        val l2 = spanM * spanM
        return if (l2 > 0.0) when (supportTypeName) {
            "CANTILEVER" -> 2.0 * maxMomentKnM / l2
            "FIXED_FIXED" -> 12.0 * maxMomentKnM / l2
            else -> 8.0 * maxMomentKnM / l2
        } else 0.0
    }

    private fun maxAbs(
        supportTypeName: String,
        fn: (String, Double) -> Double
    ): Double {
        var peak = 0.0
        for (i in 0..64) {
            peak = max(peak, abs(fn(supportTypeName, i.toDouble() / 64.0)))
        }
        return peak
    }
}