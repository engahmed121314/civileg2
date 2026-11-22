package com.civileg.core.engine.rc.ecp

/** Project detailing choices, NOT a code requirement for even bar counts.
 * Only the four equal-diameter tension-bar case is covered. Every candidate
 * must still pass the coupled section calculation, including maximum steel.
 */
object Ecp203FourBarArrangement {
    enum class Policy { STANDARD_PAIRED_UPPER, EXPLICIT_ECONOMY_FOUR_BARS }

    fun arrange(widthMm: Double, totalDepthMm: Double, coverToOutsideLinksMm: Double,
        linkDiameterMm: Double, nominalMaximumAggregateMm: Double,
        barDiameterMm: Double, policy: Policy
    ): List<Ecp203BarLayout.Layer> {
        fun rows(vararg counts: Int) = counts.map { Ecp203BarLayout.Layer(it, barDiameterMm) }
        fun fits(layers: List<Ecp203BarLayout.Layer>) = Ecp203BarLayout.assess(widthMm,
            totalDepthMm, coverToOutsideLinksMm, linkDiameterMm,
            nominalMaximumAggregateMm, layers).fitsInsideLinks
        val single = rows(4)
        if (fits(single)) return single
        val split = when (policy) {
            Policy.STANDARD_PAIRED_UPPER -> rows(3, 2)
            Policy.EXPLICIT_ECONOMY_FOUR_BARS -> rows(2, 2)
        }
        require(fits(split)) {
            "Selected four-bar split policy does not fit inside the covered rib and links. Revise the section or actual reinforcement; no extra layers are inferred."
        }
        return split
    }
}
