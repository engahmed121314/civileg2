package com.civileg.app.ui.compose.screens

import androidx.annotation.StringRes
import com.civileg.app.R

/** Which required connection input is missing or invalid. The screen maps each one to a localized string. */
internal enum class ConnectionInputProblem {
    BOLT_SHEAR, BOLT_TENSION, BOLT_ZERO_LOAD, WELD_FORCE,
    BOLT_DIAMETER, BOLT_COUNT, PLATE_THICKNESS, FU_PLATE, WELD_SIZE, WELD_LENGTH
}

@StringRes
internal fun ConnectionInputProblem.messageRes(): Int = when (this) {
    ConnectionInputProblem.BOLT_SHEAR -> R.string.steel_err_bolt_shear
    ConnectionInputProblem.BOLT_TENSION -> R.string.steel_err_bolt_tension
    ConnectionInputProblem.BOLT_ZERO_LOAD -> R.string.steel_err_bolt_zero_load
    ConnectionInputProblem.WELD_FORCE -> R.string.steel_err_weld_force
    ConnectionInputProblem.BOLT_DIAMETER -> R.string.steel_err_bolt_diameter
    ConnectionInputProblem.BOLT_COUNT -> R.string.steel_err_bolt_count
    ConnectionInputProblem.PLATE_THICKNESS -> R.string.steel_err_plate_thickness
    ConnectionInputProblem.FU_PLATE -> R.string.steel_err_fu_plate
    ConnectionInputProblem.WELD_SIZE -> R.string.steel_err_weld_size
    ConnectionInputProblem.WELD_LENGTH -> R.string.steel_err_weld_length
}

/**
 * Required-input check for the steel connection design button.
 *
 * The screen used to turn a blank or invalid applied force into 100 kN (weld) or 0 kN (bolt), and a
 * blank geometry field into a default size, then show a verdict for a connection the user never
 * described. A cleared field is now an error. A field left at its pre-filled value is still a number
 * the user can see and is accepted. Only fields that are on screen for the chosen connection type are
 * required, and the only rule on geometry is that it is a real positive number: no size ranges are
 * invented here.
 */
internal object ConnectionDemandInput {
    private fun number(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun nonNegative(raw: String): Double? = number(raw)?.takeIf { it >= 0.0 }
    private fun positive(raw: String): Double? = number(raw)?.takeIf { it > 0.0 }

    /** Returns the first problem found, or null when every input that applies is valid. */
    fun problem(
        boltActive: Boolean,
        boltGeometryShown: Boolean,
        tensionShown: Boolean,
        weldActive: Boolean,
        shear: String,
        tension: String,
        weldForce: String,
        boltDiameter: String = "1",
        boltCount: String = "1",
        plateThickness: String = "1",
        fuPlate: String = "1",
        weldSize: String = "1",
        weldLength: String = "1"
    ): ConnectionInputProblem? {
        if (boltActive) {
            val v = nonNegative(shear) ?: return ConnectionInputProblem.BOLT_SHEAR
            val t = if (tensionShown) nonNegative(tension) ?: return ConnectionInputProblem.BOLT_TENSION else 0.0
            if (v + t <= 0.0) return ConnectionInputProblem.BOLT_ZERO_LOAD
            if (boltGeometryShown) {
                if (positive(boltDiameter) == null) return ConnectionInputProblem.BOLT_DIAMETER
                val n = boltCount.trim().toIntOrNull()
                if (n == null || n < 1) return ConnectionInputProblem.BOLT_COUNT
                if (positive(plateThickness) == null) return ConnectionInputProblem.PLATE_THICKNESS
                if (positive(fuPlate) == null) return ConnectionInputProblem.FU_PLATE
            }
        }
        if (weldActive) {
            if (positive(weldForce) == null) return ConnectionInputProblem.WELD_FORCE
            if (positive(weldSize) == null) return ConnectionInputProblem.WELD_SIZE
            if (positive(weldLength) == null) return ConnectionInputProblem.WELD_LENGTH
        }
        return null
    }
}
