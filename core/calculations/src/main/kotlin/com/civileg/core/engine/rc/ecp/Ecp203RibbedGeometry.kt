package com.civileg.core.engine.rc.ecp

import kotlin.math.max

/** ECP203-2020 one-way ribbed geometry, printed6-15/16, Fig6-4. Not a slab analysis. */
object Ecp203RibbedGeometry {
    data class Assessment(val clearRibGapMm: Double, val minimumRibWidthMm: Double,
        val minimumToppingMm: Double, val geometryChecksPass: Boolean)
    enum class CrossRibs { NONE, ONE_AT_MIDSPAN, THREE }

    fun assess(webWidthMm: Double, centerSpacingMm: Double, totalDepthMm: Double,
        toppingMm: Double): Assessment {
        require(listOf(webWidthMm, centerSpacingMm, totalDepthMm, toppingMm)
            .all { it.isFinite() && it > 0.0 }) { "Ribbed geometry must be finite and positive." }
        val gap = centerSpacingMm - webWidthMm
        val minimumWeb = max(100.0, totalDepthMm / 3.0)
        val minimumTopping = max(50.0, gap / 10.0)
        return Assessment(gap, minimumWeb, minimumTopping,
            gap > 0.0 && gap <= 700.0 && webWidthMm >= minimumWeb &&
                toppingMm >= minimumTopping && toppingMm < totalDepthMm)
    }

    /** Characteristic live load only, in kN/m2, not total/factored load. Printed6-16. */
    fun requiredOneWayCrossRibs(spanMm: Double, characteristicLiveLoadKnM2: Double): CrossRibs {
        require(spanMm.isFinite() && spanMm > 0.0)
        require(characteristicLiveLoadKnM2.isFinite() && characteristicLiveLoadKnM2 >= 0.0)
        return if (characteristicLiveLoadKnM2 <= 3.0) {
            if (spanMm > 5000.0) CrossRibs.ONE_AT_MIDSPAN else CrossRibs.NONE
        } else when {
            spanMm > 7000.0 -> CrossRibs.THREE
            spanMm >= 4000.0 -> CrossRibs.ONE_AT_MIDSPAN
            else -> CrossRibs.NONE
        }
    }
}
