package com.civileg.app.ui.compose.screens

/**
 * Required-demand check for the steel connection design button.
 *
 * The screen used to turn a blank or invalid applied force into 100 kN (weld) or 0 kN (bolt) and then
 * show a SAFE or UNSAFE verdict for a load the user never entered. A cleared field is now an error.
 * A field the user left at its pre-filled value is still a typed-in number and is accepted.
 */
internal object ConnectionDemandInput {
    private fun parse(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    /** Returns an error message, or null when every demand that applies is a valid number. */
    fun error(
        boltActive: Boolean,
        tensionShown: Boolean,
        weldActive: Boolean,
        shear: String,
        tension: String,
        weldForce: String
    ): String? {
        if (boltActive) {
            val v = parse(shear) ?: return "Enter the applied shear Vu (a number, 0 or more) before designing the bolted connection."
            val t = if (tensionShown) {
                parse(tension) ?: return "Enter the applied tension Tu (a number, 0 or more) before designing the bolted connection."
            } else 0.0
            if (v + t <= 0.0) return "The applied bolt load is zero, so there is nothing to check. Enter Vu or Tu above zero."
        }
        if (weldActive) {
            val f = parse(weldForce)
            if (f == null || f <= 0.0) return "Enter the applied weld force (a number above zero) before designing the weld."
        }
        return null
    }
}
