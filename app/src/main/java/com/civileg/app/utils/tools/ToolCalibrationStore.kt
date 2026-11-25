package com.civileg.app.utils.tools

/** Minimal key-value surface so the persistence rules can be unit-tested without Android. */
interface FloatPrefs {
    fun getFloat(key: String): Float?
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
}

/**
 * Stores the ruler calibration and the spirit-level zero offsets.
 * A ruler calibration is only valid for the display density it was made at: it is saved together with that
 * density and ignored if the reported density changes (different display size or density setting).
 */
class ToolCalibrationStore(private val prefs: FloatPrefs) {

    data class RulerCalibration(val factor: Double, val dpiAtCalibration: Float)
    data class LevelZero(val xDeg: Double, val yDeg: Double)

    fun saveRuler(factor: Double, dpi: Float) {
        require(factor.isFinite() && factor > 0.0) { "factor must be positive" }
        prefs.putFloat(K_RULER_FACTOR, factor.toFloat())
        prefs.putFloat(K_RULER_DPI, dpi)
    }

    /** The saved factor, or null (uncalibrated) if none, invalid, or made at a different density than [currentDpi]. */
    fun rulerFactorFor(currentDpi: Float): Double? {
        val f = RulerMath.validStoredFactor(prefs.getFloat(K_RULER_FACTOR) ?: return null) ?: return null
        val saved = prefs.getFloat(K_RULER_DPI) ?: return null
        return if (kotlin.math.abs(saved - currentDpi) <= DPI_EPSILON) f else null
    }

    fun clearRuler() { prefs.remove(K_RULER_FACTOR); prefs.remove(K_RULER_DPI) }

    fun saveLevelZero(xDeg: Double, yDeg: Double) {
        prefs.putFloat(K_ZERO_X, xDeg.toFloat()); prefs.putFloat(K_ZERO_Y, yDeg.toFloat())
    }

    fun levelZero(): LevelZero? {
        val x = prefs.getFloat(K_ZERO_X) ?: return null
        val y = prefs.getFloat(K_ZERO_Y) ?: return null
        return if (x.isFinite() && y.isFinite()) LevelZero(x.toDouble(), y.toDouble()) else null
    }

    fun clearLevelZero() { prefs.remove(K_ZERO_X); prefs.remove(K_ZERO_Y) }

    companion object {
        const val K_RULER_FACTOR = "ruler_factor"
        const val K_RULER_DPI = "ruler_dpi"
        const val K_ZERO_X = "level_zero_x"
        const val K_ZERO_Y = "level_zero_y"
        /** Float round-trip tolerance only, not an engineering limit. */
        const val DPI_EPSILON = 0.01f
    }
}
