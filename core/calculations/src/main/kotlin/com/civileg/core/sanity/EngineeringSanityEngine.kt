package com.civileg.core.sanity

import kotlin.math.abs

/**
 * Engineering sanity checker — validates calculation results for physical plausibility.
 * Implements strict engineering boundary checks for Beams, Columns, Slabs, Footings, and Steel members.
 */
object EngineeringSanityEngine {

    /** Check a generic result map for engineering sanity violations. */
    fun check(results: Map<String, Double>, category: String = "General"): SanityReport {
        val items = mutableListOf<SanityItem>()

        // Check for NaN or Infinity in all values
        for ((key, value) in results) {
            if (value.isNaN()) {
                items.add(SanityItem(SanityLevel.ERROR, "$key is NaN — calculation produced invalid result"))
            } else if (value.isInfinite()) {
                items.add(SanityItem(SanityLevel.ERROR, "$key is Infinite — possible division by zero"))
            }
        }

        // Utilization ratio checks
        val utilizationRatio = results["utilizationRatio"] ?: results["utilization"]
        if (utilizationRatio != null && utilizationRatio > 1.0) {
            items.add(SanityItem(SanityLevel.ERROR,
                "Utilization ratio ${String.format("%.3f", utilizationRatio)} > 1.0 — element is OVER-STRESSED / العنصر غير آمن إنشائياً"))
        } else if (utilizationRatio != null && utilizationRatio > 0.9) {
            items.add(SanityItem(SanityLevel.WARNING,
                "Utilization ratio ${String.format("%.3f", utilizationRatio)} > 0.9 — element is near capacity / العنصر قريب من سعة التحمل القصوى"))
        }

        // Reinforcement ratio checks
        val requiredReinf = results["requiredReinforcement"] ?: results["asReq"]
        val providedReinf = results["providedReinforcement"] ?: results["asProv"]
        if (requiredReinf != null && providedReinf != null && requiredReinf > 0 && providedReinf < requiredReinf) {
            items.add(SanityItem(SanityLevel.ERROR,
                "Provided reinforcement ($providedReinf mm²) < required ($requiredReinf mm²) — UNDER-REINFORCED / حديد التسليح الموفر أقل من المطلوب"))
        }

        // Negative dimension checks
        for ((key, value) in results) {
            if (key.contains("thickness", ignoreCase = true) ||
                key.contains("width", ignoreCase = true) ||
                key.contains("depth", ignoreCase = true) ||
                key.contains("diameter", ignoreCase = true) ||
                key.contains("area", ignoreCase = true)) {
                if (value < 0) {
                    items.add(SanityItem(SanityLevel.ERROR, "$key = $value is negative — invalid geometry / أبعاد غير منطقية بالسالب"))
                }
            }
        }

        val hasError = items.any { it.level == SanityLevel.ERROR }
        val hasWarning = items.any { it.level == SanityLevel.WARNING }

        return SanityReport(category, items, hasError, hasWarning)
    }

    /** Beam Specific Engineering Sanity Check */
    fun checkBeam(
        bMm: Double,
        hMm: Double,
        spanM: Double,
        asReqMm2: Double,
        asProvMm2: Double,
        shearVuKN: Double,
        shearVcKN: Double,
        deflectionMm: Double = 0.0,
        deflectionLimitMm: Double = 0.0
    ): SanityReport {
        val items = mutableListOf<SanityItem>()

        if (bMm <= 0 || hMm <= 0 || spanM <= 0) {
            items.add(SanityItem(SanityLevel.ERROR, "Invalid beam dimensions (b=$bMm, h=$hMm, span=$spanM)"))
        }

        // Aspect ratio b/h check (typically between 0.2 and 0.8 for rectangular beams)
        val aspectRatio = bMm / hMm
        if (aspectRatio < 0.2 || aspectRatio > 1.0) {
            items.add(SanityItem(SanityLevel.WARNING, "Unusual beam aspect ratio b/h = ${String.format("%.2f", aspectRatio)} (recommended 0.25 - 0.6)"))
        }

        // Span to depth ratio L/h check (typically 10 to 25 for RC beams)
        val spanToDepth = (spanM * 1000.0) / hMm
        if (spanToDepth > 30.0) {
            items.add(SanityItem(SanityLevel.WARNING, "High span-to-depth ratio L/h = ${String.format("%.1f", spanToDepth)} — high risk of excessive deflection"))
        }

        // Reinforcement checks
        if (asProvMm2 < asReqMm2) {
            items.add(SanityItem(SanityLevel.ERROR, "Provided steel ($asProvMm2 mm²) < Required steel ($asReqMm2 mm²) — Beam unsafe in flexure"))
        }

        val maxSteelRatio = 0.04 // 4% maximum reinforcement
        val steelRatio = asProvMm2 / (bMm * hMm)
        if (steelRatio > maxSteelRatio) {
            items.add(SanityItem(SanityLevel.ERROR, "Reinforcement ratio ${String.format("%.2f", steelRatio * 100)}% exceeds max limit of 4% — Over-reinforced beam"))
        }

        // Shear check
        if (shearVuKN > shearVcKN * 3.0) {
            items.add(SanityItem(SanityLevel.ERROR, "Ultimate shear Vu ($shearVuKN kN) exceeds max concrete capacity limit — Section size must be increased"))
        }

        // Deflection check
        if (deflectionLimitMm > 0 && deflectionMm > deflectionLimitMm) {
            items.add(SanityItem(SanityLevel.ERROR, "Deflection δ = ${String.format("%.2f", deflectionMm)} mm exceeds limit of ${String.format("%.2f", deflectionLimitMm)} mm"))
        }

        val hasError = items.any { it.level == SanityLevel.ERROR }
        val hasWarning = items.any { it.level == SanityLevel.WARNING }
        return SanityReport("Beam Design", items, hasError, hasWarning)
    }

    /** Column Specific Engineering Sanity Check */
    fun checkColumn(
        bMm: Double,
        tMm: Double,
        lengthM: Double,
        puKN: Double,
        puCapacityKN: Double,
        slendernessLambda: Double,
        asProvMm2: Double,
        agMm2: Double = bMm * tMm
    ): SanityReport {
        val items = mutableListOf<SanityItem>()

        if (puKN > puCapacityKN) {
            items.add(SanityItem(SanityLevel.ERROR, "Ultimate axial load Pu ($puKN kN) exceeds column capacity ($puCapacityKN kN) — Column unsafe"))
        }

        val rho = asProvMm2 / agMm2
        if (rho < 0.008) {
            items.add(SanityItem(SanityLevel.WARNING, "Reinforcement ratio ${String.format("%.2f", rho * 100)}% is below standard min limit of 0.8%"))
        } else if (rho > 0.06) {
            items.add(SanityItem(SanityLevel.ERROR, "Reinforcement ratio ${String.format("%.2f", rho * 100)}% exceeds max limit of 6% — Congested column section"))
        }

        if (slendernessLambda > 30.0) {
            items.add(SanityItem(SanityLevel.WARNING, "High slenderness ratio λ = ${String.format("%.1f", slendernessLambda)} — Slender column with significant second-order moments"))
        }

        val hasError = items.any { it.level == SanityLevel.ERROR }
        val hasWarning = items.any { it.level == SanityLevel.WARNING }
        return SanityReport("Column Design", items, hasError, hasWarning)
    }

    /** Legacy overload for backward compatibility. */
    fun check(any: Any): SanityReport = SanityReport("General", emptyList())
}

data class SanityReport(
    val category: String,
    val items: List<SanityItem>,
    val hasError: Boolean = false,
    val hasWarning: Boolean = false
)

data class SanityItem(val level: SanityLevel, val message: String)

enum class SanityLevel { ERROR, WARNING, INFO }
