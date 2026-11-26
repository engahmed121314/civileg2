package com.civileg.app.domain.calculations.ecp

/**
 * ECP 203-2025 (6th update) column provisions used to compare the app's column assumptions.
 * Source: scan file-6f2d5588.pdf (cover page reads "كود رقم 203-2025، التحديث السادس"). Cites give PDF page / printed page.
 * Anything not transcribed from that scan is NOT ASSESSED and returns null. Nothing here is invented.
 */
object EcpColumnProvisions2025 {

    // ---- Slenderness: Table 6-7 (PDF 234, p.6-43) short limits; Table 6-8 (PDF 234, p.6-43) slender maxima ----

    enum class SectionKind { RECTANGULAR, CIRCULAR }

    /** Table 6-7 limit on lambda_t / lambda_b (rectangular) or lambda_D (circular): braced / unbraced. */
    fun shortLimitDimensional(kind: SectionKind, braced: Boolean): Double = when (kind) {
        SectionKind.RECTANGULAR -> if (braced) 15.0 else 10.0
        SectionKind.CIRCULAR -> if (braced) 12.0 else 8.0
    }

    /** Table 6-7 limit on lambda_i = He / i: 50 braced, 35 unbraced. */
    fun shortLimitLambdaI(braced: Boolean): Double = if (braced) 50.0 else 35.0

    /** Table 6-8 maximum lambda_i for slender columns: 100 braced, 70 unbraced. */
    fun maxLambdaI(braced: Boolean): Double = if (braced) 100.0 else 70.0

    /** Table 6-8 maximum lambda_t / lambda_b (rectangular: 30 / 23) or lambda_D (circular: 25 / 18). */
    fun maxDimensional(kind: SectionKind, braced: Boolean): Double = when (kind) {
        SectionKind.RECTANGULAR -> if (braced) 30.0 else 23.0
        SectionKind.CIRCULAR -> if (braced) 25.0 else 18.0
    }

    enum class SlendernessClass { SHORT, SLENDER, EXCEEDS_MAXIMUM }

    /** Classifies by lambda_i (clause 6-4-4 and 6-4-5, Tables 6-7 and 6-8). */
    fun classifyByLambdaI(lambdaI: Double, braced: Boolean): SlendernessClass = when {
        lambdaI <= shortLimitLambdaI(braced) -> SlendernessClass.SHORT
        lambdaI <= maxLambdaI(braced) -> SlendernessClass.SLENDER
        else -> SlendernessClass.EXCEEDS_MAXIMUM
    }

    /** FAIL text (EN + AR) when lambda_i exceeds the Table 6-8 maximum; null otherwise. FAIL per the user decision, as in ColumnDesignEngine. */
    fun maxSlendernessWarning(lambdaI: Double, braced: Boolean): String? {
        if (classifyByLambdaI(lambdaI, braced) != SlendernessClass.EXCEEDS_MAXIMUM) return null
        val v = String.format(java.util.Locale.US, "%.1f", lambdaI)
        val m = fmt(maxLambdaI(braced))
        val kind = if (braced) "braced" else "unbraced"
        return "FAIL: slenderness lambda_i = $v exceeds the ECP 203-2025 Table 6-8 maximum $m ($kind, p.6-43). " +
            "Change the section or bracing. / فشل: نسبة النحافة λ = $v تتجاوز الحد الأقصى $m في الجدول 6-8 من ECP 203-2025 (صفحة 6-43)، غيّر المقطع أو التدعيم."
    }

    /**
     * Legacy check used by core ECP203Engine.analyzeSlenderColumn: lambda = k*L/b against 15 (braced) / 10 (unbraced).
     * Returns true when the legacy result agrees with Table 6-7 lambda_b limits for a rectangular section.
     * Note: 15/0.30 = 50 matches the lambda_i limit, but 10/0.30 = 33.3 while Table 6-7 prints 35 for unbraced,
     * so the two tables are not exactly interchangeable; the printed values are used as written.
     */
    fun legacyLimitMatchesTable67(braced: Boolean): Boolean =
        shortLimitDimensional(SectionKind.RECTANGULAR, braced) == (if (braced) 15.0 else 10.0)

    // ---- Concrete cover for reinforcing steel (clause 2-3-4-2-7): Tables 2-21 and 2-22 ----

    /** Minimum cover (mm) for non-prestressed reinforced concrete. Parenthesised table values are prestressed and not used. */
    enum class Exposure(val minCoverMm: Double, val source: String) {
        CARBONATION_VERY_DRY(20.0, "Table 2-21, RH < 30% (PDF 60, p.2-32)"),
        CARBONATION_DRY(25.0, "Table 2-21, dry or permanently wet (PDF 60, p.2-32)"),
        CARBONATION_WET_RARELY_DRY(35.0, "Table 2-21, wet and rarely dry (PDF 60, p.2-32)"),
        CARBONATION_MODERATE_HUMIDITY(35.0, "Table 2-21, moderate humidity (PDF 60, p.2-32)"),
        CARBONATION_CYCLIC_WET_DRY(40.0, "Table 2-21, cyclic wet and dry (PDF 60, p.2-32)"),
        CHLORIDE_MODERATE_HUMIDITY(45.0, "Table 2-22, moderate humidity with chlorides (PDF 61, p.2-33)"),
        CHLORIDE_WET_RARELY_DRY(50.0, "Table 2-22, wet / rarely dry with chlorides (PDF 61, p.2-33)"),
        CHLORIDE_CYCLIC_WET_DRY(60.0, "Table 2-22, cyclic wet and dry with chlorides (PDF 61, p.2-33)")
    }

    data class CoverComparison(
        val exposure: Exposure,
        val requiredMm: Double,
        val appCoverMm: Double,
        val adequate: Boolean,
        val note: String
    )

    /**
     * Compares the app cover with the Table 2-21 / 2-22 minimum for an exposure.
     * Sea water (Table 2-23), sulfates (Table 2-24), fire (Tables 2-25 to 2-29, column fire cover Table 2-28)
     * and clause 2-3-4-4 are NOT ASSESSED here: no values were transcribed.
     */
    fun compareCover(exposure: Exposure, appCoverMm: Double): CoverComparison {
        val ok = appCoverMm >= exposure.minCoverMm
        val note = if (ok) {
            "Cover ${fmt(appCoverMm)} mm >= ${fmt(exposure.minCoverMm)} mm (${exposure.source}). Fire and sea/sulfate exposure NOT ASSESSED."
        } else {
            "Cover ${fmt(appCoverMm)} mm < ${fmt(exposure.minCoverMm)} mm required (${exposure.source})."
        }
        return CoverComparison(exposure, exposure.minCoverMm, appCoverMm, ok, note)
    }

    /** Note added to ECP column code notes beside the fixed app cover. */
    fun coverNote(appCoverMm: Double): String =
        "Cover ${fmt(appCoverMm)} mm is a fixed app value. ECP 203-2025 Table 2-21 (p.2-32) needs 20-40 mm for carbonation exposure, " +
            "Table 2-22 (p.2-33) needs 45-60 mm with chlorides. Fire cover (Table 2-28, p.2-40) NOT ASSESSED."

    private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else v.toString()
}
