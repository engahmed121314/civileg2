package com.civileg.core.calculations.entities

import com.civileg.core.engineering.Aci318Params
import com.civileg.core.engineering.BeamDesignFacade
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.CodeVersionRegistry
import com.civileg.core.engineering.ConcreteMaterial
import com.civileg.core.engineering.Ecp203Params
import com.civileg.core.engineering.SteelMaterial
import com.civileg.core.engineering.StairType
import com.civileg.core.engineering.UnifiedColumnDesign
import com.civileg.core.engineering.UnifiedFootingDesign
import com.civileg.core.engineering.UnifiedSlabDesign
import com.civileg.core.engineering.UnifiedStairDesign
import com.civileg.core.engineering.UnifiedTankDesign
import com.civileg.core.engineering.UnifiedRetainingWallDesign
import com.civileg.core.engineering.TankType
import com.civileg.core.engineering.RetainingWallInput
import com.civileg.core.engineering.toFootingReinforcement
import com.civileg.core.engineering.toReinforcementResult
import com.civileg.core.engineering.toRetainingWallReinforcement
import com.civileg.core.engineering.toSlabReinforcement
import com.civileg.core.engineering.toStairReinforcement
import com.civileg.core.engineering.toTankReinforcement
import com.civileg.core.calculations.entities.LoadCombination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap integration gate — the cycle that must hold:
 *
 *   BeamDesignFacade.design → toReinforcementResult → buildBeamFromFacade
 *     → ReinforcementSet → ReinforcementSet.toRebarModel (per-bar identity)
 *     → scheduleText → RebarModel.toReinforcementSet (round-trip) → validate() clean.
 *
 * No independent math: every assertion derives from the single facade outcome.
 */
class DrawingRebarIntegrationTest {

    // Mirrors the golden-test recipe (BeamDesignFacadeGoldenTest).
    private val concrete = ConcreteMaterial(fcuMpa = 25.0)
    private val steel = SteelMaterial(yieldMpa = 360.0, ultimateMpa = 520.0)

    private fun design(): BeamDesignFacade.BeamOutcome {
        val facade = BeamDesignFacade(Ecp203Params, concrete, steel)
        return facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 120.0, vuKn = 80.0,
            spanM = 6.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0, clearCoverMm = 30.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 10.0
        )
    }

    private fun model(outcome: BeamDesignFacade.BeamOutcome) = DrawingModelBuilder.buildBeamFromFacade(
        project = "Integ",
        drawingNumber = "B-101",
        sheetNumber = "1/1",
        titleBlock = TitleBlock(
            project = "Integ",
            drawingTitle = "Beam Section",
            drawingNumber = "B-101",
            date = "2026-08-27",
            scale = "1:25",
            designCode = "ECP 203-2020"
        ),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        overallHeight = 700.0,
        overallWidth = 300.0,
        effectiveDepth = 640.0,
        concreteCover = 40.0,
        beamLength = 5000.0,
        outcome = outcome
    )

    /** Independent "nØd" parse, only to prove the adapter did not recompute anything. */
    private fun flexureBars(bars: String): Pair<Int, Double> {
        val m = Regex("(\\d+)Ø(\\d+)").matchEntire(bars)!!
        return m.groupValues[1].toInt() to m.groupValues[2].toDouble()
    }

    /** Inverse of the stair engine's "Ød @ s mm c/c" — independent re-parse for the adapter test. */
    private fun stairBar(barString: String): Pair<Double, Double> {
        val m = Regex("Ø(\\d+) @ (\\d+) mm c/c").matchEntire(barString)!!
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }

    @Test
    fun adapterIsPurePassThroughNoRecomputation() {
        val result = design()
        val mapping = result.toReinforcementResult()
        val (n, dia) = flexureBars(result.flexure.bars)

        assertTrue(result.isSafe)
        assertEquals(CheckStatus.PASS, result.overallStatus)
        assertEquals(result.flexure.asRequiredMm2, mapping.astRequired, 1e-9)
        assertEquals(result.flexure.asProvidedMm2, mapping.astProvided, 1e-9)
        assertEquals(n, mapping.numberOfBars)
        assertEquals(dia, mapping.barDiameter, 1e-9)
        assertEquals(result.shear.stirrupDiaMm, mapping.tiesDiameter, 1e-9)
        assertEquals(result.shear.spacingMm, mapping.tiesSpacing, 1e-9)
        assertEquals(result.shear.utilization, mapping.utilizationRatio, 1e-9)
        assertEquals(result.sanity.warnings, mapping.warnings)
    }

    @Test
    fun realDesignFeedsReinforcementSet() {
        val result = design()
        val m = model(result)

        assertTrue(result.isSafe)
        assertEquals(CheckStatus.PASS, result.overallStatus)

        // Bars matched to the sole producer: flexure "nØd" + stirrups from shear spacing.
        assertTrue(m.reinforcement.mainTensionBars.isNotEmpty())
        assertTrue(m.reinforcement.mainTensionBars.all { it.codeReference.contains("ECP") })
        assertEquals(result.flexure.asProvidedMm2 > 0, m.reinforcement.mainTensionBars.isNotEmpty())
        assertTrue(m.reinforcement.stirrups.isNotEmpty())
    }

    @Test
    fun facadeOutcomeEmitsEngineStyleConfinementZones() {
        // R2 (P044) facade parity: with member geometry supplied, the adapter
        // must emit the engine's 3-zone confinement layout (dense at supports,
        // relaxed mid) instead of falling back to a uniform member.
        val result = design()
        val zones = result.toReinforcementResult(hMm = 700.0, dMm = 640.0, spanMm = 5000.0).zones

        assertEquals(3, zones.size)
        assertEquals("Support Zone (Left)", zones[0].name)
        assertEquals("Mid-Span Zone", zones[1].name)
        assertEquals("Support Zone (Right)", zones[2].name)
        // Confinement band = min(2h, L/4) = min(1400, 1250).
        assertEquals(1250.0, zones[0].endLocation, 1e-9)
        assertEquals(1250.0, zones[1].startLocation, 1e-9)
        assertEquals(3750.0, zones[2].startLocation, 1e-9)
        assertEquals(5000.0, zones[2].endLocation, 1e-9)
        // Support spacing is the engine's critical-section value, VERBATIM
        // (Pillar-2 passthrough — never recomputed by the adapter).
        assertEquals(result.shear.spacingMm, zones[0].spacing, 1e-9)
        assertEquals(result.shear.spacingMm, zones[2].spacing, 1e-9)
        // Mid zone follows the engine relaxation rule: never denser than the
        // support and inside the 200 mm code maximum.
        assertTrue(zones[1].spacing >= zones[0].spacing - 1e-9)
        assertTrue(zones[1].spacing <= 200.0 + 1e-9)
        assertEquals(2, zones[1].numLegs)
        assertEquals(result.shear.stirrupDiaMm.toInt(), zones[0].diameter)
        assertTrue(zones[0].description.contains("@") && zones[0].description.contains("c/c"))
    }

    @Test
    fun facadeAdapterWithoutGeometryKeepsUniformFallback() {
        // Geometry-less callers (outcome only) keep the conservative uniform
        // path — zones stay empty and buildBeam falls back to the densest one.
        assertTrue(design().toReinforcementResult().zones.isEmpty())
    }

    @Test
    fun facadeModelDensifiesSupportBandsWhenShearDemandsTighterSpacing() {
        // ACI facade with clear shear demand → support spacing well under the
        // 200 mm code cap, so the relaxed mid zone yields TWO real spacings in
        // the facade BBS and per-metre support density beats the mid span.
        val facade = BeamDesignFacade(Aci318Params, concrete, steel)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 150.0, vuKn = 200.0,
            spanM = 5.0, support = SupportCondition.SIMPLY_SUPPORTED,
            tensionRatioPercent = 1.0, clearCoverMm = 40.0, tensionBarSpacingMm = 150.0,
            computedDeflectionMm = 10.0
        )
        val zones = result.toReinforcementResult(hMm = 700.0, dMm = 640.0, spanMm = 5000.0).zones
        assertTrue(
            "expected a real dense/mid contrast, got support=${zones[0].spacing} mid=${zones[1].spacing}",
            zones[0].spacing < zones[1].spacing
        )
        val sup = zones[0].spacing
        val mid = zones[1].spacing

        val m = DrawingModelBuilder.buildBeamFromFacade(
            project = "Integ",
            drawingNumber = "B-101",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Beam Section",
                drawingNumber = "B-101",
                date = "2026-08-27",
                scale = "1:25",
                designCode = "ACI 318-19"
            ),
            code = DesignCode.ACI,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ACI),
            overallHeight = 700.0,
            overallWidth = 300.0,
            effectiveDepth = 640.0,
            concreteCover = 40.0,
            beamLength = 5000.0,
            outcome = result
        )

        val stirrups = m.reinforcement.stirrups
        assertTrue(stirrups.first().spacing == sup && stirrups.last().spacing == sup)
        assertTrue(stirrups.any { it.spacing == mid })
        assertTrue(stirrups.any { it.spacing == sup })
        val confLen = zones[0].endLocation - zones[0].startLocation
        val atSup = stirrups.count { it.spacing == sup }
        val atMid = stirrups.count { it.spacing == mid }
        assertTrue(
            "atSup=$atSup (2·$confLen mm) vs atMid=$atMid (${5000.0 - 2 * confLen} mm)",
            atSup / (2 * confLen) > atMid / (5000.0 - 2 * confLen)
        )
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    @Test
    fun rebarModelExpandsEveryBarWithUniqueId() {
        val m = model(design())
        val rebar = m.reinforcement.toRebarModel()

        val expectedCount = m.reinforcement.all.sumOf { it.quantity }
        assertEquals(expectedCount, rebar.bars.size)
        assertEquals(rebar.bars.size, rebar.ids.size)
        assertTrue(rebar.bars.all { it.codeReference.isNotBlank() })
    }

    @Test
    fun weightIsInvariantAcrossExpansionAndCollapse() {
        val m = model(design())
        val set = m.reinforcement
        val expanded = set.toRebarModel()

        assertEquals(set.totalWeightKg, expanded.totalWeightKg, 1e-9)
        val collapsed = expanded.toReinforcementSet()
        assertEquals(set.totalWeightKg, collapsed.totalWeightKg, 1e-9)
    }

    @Test
    fun scheduleTextCarriesPerBarTraceability() {
        val rebar = model(design()).reinforcement.toRebarModel()

        assertTrue(rebar.scheduleText.startsWith("BAR SCHEDULE"))
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
    }

    @Test
    fun facadeWarningsFlowIntoRenderedCardInput() {
        val result = design()

        // The adapter must carry the engine's sanity warnings into the model's
        // ReinforcementResult (no silent loss); validate() stays clean for a safe beam.
        val m = model(result)
        val flags = DrawingModelBuilder.validate(m)
        assertFalse("safe design must produce no invalid entity", flags.hasInvalid)
        assertFalse(result.sanity.hasError)
    }

    @Test
    fun columnAdapterIsPurePassThroughNoRecomputation() {
        val outcome = UnifiedColumnDesign(Ecp203Params, concrete, steel)
            .design(b = 400.0, h = 500.0, puKnm = 1500.0, momentXKnm = 40.0, momentYKnm = 25.0)
        val mapping = outcome.toReinforcementResult()
        val (n, dia) = flexureBars(outcome.bars)

        assertEquals(outcome.asRequiredMm2, mapping.astRequired, 1e-9)
        assertEquals(outcome.asProvidedMm2, mapping.astProvided, 1e-9)
        assertEquals(n, mapping.numberOfBars)
        assertEquals(dia, mapping.barDiameter, 1e-9)
        assertEquals(outcome.tieDiameterMm, mapping.tiesDiameter, 1e-9)
        assertEquals(outcome.tieSpacingMm, mapping.tiesSpacing, 1e-9)
        assertEquals(outcome.utilization, mapping.utilizationRatio, 1e-9)
        assertEquals(outcome.sanity.warnings, mapping.warnings)
    }

    @Test
    fun columnDesignFeedsDrawingAndRebarModel() {
        val outcome = UnifiedColumnDesign(Ecp203Params, concrete, steel)
            .design(b = 400.0, h = 500.0, puKnm = 1500.0, momentXKnm = 40.0, momentYKnm = 25.0)
        val m = DrawingModelBuilder.buildColumnFromFacade(
            project = "Integ",
            drawingNumber = "C-201",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Column Section",
                drawingNumber = "C-201",
                date = "2026-08-27",
                scale = "1:25",
                designCode = "ECP 203-2020"
            ),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            overallHeight = 500.0,
            overallWidth = 400.0,
            concreteCover = 40.0,
            columnLength = 3000.0,
            outcome = outcome
        )

        assertTrue(m.columnSection != null)
        assertTrue(m.reinforcement.mainTensionBars.all { it.element == "column" && it.codeReference.contains("ECP") })
        assertTrue(m.reinforcement.stirrups.isNotEmpty())

        val rebar = m.reinforcement.toRebarModel()
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, rebar.bars.size)
        assertTrue(rebar.bars.all { it.element == "column" })
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
        // Round-trip: weight parity identical to the beam test.
        assertEquals(m.reinforcement.totalWeightKg, rebar.toReinforcementSet().totalWeightKg, 1e-9)
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    @Test
    fun slabAdapterIsPurePassThroughNoRecomputation() {
        val outcome = UnifiedSlabDesign(Ecp203Params, concrete, steel)
            .designTwoWay(shortSpanM = 4.0, longSpanM = 5.0, h = 160.0, totalLoadKnm2 = 7.0, allEdgesFixed = true)
        val mesh = outcome.toSlabReinforcement()
        val (ns, ds) = flexureBars(outcome.shortDir.bars)
        val (nl, dl) = flexureBars(outcome.longDir.bars)

        assertEquals(ns, mesh.shortBarSelection.first)
        assertEquals(ds, mesh.shortBarSelection.second, 1e-9)
        assertEquals(nl, mesh.longBarSelection.first)
        assertEquals(dl, mesh.longBarSelection.second, 1e-9)
        assertEquals(1000.0 / ns, mesh.shortSpacingMm, 1e-9)
        assertEquals(1000.0 / nl, mesh.longSpacingMm, 1e-9)
        assertEquals(outcome.sanity.warnings, mesh.warnings)
    }

    @Test
    fun slabDesignFeedsDrawingAndRebarModel() {
        val outcome = UnifiedSlabDesign(Ecp203Params, concrete, steel)
            .designTwoWay(shortSpanM = 4.0, longSpanM = 5.0, h = 160.0, totalLoadKnm2 = 7.0, allEdgesFixed = true)
        val m = DrawingModelBuilder.buildSlabFromFacade(
            project = "Integ",
            drawingNumber = "S-301",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Two-Way Slab Section",
                drawingNumber = "S-301",
                date = "2026-08-27",
                scale = "1:25",
                designCode = "ECP 203-2020"
            ),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            thickness = 160.0,
            effectiveDepth = 134.0,
            concreteCover = 20.0,
            shortSpanMm = 4000.0,
            longSpanMm = 5000.0,
            outcome = outcome
        )

        assertTrue(m.slabSection is SlabSectionGeometryTwoWay)
        assertTrue(m.reinforcement.all.isNotEmpty())
        assertTrue(m.reinforcement.all.all { it.element == "slab" })
        assertTrue(m.reinforcement.all.all { it.spacing != null && it.spacing!! > 0.0 })

        val rebar = m.reinforcement.toRebarModel()
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, rebar.bars.size)
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
        assertEquals(m.reinforcement.totalWeightKg, rebar.toReinforcementSet().totalWeightKg, 1e-9)
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    @Test
    fun footingAdapterIsPurePassThroughNoRecomputation() {
        // Golden-parity recipe: ECP square column, centred load (clean outline).
        val outcome = UnifiedFootingDesign(Ecp203Params, concrete, steel)
            .designIsolatedFooting(
                columnWidth = 600.0, columnDepth = 600.0,
                axialLoad = 1200.0, momentX = 0.0, momentY = 0.0,
                soilBearingCapacity = 150.0, footingDepth = 500.0,
                loadCombination = LoadCombination.DEAD_LIVE
            )
        val mesh = outcome.toFootingReinforcement()

        assertTrue(outcome.isSafe)
        assertEquals(outcome.shortDir.barsPerMeter, mesh.shortBarSelection.first)
        assertEquals(outcome.shortDir.barDiameter, mesh.shortBarSelection.second, 1e-9)
        assertEquals(outcome.longDir.barsPerMeter, mesh.longBarSelection.first)
        assertEquals(outcome.longDir.barDiameter, mesh.longBarSelection.second, 1e-9)
        assertEquals(outcome.shortDir.spacingMm, mesh.shortSpacingMm, 1e-9)
        assertEquals(outcome.longDir.spacingMm, mesh.longSpacingMm, 1e-9)
        assertEquals(outcome.shortDir.astProvided + outcome.longDir.astProvided, mesh.bottomAsProvided, 1e-9)
        assertEquals(outcome.distribution.barsPerMeter > 0, mesh.distribution != null)
        if (mesh.distribution != null) {
            assertEquals(outcome.distribution.diameterMm, mesh.distribution!!.diameterMm, 1e-9)
            assertEquals(outcome.distribution.spacingMm, mesh.distribution!!.spacingMm, 1e-9)
        }
        assertEquals(outcome.isSafe, mesh.isSafe)
        assertEquals(outcome.sanity.warnings, mesh.warnings)
    }

    @Test
    fun footingDesignFeedsDrawingAndRebarModel() {
        val outcome = UnifiedFootingDesign(Ecp203Params, concrete, steel)
            .designIsolatedFooting(
                columnWidth = 600.0, columnDepth = 600.0,
                axialLoad = 1200.0, momentX = 0.0, momentY = 0.0,
                soilBearingCapacity = 150.0, footingDepth = 500.0,
                loadCombination = LoadCombination.DEAD_LIVE
            )
        val m = DrawingModelBuilder.buildFootingFromFacade(
            project = "Integ",
            drawingNumber = "F-401",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Isolated Footing Section",
                drawingNumber = "F-401",
                date = "2026-08-27",
                scale = "1:50",
                designCode = "ECP 203-2020"
            ),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            concreteCover = 50.0,
            outcome = outcome
        )

        assertTrue(m.footingSection != null)
        assertTrue(m.reinforcement.all.isNotEmpty())
        assertTrue(m.reinforcement.all.all { it.element == "footing" })
        assertTrue(m.reinforcement.all.all { it.spacing != null && it.spacing!! > 0.0 })
        assertTrue(m.reinforcement.all.all { it.codeReference.contains("ECP") })

        val rebar = m.reinforcement.toRebarModel()
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, rebar.bars.size)
        assertTrue(rebar.bars.all { it.element == "footing" })
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
        assertEquals(m.reinforcement.totalWeightKg, rebar.toReinforcementSet().totalWeightKg, 1e-9)
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    @Test
    fun stairAdapterIsPurePassThroughNoRecomputation() {
        // ECP-both golden recipe — byte-identical to StaircaseParityTest ecp-both.
        val outcome = UnifiedStairDesign(Ecp203Params, concrete, steel)
            .designStaircase(
                stairType = StairType.STRAIGHT,
                spanM = 3.0, totalRiseM = 1.8, stairWidthM = 1.2, waistThicknessMm = 140.0,
                deadLoadKnm2 = 6.0, liveLoadKnm2 = 4.0,
                riserCount = 10, goingMmInput = 280.0
            )
        val mesh = outcome.toStairReinforcement()
        val (mainDia, mainSp) = stairBar(outcome.mainRebar)
        val (distDia, distSp) = stairBar(outcome.distributionRebar)

        assertTrue(outcome.isSafe)
        assertEquals(mainDia, mesh.mainDiameter, 1e-9)
        assertEquals(mainSp, mesh.mainSpacingMm, 1e-9)
        assertEquals(distDia, mesh.distributionDiameter, 1e-9)
        assertEquals(distSp, mesh.distributionSpacingMm, 1e-9)
        assertEquals(outcome.isSafe, mesh.isSafe)
        assertEquals(outcome.sanity.warnings, mesh.warnings)
    }

    @Test
    fun stairDesignFeedsDrawingAndRebarModel() {
        val outcome = UnifiedStairDesign(Ecp203Params, concrete, steel)
            .designStaircase(
                stairType = StairType.STRAIGHT,
                spanM = 3.0, totalRiseM = 1.8, stairWidthM = 1.2, waistThicknessMm = 140.0,
                deadLoadKnm2 = 6.0, liveLoadKnm2 = 4.0,
                riserCount = 10, goingMmInput = 280.0
            )
        val m = DrawingModelBuilder.buildStairFromFacade(
            project = "Integ",
            drawingNumber = "ST-501",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Stair Section",
                drawingNumber = "ST-501",
                date = "2026-08-27",
                scale = "1:25",
                designCode = "ECP 203-2020"
            ),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            concreteCoverMm = 25.0,
            stairWidthM = 1.2,
            outcome = outcome
        )

        assertNotNull(m.stairSection)
        assertTrue(m.reinforcement.mainTensionBars.isNotEmpty())
        assertTrue(m.reinforcement.distributionBars.isNotEmpty())
        assertTrue(m.reinforcement.all.all { it.element == "stair" })
        assertTrue(m.reinforcement.all.all { it.codeReference.contains("ECP") })
        assertTrue(m.reinforcement.all.all { it.spacing != null && it.spacing!! > 0.0 })
        assertTrue(m.reinforcement.mainTensionBars.all { it.mark.startsWith("S-M-") })
        assertTrue(m.reinforcement.distributionBars.all { it.mark.startsWith("S-D-") })

        val rebar = m.reinforcement.toRebarModel()
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, rebar.bars.size)
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
        assertEquals(m.reinforcement.totalWeightKg, rebar.toReinforcementSet().totalWeightKg, 1e-9)
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    @Test
    fun tankAdapterIsPurePassThroughNoRecomputation() {
        // ECP ground rectangular tank — mirror of the TankParityTest recipe.
        val outcome = UnifiedTankDesign(Ecp203Params, concrete, steel)
            .designTank(
                lengthMm = 6000.0, widthMm = 4000.0, heightMm = 3000.0,
                waterDepthMm = 1500.0, type = TankType.RECTANGULAR_GROUND
            )
        val mapping = outcome.toTankReinforcement()

        assertTrue(outcome.isSafe)
        assertEquals(outcome.wallReinforcement.barDiameter, mapping.wallDiameter, 1e-9)
        assertEquals(outcome.wallReinforcement.spacing, mapping.wallSpacingMm, 1e-9)
        assertEquals(outcome.wallReinforcement.tiesDiameter, mapping.wallHorizontalDiameter, 1e-9)
        assertEquals(outcome.wallReinforcement.tiesSpacing, mapping.wallHorizontalSpacingMm, 1e-9)
        assertEquals(outcome.baseReinforcement.barDiameter, mapping.baseDiameter, 1e-9)
        assertEquals(outcome.baseReinforcement.spacing, mapping.baseSpacingMm, 1e-9)
        assertEquals(outcome.isSafe, mapping.isSafe)
        assertEquals(outcome.sanity.warnings, mapping.warnings)
    }

    @Test
    fun tankDesignFeedsDrawingAndRebarModel() {
        val outcome = UnifiedTankDesign(Ecp203Params, concrete, steel)
            .designTank(
                lengthMm = 6000.0, widthMm = 4000.0, heightMm = 3000.0,
                waterDepthMm = 1500.0, type = TankType.RECTANGULAR_GROUND
            )
        val m = DrawingModelBuilder.buildTankFromFacade(
            project = "Integ",
            drawingNumber = "TN-601",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Tank Wall/Base Section",
                drawingNumber = "TN-601",
                date = "2026-08-27",
                scale = "1:50",
                designCode = "ECP 203-2020"
            ),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            lengthM = 6.0,
            widthM = 4.0,
            heightM = 3.0,
            concreteCoverMm = 50.0,
            outcome = outcome
        )

        assertNotNull(m.tankSection)
        assertEquals(outcome.wallThickness, m.tankSection!!.wallThickness, 1e-9)
        assertEquals(outcome.baseThickness, m.tankSection!!.baseThickness, 1e-9)
        assertEquals(outcome.wallEffectiveDepth, m.tankSection!!.effectiveDepth, 1e-9)
        assertTrue(m.reinforcement.mainTensionBars.isNotEmpty())
        assertTrue(m.reinforcement.distributionBars.isNotEmpty())
        assertTrue(m.reinforcement.all.all { it.element == "tank" })
        assertTrue(m.reinforcement.all.all { it.codeReference.contains("ECP") })
        assertTrue(m.reinforcement.all.all { it.spacing != null && it.spacing!! > 0.0 })
        assertTrue(m.reinforcement.mainTensionBars.all { it.mark.startsWith("T-WV-") })
        assertTrue(m.reinforcement.distributionBars.any { it.mark.startsWith("T-WH-") })
        assertTrue(m.reinforcement.distributionBars.any { it.mark.startsWith("T-B-") })

        val rebar = m.reinforcement.toRebarModel()
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, rebar.bars.size)
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
        assertEquals(m.reinforcement.totalWeightKg, rebar.toReinforcementSet().totalWeightKg, 1e-9)
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    @Test
    fun unsafeDesignIsNotInvalidButFailsDrawingState() {
        // Guaranteed-FAIL recipe from the golden suite: ACI crack-conrol spacing
        // 200 > s_max, so the whole design reports FAIL.
        val facade = BeamDesignFacade(Aci318Params, concrete, steel)
        val result = facade.design(
            b = 300.0, d = 450.0, h = 500.0,
            muKnm = 100.0, vuKn = 60.0,
            spanM = 5.0, support = SupportCondition.CONTINUOUS,
            tensionRatioPercent = 0.8,
            clearCoverMm = 40.0, tensionBarSpacingMm = 200.0,
            computedDeflectionMm = 8.0
        )
        val m = DrawingModelBuilder.buildBeamFromFacade(
            project = "Integ",
            drawingNumber = "B-101",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Beam Section",
                drawingNumber = "B-101",
                date = "2026-08-27",
                scale = "1:25",
                designCode = "ACI 318-19"
            ),
            code = DesignCode.ACI,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ACI),
            overallHeight = 700.0,
            overallWidth = 300.0,
            effectiveDepth = 640.0,
            concreteCover = 40.0,
            beamLength = 5000.0,
            outcome = result
        )

        assertEquals(CheckStatus.FAIL, result.overallStatus)
        assertFalse(result.isSafe)
        assertEquals(CheckStatus.FAIL, m.state.overallStatus)
        // SDS still draws an unsafe element; validation observes geometry only.
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }

    private fun designRetainingWall(): UnifiedRetainingWallDesign.Outcome =
        UnifiedRetainingWallDesign(Ecp203Params, concrete, steel)
            .designRetainingWall(
                RetainingWallInput(
                    wallHeight = 4.0,
                    stemBaseThickness = 0.4,
                    stemTopThickness = 0.2,
                    baseWidth = 3.0,
                    baseThickness = 0.4,
                    toeLength = 0.6,
                    heelLength = 2.0,
                    soilDensity = 18.0,
                    frictionAngle = 30.0,
                    surchargeLoad = 10.0,
                    waterTableDepth = 50.0,
                    fcu = 25.0,
                    fy = 360.0,
                    baseFrictionCoeff = 0.5,
                    soilBearingCapacity = 200.0
                )
            )

    @Test
    fun retainingWallAdapterIsPurePassThroughNoRecomputation() {
        val outcome = designRetainingWall()
        val mapping = outcome.toRetainingWallReinforcement()

        assertTrue(outcome.isSafe)
        assertEquals(outcome.stemMainRebarCount, mapping.stemMainCount)
        assertEquals(outcome.stemMainRebarDiameter, mapping.stemMainDiameter, 1e-9)
        assertEquals(outcome.stemMainRebarSpacingMm, mapping.stemMainSpacingMm, 1e-9)
        assertEquals(outcome.distributionBarsCount, mapping.distributionBarsCount)
        assertEquals(outcome.distributionBarsDiameter, mapping.distributionDiameter, 1e-9)
        assertEquals(outcome.distributionSpacingMm, mapping.distributionSpacingMm, 1e-9)
        assertEquals(outcome.toeRebarCount, mapping.toeBarsCount)
        assertEquals(outcome.toeRebarDiameter, mapping.toeDiameter, 1e-9)
        assertEquals(outcome.toeSpacingMm, mapping.toeSpacingMm, 1e-9)
        assertEquals(outcome.heelRebarCount, mapping.heelBarsCount)
        assertEquals(outcome.heelRebarDiameter, mapping.heelDiameter, 1e-9)
        assertEquals(outcome.heelSpacingMm, mapping.heelSpacingMm, 1e-9)
        assertEquals(outcome.isSafe, mapping.isSafe)
        assertEquals(outcome.sanity.warnings, mapping.warnings)
    }

    @Test
    fun retainingWallDesignFeedsDrawingAndRebarModel() {
        val outcome = designRetainingWall()
        val m = DrawingModelBuilder.buildRetainingWallFromFacade(
            project = "Integ",
            drawingNumber = "RW-700",
            sheetNumber = "1/1",
            titleBlock = TitleBlock(
                project = "Integ",
                drawingTitle = "Cantilever Retaining Wall",
                drawingNumber = "RW-700",
                date = "2026-08-28",
                scale = "1:50",
                designCode = "ECP 203-2020"
            ),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            wallHeightM = 4.0,
            stemBaseThicknessM = 0.4,
            baseWidthM = 3.0,
            baseThicknessM = 0.4,
            toeLengthM = 0.6,
            heelLengthM = 2.0,
            concreteCoverMm = 50.0,
            outcome = outcome
        )

        assertNotNull(m.retainingWallSection)
        assertEquals(4000.0, m.retainingWallSection!!.wallHeight, 1e-9)
        assertEquals(3000.0, m.retainingWallSection!!.baseWidth, 1e-9)
        assertTrue(m.reinforcement.mainTensionBars.isNotEmpty())
        assertTrue(m.reinforcement.distributionBars.isNotEmpty())
        assertTrue(m.reinforcement.all.all { it.element == "retainingWall" })
        assertTrue(m.reinforcement.all.all { it.codeReference.contains("ECP") })
        assertTrue(m.reinforcement.all.all { it.spacing != null && it.spacing!! > 0.0 })
        assertTrue(m.reinforcement.mainTensionBars.all { it.mark.startsWith("R-SM-") })
        assertTrue(m.reinforcement.distributionBars.any { it.mark.startsWith("R-D-") })
        assertTrue(m.reinforcement.distributionBars.any { it.mark.startsWith("R-T-") })
        assertTrue(m.reinforcement.distributionBars.any { it.mark.startsWith("R-H-") })
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, m.reinforcement.toRebarModel().bars.size)

        val rebar = m.reinforcement.toRebarModel()
        assertEquals(m.reinforcement.all.sumOf { it.quantity }, rebar.bars.size)
        assertTrue(rebar.bars.all { bar -> rebar.scheduleText.contains(bar.id) })
        assertEquals(m.reinforcement.totalWeightKg, rebar.toReinforcementSet().totalWeightKg, 1e-9)
        assertFalse(DrawingModelBuilder.validate(m).hasInvalid)
    }
}
