package com.civileg.app.domain.calculations

import com.civileg.app.domain.calculations.aci.ACIFlatSlab
import com.civileg.app.domain.calculations.aci.ACIWaffleSlabDesign
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.ecp.ECPFlatSlab
import com.civileg.app.domain.calculations.ecp.ECPSeismic
import com.civileg.app.domain.calculations.ecp.ECPStrapFooting
import com.civileg.app.domain.calculations.ecp.ECPWaffleSlabDesign
import com.civileg.app.domain.calculations.aci.ACISeismic
import com.civileg.app.domain.calculations.aci.ACIStrapFooting
import com.civileg.app.domain.calculations.sbc.SBCSeismic
import com.civileg.app.domain.calculations.sbc.SBCStrapFooting
import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.calculations.base.SoilType
import com.civileg.app.domain.calculations.base.SeismicZone
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.utils.CalculatorEngine
import com.civileg.core.engineering.StrapFootingDesignEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the technical-office detail expansion (P1):
 * - strap footing is a real design (reactions, checks, steps, code notes) instead of a stub
 * - waffle slab (ECP/ACI) publishes structured safety checks
 * - flat slab overall safety includes flexural capacity
 * - seismic results carry a computed storey drift and drift verdict
 */
class EngineeringDetailExpansionTest {

    // ─── Strap footing ───────────────────────────────────────────────────

    private val strapInputs = StrapFootingDesignEngine.Inputs(
        column1Load = 500.0, column2Load = 300.0,
        distanceBetweenColumns = 4000.0,
        column1Width = 300.0, column1Depth = 500.0,
        column2Width = 300.0, column2Depth = 500.0,
        soilBearingCapacity = 150.0, fcu = 25.0, fy = 360.0,
        strapBeamWidth = 400.0
    )

    @Test
    fun `strap footing produces real reactions, checks and steps`() {
        val r = ECPStrapFooting().design(strapInputs)
        assertTrue("R1 must be positive", r.reactions.first > 0.0)
        assertTrue("R2 must be positive", r.reactions.second > 0.0)
        assertEquals("Vertical equilibrium", 800.0, r.reactions.first + r.reactions.second, 0.5)
        assertTrue("soil pressure 1 must be positive", r.soilPressure1 > 0.0)
        assertTrue("punching capacity must be computed", r.punchingCapacity1 > 0.0)
        assertTrue("strap moment must be computed", r.strapMoment > 0.0)
        assertTrue("calculation steps must be populated", r.calculationSteps.isNotEmpty())
        assertTrue("strap top steel must be provided", r.strapBeam.topReinforcement.areaProvided > 0.0)
    }

    @Test
    fun `strap footing is code-differentiated in its notes`() {
        val ecp = ECPStrapFooting().design(strapInputs)
        val aci = ACIStrapFooting().design(strapInputs)
        val sbc = SBCStrapFooting().design(strapInputs)
        assertTrue(ecp.codeNotes.any { it.contains("ECP 203") })
        assertTrue(aci.codeNotes.any { it.contains("ACI 318") })
        assertTrue(sbc.codeNotes.any { it.contains("SBC 304") })
    }

    @Test
    fun `strap footing reinforcement is not hardcoded`() {
        val light = ECPStrapFooting().design(strapInputs.copy(column1Load = 200.0, column2Load = 150.0))
        val heavy = ECPStrapFooting().design(strapInputs.copy(column1Load = 1200.0, column2Load = 900.0))
        assertTrue(
            "Heavier load must yield a larger edge footing",
            heavy.footing1.length * heavy.footing1.width > light.footing1.length * light.footing1.width
        )
        assertTrue(
            "Heavier load must yield more strap steel",
            heavy.strapBeam.topReinforcement.areaProvided >= light.strapBeam.topReinforcement.areaProvided
        )
    }

    // ─── Waffle slab ─────────────────────────────────────────────────────

    @Test
    fun `waffle slab ECP publishes safety checks`() {
        val r = ECPWaffleSlabDesign().design(WaffleSlabDesign.WaffleSlabInput())
        assertTrue("ECP waffle should expose >= 4 checks, got ${r.safetyChecks.size}", r.safetyChecks.size >= 4)
        assertTrue(r.safetyChecks.any { it.name.contains("Rib flexure") })
        assertTrue(r.safetyChecks.any { it.name.contains("Deflection") })
    }

    @Test
    fun `waffle slab ACI publishes safety checks`() {
        val r = ACIWaffleSlabDesign().design(WaffleSlabDesign.WaffleSlabInput())
        assertTrue("ACI waffle should expose >= 4 checks, got ${r.safetyChecks.size}", r.safetyChecks.size >= 4)
        assertTrue(r.safetyChecks.any { it.name.contains("Punching") })
    }

    // ─── Flat slab ───────────────────────────────────────────────────────

    @Test
    fun `flat slab overall safety includes flexural checks`() {
        val r = ECPFlatSlab().design(FlatSlabInput())
        assertTrue(
            "ECP flat slab must expose flexure checks",
            r.safetyChecks.any { it.name.contains("Col-strip top") }
        )
        assertTrue(
            "ECP flat slab must expose flexure checks",
            r.safetyChecks.any { it.name.contains("Mid-strip bottom") }
        )
        val flexureOk = r.safetyChecks
            .filter { it.name.contains("Col-strip") || it.name.contains("Mid-strip") }
            .all { it.passed }
        assertTrue("if any flexure check fails, overall must be unsafe", flexureOk || !r.isSafe)
    }

    @Test
    fun `flat slab ACI exposes flexure checks`() {
        val r = ACIFlatSlab().design(FlatSlabInput())
        assertTrue(r.safetyChecks.any { it.name.contains("Col-strip top") })
    }

    // ─── Seismic drift ───────────────────────────────────────────────────

    @Test
    fun `seismic engines compute storey drift`() {
        val ecp = ECPSeismic().calculateBaseShear(50000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0)
        val aci = ACISeismic().calculateBaseShear(50000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0)
        val sbc = SBCSeismic().calculateBaseShear(50000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 30.0)

        listOf(ecp, aci, sbc).forEach { r ->
            assertTrue("period must be computed", r.fundamentalPeriod > 0.0)
            assertTrue("roof displacement must be positive", r.roofDisplacement > 0.0)
            assertTrue("storey drift must be positive", r.maxStoryDrift > 0.0)
            assertTrue("allowable drift must be positive", r.allowableDrift > 0.0)
            assertTrue("drift ratio must be positive", r.driftRatio > 0.0)
            assertEquals(
                "drift verdict must match the computed drift",
                r.maxStoryDrift <= r.allowableDrift,
                r.driftIsSafe
            )
        }
    }

    @Test
    fun `seismic drift grows with building height`() {
        val short = ECPSeismic().calculateBaseShear(50000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 9.0)
        val tall = ECPSeismic().calculateBaseShear(50000.0, SeismicZone.ZONE_3, SoilType.C, 1.0, 5.0, 45.0)
        assertTrue("taller building should drift more", tall.roofDisplacement > short.roofDisplacement)
        assertFalse("finite drift values expected", tall.maxStoryDrift.isNaN() || tall.maxStoryDrift.isInfinite())
    }

    // ─── Beam / column traceability ──────────────────────────────────────

    private val engine = CalculatorEngine(io.mockk.mockk(relaxed = true))

    @Test
    fun `beam result exposes calculation steps, warnings and code notes`() {
        val r = engine.designBeam(
            width = 300.0, height = 600.0, span = 6.0, fcu = 30.0, fy = 420.0,
            deadLoad = 15.0, liveLoad = 10.0, preferredDiameter = 16,
            code = DesignCode.ECP, supportType = CalculatorEngine.SupportType.HINGED_HINGED
        )
        assertEquals("six traceable design steps expected", 6, r.calculationSteps.size)
        assertTrue("code notes must be surfaced", r.codeNotes.isNotEmpty())
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertEquals("step numbering must be sequential", (1..6).toList(), r.calculationSteps.map { it.stepNumber })
    }

    @Test
    fun `column result exposes calculation steps and code notes`() {
        val r = engine.designColumn(
            width = 400.0, depth = 600.0, pu = 1500.0, mx = 120.0, my = 80.0,
            fcu = 30.0, fy = 420.0, code = DesignCode.ECP
        )
        assertEquals("five traceable design steps expected", 5, r.calculationSteps.size)
        assertTrue("code notes must be surfaced", r.codeNotes.isNotEmpty())
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertTrue("eccentricity step must exist", r.calculationSteps.any { it.title == "Eccentricity" })
    }
}
