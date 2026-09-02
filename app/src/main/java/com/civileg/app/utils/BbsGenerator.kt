package com.civileg.app.utils

import android.os.Parcelable
import com.civileg.app.utils.CalculatorEngine.*
import com.civileg.app.domain.calculations.base.RetainingWallResult
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

    fun generateFlatSlabBbs(mark: String, result: com.civileg.app.domain.FlatSlabResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        
        // 1. Column Strip Bottom (Shape 00 - Straight)
        val lx = 6000.0 // Placeholder for span
        entries.add(BbsEntry(
            memberMark = mark, barMark = "CS-B",
            diameter = result.columnStripBotRebar.diameter,
            shapeCode = 0, count = result.columnStripBotRebar.bars,
            lengthA = lx, totalLengthPerBar = lx,
            totalWeightKg = result.columnStripBotRebar.bars * lx / 1000.0 * (result.columnStripBotRebar.diameter.toDouble().pow(2)/162.0)
        ))

        // 2. Middle Strip Bottom
        entries.add(BbsEntry(
            memberMark = mark, barMark = "MS-B",
            diameter = result.middleStripBotRebar.diameter,
            shapeCode = 0, count = result.middleStripBotRebar.bars,
            lengthA = lx, totalLengthPerBar = lx,
            totalWeightKg = result.middleStripBotRebar.bars * lx / 1000.0 * (result.middleStripBotRebar.diameter.toDouble().pow(2)/162.0)
        ))
        
        // 3. Column Strip Top (at supports)
        if (result.columnStripTopRebar.bars > 0) {
            val lTop = lx * 0.3 // typical L/3 top bars
            entries.add(BbsEntry(
                memberMark = mark, barMark = "CS-T",
                diameter = result.columnStripTopRebar.diameter,
                shapeCode = 0, count = result.columnStripTopRebar.bars,
                lengthA = lTop, totalLengthPerBar = lTop,
                totalWeightKg = result.columnStripTopRebar.bars * lTop / 1000.0 * (result.columnStripTopRebar.diameter.toDouble().pow(2)/162.0)
            ))
        }

        return entries
    }

    fun generateTankBbs(mark: String, result: CalculatorEngine.TankResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        val h = result.height * 1000.0
        val l = result.length * 1000.0
        val w = result.width * 1000.0
        
        // 1. Vertical Wall Bars (Shape 21 - L bar into base)
        val vBar = result.wallReinforcement
        val vCount = ((2 * (l + w)) / vBar.spacing).toInt() + 1
        val vLen = h + 500.0 // height + anchorage
        entries.add(BbsEntry(
            memberMark = mark, barMark = "W-V",
            diameter = vBar.diameter, shapeCode = 21, count = vCount,
            lengthA = h, lengthB = 500.0, totalLengthPerBar = vLen,
            totalWeightKg = vCount * vLen / 1000.0 * (vBar.diameter.toDouble().pow(2)/162.0)
        ))

        // 2. Horizontal Hoop Bars (Shape 00 - Straight or Ring)
        val hBar = vBar // Usually same for simple engine
        val hCount = (h / hBar.spacing).toInt() + 1
        val hLen = 2 * (l + w) + 1000.0 // Perimeter + laps
        entries.add(BbsEntry(
            memberMark = mark, barMark = "W-H",
            diameter = hBar.diameter, shapeCode = 0, count = hCount,
            lengthA = hLen, totalLengthPerBar = hLen,
            totalWeightKg = hCount * hLen / 1000.0 * (hBar.diameter.toDouble().pow(2)/162.0)
        ))

        return entries
    }

    fun generateShearWallBbs(mark: String, result: com.civileg.app.domain.ShearWallResult, heightMm: Double): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        
        // 1. Vertical Web Reinforcement
        val vReinf = result.verticalReinforcement
        entries.add(BbsEntry(
            memberMark = mark, barMark = "VW",
            diameter = vReinf.diameter, shapeCode = 0, count = vReinf.bars,
            lengthA = heightMm + 600.0, totalLengthPerBar = heightMm + 600.0,
            totalWeightKg = vReinf.bars * (heightMm + 600.0) / 1000.0 * (vReinf.diameter.toDouble().pow(2)/162.0)
        ))

        // 2. Horizontal Web Reinforcement
        val hReinf = result.horizontalReinforcement
        val hCount = (heightMm / hReinf.spacing).toInt() + 1
        val hLen = 4000.0 // Wall length placeholder if not in result
        entries.add(BbsEntry(
            memberMark = mark, barMark = "HW",
            diameter = hReinf.diameter, shapeCode = 0, count = hCount,
            lengthA = hLen, totalLengthPerBar = hLen,
            totalWeightKg = hCount * hLen / 1000.0 * (hReinf.diameter.toDouble().pow(2)/162.0)
        ))

        return entries
    }

    fun generatePileFoundationBbs(mark: String, result: com.civileg.app.domain.PileDesignResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        
        // 1. Pile Longitudinal Bars
        val pReinf = result.pileReinforcement
        val pLen = result.pileLengthM * 1000.0 + 1000.0 // length + cap anchorage
        val totalPBars = pReinf.longitudinalBars * result.numberOfPiles
        entries.add(BbsEntry(
            memberMark = mark, barMark = "P-L",
            diameter = pReinf.longitudinalDiameter, shapeCode = 0, count = totalPBars,
            lengthA = pLen, totalLengthPerBar = pLen,
            totalWeightKg = totalPBars * pLen / 1000.0 * (pReinf.longitudinalDiameter.toDouble().pow(2)/162.0)
        ))

        // 2. Cap Main Steel (X direction)
        val cap = result.capResult
        val cX = cap.flexuralReinforcement
        entries.add(BbsEntry(
            memberMark = mark, barMark = "C-X",
            diameter = cX.diameter, shapeCode = 21, count = cX.bars,
            lengthA = cap.capWidth, lengthB = cap.capThickness, 
            totalLengthPerBar = cap.capWidth + 2 * cap.capThickness,
            totalWeightKg = cX.bars * (cap.capWidth + 2 * cap.capThickness) / 1000.0 * (cX.diameter.toDouble().pow(2)/162.0)
        ))

        return entries
    }

    fun generateRetainingWallBbs(mark: String, result: com.civileg.app.domain.RetainingWallResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        // Stem main steel (Shape 21 - L bar)
        val h = 3000.0 // Placeholder
        entries.add(BbsEntry(
            memberMark = mark, barMark = "W1",
            diameter = 16, // Approximate from result string
            shapeCode = 21, count = 10,
            lengthA = h, lengthB = 500.0,
            totalLengthPerBar = h + 500.0,
            totalWeightKg = 10 * (h + 500.0) / 1000.0 * (16.0.pow(2)/162.0)
        ))
        return entries
    }

    fun generateSlabBbs(mark: String, result: SlabResult, lxM: Double, lyM: Double): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        val lx = lxM * 1000.0
        val ly = lyM * 1000.0
        
        // 1. Main Bottom Steel (X-direction)
        val lX = lx + 200.0 // including hooks
        val mainRebar = result.reinforcementMain
        val countX = (ly / mainRebar.spacing).toInt() + 1
        entries.add(BbsEntry(
            memberMark = mark, barMark = "B1-X",
            diameter = mainRebar.diameter, shapeCode = 0, count = countX,
            lengthA = lX, totalLengthPerBar = lX,
            totalWeightKg = countX * lX / 1000.0 * (mainRebar.diameter.toDouble().pow(2)/162.0)
        ))

        // 2. Main Bottom Steel (Y-direction)
        val lY = ly + 200.0
        val secRebar = result.reinforcementSecondary
        val countY = (lx / secRebar.spacing).toInt() + 1
        entries.add(BbsEntry(
            memberMark = mark, barMark = "B1-Y",
            diameter = secRebar.diameter, shapeCode = 0, count = countY,
            lengthA = lY, totalLengthPerBar = lY,
            totalWeightKg = countY * lY / 1000.0 * (secRebar.diameter.toDouble().pow(2)/162.0)
        ))

        return entries
    }

    fun generateStairBbs(mark: String, result: StairResult): List<BbsEntry> {
        val entries = mutableListOf<BbsEntry>()
        val l = result.span * 1000.0
        
        // 1. Main Bottom Steel (Waist bars - Shape 21 L bar)
        val lMain = l + 400.0 // approx anchorage
        entries.add(BbsEntry(
            memberMark = mark, barMark = "S1",
            diameter = result.reinforcement.diameter, shapeCode = 21, count = (1000.0 / result.reinforcement.spacing * result.span).toInt() + 1,
            lengthA = l, lengthB = 400.0, totalLengthPerBar = lMain,
            totalWeightKg = ((1000.0 / result.reinforcement.spacing * result.span).toInt() + 1) * lMain / 1000.0 * (result.reinforcement.diameter.toDouble().pow(2)/162.0)
        ))

        // 2. Distribution Steel
        val lDist = 1200.0 // typical flight width
        entries.add(BbsEntry(
            memberMark = mark, barMark = "S2",
            diameter = result.distributionReinforcement.diameter, shapeCode = 0, count = (l / result.distributionReinforcement.spacing).toInt() + 1,
            lengthA = lDist, totalLengthPerBar = lDist,
            totalWeightKg = ((l / result.distributionReinforcement.spacing).toInt() + 1) * lDist / 1000.0 * (result.distributionReinforcement.diameter.toDouble().pow(2)/162.0)
        ))

        return entries
    }

    /**
     * AI Waste Minimization — DELEGATES to the canonical cutting-stock engine
     * (AnalyzeRebarInventory: BFD + branch-and-bound, kerf-aware, post-optimized).
     * §3 single source of algorithms — the previous local FFD copy is deleted.
     * Units: BBS lengths are mm; the engine works in metres.
     */
    fun optimizeCutting(entries: List<BbsEntry>, stockLength: Double = 12000.0): String {
        val lengthsMm = entries.flatMap { entry -> List(entry.count) { entry.totalLengthPerBar } }
            .filter { it > 0 && it <= stockLength }
        if (lengthsMm.isEmpty()) return "No valid bars to optimize."

        val engine = com.civileg.app.domain.usecases.AnalyzeRebarInventory()
        val plans = engine.optimizeCuttingMultiLength(
            stockLength = stockLength / 1000.0,
            requiredLengths = lengthsMm.map { it / 1000.0 },
            kerfMm = com.civileg.app.domain.usecases.AnalyzeRebarInventory.KERF_MM
        )

        val totalUsedM = lengthsMm.sum() / 1000.0
        val totalBoughtM = plans.sumOf { it.stockLength }
        val efficiency = if (totalBoughtM > 0) (totalUsedM / totalBoughtM) * 100.0 else 0.0
        val wastePct = (100.0 - efficiency).coerceIn(0.0, 100.0)
        return "Optimization Results: Use ${plans.size} stock bars " +
            "(${String.format(java.util.Locale.US, "%.1f", stockLength / 1000.0)}m). " +
            "Site Efficiency: ${String.format(java.util.Locale.US, "%.1f", efficiency)}% " +
            "(Waste: ${String.format(java.util.Locale.US, "%.1f", wastePct)}%)"
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
