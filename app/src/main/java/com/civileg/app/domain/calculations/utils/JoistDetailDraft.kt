package com.civileg.app.domain.calculations.utils

/** Empty form values are missing, never implicit design conditions. */
data class JoistDetailDraft(
    val values: Map<String, String> = emptyMap(),
    val leftRestraint: JoistDetailCase.Restraint? = null,
    val rightRestraint: JoistDetailCase.Restraint? = null,
    val leftMaterial: JoistDetailCase.SupportMaterial? = null,
    val rightMaterial: JoistDetailCase.SupportMaterial? = null,
    val exposure: JoistDetailCase.Exposure? = null,
    val uniformlyDistributed: Boolean? = null,
    val seismicRequired: Boolean? = null,
    val sensitivePartitions: Boolean? = null
) {
    fun toCase(spanMm: Double, ultimateMomentKnM: Double, ultimateShearKn: Double,
        coverMm: Double): JoistDetailCase {
        fun number(key: String) = values[key]?.toDoubleOrNull() ?: Double.NaN
        fun integer(key: String) = values[key]?.toIntOrNull() ?: 0
        fun support(side: String, restraint: JoistDetailCase.Restraint?, material: JoistDetailCase.SupportMaterial?) =
            if (restraint == null || material == null) null else JoistDetailCase.Support(restraint, material,
                number(side + "Width"), number(side + "Moment"), number(side + "Anchorage"))
        return JoistDetailCase(
            actualRibCount = values["ribCount"]?.toIntOrNull(), clearSpanMm = spanMm,
            effectiveFlangeWidthMm = values["flangeWidth"]?.toDoubleOrNull(),
            leftSupport = support("left", leftRestraint, leftMaterial),
            rightSupport = support("right", rightRestraint, rightMaterial),
            loadBasis = uniformlyDistributed?.let { JoistDetailCase.LoadBasis(number("deadLoad"), number("liveLoad"),
                ultimateMomentKnM, ultimateShearKn, number("serviceMoment"), it) },
            serviceability = if (exposure == null || sensitivePartitions == null) null else JoistDetailCase.Serviceability(
                exposure, number("sustainedFraction"), number("duration"),
                values["partitionTime"]?.takeIf { it.isNotBlank() }?.let { it.toDoubleOrNull() ?: Double.NaN }, sensitivePartitions),
            detailing = seismicRequired?.let { JoistDetailCase.Detailing(coverMm, number("linkDiameter"), number("aggregate"),
                integer("mainDiameter"), integer("barCount"), number("stockLength"), it,
                number("leftSolid"), number("rightSolid")) }
        )
    }
}
