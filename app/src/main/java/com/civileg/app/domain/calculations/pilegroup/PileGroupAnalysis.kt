package com.civileg.app.domain.calculations.pilegroup

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.max

/**
 * مدخلات تحليل مجموعة الخوازيق.
 *
 * الوحدات:
 * - spacingX / spacingY / pileDiameter / columnWidth / columnDepth: mm
 * - pileLength: m
 * - pileCapacity / verticalLoad / horizontalLoad: kN
 * - momentX / momentY: kN·m
 * - pileModulus: MPa
 * - allowableSettlement: mm
 */
data class PileGroupInput(
    val pilesX: Int,
    val pilesY: Int,
    val spacingX: Double,
    val spacingY: Double,
    val pileDiameter: Double,
    val pileLength: Double,
    val pileCapacity: Double,
    val verticalLoad: Double,
    val momentX: Double = 0.0,
    val momentY: Double = 0.0,
    val horizontalLoad: Double = 0.0,
    val pileModulus: Double = 25000.0,
    val columnWidth: Double = 600.0,
    val columnDepth: Double = 600.0,
    val allowableSettlement: Double = 25.0
)

data class PileLoad(
    val x: Double,
    val y: Double,
    val load: Double
)

data class PileGroupCheck(
    val name: String,
    val calculated: Double,
    val limit: Double,
    val unit: String,
    val passed: Boolean
)

data class PileGroupResult(
    val numberOfPiles: Int,
    val efficiency: Double,
    val groupCapacity: Double,
    val averagePileLoad: Double,
    val maxPileLoad: Double,
    val minPileLoad: Double,
    val maxPileUtilization: Double,
    val capMomentX: Double,
    val capMomentY: Double,
    val interactionFactor: Double,
    val groupSettlementFactor: Double,
    val singlePileSettlement: Double,
    val groupSettlement: Double,
    val allowableSettlement: Double,
    val isSafe: Boolean,
    val checks: List<PileGroupCheck>,
    val warnings: List<String>,
    val notes: List<String>,
    val pileLoads: List<PileLoad>
)

/**
 * تحليل مجموعة خوازيق صلبة (rigid cap):
 * - كفاءة المجموعة بطريقة Converse-Labarre.
 * - توزيع الأحمال على الخوازيق مع عزوم حول المحورين.
 * - عزوم تصميم القاعدة عند وجه العمود.
 * - ترخيم المجموعة ومعامل التفاعل (Poulos).
 */
class PileGroupAnalysis {

    companion object {
        /**
         * كفاءة المجموعة بطريقة Converse-Labarre.
         *
         * Eg = 1 − θ/90 · [((n−1)·m + (m−1)·n) / (m·n)]
         * حيث θ = atan(d/s) بالدرجات، m = عدد الخوازيق في الصف، n = عدد الصفوف.
         */
        fun converseLabarreEfficiency(
            pilesX: Int,
            pilesY: Int,
            pileDiameter: Double,
            spacing: Double
        ): Double {
            if (pilesX <= 0 || pilesY <= 0 || pileDiameter <= 0.0 || spacing <= 0.0) return 0.0
            val thetaDeg = Math.toDegrees(atan(pileDiameter / spacing))
            val m = pilesX.toDouble()
            val n = pilesY.toDouble()
            val term = ((n - 1.0) * m + (m - 1.0) * n) / (m * n)
            return (1.0 - thetaDeg / 90.0 * term).coerceIn(0.0, 1.0)
        }
    }

    fun analyze(input: PileGroupInput): PileGroupResult {
        validate(input)

        val n = input.pilesX * input.pilesY
        val xs = (0 until input.pilesX).map { (it - (input.pilesX - 1) / 2.0) * input.spacingX }
        val ys = (0 until input.pilesY).map { (it - (input.pilesY - 1) / 2.0) * input.spacingY }

        val sumX2 = ys.size * xs.sumOf { it * it }
        val sumY2 = xs.size * ys.sumOf { it * it }
        val base = input.verticalLoad / n

        val pileLoads = mutableListOf<PileLoad>()
        for (y in ys) {
            for (x in xs) {
                // Moments are kN·m; convert to kN·mm so that M·y/Σy² is in kN.
                val fromMomentX = if (sumY2 > 0.0) input.momentX * 1000.0 * y / sumY2 else 0.0
                val fromMomentY = if (sumX2 > 0.0) input.momentY * 1000.0 * x / sumX2 else 0.0
                pileLoads += PileLoad(x = x, y = y, load = base + fromMomentX + fromMomentY)
            }
        }

        val maxPileLoad = pileLoads.maxOf { it.load }
        val minPileLoad = pileLoads.minOf { it.load }
        val averagePileLoad = pileLoads.sumOf { it.load } / n

        val averageSpacing = 0.5 * (input.spacingX + input.spacingY)
        val efficiency = converseLabarreEfficiency(
            pilesX = input.pilesX,
            pilesY = input.pilesY,
            pileDiameter = input.pileDiameter,
            spacing = averageSpacing
        )
        val groupCapacity = efficiency * n * input.pileCapacity
        val maxPileUtilization = if (input.pileCapacity > 0.0) maxPileLoad / input.pileCapacity else 0.0

        // ── Cap design moments at the column face ────────────────────────
        val halfColumnX = input.columnWidth / 2.0
        val halfColumnY = input.columnDepth / 2.0

        val capMomentY = max(
            abs(pileLoads.filter { it.x > halfColumnX }
                .sumOf { it.load * (it.x - halfColumnX) }),
            abs(pileLoads.filter { it.x < -halfColumnX }
                .sumOf { it.load * (-it.x - halfColumnX) })
        ) / 1000.0 // kN·mm → kN·m

        val capMomentX = max(
            abs(pileLoads.filter { it.y > halfColumnY }
                .sumOf { it.load * (it.y - halfColumnY) }),
            abs(pileLoads.filter { it.y < -halfColumnY }
                .sumOf { it.load * (-it.y - halfColumnY) })
        ) / 1000.0

        // ── Settlement interaction ──────────────────────────────────────
        val pileArea = PI * input.pileDiameter * input.pileDiameter / 4.0
        val singlePileSettlement = if (pileArea > 0.0 && input.pileModulus > 0.0) {
            averagePileLoad * 1000.0 * (input.pileLength * 1000.0) / (pileArea * input.pileModulus)
        } else 0.0

        val spacingToDiameter = averageSpacing / input.pileDiameter
        // Poulos-type interaction factor: α ≈ 0.5 at s/d = 3, decaying with spacing.
        val interactionFactor = (0.5 * exp(-0.15 * (spacingToDiameter - 3.0))).coerceIn(0.0, 0.5)
        val groupSettlementFactor = 1.0 + (n - 1) * interactionFactor
        val groupSettlement = singlePileSettlement * groupSettlementFactor

        val checks = listOf(
            PileGroupCheck("Group capacity", input.verticalLoad, groupCapacity, "kN", input.verticalLoad <= groupCapacity),
            PileGroupCheck("Maximum pile load", maxPileLoad, input.pileCapacity, "kN", maxPileLoad <= input.pileCapacity),
            PileGroupCheck("Uplift (minimum pile load)", minPileLoad, 0.0, "kN", minPileLoad >= 0.0),
            PileGroupCheck("Group settlement", groupSettlement, input.allowableSettlement, "mm", groupSettlement <= input.allowableSettlement)
        )

        val warnings = mutableListOf<String>()
        if (input.verticalLoad > groupCapacity) warnings.add("Group capacity is insufficient")
        if (maxPileLoad > input.pileCapacity) warnings.add("Maximum pile load exceeds the allowable pile capacity")
        if (minPileLoad < 0.0) warnings.add("Uplift occurs in some piles — increase spacing or revise the layout")
        if (groupSettlement > input.allowableSettlement) warnings.add("Group settlement exceeds the allowable limit")
        if (input.horizontalLoad > 0.0) {
            warnings.add("Horizontal load is reported but not distributed in this rigid-cap model")
        }

        val notes = mutableListOf<String>()
        notes.add("Converse-Labarre efficiency Eg = ${"%.3f".format(efficiency)}")
        notes.add("Group capacity = Eg·N·Qp = ${"%.1f".format(groupCapacity)} kN")
        notes.add("Pile loads: P = V/N + Mx·y/Σy² + My·x/Σx²")
        notes.add("Cap moments at column face: Mx = ${"%.1f".format(capMomentX)} kN·m, My = ${"%.1f".format(capMomentY)} kN·m")
        notes.add("Interaction factor α = ${"%.3f".format(interactionFactor)} at s/d = ${"%.2f".format(spacingToDiameter)}")
        notes.add("Group settlement factor = ${"%.3f".format(groupSettlementFactor)}")

        val isSafe = input.horizontalLoad == 0.0 &&
            input.verticalLoad <= groupCapacity &&
            maxPileLoad <= input.pileCapacity &&
            minPileLoad >= 0.0 &&
            groupSettlement <= input.allowableSettlement

        return PileGroupResult(
            numberOfPiles = n,
            efficiency = efficiency,
            groupCapacity = groupCapacity,
            averagePileLoad = averagePileLoad,
            maxPileLoad = maxPileLoad,
            minPileLoad = minPileLoad,
            maxPileUtilization = maxPileUtilization,
            capMomentX = capMomentX,
            capMomentY = capMomentY,
            interactionFactor = interactionFactor,
            groupSettlementFactor = groupSettlementFactor,
            singlePileSettlement = singlePileSettlement,
            groupSettlement = groupSettlement,
            allowableSettlement = input.allowableSettlement,
            isSafe = isSafe,
            checks = checks,
            warnings = warnings,
            notes = notes,
            pileLoads = pileLoads
        )
    }

    private fun validate(input: PileGroupInput) {
        require(input.pilesX > 0 && input.pilesY > 0) {
            "pilesX and pilesY must be positive / عدد الخوازيق يجب أن يكون موجباً"
        }
        require(input.spacingX > 0.0 && input.spacingY > 0.0) {
            "spacing must be positive / التباعد يجب أن يكون موجباً"
        }
        require(input.pileDiameter > 0.0) { "pileDiameter must be positive / قطر الخازوق موجب" }
        require(input.pileLength > 0.0) { "pileLength must be positive / طول الخازوق موجب" }
        require(input.pileCapacity > 0.0) { "pileCapacity must be positive / قدرة الخازوق موجبة" }
        require(input.verticalLoad >= 0.0) { "verticalLoad must be non-negative / الحمل الرأسي غير سالب" }
        require(input.pileModulus > 0.0) { "pileModulus must be positive / معامل مرونة الخازوق موجب" }
        require(input.columnWidth >= 0.0 && input.columnDepth >= 0.0) {
            "column dimensions must be non-negative / أبعاد العمود غير سالبة"
        }
        require(input.allowableSettlement > 0.0) {
            "allowableSettlement must be positive / الترخيم المسموح موجب"
        }
        listOf(
            input.spacingX, input.spacingY, input.pileDiameter, input.pileLength,
            input.pileCapacity, input.verticalLoad, input.momentX, input.momentY,
            input.horizontalLoad, input.pileModulus, input.columnWidth,
            input.columnDepth, input.allowableSettlement
        ).forEach {
            require(it.isFinite()) { "inputs must be finite / المدخلات يجب أن تكون منتهية" }
        }
    }
}
