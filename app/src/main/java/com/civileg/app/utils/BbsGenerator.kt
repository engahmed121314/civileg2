package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.utils.CalculatorEngine.*
import kotlinx.parcelize.Parcelize
import kotlin.math.*

@Parcelize
data class BbsEntry(
    val memberMark: String,
    val barMark: String,
    val diameter: Int,
    val shapeCode: Int,
    val count: Int,
    val lengthA: Double, // mm
    val lengthB: Double = 0.0,
    val lengthC: Double = 0.0,
    val totalLengthPerBar: Double,
    val totalWeightKg: Double
) : Parcelable

object BbsGenerator {

    /**
     * Generates a Bar Bending Schedule for a designed Beam.
     * @param storyHeight_mm Column/beam story height in mm (default 3000mm = 3m)
     */
    fun generateBeamBbs(mark: String, result: BeamResult, storyHeight_mm: Double = 3000.0): List<BbsEntry> {
        // ── InputGuard: حراس المدخلات — لا حساب على مدخلات غير صالحة ──
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("width", result.width)               // mm
        InputGuard.positive("depth", result.depth)               // mm
        InputGuard.positive("reinforcementBottom.diameter", result.reinforcementBottom.diameter)
        InputGuard.positive("reinforcementBottom.numBars", result.reinforcementBottom.numBars)
        InputGuard.positive("stirrups.diameter", result.stirrups.diameter)
        InputGuard.inRange("storyHeight", storyHeight_mm, 1000.0, 20000.0)  // mm

        val entries = mutableListOf<BbsEntry>()

        // Span derivation: use storyHeight as span approximation if span not directly available
        val spanMm = if (storyHeight_mm > 0.0) storyHeight_mm else result.width * 1000.0

        // Main Bottom Rebar — computed span + 9φ hook each end (ECP 203 clause)
        val hookLen = 2 * 9 * result.reinforcementBottom.diameter  // 9φ hook per end × 2 ends
        val mainBarLen = spanMm + hookLen
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.reinforcementBottom.diameter,
            shapeCode = 0, count = result.reinforcementBottom.numBars,
            lengthA = mainBarLen, totalLengthPerBar = mainBarLen,
            totalWeightKg = result.reinforcementBottom.numBars * mainBarLen / 1000.0 * (result.reinforcementBottom.diameter.toDouble().pow(2) / 162.0)
        ))

        // Stirrups (Shape Code 51 - Rectangular link)
        val s = result.stirrups
        val a = result.width - 80.0 // clear width inside stirrup (2×40mm cover)
        val b = result.depth - 80.0 // clear depth inside stirrup
        InputGuard.require(a > 0.0, "Stirrup clear width must be positive (width=${result.width} > 80mm)")
        InputGuard.require(b > 0.0, "Stirrup clear depth must be positive (depth=${result.depth} > 80mm)")
        val stirrupLen = 2 * (a + b) + 200.0 // 2×(a+b) + 2×9φ hooks (≈200mm for φ8-φ10)

        var stirrupCount = 0
        result.stirrups.zones.forEach { zone ->
            val zoneLen = zone.endLocation - zone.startLocation
            if (zoneLen > 0 && zone.spacing > 0) {
                stirrupCount += (zoneLen / zone.spacing).toInt() + 1
            }
        }
        InputGuard.require(stirrupCount > 0, "Beam must have at least 1 stirrup zone with valid spacing")

        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.stirrups.diameter,
            shapeCode = 51, count = stirrupCount,
            lengthA = a, lengthB = b,
            totalLengthPerBar = stirrupLen,
            totalWeightKg = stirrupCount * stirrupLen / 1000.0 * (result.stirrups.diameter.toDouble().pow(2) / 162.0)
        ))

        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a designed Column.
     * @param storyHeight_mm Column story height in mm (default 3000mm = 3m)
     */
    fun generateColumnBbs(mark: String, result: ColumnResult, storyHeight_mm: Double = 3000.0): List<BbsEntry> {
        // ── InputGuard: حراس المدخلات — لا حساب على مدخلات غير صالحة ──
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("width", result.width)               // mm
        InputGuard.positive("depth", result.depth)               // mm
        InputGuard.positive("reinforcement.diameter", result.reinforcement.diameter)
        InputGuard.positive("reinforcement.numBars", result.reinforcement.numBars)
        InputGuard.positive("stirrups.diameter", result.stirrups.diameter)

        val entries = mutableListOf<BbsEntry>()

        // Height derivation: storyHeight or estimate from concreteVolume
        val h = if (storyHeight_mm > 0.0) storyHeight_mm else {
            val crossAreaMm2 = result.width * result.depth
            if (crossAreaMm2 > 0.0 && result.concreteVolume > 0.0) result.concreteVolume * 1e9 / crossAreaMm2 else 3000.0
        }
        InputGuard.inRange("columnHeight", h, 500.0, 30000.0)  // mm

        // Longitudinal Bars — full height + 60φ lap splice
        val mainLen = h + 60 * result.reinforcement.diameter
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.reinforcement.diameter,
            shapeCode = 0, count = result.reinforcement.numBars,
            lengthA = mainLen, totalLengthPerBar = mainLen,
            totalWeightKg = result.reinforcement.numBars * mainLen / 1000.0 * (result.reinforcement.diameter.toDouble().pow(2)/162.0)
        ))

        // Ties
        val a = result.width - 80.0
        val b = result.depth - 80.0
        InputGuard.require(a > 0.0, "Tie clear width must be positive (width=${result.width} > 80mm)")
        InputGuard.require(b > 0.0, "Tie clear depth must be positive (depth=${result.depth} > 80mm)")
        val tieLen = 2 * (a + b) + 200.0
        var tieCount = 0
        result.stirrups.zones.forEach { zone ->
            val zoneLen = zone.endLocation - zone.startLocation
            if (zoneLen > 0 && zone.spacing > 0) {
                tieCount += (zoneLen / zone.spacing).toInt() + 1
            }
        }
        if (tieCount == 0) tieCount = 1  // Minimum 1 tie
        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.stirrups.diameter,
            shapeCode = 51, count = tieCount,
            lengthA = a, lengthB = b, totalLengthPerBar = tieLen,
            totalWeightKg = tieCount * tieLen / 1000.0 * (result.stirrups.diameter.toDouble().pow(2)/162.0)
        ))
        return entries
    }

    /**
     * Generates a Bar Bending Schedule for a designed Isolated Footing.
     */
    fun generateFootingBbs(mark: String, result: FootingResult): List<BbsEntry> {
        // ── InputGuard: حراس المدخلات — لا حساب على مدخلات غير صالحة ──
        InputGuard.notBlank("memberMark", mark)
        InputGuard.positive("length", result.length)              // mm
        InputGuard.positive("width", result.width)               // mm
        InputGuard.positive("thickness", result.thickness)       // mm
        InputGuard.positive("barDiameter", result.barDiameter)
        InputGuard.positive("barsX", result.barsX)
        InputGuard.positive("barsY", result.barsY)

        val entries = mutableListOf<BbsEntry>()
        val lx = result.length
        val ly = result.width
        val t = result.thickness

        // Bottom X (Shape Code 21 - L bar): bar length = lx - 2×cover + 2×(t - cover) bend-up
        val coverMm = 75.0 // typical footing cover
        val bendUpLen = t - coverMm
        InputGuard.require(bendUpLen > 0, "Footing bend-up length must be positive (thickness > cover)")
        val lenX = (lx - 2 * coverMm) + 2 * bendUpLen
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.barDiameter,
            shapeCode = 21, count = result.barsX,
            lengthA = lx - 2 * coverMm, lengthB = bendUpLen,
            totalLengthPerBar = lenX,
            totalWeightKg = result.barsX * lenX / 1000.0 * (result.barDiameter.toDouble().pow(2)/162.0)
        ))

        // Bottom Y
        val lenY = (ly - 2 * coverMm) + 2 * bendUpLen
        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.barDiameter,
            shapeCode = 21, count = result.barsY,
            lengthA = ly - 2 * coverMm, lengthB = bendUpLen,
            totalLengthPerBar = lenY,
            totalWeightKg = result.barsY * lenY / 1000.0 * (result.barDiameter.toDouble().pow(2)/162.0)
        ))
        return entries
    }

    /**
     * AI Waste Minimization (Bin Packing Algorithm)
     */
    fun optimizeCutting(entries: List<BbsEntry>, stockLength: Double = 12000.0): String {
        InputGuard.positive("stockLength", stockLength)
        InputGuard.inRange("stockLength", stockLength, 6000.0, 18000.0)  // mm — standard bar lengths

        val sortedBars = entries.flatMap { entry -> List(entry.count) { entry.totalLengthPerBar } }
            .filter { it <= stockLength }
            .sortedDescending()

        if (sortedBars.isEmpty()) return "No valid bars to optimize."

        var stocksCount = 0
        val bins = mutableListOf<Double>()

        sortedBars.forEach { barLen ->
            var placed = false
            for (i in bins.indices) {
                if (bins[i] >= barLen) {
                    bins[i] -= barLen
                    placed = true
                    break
                }
            }
            if (!placed) {
                stocksCount++
                bins.add(stockLength - barLen)
            }
        }

        val totalUsed = sortedBars.sum()
        val totalBought = stocksCount * stockLength
        val efficiency = (totalUsed / totalBought) * 100.0

        return "Optimization Results: Use $stocksCount stock bars (12m). Site Efficiency: ${String.format(java.util.Locale.US, "%.1f", efficiency)}% (Waste: ${String.format(java.util.Locale.US, "%.1f", 100 - efficiency)}%)"
    }

    /**
     * Combines multiple elements into a single project-level BBS.
     */
    fun combineProjectBbs(allElements: List<List<BbsEntry>>): List<BbsEntry> {
        return allElements.flatten()
            .groupBy { "${it.diameter}-${it.shapeCode}-${it.lengthA}-${it.lengthB}" }
            .map { entry ->
                val first = entry.value.first()
                first.copy(
                    count = entry.value.sumOf { it.count },
                    totalWeightKg = entry.value.sumOf { it.totalWeightKg }
                )
            }
    }
}
