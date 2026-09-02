package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorDetailingV4
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.round

/**
 * §26 — BAR BENDING ENGINE.
 * Real cutting-length mathematics per IS 2502 / BS 8666 practice
 * (ADR-010 verified 2026-08-26: bend deduction 45°=1d, 90°=2d, 135°=3d,
 * 180°=4d; hook addition 90°=10d(min75), 135°=12d, 180°=9d;
 * unit mass = d²/162 kg/m).
 *
 * All inputs/outputs in millimetres. No invented geometry — every dimension
 * comes from the caller (engine result or detailing model).
 */
object BarBendingEngine {

    private fun deductionPerBend(angleDeg: Double, diaMm: Double): Double = when {
        angleDeg <= 45.0 + 1e-9 -> 1.0 * diaMm
        angleDeg <= 90.0 + 1e-9 -> 2.0 * diaMm
        angleDeg <= 135.0 + 1e-9 -> 3.0 * diaMm
        else -> 4.0 * diaMm
    }

    fun hookAddition(angleDeg: Double, diaMm: Double): Double = when {
        angleDeg <= 90.0 + 1e-9 -> maxOf(10.0 * diaMm, 75.0)
        angleDeg <= 135.0 + 1e-9 -> 12.0 * diaMm
        else -> 9.0 * diaMm
    }

    fun unitMassKgPerM(diaMm: Int): Double {
        require(diaMm > 0) { "unitMassKgPerM: diameter must be > 0" }
        return (diaMm.toDouble() * diaMm.toDouble()) / 162.0
    }

    /** Straight bar (shape 00). */
    fun straightCutLength(lengthMm: Double): Double {
        require(lengthMm > 0) { "straightCutLength: length must be > 0" }
        return round(lengthMm)
    }

    /**
     * Rectangular stirrup / tie (shapes 11 & 22).
     * @param outWidthMm/outHeightMm OUTER concrete dims; inner bend lines are
     *        derived as (out − 2·cover − Ø).
     */
    fun stirrupCutLength(
        outWidthMm: Double,
        outHeightMm: Double,
        coverMm: Double,
        diaMm: Int,
        seismicHook135: Boolean = true
    ): Double {
        require(outWidthMm > 0 && outHeightMm > 0 && coverMm >= 0) {
            "stirrupCutLength: non-positive geometry"
        }
        val a = outWidthMm - 2 * coverMm - diaMm
        val b = outHeightMm - 2 * coverMm - diaMm
        require(a > 0 && b > 0) { "stirrupCutLength: non-positive inner dims a=$a b=$b — check cover/thickness" }
        val perimeter = 2 * (a + b)
        val cornerDeduction = 3 * deductionPerBend(90.0, diaMm.toDouble())   // 3×90° corners
        val hook = if (seismicHook135) 2.0 * hookAddition(135.0, diaMm.toDouble())
                   else 2.0 * hookAddition(90.0, diaMm.toDouble())
        return round(perimeter - cornerDeduction + hook)
    }

    /** L-bar (shape 31): one 90° bend. */
    fun lBarCutLength(leg1Mm: Double, leg2Mm: Double, diaMm: Int): Double {
        require(leg1Mm > 0 && leg2Mm > 0) { "lBarCutLength: legs must be > 0" }
        return round(leg1Mm + leg2Mm - deductionPerBend(90.0, diaMm.toDouble()))
    }

    /** U-bar (shape 21): base + two legs, two bends (90° each by default). */
    fun uBarCutLength(baseMm: Double, legMm: Double, diaMm: Int): Double {
        require(baseMm > 0 && legMm > 0) { "uBarCutLength: dims must be > 0" }
        val ded = 2 * deductionPerBend(90.0, diaMm.toDouble())
        return round(legMm * 2 + baseMm - ded)
    }

    /** Cranked bar (shape 12): span bar with two 45° cranks of height h. */
    fun crankCutLength(spanMm: Double, crankHeightMm: Double, diaMm: Int): Double {
        require(spanMm > 0 && crankHeightMm >= 0) { "crankCutLength: invalid input" }
        val crankAdd = 0.42 * crankHeightMm * 2      // two 45° inclined transitions
        val ded = 2 * deductionPerBend(45.0, diaMm.toDouble())
        return round(spanMm + crankAdd - ded)
    }

    /** Generic segment-list cut length (matches CalculatorDetailingV4.Segment). */
    fun fromSegments(segments: List<CalculatorDetailingV4.Segment>, diaMm: Int): Double {
        require(segments.isNotEmpty()) { "fromSegments: empty segment list" }
        var len = segments.sumOf { it.length }
        for (s in segments) len -= deductionPerBend(s.angleDeg, diaMm.toDouble())
        return round(len.coerceAtLeast(0.0))
    }

    /** Ties count along a zone (inclusive). */
    fun tieCount(startMm: Double, endMm: Double, spacingMm: Double): Int {
        require(spacingMm > 0) { "tieCount: spacing must be > 0" }
        require(endMm >= startMm) { "tieCount: inverted zone" }
        return ceil((endMm - startMm) / spacingMm).toInt() + 1
    }

    fun totalWeightKg(cutLengthMm: Double, diaMm: Int, qty: Int): Double {
        require(qty > 0) { "totalWeightKg: qty must be > 0" }
        return cutLengthMm / 1000.0 * unitMassKgPerM(diaMm) * qty
    }

    /** Map V4 BarShape → ISO shape code label used on BBS rows. */
    fun isoCodeOf(shape: CalculatorDetailingV4.BarShape): IsoShapeCode = when (shape) {
        CalculatorDetailingV4.BarShape.STRAIGHT -> IsoShapeCode.S00
        CalculatorDetailingV4.BarShape.L -> IsoShapeCode.S31
        CalculatorDetailingV4.BarShape.U -> IsoShapeCode.S21
        CalculatorDetailingV4.BarShape.C -> IsoShapeCode.S41
        CalculatorDetailingV4.BarShape.STIRRUP_90 -> IsoShapeCode.S11
        CalculatorDetailingV4.BarShape.STIRRUP_135, CalculatorDetailingV4.BarShape.CROSSTIE_135 -> IsoShapeCode.S22
        CalculatorDetailingV4.BarShape.HOOP -> IsoShapeCode.S11
        CalculatorDetailingV4.BarShape.CUSTOM -> IsoShapeCode.S00
    }

    @Suppress("unused") private val keepPi = PI // silence unused-import lint in narrow builds
}
