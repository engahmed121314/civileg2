package com.civileg.app.domain.calculations.settlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.log10

class SettlementAnalysisTest {

    private lateinit var analysis: SettlementAnalysis

    @Before
    fun setup() {
        analysis = SettlementAnalysis()
    }

    private fun input(
        b: Double = 2.0,
        l: Double = 4.0,
        q: Double = 100.0,
        es: Double = 20.0,
        nu: Double = 0.30,
        influence: Double = 1.0,
        depth: Double = 0.0,
        h: Double = 0.0,
        cc: Double = 0.0,
        cr: Double = 0.0,
        e0: Double = 0.0,
        sigma0: Double = 0.0,
        sigmaP: Double = 0.0,
        delta: Double = 0.0,
        cv: Double = 0.0,
        hd: Double = 0.0,
        time: Double = 1.0,
        allowable: Double = 25.0
    ) = SettlementInput(
        footingWidth = b,
        footingLength = l,
        appliedPressure = q,
        elasticModulus = es,
        foundationDepth = depth,
        poissonRatio = nu,
        influenceFactor = influence,
        layerThickness = h,
        compressionIndex = cc,
        recompressionIndex = cr,
        initialVoidRatio = e0,
        initialEffectiveStress = sigma0,
        preconsolidationPressure = sigmaP,
        stressIncrease = delta,
        coefficientOfConsolidation = cv,
        drainagePath = hd,
        timeYears = time,
        allowableSettlement = allowable
    )

    // ── Immediate settlement ──────────────────────────────────────────

    @Test
    fun `immediate elastic settlement is correct`() {
        val result = analysis.analyze(input())
        assertEquals(9.1, result.immediateSettlement, 1e-9) // 100·2·0.91/20
    }

    @Test
    fun `influence factor scales the immediate settlement`() {
        val result = analysis.analyze(input(influence = 1.5))
        assertEquals(13.65, result.immediateSettlement, 1e-9)
    }

    @Test
    fun `immediate settlement increases with pressure`() {
        val low = analysis.analyze(input(q = 50.0))
        val high = analysis.analyze(input(q = 150.0))
        assertTrue(high.immediateSettlement > low.immediateSettlement)
    }

    // ── Consolidation settlement ──────────────────────────────────────

    @Test
    fun `normally consolidated clay follows Terzaghi`() {
        val result = analysis.analyze(
            input(h = 4.0, cc = 0.30, e0 = 1.0, sigma0 = 100.0, delta = 50.0)
        )
        val expected = (0.30 / 2.0) * 4000.0 * log10(150.0 / 100.0)
        assertEquals(expected, result.consolidationSettlement, 1e-6)
        assertTrue(result.isConsolidationApplicable)
    }

    @Test
    fun `over-consolidated clay uses recompression and virgin branches`() {
        val result = analysis.analyze(
            input(
                h = 4.0, cc = 0.30, cr = 0.05, e0 = 1.0,
                sigma0 = 100.0, sigmaP = 140.0, delta = 50.0
            )
        )
        val recompression = (0.05 / 2.0) * 4000.0 * log10(140.0 / 100.0)
        val virgin = (0.30 / 2.0) * 4000.0 * log10(150.0 / 140.0)
        assertEquals(recompression + virgin, result.consolidationSettlement, 1e-6)
    }

    @Test
    fun `loading within the recompression range uses Cr only`() {
        val result = analysis.analyze(
            input(
                h = 4.0, cc = 0.30, cr = 0.05, e0 = 1.0,
                sigma0 = 100.0, sigmaP = 140.0, delta = 20.0
            )
        )
        val expected = (0.05 / 2.0) * 4000.0 * log10(120.0 / 100.0)
        assertEquals(expected, result.consolidationSettlement, 1e-6)
    }

    @Test
    fun `missing consolidation data yields zero consolidation settlement`() {
        val result = analysis.analyze(input(h = 4.0))
        assertFalse(result.isConsolidationApplicable)
        assertEquals(0.0, result.consolidationSettlement, 1e-9)
        assertTrue(result.warnings.any { it.contains("incomplete") })
    }

    // ── Stress distribution ───────────────────────────────────────────

    @Test
    fun `two to one method gives the stress increase at mid-layer`() {
        val result = analysis.analyze(input(b = 2.0, l = 2.0, q = 100.0, h = 2.0))
        assertEquals(100.0 * 4.0 / 9.0, result.stressIncrease, 1e-9)
    }

    @Test
    fun `explicit stress increase overrides the two to one method`() {
        val result = analysis.analyze(input(h = 2.0, delta = 75.0))
        assertEquals(75.0, result.stressIncrease, 1e-9)
    }

    // ── Time rate ─────────────────────────────────────────────────────

    @Test
    fun `degree of consolidation follows the Tv relation`() {
        assertEquals(0.0, SettlementAnalysis.degreeOfConsolidation(0.0), 1e-9)
        assertEquals(0.5046, SettlementAnalysis.degreeOfConsolidation(0.2), 1e-4)
        assertEquals(0.900, SettlementAnalysis.degreeOfConsolidation(0.848), 1e-3)
    }

    @Test
    fun `time factor is cv t over Hd squared`() {
        assertEquals(0.5, SettlementAnalysis.timeFactor(1.0, 2.0, 2.0), 1e-9)
    }

    @Test
    fun `time for ninety percent consolidation is correct`() {
        val result = analysis.analyze(
            input(h = 4.0, cc = 0.30, e0 = 1.0, sigma0 = 100.0, delta = 50.0, cv = 1.0, hd = 1.0)
        )
        assertEquals(0.848, result.timeFor90Consolidation, 1e-9)
    }

    @Test
    fun `settlement at a given time is the degree of consolidation times Sc`() {
        val result = analysis.analyze(
            input(
                h = 4.0, cc = 0.30, e0 = 1.0, sigma0 = 100.0, delta = 50.0,
                cv = 1.0, hd = 1.0, time = 0.848
            )
        )
        assertEquals(0.848, result.timeFor90Consolidation, 1e-9)
        assertEquals(0.900, result.degreeOfConsolidation, 1e-3)
        assertEquals(
            result.consolidationSettlement * result.degreeOfConsolidation,
            result.settlementAtTime,
            1e-9
        )
    }

    // ── Totals and safety ─────────────────────────────────────────────

    @Test
    fun `total settlement is the sum of immediate and consolidation`() {
        val result = analysis.analyze(
            input(h = 4.0, cc = 0.30, e0 = 1.0, sigma0 = 100.0, delta = 50.0, allowable = 200.0)
        )
        assertEquals(
            result.immediateSettlement + result.consolidationSettlement,
            result.totalSettlement,
            1e-9
        )
        assertTrue(result.isSafe)
    }

    @Test
    fun `excessive settlement is unsafe`() {
        val result = analysis.analyze(
            input(h = 4.0, cc = 0.30, e0 = 1.0, sigma0 = 100.0, delta = 50.0, allowable = 1.0)
        )
        assertFalse(result.isSafe)
        assertTrue(result.warnings.any { it.contains("allowable") })
    }

    // ── Validation ────────────────────────────────────────────────────

    @Test
    fun `non-positive footing width is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis.analyze(input(b = 0.0))
        }
    }

    @Test
    fun `invalid Poisson ratio is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis.analyze(input(nu = 0.60))
        }
    }

    @Test
    fun `negative time is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis.analyze(input(time = -1.0))
        }
    }
}
