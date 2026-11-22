package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.entities.LoadCombination
import com.civileg.core.engine.rc.ecp.*
import org.junit.Assert.*
import org.junit.Test

/** Source-section integration vectors. Whole-member acceptance remains a separate task. */
class ECPHordiSourceSectionTest {
    private fun context() = Ecp203RibbedSection.Context(25.0, 20.0,
        listOf(Ecp203BarLayout.Layer(2,20.0)), Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
        Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION, 5000.0, 3.0,
        Ecp203WebShear.VerticalLinks(240.0, Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD,8.0,2,200.0))
    private fun design(c: Ecp203RibbedSection.Context = context(), moment: Double = 62.0,
        combo: LoadCombination = LoadCombination.DEAD_LIVE, shear: Double = 10.0) = ECPHordiSlabWrapper().designHordiSlab(
        30.0,400.0,200.0,600.0,350.0,60.0,5000.0,moment,shear,combo,c)

    @Test fun `wrapper forwards actual context and returns source equilibrium not legacy phi result`() {
        val result = design()
        assertEquals(611.1777815875193,result.requiredReinforcement,1e-8)
        assertEquals(628.3185307179587,result.providedReinforcement,1e-8)
        assertEquals(20.0,result.barDiameter,0.0)
        assertTrue(result.utilizationRatio < 1.0)
        assertTrue(result.codeNotes.any { it.contains("307.0 mm") })
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
    }
    @Test fun `actual cover is not replaced with h minus thirty`() {
        assertTrue(design(context().copy(coverToOutsideLinksMm = 45.0)).utilizationRatio > 1.0)
    }
    @Test fun `factored forces do not get multiplied by an approximate load factor again`() {
        assertEquals(design(combo = LoadCombination.DEAD_ONLY).requiredReinforcement,
            design(combo = LoadCombination.DEAD_LIVE).requiredReinforcement,0.0)
    }
    @Test fun `negative section cannot use positive flange strength`() {
        assertTrue(design(context().copy(region=Ecp203RibbedSection.Region.FLANGE_IN_TENSION,
            contraflexureDistanceMm=null)).warnings.any { it.contains("fail the section flexure") })
    }
    @Test(expected = IllegalArgumentException::class)
    fun `ECP section context is not silently applied by an ACI engine`() {
        com.civileg.app.domain.calculations.aci.ACIJoistSlab().designHordiSlab(
            30.0,400.0,200.0,600.0,350.0,60.0,5000.0,62.0,10.0,
            LoadCombination.DEAD_LIVE,context())
    }
    @Test(expected = IllegalArgumentException::class)
    fun `impossible actual bar fit is not capped at four or silently redrawn`() {
        design(context().copy(tensionLayers=listOf(Ecp203BarLayout.Layer(10,20.0))))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `impossible design moment gives explicit unsupported domain not a fabricated numeric result`() {
        design(moment=10000.0)
    }
    @Test fun `wrapper retains source equations and distinct service loading without granting member approval`() {
        val result = design(context().copy(characteristicLiveLoadKnM2 = 8.0,
            immediateDeflectionInput = Ecp203RibbedSection.ImmediateDeflectionInput(5000.0, 6.0)))
        assertTrue(result.codeNotes.any { it.contains("deltaLive=deltaD+L-deltaD=3.94892 mm") && it.contains("Eq4-77") })
        assertTrue(result.codeNotes.any { it.contains("Ec=4400*sqrt(fcu)") && it.contains("Eq2-1") })
        assertTrue(result.codeNotes.any { it.contains("qD=3.60000 kN/m/rib") && it.contains("qD+L=8.40000") })
        assertEquals(design().requiredReinforcement, result.requiredReinforcement, 0.0)
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
        assertTrue(result.warnings.any { it.contains("Long-term deformation") })
    }
    @Test fun `long term equations and independent limits reach wrapper without member approval`() {
        val lt = Ecp203LongTermDeflection.Input(4800.0, 0.7,
            Ecp203LongTermDeflection.Duration.ofMonths(60),
            Ecp203LongTermDeflection.Attachment.DEFLECTION_SENSITIVE,
            Ecp203LongTermDeflection.Duration.ofMonths(6), true, 1.5, 0.5)
        val c = context().copy(characteristicLiveLoadKnM2 = 8.0,
            immediateDeflectionInput = Ecp203RibbedSection.ImmediateDeflectionInput(5000.0, 6.0, lt))
        val result = design(c)
        assertTrue(result.codeNotes.any { it.contains("Mechanical total=deltaD+L+alphaEnd*deltaS=11.6484 mm") })
        assertTrue(result.codeNotes.any { it.contains("Eq4-80") && it.contains("=4.39733 mm") })
        assertTrue(result.codeNotes.any { it.contains("post-installation=4.89733 mm") && it.contains("status=PASS") })
        assertTrue(result.safetyAssessmentBlocked)
        assertFalse(result.isSafe)
        assertEquals(design().requiredReinforcement, result.requiredReinforcement, 0.0)
    }
    @Test fun `missing thermal data remains unknown while actual failed limits receive cited advice`() {
        val lt = Ecp203LongTermDeflection.Input(4800.0, 0.7,
            Ecp203LongTermDeflection.Duration.ofMonths(60), Ecp203LongTermDeflection.Attachment.NONE, null, true)
        fun c(input: Ecp203LongTermDeflection.Input) = context().copy(characteristicLiveLoadKnM2 = 8.0,
            immediateDeflectionInput = Ecp203RibbedSection.ImmediateDeflectionInput(5000.0, 6.0, input))
        val unknown = design(c(lt))
        assertTrue(unknown.codeNotes.any { it.contains("status=NOT_EVALUATED (Eq4-74)") })
        assertTrue(unknown.warnings.any { it.contains("Blank thermal input is not zero") })
        assertFalse(unknown.warnings.any { it.contains("revise actual section stiffness or loading") })
        val failed = design(c(lt.copy(thermalTotalMm = 25.0)))
        assertTrue(failed.warnings.any { it.contains("clear span/250") && it.contains("Eqs4-73/74") })
    }

    @Test fun `failed shear recommends section revision rather than extra links above concrete maximum`() {
        val result = design(shear = 1000.0)
        assertTrue(result.warnings.any { it.contains("extra links cannot exceed the concrete maximum") && it.contains("Eq4-27") })
    }
    @Test fun `failed link detailing advice carries its actual source`() {
        val c = context().copy(links = context().links.copy(diameterMm = 6.0))
        val result = design(c)
        assertTrue(result.warnings.any { it.contains("diameter>=8 mm") && it.contains("clause4-2-2-1-6") })
    }
    @Test fun `elastic inner layer cannot pass using centroid yield approximation`() {
        val c = context().copy(tensionLayers = List(7) { Ecp203BarLayout.Layer(1, 8.0) },
            region = Ecp203RibbedSection.Region.FLANGE_IN_TENSION, contraflexureDistanceMm = null)
        val result = design(c, moment = 21.5)
        assertTrue(result.utilizationRatio > 1.0)
        assertTrue(result.warnings.any { it.contains("fail the section flexure") })
        assertTrue(result.codeNotes.any { it.contains("all layers at design yield=false") })
    }
}
