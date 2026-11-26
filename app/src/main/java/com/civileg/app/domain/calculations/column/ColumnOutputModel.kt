package com.civileg.app.domain.calculations.column

import com.civileg.app.domain.calculations.utils.CalculatorEngine.ColumnResult
import com.civileg.app.domain.entities.DesignCode
import java.util.Locale

/**
 * Cover used by the column engine for its tie perimeter and bar positions, and shown by every
 * drawing (screen, PDF image, DXF). One source so the sheets cannot disagree with the calculation.
 * Values are the engine's existing constants (SBC 50 mm, otherwise 40 mm). Their clause source has
 * NOT been verified against the supplied editions: they are engine constants, not citations.
 */
object ColumnCover {
    fun mm(code: DesignCode): Double = if (code == DesignCode.SBC) 50.0 else 40.0
}

/** Containment rule for the column verdict: a failed check outranks incompleteness. */
enum class ColumnStatus(val label: String) {
    FAIL("FAIL"),
    NOT_ASSESSED("NOT ASSESSED"),
    PRELIMINARY("PRELIMINARY - UNVERIFIED"),
    SAFE("SAFE");

    companion object {
        fun of(r: ColumnResult): ColumnStatus {
            // The slenderness row is informational (a slender column is magnified, not failed).
            val failed = r.safetyChecks.any { !it.isSafe && !it.notAssessed && it.name != "Slenderness" }
            return when {
                failed -> FAIL
                !r.axialAssessed || r.safetyChecks.any { it.notAssessed } -> NOT_ASSESSED
                !r.isSafe -> FAIL
                !r.assessmentComplete -> PRELIMINARY
                else -> SAFE
            }
        }
    }
}

/**
 * The values every column output prints, built once from the solved result. The screen, the PDF
 * and the drawing/BOQ read this object, so one input gives one result everywhere.
 */
data class ColumnOutputModel(
    val status: ColumnStatus,
    val axialCapacityText: String,
    val appliedPuText: String,
    val reinforcementText: String,
    val stirrupsText: String,
    val coverMm: Double,
    val concreteVolumeText: String,
    val steelWeightText: String,
    val bbsStatusText: String
) {
    companion object {
        private fun f(fmt: String, v: Double) = String.format(Locale.US, fmt, v)

        fun from(r: ColumnResult): ColumnOutputModel = ColumnOutputModel(
            status = ColumnStatus.of(r),
            axialCapacityText = r.axialCapacityLabel,
            appliedPuText = f("%.1f", r.appliedAxial) + " kN",
            reinforcementText = r.reinforcement.barString,
            stirrupsText = r.stirrups.description,
            coverMm = ColumnCover.mm(r.code),
            concreteVolumeText = f("%.3f", r.concreteVolume) + " m3",
            steelWeightText = f("%.1f", r.steelWeight) + " kg",
            bbsStatusText = bbsStatus(r)
        )

        /** Net steel only. Lap, hook and tie-hook allowances have no verified clause source: no schedule is issued. */
        fun bbsStatus(r: ColumnResult): String =
            "Bar bending schedule NOT ISSUED: lap, hook and tie-hook allowances have no verified basis in the supplied editions. " +
                "Steel quantity " + f("%.1f", r.steelWeight) + " kg is net (main bars and ties over the clear height, no laps or hooks)."
    }
}
