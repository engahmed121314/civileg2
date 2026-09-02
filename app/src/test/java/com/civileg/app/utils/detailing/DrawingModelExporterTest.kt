package com.civileg.app.utils.detailing

import com.civileg.core.calculations.entities.DesignCode
import com.civileg.core.calculations.entities.DrawingModel
import com.civileg.core.calculations.entities.DrawingModelBuilder
import com.civileg.core.calculations.entities.DrawingState
import com.civileg.core.calculations.entities.ReinforcementBar
import com.civileg.core.calculations.entities.ReinforcementResult
import com.civileg.core.calculations.entities.SectionBar
import com.civileg.core.calculations.entities.SlabSectionGeometryFlat
import com.civileg.core.calculations.entities.SteelMemberMark
import com.civileg.core.calculations.entities.FrameMemberMark
import com.civileg.core.calculations.entities.StirrupZone
import com.civileg.core.calculations.entities.TitleBlock
import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.CodeVersionRegistry
import com.civileg.core.engineering.FlatSlabReinforcementResult
import com.civileg.core.engineering.FlatSlabStripReinforcement
import com.civileg.core.engineering.FrameAnalysisDetailResult
import com.civileg.core.engineering.FrameMemberDetail
import com.civileg.core.engineering.FrameNodeDetail
import com.civileg.core.engineering.RetainingWallReinforcementResult
import com.civileg.core.engineering.SeismicDetailResult
import com.civileg.core.engineering.SeismicFloorForcePoint
import com.civileg.core.engineering.SeismicSpectrumPoint
import com.civileg.core.engineering.ShearWallBoundaryReinforcement
import com.civileg.core.engineering.ShearWallCouplingReinforcement
import com.civileg.core.engineering.ShearWallHorizontalReinforcement
import com.civileg.core.engineering.ShearWallReinforcementResult
import com.civileg.core.engineering.ShearWallVerticalReinforcement
import com.civileg.core.engineering.SlabReinforcementResult
import com.civileg.core.engineering.StairReinforcementResult
import com.civileg.core.engineering.SteelMemberReinforcementResult
import com.civileg.core.engineering.TankReinforcementResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DrawingModelExporterTest {

    private fun sampleResult() = ReinforcementResult(
        astRequired = 1200.0,
        astProvided = 1570.0,
        barDiameter = 20.0,
        numberOfBars = 5,
        tiesDiameter = 8.0,
        tiesSpacing = 150.0,
        numLegs = 2,
        isSafe = true,
        utilizationRatio = 0.76,
        spacing = 150.0,
        description = "5Ø20"
    )

    // R2 (P044): engine-style confinement zones — 100 mm at the support bands,
    // relaxed to 150 mm across the mid span.
    private fun zonedBeamResult() = sampleResult().copy(
        tiesSpacing = 150.0,
        zones = listOf(
            StirrupZone("Support Zone (Left)", 0.0, 1000.0, 100.0, 2, 8, "Ø8 @ 100 mm c/c · 2-Leg"),
            StirrupZone("Mid-Span Zone", 1000.0, 4000.0, 150.0, 2, 8, "Ø8 @ 150 mm c/c · 2-Leg"),
            StirrupZone("Support Zone (Right)", 4000.0, 5000.0, 100.0, 2, 8, "Ø8 @ 100 mm c/c · 2-Leg")
        )
    )

    private fun sampleModel() = DrawingModelBuilder.buildBeam(
        project = "Bridge",
        drawingNumber = "B-101",
        sheetNumber = "1/1",
        titleBlock = TitleBlock(
            project = "Bridge",
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
        beamResult = sampleResult()
    )

    @Test
    fun toCadDerivesAllPrimitivesFromModel() {
        val cad = DrawingModelExporter.toCad(sampleModel())

        assertTrue(cad.entities.isNotEmpty())
        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.CONC })
        assertTrue(cad.entities.any { it is CadHatch })
        assertTrue(cad.entities.any { it is CadCircle && it.layer == CadLayers.REBAR })
        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.STIRRUP })
        assertEquals("B-101", cad.titleBlock.drawingNumber)
        assertEquals("Bridge", cad.titleBlock.project)
    }

    @Test
    fun writeDxfProducesValidSkeleton() {
        val dxf = DrawingModelExporter.writeDxf(sampleModel())

        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("AcDbEntity"))
        assertTrue(dxf.trimEnd().endsWith("EOF"))
    }

    @Test
    fun writeDxfRejectsInvalidModel() {
        val model = sampleModel().copy(
            state = DrawingState(
                code = DesignCode.ECP,
                edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
                overallStatus = CheckStatus.PASS
            ),
            reinforcement = sampleModel().reinforcement.copy(
                mainTensionBars = listOf(
                    ReinforcementBar(
                        mark = "BAD", diameter = Double.NaN, totalLengthMm = 1000.0,
                        shape = "STRAIGHT", element = "beam", codeReference = "x", quantity = 1
                    )
                )
            )
        )

        try {
            DrawingModelExporter.writeDxf(model)
            fail("Expected IllegalArgumentException for NaN-bearing model")
        } catch (expected: IllegalArgumentException) {
            // validate() gate protects the writer from NaN/Inf geometry.
        }
    }

    private fun beamElevationModel(): DrawingModel = DrawingModelBuilder.buildBeam(
        project = "Bridge",
        drawingNumber = "B-201",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("B-201", "Beam Elevation"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        overallHeight = 700.0,
        overallWidth = 300.0,
        effectiveDepth = 640.0,
        concreteCover = 40.0,
        beamLength = 5000.0,
        beamResult = sampleResult(),
        supportTypeName = "FIXED_FIXED",
        appliedMomentKnM = 120.0,
        appliedShearKn = 60.0
    )

    @Test
    fun beamElevationDrawsSupportsLoadsAndDiagrams() {
        val model = beamElevationModel()
        val cad = DrawingModelExporter.toCad(model)

        // fixed–fixed: two FOUNDATION support blocks + ground ticks
        assertEquals(2, cad.entities.count { it is CadPolyline && it.layer == CadLayers.FOUNDATION })
        assertTrue(cad.entities.any { it is CadHatch && it.layer == CadLayers.FOUNDATION })
        assertTrue(cad.entities.any { it is CadLine && it.layer == CadLayers.SOIL })
        // 5 UDL arrowheads + the beam member outline
        assertEquals(5, cad.entities.count { it is CadPolyline && it.layer == CadLayers.LOAD })
        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.CONC })
        // one ANALYSIS polyline per diagram, 33 normalized ordinates each
        val curves = cad.entities.filterIsInstance<CadPolyline>().filter { it.layer == CadLayers.ANALYSIS }
        assertEquals(2, curves.size)
        assertTrue(curves.all { it.points.size == 33 })
        // captions land on the sheet (English per ADR-009)
        val texts = cad.entities.filterIsInstance<CadText>().map { it.text }
        assertTrue(texts.any { it.startsWith("CASE: FIXED-FIXED") })
        assertTrue(texts.any { it.contains("M max = 120") })
        // Mmax / Vmax peak tags present
        assertTrue(texts.any { it.startsWith("Mmax ") })
        assertTrue(texts.any { it.startsWith("Vmax ") })
    }

    @Test
    fun beamElevationSheetKeepsBbsSchedule() {
        val dxf = DrawingModelExporter.writeDxfWithSchedule(beamElevationModel())

        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.trimEnd().endsWith("EOF"))
        assertFalse(dxf.contains("NaN"))
        // beam sheet keeps the bar schedule (elevation is a drawing layer)
        assertTrue(dxf.contains("SPACING"))
        assertTrue(dxf.contains("\\U+00D820"))
    }

    @Test
    fun beamElevationStirrupTicksFollowZones() {
        val model = DrawingModelBuilder.buildBeam(
            project = "Bridge",
            drawingNumber = "B-201",
            sheetNumber = "1/1",
            titleBlock = coreTitleBlock("B-201", "Beam Elevation"),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            overallHeight = 700.0,
            overallWidth = 300.0,
            effectiveDepth = 640.0,
            concreteCover = 40.0,
            beamLength = 5000.0,
            beamResult = zonedBeamResult(),
            supportTypeName = "FIXED_FIXED",
            appliedMomentKnM = 120.0,
            appliedShearKn = 60.0
        )
        val cad = DrawingModelExporter.toCad(model)

        val ticks = cad.entities.filterIsInstance<CadLine>().filter { it.layer == CadLayers.STIRRUP }
        assertTrue("expected stirrup ticks in the elevation (DENSE@100 / MID@150)", ticks.isNotEmpty())

        // Density per unit span: 100 mm support bands must carry more ticks
        // per mm of member than the relaxed 150 mm mid span.
        val e = model.beamElevation!!
        val minX = e.beamBox.minX
        val bw = e.beamBox.maxX - e.beamBox.minX
        val leftBandEnd = minX + (1000.0 / 5000.0) * bw
        val midBandEnd = minX + (4000.0 / 5000.0) * bw
        // Strict interiors: the tick exactly ON a shared zone boundary is drawn
        // twice (once per adjacent loop), so classify by strict inequalities to
        // keep every entity in exactly one band and left/right symmetric.
        val left = ticks.count { it.x1 < leftBandEnd - 1e-6 }
        val mid = ticks.count { it.x1 in (leftBandEnd + 1e-6)..(midBandEnd - 1e-6) }
        val right = ticks.count { it.x1 > midBandEnd + 1e-6 }
        // Symmetric support bands place the same tick count at both ends.
        assertEquals(left, right)
        assertTrue(
            "support-band density ($left / 0.2m) must exceed mid density ($mid / 0.6m)",
            left / 0.2 > mid / 0.6
        )
    }

    @Test
    fun beamElevationWithoutZonesHasNoStirrupTicks() {
        val cad = DrawingModelExporter.toCad(beamElevationModel())
        val ticks = cad.entities.filterIsInstance<CadLine>().filter { it.layer == CadLayers.STIRRUP }
        assertEquals(0, ticks.size)
    }

    private fun coreTitleBlock(drawingNumber: String, title: String) = TitleBlock(
        project = "Bridge",
        drawingTitle = title,
        drawingNumber = drawingNumber,
        date = "2026-08-27",
        scale = "",
        designCode = "ECP 203-2020"
    )

    private fun stairModel() = DrawingModelBuilder.buildStair(
        project = "Bridge",
        drawingNumber = "S-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("S-101", "Stair Section"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        waistThicknessMm = 150.0,
        effectiveDepthMm = 120.0,
        concreteCoverMm = 20.0,
        stairWidthMm = 1200.0,
        inclinedLengthMm = 3600.0,
        stair = StairReinforcementResult(
            mainDiameter = 12.0, mainSpacingMm = 150.0,
            distributionDiameter = 10.0, distributionSpacingMm = 200.0,
            isSafe = true, warnings = emptyList()
        )
    )

    private fun tankModel() = DrawingModelBuilder.buildTank(
        project = "Bridge",
        drawingNumber = "T-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("T-101", "Tank Section"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        lengthMm = 4000.0, widthMm = 3000.0, heightMm = 2500.0,
        wallThicknessMm = 250.0, baseThicknessMm = 300.0,
        effectiveDepthMm = 210.0, concreteCoverMm = 50.0,
        tank = TankReinforcementResult(
            wallDiameter = 12.0, wallSpacingMm = 150.0,
            wallHorizontalDiameter = 10.0, wallHorizontalSpacingMm = 200.0,
            baseDiameter = 14.0, baseSpacingMm = 180.0,
            isSafe = true, warnings = emptyList()
        )
    )

    private fun retainingWallModel() = DrawingModelBuilder.buildRetainingWall(
        project = "Bridge",
        drawingNumber = "W-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("W-101", "Retaining Wall Section"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        wallHeightMm = 3500.0, stemHeightMm = 3000.0,
        stemBaseThicknessMm = 300.0, baseWidthMm = 2500.0,
        baseThicknessMm = 400.0, toeLengthMm = 800.0, heelLengthMm = 1100.0,
        concreteCoverMm = 50.0,
        wall = RetainingWallReinforcementResult(
            stemMainCount = 6, stemMainDiameter = 16.0, stemMainSpacingMm = 160.0,
            distributionBarsCount = 8, distributionDiameter = 10.0, distributionSpacingMm = 200.0,
            toeBarsCount = 6, toeDiameter = 14.0, toeSpacingMm = 160.0,
            heelBarsCount = 6, heelDiameter = 14.0, heelSpacingMm = 160.0,
            isSafe = true, warnings = emptyList()
        )
    )

    @Test
    fun stairSectionDerivesAllPrimitivesFromModel() {
        val cad = DrawingModelExporter.toCad(stairModel())

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.CONC })
        assertTrue(cad.entities.any { it is CadHatch })
        assertTrue(cad.entities.any { it is CadCircle && it.layer == CadLayers.REBAR })
    }

    @Test
    fun tankSectionDerivesAllPrimitivesFromModel() {
        val cad = DrawingModelExporter.toCad(tankModel())

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.WALL })
        assertTrue(cad.entities.any { it is CadHatch })
        assertTrue(cad.entities.any { it is CadCircle && it.layer == CadLayers.REBAR })
    }

    @Test
    fun retainingWallSectionDerivesAllPrimitivesFromModel() {
        val cad = DrawingModelExporter.toCad(retainingWallModel())

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.WALL })
        assertTrue(cad.entities.any { it is CadHatch })
        assertTrue(cad.entities.any { it is CadCircle && it.layer == CadLayers.REBAR })
    }

    private fun slabModel() = DrawingModelBuilder.buildSlab(
        project = "Bridge",
        drawingNumber = "SL-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("SL-101", "Slab Section"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        thickness = 180.0, effectiveDepth = 140.0, concreteCover = 25.0,
        shortSpanMm = 4000.0, longSpanMm = 5000.0,
        slab = SlabReinforcementResult(
            shortBarSelection = 7 to 12.0,
            longBarSelection = 6 to 12.0,
            shortSpacingMm = 150.0, longSpacingMm = 180.0,
            isSafe = true, warnings = emptyList()
        )
    )

    private fun flatSlabModel() = DrawingModelBuilder.buildFlatSlab(
        project = "Bridge",
        drawingNumber = "FS-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("FS-101", "Flat Slab Strip Section"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        thickness = 250.0, effectiveDepth = 217.0, concreteCover = 25.0,
        shortSpanMm = 4000.0, longSpanMm = 5000.0,
        columnStripWidthMm = 1500.0,
        dropDepthMm = 80.0, dropSizeMm = 1200.0,
        slab = FlatSlabReinforcementResult(
            columnStripTop = FlatSlabStripReinforcement(6 to 14.0, 150.0),
            columnStripBottom = FlatSlabStripReinforcement(6 to 14.0, 150.0),
            middleStripTop = FlatSlabStripReinforcement(5 to 12.0, 200.0),
            middleStripBottom = FlatSlabStripReinforcement(5 to 12.0, 200.0),
            isSafe = true, warnings = emptyList()
        )
    )

    @Test
    fun flatSlabSectionDerivesZoneLayoutAndPrimitives() {
        val model = flatSlabModel()
        val cad = DrawingModelExporter.toCad(model)

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.CONC })
        assertTrue(cad.entities.any { it is CadCircle && it.layer == CadLayers.REBAR })
        // 6+6+5+5 strip bars → one CAD circle each
        assertEquals(22, cad.entities.count { it is CadCircle })
        // Drop panel outline -> an extra CONC polyline beyond the slab rect
        assertEquals(2, cad.entities.count { it is CadPolyline && it.layer == CadLayers.CONC })

        // Zone split: col-strip circles (12) stay left of x=1500, mid-strip (10) right.
        val colXs = cad.entities.filterIsInstance<CadCircle>().map { it.cx }.filter { it <= 1500.0 + 1e-6 }
        val midXs = cad.entities.filterIsInstance<CadCircle>().map { it.cx }.filter { it >= 1500.0 - 1e-6 }
        assertEquals(12, colXs.size)
        assertEquals(10, midXs.size)
    }

    @Test
    fun flatSlabSectionWithoutDropHasNoDropOutline() {
        val cad = DrawingModelExporter.toCad(
            flatSlabModel().copy(
                slabSection = (flatSlabModel().slabSection as SlabSectionGeometryFlat).copy(
                    dropDepth = 0.0, dropSize = 0.0
                )
            )
        )

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.CONC })
        assertEquals(1, cad.entities.count { it is CadPolyline && it.layer == CadLayers.CONC })
        assertEquals(22, cad.entities.count { it is CadCircle })
    }

    @Test
    fun slabSectionDerivesAllPrimitivesFromModel() {
        val cad = DrawingModelExporter.toCad(slabModel())

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.CONC })
        assertTrue(cad.entities.any { it is CadCircle && it.layer == CadLayers.REBAR })
    }

    @Test
    fun writeDxfWithScheduleProducesValidSingleSheet() {
        val dxf = DrawingModelExporter.writeDxfWithSchedule(sampleModel())

        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("AcDbEntity"))
        assertTrue(dxf.trimEnd().endsWith("EOF"))
        assertFalse(dxf.contains("NaN"))
        assertTrue(dxf.contains("SPACING"))        // schedule header written
        assertTrue(dxf.contains("\\U+00D820"))     // bar diameter Ø20 encoded as DXF unicode escape
        assertTrue(dxf.contains("1:"))            // title-block SCALE filled with the chosen scale
    }

    @Test
    fun unsafeDesignStampsTheFailWatermarkOnTheSheet() {
        val failing = sampleModel().copy(
            state = DrawingState(
                code = DesignCode.ECP,
                edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
                overallStatus = CheckStatus.FAIL
            )
        )

        val dxf = DrawingModelExporter.writeDxfWithSchedule(failing)

        assertTrue("unsafe design must be stamped on the DXF sheet",
            dxf.contains("NOT SAFE - DESIGN FAILS"))
        assertTrue(dxf.contains("DO NOT ISSUE FOR CONSTRUCTION"))
        assertFalse(dxf.contains("NaN"))
    }

    @Test
    fun safeDesignCarriesNoFailStamp() {
        val dxf = DrawingModelExporter.writeDxfWithSchedule(sampleModel())

        assertFalse("safe design must not carry the FAIL watermark",
            dxf.contains("NOT SAFE - DESIGN FAILS"))
    }

    @Test
    fun failStampEntitiesOnlyForFailState() {
        assertTrue(DrawingModelExporter.failStampEntities(sampleModel(), 420.0, 297.0).isEmpty())
        val failing = sampleModel().copy(
            state = DrawingState(
                code = DesignCode.ECP,
                edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
                overallStatus = CheckStatus.FAIL
            )
        )
        val stamp = DrawingModelExporter.failStampEntities(failing, 420.0, 297.0)
        assertEquals(3, stamp.size)
        val texts = stamp.mapNotNull { (it as? CadText)?.text }
        assertTrue(texts.any { it == "NOT SAFE - DESIGN FAILS" })
    }

    @Test
    fun barScheduleGroupsBarsByDiameterLengthSpacingElement() {
        val model = sampleModel().copy(
            reinforcement = sampleModel().reinforcement.copy(
                mainTensionBars = listOf(
                    ReinforcementBar("M1", 20.0, 5000.0, "STRAIGHT", "beam", "x", quantity = 1, spacing = 150.0),
                    ReinforcementBar("M2", 20.0, 5000.0, "STRAIGHT", "beam", "x", quantity = 1, spacing = 150.0),
                    ReinforcementBar("M3", 20.0, 5000.0, "STRAIGHT", "beam", "x", quantity = 1, spacing = 150.0),
                    ReinforcementBar("M4", 20.0, 5000.0, "STRAIGHT", "beam", "x", quantity = 1, spacing = 150.0),
                    ReinforcementBar("M5", 16.0, 4800.0, "STRAIGHT", "beam", "x", quantity = 1, spacing = 200.0)
                )
            )
        )

        val rows = DrawingModelExporter.barScheduleTable(model).rows

        // Ø8 stirrups (34), Ø16 (1), Ø20 (4) — three distinct groups
        assertEquals(3, rows.size)
        val dia20 = rows.first { it[1] == "Ø20" }
        assertEquals("Ø20", dia20[1])
        assertEquals("4", dia20[3]) // 4 identical bars collapse into QTY=4
        val dia16 = rows.first { it[1] == "Ø16" }
        assertEquals("1", dia16[3])
    }

    private fun shearWallModel(withBoundary: Boolean, withCoupling: Boolean) =
        DrawingModelBuilder.buildShearWall(
            project = "Bridge",
            drawingNumber = "SW-101",
            sheetNumber = "1/1",
            titleBlock = coreTitleBlock("SW-101", "Shear Wall Section"),
            code = DesignCode.ECP,
            edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
            wallLengthMm = 4000.0,
            wallThicknessMm = 300.0,
            storyHeightMm = 3000.0,
            couplingBeamClearSpanMm = if (withCoupling) 1600.0 else 0.0,
            couplingBeamHeightMm = if (withCoupling) 600.0 else 0.0,
            boundaryElementLengthMm = if (withBoundary) 480.0 else 0.0,
            wallShape = "Rectangular",
            concreteCoverMm = 25.0,
            wall = ShearWallReinforcementResult(
                vertical = ShearWallVerticalReinforcement(count = 10, diameterMm = 12.0, spacingMm = 150.0),
                horizontal = ShearWallHorizontalReinforcement(diameterMm = 10.0, spacingMm = 200.0),
                boundary = if (withBoundary)
                    ShearWallBoundaryReinforcement(bars = 4, diameterMm = 14.0, spacingMm = 100.0)
                else null,
                couplingBeam = if (withCoupling)
                    ShearWallCouplingReinforcement(
                        diagonalBars = 2, diagonalDiameterMm = 20.0,
                        transverseDiameterMm = 10.0, transverseSpacingMm = 150.0
                    )
                else null,
                isSafe = true,
                warnings = emptyList()
            )
        )

    @Test
    fun shearWallPlanSectionDrawsFaceRowsAndEndZoneTies() {
        val cad = DrawingModelExporter.toCad(shearWallModel(withBoundary = true, withCoupling = true))

        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.WALL })
        assertTrue(cad.entities.any { it is CadHatch })
        // web 10 + face rows 2×(4000/200=20) → 50 longitudinal circles
        assertEquals(50, cad.entities.count { it is CadCircle && it.layer == CadLayers.REBAR })
        // end zones: 2 zones × (1 zone outline + 4 tie loops at 480/100)
        assertEquals(10, cad.entities.count { it is CadPolyline && it.layer == CadLayers.STIRRUP })
    }

    @Test
    fun shearWallCouplingDiagonalScheduledAndGrouped() {
        val model = shearWallModel(withBoundary = false, withCoupling = true)

        // diagonal cut = √(1600² + 600²) single bar family, grouped QTY=2
        val diag = model.reinforcement.all.first { it.mark.startsWith("SW-CB-D-") }
        assertEquals(kotlin.math.sqrt(1600.0 * 1600.0 + 600.0 * 600.0), diag.totalLengthMm, 1e-6)
        val rows = DrawingModelExporter.barScheduleTable(model).rows
        val diagRow = rows.first { it[0].startsWith("SW-CB-D-") }
        assertEquals("2", diagRow[3])
    }

    @Test
    fun shearWallWithoutBoundaryDrawsNoEndZoneHoops() {
        val model = shearWallModel(withBoundary = false, withCoupling = true)
        val cad = DrawingModelExporter.toCad(model)

        assertFalse(model.reinforcement.all.any { it.mark.startsWith("SW-B-") })
        assertEquals(0, cad.entities.count { it is CadPolyline && it.layer == CadLayers.STIRRUP })
        assertTrue(cad.entities.any { it is CadPolyline && it.layer == CadLayers.WALL })
    }

    private fun steelModel(): DrawingModel = DrawingModelBuilder.buildSteelMember(
        project = "Bridge",
        drawingNumber = "ST-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("ST-101", "Steel Member Elevation"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        member = SteelMemberReinforcementResult(
            sectionName = "HEB 300",
            memberType = "COLUMN",
            memberLengthMm = 6000.0,
            depthMm = 300.0,
            widthMm = 300.0,
            webThicknessMm = 10.0,
            flangeThicknessMm = 15.0,
            utilizationRatio = 0.72,
            isSafe = true,
            codeReference = "AISC 360-B4 / ECP 205-3",
            warnings = emptyList()
        ),
        steelMembers = listOf(
            SteelMemberMark(mark = "COL-1", sectionName = "HEB 300", lengthMm = 6000.0, quantity = 1)
        )
    )

    @Test
    fun steelMemberDrawsElevationAndCutAAFromProfile() {
        val cad = DrawingModelExporter.toCad(steelModel())

        // elevation box + 3 A-A plates (top flange / web / bottom flange)
        assertEquals(4, cad.entities.count { it is CadPolyline && it.layer == CadLayers.STEEL })
        // flange-interface lines on the elevation
        assertEquals(2, cad.entities.count { it is CadLine && it.layer == CadLayers.STEEL })
        // elevation centreline + cut centreline
        assertEquals(2, cad.entities.count { it is CadCenterLine })
        // 3 plate faces hatched steel (ANSI31)
        assertEquals(3, cad.entities.count { it is CadHatch })
        // steel bears no bars — no circles / stirrup loops
        assertEquals(0, cad.entities.count { it is CadCircle })
        assertEquals(0, cad.entities.count { it is CadPolyline && it.layer == CadLayers.STIRRUP })
        // length / depth / width dimensions from the model
        assertEquals(3, cad.entities.count { it is CadDimLinear })
    }

    @Test
    fun steelMemberSheetUsesSteelScheduleInsteadOfBbs() {
        val dxf = DrawingModelExporter.writeDxfWithSchedule(steelModel())

        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("MARK"))
        assertTrue(dxf.contains("SECTION"))
        assertTrue(dxf.contains("HEB 300"))
        assertTrue(dxf.contains("COL-1"))
        // steel table has no bar schedule columns
        assertFalse(dxf.contains("SPACING"))
        assertFalse(dxf.contains("\\U+00D8"))
    }

    private fun frameModel(): DrawingModel = DrawingModelBuilder.buildFrame(
        project = "Bridge",
        drawingNumber = "FR-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("FR-101", "Frame Elevation"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        detail = FrameAnalysisDetailResult(
            nodes = listOf(
                FrameNodeDetail(0.0, 0.0, "FIXED"),
                FrameNodeDetail(6000.0, 0.0, "FIXED"),
                FrameNodeDetail(6000.0, 4000.0, "FREE"),
                FrameNodeDetail(0.0, 4000.0, "FREE")
            ),
            members = listOf(
                FrameMemberDetail(1, 0.0, 0.0, 0.0, 4000.0, "CONCRETE", "COLUMN", 300.0, "300x600", true, 0.6),
                FrameMemberDetail(2, 6000.0, 0.0, 6000.0, 4000.0, "CONCRETE", "COLUMN", 300.0, "300x600", true, 0.5),
                FrameMemberDetail(3, 0.0, 4000.0, 6000.0, 4000.0, "CONCRETE", "BEAM", 500.0, "250x500", true, 0.4)
            ),
            isSafe = true,
            codeReference = "ECP 203-2020"
        ),
        frameMembers = listOf(
            FrameMemberMark("FM-1", "COLUMN", "300x600", 4000.0, 1),
            FrameMemberMark("FM-2", "COLUMN", "300x600", 4000.0, 1),
            FrameMemberMark("FM-3", "BEAM", "250x500", 6000.0, 1)
        )
    )

    @Test
    fun frameDrawsElevationMembersAndSupportsFromTopology() {
        val cad = DrawingModelExporter.toCad(frameModel())

        // 3 member outlines (CONC) + 2 fixed base plates (FOUNDATION)
        assertEquals(3, cad.entities.count { it is CadPolyline && it.layer == CadLayers.CONC })
        assertEquals(2, cad.entities.count { it is CadPolyline && it.layer == CadLayers.FOUNDATION })
        // one centreline per member
        assertEquals(3, cad.entities.count { it is CadCenterLine })
        // ground line + foundation hatch
        assertTrue(cad.entities.any { it is CadLine && it.layer == CadLayers.SOIL })
        assertTrue(cad.entities.any { it is CadHatch && it.layer == CadLayers.SOIL })
        // frame bears no bars — no circles / stirrup loops
        assertEquals(0, cad.entities.count { it is CadCircle })
        assertEquals(0, cad.entities.count { it is CadPolyline && it.layer == CadLayers.STIRRUP })
        // bay span + height dimension lines from the model
        assertTrue(cad.entities.count { it is CadDimLinear } >= 2)
    }

    @Test
    fun frameSheetUsesMemberScheduleInsteadOfBbs() {
        val dxf = DrawingModelExporter.writeDxfWithSchedule(frameModel())

        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("MARK"))
        assertTrue(dxf.contains("MEMBER"))
        assertTrue(dxf.contains("FM-1"))
        assertTrue(dxf.contains("300x600"))
        // frame table has no bar schedule columns
        assertFalse(dxf.contains("SPACING"))
        assertFalse(dxf.contains("\\U+00D8"))
    }

    private fun seismicModel(): DrawingModel = DrawingModelBuilder.buildSeismic(
        project = "Bridge",
        drawingNumber = "SE-101",
        sheetNumber = "1/1",
        titleBlock = coreTitleBlock("SE-101", "Seismic Analysis"),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        detail = SeismicDetailResult(
            spectrumPoints = (1..50).map { i ->
                SeismicSpectrumPoint(
                    period = i * 0.06,
                    acceleration = (0.56 - 0.01 * (i - 1)).coerceAtLeast(0.06)
                )
            },
            floorForces = listOf(
                SeismicFloorForcePoint(0, 3.0, 150.0, 1250.0),
                SeismicFloorForcePoint(1, 6.0, 300.0, 1100.0),
                SeismicFloorForcePoint(2, 9.0, 450.0, 800.0),
                SeismicFloorForcePoint(3, 12.0, 350.0, 350.0)
            ),
            baseShearKn = 1250.0,
            zoneFactor = 0.15,
            soilFactor = 1.5,
            importanceFactor = 1.0,
            responseModification = 5.0,
            fundamentalPeriod = 0.545,
            spectralAccel = 0.462,
            calculationFormula = "V = Sd(T1) × W",
            codeReference = "ECP 201 §8",
            isSafe = true,
            warnings = emptyList()
        )
    )

    @Test
    fun seismicDrawsSpectrumCurveAndForceBars() {
        val cad = DrawingModelExporter.toCad(seismicModel())

        // single analysis polyline = the 50-point spectrum curve
        val curve = cad.entities.filterIsInstance<CadPolyline>().firstOrNull { it.layer == CadLayers.ANALYSIS }
        assertTrue(curve != null)
        assertEquals(50, curve!!.points.size)
        // one LOAD bar per floor
        assertEquals(4, cad.entities.count { it is CadPolyline && it.layer == CadLayers.LOAD })
        // axes, dashed gridlines + design-period centreline marker
        assertTrue(cad.entities.any { it is CadLine && it.layer == CadLayers.GRID })
        assertTrue(cad.entities.any { it is CadArrow })
        assertTrue(cad.entities.any { it is CadCircle })
        assertTrue(cad.entities.any { it is CadLine && it.layer == CadLayers.CENTER })
        // chart family carries no concrete hatch and no dimensions
        assertEquals(0, cad.entities.count { it is CadHatch })
        assertEquals(0, cad.entities.count { it is CadDimLinear })
        // annotations reach the sheet as text
        val texts = cad.entities.filterIsInstance<CadText>().map { it.text }
        assertTrue(texts.any { it == "RESPONSE SPECTRUM - Sa (g) VS T (s)" })
        assertTrue(texts.any { it == "LATERAL FORCE DISTRIBUTION" })
    }

    @Test
    fun seismicSheetUsesParameterLedgerInsteadOfBbs() {
        val dxf = DrawingModelExporter.writeDxfWithSchedule(seismicModel())

        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("PARAMETER"))
        assertTrue(dxf.contains("BASE SHEAR V (kN)"))
        assertTrue(dxf.contains("FUND. PERIOD T1 (s)"))
        assertTrue(dxf.contains("OVERALL"))
        // seismic ledger has no bar schedule columns
        assertFalse(dxf.contains("SPACING"))
        assertFalse(dxf.contains("\\U+00D8"))
    }
}