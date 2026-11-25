package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Hand-computed numeric reference for the ECP 203-2020 hordi rib estimate.
 * Values derived from the printed equations (Eq. 4-1/4-2, Eq. 4-9, Eq. 4-29,
 * Table 4-1) for the interface geometry defaults (cover 25, link 8, bar 12):
 * d = t − 39 mm, blockK = 0.67·(fcu/γc)·b, fyd = fy/γs.
 * Arithmetic checks only — not engineering acceptance.
 */
class ECPHordiRibEstimateTest {

    private fun design(
        t: Double = 250.0, topping: Double = 50.0, moment: Double, shear: Double = 5.0,
        // span never enters the flexure/shear arithmetic; 4500 keeps Ln/t = 18 <=
        // 19.43 (Table 4-10 ribbed, simply supported, Eq. 4-81 factor 0.97143 for
        // fy=400) so the flexure-focused tests stay on the passing side.
        span: Double = 4500.0,
        support: com.civileg.app.domain.calculations.base.SlabSupportCondition =
            com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED,
        bondClass: com.civileg.app.domain.entities.BarBondClass =
            com.civileg.app.domain.entities.BarBondClass.UNKNOWN
    ) =
        ECPHordiSlabDesign().designHordiSlab(
            fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 600.0,
            totalThickness = t, toppingThickness = topping, span = span,
            designMoment = moment, designShear = shear, loadCombination = LoadCombination.DEAD_LIVE,
            barBondClass = bondClass, supportCondition = support
        )

    @Test
    fun `reference case matches hand-computed Eq 4-1 4-2 4-9 and 4-29 values`() {
        // d = 211, blockK = 2010 N/mm, fyd = 347.826
        // disc = (2010·211)² − 2·2010·1e7 → asCalc = 144.86 mm²
        // Eq 4-9: μ-eq 97.51 < calc; floor 0.0018·150·250 = 67.5 → AsReq = 144.86
        // bars: ceil(144.86/113.097) = 2 → 226.19 mm²
        // Vc = 0.16·√20·150·211/1000 = 22.65 kN (concrete-only, eligible: 250 ≤ 250)
        val r = design(moment = 10.0,
            bondClass = com.civileg.app.domain.entities.BarBondClass.DEFORMED)
        assertEquals(144.86, r.requiredReinforcement, 0.5)
        assertEquals(226.19, r.providedReinforcement, 0.01)
        assertEquals(22.649, r.shearCapacity, 0.01)
        assertEquals(12.0, r.barDiameter, 0.0)
        assertTrue(r.isSafe)
        assertTrue(r.bondClassAssessed)
        assertEquals(com.civileg.app.domain.entities.BarBondClass.DEFORMED, r.barBondClass)
        // span 4500/250 = 18 <= 20x0.97143 = 19.43: deemed-to-satisfy PASS (Table 4-10)
        assertTrue(r.serviceabilityAssessed)
        assertTrue(r.codeNotes.any { it.contains("Table 4-10") })
        assertTrue(r.engineeringAcceptance) // all three dimensions now qualified
        assertEquals(144.86 / 226.19, r.utilizationRatio, 0.01)
    }

    @Test
    fun `ductility Table 4-1 limit fails high moment even when quadratic solves`() {
        // M = 30 kN·m: asCalc = 519.4 → c/d = 0.5325 > 0.42 (fy=400)
        val r = design(moment = 30.0)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("Table 4-1") })
    }

    @Test
    fun `rib outside shallow exception cannot be qualified by concrete-only engine`() {
        // t = 300 > max(250, 2.5·50, 75) = 250 → §4-2-2-1-6-د-2 not met
        val r = design(t = 300.0, moment = 10.0)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("4-2-2-1-6") })
    }

    @Test
    fun `concrete-only shear overload fails without offering links`() {
        // Vc = 22.65 kN; demand 30 kN → fail, enlarge-rib warning
        val r = design(moment = 10.0, shear = 30.0)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("Eq. 4-29") })
        assertTrue(r.utilizationRatio > 1.0)
    }

    @Test
    fun `infeasible flexure reports infinity demand not a clamped bar count`() {
        val r = design(moment = 1000.0)
        assertFalse(r.isSafe)
        assertTrue(r.requiredReinforcement.isInfinite())
        assertTrue(r.warnings.any { it.contains("Flexure") })
    }

    @Test
    fun `no phi factors or ACI stress block remain in ECP path`() {
        // blockK uses 0.67·(fcu/γc); a 0.85·(fcu/γc) ACI-style block would give
        // blockK = 2550 and asCalc = 114.4 — distinct from the ECP 144.86 above.
        val r = design(moment = 10.0)
        assertEquals(144.86, r.requiredReinforcement, 0.5)
        assertTrue(r.codeNotes.any { it.contains("ECP 203-2020") })
        assertFalse(r.codeNotes.any { it.contains("2025") })
    }

    @Test
    fun `adopted layout ductility regression - bar round-up crosses Table 4-1`() {
        // M = 24.54 kN·m: asCalc = 399.97 → c/d(required) = 0.410 ≤ 0.42 passes;
        // adoption rounds up to 4φ12 = 452.39 mm² → c/d(adopted) = 0.464 > 0.42.
        // The adopted layout must fail even though the required area passed.
        val r = design(moment = 24.54)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("Adopted layout") && it.contains("Table 4-1") })
    }

    @Test
    fun `user cover propagates into effective depth`() {
        // coverMm = 35 → d = 250 − 35 − 8 − 6 = 201 (vs default 211)
        // disc = (2010·201)² − 2·2010·1e7 → asCalc = 153.13 mm²
        val r = ECPHordiSlabDesign().designHordiSlab(
            fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 600.0,
            totalThickness = 250.0, toppingThickness = 50.0, span = 4500.0,
            designMoment = 10.0, designShear = 5.0, loadCombination = LoadCombination.DEAD_LIVE,
            coverMm = 35.0,
            supportCondition = com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED
        )
        assertEquals(153.13, r.requiredReinforcement, 0.5)
        assertTrue(r.codeNotes.any { it.contains("cover 35") })
    }

    @Test
    fun `minimum steel Eq 4-9 governs very small moments with conservative UNKNOWN bond floor`() {
        // M = 1 kN·m: capped asCalc = 14.3 < min; Eq 4-9 min(1.3·14.3, 97.5)=18.6.
        // Default barBondClass = UNKNOWN → conservative plain-bar floor
        // 0.0025·150·250 = 93.75 (not the deformed 67.5) → AsReq = 93.75, 1 bar.
        // Regression (lead): UNKNOWN is unassessed — isSafe MUST be false and
        // bondClassAssessed false even though every numeric check passed.
        val r = design(moment = 1.0)
        assertEquals(93.75, r.requiredReinforcement, 0.5)
        assertEquals(113.10, r.providedReinforcement, 0.01)
        assertFalse(r.isSafe)
        assertFalse(r.bondClassAssessed)
        assertEquals(com.civileg.app.domain.entities.BarBondClass.UNKNOWN, r.barBondClass)
        assertFalse(r.engineeringAcceptance)
        assertTrue(r.warnings.any { it.contains("cannot be accepted") })
        assertTrue(r.codeNotes.any { it.contains("ESTIMATE, not acceptance") })
    }

    @Test
    fun `declared DEFORMED bond class applies the deformed Eq 4-9 floor explicitly`() {
        // Same case with an explicit declaration: floor 0.0018·150·250 = 67.5.
        val r = ECPHordiSlabDesign().designHordiSlab(
            fcu = 30.0, fy = 400.0, ribWidth = 150.0, ribSpacing = 600.0,
            totalThickness = 250.0, toppingThickness = 50.0, span = 4500.0,
            designMoment = 1.0, designShear = 5.0, loadCombination = LoadCombination.DEAD_LIVE,
            barBondClass = com.civileg.app.domain.entities.BarBondClass.DEFORMED,
            supportCondition = com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED
        )
        assertEquals(67.5, r.requiredReinforcement, 0.5)
        assertTrue(r.isSafe)
        assertTrue(r.bondClassAssessed)
        assertEquals(com.civileg.app.domain.entities.BarBondClass.DEFORMED, r.barBondClass)
        assertTrue(r.serviceabilityAssessed)
        assertTrue(r.engineeringAcceptance) // declared bond + passing deemed-to-satisfy
        assertTrue(r.codeNotes.any { it.contains("declared by caller") })
        assertFalse(r.codeNotes.any { it.contains("ESTIMATE, not acceptance") })
    }

    @Test
    fun `Table 4-10 deemed-to-satisfy failure gates isSafe`() {
        // span 6000/250 = 24 > 20x(0.4+400/700) = 19.43 (ribbed, simply supported)
        val r = design(moment = 10.0, span = 6000.0,
            bondClass = com.civileg.app.domain.entities.BarBondClass.DEFORMED)
        assertTrue(r.serviceabilityAssessed)
        assertFalse(r.isSafe)
        assertFalse(r.engineeringAcceptance)
        assertTrue(r.warnings.any { it.contains("Table 4-10") })
    }

    @Test
    fun `span beyond Table 4-10 applicability is explicit non-assessment not a pass`() {
        // span 6500 > 6000 cap: deemed-to-satisfy inapplicable; detailed calc required
        val r = design(moment = 10.0, span = 6500.0,
            bondClass = com.civileg.app.domain.entities.BarBondClass.DEFORMED)
        assertFalse(r.serviceabilityAssessed)
        assertFalse(r.engineeringAcceptance)
        assertTrue(r.isSafe) // flexure/shear/bond pass; serviceability is open, not failed
        assertTrue(r.codeNotes.any { it.contains("detailed deflection") })
    }

    @Test
    fun `cantilever deemed limit uses the 8 base ratio`() {
        // cantilever: base 8; 2000/250 = 8.0 > 8x0.97143 = 7.77 -> fail
        val r = design(moment = 10.0, span = 2000.0,
            support = com.civileg.app.domain.calculations.base.SlabSupportCondition.CANTILEVER,
            bondClass = com.civileg.app.domain.entities.BarBondClass.DEFORMED)
        assertTrue(r.serviceabilityAssessed)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("Table 4-10") })
        // 2100 mm cantilever exceeds the 2 m applicability cap -> not assessed
        val beyond = design(moment = 10.0, span = 2100.0,
            support = com.civileg.app.domain.calculations.base.SlabSupportCondition.CANTILEVER,
            bondClass = com.civileg.app.domain.entities.BarBondClass.DEFORMED)
        assertFalse(beyond.serviceabilityAssessed)
    }
}
