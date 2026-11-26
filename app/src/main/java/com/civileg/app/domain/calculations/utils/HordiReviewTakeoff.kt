package com.civileg.app.domain.calculations.utils

import com.civileg.app.domain.entities.SlabDesignResult

/** Interim review takeoff of engine-adopted longitudinal bars only.
 * It deliberately does not invent mesh, stirrups, anchorage or bends.
 * Steel is per one rib. Concrete is separately per periodic one-metre width.
 * No finite-strip rib count is inferred from pitch and cover.
 */
object HordiReviewTakeoff {
    const val BASIS_WIDTH_MM = 1000.0
    const val LIMITATION = "REVIEW ONLY: straight main-bar bodies for ONE RIB. Concrete is separately per 1 m periodic width. Anchorage, laps, topping steel, stirrups, supports and cross ribs are not adopted. Not a complete BBS or total project steel weight."

    fun schedule(
        result: SlabDesignResult, code: String, spanMm: Double,
        ribSpacingMm: Double, coverMm: Double
    ): AdoptedReinforcementSchedule? {
        if (!spanMm.isFinite() || !ribSpacingMm.isFinite() || !coverMm.isFinite() ||
            coverMm < 0 || spanMm <= 0 || ribSpacingMm <= 0) return null
        if (!result.isSafe || !HordiBarAdoption.areaIsSufficient(result.requiredReinforcement, result.providedReinforcement)) return null
        val barsPerRib = HordiBarAdoption.adoptedCount(result.providedReinforcement, result.barDiameter) ?: return null
        val diameter = result.barDiameter.toInt()
        if (diameter.toDouble() != result.barDiameter ||
            !com.civileg.app.domain.entities.RebarCatalog.isValidDiameter(diameter)) return null
        val ribCount = 1 // Actual panel rib count is not an input. Do not fabricate it.
        return AdoptedReinforcementSchedule(code, listOf(AdoptedBarGroup(
            mark = "01", role = "Rib main bottom bar bodies, review only",
            diameterMm = diameter, barsPerMember = barsPerRib, membersCount = ribCount,
            bodyLengthMm = spanMm,
            startExtensionMm = 0.0, endExtensionMm = 0.0,
            startStationMm = 0.0, endStationMm = spanMm,
            code = code, edition = null, clause = null,
            shapeDescription = "Straight body between support faces. End anchorage outside clear span not adopted."
        )))
    }

    /** Net rib+topping volume in a periodic 1 m strip, excluding blocks and
     * support/cross-rib solid zones not described by the current inputs. */
    fun concreteM3(spanMm: Double, totalMm: Double, toppingMm: Double, ribWidthMm: Double, spacingMm: Double): Double {
        require(listOf(spanMm, totalMm, toppingMm, ribWidthMm, spacingMm).all { it.isFinite() && it > 0 })
        require(toppingMm < totalMm && ribWidthMm <= spacingMm)
        return spanMm / 1000 * (toppingMm + ribWidthMm / spacingMm * (totalMm - toppingMm)) / 1000
    }
}
