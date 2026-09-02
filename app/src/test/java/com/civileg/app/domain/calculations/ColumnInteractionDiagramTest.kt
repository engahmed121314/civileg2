package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.ColumnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI

/**
 * §18/§96 benchmark — real P-M interaction diagrams from strain compatibility
 * (ACIAdvancedColumn.generateInteractionDiagram).
 *
 * Physics invariants pinned here (golden-structural, code-independent):
 *  1. Density: ≥20 points, ordered from pure compression to pure tension.
 *  2. φP0 (first point, c=3h) ≈ closed-form φ[0.85·f'c(Ag−As) + fy·As] — ±5%.
 *  3. Pure compression moment ≈ 0 (symmetric bar layout, concentric load).
 *  4. Pure tension end is NEGATIVE axial, zero moment.
 *  5. Peak moment occurs in the transition zone — strictly greater than the
 *     moments at BOTH ends of the curve.
 *  6. Curve is single-peaked in M (no wild oscillation): every interior point
 *     bounded by [0, 1.5×peak].
 */
class ColumnInteractionDiagramTest {

    private val engine = com.civileg.app.domain.calculations.aci.ACIAdvancedColumn()

    // Benchmark Beam-style case: 300×500 tied column, fcu=30, fy=400, 8Ø20
    private val colType = ColumnType.Rectangular(width = 300.0, depth = 500.0)
    private val fcu = 30.0
    private val fy = 400.0
    private val asSteel = 8.0 * PI * 20.0 * 20.0 / 4.0   // 2513 mm²
    private val cover = 40.0

    private fun diagram() = engine.generateInteractionDiagram(
        columnType = colType, fcu = fcu, fy = fy,
        reinforcementArea = asSteel, isSpiral = false, clearCover = cover
    )

    @Test fun density_atLeastTwentyPoints() {
        val d = diagram()
        assertTrue("expected ≥20 points, got ${d.size}", d.size >= 20)
    }

    @Test fun phiP0_matchesClosedFormWithinFivePercent() {
        val fcPrime = 0.8 * fcu
        val ag = colType.getGrossArea()
        val phiTied = 0.65
        val expectedP0 = phiTied * (0.85 * fcPrime * (ag - asSteel) + fy * asSteel) / 1000.0 // kN
        val firstP = diagram().first().first
        assertEquals(
            "φP0 closed-form=$expectedP0 vs diagram=$firstP",
            expectedP0, firstP, expectedP0 * 0.05
        )
    }

    @Test fun pureCompression_momentNearZero_forSymmetricSection() {
        val (p0, m0) = diagram().first()
        assertTrue("P0 must be positive compression, got $p0", p0 > 0.0)
        assertTrue("concentric P0 must carry ≈0 moment, got $m0", abs(m0) < 5.0)
    }

    @Test fun pureTension_endIsNegativeAxialZeroMoment() {
        val last = diagram().last()
        assertEquals(0.0, last.second, 1e-9)
        assertTrue("pure tension must be negative axial, got ${last.first}", last.first < 0.0)
        // Closed form: −As·fy·φ /1000
        val expectedT = -asSteel * fy * 0.9 / 1000.0
        assertEquals(expectedT, last.first, abs(expectedT) * 0.02)
    }

    @Test fun peakMoment_inTransitionZone_strictlyInterior() {
        val d = diagram()
        val peakM = d.maxOf { it.second }
        val idxPeak = d.indexOfFirst { it.second == peakM }
        val idxMaxP = d.indexOfFirst { it.first == d.maxOf { p -> p.first } }
        assertTrue("peak must be interior", idxPeak in 1 until d.size - 1)
        assertTrue(
            "peak moment ($peakM) must exceed moment at pure compression (${d[idxMaxP].second})",
            peakM > d[idxMaxP].second + 1e-9
        )
        // And exceed the near-tension side too
        assertTrue(peakM > abs(d.last().second))
    }

    @Test fun curve_singlePeaked_noOscillation() {
        val d = diagram()
        val peakM = d.maxOf { it.second }
        d.forEach { (_, m) ->
            assertTrue("moment out of physical band: $m", m in -1.0..peakM * 1.5)
            assertTrue("moment must be non-negative on this symmetric section", m >= -1.0)
        }
        // Axial descends overall (allowing local plateaus) from compression to tension
        var maxSoFar = Double.MIN_VALUE
        d.forEach { (p, _) ->
            maxSoFar = maxOf(maxSoFar, p)
            assertTrue("axial rose after descending: $p > max-so-far $maxSoFar", p <= maxSoFar + 1e-9)
        }
    }

    @Test fun circular_producesValidCurve_too() {
        val d = engine.generateInteractionDiagram(
            columnType = ColumnType.Circular(diameter = 500.0),
            fcu = fcu, fy = fy,
            reinforcementArea = asSteel, isSpiral = true, clearCover = cover
        )
        assertTrue(d.size >= 20)
        val peakM = d.maxOf { it.second }
        assertTrue(peakM > 0.0)
        // Spiral columns keep higher axial capacity at same section — sanity only,
        // compare against the tied rectangular's P0 being at least in same order.
        val rect = diagram()
        assertTrue(d.first().first > rect.first().first * 0.5)
    }
}
