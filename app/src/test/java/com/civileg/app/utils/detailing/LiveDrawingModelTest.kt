package com.civileg.app.utils.detailing

import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.domain.BoundaryElementType
import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.domain.PileReinforcementResult
import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.ShearWallInput
import com.civileg.app.domain.ShearWallResult
import com.civileg.app.domain.WallType
import com.civileg.app.domain.calculations.base.PileCapacityResult
import com.civileg.app.domain.calculations.base.PileCapResult
import com.civileg.app.domain.calculations.base.PileGroupResult
import com.civileg.app.domain.calculations.base.PileSettlementResult
import com.civileg.app.domain.calculations.base.RebarDetail
import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.calculations.base.SeismicForceDistribution
import com.civileg.app.domain.calculations.base.SpectrumValue
import com.civileg.app.domain.entities.SteelGrade
import com.civileg.app.domain.entities.SteelMemberResult
import com.civileg.app.domain.entities.SteelMemberType
import com.civileg.app.domain.entities.SteelSectionType
import com.civileg.app.domain.entities.ConcreteMemberDesignResult
import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.FrameAnalysisResult
import com.civileg.app.domain.entities.FrameAnalysisSettings
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.SupportType
import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.DrawingModelBuilder
import com.civileg.core.calculations.entities.SlabSectionGeometryFlat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDrawingModelTest {

    private val bar = CalculatorEngine.ReinforcementBar(
        numBars = 4, diameter = 16, spacing = 150.0,
        type = "Main", description = "4Ø16"
    )

    private val stirrups = CalculatorEngine.StirrupReinforcement(
        diameter = 8, spacing = 200.0, numLegs = 2
    )

    private val code = CalculatorEngine.AppDesignCode.EGYPTIAN

    @Test
    fun beamMapsLiveResultToModelBoundsAndState() {
        val res = CalculatorEngine.BeamResult(
            width = 300.0, depth = 700.0, mu = 120.0, vu = 60.0,
            reinforcementBottom = bar, reinforcementTop = bar.copy(numBars = 2),
            stirrups = stirrups, isSafe = true, concreteVolume = 1.0,
            steelWeight = 100.0, cost = 5000.0, code = code,
            appliedMoment = 120.0, appliedShear = 60.0,
            utilizationRatio = 0.76
        )

        val model = LiveDrawingModel.beam(res, spanMm = 5000.0, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.beamSection != null)
        assertTrue(model.state.overallStatus.name == "PASS")
        assertTrue(model.dimensions.all.isNotEmpty())
        assertTrue(
            "bounds.width=${model.bounds.maxX - model.bounds.minX} should >0",
            model.bounds.maxX - model.bounds.minX > 0
        )
        assertTrue(
            "bounds.height=${model.bounds.maxY - model.bounds.minY} should >0",
            model.bounds.maxY - model.bounds.minY > 0
        )
    }

    @Test
    fun beamMapsSupportCaseToElevationGeometry() {
        val res = CalculatorEngine.BeamResult(
            width = 300.0, depth = 700.0, mu = 120.0, vu = 60.0,
            reinforcementBottom = bar, reinforcementTop = bar.copy(numBars = 2),
            stirrups = stirrups, isSafe = true, concreteVolume = 1.0,
            steelWeight = 100.0, cost = 5000.0, code = code,
            appliedMoment = 120.0, appliedShear = 60.0,
            supportType = CalculatorEngine.SupportType.FIXED_FIXED,
            utilizationRatio = 0.76
        )

        val model = LiveDrawingModel.beam(res, spanMm = 5000.0, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        val e = model.beamElevation!!
        // fixed–fixed carries a fixed symbol at both ends, no free end
        assertEquals(2, e.supports.size)
        assertTrue(e.supports.all { it.kind == "FIXED" })
        // engine values pass straight through
        assertEquals(5000.0, e.spanMm, 1e-9)
        assertEquals(120.0, e.appliedMomentKnM, 1e-9)
        assertEquals(60.0, e.appliedShearKn, 1e-9)
        assertEquals("FIXED_FIXED", e.supportTypeName)
        // five UDL arrows + in-pane curve ordinates
        assertEquals(5, e.loadArrows.size)
        e.momentCurve.forEach { p ->
            assertTrue(p.xMm >= e.momentPane.minX && p.xMm <= e.momentPane.maxX)
            assertTrue(p.yMm >= e.momentPane.minY && p.yMm <= e.momentPane.maxY)
        }
        e.shearCurve.forEach { p ->
            assertTrue(p.xMm >= e.shearPane.minX && p.xMm <= e.shearPane.maxX)
            assertTrue(p.yMm >= e.shearPane.minY && p.yMm <= e.shearPane.maxY)
        }
        assertTrue(e.momentCurve.size >= 2)
        assertTrue(e.shearCurve.size >= 2)
    }

    @Test
    fun cantileverBeamElevationHasFreeEndAndSingleSupport() {
        val res = CalculatorEngine.BeamResult(
            width = 300.0, depth = 700.0, mu = 60.0, vu = 30.0,
            reinforcementBottom = bar, reinforcementTop = bar.copy(numBars = 2),
            stirrups = stirrups, isSafe = true, concreteVolume = 1.0,
            steelWeight = 100.0, cost = 5000.0, code = code,
            appliedMoment = 60.0, appliedShear = 30.0,
            supportType = CalculatorEngine.SupportType.CANTILEVER,
            utilizationRatio = 0.76
        )

        val model = LiveDrawingModel.beam(res, spanMm = 2500.0, projectName = "Porch")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        val e = model.beamElevation!!
        assertEquals(1, e.supports.size)
        assertEquals("FIXED", e.supports.first().kind)
        assertEquals("CANTILEVER", e.supportTypeName)
        assertEquals(2500.0, e.spanMm, 1e-9)
        assertTrue(e.captionTop.contains("CANTILEVER"))
        assertTrue(e.captionBottom.contains("M max = 60"))
    }

    @Test
    fun beamCarriesEngineStirrupZonesThrough() {
        // R2 (P044): real engine-style zones — dense 100 mm support bands,
        // relaxed 150 mm mid span — must survive the LiveDrawingModel seam
        // verbatim (Pillar-2 passthrough).
        val zoned = stirrups.copy(
            spacing = 100.0, spacingAtSupport = 100.0, spacingAtMidspan = 150.0,
            zones = listOf(
                CalculatorEngine.StirrupZone("Support Zone (Left)", 0.0, 1000.0, 100.0, 2, 8, "Ø8 @ 100 mm c/c · 2-Leg"),
                CalculatorEngine.StirrupZone("Mid-Span Zone", 1000.0, 4000.0, 150.0, 2, 8, "Ø8 @ 150 mm c/c · 2-Leg"),
                CalculatorEngine.StirrupZone("Support Zone (Right)", 4000.0, 5000.0, 100.0, 2, 8, "Ø8 @ 100 mm c/c · 2-Leg")
            )
        )
        val res = CalculatorEngine.BeamResult(
            width = 300.0, depth = 700.0, mu = 120.0, vu = 60.0,
            reinforcementBottom = bar, reinforcementTop = bar.copy(numBars = 2),
            stirrups = zoned, isSafe = true, concreteVolume = 1.0,
            steelWeight = 100.0, cost = 5000.0, code = code,
            appliedMoment = 120.0, appliedShear = 60.0,
            utilizationRatio = 0.76
        )

        val model = LiveDrawingModel.beam(res, spanMm = 5000.0, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)

        // Each zone contributes floor(len/spacing)+1 bars (all inside the span).
        val nLeft = (1000.0 / 100.0).toInt() + 1
        val nMid = (3000.0 / 150.0).toInt() + 1
        val nRight = (1000.0 / 100.0).toInt() + 1
        val stirrups = model.reinforcement.stirrups
        assertEquals(nLeft + nMid + nRight, stirrups.size)
        assertEquals("B-S1", stirrups.first().mark)
        assertTrue(stirrups.all { it.spacing != null && it.spacing!! > 0.0 })
        val at100 = stirrups.count { it.spacing == 100.0 }
        val at150 = stirrups.count { it.spacing == 150.0 }
        // Dense support bands carry more bars per metre than the mid span.
        assertTrue(at100 / 2.0 > at150 / 3.0)

        // The elevation mirrors the engine zones verbatim.
        val e = model.beamElevation!!
        assertEquals(3, e.stirrupZones.size)
        val first = e.stirrupZones.first()
        assertEquals("Support Zone (Left)", first.name)
        assertEquals(0.0, first.startLocation, 1e-9)
        assertEquals(1000.0, first.endLocation, 1e-9)
        assertEquals(100.0, first.spacing, 1e-9)
        assertEquals(2, first.numLegs)
        assertEquals(8, first.diameter)
        assertEquals("Ø8 @ 100 mm c/c · 2-Leg", first.description)
        assertEquals(8.0, e.stirrupDiameter, 1e-9)
        assertTrue(e.captionBottom.contains("Ø8 @ 100/150 c/c"))
        assertTrue(e.captionBottom.contains("2-LEG"))
    }

    @Test
    fun columnMapsLiveResultToNoNanModel() {
        val res = CalculatorEngine.ColumnResult(
            width = 400.0, depth = 400.0, pu = 800.0, muX = 0.0, muY = 0.0,
            reinforcement = bar.copy(numBars = 8), stirrups = stirrups,
            isSafe = true, axialCapacity = 1000.0, concreteVolume = 1.0, steelWeight = 120.0,
            cost = 6000.0, code = code,
            reinforcementArea = 1600.0,
            reinforcementRatio = 0.01,
            utilizationRatio = 0.7
        )

        val model = LiveDrawingModel.column(res, heightMm = 3500.0, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.columnSection != null)
        assertTrue(model.dimensions.all.size == 2)
    }

    @Test
    fun footingMapsLiveResultWithEcpCoverConvention() {
        val res = CalculatorEngine.FootingResult(
            type = CalculatorEngine.FootingType.ISOLATED,
            width = 2000.0, length = 2500.0, thickness = 600.0,
            soilPressure = 150.0, allowablePressure = 200.0,
            reinforcementBottom = bar, isSafe = true, code = code,
            concreteVolume = 3.0, steelWeight = 200.0, cost = 9000.0,
            barsX = 8, barsY = 10, barDiameter = 16,
            reinforcement = bar, maxSoilPressure = 200.0,
            utilizationRatio = 0.75
        )

        val model = LiveDrawingModel.footing(res, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.footingSection != null)
        assertTrue(model.dimensions.all.size == 2)
    }

    @Test
    fun tankMapsLiveResultDimensioned() {
        val res = CalculatorEngine.TankResult(
            type = CalculatorEngine.TankType.UNDERGROUND,
            length = 4.0, width = 3.0, height = 2.5,
            wallThickness = 250.0, baseThickness = 300.0,
            wallReinforcement = bar.copy(numBars = 0, spacing = 150.0, diameter = 12),
            baseReinforcement = bar.copy(numBars = 0, spacing = 180.0, diameter = 14),
            isSafe = true, concreteVolume = 10.0, steelWeight = 500.0,
            cost = 20000.0, code = code,
            waterPressure = 25.0, capacityM3 = 30.0, utilizationRatio = 0.8
        )

        val model = LiveDrawingModel.tank(res, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.tankSection != null)
        assertTrue(model.dimensions.all.size == 2)
    }

    @Test
    fun stairMapsLiveResultDimensioned() {
        val res = CalculatorEngine.StairResult(
            type = CalculatorEngine.StairType.STRAIGHT,
            thickness = 150.0,
            reinforcement = bar.copy(numBars = 0, spacing = 150.0, diameter = 12),
            distributionReinforcement = bar.copy(numBars = 0, spacing = 250.0, diameter = 10),
            isSafe = true, concreteVolume = 2.0, steelWeight = 80.0,
            cost = 4000.0, code = code,
            utilizationRatio = 0.6, span = 3.6, riser = 0.17, tread = 0.30
        )

        val model = LiveDrawingModel.stair(res, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.stairSection != null)
        assertTrue(model.dimensions.all.size == 2)
    }

    @Test
    fun slabMapsLiveResultToTwoWayMeshModel() {
        val res = CalculatorEngine.SlabResult(
            type = CalculatorEngine.SlabType.SOLID,
            thickness = 180.0,
            reinforcementMain = bar.copy(numBars = 0, spacing = 150.0, diameter = 12),
            reinforcementSecondary = bar.copy(numBars = 0, spacing = 180.0, diameter = 12),
            isSafe = true, concreteVolume = 3.6, steelWeight = 120.0,
            cost = 7000.0, code = code,
            momentX = 20.0, momentY = 12.0, totalLoad = 12.0,
            utilizationRatio = 0.65
        )

        val model = LiveDrawingModel.slab(
            res, shortSpanMm = 4000.0, longSpanMm = 5000.0, projectName = "Bridge"
        )

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.slabSection != null)
        assertTrue(model.dimensions.all.size == 2)
        assertTrue(model.reinforcement.all.size > 10)
    }

    @Test
    fun retainingWallMapsLiveResultToEngineProportions() {
        val res = CalculatorEngine.RetainingWallResult(
            type = CalculatorEngine.RetainingWallType.CANTILEVER,
            height = 3.5, stemThickness = 300.0, baseWidth = 2500.0, baseThickness = 500.0,
            reinforcement = bar.copy(numBars = 7, spacing = 0.0, diameter = 16),
            isSafe = true, concreteVolume = 6.0,
            steelWeight = 400.0, cost = 18000.0, code = code,
            stemReinforcement = bar.copy(numBars = 7, spacing = 0.0, diameter = 16),
            baseReinforcement = bar.copy(numBars = 0, spacing = 160.0, diameter = 16),
            safetyChecks = emptyList(), utilizationRatio = 0.8
        )

        val model = LiveDrawingModel.retainingWall(res, projectName = "Bridge")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.retainingWallSection != null)
        assertTrue(model.dimensions.all.size == 2)
        assertTrue(model.reinforcement.all.size >= 5)
    }

    @Test
    fun pileFoundationMapsCapToFootingSection() {
        val res = PileDesignResult(
            pileType = "Bored",
            soilType = "Clay",
            pileDiameterMm = 600.0,
            pileLengthM = 12.0,
            numberOfPiles = 4,
            fcu = 30.0,
            fy = 420.0,
            capacityResult = PileCapacityResult(
                ultimateCapacity = 1500.0, allowableCapacity = 500.0,
                shaftResistance = 800.0, endBearingResistance = 700.0,
                fs = 3.0, utilizationRatio = 0.6
            ),
            groupResult = PileGroupResult(
                efficiencyFactor = 0.85, groupCapacity = 1700.0,
                individualCapacity = 500.0, spacing = 1800.0,
                numberOfPiles = 4, pattern = "2x2"
            ),
            settlementResult = PileSettlementResult(
                immediateSettlement = 5.0, consolidationSettlement = 8.0,
                totalSettlement = 13.0, allowableSettlement = 25.0, isOk = true
            ),
            capResult = PileCapResult(
                capWidth = 2800.0, capLength = 2800.0, capThickness = 600.0,
                punchingShearOk = true, punchingShearStress = 0.5, punchingShearCapacity = 1.0,
                beamShearOk = true, beamShearStress = 0.4, beamShearCapacity = 0.9,
                flexuralReinforcement = RebarDetail(
                    bars = 8, diameter = 20, spacing = 180,
                    area = 2513.0, requiredArea = 2200.0, ratio = 0.004
                ),
                punchingReinforcement = null,
                concreteVolume = 4.7, steelWeight = 330.0
            ),
            lateralCapacity = 250.0,
            lateralUtilizationRatio = 0.4,
            negativeSkinFriction = 60.0,
            pileReinforcement = PileReinforcementResult(
                longitudinalBars = 8, longitudinalDiameter = 16, longitudinalArea = 1608.5,
                requiredLongitudinalArea = 1300.0, tiesDiameter = 8, tiesSpacing = 200,
                isSafe = true, ratio = 0.0057
            ),
            isSafe = true,
            utilizationRatio = 0.55
        )

        val model = LiveDrawingModel.pileFoundation(res, projectName = "Bridge", designCode = "ECP")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.footingSection != null)
        assertTrue(model.dimensions.all.size == 2)
        assertTrue(model.reinforcement.all.size >= 6)
    }

    @Test
    fun flatSlabMapsStripResultToFlatSection() {
        val res = FlatSlabResult(
            isSafe = true, utilizationRatio = 0.72,
            totalDeadLoad = 6.25, totalFactoredLoad = 18.0,
            panelMomentX = 120.0, panelMomentY = 150.0,
            columnStripMomentPos = 42.0, columnStripMomentNeg = 78.0,
            middleStripMomentPos = 21.0, middleStripMomentNeg = 30.0,
            columnStripWidthX = 1875.0, columnStripWidthY = 1875.0,
            columnStripTopRebar = RebarResult(8, 16, 150, 1608.5, 1200.0, 0.007),
            columnStripBotRebar = RebarResult(8, 16, 150, 1608.5, 1000.0, 0.006),
            middleStripTopRebar = RebarResult(6, 14, 200, 923.6, 800.0, 0.005),
            middleStripBotRebar = RebarResult(6, 14, 200, 923.6, 700.0, 0.004),
            punchingShearOk = true, punchingShearVu = 400.0, punchingShearVc = 500.0,
            punchingPerimeter = 3200.0, punchingReinforcement = null,
            dropRequired = true, dropThickness = 80.0,
            deflectionOk = true, deflection = 12.5, allowableDeflection = 25.0,
            concreteVolumePerPanel = 7.5, steelWeightPerPanel = 420.0
        )
        val input = FlatSlabInput(
            panelType = com.civileg.app.domain.PanelType.INTERIOR,
            designMethod = com.civileg.app.domain.DesignMethod.DDM,
            lx = 6000.0, ly = 7500.0, slabThickness = 250.0,
            dropThickness = 80.0, dropSizeX = 1200.0, dropSizeY = 1200.0,
            columnWidth = 400.0, columnDepth = 400.0, clearCover = 25.0
        )

        val model = LiveDrawingModel.flatSlab(res, input, projectName = "Bridge", designCode = "ECP")

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.slabSection is SlabSectionGeometryFlat)
        assertTrue(model.dimensions.all.size == 2)
        // 4 strip groups × their engine counts (8+8+6+6) each become a bar instance
        assertEquals(28, model.reinforcement.all.size)
        assertTrue(model.state.overallStatus.name == "PASS")
    }

    @Test
    fun shearWallMapsResultToWallPlanSection() {
        val res = ShearWallResult(
            isSafe = true, utilizationRatio = 0.62,
            flexuralOk = true, momentCapacity = 4500.0, axialCapacity = 12000.0,
            compressionDepth = 80.0,
            verticalReinforcement = RebarResult(10, 12, 150, 1131.0, 900.0, 0.003),
            boundaryElementType = BoundaryElementType.STANDARD,
            boundaryElementReinforcement = RebarResult(4, 14, 100, 615.8, 500.0, 0.004),
            shearOk = true, shearCapacity = 1400.0, concreteShearCapacity = 900.0,
            steelShearCapacity = 500.0,
            horizontalReinforcement = RebarResult(2, 10, 200, 157.1, 100.0, 0.001),
            slendernessOk = true, slendernessRatio = 20.0,
            couplingBeamResult = null,
            concreteVolumePerStory = 3.6, steelWeightPerStory = 220.0
        )
        val input = ShearWallInput(
            wallType = WallType.ORDINARY,
            wallLength = 4000.0, wallThickness = 300.0, wallHeight = 3000.0,
            numberOfStories = 10, axialLoad = 5000.0, shearForce = 800.0,
            bendingMoment = 3000.0, fcu = 30.0, fy = 400.0, fyv = 250.0,
            clearCover = 25.0, endZoneLength = 0.0
        )

        val model = LiveDrawingModel.shearWall(
            res, input, wallShape = "Rectangular", projectName = "Bridge", designCode = "ECP"
        )

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        assertTrue(model.shearWallSection != null)
        assertEquals(2, model.dimensions.all.size)
        // web 10 + boundary schedule 4×2 ends + face bars 2×(4000/200) = 58 bars
        assertEquals(58, model.reinforcement.all.size)
        // auto boundary end zone (12% × length = 480) is driven by the engine's
        // boundary family → one confinement tie family for the emitter's loop
        assertEquals(1, model.shearWallSection!!.boundaryTies.size)
        // rectangular plan cut: exact 4000 × 300 mm geometry (no flange)
        val s = model.shearWallSection!!.sectionBounds
        assertEquals(4000.0, s.maxX - s.minX, 1e-9)
        assertEquals(300.0, s.maxY - s.minY, 1e-9)
        assertTrue(model.state.overallStatus.name == "PASS")
    }

    @Test
    fun steelMemberMapsResultToElevationAndCutAA() {
        val res = SteelMemberResult(
            sectionType = SteelSectionType.ISection(
                h = 300.0, bf = 300.0, tf = 15.0, tw = 10.0,
                grade = SteelGrade.A572_G50
            ),
            memberType = SteelMemberType.COLUMN,
            axialCapacity = 2500.0, flexuralCapacity = 800.0, shearCapacity = 600.0,
            utilizationRatio = 0.72, isSafe = true,
            connectionDesign = null, bucklingCheck = null,
            weight = 57.8, cost = 1000.0, warnings = emptyList(), codeNotes = emptyList()
        )

        val model = LiveDrawingModel.steelMember(
            res, lengthMm = 6000.0, steelCode = CalculatorEngine.AppDesignCode.ACI,
            projectName = "Bridge"
        )

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        val s = model.steelSection!!
        assertEquals("I 300x300", s.sectionName)
        assertEquals(6000.0, s.memberLengthMm, 1e-9)
        assertEquals(300.0, s.depthMm, 1e-9)      // engine h
        assertEquals(300.0, s.widthMm, 1e-9)       // engine bf
        assertEquals(10.0, s.webThicknessMm, 1e-9) // engine tw
        assertEquals(15.0, s.flangeThicknessMm, 1e-9) // engine tf
        // elevation 0..6000 + 60mm gap + A-A width 300
        assertEquals(6360.0, s.sectionBounds.maxX - s.sectionBounds.minX, 1e-9)
        assertEquals(300.0, s.sectionBounds.maxY - s.sectionBounds.minY, 1e-9)
        assertEquals(3, model.dimensions.all.size)  // length / depth / width
        assertEquals(0, model.reinforcement.all.size) // steel bears no bars
        assertEquals("COL-1", model.steelMembers.first().mark)
        assertEquals("I 300x300", model.steelMembers.first().sectionName)
        assertTrue(model.annotations.all.any { it.text == "ELEVATION" })
        assertTrue(model.annotations.all.any { it.text == "SECTION A-A" })
        // ACI → the member PDF's steel-code label, not the concrete edition
        assertEquals("AISC 360-16", model.titleBlock.designCode)
        assertTrue(model.state.overallStatus.name == "PASS")
    }

    @Test
    fun frameMapsResultToElevationAndSchedule() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 6.0, 0.0, SupportType.Fixed),
            FrameNode(3, 6.0, 4.0, SupportType.Free),
            FrameNode(4, 0.0, 4.0, SupportType.Free)
        )
        val colSec = ConcreteSectionProps(width = 300.0, depth = 600.0)
        val beamSec = ConcreteSectionProps(width = 250.0, depth = 500.0)
        val members = listOf(
            FrameMember(1, 1, 4, FrameMaterialType.Concrete, FrameMemberType.Column, colSec, name = "Col L"),
            FrameMember(2, 2, 3, FrameMaterialType.Concrete, FrameMemberType.Column, colSec, name = "Col R"),
            FrameMember(3, 4, 3, FrameMaterialType.Concrete, FrameMemberType.Beam, beamSec, name = "Beam")
        )
        fun cr(memberId: Int, name: String, util: Double) = ConcreteMemberDesignResult(
            memberId = memberId, memberName = name,
            memberType = if (memberId < 3) FrameMemberType.Column else FrameMemberType.Beam,
            section = if (memberId < 3) colSec else beamSec,
            maxMoment = 100.0, maxShear = 80.0, axialForce = 300.0,
            asRequired = 1000.0, asProvided = 1600.0, barDia = 20.0,
            numBarsTop = 4, numBarsBot = 4, asTop = 1256.0, asBot = 1256.0,
            stirrupDia = 8.0, stirrupSpacing = 150.0, vuCapacity = 120.0,
            momentUtilization = util, shearUtilization = util / 2.0, isSafe = util <= 1.0
        )
        val result = FrameAnalysisResult(
            isSolved = true,
            concreteDesignResults = listOf(cr(1, "Col L", 0.6), cr(2, "Col R", 0.5), cr(3, "Beam", 0.4)),
            steelDesignResults = emptyList()
        )

        val model = LiveDrawingModel.frame(
            nodes, members, result,
            FrameAnalysisSettings(designCode = DesignCode.ECP),
            projectName = "Bridge"
        )

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        val f = model.frameGeometry!!
        assertEquals(6000.0, f.totalSpanMm, 1e-9)
        assertEquals(4000.0, f.totalHeightMm, 1e-9)
        assertEquals(3, f.members.size)
        // columns band = section width (300), beam band = section depth (500)
        assertTrue(f.members.all { it.materialType == "CONCRETE" })
        assertEquals(300.0, f.members.first { it.memberType == "COLUMN" }.bandMm, 1e-9)
        assertEquals(500.0, f.members.first { it.memberType == "BEAM" }.bandMm, 1e-9)
        // two fixed base supports
        assertEquals(2, f.supports.size)
        assertTrue(f.supports.all { it.supportType == "FIXED" })
        // team schedule rows
        assertEquals(3, model.frameMembers.size)
        val first = model.frameMembers[0]
        assertEquals("FM-1", first.mark)
        assertEquals("COLUMN", first.memberType)
        assertEquals("300x600", first.sectionName)
        assertEquals(4000.0, first.lengthMm, 1e-9)
        // dimensions fold into the model
        assertTrue(model.dimensions.all.isNotEmpty())
        // ECP frame → concrete-edition title label
        assertEquals("ECP 203-2020", model.titleBlock.designCode)
        assertTrue(model.annotations.all.any { it.text == "FRAME ELEVATION" })
        assertTrue(model.state.overallStatus.name == "PASS")
    }

    @Test
    fun seismicMapsResultToSpectrumAndForceChart() {
        val baseShearResult = SeismicBaseShearResult(
            baseShear = 1250.0,
            zoneFactor = 0.15,
            soilFactor = 1.5,
            importanceFactor = 1.0,
            responseModification = 5.0,
            calculationFormula = "V = Sd(T1) × W",
            codeReference = "ECP 201 §8",
            warnings = emptyList()
        )
        val spectrumValues = (1..50).map { i ->
            SpectrumValue(
                spectralAcceleration = (0.56 - 0.01 * (i - 1)).coerceAtLeast(0.06),
                period = i * 0.06,
                dampingRatio = 0.05,
                description = "curve"
            )
        }
        val floorForces = listOf(
            SeismicForceDistribution(0, 500.0, 3.0, 150.0, 1250.0, 3750.0),
            SeismicForceDistribution(1, 500.0, 6.0, 300.0, 1100.0, 6600.0),
            SeismicForceDistribution(2, 500.0, 9.0, 450.0, 800.0, 7200.0),
            SeismicForceDistribution(3, 500.0, 12.0, 350.0, 350.0, 4200.0)
        )

        val model = LiveDrawingModel.seismic(
            baseShearResult = baseShearResult,
            spectrumValues = spectrumValues,
            floorForces = floorForces,
            fundamentalPeriod = 0.545,
            spectralAccel = 0.462,
            code = DesignCode.ECP,
            projectName = "Tower"
        )

        assertFalse(DrawingModelBuilder.validate(model).hasInvalid)
        val s = model.seismicChart!!
        // 50 curve points, passed through verbatim, laid inside the spectrum pane
        assertEquals(50, s.spectrumPoints.size)
        assertEquals(spectrumValues[0].period, s.spectrumPoints[0].period, 1e-9)
        assertEquals(spectrumValues[0].spectralAcceleration, s.spectrumPoints[0].acceleration, 1e-9)
        s.spectrumPoints.forEach { p ->
            assertTrue(p.xMm >= s.spectrumBox.minX && p.xMm <= s.spectrumBox.maxX)
            assertTrue(p.yMm >= s.spectrumBox.minY && p.yMm <= s.spectrumBox.maxY)
        }
        // design-period + base-shear terms pass through
        assertEquals(0.545, s.fundamentalPeriod, 1e-9)
        assertEquals(1250.0, s.baseShearKn, 1e-9)
        assertEquals(0.15, s.zoneFactor, 1e-9)
        // per-floor bars mirror the engine forces, longest bar = max force
        assertEquals(4, s.forceBars.size)
        assertEquals(300.0, s.forceBars[1].forceKn, 1e-9)
        assertEquals(450.0, s.forceBars.maxByOrNull { it.barLengthMm }!!.forceKn, 1e-9)
        // chart title + ledger-visible note annotations present
        assertTrue(model.annotations.all.any { it.text == "RESPONSE SPECTRUM - Sa (g) VS T (s)" })
        assertTrue(model.annotations.all.any { it.text.startsWith("V = 1250 kN") })
        // ECP edition label + PASS state
        assertEquals("ECP 203-2020", model.titleBlock.designCode)
        assertTrue(model.state.overallStatus.name == "PASS")
    }
}
