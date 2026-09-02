package com.civileg.core.sanity

import com.civileg.core.engineering.*
import com.civileg.core.calculations.entities.SupportCondition
import org.junit.Assert.*
import org.junit.Test

class EngineeringSanityEngineTest {

    private fun trace() = CalculationTrace()

    private fun goodOutcome(): BeamDesignFacade.BeamOutcome {
        val tr = trace()
        return BeamDesignFacade.BeamOutcome(
            flexure = UnifiedBeamFlexure.Outcome(
                asRequiredMm2 = 800.0, asProvidedMm2 = 870.0, bars = "3Ø20",
                governingNote = null, trace = tr
            ),
            shear = UnifiedBeamShear.Outcome(
                concreteCapacityKn = 120.0, maxCapacityKn = 350.0, stirrupDiaMm = 10.0,
                spacingMm = 150.0, asPerMeterMm2 = 300.0, minGoverns = true, isSafe = true,
                utilization = 0.4, trace = tr
            ),
            deflection = UnifiedBeamDeflection.Outcome(
                actualRatio = 18.0, allowableRatio = 20.0, utilization = 0.9,
                isSafe = true, overall = CheckStatus.PASS, trace = tr
            ),
            crackControl = UnifiedCrackControl.Outcome(
                maxAllowedMm = 0.3, actualMm = 0.2, isCompliant = true,
                overall = CheckStatus.PASS, trace = tr
            ),
            torsion = UnifiedBeamTorsion(Ecp203Params).design(
                UnifiedBeamTorsion.Input(
                    300.0, 600.0, 40.0, ConcreteMaterial(30.0),
                    SteelMaterial(360.0, 520.0), tuKnm = 2.0
                )
            ),
            overallStatus = CheckStatus.PASS,
            trace = tr
        )
    }

    @Test
    fun goodOutcome_passesSanity() {
        val r = EngineeringSanityEngine.check(goodOutcome())
        assertTrue(r.ok)
        assertEquals(CheckStatus.PASS, r.status)
    }

    @Test
    fun negativeAsRequired_flagsError() {
        val base = goodOutcome()
        val o = base.copy(flexure = base.flexure.copy(asRequiredMm2 = -10.0))
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-NEG" })
        assertEquals(CheckStatus.FAIL, r.status)
    }

    @Test
    fun overUtilizedShear_flagsError() {
        val base = goodOutcome()
        val o = base.copy(shear = base.shear.copy(utilization = 1.4, isSafe = true))
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-UTIL" })
    }

    @Test
    fun nanValue_flagsError() {
        val base = goodOutcome()
        val o = base.copy(flexure = base.flexure.copy(asRequiredMm2 = Double.NaN))
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-NAN" })
    }

    @Test
    fun providedLessThanRequired_flagsError() {
        val base = goodOutcome()
        val o = base.copy(flexure = base.flexure.copy(asRequiredMm2 = 1000.0, asProvidedMm2 = 800.0))
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-AS-PROV" })
    }

    @Test
    fun overReinforcedTorsion_flagsError() {
        val base = goodOutcome()
        val badTorsion = base.torsion!!.copy(tuKnm = 50.0, tuMaxKnm = 5.0, torsionState = UnifiedBeamTorsion.TorsionState.FULL_DESIGN)
        val o = base.copy(torsion = badTorsion)
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-UTIL" && it.message.contains("torsion.section") })
    }

    @Test
    fun genericCheckValues_detectsNegative() {
        val r = EngineeringSanityEngine.checkValues("test", mapOf("As" to -5.0, "x" to 3.0))
        assertTrue(r.hasError)
        assertEquals(1, r.findings.count { it.severity == SanitySeverity.ERROR })
    }

    @Test
    fun noTorsion_outcomeStillPasses() {
        val base = goodOutcome()
        val o = base.copy(torsion = null)
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.ok)
    }

    // ── End-to-end wiring via BeamDesignFacade ──

    @Test
    fun facadeAttachesSanityReport_validInput_ok() {
        val facade = BeamDesignFacade(
            Ecp203Params, ConcreteMaterial(30.0), SteelMaterial(360.0, 520.0)
        )
        val out = facade.design(
            b = 250.0, d = 500.0, h = 560.0, muKnm = 50.0, vuKn = 60.0, spanM = 6.0,
            support = SupportCondition.SIMPLY_SUPPORTED, tensionRatioPercent = 1.0,
            clearCoverMm = 40.0, tensionBarSpacingMm = 150.0, tuKnm = 0.2
        )
        assertNotNull(out.sanity)
        assertTrue(out.sanity.ok)
        assertEquals(CheckStatus.PASS, out.sanity.status)
    }

    @Test
    fun facadeSanity_flipsOverallToFail_onOverloadedTorsion() {
        val facade = BeamDesignFacade(
            Ecp203Params, ConcreteMaterial(30.0), SteelMaterial(360.0, 520.0)
        )
        // Valid (non-throwing) but extreme torsion: Tu >> Tu,max (ECP) → section over-utilized
        // → sanity ERROR → overall FAIL. (Invalid inputs throw upstream in SafeMath; the
        // sanity gate catches bad *outputs*, not bad inputs.)
        val out = facade.design(
            b = 250.0, d = 500.0, h = 560.0, muKnm = 50.0, vuKn = 60.0, spanM = 6.0,
            support = SupportCondition.SIMPLY_SUPPORTED, tensionRatioPercent = 1.0,
            clearCoverMm = 40.0, tensionBarSpacingMm = 150.0, tuKnm = 100.0
        )
        assertTrue(out.sanity.hasError)
        assertTrue(out.sanity.findings.any { it.code == "SAN-UTIL" })
        assertEquals(CheckStatus.FAIL, out.overallStatus)
    }

    // ── Column (axial + shear) sanity ──

    private fun goodColumnOutcome() = UnifiedColumnDesign.Outcome(
        asRequiredMm2 = 1200.0, asProvidedMm2 = 1350.0, bars = "4Ø20",
        tieDiameterMm = 10.0, tieSpacingMm = 200.0, axialCapacityKn = 2000.0,
        utilization = 0.75, governingNote = null, isSafe = true,
        overallStatus = CheckStatus.PASS, trace = trace()
    )

    @Test
    fun columnOutcome_good_passesSanity() {
        val r = EngineeringSanityEngine.check(goodColumnOutcome())
        assertTrue(r.ok)
        assertEquals(CheckStatus.PASS, r.status)
    }

    @Test
    fun columnOutcome_providedLessThanRequired_flagsError() {
        val o = goodColumnOutcome().copy(asRequiredMm2 = 1500.0, asProvidedMm2 = 1000.0)
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-AS-PROV" })
        assertEquals(CheckStatus.FAIL, r.status)
    }

    @Test
    fun columnOutcome_negativeAsRequired_flagsError() {
        val o = goodColumnOutcome().copy(asRequiredMm2 = -100.0)
        val r = EngineeringSanityEngine.check(o)
        assertTrue(r.hasError)
        assertTrue(r.findings.any { it.code == "SAN-NEG" })
    }

    @Test
    fun columnDesign_live_attachesSanity() {
        val col = UnifiedColumnDesign(Ecp203Params, ConcreteMaterial(25.0), SteelMaterial(360.0, 520.0))
        val out = col.design(b = 400.0, h = 400.0, puKnm = 800.0)
        assertNotNull(out.sanity)
        assertTrue(out.sanity.ok)
        assertNotEquals(CheckStatus.FAIL, out.overallStatus)
    }

    @Test
    fun columnDesignShear_live_attachesSanity() {
        val col = UnifiedColumnDesign(Ecp203Params, ConcreteMaterial(25.0), SteelMaterial(360.0, 520.0))
        val out = col.designShear(vuKn = 120.0, b = 400.0, h = 400.0, cover = 40.0)
        assertNotNull(out.sanity)
        assertTrue(out.sanity.ok)
    }

    // ── Slab (two-way) sanity ──

    @Test
    fun slabDesignTwoWay_live_attachesSanity() {
        val slab = UnifiedSlabDesign(Ecp203Params, ConcreteMaterial(25.0), SteelMaterial(360.0, 520.0))
        val out = slab.designTwoWay(
            shortSpanM = 4.0, longSpanM = 5.0, h = 180.0, totalLoadKnm2 = 12.0, allEdgesFixed = true
        )
        assertNotNull(out.sanity)
        assertTrue(out.sanity.ok)
        assertNotEquals(CheckStatus.FAIL, out.overallStatus)
    }
}
