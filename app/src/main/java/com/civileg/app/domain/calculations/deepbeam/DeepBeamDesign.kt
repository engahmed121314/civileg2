package com.civileg.app.domain.calculations.deepbeam

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * كود تصميم الكمرات العميقة.
 * ECP 203-2025 و ACI 318-25.
 */
enum class DeepBeamCode(
    val displayName: String,
    val version: String,
    val usesAciBaseProvisions: Boolean
) {
    ECP("Egyptian Code", "ECP 203-2025", false),
    ACI("American Code", "ACI 318-25", true),
    SBC("Saudi Code (ACI base)", "SBC 304-2024", true)
}

/**
 * مدخلات تصميم كمرة عميقة.
 *
 * الوحدات:
 * - span: البحر الصافي (m)
 * - width / depth / clearCover / bearingPlateWidth: mm
 * - fcu / fy: MPa
 * - deadLoad / liveLoad: kN/m
 * - pointLoad: kN
 */
data class DeepBeamInput(
    val code: DeepBeamCode = DeepBeamCode.ECP,
    val span: Double,
    val width: Double,
    val depth: Double,
    val fcu: Double,
    val fy: Double,
    val deadLoad: Double = 0.0,
    val liveLoad: Double = 0.0,
    val pointLoad: Double = 0.0,
    val clearCover: Double = 40.0,
    val bearingPlateWidth: Double = 300.0,
    val providedTieArea: Double = 0.0,
    val providedStirrupArea: Double = 0.0,
    val stirrupSpacing: Double = 200.0,
    val isContinuous: Boolean = false
)

data class DeepBeamCheck(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
)

data class DeepBeamResult(
    val isDeepBeam: Boolean,
    val spanToDepthRatio: Double,
    val spanToDepthLimit: Double,
    val selfWeight: Double,
    val factoredLoad: Double,
    val factoredMoment: Double,
    val factoredShear: Double,
    val strutAngleDeg: Double,
    val strutWidth: Double,
    val strutForce: Double,
    val strutCapacity: Double,
    val tieForce: Double,
    val tieAreaRequired: Double,
    val tieAreaProvided: Double,
    val minimumReinforcement: Double,
    val shearCapacity: Double,
    val bearingStress: Double,
    val bearingCapacity: Double,
    val isSafe: Boolean,
    val checks: List<DeepBeamCheck>,
    val warnings: List<String>,
    val notes: List<String>
)

/**
 * تصميم الكمرات العميقة بنموذج Strut-and-Tie.
 *
 * النموذج المبسط: حمل موزع/مركّز عند منتصف البحر ينتقل عبر
 * دعامتين مائلتين (struts) إلى الركيزتين، ويقاوم الشد كابل/تسليح
 * سفلي (tie). زاوية الدعامة تُحسب من هندسة العقدة.
 */
class DeepBeamDesign {

    companion object {
        /** نسبة البحر/العمق الحدية لتصنيف الكمرة العميقة. */
        fun spanToDepthLimit(code: DeepBeamCode, isContinuous: Boolean): Double = when (code) {
            DeepBeamCode.ECP -> if (isContinuous) 2.0 else 2.5
            DeepBeamCode.ACI, DeepBeamCode.SBC -> if (isContinuous) 2.5 else 4.0
        }

        /** فحص سريع لتصنيف الكمرة. */
        fun isDeepBeam(
            span: Double,
            depth: Double,
            code: DeepBeamCode = DeepBeamCode.ECP,
            isContinuous: Boolean = false
        ): Boolean {
            if (span <= 0.0 || depth <= 0.0) return false
            return (span * 1000.0 / depth) <= spanToDepthLimit(code, isContinuous)
        }
    }

    fun design(input: DeepBeamInput): DeepBeamResult {
        validate(input)

        val selfWeight = input.width * input.depth * 25.0 / 1e6 // kN/m
        val deadFactor = if (input.code == DeepBeamCode.ECP) 1.4 else 1.2
        val liveFactor = 1.6
        val factoredLoad = deadFactor * (input.deadLoad + selfWeight) + liveFactor * input.liveLoad
        val factoredPoint = liveFactor * input.pointLoad

        val momentCoeff = if (input.isContinuous) 1.0 / 10.0 else 1.0 / 8.0
        val shearCoeff = if (input.isContinuous) 0.55 else 0.50
        val factoredMoment = factoredLoad * input.span * input.span * momentCoeff +
            factoredPoint * input.span / 4.0
        val factoredShear = factoredLoad * input.span * shearCoeff + factoredPoint / 2.0

        val spanToDepthRatio = input.span * 1000.0 / input.depth
        val limit = spanToDepthLimit(input.code, input.isContinuous)
        val deep = spanToDepthRatio <= limit

        // ── Strut-and-tie geometry ────────────────────────────────────────
        val effectiveDepth = (input.depth - input.clearCover - 10.0).coerceAtLeast(1.0)
        val halfSpan = input.span * 1000.0 / 2.0
        val theta = atan(effectiveDepth / halfSpan)
        val sinTheta = sin(theta)
        val cosTheta = cos(theta)
        val tanTheta = tan(theta)

        val strutForce = if (sinTheta > 0.0) factoredShear / sinTheta else 0.0
        val tieForce = if (tanTheta > 0.0) factoredShear / tanTheta else 0.0

        val tieWidth = (2.0 * (input.clearCover + 10.0)).coerceAtLeast(100.0)
        val strutWidth = tieWidth * cosTheta + input.bearingPlateWidth * sinTheta
        val strutArea = input.width * strutWidth // mm²

        // ── Material strengths ───────────────────────────────────────────
        val fcRef = if (input.code.usesAciBaseProvisions) 0.80 * input.fcu else input.fcu
        val fce = if (!input.code.usesAciBaseProvisions) {
            0.67 * input.fcu / 1.5
        } else {
            0.85 * 0.75 * fcRef
        }
        val phiStrut = if (input.code.usesAciBaseProvisions) 0.75 else 1.0
        val strutCapacity = phiStrut * fce * strutArea / 1000.0 // kN

        // ── Tie reinforcement ───────────────────────────────────────────
        val phiTie = if (input.code.usesAciBaseProvisions) 0.75 else 1.0
        val tieAreaRequired = if (input.fy > 0.0) {
            tieForce * 1000.0 / (phiTie * input.fy)
        } else 0.0
        val minimumReinforcement = 0.0025 * input.width * input.depth
        val tieAreaProvided = input.providedTieArea

        // ── Shear capacity ──────────────────────────────────────────────
        val vcN = if (input.code.usesAciBaseProvisions) {
            0.17 * sqrt(fcRef) * input.width * effectiveDepth
        } else {
            0.24 * sqrt(input.fcu / 1.5) * input.width * effectiveDepth
        }
        val vsN = if (input.stirrupSpacing > 0.0) {
            input.providedStirrupArea * input.fy * effectiveDepth / input.stirrupSpacing
        } else 0.0
        val phiShear = if (input.code.usesAciBaseProvisions) 0.75 else 1.0
        val sectionShear = phiShear * (vcN + vsN) / 1000.0
        val stmShear = 2.0 * phiStrut * fce * strutArea * sinTheta / 1000.0
        val shearCapacity = minOf(sectionShear, stmShear)

        // ── Node bearing ────────────────────────────────────────────────
        val bearingStress = if (input.bearingPlateWidth * input.width > 0.0) {
            factoredShear * 1000.0 / (input.bearingPlateWidth * input.width)
        } else 0.0
        val bearingCapacity = if (input.code.usesAciBaseProvisions) {
            0.65 * 0.85 * fcRef
        } else {
            0.67 * input.fcu / 1.5
        }

        val checks = listOf(
            DeepBeamCheck(
                "Deep beam classification (ln/h)",
                spanToDepthRatio,
                limit,
                "-",
                deep
            ),
            DeepBeamCheck("Strut force", strutForce, strutCapacity, "kN", strutForce <= strutCapacity),
            DeepBeamCheck("Tie reinforcement", tieAreaRequired, tieAreaProvided, "mm²", tieAreaProvided >= tieAreaRequired),
            DeepBeamCheck("Minimum reinforcement", minimumReinforcement, tieAreaProvided, "mm²", tieAreaProvided >= minimumReinforcement),
            DeepBeamCheck("Shear capacity", factoredShear, shearCapacity, "kN", factoredShear <= shearCapacity),
            DeepBeamCheck("Node bearing stress", bearingStress, bearingCapacity, "MPa", bearingStress <= bearingCapacity)
        )

        val warnings = mutableListOf<String>()
        if (!deep) {
            warnings.add(
                "Section is not classified as a deep beam (ln/h = ${"%.2f".format(spanToDepthRatio)} > ${"%.2f".format(limit)}); use ordinary beam design"
            )
        }
        if (strutForce > strutCapacity) warnings.add("Strut capacity exceeded — increase section or concrete strength")
        if (tieAreaProvided < tieAreaRequired) warnings.add("Tie reinforcement is insufficient")
        if (tieAreaProvided < minimumReinforcement) warnings.add("Minimum deep-beam reinforcement is not satisfied")
        if (factoredShear > shearCapacity) warnings.add("Shear capacity is insufficient")
        if (bearingStress > bearingCapacity) warnings.add("Node bearing stress exceeds the limit")

        val notes = mutableListOf<String>()
        notes.add("Design basis: ${input.code.version}")
        if (input.code == DeepBeamCode.SBC) {
            notes.add("SBC 304 local amendments are not independently modeled; ACI-base provisions are used for this screening.")
        }
        notes.add("Strut-and-tie model with θ = ${"%.1f".format(Math.toDegrees(theta))}°")
        notes.add("Strut width = ${"%.1f".format(strutWidth)} mm, area = ${"%.0f".format(strutArea)} mm²")
        notes.add("fce = ${"%.2f".format(fce)} MPa, φ_strut = ${"%.2f".format(phiStrut)}")
        notes.add("Tie force = ${"%.1f".format(tieForce)} kN, As,req = ${"%.0f".format(tieAreaRequired)} mm²")
        notes.add("Minimum reinforcement = 0.0025·b·h = ${"%.0f".format(minimumReinforcement)} mm²")
        notes.add("Provide orthogonal reinforcement with spacing ≤ min(d/5, 300 mm)")

        val isSafe = deep &&
            strutForce <= strutCapacity &&
            tieAreaProvided >= tieAreaRequired &&
            tieAreaProvided >= minimumReinforcement &&
            factoredShear <= shearCapacity &&
            bearingStress <= bearingCapacity

        return DeepBeamResult(
            isDeepBeam = deep,
            spanToDepthRatio = spanToDepthRatio,
            spanToDepthLimit = limit,
            selfWeight = selfWeight,
            factoredLoad = factoredLoad,
            factoredMoment = factoredMoment,
            factoredShear = factoredShear,
            strutAngleDeg = Math.toDegrees(theta),
            strutWidth = strutWidth,
            strutForce = strutForce,
            strutCapacity = strutCapacity,
            tieForce = tieForce,
            tieAreaRequired = tieAreaRequired,
            tieAreaProvided = tieAreaProvided,
            minimumReinforcement = minimumReinforcement,
            shearCapacity = shearCapacity,
            bearingStress = bearingStress,
            bearingCapacity = bearingCapacity,
            isSafe = isSafe,
            checks = checks,
            warnings = warnings,
            notes = notes
        )
    }

    private fun validate(input: DeepBeamInput) {
        require(input.span > 0.0) { "span must be positive / البحر يجب أن يكون موجباً" }
        require(input.width > 0.0) { "width must be positive / العرض يجب أن يكون موجباً" }
        require(input.depth > 0.0) { "depth must be positive / العمق يجب أن يكون موجباً" }
        require(input.fcu > 0.0) { "fcu must be positive / مقاومة الخرسانة يجب أن تكون موجبة" }
        require(input.fy > 0.0) { "fy must be positive / مقاومة الحديد يجب أن تكون موجبة" }
        require(input.clearCover >= 0.0 && input.clearCover < input.depth) {
            "clearCover must be non-negative and less than depth / الغطاء غير صالح"
        }
        require(input.bearingPlateWidth > 0.0) { "bearingPlateWidth must be positive / عرض لوح الاستناد موجب" }
        require(input.deadLoad >= 0.0 && input.liveLoad >= 0.0 && input.pointLoad >= 0.0) {
            "loads must be non-negative / الأحمال يجب أن تكون غير سالبة"
        }
        require(input.providedTieArea >= 0.0 && input.providedStirrupArea >= 0.0) {
            "reinforcement areas must be non-negative / مساحات التسليح غير سالبة"
        }
        require(input.stirrupSpacing > 0.0) { "stirrupSpacing must be positive / تباعد الكانات موجب" }
        listOf(
            input.span, input.width, input.depth, input.fcu, input.fy,
            input.deadLoad, input.liveLoad, input.pointLoad, input.clearCover,
            input.bearingPlateWidth, input.providedTieArea, input.providedStirrupArea,
            input.stirrupSpacing
        ).forEach {
            require(it.isFinite()) { "inputs must be finite / المدخلات يجب أن تكون منتهية" }
        }
    }
}
