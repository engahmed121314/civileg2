package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.CuttingPlan
import org.junit.Assert.*
import org.junit.Test

class AnalyzeRebarInventoryTest {

    // =====================================================================
    // The AnalyzeRebarInventory class uses @Inject but has a no-arg
    // constructor, so we instantiate it directly in JUnit tests.
    // CuttingPlan and CuttingOptimizationResult use @Parcelize which
    // depends on Android Parcelable — in JVM unit tests with the Android
    // stub JAR on the classpath, the classes can be instantiated but
    // Parcel operations are not called. We only test pure computation.
    // =====================================================================

    private val analyzer = AnalyzeRebarInventory()

    // ------------------------------------------------------------------
    // 1. Uniform lengths — all bars same length, verify optimal count
    // ------------------------------------------------------------------
    @Test
    fun testUniformLengths() {
        // 12m stock, need 8 pieces of 3m each
        // Optimal: floor((12 + 0.003) / (3 + 0.003)) = 3 pieces per bar
        // ceil(8 / 3) = 3 bars needed
        val requiredLengths = List(8) { 3.0 }
        val result = analyzer.optimizeCuttingMultiLength(
            stockLength = 12.0,
            requiredLengths = requiredLengths
        )

        assertTrue("Should produce at least 1 cutting plan", result.isNotEmpty())
        val totalPieces = result.sumOf { it.requiredLengths.size }
        assertEquals("All 8 pieces must be cut", 8, totalPieces)

        // With 3m pieces and 12m stock, we should get 3 pieces per bar (min 3 bars)
        // The optimizer may use 3 bars: [3,3,3], [3,3,3], [3,3]
        assertTrue("Should use at most 3 stock bars", result.size <= 3)

        // Verify each piece is 3.0m
        for (plan in result) {
            for (piece in plan.requiredLengths) {
                assertEquals(3.0, piece, 0.001)
            }
        }

        // Optimal packing is [3,3,3], [3,3,3], [3,3] → utilization 75%, 75%, 50%
        // Average = 66.7% by construction (4×3m cannot fit a 12m bar with kerf)
        val avgUtil = result.sumOf { it.utilizationPercentage } / result.size
        assertTrue("Average utilization should be >= 66% for this scenario, got ${"%.1f".format(avgUtil)}%", avgUtil >= 66.0)
    }

    // ------------------------------------------------------------------
    // 2. Mixed lengths — different lengths, verify pieces fit in stock
    // ------------------------------------------------------------------
    @Test
    fun testMixedLengths() {
        // Stock = 12m, mixed lengths: some long, some short
        val requiredLengths = listOf(7.0, 5.0, 5.0, 4.0, 4.0, 3.0, 2.0)
        val result = analyzer.optimizeCuttingMultiLength(
            stockLength = 12.0,
            requiredLengths = requiredLengths
        )

        assertTrue("Should produce cutting plans", result.isNotEmpty())

        // Verify total required length is accounted for
        val totalRequired = requiredLengths.sum()
        val totalCut = result.sumOf { it.requiredLengths.sum() }
        assertEquals("Total cut length must match required", totalRequired, totalCut, 0.01)

        // Verify no individual bar exceeds stock length
        for (plan in result) {
            val used = plan.requiredLengths.sum()
            assertTrue("Used length ($used) must be <= stock (12.0)",
                used <= 12.001) // small tolerance for kerf
        }

        // Verify all required pieces are present (accounting for rounding)
        val allPieces = result.flatMap { it.requiredLengths }.sorted()
        val sortedRequired = requiredLengths.sorted()
        for (i in sortedRequired.indices) {
            assertEquals("Piece $i length mismatch", sortedRequired[i], allPieces[i], 0.001)
        }
    }

    // ------------------------------------------------------------------
    // 3. Leftover reuse — verify leftover bars are used first
    // ------------------------------------------------------------------
    @Test
    fun testLeftoverReuse() {
        // Need 2 pieces of 3m and 1 piece of 4m (total 10m)
        // Available leftover: 7m bar
        // The 7m leftover should be used first: fit 4m + 3m = 7m (perfect)
        // Remaining: 1 piece of 3m → needs new stock bar
        val requiredLengths = listOf(3.0, 3.0, 4.0)
        val availableLeftovers = listOf(7.0)

        val result = analyzer.optimizeWithLeftovers(
            stockLength = 12.0,
            requiredLengths = requiredLengths,
            availableLeftovers = availableLeftovers
        )

        assertTrue("Should use the leftover", result.leftoverPiecesUsed >= 1)
        assertTrue("Should produce cutting plans", result.cuttingPlans.isNotEmpty())

        // Verify total pieces cut
        val totalPieces = result.cuttingPlans.sumOf { it.requiredLengths.size }
        assertEquals("All 3 pieces must be cut", 3, totalPieces)

        // Verify total required length is accounted for
        val totalCut = result.cuttingPlans.sumOf { it.requiredLengths.sum() }
        assertEquals(10.0, totalCut, 0.01)

        // Using the 7m leftover should reduce the number of new stock bars needed
        // Without leftovers: would need 1 bar (3+3+4=10 fits in 12m)
        // With leftovers: still 1 new bar since all 3 pieces fit in 7m + 3m leftover
        // But the key is: leftoverPiecesUsed >= 1
        assertTrue("Leftover pieces used should be >= 1", result.leftoverPiecesUsed >= 1)
    }

    // ------------------------------------------------------------------
    // 4. Multi-stock optimization — verify best stock length selected
    // ------------------------------------------------------------------
    @Test
    fun testMultiStockOptimization() {
        // Need pieces of 5.5m
        // Available stock: 12, 13, 14, 15, 18
        // Best stock: 12m (fits 2 pieces of 5.5m = 11m, waste 1m)
        // vs 15m (also fits 2, but wastes 4m)
        // vs 18m (fits 3 of 5.5m = 16.5m, waste 1.5m — only if we need >= 3)
        val requiredLengths = listOf(5.5, 5.5)  // 2 pieces

        val result = analyzer.optimizeMultiStock(
            requiredLengths = requiredLengths,
            stockLengths = listOf(12.0, 13.0, 14.0, 15.0, 18.0)
        )

        assertTrue("Should produce cutting plans", result.isNotEmpty())

        // With only 2 pieces of 5.5m, the optimizer should pick 12m stock
        // (2 × 5.5 = 11m < 12m, perfect fit, minimal waste)
        val totalPieces = result.sumOf { it.requiredLengths.size }
        assertEquals("All 2 pieces must be cut", 2, totalPieces)

        // The best stock should be 12m (smallest that fits 2 × 5.5 = 11m)
        // All plans should use stock length >= 11m
        for (plan in result) {
            assertTrue("Stock length (${plan.stockLength}) must fit longest piece (5.5m)",
                plan.stockLength >= 5.5)
        }

        // Verify minimum number of bars (should be 1)
        assertEquals("Should use 1 bar for 2 × 5.5m in 12m stock", 1, result.size)
    }

    // ------------------------------------------------------------------
    // 5. Empty input — verify empty input returns empty result
    // ------------------------------------------------------------------
    @Test
    fun testEmptyInput() {
        // optimizeCuttingMultiLength with empty list
        val cuttingResult = analyzer.optimizeCuttingMultiLength(
            stockLength = 12.0,
            requiredLengths = emptyList()
        )
        assertTrue("Empty input should return empty cutting plan", cuttingResult.isEmpty())

        // optimizeWithLeftovers with empty list
        val leftoverResult = analyzer.optimizeWithLeftovers(
            stockLength = 12.0,
            requiredLengths = emptyList(),
            availableLeftovers = listOf(5.0, 3.0)
        )
        assertEquals("Empty input should return 0 stock bars used", 0, leftoverResult.totalStockBarsUsed)
        assertEquals("Empty input should return 0 total required length", 0.0, leftoverResult.totalRequiredLength, 0.001)
        assertEquals("Empty input should return 0 waste", 0.0, leftoverResult.totalWasteLength, 0.001)

        // optimizeMultiStock with empty list
        val multiResult = analyzer.optimizeMultiStock(
            requiredLengths = emptyList(),
            stockLengths = listOf(12.0, 15.0)
        )
        assertTrue("Empty input should return empty multi-stock result", multiResult.isEmpty())

        // recommendStockLength with empty list
        val recommended = analyzer.recommendStockLength(
            requiredLengths = emptyList()
        )
        assertEquals("Empty input should return first stock length", 12.0, recommended, 0.001)
    }

    // ------------------------------------------------------------------
    // 6. Additional: recommendStockLength picks shortest adequate stock
    // ------------------------------------------------------------------
    @Test
    fun testRecommendStockLength() {
        val result = analyzer.recommendStockLength(
            requiredLengths = listOf(5.0, 6.0, 7.0),
            availableStockLengths = listOf(12.0, 13.0, 14.0, 15.0, 18.0)
        )
        // Longest piece is 7.0m, shortest stock >= 7.0 is 12.0
        assertEquals("Should recommend 12m (shortest stock >= 7m)", 12.0, result, 0.001)
    }

    // ------------------------------------------------------------------
    // 7. Additional: leftover reuse prefers larger leftovers first
    // ------------------------------------------------------------------
    @Test
    fun testLeftoverReuseMultiple() {
        // Need 5 pieces of 2m each (total 10m)
        // Leftovers: 5m, 4.5m, 3m
        // 5m can fit 2 × 2m = 4m
        // 4.5m can fit 2 × 2m = 4m
        // 3m can fit 1 × 2m = 2m
        // Total from leftovers: 5 pieces — no new stock needed!
        val requiredLengths = List(5) { 2.0 }
        val availableLeftovers = listOf(5.0, 4.5, 3.0)

        val result = analyzer.optimizeWithLeftovers(
            stockLength = 12.0,
            requiredLengths = requiredLengths,
            availableLeftovers = availableLeftovers
        )

        assertEquals("All 5 pieces should be cut from leftovers", 5,
            result.cuttingPlans.sumOf { it.requiredLengths.size })
        assertTrue("Should use at least 2 leftovers", result.leftoverPiecesUsed >= 2)
        // Since all 5 pieces (10m total) fit in 5+4.5+3=12.5m of leftovers, new stock = 0
        val newStockPlans = result.cuttingPlans.filter { it.stockLength == 12.0 }
        assertEquals("No new stock bars should be needed", 0, newStockPlans.size)
    }
}
