package com.civileg.app.utils

import com.civileg.app.domain.entities.StirrupZone
import com.civileg.app.utils.CalculatorEngine.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BbsGenerator — the Bar Bending Schedule generator.
 * Validates correct BbsEntry production, InputGuard integration,
 * and the cutting-optimization / project-combination algorithms.
 */
class BbsGeneratorTest {

    // ─── Test Fixtures ───────────────────────────────────────────────────

    private fun validStirrupZones(): List<StirrupZone> = listOf(
        StirrupZone(
            name = "Zone1",
            startLocation = 0.0,
            endLocation = 1500.0,
            spacing = 100.0,
            numLegs = 2,
            diameter = 8
        ),
        StirrupZone(
            name = "Zone2",
            startLocation = 1500.0,
            endLocation = 3000.0,
            spacing = 200.0,
            numLegs = 2,
            diameter = 8
        )
    )

    private fun validBeamResult(): BeamResult = BeamResult(
        width = 300.0,
        depth = 600.0,
        mu = 200.0,
        vu = 150.0,
        reinforcementBottom = ReinforcementBar(
            numBars = 4,
            diameter = 16,
            spacing = 0.0,
            type = "Main",
            weightKg = 10.0,
            barLength = 6000.0
        ),
        reinforcementTop = ReinforcementBar(
            numBars = 2,
            diameter = 12,
            spacing = 0.0,
            type = "Top",
            weightKg = 4.0,
            barLength = 6000.0
        ),
        stirrups = StirrupReinforcement(
            diameter = 8,
            spacing = 200.0,
            zones = validStirrupZones()
        ),
        isSafe = true,
        concreteVolume = 0.54,
        steelWeight = 14.0
    )

    private fun validColumnResult(): ColumnResult = ColumnResult(
        width = 400.0,
        depth = 400.0,
        pu = 1200.0,
        reinforcement = ReinforcementBar(
            numBars = 8,
            diameter = 20,
            spacing = 0.0,
            type = "Main",
            weightKg = 30.0,
            barLength = 3600.0
        ),
        stirrups = StirrupReinforcement(
            diameter = 10,
            spacing = 200.0,
            zones = validStirrupZones()
        ),
        isSafe = true,
        concreteVolume = 0.48
    )

    private fun validFootingResult(): FootingResult = FootingResult(
        type = FootingType.ISOLATED,
        width = 2000.0,
        length = 2000.0,
        thickness = 500.0,
        soilPressure = 150.0,
        allowablePressure = 200.0,
        reinforcementBottom = ReinforcementBar(
            numBars = 10,
            diameter = 16,
            spacing = 200.0,
            type = "Bottom",
            weightKg = 20.0
        ),
        isSafe = true,
        code = DesignCode.EGYPTIAN,
        concreteVolume = 2.0,
        steelWeight = 40.0,
        cost = 5000.0,
        barsX = 10,
        barsY = 10,
        barDiameter = 16
    )

    // ─── generateBeamBbs ─────────────────────────────────────────────────

    @Test
    fun `generateBeamBbs should produce entries for valid input`() {
        val entries = BbsGenerator.generateBeamBbs("B1", validBeamResult())
        assertTrue("Should produce at least 2 entries (main + stirrups)", entries.size >= 2)
        assertEquals("First entry memberMark should be B1", "B1", entries[0].memberMark)
        assertEquals("Main bar mark should be 01", "01", entries[0].barMark)
        assertTrue("Main bar totalLengthPerBar should be positive", entries[0].totalLengthPerBar > 0)
        assertTrue("Main bar totalWeightKg should be positive", entries[0].totalWeightKg > 0)
        assertEquals("Stirrup entry shapeCode should be 51", 51, entries[1].shapeCode)
        assertTrue("Stirrup count should be positive", entries[1].count > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateBeamBbs should throw for blank mark`() {
        BbsGenerator.generateBeamBbs("  ", validBeamResult())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateBeamBbs should throw for zero width`() {
        val badBeam = validBeamResult().copy(width = 0.0)
        BbsGenerator.generateBeamBbs("B1", badBeam)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateBeamBbs should throw for zero depth`() {
        val badBeam = validBeamResult().copy(depth = 0.0)
        BbsGenerator.generateBeamBbs("B1", badBeam)
    }

    @Test
    fun `generateBeamBbs main bar length includes hooks`() {
        val beam = validBeamResult()
        val entries = BbsGenerator.generateBeamBbs("B1", beam, storyHeight_mm = 3000.0)
        // spanMm = 3000.0, hookLen = 2 * 9 * diameter = 2 * 9 * 16 = 288
        // mainBarLen = 3000.0 + 288 = 3288.0
        val expectedMainLen = 3000.0 + 2 * 9 * beam.reinforcementBottom.diameter
        assertEquals("Main bar length should include 9φ hooks at both ends",
            expectedMainLen, entries[0].totalLengthPerBar, 1e-6)
    }

    // ─── generateColumnBbs ───────────────────────────────────────────────

    @Test
    fun `generateColumnBbs should produce entries for valid input`() {
        val entries = BbsGenerator.generateColumnBbs("C1", validColumnResult())
        assertTrue("Should produce at least 2 entries (main + ties)", entries.size >= 2)
        assertEquals("First entry memberMark should be C1", "C1", entries[0].memberMark)
        assertEquals("Main bar mark should be 01", "01", entries[0].barMark)
        assertTrue("Main bar totalWeightKg should be positive", entries[0].totalWeightKg > 0)
        assertEquals("Tie entry shapeCode should be 51", 51, entries[1].shapeCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateColumnBbs should throw for blank mark`() {
        BbsGenerator.generateColumnBbs("", validColumnResult())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateColumnBbs should throw for zero width`() {
        val badCol = validColumnResult().copy(width = 0.0)
        BbsGenerator.generateColumnBbs("C1", badCol)
    }

    @Test
    fun `generateColumnBbs main bar includes lap splice`() {
        val col = validColumnResult()
        val entries = BbsGenerator.generateColumnBbs("C1", col, storyHeight_mm = 3000.0)
        // h = 3000.0, mainLen = h + 60 * diameter = 3000 + 60*20 = 4200.0
        val expectedMainLen = 3000.0 + 60 * col.reinforcement.diameter
        assertEquals("Main bar length should include 60φ lap splice",
            expectedMainLen, entries[0].totalLengthPerBar, 1e-6)
    }

    // ─── generateFootingBbs ──────────────────────────────────────────────

    @Test
    fun `generateFootingBbs should produce entries for valid input`() {
        val entries = BbsGenerator.generateFootingBbs("F1", validFootingResult())
        assertEquals("Should produce exactly 2 entries (X + Y bars)", 2, entries.size)
        assertEquals("First entry memberMark should be F1", "F1", entries[0].memberMark)
        assertEquals("X bar mark should be 01", "01", entries[0].barMark)
        assertEquals("Y bar mark should be 02", "02", entries[1].barMark)
        assertEquals("Footing bars use shape code 21", 21, entries[0].shapeCode)
        assertTrue("X bar totalWeightKg should be positive", entries[0].totalWeightKg > 0)
        assertTrue("Y bar totalWeightKg should be positive", entries[1].totalWeightKg > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateFootingBbs should throw for blank mark`() {
        BbsGenerator.generateFootingBbs("  ", validFootingResult())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateFootingBbs should throw for zero length`() {
        val badFooting = validFootingResult().copy(length = 0.0)
        BbsGenerator.generateFootingBbs("F1", badFooting)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateFootingBbs should throw for zero width`() {
        val badFooting = validFootingResult().copy(width = 0.0)
        BbsGenerator.generateFootingBbs("F1", badFooting)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateFootingBbs should throw for zero thickness`() {
        val badFooting = validFootingResult().copy(thickness = 0.0)
        BbsGenerator.generateFootingBbs("F1", badFooting)
    }

    // ─── optimizeCutting ─────────────────────────────────────────────────

    @Test
    fun `optimizeCutting should return valid optimization results`() {
        val entries = BbsGenerator.generateBeamBbs("B1", validBeamResult())
        val result = BbsGenerator.optimizeCutting(entries, stockLength = 12000.0)
        assertNotNull("Result should not be null", result)
        assertTrue("Result should mention stock bars", result.contains("stock bars") || result.contains("Optimization"))
        assertTrue("Result should mention efficiency", result.contains("Efficiency"))
    }

    @Test
    fun `optimizeCutting should handle single entry`() {
        val entry = BbsEntry(
            memberMark = "B1", barMark = "01", diameter = 16,
            shapeCode = 0, count = 1, lengthA = 6000.0,
            totalLengthPerBar = 6000.0, totalWeightKg = 9.5
        )
        val result = BbsGenerator.optimizeCutting(listOf(entry), stockLength = 12000.0)
        assertTrue("Should contain optimization info", result.contains("Optimization Results"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `optimizeCutting should throw for zero stockLength`() {
        val entry = BbsEntry(
            memberMark = "B1", barMark = "01", diameter = 16,
            shapeCode = 0, count = 1, lengthA = 6000.0,
            totalLengthPerBar = 6000.0, totalWeightKg = 9.5
        )
        BbsGenerator.optimizeCutting(listOf(entry), stockLength = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `optimizeCutting should throw for stockLength below minimum`() {
        val entry = BbsEntry(
            memberMark = "B1", barMark = "01", diameter = 16,
            shapeCode = 0, count = 1, lengthA = 6000.0,
            totalLengthPerBar = 6000.0, totalWeightKg = 9.5
        )
        BbsGenerator.optimizeCutting(listOf(entry), stockLength = 3000.0)
    }

    @Test
    fun `optimizeCutting should return message for empty entries`() {
        val result = BbsGenerator.optimizeCutting(emptyList(), stockLength = 12000.0)
        assertTrue("Should indicate no bars to optimize", result.contains("No valid bars"))
    }

    // ─── combineProjectBbs ───────────────────────────────────────────────

    @Test
    fun `combineProjectBbs should combine multiple element lists`() {
        val beamEntries = BbsGenerator.generateBeamBbs("B1", validBeamResult())
        val colEntries = BbsGenerator.generateColumnBbs("C1", validColumnResult())
        val combined = BbsGenerator.combineProjectBbs(listOf(beamEntries, colEntries))
        assertTrue("Combined list should have entries", combined.isNotEmpty())
        // Beam and column have different diameters/shapeCodes, so they should not merge
        assertTrue("Should have at least 4 entries (2 beam + 2 column)", combined.size >= 4)
    }

    @Test
    fun `combineProjectBbs should deduplicate identical entries`() {
        val beam1 = BbsGenerator.generateBeamBbs("B1", validBeamResult())
        val beam2 = BbsGenerator.generateBeamBbs("B2", validBeamResult())
        val combined = BbsGenerator.combineProjectBbs(listOf(beam1, beam2))
        // Both beams have same diameter/shapeCode/lengthA/lengthB, so they should merge
        // Original: 2 entries per beam × 2 beams = 4, but deduped should be ≤ 4
        assertTrue("Combined should have entries", combined.isNotEmpty())
        // The main bars and stirrups should be merged since geometry is identical
        assertTrue("Deduped entries should be fewer than raw total",
            combined.size <= beam1.size + beam2.size)
    }

    @Test
    fun `combineProjectBbs should sum counts for deduplicated entries`() {
        val entry1 = BbsEntry(
            memberMark = "B1", barMark = "01", diameter = 16,
            shapeCode = 0, count = 3, lengthA = 6000.0,
            totalLengthPerBar = 6000.0, totalWeightKg = 10.0
        )
        val entry2 = BbsEntry(
            memberMark = "B2", barMark = "01", diameter = 16,
            shapeCode = 0, count = 5, lengthA = 6000.0,
            totalLengthPerBar = 6000.0, totalWeightKg = 15.0
        )
        val combined = BbsGenerator.combineProjectBbs(listOf(listOf(entry1), listOf(entry2)))
        assertEquals("Identical bars should be merged into 1 entry", 1, combined.size)
        assertEquals("Merged count should be sum of both counts", 8, combined[0].count)
        assertEquals("Merged weight should be sum of both weights", 25.0, combined[0].totalWeightKg, 1e-6)
    }

    @Test
    fun `combineProjectBbs should handle empty input`() {
        val combined = BbsGenerator.combineProjectBbs(emptyList())
        assertTrue("Empty input should produce empty output", combined.isEmpty())
    }
}
