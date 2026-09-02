package com.civileg.app.utils

import kotlin.math.roundToInt

/**
 * AI CHECKER — types & scoring primitives (Roadmap 1.1).
 *
 * Rule-based classification layer ABOVE CalculationValidator.
 * ZERO HALLUCINATION POLICY: rules only CLASSIFY values the calculators
 * produced; capacities/stresses are never invented here.
 *
 * Bilingual titles for UI; detail/suggestion English-only (ADR-009 exports).
 */
object AiChecker {

    enum class Severity(val weight: Int) {
        INFO(0),
        OPTIMIZATION(3),   // cost-saving opportunity; design still valid
        WARNING(10),       // verify before construction
        CRITICAL(25)       // code violation / internal contradiction
    }

    data class Finding(
        val code: String,
        val severity: Severity,
        val titleEn: String,
        val titleAr: String,
        val detailEn: String,
        val suggestionEn: String? = null
    )

    data class AiReport(
        val elementType: String,
        val findings: List<Finding>
    ) {
        val criticals get() = findings.filter { it.severity == Severity.CRITICAL }
        val warningsList get() = findings.filter { it.severity == Severity.WARNING }
        val optimizations get() = findings.filter { it.severity == Severity.OPTIMIZATION }

        /** Health score 0–100 (100 = clean). */
        val score: Int
            get() = (100 - findings.sumOf { it.severity.weight }).coerceIn(0, 100)

        val passed: Boolean get() = criticals.isEmpty()

        companion object {
            fun merge(vararg reports: AiReport): AiReport = AiReport(
                elementType = reports.joinToString("+") { it.elementType },
                findings = reports.flatMap { it.findings }.sortedByDescending { it.severity.weight }
            )
        }
    }

    // Plausible material windows → VERIFY-INPUT warnings only (never auto-corrected)
    const val FCU_MIN = 20.0; const val FCU_MAX = 70.0   // MPa
    const val FY_MIN = 300.0; const val FY_MAX = 600.0   // MPa

    fun checkMaterial(fcu: Double?, fy: Double?): List<Finding> {
        val out = mutableListOf<Finding>()
        fcu?.let {
            if (it !in FCU_MIN..FCU_MAX)
                out += Finding("MAT_FCU", Severity.WARNING,
                    "fcu خارج النطاق المعتاد", "fcu outside common window",
                    "fcu = $it MPa (common structural range $FCU_MIN–$FCU_MAX). Verify input.")
        }
        fy?.let {
            if (it !in FY_MIN..FY_MAX)
                out += Finding("MAT_FY", Severity.WARNING,
                    "fy خارج النطاق المعتاد", "fy outside common window",
                    "fy = $it MPa (common grades $FY_MIN–$FY_MAX). Verify input.")
        }
        return out
    }

    fun utilFinding(ratio: Double, element: String): Finding? = when {
        ratio > 1.0 -> Finding("UTIL_OVER", Severity.CRITICAL,
            "تجاوز الاستغلال 100%", "Utilization over 100%",
            "$element utilization = ${pct(ratio)} — demand exceeds capacity.")
        ratio > 0.95 -> Finding("UTIL_HIGH", Severity.WARNING,
            "عند حد السعة", "At capacity limit",
            "$element utilization = ${pct(ratio)} — no reserve for site tolerances.")
        ratio in 0.01..0.35 -> Finding("UTIL_LOW", Severity.OPTIMIZATION,
            "تصميم مفرط الحجم", "Over-designed member",
            "$element utilization = ${pct(ratio)} — a leaner section may cut cost.")
        else -> null
    }

    private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

    // Convenience builders used by AiCheckerEngine
    fun crit(code: String, ar: String, en: String, detail: String, suggestion: String? = null) =
        Finding(code, Severity.CRITICAL, en, ar, detail, suggestion)

    fun warn(code: String, ar: String, en: String, detail: String, suggestion: String? = null) =
        Finding(code, Severity.WARNING, en, ar, detail, suggestion)

    fun opt(code: String, ar: String, en: String, detail: String, suggestion: String? = null) =
        Finding(code, Severity.OPTIMIZATION, en, ar, detail, suggestion)

    fun info(code: String, ar: String, en: String, detail: String) =
        Finding(code, Severity.INFO, en, ar, detail)
}
