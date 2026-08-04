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

    /**
     * BS 8666 Shape Code Definitions (Simplified)
     * 00: Straight
     * 11: 90 degree bend
     * 21: L-Bar (A + B)
     * 51: Rectangular Link / Stirrup
     */
}
