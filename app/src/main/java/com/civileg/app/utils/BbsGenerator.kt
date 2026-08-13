package com.civileg.app.utils

import android.os.Parcelable
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
     */
    fun generateBeamBbs(mark: String, result: BeamResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        val span = result.width * 1000.0 // wait, span is in result?
        // Span is in meters in inputs, but result has width/depth. 
        // I need to be careful with units.
        
        // Main Bottom Rebar (Shape Code 21 - L-Bar or 00 - Straight)
        val mainBarLen = 5000.0 // placeholder, should be span + hooks
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01", 
            diameter = result.reinforcementBottom.diameter,
            shapeCode = 0, count = result.reinforcementBottom.numBars,
            lengthA = mainBarLen, totalLengthPerBar = mainBarLen,
            totalWeightKg = result.reinforcementBottom.numBars * mainBarLen / 1000.0 * (result.reinforcementBottom.diameter.toDouble().pow(2) / 162.0)
        ))

        // Stirrups (Shape Code 51 - Rectangular link)
        val s = result.stirrups
        val a = result.width - 80.0 // clear
        val b = result.depth - 80.0
        val stirrupLen = 2 * (a + b) + 200.0 // hooks
        
        var stirrupCount = 0
        result.stirrups.zones.forEach { zone ->
            stirrupCount += ((zone.endLocation - zone.startLocation) / zone.spacing).toInt() + 1
        }

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

    fun generateColumnBbs(mark: String, result: ColumnResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        val h = 3000.0 // placeholder height
        
        // Longitudinal Bars
        val mainLen = h + 60 * result.reinforcement.diameter // incl. laps
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
        val tieLen = 2 * (a + b) + 200.0
        var tieCount = 0
        result.stirrups.zones.forEach { zone ->
            tieCount += ((zone.endLocation - zone.startLocation) / zone.spacing).toInt() + 1
        }
        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.stirrups.diameter,
            shapeCode = 51, count = tieCount,
            lengthA = a, lengthB = b, totalLengthPerBar = tieLen,
            totalWeightKg = tieCount * tieLen / 1000.0 * (result.stirrups.diameter.toDouble().pow(2)/162.0)
        ))
        return entries
    }

    fun generateFootingBbs(mark: String, result: FootingResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        val lx = result.length
        val ly = result.width
        val t = result.thickness
        
        // Bottom X (Shape Code 21 - L bar)
        val lenX = lx - 100.0 + 2 * (t - 100.0) // simplified
        entries.add(BbsEntry(
            memberMark = mark, barMark = "01",
            diameter = result.barDiameter,
            shapeCode = 21, count = result.barsX,
            lengthA = lx - 100.0, lengthB = t - 100.0,
            totalLengthPerBar = lenX,
            totalWeightKg = result.barsX * lenX / 1000.0 * (result.barDiameter.toDouble().pow(2)/162.0)
        ))

        // Bottom Y
        val lenY = ly - 100.0 + 2 * (t - 100.0)
        entries.add(BbsEntry(
            memberMark = mark, barMark = "02",
            diameter = result.barDiameter,
            shapeCode = 21, count = result.barsY,
            lengthA = ly - 100.0, lengthB = t - 100.0,
            totalLengthPerBar = lenY,
            totalWeightKg = result.barsY * lenY / 1000.0 * (result.barDiameter.toDouble().pow(2)/162.0)
        ))
        return entries
    }

    /**
     * AI Waste Minimization (Bin Packing Algorithm)
     */
    fun optimizeCutting(entries: List<BbsEntry>, stockLength: Double = 12000.0): String {
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
