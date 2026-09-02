package com.civileg.core.engineering

import com.civileg.core.math.LengthUnit
import com.civileg.core.math.ForceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 04/06-07 golden gates. The beam-check numbers deliberately mirror
 * engineering/BeamGoldenBenchmarkTest (STEP 1) — two independent code paths,
 * one set of hand-derived answers.
 */
class MaterialsLoadsBeamGoldenTest {

    private val conc25 = ConcreteMaterial(fcuMpa = 25.0)
    private val steel360 = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)

    // ---------------- Materials ----------------

    @Test
    fun `material properties match hand values`() {
        assertEquals(4400.0 * 5.0, conc25.elasticModulusMpa(CodeFamily.ECP_203), 1e-9)   // 22000 MPa
        assertEquals(4700.0 * kotlin.math.sqrt(20.0), ConcreteMaterial(25.0).elasticModulusMpa(CodeFamily.ACI_318), 1e-9)
        assertEquals(3.0, conc25.tensileStrengthMpa, 1e-12)                              // 0.6*sqrt(25)
        assertEquals(0.85, conc25.betaOne(CodeFamily.ECP_203), 1e-12)
        assertEquals(0.80 * 25.0, conc25.cylinderStrengthMpa, 1e-12)
    }

    @Test
    fun `rebar table areas and unit mass`() {
        assertEquals(kotlin.math.PI * 100.0, RebarTable.area(20.0), 1e-9)
        // unit mass of Ø16 : 1.578 kg/m (0.00617 d²)
        assertEquals(1.5791, RebarTable.unitMassKgPerM(16.0), 1e-3)
        val (n, d) = RebarTable.select(1545.83)
        assertEquals(5, n)
        assertEquals(20.0, d, 1e-9)
    }

    // ---------------- Combinations & envelope ----------------

    @Test
    fun `ecp combinations produce documented factors`() {
        val combos = LoadCombinations.ecp203Gravity()
        assertEquals(2, combos.size)
        assertEquals(1.4 * 100.0, combos[0].demand(dead = 100.0), 1e-9)
        assertEquals(1.2 * 100.0 + 1.6 * 40.0, combos[1].demand(dead = 100.0, live = 40.0), 1e-9)
    }

    @Test
    fun `envelope picks governing combination`() {
        val env = LoadCombinations.envelopeMax(LoadCombinations.ecp203Gravity(), dead = 100.0, live = 40.0)
        // 1.4G=140 ; 1.2G+1.6Q=120+64=184 -> b governs
        assertEquals("1.2G+1.6Q", env.combinationName)
        assertEquals(184.0, env.demand, 1e-9)

        val deadOnly = LoadCombinations.envelopeMax(LoadCombinations.ecp203Gravity(), dead = 100.0)
        assertEquals("1.4G", deadOnly.combinationName)
    }

    @Test
    fun `aci envelope includes dead-reducing case`() {
        val aci = LoadCombinations.aci318Gravity()
        assertTrue(aci.any { it.isDeadReducing })
        // D=100, L=10: 1.4D=140 vs 1.2D+1.6L=136 -> a governs
        assertEquals(140.0, LoadCombinations.envelopeMax(aci, dead = 100.0, live = 10.0).demand, 1e-9)
        // D=100, L=50: b gives 200 > 140
        assertEquals(200.0, LoadCombinations.envelopeMax(aci, dead = 100.0, live = 50.0).demand, 1e-9)
    }

    // ---------------- Beam checks with full trace ----------------

    @Test
    fun `beam flexure reproduces STEP-1 golden case E1 with full trace`() {
        val checks = EcpBeamChecks(conc25, steel360)
        val r = checks.flexure(b = 250.0, d = 500.0, h = 560.0, muKnm = 200.0)

        assertEquals(0.128, r.k, 1e-6)
        assertEquals(1545.83, r.asRequiredMm2, 15.5)   // ±1%
        assertEquals("5Ø20", r.bars)
        assertEquals(1570.8, r.asProvidedMm2, 1.0)
        assertEquals(CheckStatus.PASS, r.trace.overall)
        assertTrue(r.trace.all.size >= 3)
        assertTrue(r.trace.all.first().formula.contains("K = Mu"))
    }

    @Test
    fun `beam flexure minimum-steel case warns not passes silently`() {
        val r = EcpBeamChecks(conc25, steel360).flexure(b = 250.0, d = 500.0, h = 560.0, muKnm = 5.0)
        // CORRECTED ECP pair (post-A10): max(0.25√fcu/fy, 0.0013)*250*500
        //   = max(0.0034722, 0.0013)*125000 = 434.03 mm²
        // (legacy engines still pin the pre-A10 EC2-pair value 451.39 as characterization)
        assertEquals(434.03, r.asRequiredMm2, 4.3)
        assertEquals(CheckStatus.WARNING, r.trace.overall)
        assertTrue(r.trace.count(CheckStatus.WARNING) >= 1)
    }

    @Test
    fun `over-reinforced section fails loudly at kernel level`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            EcpBeamChecks(conc25, steel360).flexure(b = 250.0, d = 500.0, h = 560.0, muKnm = 400.0)
        }
        assertTrue(ex.message!!.contains("doubly"))
    }

    @Test
    fun `beam shear reproduces STEP-1 golden case S1`() {
        val r = EcpBeamChecks(conc25, steel360).shear(b = 250.0, d = 500.0, vuKn = 150.0)
        assertEquals(122.47, r.concreteCapacityKn, 1.2)          // 0.24*sqrt(25/1.5)*125e3
        assertEquals(375.0, r.stirrupAreaPerMeterMm2, 4.0)       // min governs
        assertEquals(200.0, r.spacingMm, 1e-6)                   // coerced to max
        assertEquals(CheckStatus.FAIL, r.trace.overall)          // Vu>Vc marks capacity FAIL
    }

    // ---------------- Units cross-check inside an engineering formula ----------------

    @Test
    fun `typed units reproduce the same As through explicit conversions`() {
        val mu = com.civileg.core.math.Moment.of(200.0, com.civileg.core.math.MomentUnit.KNM)
        assertEquals(200e6, mu.asNmm, 1e-6)
        val lever = com.civileg.core.math.Length.of(413.296, LengthUnit.MM)
        val fsd = ForceUnit.KN.toN / 1.0 // sanity: kN->N factor
        assertEquals(1000.0, fsd, 1e-9)
        val asByUnits = mu.asNmm / ((steel360.yieldMpa / 1.15) * lever.value(LengthUnit.MM))
        assertEquals(1545.83, asByUnits, 1.5)
    }
}
