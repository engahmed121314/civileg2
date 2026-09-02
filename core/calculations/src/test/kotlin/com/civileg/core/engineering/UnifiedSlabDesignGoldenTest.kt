package com.civileg.core.engineering

import com.civileg.core.calculations.entities.SupportCondition
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test

/**
 * Golden tests for [UnifiedSlabDesign] (Slab DoD §83).
 *
 * Coefficient tables are verified against the legacy app slab tables; the
 * flexure routing is verified against [UnifiedBeamFlexure] (the shared engine).
 * Regression gate: spec §76-78.
 */
class UnifiedSlabDesignGoldenTest {

    private val concreteEcp = ConcreteMaterial(fcuMpa = 25.0)
    private val steelEcp = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val concreteAci = ConcreteMaterial(fcuMpa = 25.0)
    private val steelAci = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)

    // ── Coefficient tables match legacy app ──

    @Test
    fun `ECP coefficients all-fixed ratio 1_0`() {
        val c = Ecp203Params.twoWaySlabCoefficients(1.0, allEdgesFixed = true)
        assertEquals(SlabMomentCoeffs(0.031, 0.024, 0.031, 0.024), c)
    }

    @Test
    fun `ECP coefficients all-fixed ratio 1_2`() {
        val c = Ecp203Params.twoWaySlabCoefficients(1.2, allEdgesFixed = true)
        assertEquals(SlabMomentCoeffs(0.036, 0.027, 0.028, 0.021), c)
    }

    @Test
    fun `ECP coefficients not-fixed ratio 1_0`() {
        val c = Ecp203Params.twoWaySlabCoefficients(1.0, allEdgesFixed = false)
        assertEquals(SlabMomentCoeffs(0.048, 0.036, 0.048, 0.036), c)
    }

    @Test
    fun `ACI coefficients all-fixed square`() {
        val c = Aci318Params.twoWaySlabCoefficients(1.0, allEdgesFixed = true)
        assertEquals(SlabMomentCoeffs(posShort = 0.08, negShort = 0.033, posLong = 0.08, negLong = 0.063), c)
    }

    @Test
    fun `ACI coefficients long panel distributes to long dir`() {
        // ratio 2.0 → shortCoeff = 0.050 (table) ; longCoeff = 0.050 / 4 = 0.0125
        val c = Aci318Params.twoWaySlabCoefficients(2.0, allEdgesFixed = true)
        assertEquals(0.050, c.posShort, 1e-9)
        assertEquals(0.0125, c.posLong, 1e-9)
    }

    // ── Flexure routing reuses UnifiedBeamFlexure exactly ──

    @Test
    fun `ECP slab routes short dir through shared flexure engine`() {
        val slab = UnifiedSlabDesign(Ecp203Params, concreteEcp, steelEcp)
        val h = 200.0; val cover = 20.0; val d = h - cover - 6.0
        val result = slab.designTwoWay(shortSpanM = 5.0, longSpanM = 5.0, h = h,
            totalLoadKnm2 = 10.0, allEdgesFixed = true, coverMm = cover)

        // Independent flexure solve for the same governing short-dir moment
        val coeffs = Ecp203Params.twoWaySlabCoefficients(1.0, true)
        val muShort = max(coeffs.posShort, coeffs.negShort) * 10.0 * 5.0 * 5.0
        val direct = UnifiedBeamFlexure(Ecp203Params, concreteEcp, steelEcp)
            .design(b = 1000.0, d = d, h = h, muKnm = muShort)
        assertEquals(direct.asProvidedMm2, result.shortDir.asProvidedMm2, 1e-6)
        assertEquals(muShort, result.muShortKnm, 1e-9)
    }

    @Test
    fun `ACI slab routes long dir through shared flexure engine`() {
        val slab = UnifiedSlabDesign(Aci318Params, concreteAci, steelAci)
        val h = 200.0; val cover = 20.0; val d = h - cover - 6.0
        val result = slab.designTwoWay(shortSpanM = 4.0, longSpanM = 8.0, h = h,
            totalLoadKnm2 = 12.0, allEdgesFixed = true, coverMm = cover)
        val coeffs = Aci318Params.twoWaySlabCoefficients(2.0, true)
        val muLong = max(coeffs.posLong, coeffs.negLong) * 12.0 * 4.0 * 4.0
        val direct = UnifiedBeamFlexure(Aci318Params, concreteAci, steelAci)
            .design(b = 1000.0, d = d, h = h, muKnm = muLong)
        assertEquals(direct.asProvidedMm2, result.longDir.asProvidedMm2, 1e-6)
    }

    // ── Min thickness screen ──

    @Test
    fun `ECP min thickness screen PASS when adequate`() {
        val slab = UnifiedSlabDesign(Ecp203Params, concreteEcp, steelEcp)
        // Lx=5m → min h = 5000/30 = 166.7 mm; provide 200 → thickness PASS.
        // (min-steel governs in flexure → overall WARNING, still safe — spec semantic.)
        val result = slab.designTwoWay(5.0, 5.0, h = 200.0, totalLoadKnm2 = 10.0, allEdgesFixed = true)
        assertEquals(CheckStatus.PASS, result.trace.all.first { it.title.contains("Minimum thickness") }.status)
        assertTrue(result.isSafe)
        assertTrue(result.minThicknessMm <= 200.0)
    }

    @Test
    fun `ECP min thickness screen FAIL when thin`() {
        val slab = UnifiedSlabDesign(Ecp203Params, concreteEcp, steelEcp)
        val result = slab.designTwoWay(5.0, 5.0, h = 100.0, totalLoadKnm2 = 10.0, allEdgesFixed = true)
        assertEquals(CheckStatus.FAIL, result.overallStatus)
    }

    // ── Aggregated trace ──

    @Test
    fun `Slab design trace aggregates all directions and shear`() {
        val slab = UnifiedSlabDesign(Ecp203Params, concreteEcp, steelEcp)
        val result = slab.designTwoWay(5.0, 5.0, h = 200.0, totalLoadKnm2 = 10.0, allEdgesFixed = true)
        val titles = result.trace.all.map { it.title }
        assertTrue(titles.any { it.contains("coefficients") })
        assertTrue(titles.any { it.contains("[Short dir]") })
        assertTrue(titles.any { it.contains("[Long dir]") })
        assertTrue(titles.any { it.contains("Minimum thickness") })
        // ≥ 1 (coeff) + 4 (short flex) + 4 (long flex) + 3 (shear) + 1 (thick) = 13
        assertTrue("trace size ${result.trace.all.size}", result.trace.all.size >= 13)
    }
}
