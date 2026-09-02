package com.civileg.core.engineering

import org.junit.Assert.*
import org.junit.Test

/**
 * Golden tests for [UnifiedColumnDesign] (Column DoD §82).
 *
 * Each family's axial capacity and simplified reinforcement are verified by
 * independently recomputing the code formula inside the test (golden-gate
 * against the legacy ECPColumn / ACIColumn math, spec §76-78).
 */
class UnifiedColumnDesignGoldenTest {

    private val concreteEcp = ConcreteMaterial(fcuMpa = 25.0)
    private val steelEcp = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)
    private val concreteAci = ConcreteMaterial(fcuMpa = 25.0)  // f'c = 20 MPa
    private val steelAci = SteelMaterial(yieldMpa = 420.0, ultimateMpa = 600.0)

    // ── Axial capacity — exact formula match ──

    @Test
    fun `ECP axial capacity matches formula (tied)`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val b = 400.0
        val h = 400.0
        val ag = b * h
        val ast = 4.0 * RebarTable.area(20.0)
        val expected = run {
            val cs = 0.67 * 25.0 / 1.5
            val ss = 360.0 / 1.15
            val pn = cs * (ag - ast) + ss * ast
            0.65 * 0.8 * pn / 1000.0
        }
        assertEquals(expected, col.axialCapacityKn(b, h, ast, isSpiral = false), 1e-6)
    }

    @Test
    fun `ECP axial capacity matches formula (spiral)`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val b = 400.0
        val h = 400.0
        val ag = b * h
        val ast = 4.0 * RebarTable.area(20.0)
        val expected = run {
            val cs = 0.67 * 25.0 / 1.5
            val ss = 360.0 / 1.15
            val pn = cs * (ag - ast) + ss * ast
            0.75 * 0.8 * pn / 1000.0   // spiral φ = 0.75
        }
        assertEquals(expected, col.axialCapacityKn(b, h, ast, isSpiral = true), 1e-6)
    }

    @Test
    fun `ACI axial capacity matches formula (tied)`() {
        val col = UnifiedColumnDesign(Aci318Params, concreteAci, steelAci)
        val b = 400.0
        val h = 400.0
        val ag = b * h
        val ast = 4.0 * RebarTable.area(20.0)
        val expected = run {
            val fc = 0.80 * 25.0
            val p0 = 0.85 * fc * (ag - ast) + 420.0 * ast
            0.65 * 0.80 * p0 / 1000.0   // φ=0.65, cap=0.80
        }
        assertEquals(expected, col.axialCapacityKn(b, h, ast, isSpiral = false), 1e-6)
    }

    @Test
    fun `ACI axial capacity matches formula (spiral)`() {
        val col = UnifiedColumnDesign(Aci318Params, concreteAci, steelAci)
        val b = 400.0
        val h = 400.0
        val ag = b * h
        val ast = 4.0 * RebarTable.area(20.0)
        val expected = run {
            val fc = 0.80 * 25.0
            val p0 = 0.85 * fc * (ag - ast) + 420.0 * ast
            0.75 * 0.85 * p0 / 1000.0   // φ=0.75, cap=0.85
        }
        assertEquals(expected, col.axialCapacityKn(b, h, ast, isSpiral = true), 1e-6)
    }

    // ── Simplified reinforcement — exact formula match ──

    @Test
    fun `ECP simplified reinforcement matches formula`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val b = 400.0
        val h = 400.0
        val pu = 2000.0 // kN
        val ag = b * h
        val expectedAs = run {
            val cs = 0.67 * 25.0 / 1.5
            val ss = 360.0 / 1.15
            val alpha = 0.8
            val phi = 0.65
            (pu * 1000.0 / (phi * alpha) - cs * ag) / (ss - cs)
        }
        val result = col.design(b, h, pu, isSpiral = false)
        // As within rebar-selection rounding
        assertTrue("As=$expectedAs vs provided ${result.asProvidedMm2}", result.asProvidedMm2 >= expectedAs - 5.0)
    }

    @Test
    fun `ACI simplified reinforcement matches formula`() {
        val col = UnifiedColumnDesign(Aci318Params, concreteAci, steelAci)
        val b = 400.0
        val h = 400.0
        val pu = 2000.0 // kN
        val ag = b * h
        val expectedAs = run {
            val fc = 0.80 * 25.0
            val cs = 0.85 * fc
            val ss = 420.0
            val phi = 0.65
            val cap = 0.80
            (pu * 1000.0 / (phi * cap) - cs * ag) / (ss - cs)
        }
        val result = col.design(b, h, pu, isSpiral = false)
        assertTrue("As=$expectedAs vs provided ${result.asProvidedMm2}", result.asProvidedMm2 >= expectedAs - 5.0)
    }

    // ── Min / max steel gates ──

    @Test
    fun `ECP minimum steel governs at low axial load`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val b = 400.0
        val h = 400.0
        val ag = b * h
        val minAs = 0.008 * ag  // 1280 mm²
        val result = col.design(b, h, puKnm = 200.0, isSpiral = false) // low load → min governs
        assertEquals(minAs, result.asRequiredMm2, 1e-6)
        assertEquals("minimum steel governs", result.governingNote)
    }

    @Test
    fun `ECP max steel FAIL when demanded exceeds limit`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val b = 200.0
        val h = 200.0
        val ag = b * h  // 40000
        val maxAs = 0.06 * ag // 2400 mm²
        // Pu=750kN → As,req ≈ 3300 mm² (selectable with ≤24 bars) but exceeds 2400 → FAIL
        val result = col.design(b, h, puKnm = 750.0, isSpiral = false)
        assertEquals(CheckStatus.FAIL, result.overallStatus)
        assertTrue(result.asRequiredMm2 > maxAs)
    }

    // ── Ties spacing ──

    @Test
    fun `ECP ties spacing follows min rule`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val result = col.design(400.0, 400.0, puKnm = 2000.0, isSpiral = false)
        // selected bar diameter from the bars string, e.g. "8Ø20" → 20.0
        val barDia = result.bars.substringAfter('Ø').toDouble()
        val expected = minOf(16.0 * barDia, 48.0 * 10.0, 400.0, 300.0)
        assertEquals(expected, result.tieSpacingMm, 1e-6)
    }

    // ── Shear ──

    @Test
    fun `ECP column shear no stirrups when Vu below Vc`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val result = col.designShear(vuKn = 50.0, b = 400.0, h = 400.0)
        assertFalse(result.needsStirrups)
        assertEquals(CheckStatus.PASS, result.trace.overall)
    }

    @Test
    fun `ECP column shear needs stirrups when Vu exceeds Vc`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val vc = Ecp203Params.concreteShearCapacityKn(400.0, 360.0, concreteEcp) // d=400-40
        val result = col.designShear(vuKn = vc * 1.5, b = 400.0, h = 400.0)
        assertTrue(result.needsStirrups)
        assertEquals(CheckStatus.WARNING, result.trace.overall)
    }

    @Test
    fun `ACI column shear check`() {
        val col = UnifiedColumnDesign(Aci318Params, concreteAci, steelAci)
        val vc = Aci318Params.concreteShearCapacityKn(400.0, 360.0, concreteAci)
        val result = col.designShear(vuKn = vc * 1.5, b = 400.0, h = 400.0)
        assertTrue(result.needsStirrups)
        assertTrue(result.isSafe)
    }

    // ── Moment amplification ──

    @Test
    fun `ECP moment amplification increases As when e exceeds 0_05h`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val pure = col.design(400.0, 400.0, puKnm = 2000.0, momentXKnm = 0.0)
        val combined = col.design(400.0, 400.0, puKnm = 2000.0, momentXKnm = 300.0)
        assertTrue("combined As should exceed pure axial As", combined.asRequiredMm2 > pure.asRequiredMm2)
    }

    // ── Trace completeness ──

    @Test
    fun `Column design trace has all sections`() {
        val col = UnifiedColumnDesign(Ecp203Params, concreteEcp, steelEcp)
        val result = col.design(400.0, 400.0, puKnm = 2000.0, momentXKnm = 100.0)
        val titles = result.trace.all.map { it.title }
        assertTrue(titles.any { it.contains("eccentricity") || it.contains("Reinforcement limits") })
        assertTrue(titles.any { it.contains("Axial capacity") })
        assertTrue(titles.any { it.contains("Ties") })
        assertTrue(titles.any { it.contains("Concrete shear") })
    }
}
