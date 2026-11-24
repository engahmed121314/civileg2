package com.civileg.app.domain.calculations.utils

/** Duplicate entry points must describe one physical calculation case.
 * Missing detail inputs remain missing; they are not filled from this check.
 * This verifies identity only, not the engineering validity of a value.
 */
object HordiCaseBinding {
    fun conflicts(case: JoistDetailCase, clearSpanMm: Double, ultimateMomentKnM: Double,
        ultimateShearKn: Double, coverMm: Double): List<String> = buildList {
        case.clearSpanMm?.let {
            if (it != clearSpanMm) add("Detail-case clear span differs from the calculation span")
        }
        case.loadBasis?.let {
            if (it.ultimatePositiveMomentKnMPerRib != ultimateMomentKnM)
                add("Detail-case moment differs from the calculation demand")
            if (it.ultimateShearKnPerRib != ultimateShearKn)
                add("Detail-case shear differs from the calculation demand")
        }
        case.detailing?.let {
            if (it.coverToLinkOutsideMm != coverMm)
                add("Detail-case cover differs from the calculation cover")
        }
    }
}
