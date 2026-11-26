package com.civileg.app.domain.calculations.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.ceil
import kotlin.math.sqrt

/** Net flight steel only. These are not cutting lengths or a fabrication BBS. */
@Parcelize
data class StairNetBarSet(
    val diameterMm: Int,
    val count: Int,
    val netFlightLengthMm: Double,
    val totalNetWeightKg: Double
) : Parcelable

@Parcelize
data class StairQuantityReview(
    val main: StairNetBarSet,
    val distribution: StairNetBarSet,
    val coverMm: Double,
    val netFlightSteelKg: Double,
    val finalSteelKg: Double? = null,
    val fabricationScheduleAvailable: Boolean = false,
    val qualifications: List<String> = listOf(
        "Net flight bar segments only, not cutting lengths or procurement quantities",
        "Support anchorage, bends, laps, top steel and landing steel are not quantified",
        "Bar count closes both covered edge lines with actual spacing no greater than the design spacing"
    )
) : Parcelable

object StairQuantityReviewer {
    fun review(widthMm: Double, runMm: Double, riseMm: Double, coverMm: Double,
               mainDiameterMm: Int, mainSpacingMm: Double,
               distributionDiameterMm: Int, distributionSpacingMm: Double): StairQuantityReview {
        require(listOf(widthMm, runMm, riseMm, coverMm, mainSpacingMm, distributionSpacingMm)
            .all { it.isFinite() && it > 0.0 })
        require(mainDiameterMm > 0 && distributionDiameterMm > 0)
        val slope = sqrt(runMm * runMm + riseMm * riseMm)
        // Clear cover is to bar surface, so the end centreline offsets include half diameter.
        val mainAcross = widthMm - 2 * coverMm - mainDiameterMm
        val distributionAcross = slope - 2 * coverMm - distributionDiameterMm
        require(mainAcross > 0 && distributionAcross > 0)
        fun bars(dia: Int, across: Double, spacing: Double, netLength: Double): StairNetBarSet {
            require(netLength > 0)
            val count = ceil(across / spacing).toInt() + 1
            return StairNetBarSet(dia, count, netLength,
                count * netLength / 1000.0 * (dia.toDouble() * dia / 162.0))
        }
        val main = bars(mainDiameterMm, mainAcross, mainSpacingMm, slope)
        val distribution = bars(distributionDiameterMm, distributionAcross, distributionSpacingMm,
            widthMm - 2 * coverMm)
        return StairQuantityReview(main, distribution, coverMm,
            main.totalNetWeightKg + distribution.totalNetWeightKg)
    }
}
