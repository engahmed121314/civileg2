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
import com.civileg.app.domain.PileInput
import com.civileg.app.domain.ShearWallInput
import com.civileg.app.domain.calculations.base.SoilType
import com.civileg.app.domain.calculations.base.SeismicZone
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.calculations.utils.CalculatorEngine
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
        // Only checks backed by a computed demand are listed. Deflection is not assessed, so it must not appear as a check.
        // A check whose demand is not computed is left out and reported as NOT ASSESSED in the warnings, so each of the two
        // (rib, punching) is either listed or named in a NOT ASSESSED warning.
        assertTrue("ECP waffle lists at most its 2 computed checks, got ${r.safetyChecks.size}", r.safetyChecks.size in 1..2)
        val ribListed = r.safetyChecks.any { it.name.contains("Rib flexure") }
        val punchListed = r.safetyChecks.any { it.name.contains("Punching") }
        assertTrue("every listed check is rib or punching", r.safetyChecks.all { it.name.contains("Rib flexure") || it.name.contains("Punching") })
        if (!ribListed) assertTrue(r.warnings.any { it.startsWith("NOT ASSESSED") && it.contains("ib", ignoreCase = true) })
        if (!punchListed) assertTrue(r.warnings.any { it.startsWith("NOT ASSESSED") && it.contains("unch", ignoreCase = true) })
        assertTrue(r.safetyChecks.none { it.name.contains("Deflection") })
    }

    @Test
    fun `waffle slab ACI publishes safety checks`() {
        val r = ACIWaffleSlabDesign().design(WaffleSlabDesign.WaffleSlabInput())
        assertTrue("ACI waffle should expose its 2 computed checks, got ${r.safetyChecks.size}", r.safetyChecks.size == 2)
        assertTrue(r.safetyChecks.any { it.name.contains("Rib flexure") })
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
    fun `flat slab steel weight follows the dimensional hand calculation in every code`() {
        // Square panel with equal column sizes: ln_x = ln_y, so the engines' Y steel equals the X steel.
        // The strip areas are totals in mm2 over each strip width; mm2 x mm = mm3; / 1e9 = m3; x 7850 kg/m3.
        val input = FlatSlabInput(lx = 6000.0, ly = 6000.0, columnWidth = 400.0, columnDepth = 400.0)
        val results = listOf(
            "ECP" to ECPFlatSlab().design(input),
            "ACI" to ACIFlatSlab().design(input),
            "SBC" to com.civileg.app.domain.calculations.sbc.SBCFlatSlab().design(input)
        )
        for ((name, r) in results) {
            val asX = r.columnStripTopRebar.providedArea + r.columnStripBotRebar.providedArea +
                r.middleStripTopRebar.providedArea + r.middleStripBotRebar.providedArea
            val expectedKg = 2.0 * asX * (6000.0 + 6000.0) / 1e9 * 7850.0
            assertEquals("$name steel kg/panel", expectedKg, r.steelWeightPerPanel, 1e-6 * expectedKg + 1e-9)
            assertTrue("$name steel must be a few hundred kg for a 36 m2 panel, not 1000x", r.steelWeightPerPanel < 5000.0)
        }
    }

    @Test
    fun `flat slab ACI exposes flexure checks`() {
        val r = ACIFlatSlab().design(FlatSlabInput())
        assertTrue(r.safetyChecks.any { it.name.contains("Col-strip top") })
    }

    @Test
    fun `flat slab engines publish calculation steps`() {
        val ecp = ECPFlatSlab().design(FlatSlabInput())
        val aci = ACIFlatSlab().design(FlatSlabInput())
        listOf(ecp, aci).forEach { r ->
            assertEquals("six traceable steps expected", 6, r.calculationSteps.size)
            assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
            assertTrue("punching step must exist", r.calculationSteps.any { it.title == "Punching Shear" })
        }
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

    @Test
    fun `slab result exposes calculation steps`() {
        val r = engine.designSlab(
            lx = 4.0, ly = 5.0, deadLoad = 5.0, liveLoad = 3.0, fcu = 30.0, fy = 420.0,
            ts = 160.0, preferredDiameter = 12, code = DesignCode.ECP
        )
        assertEquals("six traceable design steps expected", 6, r.calculationSteps.size)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertTrue("flexural steel step must exist", r.calculationSteps.any { it.title == "Main Flexural Steel" })
    }

    @Test
    fun `unstable slender column is never reported safe`() {
        val r = engine.designColumn(
            width = 200.0, depth = 200.0, pu = 3000.0, fcu = 25.0, fy = 360.0,
            code = DesignCode.ECP, clearHeight = 6000.0
        )
        assertFalse("Pu above the buckling load must not pass", r.isSafe)
    }

    @Test
    fun `unstable column output carries no infinity or NaN`() {
        val r = engine.designColumn(width = 200.0, depth = 200.0, pu = 3000.0, fcu = 25.0, fy = 360.0, code = DesignCode.ECP, clearHeight = 6000.0)
        val nums = listOf(r.pu, r.mx, r.my, r.axialCapacity, r.mxCapacity, r.myCapacity, r.slenderness, r.utilizationRatio, r.steelWeight, r.cost)
        assertTrue(nums.all { it.isFinite() })
        assertTrue(r.safetyChecks.all { it.value.isFinite() && it.limit.isFinite() })
        assertTrue(r.calculationSteps.none { it.formulaWithValues.contains("Infinity") || it.result.contains("Infinity") })
    }

    @Test
    fun `tank delivers the requested capacity at the design water depth`() {
        val rect = engine.designTank(CalculatorEngine.TankType.RECTANGULAR_GROUND, capacity = 50.0, heightM = 3.0, fcu = 25.0, fy = 360.0)
        assertEquals(50.0, rect.capacity, 0.01)
        assertEquals(50.0, rect.length * rect.width * 2.7, 0.01)
        val circ = engine.designTank(CalculatorEngine.TankType.CIRCULAR_GROUND, capacity = 50.0, heightM = 3.0, fcu = 25.0, fy = 360.0)
        val d = circ.length
        assertEquals(50.0, Math.PI * d * d / 4.0 * 2.7, 0.01)
        assertEquals(50.0, circ.capacity, 0.01)
        assertEquals("height stays in metres", 3.0, rect.height, 1e-9)
    }

    @Test
    fun `tank quantities agree with the same engine result in metres`() {
        val rect = engine.designTank(CalculatorEngine.TankType.RECTANGULAR_GROUND, capacity = 50.0, heightM = 3.0, fcu = 25.0, fy = 360.0)
        val rows = com.civileg.app.domain.calculations.utils.BoqEstimator.tankRows(rect, "T")
        assertEquals(2 * (rect.length + rect.width) * rect.height, rows[2].quantity, 1e-6)
        assertEquals(rect.length * rect.width, rows[3].quantity, 1e-6)
        assertTrue("plan side is metres, not millimetres", rect.length in 1.0..30.0)
        val bbs = com.civileg.app.domain.calculations.utils.BbsGenerator.generateTankBbs("T", rect)
        assertTrue(bbs.isNotEmpty() && bbs.all { it.totalWeightKg.isFinite() && it.totalWeightKg > 0.0 })
        val circ = engine.designTank(CalculatorEngine.TankType.CIRCULAR_GROUND, capacity = 50.0, heightM = 3.0, fcu = 25.0, fy = 360.0)
        val crows = com.civileg.app.domain.calculations.utils.BoqEstimator.tankRows(circ, "T")
        assertEquals(Math.PI * circ.length * circ.height, crows[2].quantity, 1e-6)
        assertEquals(Math.PI * circ.length * circ.length / 4.0, crows[3].quantity, 1e-6)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            com.civileg.app.domain.calculations.utils.BbsGenerator.generateTankBbs("T", circ)
        }
    }

    @Test
    fun `strap footing verdict includes footing flexure and the unbounded ratio`() {
        val r = engine.calculateStrapFooting(
            col1Load = 1500.0, col2Load = 2000.0, distance = 5000.0,
            col1W = 300.0, col1D = 600.0, col2W = 300.0, col2D = 700.0,
            soil = 150.0, fcu = 25.0, fy = 360.0, code = DesignCode.ECP, preferredDiameter = 18
        )
        assertTrue(r.safetyChecks.any { it.name == "Flexure Steel - Footing 1" })
        assertTrue(r.safetyChecks.any { it.name == "Flexure Steel - Footing 2" })
        if (r.safetyChecks.any { !it.isSafe }) assertFalse(r.isSafe)
        assertTrue(r.utilizationRatio.isFinite() && r.utilizationRatio >= 0.0)
    }

    @Test
    fun `ECP tank selected spacing provides at least the required steel unless the bar cap is reached`() {
        // Rounding spacing up used to deliver less steel than required while the bar count looked adequate.
        // A shortfall is only acceptable when the 20 bars/m cap (50 mm spacing) was hit: a genuine failure.
        for (h in listOf(2500.0, 3000.0, 3500.0, 4000.0)) {
            val res = com.civileg.app.domain.calculations.ecp.ECPTank().calculateTank(
                length = 4000.0, width = 4000.0, height = h, waterDepth = h - 300.0, fcu = 30.0, fy = 400.0,
                type = com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_GROUND
            )
            for ((name, r) in listOf("wall" to res.wallReinforcement, "base" to res.baseReinforcement)) {
                val capped = r.spacing <= 50.0
                assertTrue("h=$h $name provided ${r.astProvided} < required ${r.astRequired} without the bar cap", r.astProvided >= r.astRequired - 1e-6 || capped)
                assertTrue("$name spacing must be a positive multiple of 10 mm", r.spacing > 0.0 && r.spacing % 10.0 == 0.0)
            }
        }
    }

    @Test
    fun `ECP tank base punching surrogate is reported as not assessed with the exact hand capacity`() {
        // qp = 0.316*sqrt(fcu/1.5) [MPa]; b0 = 4*(t+d) [mm]; d = base - 50 cover - 8 (engine's assumed bar);
        // capacity = qp*b0*d/1000 [kN]. The perimeter used to stay in metres (1000x too small).
        val r = com.civileg.app.domain.calculations.ecp.ECPTank().calculateTank(
            length = 4000.0, width = 4000.0, height = 3500.0, waterDepth = 3200.0, fcu = 30.0, fy = 400.0,
            type = com.civileg.app.domain.calculations.base.TankType.RECTANGULAR_GROUND
        )
        assertTrue("surrogate must not be a safety verdict", r.safetyChecks.none { it.name.contains("Punching") })
        val note = r.baseReinforcement.codeNotes.first { it.contains("Base punching shear: NOT ASSESSED") }
        val d = r.baseThickness - 58.0
        val expected = 0.316 * Math.sqrt(30.0 / 1.5) * 4.0 * (r.wallThickness + d) * d / 1000.0
        val got = Regex("capacity=([0-9.]+) kN").find(note)!!.groupValues[1].toDouble()
        assertEquals(expected, got, 0.01)
    }

    @Test
    fun `tank height not above freeboard is rejected`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            engine.designTank(CalculatorEngine.TankType.RECTANGULAR_GROUND, capacity = 50.0, heightM = 0.3, fcu = 25.0, fy = 360.0)
        }
    }

    @Test
    fun `footing displayed bars cover the steel the flexure check reports as required per metre`() {
        // Flexure rows are per metre of footing: As = (1000/spacing) * bar area. An earlier version of this test compared
        // the total bars across the width with the per-metre requirement, so it passed while the screen showed a failed
        // row (1131 provided vs 1143 required, because spacing was clamped up to the 100 mm minimum).
        for (soil in listOf(100.0, 150.0, 250.0)) {
            for (p in listOf(800.0, 1200.0, 2000.0)) {
                val r = engine.calculateFooting(
                    type = CalculatorEngine.FootingType.ISOLATED, p = p, ps = p / 1.5, fcu = 25.0, fy = 360.0,
                    soil = soil, colB = 300.0, colT = 500.0, code = DesignCode.ECP
                )
                val flex = r.safetyChecks.first { it.name == "Flexure Steel" }
                val barArea = Math.PI / 4.0 * r.barDiameter * r.barDiameter
                val perMetre = 1000.0 / r.reinforcementBottom.spacing * barArea
                assertTrue("soil=$soil P=$p: row provided ${flex.value} < required ${flex.limit}", flex.value >= flex.limit - 1e-6)
                assertTrue("soil=$soil P=$p: displayed ${r.barDiameter} @ ${r.reinforcementBottom.spacing} gives $perMetre mm2/m vs required ${flex.limit}", perMetre >= flex.limit - 1e-6)
            }
        }
    }

    @Test
    fun `footing pressure times plan area equals the entered service load`() {
        // Hand calculation: the entered service load is 800 kN, so q * B * L must be 800 kN. The wrapper once printed
        // A = 1200/150 = 8.00 m2 beside a 6.1 m2 plan.
        val r = engine.calculateFooting(
            type = CalculatorEngine.FootingType.ISOLATED, p = 1200.0, ps = 800.0, fcu = 25.0, fy = 360.0,
            soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
        )
        val area = r.width * r.length / 1e6
        assertEquals(800.0, r.soilPressure * area, 0.5)
        val step1 = r.calculationSteps.first { it.stepNumber == 1 }
        assertTrue(step1.formulaWithValues, step1.formulaWithValues.contains("Ps = 800 kN (entered); Pu = 1200 kN (entered)"))
        assertTrue(step1.result, !step1.result.contains("8.00"))
    }

    @Test
    fun `footing drawing status never reads SAFE and a failing footing reads FAIL`() {
        val ok = engine.calculateFooting(
            type = CalculatorEngine.FootingType.ISOLATED, p = 1200.0, ps = 800.0, fcu = 25.0, fy = 360.0,
            soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
        )
        assertTrue(ok.isSafe)
        val okSummary = com.civileg.app.ui.compose.components.drawings.footingDrawingSummary(ok)
        assertFalse(okSummary.codeAcceptanceComplete)
        assertFalse(okSummary.listedChecksFailed)
        assertEquals("UNVERIFIED", okSummary.safetyLabel)
        val bad = ok.copy(isSafe = false)
        val badSummary = com.civileg.app.ui.compose.components.drawings.footingDrawingSummary(bad)
        assertFalse("incomplete stays incomplete when a check fails", badSummary.codeAcceptanceComplete)
        assertTrue(badSummary.listedChecksFailed)
        assertEquals("FAIL", badSummary.safetyLabel)
    }

    @Test
    fun `footing plan needs an entered service load and uses it instead of a ratio to Pu`() {
        // No Ps: no plan is designed, the error names the field.
        val e = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            engine.calculateFooting(
                type = CalculatorEngine.FootingType.ISOLATED, p = 1200.0, fcu = 25.0, fy = 360.0,
                soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
            )
        }
        assertTrue(e.message, e.message!!.contains("Service load Ps"))
        // Ps = 900 with the same Pu = 1200: pressure x area must be 900 (hand value), not 1200/1.5 = 800.
        val r = engine.calculateFooting(
            type = CalculatorEngine.FootingType.ISOLATED, p = 1200.0, ps = 900.0, fcu = 25.0, fy = 360.0,
            soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
        )
        assertEquals(900.0, r.soilPressure * r.width * r.length / 1e6, 0.5)
        assertEquals(900.0, r.serviceLoadKn!!, 1e-9)
    }

    @Test
    fun `footing engines reject moments together with an entered service load`() {
        for (code in listOf(DesignCode.ECP, DesignCode.ACI, DesignCode.SBC)) {
            val e = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                com.civileg.app.domain.calculations.CalculationFactory.getFootingDesign(code).designIsolatedFooting(
                    fcu = 25.0, fy = 360.0, columnWidth = 300.0, columnDepth = 500.0, axialLoad = 1200.0,
                    momentX = 50.0, momentY = 0.0, soilBearingCapacity = 150.0, footingDepth = 600.0,
                    loadCombination = com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE, serviceAxialLoad = 800.0
                )
            }
            assertTrue("$code: ${e.message}", e.message!!.contains("Moments are not supported"))
        }
    }

    @Test
    fun `footing net length note uses the design cover and the independently computed net steel`() {
        val r = engine.calculateFooting(
            type = CalculatorEngine.FootingType.ISOLATED, p = 1200.0, ps = 800.0, fcu = 25.0, fy = 360.0,
            soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
        )
        val netX = r.length - 2 * r.coverMm
        val netY = r.width - 2 * r.coverMm
        val kg = (r.barsX * netX + r.barsY * netY) / 1000.0 * (r.barDiameter * r.barDiameter / 162.0)
        val note = com.civileg.app.domain.calculations.utils.BbsGenerator.footingNetLengthNote(r)
        assertTrue(note, note.contains("X ${r.barsX} x ${"%.0f".format(netX)} mm"))
        assertTrue(note, note.contains("Y ${r.barsY} x ${"%.0f".format(netY)} mm"))
        assertTrue(note, note.contains("net steel ${"%.1f".format(kg)} kg"))
        assertTrue(note, note.contains("not a cutting schedule"))
    }

    @Test
    fun `ECP footing punching row is a stress in MPa equal to the hand calculation`() {
        // qp = 0.9*P*1000/(b0*d) [MPa], b0 = 2(c1+c2)+4d [mm], d = t - cover - 0.75*dia (mean of the two layers, the diameter actually selected). The row used to be labelled kN.
        val r = engine.calculateFooting(
            type = CalculatorEngine.FootingType.ISOLATED, p = 1200.0, ps = 800.0, fcu = 25.0, fy = 360.0,
            soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
        )
        val row = r.safetyChecks.first { it.name == "Punching Shear" }
        assertEquals("MPa", row.unit)
        val d = r.thickness - r.coverMm - 0.75 * r.barDiameter
        val b0 = 2.0 * (300.0 + 500.0) + 4.0 * d
        assertEquals(0.9 * 1200.0 * 1000.0 / (b0 * d), row.value, 1e-6)
        assertEquals(0.316 * Math.sqrt(25.0 / 1.5), row.limit, 1e-6)
    }

    @Test
    fun `ECP isolated footing plan size equals the independently computed B and L`() {
        // Hand calculation: P_service = P / factor; A = P_service / (q_allow - 25 kN/m3 * t); ratio = sqrt(colT/colB * 1.2);
        // B = sqrt(A/ratio), L = ratio*B, each rounded UP to 50 mm. t = 0.6 m (the engine's initial depth).
        // Earlier B = A/ratio gave B*L = A^2/ratio: 22.9 m2 for an 8 m2 requirement.
        val factor = com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE.getFactorForCode(DesignCode.ECP)
        for ((p, soil, colB, colT) in listOf(
            listOf(1200.0, 150.0, 300.0, 600.0), listOf(1200.0, 250.0, 300.0, 600.0),
            listOf(800.0, 100.0, 400.0, 400.0), listOf(2000.0, 200.0, 300.0, 500.0)
        )) {
            val r = engine.calculateFooting(type = CalculatorEngine.FootingType.ISOLATED, p = p, ps = p / factor, fcu = 25.0, fy = 360.0,
                soil = soil, colB = colB, colT = colT, code = DesignCode.ECP)
            val area = (p / factor) / (soil - 25.0 * 0.6)
            val ratio = Math.sqrt(colT / colB * 1.2)
            val b = Math.sqrt(area / ratio)
            val expectedB = Math.ceil(b * 1000.0 / 50.0) * 50.0
            val expectedL = Math.ceil(ratio * b * 1000.0 / 50.0) * 50.0
            assertEquals("P=$p soil=$soil width", expectedB, r.width, 1e-9)
            assertEquals("P=$p soil=$soil length", expectedL, r.length, 1e-9)
            assertTrue("area ${r.width * r.length / 1e6} must cover required $area", r.width * r.length / 1e6 >= area - 1e-9)
            assertTrue("area ${r.width * r.length / 1e6} must stay within the 50 mm rounding of $area", r.width * r.length / 1e6 <= area * 1.25)
        }
    }

    @Test
    fun `ECP footing effective depth follows the bar diameter actually selected`() {
        // d (mean of the two layers) = t - cover - 0.75*dia. The engine must not keep a fixed 10 mm allowance when it steps the diameter up.
        val res = com.civileg.app.domain.calculations.ecp.ECPFooting().designIsolatedFooting(
            fcu = 25.0, fy = 360.0, columnWidth = 300.0, columnDepth = 600.0, axialLoad = 1200.0, momentX = 0.0, momentY = 0.0,
            soilBearingCapacity = 150.0, footingDepth = 600.0,
            loadCombination = com.civileg.app.domain.entities.LoadCombination.DEAD_LIVE,
            constraints = com.civileg.app.domain.calculations.base.BoundaryConstraints()
        )
        val d = Regex("d=([0-9]+) mm").find(res.codeNotes.first { it.contains("d=") && it.startsWith("B=") })!!.groupValues[1].toDouble()
        val dia = res.reinforcement.barDiameter
        assertTrue("d=$d must not exceed t - cover - 0.75*dia for the displayed dia $dia", d <= 600.0 - 50.0 - 0.75 * dia + 1.0)
        assertTrue("flexure row provides at least the requirement", res.reinforcement.astProvided >= res.reinforcement.astRequired - 1e-6)
    }

    @Test
    fun `footing property line limits are rejected not silently ignored`() {
        val e = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            engine.calculateFooting(
                type = CalculatorEngine.FootingType.ISOLATED, p = 800.0, ps = 540.0, fcu = 25.0, fy = 360.0,
                soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP, maxLeft = 1000.0
            )
        }
        assertTrue(e.message!!.contains("not supported"))
    }

    @Test
    fun `footing result exposes calculation steps`() {
        val r = engine.calculateFooting(
            type = CalculatorEngine.FootingType.ISOLATED, p = 800.0, ps = 540.0, fcu = 25.0, fy = 360.0,
            soil = 150.0, colB = 300.0, colT = 500.0, code = DesignCode.ECP
        )
        assertEquals("five traceable design steps expected", 5, r.calculationSteps.size)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertTrue("punching check step must exist", r.calculationSteps.any { it.title == "Punching Shear" })
    }

    @Test
    fun `strap footing facade forwards calculation steps`() {
        val r = engine.calculateStrapFooting(
            col1Load = 500.0, col2Load = 300.0, distance = 4000.0,
            col1W = 300.0, col1D = 500.0, col2W = 300.0, col2D = 500.0,
            soil = 150.0, fcu = 25.0, fy = 360.0, code = DesignCode.ECP,
            preferredDiameter = 16, strapWidth = 400.0
        )
        assertTrue("strap steps must be forwarded to the facade result", r.calculationSteps.isNotEmpty())
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
    }

    @Test
    fun `stair result exposes calculation steps`() {
        val r = engine.designStaircase(
            type = CalculatorEngine.StairType.SINGLE_FLIGHT, span = 3.6, riser = 175.0, tread = 280.0,
            deadLoad = 4.0, liveLoad = 3.0, fcu = 30.0, fy = 420.0,
            preferredDiameter = 12, code = DesignCode.ECP
        )
        assertEquals("six traceable steps expected", 6, r.calculationSteps.size)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
    }

    @Test
    fun `retaining wall result exposes calculation steps`() {
        val r = engine.designRetainingWall(
            height = 4.0, soilDensity = 18.0, frictionAngle = 30.0, surcharge = 10.0,
            fcu = 25.0, fy = 360.0, preferredDiameter = 16, code = DesignCode.ECP
        )
        assertEquals("six traceable steps expected", 6, r.calculationSteps.size)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertTrue("stability checks must be present", r.calculationSteps.any { it.title == "Overturning Stability" })
    }

    @Test
    fun `tank result exposes calculation steps`() {
        val r = engine.designTank(
            type = CalculatorEngine.TankType.RECTANGULAR_GROUND,
            capacity = 100.0, heightM = 3.0, fcu = 30.0, fy = 420.0,
            preferredDiameter = 12, code = DesignCode.ECP
        )
        assertEquals("six design steps plus the base punching NOT ASSESSED note", 7, r.calculationSteps.size)
        assertEquals("NOT ASSESSED", r.calculationSteps.last().result)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        r.calculationSteps.filter { it.title == "Wall Reinforcement" || it.title == "Base Reinforcement" }.forEach {
            assertTrue("step text must show provided steel: ${it.formulaWithValues}", it.formulaWithValues.contains("provided"))
        }
        assertTrue("wall steel step must exist", r.calculationSteps.any { it.title == "Wall Reinforcement" })
    }

    @Test
    fun `shear wall result exposes calculation steps`() {
        val r = engine.designShearWall(ShearWallInput(), DesignCode.ECP)
        assertEquals("six traceable steps expected", 6, r.calculationSteps.size)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertTrue("shear demand step must exist", r.calculationSteps.any { it.title == "Shear Demand" })
    }

    @Test
    fun `pile result exposes calculation steps`() {
        val r = engine.designPileFoundation(PileInput(), DesignCode.ECP)
        assertEquals("six traceable steps expected", 6, r.calculationSteps.size)
        assertTrue("steps must carry code references", r.calculationSteps.all { it.codeReference.isNotBlank() })
        assertTrue("capacity step must exist", r.calculationSteps.any { it.title == "Single Pile Capacity" })
    }
}
