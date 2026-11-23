package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden vectors for the SBC 304-CR-2018 joist rebuild (CLAUSE_LOG batch SJ-1…SJ-9,
 * 2026-11-22, rendered printed pages of the owner's SBC 304-CR-18 PDF).
 *
 * Vectors are hand-computed from the verified clauses: φ=0.90 (Table 21.2.2,
 * printed 169), β1 (Table 22.2.2.4.3, printed 173), beff (Table 6.3.2.1,
 * printed 40), As,min (§9.6.1.2/9.6.1.3, printed 68), geometry (§9.8, printed 72).
 * NO shear-capacity vector exists: SJ-7 holds the printed Eq (22.5.5.1)
 * discrepancy, so the shear check is BLOCKED and no result is a design pass.
 *
 * `span` is mm per the HordiSlabDesign interface contract (the production
 * caller passes span_m × 1000).
 */
class SBCJoistSlabTest {

    private fun design(
        fcu: Double = 30.0,
        fy: Double = 400.0,
        ribWidth: Double = 150.0,
        ribSpacing: Double = 650.0,
        totalThickness: Double = 300.0,
        toppingThickness: Double = 60.0,
        span: Double = 6000.0,
        designMoment: Double = 45.0,
        designShear: Double = 30.0,
        cylinderStrength: Double? = 24.0
    ) = SBCJoistSlab().designHordiSlab(
        fcu = fcu, fy = fy, ribWidth = ribWidth, ribSpacing = ribSpacing,
        totalThickness = totalThickness, toppingThickness = toppingThickness,
        span = span, designMoment = designMoment, designShear = designShear,
        loadCombination = LoadCombination.DEAD_LIVE,
        cylinderStrengthMPa = cylinderStrength
    )

    @Test
    fun `golden vector - rectangular flange case with hand-computed values`() {
        // fc′ = 24 MPa (explicit cylinder input — no conversion); d = 300−20−10 = 270; beff = min(650, 150+2·min(480,250,750)) = 650
        // Mn,req = 45e6/0.9 = 50.0e6; a = 270 − √(270² − 2·50.0e6/(0.85·24·650)) = 14.347 mm ≤ hf=60
        // As = 0.85·24·650·14.347/400 = 475.6 mm²
        // As,min = max(0.25·√24/400, 1.4/400)·150·270 = 141.75; 4/3·As = 634.1 → As,design = 475.6
        // bars: 5Φ12 needed → skip; 4Φ14 → As,prov = 615.75 mm²
        // εt = 0.003·(270 − 14.347/0.85)/(14.347/0.85) = 0.0450 ≥ 0.005 (tension-controlled)
        val r = design()
        assertEquals(475.6, r.requiredReinforcement, 0.5)
        assertEquals(615.75, r.providedReinforcement, 0.5)
        assertEquals(14.0, r.barDiameter, 1e-9)
        assertTrue(r.codeNotes.any { it.contains("beff=650") })
    }

    @Test
    fun `SJ-7 shear is blocked - null capacity and never a design pass`() {
        val r = design()
        // Explicit not-evaluated schema: null, never NaN or a fake number
        assertNull(r.shearCapacity)
        assertNull(r.utilizationRatio)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("BLOCKED (SJ-7)") })
    }

    @Test
    fun `SJ-8 cube input is never converted - cylinder strength required`() {
        // Missing cylinder strength refuses the Saudi computation loudly
        assertThrows(MissingCylinderStrengthException::class.java) {
            design(cylinderStrength = null)
        }
        // The legacy cube value is NOT converted: with cylinder = 30 the engine
        // uses exactly 30 MPa, not 0.8 x fcu
        val r = design(fcu = 25.0, cylinderStrength = 30.0)
        assertTrue(r.codeNotes.any { it.contains("fc′=30.0 MPa (cylinder, user-entered)") })
        assertTrue(r.warnings.none { it.contains("0.8") })
    }

    @Test
    fun `SJ-1 rib width below 100mm is flagged`() {
        val r = design(ribWidth = 80.0)
        assertTrue(r.warnings.any { it.contains("§9.8.1.2") })
        assertTrue(r.codeNotes.any { it.contains("§9.8.1.8") })
        assertFalse(r.isSafe)
    }

    @Test
    fun `SJ-1 clear spacing above 750mm is flagged`() {
        val r = design(ribSpacing = 1000.0)
        assertTrue(r.warnings.any { it.contains("§9.8.1.4") })
    }

    @Test
    fun `SJ-2 topping below the 9-8-3-1 rule is flagged`() {
        // clear = 500 → required = max(500/12, 50) = 50; topping 40 fails
        val r = design(toppingThickness = 40.0)
        assertTrue(r.warnings.any { it.contains("§9.8.3.1") })
    }

    @Test
    fun `SJ-5 minimum steel with the one-third-greater exemption`() {
        // Tiny moment: As,req ≈ 10.3 mm²; As,min = 141.75; exemption floor = 4/3·10.3 = 13.7
        // → As,design = 13.7 → 1Φ12 (113.1 mm²)
        val r = design(designMoment = 1.0)
        assertEquals(10.3, r.requiredReinforcement, 0.2)
        assertEquals(113.1, r.providedReinforcement, 0.2)
        assertEquals(12.0, r.barDiameter, 1e-9)
    }

    @Test
    fun `heavy moment drives T-section and over-reinforcement warning`() {
        // Mu = 200 kN·m → a = 71.5 > hf = 60 → T-section; As = 2419 > As,max = 2188 → warning
        val r = design(designMoment = 200.0)
        assertTrue(r.codeNotes.any { it.contains("T-section") })
        assertTrue(r.warnings.any { it.contains("over-reinforced") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `input guard rejects zero fcu`() {
        design(fcu = 0.0)
    }

    @Test
    fun `edition label is SBC 304-CR-2018 with no 2024 claim`() {
        val r = design()
        assertTrue(r.codeNotes.any { it.contains("SBC 304-CR-2018") })
        assertFalse(r.codeNotes.any { it.contains("304-2024") })
    }
}
