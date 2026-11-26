package com.civileg.app.domain.calculations.ecp

/**
 * Minimum concrete cover from the exposure condition, ECP 203-2025 chapter 2 (values checked on the page images).
 * Reinforced concrete values only (the prestressed values in parentheses in the code are not used here).
 * Not covered by the code text found: cover from bar diameter, soil-cast footing cover, beam fire cover (Table 2-26 not re-verified).
 */
object ECP2025Cover {

    enum class Exposure(
        val minCoverMm: Double, val minFcu: Double?, val maxWc: Double?, val source: String, val labelEn: String, val labelAr: String
    ) {
        VERY_DRY(20.0, 20.0, null, "Table 2-21, p.2-32", "Very dry (RH < 30%)", "شديدة الجفاف"),
        DRY(25.0, 25.0, 0.60, "Table 2-21, p.2-32", "Dry or permanently wet", "جافة أو مبتلة باستمرار"),
        QUASI_PERMANENT_WET(35.0, 30.0, 0.55, "Table 2-21, p.2-32", "Quasi-permanent wet, rarely dry", "مبتلة شبه مستمرة"),
        MODERATE_HUMIDITY(35.0, 30.0, 0.50, "Table 2-21, p.2-32", "Moderate humidity", "رطوبة معتدلة"),
        WET_DRY_CYCLES(40.0, 35.0, 0.45, "Table 2-21, p.2-32", "Wet and dry cycles", "دورات من البلل والجفاف"),
        CHLORIDE_MODERATE(45.0, 30.0, 0.50, "Table 2-22, p.2-33", "Chlorides: moderate humidity", "كلوريدات: رطوبة معتدلة"),
        CHLORIDE_QUASI_PERMANENT(50.0, 35.0, 0.45, "Table 2-22, p.2-33", "Chlorides: quasi-permanent wet", "كلوريدات: بلل شبه مستمر"),
        CHLORIDE_WET_DRY(60.0, 40.0, 0.40, "Table 2-22, p.2-33", "Chlorides: wet and dry cycles", "كلوريدات: دورات بلل وجفاف"),
        SEA_SALT_AIR(45.0, 35.0, 0.45, "Table 2-23, p.2-34", "Sea: salt-laden air", "هواء محمل بالأملاح"),
        SEA_SUBMERGED(50.0, 40.0, 0.45, "Table 2-23, p.2-34", "Sea: continuously submerged", "مغمورة بماء البحر"),
        SEA_TIDAL_SPLASH(60.0, 45.0, 0.40, "Table 2-23, p.2-34", "Sea: tidal and splash zone", "منطقة المد والجزر والتلاطم")
    }

    /** Table 2-25 (p.2-39, re-verified): slabs, siliceous aggregate, reinforced. Hours 1 / 1.5 / 2 / 3 / 4 -> 20 / 25 / 30 / 40 / 50 mm. */
    fun slabFireCoverMm(hours: Double): Double? = when (hours) {
        1.0 -> 20.0
        1.5 -> 25.0
        2.0 -> 30.0
        3.0 -> 40.0
        4.0 -> 50.0
        else -> null
    }

    /** Minimum cover for a slab: the larger of the exposure value and the fire value (null hours = no fire requirement entered). */
    fun slabMinCover(exposure: Exposure, fireHours: Double?): Double {
        val fire = fireHours?.let { slabFireCoverMm(it) } ?: 0.0
        return maxOf(exposure.minCoverMm, fire)
    }
}
