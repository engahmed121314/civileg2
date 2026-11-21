package com.civileg.app.domain.entities

import kotlin.math.max

/**
 * Standard Bar Bending Schedule shape definitions, parameters, and cut-length formulas.
 */
enum class BbsShapeCode(val code: Int, val titleEn: String, val titleAr: String) {
    STRAIGHT(0, "Straight Bar", "سيخ مستقيم"),
    L_BEND(11, "L-Bend Bar", "سيخ بركبة (L)"),
    U_BEND(21, "U-Bend Bar / Hairpin", "سيخ U / شوكة"),
    STIRRUP_RECT(51, "Rectangular Link / Stirrup", "كانة مستطيلة مغلقة"),
    STIRRUP_135(67, "Confined Link with 135° Hooks", "كانة تكثيف بجميع الخطافات 135°"),
    TIE_CROSS(77, "Cross Tie / S-Hook", "كانة حابسة / كانة فردية"),
    CUSTOM(99, "Custom Bending Shape", "شكل خاص");

    companion object {
        /**
         * Resolves BbsShapeCode from integer code.
         */
        fun fromCode(code: Int): BbsShapeCode {
            return entries.find { it.code == code } ?: CUSTOM
        }
    }
}

/**
 * Dimensions parameter map for shape sketches (A, B, C, D, E in mm, R radius in mm).
 */
data class BbsShapeParams(
    val a: Double = 0.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 0.0,
    val e: Double = 0.0,
    val radiusR: Double = 0.0
) {
    /**
     * Computes exact cut length L_c in mm based on shape code and dimensions,
     * accounting for hook allowances and bend deductions.
     */
    fun calculateCutLength(shapeCode: BbsShapeCode, diameterMm: Int): Double {
        val dia = diameterMm.toDouble()
        return when (shapeCode) {
            BbsShapeCode.STRAIGHT -> a
            BbsShapeCode.L_BEND -> {
                val len = a + b
                if (c > 0) len + c else len
            }
            BbsShapeCode.U_BEND -> {
                a + 2 * b
            }
            BbsShapeCode.STIRRUP_RECT -> {
                val hookLen = max(10.0 * dia, 100.0)
                2 * (a + b) + 2 * hookLen
            }
            BbsShapeCode.STIRRUP_135 -> {
                val hookLen = max(12.0 * dia, 100.0)
                2 * (a + b) + 2 * hookLen
            }
            BbsShapeCode.TIE_CROSS -> {
                val hookLen = max(10.0 * dia, 75.0)
                a + 2 * hookLen
            }
            BbsShapeCode.CUSTOM -> {
                val sum = a + b + c + d + e
                if (sum > 0) sum else a
            }
        }
    }
}
