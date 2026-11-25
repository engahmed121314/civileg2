package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.aci.ACIJoistSlab
import com.civileg.app.domain.calculations.ecp.ECPHordiSlabDesign
import com.civileg.app.domain.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Golden vectors, boundary and input-guard coverage for [SBCJoistSlab] (Phase 1.3).
 *
 * Provenance is recorded in docs/CLAUSE_LOG.md (S-01…S-05, accessed 2026-11-19).
 * Verified against the publicly available SBC 304-2007 text: rib width >= 100 mm,
 * rib depth <= 3.5*width, clear spacing <= 800 mm (§8.11.2/.3), corroborated by
 * ACI 318-19 §8.8. The engine's 700 mm limit, 0.25 shear coefficient and
 * partial-factor format are SBC 304-2024 claims that could not be verified;
 * those assertions are characterization of current behaviour, not code compliance.
 *
 * `span` is treated by the engine as metres (`span * 1000 / 4`), although the
 * `HordiSlabDesign` interface comments and existing callers use mm. That unit
 * mismatch is flagged as a follow-up, not changed in this batch.
 */
class SBCJoistSlabTest {

    private fun design(
        fcu: Double = 30.0,
        fy: Double = 420.0,
        ribWidth: Double = 150.0,
        ribSpacing: Double = 600.0,
        totalThickness: Double = 250.0,
        toppingThickness: Double = 60.0,
        // default 3.0 m (was 6.0): l/h = 3000/250 = 12 <= 16 (Table 9.3.1.1 SS,
        // fy=420 factor 1.0) so characterization tests stay on the passing side;
        // beff is min(ribSpacing, span*250+bw) and unchanged for span >= 1.8 m.
        span: Double = 3.0,
        designMoment: Double = 45.0,
        designShear: Double = 25.0,
        supportCondition: com.civileg.app.domain.calculations.base.SlabSupportCondition =
            com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED,
        loadingApplicability: com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability =
            com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS
    ) = SBCJoistSlab().designHordiSlab(
        fcu = fcu,
        fy = fy,
        ribWidth = ribWidth,
        ribSpacing = ribSpacing,
        totalThickness = totalThickness,
        toppingThickness = toppingThickness,
        span = span,
        designMoment = designMoment,
        designShear = designShear,
        loadCombination = LoadCombination.DEAD_LIVE,
        supportCondition = supportCondition,
        loadingApplicability = loadingApplicability
    )

    private fun expectGuard(name: String, block: () -> Unit) {
        val error = try {
            block()
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertNotNull("Expected InputGuard failure for '$name'", error)
        assertTrue("Guard message must name '$name'", error!!.message!!.contains("Input '$name'"))
    }

    @Test
    fun `golden vector flange case fcd fsd as and shear`() {
        val r = design()

        // Hand computation: fc=30/1.5=20, fsd=420/1.15=365.217, d=250-20-10=220,
        // beff=min(600, 6*1000/4+150)=600, MuFlange=0.9*0.85*20*600*60*190=104.652 kNm.
        assertEquals(657.4708, r.requiredReinforcement, 1e-3)
        assertEquals(452.3893, r.providedReinforcement, 1e-3)
        assertEquals(12.0, r.barDiameter, 1e-9)
        assertEquals(600.0, r.barSpacing, 1e-9)
        assertEquals(27.671341, r.shearCapacity, 1e-5)
        assertEquals(0.903462, r.utilizationRatio, 1e-5)
        assertTrue(r.isSafe)
        assertTrue("Unexpected warnings: ${r.warnings}", r.warnings.isEmpty())

        val notes = r.codeNotes.joinToString("\n")
        assertTrue(notes.contains("Neutral axis in flange"))
        assertTrue(notes.contains("fcd="))
        assertTrue(notes.contains("beff=600 mm"))
        assertFalse(notes.contains("SBC 304-8.4.7"))
        assertTrue(notes.contains("SBC 304-4.2.2"))
        assertTrue(notes.contains("Shear coeff=0.25 (SBC 304-4.3.2)"))
    }

    @Test
    fun `neutral axis transition at Mu flange boundary and web fallback`() {
        val atBoundary = design(designMoment = 104.652)
        assertTrue(atBoundary.codeNotes.any { it.contains("Neutral axis in flange") })
        assertEquals(1675.7143, atBoundary.requiredReinforcement, 1e-2)

        val justAbove = design(designMoment = 104.653)
        assertTrue(justAbove.codeNotes.any { it.contains("Neutral axis in web") })
        assertEquals(1675.7333, justAbove.requiredReinforcement, 1e-2)
        assertTrue(justAbove.warnings.any { it.contains("ρ=") && it.contains("over-reinforced") })

        val compressionFailure = design(designMoment = 140.0)
        assertEquals(2081.7857, compressionFailure.requiredReinforcement, 1e-2)
        assertTrue(compressionFailure.warnings.any { it.contains("compression failure", ignoreCase = true) })
    }

    @Test
    fun `geometry limits at inclusive boundaries and just beyond`() {
        val atMinimums = design(ribWidth = 100.0, ribSpacing = 800.0, designShear = 10.0)
        assertTrue("Unexpected warnings: ${atMinimums.warnings}", atMinimums.warnings.isEmpty())
        assertTrue(atMinimums.isSafe)

        val narrow = design(ribWidth = 99.9, ribSpacing = 800.0, designShear = 10.0)
        assertTrue(narrow.warnings.any { it.contains("8.4.2") })
        assertFalse(narrow.isSafe)

        val depthAtLimit = design(totalThickness = 410.0, ribWidth = 100.0, ribSpacing = 800.0, designShear = 10.0)
        assertFalse(depthAtLimit.warnings.any { it.contains("8.4.3") })
        val tooDeep = design(totalThickness = 411.0, ribWidth = 100.0, ribSpacing = 800.0, designShear = 10.0)
        assertTrue(tooDeep.warnings.any { it.contains("8.4.3") })

        val spacingAtLimit = design(ribSpacing = 850.0, designShear = 10.0)
        assertFalse(spacingAtLimit.warnings.any { it.contains("8.4.1") })
        val spacingOver = design(ribSpacing = 850.1, designShear = 10.0)
        assertTrue(spacingOver.warnings.any { it.contains("8.4.1") })

        val toppingAtLimit = design(toppingThickness = 50.0, designShear = 10.0)
        assertFalse(toppingAtLimit.warnings.any { it.contains("8.4.7") })
        val toppingUnder = design(toppingThickness = 49.9, designShear = 10.0)
        assertTrue(toppingUnder.warnings.any { it.contains("8.4.7") })
    }

    @Test
    fun `shear capacity boundary and joist enhancement factor cap`() {
        val safe = design(designShear = 27.671341)
        assertTrue(safe.isSafe)
        assertEquals(1.0, safe.utilizationRatio, 1e-5)

        val unsafe = design(designShear = 27.672)
        assertFalse(unsafe.isSafe)
        assertTrue(unsafe.warnings.any { it.contains("Design shear") })

        // Characterization: 10% enhancement for close ribs (UNVERIFIED-2024).
        val enhanced = design(ribWidth = 200.0, ribSpacing = 250.0, designMoment = 30.0, designShear = 10.0)
        assertEquals(39.662256, enhanced.shearCapacity, 1e-4)
        assertTrue(enhanced.codeNotes.any { it.contains("joist factor=") })

        val capped = design(ribWidth = 200.0, ribSpacing = 200.0, designMoment = 30.0, designShear = 10.0)
        assertEquals(40.584634, capped.shearCapacity, 1e-4)
    }

    @Test
    fun `input guards reject zero negative and non finite values`() {
        expectGuard("fcu") { design(fcu = 0.0) }
        expectGuard("fcu") { design(fcu = Double.NaN) }
        expectGuard("fy") { design(fy = -420.0) }
        expectGuard("ribWidth") { design(ribWidth = 0.0) }
        expectGuard("ribSpacing") { design(ribSpacing = 0.0) }
        expectGuard("totalThickness") { design(totalThickness = 0.0) }
        expectGuard("toppingThickness") { design(toppingThickness = 0.0) }
        expectGuard("span") { design(span = 0.0) }
        expectGuard("designMoment") { design(designMoment = -1.0) }
        expectGuard("designShear") { design(designShear = -1.0) }
    }

    @Test
    fun `cross code divergence shear coefficient geometry limits and reinforcement`() {
        val sbc = SBCJoistSlab()
        val aci = ACIJoistSlab()
        val ecp = ECPHordiSlabDesign()

        fun run(engine: com.civileg.app.domain.calculations.base.HordiSlabDesign, spacing: Double) =
            engine.designHordiSlab(
                fcu = 30.0, fy = 420.0, ribWidth = 150.0, ribSpacing = spacing,
                totalThickness = 250.0, toppingThickness = 60.0, span = 6.0,
                designMoment = 45.0, designShear = 10.0,
                loadCombination = LoadCombination.DEAD_LIVE
            )

        val sbcResult = run(sbc, 600.0)
        val aciResult = run(aci, 600.0)
        val ecpResult = run(ecp, 600.0)

        // Per-code shear formats with their own hand-computed expectations:
        // SBC 0.25·√(fcu/γc)·bw·d·φ with d=220 (fixed cover20+halfBar10, non-live engine);
        // ACI 0.17·√(0.8fcu)·bw·d with d=250−25−6=219 (caller cover+bar radius) and φ·1.1
        // joist factor; ECP Eq.4-29 concrete-only 0.16·√(fcu/γc)·bw·d with NO φ factor
        // and d=211 from interface defaults (cover25+link8+bar12/2).
        assertEquals(0.25 * sqrt(30.0 / 1.5) * 150.0 * 220.0 / 1000.0 * 0.75, sbcResult.shearCapacity, 1e-9)
        assertEquals(0.17 * sqrt(30.0 * 0.8) * 150.0 * 219.0 / 1000.0 * 0.75 * 1.1, aciResult.shearCapacity, 1e-9)
        assertEquals(0.16 * sqrt(30.0 / 1.5) * 150.0 * 211.0 / 1000.0, ecpResult.shearCapacity, 1e-9)
        assertTrue("SBC shear=${sbcResult.shearCapacity}, ACI shear=${aciResult.shearCapacity}",
            sbcResult.shearCapacity > aciResult.shearCapacity)

        // Different flexural widths: ECP web-only estimate (no flange credit) cannot
        // solve this demand on a 150 mm rib and must say so; SBC effective flange and
        // ACI full-spacing estimates stay finite with SBC > ACI.
        assertTrue(ecpResult.requiredReinforcement.isInfinite())
        assertFalse(ecpResult.isSafe)
        assertTrue(ecpResult.warnings.any { it.contains("Flexure") })
        assertTrue(sbcResult.requiredReinforcement > aciResult.requiredReinforcement)

        // Geometry characterization: SBC rejects 750 mm clear spacing, while
        // ACI accepts it. The ECP estimate reports the unsolvable web-only
        // flexure demand explicitly (infinite required area, zero provided).
        // The separate ECP2020 validator covers verified geometry boundaries.
        val sbcWide = run(sbc, 900.0)
        val aciWide = run(aci, 900.0)
        val ecpWide = run(ecp, 900.0)
        assertTrue(sbcWide.warnings.any { it.contains("8.4.1") })
        assertFalse(sbcWide.isSafe)
        assertTrue(aciWide.isSafe)
        assertFalse(ecpWide.isSafe)
        assertTrue(ecpWide.providedReinforcement < ecpWide.requiredReinforcement)
        assertTrue(ecpWide.utilizationRatio > 1.0)
        assertTrue(ecpWide.warnings.any { it.contains("less than") })
    }

    @Test
    fun `effective flange width uses span over four when smaller than spacing`() {
        val shortSpan = design(span = 1.5)
        assertTrue(shortSpan.codeNotes.any { it.contains("beff=525 mm") })
        assertEquals(663.1996, shortSpan.requiredReinforcement, 1e-3)

        val longSpan = design(span = 6.0)
        assertTrue(longSpan.codeNotes.any { it.contains("beff=600 mm") })
    }

    @Test
    fun `Table 9-3-1-1 deemed-to-satisfy failure gates isSafe`() {
        // l/h = 6000/250 = 24 > 16 (simply supported, fy=420) -> fail
        val r = design(span = 6.0)
        assertTrue(r.serviceabilityAssessed)
        assertFalse(r.isSafe)
        assertTrue(r.warnings.any { it.contains("Table 9.3.1.1") })
        // both ends continuous: limit 21; l/h = 4000/250 = 16 <= 21 -> pass
        val cont = design(span = 4.0,
            supportCondition = com.civileg.app.domain.calculations.base.SlabSupportCondition.BOTH_ENDS_CONTINUOUS)
        assertTrue(cont.serviceabilityAssessed)
        assertTrue(cont.warnings.none { it.contains("Table 9.3.1.1") })
    }

    @Test
    fun `ACI deemed-to-satisfy applies Table 9-3-1-1 with own citation and gate`() {
        val aci = ACIJoistSlab()
        val fail = aci.designHordiSlab(
            fcu = 30.0, fy = 420.0, ribWidth = 150.0, ribSpacing = 600.0,
            totalThickness = 250.0, toppingThickness = 60.0, span = 6000.0,
            designMoment = 45.0, designShear = 10.0, loadCombination = LoadCombination.DEAD_LIVE,
            supportCondition = com.civileg.app.domain.calculations.base.SlabSupportCondition.SIMPLY_SUPPORTED,
            loadingApplicability = com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS
        )
        assertTrue(fail.serviceabilityAssessed)
        assertFalse(fail.isSafe)
        assertTrue(fail.warnings.any { it.contains("ACI 318M-14 Table 9.3.1.1") })
        val unselected = aci.designHordiSlab(
            fcu = 30.0, fy = 420.0, ribWidth = 150.0, ribSpacing = 600.0,
            totalThickness = 250.0, toppingThickness = 60.0, span = 6000.0,
            designMoment = 45.0, designShear = 10.0, loadCombination = LoadCombination.DEAD_LIVE,
            supportCondition = com.civileg.app.domain.calculations.base.SlabSupportCondition.UNSPECIFIED,
            loadingApplicability = com.civileg.app.domain.calculations.base.DeflectionLoadingApplicability.ORDINARY_NO_SENSITIVE_ELEMENTS
        )
        assertFalse(unselected.serviceabilityAssessed)
    }

    @Test
    fun `zero moment and shear are allowed by guards and produce finite output`() {
        val r = design(designMoment = 0.0, designShear = 0.0)
        assertEquals(0.0, r.requiredReinforcement, 1e-9)
        assertEquals(113.0973, r.providedReinforcement, 1e-3)
        assertEquals(0.0, r.utilizationRatio, 1e-9)
        assertTrue(r.isSafe)
        assertTrue(r.warnings.isEmpty())
        assertEquals(sqrt(20.0) * 0.25 * 150.0 * 220.0 / 1000.0 * 0.75, r.shearCapacity, 1e-9)
    }
}
