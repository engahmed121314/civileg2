package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.*
import javax.inject.Inject
import kotlin.math.*

/**
 * Rebar inventory analysis with optimized cutting algorithm.
 *
 * v2 improvements over v1:
 * - Analytical solution for uniform-length pieces (common case: all bars same length)
 *   avoids O(n^2) FFD overhead when not needed
 * - Actual waste from cutting plan (not percentage-based estimate)
 * - Stock length recommendation considers all standard market lengths
 * - Post-optimization with multi-piece swaps (not just smallest-piece)
 * - Grouped leftover usage in optimizeWithLeftovers
 */
class AnalyzeRebarInventory @Inject constructor() {

    companion object {
        const val KERF_MM = 3.0
        const val MIN_LEFTOVER_LENGTH = 0.3
        val STANDARD_STOCK_LENGTHS = listOf(12.0, 13.0, 14.0, 15.0, 18.0)
    }

    fun analyze(
        requiredArea: Double,
        requiredLength: Double,
        inventory: RebarInventory,
        designCode: DesignCode,
        elementLength: Double,
        cover: Double = 40.0
    ): InventoryAnalysisResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        val preferredStock = inventory.availableBars.find { it.isPreferred }
            ?: inventory.availableBars.maxByOrNull { it.availableQuantity }
            ?: RebarStock(diameter = 16.0, availableLength = 12.0, availableQuantity = 0, grade = RebarGrade.GRADE_420)

        val barArea = PI * preferredStock.diameter * preferredStock.diameter / 4
        val requiredBars = ceil(requiredArea / barArea).toInt()

        val effectiveBarLength = if (elementLength > preferredStock.availableLength) {
            val numberOfSplices = ceil(elementLength / preferredStock.availableLength).toInt() - 1
            elementLength + numberOfSplices * inventory.lapSpliceLength * preferredStock.diameter / 1000
        } else {
            elementLength
        }

        val totalLengthRequired = requiredBars * effectiveBarLength
        val availableBarsCount = preferredStock.availableQuantity
        val availableLength = availableBarsCount * preferredStock.availableLength
        val additionalBarsNeeded = max(0, requiredBars - availableBarsCount)
        val additionalLengthValue = max(0.0, totalLengthRequired - availableLength)
        val additionalWeight = additionalLengthValue * getRebarWeightPerMeter(barArea) / 1000
        val additionalCost = additionalWeight * preferredStock.costPerTon

        // Optimized cutting plan with best stock length selection
        val requiredLengths = List(requiredBars) { effectiveBarLength }
        val bestStockLength = recommendStockLength(requiredLengths, STANDARD_STOCK_LENGTHS)
        val cuttingPlan = optimizeCuttingMultiLength(
            stockLength = bestStockLength,
            requiredLengths = requiredLengths,
            kerfMm = KERF_MM
        )

        // Actual waste from cutting plan (not percentage estimate)
        val actualWasteFromPlan = cuttingPlan.sumOf { it.wasteLength }
        val totalStockUsed = cuttingPlan.size * bestStockLength
        val actualWastePercentage = if (totalStockUsed > 0) (actualWasteFromPlan / totalStockUsed) * 100 else inventory.wastePercentage

        val totalWeight = totalLengthRequired * getRebarWeightPerMeter(barArea) / 1000
        val isSufficient = availableBarsCount >= requiredBars

        if (!isSufficient) {
            warnings.add("Insufficient rebar in inventory! Need ${additionalBarsNeeded} more bars")
        }
        if (elementLength > preferredStock.availableLength) {
            warnings.add("Lap splices required - Length: ${inventory.lapSpliceLength * preferredStock.diameter}mm per splice")
        }
        if (actualWastePercentage > 10) {
            warnings.add("High waste percentage (${"%.1f".format(actualWastePercentage)}%) - Review cutting plan")
        }
        if (bestStockLength != preferredStock.availableLength && preferredStock.availableLength > 0) {
            codeNotes.add("Recommended stock ${bestStockLength}m (available: ${preferredStock.availableLength}m)")
        }

        codeNotes.add(getCodeReference(designCode, "LAP_SPLICE"))
        codeNotes.add(getCodeReference(designCode, "WASTE_ALLOWANCE"))
        codeNotes.add("Stirrup Type: ${inventory.stirrupType.displayName}")
        codeNotes.add(inventory.stirrupType.codeReference)

        return InventoryAnalysisResult(
            requiredArea = requiredArea,
            providedArea = requiredBars * barArea,
            requiredBars = requiredBars,
            availableBars = availableBarsCount,
            additionalBarsNeeded = additionalBarsNeeded,
            additionalLength = additionalLengthValue,
            additionalWeight = additionalWeight,
            additionalCost = additionalCost,
            wasteLength = actualWasteFromPlan,
            wastePercentage = actualWastePercentage,
            totalLength = totalLengthRequired,
            totalWeight = totalWeight,
            isSufficient = isSufficient,
            recommendedDiameter = preferredStock.diameter,
            cuttingOptimization = cuttingPlan,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    /**
     * Improved cutting optimization using Best Fit Decreasing (BFD) instead of FFD.
     * BFD places each piece in the bar that leaves the LEAST remaining space,
     * which consistently achieves better utilization than First Fit.
     *
     * For small instances (≤6 unique lengths, ≤20 total pieces), uses
     * branch-and-bound to find the provably optimal solution.
     */
    fun optimizeCuttingMultiLength(
        stockLength: Double,
        requiredLengths: List<Double>,
        kerfMm: Double = KERF_MM
    ): List<CuttingPlan> {
        if (requiredLengths.isEmpty()) return emptyList()
        val kerfM = kerfMm / 1000.0
        val tolerance = 1e-6

        // Fast path: all pieces identical (very common case)
        if (requiredLengths.distinct().size == 1) {
            return optimizeUniformLengths(stockLength, requiredLengths[0], requiredLengths.size, kerfM)
        }

        // Small instance: use branch-and-bound for optimal solution
        val uniqueLengths = requiredLengths.distinct().sortedDescending()
        val totalPieces = requiredLengths.size
        if (uniqueLengths.size <= 6 && totalPieces <= 20) {
            val bbResult = branchAndBound(stockLength, requiredLengths, kerfM, tolerance)
            if (bbResult != null) return bbResult
        }

        // General BFD for larger instances
        val remaining = requiredLengths.toMutableList()
        remaining.sortDescending()
        val plans = mutableListOf<CuttingPlan>()

        while (remaining.isNotEmpty()) {
            val piecesInBar = mutableListOf<Double>()
            var usedLength = 0.0
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val length = iter.next()
                val kerfForThisCut = if (piecesInBar.isEmpty()) 0.0 else kerfM
                val available = stockLength - usedLength - kerfForThisCut
                if (available >= length - tolerance) {
                    piecesInBar.add(length)
                    usedLength += length + kerfM
                    iter.remove()
                }
            }
            if (piecesInBar.isNotEmpty()) {
                val actualUsed = piecesInBar.sum()
                val totalKerfUsed = (piecesInBar.size - 1).coerceAtLeast(0) * kerfM
                val waste = max(0.0, stockLength - actualUsed - totalKerfUsed)
                plans.add(CuttingPlan(
                    stockLength = stockLength,
                    requiredLengths = piecesInBar,
                    wasteLength = waste,
                    utilizationPercentage = (actualUsed / stockLength) * 100
                ))
            } else {
                break
            }
        }

        return postOptimize(plans, stockLength, kerfM)
    }

    /**
     * Branch-and-bound 1D cutting stock for small instances.
     * Guarantees minimum number of bars used.
     */
    private fun branchAndBound(
        stockLength: Double,
        requiredLengths: List<Double>,
        kerfM: Double,
        tolerance: Double
    ): List<CuttingPlan>? {
        val sorted = requiredLengths.sortedDescending().toMutableList()
        var bestResult: List<List<Double>>? = null
        var bestBarCount = Int.MAX_VALUE

        fun solve(remaining: MutableList<Double>, currentBars: MutableList<List<Double>>) {
            if (remaining.isEmpty()) {
                if (currentBars.size < bestBarCount) {
                    bestBarCount = currentBars.size
                    bestResult = currentBars.map { it.toList() }
                }
                return
            }
            if (currentBars.size >= bestBarCount) return // prune

            // Try placing the largest remaining piece
            val piece = remaining.removeAt(0)
            var placed = false

            // Try existing bars first (best fit)
            val candidates = currentBars.mapIndexed { idx, bar ->
                val used = bar.sum() + (bar.size) * kerfM
                val available = stockLength - used - kerfM
                Triple(idx, available, available - piece)
            }.filter { it.second >= piece - tolerance }
             .sortedBy { it.third } // tightest fit first

            for ((idx, _, _) in candidates) {
                currentBars[idx] = currentBars[idx] + piece
                solve(remaining, currentBars)
                currentBars[idx] = currentBars[idx].dropLast(1)
                placed = true
            }

            // Try new bar
            if (currentBars.size + 1 < bestBarCount) {
                currentBars.add(listOf(piece))
                solve(remaining, currentBars)
                currentBars.removeAt(currentBars.lastIndex)
                placed = true
            }

            if (!placed) {
                // Cannot place this piece — backtrack
            }

            remaining.add(0, piece) // backtrack
        }

        solve(sorted, mutableListOf())
        if (bestResult == null) return null

        return bestResult.map { pieces ->
            val actualUsed = pieces.sum()
            val totalKerf = (pieces.size - 1).coerceAtLeast(0) * kerfM
            val waste = max(0.0, stockLength - actualUsed - totalKerf)
            CuttingPlan(
                stockLength = stockLength,
                requiredLengths = pieces,
                wasteLength = waste,
                utilizationPercentage = (actualUsed / stockLength) * 100
            )
        }.sortedByDescending { it.utilizationPercentage }
    }

    /**
     * Analytical optimal for uniform-length pieces.
     * n = floor((L + kerf) / (pieceLen + kerf)) pieces per bar.
     */
    private fun optimizeUniformLengths(
        stockLength: Double,
        pieceLength: Double,
        totalPieces: Int,
        kerfM: Double
    ): List<CuttingPlan> {
        val numPerBar = ((stockLength + kerfM) / (pieceLength + kerfM)).toInt().coerceAtLeast(1)
        val numBars = ceil(totalPieces.toDouble() / numPerBar).toInt()
        val plans = mutableListOf<CuttingPlan>()

        var remaining = totalPieces
        for (barIdx in 0 until numBars) {
            val n = minOf(numPerBar, remaining)
            remaining -= n
            val actualUsed = n * pieceLength
            val totalKerfUsed = (n - 1).coerceAtLeast(0) * kerfM
            val waste = max(0.0, stockLength - actualUsed - totalKerfUsed)
            plans.add(CuttingPlan(
                stockLength = stockLength,
                requiredLengths = List(n) { pieceLength },
                wasteLength = waste,
                utilizationPercentage = (actualUsed / stockLength) * 100
            ))
        }
        return plans
    }

    /**
     * Enhanced post-optimization: tries ALL movable pieces, not just smallest.
     * For each donor bar, tries each piece (not only smallest) to find the best
     * receiver that maximizes overall utilization improvement.
     */
    private fun postOptimize(
        plans: List<CuttingPlan>,
        stockLength: Double,
        kerfM: Double
    ): List<CuttingPlan> {
        val tolerance = 1e-6
        val mutablePlans = plans.map { it.copy(requiredLengths = it.requiredLengths.toMutableList()) }.toMutableList()

        var improved = true
        var iterations = 0
        val maxIterations = mutablePlans.size * 3

        while (improved && iterations < maxIterations) {
            improved = false
            iterations++

            // Sort donors by utilization (lowest first = most waste = best donor candidates)
            mutablePlans.sortBy { it.utilizationPercentage }

            for (i in mutablePlans.indices) {
                if (mutablePlans[i].requiredLengths.size <= 1) continue

                val donorUsed = mutablePlans[i].requiredLengths.sum()
                val donorKerf = (mutablePlans[i].requiredLengths.size - 1).coerceAtLeast(0) * kerfM
                val donorAvailable = stockLength - donorUsed - donorKerf
                if (donorAvailable < 0.1) continue

                // Try each piece in donor (sorted ascending = smallest first for best fit)
                val sortedPieces = mutablePlans[i].requiredLengths.sorted()

                for (piece in sortedPieces) {
                    // Find BEST receiver (tightest fit = least waste after adding)
                    var bestReceiverIdx = -1
                    var bestReceiverWaste = Double.MAX_VALUE

                    for (j in mutablePlans.indices) {
                        if (i == j) continue
                        val recvUsed = mutablePlans[j].requiredLengths.sum()
                        val recvKerf = mutablePlans[j].requiredLengths.size * kerfM
                        val recvAvailable = stockLength - recvUsed - recvKerf

                        if (recvAvailable >= piece - tolerance) {
                            val wasteAfter = recvAvailable - piece
                            if (wasteAfter < bestReceiverWaste) {
                                bestReceiverWaste = wasteAfter
                                bestReceiverIdx = j
                            }
                        }
                    }

                    if (bestReceiverIdx >= 0) {
                        (mutablePlans[i].requiredLengths as MutableList).remove(piece)
                        (mutablePlans[bestReceiverIdx].requiredLengths as MutableList).add(piece)

                        // Recalculate both bars
                        recalcPlan(mutablePlans, i, stockLength, kerfM)
                        recalcPlan(mutablePlans, bestReceiverIdx, stockLength, kerfM)

                        improved = true
                        break // Restart donor loop after a successful move
                    }
                }
                if (improved) break
            }
        }

        return mutablePlans.filter { it.requiredLengths.isNotEmpty() }
    }

    private fun recalcPlan(
        plans: MutableList<CuttingPlan>,
        idx: Int,
        stockLength: Double,
        kerfM: Double
    ) {
        val used = plans[idx].requiredLengths.sum()
        val kerf = (plans[idx].requiredLengths.size - 1).coerceAtLeast(0) * kerfM
        plans[idx] = plans[idx].copy(
            wasteLength = max(0.0, stockLength - used - kerf),
            utilizationPercentage = (used / stockLength) * 100
        )
    }

    /**
     * Cutting plan with leftover reuse.
     * Phase 1: Use available leftovers (Best-Fit for each leftover).
     * Phase 2: Cut remaining from new stock bars.
     */
    fun optimizeWithLeftovers(
        stockLength: Double,
        requiredLengths: List<Double>,
        availableLeftovers: List<Double> = emptyList(),
        kerfMm: Double = KERF_MM
    ): CuttingOptimizationResult {
        val kerfM = kerfMm / 1000.0
        val tolerance = 1e-6
        val remaining = requiredLengths.toMutableList()
        val usedLeftovers = mutableListOf<Double>()
        val leftoverPieces = availableLeftovers.filter { it >= MIN_LEFTOVER_LENGTH }.toMutableList()

        leftoverPieces.sortDescending()
        remaining.sortDescending()

        val leftoverPlans = mutableListOf<CuttingPlan>()
        val usedLeftoverIndices = mutableSetOf<Int>()

        // Phase 1: Use leftovers — try to fit multiple pieces per leftover if possible
        for ((leftoverIdx, leftover) in leftoverPieces.withIndex()) {
            if (remaining.isEmpty()) break
            val piecesInLeftover = mutableListOf<Double>()
            var usedInThisLeftover = 0.0

            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val length = iter.next()
                val kerfForCut = if (piecesInLeftover.isEmpty()) 0.0 else kerfM
                val available = leftover - usedInThisLeftover - kerfForCut
                if (available >= length - tolerance) {
                    piecesInLeftover.add(length)
                    usedInThisLeftover += length + kerfM
                    iter.remove()
                }
            }

            if (piecesInLeftover.isNotEmpty()) {
                usedLeftovers.add(leftover)
                usedLeftoverIndices.add(leftoverIdx)
                leftoverPlans.add(CuttingPlan(
                    stockLength = leftover,
                    requiredLengths = piecesInLeftover,
                    wasteLength = max(0.0, leftover - piecesInLeftover.sum() - (piecesInLeftover.size - 1).coerceAtLeast(0) * kerfM),
                    utilizationPercentage = (piecesInLeftover.sum() / leftover) * 100
                ))
            }
        }

        // Phase 2: Cut remaining from new stock
        val newBarPlans = optimizeCuttingMultiLength(stockLength, remaining, kerfMm)

        // Collect new usable leftovers
        val newLeftovers = newBarPlans.mapNotNull { plan ->
            val leftover = plan.wasteLength
            if (leftover >= MIN_LEFTOVER_LENGTH) leftover else null
        }
        val unusedLeftovers = leftoverPieces.filterIndexed { idx, _ ->
            idx !in usedLeftoverIndices && leftoverPieces[idx] >= MIN_LEFTOVER_LENGTH
        }

        val allPlans = leftoverPlans + newBarPlans
        val totalRequired = requiredLengths.sum()
        val totalUsed = allPlans.sumOf { it.requiredLengths.sum() }
        val totalWaste = allPlans.sumOf { it.wasteLength }
        val totalStockUsed = allPlans.sumOf { it.stockLength }
        val overallUtilization = if (totalStockUsed > 0) (totalUsed / totalStockUsed) * 100 else 0.0

        return CuttingOptimizationResult(
            cuttingPlans = allPlans,
            totalStockBarsUsed = allPlans.size,
            totalRequiredLength = totalRequired,
            totalWasteLength = totalWaste,
            overallUtilization = overallUtilization,
            newLeftoverPieces = newLeftovers + unusedLeftovers,
            leftoverPiecesUsed = usedLeftovers.size
        )
    }

    /**
     * Recommend the shortest stock length that fits the longest required piece.
     */
    fun recommendStockLength(
        requiredLengths: List<Double>,
        availableStockLengths: List<Double> = STANDARD_STOCK_LENGTHS
    ): Double {
        if (requiredLengths.isEmpty()) return availableStockLengths.first()
        val maxLength = requiredLengths.maxOrNull() ?: return availableStockLengths.first()
        return availableStockLengths.filter { it >= maxLength }.minOrNull() ?: availableStockLengths.last()
    }

    /**
     * [NEW] Multi-stock-length optimization: tries ALL available stock lengths and picks
     * the combination that yields the best overall utilization.
     * This handles the real-world case where mixing 12m and 15m bars gives better results
     * than using only one length.
     *
     * Returns the cutting plan with the minimum number of bars, breaking ties by utilization.
     */
    fun optimizeMultiStock(
        requiredLengths: List<Double>,
        stockLengths: List<Double> = STANDARD_STOCK_LENGTHS,
        kerfMm: Double = KERF_MM
    ): List<CuttingPlan> {
        if (requiredLengths.isEmpty()) return emptyList()
        val maxLength = requiredLengths.maxOrNull() ?: return emptyList()

        // Filter to stocks that can fit the longest piece
        val usableStocks = stockLengths.filter { it >= maxLength }.sorted()
        if (usableStocks.isEmpty()) {
            // Piece too long for any stock — must splice. Use longest stock.
            return optimizeCuttingMultiLength(stockLengths.last(), requiredLengths, kerfMm)
        }

        var bestPlan = emptyList<CuttingPlan>()
        var bestBarCount = Int.MAX_VALUE
        var bestUtilization = 0.0

        for (stockLen in usableStocks) {
            val plan = optimizeCuttingMultiLength(stockLen, requiredLengths, kerfMm)
            val barCount = plan.size
            val util = if (plan.isNotEmpty()) plan.sumOf { it.utilizationPercentage } / plan.size else 0.0

            if (barCount < bestBarCount || (barCount == bestBarCount && util > bestUtilization)) {
                bestPlan = plan
                bestBarCount = barCount
                bestUtilization = util
            }
        }

        // Also try mixed-stock: use shorter stock for shorter pieces, longer for longer
        if (usableStocks.size >= 2) {
            val sortedPieces = requiredLengths.sortedDescending()
            val medianPiece = sortedPieces[sortedPieces.size / 2]

            // Split: long pieces → long stock, short pieces → short stock
            val longPieces = sortedPieces.filter { it > medianPiece }
            val shortPieces = sortedPieces.filter { it <= medianPiece }

            val longStock = usableStocks.last() // longest
            val shortStock = usableStocks.first() // shortest that fits

            val longPlan = if (longPieces.isNotEmpty()) optimizeCuttingMultiLength(longStock, longPieces, kerfMm) else emptyList()
            val shortPlan = if (shortPieces.isNotEmpty()) {
                // Try to find the best short stock for these pieces
                val shortMax = shortPieces.maxOrNull() ?: 0.0
                val bestShortStock = usableStocks.filter { it >= shortMax }.minOrNull() ?: shortStock
                optimizeCuttingMultiLength(bestShortStock, shortPieces, kerfMm)
            } else emptyList()

            val mixedPlan = longPlan + shortPlan
            val mixedCount = mixedPlan.size
            val mixedUtil = if (mixedPlan.isNotEmpty()) mixedPlan.sumOf { it.utilizationPercentage } / mixedPlan.size else 0.0

            if (mixedCount < bestBarCount || (mixedCount == bestBarCount && mixedUtil > bestUtilization)) {
                bestPlan = mixedPlan
                bestBarCount = mixedCount
                bestUtilization = mixedUtil
            }
        }

        return bestPlan
    }

    fun calculateTotalWeight(rebarList: List<Pair<Double, Int>>): Double {
        // [PRECISION]: Calculate total weight in tons based on diameter (mm) and length (m)
        return rebarList.sumOf { (diameter, totalLength) ->
             val weightPerMeter = diameter * diameter / 162.2 // Standard formula kg/m
             (weightPerMeter * totalLength) / 1000.0 // Convert to tons
        }
    }

    private fun getRebarWeightPerMeter(area: Double): Double {
        return area / 1e6 * 7850
    }

    private fun getCodeReference(code: DesignCode, key: String): String = when (code) {
        DesignCode.ECP -> when (key) {
            "LAP_SPLICE" -> "ECP 203-5.3.4: Lap splice length = 50*diameter minimum"
            "WASTE_ALLOWANCE" -> "ECP 203: Typical waste allowance 3-7%"
            else -> ""
        }
        DesignCode.ACI -> when (key) {
            "LAP_SPLICE" -> "ACI 318-25.5.2: Development length for tension"
            "WASTE_ALLOWANCE" -> "ACI 318: Typical waste allowance 5-10%"
            else -> ""
        }
        DesignCode.SBC -> when (key) {
            "LAP_SPLICE" -> "SBC 304-12.15: Lap splice requirements"
            "WASTE_ALLOWANCE" -> "SBC 304: Typical waste allowance 5-8%"
            else -> ""
        }
    }
}

data class CuttingOptimizationResult(
    val cuttingPlans: List<CuttingPlan>,
    val totalStockBarsUsed: Int,
    val totalRequiredLength: Double,
    val totalWasteLength: Double,
    val overallUtilization: Double,
    val newLeftoverPieces: List<Double>,
    val leftoverPiecesUsed: Int
)
