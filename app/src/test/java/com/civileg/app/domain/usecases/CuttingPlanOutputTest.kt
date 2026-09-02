package com.civileg.app.domain.usecases

import com.civileg.app.utils.BbsGenerator
import com.civileg.app.utils.BbsEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §33/§34 cutting-plan output contracts:
 *  - Cut list aggregation (piece length → count, longest first)
 *  - Cutting diagram (one deterministic line per stock bar)
 *  - BbsGenerator.optimizeCutting delegates to the canonical engine
 *    (single source §3) with correct mm→m unit conversion
 */
class CuttingPlanOutputTest {

    private val engine = AnalyzeRebarInventory()

    // ── Cut list ─────────────────────────────────────────────────────

    @Test fun cutList_aggregatesCounts_longestFirst() {
        val cuts = engine.buildCutList(listOf(3.2, 1.8, 3.2, 4.0, 1.8, 3.2))
        assertEquals(3, cuts.size)
        // longest first
        assertEquals(4.0, cuts[0].cutLengthM, 1e-9)
        assertEquals(1, cuts[0].count)
        assertEquals(4.0, cuts[0].totalLengthM, 1e-9)
        // 3.2m × 3
        assertEquals(3.2, cuts[1].cutLengthM, 1e-9)
        assertEquals(3, cuts[1].count)
        assertEquals(9.6, cuts[1].totalLengthM, 1e-9)
        // 1.8m × 2
        assertEquals(1.8, cuts[2].cutLengthM, 1e-9)
        assertEquals(2, cuts[2].count)
    }

    @Test fun cutList_emptyAndZeroInputs() {
        assertTrue(engine.buildCutList(emptyList()).isEmpty())
        // zero/negative lengths are dropped (workshop safety: never emit a 0m cut)
        assertTrue(engine.buildCutList(listOf(0.0, -1.0)).isEmpty())
    }

    @Test fun cutList_totalsMatchPlanPieces() {
        val lengths = listOf(2.5, 2.5, 6.0, 6.0, 6.0)
        val cuts = engine.buildCutList(lengths)
        assertEquals("cut-list total must equal raw pieces total",
            lengths.sum(), cuts.sumOf { it.totalLengthM }, 1e-9)
        assertEquals(lengths.size, cuts.sumOf { it.count })
    }

    // ── Cutting diagram ──────────────────────────────────────────────

    @Test fun diagram_oneLinePerBar_withWasteMarker() {
        val plans = engine.optimizeCuttingMultiLength(
            stockLength = 12.0,
            requiredLengths = List(7) { 3.2 }   // 3 per bar → bars of 3,3,1
        )
        assertEquals(3, plans.size)
        val lines = engine.buildCuttingDiagram(plans)
        assertEquals("one line per bar", plans.size, lines.size)
        lines.forEach { line ->
            assertTrue(line.startsWith("Bar "))
            assertTrue(line.contains("[#"))
            assertTrue("waste marker present", line.contains("waste="))
            assertTrue(line.contains("util "))
        }
        // Uniform path: 3 pieces/bar → 2 full bars + 1 bar holding 1 piece
        // (waste = 12 − 3.2 = 8.80 m)
        assertTrue(lines[2].contains("8.80"))
    }

    @Test fun diagram_invalidScale_returnsEmpty() {
        val plans = engine.optimizeCuttingMultiLength(12.0, listOf(3.0))
        assertTrue(engine.buildCuttingDiagram(plans, charsPerMeter = 0).isEmpty())
    }

    // ── BbsGenerator delegation (mm interface over m engine) ────────

    private fun entry(mark: String, count: Int, lenMm: Double) = BbsEntry(
        memberMark = mark, barMark = "01", diameter = 16, shapeCode = 0,
        count = count, lengthA = lenMm, totalLengthPerBar = lenMm,
        totalWeightKg = count * lenMm / 1000.0 * (256.0 / 162.0)
    )

    /** 5 bars × 4.0 m from 12 m stock → analytical 2/bar? No: (12+kerf)/(4+kerf)=2 → 3 bars. */
    @Test fun bbsOptimize_delegatesWithUnitConversion() {
        val summary = BbsGenerator.optimizeCutting(
            listOf(entry("B1", 5, 4000.0)),   // mm
            stockLength = 12000.0             // mm
        )
        // Engine: uniform path n=(12+0.003)/(4+0.003)=2 per bar → ceil(5/2)=3 bars
        assertTrue("summary mentions 3 stock bars: $summary", summary.contains("3 stock bars"))
        // efficiency = 20/36 = 55.6%
        assertTrue(summary.contains("Efficiency"))
    }

    @Test fun bbsOptimize_noValidBars_message() {
        val summary = BbsGenerator.optimizeCutting(
            listOf(entry("B1", 2, 15000.0)),  // longer than any stock
            stockLength = 12000.0
        )
        assertEquals("No valid bars to optimize.", summary)
    }
}
